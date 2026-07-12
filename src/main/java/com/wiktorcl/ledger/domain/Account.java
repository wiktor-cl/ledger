package com.wiktorcl.ledger.domain;

import com.wiktorcl.ledger.domain.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An account's {@code balance} is a materialized snapshot, not the source
 * of truth - it is always derived from (and kept consistent with) the sum
 * of its {@link LedgerEntry} rows, recomputed transactionally on every
 * applied entry. It exists purely so reads (GET balance) don't require
 * scanning the full entry history. See ReportService for a from-entries
 * recomputation used to verify the snapshot in tests.
 *
 * <p>{@code version} is the optimistic-locking column (see
 * TransferService for why optimistic over pessimistic locking was chosen)
 * that serializes concurrent balance mutations to this row.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // JPA
    }

    private Account(UUID id, String code, String name, AccountType type, String currency, BigDecimal openingBalance) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.balance = openingBalance;
        this.createdAt = Instant.now();
    }

    public static Account open(String code, String name, AccountType type, String currency, BigDecimal openingBalance) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        BigDecimal opening = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        if (opening.signum() < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
        return new Account(UUID.randomUUID(), code, name, type, currency, opening);
    }

    /**
     * Applies a ledger entry's effect to this account's balance snapshot.
     * An entry on the account type's normal-balance side increases the
     * balance, the opposite side decreases it (see {@link AccountType#normalBalanceSide()}).
     * Throws if the resulting balance would go negative, which is the
     * domain invariant "an account may never go below zero" - applied
     * uniformly, so e.g. a revenue account can't be debited past what it
     * has accrued in credits.
     */
    public void applyEntry(EntryType entryType, BigDecimal amount) {
        BigDecimal delta = entryType == this.type.normalBalanceSide() ? amount : amount.negate();
        BigDecimal newBalance = this.balance.add(delta);
        if (newBalance.signum() < 0) {
            throw new InsufficientFundsException(this.id);
        }
        this.balance = newBalance;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
