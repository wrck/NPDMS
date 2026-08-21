# NPDMS Specification Baseline Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将规格仓库指定 Git 提交中的 109 个正式资产，以可复现、可校验、不可在实现仓库内擅改的方式同步到 NPDMS，并停止旧任务计划继续驱动开发，形成后续业务代码纠偏所需的完整存量分类清单。

**Architecture:** 规格仓库是唯一业务与设计事实源；NPDMS 只保存由 40 位提交号和逐文件 SHA-256 锁定的只读快照。同步程序仅使用 Git 对象读取源文件，默认执行预检，只有显式 `--apply` 才写目标仓库；离线校验器不依赖源仓库即可验证快照。存量实现通过机器可读登记表分为 `CURRENT_52`、`VALID_V2_POSTPONED`、`EXCLUDED_CURRENT`、`SEMANTIC_REWORK`，本计划只建立可信输入和纠偏边界，不在同步提交中删除业务代码或创建 Flyway。

**Tech Stack:** Python 3.13 标准库、Git CLI、JSON Schema-like validation、`unittest`、Markdown、NPDMS 现有 JDK 25 / Maven / pnpm 工程。

## Global Constraints

- 设计依据固定为规格仓库提交 `a78263d2ce63ffb51da92755cf897844cdf9f601` 中的 `docs/superpowers/specs/2026-08-14-npdms-specification-baseline-sync-design.md`；执行同步时，`source.commit`必须锁定包含本实施计划的后续真实 40 位提交。
- 需求范围为 manifest 中全部 V1/V2 Requirement；本计划不新增或修改业务语义、API、数据库、授权、状态机或工作流。
- 规格仓库优先级保持 `PRD > Engineering Constitution > SDS > Feature Spec > Implementation Plan > Task > Code`。
- 不读取、不复制、不提交 `需求/割接0807需求分析报告.md`；不读取 Excel/XLSX。
- 不同步 `docs/engineering/gates/`、`docs/superpowers/`、`archive/`、`input/`、`需求/`及任何过程材料。
- manifest 不记录本机路径、签署人、批准附件、环境实例或第二套审批状态。
- 不使用目录通配符作为正式同步范围；最终 `allowlist.json` 必须列出排序后的 109 条精确相对路径。
- 目标仓库存在未提交的受管快照修改时，检查和应用都必须明确报告；`--apply`不得覆盖这些修改。
- 每个任务独立提交；提交前读取并遵循 `C:\Users\user\.codex\skills\git-commit-general\SKILL.md`，只显式暂存任务文件，不推送。
- 本计划完成后 NPDMS 状态只能是 `BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED`，不得声明 Feature Ready。

---

## Task 1: 建立提交级规格同步与离线校验工具

**Requirement scope:** manifest 所覆盖的全部 V1/V2 Requirement；仅提供工程追溯，不改变业务行为。

**Files:**

- Create: `E:/AICoding/Projects/NPDMS/docs/specification-baseline/allowlist.json`
- Create: `E:/AICoding/Projects/NPDMS/docs/specification-baseline/README.md`
- Create: `E:/AICoding/Projects/NPDMS/scripts/specification_baseline.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/sync_specification_baseline.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/validate_specification_baseline.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/tests/test_specification_baseline.py`

**Interfaces:**

```python
@dataclass(frozen=True)
class BaselineEntry:
    path: str
    category: str

@dataclass(frozen=True)
class SnapshotChange:
    path: str
    action: Literal["ADD", "REPLACE", "KEEP", "CONFLICT"]

load_allowlist(path: Path) -> tuple[BaselineEntry, ...]
validate_relative_path(path: str, category: str) -> None
resolve_full_commit(source_repo: Path, revision: str) -> str
read_git_blob(source_repo: Path, commit: str, path: str) -> bytes
build_manifest(commit: str, entries: Sequence[BaselineEntry], blobs: Mapping[str, bytes]) -> dict
plan_snapshot(destination_repo: Path, manifest: Mapping[str, object], blobs: Mapping[str, bytes]) -> tuple[SnapshotChange, ...]
apply_snapshot(destination_repo: Path, manifest: Mapping[str, object], blobs: Mapping[str, bytes]) -> None
validate_snapshot(destination_repo: Path, manifest_path: Path) -> list[str]
```

CLI contract:

```text
$specRepo = 'M:/AICoding/CodexData/worktrees/09b5/项目交付平台'
$specCommit = git -C $specRepo rev-parse HEAD
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json --apply

py -3.13 scripts/validate_specification_baseline.py
```

- [ ] **Step 1: 写提交解析与路径边界的失败测试**

在 `test_specification_baseline.py` 使用临时 Git 仓库覆盖：短提交号、未知提交、绝对路径、`../`、反斜杠越界、重复路径、未排序路径、未知类别、禁止目录和允许根之外路径均失败。

测试方法名称固定为 `test_rejects_short_revision`、`test_rejects_path_traversal`、`test_rejects_forbidden_process_material`、`test_rejects_duplicate_or_unsorted_allowlist`。

- [ ] **Step 2: 运行测试并确认红灯**

Run: `py -3.13 -m unittest scripts.tests.test_specification_baseline -v`

Expected: 因同步模块尚不存在或接口未实现而失败。

- [ ] **Step 3: 实现共享模型、Git 对象读取和路径校验**

允许类别固定为：

```python
ALLOWED_CATEGORIES = frozenset({
    "BASELINE", "ENGINEERING", "SDS", "DECISION",
    "TRACEABILITY", "DOMAIN_SPEC", "MODEL_APPENDIX", "MODEL_EVIDENCE",
})
```

所有源内容通过 `git cat-file -e revision^{commit}` 和 `git show revision:path`读取；不得从源工作区直接读取允许清单内文件。

- [ ] **Step 4: 写预检、应用、幂等和离线校验的失败测试**

测试方法名称固定为 `test_check_mode_never_writes`、`test_apply_creates_replaces_and_keeps_exact_files`、`test_apply_refuses_dirty_managed_destination`、`test_second_apply_is_idempotent`、`test_validator_rejects_missing_file`、`test_validator_rejects_one_byte_drift`、`test_validator_rejects_unregistered_manifest_entry`。

- [ ] **Step 5: 实现同步 CLI 与离线 validator**

检查模式打印稳定排序的 `ADD/REPLACE/KEEP/CONFLICT`；存在 `CONFLICT` 返回非零。`--apply`只用临时文件加原子替换写入受管路径，最后写 `manifest.json`。validator精确检查 schemaVersion、repositoryId、40位 commit、文件数量、路径集合、类别和小写64位 SHA-256。

- [ ] **Step 6: 生成并人工核对 109 项 allowlist**

allowlist必须是：

```json
{
  "schemaVersion": 1,
  "files": [
    {"path": "docs/baseline/prd-v1.7.md", "category": "BASELINE"}
  ]
}
```

实际文件包含设计第5节确定的全部109项，按 `path` 字典序排列。运行一个测试断言 `len(files) == 109`、路径唯一、无禁止前缀。

- [ ] **Step 7: 运行定点测试与格式检查**

Run:

```text
py -3.13 -m unittest scripts.tests.test_specification_baseline -v
git diff --check
```

Expected: 全部 PASS，且尚未生成 `manifest.json`、尚未写入任何正式快照。

- [ ] **Step 8: 提交同步工具**

Commit scope: allowlist、README、3个脚本、1个测试文件。

Commit message: `feat(spec): 建立规格基线同步工具`

---

## Task 2: 从锁定提交生成并验证 109 文件快照

**Requirement scope:** 109个文件中登记的全部 V1/V2 Requirement；保持原文件内容和路径语义。

**Files:**

- Create: `E:/AICoding/Projects/NPDMS/docs/specification-baseline/manifest.json`
- Add/Replace: allowlist登记的109个NPDMS正式快照文件
- Test: `E:/AICoding/Projects/NPDMS/scripts/tests/test_specification_baseline.py`

- [ ] **Step 1: 锁定规格仓库真实提交**

在规格仓库运行：

```text
git status --short
git rev-parse HEAD
```

要求：使用完整40位提交；allowlist内已跟踪文件相对该提交无未提交修改。允许清单外未跟踪过程材料不参与同步，也不构成内容输入。

- [ ] **Step 2: 在 NPDMS 执行只读预检**

```text
$specRepo = 'M:/AICoding/CodexData/worktrees/09b5/项目交付平台'
$specCommit = git -C $specRepo rev-parse HEAD
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
```

Expected: 总数109，基于初始审计显示 `ADD=50`、`REPLACE=47`、`KEEP=12`、`CONFLICT=0`。若数字变化，先用逐路径 diff 解释变化，不修改期望来掩盖漂移。

- [ ] **Step 3: 显式应用快照**

使用同一命令追加 `--apply`。写入完成后，manifest最小字段为：

```json
{
  "schemaVersion": 1,
  "source": {
    "repositoryId": "project-delivery-platform-spec",
    "commit": "0123456789abcdef0123456789abcdef01234567"
  },
  "files": []
}
```

- [ ] **Step 4: 验证离线一致性和重复同步幂等**

Run:

```text
py -3.13 scripts/validate_specification_baseline.py
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
git diff --check
```

Expected: 109/109 SHA通过；第二次预检为 `KEEP=109`；禁止目录命中为0。

- [ ] **Step 5: 运行快照回归测试**

Run: `py -3.13 -m unittest scripts.tests.test_specification_baseline -v`

Expected: 全部 PASS。

- [ ] **Step 6: 独立提交规格快照**

只暂存 `manifest.json` 和109个allowlist文件，不混入脚本或业务代码。

Commit message: `docs(spec): 锁定项目交付规格快照`

---

## Task 3: 修正 NPDMS 工程入口并废止旧任务驱动

**Requirement scope:** 全部当前 V1/V2 Requirement；确保任务来源不再引用旧FR或已排除范围。

**Files:**

- Modify: `E:/AICoding/Projects/NPDMS/AGENTS.md`
- Modify: `E:/AICoding/Projects/NPDMS/tasks/plan.md`
- Modify: `E:/AICoding/Projects/NPDMS/tasks/todo.md`
- Create: `E:/AICoding/Projects/NPDMS/scripts/validate_repository_baseline_rules.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/tests/test_repository_baseline_rules.py`

- [ ] **Step 1: 写工程入口失败测试**

测试方法名称固定为 `test_agents_uses_manifest_as_locked_input`、`test_agents_preserves_jdk25_host_runtime_and_browser_acceptance`、`test_legacy_plan_is_marked_superseded`、`test_legacy_todo_is_marked_superseded`、`test_old_tasks_cannot_claim_current_feature_ready`。

测试必须拒绝：`specs/001-project-delivery-platform/是唯一事实源`、旧计划仍为 `IMPLEMENT`、旧TODO仍允许继续执行、缺少manifest commit读取规则。

- [ ] **Step 2: 运行测试并确认红灯**

Run: `py -3.13 -m unittest scripts.tests.test_repository_baseline_rules -v`

Expected: 当前AGENTS和旧任务文件未迁移，测试失败。

- [ ] **Step 3: 修改 AGENTS.md**

保留现有基础平台、JDK 25、模块化单体、宿主机前后端、容器只承载基础设施、服务端鉴权和真实浏览器验收规则。将事实源改为：

```text
规格仓库是业务与设计唯一事实源；本仓库 docs/specification-baseline/manifest.json
所锁定的快照是实现输入。禁止在NPDMS直接修改受管快照；变更必须先进入规格仓库，
再通过同步工具生成新manifest。
```

并明确修改设计/代码前按manifest读取 PRD、工程链、相关SDS、Feature Spec和当前Task。

- [ ] **Step 4: 将旧 plan/todo 标记为 SUPERSEDED**

两文件首个标题后插入统一状态块：

```markdown
> **状态：SUPERSEDED**
>
> 本文件仅用于历史追溯，不再生成或驱动新开发任务。当前任务必须从
> `docs/specification-baseline/manifest.json` 锁定的 Feature Spec 重新生成。
```

不删除历史勾选项，不逐行改写旧任务内容。

- [ ] **Step 5: 实现并运行工程规则 validator**

CLI: `py -3.13 scripts/validate_repository_baseline_rules.py`

validator检查AGENTS关键规则、manifest存在、旧任务状态、旧任务不含当前Feature Ready声明。

Run:

```text
py -3.13 -m unittest scripts.tests.test_repository_baseline_rules -v
py -3.13 scripts/validate_repository_baseline_rules.py
py -3.13 scripts/validate_specification_baseline.py
git diff --check
```

- [ ] **Step 6: 提交工程入口迁移**

Commit message: `docs(engineering): 切换至锁定规格基线`

---

## Task 4: 建立存量实现四分类与 Feature 阻断状态

**Requirement scope:** `CURRENT_52`对应九月首发52项；`INS-05`；`CUT-01`及当前割接闭环；`ACC-06`；设备客观维保事实对应的AST需求。未被规格证明的映射保持 `BLOCKED_BY_SPEC`，不得猜测Requirement。

**Files:**

- Create: `E:/AICoding/Projects/NPDMS/tasks/implementation-baseline-inventory.json`
- Create: `E:/AICoding/Projects/NPDMS/tasks/implementation-baseline-inventory.md`
- Create: `E:/AICoding/Projects/NPDMS/tasks/implementation-baseline-status.md`
- Create: `E:/AICoding/Projects/NPDMS/scripts/validate_implementation_baseline_inventory.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/tests/test_implementation_baseline_inventory.py`

**Inventory schema:**

```json
{
  "schemaVersion": 1,
  "status": "BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED",
  "items": [
    {
      "objectKey": "CutExecution",
      "classification": "EXCLUDED_CURRENT",
      "requirementRefs": ["CUT-01"],
      "codePaths": [],
      "requiredAction": "REMOVE_CURRENT_WRITE_MODEL_AND_SALVAGE_ONLY_PROVEN_P6_FACTS"
    }
  ]
}
```

- [ ] **Step 1: 写分类完整性和业务边界的失败测试**

测试方法名称固定为 `test_every_inventory_item_has_classification_requirement_and_code_path`、`test_cut_execution_and_observation_are_excluded_current`、`test_srv_report_is_valid_v2_postponed`、`test_srv_maintenance_is_semantic_rework`、`test_maintenance_transition_is_semantic_rework`、`test_mes_work_order_is_not_removed_by_pms_keyword_rule`、`test_feature_ready_is_blocked_while_reconciliation_items_exist`。

- [ ] **Step 2: 运行测试并确认红灯**

Run: `py -3.13 -m unittest scripts.tests.test_implementation_baseline_inventory -v`

Expected: inventory尚不存在，测试失败。

- [ ] **Step 3: 只读扫描存量实现入口**

扫描并汇总：

```text
后端Controller与@RequestMapping("/api/v1/pms/")
MyBatis @TableName("pms_*模式匹配结果")
前端 src/api/pms 与 src/views/pms
菜单/Flyway中的PMS资源
现有业务测试类
```

扫描器只发现候选，不按关键词自动决定删除。每个登记项必须填写真实 `codePaths`；发现无法从快照证明的Owner或Requirement时记录 `BLOCKED_BY_SPEC`。

- [ ] **Step 4: 生成并人工审查机器清单与可读报告**

至少明确登记：

| 对象 | 分类 | 处理边界 |
|---|---|---|
| `CutExecution` | `EXCLUDED_CURRENT` | 不再作为割接逐步骤写模型；仅后续逐字段证明的P6事实可迁入闭环 |
| `CutObservation` | `EXCLUDED_CURRENT` | 不保留稳定观察状态机、菜单、API或新迁移 |
| `SrvReport` | `VALID_V2_POSTPONED` | 对应`INS-05`，保留代码，不进入九月Feature/UAT |
| `SrvMaintenance` | `SEMANTIC_REWORK` | 停止独立维保经营生命周期；客观事实后续按AST重构 |
| `MaintenanceTransition` | `SEMANTIC_REWORK` | 按`ACC-06`重构`ServiceHandover`，隔离续保字段 |
| MES生产工单 | `PLATFORM_UPSTREAM_UNCHANGED` | 不属于PMS工单排除范围，不得误删 |

- [ ] **Step 5: 实现 validator 并锁定阻断语义**

validator要求分类值属于固定集合、路径真实存在、Requirement ID能在快照追溯矩阵找到、`EXCLUDED_CURRENT`/`SEMANTIC_REWORK`存在时状态不得是Feature Ready。

Run:

```text
py -3.13 -m unittest scripts.tests.test_implementation_baseline_inventory -v
py -3.13 scripts/validate_implementation_baseline_inventory.py
py -3.13 scripts/validate_repository_baseline_rules.py
py -3.13 scripts/validate_specification_baseline.py
git diff --check
```

- [ ] **Step 6: 提交存量实现清单**

Commit message: `docs(plan): 登记NPDMS存量实现差异`

---

## Task 5: 完成本阶段验证并生成后续领域纠偏计划入口

**Requirement scope:** 不新增业务范围；确认同步和分类不把V3、OUT_OF_SCOPE或V2后置能力带入九月首发。

**Files:**

- Modify: `E:/AICoding/Projects/NPDMS/tasks/implementation-baseline-status.md`
- Test: all Task 1-4 validators and existing repository tests

- [ ] **Step 1: 执行完整工程验证**

```text
py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v
py -3.13 scripts/validate_specification_baseline.py
py -3.13 scripts/validate_repository_baseline_rules.py
py -3.13 scripts/validate_implementation_baseline_inventory.py
mvn clean verify
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 install --frozen-lockfile
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 build:prod
git diff --check
```

构建失败必须区分本次回归与既存质量债；不得关闭检查或放宽TypeScript/Maven规则。

- [ ] **Step 2: 自审范围与保护项**

确认：

- manifest恰好109项且逐文件SHA通过；
- 源提交为真实40位Git提交；
- 禁止目录与受保护资料未进入快照；
- 旧plan/todo明确SUPERSEDED；
- `EXCLUDED_CURRENT`和`SEMANTIC_REWORK`仍阻断Feature Ready；
- `SrvReport`未被误删且未进入首发；
- 没有业务代码、Flyway、菜单或API被本计划修改；
- `git status --short`只包含本任务明确文件和原有受保护项。

- [ ] **Step 3: 在状态文件登记后续三份独立计划**

后续计划名称固定为：

1. `npdms-cutover-current-model-correction`：移除`CutExecution/CutObservation`当前写模型并按P6证据重构闭环；
2. `npdms-asset-maintenance-fact-rework`：将客观维保事实归AST，停止独立维保经营生命周期；
3. `npdms-service-handover-rework`：按`ACC-06`重构`ServiceHandover`并隔离续保字段。

`INS-05/SrvReport`另列为V2后置，不生成九月实施任务。

- [ ] **Step 4: 提交阶段状态**

Commit message: `docs(gate): 完成NPDMS规格基线对齐`

该提交只能确认“规格输入已对齐、差异已登记”，不得标记Feature Ready。

---

## Completion Criteria

- NPDMS存在绑定真实规格提交的manifest，109个路径、类别和SHA全部可离线复算。
- 同步命令默认不写、显式应用、脏文件拒绝、重复同步幂等。
- NPDMS规则不再把漂移的本地规格或旧任务声明为事实源。
- 存量实现四分类完整，重点对象的Requirement、真实代码路径和后续动作明确。
- 已排除或语义错误对象仍被工程状态阻断，未通过文档措辞伪装为完成。
- V2合法资产被保留并后置，基础平台MES工单未被关键词误删。
- 受保护割接分析文档、Excel及过程材料均未读取或提交。
- 后续领域纠偏计划具备明确入口，但本计划未越权修改业务实现。
