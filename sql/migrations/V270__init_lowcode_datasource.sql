-- 批次3-T7: 多数据源配置表
-- 统一建模三种外部数据源集成模式：DIRECT(外联库直连)/REPLICA(外联表副本)/FEDERATED(中间库整合)

CREATE TABLE IF NOT EXISTS `low_datasource` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                VARCHAR(100) NOT NULL                COMMENT '数据源编码（唯一）',
    `name`                VARCHAR(200) NOT NULL                COMMENT '数据源名称',
    `db_type`             VARCHAR(20)  NOT NULL                COMMENT '数据库类型: mysql/postgresql/sqlserver/oracle',
    `integration_mode`    VARCHAR(20)           DEFAULT 'DIRECT' COMMENT '集成模式: DIRECT/REPLICA/FEDERATED',
    `url`                 VARCHAR(500) NOT NULL                COMMENT 'JDBC URL',
    `username`            VARCHAR(100) NOT NULL                COMMENT '用户名',
    `password`            VARCHAR(500)          DEFAULT NULL   COMMENT '密码（加密存储）',
    `driver_class_name`   VARCHAR(200)          DEFAULT NULL   COMMENT '驱动类名',
    `pool_size`           INT                   DEFAULT 5      COMMENT '连接池大小',
    `status`              VARCHAR(20)           DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `linked_entity_code`  VARCHAR(100)          DEFAULT NULL   COMMENT '关联实体编码(REPLICA模式)',
    `sync_config`         TEXT                  DEFAULT NULL   COMMENT '同步配置JSON(REPLICA模式)',
    `description`         VARCHAR(500)          DEFAULT NULL   COMMENT '描述',
    `create_by`           VARCHAR(64)           DEFAULT ''     COMMENT '创建者',
    `create_time`         DATETIME              DEFAULT NULL   COMMENT '创建时间',
    `update_by`           VARCHAR(64)           DEFAULT ''     COMMENT '更新者',
    `update_time`         DATETIME              DEFAULT NULL   COMMENT '更新时间',
    `remark`              VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码多数据源配置';
