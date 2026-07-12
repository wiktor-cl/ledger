package com.wiktorcl.ledger.domain.exception;

/**
 * Thrown when the optimistic-locking retry loop in TransferService exhausts
 * its attempt budget because the target account(s) are under sustained
 * write contention. The client is expected to retry the request (it is
 * safe to do so under the same Idempotency-Key).
 */
public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String message) {
        super(message);
    }

    public ConcurrencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
