-- V33: 低代码微流表 + 微流版本表 + form.events 字段扩展
-- （权限适配：剔除源工程 sys_permission 权限初始化段，用户权限/菜单体系不迁移；
--   权限对齐由 yudao system_menu 体系承担）

CREATE TABLE IF NOT EXISTS `low_microflow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL                COMMENT '微流编码（唯一）',
    `name`        VARCHAR(128) NOT NULL                COMMENT '微流名称',
    `description` VARCHAR(512) NULL                    COMMENT '描述',
    `definition`  LONGTEXT     NULL                    COMMENT '微流定义 JSON（节点 + 边）',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/ARCHIVED',
    `version`     INT          NOT NULL DEFAULT 1      COMMENT '版本号',
    `biz_type`    VARCHAR(64)  NULL                    COMMENT '业务类型',
    `create_by`   VARCHAR(64)  NULL,
    `update_by`   VARCHAR(64)  NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码微流';

CREATE TABLE IF NOT EXISTS `low_microflow_version` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `microflow_id`  BIGINT       NOT NULL,
    `version`       INT          NOT NULL,
    `definition`    LONGTEXT     NOT NULL,
    `change_log`    VARCHAR(512) NULL,
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    `create_by`     VARCHAR(64)  NULL,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_microflow_id` (`microflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码微流版本';

-- 扩展 low_form 表，新增 events 字段
ALTER TABLE `low_form` ADD COLUMN `events` LONGTEXT NULL COMMENT '事件绑定 JSON: {onLoad:{type,code}, onChange:{...}, onSubmit:{...}}' AFTER `form_config`;

-- 权限初始化：剔除（源工程 sys_permission 初始化段，权限对齐由 yudao system_menu 承担）
