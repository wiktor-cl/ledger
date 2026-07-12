package com.wiktorcl.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String description,
        String idempotencyKey
) {
}
