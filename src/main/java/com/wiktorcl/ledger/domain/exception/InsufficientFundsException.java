package com.wiktorcl.ledger.domain.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId) {
        super("Account " + accountId + " does not have sufficient funds for this entry");
    }
}
