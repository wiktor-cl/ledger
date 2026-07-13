package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.AccountType;
import com.wiktorcl.ledger.domain.EntryType;
import com.wiktorcl.ledger.domain.LedgerEntry;
import com.wiktorcl.ledger.domain.exception.AccountNotFoundException;
import com.wiktorcl.ledger.repository.AccountRepository;
import com.wiktorcl.ledger.repository.LedgerEntryRepository;
import com.wiktorcl.ledger.repository.TurnoverRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    public ReportService(AccountRepository accountRepository, LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    /**
     * An account statement for [from, to): the opening balance is derived as
     * {@code account.balance - sumSignedFrom(accountId, from)} (see the
     * javadoc on {@link com.wiktorcl.ledger.repository.LedgerEntryRepository#sumSignedFrom}
     * for why summing entries strictly before {@code from} isn't enough - an
     * account's opening balance isn't itself a {@link LedgerEntry}), followed
     * by a running balance for each entry actually in the period.
     */
    @Transactional(readOnly = true)
    public AccountStatement statement(UUID accountId, Instant from, Instant to) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal openingBalance = account.getBalance().subtract(entryRepository.sumSignedFrom(accountId, from));
        BigDecimal running = openingBalance;

        List<LedgerEntry> entries = entryRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(accountId, from, to);
        List<AccountStatement.Line> lines = new ArrayList<>(entries.size());
        for (LedgerEntry entry : entries) {
            running = running.add(entry.signedAmount());
            lines.add(new AccountStatement.Line(
                    entry.getId(), entry.getTransaction().getId(), entry.getType(), entry.getAmount(), entry.getCreatedAt(), running));
        }

        return new AccountStatement(account.getId(), account.getCode(), from, to, openingBalance, running, lines);
    }

    /** Turnover (total debits/credits) per account category within a period. */
    @Transactional(readOnly = true)
    public List<CategoryTurnover> turnoverByCategory(Instant from, Instant to) {
        Map<AccountType, BigDecimal> debits = new EnumMap<>(AccountType.class);
        Map<AccountType, BigDecimal> credits = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            debits.put(type, BigDecimal.ZERO);
            credits.put(type, BigDecimal.ZERO);
        }

        for (TurnoverRow row : entryRepository.aggregateByAccountType(from, to)) {
            Map<AccountType, BigDecimal> target = row.getEntryType() == EntryType.DEBIT ? debits : credits;
            target.merge(row.getType(), row.getTotal(), BigDecimal::add);
        }

        List<CategoryTurnover> result = new ArrayList<>();
        for (AccountType type : AccountType.values()) {
            BigDecimal debit = debits.get(type);
            BigDecimal credit = credits.get(type);
            result.add(new CategoryTurnover(type, debit, credit, debit.subtract(credit)));
        }
        return result;
    }
}
