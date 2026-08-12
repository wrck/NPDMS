# SDS Phase 1 领域模型评审（命名统一后）

> 评审对象：当前 `SDS Phase 1：领域模型`
> 评审状态：**CONDITIONAL PASS / NOT READY FOR PHASE 2**
> 结论：`Implementation Execution` 命名整改通过，但领域 Owner、Context 完整性、Context Map、跨域契约仍需继续修订。

---

# 1. 本轮已确认通过

## 1.1 `Field Execution` 命名整改

原：

```text
Field Execution
```

现：

```text
Implementation Execution
实施执行域
```

结论：**PASS**

该命名更符合 PRD 的“实施部署 / 实施执行”业务语言，也避免 `Field` 在研发语境中被误解为“字段”。

---

## 1.2 `FieldQualityCheck` / `FieldSafetyCheck` 命名整改

现：

```text
ImplementationQualityCheck
ImplementationSafetyCheck
```

结论：**PASS**

两个名称均清晰表达“实施阶段的质量检查 / 安全检查”，不存在 `field` 歧义。

---

## 1.3 Implementation Execution 作为 Bounded Context

当前已明确：

```text
Implementation Execution
不是单一聚合根
```

而是包含：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
JointDebuggingResult
ImplementationRisk
ImplementationQualityCheck
ImplementationSafetyCheck
DeliveryEvidence
```

结论：**方向正确**

这与独立评审此前确认的“现场实施 Context 可拆分成多个独立聚合”的判断一致。

---

# 2. 仍需整改的 Blocker

## BLOCKER-01：`Implementation Execution` Owner 中的“采集结果”仍然过宽

当前：

```text
Implementation Execution Owner:
到货、安装、采集结果、质量检查、安全检查、实施阶段交付件/证据上传
```

建议改成：

```text
Implementation Execution Owner:
到货事实、安装事实、配置采集业务解释结果、业务联调结果、
实施风险、实施质量检查、实施安全检查、实施证据语义
```

原因：

```text
CollectionTask
外部执行状态
原始结果引用
回调证据
```

不应归 Implementation Execution。

这些属于：

```text
Device Access & Collection
设备连接与采集域
```

Implementation Execution 只负责：

> “采集结果在实施业务中意味着什么”。

---

## BLOCKER-02：仍缺少 `Device Access & Collection`

当前聚合列表已有：

```text
DeviceCredential
CollectionTask
```

但 Bounded Context 表仍然没有它们的唯一 Owner。

必须新增：

```text
Device Access & Collection
设备连接与采集域
```

建议：

| Context | Owner数据 | 允许引用 | 禁止直接修改 |
|---|---|---|---|
| Device Access & Collection | 设备凭证、凭证授权、采集任务、设备级执行记录、外部状态原值、结果引用、回调证据、执行授权 | 项目、设备、业务对象、已发布命令模板 | IMP/CUT/INS业务结论、设备主档、外部采集执行引擎 |

核心聚合：

```text
DeviceCredential
CollectionTask
```

---

## BLOCKER-03：仍缺少 `Work Order & Time`

当前 PRD 中工单、打卡、工时、责任区间等已形成独立业务能力，但 Bounded Context 表仍未出现对应 Owner。

建议新增：

```text
Work Order & Time
工单与工时域
```

Owner：

```text
WorkOrder
TimeClaim
ResponsibilityInterval
WorkOrderEvidence
WorkOrderStateHistory
```

禁止直接修改：

```text
Project 主状态
Device 主档
钉钉原始打卡事实
Financial Ledger
```

---

## BLOCKER-04：Customer & Asset Owner 仍然越界

当前：

```text
Owner:
客户、联系人、设备身份、设备档案、资产关系
```

仍然过于宽泛。

建议改为：

```text
Owner:
平台客户扩展字段
平台临时客户
联系人
客户/项目/设备关系
设备当前项目归属
设备当前客户归属
维保基本信息
配置Log关联
资产聚合视图
```

外部 Owner：

```text
CRM:
客户核心权威字段

MES:
设备出厂/生产字段

ITR:
在网版本、技术公告
```

因此：

```text
Device
```

可以作为平台聚合对象存在，但必须做字段级 Owner，而不是宣称平台拥有全部设备主档事实。

---

## BLOCKER-05：`Contract & Fulfillment` 仍未整改

当前：

```text
Owner:
合同、订单行、交付范围、对账
```

仍然存在 ERP Owner 越界。

推荐改名：

```text
Commercial Reference & Fulfillment
商业引用与履约分配域
```

Owner：

```text
ContractReference
SalesOrderReference
OrderLineAllocation
DeliveryScopeAllocation
FulfillmentSnapshot
ReconciliationRecord
```

不 Owner：

```text
ERP Contract
ERP SalesOrder
Financial Ledger
```

如果该领域无法形成独立生命周期，也可以不单独建 Context，将 Allocation 放入 Project Delivery，将 ERP 数据保留为 Reference / Snapshot。

---

## BLOCKER-06：`Platform Governance` 仍然过宽

当前：

```text
待办、文件、授权、变更、字典、审计
```

这些不应全部作为一个业务 Context。

建议改为平台能力集合：

```text
Platform Capabilities
├── Identity & Access
├── Workflow & Tasking
├── Platform Configuration
├── Audit & Compliance
└── File Service
```

其中：

```text
CHG-01 项目变更
```

如果是业务变更 Case，应由 Project Delivery 拥有业务对象；

Workflow 只负责流程执行。

---

## BLOCKER-07：`Inspection & Service` 中 `Service` 仍未定义

当前：

```text
Inspection & Service
Owner:
巡检任务、规则、报告、服务问题、服务状态
```

问题：

```text
Service
```

范围太模糊。

如果当前只承载 INS-01~09：

建议直接改成：

```text
Inspection
巡检域
```

如果还承载：

```text
持续服务跟踪
通用服务工单
服务问题
```

则必须证明它们与 Inspection 具有相同：

```text
业务语言
生命周期
责任角色
一致性边界
```

否则应拆出：

```text
Service Operations
```

---

# 3. Implementation Execution 内部聚合评审

## 3.1 ArrivalAcceptance

结论：**Candidate Aggregate Root，合理**

建议内部：

```text
ArrivalAcceptance
├── ArrivalBatch
├── ArrivalItem
├── ArrivalDifference
└── EvidenceReference
```

当前不变量还需在状态机文件中补：

```text
部分签收
补签
拒收
差异关闭
授权豁免
```

---

## 3.2 InstallationRecord

结论：**Candidate Aggregate Root，合理**

需要明确：

```text
InstallationRecord
```

究竟是：

```text
STATEFUL_AGGREGATE
```

还是：

```text
VERSIONED_FACT_RECORD
```

如果只是“每次安装形成一条不可覆盖记录”，不一定需要复杂状态机。

---

## 3.3 ConfigurationCollectionResult

结论：**合理，但 Owner 文案必须精确**

它负责：

```text
配置Log业务解析
设备关联
结果解释
归档引用
```

不负责：

```text
CollectionTask
设备连接
命令执行
原始采集执行
凭证
```

推荐名称可以继续保留：

```text
ConfigurationCollectionResult
```

---

## 3.4 JointDebuggingResult

结论：**合理**

与 ConfigurationCollectionResult 分开是合理的，因为：

```text
EXE-03:
配置Log采集/解析

EXE-04:
业务联调配置收集/联调结论
```

业务目的、输出和门禁语义不同。

---

## 3.5 ImplementationRisk

结论：**合理**

EXE-05 风险应作为平台业务事实，而不是只有 CRM 推送记录。

建议至少包含：

```text
RiskType
RiskLevel
DeviceReference
Description
Status
Disposition
Evidence
ExternalFeedbackReference
```

---

## 3.6 ImplementationQualityCheck

结论：**合理，但版本边界必须标记**

必须：

```text
Priority: P1
IntroducedIn: V2
RequiredForV1Gate: false
```

---

## 3.7 ImplementationSafetyCheck

结论：**合理，但需完成独立聚合边界决策**

当前拆为独立聚合的理由是：

```text
独立阻断
独立风险
独立复核
```

这个方向成立。

但 Phase 1 仍需要回答：

```text
质量检查和安全检查是否真的拥有不同生命周期？
是否拥有不同 Owner Role？
是否拥有不同状态机？
是否有不同豁免策略？
```

如果答案为 YES，则保持两个聚合。

如果规则高度一致，也可设计为：

```text
ImplementationComplianceCheck

CheckType:
QUALITY
SAFETY
```

但不能为了减少表数量强行合并。

---

## 3.8 DeliveryEvidence

结论：**仍需 Boundary Decision**

当前把它作为 Aggregate Root：

```text
DeliveryEvidence
```

尚未充分证明。

如果它只是：

```text
fileRef
fileHash
sourceRecordId
sourceVersion
uploadStatus
```

且完全依附于：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
JointDebuggingResult
```

则更适合：

```text
EvidenceReference
```

作为源聚合内部 Entity / Value Object。

只有它拥有独立：

```text
版本
作废
替换
授权
审核
独立状态
```

时，才建议保留为 Aggregate Root。

---

# 4. 聚合总表仍不完整

当前第 3 节只列：

```text
Project
ProjectTask
Device
DeviceCredential
CollectionTask
CutoverTask
InspectionTask
Closure
```

而第 3.1 又列了 Implementation Execution 聚合。

这会造成：

```text
“主聚合表”和“实施聚合表”
```

两套口径。

建议改成：

```text
3. 聚合归属总表
3.1 Project Delivery
3.2 Preparation & Solution
3.3 Implementation Execution
3.4 Acceptance & Closure
3.5 Cutover
3.6 Inspection
3.7 Work Order & Time
3.8 Customer & Asset
3.9 Supplier & Subcontract
3.10 Device Access & Collection
```

每个 Aggregate 必须唯一对应一个 Context。

---

# 5. `Closure` 建议改名

当前：

```text
Closure
```

容易被理解为状态或动作。

如果它表示：

```text
闭环申请
门禁快照
材料审核
回访
整改
最终关闭
```

建议命名：

```text
ClosureCase
```

这样更明确是一个有生命周期的业务对象。

---

# 6. Context Map 当前仍不是完整 DDD Context Map

当前：

```text
Project Delivery ↔ Cutover ↔ CollectionTask
```

这里：

```text
CollectionTask
```

是 Aggregate，不是 Context。

当前：

```text
Project Delivery ↔ Implementation Execution → DeliveryEvidence → Acceptance & Closure
```

这里：

```text
DeliveryEvidence
```

也是 Aggregate / Entity，不是 Context。

因此 Context Map 节点必须全部改成 Context。

推荐：

```text
CRM / ERP / ITR / MES
        |
        v
Integration / ACL
        |
        +----------------------+
        |                      |
        v                      v
Customer & Asset         Project Delivery
                              |
              +---------------+----------------+
              |               |                |
              v               v                v
Preparation & Solution  Implementation Execution  Cutover
                              |                |
                              +-------+--------+
                                      |
                                      v
                         Device Access & Collection
                                      |
                                      v
                        External Collection Platform

Project Delivery
    |
    +----> Work Order & Time
    |
    +----> Supplier & Subcontract

Implementation Execution / Cutover / Preparation
                |
                v
        Acceptance & Closure

Inspection
    |
    +----> Device Access & Collection
    +----> Customer & Asset

All business contexts
    |
    +---- Domain Events / Projections ----> Analytics
```

---

# 7. Context Map 还必须补关系模式

不能只有箭头。

建议增加：

| Upstream | Downstream | Pattern | Contract |
|---|---|---|---|
| CRM | Integration | ACL | CustomerSync |
| ERP | Integration | ACL | OrderSync |
| Integration | Project Delivery | Published Language / Command | ProjectSourceReceived |
| Project Delivery | Preparation & Solution | Customer/Supplier | ProjectContextQuery |
| Preparation & Solution | Project Delivery | Domain Event | ConstructionPlanApproved |
| Implementation Execution | Device Access & Collection | Customer/Supplier | CreateCollectionTask |
| Device Access & Collection | Implementation Execution | Domain Event | CollectionResultAvailable |
| Cutover | Device Access & Collection | Customer/Supplier | CreateCollectionTask |
| Inspection | Device Access & Collection | Customer/Supplier | CreateCollectionTask |
| Implementation Execution | Acceptance & Closure | Published Event / Query | EvidenceAvailable |
| Acceptance & Closure | Project Delivery | Domain Event | ClosureCompleted |

---

# 8. 跨域契约当前仍过薄

当前只有 3 条。

建议至少扩展为以下规则。

## 8.1 Repository

```text
禁止跨 Context Repository。
禁止跨 Context 直接 UPDATE 对方表。
```

## 8.2 State Owner

```text
业务状态只能由 Owner Context 修改。
```

其他 Context 只能发布：

```text
Fact Event
```

例如：

```text
ImplementationExecutionCompleted
```

不能：

```text
project.status = S5
```

---

## 8.3 Transaction

跨 Context 默认：

```text
eventual consistency
```

使用：

```text
Outbox
Inbox
Idempotency
Compensation
Reconciliation
```

不得建立跨域大事务。

---

## 8.4 External Status

必须保存：

```text
externalOriginalStatus
mappingVersion
mappedStatus
receivedAt
```

外部状态不能直接驱动内部生命周期。

---

## 8.5 Authorization

跨域调用必须保留：

```text
actor
tenant
organizationScope
projectScope
resourceScope
authorizationSnapshot
```

不能因为是内部服务调用就获得管理员权限。

---

## 8.6 Files

业务域拥有：

```text
文件业务语义
```

File Service 拥有：

```text
storage
hash
version
secure download
```

业务域只保存：

```text
FileRef
FileHash
FileVersion
```

---

## 8.7 Event Trace

跨域事件至少：

```text
eventId
eventType
eventVersion
aggregateId
aggregateVersion
actor
traceId
sourceContext
occurredAt
```

---

# 9. 版本边界仍需写入模型

建议 Context / Aggregate 表增加：

```text
IntroducedIn
RequiredForV1
EnhancementIn
```

至少：

```text
ImplementationQualityCheck:
IntroducedIn = V2

ImplementationSafetyCheck:
IntroducedIn = V2

Inspection:
IntroducedIn = V2
```

不得让这些模型成为 V1 Gate。

---

# 10. 当前独立评审 Blocker 仍未关闭

本次命名整改不能改变现有阶段状态。

当前仍需关闭：

```text
领域 Owner 正式签署
实现工作包登记
INT-12 集成形态确认
Requirement -> Aggregate 精确映射
Implementation 聚合状态模型
IMP-01/02 Workflow
操作级 Authorization Matrix
```

因此当前：

```text
PHASE_1_GATE = NOT_READY_FOR_PHASE_2
```

---

# 11. 推荐最终 Bounded Context

建议最终收敛为：

```text
Project Delivery
Preparation & Solution
Implementation Execution
Acceptance & Closure
Cutover
Inspection
Work Order & Time
Customer & Asset
Commercial Reference & Fulfillment
Supplier & Subcontract
Device Access & Collection
Analytics
Integration / ACL
```

平台能力：

```text
Identity & Access
Workflow & Tasking
Platform Configuration
Audit & Compliance
File Service
```

不再把 `Platform Governance` 当作单一万能业务域。

---

# 12. 本轮 Gate 结论

## Naming Gate

```text
Field Execution -> Implementation Execution
FieldQualityCheck -> ImplementationQualityCheck
FieldSafetyCheck -> ImplementationSafetyCheck

NAMING_GATE = PASS
```

## Domain Model Gate

```text
DOMAIN_MODEL_GATE = CONDITIONAL_PASS
```

## Phase 1 Gate

```text
PHASE_1_GATE = NOT_READY_FOR_PHASE_2
```

---

# 13. 下一步 Codex 整改顺序

推荐：

```text
1. 补 Device Access & Collection
2. 补 Work Order & Time
3. 修 Customer & Asset Owner
4. 修 Contract & Fulfillment
5. 拆 Platform Governance
6. 明确 Inspection & Service
7. 修 Aggregate Owner 总表
8. 完成 DeliveryEvidence Boundary Decision
9. 重画 Context Map
10. 扩展 Cross-Context Contracts
11. 补 V1/V2 Scope Matrix
12. 修 Requirement -> Aggregate 映射
13. 补 Implementation 状态模型
14. 补质量/安全 Workflow
15. 补操作级权限矩阵
16. 关闭三项 Phase 1 硬门禁
17. 重新 Phase 1 Review
18. 独立复审
```

通过后才能：

```text
PASS_FOR_PHASE_2
```
