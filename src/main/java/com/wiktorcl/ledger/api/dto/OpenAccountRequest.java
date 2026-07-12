package com.wiktorcl.ledger.api.dto;

import com.wiktorcl.ledger.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record OpenAccountRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "code must be 1-64 alphanumeric/._- characters")
        String code,

        @NotBlank
        String name,

        @NotNull
        AccountType type,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @DecimalMin(value = "0", message = "openingBalance cannot be negative")
        BigDecimal openingBalance
) {
}
