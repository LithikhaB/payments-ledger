package com.lithikha.paymentsledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.lithikha.paymentsledger.api.ApiException;
import com.lithikha.paymentsledger.domain.Account;
import com.lithikha.paymentsledger.repo.AccountRepository;
import com.lithikha.paymentsledger.repo.LedgerEntryRepository;
import com.lithikha.paymentsledger.service.AccountService;
import com.lithikha.paymentsledger.service.TransferService;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerEntryRepository ledgerRepository;

    /** Runs after EVERY test: money is conserved and every cached balance matches the ledger. */
    @AfterEach
    void ledgerInvariants() {
        List<Account> all = accountRepository.findAll();
        assertEquals(0L, all.stream().mapToLong(Account::getBalance).sum(), "money was created or destroyed");
        for (Account a : all) {
            assertEquals(ledgerRepository.netBalance(a.getId()), a.getBalance(),
                    "cached balance drifted from the ledger for " + a.getId());
        }
    }

    // ---------------------------------------------------------------- core behaviour

    @Test
    void transferMovesMoneyAndWritesBalancedEntries() {
        Account alice = funded(10_000);
        Account bob = accountService.create("bob", "INR");

        TransferService.Result result = transferService.transfer(newKey(), cmd(alice, bob, 2_500));

        assertFalse(result.replayed());
        assertEquals(2, result.entries().size());
        assertEquals(7_500, balance(alice));
        assertEquals(2_500, balance(bob));
    }

    @Test
    void insufficientFundsIs422AndChangesNothing() {
        Account alice = funded(1_000);
        Account bob = accountService.create("bob", "INR");

        ApiException ex = assertThrows(ApiException.class,
                () -> transferService.transfer(newKey(), cmd(alice, bob, 5_000)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals(1_000, balance(alice));
        assertEquals(0, balance(bob));
    }

    // ---------------------------------------------------------------- idempotency over HTTP

    @Test
    void replayReturns200WithIdenticalBodyAndMovesMoneyOnce() throws Exception {
        Account alice = funded(10_000);
        Account bob = accountService.create("bob", "INR");
        String key = newKey();

        MvcResult first = postTransfer(key, alice, bob, 2_500);
        MvcResult replay = postTransfer(key, alice, bob, 2_500);

        assertEquals(201, first.getResponse().getStatus());
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals("true", replay.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals(first.getResponse().getContentAsString(), replay.getResponse().getContentAsString());
        assertEquals(7_500, balance(alice));
    }

    @Test
    void reusingKeyWithDifferentPayloadIs422() throws Exception {
        Account alice = funded(10_000);
        Account bob = accountService.create("bob", "INR");
        String key = newKey();

        postTransfer(key, alice, bob, 1_000);
        MvcResult clash = postTransfer(key, alice, bob, 2_000);

        assertEquals(422, clash.getResponse().getStatus());
        assertEquals(9_000, balance(alice));
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        Account alice = funded(1_000);
        Account bob = accountService.create("bob", "INR");

        MvcResult result = mvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(alice, bob, 100))).andReturn();

        assertEquals(400, result.getResponse().getStatus());
    }

    @Test
    void unknownAccountIs404() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/accounts/" + UUID.randomUUID())).andReturn();
        assertEquals(404, result.getResponse().getStatus());
    }

    @Test
    void openApiDocsAreServed() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("/api/v1/transfers"));
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void concurrentDuplicatesApplyExactlyOnce() throws Exception {
        Account alice = funded(10_000);
        Account bob = accountService.create("bob", "INR");
        String key = newKey();

        List<TransferService.Result> results =
                runConcurrently(16, () -> transferService.transfer(key, cmd(alice, bob, 1_000)));

        assertEquals(1, results.stream().map(r -> r.transfer().getId()).distinct().count());
        assertEquals(1, results.stream().filter(r -> !r.replayed()).count()); // exactly one winner
        assertEquals(9_000, balance(alice));
        assertEquals(1_000, balance(bob));
    }

    @Test
    void concurrentDistinctTransfersConserveMoney() throws Exception {
        Account alice = funded(100_000);
        Account bob = accountService.create("bob", "INR");

        List<Boolean> outcomes = runConcurrently(20, () -> {
            try {
                transferService.transfer(newKey(), cmd(alice, bob, 1_000));
                return true;
            } catch (ApiException e) {
                assertEquals(HttpStatus.CONFLICT, e.getStatus()); // only allowed failure: retries exhausted
                return false;
            }
        });

        long ok = outcomes.stream().filter(b -> b).count();
        assertTrue(ok > 0);
        assertEquals(100_000 - ok * 1_000, balance(alice));
        assertEquals(ok * 1_000, balance(bob));
    }

    // ---------------------------------------------------------------- helpers

    private Account funded(long amount) {
        Account a = accountService.create("acct-" + UUID.randomUUID().toString().substring(0, 8), "INR");
        accountService.deposit(newKey(), a.getId(), amount);
        return a;
    }

    private long balance(Account a) {
        return accountService.get(a.getId()).getBalance();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static TransferService.Command cmd(Account from, Account to, long amount) {
        return new TransferService.Command(from.getId(), to.getId(), amount, "INR");
    }

    private static String transferJson(Account from, Account to, long amount) {
        return """
                {"fromAccountId":"%s","toAccountId":"%s","amount":%d,"currency":"INR"}
                """.formatted(from.getId(), to.getId(), amount);
    }

    private MvcResult postTransfer(String key, Account from, Account to, long amount) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(from, to, amount))).andReturn();
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
