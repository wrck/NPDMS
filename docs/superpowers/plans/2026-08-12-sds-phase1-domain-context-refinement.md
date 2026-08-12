# SDS Phase 1 Domain Context Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的 Phase 1 Bounded Context、数据 Owner、聚合边界和跨域契约回写到活动 SDS 与需求追溯资料，同时保持 PRD V1.6、13 个领域编码、115 项正式需求和 Phase 1 门禁状态不变。

**Architecture:** 保留13个领域作为需求 Owner 和规格分册边界；在领域内部使用更细的 Bounded Context。`Device Access & Collection` 作为正式 Context，现有采集模块或子应用作为实现载体；基础平台通用能力单独描述，不作为万能业务 Context。外部主数据以本地同步副本支撑查询，外部系统继续作为权威来源。

**Tech Stack:** Markdown SDS、Python 3.13 追溯生成器、仓库现有 PRD 语义与领域生成校验脚本、Git。

## Global Constraints

- PRD V1.6 是业务语义最高基线；不得新增未经确认的正式需求。
- 13 个领域编码和 115 项 V1/V2 正式需求保持不变。
- `Field = 字段` 的技术语义保留；当前业务上下文统一使用 `Implementation Execution`。
- 外部系统权威字段只读；同步副本必须保留来源系统、来源主键、来源版本、同步时间和同步状态。
- 日常查询优先使用本地同步副本；外部不可用时展示最近成功版本和截止时间。
- `Device Access & Collection` 不重复建设外部采集平台的设备连接、命令执行和原始采集引擎。
- 三个 Phase 1 硬门禁仍保持未关闭，不得将状态改为 `APPROVED` 或进入 Phase 2。
- 历史独立评审原文不得被重写；本轮只更新活动设计和追溯工作稿。

---

### Task 1: 固化 Context 与 Owner 总表

**Files:** `docs/design/02-domain-model.md`, `docs/design/02a-context-map.md`, `docs/design/phase-1-domain-ownership.md`

- [ ] 在领域模型中增加 `Device Access & Collection`、`Work Order & Time`、`Inspection`、`Service Operations`、`Customer & Relationship`、`Asset Management` Context。
- [ ] 明确一个领域可包含多个 Context；SRV 映射为 WO-01～WO-06→`Work Order & Time`、INS-01～INS-09→`Inspection`、SRV-01→`Service Operations`。
- [ ] 将 INT-12 的需求 Owner 保持为 PLT，Context 映射为 `Device Access & Collection`。
- [ ] Context Map 只使用 Context 或外部系统作为节点，不把 `CollectionTask`、`DeliveryEvidence` 画成 Context。
- [ ] 运行领域生成校验，确认 13 个领域、115 条正式需求、V3=22、OUT_OF_SCOPE=9。

### Task 2: 回写采集、主数据和平台能力边界

**Files:** `docs/design/02c-data-ownership-matrix.md`, `docs/design/02d-cross-context-contracts.md`, `docs/design/02e-version-scope-matrix.md`, `docs/design/03-system-architecture.md`

- [ ] 将 `DeviceCredential`、`CredentialGrant`、`CollectionTask`、外部状态原值、回调证据和授权快照归入 `Device Access & Collection`；外部采集平台继续负责原始执行。
- [ ] 为 CRM、MES、ITR、ERP 增加本地同步副本、来源版本、同步时间和同步状态；明确外部系统仍为权威来源。
- [ ] 明确日常查询读取本地副本，外部不可用时展示最近成功版本和截止时间。
- [ ] 补充 `CollectionTaskRequested`、`CollectionResultAvailable`、主数据同步、`ProjectClosureCompleted` 契约的版本、幂等、重试和追溯字段。
- [ ] 在系统架构中说明现有采集平台按子应用或模块纳入该 Context，不重复建设连接、命令执行和原始采集引擎。

### Task 3: 收敛聚合、闭环和实施执行设计

**Files:** `docs/design/02b-aggregate-boundary-decisions.md`, `docs/design/04-module-design.md`, `docs/design/05-state-machine.md`, `docs/design/06-workflow-design.md`, `docs/design/07-authorization-design.md`

- [ ] 将 `Closure` 统一为 `ProjectClosure`，由 `Acceptance & Closure` 持有；项目主状态仍由 `Project Delivery` 修改。
- [ ] 保留 `DeliveryEvidence` 独立聚合，维护版本、替换、作废、授权和审计生命周期；业务聚合只保存 `EvidenceReference`。
- [ ] 为 `Device Access & Collection` 补齐凭证、授权、采集任务状态机和回调幂等规则，禁止明文凭据持久化和日志泄露。
- [ ] 在模块和追溯工作映射中将 WO、INS、SRV-01 分别标记为三个 SRV 内部 Context，不改变领域编码和需求编号。
- [ ] 明确 INT-12 编排、凭证和任务下发为 V1；IMP-01/02、INS 和服务运营增强能力按 PRD 保持 V2。

### Task 4: 更新需求追溯映射

**Files:** `docs/design/01-requirement-traceability.md`, `scripts/generate_requirement_traceability.py`, `docs/traceability/requirement-matrix.md`

- [ ] 在生成器中增加 SRV、CUS、AST、COM、PLT 的精确 Context 映射，覆盖 WO、INS、SRV-01、INT-12、CUS、EQP、COM 需求。
- [ ] 保留 EXE-01～06、IMP-01～02 当前精确聚合映射，不回退为笼统全量实施聚合。
- [ ] 重生成矩阵并确认 115 条唯一正式需求、V1=57、V2=58、13 个 Owner 不变。

### Task 5: 综合校验与 Phase 1 复审记录

**Files:** `docs/engineering/gates/phase-1/gate-status.md`, `docs/engineering/gates/phase-1/context-refinement-review.md`

- [ ] 运行命名门禁和差异检查：

```powershell
py -3.13 -B scripts/check_business_naming.py
git diff --check
```

- [ ] 运行 PRD、领域生成和语义测试：

```powershell
py -3.13 -B scripts/validate_prd_semantics.py --prd '需求/PRD-项目实施交付管理平台.md'
py -3.13 -B scripts/validate_prd_domain_generation.py --prd '需求/PRD-项目实施交付管理平台.md' --domains 'specs/001-project-delivery-platform/domains'
py -3.13 -B scripts/tests/test_validate_prd_semantics.py
```

- [ ] 复核 `BLOCKED-SDS-01`、`BLOCKED-SDS-02`、`BLOCKED-SDS-03`，不得因文档整改自动关闭；Phase 1 保持 `NOT_READY_FOR_PHASE_2`。
- [ ] 形成 Context 整改复审记录，记录 Context 结构、同步副本、聚合边界、追溯统计、校验命令和未关闭门禁。

## 变更顺序

1. Context 与 Owner
2. 采集、主数据和平台能力边界
3. 聚合、状态机、工作流和权限
4. 追溯生成与矩阵
5. 综合校验与复审记录

每个任务完成后进行定点校验；全部任务完成后再进行一次综合校验，不修改 PRD V1.6 的业务语义。
