-- =============================================================
-- V278__init_lowcode_process_sla.sql
-- 流程 SLA 双阶段触发（缺口4）
--
-- 借鉴 ServiceNow / Pega 流程 SLA 治理：在任务截止前 80% 时间点触发
-- "接近违约预警"微流，截止时间到达后触发"升级"微流，实现双阶段治理。
--
-- SLA 配置来源：BPMN 用户任务的 lowcode:config 扩展元素，前端字段
-- slaDuration / slaUnit / slaEscalationMicroflow（参考 LowCodeBpmnProperties.vue）。
-- =============================================================

CREATE TABLE IF NOT EXISTS `low_process_sla_record` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `process_instance_id` VARCHAR(64)  NOT NULL COMMENT '流程实例ID',
    `task_id`             VARCHAR(64)  NOT NULL COMMENT '任务ID',
    `sla_config_json`     LONGTEXT     DEFAULT NULL COMMENT 'SLA 配置 JSON（slaDuration/slaUnit/slaEscalationMicroflow）',
    `deadline`            DATETIME     NOT NULL COMMENT '截止时间',
    `warning_sent`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已发送预警: 0否 1是',
    `escalate_sent`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已发送升级: 0否 1是',
    `status`              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/WARNING/ESCALATED/COMPLETED',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码流程 SLA 记录';
