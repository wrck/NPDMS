ALTER TABLE device_ops_collection ALTER COLUMN project_key DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN project_name DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN project_code DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN device_key DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN device_name DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN vendor DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN model DROP NOT NULL;
ALTER TABLE device_ops_collection_target ADD COLUMN protocol VARCHAR(20) NOT NULL DEFAULT 'SSH2';
ALTER TABLE device_ops_collection_target ADD COLUMN telnet_prompts_json CLOB;
