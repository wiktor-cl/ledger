package com.wiktorcl.ledger.repository;

import com.wiktorcl.ledger.domain.AccountType;
import com.wiktorcl.ledger.domain.EntryType;

import java.math.BigDecimal;

/** Interface-based projection for the per-category turnover aggregation query. */
public interface TurnoverRow {
    AccountType getType();

    EntryType getEntryType();

    BigDecimal getTotal();
}
