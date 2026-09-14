-- Freeze the actual deliverable selected when an acceptance activity is initialized.
-- Do not infer or rewrite historical bindings from mutable codes.
ALTER TABLE acc_acceptance
    ADD COLUMN deliverable_id BIGINT NULL COMMENT 'Frozen deliverable instance identity',
    ADD INDEX idx_acceptance_deliverable (tenant_id, deliverable_id);
