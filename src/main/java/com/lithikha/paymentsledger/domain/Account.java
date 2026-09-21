package com.lithikha.paymentsledger.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Version // optimistic locking: UPDATE ... WHERE id=? AND version=?
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); // Postgres precision

    protected Account() {}

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
    }

    public void debit(long amount)  { balance -= amount; }
    public void credit(long amount) { balance += amount; }

    public UUID getId() { return id; }
    public String getOwnerName() { return ownerName; }
    public String getCurrency() { return currency; }
    public long getBalance() { return balance; }
    public boolean isAllowNegative() { return allowNegative; }
    public Instant getCreatedAt() { return createdAt; }
}