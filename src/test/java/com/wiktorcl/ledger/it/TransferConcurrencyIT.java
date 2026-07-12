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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The core resilience requirement: N parallel transfers hitting the same
 * source account through real HTTP, a real Spring context and a real
 * Postgres instance - final balance must be correct to the last cent, the
 * account must never be observed negative, and no entry may be lost, all
 * driven by optimistic locking + retry rather than any explicit lock.
 */
class TransferConcurrencyIT extends AbstractIntegrationTest {

    @Test
    void allTransfersSucceedWhenFundsExactlyCoverThem() throws Exception {
        int concurrentRequests = 30;
        BigDecimal amount = new BigDecimal("10.00");
        BigDecimal opening = amount.multiply(BigDecimal.valueOf(concurrentRequests));

        String token = registerNewUserAndGetToken();
        UUID from = openAccount(token, AccountType.ASSET, opening);
        UUID to = openAccount(token, AccountType.ASSET, BigDecimal.ZERO);

        List<ResponseEntity<TransferResponse>> results = runConcurrentTransfers(token, from, to, amount, concurrentRequests);

        assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));

        AccountResponse fromAccount = getAccount(token, from);
        AccountResponse toAccount = getAccount(token, to);
        assertThat(fromAccount.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fromAccount.balance().signum()).isGreaterThanOrEqualTo(0);
        assertThat(toAccount.balance()).isEqualByComparingTo(opening);

        assertThat(totalEntryCount(token, from, to)).isEqualTo(2L * concurrentRequests);
    }

    @Test
    void concurrentTransfersNeverDriveBalanceNegativeWhenFundsAreInsufficientForAll() throws Exception {
        int concurrentRequests = 20;
        int affordable = concurrentRequests / 2;
        BigDecimal amount = new BigDecimal("10.00");
        BigDecimal opening = amount.multiply(BigDecimal.valueOf(affordable));

        String token = registerNewUserAndGetToken();
        UUID from = openAccount(token, AccountType.ASSET, opening);
        UUID to = openAccount(token, AccountType.ASSET, BigDecimal.ZERO);

        List<ResponseEntity<TransferResponse>> results = runConcurrentTransfers(token, from, to, amount, concurrentRequests);

        long succeeded = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long rejected = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();

        assertThat(succeeded).isEqualTo(affordable);
        assertThat(rejected).isEqualTo(concurrentRequests - affordable);

        AccountResponse fromAccount = getAccount(token, from);
        AccountResponse toAccount = getAccount(token, to);
        assertThat(fromAccount.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fromAccount.balance().signum()).isGreaterThanOrEqualTo(0);
        assertThat(toAccount.balance()).isEqualByComparingTo(opening);

        assertThat(totalEntryCount(token, from, to)).isEqualTo(2L * affordable);
    }

    private List<ResponseEntity<TransferResponse>> runConcurrentTransfers(
            String token, UUID from, UUID to, BigDecimal amount, int concurrentRequests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResponseEntity<TransferResponse>>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            String idempotencyKey = UUID.randomUUID().toString();
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return transfer(token, from, to, amount, idempotencyKey);
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
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private long totalEntryCount(String token, UUID from, UUID to) {
        Instant epoch = Instant.EPOCH;
        Instant future = Instant.now().plusSeconds(60);
        return statement(token, from, epoch, future).lines().size()
                + statement(token, to, epoch, future).lines().size();
    }
}
