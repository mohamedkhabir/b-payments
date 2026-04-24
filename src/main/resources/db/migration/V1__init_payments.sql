-- V1__init_payments.sql

CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL       PRIMARY KEY,
    reference_id    VARCHAR(100)    NOT NULL,
    amount          NUMERIC(19, 4)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    debtor_name     VARCHAR(255)    NOT NULL,
    debtor_iban     VARCHAR(34)     NOT NULL,
    creditor_iban   VARCHAR(34)     NOT NULL,
    value_date      DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    event_timestamp TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payments_reference_id UNIQUE (reference_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PROCESSING', 'SETTLED', 'REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_reference_id ON payments (reference_id);

COMMENT ON TABLE payments IS 'Persisted payment events from the BancoLuso payments platform';
COMMENT ON COLUMN payments.reference_id    IS 'Unique business key from the external payments platform';
COMMENT ON COLUMN payments.event_timestamp IS 'Timestamp from the source system → used for out-of-order event detection';
COMMENT ON COLUMN payments.status          IS 'Current lifecycle status: PENDING → PROCESSING → SETTLED | REJECTED';
