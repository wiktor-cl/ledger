package com.wiktorcl.ledger.domain;

import com.wiktorcl.ledger.domain.exception.UnbalancedTransactionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The unit of double-entry posting. A transaction is immutable and
 * append-only once persisted: it is never edited or deleted (see
 * LedgerTransactionRepository). {@link #assertBalanced()} enforces the
 * core invariant - the signed sum of all entries is exactly zero - before
 * the transaction is allowed to be persisted (called from TransferService
 * right before save, and exercised directly in domain unit tests).
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Null for transactions that don't need idempotent replay protection. */
    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    /** Set only on reversal ("storno") transactions; points at the original, never mutated after creation. */
    @Column(name = "reversal_of_transaction_id")
    private UUID reversalOfTransactionId;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
        // JPA
    }

    private LedgerTransaction(String description, String idempotencyKey, UUID reversalOfTransactionId) {
        this.id = UUID.randomUUID();
        this.description = description;
        this.occurredAt = Instant.now();
        this.idempotencyKey = idempotencyKey;
        this.reversalOfTransactionId = reversalOfTransactionId;
    }

    public static LedgerTransaction create(String description, String idempotencyKey) {
        return new LedgerTransaction(description, idempotencyKey, null);
    }

    public static LedgerTransaction reversalOf(LedgerTransaction original, String description, String idempotencyKey) {
        return new LedgerTransaction(description, idempotencyKey, original.getId());
    }

    /** Posts one debit/credit line against {@code account}, applying its effect to the account's balance snapshot. */
    public void post(Account account, EntryType type, BigDecimal amount) {
        LedgerEntry entry = new LedgerEntry(this, account, type, amount);
        entries.add(entry);
        account.applyEntry(type, amount);
    }

    /**
     * Verifies the double-entry invariant: at least two entries, and their
     * signed amounts sum to exactly zero. Called before every persist.
     */
    public void assertBalanced() {
        if (entries.size() < 2) {
            throw new UnbalancedTransactionException("A transaction requires at least two entries, got " + entries.size());
        }
        BigDecimal sum = entries.stream()
                .map(LedgerEntry::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new UnbalancedTransactionException("Transaction entries must sum to zero, got " + sum);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getReversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerTransaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
