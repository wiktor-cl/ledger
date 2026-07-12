package com.wiktorcl.ledger.it;

import com.wiktorcl.ledger.api.dto.AccountResponse;
import com.wiktorcl.ledger.api.dto.TransferResponse;
import com.wiktorcl.ledger.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the literal requirement: a repeated request with the same
 * Idempotency-Key must not post the transfer a second time. Covers both
 * the "fast path" (the key is already committed when the retry arrives)
 * and the harder race (both requests reach the server before either has
 * committed, so the DB's unique constraint - not just the initial lookup -
 * has to be what prevents the double post).
 */
class TransferIdempotencyIT extends AbstractIntegrationTest {

    @Test
    void sequentialDuplicateRequestReturnsSameTransactionWithoutPostingTwice() {
        String token = registerNewUserAndGetToken();
        UUID from = openAccount(token, AccountType.ASSET, new BigDecimal("100.00"));
        UUID to = openAccount(token, AccountType.ASSET, new BigDecimal("0.00"));
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<TransferResponse> first = transfer(token, from, to, new BigDecimal("40.00"), idempotencyKey);
        ResponseEntity<TransferResponse> second = transfer(token, from, to, new BigDecimal("40.00"), idempotencyKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().replayed()).isFalse();
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().replayed()).isTrue();
        assertThat(second.getBody().transactionId()).isEqualTo(first.getBody().transactionId());

        AccountResponse fromAccount = getAccount(token, from);
        AccountResponse toAccount = getAccount(token, to);
        assertThat(fromAccount.balance()).isEqualByComparingTo("60.00");
        assertThat(toAccount.balance()).isEqualByComparingTo("40.00");

        // Exactly one posted transaction's worth of entries (one credit, one debit) exists.
        long entryCount = statement(token, from, Instant.EPOCH, Instant.now().plusSeconds(60)).lines().size()
                + statement(token, to, Instant.EPOCH, Instant.now().plusSeconds(60)).lines().size();
        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateRequestsWithSameKeyStillPostOnlyOnce() throws Exception {
        String token = registerNewUserAndGetToken();
        UUID from = openAccount(token, AccountType.ASSET, new BigDecimal("100.00"));
        UUID to = openAccount(token, AccountType.ASSET, new BigDecimal("0.00"));
        String idempotencyKey = UUID.randomUUID().toString();
        int concurrentRequests = 10;

        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResponseEntity<TransferResponse>>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return transfer(token, from, to, new BigDecimal("40.00"), idempotencyKey);
            });
        }

        List<Future<ResponseEntity<TransferResponse>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<TransferResponse>> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await();
        start.countDown();

        List<ResponseEntity<TransferResponse>> results = new ArrayList<>();
        for (Future<ResponseEntity<TransferResponse>> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK));
        List<UUID> transactionIds = results.stream()
                .map(r -> r.getBody().transactionId())
                .distinct()
                .collect(Collectors.toList());
        assertThat(transactionIds).as("all concurrent duplicate requests must resolve to the same transaction").hasSize(1);

        long createdCount = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        assertThat(createdCount).as("exactly one of the racing requests actually created the transaction").isEqualTo(1);

        AccountResponse fromAccount = getAccount(token, from);
        AccountResponse toAccount = getAccount(token, to);
        assertThat(fromAccount.balance()).isEqualByComparingTo("60.00");
        assertThat(toAccount.balance()).isEqualByComparingTo("40.00");
    }
}
