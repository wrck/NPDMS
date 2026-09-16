-- V44: 低代码触发器执行日志表（记录触发器每次执行的历史，前端可查看）

CREATE TABLE IF NOT EXISTS `low_trigger_execution_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trigger_id`      BIGINT       NOT NULL                COMMENT '触发器 ID',
    `trigger_code`    VARCHAR(64)  NOT NULL                COMMENT '触发器编码',
    `trigger_type`    VARCHAR(16)  NOT NULL                COMMENT '触发类型: CRUD/QUARTZ/EVENT',
    `target_type`     VARCHAR(16)  NOT NULL                COMMENT '目标类型: MICROFLOW/PROCESS',
    `target_code`     VARCHAR(64)  NOT NULL                COMMENT '目标编码',
    `execution_id`    VARCHAR(64)  NULL                    COMMENT '执行唯一ID（微流执行ID）',
    `inputs`          LONGTEXT     NULL                    COMMENT '输入数据 JSON',
    `outputs`         LONGTEXT     NULL                    COMMENT '输出结果 JSON',
    `status`          VARCHAR(16)  NOT NULL                COMMENT '执行状态: SUCCESS/FAILED',
    `error_message`   TEXT         NULL                    COMMENT '失败原因',
    `duration_ms`     BIGINT       NULL                    COMMENT '执行耗时毫秒',
    `operator`        VARCHAR(64)  NULL                    COMMENT '操作人',
    `create_by`       VARCHAR(64)  NULL,
    `update_by`       VARCHAR(64)  NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_trigger_id` (`trigger_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码触发器执行日志';
