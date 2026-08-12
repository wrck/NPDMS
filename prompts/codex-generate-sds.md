# Codex Prompt：生成系统详细设计 SDS

你现在负责《项目实施交付管理平台》的系统详细设计。

## 一、输入

首先读取：

1. `AGENTS.md`
2. `docs/baseline/prd-v1.6.md`
3. `docs/engineering/00-engineering-chain.md`
4. `docs/design/README.md`
5. `docs/baseline/requirement-baseline.yaml`
6. `docs/decisions/open-questions.md`

PRD 是最高业务语义基线。

本项目不使用项目记忆；只允许依据当前工作树、当前会话和仓库内已批准资料工作。若快照哈希、版本或状态与元数据不一致，先标记`STALE`并停止生成。

## 二、目标

生成可直接指导研发实现、测试和评审的《系统详细设计说明书 SDS》，不是对 PRD 的摘要或改写。

## 三、禁止事项

你不得：

- 修改 PRD；
- 自行新增业务需求；
- 将 V2/V3/OUT_OF_SCOPE 混入 V1；
- 自行新增业务角色；
- 自行新增审批节点；
- 自行改变状态迁移；
- 自行设置 PRD 未确认的业务阈值；
- 将外部系统 Owner 数据变成本平台 Owner；
- 以通知成功代替业务成功；
- 为实现方便降低权限、门禁或审计要求；
- 进入大规模业务编码。

遇到业务缺口时：

- 标记 `BLOCKED_BY_SPEC`
- 记录到 `docs/decisions/open-questions.md`

## 四、SDS Phase 1

本轮只生成：

- `docs/design/01-requirement-traceability.md`
- `docs/design/02-domain-model.md`
- `docs/design/03-system-architecture.md`
- `docs/design/04-module-design.md`
- `docs/design/05-state-machine.md`
- `docs/design/06-workflow-design.md`
- `docs/design/07-authorization-design.md`

### 1. Requirement Traceability

至少建立：

Requirement ID
-> Business Domain
-> Module
-> Aggregate
-> State Machine / Workflow
-> Permission Model
-> Planned API
-> Planned Data Object
-> Test Category

不得漏掉PRD V1.6的115项V1/V2正式需求；V3和`OUT_OF_SCOPE`只建立边界追溯，不生成当前实现设计。

### 2. Domain Model

输出：

- bounded contexts
- context map
- aggregates
- aggregate roots
- entities
- value objects
- domain services
- application services
- commands
- queries
- domain events
- invariants
- cross-context contracts

明确：

- 谁拥有数据
- 谁只能引用数据
- 哪些对象不能直接跨模块修改

### 3. System Architecture

输出：

- system context
- container view
- component view
- module dependencies
- runtime view
- deployment assumptions
- internal event strategy
- external adapter strategy

优先评估“模块化单体 + 领域模块 + 内部事件”的适用性，但必须以 PRD 业务关系为依据，而不是为了套用模板。

### 4. Module Design

每个模块必须明确：

- responsibility
- public application services
- owned aggregates
- owned repositories
- incoming dependencies
- outgoing dependencies
- domain events
- integration events
- forbidden dependencies

禁止跨 bounded context 直接操作 Repository。

### 5. State Machine

对所有核心生命周期对象识别：

- states
- transitions
- guards
- actions
- allowed roles
- preconditions
- postconditions
- emitted events
- audit fields
- state-machine versioning

不要只列状态名称。

### 6. Workflow

审批和流程定义至少包含：

- trigger
- applicant
- nodes
- node owner
- routing rule
- pass rule
- reject rule
- withdraw rule
- retry rule
- timeout rule
- idempotency
- state mapping
- audit requirements

Workflow 与 Domain State Machine 分开描述。

### 7. Authorization

建立：

Subject + Role + Resource + Action + Scope + Condition

必须覆盖：

- function permission
- data permission
- action permission
- field permission
- temporary grant
- project-tree scope
- organization scope
- sensitive file access
- device credential grant

必须给出 server-side enforcement 方案和负向测试矩阵。

## 五、Phase 1 Review

完成后不要进入 Phase 2，除非`phase-1-review.md`明确标记`APPROVED`且无`BLOCKED_BY_SPEC`或`BLOCKED_BY_EVIDENCE`。

创建：

`docs/design/phase-1-review.md`

检查：

1. 正式需求是否全部进入追溯矩阵；
2. V1/V2/V3/OUT_OF_SCOPE 是否混淆；
3. bounded context 是否存在职责重叠；
4. aggregate 是否过大；
5. 是否存在跨模块直接 Repository 访问；
6. 核心生命周期是否都有受控状态机；
7. workflow 与状态机是否混在一起；
8. 权限是否覆盖功能/数据/操作/字段/临时授权；
9. 外部系统 Owner 是否正确；
10. 是否发明了 PRD 中不存在的业务规则；
11. 是否存在 `BLOCKED_BY_SPEC`；
12. 是否足以进入 Data/API/Integration 详细设计。

最后输出：

- files created/changed
- decisions
- requirement coverage
- blockers
- risks
- recommendation for Phase 2

到此停止。
