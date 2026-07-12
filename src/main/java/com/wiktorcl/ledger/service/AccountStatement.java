package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatement(
        UUID accountId,
        String accountCode,
        Instant from,
        Instant to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<Line> lines
) {
    public record Line(UUID entryId, UUID transactionId, EntryType type, BigDecimal amount, Instant createdAt, BigDecimal runningBalance) {
    }
}
