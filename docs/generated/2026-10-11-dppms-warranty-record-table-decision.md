# ast_device_warranty_record 整表裁决方案

## 最新裁决：计算结果不独立同步

2026-10-11，用户明确：“维保信息是发货和维保期限计算得到的数据，不需要单独同步”。因此下方19列记录承接、两个原区间扩展及缓存日差处理方案不再作为待批准实施方案；不创建这些扩展，也不将 view_warranty 计算结果继续独立写入维保历史。完整36原字段清单保留作为来源核查与计算依赖说明。

已核实旧视图定义包含发货起始日期/期限、合同保修年数、装箱单日期及服务权利覆盖的计算，diff/old_diff按查询时间计算。该依赖证明不替换用户指定的原 view_warranty 材料，不将 fb_service 权利覆盖误认为普通发货字段。正常计算功能须复用正式输入实体，尚未明确承接的独立权利覆盖继续登记，不以“计算结果不迁”丢弃其原始输入。

只读复核：现表149条全部50列未变化，本任务118条 source_system=DPPMS、source_key=view_warranty:* 为重复计算结果；31条原历史及31条当前投影未变化。用户进一步裁决“现存记录及结构暂保留”，因此不撤回118条、不清理31列、不创建扩展，也不修改来源、成功回执或绑定。后续新迁移不独立写入 view_warranty 结果，报告明确列出不独立同步原因；原计算输入的承接不受本次排除替代。私有全字段预览：derived-warranty-retirement-preview-20261011.json。

状态：WHOLE_TABLE_DECISION_PENDING / BLOCKED_BY_SPEC。此前V412在既有19列上追加31列，现表50列/149行：31条原记录、118条本任务DPPMS记录。该方案仅供裁决，没有改代码、结构或业务数据。保留用户指定view_warranty原36列材料，不替换当前缺失的来源对象。

建议最终仍为19个既有物理列，新增表/物理列均0；31个V4新增列仅在逐字段正式承接、存量保护及依赖核验完成后清理。保留149条ID及正式审计；31条原历史和31条当前维保投影保护，不据本批历史自动覆盖当前投影。

本次整表需要决定：两个独立原区间日期是否通过现有正式实体扩展 original_period_start / original_period_end（DATETIME）保存；两个无法确定计算基准的旧缓存日差 diff/old_diff 是否明确不迁入（留原材料作为排除证据，不计承接）；等级及旧合同类型名称是否按真实原码/名称补入既有字典，不增字段。若不采用，请一次指定对应原字段的正式承接点。

2026-10-11重新核对：118条保修原材料全部36列、50列149条现值以及31条当前投影不变；118条代理商与通过原执行单绑定匹配的正式agent_name一致，118条syncTime与精确发货的sync_time一致。消除了此前仅凭同名相关方/同步字段名称猜配的候选。不使用项目名称猜配合同、设备或人员。

本批原新/旧起止日期均相同，但不能推广为全历史重复；原列仍独立列明。当前起止日期118条全部午夜，复用已有DATE无损。后续非午夜、负数/非整数月数、关联不能唯一的记录阻断并登记，不自行截断或丢值。原材料无创建/更新人时间，保持已迁入正式审计，不把syncTime当创建时间。

## 19个既有物理列

| 字段 | 类型/可空 | 处理 |
|---|---|---|
| id | bigint/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| device_sn | varchar(100)/NO | 原barcode；精确设备SN |
| warranty_start_date | date/YES | 原warrantyStartTime，午夜日期无损承接 |
| warranty_end_date | date/YES | 原warrantyEndTime，午夜日期无损承接 |
| warranty_months | int unsigned/YES | 原warrantyMonth，月数；不从合同年数反推来源覆盖值 |
| warranty_grade | varchar(32)/YES | 原gradeCode |
| warranty_contract_no | varchar(64)/YES | 原contract_code |
| extended | bit(1)/YES | 保留既有值；不得把原warranty年数转换为是否延期 |
| remark | varchar(500)/YES | 保留既有备注，不塞未匹配原字段 |
| source_system | varchar(32)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| source_key | varchar(128)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| source_version | varchar(64)/YES | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| version | int unsigned/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| creator | varchar(64)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| create_time | datetime(3)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| updater | varchar(64)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| update_time | datetime(3)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| deleted | bit(1)/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |
| tenant_id | bigint/NO | 保留既有正式身份、版本或审计用途；技术幂等沿已存在INT/PLT绑定 |

## V4追加31列逐项承接与撤回条件

| V4列 | 现类型/可空 | 全表非NULL数 | 正式承接或待裁决处理 |
|---|---|---|---|
| combined_device_sn | text/YES | 17 | ast_device_shipment.internal_serial_no |
| product_code | text/YES | 118 | ast_device_shipment.product_code / 正式产品目录编码 |
| product_name | text/YES | 118 | 正式产品目录product_model及已批准目录型号属性 |
| product_description | text/YES | 118 | 正式产品目录已批准dppms_catalog_labels.productName |
| grade_name | text/YES | 118 | 既有SYSTEM字典的原等级码/名称；字典值及Owner接入待本次裁决 |
| shipment_time | datetime(3)/YES | 118 | ast_device_shipment.shipment_time |
| warranty_flag | text/YES | 118 | com_shipment_contract_reference.warranty_years（已批准整数年数） |
| source_warranty_start_time | datetime(3)/YES | 118 | 本批午夜原值复用warranty_start_date；以后非午夜阻断精度冲突，不截断 |
| source_warranty_end_time | datetime(3)/YES | 118 | 本批午夜原值复用warranty_end_date；以后非午夜阻断精度冲突，不截断 |
| original_warranty_start_time | datetime(3)/YES | 118 | 候选：正式维保记录实体扩展 original_period_start（DATETIME）；既有列无独立原区间含义，不因本批同值删原值 |
| original_warranty_end_time | datetime(3)/YES | 118 | 候选：正式维保记录实体扩展 original_period_end（DATETIME）；与当前区间独立保留 |
| remaining_days_at_source | int/YES | 118 | 候选排除：原diff为查询/缓存过期日差，符号与本列含义相反；计算基准未明确，不当作当前期限或重算冒充原值。是否排除由用户裁决 |
| original_remaining_days_at_source | int/YES | 118 | 候选排除：原old_diff为旧区间查询/缓存过期日差，与前项同理；未经裁决保持原列和值 |
| contract_type | text/YES | 118 | com_shipment_contract_reference.contract_type |
| contract_type_name | text/YES | 118 | 既有SYSTEM旧合同类型字典的精确编码/名称；值和Owner接入待本次裁决 |
| project_name | text/YES | 118 | com_shipment_contract_reference.project_name |
| customer_name | text/YES | 118 | com_shipment_contract_reference.customer_name |
| final_customer_name | text/YES | 118 | proj_project_party FINAL_CUSTOMER/party_name，精确项目关系，不混用签约客户 |
| agent_name | text/YES | 118 | 经原执行单成功绑定复用com_crm_execution_order.agent_name，118/118逐值一致 |
| office_code | text/YES | 118 | com_shipment_contract_reference.department_code |
| office_name | text/YES | 118 | com_shipment_contract_reference.department_name |
| market_code | text/YES | 118 | com_shipment_contract_reference.market_code |
| market_name | text/YES | 118 | com_shipment_contract_reference.market_name |
| system_code | text/YES | 118 | com_shipment_contract_reference.system_source_key（原ID，不伪装为编码） |
| system_name | text/YES | 118 | com_shipment_contract_reference.system_name |
| secondary_sn | text/YES | 0 | ast_device_shipment.secondary_sn |
| secondary_item | text/YES | 0 | ast_device_shipment.secondary_item及正式产品目录 |
| secondary_product_name | text/YES | 0 | 副产品正式目录型号及已批准目录属性，缺失精确关系阻断 |
| secondary_product_description | text/YES | 0 | 副产品正式目录已批准名称属性，缺失精确关系阻断 |
| source_sync_time | datetime(3)/YES | 118 | ast_device_shipment.sync_time，118/118逐值一致 |
| source_shipment_key | text/YES | 118 | 既有迁移来源绑定精确查询正式发货，不另复制原技术键 |

## 原view_warranty全部36列

| 原字段 | 原类型 | 已有承接点或待明确项 | 核查与处理原因 |
|---|---|---|---|
| newId | bigint(20) | 既有INT/PLT来源绑定 | 原缓存技术ID；不是设备或合同业务键，不再复制为实体业务列 |
| id | bigint(20) | 正式ast_device_shipment及既有来源绑定 | 本批118项按原发货ID＋SN各匹配唯一正式发货；正式关系的具体读写方案须随整表裁决，不能仅删除引用列 |
| barcode | varchar(50) | ast_device_warranty_record.device_sn | 既有正式设备序列号 |
| comBarCode | varchar(50) | ast_device_shipment.internal_serial_no | 本批已建立精确发货对应；正式组合序列号字段 |
| old_warrantyEndTime | datetime | 候选正式实体扩展original_period_end（DATETIME） | 与新计算区间独立保留；本批同值不证明全历史冗余，待用户批准后由AST维保记录Owner承接 |
| warrantyEndTime | datetime | ast_device_warranty_record.warranty_end_date | 同上；属于保修区间，不能代替安装/验收日期 |
| old_diff | int(7) | 候选不迁入；等待用户明确批准排除 | 旧区间缓存日差，计算基准未核实；不能当当前剩余天数，未批准前保留原列与值 |
| diff | int(7) | 候选不迁入；等待用户明确批准排除 | 查询/缓存过期日差，不是当前期限事实，正值符号与剩余天数相反；不能用当前时间重算冒充原值，未批准前不丢弃 |
| warrantyStartTime | datetime | ast_device_warranty_record.warranty_start_date | 本批118值均午夜，可无损复用DATE；全历史非午夜时须重新裁决精度，不能截断 |
| old_warrantyStartTime | datetime | 候选正式实体扩展original_period_start（DATETIME） | 原区间独立保存；本批同值不推广为全历史重复，待用户批准后接入正式Owner |
| item | varchar(16) | ast_device_shipment.product_code / ast_product_official_info.product_code | 本批118原码均有尾部空格；按已有产品编码trim匹配118唯一目录，不创建带空格的第二产品编码；原原文留证据 |
| describe_ | varchar(255) | 正式产品dppms_catalog_labels.productName | 118原名称逐值匹配；已有CRM名称保持，不覆盖为旧名称 |
| itemName | varchar(255) | 正式产品目录product_model及dppms_catalog_labels.productModel | 本批118原型号经已批准实体扩展逐值匹配 |
| gradeName | varchar(125) | 既有system_dict_type/system_dict_data（维保等级字典候选） | 原fb_warranty_grade全部7列/12行已读，正式库未有同义等级字典；建议仅以原码/原名称补正式字典，完整目录有效期等原属性仍归已批准基础数据域另审，不在本表丢弃 |
| gradeCode | varchar(25) | ast_device_warranty_record.warranty_grade | 既有正式维保等级码；本批均53030001 |
| packdate | datetime | ast_device_shipment.shipment_time | 复用发货业务时间，不存到保修镜像 |
| contract_code | varchar(25) | warranty_contract_no及com_shipment_contract_reference.contract_no | 118均有唯一正式发货合同引用和项目合同关系 |
| contract_type | int(11) | com_shipment_contract_reference.contract_type | 按既有STRING转换，118原0与正式0一致；com_contract.contract_type为空，不能声称它已承接 |
| contract_type_name | varchar(25) | 既有system_dict_type/system_dict_data（旧合同类型字典候选） | 原sys_state_or_type全部7列已读，stCode=01的精确码/名称建议复用正式字典；不覆盖现平台其他合同分类 |
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
| syncTime | datetime | ast_device_shipment.sync_time | 经原发货ID＋SN对应，118/118时间与正式sync_time逐值一致；不用source_updated_at或当前同步时间代替 |
| describe_2 | varchar(255) | 副产品正式目录已批准名称属性 | 本批全NULL；非空须保留副产品自身原值 |
| itemName2 | varchar(255) | 副产品正式目录型号/已批准目录名称属性 | 本批全NULL；非空须先精确关联目录，不能从主产品复制 |
| agentName | varchar(500) | com_crm_execution_order.agent_name（精确迁入执行单） | 经原不可变执行单记录和既有目标绑定关联，118/118代理商名称与正式执行单逐值一致，不按相关方名称猜角色 |
| finalCustomerName | varchar(255) | proj_project_party.party_role=FINAL_CUSTOMER / party_name | 118均与本轮已批准正式实际最终用户逐值匹配；当前产品使用方业务含义保持 |

## 关联及清理边界

发货关联：原发货ID＋SN唯一对应正式发货，保留INT/PLT来源绑定，业务查询经AST Owner及稳定跨模块接口读取正式合同/产品/执行单/项目相关方。不得在PROJ或通用扩展模块直接查COM/AST表。原自增newId仍作为技术证据，不当业务关联键。

原等级fb_warranty_grade全部7列及合同类型sys_state_or_type全部7列已审计；本表仅决定原视图中等级/合同类型码和标签的正式查用，完整词典其他属性仍归已批准基础数据领域，另按SYSTEM既有整表映射确认。不得以本表样本为空丢弃以后非空的副产品资料。

两个原区间扩展候选仅保存明确业务属性，AST维保记录Owner负责写入和查询；不得放到迁移中间快照冒充承接。两个日差排除候选尚未获准，没有删除原字段或数值。

当前四张本任务V414维保附加表（服务、登记、变更、项目关联）仍为空，未通过本次方案自动批准。原fb_service维护的是合同/SN/等级/保修日期等权利资料，不能冒用已裁决暂不迁入的pm_project_maintenance维护记录。其他原表仍按各自全部字段另行整表审查。

批准后先做迁移Owner预览及实际正式读回，保护所有原业务值、现存审计、当前投影、来源材料/回执；清理31列前再核全表、索引、任务映射和调用依赖。仓库SQL同步清理撤回定义，不加了再删；已执行Flyway回执不改写，生产基线切换另行落实。

私有只读证据：warranty-final-existing-field-audit-20261011.json、warranty-original-materials-whole-tables-readonly-20261010.json、warranty-dictionary-originals-readonly-20261010.json、warranty-contract-reference-readonly-20261010.json。writes=0。
