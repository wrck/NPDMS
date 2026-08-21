# Codex 可归档执行提示：Phase 1 `Field*` 业务命名统一与领域模型整改

> 文件建议路径：`prompts/codex-phase1-field-naming-refactor.md`  
> 用途：统一 SDS Phase 1 中所有以 `Field` 表达“现场/实施”业务语义的 Context、Aggregate、State Machine、Workflow、权限、追溯矩阵及生成脚本命名。  
> 本提示是工程整改任务，不改变 PRD 业务语义，不解除现有 Phase 1 `NO-GO / NOT_READY_FOR_PHASE_2` 门禁。

---

# 1. 背景

当前 SDS Phase 1 中存在以 `Field` 表示“现场/实施”的业务命名，例如：

- `Field Execution`
- `FieldQualityCheck`

独立评审已经指出：

- `Field Execution` 需求映射过宽；
- `FieldQualityCheck` 等实施聚合缺少完整状态机；
- IMP-01/IMP-02 质量/安全检查工作流需要补齐；
- Phase 1 当前仍为 `NO-GO / NOT_READY_FOR_PHASE_2`。

同时，`Field` 在软件工程上下文中通常也用于：

- database field
- form field
- field permission
- field mapping
- object field

因此不能进行全仓库无脑字符串替换。

本任务要求：

> **只统一“Field = 现场/实施”这一业务领域语义；保留“Field = 字段”这一技术语义。**

---

# 2. 最高基线

执行前必须读取：

1. 根目录 `AGENTS.md`
2. 正式 PRD baseline（当前工程链使用的正式版本）
3. `docs/design/02-domain-model.md`
4. `docs/design/05-state-machine.md`
5. `docs/design/06-workflow-design.md`
6. `docs/design/07-authorization-design.md`
7. `docs/traceability/requirement-matrix.md`
8. `scripts/generate_requirement_traceability.py`
9. `docs/engineering/gates/phase-1/gate-status.md`
10. `docs/engineering/gates/phase-1/independent-review.md`
11. 当前 Phase 1 工程链、Context Map、Owner Matrix、版本范围矩阵

如果 `AGENTS.md` 仍指向旧 PRD 路径，先按当前正式工程链修正唯一 baseline 路径。

不得自行选择旧 PRD 或旧 `specs` 作为业务基线。

---

# 3. 任务目标

完成一次全仓库 **Business Naming Refactoring**，统一所有表示“现场实施/实施执行”的 `Field*` 业务命名。

目标正式术语：

```text
Bounded Context:
Implementation Execution

中文：
实施执行域

代码模块简称：
implementation
```

核心原则：

```text
Field = 现场/实施业务语义
    -> 必须评审并统一到 Implementation / Site / Execution 等明确业务词

Field = 字段技术语义
    -> 必须保留
```

---

# 4. 标准命名映射

## 4.1 必须直接替换的已确认名称

| 旧名称 | 新名称 | 中文 | 备注 |
|---|---|---|---|
| `Field Execution` | `Implementation Execution` | 实施执行域 | Bounded Context 正式名称 |
| `FieldExecution` | `ImplementationExecution` | 实施执行 | 类型/标识符 |
| `FieldExecutionContext` | `ImplementationExecutionContext` | 实施执行上下文 | 若存在 |
| `FieldExecutionModule` | `ImplementationModule` | 实施模块 | 代码模块优先简化 |
| `field-execution` | `implementation` 或 `implementation-execution` | 实施模块 | 代码模块优先 `implementation` |
| `field_execution` | `implementation_execution` | 实施执行 | 仅确属业务标识符时 |
| `FieldQualityCheck` | `ImplementationQualityCheck` | 实施质量检查 | IMP-01/质量检查 |
| `FieldSafetyCheck` | `ImplementationSafetyCheck` | 实施安全检查 | 若当前模型存在独立安全检查聚合 |
| `FieldQualityCheckState` | `ImplementationQualityCheckState` | 实施质量检查状态 | 若存在 |
| `FieldSafetyCheckState` | `ImplementationSafetyCheckState` | 实施安全检查状态 | 若存在 |
| `FieldQualityCheckWorkflow` | `ImplementationQualityCheckWorkflow` | 实施质量检查流程 | 若存在 |
| `FieldSafetyCheckWorkflow` | `ImplementationSafetyCheckWorkflow` | 实施安全检查流程 | 若存在 |

---

# 5. 禁止机械替换的 `Field*`

以下名称如果存在，**必须先根据业务含义判断，不得自动替换为 `Implementation*`**：

```text
FieldEvidence
FieldBatch
FieldTask
FieldRecord
FieldResult
FieldOperation
FieldWork
FieldSite
FieldLocation
FieldEngineer
FieldService
FieldIssue
FieldInspection
```

逐项按以下规则判断。

## 5.1 如果表达“实施阶段”

优先使用：

```text
Implementation*
```

例如：

```text
FieldResult
若实际表示实施执行业务结果
    -> ImplementationResult
```

## 5.2 如果表达“现场地点/站点”

优先使用：

```text
Site*
```

例如：

```text
FieldLocation
若实际表示客户现场位置
    -> SiteLocation
```

## 5.3 如果表达“现场工程师”

优先根据真实角色使用：

```text
ImplementationEngineer
OnsiteEngineer
ServiceEngineer
```

不得在没有 PRD 角色依据时自行新增 `OnsiteEngineer` 角色。

## 5.4 如果表达“现场服务”

必须先确认它属于：

```text
Implementation Execution
Inspection
Work Order & Time
Service Operations
```

不能因为名称有 `FieldService` 就自动放进 Implementation Execution。

## 5.5 无法确认

标记：

```text
BLOCKED_BY_NAMING_REVIEW
```

写入：

```text
docs/decisions/open-questions.md
```

不得猜测。

---

# 6. 必须保留的技术 `field`

以下 `field` 表示“字段”，不得改名：

```text
field permission
field-level permission
field security
field mapping
field definition
field validation
field type
field value
database field
table field
form field
request field
response field
API field
JSON field
entity field
object field
custom field
required field
sensitive field
```

中文对应：

```text
字段权限
字段安全
字段映射
字段定义
字段校验
字段类型
字段值
数据库字段
表字段
表单字段
接口字段
自定义字段
必填字段
敏感字段
```

这些属于软件/数据技术语义，不属于本次命名整改。

---

# 7. 全仓库扫描

先扫描，不要先修改。

执行等价检查：

```bash
rg -n --hidden \
  --glob '!**/.git/**' \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  '\bField[A-Z][A-Za-z0-9_]*\b|Field Execution|field-execution|field_execution' .
```

然后扫描小写 `field`：

```bash
rg -n --hidden \
  --glob '!**/.git/**' \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  '\bfield\b' \
  docs specs scripts backend frontend database tests
```

如果仓库目录不同，按实际目录调整。

---

# 8. 建立命名清单

先生成：

```text
docs/engineering/gates/phase-1/naming-inventory.md
```

每一个命中项必须分类。

格式：

| Location | Symbol/Text | Category | Meaning | Action | Target Name |
|---|---|---|---|---|---|
| `02-domain-model.md` | Field Execution | BUSINESS_DOMAIN | 实施执行域 | RENAME | Implementation Execution |
| `05-state-machine.md` | FieldQualityCheck | BUSINESS_AGGREGATE | 实施质量检查 | RENAME | ImplementationQualityCheck |
| `07-authorization-design.md` | field permission | TECHNICAL_FIELD | 字段权限 | KEEP | field permission |

Category 只能使用：

```text
BUSINESS_CONTEXT
BUSINESS_AGGREGATE
BUSINESS_ENTITY
BUSINESS_STATE_MACHINE
BUSINESS_WORKFLOW
BUSINESS_EVENT
BUSINESS_PERMISSION_RESOURCE
BUSINESS_MODULE
TECHNICAL_FIELD
HISTORICAL_EVIDENCE
UNKNOWN
```

任何 `UNKNOWN` 必须人工/规格确认。

---

# 9. 历史评审文件处理规则

以下类型文件属于评审证据：

```text
docs/engineering/gates/phase-1/independent-review.md
已签署 review
已批准 baseline review
历史审计报告
```

**原则上不得为了统一命名直接改写历史评审原文。**

例如历史报告中：

```text
Field Execution
FieldQualityCheck
```

继续保留原文。

新增：

```text
docs/decisions/ADR-xxx-implementation-execution-naming.md
```

记录：

```text
Former Name -> Canonical Name

Field Execution -> Implementation Execution
FieldQualityCheck -> ImplementationQualityCheck
FieldSafetyCheck -> ImplementationSafetyCheck
```

新的评审、SDS、矩阵、脚本和代码只使用新名称。

如果旧文件属于未签署工作稿，可修改，但必须在 Git 历史中可追溯。

---

# 10. 必须创建 Naming ADR

创建：

```text
docs/decisions/ADR-xxx-implementation-execution-naming.md
```

内容至少包含：

```text
Status: Accepted
Decision:
Field Execution -> Implementation Execution

Reason:
1. Field 在研发上下文中容易理解为“字段”；
2. PRD 业务语言是实施、实施部署、实施执行；
3. Context 不仅包含现场动作，还包含配置结果解释、业务联调、实施风险和实施证据；
4. Implementation Execution 更符合 bounded context 的业务职责。

Scope:
- SDS active documents
- Context Map
- Aggregate names
- state machine
- workflow
- authorization
- traceability matrix
- generators
- future source code/module naming

Excluded:
- database/form/API “field” technical semantics
- immutable historical review evidence

Aliases:
Field Execution = former name
Implementation Execution = canonical name
```

---

# 11. 领域模型同步整改

修改当前活动版：

```text
docs/design/02-domain-model.md
```

正式名称：

```text
Implementation Execution
```

Owner 至少应精确到：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
JointDebuggingResult
ImplementationRisk
ImplementationQualityCheck (V2)
ImplementationSafetyCheck (V2, if independently modeled)
EvidenceReference / DeliveryEvidence (according to boundary decision)
```

禁止 Owner：

```text
DeviceCredential
CollectionTask
RawCollectionExecution
CutoverTask
DeliveryArtifactArchive
```

---

# 12. Quality / Safety 聚合命名决策

PRD 的质量检查和安全检查是业务能力，不应继续使用 `Field*`。

优先方案：

```text
ImplementationQualityCheck
ImplementationSafetyCheck
```

如果当前实现将二者建成一个统一可配置检查聚合，则可以使用：

```text
ImplementationComplianceCheck
```

内部区分：

```text
CheckType:
QUALITY
SAFETY
```

但必须满足：

1. PRD 允许二者共享生命周期；
2. 权限一致；
3. 整改/复核规则一致；
4. 不会丢失质量与安全的独立业务语义。

如果不能证明，继续保持两个聚合。

不得为了减少表数量强行合并。

---

# 13. 需求追溯矩阵必须同步

当前独立评审已经指出 EXE/IMP 被机械映射到全部实施聚合。

本次命名整改必须同时修复追溯责任。

至少应达到：

```text
EXE-01
    -> ArrivalAcceptance

EXE-02
    -> InstallationRecord

EXE-03
    -> ConfigurationCollectionResult
    -> CollectionTask (cross-context dependency, not owner)

EXE-04
    -> JointDebuggingResult
    -> CollectionTask (cross-context dependency, not owner)

EXE-05
    -> ImplementationRisk

EXE-06
    -> Cutover readiness contract / CutoverTask dependency

IMP-01
    -> ImplementationQualityCheck

IMP-02
    -> ImplementationSafetyCheck
```

如果 PRD V1.6 实际编号/标题不同，以正式 PRD 为准，不得凭本提示覆盖 PRD。

---

# 14. 修复追溯生成脚本

检查：

```text
scripts/generate_requirement_traceability.py
```

当前评审指出 IMP 默认映射存在机械映射问题。

必须：

1. 删除 “IMP/EXE -> 所有 Implementation 聚合” 默认规则；
2. 改为显式 Requirement ID -> Aggregate Mapping；
3. 新命名全部使用 `Implementation*`；
4. 生成结果必须稳定、可重复；
5. 禁止手工修改生成结果而不修改生成器。

生成后重新验证：

```text
115/115 requirements
V1/V2 counts
aggregate ownership
version scope
```

以当前正式 PRD 统计为准。

---

# 15. State Machine 同步

扫描：

```text
docs/design/05-state-machine.md
```

所有业务 `Field*` 状态机改名。

必须明确：

```text
ArrivalAcceptance
InstallationRecord
ConfigurationCollectionResult
ImplementationQualityCheck
ImplementationSafetyCheck
```

分别属于：

```text
STATEFUL_AGGREGATE
```

或：

```text
STATELESS_FACT_RECORD
```

不能处于“有状态字段但没有状态机”的中间状态。

如果为 STATEFUL，必须定义：

```text
states
transitions
guards
roles
preconditions
postconditions
terminal states
events
audit
version
```

---

# 16. Workflow 同步

扫描：

```text
docs/design/06-workflow-design.md
```

所有：

```text
FieldQuality*
FieldSafety*
FieldExecution*
```

改为：

```text
ImplementationQuality*
ImplementationSafety*
ImplementationExecution*
```

IMP-01/IMP-02 至少需要覆盖：

```text
检查提交
    ->
复核
    ->
整改
    ->
再复核
    ->
通过 / 阻断
```

如存在豁免：

```text
豁免申请
-> 有权角色审批
-> 明确风险
-> 明确范围
-> 明确有效期
-> 完整审计
```

不得因为改名顺便改变业务规则。

---

# 17. Authorization 同步

扫描：

```text
docs/design/07-authorization-design.md
```

注意两类 Field：

## 17.1 需要改名

例如：

```text
FieldExecutionResource
FieldQualityCheckAction
FieldSafetyCheckPermission
```

如果表示实施业务资源，改为：

```text
ImplementationExecutionResource
ImplementationQualityCheckAction
ImplementationSafetyCheckPermission
```

## 17.2 必须保留

例如：

```text
fieldPermission
field-level authorization
sensitive field
```

这些表示“字段权限”，必须保留。

不要误改成：

```text
implementationPermission
```

---

# 18. Context Map 同步

所有 Context Map 使用：

```text
Implementation Execution
```

禁止继续出现：

```text
Field Execution
```

推荐关系：

```text
Preparation & Solution
        |
        v
Implementation Execution
        |
        +------> Device Access & Collection
        |
        +------> Cutover
        |
        v
Acceptance & Closure
```

注意 Context Map 只能使用 Context 作为主要节点。

Aggregate：

```text
CollectionTask
CutoverTask
ImplementationQualityCheck
```

只能出现在 Context 内部说明或契约说明中，不能冒充 Context 节点。

---

# 19. Data Owner Matrix 同步

修改/生成：

```text
docs/design/02c-data-ownership-matrix.md
```

要求：

```text
Implementation Execution owns:
- arrival facts
- installation facts
- implementation result interpretation
- implementation risk
- implementation quality/safety checks
- implementation evidence semantics

Device Access & Collection owns:
- credentials
- grants
- collection task
- external execution status
- raw result reference
- callback evidence
```

不允许：

```text
Implementation Execution owns CollectionTask
```

---

# 20. 文件、目录、包名和代码标识符

如果当前代码尚未正式实现，只修改设计/脚手架中的活动命名。

推荐：

```text
Bounded Context:
Implementation Execution

Directory:
implementation/

Java package:
...implementation

Module:
implementation
```

避免：

```text
field
fieldexecution
implementationexecution
```

过长包名。

如果已经有正式代码：

1. 先生成 rename impact；
2. 保证 class/package/import/test/config/API/event 名同步；
3. 如果涉及公开 API/Event，必须考虑版本兼容；
4. 不得在未评审的情况下破坏持久化表名或外部契约。

---

# 21. 不建议自动修改的数据库对象

即使未来 Phase 2 出现：

```text
field_xxx
```

也必须先确认这里的 `field` 是：

```text
字段
```

还是：

```text
现场实施
```

当前 Phase 1 尚未批准进入数据库设计，因此本任务：

> **不得新增或重命名正式数据库表、字段、索引。**

---

# 22. 测试同步

扫描测试和设计验证：

```text
FieldExecution*
FieldQualityCheck*
FieldSafetyCheck*
```

业务命名统一为：

```text
ImplementationExecution*
ImplementationQualityCheck*
ImplementationSafetyCheck*
```

同时增加一个命名一致性检查。

建议脚本：

```text
scripts/check_business_naming.py
```

至少检查活动工程文件中不再出现：

```text
Field Execution
FieldExecution
FieldQualityCheck
FieldSafetyCheck
field-execution
```

历史评审证据目录允许通过 allowlist 保留。

---

# 23. 生成命名一致性 Gate

新增：

```text
docs/engineering/gates/phase-1/naming-review.md
```

必须包含：

```text
Active business Field identifiers: 0
Technical field semantics mistakenly renamed: 0
Historical evidence modified: 0
UNKNOWN naming items: 0
BLOCKED_BY_NAMING_REVIEW: 0
Traceability generator updated: PASS
Traceability regenerated: PASS
State machine names aligned: PASS
Workflow names aligned: PASS
Authorization business resource names aligned: PASS
Context map aligned: PASS
```

如果任何一项不满足：

```text
NAMING_GATE = FAIL
```

---

# 24. 不改变现有 Phase 1 NO-GO

非常重要：

本命名整改完成：

```text
!= Phase 1 Approved
```

当前 Phase 1 仍有独立硬门禁，例如：

```text
领域 Owner 签署
实现工作包登记
INT-12 集成形态确认
状态机完整性
IMP-01/02 Workflow
操作级权限矩阵
```

本任务只能关闭：

```text
NAMING CONSISTENCY
TRACEABILITY NAMING
```

不能自动将：

```text
NOT_READY_FOR_PHASE_2
```

改成：

```text
APPROVED
```

---

# 25. 输出文件

本次 Codex 执行必须生成/更新：

```text
docs/decisions/ADR-xxx-implementation-execution-naming.md
docs/engineering/gates/phase-1/naming-inventory.md
docs/design/02-domain-model.md
docs/design/02a-context-map.md
docs/design/02b-aggregate-boundary-decisions.md
docs/design/02c-data-ownership-matrix.md
docs/design/02d-cross-context-contracts.md
docs/design/02e-version-scope-matrix.md
docs/design/05-state-machine.md
docs/design/06-workflow-design.md
docs/design/07-authorization-design.md
docs/engineering/gates/phase-1/naming-review.md
docs/traceability/requirement-matrix.md
scripts/generate_requirement_traceability.py
```

如不存在某个拆分文件：

- 按当前正式工程链创建；
- 不得删除已有内容；
- 保持引用关系。

---

# 26. 历史文件策略

以下文件如果已经作为评审证据：

```text
docs/engineering/gates/phase-1/independent-review.md
旧 docs/engineering/gates/phase-1/gate-status.md
签署后的 review
baseline snapshot
```

不得直接覆盖旧术语。

在 ADR 中记录：

```text
Historical term:
Field Execution
Canonical term after ADR:
Implementation Execution
```

新的：

```text
docs/engineering/gates/phase-1/naming-review.md
docs/engineering/gates/phase-1/independent-review.md
```

只能使用新术语。

---

# 27. 自审清单

修改完成后逐项回答：

- [ ] 所有业务 `Field Execution` 是否已改为 `Implementation Execution`？
- [ ] `FieldQualityCheck` 是否已改为 `ImplementationQualityCheck`？
- [ ] 若存在 `FieldSafetyCheck`，是否已改为 `ImplementationSafetyCheck`？
- [ ] 是否存在其他 `Field*` 业务标识？
- [ ] 是否逐项完成语义分类，而不是字符串替换？
- [ ] 数据库/API/表单“字段”语义是否全部保留？
- [ ] 历史第三方评审是否未被篡改？
- [ ] ADR 是否记录 former name / canonical name？
- [ ] Requirement Matrix 是否不再机械映射 IMP/EXE 到全部实施聚合？
- [ ] 生成脚本是否同步修复？
- [ ] State Machine 是否使用新聚合名？
- [ ] Workflow 是否使用新聚合名？
- [ ] Authorization 的业务资源是否使用新名称？
- [ ] field-level permission 是否保持原技术语义？
- [ ] Context Map 是否只使用 `Implementation Execution`？
- [ ] Data Owner 是否仍然正确？
- [ ] V1/V2 scope 是否未被改名动作改变？
- [ ] Phase 1 状态是否仍按真实 Gate 判定，没有误改为 APPROVED？

---

# 28. 最终报告格式

输出：

```text
# Phase 1 Business Naming Refactoring Report

## Baseline
## Files Scanned
## Naming Inventory
## Direct Renames
## Semantic Renames
## Technical Field Terms Preserved
## Historical Evidence Preserved
## Requirement Traceability Changes
## Generator Changes
## State Machine Changes
## Workflow Changes
## Authorization Changes
## Context Map Changes
## Open Questions
## Tests / Validation
## Remaining Phase 1 Blockers
## Final Naming Gate
```

最终结论只能是：

```text
NAMING_GATE = PASS
```

或：

```text
NAMING_GATE = FAIL
```

本结论**不得替代**：

```text
PHASE_1_GATE
```

---

# 29. 执行边界

本轮允许：

```text
领域命名整改
追溯矩阵修复
生成器修复
SDS Phase 1 文档同步
状态机/Workflow/权限命名同步
命名校验脚本
ADR
```

本轮禁止：

```text
正式数据库设计
正式 OpenAPI 设计
正式 Event Contract Phase 2
正式 Integration Contract Phase 2
业务代码实现
V1/V2 范围调整
PRD 修改
自行新增业务规则
```

---

# 30. Codex 执行指令

现在开始执行本提示。

顺序必须是：

```text
READ BASELINE
    ->
SCAN
    ->
BUILD NAMING INVENTORY
    ->
CLASSIFY FIELD SEMANTICS
    ->
WRITE ADR
    ->
RENAME ACTIVE BUSINESS IDENTIFIERS
    ->
FIX REQUIREMENT MAPPING
    ->
FIX TRACEABILITY GENERATOR
    ->
SYNC DOMAIN MODEL
    ->
SYNC STATE MACHINE
    ->
SYNC WORKFLOW
    ->
SYNC AUTHORIZATION
    ->
SYNC CONTEXT MAP / OWNER MATRIX
    ->
RUN VALIDATION
    ->
WRITE NAMING REVIEW
```

在完成扫描和 inventory 之前不得执行全局替换。

发现无法判断的 `Field*`：

```text
STOP THAT ITEM
-> BLOCKED_BY_NAMING_REVIEW
-> RECORD OPEN QUESTION
```

其他独立项可以继续。

完成后停止，不进入 Phase 2。
