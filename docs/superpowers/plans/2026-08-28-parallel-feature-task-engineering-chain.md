# 并行 Feature 与 Task 工程链调整实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将正式工程链调整为“多个 Feature 并行、同一 Feature 多 Task 并行、共享资源串行收口、Feature 单一 Done、Requirement 按版本切片派生覆盖”的可执行规则。

**Architecture:** `Requirement ID + 目标版本切片`覆盖映射只表达 Capability、Feature、依赖和 Owner 关系；每个 Feature 使用一个当前有效 Technical Plan，在独立分支或 Worktree 中并行执行排他认领的 Task。Task 先在 Feature 内集成，随后由公共契约、Flyway 和共享文件的全局串行合入收口，最终只产生一条 Feature Implementation Done；Deployment、SIT、UAT 和 Release 按发布候选范围继续推进。

**Tech Stack:** Markdown、Python 3、`unittest`、现有 Requirement 追溯生成器

**Spec:** `docs/engineering/00-engineering-chain.md`（当前正式基线）及当前任务中已批准的两级并行方案

## Global Constraints

- PRD V1.8业务语义、Requirement ID、目标版本、领域Owner、权限和状态机均不改变。
- Capability只作无状态覆盖分组，不新增Ready、Done、Gate或证据副本。
- Feature是唯一实施与Implementation Done单元；Task完成不得直接关闭Feature或Requirement。
- 公共契约、Flyway最终编号和共享文件只在合入阶段串行收口，不建立版本预约台账或新Gate。
- Requirement实施覆盖按`Requirement ID + 目标版本切片`派生；Deployment、SIT、UAT和Release状态不回写为Feature Done。
- 本次只修正已有证据明确证明错误的PM-04、PM-08、PRE-04及同一组合边界下的SOL-01口径，不推测未定义Feature ID。
- 用户未请求Git提交，本计划不执行`git commit`。

---

### Task 1: 更新正式工程链

**Files:**
- Modify: `docs/engineering/00-engineering-chain.md:51-120`
- Modify: `docs/engineering/00-engineering-chain.md:216-287`
- Modify: `docs/engineering/00-engineering-chain.md:317-348`

**Interfaces:**
- Consumes: PRD/SDS权威顺序、现有Feature Ready和Implementation Done门禁、现有部署与测试设计。
- Produces: 覆盖映射规则、Feature级并行模型、Task认领协议、共享资源串行合入规则、Feature/Requirement/Release状态边界和存量处理规则。

- [x] **Step 1: 调整总工程链**

  将主链改为：

  ```text
  PRD / SDS Baseline
  -> Requirement ID + 目标版本切片覆盖映射
  -> 多个纵向业务Feature并行
  -> 每个Feature一个当前有效Technical Plan
  -> Feature内多个Task独立Worktree并行
  -> Feature内集成
  -> 公共契约/Flyway/共享文件串行合入
  -> Feature适用验证
  -> 单一Feature Implementation Done
  -> Release Candidate -> Deployment -> SIT -> UAT -> Release
  ```

- [x] **Step 2: 写入唯一状态源和Requirement派生规则**

  明确Capability无状态；Feature Spec拥有Feature Ready；实施任务记录拥有Implementation Done；Task认领只存在于单一执行记录；索引、矩阵、CI和浏览器证据不是独立状态源。

- [x] **Step 3: 写入两级并行执行协议**

  明确一个Feature协调责任、多个Feature参与者、一个Task一个主要执行者、每个并行Task独立分支/Worktree、Task交付契约、Feature内集成和全局串行合入。

- [x] **Step 4: 扩充Implementation Done**

  在现有DoD内加入最终Flyway编号、空库`migrate/info/validate`、最近批准基线升级、重复`migrate`、公共契约测试，以及合入最终状态后的Feature适用验证。

- [x] **Step 5: 定点检查正式文档**

  Run:

  ```powershell
  rg -n "目标版本切片|Capability|Technical Plan|Worktree|Flyway最终|RequirementImplementationCoverage|Release Candidate" docs/engineering/00-engineering-chain.md
  ```

  Expected: 每个核心规则均能定位，且不存在Capability Ready/Done或新Gate定义。

### Task 2: 统一Feature索引和Requirement覆盖口径

**Files:**
- Modify: `specs/features/README.md`
- Modify: `scripts/generate_requirement_traceability.py`
- Modify: `docs/traceability/requirement-matrix.md`（由生成器生成）

**Interfaces:**
- Consumes: Feature Spec的Feature Ready、实施任务记录中的实现事实、已确认的Requirement覆盖缺口。
- Produces: Feature索引的非权威投影声明，以及PM-04、PM-08、PRE-04不再误闭合的Requirement实施覆盖视图。

- [x] **Step 1: 明确Feature索引字段语义**

  说明规格状态、Feature Ready和实施状态是不同维度；索引是投影视图，浏览器和CI只提供证据，不能声明状态。

- [x] **Step 2: 修正生成器中的已知错误口径**

  将PM-04改为部分覆盖；将PM-08按版本切片分别登记V1完成、V2未开始；将PRE-04改为共享基础已完成但F-SOL-003和SCH-01义务未完成的部分覆盖；同一组合边界下的SOL-01保持部分覆盖。

- [x] **Step 3: 重新生成追溯矩阵**

  Run:

  ```powershell
  py -3 -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md
  ```

  Expected: 生成成功，PM-04、PRE-04和SOL-01显示`IMPLEMENTATION_PARTIAL`；PM-08分别显示V1`IMPLEMENTATION_COMPLETE`和V2`NOT_STARTED`，不再整体误闭合。

### Task 3: 增加回归校验并完成自审

**Files:**
- Modify: `scripts/tests/test_generate_requirement_traceability.py`
- Test: `scripts/tests/test_generate_requirement_traceability.py`

**Interfaces:**
- Consumes: Task 2的生成器状态文本。
- Produces: 防止三项Requirement再次误闭合的自动化回归断言。

- [x] **Step 1: 增加状态口径测试**

  断言生成内容包含：

  ```text
  PM-04 -> IMPLEMENTATION_PARTIAL
  PM-08 V1 -> IMPLEMENTATION_COMPLETE
  PM-08 V2 -> NOT_STARTED
  PRE-04 -> IMPLEMENTATION_PARTIAL
  ```

- [x] **Step 2: 运行聚焦测试**

  Run:

  ```powershell
  py -3 -B -m unittest scripts.tests.test_generate_requirement_traceability
  ```

  Expected: `OK`。

- [x] **Step 3: 运行只读漂移检查**

  Run:

  ```powershell
  py -3 -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --check
  ```

  Expected: `[PASS] requirement traceability is current`。

- [x] **Step 4: 检查差异质量和范围**

  Run:

  ```powershell
  git diff --check
  git status --short
  git diff -- docs/engineering/00-engineering-chain.md specs/features/README.md scripts/generate_requirement_traceability.py scripts/tests/test_generate_requirement_traceability.py docs/traceability/requirement-matrix.md
  ```

  Expected: 无空白错误；差异仅包含本计划所列工程链、投影语义、已知覆盖口径和测试。
