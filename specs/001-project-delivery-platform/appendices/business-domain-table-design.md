# 项目交付平台业务领域表结构正式设计

## 1. 结论

正式模型采用“**业务事实规范化 + 发生时引用固化 + 来源数据完整留痕 + 高频查询可重建读模型**”四层结构：

1. 业务表保存稳定、可解释、可约束的业务事实；高频检索、关联、显示和导出的引用编码与名称在业务发生时受控固化；
2. 每条旧记录完整写入`pms_migration_source_record.source_payload`，旧字段即使不进入业务列也不丢失；
3. `pms_external_key_map`保存来源记录和一个或多个目标记录之间的幂等映射；
4. 项目树路径、当前附加SN和项目交付汇总允许作为可重建读取缓存，其权威来源必须明确；读模型从业务记录的发生时引用值重建，不从当前主档偷偷刷新名称；
5. CRM执行单和配置只作辅助关联证据，实施权威范围仍是ERP订单行和项目订单行实施范围；
6. 所有业务外键使用`(tenant_id, id)`复合引用，数据库阻止跨租户误关联；全局ID由基础平台分布式ID生成器赋值。

全平台目标语义统一如下：公司是业务主体，目标表、字段、DTO和接口统一使用`company_*`；部门是共享且独立的主数据，统一使用`department_*`。公司与部门在项目归属、成员和权限范围中可以作为同一业务组合保存，但不建立可从部门反推公司的全局主数据从属关系。`org_*`、`organization*`仅允许出现在旧来源字段名和原始载荷中，不得作为目标标识。

物理设计见[`project-order-physical-schema.mysql.sql`](project-order-physical-schema.mysql.sql)，字段目录见[`../evidence/migration/target-field-catalog.jsonl`](../evidence/migration/target-field-catalog.jsonl)。

## 2. 设计依据

- `需求/数据元.xlsx`已按单元格直接读取，结构化证据包含隐藏列值，不使用截图或OCR；
- 活动结构页包含3,931条物理字段证据，归并后为259张表、3,908个唯一表字段，另有82条业务数据元；
- 语义页包含197条来源记录，按“分区+名称”归并为108个唯一数据元；
- 18张项目、合同、执行单、订单、SN和发货核心旧表已结合旧库数据画像形成326字段显式映射；
- `sms_ofst_contract_head_sap`按合同号和所属公司识别合同主档，`fb_contract`只表示发货记录合同归属；
- `fb_shipment`是装箱单，`fb_shipment_barcode`是设备发货事件，`fb_shipment_barcode_relation`是合同维度主附加SN关系；
- CRM执行单配置不全，安服属性只能由已取得的安服产品配置作正向证据，缺少配置不能推断为非安服。

未被这些证据证明的旧字段不猜测业务语义，处置为`SOURCE_ONLY`并保留完整原值。

## 3. 领域模型

| 领域 | 权威表 | 粒度与职责 |
|---|---|---|
| 客户与联系人 | `pms_customer`、`pms_customer_contact` | 一客户一主档；联系人按客户多行保存；客户联系人所在部门使用`customer_department_name`，不关联平台内部部门 |
| 项目树 | `pms_project`、`pms_project_relation` | 正式子项目使用`parent_id`；组合、扩容、续采等非树关系不得混入父子树 |
| 项目公司、部门与人员 | `pms_project_company_department_rel`、`pms_project_member`、`pms_project_party` | 公司与部门主数据分离，但同一项目业务角色的公司—部门组合在一行保存；系统用户和外部参与方分开建模 |
| 项目组合 | `pms_portfolio`、`pms_portfolio_project_rel` | 组合成员不改变项目父子关系，一个项目可属于多个组合 |
| 合同与回款 | `pms_contract`、`pms_contract_receivable` | 合同以“所属公司+合同号”唯一；回款是合同下的同步事实，不反向覆盖合同主档 |
| 项目、合同、订单 | `pms_project_contract_rel`、`pms_order_contract_rel` | 项目1:N合同、合同N:N订单均以关系表表达，不在项目或订单头固化单合同外键 |
| ERP订单 | `pms_sales_order`、`pms_sales_order_line` | 订单头与订单行1:N；订单行固化父订单自然键、公司和客户发生时值，是产品、数量、发货和实施跟踪的基础粒度 |
| 项目实施范围 | `pms_project_order_line_scope` | 一行表示“项目节点分配到ERP订单行的实施数量”；同时固化项目、客户、公司—部门组合、负责人、订单行和物料显示值，一个订单行可拆给多个正式子项目 |
| CRM执行单辅助 | `pms_crm_execution_order`、`pms_crm_execution_config` | 保存当前可获得的CRM镜像；不要求全量配置，不作为实施范围权威表 |
| 订单与执行单 | `pms_order_execution_rel`、`pms_order_line_execution_rel` | 订单级和订单行级分别建表，避免双可空外键和粒度矛盾 |
| 合并与改单 | `pms_execution_merge_batch`、`pms_execution_merge_member`、`pms_order_change_rel` | 合并成员不限数量；取消、新单、退货、替代等保留订单血缘 |
| 产品 | `pms_product` | 产品编码、型号、产品线和安服产品标志集中维护；安服项目由配置行判定 |
| 发货与SN | `pms_shipment_contract_ref`、`pms_shipment_package`、`pms_device_sn`、`pms_device_shipment_event` | 合同归属、装箱单、设备身份、每次发放/退回/RMA事件分别保存，不互相复制物流字段 |
| 项目设备归属 | `pms_project_device_assignment` | 保存项目、SN、实施范围、项目公司—部门组合、订单行识别值、安装地址、归属类型和有效期；物流、合同、快递和发货时间仍从权威事件查询 |
| 主附加SN | `pms_device_relation` | 合同维度关系为权威；`pms_device_sn.secondary_sn/secondary_item`仅是最新发货合同下的可重建缓存 |
| 交付件 | `pms_deliverable_template`、`pms_project_deliverable`、`pms_business_document`、`pms_document_version` | 交付件类型按行配置，不使用“工勘表、验收报告”等固定文件列；文件版本独立保存 |
| 设备配置与拓扑 | `pms_device_configuration`及功能、服务明细，`pms_network_topology`、`pms_topology_device_rel` | 多值启用功能和运行业务拆成子表；拓扑文件和设备节点关系分开 |
| 版本 | `pms_device_version`、`pms_product_release` | 出厂/在网和软件/CPLD/conboot按阶段与组件拆行，避免固定版本列不断扩展 |
| 技术公告 | `pms_technical_advisory`、`pms_technical_advisory_product`、`pms_technical_advisory_read`、`pms_device_advisory_match` | 公告内容、适用产品版本、用户阅读确认、设备命中与处置结果分层 |
| 故障 | `pms_service_incident`、`pms_incident_device_rel` | 故障主档与设备N:N；故障报告引用通用文档，不把多个SN塞入文本列 |
| 迁移与同步 | `pms_sync_batch`、`pms_migration_source_record`、`pms_external_key_map`、`pms_migration_issue` | 批次、原值、目标映射和问题闭环分开；这些表不参与业务权威统计 |
| 查询读模型 | `pms_project_delivery_summary` | 直接承载项目列表和统计导出的项目、客户、公司—部门、负责人显示值及汇总指标；可重建，不作为数量或状态权威来源 |

## 4. 已删除或禁止进入业务主表的冗余

| 原不合理设计 | 正式处置 |
|---|---|
| 项目表把最终用户、代理商和服务商名称混入直接客户 | 项目直接客户固化`customer_id/code/name`；其他参与方按`pms_project_party.party_role`保存 |
| 用一个`org_code`混装公司、办事处、市场部、系统部和拓展部 | 改为明确的`company_*`、`department_*`；同一业务角色的公司—部门组合进入`pms_project_company_department_rel`同一行 |
| 项目表保存旧流程状态、回退说明、来源创建人和整行JSON | 状态进入项目状态/流程历史；旧值及审计字段仅在迁移原值层保存 |
| 把ERP订单来源项目名称当作正式项目归属 | 只保留`source_project_name`用于来源显示和对账；正式归属由项目订单行实施范围决定 |
| 项目订单行范围复制订单数量、未执行数量、发货数量和合同关系 | 只固化高频识别与显示字段；数量事实仍只保存`allocated_qty`，其余数量从订单行读取 |
| 项目设备归属重复收件人、快递、发货时间和合同 | 只固化项目、公司—部门、SN、物料及订单行识别字段；物流和合同事实从事件及关系表读取 |
| 设备关系重复源/目标物料和合同号 | 通过两个设备ID和合同ID读取；来源原值保留在迁移层 |
| 发货事件重复设备主档的物料、条码和附加SN | 发货事件只保存事件事实；设备属性和合同关系从权威表读取 |
| 一张关系表同时可空关联订单头和订单行 | 拆成订单级、订单行级两张关系表 |
| 多个交付件、功能、服务、版本使用固定列或JSON数组 | 按可重复业务对象拆成明细行，支持增加类型而不改表 |
| 每张业务表重复`source_payload` | 原始行只在`pms_migration_source_record`保存；业务表仅保留确有同步幂等用途的来源键和同步时间 |

## 5. 有依据的冗余缓存

以下字段保留是为查询性能，不属于第二份可独立修改的业务事实：

- `pms_project.root_id/tree_path/tree_depth`：项目子树查询缓存，父子关系是权威；移动节点时事务性重算；
- `pms_device_sn.secondary_sn/secondary_item`：最新发货合同下附加SN缓存，合同特定和历史查询必须读取`pms_device_relation`；
- `pms_business_document.current_version_id`：当前文档版本读取缓存，可由版本表重建；
- `pms_project_delivery_summary`：面向列表和统计的可重建读模型，批次失败时不得发布部分结果。
- `pms_project`、`pms_sales_order_line`、`pms_project_order_line_scope`、`pms_project_device_assignment`中的`*_code/*_name`：各自业务发生或迁移基线时固化的引用值；主档变化不自动回写，只有明确的回归刷新任务才能更新；需要当前主档值时显式按`*_id`查询；
- `pms_project_delivery_summary`中的项目、客户、公司、部门和负责人字段：从`pms_project`的业务发生时值复制，不得在重建时绕过项目记录读取当前主档名称。

缓存必须有重建任务、差异对账和修复入口，禁止人工直接修改缓存来修业务事实。

## 6. 查询路径与性能

项目列表和项目实施订单查询不需要联接全部层级：

1. 项目列表和常规统计优先直接读取`pms_project_delivery_summary`，无需再次关联客户、公司、部门和用户主档；
2. 当前项目节点的实施订单直接从`pms_project_order_line_scope`按项目、公司—部门、客户、订单号或物料编码筛选；只有读取可变数量事实或当前主档值时才连接订单行、订单头或主档；
3. 项目树统计先用`root_id/tree_path`确定子树，再按项目ID集合访问实施范围；
4. 项目设备列表和导出从`pms_project_device_assignment`直接取得项目、公司—部门、SN、物料和订单行识别值；物流历史另查`pms_device_shipment_event`；
5. 合同、订单、执行单关系均有正向和反向组合索引，不从名称或逗号分隔字段关联；
6. 大列表默认读取汇总读模型或已固化高频引用字段的事实表，明细钻取和当前主档查询才连接订单行、发货事件、设备关系或基础主档。

因此，层级拆分本身不会造成查询必然变慢；真正影响性能的是缺少确定粒度、用文本关联和在列表中重复排序大事件表。正式模型已避免这些路径。

## 7. 完整迁移与同步规则

1. 一次性迁移以不可变抽取批次为边界，先写`pms_migration_source_record`，再写业务表；
2. 业务数据转换失败不删除原值，写入`pms_migration_issue`并阻止批次完成；
3. `pms_external_key_map`允许一源多目标和多源归并目标，拆分子行必须使用稳定子键；
4. 关联同步只读取旧库，不跨库查询；按来源稳定键在新库幂等更新当前镜像和关系；
5. `SOURCE_ONLY`、`PLATFORM_REPLACED`、`LINEAGE`和`PAYLOAD`不计入结构化业务覆盖率，但必须计入原值完整性对账；
6. 数量拆分必须验证同一订单行有效分配量不超过订单数量；并发写入使用订单行粒度锁和版本检查；
7. 每个批次满足“来源读取数 = 已完成 + 待处理 + 经批准排除”，三类互斥；
8. 正式迁移前重新读取旧库结构和数据画像，检测新增列、改型、超长值和新增枚举。
9. 一次性迁移优先使用来源记录自带的发生时编码和名称；来源缺少名称时，使用迁移基线时解析到的主档值并在转换规则标明`MIGRATION_BASELINE_RESOLVED`，不得宣称能够还原不存在的历史名称。
10. 常规主档同步不得级联更新业务表引用值；明确执行回归刷新时必须限定字段、范围和批次，保留操作审计并完成刷新前后对账。

## 8. 与基础平台实现的关系

`implement-cp-foundation`中已执行的迁移文件不得直接修改。现有客户、联系人、项目成员和业务文档表与本设计存在字段或唯一键差异时，必须新增前向数据库迁移：

- 客户补行业、地址、服务等级；
- 联系人补联系地址、客户单位内部部门、职位；
- 平台增加公司主档和用户—公司—部门业务上下文；部门主数据保持共享，不建立可反推公司的全局从属关系；
- 项目成员把“项目+用户唯一”调整为“项目+用户+角色+生效时间唯一”，并固化成员加入时的公司—部门组合；
- 文档与交付件实例分开；
- 所有业务外键逐步升级为租户复合外键。

## 9. 尚需业务确认但不阻塞物理模型的规则

- 金额字段的统一币种、含税口径和舍入规则；
- 物料数量是否存在小数单位及最终小数位；
- 软删除后客户编码、项目编码、文档编码等业务键是否允许复用；
- 同一范围唯一主联系人、主公司—部门组合和主执行单的并发写入实现方式；
- 技术公告适用版本表达式的正式语法和匹配引擎；
- 旧状态值、公司—部门关系角色、人员角色和RMA行为编码的版本化字典。

这些事项不得通过新增重复名称列或自由JSON规避，应在字典、约束和迁移规则中闭环。
