CREATE TABLE device_ops_collection_submission_guard (
    namespace VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    cancelled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, idempotency_key)
);

INSERT INTO device_ops_collection_submission_guard (namespace, idempotency_key, cancelled)
SELECT namespace, idempotency_key, FALSE FROM device_ops_collection;
