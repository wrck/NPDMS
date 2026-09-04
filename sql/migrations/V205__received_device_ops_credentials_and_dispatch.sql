-- Chronologically received from the ACC/INT/CUT source branches.
-- Original path: sql/migrations/V105__device_ops_credentials_and_dispatch.sql
-- Active Flyway version reassigned after master@220486237b9570ab3d2b0663df39c89be2a5ec69.

ALTER TABLE plt_credential_grant
    ADD COLUMN command_template_id VARCHAR(64) NULL AFTER protocol;

ALTER TABLE plt_collection_task
    ADD COLUMN temporary_username VARCHAR(128) NULL AFTER grant_snapshot_id;
