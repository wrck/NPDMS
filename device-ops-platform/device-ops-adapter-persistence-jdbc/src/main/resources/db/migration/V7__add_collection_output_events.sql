CREATE TABLE device_ops_collection_output_event (
    task_id VARCHAR(80) NOT NULL,
    target_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    stream_type VARCHAR(16) NOT NULL,
    content CLOB NOT NULL,
    received_bytes BIGINT NOT NULL,
    page_count INTEGER NOT NULL,
    output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (task_id, sequence_no),
    CONSTRAINT fk_device_ops_output_collection
        FOREIGN KEY (task_id) REFERENCES device_ops_collection(task_id),
    CONSTRAINT fk_device_ops_output_target
        FOREIGN KEY (target_id) REFERENCES device_ops_collection_target(id)
);

CREATE INDEX idx_device_ops_output_target_sequence
    ON device_ops_collection_output_event(target_id, sequence_no);

ALTER TABLE device_ops_collection
    ADD COLUMN output_sequence BIGINT NOT NULL DEFAULT 0;
