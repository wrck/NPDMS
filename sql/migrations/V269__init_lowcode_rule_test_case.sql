-- 批次3-T3: 规则测试用例表
-- 为规则定义可重复执行的测试用例，包含输入事实和期望输出。
-- 运行测试时对比实际输出与期望输出，判定 PASS/FAIL。

CREATE TABLE IF NOT EXISTS `low_rule_test_case` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_id`               BIGINT       NOT NULL                COMMENT '规则 ID',
    `rule_code`             VARCHAR(100)          DEFAULT NULL   COMMENT '规则编码（冗余）',
    `name`                  VARCHAR(200) NOT NULL                COMMENT '测试用例名称',
    `description`           VARCHAR(500)          DEFAULT NULL   COMMENT '描述',
    `input_json`            TEXT         NOT NULL                COMMENT '输入事实 JSON',
    `expected_output_json`  TEXT         NOT NULL                COMMENT '期望输出 JSON',
    `assertion_mode`        VARCHAR(20)           DEFAULT 'EQUALS' COMMENT '断言模式: EQUALS/CONTAINS/NOT_NULL',
    `enabled`               TINYINT(1)            DEFAULT 1      COMMENT '是否启用',
    `create_by`             VARCHAR(64)           DEFAULT ''     COMMENT '创建者',
    `create_time`           DATETIME              DEFAULT NULL   COMMENT '创建时间',
    `update_by`             VARCHAR(64)           DEFAULT ''     COMMENT '更新者',
    `update_time`           DATETIME              DEFAULT NULL   COMMENT '更新时间',
    `remark`                VARCHAR(500)          DEFAULT NULL   COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码规则测试用例';
