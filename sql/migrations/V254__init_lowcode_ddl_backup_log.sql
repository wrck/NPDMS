-- V32: 低代码 DDL 执行备份表 + 执行日志表
-- 用于 DdlExecutionService 记录每次 DDL 执行的备份和日志

CREATE TABLE IF NOT EXISTS `low_ddl_backup` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entity_id`       BIGINT       NOT NULL                COMMENT '实体 ID',
    `entity_code`     VARCHAR(64)  NOT NULL                COMMENT '实体编码',
    `table_name`      VARCHAR(64)  NOT NULL                COMMENT '物理表名',
    `backup_type`     VARCHAR(16)  NOT NULL                COMMENT '备份类型: CREATE/ALTER/DROP_COLUMN',
    `backup_sql`      LONGTEXT     NULL                    COMMENT '备份的 SQL（SHOW CREATE TABLE 结果或列数据 JSON）',
    `backup_data`     LONGTEXT     NULL                    COMMENT 'DROP COLUMN 时备份的列数据 JSON',
    `operator`        VARCHAR(64)  NULL                    COMMENT '操作人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_table_name` (`table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码 DDL 执行备份';

CREATE TABLE IF NOT EXISTS `low_ddl_execution_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entity_id`       BIGINT       NOT NULL                COMMENT '实体 ID',
    `entity_code`     VARCHAR(64)  NOT NULL                COMMENT '实体编码',
    `table_name`      VARCHAR(64)  NOT NULL                COMMENT '物理表名',
    `execution_type`  VARCHAR(16)  NOT NULL                COMMENT '执行类型: CREATE/ALTER/DROP_COLUMN/CREATE_INDEX/DROP_INDEX',
    `ddl_sql`         LONGTEXT     NOT NULL                COMMENT '执行的 DDL SQL',
    `status`          VARCHAR(16)  NOT NULL                COMMENT '执行状态: SUCCESS/FAILED',
    `error_message`   TEXT         NULL                    COMMENT '失败原因',
    `operator`        VARCHAR(64)  NULL                    COMMENT '操作人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity_id` (`entity_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码 DDL 执行日志';
