# 项目交付平台业务领域表结构正式设计

## 1. 结论

正式模型采用“**业务事实规范化 + 来源数据完整留痕 + 高频查询可重建缓存**”三层结构：

1. 业务表只保存稳定、可解释、可约束的业务事实，不为迁移方便复制旧表宽字段；
2. 每条旧记录完整写入`pms_migration_source_record.source_payload`，旧字段即使不进入业务列也不丢失；
3. `pms_external_key_map`保存来源记录和一个或多个目标记录之间的幂等映射；
4. 项目树路径、当前附加SN和项目交付汇总允许作为可重建读取缓存，其权威来源必须明确；
5. CRM执行单和配置只作辅助关联证据，实施权威范围仍是ERP订单行和项目订单行实施范围；
6. 所有业务外键使用`(tenant_id, id)`复合引用，数据库阻止跨租户误关联；全局ID由基础平台分布式ID生成器赋值。

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
| 客户与联系人 | `pms_customer`、`pms_customer_contact` | 一客户一主档；联系人按客户多行保存，不在项目和订单重复客户名称、地址、电话 |
| 项目树 | `pms_project`、`pms_project_relation` | 正式子项目使用`parent_id`；组合、扩容、续采等非树关系不得混入父子树 |
| 项目组织与人员 | `pms_project_org_rel`、`pms_project_member`、`pms_project_party` | 公司组织、系统用户、外部业务参与方分开建模；角色和有效期按行保存 |
| 项目组合 | `pms_portfolio`、`pms_portfolio_project_rel` | 组合成员不改变项目父子关系，一个项目可属于多个组合 |
| 合同与回款 | `pms_contract`、`pms_contract_receivable` | 合同以“所属公司+合同号”唯一；回款是合同下的同步事实，不反向覆盖合同主档 |
| 项目、合同、订单 | `pms_project_contract_rel`、`pms_order_contract_rel` | 项目1:N合同、合同N:N订单均以关系表表达，不在项目或订单头固化单合同外键 |
| ERP订单 | `pms_sales_order`、`pms_sales_order_line` | 订单头与订单行1:N；订单行是产品、数量、发货和实施跟踪的基础粒度 |
| 项目实施范围 | `pms_project_order_line_scope` | 一行表示“项目节点分配到ERP订单行的实施数量”；一个订单行可拆给多个正式子项目 |
| CRM执行单辅助 | `pms_crm_execution_order`、`pms_crm_execution_config` | 保存当前可获得的CRM镜像；不要求全量配置，不作为实施范围权威表 |
| 订单与执行单 | `pms_order_execution_rel`、`pms_order_line_execution_rel` | 订单级和订单行级分别建表，避免双可空外键和粒度矛盾 |
| 合并与改单 | `pms_execution_merge_batch`、`pms_execution_merge_member`、`pms_order_change_rel` | 合并成员不限数量；取消、新单、退货、替代等保留订单血缘 |
| 产品 | `pms_product` | 产品编码、型号、产品线和安服产品标志集中维护；安服项目由配置行判定 |
| 发货与SN | `pms_shipment_contract_ref`、`pms_shipment_package`、`pms_device_sn`、`pms_device_shipment_event` | 合同归属、装箱单、设备身份、每次发放/退回/RMA事件分别保存，不互相复制物流字段 |
| 项目设备归属 | `pms_project_device_assignment` | 只保存项目、SN、实施范围、安装地址、归属类型和有效期；发货、快递、物料从权威表连接查询 |
| 主附加SN | `pms_device_relation` | 合同维度关系为权威；`pms_device_sn.secondary_sn/secondary_item`仅是最新发货合同下的可重建缓存 |
| 交付件 | `pms_deliverable_template`、`pms_project_deliverable`、`pms_business_document`、`pms_document_version` | 交付件类型按行配置，不使用“工勘表、验收报告”等固定文件列；文件版本独立保存 |
| 设备配置与拓扑 | `pms_device_configuration`及功能、服务明细，`pms_network_topology`、`pms_topology_device_rel` | 多值启用功能和运行业务拆成子表；拓扑文件和设备节点关系分开 |
| 版本 | `pms_device_version`、`pms_product_release` | 出厂/在网和软件/CPLD/conboot按阶段与组件拆行，避免固定版本列不断扩展 |
| 技术公告 | `pms_technical_advisory`、`pms_technical_advisory_product`、`pms_technical_advisory_read`、`pms_device_advisory_match` | 公告内容、适用产品版本、用户阅读确认、设备命中与处置结果分层 |
| 故障 | `pms_service_incident`、`pms_incident_device_rel` | 故障主档与设备N:N；故障报告引用通用文档，不把多个SN塞入文本列 |
| 迁移与同步 | `pms_sync_batch`、`pms_migration_source_record`、`pms_external_key_map`、`pms_migration_issue` | 批次、原值、目标映射和问题闭环分开；这些表不参与业务权威统计 |
| 查询读模型 | `pms_project_delivery_summary` | 可从项目、实施范围、设备、发货和交付件重建；不作为数量或状态权威来源 |

## 4. 已删除或禁止进入业务主表的冗余

| 原不合理设计 | 正式处置 |
|---|---|
| 项目表重复客户编码、客户名称、最终用户名称 | 客户解析为`customer_id`；最终用户等按`pms_project_party.party_role`保存 |
| 项目表固定办事处、市场、体系和拓展组织字段 | 统一拆为`pms_project_org_rel`的组织角色行 |
| 项目表保存旧流程状态、回退说明、来源创建人和整行JSON | 状态进入项目状态/流程历史；旧值及审计字段仅在迁移原值层保存 |
| 订单头重复客户名称和项目名称 | 只保留稳定客户编码及可解析的`customer_id`；项目通过实施范围和关系查询 |
| 项目订单行范围复制订单号、物料、合同和四套数量 | 只保存`order_line_id`和`allocated_qty`；订单数量、发货数量从订单行读取 |
| 项目设备归属重复物料、收件人、快递、发货时间和合同 | 删除重复列；分别连接设备、发货事件、装箱单和合同归属 |
| 设备关系重复源/目标物料和合同号 | 通过两个设备ID和合同ID读取；来源原值保留在迁移层 |
| 发货事件重复设备主档的物料、条码和附加SN | 发货事件只保存事件事实；设备属性和合同关系从权威表读取 |
| 一张关系表同时可空关联订单头和订单行 | 拆成订单级、订单行级两张关系表 |
| 多个交付件、功能、服务、版本使用固定列或JSON数组 | 按可重复业务对象拆成明细行，支持增加类型而不改表 |
| 每张业务表重复`source_payload` | 原始行只在`pms_migration_source_record`保存；业务表仅保留确有同步幂等用途的来源键和同步时间 |

## 5. 有依据的冗余缓存

以下字段保留是为查询性能，不属于第二份业务事实：

- `pms_project.root_id/tree_path/tree_depth`：项目子树查询缓存，父子关系是权威；移动节点时事务性重算；
- `pms_device_sn.secondary_sn/secondary_item`：最新发货合同下附加SN缓存，合同特定和历史查询必须读取`pms_device_relation`；
- `pms_business_document.current_version_id`：当前文档版本读取缓存，可由版本表重建；
- `pms_project_delivery_summary`：面向列表和统计的可重建读模型，批次失败时不得发布部分结果。

缓存必须有重建任务、差异对账和修复入口，禁止人工直接修改缓存来修业务事实。

## 6. 查询路径与性能

项目列表和项目实施订单查询不需要联接全部层级：

1. 项目列表读取`pms_project`和`pms_project_delivery_summary`；
2. 当前项目节点的实施订单，从`pms_project_order_line_scope(project_id, scope_status, order_line_id)`连接订单行和订单头；
3. 项目树统计先用`root_id/tree_path`确定子树，再按项目ID集合访问实施范围；
4. SN追踪从`pms_project_device_assignment(project_id, effective_to, device_id)`连接设备，物流历史另查`pms_device_shipment_event`；
5. 合同、订单、执行单关系均有正向和反向组合索引，不从名称或逗号分隔字段关联；
6. 大列表默认读取汇总读模型，明细钻取才连接订单行、发货事件和设备关系。

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

## 8. 与基础平台实现的关系

`implement-cp-foundation`中已执行的迁移文件不得直接修改。现有客户、联系人、项目成员和业务文档表与本设计存在字段或唯一键差异时，必须新增前向数据库迁移：

- 客户补行业、地址、服务等级；
- 联系人补联系地址、部门、职位；
- 项目成员把“项目+用户唯一”调整为“项目+用户+角色+生效时间唯一”；
- 文档与交付件实例分开；
- 所有业务外键逐步升级为租户复合外键。

## 9. 尚需业务确认但不阻塞物理模型的规则

- 金额字段的统一币种、含税口径和舍入规则；
- 物料数量是否存在小数单位及最终小数位；
- 软删除后客户编码、项目编码、文档编码等业务键是否允许复用；
- 同一范围唯一主联系人、主组织和主执行单的并发写入实现方式；
- 技术公告适用版本表达式的正式语法和匹配引擎；
- 旧状态值、组织角色、人员角色和RMA行为编码的版本化字典。

这些事项不得通过新增重复名称列或自由JSON规避，应在字典、约束和迁移规则中闭环。
