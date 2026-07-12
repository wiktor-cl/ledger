package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.config.TransferProperties;
import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.EntryType;
import com.wiktorcl.ledger.domain.LedgerTransaction;
import com.wiktorcl.ledger.domain.exception.ConcurrencyConflictException;
import com.wiktorcl.ledger.domain.exception.InvalidTransferException;
import com.wiktorcl.ledger.repository.AccountRepository;
import com.wiktorcl.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferAttemptExecutor executor;
    @Mock
    private LedgerTransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();

    private TransferService newService(int maxRetries) {
        return new TransferService(executor, transactionRepository, accountRepository, new TransferProperties(maxRetries));
    }

    private TransferCommand command(String idempotencyKey) {
        return new TransferCommand(fromId, toId, new BigDecimal("10.0000"), "test transfer", idempotencyKey);
    }

    @BeforeEach
    void defaultStubs() {
        lenient().when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        TransferService service = newService(5);

        assertThatThrownBy(() -> service.transfer(command(null)))
                .isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> service.transfer(command("  ")))
                .isInstanceOf(InvalidTransferException.class);

        verify(executor, never()).attempt(any());
    }

    @Test
    void rejectsNonPositiveAmount() {
        TransferService service = newService(5);
        TransferCommand zeroAmount = new TransferCommand(fromId, toId, BigDecimal.ZERO, "x", "idem-zero");

        assertThatThrownBy(() -> service.transfer(zeroAmount)).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void happyPathCallsExecutorExactlyOnce() {
        TransferService service = newService(5);
        TransferResult expected = new TransferResult(UUID.randomUUID(), fromId, toId, new BigDecimal("10.0000"),
                new BigDecimal("90.0000"), new BigDecimal("10.0000"), false);
        when(executor.attempt(any())).thenReturn(expected);

        TransferResult result = service.transfer(command("idem-1"));

        assertThat(result).isEqualTo(expected);
        verify(executor, times(1)).attempt(any());
    }

    @Test
    void existingIdempotencyKeyShortCircuitsWithoutCallingExecutor() {
        LedgerTransaction existing = existingTransactionTo(toId, new BigDecimal("10.0000"));
        when(transactionRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.of(existing));
        stubAccounts();

        TransferService service = newService(5);
        TransferResult result = service.transfer(command("idem-2"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transactionId()).isEqualTo(existing.getId());
        verify(executor, never()).attempt(any());
    }

    @Test
    void retriesOnOptimisticLockConflictThenSucceeds() {
        TransferResult expected = new TransferResult(UUID.randomUUID(), fromId, toId, new BigDecimal("10.0000"),
                new BigDecimal("90.0000"), new BigDecimal("10.0000"), false);
        when(executor.attempt(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, fromId))
                .thenReturn(expected);

        TransferService service = newService(5);
        TransferResult result = service.transfer(command("idem-3"));

        assertThat(result).isEqualTo(expected);
        verify(executor, times(2)).attempt(any());
    }

    @Test
    void exhaustsRetriesAndThrowsConcurrencyConflict() {
        when(executor.attempt(any())).thenThrow(new ObjectOptimisticLockingFailureException(Account.class, fromId));

        TransferService service = newService(3);

        assertThatThrownBy(() -> service.transfer(command("idem-4")))
                .isInstanceOf(ConcurrencyConflictException.class);
        verify(executor, times(3)).attempt(any());
    }

    @Test
    void raceOnIdempotencyKeyInsertFallsBackToExistingTransaction() {
        LedgerTransaction existing = existingTransactionTo(toId, new BigDecimal("10.0000"));
        when(executor.attempt(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(transactionRepository.findByIdempotencyKey("idem-5"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        stubAccounts();

        TransferService service = newService(5);
        TransferResult result = service.transfer(command("idem-5"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transactionId()).isEqualTo(existing.getId());
        verify(executor, times(1)).attempt(any());
    }

    private void stubAccounts() {
        Account from = mock(Account.class);
        when(from.getId()).thenReturn(fromId);
        when(from.getBalance()).thenReturn(new BigDecimal("90.0000"));
        Account to = mock(Account.class);
        when(to.getId()).thenReturn(toId);
        when(to.getBalance()).thenReturn(new BigDecimal("10.0000"));
        when(accountRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(to));
    }

    private LedgerTransaction existingTransactionTo(UUID toAccountId, BigDecimal amount) {
        Account to = mock(Account.class);
        when(to.getId()).thenReturn(toAccountId);
        LedgerTransaction transaction = LedgerTransaction.create("replayed", "idem-existing");
        Account from = mock(Account.class);
        when(from.getId()).thenReturn(fromId);
        transaction.post(from, EntryType.CREDIT, amount);
        transaction.post(to, EntryType.DEBIT, amount);
        return transaction;
    }
}
