package com.lithikha.paymentsledger.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lithikha.paymentsledger.domain.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findFirstByCurrencyAndAllowNegativeTrue(String currency); // system funding account
}