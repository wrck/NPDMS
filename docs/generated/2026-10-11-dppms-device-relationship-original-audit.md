# 主子设备关系原表及现有承接只读核查

状态：SOURCE_AND_EXISTING_STRUCTURE_VERIFIED / WHOLE_TABLE_PLAN_PENDING。本文件只登记原表、现有结构、映射及实际差异，不批准新增表列、索引修改、记录纠正或排除原行。

## 实际来源与现有承接

原fb_shipment_barcode_relation完整8列、65406条；正式ast_device_relationship实际19列、65046条，全部tenant_id=1、source_system=DPPMS、relationship_type=EXTRA_SN，均未删除。逐原ID关联后，已承接65046条的主/副SN、主/副产品编码、合同号和原更新时间字符串全部原值一致；原创建时间与effective_from也全部一致。

数量差360不是未查明的分页遗漏：既有同步模板实际使用WHERE sn1<>sn2；未匹配的360条全部sn1=sn2且item1=item2。旧原行仍存在，未获准排除或删除；是否没有双设备关系事实及如何列明排除，须交用户裁决，不能自行放宽禁止自关联的正式约束。

既有模板将原updatetime放入source_version字符串，未映射到正式update_time；所有65046条正式create_time不同于原createtime，15706条非空原updatetime不同于正式update_time，另49340条已承接原updatetime为空。当前正式审计日期为早先实际迁入时间，不能宣称原历史审计已经承接。正式update_time非空及原NULL处理需在整表方案明确，不能凭其他表的限定裁决自动覆盖。

## 全8原字段映射与缺口

| 原字段 | 原类型/可空 | 已有正式字段及核查结论 |
|---|---|---|
| id | INT / 非空，非自增 | 既有source_key完整保存原ID；技术来源绑定继续由迁移/同步维护，不再新增镜像 |
| sn1 | VARCHAR(50) / 可空 | source_device_sn；已承接原值一致，全源实际无NULL |
| item1 | VARCHAR(15) / 可空 | source_item；已承接原值一致，字段是关系两侧的主产品编码，并非新拟source_*镜像 |
| sn2 | VARCHAR(50) / 可空 | target_device_sn；已承接原值一致，全源实际无NULL |
| item2 | VARCHAR(15) / 可空 | target_item；已承接原值一致 |
| contract | VARCHAR(25) / 可空 | contract_no；已承接原值一致 |
| createtime | VARCHAR(50) / 可空 | effective_from已转换一致，create_time未保存原时间；全源实际非NULL。创建及更新日期的毫秒以外小数全为0，现有DATETIME(3)可保存实际精度 |
| updatetime | VARCHAR(50) / 可空 | source_version已有原字符串；正式update_time缺少历史日期承接，全源49658条原NULL（含未匹配行） |

## 既有正式表全部19列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| source_device_sn | varchar(100) | NO |
| target_device_sn | varchar(100) | NO |
| relationship_type | varchar(32) | NO |
| contract_no | varchar(64) | YES |
| effective_from | datetime(3) | YES |
| effective_to | datetime(3) | YES |
| source_system | varchar(32) | NO |
| source_key | varchar(128) | NO |
| source_version | varchar(64) | YES |
| version | int unsigned | NO |
| creator | varchar(64) | NO |
| create_time | datetime(3) | NO |
| updater | varchar(64) | NO |
| update_time | datetime(3) | NO |
| deleted | bit(1) | NO |
| tenant_id | bigint | NO |
| source_item | varchar(15) | YES |
| target_item | varchar(15) | YES |

现有唯一键为租户＋来源系统＋来源键；禁止source_device_sn=target_device_sn，日期结束不早于开始。V351已撤回旧SN外键，不能把不存在设备外键误报为当前阻断原因。当前effective_to全为空；这不能自动推导需要激活设备当前组合关系或重建任何设备投影。

## 原系统作用和两条实现路径

业务归属同时有既有设计依据：Accepted的ADR-0003要求按合同完整保留主SN—附加SN关系，历史查询不能只读设备缓存；F-AST-001设计第2.5节明确非装配主附SN进入ast_device_relationship、物理装配才进入ast_device_assembly。它有先前业务规划，不是仅因本任务发现一张物理表就认定为权威。ADR旧物理名称与当前AST名称须结合该设计阅读。2026-09-28用户后续明确摘除device-sn同步中的三种设备主档/投影步骤；本轮不能因旧ADR缓存条款自行把这些步骤加回。

旧sql-map-project-config.xml在发货信息查询中按合同（去掉-C）及主SN关联fb_shipment_barcode_relation，再以item2关联fb_items取得副产品名称/型号。它表达合同下主/附加设备及产品关系，不等同于项目父子树。

已有generic-templates.json DEVICE_RELATION将该原表同步到ast_device_relationship；generic-targets.json注册正式目标及其不可变保护。正常DeviceRelationshipQueryService实际只查询设备项目历史和客户关系，并不读取主子设备表，不能将该类存在当作主子设备查询功能闭环已经具备。

本任务先前的DppmsDeviceSourceRelationshipService仍引用ast_device_source_relationship。实际SHOW TABLES确认该表不存在；该原型不能作为现有正式承接点，亦不得据此自行建表。本批10项目来源为0条只证明本批没有此事实，不能代替65406条全历史的字段和非空查询验收。后续优先复用实际ast_device_relationship，Owner写入、查询授权、审计规则及360条处理须完整明确后才实施，不直接用原型替代。

## 证据和验证边界

任务私有device-relationship-original-schema-readonly-20261011.json及device-relationship-original-comparison-readonly-20261011.json，覆盖原8列、现19列、索引、全量原ID和六项原值、日期与360条未匹配原行。查询只读，schemaWrites=0、businessWrites=0。数量及值一致不代表回执保护、当前关系投影和真实业务查询已经验收。

代码依据：SQL/migrations/V112__fast001_device_relationship_location_warranty.sql、V351__drop_device_sn_foreign_keys.sql、V382__shipment_barcode_raw_landing.sql；pms-module-integration/src/main/resources/sync/generic-templates.json、generic-targets.json；AST现有查询及DppmsDeviceSourceRelationshipService；旧PMS-struts/config-ibaits/sql-map-project-config.xml:2230。
