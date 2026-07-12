package com.wiktorcl.ledger.it;

import com.wiktorcl.ledger.api.dto.AccountResponse;
import com.wiktorcl.ledger.api.dto.AccountStatementResponse;
import com.wiktorcl.ledger.api.dto.AuthResponse;
import com.wiktorcl.ledger.api.dto.OpenAccountRequest;
import com.wiktorcl.ledger.api.dto.RegisterRequest;
import com.wiktorcl.ledger.api.dto.TransferRequest;
import com.wiktorcl.ledger.api.dto.TransferResponse;
import com.wiktorcl.ledger.domain.AccountType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared Testcontainers Postgres instance for every integration test in
 * this package - real HTTP calls into a real Spring context, a real
 * database, and real Flyway migrations, no mocks. Every test generates its
 * own unique account codes/idempotency keys so classes sharing the one
 * container never collide.
 *
 * <p>This is the Testcontainers "singleton container" pattern: the
 * container is started once, manually, in a static initializer - it is
 * deliberately <b>not</b> annotated with {@code @Container}/{@code @Testcontainers}.
 * That JUnit extension manages start/stop per test class, and since this
 * field is inherited (shared) across every {@code *IT} subclass here, its
 * per-class {@code afterAll} would stop the one shared container out from
 * under whichever test class happens to run next - which is exactly what
 * happened when this was first written (later test classes got connection
 * refused / stale-connection errors). Testcontainers' own Ryuk reaper
 * container cleans this up when the JVM exits; there's no explicit stop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Default Hikari pool (10) would bottleneck the concurrency tests, which fire dozens
        // of simultaneous HTTP requests each needing a connection for their transactional attempt.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "40");
        // A generous retry budget so the concurrency tests' exact-success-count assertions
        // reflect genuine insufficient-funds rejections, not the retry loop being exhausted
        // under this test's deliberately extreme same-row contention.
        registry.add("ledger.transfer.max-retries", () -> "500");
    }

    @LocalServerPort
    protected int port;

    protected final TestRestTemplate restTemplate = new TestRestTemplate();

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String registerNewUserAndGetToken() {
        String username = "user-" + UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(username, "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                baseUrl() + "/auth/register", request, AuthResponse.class);
        return response.getBody().token();
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected UUID openAccount(String token, AccountType type, BigDecimal openingBalance) {
        String code = type.name() + "-" + UUID.randomUUID();
        OpenAccountRequest request = new OpenAccountRequest(code, code, type, "USD", openingBalance);
        HttpEntity<OpenAccountRequest> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<AccountResponse> response = restTemplate.exchange(
                baseUrl() + "/accounts", HttpMethod.POST, entity, AccountResponse.class);
        return response.getBody().id();
    }

    protected AccountResponse getAccount(String token, UUID id) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<AccountResponse> response = restTemplate.exchange(
                baseUrl() + "/accounts/" + id, HttpMethod.GET, entity, AccountResponse.class);
        return response.getBody();
    }

    protected ResponseEntity<TransferResponse> transfer(String token, UUID from, UUID to, BigDecimal amount, String idempotencyKey) {
        HttpHeaders headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        TransferRequest body = new TransferRequest(from, to, amount, "integration test transfer");
        HttpEntity<TransferRequest> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(baseUrl() + "/transfers", HttpMethod.POST, entity, TransferResponse.class);
    }

    protected AccountStatementResponse statement(String token, UUID accountId, Instant from, Instant to) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/reports/accounts/" + accountId + "/statement")
                .queryParam("from", from)
                .queryParam("to", to)
                .build()
                .toUri();
        ResponseEntity<AccountStatementResponse> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, AccountStatementResponse.class);
        return response.getBody();
    }
}
