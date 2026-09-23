-- Preparation input is already owned by the site survey. Keep all published revisions and records.
UPDATE plt_dynamic_form_template
SET availability_code = 'DISABLED', version = version + 1,
    updater = 'retire-preparation-20260922', update_time = CURRENT_TIMESTAMP
WHERE id = 992209220342 AND tenant_id = 1 AND creator = 'seed_preparation_confirmation'
  AND availability_code = 'ENABLED' AND deleted = b'0';

-- Add display-only branch configuration to a new survey form revision.
-- User mapping: item 8 -> original manufacturer branch; item 9 -> BOM/material branch.
-- No new input fields and no changes to previously bound form revisions.
INSERT INTO plt_dynamic_form_template_revision
(id, template_id, revision_no, status_code, draft_marker, source_revision_id,
 form_conf_json, form_rules_json, engine_code, designer_version, renderer_version,
 published_by, published_at, version, creator, updater, deleted, tenant_id)
SELECT 992209220346, template_id, 2, 'PUBLISHED', NULL, id,
 form_conf_json, JSON_ARRAY_APPEND(form_rules_json, '$', CAST(
 '{"type":"SurveyPreparationBranches","props":{"branches":[{"title":"CRM 改单（独立流程）","when":{"extra_materialMatches":false}},{"title":"物料申领（独立流程）","when":{"extra_manufacturerInstallation":true,"extra_railTrayRequired":true}},{"title":"外采事前申请（独立流程）","when":{"extra_manufacturerInstallation":true,"extra_railTrayRequired":false}}]}}' AS JSON)),
 engine_code, designer_version, renderer_version, 1, CURRENT_TIMESTAMP, 1,
 'survey-preparation-20260922', 'survey-preparation-20260922', b'0', tenant_id
FROM plt_dynamic_form_template_revision source
WHERE source.id = 993109090007 AND source.tenant_id = 1 AND source.status_code = 'PUBLISHED'
  AND NOT EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision present
                  WHERE present.id = 992209220346 OR (present.template_id = source.template_id AND present.revision_no = 2));

UPDATE plt_dynamic_form_template
SET current_published_revision_id = 992209220346, version = version + 1,
    updater = 'survey-preparation-20260922', update_time = CURRENT_TIMESTAMP
WHERE id = 993109090006 AND tenant_id = 1 AND creator = 'site-survey-restoration'
  AND current_published_revision_id = 993109090007
  AND EXISTS (SELECT 1 FROM plt_dynamic_form_template_revision revision
              WHERE revision.id = 992209220346 AND revision.template_id = 993109090006
                AND revision.creator = 'survey-preparation-20260922');
