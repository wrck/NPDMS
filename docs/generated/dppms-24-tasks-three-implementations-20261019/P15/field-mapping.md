# P15 原表逐字段承载清单

合并组件：P15, P26, P27, P28, P29。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_order_data_from_erp.id | int(11) / NO | com_contract.master_source_record_key | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| pm_order_data_from_erp.orderNumber | varchar(25) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.contractNo | varchar(50) / YES | com_contract.contract_no | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| pm_order_data_from_erp.orderExecNumber | varchar(50) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.orderCreateTime | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.customerRequireTime | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.customerCode | varchar(55) / YES | com_contract.customer_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| pm_order_data_from_erp.customerName | varchar(255) / YES | com_contract.customer_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| pm_order_data_from_erp.projectName | varchar(255) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.orderComment | varchar(2048) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.orderType | int(11) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.compCode | varchar(25) / YES | com_contract.company_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| pm_order_data_from_erp.salesType | varchar(25) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.source | varchar(25) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.customInfo | json / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| pm_order_data_from_erp.syncTime | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.id | bigint(20) / NO | 来源排序参考，不单独写合同业务列 | 逐字段以update_time＋补零id取最近非空值；原完整回款事实属于P24。；查询归并参考 | P15 |
| sms_ofst_contract_head_sap.contract_num | varchar(45) / YES | com_contract.contract_no | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.batch_code | varchar(10) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.project_name | varchar(200) / YES | com_contract.project_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.order_num | varchar(25) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.client_supplier_code | varchar(20) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.client_supplier_name | varchar(200) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.contract_money_amount | decimal(20,2) / NO | com_contract.contract_amount | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.delivered_money_amount | decimal(20,2) / NO | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.collected_money_amount | decimal(20,2) / NO | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.collected_money_ratio | double / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.receivables_money_amount | decimal(20,2) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.over_due_money_amount | decimal(20,2) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.maketing_department_name | varchar(40) / YES | com_contract.market_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.office_name | varchar(20) / YES | com_contract.department_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.industry_name | varchar(40) / YES | com_contract.industry_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.marketing_representative_name | varchar(20) / YES | com_contract.marketing_representative_name | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.currency_name | varchar(25) / YES | com_contract.currency_name；com_contract.currency_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.create_by | varchar(20) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.create_time | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.update_by | varchar(20) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.update_time | datetime / YES | 来源排序参考，不单独写合同业务列 | 逐字段以update_time＋补零id取最近非空值；原完整回款事实属于P24。；查询归并参考 | P15 |
| sms_ofst_contract_head_sap.effective_from | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.effective_to | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.import_batch_num | varchar(12) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.contract_create_date | datetime / YES | com_contract.contract_create_time | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.projectCode | varchar(80) / YES | com_contract.project_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.marketCode | varchar(80) / YES | com_contract.market_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.systemId | int(11) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.industryId | int(11) / YES | com_contract.industry_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.officeCode | varchar(80) / YES | com_contract.department_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.expendId | int(11) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.usernamec | varchar(10) / YES | com_contract.marketing_representative_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.latest_ship_date | datetime / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.usernamec2 | varchar(10) / YES | com_contract.secondary_representative_code | 按当前CONTRACT SQL先归并公司＋合同号；ERP客户冲突必须有批准裁决，补充字段仅单公司归属时按各字段最近非空值取值。fb_contract对项目/市场/办事处有COALESCE优先；完整表达式及更新白名单见本页SQL/配置。；P15当前正式合同映射 | P15 |
| sms_ofst_contract_head_sap.systemid_o | int(11) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.expendid_o | int(11) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.industry_name_o | varchar(40) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| sms_ofst_contract_head_sap.dataSource | varchar(25) / YES | P15不写该字段 | 当前合同SQL未将该原字段输出到合同步骤；订单本体见P14，原回款本体见P24，不能把其他任务目标误记为本任务写入。；其他任务/本任务未迁入 | P15 |
| fb_contract.contract_id | varchar(64) / YES | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P26 |
| fb_contract.contract_code | varchar(25) / YES | com_shipment_contract_reference.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.office_code | varchar(15) / YES | com_shipment_contract_reference.department_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.contract_type | int(11) / YES | com_shipment_contract_reference.contract_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.customer_name | varchar(512) / YES | com_shipment_contract_reference.customer_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.project_name | varchar(512) / YES | com_shipment_contract_reference.project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.warranty | varchar(2) / YES | com_shipment_contract_reference.warranty_years | 单位年，空白/NULL→NULL；非负整数直接承接，负数/小数/溢出阻断；不存布尔warranty_flag；业务字段/正式关系 | P26 |
| fb_contract.marketCode | varchar(10) / YES | com_shipment_contract_reference.market_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.marketName | varchar(15) / YES | com_shipment_contract_reference.market_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.systemId | int(11) / YES | com_shipment_contract_reference.system_source_key | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.systemName | varchar(15) / YES | com_shipment_contract_reference.system_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_contract.remark | varchar(4096) / YES | com_shipment_contract_reference.remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P26 |
| fb_shipment.packlist_id | varchar(64) / YES | com_shipment_package.package_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.con_id | varchar(64) / YES | com_shipment_package.shipment_contract_ref_id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.packdate | datetime / YES | com_shipment_package.shipment_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.warrantyStartTime | datetime / YES | com_shipment_package.warranty_start_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.warrantyEndTime | datetime / YES | com_shipment_package.warranty_end_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.receiveName | text / YES | com_shipment_package.receiver_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.emsNum | text / YES | com_shipment_package.express_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment.emsCompany | text / YES | com_shipment_package.carrier_code；com_shipment_package.carrier_name | 原码单独保存carrier_code；名称按fnd_basic_data快递公司词典精确解析carrier_name，两个属性不互相替代；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.contract_code | varchar(25) / YES | com_shipment_contract_reference.contract_no | 原con_id精确合同引用，视图不独立制造合同；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.receiveName | text / YES | com_shipment_package.receiver_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.emsNum | text / YES | com_shipment_package.express_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.packdate | datetime / YES | com_shipment_package.shipment_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.emsCompany | mediumtext / YES | com_shipment_package.carrier_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| view_shipment_ems_4_pm.packId | varchar(64) / YES | com_shipment_package.package_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P27 |
| fb_shipment_barcode.id | bigint(20) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P28 |
| fb_shipment_barcode.pack_id | varchar(64) / YES | ast_device_shipment.package_no；ast_device_shipment.legacy_package_key | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.item | varchar(16) / YES | ast_device_shipment.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.barcode | varchar(50) / YES | ast_device_shipment.device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.com_barcode | varchar(50) / YES | ast_device_shipment.internal_serial_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.rma_no | varchar(64) / YES | ast_device_shipment.rma_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.isRMA | int(11) / YES | ast_device_shipment.is_rma | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.item2 | varchar(16) / YES | ast_device_shipment.secondary_item | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.barcode2 | varchar(50) / YES | ast_device_shipment.secondary_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.orderNumber | varchar(32) / YES | ast_device_shipment.order_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.lineNum | int(11) / YES | ast_device_shipment.line_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.profitCenter | varchar(32) / YES | ast_device_shipment.profit_center | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.soleAgentSuffix | varchar(32) / YES | ast_device_shipment.sole_agent_suffix | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.warrantyStartDate | date / YES | ast_device_shipment.warranty_start_date | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.warrantyMonth | int(11) / YES | ast_device_shipment.warranty_months | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.rmaBarcode | varchar(50) / YES | ast_device_shipment.rma_related_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.updateTime | datetime / YES | ast_device_shipment.source_updated_at | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.syncTime | datetime / YES | ast_device_shipment.sync_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode.uuid | varchar(64) / YES | ast_device_shipment.uuid | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P28 |
| fb_shipment_barcode_relation.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P29 |
| fb_shipment_barcode_relation.sn1 | varchar(50) / YES | ast_device_relationship.source_device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.item1 | varchar(15) / YES | ast_device_relationship.source_item | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.sn2 | varchar(50) / YES | ast_device_relationship.target_device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.item2 | varchar(15) / YES | ast_device_relationship.target_item | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.contract | varchar(25) / YES | ast_device_relationship.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.createtime | varchar(50) / YES | ast_device_relationship.create_time；ast_device_relationship.effective_from | 保留原日期；正式操作者保留真实迁入人，不编造旧操作者；业务字段/正式关系 | P29 |
| fb_shipment_barcode_relation.updatetime | varchar(50) / YES | ast_device_relationship.update_time；ast_device_relationship.source_version | 原NULL取本条createtime，原版本字符串保持；65046限定纠正通过，360双方SN＋产品同值排除；业务字段/正式关系 | P29 |
