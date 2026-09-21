package com.lithikha.paymentsledger.api;

import com.lithikha.paymentsledger.api.Dtos.*;
import com.lithikha.paymentsledger.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) { this.transfers = transfers; }

    @PostMapping
    @Operation(summary = "Create a transfer (idempotent)",
            description = "Send a unique Idempotency-Key header. Same key + same body replays the original result (200). "
                    + "Same key + different body returns 422. Amounts are in minor units.")
    public ResponseEntity<TransferResponse> create(@RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody TransferRequest req) {
        return toResponse(transfers.transfer(key,
                new TransferService.Command(req.fromAccountId(), req.toAccountId(), req.amount(), req.currency())));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transfers.get(id));
    }

    static ResponseEntity<TransferResponse> toResponse(TransferService.Result r) {
        return ResponseEntity.status(r.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(r.replayed()))
                .body(TransferResponse.from(r));
    }
}