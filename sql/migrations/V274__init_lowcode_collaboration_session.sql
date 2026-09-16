-- =============================================================
-- V274__init_lowcode_collaboration_session.sql
-- 低代码协同编辑会话（批次5-T6，借鉴 Mendix 协同编辑）。
--
-- <p>记录每个配置（configType+configId）的协同会话与在线用户。
-- 注：需求文档要求 Yjs + y-websocket，环境无网络无法安装，
-- 采用 HTTP 轮询简化方案，预留 WebSocket 升级点。</p>
-- =============================================================

CREATE TABLE IF NOT EXISTS `low_collaboration_session` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_type`     VARCHAR(32)  NOT NULL                COMMENT '配置类型',
    `config_id`       BIGINT       NOT NULL                COMMENT '配置 ID',
    `online_users`    JSON         NULL                    COMMENT '在线用户 JSON: [{userId,userName,avatar,joinedAt,lastHeartbeat}]',
    `change_seq`      BIGINT       NOT NULL DEFAULT 0      COMMENT '变更序号（增量同步用）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config` (`config_type`, `config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码协同编辑会话';

-- 协同会话权限：剔除（源工程 sys_permission 初始化段，权限对齐由 yudao system_menu 承担）
