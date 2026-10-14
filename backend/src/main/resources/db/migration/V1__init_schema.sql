-- =====================================================================
-- V1__init_schema.sql
-- Initial schema for the payment reconciliation engine.
-- Money is always stored as BIGINT cents (never floating point).
-- =====================================================================


-- ---------------------------------------------------------------------
-- SOURCE 1: What the payment processor (Stripe) says happened.
-- Modeled on Stripe "balance transactions": charges, refunds and
-- payouts live in one list.
-- ---------------------------------------------------------------------
CREATE TABLE processor_transactions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id         TEXT        NOT NULL UNIQUE,   -- e.g. ch_001, po_101; UNIQUE = idempotent ingestion
    type                TEXT        NOT NULL CHECK (type IN ('CHARGE', 'REFUND', 'PAYOUT')),
    gross_amount        BIGINT      NOT NULL,          -- cents the customer paid (negative for refunds)
    fee_amount          BIGINT      NOT NULL DEFAULT 0,
    net_amount          BIGINT      NOT NULL,          -- what the business actually receives
    currency            CHAR(3)     NOT NULL DEFAULT 'USD',
    payout_external_id  TEXT,                          -- which payout bundled this charge (NULL for payout rows)
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_processor_net CHECK (net_amount = gross_amount - fee_amount)
);

-- Pass 3 (payout -> bank) needs "all charges in payout X" quickly.
CREATE INDEX idx_processor_payout ON processor_transactions (payout_external_id);


-- ---------------------------------------------------------------------
-- SOURCE 2: What the company's own books (ledger) say.
-- processor_ref is deliberately NOT a foreign key and NOT unique:
-- references to missing records and duplicate references are exactly
-- the problems we need to detect, so the database must accept them.
-- ---------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id    TEXT        NOT NULL UNIQUE,        -- e.g. L1
    processor_ref  TEXT,                               -- the Stripe id this entry claims to record
    amount         BIGINT      NOT NULL,               -- cents, gross
    currency       CHAR(3)     NOT NULL DEFAULT 'USD',
    description    TEXT,
    entry_date     TIMESTAMPTZ NOT NULL
);

-- Pass 1 (exact ID match) looks ledger entries up by processor_ref.
CREATE INDEX idx_ledger_processor_ref ON ledger_entries (processor_ref);


-- ---------------------------------------------------------------------
-- SOURCE 3: What the company's bank statement says.
-- Banks report a posting DATE (no time) and little detail.
-- ---------------------------------------------------------------------
CREATE TABLE bank_transactions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id  TEXT    NOT NULL UNIQUE,              -- bank reference number
    description  TEXT,                                 -- e.g. 'STRIPE TRANSFER', 'RENT PAYMENT'
    amount       BIGINT  NOT NULL,                     -- cents
    currency     CHAR(3) NOT NULL DEFAULT 'USD',
    posted_date  DATE    NOT NULL
);

-- Pass 3 looks for "a deposit of exactly this amount within a date window".
CREATE INDEX idx_bank_amount_date ON bank_transactions (amount, posted_date);


-- ---------------------------------------------------------------------
-- RESULTS: one row per execution of the matching engine (audit trail).
-- ---------------------------------------------------------------------
CREATE TABLE recon_runs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status        TEXT        NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);


-- ---------------------------------------------------------------------
-- RESULTS: a group of related records and what we concluded about them.
-- A group is either MATCHED (records agree) or a BREAK (they don't).
-- ---------------------------------------------------------------------
CREATE TABLE recon_groups (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id            BIGINT NOT NULL REFERENCES recon_runs (id) ON DELETE CASCADE,
    outcome           TEXT   NOT NULL CHECK (outcome IN ('MATCHED', 'BREAK')),
    match_method      TEXT   CHECK (match_method IN ('EXACT_ID', 'AMOUNT_DATE', 'PAYOUT_BATCH')),
    break_type        TEXT   CHECK (break_type IN (
                                 'MISSING_IN_LEDGER',
                                 'MISSING_IN_PROCESSOR',
                                 'DUPLICATE',
                                 'AMOUNT_MISMATCH',
                                 'IN_TRANSIT',
                                 'MISSING_IN_BANK',
                                 'UNEXPLAINED')),
    amount_impact     BIGINT NOT NULL DEFAULT 0,       -- cents of money affected by this break

    -- Filled in by the AI explanation layer (only for breaks).
    ai_cause          TEXT,
    ai_confidence     TEXT CHECK (ai_confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    ai_suggested_fix  TEXT,
    explained_at      TIMESTAMPTZ,

    -- A MATCHED group never has a break_type; a BREAK always has one.
    CONSTRAINT chk_outcome_break_type CHECK (
        (outcome = 'MATCHED' AND break_type IS NULL) OR
        (outcome = 'BREAK'   AND break_type IS NOT NULL)
    )
);

CREATE INDEX idx_groups_run_outcome ON recon_groups (run_id, outcome);


-- ---------------------------------------------------------------------
-- RESULTS: which records belong to which group.
-- record_id points into processor_transactions, ledger_entries or
-- bank_transactions depending on `source`, so it cannot be a foreign
-- key; the application guarantees it.
-- The primary key (run_id, source, record_id) means the DATABASE
-- guarantees a record is placed in at most one group per run.
-- ---------------------------------------------------------------------
CREATE TABLE recon_group_items (
    run_id     BIGINT NOT NULL REFERENCES recon_runs (id) ON DELETE CASCADE,
    group_id   BIGINT NOT NULL REFERENCES recon_groups (id) ON DELETE CASCADE,
    source     TEXT   NOT NULL CHECK (source IN ('PROCESSOR', 'LEDGER', 'BANK')),
    record_id  BIGINT NOT NULL,
    PRIMARY KEY (run_id, source, record_id)
);

CREATE INDEX idx_group_items_group ON recon_group_items (group_id);


-- ---------------------------------------------------------------------
-- ANSWER KEY: breaks the data generator planted on purpose.
-- Comparing this table with recon_groups measures engine accuracy.
-- ---------------------------------------------------------------------
CREATE TABLE seeded_breaks (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    break_type    TEXT   NOT NULL,
    external_ids  TEXT[] NOT NULL,                     -- e.g. {ch_002, L2, L3}
    note          TEXT                                 -- e.g. 'webhook delivered twice'
);
