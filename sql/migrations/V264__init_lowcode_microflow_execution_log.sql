-- V42: 低代码微流执行轨迹表（借鉴 Joget APM，记录每次微流执行中各节点的耗时与输入输出）

CREATE TABLE IF NOT EXISTS `low_microflow_execution_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `microflow_id`       BIGINT       NOT NULL                COMMENT '微流ID',
    `microflow_code`     VARCHAR(64)  NOT NULL                COMMENT '微流编码',
    `execution_id`       VARCHAR(64)  NOT NULL                COMMENT '执行唯一ID（UUID，串联同一次执行的所有节点轨迹）',
    `node_id`            VARCHAR(64)  NOT NULL                COMMENT '节点ID',
    `node_type`          VARCHAR(32)  NOT NULL                COMMENT '节点类型',
    `start_time`         DATETIME     NOT NULL                COMMENT '节点执行开始时间',
    `end_time`           DATETIME     NULL                    COMMENT '节点执行结束时间',
    `duration_ms`        BIGINT       NULL                    COMMENT '节点执行耗时（毫秒）',
    `inputs`             JSON         NULL                    COMMENT '节点输入（config JSON）',
    `outputs`            JSON         NULL                    COMMENT '节点输出（result 等）',
    `variables_snapshot` JSON         NULL                    COMMENT '执行前变量快照',
    `status`             VARCHAR(16)  NOT NULL                COMMENT '状态: RUNNING/SUCCESS/FAILED',
    `error_message`      TEXT         NULL                    COMMENT '错误信息（FAILED 时）',
    `operator`           VARCHAR(64)  NULL                    COMMENT '操作人',
    `create_by`          VARCHAR(64)  NULL,
    `update_by`          VARCHAR(64)  NULL,
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_microflow_id` (`microflow_id`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码微流执行轨迹';
