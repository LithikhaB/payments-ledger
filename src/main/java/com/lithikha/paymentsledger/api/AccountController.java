package com.lithikha.paymentsledger.api;

import com.lithikha.paymentsledger.api.Dtos.*;
import com.lithikha.paymentsledger.service.AccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest req) {
        return AccountResponse.from(accounts.create(req.ownerName(), req.currency()));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accounts.get(id));
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransferResponse> deposit(@PathVariable UUID id,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody DepositRequest req) {
        return TransferController.toResponse(accounts.deposit(key, id, req.amount()));
    }
}