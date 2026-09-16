-- =============================================================
-- V273__init_lowcode_gray_release.sql
-- 低代码灰度发布策略（批次5-T4，借鉴华为 AppCube / OutSystems LifeTime）。
--
-- <p>支持按比例（gray_percentage 0-100）或租户白名单渐进发布，
-- 灰度期间仅命中用户/租户可见新版本。可随时调整比例、全量发布或回滚。</p>
-- =============================================================

CREATE TABLE IF NOT EXISTS `low_gray_release` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_type`        VARCHAR(32)  NOT NULL                COMMENT '配置类型',
    `config_id`          BIGINT       NOT NULL                COMMENT '配置 ID',
    `config_code`        VARCHAR(64)  NULL                    COMMENT '配置编码',
    `version`            INT          NOT NULL                COMMENT '灰度版本号',
    `publish_record_id`  BIGINT       NULL                    COMMENT '关联发布记录 ID',
    `gray_percentage`    INT          NOT NULL DEFAULT 0      COMMENT '灰度比例 0-100',
    `tenant_whitelist`   JSON         NULL                    COMMENT '租户白名单 JSON 数组',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'GRAYING' COMMENT 'GRAYING/FULL/ROLLED_BACK',
    `gray_started_at`    DATETIME     NULL                    COMMENT '灰度开始时间',
    `full_released_at`   DATETIME     NULL                    COMMENT '全量发布时间',
    `rolled_back_at`     DATETIME     NULL                    COMMENT '回滚时间',
    `create_by`          VARCHAR(64)  NULL                    COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_config` (`config_type`, `config_id`),
    KEY `idx_status` (`status`),
    KEY `idx_publish_record` (`publish_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码灰度发布策略';

-- 灰度发布管理权限：剔除（源工程 sys_permission 初始化段，权限对齐由 yudao system_menu 承担）
