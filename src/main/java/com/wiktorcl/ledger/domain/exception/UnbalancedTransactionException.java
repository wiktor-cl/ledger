package com.wiktorcl.ledger.domain.exception;

public class UnbalancedTransactionException extends RuntimeException {
    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
