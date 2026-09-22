-- PRE-04 / F-SOL-003: tenant-specific module configuration for independent creation.
-- Reuse the existing standard form; preserve administrator configuration and all publications/history.
INSERT INTO infra_config
    (id, category, type, name, config_key, value, visible, remark, creator, updater, deleted)
SELECT 992209220338, 'pms', 2, '需求分析独立入口表单（租户1）',
       'pms.requirement-analysis.form-template-id.1', CAST(t.id AS CHAR), b'0',
       '首次创建冻结该模板当前启用且兼容的已发布修订；已有需求分析不随配置变化。',
       'seed_requirement_independent', 'seed_requirement_independent', b'0'
FROM plt_dynamic_form_template t
WHERE t.tenant_id = 1 AND t.deleted = b'0'
  AND t.template_code = 'SOL_PRE04_REQUIREMENT_ANALYSIS_V1'
  AND NOT EXISTS (SELECT 1 FROM infra_config c
                  WHERE c.config_key = 'pms.requirement-analysis.form-template-id.1' AND c.deleted = b'0');
