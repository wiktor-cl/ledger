package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.AccountType;
import com.wiktorcl.ledger.domain.EntryType;
import com.wiktorcl.ledger.domain.LedgerEntry;
import com.wiktorcl.ledger.domain.LedgerTransaction;
import com.wiktorcl.ledger.repository.AccountRepository;
import com.wiktorcl.ledger.repository.LedgerEntryRepository;
import com.wiktorcl.ledger.repository.TurnoverRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerEntryRepository entryRepository;

    @Test
    void statementIncludesOpeningBalanceAndRunningBalancePerLine() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", BigDecimal.ZERO);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(withId(account, accountId)));
        when(entryRepository.sumSignedBefore(eq(accountId), eq(from))).thenReturn(new BigDecimal("100.0000"));

        LedgerTransaction txn = LedgerTransaction.create("desc", "idem-1");
        LedgerEntry entry = post(txn, withId(account, accountId), EntryType.DEBIT, new BigDecimal("25.0000"));
        when(entryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(accountId, from, to))
                .thenReturn(List.of(entry));

        ReportService reportService = new ReportService(accountRepository, entryRepository);
        AccountStatement statement = reportService.statement(accountId, from, to);

        assertThat(statement.openingBalance()).isEqualByComparingTo("100.0000");
        assertThat(statement.closingBalance()).isEqualByComparingTo("125.0000");
        assertThat(statement.lines()).hasSize(1);
        assertThat(statement.lines().get(0).runningBalance()).isEqualByComparingTo("125.0000");
    }

    @Test
    void turnoverByCategoryAggregatesDebitsAndCreditsPerType() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");

        TurnoverRow assetDebit = row(AccountType.ASSET, EntryType.DEBIT, new BigDecimal("300.0000"));
        TurnoverRow incomeCredit = row(AccountType.INCOME, EntryType.CREDIT, new BigDecimal("300.0000"));
        when(entryRepository.aggregateByAccountType(from, to)).thenReturn(List.of(assetDebit, incomeCredit));

        ReportService reportService = new ReportService(accountRepository, entryRepository);
        List<CategoryTurnover> result = reportService.turnoverByCategory(from, to);

        CategoryTurnover asset = result.stream().filter(r -> r.category() == AccountType.ASSET).findFirst().orElseThrow();
        CategoryTurnover income = result.stream().filter(r -> r.category() == AccountType.INCOME).findFirst().orElseThrow();
        CategoryTurnover liability = result.stream().filter(r -> r.category() == AccountType.LIABILITY).findFirst().orElseThrow();

        assertThat(asset.totalDebit()).isEqualByComparingTo("300.0000");
        assertThat(asset.totalCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(income.totalCredit()).isEqualByComparingTo("300.0000");
        assertThat(liability.totalDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(liability.totalCredit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private TurnoverRow row(AccountType type, EntryType entryType, BigDecimal total) {
        return new TurnoverRow() {
            @Override
            public AccountType getType() {
                return type;
            }

            @Override
            public EntryType getEntryType() {
                return entryType;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }

    private Account withId(Account account, UUID id) {
        try {
            var field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
            return account;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private LedgerEntry post(LedgerTransaction txn, Account account, EntryType type, BigDecimal amount) {
        txn.post(account, type, amount);
        return txn.getEntries().get(txn.getEntries().size() - 1);
    }
}
