-- Chronologically replayed from d2d1765ffe14233d8041d4b10c871d246c4a9183 (prereq-parallel-check-kKiAdn), original sql/migrations/V105__device_ops_credentials_and_dispatch.sql.
-- Renumbered after current master; Feature status is not promoted by this receipt.

ALTER TABLE plt_credential_grant
    ADD COLUMN command_template_id VARCHAR(64) NULL AFTER protocol;

ALTER TABLE plt_collection_task
    ADD COLUMN temporary_username VARCHAR(128) NULL AFTER grant_snapshot_id;
