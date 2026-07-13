package com.wiktorcl.ledger.repository;

import com.wiktorcl.ledger.domain.LedgerEntry;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Same rationale as {@link LedgerTransactionRepository}: extends the bare
 * {@link Repository} marker so no delete/update method can exist on the
 * append-only entry log, even by accident.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId);

    List<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID accountId, Instant from, Instant to);

    long countByTransactionId(UUID transactionId);

    /**
     * Signed sum (debit positive, credit negative) of all of an account's entries at or after
     * {@code from}, computed DB-side rather than by pulling full history into memory.
     *
     * <p>Used by {@link com.wiktorcl.ledger.service.ReportService#statement} to derive a report
     * period's opening balance as {@code account.balance - sumSignedFrom(accountId, from)} rather
     * than by summing entries strictly before {@code from}: an account's opening balance (set at
     * {@link com.wiktorcl.ledger.domain.Account#open}) is not itself represented by a
     * {@link LedgerEntry}, so summing "before" entries alone silently drops it for any account
     * that wasn't opened at exactly zero. The account's live {@code balance} already reflects
     * that opening balance plus every entry ever posted, so subtracting everything from
     * {@code from} onward recovers the correct opening balance for the period regardless of how
     * the account itself was opened.
     */
    @Query("select coalesce(sum(case when e.type = com.wiktorcl.ledger.domain.EntryType.DEBIT then e.amount else -e.amount end), 0) " +
            "from LedgerEntry e where e.account.id = :accountId and e.createdAt >= :from")
    BigDecimal sumSignedFrom(@Param("accountId") UUID accountId, @Param("from") Instant from);

    /** Per-account-category, per-entry-type turnover totals within a period, aggregated in the database. */
    @Query("select e.account.type as type, e.type as entryType, sum(e.amount) as total " +
            "from LedgerEntry e where e.createdAt between :from and :to " +
            "group by e.account.type, e.type")
    List<TurnoverRow> aggregateByAccountType(@Param("from") Instant from, @Param("to") Instant to);
}
