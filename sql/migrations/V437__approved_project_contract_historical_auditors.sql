-- Whole-table adjudication: reuse the existing 17 fields; absent original auditors remain NULL.
-- Normal relationship commands continue recording their authenticated actor.
ALTER TABLE com_project_contract_relation
    MODIFY COLUMN creator VARCHAR(64) NULL,
    MODIFY COLUMN updater VARCHAR(64) NULL;
