# 保修原材料与正式业务复用核查

状态：READ_ONLY / WHOLE_TABLE_DECISION_PREPARATION。044已裁决保留view_warranty原36列及原材料继续推进。本报告没有结构或写入授权，不修改原库、目标实体、当前维保投影、来源证据或旧回执。

本轮核对10项目当前不可变来源中118条完整36列原记录。118条均按原发货ID＋SN唯一匹配正式发货；按既有产品编码trim规则均匹配唯一正式产品，118套原名称/型号均已在批准的目录实体扩展中完整保存；118条也均匹配唯一发货合同引用及项目合同关系。以原材料开展核对，不将实际31列view_warranty_source替换成来源契约。

关键区别：com_contract主表的contract_type、system_code为空，department_name为长春；相应原值实际在com_shipment_contract_reference的contract_type、system_source_key、department_name得到准确承接。不能把source原系统ID当正式编码，也不能在保修表追加合同信息副本。

审查最初发现两项语义差异：①原warranty为3/5，实际共同视图以它×12计算月份，按该定义对应年数；既有warranty_flag定义为0/1标志，同值不等于同义。此项现已由用户整表批准，com_shipment_contract_reference已改为warranty_years INT NULL并完成120638行核对、迁移/同步纠正及重放，详见[合同引用实施验收](2026-10-10-dppms-shipment-contract-reference-table-decision.md)。②实际共同视图的diff/old_diff是计算日减到期日，正值为已过期，不是剩余天数；将此公式用于118原材料可一致反推2026-08-04，这属于从原值及已知公式作出的推断，不证明原36列表的缓存生成链已找到。不能据10月采集或迁入日期改写后声称原值承接。第二项对应承接点尚未裁决，既有列和值先保留，不删除或更名。

## 全36列及逐项承接分析

| 原字段 | 原类型 | 已有承接点或待明确项 | 核查与处理原因 |
|---|---|---|---|
| newId | bigint(20) | 既有INT/PLT来源绑定 | 原缓存技术ID；不是设备或合同业务键，不再复制为实体业务列 |
| id | bigint(20) | 正式ast_device_shipment及既有来源绑定 | 本批118项按原发货ID＋SN各匹配唯一正式发货；正式关系的具体读写方案须随整表裁决，不能仅删除引用列 |
| barcode | varchar(50) | ast_device_warranty_record.device_sn | 既有正式设备序列号 |
| comBarCode | varchar(50) | ast_device_shipment.internal_serial_no | 本批已建立精确发货对应；正式组合序列号字段 |
| old_warrantyEndTime | datetime | 待确认原包件区间/服务记录的复用与计算 | 共同定义按包件结束－1日或服务结束日期计算，与当前区间的来源可不同；本批同值不证明全历史冗余 |
| warrantyEndTime | datetime | ast_device_warranty_record.warranty_end_date | 同上；属于保修区间，不能代替安装/验收日期 |
| old_diff | int(7) | 待裁决：旧区间缓存计算值 | 本批同样对应2026-08-04；不能当剩余天数，也不能和当前diff互相覆盖 |
| diff | int(7) | 待裁决：旧缓存计算值与动态期限计算 | 实际共同视图为TO_DAYS(now)-TO_DAYS(到期日)，正值表示已过期，现remaining_days_at_source命名符号含义错误；本批反推计算日为2026-08-04，不能用10月迁移时间重算后声称原值承接 |
| warrantyStartTime | datetime | ast_device_warranty_record.warranty_start_date | 本批118值均午夜，可无损复用DATE；全历史非午夜时须重新裁决精度，不能截断 |
| old_warrantyStartTime | datetime | 待确认旧发货保修日期与正式装箱单关系 | 实际共同视图定义取fb_shipment.warrantyStartTime；本批与当前起始同值，不能据样本同值判定全历史重复 |
| item | varchar(16) | ast_device_shipment.product_code / ast_product_official_info.product_code | 本批118原码均有尾部空格；按已有产品编码trim匹配118唯一目录，不创建带空格的第二产品编码；原原文留证据 |
| describe_ | varchar(255) | 正式产品dppms_catalog_labels.productName | 118原名称逐值匹配；已有CRM名称保持，不覆盖为旧名称 |
| itemName | varchar(255) | 正式产品目录product_model及dppms_catalog_labels.productModel | 本批118原型号经已批准实体扩展逐值匹配 |
| gradeName | varchar(125) | 待核正式等级字典/目录承接点 | 本批为同一基本维保名称；不能把等级名称当备注或凭相同编码就删除 |
| gradeCode | varchar(25) | ast_device_warranty_record.warranty_grade | 既有正式维保等级码；本批均53030001 |
| packdate | datetime | ast_device_shipment.shipment_time | 复用发货业务时间，不存到保修镜像 |
| contract_code | varchar(25) | warranty_contract_no及com_shipment_contract_reference.contract_no | 118均有唯一正式发货合同引用和项目合同关系 |
| contract_type | int(11) | com_shipment_contract_reference.contract_type | 按既有STRING转换，118原0与正式0一致；com_contract.contract_type为空，不能声称它已承接 |
| contract_type_name | varchar(25) | 待核正式合同类型字典 | 本批销售合同；编码已正式承接不等于原标签有业务承接 |
| project_name | varchar(512) | com_shipment_contract_reference.project_name | 118原值逐值匹配 |
| customer_name | varchar(512) | com_shipment_contract_reference.customer_name | 118原值逐值匹配，不能混用最终用户 |
| office_code | varchar(25) | com_shipment_contract_reference.department_code | 118逐值匹配；公司沿已裁决01..04→001..004 |
| office_name | varchar(25) | com_shipment_contract_reference.department_name | 118均为长春办事处且精确匹配；合同主表是长春，两者不能按名字直接相同处理 |
| marketCode | varchar(10) | com_shipment_contract_reference.market_code | 118逐值匹配 |
| marketName | varchar(15) | com_shipment_contract_reference.market_name | 118逐值匹配 |
| systemId | int(11) | com_shipment_contract_reference.system_source_key | 原系统主档ID，118按既有STRING转换匹配；不能当system_code或正式部门ID |
| systemName | varchar(15) | com_shipment_contract_reference.system_name | 118逐值匹配 |
| warranty | varchar(2) | com_shipment_contract_reference.warranty_years INT NULL（已批准及本地验收） | 原值3或5，视图以其×12计算月数；年数不再存0/1标志。通过既有迁移/同步转换，保留0至10年、NULL及完整合同关系 |
| warrantyMonth | double | ast_device_warranty_record.warranty_months | 本批36/60个月；全历史可由设备覆盖合同年数，不能用它反推所有原合同年数 |
| barcode2 | varchar(50) | ast_device_shipment.secondary_sn | 本批全NULL保持；扩大全历史仍按副设备业务事实 |
| item2 | varchar(16) | ast_device_shipment.secondary_item及正式目录编码 | 本批全NULL；不得以样本空值排除全历史 |
| syncTime | datetime | 正式发货sync_time / source_updated_at关系核验 | 不得仅因名字都为同步时间就复用；以原发货ID+SN及原时间逐值核验后决定 |
| describe_2 | varchar(255) | 副产品正式目录已批准名称属性 | 本批全NULL；非空须保留副产品自身原值 |
| itemName2 | varchar(255) | 副产品正式目录型号/已批准目录名称属性 | 本批全NULL；非空须先精确关联目录，不能从主产品复制 |
| agentName | varchar(500) | 已有proj_project_party的历史相关方候选 | 118来源项目均有同名相关方；有多行及不同角色，须进一步核原角色语义和精确关系，不按同名任选一行 |
| finalCustomerName | varchar(255) | proj_project_party.party_role=FINAL_CUSTOMER / party_name | 118均与本轮已批准正式实际最终用户逐值匹配；当前产品使用方业务含义保持 |

本报告没有批准任何排除项。技术ID的业务无意义不影响原绑定保留；原名称、原区间、年数及过期日差不因为已有回执或来源证据就被判定正式承接。待明确项进入对应整表方案后由用户裁决。

## 现有表与全部实际列

### ast_device_warranty（21列/31行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| device_sn | varchar(100)/NO |
| warranty_start_date | date/YES |
| warranty_end_date | date/YES |
| warranty_months | int unsigned/YES |
| warranty_grade | varchar(32)/YES |
| warranty_contract_no | varchar(64)/YES |
| warranty_provider | varchar(128)/YES |
| warranty_type | varchar(32)/YES |
| warranty_status | varchar(32)/YES |
| remark | varchar(500)/YES |
| source_system | varchar(32)/NO |
| source_key | varchar(128)/NO |
| source_version | varchar(64)/YES |
| version | int unsigned/NO |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |
| tenant_id | bigint/NO |

### ast_device_warranty_record（50列/149行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| device_sn | varchar(100)/NO |
| warranty_start_date | date/YES |
| warranty_end_date | date/YES |
| warranty_months | int unsigned/YES |
| warranty_grade | varchar(32)/YES |
| warranty_contract_no | varchar(64)/YES |
| extended | bit(1)/YES |
| remark | varchar(500)/YES |
| source_system | varchar(32)/NO |
| source_key | varchar(128)/NO |
| source_version | varchar(64)/YES |
| version | int unsigned/NO |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |
| tenant_id | bigint/NO |
| combined_device_sn | text/YES |
| product_code | text/YES |
| product_name | text/YES |
| product_description | text/YES |
| grade_name | text/YES |
| shipment_time | datetime(3)/YES |
| warranty_flag | text/YES |
| source_warranty_start_time | datetime(3)/YES |
| source_warranty_end_time | datetime(3)/YES |
| original_warranty_start_time | datetime(3)/YES |
| original_warranty_end_time | datetime(3)/YES |
| remaining_days_at_source | int/YES |
| original_remaining_days_at_source | int/YES |
| contract_type | text/YES |
| contract_type_name | text/YES |
| project_name | text/YES |
| customer_name | text/YES |
| final_customer_name | text/YES |
| agent_name | text/YES |
| office_code | text/YES |
| office_name | text/YES |
| market_code | text/YES |
| market_name | text/YES |
| system_code | text/YES |
| system_name | text/YES |
| secondary_sn | text/YES |
| secondary_item | text/YES |
| secondary_product_name | text/YES |
| secondary_product_description | text/YES |
| source_sync_time | datetime(3)/YES |
| source_shipment_key | text/YES |

### ast_device_warranty_service_record（23列/0行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| source_system | varchar(32)/NO |
| source_table | varchar(64)/NO |
| source_key | varchar(128)/NO |
| source_version | varchar(128)/YES |
| source_read_at | datetime(3)/NO |
| source_checksum | char(64)/NO |
| source_id | varchar(64)/YES |
| renewal_contract_no | varchar(25)/YES |
| device_sn | varchar(50)/YES |
| grade | varchar(15)/YES |
| begin_date | datetime(3)/YES |
| end_date | datetime(3)/YES |
| warranty_flag | varchar(1)/YES |
| remark | text/YES |
| extension_flag | int/YES |
| source_state | int/YES |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |

### ast_device_warranty_registration（28列/0行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| source_system | varchar(32)/NO |
| source_table | varchar(64)/NO |
| source_key | varchar(128)/NO |
| source_version | varchar(128)/YES |
| source_read_at | datetime(3)/NO |
| source_checksum | char(64)/NO |
| source_id | bigint/NO |
| package_sync_id | int/YES |
| contract_sync_id | int/YES |
| device_sn | varchar(25)/YES |
| office_code | varchar(25)/YES |
| source_project_name | varchar(255)/YES |
| contract_no | varchar(25)/YES |
| contract_type | int/YES |
| customer_name | varchar(255)/YES |
| item_code | varchar(8)/YES |
| item_name | varchar(255)/YES |
| warranty_level | varchar(8)/YES |
| warranty_start_time | datetime(3)/YES |
| warranty_end_time | datetime(3)/YES |
| warranty_limit | int/YES |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |

### ast_device_warranty_change_record（21列/0行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| source_system | varchar(32)/NO |
| source_table | varchar(64)/NO |
| source_key | varchar(128)/NO |
| source_version | varchar(128)/YES |
| source_read_at | datetime(3)/NO |
| source_checksum | char(64)/NO |
| source_id | bigint/NO |
| source_username | varchar(10)/NO |
| update_type | int/YES |
| device_sn | varchar(20)/YES |
| warranty_start_time | datetime(3)/YES |
| warranty_end_time | datetime(3)/YES |
| warranty_times | int/YES |
| source_updated_at | datetime(3)/YES |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |

### ast_device_warranty_fact_project（11列/0行）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| project_id | bigint/NO |
| source_project_key | varchar(128)/NO |
| fact_type | varchar(32)/NO |
| fact_id | bigint/NO |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | bit(1)/NO |

### com_shipment_contract_reference（29列）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| contract_id | bigint/YES |
| contract_no | varchar(64)/YES |
| company_id | bigint/YES |
| company_code | varchar(64)/YES |
| company_name | varchar(255)/YES |
| department_id | bigint/YES |
| department_code | varchar(64)/YES |
| department_name | varchar(128)/YES |
| contract_type | varchar(32)/YES |
| customer_name | varchar(512)/YES |
| project_name | varchar(512)/YES |
| market_code | varchar(64)/YES |
| market_name | varchar(128)/YES |
| system_source_key | varchar(64)/YES |
| system_code | varchar(64)/YES |
| system_name | varchar(128)/YES |
| warranty_flag | varchar(8)/YES |
| remark | varchar(4096)/YES |
| mapping_status | varchar(32)/NO |
| source_system | varchar(32)/NO |
| source_record_key | varchar(128)/NO |
| source_sync_time | datetime(3)/YES |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | tinyint/NO |

### proj_project_party（既有结构）

| 当前字段 | 类型/可空 |
|---|---|
| id | bigint/NO |
| tenant_id | bigint/NO |
| project_id | bigint/NO |
| party_role | varchar(32)/NO |
| party_code | varchar(128)/YES |
| party_name | varchar(1024)/YES |
| contact_name | varchar(255)/YES |
| phone | varchar(128)/YES |
| source_system | varchar(32)/NO |
| source_table | varchar(64)/NO |
| source_record_key | varchar(128)/NO |
| effective_from | datetime(3)/YES |
| effective_to | datetime(3)/YES |
| status | varchar(32)/NO |
| version | int unsigned/NO |
| creator | varchar(64)/NO |
| create_time | datetime(3)/NO |
| updater | varchar(64)/NO |
| update_time | datetime(3)/NO |
| deleted | tinyint/NO |

## 其他原表与写入边界

实际配置库fb_service为10列/462213行，全部非NULL的合同号、SN、等级、日期、保修字段、延期标记需按原含义保留；remark全NULL，state有213658条非NULL，不因此擅自排除后续数据。warranty_info为15列/0行，warranty_change_logs为8列/55行。本任务新建的三个对应表及关系表当前均0行，不等于其结构已批准或可以自行删除。全部字段、类型及非空统计见下述只读证据。

后续需要把普通维保/续保记录、登记、变更及项目关系分别结合既有业务能力做整表方案。原view_warranty没有审计日期，不能用其中syncTime伪造创建时间。31条既有维保记录和31条当前投影保护，118条本任务记录只有正式承接、所有值与依赖核验且用户批准后才清理冗余31列。

证据：任务私有warranty-original-materials-whole-tables-readonly-20261010.json、warranty-formal-reuse-normalized-readonly-20261010.json、warranty-contract-reference-readonly-20261010.json及原v4-maintenance-warranty-payment-readonly-review-20261008T223739628093Z.json。所有审查writes=0。首次未限定发货原键的查询超时，改按118原发货键精确范围完成；没有提高超时掩盖范围问题或修改业务数据。
