package com.wiktorcl.ledger.repository;

import com.wiktorcl.ledger.domain.LedgerTransaction;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately extends the bare {@link Repository} marker rather than
 * {@code JpaRepository} or {@code CrudRepository}: only the methods
 * declared below exist, and none of them are a delete. The ledger is
 * append-only by construction, not just by convention - there is no
 * repository method that could remove a posted transaction.
 */
public interface LedgerTransactionRepository extends Repository<LedgerTransaction, UUID> {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(UUID id);

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    long count();
}
