-- Store the accuracy check with each run, so the dashboard can show it later.
-- Nullable on purpose: a run on real uploaded data has no answer key.
ALTER TABLE recon_runs
    ADD COLUMN seeded_breaks INT,
    ADD COLUMN detected      INT,
    ADD COLUMN missed        INT,
    ADD COLUMN false_alarms  INT;