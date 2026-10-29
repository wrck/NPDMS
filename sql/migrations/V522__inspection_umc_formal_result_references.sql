-- UMC formal offline workflow. Existing platform rows retain their original rule FKs and history.
ALTER TABLE srv_inspection_attempt
  ADD COLUMN provider_code varchar(16) NOT NULL DEFAULT 'PLATFORM' COMMENT 'Frozen judgement provider',
  ADD CONSTRAINT ck_ins_attempt_provider CHECK (provider_code IN ('PLATFORM','UMC'));

ALTER TABLE srv_inspection_detection_result
  MODIFY COLUMN rule_snapshot_id bigint NULL,
  MODIFY COLUMN rule_revision_id bigint NULL,
  ADD COLUMN source_code varchar(16) NOT NULL DEFAULT 'PLATFORM',
  ADD COLUMN source_report_id bigint NULL,
  ADD COLUMN external_rule_id varchar(64) NULL,
  ADD UNIQUE KEY uk_ins_result_umc (tenant_id,source_report_id,external_rule_id),
  ADD CONSTRAINT fk_ins_result_source_report FOREIGN KEY (tenant_id,task_id,source_report_id)
    REFERENCES srv_inspection_report_revision(tenant_id,task_id,id),
  ADD CONSTRAINT ck_ins_result_provider CHECK (
    (source_code='PLATFORM' AND rule_snapshot_id IS NOT NULL AND rule_revision_id IS NOT NULL
      AND source_report_id IS NULL AND external_rule_id IS NULL)
    OR (source_code='UMC' AND rule_snapshot_id IS NULL AND rule_revision_id IS NULL
      AND source_report_id IS NOT NULL AND external_rule_id IS NOT NULL AND external_rule_id<>''));
-- Original execution/snapshot/report/annotation FKs, outcome CHECK and immutable history triggers remain.

-- Attempt providers are selected only when creating a new attempt. No provider-update API is exposed.
-- Tenant/task/attempt/execution/device/log/rule membership is validated by the workflow transaction
-- and canonical report snapshot loader before registering or adopting formal results.
