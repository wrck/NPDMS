# SDS Phase 1 领域模型完整评审建议

> 适用项目：项目实施交付管理平台  
> 评审对象：SDS Phase 1 `02-domain-model.md` 当前版本  
> 评审结论：**CONDITIONAL PASS（有条件通过）**  
> 下一 Gate：完成本文“阻塞项”整改后，方可进入 SDS Phase 2 的 Data Model / Database / API / Event / Integration 设计。

---

## 1. 评审范围与依据

本次评审重点检查以下内容：

1. Bounded Context 是否与 PRD 的业务责任、数据 Owner、生命周期和权限边界一致；
2. Context 是否存在职责重叠、遗漏或“万能域”；
3. Aggregate Root 是否具有明确的一致性边界和唯一 Context Owner；
4. 外部权威数据是否被错误建模成本平台 Owner；
5. 实施、采集、割接、巡检之间是否正确分离；
6. 来源业务证据与验收归档是否重复建模；
7. V1/V2/V3/OUT_OF_SCOPE 是否在领域模型中保持边界；
8. Context Map 是否足够支撑后续 API、Event 和 Integration Design；
9. 跨域契约是否足以阻止后续 Codex 产生跨域 Repository、共享表和状态越权修改。

主要 PRD 追溯：

- PM-01~PM-11：项目、模板、项目树、任务、指派；
- PRE-01~05、PLN-01~04、SCH-01~05：工前、计划、方案；
- EXE-01~06：实施执行；
- ACC-01~04、CLO-01~06：验收、交付件索引、闭环；
- WO-01~06：工单与工时；
- SUB-01~05：转包与付款门禁；
- CUS-01~04、EQP-01~07：客户与设备资产；
- CUT-01~10：割接；
- INS-01~09：巡检；
- INT-01~12、NFR-01~03：集成、采集、安全与非功能；
- Q-12：阶段质量检查和现场安全检查为 P1/V2 增强；
- Q-22：订单可分配量及并发分配规则。

---

# 2. 总体结论

当前领域模型的主方向正确：

- 项目、割接、巡检没有被塞进一个大聚合；
- 已明确禁止跨 Context Repository 直接修改；
- `Field Execution` 已被定义为 Bounded Context，而非单一聚合根；
- DeviceCredential、CollectionTask、CutoverTask、InspectionTask 等关键生命周期对象已经被识别；
- 已意识到外部系统 Owner 与平台业务事实需要分离。

但当前版本仍有 **6 个进入 Phase 2 前必须修订的问题**：

## BLOCKER-01：`Field Execution` 命名需要统一替换

建议：

```text
Field Execution
    ↓
Implementation Execution
```

中文统一：

```text
实施执行域
```

原因：

- `Field` 在研发文档中很容易先被理解为“字段”；
- PRD 自身使用“实施部署”“实施执行闭环”等术语；
- 该 Context 实际覆盖的业务不限于“现场动作”，还包括配置结果解释、业务联调和实施证据；
- `Implementation Execution` 与上下游 `Preparation & Solution`、`Acceptance & Closure` 的语义衔接更自然。

代码模块建议简称：

```text
implementation
```

而不是：

```text
field-execution
implementation-execution
```

---

## BLOCKER-02：缺少 `Device Access & Collection` Context

当前 Aggregate Root 已包含：

- DeviceCredential
- CollectionTask

但 Bounded Context 表没有明确 Owner。

必须增加：

```text
Device Access & Collection
设备连接与采集域
```

这是 PRD INT-12 明确存在的平台业务能力，不应归入：

- Implementation Execution；
- Cutover；
- Inspection；
- Integration；
- Platform Governance。

该 Context 的职责是：

```text
设备认证
凭证授权
统一采集任务
设备级执行记录
任务下发
外部状态映射
回调验签
幂等处理
结果引用
调用审计
```

它不负责：

```text
实施是否完成
割接是否成功
巡检问题是否闭环
```

这些业务结论分别由 IMP/CUT/SRV 解释。

---

## BLOCKER-03：缺少 `Work Order & Time` Context

当前 PRD 有完整的 WO-01~06 工单体系，包括：

- 钉钉打卡生成工单；
- 工单补齐；
- 工单类型；
- 设备关联；
- 工时申报；
- 工时审批；
- 工单数据进入项目视图；
- 割接保障工单。

但当前 Bounded Context 表没有工单域。

建议增加：

```text
Work Order & Time
工单与工时域
```

Owner：

```text
WorkOrder
AttendanceEvidence
TimeClaim / Timesheet
WorkOrderAttachmentReference
ResponsibilityInterval
WorkOrderStateHistory
```

允许引用：

```text
Project
Device
Customer
Employee
CutoverTask
```

禁止直接修改：

```text
Project 主状态
Device 主档
Attendance 外部原始证据
Financial Ledger
```

建议核心聚合：

```text
WorkOrder
TimeClaim
```

`AttendanceRecord` 是否成为独立聚合，取决于钉钉原始打卡证据是否需要独立生命周期；否则可作为来源证据实体/快照。

---

## BLOCKER-04：Customer & Asset 的 Owner 表述过度

当前：

```text
Customer & Asset
Owner = 客户、联系人、设备身份、设备档案、资产关系
```

不能直接保留。

PRD 已明确字段级 Owner：

```text
CRM
    客户核心权威字段

MES
    设备生产/出厂字段

ITR
    在网版本、技术公告

Platform
    平台客户扩展字段
    临时客户
    客户/项目/设备关系
    设备项目/客户归属
    维保基本信息
    配置Log关联
    平台资产聚合视图
```

因此领域设计必须从“表级 Owner”升级为“字段/事实级 Owner”。

---

## BLOCKER-05：Contract & Fulfillment 不能拥有 ERP 合同/订单事实

当前：

```text
Contract & Fulfillment
Owner = 合同、订单行、交付范围、对账
```

存在明显 Owner 越界风险。

ERP 是：

```text
SalesOrder
ContractOrder
```

的权威来源。

推荐两种方案。

### 方案 A（推荐）

将 Context 改为：

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
ExternalBusinessMapping
```

不 Owner：

```text
ERP Contract
ERP SalesOrder
Financial Ledger
```

### 方案 B

如果实际业务规则不够形成独立领域，则不要单独建立该 Context。

将：

```text
OrderLineAllocation
DeliveryScopeAllocation
```

放到 `Project Delivery`，

而 ERP 合同/订单只通过 Integration ACL 形成只读 Reference/Snapshot。

Phase 1 Review 应要求 Codex 明确选择 A 或 B，不能保持目前含混状态。

---

## BLOCKER-06：`Platform Governance` 职责过宽

当前：

```text
待办、文件、授权、变更、字典、审计
```

被放在同一个 Context。

这些对象生命周期和责任明显不同：

```text
IAM / Authorization
Workflow / Todo
Dictionary / Configuration
File Service
Audit
Change Management
```

如果继续把它们作为一个业务 Context，Phase 2 很容易形成：

```text
platform_common
platform_service
platform_repository
```

最终所有业务域都依赖它。

推荐将 `Platform Governance` 重新定义为“支撑能力集合”，而不是一个大聚合边界。

至少逻辑拆为：

```text
Identity & Access
Workflow & Tasking
Platform Configuration
Audit & Compliance
File Service
```

其中 `CHG-01 项目变更` 如果具有明确业务流程和状态，应由 Project Delivery 拥有业务变更 Case，Workflow 只提供流程执行能力。

---

# 3. Bounded Context 逐项评审

## 3.1 Project Delivery

### 当前定义

Owner：

```text
项目、模板、阶段、任务
```

### 结论

**PASS WITH REFINEMENT**

范围基本正确。

建议 Owner 明确为：

```text
Project
ProjectTemplate
ProjectLifecycleInstance
ProjectTask
ProjectAssignment
ProjectHierarchy
ProjectProgressSnapshot
```

需要进一步明确：

1. `Project` 不应包含全部阶段业务数据；
2. PRE/SCH/EXE/ACC 数据通过 ID 和状态投影关联；
3. Project 主状态只能由 Project Delivery 自己迁移；
4. 其他 Context 只能发布“业务结果事件”，不能直接更新项目状态字段。

推荐事件：

```text
PreparationCompleted
ConstructionPlanApproved
ImplementationPlanApproved
ImplementationExecutionCompleted
CutoverCompleted
AcceptanceCompleted
ClosureCompleted
```

Project Delivery 消费事件后执行自己的状态机。

---

## 3.2 Preparation & Solution

### 当前定义

Owner：

```text
工勘、需求分析、计划、方案
```

### 结论

**PASS**

建议正式名称可保留：

```text
Preparation & Solution
```

如果希望术语更清晰，可改：

```text
Delivery Preparation & Solution
交付准备与方案域
```

候选聚合：

```text
SiteSurvey
RequirementAnalysis
ConstructionPlan
ImplementationPlan
```

注意：

- 工期基线是否属于该域，需要与 Project Delivery 明确；
- Project 只保存当前有效计划/方案 ID 或状态投影；
- 方案审批 Workflow 不应该把方案正文塞入 Workflow 聚合。

---

## 3.3 Implementation Execution

原 `Field Execution`。

### 结论

**PASS AFTER RENAME AND OWNER FIX**

建议 Owner：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
JointDebuggingResult
ImplementationRisk
FieldQualityCheck (V2)
EvidenceReference
```

禁止 Owner：

```text
DeviceCredential
CollectionTask
RawCollectionExecution
CutoverTask
DeliveryArtifactArchive
```

### 到货

推荐：

```text
ArrivalAcceptance
├── ArrivalBatch
├── ArrivalItem
├── ArrivalDifference
└── EvidenceReference
```

### 安装

推荐：

```text
InstallationRecord
├── DeviceReference
├── InstallationLocation
├── InstallationEvidence
└── InstallationVersion
```

### 配置与联调

必须分清：

```text
CollectionTask
    属于 Device Access & Collection

ConfigurationCollectionResult
JointDebuggingResult
    属于 Implementation Execution
```

业务域不能修改 CollectionTask 的执行状态。

---

## 3.4 Acceptance & Closure

### 结论

**PASS WITH OWNER REFINEMENT**

当前 Owner 中：

```text
统一归档
```

需要精确表达。

ACC-04 的本质是：

```text
来源业务事实的统一索引和归档状态
```

而不是重新拥有来源文件事实。

建议候选聚合：

```text
TrainingConfirmation
SatisfactionSurvey
AcceptanceReport
DeliveryArtifactIndex
ClosureCase
```

推荐将：

```text
Closure
```

改名：

```text
ClosureCase
```

因为 `Closure` 更像动作/状态，而 `ClosureCase` 能明确表达一轮闭环申请及其快照、回访、材料审核和整改链。

### DeliveryArtifactIndex

Owner：

```text
sourceRequirement
sourceRecordId
sourceVersion
fileHash
archiveStatus
requiredFlagSnapshot
```

不 Owner：

```text
ArrivalAcceptance 原始签收事实
ImplementationPlan 正文
Training 原始签字事实
```

---

## 3.5 Cutover

### 结论

**PASS**

核心聚合 `CutoverTask` 合理。

建议不要把所有内容都塞进 CutoverTask 单聚合。

可进一步评估：

```text
CutoverTask
CutoverAssessment
CutoverPlan
CutoverApprovalCase
CutoverExecution
```

是否需要独立一致性边界。

至少要明确：

```text
CutoverTask != CollectionTask
```

关系：

```text
Cutover
  -> command
Device Access & Collection
  -> CollectionResultAvailable
Cutover
  -> CutoverEvidence / Result Interpretation
```

CUT 不拥有凭证明文和采集引擎。

---

## 3.6 Inspection & Service

### 当前定义

Owner：

```text
巡检任务、规则、报告、服务问题、服务状态
```

### 结论

**REVIEW REQUIRED**

`Inspection` 边界明确，但 `Service` 容易过宽。

需要回答：

```text
“Service”具体包括什么？
```

如果只是 INS-01~09：

建议直接命名：

```text
Inspection
巡检域
```

如果还包含：

```text
服务问题
持续服务跟踪
通用服务工单
```

则需要证明这些对象与 Inspection 共用：

- 生命周期；
- 业务语言；
- 责任角色；
- 一致性边界。

否则建议未来拆：

```text
Inspection
Service Operations
```

不要因为都属于“售后”就放一个 Context。

---

## 3.7 Customer & Asset

### 结论

**PASS AFTER OWNER REWRITE**

可以暂时作为一个 Context，但内部至少要有两个独立聚合：

```text
Customer
Device
```

禁止：

```text
Customer
  └── Device[]
```

通过 Customer 聚合直接修改 Device。

推荐：

```text
Customer
Device
CustomerDeviceRelation
ProjectDeviceRelation
```

是否将 Relation 独立聚合，需要 Phase 2 按版本和并发规则决定。

重点：

**外部 Owner 字段必须保存 source + source version + synchronizedAt。**

---

## 3.8 Commercial Reference & Fulfillment

原 `Contract & Fulfillment`。

### 结论

**必须重构**

推荐正式名称：

```text
Commercial Reference & Fulfillment
商业引用与履约分配域
```

如果保留独立 Context，它真正负责的是：

```text
引用
分配
履约范围
对账
快照
```

而不是合同订单本体。

---

## 3.9 Supplier & Subcontract

### 结论

**PASS**

候选聚合：

```text
Supplier
Subcontract
PaymentGate
PaymentPlan
```

需要注意：

```text
PaymentGate
!=
Payment
```

平台拥有：

```text
付款门禁
付款计划
付款请求/同步状态
```

财务系统拥有：

```text
最终账务事实
```

若 PRD 明确平台保存已支付记录，则本域可以保存：

```text
PaymentRecordReference
FinancialResultSnapshot
```

但不能把财务账务本身变成本平台聚合。

---

## 3.10 Work Order & Time（新增）

### 结论

**必须新增**

建议：

| Context | Owner数据 | 允许引用 | 禁止直接修改 |
|---|---|---|---|
| Work Order & Time | 工单、补单、工时申报、责任区间、工单证据、工单状态历史 | 项目、设备、客户、工程师、割接任务、外部打卡证据 | 项目主状态、设备主档、钉钉原始打卡事实、财务账务 |

候选聚合：

```text
WorkOrder
TimeClaim
```

WO-06 割接保障工单仍属于 Work Order Context，但引用 CutoverTask。

不要因为叫“割接保障工单”就把它放入 Cutover Context。

---

## 3.11 Analytics

### 结论

**PASS**

Analytics 应坚持：

```text
read model / snapshot / projection only
```

禁止：

```text
通过报表反向修改业务交易
```

推荐数据来源：

```text
Domain Events
Published Read Models
ETL/Projection
```

不要直接跨域 join 所有业务表形成强耦合。

---

## 3.12 Platform Governance

### 结论

**REFACTOR REQUIRED**

建议不再作为单一 Bounded Context 描述。

在 SDS 中改为：

```text
Platform Capabilities
├── Identity & Access
├── Workflow & Tasking
├── Platform Configuration
├── Audit & Compliance
└── File Service
```

每项定义 API 和 Owner。

---

## 3.13 Integration

### 结论

**PASS AS SUPPORTING CONTEXT / ACL**

Integration 更接近：

```text
Anti-Corruption Layer
Integration Adapter
Mapping & Synchronization
```

而不是核心业务域。

它可以拥有：

```text
SourceMapping
SyncJob
SyncRecord
CallbackRecord
ExternalStatusMapping
ReconciliationRecord
```

但不拥有：

```text
CRM Customer
ERP Contract
ITR Incident
MES Device
```

也不能直接修改业务域数据库。

正确方向：

```text
External System
    ↓
Integration ACL
    ↓ command/event
Domain Application Service
```

---

## 3.14 Device Access & Collection（新增）

### 结论

**必须新增**

正式名称：

```text
Device Access & Collection
设备连接与采集域
```

Owner：

```text
DeviceCredential
CredentialGrant
CollectionTask
DeviceExecutionRecord
ExternalExecutionStatus
RawResultReference
CallbackEvidence
ExecutionAuthorization
```

允许引用：

```text
Project
Device
BusinessObject
PublishedCommandTemplate
```

禁止修改：

```text
Implementation result
Cutover result
Inspection result
Device master data
External collection engine state database
```

---

# 4. 聚合根完整性评审

当前 Aggregate Root 数量不足以支撑全部正式业务，但也不应该机械地“一个 PRD 实体 = 一个 Aggregate Root”。

Phase 1 必须增加一张：

```text
Aggregate Boundary Decision Matrix
```

判断标准：

| 判断问题 | 是时倾向 |
|---|---|
| 是否有独立生命周期？ | 独立 Aggregate |
| 是否有自己的强业务不变量？ | 独立 Aggregate |
| 是否需要独立并发控制？ | 独立 Aggregate |
| 是否可以通过 ID 独立操作？ | 独立 Aggregate |
| 是否必须和另一个对象同事务提交？ | 同一 Aggregate |
| 是否只是引用/快照/显示模型？ | Entity / VO / Projection |
| 是否来自外部 Owner？ | Reference / Snapshot / ACL |

建议候选集合：

```text
Project Delivery
- Project
- ProjectTemplate
- ProjectTask

Preparation & Solution
- SiteSurvey
- RequirementAnalysis
- ConstructionPlan
- ImplementationPlan

Implementation Execution
- ArrivalAcceptance
- InstallationRecord
- ConfigurationCollectionResult
- JointDebuggingResult
- ImplementationRisk
- FieldQualityCheck (V2)

Acceptance & Closure
- TrainingConfirmation
- SatisfactionSurvey
- AcceptanceReport
- DeliveryArtifactIndex
- ClosureCase

Cutover
- CutoverTask
- CutoverAssessment
- CutoverPlan
- CutoverExecution

Inspection
- InspectionTask
- InspectionRuleSet / RuleVersion
- InspectionReport

Work Order & Time
- WorkOrder
- TimeClaim

Customer & Asset
- Customer
- Device

Commercial Reference & Fulfillment
- OrderAllocation / DeliveryScopeAllocation (if context retained)

Supplier & Subcontract
- Supplier
- Subcontract
- PaymentGate
- PaymentPlan

Device Access & Collection
- DeviceCredential
- CollectionTask
```

注意：

这是一组 **候选 Aggregate Root**，不是要求全部最终独立建表/独立聚合。

Codex 必须逐个做 Boundary Decision，而不是全部照抄。

---

# 5. 对当前 `DeliveryEvidence` 聚合的建议

当前将 `DeliveryEvidence` 定义为 Aggregate Root，需要再做一次判定。

如果证据只有：

```text
fileRef
fileHash
sourceRecord
sourceVersion
uploadStatus
```

且其生命周期完全依附于：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
```

则更适合建模成：

```text
EvidenceReference
```

作为源聚合内部 Entity/Value Object。

只有当它具有独立的：

```text
版本管理
访问授权
审核
作废
替换
独立状态
```

且需要跨业务独立操作时，才建议提升为 `DeliveryEvidence` Aggregate Root。

无论哪种方式，ACC-04 都只建立 `DeliveryArtifactIndex`，不复制来源事实。

---

# 6. Context Map 完整修订建议

当前 Context Map：

```text
CRM/ERP -> Integration -> Project Delivery
-> Preparation/Solution
-> Field Execution
-> Acceptance/Closure
```

过于线性。

它会误导 Phase 2 认为业务数据必须逐级传递。

建议改成：

```text
                       External Systems
          CRM / ERP / ITR / MES / HR / OA / ...
                              |
                              v
                   Integration / ACL
                              |
         +--------------------+--------------------+
         |                    |                    |
         v                    v                    v
 Customer & Asset     Commercial Reference   Project Delivery
                                                  |
                    +-----------------------------+--------------------+
                    |                             |                    |
                    v                             v                    v
        Preparation & Solution        Implementation Execution      Cutover
                                                  |                    |
                                                  +---------+----------+
                                                            |
                                                            v
                                              Device Access & Collection
                                                            |
                                                            v
                                               Collection Sub-Application

Project Delivery
      |
      +-----------> Work Order & Time
      |
      +-----------> Supplier & Subcontract

Project Delivery / Preparation / Implementation / Cutover
                    |
                    v
           Acceptance & Closure

Inspection
   |                       |
   +----> Customer & Asset |
   +----------------------> Device Access & Collection

Business Contexts
   |
   +---- domain events / projections ----> Analytics
```

Platform capabilities 横向支撑：

```text
Identity & Access
Workflow & Tasking
Platform Configuration
Audit & Compliance
File Service
```

---

# 7. Context Map 不应只画箭头

DDD Context Map 还必须为每条关系定义集成模式。

建议增加矩阵：

| Upstream | Downstream | Pattern | Contract |
|---|---|---|---|
| CRM | Integration | ACL | CustomerSync |
| ERP | Integration | ACL | OrderSync |
| Integration | Project Delivery | Published Language / Command | ProjectSourceReceived |
| Project Delivery | Preparation | Customer/Supplier | ProjectContextQuery |
| Preparation | Project Delivery | Domain Event | ConstructionPlanApproved |
| Implementation | Collection | Customer/Supplier | CreateCollectionTask |
| Collection | Implementation | Domain Event | CollectionResultAvailable |
| Cutover | Collection | Customer/Supplier | CreateCollectionTask |
| Inspection | Collection | Customer/Supplier | CreateCollectionTask |
| Implementation | Acceptance | Published Event / Query | EvidenceAvailable |
| Acceptance | Project Delivery | Domain Event | ProjectClosureCompleted |

这样 Phase 2 才知道哪些应该设计：

```text
API
Command
Query
Event
Snapshot
```

---

# 8. 跨域契约章节目前过薄

当前只有：

- 应用服务/查询/事件；
- 外部来源字段；
- 凭证授权快照。

建议扩充成以下强约束。

## 8.1 数据访问

```text
禁止跨 Context Repository 直接调用。
禁止跨 Context 直接 UPDATE 对方表。
```

允许：

```text
Application API
Query API
Internal Event
Published Read Model
```

## 8.2 身份引用

跨 Context 只保存：

```text
ForeignAggregateId
ExternalSourceId
Version / SnapshotId
```

不共享可变实体对象。

## 8.3 事务

一个本地事务只保证一个 Aggregate 的强一致性。

跨 Context：

```text
eventual consistency
outbox
inbox
idempotency
compensation
reconciliation
```

不得通过跨域大事务强行一致。

## 8.4 状态 Owner

业务对象状态只能由 Owner Context 迁移。

其他域只能发布：

```text
Fact Event
```

例如：

```text
ImplementationCompleted
```

不能：

```text
project.status = S5
```

## 8.5 外部状态

必须保存：

```text
externalOriginalStatus
mappingVersion
mappedPlatformStatus
receivedAt
```

外部状态不能直接成为平台生命周期状态。

## 8.6 权限

跨域调用必须保留：

```text
actor
tenant
organization scope
project scope
resource scope
authorization decision / grant snapshot
```

不能因为内部服务调用就默认系统管理员权限。

## 8.7 审计

跨域事件必须至少关联：

```text
traceId
eventId
aggregateId
aggregateVersion
actor
sourceContext
targetContext
occurredAt
```

## 8.8 文件

业务域只持有：

```text
FileRef
FileHash
FileVersion
Classification
```

File Service 负责存储和受控下载。

业务域 Owner 负责：

```text
“这个文件在业务中意味着什么”
```

---

# 9. V1 / V2 边界必须进入领域模型

当前领域模型只描述结构，没有充分标记版本边界。

建议 Context/聚合表增加：

```text
IntroducedIn
RequiredForV1
EnhancementIn
```

典型：

| Capability | Version |
|---|---|
| Project / ProjectTemplate | V1 |
| Customer CRUD | V1 |
| Device core archive | V1 |
| DeviceCredential / CollectionTask | V1 |
| Implementation Execution core | V1 |
| Cutover core | V1 |
| WorkOrder attendance sync | V1 |
| Inspection | V2 |
| FieldQualityCheck | V2 |
| Full subcontract | V2 |
| Customer Asset overview | V2 |
| WorkOrder advanced capabilities | V2 |
| V3 items | 不进入当前实现模型门禁 |

特别注意：

`FieldQualityCheck` 可以在 Domain Model 中预留，但必须：

```text
RequiredForV1 = false
IntroducedIn = V2
```

不得成为 V1 的：

```text
Stage Guard
Closure Gate
Release Gate
```

---

# 10. 命名规范建议

建议所有 Context 使用“业务名词”而不是技术概念。

推荐最终命名：

| 当前 | 推荐 | 中文 |
|---|---|---|
| Project Delivery | Project Delivery | 项目交付域 |
| Preparation & Solution | Preparation & Solution | 交付准备与方案域 |
| Field Execution | **Implementation Execution** | **实施执行域** |
| Acceptance & Closure | Acceptance & Closure | 验收闭环域 |
| Cutover | Cutover | 割接域 |
| Inspection & Service | Inspection（若只含巡检） | 巡检域 |
| Customer & Asset | Customer & Asset | 客户资产域 |
| Contract & Fulfillment | **Commercial Reference & Fulfillment** | 商业引用与履约分配域 |
| Supplier & Subcontract | Supplier & Subcontract | 服务商与转包域 |
| Work Order & Time | **新增** | 工单与工时域 |
| Analytics | Analytics | 分析域 |
| Platform Governance | Platform Capabilities（不要作为单一大域） | 平台支撑能力 |
| Integration | Integration / ACL | 集成适配域 |
| Device Access & Collection | **新增** | 设备连接与采集域 |

---

# 11. 推荐修订后的 Bounded Context 表

| Context | Owner 数据/事实 | 允许引用 | 禁止直接修改 |
|---|---|---|---|
| Project Delivery | Project、Template、Lifecycle、Task、Assignment、Hierarchy、Progress Snapshot | Customer、Commercial Ref、Device、下游结果投影 | 客户/设备权威字段、其他域业务事实 |
| Preparation & Solution | SiteSurvey、RequirementAnalysis、ConstructionPlan、ImplementationPlan | Project、Template、User | Project 主状态、Implementation 事实 |
| Implementation Execution | ArrivalAcceptance、InstallationRecord、Configuration/Debugging Business Result、ImplementationRisk、FieldQualityCheck(V2)、EvidenceReference | Project、Device、ApprovedSolution、CollectionTask | DeviceCredential、CollectionTask 状态、Cutover 内部状态、验收归档状态 |
| Acceptance & Closure | Training、SatisfactionSurvey、AcceptanceReport、DeliveryArtifactIndex、ClosureCase | Project、各域 Evidence/Result | 来源实施事实、合同/财务事实 |
| Cutover | CutoverTask、Assessment、Plan、Execution、Evidence | Project、Device、CollectionTask、配置字典 | 采集执行引擎、凭证明文、Project 主状态 |
| Inspection | InspectionTask、RuleVersion、InspectionResult、Report、InspectionIssue | Project、Device、CollectionTask | Credential 授权、UMC 原始 Owner 数据 |
| Work Order & Time | WorkOrder、TimeClaim、ResponsibilityInterval、工单业务证据 | Project、Device、Customer、User、CutoverTask | 项目主状态、设备主档、钉钉原始证据 |
| Customer & Asset | 平台客户扩展、临时客户、Customer/Device 关系、设备归属、维保基本信息、资产聚合视图 | CRM/MES/ITR Snapshot、Project、Service Record | CRM/MES/ITR 权威字段源事实、Project 状态 |
| Commercial Reference & Fulfillment | Contract/Order Reference、Order Allocation、Delivery Scope Allocation、Fulfillment Snapshot、Reconciliation | ERP Snapshot、Project | ERP Contract/SalesOrder、财务账务 |
| Supplier & Subcontract | Supplier、Subcontract、PaymentGate、PaymentPlan、FinancialResultReference | Project、Commercial Ref、Survey | 财务账务事实 |
| Device Access & Collection | DeviceCredential、CredentialGrant、CollectionTask、DeviceExecutionRecord、ExternalStatus、ResultReference、CallbackEvidence | Project、Device、业务单据、Published Command Template | IMP/CUT/INS 业务结论、Device 主档、外部采集引擎数据库 |
| Analytics | Projection、Metric Snapshot、Portfolio View | 各域 Published Facts | 所有业务交易 |
| Integration / ACL | SourceMapping、SyncRecord、CallbackRecord、ExternalStatusMapping、Reconciliation | External Systems、Domain Application API | 外部数据库、业务域聚合内部状态 |

平台支撑能力单独列出，不作为上述业务 Context 的“万能父域”。

---

# 12. Aggregate Owner 必须形成唯一矩阵

Phase 1 最终必须增加：

| Aggregate | Owner Context | Version | Status |
|---|---|---|---|
| Project | Project Delivery | V1 | Confirmed |
| ProjectTemplate | Project Delivery | V1 | Candidate |
| ProjectTask | Project Delivery | V1 | Confirmed |
| SiteSurvey | Preparation & Solution | V1 | Candidate |
| RequirementAnalysis | Preparation & Solution | V1 | Candidate |
| ConstructionPlan | Preparation & Solution | V1 | Candidate |
| ImplementationPlan | Preparation & Solution | V1 | Candidate |
| ArrivalAcceptance | Implementation Execution | V1 | Candidate |
| InstallationRecord | Implementation Execution | V1 | Candidate |
| ConfigurationCollectionResult | Implementation Execution | V1 | Candidate |
| FieldQualityCheck | Implementation Execution | V2 | Candidate |
| DeliveryArtifactIndex | Acceptance & Closure | V1 | Candidate |
| ClosureCase | Acceptance & Closure | V1 | Candidate |
| WorkOrder | Work Order & Time | V1 | Candidate |
| TimeClaim | Work Order & Time | V2 / according to PRD release | Candidate |
| Device | Customer & Asset | V1 | Confirmed |
| Customer | Customer & Asset | V1 | Candidate |
| DeviceCredential | Device Access & Collection | V1 | Confirmed |
| CollectionTask | Device Access & Collection | V1 | Confirmed |
| CutoverTask | Cutover | V1 | Confirmed |
| InspectionTask | Inspection | V2 | Confirmed |
| Subcontract | Supplier & Subcontract | V2 | Candidate |
| PaymentGate | Supplier & Subcontract | V2 | Candidate |

“Candidate” 不是缺陷。

它表示在 Phase 2 前还要通过 Aggregate Boundary Decision 确认。

---

# 13. 不变量评审建议

当前不变量总体方向正确，但需要避免把跨域最终一致性写成单聚合强不变量。

例如当前：

```text
Project:
闭环需满足全部后代闭环门禁
```

这是 Project 自身可以校验的层级不变量，合理。

但类似：

```text
项目只有在全部交付件、回访、材料审核完成后才关闭
```

不要全部写进 Project Aggregate。

应变成：

```text
Acceptance & Closure
    形成 ClosureCompleted fact

Project
    消费/验证 ClosureCompleted
    执行 Project transition
```

同理：

```text
CutoverCompleted
ImplementationExecutionCompleted
ConstructionPlanApproved
```

都应属于各自 Owner Context 的业务事实。

原则：

> Aggregate Invariant 只覆盖本 Aggregate 在一个事务边界内能够可靠判断和保护的事实。

跨 Context Gate：

```text
Snapshot + Query + Version + Event
```

而不是直接拿其他 Repository 强校验。

---

# 14. Phase 2 前必须新增的设计产物

建议 Codex 在 `02-domain-model.md` 之外增加：

```text
docs/design/
├── 02-domain-model.md
├── 02a-context-map.md
├── 02b-aggregate-boundary-decisions.md
├── 02c-data-ownership-matrix.md
├── 02d-cross-context-contracts.md
└── 02e-version-scope-matrix.md
```

这样不要把所有领域设计继续堆入一个巨型文件。

---

# 15. Phase 2 Gate

完成以下所有项后：

```text
SDS PHASE 1 DOMAIN MODEL = PASS
```

### 必须完成

- [ ] `Field Execution` 全部改名为 `Implementation Execution`
- [ ] 增加 `Device Access & Collection`
- [ ] 增加 `Work Order & Time`
- [ ] `FieldQualityCheck` 明确 P1/V2，不能成为 V1 门禁
- [ ] Customer & Asset 改成字段/事实级 Owner
- [ ] Contract & Fulfillment 完成重命名/边界选择
- [ ] Platform Governance 不再作为万能业务 Context
- [ ] Inspection & Service 明确“Service”的真实范围
- [ ] 每个 Aggregate Root 有且只有一个 Owner Context
- [ ] DeliveryEvidence 完成“Aggregate Root 还是 EvidenceReference”的决策
- [ ] Context Map 使用 Context，而不是 Aggregate 名称作为节点
- [ ] Context Map 标注 upstream/downstream 与 contract pattern
- [ ] 跨 Context Repository/DB Update 明确禁止
- [ ] 跨域 Event 使用幂等、Outbox/Inbox、版本和 Trace
- [ ] 状态 Owner 唯一
- [ ] V1/V2 版本矩阵补齐
- [ ] 无 V3/OUT_OF_SCOPE 被误纳入当前 Gate

### Phase 2 才允许开始

```text
08-data-model.md
09-database-design.md
10-api-design.md
11-event-design.md
12-integration-design.md
```

在此之前不建议 Codex 生成完整数据库表和 OpenAPI。

---

# 16. 最终评审结论

## 当前状态

```text
SDS Phase 1
Domain Model

STATUS = CONDITIONAL_PASS
```

## 评分

| 维度 | 当前评价 |
|---|---|
| 业务域识别 | 良好 |
| Project/Cutover/Inspection 分离 | 良好 |
| Implementation Execution 定位 | 基本正确，需重命名和 Owner 修正 |
| 数据 Owner | 需整改 |
| Aggregate 完整性 | 需补充 |
| Work Order 覆盖 | 缺失 |
| Device Access & Collection | 缺失 |
| Platform Support Boundary | 过宽 |
| Context Map | 需要从流程图升级为真正 Context Map |
| V1/V2 边界 | 需显式进入模型 |
| 可进入数据库设计程度 | 尚未达到 |
| 可继续 Phase 1 修订 | 可以 |

## 推荐动作

不要推倒重写。

让 Codex 基于当前版本做一次 **Phase 1 Domain Model Refactoring**，完成本文列出的 blocker 与 Gate 项，再进行第二轮领域模型评审。

第二轮通过后，才正式进入：

```text
Domain Model Baseline
        ↓
Data Model
        ↓
Database Design
        ↓
API Design
        ↓
Event Design
        ↓
Integration Design
```
