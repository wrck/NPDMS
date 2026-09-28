-- =============================================================================
-- V374: P06R 统一交付件数据迁移（.run/unified-business-model-plan-20260923/P06R-交付件全景清单与统一承接设计.md I2）。
-- 映射（幂等可重跑：全部 INSERT...SELECT ... WHERE NOT EXISTS，重复执行零行）：
--   1) acc_project_deliverable 189 根 → plt_delivery_requirement（TEMPLATE_FROZEN，ID 保留）。
--      frozen_config_json/plan_version_id 从项目活跃计划执行快照 JSON 提取（判定不依赖它，仅冻结快照留档）；
--      活跃计划不可用置 NULL（F-ACC-001 种子项目无计划，旧系统同样在 rules.lock 拒绝上传，行为一致）。
--   2) source_version+attachment → 材料 + 提交台账（提交 ID 保留 = source_version.id）：
--      UPLOAD 附件 77 件经 plt_file_reference (tenant,artifact,version) 唯一反查 reference_id，
--      材料 ID = 附件 ID；BUSINESS_DOCUMENT 归集文件以业务对象身份落材料（scope 编码 + referenceId，
--      对齐 registerTemplateFrozenDocument 语义），材料 ID 取高段 9.1e18 顺位；
--      BUSINESS_RESULT 成果锚材料 ID = 提交 ID，businessObjectId = type|objectId|resultId|formedAt
--      （formedAt 取 sol_requirement_analysis_revision.frozen_at，秒级 ISO，对齐 EvidenceProvider 复合构造）。
--   3) 本数据集无报告/满意度投影提交（105 份全部为 ProjectDeliverableSubmission，已核）；
--      AUTO_PROJECTION 由运行期投影服务以 requestKey 幂等承接，迁移不合成。
--   4) 绑定重指（acc_acceptance/acc_satisfaction_collection_task.deliverable_id）因 ID 保留天然成立，
--      迁移后以一致性查询核验，无需 UPDATE。
-- 材料唯一键补 type_code：同项目不同交付件类型可共享同一文件版本作为各自材料。
-- =============================================================================

-- 1) 材料唯一键补 type_code（同 tenant+owner 三元组内不同交付件类型互不挤占）；
--    business_object_id 扩容：成果复合身份（type|objectId|resultId|formedAt ≈115 字符）超出 V372 的 64，
--    运行期首个 BUSINESS_RESULT 登记同样会溢出，属模型缺陷随本次收口。
ALTER TABLE `plt_delivery_material`
    MODIFY COLUMN `business_object_id` VARCHAR(256) NULL
        COMMENT '业务成果对象ID（type|objectId|resultId|formedAt 复合身份）',
    DROP INDEX `uk_plt_delivery_mat`,
    ADD UNIQUE KEY `uk_plt_delivery_mat` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`,
        `type_code`, `file_artifact_id`, `file_version_no`);

-- 2) 要求实例：189 根 → TEMPLATE_FROZEN 要求（ID 保留）
INSERT INTO `plt_delivery_requirement` (`id`, `owner_module`, `entity_type`, `entity_id`, `type_code`,
    `name`, `requirement_kind`, `stage_code`, `task_code`, `plan_version_id`, `source_definition_id`,
    `project_id`, `frozen_config_json`, `required`, `minimum_quantity`, `counting_unit`, `status`,
    `config_id`, `config_version`, `version`, `tenant_id`, `creator`, `create_time`, `updater`,
    `update_time`, `deleted`)
SELECT r.id, 'ACC', 'project_deliverable', r.project_id, r.deliverable_code, r.name, 'TEMPLATE_FROZEN',
    r.stage_code, r.task_code, m.active_plan_version_id, r.source_definition_id, r.project_id,
    cfg.configuration, r.required,
    GREATEST(IF(r.required = 0, 0, 1), COALESCE(CAST(JSON_EXTRACT(cfg.configuration, '$.minimumQuantity') AS SIGNED), 0)),
    'MATERIAL',
    CASE r.status WHEN 'ACCEPTED' THEN 'SATISFIED' WHEN 'CONFIRMED' THEN 'CONFIRMED' ELSE 'OPEN' END,
    0, 0, 0, r.tenant_id, r.creator, r.create_time, r.updater, r.update_time, b'0'
FROM acc_project_deliverable r
LEFT JOIN proj_project m ON m.id = r.project_id AND m.tenant_id = r.tenant_id
LEFT JOIN proj_project_plan_version pv ON pv.id = m.active_plan_version_id AND pv.tenant_id = m.tenant_id
LEFT JOIN JSON_TABLE(pv.execution_snapshot, '$.deliverables[*]' COLUMNS (
        code VARCHAR(64) PATH '$.code', configuration JSON PATH '$.configuration')) cfg
    ON cfg.code = r.deliverable_code
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_requirement t WHERE t.id = r.id);

-- 3a) UPLOAD 材料：附件经 (tenant, artifact, version) 反查统一文件引用，材料 ID = 附件 ID
INSERT INTO `plt_delivery_material` (`id`, `owner_module`, `entity_type`, `entity_id`, `type_code`,
    `material_kind`, `requirement_id`, `project_id`, `file_reference_id`, `file_artifact_id`,
    `file_version_no`, `file_sha256`, `file_name`, `source_kind`, `status`, `archive_status`,
    `archive_failure_code`, `archive_retry_count`, `archive_time`, `tenant_id`, `creator`,
    `create_time`, `updater`, `update_time`, `deleted`)
SELECT a.id, 'ACC', 'project_deliverable', r.project_id, r.deliverable_code, 'FILE', r.id, r.project_id,
    fr.id, a.file_artifact_id, a.file_version_no, a.file_hash, fa.name, 'UPLOAD', 'ACTIVE',
    sv.archive_status, sv.archive_failure_code, sv.archive_retry_count, sv.archive_time,
    a.tenant_id, a.creator, a.create_time, a.updater, a.update_time, b'0'
FROM acc_project_deliverable_source_attachment a
JOIN acc_project_deliverable_source_version sv ON sv.id = a.deliverable_source_version_id AND sv.deleted = 0
JOIN acc_project_deliverable r ON r.id = sv.deliverable_id AND r.deleted = 0
JOIN plt_file_reference fr ON fr.tenant_id = a.tenant_id
    AND fr.artifact_id = a.file_artifact_id AND fr.file_version_no = a.file_version_no
LEFT JOIN plt_file_artifact fa ON fa.id = a.file_artifact_id
WHERE a.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_material t WHERE t.id = a.id);

-- 3b) BUSINESS_DOCUMENT 材料：归集文件以（scope 编码 + referenceId）业务对象身份落材料，ID 取高段顺位
INSERT INTO `plt_delivery_material` (`id`, `owner_module`, `entity_type`, `entity_id`, `type_code`,
    `material_kind`, `requirement_id`, `project_id`, `business_object_type`, `business_object_id`,
    `file_reference_id`, `file_artifact_id`, `file_version_no`, `file_sha256`, `file_name`,
    `source_kind`, `status`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT (9100000000000000000 + ROW_NUMBER() OVER (ORDER BY s.id, jt.ord)), 'ACC', 'project_deliverable',
    s.project_id, r.deliverable_code, 'FILE', s.deliverable_id, s.project_id,
    CASE WHEN act.acceptance_type IS NOT NULL
         THEN CONCAT('ACC.', act.acceptance_type, '_REPORT') ELSE 'ACC.SATISFACTION_DOCUMENT' END,
    CAST(jt.ref AS CHAR), jt.ref, CAST(JSON_EXTRACT(jt.f, '$.artifactId') AS SIGNED),
    CAST(JSON_EXTRACT(jt.f, '$.versionNo') AS SIGNED),
    JSON_UNQUOTE(JSON_EXTRACT(jt.f, '$.sha256')), JSON_UNQUOTE(JSON_EXTRACT(jt.f, '$.name')),
    'ASSOCIATED', 'ACTIVE', s.tenant_id, s.creator, s.create_time, NULL, s.create_time, b'0'
FROM acc_project_deliverable_submission s
JOIN acc_project_deliverable r ON r.id = s.deliverable_id AND r.deleted = 0
JOIN JSON_TABLE(s.source_evidence, '$.businessFiles[*]' COLUMNS (
        f JSON PATH '$', ref BIGINT PATH '$.referenceId', ord FOR ORDINALITY)) jt
LEFT JOIN plt_file_reference fr2 ON fr2.id = jt.ref
LEFT JOIN acc_acceptance_report_version arv ON arv.id = CAST(fr2.object_id AS UNSIGNED)
    AND fr2.owner_context = 'ACC' AND fr2.object_type = 'ACCEPTANCE_REPORT_VERSION'
LEFT JOIN acc_acceptance act ON act.id = arv.acceptance_id
WHERE s.source_type = 'BUSINESS_DOCUMENT'
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_material t
      WHERE t.requirement_id = s.deliverable_id AND t.business_object_id = CAST(jt.ref AS CHAR)
          AND t.material_kind = 'FILE' AND t.file_reference_id = jt.ref);

-- 3c) BUSINESS_RESULT 材料：成果锚材料，ID = 同一（要求 + resultId）内最早提交的 ID——对齐运行期
--     registerTemplateFrozenBusinessResult 的（要求+业务对象+修订）幂等：同一成果被多次提交共享同一材料行。
--     复合身份 formedAt 取成果冻结时间（秒级 ISO，对齐 EvidenceProvider 复合构造）。
INSERT INTO `plt_delivery_material` (`id`, `owner_module`, `entity_type`, `entity_id`, `type_code`,
    `material_kind`, `requirement_id`, `project_id`, `business_object_type`, `business_object_id`,
    `business_revision_no`, `source_kind`, `status`, `tenant_id`, `creator`, `create_time`, `updater`,
    `update_time`, `deleted`)
SELECT s.id, 'ACC', 'project_deliverable', s.project_id, r.deliverable_code, 'BUSINESS_RESULT',
    s.deliverable_id, s.project_id, 'project_business_result',
    CONCAT(ev.owner_ctx, '.', ev.entity_type, '.', ev.result_type, '|', ev.obj_id, '|',
           ev.result_id, '|', DATE_FORMAT(rev.frozen_at, '%Y-%m-%dT%H:%i:%s')),
    rev.revision_no, 'ASSOCIATED', 'ACTIVE', s.tenant_id, s.creator, s.create_time, NULL,
    s.create_time, b'0'
FROM acc_project_deliverable_submission s
JOIN acc_project_deliverable r ON r.id = s.deliverable_id AND r.deleted = 0
JOIN sol_requirement_analysis_revision rev ON rev.id = CAST(COALESCE(
    JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.resultId')),
    JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.resultId'))) AS UNSIGNED)
CROSS JOIN LATERAL (SELECT
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.ownerContext')),
                 JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.ownerContext'))) owner_ctx,
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.entityType')),
                 JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.entityType'))) entity_type,
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.resultType')),
                 JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.resultType'))) result_type,
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.objectId')),
                 JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.objectId'))) obj_id,
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.resultId')),
                 JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.resultId'))) result_id) ev
WHERE s.source_type = 'BUSINESS_RESULT'
  AND rev.revision_state = 'FROZEN'
  AND s.id = (SELECT MIN(s3.id) FROM acc_project_deliverable_submission s3
      WHERE s3.source_type = 'BUSINESS_RESULT' AND s3.deliverable_id = s.deliverable_id
        AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s3.request_payload, '$.businessResult.resultId')),
                     JSON_UNQUOTE(JSON_EXTRACT(s3.request_payload, '$.observation.result.resultId'))) = ev.result_id)
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_material t
      WHERE t.requirement_id = s.deliverable_id AND t.material_kind = 'BUSINESS_RESULT'
          AND t.business_object_id = CONCAT(ev.owner_ctx, '.', ev.entity_type, '.', ev.result_type,
              '|', ev.obj_id, '|', ev.result_id, '|', DATE_FORMAT(rev.frozen_at, '%Y-%m-%dT%H:%i:%s')));

-- 4) 提交台账：105 份提交（ID 保留 = source_version.id；状态 CURRENT/SUPERSEDED/REVOKED→WITHDRAWN）
INSERT INTO `plt_delivery_submission` (`id`, `requirement_id`, `request_key`, `material_ids_json`,
    `source_type`, `project_id`, `request_payload_json`, `decision_evidence_json`, `status`,
    `submit_evidence_json`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT s.source_version_id, s.deliverable_id, s.request_key, mats.material_ids, s.source_type,
    s.project_id, CAST(s.request_payload AS CHAR), CAST(s.decision_evidence AS CHAR),
    CASE sv.relation_status WHEN 'REVOKED' THEN 'WITHDRAWN' ELSE sv.relation_status END,
    NULL, s.tenant_id, s.creator, s.create_time, NULL, s.create_time, b'0'
FROM acc_project_deliverable_submission s
JOIN acc_project_deliverable_source_version sv ON sv.id = s.source_version_id AND sv.deleted = 0
JOIN acc_project_deliverable r ON r.id = s.deliverable_id AND r.deleted = 0
CROSS JOIN LATERAL (
    SELECT CASE s.source_type
        WHEN 'UPLOAD' THEN (SELECT JSON_ARRAYAGG(a2.id) FROM acc_project_deliverable_source_attachment a2
            WHERE a2.deliverable_source_version_id = sv.id AND a2.deleted = 0)
        WHEN 'BUSINESS_DOCUMENT' THEN (
            SELECT JSON_ARRAYAGG(m.id)
            FROM JSON_TABLE(s.source_evidence, '$.businessFiles[*]' COLUMNS (
                    ref BIGINT PATH '$.referenceId')) jt2
            JOIN plt_delivery_material m ON m.requirement_id = s.deliverable_id
                AND m.material_kind = 'FILE' AND m.file_reference_id = jt2.ref
                AND m.business_object_id = CAST(jt2.ref AS CHAR))
        ELSE (SELECT JSON_ARRAYAGG(m.id) FROM plt_delivery_material m
            WHERE m.requirement_id = s.deliverable_id AND m.material_kind = 'BUSINESS_RESULT'
              AND m.business_object_id = (
                SELECT CONCAT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.ownerContext')),
                                       JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.ownerContext'))), '.',
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.entityType')),
                                JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.entityType'))), '.',
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.type.resultType')),
                                JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.type.resultType'))), '|',
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.objectId')),
                                JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.objectId'))), '|',
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.resultId')),
                                JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.resultId'))), '|',
                       DATE_FORMAT(rev2.frozen_at, '%Y-%m-%dT%H:%i:%s'))
                FROM sol_requirement_analysis_revision rev2
                WHERE rev2.id = CAST(COALESCE(
                    JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.businessResult.resultId')),
                    JSON_UNQUOTE(JSON_EXTRACT(s.request_payload, '$.observation.result.resultId'))) AS UNSIGNED)))
    END AS material_ids
) mats
WHERE NOT EXISTS (SELECT 1 FROM plt_delivery_submission t WHERE t.id = s.source_version_id);
