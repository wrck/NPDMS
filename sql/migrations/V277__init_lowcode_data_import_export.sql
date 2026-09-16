-- =============================================================
-- V277__init_lowcode_data_import_export.sql
-- 业务数据导入导出（缺口3）
--
-- 借鉴 Budibase/OutSystems 数据导入导出能力：
--   1. low_import_task — 异步导入任务记录（含成功/失败行数与失败明细）
--   2. low_backup_record — 备份记录（本次仅建表预留，备份功能在批次6实现）
--
-- 异步导入流程：上传 Excel → 创建 PENDING 任务 → @Async 解析按行插入 →
-- 更新 SUCCESS/FAILED 状态。失败行记录到 failed_detail JSON。
-- =============================================================

-- -------------------------------------------------------------
-- 1. 导入任务表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_import_task` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entity_code`    VARCHAR(64)  NOT NULL COMMENT '实体编码',
    `file_name`      VARCHAR(256) NOT NULL COMMENT '上传文件名',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED',
    `total_rows`     INT          NOT NULL DEFAULT 0 COMMENT '总行数',
    `success_rows`   INT          NOT NULL DEFAULT 0 COMMENT '成功行数',
    `failed_rows`    INT          NOT NULL DEFAULT 0 COMMENT '失败行数',
    `failed_detail`  LONGTEXT     DEFAULT NULL COMMENT '失败明细 JSON: [{row, field, error}]',
    `error_message`  VARCHAR(512) DEFAULT NULL COMMENT '任务级错误信息',
    `operator`       VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `start_time`     DATETIME     DEFAULT NULL COMMENT '任务开始时间',
    `end_time`       DATETIME     DEFAULT NULL COMMENT '任务结束时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity` (`entity_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码数据导入任务';

-- -------------------------------------------------------------
-- 2. 备份记录表（预留，备份功能在批次6实现）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_backup_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type`          VARCHAR(16)  NOT NULL COMMENT '类型: FULL/INCREMENTAL',
    `scope`         VARCHAR(128) DEFAULT NULL COMMENT '备份范围（实体编码/全部）',
    `file_path`     VARCHAR(512) NOT NULL COMMENT '备份文件路径',
    `file_size`     BIGINT       DEFAULT 0 COMMENT '文件大小（字节）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED',
    `operator`      VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `backup_time`   DATETIME     DEFAULT NULL COMMENT '备份时间',
    `expire_at`     DATETIME     DEFAULT NULL COMMENT '过期时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_backup_time` (`backup_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码数据备份记录（预留）';
