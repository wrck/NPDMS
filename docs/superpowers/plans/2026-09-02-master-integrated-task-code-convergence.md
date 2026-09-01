# master已集成任务代码收口执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 逐项证明任务追溯矩阵中全部“已集成”Task/Delivery Unit的实际代码已进入`master`，并把唯一仍缺少真实分支拓扑回执的F-CUT-001隔离代码分支合入主干。

**Architecture:** 以`master`中的Feature Task、Delivery Unit回执和Git对象为唯一实施事实，按“来源提交 -> master代码回执 -> 当前代码路径”核对。隔离且补丁等价的F-CUT-001代码分支使用标准双亲merge收口；继承未批准Feature历史的PROJ/CUT多Feature分支只承认已在master上的选择性代码回执，禁止整支合并。

**Tech Stack:** Git、PowerShell、Python 3、Maven/JDK 25、仓库Delivery Unit与分支时间线校验器。

**Spec:** `tasks/features/README.md`

## Global Constraints

- `master`是唯一集成分支，允许承载可构建的`INTEGRATED_PARTIAL`增量。
- Feature Ready、Implementation Status和Delivery Unit回执分别以Feature Spec、Feature Task和`DU-*.md`为唯一权威。
- 多Feature分支只允许按DU选择提交范围，不得整支合入。
- 已由新能力替代的旧入口继续保持`DEPRECATED_READ_ONLY`或`RUNTIME_REMOVED`，不得恢复为新实施基础。
- 本轮不实现F-PROJ-008 Task 3、F-CUT-001 V133示例迁移、任何CUT/COM/IMP消费Feature或缺失Provider。

---

### Task 1: 冻结已集成代码回执清单

**Files:**
- Create: `tasks/delivery-units/DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE.md`
- Modify: `tasks/delivery-units/README.md`

**Interfaces:**
- Consumes: `tasks/features/README.md`中的已集成Task投影和现有`INTEGRATED_PARTIAL|INTEGRATED_COMPLETE` DU。
- Produces: 本轮排他治理认领以及需要核验的master代码回执集合。

- [x] **Step 1: 登记CLAIMED Delivery Unit**

  在`master@ee5dd5f3c88afd88cd840e53b448820de9c18d13`上登记本轮DU，修改边界仅覆盖Git合并拓扑、执行计划、DU、Feature矩阵和新的分支时间线报告。

- [x] **Step 2: 生成并校验DU索引**

  Run: `python scripts/validate_delivery_units.py --write-index`

  Expected: `tasks/delivery-units/README.md`包含本轮`CLAIMED` DU，校验退出码为0。

- [x] **Step 3: 提交认领**

  Run: `git add docs/superpowers/plans/2026-09-02-master-integrated-task-code-convergence.md tasks/delivery-units/DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE.md tasks/delivery-units/README.md && git commit -m "chore(governance): claim integrated code convergence"`

  Expected: 新提交成为本DU的`SELF`认领提交，后续修改均位于声明边界。

### Task 2: 逐项核验代码已存在于master

**Files:**
- Modify: `tasks/delivery-units/DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE.md`

**Interfaces:**
- Consumes: master代码回执`5f5148a9`、`f1cf7920`、`c61e5b1e`、`db876b43`、`158118d0`、`2bdbb04c`及合同回执`e2f51762`。
- Produces: 每个已集成单元的代码存在性、来源补丁关系和整支合并裁决。

- [x] **Step 1: 核验master祖先关系**

  Run: `@('5f5148a9','f1cf7920','e2f51762','c61e5b1e','db876b43','158118d0','2bdbb04c') | ForEach-Object { git merge-base --is-ancestor $_ master; if ($LASTEXITCODE -ne 0) { throw "missing master receipt $_" } }`

  Expected: 七个回执全部为`master`祖先。

- [x] **Step 2: 核验当前代码树没有删除回执代码路径**

  对每个回执执行`git diff-tree --no-commit-id --name-status -r <commit>`，筛选Java、XML、TypeScript、Vue、SQL、Python与机器合同JSON；所有生产/测试/迁移路径在当前工作树必须存在。合同专用`e2f51762`明确记录为`CONTRACT_ONLY`，不得虚构Java Provider已经完成。

- [x] **Step 3: 核验来源到master的选择性适配**

  Run: `git range-diff --no-color 0c7a9634^! db876b43^!`

  Run: `git range-diff --no-color d69b3ff8^! 158118d0^!`

  Expected: F-PROJ-008 Task 1/2的文件集合进入master，master回执保留或增强失败关闭、REST前缀、幂等和聚焦测试；不得把`a3bd0043` Task 3或该分支继承的其他Feature历史带入。

### Task 3: 合入F-CUT-001隔离代码分支

**Files:**
- Modify: Git merge topology only
- Preserve: `docs/decisions/open-questions.md`
- Preserve: `specs/features/README.md`
- Preserve: `tasks/features/F-CUT-001.md`
- Preserve: `tasks/features/README.md`

**Interfaces:**
- Consumes: `codex/f-cut-001-master-integration@07b6eb063ab9a54fe419930c8417581eeb983f05`，其稳定patch-id与`master@c61e5b1efceae13f091f2191184a6301ad32061e`一致。
- Produces: 一个标准双亲merge，使隔离F-CUT-001代码分支成为`master`祖先，同时不回退master后续治理事实。

- [x] **Step 1: 执行标准非快进合并但暂不提交**

  Run: `git merge --no-ff --no-commit codex/f-cut-001-master-integration`

  Expected: 代码文件自动合并；仅四个后续已演进的治理文件发生冲突。

- [x] **Step 2: 对四个治理冲突保留master权威版本**

  Run: `git checkout --ours -- docs/decisions/open-questions.md specs/features/README.md tasks/features/F-CUT-001.md tasks/features/README.md`

  Run: `git add docs/decisions/open-questions.md specs/features/README.md tasks/features/F-CUT-001.md tasks/features/README.md`

  Expected: `git diff --name-only --diff-filter=U`无输出；`git diff --cached --stat`无文件内容差异。

- [x] **Step 3: 提交真实双亲merge**

  Run: `git commit -m "merge(cutover): 合入 F-CUT-001 代码分支"`

  Expected: merge提交第二父为`07b6eb06`，merge tree与第一父tree一致，且`git merge-base --is-ancestor 07b6eb06 master`返回0。

### Task 4: 更新权威矩阵与全分支时间线

**Files:**
- Modify: `tasks/delivery-units/DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE.md`
- Modify: `tasks/delivery-units/README.md`
- Modify: `tasks/features/README.md`
- Create: `docs/generated/branch-history-audit-2026-09-02-integrated-code-convergence.md`

**Interfaces:**
- Consumes: Task 2代码回执核验和Task 3 merge提交。
- Produces: `master`当前唯一的已集成代码收口结论和新的固定时间线快照。

- [x] **Step 1: 生成新时间线报告**

  Run: `python scripts/generate_branch_history_audit.py --repository . --master-ref master --master-input (git rev-parse master) --snapshot-at (Get-Date -Format o) --output docs/generated/branch-history-audit-2026-09-02-integrated-code-convergence.md`

  Expected: 报告覆盖全部本地分支、Worktree、stash和master外提交；旧报告不被覆盖。

- [x] **Step 2: 回写代码收口矩阵**

  在Feature矩阵记录：F-CUT-001隔离代码分支已真实双亲合入；F-PROJ-008 Task 1/2由master适配回执承载，源多Feature分支继续禁止整支合并；F-SOL-003已真实合入；PROJ Owner carve-out按“生产代码/公共接口/合同专用”三类明确成熟度。

- [x] **Step 3: 关闭DU并重建索引**

  将本轮DU改为`INTEGRATED_COMPLETE`，写入merge提交、全部代码回执、未合入边界和验证结果；执行`python scripts/validate_delivery_units.py --write-index`。

### Task 5: 构建、校验与最终提交

**Files:**
- Test: `tasks/delivery-units/DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE.md`
- Test: `tasks/features/README.md`
- Test: `docs/generated/branch-history-audit-2026-09-02-integrated-code-convergence.md`

**Interfaces:**
- Consumes: 最终master树与治理回执。
- Produces: 可复核的构建、治理和Git拓扑证据。

- [x] **Step 1: 验证受影响模块可编译**

  Run: `mvn -pl pms-module-cutover,pms-module-project,pms-module-integration -am -DskipTests compile`

  Expected: `BUILD SUCCESS`。

- [x] **Step 2: 验证工程链与追溯投影**

  Run: `python -m unittest discover -s scripts/tests -p "test_*.py"`

  Actual: `576`项中`572`项通过、`4`项既有失败；失败涉及既有Flyway摘要、Phase 2测试夹具和PM-03/SCH-05 Phase 3 PRD摘录，本轮代码merge tree与第一父tree相同且未修改其输入，不归因于本DU。

  Run: `python -B -m unittest scripts/tests/test_validate_delivery_units.py scripts/tests/test_generate_branch_history_audit.py scripts/tests/test_generate_requirement_traceability.py scripts/tests/test_implementation_baseline_inventory.py scripts/tests/test_fsol003_dynamic_form_amendment.py`

  Expected: 本轮相关`59`项聚焦测试全部通过。

  Run: `python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check`

  Run: `python scripts/validate_delivery_units.py --base-ref master`

  Run: `python scripts/generate_branch_history_audit.py --repository . --master-ref master --master-input (git rev-parse master) --snapshot-at (Get-Date -Format o) --output docs/generated/branch-history-audit-2026-09-02-integrated-code-convergence.md --check`

  Expected: 聚焦测试、Requirement追溯、Delivery Unit和固定截点分支时间线校验退出码为0，生成投影无漂移；全量测试的既有失败单独披露，不伪记为通过。

- [x] **Step 3: Code Review并提交收口记录**

  Run: `git diff --check`

  Run: `git status --short`

  确认最终差异只包含本轮计划、DU/矩阵和新时间线报告后，提交`docs(governance): close integrated code convergence`。不推送远端。
