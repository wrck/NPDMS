ALTER TABLE device_ops_collection ADD COLUMN created_at TIMESTAMP(6) NULL DEFAULT NULL;
ALTER TABLE device_ops_collection MODIFY COLUMN created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6);
CREATE INDEX idx_collection_management_scope ON device_ops_collection(namespace, project_key, created_at, task_id);
CREATE INDEX idx_collection_management_created ON device_ops_collection(created_at, task_id);
CREATE INDEX idx_collection_target_device ON device_ops_collection_target(device_key, task_id);
