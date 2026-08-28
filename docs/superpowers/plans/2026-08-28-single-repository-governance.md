# 规格与代码同库治理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将正式规格仓提交 `02f6360735980c4bbd9947844917feb0d4b4aecf` 的历史和最新资产合入 NPDMS，并用同库事实源治理替代外部规格快照同步。

**Architecture:** 先以 `ours` 策略创建双父 Git 历史接点，保证两个仓库的祖先均可追溯而不自动混合顶层文件树；随后分别提交正式规格差异和治理切换。NPDMS 主仓最终直接承载规格、实现、测试和证据，历史文档保留旧术语，当前入口不再依赖 manifest、allowlist 或外部工作树。

**Tech Stack:** Git、Python 3.13 `unittest`、Markdown/JSON 规格资产、现有 NPDMS 规格生成与校验脚本

**Spec:** `docs/superpowers/specs/2026-08-28-single-repository-governance-design.md`

## Global Constraints

- 正式规格输入固定为 `02f6360735980c4bbd9947844917feb0d4b4aecf`，不得使用规格工作树未提交内容。
- 目标输入固定为本治理分支从 NPDMS `master` 提交 `bddf6dd6` 建立后的提交序列。
- 不修改业务代码、前端、Flyway、运行配置或运行数据。
- 不删除原规格仓工作树，不改写任一仓库既有历史，不推送远端。
- PRD > Engineering Constitution > SDS > Feature Spec > Technical Plan > Task > Code > Test / Runtime Evidence。
- Feature 是唯一 Implementation Done 单元；Capability 无状态；Requirement Coverage 按版本切片派生。
- 用户明确不要求 TDD；每个切片修改后运行对应现有测试和治理回归。

---

### Task 1: 建立可追溯的双仓历史接点

**Files:**
- Modify: Git object database and branch history only
- Verify: `git log` parent relationship

**Interfaces:**
- Consumes: clean NPDMS governance branch; clean formal specification commit `02f63607...`
- Produces: a merge commit whose first parent is the NPDMS design/plan tip and second parent is the formal specification commit

- [ ] **Step 1: 确认两端输入不漂移**

Run:

```powershell
git status --short --branch
git -C 'M:\AICoding\CodexData\worktrees\09b5\项目交付平台' status --short --branch
git -C 'M:\AICoding\CodexData\worktrees\09b5\项目交付平台' rev-parse HEAD
```

Expected: 两个工作树均无未提交内容，规格 HEAD 为完整提交 `02f6360735980c4bbd9947844917feb0d4b4aecf`。

- [ ] **Step 2: 抓取规格提交到临时本地引用**

Run:

```powershell
git fetch 'M:\AICoding\CodexData\worktrees\09b5\项目交付平台' 02f6360735980c4bbd9947844917feb0d4b4aecf:refs/heads/archive/specification-history-20260828
git rev-parse archive/specification-history-20260828
```

Expected: 输出 `02f6360735980c4bbd9947844917feb0d4b4aecf`。

- [ ] **Step 3: 使用 ours 策略建立历史接点**

Run:

```powershell
git merge --allow-unrelated-histories -s ours --no-ff --no-commit archive/specification-history-20260828
@'
merge(governance): 接入正式规格仓历史

- 保留规格提交 02f6360735980c4bbd9947844917feb0d4b4aecf 的完整祖先
- 保持 NPDMS 文件树不变，后续按权威边界导入正式资产
'@ | git commit -F -
git log -1 --format='%H%n%P%n%s'
git diff HEAD^1..HEAD --stat
```

Expected: merge 提交有两个父提交，第二父为 `02f63607...`，文件树相对第一父无变化。

- [ ] **Step 4: 删除临时分支名称但保留可达历史**

Run:

```powershell
git branch -D archive/specification-history-20260828
git merge-base --is-ancestor 02f6360735980c4bbd9947844917feb0d4b4aecf HEAD
```

Expected: exit code 0；规格历史仍通过 merge 父提交可达。

### Task 2: 合入正式规格最新差异

**Files:**
- Modify: `.gitattributes`
- Modify: `docs/baseline/README.md`
- Modify: `docs/baseline/change-log.md`
- Modify: `docs/engineering/gates/phase-2/independent-review.md`
- Modify: `docs/engineering/gates/phase-2/self-review.md`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Create: `docs/superpowers/plans/2026-08-27-f-plt-001-optional-security-scan.md`
- Create: `docs/superpowers/plans/2026-08-28-parallel-feature-task-engineering-chain.md`
- Modify: `specs/001-project-delivery-platform/README.md`
- Modify: `specs/001-project-delivery-platform/domains/PLT-平台公共能力需求规格.md`
- Modify: `scripts/generate_domain_entity_migration_contract.py`
- Modify: `scripts/generate_phase2_contract_map.py`
- Modify: `scripts/generate_requirement_traceability.py`
- Create: `scripts/tests/test_fplt001_feature_contract.py`
- Create: `scripts/tests/test_fplt002_feature_contract.py`
- Create: `scripts/tests/test_fsol001_feature_contract.py`
- Create: `scripts/tests/test_fsol002_feature_contract.py`
- Create: `scripts/tests/test_fsol003_dynamic_form_amendment.py`
- Modify: `scripts/tests/test_generate_requirement_traceability.py`
- Modify: `scripts/tests/test_validate_domain_entity_migration_alignment.py`
- Modify: `scripts/tests/test_validate_phase3_evidence_register.py`
- Modify: `scripts/tests/test_validate_sds_phase2.py`
- Modify: `scripts/validate_domain_entity_migration_alignment.py`
- Modify: `scripts/validate_phase3_evidence_register.py`

**Interfaces:**
- Consumes: the tree of formal specification commit `02f63607...`
- Produces: all non-cache formal changes that are absent or different in NPDMS, without replacing NPDMS-only files

- [ ] **Step 1: 生成正式差异清单并排除缓存**

Run:

```powershell
$code = (Get-Location).Path
$spec = 'M:\AICoding\CodexData\worktrees\09b5\项目交付平台'
foreach ($name in @('docs', 'specs', 'scripts')) {
  git diff --no-index --name-status --no-renames -- "$code\$name" "$spec\$name" 2>$null |
    Where-Object { $_ -match '^(A|M)' -and $_ -notmatch '__pycache__|\.pyc$' }
}
```

Expected: the source files listed in this task are the only formal files requiring import; `tasks/plan.md` and `tasks/todo.md` remain NPDMS historical versions.

- [ ] **Step 2: 将规格提交内容写入明确文件**

For each listed path, read the blob with:

```powershell
git show 02f6360735980c4bbd9947844917feb0d4b4aecf:<relative-path>
```

Use the formal blob for specification-owned content. For shared Python files, review the full diff first and preserve any NPDMS-only behavior that remains required by current tests.

- [ ] **Step 3: 验证生成器和契约测试**

Run:

```powershell
python -B -m unittest scripts.tests.test_generate_requirement_traceability
python -B -m unittest scripts.tests.test_fplt001_feature_contract scripts.tests.test_fplt002_feature_contract
python -B -m unittest scripts.tests.test_fsol001_feature_contract scripts.tests.test_fsol002_feature_contract scripts.tests.test_fsol003_dynamic_form_amendment
```

Expected: all tests PASS and no tracked `__pycache__` file changes.

- [ ] **Step 4: 提交正式规格差异**

Stage only the files listed in this task and commit as one formal-baseline import save point.

### Task 3: 切换根治理入口并退役双仓同步链

**Files:**
- Modify: `AGENTS.md`
- Create: `CLAUDE.md`
- Modify: `.gitignore`
- Modify: `features/README.md`
- Modify: `tasks/plan.md`
- Modify: `tasks/todo.md`
- Modify: `scripts/validate_repository_baseline_rules.py`
- Modify: `scripts/tests/test_repository_baseline_rules.py`
- Modify: `scripts/tests/test_fast001_implementation_input.py`
- Delete: `docs/specification-baseline/README.md`
- Delete: `docs/specification-baseline/allowlist.json`
- Delete: `docs/specification-baseline/manifest.json`
- Delete: `scripts/sync_specification_baseline.py`
- Delete: `scripts/specification_baseline.py`
- Delete: `scripts/validate_specification_baseline.py`
- Delete: `scripts/tests/test_specification_baseline.py`
- Delete: `scripts/__pycache__/specification_baseline.cpython-312.pyc`
- Delete: `scripts/__pycache__/specification_baseline.cpython-313.pyc`
- Delete: `scripts/__pycache__/sync_specification_baseline.cpython-313.pyc`
- Delete: `scripts/__pycache__/validate_specification_baseline.cpython-313.pyc`
- Delete: `scripts/tests/__pycache__/test_specification_baseline.cpython-312.pyc`
- Delete: `scripts/tests/__pycache__/test_specification_baseline.cpython-313.pyc`

**Interfaces:**
- Consumes: merged specification and implementation tree
- Produces: one current governance entrypoint and a validator that rejects reintroduction of the external snapshot mechanism

- [ ] **Step 1: 合并根规则**

Update `AGENTS.md` so that it contains the formal source priority and hard rules from the specification repository plus NPDMS technical, runtime, domain, query, scope, and long-running-work constraints. Replace only the three external-snapshot rules; do not weaken existing authorization, migration, browser, or module-boundary requirements.

Create `CLAUDE.md` as a short pointer to `AGENTS.md`, so alternate coding clients do not retain the former specification-only subset of rules. Merge `.gitignore` by preserving NPDMS runtime/build ignores and adding the formal repository's `.superpowers/` local-runtime exclusion.

- [ ] **Step 2: 更新当前规格入口**

Update `features/README.md` to declare `specs/features/` and `tasks/features/` as direct same-repository sources. Keep `features/` as legacy implementation inventory and do not promote it to a second Feature Spec source.

- [ ] **Step 3: 改造同库治理校验器**

`validate_repository_rules(repository: Path) -> list[str]` must check:

- required single-repository markers in `AGENTS.md`;
- required formal paths exist;
- `docs/specification-baseline/manifest.json`, `.spec-repo-f-ast-001`, and the three synchronization scripts do not exist;
- historical `tasks/plan.md` and `tasks/todo.md` remain `SUPERSEDED` and point new work at `specs/features/` plus `tasks/features/`;
- JDK 25, host runtime, and real-browser rules remain present.

Update `test_repository_baseline_rules.py` with positive and negative cases for each boundary. Update `test_fast001_implementation_input.py` to validate the direct Feature Spec and remove manifest/commit assertions.

- [ ] **Step 4: 删除同步机制并运行聚焦测试**

Run:

```powershell
python -B -m unittest scripts.tests.test_repository_baseline_rules scripts.tests.test_fast001_implementation_input
python -B scripts/validate_repository_baseline_rules.py
```

Expected: PASS; no manifest, allowlist, synchronization entrypoint, or staging specification directory remains active.

- [ ] **Step 5: 提交治理切换**

Stage only the files listed in this task and commit as one rollbackable governance cutover.

### Task 4: 修正当前有效规范中的双仓指令

**Files:**
- Modify: `docs/design/00-system-detailed-design.md`
- Modify: `docs/design/03-system-architecture.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/20-test-design.md`
- Modify: `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
- Modify: `specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md`
- Modify: `specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`
- Modify: `specs/features/F-SOL-002-site-survey-assignment-and-readiness.md`
- Modify: `specs/features/F-SOL-003-requirement-analysis-versioning.md`
- Modify: `specs/features/F-PLT-001-unified-file-identity-and-version-management.md`
- Modify: `specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`
- Modify: `specs/features/F-CUS-001-customer-master-and-local-lifecycle.md`
- Modify: `specs/features/F-AST-001-device-serial-archive-and-temporal-assignment.md`
- Modify: `scripts/tests/test_validate_sds_phase1.py`

**Interfaces:**
- Consumes: same-repository governance rules from Task 3
- Produces: current normative design and Feature conclusions that no longer instruct users to synchronize an external repository

- [ ] **Step 1: 替换当前规范性指令**

Use “本仓正式规格提交/目标分支/当前 Feature 任务记录” in place of “规格仓提交并同步 NPDMS”. Preserve historical evidence IDs and do not rewrite archived plans, reviews, acceptance outputs, or migration evidence.

- [ ] **Step 2: 保持设计层与实现层分离**

Change the Phase 1 statement from “本规格仓库不承载正式实现” to “Phase 1 formal documents do not approve or embed implementation; implementation lives in the same repository's module and migration paths and remains gated.” Update exact-string validator tests accordingly.

- [ ] **Step 3: 运行 SDS 与 Feature 规格测试**

Run:

```powershell
python -B -m unittest scripts.tests.test_validate_sds_phase1
python -B -m unittest discover -s scripts/tests -p 'test_f*_feature_contract.py'
```

Expected: all applicable tests PASS.

- [ ] **Step 4: 提交当前规范切换**

Stage only the current normative files listed in this task. Historical artifacts remain unchanged.

### Task 5: 全量治理验证、审查与主干合并

**Files:**
- Review: all commits on `chore/single-repository-governance`
- Modify only if a Required/Critical governance defect is found

**Interfaces:**
- Consumes: completed single-repository governance branch
- Produces: reviewed merge into NPDMS `master`

- [ ] **Step 1: 运行规格治理测试集**

Run:

```powershell
python -B scripts/validate_repository_baseline_rules.py
python -B -m unittest discover -s scripts/tests -p 'test_*.py'
git diff --check master...HEAD
```

Expected: governance validator and specification tests PASS; any environment-dependent failures are reported with exact command and root cause, not hidden.

- [ ] **Step 2: 审查范围与历史关系**

Verify:

```powershell
git merge-base --is-ancestor 02f6360735980c4bbd9947844917feb0d4b4aecf HEAD
git diff --name-status master...HEAD
git diff --name-only master...HEAD -- 'pms-module-*' 'yudao-*' 'sql/migrations' 'compose.yaml'
```

Expected: formal specification history is reachable; the last command returns no business code, migration, or runtime configuration changes.

- [ ] **Step 3: 按多轴代码审查处理 Required/Critical 问题**

Review correctness, traceability, source authority, Git reachability, obsolete-path removal, test coverage, and scope. Fix only actual in-scope defects and rerun the affected verification once.

- [ ] **Step 4: 合入 master**

Switch to the primary worktree's `master`, confirm it has not advanced unexpectedly, then merge the reviewed governance branch with a non-fast-forward merge commit. Do not push.

- [ ] **Step 5: 最终复验**

Run:

```powershell
git status --short --branch
git log --oneline -5
git merge-base --is-ancestor 02f6360735980c4bbd9947844917feb0d4b4aecf master
python -B scripts/validate_repository_baseline_rules.py
```

Expected: `master` clean, specification history reachable, single-repository governance PASS.

## 执行记录

状态：`IMPLEMENTATION_COMPLETE`

| 切片 | 提交 | 结果 |
|---|---|---|
| 设计与计划 | `0e8bda30`、`31e3c215` | 固化单一事实源、双父历史和回滚边界 |
| 正式规格历史 | `344ae9f8` | 第二父为`02f6360735980c4bbd9947844917feb0d4b4aecf`，相对第一父零树变化 |
| 正式资产导入 | `6a75889b` | 补入V1.8修订、Phase 2证据、PLT契约、追溯生成器和Feature契约测试；保留较新的P3-E08运行证据 |
| 同库治理切换 | `56801b2f`、`f4d8f949` | 退役manifest/allowlist/sync链，统一根规则、当前SDS和Feature入口 |
| P3-E09物理字节 | `e76277e5` | 正式DDL blob与登记证据一致；41项P3-E09聚焦回归通过 |
| 原规格仓归档 | 规格仓`e44d48ef`、NPDMS`f9babf12` | 原入口标记ARCHIVED并接入NPDMS历史；未提交用户文件未带入 |

验证结果：

- 同库治理校验：PASS；
- Requirement追溯及新增Feature契约聚焦测试：43项PASS；
- Phase 1与Feature契约回归：91项PASS；
- P3-E09物理字节、证据包和核心契约回归：41项PASS；
- 全量规格测试：555项中552项PASS，3项FAIL；失败均位于本分支相对`master`零变更路径，分别为F-PROJ-006/F-PROJ-007旧迁移区间摘要和动态表单存量清单16个未登记实现资产，未在本次同库治理中扩修；
- 业务模块、Flyway迁移和运行配置变更数：0。
