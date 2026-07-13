package com.wiktorcl.ledger.api;

import com.wiktorcl.ledger.domain.exception.AccountNotFoundException;
import com.wiktorcl.ledger.domain.exception.ConcurrencyConflictException;
import com.wiktorcl.ledger.domain.exception.DuplicateAccountCodeException;
import com.wiktorcl.ledger.domain.exception.InsufficientFundsException;
import com.wiktorcl.ledger.domain.exception.InvalidTransferException;
import com.wiktorcl.ledger.domain.exception.UnbalancedTransactionException;
import com.wiktorcl.ledger.security.UsernameAlreadyTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Exception, not RuntimeException: MissingRequestHeaderException extends the checked
    // jakarta.servlet.ServletException, so a RuntimeException-typed parameter here can never
    // be bound to it - Spring fails with "Could not resolve parameter... No suitable resolver"
    // and the request falls through to handleUnexpected() as a 500 instead of a 400.
    @ExceptionHandler({InvalidTransferException.class, MissingRequestHeaderException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({DuplicateAccountCodeException.class, UsernameAlreadyTakenException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ProblemDetail handleConcurrencyConflict(ConcurrencyConflictException ex) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, ex.getMessage());
        detail.setProperty("retryable", true);
        return detail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (var fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    /**
     * A balanced-sum or minimum-entry-count violation here means a bug in
     * TransferAttemptExecutor (it should be structurally impossible to
     * reach the API with an unbalanced transaction) - logged loudly and
     * reported generically rather than as a client-facing 4xx.
     */
    @ExceptionHandler(UnbalancedTransactionException.class)
    public ProblemDetail handleUnbalancedTransaction(UnbalancedTransactionException ex) {
        log.error("Unbalanced transaction invariant violated: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred while posting the transaction");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        return ProblemDetail.forStatusAndDetail(status, message);
    }
}
