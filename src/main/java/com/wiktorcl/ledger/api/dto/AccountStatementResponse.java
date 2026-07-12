package com.wiktorcl.ledger.api.dto;

import com.wiktorcl.ledger.domain.EntryType;
import com.wiktorcl.ledger.service.AccountStatement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatementResponse(
        UUID accountId,
        String accountCode,
        Instant from,
        Instant to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        List<Line> lines
) {
    public record Line(UUID entryId, UUID transactionId, EntryType type, BigDecimal amount, Instant createdAt, BigDecimal runningBalance) {
        static Line from(AccountStatement.Line line) {
            return new Line(line.entryId(), line.transactionId(), line.type(), line.amount(), line.createdAt(), line.runningBalance());
        }
    }

    public static AccountStatementResponse from(AccountStatement statement) {
        return new AccountStatementResponse(
                statement.accountId(), statement.accountCode(), statement.from(), statement.to(),
                statement.openingBalance(), statement.closingBalance(),
                statement.lines().stream().map(Line::from).toList());
    }
}
