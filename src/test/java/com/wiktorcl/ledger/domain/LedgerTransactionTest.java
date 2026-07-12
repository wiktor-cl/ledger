package com.wiktorcl.ledger.domain;

import com.wiktorcl.ledger.domain.exception.UnbalancedTransactionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

    private Account cash() {
        return Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("100.0000"));
    }

    private Account revenue() {
        return Account.open("REV", "Revenue", AccountType.INCOME, "USD", new BigDecimal("0.0000"));
    }

    @Test
    void twoDebitsWithNoOffsettingCreditFailInvariant() {
        Account cash = cash();
        Account otherAsset = Account.open("BANK", "Bank", AccountType.ASSET, "USD", new BigDecimal("100.0000"));
        LedgerTransaction transaction = LedgerTransaction.create("sale", "idem-1");

        transaction.post(cash, EntryType.DEBIT, new BigDecimal("50.0000"));
        transaction.post(otherAsset, EntryType.DEBIT, new BigDecimal("50.0000"));

        // Not realistic accounting (both entries debit, nothing offsetting) but exercises the
        // raw invariant: signed sum must be zero, so this specific pairing must fail.
        assertThatThrownBy(transaction::assertBalanced).isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void debitAndCreditOfEqualAmountBalances() {
        Account cash = cash();
        Account revenue = revenue();
        LedgerTransaction transaction = LedgerTransaction.create("sale", "idem-2");

        transaction.post(cash, EntryType.DEBIT, new BigDecimal("50.0000"));
        transaction.post(revenue, EntryType.CREDIT, new BigDecimal("50.0000"));

        transaction.assertBalanced();

        assertThat(transaction.getEntries()).hasSize(2);
        assertThat(cash.getBalance()).isEqualByComparingTo("150.0000");
        assertThat(revenue.getBalance()).isEqualByComparingTo("50.0000");
    }

    @Test
    void mismatchedAmountsFailInvariant() {
        Account cash = cash();
        Account revenue = revenue();
        LedgerTransaction transaction = LedgerTransaction.create("sale", "idem-3");

        transaction.post(cash, EntryType.DEBIT, new BigDecimal("50.0000"));
        transaction.post(revenue, EntryType.CREDIT, new BigDecimal("49.9900"));

        assertThatThrownBy(transaction::assertBalanced).isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void requiresAtLeastTwoEntries() {
        Account cash = cash();
        LedgerTransaction transaction = LedgerTransaction.create("sale", "idem-4");

        transaction.post(cash, EntryType.DEBIT, new BigDecimal("50.0000"));

        assertThatThrownBy(transaction::assertBalanced).isInstanceOf(UnbalancedTransactionException.class);
    }

    @Test
    void reversalPointsBackAtOriginal() {
        LedgerTransaction original = LedgerTransaction.create("sale", "idem-5");
        LedgerTransaction reversal = LedgerTransaction.reversalOf(original, "storno of sale", "idem-6");

        assertThat(reversal.getReversalOfTransactionId()).isEqualTo(original.getId());
    }
}
