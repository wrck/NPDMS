-- V39: 低代码配置编辑锁持久化表（Redis 为缓存，DB 为持久化备份）
-- （权限适配：剔除源工程 sys_permission 初始化段，用户权限/菜单体系不迁移；
--   权限对齐由 yudao system_menu 体系承担）

CREATE TABLE IF NOT EXISTS `low_edit_lock` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `config_type`  VARCHAR(32)  NOT NULL COMMENT '配置类型: ENTITY/FORM/LIST/MICROFLOW/RULE/CONNECTOR',
    `config_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '持锁人 ID',
    `user_name`    VARCHAR(64)  NULL,
    `acquired_at`  DATETIME     NOT NULL COMMENT '获取时间',
    `expire_at`    DATETIME     NOT NULL COMMENT '过期时间（Redis TTL 同步）',
    `renew_count`  INT          NOT NULL DEFAULT 0,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config` (`config_type`, `config_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码编辑锁';

