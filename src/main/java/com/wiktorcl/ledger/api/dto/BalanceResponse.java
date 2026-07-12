package com.wiktorcl.ledger.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(UUID accountId, String accountCode, BigDecimal balance, Instant asOf) {
}
