-- Historical tasks have no captured submission; never infer one from current configuration.
ALTER TABLE device_ops_collection ADD COLUMN submission_snapshot_json CLOB NULL;
