-- =============================================================================
-- P06R I2：模板冻结交付链存储迁入的结构准备。
-- 1) 提交台账幂等键收紧为（租户 + 要求实例 + request_key）：手工提交的 Idempotency-Key
--    只在要求内唯一，不再是全局键；旧 uk (tenant_id, request_key) 会跨要求误回放。
-- 2) 提交台账补"单一当前提交"数据库不变量（对齐 acc source_version 的 current_marker 手法）：
--    CURRENT 状态生成 requirement_id 标记并唯一，取代旧 CURRENT 时先改状态再插入。
-- 3) 材料补归档补偿事实列（acc source_version 的 archive_* 语义随迁到材料行）。
-- =============================================================================

ALTER TABLE `plt_delivery_submission`
    DROP INDEX `uk_plt_delivery_sub`,
    ADD UNIQUE KEY `uk_plt_delivery_sub_request` (`tenant_id`, `requirement_id`, `request_key`),
    ADD COLUMN `current_marker` BIGINT GENERATED ALWAYS AS
        (CASE WHEN `status` = 'CURRENT' THEN `requirement_id` ELSE NULL END) STORED
        COMMENT '当前提交标记（CURRENT 时=requirement_id，其余为 NULL）',
    ADD UNIQUE KEY `uk_plt_delivery_sub_current` (`tenant_id`, `current_marker`);

ALTER TABLE `plt_delivery_material`
    ADD COLUMN `archive_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED'
        COMMENT '归档补偿状态 NOT_REQUIRED/PENDING_COMPENSATION/ARCHIVED/INVALID',
    ADD COLUMN `archive_failure_code` VARCHAR(64) NULL COMMENT '归档失败原因编码',
    ADD COLUMN `archive_retry_count` INT NOT NULL DEFAULT 0 COMMENT '归档补偿重试次数',
    ADD COLUMN `archive_time` DATETIME(3) NULL COMMENT '归档完成时间',
    ADD KEY `idx_plt_delivery_mat_archive` (`tenant_id`, `archive_status`);

-- TEMPLATE_FROZEN 要求承载交付件显示名（CATALOG 要求为 NULL，显示名取类型目录）。
ALTER TABLE `plt_delivery_requirement`
    ADD COLUMN `name` VARCHAR(128) NULL COMMENT '要求显示名（TEMPLATE_FROZEN=交付件名称）' AFTER `type_code`;
