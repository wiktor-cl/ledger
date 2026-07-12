package com.wiktorcl.ledger.api.dto;

import com.wiktorcl.ledger.service.TransferResult;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        BigDecimal fromAccountBalance,
        BigDecimal toAccountBalance,
        boolean replayed
) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.transactionId(), result.fromAccountId(), result.toAccountId(),
                result.amount(), result.fromAccountBalance(), result.toAccountBalance(), result.replayed());
    }
}
