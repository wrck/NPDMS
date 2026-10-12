# DPPMS V4 结构逐项裁决清单

状态：待需求方按表一并确认全部字段，逐字段保留结论。此清单是审查资料，不是建表或增列批准。

最新提交方式：[G001：proj_project全部37新增列](2026-10-09-dppms-proj-project-table-decision.md)。C001单列问题已撤回，不再逐列零散询问；各表分组见机器清单tableReviewGroups。未明项和字段例外保持待处理。

覆盖 V412–V415：9 张既有表新增 114 列，14 张新表内含 373 列，合计 501 个独立裁决编号（含 14 个表模型）。其中 C001–C004 已有具体清理建议；其余 497 项目前仅完成结构/原字段登记，尚需完成各自语义和既有实体复核，不能要求用户批准不完整设计。

按表材料包含该表全部字段，用户在同一批内确认每列处理及例外；仅批准建表模型、不涉及具体字段时，不能默认批准所有列。已有业务范围批准继续有效，不能代替物理结构批准。现有数据保留，未决项不增改或清理。V417 四个重复列按先前明确批准经 V418 清理，列为历史，不重开。后续 V5 或其他新增提案须追加独立编号后逐项提交，不默认放行。V411 来源证据表不是业务承接实体，此处不把它计入 V4 业务新增表。

来源：[114 列及 14 表物理审查](2026-10-09-dppms-v4-physical-change-review.json)、[799 原字段及映射审查](2026-10-09-dppms-v4-source-field-correction.json)、[完整逐项机器清单](2026-10-09-dppms-individual-structure-decisions.json)。下列类型/约束直接取既有迁移 SQL，说明的是已提出或已落库定义，不是本次批准的新定义。

## C001：市场名称重复来源列

- 待裁决结构：`proj_project.source_market_name`，V414 新增，定义 `source_market_name VARCHAR(255) NULL`。
- 原字段：`pm_project_header.column004`；正式承接：`proj_project.market_name`，沿用 PROJ 创建 Owner 及 COM 名称 DIRECT/text 转换；代码单独保存。
- 证据：旧正常写链确认保存名称；前次只读核查全租户 136 行（包含删除行检查范围），本列 10 个非 NULL 原文与正式字段精确一致，126 个来源 NULL，数据库索引/FK/视图/触发器/check 依赖为 0。此为已保存的核查结果，执行前必须复查。
- 建议：撤回本列重复读写；在消费者切换并通过执行前全表相等/依赖核查后，独立前向迁移清理本列。
- 执行条件：仅这一列；不补写或覆盖正式名称；先撤回重复依赖并切换消费者。原 NULL 继续 NULL，绑定不明确、原值不一致或任何新差异时阻断。原来源/快照/回执/审计及执行过的 V414 保持。
- 当前裁决：未收到，已纳入G001按表提交，不再单独询问。未进行代码撤回或 DROP。

## C002：系统名称重复来源列

- 待裁决结构：`proj_project.source_system_name`，V414 新增，定义 `source_system_name VARCHAR(255) NULL`。
- 原字段：`pm_project_header.column005`；正式承接：`proj_project.system_name`，沿用 PROJ 创建 Owner 及 COM 名称 DIRECT/text 转换；代码单独保存。
- 证据：旧正常写链确认保存名称；前次只读核查全租户 136 行（包含删除行检查范围），本列 10 个非 NULL 原文与正式字段精确一致，126 个来源 NULL，数据库索引/FK/视图/触发器/check 依赖为 0。此为已保存的核查结果，执行前必须复查。
- 建议：撤回本列重复读写；在消费者切换并通过执行前全表相等/依赖核查后，独立前向迁移清理本列。
- 执行条件：仅这一列；不补写或覆盖正式名称；先撤回重复依赖并切换消费者。原 NULL 继续 NULL，绑定不明确、原值不一致或任何新差异时阻断。原来源/快照/回执/审计及执行过的 V414 保持。
- 当前裁决：未收到，已纳入G001按表提交，不再单独询问。未进行代码撤回或 DROP。

## C003：拓展名称重复来源列

- 待裁决结构：`proj_project.source_expend_name`，V414 新增，定义 `source_expend_name VARCHAR(255) NULL`。
- 原字段：`pm_project_header.column006`；正式承接：`proj_project.expend_name`，沿用 PROJ 创建 Owner 及 COM 名称 DIRECT/text 转换；代码单独保存。
- 证据：旧正常写链确认保存名称；前次只读核查全租户 136 行（包含删除行检查范围），本列 10 个非 NULL 原文与正式字段精确一致，126 个来源 NULL，数据库索引/FK/视图/触发器/check 依赖为 0。此为已保存的核查结果，执行前必须复查。
- 建议：撤回本列重复读写；在消费者切换并通过执行前全表相等/依赖核查后，独立前向迁移清理本列。
- 执行条件：仅这一列；不补写或覆盖正式名称；先撤回重复依赖并切换消费者。原 NULL 继续 NULL，绑定不明确、原值不一致或任何新差异时阻断。原来源/快照/回执/审计及执行过的 V414 保持。
- 当前裁决：未收到，已纳入G001按表提交，不再单独询问。未进行代码撤回或 DROP。

## C004：行业名称重复来源列

- 待裁决结构：`proj_project.source_industry_name`，V414 新增，定义 `source_industry_name VARCHAR(255) NULL`。
- 原字段：`pm_project_header.column007`；正式承接：`proj_project.industry_name`，沿用 PROJ 创建 Owner 及 COM 名称 DIRECT/text 转换；代码单独保存。
- 证据：旧正常写链确认保存名称；前次只读核查全租户 136 行（包含删除行检查范围），本列 10 个非 NULL 原文与正式字段精确一致，126 个来源 NULL，数据库索引/FK/视图/触发器/check 依赖为 0。此为已保存的核查结果，执行前必须复查。
- 建议：撤回本列重复读写；在消费者切换并通过执行前全表相等/依赖核查后，独立前向迁移清理本列。
- 执行条件：仅这一列；不补写或覆盖正式名称；先撤回重复依赖并切换消费者。原 NULL 继续 NULL，绑定不明确、原值不一致或任何新差异时阻断。原来源/快照/回执/审计及执行过的 V414 保持。
- 当前裁决：未收到，已纳入G001按表提交，不再单独询问。未进行代码撤回或 DROP。

## 既有表新增列：C001–C114

每行独立裁决。表中既有承接点是审查候选，除上述四项外不能据此视为可直接清理。详细原字段类型、Owner、原因和证据在机器清单同号项。

| 编号 | 目标列 | 原字段 | 既有承接点/Owner审查 | 当前状态 |
|---|---|---|---|---|
| C001 | `proj_project.source_market_name` | pm_project_header.column004 | proj_project.market_name; PROJ existing creation Owner; COM Name DIRECT/text conversion | 待单项裁决 |
| C002 | `proj_project.source_system_name` | pm_project_header.column005 | proj_project.system_name; PROJ existing creation Owner; COM Name DIRECT/text conversion | 待单项裁决 |
| C003 | `proj_project.source_expend_name` | pm_project_header.column006 | proj_project.expend_name; PROJ existing creation Owner; COM Name DIRECT/text conversion | 待单项裁决 |
| C004 | `proj_project.source_industry_name` | pm_project_header.column007 | proj_project.industry_name; PROJ existing creation Owner; COM Name DIRECT/text conversion | 待单项裁决 |
| C005 | `ast_device_network_version_event.custom_info` | 待核实 | ast_device_network_version_event.conp_version; ast_device_network_version_event.conp_type; ast_device_network_version_event.conp_series; ast_device_network_version_event.conp_mark; ast_device_network_version_event.boot_version; ast_device_network_version_event.cpld_version; ast_device_network_version_event.pcb_version; ast_device_network_version_event.customized | 已登记，继续语义复核 |
| C006 | `ast_device_network_version_event.source_data_state` | 待核实 | ast_device_network_version_event.revoked; ast_device_network_version_event.mapping_status; ast_device_network_version_event.source_version | 已登记，继续语义复核 |
| C007 | `ast_device_network_version_event.source_log_key` | 待核实 | ast_device_network_version_event.source_event_key; ast_device_network_version_event.source_device_key | 已登记，继续语义复核 |
| C008 | `ast_device_warranty_record.agent_name` | view_warranty.agentName | proj_project_party.party_role; proj_project_party.party_code; proj_project_party.party_name; proj_project_party.effective_from; proj_project_party.effective_to | 已登记，继续语义复核 |
| C009 | `ast_device_warranty_record.combined_device_sn` | view_warranty.comBarCode | ast_device_shipment.internal_serial_no | 已登记，继续语义复核 |
| C010 | `ast_device_warranty_record.contract_type` | view_warranty.contract_type | com_contract.contract_type | 已登记，继续语义复核 |
| C011 | `ast_device_warranty_record.contract_type_name` | view_warranty.contract_type_name | com_contract.contract_type | 已登记，继续语义复核 |
| C012 | `ast_device_warranty_record.customer_name` | view_warranty.customer_name | com_contract.customer_name | 已登记，继续语义复核 |
| C013 | `ast_device_warranty_record.final_customer_name` | view_warranty.finalCustomerName | proj_project_party.party_role; proj_project_party.party_code; proj_project_party.party_name; proj_project_party.effective_from; proj_project_party.effective_to | 已登记，继续语义复核 |
| C014 | `ast_device_warranty_record.grade_name` | view_warranty.gradeName | ast_device_warranty_record.warranty_grade | 已登记，继续语义复核 |
| C015 | `ast_device_warranty_record.market_code` | view_warranty.marketCode | com_contract.market_code | 已登记，继续语义复核 |
| C016 | `ast_device_warranty_record.market_name` | view_warranty.marketName | com_contract.market_name | 已登记，继续语义复核 |
| C017 | `ast_device_warranty_record.office_code` | view_warranty.office_code | com_contract.department_code | 已登记，继续语义复核 |
| C018 | `ast_device_warranty_record.office_name` | view_warranty.office_name | com_contract.department_name | 已登记，继续语义复核 |
| C019 | `ast_device_warranty_record.original_remaining_days_at_source` | view_warranty.old_diff | AST DppmsAssetEntityImportApi.importEntities/importWarranty; related AST/COM/PROJ public Owner APIs | 已登记，继续语义复核 |
| C020 | `ast_device_warranty_record.original_warranty_end_time` | view_warranty.old_warrantyEndTime | ast_device_warranty_record.warranty_start_date; ast_device_warranty_record.warranty_end_date; ast_device_warranty_record.extended | 已登记，继续语义复核 |
| C021 | `ast_device_warranty_record.original_warranty_start_time` | view_warranty.old_warrantyStartTime | ast_device_warranty_record.warranty_start_date; ast_device_warranty_record.warranty_end_date; ast_device_warranty_record.extended | 已登记，继续语义复核 |
| C022 | `ast_device_warranty_record.product_code` | view_warranty.item | ast_device_shipment.product_code | 已登记，继续语义复核 |
| C023 | `ast_device_warranty_record.product_description` | view_warranty.describe_ | ast_product_official_info.product_name; ast_product_official_info.product_model; ast_product_official_info.product_desc | 已登记，继续语义复核 |
| C024 | `ast_device_warranty_record.product_name` | view_warranty.itemName | ast_product_official_info.product_name; ast_product_official_info.product_model; ast_product_official_info.product_desc | 已登记，继续语义复核 |
| C025 | `ast_device_warranty_record.project_name` | view_warranty.project_name | com_contract.project_name | 已登记，继续语义复核 |
| C026 | `ast_device_warranty_record.remaining_days_at_source` | view_warranty.diff | AST DppmsAssetEntityImportApi.importEntities/importWarranty; related AST/COM/PROJ public Owner APIs | 已登记，继续语义复核 |
| C027 | `ast_device_warranty_record.secondary_item` | view_warranty.item2 | ast_device_shipment.secondary_item | 已登记，继续语义复核 |
| C028 | `ast_device_warranty_record.secondary_product_description` | view_warranty.describe_2 | ast_product_official_info.product_name; ast_product_official_info.product_model; ast_product_official_info.product_desc | 已登记，继续语义复核 |
| C029 | `ast_device_warranty_record.secondary_product_name` | view_warranty.itemName2 | ast_product_official_info.product_name; ast_product_official_info.product_model; ast_product_official_info.product_desc | 已登记，继续语义复核 |
| C030 | `ast_device_warranty_record.secondary_sn` | view_warranty.barcode2 | ast_device_shipment.secondary_sn | 已登记，继续语义复核 |
| C031 | `ast_device_warranty_record.shipment_time` | view_warranty.packdate | ast_device_shipment.shipment_time | 已登记，继续语义复核 |
| C032 | `ast_device_warranty_record.source_shipment_key` | view_warranty.id | ast_device_shipment.source_key; ast_device_shipment.device_sn | 已登记，继续语义复核 |
| C033 | `ast_device_warranty_record.source_sync_time` | view_warranty.syncTime | AST DppmsAssetEntityImportApi.importEntities/importWarranty; related AST/COM/PROJ public Owner APIs | 已登记，继续语义复核 |
| C034 | `ast_device_warranty_record.source_warranty_end_time` | view_warranty.warrantyEndTime | ast_device_warranty_record.warranty_end_date | 已登记，继续语义复核 |
| C035 | `ast_device_warranty_record.source_warranty_start_time` | view_warranty.warrantyStartTime | ast_device_warranty_record.warranty_start_date | 已登记，继续语义复核 |
| C036 | `ast_device_warranty_record.system_code` | view_warranty.systemId | com_contract.system_code | 已登记，继续语义复核 |
| C037 | `ast_device_warranty_record.system_name` | view_warranty.systemName | com_contract.system_name | 已登记，继续语义复核 |
| C038 | `ast_device_warranty_record.warranty_flag` | view_warranty.warranty | ast_device_warranty_record.extended | 已登记，继续语义复核 |
| C039 | `com_sales_order.legacy_record_id` | pm_order_data_from_erp.id | INT source binding / external mapping; canonical source_record_key or execution source_object_id | 已登记，继续语义复核 |
| C040 | `com_sales_order.source_company_code` | pm_order_data_from_erp.compCode | company_code via approved01→001/02→002/03→003/04→004 | 已登记，继续语义复核 |
| C041 | `com_sales_order.source_erp_system` | pm_order_data_from_erp.source | source_record_key DPPMS\|provider\|company\|type\|order[\|line] | 已登记，继续语义复核 |
| C042 | `com_sales_order_line.legacy_record_id` | pm_order_line_from_erp.id | INT source binding / external mapping; canonical source_record_key or execution source_object_id | 已登记，继续语义复核 |
| C043 | `com_sales_order_line.source_company_code` | pm_order_line_from_erp.compCode | company_code via approved01→001/02→002/03→003/04→004 | 已登记，继续语义复核 |
| C044 | `com_sales_order_line.source_erp_system` | pm_order_line_from_erp.source | source_record_key DPPMS\|provider\|company\|type\|order[\|line] | 已登记，继续语义复核 |
| C045 | `com_shipment_package.carrier_code` | fb_shipment.emsCompany; fnd_basic_data.basicDataId | carrier_name is decoded name; no HEAD dedicated code field proved | 已登记，继续语义复核 |
| C046 | `com_shipment_package.source_contract_key` | fb_contract.contract_id; fb_shipment.con_id | shipment_contract_ref_id→com_shipment_contract_reference.source_record_key | 已登记，继续语义复核 |
| C047 | `imp_eng_installation.changed_contract_no` | 待核实 | com_contract.contract_no | 已登记，继续语义复核 |
| C048 | `imp_eng_installation.changed_project_key` | 待核实 | ast_device_project_relationship.project_id | 已登记，继续语义复核 |
| C049 | `imp_eng_installation.source_carrier_name` | 待核实 | com_shipment_package.carrier_name | 已登记，继续语义复核 |
| C050 | `imp_eng_installation.source_contract_no` | 待核实 | com_contract.contract_no; com_shipment_contract_reference.contract_id | 已登记，继续语义复核 |
| C051 | `imp_eng_installation.source_created_at` | 待核实 | IMP installation/address Owner plus AST/COM authorized public relation queries | 已登记，继续语义复核 |
| C052 | `imp_eng_installation.source_created_by` | 待核实 | IMP installation/address Owner plus AST/COM authorized public relation queries | 已登记，继续语义复核 |
| C053 | `imp_eng_installation.source_express_no` | 待核实 | com_shipment_package.express_no | 已登记，继续语义复核 |
| C054 | `imp_eng_installation.source_product_code` | 待核实 | ast_product_official_info.product_code; ast_device_shipment.product_code | 已登记，继续语义复核 |
| C055 | `imp_eng_installation.source_product_model` | 待核实 | ast_product_official_info.product_model | 已登记，继续语义复核 |
| C056 | `imp_eng_installation.source_product_name` | 待核实 | ast_product_official_info.product_name | 已登记，继续语义复核 |
| C057 | `imp_eng_installation.source_receiver_name` | 待核实 | com_shipment_package.receiver_name | 已登记，继续语义复核 |
| C058 | `imp_eng_installation.source_shipment_time` | 待核实 | ast_device_shipment.shipment_time; com_shipment_package.shipment_time | 已登记，继续语义复核 |
| C059 | `imp_eng_installation.source_transfer_flag` | 待核实 | IMP installation/address Owner plus AST/COM authorized public relation queries | 已登记，继续语义复核 |
| C060 | `imp_eng_installation.source_updated_at` | 待核实 | IMP installation/address Owner plus AST/COM authorized public relation queries | 已登记，继续语义复核 |
| C061 | `imp_eng_installation.source_updated_by` | 待核实 | IMP installation/address Owner plus AST/COM authorized public relation queries | 已登记，继续语义复核 |
| C062 | `imp_eng_installation.transfer_contract_no` | 待核实 | com_contract.contract_no | 已登记，继续语义复核 |
| C063 | `imp_eng_installation.transfer_project_key` | 待核实 | ast_device_project_relationship.project_id | 已登记，继续语义复核 |
| C064 | `proj_project.erp_customer_code` | pm_project_header.column002 | customer_code/customer_id; verified ERP customer alias or project-party relation | 已登记，继续语义复核 |
| C065 | `proj_project.erp_customer_name` | pm_project_header.column003 | customer_name; COM order customer name; project-party name | 已登记，继续语义复核 |
| C066 | `proj_project.source_custom_info` | pm_project_header.customInfo | 待核实 | 已登记，继续语义复核 |
| C067 | `proj_project.source_order_create_time` | pm_project_header.column009 | COM com_sales_order.order_create_time through formal order relation | 已登记，继续语义复核 |
| C068 | `com_sales_order.original_source_sync_time` | pm_order_data_from_erp.syncTime | source_updated_at | 已登记，继续语义复核 |
| C069 | `com_sales_order.source_customer_required_time` | pm_order_data_from_erp.customerRequireTime | customer_required_time | 已登记，继续语义复核 |
| C070 | `com_sales_order.source_order_create_time` | pm_order_data_from_erp.orderCreateTime | order_create_time | 已登记，继续语义复核 |
| C071 | `com_sales_order_line.original_source_sync_time` | pm_order_line_from_erp.syncTime | source_updated_at | 已登记，继续语义复核 |
| C072 | `com_shipment_package.source_carrier_name` | fnd_basic_data.basicDataName; view_shipment_ems_4_pm.emsCompany | carrier_name with exact source/code/current guards | 已登记，继续语义复核 |
| C073 | `ast_device_shipment.secondary_product_model` | view_shipment_info_4_pm.itemModel2 | ast_product_official_info.product_model; ast_device_shipment.secondary_item | 已登记，继续语义复核 |
| C074 | `ast_device_shipment.secondary_product_name` | view_shipment_info_4_pm.itemName2 | ast_product_official_info.product_name; ast_device_shipment.secondary_item | 已登记，继续语义复核 |
| C075 | `ast_device_shipment.source_primary_material_id` | fb_items.id | ast_product_official_info.product_code; ast_device_shipment.product_code | 已登记，继续语义复核 |
| C076 | `ast_device_shipment.source_product_model` | fb_items.itemname; view_shipment_info_4_pm.itemModel | ast_product_official_info.product_model; ast_device_shipment.product_code | 已登记，继续语义复核 |
| C077 | `ast_device_shipment.source_product_name` | fb_items.describe_; view_shipment_info_4_pm.itemName | ast_product_official_info.product_name; ast_device_shipment.product_code | 已登记，继续语义复核 |
| C078 | `ast_device_shipment.source_secondary_material_id` | 待核实 | ast_product_official_info.product_code; ast_device_shipment.secondary_item | 已登记，继续语义复核 |
| C079 | `ast_device_shipment.source_updated_time` | fb_shipment_barcode.updateTime | ast_device_shipment.source_updated_at | 已登记，继续语义复核 |
| C080 | `com_crm_execution_order.legacy_record_id` | pm_project_property_from_sms.id | INT source binding / external mapping; canonical source_record_key or execution source_object_id | 已登记，继续语义复核 |
| C081 | `com_crm_execution_order.source_company_code` | pm_project_property_from_sms.corporationCode | company_code via approved01→001/02→002/03→003/04→004 | 已登记，继续语义复核 |
| C082 | `proj_project.source_back_reason` | pm_project_header.column014 | existing governance/return history if action meaning is mapped | 已登记，继续语义复核 |
| C083 | `proj_project.source_back_state` | pm_project_header.isback | 待核实 | 已登记，继续语义复核 |
| C084 | `proj_project.source_company_id` | pm_project_header.compId | company_id/company_code via source company identity | 已登记，继续语义复核 |
| C085 | `proj_project.source_created_at` | pm_project_header.createTime | native create_time only via explicit audit migration policy | 已登记，继续语义复核 |
| C086 | `proj_project.source_created_by` | pm_project_header.createBy | creator only via source-user/audit policy | 已登记，继续语义复核 |
| C087 | `proj_project.source_custom_config` | pm_project_header.customConfig | 待核实 | 已登记，继续语义复核 |
| C088 | `proj_project.source_customer_project_name` | pm_project_header.customerProjectName | customer_project_name | 已登记，继续语义复核 |
| C089 | `proj_project.source_delivery_project_type` | pm_project_header.column010 | project_type/project_category/business_type after dictionary mapping | 已登记，继续语义复核 |
| C090 | `proj_project.source_disabled` | pm_project_header.disabled | source record validity, not native deleted/current status | 已登记，继续语义复核 |
| C091 | `proj_project.source_effective_from` | pm_project_header.effectiveFrom | source-record revision interval if existing Owner supports | 已登记，继续语义复核 |
| C092 | `proj_project.source_effective_to` | pm_project_header.effectiveTo | source-record revision interval | 已登记，继续语义复核 |
| C093 | `proj_project.source_final_customer_name` | pm_project_header.column013 | proj_project_party party_role/party_name; COM final_customer_name | 已登记，继续语义复核 |
| C094 | `proj_project.source_implementation_mode` | pm_project_header.column012 | implementation_mode through existing attribute Owner | 已登记，继续语义复核 |
| C095 | `proj_project.source_implementation_readonly` | pm_project_header.columno12_readonly | CRM attribute ownership; original implementation fallback value if applicable | 已登记，继续语义复核 |
| C096 | `proj_project.source_major_project_level` | pm_project_header.majorProjectLevel | major_project_level via CRM Owner | 已登记，继续语义复核 |
| C097 | `proj_project.source_not_track_reason` | pm_project_header.column008 | not_track_reason only with approved state meaning | 已登记，继续语义复核 |
| C098 | `proj_project.source_office_code` | pm_project_header.column001 | department_code/department_id plus organization relation | 已登记，继续语义复核 |
| C099 | `proj_project.source_project_close_time` | pm_project_header.projectCloseTime | project_close_time/closed_at through lifecycle Owner | 已登记，继续语义复核 |
| C100 | `proj_project.source_project_code` | pm_project_header.projectCode | project_code plus formal original business identity mapping | 已登记，继续语义复核 |
| C101 | `proj_project.source_project_key` | pm_project_header.projectId | existing source identity binding / native project id | 已登记，继续语义复核 |
| C102 | `proj_project.source_project_name` | pm_project_header.projectName | project_name | 已登记，继续语义复核 |
| C103 | `proj_project.source_project_refresh_time` | pm_project_header.projectRefreshTime | project_refresh_time through PROJ Owner | 已登记，继续语义复核 |
| C104 | `proj_project.source_project_start_time` | pm_project_header.projectStartTime | project_start_time through assignment/start Owner | 已登记，继续语义复核 |
| C105 | `proj_project.source_project_state` | pm_project_header.projectState | status/current_stage/lifecycle_status through lifecycle Owner | 已登记，继续语义复核 |
| C106 | `proj_project.source_project_type` | pm_project_header.projectType | project_type/project_category via approved taxonomy | 已登记，继续语义复核 |
| C107 | `proj_project.source_sales_type` | pm_project_header.salesType | sales_type | 已登记，继续语义复核 |
| C108 | `proj_project.source_signing_method` | pm_project_header.column011 | signing_method or project_category after verified old meaning | 已登记，继续语义复核 |
| C109 | `proj_project.source_updated_at` | pm_project_header.updateTime | update_time / approved source revision history | 已登记，继续语义复核 |
| C110 | `proj_project.source_updated_by` | pm_project_header.updateBy | updater / approved source actor history | 已登记，继续语义复核 |
| C111 | `imp_eng_installation.source_device_sn` | 待核实 | imp_eng_installation.equipment_id; ast_device.sn | 已登记，继续语义复核 |
| C112 | `imp_eng_installation.source_history` | 待核实 | imp_eng_installation.code; imp_eng_installation.status; imp_eng_installation.effective_from; imp_eng_installation.effective_to | 已登记，继续语义复核 |
| C113 | `imp_eng_installation.source_project_key` | 待核实 | imp_eng_installation.project_id | 已登记，继续语义复核 |
| C114 | `imp_eng_installation.source_record_key` | 待核实 | imp_eng_installation.code | 已登记，继续语义复核 |

## 新表及其各列：T001–T014

表模型和每列分别裁决；含主键、租户、关系、来源身份、审计列，不因属于通用模板就默认批准。下列来源是旧实现映射，不能作为正确性结论。没有明确来源映射的项列为待核定用途，不作丢弃。

### T001：`proj_project_product`

V412；21 列；已批准项目范围内登记行数 105（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_product_line.id; pm_project_product_line.projectId; pm_project_product_line.contractNo; pm_project_product_line.itemCode; pm_project_product_line.itemName; pm_project_product_line.projectQuantity; pm_project_product_line.orderQuantity; pm_project_product_line.deliverQuantity; pm_project_product_line.openQuantity; pm_project_product_line.orderNumber; pm_project_product_line.lineNum。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_record_key); KEY idx_project (tenant_id,project_id,deleted)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T001-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T001-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T001-C003 | `project_id` | `project_id BIGINT NOT NULL` | pm_project_product_line.projectId | 已登记，逐列语义复核 |
| T001-C004 | `source_system` | `source_system VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T001-C005 | `source_table` | `source_table VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T001-C006 | `source_record_key` | `source_record_key VARCHAR(128) NOT NULL` | pm_project_product_line.id | 已登记，逐列语义复核 |
| T001-C007 | `contract_no` | `contract_no TEXT NULL` | pm_project_product_line.contractNo | 已登记，逐列语义复核 |
| T001-C008 | `product_code` | `product_code TEXT NULL` | pm_project_product_line.itemCode | 已登记，逐列语义复核 |
| T001-C009 | `product_name` | `product_name TEXT NULL` | pm_project_product_line.itemName | 已登记，逐列语义复核 |
| T001-C010 | `project_quantity` | `project_quantity DECIMAL(24,6) NULL` | pm_project_product_line.projectQuantity | 已登记，逐列语义复核 |
| T001-C011 | `order_quantity` | `order_quantity DECIMAL(24,6) NULL` | pm_project_product_line.orderQuantity | 已登记，逐列语义复核 |
| T001-C012 | `delivered_quantity` | `delivered_quantity DECIMAL(24,6) NULL` | pm_project_product_line.deliverQuantity | 已登记，逐列语义复核 |
| T001-C013 | `open_quantity` | `open_quantity DECIMAL(24,6) NULL` | pm_project_product_line.openQuantity | 已登记，逐列语义复核 |
| T001-C014 | `order_no` | `order_no TEXT NULL` | pm_project_product_line.orderNumber | 已登记，逐列语义复核 |
| T001-C015 | `line_no` | `line_no TEXT NULL` | pm_project_product_line.lineNum | 已登记，逐列语义复核 |
| T001-C016 | `unit_code` | `unit_code TEXT NULL` | 待核实 | 已登记，逐列语义复核 |
| T001-C017 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T001-C018 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T001-C019 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T001-C020 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T001-C021 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T002：`proj_project_real_product`

V412；21 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_product_line_real.id; pm_project_product_line_real.projectId; pm_project_product_line_real.contractNo; pm_project_product_line_real.projectCode; pm_project_product_line_real.orderExecNumber; pm_project_product_line_real.productFirstName; pm_project_product_line_real.productName; pm_project_product_line_real.productSubCode; pm_project_product_line_real.productSubModel; pm_project_product_line_real.productSubName; pm_project_product_line_real.num; pm_project_product_line_real.memo。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_record_key); KEY idx_project (tenant_id,project_id,deleted)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T002-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T002-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T002-C003 | `project_id` | `project_id BIGINT NOT NULL` | pm_project_product_line_real.projectId | 已登记，逐列语义复核 |
| T002-C004 | `source_system` | `source_system VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T002-C005 | `source_table` | `source_table VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T002-C006 | `source_record_key` | `source_record_key VARCHAR(128) NOT NULL` | pm_project_product_line_real.id | 已登记，逐列语义复核 |
| T002-C007 | `contract_no` | `contract_no TEXT NULL` | pm_project_product_line_real.contractNo | 已登记，逐列语义复核 |
| T002-C008 | `source_project_code` | `source_project_code TEXT NULL` | pm_project_product_line_real.projectCode | 已登记，逐列语义复核 |
| T002-C009 | `execution_no` | `execution_no TEXT NULL` | pm_project_product_line_real.orderExecNumber | 已登记，逐列语义复核 |
| T002-C010 | `product_category_name` | `product_category_name TEXT NULL` | pm_project_product_line_real.productFirstName | 已登记，逐列语义复核 |
| T002-C011 | `product_name` | `product_name TEXT NULL` | pm_project_product_line_real.productName | 已登记，逐列语义复核 |
| T002-C012 | `product_sub_code` | `product_sub_code TEXT NULL` | pm_project_product_line_real.productSubCode | 已登记，逐列语义复核 |
| T002-C013 | `product_sub_model` | `product_sub_model TEXT NULL` | pm_project_product_line_real.productSubModel | 已登记，逐列语义复核 |
| T002-C014 | `product_sub_name` | `product_sub_name TEXT NULL` | pm_project_product_line_real.productSubName | 已登记，逐列语义复核 |
| T002-C015 | `quantity` | `quantity DECIMAL(24,6) NULL` | pm_project_product_line_real.num | 已登记，逐列语义复核 |
| T002-C016 | `memo` | `memo TEXT NULL` | pm_project_product_line_real.memo | 已登记，逐列语义复核 |
| T002-C017 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T002-C018 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T002-C019 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T002-C020 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T002-C021 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T003：`proj_project_lease_product`

V412；22 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_product_lease_line_from_crm.projectCode; pm_project_product_lease_line_from_crm.orderExecNumber; pm_project_product_lease_line_from_crm.productFirstName; pm_project_product_lease_line_from_crm.productName; pm_project_product_lease_line_from_crm.productSubCode; pm_project_product_lease_line_from_crm.productSubModel; pm_project_product_lease_line_from_crm.productSubName; pm_project_product_lease_line_from_crm.num; pm_project_product_lease_line_from_crm.memo; pm_project_product_lease_line_from_crm.leaseDuration; pm_project_product_lease_line_from_crm.dataSource。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_record_key); KEY idx_project (tenant_id,project_id,deleted)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T003-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C004 | `source_system` | `source_system VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C005 | `source_table` | `source_table VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C006 | `source_record_key` | `source_record_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T003-C007 | `source_project_code` | `source_project_code TEXT NULL` | pm_project_product_lease_line_from_crm.projectCode | 已登记，逐列语义复核 |
| T003-C008 | `execution_no` | `execution_no TEXT NULL` | pm_project_product_lease_line_from_crm.orderExecNumber | 已登记，逐列语义复核 |
| T003-C009 | `product_category_name` | `product_category_name TEXT NULL` | pm_project_product_lease_line_from_crm.productFirstName | 已登记，逐列语义复核 |
| T003-C010 | `product_name` | `product_name TEXT NULL` | pm_project_product_lease_line_from_crm.productName | 已登记，逐列语义复核 |
| T003-C011 | `product_sub_code` | `product_sub_code TEXT NULL` | pm_project_product_lease_line_from_crm.productSubCode | 已登记，逐列语义复核 |
| T003-C012 | `product_sub_model` | `product_sub_model TEXT NULL` | pm_project_product_lease_line_from_crm.productSubModel | 已登记，逐列语义复核 |
| T003-C013 | `product_sub_name` | `product_sub_name TEXT NULL` | pm_project_product_lease_line_from_crm.productSubName | 已登记，逐列语义复核 |
| T003-C014 | `quantity` | `quantity DECIMAL(24,6) NULL` | pm_project_product_lease_line_from_crm.num | 已登记，逐列语义复核 |
| T003-C015 | `memo` | `memo TEXT NULL` | pm_project_product_lease_line_from_crm.memo | 已登记，逐列语义复核 |
| T003-C016 | `lease_duration` | `lease_duration TEXT NULL` | pm_project_product_lease_line_from_crm.leaseDuration | 已登记，逐列语义复核 |
| T003-C017 | `data_source` | `data_source TEXT NULL` | pm_project_product_lease_line_from_crm.dataSource | 已登记，逐列语义复核 |
| T003-C018 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T003-C019 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T003-C020 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T003-C021 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T003-C022 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T004：`proj_project_product_configuration`

V412；22 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_product_config_level_info_from_crm.projectCode; pm_project_product_config_level_info_from_crm.orderExecNumber; pm_project_product_config_level_info_from_crm.itemGroup; pm_project_product_config_level_info_from_crm.itemCode; pm_project_product_config_level_info_from_crm.parentCode; pm_project_product_config_level_info_from_crm.quantity; pm_project_product_config_level_info_from_crm.bomPaths; pm_project_product_config_level_info_from_crm.itemModel; pm_project_product_config_level_info_from_crm.itemDesc; pm_project_product_config_level_info_from_crm.level; pm_project_product_config_level_info_from_crm.dataSource。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_record_key); KEY idx_project (tenant_id,project_id,deleted)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T004-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C004 | `source_system` | `source_system VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C005 | `source_table` | `source_table VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C006 | `source_record_key` | `source_record_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T004-C007 | `source_project_code` | `source_project_code TEXT NULL` | pm_project_product_config_level_info_from_crm.projectCode | 已登记，逐列语义复核 |
| T004-C008 | `execution_no` | `execution_no TEXT NULL` | pm_project_product_config_level_info_from_crm.orderExecNumber | 已登记，逐列语义复核 |
| T004-C009 | `item_group` | `item_group TEXT NULL` | pm_project_product_config_level_info_from_crm.itemGroup | 已登记，逐列语义复核 |
| T004-C010 | `product_code` | `product_code TEXT NULL` | pm_project_product_config_level_info_from_crm.itemCode | 已登记，逐列语义复核 |
| T004-C011 | `parent_code` | `parent_code TEXT NULL` | pm_project_product_config_level_info_from_crm.parentCode | 已登记，逐列语义复核 |
| T004-C012 | `quantity` | `quantity DECIMAL(24,6) NULL` | pm_project_product_config_level_info_from_crm.quantity | 已登记，逐列语义复核 |
| T004-C013 | `bom_path` | `bom_path TEXT NULL` | pm_project_product_config_level_info_from_crm.bomPaths | 已登记，逐列语义复核 |
| T004-C014 | `product_model` | `product_model TEXT NULL` | pm_project_product_config_level_info_from_crm.itemModel | 已登记，逐列语义复核 |
| T004-C015 | `product_description` | `product_description TEXT NULL` | pm_project_product_config_level_info_from_crm.itemDesc | 已登记，逐列语义复核 |
| T004-C016 | `level` | `level INT NULL` | pm_project_product_config_level_info_from_crm.level | 已登记，逐列语义复核 |
| T004-C017 | `data_source` | `data_source TEXT NULL` | pm_project_product_config_level_info_from_crm.dataSource | 已登记，逐列语义复核 |
| T004-C018 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T004-C019 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T004-C020 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T004-C021 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T004-C022 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T005：`com_payment_plan`

V412；26 列；已批准项目范围内登记行数 10（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_pb_plan_from_sms.id; pm_pb_plan_from_sms.contractNo; pm_pb_plan_from_sms.batchCode; pm_pb_plan_from_sms.basicDataName; pm_pb_plan_from_sms.referenceEventName; pm_pb_plan_from_sms.eventPlanHappenDate; pm_pb_plan_from_sms.afterDaysNum; pm_pb_plan_from_sms.eventActualFinishDate; pm_pb_plan_from_sms.marketingFeedback; pm_pb_plan_from_sms.createBy; pm_pb_plan_from_sms.createTime; pm_pb_plan_from_sms.updateBy; pm_pb_plan_from_sms.updateTime; pm_pb_plan_from_sms.effectiveFrom; pm_pb_plan_from_sms.effectiveTo; pm_pb_plan_from_sms.dataSource。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_record_key); KEY idx_project (tenant_id,project_id,deleted); KEY idx_contract (tenant_id,contract_id,deleted)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T005-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T005-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T005-C003 | `contract_id` | `contract_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T005-C004 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T005-C005 | `contract_no` | `contract_no TEXT NULL` | pm_pb_plan_from_sms.contractNo | 已登记，逐列语义复核 |
| T005-C006 | `batch_code` | `batch_code TEXT NULL` | pm_pb_plan_from_sms.batchCode | 已登记，逐列语义复核 |
| T005-C007 | `plan_name` | `plan_name TEXT NULL` | pm_pb_plan_from_sms.basicDataName | 已登记，逐列语义复核 |
| T005-C008 | `reference_event_name` | `reference_event_name TEXT NULL` | pm_pb_plan_from_sms.referenceEventName | 已登记，逐列语义复核 |
| T005-C009 | `event_planned_at` | `event_planned_at DATETIME(3) NULL` | pm_pb_plan_from_sms.eventPlanHappenDate | 已登记，逐列语义复核 |
| T005-C010 | `after_days` | `after_days DECIMAL(24,6) NULL` | pm_pb_plan_from_sms.afterDaysNum | 已登记，逐列语义复核 |
| T005-C011 | `event_actual_finished_at` | `event_actual_finished_at DATETIME(3) NULL` | pm_pb_plan_from_sms.eventActualFinishDate | 已登记，逐列语义复核 |
| T005-C012 | `marketing_feedback` | `marketing_feedback TEXT NULL` | pm_pb_plan_from_sms.marketingFeedback | 已登记，逐列语义复核 |
| T005-C013 | `data_source` | `data_source TEXT NULL` | pm_pb_plan_from_sms.dataSource | 已登记，逐列语义复核 |
| T005-C014 | `source_created_at` | `source_created_at DATETIME(3) NULL` | pm_pb_plan_from_sms.createTime | 已登记，逐列语义复核 |
| T005-C015 | `source_created_by` | `source_created_by TEXT NULL` | pm_pb_plan_from_sms.createBy | 已登记，逐列语义复核 |
| T005-C016 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | pm_pb_plan_from_sms.updateTime | 已登记，逐列语义复核 |
| T005-C017 | `source_updated_by` | `source_updated_by TEXT NULL` | pm_pb_plan_from_sms.updateBy | 已登记，逐列语义复核 |
| T005-C018 | `effective_from` | `effective_from DATETIME(3) NULL` | pm_pb_plan_from_sms.effectiveFrom | 已登记，逐列语义复核 |
| T005-C019 | `effective_to` | `effective_to DATETIME(3) NULL` | pm_pb_plan_from_sms.effectiveTo | 已登记，逐列语义复核 |
| T005-C020 | `source_system` | `source_system VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T005-C021 | `source_record_key` | `source_record_key VARCHAR(128) NOT NULL` | pm_pb_plan_from_sms.id | 已登记，逐列语义复核 |
| T005-C022 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T005-C023 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T005-C024 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T005-C025 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T005-C026 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T006：`proj_project_maintenance_record`

V414；55 列；已批准项目范围内登记行数 17（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_maintenance.id; pm_project_maintenance.projectId; pm_project_maintenance.projectCode; pm_project_maintenance.projectName; pm_project_maintenance.projectType; pm_project_maintenance.projectExecutionState; pm_project_maintenance.contractNo; pm_project_maintenance.officeCode; pm_project_maintenance.compId; pm_project_maintenance.type; pm_project_maintenance.category; pm_project_maintenance.subCategory; pm_project_maintenance.processTime; pm_project_maintenance.processDesc; pm_project_maintenance.processStep; pm_project_maintenance.remainProblem; pm_project_maintenance.transitHour; pm_project_maintenance.processHour; pm_project_maintenance.itemModel; pm_project_maintenance.softVersion; pm_project_maintenance.enabledFeatures; pm_project_maintenance.customTos; pm_project_maintenance.customCcs; pm_project_maintenance.hasReport; pm_project_maintenance.quesnaireId; pm_project_maintenance.deliverFileIds; pm_project_maintenance.warrantyStatus; pm_project_maintenance.industryName; pm_project_maintenance.userOffice; pm_project_maintenance.year; pm_project_maintenance.quarter; pm_project_maintenance.month; pm_project_maintenance.wsCount; pm_project_maintenance.wafCount; pm_project_maintenance.wsYearCount; pm_project_maintenance.wafYearCount; pm_project_maintenance.warrantyInfo; pm_project_maintenance.serviceInfo; pm_project_maintenance.remark; pm_project_maintenance.createTime; pm_project_maintenance.createBy; pm_project_maintenance.updateTime; pm_project_maintenance.updateBy; pm_project_maintenance.customInfo。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_record_key); KEY idx_project (tenant_id,project_id,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T006-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C004 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C005 | `source_table` | `source_table VARCHAR(64) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C006 | `source_record_key` | `source_record_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T006-C007 | `legacy_maintenance_id` | `legacy_maintenance_id BIGINT NULL` | pm_project_maintenance.id | 已登记，逐列语义复核 |
| T006-C008 | `source_project_id` | `source_project_id BIGINT NULL` | pm_project_maintenance.projectId | 已登记，逐列语义复核 |
| T006-C009 | `source_project_code` | `source_project_code TEXT NULL` | pm_project_maintenance.projectCode | 已登记，逐列语义复核 |
| T006-C010 | `source_project_name` | `source_project_name TEXT NULL` | pm_project_maintenance.projectName | 已登记，逐列语义复核 |
| T006-C011 | `source_project_type` | `source_project_type INT NULL` | pm_project_maintenance.projectType | 已登记，逐列语义复核 |
| T006-C012 | `source_project_execution_state` | `source_project_execution_state TEXT NULL` | pm_project_maintenance.projectExecutionState | 已登记，逐列语义复核 |
| T006-C013 | `contract_no` | `contract_no TEXT NULL` | pm_project_maintenance.contractNo | 已登记，逐列语义复核 |
| T006-C014 | `office_code` | `office_code TEXT NULL` | pm_project_maintenance.officeCode | 已登记，逐列语义复核 |
| T006-C015 | `source_company_id` | `source_company_id INT NULL` | pm_project_maintenance.compId | 已登记，逐列语义复核 |
| T006-C016 | `service_type` | `service_type TEXT NULL` | pm_project_maintenance.type | 已登记，逐列语义复核 |
| T006-C017 | `service_category` | `service_category TEXT NULL` | pm_project_maintenance.category | 已登记，逐列语义复核 |
| T006-C018 | `service_sub_category` | `service_sub_category TEXT NULL` | pm_project_maintenance.subCategory | 已登记，逐列语义复核 |
| T006-C019 | `process_time` | `process_time DATETIME(3) NULL` | pm_project_maintenance.processTime | 已登记，逐列语义复核 |
| T006-C020 | `process_desc` | `process_desc TEXT NULL` | pm_project_maintenance.processDesc | 已登记，逐列语义复核 |
| T006-C021 | `process_step` | `process_step TEXT NULL` | pm_project_maintenance.processStep | 已登记，逐列语义复核 |
| T006-C022 | `remain_problem` | `remain_problem TEXT NULL` | pm_project_maintenance.remainProblem | 已登记，逐列语义复核 |
| T006-C023 | `transit_hour` | `transit_hour DOUBLE NULL` | pm_project_maintenance.transitHour | 已登记，逐列语义复核 |
| T006-C024 | `process_hour` | `process_hour DOUBLE NULL` | pm_project_maintenance.processHour | 已登记，逐列语义复核 |
| T006-C025 | `item_model` | `item_model TEXT NULL` | pm_project_maintenance.itemModel | 已登记，逐列语义复核 |
| T006-C026 | `soft_version` | `soft_version TEXT NULL` | pm_project_maintenance.softVersion | 已登记，逐列语义复核 |
| T006-C027 | `enabled_features` | `enabled_features TEXT NULL` | pm_project_maintenance.enabledFeatures | 已登记，逐列语义复核 |
| T006-C028 | `custom_tos` | `custom_tos TEXT NULL` | pm_project_maintenance.customTos | 已登记，逐列语义复核 |
| T006-C029 | `custom_ccs` | `custom_ccs TEXT NULL` | pm_project_maintenance.customCcs | 已登记，逐列语义复核 |
| T006-C030 | `has_report` | `has_report INT NULL` | pm_project_maintenance.hasReport | 已登记，逐列语义复核 |
| T006-C031 | `source_questionnaire_id` | `source_questionnaire_id BIGINT NULL` | pm_project_maintenance.quesnaireId | 已登记，逐列语义复核 |
| T006-C032 | `source_deliver_file_ids` | `source_deliver_file_ids TEXT NULL` | pm_project_maintenance.deliverFileIds | 已登记，逐列语义复核 |
| T006-C033 | `warranty_status` | `warranty_status TEXT NULL` | pm_project_maintenance.warrantyStatus | 已登记，逐列语义复核 |
| T006-C034 | `industry_name` | `industry_name TEXT NULL` | pm_project_maintenance.industryName | 已登记，逐列语义复核 |
| T006-C035 | `user_office` | `user_office TEXT NULL` | pm_project_maintenance.userOffice | 已登记，逐列语义复核 |
| T006-C036 | `service_year` | `service_year INT NULL` | pm_project_maintenance.year | 已登记，逐列语义复核 |
| T006-C037 | `service_quarter` | `service_quarter INT NULL` | pm_project_maintenance.quarter | 已登记，逐列语义复核 |
| T006-C038 | `service_month` | `service_month INT NULL` | pm_project_maintenance.month | 已登记，逐列语义复核 |
| T006-C039 | `ws_count` | `ws_count INT NULL` | pm_project_maintenance.wsCount | 已登记，逐列语义复核 |
| T006-C040 | `waf_count` | `waf_count INT NULL` | pm_project_maintenance.wafCount | 已登记，逐列语义复核 |
| T006-C041 | `ws_year_count` | `ws_year_count INT NULL` | pm_project_maintenance.wsYearCount | 已登记，逐列语义复核 |
| T006-C042 | `waf_year_count` | `waf_year_count INT NULL` | pm_project_maintenance.wafYearCount | 已登记，逐列语义复核 |
| T006-C043 | `warranty_info` | `warranty_info TEXT NULL` | pm_project_maintenance.warrantyInfo | 已登记，逐列语义复核 |
| T006-C044 | `service_info` | `service_info TEXT NULL` | pm_project_maintenance.serviceInfo | 已登记，逐列语义复核 |
| T006-C045 | `remark` | `remark TEXT NULL` | pm_project_maintenance.remark | 已登记，逐列语义复核 |
| T006-C046 | `source_created_at` | `source_created_at DATETIME(3) NULL` | pm_project_maintenance.createTime | 已登记，逐列语义复核 |
| T006-C047 | `source_created_by` | `source_created_by TEXT NULL` | pm_project_maintenance.createBy | 已登记，逐列语义复核 |
| T006-C048 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | pm_project_maintenance.updateTime | 已登记，逐列语义复核 |
| T006-C049 | `source_updated_by` | `source_updated_by TEXT NULL` | pm_project_maintenance.updateBy | 已登记，逐列语义复核 |
| T006-C050 | `custom_info` | `custom_info JSON NULL` | pm_project_maintenance.customInfo | 已登记，逐列语义复核 |
| T006-C051 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T006-C052 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T006-C053 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T006-C054 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T006-C055 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T007：`ast_device_software_observation`

V414；42 列；已批准项目范围内登记行数 659（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_soft_version.id; pm_project_soft_version.projectId; pm_project_soft_version.logId; pm_project_soft_version.contractNo; pm_project_soft_version.itemCode; pm_project_soft_version.barCode; pm_project_soft_version.conp; pm_project_soft_version.conpType; pm_project_soft_version.conpSeries; pm_project_soft_version.conpMark; pm_project_soft_version.conpBak; pm_project_soft_version.conpChange; pm_project_soft_version.cpld; pm_project_soft_version.cpldBak; pm_project_soft_version.cpldChange; pm_project_soft_version.boot; pm_project_soft_version.bootBak; pm_project_soft_version.bootChange; pm_project_soft_version.pcb; pm_project_soft_version.pcbBak; pm_project_soft_version.pcbChange; pm_project_soft_version.executeTime; pm_project_soft_version.datastate; pm_project_soft_version.createTime; pm_project_soft_version.createBy; pm_project_soft_version.updateTime; pm_project_soft_version.updateBy; pm_project_soft_version.customInfo。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,project_id,source_system,source_key,source_checksum); KEY idx_project (tenant_id,project_id,deleted,id); KEY idx_device (tenant_id,bar_code,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T007-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C004 | `source_project_key` | `source_project_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C005 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C006 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C007 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C008 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C009 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T007-C010 | `source_id` | `source_id INT NULL` | pm_project_soft_version.id | 已登记，逐列语义复核 |
| T007-C011 | `source_project_id` | `source_project_id INT NULL` | pm_project_soft_version.projectId | 已登记，逐列语义复核 |
| T007-C012 | `log_id` | `log_id INT NULL` | pm_project_soft_version.logId | 已登记，逐列语义复核 |
| T007-C013 | `contract_no` | `contract_no VARCHAR(100) NULL` | pm_project_soft_version.contractNo | 已登记，逐列语义复核 |
| T007-C014 | `item_code` | `item_code VARCHAR(25) NULL` | pm_project_soft_version.itemCode | 已登记，逐列语义复核 |
| T007-C015 | `bar_code` | `bar_code VARCHAR(25) NULL` | pm_project_soft_version.barCode | 已登记，逐列语义复核 |
| T007-C016 | `conp` | `conp VARCHAR(100) NULL` | pm_project_soft_version.conp | 已登记，逐列语义复核 |
| T007-C017 | `conp_type` | `conp_type VARCHAR(100) NULL` | pm_project_soft_version.conpType | 已登记，逐列语义复核 |
| T007-C018 | `conp_series` | `conp_series VARCHAR(100) NULL` | pm_project_soft_version.conpSeries | 已登记，逐列语义复核 |
| T007-C019 | `conp_mark` | `conp_mark VARCHAR(255) NULL` | pm_project_soft_version.conpMark | 已登记，逐列语义复核 |
| T007-C020 | `conp_bak` | `conp_bak VARCHAR(255) NULL` | pm_project_soft_version.conpBak | 已登记，逐列语义复核 |
| T007-C021 | `conp_change` | `conp_change INT NULL` | pm_project_soft_version.conpChange | 已登记，逐列语义复核 |
| T007-C022 | `cpld` | `cpld VARCHAR(100) NULL` | pm_project_soft_version.cpld | 已登记，逐列语义复核 |
| T007-C023 | `cpld_bak` | `cpld_bak VARCHAR(255) NULL` | pm_project_soft_version.cpldBak | 已登记，逐列语义复核 |
| T007-C024 | `cpld_change` | `cpld_change INT NULL` | pm_project_soft_version.cpldChange | 已登记，逐列语义复核 |
| T007-C025 | `boot` | `boot VARCHAR(100) NULL` | pm_project_soft_version.boot | 已登记，逐列语义复核 |
| T007-C026 | `boot_bak` | `boot_bak VARCHAR(255) NULL` | pm_project_soft_version.bootBak | 已登记，逐列语义复核 |
| T007-C027 | `boot_change` | `boot_change INT NULL` | pm_project_soft_version.bootChange | 已登记，逐列语义复核 |
| T007-C028 | `pcb` | `pcb VARCHAR(100) NULL` | pm_project_soft_version.pcb | 已登记，逐列语义复核 |
| T007-C029 | `pcb_bak` | `pcb_bak VARCHAR(255) NULL` | pm_project_soft_version.pcbBak | 已登记，逐列语义复核 |
| T007-C030 | `pcb_change` | `pcb_change INT NULL` | pm_project_soft_version.pcbChange | 已登记，逐列语义复核 |
| T007-C031 | `execute_time` | `execute_time DATE NULL` | pm_project_soft_version.executeTime | 已登记，逐列语义复核 |
| T007-C032 | `datastate` | `datastate INT NULL` | pm_project_soft_version.datastate | 已登记，逐列语义复核 |
| T007-C033 | `source_created_at` | `source_created_at DATETIME(3) NULL` | pm_project_soft_version.createTime | 已登记，逐列语义复核 |
| T007-C034 | `source_created_by` | `source_created_by VARCHAR(25) NULL` | pm_project_soft_version.createBy | 已登记，逐列语义复核 |
| T007-C035 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | pm_project_soft_version.updateTime | 已登记，逐列语义复核 |
| T007-C036 | `source_updated_by` | `source_updated_by VARCHAR(25) NULL` | pm_project_soft_version.updateBy | 已登记，逐列语义复核 |
| T007-C037 | `custom_info` | `custom_info JSON NULL` | pm_project_soft_version.customInfo | 已登记，逐列语义复核 |
| T007-C038 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T007-C039 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T007-C040 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T007-C041 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T007-C042 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T008：`ast_device_software_change_batch`

V414；23 列；已批准项目范围内登记行数 6（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_soft_change_logs.id; pm_project_soft_change_logs.projectId; pm_project_soft_change_logs.changeVersion; pm_project_soft_change_logs.changeRemark; pm_project_soft_change_logs.latest; pm_project_soft_change_logs.createBy; pm_project_soft_change_logs.createTime; pm_project_soft_change_logs.updateBy; pm_project_soft_change_logs.updateTime。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,project_id,source_system,source_key,source_checksum); KEY idx_project (tenant_id,project_id,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T008-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C004 | `source_project_key` | `source_project_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C005 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C006 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C007 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C008 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C009 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T008-C010 | `source_id` | `source_id INT NULL` | pm_project_soft_change_logs.id | 已登记，逐列语义复核 |
| T008-C011 | `source_project_id` | `source_project_id INT NULL` | pm_project_soft_change_logs.projectId | 已登记，逐列语义复核 |
| T008-C012 | `change_version` | `change_version VARCHAR(10) NULL` | pm_project_soft_change_logs.changeVersion | 已登记，逐列语义复核 |
| T008-C013 | `change_remark` | `change_remark VARCHAR(255) NULL` | pm_project_soft_change_logs.changeRemark | 已登记，逐列语义复核 |
| T008-C014 | `latest` | `latest INT NULL` | pm_project_soft_change_logs.latest | 已登记，逐列语义复核 |
| T008-C015 | `source_created_by` | `source_created_by VARCHAR(25) NULL` | pm_project_soft_change_logs.createBy | 已登记，逐列语义复核 |
| T008-C016 | `source_created_at` | `source_created_at DATETIME(3) NULL` | pm_project_soft_change_logs.createTime | 已登记，逐列语义复核 |
| T008-C017 | `source_updated_by` | `source_updated_by VARCHAR(25) NULL` | pm_project_soft_change_logs.updateBy | 已登记，逐列语义复核 |
| T008-C018 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | pm_project_soft_change_logs.updateTime | 已登记，逐列语义复核 |
| T008-C019 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T008-C020 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T008-C021 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T008-C022 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T008-C023 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T009：`ast_device_factory_version_record`

V414；19 列；已批准项目范围内登记行数 19（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：fb_soft_version.serial_number; fb_soft_version.conp; fb_soft_version.cpld; fb_soft_version.boot; fb_soft_version.pcb。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,project_id,source_system,source_key,source_checksum); KEY idx_project (tenant_id,project_id,deleted,id); KEY idx_device (tenant_id,serial_number,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T009-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C004 | `source_project_key` | `source_project_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C005 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C006 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C007 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C008 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C009 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T009-C010 | `serial_number` | `serial_number VARCHAR(100) NULL` | fb_soft_version.serial_number | 已登记，逐列语义复核 |
| T009-C011 | `conp` | `conp VARCHAR(100) NULL` | fb_soft_version.conp | 已登记，逐列语义复核 |
| T009-C012 | `boot` | `boot VARCHAR(100) NULL` | fb_soft_version.boot | 已登记，逐列语义复核 |
| T009-C013 | `cpld` | `cpld VARCHAR(100) NULL` | fb_soft_version.cpld | 已登记，逐列语义复核 |
| T009-C014 | `pcb` | `pcb VARCHAR(100) NULL` | fb_soft_version.pcb | 已登记，逐列语义复核 |
| T009-C015 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T009-C016 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T009-C017 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T009-C018 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T009-C019 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T010：`imp_installation_address_source_fact`

V414；39 列；已批准项目范围内登记行数 948（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：pm_project_shipment.id; pm_project_shipment.projectId; pm_project_shipment.barcode; pm_project_shipment.itemCode; pm_project_shipment.itemModel; pm_project_shipment.itemName; pm_project_shipment.receiveName; pm_project_shipment.emsNum; pm_project_shipment.emsCompany; pm_project_shipment.packdate; pm_project_shipment.contractNo; pm_project_shipment.installAddress; pm_project_shipment.chProjectId; pm_project_shipment.chContractNo; pm_project_shipment.transferProjectId; pm_project_shipment.transferContractNo; pm_project_shipment.transferFlag; pm_project_shipment.createTime; pm_project_shipment.createBy; pm_project_shipment.updateTime; pm_project_shipment.updateBy。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,project_id,source_system,source_key,source_checksum); KEY idx_project (tenant_id,project_id,deleted,id); KEY idx_equipment (tenant_id,equipment_id,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T010-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C004 | `source_project_key` | `source_project_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C005 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C006 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C007 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C008 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C009 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C010 | `equipment_id` | `equipment_id BIGINT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C011 | `address_id` | `address_id BIGINT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C012 | `address_version` | `address_version BIGINT NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C013 | `address_snapshot` | `address_snapshot JSON NULL` | 待核实 | 已登记，逐列语义复核 |
| T010-C014 | `source_id` | `source_id INT NULL` | pm_project_shipment.id | 已登记，逐列语义复核 |
| T010-C015 | `source_project_id` | `source_project_id INT NULL` | pm_project_shipment.projectId | 已登记，逐列语义复核 |
| T010-C016 | `source_device_sn` | `source_device_sn VARCHAR(25) NULL` | pm_project_shipment.barcode | 已登记，逐列语义复核 |
| T010-C017 | `install_location` | `install_location TEXT NULL` | pm_project_shipment.installAddress | 已登记，逐列语义复核 |
| T010-C018 | `source_product_code` | `source_product_code VARCHAR(25) NULL` | pm_project_shipment.itemCode | 已登记，逐列语义复核 |
| T010-C019 | `source_product_model` | `source_product_model VARCHAR(255) NULL` | pm_project_shipment.itemModel | 已登记，逐列语义复核 |
| T010-C020 | `source_product_name` | `source_product_name VARCHAR(255) NULL` | pm_project_shipment.itemName | 已登记，逐列语义复核 |
| T010-C021 | `source_receiver_name` | `source_receiver_name VARCHAR(255) NULL` | pm_project_shipment.receiveName | 已登记，逐列语义复核 |
| T010-C022 | `source_express_no` | `source_express_no VARCHAR(255) NULL` | pm_project_shipment.emsNum | 已登记，逐列语义复核 |
| T010-C023 | `source_carrier_name` | `source_carrier_name VARCHAR(15) NULL` | pm_project_shipment.emsCompany | 已登记，逐列语义复核 |
| T010-C024 | `source_shipment_time` | `source_shipment_time DATETIME(3) NULL` | pm_project_shipment.packdate | 已登记，逐列语义复核 |
| T010-C025 | `source_contract_no` | `source_contract_no VARCHAR(50) NULL` | pm_project_shipment.contractNo | 已登记，逐列语义复核 |
| T010-C026 | `changed_project_key` | `changed_project_key INT NULL` | pm_project_shipment.chProjectId | 已登记，逐列语义复核 |
| T010-C027 | `changed_contract_no` | `changed_contract_no VARCHAR(50) NULL` | pm_project_shipment.chContractNo | 已登记，逐列语义复核 |
| T010-C028 | `transfer_project_key` | `transfer_project_key INT NULL` | pm_project_shipment.transferProjectId | 已登记，逐列语义复核 |
| T010-C029 | `transfer_contract_no` | `transfer_contract_no VARCHAR(50) NULL` | pm_project_shipment.transferContractNo | 已登记，逐列语义复核 |
| T010-C030 | `source_transfer_flag` | `source_transfer_flag INT NULL` | pm_project_shipment.transferFlag | 已登记，逐列语义复核 |
| T010-C031 | `source_created_at` | `source_created_at DATETIME(3) NULL` | pm_project_shipment.createTime | 已登记，逐列语义复核 |
| T010-C032 | `source_created_by` | `source_created_by VARCHAR(25) NULL` | pm_project_shipment.createBy | 已登记，逐列语义复核 |
| T010-C033 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | pm_project_shipment.updateTime | 已登记，逐列语义复核 |
| T010-C034 | `source_updated_by` | `source_updated_by VARCHAR(25) NULL` | pm_project_shipment.updateBy | 已登记，逐列语义复核 |
| T010-C035 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T010-C036 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T010-C037 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T010-C038 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T010-C039 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T011：`ast_device_warranty_service_record`

V414；23 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：fb_service.id; fb_service.con_xb; fb_service.barcode; fb_service.grade; fb_service.begin_date; fb_service.end_date; fb_service.warranty; fb_service.remark; fb_service.isyb; fb_service.state。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum); KEY idx_device (tenant_id,device_sn,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T011-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C003 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C004 | `source_table` | `source_table VARCHAR(64) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C005 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C006 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C007 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C008 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T011-C009 | `source_id` | `source_id VARCHAR(64) NULL` | fb_service.id | 已登记，逐列语义复核 |
| T011-C010 | `renewal_contract_no` | `renewal_contract_no VARCHAR(25) NULL` | fb_service.con_xb | 已登记，逐列语义复核 |
| T011-C011 | `device_sn` | `device_sn VARCHAR(50) NULL` | fb_service.barcode | 已登记，逐列语义复核 |
| T011-C012 | `grade` | `grade VARCHAR(15) NULL` | fb_service.grade | 已登记，逐列语义复核 |
| T011-C013 | `begin_date` | `begin_date DATETIME(3) NULL` | fb_service.begin_date | 已登记，逐列语义复核 |
| T011-C014 | `end_date` | `end_date DATETIME(3) NULL` | fb_service.end_date | 已登记，逐列语义复核 |
| T011-C015 | `warranty_flag` | `warranty_flag VARCHAR(1) NULL` | fb_service.warranty | 已登记，逐列语义复核 |
| T011-C016 | `remark` | `remark TEXT NULL` | fb_service.remark | 已登记，逐列语义复核 |
| T011-C017 | `extension_flag` | `extension_flag INT NULL` | fb_service.isyb | 已登记，逐列语义复核 |
| T011-C018 | `source_state` | `source_state INT NULL` | fb_service.state | 已登记，逐列语义复核 |
| T011-C019 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T011-C020 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T011-C021 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T011-C022 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T011-C023 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T012：`ast_device_warranty_registration`

V414；28 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：warranty_info.id; warranty_info.packlistId; warranty_info.contractId; warranty_info.barCode; warranty_info.officeCode; warranty_info.projectName; warranty_info.contractNo; warranty_info.contractType; warranty_info.customerName; warranty_info.itemCode; warranty_info.itemName; warranty_info.warrantyLevel; warranty_info.warrantyStartTime; warranty_info.warrantyEndTime; warranty_info.warrantyLimit。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum); KEY idx_device (tenant_id,device_sn,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T012-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C003 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C004 | `source_table` | `source_table VARCHAR(64) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C005 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C006 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C007 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C008 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T012-C009 | `source_id` | `source_id BIGINT NOT NULL` | warranty_info.id | 已登记，逐列语义复核 |
| T012-C010 | `package_sync_id` | `package_sync_id INT NULL` | warranty_info.packlistId | 已登记，逐列语义复核 |
| T012-C011 | `contract_sync_id` | `contract_sync_id INT NULL` | warranty_info.contractId | 已登记，逐列语义复核 |
| T012-C012 | `device_sn` | `device_sn VARCHAR(25) NULL` | warranty_info.barCode | 已登记，逐列语义复核 |
| T012-C013 | `office_code` | `office_code VARCHAR(25) NULL` | warranty_info.officeCode | 已登记，逐列语义复核 |
| T012-C014 | `source_project_name` | `source_project_name VARCHAR(255) NULL` | warranty_info.projectName | 已登记，逐列语义复核 |
| T012-C015 | `contract_no` | `contract_no VARCHAR(25) NULL` | warranty_info.contractNo | 已登记，逐列语义复核 |
| T012-C016 | `contract_type` | `contract_type INT NULL` | warranty_info.contractType | 已登记，逐列语义复核 |
| T012-C017 | `customer_name` | `customer_name VARCHAR(255) NULL` | warranty_info.customerName | 已登记，逐列语义复核 |
| T012-C018 | `item_code` | `item_code VARCHAR(8) NULL` | warranty_info.itemCode | 已登记，逐列语义复核 |
| T012-C019 | `item_name` | `item_name VARCHAR(255) NULL` | warranty_info.itemName | 已登记，逐列语义复核 |
| T012-C020 | `warranty_level` | `warranty_level VARCHAR(8) NULL` | warranty_info.warrantyLevel | 已登记，逐列语义复核 |
| T012-C021 | `warranty_start_time` | `warranty_start_time DATETIME(3) NULL` | warranty_info.warrantyStartTime | 已登记，逐列语义复核 |
| T012-C022 | `warranty_end_time` | `warranty_end_time DATETIME(3) NULL` | warranty_info.warrantyEndTime | 已登记，逐列语义复核 |
| T012-C023 | `warranty_limit` | `warranty_limit INT NULL` | warranty_info.warrantyLimit | 已登记，逐列语义复核 |
| T012-C024 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T012-C025 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T012-C026 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T012-C027 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T012-C028 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T013：`ast_device_warranty_change_record`

V414；21 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：warranty_change_logs.id; warranty_change_logs.username; warranty_change_logs.updateType; warranty_change_logs.barcode; warranty_change_logs.warrantyStartTime; warranty_change_logs.warrantyEndTime; warranty_change_logs.warrantyTimes; warranty_change_logs.updateTime。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum); KEY idx_device (tenant_id,device_sn,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T013-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C003 | `source_system` | `source_system VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C004 | `source_table` | `source_table VARCHAR(64) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C005 | `source_key` | `source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C006 | `source_version` | `source_version VARCHAR(128) NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C007 | `source_read_at` | `source_read_at DATETIME(3) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C008 | `source_checksum` | `source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T013-C009 | `source_id` | `source_id BIGINT NOT NULL` | warranty_change_logs.id | 已登记，逐列语义复核 |
| T013-C010 | `source_username` | `source_username VARCHAR(10) NOT NULL` | warranty_change_logs.username | 已登记，逐列语义复核 |
| T013-C011 | `update_type` | `update_type INT NULL` | warranty_change_logs.updateType | 已登记，逐列语义复核 |
| T013-C012 | `device_sn` | `device_sn VARCHAR(20) NULL` | warranty_change_logs.barcode | 已登记，逐列语义复核 |
| T013-C013 | `warranty_start_time` | `warranty_start_time DATETIME(3) NULL` | warranty_change_logs.warrantyStartTime | 已登记，逐列语义复核 |
| T013-C014 | `warranty_end_time` | `warranty_end_time DATETIME(3) NULL` | warranty_change_logs.warrantyEndTime | 已登记，逐列语义复核 |
| T013-C015 | `warranty_times` | `warranty_times INT NULL` | warranty_change_logs.warrantyTimes | 已登记，逐列语义复核 |
| T013-C016 | `source_updated_at` | `source_updated_at DATETIME(3) NULL` | warranty_change_logs.updateTime | 已登记，逐列语义复核 |
| T013-C017 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T013-C018 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T013-C019 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T013-C020 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T013-C021 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

### T014：`ast_device_warranty_fact_project`

V414；11 列；已批准项目范围内登记行数 0（引用前次核查，不代表实时计数）。表必要性、现有实体替代关系及各字段均未获得本清单批准。

原字段域：待核实。

既有 SQL 约束：PRIMARY KEY (id); UNIQUE KEY uk_source (tenant_id,project_id,fact_type,fact_id); KEY idx_fact (tenant_id,fact_type,fact_id,deleted); KEY idx_project (tenant_id,project_id,deleted,id)。

| 独立编号 | 列 | 已提出/已落库定义 | 旧映射原字段 | 当前状态 |
|---|---|---|---|---|
| T014-C001 | `id` | `id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C002 | `tenant_id` | `tenant_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C003 | `project_id` | `project_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C004 | `source_project_key` | `source_project_key VARCHAR(128) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C005 | `fact_type` | `fact_type VARCHAR(32) NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C006 | `fact_id` | `fact_id BIGINT NOT NULL` | 待核实 | 已登记，逐列语义复核 |
| T014-C007 | `creator` | `creator VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T014-C008 | `create_time` | `create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T014-C009 | `updater` | `updater VARCHAR(64) NOT NULL DEFAULT ''` | 待核实 | 已登记，逐列语义复核 |
| T014-C010 | `update_time` | `update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 待核实 | 已登记，逐列语义复核 |
| T014-C011 | `deleted` | `deleted BIT(1) NOT NULL DEFAULT b'0'` | 待核实 | 已登记，逐列语义复核 |

## 已批准执行历史

| 编号 | V417 重复列 | 正式复用列 | 状态 |
|---|---|---|---|
| H001 | `ast_device_shipment.source_primary_material_code` | `product_code` | 先前用户明确批准，V418 已执行 |
| H002 | `ast_device_shipment.source_secondary_material_code` | `secondary_item` | 先前用户明确批准，V418 已执行 |
| H003 | `com_crm_execution_order.source_channel_name` | `channel_name` | 先前用户明确批准，V418 已执行 |
| H004 | `com_crm_execution_order.source_customer_project_name` | `customer_project_name` | 先前用户明确批准，V418 已执行 |

本次仅整理裁决资料和更新当前审查状态，未改业务代码、未执行 DDL/DML、未重启服务。V4 整体承接和生产放行均未完成。
