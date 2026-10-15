-- 0807 原始明细校正草稿。原发布修订及其历史引用保持不变；本迁移不发布、不重绑现有任务。
-- 只复制关系化配置；固定业务字段不写 JSON。Creator: cutover-0807-20261015。


CREATE TEMPORARY TABLE tmp_cut478_seed AS SELECT id FROM cut_cutover_configuration_revision WHERE id=970000000000442001 AND deleted=b'0' AND NOT EXISTS (SELECT 1 FROM cut_cutover_configuration_revision WHERE id=970000000000478001);

INSERT INTO cut_cutover_configuration_revision (id, configuration_code, configuration_name, revision_no, status_code, effective_start_time, effective_end_time, change_summary, publish_user_id, publish_time, disable_user_id, disable_time, version, creator, create_time, updater, update_time, deleted, tenant_id, navigation_target_code, validation_valid_flag, current_validation_no, validate_time, validate_user_id)
SELECT 970000000000478001, s.configuration_code, '割接标准配置（0807明细校正）', 2, 'DRAFT', NULL, NULL, '按0807原表校正8项风险适用类型、ADX限定、升级目标公告组网条件；双机反馈保留执行选择和特殊情况备注。待验证及人工发布。', NULL, NULL, NULL, NULL, 0, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted, s.tenant_id, s.navigation_target_code, NULL, NULL, NULL, NULL
FROM cut_cutover_configuration_revision s JOIN tmp_cut478_seed seed ON seed.id=s.id;

CREATE TEMPORARY TABLE tmp_cut478_dimensions AS
SELECT s.id old_id, 970000000478100000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_configuration_dimension s  WHERE s.configuration_revision_id=970000000000442001 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_configuration_dimension (id, tenant_id, configuration_revision_id, dimension_code, dimension_name, data_type_code, value_source, business_owner, context_path, dictionary_snapshot_key, enabled_flag, sort_order, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, 970000000000478001, s.dimension_code, s.dimension_name, s.data_type_code, s.value_source, s.business_owner, s.context_path, s.dictionary_snapshot_key, s.enabled_flag, s.sort_order, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_configuration_dimension s JOIN tmp_cut478_dimensions m ON m.old_id=s.id ;

CREATE TEMPORARY TABLE tmp_cut478_dictionary AS
SELECT s.id old_id, 970000000478110000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_configuration_dictionary_value s  WHERE s.configuration_revision_id=970000000000442001 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_configuration_dictionary_value (id, tenant_id, configuration_revision_id, dict_type, value_code, label, sort_order, source_status_code, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, 970000000000478001, s.dict_type, s.value_code, s.label, s.sort_order, s.source_status_code, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_configuration_dictionary_value s JOIN tmp_cut478_dictionary m ON m.old_id=s.id ;

-- 69 行的“重大项目”按项目主数据级别匹配，字典取值只复制 CRM 权威来源映射。
INSERT INTO cut_configuration_dimension
(id,tenant_id,configuration_revision_id,dimension_code,dimension_name,data_type_code,value_source,business_owner,context_path,dictionary_snapshot_key,enabled_flag,sort_order,creator,create_time,updater,update_time,deleted)
SELECT 970000000478190001,s.tenant_id,970000000000478001,'PROJECT_MAJOR_LEVEL','项目重大项目级别','STRING','DICT:majorProjectLevel','PROJ','project.majorProjectLevel','majorProjectLevel',b'1',50,
'cutover-0807-20261015',CURRENT_TIMESTAMP(3),'cutover-0807-20261015',CURRENT_TIMESTAMP(3),b'0'
FROM cut_cutover_configuration_revision s JOIN tmp_cut478_seed seed ON seed.id=s.id;

INSERT INTO cut_configuration_dictionary_value
(id,tenant_id,configuration_revision_id,dict_type,value_code,label,sort_order,source_status_code,creator,create_time,updater,update_time,deleted)
SELECT 970000000478200000+ROW_NUMBER() OVER(ORDER BY v.id),s.tenant_id,970000000000478001,'majorProjectLevel',v.value,v.label,v.sort,'ENABLED',
'cutover-0807-20261015',CURRENT_TIMESTAMP(3),'cutover-0807-20261015',CURRENT_TIMESTAMP(3),b'0'
FROM system_dict_data v CROSS JOIN cut_cutover_configuration_revision s JOIN tmp_cut478_seed seed ON seed.id=s.id
WHERE v.dict_type='majorProjectLevel' AND v.status=0 AND v.deleted=b'0';

CREATE TEMPORARY TABLE tmp_cut478_sections AS
SELECT s.id old_id, 970000000478120000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_configuration_plan_section s  WHERE s.configuration_revision_id=970000000000442001 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_configuration_plan_section (id, tenant_id, configuration_revision_id, stable_section_key, title, required_flag, sort_order, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, 970000000000478001, s.stable_section_key, s.title, s.required_flag, s.sort_order, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_configuration_plan_section s JOIN tmp_cut478_sections m ON m.old_id=s.id ;

CREATE TEMPORARY TABLE tmp_cut478_items AS
SELECT s.id old_id, 970000000478130000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_cutover_checklist_item_definition_revision s  WHERE s.configuration_revision_id=970000000000442001 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_cutover_checklist_item_definition_revision (id, configuration_revision_id, stable_item_key, item_definition_version, item_type_code, business_category_code, item_name, item_description, interface_format_code, feedback_format_code, required_flag, work_mode_code, subtable_code, status_code, sort_order, version, creator, create_time, updater, update_time, deleted, tenant_id, external_interface_code, external_manual_fallback_flag)
SELECT m.new_id, 970000000000478001, s.stable_item_key, s.item_definition_version, s.item_type_code, s.business_category_code, s.item_name, s.item_description, s.interface_format_code, s.feedback_format_code, s.required_flag, s.work_mode_code, s.subtable_code, s.status_code, s.sort_order, 0, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted, s.tenant_id, s.external_interface_code, s.external_manual_fallback_flag
FROM cut_cutover_checklist_item_definition_revision s JOIN tmp_cut478_items m ON m.old_id=s.id ;

CREATE TEMPORARY TABLE tmp_cut478_rules AS
SELECT s.id old_id, 970000000478140000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_cutover_checklist_binding_rule_revision s JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id WHERE s.configuration_revision_id=970000000000442001 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_cutover_checklist_binding_rule_revision (id, configuration_revision_id, stable_rule_key, item_definition_revision_id, item_definition_version, priority, required_result_flag, status_code, version, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT m.new_id, 970000000000478001, s.stable_rule_key, m_item_definition_revision_id.new_id, s.item_definition_version, s.priority, s.required_result_flag, s.status_code, 0, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted, s.tenant_id
FROM cut_cutover_checklist_binding_rule_revision s JOIN tmp_cut478_rules m ON m.old_id=s.id JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id;

CREATE TEMPORARY TABLE tmp_cut478_fields AS
SELECT s.id old_id, 970000000478150000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_checklist_field_definition s JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id WHERE 1=1 AND s.deleted=b'0' AND NOT (COALESCE(s.field_code,'')='normal' AND EXISTS (SELECT 1 FROM cut_cutover_checklist_item_definition_revision d WHERE d.id=s.item_definition_revision_id AND d.item_type_code='DUAL_MACHINE_CHECK')) AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

CREATE TEMPORARY TABLE tmp_cut478_parent_fields AS SELECT * FROM tmp_cut478_fields;

INSERT INTO cut_checklist_field_definition (id, tenant_id, item_definition_revision_id, custom_checklist_item_id, parent_field_id, node_path, node_kind_code, field_code, source_name, label, data_type_code, required_flag, max_length, value_source_code, sort_order, builtin_field_code, extension_field_key, visible_when_field_path, visible_equals_type_code, visible_equals_text, visible_equals_number, visible_equals_boolean, visible_equals_datetime, source_file, source_sheet, source_row, source_original_name, source_part, source_split_flag, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, m_item_definition_revision_id.new_id, s.custom_checklist_item_id, parent.new_id, s.node_path, s.node_kind_code, s.field_code, s.source_name, s.label, s.data_type_code, s.required_flag, s.max_length, s.value_source_code, s.sort_order, s.builtin_field_code, s.extension_field_key, s.visible_when_field_path, s.visible_equals_type_code, s.visible_equals_text, s.visible_equals_number, s.visible_equals_boolean, s.visible_equals_datetime, s.source_file, s.source_sheet, s.source_row, s.source_original_name, s.source_part, s.source_split_flag, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_checklist_field_definition s JOIN tmp_cut478_fields m ON m.old_id=s.id JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id LEFT JOIN tmp_cut478_parent_fields parent ON parent.old_id=s.parent_field_id;

CREATE TEMPORARY TABLE tmp_cut478_options AS
SELECT s.id old_id, 970000000478160000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_checklist_field_option s JOIN tmp_cut478_fields m_field_definition_id ON m_field_definition_id.old_id=s.field_definition_id WHERE 1=1 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_checklist_field_option (id, tenant_id, field_definition_id, option_no, label, value_type_code, value_text, value_number, value_boolean, value_datetime, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, m_field_definition_id.new_id, s.option_no, s.label, s.value_type_code, s.value_text, s.value_number, s.value_boolean, s.value_datetime, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_checklist_field_option s JOIN tmp_cut478_options m ON m.old_id=s.id JOIN tmp_cut478_fields m_field_definition_id ON m_field_definition_id.old_id=s.field_definition_id;

CREATE TEMPORARY TABLE tmp_cut478_conditions AS
SELECT s.id old_id, 970000000478170000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_checklist_binding_condition s JOIN tmp_cut478_rules m_binding_rule_revision_id ON m_binding_rule_revision_id.old_id=s.binding_rule_revision_id JOIN tmp_cut478_dimensions m_configuration_dimension_id ON m_configuration_dimension_id.old_id=s.configuration_dimension_id WHERE 1=1 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_checklist_binding_condition (id, tenant_id, binding_rule_revision_id, configuration_dimension_id, value_no, value_type_code, value_text, value_number, value_boolean, value_datetime, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, m_binding_rule_revision_id.new_id, m_configuration_dimension_id.new_id, s.value_no, s.value_type_code, s.value_text, s.value_number, s.value_boolean, s.value_datetime, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_checklist_binding_condition s JOIN tmp_cut478_conditions m ON m.old_id=s.id JOIN tmp_cut478_rules m_binding_rule_revision_id ON m_binding_rule_revision_id.old_id=s.binding_rule_revision_id JOIN tmp_cut478_dimensions m_configuration_dimension_id ON m_configuration_dimension_id.old_id=s.configuration_dimension_id;

CREATE TEMPORARY TABLE tmp_cut478_mapping AS
SELECT s.id old_id, 970000000478180000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_checklist_external_mapping s JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id WHERE 1=1 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_checklist_external_mapping (id, tenant_id, item_definition_revision_id, mapping_direction_code, mapping_no, source_path, target_key, value_type_code, required_flag, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, m_item_definition_revision_id.new_id, s.mapping_direction_code, s.mapping_no, s.source_path, s.target_key, s.value_type_code, s.required_flag, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_checklist_external_mapping s JOIN tmp_cut478_mapping m ON m.old_id=s.id JOIN tmp_cut478_items m_item_definition_revision_id ON m_item_definition_revision_id.old_id=s.item_definition_revision_id;

CREATE TEMPORARY TABLE tmp_cut478_scopes AS
SELECT s.id old_id, 970000000478190000 + ROW_NUMBER() OVER (ORDER BY s.id) new_id
FROM cut_configuration_plan_section_scope s JOIN tmp_cut478_sections m_plan_section_id ON m_plan_section_id.old_id=s.plan_section_id WHERE 1=1 AND s.deleted=b'0' AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

INSERT INTO cut_configuration_plan_section_scope (id, tenant_id, plan_section_id, scope_axis_code, scope_value, sort_order, creator, create_time, updater, update_time, deleted)
SELECT m.new_id, s.tenant_id, m_plan_section_id.new_id, s.scope_axis_code, s.scope_value, s.sort_order, 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), 'cutover-0807-20261015', CURRENT_TIMESTAMP(3), s.deleted
FROM cut_configuration_plan_section_scope s JOIN tmp_cut478_scopes m ON m.old_id=s.id JOIN tmp_cut478_sections m_plan_section_id ON m_plan_section_id.old_id=s.plan_section_id;

-- 76-83 行：配置变更替代板卡替换，其他条件不变。
UPDATE cut_checklist_binding_condition c
JOIN cut_cutover_checklist_binding_rule_revision r ON r.id=c.binding_rule_revision_id
JOIN cut_cutover_checklist_item_definition_revision i ON i.id=r.item_definition_revision_id
JOIN cut_configuration_dimension d ON d.id=c.configuration_dimension_id
SET c.value_text='CONFIGURATION_CHANGE'
WHERE r.configuration_revision_id=970000000000478001 AND d.dimension_code='CUTOVER_TYPE' AND c.value_text='DEVICE_REPLACE_BOARD'
AND i.stable_item_key IN ('RISK_DYNAMIC_TABLE_COLLECTION','RISK_MTU_JUMBO_FRAME','RISK_HUNDRED_G_FEC','RISK_LONG_CONNECTION','RISK_SECOND_PASS_DEVICE','RISK_STP','RISK_F5_DEFAULT','RISK_ADWARE_DEFAULT')
AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

-- 82-83 行只适用 ADX；设备多选采用包含命中。
INSERT INTO cut_checklist_binding_condition
(id,tenant_id,binding_rule_revision_id,configuration_dimension_id,value_no,value_type_code,value_text,creator,create_time,updater,update_time,deleted)
SELECT 970000000478900000+ROW_NUMBER() OVER(ORDER BY r.id),r.tenant_id,r.id,d.id,0,'STRING','ADX',
'cutover-0807-20261015',CURRENT_TIMESTAMP(3),'cutover-0807-20261015',CURRENT_TIMESTAMP(3),b'0'
FROM cut_cutover_checklist_binding_rule_revision r
JOIN cut_cutover_checklist_item_definition_revision i ON i.id=r.item_definition_revision_id
JOIN cut_configuration_dimension d ON d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code='DEVICE_TYPE'
WHERE r.configuration_revision_id=970000000000478001 AND i.stable_item_key IN ('RISK_F5_DEFAULT','RISK_ADWARE_DEFAULT')
AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

-- 62 行：升级后技术公告限定于原表列举的五种可靠性组网。
INSERT INTO cut_checklist_binding_condition
(id,tenant_id,binding_rule_revision_id,configuration_dimension_id,value_no,value_type_code,value_text,creator,create_time,updater,update_time,deleted)
SELECT 970000000478910000+v.sort_order,r.tenant_id,r.id,d.id,v.sort_order,'STRING',v.value_code,
'cutover-0807-20261015',CURRENT_TIMESTAMP(3),'cutover-0807-20261015',CURRENT_TIMESTAMP(3),b'0'
FROM cut_cutover_checklist_binding_rule_revision r
JOIN cut_cutover_checklist_item_definition_revision i ON i.id=r.item_definition_revision_id
JOIN cut_configuration_dimension d ON d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code='NETWORK_MODE'
JOIN cut_configuration_dictionary_value v ON v.configuration_revision_id=r.configuration_revision_id AND v.dict_type='pms_cutover_task_network_mode'
WHERE r.configuration_revision_id=970000000000478001 AND i.stable_item_key='RISK_TARGET_VERSION_BULLETIN'
AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

-- 双机表原始反馈：一个执行选择 + 特殊情况备注。负向执行须备注；旧答案结构不变。
INSERT INTO cut_checklist_binding_condition
(id,tenant_id,binding_rule_revision_id,configuration_dimension_id,value_no,value_type_code,value_text,creator,create_time,updater,update_time,deleted)
SELECT 970000000478920000+ROW_NUMBER() OVER(ORDER BY v.id),r.tenant_id,r.id,d.id,ROW_NUMBER() OVER(ORDER BY v.id)-1,'STRING',v.value_code,
'cutover-0807-20261015',CURRENT_TIMESTAMP(3),'cutover-0807-20261015',CURRENT_TIMESTAMP(3),b'0'
FROM cut_cutover_checklist_binding_rule_revision r
JOIN cut_cutover_checklist_item_definition_revision i ON i.id=r.item_definition_revision_id
JOIN cut_configuration_dimension d ON d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code='PROJECT_MAJOR_LEVEL'
JOIN cut_configuration_dictionary_value v ON v.configuration_revision_id=r.configuration_revision_id AND v.dict_type='majorProjectLevel'
WHERE r.configuration_revision_id=970000000000478001 AND i.stable_item_key='RISK_MAJOR_PROJECT_SPARES'
AND v.value_code IN ('办事处级','市场级','公司级') AND EXISTS (SELECT 1 FROM tmp_cut478_seed);

UPDATE cut_checklist_field_definition f
JOIN cut_cutover_checklist_item_definition_revision i ON i.id=f.item_definition_revision_id
SET f.label=CASE WHEN f.field_code='executed' THEN '是否已执行' ELSE f.label END,
    f.visible_when_field_path=CASE WHEN f.field_code='remark' THEN 'executed' ELSE f.visible_when_field_path END,
    f.node_path=CASE WHEN f.field_code='remark' THEN '$.fields[1]' ELSE f.node_path END,
    f.sort_order=CASE WHEN f.field_code='remark' THEN 1 ELSE f.sort_order END
WHERE i.configuration_revision_id=970000000000478001 AND i.item_type_code='DUAL_MACHINE_CHECK'
AND EXISTS (SELECT 1 FROM tmp_cut478_seed);


DROP TEMPORARY TABLE tmp_cut478_scopes;

DROP TEMPORARY TABLE tmp_cut478_mapping;

DROP TEMPORARY TABLE tmp_cut478_conditions;

DROP TEMPORARY TABLE tmp_cut478_options;

DROP TEMPORARY TABLE tmp_cut478_fields;

DROP TEMPORARY TABLE tmp_cut478_rules;

DROP TEMPORARY TABLE tmp_cut478_items;

DROP TEMPORARY TABLE tmp_cut478_sections;

DROP TEMPORARY TABLE tmp_cut478_dictionary;

DROP TEMPORARY TABLE tmp_cut478_dimensions;

DROP TEMPORARY TABLE tmp_cut478_parent_fields;

DROP TEMPORARY TABLE tmp_cut478_seed;
