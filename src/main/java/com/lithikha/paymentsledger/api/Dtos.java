package com.lithikha.paymentsledger.api;

import com.lithikha.paymentsledger.domain.*;
import com.lithikha.paymentsledger.service.TransferService;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record CreateAccountRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

    /** amount is in minor units (paise/cents) */
    public record DepositRequest(@Positive long amount) {}

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @Positive long amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}

    public record AccountResponse(UUID id, String ownerName, String currency, long balance, Instant createdAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getOwnerName(), a.getCurrency(), a.getBalance(), a.getCreatedAt());
        }
    }

    public record EntryResponse(UUID accountId, Direction direction, long amount) {}

    public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, long amount,
                                   String currency, TransferStatus status, Instant createdAt,
                                   List<EntryResponse> entries) {
        public static TransferResponse from(TransferService.Result r) {
            Transfer t = r.transfer();
            List<EntryResponse> es = r.entries().stream()
                    .sorted(Comparator.comparing(LedgerEntry::getDirection))
                    .map(e -> new EntryResponse(e.getAccountId(), e.getDirection(), e.getAmount()))
                    .toList();
            return new TransferResponse(t.getId(), t.getFromAccountId(), t.getToAccountId(),
                    t.getAmount(), t.getCurrency(), t.getStatus(), t.getCreatedAt(), es);
        }
    }
}