# P24 原表逐字段承载清单

合并组件：P24。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| sms_ofst_contract_head_sap.id | bigint(20) / NO | com_contract_receivable.source_record_key | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.contract_num | varchar(45) / YES | com_contract_receivable.contract_no | 空合同号按批准排除；唯一匹配才补contract_id，缺失/多义保留原号和可空引用。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.batch_code | varchar(10) / YES | com_contract_receivable.source_batch_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.project_name | varchar(200) / YES | com_contract_receivable.project_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.order_num | varchar(25) / YES | com_contract_receivable.source_order_no | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.client_supplier_code | varchar(20) / YES | com_contract_receivable.customer_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.client_supplier_name | varchar(200) / YES | com_contract_receivable.customer_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.contract_money_amount | decimal(20,2) / NO | com_contract_receivable.contract_amount | DECIMAL(20,2)无损保存，禁止静默舍入。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.delivered_money_amount | decimal(20,2) / NO | com_contract_receivable.delivered_amount | DECIMAL(20,2)无损保存，禁止静默舍入。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.collected_money_amount | decimal(20,2) / NO | com_contract_receivable.collected_amount | DECIMAL(20,2)无损保存，禁止静默舍入。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.collected_money_ratio | double / YES | com_contract_receivable.collected_ratio | DECIMAL(18,6)保留原单位，不截断到0～100。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.receivables_money_amount | decimal(20,2) / YES | com_contract_receivable.receivable_amount | DECIMAL(20,2)无损保存，禁止静默舍入。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.over_due_money_amount | decimal(20,2) / YES | com_contract_receivable.overdue_amount | DECIMAL(20,2)无损保存，禁止静默舍入。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.maketing_department_name | varchar(40) / YES | com_contract_receivable.market_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.office_name | varchar(20) / YES | com_contract_receivable.department_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.industry_name | varchar(40) / YES | com_contract_receivable.industry_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.marketing_representative_name | varchar(20) / YES | com_contract_receivable.marketing_representative_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.currency_name | varchar(25) / YES | com_contract_receivable.currency_name | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.create_by | varchar(20) / YES | com_contract_receivable.creator | 精确用户来源绑定优先，否则按批准原码保留；不造账号，不用管理员填NULL。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.create_time | datetime / YES | com_contract_receivable.create_time | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.update_by | varchar(20) / YES | com_contract_receivable.updater | 精确用户来源绑定优先，否则按批准原码保留；不造账号，不用管理员填NULL。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.update_time | datetime / YES | com_contract_receivable.update_time；com_contract_receivable.source_sync_time | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.effective_from | datetime / YES | com_contract_receivable.source_effective_from | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.effective_to | datetime / YES | com_contract_receivable.source_effective_to | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.import_batch_num | varchar(12) / YES | com_contract_receivable.import_batch_no | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.contract_create_date | datetime / YES | com_contract_receivable.contract_create_time | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.projectCode | varchar(80) / YES | com_contract_receivable.project_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.marketCode | varchar(80) / YES | com_contract_receivable.market_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.systemId | int(11) / YES | com_contract_receivable.system_source_key | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.industryId | int(11) / YES | com_contract_receivable.industry_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.officeCode | varchar(80) / YES | com_contract_receivable.department_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.expendId | int(11) / YES | com_contract_receivable.expend_source_key | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.usernamec | varchar(10) / YES | com_contract_receivable.marketing_representative_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.latest_ship_date | datetime / YES | com_contract_receivable.latest_ship_time | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.usernamec2 | varchar(10) / YES | com_contract_receivable.secondary_representative_code | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.systemid_o | int(11) / YES | com_contract_receivable.original_division_values.systemSourceKey | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.expendid_o | int(11) / YES | com_contract_receivable.original_division_values.expendSourceKey | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.industry_name_o | varchar(40) / YES | com_contract_receivable.original_division_values.industryName | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
| sms_ofst_contract_head_sap.dataSource | varchar(25) / YES | com_contract_receivable.source_system | 原业务值保持；由COM Owner核实已有关系并显式承接原审计。；业务字段/正式关系 | P24 |
