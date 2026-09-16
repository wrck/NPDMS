-- V30: 低代码配置版本快照表
-- 每次发布生成不可变快照，支持 Diff 对比与回滚

CREATE TABLE low_config_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_type VARCHAR(32) NOT NULL COMMENT '配置类型: FORM/LIST/TAB/RELATED_PAGE/ENTITY/MICROFLOW/RULE/CONNECTOR',
    config_id BIGINT NOT NULL COMMENT '配置ID',
    config_code VARCHAR(64) NOT NULL COMMENT '配置编码（冗余，便于查询）',
    version INT NOT NULL COMMENT '版本号',
    snapshot LONGTEXT NOT NULL COMMENT 'JSON 全量快照',
    change_log VARCHAR(512) DEFAULT NULL COMMENT '变更说明',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
    environment VARCHAR(16) NOT NULL DEFAULT 'DEV' COMMENT '环境: DEV/TEST/PROD',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by VARCHAR(64) DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_version (config_type, config_id, version, environment),
    KEY idx_config (config_type, config_id),
    KEY idx_environment (environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码配置版本快照';
