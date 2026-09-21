package com.lithikha.paymentsledger.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lithikha.paymentsledger.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransferId(UUID transferId);

    // Source of truth: credits minus debits. Used to reconcile the cached accounts.balance.
    @Query(value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0) AS BIGINT)
            FROM ledger_entries WHERE account_id = :accountId
            """, nativeQuery = true)
    long netBalance(@Param("accountId") UUID accountId);
}