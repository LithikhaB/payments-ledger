package com.lithikha.paymentsledger.service;

import com.lithikha.paymentsledger.api.ApiException;
import com.lithikha.paymentsledger.domain.*;
import com.lithikha.paymentsledger.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransferExecutor {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;

    public TransferExecutor(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
    }

    @Transactional
    public TransferService.Result execute(String key, TransferService.Command cmd) {
        String hash = fingerprint(cmd);

        // 1. Idempotency: seen this key before?
        Optional<Transfer> existing = transfers.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Transfer t = existing.get();
            if (!t.getRequestHash().equals(hash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key was already used with a different request");
            }
            return new TransferService.Result(t, entries.findByTransferId(t.getId()), true);
        }

        // 2. Validate
        if (cmd.fromAccountId().equals(cmd.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account");
        }
        Account from = load(cmd.fromAccountId());
        Account to = load(cmd.toAccountId());
        if (!from.getCurrency().equals(cmd.currency()) || !to.getCurrency().equals(cmd.currency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Currency mismatch");
        }
        if (!from.isAllowNegative() && from.getBalance() < cmd.amount()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }

        // 3. Claim the key. The UNIQUE constraint, not the check above, is what guarantees exactly-once.
        Transfer t = transfers.saveAndFlush(new Transfer(
                key, hash, from.getId(), to.getId(), cmd.amount(), cmd.currency()));

        // 4. Double entry: one debit + one credit of the same amount
        from.debit(cmd.amount());
        to.credit(cmd.amount());
        List<LedgerEntry> written = entries.saveAll(List.of(
                new LedgerEntry(t.getId(), from.getId(), Direction.DEBIT, cmd.amount()),
                new LedgerEntry(t.getId(), to.getId(), Direction.CREDIT, cmd.amount())));

        // 5. Flush now so an optimistic-lock conflict surfaces inside this attempt
        accounts.flush();
        return new TransferService.Result(t, written, false);
    }

    private Account load(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }

    private static String fingerprint(TransferService.Command c) {
        try {
            String raw = c.fromAccountId() + "|" + c.toAccountId() + "|" + c.amount() + "|" + c.currency();
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}