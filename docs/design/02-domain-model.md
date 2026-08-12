# SDS Phase 1：领域模型

## 1. 建模原则

领域边界从 PRD 的业务责任、数据 Owner、生命周期和权限范围推导；旧 `specs` 仅作为交叉检查材料。项目、割接、巡检属于同一平台，但不是同一个聚合，不能通过跨域 Repository 直接修改。一个需求领域可以包含多个 bounded context；领域编码仍是需求 Owner 和规格分册边界。`Implementation Execution`（实施执行域）是现场实施 bounded context，不是单一聚合根；其内部聚合按现场事实、采集结果、质量检查、安全检查和交付证据分别维护。

## 2. Bounded Context

| Context | Owner数据 | 允许引用 | 禁止直接修改 |
|---|---|---|---|
| Project Delivery | 项目、模板、阶段、任务 | 客户、合同、设备、交付件 | 客户主数据、设备主档 |
| Preparation & Solution | 工勘、需求分析、计划、方案 | 项目、模板、人员 | 项目主状态 |
| Implementation Execution | 到货、安装、采集结果、质量检查、安全检查、实施阶段交付件/证据上传 | 项目、设备、采集任务、文件服务 | 设备主档核心身份、验收归档状态 |
| Acceptance & Closure | 培训、验收、交付件齐套校验、审核、统一归档、问卷、闭环交接 | 项目、实施证据、方案、问题、文件 | 外部合同/财务事实、现场实施原始证据 |
| Cutover | 割接任务、评估、方案、执行结果 | 项目、设备、采集任务、风险/调研字典 | 采集执行引擎、设备凭证明文 |
| Work Order & Time | WO-01～WO-06 工单、责任区间、打卡和工时 | 项目、设备、割接任务 | 项目主状态、设备主档 |
| Inspection | INS-01～INS-09 巡检任务、规则、报告和问题闭环 | 项目、设备、采集结果 | 设备凭证授权、外部UMC原始数据 |
| Service Operations | SRV-01 设备服务状态和持续服务跟踪 | 设备、客户、服务交接事实 | 设备主档核心身份、外部服务原始数据 |
| Customer & Relationship | 客户同步副本、临时客户、联系人和客户关系 | 合同、项目、设备 | CRM权威字段、项目交付状态 |
| Asset Management | 设备同步副本、设备档案、归属、维保基本信息和配置Log关联 | 项目、客户、采集结果 | MES/ITR权威字段、项目交付状态 |
| Contract & Fulfillment | 合同、订单行、交付范围、对账 | 项目、设备、财务回写 | 财务系统账务 |
| Supplier & Subcontract | 服务商、转包、付款门禁 | 项目、合同、问卷 | 财务付款结果 |
| Analytics | 指标快照、组合视图 | 各域只读事实 | 任何业务交易 |
| Platform Governance | 待办、文件、授权、变更、字典、审计 | 各域标识和事件 | 业务域聚合内部状态 |
| Device Access & Collection | 设备凭证、授权、采集任务、外部状态原值、结果引用和回调证据 | 项目、设备、业务对象、命令模板 | IMP/CUT/INS业务结论、设备主档、外部采集引擎 |
| Integration | 外部来源映射、回调、同步记录 | 各域命令/查询接口 | 外部系统数据库 |

## 3. 聚合与不变量

| 聚合根 | 关键不变量 |
|---|---|
| Project | 项目编码唯一；父子关系无环；项目层级不设固定深度；父项目只汇总直接子项目快照；闭环需满足全部后代闭环门禁 |
| ProjectTask | 任务父子关系无环；任务层级可配置；状态变化必须经过受控 transition；查询按项目树索引和权限范围过滤 |
| Device | 序列号/设备身份唯一；同一时点同一设备只能有一个当前项目归属；历史归属通过关系版本保留 |
| DeviceCredential | 默认仅创建人可用；授权绑定用户、设备、协议、命令模板和有效期；任何业务不得读取明文 |
| CollectionTask | 幂等键唯一；临时密码不落库；外部状态原值保留；回调重复不得重复消费 |
| CutoverTask | 等级确认后才能进入对应审批；执行前必须满足方案、采集和审批门禁；失败保留原任务证据 |
| InspectionTask | 在线/离线模式互斥；规则版本冻结到任务；报告生成和问题闭环可追溯 |
| ProjectClosure | 交付件、回访、材料审核等门禁全部满足后才能闭环；闭环后 V1/V2 只读，完成后通过事件请求 Project 关闭 |

## 3.1 Implementation Execution 内部聚合拆分

| 聚合根 | 负责事实 | 不负责 | 拆分理由 |
|---|---|---|---|
| ArrivalAcceptance | 到货数量、签收结果、签收人、签收证据 | 设备主档身份、验收最终结论 | 到货可独立补录/审核，和安装、配置不共用事务 |
| InstallationRecord | 安装位置、照片、安装结果、设备关联 | 到货签收状态、设备主档核心字段 | 安装可重复记录并保留历史，生命周期独立 |
| ConfigurationCollectionResult | 配置Log采集结果、解析状态、结果引用 | 设备连接执行、凭证明文、通用任务下发 | 采集执行由外部平台负责，结果消费与现场业务解释分离 |
| JointDebuggingResult | 业务联调配置收集结果、联调结论、结果引用 | 设备连接执行、割接执行、项目闭环审批 | 与配置Log采集目的、结果解释和门禁不同 |
| ImplementationRisk | 单机风险标记、风险等级、处置记录 | 割接风险项和项目闭环审批 | 风险事实与质量安全检查不是同一责任边界 |
| ImplementationQualityCheck | 阶段质量检查、整改项、复核结论 | 项目闭环审批、交付件最终归档 | 质量检查有独立责任人与整改状态 |
| ImplementationSafetyCheck | 现场工作安全检查、安全风险、阻断结论 | 项目闭环审批、交付件最终归档 | 安全检查有独立阻断和复核规则，不能与质量检查合并 |
| DeliveryEvidence | 实施阶段交付件、来源记录、版本、文件哈希、上传状态 | 验收归档审批 | IMP上传；ACC审核、齐套校验和归档 |

这些聚合通过项目ID、设备ID、任务ID或证据引用关联，不在一个跨聚合事务中强制同步；阶段门禁使用查询快照或领域事件校验完整性。

## 4. Context Map

`CRM/ERP → Integration → Project Delivery → Preparation/Solution → Implementation Execution → Acceptance/Closure`

`Project Delivery ↔ Implementation Execution → DeliveryEvidence → Acceptance & Closure`

`Project Delivery ↔ Cutover ↔ Device Access & Collection → External Collection Platform`

`Project/Device → Inspection → Device Access & Collection / UMC / Customer & Relationship`

`Work Order & Time → Project Delivery` 发布工单与工时快照；`Service Operations` 消费项目闭环和设备服务事实。

`Platform Governance` 作为基础平台能力集合，通过命令、查询、事件和统一权限服务横向支撑各 Context，不作为万能业务 Context，也不拥有业务域交易数据。

## 5. 跨域契约

- 只通过应用服务、查询接口或内部事件引用其他 Context。
- 外部系统来源字段保留来源系统、来源单号、版本和同步时间；平台不覆盖外部 Owner 数据。
- 设备凭证授权快照随采集任务保存，撤销不改写历史执行事实。
