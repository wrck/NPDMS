-- =============================================================================
-- V372: P06R 统一交付件模型扩展（.run/unified-business-model-plan-20260923/P06R-交付件全景清单与统一承接设计.md）。
-- 目标：plt_delivery_* 承接全部交付件情形——
--   1) 材料支持两类证据锚：FILE（文件版本，保持原强约束）与 BUSINESS_RESULT（业务成果，
--      如批准实施方案、确认培训记录；文件列置空，业务对象身份+修订作为证据锚点）；
--   2) 要求支持两类来源：CATALOG（能力配置生成，原语义）与 TEMPLATE_FROZEN（模板冻结应交根，
--      绑定阶段/任务、计划版本与冻结配置，承接 acc_project_deliverable 语义，I2 迁入）；
--   3) 提交台账补判定证据快照（对齐 ACC decisionEvidence 语义）与来源类型。
-- 幂等：重复执行报 Duplicate column（与 V361 系列同等约束，测试库一次性应用）。
-- 类型目录种子：TRAINING_RECORD / IMPLEMENTATION_PLAN 取值来自 PRD 3.4.1.6 交付件分类
--   （培训记录、实施方案）与既有 imp_eng_deliverable 实际使用值，不臆造。
-- =============================================================================

-- 1) 要求实例：模板冻结应交根扩展列
ALTER TABLE `plt_delivery_requirement`
    ADD COLUMN `requirement_kind` VARCHAR(16) NOT NULL DEFAULT 'CATALOG'
        COMMENT '要求来源 CATALOG=能力配置生成 / TEMPLATE_FROZEN=模板冻结应交根' AFTER `type_code`,
    ADD COLUMN `stage_code` VARCHAR(64) NULL
        COMMENT '绑定阶段编码（TEMPLATE_FROZEN）' AFTER `requirement_kind`,
    ADD COLUMN `task_code` VARCHAR(64) NULL
        COMMENT '绑定任务编码（NULL=阶段级要求）' AFTER `stage_code`,
    ADD COLUMN `plan_version_id` BIGINT NULL
        COMMENT '冻结计划版本（TEMPLATE_FROZEN）' AFTER `task_code`,
    ADD COLUMN `source_definition_id` BIGINT NULL
        COMMENT '模板交付件定义引用ID' AFTER `plan_version_id`,
    ADD COLUMN `project_id` BIGINT NULL
        COMMENT '项目上下文（供项目级汇总与门禁定位）' AFTER `source_definition_id`,
    ADD COLUMN `frozen_config_json` VARCHAR(2048) NULL
        COMMENT '冻结要求配置（数量/允许来源/自动来源/确认规则 JSON）' AFTER `project_id`;

-- 2) 实际材料：业务成果证据锚 + 要求直连 + 项目上下文；文件列对 BUSINESS_RESULT 行置空
ALTER TABLE `plt_delivery_material`
    ADD COLUMN `material_kind` VARCHAR(16) NOT NULL DEFAULT 'FILE'
        COMMENT '材料证据锚 FILE=文件版本 / BUSINESS_RESULT=业务成果' AFTER `type_code`,
    ADD COLUMN `requirement_id` BIGINT NULL
        COMMENT '绑定的模板冻结要求（模板行材料直连要求）' AFTER `material_kind`,
    ADD COLUMN `project_id` BIGINT NULL
        COMMENT '项目上下文（供项目级汇总）' AFTER `requirement_id`,
    ADD COLUMN `business_object_type` VARCHAR(64) NULL
        COMMENT '业务成果对象类型（BUSINESS_RESULT 行，统一目录 entityType）' AFTER `project_id`,
    ADD COLUMN `business_object_id` VARCHAR(64) NULL
        COMMENT '业务成果对象ID' AFTER `business_object_type`,
    ADD COLUMN `business_revision_no` BIGINT NULL
        COMMENT '业务成果修订锚（如方案基线版本；无修订对象为 NULL）' AFTER `business_object_id`,
    MODIFY COLUMN `file_reference_id` BIGINT NULL COMMENT '统一文件引用ID（BUSINESS_RESULT 行为空）',
    MODIFY COLUMN `file_artifact_id` BIGINT NULL COMMENT '文件工件ID（BUSINESS_RESULT 行为空）',
    MODIFY COLUMN `file_version_no` INT NULL COMMENT '文件版本号（BUSINESS_RESULT 行为空）',
    MODIFY COLUMN `file_sha256` VARCHAR(64) NULL COMMENT '文件内容摘要（BUSINESS_RESULT 行为空）',
    MODIFY COLUMN `file_name` VARCHAR(255) NULL COMMENT '文件名称（BUSINESS_RESULT 行为空）';

-- FILE 行唯一键（tenant+owner+artifact+version）不变仍生效；
-- BUSINESS_RESULT 行 file_artifact_id 为 NULL 不参与该唯一键，幂等由服务层按
-- （owner 三元组 + business_object_type + business_object_id + business_revision_no）保证。
ALTER TABLE `plt_delivery_material`
    ADD INDEX `idx_plt_delivery_mat_project` (`tenant_id`, `project_id`),
    ADD INDEX `idx_plt_delivery_mat_requirement` (`requirement_id`),
    ADD INDEX `idx_plt_delivery_mat_business` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`,
        `business_object_type`, `business_object_id`);

-- 3) 提交台账：来源类型 + 判定证据快照 + 项目上下文
ALTER TABLE `plt_delivery_submission`
    ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT 'UPLOAD'
        COMMENT '提交来源 UPLOAD/BUSINESS_RESULT/BUSINESS_DOCUMENT/AUTO_PROJECTION' AFTER `material_ids_json`,
    ADD COLUMN `project_id` BIGINT NULL COMMENT '项目上下文' AFTER `source_type`,
    ADD COLUMN `request_payload_json` VARCHAR(2048) NULL COMMENT '请求快照（幂等重放比对）' AFTER `project_id`,
    ADD COLUMN `decision_evidence_json` VARCHAR(2048) NULL COMMENT '判定证据快照（满足/原因/证据）' AFTER `request_payload_json`;

-- 4) 类型目录补齐业务类型（既有 7 个演示类型不动；creator 标识沿用种子约定，幂等跳过已存在编码）
INSERT INTO `plt_delivery_type` (`type_code`, `name`, `category`, `allowed_media_json`, `max_size_bytes`,
    `enabled`, `remark`, `version`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (SELECT 'TRAINING_RECORD' AS type_code, '培训记录' AS name, '工程交付' AS category,
    '["pdf","jpeg","png"]' AS allowed_media_json, 52428800 AS max_size_bytes,
    b'1' AS enabled, 'P06R 业务类型（PRD 3.4.1.6 分类：培训记录）' AS remark, 0 AS version, 1 AS tenant_id,
    'p06r-seed' AS creator, NOW(3) AS create_time, 'p06r-seed' AS updater, NOW(3) AS update_time, b'0' AS deleted) t
WHERE NOT EXISTS (SELECT 1 FROM `plt_delivery_type` WHERE `tenant_id` = 1 AND `type_code` = 'TRAINING_RECORD' AND `deleted` = b'0');

INSERT INTO `plt_delivery_type` (`type_code`, `name`, `category`, `allowed_media_json`, `max_size_bytes`,
    `enabled`, `remark`, `version`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT * FROM (SELECT 'IMPLEMENTATION_PLAN' AS type_code, '实施方案' AS name, '工程交付' AS category,
    '["pdf"]' AS allowed_media_json, 52428800 AS max_size_bytes,
    b'1' AS enabled, 'P06R 业务类型（PRD 3.4.1.6 分类：实施方案）' AS remark, 0 AS version, 1 AS tenant_id,
    'p06r-seed' AS creator, NOW(3) AS create_time, 'p06r-seed' AS updater, NOW(3) AS update_time, b'0' AS deleted) t
WHERE NOT EXISTS (SELECT 1 FROM `plt_delivery_type` WHERE `tenant_id` = 1 AND `type_code` = 'IMPLEMENTATION_PLAN' AND `deleted` = b'0');
