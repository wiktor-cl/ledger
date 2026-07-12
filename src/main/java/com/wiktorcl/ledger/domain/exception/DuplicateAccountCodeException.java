package com.wiktorcl.ledger.domain.exception;

public class DuplicateAccountCodeException extends RuntimeException {
    public DuplicateAccountCodeException(String code) {
        super("An account with code '" + code + "' already exists");
    }
}
