package com.wiktorcl.ledger.domain;

/**
 * Chart-of-accounts category. Each type has a normal balance side, exactly
 * as in real double-entry bookkeeping: assets and expenses grow on the
 * debit side, liabilities/equity/income grow on the credit side. See
 * {@link Account#applyEntry} for where this is applied, and the README for
 * why the "balance may never go negative" invariant is enforced uniformly
 * on the normal-balance side for every category, not just assets.
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    EXPENSE;

    public EntryType normalBalanceSide() {
        return switch (this) {
            case ASSET, EXPENSE -> EntryType.DEBIT;
            case LIABILITY, EQUITY, INCOME -> EntryType.CREDIT;
        };
    }
}
