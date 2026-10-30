-- Publish a new revision; published source schemas and bound historical records remain immutable.
INSERT INTO plt_dynamic_form_template_revision
(id, template_id, revision_no, status_code, draft_marker, source_revision_id,
 form_conf_json, form_rules_json, engine_code, designer_version, renderer_version,
 published_by, published_at, version, creator, updater, deleted, tenant_id)
SELECT 993110100426, source.template_id, source.revision_no + 1, 'PUBLISHED', NULL, source.id,
 source.form_conf_json,
 (SELECT JSON_ARRAYAGG(items.rule) OVER (
     ORDER BY items.position ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
  FROM JSON_TABLE(source.form_rules_json, '$[*]' COLUMNS (
    position FOR ORDINALITY,
    rule JSON PATH '$',
    field_name VARCHAR(128) PATH '$.field' NULL ON EMPTY,
    heading VARCHAR(255) PATH '$.props.innerText' NULL ON EMPTY
  )) items
  WHERE COALESCE(items.field_name, '') NOT IN
    ('powerSupply', 'cabinet', 'networkPort', 'fiber', 'module', 'cable', 'ground', 'constructionResource')
    AND COALESCE(items.heading, '') <> '原工勘补充说明（保留原字段内容）'
  LIMIT 1),
 source.engine_code, source.designer_version, source.renderer_version,
 source.published_by, CURRENT_TIMESTAMP, 1,
 'survey-supplements-20261010', 'survey-supplements-20261010', b'0', source.tenant_id
FROM plt_dynamic_form_template_revision source
JOIN plt_dynamic_form_template template
  ON template.id = source.template_id AND template.tenant_id = source.tenant_id
WHERE source.id = 992209220346 AND source.template_id = 993109090006
  AND source.tenant_id = 1 AND source.status_code = 'PUBLISHED' AND source.deleted = b'0'
  AND template.current_published_revision_id = source.id AND template.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision present
    WHERE present.id = 993110100426
       OR (present.template_id = source.template_id AND present.tenant_id = source.tenant_id
           AND present.revision_no = source.revision_no + 1));

UPDATE plt_dynamic_form_template template
JOIN plt_dynamic_form_template_revision revision
  ON revision.id = 993110100426 AND revision.template_id = template.id
  AND revision.tenant_id = template.tenant_id
SET template.current_published_revision_id = revision.id, template.version = template.version + 1,
    template.updater = 'survey-supplements-20261010', template.update_time = CURRENT_TIMESTAMP
WHERE template.id = 993109090006 AND template.tenant_id = 1
  AND template.current_published_revision_id = 992209220346 AND template.deleted = b'0'
  AND revision.source_revision_id = 992209220346 AND revision.status_code = 'PUBLISHED'
  AND revision.creator = 'survey-supplements-20261010' AND revision.deleted = b'0';
