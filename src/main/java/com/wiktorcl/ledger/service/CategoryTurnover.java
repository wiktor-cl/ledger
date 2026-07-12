package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.AccountType;

import java.math.BigDecimal;

public record CategoryTurnover(AccountType category, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal net) {
}
