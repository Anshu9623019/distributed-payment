-- =====================================================================
-- Distributed Payment Platform - schema
-- Run this against a fresh database (e.g. via Flyway/Liquibase or psql -f)
-- =====================================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    phone VARCHAR(20),
    address TEXT,
    CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE merchants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    business_name VARCHAR(150) NOT NULL,
    api_key VARCHAR(64) UNIQUE NOT NULL,
    api_secret VARCHAR(128) NOT NULL,
    webhook_url VARCHAR(255),
    settlement_account VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_merchants_api_key ON merchants(api_key);

-- Fixed: owner_id alone was UNIQUE, which breaks the moment a CUSTOMER and a
-- MERCHANT both have owner_id = 1 (each ID sequence starts at 1 independently).
-- The uniqueness constraint has to be on the (owner_id, owner_type) pair.
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    owner_type VARCHAR(20) NOT NULL, -- 'CUSTOMER' or 'MERCHANT'
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_wallet_owner UNIQUE (owner_id, owner_type)
);

CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    payment_id VARCHAR(50),
    entry_type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount NUMERIC(18, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_payment_id ON ledger_entries(payment_id);

-- "owner id" here means Customer.id or Merchant.id — NOT wallets.id. It's
-- ambiguous on its own (a customer #3 and a merchant #3 both exist), which is
-- why the owner_type columns travel alongside it. No FK to wallets(id): the
-- true reference is polymorphic (customers OR merchants), which SQL can't
-- express as a single FK, so this is enforced at the application layer.
CREATE TABLE payments (
    id VARCHAR(50) PRIMARY KEY,
    sender_owner_id BIGINT NOT NULL,
    sender_owner_type VARCHAR(20) NOT NULL,
    receiver_owner_id BIGINT NOT NULL,
    receiver_owner_type VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED', 'REVERSED'
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    error_message VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
CREATE INDEX idx_payments_sender ON payments(sender_owner_id, sender_owner_type);
CREATE INDEX idx_payments_receiver ON payments(receiver_owner_id, receiver_owner_type);

-- New: backs the transactional outbox pattern used by PaymentService and
-- WalletEventConsumer so a DB commit and a Kafka publish can never diverge.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PUBLISHED', 'FAILED'
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

-- New: fraud engine audit trail + manual review queue. payment_id is
-- nullable because a BLOCK decision happens before any Payment row exists.
CREATE TABLE fraud_alerts (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(50),
    sender_owner_id BIGINT NOT NULL,
    sender_owner_type VARCHAR(20) NOT NULL,
    rule_triggered VARCHAR(30) NOT NULL, -- 'VELOCITY_LIMIT', 'LARGE_AMOUNT', 'BLACKLISTED_IP'
    decision VARCHAR(20) NOT NULL,        -- 'MANUAL_REVIEW', 'BLOCK'
    details VARCHAR(500) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fraud_alerts_unresolved ON fraud_alerts(resolved, created_at);

-- New: one row per payout attempt (a merchant wallet sweep -> bank transfer).
CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL, -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    bank_reference VARCHAR(100),
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_settlement_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)
);

CREATE INDEX idx_settlements_merchant ON settlements(merchant_id, created_at);