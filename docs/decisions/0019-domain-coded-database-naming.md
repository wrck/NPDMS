# ADR-0019：按领域编码划分数据库表名

## 状态

Accepted

## 日期

2026-08-13

## 适用范围

- PRD 基线：V1.6
- 需求范围：全部涉及持久化、数据迁移和跨领域引用的 Requirement ID
- 数据模型门禁：P3-E09 / AI-MIG-000
- 适用对象：新平台业务表、迁移承接表、同步表及其字段；基础平台原生表不在本 ADR 的重命名范围内

## 背景

当前物理模型中的业务表统一使用 `pms_` 前缀。`PMS` 是业务系统名称，不能表达表的数据 Owner，也无法在数据库层直观看出领域边界。与此同时，部分既有表名采用缩写或历史物理概念，可能与正式领域对象名称不一致。

数据迁移仍需复用旧库数据元中已经证实的业务含义，但不能机械继承旧库 camelCase、拼音、历史缩写或系统名称前缀。

## 决策

### 1. 表名结构

新平台业务表统一采用：

```text
<domain_code>_<full_domain_object_name>
```

- `domain_code` 使用正式13领域编码的小写形式：`proj`、`sol`、`imp`、`acc`、`cut`、`srv`、`cus`、`ast`、`com`、`res`、`kno`、`ana`、`plt`。
- 删除业务系统名称前缀 `pms_`。
- 表名必须保留正式领域模型中的全部对象语义组件，默认使用完整英文词；仅允许使用本ADR明确登记的表名标准缩写。
- 表名当前只允许 `config`、`sn` 两个标准缩写。同一表名词根必须全局统一，例如统一使用 `relation`，不得同时出现 `rel` 和 `relation`。
- 不使用 `main`、`info`、`data`、`detail` 代替正式对象名称。
- Bounded Context 编码不是领域编码。Device Access & Collection 等 Context 所属表仍以其正式需求 Owner 领域 `plt` 为表名前缀，不另设 `dac` 表名前缀。

受控缩写如下；“表名”列为“否”的缩写只能用于字段：

|完整词|标准缩写|表名|适用含义|
|---|---|---:|---|
|relation|`rel`|否|两个或多个业务对象之间的关系字段|
|reference|`ref`|否|引用字段|
|configuration|`config`|是|配置对象；不得用于表示运行结果或设备采集结果|
|mapping|`map`|否|键、值或对象之间的映射字段|
|serial number|`sn`|是|设备序列号|
|identifier|`id`|否|技术标识或对象引用|
|number|`no`|否|单据号、流水号或外部编号|
|quantity|`qty`|否|有业务计量单位的数量|

除已批准的 `config`、`sn` 外，表名中的 `relation`、`reference`、`mapping`、`assignment`、`revision`、`snapshot`、`history`、`record`全部使用完整英文词。新增缩写必须先登记适用范围，再用于DDL，禁止开发人员自行创造。

### 2. 字段命名结构

- 同一业务含义使用同一字段名。
- 表内字段不重复本表领域编码或完整对象名称；只有跨领域引用、同表存在多个同名角色或脱离上下文会产生歧义时，才增加领域编码或角色词。
- 字段在无业务歧义时尽可能简短，删除重复上下文和不必要的下划线，但不得通过难懂缩写换取长度。
- 允许的通用缩写限定为已稳定使用且不会产生歧义的 `id`、`no`、`qty`、`sn`、`ip`、`url`。
- `id` 表示技术标识或引用，`code` 表示业务编码，`no` 表示单据号/流水号/外部编号，`qty` 表示业务数量，`count` 表示记录条数。
- 时间字段使用“动作原形 + `_time`”，日期字段使用“业务词 + `_date`”。例如统一使用 `submit_time`，不混用 `submitted_time`。
- 旧库数据元已有规范业务含义时优先复用该含义和成熟术语；旧库物理字段名只作为追溯证据，不自动成为新字段名。

### 3. 外部系统镜像及技术支撑表

- CRM、ERP、MES 等外部系统镜像按平台内实际业务 Owner 领域归类，不以外部系统名作为一级分区。
- 外部系统名可以作为完整对象名的一部分，用于说明镜像来源，例如 `com_crm_execution_order`。
- 同步批次、迁移源记录、外部键映射和迁移问题等平台支撑对象归 `plt`。
- 经营分析读模型归 `ana`，不得因来源为项目数据而归 `proj`。

## 当前52张物理表的目标命名裁决

下表是 P3-E09 的表级命名输入，只批准目标名称，不等同于批准当前 DDL 哈希、字段、约束或生产迁移。

|序号|当前表名|目标表名|领域|裁决说明|
|---:|---|---|---|---|
|1|`pms_customer`|`cus_customer`|CUS|客户主档|
|2|`pms_customer_contact`|`cus_customer_contact`|CUS|保留完整 CustomerContact 对象名|
|3|`pms_product`|`ast_product`|AST|产品/设备型号主数据归资产领域|
|4|`pms_project`|`proj_project`|PROJ|项目主档|
|5|`pms_project_relation`|`proj_project_relation`|PROJ|项目关系|
|6|`pms_project_party`|`proj_project_party`|PROJ|项目参与方|
|7|`pms_project_company_department_rel`|`proj_project_company_department_relation`|PROJ|表名统一使用完整 `relation`|
|8|`pms_project_member`|`proj_project_member_assignment`|PROJ|对齐 ProjectMemberAssignment 正式对象|
|9|`pms_business_document`|`plt_business_document`|PLT|公共文档对象|
|10|`pms_document_version`|`plt_document_version`|PLT|文档版本|
|11|`pms_deliverable_template`|`acc_deliverable_template`|ACC|交付件模板归验收与闭环领域|
|12|`pms_project_deliverable`|`acc_project_deliverable`|ACC|保留完整 ProjectDeliverable 名称|
|13|`pms_portfolio`|`proj_project_portfolio`|PROJ|对齐 ProjectPortfolio 正式对象|
|14|`pms_portfolio_project_rel`|`proj_project_portfolio_member`|PROJ|对齐 ProjectPortfolioMember 正式对象|
|15|`pms_contract`|`com_contract`|COM|合同主档|
|16|`pms_contract_receivable`|`com_contract_receivable`|COM|合同回款事实|
|17|`pms_shipment_contract_ref`|`com_shipment_contract_reference`|COM|表名统一使用完整 `reference`|
|18|`pms_shipment_package`|`com_shipment_package`|COM|发货装箱对象|
|19|`pms_project_contract_rel`|`com_project_contract_relation`|COM|表名统一使用完整 `relation`|
|20|`pms_sales_order`|`com_sales_order`|COM|销售订单|
|21|`pms_order_contract_rel`|`com_order_contract_relation`|COM|表名统一使用完整 `relation`|
|22|`pms_sales_order_line`|`com_sales_order_line`|COM|销售订单行|
|23|`pms_project_order_line_scope`|`com_delivery_scope`|COM|对齐 DeliveryScope 正式领域对象；不是对象名缩写|
|24|`pms_device_sn`|`ast_device_sn`|AST|`sn`为已批准的表名标准缩写|
|25|`pms_device_shipment_event`|`ast_device_shipment_event`|AST|设备发货事件|
|26|`pms_project_device_assignment`|`ast_device_project_assignment`|AST|设备项目归属；后续仍需按当前归属与历史对象拆表裁决|
|27|`pms_device_relation`|`ast_device_relation`|AST|设备关系|
|28|`pms_device_configuration`|`ast_device_configuration`|AST|设备配置|
|29|`pms_device_configuration_feature`|`ast_device_configuration_feature`|AST|设备配置功能|
|30|`pms_device_configuration_service`|`ast_device_configuration_service`|AST|设备配置服务|
|31|`pms_network_topology`|`ast_network_topology`|AST|网络拓扑|
|32|`pms_topology_device_rel`|`ast_network_topology_device_relation`|AST|保留完整 NetworkTopologyDeviceRelation 语义|
|33|`pms_device_version`|`ast_device_version`|AST|设备版本|
|34|`pms_product_release`|`ast_product_release`|AST|产品发布版本|
|35|`pms_technical_advisory`|`kno_technical_advisory`|KNO|技术公告/建议知识对象|
|36|`pms_technical_advisory_read`|`kno_technical_advisory_read_record`|KNO|阅读记录，不以动作词直接结尾|
|37|`pms_technical_advisory_product`|`kno_technical_advisory_product_relation`|KNO|公告与产品关系|
|38|`pms_device_advisory_match`|`kno_device_technical_advisory_match`|KNO|设备与技术公告匹配|
|39|`pms_service_incident`|`srv_service_incident`|SRV|服务故障事件|
|40|`pms_incident_device_rel`|`srv_service_incident_device_relation`|SRV|保留完整 ServiceIncidentDeviceRelation 语义|
|41|`pms_crm_execution_order`|`com_crm_execution_order`|COM|CRM执行单镜像由合同订单履约领域承接|
|42|`pms_crm_execution_config`|`com_crm_execution_config`|COM|`config`为已批准的表名标准缩写|
|43|`pms_order_execution_rel`|`com_order_execution_relation`|COM|订单与执行单关系|
|44|`pms_order_line_execution_rel`|`com_order_line_execution_relation`|COM|订单行与执行单关系|
|45|`pms_execution_merge_batch`|`com_execution_order_merge_batch`|COM|明确被合并对象为执行单|
|46|`pms_execution_merge_member`|`com_execution_order_merge_member`|COM|明确成员所属合并对象|
|47|`pms_order_change_rel`|`com_order_change_relation`|COM|订单变更关系|
|48|`pms_sync_batch`|`plt_sync_batch`|PLT|通用同步批次|
|49|`pms_migration_source_record`|`plt_migration_source_record`|PLT|迁移源记录|
|50|`pms_external_key_map`|`plt_external_key_mapping`|PLT|表名统一使用完整 `mapping`|
|51|`pms_migration_issue`|`plt_migration_issue`|PLT|迁移问题|
|52|`pms_project_delivery_summary`|`ana_project_delivery_summary`|ANA|经营分析读模型|

## 字段首批一致性裁决

|编号|当前字段|目标字段|依据|
|---|---|---|---|
|NAM-001|`pms_project_deliverable.submitted_time`|`acc_project_deliverable.submit_time`|同一“提交时间”统一为动作原形；旧库 `submitTime` 提供业务含义证据|
|NAM-002|`pms_crm_execution_config.quantity`|`com_crm_execution_configuration.qty`|业务数量统一使用 `qty`|
|NAM-003|`pms_crm_execution_config.borrow_quantity`|`com_crm_execution_configuration.borrow_qty`|同一数量词根统一|
|NAM-004|`pms_project_delivery_summary.pending_quantity_count`|`ana_project_delivery_summary.pending_qty_count`|字段表示待处理数量类记录条数，`qty` 与 `count` 含义并存|
|NAM-005|`pms_service_incident.resolution`|`srv_service_incident.solution`|数据元和旧库均以“解决方案”表达该业务含义|
|NAM-006|`pms_project_party.contact_phone`|`proj_project_party.phone`|表内只有一个电话角色；与联系人数据元 `phone` 统一|

## 不得机械缩短的情形

- 同表存在多个同类角色，例如 `contact_phone` 与 `sales_representative_phone`。
- 需要区分业务时间和基础平台审计时间，例如 `contract_create_time` 与 `create_time`。
- 字段表达跨领域引用且对象类型不唯一。
- 删除词语会改变正式领域对象或数据元业务含义。
- 长名称由多个真实角色或来源维度构成，而不是重复上下文。

## 迁移与实施影响

1. 当前 DDL、目标字段目录、对象—目标表映射、字段映射、约束清单、迁移校验和 release manifest 必须在同一次 AI-MIG-000 裁决中更新到同一批准哈希。
2. 表名调整不得通过修改已经执行的 Flyway 脚本完成；实施仓库必须使用新的前向迁移。
3. 旧库保持只读；禁止因新表命名变化对旧库执行 DDL/DML，也禁止跨库 SQL。
4. API、事件和领域对象名称不因物理表名变化而改变业务语义。
5. 任何无法唯一归属到13领域之一的表必须保持 `BLOCKED_BY_SPEC`，不得临时恢复 `pms_` 或自创 Context 前缀。

## 验收标准

- 全部新平台业务表都满足 `<13领域编码>_<完整领域对象名称>`。
- 不存在以 `pms_` 开头的新平台业务表。
- 表名仅使用已批准的 `config`、`sn`；不存在 `rel/relation`、`ref/reference`、`map/mapping`等混用或其他未登记缩写。
- 表名唯一，且每张表只有一个领域 Owner。
- 同义字段通过机器清单检测，差异必须有明确业务角色或数据元证据。
- AI-MIG-000 未批准前，P3-E09继续阻断数据模型基线、历史迁移和切换。

## 后果

- 数据库表名可直接体现领域 Owner，降低跨领域直接访问和迁移错配风险。
- 表名长度增加，但换取稳定、可检索且无歧义的模型；字段仍保持必要的简洁性。
- 现有 DDL 及其92类来源证据需要统一重建，不能只做文本替换。
- `domain-object-table-map.json` 等机器契约必须以最终目标表名为准，防止文档与DDL漂移。
