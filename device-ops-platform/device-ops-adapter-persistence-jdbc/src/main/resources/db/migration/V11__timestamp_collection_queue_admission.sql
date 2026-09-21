ALTER TABLE device_ops_collection_target
    ADD COLUMN queued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL;

CREATE INDEX idx_device_ops_collection_target_unclaimed_recovery
    ON device_ops_collection_target (status, lease_owner, queued_at, id);

CREATE INDEX idx_device_ops_collection_target_lease_recovery
    ON device_ops_collection_target (status, lease_until, id);
