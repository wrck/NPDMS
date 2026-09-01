# PRE/SOL 权威选择性集成执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 以 `master` 为唯一权威集成分支，全面审查所有本地分支、提交和工作树中的 PRE/SOL 物理 Owner 变更，只接收当前主干真实缺失且与正式 Feature/Task 一致的可构建增量，并把无须合入、补丁等价和禁止合入的结论回写权威矩阵。

**Architecture:** Feature Ready 继续由 `specs/features/F-SOL-*.md` 维护，Implementation 状态继续由 `tasks/features/F-SOL-*.md` 维护。本单元只维护 Git/Worktree 候选的集成事实，不反向修改 Feature Done。`F-SOL-001/PRE-01`、`F-SOL-002/PRE-02`、`F-SOL-003/PRE-04` 分别按既有 Owner 事实审查；`PRE-03`、`PRE-05` 和 `SOL-01` 不因相邻实现或平台基础切片而推导完成。

**Tech Stack:** Git/Worktree、Python 工程链校验器、Maven/JUnit、Vitest、Markdown/JSON 权威投影。

---

### Task 1: 建立本轮权威集成认领

**Files:**
- Create: `tasks/delivery-units/DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION.md`
- Create: `docs/superpowers/plans/2026-09-01-pre-sol-authoritative-integration.md`
- Modify: `tasks/delivery-units/README.md`

**Step 1: 固定基线和修改边界**

在 `master@f1c2b74a92aff44753f49ead7426b79c3e8a9018` 建立治理 DU，只认领计划、DU、Feature 矩阵和本轮审计报告；不预先认领 PRE/SOL 源码。

**Step 2: 生成并校验 DU 索引**

Run: `python scripts/validate_delivery_units.py --write-index --check-index`
Expected: `SUMMARY PASS`，且不存在活动写边界冲突。

**Step 3: 提交认领**

Run: `git add docs/superpowers/plans/2026-09-01-pre-sol-authoritative-integration.md tasks/delivery-units/DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION.md tasks/delivery-units/README.md && git commit -m "chore(governance): claim pre sol integration audit"`
Expected: 仅提交认领和计划文件。

### Task 2: 全时间线裁决 PRE/SOL 候选

**Files:**
- Read: all local refs/worktrees and commits outside `master`
- Read: `specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`
- Read: `specs/features/F-SOL-002-site-survey-assignment-and-readiness.md`
- Read: `specs/features/F-SOL-003-requirement-analysis-versioning.md`
- Read: `tasks/features/F-SOL-001.md`
- Read: `tasks/features/F-SOL-002.md`
- Read: `tasks/features/F-SOL-003.md`
- Read: `tasks/implementation-baseline-inventory.json`

**Step 1: 枚举全部分支外提交和工作树脏项**

按提交时间、路径、Requirement/Feature 引用和物理 Owner 枚举所有 `master` 外 PRE/SOL 候选；同时检查所有 Worktree 未提交变更和 stash，不按分支名推断归属。

**Step 2: 逐项判定**

每个候选只允许归入 `MISSING_VALID_DELTA`、`ALREADY_IN_MASTER`、`PATCH_EQUIVALENT`、`STALE_COPY`、`CROSS_FEATURE_NOT_APPLICABLE` 或 `SUPERSEDED`。若发现 `MISSING_VALID_DELTA`，先更新 DU 源码写边界，再选择性移植并验证；否则不得制造空的代码合并。

**Step 3: 核对未覆盖 Requirement**

确认 `PRE-03`、`PRE-05`、`SOL-01` 是否存在正式 Feature/Task 与实现候选；没有权威链路时保持 `NOT_STARTED/PARTIAL`，不得推导 Done。

### Task 3: 验证 master 当前实现和废弃边界

**Files:**
- Test: `pms-module-engineering/src/test/**`
- Test: `scripts/tests/test_fsol003_dynamic_form_amendment.py`
- Test: `scripts/tests/test_implementation_baseline_inventory.py`
- Test: relevant PRE/SOL frontend specs

**Step 1: 运行工程链和废弃守卫**

Run: `python -m unittest scripts.tests.test_fsol003_dynamic_form_amendment scripts.tests.test_implementation_baseline_inventory scripts.tests.test_validate_delivery_units scripts.tests.test_generate_branch_history_audit`
Expected: all tests pass；固定章节候选不得重新成为新实施入口。

**Step 2: 运行 PRE/SOL 聚焦构建或测试**

先枚举当前测试类，再运行能够检测 PRE/SOL 合同、版本、就绪和旧入口回归的最小 Maven/Vitest 集合。若环境型测试缺少明确运行条件，记录为未重跑，不把历史证据冒充本轮结果。

### Task 4: 回写权威矩阵和审计报告

**Files:**
- Modify: `tasks/features/README.md`
- Modify: `tasks/delivery-units/DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION.md`
- Modify: `tasks/delivery-units/README.md`
- Create: `docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md`

**Step 1: 记录候选裁决**

在 Feature 矩阵中冻结 PRE/SOL 本轮输入、候选提交/工作树裁决及 Requirement 覆盖边界；保持三个 Feature Task 的既有权威状态不变。

**Step 2: 关闭 DU**

若没有缺失代码，DU 以 `INTEGRATED_COMPLETE` 记录“有效 PRE/SOL 变更已在 master、无代码移植”，并明确该状态只表示本轮集成审计完成；若确有可构建代码增量，则按实际范围记录源提交和 master 回执。

**Step 3: 生成全分支时间线**

Run: `python scripts/generate_branch_history_audit.py --output docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md`
Expected: 覆盖全部本地分支、Worktree、master 外提交和 stash；报告中的 DU/分支状态与矩阵一致。

### Task 5: 自审、验证和提交

**Files:**
- Review: all files changed from the claim commit

**Step 1: 运行最终校验**

Run: `python scripts/validate_delivery_units.py --write-index --check-index`
Run: `python scripts/generate_branch_history_audit.py --check --output docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md`
Expected: all checks pass。

**Step 2: 审查差异**

Run: `git diff --check` and `git diff --stat <claim-commit>..HEAD`
Expected: 只有本 DU 边界内变更；没有 PRE/SOL 源码倒退、Feature 状态漂移或旧功能复活。

**Step 3: 提交最终结果**

Run: explicit `git add` for the DU, index, Feature matrix and generated audit, then `git commit` with a conventional governance message.
Expected: `master` clean；不 push。
