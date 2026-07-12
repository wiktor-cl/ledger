package com.wiktorcl.ledger.repository;

import com.wiktorcl.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByCode(String code);

    List<Account> findAllByOrderByCodeAsc();

    /**
     * Available for a pessimistic-locking comparison/benchmark (see README
     * trade-off discussion) - not used on the default transfer path, which
     * relies on optimistic locking (@Version) instead.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
