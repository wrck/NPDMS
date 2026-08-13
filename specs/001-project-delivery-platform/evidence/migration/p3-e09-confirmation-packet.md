# P3-E09 当前哈希完整确认清单

> 状态：`USER_CONFIRMATION_REQUIRED`
> 当前 DDL SHA-256：`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`
> 待确认项：692；本清单覆盖：692。

## 决策摘要

|编号|决策组|项数|推荐|确认效果|
|---|---|---:|---|---|
|Q07|当前哈希技术约束|257|A|接受257项主键、租户引用键、同域外键和稳定技术CHECK；历史违规数据进入迁移问题池。|
|Q08|当前哈希候选索引|122|A|接受122项为候选索引基线；不代表性能验收，后续仅以前向迁移调整。|
|V1.7|V1.7十表物理候选|257|A|接受十张候选表的全部表、字段、约束、索引和表选项；不扩大到已排除或后置对象。|
|Q09|非V1.7表选项|50|A|统一采用InnoDB、utf8mb4、utf8mb4_0900_ai_ci；COMMENT仅描述对象语义，不作为业务规则。|
|Q10|来源幂等唯一键|14|A|按租户、来源系统和来源业务键防止重复同步；来源键按不透明值精确比较。|
|Q11|关系粒度唯一键|13|A|仅阻止同一关系粒度重复，不额外限制项目、订单、设备或参与方数量。|
|Q12|业务身份与版本序号唯一键|16|A|业务编码、单号、SN、文档版本和产品版本在声明粒度内唯一且不复用。|
|Q13|跨字段一致性CHECK|2|A|保留设备缓存一致性及公司/部门成对填写检查，不固化可扩展状态值。|
|Q14|市场目录审计字段与RMA投影|13|A|保留基础平台审计/租户/来源字段；rma_marked仅作兼容查询投影，不推导业务动作或数量方向。|

推荐组合：`Q07 A、Q08 A、V1.7 A、Q09 A、Q10 A、Q11 A、Q12 A、Q13 A、Q14 A`。

该组合只形成当前哈希下的需求方决策，不代表Reviewer签署或生成`approvedDdlSha256`。

## Q07 当前哈希技术约束（257项）

推荐：**A**。接受257项主键、租户引用键、同域外键和稳定技术CHECK；历史违规数据进入迁移问题池。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:acc_deliverable_template:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_deliverable_template:chk_deliverable_template_deleted`|`CONSTRAINT chk_deliverable_template_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:acc_deliverable_template:chk_deliverable_template_required`|`CONSTRAINT chk_deliverable_template_required CHECK (required_flag IN (0, 1))`|
|`CONSTRAINT:acc_deliverable_template:uk_deliverable_template_tenant_row`|`UNIQUE KEY uk_deliverable_template_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_project_deliverable:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_project_deliverable:chk_project_deliverable_deleted`|`CONSTRAINT chk_project_deliverable_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:acc_project_deliverable:fk_project_deliverable_template`|`CONSTRAINT fk_project_deliverable_template FOREIGN KEY (tenant_id, template_id) REFERENCES acc_deliverable_template (tenant_id, id)`|
|`CONSTRAINT:acc_project_deliverable:uk_project_deliverable_tenant_row`|`UNIQUE KEY uk_project_deliverable_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_collection_task:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_collection_task:chk_satisfaction_task_revision`|`CONSTRAINT chk_satisfaction_task_revision CHECK (task_revision_no > 0)`|
|`CONSTRAINT:acc_satisfaction_collection_task:uk_satisfaction_collection_task_tenant_row`|`UNIQUE KEY uk_satisfaction_collection_task_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:chk_satisfaction_questionnaire_revision`|`CONSTRAINT chk_satisfaction_questionnaire_revision CHECK (questionnaire_revision_no > 0)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:uk_satisfaction_questionnaire_tenant_row`|`UNIQUE KEY uk_satisfaction_questionnaire_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_response:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_response:chk_satisfaction_response_sequence`|`CONSTRAINT chk_satisfaction_response_sequence CHECK (response_no > 0)`|
|`CONSTRAINT:acc_satisfaction_response:uk_satisfaction_response_tenant_row`|`UNIQUE KEY uk_satisfaction_response_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_result:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_result:chk_satisfaction_result_passed`|`CONSTRAINT chk_satisfaction_result_passed CHECK (passed IN (0, 1))`|
|`CONSTRAINT:acc_satisfaction_result:chk_satisfaction_result_sequence`|`CONSTRAINT chk_satisfaction_result_sequence CHECK (result_no > 0)`|
|`CONSTRAINT:acc_satisfaction_result:uk_satisfaction_result_tenant_row`|`UNIQUE KEY uk_satisfaction_result_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ana_project_delivery_summary:PRIMARY`|`PRIMARY KEY (tenant_id, project_id)`|
|`CONSTRAINT:ast_device_component_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_component_relation:chk_device_component_dates`|`CONSTRAINT chk_device_component_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_device_component_relation:uk_device_component_relation_tenant_row`|`UNIQUE KEY uk_device_component_relation_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_configuration:chk_device_configuration_dates`|`CONSTRAINT chk_device_configuration_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_device_configuration:chk_device_configuration_deleted`|`CONSTRAINT chk_device_configuration_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_configuration:fk_device_configuration_device`|`CONSTRAINT fk_device_configuration_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration:uk_device_configuration_tenant_row`|`UNIQUE KEY uk_device_configuration_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration_feature:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_configuration_feature:chk_configuration_feature_deleted`|`CONSTRAINT chk_configuration_feature_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_configuration_feature:fk_configuration_feature_configuration`|`CONSTRAINT fk_configuration_feature_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration_feature:uk_device_configuration_feature_tenant_row`|`UNIQUE KEY uk_device_configuration_feature_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration_service:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_configuration_service:chk_configuration_service_deleted`|`CONSTRAINT chk_configuration_service_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_configuration_service:fk_configuration_service_configuration`|`CONSTRAINT fk_configuration_service_configuration FOREIGN KEY (tenant_id, configuration_id) REFERENCES ast_device_configuration (tenant_id, id)`|
|`CONSTRAINT:ast_device_configuration_service:uk_device_configuration_service_tenant_row`|`UNIQUE KEY uk_device_configuration_service_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_project_assignment:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_project_assignment:chk_device_assignment_dates`|`CONSTRAINT chk_device_assignment_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_device_project_assignment:chk_device_assignment_deleted`|`CONSTRAINT chk_device_assignment_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_project_assignment:fk_device_assignment_device`|`CONSTRAINT fk_device_assignment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_project_assignment:uk_project_device_assignment_tenant_row`|`UNIQUE KEY uk_project_device_assignment_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_relation:chk_device_relation_deleted`|`CONSTRAINT chk_device_relation_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_relation:chk_device_relation_self`|`CONSTRAINT chk_device_relation_self CHECK (source_device_id <> target_device_id)`|
|`CONSTRAINT:ast_device_relation:fk_device_relation_source`|`CONSTRAINT fk_device_relation_source FOREIGN KEY (tenant_id, source_device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_relation:fk_device_relation_target`|`CONSTRAINT fk_device_relation_target FOREIGN KEY (tenant_id, target_device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_relation:uk_device_relation_tenant_row`|`UNIQUE KEY uk_device_relation_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_shipment_event:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_shipment_event:chk_shipment_deleted`|`CONSTRAINT chk_shipment_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_shipment_event:fk_shipment_device`|`CONSTRAINT fk_shipment_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_shipment_event:uk_device_shipment_event_tenant_row`|`UNIQUE KEY uk_device_shipment_event_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_sn:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_sn:chk_device_deleted`|`CONSTRAINT chk_device_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_sn:chk_device_secondary_self`|`CONSTRAINT chk_device_secondary_self CHECK ( secondary_sn IS NULL OR secondary_sn <> sn )`|
|`CONSTRAINT:ast_device_sn:uk_device_sn_tenant_row`|`UNIQUE KEY uk_device_sn_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_version:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_version:chk_device_version_dates`|`CONSTRAINT chk_device_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_device_version:chk_device_version_deleted`|`CONSTRAINT chk_device_version_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_device_version:fk_device_version_device`|`CONSTRAINT fk_device_version_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_device_version:uk_device_version_tenant_row`|`UNIQUE KEY uk_device_version_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_network_topology:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_network_topology:chk_network_topology_dates`|`CONSTRAINT chk_network_topology_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_network_topology:chk_network_topology_deleted`|`CONSTRAINT chk_network_topology_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_network_topology:uk_network_topology_tenant_row`|`UNIQUE KEY uk_network_topology_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_network_topology_device_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_network_topology_device_relation:chk_topology_device_deleted`|`CONSTRAINT chk_topology_device_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_network_topology_device_relation:fk_topology_device_device`|`CONSTRAINT fk_topology_device_device FOREIGN KEY (tenant_id, device_id) REFERENCES ast_device_sn (tenant_id, id)`|
|`CONSTRAINT:ast_network_topology_device_relation:fk_topology_device_topology`|`CONSTRAINT fk_topology_device_topology FOREIGN KEY (tenant_id, topology_id) REFERENCES ast_network_topology (tenant_id, id)`|
|`CONSTRAINT:ast_network_topology_device_relation:uk_topology_device_rel_tenant_row`|`UNIQUE KEY uk_topology_device_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_product:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_product:chk_product_deleted`|`CONSTRAINT chk_product_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_product:chk_product_service`|`CONSTRAINT chk_product_service CHECK (service_product_flag IN (0, 1))`|
|`CONSTRAINT:ast_product:uk_product_tenant_row`|`UNIQUE KEY uk_product_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_product_release:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_product_release:chk_product_release_deleted`|`CONSTRAINT chk_product_release_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:ast_product_release:fk_product_release_product`|`CONSTRAINT fk_product_release_product FOREIGN KEY (tenant_id, product_id) REFERENCES ast_product (tenant_id, id)`|
|`CONSTRAINT:ast_product_release:uk_product_release_tenant_row`|`UNIQUE KEY uk_product_release_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_contract:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_contract:chk_contract_dates`|`CONSTRAINT chk_contract_dates CHECK (expiry_date IS NULL OR effective_date IS NULL OR expiry_date >= effective_date)`|
|`CONSTRAINT:com_contract:chk_contract_deleted`|`CONSTRAINT chk_contract_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_contract:uk_contract_tenant_row`|`UNIQUE KEY uk_contract_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_contract_receivable:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_contract_receivable:chk_contract_receivable_dates`|`CONSTRAINT chk_contract_receivable_dates CHECK ( source_effective_to IS NULL OR source_effective_from IS NULL OR source_effective_to >= source_effective_from )`|
|`CONSTRAINT:com_contract_receivable:chk_contract_receivable_deleted`|`CONSTRAINT chk_contract_receivable_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_contract_receivable:fk_contract_receivable_contract`|`CONSTRAINT fk_contract_receivable_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|
|`CONSTRAINT:com_contract_receivable:uk_contract_receivable_tenant_row`|`UNIQUE KEY uk_contract_receivable_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_crm_execution_config:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_crm_execution_config:chk_crm_execution_config_af`|`CONSTRAINT chk_crm_execution_config_af CHECK (is_af_evidence IN (0, 1))`|
|`CONSTRAINT:com_crm_execution_config:chk_crm_execution_config_deleted`|`CONSTRAINT chk_crm_execution_config_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_crm_execution_config:fk_crm_execution_config_execution`|`CONSTRAINT fk_crm_execution_config_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|
|`CONSTRAINT:com_crm_execution_config:uk_crm_execution_config_tenant_row`|`UNIQUE KEY uk_crm_execution_config_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_crm_execution_order:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_crm_execution_order:chk_crm_execution_deleted`|`CONSTRAINT chk_crm_execution_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_crm_execution_order:uk_crm_execution_order_tenant_row`|`UNIQUE KEY uk_crm_execution_order_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_delivery_scope:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_delivery_scope:chk_scope_dates`|`CONSTRAINT chk_scope_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:com_delivery_scope:chk_scope_deleted`|`CONSTRAINT chk_scope_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_delivery_scope:fk_scope_order_line`|`CONSTRAINT fk_scope_order_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|
|`CONSTRAINT:com_delivery_scope:uk_project_order_line_scope_tenant_row`|`UNIQUE KEY uk_project_order_line_scope_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_delivery_scope_detail:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_delivery_scope_detail:chk_delivery_scope_detail_deleted`|`CONSTRAINT chk_delivery_scope_detail_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_delivery_scope_detail:fk_delivery_scope_detail_scope`|`CONSTRAINT fk_delivery_scope_detail_scope FOREIGN KEY (tenant_id, delivery_scope_id) REFERENCES com_delivery_scope (tenant_id, id)`|
|`CONSTRAINT:com_delivery_scope_detail:uk_delivery_scope_detail_tenant_row`|`UNIQUE KEY uk_delivery_scope_detail_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_batch:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_execution_order_merge_batch:chk_execution_merge_deleted`|`CONSTRAINT chk_execution_merge_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_execution_order_merge_batch:fk_execution_merge_contract`|`CONSTRAINT fk_execution_merge_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_batch:fk_execution_merge_primary`|`CONSTRAINT fk_execution_merge_primary FOREIGN KEY (tenant_id, primary_execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_batch:uk_execution_merge_batch_tenant_row`|`UNIQUE KEY uk_execution_merge_batch_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_member:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_execution_order_merge_member:chk_execution_merge_member_deleted`|`CONSTRAINT chk_execution_merge_member_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_execution_order_merge_member:chk_execution_merge_member_primary`|`CONSTRAINT chk_execution_merge_member_primary CHECK (is_primary IN (0, 1))`|
|`CONSTRAINT:com_execution_order_merge_member:fk_execution_merge_member_batch`|`CONSTRAINT fk_execution_merge_member_batch FOREIGN KEY (tenant_id, merge_batch_id) REFERENCES com_execution_order_merge_batch (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_member:fk_execution_merge_member_execution`|`CONSTRAINT fk_execution_merge_member_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|
|`CONSTRAINT:com_execution_order_merge_member:uk_execution_merge_member_tenant_row`|`UNIQUE KEY uk_execution_merge_member_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_order_change_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_order_change_relation:chk_order_change_deleted`|`CONSTRAINT chk_order_change_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_order_change_relation:chk_order_change_self`|`CONSTRAINT chk_order_change_self CHECK (source_order_id <> target_order_id)`|
|`CONSTRAINT:com_order_change_relation:fk_order_change_source`|`CONSTRAINT fk_order_change_source FOREIGN KEY (tenant_id, source_order_id) REFERENCES com_sales_order (tenant_id, id)`|
|`CONSTRAINT:com_order_change_relation:fk_order_change_target`|`CONSTRAINT fk_order_change_target FOREIGN KEY (tenant_id, target_order_id) REFERENCES com_sales_order (tenant_id, id)`|
|`CONSTRAINT:com_order_change_relation:uk_order_change_rel_tenant_row`|`UNIQUE KEY uk_order_change_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_order_contract_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_order_contract_relation:chk_order_contract_deleted`|`CONSTRAINT chk_order_contract_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_order_contract_relation:fk_order_contract_contract`|`CONSTRAINT fk_order_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|
|`CONSTRAINT:com_order_contract_relation:fk_order_contract_order`|`CONSTRAINT fk_order_contract_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|
|`CONSTRAINT:com_order_contract_relation:uk_order_contract_rel_tenant_row`|`UNIQUE KEY uk_order_contract_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_order_execution_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_order_execution_relation:chk_order_execution_deleted`|`CONSTRAINT chk_order_execution_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_order_execution_relation:chk_order_execution_primary`|`CONSTRAINT chk_order_execution_primary CHECK (is_primary IN (0, 1))`|
|`CONSTRAINT:com_order_execution_relation:fk_order_execution_execution`|`CONSTRAINT fk_order_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|
|`CONSTRAINT:com_order_execution_relation:fk_order_execution_order`|`CONSTRAINT fk_order_execution_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|
|`CONSTRAINT:com_order_execution_relation:uk_order_execution_rel_tenant_row`|`UNIQUE KEY uk_order_execution_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_order_line_execution_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_order_line_execution_relation:chk_order_line_execution_deleted`|`CONSTRAINT chk_order_line_execution_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_order_line_execution_relation:fk_order_line_execution_execution`|`CONSTRAINT fk_order_line_execution_execution FOREIGN KEY (tenant_id, execution_id) REFERENCES com_crm_execution_order (tenant_id, id)`|
|`CONSTRAINT:com_order_line_execution_relation:fk_order_line_execution_line`|`CONSTRAINT fk_order_line_execution_line FOREIGN KEY (tenant_id, order_line_id) REFERENCES com_sales_order_line (tenant_id, id)`|
|`CONSTRAINT:com_order_line_execution_relation:uk_order_line_execution_rel_tenant_row`|`UNIQUE KEY uk_order_line_execution_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_project_contract_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_project_contract_relation:chk_project_contract_dates`|`CONSTRAINT chk_project_contract_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:com_project_contract_relation:chk_project_contract_deleted`|`CONSTRAINT chk_project_contract_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_project_contract_relation:fk_project_contract_contract`|`CONSTRAINT fk_project_contract_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|
|`CONSTRAINT:com_project_contract_relation:uk_project_contract_rel_tenant_row`|`UNIQUE KEY uk_project_contract_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_sales_order:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_sales_order:chk_sales_order_deleted`|`CONSTRAINT chk_sales_order_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_sales_order:uk_sales_order_tenant_row`|`UNIQUE KEY uk_sales_order_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_sales_order_line:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_sales_order_line:chk_sales_order_line_deleted`|`CONSTRAINT chk_sales_order_line_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_sales_order_line:fk_sales_order_line_order`|`CONSTRAINT fk_sales_order_line_order FOREIGN KEY (tenant_id, order_id) REFERENCES com_sales_order (tenant_id, id)`|
|`CONSTRAINT:com_sales_order_line:uk_sales_order_line_tenant_row`|`UNIQUE KEY uk_sales_order_line_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_shipment_contract_reference:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_shipment_contract_reference:chk_shipment_contract_ref_deleted`|`CONSTRAINT chk_shipment_contract_ref_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_shipment_contract_reference:fk_shipment_contract_ref_contract`|`CONSTRAINT fk_shipment_contract_ref_contract FOREIGN KEY (tenant_id, contract_id) REFERENCES com_contract (tenant_id, id)`|
|`CONSTRAINT:com_shipment_contract_reference:uk_shipment_contract_ref_tenant_row`|`UNIQUE KEY uk_shipment_contract_ref_tenant_row (tenant_id, id)`|
|`CONSTRAINT:com_shipment_package:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:com_shipment_package:chk_shipment_package_deleted`|`CONSTRAINT chk_shipment_package_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:com_shipment_package:chk_shipment_package_warranty_dates`|`CONSTRAINT chk_shipment_package_warranty_dates CHECK ( warranty_end_time IS NULL OR warranty_start_time IS NULL OR warranty_end_time >= warranty_start_time )`|
|`CONSTRAINT:com_shipment_package:fk_shipment_package_contract_ref`|`CONSTRAINT fk_shipment_package_contract_ref FOREIGN KEY (tenant_id, shipment_contract_ref_id) REFERENCES com_shipment_contract_reference (tenant_id, id)`|
|`CONSTRAINT:com_shipment_package:uk_shipment_package_tenant_row`|`UNIQUE KEY uk_shipment_package_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cus_customer:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cus_customer:chk_customer_deleted`|`CONSTRAINT chk_customer_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:cus_customer:uk_customer_tenant_row`|`UNIQUE KEY uk_customer_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cus_customer_contact:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cus_customer_contact:chk_customer_contact_deleted`|`CONSTRAINT chk_customer_contact_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:cus_customer_contact:chk_customer_contact_primary`|`CONSTRAINT chk_customer_contact_primary CHECK (is_primary IN (0, 1))`|
|`CONSTRAINT:cus_customer_contact:fk_customer_contact_customer`|`CONSTRAINT fk_customer_contact_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES cus_customer (tenant_id, id)`|
|`CONSTRAINT:cus_customer_contact:uk_customer_contact_tenant_row`|`UNIQUE KEY uk_customer_contact_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cus_market_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cus_market_relation:chk_market_relation_deleted`|`CONSTRAINT chk_market_relation_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:cus_market_relation:uk_market_relation_tenant_row`|`UNIQUE KEY uk_market_relation_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cut_cutover_closure:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_execution`|`CONSTRAINT chk_cutover_closure_execution CHECK (execution_normal IS NULL OR execution_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_precheck`|`CONSTRAINT chk_cutover_closure_precheck CHECK (precheck_normal IS NULL OR precheck_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_rollback`|`CONSTRAINT chk_cutover_closure_rollback CHECK (rollback_occurred IS NULL OR rollback_occurred IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_test`|`CONSTRAINT chk_cutover_closure_test CHECK (test_normal IS NULL OR test_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:uk_cutover_closure_tenant_row`|`UNIQUE KEY uk_cutover_closure_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cut_cutover_support_arrangement:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cut_cutover_support_arrangement:chk_cutover_support_arrangement_no`|`CONSTRAINT chk_cutover_support_arrangement_no CHECK (arrangement_no > 0)`|
|`CONSTRAINT:cut_cutover_support_arrangement:uk_cutover_support_arrangement_tenant_row`|`UNIQUE KEY uk_cutover_support_arrangement_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:chk_configuration_parse_attempt_no`|`CONSTRAINT chk_configuration_parse_attempt_no CHECK (attempt_no > 0)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:chk_configuration_parse_attempt_time`|`CONSTRAINT chk_configuration_parse_attempt_time CHECK (completed_time IS NULL OR completed_time >= started_time)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:uk_configuration_parse_attempt_tenant_row`|`UNIQUE KEY uk_configuration_parse_attempt_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_collection_result:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_collection_result:chk_configuration_collection_result_version`|`CONSTRAINT chk_configuration_collection_result_version CHECK (result_version_no > 0)`|
|`CONSTRAINT:imp_configuration_collection_result:uk_configuration_collection_result_tenant_row`|`UNIQUE KEY uk_configuration_collection_result_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_component_candidate:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_component_candidate:chk_configuration_component_candidate_no`|`CONSTRAINT chk_configuration_component_candidate_no CHECK (candidate_no > 0)`|
|`CONSTRAINT:imp_configuration_component_candidate:uk_configuration_component_candidate_tenant_row`|`UNIQUE KEY uk_configuration_component_candidate_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_business_document:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_business_document:chk_business_document_deleted`|`CONSTRAINT chk_business_document_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:plt_business_document:fk_business_document_current_version`|`CONSTRAINT fk_business_document_current_version FOREIGN KEY (tenant_id, id, current_version_id) REFERENCES plt_document_version (tenant_id, document_id, id)`|
|`CONSTRAINT:plt_business_document:uk_business_document_tenant_row`|`UNIQUE KEY uk_business_document_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_document_version:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_document_version:chk_document_version_deleted`|`CONSTRAINT chk_document_version_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:plt_document_version:fk_document_version_document`|`CONSTRAINT fk_document_version_document FOREIGN KEY (tenant_id, document_id) REFERENCES plt_business_document (tenant_id, id)`|
|`CONSTRAINT:plt_document_version:uk_document_version_owner`|`UNIQUE KEY uk_document_version_owner (tenant_id, document_id, id)`|
|`CONSTRAINT:plt_document_version:uk_document_version_tenant_row`|`UNIQUE KEY uk_document_version_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_external_key_mapping:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_external_key_mapping:chk_external_key_target_sequence`|`CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)`|
|`CONSTRAINT:plt_external_key_mapping:fk_external_key_batch`|`CONSTRAINT fk_external_key_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|
|`CONSTRAINT:plt_external_key_mapping:uk_external_key_map_tenant_row`|`UNIQUE KEY uk_external_key_map_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_migration_issue:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_migration_issue:fk_migration_issue_batch`|`CONSTRAINT fk_migration_issue_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|
|`CONSTRAINT:plt_migration_issue:uk_migration_issue_tenant_row`|`UNIQUE KEY uk_migration_issue_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_migration_source_record:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_migration_source_record:chk_migration_source_target_count`|`CONSTRAINT chk_migration_source_target_count CHECK (mapped_target_count >= 0)`|
|`CONSTRAINT:plt_migration_source_record:fk_migration_source_batch`|`CONSTRAINT fk_migration_source_batch FOREIGN KEY (tenant_id, batch_id) REFERENCES plt_sync_batch (tenant_id, id)`|
|`CONSTRAINT:plt_migration_source_record:uk_migration_source_record_tenant_row`|`UNIQUE KEY uk_migration_source_record_tenant_row (tenant_id, id)`|
|`CONSTRAINT:plt_sync_batch:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:plt_sync_batch:chk_sync_batch_count`|`CONSTRAINT chk_sync_batch_count CHECK (success_count + failure_count <= read_count)`|
|`CONSTRAINT:plt_sync_batch:chk_sync_batch_time`|`CONSTRAINT chk_sync_batch_time CHECK (finished_time IS NULL OR finished_time >= started_time)`|
|`CONSTRAINT:plt_sync_batch:uk_sync_batch_tenant_row`|`UNIQUE KEY uk_sync_batch_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project:chk_project_deleted`|`CONSTRAINT chk_project_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project:chk_project_depth`|`CONSTRAINT chk_project_depth CHECK (tree_depth >= 0)`|
|`CONSTRAINT:proj_project:fk_project_code_root`|`CONSTRAINT fk_project_code_root FOREIGN KEY (tenant_id, code_root_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project:fk_project_parent`|`CONSTRAINT fk_project_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project:uk_project_tenant_row`|`UNIQUE KEY uk_project_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_company_department_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_company_department_relation:chk_project_company_department_dates`|`CONSTRAINT chk_project_company_department_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:proj_project_company_department_relation:chk_project_company_department_deleted`|`CONSTRAINT chk_project_company_department_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_company_department_relation:chk_project_company_department_primary`|`CONSTRAINT chk_project_company_department_primary CHECK (is_primary IN (0, 1))`|
|`CONSTRAINT:proj_project_company_department_relation:fk_project_company_department_project`|`CONSTRAINT fk_project_company_department_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_company_department_relation:uk_project_company_department_rel_tenant_row`|`UNIQUE KEY uk_project_company_department_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_member_assignment:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_member_assignment:chk_project_member_dates`|`CONSTRAINT chk_project_member_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:proj_project_member_assignment:chk_project_member_deleted`|`CONSTRAINT chk_project_member_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_member_assignment:fk_project_member_project`|`CONSTRAINT fk_project_member_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_member_assignment:uk_project_member_tenant_row`|`UNIQUE KEY uk_project_member_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_party:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_party:chk_project_party_dates`|`CONSTRAINT chk_project_party_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:proj_project_party:chk_project_party_deleted`|`CONSTRAINT chk_project_party_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_party:fk_project_party_project`|`CONSTRAINT fk_project_party_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_party:uk_project_party_tenant_row`|`UNIQUE KEY uk_project_party_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_portfolio:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_portfolio:chk_portfolio_deleted`|`CONSTRAINT chk_portfolio_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_portfolio:uk_portfolio_tenant_row`|`UNIQUE KEY uk_portfolio_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_portfolio_member:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_portfolio_member:chk_portfolio_project_deleted`|`CONSTRAINT chk_portfolio_project_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_portfolio_member:fk_portfolio_project_portfolio`|`CONSTRAINT fk_portfolio_project_portfolio FOREIGN KEY (tenant_id, portfolio_id) REFERENCES proj_project_portfolio (tenant_id, id)`|
|`CONSTRAINT:proj_project_portfolio_member:fk_portfolio_project_project`|`CONSTRAINT fk_portfolio_project_project FOREIGN KEY (tenant_id, project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_portfolio_member:uk_portfolio_project_rel_tenant_row`|`UNIQUE KEY uk_portfolio_project_rel_tenant_row (tenant_id, id)`|
|`CONSTRAINT:proj_project_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:proj_project_relation:chk_project_relation_deleted`|`CONSTRAINT chk_project_relation_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:proj_project_relation:chk_project_relation_self`|`CONSTRAINT chk_project_relation_self CHECK (source_project_id <> target_project_id)`|
|`CONSTRAINT:proj_project_relation:fk_project_rel_source`|`CONSTRAINT fk_project_rel_source FOREIGN KEY (tenant_id, source_project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_relation:fk_project_rel_target`|`CONSTRAINT fk_project_rel_target FOREIGN KEY (tenant_id, target_project_id) REFERENCES proj_project (tenant_id, id)`|
|`CONSTRAINT:proj_project_relation:uk_project_relation_tenant_row`|`UNIQUE KEY uk_project_relation_tenant_row (tenant_id, id)`|
|`CONSTRAINT:srv_service_incident:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:srv_service_incident:chk_service_incident_deleted`|`CONSTRAINT chk_service_incident_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:srv_service_incident:chk_service_incident_times`|`CONSTRAINT chk_service_incident_times CHECK ( restored_time IS NULL OR occurred_time IS NULL OR restored_time >= occurred_time )`|
|`CONSTRAINT:srv_service_incident:uk_service_incident_tenant_row`|`UNIQUE KEY uk_service_incident_tenant_row (tenant_id, id)`|
|`CONSTRAINT:srv_service_incident_device_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:srv_service_incident_device_relation:chk_incident_device_deleted`|`CONSTRAINT chk_incident_device_deleted CHECK (deleted IN (0, 1))`|
|`CONSTRAINT:srv_service_incident_device_relation:fk_incident_device_incident`|`CONSTRAINT fk_incident_device_incident FOREIGN KEY (tenant_id, incident_id) REFERENCES srv_service_incident (tenant_id, id)`|
|`CONSTRAINT:srv_service_incident_device_relation:uk_incident_device_rel_tenant_row`|`UNIQUE KEY uk_incident_device_rel_tenant_row (tenant_id, id)`|

## Q08 当前哈希候选索引（122项）

推荐：**A**。接受122项为候选索引基线；不代表性能验收，后续仅以前向迁移调整。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:acc_project_deliverable:idx_deliverable_owner`|`KEY idx_deliverable_owner (tenant_id, owner_id, status, planned_due_date)`|
|`CONSTRAINT:acc_project_deliverable:idx_project_deliverable`|`KEY idx_project_deliverable (tenant_id, project_id, deliverable_type, status)`|
|`CONSTRAINT:acc_satisfaction_collection_task:idx_satisfaction_task_owner`|`KEY idx_satisfaction_task_owner (tenant_id, current_responsible_user_id, status_code)`|
|`CONSTRAINT:acc_satisfaction_collection_task:idx_satisfaction_task_source`|`KEY idx_satisfaction_task_source (tenant_id, source_context, source_object_type, source_object_id)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:idx_satisfaction_questionnaire_task`|`KEY idx_satisfaction_questionnaire_task (tenant_id, task_id, create_time)`|
|`CONSTRAINT:acc_satisfaction_response:idx_satisfaction_response_questionnaire`|`KEY idx_satisfaction_response_questionnaire (tenant_id, questionnaire_id, submit_time)`|
|`CONSTRAINT:acc_satisfaction_result:idx_satisfaction_result_gate`|`KEY idx_satisfaction_result_gate (tenant_id, passed, decision_time)`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_company_department`|`KEY idx_project_summary_company_department ( tenant_id, company_code, department_code, project_status, project_id )`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_customer`|`KEY idx_project_summary_customer ( tenant_id, customer_code, project_status, project_id )`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_manager`|`KEY idx_project_summary_manager ( tenant_id, manager_employee_no, project_status, project_id )`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_project_status`|`KEY idx_project_summary_project_status ( tenant_id, project_status, project_type, project_id )`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_status`|`KEY idx_project_summary_status ( tenant_id, pending_mapping_count, pending_qty_count )`|
|`CONSTRAINT:ana_project_delivery_summary:idx_project_summary_time`|`KEY idx_project_summary_time (tenant_id, statistic_time)`|
|`CONSTRAINT:ast_device_component_relation:idx_device_component_card`|`KEY idx_device_component_card (tenant_id, card_sn, effective_to)`|
|`CONSTRAINT:ast_device_component_relation:idx_device_component_chassis`|`KEY idx_device_component_chassis (tenant_id, chassis_sn, effective_from)`|
|`CONSTRAINT:ast_device_configuration:idx_device_configuration`|`KEY idx_device_configuration (tenant_id, device_id, status, effective_from)`|
|`CONSTRAINT:ast_device_configuration:idx_project_configuration`|`KEY idx_project_configuration (tenant_id, project_id, configuration_stage)`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_company_department`|`KEY idx_device_assignment_company_department ( tenant_id, project_company_code, project_department_code, effective_to, project_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_customer`|`KEY idx_device_assignment_customer ( tenant_id, project_customer_code, effective_to, project_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_device`|`KEY idx_device_assignment_device ( tenant_id, device_id, effective_to, project_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_order`|`KEY idx_device_assignment_order ( tenant_id, order_no, line_no, effective_to, project_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_project`|`KEY idx_device_assignment_project ( tenant_id, project_id, effective_to, device_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_project_code`|`KEY idx_device_assignment_project_code ( tenant_id, project_code, effective_to, device_id )`|
|`CONSTRAINT:ast_device_project_assignment:idx_device_assignment_sn`|`KEY idx_device_assignment_sn ( tenant_id, device_sn, effective_to, project_id )`|
|`CONSTRAINT:ast_device_relation:idx_device_relation_contract_refresh`|`KEY idx_device_relation_contract_refresh ( tenant_id, contract_id, relation_type, status, source_device_id )`|
|`CONSTRAINT:ast_device_relation:idx_device_relation_latest`|`KEY idx_device_relation_latest ( tenant_id, source_device_id, contract_id, relation_type, status, effective_time, id )`|
|`CONSTRAINT:ast_device_relation:idx_device_relation_source_device`|`KEY idx_device_relation_source_device ( tenant_id, source_device_id, relation_type )`|
|`CONSTRAINT:ast_device_relation:idx_device_relation_target_device`|`KEY idx_device_relation_target_device ( tenant_id, target_device_id, relation_type )`|
|`CONSTRAINT:ast_device_shipment_event:idx_shipment_device`|`KEY idx_shipment_device (tenant_id, device_id, shipment_time)`|
|`CONSTRAINT:ast_device_shipment_event:idx_shipment_order_line`|`KEY idx_shipment_order_line (tenant_id, order_line_id, shipment_time)`|
|`CONSTRAINT:ast_device_shipment_event:idx_shipment_package`|`KEY idx_shipment_package (tenant_id, shipment_package_id, device_id)`|
|`CONSTRAINT:ast_device_shipment_event:idx_shipment_rma`|`KEY idx_shipment_rma ( tenant_id, rma_marked, business_action_code, rma_no )`|
|`CONSTRAINT:ast_device_sn:idx_device_internal_serial_no`|`KEY idx_device_internal_serial_no (tenant_id, internal_serial_no)`|
|`CONSTRAINT:ast_device_sn:idx_device_item`|`KEY idx_device_item (tenant_id, item_code, asset_status)`|
|`CONSTRAINT:ast_device_sn:idx_device_secondary_sn`|`KEY idx_device_secondary_sn (tenant_id, secondary_sn)`|
|`CONSTRAINT:ast_device_version:idx_device_version_current`|`KEY idx_device_version_current ( tenant_id, device_id, component_type, status, effective_from )`|
|`CONSTRAINT:ast_device_version:idx_project_device_version`|`KEY idx_project_device_version (tenant_id, project_id, version_stage)`|
|`CONSTRAINT:ast_network_topology:idx_network_topology_project`|`KEY idx_network_topology_project (tenant_id, project_id, status)`|
|`CONSTRAINT:ast_network_topology_device_relation:idx_topology_device_reverse`|`KEY idx_topology_device_reverse (tenant_id, device_id, topology_id)`|
|`CONSTRAINT:ast_product:idx_product_line`|`KEY idx_product_line (tenant_id, product_line_code, status)`|
|`CONSTRAINT:com_contract:idx_contract_company`|`KEY idx_contract_company (tenant_id, company_id, status, contract_no)`|
|`CONSTRAINT:com_contract:idx_contract_customer`|`KEY idx_contract_customer (tenant_id, customer_id, status)`|
|`CONSTRAINT:com_contract:idx_contract_no`|`KEY idx_contract_no (tenant_id, contract_no, company_code)`|
|`CONSTRAINT:com_contract_receivable:idx_contract_receivable_business`|`KEY idx_contract_receivable_business ( tenant_id, contract_no, company_code, mapping_status )`|
|`CONSTRAINT:com_contract_receivable:idx_contract_receivable_company`|`KEY idx_contract_receivable_company ( tenant_id, company_id, mapping_status, contract_id )`|
|`CONSTRAINT:com_contract_receivable:idx_contract_receivable_contract`|`KEY idx_contract_receivable_contract ( tenant_id, contract_id, source_sync_time )`|
|`CONSTRAINT:com_crm_execution_config:idx_crm_execution_config_company`|`KEY idx_crm_execution_config_company ( tenant_id, company_code, status, execution_id )`|
|`CONSTRAINT:com_crm_execution_config:idx_crm_execution_config_execution`|`KEY idx_crm_execution_config_execution ( tenant_id, execution_id, item_code )`|
|`CONSTRAINT:com_crm_execution_order:idx_crm_execution_company_office`|`KEY idx_crm_execution_company_office ( tenant_id, company_id, office_department_id, status, id )`|
|`CONSTRAINT:com_crm_execution_order:idx_crm_execution_company_office_code`|`KEY idx_crm_execution_company_office_code ( tenant_id, company_code, office_department_code, status, id )`|
|`CONSTRAINT:com_crm_execution_order:idx_crm_execution_crm_project`|`KEY idx_crm_execution_crm_project ( tenant_id, crm_project_code, execution_no )`|
|`CONSTRAINT:com_crm_execution_order:idx_crm_execution_project`|`KEY idx_crm_execution_project ( tenant_id, primary_project_id, status )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_item`|`KEY idx_scope_item (tenant_id, item_code, scope_status, project_id)`|
|`CONSTRAINT:com_delivery_scope:idx_scope_order_business`|`KEY idx_scope_order_business ( tenant_id, order_source_system, order_company_code, order_type, order_no, line_no )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_order_line`|`KEY idx_scope_order_line ( tenant_id, order_line_id, scope_status, project_id )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_project`|`KEY idx_scope_project ( tenant_id, project_id, scope_status, order_line_id )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_project_company`|`KEY idx_scope_project_company ( tenant_id, project_company_code, scope_status, project_id )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_project_customer`|`KEY idx_scope_project_customer ( tenant_id, project_customer_code, scope_status, project_id )`|
|`CONSTRAINT:com_delivery_scope:idx_scope_project_department`|`KEY idx_scope_project_department ( tenant_id, project_department_code, scope_status, project_id )`|
|`CONSTRAINT:com_delivery_scope_detail:idx_delivery_scope_detail_location`|`KEY idx_delivery_scope_detail_location ( tenant_id, implementation_location, delivery_scope_id )`|
|`CONSTRAINT:com_delivery_scope_detail:idx_delivery_scope_detail_product`|`KEY idx_delivery_scope_detail_product ( tenant_id, product_code, device_type_code, delivery_scope_id )`|
|`CONSTRAINT:com_execution_order_merge_batch:idx_execution_merge_primary`|`KEY idx_execution_merge_primary ( tenant_id, primary_execution_id, status )`|
|`CONSTRAINT:com_execution_order_merge_member:idx_execution_merge_member_execution`|`KEY idx_execution_merge_member_execution ( tenant_id, execution_id, merge_batch_id )`|
|`CONSTRAINT:com_order_change_relation:idx_order_change_target`|`KEY idx_order_change_target ( tenant_id, target_order_id, relation_type )`|
|`CONSTRAINT:com_order_contract_relation:idx_order_contract_reverse`|`KEY idx_order_contract_reverse (tenant_id, contract_id, order_id)`|
|`CONSTRAINT:com_order_execution_relation:idx_order_execution_execution`|`KEY idx_order_execution_execution ( tenant_id, execution_id, order_id )`|
|`CONSTRAINT:com_order_line_execution_relation:idx_order_line_execution_reverse`|`KEY idx_order_line_execution_reverse (tenant_id, execution_id, order_line_id)`|
|`CONSTRAINT:com_project_contract_relation:idx_project_contract_reverse`|`KEY idx_project_contract_reverse (tenant_id, contract_id, project_id)`|
|`CONSTRAINT:com_sales_order:idx_sales_order_company`|`KEY idx_sales_order_company (tenant_id, company_id, status, order_no)`|
|`CONSTRAINT:com_sales_order:idx_sales_order_customer`|`KEY idx_sales_order_customer (tenant_id, customer_code, status)`|
|`CONSTRAINT:com_sales_order:idx_sales_order_no`|`KEY idx_sales_order_no (tenant_id, order_no)`|
|`CONSTRAINT:com_sales_order:idx_sales_order_time`|`KEY idx_sales_order_time (tenant_id, order_create_time, status)`|
|`CONSTRAINT:com_sales_order_line:idx_sales_order_line_business`|`KEY idx_sales_order_line_business ( tenant_id, source_system, company_code, order_type, order_no, line_no )`|
|`CONSTRAINT:com_sales_order_line:idx_sales_order_line_customer`|`KEY idx_sales_order_line_customer (tenant_id, customer_code, status, id)`|
|`CONSTRAINT:com_sales_order_line:idx_sales_order_line_item`|`KEY idx_sales_order_line_item (tenant_id, item_code)`|
|`CONSTRAINT:com_sales_order_line:idx_sales_order_line_profit`|`KEY idx_sales_order_line_profit (tenant_id, profit_center, order_id)`|
|`CONSTRAINT:com_shipment_contract_reference:idx_shipment_contract_ref_company`|`KEY idx_shipment_contract_ref_company ( tenant_id, company_id, mapping_status, contract_id )`|
|`CONSTRAINT:com_shipment_contract_reference:idx_shipment_contract_ref_contract`|`KEY idx_shipment_contract_ref_contract ( tenant_id, contract_id, mapping_status )`|
|`CONSTRAINT:com_shipment_contract_reference:idx_shipment_contract_ref_no`|`KEY idx_shipment_contract_ref_no ( tenant_id, contract_no, company_code, mapping_status )`|
|`CONSTRAINT:com_shipment_package:idx_shipment_package_contract_ref`|`KEY idx_shipment_package_contract_ref ( tenant_id, shipment_contract_ref_id, shipment_time )`|
|`CONSTRAINT:cus_customer:idx_customer_market_relation`|`KEY idx_customer_market_relation ( tenant_id, market_code, system_code, expend_code, industry_code )`|
|`CONSTRAINT:cus_customer:idx_customer_name`|`KEY idx_customer_name (tenant_id, customer_name)`|
|`CONSTRAINT:cus_customer_contact:idx_customer_contact`|`KEY idx_customer_contact (tenant_id, customer_id, status, is_primary)`|
|`CONSTRAINT:cus_market_relation:idx_market_relation_name`|`KEY idx_market_relation_name ( tenant_id, market_name(64), system_name(64), expend_name(64), industry_name(64) )`|
|`CONSTRAINT:cut_cutover_closure:idx_cutover_closure_result`|`KEY idx_cutover_closure_result (tenant_id, result_code, archive_time)`|
|`CONSTRAINT:cut_cutover_support_arrangement:idx_cutover_support_arrangement_task`|`KEY idx_cutover_support_arrangement_task (tenant_id, cutover_task_id, plan_revision_id)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:idx_configuration_parse_attempt_result`|`KEY idx_configuration_parse_attempt_result (tenant_id, collection_result_id, started_time)`|
|`CONSTRAINT:imp_configuration_collection_result:idx_configuration_collection_result_device`|`KEY idx_configuration_collection_result_device (tenant_id, project_id, device_id, operated_time)`|
|`CONSTRAINT:imp_configuration_collection_result:idx_configuration_collection_result_hash`|`KEY idx_configuration_collection_result_hash (tenant_id, raw_log_sha256)`|
|`CONSTRAINT:imp_configuration_component_candidate:idx_configuration_component_candidate_match`|`KEY idx_configuration_component_candidate_match (tenant_id, match_status_code, create_time)`|
|`CONSTRAINT:imp_configuration_component_candidate:idx_configuration_component_candidate_sn`|`KEY idx_configuration_component_candidate_sn (tenant_id, chassis_sn, slot_code, card_sn)`|
|`CONSTRAINT:plt_document_version:idx_document_file`|`KEY idx_document_file (tenant_id, file_id)`|
|`CONSTRAINT:plt_external_key_mapping:idx_external_key_batch`|`KEY idx_external_key_batch (tenant_id, batch_id, mapping_status)`|
|`CONSTRAINT:plt_external_key_mapping:idx_external_key_source`|`KEY idx_external_key_source ( tenant_id, source_system, source_table, source_pk )`|
|`CONSTRAINT:plt_external_key_mapping:idx_external_key_target`|`KEY idx_external_key_target ( tenant_id, target_table, target_id )`|
|`CONSTRAINT:plt_migration_issue:idx_migration_issue_status`|`KEY idx_migration_issue_status ( tenant_id, issue_type, resolution_status, create_time )`|
|`CONSTRAINT:plt_migration_source_record:idx_migration_source_business`|`KEY idx_migration_source_business ( tenant_id, source_system, source_table, source_business_key(191) )`|
|`CONSTRAINT:plt_migration_source_record:idx_migration_source_mapping`|`KEY idx_migration_source_mapping ( tenant_id, batch_id, source_table, mapping_status )`|
|`CONSTRAINT:plt_sync_batch:idx_sync_batch_object`|`KEY idx_sync_batch_object ( tenant_id, source_system, object_type, started_time )`|
|`CONSTRAINT:proj_project:idx_project_company_department`|`KEY idx_project_company_department ( tenant_id, company_code, department_code, status, id )`|
|`CONSTRAINT:proj_project:idx_project_company_department_id`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, id )`|
|`CONSTRAINT:proj_project:idx_project_customer_code`|`KEY idx_project_customer_code (tenant_id, customer_code, status, id)`|
|`CONSTRAINT:proj_project:idx_project_department_company`|`KEY idx_project_department_company ( tenant_id, department_code, company_code, status, id )`|
|`CONSTRAINT:proj_project:idx_project_manager`|`KEY idx_project_manager (tenant_id, manager_id, status)`|
|`CONSTRAINT:proj_project:idx_project_manager_employee`|`KEY idx_project_manager_employee (tenant_id, manager_employee_no, status, id)`|
|`CONSTRAINT:proj_project:idx_project_market_relation`|`KEY idx_project_market_relation ( tenant_id, market_code, system_code, expend_code, industry_code, status, id )`|
|`CONSTRAINT:proj_project:idx_project_parent`|`KEY idx_project_parent (tenant_id, parent_id, tree_sort, id)`|
|`CONSTRAINT:proj_project:idx_project_path`|`KEY idx_project_path (tenant_id, root_id, tree_path(191))`|
|`CONSTRAINT:proj_project_company_department_relation:idx_project_company_department_id`|`KEY idx_project_company_department_id ( tenant_id, company_id, department_id, status, project_id )`|
|`CONSTRAINT:proj_project_company_department_relation:idx_project_company_reverse`|`KEY idx_project_company_reverse ( tenant_id, company_code, relation_role, status, project_id )`|
|`CONSTRAINT:proj_project_company_department_relation:idx_project_department_reverse`|`KEY idx_project_department_reverse ( tenant_id, department_code, company_code, relation_role, status, project_id )`|
|`CONSTRAINT:proj_project_member_assignment:idx_project_member_company_department`|`KEY idx_project_member_company_department ( tenant_id, company_code, department_code, status, project_id )`|
|`CONSTRAINT:proj_project_member_assignment:idx_project_member_employee`|`KEY idx_project_member_employee (tenant_id, employee_no, status, project_id)`|
|`CONSTRAINT:proj_project_member_assignment:idx_project_member_user`|`KEY idx_project_member_user (tenant_id, user_id, status, project_id)`|
|`CONSTRAINT:proj_project_party:idx_project_party_code`|`KEY idx_project_party_code ( tenant_id, party_role, party_code, status )`|
|`CONSTRAINT:proj_project_party:idx_project_party_project`|`KEY idx_project_party_project ( tenant_id, project_id, party_role, status )`|
|`CONSTRAINT:proj_project_portfolio:idx_portfolio_owner`|`KEY idx_portfolio_owner (tenant_id, owner_id, status)`|
|`CONSTRAINT:proj_project_portfolio_member:idx_portfolio_project_reverse`|`KEY idx_portfolio_project_reverse (tenant_id, project_id, portfolio_id)`|
|`CONSTRAINT:proj_project_relation:idx_project_relation_target`|`KEY idx_project_relation_target ( tenant_id, target_project_id, relation_type )`|
|`CONSTRAINT:srv_service_incident:idx_incident_owner`|`KEY idx_incident_owner (tenant_id, owner_id, status)`|
|`CONSTRAINT:srv_service_incident:idx_incident_project`|`KEY idx_incident_project (tenant_id, project_id, status, occurred_time)`|
|`CONSTRAINT:srv_service_incident_device_relation:idx_incident_device_reverse`|`KEY idx_incident_device_reverse (tenant_id, device_id, incident_id)`|

## V1.7 V1.7十表物理候选（257项）

推荐：**A**。接受十张候选表的全部表、字段、约束、索引和表选项；不扩大到已排除或后置对象。

|Item ID|当前定义|
|---|---|
|`COLUMN:acc_satisfaction_collection_task:applicable_timing_code`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '初验、终验、转包付款或模板配置时点'}`|
|`COLUMN:acc_satisfaction_collection_task:business_purpose_code`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结满意度业务用途'}`|
|`COLUMN:acc_satisfaction_collection_task:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:current_responsible_user_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '当前责任人逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:delivery_scope_sha256`|`{'dataType': 'CHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:delivery_scope_snapshot`|`{'dataType': 'JSON', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '本次付款或验收交付范围冻结快照'}`|
|`COLUMN:acc_satisfaction_collection_task:frozen_threshold`|`{'dataType': 'DECIMAL(10, 4)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:payment_stage_code`|`{'dataType': 'VARCHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '适用转包付款阶段'}`|
|`COLUMN:acc_satisfaction_collection_task:payment_stage_key`|`{'dataType': 'VARCHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': True, 'description': None, 'generatedExpression': "COALESCE(payment_stage_code, '')"}`|
|`COLUMN:acc_satisfaction_collection_task:prior_task_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '整改前序满意度任务逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:project_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '项目逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:remediation_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '整改事实逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:source_context`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:source_object_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '来源对象逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:source_object_type`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:source_object_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结来源业务对象版本'}`|
|`COLUMN:acc_satisfaction_collection_task:state_machine_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:status_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:task_revision_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '整改重收任务序号'}`|
|`COLUMN:acc_satisfaction_collection_task:template_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结问卷模板逻辑引用'}`|
|`COLUMN:acc_satisfaction_collection_task:template_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:update_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:updater`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_collection_task:version`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': '0', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:frozen_question_json`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结题目、必答项与分值规则'}`|
|`COLUMN:acc_satisfaction_questionnaire:frozen_threshold`|`{'dataType': 'DECIMAL(10, 4)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:prior_questionnaire_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '整改前序问卷逻辑引用'}`|
|`COLUMN:acc_satisfaction_questionnaire:questionnaire_revision_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:remediation_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '整改事实逻辑引用'}`|
|`COLUMN:acc_satisfaction_questionnaire:required_question_count`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结必答题数'}`|
|`COLUMN:acc_satisfaction_questionnaire:rule_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '冻结满意度规则版本'}`|
|`COLUMN:acc_satisfaction_questionnaire:source_questionnaire_key`|`{'dataType': 'VARCHAR(128)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '旧问卷实例来源键，不作为目标主键'}`|
|`COLUMN:acc_satisfaction_questionnaire:source_questionnaire_version`|`{'dataType': 'VARCHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:task_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '满意度任务逻辑引用'}`|
|`COLUMN:acc_satisfaction_questionnaire:template_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '问卷模板逻辑引用'}`|
|`COLUMN:acc_satisfaction_questionnaire:template_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_questionnaire:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:answer_json`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:attachment_refs_json`|`{'dataType': 'JSON', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:item_validation_summary`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '逐项答案验证摘要'}`|
|`COLUMN:acc_satisfaction_response:questionnaire_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '问卷实例逻辑引用'}`|
|`COLUMN:acc_satisfaction_response:request_id`|`{'dataType': 'VARCHAR(128)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '提交幂等键'}`|
|`COLUMN:acc_satisfaction_response:required_validation_summary`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '必答项验证摘要'}`|
|`COLUMN:acc_satisfaction_response:response_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:response_valid`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '答卷整体有效性事实'}`|
|`COLUMN:acc_satisfaction_response:signature_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:signature_valid`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '客户签字有效性事实'}`|
|`COLUMN:acc_satisfaction_response:submit_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_response:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:archive_artifact_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': 'ACC-04交付件逻辑引用'}`|
|`COLUMN:acc_satisfaction_result:archive_payload_sha256`|`{'dataType': 'CHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:archive_status_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'ACC-04归档事实状态'}`|
|`COLUMN:acc_satisfaction_result:archive_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:blocking_reason`|`{'dataType': 'VARCHAR(1000)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '未通过时的闭环或付款阻断原因'}`|
|`COLUMN:acc_satisfaction_result:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:decision_rule_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:decision_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:frozen_threshold`|`{'dataType': 'DECIMAL(10, 4)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:passed`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:questionnaire_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '问卷实例逻辑引用'}`|
|`COLUMN:acc_satisfaction_result:required_items_valid`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:response_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '答卷逻辑引用'}`|
|`COLUMN:acc_satisfaction_result:response_valid`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:result_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:score`|`{'dataType': 'DECIMAL(10, 4)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:signature_valid`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:acc_satisfaction_result:validation_summary`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '答卷、签字、必答项和逐项校验摘要'}`|
|`COLUMN:ast_device_component_relation:card_device_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '板卡设备逻辑引用'}`|
|`COLUMN:ast_device_component_relation:card_model_code`|`{'dataType': 'VARCHAR(128)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:card_sn`|`{'dataType': 'VARCHAR(128)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:chassis_device_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '机框设备逻辑引用'}`|
|`COLUMN:ast_device_component_relation:chassis_sn`|`{'dataType': 'VARCHAR(128)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:current_slot_code`|`{'dataType': 'VARCHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': True, 'description': None, 'generatedExpression': 'CASE WHEN effective_to IS NULL THEN slot_code ELSE NULL END'}`|
|`COLUMN:ast_device_component_relation:effective_from`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:effective_to`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:evidence_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:relation_source_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:slot_code`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:ast_device_component_relation:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:archive_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:attachment_refs`|`{'dataType': 'JSON', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:collection_result_refs`|`{'dataType': 'JSON', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': 'INT-12回调或人工上传结果引用'}`|
|`COLUMN:cut_cutover_closure:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:cutover_task_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'CUT-01割接任务逻辑引用'}`|
|`COLUMN:cut_cutover_closure:detail_description`|`{'dataType': 'TEXT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:execution_normal`|`{'dataType': 'TINYINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:legacy_item_text`|`{'dataType': 'TEXT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '遗留项闭环快照文本，不形成独立生命周期'}`|
|`COLUMN:cut_cutover_closure:plan_revision_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'P6引用的已批准CUT-04方案版本'}`|
|`COLUMN:cut_cutover_closure:precheck_normal`|`{'dataType': 'TINYINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:result_code`|`{'dataType': 'VARCHAR(32)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '成功或失败；提交前可空'}`|
|`COLUMN:cut_cutover_closure:rollback_description`|`{'dataType': 'VARCHAR(1000)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:rollback_occurred`|`{'dataType': 'TINYINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:submitted_by`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:submitted_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_closure:test_normal`|`{'dataType': 'TINYINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:arrangement_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '方案版本内保障人员顺序'}`|
|`COLUMN:cut_cutover_support_arrangement:arrival_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:contact_info`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:cutover_task_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'CUT-01割接任务逻辑引用'}`|
|`COLUMN:cut_cutover_support_arrangement:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:internal_user_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '内部人员逻辑引用，外部联系人为空'}`|
|`COLUMN:cut_cutover_support_arrangement:person_name`|`{'dataType': 'VARCHAR(128)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:person_type_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '内部人员或外部联系人'}`|
|`COLUMN:cut_cutover_support_arrangement:plan_revision_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'CUT-04方案版本逻辑引用'}`|
|`COLUMN:cut_cutover_support_arrangement:role_code`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:task_duty`|`{'dataType': 'VARCHAR(1000)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:update_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:cut_cutover_support_arrangement:updater`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:attempt_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:collection_result_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '配置采集结果逻辑引用'}`|
|`COLUMN:imp_configuration_collection_parse_attempt:completed_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:error_summary`|`{'dataType': 'VARCHAR(1000)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:evidence_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '原始配置Log证据引用'}`|
|`COLUMN:imp_configuration_collection_parse_attempt:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:parse_status_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:parser_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:started_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_parse_attempt:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_result:collection_task_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '统一采集任务逻辑引用'}`|
|`COLUMN:imp_configuration_collection_result:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_result:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_result:device_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '设备逻辑引用'}`|
|`COLUMN:imp_configuration_collection_result:device_snapshot`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '采集时设备序列号、型号与类型快照'}`|
|`COLUMN:imp_configuration_collection_result:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_result:operated_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_collection_result:operator_user_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '采集或上传操作人逻辑引用'}`|
|`COLUMN:imp_configuration_collection_result:parser_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '当前结果引用的解析器版本'}`|
|`COLUMN:imp_configuration_collection_result:project_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '项目逻辑引用'}`|
|`COLUMN:imp_configuration_collection_result:project_snapshot`|`{'dataType': 'JSON', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '采集时项目上下文快照'}`|
|`COLUMN:imp_configuration_collection_result:raw_log_file_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '不可覆盖的原始整机Log文件逻辑引用'}`|
|`COLUMN:imp_configuration_collection_result:raw_log_sha256`|`{'dataType': 'CHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '原始整机Log SHA-256'}`|
|`COLUMN:imp_configuration_collection_result:result_type_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '整机或其他可扩展结果类型'}`|
|`COLUMN:imp_configuration_collection_result:result_version_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '不可覆盖的结果版本'}`|
|`COLUMN:imp_configuration_collection_result:script_version`|`{'dataType': 'VARCHAR(64)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '上线采集时冻结的SCH-03脚本版本'}`|
|`COLUMN:imp_configuration_collection_result:source_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '在线采集或手工上传等可扩展来源'}`|
|`COLUMN:imp_configuration_collection_result:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:candidate_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:card_configuration_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '板卡配置提取结果逻辑引用'}`|
|`COLUMN:imp_configuration_component_candidate:card_model_code`|`{'dataType': 'VARCHAR(128)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:card_sn`|`{'dataType': 'VARCHAR(128)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:chassis_sn`|`{'dataType': 'VARCHAR(128)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:creator`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:evidence_ref`|`{'dataType': 'VARCHAR(512)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:match_status_code`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:matched_device_id`|`{'dataType': 'BIGINT', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '已匹配设备逻辑引用'}`|
|`COLUMN:imp_configuration_component_candidate:parse_attempt_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '解析尝试逻辑引用'}`|
|`COLUMN:imp_configuration_component_candidate:parse_revision_no`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '所属独立解析版本'}`|
|`COLUMN:imp_configuration_component_candidate:parser_version`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:slot_code`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`COLUMN:imp_configuration_component_candidate:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': None}`|
|`CONSTRAINT:acc_satisfaction_collection_task:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_collection_task:chk_satisfaction_task_revision`|`CONSTRAINT chk_satisfaction_task_revision CHECK (task_revision_no > 0)`|
|`CONSTRAINT:acc_satisfaction_collection_task:idx_satisfaction_task_owner`|`KEY idx_satisfaction_task_owner (tenant_id, current_responsible_user_id, status_code)`|
|`CONSTRAINT:acc_satisfaction_collection_task:idx_satisfaction_task_source`|`KEY idx_satisfaction_task_source (tenant_id, source_context, source_object_type, source_object_id)`|
|`CONSTRAINT:acc_satisfaction_collection_task:uk_satisfaction_collection_task_tenant_row`|`UNIQUE KEY uk_satisfaction_collection_task_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_collection_task:uk_satisfaction_task_revision`|`UNIQUE KEY uk_satisfaction_task_revision (tenant_id, project_id, source_context, source_object_type, source_object_id, source_object_version, business_purpose_code, applicable_timing_code, payment_stage_key, task_revision_no)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:chk_satisfaction_questionnaire_revision`|`CONSTRAINT chk_satisfaction_questionnaire_revision CHECK (questionnaire_revision_no > 0)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:idx_satisfaction_questionnaire_task`|`KEY idx_satisfaction_questionnaire_task (tenant_id, task_id, create_time)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:uk_satisfaction_questionnaire_revision`|`UNIQUE KEY uk_satisfaction_questionnaire_revision (tenant_id, task_id, questionnaire_revision_no)`|
|`CONSTRAINT:acc_satisfaction_questionnaire:uk_satisfaction_questionnaire_tenant_row`|`UNIQUE KEY uk_satisfaction_questionnaire_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_response:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_response:chk_satisfaction_response_sequence`|`CONSTRAINT chk_satisfaction_response_sequence CHECK (response_no > 0)`|
|`CONSTRAINT:acc_satisfaction_response:idx_satisfaction_response_questionnaire`|`KEY idx_satisfaction_response_questionnaire (tenant_id, questionnaire_id, submit_time)`|
|`CONSTRAINT:acc_satisfaction_response:uk_satisfaction_response_request`|`UNIQUE KEY uk_satisfaction_response_request (tenant_id, questionnaire_id, request_id)`|
|`CONSTRAINT:acc_satisfaction_response:uk_satisfaction_response_sequence`|`UNIQUE KEY uk_satisfaction_response_sequence (tenant_id, questionnaire_id, response_no)`|
|`CONSTRAINT:acc_satisfaction_response:uk_satisfaction_response_tenant_row`|`UNIQUE KEY uk_satisfaction_response_tenant_row (tenant_id, id)`|
|`CONSTRAINT:acc_satisfaction_result:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:acc_satisfaction_result:chk_satisfaction_result_passed`|`CONSTRAINT chk_satisfaction_result_passed CHECK (passed IN (0, 1))`|
|`CONSTRAINT:acc_satisfaction_result:chk_satisfaction_result_sequence`|`CONSTRAINT chk_satisfaction_result_sequence CHECK (result_no > 0)`|
|`CONSTRAINT:acc_satisfaction_result:idx_satisfaction_result_gate`|`KEY idx_satisfaction_result_gate (tenant_id, passed, decision_time)`|
|`CONSTRAINT:acc_satisfaction_result:uk_satisfaction_result_response`|`UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id)`|
|`CONSTRAINT:acc_satisfaction_result:uk_satisfaction_result_sequence`|`UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no)`|
|`CONSTRAINT:acc_satisfaction_result:uk_satisfaction_result_tenant_row`|`UNIQUE KEY uk_satisfaction_result_tenant_row (tenant_id, id)`|
|`CONSTRAINT:ast_device_component_relation:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:ast_device_component_relation:chk_device_component_dates`|`CONSTRAINT chk_device_component_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)`|
|`CONSTRAINT:ast_device_component_relation:idx_device_component_card`|`KEY idx_device_component_card (tenant_id, card_sn, effective_to)`|
|`CONSTRAINT:ast_device_component_relation:idx_device_component_chassis`|`KEY idx_device_component_chassis (tenant_id, chassis_sn, effective_from)`|
|`CONSTRAINT:ast_device_component_relation:uk_device_component_current_slot`|`UNIQUE KEY uk_device_component_current_slot (tenant_id, chassis_device_id, current_slot_code)`|
|`CONSTRAINT:ast_device_component_relation:uk_device_component_relation_tenant_row`|`UNIQUE KEY uk_device_component_relation_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cut_cutover_closure:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_execution`|`CONSTRAINT chk_cutover_closure_execution CHECK (execution_normal IS NULL OR execution_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_precheck`|`CONSTRAINT chk_cutover_closure_precheck CHECK (precheck_normal IS NULL OR precheck_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_rollback`|`CONSTRAINT chk_cutover_closure_rollback CHECK (rollback_occurred IS NULL OR rollback_occurred IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_submit`|`CONSTRAINT chk_cutover_closure_submit CHECK ( submitted_time IS NULL OR (submitted_by IS NOT NULL AND archive_time IS NOT NULL AND result_code IS NOT NULL) )`|
|`CONSTRAINT:cut_cutover_closure:chk_cutover_closure_test`|`CONSTRAINT chk_cutover_closure_test CHECK (test_normal IS NULL OR test_normal IN (0, 1))`|
|`CONSTRAINT:cut_cutover_closure:idx_cutover_closure_result`|`KEY idx_cutover_closure_result (tenant_id, result_code, archive_time)`|
|`CONSTRAINT:cut_cutover_closure:uk_cutover_closure_task`|`UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id)`|
|`CONSTRAINT:cut_cutover_closure:uk_cutover_closure_tenant_row`|`UNIQUE KEY uk_cutover_closure_tenant_row (tenant_id, id)`|
|`CONSTRAINT:cut_cutover_support_arrangement:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:cut_cutover_support_arrangement:chk_cutover_support_arrangement_no`|`CONSTRAINT chk_cutover_support_arrangement_no CHECK (arrangement_no > 0)`|
|`CONSTRAINT:cut_cutover_support_arrangement:idx_cutover_support_arrangement_task`|`KEY idx_cutover_support_arrangement_task (tenant_id, cutover_task_id, plan_revision_id)`|
|`CONSTRAINT:cut_cutover_support_arrangement:uk_cutover_support_arrangement_no`|`UNIQUE KEY uk_cutover_support_arrangement_no (tenant_id, plan_revision_id, arrangement_no)`|
|`CONSTRAINT:cut_cutover_support_arrangement:uk_cutover_support_arrangement_tenant_row`|`UNIQUE KEY uk_cutover_support_arrangement_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:chk_configuration_parse_attempt_no`|`CONSTRAINT chk_configuration_parse_attempt_no CHECK (attempt_no > 0)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:chk_configuration_parse_attempt_time`|`CONSTRAINT chk_configuration_parse_attempt_time CHECK (completed_time IS NULL OR completed_time >= started_time)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:idx_configuration_parse_attempt_result`|`KEY idx_configuration_parse_attempt_result (tenant_id, collection_result_id, started_time)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:uk_configuration_parse_attempt`|`UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)`|
|`CONSTRAINT:imp_configuration_collection_parse_attempt:uk_configuration_parse_attempt_tenant_row`|`UNIQUE KEY uk_configuration_parse_attempt_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_collection_result:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_collection_result:chk_configuration_collection_result_version`|`CONSTRAINT chk_configuration_collection_result_version CHECK (result_version_no > 0)`|
|`CONSTRAINT:imp_configuration_collection_result:idx_configuration_collection_result_device`|`KEY idx_configuration_collection_result_device (tenant_id, project_id, device_id, operated_time)`|
|`CONSTRAINT:imp_configuration_collection_result:idx_configuration_collection_result_hash`|`KEY idx_configuration_collection_result_hash (tenant_id, raw_log_sha256)`|
|`CONSTRAINT:imp_configuration_collection_result:uk_configuration_collection_result`|`UNIQUE KEY uk_configuration_collection_result (tenant_id, collection_task_id, result_type_code, result_version_no)`|
|`CONSTRAINT:imp_configuration_collection_result:uk_configuration_collection_result_tenant_row`|`UNIQUE KEY uk_configuration_collection_result_tenant_row (tenant_id, id)`|
|`CONSTRAINT:imp_configuration_component_candidate:PRIMARY`|`PRIMARY KEY (id)`|
|`CONSTRAINT:imp_configuration_component_candidate:chk_configuration_component_candidate_no`|`CONSTRAINT chk_configuration_component_candidate_no CHECK (candidate_no > 0)`|
|`CONSTRAINT:imp_configuration_component_candidate:idx_configuration_component_candidate_match`|`KEY idx_configuration_component_candidate_match (tenant_id, match_status_code, create_time)`|
|`CONSTRAINT:imp_configuration_component_candidate:idx_configuration_component_candidate_sn`|`KEY idx_configuration_component_candidate_sn (tenant_id, chassis_sn, slot_code, card_sn)`|
|`CONSTRAINT:imp_configuration_component_candidate:uk_configuration_component_candidate`|`UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no)`|
|`CONSTRAINT:imp_configuration_component_candidate:uk_configuration_component_candidate_tenant_row`|`UNIQUE KEY uk_configuration_component_candidate_tenant_row (tenant_id, id)`|
|`TABLE:acc_satisfaction_collection_task`|`True`|
|`TABLE:acc_satisfaction_questionnaire`|`True`|
|`TABLE:acc_satisfaction_response`|`True`|
|`TABLE:acc_satisfaction_result`|`True`|
|`TABLE:ast_device_component_relation`|`True`|
|`TABLE:cut_cutover_closure`|`True`|
|`TABLE:cut_cutover_support_arrangement`|`True`|
|`TABLE:imp_configuration_collection_parse_attempt`|`True`|
|`TABLE:imp_configuration_collection_result`|`True`|
|`TABLE:imp_configuration_component_candidate`|`True`|
|`TABLE_OPTION:acc_satisfaction_collection_task`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目成员承办的满意度收集领域任务'`|
|`TABLE_OPTION:acc_satisfaction_questionnaire`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的满意度问卷冻结实例'`|
|`TABLE_OPTION:acc_satisfaction_response`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的客户满意度答卷、签字和附件事实'`|
|`TABLE_OPTION:acc_satisfaction_result`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的满意度评分、阈值与达标判定事实'`|
|`TABLE_OPTION:ast_device_component_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '机框、槽位与板卡的当前及历史关系'`|
|`TABLE_OPTION:cut_cutover_closure`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-06 P6轻量闭环与归档事实，不保存逐步骤执行或稳定观察'`|
|`TABLE_OPTION:cut_cutover_support_arrangement`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CUT-04方案从属保障人员安排，不具有工单状态或责任区间'`|
|`TABLE_OPTION:imp_configuration_collection_parse_attempt`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置采集结果解析尝试，不覆盖原始配置Log'`|
|`TABLE_OPTION:imp_configuration_collection_result`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '不可覆盖的配置采集结果、整机Log及项目设备快照'`|
|`TABLE_OPTION:imp_configuration_component_candidate`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置Log解析形成的板卡候选及待匹配证据'`|

## Q09 非V1.7表选项（50项）

推荐：**A**。统一采用InnoDB、utf8mb4、utf8mb4_0900_ai_ci；COMMENT仅描述对象语义，不作为业务规则。

|Item ID|当前定义|
|---|---|
|`TABLE_OPTION:acc_deliverable_template`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交付件类型和模板配置'`|
|`TABLE_OPTION:acc_project_deliverable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目交付件实例及完成状态'`|
|`TABLE_OPTION:ana_project_delivery_summary`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '可重建的项目合同、订单、发货和SN汇总读模型'`|
|`TABLE_OPTION:ast_device_configuration`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备分阶段配置主记录'`|
|`TABLE_OPTION:ast_device_configuration_feature`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置启用特性明细'`|
|`TABLE_OPTION:ast_device_configuration_service`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备配置运行服务明细'`|
|`TABLE_OPTION:ast_device_project_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN到项目的归属及转移历史'`|
|`TABLE_OPTION:ast_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同维度主附加SN、RMA替换等设备关系'`|
|`TABLE_OPTION:ast_device_shipment_event`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备发货、退回、返还和再次发放的物流生命周期事件'`|
|`TABLE_OPTION:ast_device_sn`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备SN主档，不承载重复发货事件'`|
|`TABLE_OPTION:ast_device_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备组件版本及阶段历史'`|
|`TABLE_OPTION:ast_network_topology`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目网络拓扑版本'`|
|`TABLE_OPTION:ast_network_topology_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '拓扑节点与设备关系'`|
|`TABLE_OPTION:ast_product`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品主档，安服属性由产品配置判定'`|
|`TABLE_OPTION:ast_product_release`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品版本发布与支持周期'`|
|`TABLE_OPTION:com_contract`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同主档，以所属公司和合同号为业务唯一键'`|
|`TABLE_OPTION:com_contract_receivable`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SAP合同回款来源记录，保留公司待解析和一号多行证据'`|
|`TABLE_OPTION:com_crm_execution_config`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM已获得的执行单产品配置，仅作辅助证据'`|
|`TABLE_OPTION:com_crm_execution_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM执行单辅助主档，安服仅保存正向证据'`|
|`TABLE_OPTION:com_delivery_scope`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目对ERP订单行的权威实施范围'`|
|`TABLE_OPTION:com_delivery_scope_detail`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '交付范围按地点、产品或设备类型及批次拆分的明细'`|
|`TABLE_OPTION:com_execution_order_merge_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊业务合并下单批次'`|
|`TABLE_OPTION:com_execution_order_merge_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '特殊合并下单执行单成员，不限制成员数量'`|
|`TABLE_OPTION:com_order_change_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '改单、拆分、替代和退货订单血缘'`|
|`TABLE_OPTION:com_order_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '合同与ERP订单N:N关系'`|
|`TABLE_OPTION:com_order_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单与CRM执行单辅助关系'`|
|`TABLE_OPTION:com_order_line_execution_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP订单行与CRM执行单辅助关系'`|
|`TABLE_OPTION:com_project_contract_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目与合同直接N:N关系'`|
|`TABLE_OPTION:com_sales_order`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单主档'`|
|`TABLE_OPTION:com_sales_order_line`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'ERP销售订单行及数量快照'`|
|`TABLE_OPTION:com_shipment_contract_reference`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货记录的合同归属，不作为合同主档'`|
|`TABLE_OPTION:com_shipment_package`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发货装箱单主档'`|
|`TABLE_OPTION:cus_customer`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户主档'`|
|`TABLE_OPTION:cus_customer_contact`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户联系人'`|
|`TABLE_OPTION:cus_market_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'CRM同步的客户市场行业划分组合目录'`|
|`TABLE_OPTION:plt_business_document`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档元数据'`|
|`TABLE_OPTION:plt_document_version`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务文档不可变版本'`|
|`TABLE_OPTION:plt_external_key_mapping`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旧主键到新主键的可追溯映射'`|
|`TABLE_OPTION:plt_migration_issue`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移缺失、重复、多义映射和人工解决记录'`|
|`TABLE_OPTION:plt_migration_source_record`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移批次逐源行的完整原值证据，不因目标归并或去重而覆盖'`|
|`TABLE_OPTION:plt_sync_batch`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '一次性迁移及只读同步批次'`|
|`TABLE_OPTION:proj_project`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目主档及非固定层级项目树'`|
|`TABLE_OPTION:proj_project_company_department_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目业务角色下的公司与部门组合关系，保留配对但不建立全局主数据从属关系'`|
|`TABLE_OPTION:proj_project_member_assignment`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目成员、角色及有效期'`|
|`TABLE_OPTION:proj_project_party`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目参与方，按合同客户、最终用户、代理商、服务商等角色保存'`|
|`TABLE_OPTION:proj_project_portfolio`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合，不改变项目父子层级'`|
|`TABLE_OPTION:proj_project_portfolio_member`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目组合成员'`|
|`TABLE_OPTION:proj_project_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '扩容、续采、改造等非树项目关系'`|
|`TABLE_OPTION:srv_service_incident`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障及服务事件主档'`|
|`TABLE_OPTION:srv_service_incident_device_relation`|`ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '故障与受影响设备多对多关系'`|

## Q10 来源幂等唯一键（14项）

推荐：**A**。按租户、来源系统和来源业务键防止重复同步；来源键按不透明值精确比较。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:ast_device_project_assignment:uk_device_assignment_source`|`UNIQUE KEY uk_device_assignment_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:ast_device_relation:uk_device_relation_source`|`UNIQUE KEY uk_device_relation_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:ast_device_shipment_event:uk_shipment_event_source`|`UNIQUE KEY uk_shipment_event_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:com_contract:uk_contract_master_source`|`UNIQUE KEY uk_contract_master_source ( tenant_id, master_source_system, master_source_record_key )`|
|`CONSTRAINT:com_contract_receivable:uk_contract_receivable_source`|`UNIQUE KEY uk_contract_receivable_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:com_crm_execution_config:uk_crm_execution_config`|`UNIQUE KEY uk_crm_execution_config ( tenant_id, config_source, source_config_key )`|
|`CONSTRAINT:com_execution_order_merge_batch:uk_execution_merge_batch`|`UNIQUE KEY uk_execution_merge_batch ( tenant_id, source_system, source_merge_key )`|
|`CONSTRAINT:com_execution_order_merge_member:uk_execution_merge_member_source`|`UNIQUE KEY uk_execution_merge_member_source ( tenant_id, merge_batch_id, source_record_key )`|
|`CONSTRAINT:com_shipment_contract_reference:uk_shipment_contract_ref_source`|`UNIQUE KEY uk_shipment_contract_ref_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:com_shipment_package:uk_shipment_package_source`|`UNIQUE KEY uk_shipment_package_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:cus_market_relation:uk_market_relation_source`|`UNIQUE KEY uk_market_relation_source ( tenant_id, source_system, source_record_key )`|
|`CONSTRAINT:plt_migration_issue:uk_migration_issue_source`|`UNIQUE KEY uk_migration_issue_source ( tenant_id, batch_id, source_table, source_pk, issue_type )`|
|`CONSTRAINT:plt_migration_source_record:uk_migration_source_record`|`UNIQUE KEY uk_migration_source_record ( tenant_id, batch_id, source_system, source_table, source_pk )`|
|`CONSTRAINT:proj_project_party:uk_project_party_source`|`UNIQUE KEY uk_project_party_source ( tenant_id, source_system, source_table, source_record_key, party_role )`|

## Q11 关系粒度唯一键（13项）

推荐：**A**。仅阻止同一关系粒度重复，不额外限制项目、订单、设备或参与方数量。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:ast_device_configuration_feature:uk_device_configuration_feature`|`UNIQUE KEY uk_device_configuration_feature ( tenant_id, configuration_id, feature_code )`|
|`CONSTRAINT:ast_device_configuration_service:uk_device_configuration_service`|`UNIQUE KEY uk_device_configuration_service ( tenant_id, configuration_id, service_code )`|
|`CONSTRAINT:ast_network_topology_device_relation:uk_topology_device`|`UNIQUE KEY uk_topology_device (tenant_id, topology_id, device_id)`|
|`CONSTRAINT:com_order_change_relation:uk_order_change`|`UNIQUE KEY uk_order_change ( tenant_id, source_order_id, target_order_id, relation_type )`|
|`CONSTRAINT:com_order_contract_relation:uk_order_contract`|`UNIQUE KEY uk_order_contract (tenant_id, order_id, contract_id)`|
|`CONSTRAINT:com_order_execution_relation:uk_order_execution`|`UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)`|
|`CONSTRAINT:com_order_line_execution_relation:uk_order_line_execution`|`UNIQUE KEY uk_order_line_execution (tenant_id, order_line_id, execution_id)`|
|`CONSTRAINT:com_project_contract_relation:uk_project_contract`|`UNIQUE KEY uk_project_contract ( tenant_id, project_id, contract_id, relation_role )`|
|`CONSTRAINT:proj_project_company_department_relation:uk_project_company_department_role`|`UNIQUE KEY uk_project_company_department_role ( tenant_id, project_id, company_code, department_code, relation_role, effective_from )`|
|`CONSTRAINT:proj_project_member_assignment:uk_project_member_role`|`UNIQUE KEY uk_project_member_role ( tenant_id, project_id, user_id, member_role, effective_from )`|
|`CONSTRAINT:proj_project_portfolio_member:uk_portfolio_project`|`UNIQUE KEY uk_portfolio_project ( tenant_id, portfolio_id, project_id, member_source )`|
|`CONSTRAINT:proj_project_relation:uk_project_relation`|`UNIQUE KEY uk_project_relation ( tenant_id, source_project_id, target_project_id, relation_type )`|
|`CONSTRAINT:srv_service_incident_device_relation:uk_incident_device`|`UNIQUE KEY uk_incident_device (tenant_id, incident_id, device_id)`|

## Q12 业务身份与版本序号唯一键（16项）

推荐：**A**。业务编码、单号、SN、文档版本和产品版本在声明粒度内唯一且不复用。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:acc_deliverable_template:uk_deliverable_template`|`UNIQUE KEY uk_deliverable_template (tenant_id, template_code)`|
|`CONSTRAINT:ast_device_sn:uk_device_sn`|`UNIQUE KEY uk_device_sn (tenant_id, sn)`|
|`CONSTRAINT:ast_product:uk_product_code`|`UNIQUE KEY uk_product_code (tenant_id, product_code)`|
|`CONSTRAINT:ast_product_release:uk_product_release`|`UNIQUE KEY uk_product_release ( tenant_id, product_id, release_version, release_type )`|
|`CONSTRAINT:com_contract:uk_contract_business`|`UNIQUE KEY uk_contract_business ( tenant_id, company_code, contract_no )`|
|`CONSTRAINT:com_crm_execution_order:uk_crm_execution`|`UNIQUE KEY uk_crm_execution ( tenant_id, source_system, execution_no )`|
|`CONSTRAINT:com_sales_order:uk_sales_order_business`|`UNIQUE KEY uk_sales_order_business ( tenant_id, source_system, company_code, order_type, order_no )`|
|`CONSTRAINT:com_sales_order_line:uk_sales_order_line`|`UNIQUE KEY uk_sales_order_line (tenant_id, order_id, line_no)`|
|`CONSTRAINT:com_shipment_package:uk_shipment_package_no`|`UNIQUE KEY uk_shipment_package_no ( tenant_id, source_system, package_no )`|
|`CONSTRAINT:cus_customer:uk_customer_code`|`UNIQUE KEY uk_customer_code (tenant_id, customer_code)`|
|`CONSTRAINT:cus_market_relation:uk_market_relation_business`|`UNIQUE KEY uk_market_relation_business ( tenant_id, market_code, system_code, expend_code, industry_code )`|
|`CONSTRAINT:plt_business_document:uk_business_document_code`|`UNIQUE KEY uk_business_document_code (tenant_id, document_code)`|
|`CONSTRAINT:plt_document_version:uk_document_version`|`UNIQUE KEY uk_document_version (tenant_id, document_id, version_no)`|
|`CONSTRAINT:plt_sync_batch:uk_sync_batch_no`|`UNIQUE KEY uk_sync_batch_no (tenant_id, batch_no)`|
|`CONSTRAINT:proj_project_portfolio:uk_portfolio_code`|`UNIQUE KEY uk_portfolio_code (tenant_id, portfolio_code)`|
|`CONSTRAINT:srv_service_incident:uk_service_incident_no`|`UNIQUE KEY uk_service_incident_no (tenant_id, incident_no)`|

## Q13 跨字段一致性CHECK（2项）

推荐：**A**。保留设备缓存一致性及公司/部门成对填写检查，不固化可扩展状态值。

|Item ID|当前定义|
|---|---|
|`CONSTRAINT:ast_device_sn:chk_device_secondary_cache`|`CONSTRAINT chk_device_secondary_cache CHECK ( secondary_sn IS NOT NULL OR secondary_item IS NULL )`|
|`CONSTRAINT:proj_project_company_department_relation:chk_project_company_department_pair`|`CONSTRAINT chk_project_company_department_pair CHECK (department_id IS NULL OR department_code IS NOT NULL)`|

## Q14 市场目录审计字段与RMA投影（13项）

推荐：**A**。保留基础平台审计/租户/来源字段；rma_marked仅作兼容查询投影，不推导业务动作或数量方向。

|Item ID|当前定义|
|---|---|
|`COLUMN:ast_device_shipment_event:rma_marked`|`{'dataType': 'TINYINT', 'nullable': True, 'defaultValue': None, 'generated': True, 'description': '由RMA编号计算的标志：0非RMA类事件，1为RMA或借转类事件', 'generatedExpression': "CASE WHEN rma_no IS NULL OR TRIM(rma_no) = '' OR LOWER(TRIM(rma_no)) = 'null' THEN 0 ELSE 1 END"}`|
|`COLUMN:cus_market_relation:create_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': '创建时间'}`|
|`COLUMN:cus_market_relation:creator`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': "''", 'generated': False, 'description': '创建人'}`|
|`COLUMN:cus_market_relation:deleted`|`{'dataType': 'TINYINT', 'nullable': False, 'defaultValue': '0', 'generated': False, 'description': '删除标志：0否，1是'}`|
|`COLUMN:cus_market_relation:id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '主键ID'}`|
|`COLUMN:cus_market_relation:source_record_key`|`{'dataType': 'VARCHAR(128)', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': 'CRM市场行业组合的稳定来源键'}`|
|`COLUMN:cus_market_relation:source_sync_time`|`{'dataType': 'DATETIME(3)', 'nullable': True, 'defaultValue': None, 'generated': False, 'description': '来源记录最近一次成功同步时间'}`|
|`COLUMN:cus_market_relation:source_system`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': "'CRM'", 'generated': False, 'description': '权威来源系统编码'}`|
|`COLUMN:cus_market_relation:status`|`{'dataType': 'VARCHAR(32)', 'nullable': False, 'defaultValue': "'ENABLED'", 'generated': False, 'description': '状态'}`|
|`COLUMN:cus_market_relation:tenant_id`|`{'dataType': 'BIGINT', 'nullable': False, 'defaultValue': None, 'generated': False, 'description': '租户ID'}`|
|`COLUMN:cus_market_relation:update_time`|`{'dataType': 'DATETIME(3)', 'nullable': False, 'defaultValue': 'CURRENT_TIMESTAMP(3)', 'generated': False, 'description': '更新时间'}`|
|`COLUMN:cus_market_relation:updater`|`{'dataType': 'VARCHAR(64)', 'nullable': False, 'defaultValue': "''", 'generated': False, 'description': '更新人'}`|
|`COLUMN:cus_market_relation:version`|`{'dataType': 'INT UNSIGNED', 'nullable': False, 'defaultValue': '0', 'generated': False, 'description': '乐观锁版本'}`|
