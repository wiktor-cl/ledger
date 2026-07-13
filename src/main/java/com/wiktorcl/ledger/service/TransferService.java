package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.config.TransferProperties;
import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.LedgerTransaction;
import com.wiktorcl.ledger.domain.exception.AccountNotFoundException;
import com.wiktorcl.ledger.domain.exception.ConcurrencyConflictException;
import com.wiktorcl.ledger.domain.exception.InvalidTransferException;
import com.wiktorcl.ledger.repository.AccountRepository;
import com.wiktorcl.ledger.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Transfers money between two accounts inside one double-entry
 * {@link LedgerTransaction}.
 *
 * <p><b>Why optimistic locking, not pessimistic:</b> accounts are read far
 * more often than they're written, and write contention on any single
 * account is expected to be occasional, not sustained (this is a ledger,
 * not a single hot counter). Optimistic locking (a {@code @Version} column
 * on {@link Account}) costs nothing on the read path and nothing on an
 * uncontended write, whereas {@code SELECT ... FOR UPDATE} would take a row
 * lock on every transfer whether or not there's actually a conflict, and
 * would require lock-ordering discipline (always lock accounts in a fixed
 * order, e.g. by id) to avoid deadlocks between two transfers moving money
 * in opposite directions between the same pair of accounts. With optimistic
 * locking there is no such ordering requirement: the two accounts are read
 * independently, and only a genuine concurrent write causes a conflict,
 * which this service resolves by re-reading fresh state and retrying - the
 * cost of a conflict is paid only when a conflict actually happens.
 *
 * <p>Retrying is what makes this safe: each retry re-fetches both accounts
 * from the database, so a retry either sees the effect of the transaction
 * it just lost the race with (and may then correctly fail with
 * insufficient-funds if that's now true) or sees room to succeed.
 * {@link AccountRepository#findByIdForUpdate} is kept in the codebase
 * purely as a pessimistic-locking comparison point, not used here.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferAttemptExecutor executor;
    private final LedgerTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final int maxRetries;

    public TransferService(TransferAttemptExecutor executor,
                            LedgerTransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            TransferProperties transferProperties) {
        this.executor = executor;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.maxRetries = transferProperties.maxRetries();
    }

    public TransferResult transfer(TransferCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new InvalidTransferException("Idempotency-Key header is required");
        }
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }

        Optional<LedgerTransaction> existing = transactionRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return toReplayedResult(existing.get(), command);
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executor.attempt(command);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Optimistic lock conflict on attempt {}/{} for idempotency key {}",
                        attempt, maxRetries, command.idempotencyKey());
                backoff(attempt);
            } catch (DataIntegrityViolationException | TransactionSystemException e) {
                // Another concurrent request with the same Idempotency-Key committed first.
                //
                // The unique-constraint violation on ledger_transactions.idempotency_key is
                // sometimes thrown as a plain DataIntegrityViolationException (when Hibernate's
                // flush happens inside an explicit repository call) and sometimes as a
                // TransactionSystemException (when the constraint is only violated at the
                // transaction-commit flush, which JpaTransactionManager wraps differently) -
                // both are handled the same way here. If the lookup comes up empty, this wasn't
                // actually an idempotency-key race, so the original exception is rethrown rather
                // than swallowed.
                return transactionRepository.findByIdempotencyKey(command.idempotencyKey())
                        .map(t -> toReplayedResult(t, command))
                        .orElseThrow(() -> e);
            }
        }

        throw new ConcurrencyConflictException(
                "Could not complete transfer after " + maxRetries + " attempts due to concurrent updates to account "
                        + command.fromAccountId() + " or " + command.toAccountId());
    }

    /**
     * Builds the response for a replayed (already-posted) transfer. Deliberately
     * does not touch {@code transaction.getEntries()}: that's a lazy collection,
     * and this method runs outside any transaction (this class isn't
     * {@code @Transactional} - only {@link TransferAttemptExecutor} is, for the
     * actual posting), so initializing it here would throw
     * {@code LazyInitializationException}. The command's own amount is exactly
     * what was posted under this idempotency key, so there is nothing to
     * re-derive from the entries in the first place.
     */
    private TransferResult toReplayedResult(LedgerTransaction transaction, TransferCommand command) {
        Account from = accountRepository.findById(command.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));
        Account to = accountRepository.findById(command.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));
        return new TransferResult(transaction.getId(), from.getId(), to.getId(), command.amount(),
                from.getBalance(), to.getBalance(), true);
    }

    private void backoff(int attempt) {
        // Small bounded jitter, not exponential: contention here is expected to clear in a
        // handful of retries, and this loop is meant to stay fast under test.
        int millis = ThreadLocalRandom.current().nextInt(1, 5) * Math.min(attempt, 5);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
