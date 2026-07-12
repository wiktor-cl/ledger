package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.EntryType;
import com.wiktorcl.ledger.domain.LedgerTransaction;
import com.wiktorcl.ledger.domain.exception.AccountNotFoundException;
import com.wiktorcl.ledger.domain.exception.InvalidTransferException;
import com.wiktorcl.ledger.repository.AccountRepository;
import com.wiktorcl.ledger.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A single all-or-nothing transfer attempt. Split into its own bean (rather
 * than a private method on {@link TransferService}) so {@code @Transactional}
 * goes through the Spring proxy - a self-invoked private/same-class method
 * would silently run without a transaction boundary, which is a classic Spring
 * AOP pitfall. {@link TransferService} is the one that owns the
 * optimistic-locking retry loop around repeated calls to this method.
 */
@Service
class TransferAttemptExecutor {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    TransferAttemptExecutor(AccountRepository accountRepository, LedgerTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    TransferResult attempt(TransferCommand command) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new InvalidTransferException("fromAccountId and toAccountId must be different accounts");
        }

        // Fresh reads at the start of every attempt (including retries) are what make the
        // optimistic-locking scheme correct: a retry sees the post-conflict committed state,
        // not a stale in-memory snapshot from a failed earlier attempt.
        Account from = accountRepository.findById(command.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));
        Account to = accountRepository.findById(command.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));

        LedgerTransaction transaction = LedgerTransaction.create(command.description(), command.idempotencyKey());
        transaction.post(from, EntryType.CREDIT, command.amount());
        transaction.post(to, EntryType.DEBIT, command.amount());
        transaction.assertBalanced();

        // Saving the accounts is what triggers the @Version check (and thus a possible
        // ObjectOptimisticLockingFailureException) on commit; saving the transaction is what
        // triggers the idempotency-key unique-constraint check (and thus a possible
        // DataIntegrityViolationException) on commit. Both are handled by the caller's retry loop.
        accountRepository.save(from);
        accountRepository.save(to);
        LedgerTransaction saved = transactionRepository.save(transaction);

        return new TransferResult(saved.getId(), from.getId(), to.getId(), command.amount(),
                from.getBalance(), to.getBalance(), false);
    }
}
