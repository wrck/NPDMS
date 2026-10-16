-- 在线方案的生成文件独立记录，不复用完整上传方案列，不改变原表校验与历史。
CREATE TABLE cut_plan_generated_file (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    plan_revision_id BIGINT NOT NULL,
    plan_version BIGINT NOT NULL,
    artifact_id BIGINT NOT NULL,
    file_version_no INT NOT NULL,
    reference_key VARCHAR(128) NOT NULL,
    artifact_fact_version INT NOT NULL,
    reference_fact_version INT NOT NULL,
    availability_fact_version INT NOT NULL,
    scope_version BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    generated_at BIGINT NOT NULL COMMENT '生成成功时间，EpochMillis',
    creator BIGINT NOT NULL,
    KEY idx_cut_plan_generated_version (tenant_id, plan_revision_id, plan_version, generated_at, id),
    CONSTRAINT chk_cut_plan_generated_fact CHECK (plan_version >= 0 AND artifact_id > 0 AND file_version_no > 0
        AND artifact_fact_version >= 0 AND reference_fact_version >= 0 AND availability_fact_version >= 0
        AND scope_version >= 0 AND generated_at > 0 AND CHAR_LENGTH(TRIM(reference_key)) > 0
        AND sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CUT在线方案按正文版本保存的生成文件引用';

-- 只从既有成功审计恢复同一当前草稿正文的事实；不改写审计、文件或批准历史。
INSERT INTO cut_plan_generated_file
    (id, tenant_id, task_id, plan_revision_id, plan_version, artifact_id, file_version_no, reference_key,
     artifact_fact_version, reference_fact_version, availability_fact_version, scope_version, sha256, generated_at, creator)
SELECT a.id, p.tenant_id, p.task_id, p.id, p.version, r.artifact_id, r.file_version_no, r.reference_key,
       f.version, r.version, v.availability_version, r.scope_version, v.sha256, a.generated_at, a.actor_id
FROM cut_plan_revision p
JOIN (
    SELECT a.id, a.tenant_id, a.aggregate_key, a.actor_id,
           JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.planVersion')) AS plan_version,
           JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.fileArtifactFact.artifactId')) AS artifact_id,
           JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.fileArtifactFact.versionNo')) AS file_version_no,
           JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.fileArtifactFact.referenceKey')) AS reference_key,
           JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.downloadedAt')) AS generated_at,
           ROW_NUMBER() OVER (PARTITION BY a.tenant_id, a.aggregate_key,
               JSON_UNQUOTE(JSON_EXTRACT(a.detail_snapshot, '$.planVersion'))
               ORDER BY a.occurred_at DESC, a.id DESC) AS rn
    FROM plt_operation_audit a
    WHERE a.operation_code = 'CUTOVER_PLAN_DRAFT_DOWNLOAD'
      AND a.aggregate_type = 'CutoverPlanRevision' AND a.result_code = 'SUCCESS'
      AND JSON_VALID(a.detail_snapshot)
) a ON a.tenant_id = p.tenant_id AND CAST(a.aggregate_key AS UNSIGNED) = p.id
    AND CAST(a.plan_version AS UNSIGNED) = p.version AND a.rn = 1 AND a.generated_at REGEXP '^[0-9]+$'
JOIN plt_file_reference r ON r.tenant_id = p.tenant_id AND r.artifact_id = CAST(a.artifact_id AS UNSIGNED)
    AND r.file_version_no = CAST(a.file_version_no AS UNSIGNED) AND CAST(r.reference_key AS BINARY) = CAST(a.reference_key AS BINARY)
    AND r.owner_context = 'CUT' AND r.object_type = 'CUTOVER_PLAN'
    AND CAST(r.object_id AS BINARY) = CAST(CAST(p.task_id AS CHAR) AS BINARY) AND r.purpose_code = 'FULL_PLAN' AND r.status_code = 'ACTIVE'
JOIN plt_file_version v ON v.tenant_id = p.tenant_id AND v.artifact_id = r.artifact_id
    AND v.version_no = r.file_version_no AND v.availability_status_code = 'AVAILABLE'
JOIN plt_file_artifact f ON f.tenant_id = p.tenant_id AND f.id = r.artifact_id AND f.deleted = b'0'
WHERE p.deleted = b'0' AND p.current_marker = 1 AND p.status_code = 'DRAFT'
  AND p.edit_mode_code IN ('ONLINE_TEMPLATE_STANDARD', 'ONLINE_TEMPLATE_SIMPLE_D')
  AND NOT EXISTS (SELECT 1 FROM cut_plan_generated_file g
      WHERE g.tenant_id = p.tenant_id AND g.plan_revision_id = p.id AND g.plan_version = p.version);
