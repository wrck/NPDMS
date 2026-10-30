-- Approved 75-table CUT storage: project existing immutable JSON into explicit columns/relations.
-- Reads below are a one-off migration only. No original JSON, audit value, version or status is rewritten.
-- IDs occupy a migration-only high range; references always include the original tenant.
-- MySQL 8.4 JSON_TABLE: https://dev.mysql.com/doc/refman/8.4/en/json-table-functions.html
DELIMITER $$
DROP PROCEDURE IF EXISTS cut_v475_assert$$
CREATE PROCEDURE cut_v475_assert(IN valid_value BOOLEAN, IN failure_message VARCHAR(128))
BEGIN
  IF NOT COALESCE(valid_value, FALSE) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
  END IF;
END$$
DELIMITER ;
SET collation_connection = 'utf8mb4_unicode_ci';
START TRANSACTION;

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_configuration_dimension`) = 0), 'cut_configuration_dimension: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_configuration_dictionary_value`) = 0), 'cut_configuration_dictionary_value: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_configuration_plan_section`) = 0), 'cut_configuration_plan_section: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_configuration_plan_section_scope`) = 0), 'cut_configuration_plan_section_scope: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_configuration_validation_issue`) = 0), 'cut_configuration_validation_issue: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_field_definition`) = 0), 'cut_checklist_field_definition: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_field_option`) = 0), 'cut_checklist_field_option: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_binding_condition`) = 0), 'cut_checklist_binding_condition: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_external_mapping`) = 0), 'cut_checklist_external_mapping: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_assessment_device`) = 0), 'cut_assessment_device: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_input_dimension`) = 0), 'cut_checklist_input_dimension: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_match_candidate`) = 0), 'cut_checklist_match_candidate: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_device_context`) = 0), 'cut_checklist_device_context: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_result_file`) = 0), 'cut_checklist_result_file: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_external_query`) = 0), 'cut_checklist_external_query: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_external_query_parameter`) = 0), 'cut_checklist_external_query_parameter: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_checklist_result_query_reference`) = 0), 'cut_checklist_result_query_reference: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_cutover_background`) = 0), 'cut_result_cutover_background: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_business_summary`) = 0), 'cut_result_business_summary: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_impact_scope`) = 0), 'cut_result_impact_scope: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_continuity_requirement`) = 0), 'cut_result_continuity_requirement: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_interruption`) = 0), 'cut_result_interruption: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_check_feedback`) = 0), 'cut_result_check_feedback: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_spare_reference`) = 0), 'cut_result_spare_reference: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_bulletin_summary`) = 0), 'cut_result_bulletin_summary: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_device_location`) = 0), 'cut_result_device_location: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_interface_plan`) = 0), 'cut_result_interface_plan: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_ip_plan`) = 0), 'cut_result_ip_plan: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_performance`) = 0), 'cut_result_performance: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_technical_bulletin`) = 0), 'cut_result_technical_bulletin: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_custom_value`) = 0), 'cut_result_custom_value: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_result_custom_row`) = 0), 'cut_result_custom_row: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_plan_schedule`) = 0), 'cut_plan_schedule: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_plan_device`) = 0), 'cut_plan_device: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_plan_file`) = 0), 'cut_plan_file: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_plan_risk_mitigation`) = 0), 'cut_plan_risk_mitigation: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_plan_support_contact_change`) = 0), 'cut_plan_support_contact_change: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_approval_result_reference`) = 0), 'cut_approval_result_reference: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_closure_device`) = 0), 'cut_closure_device: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_spare_need_source`) = 0), 'cut_spare_need_source: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `cut_spare_request_device`) = 0), 'cut_spare_request_device: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `imp_cutover_readiness_device`) = 0), 'imp_cutover_readiness_device: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `imp_cutover_readiness_source`) = 0), 'imp_cutover_readiness_source: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `imp_cutover_readiness_source_object`) = 0), 'imp_cutover_readiness_source_object: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `imp_cutover_readiness_source_watermark`) = 0), 'imp_cutover_readiness_source_watermark: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `imp_cutover_readiness_unmet`) = 0), 'imp_cutover_readiness_unmet: target must be empty before history projection');

CALL cut_v475_assert(((SELECT COUNT(*) FROM `ast_device_cutover_result_file`) = 0), 'ast_device_cutover_result_file: target must be empty before history projection');

-- Configuration revision metadata and field trees.

UPDATE `cut_cutover_configuration_revision` r 
SET r.`navigation_target_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.navigation_rule_snapshot, '$.target')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.navigation_rule_snapshot, '$.target')) END,
    r.`validation_valid_flag` = IF(r.validation_result_snapshot IS NULL, NULL, JSON_LENGTH(r.validation_result_snapshot)=0),
    r.`current_validation_no` = IF(r.validation_result_snapshot IS NULL, NULL, 1),
    r.`update_time` = r.update_time
WHERE 1=1;

CREATE TEMPORARY TABLE cut_v475_dimensions AS
SELECT r.*, a.seq, JSON_EXTRACT(r.dimension_definition_snapshot, CONCAT('$[',a.seq-1,']')) AS value_json
FROM cut_cutover_configuration_revision r
JOIN JSON_TABLE(r.dimension_definition_snapshot, '$[*]' COLUMNS(seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `cut_configuration_dimension` (`id`, `tenant_id`, `configuration_revision_id`, `dimension_code`, `dimension_name`, `data_type_code`, `value_source`, `business_owner`, `context_path`, `enabled_flag`, `sort_order`, `dictionary_snapshot_key`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800004000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,r.seq),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.code')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.code')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.name')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.name')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.dataType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.dataType')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.valueSource')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.valueSource')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.owner')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.owner')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.contextPath')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.contextPath')) END,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.enabled')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.enabled') = CAST('true' AS JSON) ELSE NULL END,
       r.seq-1,
       (SELECT MIN(k.dict_key) FROM JSON_TABLE(JSON_KEYS(r.dictionary_snapshot), '$[*]' COLUMNS(dict_key VARCHAR(128) PATH '$')) k WHERE JSON_TYPE(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')))='STRING' AND CONCAT('DICT:',JSON_UNQUOTE(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"'))))=CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.valueSource')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.valueSource')) END),
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_v475_dimensions r;

INSERT INTO `cut_configuration_dictionary_value` (`id`, `tenant_id`, `configuration_revision_id`, `dict_type`, `value_code`, `label`, `sort_order`, `source_status_code`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800005000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,k.dict_key,a.seq),
       r.tenant_id,
       r.id,
       k.dict_key,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.value')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.value')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.label')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.label')) END,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.status')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')), CONCAT('$[', a.seq-1, ']')), '$.status')) END,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_configuration_revision r JOIN JSON_TABLE(JSON_KEYS(r.dictionary_snapshot), '$[*]' COLUMNS(dict_key VARCHAR(128) PATH '$')) k ON TRUE
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')))='ARRAY',JSON_EXTRACT(r.dictionary_snapshot,CONCAT('$."',k.dict_key,'"')),JSON_ARRAY()), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `cut_configuration_plan_section` (`id`, `tenant_id`, `configuration_revision_id`, `stable_section_key`, `title`, `required_flag`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800006000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.title')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.title')) END,
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.required') = CAST('true' AS JSON) ELSE NULL END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.sortOrder')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.sortOrder')) END) AS SIGNED),
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_configuration_revision r
JOIN JSON_TABLE(COALESCE(r.plan_template_section_snapshot, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `cut_configuration_plan_section_scope` (`id`, `tenant_id`, `plan_section_id`, `scope_axis_code`, `scope_value`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7801000000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq,b.seq),
       r.tenant_id,
       s.id,
       'CUTOVER_TYPE',
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.cutoverTypeCodes'), CONCAT('$[', b.seq-1, ']'))) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.cutoverTypeCodes'), CONCAT('$[', b.seq-1, ']'))) END,
       b.seq-1,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_configuration_revision r
JOIN JSON_TABLE(COALESCE(r.plan_template_section_snapshot, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
JOIN cut_configuration_plan_section s ON s.tenant_id=r.tenant_id AND s.configuration_revision_id=r.id AND s.stable_section_key=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) END
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.cutoverTypeCodes'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) b ON TRUE;

INSERT INTO `cut_configuration_plan_section_scope` (`id`, `tenant_id`, `plan_section_id`, `scope_axis_code`, `scope_value`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7801000001000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq,b.seq),
       r.tenant_id,
       s.id,
       'CUTOVER_LEVEL',
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.levelCodes'), CONCAT('$[', b.seq-1, ']'))) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.levelCodes'), CONCAT('$[', b.seq-1, ']'))) END,
       b.seq-1,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_configuration_revision r
JOIN JSON_TABLE(COALESCE(r.plan_template_section_snapshot, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
JOIN cut_configuration_plan_section s ON s.tenant_id=r.tenant_id AND s.configuration_revision_id=r.id AND s.stable_section_key=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.stableSectionKey')) END
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(JSON_EXTRACT(r.plan_template_section_snapshot, CONCAT('$[', a.seq-1, ']')), '$.levelCodes'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) b ON TRUE;

INSERT INTO `cut_configuration_validation_issue` (`id`, `tenant_id`, `configuration_revision_id`, `validation_no`, `issue_no`, `location`, `message`, `validate_time`, `validate_user_id`, `create_user_id`, `create_time`)
SELECT 7800008000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       1,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.validation_result_snapshot, CONCAT('$[', a.seq-1, ']')), '$.location')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.validation_result_snapshot, CONCAT('$[', a.seq-1, ']')), '$.location')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.validation_result_snapshot, CONCAT('$[', a.seq-1, ']')), '$.message')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.validation_result_snapshot, CONCAT('$[', a.seq-1, ']')), '$.message')) END,
       r.update_time,
       CAST(IF(r.updater REGEXP '^[1-9][0-9]*$',r.updater,NULL) AS SIGNED),
       CAST(IF(r.updater REGEXP '^[1-9][0-9]*$',r.updater,NULL) AS SIGNED),
       r.update_time
FROM cut_cutover_configuration_revision r
JOIN JSON_TABLE(COALESCE(r.validation_result_snapshot, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

UPDATE `cut_cutover_checklist_item_definition_revision` r 
SET r.`external_interface_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.external_source_config, '$.interfaceCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.external_source_config, '$.interfaceCode')) END,
    r.`external_manual_fallback_flag` = CASE JSON_TYPE(JSON_EXTRACT(r.external_source_config, '$.manualFallback')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.external_source_config, '$.manualFallback') = CAST('true' AS JSON) ELSE NULL END,
    r.`update_time` = r.update_time
WHERE 1=1;

CREATE TEMPORARY TABLE cut_v475_schema_source AS
SELECT tenant_id, id AS owner_id, id AS definition_id, CAST(NULL AS SIGNED) AS custom_id,
       business_category_code AS category, item_type_code, interface_schema AS schema_json,
       creator, create_time, updater, update_time, deleted
FROM cut_cutover_checklist_item_definition_revision
UNION ALL
SELECT tenant_id, id, NULL, id, NULL, item_type_code, interface_schema_snapshot,
       creator, create_time, updater, update_time, deleted
FROM cut_cutover_checklist_item WHERE source_code='CUSTOM';
CREATE TEMPORARY TABLE cut_v475_field_tree AS
WITH RECURSIVE field_tree AS (
 SELECT s.*, CAST('$' AS CHAR(512)) AS node_path, CAST(NULL AS CHAR(512)) AS parent_path,
        CAST('$' AS CHAR(512)) AS semantic_path, CAST('ROOT' AS CHAR(16)) AS node_kind,
        0 AS sort_order, schema_json AS node_json
 FROM cut_v475_schema_source s
 UNION ALL
 SELECT p.tenant_id,p.owner_id,p.definition_id,p.custom_id,p.category,p.item_type_code,p.schema_json,
        p.creator,p.create_time,p.updater,p.update_time,p.deleted,
        CONCAT(p.node_path,'.',k.collection,'[',a.seq-1,']'),p.node_path,
        CONCAT(IF(p.semantic_path='$','',CONCAT(p.semantic_path,'.')),
          COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.node_json,CONCAT('$.',k.collection,'[',a.seq-1,'].code'))),
                   JSON_UNQUOTE(JSON_EXTRACT(p.node_json,CONCAT('$.',k.collection,'[',a.seq-1,'].key'))))),
        IF(k.collection='fields','FIELD','COLUMN'),a.seq-1,
        JSON_EXTRACT(p.node_json,CONCAT('$.',k.collection,'[',a.seq-1,']'))
 FROM field_tree p
 JOIN JSON_TABLE('["fields","columns"]','$[*]' COLUMNS(collection VARCHAR(16) PATH '$')) k ON TRUE
 JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(p.node_json,CONCAT('$.',k.collection)),JSON_ARRAY()),'$[*]' COLUMNS(seq FOR ORDINALITY)) a ON TRUE
)
SELECT 7802000000000000000 + ROW_NUMBER() OVER(ORDER BY tenant_id,owner_id,node_path) AS id, field_tree.* FROM field_tree;
CREATE TEMPORARY TABLE cut_v475_field_parent AS SELECT tenant_id,owner_id,node_path,id FROM cut_v475_field_tree;

INSERT INTO `cut_checklist_field_definition` (`id`, `tenant_id`, `item_definition_revision_id`, `custom_checklist_item_id`, `parent_field_id`, `node_path`, `node_kind_code`, `field_code`, `source_name`, `label`, `data_type_code`, `required_flag`, `max_length`, `value_source_code`, `sort_order`, `builtin_field_code`, `extension_field_key`, `visible_when_field_path`, `visible_equals_type_code`, `visible_equals_text`, `visible_equals_number`, `visible_equals_boolean`, `visible_equals_datetime`, `source_file`, `source_sheet`, `source_row`, `source_original_name`, `source_part`, `source_split_flag`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT r.id,
       r.tenant_id,
       r.definition_id,
       r.custom_id,
       p.id,
       r.node_path,
       r.node_kind,
       COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.code')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.code')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.key')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.key')) END),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.name')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.name')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.label')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.label')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.type')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.type')) END,
       COALESCE(CASE JSON_TYPE(JSON_EXTRACT(r.node_json, '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.node_json, '$.required') = CAST('true' AS JSON) ELSE NULL END,0),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.maxLength')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.maxLength')) END) AS SIGNED),
       IF(JSON_TYPE(JSON_EXTRACT(r.node_json,'$.source'))='STRING',JSON_UNQUOTE(JSON_EXTRACT(r.node_json,'$.source')),NULL),
       r.sort_order,
       IF((r.item_type_code='DUAL_MACHINE_CHECK' OR r.category IN ('ADWARE_DEFAULT','BUSINESS_SUMMARY','COMPILE_LIMIT_ASSESSMENT','CONFIG_BACKUP','CONNECTIVITY_TEST_CASE','CONTINUITY_REQUIREMENT','CURRENT_TOPOLOGY','CURRENT_VERSION_BULLETIN','CUTOVER_BACKGROUND','DEVICE_LOCATION_PLAN','DIAGNOSTIC_LOG','DUAL_CONFIG_CONSISTENCY','DUAL_CONTROLLER_VERSION','DYNAMIC_TABLE_COLLECTION','FILTER_NAT_QOS_COMPILE_COUNT','HOT_PATCH_BACKUP','HUNDRED_G_FEC','IMPACT_SCOPE','INTERFACE_INTERCONNECT_PLAN','INTERRUPTION_COUNT','IP_VLAN_PLAN','LICENSE_BACKUP','LONG_CONNECTION','MAJOR_PROJECT_SPARES','MTU_JUMBO_FRAME','PERFORMANCE_BASELINE','ROOM_OPERATION_COMMITMENT','RUNNING_VERSION_BACKUP','SECOND_PASS_DEVICE','SESSION_SYNC','STP','SYSTEM_LOG','TARGET_VERSION_BULLETIN','VENDOR_CONFIG_TRANSLATION')), IF(r.semantic_path='$',IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category),IF(CASE IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category) WHEN 'CUTOVER_BACKGROUND' THEN r.semantic_path IN ('solvesOnlineIssue','issueTicketNo','issueHandler','repeatCutover','firstCutoverOwner','backgroundDescription') WHEN 'DEVICE_LOCATION_PLAN' THEN r.semantic_path IN ('deviceType','serialNumber','deviceName','location') WHEN 'INTERFACE_INTERCONNECT_PLAN' THEN r.semantic_path IN ('localDevice','localInterface','remoteInterface','interfaceType','cableModel','remark') WHEN 'IP_VLAN_PLAN' THEN r.semantic_path IN ('deviceName','interface','ip','mask','vrrpIp','gateway','remark') WHEN 'PERFORMANCE_BASELINE' THEN r.semantic_path IN ('serialNumber','concurrentConnections','newConnections','cpuUsage','memoryUsage') WHEN 'MAJOR_PROJECT_SPARES' THEN r.semantic_path IN ('majorCutover','spareReference') WHEN 'CURRENT_VERSION_BULLETIN' THEN r.semantic_path IN ('hasBulletins','queryBasis','bulletins','bulletins.number','bulletins.subject','bulletins.description','bulletins.solution','bulletins.involved','bulletins.measures') WHEN 'TARGET_VERSION_BULLETIN' THEN r.semantic_path IN ('hasBulletins','queryBasis','bulletins','bulletins.number','bulletins.subject','bulletins.description','bulletins.solution','bulletins.involved','bulletins.measures') ELSE r.semantic_path IN ('executed','normal','remark') END,CONCAT(IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category),'.',r.semantic_path),NULL)),NULL),
       IF(IF((r.item_type_code='DUAL_MACHINE_CHECK' OR r.category IN ('ADWARE_DEFAULT','BUSINESS_SUMMARY','COMPILE_LIMIT_ASSESSMENT','CONFIG_BACKUP','CONNECTIVITY_TEST_CASE','CONTINUITY_REQUIREMENT','CURRENT_TOPOLOGY','CURRENT_VERSION_BULLETIN','CUTOVER_BACKGROUND','DEVICE_LOCATION_PLAN','DIAGNOSTIC_LOG','DUAL_CONFIG_CONSISTENCY','DUAL_CONTROLLER_VERSION','DYNAMIC_TABLE_COLLECTION','FILTER_NAT_QOS_COMPILE_COUNT','HOT_PATCH_BACKUP','HUNDRED_G_FEC','IMPACT_SCOPE','INTERFACE_INTERCONNECT_PLAN','INTERRUPTION_COUNT','IP_VLAN_PLAN','LICENSE_BACKUP','LONG_CONNECTION','MAJOR_PROJECT_SPARES','MTU_JUMBO_FRAME','PERFORMANCE_BASELINE','ROOM_OPERATION_COMMITMENT','RUNNING_VERSION_BACKUP','SECOND_PASS_DEVICE','SESSION_SYNC','STP','SYSTEM_LOG','TARGET_VERSION_BULLETIN','VENDOR_CONFIG_TRANSLATION')), IF(r.semantic_path='$',IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category),IF(CASE IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category) WHEN 'CUTOVER_BACKGROUND' THEN r.semantic_path IN ('solvesOnlineIssue','issueTicketNo','issueHandler','repeatCutover','firstCutoverOwner','backgroundDescription') WHEN 'DEVICE_LOCATION_PLAN' THEN r.semantic_path IN ('deviceType','serialNumber','deviceName','location') WHEN 'INTERFACE_INTERCONNECT_PLAN' THEN r.semantic_path IN ('localDevice','localInterface','remoteInterface','interfaceType','cableModel','remark') WHEN 'IP_VLAN_PLAN' THEN r.semantic_path IN ('deviceName','interface','ip','mask','vrrpIp','gateway','remark') WHEN 'PERFORMANCE_BASELINE' THEN r.semantic_path IN ('serialNumber','concurrentConnections','newConnections','cpuUsage','memoryUsage') WHEN 'MAJOR_PROJECT_SPARES' THEN r.semantic_path IN ('majorCutover','spareReference') WHEN 'CURRENT_VERSION_BULLETIN' THEN r.semantic_path IN ('hasBulletins','queryBasis','bulletins','bulletins.number','bulletins.subject','bulletins.description','bulletins.solution','bulletins.involved','bulletins.measures') WHEN 'TARGET_VERSION_BULLETIN' THEN r.semantic_path IN ('hasBulletins','queryBasis','bulletins','bulletins.number','bulletins.subject','bulletins.description','bulletins.solution','bulletins.involved','bulletins.measures') ELSE r.semantic_path IN ('executed','normal','remark') END,CONCAT(IF(r.item_type_code='DUAL_MACHINE_CHECK','DUAL_MACHINE_CHECK',r.category),'.',r.semantic_path),NULL)),NULL) IS NULL,COALESCE(COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.code')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.code')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.key')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.key')) END),'$'),NULL),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.field')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.visibleWhen.field')) END,
       IF(JSON_CONTAINS_PATH(r.node_json,'one','$.visibleWhen'),CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals')) IN ('INTEGER','DOUBLE','DECIMAL') THEN 'NUMBER' WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals'))='STRING' THEN 'STRING' WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals'))='BOOLEAN' THEN 'BOOLEAN' ELSE 'NULL' END,NULL),
       IF(JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals'))='STRING', JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals')), NULL),
       IF(JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals')) IN ('INTEGER','DOUBLE','DECIMAL'), CAST(JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals')) AS DECIMAL(65,30)), NULL),
       CASE JSON_TYPE(JSON_EXTRACT(r.node_json, '$.visibleWhen.equals')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.node_json, '$.visibleWhen.equals') = CAST('true' AS JSON) ELSE NULL END,
       NULL,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.file')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.source.file')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.sheet')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.source.sheet')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.row')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.source.row')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.originalName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.source.originalName')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.part')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.node_json, '$.source.part')) END) AS SIGNED),
       CASE JSON_TYPE(JSON_EXTRACT(r.node_json, '$.source.split')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.node_json, '$.source.split') = CAST('true' AS JSON) ELSE NULL END,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_v475_field_tree r LEFT JOIN cut_v475_field_parent p ON p.tenant_id=r.tenant_id AND p.owner_id=r.owner_id AND p.node_path=r.parent_path ORDER BY LENGTH(r.node_path),r.id;

INSERT INTO `cut_checklist_field_option` (`id`, `tenant_id`, `field_definition_id`, `option_no`, `label`, `value_type_code`, `value_text`, `value_number`, `value_boolean`, `value_datetime`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800010000000000000 + ROW_NUMBER() OVER (ORDER BY r.id,a.seq),
       r.tenant_id,
       r.id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')), '$.label')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')), '$.label')) END,
       CASE WHEN JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))) IN ('INTEGER','DOUBLE','DECIMAL') THEN 'NUMBER' WHEN JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']'))))='STRING' THEN 'STRING' WHEN JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']'))))='BOOLEAN' THEN 'BOOLEAN' ELSE 'NULL' END,
       IF(JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']'))))='STRING', JSON_UNQUOTE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))), NULL),
       IF(JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))) IN ('INTEGER','DOUBLE','DECIMAL'), CAST(JSON_UNQUOTE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))) AS DECIMAL(65,30)), NULL),
       CASE JSON_TYPE(IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))) WHEN 'BOOLEAN' THEN IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')))='OBJECT',JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']')),'$.value'),JSON_EXTRACT(JSON_EXTRACT(r.node_json, '$.options'), CONCAT('$[', a.seq-1, ']'))) = CAST('true' AS JSON) ELSE NULL END,
       NULL,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_v475_field_tree r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.node_json, '$.options'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist_binding_rule_revision r JOIN JSON_TABLE(JSON_KEYS(r.dimension_condition_snapshot),'$[*]' COLUMNS(dimension_code VARCHAR(64) PATH '$')) k ON TRUE LEFT JOIN cut_configuration_dimension d ON d.tenant_id=r.tenant_id AND d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code=k.dimension_code COLLATE utf8mb4_0900_ai_ci WHERE d.id IS NULL) = 0), 'Binding condition has no exact configuration dimension');

INSERT INTO `cut_checklist_binding_condition` (`id`, `tenant_id`, `binding_rule_revision_id`, `configuration_dimension_id`, `value_no`, `value_type_code`, `value_text`, `value_number`, `value_boolean`, `value_datetime`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800011000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,k.dimension_code,a.seq),
       r.tenant_id,
       r.id,
       d.id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']'))) IN ('INTEGER','DOUBLE','DECIMAL') THEN 'NUMBER' WHEN JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']')))='STRING' THEN 'STRING' WHEN JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']')))='BOOLEAN' THEN 'BOOLEAN' ELSE 'NULL' END,
       IF(JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']')))='STRING', JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']'))), NULL),
       IF(JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']'))) IN ('INTEGER','DOUBLE','DECIMAL'), CAST(JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']'))) AS DECIMAL(65,30)), NULL),
       CASE JSON_TYPE(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']'))) WHEN 'BOOLEAN' THEN JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), CONCAT('$[', a.seq-1, ']')) = CAST('true' AS JSON) ELSE NULL END,
       NULL,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_checklist_binding_rule_revision r JOIN JSON_TABLE(JSON_KEYS(r.dimension_condition_snapshot),'$[*]' COLUMNS(dimension_code VARCHAR(64) PATH '$')) k ON TRUE JOIN cut_configuration_dimension d ON d.tenant_id=r.tenant_id AND d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code=k.dimension_code COLLATE utf8mb4_0900_ai_ci
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))='ARRAY',JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')),JSON_ARRAY(JSON_EXTRACT(r.dimension_condition_snapshot,CONCAT('$."',k.dimension_code,'"')))), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `cut_checklist_external_mapping` (`id`, `tenant_id`, `item_definition_revision_id`, `mapping_direction_code`, `mapping_no`, `source_path`, `target_key`, `value_type_code`, `required_flag`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7803000000000000000 + ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,k.target_key),
       r.tenant_id,
       r.id,
       'PARAM',
       ROW_NUMBER() OVER(PARTITION BY r.id ORDER BY k.target_key)-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.sourcePath')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.sourcePath')) END,
       k.target_key,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.valueType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.valueType')) END,
       COALESCE(CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.parameterMapping'),CONCAT('$."',k.target_key,'"')), '$.required') = CAST('true' AS JSON) ELSE NULL END,0),
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_checklist_item_definition_revision r JOIN JSON_TABLE(JSON_KEYS(JSON_EXTRACT(r.external_source_config, '$.parameterMapping')),'$[*]' COLUMNS(target_key VARCHAR(128) PATH '$')) k ON TRUE;

INSERT INTO `cut_checklist_external_mapping` (`id`, `tenant_id`, `item_definition_revision_id`, `mapping_direction_code`, `mapping_no`, `source_path`, `target_key`, `value_type_code`, `required_flag`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7803000001000000000 + ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,k.target_key),
       r.tenant_id,
       r.id,
       'RETURN',
       ROW_NUMBER() OVER(PARTITION BY r.id ORDER BY k.target_key)-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.sourcePath')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.sourcePath')) END,
       k.target_key,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.valueType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.valueType')) END,
       COALESCE(CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.external_source_config, '$.returnMapping'),CONCAT('$."',k.target_key,'"')), '$.required') = CAST('true' AS JSON) ELSE NULL END,0),
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_checklist_item_definition_revision r JOIN JSON_TABLE(JSON_KEYS(JSON_EXTRACT(r.external_source_config, '$.returnMapping')),'$[*]' COLUMNS(target_key VARCHAR(128) PATH '$')) k ON TRUE;

-- P1/P2: frozen project, customer and readiness facts, plus assessment device rows.

UPDATE `cut_task` r 
SET r.`project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.projectVersion')) END) AS SIGNED),
    r.`project_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.projectCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.projectCode')) END,
    r.`project_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.projectName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.projectName')) END,
    r.`customer_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.customerCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.customerCode')) END,
    r.`customer_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.customerName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.customerName')) END,
    r.`department_id` = COALESCE(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentId')) END) AS SIGNED),CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.officeDepartmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.officeDepartmentId')) END) AS SIGNED)),
    r.`department_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentCode')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.officeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.officeCode')) END),
    r.`department_name` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.departmentName')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.project_context_snapshot, '$.officeName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.project_context_snapshot, '$.officeName')) END),
    r.`customer_fact_status_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.status')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.status')) END,
    r.`service_level_revision_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.serviceLevelRevisionId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.serviceLevelRevisionId')) END) AS SIGNED),
    r.`service_level_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.serviceLevelCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.serviceLevelCode')) END,
    r.`customer_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.factVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.factVersion')) END) AS SIGNED),
    r.`service_level_effective_start_time` = CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.effectiveFrom')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.effectiveFrom')) END), 'T', ' ') AS DATETIME(3)),
    r.`service_level_effective_end_time` = CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.customer_context_snapshot, '$.effectiveTo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.customer_context_snapshot, '$.effectiveTo')) END), 'T', ' ') AS DATETIME(3)),
    r.`readiness_decision_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.readiness_context_snapshot, '$.decision')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.readiness_context_snapshot, '$.decision')) END,
    r.`update_time` = r.update_time
WHERE r.task_origin='NEW_PLATFORM';

UPDATE `cut_assessment` r 
SET r.`project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectVersion')) END) AS SIGNED),
    r.`project_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectCode')) END,
    r.`project_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectName')) END,
    r.`customer_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerCode')) END,
    r.`customer_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerName')) END,
    r.`department_id` = COALESCE(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentId')) END) AS SIGNED),CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeDepartmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeDepartmentId')) END) AS SIGNED)),
    r.`department_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentCode')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeCode')) END),
    r.`department_name` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.departmentName')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.officeName')) END),
    r.`customer_fact_status_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.status')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.status')) END,
    r.`service_level_revision_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.serviceLevelRevisionId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.serviceLevelRevisionId')) END) AS SIGNED),
    r.`service_level_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.serviceLevelCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.serviceLevelCode')) END,
    r.`customer_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.factVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.factVersion')) END) AS SIGNED),
    r.`service_level_effective_start_time` = CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.effectiveFrom')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.effectiveFrom')) END), 'T', ' ') AS DATETIME(3)),
    r.`service_level_effective_end_time` = CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.effectiveTo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.customerServiceLevel'), '$.effectiveTo')) END), 'T', ' ') AS DATETIME(3)),
    r.`business_importance_level` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.businessImportanceLevel')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.businessImportanceLevel')) END,
    r.`operation_complexity_level` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.operationComplexityLevel')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.operationComplexityLevel')) END,
    r.`hidden_risk_level` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.hiddenRiskLevel')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.hiddenRiskLevel')) END,
    r.`spare_part_applied_flag` = CASE JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.sparePartApplied')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.answer_snapshot, '$.sparePartApplied') = CAST('true' AS JSON) ELSE NULL END,
    r.`intake_source_type_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.context_snapshot, '$.intakeSourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.context_snapshot, '$.intakeSourceType')) END,
    r.`implementation_readiness_snapshot_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotId')) END) AS SIGNED),
    r.`implementation_readiness_snapshot_version` = IF(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotId')) END) AS SIGNED) IS NULL,NULL,CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.snapshotVersion')) END) AS SIGNED)),
    r.`readiness_decision_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.decision')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.implementationReadiness'), '$.decision')) END,
    r.`project_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectId')) END) AS SIGNED),
    r.`customer_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.customerId')) END) AS SIGNED),
    r.`project_scope_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.project'), '$.projectScopeVersion')) END) AS SIGNED),
    r.`update_time` = r.update_time
WHERE 1=1;

INSERT INTO `cut_assessment_device` (`id`, `tenant_id`, `assessment_id`, `task_device_scope_id`, `device_id`, `project_id`, `sn`, `project_assignment_version`, `device_type_code`, `device_type_source_version`, `product_type_code`, `product_type_source_version`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800017000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       s.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeCode')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeSourceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeSourceVersion')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),CONCAT('$[',p.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) p WHERE p.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.productTypeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),CONCAT('$[',p.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) p WHERE p.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.productTypeCode')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),CONCAT('$[',p.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) p WHERE p.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.sourceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),CONCAT('$[',p.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.context_snapshot, '$.deviceProductTypes'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) p WHERE p.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.sourceVersion')) END,
       a.seq-1,
       CAST(IF(r.creator REGEXP '^[1-9][0-9]*$', r.creator, NULL) AS SIGNED),
       r.create_time
FROM cut_assessment r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_snapshot, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_task_device_scope s ON s.tenant_id=r.tenant_id AND s.cutover_task_id=r.cutover_task_id AND s.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED) AND s.project_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) END) AS SIGNED) AND s.project_assignment_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED) AND s.serial_number_snapshot=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_task r JOIN JSON_TABLE(COALESCE(r.device_scope_watermark, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE LEFT JOIN cut_task_device_scope d ON d.tenant_id=r.tenant_id AND d.cutover_task_id=r.id AND d.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED) AND d.project_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectId')) END) AS SIGNED) AND d.project_assignment_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED) AND d.serial_number_snapshot=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END WHERE d.id IS NULL) = 0), 'cut_task: frozen device identity cannot be matched exactly');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_assessment r JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_snapshot, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE LEFT JOIN cut_task_device_scope d ON d.tenant_id=r.tenant_id AND d.cutover_task_id=r.cutover_task_id AND d.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED) AND d.project_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectId')) END) AS SIGNED) AND d.project_assignment_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED) AND d.serial_number_snapshot=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END WHERE d.id IS NULL) = 0), 'cut_assessment: frozen device identity cannot be matched exactly');

-- P3: exact configuration references, matching inputs/candidates and business answer rows.

UPDATE `cut_cutover_checklist` r 
SET r.`source_task_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.input_snapshot, '$.taskVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.input_snapshot, '$.taskVersion')) END) AS SIGNED),
    r.`project_scope_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.input_snapshot, '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.input_snapshot, '$.projectScopeVersion')) END) AS SIGNED),
    r.`configuration_revision_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.input_snapshot, '$.configurationRevisionId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.input_snapshot, '$.configurationRevisionId')) END) AS SIGNED),
    r.`configuration_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.input_snapshot, '$.configurationCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.input_snapshot, '$.configurationCode')) END,
    r.`configuration_revision_no` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.input_snapshot, '$.configurationRevisionNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.input_snapshot, '$.configurationRevisionNo')) END) AS SIGNED),
    r.`config_gap_reason_code` = IF(CASE JSON_TYPE(JSON_EXTRACT(r.match_trace, '$.gap')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.match_trace, '$.gap') = CAST('true' AS JSON) ELSE NULL END,'NO_APPLICABLE_RULE',NULL),
    r.`update_time` = r.update_time
WHERE 1=1;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist r LEFT JOIN cut_cutover_configuration_revision c ON c.tenant_id=r.tenant_id AND c.id=r.configuration_revision_id AND c.revision_no=r.configuration_revision_no AND c.configuration_code=r.configuration_code WHERE c.id IS NULL) = 0), 'Checklist has no exact frozen configuration revision');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist_item i LEFT JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=i.tenant_id AND d.id=i.item_definition_id AND d.item_definition_version=i.item_definition_version WHERE i.source_code<>'CUSTOM' AND (d.id IS NULL OR NOT(i.interface_schema_snapshot <=> d.interface_schema))) = 0), 'Checklist schema differs from its referenced immutable definition');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist r JOIN JSON_TABLE(JSON_KEYS(JSON_EXTRACT(r.input_snapshot, '$.dimensions')),'$[*]' COLUMNS(dimension_code VARCHAR(64) PATH '$')) k ON TRUE LEFT JOIN cut_configuration_dimension d ON d.tenant_id=r.tenant_id AND d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code=k.dimension_code COLLATE utf8mb4_0900_ai_ci WHERE d.id IS NULL) = 0), 'Checklist input has no exact configuration dimension');

INSERT INTO `cut_checklist_input_dimension` (`id`, `tenant_id`, `checklist_id`, `configuration_dimension_id`, `dimension_code`, `value_no`, `value_type_code`, `value_text`, `value_number`, `value_boolean`, `value_datetime`, `create_user_id`, `create_time`)
SELECT 7800021000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,k.dimension_code,a.seq),
       r.tenant_id,
       r.id,
       d.id,
       k.dimension_code,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']'))) IN ('INTEGER','DOUBLE','DECIMAL') THEN 'NUMBER' WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']')))='STRING' THEN 'STRING' WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']')))='BOOLEAN' THEN 'BOOLEAN' ELSE 'NULL' END,
       IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']')))='STRING', JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']'))), NULL),
       IF(JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']'))) IN ('INTEGER','DOUBLE','DECIMAL'), CAST(JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']'))) AS DECIMAL(65,30)), NULL),
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']'))) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), CONCAT('$[', a.seq-1, ']')) = CAST('true' AS JSON) ELSE NULL END,
       NULL,
       CAST(IF(r.creator REGEXP '^[1-9][0-9]*$', r.creator, NULL) AS SIGNED),
       r.create_time
FROM cut_cutover_checklist r JOIN JSON_TABLE(JSON_KEYS(JSON_EXTRACT(r.input_snapshot, '$.dimensions')),'$[*]' COLUMNS(dimension_code VARCHAR(64) PATH '$')) k ON TRUE JOIN cut_configuration_dimension d ON d.tenant_id=r.tenant_id AND d.configuration_revision_id=r.configuration_revision_id AND d.dimension_code=k.dimension_code COLLATE utf8mb4_0900_ai_ci
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(JSON_EXTRACT(r.input_snapshot, '$.dimensions'),CONCAT('$."',k.dimension_code,'"')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

CREATE TEMPORARY TABLE cut_v475_matches AS
SELECT c.tenant_id,c.id AS checklist_id,b.id AS binding_rule_id,b.version AS binding_rule_version,b.priority,
       b.item_definition_id,b.required_result,d.stable_item_key,
       c.creator,c.create_time,c.updater,c.update_time,c.deleted
FROM cut_cutover_checklist c
JOIN cut_cutover_checklist_binding_rule_revision b ON b.tenant_id=c.tenant_id AND b.configuration_revision_id=c.configuration_revision_id
JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=b.tenant_id AND d.id=b.item_definition_id
WHERE JSON_LENGTH(b.dimension_condition_snapshot)>0
AND NOT EXISTS (
 SELECT 1 FROM JSON_TABLE(JSON_KEYS(b.dimension_condition_snapshot),'$[*]' COLUMNS(dimension_code VARCHAR(64) PATH '$')) k
 WHERE NOT EXISTS (
  SELECT 1 FROM cut_checklist_binding_condition bc
  JOIN cut_configuration_dimension dimension ON dimension.tenant_id=bc.tenant_id AND dimension.id=bc.configuration_dimension_id
  JOIN cut_checklist_input_dimension v ON v.tenant_id=c.tenant_id AND v.checklist_id=c.id
      AND UPPER(TRIM(v.dimension_code))=UPPER(TRIM(k.dimension_code)) COLLATE utf8mb4_0900_ai_ci
  WHERE bc.tenant_id=b.tenant_id AND bc.binding_rule_revision_id=b.id
      AND dimension.dimension_code=k.dimension_code COLLATE utf8mb4_0900_ai_ci
      AND UPPER(TRIM(COALESCE(bc.value_text,CAST(bc.value_number AS CHAR),IF(bc.value_boolean,'true','false')))) COLLATE utf8mb4_0900_ai_ci
         =UPPER(TRIM(COALESCE(v.value_text,CAST(v.value_number AS CHAR),IF(v.value_boolean,'true','false')))) COLLATE utf8mb4_0900_ai_ci
  )
);
CREATE TEMPORARY TABLE cut_v475_match_priority AS SELECT tenant_id,checklist_id,stable_item_key,MAX(priority) AS priority
FROM cut_v475_matches GROUP BY tenant_id,checklist_id,stable_item_key;

INSERT INTO `cut_checklist_match_candidate` (`id`, `tenant_id`, `checklist_id`, `stable_item_key`, `item_definition_revision_id`, `binding_rule_revision_id`, `binding_rule_version`, `priority`, `required_result_flag`, `selected_flag`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800022000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.checklist_id,r.binding_rule_id),
       r.tenant_id,
       r.checklist_id,
       r.stable_item_key,
       r.item_definition_id,
       r.binding_rule_id,
       r.binding_rule_version,
       r.priority,
       r.required_result,
       r.priority=p.priority AND EXISTS(SELECT 1 FROM cut_cutover_checklist_item i WHERE i.tenant_id=r.tenant_id AND i.checklist_id=r.checklist_id AND i.item_definition_id=r.item_definition_id AND i.applicable_flag=b'1'),
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_v475_matches r JOIN cut_v475_match_priority p ON p.tenant_id=r.tenant_id AND p.checklist_id=r.checklist_id AND p.stable_item_key=r.stable_item_key;

INSERT INTO `cut_checklist_device_context` (`id`, `tenant_id`, `checklist_id`, `assessment_device_id`, `device_id`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800023000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,d.sort_order,d.id),
       r.tenant_id,
       r.id,
       d.id,
       d.device_id,
       d.sort_order,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_cutover_checklist r JOIN cut_assessment_device d ON d.tenant_id=r.tenant_id AND d.assessment_id=r.assessment_id;

UPDATE `cut_cutover_checklist_item_result` r JOIN cut_cutover_checklist_item i ON i.tenant_id=r.tenant_id AND i.id=r.checklist_item_id JOIN cut_checklist_field_definition f ON f.tenant_id=i.tenant_id AND f.parent_field_id IS NULL AND ((i.source_code='CUSTOM' AND f.custom_checklist_item_id=i.id) OR (i.source_code<>'CUSTOM' AND f.item_definition_revision_id=i.item_definition_id))
SET r.`field_definition_id` = f.id,
    r.`device_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.deviceId')) END) AS SIGNED),
    r.`command_template_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.commandTemplateId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.commandTemplateId')) END) AS SIGNED),
    r.`collection_status_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.technicalStatus')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.technicalStatus')) END,
    r.`collection_failure_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.failureCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.failureCode')) END,
    r.`original_failed_collection_task_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailedCollectionTaskId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailedCollectionTaskId')) END) AS SIGNED),
    r.`original_failed_result_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailedResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailedResultVersion')) END) AS SIGNED),
    r.`original_failure_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailureCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.answer_snapshot, '$.originalFailureCode')) END
WHERE 1=1;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist_item_result WHERE field_definition_id IS NULL) = 0), 'Result has no exact field definition root');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist_item_result WHERE result_source_code='EXTERNAL') = 0), 'Legacy EXTERNAL result lacks the approved per-device query evidence; retain original and resolve');

UPDATE cut_cutover_checklist_item_result r
JOIN cut_cutover_checklist_item_result b ON b.tenant_id=r.tenant_id AND b.checklist_item_id=r.checklist_item_id
 AND b.collection_task_id=r.collection_task_id AND b.result_version<r.result_version
 AND JSON_CONTAINS_PATH(b.answer_snapshot,'one','$.analysis')=0
 AND b.result_version=(SELECT MAX(candidate.result_version) FROM (SELECT tenant_id,checklist_item_id,collection_task_id,result_version,answer_snapshot FROM cut_cutover_checklist_item_result) candidate
 WHERE candidate.tenant_id=r.tenant_id AND candidate.checklist_item_id=r.checklist_item_id AND candidate.collection_task_id=r.collection_task_id
 AND candidate.result_version<r.result_version AND JSON_CONTAINS_PATH(candidate.answer_snapshot,'one','$.analysis')=0)
SET r.base_checklist_result_id=b.id
WHERE r.result_source_code='COLLECTION' AND JSON_CONTAINS_PATH(r.answer_snapshot,'one','$.analysis');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_cutover_checklist_item_result WHERE result_source_code='COLLECTION' AND JSON_CONTAINS_PATH(answer_snapshot,'one','$.analysis') AND base_checklist_result_id IS NULL) = 0), 'Collection analysis has no original result to reference');

CREATE TEMPORARY TABLE cut_v475_answers AS
SELECT r.*,i.interface_format_code,f.builtin_field_code AS family,
 CASE WHEN r.result_source_code='COLLECTION' THEN JSON_EXTRACT(r.answer_snapshot,'$.analysis.value')
      WHEN JSON_CONTAINS_PATH(r.answer_snapshot,'one','$.value') THEN JSON_EXTRACT(r.answer_snapshot,'$.value')
      ELSE r.answer_snapshot END AS value_json
FROM cut_cutover_checklist_item_result r
JOIN cut_cutover_checklist_item i ON i.tenant_id=r.tenant_id AND i.id=r.checklist_item_id
JOIN cut_checklist_field_definition f ON f.tenant_id=r.tenant_id AND f.id=r.field_definition_id
WHERE r.result_source_code='DIRECT' OR (r.result_source_code='COLLECTION' AND r.base_checklist_result_id IS NOT NULL);
UPDATE cut_v475_answers SET value_json=CAST(JSON_UNQUOTE(value_json) AS JSON)
WHERE interface_format_code='TABLE' AND JSON_TYPE(value_json)='STRING';

INSERT INTO `cut_result_cutover_background` (`id`, `tenant_id`, `checklist_result_id`, `solves_online_issue_flag`, `issue_ticket_no`, `issue_handler`, `repeat_cutover_flag`, `first_cutover_owner`, `background_description`, `create_user_id`, `create_time`)
SELECT 7800028000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.solvesOnlineIssue')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.solvesOnlineIssue') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.issueTicketNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.issueTicketNo')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.issueHandler')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.issueHandler')) END,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.repeatCutover')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.repeatCutover') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.firstCutoverOwner')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.firstCutoverOwner')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.backgroundDescription')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.backgroundDescription')) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='CUTOVER_BACKGROUND' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_business_summary` (`id`, `tenant_id`, `checklist_result_id`, `business_description`, `create_user_id`, `create_time`)
SELECT 7800029000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(r.value_json) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(r.value_json) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='BUSINESS_SUMMARY' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_impact_scope` (`id`, `tenant_id`, `checklist_result_id`, `impact_scope_code`, `create_user_id`, `create_time`)
SELECT 7800030000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(r.value_json) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(r.value_json) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='IMPACT_SCOPE' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_continuity_requirement` (`id`, `tenant_id`, `checklist_result_id`, `continuity_requirement_code`, `create_user_id`, `create_time`)
SELECT 7800031000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(r.value_json) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(r.value_json) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='CONTINUITY_REQUIREMENT' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_interruption` (`id`, `tenant_id`, `checklist_result_id`, `interruption_count`, `create_user_id`, `create_time`)
SELECT 7800032000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CAST(CASE WHEN JSON_TYPE(r.value_json) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(r.value_json) END AS DECIMAL(65,30)),
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='INTERRUPTION_COUNT' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_spare_reference` (`id`, `tenant_id`, `checklist_result_id`, `major_cutover_flag`, `spare_reference_text`, `create_user_id`, `create_time`)
SELECT 7800034000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.majorCutover')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.majorCutover') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.spareReference')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.spareReference')) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family='MAJOR_PROJECT_SPARES' AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_bulletin_summary` (`id`, `tenant_id`, `checklist_result_id`, `has_bulletins_flag`, `manual_query_basis`, `create_user_id`, `create_time`)
SELECT 7800035000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.hasBulletins')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.hasBulletins') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.queryBasis')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.queryBasis')) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family IN ('CURRENT_VERSION_BULLETIN','TARGET_VERSION_BULLETIN') AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_check_feedback` (`id`, `tenant_id`, `checklist_result_id`, `check_normal_flag`, `executed_flag`, `remark`, `create_user_id`, `create_time`)
SELECT 7800033000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.normal')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.normal') = CAST('true' AS JSON) ELSE NULL END,
       CASE JSON_TYPE(JSON_EXTRACT(r.value_json, '$.executed')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.value_json, '$.executed') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.value_json, '$.remark')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.value_json, '$.remark')) END,
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family IN ('ADWARE_DEFAULT','COMPILE_LIMIT_ASSESSMENT','DIAGNOSTIC_LOG','DUAL_CONFIG_CONSISTENCY','DUAL_CONTROLLER_VERSION','DUAL_MACHINE_CHECK','DYNAMIC_TABLE_COLLECTION','FILTER_NAT_QOS_COMPILE_COUNT','HUNDRED_G_FEC','LONG_CONNECTION','MTU_JUMBO_FRAME','SECOND_PASS_DEVICE','SESSION_SYNC','STP','SYSTEM_LOG') AND r.interface_format_code<>'FILE';

INSERT INTO `cut_result_device_location` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `device_type`, `sn`, `device_name`, `location`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800036000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceType')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceName')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.location')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.location')) END,
       (SELECT JSON_OBJECTAGG(e.extension_field_key, JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),CONCAT('$."',e.field_code,'"'))) FROM cut_checklist_field_definition e WHERE e.tenant_id=r.tenant_id AND e.parent_field_id=r.field_definition_id AND e.extension_field_key IS NOT NULL AND JSON_CONTAINS_PATH(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),'one',CONCAT('$."',e.field_code,'"'))),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
WHERE r.family='DEVICE_LOCATION_PLAN';

INSERT INTO `cut_result_interface_plan` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `local_device`, `local_interface`, `remote_interface`, `interface_type`, `cable_model`, `remark`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800037000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.localDevice')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.localDevice')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.localInterface')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.localInterface')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remoteInterface')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remoteInterface')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.interfaceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.interfaceType')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.cableModel')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.cableModel')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remark')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remark')) END,
       (SELECT JSON_OBJECTAGG(e.extension_field_key, JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),CONCAT('$."',e.field_code,'"'))) FROM cut_checklist_field_definition e WHERE e.tenant_id=r.tenant_id AND e.parent_field_id=r.field_definition_id AND e.extension_field_key IS NOT NULL AND JSON_CONTAINS_PATH(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),'one',CONCAT('$."',e.field_code,'"'))),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
WHERE r.family='INTERFACE_INTERCONNECT_PLAN';

INSERT INTO `cut_result_ip_plan` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `device_name`, `interface_name`, `ip_address`, `subnet_mask`, `vrrp_ip`, `gateway`, `remark`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800038000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.deviceName')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.interface')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.interface')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.ip')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.ip')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.mask')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.mask')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.vrrpIp')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.vrrpIp')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.gateway')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.gateway')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remark')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.remark')) END,
       (SELECT JSON_OBJECTAGG(e.extension_field_key, JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),CONCAT('$."',e.field_code,'"'))) FROM cut_checklist_field_definition e WHERE e.tenant_id=r.tenant_id AND e.parent_field_id=r.field_definition_id AND e.extension_field_key IS NOT NULL AND JSON_CONTAINS_PATH(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),'one',CONCAT('$."',e.field_code,'"'))),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
WHERE r.family='IP_VLAN_PLAN';

INSERT INTO `cut_result_performance` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `sn`, `concurrent_connections`, `new_connections`, `cpu_usage`, `memory_usage`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800039000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.concurrentConnections')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.concurrentConnections')) END AS DECIMAL(65,30)),
       CAST(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.newConnections')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.newConnections')) END AS DECIMAL(65,30)),
       CAST(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.cpuUsage')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.cpuUsage')) END AS DECIMAL(65,30)),
       CAST(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.memoryUsage')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.memoryUsage')) END AS DECIMAL(65,30)),
       (SELECT JSON_OBJECTAGG(e.extension_field_key, JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),CONCAT('$."',e.field_code,'"'))) FROM cut_checklist_field_definition e WHERE e.tenant_id=r.tenant_id AND e.parent_field_id=r.field_definition_id AND e.extension_field_key IS NOT NULL AND JSON_CONTAINS_PATH(JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),'one',CONCAT('$."',e.field_code,'"'))),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
WHERE r.family='PERFORMANCE_BASELINE';

INSERT INTO `cut_result_technical_bulletin` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `bulletin_number`, `bulletin_subject`, `bulletin_description`, `bulletin_solution`, `involved_flag`, `measures`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800040000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       f.id,
       a.seq-1,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.number')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.number')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.subject')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.subject')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.description')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.description')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.solution')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.solution')) END,
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.involved')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.involved') = CAST('true' AS JSON) ELSE NULL END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.measures')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')), '$.measures')) END,
       (SELECT JSON_OBJECTAGG(e.extension_field_key, JSON_EXTRACT(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')),CONCAT('$."',e.field_code,'"'))) FROM cut_checklist_field_definition e WHERE e.tenant_id=r.tenant_id AND e.parent_field_id=f.id AND e.extension_field_key IS NOT NULL AND JSON_CONTAINS_PATH(JSON_EXTRACT(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), CONCAT('$[', a.seq-1, ']')),'one',CONCAT('$."',e.field_code,'"'))),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(JSON_EXTRACT(r.value_json, '$.bulletins'))='ARRAY',JSON_EXTRACT(r.value_json, '$.bulletins'),JSON_EXTRACT(JSON_EXTRACT(r.value_json, '$.bulletins'),'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
JOIN cut_checklist_field_definition f ON f.tenant_id=r.tenant_id AND f.parent_field_id=r.field_definition_id AND f.field_code='bulletins'
WHERE r.family IN ('CURRENT_VERSION_BULLETIN','TARGET_VERSION_BULLETIN');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_v475_answers r JOIN cut_checklist_field_definition f ON f.tenant_id=r.tenant_id AND (f.item_definition_revision_id=(SELECT item_definition_revision_id FROM cut_checklist_field_definition WHERE id=r.field_definition_id) OR f.custom_checklist_item_id=(SELECT custom_checklist_item_id FROM cut_checklist_field_definition WHERE id=r.field_definition_id)) WHERE f.parent_field_id IS NOT NULL AND f.builtin_field_code IS NULL AND f.data_type_code IN ('FILE','TABLE') AND r.family IS NULL) = 0), 'Nested custom history needs an explicit source projection; no JSON relationship fallback');

INSERT INTO `cut_result_custom_value` (`id`, `tenant_id`, `checklist_result_id`, `field_definition_id`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800041000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       JSON_OBJECT('value',r.value_json),
       r.created_by,
       r.created_at
FROM cut_v475_answers r WHERE r.family IS NULL AND r.interface_format_code NOT IN ('FILE','TABLE') AND JSON_TYPE(r.value_json)<>'NULL';

INSERT INTO `cut_result_custom_row` (`id`, `tenant_id`, `checklist_result_id`, `table_field_definition_id`, `row_no`, `extension_values`, `create_user_id`, `create_time`)
SELECT 7800042000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       a.seq-1,
       JSON_EXTRACT(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), CONCAT('$[', a.seq-1, ']')),
       r.created_by,
       r.created_at
FROM cut_v475_answers r
JOIN JSON_TABLE(COALESCE(IF(JSON_TYPE(r.value_json)='ARRAY',r.value_json,JSON_EXTRACT(r.value_json,'$.rows')), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
WHERE r.family IS NULL AND r.interface_format_code='TABLE';

CREATE TEMPORARY TABLE cut_v475_result_files AS
SELECT r.tenant_id,r.id,r.field_definition_id,r.created_by,r.created_at,'MANUAL_EVIDENCE' AS file_role,
       JSON_EXTRACT(r.answer_snapshot,'$.file') AS file_json
FROM cut_cutover_checklist_item_result r WHERE r.result_source_code='MANUAL'
UNION ALL
SELECT tenant_id,id,field_definition_id,created_by,created_at,'RESULT_FILE',value_json
FROM cut_v475_answers WHERE interface_format_code='FILE' AND JSON_TYPE(value_json)<>'NULL';

INSERT INTO `cut_checklist_result_file` (`id`, `tenant_id`, `checklist_result_id`, `field_definition_id`, `file_role_code`, `file_no`, `file_artifact_id`, `file_version_no`, `file_reference_key`, `file_artifact_fact_version`, `file_reference_fact_version`, `file_availability_fact_version`, `file_scope_version`, `file_sha256`, `create_user_id`, `create_time`)
SELECT 7800024000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id),
       r.tenant_id,
       r.id,
       r.field_definition_id,
       r.file_role,
       0,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.artifactId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.artifactId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.versionNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.versionNo')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.referenceKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.referenceKey')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.artifactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.artifactVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.referenceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.referenceVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.availabilityVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.fileFactVersion.availabilityVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.scopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.scopeVersion')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_json, '$.sha256')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_json, '$.sha256')) END,
       r.created_by,
       r.created_at
FROM cut_v475_result_files r;

DROP TEMPORARY TABLE cut_v475_matches;
DROP TEMPORARY TABLE cut_v475_match_priority;
DROP TEMPORARY TABLE cut_v475_answers;
DROP TEMPORARY TABLE cut_v475_result_files;

-- P4/P5: immutable revision references and original approval qualifications.

UPDATE `cut_plan_revision` r JOIN cut_task t ON t.tenant_id=r.tenant_id AND t.id=r.cutover_task_id
SET r.`source_task_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.taskVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.taskVersion')) END) AS SIGNED),
    r.`source_project_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.projectId')) END) AS SIGNED),
    r.`source_project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.projectVersion')) END) AS SIGNED),
    r.`source_project_scope_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.projectScopeVersion')) END) AS SIGNED),
    r.`source_cutover_type_code` = t.cutover_type,
    r.`project_description` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.content_snapshot, '$.overview.projectDescription')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.content_snapshot, '$.overview.projectDescription')) END,
    r.`rollback_description` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.content_snapshot, '$.rollbackDescription')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.content_snapshot, '$.rollbackDescription')) END,
    r.`customer_contact_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.content_snapshot, '$.customerContactReference.contactId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.content_snapshot, '$.customerContactReference.contactId')) END) AS SIGNED),
    r.`customer_contact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.content_snapshot, '$.customerContactReference.contactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.content_snapshot, '$.customerContactReference.contactVersion')) END) AS SIGNED),
    r.`file_artifact_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) END) AS SIGNED),
    r.`file_reference_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) END) AS SIGNED),
    r.`file_availability_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) END) AS SIGNED),
    r.`update_time` = r.update_time
WHERE r.origin_code<>'LEGACY_FORWARD';

UPDATE `cut_plan_revision` r 
SET r.`legacy_task_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.sourceTaskId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.sourceTaskId')) END) AS SIGNED),
    r.`legacy_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.code')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.code')) END,
    r.`legacy_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.name')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.name')) END,
    r.`legacy_grade_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.level')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.level')) END,
    r.`legacy_remark` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.remark')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.remark')) END,
    r.`update_time` = r.update_time
WHERE r.origin_code='LEGACY_FORWARD';

UPDATE `cut_step` r JOIN cut_plan_revision p ON p.tenant_id=r.tenant_id AND p.id=r.plan_revision_id
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
SET r.`step_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.stepName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.stepName')) END,
    r.`test_result` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.testResult')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.testResult')) END,
    r.`business_impact_time` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.businessImpactTime')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.businessImpactTime')) END,
    r.`operator_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.operator')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.operator')) END,
    r.`operation_time` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.operationTime')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.details.operationTime')) END,
    r.`update_time` = r.update_time
WHERE r.section_code=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.sectionCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.sectionCode')) END AND r.step_no=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.stepNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(p.content_snapshot, '$.stepDetails'), CONCAT('$[', a.seq-1, ']')), '$.stepNo')) END) AS SIGNED);

INSERT INTO `cut_plan_schedule` (`id`, `tenant_id`, `plan_revision_id`, `sequence_no`, `plan_time`, `content`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800046000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.sequenceNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.sequenceNo')) END) AS SIGNED),
       TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.plannedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.plannedAt')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.content')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), CONCAT('$[', a.seq-1, ']')), '$.content')) END,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_plan_revision r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.content_snapshot, '$.overview.scheduleTable'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_plan_revision r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.source_snapshot, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_assessment_device d ON d.tenant_id=r.tenant_id AND d.assessment_id=r.assessment_id AND d.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED) AND d.project_assignment_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED) AND d.sn=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END WHERE r.origin_code<>'LEGACY_FORWARD' AND d.id IS NULL) = 0), 'Plan device has no matching frozen assessment device');

INSERT INTO `cut_plan_device` (`id`, `tenant_id`, `plan_revision_id`, `assessment_device_id`, `device_id`, `sn`, `project_assignment_version`, `device_type_code`, `device_type_source_version`, `product_model`, `current_software_version`, `target_software_version`, `remark`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800047000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       d.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeCode')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeSourceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceTypeSourceVersion')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.productModel')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.productModel')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.currentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.currentVersion')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.targetVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.targetVersion')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.remark')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT((SELECT JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),CONCAT('$[',dd.seq-1,']')) FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.overview.deviceDetails'),'$[*]' COLUMNS(seq FOR ORDINALITY,device_id BIGINT PATH '$.deviceId')) dd WHERE dd.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)), '$.remark')) END,
       a.seq-1,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_plan_revision r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.source_snapshot, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_assessment_device d ON d.tenant_id=r.tenant_id AND d.assessment_id=r.assessment_id AND d.device_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED) AND d.project_assignment_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED) AND d.sn=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END;

INSERT INTO `cut_plan_file` (`id`, `tenant_id`, `plan_revision_id`, `file_role_code`, `file_artifact_id`, `file_version_no`, `file_reference_key`, `file_artifact_fact_version`, `file_reference_fact_version`, `file_availability_fact_version`, `file_scope_version`, `file_sha256`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800048000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,slot.code),
       r.tenant_id,
       r.id,
       CASE slot.code WHEN 'preTopologyFile' THEN 'PRE_TOPOLOGY' WHEN 'postTopologyFile' THEN 'POST_TOPOLOGY' ELSE 'NETWORK_CONFIGURATION' END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.artifactId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.artifactId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.versionNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.versionNo')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.referenceKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.referenceKey')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.artifactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.artifactVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.referenceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.referenceVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.availabilityVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.fileFactVersion.availabilityVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.scopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.scopeVersion')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.sha256')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)), '$.sha256')) END,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_plan_revision r JOIN JSON_TABLE('["preTopologyFile","postTopologyFile","networkConfigurationFile"]','$[*]' COLUMNS(code VARCHAR(64) PATH '$')) slot ON TRUE WHERE JSON_TYPE(JSON_EXTRACT(r.content_snapshot,CONCAT('$.overview.',slot.code)))='OBJECT';

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_plan_revision r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_cutover_checklist_item_result result ON result.tenant_id=r.tenant_id AND result.checklist_item_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED) AND result.result_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) END) AS SIGNED) WHERE result.id IS NULL) = 0), 'Plan risk points to a missing exact result version');

INSERT INTO `cut_plan_risk_mitigation` (`id`, `tenant_id`, `plan_revision_id`, `checklist_result_id`, `checklist_item_id`, `checklist_result_version`, `mitigation`, `sort_order`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 7800049000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       result.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) END) AS SIGNED),
       (SELECT m.mitigation FROM JSON_TABLE(JSON_EXTRACT(r.content_snapshot, '$.riskMitigations'),'$[*]' COLUMNS(item_id BIGINT PATH '$.riskFact.checklistItemId',mitigation TEXT PATH '$.mitigation')) m WHERE m.item_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED)),
       a.seq-1,
       r.creator,
       r.create_time,
       r.updater,
       r.update_time,
       r.deleted
FROM cut_plan_revision r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_cutover_checklist_item_result result ON result.tenant_id=r.tenant_id AND result.checklist_item_id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED) AND result.result_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.failedRiskFacts'), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) END) AS SIGNED);

UPDATE `cut_approval_instance` r 
SET r.`project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectVersion')) END) AS SIGNED),
    r.`project_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectCode')) END,
    r.`project_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectName')) END,
    r.`customer_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerId')) END) AS SIGNED),
    r.`customer_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerCode')) END,
    r.`customer_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.customerName')) END,
    r.`department_id` = COALESCE(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentId')) END) AS SIGNED),CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeDepartmentId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeDepartmentId')) END) AS SIGNED)),
    r.`department_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentCode')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeCode')) END),
    r.`department_name` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.departmentName')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.officeName')) END),
    r.`project_scope_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.source_snapshot, '$.project'), '$.projectScopeVersion')) END) AS SIGNED),
    r.`source_task_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.taskVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.taskVersion')) END) AS SIGNED),
    r.`source_plan_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.plan.planVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.plan.planVersion')) END) AS SIGNED),
    r.`source_cutover_type_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.cutoverType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.cutoverType')) END,
    r.`source_network_mode_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.networkMode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.networkMode')) END,
    r.`source_schedule_time` = TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.scheduledTime')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.source_snapshot, '$.collectionAnalysis.scheduledTime')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')),
    r.`lead_rule_version` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.ruleVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.lead_time_snapshot, '$.ruleVersion')) END,
    r.`lead_timezone_id` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.timezoneId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.lead_time_snapshot, '$.timezoneId')) END,
    r.`lead_plan_submit_time` = TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.planSubmittedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.lead_time_snapshot, '$.planSubmittedAt')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')),
    r.`lead_required_days` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.requiredDays')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.lead_time_snapshot, '$.requiredDays')) END) AS SIGNED),
    r.`lead_actual_natural_days` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.actualNaturalDays')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.lead_time_snapshot, '$.actualNaturalDays')) END) AS SIGNED),
    r.`lead_late_submission_flag` = CASE JSON_TYPE(JSON_EXTRACT(r.lead_time_snapshot, '$.lateSubmission')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.lead_time_snapshot, '$.lateSubmission') = CAST('true' AS JSON) ELSE NULL END,
    r.`update_time` = r.update_time
WHERE 1=1;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_approval_instance r LEFT JOIN cut_plan_revision p ON p.tenant_id=r.tenant_id AND p.id=r.plan_revision_id AND p.revision_no=r.plan_revision_no AND p.cutover_task_id=r.task_id LEFT JOIN cut_assessment a ON a.tenant_id=r.tenant_id AND a.id=r.assessment_id AND a.assessment_version=r.assessment_version AND a.cutover_task_id=r.task_id WHERE p.id IS NULL OR a.id IS NULL) = 0), 'Approval immutable plan or assessment reference is missing');

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_approval_instance r
JOIN JSON_TABLE(COALESCE(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=r.tenant_id AND i.id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED) AND i.checklist_id=r.checklist_id
LEFT JOIN cut_cutover_checklist_item_result result ON result.tenant_id=r.tenant_id AND result.checklist_item_id=i.id AND result.result_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) END) AS SIGNED) WHERE result.id IS NULL OR i.stable_item_key<>CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.stableItemKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.stableItemKey')) END OR NOT(result.answer_snapshot <=> CAST(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.answerSnapshot')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.answerSnapshot')) END AS JSON))) = 0), 'Approval answer differs from the referenced result; original evidence is retained');

INSERT INTO `cut_approval_result_reference` (`id`, `tenant_id`, `approval_instance_id`, `checklist_item_id`, `checklist_result_id`, `result_version`, `required_flag`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800057000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       i.id,
       result.id,
       result.result_version,
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.required') = CAST('true' AS JSON) ELSE NULL END,
       a.seq-1,
       r.initiator_user_id,
       r.create_time
FROM cut_approval_instance r
JOIN JSON_TABLE(COALESCE(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=r.tenant_id AND i.id=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.checklistItemId')) END) AS SIGNED) AND i.checklist_id=r.checklist_id
LEFT JOIN cut_cutover_checklist_item_result result ON result.tenant_id=r.tenant_id AND result.checklist_item_id=i.id AND result.result_version=CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_MERGE_PRESERVE(COALESCE(JSON_EXTRACT(r.source_snapshot,'$.riskItems'),JSON_ARRAY()),COALESCE(JSON_EXTRACT(r.source_snapshot,'$.businessSurveyItems'),JSON_ARRAY())), CONCAT('$[', a.seq-1, ']')), '$.itemResultVersion')) END) AS SIGNED);

UPDATE `cut_approval_node` r 
SET r.`qualification_kind_code` = CASE r.node_code WHEN 'INITIATOR' THEN 'PROJECT_SCOPE' WHEN 'SERVICE_MANAGER' THEN 'SERVICE_MANAGER' ELSE IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) END='ROLE',NULL,'ROLE_PROJECT_SCOPE') END,
    r.`qualification_outcome_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.outcome')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.outcome')) END,
    r.`qualification_role_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleCode')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) END),
    r.`qualification_project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectVersion')) END) AS SIGNED),
    r.`qualification_participant_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.participantFactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.participantFactVersion')) END) AS SIGNED),
    r.`qualification_required_action_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) END,
    r.`qualification_allowed_flag` = CASE WHEN JSON_CONTAINS_PATH(r.candidate_fact_snapshot,'one','$.allowed') THEN CASE JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.allowed')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.candidate_fact_snapshot, '$.allowed') = CAST('true' AS JSON) ELSE NULL END WHEN CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.userId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.userId')) END) AS SIGNED)>0 AND CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) END IS NOT NULL THEN 1 ELSE NULL END,
    r.`qualification_check_time` = CASE JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) WHEN 'INTEGER' THEN TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')) WHEN 'STRING' THEN CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) END), 'T', ' ') AS DATETIME(3)) ELSE NULL END,
    r.`approval_mode_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) END,'PERSONAL'),
    r.`candidate_role_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) END) AS SIGNED),
    r.`candidate_role_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) END,
    r.`candidate_group_key` = IF(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) END) AS SIGNED) IS NULL,NULL,CONCAT('ROLE:',CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupId')) END) AS SIGNED))),
    r.`decision_actor_user_id` = IF(r.status_code IN ('APPROVED','REJECTED'),r.current_approver_user_id,NULL),
    r.`decision_role_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.roleId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.roleId')) END) AS SIGNED),
    r.`decision_membership_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.membershipVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.membershipVersion')) END) AS SIGNED),
    r.`decision_user_status_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.userStatusVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.userStatusVersion')) END) AS SIGNED),
    r.`decision_check_time` = CASE JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.checkedAt')) WHEN 'INTEGER' THEN TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.checkedAt')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')) WHEN 'STRING' THEN CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.decisionActor.checkedAt')) END), 'T', ' ') AS DATETIME(3)) ELSE NULL END,
    r.`update_time` = r.update_time
WHERE 1=1;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_approval_node WHERE approval_mode_code='ROLE' AND (candidate_role_id IS NULL OR candidate_role_code IS NULL)) = 0), 'Historical role approval has no exact role identity');

UPDATE `cut_approval_reassignment` r JOIN cut_approval_node n ON n.tenant_id=r.tenant_id AND n.id=r.approval_node_id
SET r.`qualification_kind_code` = CASE n.node_code WHEN 'INITIATOR' THEN 'PROJECT_SCOPE' WHEN 'SERVICE_MANAGER' THEN 'SERVICE_MANAGER' ELSE IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.approvalMode')) END='ROLE',NULL,'ROLE_PROJECT_SCOPE') END,
    r.`qualification_outcome_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.outcome')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.outcome')) END,
    r.`qualification_role_code` = COALESCE(CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleCode')) END,CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.roleGroupCode')) END),
    r.`qualification_project_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectVersion')) END) AS SIGNED),
    r.`qualification_participant_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.participantFactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.participantFactVersion')) END) AS SIGNED),
    r.`qualification_required_action_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) END,
    r.`qualification_allowed_flag` = CASE WHEN JSON_CONTAINS_PATH(r.candidate_fact_snapshot,'one','$.allowed') THEN CASE JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.allowed')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.candidate_fact_snapshot, '$.allowed') = CAST('true' AS JSON) ELSE NULL END WHEN CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.userId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.userId')) END) AS SIGNED)>0 AND CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.requiredAction')) END IS NOT NULL THEN 1 ELSE NULL END,
    r.`qualification_check_time` = CASE JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) WHEN 'INTEGER' THEN TIMESTAMPADD(MICROSECOND, CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) END) AS SIGNED) * 1000, TIMESTAMP('1970-01-01 08:00:00')) WHEN 'STRING' THEN CAST(REPLACE((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.checkedAt')) END), 'T', ' ') AS DATETIME(3)) ELSE NULL END,
    r.`project_scope_version` = COALESCE(CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.treeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.treeVersion')) END) AS SIGNED), CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.candidate_fact_snapshot, '$.projectScopeVersion')) END) AS SIGNED)),
    r.`update_time` = r.update_time
WHERE 1=1;

-- P6, spare facts and the IMP/AST owner projections.

INSERT INTO `cut_closure_device` (`id`, `tenant_id`, `closure_id`, `device_id`, `project_id`, `sn`, `project_assignment_version`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800062000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(r.device_scope_watermark, CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED),
       a.seq-1,
       CAST(IF(r.creator REGEXP '^[1-9][0-9]*$', r.creator, NULL) AS SIGNED),
       r.create_time
FROM cut_cutover_closure r
JOIN JSON_TABLE(COALESCE(r.device_scope_watermark, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

CALL cut_v475_assert(((SELECT COUNT(*) FROM cut_closure_device d JOIN cut_cutover_closure c ON c.tenant_id=d.tenant_id AND c.id=d.closure_id LEFT JOIN cut_task_device_scope s ON s.tenant_id=c.tenant_id AND s.cutover_task_id=c.task_id AND s.device_id=d.device_id AND s.project_id=d.project_id AND s.project_assignment_version=d.project_assignment_version AND CAST(s.serial_number_snapshot AS BINARY)=CAST(d.sn AS BINARY) WHERE c.project_id<>d.project_id OR s.id IS NULL) = 0), 'Closure frozen device differs from the original task scope');

UPDATE `cut_cutover_closure_attachment` r 
SET r.`file_artifact_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) END) AS SIGNED),
    r.`file_reference_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) END) AS SIGNED),
    r.`file_availability_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) END) AS SIGNED),
    r.`update_time` = r.update_time
WHERE 1=1;

UPDATE `cut_spare_manual_evidence` r 
SET r.`file_artifact_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.artifactVersion')) END) AS SIGNED),
    r.`file_reference_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.referenceVersion')) END) AS SIGNED),
    r.`file_availability_fact_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.file_fact_version, '$.availabilityVersion')) END) AS SIGNED)
WHERE 1=1;

UPDATE `cut_collection_log` r 
SET r.`source_context_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.sourceContext')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.sourceContext')) END,
    r.`source_object_type_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.sourceObjectType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.sourceObjectType')) END,
    r.`project_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.projectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.projectId')) END) AS SIGNED),
    r.`actor_user_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.actorId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.actorId')) END) AS SIGNED),
    r.`protocol_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.protocol')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.protocol')) END,
    r.`external_status_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.externalStatus')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.externalStatus')) END,
    r.`failure_category_code` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.failureCategory')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.failureCategory')) END,
    r.`command_text` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.commandText')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.commandText')) END,
    r.`template_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.templateName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.templateName')) END,
    r.`update_time` = r.update_time
WHERE 1=1;

UPDATE `cut_spare_application_reference` r 
SET r.`need_required_flag` = CASE JSON_TYPE(JSON_EXTRACT(r.need_snapshot, '$.required')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(r.need_snapshot, '$.required') = CAST('true' AS JSON) ELSE NULL END,
    r.`task_no` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.request_context_snapshot, '$.taskNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.request_context_snapshot, '$.taskNo')) END,
    r.`source_task_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.request_context_snapshot, '$.taskVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.request_context_snapshot, '$.taskVersion')) END) AS SIGNED),
    r.`correlation_id` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.request_context_snapshot, '$.correlationId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.request_context_snapshot, '$.correlationId')) END,
    r.`update_time` = r.update_time
WHERE 1=1;

INSERT INTO `cut_spare_need_source` (`id`, `tenant_id`, `application_reference_id`, `source_type_code`, `assessment_id`, `assessment_version`, `checklist_item_id`, `checklist_item_version`, `spare_part_applied_flag`, `applicable_flag`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800066000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) END,
       IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) END='ASSESSMENT',CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceId')) END) AS SIGNED),NULL),
       IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) END='ASSESSMENT',CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceVersion')) END) AS SIGNED),NULL),
       IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) END='CHECKLIST_RISK',CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceId')) END) AS SIGNED),NULL),
       IF(CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceType')) END='CHECKLIST_RISK',CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sourceVersion')) END) AS SIGNED),NULL),
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sparePartApplied')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.sparePartApplied') = CAST('true' AS JSON) ELSE NULL END,
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.applicable')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.need_snapshot, '$.sources'), CONCAT('$[', a.seq-1, ']')), '$.applicable') = CAST('true' AS JSON) ELSE NULL END,
       a.seq-1,
       CAST(IF(r.creator REGEXP '^[1-9][0-9]*$', r.creator, NULL) AS SIGNED),
       r.create_time
FROM cut_spare_application_reference r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.need_snapshot, '$.sources'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `cut_spare_request_device` (`id`, `tenant_id`, `application_reference_id`, `device_id`, `sn`, `project_assignment_version`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800067000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED),
       a.seq-1,
       CAST(IF(r.creator REGEXP '^[1-9][0-9]*$', r.creator, NULL) AS SIGNED),
       r.create_time
FROM cut_spare_application_reference r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.request_context_snapshot, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

UPDATE `imp_cutover_readiness_snapshot` r 
SET r.`project_scope_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.context_json, '$.projectScopeVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.context_json, '$.projectScopeVersion')) END) AS SIGNED),
    r.`approved_plan_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.context_json, '$.approvedPlan.planId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.context_json, '$.approvedPlan.planId')) END) AS SIGNED),
    r.`approved_plan_version` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.context_json, '$.approvedPlan.planVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.context_json, '$.approvedPlan.planVersion')) END) AS SIGNED),
    r.`update_time` = r.update_time
WHERE 1=1;

INSERT INTO `imp_cutover_readiness_device` (`id`, `tenant_id`, `snapshot_id`, `device_id`, `sn`, `project_assignment_version`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800069000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.serialNumber')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.devices'), CONCAT('$[', a.seq-1, ']')), '$.projectAssignmentVersion')) END) AS SIGNED),
       a.seq-1,
       r.evaluated_by,
       r.create_time
FROM imp_cutover_readiness_snapshot r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_json, '$.devices'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `imp_cutover_readiness_source` (`id`, `tenant_id`, `snapshot_id`, `source_code`, `completion_status_code`, `fact_version`, `reopened_flag`, `create_user_id`, `create_time`)
SELECT 7800070000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.completionStatus')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.completionStatus')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.factVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.factVersion')) END) AS SIGNED),
       CASE JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.reopened')) WHEN 'BOOLEAN' THEN JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.reopened') = CAST('true' AS JSON) ELSE NULL END,
       r.evaluated_by,
       r.create_time
FROM imp_cutover_readiness_snapshot r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_json, '$.sourceFacts'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

INSERT INTO `imp_cutover_readiness_source_object` (`id`, `tenant_id`, `source_fact_id`, `source_object_id`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800071000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq,b.seq),
       r.tenant_id,
       s.id,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceObjectIds'), CONCAT('$[', b.seq-1, ']'))) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceObjectIds'), CONCAT('$[', b.seq-1, ']'))) END) AS SIGNED),
       b.seq-1,
       r.evaluated_by,
       r.create_time
FROM imp_cutover_readiness_snapshot r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_json, '$.sourceFacts'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
JOIN imp_cutover_readiness_source s ON s.tenant_id=r.tenant_id AND s.snapshot_id=r.id AND s.source_code=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) END
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceObjectIds'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) b ON TRUE;

INSERT INTO `imp_cutover_readiness_source_watermark` (`id`, `tenant_id`, `source_fact_id`, `axis_code`, `object_id`, `fact_version`, `create_user_id`, `create_time`)
SELECT 7800072000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq,b.seq),
       r.tenant_id,
       s.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.axisCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.axisCode')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.objectId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.objectId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.version')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), CONCAT('$[', b.seq-1, ']')), '$.version')) END) AS SIGNED),
       r.evaluated_by,
       r.create_time
FROM imp_cutover_readiness_snapshot r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.context_json, '$.sourceFacts'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE
JOIN imp_cutover_readiness_source s ON s.tenant_id=r.tenant_id AND s.snapshot_id=r.id AND s.source_code=CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.sourceCode')) END
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.context_json, '$.sourceFacts'), CONCAT('$[', a.seq-1, ']')), '$.watermarkEntries'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) b ON TRUE;

INSERT INTO `imp_cutover_readiness_unmet` (`id`, `tenant_id`, `snapshot_id`, `unmet_code`, `sort_order`, `create_user_id`, `create_time`)
SELECT 7800073000000000000 + ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(r.unmet_codes_json, CONCAT('$[', a.seq-1, ']'))) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.unmet_codes_json, CONCAT('$[', a.seq-1, ']'))) END,
       a.seq-1,
       r.evaluated_by,
       r.create_time
FROM imp_cutover_readiness_snapshot r
JOIN JSON_TABLE(COALESCE(r.unmet_codes_json, JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE;

CALL cut_v475_assert(((SELECT COUNT(*) FROM imp_cutover_readiness_snapshot r WHERE (SELECT COUNT(*) FROM imp_cutover_readiness_source s WHERE s.tenant_id=r.tenant_id AND s.snapshot_id=r.id)<>4 OR NOT EXISTS(SELECT 1 FROM imp_cutover_readiness_device d WHERE d.tenant_id=r.tenant_id AND d.snapshot_id=r.id)) = 0), 'Readiness snapshot must retain devices and all four source facts');

CALL cut_v475_assert(((SELECT COUNT(*) FROM imp_cutover_readiness_source s WHERE s.completion_status_code IN ('ACCEPTED','COMPLETED') AND (NOT EXISTS(SELECT 1 FROM imp_cutover_readiness_source_object o WHERE o.tenant_id=s.tenant_id AND o.source_fact_id=s.id) OR NOT EXISTS(SELECT 1 FROM imp_cutover_readiness_source_watermark w WHERE w.tenant_id=s.tenant_id AND w.source_fact_id=s.id))) = 0), 'Completed readiness source has no original object/watermark evidence');

UPDATE `ast_device_cutover_result` r 
SET r.`task_no` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.taskNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.taskNo')) END,
    r.`task_name` = CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.taskName')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.taskName')) END,
    r.`submit_user_id` = CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(r.result_snapshot, '$.submittedBy')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(r.result_snapshot, '$.submittedBy')) END) AS SIGNED),
    r.`update_time` = r.update_time
WHERE 1=1;

INSERT INTO `ast_device_cutover_result_file` (`id`, `tenant_id`, `device_cutover_result_id`, `file_source_code`, `collection_stage_code`, `sort_order`, `actor_user_id`, `platform_task_id`, `result_version`, `file_version_id`, `create_user_id`, `create_time`)
SELECT 7804000000000000000 + ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       'COLLECTION',
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.stageCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.stageCode')) END,
       a.seq-1,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.actorId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.actorId')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.platformTaskId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.platformTaskId')) END,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.resultVersion')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.resultVersion')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.fileVersionId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.fileVersionId')) END) AS SIGNED),
       r.submit_user_id,
       r.create_time
FROM ast_device_cutover_result r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE WHERE CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.collectionFiles'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)=r.device_id;

INSERT INTO `ast_device_cutover_result_file` (`id`, `tenant_id`, `device_cutover_result_id`, `file_source_code`, `collection_stage_code`, `sort_order`, `file_artifact_id`, `file_version_no`, `file_reference_key`, `file_sha256`, `create_user_id`, `create_time`)
SELECT 7804000001000000000 + ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,a.seq),
       r.tenant_id,
       r.id,
       'MANUAL',
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.stageCode')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.stageCode')) END,
       a.seq-1,
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.artifactId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.artifactId')) END) AS SIGNED),
       CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.versionNo')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.versionNo')) END) AS SIGNED),
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.referenceKey')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.referenceKey')) END,
       CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.sha256')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.sha256')) END,
       r.submit_user_id,
       r.create_time
FROM ast_device_cutover_result r
JOIN JSON_TABLE(COALESCE(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), JSON_ARRAY()), '$[*]' COLUMNS (seq FOR ORDINALITY)) a ON TRUE WHERE CAST((CASE WHEN JSON_TYPE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(JSON_EXTRACT(JSON_EXTRACT(r.result_snapshot, '$.manualFiles'), CONCAT('$[', a.seq-1, ']')), '$.deviceId')) END) AS SIGNED)=r.device_id;

CALL cut_v475_assert(((SELECT COUNT(*) FROM ast_device_cutover_result r WHERE NOT EXISTS (SELECT 1 FROM JSON_TABLE(JSON_EXTRACT(r.result_snapshot, '$.devices'),'$[*]' COLUMNS(device_id BIGINT PATH '$.deviceId',sn VARCHAR(128) PATH '$.serialNumber',assignment_version BIGINT PATH '$.assignmentVersion')) d WHERE d.device_id=r.device_id AND CAST(d.sn AS BINARY)=CAST(r.serial_number AS BINARY) AND d.assignment_version=r.assignment_version)) = 0), 'AST result device identity differs from its immutable CUT result');

COMMIT;
DROP TEMPORARY TABLE cut_v475_dimensions;
DROP TEMPORARY TABLE cut_v475_schema_source;
DROP TEMPORARY TABLE cut_v475_field_tree;
DROP TEMPORARY TABLE cut_v475_field_parent;
DROP PROCEDURE cut_v475_assert;
