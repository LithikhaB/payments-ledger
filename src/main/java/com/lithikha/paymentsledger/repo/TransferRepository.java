package com.lithikha.paymentsledger.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lithikha.paymentsledger.domain.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String key);
    boolean existsByIdempotencyKey(String key);
}