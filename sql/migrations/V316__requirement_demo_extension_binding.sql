-- Repair only editable R2 requirement drafts; published layouts and frozen business history remain immutable.
INSERT INTO plt_entity_extension_definition
(id, tenant_id, owner_module, entity_type, revision_no, source_form_revision_id, fields_json, creator, updater)
SELECT 992203020106, 1, 'SOL', 'REQUIREMENT_ANALYSIS',
       COALESCE(MAX(d.revision_no),0)+1, 992203020005, '[{"code":"TRANSMISSION_CURRENT_OPTIONS","label":"TRANSMISSION_CURRENT_OPTIONS","type":"TEXT_LIST","required":false,"maxLength":null,"allowedValues":["IPv6","分片","MTU","Jumbo","隧道"]},{"code":"TRAFFIC_NEW_CONNECTIONS","label":"TRAFFIC_NEW_CONNECTIONS","type":"TEXT","required":false,"maxLength":null,"allowedValues":[]},{"code":"TRAFFIC_CONCURRENCY","label":"TRAFFIC_CONCURRENCY","type":"TEXT","required":false,"maxLength":null,"allowedValues":[]},{"code":"TRAFFIC_THROUGHPUT","label":"TRAFFIC_THROUGHPUT","type":"TEXT","required":false,"maxLength":null,"allowedValues":[]},{"code":"BUSINESS_DEVICE_DETAILS","label":"BUSINESS_DEVICE_DETAILS","type":"OBJECT_LIST","required":false,"maxLength":null,"allowedValues":[]},{"code":"IP_MANAGEMENT_RESOURCES","label":"IP_MANAGEMENT_RESOURCES","type":"TEXT","required":false,"maxLength":null,"allowedValues":[]},{"code":"IP_PUBLIC_RESOURCES","label":"IP_PUBLIC_RESOURCES","type":"TEXT","required":false,"maxLength":null,"allowedValues":[]},{"code":"OPERATIONS_MANAGEMENT_OPTIONS","label":"OPERATIONS_MANAGEMENT_OPTIONS","type":"TEXT_LIST","required":false,"maxLength":null,"allowedValues":["带内管理","带外管理","SNMP","UMC","第三平台","堡垒机"]}]', 'project_detail_repair', 'project_detail_repair'
FROM plt_entity_extension_definition d
WHERE d.tenant_id=1 AND d.owner_module='SOL' AND d.entity_type='REQUIREMENT_ANALYSIS'
HAVING NOT EXISTS (SELECT 1 FROM plt_entity_extension_definition e
 WHERE e.tenant_id=1 AND e.owner_module='SOL' AND e.entity_type='REQUIREMENT_ANALYSIS'
 AND e.source_form_revision_id=992203020005);

UPDATE plt_entity_form_binding b
JOIN sol_requirement_analysis_revision r ON r.tenant_id=b.tenant_id AND r.id=b.revision_id AND r.entity_id=b.entity_id
JOIN plt_entity_extension_definition d ON d.tenant_id=b.tenant_id AND d.owner_module=b.owner_module
 AND d.entity_type=b.entity_type AND d.source_form_revision_id=b.form_revision_id AND d.deleted=b'0'
SET b.extension_definition_revision_id=d.id,
 b.field_bindings_json=JSON_MERGE_PATCH(b.field_bindings_json, '{"TRANSMISSION_CURRENT_OPTIONS":"TRANSMISSION_CURRENT_OPTIONS","TRAFFIC_NEW_CONNECTIONS":"TRAFFIC_NEW_CONNECTIONS","TRAFFIC_CONCURRENCY":"TRAFFIC_CONCURRENCY","TRAFFIC_THROUGHPUT":"TRAFFIC_THROUGHPUT","BUSINESS_DEVICE_DETAILS":"BUSINESS_DEVICE_DETAILS","IP_MANAGEMENT_RESOURCES":"IP_MANAGEMENT_RESOURCES","IP_PUBLIC_RESOURCES":"IP_PUBLIC_RESOURCES","OPERATIONS_MANAGEMENT_OPTIONS":"OPERATIONS_MANAGEMENT_OPTIONS"}'),
 b.version=b.version+1, b.updater='project_detail_repair', b.update_time=NOW(3)
WHERE b.tenant_id=1 AND b.owner_module='SOL' AND b.entity_type='REQUIREMENT_ANALYSIS'
 AND b.form_revision_id=992203020005 AND b.extension_definition_revision_id IS NULL
 AND b.deleted=b'0' AND r.deleted=b'0' AND r.revision_state='DRAFT' AND r.frozen_at IS NULL;
