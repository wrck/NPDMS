ALTER TABLE device_ops_parse_task ADD COLUMN requested_release_id VARCHAR(100);
ALTER TABLE device_ops_parse_task ADD COLUMN requested_release_recorded BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE device_ops_parser_payload ADD COLUMN caller_namespace VARCHAR(200);
ALTER TABLE device_ops_parser_payload ADD COLUMN scoped_content LONGBLOB;

CREATE INDEX idx_device_ops_parse_task_input_owner
    ON device_ops_parse_task(caller_namespace, input_payload_id);
