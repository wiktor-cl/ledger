package com.wiktorcl.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single append-only debit or credit line. Rows are never updated or
 * deleted (see LedgerEntryRepository, which deliberately does not extend
 * an interface exposing delete methods) - correcting a mistake means
 * posting a new reversing LedgerTransaction (a "storno"), never touching
 * history.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private EntryType type;

    /** Always stored positive; sign is derived from {@link #type}. */
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    LedgerEntry(LedgerTransaction transaction, Account account, EntryType type, BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Entry amount must be positive");
        }
        this.id = UUID.randomUUID();
        this.transaction = transaction;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    /** Debit is positive, credit is negative - used to verify the zero-sum invariant per transaction. */
    public BigDecimal signedAmount() {
        return type == EntryType.DEBIT ? amount : amount.negate();
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public EntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
