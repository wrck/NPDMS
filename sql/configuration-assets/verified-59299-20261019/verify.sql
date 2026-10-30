-- Read-only review after V502; native publication may have advanced DRAFT states.
SELECT version,description,type,success
FROM flyway_schema_history WHERE version='502';

SELECT t.template_code,t.availability_code,t.current_published_revision_id,
       r.id AS imported_revision_id,r.revision_no,r.status_code
FROM plt_dynamic_form_template t
JOIN plt_dynamic_form_template_revision r ON r.tenant_id=t.tenant_id AND r.template_id=t.id
WHERE r.creator='migration-v502-59299-config' AND t.tenant_id=1 AND t.deleted=0 AND r.deleted=0
ORDER BY t.template_code,r.revision_no;

SELECT stage_code,signing_method,source_type,reference_stage_code,offset_months,offset_days,enabled
FROM proj_stage_suggestion_rule
WHERE creator='migration-v502-59299-config' AND tenant_id=1 AND deleted=0
ORDER BY stage_code,signing_method;

SELECT id,configuration_code,revision_no,status_code,validation_valid_flag,
       (SELECT COUNT(*) FROM cut_cutover_checklist_item_definition_revision i
        WHERE i.configuration_revision_id=c.id AND i.tenant_id=c.tenant_id AND i.deleted=0) AS item_count,
       (SELECT COUNT(*) FROM cut_cutover_checklist_binding_rule_revision r
        WHERE r.configuration_revision_id=c.id AND r.tenant_id=c.tenant_id AND r.deleted=0) AS rule_count
FROM cut_cutover_configuration_revision c
WHERE c.creator='migration-v502-59299-config' AND c.tenant_id=1 AND c.deleted=0;

SELECT t.code,t.status,r.revision_no,r.status AS revision_status,
       JSON_LENGTH(r.designer_document,'$.stages') AS stages,
       JSON_LENGTH(r.designer_document,'$.tasks') AS tasks,
       JSON_LENGTH(r.designer_document,'$.deliverables') AS deliverables,
       JSON_LENGTH(r.designer_document,'$.gates') AS gates
FROM proj_project_template t
JOIN proj_project_template_revision r ON r.tenant_id=t.tenant_id AND r.template_id=t.id
WHERE t.creator='migration-v502-59299-config' AND t.tenant_id=1 AND t.deleted=0 AND r.deleted=0
ORDER BY r.revision_no;

SELECT 'orphan_imported_cut_fields' AS check_name,COUNT(*) AS violations
FROM cut_checklist_field_definition f
LEFT JOIN cut_cutover_checklist_item_definition_revision i
  ON i.tenant_id=f.tenant_id AND i.id=f.item_definition_revision_id
WHERE f.creator='migration-v502-59299-config' AND f.deleted=0 AND i.id IS NULL;

SELECT 'orphan_imported_cut_binding_conditions' AS check_name,COUNT(*) AS violations
FROM cut_checklist_binding_condition c
LEFT JOIN cut_configuration_dimension d ON d.tenant_id=c.tenant_id AND d.id=c.configuration_dimension_id
LEFT JOIN cut_cutover_checklist_binding_rule_revision r ON r.tenant_id=c.tenant_id AND r.id=c.binding_rule_revision_id
WHERE c.creator='migration-v502-59299-config' AND c.deleted=0 AND (d.id IS NULL OR r.id IS NULL);

SELECT 'orphan_imported_cut_parent_fields' AS check_name,COUNT(*) AS violations
FROM cut_checklist_field_definition f
LEFT JOIN cut_checklist_field_definition p ON p.tenant_id=f.tenant_id AND p.id=f.parent_field_id
WHERE f.creator='migration-v502-59299-config' AND f.deleted=0
  AND f.parent_field_id IS NOT NULL AND p.id IS NULL;
