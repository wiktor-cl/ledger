package com.wiktorcl.ledger.domain;

import com.wiktorcl.ledger.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void opensWithGivenOpeningBalance() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("100.0000"));

        assertThat(account.getBalance()).isEqualByComparingTo("100.0000");
        assertThat(account.getCode()).isEqualTo("CASH");
    }

    @Test
    void opensWithZeroBalanceWhenNoneGiven() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", null);

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThatThrownBy(() -> Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void debitIncreasesBalance() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("10.0000"));

        account.applyEntry(EntryType.DEBIT, new BigDecimal("5.0000"));

        assertThat(account.getBalance()).isEqualByComparingTo("15.0000");
    }

    @Test
    void creditDecreasesBalance() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("10.0000"));

        account.applyEntry(EntryType.CREDIT, new BigDecimal("4.0000"));

        assertThat(account.getBalance()).isEqualByComparingTo("6.0000");
    }

    @Test
    void creditCannotTakeBalanceBelowZero() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("10.0000"));

        assertThatThrownBy(() -> account.applyEntry(EntryType.CREDIT, new BigDecimal("10.0001")))
                .isInstanceOf(InsufficientFundsException.class);

        // The rejected entry must not have partially mutated the balance.
        assertThat(account.getBalance()).isEqualByComparingTo("10.0000");
    }

    @Test
    void creditToExactlyZeroIsAllowed() {
        Account account = Account.open("CASH", "Cash", AccountType.ASSET, "USD", new BigDecimal("10.0000"));

        account.applyEntry(EntryType.CREDIT, new BigDecimal("10.0000"));

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
