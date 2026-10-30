# Flyway V498 基线触发器清单

来源：对本次 421 份迁移进行完整演练后查询 `information_schema.TRIGGERS`，再按需求方确认排除 29 张旧表及其 87 个只读触发器；当前基线共 **238 个触发器，涉及 98 张表**。保留触发器的定义不变，本次没有新增业务触发器。

运行顺序：先创建表并写入所选基础种子，最后安装触发器。保留表的运行时约束与来源一致；排除旧表仅限尚未发布的空库基线，不删除已有数据库中的历史事实或保护约束。

对应 SQL：[`B498__npdms_formal_test_baseline.sql`](../../sql/releases/test-v498/migrations/B498__npdms_formal_test_baseline.sql)。下表行号指向本次交付的该文件。

| 分组 | 数量 | 涉及表数 | 作用 |
| --- | ---: | ---: | --- |
| 割接及关联设备实施记录 | 187 | 73 | 保护已发布配置、结构化明细、审批/方案/闭环历史；校验状态及父子关系，并禁止向既有历史 JSON 原件新增写入。 |
| 巡检及客户巡检历史 | 49 | 23 | 保护执行、采集回执、检测结果、报告、反馈和客户巡检来源；校验租户/对象/状态归属。 |
| 项目阶段任务完成留痕 | 2 | 2 | 阶段或任务从非 DONE 进入 DONE 且已有实际结束时间时，追加 proj_node_overdue_completion 记录及逾期标记。 |

事件分布：BEFORE INSERT 42、BEFORE UPDATE 100、BEFORE DELETE 94、AFTER UPDATE 2。

其中 **2 个 AFTER UPDATE 会追加业务留痕**，不是只做拒写校验；触发器不会把任务或阶段改为 DONE，也不替代服务端状态机。

## 割接及关联设备实施记录

保护已发布配置、结构化明细、审批/方案/闭环历史；校验状态及父子关系，并禁止向既有历史 JSON 原件新增写入。

| 触发器 | 目标表 | 时机 | SQL 行 | 信号/写入目标 |
| --- | --- | --- | ---: | --- |
| `trg_cut_structured_74_d` | `ast_device_cutover_result` | BEFORE DELETE | 18000 | CUT_IMMUTABLE_HISTORY: ast_device_cutover_result |
| `trg_cut_structured_74_i` | `ast_device_cutover_result` | BEFORE INSERT | 17959 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_74_u` | `ast_device_cutover_result` | BEFORE UPDATE | 17978 | CUT_IMMUTABLE_HISTORY: ast_device_cutover_result |
| `trg_cut_structured_75_d` | `ast_device_cutover_result_file` | BEFORE DELETE | 18038 | CUT_IMMUTABLE_HISTORY: ast_device_cutover_result_file |
| `trg_cut_structured_75_u` | `ast_device_cutover_result_file` | BEFORE UPDATE | 18019 | CUT_IMMUTABLE_HISTORY: ast_device_cutover_result_file |
| `trg_cut_structured_51_d` | `cut_approval_instance` | BEFORE DELETE | 18158 | CUT_IMMUTABLE_HISTORY: cut_approval_instance |
| `trg_cut_structured_51_i` | `cut_approval_instance` | BEFORE INSERT | 18117 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_51_u` | `cut_approval_instance` | BEFORE UPDATE | 18136 | CUT_IMMUTABLE_HISTORY: cut_approval_instance |
| `trg_cut_structured_52_d` | `cut_approval_node` | BEFORE DELETE | 18221 | CUT_IMMUTABLE_HISTORY: cut_approval_node |
| `trg_cut_structured_52_i` | `cut_approval_node` | BEFORE INSERT | 18177 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_52_u` | `cut_approval_node` | BEFORE UPDATE | 18196 | CUT_IMMUTABLE_HISTORY: cut_approval_node |
| `trg_cut_structured_54_d` | `cut_approval_reassignment` | BEFORE DELETE | 18281 | CUT_IMMUTABLE_HISTORY: cut_approval_reassignment |
| `trg_cut_structured_54_i` | `cut_approval_reassignment` | BEFORE INSERT | 18240 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_54_u` | `cut_approval_reassignment` | BEFORE UPDATE | 18259 | CUT_IMMUTABLE_HISTORY: cut_approval_reassignment |
| `trg_cut_structured_57_d` | `cut_approval_result_reference` | BEFORE DELETE | 18319 | CUT_IMMUTABLE_HISTORY: cut_approval_result_reference |
| `trg_cut_structured_57_u` | `cut_approval_result_reference` | BEFORE UPDATE | 18300 | CUT_IMMUTABLE_HISTORY: cut_approval_result_reference |
| `trg_cut_structured_53_d` | `cut_approval_review_item` | BEFORE DELETE | 18357 | CUT_IMMUTABLE_HISTORY: cut_approval_review_item |
| `trg_cut_structured_53_u` | `cut_approval_review_item` | BEFORE UPDATE | 18338 | CUT_IMMUTABLE_HISTORY: cut_approval_review_item |
| `trg_cut_structured_16_d` | `cut_assessment` | BEFORE DELETE | 18421 | CUT_IMMUTABLE_HISTORY: cut_assessment |
| `trg_cut_structured_16_i` | `cut_assessment` | BEFORE INSERT | 18376 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_16_u` | `cut_assessment` | BEFORE UPDATE | 18395 | CUT_IMMUTABLE_HISTORY: cut_assessment；CUT_ASSESSMENT_REQUIRES_FROZEN_DEVICES |
| `trg_cut_structured_17_d` | `cut_assessment_device` | BEFORE DELETE | 18484 | CUT_PARENT_FROZEN: cut_assessment_device |
| `trg_cut_structured_17_i` | `cut_assessment_device` | BEFORE INSERT | 18440 | CUT_PARENT_FROZEN: cut_assessment_device |
| `trg_cut_structured_17_u` | `cut_assessment_device` | BEFORE UPDATE | 18459 | CUT_PARENT_FROZEN: cut_assessment_device；CUT_IMMUTABLE_HISTORY: cut_assessment_device |
| `trg_cut_structured_11_d` | `cut_checklist_binding_condition` | BEFORE DELETE | 18547 | CUT_PARENT_FROZEN: cut_checklist_binding_condition |
| `trg_cut_structured_11_i` | `cut_checklist_binding_condition` | BEFORE INSERT | 18503 | CUT_PARENT_FROZEN: cut_checklist_binding_condition |
| `trg_cut_structured_11_u` | `cut_checklist_binding_condition` | BEFORE UPDATE | 18522 | CUT_PARENT_FROZEN: cut_checklist_binding_condition；CUT_IMMUTABLE_HISTORY: cut_checklist_binding_condition |
| `trg_cut_structured_23_d` | `cut_checklist_device_context` | BEFORE DELETE | 18610 | CUT_PARENT_FROZEN: cut_checklist_device_context |
| `trg_cut_structured_23_i` | `cut_checklist_device_context` | BEFORE INSERT | 18566 | CUT_PARENT_FROZEN: cut_checklist_device_context |
| `trg_cut_structured_23_u` | `cut_checklist_device_context` | BEFORE UPDATE | 18585 | CUT_PARENT_FROZEN: cut_checklist_device_context；CUT_IMMUTABLE_HISTORY: cut_checklist_device_context |
| `trg_cut_structured_12_d` | `cut_checklist_external_mapping` | BEFORE DELETE | 18673 | CUT_PARENT_FROZEN: cut_checklist_external_mapping |
| `trg_cut_structured_12_i` | `cut_checklist_external_mapping` | BEFORE INSERT | 18629 | CUT_PARENT_FROZEN: cut_checklist_external_mapping |
| `trg_cut_structured_12_u` | `cut_checklist_external_mapping` | BEFORE UPDATE | 18648 | CUT_PARENT_FROZEN: cut_checklist_external_mapping；CUT_IMMUTABLE_HISTORY: cut_checklist_external_mapping |
| `trg_cut_structured_25_d` | `cut_checklist_external_query` | BEFORE DELETE | 18714 | CUT_IMMUTABLE_HISTORY: cut_checklist_external_query |
| `trg_cut_structured_25_u` | `cut_checklist_external_query` | BEFORE UPDATE | 18692 | CUT_IMMUTABLE_HISTORY: cut_checklist_external_query |
| `trg_cut_structured_26_d` | `cut_checklist_external_query_parameter` | BEFORE DELETE | 18752 | CUT_IMMUTABLE_HISTORY: cut_checklist_external_query_parameter |
| `trg_cut_structured_26_u` | `cut_checklist_external_query_parameter` | BEFORE UPDATE | 18733 | CUT_IMMUTABLE_HISTORY: cut_checklist_external_query_parameter |
| `trg_cut_structured_9_d` | `cut_checklist_field_definition` | BEFORE DELETE | 18827 | CUT_PARENT_FROZEN: field definition |
| `trg_cut_structured_9_i` | `cut_checklist_field_definition` | BEFORE INSERT | 18771 | CUT_PARENT_FROZEN: field definition |
| `trg_cut_structured_9_u` | `cut_checklist_field_definition` | BEFORE UPDATE | 18795 | CUT_PARENT_FROZEN: field definition |
| `trg_cut_structured_10_d` | `cut_checklist_field_option` | BEFORE DELETE | 18907 | CUT_PARENT_FROZEN: cut_checklist_field_option |
| `trg_cut_structured_10_i` | `cut_checklist_field_option` | BEFORE INSERT | 18851 | CUT_PARENT_FROZEN: cut_checklist_field_option |
| `trg_cut_structured_10_u` | `cut_checklist_field_option` | BEFORE UPDATE | 18874 | CUT_PARENT_FROZEN: cut_checklist_field_option；CUT_IMMUTABLE_HISTORY: cut_checklist_field_option |
| `trg_cut_structured_21_d` | `cut_checklist_input_dimension` | BEFORE DELETE | 18974 | CUT_PARENT_FROZEN: cut_checklist_input_dimension |
| `trg_cut_structured_21_i` | `cut_checklist_input_dimension` | BEFORE INSERT | 18930 | CUT_PARENT_FROZEN: cut_checklist_input_dimension |
| `trg_cut_structured_21_u` | `cut_checklist_input_dimension` | BEFORE UPDATE | 18949 | CUT_PARENT_FROZEN: cut_checklist_input_dimension；CUT_IMMUTABLE_HISTORY: cut_checklist_input_dimension |
| `trg_cut_structured_22_d` | `cut_checklist_match_candidate` | BEFORE DELETE | 19037 | CUT_PARENT_FROZEN: cut_checklist_match_candidate |
| `trg_cut_structured_22_i` | `cut_checklist_match_candidate` | BEFORE INSERT | 18993 | CUT_PARENT_FROZEN: cut_checklist_match_candidate |
| `trg_cut_structured_22_u` | `cut_checklist_match_candidate` | BEFORE UPDATE | 19012 | CUT_PARENT_FROZEN: cut_checklist_match_candidate；CUT_IMMUTABLE_HISTORY: cut_checklist_match_candidate |
| `trg_cut_structured_24_d` | `cut_checklist_result_file` | BEFORE DELETE | 19075 | CUT_IMMUTABLE_HISTORY: cut_checklist_result_file |
| `trg_cut_structured_24_u` | `cut_checklist_result_file` | BEFORE UPDATE | 19056 | CUT_IMMUTABLE_HISTORY: cut_checklist_result_file |
| `trg_cut_structured_27_d` | `cut_checklist_result_query_reference` | BEFORE DELETE | 19113 | CUT_IMMUTABLE_HISTORY: cut_checklist_result_query_reference |
| `trg_cut_structured_27_u` | `cut_checklist_result_query_reference` | BEFORE UPDATE | 19094 | CUT_IMMUTABLE_HISTORY: cut_checklist_result_query_reference |
| `trg_cut_structured_62_d` | `cut_closure_device` | BEFORE DELETE | 19176 | CUT_PARENT_FROZEN: cut_closure_device |
| `trg_cut_structured_62_i` | `cut_closure_device` | BEFORE INSERT | 19132 | CUT_PARENT_FROZEN: cut_closure_device |
| `trg_cut_structured_62_u` | `cut_closure_device` | BEFORE UPDATE | 19151 | CUT_PARENT_FROZEN: cut_closure_device；CUT_IMMUTABLE_HISTORY: cut_closure_device |
| `trg_cut_structured_61_d` | `cut_collection_log` | BEFORE DELETE | 19236 | CUT_IMMUTABLE_HISTORY: cut_collection_log |
| `trg_cut_structured_61_i` | `cut_collection_log` | BEFORE INSERT | 19195 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_61_u` | `cut_collection_log` | BEFORE UPDATE | 19214 | CUT_IMMUTABLE_HISTORY: cut_collection_log |
| `trg_cut_structured_5_d` | `cut_configuration_dictionary_value` | BEFORE DELETE | 19299 | CUT_PARENT_FROZEN: cut_configuration_dictionary_value |
| `trg_cut_structured_5_i` | `cut_configuration_dictionary_value` | BEFORE INSERT | 19255 | CUT_PARENT_FROZEN: cut_configuration_dictionary_value |
| `trg_cut_structured_5_u` | `cut_configuration_dictionary_value` | BEFORE UPDATE | 19274 | CUT_PARENT_FROZEN: cut_configuration_dictionary_value；CUT_IMMUTABLE_HISTORY: cut_configuration_dictionary_value |
| `trg_cut_structured_4_d` | `cut_configuration_dimension` | BEFORE DELETE | 19362 | CUT_PARENT_FROZEN: cut_configuration_dimension |
| `trg_cut_structured_4_i` | `cut_configuration_dimension` | BEFORE INSERT | 19318 | CUT_PARENT_FROZEN: cut_configuration_dimension |
| `trg_cut_structured_4_u` | `cut_configuration_dimension` | BEFORE UPDATE | 19337 | CUT_PARENT_FROZEN: cut_configuration_dimension；CUT_IMMUTABLE_HISTORY: cut_configuration_dimension |
| `trg_cut_structured_6_d` | `cut_configuration_plan_section` | BEFORE DELETE | 19425 | CUT_PARENT_FROZEN: cut_configuration_plan_section |
| `trg_cut_structured_6_i` | `cut_configuration_plan_section` | BEFORE INSERT | 19381 | CUT_PARENT_FROZEN: cut_configuration_plan_section |
| `trg_cut_structured_6_u` | `cut_configuration_plan_section` | BEFORE UPDATE | 19400 | CUT_PARENT_FROZEN: cut_configuration_plan_section；CUT_IMMUTABLE_HISTORY: cut_configuration_plan_section |
| `trg_cut_structured_7_d` | `cut_configuration_plan_section_scope` | BEFORE DELETE | 19488 | CUT_PARENT_FROZEN: cut_configuration_plan_section_scope |
| `trg_cut_structured_7_i` | `cut_configuration_plan_section_scope` | BEFORE INSERT | 19444 | CUT_PARENT_FROZEN: cut_configuration_plan_section_scope |
| `trg_cut_structured_7_u` | `cut_configuration_plan_section_scope` | BEFORE UPDATE | 19463 | CUT_PARENT_FROZEN: cut_configuration_plan_section_scope；CUT_IMMUTABLE_HISTORY: cut_configuration_plan_section_scope |
| `trg_cut_structured_8_d` | `cut_configuration_validation_issue` | BEFORE DELETE | 19526 | CUT_IMMUTABLE_HISTORY: cut_configuration_validation_issue |
| `trg_cut_structured_8_u` | `cut_configuration_validation_issue` | BEFORE UPDATE | 19507 | CUT_IMMUTABLE_HISTORY: cut_configuration_validation_issue |
| `trg_cut_structured_18_d` | `cut_cutover_checklist` | BEFORE DELETE | 19592 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist |
| `trg_cut_structured_18_i` | `cut_cutover_checklist` | BEFORE INSERT | 19545 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_18_u` | `cut_cutover_checklist` | BEFORE UPDATE | 19564 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist；CUT_MANUAL_RESULT_REQUIRES_EXACT_FILE |
| `trg_cut_structured_3_d` | `cut_cutover_checklist_binding_rule_revision` | BEFORE DELETE | 19661 | CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision |
| `trg_cut_structured_3_i` | `cut_cutover_checklist_binding_rule_revision` | BEFORE INSERT | 19611 | CUT_LEGACY_ORIGINAL_READ_ONLY；CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision |
| `trg_cut_structured_3_u` | `cut_cutover_checklist_binding_rule_revision` | BEFORE UPDATE | 19633 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_binding_rule_revision；CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision |
| `trg_cut_structured_19_d` | `cut_cutover_checklist_item` | BEFORE DELETE | 19730 | CUT_PARENT_FROZEN: cut_cutover_checklist_item |
| `trg_cut_structured_19_i` | `cut_cutover_checklist_item` | BEFORE INSERT | 19680 | CUT_LEGACY_ORIGINAL_READ_ONLY；CUT_PARENT_FROZEN: cut_cutover_checklist_item |
| `trg_cut_structured_19_u` | `cut_cutover_checklist_item` | BEFORE UPDATE | 19702 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item；CUT_PARENT_FROZEN: cut_cutover_checklist_item |
| `trg_cut_structured_2_d` | `cut_cutover_checklist_item_definition_revision` | BEFORE DELETE | 19799 | CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision |
| `trg_cut_structured_2_i` | `cut_cutover_checklist_item_definition_revision` | BEFORE INSERT | 19749 | CUT_LEGACY_ORIGINAL_READ_ONLY；CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision |
| `trg_cut_structured_2_u` | `cut_cutover_checklist_item_definition_revision` | BEFORE UPDATE | 19771 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_definition_revision；CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision |
| `trg_cut_structured_20_d` | `cut_cutover_checklist_item_result` | BEFORE DELETE | 19862 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_result |
| `trg_cut_structured_20_i` | `cut_cutover_checklist_item_result` | BEFORE INSERT | 19818 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_20_u` | `cut_cutover_checklist_item_result` | BEFORE UPDATE | 19837 | CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_result；CUT_RESULT_SELECTION_ALREADY_CLOSED |
| `trg_cut_structured_58_d` | `cut_cutover_closure` | BEFORE DELETE | 19922 | CUT_IMMUTABLE_HISTORY: cut_cutover_closure |
| `trg_cut_structured_58_i` | `cut_cutover_closure` | BEFORE INSERT | 19881 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_58_u` | `cut_cutover_closure` | BEFORE UPDATE | 19900 | CUT_IMMUTABLE_HISTORY: cut_cutover_closure |
| `trg_cut_structured_59_d` | `cut_cutover_closure_attachment` | BEFORE DELETE | 19991 | CUT_PARENT_FROZEN: cut_cutover_closure_attachment |
| `trg_cut_structured_59_i` | `cut_cutover_closure_attachment` | BEFORE INSERT | 19941 | CUT_LEGACY_ORIGINAL_READ_ONLY；CUT_PARENT_FROZEN: cut_cutover_closure_attachment |
| `trg_cut_structured_59_u` | `cut_cutover_closure_attachment` | BEFORE UPDATE | 19963 | CUT_IMMUTABLE_HISTORY: cut_cutover_closure_attachment；CUT_PARENT_FROZEN: cut_cutover_closure_attachment |
| `trg_cut_structured_60_d` | `cut_cutover_collection_evidence` | BEFORE DELETE | 20029 | CUT_IMMUTABLE_HISTORY: cut_cutover_collection_evidence |
| `trg_cut_structured_60_u` | `cut_cutover_collection_evidence` | BEFORE UPDATE | 20010 | CUT_IMMUTABLE_HISTORY: cut_cutover_collection_evidence |
| `trg_cut_structured_1_d` | `cut_cutover_configuration_revision` | BEFORE DELETE | 20089 | CUT_IMMUTABLE_HISTORY: cut_cutover_configuration_revision |
| `trg_cut_structured_1_i` | `cut_cutover_configuration_revision` | BEFORE INSERT | 20048 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_1_u` | `cut_cutover_configuration_revision` | BEFORE UPDATE | 20067 | CUT_IMMUTABLE_HISTORY: cut_cutover_configuration_revision |
| `trg_cut_structured_45_d` | `cut_cutover_support_arrangement` | BEFORE DELETE | 20177 | CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement |
| `trg_cut_structured_45_u` | `cut_cutover_support_arrangement` | BEFORE UPDATE | 20129 | CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement |
| `trg_cut_support_draft_insert` | `cut_cutover_support_arrangement` | BEFORE INSERT | 20108 | CUT_PARENT_FROZEN: cut_cutover_support_arrangement |
| `trg_cut_support_reference_update` | `cut_cutover_support_arrangement` | BEFORE UPDATE | 20148 | CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement；CUT_PARENT_FROZEN: cut_cutover_support_arrangement |
| `trg_cut_structured_47_d` | `cut_plan_device` | BEFORE DELETE | 20240 | CUT_PARENT_FROZEN: cut_plan_device |
| `trg_cut_structured_47_i` | `cut_plan_device` | BEFORE INSERT | 20196 | CUT_PARENT_FROZEN: cut_plan_device |
| `trg_cut_structured_47_u` | `cut_plan_device` | BEFORE UPDATE | 20215 | CUT_PARENT_FROZEN: cut_plan_device；CUT_IMMUTABLE_HISTORY: cut_plan_device |
| `trg_cut_structured_48_d` | `cut_plan_file` | BEFORE DELETE | 20303 | CUT_PARENT_FROZEN: cut_plan_file |
| `trg_cut_structured_48_i` | `cut_plan_file` | BEFORE INSERT | 20259 | CUT_PARENT_FROZEN: cut_plan_file |
| `trg_cut_structured_48_u` | `cut_plan_file` | BEFORE UPDATE | 20278 | CUT_PARENT_FROZEN: cut_plan_file；CUT_IMMUTABLE_HISTORY: cut_plan_file |
| `trg_cut_plan_scheduled_time_immutable` | `cut_plan_revision` | BEFORE UPDATE | 20390 | CUT_IMMUTABLE_HISTORY: cut_plan_revision.scheduled_time |
| `trg_cut_plan_support_submit` | `cut_plan_revision` | BEFORE UPDATE | 20363 | CUT_SUPPORT_INCOMPLETE |
| `trg_cut_structured_43_d` | `cut_plan_revision` | BEFORE DELETE | 20409 | CUT_IMMUTABLE_HISTORY: cut_plan_revision |
| `trg_cut_structured_43_i` | `cut_plan_revision` | BEFORE INSERT | 20322 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_43_u` | `cut_plan_revision` | BEFORE UPDATE | 20341 | CUT_IMMUTABLE_HISTORY: cut_plan_revision |
| `trg_cut_structured_49_d` | `cut_plan_risk_mitigation` | BEFORE DELETE | 20472 | CUT_PARENT_FROZEN: cut_plan_risk_mitigation |
| `trg_cut_structured_49_i` | `cut_plan_risk_mitigation` | BEFORE INSERT | 20428 | CUT_PARENT_FROZEN: cut_plan_risk_mitigation |
| `trg_cut_structured_49_u` | `cut_plan_risk_mitigation` | BEFORE UPDATE | 20447 | CUT_PARENT_FROZEN: cut_plan_risk_mitigation；CUT_IMMUTABLE_HISTORY: cut_plan_risk_mitigation |
| `trg_cut_structured_46_d` | `cut_plan_schedule` | BEFORE DELETE | 20535 | CUT_PARENT_FROZEN: cut_plan_schedule |
| `trg_cut_structured_46_i` | `cut_plan_schedule` | BEFORE INSERT | 20491 | CUT_PARENT_FROZEN: cut_plan_schedule |
| `trg_cut_structured_46_u` | `cut_plan_schedule` | BEFORE UPDATE | 20510 | CUT_PARENT_FROZEN: cut_plan_schedule；CUT_IMMUTABLE_HISTORY: cut_plan_schedule |
| `trg_cut_structured_50_d` | `cut_plan_support_contact_change` | BEFORE DELETE | 20573 | CUT_IMMUTABLE_HISTORY: cut_plan_support_contact_change |
| `trg_cut_structured_50_u` | `cut_plan_support_contact_change` | BEFORE UPDATE | 20554 | CUT_IMMUTABLE_HISTORY: cut_plan_support_contact_change |
| `trg_cut_structured_35_d` | `cut_result_bulletin_summary` | BEFORE DELETE | 20611 | CUT_IMMUTABLE_HISTORY: cut_result_bulletin_summary |
| `trg_cut_structured_35_u` | `cut_result_bulletin_summary` | BEFORE UPDATE | 20592 | CUT_IMMUTABLE_HISTORY: cut_result_bulletin_summary |
| `trg_cut_structured_29_d` | `cut_result_business_summary` | BEFORE DELETE | 20649 | CUT_IMMUTABLE_HISTORY: cut_result_business_summary |
| `trg_cut_structured_29_u` | `cut_result_business_summary` | BEFORE UPDATE | 20630 | CUT_IMMUTABLE_HISTORY: cut_result_business_summary |
| `trg_cut_structured_33_d` | `cut_result_check_feedback` | BEFORE DELETE | 20687 | CUT_IMMUTABLE_HISTORY: cut_result_check_feedback |
| `trg_cut_structured_33_u` | `cut_result_check_feedback` | BEFORE UPDATE | 20668 | CUT_IMMUTABLE_HISTORY: cut_result_check_feedback |
| `trg_cut_structured_31_d` | `cut_result_continuity_requirement` | BEFORE DELETE | 20725 | CUT_IMMUTABLE_HISTORY: cut_result_continuity_requirement |
| `trg_cut_structured_31_u` | `cut_result_continuity_requirement` | BEFORE UPDATE | 20706 | CUT_IMMUTABLE_HISTORY: cut_result_continuity_requirement |
| `trg_cut_structured_42_d` | `cut_result_custom_row` | BEFORE DELETE | 20763 | CUT_IMMUTABLE_HISTORY: cut_result_custom_row |
| `trg_cut_structured_42_u` | `cut_result_custom_row` | BEFORE UPDATE | 20744 | CUT_IMMUTABLE_HISTORY: cut_result_custom_row |
| `trg_cut_structured_41_d` | `cut_result_custom_value` | BEFORE DELETE | 20801 | CUT_IMMUTABLE_HISTORY: cut_result_custom_value |
| `trg_cut_structured_41_u` | `cut_result_custom_value` | BEFORE UPDATE | 20782 | CUT_IMMUTABLE_HISTORY: cut_result_custom_value |
| `trg_cut_structured_28_d` | `cut_result_cutover_background` | BEFORE DELETE | 20839 | CUT_IMMUTABLE_HISTORY: cut_result_cutover_background |
| `trg_cut_structured_28_u` | `cut_result_cutover_background` | BEFORE UPDATE | 20820 | CUT_IMMUTABLE_HISTORY: cut_result_cutover_background |
| `trg_cut_structured_36_d` | `cut_result_device_location` | BEFORE DELETE | 20877 | CUT_IMMUTABLE_HISTORY: cut_result_device_location |
| `trg_cut_structured_36_u` | `cut_result_device_location` | BEFORE UPDATE | 20858 | CUT_IMMUTABLE_HISTORY: cut_result_device_location |
| `trg_cut_structured_30_d` | `cut_result_impact_scope` | BEFORE DELETE | 20915 | CUT_IMMUTABLE_HISTORY: cut_result_impact_scope |
| `trg_cut_structured_30_u` | `cut_result_impact_scope` | BEFORE UPDATE | 20896 | CUT_IMMUTABLE_HISTORY: cut_result_impact_scope |
| `trg_cut_structured_37_d` | `cut_result_interface_plan` | BEFORE DELETE | 20953 | CUT_IMMUTABLE_HISTORY: cut_result_interface_plan |
| `trg_cut_structured_37_u` | `cut_result_interface_plan` | BEFORE UPDATE | 20934 | CUT_IMMUTABLE_HISTORY: cut_result_interface_plan |
| `trg_cut_structured_32_d` | `cut_result_interruption` | BEFORE DELETE | 20991 | CUT_IMMUTABLE_HISTORY: cut_result_interruption |
| `trg_cut_structured_32_u` | `cut_result_interruption` | BEFORE UPDATE | 20972 | CUT_IMMUTABLE_HISTORY: cut_result_interruption |
| `trg_cut_structured_38_d` | `cut_result_ip_plan` | BEFORE DELETE | 21029 | CUT_IMMUTABLE_HISTORY: cut_result_ip_plan |
| `trg_cut_structured_38_u` | `cut_result_ip_plan` | BEFORE UPDATE | 21010 | CUT_IMMUTABLE_HISTORY: cut_result_ip_plan |
| `trg_cut_structured_39_d` | `cut_result_performance` | BEFORE DELETE | 21067 | CUT_IMMUTABLE_HISTORY: cut_result_performance |
| `trg_cut_structured_39_u` | `cut_result_performance` | BEFORE UPDATE | 21048 | CUT_IMMUTABLE_HISTORY: cut_result_performance |
| `trg_cut_structured_34_d` | `cut_result_spare_reference` | BEFORE DELETE | 21105 | CUT_IMMUTABLE_HISTORY: cut_result_spare_reference |
| `trg_cut_structured_34_u` | `cut_result_spare_reference` | BEFORE UPDATE | 21086 | CUT_IMMUTABLE_HISTORY: cut_result_spare_reference |
| `trg_cut_structured_40_d` | `cut_result_technical_bulletin` | BEFORE DELETE | 21143 | CUT_IMMUTABLE_HISTORY: cut_result_technical_bulletin |
| `trg_cut_structured_40_u` | `cut_result_technical_bulletin` | BEFORE UPDATE | 21124 | CUT_IMMUTABLE_HISTORY: cut_result_technical_bulletin |
| `trg_cut_structured_63_i` | `cut_spare_application_reference` | BEFORE INSERT | 21162 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_63_u` | `cut_spare_application_reference` | BEFORE UPDATE | 21181 | CUT_IMMUTABLE_HISTORY: cut_spare_application_reference |
| `trg_cut_structured_65_d` | `cut_spare_manual_evidence` | BEFORE DELETE | 21241 | CUT_IMMUTABLE_HISTORY: cut_spare_manual_evidence |
| `trg_cut_structured_65_i` | `cut_spare_manual_evidence` | BEFORE INSERT | 21200 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_65_u` | `cut_spare_manual_evidence` | BEFORE UPDATE | 21219 | CUT_IMMUTABLE_HISTORY: cut_spare_manual_evidence |
| `trg_cut_structured_66_d` | `cut_spare_need_source` | BEFORE DELETE | 21279 | CUT_IMMUTABLE_HISTORY: cut_spare_need_source |
| `trg_cut_structured_66_u` | `cut_spare_need_source` | BEFORE UPDATE | 21260 | CUT_IMMUTABLE_HISTORY: cut_spare_need_source |
| `trg_cut_structured_67_d` | `cut_spare_request_device` | BEFORE DELETE | 21317 | CUT_IMMUTABLE_HISTORY: cut_spare_request_device |
| `trg_cut_structured_67_u` | `cut_spare_request_device` | BEFORE UPDATE | 21298 | CUT_IMMUTABLE_HISTORY: cut_spare_request_device |
| `trg_cut_structured_64_d` | `cut_spare_status_revision` | BEFORE DELETE | 21380 | CUT_IMMUTABLE_HISTORY: cut_spare_status_revision |
| `trg_cut_structured_64_i` | `cut_spare_status_revision` | BEFORE INSERT | 21336 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_64_u` | `cut_spare_status_revision` | BEFORE UPDATE | 21355 | CUT_IMMUTABLE_HISTORY: cut_spare_status_revision；CUT_SPARE_HISTORY_CANNOT_BECOME_CURRENT |
| `trg_cut_structured_44_d` | `cut_step` | BEFORE DELETE | 21443 | CUT_PARENT_FROZEN: cut_step |
| `trg_cut_structured_44_i` | `cut_step` | BEFORE INSERT | 21399 | CUT_PARENT_FROZEN: cut_step |
| `trg_cut_structured_44_u` | `cut_step` | BEFORE UPDATE | 21418 | CUT_PARENT_FROZEN: cut_step；CUT_IMMUTABLE_HISTORY: cut_step |
| `trg_cut_structured_13_i` | `cut_task` | BEFORE INSERT | 21462 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_13_u` | `cut_task` | BEFORE UPDATE | 21481 | CUT_IMMUTABLE_HISTORY: cut_task |
| `trg_cut_structured_14_d` | `cut_task_device_scope` | BEFORE DELETE | 21519 | CUT_IMMUTABLE_HISTORY: cut_task_device_scope |
| `trg_cut_structured_14_u` | `cut_task_device_scope` | BEFORE UPDATE | 21500 | CUT_IMMUTABLE_HISTORY: cut_task_device_scope |
| `trg_cut_structured_15_d` | `cut_task_stage_history` | BEFORE DELETE | 21557 | CUT_IMMUTABLE_HISTORY: cut_task_stage_history |
| `trg_cut_structured_15_u` | `cut_task_stage_history` | BEFORE UPDATE | 21538 | CUT_IMMUTABLE_HISTORY: cut_task_stage_history |
| `trg_cut_structured_69_d` | `imp_cutover_readiness_device` | BEFORE DELETE | 21595 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_device |
| `trg_cut_structured_69_u` | `imp_cutover_readiness_device` | BEFORE UPDATE | 21576 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_device |
| `trg_cut_structured_68_d` | `imp_cutover_readiness_snapshot` | BEFORE DELETE | 21675 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_snapshot |
| `trg_cut_structured_68_i` | `imp_cutover_readiness_snapshot` | BEFORE INSERT | 21614 | CUT_LEGACY_ORIGINAL_READ_ONLY |
| `trg_cut_structured_68_u` | `imp_cutover_readiness_snapshot` | BEFORE UPDATE | 21633 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_snapshot |
| `trg_imp_readiness_plan_immutable` | `imp_cutover_readiness_snapshot` | BEFORE UPDATE | 21655 | IMP readiness frozen plan is immutable |
| `trg_cut_structured_70_d` | `imp_cutover_readiness_source` | BEFORE DELETE | 21713 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source |
| `trg_cut_structured_70_u` | `imp_cutover_readiness_source` | BEFORE UPDATE | 21694 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source |
| `trg_cut_structured_71_d` | `imp_cutover_readiness_source_object` | BEFORE DELETE | 21751 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_object |
| `trg_cut_structured_71_u` | `imp_cutover_readiness_source_object` | BEFORE UPDATE | 21732 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_object |
| `trg_cut_structured_72_d` | `imp_cutover_readiness_source_watermark` | BEFORE DELETE | 21789 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_watermark |
| `trg_cut_structured_72_u` | `imp_cutover_readiness_source_watermark` | BEFORE UPDATE | 21770 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_watermark |
| `trg_cut_structured_73_d` | `imp_cutover_readiness_unmet` | BEFORE DELETE | 21827 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_unmet |
| `trg_cut_structured_73_u` | `imp_cutover_readiness_unmet` | BEFORE UPDATE | 21808 | CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_unmet |

## 巡检及客户巡检历史

保护执行、采集回执、检测结果、报告、反馈和客户巡检来源；校验租户/对象/状态归属。

| 触发器 | 目标表 | 时机 | SQL 行 | 信号/写入目标 |
| --- | --- | --- | ---: | --- |
| `trg_cus_inspection_history_bd` | `cus_inspection_history` | BEFORE DELETE | 18072 | Customer inspection history is immutable |
| `trg_cus_inspection_history_bu` | `cus_inspection_history` | BEFORE UPDATE | 18057 | Customer inspection history is immutable |
| `trg_cus_inspection_reference_bd` | `cus_inspection_reference` | BEFORE DELETE | 18102 | Customer inspection source reference is immutable |
| `trg_cus_inspection_reference_bu` | `cus_inspection_reference` | BEFORE UPDATE | 18087 | Customer inspection source reference is immutable |
| `trg_inspection_annotation_bd` | `srv_inspection_annotation` | BEFORE DELETE | 21933 | INS_IMMUTABLE_HISTORY: srv_inspection_annotation |
| `trg_inspection_annotation_bu` | `srv_inspection_annotation` | BEFORE UPDATE | 21918 | INS_IMMUTABLE_HISTORY: srv_inspection_annotation |
| `trg_ins_annotation_bi` | `srv_inspection_annotation` | BEFORE INSERT | 21898 | INS_ANNOTATION_RESULT_INVALID |
| `trg_inspection_attempt_bd` | `srv_inspection_attempt` | BEFORE DELETE | 21970 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_attempt_bu` | `srv_inspection_attempt` | BEFORE UPDATE | 21948 | INS_ATTEMPT_HISTORY_PROTECTED |
| `trg_inspection_collection_receipt_bd` | `srv_inspection_collection_receipt` | BEFORE DELETE | 22000 | INS_IMMUTABLE_HISTORY: srv_inspection_collection_receipt |
| `trg_inspection_collection_receipt_bu` | `srv_inspection_collection_receipt` | BEFORE UPDATE | 21985 | INS_IMMUTABLE_HISTORY: srv_inspection_collection_receipt |
| `trg_inspection_detection_result_bd` | `srv_inspection_detection_result` | BEFORE DELETE | 22030 | INS_IMMUTABLE_HISTORY: srv_inspection_detection_result |
| `trg_inspection_detection_result_bu` | `srv_inspection_detection_result` | BEFORE UPDATE | 22015 | INS_IMMUTABLE_HISTORY: srv_inspection_detection_result |
| `trg_inspection_evidence_bd` | `srv_inspection_evidence` | BEFORE DELETE | 22060 | INS_IMMUTABLE_HISTORY: srv_inspection_evidence |
| `trg_inspection_evidence_bu` | `srv_inspection_evidence` | BEFORE UPDATE | 22045 | INS_IMMUTABLE_HISTORY: srv_inspection_evidence |
| `trg_inspection_execution_bd` | `srv_inspection_execution` | BEFORE DELETE | 22114 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_execution_bu` | `srv_inspection_execution` | BEFORE UPDATE | 22075 | INS_EXECUTION_HISTORY_PROTECTED |
| `trg_inspection_feedback_confirmation_bd` | `srv_inspection_feedback_confirmation` | BEFORE DELETE | 22144 | INS_IMMUTABLE_HISTORY: srv_inspection_feedback_confirmation |
| `trg_inspection_feedback_confirmation_bu` | `srv_inspection_feedback_confirmation` | BEFORE UPDATE | 22129 | INS_IMMUTABLE_HISTORY: srv_inspection_feedback_confirmation |
| `trg_inspection_feedback_export_bd` | `srv_inspection_feedback_export` | BEFORE DELETE | 22183 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_export_bu` | `srv_inspection_feedback_export` | BEFORE UPDATE | 22159 | INS_EXPORT_HISTORY_PROTECTED |
| `trg_inspection_feedback_export_item_bd` | `srv_inspection_feedback_export_item` | BEFORE DELETE | 22213 | INS_IMMUTABLE_HISTORY: srv_inspection_feedback_export_item |
| `trg_inspection_feedback_export_item_bu` | `srv_inspection_feedback_export_item` | BEFORE UPDATE | 22198 | INS_IMMUTABLE_HISTORY: srv_inspection_feedback_export_item |
| `trg_inspection_parse_part_bd` | `srv_inspection_parse_part` | BEFORE DELETE | 22250 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_parse_part_bu` | `srv_inspection_parse_part` | BEFORE UPDATE | 22228 | INS_PARSE_HISTORY_PROTECTED |
| `trg_inspection_report_file_bd` | `srv_inspection_report_file` | BEFORE DELETE | 22309 | INS_IMMUTABLE_HISTORY: srv_inspection_report_file |
| `trg_inspection_report_file_bu` | `srv_inspection_report_file` | BEFORE UPDATE | 22285 | INS_IMMUTABLE_HISTORY: srv_inspection_report_file |
| `trg_ins_report_file_bi` | `srv_inspection_report_file` | BEFORE INSERT | 22265 | INS_REPORT_FROZEN |
| `trg_inspection_report_result_bd` | `srv_inspection_report_result` | BEFORE DELETE | 22361 | INS_IMMUTABLE_HISTORY: srv_inspection_report_result |
| `trg_inspection_report_result_bu` | `srv_inspection_report_result` | BEFORE UPDATE | 22346 | INS_IMMUTABLE_HISTORY: srv_inspection_report_result |
| `trg_ins_report_result_bi` | `srv_inspection_report_result` | BEFORE INSERT | 22324 | INS_REPORT_INPUT_INVALID |
| `trg_inspection_report_revision_bd` | `srv_inspection_report_revision` | BEFORE DELETE | 22399 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_report_bu` | `srv_inspection_report_revision` | BEFORE UPDATE | 22376 | INS_REPORT_HISTORY_PROTECTED |
| `trg_inspection_rule_feedback_bd` | `srv_inspection_rule_feedback` | BEFORE DELETE | 22429 | INS_IMMUTABLE_HISTORY: srv_inspection_rule_feedback |
| `trg_inspection_rule_feedback_bu` | `srv_inspection_rule_feedback` | BEFORE UPDATE | 22414 | INS_IMMUTABLE_HISTORY: srv_inspection_rule_feedback |
| `trg_inspection_rule_optimization_bd` | `srv_inspection_rule_optimization` | BEFORE DELETE | 22459 | INS_IMMUTABLE_HISTORY: srv_inspection_rule_optimization |
| `trg_inspection_rule_optimization_bu` | `srv_inspection_rule_optimization` | BEFORE UPDATE | 22444 | INS_IMMUTABLE_HISTORY: srv_inspection_rule_optimization |
| `trg_inspection_task_bd` | `srv_inspection_task` | BEFORE DELETE | 22495 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_ins_task_bu` | `srv_inspection_task` | BEFORE UPDATE | 22474 | INS_TASK_HISTORY_PROTECTED |
| `trg_inspection_task_device_bd` | `srv_inspection_task_device` | BEFORE DELETE | 22525 | INS_IMMUTABLE_HISTORY: srv_inspection_task_device |
| `trg_inspection_task_device_bu` | `srv_inspection_task_device` | BEFORE UPDATE | 22510 | INS_IMMUTABLE_HISTORY: srv_inspection_task_device |
| `trg_inspection_task_event_bd` | `srv_inspection_task_event` | BEFORE DELETE | 22555 | INS_IMMUTABLE_HISTORY: srv_inspection_task_event |
| `trg_inspection_task_event_bu` | `srv_inspection_task_event` | BEFORE UPDATE | 22540 | INS_IMMUTABLE_HISTORY: srv_inspection_task_event |
| `trg_inspection_task_rule_snapshot_bd` | `srv_inspection_task_rule_snapshot` | BEFORE DELETE | 22585 | INS_IMMUTABLE_HISTORY: srv_inspection_task_rule_snapshot |
| `trg_inspection_task_rule_snapshot_bu` | `srv_inspection_task_rule_snapshot` | BEFORE UPDATE | 22570 | INS_IMMUTABLE_HISTORY: srv_inspection_task_rule_snapshot |
| `trg_ins_issue_bu` | `srv_service_issue` | BEFORE UPDATE | 22600 | INS_ISSUE_HISTORY_PROTECTED |
| `trg_service_issue_bd` | `srv_service_issue` | BEFORE DELETE | 22620 | INS_HISTORY_DELETE_FORBIDDEN |
| `trg_service_issue_remediation_bd` | `srv_service_issue_remediation` | BEFORE DELETE | 22650 | INS_IMMUTABLE_HISTORY: srv_service_issue_remediation |
| `trg_service_issue_remediation_bu` | `srv_service_issue_remediation` | BEFORE UPDATE | 22635 | INS_IMMUTABLE_HISTORY: srv_service_issue_remediation |


## 项目阶段任务完成留痕

阶段或任务从非 DONE 进入 DONE 且已有实际结束时间时，追加 proj_node_overdue_completion 记录及逾期标记。

| 触发器 | 目标表 | 时机 | SQL 行 | 信号/写入目标 |
| --- | --- | --- | ---: | --- |
| `trg_proj_stage_overdue_completion` | `proj_project_stage` | AFTER UPDATE | 21846 | INSERT proj_node_overdue_completion |
| `trg_proj_task_overdue_completion` | `proj_project_task` | AFTER UPDATE | 21872 | INSERT proj_node_overdue_completion |

## 已排除的旧域触发器

29 张旧表及其 87 个触发器已从本空库基线移除。完整名称见 [source-manifest.json](../../sql/releases/test-v498/source-manifest.json) 的 `excluded_legacy_objects`；原 V310 历史迁移保持不变。
