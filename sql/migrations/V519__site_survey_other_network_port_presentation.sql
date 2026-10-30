-- TF-019: copy the current published presentation; preserve source revisions and existing bindings.
-- The manual description uses the existing networkPort business field. No data backfill.
INSERT INTO plt_dynamic_form_template_revision
(id, template_id, revision_no, status_code, draft_marker, source_revision_id,
 form_conf_json, form_rules_json, engine_code, designer_version, renderer_version,
 published_by, published_at, version, creator, updater, deleted, tenant_id)
SELECT 996610275190001, source.template_id,
 (SELECT COALESCE(MAX(existing.revision_no), 0) + 1 FROM plt_dynamic_form_template_revision existing
  WHERE existing.template_id = source.template_id AND existing.tenant_id = source.tenant_id),
 'PUBLISHED', NULL, source.id, source.form_conf_json,
 JSON_MERGE_PRESERVE(
   (SELECT JSON_ARRAYAGG(
     CASE WHEN item.field_name IN ('extra_networkPortTypes', 'networkPortTypes')
       THEN JSON_SET(item.rule, '$.options', JSON_ARRAY_APPEND(JSON_EXTRACT(item.rule, '$.options'), '$',
         JSON_OBJECT('label', '其他', 'value', 'OTHER')))
       ELSE item.rule END) OVER (
       ORDER BY item.position ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
    FROM JSON_TABLE(source.form_rules_json, '$[*]' COLUMNS (
      position FOR ORDINALITY, rule JSON PATH '$', field_name VARCHAR(128) PATH '$.field' NULL ON EMPTY
    )) item LIMIT 1),
   CASE WHEN EXISTS (SELECT 1 FROM JSON_TABLE(source.form_rules_json, '$[*]' COLUMNS (
     field_name VARCHAR(128) PATH '$.field' NULL ON EMPTY)) item WHERE item.field_name = 'networkPort')
   THEN JSON_ARRAY() ELSE JSON_ARRAY(JSON_OBJECT('type', 'input', 'field', 'networkPort', 'title', '其他网口说明',
     'props', JSON_OBJECT('type', 'textarea', 'rows', 2, 'maxlength', 500, 'showWordLimit', TRUE,
       'placeholder', '选择其他时可填写网口类型，例如100G光口'))) END),
 source.engine_code, source.designer_version, source.renderer_version,
 source.published_by, CURRENT_TIMESTAMP, 1, 'feedback-tf019-20261027', 'feedback-tf019-20261027', b'0', source.tenant_id
FROM plt_dynamic_form_template template
JOIN plt_dynamic_form_template_revision source
  ON source.id = template.current_published_revision_id AND source.template_id = template.id
  AND source.tenant_id = template.tenant_id
WHERE template.template_code = 'SITE_SURVEY_BUSINESS_UI' AND template.tenant_id = 1
  AND template.deleted = b'0' AND source.deleted = b'0' AND source.status_code = 'PUBLISHED'
  AND EXISTS (SELECT 1 FROM JSON_TABLE(source.form_rules_json, '$[*]' COLUMNS (
      field_name VARCHAR(128) PATH '$.field' NULL ON EMPTY, options JSON PATH '$.options' NULL ON EMPTY
    )) item WHERE item.field_name IN ('extra_networkPortTypes', 'networkPortTypes')
      AND JSON_TYPE(item.options) = 'ARRAY' AND NOT JSON_CONTAINS(item.options, JSON_OBJECT('value', 'OTHER')))
  AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision present WHERE present.id = 996610275190001);

UPDATE plt_dynamic_form_template template
JOIN plt_dynamic_form_template_revision revision
  ON revision.id = 996610275190001 AND revision.template_id = template.id AND revision.tenant_id = template.tenant_id
SET template.current_published_revision_id = revision.id, template.version = template.version + 1,
    template.updater = 'feedback-tf019-20261027', template.update_time = CURRENT_TIMESTAMP
WHERE template.template_code = 'SITE_SURVEY_BUSINESS_UI' AND template.tenant_id = 1 AND template.deleted = b'0'
  AND template.current_published_revision_id = revision.source_revision_id
  AND revision.status_code = 'PUBLISHED' AND revision.creator = 'feedback-tf019-20261027' AND revision.deleted = b'0';
