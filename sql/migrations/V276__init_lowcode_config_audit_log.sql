-- =============================================================
-- V276__init_lowcode_config_audit_log.sql
-- 平台配置审计日志（缺口2）
--
-- 借鉴 Mendix/OutSystems 平台审计：记录"谁在何时改了哪个实体/表单/连接器
-- 等配置"，提供 before/after JSON 快照与变更摘要，便于事后追溯与合规审计。
--
-- 8 大配置类型：ENTITY / FORM / LIST / TAB / RELATED_PAGE / MICROFLOW /
-- RULE / CONNECTOR。动作：CREATE / UPDATE / DELETE / PUBLISH / ROLLBACK /
-- PROMOTE。
-- =============================================================

CREATE TABLE IF NOT EXISTS `low_config_audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `actor`           VARCHAR(64)  NOT NULL COMMENT '操作人',
    `config_type`     VARCHAR(32)  NOT NULL COMMENT '配置类型: ENTITY/FORM/LIST/MICROFLOW/RULE/CONNECTOR/TRIGGER/TAB/RELATED_PAGE',
    `config_id`       BIGINT       DEFAULT NULL COMMENT '配置ID',
    `config_code`     VARCHAR(128) DEFAULT NULL COMMENT '配置编码',
    `action`          VARCHAR(16)  NOT NULL COMMENT '动作: CREATE/UPDATE/DELETE/PUBLISH/ROLLBACK/PROMOTE',
    `before_snapshot` LONGTEXT     DEFAULT NULL COMMENT '操作前 JSON 快照',
    `after_snapshot`  LONGTEXT     DEFAULT NULL COMMENT '操作后 JSON 快照',
    `diff_summary`    VARCHAR(512) DEFAULT NULL COMMENT '变更摘要',
    `ip`              VARCHAR(64)  DEFAULT NULL COMMENT '操作IP',
    `user_agent`      VARCHAR(256) DEFAULT NULL COMMENT 'User-Agent',
    `tenant_id`       VARCHAR(64)  DEFAULT NULL COMMENT '租户ID（预留）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_actor` (`actor`),
    KEY `idx_config` (`config_type`, `config_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码配置审计日志';
