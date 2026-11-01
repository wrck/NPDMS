-- Read-only, immediately after fresh initialization and before business use.

SELECT DATABASE() AS target_schema,VERSION() AS mysql_version;

SELECT version,type,success FROM flyway_schema_history ORDER BY installed_rank;

SELECT 'tables_excluding_flyway' AS check_name,527 AS expected,COUNT(*) AS actual FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history';

SELECT 'triggers' AS check_name,240 AS expected,COUNT(*) AS actual FROM information_schema.triggers WHERE trigger_schema=DATABASE();

SELECT 'invalid_charset_or_collation' AS violation,table_name,column_name,character_set_name,collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND character_set_name IS NOT NULL AND (character_set_name<>'utf8mb4' OR collation_name NOT IN ('utf8mb4_unicode_ci','utf8mb4_bin','utf8mb4_0900_bin'));

SELECT 'dictionary_without_type' AS violation,d.id FROM system_dict_data d LEFT JOIN system_dict_type t ON t.type=d.dict_type AND t.deleted=0 WHERE d.deleted=0 AND t.id IS NULL;

SELECT 'role_menu_without_parent' AS violation,rm.id FROM system_role_menu rm LEFT JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id AND r.deleted=0 LEFT JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 WHERE rm.deleted=0 AND (r.id IS NULL OR m.id IS NULL);

SELECT 'bootstrap_admin_disabled' AS check_name,COUNT(*) AS actual FROM system_users WHERE id=1 AND username='admin' AND status=1 AND password='!BOOTSTRAP_REQUIRED!';

SELECT 'ACT_FK_BYTEARR_DEPL' AS violation,COUNT(*) AS orphan_count FROM `ACT_GE_BYTEARRAY` c WHERE c.`DEPLOYMENT_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_DEPLOYMENT` p WHERE c.`DEPLOYMENT_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_MEMB_GROUP' AS violation,COUNT(*) AS orphan_count FROM `ACT_ID_MEMBERSHIP` c WHERE c.`GROUP_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_ID_GROUP` p WHERE c.`GROUP_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_MEMB_USER' AS violation,COUNT(*) AS orphan_count FROM `ACT_ID_MEMBERSHIP` c WHERE c.`USER_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_ID_USER` p WHERE c.`USER_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_PRIV_MAPPING' AS violation,COUNT(*) AS orphan_count FROM `ACT_ID_PRIV_MAPPING` c WHERE c.`PRIV_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_ID_PRIV` p WHERE c.`PRIV_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_INFO_JSON_BA' AS violation,COUNT(*) AS orphan_count FROM `ACT_PROCDEF_INFO` c WHERE c.`INFO_JSON_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`INFO_JSON_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_INFO_PROCDEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_PROCDEF_INFO` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_MODEL_DEPLOYMENT' AS violation,COUNT(*) AS orphan_count FROM `ACT_RE_MODEL` c WHERE c.`DEPLOYMENT_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_DEPLOYMENT` p WHERE c.`DEPLOYMENT_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_MODEL_SOURCE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RE_MODEL` c WHERE c.`EDITOR_SOURCE_VALUE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EDITOR_SOURCE_VALUE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_MODEL_SOURCE_EXTRA' AS violation,COUNT(*) AS orphan_count FROM `ACT_RE_MODEL` c WHERE c.`EDITOR_SOURCE_EXTRA_VALUE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EDITOR_SOURCE_EXTRA_VALUE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_DEADLETTER_JOB_CUSTOM_VALUES' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_DEADLETTER_JOB` c WHERE c.`CUSTOM_VALUES_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`CUSTOM_VALUES_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_DEADLETTER_JOB_EXCEPTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_DEADLETTER_JOB` c WHERE c.`EXCEPTION_STACK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EXCEPTION_STACK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_DEADLETTER_JOB_EXECUTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_DEADLETTER_JOB` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_DEADLETTER_JOB` c WHERE c.`PROCESS_INSTANCE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROCESS_INSTANCE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_DEADLETTER_JOB_PROC_DEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_DEADLETTER_JOB` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EVENT_EXEC' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EVENT_SUBSCR` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXE_PARENT' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXECUTION` c WHERE c.`PARENT_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PARENT_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXE_PROCDEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXECUTION` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXE_PROCINST' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXECUTION` c WHERE c.`PROC_INST_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROC_INST_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXE_SUPER' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXECUTION` c WHERE c.`SUPER_EXEC_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`SUPER_EXEC_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXTERNAL_JOB_CUSTOM_VALUES' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXTERNAL_JOB` c WHERE c.`CUSTOM_VALUES_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`CUSTOM_VALUES_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_EXTERNAL_JOB_EXCEPTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_EXTERNAL_JOB` c WHERE c.`EXCEPTION_STACK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EXCEPTION_STACK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_ATHRZ_PROCEDEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_IDENTITYLINK` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_IDL_PROCINST' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_IDENTITYLINK` c WHERE c.`PROC_INST_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROC_INST_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TSKASS_TASK' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_IDENTITYLINK` c WHERE c.`TASK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_TASK` p WHERE c.`TASK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_JOB_CUSTOM_VALUES' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_JOB` c WHERE c.`CUSTOM_VALUES_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`CUSTOM_VALUES_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_JOB_EXCEPTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_JOB` c WHERE c.`EXCEPTION_STACK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EXCEPTION_STACK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_JOB_EXECUTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_JOB` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_JOB_PROCESS_INSTANCE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_JOB` c WHERE c.`PROCESS_INSTANCE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROCESS_INSTANCE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_JOB_PROC_DEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_JOB` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_SUSPENDED_JOB_CUSTOM_VALUES' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_SUSPENDED_JOB` c WHERE c.`CUSTOM_VALUES_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`CUSTOM_VALUES_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_SUSPENDED_JOB_EXCEPTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_SUSPENDED_JOB` c WHERE c.`EXCEPTION_STACK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EXCEPTION_STACK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_SUSPENDED_JOB_EXECUTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_SUSPENDED_JOB` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_SUSPENDED_JOB` c WHERE c.`PROCESS_INSTANCE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROCESS_INSTANCE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_SUSPENDED_JOB_PROC_DEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_SUSPENDED_JOB` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TASK_EXE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TASK` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TASK_PROCDEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TASK` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TASK_PROCINST' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TASK` c WHERE c.`PROC_INST_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROC_INST_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TIMER_JOB_CUSTOM_VALUES' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TIMER_JOB` c WHERE c.`CUSTOM_VALUES_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`CUSTOM_VALUES_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TIMER_JOB_EXCEPTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TIMER_JOB` c WHERE c.`EXCEPTION_STACK_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`EXCEPTION_STACK_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TIMER_JOB_EXECUTION' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TIMER_JOB` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TIMER_JOB_PROCESS_INSTANCE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TIMER_JOB` c WHERE c.`PROCESS_INSTANCE_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROCESS_INSTANCE_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_TIMER_JOB_PROC_DEF' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_TIMER_JOB` c WHERE c.`PROC_DEF_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RE_PROCDEF` p WHERE c.`PROC_DEF_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_VAR_BYTEARRAY' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_VARIABLE` c WHERE c.`BYTEARRAY_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_GE_BYTEARRAY` p WHERE c.`BYTEARRAY_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_VAR_EXE' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_VARIABLE` c WHERE c.`EXECUTION_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`EXECUTION_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'ACT_FK_VAR_PROCINST' AS violation,COUNT(*) AS orphan_count FROM `ACT_RU_VARIABLE` c WHERE c.`PROC_INST_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ACT_RU_EXECUTION` p WHERE c.`PROC_INST_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'FLW_FK_EVENT_RSRC_DPL' AS violation,COUNT(*) AS orphan_count FROM `FLW_EVENT_RESOURCE` c WHERE c.`DEPLOYMENT_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `FLW_EVENT_DEPLOYMENT` p WHERE c.`DEPLOYMENT_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'FLW_FK_BATCH_PART_PARENT' AS violation,COUNT(*) AS orphan_count FROM `FLW_RU_BATCH_PART` c WHERE c.`BATCH_ID_` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `FLW_RU_BATCH` p WHERE c.`BATCH_ID_`=p.`ID_`) HAVING COUNT(*)>0;

SELECT 'QRTZ_BLOB_TRIGGERS_ibfk_1' AS violation,COUNT(*) AS orphan_count FROM `QRTZ_BLOB_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` p WHERE c.`SCHED_NAME`=p.`SCHED_NAME` AND c.`TRIGGER_NAME`=p.`TRIGGER_NAME` AND c.`TRIGGER_GROUP`=p.`TRIGGER_GROUP`) HAVING COUNT(*)>0;

SELECT 'QRTZ_CRON_TRIGGERS_ibfk_1' AS violation,COUNT(*) AS orphan_count FROM `QRTZ_CRON_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` p WHERE c.`SCHED_NAME`=p.`SCHED_NAME` AND c.`TRIGGER_NAME`=p.`TRIGGER_NAME` AND c.`TRIGGER_GROUP`=p.`TRIGGER_GROUP`) HAVING COUNT(*)>0;

SELECT 'QRTZ_SIMPLE_TRIGGERS_ibfk_1' AS violation,COUNT(*) AS orphan_count FROM `QRTZ_SIMPLE_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` p WHERE c.`SCHED_NAME`=p.`SCHED_NAME` AND c.`TRIGGER_NAME`=p.`TRIGGER_NAME` AND c.`TRIGGER_GROUP`=p.`TRIGGER_GROUP`) HAVING COUNT(*)>0;

SELECT 'QRTZ_SIMPROP_TRIGGERS_ibfk_1' AS violation,COUNT(*) AS orphan_count FROM `QRTZ_SIMPROP_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`TRIGGER_NAME` IS NOT NULL AND c.`TRIGGER_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_TRIGGERS` p WHERE c.`SCHED_NAME`=p.`SCHED_NAME` AND c.`TRIGGER_NAME`=p.`TRIGGER_NAME` AND c.`TRIGGER_GROUP`=p.`TRIGGER_GROUP`) HAVING COUNT(*)>0;

SELECT 'QRTZ_TRIGGERS_ibfk_1' AS violation,COUNT(*) AS orphan_count FROM `QRTZ_TRIGGERS` c WHERE c.`SCHED_NAME` IS NOT NULL AND c.`JOB_NAME` IS NOT NULL AND c.`JOB_GROUP` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `QRTZ_JOB_DETAILS` p WHERE c.`SCHED_NAME`=p.`SCHED_NAME` AND c.`JOB_NAME`=p.`JOB_NAME` AND c.`JOB_GROUP`=p.`JOB_GROUP`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_ancestor_projection_operation_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_ancestor_projection_operation` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_assembly_child' AS violation,COUNT(*) AS orphan_count FROM `ast_device_assembly` c WHERE c.`tenant_id` IS NOT NULL AND c.`child_device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`child_device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_assembly_parent' AS violation,COUNT(*) AS orphan_count FROM `ast_device_assembly` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`parent_device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_assignment_reconciliation_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_assignment_reconciliation` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_product_type_mapping' AS violation,COUNT(*) AS orphan_count FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_mapping_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type_source_mapping` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_mapping_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_product_type_mapping_target' AS violation,COUNT(*) AS orphan_count FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_mapping_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type_source_mapping` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_mapping_id`=p.`id` AND c.`product_type_id`=p.`product_type_id`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_product_type_target' AS violation,COUNT(*) AS orphan_count FROM `ast_device_current_product_type` c WHERE c.`tenant_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND c.`product_type_code` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`product_type_id`=p.`id` AND c.`product_type_code`=p.`type_code`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_customer_relationship_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_customer_relationship` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_84' AS violation,COUNT(*) AS orphan_count FROM `ast_device_cutover_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_cutover_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device_cutover_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_cutover_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_download_grant_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_download_grant` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_factory_info_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_factory_info` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_factory_version_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_factory_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_location_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_network_version_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_network_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_network_event_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_network_version_event` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_project_ancestor_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_project_ancestor` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_project_relationship_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_project_relationship` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_warranty_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_warranty` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_device_warranty_record_device' AS violation,COUNT(*) AS orphan_count FROM `ast_device_warranty_record` c WHERE c.`tenant_id` IS NOT NULL AND c.`device_sn` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`device_sn`=p.`sn`) HAVING COUNT(*)>0;

SELECT 'fk_ast_product_type_mapping_target' AS violation,COUNT(*) AS orphan_count FROM `ast_product_type_source_mapping` c WHERE c.`tenant_id` IS NOT NULL AND c.`product_type_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `ast_product_type` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`product_type_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_contract_receivable_contract' AS violation,COUNT(*) AS orphan_count FROM `com_contract_receivable` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`contract_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_delivery_scope_detail_scope' AS violation,COUNT(*) AS orphan_count FROM `com_delivery_scope_detail` c WHERE c.`tenant_id` IS NOT NULL AND c.`delivery_scope_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_delivery_scope` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`delivery_scope_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_order_contract_contract' AS violation,COUNT(*) AS orphan_count FROM `com_order_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`contract_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_order_contract_order' AS violation,COUNT(*) AS orphan_count FROM `com_order_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`order_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_sales_order` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`order_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_project_contract_contract' AS violation,COUNT(*) AS orphan_count FROM `com_project_contract_relation` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`contract_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sales_order_line_order' AS violation,COUNT(*) AS orphan_count FROM `com_sales_order_line` c WHERE c.`tenant_id` IS NOT NULL AND c.`order_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_sales_order` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`order_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_shipment_contract_ref_contract' AS violation,COUNT(*) AS orphan_count FROM `com_shipment_contract_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_contract` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`contract_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_shipment_package_contract_ref' AS violation,COUNT(*) AS orphan_count FROM `com_shipment_package` c WHERE c.`tenant_id` IS NOT NULL AND c.`shipment_contract_ref_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `com_shipment_contract_reference` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`shipment_contract_ref_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cus_inspection_history' AS violation,COUNT(*) AS orphan_count FROM `cus_inspection_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`history_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cus_inspection_history` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`history_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_plan' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_instance` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_task' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_instance` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_node_instance' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_node` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`approval_instance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_notification_instance' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_notification` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`approval_instance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_notification_node' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_notification` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` p WHERE c.`approval_node_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_reassignment_instance' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_reassignment` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`approval_instance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_reassignment_node' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_reassignment` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` p WHERE c.`approval_node_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_71' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`approval_instance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_72' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_73' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_result_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_review_instance' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_review_item` c WHERE c.`approval_instance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`approval_instance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_approval_review_node' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_review_item` c WHERE c.`approval_node_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_node` p WHERE c.`approval_node_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_workflow_approval' AS violation,COUNT(*) AS orphan_count FROM `cut_approval_workflow` c WHERE c.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_approval_instance` p WHERE c.`id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_13' AS violation,COUNT(*) AS orphan_count FROM `cut_assessment_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`assessment_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_14' AS violation,COUNT(*) AS orphan_count FROM `cut_assessment_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_device_scope_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task_device_scope` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_device_scope_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_10' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_binding_condition` c WHERE c.`tenant_id` IS NOT NULL AND c.`binding_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_binding_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`binding_rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_11' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_binding_condition` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_dimension_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_dimension` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_dimension_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_24' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_device_context` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_25' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_device_context` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`assessment_device_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_12' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_mapping` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_definition_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_29' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_30' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_definition_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_31' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_query` c WHERE c.`tenant_id` IS NOT NULL AND c.`loaded_checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`loaded_checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_32' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_query_parameter` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`external_query_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_33' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_external_query_parameter` c WHERE c.`tenant_id` IS NOT NULL AND c.`mapping_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_mapping` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`mapping_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_6' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_definition_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_7' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`custom_checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`custom_checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_8' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_field_definition` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_field_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`parent_field_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_9' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_field_option` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_19' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_input_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_20' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_input_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_dimension_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_dimension` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_dimension_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_21' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_22' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_definition_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_23' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_match_candidate` c WHERE c.`tenant_id` IS NOT NULL AND c.`binding_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_binding_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`binding_rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_26' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_27' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`custom_result_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_custom_row` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`custom_result_row_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_28' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_result_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_34' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_result_query_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_35' AS violation,COUNT(*) AS orphan_count FROM `cut_checklist_result_query_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`external_query_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_74' AS violation,COUNT(*) AS orphan_count FROM `cut_closure_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`closure_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_2' AS violation,COUNT(*) AS orphan_count FROM `cut_configuration_dictionary_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_1' AS violation,COUNT(*) AS orphan_count FROM `cut_configuration_dimension` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_3' AS violation,COUNT(*) AS orphan_count FROM `cut_configuration_plan_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_4' AS violation,COUNT(*) AS orphan_count FROM `cut_configuration_plan_section_scope` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_section_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_configuration_plan_section` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_section_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_5' AS violation,COUNT(*) AS orphan_count FROM `cut_configuration_validation_issue` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_checklist_task' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_15' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist` c WHERE c.`tenant_id` IS NOT NULL AND c.`configuration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_configuration_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`configuration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_checklist_item_root' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist_item` c WHERE c.`checklist_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist` p WHERE c.`checklist_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_checklist_result_item' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist_item_result` c WHERE c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_16' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_17' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`base_checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`base_checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_18' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_checklist_item_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`collection_log_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_collection_log` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`collection_log_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_closure_task' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_closure` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_closure_attachment_root' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_closure_attachment` c WHERE c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` p WHERE c.`closure_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_collection_closure' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_collection_evidence` c WHERE c.`closure_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure` p WHERE c.`closure_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_collection_manual_attachment' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_collection_evidence` c WHERE c.`manual_attachment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_closure_attachment` p WHERE c.`manual_attachment_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_collection_task' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_collection_evidence` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_61' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_support_arrangement` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_support_contact_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_support_contact_change` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`current_support_contact_change_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_support_plan' AS violation,COUNT(*) AS orphan_count FROM `cut_cutover_support_arrangement` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_63' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_64' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`assessment_device_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_65' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_plan_task' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_revision` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_66' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_67' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_68' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_risk_mitigation` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_62' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_schedule` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_69' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_support_contact_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`support_arrangement_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_support_arrangement` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`support_arrangement_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_70' AS violation,COUNT(*) AS orphan_count FROM `cut_plan_support_contact_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`previous_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_support_contact_change` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`previous_change_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_43' AS violation,COUNT(*) AS orphan_count FROM `cut_result_bulletin_summary` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_37' AS violation,COUNT(*) AS orphan_count FROM `cut_result_business_summary` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_41' AS violation,COUNT(*) AS orphan_count FROM `cut_result_check_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_39' AS violation,COUNT(*) AS orphan_count FROM `cut_result_continuity_requirement` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_58' AS violation,COUNT(*) AS orphan_count FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_59' AS violation,COUNT(*) AS orphan_count FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_60' AS violation,COUNT(*) AS orphan_count FROM `cut_result_custom_row` c WHERE c.`tenant_id` IS NOT NULL AND c.`parent_custom_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_custom_row` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`parent_custom_row_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_56' AS violation,COUNT(*) AS orphan_count FROM `cut_result_custom_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_57' AS violation,COUNT(*) AS orphan_count FROM `cut_result_custom_value` c WHERE c.`tenant_id` IS NOT NULL AND c.`field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_36' AS violation,COUNT(*) AS orphan_count FROM `cut_result_cutover_background` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_44' AS violation,COUNT(*) AS orphan_count FROM `cut_result_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_45' AS violation,COUNT(*) AS orphan_count FROM `cut_result_device_location` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_38' AS violation,COUNT(*) AS orphan_count FROM `cut_result_impact_scope` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_46' AS violation,COUNT(*) AS orphan_count FROM `cut_result_interface_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_47' AS violation,COUNT(*) AS orphan_count FROM `cut_result_interface_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_40' AS violation,COUNT(*) AS orphan_count FROM `cut_result_interruption` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_48' AS violation,COUNT(*) AS orphan_count FROM `cut_result_ip_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_49' AS violation,COUNT(*) AS orphan_count FROM `cut_result_ip_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_50' AS violation,COUNT(*) AS orphan_count FROM `cut_result_performance` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_51' AS violation,COUNT(*) AS orphan_count FROM `cut_result_performance` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_42' AS violation,COUNT(*) AS orphan_count FROM `cut_result_spare_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_52' AS violation,COUNT(*) AS orphan_count FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_53' AS violation,COUNT(*) AS orphan_count FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`table_field_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_field_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`table_field_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_54' AS violation,COUNT(*) AS orphan_count FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`external_query_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_checklist_external_query` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`external_query_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_55' AS violation,COUNT(*) AS orphan_count FROM `cut_result_technical_bulletin` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_bulletin_row_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_result_technical_bulletin` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_bulletin_row_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_spare_application_task' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_application_reference` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_spare_manual_application' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_manual_evidence` c WHERE c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` p WHERE c.`application_reference_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_spare_manual_task' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_manual_evidence` c WHERE c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_task` p WHERE c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_75' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`application_reference_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_76' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`assessment_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_assessment` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`assessment_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_77' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_need_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`checklist_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_cutover_checklist_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`checklist_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_78' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_request_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`application_reference_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_spare_status_application' AS violation,COUNT(*) AS orphan_count FROM `cut_spare_status_revision` c WHERE c.`application_reference_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_spare_application_reference` p WHERE c.`application_reference_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_step_plan' AS violation,COUNT(*) AS orphan_count FROM `cut_step` c WHERE c.`plan_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `cut_plan_revision` p WHERE c.`plan_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_evidence_revision' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_acceptance` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND c.`evidence_revision` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`evidence_id`=p.`evidence_id` AND c.`evidence_revision`=p.`revision_no`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_predecessor' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_acceptance` c WHERE c.`tenant_id` IS NOT NULL AND c.`predecessor_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`predecessor_acceptance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_difference_evidence' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND c.`evidence_revision` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`evidence_id`=p.`evidence_id` AND c.`evidence_revision`=p.`revision_no`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_difference_line' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_line_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_line` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`arrival_line_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_difference_root' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_difference` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`arrival_acceptance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_imp_arrival_line_root' AS violation,COUNT(*) AS orphan_count FROM `imp_arrival_line` c WHERE c.`tenant_id` IS NOT NULL AND c.`arrival_acceptance_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_arrival_acceptance` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`arrival_acceptance_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_79' AS violation,COUNT(*) AS orphan_count FROM `imp_cutover_readiness_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`snapshot_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_80' AS violation,COUNT(*) AS orphan_count FROM `imp_cutover_readiness_source` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`snapshot_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_81' AS violation,COUNT(*) AS orphan_count FROM `imp_cutover_readiness_source_object` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_fact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_source` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_fact_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_82' AS violation,COUNT(*) AS orphan_count FROM `imp_cutover_readiness_source_watermark` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_fact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_source` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_fact_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_cut_structured_83' AS violation,COUNT(*) AS orphan_count FROM `imp_cutover_readiness_unmet` c WHERE c.`tenant_id` IS NOT NULL AND c.`snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_cutover_readiness_snapshot` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`snapshot_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_imp_delivery_evidence_revision_root' AS violation,COUNT(*) AS orphan_count FROM `imp_delivery_evidence_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`evidence_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `imp_delivery_evidence` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`evidence_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'JOB_INST_EXEC_FK' AS violation,COUNT(*) AS orphan_count FROM `int_batch_JOB_EXECUTION` c WHERE c.`JOB_INSTANCE_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_INSTANCE` p WHERE c.`JOB_INSTANCE_ID`=p.`JOB_INSTANCE_ID`) HAVING COUNT(*)>0;

SELECT 'JOB_EXEC_CTX_FK' AS violation,COUNT(*) AS orphan_count FROM `int_batch_JOB_EXECUTION_CONTEXT` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` p WHERE c.`JOB_EXECUTION_ID`=p.`JOB_EXECUTION_ID`) HAVING COUNT(*)>0;

SELECT 'JOB_EXEC_PARAMS_FK' AS violation,COUNT(*) AS orphan_count FROM `int_batch_JOB_EXECUTION_PARAMS` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` p WHERE c.`JOB_EXECUTION_ID`=p.`JOB_EXECUTION_ID`) HAVING COUNT(*)>0;

SELECT 'JOB_EXEC_STEP_FK' AS violation,COUNT(*) AS orphan_count FROM `int_batch_STEP_EXECUTION` c WHERE c.`JOB_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_JOB_EXECUTION` p WHERE c.`JOB_EXECUTION_ID`=p.`JOB_EXECUTION_ID`) HAVING COUNT(*)>0;

SELECT 'STEP_EXEC_CTX_FK' AS violation,COUNT(*) AS orphan_count FROM `int_batch_STEP_EXECUTION_CONTEXT` c WHERE c.`STEP_EXECUTION_ID` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `int_batch_STEP_EXECUTION` p WHERE c.`STEP_EXECUTION_ID`=p.`STEP_EXECUTION_ID`) HAVING COUNT(*)>0;

SELECT 'fk_plt_dynamic_form_instance_revision' AS violation,COUNT(*) AS orphan_count FROM `plt_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_revision_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`template_revision_id`=p.`id` AND c.`template_id`=p.`template_id`) HAVING COUNT(*)>0;

SELECT 'fk_plt_dynamic_form_template_published_revision' AS violation,COUNT(*) AS orphan_count FROM `plt_dynamic_form_template` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_published_revision_id` IS NOT NULL AND c.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`current_published_revision_id`=p.`id` AND c.`id`=p.`template_id`) HAVING COUNT(*)>0;

SELECT 'fk_plt_dynamic_form_revision_source' AS violation,COUNT(*) AS orphan_count FROM `plt_dynamic_form_template_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_revision_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_revision_id`=p.`id` AND c.`template_id`=p.`template_id`) HAVING COUNT(*)>0;

SELECT 'fk_plt_dynamic_form_revision_template' AS violation,COUNT(*) AS orphan_count FROM `plt_dynamic_form_template_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_dynamic_form_template` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`template_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_extension_definition' AS violation,COUNT(*) AS orphan_count FROM `plt_entity_extension_value` c WHERE c.`definition_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_entity_extension_definition` p WHERE c.`definition_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_plt_file_access_grant_version' AS violation,COUNT(*) AS orphan_count FROM `plt_file_access_grant` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`artifact_id`=p.`artifact_id` AND c.`file_version_no`=p.`version_no`) HAVING COUNT(*)>0;

SELECT 'fk_plt_file_archive_record_version' AS violation,COUNT(*) AS orphan_count FROM `plt_file_archive_record` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`artifact_id`=p.`artifact_id` AND c.`file_version_no`=p.`version_no`) HAVING COUNT(*)>0;

SELECT 'fk_plt_file_reference_artifact' AS violation,COUNT(*) AS orphan_count FROM `plt_file_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_artifact` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`artifact_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_plt_file_reference_version' AS violation,COUNT(*) AS orphan_count FROM `plt_file_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND c.`file_version_no` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_version` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`artifact_id`=p.`artifact_id` AND c.`file_version_no`=p.`version_no`) HAVING COUNT(*)>0;

SELECT 'fk_plt_file_version_artifact' AS violation,COUNT(*) AS orphan_count FROM `plt_file_version` c WHERE c.`tenant_id` IS NOT NULL AND c.`artifact_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `plt_file_artifact` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`artifact_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_pdr_source' AS violation,COUNT(*) AS orphan_count FROM `proj_delivery_definition_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`owner_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_delivery_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`owner_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_pdr_target' AS violation,COUNT(*) AS orphan_count FROM `proj_delivery_definition_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`target_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_delivery_definition_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`target_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_project_party_project' AS violation,COUNT(*) AS orphan_count FROM `proj_project_party` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_parent' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`parent_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`parent_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_root' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`root_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`root_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_state_revision' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`state_machine_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_task_state_machine_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`state_machine_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_assignment_task' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task_assignment` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_completion_evaluation_contract' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task_completion_evaluation` c WHERE c.`tenant_id` IS NOT NULL AND c.`execution_contract_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task_execution_contract` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`execution_contract_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_completion_evaluation_task' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task_completion_evaluation` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_project_task_execution_contract_definition' AS violation,COUNT(*) AS orphan_count FROM `proj_project_task_execution_contract` c WHERE c.`tenant_id` IS NOT NULL AND c.`template_task_definition_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_template_task_definition` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`template_task_definition_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_dependency_predecessor' AS violation,COUNT(*) AS orphan_count FROM `proj_task_dependency` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`predecessor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`predecessor_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_dependency_successor' AS violation,COUNT(*) AS orphan_count FROM `proj_task_dependency` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`successor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`successor_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_state_transition_revision' AS violation,COUNT(*) AS orphan_count FROM `proj_task_state_transition` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_task_state_machine_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_tree_path_ancestor' AS violation,COUNT(*) AS orphan_count FROM `proj_task_tree_path` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`ancestor_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`ancestor_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_proj_task_tree_path_descendant' AS violation,COUNT(*) AS orphan_count FROM `proj_task_tree_path` c WHERE c.`tenant_id` IS NOT NULL AND c.`project_id` IS NOT NULL AND c.`descendant_task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `proj_project_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`project_id`=p.`project_id` AND c.`descendant_task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_current_revision' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`current_duration_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`current_duration_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_pending_change' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`pending_change_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_change` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`pending_change_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_recalculation_revision' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_recalculation_source_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_recalculation_source_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_change_base_revision' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`base_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`base_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_change_candidate_revision' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`candidate_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`candidate_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_change_plan' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan_change` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_construction_plan_revision_plan' AS violation,COUNT(*) AS orphan_count FROM `sol_construction_plan_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`plan_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_construction_plan` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`plan_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_dynamic_form_instance_item' AS violation,COUNT(*) AS orphan_count FROM `sol_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_dynamic_form_instance_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_dynamic_form_instance` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_latest_snapshot' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation` c WHERE c.`tenant_id` IS NOT NULL AND c.`latest_readiness_snapshot_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_readiness_snapshot` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`latest_readiness_snapshot_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_source' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_item_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_item_source' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_waiver_item' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_item_waiver` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_waiver_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_item_waiver` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_snapshot_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_readiness_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_source_item' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_source_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`item_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation_item` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`item_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_preparation_source_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_preparation_source_reference` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_requirement_section_preparation' AS violation,COUNT(*) AS orphan_count FROM `sol_requirement_analysis_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`preparation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_preparation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`preparation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_sol_requirement_section_source' AS violation,COUNT(*) AS orphan_count FROM `sol_requirement_analysis_section` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_section_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sol_requirement_analysis_section` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_section_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_annotation_input' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_annotation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`report_id`=p.`report_id` AND c.`result_id`=p.`result_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_attempt_task' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_attempt` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_receipt_execution' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_collection_receipt` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`execution_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_result_execution' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_detection_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`attempt_id` AND c.`device_id`=p.`device_id` AND c.`execution_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_result_snapshot' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_detection_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`rule_snapshot_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_rule_snapshot` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`attempt_id` AND c.`device_id`=p.`device_id` AND c.`rule_snapshot_id`=p.`id` AND c.`rule_revision_id`=p.`rule_revision_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_result_source_report' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_detection_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`source_report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`source_report_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_evidence_remediation' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_evidence` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`remediation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_service_issue_remediation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`remediation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_execution_attempt' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_execution_device' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`device_id`=p.`device_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_execution_precheck' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_execution` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND c.`precheck_execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`attempt_id` AND c.`device_id`=p.`device_id` AND c.`precheck_execution_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_confirmation_annotation' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_feedback_confirmation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`annotation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_inspection_export_annotation' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`annotation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_inspection_export_confirmation' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`confirmation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_feedback_confirmation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`confirmation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_inspection_export_item' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_feedback_export_item` c WHERE c.`tenant_id` IS NOT NULL AND c.`export_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_feedback_export` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`export_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_parse_execution' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_parse_part` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`execution_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_execution` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`execution_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_report_file_report' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_report_file` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`report_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_report_result_report' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_report_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`report_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_report_result_result' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_report_result` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_detection_result` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`result_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_report_attempt' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_report_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_srv_inspection_command_revision' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_command_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_feedback_device' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`device_id`=p.`device_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_feedback_task' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_feedback` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_optimization_draft' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`draft_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`draft_rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_optimization_source' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_inspection_optimization_annotation' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_optimization` c WHERE c.`tenant_id` IS NOT NULL AND c.`source_annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`source_annotation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_srv_inspection_product_type_revision' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_product_type_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_srv_inspection_revision_rule' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_revision` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_id` IS NOT NULL AND c.`rule_name_snapshot` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`rule_id`=p.`id` AND c.`rule_name_snapshot`=p.`rule_name`) HAVING COUNT(*)>0;

SELECT 'fk_srv_inspection_review_revision' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_security_review` c WHERE c.`tenant_id` IS NOT NULL AND c.`revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_selection_device' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_selection` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`device_id`=p.`device_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_selection_revision' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_rule_selection` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_task_attempt' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`id` IS NOT NULL AND c.`current_attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`id`=p.`task_id` AND c.`current_attempt_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_task_report' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task` c WHERE c.`tenant_id` IS NOT NULL AND c.`id` IS NOT NULL AND c.`current_report_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_report_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`id`=p.`task_id` AND c.`current_report_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_device_task' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task_device` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_event_task' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task_event` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_snapshot_attempt' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`attempt_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_attempt` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`attempt_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_snapshot_device' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`device_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_task_device` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`device_id`=p.`device_id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_snapshot_revision' AS violation,COUNT(*) AS orphan_count FROM `srv_inspection_task_rule_snapshot` c WHERE c.`tenant_id` IS NOT NULL AND c.`rule_revision_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_rule_revision` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`rule_revision_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_issue_annotation' AS violation,COUNT(*) AS orphan_count FROM `srv_service_issue` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`report_id` IS NOT NULL AND c.`result_id` IS NOT NULL AND c.`annotation_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_inspection_annotation` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`report_id`=p.`report_id` AND c.`result_id`=p.`result_id` AND c.`annotation_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'fk_ins_remediation_issue' AS violation,COUNT(*) AS orphan_count FROM `srv_service_issue_remediation` c WHERE c.`tenant_id` IS NOT NULL AND c.`task_id` IS NOT NULL AND c.`issue_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `srv_service_issue` p WHERE c.`tenant_id`=p.`tenant_id` AND c.`task_id`=p.`task_id` AND c.`issue_id`=p.`id`) HAVING COUNT(*)>0;

SELECT 'ACT_GE_PROPERTY' AS seed_table,5 AS expected,COUNT(*) AS actual FROM `ACT_GE_PROPERTY`;

SELECT 'ACT_ID_PROPERTY' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `ACT_ID_PROPERTY`;

SELECT 'acc_satisfaction_questionnaire_template' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `acc_satisfaction_questionnaire_template`;

SELECT 'acc_satisfaction_questionnaire_template_revision' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `acc_satisfaction_questionnaire_template_revision`;

SELECT 'bpm_category' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `bpm_category`;

SELECT 'cut_checklist_binding_condition' AS seed_table,1446 AS expected,COUNT(*) AS actual FROM `cut_checklist_binding_condition`;

SELECT 'cut_checklist_field_definition' AS seed_table,914 AS expected,COUNT(*) AS actual FROM `cut_checklist_field_definition`;

SELECT 'cut_checklist_field_option' AS seed_table,20 AS expected,COUNT(*) AS actual FROM `cut_checklist_field_option`;

SELECT 'cut_configuration_dictionary_value' AS seed_table,50 AS expected,COUNT(*) AS actual FROM `cut_configuration_dictionary_value`;

SELECT 'cut_configuration_dimension' AS seed_table,13 AS expected,COUNT(*) AS actual FROM `cut_configuration_dimension`;

SELECT 'cut_configuration_plan_section' AS seed_table,13 AS expected,COUNT(*) AS actual FROM `cut_configuration_plan_section`;

SELECT 'cut_configuration_plan_section_scope' AS seed_table,164 AS expected,COUNT(*) AS actual FROM `cut_configuration_plan_section_scope`;

SELECT 'cut_cutover_checklist_binding_rule_revision' AS seed_table,268 AS expected,COUNT(*) AS actual FROM `cut_cutover_checklist_binding_rule_revision`;

SELECT 'cut_cutover_checklist_item_definition_revision' AS seed_table,269 AS expected,COUNT(*) AS actual FROM `cut_cutover_checklist_item_definition_revision`;

SELECT 'cut_cutover_configuration_revision' AS seed_table,3 AS expected,COUNT(*) AS actual FROM `cut_cutover_configuration_revision`;

SELECT 'flyway_schema_history' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `flyway_schema_history`;

SELECT 'infra_config' AS seed_table,13 AS expected,COUNT(*) AS actual FROM `infra_config`;

SELECT 'infra_job' AS seed_table,22 AS expected,COUNT(*) AS actual FROM `infra_job`;

SELECT 'plt_business_view_revision' AS seed_table,15 AS expected,COUNT(*) AS actual FROM `plt_business_view_revision`;

SELECT 'plt_delivery_type' AS seed_table,21 AS expected,COUNT(*) AS actual FROM `plt_delivery_type`;

SELECT 'plt_dynamic_form_template' AS seed_table,12 AS expected,COUNT(*) AS actual FROM `plt_dynamic_form_template`;

SELECT 'plt_dynamic_form_template_revision' AS seed_table,26 AS expected,COUNT(*) AS actual FROM `plt_dynamic_form_template_revision`;

SELECT 'plt_entity_extension_definition' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `plt_entity_extension_definition`;

SELECT 'proj_project_template' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `proj_project_template`;

SELECT 'proj_project_template_revision' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `proj_project_template_revision`;

SELECT 'proj_stage_suggestion_rule' AS seed_table,4 AS expected,COUNT(*) AS actual FROM `proj_stage_suggestion_rule`;

SELECT 'proj_task_state_machine_revision' AS seed_table,2 AS expected,COUNT(*) AS actual FROM `proj_task_state_machine_revision`;

SELECT 'proj_task_state_transition' AS seed_table,16 AS expected,COUNT(*) AS actual FROM `proj_task_state_transition`;

SELECT 'system_dict_data' AS seed_table,2055 AS expected,COUNT(*) AS actual FROM `system_dict_data`;

SELECT 'system_dict_type' AS seed_table,407 AS expected,COUNT(*) AS actual FROM `system_dict_type`;

SELECT 'system_menu' AS seed_table,744 AS expected,COUNT(*) AS actual FROM `system_menu`;

SELECT 'system_notify_template' AS seed_table,3 AS expected,COUNT(*) AS actual FROM `system_notify_template`;

SELECT 'system_oauth2_client' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `system_oauth2_client`;

SELECT 'system_role' AS seed_table,33 AS expected,COUNT(*) AS actual FROM `system_role`;

SELECT 'system_role_menu' AS seed_table,825 AS expected,COUNT(*) AS actual FROM `system_role_menu`;

SELECT 'system_tenant' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `system_tenant`;

SELECT 'system_user_role' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `system_user_role`;

SELECT 'system_users' AS seed_table,1 AS expected,COUNT(*) AS actual FROM `system_users`;
