-- Associate parse tasks with the business request that initiated their source collection.
-- Columns stay nullable: standalone parse tasks and historical rows have no collection origin.
ALTER TABLE device_ops_parse_task ADD COLUMN external_request_id VARCHAR(200) NULL;
ALTER TABLE device_ops_parse_task ADD COLUMN activity_type VARCHAR(100) NULL;

CREATE INDEX idx_device_ops_parse_task_business_ref
    ON device_ops_parse_task (caller_namespace, external_request_id);
