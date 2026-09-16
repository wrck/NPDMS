-- V40: 低代码配置评论表
-- （权限适配：剔除源工程 sys_permission 初始化段，用户权限/菜单体系不迁移；
--   权限对齐由 yudao system_menu 体系承担）

CREATE TABLE IF NOT EXISTS `low_comment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `config_type`  VARCHAR(32)  NOT NULL,
    `config_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `user_name`    VARCHAR(64)  NULL,
    `content`      TEXT         NOT NULL COMMENT '评论内容（支持 @提及）',
    `mentions`     VARCHAR(512) NULL COMMENT '@提及的用户 ID 列表（逗号分隔）',
    `parent_id`    BIGINT       NULL COMMENT '父评论 ID（用于回复）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_config` (`config_type`, `config_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码配置评论';

