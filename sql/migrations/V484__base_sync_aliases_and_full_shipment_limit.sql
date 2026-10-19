-- Prepared only. Do not execute without separate database-change authorization.
-- Source uniqueness remains strict; distinct approved originals may resolve to the same target.
ALTER TABLE int_base_sync_binding
  DROP INDEX uk_int_base_sync_binding_target,
  ADD KEY idx_int_base_sync_binding_target (tenant_id,task_id,object_key,target_id);

-- P15's approved whole fb_shipment_barcode source exceeds four million rows.
-- This changes only the upper ceiling. It does not raise any task's registered fuse or run a task.
ALTER TABLE int_base_sync_task
  DROP CHECK ck_int_base_sync_max_rows,
  ADD CONSTRAINT ck_int_base_sync_max_rows CHECK (max_rows BETWEEN 1 AND 5000000);
