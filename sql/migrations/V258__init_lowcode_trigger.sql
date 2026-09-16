-- V36: 低代码触发器表
-- （权限适配：剔除源工程 sys_permission 初始化段，用户权限/菜单体系不迁移；
--   权限对齐由 yudao system_menu 体系承担）

CREATE TABLE IF NOT EXISTS `low_trigger` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `code`            VARCHAR(64)  NOT NULL,
    `name`            VARCHAR(128) NOT NULL,
    `type`            VARCHAR(32)  NOT NULL COMMENT '触发类型: CRUD/QUARTZ/EVENT',
    `config`          LONGTEXT     NOT NULL COMMENT '配置 JSON: {entityCode, operation / cron / eventType}',
    `target_type`     VARCHAR(32)  NOT NULL COMMENT '目标类型: MICROFLOW/PROCESS',
    `target_code`     VARCHAR(128) NOT NULL COMMENT '目标编码',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `create_by`       VARCHAR(64)  NULL,
    `update_by`       VARCHAR(64)  NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码触发器';

