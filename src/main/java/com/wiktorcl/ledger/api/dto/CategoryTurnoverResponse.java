package com.wiktorcl.ledger.api.dto;

import com.wiktorcl.ledger.domain.AccountType;
import com.wiktorcl.ledger.service.CategoryTurnover;

import java.math.BigDecimal;

public record CategoryTurnoverResponse(AccountType category, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal net) {
    public static CategoryTurnoverResponse from(CategoryTurnover turnover) {
        return new CategoryTurnoverResponse(turnover.category(), turnover.totalDebit(), turnover.totalCredit(), turnover.net());
    }
}
