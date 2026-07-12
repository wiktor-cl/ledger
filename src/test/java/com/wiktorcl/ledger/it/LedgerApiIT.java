package com.wiktorcl.ledger.it;

import com.wiktorcl.ledger.api.dto.AccountResponse;
import com.wiktorcl.ledger.api.dto.AccountStatementResponse;
import com.wiktorcl.ledger.api.dto.AuthResponse;
import com.wiktorcl.ledger.api.dto.CategoryTurnoverResponse;
import com.wiktorcl.ledger.api.dto.LoginRequest;
import com.wiktorcl.ledger.api.dto.RegisterRequest;
import com.wiktorcl.ledger.api.dto.TransferResponse;
import com.wiktorcl.ledger.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Broad end-to-end smoke coverage of the REST surface, beyond the two
 * focus areas (idempotency, concurrency) covered by their own test
 * classes: registration/login, unauthenticated access being rejected,
 * account lifecycle, a real transfer, and both report endpoints.
 */
class LedgerApiIT extends AbstractIntegrationTest {

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/accounts", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registerLoginRejectsDuplicateUsernameAndWrongPassword() {
        String username = "user-" + UUID.randomUUID();
        RegisterRequest registerRequest = new RegisterRequest(username, "password123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register", registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody().token()).isNotBlank();

        ResponseEntity<String> duplicate = restTemplate.postForEntity(
                baseUrl() + "/auth/register", registerRequest, String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        LoginRequest wrongPassword = new LoginRequest(username, "wrong-password");
        ResponseEntity<String> badLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", wrongPassword, String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        LoginRequest correct = new LoginRequest(username, "password123");
        ResponseEntity<AuthResponse> goodLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/login", correct, AuthResponse.class);
        assertThat(goodLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openAccountRejectsDuplicateCode() {
        String token = registerNewUserAndGetToken();
        String code = "DUP-" + UUID.randomUUID();
        var request = new com.wiktorcl.ledger.api.dto.OpenAccountRequest(code, "Dup", AccountType.ASSET, "USD", BigDecimal.ZERO);
        HttpEntity<Object> entity = new HttpEntity<>(request, authHeaders(token));

        ResponseEntity<AccountResponse> first = restTemplate.exchange(
                baseUrl() + "/accounts", HttpMethod.POST, entity, AccountResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.exchange(
                baseUrl() + "/accounts", HttpMethod.POST, entity, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void transferBetweenAccountsUpdatesBalancesAndReports() {
        String token = registerNewUserAndGetToken();
        UUID cash = openAccount(token, AccountType.ASSET, new BigDecimal("500.00"));
        UUID revenue = openAccount(token, AccountType.INCOME, BigDecimal.ZERO);

        // A sale: cash increases (debit, asset's normal side), revenue increases (credit, income's normal side).
        ResponseEntity<TransferResponse> response = transfer(token, revenue, cash, new BigDecimal("75.00"), UUID.randomUUID().toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AccountResponse cashAccount = getAccount(token, cash);
        AccountResponse revenueAccount = getAccount(token, revenue);
        assertThat(cashAccount.balance()).isEqualByComparingTo("575.00");
        assertThat(revenueAccount.balance()).isEqualByComparingTo("75.00");

        Instant from = Instant.EPOCH;
        Instant to = Instant.now().plusSeconds(60);
        AccountStatementResponse cashStatement = statement(token, cash, from, to);
        assertThat(cashStatement.closingBalance()).isEqualByComparingTo("575.00");
        assertThat(cashStatement.lines()).hasSize(1);

        List<CategoryTurnoverResponse> turnover = turnover(token, from, to);
        CategoryTurnoverResponse incomeTurnover = turnover.stream()
                .filter(t -> t.category() == AccountType.INCOME)
                .findFirst()
                .orElseThrow();
        assertThat(incomeTurnover.totalCredit()).isGreaterThanOrEqualTo(new BigDecimal("75.00"));
    }

    @Test
    void missingIdempotencyKeyHeaderIsRejected() {
        String token = registerNewUserAndGetToken();
        UUID from = openAccount(token, AccountType.ASSET, new BigDecimal("10.00"));
        UUID to = openAccount(token, AccountType.ASSET, BigDecimal.ZERO);

        var body = new com.wiktorcl.ledger.api.dto.TransferRequest(from, to, new BigDecimal("1.00"), "no key");
        HttpEntity<Object> entity = new HttpEntity<>(body, authHeaders(token));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/transfers", HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private List<CategoryTurnoverResponse> turnover(String token, Instant from, Instant to) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/reports/turnover")
                .queryParam("from", from)
                .queryParam("to", to)
                .build()
                .toUri();
        ResponseEntity<CategoryTurnoverResponse[]> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, CategoryTurnoverResponse[].class);
        return List.of(response.getBody());
    }
}
