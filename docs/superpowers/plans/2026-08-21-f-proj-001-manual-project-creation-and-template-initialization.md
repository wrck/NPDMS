# F-PROJ-001 Manual Project Creation and Template Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在NPDMS中交付手动项目创建Vertical Slice：候选模板与预览、服务端选模、项目编码、模板/流程冻结、Stage→ProjectTask与执行契约初始化、ACC交付件同步同事务初始化、服务经理人工确认、详情与审计，并保证创建全有或全无且不持久化创建草稿。

**Architecture:** 规格仓库继续作为唯一业务与设计事实源，NPDMS只消费Git提交锁定的只读快照。PROJ应用服务编排一个MySQL本地事务；ACC通过公开内部应用接口并以`Propagation.MANDATORY`加入同一事务，禁止PROJ访问ACC Repository。Project、任务、执行契约、交付件、成功幂等记录、成功审计和Outbox只有全部写入成功才共同提交；任一失败全部回滚，不产生初始化中间状态。

**Tech Stack:** Java 25、Spring Boot、Spring Transaction、MyBatis-Plus、MySQL 8.4、Flyway 11.10.5、Vue 3.5、TypeScript 6、Element Plus、Axios、pnpm 9.15.5、Playwright、Python 3.13规格/Schema校验脚本。

**Spec:** `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`

## Global Constraints

- Requirement仅为`PM-01`、`PM-03`；`PM-08`只限定V1人工确认服务经理与V2自动指派的边界，不宣称完成PM-08。
- PRD增量`CHG-PRD-2026-08-21-001`有效：创建失败不持久化Project或创建草稿，不新增Project `DRAFT`状态，不写浏览器持久化存储。
- Q-FPROJ-002有效：创建时PROJ与ACC必须同步、同库、同Spring事务，全有或全无；禁止`PENDING/INITIALIZING`初始化状态、Saga、最终一致性补建或异步降级。
- ADR-0032有效：上述语义是F-PROJ-001对跨Context默认最终一致性的限定例外，不授权跨Context Repository访问。
- `acc_project_deliverable.status='PENDING'`仅表示已经完整创建的交付件等待后续业务提交，不是项目创建初始化中间状态。
- PROJ不得直接访问ACC Repository；只能调用`AcceptanceDeliverableInitializationApi`。
- 当前正式表前缀为`proj_*`、`acc_*`、`plt_*`；不得为本Feature继续新增`pms_project*`写路径或建立第二套Project真值。
- 不修改已经执行的Flyway；只增加前向迁移。历史迁移和旧库切换不属于本Feature。
- API业务前缀为`/api/v1/pms`；Yudao管理端运行时仍会统一叠加`/admin-api`。
- 服务端权限是最终依据；前端按钮隐藏不能代替`pms:project:create`、模板候选范围或项目树数据权限校验。
- 目标工程必须使用JDK 25；前端必须使用Node `>=20.19.0`和pnpm `9.15.5`。
- 实施只允许以本地`E:/AICoding/Projects/NPDMS`的`feat/specification-baseline-sync@fd3978bad2955263a653900d04ab39b09cc05abf`为起点创建干净Codex隔离worktree；明确禁止读取、合并、cherry-pick或以`engineering-chain-phase-TmrsP0@abbc3fa0b5b2ad98a405e0118cc0f9231f99cb46`为实现输入。
- Docker实施数据库使用全新数据库名`npms`和独立Compose project/volume，不复用或清理现有`npdms`数据卷。
- 实现前先盘点当前允许基线中的活跃相关业务，优先复用再改造；不得先删除后重建相同能力。
- 只有替代入口和全部消费者切换完成后，才可把旧代码标记为`@Deprecated(forRemoval = false)`并移除运行时注册/引用；暂不删除文件。文件一经标记废弃，后续实现不得再读取其正文，只允许按路径执行引用扫描和Java编译lint。
- Java门禁必须以`-Xlint:deprecation`编译且不存在任何`@Deprecated`引用警告；禁止通过关闭warning、降低lint或忽略编译结果放行。
- 需求方已于2026-08-21授权本地实施与本地提交；不授权推送、UAT或发布。

---

## Implementation Start Gate

本计划完整，需求方已授权本地实施；当前状态为`IMPLEMENTATION_AUTHORIZED / START_GATE_TASK_0_REQUIRED`。执行者在Task 1前必须先完成Task 0并逐项取得PASS；任一项失败即停止，不得用旧`pms_*`结构临时兼容。

1. 不修改`E:/AICoding/Projects/NPDMS`当前脏工作区；从允许的本地提交`fd3978bad2955263a653900d04ab39b09cc05abf`创建`codex/f-proj-001-atomic-alignment`隔离worktree。禁止使用明确排除的`engineering-chain-phase-TmrsP0`分支或其文件内容。
2. 规格仓库必须先提交本Feature Spec、PRD增量、Q-FPROJ-001/002决策和本计划；同步工具只读取Git对象，不读取未提交文件。
3. NPDMS `docs/specification-baseline/allowlist.json`必须纳入：
   - `docs/baseline/prd-v1.8.md`
   - `docs/baseline/prd-v1.8-amendment-001-no-manual-project-draft.md`
   - `docs/decisions/0029-stage-task-work-binding-workbench.md`
   - `docs/decisions/0030-project-task-execution-contract-and-cutover-checklist-carriers.md`
   - `docs/decisions/0032-manual-project-creation-cross-context-atomicity.md`
   - `specs/features/README.md`
   - `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
4. Task 0必须先在允许基线上完成F-PROJ-001所需的最小核心前向割接，并在全新`npms` MySQL 8.4数据库提供单一正式写模型：`proj_project`、`proj_project_task`、`proj_project_template_task_definition`、`proj_project_task_execution_contract`、`proj_project_member_assignment`、`acc_project_deliverable`。不实施与本Feature无关的全域历史迁移。
5. 核心割接后，下列扫描必须为零命中：

```powershell
rg -n "@TableName\(\"pms_project|INSERT INTO pms_project|UPDATE pms_project|FROM pms_project" `
  pms-module-project pms-module-engineering yudao-server -g "*.java" -g "*.xml"
```

6. 干净实施worktree中运行：

```powershell
git status --short
py -3.13 scripts/validate_specification_baseline.py
$env:COMPOSE_PROJECT_NAME='npms-f-proj-001'
$env:NPDMS_DB_NAME='npms'
$env:NPDMS_MYSQL_PORT='13316'
$env:NPDMS_REDIS_PORT='16389'
docker compose config --quiet
```

Expected：`git status --short`无输出；规格校验PASS；Compose配置PASS。

---

### Task 0: Establish the Allowed Local Baseline and Reuse-First Core Cutover

**Files:**
- Create: Codex隔离worktree on branch `codex/f-proj-001-atomic-alignment` from `fd3978bad2955263a653900d04ab39b09cc05abf`
- Create: `E:/AICoding/Projects/NPDMS/sql/migrations/V51__f_proj_001_core_project_write_model.sql`
- Create: `E:/AICoding/Projects/NPDMS/scripts/validate_f_proj_001_core_cutover.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/tests/test_validate_f_proj_001_core_cutover.py`
- Modify: only active current-branch Project/Template/Task services, controllers and consumers proven reusable by the inventory
- Modify: `E:/AICoding/Projects/NPDMS/pom.xml` only if required to expose a repeatable `-Xlint:deprecation` compilation profile

**Interfaces:**
- Consumes: only the allowed local baseline and the committed F-PROJ-001 specification snapshot.
- Produces: the six formal core tables required by Tasks 1～10, one active write path, a deprecated-but-not-deleted legacy surface, and an isolated Docker `npms` database.

- [ ] **Step 1: Create and verify the isolated worktree**

Create `codex/f-proj-001-atomic-alignment` from the exact allowed commit. Verify `git status --short` is empty and `git merge-base --is-ancestor abbc3fa0b5b2ad98a405e0118cc0f9231f99cb46 HEAD` is false. Do not inspect files from the prohibited branch.

- [ ] **Step 2: Inventory active reusable behavior without reading deprecated files**

Use `rg -l` first to list current Project/Template/Task creation consumers and separately list files already carrying`@Deprecated`. Read only active files. Record each active capability as `REUSE_AS_IS`、`REUSE_AND_ADAPT`或`DEPRECATE_AFTER_CUTOVER`; every adapted line must map to PM-01/PM-03. Do not delete or wholesale recreate an existing active capability.

- [ ] **Step 3: Write a failing core-cutover contract test**

The test requires the six formal tables, rejects new F-PROJ-001 writes to`pms_project*`, verifies exactly one active create controller/service path, and verifies no active source references a type listed as deprecated.

- [ ] **Step 4: Add the minimal additive V51 core model**

Create only the F-PROJ-001 prerequisites from the approved SDS physical contract. Because`npms` is a fresh database, do not add legacy data backfill or destructive rename/drop. Existing legacy tables remain available only until consumer cutover is proven; no new business command may write them.

- [ ] **Step 5: Reuse and adapt active services before deprecation**

Reuse current validation, template matching, authorization and UI behavior where they satisfy the locked contract. Build the replacement and move every consumer first. Only then mark confirmed retired Java types with`@Deprecated(forRemoval = false, since = "F-PROJ-001")`, remove their Spring/runtime registration, and stop reading those files.

- [ ] **Step 6: Enforce zero deprecated references**

Run a path-only reference scan plus Java compilation with`-Xlint:deprecation`; any deprecated-use warning fails the gate. Do not suppress warnings and do not delete deprecated files.

- [ ] **Step 7: Start a fresh isolated Docker database**

```powershell
$env:COMPOSE_PROJECT_NAME='npms-f-proj-001'
$env:NPDMS_DB_NAME='npms'
$env:NPDMS_MYSQL_PORT='13316'
$env:NPDMS_REDIS_PORT='16389'
docker compose up -d mysql redis
docker compose run --rm migrate
```

Expected: a new Compose volume is created, Flyway initializes database`npms` from V1 through the current migration, and existing`npdms`containers/volumes remain untouched.

- [ ] **Step 8: Validate and commit the prerequisite slice**

Run the core-cutover validator, focused Java tests,`-Xlint:deprecation` compilation, Flyway`validate`, and`git diff --check`. Commit only the reviewed Task 0 files; do not push.

---

## File Structure

### 规格锁与Schema

- `E:/AICoding/Projects/NPDMS/docs/specification-baseline/allowlist.json`：显式登记V1.8与F-PROJ-001正式输入。
- `E:/AICoding/Projects/NPDMS/tasks/features/F-PROJ-001.md`：从锁定Feature Spec生成的当前任务定义；旧`tasks/plan.md`、`tasks/todo.md`保持历史只读。
- `E:/AICoding/Projects/NPDMS/sql/migrations/V60__f_proj_001_manual_project_creation.sql`：Feature专属前向Schema；V51～V59保留给核心割接，避免版本碰撞。
- `E:/AICoding/Projects/NPDMS/scripts/validate_f_proj_001_schema.py`：只读校验Feature需要的表、列、索引和禁止的草稿载体。
- `E:/AICoding/Projects/NPDMS/scripts/tests/test_validate_f_proj_001_schema.py`：校验器单元测试。

### PLT事务支撑

- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/businesscode/BusinessCodeApi.java`：按已发布规则分配项目编码并返回规则版本。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/businesscode/dto/BusinessCodeAllocation.java`：编码和值对象。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/idempotency/TransactionalIdempotencyApi.java`：同事务幂等占用与成功响应冻结。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/idempotency/dto/IdempotencyDecision.java`：OWNER或REPLAY决定及冻结响应。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/outbox/TransactionalOutboxApi.java`：同事务追加`ProjectCreated`事件。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/outbox/dto/OutboxAppendCommand.java`：Outbox追加命令。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/audit/BusinessAuditApi.java`：成功审计参与业务事务，失败审计在业务回滚后独立追加。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/audit/dto/BusinessAuditCommand.java`：已脱敏审计命令。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/businesscode/BusinessCodeServiceImpl.java`、`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/idempotency/TransactionalIdempotencyServiceImpl.java`、`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/outbox/TransactionalOutboxServiceImpl.java`、`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/audit/BusinessAuditServiceImpl.java`：PLT Owner实现。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/businesscode/BusinessCodeRuleDO.java`与`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/businesscode/BusinessCodeRuleMapper.java`：只封装`plt_business_code_rule`。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/idempotency/IdempotencyRecordDO.java`与`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/idempotency/IdempotencyRecordMapper.java`：只封装`plt_idempotency_record`。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/outbox/OutboxEventDO.java`与`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/outbox/OutboxEventMapper.java`：只封装`plt_outbox_event`。
- `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/audit/OperationAuditDO.java`与`E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/audit/OperationAuditMapper.java`：只封装`plt_operation_audit`。

### PROJ与ACC

- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/ProjectTemplateRevisionSnapshot.java`：schemaVersion=1模板快照类型。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/template/ProjectTemplateCandidateService.java`：候选、唯一默认和预览。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationService.java`：唯一创建事务编排入口。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptance/AcceptanceDeliverableInitializationApi.java`：PROJ可调用的ACC内部边界。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance/AcceptanceDeliverableInitializationApiImpl.java`：`Propagation.MANDATORY`同步写入交付件。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/assignment/ServiceManagerAssignmentService.java`：服务经理时态指派与乐观锁。
- `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/ManualProjectController.java`、`E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/ProjectTemplateQueryController.java`：新Business API。
- 新DO/Mapper全部映射`proj_*`、`acc_*`；旧`ProjectTemplateServiceImpl.createProjectFromTemplate`不再作为新入口。

### 前端与验收

- `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/manual-project/index.ts`：新API类型与Header。
- `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/components/ManualProjectCreateDialog.vue`：内存表单、候选、预览、逐项错误。
- `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/index.vue`：只负责打开对话框和刷新列表。
- `E:/AICoding/Projects/NPDMS/tests/e2e/f-proj-001-manual-project.cjs`：真实浏览器闭环与存储负向检查。
- `E:/AICoding/Projects/NPDMS/tests/evidence/F-PROJ-001/README.md`：需求、AC、命令和证据索引，不保存秘密。

---

### Task 1: Lock the V1.8 Feature Baseline

**Files:**
- Modify: `E:/AICoding/Projects/NPDMS/docs/specification-baseline/allowlist.json`
- Modify: `E:/AICoding/Projects/NPDMS/scripts/tests/test_specification_baseline.py`
- Generated: `E:/AICoding/Projects/NPDMS/docs/specification-baseline/manifest.json`
- Create: `E:/AICoding/Projects/NPDMS/tasks/features/F-PROJ-001.md`

**Interfaces:**
- Consumes: 规格仓库包含本计划的已提交40位Git SHA。
- Produces: 本地只读Feature Spec与逐文件SHA-256锁；后续所有任务只读取该快照。

- [ ] **Step 1: Add a failing allowlist contract test**

```python
def test_f_proj_001_inputs_are_allowlisted(self) -> None:
    allowlist = load_allowlist(self.repo / "docs/specification-baseline/allowlist.json")
    paths = {item.path for item in allowlist}
    required = {
        "docs/baseline/prd-v1.8.md",
        "docs/baseline/prd-v1.8-amendment-001-no-manual-project-draft.md",
        "docs/decisions/0029-stage-task-work-binding-workbench.md",
        "docs/decisions/0030-project-task-execution-contract-and-cutover-checklist-carriers.md",
        "docs/decisions/0032-manual-project-creation-cross-context-atomicity.md",
        "specs/features/README.md",
        "specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md",
    }
    self.assertTrue(required <= paths)
```

- [ ] **Step 2: Run the focused test and verify failure**

Run: `py -3.13 -m unittest scripts.tests.test_specification_baseline.SpecificationBaselineTest.test_f_proj_001_inputs_are_allowlisted -v`

Expected: FAIL because the current allowlist locks PRD V1.7 and contains no Feature Spec.

- [ ] **Step 3: Add the six exact files to the sorted allowlist**

Use categories `BASELINE`, `DECISION`, and `FEATURE_SPEC`; do not add `docs/superpowers/` because the target policy excludes planning assets from managed snapshots.

- [ ] **Step 4: Synchronize from the committed spec SHA**

```powershell
$specRepo = 'M:/AICoding/CodexData/worktrees/09b5/项目交付平台'
$specCommit = git -C $specRepo rev-parse HEAD
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json --apply
py -3.13 scripts/validate_specification_baseline.py
```

Expected: preflight lists only reviewed ADD/REPLACE/KEEP entries; apply succeeds; validator reports PASS and a 40-character `source.commit`.

- [ ] **Step 5: Run the baseline test suite**

Run: `py -3.13 -m unittest scripts.tests.test_specification_baseline scripts.tests.test_repository_baseline_rules -v`

Expected: PASS.

- [ ] **Step 6: Generate the current Feature task definition**

Create `tasks/features/F-PROJ-001.md` with Requirement IDs PM-01/PM-03, the locked Feature Spec path/SHA-256, this Technical Plan path in the source repository, Start Gate checks, Tasks 2～10 identifiers, and unchecked acceptance items AC-FPROJ-001～010. It must state that legacy `tasks/plan.md` and `tasks/todo.md` are historical and cannot authorize this Feature.

- [ ] **Step 7: Commit only the baseline lock and current task definition**

```powershell
git add docs/specification-baseline/allowlist.json docs/specification-baseline/manifest.json scripts/tests/test_specification_baseline.py docs/baseline docs/decisions docs/design docs/engineering docs/traceability specs/features specs/001-project-delivery-platform tasks/features/F-PROJ-001.md
git commit -m "docs(spec): lock F-PROJ-001 implementation baseline"
```

---

### Task 2: Add the Feature-Forward Schema Contract

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/sql/migrations/V60__f_proj_001_manual_project_creation.sql`
- Create: `E:/AICoding/Projects/NPDMS/scripts/validate_f_proj_001_schema.py`
- Create: `E:/AICoding/Projects/NPDMS/scripts/tests/test_validate_f_proj_001_schema.py`

**Interfaces:**
- Consumes: Start Gate提供的六个正式核心表。
- Produces: `proj_project_template_revision`、`proj_project_stage_snapshot`、PLT事务支撑表，以及Feature所需列/唯一约束。

- [ ] **Step 1: Write a failing machine-readable schema validator test**

```python
REQUIRED_TABLES = {
    "proj_project",
    "proj_project_template_revision",
    "proj_project_template_task_definition",
    "proj_project_stage_snapshot",
    "proj_project_task",
    "proj_project_task_execution_contract",
    "proj_project_member_assignment",
    "acc_project_deliverable",
    "plt_business_code_rule",
    "plt_idempotency_record",
    "plt_operation_audit",
    "plt_outbox_event",
}

FORBIDDEN_TABLES = {"proj_project_creation_draft", "pms_project_creation_draft"}

def test_feature_contract_requires_atomic_carriers(self) -> None:
    contract = parse_ddl(MIGRATION)
    self.assertEqual(set(), REQUIRED_TABLES - contract.tables)
    self.assertEqual(set(), FORBIDDEN_TABLES & contract.tables)
```

- [ ] **Step 2: Run the validator test and verify failure**

Run: `py -3.13 -m unittest scripts.tests.test_validate_f_proj_001_schema -v`

Expected: FAIL because V60 does not exist.

- [ ] **Step 3: Create the exact template revision and stage snapshot carriers**

The migration must create these identities and constraints:

```sql
CREATE TABLE proj_project_template_revision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(255) NOT NULL,
  revision_no INT UNSIGNED NOT NULL,
  applicability_snapshot JSON NOT NULL,
  business_scene_code VARCHAR(64) NOT NULL,
  match_priority INT NOT NULL DEFAULT 0,
  default_flag TINYINT NOT NULL DEFAULT 0,
  workflow_definition_key VARCHAR(128) NOT NULL,
  workflow_definition_version INT UNSIGNED NOT NULL,
  definition_snapshot JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  effective_from DATETIME(3) NOT NULL,
  effective_to DATETIME(3) NULL,
  status VARCHAR(32) NOT NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  creator BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater BIGINT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_template_revision_tenant_row (tenant_id, id),
  UNIQUE KEY uk_project_template_revision (tenant_id, template_id, revision_no),
  UNIQUE KEY uk_project_template_revision_code (tenant_id, template_code, revision_no),
  KEY idx_project_template_revision_candidate (tenant_id, status, business_scene_code, match_priority),
  CONSTRAINT chk_project_template_revision_default CHECK (default_flag IN (0, 1)),
  CONSTRAINT chk_project_template_revision_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE proj_project_stage_snapshot (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  stage_code VARCHAR(32) NOT NULL,
  stage_name VARCHAR(128) NOT NULL,
  snapshot_no INT UNSIGNED NOT NULL,
  sort_order INT UNSIGNED NOT NULL,
  template_revision_id BIGINT NOT NULL,
  workflow_definition_key VARCHAR(128) NOT NULL,
  workflow_definition_version INT UNSIGNED NOT NULL,
  entry_rule_snapshot JSON NOT NULL,
  exit_rule_snapshot JSON NOT NULL,
  stage_status VARCHAR(32) NOT NULL,
  creator BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_stage_snapshot_tenant_row (tenant_id, id),
  UNIQUE KEY uk_project_stage_snapshot (tenant_id, project_id, stage_code, snapshot_no),
  KEY idx_project_stage_snapshot_navigation (tenant_id, project_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

`definition_snapshot.schemaVersion`必须为1，包含`stages`、`milestones`、`deliverables`和`gates`；TaskDefinition继续由`proj_project_template_task_definition`规范化保存。里程碑实例使用`proj_project_task.task_kind_code='MILESTONE'`，Gate使用执行契约中的`gate_ref`，不新增重复Milestone/Gate真值表。

- [ ] **Step 4: Add the transaction support tables and feature columns**

V60必须：

- 为`proj_project`增加`template_revision_id`、`workflow_definition_key`、`workflow_definition_version`、`current_stage_code`、`assignment_status`、`create_reason`；生命周期继续使用正式`status`列并保存`ACTIVE`。
- 为`proj_project_task`增加`stage_definition_key`、`task_definition_key`、`task_kind_code`、`milestone_definition_key`、`template_task_definition_id`、`status_machine_version`。
- 为`acc_project_deliverable`增加`template_requirement_key`、`source_template_revision_id`、`applicable_stage_code`、`required_flag`，并增加`uk(tenant_id, project_id, source_template_revision_id, template_requirement_key)`。
- 创建`plt_business_code_rule`，唯一键`(tenant_id, rule_code, rule_version)`，当前有效规则使用生成列唯一约束；规则字段为`prefix`、`padding_width`、`next_value`、`status`。不在迁移中硬编码生产前缀，测试fixture使用`MP`、宽度8、版本`V1`。
- 创建`plt_idempotency_record`，唯一键`(tenant_id, scope_code, actor_id, idempotency_key)`，保存`request_sha256`、`status`、`response_json`、`resource_id`。
- 创建追加写`plt_operation_audit`，保存tenant、actor、operation、resource、decision、redacted detail、correlationId和时间。
- 创建`plt_outbox_event`，`event_id`全局唯一，保存aggregate、event type/version、payload、publish state和重试水位。

- [ ] **Step 5: Execute the migration on an isolated MySQL 8.4 database**

```powershell
$env:COMPOSE_PROJECT_NAME='npms-f-proj-001'
$env:NPDMS_DB_NAME='npms'
$env:NPDMS_MYSQL_PORT='13316'
$env:NPDMS_REDIS_PORT='16389'
docker compose up -d mysql
docker compose run --rm migrate
py -3.13 scripts/validate_f_proj_001_schema.py --compose-service mysql --database npms
```

Expected: Flyway through V60 succeeds; validator reports all required tables/columns/indexes present, forbidden draft carriers absent, and no `pms_*` Feature write carrier.

- [ ] **Step 6: Re-run Flyway to prove repeat startup safety**

Run: `docker compose run --rm migrate`

Expected: no pending migration, no checksum drift, exit 0.

- [ ] **Step 7: Commit schema and validator**

```powershell
git add sql/migrations/V60__f_proj_001_manual_project_creation.sql scripts/validate_f_proj_001_schema.py scripts/tests/test_validate_f_proj_001_schema.py
git commit -m "feat(project): add F-PROJ-001 schema carriers"
```

---

### Task 3: Implement Transactional PLT Support

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/businesscode/BusinessCodeApi.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/businesscode/dto/BusinessCodeAllocation.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/idempotency/TransactionalIdempotencyApi.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/idempotency/dto/IdempotencyDecision.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/outbox/TransactionalOutboxApi.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/outbox/dto/OutboxAppendCommand.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/audit/BusinessAuditApi.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/audit/dto/BusinessAuditCommand.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/businesscode/BusinessCodeService.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/businesscode/BusinessCodeServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/businesscode/BusinessCodeRuleDO.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/businesscode/BusinessCodeRuleMapper.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/idempotency/TransactionalIdempotencyService.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/idempotency/TransactionalIdempotencyServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/idempotency/IdempotencyRecordDO.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/idempotency/IdempotencyRecordMapper.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/outbox/TransactionalOutboxService.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/outbox/TransactionalOutboxServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/outbox/OutboxEventDO.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/outbox/OutboxEventMapper.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/audit/BusinessAuditService.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/audit/BusinessAuditServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/audit/OperationAuditDO.java`
- Create: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/mysql/audit/OperationAuditMapper.java`
- Test: `E:/AICoding/Projects/NPDMS/yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/transaction/TransactionalBusinessSupportIntegrationTest.java`

**Interfaces:**
- Produces:

```java
public interface BusinessCodeApi {
    BusinessCodeAllocation allocate(long tenantId, String ruleCode);
}

public record BusinessCodeAllocation(String code, String ruleVersion) {}

public interface TransactionalIdempotencyApi {
    IdempotencyDecision begin(long tenantId, long actorId, String scopeCode,
                              String idempotencyKey, String requestSha256);
    void complete(long recordId, long resourceId, String responseJson);
}

public record IdempotencyDecision(
        Mode mode, long recordId, Long resourceId, String responseJson) {
    public enum Mode { OWNER, REPLAY }
    public boolean isOwner() { return mode == Mode.OWNER; }
    public boolean isReplay() { return mode == Mode.REPLAY; }
}

public interface TransactionalOutboxApi {
    void append(OutboxAppendCommand command);
}

public record OutboxAppendCommand(
        String eventId, long tenantId, String aggregateType, long aggregateId,
        String eventType, int eventVersion, String payloadJson) {}

public interface BusinessAuditApi {
    void appendSuccess(BusinessAuditCommand command);
    void appendFailureAfterRollback(BusinessAuditCommand command);
}

public record BusinessAuditCommand(
        long tenantId, Long actorId, String operationCode, String resourceType,
        Long resourceId, String decisionCode, String correlationId,
        String redactedDetailJson) {}
```

- [ ] **Step 1: Write failing allocation and idempotency integration tests**

```java
@Test
void allocateAndComplete_areTenantScopedAndReplayable() {
    BusinessCodeAllocation code = businessCodeApi.allocate(1L, "PROJECT_MANUAL");
    assertEquals("MP00000001", code.code());
    assertEquals("V1", code.ruleVersion());

    IdempotencyDecision first = idempotencyApi.begin(1L, 9L, "POST:/api/v1/pms/projects", "K1", "H1");
    assertTrue(first.isOwner());
    idempotencyApi.complete(first.recordId(), 100L, "{\"projectId\":100}");

    IdempotencyDecision replay = idempotencyApi.begin(1L, 9L, "POST:/api/v1/pms/projects", "K1", "H1");
    assertTrue(replay.isReplay());
    assertEquals(100L, replay.resourceId());
}
```

- [ ] **Step 2: Run the integration test and verify failure**

Run: `mvn.cmd -pl yudao-module-system -am -Ppms-test-integration -Dtest=TransactionalBusinessSupportIntegrationTest test`

Expected: FAIL because the APIs and services do not exist.

- [ ] **Step 3: Implement deterministic code allocation**

Use `SELECT ... FOR UPDATE` on the single enabled `PROJECT_MANUAL` rule row, increment `next_value` in the same transaction, format `prefix + leftPad(sequence, paddingWidth, '0')`, and return the frozen `rule_version`. Missing or multiple active rules must throw `BUSINESS_CODE_RULE_UNAVAILABLE`; do not fall back to timestamp/random/client code.

- [ ] **Step 4: Implement database idempotency without a visible processing state**

`begin` must use `INSERT IGNORE` followed by `SELECT ... FOR UPDATE`. Because the row is uncommitted until the whole project transaction commits, concurrent equal requests block and then replay the committed response; rollback removes the reservation. Same key with another SHA returns `PMS-COMMON-IDEMPOTENCY-0001`.

- [ ] **Step 5: Implement success audit/outbox as mandatory transaction participants**

Annotate code allocation, idempotency, success audit, and outbox methods with:

```java
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
```

`appendFailureAfterRollback` alone uses `REQUIRES_NEW`; it may record a redacted failed attempt after all business rows have rolled back. It must never create or update Project, Deliverable, success idempotency, or Outbox rows.

- [ ] **Step 6: Add rollback and hash-conflict tests**

Verify a thrown exception removes allocated code sequence increment, idempotency reservation, success audit and outbox in the same transaction; verify failure audit remains with `decision=FAILED` and no resource ID.

- [ ] **Step 7: Run PLT tests**

Run: `mvn.cmd -pl yudao-module-system -am -Ppms-test-integration -Dtest=TransactionalBusinessSupportIntegrationTest test`

Expected: PASS.

- [ ] **Step 8: Commit PLT support**

```powershell
git add yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/transaction
git commit -m "feat(platform): add transactional project creation support"
```

---

### Task 4: Implement Published Template Candidate and Preview Queries

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/ProjectTemplateRevisionSnapshot.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/template/ProjectTemplateCandidateService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/template/ProjectTemplateCandidateServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttemplate/ProjectTemplateRevisionDO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttemplate/ProjectTemplateRevisionMapper.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/ProjectTemplateQueryController.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/vo/ProjectTemplateCandidateReqVO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/vo/ProjectTemplateCandidateRespVO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/vo/ProjectTemplatePreviewRespVO.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/template/ProjectTemplateCandidateServiceTest.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template/ProjectTemplateQueryControllerContractTest.java`

**Interfaces:**
- Produces:

```java
public record TemplateMatchCriteria(
        String signingMethodCode,
        String projectCategoryCode,
        String implementationModeCode,
        String majorProjectLevelCode,
        String businessSceneCode,
        long customerId,
        long officeId,
        long implementationLocationId) {}

public interface ProjectTemplateCandidateService {
    List<TemplateCandidate> findCandidates(long tenantId, long actorId, TemplateMatchCriteria criteria);
    ProjectTemplateRevisionSnapshot getPreview(long tenantId, long actorId, long revisionId,
                                                TemplateMatchCriteria criteria);
    ProjectTemplateRevisionSnapshot resolveForCreate(long tenantId, long actorId, Long selectedRevisionId,
                                                      TemplateMatchCriteria criteria, String candidateWatermark);
}
```

- [ ] **Step 1: Write failing candidate rule tests**

Cover exact tenant, `status=PUBLISHED`, effective time, all four independent dimensions, business scene, authorization scope, explicit revision revalidation, one default, zero default, and two equal-priority defaults.

```java
@Test
void resolveForCreate_rejectsTwoDefaults() {
    seedPublishedDefault(101L, 10);
    seedPublishedDefault(102L, 10);
    assertThrows(ProjectTemplateAmbiguousException.class,
            () -> service.resolveForCreate(1L, 9L, null, criteria(), "W1"));
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-unit -Dtest=ProjectTemplateCandidateServiceTest test`

Expected: FAIL because the candidate service is absent.

- [ ] **Step 3: Implement schemaVersion=1 snapshot parsing and validation**

`ProjectTemplateRevisionSnapshot` must deserialize immutable lists of stages, milestones, deliverable requirements and gates. Each TaskDefinition is loaded from `proj_project_template_task_definition`. Reject unknown schemaVersion, duplicate keys, missing S0, missing parent task, invalid GateRef, `TASK_NATIVE` with external target, non-native binding without a target, or unparseable CompletionRule.

- [ ] **Step 4: Implement candidate resolution**

Treat each applicability condition as an explicit schema-versioned code set; the set must contain the supplied code. For a manual project with no CRM major level, match the explicit `NOT_APPLICABLE` code. Never infer one dimension from another. Compute `candidateWatermark=sha256(sorted(revisionId + contentSha256))` and re-read all revisions during create.

- [ ] **Step 5: Implement GET contracts**

- `GET /api/v1/pms/project-templates` returns only candidate summaries and watermark.
- `GET /api/v1/pms/project-templates/{revisionId}` returns Stage→Task, milestone, deliverable and gate summaries without scripts, Repository names, external business正文 or secrets.
- Both require `pms:project:create`; template maintenance permission alone is insufficient.

- [ ] **Step 6: Run unit and contract tests**

```powershell
mvn.cmd -pl pms-module-project -am -Ppms-test-unit -Dtest=ProjectTemplateCandidateServiceTest test
mvn.cmd -pl pms-module-project -am -Ppms-test-contract -Dtest=ProjectTemplateQueryControllerContractTest test
```

Expected: PASS.

- [ ] **Step 7: Commit template query slice**

```powershell
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/template pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/template pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/template
git commit -m "feat(project): add published template candidate queries"
```

---

### Task 5: Implement the ACC Mandatory-Transaction Boundary

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptance/AcceptanceDeliverableInitializationApi.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptance/dto/DeliverableInitializationCommand.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptance/dto/DeliverableInitializationResult.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance/AcceptanceDeliverableInitializationApiImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptance/ProjectDeliverableDO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance/ProjectDeliverableMapper.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptance/AcceptanceDeliverableInitializationIntegrationTest.java`

**Interfaces:**
- Produces:

```java
public interface AcceptanceDeliverableInitializationApi {
    DeliverableInitializationResult initialize(DeliverableInitializationCommand command);
}

public record DeliverableRequirementSnapshot(
        String requirementKey, Long deliverableTemplateId, String deliverableType,
        String applicableStageCode, boolean required) {}

public record DeliverableInitializationCommand(
        long tenantId, long projectId, long templateRevisionId,
        List<DeliverableRequirementSnapshot> requirements) {}

public record DeliverableInitializationResult(int createdCount, List<Long> deliverableIds) {}
```

- [ ] **Step 1: Write a failing no-transaction test**

```java
@Test
void initialize_withoutCallerTransaction_isRejected() {
    assertThrows(IllegalTransactionStateException.class,
            () -> api.initialize(commandWithTwoRequirements()));
}
```

- [ ] **Step 2: Write failing all-or-nothing tests**

Inside a caller transaction, assert two requirements create exactly two rows. Inject a mapper failure on the second row and assert zero rows remain after rollback. Replaying the same project/revision/requirement keys must return the existing IDs without duplicates.

- [ ] **Step 3: Run the focused test and verify failure**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=AcceptanceDeliverableInitializationIntegrationTest test`

Expected: FAIL because the ACC boundary is absent.

- [ ] **Step 4: Implement the mandatory transaction service**

```java
@Override
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public DeliverableInitializationResult initialize(DeliverableInitializationCommand command) {
    List<Long> ids = new ArrayList<>();
    for (DeliverableRequirementSnapshot item : command.requirements()) {
        ids.add(insertOrLoad(command, item));
    }
    if (ids.size() != command.requirements().size()) {
        throw new IllegalStateException("deliverable initialization count mismatch");
    }
    return new DeliverableInitializationResult(ids.size(), List.copyOf(ids));
}
```

Every new row uses business status `PENDING`, meaning the initialized deliverable awaits future submission. The API exposes no initialization status and catches no persistence exception.

- [ ] **Step 5: Run ACC tests**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=AcceptanceDeliverableInitializationIntegrationTest test`

Expected: PASS.

- [ ] **Step 6: Commit the ACC boundary**

```powershell
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptance pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptance pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptance
git commit -m "feat(acceptance): add atomic deliverable initialization boundary"
```

---

### Task 6: Implement the Manual Project Creation Transaction

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationFacade.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreateCommand.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ServiceManagerConfirmation.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationResult.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ProjectCreationAuthorizationService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation/ProjectReferenceValidationService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/project/ProjectStageSnapshotDO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttask/ProjectTaskExecutionContractDO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/project/ProjectStageSnapshotMapper.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttask/ProjectTaskExecutionContractMapper.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/project/ProjectDO.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/project/ProjectMapper.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttask/ProjectTaskDO.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttask/ProjectTaskMapper.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationServiceTest.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/creation/ManualProjectCreationIntegrationTest.java`

**Interfaces:**
- Produces:

```java
public record ManualProjectCreateCommand(
        String projectName, long customerId, String contractCode,
        long officeId, long implementationLocationId,
        String signingMethodCode, String projectCategoryCode,
        String implementationModeCode, String majorProjectLevelCode,
        String businessSceneCode, String createReason,
        Long templateRevisionId, String candidateWatermark,
        ServiceManagerConfirmation serviceManager) {}

public record ManualProjectCreationResult(
        long projectId, String projectCode, String lifecycleStatus,
        String currentStage, String assignmentStatus,
        long templateRevisionId, String workflowDefinitionKey,
        int workflowDefinitionVersion, int stageCount, int taskCount,
        int milestoneCount, int deliverableCount, int gateCount,
        int projectVersion, String detailUrl) {}

public interface ManualProjectCreationService {
    ManualProjectCreationResult create(long tenantId, long actorId,
                                       String idempotencyKey, ManualProjectCreateCommand command);
}

public interface ProjectCreationAuthorizationService {
    void checkCreate(long tenantId, long actorId, long customerId,
                     long officeId, long implementationLocationId);
}

public interface ProjectReferenceValidationService {
    void validate(long tenantId, long customerId, String contractCode,
                  long officeId, long implementationLocationId);
}

public record ServiceManagerConfirmation(
        long userId, String roleCode, String responsibilityScopeCode,
        long responsibilityScopeId, LocalDateTime effectiveFrom) {}

public interface ManualProjectCreationFacade {
    ManualProjectCreationResult create(long tenantId, long actorId,
                                       String idempotencyKey, ManualProjectCreateCommand command);
}
```

- [ ] **Step 1: Write failing orchestration unit tests**

Assert call order and negative exits:

```text
permission/master-data validation
-> request SHA + idempotency begin
-> template re-read and resolution
-> platform code allocation
-> Project ACTIVE/S0/UNASSIGNED insert
-> all stage snapshots
-> all ProjectTask rows and one current execution contract per task
-> ACC synchronous deliverable initialize
-> optional SERVICE_MANAGER assignment
-> success audit + ProjectCreated outbox
-> idempotency complete
```

No downstream write may execute after any failed validation.

- [ ] **Step 2: Run unit tests and verify failure**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-unit -Dtest=ManualProjectCreationServiceTest test`

Expected: FAIL because the creation service is absent.

- [ ] **Step 3: Implement the single transaction entry**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public ManualProjectCreationResult create(long tenantId, long actorId,
                                          String key, ManualProjectCreateCommand command) {
    String requestHash = canonicalRequestSha256(command);
    IdempotencyDecision idem = idempotencyApi.begin(tenantId, actorId, CREATE_SCOPE, key, requestHash);
    if (idem.isReplay()) {
        return jsonCodec.read(idem.responseJson(), ManualProjectCreationResult.class);
    }
    ProjectTemplateRevisionSnapshot template = templateService.resolveForCreate(
            tenantId, actorId, command.templateRevisionId(), command.toCriteria(), command.candidateWatermark());
    BusinessCodeAllocation code = businessCodeApi.allocate(tenantId, "PROJECT_MANUAL");
    ProjectDO project = insertProject(command, template, code, tenantId, actorId);
    InstanceCounts counts = instantiateProjectOwnedFacts(project, template, actorId);
    DeliverableInitializationResult deliverables = acceptanceApi.initialize(
            toDeliverableCommand(project, template));
    ManualProjectCreationResult result = buildResult(project, template, counts, deliverables);
    auditApi.appendSuccess(toSuccessAudit(project, result, key));
    outboxApi.append(newProjectCreatedEvent(project, result));
    idempotencyApi.complete(idem.recordId(), project.getId(), jsonCodec.write(result));
    return result;
}
```

On exception, let Spring roll back. `ManualProjectCreationFacade` is non-transactional; it calls the transactional service, catches only to call `appendFailureAfterRollback` with redacted inputs, then rethrows the original classified exception.

- [ ] **Step 4: Implement instance mapping invariants**

- Create one stage snapshot per template stage; S0 uses`stage_status=ACTIVE`, later stages use`NOT_STARTED`.
- Create one ProjectTask per TaskDefinition; milestone definitions become tasks with`task_kind_code=MILESTONE`.
- Create exactly one current `proj_project_task_execution_contract` per task with `contract_version=1` and `effective_to=NULL`.
- `TASK_NATIVE` target fields are null; other bindings copy only approved stable reference/parameter snapshots.
- Gate definitions are frozen through stage exit rules and each execution contract `gate_ref`; no second gate table.
- The ACC result count must equal the template deliverable requirement count before success audit/outbox/idempotency complete.

- [ ] **Step 5: Write MySQL integration rollback tests**

Parameterize failures at Project, stage 2, task 2, execution contract 2, ACC deliverable 2, success audit, outbox, and idempotency complete. After each failure assert zero rows for the generated project code across Project, stage, task, contract, deliverable, success audit, outbox and completed idempotency.

- [ ] **Step 6: Add concurrency tests**

- Two concurrent identical requests with the same key return the same Project ID and create one Project.
- Same key with another request SHA returns`PMS-COMMON-IDEMPOTENCY-0001`.
- Template deactivated between preview and create returns version/business gate conflict and creates no facts.
- Database project-code unique collision retries code allocation only within the same operation and never changes a committed project code.

- [ ] **Step 7: Run creation tests**

```powershell
mvn.cmd -pl pms-module-project -am -Ppms-test-unit -Dtest=ManualProjectCreationServiceTest test
mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=ManualProjectCreationIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit the creation transaction**

```powershell
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/creation pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/creation
git commit -m "feat(project): create projects atomically from templates"
```

---

### Task 7: Publish the Business API and Authorization Contract

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/ManualProjectController.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/vo/ManualProjectCreateReqVO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/vo/ManualProjectCreateRespVO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/vo/ManualProjectDetailRespVO.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/project/ProjectDetailQueryService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/project/ProjectDetailQueryServiceImpl.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/project/ProjectMapper.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/ManualProjectControllerContractTest.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/ManualProjectAuthorizationIntegrationTest.java`

**Interfaces:**
- Produces:
  - `POST /api/v1/pms/projects`
  - `GET /api/v1/pms/projects/{id}`

- [ ] **Step 1: Write failing controller contract tests**

Verify missing `Idempotency-Key` returns validation error; valid POST returns all `ManualProjectCreationResult` fields; no response field contains `draftId` or `deliverableInitializationStatus`; detail includes frozen versions, instance counts, service manager summary, audit summary and server-calculated `allowedActions`.

- [ ] **Step 2: Write authorization negative tests**

Test unauthenticated, ordinary member, template maintainer without project-create permission, unauthorized office/location, and cross-tenant request. All must create zero Project/Deliverable facts and must not reveal whether a foreign template/project exists.

- [ ] **Step 3: Implement the controller**

```java
@PostMapping("/api/v1/pms/projects")
@PreAuthorize("@ss.hasPermission('pms:project:create')")
public CommonResult<ManualProjectCreateRespVO> create(
        @RequestHeader("Idempotency-Key") @NotBlank String key,
        @Valid @RequestBody ManualProjectCreateReqVO body) {
    long actorId = SecurityFrameworkUtils.getLoginUserId();
    long tenantId = TenantContextHolder.getRequiredTenantId();
    return success(BeanUtils.toBean(creationFacade.create(tenantId, actorId, key, body.toCommand()),
            ManualProjectCreateRespVO.class));
}
```

Controller accepts no client project code, lifecycle status, current stage, assignment status, template status or matching result.

- [ ] **Step 4: Protect generic update endpoints**

Remove lifecycle/template/source/assignment fields from generic update VO mapping. Existing legacy endpoints must either delegate to the formal application service or be denied for new writes; they may not update status fields directly.

- [ ] **Step 5: Run contract and authorization tests**

```powershell
mvn.cmd -pl pms-module-project -am -Ppms-test-contract -Dtest=ManualProjectControllerContractTest test
mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=ManualProjectAuthorizationIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit API and authorization**

```powershell
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project
git commit -m "feat(project): publish atomic manual creation API"
```

---

### Task 8: Implement V1 Service Manager Confirmation

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/assignment/ServiceManagerAssignmentService.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/assignment/ServiceManagerAssignmentServiceImpl.java`
- Create: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/vo/ServiceManagerAssignReqVO.java`
- Modify: `E:/AICoding/Projects/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project/ManualProjectController.java`
- Test: `E:/AICoding/Projects/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/assignment/ServiceManagerAssignmentIntegrationTest.java`

**Interfaces:**
- Produces:

```java
AssignmentResult assignServiceManager(long tenantId, long actorId, long projectId,
                                      int expectedProjectVersion, String idempotencyKey,
                                      ServiceManagerAssignmentCommand command);
```

Endpoint: `POST /api/v1/pms/projects/{id}/actions/assign-manager`, Headers `Idempotency-Key` and `If-Match`.

- [ ] **Step 1: Write failing temporal assignment tests**

Verify legal office/location scope, role codes `SERVICE_MANAGER_LEVEL_1` and `SERVICE_MANAGER_LEVEL_2`, effective interval history, duplicate replay, stale `If-Match`, unauthorized actor and cross-tenant target. Confirming only service manager keeps `assignment_status=UNASSIGNED` until every PRD-required primary assignment exists.

- [ ] **Step 2: Run and verify failure**

Run: `mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=ServiceManagerAssignmentIntegrationTest test`

Expected: FAIL because the service is absent.

- [ ] **Step 3: Implement append/close temporal assignments**

Lock Project by tenant/id/version, close the previous active assignment by setting`effective_to`, insert the new row, increment Project version, recompute assignment projection without inferring candidates, append success audit, and complete the action idempotency record in one transaction.

- [ ] **Step 4: Implement the action endpoint**

Require `pms:project:assign`; parse `If-Match` as an integer Project version; return the new Project version, assignment relation ID and current `assignmentStatus`.

- [ ] **Step 5: Run tests and commit**

```powershell
mvn.cmd -pl pms-module-project -am -Ppms-test-integration -Dtest=ServiceManagerAssignmentIntegrationTest test
git add pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/assignment pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/v1/project pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/assignment
git commit -m "feat(project): add V1 service manager confirmation"
```

---

### Task 9: Replace the Legacy Create Dialog with the Atomic UI Flow

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/manual-project/index.ts`
- Create: `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/components/ManualProjectCreateDialog.vue`
- Modify: `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project/index.vue`
- Modify: `E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-template/index.ts` to remove the legacy create call from consumers

**Interfaces:**
- Consumes: Tasks 4、7、8 APIs.
- Produces: memory-only creation form and template preview; successful create navigates to formal detail.

- [ ] **Step 1: Define exact TypeScript contracts**

```ts
export interface ManualProjectCreateRequest {
  projectName: string
  customerId: number
  contractCode?: string
  officeId: number
  implementationLocationId: number
  signingMethodCode: string
  projectCategoryCode: string
  implementationModeCode: string
  majorProjectLevelCode: 'NOT_APPLICABLE'
  businessSceneCode: string
  createReason: string
  templateRevisionId?: number
  candidateWatermark: string
  serviceManager?: ServiceManagerConfirmation
}

export const createManualProject = (data: ManualProjectCreateRequest, idempotencyKey: string) =>
  request.post({ url: '/api/v1/pms/projects', data, headers: { 'Idempotency-Key': idempotencyKey } })
```

- [ ] **Step 2: Run TypeScript check and verify failure**

Run: `pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 ts:check`

Expected: FAIL until component imports and new types are complete.

- [ ] **Step 3: Implement the memory-only dialog**

Use component-local `reactive` state only. Do not import Pinia persistence, `localStorage`, `sessionStorage`, `IndexedDB`, service worker cache or offline form utilities. Candidate fields trigger `GET /project-templates`; preview shows Stage→Task, milestones, deliverables and gates. Submit creates one UUID idempotency key; after any business validation error, show field/item errors and generate a new key for the corrected submit.

- [ ] **Step 4: Remove the legacy client-generated identity path**

The dialog must not ask for project code, source system, source business key, lifecycle status or project manager. Stop calling `/pms/project-template/create-project` and `/pms/project/create` from the manual-create button.

- [ ] **Step 5: Handle all-or-nothing responses**

Only a successful `POST /api/v1/pms/projects` closes the dialog, refreshes the list and opens detail. Any HTTP/network/business failure keeps the in-memory form open and displays that no project was created; the UI must not show an initialization progress state or poll for deliverables.

- [ ] **Step 6: Run frontend checks**

```powershell
pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 ts:check
pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 lint:eslint:check
pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 build:local
```

Expected: PASS.

- [ ] **Step 7: Commit UI slice**

```powershell
git add yudao-ui/yudao-ui-admin-vue3/src/api/pms/project yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project
git commit -m "feat(project-ui): add atomic manual project creation"
```

---

### Task 10: Prove Atomicity, Browser Behavior, and Traceability

**Files:**
- Create: `E:/AICoding/Projects/NPDMS/tests/e2e/f-proj-001-manual-project.cjs`
- Create: `E:/AICoding/Projects/NPDMS/tests/evidence/F-PROJ-001/README.md`
- Modify: `E:/AICoding/Projects/NPDMS/docs/traceability/requirement-matrix.md`
- Modify: `E:/AICoding/Projects/NPDMS/tasks/features/F-PROJ-001.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: AC-FPROJ-001～010 evidence and implementation-ready report.

- [ ] **Step 1: Create deterministic local acceptance fixtures**

Insert only test-environment data: one published revision matching manual inputs, schemaVersion=1 snapshot, S0～S6 stages, TASK_NATIVE task definitions with valid completion rules, one milestone task, two deliverable requirements, one GateRef, `PROJECT_MANUAL` code rule`MP/8/V1`, authorized and unauthorized users, office/location scope. Fixture data must be outside Flyway production migrations and removed after the test.

- [ ] **Step 2: Run the consolidated backend suite**

```powershell
mvn.cmd -pl yudao-module-system,pms-module-project -am -Ppms-test-unit test
mvn.cmd -pl yudao-module-system,pms-module-project -am -Ppms-test-integration test
mvn.cmd -pl pms-module-project -am -Ppms-test-contract test
```

Expected: PASS with real MySQL integration profile for transactional tests.

- [ ] **Step 3: Run schema, baseline, and forbidden-semantic scans**

```powershell
py -3.13 scripts/validate_specification_baseline.py
py -3.13 scripts/validate_f_proj_001_schema.py --compose-service mysql --database npms
rg -n "ProjectCreationDraft|project-creation-drafts|deliverableInitializationStatus|INITIALIZING" pms-module-project yudao-module-system yudao-ui sql/migrations
rg -n "pms_project-template/create-project|/pms/project/create" yudao-ui/yudao-ui-admin-vue3/src
```

Expected: both validators PASS; both scans have zero matches in active Feature paths.

- [ ] **Step 4: Execute real-browser acceptance**

Start MySQL/Redis/Flyway, backend on58080 and frontend on18081. Run:

```powershell
node tests/e2e/f-proj-001-manual-project.cjs --base-url http://127.0.0.1:18081
```

The script must assert:

- candidate filtering and preview content;
- explicit revision and unique-default creation;
- one Project with`ACTIVE/S0/UNASSIGNED`;
- exact stage/task/milestone/deliverable/gate/contract counts;
- same-key replay and different-payload conflict;
- ordinary member, cross-tenant and unauthorized office rejection;
- injected ACC second-row failure leaves zero Project and zero Deliverable rows;
- form values remain before refresh after failure, disappear after refresh, and no matching keys exist in localStorage/sessionStorage/IndexedDB;
- service manager confirmation preserves temporal history and does not falsely mark all assignments complete;
- page body has no error boundary text, console has no unexpected error, and no failed request remains unexplained.

- [ ] **Step 5: Record evidence mapped to requirements and ACs**

`tests/evidence/F-PROJ-001/README.md` must list command, timestamp, Git SHA, browser/version, database migration version, test result, screenshot/trace paths and exact mapping to PM-01/PM-03 plus AC-FPROJ-001～010. Do not store credentials, tokens, customer secrets or raw unredacted payloads.

- [ ] **Step 6: Run final repository checks**

```powershell
git diff --check
git status --short
py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v
pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 ts:check
```

Expected: formatting clean; only reviewed Feature/evidence changes remain; Python suite and TypeScript check PASS.

- [ ] **Step 7: Perform a fresh-context self-review**

Review every Feature section and point it to a task/test. Specifically re-check synchronous ACC `MANDATORY` propagation, absence of initialization intermediate state, no Project draft persistence, tenant/auth negative paths, immutable template revision, one current task contract, failure rollback, no direct ACC Repository access, and no legacy `pms_*` write path.

- [ ] **Step 8: Commit evidence and traceability**

```powershell
git add tests/e2e/f-proj-001-manual-project.cjs tests/evidence/F-PROJ-001 docs/traceability tasks/features/F-PROJ-001.md
git commit -m "test(project): prove F-PROJ-001 atomic creation"
```

---

## Coverage Matrix

| Feature evidence | Plan task |
|---|---|
| BR-FPROJ-001、权限与来源 | 4、7、10 |
| BR-FPROJ-002项目编码 | 2、3、6、10 |
| BR-FPROJ-003候选/唯一默认 | 4、9、10 |
| BR-FPROJ-004冻结与WorkBinding | 2、4、6、10 |
| BR-FPROJ-005同步全有或全无 | 3、5、6、10 |
| BR-FPROJ-006服务经理人工确认 | 8、9、10 |
| BR-FPROJ-007失败无持久化草稿 | 6、9、10 |
| AC-FPROJ-001～004 | 4、6、9、10 |
| AC-FPROJ-005～007 | 7、8、9、10 |
| AC-FPROJ-008同步原子失败 | 3、5、6、10 |
| AC-FPROJ-009真实界面 | 9、10 |
| AC-FPROJ-010失败无持久化 | 6、9、10 |

## Plan Self-Review Result

- Spec coverage：PM-01、PM-03、全部业务规则和AC均已映射到任务与验证命令。
- Context boundary：PROJ只依赖ACC内部API；计划没有跨Context Repository访问。
- Transaction boundary：ACC接口为`MANDATORY`；成功审计、Outbox与幂等完成同事务；失败审计明确不形成业务中间状态。
- Type consistency：`ManualProjectCreateCommand`、`ManualProjectCreationResult`、`DeliverableInitializationCommand`和HTTP/TypeScript字段名一致。
- Scope control：CRM/ERP同步、PM-07自动分类、PM-08 V2自动指派、模板维护后台、S0后推进、历史迁移、UAT与生产发布未混入。
- Execution readiness：计划内容完成；实施仍受Start Gate约束，当前NPDMS脏工作区和未落地的核心`proj_*`割接不满足启动条件。
