-- Read-only checks immediately after B498 / optional administrator bootstrap.
-- Zero rows from each violations query means PASS. Do not use seed-count checks after business use.
-- This verifies database release integrity, not workflow deployment or browser/business acceptance.
SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;
SELECT version,type,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT 'table_count' AS check_name,461 AS expected,COUNT(*) AS actual
FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history';
SELECT 'trigger_count' AS check_name,238 AS expected,COUNT(*) AS actual
FROM information_schema.triggers WHERE trigger_schema=DATABASE();

-- Removed legacy objects: each query must return zero rows.
SELECT 'excluded_legacy_table' AS check_name, table_name AS object_name
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN (
  'pms_acc_maintenance_transition_retired',
  'pms_customer_contact_retired',
  'pms_customer_retired',
  'pms_cut_execution_retired',
  'pms_cut_observation_retired',
  'pms_cut_plan_retired',
  'pms_cut_risk_retired',
  'pms_cut_task_retired',
  'pms_equipment_retired',
  'pms_equipment_version_retired',
  'pms_plan_change_phase_snapshot_retired',
  'pms_plan_change_request_retired',
  'pms_project_phase_retired',
  'pms_project_phase_template_retired',
  'pms_project_retired',
  'pms_project_sync_batch_retired',
  'pms_project_sync_detail_retired',
  'pms_project_task_dependency_retired',
  'pms_project_task_retired',
  'pms_project_team_member_retired',
  'pms_project_template_retired',
  'pms_project_tree_change_batch_retired',
  'pms_srv_execution_retired',
  'pms_srv_issue_retired',
  'pms_srv_maintenance_retired',
  'pms_srv_offline_file_retired',
  'pms_srv_report_retired',
  'pms_srv_rule_retired',
  'pms_srv_task_retired'
);
SELECT 'excluded_legacy_trigger' AS check_name, trigger_name AS object_name
FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND trigger_name IN (
  'trg_pms_acc_maintenance_transition_ro_delete',
  'trg_pms_acc_maintenance_transition_ro_insert',
  'trg_pms_acc_maintenance_transition_ro_update',
  'trg_pms_customer_contact_ro_delete',
  'trg_pms_customer_contact_ro_insert',
  'trg_pms_customer_contact_ro_update',
  'trg_pms_customer_ro_delete',
  'trg_pms_customer_ro_insert',
  'trg_pms_customer_ro_update',
  'trg_pms_cut_execution_ro_delete',
  'trg_pms_cut_execution_ro_insert',
  'trg_pms_cut_execution_ro_update',
  'trg_pms_cut_observation_ro_delete',
  'trg_pms_cut_observation_ro_insert',
  'trg_pms_cut_observation_ro_update',
  'trg_pms_cut_plan_ro_delete',
  'trg_pms_cut_plan_ro_insert',
  'trg_pms_cut_plan_ro_update',
  'trg_pms_cut_risk_ro_delete',
  'trg_pms_cut_risk_ro_insert',
  'trg_pms_cut_risk_ro_update',
  'trg_pms_cut_task_ro_delete',
  'trg_pms_cut_task_ro_insert',
  'trg_pms_cut_task_ro_update',
  'trg_pms_equipment_ro_delete',
  'trg_pms_equipment_ro_insert',
  'trg_pms_equipment_ro_update',
  'trg_pms_equipment_version_ro_delete',
  'trg_pms_equipment_version_ro_insert',
  'trg_pms_equipment_version_ro_update',
  'trg_pms_plan_change_phase_snapshot_ro_delete',
  'trg_pms_plan_change_phase_snapshot_ro_insert',
  'trg_pms_plan_change_phase_snapshot_ro_update',
  'trg_pms_plan_change_request_ro_delete',
  'trg_pms_plan_change_request_ro_insert',
  'trg_pms_plan_change_request_ro_update',
  'trg_pms_project_phase_ro_delete',
  'trg_pms_project_phase_ro_insert',
  'trg_pms_project_phase_ro_update',
  'trg_pms_project_phase_template_ro_delete',
  'trg_pms_project_phase_template_ro_insert',
  'trg_pms_project_phase_template_ro_update',
  'trg_pms_project_ro_delete',
  'trg_pms_project_ro_insert',
  'trg_pms_project_ro_update',
  'trg_pms_project_sync_batch_ro_delete',
  'trg_pms_project_sync_batch_ro_insert',
  'trg_pms_project_sync_batch_ro_update',
  'trg_pms_project_sync_detail_ro_delete',
  'trg_pms_project_sync_detail_ro_insert',
  'trg_pms_project_sync_detail_ro_update',
  'trg_pms_project_task_dependency_ro_delete',
  'trg_pms_project_task_dependency_ro_insert',
  'trg_pms_project_task_dependency_ro_update',
  'trg_pms_project_task_ro_delete',
  'trg_pms_project_task_ro_insert',
  'trg_pms_project_task_ro_update',
  'trg_pms_project_team_member_ro_delete',
  'trg_pms_project_team_member_ro_insert',
  'trg_pms_project_team_member_ro_update',
  'trg_pms_project_template_ro_delete',
  'trg_pms_project_template_ro_insert',
  'trg_pms_project_template_ro_update',
  'trg_pms_project_tree_change_batch_ro_delete',
  'trg_pms_project_tree_change_batch_ro_insert',
  'trg_pms_project_tree_change_batch_ro_update',
  'trg_pms_srv_execution_ro_delete',
  'trg_pms_srv_execution_ro_insert',
  'trg_pms_srv_execution_ro_update',
  'trg_pms_srv_issue_ro_delete',
  'trg_pms_srv_issue_ro_insert',
  'trg_pms_srv_issue_ro_update',
  'trg_pms_srv_maintenance_ro_delete',
  'trg_pms_srv_maintenance_ro_insert',
  'trg_pms_srv_maintenance_ro_update',
  'trg_pms_srv_offline_file_ro_delete',
  'trg_pms_srv_offline_file_ro_insert',
  'trg_pms_srv_offline_file_ro_update',
  'trg_pms_srv_report_ro_delete',
  'trg_pms_srv_report_ro_insert',
  'trg_pms_srv_report_ro_update',
  'trg_pms_srv_rule_ro_delete',
  'trg_pms_srv_rule_ro_insert',
  'trg_pms_srv_rule_ro_update',
  'trg_pms_srv_task_ro_delete',
  'trg_pms_srv_task_ro_insert',
  'trg_pms_srv_task_ro_update'
);

-- Exact seed counts and empty business/audit/runtime tables.
SELECT * FROM (
SELECT 'QRTZ_BLOB_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_BLOB_TRIGGERS` UNION ALL
SELECT 'QRTZ_CALENDARS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_CALENDARS` UNION ALL
SELECT 'QRTZ_CRON_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_CRON_TRIGGERS` UNION ALL
SELECT 'QRTZ_FIRED_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_FIRED_TRIGGERS` UNION ALL
SELECT 'QRTZ_JOB_DETAILS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_JOB_DETAILS` UNION ALL
SELECT 'QRTZ_LOCKS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_LOCKS` UNION ALL
SELECT 'QRTZ_PAUSED_TRIGGER_GRPS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_PAUSED_TRIGGER_GRPS` UNION ALL
SELECT 'QRTZ_SCHEDULER_STATE' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_SCHEDULER_STATE` UNION ALL
SELECT 'QRTZ_SIMPLE_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_SIMPLE_TRIGGERS` UNION ALL
SELECT 'QRTZ_SIMPROP_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_SIMPROP_TRIGGERS` UNION ALL
SELECT 'QRTZ_TRIGGERS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `QRTZ_TRIGGERS` UNION ALL
SELECT 'acc_acceptance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_acceptance` UNION ALL
SELECT 'acc_acceptance_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_acceptance_record` UNION ALL
SELECT 'acc_acceptance_report_attachment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_acceptance_report_attachment` UNION ALL
SELECT 'acc_acceptance_report_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_acceptance_report_version` UNION ALL
SELECT 'acc_acceptance_scope_binding' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_acceptance_scope_binding` UNION ALL
SELECT 'acc_archive_document' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_archive_document` UNION ALL
SELECT 'acc_closure_gate_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_closure_gate_snapshot` UNION ALL
SELECT 'acc_closure_review' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_closure_review` UNION ALL
SELECT 'acc_completion_certificate' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_completion_certificate` UNION ALL
SELECT 'acc_completion_certificate_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_completion_certificate_device` UNION ALL
SELECT 'acc_completion_pdf_binding' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_completion_pdf_binding` UNION ALL
SELECT 'acc_completion_satisfaction_bundle' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_completion_satisfaction_bundle` UNION ALL
SELECT 'acc_completion_satisfaction_signing' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_completion_satisfaction_signing` UNION ALL
SELECT 'acc_deliverable_checklist' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_deliverable_checklist` UNION ALL
SELECT 'acc_project_closure' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_closure` UNION ALL
SELECT 'acc_project_closure_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_closure_record` UNION ALL
SELECT 'acc_project_deliverable' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_deliverable` UNION ALL
SELECT 'acc_project_deliverable_source_attachment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_deliverable_source_attachment` UNION ALL
SELECT 'acc_project_deliverable_source_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_deliverable_source_version` UNION ALL
SELECT 'acc_project_deliverable_submission' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_project_deliverable_submission` UNION ALL
SELECT 'acc_satisfaction_access_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_access_grant` UNION ALL
SELECT 'acc_satisfaction_collection_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_collection_task` UNION ALL
SELECT 'acc_satisfaction_questionnaire' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_questionnaire` UNION ALL
SELECT 'acc_satisfaction_questionnaire_template' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_questionnaire_template` UNION ALL
SELECT 'acc_satisfaction_questionnaire_template_revision' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_questionnaire_template_revision` UNION ALL
SELECT 'acc_satisfaction_remediation_fact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_remediation_fact` UNION ALL
SELECT 'acc_satisfaction_response' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_response` UNION ALL
SELECT 'acc_satisfaction_response_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_response_file` UNION ALL
SELECT 'acc_satisfaction_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_result` UNION ALL
SELECT 'acc_satisfaction_result_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `acc_satisfaction_result_file` UNION ALL
SELECT 'ast_address' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_address` UNION ALL
SELECT 'ast_area_department_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_area_department_mapping` UNION ALL
SELECT 'ast_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device` UNION ALL
SELECT 'ast_device_ancestor_projection_operation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_ancestor_projection_operation` UNION ALL
SELECT 'ast_device_assembly' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_assembly` UNION ALL
SELECT 'ast_device_assembly_graph_lock' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_assembly_graph_lock` UNION ALL
SELECT 'ast_device_assignment_reconciliation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_assignment_reconciliation` UNION ALL
SELECT 'ast_device_config_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_config_log` UNION ALL
SELECT 'ast_device_current_product_type' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_current_product_type` UNION ALL
SELECT 'ast_device_customer_relationship' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_customer_relationship` UNION ALL
SELECT 'ast_device_cutover_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_cutover_result` UNION ALL
SELECT 'ast_device_cutover_result_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_cutover_result_file` UNION ALL
SELECT 'ast_device_download_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_download_grant` UNION ALL
SELECT 'ast_device_factory_info' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_factory_info` UNION ALL
SELECT 'ast_device_factory_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_factory_version` UNION ALL
SELECT 'ast_device_location' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_location` UNION ALL
SELECT 'ast_device_log_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_file` UNION ALL
SELECT 'ast_device_log_import' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_import` UNION ALL
SELECT 'ast_device_log_parse_job' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_parse_job` UNION ALL
SELECT 'ast_device_log_parse_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_parse_snapshot` UNION ALL
SELECT 'ast_device_log_parsed_fact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_parsed_fact` UNION ALL
SELECT 'ast_device_log_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_log_version` UNION ALL
SELECT 'ast_device_network_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_network_version` UNION ALL
SELECT 'ast_device_network_version_event' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_network_version_event` UNION ALL
SELECT 'ast_device_project_ancestor' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_project_ancestor` UNION ALL
SELECT 'ast_device_project_relationship' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_project_relationship` UNION ALL
SELECT 'ast_device_relationship' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_relationship` UNION ALL
SELECT 'ast_device_shipment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_shipment` UNION ALL
SELECT 'ast_device_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_version` UNION ALL
SELECT 'ast_device_warranty' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_warranty` UNION ALL
SELECT 'ast_device_warranty_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_device_warranty_record` UNION ALL
SELECT 'ast_location_code_sequence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_location_code_sequence` UNION ALL
SELECT 'ast_location_source_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_location_source_mapping` UNION ALL
SELECT 'ast_product_official_info' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_product_official_info` UNION ALL
SELECT 'ast_product_official_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_product_official_version` UNION ALL
SELECT 'ast_product_type' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_product_type` UNION ALL
SELECT 'ast_product_type_source_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_product_type_source_mapping` UNION ALL
SELECT 'ast_site' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_site` UNION ALL
SELECT 'ast_site_location' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `ast_site_location` UNION ALL
SELECT 'bpm_category' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_category` UNION ALL
SELECT 'bpm_form' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_form` UNION ALL
SELECT 'bpm_oa_leave' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_oa_leave` UNION ALL
SELECT 'bpm_process_definition_info' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_process_definition_info` UNION ALL
SELECT 'bpm_process_expression' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_process_expression` UNION ALL
SELECT 'bpm_process_instance_copy' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_process_instance_copy` UNION ALL
SELECT 'bpm_process_listener' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_process_listener` UNION ALL
SELECT 'bpm_user_group' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `bpm_user_group` UNION ALL
SELECT 'com_authority_candidate' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_authority_candidate` UNION ALL
SELECT 'com_contract' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_contract` UNION ALL
SELECT 'com_contract_receivable' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_contract_receivable` UNION ALL
SELECT 'com_crm_execution_order' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_crm_execution_order` UNION ALL
SELECT 'com_delivery_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_delivery_scope` UNION ALL
SELECT 'com_delivery_scope_detail' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_delivery_scope_detail` UNION ALL
SELECT 'com_delivery_scope_project_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_delivery_scope_project_version` UNION ALL
SELECT 'com_order_contract_relation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_order_contract_relation` UNION ALL
SELECT 'com_outbox_event' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_outbox_event` UNION ALL
SELECT 'com_project_contract_relation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_project_contract_relation` UNION ALL
SELECT 'com_sales_order' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_sales_order` UNION ALL
SELECT 'com_sales_order_line' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_sales_order_line` UNION ALL
SELECT 'com_shipment_contract_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_shipment_contract_reference` UNION ALL
SELECT 'com_shipment_package' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `com_shipment_package` UNION ALL
SELECT 'cus_contact_history' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_contact_history` UNION ALL
SELECT 'cus_customer_contact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_contact` UNION ALL
SELECT 'cus_customer_external_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_external_mapping` UNION ALL
SELECT 'cus_customer_field_history' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_field_history` UNION ALL
SELECT 'cus_customer_location_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_location_reference` UNION ALL
SELECT 'cus_customer_master' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_master` UNION ALL
SELECT 'cus_customer_scope_slice' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_scope_slice` UNION ALL
SELECT 'cus_customer_service_level' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_customer_service_level` UNION ALL
SELECT 'cus_inspection_history' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_inspection_history` UNION ALL
SELECT 'cus_inspection_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_inspection_reference` UNION ALL
SELECT 'cus_market_relation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_market_relation` UNION ALL
SELECT 'cus_project_customer_contact_relation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cus_project_customer_contact_relation` UNION ALL
SELECT 'cut_approval_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_instance` UNION ALL
SELECT 'cut_approval_node' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_node` UNION ALL
SELECT 'cut_approval_notification' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_notification` UNION ALL
SELECT 'cut_approval_reassignment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_reassignment` UNION ALL
SELECT 'cut_approval_result_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_result_reference` UNION ALL
SELECT 'cut_approval_review_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_review_item` UNION ALL
SELECT 'cut_approval_workflow' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_approval_workflow` UNION ALL
SELECT 'cut_assessment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_assessment` UNION ALL
SELECT 'cut_assessment_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_assessment_device` UNION ALL
SELECT 'cut_checklist_binding_condition' AS check_name, 720 AS expected, COUNT(*) AS actual FROM `cut_checklist_binding_condition` UNION ALL
SELECT 'cut_checklist_device_context' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_device_context` UNION ALL
SELECT 'cut_checklist_external_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_external_mapping` UNION ALL
SELECT 'cut_checklist_external_query' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_external_query` UNION ALL
SELECT 'cut_checklist_external_query_parameter' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_external_query_parameter` UNION ALL
SELECT 'cut_checklist_field_definition' AS check_name, 507 AS expected, COUNT(*) AS actual FROM `cut_checklist_field_definition` UNION ALL
SELECT 'cut_checklist_field_option' AS check_name, 10 AS expected, COUNT(*) AS actual FROM `cut_checklist_field_option` UNION ALL
SELECT 'cut_checklist_input_dimension' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_input_dimension` UNION ALL
SELECT 'cut_checklist_match_candidate' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_match_candidate` UNION ALL
SELECT 'cut_checklist_result_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_result_file` UNION ALL
SELECT 'cut_checklist_result_query_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_checklist_result_query_reference` UNION ALL
SELECT 'cut_closure_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_closure_device` UNION ALL
SELECT 'cut_collection_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_collection_log` UNION ALL
SELECT 'cut_configuration_dictionary_value' AS check_name, 23 AS expected, COUNT(*) AS actual FROM `cut_configuration_dictionary_value` UNION ALL
SELECT 'cut_configuration_dimension' AS check_name, 8 AS expected, COUNT(*) AS actual FROM `cut_configuration_dimension` UNION ALL
SELECT 'cut_configuration_plan_section' AS check_name, 7 AS expected, COUNT(*) AS actual FROM `cut_configuration_plan_section` UNION ALL
SELECT 'cut_configuration_plan_section_scope' AS check_name, 84 AS expected, COUNT(*) AS actual FROM `cut_configuration_plan_section_scope` UNION ALL
SELECT 'cut_configuration_validation_issue' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_configuration_validation_issue` UNION ALL
SELECT 'cut_cutover_checklist' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_checklist` UNION ALL
SELECT 'cut_cutover_checklist_binding_rule_revision' AS check_name, 135 AS expected, COUNT(*) AS actual FROM `cut_cutover_checklist_binding_rule_revision` UNION ALL
SELECT 'cut_cutover_checklist_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_checklist_item` UNION ALL
SELECT 'cut_cutover_checklist_item_definition_revision' AS check_name, 136 AS expected, COUNT(*) AS actual FROM `cut_cutover_checklist_item_definition_revision` UNION ALL
SELECT 'cut_cutover_checklist_item_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_checklist_item_result` UNION ALL
SELECT 'cut_cutover_closure' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_closure` UNION ALL
SELECT 'cut_cutover_closure_attachment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_closure_attachment` UNION ALL
SELECT 'cut_cutover_collection_evidence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_collection_evidence` UNION ALL
SELECT 'cut_cutover_configuration_revision' AS check_name, 2 AS expected, COUNT(*) AS actual FROM `cut_cutover_configuration_revision` UNION ALL
SELECT 'cut_cutover_support_arrangement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_cutover_support_arrangement` UNION ALL
SELECT 'cut_plan_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_device` UNION ALL
SELECT 'cut_plan_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_file` UNION ALL
SELECT 'cut_plan_generated_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_generated_file` UNION ALL
SELECT 'cut_plan_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_revision` UNION ALL
SELECT 'cut_plan_risk_mitigation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_risk_mitigation` UNION ALL
SELECT 'cut_plan_schedule' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_schedule` UNION ALL
SELECT 'cut_plan_support_contact_change' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_plan_support_contact_change` UNION ALL
SELECT 'cut_result_bulletin_summary' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_bulletin_summary` UNION ALL
SELECT 'cut_result_business_summary' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_business_summary` UNION ALL
SELECT 'cut_result_check_feedback' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_check_feedback` UNION ALL
SELECT 'cut_result_continuity_requirement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_continuity_requirement` UNION ALL
SELECT 'cut_result_custom_row' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_custom_row` UNION ALL
SELECT 'cut_result_custom_value' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_custom_value` UNION ALL
SELECT 'cut_result_cutover_background' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_cutover_background` UNION ALL
SELECT 'cut_result_device_location' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_device_location` UNION ALL
SELECT 'cut_result_impact_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_impact_scope` UNION ALL
SELECT 'cut_result_interface_plan' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_interface_plan` UNION ALL
SELECT 'cut_result_interruption' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_interruption` UNION ALL
SELECT 'cut_result_ip_plan' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_ip_plan` UNION ALL
SELECT 'cut_result_performance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_performance` UNION ALL
SELECT 'cut_result_spare_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_spare_reference` UNION ALL
SELECT 'cut_result_technical_bulletin' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_result_technical_bulletin` UNION ALL
SELECT 'cut_spare_application_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_spare_application_reference` UNION ALL
SELECT 'cut_spare_manual_evidence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_spare_manual_evidence` UNION ALL
SELECT 'cut_spare_need_source' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_spare_need_source` UNION ALL
SELECT 'cut_spare_request_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_spare_request_device` UNION ALL
SELECT 'cut_spare_status_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_spare_status_revision` UNION ALL
SELECT 'cut_step' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_step` UNION ALL
SELECT 'cut_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_task` UNION ALL
SELECT 'cut_task_device_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_task_device_scope` UNION ALL
SELECT 'cut_task_stage_history' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `cut_task_stage_history` UNION ALL
SELECT 'fcom001_v70_com_delivery_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `fcom001_v70_com_delivery_scope` UNION ALL
SELECT 'fcom001_v70_com_delivery_scope_detail' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `fcom001_v70_com_delivery_scope_detail` UNION ALL
SELECT 'fcom001_v70_com_order_line' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `fcom001_v70_com_order_line` UNION ALL
SELECT 'imp_arrival_acceptance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_arrival_acceptance` UNION ALL
SELECT 'imp_arrival_difference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_arrival_difference` UNION ALL
SELECT 'imp_arrival_line' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_arrival_line` UNION ALL
SELECT 'imp_collection_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_collection_log` UNION ALL
SELECT 'imp_configuration_collection' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_configuration_collection` UNION ALL
SELECT 'imp_cutover_readiness_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_device` UNION ALL
SELECT 'imp_cutover_readiness_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_snapshot` UNION ALL
SELECT 'imp_cutover_readiness_source' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_source` UNION ALL
SELECT 'imp_cutover_readiness_source_object' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_source_object` UNION ALL
SELECT 'imp_cutover_readiness_source_watermark' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_source_watermark` UNION ALL
SELECT 'imp_cutover_readiness_unmet' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_cutover_readiness_unmet` UNION ALL
SELECT 'imp_delivery_evidence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_delivery_evidence` UNION ALL
SELECT 'imp_delivery_evidence_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_delivery_evidence_revision` UNION ALL
SELECT 'imp_eng_arrival' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_arrival` UNION ALL
SELECT 'imp_eng_configuration' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_configuration` UNION ALL
SELECT 'imp_eng_deliverable' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_deliverable` UNION ALL
SELECT 'imp_eng_doc_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_doc_template` UNION ALL
SELECT 'imp_eng_doc_template_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_doc_template_version` UNION ALL
SELECT 'imp_eng_external_procurement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_external_procurement` UNION ALL
SELECT 'imp_eng_installation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_installation` UNION ALL
SELECT 'imp_eng_issue' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_issue` UNION ALL
SELECT 'imp_eng_joint_test' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_joint_test` UNION ALL
SELECT 'imp_eng_material_exchange' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_material_exchange` UNION ALL
SELECT 'imp_eng_material_exchange_serial' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_material_exchange_serial` UNION ALL
SELECT 'imp_eng_material_requisition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_material_requisition` UNION ALL
SELECT 'imp_eng_risk' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_risk` UNION ALL
SELECT 'imp_eng_training' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_eng_training` UNION ALL
SELECT 'imp_training_confirmation_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `imp_training_confirmation_grant` UNION ALL
SELECT 'infra_api_access_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_api_access_log` UNION ALL
SELECT 'infra_api_error_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_api_error_log` UNION ALL
SELECT 'infra_codegen_column' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_codegen_column` UNION ALL
SELECT 'infra_codegen_table' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_codegen_table` UNION ALL
SELECT 'infra_config' AS check_name, 12 AS expected, COUNT(*) AS actual FROM `infra_config` UNION ALL
SELECT 'infra_data_source_config' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_data_source_config` UNION ALL
SELECT 'infra_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_file` UNION ALL
SELECT 'infra_file_content' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_file_content` UNION ALL
SELECT 'infra_job' AS check_name, 22 AS expected, COUNT(*) AS actual FROM `infra_job` UNION ALL
SELECT 'infra_job_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `infra_job_log` UNION ALL
SELECT 'int_batch_JOB_EXECUTION' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_EXECUTION` UNION ALL
SELECT 'int_batch_JOB_EXECUTION_CONTEXT' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_EXECUTION_CONTEXT` UNION ALL
SELECT 'int_batch_JOB_EXECUTION_PARAMS' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_EXECUTION_PARAMS` UNION ALL
SELECT 'int_batch_JOB_EXECUTION_SEQ' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_EXECUTION_SEQ` UNION ALL
SELECT 'int_batch_JOB_INSTANCE' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_INSTANCE` UNION ALL
SELECT 'int_batch_JOB_INSTANCE_SEQ' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_JOB_INSTANCE_SEQ` UNION ALL
SELECT 'int_batch_STEP_EXECUTION' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_STEP_EXECUTION` UNION ALL
SELECT 'int_batch_STEP_EXECUTION_CONTEXT' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_STEP_EXECUTION_CONTEXT` UNION ALL
SELECT 'int_batch_STEP_EXECUTION_SEQ' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_batch_STEP_EXECUTION_SEQ` UNION ALL
SELECT 'int_device_ops_callback_receipt' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_device_ops_callback_receipt` UNION ALL
SELECT 'int_device_ops_dispatch' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_device_ops_dispatch` UNION ALL
SELECT 'int_sync_binding' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_sync_binding` UNION ALL
SELECT 'int_sync_connection' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_sync_connection` UNION ALL
SELECT 'int_sync_run' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_sync_run` UNION ALL
SELECT 'int_sync_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `int_sync_task` UNION ALL
SELECT 'kno_announcement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `kno_announcement` UNION ALL
SELECT 'kno_announcement_check' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `kno_announcement_check` UNION ALL
SELECT 'plt_approval_attempt' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_approval_attempt` UNION ALL
SELECT 'plt_approval_effect' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_approval_effect` UNION ALL
SELECT 'plt_approval_opinion' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_approval_opinion` UNION ALL
SELECT 'plt_authorization' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_authorization` UNION ALL
SELECT 'plt_authorization_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_authorization_grant` UNION ALL
SELECT 'plt_business_view_revision' AS check_name, 13 AS expected, COUNT(*) AS actual FROM `plt_business_view_revision` UNION ALL
SELECT 'plt_collection_batch' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_batch` UNION ALL
SELECT 'plt_collection_callback_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_callback_record` UNION ALL
SELECT 'plt_collection_log_quarantine' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_log_quarantine` UNION ALL
SELECT 'plt_collection_request' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_request` UNION ALL
SELECT 'plt_collection_result_consumption' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_result_consumption` UNION ALL
SELECT 'plt_collection_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_task` UNION ALL
SELECT 'plt_collection_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_collection_template` UNION ALL
SELECT 'plt_credential_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_credential_grant` UNION ALL
SELECT 'plt_delivery_capability_config' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_delivery_capability_config` UNION ALL
SELECT 'plt_delivery_fulfillment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_delivery_fulfillment` UNION ALL
SELECT 'plt_delivery_material' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_delivery_material` UNION ALL
SELECT 'plt_delivery_requirement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_delivery_requirement` UNION ALL
SELECT 'plt_delivery_submission' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_delivery_submission` UNION ALL
SELECT 'plt_delivery_type' AS check_name, 21 AS expected, COUNT(*) AS actual FROM `plt_delivery_type` UNION ALL
SELECT 'plt_device_credential' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_device_credential` UNION ALL
SELECT 'plt_device_log_parse' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_device_log_parse` UNION ALL
SELECT 'plt_dynamic_form_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_dynamic_form_instance` UNION ALL
SELECT 'plt_dynamic_form_template' AS check_name, 10 AS expected, COUNT(*) AS actual FROM `plt_dynamic_form_template` UNION ALL
SELECT 'plt_dynamic_form_template_revision' AS check_name, 23 AS expected, COUNT(*) AS actual FROM `plt_dynamic_form_template_revision` UNION ALL
SELECT 'plt_entity_extension_definition' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `plt_entity_extension_definition` UNION ALL
SELECT 'plt_entity_extension_value' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_entity_extension_value` UNION ALL
SELECT 'plt_entity_form_binding' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_entity_form_binding` UNION ALL
SELECT 'plt_export_audit' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_export_audit` UNION ALL
SELECT 'plt_export_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_export_task` UNION ALL
SELECT 'plt_external_key_mapping' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_external_key_mapping` UNION ALL
SELECT 'plt_file_access_grant' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_access_grant` UNION ALL
SELECT 'plt_file_archive_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_archive_record` UNION ALL
SELECT 'plt_file_artifact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_artifact` UNION ALL
SELECT 'plt_file_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_reference` UNION ALL
SELECT 'plt_file_upload_session' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_upload_session` UNION ALL
SELECT 'plt_file_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_file_version` UNION ALL
SELECT 'plt_form_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_form_instance` UNION ALL
SELECT 'plt_form_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_form_template` UNION ALL
SELECT 'plt_idempotency_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_idempotency_record` UNION ALL
SELECT 'plt_migration_batch' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_migration_batch` UNION ALL
SELECT 'plt_migration_issue' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_migration_issue` UNION ALL
SELECT 'plt_migration_source_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_migration_source_record` UNION ALL
SELECT 'plt_operation_audit' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_operation_audit` UNION ALL
SELECT 'plt_outbox_event' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `plt_outbox_event` UNION ALL
SELECT 'pms_bind_approval_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_bind_approval_instance` UNION ALL
SELECT 'pms_bind_event_checkpoint' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_bind_event_checkpoint` UNION ALL
SELECT 'pms_bind_evented_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_bind_evented_instance` UNION ALL
SELECT 'pms_bind_inline_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_bind_inline_instance` UNION ALL
SELECT 'pms_plat_business_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_business_result` UNION ALL
SELECT 'pms_plat_demo_ticket_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_demo_ticket_revision` UNION ALL
SELECT 'pms_plat_evidence_impact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_evidence_impact` UNION ALL
SELECT 'pms_plat_evidence_result_ref' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_evidence_result_ref` UNION ALL
SELECT 'pms_plat_execution_evidence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_execution_evidence` UNION ALL
SELECT 'pms_plat_process_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_process_definition` UNION ALL
SELECT 'pms_plat_result_subscription' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `pms_plat_result_subscription` UNION ALL
SELECT 'proj_business_result_change' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_business_result_change` UNION ALL
SELECT 'proj_business_result_channel' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_business_result_channel` UNION ALL
SELECT 'proj_delivery_definition_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_delivery_definition_reference` UNION ALL
SELECT 'proj_delivery_definition_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_delivery_definition_revision` UNION ALL
SELECT 'proj_idempotency_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_idempotency_record` UNION ALL
SELECT 'proj_node_overdue_completion' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_node_overdue_completion` UNION ALL
SELECT 'proj_payment_acceptance_binding' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_payment_acceptance_binding` UNION ALL
SELECT 'proj_preparation_confirmation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_preparation_confirmation` UNION ALL
SELECT 'proj_project' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project` UNION ALL
SELECT 'proj_project_code_sequence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_code_sequence` UNION ALL
SELECT 'proj_project_company_department_relation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_company_department_relation` UNION ALL
SELECT 'proj_project_deliverable' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_deliverable` UNION ALL
SELECT 'proj_project_exit_record' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_exit_record` UNION ALL
SELECT 'proj_project_gate' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_gate` UNION ALL
SELECT 'proj_project_gate_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_gate_reference` UNION ALL
SELECT 'proj_project_governance_action' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_governance_action` UNION ALL
SELECT 'proj_project_member_assignment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_member_assignment` UNION ALL
SELECT 'proj_project_milestone' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_milestone` UNION ALL
SELECT 'proj_project_node_execution' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_node_execution` UNION ALL
SELECT 'proj_project_party' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_party` UNION ALL
SELECT 'proj_project_plan_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_plan_version` UNION ALL
SELECT 'proj_project_portfolio' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_portfolio` UNION ALL
SELECT 'proj_project_portfolio_member' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_portfolio_member` UNION ALL
SELECT 'proj_project_portfolio_rule' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_portfolio_rule` UNION ALL
SELECT 'proj_project_progress_fact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_progress_fact` UNION ALL
SELECT 'proj_project_progress_policy_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_progress_policy_item` UNION ALL
SELECT 'proj_project_progress_policy_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_progress_policy_revision` UNION ALL
SELECT 'proj_project_progress_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_progress_snapshot` UNION ALL
SELECT 'proj_project_progress_snapshot_detail' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_progress_snapshot_detail` UNION ALL
SELECT 'proj_project_risk' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_risk` UNION ALL
SELECT 'proj_project_site' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_site` UNION ALL
SELECT 'proj_project_split_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_split_item` UNION ALL
SELECT 'proj_project_split_request' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_split_request` UNION ALL
SELECT 'proj_project_split_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_split_scope` UNION ALL
SELECT 'proj_project_stage' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_stage` UNION ALL
SELECT 'proj_project_stage_execution_contract' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_stage_execution_contract` UNION ALL
SELECT 'proj_project_stage_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_stage_snapshot` UNION ALL
SELECT 'proj_project_stage_transition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_stage_transition` UNION ALL
SELECT 'proj_project_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_task` UNION ALL
SELECT 'proj_project_task_assignment' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_task_assignment` UNION ALL
SELECT 'proj_project_task_completion_evaluation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_task_completion_evaluation` UNION ALL
SELECT 'proj_project_task_execution_contract' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_task_execution_contract` UNION ALL
SELECT 'proj_project_task_responsible' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_task_responsible` UNION ALL
SELECT 'proj_project_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template` UNION ALL
SELECT 'proj_project_template_deliverable_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_deliverable_definition` UNION ALL
SELECT 'proj_project_template_gate_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_gate_definition` UNION ALL
SELECT 'proj_project_template_gate_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_gate_reference` UNION ALL
SELECT 'proj_project_template_match_history' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_match_history` UNION ALL
SELECT 'proj_project_template_milestone_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_milestone_definition` UNION ALL
SELECT 'proj_project_template_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_revision` UNION ALL
SELECT 'proj_project_template_stage_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_stage_definition` UNION ALL
SELECT 'proj_project_template_task_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_template_task_definition` UNION ALL
SELECT 'proj_project_tree_change' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_tree_change` UNION ALL
SELECT 'proj_project_tree_path' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_tree_path` UNION ALL
SELECT 'proj_project_tree_version' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_project_tree_version` UNION ALL
SELECT 'proj_result_evidence_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_result_evidence_item` UNION ALL
SELECT 'proj_result_evidence_scan' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_result_evidence_scan` UNION ALL
SELECT 'proj_result_subscription' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_result_subscription` UNION ALL
SELECT 'proj_result_subscription_candidate' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_result_subscription_candidate` UNION ALL
SELECT 'proj_stage_suggestion_rule' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_stage_suggestion_rule` UNION ALL
SELECT 'proj_stage_transition_definition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_stage_transition_definition` UNION ALL
SELECT 'proj_task_business_link' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_task_business_link` UNION ALL
SELECT 'proj_task_dependency' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_task_dependency` UNION ALL
SELECT 'proj_task_state_machine_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_task_state_machine_revision` UNION ALL
SELECT 'proj_task_state_transition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_task_state_transition` UNION ALL
SELECT 'proj_task_tree_path' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_task_tree_path` UNION ALL
SELECT 'proj_team_batch_change' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_team_batch_change` UNION ALL
SELECT 'proj_team_batch_change_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `proj_team_batch_change_item` UNION ALL
SELECT 'res_outsource_request' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `res_outsource_request` UNION ALL
SELECT 'sol_construction_plan' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_construction_plan` UNION ALL
SELECT 'sol_construction_plan_change' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_construction_plan_change` UNION ALL
SELECT 'sol_construction_plan_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_construction_plan_revision` UNION ALL
SELECT 'sol_dynamic_form_instance' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_dynamic_form_instance` UNION ALL
SELECT 'sol_eng_briefing' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_briefing` UNION ALL
SELECT 'sol_eng_requirement' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_requirement` UNION ALL
SELECT 'sol_eng_resource_ready' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_resource_ready` UNION ALL
SELECT 'sol_eng_site_survey' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_site_survey` UNION ALL
SELECT 'sol_eng_solution' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_solution` UNION ALL
SELECT 'sol_eng_solution_source' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_eng_solution_source` UNION ALL
SELECT 'sol_engineering_briefing_retired' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_engineering_briefing_retired` UNION ALL
SELECT 'sol_preparation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_preparation` UNION ALL
SELECT 'sol_preparation_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_preparation_item` UNION ALL
SELECT 'sol_preparation_item_waiver' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_preparation_item_waiver` UNION ALL
SELECT 'sol_preparation_readiness_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_preparation_readiness_snapshot` UNION ALL
SELECT 'sol_preparation_source_reference' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_preparation_source_reference` UNION ALL
SELECT 'sol_requirement_analysis' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_requirement_analysis` UNION ALL
SELECT 'sol_requirement_analysis_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_requirement_analysis_revision` UNION ALL
SELECT 'sol_requirement_analysis_section' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_requirement_analysis_section` UNION ALL
SELECT 'sol_schedule_backward' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_schedule_backward` UNION ALL
SELECT 'sol_schedule_backward_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_schedule_backward_item` UNION ALL
SELECT 'sol_site_survey' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_site_survey` UNION ALL
SELECT 'sol_site_survey_condition' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_site_survey_condition` UNION ALL
SELECT 'sol_site_survey_material' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_site_survey_material` UNION ALL
SELECT 'sol_solution_review' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_solution_review` UNION ALL
SELECT 'sol_solution_review_policy' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_solution_review_policy` UNION ALL
SELECT 'sol_stage_plan_batch' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_stage_plan_batch` UNION ALL
SELECT 'sol_stage_plan_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `sol_stage_plan_item` UNION ALL
SELECT 'srv_inspection_annotation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_annotation` UNION ALL
SELECT 'srv_inspection_attempt' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_attempt` UNION ALL
SELECT 'srv_inspection_collection_receipt' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_collection_receipt` UNION ALL
SELECT 'srv_inspection_detection_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_detection_result` UNION ALL
SELECT 'srv_inspection_evidence' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_evidence` UNION ALL
SELECT 'srv_inspection_execution' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_execution` UNION ALL
SELECT 'srv_inspection_feedback_confirmation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_feedback_confirmation` UNION ALL
SELECT 'srv_inspection_feedback_export' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_feedback_export` UNION ALL
SELECT 'srv_inspection_feedback_export_item' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_feedback_export_item` UNION ALL
SELECT 'srv_inspection_parse_part' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_parse_part` UNION ALL
SELECT 'srv_inspection_report_file' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_report_file` UNION ALL
SELECT 'srv_inspection_report_result' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_report_result` UNION ALL
SELECT 'srv_inspection_report_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_report_revision` UNION ALL
SELECT 'srv_inspection_rule' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule` UNION ALL
SELECT 'srv_inspection_rule_command_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_command_revision` UNION ALL
SELECT 'srv_inspection_rule_feedback' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_feedback` UNION ALL
SELECT 'srv_inspection_rule_optimization' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_optimization` UNION ALL
SELECT 'srv_inspection_rule_product_type_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_product_type_revision` UNION ALL
SELECT 'srv_inspection_rule_revision' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_revision` UNION ALL
SELECT 'srv_inspection_rule_security_review' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_security_review` UNION ALL
SELECT 'srv_inspection_rule_selection' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_rule_selection` UNION ALL
SELECT 'srv_inspection_task' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_task` UNION ALL
SELECT 'srv_inspection_task_device' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_task_device` UNION ALL
SELECT 'srv_inspection_task_event' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_task_event` UNION ALL
SELECT 'srv_inspection_task_rule_snapshot' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_inspection_task_rule_snapshot` UNION ALL
SELECT 'srv_service_issue' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_service_issue` UNION ALL
SELECT 'srv_service_issue_remediation' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `srv_service_issue_remediation` UNION ALL
SELECT 'system_company' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_company` UNION ALL
SELECT 'system_dept' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_dept` UNION ALL
SELECT 'system_dict_data' AS check_name, 1414 AS expected, COUNT(*) AS actual FROM `system_dict_data` UNION ALL
SELECT 'system_dict_type' AS check_name, 301 AS expected, COUNT(*) AS actual FROM `system_dict_type` UNION ALL
SELECT 'system_login_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_login_log` UNION ALL
SELECT 'system_mail_account' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_mail_account` UNION ALL
SELECT 'system_mail_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_mail_log` UNION ALL
SELECT 'system_mail_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_mail_template` UNION ALL
SELECT 'system_menu' AS check_name, 742 AS expected, COUNT(*) AS actual FROM `system_menu` UNION ALL
SELECT 'system_notice' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_notice` UNION ALL
SELECT 'system_notify_message' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_notify_message` UNION ALL
SELECT 'system_notify_template' AS check_name, 3 AS expected, COUNT(*) AS actual FROM `system_notify_template` UNION ALL
SELECT 'system_oauth2_access_token' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_oauth2_access_token` UNION ALL
SELECT 'system_oauth2_approve' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_oauth2_approve` UNION ALL
SELECT 'system_oauth2_client' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `system_oauth2_client` UNION ALL
SELECT 'system_oauth2_code' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_oauth2_code` UNION ALL
SELECT 'system_oauth2_refresh_token' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_oauth2_refresh_token` UNION ALL
SELECT 'system_operate_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_operate_log` UNION ALL
SELECT 'system_organization_ownership' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_organization_ownership` UNION ALL
SELECT 'system_post' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_post` UNION ALL
SELECT 'system_role' AS check_name, 8 AS expected, COUNT(*) AS actual FROM `system_role` UNION ALL
SELECT 'system_role_menu' AS check_name, 388 AS expected, COUNT(*) AS actual FROM `system_role_menu` UNION ALL
SELECT 'system_sms_channel' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_sms_channel` UNION ALL
SELECT 'system_sms_code' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_sms_code` UNION ALL
SELECT 'system_sms_log' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_sms_log` UNION ALL
SELECT 'system_sms_template' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_sms_template` UNION ALL
SELECT 'system_social_client' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_social_client` UNION ALL
SELECT 'system_social_user' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_social_user` UNION ALL
SELECT 'system_social_user_bind' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_social_user_bind` UNION ALL
SELECT 'system_tenant' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `system_tenant` UNION ALL
SELECT 'system_tenant_package' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_tenant_package` UNION ALL
SELECT 'system_user_company_department_scope' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_user_company_department_scope` UNION ALL
SELECT 'system_user_post' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `system_user_post` UNION ALL
SELECT 'system_user_role' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `system_user_role` UNION ALL
SELECT 'system_users' AS check_name, 1 AS expected, COUNT(*) AS actual FROM `system_users` UNION ALL
SELECT 'yudao_demo01_contact' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `yudao_demo01_contact` UNION ALL
SELECT 'yudao_demo02_category' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `yudao_demo02_category` UNION ALL
SELECT 'yudao_demo03_course' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `yudao_demo03_course` UNION ALL
SELECT 'yudao_demo03_grade' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `yudao_demo03_grade` UNION ALL
SELECT 'yudao_demo03_student' AS check_name, 0 AS expected, COUNT(*) AS actual FROM `yudao_demo03_student`
) checked WHERE actual<>expected;

-- Physical and logical reference integrity.
SELECT * FROM (
SELECT 'QRTZ_BLOB_TRIGGERS.QRTZ_BLOB_TRIGGERS_ibfk_1' AS check_name, COUNT(*) AS violations FROM `QRTZ_BLOB_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` r WHERE r.`SCHED_NAME`=c.`SCHED_NAME` AND r.`TRIGGER_NAME`=c.`TRIGGER_NAME` AND r.`TRIGGER_GROUP`=c.`TRIGGER_GROUP`) UNION ALL
SELECT 'QRTZ_CRON_TRIGGERS.QRTZ_CRON_TRIGGERS_ibfk_1' AS check_name, COUNT(*) AS violations FROM `QRTZ_CRON_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` r WHERE r.`SCHED_NAME`=c.`SCHED_NAME` AND r.`TRIGGER_NAME`=c.`TRIGGER_NAME` AND r.`TRIGGER_GROUP`=c.`TRIGGER_GROUP`) UNION ALL
SELECT 'QRTZ_SIMPLE_TRIGGERS.QRTZ_SIMPLE_TRIGGERS_ibfk_1' AS check_name, COUNT(*) AS violations FROM `QRTZ_SIMPLE_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` r WHERE r.`SCHED_NAME`=c.`SCHED_NAME` AND r.`TRIGGER_NAME`=c.`TRIGGER_NAME` AND r.`TRIGGER_GROUP`=c.`TRIGGER_GROUP`) UNION ALL
SELECT 'QRTZ_SIMPROP_TRIGGERS.QRTZ_SIMPROP_TRIGGERS_ibfk_1' AS check_name, COUNT(*) AS violations FROM `QRTZ_SIMPROP_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` r WHERE r.`SCHED_NAME`=c.`SCHED_NAME` AND r.`TRIGGER_NAME`=c.`TRIGGER_NAME` AND r.`TRIGGER_GROUP`=c.`TRIGGER_GROUP`) UNION ALL
SELECT 'QRTZ_TRIGGERS.QRTZ_TRIGGERS_ibfk_1' AS check_name, COUNT(*) AS violations FROM `QRTZ_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`JOB_NAME` IS NOT NULL AND c.`JOB_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_JOB_DETAILS` r WHERE r.`SCHED_NAME`=c.`SCHED_NAME` AND r.`JOB_NAME`=c.`JOB_NAME` AND r.`JOB_GROUP`=c.`JOB_GROUP`) UNION ALL
SELECT 'ast_device_ancestor_projection_operation.fk_ast_device_ancestor_projection_operation_device' AS check_name, COUNT(*) AS violations FROM `ast_device_ancestor_projection_operation` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_assembly.fk_ast_device_assembly_child' AS check_name, COUNT(*) AS violations FROM `ast_device_assembly` c WHERE c.`tenant_id` IS NOT NULL AND c.`child_device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`child_device_sn`) UNION ALL
SELECT 'ast_device_assembly.fk_ast_device_assembly_parent' AS check_name, COUNT(*) AS violations FROM `ast_device_assembly` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`parent_device_sn`) UNION ALL
SELECT 'ast_device_assignment_reconciliation.fk_ast_device_assignment_reconciliation_device' AS check_name, COUNT(*) AS violations FROM `ast_device_assignment_reconciliation` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_current_product_type.fk_ast_device_product_type_mapping' AS check_name, COUNT(*) AS violations FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_mapping_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type_source_mapping` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_mapping_id`) UNION ALL
SELECT 'ast_device_current_product_type.fk_ast_device_product_type_mapping_target' AS check_name, COUNT(*) AS violations FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_mapping_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type_source_mapping` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_mapping_id` AND r.`product_type_id`=c.`product_type_id`) UNION ALL
SELECT 'ast_device_current_product_type.fk_ast_device_product_type_target' AS check_name, COUNT(*) AS violations FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND c.`product_type_code` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`product_type_id` AND r.`type_code`=c.`product_type_code`) UNION ALL
SELECT 'ast_device_customer_relationship.fk_ast_device_customer_relationship_device' AS check_name, COUNT(*) AS violations FROM `ast_device_customer_relationship` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_cutover_result_file.fk_cut_structured_84' AS check_name, COUNT(*) AS violations FROM `ast_device_cutover_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_cutover_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device_cutover_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`device_cutover_result_id`) UNION ALL
SELECT 'ast_device_download_grant.fk_ast_device_download_grant_device' AS check_name, COUNT(*) AS violations FROM `ast_device_download_grant` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_factory_info.fk_ast_device_factory_info_device' AS check_name, COUNT(*) AS violations FROM `ast_device_factory_info` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_factory_version.fk_ast_device_factory_version_device' AS check_name, COUNT(*) AS violations FROM `ast_device_factory_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_location.fk_ast_device_location_device' AS check_name, COUNT(*) AS violations FROM `ast_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_network_version.fk_ast_device_network_version_device' AS check_name, COUNT(*) AS violations FROM `ast_device_network_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_network_version_event.fk_ast_device_network_event_device' AS check_name, COUNT(*) AS violations FROM `ast_device_network_version_event` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_project_ancestor.fk_ast_device_project_ancestor_device' AS check_name, COUNT(*) AS violations FROM `ast_device_project_ancestor` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_project_relationship.fk_ast_device_project_relationship_device' AS check_name, COUNT(*) AS violations FROM `ast_device_project_relationship` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_warranty.fk_ast_device_warranty_device' AS check_name, COUNT(*) AS violations FROM `ast_device_warranty` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_device_warranty_record.fk_ast_device_warranty_record_device' AS check_name, COUNT(*) AS violations FROM `ast_device_warranty_record` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`sn`=c.`device_sn`) UNION ALL
SELECT 'ast_product_type_source_mapping.fk_ast_product_type_mapping_target' AS check_name, COUNT(*) AS violations FROM `ast_product_type_source_mapping` c WHERE c.`tenant_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`product_type_id`) UNION ALL
SELECT 'com_contract_receivable.fk_contract_receivable_contract' AS check_name, COUNT(*) AS violations FROM `com_contract_receivable` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`contract_id`) UNION ALL
SELECT 'com_delivery_scope.fk_scope_order_line' AS check_name, COUNT(*) AS violations FROM `com_delivery_scope` c WHERE c.`tenant_id` IS NOT NULL AND c.`order_line_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_sales_order_line` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`order_line_id`) UNION ALL
SELECT 'com_delivery_scope_detail.fk_delivery_scope_detail_scope' AS check_name, COUNT(*) AS violations FROM `com_delivery_scope_detail` c WHERE c.`tenant_id` IS NOT NULL AND c.`delivery_scope_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_delivery_scope` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`delivery_scope_id`) UNION ALL
SELECT 'com_order_contract_relation.fk_order_contract_contract' AS check_name, COUNT(*) AS violations FROM `com_order_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`contract_id`) UNION ALL
SELECT 'com_order_contract_relation.fk_order_contract_order' AS check_name, COUNT(*) AS violations FROM `com_order_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`order_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_sales_order` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`order_id`) UNION ALL
SELECT 'com_project_contract_relation.fk_project_contract_contract' AS check_name, COUNT(*) AS violations FROM `com_project_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`contract_id`) UNION ALL
SELECT 'com_sales_order_line.fk_sales_order_line_order' AS check_name, COUNT(*) AS violations FROM `com_sales_order_line` c WHERE c.`tenant_id` IS NOT NULL AND c.`order_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_sales_order` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`order_id`) UNION ALL
SELECT 'com_shipment_contract_reference.fk_shipment_contract_ref_contract' AS check_name, COUNT(*) AS violations FROM `com_shipment_contract_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`contract_id`) UNION ALL
SELECT 'com_shipment_package.fk_shipment_package_contract_ref' AS check_name, COUNT(*) AS violations FROM `com_shipment_package` c WHERE c.`tenant_id` IS NOT NULL AND c.`shipment_contract_ref_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_shipment_contract_reference` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`shipment_contract_ref_id`) UNION ALL
SELECT 'cus_inspection_reference.fk_cus_inspection_history' AS check_name, COUNT(*) AS violations FROM `cus_inspection_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`history_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cus_inspection_history` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`history_id`) UNION ALL
SELECT 'cut_approval_instance.fk_cut_approval_plan' AS check_name, COUNT(*) AS violations FROM `cut_approval_instance` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_approval_instance.fk_cut_approval_task' AS check_name, COUNT(*) AS violations FROM `cut_approval_instance` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_approval_node.fk_cut_approval_node_instance' AS check_name, COUNT(*) AS violations FROM `cut_approval_node` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`id`=c.`approval_instance_id`) UNION ALL
SELECT 'cut_approval_notification.fk_cut_approval_notification_instance' AS check_name, COUNT(*) AS violations FROM `cut_approval_notification` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`id`=c.`approval_instance_id`) UNION ALL
SELECT 'cut_approval_notification.fk_cut_approval_notification_node' AS check_name, COUNT(*) AS violations FROM `cut_approval_notification` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` r WHERE r.`id`=c.`approval_node_id`) UNION ALL
SELECT 'cut_approval_reassignment.fk_cut_approval_reassignment_instance' AS check_name, COUNT(*) AS violations FROM `cut_approval_reassignment` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`id`=c.`approval_instance_id`) UNION ALL
SELECT 'cut_approval_reassignment.fk_cut_approval_reassignment_node' AS check_name, COUNT(*) AS violations FROM `cut_approval_reassignment` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` r WHERE r.`id`=c.`approval_node_id`) UNION ALL
SELECT 'cut_approval_result_reference.fk_cut_structured_71' AS check_name, COUNT(*) AS violations FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`approval_instance_id`) UNION ALL
SELECT 'cut_approval_result_reference.fk_cut_structured_72' AS check_name, COUNT(*) AS violations FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_item_id`) UNION ALL
SELECT 'cut_approval_result_reference.fk_cut_structured_73' AS check_name, COUNT(*) AS violations FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_approval_review_item.fk_cut_approval_review_instance' AS check_name, COUNT(*) AS violations FROM `cut_approval_review_item` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`id`=c.`approval_instance_id`) UNION ALL
SELECT 'cut_approval_review_item.fk_cut_approval_review_node' AS check_name, COUNT(*) AS violations FROM `cut_approval_review_item` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` r WHERE r.`id`=c.`approval_node_id`) UNION ALL
SELECT 'cut_approval_workflow.fk_cut_workflow_approval' AS check_name, COUNT(*) AS violations FROM `cut_approval_workflow` c WHERE c.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` r WHERE r.`id`=c.`id`) UNION ALL
SELECT 'cut_assessment_device.fk_cut_structured_13' AS check_name, COUNT(*) AS violations FROM `cut_assessment_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`assessment_id`) UNION ALL
SELECT 'cut_assessment_device.fk_cut_structured_14' AS check_name, COUNT(*) AS violations FROM `cut_assessment_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_device_scope_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task_device_scope` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`task_device_scope_id`) UNION ALL
SELECT 'cut_checklist_binding_condition.fk_cut_structured_10' AS check_name, COUNT(*) AS violations FROM `cut_checklist_binding_condition` c WHERE c.`tenant_id` IS NOT NULL AND c.`binding_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_binding_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`binding_rule_revision_id`) UNION ALL
SELECT 'cut_checklist_binding_condition.fk_cut_structured_11' AS check_name, COUNT(*) AS violations FROM `cut_checklist_binding_condition` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_dimension_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_dimension` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_dimension_id`) UNION ALL
SELECT 'cut_checklist_device_context.fk_cut_structured_24' AS check_name, COUNT(*) AS violations FROM `cut_checklist_device_context` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_id`) UNION ALL
SELECT 'cut_checklist_device_context.fk_cut_structured_25' AS check_name, COUNT(*) AS violations FROM `cut_checklist_device_context` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`assessment_device_id`) UNION ALL
SELECT 'cut_checklist_external_mapping.fk_cut_structured_12' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_mapping` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_definition_revision_id`) UNION ALL
SELECT 'cut_checklist_external_query.fk_cut_structured_29' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_item_id`) UNION ALL
SELECT 'cut_checklist_external_query.fk_cut_structured_30' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_definition_revision_id`) UNION ALL
SELECT 'cut_checklist_external_query.fk_cut_structured_31' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`loaded_checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`loaded_checklist_result_id`) UNION ALL
SELECT 'cut_checklist_external_query_parameter.fk_cut_structured_32' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_query_parameter` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`external_query_id`) UNION ALL
SELECT 'cut_checklist_external_query_parameter.fk_cut_structured_33' AS check_name, COUNT(*) AS violations FROM `cut_checklist_external_query_parameter` c WHERE c.`tenant_id` IS NOT NULL AND c.`mapping_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_mapping` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`mapping_id`) UNION ALL
SELECT 'cut_checklist_field_definition.fk_cut_structured_6' AS check_name, COUNT(*) AS violations FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_definition_revision_id`) UNION ALL
SELECT 'cut_checklist_field_definition.fk_cut_structured_7' AS check_name, COUNT(*) AS violations FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`custom_checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`custom_checklist_item_id`) UNION ALL
SELECT 'cut_checklist_field_definition.fk_cut_structured_8' AS check_name, COUNT(*) AS violations FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_field_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`parent_field_id`) UNION ALL
SELECT 'cut_checklist_field_option.fk_cut_structured_9' AS check_name, COUNT(*) AS violations FROM `cut_checklist_field_option` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`field_definition_id`) UNION ALL
SELECT 'cut_checklist_input_dimension.fk_cut_structured_19' AS check_name, COUNT(*) AS violations FROM `cut_checklist_input_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_id`) UNION ALL
SELECT 'cut_checklist_input_dimension.fk_cut_structured_20' AS check_name, COUNT(*) AS violations FROM `cut_checklist_input_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_dimension_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_dimension` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_dimension_id`) UNION ALL
SELECT 'cut_checklist_match_candidate.fk_cut_structured_21' AS check_name, COUNT(*) AS violations FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_id`) UNION ALL
SELECT 'cut_checklist_match_candidate.fk_cut_structured_22' AS check_name, COUNT(*) AS violations FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_definition_revision_id`) UNION ALL
SELECT 'cut_checklist_match_candidate.fk_cut_structured_23' AS check_name, COUNT(*) AS violations FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`binding_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_binding_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`binding_rule_revision_id`) UNION ALL
SELECT 'cut_checklist_result_file.fk_cut_structured_26' AS check_name, COUNT(*) AS violations FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_checklist_result_file.fk_cut_structured_27' AS check_name, COUNT(*) AS violations FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`custom_result_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_custom_row` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`custom_result_row_id`) UNION ALL
SELECT 'cut_checklist_result_file.fk_cut_structured_28' AS check_name, COUNT(*) AS violations FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`field_definition_id`) UNION ALL
SELECT 'cut_checklist_result_query_reference.fk_cut_structured_34' AS check_name, COUNT(*) AS violations FROM `cut_checklist_result_query_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_checklist_result_query_reference.fk_cut_structured_35' AS check_name, COUNT(*) AS violations FROM `cut_checklist_result_query_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`external_query_id`) UNION ALL
SELECT 'cut_closure_device.fk_cut_structured_74' AS check_name, COUNT(*) AS violations FROM `cut_closure_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`closure_id`) UNION ALL
SELECT 'cut_configuration_dictionary_value.fk_cut_structured_2' AS check_name, COUNT(*) AS violations FROM `cut_configuration_dictionary_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_revision_id`) UNION ALL
SELECT 'cut_configuration_dimension.fk_cut_structured_1' AS check_name, COUNT(*) AS violations FROM `cut_configuration_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_revision_id`) UNION ALL
SELECT 'cut_configuration_plan_section.fk_cut_structured_3' AS check_name, COUNT(*) AS violations FROM `cut_configuration_plan_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_revision_id`) UNION ALL
SELECT 'cut_configuration_plan_section_scope.fk_cut_structured_4' AS check_name, COUNT(*) AS violations FROM `cut_configuration_plan_section_scope` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_section_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_plan_section` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_section_id`) UNION ALL
SELECT 'cut_configuration_validation_issue.fk_cut_structured_5' AS check_name, COUNT(*) AS violations FROM `cut_configuration_validation_issue` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_revision_id`) UNION ALL
SELECT 'cut_cutover_checklist.fk_cut_checklist_task' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_cutover_checklist.fk_cut_structured_15' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`configuration_revision_id`) UNION ALL
SELECT 'cut_cutover_checklist_item.fk_cut_checklist_item_root' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist_item` c WHERE c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` r WHERE r.`id`=c.`checklist_id`) UNION ALL
SELECT 'cut_cutover_checklist_item_result.fk_cut_checklist_result_item' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist_item_result` c WHERE c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`id`=c.`checklist_item_id`) UNION ALL
SELECT 'cut_cutover_checklist_item_result.fk_cut_structured_16' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`field_definition_id`) UNION ALL
SELECT 'cut_cutover_checklist_item_result.fk_cut_structured_17' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`base_checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`base_checklist_result_id`) UNION ALL
SELECT 'cut_cutover_checklist_item_result.fk_cut_structured_18' AS check_name, COUNT(*) AS violations FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`collection_log_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_collection_log` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`collection_log_id`) UNION ALL
SELECT 'cut_cutover_closure.fk_cut_closure_task' AS check_name, COUNT(*) AS violations FROM `cut_cutover_closure` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_cutover_closure_attachment.fk_cut_closure_attachment_root' AS check_name, COUNT(*) AS violations FROM `cut_cutover_closure_attachment` c WHERE c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` r WHERE r.`id`=c.`closure_id`) UNION ALL
SELECT 'cut_cutover_collection_evidence.fk_cut_collection_closure' AS check_name, COUNT(*) AS violations FROM `cut_cutover_collection_evidence` c WHERE c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` r WHERE r.`id`=c.`closure_id`) UNION ALL
SELECT 'cut_cutover_collection_evidence.fk_cut_collection_manual_attachment' AS check_name, COUNT(*) AS violations FROM `cut_cutover_collection_evidence` c WHERE c.`manual_attachment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure_attachment` r WHERE r.`id`=c.`manual_attachment_id`) UNION ALL
SELECT 'cut_cutover_collection_evidence.fk_cut_collection_task' AS check_name, COUNT(*) AS violations FROM `cut_cutover_collection_evidence` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_cutover_support_arrangement.fk_cut_structured_61' AS check_name, COUNT(*) AS violations FROM `cut_cutover_support_arrangement` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_support_contact_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_support_contact_change` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`current_support_contact_change_id`) UNION ALL
SELECT 'cut_cutover_support_arrangement.fk_cut_support_plan' AS check_name, COUNT(*) AS violations FROM `cut_cutover_support_arrangement` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_plan_device.fk_cut_structured_63' AS check_name, COUNT(*) AS violations FROM `cut_plan_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_plan_device.fk_cut_structured_64' AS check_name, COUNT(*) AS violations FROM `cut_plan_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`assessment_device_id`) UNION ALL
SELECT 'cut_plan_file.fk_cut_structured_65' AS check_name, COUNT(*) AS violations FROM `cut_plan_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_plan_revision.fk_cut_plan_task' AS check_name, COUNT(*) AS violations FROM `cut_plan_revision` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_plan_risk_mitigation.fk_cut_structured_66' AS check_name, COUNT(*) AS violations FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_plan_risk_mitigation.fk_cut_structured_67' AS check_name, COUNT(*) AS violations FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_plan_risk_mitigation.fk_cut_structured_68' AS check_name, COUNT(*) AS violations FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_item_id`) UNION ALL
SELECT 'cut_plan_schedule.fk_cut_structured_62' AS check_name, COUNT(*) AS violations FROM `cut_plan_schedule` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'cut_plan_support_contact_change.fk_cut_structured_69' AS check_name, COUNT(*) AS violations FROM `cut_plan_support_contact_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`support_arrangement_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_support_arrangement` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`support_arrangement_id`) UNION ALL
SELECT 'cut_plan_support_contact_change.fk_cut_structured_70' AS check_name, COUNT(*) AS violations FROM `cut_plan_support_contact_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`previous_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_support_contact_change` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`previous_change_id`) UNION ALL
SELECT 'cut_result_bulletin_summary.fk_cut_structured_43' AS check_name, COUNT(*) AS violations FROM `cut_result_bulletin_summary` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_business_summary.fk_cut_structured_37' AS check_name, COUNT(*) AS violations FROM `cut_result_business_summary` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_check_feedback.fk_cut_structured_41' AS check_name, COUNT(*) AS violations FROM `cut_result_check_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_continuity_requirement.fk_cut_structured_39' AS check_name, COUNT(*) AS violations FROM `cut_result_continuity_requirement` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_custom_row.fk_cut_structured_58' AS check_name, COUNT(*) AS violations FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_custom_row.fk_cut_structured_59' AS check_name, COUNT(*) AS violations FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_custom_row.fk_cut_structured_60' AS check_name, COUNT(*) AS violations FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_custom_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_custom_row` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`parent_custom_row_id`) UNION ALL
SELECT 'cut_result_custom_value.fk_cut_structured_56' AS check_name, COUNT(*) AS violations FROM `cut_result_custom_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_custom_value.fk_cut_structured_57' AS check_name, COUNT(*) AS violations FROM `cut_result_custom_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`field_definition_id`) UNION ALL
SELECT 'cut_result_cutover_background.fk_cut_structured_36' AS check_name, COUNT(*) AS violations FROM `cut_result_cutover_background` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_device_location.fk_cut_structured_44' AS check_name, COUNT(*) AS violations FROM `cut_result_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_device_location.fk_cut_structured_45' AS check_name, COUNT(*) AS violations FROM `cut_result_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_impact_scope.fk_cut_structured_38' AS check_name, COUNT(*) AS violations FROM `cut_result_impact_scope` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_interface_plan.fk_cut_structured_46' AS check_name, COUNT(*) AS violations FROM `cut_result_interface_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_interface_plan.fk_cut_structured_47' AS check_name, COUNT(*) AS violations FROM `cut_result_interface_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_interruption.fk_cut_structured_40' AS check_name, COUNT(*) AS violations FROM `cut_result_interruption` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_ip_plan.fk_cut_structured_48' AS check_name, COUNT(*) AS violations FROM `cut_result_ip_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_ip_plan.fk_cut_structured_49' AS check_name, COUNT(*) AS violations FROM `cut_result_ip_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_performance.fk_cut_structured_50' AS check_name, COUNT(*) AS violations FROM `cut_result_performance` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_performance.fk_cut_structured_51' AS check_name, COUNT(*) AS violations FROM `cut_result_performance` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_spare_reference.fk_cut_structured_42' AS check_name, COUNT(*) AS violations FROM `cut_result_spare_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_technical_bulletin.fk_cut_structured_52' AS check_name, COUNT(*) AS violations FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_result_id`) UNION ALL
SELECT 'cut_result_technical_bulletin.fk_cut_structured_53' AS check_name, COUNT(*) AS violations FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`table_field_definition_id`) UNION ALL
SELECT 'cut_result_technical_bulletin.fk_cut_structured_54' AS check_name, COUNT(*) AS violations FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`external_query_id`) UNION ALL
SELECT 'cut_result_technical_bulletin.fk_cut_structured_55' AS check_name, COUNT(*) AS violations FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_bulletin_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_technical_bulletin` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_bulletin_row_id`) UNION ALL
SELECT 'cut_spare_application_reference.fk_cut_spare_application_task' AS check_name, COUNT(*) AS violations FROM `cut_spare_application_reference` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_spare_manual_evidence.fk_cut_spare_manual_application' AS check_name, COUNT(*) AS violations FROM `cut_spare_manual_evidence` c WHERE c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` r WHERE r.`id`=c.`application_reference_id`) UNION ALL
SELECT 'cut_spare_manual_evidence.fk_cut_spare_manual_task' AS check_name, COUNT(*) AS violations FROM `cut_spare_manual_evidence` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` r WHERE r.`id`=c.`task_id`) UNION ALL
SELECT 'cut_spare_need_source.fk_cut_structured_75' AS check_name, COUNT(*) AS violations FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`application_reference_id`) UNION ALL
SELECT 'cut_spare_need_source.fk_cut_structured_76' AS check_name, COUNT(*) AS violations FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`assessment_id`) UNION ALL
SELECT 'cut_spare_need_source.fk_cut_structured_77' AS check_name, COUNT(*) AS violations FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`checklist_item_id`) UNION ALL
SELECT 'cut_spare_request_device.fk_cut_structured_78' AS check_name, COUNT(*) AS violations FROM `cut_spare_request_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`application_reference_id`) UNION ALL
SELECT 'cut_spare_status_revision.fk_cut_spare_status_application' AS check_name, COUNT(*) AS violations FROM `cut_spare_status_revision` c WHERE c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` r WHERE r.`id`=c.`application_reference_id`) UNION ALL
SELECT 'cut_step.fk_cut_step_plan' AS check_name, COUNT(*) AS violations FROM `cut_step` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` r WHERE r.`id`=c.`plan_revision_id`) UNION ALL
SELECT 'imp_arrival_acceptance.fk_imp_arrival_evidence_revision' AS check_name, COUNT(*) AS violations FROM `imp_arrival_acceptance` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND c.`evidence_revision` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`evidence_id`=c.`evidence_id` AND r.`revision_no`=c.`evidence_revision`) UNION ALL
SELECT 'imp_arrival_acceptance.fk_imp_arrival_predecessor' AS check_name, COUNT(*) AS violations FROM `imp_arrival_acceptance` c WHERE c.`tenant_id` IS NOT NULL AND c.`predecessor_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`predecessor_acceptance_id`) UNION ALL
SELECT 'imp_arrival_difference.fk_imp_arrival_difference_evidence' AS check_name, COUNT(*) AS violations FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND c.`evidence_revision` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`evidence_id`=c.`evidence_id` AND r.`revision_no`=c.`evidence_revision`) UNION ALL
SELECT 'imp_arrival_difference.fk_imp_arrival_difference_line' AS check_name, COUNT(*) AS violations FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_line_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_line` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`arrival_line_id`) UNION ALL
SELECT 'imp_arrival_difference.fk_imp_arrival_difference_root' AS check_name, COUNT(*) AS violations FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`arrival_acceptance_id`) UNION ALL
SELECT 'imp_arrival_line.fk_imp_arrival_line_root' AS check_name, COUNT(*) AS violations FROM `imp_arrival_line` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`arrival_acceptance_id`) UNION ALL
SELECT 'imp_cutover_readiness_device.fk_cut_structured_79' AS check_name, COUNT(*) AS violations FROM `imp_cutover_readiness_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`snapshot_id`) UNION ALL
SELECT 'imp_cutover_readiness_source.fk_cut_structured_80' AS check_name, COUNT(*) AS violations FROM `imp_cutover_readiness_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`snapshot_id`) UNION ALL
SELECT 'imp_cutover_readiness_source_object.fk_cut_structured_81' AS check_name, COUNT(*) AS violations FROM `imp_cutover_readiness_source_object` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_fact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_source` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_fact_id`) UNION ALL
SELECT 'imp_cutover_readiness_source_watermark.fk_cut_structured_82' AS check_name, COUNT(*) AS violations FROM `imp_cutover_readiness_source_watermark` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_fact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_source` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_fact_id`) UNION ALL
SELECT 'imp_cutover_readiness_unmet.fk_cut_structured_83' AS check_name, COUNT(*) AS violations FROM `imp_cutover_readiness_unmet` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`snapshot_id`) UNION ALL
SELECT 'imp_delivery_evidence_revision.fk_imp_delivery_evidence_revision_root' AS check_name, COUNT(*) AS violations FROM `imp_delivery_evidence_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`evidence_id`) UNION ALL
SELECT 'int_batch_JOB_EXECUTION.JOB_INST_EXEC_FK' AS check_name, COUNT(*) AS violations FROM `int_batch_JOB_EXECUTION` c WHERE c.`JOB_INSTANCE_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_INSTANCE` r WHERE r.`JOB_INSTANCE_ID`=c.`JOB_INSTANCE_ID`) UNION ALL
SELECT 'int_batch_JOB_EXECUTION_CONTEXT.JOB_EXEC_CTX_FK' AS check_name, COUNT(*) AS violations FROM `int_batch_JOB_EXECUTION_CONTEXT` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` r WHERE r.`JOB_EXECUTION_ID`=c.`JOB_EXECUTION_ID`) UNION ALL
SELECT 'int_batch_JOB_EXECUTION_PARAMS.JOB_EXEC_PARAMS_FK' AS check_name, COUNT(*) AS violations FROM `int_batch_JOB_EXECUTION_PARAMS` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` r WHERE r.`JOB_EXECUTION_ID`=c.`JOB_EXECUTION_ID`) UNION ALL
SELECT 'int_batch_STEP_EXECUTION.JOB_EXEC_STEP_FK' AS check_name, COUNT(*) AS violations FROM `int_batch_STEP_EXECUTION` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` r WHERE r.`JOB_EXECUTION_ID`=c.`JOB_EXECUTION_ID`) UNION ALL
SELECT 'int_batch_STEP_EXECUTION_CONTEXT.STEP_EXEC_CTX_FK' AS check_name, COUNT(*) AS violations FROM `int_batch_STEP_EXECUTION_CONTEXT` c WHERE c.`STEP_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_STEP_EXECUTION` r WHERE r.`STEP_EXECUTION_ID`=c.`STEP_EXECUTION_ID`) UNION ALL
SELECT 'plt_dynamic_form_instance.fk_plt_dynamic_form_instance_revision' AS check_name, COUNT(*) AS violations FROM `plt_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_revision_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`template_revision_id` AND r.`template_id`=c.`template_id`) UNION ALL
SELECT 'plt_dynamic_form_template.fk_plt_dynamic_form_template_published_revision' AS check_name, COUNT(*) AS violations FROM `plt_dynamic_form_template` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_published_revision_id` IS NOT NULL AND c.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`current_published_revision_id` AND r.`template_id`=c.`id`) UNION ALL
SELECT 'plt_dynamic_form_template_revision.fk_plt_dynamic_form_revision_source' AS check_name, COUNT(*) AS violations FROM `plt_dynamic_form_template_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_revision_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_revision_id` AND r.`template_id`=c.`template_id`) UNION ALL
SELECT 'plt_dynamic_form_template_revision.fk_plt_dynamic_form_revision_template' AS check_name, COUNT(*) AS violations FROM `plt_dynamic_form_template_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`template_id`) UNION ALL
SELECT 'plt_entity_extension_value.fk_extension_definition' AS check_name, COUNT(*) AS violations FROM `plt_entity_extension_value` c WHERE c.`definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_entity_extension_definition` r WHERE r.`id`=c.`definition_revision_id`) UNION ALL
SELECT 'plt_file_access_grant.fk_plt_file_access_grant_version' AS check_name, COUNT(*) AS violations FROM `plt_file_access_grant` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`artifact_id`=c.`artifact_id` AND r.`version_no`=c.`file_version_no`) UNION ALL
SELECT 'plt_file_archive_record.fk_plt_file_archive_record_version' AS check_name, COUNT(*) AS violations FROM `plt_file_archive_record` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`artifact_id`=c.`artifact_id` AND r.`version_no`=c.`file_version_no`) UNION ALL
SELECT 'plt_file_reference.fk_plt_file_reference_artifact' AS check_name, COUNT(*) AS violations FROM `plt_file_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_artifact` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`artifact_id`) UNION ALL
SELECT 'plt_file_reference.fk_plt_file_reference_version' AS check_name, COUNT(*) AS violations FROM `plt_file_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`artifact_id`=c.`artifact_id` AND r.`version_no`=c.`file_version_no`) UNION ALL
SELECT 'plt_file_version.fk_plt_file_version_artifact' AS check_name, COUNT(*) AS violations FROM `plt_file_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_artifact` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`artifact_id`) UNION ALL
SELECT 'proj_delivery_definition_reference.fk_pdr_source' AS check_name, COUNT(*) AS violations FROM `proj_delivery_definition_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`owner_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_delivery_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`owner_revision_id`) UNION ALL
SELECT 'proj_delivery_definition_reference.fk_pdr_target' AS check_name, COUNT(*) AS violations FROM `proj_delivery_definition_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`target_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_delivery_definition_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`target_revision_id`) UNION ALL
SELECT 'proj_project_party.fk_project_party_project' AS check_name, COUNT(*) AS violations FROM `proj_project_party` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`project_id`) UNION ALL
SELECT 'proj_project_task.fk_proj_task_parent' AS check_name, COUNT(*) AS violations FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`parent_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`parent_task_id`) UNION ALL
SELECT 'proj_project_task.fk_proj_task_root' AS check_name, COUNT(*) AS violations FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`root_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`root_task_id`) UNION ALL
SELECT 'proj_project_task.fk_proj_task_state_revision' AS check_name, COUNT(*) AS violations FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`state_machine_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_task_state_machine_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`state_machine_revision_id`) UNION ALL
SELECT 'proj_project_task_assignment.fk_proj_task_assignment_task' AS check_name, COUNT(*) AS violations FROM `proj_project_task_assignment` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`project_task_id`) UNION ALL
SELECT 'proj_project_task_completion_evaluation.fk_proj_task_completion_evaluation_contract' AS check_name, COUNT(*) AS violations FROM `proj_project_task_completion_evaluation` c WHERE c.`tenant_id` IS NOT NULL AND c.`execution_contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task_execution_contract` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`execution_contract_id`) UNION ALL
SELECT 'proj_project_task_completion_evaluation.fk_proj_task_completion_evaluation_task' AS check_name, COUNT(*) AS violations FROM `proj_project_task_completion_evaluation` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`project_task_id`) UNION ALL
SELECT 'proj_project_task_execution_contract.fk_project_task_execution_contract_definition' AS check_name, COUNT(*) AS violations FROM `proj_project_task_execution_contract` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_task_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_template_task_definition` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`template_task_definition_id`) UNION ALL
SELECT 'proj_task_dependency.fk_proj_task_dependency_predecessor' AS check_name, COUNT(*) AS violations FROM `proj_task_dependency` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`predecessor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`predecessor_task_id`) UNION ALL
SELECT 'proj_task_dependency.fk_proj_task_dependency_successor' AS check_name, COUNT(*) AS violations FROM `proj_task_dependency` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`successor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`successor_task_id`) UNION ALL
SELECT 'proj_task_state_transition.fk_proj_task_state_transition_revision' AS check_name, COUNT(*) AS violations FROM `proj_task_state_transition` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_task_state_machine_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`revision_id`) UNION ALL
SELECT 'proj_task_tree_path.fk_proj_task_tree_path_ancestor' AS check_name, COUNT(*) AS violations FROM `proj_task_tree_path` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`ancestor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`ancestor_task_id`) UNION ALL
SELECT 'proj_task_tree_path.fk_proj_task_tree_path_descendant' AS check_name, COUNT(*) AS violations FROM `proj_task_tree_path` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`descendant_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`project_id`=c.`project_id` AND r.`id`=c.`descendant_task_id`) UNION ALL
SELECT 'sol_construction_plan.fk_sol_construction_plan_current_revision' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_duration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`current_duration_revision_id`) UNION ALL
SELECT 'sol_construction_plan.fk_sol_construction_plan_pending_change' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`pending_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_change` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`pending_change_id`) UNION ALL
SELECT 'sol_construction_plan.fk_sol_construction_plan_recalculation_revision' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_recalculation_source_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_recalculation_source_revision_id`) UNION ALL
SELECT 'sol_construction_plan_change.fk_sol_construction_plan_change_base_revision' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`base_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`base_revision_id`) UNION ALL
SELECT 'sol_construction_plan_change.fk_sol_construction_plan_change_candidate_revision' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`candidate_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`candidate_revision_id`) UNION ALL
SELECT 'sol_construction_plan_change.fk_sol_construction_plan_change_plan' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_id`) UNION ALL
SELECT 'sol_construction_plan_revision.fk_sol_construction_plan_revision_plan' AS check_name, COUNT(*) AS violations FROM `sol_construction_plan_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`plan_id`) UNION ALL
SELECT 'sol_dynamic_form_instance.fk_sol_dynamic_form_instance_item' AS check_name, COUNT(*) AS violations FROM `sol_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_id`) UNION ALL
SELECT 'sol_dynamic_form_instance.fk_sol_dynamic_form_instance_preparation' AS check_name, COUNT(*) AS violations FROM `sol_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_preparation.fk_sol_preparation_latest_snapshot' AS check_name, COUNT(*) AS violations FROM `sol_preparation` c WHERE c.`tenant_id` IS NOT NULL AND c.`latest_readiness_snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_readiness_snapshot` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`latest_readiness_snapshot_id`) UNION ALL
SELECT 'sol_preparation.fk_sol_preparation_source' AS check_name, COUNT(*) AS violations FROM `sol_preparation` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_preparation_id`) UNION ALL
SELECT 'sol_preparation_item.fk_sol_preparation_item_preparation' AS check_name, COUNT(*) AS violations FROM `sol_preparation_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_preparation_item.fk_sol_preparation_item_source' AS check_name, COUNT(*) AS violations FROM `sol_preparation_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_item_id`) UNION ALL
SELECT 'sol_preparation_item_waiver.fk_sol_preparation_waiver_item' AS check_name, COUNT(*) AS violations FROM `sol_preparation_item_waiver` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_id`) UNION ALL
SELECT 'sol_preparation_item_waiver.fk_sol_preparation_waiver_preparation' AS check_name, COUNT(*) AS violations FROM `sol_preparation_item_waiver` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_preparation_readiness_snapshot.fk_sol_preparation_snapshot_preparation' AS check_name, COUNT(*) AS violations FROM `sol_preparation_readiness_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_preparation_source_reference.fk_sol_preparation_source_item' AS check_name, COUNT(*) AS violations FROM `sol_preparation_source_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`item_id`) UNION ALL
SELECT 'sol_preparation_source_reference.fk_sol_preparation_source_preparation' AS check_name, COUNT(*) AS violations FROM `sol_preparation_source_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_requirement_analysis_section.fk_sol_requirement_section_preparation' AS check_name, COUNT(*) AS violations FROM `sol_requirement_analysis_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`preparation_id`) UNION ALL
SELECT 'sol_requirement_analysis_section.fk_sol_requirement_section_source' AS check_name, COUNT(*) AS violations FROM `sol_requirement_analysis_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_section_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_requirement_analysis_section` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_section_id`) UNION ALL
SELECT 'srv_inspection_annotation.fk_ins_annotation_input' AS check_name, COUNT(*) AS violations FROM `srv_inspection_annotation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`report_id`=c.`report_id` AND r.`result_id`=c.`result_id`) UNION ALL
SELECT 'srv_inspection_attempt.fk_ins_attempt_task' AS check_name, COUNT(*) AS violations FROM `srv_inspection_attempt` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`task_id`) UNION ALL
SELECT 'srv_inspection_collection_receipt.fk_ins_receipt_execution' AS check_name, COUNT(*) AS violations FROM `srv_inspection_collection_receipt` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`execution_id`) UNION ALL
SELECT 'srv_inspection_detection_result.fk_ins_result_execution' AS check_name, COUNT(*) AS violations FROM `srv_inspection_detection_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`attempt_id`=c.`attempt_id` AND r.`device_id`=c.`device_id` AND r.`id`=c.`execution_id`) UNION ALL
SELECT 'srv_inspection_detection_result.fk_ins_result_snapshot' AS check_name, COUNT(*) AS violations FROM `srv_inspection_detection_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`rule_snapshot_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_rule_snapshot` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`attempt_id`=c.`attempt_id` AND r.`device_id`=c.`device_id` AND r.`id`=c.`rule_snapshot_id` AND r.`rule_revision_id`=c.`rule_revision_id`) UNION ALL
SELECT 'srv_inspection_evidence.fk_ins_evidence_remediation' AS check_name, COUNT(*) AS violations FROM `srv_inspection_evidence` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`remediation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_service_issue_remediation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`remediation_id`) UNION ALL
SELECT 'srv_inspection_execution.fk_ins_execution_attempt' AS check_name, COUNT(*) AS violations FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`attempt_id`) UNION ALL
SELECT 'srv_inspection_execution.fk_ins_execution_device' AS check_name, COUNT(*) AS violations FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`device_id`=c.`device_id`) UNION ALL
SELECT 'srv_inspection_execution.fk_ins_execution_precheck' AS check_name, COUNT(*) AS violations FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`precheck_execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`attempt_id`=c.`attempt_id` AND r.`device_id`=c.`device_id` AND r.`id`=c.`precheck_execution_id`) UNION ALL
SELECT 'srv_inspection_feedback_confirmation.fk_ins_confirmation_annotation' AS check_name, COUNT(*) AS violations FROM `srv_inspection_feedback_confirmation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`annotation_id`) UNION ALL
SELECT 'srv_inspection_feedback_export_item.fk_inspection_export_annotation' AS check_name, COUNT(*) AS violations FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`annotation_id`) UNION ALL
SELECT 'srv_inspection_feedback_export_item.fk_inspection_export_confirmation' AS check_name, COUNT(*) AS violations FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`confirmation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_feedback_confirmation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`confirmation_id`) UNION ALL
SELECT 'srv_inspection_feedback_export_item.fk_inspection_export_item' AS check_name, COUNT(*) AS violations FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`export_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_feedback_export` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`export_id`) UNION ALL
SELECT 'srv_inspection_parse_part.fk_ins_parse_execution' AS check_name, COUNT(*) AS violations FROM `srv_inspection_parse_part` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`execution_id`) UNION ALL
SELECT 'srv_inspection_report_file.fk_ins_report_file_report' AS check_name, COUNT(*) AS violations FROM `srv_inspection_report_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`report_id`) UNION ALL
SELECT 'srv_inspection_report_result.fk_ins_report_result_report' AS check_name, COUNT(*) AS violations FROM `srv_inspection_report_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`report_id`) UNION ALL
SELECT 'srv_inspection_report_result.fk_ins_report_result_result' AS check_name, COUNT(*) AS violations FROM `srv_inspection_report_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_detection_result` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`result_id`) UNION ALL
SELECT 'srv_inspection_report_revision.fk_ins_report_attempt' AS check_name, COUNT(*) AS violations FROM `srv_inspection_report_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`attempt_id`) UNION ALL
SELECT 'srv_inspection_rule_command_revision.fk_srv_inspection_command_revision' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_command_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`revision_id`) UNION ALL
SELECT 'srv_inspection_rule_feedback.fk_ins_feedback_device' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`device_id`=c.`device_id`) UNION ALL
SELECT 'srv_inspection_rule_feedback.fk_ins_feedback_task' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`task_id`) UNION ALL
SELECT 'srv_inspection_rule_optimization.fk_ins_optimization_draft' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`draft_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`draft_rule_revision_id`) UNION ALL
SELECT 'srv_inspection_rule_optimization.fk_ins_optimization_source' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_rule_revision_id`) UNION ALL
SELECT 'srv_inspection_rule_optimization.fk_inspection_optimization_annotation' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`source_annotation_id`) UNION ALL
SELECT 'srv_inspection_rule_product_type_revision.fk_srv_inspection_product_type_revision' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_product_type_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`revision_id`) UNION ALL
SELECT 'srv_inspection_rule_revision.fk_srv_inspection_revision_rule' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_id` IS NOT NULL AND c.`rule_name_snapshot` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`rule_id` AND r.`rule_name`=c.`rule_name_snapshot`) UNION ALL
SELECT 'srv_inspection_rule_security_review.fk_srv_inspection_review_revision' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_security_review` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`revision_id`) UNION ALL
SELECT 'srv_inspection_rule_selection.fk_ins_selection_device' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_selection` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`device_id`=c.`device_id`) UNION ALL
SELECT 'srv_inspection_rule_selection.fk_ins_selection_revision' AS check_name, COUNT(*) AS violations FROM `srv_inspection_rule_selection` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`rule_revision_id`) UNION ALL
SELECT 'srv_inspection_task.fk_ins_task_attempt' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`id` IS NOT NULL AND c.`current_attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`id` AND r.`id`=c.`current_attempt_id`) UNION ALL
SELECT 'srv_inspection_task.fk_ins_task_report' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`id` IS NOT NULL AND c.`current_report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`id` AND r.`id`=c.`current_report_id`) UNION ALL
SELECT 'srv_inspection_task_device.fk_ins_device_task' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`task_id`) UNION ALL
SELECT 'srv_inspection_task_event.fk_ins_event_task' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task_event` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`task_id`) UNION ALL
SELECT 'srv_inspection_task_rule_snapshot.fk_ins_snapshot_attempt' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`attempt_id`) UNION ALL
SELECT 'srv_inspection_task_rule_snapshot.fk_ins_snapshot_device' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`device_id`=c.`device_id`) UNION ALL
SELECT 'srv_inspection_task_rule_snapshot.fk_ins_snapshot_revision' AS check_name, COUNT(*) AS violations FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`id`=c.`rule_revision_id`) UNION ALL
SELECT 'srv_service_issue.fk_ins_issue_annotation' AS check_name, COUNT(*) AS violations FROM `srv_service_issue` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`report_id`=c.`report_id` AND r.`result_id`=c.`result_id` AND r.`id`=c.`annotation_id`) UNION ALL
SELECT 'srv_service_issue_remediation.fk_ins_remediation_issue' AS check_name, COUNT(*) AS violations FROM `srv_service_issue_remediation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`issue_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_service_issue` r WHERE r.`tenant_id`=c.`tenant_id` AND r.`task_id`=c.`task_id` AND r.`id`=c.`issue_id`) UNION ALL
SELECT 'menu_parent' AS check_name, (SELECT COUNT(*) FROM system_menu c WHERE c.parent_id<>0 AND NOT EXISTS (SELECT 1 FROM system_menu p WHERE p.id=c.parent_id)) AS violations UNION ALL
SELECT 'role_menu' AS check_name, (SELECT COUNT(*) FROM system_role_menu x WHERE NOT EXISTS(SELECT 1 FROM system_role r WHERE r.id=x.role_id AND r.tenant_id=x.tenant_id) OR NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.id=x.menu_id)) AS violations UNION ALL
SELECT 'user_role' AS check_name, (SELECT COUNT(*) FROM system_user_role x WHERE NOT EXISTS(SELECT 1 FROM system_users u WHERE u.id=x.user_id AND u.tenant_id=x.tenant_id) OR NOT EXISTS(SELECT 1 FROM system_role r WHERE r.id=x.role_id AND r.tenant_id=x.tenant_id)) AS violations UNION ALL
SELECT 'form_current_revision' AS check_name, (SELECT COUNT(*) FROM plt_dynamic_form_template t WHERE t.current_published_revision_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM plt_dynamic_form_template_revision r WHERE r.id=t.current_published_revision_id AND r.template_id=t.id AND r.tenant_id=t.tenant_id AND r.status_code='PUBLISHED')) AS violations UNION ALL
SELECT 'form_source_revision' AS check_name, (SELECT COUNT(*) FROM plt_dynamic_form_template_revision t WHERE t.source_revision_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM plt_dynamic_form_template_revision r WHERE r.id=t.source_revision_id AND r.template_id=t.template_id AND r.tenant_id=t.tenant_id)) AS violations UNION ALL
SELECT 'questionnaire_revision' AS check_name, (SELECT COUNT(*) FROM acc_satisfaction_questionnaire_template t WHERE t.current_revision_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM acc_satisfaction_questionnaire_template_revision r WHERE r.id=t.current_revision_id AND r.template_id=t.id AND r.tenant_id=t.tenant_id)) AS violations UNION ALL
SELECT 'oauth_source_secret_removed' AS check_name, (SELECT COUNT(*) FROM system_oauth2_client WHERE client_id<>'default' OR secret<>'' OR JSON_LENGTH(authorized_grant_types)<>0) AS violations UNION ALL
SELECT 'no_extra_user_or_tenant' AS check_name, (SELECT (SELECT COUNT(*) FROM system_users WHERE id<>1 OR tenant_id<>1)+(SELECT COUNT(*) FROM system_tenant WHERE id<>1)) AS violations UNION ALL
SELECT 'no_source_plaintext_config' AS check_name, (SELECT COUNT(*) FROM infra_config WHERE config_key='system.user.init-password') AS violations
) checked WHERE violations<>0;

SELECT id,username,status,IF(password='!BOOTSTRAP_REQUIRED!','BOOTSTRAP_REQUIRED','CONFIGURED') AS credential_state FROM system_users;
SELECT COUNT(*) AS configured_file_stores FROM infra_file_config;
