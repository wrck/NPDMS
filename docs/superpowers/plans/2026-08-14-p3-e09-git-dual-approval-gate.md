# P3-E09 Git双确认门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将P3-E09从“四角色外部签署共用一个批准哈希”改造为“模型基线Git确认”和“迁移发布Git确认”两道相互隔离、可机器复验的门禁。

**Architecture:** 批准记录先作为不可自引用的JSON制品提交，随后由机器契约记录该提交的完整commit ID、Git作者元数据、记录路径和blob SHA-256，并使用`git show <commit>:<path>`读取提交内内容复验。`modelBaselineDdlSha256`只解除SDS数据模型基线阻断；`approvedDdlSha256`只在另一笔迁移发布确认提交绑定具体批次和运行证据后解除历史迁移与数据切换阻断。

**Tech Stack:** Python 3.13、Git原生命令、JSON机器契约、Markdown工程门禁文档、`unittest`、MySQL 8.4既有执行证据。

## Global Constraints

- 业务语义以`docs/baseline/prd-v1.7.md`为最高事实，不修改PRD、60表DDL、领域模型、API、权限、状态机或业务流程。
- 模型基线确认和迁移发布确认必须引用不同的完整Git commit ID，且不得共享授权范围。
- Git确认只接受已提交对象；工作区文件、暂存区文件和任意外部附件不能单独形成批准。
- 模型基线通过时`approvedDdlSha256`必须仍为空，历史迁移和数据切换继续阻断。
- 迁移发布确认必须绑定具体`releaseId`、环境、源水位、迁移清单、运行验证、对账和回退证据。
- Q08的122项索引仅为候选索引，继续受Feature查询计划和P3-E06性能验收约束。
- 不读取、不生成、不校验、不提交两份受保护的未跟踪原始资料。
- 所有Python命令使用`py -3.13`；Git仅显式暂存本任务文件，不使用`git add .`或`git add -A`，不推送。

---

## 文件结构

### 新增文件

- `scripts/p3e09_git_confirmation.py`：唯一Git对象读取与双确认校验组件。
- `scripts/generate_p3e09_model_baseline_approval.py`：生成不可自引用的模型基线批准记录。
- `scripts/tests/test_p3e09_git_confirmation.py`：commit、祖先、blob、作者、范围隔离和篡改负向测试。
- `scripts/tests/test_generate_p3e09_model_baseline_approval.py`：批准记录生成与漂移测试。
- `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-model-baseline-approval.json`：模型基线批准记录结构示例。
- `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-migration-release-approval.json`：迁移发布批准记录结构示例。
- `docs/engineering/gates/phase-3/submissions/P3-E09/model-baseline/<ddl-sha-prefix>.json`：当前DDL模型基线批准记录，由生成器产生并在独立提交中冻结。

### 修改文件

- `scripts/p3e09_approval_policy.py`：移除四角色外部附件模型，组合两类Git确认校验结果。
- `scripts/generate_ddl_drift_review.py`：生成模型复审状态与迁移发布状态相互独立的寄存器。
- `scripts/validate_ddl_item_decision_register.py`：验证模型基线确认，不再要求SDS阶段填写迁移批准哈希。
- `scripts/validate_core_migration_schema_contract.py`：验证`modelBaselineApproval`和`migrationReleaseApproval`的范围、哈希与提交隔离。
- `scripts/generate_phase3_evidence_packets.py`：从模型基线Git确认派生SDS Ready，同时保留迁移阻断。
- `scripts/validate_phase3_evidence_register.py`：校验双门禁状态和阻断范围。
- `scripts/validate_phase3_evidence_submission.py`：按批准类型校验提交结构，禁止混用。
- `scripts/sync_phase3_p3e09_requirement_confirmation.py`：同步模型基线批准结果而不填充迁移批准字段。
- `scripts/tests/test_generate_ddl_drift_review.py`、`scripts/tests/test_validate_ddl_item_decision_register.py`、`scripts/tests/test_validate_core_migration_schema_contract.py`、`scripts/tests/test_generate_phase3_evidence_packets.py`、`scripts/tests/test_validate_phase3_evidence_register.py`、`scripts/tests/test_validate_phase3_evidence_submission.py`、`scripts/tests/test_sync_phase3_p3e09_requirement_confirmation.py`：双门禁回归测试。
- `docs/traceability/core-migration-schema-contract.json`：新增模型基线批准结构并保留迁移发布批准结构。
- `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`：写回模型基线确认与迁移发布待确认的独立状态。
- `docs/engineering/gates/phase-3/phase3-evidence-register.json`：把P3-E09阻断拆为模型基线、历史迁移和数据切换三类。
- `docs/engineering/gates/phase-3/evidence-packet-templates/manifest.json`：登记两个新模板并移除旧的共用签署模板。
- `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`：降级为迁移发布兼容入口或由新模板替代，禁止继续表达SDS模型批准。
- `docs/engineering/gates/phase-3/submissions/README.md`、`docs/engineering/gates/phase-3/README.md`、`docs/engineering/gates/phase-3/gate-status.md`、`docs/design/08-data-model.md`、`docs/design/09-database-design.md`：统一Git双确认门禁说明。

---

### Task 1: 建立可独立测试的Git确认校验组件

**Files:**
- Create: `scripts/p3e09_git_confirmation.py`
- Create: `scripts/tests/test_p3e09_git_confirmation.py`
- Modify: `scripts/p3e09_approval_policy.py`
- Modify: `scripts/tests/test_p3e09_approval_policy.py`

**Interfaces:**
- Consumes: 仓库根路径、批准类型、批准登记对象、当前HEAD和期望批准事实。
- Produces: `git_blob_sha256(data: bytes) -> str`、`read_commit_artifact(root: Path, commit_id: str, record_ref: str) -> tuple[bytes, dict[str, str]]`、`validate_git_confirmation(root: Path, confirmation: dict[str, object], expected_kind: str, expected_record: dict[str, object], current_head: str | None = None) -> list[str]`、`validate_distinct_confirmation_commits(model: dict[str, object], migration: dict[str, object]) -> list[str]`。

- [ ] **Step 1: 写Git对象读取与篡改负向测试**

```python
def test_committed_record_is_verified_from_git_object_not_worktree(self):
    commit_id = self.commit_record("model-baseline.json", self.record)
    self.write_record("model-baseline.json", {**self.record, "status": "TAMPERED"})
    errors = POLICY.validate_git_confirmation(
        self.root,
        self.confirmation(commit_id, "model-baseline.json"),
        "MODEL_BASELINE",
        self.record,
        current_head=commit_id,
    )
    self.assertEqual([], errors)

def test_non_ancestor_commit_is_rejected(self):
    errors = POLICY.validate_git_confirmation(
        self.root, self.orphan_confirmation, "MODEL_BASELINE", self.record
    )
    self.assertTrue(any("ancestor" in error for error in errors))

def test_model_and_migration_cannot_share_commit(self):
    errors = POLICY.validate_distinct_confirmation_commits(
        {"commitId": "a" * 40}, {"commitId": "a" * 40}
    )
    self.assertTrue(any("distinct" in error for error in errors))
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_p3e09_git_confirmation -v`

Expected: FAIL，提示`p3e09_git_confirmation.py`或目标函数尚不存在。

- [ ] **Step 3: 实现Git对象和确认登记校验**

```python
def read_commit_artifact(root: Path, commit_id: str, record_ref: str) -> tuple[bytes, dict[str, str]]:
    full_id = run_git(root, "rev-parse", f"{commit_id}^{{commit}}")
    metadata = run_git(root, "show", "-s", "--format=%H%x00%an%x00%ae%x00%aI", full_id).split("\0")
    blob = subprocess.run(
        ["git", "show", f"{full_id}:{record_ref}"], cwd=root, check=True, stdout=subprocess.PIPE
    ).stdout
    return blob, {"commitId": metadata[0], "authorName": metadata[1], "authorEmail": metadata[2], "authoredAt": metadata[3]}

def validate_distinct_confirmation_commits(model: dict[str, object], migration: dict[str, object]) -> list[str]:
    if model.get("commitId") and model.get("commitId") == migration.get("commitId"):
        return ["model baseline and migration release confirmations require distinct commits"]
    return []
```

实现同时校验40位完整commit ID、`merge-base --is-ancestor`、记录路径位于仓库内、提交内blob SHA-256、Git作者四元组、批准类型、当前事实和禁止工作区证据。

- [ ] **Step 4: 替换旧四角色附件策略并运行定点测试**

Run: `py -3.13 -m unittest scripts.tests.test_p3e09_git_confirmation scripts.tests.test_p3e09_approval_policy -v`

Expected: PASS；旧`signoffs`、`attestationMethod`、四份附件不再是模型基线通过条件。

- [ ] **Step 5: 提交Git确认基础组件**

```bash
git add scripts/p3e09_git_confirmation.py scripts/p3e09_approval_policy.py scripts/tests/test_p3e09_git_confirmation.py scripts/tests/test_p3e09_approval_policy.py
git commit -m "refactor(gate): 建立P3-E09 Git确认策略"
```

### Task 2: 生成不可自引用的模型基线批准记录

**Files:**
- Create: `scripts/generate_p3e09_model_baseline_approval.py`
- Create: `scripts/tests/test_generate_p3e09_model_baseline_approval.py`
- Create: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-model-baseline-approval.json`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/manifest.json`

**Interfaces:**
- Consumes: `core-migration-schema-contract.json`、逐项寄存器、ADR-0028、确认包、MySQL 8.4执行证据和独立复审结论。
- Produces: `build_model_baseline_record(root: Path) -> dict[str, object]`和确定性JSON批准记录；记录不包含自身commit ID。

- [ ] **Step 1: 写批准记录完整性与确定性测试**

```python
def test_record_binds_current_model_without_migration_authority(self):
    record = GENERATOR.build_model_baseline_record(ROOT)
    self.assertEqual("MODEL_BASELINE", record["approvalKind"])
    self.assertEqual(record["currentDdlSha256"], record["modelBaselineDdlSha256"])
    self.assertIsNone(record["approvedDdlSha256"])
    self.assertEqual(1883, record["itemCount"])
    self.assertEqual(0, record["deferredItemCount"])
    self.assertNotIn("confirmationCommitId", record)
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_generate_p3e09_model_baseline_approval -v`

Expected: FAIL，提示生成器尚不存在。

- [ ] **Step 3: 实现确定性记录生成器**

批准记录固定包含：`schemaVersion`、`id`、`approvalKind`、`status`、`currentDdlSha256`、`modelBaselineDdlSha256`、`approvedDdlSha256: null`、`itemsSha256`、`itemCount`、`itemIdsSha256`、当前规模、九组决策摘要、Q08候选声明、证据引用及生成器版本。时间字段从调用参数传入或由独立元数据文件记录，不参与事实哈希。

- [ ] **Step 4: 验证生成内容和漂移检查**

Run: `py -3.13 scripts/generate_p3e09_model_baseline_approval.py --check`

Expected: 首次在记录尚未生成时FAIL；生成记录后再次运行PASS，重复生成字节一致。

- [ ] **Step 5: 提交生成器和模板**

```bash
git add scripts/generate_p3e09_model_baseline_approval.py scripts/tests/test_generate_p3e09_model_baseline_approval.py docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-model-baseline-approval.json docs/engineering/gates/phase-3/evidence-packet-templates/manifest.json
git commit -m "feat(gate): 生成P3-E09模型基线批准记录"
```

### Task 3: 拆分机器契约中的模型批准与迁移批准

**Files:**
- Modify: `scripts/generate_ddl_drift_review.py`
- Modify: `scripts/validate_ddl_item_decision_register.py`
- Modify: `scripts/validate_core_migration_schema_contract.py`
- Modify: `scripts/tests/test_generate_ddl_drift_review.py`
- Modify: `scripts/tests/test_validate_ddl_item_decision_register.py`
- Modify: `scripts/tests/test_validate_core_migration_schema_contract.py`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`

**Interfaces:**
- Consumes: Task 1的`validate_git_confirmation`和Task 2的模型批准记录。
- Produces: `modelBaselineApproval`与`migrationReleaseApproval`两个独立对象；前者含`modelBaselineDdlSha256`，后者继续持有`approvedDdlSha256`。

- [ ] **Step 1: 写状态隔离和字段串用负向测试**

```python
def test_model_baseline_approval_does_not_fill_migration_hash(self):
    contract = self.approved_model_contract()
    self.assertEqual(contract["currentDdlSha256"], contract["modelBaselineApproval"]["modelBaselineDdlSha256"])
    self.assertIsNone(contract["migrationReleaseApproval"]["approvedDdlSha256"])
    self.assertEqual([], VALIDATOR.validate(contract, self.root))

def test_copying_model_hash_to_migration_approval_is_rejected(self):
    contract = self.approved_model_contract()
    contract["migrationReleaseApproval"]["approvedDdlSha256"] = contract["currentDdlSha256"]
    self.assertTrue(any("migration release confirmation" in error for error in VALIDATOR.validate(contract, self.root)))
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_generate_ddl_drift_review scripts.tests.test_validate_ddl_item_decision_register scripts.tests.test_validate_core_migration_schema_contract -v`

Expected: FAIL，现有单一`approval`仍要求四角色签署并把SDS批准绑定到`approvedDdlSha256`。

- [ ] **Step 3: 实现双批准契约和受控状态迁移**

```json
{
  "modelBaselineApproval": {
    "status": "MODEL_BASELINE_APPROVED",
    "modelBaselineDdlSha256": "<currentDdlSha256>",
    "recordRef": "docs/engineering/gates/phase-3/submissions/P3-E09/model-baseline/<ddl-prefix>.json",
    "gitConfirmation": {
      "commitId": "<40-hex>",
      "authorName": "<git-author>",
      "authorEmail": "<git-email>",
      "authoredAt": "<git-iso-time>",
      "recordBlobSha256": "<sha256>"
    }
  },
  "migrationReleaseApproval": {
    "status": "MIGRATION_RELEASE_APPROVAL_PENDING",
    "approvedDdlSha256": null,
    "releaseId": null,
    "gitConfirmation": null
  }
}
```

validator只允许证据驱动的状态组合；任一DDL、Items、Item ID集合、记录blob或祖先关系漂移均使模型批准失效。

- [ ] **Step 4: 重生成寄存器并验证**

Run: `py -3.13 scripts/generate_ddl_drift_review.py`

Run: `py -3.13 scripts/validate_ddl_item_decision_register.py`

Run: `py -3.13 scripts/validate_core_migration_schema_contract.py`

Expected: 三项PASS；1,883项逐项裁决保持不变，迁移批准仍为待确认。

- [ ] **Step 5: 提交机器契约拆分**

```bash
git add scripts/generate_ddl_drift_review.py scripts/validate_ddl_item_decision_register.py scripts/validate_core_migration_schema_contract.py scripts/tests/test_generate_ddl_drift_review.py scripts/tests/test_validate_ddl_item_decision_register.py scripts/tests/test_validate_core_migration_schema_contract.py docs/traceability/core-migration-schema-contract.json specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json
git commit -m "refactor(data-model): 拆分模型与迁移批准契约"
```

### Task 4: 拆分Phase 3证据状态与提交模板

**Files:**
- Modify: `scripts/generate_phase3_evidence_packets.py`
- Modify: `scripts/validate_phase3_evidence_register.py`
- Modify: `scripts/validate_phase3_evidence_submission.py`
- Modify: `scripts/sync_phase3_p3e09_requirement_confirmation.py`
- Modify: `scripts/tests/test_generate_phase3_evidence_packets.py`
- Modify: `scripts/tests/test_validate_phase3_evidence_register.py`
- Modify: `scripts/tests/test_validate_phase3_evidence_submission.py`
- Modify: `scripts/tests/test_sync_phase3_p3e09_requirement_confirmation.py`
- Create: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-migration-release-approval.json`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`

**Interfaces:**
- Consumes: Task 3双批准机器契约。
- Produces: `READY_FOR_SDS_BASELINE`、`MIGRATION_RELEASE_APPROVAL_PENDING`和`MIGRATION_RELEASE_APPROVED`的确定性派生；阻断范围分别落到`PHASE_3_BASELINE / DATA_MODEL_BASELINE`与`HISTORICAL_DATA_MIGRATION / DATA_CUTOVER`。

- [ ] **Step 1: 写门禁范围正反测试**

```python
def test_model_approval_releases_only_sds_baseline_blocks(self):
    item = self.generated_p3e09(model_approved=True, migration_approved=False)
    self.assertEqual("MODEL_BASELINE_APPROVED", item["confirmedFacts"]["modelDecisionStatus"])
    self.assertNotIn("PHASE_3_BASELINE", item["blocks"])
    self.assertNotIn("DATA_MODEL_BASELINE", item["blocks"])
    self.assertIn("HISTORICAL_DATA_MIGRATION", item["blocks"])
    self.assertIn("DATA_CUTOVER", item["blocks"])

def test_same_commit_for_both_gates_is_rejected(self):
    payload = self.dual_approved_payload(shared_commit=True)
    self.assertTrue(any("distinct commits" in error for error in VALIDATOR.validate(payload)))
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_generate_phase3_evidence_packets scripts.tests.test_validate_phase3_evidence_register scripts.tests.test_validate_phase3_evidence_submission scripts.tests.test_sync_phase3_p3e09_requirement_confirmation -v`

Expected: FAIL，现有P3-E09仍使用一个`VERIFIED`状态和一个共用提交模板。

- [ ] **Step 3: 实现双状态派生和迁移模板**

迁移发布模板固定要求`approvalKind=MIGRATION_RELEASE`、`modelBaselineDdlSha256`、`approvedDdlSha256`、`releaseId`、环境、源结构哈希、源水位、迁移清单、目标目录、映射/校验/manifest/回退制品哈希、演练/对账/回退结果、迁移责任人和独立复审引用；缺少任一字段时保持`MIGRATION_RELEASE_APPROVAL_PENDING`。

- [ ] **Step 4: 重生成并验证Phase 3证据**

Run: `py -3.13 scripts/generate_phase3_evidence_packets.py`

Run: `py -3.13 scripts/sync_phase3_p3e09_requirement_confirmation.py`

Run: `py -3.13 scripts/validate_phase3_evidence_register.py`

Expected: 模型确认未登记前保持现态；登记后SDS基线可Ready，但历史迁移和切换仍明确阻断。

- [ ] **Step 5: 提交Phase 3双门禁派生**

```bash
git add scripts/generate_phase3_evidence_packets.py scripts/validate_phase3_evidence_register.py scripts/validate_phase3_evidence_submission.py scripts/sync_phase3_p3e09_requirement_confirmation.py scripts/tests/test_generate_phase3_evidence_packets.py scripts/tests/test_validate_phase3_evidence_register.py scripts/tests/test_validate_phase3_evidence_submission.py scripts/tests/test_sync_phase3_p3e09_requirement_confirmation.py docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-migration-release-approval.json docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json docs/engineering/gates/phase-3/phase3-evidence-register.json
git commit -m "feat(gate): 拆分P3-E09双门禁状态"
```

### Task 5: 冻结模型基线批准记录并登记确认提交

**Files:**
- Create: `docs/engineering/gates/phase-3/submissions/P3-E09/model-baseline/<ddl-sha-prefix>.json`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`

**Interfaces:**
- Consumes: Task 2批准记录生成器、Task 1 Git确认校验器和当前独立复审GO证据。
- Produces: 一笔只包含模型基线批准记录的确认提交，以及另一笔登记该commit/blob的派生状态提交。

- [ ] **Step 1: 生成并验证模型基线批准记录**

Run: `py -3.13 scripts/generate_p3e09_model_baseline_approval.py`

Run: `py -3.13 scripts/generate_p3e09_model_baseline_approval.py --check`

Expected: PASS；记录内`approvedDdlSha256`为空且不含自身commit ID。

- [ ] **Step 2: 创建独立模型基线Git确认提交**

```bash
git add docs/engineering/gates/phase-3/submissions/P3-E09/model-baseline/<ddl-sha-prefix>.json
git commit -m "docs(gate): 确认P3-E09模型基线"
```

该提交只冻结批准记录；不得同时修改引用该提交ID的机器契约，从结构上消除自引用。

- [ ] **Step 3: 从Git对象生成确认登记**

Run: `py -3.13 scripts/sync_phase3_p3e09_requirement_confirmation.py --model-confirmation-commit <40-hex>`

Expected: 脚本从`git show`读取批准记录并填入commit、author、email、time、recordRef和recordBlobSha256；不读取工作区同名文件作为批准事实。

- [ ] **Step 4: 验证模型批准与迁移阻断并提交登记**

Run: `py -3.13 scripts/validate_core_migration_schema_contract.py`

Run: `py -3.13 scripts/validate_ddl_item_decision_register.py`

Run: `py -3.13 scripts/validate_phase3_evidence_register.py`

Expected: 模型基线为`MODEL_BASELINE_APPROVED`；`approvedDdlSha256`为空；`HISTORICAL_DATA_MIGRATION`和`DATA_CUTOVER`仍阻断。

```bash
git add docs/traceability/core-migration-schema-contract.json specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json docs/engineering/gates/phase-3/phase3-evidence-register.json
git commit -m "docs(gate): 登记P3-E09模型确认提交"
```

### Task 6: 统一正式文档并完成全量验收

**Files:**
- Modify: `docs/engineering/gates/phase-3/submissions/README.md`
- Modify: `docs/engineering/gates/phase-3/README.md`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/decisions/open-questions.md`

**Interfaces:**
- Consumes: Tasks 1-5的最终机器状态。
- Produces: 唯一一致的工程门禁说明；SDS模型基线可关闭，迁移发布门禁保持后置待执行。

- [ ] **Step 1: 更新正式文档的双门禁术语**

所有正式文档统一使用：`modelBaselineDdlSha256`表示SDS模型基线；`approvedDdlSha256`表示具体迁移发布；不得再使用“四角色外部签署”“一个批准哈希同时控制SDS与迁移”的表述。

- [ ] **Step 2: 定点扫描冲突和受保护输入引用**

Run: `rg -n "four-role|四角色外部|attestationMethod|APPROVAL_SYSTEM_RECORD|MANUAL_SIGNED_RECORD" scripts docs specs/001-project-delivery-platform/evidence/migration`

Expected: 仅允许历史决策说明中的明确“已废止规则”命中；正式现行规则零命中。

Run: `git status --short`

Expected: 两份受保护原始资料仅保持未跟踪，未进入暂存区。

- [ ] **Step 3: 运行生成器漂移和正式validator**

Run: `py -3.13 scripts/generate_p3e09_model_baseline_approval.py --check`

Run: `py -3.13 scripts/validate_core_migration_schema_contract.py`

Run: `py -3.13 scripts/validate_ddl_item_decision_register.py`

Run: `py -3.13 scripts/validate_phase3_evidence_register.py`

Run: `py -3.13 scripts/validate_sds_phase3.py`

Expected: 全部PASS。

- [ ] **Step 4: 运行全量单元测试和格式检查**

Run: `py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v`

Run: `git diff --check`

Expected: 全量测试PASS，格式检查无输出。

- [ ] **Step 5: 完成自审和独立复审**

自审逐项确认：两类commit不同；模型批准不含迁移授权；迁移批准缺批次证据时不可达；Git blob而非工作区文件是批准事实；Q08仍是候选；DDL和1,883项裁决未改变。独立复审必须用fresh context复跑关键负向攻击和全量validator，结论为GO后才允许发布SDS Phase 3模型基线。

- [ ] **Step 6: 提交文档与验收闭环**

```bash
git add docs/engineering/gates/phase-3/submissions/README.md docs/engineering/gates/phase-3/README.md docs/engineering/gates/phase-3/gate-status.md docs/design/08-data-model.md docs/design/09-database-design.md docs/decisions/open-questions.md
git commit -m "docs(gate): 发布P3-E09模型基线门禁"
```

## 迁移发布后续执行边界

本计划只实现迁移发布Git确认的契约、模板和校验能力，不生成虚构的迁移批准提交。具体迁移批次形成后，使用同一校验组件生成`MIGRATION_RELEASE`批准记录，在独立commit中冻结，再由后续登记提交写入`approvedDdlSha256`；该commit必须与模型基线确认commit不同，并绑定真实`releaseId`、环境、水位、清单、演练、对账和回退证据。
