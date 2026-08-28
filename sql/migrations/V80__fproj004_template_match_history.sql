-- F-PROJ-004 / PM-07：模板匹配决策历史。
-- 仅新增单一append-only业务事实；四属性当前值继续复用proj_project既有列。

CREATE TABLE IF NOT EXISTS `proj_project_template_match_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '匹配决策历史ID',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `trigger_type` VARCHAR(32) NOT NULL COMMENT 'INITIAL_CREATE/SOURCE_CORRECTION/MANUAL_ADJUSTMENT',
    `record_purpose` VARCHAR(32) NOT NULL COMMENT 'CREATE_DECISION/IMPACT_EVALUATION',
    `input_origin` VARCHAR(32) NOT NULL COMMENT 'MANUAL/SOURCE',
    `snapshot_schema_version` VARCHAR(32) NOT NULL COMMENT '四属性快照结构版本',
    `before_attribute_snapshot` JSON NULL COMMENT '变更前四属性快照；首次创建为空',
    `attribute_snapshot` JSON NOT NULL COMMENT '本次判定使用的四属性完整快照',
    `attribute_owner_snapshot` JSON NOT NULL COMMENT '四属性Owner快照',
    `source_owner` VARCHAR(64) NULL COMMENT '来源事实Owner',
    `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统；手工为MANUAL',
    `source_key` VARCHAR(128) NULL COMMENT '来源业务键',
    `source_event_id` VARCHAR(128) NULL COMMENT '来源事件ID',
    `source_version` VARCHAR(128) NULL COMMENT '来源版本',
    `source_occurred_at` DATETIME(3) NULL COMMENT '来源发生时间',
    `source_value_digest` CHAR(64) NULL COMMENT '来源原值SHA-256',
    `mapping_version` VARCHAR(64) NULL COMMENT '来源映射版本',
    `matcher_version` VARCHAR(64) NOT NULL COMMENT '模板匹配规则版本',
    `match_result` VARCHAR(32) NOT NULL COMMENT 'UNIQUE/NO_MATCH/MULTIPLE_MATCHES',
    `candidate_digest` CHAR(64) NOT NULL COMMENT '排序后候选集合SHA-256',
    `decision_mode` VARCHAR(32) NULL COMMENT '首次创建AUTO_UNIQUE/EXPLICIT_SELECTION',
    `matched_template_id` BIGINT NULL COMMENT '唯一命中或显式选中模板ID',
    `matched_template_revision_id` BIGINT NULL COMMENT '唯一命中或显式选中模板修订ID',
    `frozen_template_revision_id` BIGINT NOT NULL COMMENT '项目当前冻结模板修订ID',
    `impact_result` VARCHAR(32) NOT NULL COMMENT 'NOT_APPLICABLE/NO_IMPACT/CANDIDATE_CHANGED/NO_MATCH/MULTIPLE_MATCHES',
    `operator_id` BIGINT NOT NULL COMMENT '认证用户或注册服务主体稳定ID',
    `change_reason` VARCHAR(500) NOT NULL COMMENT '去除首尾空白后的创建或调整原因',
    `occurred_at` DATETIME(3) NOT NULL COMMENT '业务发生时间',
    `recorded_at` DATETIME(3) NOT NULL COMMENT '事实写入时间',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '命令幂等键',
    `request_digest` CHAR(64) NOT NULL COMMENT '请求摘要',
    `operation_id` VARCHAR(128) NOT NULL COMMENT '稳定业务操作ID',
    `trace_id` VARCHAR(128) NULL COMMENT '可选技术Trace关联',
    `audit_log_id` BIGINT NULL COMMENT '可选系统操作日志关联',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_template_match_history_operation` (`tenant_id`, `operation_id`),
    UNIQUE KEY `uk_proj_template_match_history_idempotency` (`tenant_id`, `project_id`, `idempotency_key`),
    KEY `idx_proj_template_match_history_project_time` (`tenant_id`, `project_id`, `occurred_at`, `id`),
    KEY `idx_proj_template_match_history_result` (`tenant_id`, `project_id`, `match_result`, `impact_result`, `occurred_at`),
    CONSTRAINT `ck_proj_template_match_history_trigger`
        CHECK (`trigger_type` IN ('INITIAL_CREATE','SOURCE_CORRECTION','MANUAL_ADJUSTMENT')),
    CONSTRAINT `ck_proj_template_match_history_purpose`
        CHECK (`record_purpose` IN ('CREATE_DECISION','IMPACT_EVALUATION')),
    CONSTRAINT `ck_proj_template_match_history_origin`
        CHECK (`input_origin` IN ('MANUAL','SOURCE')),
    CONSTRAINT `ck_proj_template_match_history_result`
        CHECK (`match_result` IN ('UNIQUE','NO_MATCH','MULTIPLE_MATCHES')),
    CONSTRAINT `ck_proj_template_match_history_decision`
        CHECK (`decision_mode` IS NULL OR `decision_mode` IN ('AUTO_UNIQUE','EXPLICIT_SELECTION')),
    CONSTRAINT `ck_proj_template_match_history_impact`
        CHECK (`impact_result` IN ('NOT_APPLICABLE','NO_IMPACT','CANDIDATE_CHANGED','NO_MATCH','MULTIPLE_MATCHES')),
    CONSTRAINT `ck_proj_template_match_history_trigger_purpose`
        CHECK ((`trigger_type` = 'INITIAL_CREATE' AND `record_purpose` = 'CREATE_DECISION'
                AND `before_attribute_snapshot` IS NULL AND `impact_result` = 'NOT_APPLICABLE'
                AND `decision_mode` IS NOT NULL)
            OR (`trigger_type` IN ('SOURCE_CORRECTION','MANUAL_ADJUSTMENT')
                AND `record_purpose` = 'IMPACT_EVALUATION'
                AND `before_attribute_snapshot` IS NOT NULL AND `decision_mode` IS NULL)),
    CONSTRAINT `ck_proj_template_match_history_source`
        CHECK (`trigger_type` <> 'SOURCE_CORRECTION'
            OR (`input_origin` = 'SOURCE' AND `source_owner` IS NOT NULL
                AND `source_key` IS NOT NULL AND `source_event_id` IS NOT NULL
                AND `source_version` IS NOT NULL AND `source_occurred_at` IS NOT NULL
                AND `source_value_digest` IS NOT NULL AND `mapping_version` IS NOT NULL)),
    CONSTRAINT `ck_proj_template_match_history_matched`
        CHECK ((`match_result` = 'UNIQUE'
                AND `matched_template_id` IS NOT NULL AND `matched_template_revision_id` IS NOT NULL)
            OR (`match_result` = 'MULTIPLE_MATCHES' AND `trigger_type` = 'INITIAL_CREATE'
                AND `decision_mode` = 'EXPLICIT_SELECTION'
                AND `matched_template_id` IS NOT NULL AND `matched_template_revision_id` IS NOT NULL)
            OR (`match_result` IN ('NO_MATCH','MULTIPLE_MATCHES')
                AND `trigger_type` <> 'INITIAL_CREATE'
                AND `matched_template_id` IS NULL AND `matched_template_revision_id` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PM-07模板匹配决策与影响评估永久历史（业务字段只插入不更新）';
