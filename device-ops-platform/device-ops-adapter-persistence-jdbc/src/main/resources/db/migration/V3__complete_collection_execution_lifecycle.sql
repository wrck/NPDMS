ALTER TABLE device_ops_collection_target ADD COLUMN lease_owner VARCHAR(200);
ALTER TABLE device_ops_collection_target ADD COLUMN lease_until TIMESTAMP;
ALTER TABLE device_ops_collection_target ADD COLUMN parsed_facts_json CLOB;
