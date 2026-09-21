CREATE TABLE accounts (
    id             UUID         PRIMARY KEY,
    owner_name     VARCHAR(100) NOT NULL,
    currency       VARCHAR(3)   NOT NULL,
    balance        BIGINT       NOT NULL DEFAULT 0,      -- minor units (paise/cents)
    allow_negative BOOLEAN      NOT NULL DEFAULT FALSE,  -- only for system funding accounts
    version        BIGINT       NOT NULL DEFAULT 0,      -- optimistic lock
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_balance CHECK (allow_negative OR balance >= 0)  -- DB-level safety net
);

CREATE TABLE transfers (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    from_account_id UUID         NOT NULL REFERENCES accounts(id),
    to_account_id   UUID         NOT NULL REFERENCES accounts(id),
    amount          BIGINT       NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_distinct_accounts CHECK (from_account_id <> to_account_id)
);

CREATE TABLE ledger_entries (
    id          UUID        PRIMARY KEY,
    transfer_id UUID        NOT NULL REFERENCES transfers(id),
    account_id  UUID        NOT NULL REFERENCES accounts(id),
    direction   VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount      BIGINT      NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_account  ON ledger_entries(account_id, created_at);
CREATE INDEX idx_entries_transfer ON ledger_entries(transfer_id);