ALTER TABLE device_ops_outbox ADD COLUMN destination VARCHAR(2000);
ALTER TABLE device_ops_outbox ADD COLUMN lease_owner VARCHAR(200);
ALTER TABLE device_ops_outbox ADD COLUMN lease_until TIMESTAMP;
ALTER TABLE device_ops_outbox ADD COLUMN dead_lettered_at TIMESTAMP;
