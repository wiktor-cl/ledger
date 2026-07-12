package com.wiktorcl.ledger.api.dto;

import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        AccountType type,
        String currency,
        BigDecimal balance,
        long version,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(), account.getCode(), account.getName(), account.getType(),
                account.getCurrency(), account.getBalance(), account.getVersion(), account.getCreatedAt());
    }
}
