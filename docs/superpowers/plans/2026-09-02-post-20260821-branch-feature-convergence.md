# 2026-08-21起非排除分支Feature收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 审查2026-08-21当日及之后除`codex/f-cut-001-matrices`、`feat-inspection-feature-xkjuCC`外全部本地分支的Feature、Task与代码差量，只把权威Gate允许且master尚未等价接收的增量合入主干。

**Architecture:** 以`master`为唯一状态和集成分支，以固定时间线、Delivery Unit、Feature Spec/Task和补丁/树差异共同裁决。分支提交只作候选证据；`BLOCKED_BY_SPEC`、无Task/无认领、被后续实现替代或已补丁等价接收的内容均不重复合并。

**Tech Stack:** Git、PowerShell、Python治理生成器与校验器、Maven（仅在实际接收Java增量时运行受影响模块验证）。

**Spec:** `docs/engineering/00-engineering-chain.md`

## Global Constraints

- 时间下界固定为`2026-08-21T00:00:00+08:00`，包含2026-08-21当日。
- 排除分支固定为`codex/f-cut-001-matrices`和`feat-inspection-feature-xkjuCC`；不得通过其他分支的继承关系绕过排除边界。
- `master`允许接收可构建但Feature未Done的增量，但不得绕过Feature Ready、当前Task、Open Question、DU认领和物理Owner边界。
- 已替代、补丁等价、树等价或已由master适配回执承载的提交不得制造重复代码或重复合并。
- 不修改两个排除Worktree及其他分支的脏改动、stash或未跟踪文件；不推送远端。

---

### Task 1: 建立本轮排他治理认领

**Files:**
- Create: `tasks/delivery-units/DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE.md`
- Modify: `tasks/delivery-units/README.md`

**Interfaces:**
- Consumes: `master@0d9dffbb41fcfc0a58645cb64b081c2c6b6c78bc`和当前DU矩阵。
- Produces: 仅覆盖本轮计划、DU、Feature矩阵和新时间线报告的有效master写入认领。

- [x] **Step 1: 登记CLAIMED DU**

  固定时间、排除分支、候选分支集合、写边界和验证方式，不认领任何候选Feature代码。

- [x] **Step 2: 重建并校验DU索引**

  Run: `python -B scripts/validate_delivery_units.py --write-index`

  Expected: 新DU出现在索引且校验退出码为0。

- [x] **Step 3: 提交认领**

  Run: `git add docs/superpowers/plans/2026-09-02-post-20260821-branch-feature-convergence.md tasks/delivery-units/DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE.md tasks/delivery-units/README.md`

  Run: `git commit -m "chore(governance): claim post-20260821 branch convergence"`

  Expected: 认领提交成为master第一父历史，其他工作树不发生变化。

### Task 2: 固定完整分支时间线与候选集合

**Files:**
- Create: `docs/generated/branch-history-audit-2026-09-02-post-20260821-feature-convergence.md`

**Interfaces:**
- Consumes: 全部本地分支、Worktree、stash和认领后的master提交。
- Produces: 固定截点的Git事实报告；报告本身不产生Feature状态。

- [x] **Step 1: 生成新报告**

  Run: `$auditMaster = git rev-parse master; $auditSnapshot = (Get-Date).ToString('o'); python -B scripts/generate_branch_history_audit.py --repository . --master-ref master --master-input $auditMaster --snapshot-at $auditSnapshot --output docs/generated/branch-history-audit-2026-09-02-post-20260821-feature-convergence.md`

  Expected: 报告覆盖全部分支、Worktree、stash和master外提交，且不覆盖历史报告。

- [x] **Step 2: 固定本轮非排除候选**

  对每个非排除分支执行`git rev-list --since=2026-08-21T00:00:00+08:00 branch --not master`，并以`git cherry`、`git diff`、Feature Spec/Task、DU和Open Question判定来源与成熟度。

- [x] **Step 3: 校验报告固定点**

  以生成时相同`master-input`和`snapshot-at`执行生成器`--check`。

  Actual: 报告生成时退出码为0；最终复核时完整`--check`检测到明确排除的`codex/f-cut-001-matrices`在截点后从`ff1dc761`前进到`e09b150a`，因此按设计返回stale。本轮19个纳入范围分支HEAD逐一锁定检查全部通过，去重候选集合仍为179条。

  Expected: 纳入范围分支或候选集合变化时重开本轮审计；只有排除分支在截点后前进时保留固定报告，不追赶并覆盖历史快照。

### Task 3: 逐分支Feature与代码裁决

**Files:**
- Modify: `tasks/features/README.md`
- Modify: `tasks/delivery-units/DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE.md`

**Interfaces:**
- Consumes: 固定时间线、主干Feature Task、相关Feature Spec、Open Question和代码差量。
- Produces: 每个分支`IN_MASTER / ALREADY_RECEIVED / SUPERSEDED / BLOCKED_BY_SPEC / QUARANTINED / MERGE_APPROVED`中的唯一裁决及理由。

- [x] **Step 1: 裁决已包含或等价分支**

  核验所有`IN_MASTER`分支、`codex/v1-8-feature-revalidation-50eb`、`codex/integrate-f-cut-001`及历史合并线，禁止重复合并。

- [x] **Step 2: 裁决PROJ分支**

  `codex/f-proj-001-atomic-alignment`对照master当前F-PROJ-001 Task 0～9和AC 1～10；`codex/f-proj-008-stage-advance`只承认Task 1/2的master适配回执，Task 3继续受`Q-FPROJ-009`阻断。

- [x] **Step 3: 裁决COM/ACC与INT分支**

  `codex/f-com-001-feature-ready`和`codex/f-acc-001-sds`受`Q-GOV-20260901-001/002`阻断；`prereq-parallel-check-kKiAdn`的F-INT-012无master Feature Task和有效认领。不得将分支自报Done或Feature Spec单独解释为可合入。

- [x] **Step 4: 裁决报告/审计分支**

  `prd-audit-v1-8-LAR2Ap`只作历史审计输入；不得把旧PRD报告覆盖当前修订001～007合并基线。

### Task 4: 执行获批增量合并或记录零合并结论

**Files:**
- Modify: `tasks/features/README.md`
- Modify: `tasks/delivery-units/DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE.md`

**Interfaces:**
- Consumes: Task 3的逐分支裁决。
- Produces: 仅包含获批增量的master代码；若没有`MERGE_APPROVED`项，则形成“零新增代码合并”的权威结论而不制造空merge。

- [x] **Step 1: 检查MERGE_APPROVED集合**

  只有同时满足正式规格、当前Task、有效DU、物理Owner、非排除继承和master缺失差量的提交才能进入集合。

- [x] **Step 2: 选择性合并**

  当前审计若保持零`MERGE_APPROVED`，不得执行`git merge`或`git cherry-pick`；若裁决出现获批项，先把Feature、提交、文件和验证边界补入本DU，再逐Feature提交。

- [x] **Step 3: 关闭DU**

  将实际接收回执或零合并原因、未接收提交及其最近上游Gate写回DU，状态改为`INTEGRATED_COMPLETE`并重建索引。

### Task 5: 验证、审查与提交

**Files:**
- Test: `tasks/features/README.md`
- Test: `tasks/delivery-units/DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE.md`
- Test: `docs/generated/branch-history-audit-2026-09-02-post-20260821-feature-convergence.md`

**Interfaces:**
- Consumes: 最终master治理树。
- Produces: 可复核的任务矩阵、时间线和本地提交。

- [x] **Step 1: 校验治理投影**

  Run: `python -B scripts/validate_delivery_units.py --base-ref master`

  Run: `python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check`

  Expected: 两项退出码均为0。

- [x] **Step 2: 运行相关治理测试**

  Run: `python -B -m unittest scripts/tests/test_validate_delivery_units.py scripts/tests/test_generate_branch_history_audit.py scripts/tests/test_generate_requirement_traceability.py scripts/tests/test_implementation_baseline_inventory.py`

  Expected: 相关测试全部通过；本轮无代码接收时不运行不会改变裁决的业务模块测试。

- [x] **Step 3: Code Review并提交**

  Run: `git diff --check`

  Run: `git status --short`

  只暂存本计划、DU、DU索引、Feature矩阵和新时间线报告，提交`docs(governance): converge post-20260821 branch features`。不推送远端。
