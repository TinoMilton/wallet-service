CREATE TABLE wallet (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE operation (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES operation(id),
    wallet_id UUID NOT NULL REFERENCES wallet(id),
    seq BIGINT NOT NULL,
    amount_signed NUMERIC(19, 2) NOT NULL CHECK (amount_signed <> 0),
    balance_after NUMERIC(19, 2) NOT NULL CHECK (balance_after >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (wallet_id, seq)
);

CREATE INDEX ledger_entry_wallet_created_at_idx ON ledger_entry (wallet_id, created_at);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL
);

CREATE INDEX outbox_event_unpublished_occurred_at_idx ON outbox_event (occurred_at)
    WHERE published_at IS NULL;
