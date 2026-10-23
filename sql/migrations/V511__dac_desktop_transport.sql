-- F-INT-012: explicit local transport; legacy rows keep SERVER semantics.
ALTER TABLE plt_collection_request
    ADD COLUMN dispatch_mode VARCHAR(16) NOT NULL DEFAULT 'SERVER',
    ADD COLUMN local_connection_id VARCHAR(128) NULL,
    ADD COLUMN local_connection_version BIGINT NULL,
    ADD COLUMN desktop_source_version BIGINT NULL;

ALTER TABLE int_device_ops_dispatch
    ADD COLUMN dispatch_mode VARCHAR(16) NOT NULL DEFAULT 'SERVER',
    ADD COLUMN actor_id BIGINT NULL,
    ADD COLUMN project_id VARCHAR(64) NULL,
    ADD COLUMN desktop_claimed BIT NOT NULL DEFAULT b'0',
    ADD COLUMN desktop_claim_id VARCHAR(36) NULL,
    ADD COLUMN cancel_requested BIT NOT NULL DEFAULT b'0',
    ADD COLUMN redelivery_requested BIT NOT NULL DEFAULT b'0';

-- No role, menu or dictionary is introduced. Existing collection and credential permissions apply.
