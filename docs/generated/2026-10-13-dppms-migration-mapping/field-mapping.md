# 原表全部字段到正式业务表的映射

基线：`e21f02d2d`及截至2026-10-12的成功回执；最新用户裁决优先。`—`明确表示没有完成的正式承接点。技术绑定/不可变来源材料不计业务承接。

原字段逐项列示；备注中的“待核对/待关联”没有提升为完成。源表中不存在的原审计字段不补造；无审计来源按该表批准的实际迁入审计初始化。

## 1. ehr_department

状态：**已实际承接（批准范围）**。8个补齐项中5项以EHR为主，另3项以fnd为主；完整24列裁决；16列不变

正式表：`system_dept`。

旧查询索引：L0429, L0430, L0431, L0432, L0433, L0434；原视图关系：—；迁移查询索引：M002, M009, M013, M014, M025；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| depID | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| depCode | varchar(20)/YES | system_dept.code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| depName | varchar(100)/YES | system_dept.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| depAbbr | varchar(100)/YES | 3/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| compID | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| adminID | int(11)/YES | system_dept.parent_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| depGrade | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depType | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depProperty | int(11)/YES | 0/8 | 【已批准不迁入】本8项全NULL；无可承接业务值，不新增字段。全历史出现非空需重新裁决。 | 业务字段/正式关系 |
| depCost | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| director | int(11)/YES | system_dept.leader_user_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| director2 | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depEmp | int(11)/YES | 2/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depNum | int(11)/YES | 0/8 | 【已批准不迁入】本8项全NULL，原部门人数无业务值。 | 业务字段/正式关系 |
| effectDate | datetime/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| xOrder | varchar(20)/YES | 0/8 | 【已批准不迁入】本8项全NULL，不编造排序；正式sort建议0。 | 业务字段/正式关系 |
| isDisabled | bit(1)/YES | system_dept.status | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| disabledDate | datetime/YES | 2/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| remark | varchar(500)/YES | 0/8 | 【已批准不迁入】本8项全NULL，既有正式表无备注列，不新增。 | 业务字段/正式关系 |
| depCustom1 | int(11)/YES | 0/8 | 【已批准不迁入】本8项全NULL；无业务值。 | 业务字段/正式关系 |
| depCustom2 | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depCustom3 | int(11)/YES | 0/8 | 【已批准不迁入】本8项全NULL；无业务值。 | 业务字段/正式关系 |
| depCustom4 | int(11)/YES | 8/8 | 【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。 | 业务字段/正式关系 |
| depCustom5 | int(11)/YES | 0/8 | 【已批准不迁入】本8项全NULL；无业务值。 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-system-dept-eight-missing-table-decision.md)。

运行/核验定位：`eight-department-completion-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 2. ehr_employee

状态：**引用核对；不独立迁移整表**。作为真实姓名、工号及账号身份外键核对；不表示EHR全部员工/任职属性已独立迁入

正式表：`system_users`。

旧查询索引：L0334, L0386, L0387, L0388, L0389, L0440, L0441, L0442, L0443, L0444, L0625, L0626, L0627, L0628；原视图关系：—；迁移查询索引：M001, M002, M003, M008, M009, M010；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| empID | int(11)/NO | system_users.custom_info.employeeReference | 精确员工引用；不复用旧员工ID作平台user_id | 业务字段/正式关系 |
| workNo | varchar(100)/NO | system_users.employee_no | 工号经ehr_login.empID精确员工外键核对；指定三账号姓名/工号按EHR，其他fnd主t补缺 | 业务字段/正式关系 |
| name | varchar(200)/YES | system_users.nickname | 工号经ehr_login.empID精确员工外键核对；指定三账号姓名/工号按EHR，其他fnd主t补缺 | 业务字段/正式关系 |
| eName | varchar(200)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| compID | int(11)/NO | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| depID | int(11)/NO | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| jobID | int(11)/NO | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| reportTo | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| wfreportTo | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empStatus | int(11)/NO | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| jobStatus | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empType | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| joinDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| workBeginDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| jobBeginDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| pracBeginDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| pracEndDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| probBeginDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| probEndDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| leaveDate | datetime/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| gender | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| email | varchar(500)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| mobile | varchar(50)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| officePhone | varchar(50)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| remark | varchar(100)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| disabled | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empCustom1 | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empCustom2 | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empCustom3 | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empCustom4 | varchar(50)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| empCustom5 | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-user-directory-reference-audit.md)。

## 3. ehr_login

状态：**引用核对；不独立迁移整表**。作为真实姓名、工号及账号身份外键核对；不表示EHR全部员工/任职属性已独立迁入

正式表：`system_users`。

旧查询索引：L0435, L0436, L0437, L0438, L0439, L0445, L0446；原视图关系：—；迁移查询索引：M001, M003, M008, M010, M014；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| title | varchar(255)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| account | varchar(255)/YES | system_users.username | 仅辅助精确账号身份核对，不单独创建ehr_login账号 | 业务字段/正式关系 |
| empID | int(11)/YES | system_users.employee_no/nickname的员工外键依据 | 通过ehr_employee外键取得真实工号/姓名，不能用登录冗余name覆盖员工名 | 业务字段/正式关系 |
| workNo | varchar(255)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| name | varchar(255)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |
| isDisabled | int(11)/YES | — | 仅作引用目录：未规划独立整表承接，不能把未读取/未使用属性视为已迁入 | 引用表未独立迁移 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-user-directory-reference-audit.md)。

## 4. fb_contract

状态：**已实际承接（批准范围）**。120638条引用及年数纠正核对；原额外1条无业务身份尚待处理，不记为整表无缺项完成

正式表：`com_shipment_contract_reference`。

旧查询索引：L0068, L0074, L0089, L0160, L0166, L0167, L0244, L0249, L0250, L0526, L0544, L0551, L0552, L0572, L0580；原视图关系：view_shipment_ems_4_pm, view_shipment_info_4_pm；迁移查询索引：M016；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| contract_id | varchar(64)/YES | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| contract_code | varchar(25)/YES | com_shipment_contract_reference.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| office_code | varchar(15)/YES | com_shipment_contract_reference.department_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contract_type | int(11)/YES | com_shipment_contract_reference.contract_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customer_name | varchar(512)/YES | com_shipment_contract_reference.customer_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| project_name | varchar(512)/YES | com_shipment_contract_reference.project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warranty | varchar(2)/YES | com_shipment_contract_reference.warranty_years | 单位年，空白/NULL→NULL；非负整数直接承接，负数/小数/溢出阻断；不存布尔warranty_flag | 业务字段/正式关系 |
| marketCode | varchar(10)/YES | com_shipment_contract_reference.market_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| marketName | varchar(15)/YES | com_shipment_contract_reference.market_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| systemId | int(11)/YES | com_shipment_contract_reference.system_source_key | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| systemName | varchar(15)/YES | com_shipment_contract_reference.system_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| remark | varchar(4096)/YES | com_shipment_contract_reference.remark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-shipment-contract-reference-table-decision.md)。

运行/核验定位：`approved-contract-years-sync-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 5. fb_items

状态：**已完成可核实原记录承接；缺项另列**。2026-10-13已有8418正式产品原标签承接，8314更新/104未变化，重放8418未变化；原21列保护、新增0；另26558缺目录未创建。

正式表：`ast_product_official_info`、`plt_entity_extension_value`。

旧查询索引：L0068, L0074, L0082, L0083, L0089, L0109, L0110, L0112, L0115, L0128, L0164, L0196, L0197, L0247, L0293, L0303, L0526, L0527, L0528, L0535, L0536, L0537, L0538, L0546, L0547, L0548, L0549, L0551, L0552, L0556, L0570, L0571, L0577, L0579；原视图关系：view_shipment_info_4_pm；迁移查询索引：M015, M033；原材料采集索引：C035。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/YES | 不迁入 | 用户明确自增ID没有产品业务意义，产品唯一键为租户ID＋产品编码 | 已批准排除 |
| item | varchar(25)/YES | ast_product_official_info.product_code | 租户＋编码唯一；不按旧id关联产品 | 业务字段/正式关系 |
| describe_ | varchar(255)/YES | ast_product_official_info.product_name/product_desc ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productName | 新5编码填写正式名称/描述；已有99保CRM名称，来源不同名称通过正式扩展保留 | 业务字段/正式关系 |
| itemname | varchar(255)/YES | ast_product_official_info.product_model ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productModel | 既有CRM主档保持；DPPMS完整目录标签由正式扩展承接 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-official-product-table-decision.md)。

运行/核验定位：`approved-product-catalog-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 6. fb_service

状态：**已实际承接（批准范围）**。新增10个多合同用服项目222条，加此前单条验收，共223条；全部原字段准确承接并完成重放、正式页面验收。完整462,213条设计适用，不代表已经全量写入。

正式表：`ast_device_warranty_renewal_record`，用户批准的17列。原维保记录149条及当前投影31条保持；等级目录、人工变更和当前计算仍单列，不混同为续保记录完成。

原SQL依据：实际`view_warranty_source`与每日固化过程，见[原计算SQL](sql/warranty-original-calculation.sql)。正式任务`2110010787726274561`使用可编辑、参数化完整查询；[整表裁决](../2026-10-13-dppms-renewal-and-warranty-table-proposal.md)及[实际验证报告](../2026-10-13-dppms-renewal-multi-contract-validation.md)。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | varchar(64)/YES | int_sync_binding.source_key | 与原SN组成复合技术身份，保留同ID不同SN；正式记录ID由Owner生成 | 技术绑定 |
| con_xb | varchar(25)/YES | ast_device_warranty_renewal_record.warranty_contract_no | 原续保合同号原样保存，不替换项目销售合同关系 | 正式字段承接 |
| barcode | varchar(50)/YES | ast_device_warranty_renewal_record.device_sn | 原SN原样保存；可为空且不强制设备主档外键，空SN不参与项目/设备查询 | 正式字段承接 |
| grade | varchar(15)/YES | ast_device_warranty_renewal_record.warranty_grade | 保存原等级编码；等级目录的完整承接仍单列待处理 | 正式字段承接 |
| begin_date | datetime/YES | ast_device_warranty_renewal_record.warranty_start_date | 实际开始日期原值；原非零时分秒阻断，不能无声截断 | 正式字段承接 |
| end_date | datetime/YES | ast_device_warranty_renewal_record.warranty_end_date | 实际结束日期原值，不以参考月数重算 | 正式字段承接 |
| warranty | char(1)/YES | ast_device_warranty_renewal_record.warranty_months | 原年数乘12；空值/空串为NULL，0保留0，仅作参考 | 正式字段承接 |
| remark | text/YES | ast_device_warranty_renewal_record.remark | 原TEXT及NULL原样保存 | 正式字段承接 |
| isyb | int(11)/YES | ast_device_warranty_renewal_record.extended | 1为true，0为false，NULL保持；不据此生成当前完成事实 | 正式字段承接 |
| state | int(11)/YES | ast_device_warranty_renewal_record.is_latest | 1为true，NULL保持；仅原最新选择标记，不是当前在保状态 | 正式字段承接 |

运行/核验定位：`renewal-ten-formal-fields-verification-20261013.json`，重放运行`2110024910384070658`为222条UNCHANGED；此前单条重放`2110016692773629954`为1条UNCHANGED。

## 7. fb_shipment

状态：**已承接10项目；全历史未执行**。19装箱单全部8原字段核对及重放；最终20列含carrier_code；物流名称/编码分别保存

正式表：`com_shipment_package`。

旧查询索引：L0068, L0074, L0089, L0160, L0166, L0167, L0244, L0249, L0250, L0526, L0544, L0551, L0552, L0572, L0580；原视图关系：view_shipment_ems_4_pm, view_shipment_info_4_pm；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C026。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| packlist_id | varchar(64)/YES | com_shipment_package.package_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| con_id | varchar(64)/YES | com_shipment_package.shipment_contract_ref_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| packdate | datetime/YES | com_shipment_package.shipment_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warrantyStartTime | datetime/YES | com_shipment_package.warranty_start_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warrantyEndTime | datetime/YES | com_shipment_package.warranty_end_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| receiveName | text/YES | com_shipment_package.receiver_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| emsNum | text/YES | com_shipment_package.express_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| emsCompany | text/YES | com_shipment_package.carrier_code/carrier_name | 原码单独保存carrier_code；名称按fnd_basic_data快递公司词典精确解析carrier_name，两个属性不互相替代 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-shipment-package-table-decision.md)。

运行/核验定位：`shipment-package-final-approved-state-20261011.json`（任务私有证据目录；若为md则位于上级generated）。

## 8. fb_shipment_barcode

状态：**已承接10项目；全历史未执行**。1284本批有值行正式字段核对；全表4,194,867行7个镜像列扫描/清理完成，不等于全历史逐业务字段验收

正式表：`ast_device_shipment`。

旧查询索引：L0068, L0074, L0089, L0526, L0544, L0551, L0552, L0580；原视图关系：view_shipment_info_4_pm；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C025。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | bigint(20)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| pack_id | varchar(64)/YES | ast_device_shipment.package_no/legacy_package_key | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| item | varchar(16)/YES | ast_device_shipment.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| barcode | varchar(50)/YES | ast_device_shipment.device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| com_barcode | varchar(50)/YES | ast_device_shipment.internal_serial_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| rma_no | varchar(64)/YES | ast_device_shipment.rma_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| isRMA | int(11)/YES | ast_device_shipment.is_rma | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| item2 | varchar(16)/YES | ast_device_shipment.secondary_item | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| barcode2 | varchar(50)/YES | ast_device_shipment.secondary_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderNumber | varchar(32)/YES | ast_device_shipment.order_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| lineNum | int(11)/YES | ast_device_shipment.line_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| profitCenter | varchar(32)/YES | ast_device_shipment.profit_center | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| soleAgentSuffix | varchar(32)/YES | ast_device_shipment.sole_agent_suffix | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warrantyStartDate | date/YES | ast_device_shipment.warranty_start_date | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warrantyMonth | int(11)/YES | ast_device_shipment.warranty_months | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| rmaBarcode | varchar(50)/YES | ast_device_shipment.rma_related_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| updateTime | datetime/YES | ast_device_shipment.source_updated_at | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| syncTime | datetime/YES | ast_device_shipment.sync_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| uuid | varchar(64)/YES | ast_device_shipment.uuid | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-ast-device-shipment-table-decision.md)。

运行/核验定位：`verified-shipment-mirror-final-schema-20261010T005009150311.json`（任务私有证据目录；若为md则位于上级generated）。

## 9. fb_shipment_barcode_relation

状态：**已实际承接（批准范围）**。65406来源→65046正式关系＋360已批准自关联排除；限定日期纠正、重放与原8/正式19列核对完成

正式表：`ast_device_relationship`。

旧查询索引：L0112, L0115, L0119, L0128, L0164, L0293, L0303, L0548, L0549, L0551, L0552, L0553, L0556, L0570, L0571, L0577；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C030。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| sn1 | varchar(50)/YES | ast_device_relationship.source_device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| item1 | varchar(15)/YES | ast_device_relationship.source_item | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| sn2 | varchar(50)/YES | ast_device_relationship.target_device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| item2 | varchar(15)/YES | ast_device_relationship.target_item | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contract | varchar(25)/YES | ast_device_relationship.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| createtime | varchar(50)/YES | ast_device_relationship.create_time/effective_from | 保留原日期；正式操作者保留真实迁入人，不编造旧操作者 | 业务字段/正式关系 |
| updatetime | varchar(50)/YES | ast_device_relationship.update_time/source_version | 原NULL取本条createtime，原版本字符串保持；65046限定纠正通过，360双方SN＋产品同值排除 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-device-relationship-table-decision.md)。

运行/核验定位：`2026-10-12-dppms-device-relationship-audit-correction-decision.md`（任务私有证据目录；若为md则位于上级generated）。

## 10. fb_soft_version

状态：**已承接10项目；全历史未执行**。19正式版本记录，主档冲突不覆盖；全历史重复SN的完整保留规则已批准

正式表：`ast_device_factory_version_record`、`ast_device_factory_version`。

旧查询索引：L0073, L0074, L0082, L0083, L0089, L0115, L0116, L0201, L0202, L0525, L0526, L0535, L0536, L0549, L0550；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C031。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| serial_number | varchar(100)/YES | ast_device_factory_version_record.device_sn | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本 | 业务字段/正式关系 |
| conp | varchar(100)/YES | ast_device_factory_version_record.conp_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本 | 业务字段/正式关系 |
| cpld | varchar(100)/YES | ast_device_factory_version_record.cpld_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本 | 业务字段/正式关系 |
| boot | varchar(100)/YES | ast_device_factory_version_record.boot_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本 | 业务字段/正式关系 |
| pcb | varchar(100)/YES | ast_device_factory_version_record.pcb_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-factory-version-record-table-decision.md)。

运行/核验定位：`factory-record-approved-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 11. fnd_act_hi_comment

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0001, L0046, L0146, L0288, L0290, L0291, L0310, L0312, L0325, L0344, L0351, L0353, L0354, L0356, L0564, L0585, L0587, L0588, L0597, L0598, L0613, L0616, L0617；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C049。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| objId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| procdefKey | varchar(50)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskKey | varchar(50)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskId | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instId | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| assignee | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| assigneeTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| nextAssignee | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| nextAssigneeName | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| result | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| message | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 12. fnd_basic_data

状态：**已实际承接（批准范围）**。532来源→531新增、1无业务项排除；重放531未变化；既有14列不变

正式表：`system_dict_data`。

旧查询索引：L0001, L0023, L0024, L0025, L0027, L0028, L0034, L0035, L0036, L0040, L0046, L0052, L0053, L0054, L0057, L0058, L0059, L0061, L0062, L0063, L0065, L0067, L0068, L0069, L0070, L0071, L0072, L0074, L0075, L0076, L0081, L0085, L0086, L0087, L0088, L0089, L0090, L0091, L0095, L0097, L0107, L0123, L0124, L0125, L0127, L0129, L0130, L0131, L0132, L0133, L0134, L0151, L0152, L0153, L0155, L0159, L0160, L0166, L0167, L0168, L0172, L0173, L0177, L0178, L0180, L0183, L0184, L0194, L0209, L0210, L0211, L0213, L0215, L0216, L0217, L0218, L0235, L0236, L0237, L0239, L0243, L0244, L0249, L0250, L0251, L0254, L0255, L0258, L0262, L0288, L0290, L0291, L0292, L0294, L0301, L0302, L0310, L0311, L0312, L0317, L0318, L0321, L0322, L0326, L0330, L0331, L0332, L0335, L0336, L0337, L0338, L0340, L0341, L0346, L0347, L0351, L0353, L0354, L0355, L0356, L0357, L0358, L0359, L0360, L0361, L0362, L0363, L0366, L0367, L0368, L0369, L0370, L0372, L0373, L0374, L0376, L0379, L0381, L0394, L0396, L0397, L0398, L0399, L0400, L0402, L0403, L0420, L0425, L0427, L0515, L0516, L0517, L0519, L0521, L0522, L0523, L0524, L0526, L0529, L0530, L0534, L0539, L0540, L0541, L0542, L0543, L0544, L0555, L0557, L0572, L0573, L0575, L0581, L0585, L0587, L0588, L0589, L0592, L0593, L0597, L0598, L0604, L0605, L0606, L0607, L0608, L0609, L0614, L0619, L0620, L0621, L0623, L0624, L0630, L0631；原视图关系：view_shipment_ems_4_pm；迁移查询索引：M021, M022；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| dataTypeCode | varchar(45)/YES | system_dict_data.dict_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| basicDataId | varchar(255)/YES | system_dict_data.value | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| basicDataName | varchar(255)/YES | system_dict_data.label | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| basicDataAttri1 | varchar(255)/YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列 | 已批准排除 |
| sortId | int(11)/YES | system_dict_data.sort | NULL取0；原升序直接使用；ID114无有效项明确不迁入 | 业务字段/正式关系 |
| createTime | datetime/YES | system_dict_data.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | system_dict_data.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | system_dict_data.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | system_dict_data.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列 | 已批准排除 |
| effectiveTo | datetime/YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列 | 已批准排除 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-system-dictionary-table-proposal.md)。

运行/核验定位：`dictionary-data-applied-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 13. fnd_basic_data_type

状态：**已实际承接（批准范围）**。31原定义＋49缺失分组=80；重放UNCHANGED80；既有11列不变

正式表：`system_dict_type`。

旧查询索引：L0025, L0026, L0027；原视图关系：—；迁移查询索引：M021；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| dataTypeCode | varchar(45)/YES | system_dict_type.type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| dataTypeName | varchar(45)/YES | system_dict_type.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| status | int(11)/YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入 | 已批准排除 |
| createTime | datetime/YES | system_dict_type.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | system_dict_type.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | system_dict_type.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | system_dict_type.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入 | 已批准排除 |
| effectiveTo | datetime/YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入 | 已批准排除 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-system-dictionary-table-proposal.md)。

运行/核验定位：`dictionary-type-approved-applied-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 14. fnd_department

状态：**已实际承接（批准范围）**。原28＋后续8中的3项补齐；2026-10-13另补163130一项并重放；补部门不代表授组织范围。

正式表：`system_dept`。

旧查询索引：L0014, L0015, L0016, L0017, L0022, L0052, L0053, L0054, L0056, L0063, L0065, L0069, L0074, L0075, L0076, L0082, L0083, L0089, L0095, L0097, L0102, L0103, L0104, L0127, L0146, L0167, L0168, L0172, L0173, L0177, L0178, L0183, L0184, L0189, L0190, L0191, L0213, L0230, L0250, L0251, L0254, L0255, L0256, L0259, L0263, L0266, L0268, L0269, L0270, L0271, L0276, L0277, L0279, L0288, L0290, L0291, L0292, L0301, L0302, L0307, L0308, L0311, L0312, L0317, L0319, L0320, L0321, L0322, L0323, L0324, L0326, L0327, L0330, L0331, L0332, L0334, L0340, L0341, L0344, L0346, L0348, L0351, L0353, L0354, L0356, L0357, L0358, L0359, L0360, L0361, L0362, L0366, L0367, L0368, L0369, L0370, L0398, L0399, L0400, L0501, L0502, L0505, L0515, L0516, L0517, L0519, L0521, L0522, L0526, L0527, L0528, L0529, L0530, L0541, L0542, L0543, L0544, L0545, L0551, L0552, L0555, L0564, L0572, L0573, L0581, L0582, L0585, L0587, L0588, L0592, L0593, L0594, L0595, L0598, L0604, L0605, L0606, L0607, L0608, L0610, L0611, L0612, L0614, L0615, L0616, L0617, L0619, L0620, L0621；原视图关系：—；迁移查询索引：M013, M014, M025；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| departmentNum | varchar(20)/NO | system_dept.code/parent_id | 16开头父160000，31开头父310000，280700父280000，999999根；311202—311210显式转162102—162110，不截断猜配 | 业务字段/正式关系 |
| departmentName | varchar(20)/NO | system_dept.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| isparam | int(11)/YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性 | 已批准排除 |
| status | int(11)/NO | system_dept.status | 来源1→启用0、0→停用1；指定两码按EHR停用；已有目录不覆盖 | 业务字段/正式关系 |
| createTime | datetime/YES | system_dept.create_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性 | 已批准排除 |
| effectiveTo | datetime/YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性 | 已批准排除 |
| updateTime | datetime/YES | system_dept.update_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-system-dept-table-decision.md)。

运行/核验定位：`approved-department-migration-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 15. fnd_roles

状态：**已实际承接（批准范围）**。31条来源角色绑定，25新角色＋6复用；停用不迁；13角色25处NULL审计已纠正

正式表：`system_role`。

旧查询索引：L0002, L0012, L0013, L0021, L0499, L0500, L0594；原视图关系：—；迁移查询索引：M005；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(6)/NO | system_role.code ＋ 来源绑定 | 按已裁决常量语义转换小驼峰字符串，复用DppmsRoleCodes.FND_CODES；绝不用原ID或常量名作为角色编码 | 技术身份及受控角色编码转换 |
| roleName | varchar(64)/NO | system_role.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| defaultPage | varchar(255)/YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接 | 已批准排除 |
| status | int(1)/NO | system_role.status | 停用来源角色不迁；启用1→正式0；超级管理员明确关联平台超级管理员角色 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接 | 已批准排除 |
| effectiveTo | datetime/YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接 | 已批准排除 |
| createBy | varchar(25)/YES | system_role.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createTime | datetime/YES | system_role.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | system_role.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | system_role.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| roleRemark | varchar(200)/YES | system_role.remark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-role-identity-mapping-decision.md)。

运行/核验定位：`approved-role-migration-verification-2108559110330822657.json`（任务私有证据目录；若为md则位于上级generated）。

## 16. fnd_user_info

状态：**已实际承接（批准范围）**。合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行

正式表：`system_users`、`system_user_company_department_scope`、`system_user_role`。

旧查询索引：L0001, L0003, L0004, L0005, L0006, L0007, L0008, L0009, L0010, L0011, L0018, L0019, L0020, L0021, L0029, L0030, L0031, L0032, L0033, L0037, L0038, L0039, L0046, L0052, L0053, L0054, L0065, L0069, L0071, L0072, L0075, L0076, L0077, L0078, L0080, L0081, L0084, L0085, L0086, L0088, L0090, L0092, L0093, L0101, L0133, L0134, L0137, L0138, L0139, L0153, L0155, L0165, L0172, L0173, L0177, L0181, L0182, L0188, L0218, L0221, L0222, L0223, L0237, L0239, L0248, L0254, L0255, L0259, L0263, L0288, L0290, L0291, L0292, L0294, L0307, L0310, L0311, L0315, L0320, L0323, L0324, L0325, L0326, L0330, L0331, L0332, L0334, L0336, L0338, L0351, L0353, L0354, L0355, L0356, L0494, L0495, L0496, L0497, L0498, L0503, L0504, L0506, L0507, L0508, L0509, L0510, L0511, L0512, L0515, L0516, L0517, L0521, L0522, L0524, L0529, L0530, L0531, L0533, L0534, L0539, L0558, L0559, L0560, L0581, L0585, L0587, L0588, L0589, L0594, L0597, L0598, L0611, L0612, L0613, L0614, L0616, L0617；原视图关系：—；迁移查询索引：M003, M005, M007, M010, M014, M017, M019, M026, M027, M028, M029, M036, M037；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(8)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| username | varchar(128)/NO | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING | 业务字段/正式关系 |
| password | varchar(32)/NO | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则 | 已批准排除 |
| email | varchar(128)/NO | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。；USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS | 业务字段/正式关系 |
| dpNo | varchar(25)/YES | system_user_company_department_scope.department_id/code/name | 自身部门加入可服务/范围集合；不是工号，没有公司事实时不伪配公司。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| realName | varchar(128)/NO | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| roleIds | varchar(64)/YES | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| isemail | int(11)/YES | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| status | int(1)/YES | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION | 业务字段/正式关系 |
| defaultPage | varchar(255)/YES | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| pwdoverdue | datetime/YES | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| createBy | varchar(25)/YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createTime | datetime/YES | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| effectiveTo | datetime/YES | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| customInfo | json/YES | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-system-users-table-decision.md)。

运行/核验定位：`approved-user-migration-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 17. fnd_user_power

状态：**已实际承接（批准范围）**。9492范围绑定→9477新增正式范围；空范围、指定账号及范围排除按裁决；不直接赋办事处全部项目查看权

正式表：`system_user_company_department_scope`。

旧查询索引：L0003, L0005, L0032, L0259, L0263, L0494, L0495, L0496, L0508；原视图关系：—；迁移查询索引：M012；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| fndUserId | int(11)/YES | system_user_company_department_scope.user_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| username | varchar(25)/YES | 不用于关联 | 以fndUserId外键为准；冗余名称差异列明，不另关联账号 | 已批准不采用冗余值 |
| areapower | varchar(4096)/YES | system_user_company_department_scope.department_id/department_code | 跨公司限定001、002；空areapower109条明确排除：空范围没有附加授权事实；konglinghui_supp范围不迁；内部外部仅按项目授予查看权 | 业务字段/正式关系 |
| createTime | datetime/YES | system_user_company_department_scope.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(25)/YES | system_user_company_department_scope.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | system_user_company_department_scope.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | system_user_company_department_scope.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | system_user_company_department_scope.effective_from | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveTo | datetime/YES | system_user_company_department_scope.effective_to | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-user-company-department-scope-table-decision.md)。

运行/核验定位：`verified-user-scope-apply-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 18. pm_cl_callback

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0041, L0047, L0146, L0153, L0154, L0155, L0230, L0237, L0238, L0239, L0323, L0325, L0344, L0564, L0612, L0613, L0616, L0617；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C013。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instId | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| remark | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| applyState | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| applyBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| applyTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | timestamp/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveFrom | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveTo | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 19. pm_cl_callback_quesnaire

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0041, L0042, L0043, L0045, L0046, L0047, L0146, L0230, L0344, L0564；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C043。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| callBackId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskId | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireVersion | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireState | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveFrom | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveTo | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 20. pm_cl_quesnaire_result_header

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0044, L0105, L0146, L0192, L0230, L0344, L0564；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C044。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11) unsigned/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| evaluationHeaderId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireTemplateHeaderId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesMarkScore | double/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesMarkResult | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| status | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 21. pm_cl_quesnaire_result_line

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0106, L0146, L0193, L0230, L0344, L0564；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C045。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11) unsigned/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireTemplateHeaderId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireTemplateLineId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionTemplateOptId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionAnswer | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionScore | double/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireResultHeaderId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesTypeForCB | varchar(10)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesEvaResult | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 22. pm_cl_quesnaire_template_header

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0091, L0180, L0540；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C046。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(10) unsigned/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionnaireTemplateNum | varchar(45)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionnaireTemplateName | varchar(200)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionnaireScore | double/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionnairePassScore | double/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionnaireStatus | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveStartTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveEndTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesType | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| markIndexs | varchar(45)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 23. pm_cl_quesnaire_template_line

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0121, L0146, L0207, L0230, L0344, L0564；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C047。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11) unsigned/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionContent | varchar(200)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionType | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionScore | double/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionRemark | varchar(200)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionNum | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireTemplateHeaderId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionStatus | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveStartTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveEndTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionTypeForCB | varchar(10)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 24. pm_cl_quesnaire_template_options

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0122, L0208, L0564；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C048。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11) unsigned/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionOptionNum | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionOptionsContent | varchar(200)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionOptionScore | double/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireTemplateHeaderId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveStartTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveEndTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createdPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updatedPerson | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesLineType | varchar(10)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 25. pm_order_data_from_erp

状态：**已完成可核实原记录承接；缺项另列**。2026-10-13已有正式订单字段承接91161来源，91060正式订单改变且无新建/删除；重放91161未变化，411冲突来源保持待裁决。

正式表：`com_sales_order`。

旧查询索引：本地未检出直接SELECT；不能用生成的审查语句冒称原查询；原视图关系：—；迁移查询索引：M032；原材料采集索引：C019。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| orderNumber | varchar(25)/YES | com_sales_order.order_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contractNo | varchar(50)/YES | com_sales_order.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderExecNumber | varchar(50)/YES | com_sales_order.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderCreateTime | datetime/YES | com_sales_order.order_create_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customerRequireTime | datetime/YES | com_sales_order.customer_required_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customerCode | varchar(55)/YES | com_sales_order.customer_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customerName | varchar(255)/YES | com_sales_order.customer_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectName | varchar(255)/YES | com_sales_order.source_project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderComment | varchar(2048)/YES | com_sales_order.order_comment | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderType | int(11)/YES | com_sales_order.order_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| compCode | varchar(25)/YES | com_sales_order.company_code/company_id | 01→001、02→002、03→003、04→004；未知码阻断；按租户＋公司＋系统＋业务键匹配 | 业务字段/正式关系 |
| salesType | varchar(25)/YES | com_sales_order.sales_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| source | varchar(25)/YES | com_sales_order.source_system | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customInfo | json/YES | com_sales_order.custom_info | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| syncTime | datetime/YES | com_sales_order.source_updated_at | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-sales-order-table-decision.md)。

运行/核验定位：`order-fields-correction-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 26. pm_order_line_from_erp

状态：**已承接10项目；全历史未执行**。截至2026-10-12试点105行已承接；2026-10-13整表预演仍未正式写入：旧目标缺失236259、冲突/父单缺失等待处理，候选数不算迁入数。

正式表：`com_sales_order_line`。

旧查询索引：本地未检出直接SELECT；不能用生成的审查语句冒称原查询；原视图关系：—；迁移查询索引：M032；原材料采集索引：C021。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| orderNumber | varchar(25)/YES | com_sales_order_line.order_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| lineNum | varchar(25)/YES | com_sales_order_line.line_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemCode | varchar(25)/YES | com_sales_order_line.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemDesc | varchar(255)/YES | com_sales_order_line.product_desc | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderQuantity | int(11)/YES | com_sales_order_line.order_qty | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| openQuantity | int(11)/YES | com_sales_order_line.open_qty | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| bundleCode | varchar(25)/YES | com_sales_order_line.bundle_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| warrantyMonth | int(11)/YES | com_sales_order_line.warranty_month | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| lineType | int(11)/YES | com_sales_order_line.line_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| compCode | varchar(25)/YES | com_sales_order_line.company_code/company_id | 01→001、02→002、03→003、04→004；未知码阻断；按租户＋公司＋系统＋业务键匹配 | 业务字段/正式关系 |
| profitCenter | varchar(25)/YES | com_sales_order_line.profit_center | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| realOrderExecNumber | varchar(25)/YES | com_sales_order_line.real_execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| source | varchar(25)/YES | com_sales_order_line.source_system | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customInfo | json/YES | com_sales_order_line.custom_info | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| syncTime | datetime/YES | com_sales_order_line.source_updated_at | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-sales-order-line-table-decision.md)。

运行/核验定位：`order-fields-correction-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 27. pm_pb_plan_from_sms

状态：**原试点完成；续办扩大写入待范围处置**。2026-10-13续办正式计划5330＝原10＋4353＋967，重放5330未变化；30171缺审计账号待裁决；扩大范围相关写入待核定。

正式表：`com_contract_collection_plan`。

旧查询索引：L0107, L0167, L0194, L0250, L0572；原视图关系：—；迁移查询索引：M019, M036, M037；原材料采集索引：C022。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| contractNo | varchar(25)/YES | com_contract_collection_plan.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| batchCode | varchar(10)/YES | com_contract_collection_plan.batch_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| basicDataName | varchar(20)/YES | com_contract_collection_plan.collection_item_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| referenceEventName | varchar(20)/YES | com_contract_collection_plan.reference_event_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| eventPlanHappenDate | datetime/YES | com_contract_collection_plan.event_planned_at | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| afterDaysNum | int(11)/YES | com_contract_collection_plan.after_days | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| eventActualFinishDate | datetime/YES | com_contract_collection_plan.event_actual_finished_at | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| marketingFeedback | varchar(2000)/YES | com_contract_collection_plan.marketing_feedback | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | com_contract_collection_plan.creator | 可核实人员关联user_id；CrmInterfaceUser/crmapp及已批准l01663保留原编码；历史日期不变 | 业务字段/正式关系 |
| createTime | datetime/YES | com_contract_collection_plan.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | com_contract_collection_plan.updater | 可核实人员关联user_id；CrmInterfaceUser/crmapp及已批准l01663保留原编码；历史日期不变 | 业务字段/正式关系 |
| updateTime | datetime/YES | com_contract_collection_plan.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | com_contract_collection_plan.effective_from | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveTo | datetime/YES | com_contract_collection_plan.effective_to | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| dataSource | varchar(25)/YES | com_contract_collection_plan.data_source | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-contract-collection-plan-table-decision.md)。

运行/核验定位：`collection-whole-task-upgrade-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 28. pm_project

状态：**原试点完成；续办扩大写入待范围处置**。2026-10-13续办实际新增6570主档（不在已确认迁入范围，已停止待处置）；加原10共6580规范主档，仍非80170全量。不能计作批准完成。

正式表：`proj_project`、`proj_project_party`。

旧查询索引：L0342, L0343, L0363, L0364, L0365, L0368, L0369, L0374, L0375, L0376, L0377, L0378, L0379, L0380, L0381, L0382, L0383, L0384, L0391, L0392, L0393, L0394, L0395, L0396, L0397, L0398, L0399, L0400, L0401, L0404, L0405, L0406, L0407, L0412, L0413, L0414, L0415, L0416, L0428, L0562, L0618, L0621, L0629, L0632, L0633；原视图关系：pm_project_header；迁移查询索引：M018, M020, M023, M024, M026, M027, M028, M029, M034, M035；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectId | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectType | varchar(45)/NO | proj_project.project_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectCode | varchar(45)/NO | proj_project.project_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectName | varchar(200)/YES | proj_project.project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectState | varchar(11)/YES | proj_project.lifecycle_status/current_stage/closed_at | 按类型及原状态已批准初始化：用服100正常闭环S6、20异常闭环、30/31/32沿规则；关闭时间缺失保持NULL，不生成任务/审批完成 | 业务字段/正式关系 |
| isback | varchar(11)/YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接 | 已批准排除 |
| column001 | varchar(255)/YES | proj_project.department_id/department_code/department_name | 精确正式目录；原NULL可保留，未知非空编码阻断；不授区域全部项目权限 | 业务字段/正式关系 |
| column002 | varchar(255)/YES | com_sales_order.customer_code/customer_id | 仅经正式合同/订单链精确核对；不加项目ERP客户镜像，不能据名称猜匹配 | 业务字段/正式关系 |
| column003 | varchar(255)/YES | com_sales_order.customer_name | 同一精确订单客户事实，不替代最终用户；全历史链路尚未完成逐行验证 | 业务字段/正式关系 |
| column004 | varchar(255)/YES | proj_project.market_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column005 | varchar(255)/YES | proj_project.system_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column006 | varchar(255)/YES | proj_project.expend_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column007 | varchar(255)/YES | proj_project.industry_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column008 | varchar(255)/YES | proj_project.not_track_reason | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column009 | datetime/YES | proj_project.order_create_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column010 | varchar(10)/YES | proj_project.project_category | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column011 | varchar(10)/YES | proj_project.signing_method | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column012 | varchar(2)/YES | proj_project.implementation_mode | 原空NULL/空串优先Readonly回退；0/1/3沿既有转换，2=DIRECT_SUBCONTRACT、4=FACTORY_INTEGRATION，两项均承接 | 业务字段/正式关系 |
| columno12_readonly | int(2)/YES | proj_project.implementation_mode（仅主值缺失时） | 用户明确同名Readonly回退；不存第二份控制字段 | 业务字段/正式关系 |
| column013 | varchar(255)/YES | proj_project_party.party_name（party_role=FINAL_CUSTOMER） | 实际最终产品用户，经PROJ相关方Owner承接；不覆盖购货方 | 业务字段/正式关系 |
| column014 | text/YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接 | 已批准排除 |
| customerProjectName | varchar(255)/YES | proj_project.customer_project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| salesType | varchar(25)/YES | proj_project.sales_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| majorProjectLevel | varchar(255)/YES | proj_project.major_project_level | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| compId | int(2)/YES | proj_project.company_id/company_code/company_name | 原公司外键→实际公司编码→01/02/03/04转001/002/003/004；不将旧ID当新ID，原NULL按裁决保留 | 业务字段/正式关系 |
| createTime | datetime/YES | proj_project.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | proj_project.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | proj_project.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | proj_project.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | proj_project.effective_from | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveTo | datetime/YES | proj_project.effective_to | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| disabled | bit(1)/YES | proj_project.disabled | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectStartTime | datetime/YES | proj_project.project_start_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectRefreshTime | datetime/YES | proj_project.project_refresh_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectCloseTime | datetime/YES | proj_project.project_close_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customInfo | json/YES | proj_project.custom_info | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customConfig | json/YES | — | 已审查来源全NULL，没有实际业务值；全历史遇非空无批准同义承接点时阻断 | 无值未落业务字段 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-proj-project-table-decision.md)。

运行/核验定位：`project-identity-task-pilot-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 29. pm_project_contract

状态：**原试点完成；续办扩大写入待范围处置**。2026-10-13续办6480来源已重放，新增6462正式关系/6470绑定；依赖扩大项目范围的写入待核定处置，不计已批准全历史完成。

正式表：`com_project_contract_relation`。

旧查询索引：L0056, L0074, L0089, L0097, L0119, L0127, L0145, L0160, L0163, L0166, L0167, L0178, L0184, L0213, L0229, L0244, L0246, L0249, L0250, L0256, L0258, L0262, L0301, L0302, L0343, L0346, L0347, L0348, L0357, L0358, L0359, L0360, L0361, L0362, L0394, L0398, L0399, L0526, L0527, L0528, L0535, L0536, L0543, L0544, L0551, L0552, L0553, L0555, L0572, L0573, L0582, L0592, L0593, L0604, L0605, L0606, L0607；原视图关系：—；迁移查询索引：M020, M023, M024, M034, M035；原材料采集索引：C003。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| contractNo | varchar(45)/NO | com_project_contract_relation.contract_id | 公司＋原合同号精确解析正式合同，禁止同号跨公司任选 | 业务字段/正式关系 |
| projectGroupCode | varchar(45)/NO | com_project_contract_relation.project_id/contract_id | 原组→成员→同类型编码MAX(projectId)→正式项目，展开合同关系；旧组不是新模型权威 | 业务字段/正式关系 |
| createTime | datetime/YES | com_project_contract_relation.create_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | com_project_contract_relation.creator | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| updateTime | datetime/YES | com_project_contract_relation.update_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | com_project_contract_relation.updater | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-project-contract-relation-table-proposal.md)。

运行/核验定位：`contract-whole-task-upgrade-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 30. pm_project_group

状态：**原试点完成；续办扩大写入待范围处置**。只作类型/关联展开，不建组实体；续办扩大关系写入的范围问题仍待处理，原5属性不迁裁决保持。

正式表：`com_project_contract_relation`。

旧查询索引：L0056, L0094, L0127, L0160, L0163, L0166, L0167, L0213, L0244, L0246, L0249, L0250, L0301, L0302, L0339, L0359, L0360, L0361, L0362, L0544, L0555, L0572, L0592, L0593, L0604, L0605, L0606, L0607；原视图关系：—；迁移查询索引：M020, M023, M024, M034, M035；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectGroupCode | varchar(45)/NO | com_project_contract_relation的精确展开依据 | 仅组身份用于展开，不创建新合同组或项目组合 | 关联依据 |
| projectGroupName | varchar(45)/YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体 | 已批准排除 |
| projectType | varchar(25)/YES | proj_project.project_type（原查询匹配约束） | 按原类型匹配规范项目；不独立复制组类型 | 关联依据 |
| createTime | datetime/YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体 | 已批准排除 |
| createBy | varchar(15)/YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体 | 已批准排除 |
| updateTime | datetime/YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体 | 已批准排除 |
| updateBy | varchar(15)/YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体 | 已批准排除 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-project-contract-relation-table-proposal.md)。

运行/核验定位：`contract-whole-task-upgrade-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 31. pm_project_group_relationship

状态：**原试点完成；续办扩大写入待范围处置**。原合同桥沿正式关系承接；续办已扩至6480来源，smsProjectCode正式链全源73644匹配，其他缺失/多义仍阻断；扩大范围待核定。

正式表：`com_project_contract_relation`。

旧查询索引：L0056, L0074, L0089, L0097, L0119, L0127, L0145, L0160, L0163, L0166, L0167, L0178, L0184, L0213, L0229, L0244, L0246, L0249, L0250, L0256, L0258, L0262, L0301, L0302, L0343, L0346, L0347, L0348, L0357, L0358, L0359, L0360, L0361, L0362, L0394, L0398, L0399, L0526, L0527, L0528, L0535, L0536, L0543, L0544, L0551, L0552, L0553, L0555, L0572, L0573, L0582, L0592, L0593, L0604, L0605, L0606, L0607；原视图关系：—；迁移查询索引：M020, M023, M024, M034, M035；原材料采集索引：C002。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectGroupCode | varchar(45)/NO | com_project_contract_relation.contract_id | 关联pm_project_contract.contractNo后精确正式合同绑定 | 业务字段/正式关系 |
| projectCode | varchar(45)/YES | com_project_contract_relation.project_id/status | 还原所有-his；类型＋编码最大projectId；-his表示HISTORICAL不造解除日期，当前来源优先；跨组完整归并规则尚未完成 | 业务字段/正式关系 |
| mergeBranchMark | varchar(45)/YES | 不迁入 | 用户不迁入旧拆分备注；拆分采用新系统正式主子项目功能，不据旧备注改名/造树 | 已批准排除 |
| smsProjectCode | varchar(45)/YES | com_crm_execution_order.project_code（正式合同→订单→执行单链） | 原CRM商务编码及尾号依据；该完整正式链尚待核对，不加项目扩展/列 | 正式链映射尚未闭合 |
| createTime | datetime/YES | com_project_contract_relation.create_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | com_project_contract_relation.creator | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| updateTime | datetime/YES | com_project_contract_relation.update_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | com_project_contract_relation.updater | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-12-dppms-project-contract-relation-table-proposal.md)。

运行/核验定位：`contract-whole-task-upgrade-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 32. pm_project_header

状态：**原试点完成；续办扩大写入待范围处置**。同pm_project：原10项目已验收；续办6570新增主档范围未经批准，待处置。后续整表源仍必须pm_project。

正式表：`proj_project`、`proj_project_party`。

旧查询索引：L0052, L0053, L0054, L0056, L0074, L0082, L0083, L0089, L0095, L0096, L0097, L0102, L0103, L0104, L0108, L0119, L0127, L0141, L0145, L0146, L0156, L0159, L0160, L0163, L0166, L0167, L0168, L0178, L0179, L0183, L0184, L0189, L0190, L0191, L0195, L0213, L0225, L0229, L0230, L0240, L0243, L0244, L0246, L0249, L0250, L0251, L0256, L0258, L0260, L0262, L0264, L0265, L0266, L0267, L0268, L0272, L0273, L0274, L0275, L0276, L0277, L0278, L0279, L0280, L0281, L0282, L0283, L0284, L0301, L0302, L0317, L0318, L0319, L0320, L0321, L0322, L0323, L0325, L0330, L0331, L0332, L0340, L0341, L0344, L0346, L0347, L0348, L0357, L0358, L0359, L0360, L0361, L0362, L0515, L0516, L0517, L0526, L0527, L0528, L0529, L0530, L0541, L0542, L0543, L0544, L0545, L0551, L0552, L0553, L0555, L0564, L0565, L0572, L0573, L0574, L0582, L0592, L0593, L0604, L0605, L0606, L0607, L0608, L0609, L0610, L0611, L0612, L0613, L0616, L0617；原视图关系：pm_project_header；迁移查询索引：M017；原材料采集索引：C001。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectId | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectType | varchar(45)/NO | proj_project.project_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectCode | varchar(45)/NO | proj_project.project_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectName | varchar(200)/YES | proj_project.project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectState | varchar(11)/YES | proj_project.lifecycle_status/current_stage/closed_at | 按类型及原状态已批准初始化：用服100正常闭环S6、20异常闭环、30/31/32沿规则；关闭时间缺失保持NULL，不生成任务/审批完成 | 业务字段/正式关系 |
| isback | varchar(11)/YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接 | 已批准排除 |
| column001 | varchar(255)/YES | proj_project.department_id/department_code/department_name | 精确正式目录；原NULL可保留，未知非空编码阻断；不授区域全部项目权限 | 业务字段/正式关系 |
| column002 | varchar(255)/YES | com_sales_order.customer_code/customer_id | 仅经正式合同/订单链精确核对；不加项目ERP客户镜像，不能据名称猜匹配 | 业务字段/正式关系 |
| column003 | varchar(255)/YES | com_sales_order.customer_name | 同一精确订单客户事实，不替代最终用户；全历史链路尚未完成逐行验证 | 业务字段/正式关系 |
| column004 | varchar(255)/YES | proj_project.market_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column005 | varchar(255)/YES | proj_project.system_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column006 | varchar(255)/YES | proj_project.expend_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column007 | varchar(255)/YES | proj_project.industry_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column008 | varchar(255)/YES | proj_project.not_track_reason | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column009 | datetime/YES | proj_project.order_create_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column010 | varchar(10)/YES | proj_project.project_category | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column011 | varchar(10)/YES | proj_project.signing_method | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| column012 | varchar(2)/YES | proj_project.implementation_mode | 原空NULL/空串优先Readonly回退；0/1/3沿既有转换，2=DIRECT_SUBCONTRACT、4=FACTORY_INTEGRATION，两项均承接 | 业务字段/正式关系 |
| columno12_readonly | int(2)/YES | proj_project.implementation_mode（仅主值缺失时） | 用户明确同名Readonly回退；不存第二份控制字段 | 业务字段/正式关系 |
| column013 | varchar(255)/YES | proj_project_party.party_name（party_role=FINAL_CUSTOMER） | 实际最终产品用户，经PROJ相关方Owner承接；不覆盖购货方 | 业务字段/正式关系 |
| column014 | text/YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接 | 已批准排除 |
| customerProjectName | varchar(255)/YES | proj_project.customer_project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| salesType | varchar(25)/YES | proj_project.sales_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| majorProjectLevel | varchar(255)/YES | proj_project.major_project_level | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| compId | int(2)/YES | proj_project.company_id/company_code/company_name | 原公司外键→实际公司编码→01/02/03/04转001/002/003/004；不将旧ID当新ID，原NULL按裁决保留 | 业务字段/正式关系 |
| createTime | datetime/YES | proj_project.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | proj_project.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | proj_project.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(45)/YES | proj_project.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | proj_project.effective_from | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveTo | datetime/YES | proj_project.effective_to | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| disabled | bit(1)/YES | proj_project.disabled | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectStartTime | datetime/YES | proj_project.project_start_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectRefreshTime | datetime/YES | proj_project.project_refresh_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectCloseTime | datetime/YES | proj_project.project_close_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customInfo | json/YES | proj_project.custom_info | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| customConfig | json/YES | — | 已审查来源全NULL，没有实际业务值；全历史遇非空无批准同义承接点时阻断 | 无值未落业务字段 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-proj-project-table-decision.md)。

运行/核验定位：`project-identity-task-pilot-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 33. pm_project_incident_table_from_itr

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0576, L0577；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C037。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| incidentId | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| ticketNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| STATUS | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| statusName | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| caseTopic | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| memo | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| principal | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| principalName | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| accepter | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| accepterName | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| processor | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| processorName | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| supplied | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionLevel | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| title | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| acceptTime | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| productType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| productModel | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| progress | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| questionReason | varchar(2048)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| solutionType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| solutions | varchar(2048)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| rmaNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| accidentNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| caseType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| reasonFstType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| reasonSndType | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectCode | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| contractNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| barcode | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| bulletinNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| bugNo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| productLine | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| customInfo | json/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| url | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 34. pm_project_instruction

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0092, L0093, L0181, L0182；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C016。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instructionsInfo | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instructionsTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instructionsUser | varchar(45)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| dataType | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| instructionsId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 35. pm_project_license_info_from_license

状态：**已承接10项目；全历史未执行**。153条16列认证加密承接、解密比对/重放/非秘密页面通过；475468全历史未迁

正式表：`ast_device_license_record`。

旧查询索引：L0578；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C036。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| licenseCode | varchar(256)/NO | ast_device_license_record.license_code_ciphertext | 独立受控AES-256认证加密；密钥DPAPI受控，原值仅内存核对；页面/日志/明文证据禁显 | 受控加密字段 |
| sn | varchar(255)/YES | ast_device_license_record.device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| specModel | varchar(256)/YES | ast_device_license_record.product_model | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contract | varchar(32)/YES | ast_device_license_record.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contractType | varchar(16)/YES | ast_device_license_record.contract_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| item | varchar(255)/YES | ast_device_license_record.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| status | varchar(255)/YES | ast_device_license_record.license_status | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-license-record-table-decision.md)。

运行/核验定位：`license-pilot-final-proof-20261011.json`（任务私有证据目录；若为md则位于上级generated）。

## 36. pm_project_maintenance

状态：**按裁决暂缓**。用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案

正式表：尚无已确认完整承接主体。

旧查询索引：L0048, L0049, L0050, L0051, L0052, L0053, L0054, L0055, L0057, L0058, L0059, L0060, L0061, L0062, L0170, L0171, L0172, L0173, L0253, L0254, L0255, L0328, L0329, L0330, L0331, L0332, L0333, L0513, L0514, L0515, L0516, L0517, L0518；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C012。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| projectId | int(11)/NO | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| projectCode | varchar(45)/NO | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| projectName | varchar(200)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| projectType | int(11)/NO | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| projectExecutionState | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| contractNo | varchar(255)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| officeCode | varchar(25)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| compId | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| type | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| category | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| subCategory | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| processTime | datetime/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| processDesc | varchar(2048)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| processStep | varchar(2048)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| remainProblem | varchar(2048)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| transitHour | float/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| processHour | float/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| itemModel | varchar(255)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| softVersion | varchar(255)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| enabledFeatures | varchar(255)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| customTos | varchar(512)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| customCcs | varchar(512)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| hasReport | bit(1)/NO | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| quesnaireId | int(11)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| deliverFileIds | varchar(255)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| warrantyStatus | varchar(25)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| industryName | varchar(25)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| userOffice | varchar(25)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| year | int(4)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| quarter | int(1)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| month | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| wsCount | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| wafCount | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| wsYearCount | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| wafYearCount | int(2)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| warrantyInfo | varchar(4096)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| serviceInfo | varchar(2048)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| remark | varchar(2048)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| createBy | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| updateBy | varchar(45)/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |
| customInfo | json/YES | — | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 本轮暂缓，未丢弃 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-maintenance-business-subject-decision.md)。

## 37. pm_project_member

状态：**已承接10项目；全历史未执行**。70原成员→61正式人员（23已确认授权、38未确认历史）＋9最终客户联系人；管理员6区间保留历史

正式表：`proj_project_member_assignment`、`cus_project_customer_contact_relation`。

旧查询索引：L0049, L0050, L0052, L0053, L0054, L0061, L0062, L0063, L0064, L0065, L0069, L0074, L0082, L0083, L0089, L0095, L0097, L0103, L0124, L0136, L0144, L0146, L0156, L0161, L0162, L0167, L0169, L0170, L0171, L0172, L0173, L0175, L0176, L0177, L0178, L0183, L0184, L0190, L0210, L0220, L0228, L0230, L0240, L0245, L0250, L0252, L0253, L0254, L0255, L0256, L0260, L0264, L0265, L0266, L0267, L0268, L0317, L0318, L0319, L0320, L0321, L0322, L0323, L0324, L0327, L0328, L0329, L0330, L0331, L0332, L0340, L0341, L0344, L0346, L0359, L0360, L0361, L0362, L0363, L0364, L0365, L0376, L0377, L0381, L0382, L0383, L0384, L0394, L0395, L0400, L0401, L0417, L0418, L0419, L0420, L0421, L0428, L0513, L0514, L0515, L0516, L0517, L0519, L0520, L0521, L0522, L0541, L0542, L0543, L0544, L0545, L0563, L0564, L0565, L0572, L0573, L0600, L0601, L0602, L0603, L0604, L0605, L0606, L0607, L0608, L0609, L0610, L0611, L0612, L0615, L0616, L0617, L0618, L0629, L0633；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C006。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/YES | proj_project_member_assignment.project_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectType | varchar(25)/YES | proj_project.project_type（关联约束） | 原projectId/type精确主体核验；不猜关联其他类型项目 | 业务字段/正式关系 |
| memberRole | varchar(45)/YES | proj_project_member_assignment.member_role 或 cus_project_customer_contact_relation.project_party_id | 原10销售、20服务经理、30项目经理；60最终客户联系人→CUS，不授账号角色 | 业务字段/正式关系 |
| memberCode | varchar(45)/YES | proj_project_member_assignment.member_code；角色60→cus_project_customer_contact_relation.contact_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| memberName | varchar(45)/YES | proj_project_member_assignment.member_name；角色60→cus_project_customer_contact_relation.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| phoneNum | varchar(20)/YES | proj_project_member_assignment.phone_number；角色60→cus_project_customer_contact_relation.phone | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| email | varchar(45)/YES | proj_project_member_assignment.email；角色60→cus_project_customer_contact_relation.email | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| fromFlag | varchar(2)/YES | proj_project_member_assignment.registration_source；角色60→cus_project_customer_contact_relation.registration_source | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| createTime | datetime/YES | proj_project_member_assignment.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(45)/YES | proj_project_member_assignment.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | proj_project_member_assignment.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(15)/YES | proj_project_member_assignment.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| effectiveTo | datetime/YES | proj_project_member_assignment.effective_to；角色60→cus_project_customer_contact_relation.effective_to | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| effectiveFrom | datetime/YES | proj_project_member_assignment.effective_from；角色60→cus_project_customer_contact_relation.effective_from | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-project-member-table-decision.md)。

运行/核验定位：`member-ten-project-final-proof-20261012.json`（任务私有证据目录；若为md则位于上级generated）。

## 38. pm_project_notification

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0165, L0248, L0316, L0319, L0320, L0610, L0611；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C014。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| notifySubject | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| notifyContent | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 39. pm_project_product_config_level_info_from_crm

状态：**已实现；试点原数据0条**。19列已实现，10项目0条；完整历史层级及缺父节点原样保留规则，不补造节点

正式表：`proj_project_product_configuration`。

旧查询索引：L0568, L0569；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C024。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectCode | varchar(100)/YES | proj_project_product_configuration.project_code ＋ project_id关联依据 | 旧查询用主码首段，共享首段不能自行指派项目；需精确规范项目绑定，不仅迁页面最大执行单 | 业务字段/正式关系 |
| orderExecNumber | varchar(255)/YES | proj_project_product_configuration.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemGroup | int(11)/YES | proj_project_product_configuration.item_group | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemCode | varchar(100)/YES | proj_project_product_configuration.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| parentCode | varchar(1000)/YES | proj_project_product_configuration.parent_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| quantity | int(11)/YES | proj_project_product_configuration.quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| bomPaths | varchar(1000)/YES | proj_project_product_configuration.bom_path | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemModel | varchar(100)/YES | proj_project_product_configuration.product_model | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemDesc | varchar(500)/YES | proj_project_product_configuration.product_description | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| level | int(11)/YES | proj_project_product_configuration.level | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| dataSource | varchar(25)/YES | proj_project_product_configuration.data_source | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-product-configuration-table-decision.md)。

运行/核验定位：`associated-lists-approved-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 40. pm_project_product_lease_line_from_crm

状态：**已实现；试点原数据0条**。19列已实现，10项目0条；454条全历史未迁入；productCategory/productType依裁决

正式表：`proj_project_lease_product`。

旧查询索引：L0566, L0567；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C023。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectCode | varchar(255)/NO | proj_project_lease_product.project_code ＋ project_id关联依据 | 旧查询用主码首段，共享首段不能自行指派项目；需精确规范项目绑定，不仅迁页面最大执行单 | 业务字段/正式关系 |
| orderExecNumber | varchar(255)/YES | proj_project_lease_product.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productFirstName | varchar(255)/NO | proj_project_lease_product.product_category | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productName | varchar(128)/NO | proj_project_lease_product.product_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubCode | varchar(255)/NO | proj_project_lease_product.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubModel | varchar(255)/YES | proj_project_lease_product.product_model | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubName | varchar(255)/YES | proj_project_lease_product.product_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| num | int(11)/NO | proj_project_lease_product.quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| memo | mediumtext/YES | proj_project_lease_product.memo | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| leaseDuration | decimal(16,2)/YES | proj_project_lease_product.lease_duration | DECIMAL(16,2)，单位租赁月数；不截成整数或按天 | 业务字段/正式关系 |
| dataSource | varchar(25)/YES | proj_project_lease_product.data_source | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-lease-product-table-decision.md)。

运行/核验定位：`associated-lists-approved-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 41. pm_project_product_line

状态：**已承接10项目；全历史未执行**。105历史实施范围＋105产品数量明细；allocation_confirmed=false；全历史0数量及重复行尚需统一处理

正式表：`com_delivery_scope`、`com_delivery_scope_detail`、`com_sales_order_line`。

旧查询索引：L0110, L0149, L0197, L0233, L0257, L0261, L0547；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C007。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/YES | com_delivery_scope.project_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contractNo | varchar(45)/YES | com_sales_order.contract_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product | 业务字段/正式关系 |
| itemCode | varchar(15)/YES | com_sales_order_line.product_code ＋ com_delivery_scope_detail.product_code | 按正式订单头/行及范围关联承接，不重建独立proj_project_product | 业务字段/正式关系 |
| itemName | varchar(255)/YES | com_delivery_scope.product_desc | 按正式订单头/行及范围关联承接，不重建独立proj_project_product | 业务字段/正式关系 |
| projectQuantity | int(11)/YES | com_delivery_scope.allocated_qty | 原projectQuantity优先，NULL取正式订单行order_qty；正数量约束保持，0/负数阻断；allocation_confirmed=false | 业务字段/正式关系 |
| orderQuantity | int(11)/YES | com_sales_order_line.order_qty | 通过精确order_line_id复用，非独立范围数量镜像；历史差异不得擅自吞掉 | 业务字段/正式关系 |
| deliverQuantity | int(11)/YES | com_delivery_scope.delivered_quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| openQuantity | int(11)/YES | com_delivery_scope.open_quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderNumber | varchar(25)/YES | com_sales_order_line.order_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product | 业务字段/正式关系 |
| lineNum | varchar(25)/YES | com_sales_order_line.line_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-delivery-scope-table-decision.md)。

运行/核验定位：`approved-product-scope-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 42. pm_project_product_line_real

状态：**已实现；试点原数据0条**。19列正式实体/Owner已实现，10项目0条；全历史7848项目清单/9992上游行未实际迁入

正式表：`proj_project_real_product`。

旧查询索引：L0157, L0158, L0241, L0242；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C017。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/NO | proj_project_real_product.project_id | 通过项目绑定精确关联；不以不同的商务projectCode覆盖主档编码 | 业务字段/正式关系 |
| contractNo | varchar(25)/NO | proj_project_real_product.contract_no | 清单合同号原文及空串保持 | 业务字段/正式关系 |
| projectCode | varchar(255)/NO | proj_project_real_product.project_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderExecNumber | varchar(255)/NO | proj_project_real_product.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productFirstName | varchar(255)/YES | proj_project_real_product.product_category_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productName | varchar(128)/YES | proj_project_real_product.product_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubCode | varchar(255)/NO | proj_project_real_product.product_sub_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubModel | varchar(255)/NO | proj_project_real_product.product_sub_model | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubName | varchar(255)/NO | proj_project_real_product.product_sub_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| num | int(11)/YES | proj_project_real_product.quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| memo | mediumtext/YES | proj_project_real_product.memo | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-real-product-table-decision.md)。

运行/核验定位：`real-product-approved-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 43. pm_project_property_from_sms

状态：**已完成可核实原记录承接；缺项另列**。2026-10-13全源102520原行映射83462既有正式执行单，102411更新/109未变化，重放102520未变化；主项目/状态及保护字段保持。

正式表：`com_crm_execution_order`。

旧查询索引：L0095, L0140, L0143, L0179, L0183, L0224, L0227, L0258, L0262, L0305, L0306, L0340, L0341, L0347, L0359, L0360, L0361, L0362, L0541, L0542, L0544, L0561, L0574, L0604, L0605, L0606, L0607；原视图关系：—；迁移查询索引：M030, M031；原材料采集索引：C020。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| orderExecNumber | varchar(25)/YES | com_crm_execution_order.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectCode | varchar(25)/YES | com_crm_execution_order.project_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectName | varchar(255)/YES | com_crm_execution_order.project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| salesManCode | varchar(45)/YES | com_crm_execution_order.sales_rep_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| salesManName | varchar(45)/YES | com_crm_execution_order.sales_rep_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| marketCode | varchar(64)/YES | com_crm_execution_order.market_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| marketName | varchar(255)/YES | com_crm_execution_order.market_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| systemId | varchar(64)/YES | com_crm_execution_order.system_source_key | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| systemName | varchar(255)/YES | com_crm_execution_order.system_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| expendId | varchar(64)/YES | com_crm_execution_order.expend_source_key | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| expendName | varchar(255)/YES | com_crm_execution_order.expend_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| industryId | varchar(64)/YES | com_crm_execution_order.industry_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| industryName | varchar(255)/YES | com_crm_execution_order.industry_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| officeCode | varchar(15)/YES | com_crm_execution_order.department_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| officeName | varchar(15)/YES | com_crm_execution_order.department_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| serviceTypeName | varchar(10)/YES | com_crm_execution_order.service_type_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| channelName | varchar(255)/YES | com_crm_execution_order.channel_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| engineeFee | varchar(25)/YES | com_crm_execution_order.engineering_fee_raw/engineering_fee | 完整原文与合法金额分开，非数值不冒充金额 | 业务字段/正式关系 |
| objId | varchar(64)/YES | com_crm_execution_order.source_object_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| applyType | varchar(25)/YES | com_crm_execution_order.apply_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| corporationCode | varchar(25)/YES | com_crm_execution_order.company_code/company_id | 同四项显式码转换；NULL保持，未知非空码阻断 | 业务字段/正式关系 |
| customerProjectName | varchar(255)/YES | com_crm_execution_order.customer_project_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| finalCustomerName | varchar(255)/YES | com_crm_execution_order.final_customer_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| agentName | varchar(500)/YES | com_crm_execution_order.agent_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| projectMoney | decimal(16,2)/YES | com_crm_execution_order.project_amount | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| submitTime | datetime/YES | com_crm_execution_order.submit_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| majorProjectLevel | varchar(255)/YES | com_crm_execution_order.major_project_level | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| predBidDate | datetime/YES | com_crm_execution_order.predicted_bid_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| linkmanName | varchar(255)/YES | com_crm_execution_order.contact_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| linkmanTel | varchar(64)/YES | com_crm_execution_order.contact_phone | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| dataSource | varchar(25)/YES | com_crm_execution_order.source_system | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-crm-execution-order-table-decision.md)。

运行/核验定位：`execution-fields-correction-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 44. pm_project_real_product_line_from_sms

状态：**已实现；试点原数据0条**。19列正式实体/Owner已实现，10项目0条；全历史7848项目清单/9992上游行未实际迁入

正式表：`proj_project_real_product`。

旧查询索引：本地未检出直接SELECT；不能用生成的审查语句冒称原查询；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C050。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectCode | varchar(255)/NO | proj_project_real_product.project_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| orderExecNumber | varchar(255)/YES | proj_project_real_product.execution_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productFirstName | varchar(255)/NO | proj_project_real_product.product_category_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productName | varchar(128)/NO | proj_project_real_product.product_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubCode | varchar(255)/NO | proj_project_real_product.product_sub_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubModel | varchar(255)/YES | proj_project_real_product.product_sub_model | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| productSubName | varchar(255)/YES | proj_project_real_product.product_sub_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| num | int(11)/NO | proj_project_real_product.quantity | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| memo | mediumtext/YES | proj_project_real_product.memo | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| dataSource | varchar(25)/YES | proj_project_real_product.data_source | 按其余9业务值NULL安全精确匹配项目清单；未形成项目行的上游资料仍待承接，不能声称9992全部完成 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-real-product-table-decision.md)。

运行/核验定位：`real-product-approved-pilot-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 45. pm_project_related_party

状态：**未完成正式承接**。整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0097, L0184, L0359, L0360, L0361, L0362, L0543, L0544, L0604, L0605, L0606, L0607；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C005。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| projectId | int(11)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| partyRole | varchar(45)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| partyCode | varchar(45)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| partyName | varchar(45)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| createTime | datetime/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| createBy | varchar(45)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| updateTime | datetime/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| updateBy | varchar(45)/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| effectiveFrom | datetime/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |
| effectiveTo | datetime/YES | — | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 待明确正式映射 |

## 46. pm_project_shipment

状态：**已承接10项目；全历史未执行**。948正式安装历史：有地址=已安装，创建时间作安装/生效时间；保20较新位置、补928空位置；串货全历史关系未完成

正式表：`imp_eng_installation`、`ast_device_location`、`ast_address`、`ast_device`。

旧查询索引：L0111, L0112, L0115, L0126, L0128, L0147, L0164, L0198, L0199, L0201, L0212, L0214, L0231, L0247, L0303, L0345, L0548, L0549, L0556, L0570, L0571, L0577；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C008。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/YES | imp_eng_installation.project_id | 原项目通过正式绑定关联，不取设备后来的归属代替 | 业务字段/正式关系 |
| barcode | varchar(25)/YES | imp_eng_installation.equipment_id（ast_device.sn精确关联） | 仅精确设备SN，不将原SN当新equipment_id | 业务字段/正式关系 |
| itemCode | varchar(25)/YES | 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致 | 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致 | 业务字段/正式关系 |
| itemModel | varchar(255)/YES | 本批及当前全历史均NULL，不新增安装镜像；后续遇非空重新核对正式产品属性 | 本批及当前全历史均NULL，不新增安装镜像；后续遇非空重新核对正式产品属性 | 业务字段/正式关系 |
| itemName | varchar(255)/YES | 现有正式产品目录已批准的DPPMS名称属性，本批逐字一致 | 现有正式产品目录已批准的DPPMS名称属性，本批逐字一致 | 业务字段/正式关系 |
| receiveName | varchar(255)/YES | 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对 | 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对 | 业务字段/正式关系 |
| emsNum | varchar(255)/YES | 正式com_shipment_package.express_no | 正式com_shipment_package.express_no | 业务字段/正式关系 |
| emsCompany | varchar(15)/YES | 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代 | 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代 | 业务字段/正式关系 |
| packdate | datetime/YES | 正式发货及装箱单shipment_time，不推导安装日期 | 正式发货及装箱单shipment_time，不推导安装日期 | 业务字段/正式关系 |
| contractNo | varchar(50)/YES | 正式发货contract_no及合同关系，不盲目删-C后缀 | 正式发货contract_no及合同关系，不盲目删-C后缀 | 业务字段/正式关系 |
| installAddress | text/YES | imp_eng_installation.install_location/location_snapshot ＋ ast_address.full_address | 原地址完整保存；正常地址目录与设备位置由各自Owner承接；业务位置快照是既有正式安装属性，不是迁移中间快照 | 业务字段/正式关系 |
| chProjectId | int(11)/YES | — | 本批NULL或-1无串货事实；全历史有值关系须正式转移业务承接，尚未裁决实施，不丢弃 | 全历史关联未承接 |
| chContractNo | varchar(50)/YES | — | 本批NULL或-1无串货事实；全历史有值关系须正式转移业务承接，尚未裁决实施，不丢弃 | 全历史关联未承接 |
| transferProjectId | int(11)/YES | — | 本批NULL或-1无串货事实；全历史有值关系须正式转移业务承接，尚未裁决实施，不丢弃 | 全历史关联未承接 |
| transferContractNo | varchar(50)/YES | — | 本批NULL或-1无串货事实；全历史有值关系须正式转移业务承接，尚未裁决实施，不丢弃 | 全历史关联未承接 |
| transferFlag | varchar(2)/YES | — | 本批NULL或-1无串货事实；全历史有值关系须正式转移业务承接，尚未裁决实施，不丢弃 | 全历史关联未承接 |
| createTime | datetime/YES | imp_eng_installation.create_time/install_time/effective_from | 有地址则status=2；原创建时间表示安装/生效；20已有较新安装取其生效为历史结束，其余928补当前空位置 | 业务字段/正式关系 |
| createBy | varchar(25)/YES | imp_eng_installation.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | imp_eng_installation.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | imp_eng_installation.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-existing-installation-table-decision.md)。

运行/核验定位：`verified-installation-record-correction-state-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 47. pm_project_soft_change_logs

状态：**已承接10项目；全历史未执行**。6条11列正式批次；原审计和NULL处理核对，旧标记不生成升级完成

正式表：`ast_device_software_change_batch`。

旧查询索引：L0079, L0082, L0083, L0117, L0118, L0120, L0203, L0204, L0206, L0532, L0554；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C009。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/YES | ast_device_software_change_batch.project_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| changeVersion | varchar(10)/YES | ast_device_software_change_batch.change_version | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| changeRemark | varchar(255)/YES | ast_device_software_change_batch.change_remark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| latest | int(11)/YES | ast_device_software_change_batch.latest | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| createBy | varchar(25)/YES | ast_device_software_change_batch.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createTime | datetime/YES | ast_device_software_change_batch.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | ast_device_software_change_batch.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | ast_device_software_change_batch.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-software-batch-table-decision.md)。

运行/核验定位：`software-final-full-field-verification-20261011.json`（任务私有证据目录；若为md则位于上级generated）。

## 48. pm_project_soft_version

状态：**已承接10项目；全历史未执行**。659条31列正式记录＋5272旧Bak/Change属性；一次性迁移，后续在正常软件维护业务执行

正式表：`ast_device_software_version_record`、`plt_entity_extension_value`。

旧查询索引：L0073, L0074, L0075, L0076, L0079, L0082, L0083, L0089, L0115, L0119, L0201, L0205, L0525, L0526, L0527, L0528, L0529, L0530, L0532, L0535, L0536, L0549, L0551, L0552, L0553；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C010。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| projectId | int(11)/YES | ast_device_software_version_record.project_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| logId | int(11)/YES | ast_device_software_version_record.change_batch_id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| contractNo | varchar(100)/YES | ast_device_software_version_record.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemCode | varchar(25)/YES | ast_device_software_version_record.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| barCode | varchar(25)/YES | ast_device_software_version_record.device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| conp | varchar(100)/YES | ast_device_software_version_record.conp_version | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| conpType | varchar(100)/YES | ast_device_software_version_record.conp_type | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| conpSeries | varchar(100)/YES | ast_device_software_version_record.conp_series | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| conpMark | varchar(255)/YES | ast_device_software_version_record.conp_mark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| conpBak | varchar(255)/YES | plt_entity_extension_value.values_json.conp_bak（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| conpChange | int(11)/YES | plt_entity_extension_value.values_json.conp_change（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| cpld | varchar(100)/YES | ast_device_software_version_record.cpld_version | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| cpldBak | varchar(255)/YES | plt_entity_extension_value.values_json.cpld_bak（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| cpldChange | int(11)/YES | plt_entity_extension_value.values_json.cpld_change（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| boot | varchar(100)/YES | ast_device_software_version_record.boot_version | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| bootBak | varchar(255)/YES | plt_entity_extension_value.values_json.boot_bak（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| bootChange | int(11)/YES | plt_entity_extension_value.values_json.boot_change（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| pcb | varchar(100)/YES | ast_device_software_version_record.pcb_version | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| pcbBak | varchar(255)/YES | plt_entity_extension_value.values_json.pcb_bak（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| pcbChange | int(11)/YES | plt_entity_extension_value.values_json.pcb_change（SoftwareVersionRecord） | 按正式软件记录ID关联原备份/变化标志业务属性；原值保持不重算，新操作通过previous_record_id比较，不冗余存新Bak/Change | 已批准正式实体扩展 |
| executeTime | date/YES | ast_device_software_version_record.execute_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| datastate | int(11)/YES | ast_device_software_version_record.data_state | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| createTime | datetime/YES | ast_device_software_version_record.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| createBy | varchar(25)/YES | ast_device_software_version_record.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateTime | datetime/YES | ast_device_software_version_record.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| updateBy | varchar(25)/YES | ast_device_software_version_record.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| customInfo | json/YES | ast_device_software_version_record.custom_info | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-10-dppms-software-version-maintenance-table-decision.md)。

运行/核验定位：`software-final-full-field-verification-20261011.json`（任务私有证据目录；若为md则位于上级generated）。

## 49. pm_project_state

状态：**未完成正式承接**。已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施

正式表：尚无已确认完整承接主体。

旧查询索引：L0097, L0148, L0150, L0160, L0166, L0167, L0168, L0178, L0232, L0234, L0244, L0249, L0250, L0251, L0267, L0268, L0280, L0281, L0282, L0283, L0284, L0317, L0318, L0346, L0348, L0543, L0544, L0572, L0573, L0582, L0608, L0609；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C004。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| projectId | int(11)/NO | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| projectPlanState | varchar(10)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| projectplanTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| shipmentState | varchar(11)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| shipmentTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| executionState | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| executionStateTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| closeProcessState | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| closeProcessStateTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-project-state-task-original-audit.md)。

## 50. pm_project_supervision

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0174, L0175, L0176, L0177, L0327, L0615；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C018。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectCode | varchar(45)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectName | varchar(200)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| channel | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| officeCode | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| type | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| processTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| state | bit(1)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| isDelete | bit(1)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| quesnaireId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| deliverFileIds | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| remark | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(45)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(45)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 51. pm_project_task

状态：**未完成正式承接**。已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施

正式表：尚无已确认完整承接主体。

旧查询索引：L0063, L0066, L0067, L0069, L0125, L0142, L0151, L0159, L0160, L0166, L0167, L0211, L0226, L0235, L0243, L0244, L0249, L0250, L0317, L0318, L0422, L0423, L0424, L0425, L0426, L0427, L0428, L0519, L0522, L0572, L0608, L0609, L0633；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C011。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| taskId | int(11)/NO | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| projectId | int(11)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| projectType | varchar(25)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| contractNo | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| taskTypeCode | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| taskTypeId | varchar(25)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| taskName | varchar(255)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| eventPlanHappenDate | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| eventPlanHappenDateENG | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| planStartTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| planEndTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| actualStartTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| eventActualFinishDate | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| priority | varchar(25)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| progress | int(3)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| progressDesc | varchar(255)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| status | varchar(25)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| parentId | int(11)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| remark | text/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| createTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| createBy | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| updateTime | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| updateBy | varchar(45)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| effectiveFrom | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| effectiveTo | datetime/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| visibleFlag | varchar(2)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| deliverFileIds | varchar(255)/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |
| customInfo | json/YES | — | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 待明确正式映射 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-project-state-task-original-audit.md)。

## 52. pm_project_weekly

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0098, L0099, L0135, L0185, L0186, L0219；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C015。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| weeklyId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| currentTask | varchar(100)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskStartTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskEndTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| taskDeviation | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| remark | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| weeklyStartTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| weeklyEndTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| weeklyState | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 53. pm_project_weekly_content

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0100, L0187；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C041。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| weeklyId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| optionDesc001 | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| optionDesc002 | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| optionType | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(15)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveFrom | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveTo | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 54. pm_project_weekly_feedback

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0101, L0188；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C042。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| weeklyId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| feedback | text/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| feedbacker | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| feedbackTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 55. pm_subcontract_project_header

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0285, L0286, L0287, L0288, L0289, L0290, L0291, L0292, L0304, L0307, L0308, L0309, L0311, L0312, L0314, L0315, L0349, L0350, L0351, L0352, L0353, L0354, L0356, L0581, L0583, L0584, L0585, L0586, L0587, L0588, L0594, L0595, L0596, L0598, L0599；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C038。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| subcontractName | varchar(512)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| subcontractNo | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| contractNos | varchar(2048)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectIds | varchar(1024)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| type | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| state | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| callbackState | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| facilitatorId | int(11)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| facilitatorName | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| bankInfo | varchar(255)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| bankAccount | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| officeCode | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| profitDepCode | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| isAccrued | bit(1)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| isInvoiced | bit(1)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| subcontractAmount | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| reason | varchar(512)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| remark | varchar(512)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveFrom | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| effectiveTo | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| zrApproveTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| orgId | int(2)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| customInfo | json/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 56. pm_subcontract_project_payment

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0288, L0290, L0291, L0295, L0296, L0297, L0312, L0313, L0314, L0315, L0351, L0353, L0354, L0356, L0581, L0585, L0586, L0587, L0588, L0590, L0591, L0594, L0598, L0599；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C039。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| subcontractId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| ratio | varchar(10)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| amount | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| confirmTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| paymentTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| remark | varchar(512)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| sseId | bigint(20)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| customInfo | json/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 57. pm_subcontract_project_price

状态：**按裁决暂缓**。额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接

正式表：尚无已确认完整承接主体。

旧查询索引：L0298, L0299, L0300, L0306；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C040。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| subcontractId | int(11)/NO | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| contractNo | varchar(50)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| orderExecNumber | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| projectCode | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| engineeFee | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| objId | varchar(64)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| procType | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| price | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| createBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateTime | datetime/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |
| updateBy | varchar(25)/YES | — | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 本轮暂缓，未丢弃 |

## 58. sms_ofst_contract_head_sap

状态：**未完成正式承接**。39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成

正式表：尚无已确认完整承接主体。

旧查询索引：L0368, L0369, L0370, L0385, L0394, L0620, L0621, L0622；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | bigint(20)/NO | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| contract_num | varchar(45)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| batch_code | varchar(10)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| project_name | varchar(200)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| order_num | varchar(25)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| client_supplier_code | varchar(20)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| client_supplier_name | varchar(200)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| contract_money_amount | decimal(20,2)/NO | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| delivered_money_amount | decimal(20,2)/NO | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| collected_money_amount | decimal(20,2)/NO | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| collected_money_ratio | double/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| receivables_money_amount | decimal(20,2)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| over_due_money_amount | decimal(20,2)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| maketing_department_name | varchar(40)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| office_name | varchar(20)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| industry_name | varchar(40)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| marketing_representative_name | varchar(20)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| currency_name | varchar(25)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| create_by | varchar(20)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| create_time | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| update_by | varchar(20)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| update_time | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| effective_from | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| effective_to | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| import_batch_num | varchar(12)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| contract_create_date | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| projectCode | varchar(80)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| marketCode | varchar(80)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| systemId | int(11)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| industryId | int(11)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| officeCode | varchar(80)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| expendId | int(11)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| usernamec | varchar(10)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| latest_ship_date | datetime/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| usernamec2 | varchar(10)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| systemid_o | int(11)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| expendid_o | int(11)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| industry_name_o | varchar(40)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |
| dataSource | varchar(25)/YES | — | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 待明确正式映射 |

## 59. t_role

状态：**已实际承接（批准范围）**。31条来源角色绑定，25新角色＋6复用；停用不迁；13角色25处NULL审计已纠正

正式表：`system_role`。

旧查询索引：L0402, L0403, L0408, L0409, L0410, L0411, L0447, L0448, L0449, L0450, L0451, L0452, L0473, L0474, L0476, L0477, L0478, L0484, L0485, L0486, L0487, L0630, L0631, L0634；原视图关系：—；迁移查询索引：M004；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| role_id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| role_name | varchar(100)/YES | system_role.code | 按已裁决DppmsRoleCodes.T_CODES复用同义正式角色：项目经理/服务经理关联既有正式编码，超级管理员授平台超级管理员；不是所有原字符串无转换直写 | 业务字段/正式关系 |
| role_name_zn | varchar(100)/YES | system_role.name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| home_page | varchar(100)/YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接 | 已批准排除 |
| priority | int(11)/YES | system_role.sort | 100-priority，沿已批准排序规则 | 业务字段/正式关系 |
| status | smallint(1)/YES | system_role.status | 停用来源角色不迁；启用1→正式0；超级管理员明确关联平台超级管理员角色 | 业务字段/正式关系 |
| create_by | varchar(25)/YES | system_role.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| create_time | datetime/YES | system_role.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| update_by | varchar(25)/YES | system_role.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| update_time | datetime/YES | system_role.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| remark | varchar(255)/YES | system_role.remark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-role-identity-mapping-decision.md)。

运行/核验定位：`approved-role-migration-verification-2108559110330822657.json`（任务私有证据目录；若为md则位于上级generated）。

## 60. t_user

状态：**已实际承接（批准范围）**。合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行

正式表：`system_users`、`system_user_company_department_scope`、`system_user_role`。

旧查询索引：L0366, L0367, L0368, L0369, L0371, L0408, L0409, L0410, L0411, L0420, L0427, L0447, L0462, L0463, L0464, L0465, L0466, L0467, L0468, L0469, L0470, L0471, L0472, L0473, L0474, L0475, L0478, L0484, L0485, L0486, L0487, L0488, L0489, L0490, L0491, L0619, L0634；原视图关系：—；迁移查询索引：M001, M002, M004, M006, M008, M009, M011, M014, M017, M019, M026, M027, M028, M029, M036, M037；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| user_id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| user_name | varchar(25)/YES | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING | 业务字段/正式关系 |
| password | varchar(100)/YES | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则 | 已批准排除 |
| create_by | varchar(25)/YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| create_time | datetime/NO | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| update_by | varchar(25)/YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| update_time | datetime/YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 | 业务字段/正式关系 |
| status | smallint(1)/NO | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION | 业务字段/正式关系 |
| needChangePwd | bit(1)/NO | system_users.custom_info.mustChangePassword | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| loginErrorCount | int(1)/NO | system_users.custom_info.loginErrorCount | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| isSysUser | smallint(1)/NO | — | 已读入完整原字段；已提交DppmsUserCustomInfo没有accountType/isSysUser属性，未证明该原标记已进入正式字段；账号范围/权限规则不能替代原值承接 | 正式字段映射缺口 |
| userCustom1 | varchar(50)/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |
| userCustom2 | varchar(50)/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |
| userCustom3 | varchar(50)/YES | system_users.employee_no / custom_info.employmentAssignments | 经EHR真实工号核实；外部用户名不当工号 | 业务字段/正式关系 |
| userCustom4 | int(11)/YES | system_users.custom_info.employmentAssignments[].employeeReference | 作为EHR员工外键核对，保存已核实任职员工引用；不同创建链ID不能无条件当员工ID；未核实原值不宣称已保存 | 业务字段/正式关系 |
| userCustom5 | int(11)/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-system-users-table-decision.md)。

运行/核验定位：`approved-user-migration-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 61. t_user_info

状态：**已实际承接（批准范围）**。合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行

正式表：`system_users`、`system_user_company_department_scope`、`system_user_role`。

旧查询索引：L0363, L0364, L0365, L0366, L0367, L0368, L0369, L0371, L0390, L0401, L0403, L0408, L0409, L0410, L0411, L0420, L0427, L0428, L0447, L0453, L0454, L0455, L0456, L0457, L0458, L0459, L0460, L0461, L0462, L0465, L0472, L0473, L0474, L0478, L0488, L0489, L0490, L0491, L0618, L0619, L0629, L0630, L0631, L0633, L0634；原视图关系：—；迁移查询索引：M002, M009；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| workNo | varchar(25)/NO | system_users.employee_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| realName | varchar(50)/NO | system_users.nickname | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| eName | varchar(50)/YES | system_users.custom_info.englishName | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| compID | int(11)/NO | system_user_company_department_scope.company_id/code/name | 保留用户所属公司事实，经实际来源公司解析及01→001等显式规则；不是t_user_role的授权公司替代值。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| depID | int(11)/NO | system_users.dept_id；system_user_company_department_scope.department_id/code/name | 通过来源部门目录和EHR同步绑定解析，不复用旧ID，不由部门猜公司；主部门选取与多公司资料冲突需明确。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| jobID | int(11)/NO | system_users.post_ids（关联system_post） | 通过EHR岗位编码精确核对正式岗位；未匹配不伪造岗位 | 业务字段/正式关系 |
| reportTo | int(11)/YES | system_users.custom_info.employmentAssignments[].reportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| wfreportTo | int(11)/YES | system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| empStatus | int(11)/NO | system_users.custom_info.employmentAssignments[].employeeStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| jobStatus | int(11)/YES | system_users.custom_info.employmentAssignments[].positionStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| empType | int(11)/YES | system_users.custom_info.employmentAssignments[].employeeType | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| sex | smallint(1)/YES | system_users.sex | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| birthday | date/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |
| email | varchar(50)/YES | system_users.email | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| mobile | varchar(50)/YES | system_users.mobile | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| telphone | varchar(50)/YES | system_users.custom_info.officePhone | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| avatar | varchar(500)/YES | system_users.avatar | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| remark | varchar(100)/YES | system_users.remark | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| state | int(11)/YES | system_users.custom_info.employmentAssignments[].historicalEmploymentState | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| user_id | int(11)/YES | system_users.id | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| custom1 | int(11)/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |
| custom2 | int(11)/YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION | 已批准排除 |
| custom3 | varchar(50)/YES | system_user_company_department_scope.department_code / 正式部门上下文 | 旧创建链明确是二级办事处编码，不必等于实际主部门；原341非空，同一公司配对保存，不新增custom3镜像。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |
| custom4 | varchar(50)/YES | system_users.custom_info.employmentAssignments[].projectTypeRestrictions | 完整承接原项目类型准入值及已核实转换，历史限制不授予办事处或具体项目访问权；未知类型不得猜配。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE | 业务字段/正式关系 |
| custom5 | varchar(4096)/YES | system_user_company_department_scope 部门范围 | 原341非空；与该条userinfo明确公司组合，去空去重/别名映射，不能跨公司套用。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-system-users-table-decision.md)。

运行/核验定位：`approved-user-migration-verification-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 62. t_user_role

状态：**已实际承接（批准范围）**。1932来源关系→1927新增正式用户角色对；5条空公司关系不新增公司授权

正式表：`system_user_role`。

旧查询索引：L0403, L0408, L0409, L0410, L0411, L0447, L0470, L0471, L0473, L0474, L0476, L0477, L0478, L0479, L0480, L0481, L0482, L0483, L0484, L0485, L0486, L0487, L0488, L0489, L0490, L0491, L0492, L0493, L0630, L0631, L0634；原视图关系：—；迁移查询索引：M006, M011；原材料采集索引：—。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接 | 技术身份 |
| user_id | int(11)/NO | system_user_role.user_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系 | 业务字段/正式关系 |
| role_id | int(11)/NO | system_user_role.role_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系 | 业务字段/正式关系 |
| comp_id | int(11)/YES | system_user_company_department_scope.company_id/company_code | NULL公司只迁用户角色不新增公司范围；非空按实际目录和四项显式码转换 | 业务字段/正式关系 |

运行/核验定位：`verified-user-role-apply-20261009.json`（任务私有证据目录；若为md则位于上级generated）。

## 63. view_shipment_ems_4_pm

状态：**已承接10项目；全历史未执行**。19装箱单全部8原字段核对及重放；最终20列含carrier_code；物流名称/编码分别保存

正式表：`com_shipment_package`。

旧查询索引：L0111, L0112, L0113, L0114, L0115, L0116, L0128, L0164, L0198, L0199, L0200, L0201, L0202, L0214, L0247, L0303, L0535, L0536, L0548, L0549, L0550, L0556, L0570, L0571, L0577；原视图关系：view_shipment_ems_4_pm；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C027。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| contract_code | varchar(25)/YES | com_shipment_contract_reference.contract_no | 原con_id精确合同引用，视图不独立制造合同 | 业务字段/正式关系 |
| receiveName | text/YES | com_shipment_package.receiver_name | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| emsNum | text/YES | com_shipment_package.express_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| packdate | datetime/YES | com_shipment_package.shipment_time | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| emsCompany | mediumtext/YES | com_shipment_package.carrier_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| packId | varchar(64)/YES | com_shipment_package.package_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-shipment-package-table-decision.md)。

运行/核验定位：`shipment-package-final-approved-state-20261011.json`（任务私有证据目录；若为md则位于上级generated）。

## 64. view_shipment_info_4_pm

状态：**已承接10项目；全历史未执行**。不另复制视图；本批1284行通过正式发货、目录及目录标签承接

正式表：`ast_device_shipment`、`ast_product_official_info`、`plt_entity_extension_value`。

旧查询索引：L0074, L0089, L0111, L0112, L0113, L0114, L0115, L0116, L0128, L0147, L0164, L0198, L0199, L0200, L0201, L0202, L0214, L0231, L0247, L0303, L0535, L0536, L0548, L0549, L0550, L0556, L0570, L0571, L0577；原视图关系：view_shipment_info_4_pm；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C028。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| contract_code | varchar(25)/YES | ast_device_shipment.contract_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemCode | varchar(16)/YES | ast_device_shipment.product_code | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemModel | varchar(255)/YES | ast_product_official_info ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productModel | 主/副产品分别以租户＋编码关联；完整原型号/名称是已批准正式目录属性，不新增发货镜像 | 业务字段/正式关系 |
| itemName | varchar(255)/YES | ast_product_official_info ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productName | 主/副产品分别以租户＋编码关联；完整原型号/名称是已批准正式目录属性，不新增发货镜像 | 业务字段/正式关系 |
| barcode | varchar(50)/YES | ast_device_shipment.device_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| packId | varchar(64)/YES | ast_device_shipment.package_no | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemCode2 | varchar(16)/YES | ast_device_shipment.secondary_item | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| itemModel2 | varchar(255)/YES | ast_product_official_info ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productModel | 主/副产品分别以租户＋编码关联；完整原型号/名称是已批准正式目录属性，不新增发货镜像 | 业务字段/正式关系 |
| itemName2 | varchar(255)/YES | ast_product_official_info ＋ plt_entity_extension_value.values_json.dppms_catalog_labels[].productName | 主/副产品分别以租户＋编码关联；完整原型号/名称是已批准正式目录属性，不新增发货镜像 | 业务字段/正式关系 |
| barcode2 | varchar(50)/YES | ast_device_shipment.secondary_sn | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |
| profitCenter | varchar(32)/YES | ast_device_shipment.profit_center | 原值直接复用；按正式Owner及租户、权限、并发和来源保护处理 | 业务字段/正式关系 |

整表裁决及过程证据：[原审查](../2026-10-09-dppms-ast-device-shipment-table-decision.md)。

运行/核验定位：`reviewed-shipment-mirror-formal-directory-20261010.json`（任务私有证据目录；若为md则位于上级generated）。

## 65. view_warranty

状态：**现存保留；不独立同步**。149条现存记录/结构暂保留（118此前派生＋31原历史）；维保计算结果不独立同步；当前原view_warranty来源缺失不得自动替换

正式表：`ast_device_warranty_record`。

旧查询索引：L0548, L0571, L0580；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C029。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| newId | bigint(20)/NO | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| id | bigint(20)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| barcode | varchar(50)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| comBarCode | varchar(50)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| old_warrantyEndTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyEndTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| old_diff | int(7)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| diff | int(7)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyStartTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| old_warrantyStartTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| item | varchar(16)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| describe_ | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| itemName | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| gradeName | varchar(125)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| gradeCode | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| packdate | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contract_code | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contract_type | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contract_type_name | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| project_name | varchar(512)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| customer_name | varchar(512)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| office_code | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| office_name | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| marketCode | varchar(10)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| marketName | varchar(15)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| systemId | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| systemName | varchar(15)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warranty | varchar(2)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyMonth | double/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| barcode2 | varchar(50)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| item2 | varchar(16)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| syncTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| describe_2 | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| itemName2 | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| agentName | varchar(500)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| finalCustomerName | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-warranty-record-table-decision.md)。

## 66. warranty_change_logs

状态：**未完成正式承接**。本批来源0；维保计算结果不独立同步不等于原权利/登记/变更事实获准丢弃；原附加原型不是已批准承接

正式表：尚无已确认完整承接主体。

旧查询索引：本地未检出直接SELECT；不能用生成的审查语句冒称原查询；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C034。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| username | varchar(10)/NO | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| updateType | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| barcode | varchar(20)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyStartTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyEndTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyTimes | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| updateTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-warranty-record-table-decision.md)。

## 67. warranty_info

状态：**未完成正式承接**。本批来源0；维保计算结果不独立同步不等于原权利/登记/变更事实获准丢弃；原附加原型不是已批准承接

正式表：尚无已确认完整承接主体。

旧查询索引：本地未检出直接SELECT；不能用生成的审查语句冒称原查询；原视图关系：—；迁移查询索引：无独立成功SQL任务材料；原材料采集索引：C033。

| 原字段 | 原类型/可空 | 正式承接点或不迁入 | 转换、关联、排除原因 | 字段处理 |
|---|---|---|---|---|
| id | int(11)/NO | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| packlistId | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contractId | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| barCode | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| officeCode | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| projectName | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contractNo | varchar(25)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| contractType | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| customerName | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| itemCode | varchar(8)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| itemName | varchar(255)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyLevel | varchar(8)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyStartTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyEndTime | datetime/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |
| warrantyLimit | int(11)/YES | — | 维保结果不独立同步；现存维保记录结构暂保留。原服务权利/登记/变更字段尚未获完整正式承接，不计迁移快照为业务承接 | 保留/未独立承接 |

整表裁决及过程证据：[原审查](../2026-10-11-dppms-warranty-record-table-decision.md)。
