# P3-E09 数据模型逐项裁决清单

> 状态：`REVIEW_REQUIRED`
> 决策登记SHA-256：`8DACBC4D7E50A86CEF1A6CB5E9EFF4C5B2F5B88ECC12A3AD4DFC20E8E0F69D3C`
> 约束清单SHA-256：`B8E1075C3378504AF88A09CA8282E5B94414281DA2A06C050FDC399E08B9D018`
> 本清单只展开现有机器证据，不自动批准数据模型。

## 1. 核对结论与裁决分组

|分组|数量|当前事实|建议裁决方式|
|---|---:|---|---|
|表|52|与旧字段目录一致|可批量确认`ACCEPT_CURRENT`|
|字段|1,076|名称、类型、空值、默认值、生成属性和说明一致|可批量确认`ACCEPT_CURRENT`|
|表选项|52|旧基线未保存|需确认字符比较与存储规则|
|主键|52|旧基线未保存|结构性规则，可分类确认|
|外键|78|旧基线未保存|影响迁移顺序和异常隔离|
|普通索引|107|旧基线未保存|影响查询性能和写入成本|
|唯一键|104|旧基线未保存|影响重复业务数据，必须业务审查|
|CHECK|81|旧基线未保存|影响异常历史数据，必须业务审查|

## 2. 表与字段完整清单

以下每行均为`MATCH`；字段列表是本次拟批量接受的具体范围。

|编号|表|字段数|字段清单|
|---|---|---:|---|
|T-001|`acc_deliverable_template`|14|`applicable_stage`、`create_time`、`creator`、`deleted`、`deliverable_type`、`id`、`required_flag`、`status`、`template_code`、`template_document_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-002|`acc_project_deliverable`|17|`accepted_time`、`create_time`、`creator`、`deleted`、`deliverable_type`、`document_id`、`id`、`owner_id`、`planned_due_date`、`project_id`、`status`、`submit_time`、`template_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-003|`ana_project_delivery_summary`|32|`active_scope_qty`、`company_code`、`company_id`、`company_name`、`contract_count`、`customer_code`、`customer_id`、`customer_name`、`department_code`、`department_id`、`department_name`、`device_count`、`erp_delivered_qty`、`manager_employee_no`、`manager_id`、`manager_name`、`order_count`、`order_line_count`、`parent_id`、`pending_mapping_count`、`pending_qty_count`、`project_code`、`project_id`、`project_name`、`project_status`、`project_type`、`root_id`、`source_batch_no`、`statistic_time`、`tenant_id`、`update_time`、`version`|
|T-004|`ast_device_configuration`|17|`configuration_stage`、`create_time`、`creator`、`deleted`、`deployment_mode`、`device_id`、`effective_from`、`effective_to`、`id`、`install_location`、`management_address`、`project_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-005|`ast_device_configuration_feature`|11|`configuration_id`、`create_time`、`creator`、`deleted`、`feature_code`、`feature_name`、`feature_value`、`id`、`tenant_id`、`update_time`、`updater`|
|T-006|`ast_device_configuration_service`|11|`configuration_id`、`create_time`、`creator`、`deleted`、`id`、`service_code`、`service_endpoint`、`service_name`、`tenant_id`、`update_time`、`updater`|
|T-007|`ast_device_project_assignment`|33|`assignment_status`、`assignment_type`、`create_time`、`creator`、`current_device_id`、`deleted`、`device_id`、`device_sn`、`effective_from`、`effective_to`、`id`、`install_address`、`item_code`、`line_no`、`order_no`、`project_code`、`project_company_code`、`project_company_name`、`project_customer_code`、`project_customer_name`、`project_department_code`、`project_department_name`、`project_id`、`project_name`、`project_order_line_scope_id`、`source_record_key`、`source_system`、`status`、`tenant_id`、`transfer_batch_id`、`update_time`、`updater`、`version`|
|T-008|`ast_device_relation`|16|`contract_id`、`create_time`、`creator`、`deleted`、`effective_time`、`id`、`relation_type`、`source_device_id`、`source_record_key`、`source_system`、`status`、`target_device_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-009|`ast_device_shipment_event`|25|`business_action_code`、`create_time`、`creator`、`deleted`、`device_id`、`event_type`、`id`、`legacy_package_key`、`mapping_status`、`order_line_id`、`rma_marked`、`rma_no`、`rma_related_sn`、`shipment_package_id`、`shipment_time`、`source_record_key`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_month`、`warranty_start_date`|
|T-010|`ast_device_sn`|21|`asset_status`、`create_time`、`creator`、`deleted`、`hardware_customized`、`id`、`internal_serial_no`、`item_code`、`product_id`、`secondary_item`、`secondary_sn`、`sn`、`software_maintenance_status`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_status`|
|T-011|`ast_device_version`|19|`collected_time`、`component_name`、`component_type`、`create_time`、`creator`、`customized_flag`、`deleted`、`device_id`、`effective_from`、`effective_to`、`id`、`project_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`version_stage`、`version_value`|
|T-012|`ast_network_topology`|14|`create_time`、`creator`、`deleted`、`document_id`、`effective_from`、`effective_to`、`id`、`project_id`、`status`、`tenant_id`、`topology_name`、`update_time`、`updater`、`version`|
|T-013|`ast_network_topology_device_relation`|11|`create_time`、`creator`、`deleted`、`device_id`、`id`、`node_code`、`node_role`、`tenant_id`、`topology_id`、`update_time`、`updater`|
|T-014|`ast_product`|16|`create_time`、`creator`、`deleted`、`id`、`product_category_code`、`product_code`、`product_line_code`、`product_model`、`product_name`、`product_type`、`service_product_flag`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-015|`ast_product_release`|15|`create_time`、`creator`、`deleted`、`document_id`、`end_of_support_date`、`id`、`product_id`、`release_date`、`release_type`、`release_version`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-016|`com_contract`|24|`company_code`、`company_id`、`company_name`、`contract_name`、`contract_no`、`contract_type`、`create_time`、`creator`、`currency_code`、`customer_code`、`customer_id`、`customer_name`、`deleted`、`effective_date`、`expiry_date`、`id`、`master_source_record_key`、`master_source_system`、`source_sync_time`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-017|`com_contract_receivable`|57|`collected_amount`、`collected_ratio`、`company_code`、`company_id`、`company_name`、`company_resolution_source`、`contract_amount`、`contract_create_time`、`contract_id`、`contract_no`、`create_time`、`creator`、`currency_name`、`customer_code`、`customer_name`、`deleted`、`delivered_amount`、`expansion_department_code`、`expansion_department_id`、`expansion_department_name`、`expansion_department_source_key`、`id`、`import_batch_no`、`industry_code`、`industry_name`、`latest_ship_time`、`mapping_status`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`marketing_representative_code`、`marketing_representative_name`、`office_department_code`、`office_department_id`、`office_department_name`、`original_expansion_department_source_key`、`original_industry_name`、`original_system_department_source_key`、`overdue_amount`、`project_code`、`project_name`、`receivable_amount`、`secondary_representative_code`、`source_batch_code`、`source_effective_from`、`source_effective_to`、`source_order_no`、`source_record_key`、`source_sync_time`、`source_system`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`|
|T-018|`com_crm_execution_config`|34|`amount`、`borrow_qty`、`company_code`、`company_id`、`company_name`、`config_source`、`create_time`、`creator`、`crm_project_code`、`deleted`、`execution_id`、`id`、`is_af_evidence`、`item_code`、`item_model`、`item_name`、`line_type`、`memo`、`product_code`、`product_first_code`、`product_first_name`、`product_name`、`purchase_discount`、`purchase_price`、`qty`、`settlement_id`、`source_config_key`、`source_sync_time`、`status`、`tenant_id`、`unit_price`、`update_time`、`updater`、`version`|
|T-019|`com_crm_execution_order`|63|`af_evidence_status`、`af_project_amount`、`agent_name`、`application_type`、`channel_name`、`company_code`、`company_id`、`company_name`、`contact_name`、`contact_phone`、`create_time`、`creator`、`crm_project_code`、`crm_project_name`、`crm_project_type`、`customer_project_name`、`decision_path`、`deleted`、`engineering_fee`、`engineering_fee_raw`、`execution_no`、`expansion_department_code`、`expansion_department_id`、`expansion_department_name`、`expansion_department_source_key`、`final_customer_name`、`id`、`industry_code`、`industry_name`、`loan_reason`、`major_project_level`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`office_department_code`、`office_department_id`、`office_department_name`、`predicted_bid_time`、`primary_project_id`、`project_amount`、`project_manager_code`、`project_manager_name`、`receiver_address`、`receiver_contact`、`receiver_name`、`required_in_date`、`sales_rep_code`、`sales_rep_name`、`sales_rep_phone`、`service_type_name`、`source_object_id`、`source_sync_time`、`source_system`、`status`、`submit_time`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`、`version`|
|T-020|`com_delivery_scope`|36|`allocated_qty`、`allocation_source`、`change_reason`、`create_time`、`creator`、`current_order_line_id`、`deleted`、`effective_from`、`effective_to`、`id`、`item_code`、`item_desc`、`line_no`、`order_company_code`、`order_company_name`、`order_line_id`、`order_no`、`order_source_system`、`order_type`、`project_code`、`project_company_code`、`project_company_name`、`project_customer_code`、`project_customer_name`、`project_department_code`、`project_department_name`、`project_id`、`project_manager_employee_no`、`project_manager_name`、`project_name`、`scope_status`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-021|`com_execution_order_merge_batch`|17|`agent_name`、`contract_id`、`create_time`、`creator`、`deleted`、`id`、`legacy_contract_no`、`primary_execution_id`、`project_name`、`source_merge_key`、`source_order_codes`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-022|`com_execution_order_merge_member`|16|`create_time`、`creator`、`deleted`、`execution_id`、`execution_no`、`execution_no_short`、`id`、`is_primary`、`member_sort`、`merge_batch_id`、`profit_center`、`source_order_code`、`source_record_key`、`tenant_id`、`update_time`、`updater`|
|T-023|`com_order_change_relation`|16|`change_batch_no`、`create_time`、`creator`、`deleted`、`effective_time`、`id`、`reason`、`relation_type`、`source_evidence`、`source_order_id`、`status`、`target_order_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-024|`com_order_contract_relation`|11|`contract_id`、`create_time`、`creator`、`deleted`、`id`、`order_id`、`relation_role`、`relation_source`、`tenant_id`、`update_time`、`updater`|
|T-025|`com_order_execution_relation`|16|`create_time`、`creator`、`deleted`、`execution_id`、`id`、`is_primary`、`mapping_status`、`order_id`、`primary_order_id`、`relation_source`、`source_record_key`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-026|`com_order_line_execution_relation`|14|`create_time`、`creator`、`deleted`、`execution_id`、`id`、`mapping_status`、`order_line_id`、`relation_source`、`source_record_key`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-027|`com_project_contract_relation`|17|`contract_id`、`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`project_id`、`relation_role`、`source_record_key`、`source_system`、`source_table`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-028|`com_sales_order`|24|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`customer_required_time`、`deleted`、`id`、`order_comment`、`order_create_time`、`order_no`、`order_type`、`sales_type`、`source_project_name`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-029|`com_sales_order_line`|32|`bundle_code`、`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`deleted`、`delivered_qty`、`id`、`item_code`、`item_desc`、`line_no`、`line_type`、`open_qty`、`order_id`、`order_no`、`order_qty`、`order_type`、`product_id`、`profit_center`、`real_execution_no`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_month`|
|T-030|`com_shipment_contract_reference`|31|`company_code`、`company_id`、`company_name`、`contract_id`、`contract_no`、`contract_type`、`create_time`、`creator`、`customer_name`、`deleted`、`id`、`mapping_status`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`office_department_code`、`office_department_id`、`office_department_name`、`project_name`、`remark`、`source_record_key`、`source_sync_time`、`source_system`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`、`warranty_flag`|
|T-031|`com_shipment_package`|19|`carrier_name`、`create_time`、`creator`、`deleted`、`express_no`、`id`、`mapping_status`、`package_no`、`receiver_name`、`shipment_contract_ref_id`、`shipment_time`、`source_record_key`、`source_sync_time`、`source_system`、`tenant_id`、`update_time`、`updater`、`warranty_end_time`、`warranty_start_time`|
|T-032|`cus_customer`|14|`create_time`、`creator`、`customer_address`、`customer_code`、`customer_name`、`deleted`、`id`、`industry_code`、`service_level_code`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-033|`cus_customer_contact`|18|`contact_address`、`contact_name`、`create_time`、`creator`、`customer_department_name`、`customer_id`、`deleted`、`email`、`id`、`is_primary`、`phone`、`position_name`、`primary_customer_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-034|`kno_device_technical_advisory_match`|16|`advisory_id`、`create_time`、`creator`、`deleted`、`device_id`、`handled_time`、`handler_id`、`handling_note`、`id`、`match_status`、`matched_time`、`matched_version_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-035|`kno_technical_advisory`|29|`advisory_content`、`advisory_no`、`advisory_title`、`advisory_type`、`approval_note`、`create_time`、`creator`、`deleted`、`document_id`、`effective_from`、`effective_to`、`id`、`impact_risk`、`judgment_method`、`owner_id`、`planned_due_date`、`publish_time`、`root_cause`、`severity`、`solution`、`status`、`symptom`、`tenant_id`、`trigger_condition`、`update_time`、`updater`、`version`、`visibility_scope`、`workaround`|
|T-036|`kno_technical_advisory_product_relation`|10|`advisory_id`、`affected_version_expression`、`create_time`、`creator`、`deleted`、`id`、`product_id`、`tenant_id`、`update_time`、`updater`|
|T-037|`kno_technical_advisory_read_record`|12|`advisory_id`、`confirmed_time`、`create_time`、`creator`、`deleted`、`first_read_time`、`id`、`read_status`、`reader_id`、`tenant_id`、`update_time`、`updater`|
|T-038|`plt_business_document`|13|`create_time`、`creator`、`current_version_id`、`deleted`、`document_code`、`document_name`、`document_type`、`id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-039|`plt_document_version`|15|`create_time`、`creator`、`deleted`、`document_id`、`file_checksum`、`file_id`、`file_name`、`id`、`status`、`tenant_id`、`update_time`、`updater`、`uploaded_by`、`uploaded_time`、`version_no`|
|T-040|`plt_external_key_mapping`|15|`batch_id`、`create_time`、`creator`、`id`、`mapping_status`、`source_business_key`、`source_checksum`、`source_pk`、`source_system`、`source_table`、`target_id`、`target_table`、`tenant_id`、`update_time`、`updater`|
|T-041|`plt_migration_issue`|18|`batch_id`、`candidate_target_ids`、`create_time`、`creator`、`id`、`issue_type`、`raw_business_key`、`raw_payload`、`resolution_action`、`resolution_status`、`resolved_time`、`resolver`、`source_pk`、`source_system`、`source_table`、`tenant_id`、`update_time`、`updater`|
|T-042|`plt_migration_source_record`|16|`batch_id`、`create_time`、`creator`、`extracted_time`、`id`、`mapped_target_count`、`mapping_status`、`source_business_key`、`source_checksum`、`source_payload`、`source_pk`、`source_system`、`source_table`、`tenant_id`、`update_time`、`updater`|
|T-043|`plt_sync_batch`|20|`batch_no`、`create_time`、`creator`、`error_summary`、`failure_count`、`finished_time`、`id`、`object_type`、`read_count`、`source_cursor`、`source_extract_checksum`、`source_extract_location`、`source_system`、`started_time`、`status`、`success_count`、`sync_mode`、`tenant_id`、`update_time`、`updater`|
|T-044|`proj_project`|43|`business_type`、`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`customer_project_name`、`deleted`、`department_code`、`department_id`、`department_name`、`id`、`implementation_mode`、`industry_code`、`lifecycle_template_id`、`major_project_level`、`manager_employee_no`、`manager_id`、`manager_name`、`not_track_reason`、`parent_id`、`project_category`、`project_close_time`、`project_code`、`project_name`、`project_refresh_time`、`project_start_time`、`project_type`、`root_id`、`sales_type`、`service_level_code`、`source_type`、`status`、`tenant_id`、`tree_depth`、`tree_path`、`tree_sort`、`update_time`、`updater`、`version`|
|T-045|`proj_project_company_department_relation`|21|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`deleted`、`department_code`、`department_id`、`department_name`、`effective_from`、`effective_to`、`id`、`is_primary`、`primary_project_id`、`project_id`、`relation_role`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-046|`proj_project_member_assignment`|22|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`deleted`、`department_code`、`department_name`、`effective_from`、`effective_to`、`employee_no`、`id`、`member_name`、`member_role`、`project_id`、`responsibility`、`status`、`tenant_id`、`update_time`、`updater`、`user_id`、`version`|
|T-047|`proj_project_party`|20|`contact_name`、`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`party_code`、`party_name`、`party_role`、`phone`、`project_id`、`source_record_key`、`source_system`、`source_table`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-048|`proj_project_portfolio`|16|`create_time`、`creator`、`deleted`、`id`、`member_rule`、`member_rule_type`、`owner_id`、`portfolio_code`、`portfolio_name`、`status`、`tenant_id`、`update_time`、`updater`、`valid_from`、`valid_to`、`version`|
|T-049|`proj_project_portfolio_member`|12|`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`member_source`、`portfolio_id`、`project_id`、`tenant_id`、`update_time`、`updater`|
|T-050|`proj_project_relation`|14|`create_time`、`creator`、`deleted`、`effective_time`、`id`、`reason`、`relation_type`、`source_project_id`、`status`、`target_project_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-051|`srv_service_incident`|23|`closed_time`、`create_time`、`creator`、`deleted`、`id`、`incident_no`、`incident_title`、`incident_type`、`occurred_time`、`owner_id`、`project_id`、`report_document_id`、`reported_time`、`restored_time`、`root_cause`、`severity`、`solution`、`status`、`symptom`、`tenant_id`、`update_time`、`updater`、`version`|
|T-052|`srv_service_incident_device_relation`|10|`create_time`、`creator`、`deleted`、`device_id`、`id`、`impact_description`、`incident_id`、`tenant_id`、`update_time`、`updater`|

## 3. 表选项完整清单

|编号|表|当前表选项|建议|
|---|---|---|---|
|O-001|`acc_deliverable_template`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交付件类型和模板配置'`|待确认字符比较规则后分类接受|
|O-002|`acc_project_deliverable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目交付件实例及完成状态'`|待确认字符比较规则后分类接受|
|O-003|`ana_project_delivery_summary`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '可重建的项目合同、订单、发货和SN汇总读模型'`|待确认字符比较规则后分类接受|
|O-004|`ast_device_configuration`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备分阶段配置主记录'`|待确认字符比较规则后分类接受|
|O-005|`ast_device_configuration_feature`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置启用特性明细'`|待确认字符比较规则后分类接受|
|O-006|`ast_device_configuration_service`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置运行服务明细'`|待确认字符比较规则后分类接受|
|O-007|`ast_device_project_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN到项目的归属及转移历史'`|待确认字符比较规则后分类接受|
|O-008|`ast_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同维度主附加SN、RMA替换等设备关系'`|待确认字符比较规则后分类接受|
|O-009|`ast_device_shipment_event`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备发货、退回、返还和再次发放的物流生命周期事件'`|待确认字符比较规则后分类接受|
|O-010|`ast_device_sn`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN主档，不承载重复发货事件'`|待确认字符比较规则后分类接受|
|O-011|`ast_device_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备组件版本及阶段历史'`|待确认字符比较规则后分类接受|
|O-012|`ast_network_topology`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目网络拓扑版本'`|待确认字符比较规则后分类接受|
|O-013|`ast_network_topology_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '拓扑节点与设备关系'`|待确认字符比较规则后分类接受|
|O-014|`ast_product`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品主档，安服属性由产品配置判定'`|待确认字符比较规则后分类接受|
|O-015|`ast_product_release`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品版本发布与支持周期'`|待确认字符比较规则后分类接受|
|O-016|`com_contract`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同主档，以所属公司和合同号为业务唯一键'`|待确认字符比较规则后分类接受|
|O-017|`com_contract_receivable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SAP合同回款来源记录，保留公司待解析和一号多行证据'`|待确认字符比较规则后分类接受|
|O-018|`com_crm_execution_config`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM已获得的执行单产品配置，仅作辅助证据'`|待确认字符比较规则后分类接受|
|O-019|`com_crm_execution_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM执行单辅助主档，安服仅保存正向证据'`|待确认字符比较规则后分类接受|
|O-020|`com_delivery_scope`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目对ERP订单行的权威实施范围'`|待确认字符比较规则后分类接受|
|O-021|`com_execution_order_merge_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊业务合并下单批次'`|待确认字符比较规则后分类接受|
|O-022|`com_execution_order_merge_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊合并下单执行单成员，不限制成员数量'`|待确认字符比较规则后分类接受|
|O-023|`com_order_change_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '改单、拆分、替代和退货订单血缘'`|待确认字符比较规则后分类接受|
|O-024|`com_order_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同与ERP订单N:N关系'`|待确认字符比较规则后分类接受|
|O-025|`com_order_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单与CRM执行单辅助关系'`|待确认字符比较规则后分类接受|
|O-026|`com_order_line_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单行与CRM执行单辅助关系'`|待确认字符比较规则后分类接受|
|O-027|`com_project_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目与合同直接N:N关系'`|待确认字符比较规则后分类接受|
|O-028|`com_sales_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单主档'`|待确认字符比较规则后分类接受|
|O-029|`com_sales_order_line`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单行及数量快照'`|待确认字符比较规则后分类接受|
|O-030|`com_shipment_contract_reference`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货记录的合同归属，不作为合同主档'`|待确认字符比较规则后分类接受|
|O-031|`com_shipment_package`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货装箱单主档'`|待确认字符比较规则后分类接受|
|O-032|`cus_customer`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户主档'`|待确认字符比较规则后分类接受|
|O-033|`cus_customer_contact`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户联系人'`|待确认字符比较规则后分类接受|
|O-034|`kno_device_technical_advisory_match`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备与技术公告的匹配及处置结果'`|待确认字符比较规则后分类接受|
|O-035|`kno_technical_advisory`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技术公告主档'`|待确认字符比较规则后分类接受|
|O-036|`kno_technical_advisory_product_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技术公告适用产品和版本范围'`|待确认字符比较规则后分类接受|
|O-037|`kno_technical_advisory_read_record`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技术公告阅读及确认记录'`|待确认字符比较规则后分类接受|
|O-038|`plt_business_document`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档元数据'`|待确认字符比较规则后分类接受|
|O-039|`plt_document_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档不可变版本'`|待确认字符比较规则后分类接受|
|O-040|`plt_external_key_mapping`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旧主键到新主键的可追溯映射'`|待确认字符比较规则后分类接受|
|O-041|`plt_migration_issue`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移缺失、重复、多义映射和人工解决记录'`|待确认字符比较规则后分类接受|
|O-042|`plt_migration_source_record`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移批次逐源行的完整原值证据，不因目标归并或去重而覆盖'`|待确认字符比较规则后分类接受|
|O-043|`plt_sync_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '一次性迁移及只读同步批次'`|待确认字符比较规则后分类接受|
|O-044|`proj_project`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目主档及非固定层级项目树'`|待确认字符比较规则后分类接受|
|O-045|`proj_project_company_department_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目业务角色下的公司与部门组合关系，保留配对但不建立全局主数据从属关系'`|待确认字符比较规则后分类接受|
|O-046|`proj_project_member_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目成员、角色及有效期'`|待确认字符比较规则后分类接受|
|O-047|`proj_project_party`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目参与方，按合同客户、最终用户、代理商、服务商等角色保存'`|待确认字符比较规则后分类接受|
|O-048|`proj_project_portfolio`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合，不改变项目父子层级'`|待确认字符比较规则后分类接受|
|O-049|`proj_project_portfolio_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合成员'`|待确认字符比较规则后分类接受|
|O-050|`proj_project_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '扩容、续采、改造等非树项目关系'`|待确认字符比较规则后分类接受|
|O-051|`srv_service_incident`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障及服务事件主档'`|待确认字符比较规则后分类接受|
|O-052|`srv_service_incident_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障与受影响设备多对多关系'`|待确认字符比较规则后分类接受|

## 4. 主键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|PK-001|`acc_deliverable_template`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-002|`acc_project_deliverable`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-003|`ana_project_delivery_summary`|`PRIMARY KEY (tenant_id, project_id)`|结构性规则；建议接受|
|PK-004|`ast_device_configuration`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-005|`ast_device_configuration_feature`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-006|`ast_device_configuration_service`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-007|`ast_device_project_assignment`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-008|`ast_device_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-009|`ast_device_shipment_event`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-010|`ast_device_sn`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-011|`ast_device_version`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-012|`ast_network_topology`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-013|`ast_network_topology_device_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-014|`ast_product`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-015|`ast_product_release`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-016|`com_contract`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-017|`com_contract_receivable`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-018|`com_crm_execution_config`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-019|`com_crm_execution_order`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-020|`com_delivery_scope`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-021|`com_execution_order_merge_batch`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-022|`com_execution_order_merge_member`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-023|`com_order_change_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-024|`com_order_contract_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-025|`com_order_execution_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-026|`com_order_line_execution_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-027|`com_project_contract_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-028|`com_sales_order`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-029|`com_sales_order_line`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-030|`com_shipment_contract_reference`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-031|`com_shipment_package`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-032|`cus_customer`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-033|`cus_customer_contact`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-034|`kno_device_technical_advisory_match`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-035|`kno_technical_advisory`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-036|`kno_technical_advisory_product_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-037|`kno_technical_advisory_read_record`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-038|`plt_business_document`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-039|`plt_document_version`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-040|`plt_external_key_mapping`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-041|`plt_migration_issue`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-042|`plt_migration_source_record`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-043|`plt_sync_batch`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-044|`proj_project`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-045|`proj_project_company_department_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-046|`proj_project_member_assignment`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-047|`proj_project_party`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-048|`proj_project_portfolio`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-049|`proj_project_portfolio_member`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-050|`proj_project_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-051|`srv_service_incident`|`PRIMARY KEY (id)`|结构性规则；建议接受|
|PK-052|`srv_service_incident_device_relation`|`PRIMARY KEY (id)`|结构性规则；建议接受|

## 5. 外键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|FK-001|`acc_deliverable_template`|`CONSTRAINT fk_deliverable_template_document FOREIGN KEY (tenant_id, template_document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-002|`acc_project_deliverable`|`CONSTRAINT fk_project_deliverable_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-003|`acc_project_deliverable`|`CONSTRAINT fk_project_deliverable_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-004|`acc_project_deliverable`|`CONSTRAINT fk_project_deliverable_template FOREIGN KEY (tenant_id, template_id) REFERENCES acc_deliverable_template (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-005|`ana_project_delivery_summary`|`CONSTRAINT fk_project_summary_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-006|`ana_project_delivery_summary`|`CONSTRAINT fk_project_summary_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-007|`ast_device_configuration`|`CONSTRAINT fk_device_configuration_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-008|`ast_device_configuration`|`CONSTRAINT fk_device_configuration_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-009|`ast_device_configuration_feature`|`CONSTRAINT fk_configuration_feature_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-010|`ast_device_configuration_service`|`CONSTRAINT fk_configuration_service_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-011|`ast_device_project_assignment`|`CONSTRAINT fk_device_assignment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-012|`ast_device_project_assignment`|`CONSTRAINT fk_device_assignment_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-013|`ast_device_project_assignment`|`CONSTRAINT fk_device_assignment_scope FOREIGN KEY (tenant_id, project_order_line_scope_id) REFERENCES com_delivery_scope (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-014|`ast_device_relation`|`CONSTRAINT fk_device_relation_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-015|`ast_device_relation`|`CONSTRAINT fk_device_relation_source FOREIGN KEY (tenant_id, source_device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-016|`ast_device_relation`|`CONSTRAINT fk_device_relation_target FOREIGN KEY (tenant_id, target_device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-017|`ast_device_shipment_event`|`CONSTRAINT fk_shipment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-018|`ast_device_shipment_event`|`CONSTRAINT fk_shipment_order_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-019|`ast_device_shipment_event`|`CONSTRAINT fk_shipment_package FOREIGN KEY (tenant_id, shipment_package_id) REFERENCES com_shipment_package (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-020|`ast_device_version`|`CONSTRAINT fk_device_version_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-021|`ast_device_version`|`CONSTRAINT fk_device_version_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-022|`ast_network_topology`|`CONSTRAINT fk_network_topology_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-023|`ast_network_topology`|`CONSTRAINT fk_network_topology_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-024|`ast_network_topology_device_relation`|`CONSTRAINT fk_topology_device_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-025|`ast_network_topology_device_relation`|`CONSTRAINT fk_topology_device_topology FOREIGN KEY (tenant_id, topology_id) REFERENCES ast_network_topology (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-026|`ast_product_release`|`CONSTRAINT fk_product_release_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-027|`ast_product_release`|`CONSTRAINT fk_product_release_product FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-028|`com_contract`|`CONSTRAINT fk_contract_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-029|`com_contract_receivable`|`CONSTRAINT fk_contract_receivable_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-030|`com_crm_execution_config`|`CONSTRAINT fk_crm_execution_config_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-031|`com_crm_execution_order`|`CONSTRAINT fk_crm_execution_project FOREIGN KEY (tenant_id, primary_project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-032|`com_delivery_scope`|`CONSTRAINT fk_scope_order_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-033|`com_delivery_scope`|`CONSTRAINT fk_scope_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-034|`com_execution_order_merge_batch`|`CONSTRAINT fk_execution_merge_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-035|`com_execution_order_merge_batch`|`CONSTRAINT fk_execution_merge_primary FOREIGN KEY (tenant_id, primary_execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-036|`com_execution_order_merge_member`|`CONSTRAINT fk_execution_merge_member_batch FOREIGN KEY (tenant_id, merge_batch_id) REFERENCES com_execution_order_merge_batch (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-037|`com_execution_order_merge_member`|`CONSTRAINT fk_execution_merge_member_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-038|`com_order_change_relation`|`CONSTRAINT fk_order_change_source FOREIGN KEY (tenant_id, source_order_id) REFERENCES com_sales_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-039|`com_order_change_relation`|`CONSTRAINT fk_order_change_target FOREIGN KEY (tenant_id, target_order_id) REFERENCES com_sales_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-040|`com_order_contract_relation`|`CONSTRAINT fk_order_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-041|`com_order_contract_relation`|`CONSTRAINT fk_order_contract_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-042|`com_order_execution_relation`|`CONSTRAINT fk_order_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-043|`com_order_execution_relation`|`CONSTRAINT fk_order_execution_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-044|`com_order_line_execution_relation`|`CONSTRAINT fk_order_line_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-045|`com_order_line_execution_relation`|`CONSTRAINT fk_order_line_execution_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-046|`com_project_contract_relation`|`CONSTRAINT fk_project_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-047|`com_project_contract_relation`|`CONSTRAINT fk_project_contract_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-048|`com_sales_order`|`CONSTRAINT fk_sales_order_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-049|`com_sales_order_line`|`CONSTRAINT fk_sales_order_line_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-050|`com_sales_order_line`|`CONSTRAINT fk_sales_order_line_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-051|`com_sales_order_line`|`CONSTRAINT fk_sales_order_line_product FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-052|`com_shipment_contract_reference`|`CONSTRAINT fk_shipment_contract_ref_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-053|`com_shipment_package`|`CONSTRAINT fk_shipment_package_contract_ref FOREIGN KEY (tenant_id, shipment_contract_ref_id) REFERENCES com_shipment_contract_reference (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-054|`cus_customer_contact`|`CONSTRAINT fk_customer_contact_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-055|`kno_device_technical_advisory_match`|`CONSTRAINT fk_device_advisory_advisory FOREIGN KEY (tenant_id, advisory_id) REFERENCES kno_technical_advisory (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-056|`kno_device_technical_advisory_match`|`CONSTRAINT fk_device_advisory_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-057|`kno_device_technical_advisory_match`|`CONSTRAINT fk_device_advisory_version FOREIGN KEY (tenant_id, matched_version_id) REFERENCES ast_device_version (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-058|`kno_technical_advisory`|`CONSTRAINT fk_technical_advisory_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-059|`kno_technical_advisory_product_relation`|`CONSTRAINT fk_advisory_product_advisory FOREIGN KEY (tenant_id, advisory_id) REFERENCES kno_technical_advisory (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-060|`kno_technical_advisory_product_relation`|`CONSTRAINT fk_advisory_product_product FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-061|`kno_technical_advisory_read_record`|`CONSTRAINT fk_advisory_read_advisory FOREIGN KEY (tenant_id, advisory_id) REFERENCES kno_technical_advisory (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-062|`plt_document_version`|`CONSTRAINT fk_document_version_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-063|`plt_external_key_mapping`|`CONSTRAINT fk_external_key_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-064|`plt_migration_issue`|`CONSTRAINT fk_migration_issue_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-065|`plt_migration_source_record`|`CONSTRAINT fk_migration_source_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-066|`proj_project`|`CONSTRAINT fk_project_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-067|`proj_project`|`CONSTRAINT fk_project_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-068|`proj_project_company_department_relation`|`CONSTRAINT fk_project_company_department_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-069|`proj_project_member_assignment`|`CONSTRAINT fk_project_member_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-070|`proj_project_party`|`CONSTRAINT fk_project_party_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-071|`proj_project_portfolio_member`|`CONSTRAINT fk_portfolio_project_portfolio FOREIGN KEY (tenant_id, portfolio_id) REFERENCES proj_project_portfolio (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-072|`proj_project_portfolio_member`|`CONSTRAINT fk_portfolio_project_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-073|`proj_project_relation`|`CONSTRAINT fk_project_rel_source FOREIGN KEY (tenant_id, source_project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-074|`proj_project_relation`|`CONSTRAINT fk_project_rel_target FOREIGN KEY (tenant_id, target_project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-075|`srv_service_incident`|`CONSTRAINT fk_service_incident_document FOREIGN KEY (tenant_id, report_document_id) REFERENCES plt_business_document (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-076|`srv_service_incident`|`CONSTRAINT fk_service_incident_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-077|`srv_service_incident_device_relation`|`CONSTRAINT fk_incident_device_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|
|FK-078|`srv_service_incident_device_relation`|`CONSTRAINT fk_incident_device_incident FOREIGN KEY (tenant_id, incident_id) REFERENCES srv_service_incident (tenant_id, id)`|影响迁移顺序；建议接受并隔离违规历史数据|

## 6. 普通索引完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|IX-001|`acc_project_deliverable`|`KEY idx_deliverable_owner (tenant_id, owner_id, status, planned_due_date)`|查询设计规则；建议接受，后续以压测验证|
|IX-002|`acc_project_deliverable`|`KEY idx_project_deliverable (tenant_id, project_id, deliverable_type, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-003|`ana_project_delivery_summary`|`KEY idx_project_summary_company_department ( tenant_id, company_code, department_code, project_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-004|`ana_project_delivery_summary`|`KEY idx_project_summary_customer ( tenant_id, customer_code, project_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-005|`ana_project_delivery_summary`|`KEY idx_project_summary_manager ( tenant_id, manager_employee_no, project_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-006|`ana_project_delivery_summary`|`KEY idx_project_summary_project_status ( tenant_id, project_status, project_type, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-007|`ana_project_delivery_summary`|`KEY idx_project_summary_status ( tenant_id, pending_mapping_count, pending_qty_count )`|查询设计规则；建议接受，后续以压测验证|
|IX-008|`ana_project_delivery_summary`|`KEY idx_project_summary_time (tenant_id, statistic_time)`|查询设计规则；建议接受，后续以压测验证|
|IX-009|`ast_device_configuration`|`KEY idx_device_configuration (tenant_id, device_id, status, effective_from)`|查询设计规则；建议接受，后续以压测验证|
|IX-010|`ast_device_configuration`|`KEY idx_project_configuration (tenant_id, project_id, configuration_stage)`|查询设计规则；建议接受，后续以压测验证|
|IX-011|`ast_device_project_assignment`|`KEY idx_device_assignment_company_department ( tenant_id, project_company_code, project_department_code, effective_to, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-012|`ast_device_project_assignment`|`KEY idx_device_assignment_customer ( tenant_id, project_customer_code, effective_to, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-013|`ast_device_project_assignment`|`KEY idx_device_assignment_device ( tenant_id, device_id, effective_to, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-014|`ast_device_project_assignment`|`KEY idx_device_assignment_order ( tenant_id, order_no, line_no, effective_to, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-015|`ast_device_project_assignment`|`KEY idx_device_assignment_project ( tenant_id, project_id, effective_to, device_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-016|`ast_device_project_assignment`|`KEY idx_device_assignment_project_code ( tenant_id, project_code, effective_to, device_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-017|`ast_device_project_assignment`|`KEY idx_device_assignment_sn ( tenant_id, device_sn, effective_to, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-018|`ast_device_relation`|`KEY idx_device_relation_contract_refresh ( tenant_id, contract_id, relation_type, status, source_device_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-019|`ast_device_relation`|`KEY idx_device_relation_latest ( tenant_id, source_device_id, contract_id, relation_type, status, effective_time, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-020|`ast_device_relation`|`KEY idx_device_relation_source_device ( tenant_id, source_device_id, relation_type )`|查询设计规则；建议接受，后续以压测验证|
|IX-021|`ast_device_relation`|`KEY idx_device_relation_target_device ( tenant_id, target_device_id, relation_type )`|查询设计规则；建议接受，后续以压测验证|
|IX-022|`ast_device_shipment_event`|`KEY idx_shipment_device (tenant_id, device_id, shipment_time)`|查询设计规则；建议接受，后续以压测验证|
|IX-023|`ast_device_shipment_event`|`KEY idx_shipment_order_line (tenant_id, order_line_id, shipment_time)`|查询设计规则；建议接受，后续以压测验证|
|IX-024|`ast_device_shipment_event`|`KEY idx_shipment_package (tenant_id, shipment_package_id, device_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-025|`ast_device_shipment_event`|`KEY idx_shipment_rma ( tenant_id, rma_marked, business_action_code, rma_no )`|查询设计规则；建议接受，后续以压测验证|
|IX-026|`ast_device_sn`|`KEY idx_device_internal_serial_no (tenant_id, internal_serial_no)`|查询设计规则；建议接受，后续以压测验证|
|IX-027|`ast_device_sn`|`KEY idx_device_item (tenant_id, item_code, asset_status)`|查询设计规则；建议接受，后续以压测验证|
|IX-028|`ast_device_sn`|`KEY idx_device_secondary_sn (tenant_id, secondary_sn)`|查询设计规则；建议接受，后续以压测验证|
|IX-029|`ast_device_version`|`KEY idx_device_version_current ( tenant_id, device_id, component_type, status, effective_from )`|查询设计规则；建议接受，后续以压测验证|
|IX-030|`ast_device_version`|`KEY idx_project_device_version (tenant_id, project_id, version_stage)`|查询设计规则；建议接受，后续以压测验证|
|IX-031|`ast_network_topology`|`KEY idx_network_topology_project (tenant_id, project_id, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-032|`ast_network_topology_device_relation`|`KEY idx_topology_device_reverse (tenant_id, device_id, topology_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-033|`ast_product`|`KEY idx_product_line (tenant_id, product_line_code, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-034|`com_contract`|`KEY idx_contract_company (tenant_id, company_id, status, contract_no)`|查询设计规则；建议接受，后续以压测验证|
|IX-035|`com_contract`|`KEY idx_contract_customer (tenant_id, customer_id, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-036|`com_contract`|`KEY idx_contract_no (tenant_id, contract_no, company_code)`|查询设计规则；建议接受，后续以压测验证|
|IX-037|`com_contract_receivable`|`KEY idx_contract_receivable_business ( tenant_id, contract_no, company_code, mapping_status )`|查询设计规则；建议接受，后续以压测验证|
|IX-038|`com_contract_receivable`|`KEY idx_contract_receivable_company ( tenant_id, company_id, mapping_status, contract_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-039|`com_contract_receivable`|`KEY idx_contract_receivable_contract ( tenant_id, contract_id, source_sync_time )`|查询设计规则；建议接受，后续以压测验证|
|IX-040|`com_crm_execution_config`|`KEY idx_crm_execution_config_company ( tenant_id, company_code, status, execution_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-041|`com_crm_execution_config`|`KEY idx_crm_execution_config_execution ( tenant_id, execution_id, item_code )`|查询设计规则；建议接受，后续以压测验证|
|IX-042|`com_crm_execution_order`|`KEY idx_crm_execution_company_office ( tenant_id, company_id, office_department_id, status, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-043|`com_crm_execution_order`|`KEY idx_crm_execution_company_office_code ( tenant_id, company_code, office_department_code, status, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-044|`com_crm_execution_order`|`KEY idx_crm_execution_crm_project ( tenant_id, crm_project_code, execution_no )`|查询设计规则；建议接受，后续以压测验证|
|IX-045|`com_crm_execution_order`|`KEY idx_crm_execution_project ( tenant_id, primary_project_id, status )`|查询设计规则；建议接受，后续以压测验证|
|IX-046|`com_delivery_scope`|`KEY idx_scope_item (tenant_id, item_code, scope_status, project_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-047|`com_delivery_scope`|`KEY idx_scope_order_business ( tenant_id, order_source_system, order_company_code, order_type, order_no, line_no )`|查询设计规则；建议接受，后续以压测验证|
|IX-048|`com_delivery_scope`|`KEY idx_scope_order_line ( tenant_id, order_line_id, scope_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-049|`com_delivery_scope`|`KEY idx_scope_project ( tenant_id, project_id, scope_status, order_line_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-050|`com_delivery_scope`|`KEY idx_scope_project_company ( tenant_id, project_company_code, scope_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-051|`com_delivery_scope`|`KEY idx_scope_project_customer ( tenant_id, project_customer_code, scope_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-052|`com_delivery_scope`|`KEY idx_scope_project_department ( tenant_id, project_department_code, scope_status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-053|`com_execution_order_merge_batch`|`KEY idx_execution_merge_primary ( tenant_id, primary_execution_id, status )`|查询设计规则；建议接受，后续以压测验证|
|IX-054|`com_execution_order_merge_member`|`KEY idx_execution_merge_member_execution ( tenant_id, execution_id, merge_batch_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-055|`com_order_change_relation`|`KEY idx_order_change_target ( tenant_id, target_order_id, relation_type )`|查询设计规则；建议接受，后续以压测验证|
|IX-056|`com_order_contract_relation`|`KEY idx_order_contract_reverse (tenant_id, contract_id, order_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-057|`com_order_execution_relation`|`KEY idx_order_execution_execution ( tenant_id, execution_id, order_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-058|`com_order_line_execution_relation`|`KEY idx_order_line_execution_reverse (tenant_id, execution_id, order_line_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-059|`com_project_contract_relation`|`KEY idx_project_contract_reverse (tenant_id, contract_id, project_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-060|`com_sales_order`|`KEY idx_sales_order_company (tenant_id, company_id, status, order_no)`|查询设计规则；建议接受，后续以压测验证|
|IX-061|`com_sales_order`|`KEY idx_sales_order_customer (tenant_id, customer_code, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-062|`com_sales_order`|`KEY idx_sales_order_no (tenant_id, order_no)`|查询设计规则；建议接受，后续以压测验证|
|IX-063|`com_sales_order`|`KEY idx_sales_order_time (tenant_id, order_create_time, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-064|`com_sales_order_line`|`KEY idx_sales_order_line_business ( tenant_id, source_system, company_code, order_type, order_no, line_no )`|查询设计规则；建议接受，后续以压测验证|
|IX-065|`com_sales_order_line`|`KEY idx_sales_order_line_customer (tenant_id, customer_code, status, id)`|查询设计规则；建议接受，后续以压测验证|
|IX-066|`com_sales_order_line`|`KEY idx_sales_order_line_item (tenant_id, item_code)`|查询设计规则；建议接受，后续以压测验证|
|IX-067|`com_sales_order_line`|`KEY idx_sales_order_line_profit (tenant_id, profit_center, order_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-068|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_company ( tenant_id, company_id, mapping_status, contract_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-069|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_contract ( tenant_id, contract_id, mapping_status )`|查询设计规则；建议接受，后续以压测验证|
|IX-070|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_no ( tenant_id, contract_no, company_code, mapping_status )`|查询设计规则；建议接受，后续以压测验证|
|IX-071|`com_shipment_package`|`KEY idx_shipment_package_contract_ref ( tenant_id, shipment_contract_ref_id, shipment_time )`|查询设计规则；建议接受，后续以压测验证|
|IX-072|`cus_customer`|`KEY idx_customer_name (tenant_id, customer_name)`|查询设计规则；建议接受，后续以压测验证|
|IX-073|`cus_customer_contact`|`KEY idx_customer_contact (tenant_id, customer_id, status, is_primary)`|查询设计规则；建议接受，后续以压测验证|
|IX-074|`kno_device_technical_advisory_match`|`KEY idx_device_advisory_reverse (tenant_id, device_id, match_status)`|查询设计规则；建议接受，后续以压测验证|
|IX-075|`kno_technical_advisory`|`KEY idx_technical_advisory_status (tenant_id, status, publish_time)`|查询设计规则；建议接受，后续以压测验证|
|IX-076|`kno_technical_advisory_product_relation`|`KEY idx_advisory_product_reverse (tenant_id, product_id, advisory_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-077|`kno_technical_advisory_read_record`|`KEY idx_advisory_reader_reverse (tenant_id, reader_id, read_status, advisory_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-078|`plt_document_version`|`KEY idx_document_file (tenant_id, file_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-079|`plt_external_key_mapping`|`KEY idx_external_key_batch (tenant_id, batch_id, mapping_status)`|查询设计规则；建议接受，后续以压测验证|
|IX-080|`plt_external_key_mapping`|`KEY idx_external_key_source ( tenant_id, source_system, source_table, source_pk )`|查询设计规则；建议接受，后续以压测验证|
|IX-081|`plt_external_key_mapping`|`KEY idx_external_key_target ( tenant_id, target_table, target_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-082|`plt_migration_issue`|`KEY idx_migration_issue_status ( tenant_id, issue_type, resolution_status, create_time )`|查询设计规则；建议接受，后续以压测验证|
|IX-083|`plt_migration_source_record`|`KEY idx_migration_source_business ( tenant_id, source_system, source_table, source_business_key(191) )`|查询设计规则；建议接受，后续以压测验证|
|IX-084|`plt_migration_source_record`|`KEY idx_migration_source_mapping ( tenant_id, batch_id, source_table, mapping_status )`|查询设计规则；建议接受，后续以压测验证|
|IX-085|`plt_sync_batch`|`KEY idx_sync_batch_object ( tenant_id, source_system, object_type, started_time )`|查询设计规则；建议接受，后续以压测验证|
|IX-086|`proj_project`|`KEY idx_project_company_department ( tenant_id, company_code, department_code, status, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-087|`proj_project`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-088|`proj_project`|`KEY idx_project_customer_code (tenant_id, customer_code, status, id)`|查询设计规则；建议接受，后续以压测验证|
|IX-089|`proj_project`|`KEY idx_project_department_company ( tenant_id, department_code, company_code, status, id )`|查询设计规则；建议接受，后续以压测验证|
|IX-090|`proj_project`|`KEY idx_project_manager (tenant_id, manager_id, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-091|`proj_project`|`KEY idx_project_manager_employee (tenant_id, manager_employee_no, status, id)`|查询设计规则；建议接受，后续以压测验证|
|IX-092|`proj_project`|`KEY idx_project_parent (tenant_id, parent_id, tree_sort, id)`|查询设计规则；建议接受，后续以压测验证|
|IX-093|`proj_project`|`KEY idx_project_path (tenant_id, root_id, tree_path(191))`|查询设计规则；建议接受，后续以压测验证|
|IX-094|`proj_project_company_department_relation`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-095|`proj_project_company_department_relation`|`KEY idx_project_company_reverse ( tenant_id, company_code, relation_role, status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-096|`proj_project_company_department_relation`|`KEY idx_project_department_reverse ( tenant_id, department_code, company_code, relation_role, status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-097|`proj_project_member_assignment`|`KEY idx_project_member_company_department ( tenant_id, company_code, department_code, status, project_id )`|查询设计规则；建议接受，后续以压测验证|
|IX-098|`proj_project_member_assignment`|`KEY idx_project_member_employee (tenant_id, employee_no, status, project_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-099|`proj_project_member_assignment`|`KEY idx_project_member_user (tenant_id, user_id, status, project_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-100|`proj_project_party`|`KEY idx_project_party_code ( tenant_id, party_role, party_code, status )`|查询设计规则；建议接受，后续以压测验证|
|IX-101|`proj_project_party`|`KEY idx_project_party_project ( tenant_id, project_id, party_role, status )`|查询设计规则；建议接受，后续以压测验证|
|IX-102|`proj_project_portfolio`|`KEY idx_portfolio_owner (tenant_id, owner_id, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-103|`proj_project_portfolio_member`|`KEY idx_portfolio_project_reverse (tenant_id, project_id, portfolio_id)`|查询设计规则；建议接受，后续以压测验证|
|IX-104|`proj_project_relation`|`KEY idx_project_relation_target ( tenant_id, target_project_id, relation_type )`|查询设计规则；建议接受，后续以压测验证|
|IX-105|`srv_service_incident`|`KEY idx_incident_owner (tenant_id, owner_id, status)`|查询设计规则；建议接受，后续以压测验证|
|IX-106|`srv_service_incident`|`KEY idx_incident_project (tenant_id, project_id, status, occurred_time)`|查询设计规则；建议接受，后续以压测验证|
|IX-107|`srv_service_incident_device_relation`|`KEY idx_incident_device_reverse (tenant_id, device_id, incident_id)`|查询设计规则；建议接受，后续以压测验证|

## 7. 唯一键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|UK-001|`acc_deliverable_template`|`UNIQUE KEY uk_deliverable_template (tenant_id, template_code)`|影响重复数据；需逐组业务确认|
|UK-002|`acc_deliverable_template`|`UNIQUE KEY uk_deliverable_template_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-003|`acc_project_deliverable`|`UNIQUE KEY uk_project_deliverable_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-004|`ast_device_configuration`|`UNIQUE KEY uk_device_configuration_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-005|`ast_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature ( tenant_id, configuration_id, feature_code )`|影响重复数据；需逐组业务确认|
|UK-006|`ast_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-007|`ast_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service ( tenant_id, configuration_id, service_code )`|影响重复数据；需逐组业务确认|
|UK-008|`ast_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-009|`ast_device_project_assignment`|`UNIQUE KEY uk_device_assignment_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-010|`ast_device_project_assignment`|`UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id)`|影响重复数据；需逐组业务确认|
|UK-011|`ast_device_project_assignment`|`UNIQUE KEY uk_project_device_assignment_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-012|`ast_device_relation`|`UNIQUE KEY uk_device_relation_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-013|`ast_device_relation`|`UNIQUE KEY uk_device_relation_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-014|`ast_device_shipment_event`|`UNIQUE KEY uk_device_shipment_event_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-015|`ast_device_shipment_event`|`UNIQUE KEY uk_shipment_event_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-016|`ast_device_sn`|`UNIQUE KEY uk_device_sn (tenant_id, sn)`|影响重复数据；需逐组业务确认|
|UK-017|`ast_device_sn`|`UNIQUE KEY uk_device_sn_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-018|`ast_device_version`|`UNIQUE KEY uk_device_version_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-019|`ast_network_topology`|`UNIQUE KEY uk_network_topology_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-020|`ast_network_topology_device_relation`|`UNIQUE KEY uk_topology_device (tenant_id, topology_id, device_id)`|影响重复数据；需逐组业务确认|
|UK-021|`ast_network_topology_device_relation`|`UNIQUE KEY uk_topology_device_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-022|`ast_product`|`UNIQUE KEY uk_product_code (tenant_id, product_code)`|影响重复数据；需逐组业务确认|
|UK-023|`ast_product`|`UNIQUE KEY uk_product_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-024|`ast_product_release`|`UNIQUE KEY uk_product_release ( tenant_id, product_id, release_version, release_type )`|影响重复数据；需逐组业务确认|
|UK-025|`ast_product_release`|`UNIQUE KEY uk_product_release_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-026|`com_contract`|`UNIQUE KEY uk_contract_business ( tenant_id, company_code, contract_no )`|影响重复数据；需逐组业务确认|
|UK-027|`com_contract`|`UNIQUE KEY uk_contract_master_source ( tenant_id, master_source_system, master_source_record_key )`|影响重复数据；需逐组业务确认|
|UK-028|`com_contract`|`UNIQUE KEY uk_contract_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-029|`com_contract_receivable`|`UNIQUE KEY uk_contract_receivable_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-030|`com_contract_receivable`|`UNIQUE KEY uk_contract_receivable_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-031|`com_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config ( tenant_id, config_source, source_config_key )`|影响重复数据；需逐组业务确认|
|UK-032|`com_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-033|`com_crm_execution_order`|`UNIQUE KEY uk_crm_execution ( tenant_id, source_system, execution_no )`|影响重复数据；需逐组业务确认|
|UK-034|`com_crm_execution_order`|`UNIQUE KEY uk_crm_execution_order_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-035|`com_delivery_scope`|`UNIQUE KEY uk_project_order_line_scope_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-036|`com_delivery_scope`|`UNIQUE KEY uk_scope_current ( tenant_id, project_id, current_order_line_id )`|影响重复数据；需逐组业务确认|
|UK-037|`com_execution_order_merge_batch`|`UNIQUE KEY uk_execution_merge_batch ( tenant_id, source_system, source_merge_key )`|影响重复数据；需逐组业务确认|
|UK-038|`com_execution_order_merge_batch`|`UNIQUE KEY uk_execution_merge_batch_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-039|`com_execution_order_merge_member`|`UNIQUE KEY uk_execution_merge_member_source ( tenant_id, merge_batch_id, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-040|`com_execution_order_merge_member`|`UNIQUE KEY uk_execution_merge_member_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-041|`com_order_change_relation`|`UNIQUE KEY uk_order_change ( tenant_id, source_order_id, target_order_id, relation_type )`|影响重复数据；需逐组业务确认|
|UK-042|`com_order_change_relation`|`UNIQUE KEY uk_order_change_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-043|`com_order_contract_relation`|`UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id)`|影响重复数据；需逐组业务确认|
|UK-044|`com_order_contract_relation`|`UNIQUE KEY uk_order_contract_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-045|`com_order_execution_relation`|`UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)`|影响重复数据；需逐组业务确认|
|UK-046|`com_order_execution_relation`|`UNIQUE KEY uk_order_execution_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-047|`com_order_execution_relation`|`UNIQUE KEY uk_order_primary_execution (tenant_id, primary_order_id)`|影响重复数据；需逐组业务确认|
|UK-048|`com_order_line_execution_relation`|`UNIQUE KEY uk_order_line_execution (tenant_id, order_line_id, execution_id)`|影响重复数据；需逐组业务确认|
|UK-049|`com_order_line_execution_relation`|`UNIQUE KEY uk_order_line_execution_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-050|`com_project_contract_relation`|`UNIQUE KEY uk_project_contract ( tenant_id, project_id, contract_id, relation_role )`|影响重复数据；需逐组业务确认|
|UK-051|`com_project_contract_relation`|`UNIQUE KEY uk_project_contract_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-052|`com_sales_order`|`UNIQUE KEY uk_sales_order_business ( tenant_id, source_system, company_code, order_type, order_no )`|影响重复数据；需逐组业务确认|
|UK-053|`com_sales_order`|`UNIQUE KEY uk_sales_order_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-054|`com_sales_order_line`|`UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no)`|影响重复数据；需逐组业务确认|
|UK-055|`com_sales_order_line`|`UNIQUE KEY uk_sales_order_line_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-056|`com_shipment_contract_reference`|`UNIQUE KEY uk_shipment_contract_ref_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-057|`com_shipment_contract_reference`|`UNIQUE KEY uk_shipment_contract_ref_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-058|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_no ( tenant_id, source_system, package_no )`|影响重复数据；需逐组业务确认|
|UK-059|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_source ( tenant_id, source_system, source_record_key )`|影响重复数据；需逐组业务确认|
|UK-060|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-061|`cus_customer`|`UNIQUE KEY uk_customer_code (tenant_id, customer_code)`|影响重复数据；需逐组业务确认|
|UK-062|`cus_customer`|`UNIQUE KEY uk_customer_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-063|`cus_customer_contact`|`UNIQUE KEY uk_customer_contact_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-064|`cus_customer_contact`|`UNIQUE KEY uk_customer_primary_contact (tenant_id, primary_customer_id)`|影响重复数据；需逐组业务确认|
|UK-065|`kno_device_technical_advisory_match`|`UNIQUE KEY uk_device_advisory (tenant_id, advisory_id, device_id)`|影响重复数据；需逐组业务确认|
|UK-066|`kno_device_technical_advisory_match`|`UNIQUE KEY uk_device_advisory_match_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-067|`kno_technical_advisory`|`UNIQUE KEY uk_technical_advisory_no (tenant_id, advisory_no)`|影响重复数据；需逐组业务确认|
|UK-068|`kno_technical_advisory`|`UNIQUE KEY uk_technical_advisory_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-069|`kno_technical_advisory_product_relation`|`UNIQUE KEY uk_advisory_product (tenant_id, advisory_id, product_id)`|影响重复数据；需逐组业务确认|
|UK-070|`kno_technical_advisory_product_relation`|`UNIQUE KEY uk_technical_advisory_product_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-071|`kno_technical_advisory_read_record`|`UNIQUE KEY uk_advisory_reader (tenant_id, advisory_id, reader_id)`|影响重复数据；需逐组业务确认|
|UK-072|`kno_technical_advisory_read_record`|`UNIQUE KEY uk_technical_advisory_read_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-073|`plt_business_document`|`UNIQUE KEY uk_business_document_code (tenant_id, document_code)`|影响重复数据；需逐组业务确认|
|UK-074|`plt_business_document`|`UNIQUE KEY uk_business_document_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-075|`plt_document_version`|`UNIQUE KEY uk_document_version (tenant_id, document_id, version_no)`|影响重复数据；需逐组业务确认|
|UK-076|`plt_document_version`|`UNIQUE KEY uk_document_version_owner (tenant_id, document_id, id)`|影响重复数据；需逐组业务确认|
|UK-077|`plt_document_version`|`UNIQUE KEY uk_document_version_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-078|`plt_external_key_mapping`|`UNIQUE KEY uk_external_key_map_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-079|`plt_external_key_mapping`|`UNIQUE KEY uk_external_key_source_target ( tenant_id, source_system, source_table, source_pk, target_table, target_id )`|影响重复数据；需逐组业务确认|
|UK-080|`plt_migration_issue`|`UNIQUE KEY uk_migration_issue_source ( tenant_id, batch_id, source_table, source_pk, issue_type )`|影响重复数据；需逐组业务确认|
|UK-081|`plt_migration_issue`|`UNIQUE KEY uk_migration_issue_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-082|`plt_migration_source_record`|`UNIQUE KEY uk_migration_source_record ( tenant_id, batch_id, source_system, source_table, source_pk )`|影响重复数据；需逐组业务确认|
|UK-083|`plt_migration_source_record`|`UNIQUE KEY uk_migration_source_record_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-084|`plt_sync_batch`|`UNIQUE KEY uk_sync_batch_no (tenant_id, batch_no)`|影响重复数据；需逐组业务确认|
|UK-085|`plt_sync_batch`|`UNIQUE KEY uk_sync_batch_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-086|`proj_project`|`UNIQUE KEY uk_project_code (tenant_id, project_type, project_code)`|影响重复数据；需逐组业务确认|
|UK-087|`proj_project`|`UNIQUE KEY uk_project_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-088|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_company_department_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-089|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_company_department_role ( tenant_id, project_id, company_code, department_code, relation_role, effective_from )`|影响重复数据；需逐组业务确认|
|UK-090|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_primary_company_department ( tenant_id, primary_project_id, relation_role )`|影响重复数据；需逐组业务确认|
|UK-091|`proj_project_member_assignment`|`UNIQUE KEY uk_project_member_role ( tenant_id, project_id, user_id, member_role, effective_from )`|影响重复数据；需逐组业务确认|
|UK-092|`proj_project_member_assignment`|`UNIQUE KEY uk_project_member_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-093|`proj_project_party`|`UNIQUE KEY uk_project_party_source ( tenant_id, source_system, source_table, source_record_key, party_role )`|影响重复数据；需逐组业务确认|
|UK-094|`proj_project_party`|`UNIQUE KEY uk_project_party_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-095|`proj_project_portfolio`|`UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code)`|影响重复数据；需逐组业务确认|
|UK-096|`proj_project_portfolio`|`UNIQUE KEY uk_portfolio_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-097|`proj_project_portfolio_member`|`UNIQUE KEY uk_portfolio_project ( tenant_id, portfolio_id, project_id, member_source )`|影响重复数据；需逐组业务确认|
|UK-098|`proj_project_portfolio_member`|`UNIQUE KEY uk_portfolio_project_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-099|`proj_project_relation`|`UNIQUE KEY uk_project_relation ( tenant_id, source_project_id, target_project_id, relation_type )`|影响重复数据；需逐组业务确认|
|UK-100|`proj_project_relation`|`UNIQUE KEY uk_project_relation_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-101|`srv_service_incident`|`UNIQUE KEY uk_service_incident_no (tenant_id, incident_no)`|影响重复数据；需逐组业务确认|
|UK-102|`srv_service_incident`|`UNIQUE KEY uk_service_incident_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|
|UK-103|`srv_service_incident_device_relation`|`UNIQUE KEY uk_incident_device (tenant_id, incident_id, device_id)`|影响重复数据；需逐组业务确认|
|UK-104|`srv_service_incident_device_relation`|`UNIQUE KEY uk_incident_device_rel_tenant_row (tenant_id, id)`|影响重复数据；需逐组业务确认|

## 8. CHECK规则完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|CK-001|`acc_deliverable_template`|`CONSTRAINT chk_deliverable_template_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-002|`acc_deliverable_template`|`CONSTRAINT chk_deliverable_template_required CHECK (required_flag IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-003|`acc_project_deliverable`|`CONSTRAINT chk_project_deliverable_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-004|`ast_device_configuration`|`CONSTRAINT chk_device_configuration_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-005|`ast_device_configuration`|`CONSTRAINT chk_device_configuration_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-006|`ast_device_configuration_feature`|`CONSTRAINT chk_configuration_feature_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-007|`ast_device_configuration_service`|`CONSTRAINT chk_configuration_service_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-008|`ast_device_project_assignment`|`CONSTRAINT chk_device_assignment_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-009|`ast_device_project_assignment`|`CONSTRAINT chk_device_assignment_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-010|`ast_device_relation`|`CONSTRAINT chk_device_relation_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-011|`ast_device_relation`|`CONSTRAINT chk_device_relation_self CHECK (source_device_id <> target_device_id)`|影响异常历史数据；需逐组业务确认|
|CK-012|`ast_device_shipment_event`|`CONSTRAINT chk_shipment_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-013|`ast_device_sn`|`CONSTRAINT chk_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-014|`ast_device_sn`|`CONSTRAINT chk_device_secondary_cache CHECK ( secondary_sn IS NOT NULL OR secondary_item IS NULL )`|影响异常历史数据；需逐组业务确认|
|CK-015|`ast_device_sn`|`CONSTRAINT chk_device_secondary_self CHECK ( secondary_sn IS NULL OR secondary_sn <> sn )`|影响异常历史数据；需逐组业务确认|
|CK-016|`ast_device_version`|`CONSTRAINT chk_device_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-017|`ast_device_version`|`CONSTRAINT chk_device_version_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-018|`ast_network_topology`|`CONSTRAINT chk_network_topology_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-019|`ast_network_topology`|`CONSTRAINT chk_network_topology_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-020|`ast_network_topology_device_relation`|`CONSTRAINT chk_topology_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-021|`ast_product`|`CONSTRAINT chk_product_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-022|`ast_product`|`CONSTRAINT chk_product_service CHECK (service_product_flag IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-023|`ast_product_release`|`CONSTRAINT chk_product_release_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-024|`com_contract`|`CONSTRAINT chk_contract_dates CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date)`|影响异常历史数据；需逐组业务确认|
|CK-025|`com_contract`|`CONSTRAINT chk_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-026|`com_contract_receivable`|`CONSTRAINT chk_contract_receivable_dates CHECK ( source_effective_to IS NULL OR source_effective_from IS NULL OR source_effective_to >= source_effective_from )`|影响异常历史数据；需逐组业务确认|
|CK-027|`com_contract_receivable`|`CONSTRAINT chk_contract_receivable_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-028|`com_crm_execution_config`|`CONSTRAINT chk_crm_execution_config_af CHECK (is_af_evidence IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-029|`com_crm_execution_config`|`CONSTRAINT chk_crm_execution_config_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-030|`com_crm_execution_order`|`CONSTRAINT chk_crm_execution_af CHECK (af_evidence_status IN ('CONFIRMED', 'UNKNOWN'))`|影响异常历史数据；需逐组业务确认|
|CK-031|`com_crm_execution_order`|`CONSTRAINT chk_crm_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-032|`com_delivery_scope`|`CONSTRAINT chk_scope_active CHECK ( scope_status <> 'ACTIVE' OR allocated_qty IS NOT NULL )`|影响异常历史数据；需逐组业务确认|
|CK-033|`com_delivery_scope`|`CONSTRAINT chk_scope_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-034|`com_delivery_scope`|`CONSTRAINT chk_scope_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-035|`com_execution_order_merge_batch`|`CONSTRAINT chk_execution_merge_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-036|`com_execution_order_merge_member`|`CONSTRAINT chk_execution_merge_member_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-037|`com_execution_order_merge_member`|`CONSTRAINT chk_execution_merge_member_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-038|`com_order_change_relation`|`CONSTRAINT chk_order_change_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-039|`com_order_change_relation`|`CONSTRAINT chk_order_change_self CHECK (source_order_id <> target_order_id)`|影响异常历史数据；需逐组业务确认|
|CK-040|`com_order_contract_relation`|`CONSTRAINT chk_order_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-041|`com_order_execution_relation`|`CONSTRAINT chk_order_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-042|`com_order_execution_relation`|`CONSTRAINT chk_order_execution_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-043|`com_order_line_execution_relation`|`CONSTRAINT chk_order_line_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-044|`com_project_contract_relation`|`CONSTRAINT chk_project_contract_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-045|`com_project_contract_relation`|`CONSTRAINT chk_project_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-046|`com_sales_order`|`CONSTRAINT chk_sales_order_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-047|`com_sales_order_line`|`CONSTRAINT chk_sales_order_line_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-048|`com_shipment_contract_reference`|`CONSTRAINT chk_shipment_contract_ref_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-049|`com_shipment_package`|`CONSTRAINT chk_shipment_package_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-050|`com_shipment_package`|`CONSTRAINT chk_shipment_package_warranty_dates CHECK ( warranty_end_time IS NULL OR warranty_start_time IS NULL OR warranty_end_time >= warranty_start_time )`|影响异常历史数据；需逐组业务确认|
|CK-051|`cus_customer`|`CONSTRAINT chk_customer_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-052|`cus_customer_contact`|`CONSTRAINT chk_customer_contact_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-053|`cus_customer_contact`|`CONSTRAINT chk_customer_contact_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-054|`kno_device_technical_advisory_match`|`CONSTRAINT chk_device_advisory_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-055|`kno_technical_advisory`|`CONSTRAINT chk_technical_advisory_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-056|`kno_technical_advisory`|`CONSTRAINT chk_technical_advisory_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-057|`kno_technical_advisory_product_relation`|`CONSTRAINT chk_advisory_product_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-058|`kno_technical_advisory_read_record`|`CONSTRAINT chk_advisory_read_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-059|`plt_business_document`|`CONSTRAINT chk_business_document_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-060|`plt_document_version`|`CONSTRAINT chk_document_version_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-061|`plt_migration_issue`|`CONSTRAINT chk_migration_issue_resolution CHECK ( resolution_status <> 'RESOLVED' OR (resolver IS NOT NULL AND resolved_time IS NOT NULL) )`|影响异常历史数据；需逐组业务确认|
|CK-062|`plt_migration_source_record`|`CONSTRAINT chk_migration_source_target_count CHECK (mapped_target_count >= 0)`|影响异常历史数据；需逐组业务确认|
|CK-063|`plt_sync_batch`|`CONSTRAINT chk_sync_batch_count CHECK (success_count + failure_count <= read_count)`|影响异常历史数据；需逐组业务确认|
|CK-064|`plt_sync_batch`|`CONSTRAINT chk_sync_batch_time CHECK (finished_time IS NULL OR finished_time >= started_time)`|影响异常历史数据；需逐组业务确认|
|CK-065|`proj_project`|`CONSTRAINT chk_project_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-066|`proj_project`|`CONSTRAINT chk_project_depth CHECK (tree_depth >= 0)`|影响异常历史数据；需逐组业务确认|
|CK-067|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-068|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-069|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_pair CHECK (department_id IS NULL OR department_code IS NOT NULL)`|影响异常历史数据；需逐组业务确认|
|CK-070|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-071|`proj_project_member_assignment`|`CONSTRAINT chk_project_member_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-072|`proj_project_member_assignment`|`CONSTRAINT chk_project_member_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-073|`proj_project_party`|`CONSTRAINT chk_project_party_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；需逐组业务确认|
|CK-074|`proj_project_party`|`CONSTRAINT chk_project_party_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-075|`proj_project_portfolio`|`CONSTRAINT chk_portfolio_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-076|`proj_project_portfolio_member`|`CONSTRAINT chk_portfolio_project_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-077|`proj_project_relation`|`CONSTRAINT chk_project_relation_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-078|`proj_project_relation`|`CONSTRAINT chk_project_relation_self CHECK (source_project_id <> target_project_id)`|影响异常历史数据；需逐组业务确认|
|CK-079|`srv_service_incident`|`CONSTRAINT chk_service_incident_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|
|CK-080|`srv_service_incident`|`CONSTRAINT chk_service_incident_times CHECK ( restored_time IS NULL OR occurred_time IS NULL OR restored_time >= occurred_time )`|影响异常历史数据；需逐组业务确认|
|CK-081|`srv_service_incident_device_relation`|`CONSTRAINT chk_incident_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；需逐组业务确认|

## 9. 裁决边界

- `ACCEPT_CURRENT`表示接受当前DDL作为目标数据模型，不代表历史数据天然满足约束。
- 历史数据违反已批准约束时进入迁移问题池并保留来源证据，不得静默删除、改写或临时放宽模型掩盖问题。
- 唯一键和CHECK规则将在业务确认后回写逐项决策登记；纯性能索引仍需在P3-E06压测中验证。
- 本清单不授权连接或修改旧库，不授权执行生产迁移。
