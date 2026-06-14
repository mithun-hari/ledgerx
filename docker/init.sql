-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Wallets table
CREATE TABLE IF NOT EXISTS wallets (
    id         UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID          NOT NULL REFERENCES users(id),
    currency   VARCHAR(3)       NOT NULL,
    balance    NUMERIC(20,8) NOT NULL DEFAULT 0,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, currency)
);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    initiator_id    UUID          NOT NULL REFERENCES users(id),
    type            VARCHAR(30)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    amount          NUMERIC(20,8) NOT NULL,
    currency        VARCHAR(3)       NOT NULL,
    idempotency_key VARCHAR(255)  UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Ledger entries (double-entry core)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id       UUID          NOT NULL REFERENCES wallets(id),
    transaction_id  UUID          NOT NULL REFERENCES transactions(id),
    entry_type      VARCHAR(10)   NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(20,8) NOT NULL,
    currency        VARCHAR(3)       NOT NULL,
    running_balance NUMERIC(20,8) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Indexes for fast queries
CREATE INDEX idx_wallets_user     ON wallets(user_id);
CREATE INDEX idx_tx_initiator     ON transactions(initiator_id);
CREATE INDEX idx_tx_status        ON transactions(status);
CREATE INDEX idx_ledger_wallet    ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_tx        ON ledger_entries(transaction_id);