package com.lithikha.paymentsledger.service;

import com.lithikha.paymentsledger.api.ApiException;
import com.lithikha.paymentsledger.domain.LedgerEntry;
import com.lithikha.paymentsledger.domain.Transfer;
import com.lithikha.paymentsledger.repo.LedgerEntryRepository;
import com.lithikha.paymentsledger.repo.TransferRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TransferService {

    public record Command(UUID fromAccountId, UUID toAccountId, long amount, String currency) {}
    public record Result(Transfer transfer, List<LedgerEntry> entries, boolean replayed) {}

    private static final int MAX_ATTEMPTS = 10;

    private final TransferExecutor executor;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;

    public TransferService(TransferExecutor executor, TransferRepository transfers, LedgerEntryRepository entries) {
        this.executor = executor;
        this.transfers = transfers;
        this.entries = entries;
    }

    public Result transfer(String idempotencyKey, Command cmd) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-100 characters");
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return executor.execute(idempotencyKey, cmd);
            } catch (ConcurrencyFailureException e) {
                // optimistic-lock conflict (or deadlock victim): another txn touched our accounts -> retry
            } catch (DataIntegrityViolationException e) {
                // A concurrent request with the same key won the insert -> retry will replay it.
                // If the key doesn't exist, it's a genuine constraint violation: don't hide it.
                if (!transfers.existsByIdempotencyKey(idempotencyKey)) throw e;
            }
            if (attempt >= MAX_ATTEMPTS) {
                throw new ApiException(HttpStatus.CONFLICT, "Too much contention, please retry");
            }
            sleepWithJitter(attempt);
        }
    }

    public Result get(UUID id) {
        Transfer t = transfers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transfer not found: " + id));
        return new Result(t, entries.findByTransferId(id), false);
    }

    private static void sleepWithJitter(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 10L * attempt + 10));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }
}