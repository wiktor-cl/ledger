package com.wiktorcl.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResult(
        UUID transactionId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        BigDecimal fromAccountBalance,
        BigDecimal toAccountBalance,
        boolean replayed
) {
}
