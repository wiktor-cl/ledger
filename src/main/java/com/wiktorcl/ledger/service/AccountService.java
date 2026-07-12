package com.wiktorcl.ledger.service;

import com.wiktorcl.ledger.domain.Account;
import com.wiktorcl.ledger.domain.AccountType;
import com.wiktorcl.ledger.domain.exception.AccountNotFoundException;
import com.wiktorcl.ledger.domain.exception.DuplicateAccountCodeException;
import com.wiktorcl.ledger.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account openAccount(String code, String name, AccountType type, String currency, BigDecimal openingBalance) {
        accountRepository.findByCode(code).ifPresent(existing -> {
            throw new DuplicateAccountCodeException(code);
        });
        Account account = Account.open(code, name, type, currency, openingBalance);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getById(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Account> listAll() {
        return accountRepository.findAllByOrderByCodeAsc();
    }
}
