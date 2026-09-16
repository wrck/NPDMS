-- =============================================================
-- V267__add_lowcode_publish_approval_chain.sql
-- 低代码发布多级审批链（借鉴 OutSystems LifeTime 多级审批）。
--
-- <p>新增审批链配置表 low_approval_chain，支持按 configType
-- 配置多级审批（levels JSON 数组：[{level, approverRole, name}]）。
-- 同时为发布记录表 low_publish_record 增加当前审批级别与审批链 ID 字段，
-- 向后兼容：无审批链时仍走单步审批（SUBMITTED → PUBLISHED）。</p>
-- =============================================================

-- 多级审批链配置表
CREATE TABLE IF NOT EXISTS `low_approval_chain` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_type`     VARCHAR(32)  NOT NULL                COMMENT '配置类型: FORM/LIST/ENTITY/...',
    `name`            VARCHAR(128) NOT NULL                COMMENT '审批链名称',
    `levels`          JSON         NOT NULL                COMMENT '审批级别 JSON: [{level:1, approverRole:admin, name:"主管审批"},{level:2, approverRole:manager, name:"经理审批"}]',
    `enabled`         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码发布多级审批链';

-- 发布记录表增加当前审批级别字段
ALTER TABLE `low_publish_record` ADD COLUMN `current_level` INT NULL COMMENT '当前审批级别' AFTER `status`;
ALTER TABLE `low_publish_record` ADD COLUMN `approval_chain_id` BIGINT NULL COMMENT '审批链 ID' AFTER `current_level`;

-- 审批链管理权限：剔除（源工程 sys_permission 初始化段，权限对齐由 yudao system_menu 承担）
