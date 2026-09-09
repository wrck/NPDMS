-- Independent site-survey restoration: preserve all existing entity rows and values.
ALTER TABLE pms_eng_site_survey
    MODIFY COLUMN conclusion MEDIUMTEXT NULL COMMENT '工勘结论（富文本）';

-- MySQL DDL is non-transactional: allow a retry after this migration's seed failure.
SET @survey_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pms_eng_site_survey' AND column_name = 'form_revision_id'), 'SELECT 1',
    'ALTER TABLE pms_eng_site_survey ADD COLUMN form_revision_id BIGINT NULL');
PREPARE survey_statement FROM @survey_ddl;
EXECUTE survey_statement;
DEALLOCATE PREPARE survey_statement;
SET @survey_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pms_eng_site_survey' AND column_name = 'form_revision_version'), 'SELECT 1',
    'ALTER TABLE pms_eng_site_survey ADD COLUMN form_revision_version INT NULL');
PREPARE survey_statement FROM @survey_ddl;
EXECUTE survey_statement;
DEALLOCATE PREPARE survey_statement;
SET @survey_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pms_eng_site_survey' AND column_name = 'form_extra_values'), 'SELECT 1',
    'ALTER TABLE pms_eng_site_survey ADD COLUMN form_extra_values JSON NULL');
PREPARE survey_statement FROM @survey_ddl;
EXECUTE survey_statement;
DEALLOCATE PREPARE survey_statement;

INSERT INTO plt_dynamic_form_template
(id, template_code, template_name, category_code, description, availability_code, current_published_revision_id, version, creator, updater, deleted, tenant_id)
SELECT 993109090001, 'SITE_SURVEY_ENTITY_STANDARD', '现场工勘完整业务表单', 'SITE_SURVEY', '原工勘实体字段；只配置表单，不保存业务正文', 'ENABLED', NULL, 1, 'site-survey-restoration', 'site-survey-restoration', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template WHERE id = 993109090001);

INSERT INTO plt_dynamic_form_template_revision
(id, template_id, revision_no, status_code, draft_marker, form_conf_json, form_rules_json, engine_code, designer_version, renderer_version, published_by, published_at, version, creator, updater, deleted, tenant_id)
SELECT 993109090002, 993109090001, 1, 'PUBLISHED', NULL,
 '{"form":{"labelPosition":"top"},"submitBtn":false,"resetBtn":false}',
 '[{"type":"input","field":"powerSupply","title":"供电情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"cabinet","title":"机柜情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"networkPort","title":"网口情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"fiber","title":"光纤情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"module","title":"模块情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"cable","title":"线缆情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"ground","title":"接地情况","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"input","field":"constructionResource","title":"施工资源","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}},{"type":"Editor","field":"conclusion","title":"工勘结论","props":{"height":"200px","editorId":"site-survey-conclusion"},"col":{"span":24}},{"type":"input","field":"remark","title":"备注","props":{"type":"textarea","rows":2,"maxlength":500,"showWordLimit":true},"col":{"span":24}}]',
 'FORM_CREATE_ELEMENT_PLUS', '3.4.0', '3.2.38', 1, CURRENT_TIMESTAMP, 1, 'site-survey-restoration', 'site-survey-restoration', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision WHERE id = 993109090002);

UPDATE plt_dynamic_form_template SET current_published_revision_id = 993109090002
WHERE id = 993109090001 AND tenant_id = 1 AND current_published_revision_id IS NULL
  AND creator = 'site-survey-restoration';
