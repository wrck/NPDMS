-- Q-PROJECT-RESTART-RESULT-20261020: separate current requirements from retained restart history.
-- Existing requirements remain current; no project or delivery result is reset by this migration.
ALTER TABLE plt_delivery_requirement
    ADD COLUMN archived_reopen_snapshot_id BIGINT NOT NULL DEFAULT 0
        COMMENT '0=current; otherwise governance REOPEN snapshot retaining this history',
    DROP INDEX uk_plt_delivery_req,
    ADD UNIQUE KEY uk_plt_delivery_req
        (tenant_id, owner_module, entity_type, entity_id, type_code, archived_reopen_snapshot_id);
