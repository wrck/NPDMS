# P3-E09 数据模型逐项裁决清单

> 状态：`REQUIREMENT_OWNER_ACCEPTED / REVIEW_PENDING`
> 决策登记SHA-256：`BB635DE4AD561E4B46C70BA8E00198B5962F8B94AC02B08D04E4C04A2CE98BC7`
> 约束清单SHA-256：`CA50BB233376ACC33F824583D44E72CA2A761180D3D5FA9424AF4D38290AA412`
> 本清单只展开现有机器证据，不自动批准数据模型。

## 1. 核对结论与裁决分组

|分组|数量|当前事实|建议裁决方式|
|---|---:|---|---|
|表|66|当前核心迁移子集；新增、修改和移除事实见逐项登记|逐项决策已登记；独立复审仅核对整体一致性|
|字段|1,382|当前DDL字段；不包含已移除V3治理表字段|按业务语义、类型和约束分类裁决|
|表选项|66|旧基线未保存|逐项决策已完成，待独立整体一致性复审|
|主键|66|旧基线未保存|逐项决策已完成，待独立整体一致性复审；Q07|
|外键|52|旧基线未保存|逐项决策已完成，待独立整体一致性复审；Q07|
|普通索引|130|旧基线未保存|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需Feature/P3-E06验证|
|唯一键|138|旧基线未保存|逐项决策已完成，待独立整体一致性复审|
|CHECK|103|旧基线未保存|逐项决策已完成，待独立整体一致性复审|

### 1.1 2079项证据的真实含义

|比较结果|数量|实际含义|能否据此直接批准|
|---|---:|---|---|
|`MATCH`|1,048|历史目标DDL与当前DDL中的表/字段定义一致；不是旧库数据质量证明|无需重复讨论未改变的字段语义，但不能据此宣称迁移通过|
|`ADDED`|392|当前模型相对历史目标DDL新增|必须有需求或ADR依据|
|`MODIFIED`|3|字段定义或说明发生变化|必须说明是否改变业务含义|
|`REMOVED`|72|历史目标DDL中存在、当前模型已移除|必须确认是范围排除而非数据遗漏|
|`UNVERIFIED_BASELINE_MISSING`|564|历史目录未保存约束和表选项|必须按约束语义分类评审，不能自动接受|

因此，1048项MATCH只保留逐项追溯；真正需要裁决的是新增/修改/移除的模型变化，以及564项缺少历史结构证据的约束、表选项或生成表达式。

### 1.2 当前核心迁移子集按领域分布

|领域|表数|表清单|
|---|---:|---|
|ACC|6|`acc_deliverable_template`、`acc_project_deliverable`、`acc_satisfaction_collection_task`、`acc_satisfaction_questionnaire`、`acc_satisfaction_response`、`acc_satisfaction_result`|
|ANA|1|`ana_project_delivery_summary`|
|AST|13|`ast_device_component_relation`、`ast_device_configuration`、`ast_device_configuration_feature`、`ast_device_configuration_service`、`ast_device_project_assignment`、`ast_device_relation`、`ast_device_shipment_event`、`ast_device_sn`、`ast_device_version`、`ast_network_topology`、`ast_network_topology_device_relation`、`ast_product`、`ast_product_release`|
|COM|17|`com_contract`、`com_contract_receivable`、`com_crm_execution_config`、`com_crm_execution_order`、`com_delivery_scope`、`com_delivery_scope_detail`、`com_execution_order_merge_batch`、`com_execution_order_merge_member`、`com_order_change_relation`、`com_order_contract_relation`、`com_order_execution_relation`、`com_order_line_execution_relation`、`com_project_contract_relation`、`com_sales_order`、`com_sales_order_line`、`com_shipment_contract_reference`、`com_shipment_package`|
|CUS|3|`cus_customer`、`cus_customer_contact`、`cus_market_relation`|
|CUT|5|`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`、`cut_cutover_closure`、`cut_cutover_support_arrangement`|
|IMP|3|`imp_configuration_collection_parse_attempt`、`imp_configuration_collection_result`、`imp_configuration_component_candidate`|
|PLT|6|`plt_business_document`、`plt_document_version`、`plt_external_key_mapping`、`plt_migration_issue`、`plt_migration_source_record`、`plt_sync_batch`|
|PROJ|10|`proj_project`、`proj_project_company_department_relation`、`proj_project_member_assignment`、`proj_project_party`、`proj_project_portfolio`、`proj_project_portfolio_member`、`proj_project_relation`、`proj_project_task_completion_evaluation`、`proj_project_task_execution_contract`、`proj_project_template_task_definition`|
|SRV|2|`srv_service_incident`、`srv_service_incident_device_relation`|

### 1.3 已有ADR明确的模型变化

|变化组|具体内容|依据|当前判断|
|---|---|---|---|
|客户与项目市场行业四维|新增`cus_market_relation`及20个字段；客户新增7个字段并修订`industry_code`语义；项目新增7个字段并修订`industry_code`语义|ADR-0021；CRM表`pm_project_market_relations_from_sms`|业务含义已确认；仍需保证来源键精确匹配|
|项目编码命名空间|`proj_project`新增`code_root_id`、`code_rule_version`、`project_sequence`|ADR-0020|同一CRM项目不因多合同/订单改号，子项目使用永久流水号|
|一源多目标映射|`plt_external_key_mapping`新增`target_role`、`target_sequence`|ADR-0022|目标角色和顺序已确认且重跑不可重排|
|V3技术公告治理排除|移除4张KNO表及67个字段|ADR-0022|不进入核心迁移DDL；INT-04只读引用逻辑对象仍保留|
|Q03当前关系与交付范围粒度|4项当前唯一生成列去除扩展状态依赖；新增`com_delivery_scope_detail`；删除订单级唯一主执行单约束|ADR-0023|同一项目节点—订单行一条当前范围主记录并允许多条明细；订单可关联多个默认主执行单|

### 1.4 不能用“整组接受”带过的实质风险

|风险|当前证据|业务影响|批准前应作出的选择|
|---|---|---|---|
|精确键与默认排序规则冲突|66张表默认`utf8mb4_0900_ai_ci`；27个来源键/哈希字段要求原值精确匹配|大小写或重音不同的来源键可能被视为相同|来源键改用二进制排序规则，名称继续使用中文友好排序规则|
|可空列参与唯一键|8个唯一键包含可空列；5个是有意的当前记录标记，1个是可选来源键，2个关系粒度键存在空洞|可能允许重复历史关系或重复成员任职|逐项区分有意NULL语义与意外空洞|
|状态码写入数据库表达式|3个原固定状态CHECK已移除；5个当前唯一生成列使用稳定事实表达式|状态扩展不再需要修改DDL；已确认当前唯一事实不会被状态扩展绕过|保持业务守卫由受控状态动作执行并留痕|
|普通索引没有查询证据|130个候选索引未绑定查询计划、基数和写入成本|过量索引增加同步写入成本，缺失索引影响树查询和对账|已接受为候选索引；待独立整体一致性复审及后续性能验证；Feature/P3-E06用真实查询和压测定稿|

### 1.5 当前哈希下已由需求方接受的Q07技术约束

|内容|数量|批量确认的前提|仍未包含的业务判断|
|---|---:|---|---|
|主键结构|66|65张实体/关系表使用单列`id`；分析投影使用`(tenant_id, project_id)`复合主键|不决定业务编码是否可重复|
|租户复合引用键|66|仅支撑同租户复合外键/行引用|2,079项已决策；待独立整体一致性复审，不替代业务唯一键|
|同领域物理外键|52|52个外键的父子表均在同一领域；违规旧数据进入迁移问题池|不授权跨Context直接访问Repository|
|软删除检查|45|`deleted`稳定为0/1技术字段|删除不得释放永久业务键|
|时间顺序检查|18|只拒绝结束早于开始，不补造旧数据时间|不决定业务有效期|
|稳定布尔标志|14|字段确为稳定0/1标志|业务状态不能压缩成布尔值|
|禁止直接自关联|4|拒绝对象直接关联自身|项目/任务完整防环仍由应用校验|
|非负数与计数一致性|17|仅约束物理不变量|不替代数量可分配性检查|

### 1.6 138个唯一键按业务语义分组

#### 业务身份键（27项）

决定业务编码、SN或单号能否重复；建议永久不复用。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`acc_deliverable_template`|`uk_deliverable_template`|`UNIQUE KEY uk_deliverable_template (tenant_id, template_code)`|确认租户内业务身份永久唯一|
|`ast_device_sn`|`uk_device_sn`|`UNIQUE KEY uk_device_sn (tenant_id, sn)`|确认租户内业务身份永久唯一|
|`ast_product`|`uk_product_code`|`UNIQUE KEY uk_product_code (tenant_id, product_code)`|确认租户内业务身份永久唯一|
|`com_contract`|`uk_contract_business`|`UNIQUE KEY uk_contract_business ( tenant_id, company_code, contract_no )`|确认租户内业务身份永久唯一|
|`com_crm_execution_order`|`uk_crm_execution`|`UNIQUE KEY uk_crm_execution ( tenant_id, source_system, execution_no )`|确认租户内业务身份永久唯一|
|`com_sales_order`|`uk_sales_order_business`|`UNIQUE KEY uk_sales_order_business ( tenant_id, source_system, company_code, order_type, order_no )`|确认租户内业务身份永久唯一|
|`com_sales_order_line`|`uk_sales_order_line`|`UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no)`|确认租户内业务身份永久唯一|
|`com_shipment_package`|`uk_shipment_package_no`|`UNIQUE KEY uk_shipment_package_no ( tenant_id, source_system, package_no )`|确认租户内业务身份永久唯一|
|`cus_customer`|`uk_customer_code`|`UNIQUE KEY uk_customer_code (tenant_id, customer_code)`|确认租户内业务身份永久唯一|
|`cus_market_relation`|`uk_market_relation_business`|`UNIQUE KEY uk_market_relation_business ( tenant_id, market_code, system_code, expend_code, industry_code )`|确认租户内业务身份永久唯一|
|`cut_cutover_checklist`|`uk_cutover_checklist_current`|`UNIQUE KEY uk_cutover_checklist_current ( tenant_id, cutover_task_id, current_marker )`|确认租户内业务身份永久唯一|
|`cut_cutover_checklist`|`uk_cutover_checklist_version`|`UNIQUE KEY uk_cutover_checklist_version ( tenant_id, cutover_task_id, checklist_version )`|确认租户内业务身份永久唯一|
|`cut_cutover_checklist_item`|`uk_cutover_checklist_item_key`|`UNIQUE KEY uk_cutover_checklist_item_key ( tenant_id, checklist_id, stable_item_key )`|确认租户内业务身份永久唯一|
|`cut_cutover_checklist_item_result`|`uk_cutover_checklist_item_result_current`|`UNIQUE KEY uk_cutover_checklist_item_result_current ( tenant_id, checklist_item_id, current_marker )`|确认租户内业务身份永久唯一|
|`cut_cutover_checklist_item_result`|`uk_cutover_checklist_item_result_version`|`UNIQUE KEY uk_cutover_checklist_item_result_version ( tenant_id, checklist_item_id, result_version )`|确认租户内业务身份永久唯一|
|`cut_cutover_closure`|`uk_cutover_closure_task`|`UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id)`|确认租户内业务身份永久唯一|
|`cut_cutover_support_arrangement`|`uk_cutover_support_arrangement_no`|`UNIQUE KEY uk_cutover_support_arrangement_no (tenant_id, plan_revision_id, arrangement_no)`|确认租户内业务身份永久唯一|
|`imp_configuration_collection_result`|`uk_configuration_collection_result`|`UNIQUE KEY uk_configuration_collection_result (tenant_id, collection_task_id, result_type_code, result_version_no)`|确认租户内业务身份永久唯一|
|`plt_business_document`|`uk_business_document_code`|`UNIQUE KEY uk_business_document_code (tenant_id, document_code)`|确认租户内业务身份永久唯一|
|`plt_sync_batch`|`uk_sync_batch_no`|`UNIQUE KEY uk_sync_batch_no (tenant_id, batch_no)`|确认租户内业务身份永久唯一|
|`proj_project`|`uk_project_code`|`UNIQUE KEY uk_project_code (tenant_id, project_code)`|确认租户内业务身份永久唯一|
|`proj_project_portfolio`|`uk_portfolio_code`|`UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code)`|确认租户内业务身份永久唯一|
|`proj_project_task_completion_evaluation`|`uk_project_task_completion_evaluation_idempotency`|`UNIQUE KEY uk_project_task_completion_evaluation_idempotency ( tenant_id, project_task_id, idempotency_key )`|确认租户内业务身份永久唯一|
|`proj_project_task_execution_contract`|`uk_project_task_execution_contract_current`|`UNIQUE KEY uk_project_task_execution_contract_current ( tenant_id, project_task_id, current_marker )`|确认租户内业务身份永久唯一|
|`proj_project_task_execution_contract`|`uk_project_task_execution_contract_version`|`UNIQUE KEY uk_project_task_execution_contract_version ( tenant_id, project_task_id, contract_version )`|确认租户内业务身份永久唯一|
|`proj_project_template_task_definition`|`uk_project_template_task_definition`|`UNIQUE KEY uk_project_template_task_definition ( tenant_id, template_revision_id, task_definition_key )`|确认租户内业务身份永久唯一|
|`srv_service_incident`|`uk_service_incident_no`|`UNIQUE KEY uk_service_incident_no (tenant_id, incident_no)`|确认租户内业务身份永久唯一|

#### 来源幂等键（15项）

决定外部记录重放时更新同一事实还是产生重复记录。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`ast_device_project_assignment`|`uk_device_assignment_source`|`UNIQUE KEY uk_device_assignment_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`ast_device_relation`|`uk_device_relation_source`|`UNIQUE KEY uk_device_relation_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`ast_device_shipment_event`|`uk_shipment_event_source`|`UNIQUE KEY uk_shipment_event_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`com_contract`|`uk_contract_master_source`|`UNIQUE KEY uk_contract_master_source ( tenant_id, master_source_system, master_source_record_key )`|确认来源键精确匹配且重放幂等|
|`com_contract_receivable`|`uk_contract_receivable_source`|`UNIQUE KEY uk_contract_receivable_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`com_crm_execution_config`|`uk_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config ( tenant_id, config_source, source_config_key )`|确认来源键精确匹配且重放幂等|
|`com_execution_order_merge_batch`|`uk_execution_merge_batch`|`UNIQUE KEY uk_execution_merge_batch ( tenant_id, source_system, source_merge_key )`|确认来源键精确匹配且重放幂等|
|`com_execution_order_merge_member`|`uk_execution_merge_member_source`|`UNIQUE KEY uk_execution_merge_member_source ( tenant_id, merge_batch_id, source_record_key )`|确认来源键精确匹配且重放幂等|
|`com_shipment_contract_reference`|`uk_shipment_contract_ref_source`|`UNIQUE KEY uk_shipment_contract_ref_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`com_shipment_package`|`uk_shipment_package_source`|`UNIQUE KEY uk_shipment_package_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`cus_market_relation`|`uk_market_relation_source`|`UNIQUE KEY uk_market_relation_source ( tenant_id, source_system, source_record_key )`|确认来源键精确匹配且重放幂等|
|`plt_external_key_mapping`|`uk_external_key_source_target`|`UNIQUE KEY uk_external_key_source_target ( tenant_id, source_system, source_table, source_pk, target_role, target_sequence, target_table, target_id )`|确认来源键精确匹配且重放幂等|
|`plt_migration_issue`|`uk_migration_issue_source`|`UNIQUE KEY uk_migration_issue_source ( tenant_id, batch_id, source_table, source_pk, issue_type )`|确认来源键精确匹配且重放幂等|
|`plt_migration_source_record`|`uk_migration_source_record`|`UNIQUE KEY uk_migration_source_record ( tenant_id, batch_id, source_system, source_table, source_pk )`|确认来源键精确匹配且重放幂等|
|`proj_project_party`|`uk_project_party_source`|`UNIQUE KEY uk_project_party_source ( tenant_id, source_system, source_table, source_record_key, party_role )`|确认来源键精确匹配且重放幂等|

#### 当前唯一记录（5项）

利用生成列仅限制当前有效记录。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`ast_device_component_relation`|`uk_device_component_current_slot`|`UNIQUE KEY uk_device_component_current_slot (tenant_id, chassis_device_id, current_slot_code)`|确认同一时点只能存在一条当前记录|
|`ast_device_project_assignment`|`uk_device_current_assignment`|`UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id)`|确认同一时点只能存在一条当前记录|
|`com_delivery_scope`|`uk_scope_current`|`UNIQUE KEY uk_scope_current ( tenant_id, project_id, current_order_line_id )`|确认同一时点只能存在一条当前记录|
|`cus_customer_contact`|`uk_customer_primary_contact`|`UNIQUE KEY uk_customer_primary_contact (tenant_id, primary_customer_id)`|确认同一时点只能存在一条当前记录|
|`proj_project_company_department_relation`|`uk_project_primary_company_department`|`UNIQUE KEY uk_project_primary_company_department ( tenant_id, primary_project_id, relation_role )`|确认同一时点只能存在一条当前记录|

#### 版本与永久序号（8项）

保证版本号或编码流水号不可重复、不可复用。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`acc_satisfaction_collection_task`|`uk_satisfaction_task_revision`|`UNIQUE KEY uk_satisfaction_task_revision (tenant_id, project_id, source_context, source_object_type, source_object_id, source_object_version, business_purpose_code, applicable_timing_code, payment_stage_key, task_revision_no)`|确认版本/序号只增不复用|
|`acc_satisfaction_questionnaire`|`uk_satisfaction_questionnaire_revision`|`UNIQUE KEY uk_satisfaction_questionnaire_revision (tenant_id, task_id, questionnaire_revision_no)`|确认版本/序号只增不复用|
|`acc_satisfaction_response`|`uk_satisfaction_response_sequence`|`UNIQUE KEY uk_satisfaction_response_sequence (tenant_id, questionnaire_id, response_no)`|确认版本/序号只增不复用|
|`acc_satisfaction_result`|`uk_satisfaction_result_sequence`|`UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no)`|确认版本/序号只增不复用|
|`ast_product_release`|`uk_product_release`|`UNIQUE KEY uk_product_release ( tenant_id, product_id, release_version, release_type )`|确认版本/序号只增不复用|
|`imp_configuration_collection_parse_attempt`|`uk_configuration_parse_attempt`|`UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)`|确认版本/序号只增不复用|
|`plt_document_version`|`uk_document_version`|`UNIQUE KEY uk_document_version (tenant_id, document_id, version_no)`|确认版本/序号只增不复用|
|`proj_project`|`uk_project_code_sequence`|`UNIQUE KEY uk_project_code_sequence (tenant_id, code_root_id, project_sequence)`|确认版本/序号只增不复用|

#### 关系事实粒度（17项）

决定哪些字段组合代表同一条关系事实。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`acc_satisfaction_response`|`uk_satisfaction_response_request`|`UNIQUE KEY uk_satisfaction_response_request (tenant_id, questionnaire_id, request_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`acc_satisfaction_result`|`uk_satisfaction_result_response`|`UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`ast_device_configuration_feature`|`uk_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature ( tenant_id, configuration_id, feature_code )`|确认字段完整表达关系粒度，重点检查NULL|
|`ast_device_configuration_service`|`uk_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service ( tenant_id, configuration_id, service_code )`|确认字段完整表达关系粒度，重点检查NULL|
|`ast_network_topology_device_relation`|`uk_topology_device`|`UNIQUE KEY uk_topology_device (tenant_id, topology_id, device_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`com_delivery_scope_detail`|`uk_delivery_scope_detail_sequence`|`UNIQUE KEY uk_delivery_scope_detail_sequence ( tenant_id, delivery_scope_id, detail_sequence )`|确认字段完整表达关系粒度，重点检查NULL|
|`com_order_change_relation`|`uk_order_change`|`UNIQUE KEY uk_order_change ( tenant_id, source_order_id, target_order_id, relation_type )`|确认字段完整表达关系粒度，重点检查NULL|
|`com_order_contract_relation`|`uk_order_contract`|`UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`com_order_execution_relation`|`uk_order_execution`|`UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`com_order_line_execution_relation`|`uk_order_line_execution`|`UNIQUE KEY uk_order_line_execution (tenant_id, order_line_id, execution_id)`|确认字段完整表达关系粒度，重点检查NULL|
|`com_project_contract_relation`|`uk_project_contract`|`UNIQUE KEY uk_project_contract ( tenant_id, project_id, contract_id, relation_role )`|确认字段完整表达关系粒度，重点检查NULL|
|`imp_configuration_component_candidate`|`uk_configuration_component_candidate`|`UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no)`|确认字段完整表达关系粒度，重点检查NULL|
|`proj_project_company_department_relation`|`uk_project_company_department_role`|`UNIQUE KEY uk_project_company_department_role ( tenant_id, project_id, company_code, department_code, relation_role, effective_from )`|确认字段完整表达关系粒度，重点检查NULL|
|`proj_project_member_assignment`|`uk_project_member_role`|`UNIQUE KEY uk_project_member_role ( tenant_id, project_id, user_id, member_role, effective_from )`|确认字段完整表达关系粒度，重点检查NULL|
|`proj_project_portfolio_member`|`uk_portfolio_project`|`UNIQUE KEY uk_portfolio_project ( tenant_id, portfolio_id, project_id, member_source )`|确认字段完整表达关系粒度，重点检查NULL|
|`proj_project_relation`|`uk_project_relation`|`UNIQUE KEY uk_project_relation ( tenant_id, source_project_id, target_project_id, relation_type )`|确认字段完整表达关系粒度，重点检查NULL|
|`srv_service_incident_device_relation`|`uk_incident_device`|`UNIQUE KEY uk_incident_device (tenant_id, incident_id, device_id)`|确认字段完整表达关系粒度，重点检查NULL|

#### 租户行引用键（66项）

支撑同租户复合引用，属于技术完整性。

|表|唯一键|当前字段组合|判断重点|
|---|---|---|---|
|`acc_deliverable_template`|`uk_deliverable_template_tenant_row`|`UNIQUE KEY uk_deliverable_template_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`acc_project_deliverable`|`uk_project_deliverable_tenant_row`|`UNIQUE KEY uk_project_deliverable_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`acc_satisfaction_collection_task`|`uk_satisfaction_collection_task_tenant_row`|`UNIQUE KEY uk_satisfaction_collection_task_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`acc_satisfaction_questionnaire`|`uk_satisfaction_questionnaire_tenant_row`|`UNIQUE KEY uk_satisfaction_questionnaire_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`acc_satisfaction_response`|`uk_satisfaction_response_tenant_row`|`UNIQUE KEY uk_satisfaction_response_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`acc_satisfaction_result`|`uk_satisfaction_result_tenant_row`|`UNIQUE KEY uk_satisfaction_result_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_component_relation`|`uk_device_component_relation_tenant_row`|`UNIQUE KEY uk_device_component_relation_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_configuration`|`uk_device_configuration_tenant_row`|`UNIQUE KEY uk_device_configuration_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_configuration_feature`|`uk_device_configuration_feature_tenant_row`|`UNIQUE KEY uk_device_configuration_feature_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_configuration_service`|`uk_device_configuration_service_tenant_row`|`UNIQUE KEY uk_device_configuration_service_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_project_assignment`|`uk_project_device_assignment_tenant_row`|`UNIQUE KEY uk_project_device_assignment_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_relation`|`uk_device_relation_tenant_row`|`UNIQUE KEY uk_device_relation_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_shipment_event`|`uk_device_shipment_event_tenant_row`|`UNIQUE KEY uk_device_shipment_event_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_sn`|`uk_device_sn_tenant_row`|`UNIQUE KEY uk_device_sn_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_device_version`|`uk_device_version_tenant_row`|`UNIQUE KEY uk_device_version_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_network_topology`|`uk_network_topology_tenant_row`|`UNIQUE KEY uk_network_topology_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_network_topology_device_relation`|`uk_topology_device_rel_tenant_row`|`UNIQUE KEY uk_topology_device_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_product`|`uk_product_tenant_row`|`UNIQUE KEY uk_product_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`ast_product_release`|`uk_product_release_tenant_row`|`UNIQUE KEY uk_product_release_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_contract`|`uk_contract_tenant_row`|`UNIQUE KEY uk_contract_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_contract_receivable`|`uk_contract_receivable_tenant_row`|`UNIQUE KEY uk_contract_receivable_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_crm_execution_config`|`uk_crm_execution_config_tenant_row`|`UNIQUE KEY uk_crm_execution_config_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_crm_execution_order`|`uk_crm_execution_order_tenant_row`|`UNIQUE KEY uk_crm_execution_order_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_delivery_scope`|`uk_project_order_line_scope_tenant_row`|`UNIQUE KEY uk_project_order_line_scope_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_delivery_scope_detail`|`uk_delivery_scope_detail_tenant_row`|`UNIQUE KEY uk_delivery_scope_detail_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_execution_order_merge_batch`|`uk_execution_merge_batch_tenant_row`|`UNIQUE KEY uk_execution_merge_batch_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_execution_order_merge_member`|`uk_execution_merge_member_tenant_row`|`UNIQUE KEY uk_execution_merge_member_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_order_change_relation`|`uk_order_change_rel_tenant_row`|`UNIQUE KEY uk_order_change_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_order_contract_relation`|`uk_order_contract_rel_tenant_row`|`UNIQUE KEY uk_order_contract_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_order_execution_relation`|`uk_order_execution_rel_tenant_row`|`UNIQUE KEY uk_order_execution_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_order_line_execution_relation`|`uk_order_line_execution_rel_tenant_row`|`UNIQUE KEY uk_order_line_execution_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_project_contract_relation`|`uk_project_contract_rel_tenant_row`|`UNIQUE KEY uk_project_contract_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_sales_order`|`uk_sales_order_tenant_row`|`UNIQUE KEY uk_sales_order_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_sales_order_line`|`uk_sales_order_line_tenant_row`|`UNIQUE KEY uk_sales_order_line_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_shipment_contract_reference`|`uk_shipment_contract_ref_tenant_row`|`UNIQUE KEY uk_shipment_contract_ref_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`com_shipment_package`|`uk_shipment_package_tenant_row`|`UNIQUE KEY uk_shipment_package_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cus_customer`|`uk_customer_tenant_row`|`UNIQUE KEY uk_customer_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cus_customer_contact`|`uk_customer_contact_tenant_row`|`UNIQUE KEY uk_customer_contact_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cus_market_relation`|`uk_market_relation_tenant_row`|`UNIQUE KEY uk_market_relation_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cut_cutover_checklist`|`uk_cutover_checklist_tenant_row`|`UNIQUE KEY uk_cutover_checklist_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cut_cutover_checklist_item`|`uk_cutover_checklist_item_tenant_row`|`UNIQUE KEY uk_cutover_checklist_item_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cut_cutover_checklist_item_result`|`uk_cutover_checklist_item_result_tenant_row`|`UNIQUE KEY uk_cutover_checklist_item_result_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cut_cutover_closure`|`uk_cutover_closure_tenant_row`|`UNIQUE KEY uk_cutover_closure_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`cut_cutover_support_arrangement`|`uk_cutover_support_arrangement_tenant_row`|`UNIQUE KEY uk_cutover_support_arrangement_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`imp_configuration_collection_parse_attempt`|`uk_configuration_parse_attempt_tenant_row`|`UNIQUE KEY uk_configuration_parse_attempt_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`imp_configuration_collection_result`|`uk_configuration_collection_result_tenant_row`|`UNIQUE KEY uk_configuration_collection_result_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`imp_configuration_component_candidate`|`uk_configuration_component_candidate_tenant_row`|`UNIQUE KEY uk_configuration_component_candidate_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_business_document`|`uk_business_document_tenant_row`|`UNIQUE KEY uk_business_document_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_document_version`|`uk_document_version_owner`|`UNIQUE KEY uk_document_version_owner (tenant_id, document_id, id)`|技术引用键，可按架构规则确认|
|`plt_document_version`|`uk_document_version_tenant_row`|`UNIQUE KEY uk_document_version_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_external_key_mapping`|`uk_external_key_map_tenant_row`|`UNIQUE KEY uk_external_key_map_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_migration_issue`|`uk_migration_issue_tenant_row`|`UNIQUE KEY uk_migration_issue_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_migration_source_record`|`uk_migration_source_record_tenant_row`|`UNIQUE KEY uk_migration_source_record_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`plt_sync_batch`|`uk_sync_batch_tenant_row`|`UNIQUE KEY uk_sync_batch_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project`|`uk_project_tenant_row`|`UNIQUE KEY uk_project_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_company_department_relation`|`uk_project_company_department_rel_tenant_row`|`UNIQUE KEY uk_project_company_department_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_member_assignment`|`uk_project_member_tenant_row`|`UNIQUE KEY uk_project_member_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_party`|`uk_project_party_tenant_row`|`UNIQUE KEY uk_project_party_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_portfolio`|`uk_portfolio_tenant_row`|`UNIQUE KEY uk_portfolio_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_portfolio_member`|`uk_portfolio_project_rel_tenant_row`|`UNIQUE KEY uk_portfolio_project_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_relation`|`uk_project_relation_tenant_row`|`UNIQUE KEY uk_project_relation_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_task_completion_evaluation`|`uk_project_task_completion_evaluation_tenant_row`|`UNIQUE KEY uk_project_task_completion_evaluation_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_task_execution_contract`|`uk_project_task_execution_contract_tenant_row`|`UNIQUE KEY uk_project_task_execution_contract_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`proj_project_template_task_definition`|`uk_project_template_task_definition_tenant_row`|`UNIQUE KEY uk_project_template_task_definition_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`srv_service_incident`|`uk_service_incident_tenant_row`|`UNIQUE KEY uk_service_incident_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|
|`srv_service_incident_device_relation`|`uk_incident_device_rel_tenant_row`|`UNIQUE KEY uk_incident_device_rel_tenant_row (tenant_id, id)`|技术引用键，可按架构规则确认|

### 1.7 8个可空唯一键逐项判断

|唯一键|可空列|NULL是否有意|判断|
|---|---|---|---|
|`uk_device_current_assignment`|`current_device_id`|是：仅当前归属生成设备ID|约束同一设备同一时点仅归属一个最具体项目；建议保留|
|`uk_scope_current`|`current_order_line_id`|是：仅当前范围生成订单行ID|约束同一项目—订单行只有一条当前范围；建议保留|
|`uk_customer_primary_contact`|`primary_customer_id`|是：仅主联系人生成客户ID|约束客户只有一个主联系人；建议保留|
|`uk_project_primary_company_department`|`primary_project_id`|是：仅主关系生成项目ID|按关系角色约束一个主公司部门关系；建议保留|
|`uk_device_component_current_slot`|`current_slot_code`|是：仅当前组件关系生成槽位编码|约束同一机框槽位同一时点最多一个当前板卡；建议保留|
|`uk_contract_master_source`|`master_source_record_key`|是：未取得主来源键时允许NULL|非NULL来源键必须唯一；建议保留并改为精确比较|
|`uk_project_company_department_role`|`department_code`、`effective_from`|尚无证据表明有意|NULL会允许相同项目/公司/角色重复，存在约束空洞|
|`uk_project_member_role`|`effective_from`|尚无证据表明有意|NULL会允许相同成员/角色重复，存在约束空洞|

### 1.8 103个CHECK按业务语义分组

|分组|数量|代表规则|建议|
|---|---:|---|---|
|软删除|45|`deleted IN (0,1)`|技术规则批量确认|
|时间顺序|18|结束不得早于开始|接受；旧数据缺失保持NULL|
|稳定布尔标志|14|主标记、必需标记等|仅稳定二值字段可接受|
|禁止直接自关联|4|设备/订单/项目不能直接关联自身|接受；多节点防环由应用校验|
|非负数与计数|17|序号、目标数、树深、成功失败计数|技术规则批量确认|
|跨字段不变量|4|附加SN、项目编码命名空间、部门配对、交付范围明细主体|按ADR和业务规则逐项确认|
|状态耦合|1|当前DDL不再用固定状态码触发允许值或必填规则|已按ADR-0023调整|

状态耦合CHECK当前为0项；AF证据、DeliveryScope生效数量和MigrationIssue关闭完整性改由受控业务动作校验并留痕。

9个当前唯一生成列逐项如下。Q03已确认其业务事实，表达式只依赖稳定有效期、删除标记或主标记，不依赖可扩展业务状态码：

|表/生成列|当前表达式|被保护的不变量|推荐调整|
|---|---|---|---|
|`acc_satisfaction_collection_task.payment_stage_key`|`COALESCE(payment_stage_code, '')`|当前唯一业务事实|已改为只依赖稳定有效期、删除标记或主标记|
|`ast_device_component_relation.current_slot_code`|`CASE WHEN effective_to IS NULL THEN slot_code ELSE NULL END`|同一机框槽位同一时点最多一个当前板卡|已改为只依赖稳定有效期、删除标记或主标记|
|`ast_device_project_assignment.current_device_id`|`CASE WHEN deleted = 0 AND effective_to IS NULL THEN device_id ELSE NULL END`|同一设备同一时点只有一个直接项目归属|已改为只依赖稳定有效期、删除标记或主标记|
|`com_delivery_scope.current_order_line_id`|`CASE WHEN deleted = 0 AND effective_to IS NULL THEN order_line_id ELSE NULL END`|同一项目—订单行只有一个当前交付范围|已改为只依赖稳定有效期、删除标记或主标记|
|`cus_customer_contact.primary_customer_id`|`CASE WHEN deleted = 0 AND is_primary = 1 THEN customer_id ELSE NULL END`|一个客户只有一个当前主联系人|已改为只依赖稳定有效期、删除标记或主标记|
|`cut_cutover_checklist.current_marker`|`CASE WHEN invalidated_at IS NULL THEN 1 ELSE NULL END`|当前唯一业务事实|已改为只依赖稳定有效期、删除标记或主标记|
|`cut_cutover_checklist_item_result.current_marker`|`CASE WHEN selection_ended_at IS NULL THEN 1 ELSE NULL END`|当前唯一业务事实|已改为只依赖稳定有效期、删除标记或主标记|
|`proj_project_company_department_relation.primary_project_id`|`CASE WHEN deleted = 0 AND effective_to IS NULL AND is_primary = 1 THEN project_id ELSE NULL END`|项目同一角色只有一个主公司部门关系|已改为只依赖稳定有效期、删除标记或主标记|
|`proj_project_task_execution_contract.current_marker`|`CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END`|当前唯一业务事实|已改为只依赖稳定有效期、删除标记或主标记|

另有1个非状态生成列也需明确边界：`ast_device_shipment_event.rma_marked`当前按RMA编号是否为空生成，并把字符串`null`视为空。该列只能作为迁移兼容和查询索引投影，不能替代已确认的`business_action_code`、方向和正负数量业务事实；字符串哨兵清洗必须在迁移规则中留痕。

### 1.9 27个精确匹配字段与排序规则

这些字段当前继承表级`utf8mb4_0900_ai_ci`。推荐来源键使用`utf8mb4_0900_bin`；契约明确为ASCII摘要的字段使用`ascii_bin`；不得改变原值大小写。

|表|字段|类型|可空|推荐比较语义|
|---|---|---|---:|---|
|`acc_satisfaction_collection_task`|`source_object_id`|`BIGINT`|否|`utf8mb4_0900_bin`|
|`ast_device_project_assignment`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`ast_device_relation`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`ast_device_shipment_event`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_contract`|`master_source_record_key`|`VARCHAR(128)`|是|`utf8mb4_0900_bin`|
|`com_contract_receivable`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_crm_execution_config`|`source_config_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_crm_execution_order`|`source_object_id`|`VARCHAR(64)`|是|`utf8mb4_0900_bin`|
|`com_delivery_scope_detail`|`source_record_key`|`VARCHAR(128)`|是|`utf8mb4_0900_bin`|
|`com_execution_order_merge_batch`|`source_merge_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_execution_order_merge_member`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_order_execution_relation`|`source_record_key`|`VARCHAR(128)`|是|`utf8mb4_0900_bin`|
|`com_order_line_execution_relation`|`source_record_key`|`VARCHAR(128)`|是|`utf8mb4_0900_bin`|
|`com_project_contract_relation`|`source_record_key`|`VARCHAR(128)`|是|`utf8mb4_0900_bin`|
|`com_shipment_contract_reference`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`com_shipment_package`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`cus_market_relation`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`plt_document_version`|`file_checksum`|`VARCHAR(128)`|是|`ascii_bin`（限定ASCII摘要时），否则`utf8mb4_0900_bin`|
|`plt_external_key_mapping`|`source_business_key`|`VARCHAR(512)`|是|`utf8mb4_0900_bin`|
|`plt_external_key_mapping`|`source_checksum`|`VARCHAR(128)`|是|`ascii_bin`（限定ASCII摘要时），否则`utf8mb4_0900_bin`|
|`plt_external_key_mapping`|`source_pk`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`plt_migration_issue`|`source_pk`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`plt_migration_source_record`|`source_business_key`|`VARCHAR(512)`|是|`utf8mb4_0900_bin`|
|`plt_migration_source_record`|`source_checksum`|`VARCHAR(128)`|否|`ascii_bin`（限定ASCII摘要时），否则`utf8mb4_0900_bin`|
|`plt_migration_source_record`|`source_pk`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|
|`plt_sync_batch`|`source_extract_checksum`|`VARCHAR(128)`|是|`ascii_bin`（限定ASCII摘要时），否则`utf8mb4_0900_bin`|
|`proj_project_party`|`source_record_key`|`VARCHAR(128)`|否|`utf8mb4_0900_bin`|

### 1.10 建议审批层次

|层次|内容|批准主体|批准结果|
|---|---|---|---|
|L1 已确认业务变化|ADR-0019～0022对应111项|需求Owner复核引用|回写逐项登记，不重复讨论|
|L2 数据架构不变量|主键、租户引用、同域外键、稳定技术CHECK|REQUIREMENT_OWNER_ACCEPTED / REVIEW_PENDING|2,079项已决策；待独立整体一致性复审|
|L3 业务唯一性与状态守卫|业务身份、来源幂等、当前唯一、关系粒度、状态耦合CHECK|需求Owner+数据架构Owner|九组已接受；待独立整体一致性复审|
|L4 性能候选|130个普通索引|REQUIREMENT_OWNER_ACCEPTED / REVIEW_PENDING；Feature Owner+性能Owner后续验证|已接受为候选索引；待独立整体一致性复审及后续性能验证；仍须由P3-E06压测定稿|
|L5 迁移运行证据|源库哈希、水位、脏数据量、对账、回退、切换|迁移Owner+独立复核人|AI-MIG-000实施/切换门禁关闭|

## 2. 表与字段完整清单

以下仅列当前核心迁移DDL中的表与字段；相对旧目录的`MATCH/ADDED/MODIFIED`状态以逐项决策登记为准。

|编号|表|字段数|字段清单|
|---|---|---:|---|
|T-001|`acc_deliverable_template`|14|`applicable_stage`、`create_time`、`creator`、`deleted`、`deliverable_type`、`id`、`required_flag`、`status`、`template_code`、`template_document_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-002|`acc_project_deliverable`|17|`accepted_time`、`create_time`、`creator`、`deleted`、`deliverable_type`、`document_id`、`id`、`owner_id`、`planned_due_date`、`project_id`、`status`、`submit_time`、`template_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-003|`acc_satisfaction_collection_task`|27|`applicable_timing_code`、`business_purpose_code`、`create_time`、`creator`、`current_responsible_user_id`、`delivery_scope_sha256`、`delivery_scope_snapshot`、`frozen_threshold`、`id`、`payment_stage_code`、`payment_stage_key`、`prior_task_id`、`project_id`、`remediation_ref`、`source_context`、`source_object_id`、`source_object_type`、`source_object_version`、`state_machine_version`、`status_code`、`task_revision_no`、`template_id`、`template_version`、`tenant_id`、`update_time`、`updater`、`version`|
|T-004|`acc_satisfaction_questionnaire`|16|`create_time`、`creator`、`frozen_question_json`、`frozen_threshold`、`id`、`prior_questionnaire_id`、`questionnaire_revision_no`、`remediation_ref`、`required_question_count`、`rule_version`、`source_questionnaire_key`、`source_questionnaire_version`、`task_id`、`template_id`、`template_version`、`tenant_id`|
|T-005|`acc_satisfaction_response`|15|`answer_json`、`attachment_refs_json`、`create_time`、`creator`、`id`、`item_validation_summary`、`questionnaire_id`、`request_id`、`required_validation_summary`、`response_no`、`response_valid`、`signature_ref`、`signature_valid`、`submit_time`、`tenant_id`|
|T-006|`acc_satisfaction_result`|21|`archive_artifact_id`、`archive_payload_sha256`、`archive_status_code`、`archive_time`、`blocking_reason`、`create_time`、`creator`、`decision_rule_version`、`decision_time`、`frozen_threshold`、`id`、`passed`、`questionnaire_id`、`required_items_valid`、`response_id`、`response_valid`、`result_no`、`score`、`signature_valid`、`tenant_id`、`validation_summary`|
|T-007|`ana_project_delivery_summary`|32|`active_scope_qty`、`company_code`、`company_id`、`company_name`、`contract_count`、`customer_code`、`customer_id`、`customer_name`、`department_code`、`department_id`、`department_name`、`device_count`、`erp_delivered_qty`、`manager_employee_no`、`manager_id`、`manager_name`、`order_count`、`order_line_count`、`parent_id`、`pending_mapping_count`、`pending_qty_count`、`project_code`、`project_id`、`project_name`、`project_status`、`project_type`、`root_id`、`source_batch_no`、`statistic_time`、`tenant_id`、`update_time`、`version`|
|T-008|`ast_device_component_relation`|15|`card_device_id`、`card_model_code`、`card_sn`、`chassis_device_id`、`chassis_sn`、`create_time`、`creator`、`current_slot_code`、`effective_from`、`effective_to`、`evidence_ref`、`id`、`relation_source_code`、`slot_code`、`tenant_id`|
|T-009|`ast_device_configuration`|17|`configuration_stage`、`create_time`、`creator`、`deleted`、`deployment_mode`、`device_id`、`effective_from`、`effective_to`、`id`、`install_location`、`management_address`、`project_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-010|`ast_device_configuration_feature`|11|`configuration_id`、`create_time`、`creator`、`deleted`、`feature_code`、`feature_name`、`feature_value`、`id`、`tenant_id`、`update_time`、`updater`|
|T-011|`ast_device_configuration_service`|11|`configuration_id`、`create_time`、`creator`、`deleted`、`id`、`service_code`、`service_endpoint`、`service_name`、`tenant_id`、`update_time`、`updater`|
|T-012|`ast_device_project_assignment`|33|`assignment_status`、`assignment_type`、`create_time`、`creator`、`current_device_id`、`deleted`、`device_id`、`device_sn`、`effective_from`、`effective_to`、`id`、`install_address`、`item_code`、`line_no`、`order_no`、`project_code`、`project_company_code`、`project_company_name`、`project_customer_code`、`project_customer_name`、`project_department_code`、`project_department_name`、`project_id`、`project_name`、`project_order_line_scope_id`、`source_record_key`、`source_system`、`status`、`tenant_id`、`transfer_batch_id`、`update_time`、`updater`、`version`|
|T-013|`ast_device_relation`|16|`contract_id`、`create_time`、`creator`、`deleted`、`effective_time`、`id`、`relation_type`、`source_device_id`、`source_record_key`、`source_system`、`status`、`target_device_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-014|`ast_device_shipment_event`|25|`business_action_code`、`create_time`、`creator`、`deleted`、`device_id`、`event_type`、`id`、`legacy_package_key`、`mapping_status`、`order_line_id`、`rma_marked`、`rma_no`、`rma_related_sn`、`shipment_package_id`、`shipment_time`、`source_record_key`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_month`、`warranty_start_date`|
|T-015|`ast_device_sn`|21|`asset_status`、`create_time`、`creator`、`deleted`、`hardware_customized`、`id`、`internal_serial_no`、`item_code`、`product_id`、`secondary_item`、`secondary_sn`、`sn`、`software_maintenance_status`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_status`|
|T-016|`ast_device_version`|19|`collected_time`、`component_name`、`component_type`、`create_time`、`creator`、`customized_flag`、`deleted`、`device_id`、`effective_from`、`effective_to`、`id`、`project_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`version_stage`、`version_value`|
|T-017|`ast_network_topology`|14|`create_time`、`creator`、`deleted`、`document_id`、`effective_from`、`effective_to`、`id`、`project_id`、`status`、`tenant_id`、`topology_name`、`update_time`、`updater`、`version`|
|T-018|`ast_network_topology_device_relation`|11|`create_time`、`creator`、`deleted`、`device_id`、`id`、`node_code`、`node_role`、`tenant_id`、`topology_id`、`update_time`、`updater`|
|T-019|`ast_product`|16|`create_time`、`creator`、`deleted`、`id`、`product_category_code`、`product_code`、`product_line_code`、`product_model`、`product_name`、`product_type`、`service_product_flag`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-020|`ast_product_release`|15|`create_time`、`creator`、`deleted`、`document_id`、`end_of_support_date`、`id`、`product_id`、`release_date`、`release_type`、`release_version`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-021|`com_contract`|24|`company_code`、`company_id`、`company_name`、`contract_name`、`contract_no`、`contract_type`、`create_time`、`creator`、`currency_code`、`customer_code`、`customer_id`、`customer_name`、`deleted`、`effective_date`、`expiry_date`、`id`、`master_source_record_key`、`master_source_system`、`source_sync_time`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-022|`com_contract_receivable`|57|`collected_amount`、`collected_ratio`、`company_code`、`company_id`、`company_name`、`company_resolution_source`、`contract_amount`、`contract_create_time`、`contract_id`、`contract_no`、`create_time`、`creator`、`currency_name`、`customer_code`、`customer_name`、`deleted`、`delivered_amount`、`expansion_department_code`、`expansion_department_id`、`expansion_department_name`、`expansion_department_source_key`、`id`、`import_batch_no`、`industry_code`、`industry_name`、`latest_ship_time`、`mapping_status`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`marketing_representative_code`、`marketing_representative_name`、`office_department_code`、`office_department_id`、`office_department_name`、`original_expansion_department_source_key`、`original_industry_name`、`original_system_department_source_key`、`overdue_amount`、`project_code`、`project_name`、`receivable_amount`、`secondary_representative_code`、`source_batch_code`、`source_effective_from`、`source_effective_to`、`source_order_no`、`source_record_key`、`source_sync_time`、`source_system`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`|
|T-023|`com_crm_execution_config`|34|`amount`、`borrow_qty`、`company_code`、`company_id`、`company_name`、`config_source`、`create_time`、`creator`、`crm_project_code`、`deleted`、`execution_id`、`id`、`is_af_evidence`、`item_code`、`item_model`、`item_name`、`line_type`、`memo`、`product_code`、`product_first_code`、`product_first_name`、`product_name`、`purchase_discount`、`purchase_price`、`qty`、`settlement_id`、`source_config_key`、`source_sync_time`、`status`、`tenant_id`、`unit_price`、`update_time`、`updater`、`version`|
|T-024|`com_crm_execution_order`|63|`af_evidence_status`、`af_project_amount`、`agent_name`、`application_type`、`channel_name`、`company_code`、`company_id`、`company_name`、`contact_name`、`contact_phone`、`create_time`、`creator`、`crm_project_code`、`crm_project_name`、`crm_project_type`、`customer_project_name`、`decision_path`、`deleted`、`engineering_fee`、`engineering_fee_raw`、`execution_no`、`expansion_department_code`、`expansion_department_id`、`expansion_department_name`、`expansion_department_source_key`、`final_customer_name`、`id`、`industry_code`、`industry_name`、`loan_reason`、`major_project_level`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`office_department_code`、`office_department_id`、`office_department_name`、`predicted_bid_time`、`primary_project_id`、`project_amount`、`project_manager_code`、`project_manager_name`、`receiver_address`、`receiver_contact`、`receiver_name`、`required_in_date`、`sales_rep_code`、`sales_rep_name`、`sales_rep_phone`、`service_type_name`、`source_object_id`、`source_sync_time`、`source_system`、`status`、`submit_time`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`、`version`|
|T-025|`com_delivery_scope`|36|`allocated_qty`、`allocation_source`、`change_reason`、`create_time`、`creator`、`current_order_line_id`、`deleted`、`effective_from`、`effective_to`、`id`、`item_code`、`item_desc`、`line_no`、`order_company_code`、`order_company_name`、`order_line_id`、`order_no`、`order_source_system`、`order_type`、`project_code`、`project_company_code`、`project_company_name`、`project_customer_code`、`project_customer_name`、`project_department_code`、`project_department_name`、`project_id`、`project_manager_employee_no`、`project_manager_name`、`project_name`、`scope_status`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-026|`com_delivery_scope_detail`|18|`allocated_qty`、`create_time`、`creator`、`deleted`、`delivery_batch_no`、`delivery_scope_id`、`detail_sequence`、`device_type_code`、`device_type_name`、`id`、`implementation_location`、`product_code`、`product_name`、`remark`、`source_record_key`、`tenant_id`、`update_time`、`updater`|
|T-027|`com_execution_order_merge_batch`|17|`agent_name`、`contract_id`、`create_time`、`creator`、`deleted`、`id`、`legacy_contract_no`、`primary_execution_id`、`project_name`、`source_merge_key`、`source_order_codes`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-028|`com_execution_order_merge_member`|16|`create_time`、`creator`、`deleted`、`execution_id`、`execution_no`、`execution_no_short`、`id`、`is_primary`、`member_sort`、`merge_batch_id`、`profit_center`、`source_order_code`、`source_record_key`、`tenant_id`、`update_time`、`updater`|
|T-029|`com_order_change_relation`|16|`change_batch_no`、`create_time`、`creator`、`deleted`、`effective_time`、`id`、`reason`、`relation_type`、`source_evidence`、`source_order_id`、`status`、`target_order_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-030|`com_order_contract_relation`|11|`contract_id`、`create_time`、`creator`、`deleted`、`id`、`order_id`、`relation_role`、`relation_source`、`tenant_id`、`update_time`、`updater`|
|T-031|`com_order_execution_relation`|15|`create_time`、`creator`、`deleted`、`execution_id`、`id`、`is_primary`、`mapping_status`、`order_id`、`relation_source`、`source_record_key`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-032|`com_order_line_execution_relation`|14|`create_time`、`creator`、`deleted`、`execution_id`、`id`、`mapping_status`、`order_line_id`、`relation_source`、`source_record_key`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-033|`com_project_contract_relation`|17|`contract_id`、`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`project_id`、`relation_role`、`source_record_key`、`source_system`、`source_table`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-034|`com_sales_order`|24|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`customer_required_time`、`deleted`、`id`、`order_comment`、`order_create_time`、`order_no`、`order_type`、`sales_type`、`source_project_name`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-035|`com_sales_order_line`|32|`bundle_code`、`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`deleted`、`delivered_qty`、`id`、`item_code`、`item_desc`、`line_no`、`line_type`、`open_qty`、`order_id`、`order_no`、`order_qty`、`order_type`、`product_id`、`profit_center`、`real_execution_no`、`source_sync_time`、`source_system`、`status`、`tenant_id`、`update_time`、`updater`、`version`、`warranty_month`|
|T-036|`com_shipment_contract_reference`|31|`company_code`、`company_id`、`company_name`、`contract_id`、`contract_no`、`contract_type`、`create_time`、`creator`、`customer_name`、`deleted`、`id`、`mapping_status`、`marketing_department_code`、`marketing_department_id`、`marketing_department_name`、`office_department_code`、`office_department_id`、`office_department_name`、`project_name`、`remark`、`source_record_key`、`source_sync_time`、`source_system`、`system_department_code`、`system_department_id`、`system_department_name`、`system_department_source_key`、`tenant_id`、`update_time`、`updater`、`warranty_flag`|
|T-037|`com_shipment_package`|19|`carrier_name`、`create_time`、`creator`、`deleted`、`express_no`、`id`、`mapping_status`、`package_no`、`receiver_name`、`shipment_contract_ref_id`、`shipment_time`、`source_record_key`、`source_sync_time`、`source_system`、`tenant_id`、`update_time`、`updater`、`warranty_end_time`、`warranty_start_time`|
|T-038|`cus_customer`|21|`create_time`、`creator`、`customer_address`、`customer_code`、`customer_name`、`deleted`、`expend_code`、`expend_name`、`id`、`industry_code`、`industry_name`、`market_code`、`market_name`、`service_level_code`、`status`、`system_code`、`system_name`、`tenant_id`、`update_time`、`updater`、`version`|
|T-039|`cus_customer_contact`|18|`contact_address`、`contact_name`、`create_time`、`creator`、`customer_department_name`、`customer_id`、`deleted`、`email`、`id`、`is_primary`、`phone`、`position_name`、`primary_customer_id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-040|`cus_market_relation`|20|`create_time`、`creator`、`deleted`、`expend_code`、`expend_name`、`id`、`industry_code`、`industry_name`、`market_code`、`market_name`、`source_record_key`、`source_sync_time`、`source_system`、`status`、`system_code`、`system_name`、`tenant_id`、`update_time`、`updater`、`version`|
|T-041|`cut_cutover_checklist`|22|`assessment_id`、`assessment_version`、`checklist_version`、`config_gap_snapshot`、`config_revision_snapshot`、`create_time`、`creator`、`current_marker`、`cutover_task_id`、`id`、`input_snapshot`、`input_snapshot_hash`、`invalidated_at`、`invalidated_reason`、`match_trace`、`status_code`、`submitted_at`、`submitted_by`、`tenant_id`、`update_time`、`updater`、`version`|
|T-042|`cut_cutover_checklist_item`|27|`applicable_flag`、`checklist_id`、`command_template_id`、`create_time`、`creator`、`custom_creator_user_id`、`device_id`、`display_condition_snapshot`、`id`、`interface_format_code`、`interface_schema_snapshot`、`item_definition_id`、`item_definition_version`、`item_description`、`item_name`、`item_type_code`、`matched_rule_id`、`matched_rule_version`、`required_flag`、`sort_order`、`source_code`、`stable_item_key`、`tenant_id`、`update_time`、`updater`、`version`、`work_mode_code`|
|T-043|`cut_cutover_checklist_item_result`|22|`answer_snapshot`、`checklist_item_id`、`collection_result_reference_id`、`collection_result_version`、`collection_task_id`、`created_at`、`created_by`、`current_marker`、`external_source_code`、`fact_description`、`id`、`load_failure_code`、`manual_evidence_file_reference`、`queried_at`、`query_condition_snapshot`、`result_source_code`、`result_version`、`selected_by`、`selection_ended_at`、`selection_reason_code`、`selection_started_at`、`tenant_id`|
|T-044|`cut_cutover_closure`|19|`archive_time`、`attachment_refs`、`collection_result_refs`、`create_time`、`creator`、`cutover_task_id`、`detail_description`、`execution_normal`、`id`、`legacy_item_text`、`plan_revision_id`、`precheck_normal`、`result_code`、`rollback_description`、`rollback_occurred`、`submitted_by`、`submitted_time`、`tenant_id`、`test_normal`|
|T-045|`cut_cutover_support_arrangement`|16|`arrangement_no`、`arrival_time`、`contact_info`、`create_time`、`creator`、`cutover_task_id`、`id`、`internal_user_id`、`person_name`、`person_type_code`、`plan_revision_id`、`role_code`、`task_duty`、`tenant_id`、`update_time`、`updater`|
|T-046|`imp_configuration_collection_parse_attempt`|12|`attempt_no`、`collection_result_id`、`completed_time`、`create_time`、`creator`、`error_summary`、`evidence_ref`、`id`、`parse_status_code`、`parser_version`、`started_time`、`tenant_id`|
|T-047|`imp_configuration_collection_result`|18|`collection_task_id`、`create_time`、`creator`、`device_id`、`device_snapshot`、`id`、`operated_time`、`operator_user_id`、`parser_version`、`project_id`、`project_snapshot`、`raw_log_file_id`、`raw_log_sha256`、`result_type_code`、`result_version_no`、`script_version`、`source_code`、`tenant_id`|
|T-048|`imp_configuration_component_candidate`|16|`candidate_no`、`card_configuration_ref`、`card_model_code`、`card_sn`、`chassis_sn`、`create_time`、`creator`、`evidence_ref`、`id`、`match_status_code`、`matched_device_id`、`parse_attempt_id`、`parse_revision_no`、`parser_version`、`slot_code`、`tenant_id`|
|T-049|`plt_business_document`|13|`create_time`、`creator`、`current_version_id`、`deleted`、`document_code`、`document_name`、`document_type`、`id`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-050|`plt_document_version`|15|`create_time`、`creator`、`deleted`、`document_id`、`file_checksum`、`file_id`、`file_name`、`id`、`status`、`tenant_id`、`update_time`、`updater`、`uploaded_by`、`uploaded_time`、`version_no`|
|T-051|`plt_external_key_mapping`|17|`batch_id`、`create_time`、`creator`、`id`、`mapping_status`、`source_business_key`、`source_checksum`、`source_pk`、`source_system`、`source_table`、`target_id`、`target_role`、`target_sequence`、`target_table`、`tenant_id`、`update_time`、`updater`|
|T-052|`plt_migration_issue`|18|`batch_id`、`candidate_target_ids`、`create_time`、`creator`、`id`、`issue_type`、`raw_business_key`、`raw_payload`、`resolution_action`、`resolution_status`、`resolved_time`、`resolver`、`source_pk`、`source_system`、`source_table`、`tenant_id`、`update_time`、`updater`|
|T-053|`plt_migration_source_record`|16|`batch_id`、`create_time`、`creator`、`extracted_time`、`id`、`mapped_target_count`、`mapping_status`、`source_business_key`、`source_checksum`、`source_payload`、`source_pk`、`source_system`、`source_table`、`tenant_id`、`update_time`、`updater`|
|T-054|`plt_sync_batch`|20|`batch_no`、`create_time`、`creator`、`error_summary`、`failure_count`、`finished_time`、`id`、`object_type`、`read_count`、`source_cursor`、`source_extract_checksum`、`source_extract_location`、`source_system`、`started_time`、`status`、`success_count`、`sync_mode`、`tenant_id`、`update_time`、`updater`|
|T-055|`proj_project`|53|`business_type`、`code_root_id`、`code_rule_version`、`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`customer_code`、`customer_id`、`customer_name`、`customer_project_name`、`deleted`、`department_code`、`department_id`、`department_name`、`expend_code`、`expend_name`、`id`、`implementation_mode`、`industry_code`、`industry_name`、`lifecycle_template_id`、`major_project_level`、`manager_employee_no`、`manager_id`、`manager_name`、`market_code`、`market_name`、`not_track_reason`、`parent_id`、`project_category`、`project_close_time`、`project_code`、`project_name`、`project_refresh_time`、`project_sequence`、`project_start_time`、`project_type`、`root_id`、`sales_type`、`service_level_code`、`source_type`、`status`、`system_code`、`system_name`、`tenant_id`、`tree_depth`、`tree_path`、`tree_sort`、`update_time`、`updater`、`version`|
|T-056|`proj_project_company_department_relation`|21|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`deleted`、`department_code`、`department_id`、`department_name`、`effective_from`、`effective_to`、`id`、`is_primary`、`primary_project_id`、`project_id`、`relation_role`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-057|`proj_project_member_assignment`|22|`company_code`、`company_id`、`company_name`、`create_time`、`creator`、`deleted`、`department_code`、`department_name`、`effective_from`、`effective_to`、`employee_no`、`id`、`member_name`、`member_role`、`project_id`、`responsibility`、`status`、`tenant_id`、`update_time`、`updater`、`user_id`、`version`|
|T-058|`proj_project_party`|20|`contact_name`、`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`party_code`、`party_name`、`party_role`、`phone`、`project_id`、`source_record_key`、`source_system`、`source_table`、`status`、`tenant_id`、`update_time`、`updater`、`version`|
|T-059|`proj_project_portfolio`|16|`create_time`、`creator`、`deleted`、`id`、`member_rule`、`member_rule_type`、`owner_id`、`portfolio_code`、`portfolio_name`、`status`、`tenant_id`、`update_time`、`updater`、`valid_from`、`valid_to`、`version`|
|T-060|`proj_project_portfolio_member`|12|`create_time`、`creator`、`deleted`、`effective_from`、`effective_to`、`id`、`member_source`、`portfolio_id`、`project_id`、`tenant_id`、`update_time`、`updater`|
|T-061|`proj_project_relation`|14|`create_time`、`creator`、`deleted`、`effective_time`、`id`、`reason`、`relation_type`、`source_project_id`、`status`、`target_project_id`、`tenant_id`、`update_time`、`updater`、`version`|
|T-062|`proj_project_task_completion_evaluation`|19|`command_id`、`contract_version`、`create_time`、`creator`、`evaluated_at`、`evaluated_by`、`evaluation_result_code`、`execution_contract_id`、`fact_context_code`、`fact_object_key`、`fact_object_type`、`fact_version`、`gate_snapshot_ref`、`id`、`idempotency_key`、`project_task_id`、`task_version`、`tenant_id`、`unmet_item_snapshot`|
|T-063|`proj_project_task_execution_contract`|26|`approval_instance_id`、`binding_parameter_snapshot`、`completion_rule_snapshot`、`completion_rule_type_code`、`component_key`、`contract_version`、`create_time`、`creator`、`current_marker`、`dynamic_form_revision_id`、`effective_from`、`effective_to`、`gate_ref`、`id`、`permission_policy_ref`、`project_task_id`、`source_definition_version`、`target_context_code`、`target_object_key`、`target_object_type`、`template_task_definition_id`、`tenant_id`、`update_time`、`updater`、`version`、`work_binding_type_code`|
|T-064|`proj_project_template_task_definition`|26|`approval_definition_key`、`binding_config`、`completion_rule_config`、`completion_rule_type_code`、`component_key`、`create_time`、`creator`、`definition_version`、`dynamic_form_revision_id`、`gate_ref`、`id`、`name`、`parent_task_definition_key`、`permission_policy_ref`、`sort_order`、`stage_definition_key`、`target_context_code`、`target_object_key`、`target_object_type`、`task_definition_key`、`template_revision_id`、`tenant_id`、`update_time`、`updater`、`version`、`work_binding_type_code`|
|T-065|`srv_service_incident`|23|`closed_time`、`create_time`、`creator`、`deleted`、`id`、`incident_no`、`incident_title`、`incident_type`、`occurred_time`、`owner_id`、`project_id`、`report_document_id`、`reported_time`、`restored_time`、`root_cause`、`severity`、`solution`、`status`、`symptom`、`tenant_id`、`update_time`、`updater`、`version`|
|T-066|`srv_service_incident_device_relation`|10|`create_time`、`creator`、`deleted`、`device_id`、`id`、`impact_description`、`incident_id`、`tenant_id`、`update_time`、`updater`|

## 3. 表选项完整清单

|编号|表|当前表选项|建议|
|---|---|---|---|
|O-001|`acc_deliverable_template`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交付件类型和模板配置'`|逐项决策已完成，待独立整体一致性复审|
|O-002|`acc_project_deliverable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目交付件实例及完成状态'`|逐项决策已完成，待独立整体一致性复审|
|O-003|`acc_satisfaction_collection_task`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目成员承办的满意度收集领域任务'`|逐项决策已完成，待独立整体一致性复审|
|O-004|`acc_satisfaction_questionnaire`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的满意度问卷冻结实例'`|逐项决策已完成，待独立整体一致性复审|
|O-005|`acc_satisfaction_response`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的客户满意度答卷、签字和附件事实'`|逐项决策已完成，待独立整体一致性复审|
|O-006|`acc_satisfaction_result`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的满意度评分、阈值与达标判定事实'`|逐项决策已完成，待独立整体一致性复审|
|O-007|`ana_project_delivery_summary`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '可重建的项目合同、订单、发货和SN汇总读模型'`|逐项决策已完成，待独立整体一致性复审|
|O-008|`ast_device_component_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '机框、槽位与板卡的当前及历史关系'`|逐项决策已完成，待独立整体一致性复审|
|O-009|`ast_device_configuration`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备分阶段配置主记录'`|逐项决策已完成，待独立整体一致性复审|
|O-010|`ast_device_configuration_feature`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置启用特性明细'`|逐项决策已完成，待独立整体一致性复审|
|O-011|`ast_device_configuration_service`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置运行服务明细'`|逐项决策已完成，待独立整体一致性复审|
|O-012|`ast_device_project_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN到项目的归属及转移历史'`|逐项决策已完成，待独立整体一致性复审|
|O-013|`ast_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同维度主附加SN、RMA替换等设备关系'`|逐项决策已完成，待独立整体一致性复审|
|O-014|`ast_device_shipment_event`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备发货、退回、返还和再次发放的物流生命周期事件'`|逐项决策已完成，待独立整体一致性复审|
|O-015|`ast_device_sn`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN主档，不承载重复发货事件'`|逐项决策已完成，待独立整体一致性复审|
|O-016|`ast_device_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备组件版本及阶段历史'`|逐项决策已完成，待独立整体一致性复审|
|O-017|`ast_network_topology`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目网络拓扑版本'`|逐项决策已完成，待独立整体一致性复审|
|O-018|`ast_network_topology_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '拓扑节点与设备关系'`|逐项决策已完成，待独立整体一致性复审|
|O-019|`ast_product`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品主档，安服属性由产品配置判定'`|逐项决策已完成，待独立整体一致性复审|
|O-020|`ast_product_release`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品版本发布与支持周期'`|逐项决策已完成，待独立整体一致性复审|
|O-021|`com_contract`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同主档，以所属公司和合同号为业务唯一键'`|逐项决策已完成，待独立整体一致性复审|
|O-022|`com_contract_receivable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SAP合同回款来源记录，保留公司待解析和一号多行证据'`|逐项决策已完成，待独立整体一致性复审|
|O-023|`com_crm_execution_config`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM已获得的执行单产品配置，仅作辅助证据'`|逐项决策已完成，待独立整体一致性复审|
|O-024|`com_crm_execution_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM执行单辅助主档，安服仅保存正向证据'`|逐项决策已完成，待独立整体一致性复审|
|O-025|`com_delivery_scope`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目对ERP订单行的权威实施范围'`|逐项决策已完成，待独立整体一致性复审|
|O-026|`com_delivery_scope_detail`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交付范围按地点、产品或设备类型及批次拆分的明细'`|逐项决策已完成，待独立整体一致性复审|
|O-027|`com_execution_order_merge_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊业务合并下单批次'`|逐项决策已完成，待独立整体一致性复审|
|O-028|`com_execution_order_merge_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊合并下单执行单成员，不限制成员数量'`|逐项决策已完成，待独立整体一致性复审|
|O-029|`com_order_change_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '改单、拆分、替代和退货订单血缘'`|逐项决策已完成，待独立整体一致性复审|
|O-030|`com_order_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同与ERP订单N:N关系'`|逐项决策已完成，待独立整体一致性复审|
|O-031|`com_order_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单与CRM执行单辅助关系'`|逐项决策已完成，待独立整体一致性复审|
|O-032|`com_order_line_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单行与CRM执行单辅助关系'`|逐项决策已完成，待独立整体一致性复审|
|O-033|`com_project_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目与合同直接N:N关系'`|逐项决策已完成，待独立整体一致性复审|
|O-034|`com_sales_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单主档'`|逐项决策已完成，待独立整体一致性复审|
|O-035|`com_sales_order_line`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单行及数量快照'`|逐项决策已完成，待独立整体一致性复审|
|O-036|`com_shipment_contract_reference`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货记录的合同归属，不作为合同主档'`|逐项决策已完成，待独立整体一致性复审|
|O-037|`com_shipment_package`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货装箱单主档'`|逐项决策已完成，待独立整体一致性复审|
|O-038|`cus_customer`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户主档'`|逐项决策已完成，待独立整体一致性复审|
|O-039|`cus_customer_contact`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户联系人'`|逐项决策已完成，待独立整体一致性复审|
|O-040|`cus_market_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM同步的客户市场行业划分组合目录'`|逐项决策已完成，待独立整体一致性复审|
|O-041|`cut_cutover_checklist`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-03调研及风险考察清单的输入、匹配与配置缺口版本'`|逐项决策已完成，待独立整体一致性复审|
|O-042|`cut_cutover_checklist_item`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-03清单版本内稳定采集项、界面与匹配快照'`|逐项决策已完成，待独立整体一致性复审|
|O-043|`cut_cutover_checklist_item_result`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-03直接填写、采集、外部加载和人工降级结果的追加事实'`|逐项决策已完成，待独立整体一致性复审|
|O-044|`cut_cutover_closure`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-06 P6轻量闭环与归档事实，不保存逐步骤执行或稳定观察'`|逐项决策已完成，待独立整体一致性复审|
|O-045|`cut_cutover_support_arrangement`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-04方案从属保障人员安排，不具有工单状态或责任区间'`|逐项决策已完成，待独立整体一致性复审|
|O-046|`imp_configuration_collection_parse_attempt`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置采集结果解析尝试，不覆盖原始配置Log'`|逐项决策已完成，待独立整体一致性复审|
|O-047|`imp_configuration_collection_result`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的配置采集结果、整机Log及项目设备快照'`|逐项决策已完成，待独立整体一致性复审|
|O-048|`imp_configuration_component_candidate`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置Log解析形成的板卡候选及待匹配证据'`|逐项决策已完成，待独立整体一致性复审|
|O-049|`plt_business_document`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档元数据'`|逐项决策已完成，待独立整体一致性复审|
|O-050|`plt_document_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档不可变版本'`|逐项决策已完成，待独立整体一致性复审|
|O-051|`plt_external_key_mapping`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旧主键到新主键的可追溯映射'`|逐项决策已完成，待独立整体一致性复审|
|O-052|`plt_migration_issue`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移缺失、重复、多义映射和人工解决记录'`|逐项决策已完成，待独立整体一致性复审|
|O-053|`plt_migration_source_record`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移批次逐源行的完整原值证据，不因目标归并或去重而覆盖'`|逐项决策已完成，待独立整体一致性复审|
|O-054|`plt_sync_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '一次性迁移及只读同步批次'`|逐项决策已完成，待独立整体一致性复审|
|O-055|`proj_project`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目主档及非固定层级项目树'`|逐项决策已完成，待独立整体一致性复审|
|O-056|`proj_project_company_department_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目业务角色下的公司与部门组合关系，保留配对但不建立全局主数据从属关系'`|逐项决策已完成，待独立整体一致性复审|
|O-057|`proj_project_member_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目成员、角色及有效期'`|逐项决策已完成，待独立整体一致性复审|
|O-058|`proj_project_party`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目参与方，按合同客户、最终用户、代理商、服务商等角色保存'`|逐项决策已完成，待独立整体一致性复审|
|O-059|`proj_project_portfolio`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合，不改变项目父子层级'`|逐项决策已完成，待独立整体一致性复审|
|O-060|`proj_project_portfolio_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合成员'`|逐项决策已完成，待独立整体一致性复审|
|O-061|`proj_project_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '扩容、续采、改造等非树项目关系'`|逐项决策已完成，待独立整体一致性复审|
|O-062|`proj_project_task_completion_evaluation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ProjectTask完成规则对绑定事实的不可覆盖判定记录'`|逐项决策已完成，待独立整体一致性复审|
|O-063|`proj_project_task_execution_contract`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ProjectTask当前及历史WorkBinding、权限与完成规则冻结契约'`|逐项决策已完成，待独立整体一致性复审|
|O-064|`proj_project_template_task_definition`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目模板版本内的Stage-Task执行定义，发布后不可覆盖'`|逐项决策已完成，待独立整体一致性复审|
|O-065|`srv_service_incident`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障及服务事件主档'`|逐项决策已完成，待独立整体一致性复审|
|O-066|`srv_service_incident_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障与受影响设备多对多关系'`|逐项决策已完成，待独立整体一致性复审|

## 4. 主键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|PK-001|`acc_deliverable_template`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-002|`acc_project_deliverable`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-003|`acc_satisfaction_collection_task`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-004|`acc_satisfaction_questionnaire`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-005|`acc_satisfaction_response`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-006|`acc_satisfaction_result`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-007|`ana_project_delivery_summary`|`PRIMARY KEY (tenant_id, project_id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-008|`ast_device_component_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-009|`ast_device_configuration`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-010|`ast_device_configuration_feature`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-011|`ast_device_configuration_service`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-012|`ast_device_project_assignment`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-013|`ast_device_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-014|`ast_device_shipment_event`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-015|`ast_device_sn`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-016|`ast_device_version`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-017|`ast_network_topology`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-018|`ast_network_topology_device_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-019|`ast_product`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-020|`ast_product_release`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-021|`com_contract`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-022|`com_contract_receivable`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-023|`com_crm_execution_config`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-024|`com_crm_execution_order`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-025|`com_delivery_scope`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-026|`com_delivery_scope_detail`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-027|`com_execution_order_merge_batch`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-028|`com_execution_order_merge_member`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-029|`com_order_change_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-030|`com_order_contract_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-031|`com_order_execution_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-032|`com_order_line_execution_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-033|`com_project_contract_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-034|`com_sales_order`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-035|`com_sales_order_line`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-036|`com_shipment_contract_reference`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-037|`com_shipment_package`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-038|`cus_customer`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-039|`cus_customer_contact`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-040|`cus_market_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-041|`cut_cutover_checklist`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-042|`cut_cutover_checklist_item`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-043|`cut_cutover_checklist_item_result`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-044|`cut_cutover_closure`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-045|`cut_cutover_support_arrangement`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-046|`imp_configuration_collection_parse_attempt`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-047|`imp_configuration_collection_result`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-048|`imp_configuration_component_candidate`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-049|`plt_business_document`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-050|`plt_document_version`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-051|`plt_external_key_mapping`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-052|`plt_migration_issue`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-053|`plt_migration_source_record`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-054|`plt_sync_batch`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-055|`proj_project`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-056|`proj_project_company_department_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-057|`proj_project_member_assignment`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-058|`proj_project_party`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-059|`proj_project_portfolio`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-060|`proj_project_portfolio_member`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-061|`proj_project_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-062|`proj_project_task_completion_evaluation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-063|`proj_project_task_execution_contract`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-064|`proj_project_template_task_definition`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-065|`srv_service_incident`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|PK-066|`srv_service_incident_device_relation`|`PRIMARY KEY (id)`|逐项决策已完成，待独立整体一致性复审；Q07|

## 5. 外键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|FK-001|`acc_project_deliverable`|`CONSTRAINT fk_project_deliverable_template FOREIGN KEY (tenant_id, template_id) REFERENCES acc_deliverable_template (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-002|`ast_device_configuration`|`CONSTRAINT fk_device_configuration_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-003|`ast_device_configuration_feature`|`CONSTRAINT fk_configuration_feature_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-004|`ast_device_configuration_service`|`CONSTRAINT fk_configuration_service_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-005|`ast_device_project_assignment`|`CONSTRAINT fk_device_assignment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-006|`ast_device_relation`|`CONSTRAINT fk_device_relation_source FOREIGN KEY (tenant_id, source_device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-007|`ast_device_relation`|`CONSTRAINT fk_device_relation_target FOREIGN KEY (tenant_id, target_device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-008|`ast_device_shipment_event`|`CONSTRAINT fk_shipment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-009|`ast_device_version`|`CONSTRAINT fk_device_version_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-010|`ast_network_topology_device_relation`|`CONSTRAINT fk_topology_device_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-011|`ast_network_topology_device_relation`|`CONSTRAINT fk_topology_device_topology FOREIGN KEY (tenant_id, topology_id) REFERENCES ast_network_topology (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-012|`ast_product_release`|`CONSTRAINT fk_product_release_product FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-013|`com_contract_receivable`|`CONSTRAINT fk_contract_receivable_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-014|`com_crm_execution_config`|`CONSTRAINT fk_crm_execution_config_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-015|`com_delivery_scope`|`CONSTRAINT fk_scope_order_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-016|`com_delivery_scope_detail`|`CONSTRAINT fk_delivery_scope_detail_scope FOREIGN KEY (tenant_id, delivery_scope_id) REFERENCES com_delivery_scope (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-017|`com_execution_order_merge_batch`|`CONSTRAINT fk_execution_merge_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-018|`com_execution_order_merge_batch`|`CONSTRAINT fk_execution_merge_primary FOREIGN KEY (tenant_id, primary_execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-019|`com_execution_order_merge_member`|`CONSTRAINT fk_execution_merge_member_batch FOREIGN KEY (tenant_id, merge_batch_id) REFERENCES com_execution_order_merge_batch (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-020|`com_execution_order_merge_member`|`CONSTRAINT fk_execution_merge_member_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-021|`com_order_change_relation`|`CONSTRAINT fk_order_change_source FOREIGN KEY (tenant_id, source_order_id) REFERENCES com_sales_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-022|`com_order_change_relation`|`CONSTRAINT fk_order_change_target FOREIGN KEY (tenant_id, target_order_id) REFERENCES com_sales_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-023|`com_order_contract_relation`|`CONSTRAINT fk_order_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-024|`com_order_contract_relation`|`CONSTRAINT fk_order_contract_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-025|`com_order_execution_relation`|`CONSTRAINT fk_order_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-026|`com_order_execution_relation`|`CONSTRAINT fk_order_execution_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-027|`com_order_line_execution_relation`|`CONSTRAINT fk_order_line_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-028|`com_order_line_execution_relation`|`CONSTRAINT fk_order_line_execution_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-029|`com_project_contract_relation`|`CONSTRAINT fk_project_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-030|`com_sales_order_line`|`CONSTRAINT fk_sales_order_line_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-031|`com_shipment_contract_reference`|`CONSTRAINT fk_shipment_contract_ref_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-032|`com_shipment_package`|`CONSTRAINT fk_shipment_package_contract_ref FOREIGN KEY (tenant_id, shipment_contract_ref_id) REFERENCES com_shipment_contract_reference (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-033|`cus_customer_contact`|`CONSTRAINT fk_customer_contact_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-034|`cut_cutover_checklist_item`|`CONSTRAINT fk_cutover_checklist_item_checklist FOREIGN KEY (tenant_id, checklist_id) REFERENCES cut_cutover_checklist (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-035|`cut_cutover_checklist_item_result`|`CONSTRAINT fk_cutover_checklist_item_result_item FOREIGN KEY (tenant_id, checklist_item_id) REFERENCES cut_cutover_checklist_item (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-036|`plt_business_document`|`CONSTRAINT fk_business_document_current_version FOREIGN KEY (tenant_id, id, current_version_id) REFERENCES plt_document_version (tenant_id, document_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-037|`plt_document_version`|`CONSTRAINT fk_document_version_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-038|`plt_external_key_mapping`|`CONSTRAINT fk_external_key_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-039|`plt_migration_issue`|`CONSTRAINT fk_migration_issue_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-040|`plt_migration_source_record`|`CONSTRAINT fk_migration_source_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-041|`proj_project`|`CONSTRAINT fk_project_code_root FOREIGN KEY (tenant_id, code_root_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-042|`proj_project`|`CONSTRAINT fk_project_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-043|`proj_project_company_department_relation`|`CONSTRAINT fk_project_company_department_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-044|`proj_project_member_assignment`|`CONSTRAINT fk_project_member_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-045|`proj_project_party`|`CONSTRAINT fk_project_party_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-046|`proj_project_portfolio_member`|`CONSTRAINT fk_portfolio_project_portfolio FOREIGN KEY (tenant_id, portfolio_id) REFERENCES proj_project_portfolio (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-047|`proj_project_portfolio_member`|`CONSTRAINT fk_portfolio_project_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-048|`proj_project_relation`|`CONSTRAINT fk_project_rel_source FOREIGN KEY (tenant_id, source_project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-049|`proj_project_relation`|`CONSTRAINT fk_project_rel_target FOREIGN KEY (tenant_id, target_project_id) REFERENCES proj_project (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-050|`proj_project_task_completion_evaluation`|`CONSTRAINT fk_project_task_completion_evaluation_contract FOREIGN KEY (tenant_id, execution_contract_id) REFERENCES proj_project_task_execution_contract (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-051|`proj_project_task_execution_contract`|`CONSTRAINT fk_project_task_execution_contract_definition FOREIGN KEY (tenant_id, template_task_definition_id) REFERENCES proj_project_template_task_definition (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|
|FK-052|`srv_service_incident_device_relation`|`CONSTRAINT fk_incident_device_incident FOREIGN KEY (tenant_id, incident_id) REFERENCES srv_service_incident (tenant_id, id)`|逐项决策已完成，待独立整体一致性复审；Q07|

## 6. 普通索引完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|IX-001|`acc_project_deliverable`|`KEY idx_deliverable_owner (tenant_id, owner_id, status, planned_due_date)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-002|`acc_project_deliverable`|`KEY idx_project_deliverable (tenant_id, project_id, deliverable_type, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-003|`acc_satisfaction_collection_task`|`KEY idx_satisfaction_task_owner (tenant_id, current_responsible_user_id, status_code)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-004|`acc_satisfaction_collection_task`|`KEY idx_satisfaction_task_source (tenant_id, source_context, source_object_type, source_object_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-005|`acc_satisfaction_questionnaire`|`KEY idx_satisfaction_questionnaire_task (tenant_id, task_id, create_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-006|`acc_satisfaction_response`|`KEY idx_satisfaction_response_questionnaire (tenant_id, questionnaire_id, submit_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-007|`acc_satisfaction_result`|`KEY idx_satisfaction_result_gate (tenant_id, passed, decision_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-008|`ana_project_delivery_summary`|`KEY idx_project_summary_company_department ( tenant_id, company_code, department_code, project_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-009|`ana_project_delivery_summary`|`KEY idx_project_summary_customer ( tenant_id, customer_code, project_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-010|`ana_project_delivery_summary`|`KEY idx_project_summary_manager ( tenant_id, manager_employee_no, project_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-011|`ana_project_delivery_summary`|`KEY idx_project_summary_project_status ( tenant_id, project_status, project_type, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-012|`ana_project_delivery_summary`|`KEY idx_project_summary_status ( tenant_id, pending_mapping_count, pending_qty_count )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-013|`ana_project_delivery_summary`|`KEY idx_project_summary_time (tenant_id, statistic_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-014|`ast_device_component_relation`|`KEY idx_device_component_card (tenant_id, card_sn, effective_to)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-015|`ast_device_component_relation`|`KEY idx_device_component_chassis (tenant_id, chassis_sn, effective_from)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-016|`ast_device_configuration`|`KEY idx_device_configuration (tenant_id, device_id, status, effective_from)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-017|`ast_device_configuration`|`KEY idx_project_configuration (tenant_id, project_id, configuration_stage)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-018|`ast_device_project_assignment`|`KEY idx_device_assignment_company_department ( tenant_id, project_company_code, project_department_code, effective_to, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-019|`ast_device_project_assignment`|`KEY idx_device_assignment_customer ( tenant_id, project_customer_code, effective_to, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-020|`ast_device_project_assignment`|`KEY idx_device_assignment_device ( tenant_id, device_id, effective_to, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-021|`ast_device_project_assignment`|`KEY idx_device_assignment_order ( tenant_id, order_no, line_no, effective_to, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-022|`ast_device_project_assignment`|`KEY idx_device_assignment_project ( tenant_id, project_id, effective_to, device_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-023|`ast_device_project_assignment`|`KEY idx_device_assignment_project_code ( tenant_id, project_code, effective_to, device_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-024|`ast_device_project_assignment`|`KEY idx_device_assignment_sn ( tenant_id, device_sn, effective_to, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-025|`ast_device_relation`|`KEY idx_device_relation_contract_refresh ( tenant_id, contract_id, relation_type, status, source_device_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-026|`ast_device_relation`|`KEY idx_device_relation_latest ( tenant_id, source_device_id, contract_id, relation_type, status, effective_time, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-027|`ast_device_relation`|`KEY idx_device_relation_source_device ( tenant_id, source_device_id, relation_type )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-028|`ast_device_relation`|`KEY idx_device_relation_target_device ( tenant_id, target_device_id, relation_type )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-029|`ast_device_shipment_event`|`KEY idx_shipment_device (tenant_id, device_id, shipment_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-030|`ast_device_shipment_event`|`KEY idx_shipment_order_line (tenant_id, order_line_id, shipment_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-031|`ast_device_shipment_event`|`KEY idx_shipment_package (tenant_id, shipment_package_id, device_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-032|`ast_device_shipment_event`|`KEY idx_shipment_rma ( tenant_id, rma_marked, business_action_code, rma_no )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-033|`ast_device_sn`|`KEY idx_device_internal_serial_no (tenant_id, internal_serial_no)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-034|`ast_device_sn`|`KEY idx_device_item (tenant_id, item_code, asset_status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-035|`ast_device_sn`|`KEY idx_device_secondary_sn (tenant_id, secondary_sn)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-036|`ast_device_version`|`KEY idx_device_version_current ( tenant_id, device_id, component_type, status, effective_from )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-037|`ast_device_version`|`KEY idx_project_device_version (tenant_id, project_id, version_stage)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-038|`ast_network_topology`|`KEY idx_network_topology_project (tenant_id, project_id, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-039|`ast_network_topology_device_relation`|`KEY idx_topology_device_reverse (tenant_id, device_id, topology_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-040|`ast_product`|`KEY idx_product_line (tenant_id, product_line_code, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-041|`com_contract`|`KEY idx_contract_company (tenant_id, company_id, status, contract_no)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-042|`com_contract`|`KEY idx_contract_customer (tenant_id, customer_id, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-043|`com_contract`|`KEY idx_contract_no (tenant_id, contract_no, company_code)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-044|`com_contract_receivable`|`KEY idx_contract_receivable_business ( tenant_id, contract_no, company_code, mapping_status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-045|`com_contract_receivable`|`KEY idx_contract_receivable_company ( tenant_id, company_id, mapping_status, contract_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-046|`com_contract_receivable`|`KEY idx_contract_receivable_contract ( tenant_id, contract_id, source_sync_time )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-047|`com_crm_execution_config`|`KEY idx_crm_execution_config_company ( tenant_id, company_code, status, execution_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-048|`com_crm_execution_config`|`KEY idx_crm_execution_config_execution ( tenant_id, execution_id, item_code )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-049|`com_crm_execution_order`|`KEY idx_crm_execution_company_office ( tenant_id, company_id, office_department_id, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-050|`com_crm_execution_order`|`KEY idx_crm_execution_company_office_code ( tenant_id, company_code, office_department_code, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-051|`com_crm_execution_order`|`KEY idx_crm_execution_crm_project ( tenant_id, crm_project_code, execution_no )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-052|`com_crm_execution_order`|`KEY idx_crm_execution_project ( tenant_id, primary_project_id, status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-053|`com_delivery_scope`|`KEY idx_scope_item (tenant_id, item_code, scope_status, project_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-054|`com_delivery_scope`|`KEY idx_scope_order_business ( tenant_id, order_source_system, order_company_code, order_type, order_no, line_no )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-055|`com_delivery_scope`|`KEY idx_scope_order_line ( tenant_id, order_line_id, scope_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-056|`com_delivery_scope`|`KEY idx_scope_project ( tenant_id, project_id, scope_status, order_line_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-057|`com_delivery_scope`|`KEY idx_scope_project_company ( tenant_id, project_company_code, scope_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-058|`com_delivery_scope`|`KEY idx_scope_project_customer ( tenant_id, project_customer_code, scope_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-059|`com_delivery_scope`|`KEY idx_scope_project_department ( tenant_id, project_department_code, scope_status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-060|`com_delivery_scope_detail`|`KEY idx_delivery_scope_detail_location ( tenant_id, implementation_location, delivery_scope_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-061|`com_delivery_scope_detail`|`KEY idx_delivery_scope_detail_product ( tenant_id, product_code, device_type_code, delivery_scope_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-062|`com_execution_order_merge_batch`|`KEY idx_execution_merge_primary ( tenant_id, primary_execution_id, status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-063|`com_execution_order_merge_member`|`KEY idx_execution_merge_member_execution ( tenant_id, execution_id, merge_batch_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-064|`com_order_change_relation`|`KEY idx_order_change_target ( tenant_id, target_order_id, relation_type )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-065|`com_order_contract_relation`|`KEY idx_order_contract_reverse (tenant_id, contract_id, order_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-066|`com_order_execution_relation`|`KEY idx_order_execution_execution ( tenant_id, execution_id, order_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-067|`com_order_line_execution_relation`|`KEY idx_order_line_execution_reverse (tenant_id, execution_id, order_line_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-068|`com_project_contract_relation`|`KEY idx_project_contract_reverse (tenant_id, contract_id, project_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-069|`com_sales_order`|`KEY idx_sales_order_company (tenant_id, company_id, status, order_no)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-070|`com_sales_order`|`KEY idx_sales_order_customer (tenant_id, customer_code, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-071|`com_sales_order`|`KEY idx_sales_order_no (tenant_id, order_no)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-072|`com_sales_order`|`KEY idx_sales_order_time (tenant_id, order_create_time, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-073|`com_sales_order_line`|`KEY idx_sales_order_line_business ( tenant_id, source_system, company_code, order_type, order_no, line_no )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-074|`com_sales_order_line`|`KEY idx_sales_order_line_customer (tenant_id, customer_code, status, id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-075|`com_sales_order_line`|`KEY idx_sales_order_line_item (tenant_id, item_code)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-076|`com_sales_order_line`|`KEY idx_sales_order_line_profit (tenant_id, profit_center, order_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-077|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_company ( tenant_id, company_id, mapping_status, contract_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-078|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_contract ( tenant_id, contract_id, mapping_status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-079|`com_shipment_contract_reference`|`KEY idx_shipment_contract_ref_no ( tenant_id, contract_no, company_code, mapping_status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-080|`com_shipment_package`|`KEY idx_shipment_package_contract_ref ( tenant_id, shipment_contract_ref_id, shipment_time )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-081|`cus_customer`|`KEY idx_customer_market_relation ( tenant_id, market_code, system_code, expend_code, industry_code )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-082|`cus_customer`|`KEY idx_customer_name (tenant_id, customer_name)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-083|`cus_customer_contact`|`KEY idx_customer_contact (tenant_id, customer_id, status, is_primary)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-084|`cus_market_relation`|`KEY idx_market_relation_name ( tenant_id, market_name(64), system_name(64), expend_name(64), industry_name(64) )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-085|`cut_cutover_checklist`|`KEY idx_cutover_checklist_assessment ( tenant_id, assessment_id, assessment_version )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-086|`cut_cutover_checklist_item`|`KEY idx_cutover_checklist_item_device (tenant_id, device_id, checklist_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-087|`cut_cutover_checklist_item`|`KEY idx_cutover_checklist_item_type ( tenant_id, checklist_id, item_type_code, applicable_flag, sort_order )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-088|`cut_cutover_checklist_item_result`|`KEY idx_cutover_checklist_item_result_collection_task ( tenant_id, collection_task_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-089|`cut_cutover_checklist_item_result`|`KEY idx_cutover_checklist_item_result_selected ( tenant_id, checklist_item_id, selection_started_at )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-090|`cut_cutover_closure`|`KEY idx_cutover_closure_result (tenant_id, result_code, archive_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-091|`cut_cutover_support_arrangement`|`KEY idx_cutover_support_arrangement_task (tenant_id, cutover_task_id, plan_revision_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-092|`imp_configuration_collection_parse_attempt`|`KEY idx_configuration_parse_attempt_result (tenant_id, collection_result_id, started_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-093|`imp_configuration_collection_result`|`KEY idx_configuration_collection_result_device (tenant_id, project_id, device_id, operated_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-094|`imp_configuration_collection_result`|`KEY idx_configuration_collection_result_hash (tenant_id, raw_log_sha256)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-095|`imp_configuration_component_candidate`|`KEY idx_configuration_component_candidate_match (tenant_id, match_status_code, create_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-096|`imp_configuration_component_candidate`|`KEY idx_configuration_component_candidate_sn (tenant_id, chassis_sn, slot_code, card_sn)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-097|`plt_document_version`|`KEY idx_document_file (tenant_id, file_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-098|`plt_external_key_mapping`|`KEY idx_external_key_batch (tenant_id, batch_id, mapping_status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-099|`plt_external_key_mapping`|`KEY idx_external_key_source ( tenant_id, source_system, source_table, source_pk )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-100|`plt_external_key_mapping`|`KEY idx_external_key_target ( tenant_id, target_table, target_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-101|`plt_migration_issue`|`KEY idx_migration_issue_status ( tenant_id, issue_type, resolution_status, create_time )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-102|`plt_migration_source_record`|`KEY idx_migration_source_business ( tenant_id, source_system, source_table, source_business_key(191) )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-103|`plt_migration_source_record`|`KEY idx_migration_source_mapping ( tenant_id, batch_id, source_table, mapping_status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-104|`plt_sync_batch`|`KEY idx_sync_batch_object ( tenant_id, source_system, object_type, started_time )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-105|`proj_project`|`KEY idx_project_company_department ( tenant_id, company_code, department_code, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-106|`proj_project`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-107|`proj_project`|`KEY idx_project_customer_code (tenant_id, customer_code, status, id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-108|`proj_project`|`KEY idx_project_department_company ( tenant_id, department_code, company_code, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-109|`proj_project`|`KEY idx_project_manager (tenant_id, manager_id, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-110|`proj_project`|`KEY idx_project_manager_employee (tenant_id, manager_employee_no, status, id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-111|`proj_project`|`KEY idx_project_market_relation ( tenant_id, market_code, system_code, expend_code, industry_code, status, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-112|`proj_project`|`KEY idx_project_parent (tenant_id, parent_id, tree_sort, id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-113|`proj_project`|`KEY idx_project_path (tenant_id, root_id, tree_path(191))`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-114|`proj_project_company_department_relation`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-115|`proj_project_company_department_relation`|`KEY idx_project_company_reverse ( tenant_id, company_code, relation_role, status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-116|`proj_project_company_department_relation`|`KEY idx_project_department_reverse ( tenant_id, department_code, company_code, relation_role, status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-117|`proj_project_member_assignment`|`KEY idx_project_member_company_department ( tenant_id, company_code, department_code, status, project_id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-118|`proj_project_member_assignment`|`KEY idx_project_member_employee (tenant_id, employee_no, status, project_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-119|`proj_project_member_assignment`|`KEY idx_project_member_user (tenant_id, user_id, status, project_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-120|`proj_project_party`|`KEY idx_project_party_code ( tenant_id, party_role, party_code, status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-121|`proj_project_party`|`KEY idx_project_party_project ( tenant_id, project_id, party_role, status )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-122|`proj_project_portfolio`|`KEY idx_portfolio_owner (tenant_id, owner_id, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-123|`proj_project_portfolio_member`|`KEY idx_portfolio_project_reverse (tenant_id, project_id, portfolio_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-124|`proj_project_relation`|`KEY idx_project_relation_target ( tenant_id, target_project_id, relation_type )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-125|`proj_project_task_completion_evaluation`|`KEY idx_project_task_completion_evaluation_time ( tenant_id, project_task_id, evaluated_at, id )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-126|`proj_project_task_execution_contract`|`KEY idx_project_task_execution_contract_target ( tenant_id, target_context_code, target_object_type, target_object_key )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-127|`proj_project_template_task_definition`|`KEY idx_project_template_task_definition_tree ( tenant_id, template_revision_id, stage_definition_key, parent_task_definition_key, sort_order )`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-128|`srv_service_incident`|`KEY idx_incident_owner (tenant_id, owner_id, status)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-129|`srv_service_incident`|`KEY idx_incident_project (tenant_id, project_id, status, occurred_time)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|
|IX-130|`srv_service_incident_device_relation`|`KEY idx_incident_device_reverse (tenant_id, device_id, incident_id)`|逐项决策已完成，待独立整体一致性复审；Q08候选，后续仍需性能验证|

## 7. 唯一键完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|UK-001|`acc_deliverable_template`|`UNIQUE KEY uk_deliverable_template (tenant_id, template_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-002|`acc_deliverable_template`|`UNIQUE KEY uk_deliverable_template_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-003|`acc_project_deliverable`|`UNIQUE KEY uk_project_deliverable_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-004|`acc_satisfaction_collection_task`|`UNIQUE KEY uk_satisfaction_collection_task_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-005|`acc_satisfaction_collection_task`|`UNIQUE KEY uk_satisfaction_task_revision (tenant_id, project_id, source_context, source_object_type, source_object_id, source_object_version, business_purpose_code, applicable_timing_code, payment_stage_key, task_revision_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-006|`acc_satisfaction_questionnaire`|`UNIQUE KEY uk_satisfaction_questionnaire_revision (tenant_id, task_id, questionnaire_revision_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-007|`acc_satisfaction_questionnaire`|`UNIQUE KEY uk_satisfaction_questionnaire_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-008|`acc_satisfaction_response`|`UNIQUE KEY uk_satisfaction_response_request (tenant_id, questionnaire_id, request_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-009|`acc_satisfaction_response`|`UNIQUE KEY uk_satisfaction_response_sequence (tenant_id, questionnaire_id, response_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-010|`acc_satisfaction_response`|`UNIQUE KEY uk_satisfaction_response_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-011|`acc_satisfaction_result`|`UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-012|`acc_satisfaction_result`|`UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-013|`acc_satisfaction_result`|`UNIQUE KEY uk_satisfaction_result_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-014|`ast_device_component_relation`|`UNIQUE KEY uk_device_component_current_slot (tenant_id, chassis_device_id, current_slot_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-015|`ast_device_component_relation`|`UNIQUE KEY uk_device_component_relation_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-016|`ast_device_configuration`|`UNIQUE KEY uk_device_configuration_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-017|`ast_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature ( tenant_id, configuration_id, feature_code )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-018|`ast_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-019|`ast_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service ( tenant_id, configuration_id, service_code )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-020|`ast_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-021|`ast_device_project_assignment`|`UNIQUE KEY uk_device_assignment_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-022|`ast_device_project_assignment`|`UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-023|`ast_device_project_assignment`|`UNIQUE KEY uk_project_device_assignment_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-024|`ast_device_relation`|`UNIQUE KEY uk_device_relation_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-025|`ast_device_relation`|`UNIQUE KEY uk_device_relation_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-026|`ast_device_shipment_event`|`UNIQUE KEY uk_device_shipment_event_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-027|`ast_device_shipment_event`|`UNIQUE KEY uk_shipment_event_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-028|`ast_device_sn`|`UNIQUE KEY uk_device_sn (tenant_id, sn)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-029|`ast_device_sn`|`UNIQUE KEY uk_device_sn_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-030|`ast_device_version`|`UNIQUE KEY uk_device_version_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-031|`ast_network_topology`|`UNIQUE KEY uk_network_topology_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-032|`ast_network_topology_device_relation`|`UNIQUE KEY uk_topology_device (tenant_id, topology_id, device_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-033|`ast_network_topology_device_relation`|`UNIQUE KEY uk_topology_device_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-034|`ast_product`|`UNIQUE KEY uk_product_code (tenant_id, product_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-035|`ast_product`|`UNIQUE KEY uk_product_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-036|`ast_product_release`|`UNIQUE KEY uk_product_release ( tenant_id, product_id, release_version, release_type )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-037|`ast_product_release`|`UNIQUE KEY uk_product_release_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-038|`com_contract`|`UNIQUE KEY uk_contract_business ( tenant_id, company_code, contract_no )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-039|`com_contract`|`UNIQUE KEY uk_contract_master_source ( tenant_id, master_source_system, master_source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-040|`com_contract`|`UNIQUE KEY uk_contract_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-041|`com_contract_receivable`|`UNIQUE KEY uk_contract_receivable_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-042|`com_contract_receivable`|`UNIQUE KEY uk_contract_receivable_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-043|`com_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config ( tenant_id, config_source, source_config_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-044|`com_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-045|`com_crm_execution_order`|`UNIQUE KEY uk_crm_execution ( tenant_id, source_system, execution_no )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-046|`com_crm_execution_order`|`UNIQUE KEY uk_crm_execution_order_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-047|`com_delivery_scope`|`UNIQUE KEY uk_project_order_line_scope_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-048|`com_delivery_scope`|`UNIQUE KEY uk_scope_current ( tenant_id, project_id, current_order_line_id )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-049|`com_delivery_scope_detail`|`UNIQUE KEY uk_delivery_scope_detail_sequence ( tenant_id, delivery_scope_id, detail_sequence )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-050|`com_delivery_scope_detail`|`UNIQUE KEY uk_delivery_scope_detail_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-051|`com_execution_order_merge_batch`|`UNIQUE KEY uk_execution_merge_batch ( tenant_id, source_system, source_merge_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-052|`com_execution_order_merge_batch`|`UNIQUE KEY uk_execution_merge_batch_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-053|`com_execution_order_merge_member`|`UNIQUE KEY uk_execution_merge_member_source ( tenant_id, merge_batch_id, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-054|`com_execution_order_merge_member`|`UNIQUE KEY uk_execution_merge_member_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-055|`com_order_change_relation`|`UNIQUE KEY uk_order_change ( tenant_id, source_order_id, target_order_id, relation_type )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-056|`com_order_change_relation`|`UNIQUE KEY uk_order_change_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-057|`com_order_contract_relation`|`UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-058|`com_order_contract_relation`|`UNIQUE KEY uk_order_contract_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-059|`com_order_execution_relation`|`UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-060|`com_order_execution_relation`|`UNIQUE KEY uk_order_execution_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-061|`com_order_line_execution_relation`|`UNIQUE KEY uk_order_line_execution (tenant_id, order_line_id, execution_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-062|`com_order_line_execution_relation`|`UNIQUE KEY uk_order_line_execution_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-063|`com_project_contract_relation`|`UNIQUE KEY uk_project_contract ( tenant_id, project_id, contract_id, relation_role )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-064|`com_project_contract_relation`|`UNIQUE KEY uk_project_contract_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-065|`com_sales_order`|`UNIQUE KEY uk_sales_order_business ( tenant_id, source_system, company_code, order_type, order_no )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-066|`com_sales_order`|`UNIQUE KEY uk_sales_order_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-067|`com_sales_order_line`|`UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-068|`com_sales_order_line`|`UNIQUE KEY uk_sales_order_line_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-069|`com_shipment_contract_reference`|`UNIQUE KEY uk_shipment_contract_ref_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-070|`com_shipment_contract_reference`|`UNIQUE KEY uk_shipment_contract_ref_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-071|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_no ( tenant_id, source_system, package_no )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-072|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-073|`com_shipment_package`|`UNIQUE KEY uk_shipment_package_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-074|`cus_customer`|`UNIQUE KEY uk_customer_code (tenant_id, customer_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-075|`cus_customer`|`UNIQUE KEY uk_customer_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-076|`cus_customer_contact`|`UNIQUE KEY uk_customer_contact_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-077|`cus_customer_contact`|`UNIQUE KEY uk_customer_primary_contact (tenant_id, primary_customer_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-078|`cus_market_relation`|`UNIQUE KEY uk_market_relation_business ( tenant_id, market_code, system_code, expend_code, industry_code )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-079|`cus_market_relation`|`UNIQUE KEY uk_market_relation_source ( tenant_id, source_system, source_record_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-080|`cus_market_relation`|`UNIQUE KEY uk_market_relation_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-081|`cut_cutover_checklist`|`UNIQUE KEY uk_cutover_checklist_current ( tenant_id, cutover_task_id, current_marker )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-082|`cut_cutover_checklist`|`UNIQUE KEY uk_cutover_checklist_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-083|`cut_cutover_checklist`|`UNIQUE KEY uk_cutover_checklist_version ( tenant_id, cutover_task_id, checklist_version )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-084|`cut_cutover_checklist_item`|`UNIQUE KEY uk_cutover_checklist_item_key ( tenant_id, checklist_id, stable_item_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-085|`cut_cutover_checklist_item`|`UNIQUE KEY uk_cutover_checklist_item_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-086|`cut_cutover_checklist_item_result`|`UNIQUE KEY uk_cutover_checklist_item_result_current ( tenant_id, checklist_item_id, current_marker )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-087|`cut_cutover_checklist_item_result`|`UNIQUE KEY uk_cutover_checklist_item_result_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-088|`cut_cutover_checklist_item_result`|`UNIQUE KEY uk_cutover_checklist_item_result_version ( tenant_id, checklist_item_id, result_version )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-089|`cut_cutover_closure`|`UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-090|`cut_cutover_closure`|`UNIQUE KEY uk_cutover_closure_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-091|`cut_cutover_support_arrangement`|`UNIQUE KEY uk_cutover_support_arrangement_no (tenant_id, plan_revision_id, arrangement_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-092|`cut_cutover_support_arrangement`|`UNIQUE KEY uk_cutover_support_arrangement_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-093|`imp_configuration_collection_parse_attempt`|`UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-094|`imp_configuration_collection_parse_attempt`|`UNIQUE KEY uk_configuration_parse_attempt_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-095|`imp_configuration_collection_result`|`UNIQUE KEY uk_configuration_collection_result (tenant_id, collection_task_id, result_type_code, result_version_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-096|`imp_configuration_collection_result`|`UNIQUE KEY uk_configuration_collection_result_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-097|`imp_configuration_component_candidate`|`UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-098|`imp_configuration_component_candidate`|`UNIQUE KEY uk_configuration_component_candidate_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-099|`plt_business_document`|`UNIQUE KEY uk_business_document_code (tenant_id, document_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-100|`plt_business_document`|`UNIQUE KEY uk_business_document_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-101|`plt_document_version`|`UNIQUE KEY uk_document_version (tenant_id, document_id, version_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-102|`plt_document_version`|`UNIQUE KEY uk_document_version_owner (tenant_id, document_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-103|`plt_document_version`|`UNIQUE KEY uk_document_version_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-104|`plt_external_key_mapping`|`UNIQUE KEY uk_external_key_map_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-105|`plt_external_key_mapping`|`UNIQUE KEY uk_external_key_source_target ( tenant_id, source_system, source_table, source_pk, target_role, target_sequence, target_table, target_id )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-106|`plt_migration_issue`|`UNIQUE KEY uk_migration_issue_source ( tenant_id, batch_id, source_table, source_pk, issue_type )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-107|`plt_migration_issue`|`UNIQUE KEY uk_migration_issue_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-108|`plt_migration_source_record`|`UNIQUE KEY uk_migration_source_record ( tenant_id, batch_id, source_system, source_table, source_pk )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-109|`plt_migration_source_record`|`UNIQUE KEY uk_migration_source_record_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-110|`plt_sync_batch`|`UNIQUE KEY uk_sync_batch_no (tenant_id, batch_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-111|`plt_sync_batch`|`UNIQUE KEY uk_sync_batch_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-112|`proj_project`|`UNIQUE KEY uk_project_code (tenant_id, project_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-113|`proj_project`|`UNIQUE KEY uk_project_code_sequence (tenant_id, code_root_id, project_sequence)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-114|`proj_project`|`UNIQUE KEY uk_project_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-115|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_company_department_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-116|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_company_department_role ( tenant_id, project_id, company_code, department_code, relation_role, effective_from )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-117|`proj_project_company_department_relation`|`UNIQUE KEY uk_project_primary_company_department ( tenant_id, primary_project_id, relation_role )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-118|`proj_project_member_assignment`|`UNIQUE KEY uk_project_member_role ( tenant_id, project_id, user_id, member_role, effective_from )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-119|`proj_project_member_assignment`|`UNIQUE KEY uk_project_member_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-120|`proj_project_party`|`UNIQUE KEY uk_project_party_source ( tenant_id, source_system, source_table, source_record_key, party_role )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-121|`proj_project_party`|`UNIQUE KEY uk_project_party_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-122|`proj_project_portfolio`|`UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-123|`proj_project_portfolio`|`UNIQUE KEY uk_portfolio_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-124|`proj_project_portfolio_member`|`UNIQUE KEY uk_portfolio_project ( tenant_id, portfolio_id, project_id, member_source )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-125|`proj_project_portfolio_member`|`UNIQUE KEY uk_portfolio_project_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-126|`proj_project_relation`|`UNIQUE KEY uk_project_relation ( tenant_id, source_project_id, target_project_id, relation_type )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-127|`proj_project_relation`|`UNIQUE KEY uk_project_relation_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-128|`proj_project_task_completion_evaluation`|`UNIQUE KEY uk_project_task_completion_evaluation_idempotency ( tenant_id, project_task_id, idempotency_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-129|`proj_project_task_completion_evaluation`|`UNIQUE KEY uk_project_task_completion_evaluation_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-130|`proj_project_task_execution_contract`|`UNIQUE KEY uk_project_task_execution_contract_current ( tenant_id, project_task_id, current_marker )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-131|`proj_project_task_execution_contract`|`UNIQUE KEY uk_project_task_execution_contract_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-132|`proj_project_task_execution_contract`|`UNIQUE KEY uk_project_task_execution_contract_version ( tenant_id, project_task_id, contract_version )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-133|`proj_project_template_task_definition`|`UNIQUE KEY uk_project_template_task_definition ( tenant_id, template_revision_id, task_definition_key )`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-134|`proj_project_template_task_definition`|`UNIQUE KEY uk_project_template_task_definition_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-135|`srv_service_incident`|`UNIQUE KEY uk_service_incident_no (tenant_id, incident_no)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-136|`srv_service_incident`|`UNIQUE KEY uk_service_incident_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-137|`srv_service_incident_device_relation`|`UNIQUE KEY uk_incident_device (tenant_id, incident_id, device_id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|
|UK-138|`srv_service_incident_device_relation`|`UNIQUE KEY uk_incident_device_rel_tenant_row (tenant_id, id)`|影响重复数据；逐项决策已完成，待独立整体一致性复审|

## 8. CHECK规则完整清单

|编号|表|当前定义|业务影响/建议|
|---|---|---|---|
|CK-001|`acc_deliverable_template`|`CONSTRAINT chk_deliverable_template_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-002|`acc_deliverable_template`|`CONSTRAINT chk_deliverable_template_required CHECK (required_flag IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-003|`acc_project_deliverable`|`CONSTRAINT chk_project_deliverable_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-004|`acc_satisfaction_collection_task`|`CONSTRAINT chk_satisfaction_task_revision CHECK (task_revision_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-005|`acc_satisfaction_questionnaire`|`CONSTRAINT chk_satisfaction_questionnaire_revision CHECK (questionnaire_revision_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-006|`acc_satisfaction_response`|`CONSTRAINT chk_satisfaction_response_sequence CHECK (response_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-007|`acc_satisfaction_result`|`CONSTRAINT chk_satisfaction_result_passed CHECK (passed IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-008|`acc_satisfaction_result`|`CONSTRAINT chk_satisfaction_result_sequence CHECK (result_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-009|`ast_device_component_relation`|`CONSTRAINT chk_device_component_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-010|`ast_device_configuration`|`CONSTRAINT chk_device_configuration_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-011|`ast_device_configuration`|`CONSTRAINT chk_device_configuration_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-012|`ast_device_configuration_feature`|`CONSTRAINT chk_configuration_feature_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-013|`ast_device_configuration_service`|`CONSTRAINT chk_configuration_service_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-014|`ast_device_project_assignment`|`CONSTRAINT chk_device_assignment_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-015|`ast_device_project_assignment`|`CONSTRAINT chk_device_assignment_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-016|`ast_device_relation`|`CONSTRAINT chk_device_relation_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-017|`ast_device_relation`|`CONSTRAINT chk_device_relation_self CHECK (source_device_id <> target_device_id)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-018|`ast_device_shipment_event`|`CONSTRAINT chk_shipment_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-019|`ast_device_sn`|`CONSTRAINT chk_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-020|`ast_device_sn`|`CONSTRAINT chk_device_secondary_cache CHECK ( secondary_sn IS NOT NULL OR secondary_item IS NULL )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-021|`ast_device_sn`|`CONSTRAINT chk_device_secondary_self CHECK ( secondary_sn IS NULL OR secondary_sn <> sn )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-022|`ast_device_version`|`CONSTRAINT chk_device_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-023|`ast_device_version`|`CONSTRAINT chk_device_version_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-024|`ast_network_topology`|`CONSTRAINT chk_network_topology_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-025|`ast_network_topology`|`CONSTRAINT chk_network_topology_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-026|`ast_network_topology_device_relation`|`CONSTRAINT chk_topology_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-027|`ast_product`|`CONSTRAINT chk_product_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-028|`ast_product`|`CONSTRAINT chk_product_service CHECK (service_product_flag IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-029|`ast_product_release`|`CONSTRAINT chk_product_release_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-030|`com_contract`|`CONSTRAINT chk_contract_dates CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-031|`com_contract`|`CONSTRAINT chk_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-032|`com_contract_receivable`|`CONSTRAINT chk_contract_receivable_dates CHECK ( source_effective_to IS NULL OR source_effective_from IS NULL OR source_effective_to >= source_effective_from )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-033|`com_contract_receivable`|`CONSTRAINT chk_contract_receivable_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-034|`com_crm_execution_config`|`CONSTRAINT chk_crm_execution_config_af CHECK (is_af_evidence IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-035|`com_crm_execution_config`|`CONSTRAINT chk_crm_execution_config_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-036|`com_crm_execution_order`|`CONSTRAINT chk_crm_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-037|`com_delivery_scope`|`CONSTRAINT chk_scope_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-038|`com_delivery_scope`|`CONSTRAINT chk_scope_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-039|`com_delivery_scope_detail`|`CONSTRAINT chk_delivery_scope_detail_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-040|`com_delivery_scope_detail`|`CONSTRAINT chk_delivery_scope_detail_subject CHECK (product_code IS NOT NULL OR device_type_code IS NOT NULL)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-041|`com_execution_order_merge_batch`|`CONSTRAINT chk_execution_merge_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-042|`com_execution_order_merge_member`|`CONSTRAINT chk_execution_merge_member_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-043|`com_execution_order_merge_member`|`CONSTRAINT chk_execution_merge_member_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-044|`com_order_change_relation`|`CONSTRAINT chk_order_change_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-045|`com_order_change_relation`|`CONSTRAINT chk_order_change_self CHECK (source_order_id <> target_order_id)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-046|`com_order_contract_relation`|`CONSTRAINT chk_order_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-047|`com_order_execution_relation`|`CONSTRAINT chk_order_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-048|`com_order_execution_relation`|`CONSTRAINT chk_order_execution_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-049|`com_order_line_execution_relation`|`CONSTRAINT chk_order_line_execution_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-050|`com_project_contract_relation`|`CONSTRAINT chk_project_contract_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-051|`com_project_contract_relation`|`CONSTRAINT chk_project_contract_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-052|`com_sales_order`|`CONSTRAINT chk_sales_order_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-053|`com_sales_order_line`|`CONSTRAINT chk_sales_order_line_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-054|`com_shipment_contract_reference`|`CONSTRAINT chk_shipment_contract_ref_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-055|`com_shipment_package`|`CONSTRAINT chk_shipment_package_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-056|`com_shipment_package`|`CONSTRAINT chk_shipment_package_warranty_dates CHECK ( warranty_end_time IS NULL OR warranty_start_time IS NULL OR warranty_end_time >= warranty_start_time )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-057|`cus_customer`|`CONSTRAINT chk_customer_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-058|`cus_customer_contact`|`CONSTRAINT chk_customer_contact_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-059|`cus_customer_contact`|`CONSTRAINT chk_customer_contact_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-060|`cus_market_relation`|`CONSTRAINT chk_market_relation_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-061|`cut_cutover_checklist`|`CONSTRAINT chk_cutover_checklist_version CHECK ( assessment_version > 0 AND checklist_version > 0 )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-062|`cut_cutover_checklist_item`|`CONSTRAINT chk_cutover_checklist_item_applicable CHECK (applicable_flag IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-063|`cut_cutover_checklist_item`|`CONSTRAINT chk_cutover_checklist_item_required CHECK (required_flag IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-064|`cut_cutover_checklist_item_result`|`CONSTRAINT chk_cutover_checklist_item_result_selection CHECK ( selection_ended_at IS NULL OR selection_ended_at >= selection_started_at )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-065|`cut_cutover_checklist_item_result`|`CONSTRAINT chk_cutover_checklist_item_result_version CHECK (result_version > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-066|`cut_cutover_closure`|`CONSTRAINT chk_cutover_closure_execution CHECK (execution_normal IS NULL OR execution_normal IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-067|`cut_cutover_closure`|`CONSTRAINT chk_cutover_closure_precheck CHECK (precheck_normal IS NULL OR precheck_normal IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-068|`cut_cutover_closure`|`CONSTRAINT chk_cutover_closure_rollback CHECK (rollback_occurred IS NULL OR rollback_occurred IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-069|`cut_cutover_closure`|`CONSTRAINT chk_cutover_closure_submit CHECK ( submitted_time IS NULL OR (submitted_by IS NOT NULL AND archive_time IS NOT NULL AND result_code IS NOT NULL) )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-070|`cut_cutover_closure`|`CONSTRAINT chk_cutover_closure_test CHECK (test_normal IS NULL OR test_normal IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-071|`cut_cutover_support_arrangement`|`CONSTRAINT chk_cutover_support_arrangement_no CHECK (arrangement_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-072|`imp_configuration_collection_parse_attempt`|`CONSTRAINT chk_configuration_parse_attempt_no CHECK (attempt_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-073|`imp_configuration_collection_parse_attempt`|`CONSTRAINT chk_configuration_parse_attempt_time CHECK (completed_time IS NULL OR completed_time >= started_time)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-074|`imp_configuration_collection_result`|`CONSTRAINT chk_configuration_collection_result_version CHECK (result_version_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-075|`imp_configuration_component_candidate`|`CONSTRAINT chk_configuration_component_candidate_no CHECK (candidate_no > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-076|`plt_business_document`|`CONSTRAINT chk_business_document_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-077|`plt_document_version`|`CONSTRAINT chk_document_version_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-078|`plt_external_key_mapping`|`CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-079|`plt_migration_source_record`|`CONSTRAINT chk_migration_source_target_count CHECK (mapped_target_count >= 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-080|`plt_sync_batch`|`CONSTRAINT chk_sync_batch_count CHECK (success_count + failure_count <= read_count)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-081|`plt_sync_batch`|`CONSTRAINT chk_sync_batch_time CHECK (finished_time IS NULL OR finished_time >= started_time)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-082|`proj_project`|`CONSTRAINT chk_project_code_namespace CHECK ( (project_sequence = 0 AND code_root_id = id) OR project_sequence > 0 )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-083|`proj_project`|`CONSTRAINT chk_project_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-084|`proj_project`|`CONSTRAINT chk_project_depth CHECK (tree_depth >= 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-085|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-086|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-087|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_pair CHECK (department_id IS NULL OR department_code IS NOT NULL)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-088|`proj_project_company_department_relation`|`CONSTRAINT chk_project_company_department_primary CHECK (is_primary IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-089|`proj_project_member_assignment`|`CONSTRAINT chk_project_member_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-090|`proj_project_member_assignment`|`CONSTRAINT chk_project_member_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-091|`proj_project_party`|`CONSTRAINT chk_project_party_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-092|`proj_project_party`|`CONSTRAINT chk_project_party_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-093|`proj_project_portfolio`|`CONSTRAINT chk_portfolio_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-094|`proj_project_portfolio_member`|`CONSTRAINT chk_portfolio_project_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-095|`proj_project_relation`|`CONSTRAINT chk_project_relation_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-096|`proj_project_relation`|`CONSTRAINT chk_project_relation_self CHECK (source_project_id <> target_project_id)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-097|`proj_project_task_completion_evaluation`|`CONSTRAINT chk_project_task_completion_evaluation_version CHECK ( task_version > 0 AND contract_version > 0 )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-098|`proj_project_task_execution_contract`|`CONSTRAINT chk_project_task_execution_contract_dates CHECK ( effective_to IS NULL OR effective_to >= effective_from )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-099|`proj_project_task_execution_contract`|`CONSTRAINT chk_project_task_execution_contract_version CHECK ( source_definition_version > 0 AND contract_version > 0 )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-100|`proj_project_template_task_definition`|`CONSTRAINT chk_project_template_task_definition_version CHECK (definition_version > 0)`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-101|`srv_service_incident`|`CONSTRAINT chk_service_incident_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-102|`srv_service_incident`|`CONSTRAINT chk_service_incident_times CHECK ( restored_time IS NULL OR occurred_time IS NULL OR restored_time >= occurred_time )`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|
|CK-103|`srv_service_incident_device_relation`|`CONSTRAINT chk_incident_device_deleted CHECK (deleted IN (0, 1))`|影响异常历史数据；逐项决策已完成，待独立整体一致性复审|

## 9. 裁决边界

- `ACCEPT_CURRENT`表示接受当前DDL作为目标数据模型，不代表历史数据天然满足约束。
- 历史数据违反已批准约束时进入迁移问题池并保留来源证据，不得静默删除、改写或临时放宽模型掩盖问题。
- 当前哈希决策由ADR-0028历史清单与ADR-0030六表差量共同组成，2,079项均已有逐项决策；独立复审只复核候选制品、哈希和整体一致性，不重新逐项签署。P3-E09不定义迁移批准哈希；历史迁移门禁未来另行定义。
- 本清单不授权连接或修改旧库，不授权执行生产迁移。
