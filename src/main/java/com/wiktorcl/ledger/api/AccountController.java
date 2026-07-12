package com.wiktorcl.ledger.api;

import com.wiktorcl.ledger.api.dto.AccountResponse;
import com.wiktorcl.ledger.api.dto.BalanceResponse;
import com.wiktorcl.ledger.api.dto.OpenAccountRequest;
import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        var openingBalance = request.openingBalance() == null
                ? null
                : request.openingBalance().setScale(4, RoundingMode.HALF_UP);
        Account account = accountService.openAccount(
                request.code(), request.name(), request.type(), request.currency(), openingBalance);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> listAll() {
        return accountService.listAll().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable UUID id) {
        return AccountResponse.from(accountService.getById(id));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        Account account = accountService.getById(id);
        return new BalanceResponse(account.getId(), account.getCode(), account.getBalance(), Instant.now());
    }
}
