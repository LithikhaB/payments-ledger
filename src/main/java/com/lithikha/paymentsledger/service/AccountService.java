package com.lithikha.paymentsledger.service;

import com.lithikha.paymentsledger.api.ApiException;
import com.lithikha.paymentsledger.domain.Account;
import com.lithikha.paymentsledger.repo.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransferService transfers;

    public AccountService(AccountRepository accounts, TransferService transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    public Account create(String ownerName, String currency) {
        fundingAccount(currency); // rejects unsupported currencies
        return accounts.save(new Account(ownerName, currency));
    }

    public Account get(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }

    /** A deposit is just a transfer from the system funding account, so it stays double-entry + idempotent. */
    public TransferService.Result deposit(String idempotencyKey, UUID accountId, long amount) {
        Account target = get(accountId);
        Account funding = fundingAccount(target.getCurrency());
        return transfers.transfer(idempotencyKey,
                new TransferService.Command(funding.getId(), target.getId(), amount, target.getCurrency()));
    }

    private Account fundingAccount(String currency) {
        return accounts.findFirstByCurrencyAndAllowNegativeTrue(currency)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unsupported currency: " + currency));
    }
}