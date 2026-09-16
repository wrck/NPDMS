-- V34: 低代码规则表

CREATE TABLE IF NOT EXISTS `low_rule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(64)  NOT NULL,
    `name`        VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) NULL,
    `type`        VARCHAR(32)  NOT NULL COMMENT '规则类型: DECISION_TABLE/EXPRESSION/LITEFLOW',
    `definition`  LONGTEXT     NOT NULL COMMENT '决策表 JSON / 表达式 / LiteFlow EL',
    `status`      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `version`     INT          NOT NULL DEFAULT 1,
    `biz_type`    VARCHAR(64)  NULL,
    `create_by`   VARCHAR(64)  NULL,
    `update_by`   VARCHAR(64)  NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码规则';
