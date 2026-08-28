ALTER TABLE plt_credential_grant
    ADD COLUMN command_template_id VARCHAR(64) NULL AFTER protocol;

ALTER TABLE plt_collection_task
    ADD COLUMN temporary_username VARCHAR(128) NULL AFTER grant_snapshot_id;
