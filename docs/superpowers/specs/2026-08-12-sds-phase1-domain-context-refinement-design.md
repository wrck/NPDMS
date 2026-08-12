# SDS Phase 1 领域 Context 整改设计

> 日期：2026-08-12  
> 依据：PRD V1.6、当前 Phase 1 SDS、`sds-phase1-domain-model-review-after-field-renaming.md` 及本轮已确认结论  
> 状态：待用户复核后实施

## 1. 目标

在不改变 PRD V1.6 业务范围、不改变13个领域编码和115项正式需求唯一 Owner 的前提下，修正 Phase 1 SDS 中过宽、缺失或混合了平台能力与业务职责的 Bounded Context，形成可用于后续数据、API和集成设计的清晰领域边界。

本轮只整改 Phase 1 领域模型及其关联工作映射，不关闭领域 Owner 签署、实现工作包登记和 INT-12 集成形态确认三个硬门禁，不进入 Phase 2。

## 2. 总体原则

1. 领域编码、领域规格分册和 Bounded Context 不要求一一对应；一个领域可以包含多个职责清晰的 Context。
2. 外部系统是权威来源，不等于平台只能实时调用接口。平台可以同步并本地保存业务所需主数据副本，以保证查询效率、可用性和历史追溯。
3. 外部权威字段原则上只读，必须保存来源系统、来源主键、来源版本、同步时间和同步状态；平台自有扩展字段由所属 Context 管理。
4. 日常业务查询优先读取本地同步数据；外部系统不可用时允许使用最近一次成功同步版本，并展示数据截止时间和同步状态。
5. 跨 Context 禁止直接访问 Repository 或修改对方状态，使用应用服务、查询契约、领域事件和不可变快照协作。
6. 现有采集功能模块或子应用可以作为新 Context 的实现载体，不重复建设设备连接、命令执行和原始采集引擎。

## 3. 已确认 Context 结构

### 3.1 核心业务 Context

| Bounded Context | 中文名称 | 主要领域编码 | 核心职责 |
|---|---|---|---|
| Project Delivery | 项目交付域 | PROJ | 项目、项目树、任务、模板、项目状态和业务变更 |
| Preparation & Solution | 交付准备与方案域 | SOL | 工前、计划、方案和准备数据 |
| Implementation Execution | 实施执行域 | IMP | 到货、安装、实施结果解释、风险、质量安全检查和实施证据 |
| Acceptance & Closure | 验收与项目闭环域 | ACC | 验收、齐套审核、归档、回访、服务交接和项目闭环 |
| Cutover | 变更切换域 | CUT | 割接评估、方案、审批、执行和稳定治理 |
| Work Order & Time | 工单与工时域 | SRV | WO-01～WO-06的工单、责任区间、打卡和工时 |
| Inspection | 巡检域 | SRV | INS-01～INS-09的巡检任务、规则、执行、报告和问题闭环 |
| Service Operations | 服务运营域 | SRV | SRV-01及持续服务交接后的服务状态和服务跟踪 |
| Customer & Relationship | 客户与关系域 | CUS | 客户同步副本、临时客户、联系人和客户关系 |
| Asset Management | 资产管理域 | AST | 设备同步副本、归属、位置、维保基本信息和配置Log关联 |
| Contract & Fulfillment | 合同订单履约域 | COM | 合同订单同步副本、订单行范围分配、履约事实和对账 |
| Supplier & Subcontract | 资源与外包域 | RES | 服务商、转包、付款门禁和资质 |
| Device Access & Collection | 设备连接与采集域 | PLT | 凭证、授权、采集任务、外部状态原值、结果引用和回调证据 |
| Analytics | 经营分析域 | ANA | 只读指标、组合视图和分析快照 |
| Integration / ACL | 集成与防腐层 | 多领域协作 | 外部协议适配、映射、同步证据、重试和对账 |

KNO仍保留技术知识治理需求 Owner；其技术公告等事实通过业务能力或集成契约服务 AST、CUT 等消费方，不因本轮 Context 整改改变需求归属。

### 3.2 基础平台能力集合

`Platform Governance` 不再作为万能业务 Context，改为以下基础平台能力集合：

| 平台能力 | 职责 |
|---|---|
| Identity & Access | 身份、组织、租户、角色和数据权限 |
| Workflow & Tasking | 审批编排、待办和状态同步 |
| Platform Configuration | 字典、参数、模板和状态定义 |
| Audit & Compliance | 审计、操作留痕和安全事件 |
| File Service | 存储、哈希、版本和安全下载 |

业务状态和文件业务含义仍由业务 Context 拥有；基础平台只提供通用能力。

## 4. 关键数据 Owner 决策

### 4.1 Device Access & Collection

该 Context 正式成立，现有采集模块或子应用可以按此 Context 纳入。

它拥有：

- `DeviceCredential`
- `CredentialGrant`
- `CollectionTask`
- 设备级执行记录
- 外部状态原值及映射版本
- 原始结果引用
- 回调证据
- 任务级执行授权快照

它不拥有：

- IMP、CUT、INS的业务结论
- 设备主档
- 外部采集引擎内部数据
- 其他 Context 的状态

外部采集平台仍负责设备连接、命令执行和原始采集。该 Context 负责平台侧凭证、授权、任务下发、回调接收和业务消费者之间的防腐边界。

### 4.2 Contract & Fulfillment

保留 Context 名称，不降级为只保存ID的商业引用域。

平台本地同步并保存：

- 合同主数据副本
- 销售订单及订单行副本
- 来源系统ID、版本、同步时间和状态
- 订单行实施范围分配
- 履约事实、履约快照和对账记录

ERP仍是合同和订单权威来源。ERP权威字段在平台原则上只读；项目关联、设备范围、实施分配、履约状态和对账处理属于平台自有事实。

### 4.3 Customer & Relationship

平台本地同步CRM客户主数据副本，并拥有临时客户、联系人、项目联系人和平台扩展字段。CRM权威字段原则上只读；同步失败时读取最近成功版本并显示截止时间和同步状态。

### 4.4 Asset Management

平台本地同步MES、ITR、备件等必要设备主数据，并拥有设备当前客户归属、最具体项目归属、安装位置、维保基本信息和配置Log关联。外部来源字段保持来源级 Owner 和只读边界。

## 5. 实施执行与证据边界

`Implementation Execution` 保留以下独立聚合：

- `ArrivalAcceptance`
- `InstallationRecord`
- `ConfigurationCollectionResult`
- `JointDebuggingResult`
- `ImplementationRisk`
- `ImplementationQualityCheck`（V2）
- `ImplementationSafetyCheck`（V2）
- `DeliveryEvidence`

`ConfigurationCollectionResult` 和 `JointDebuggingResult` 只拥有采集结果的业务解释、确认结论和引用，不拥有 `CollectionTask`、设备连接、命令执行、凭证或原始采集执行。

`DeliveryEvidence` 保留为独立聚合根，拥有上传、版本、替换、作废、来源、哈希、授权和跨域协作状态。其他业务聚合仅保存 `EvidenceReference`。继续执行“IMP上传、ACC审核归档”的职责分工。

## 6. 项目闭环聚合

原 `Closure` 统一命名为：

```text
ProjectClosure
项目闭环
```

它属于 `Acceptance & Closure`，负责闭环申请、门禁快照、材料审核、回访、整改和最终闭环。`Project` 主状态仍由 `Project Delivery` 拥有；`ProjectClosure` 完成后发布领域事件，请求项目进入关闭状态，不跨域直接写项目状态。

## 7. Context Map 与跨域契约

Context Map只允许使用 Context 或外部系统作为节点，不再把 `CollectionTask`、`DeliveryEvidence` 等聚合画成 Context。

至少维护以下关系：

| 上游 | 下游 | 模式 | 契约示例 |
|---|---|---|---|
| CRM/ERP/MES/ITR | Integration / ACL | ACL | 主数据同步契约 |
| Project Delivery | Preparation & Solution | Customer/Supplier | 项目上下文查询 |
| Implementation Execution | Device Access & Collection | Customer/Supplier | `CollectionTaskRequested` |
| Device Access & Collection | Implementation Execution | Published Event | `CollectionResultAvailable` |
| Cutover | Device Access & Collection | Customer/Supplier | `CollectionTaskRequested` |
| Inspection | Device Access & Collection | Customer/Supplier | `CollectionTaskRequested` |
| Implementation Execution | Acceptance & Closure | Published Language | `ImplementationEvidencePublished` |
| Acceptance & Closure | Project Delivery | Domain Event | `ProjectClosureCompleted` |
| Work Order & Time | Project Delivery | Published Event / Query | 工单与工时快照 |
| 各业务Context | Analytics | Event / Projection | 只读分析投影 |

跨域契约统一包含身份和追溯信息：`eventId`、`eventType`、`eventVersion`、`aggregateId`、`aggregateVersion`、`actor`、`tenant`、`authorizationSnapshot`、`traceId`、`sourceContext`、`occurredAt`。跨域默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账；不建立跨 Context 大事务。

## 8. 版本与追溯约束

1. `ImplementationQualityCheck`、`ImplementationSafetyCheck`、`Inspection`、大部分工单与服务运营能力按PRD保持V2，不得成为V1门禁。
2. `Device Access & Collection` 的INT-12、凭证和任务编排相关V1能力进入V1；后续智能采集能力不提前进入V1/V2。
3. Context拆分不改变需求编号、领域编码、正式版本和优先级。
4. 追溯矩阵继续以13领域Owner为唯一需求Owner，同时增加或修正“业务模块/Context/聚合”工作映射。

## 9. 实施范围

整改以下活动文件：

- `docs/design/02-domain-model.md`
- `docs/design/02a-context-map.md`
- `docs/design/02b-aggregate-boundary-decisions.md`
- `docs/design/02c-data-ownership-matrix.md`
- `docs/design/02d-cross-context-contracts.md`
- `docs/design/02e-version-scope-matrix.md`
- `docs/design/03-system-architecture.md`
- `docs/design/04-module-design.md`
- `docs/design/05-state-machine.md`
- `docs/design/06-workflow-design.md`
- `docs/design/07-authorization-design.md`
- `docs/design/01-requirement-traceability.md`
- `docs/design/phase-1-domain-ownership.md`
- `docs/design/phase-1-review.md`
- `docs/traceability/requirement-matrix.md`
- `scripts/generate_requirement_traceability.py`

原评审文件作为输入证据保留，不机械执行其中已过时或未经确认的建议。不得改写历史独立评审原文。

## 10. 验收标准

- Context Map不存在聚合作为Context节点的情况。
- `Device Access & Collection`具有唯一Owner、数据边界、实现载体说明和上下游契约。
- SRV领域内部三个Context映射明确，需求Owner仍为SRV。
- CUS和AST分别映射到独立Context，外部主数据同步副本与平台自有字段边界明确。
- COM保留本地同步主数据，ERP权威Owner与平台履约事实不混淆。
- `Platform Governance`不再作为万能业务Context。
- `ProjectClosure`命名、生命周期和Project状态Owner一致。
- `DeliveryEvidence`独立聚合边界及IMP/ACC职责保持一致。
- 115项正式需求、13个领域Owner、V1/V2数量和优先级不发生变化。
- 命名、PRD语义、领域生成、追溯矩阵和Markdown差异校验全部通过。
- Phase 1仍保持`NOT_READY_FOR_PHASE_2`，三个硬门禁不得被文档整改自动关闭。
