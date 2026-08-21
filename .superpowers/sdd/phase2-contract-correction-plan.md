# Phase 2 Contract Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 纠正 Phase 2 正式契约中的 V3/工单范围漂移、需求数量与状态漂移及迁移证据绑定错误，使 Phase 2 重新具备可复核的 103 项 V1/V2 契约基线。

**Architecture:** 以 PRD V1.7 为唯一业务语义来源，先机械修正统计和范围，再加固独立校验器，最后根据需求方对历史工单/工时承载方式的明确决定收口 API、文件和迁移边界。当前未决项只阻断相关历史查询能力，不阻断其他纠偏。

**Tech Stack:** Markdown/YAML/JSON 规格资产，Python 3 校验器与 unittest，Git 对象读取。

## Global Constraints

- 禁止读取 Excel/XLSX 和 `需求/割接0807需求分析报告.md`。
- PRD V1.7 业务语义优先；不得恢复 WO、打卡、工时、EQP-06、RPT-01、RPT-04 到 V1/V2。
- 不新增角色、审批、状态、阈值、Owner、API 或迁移对象。
- 过程材料只放 `.superpowers/sdd/`，不得混入正式 `docs/design/`。
- 每个任务先写失败测试，再最小修改，再运行定点与全量相关校验；每个任务独立提交且不推送。
- 历史迁移执行、生产切换、真实端点、KMS、容量、SIT/UAT 均保持后续门禁，不前置阻断当前 SDS 纠偏。

---

### Task 1: 清除当前 Phase 2 的范围与统计漂移

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/baseline/prd-v1.7.md`
- Modify: `docs/baseline/requirement-baseline.yaml`
- Modify: `docs/baseline/baseline-signoff.md`
- Modify: `docs/traceability/business-feedback-change-map.md`
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/08a-domain-entity-migration-alignment.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/11-event-design.md`
- Modify: `docs/design/12-integration-design.md`
- Modify: `docs/design/16-exception-and-idempotency.md`
- Modify: `docs/engineering/gates/phase-1/gate-status.md`
- Modify: `docs/engineering/gates/phase-2/gate-status.md`
- Modify: `docs/engineering/gates/phase-2/self-review.md`
- Test: `scripts/tests/test_validate_prd_baseline.py`
- Test: `scripts/tests/test_validate_sds_phase2.py`

**Interfaces:**
- Consumes: PRD V1.7 的 103 个 V1/V2 Requirement、30 个 V3、9 个 OUT_OF_SCOPE。
- Produces: 55 个 V1、48 个 V2、103 个正式 Requirement 的统一口径；Phase 2 临时回落为 `IN_REVIEW / NOT_READY_FOR_PHASE_3`，等待本计划复审收口。

- [ ] **Step 1: 写统计和范围负向测试**

  在 `test_validate_prd_baseline.py` 中构造 A.2 `V2主版本需求=49` 的副本并断言失败；在 `test_validate_sds_phase2.py` 中分别注入 `EQP-06`、`RPT-01`、`RPT-04`、`ProjectConversionCompleted -> WO`、`钉钉打卡原始事实` 并断言失败。

- [ ] **Step 2: 运行负向测试确认红灯**

  Run: `python -m unittest scripts.tests.test_validate_prd_baseline scripts.tests.test_validate_sds_phase2 -v`

  Expected: 新增用例因校验缺失而 FAIL。

- [ ] **Step 3: 最小修正 PRD 统计与正式范围**

  将两份 PRD A.2 的 V2 数量从 49 改为 48；重算并同步 PRD SHA-256。移除当前 SDS 中 V3 Requirement 与 WO/打卡事实的 V1/V2 适用声明；钉钉仅保留平台待办/通知与送达回执。不要删除 CUT-05 专项提前时间规则。

- [ ] **Step 4: 校准 Phase 1/2 状态与数量文字**

  将 104 改为 103、88对象/100来源改为当前机器事实 84对象/95来源/1排除源；清除 `08a` 的 `IN_REVIEW` 与已删除历史对象残留。把仍需 Feature 或 Phase 3 落地的参数标为 `DEFERRED_TO_FEATURE_INTEGRATION` 或 `DEFERRED_TO_PHASE_3`。

- [ ] **Step 5: 加固校验器并转绿**

  `validate_prd_baseline.py` 必须校验 A.2 的 55/48/103；`validate_sds_phase2.py` 必须纳入 08a、做 Requirement 反向白名单和 V3/WO/打卡禁止项检查。

  Run: `python -m unittest scripts.tests.test_validate_prd_baseline scripts.tests.test_validate_sds_phase2 -v`

  Expected: PASS。

- [ ] **Step 6: 运行相关生成、语义和差异检查**

  Run: `python scripts/validate_prd_baseline.py`

  Run: `python scripts/validate_sds_phase2.py`

  Run: `git diff --check`

  Expected: 全部 PASS；源 PRD 与基线快照 SHA 一致。

- [ ] **Step 7: 自审并提交**

  检查未读取受保护资料、未改业务语义、未把后续证据前置。读取 `$git-commit` skill，显式暂存本任务文件并创建单一提交。

---

### Task 2: 修复领域迁移证据的冻结提交绑定

**Files:**
- Modify: `scripts/generate_domain_entity_migration_contract.py`
- Modify: `scripts/validate_domain_entity_migration_alignment.py`
- Modify: `scripts/tests/test_validate_domain_entity_migration_alignment.py`
- Regenerate only when required: `docs/traceability/domain-entity-migration-contract.json`
- Regenerate only when required: `docs/traceability/domain-entity-migration-contract.md`

**Interfaces:**
- Consumes: 契约登记的冻结 implementation commit 与该提交中的 `sql/migrations` Git blobs。
- Produces: 不依赖 NPDMS 当前 HEAD 的可重复迁移证据生成和校验。

- [ ] **Step 1: 写三类冻结提交测试**

  覆盖：HEAD 前进但相关 SQL 未变仍 PASS；生成器读取契约登记的提交而非工作树；将冻结提交改为含不兼容 SQL 的提交必须 FAIL。

- [ ] **Step 2: 运行定点测试确认现状失败**

  Run: `python -m unittest scripts.tests.test_validate_domain_entity_migration_alignment -v`

- [ ] **Step 3: 使用 Git 对象读取替代 HEAD 精确相等**

  实现按登记 commit 执行等价于 `git show <commit>:<path>` 的只读内容获取；验证 commit 存在、目标文件存在、内容与契约一致。禁止把当前工作树或当前 HEAD 当作冻结证据。

- [ ] **Step 4: 重生成并验证**

  Run: `python scripts/generate_domain_entity_migration_contract.py --check`

  Run: `python scripts/validate_domain_entity_migration_alignment.py`

  Run: `python -m unittest scripts.tests.test_validate_domain_entity_migration_alignment -v`

  Expected: 全部 PASS，NPDMS HEAD 可前进而冻结证据仍可复现。

- [ ] **Step 5: 自审并提交**

  确认无实现仓写操作、无迁移执行、无当前 HEAD 偶然依赖。读取 `$git-commit` skill后显式提交。

---

### Task 3: 按需求方决定收口历史工单与工时边界

**Files:**
- Modify: `docs/design/08a-domain-entity-migration-alignment.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/13-file-design.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/engineering/gates/phase-2/gate-status.md`
- Modify: `docs/engineering/gates/phase-2/self-review.md`
- Test: `scripts/tests/test_validate_sds_phase2.py`

**Interfaces:**
- Consumes: 需求方对历史工单/工时采用 A 用户可查询导出或 B 仅迁移归档证据的明确决定。
- Produces: 与决定一致的 API、文件权限、迁移对象和 Gate 状态。

- [ ] **Step 1: 写所选方案的负向测试**

  若选择 B：断言 `/historical-work-orders`、`/historical-time-records` 及对应 V1/V2 用户文件入口出现即 FAIL；只允许 AI-MIG-000 的不可变来源证据。若选择 A：先登记正式 Requirement ID 和 Owner，测试必须要求只读 API、导出审计、项目数据范围和附件权限全部存在。

- [ ] **Step 2: 运行定点测试确认红灯**

  Run: `python -m unittest scripts.tests.test_validate_sds_phase2 -v`

- [ ] **Step 3: 最小修改所选边界**

  不把历史资料恢复为可流转 WorkOrder 聚合；不创建当前写状态机。方案 B 删除未获需求支持的用户 API/文件入口并保留迁移排除/归档审计；方案 A 仅在正式变更批准后落位完整只读能力。

- [ ] **Step 4: 关闭开放问题并验证**

  Run: `python scripts/validate_sds_phase2.py`

  Run: `git diff --check`

  Expected: PASS。

- [ ] **Step 5: 自审并提交**

  读取 `$git-commit` skill，显式提交本任务文件。

---

### Task 4: 独立复审并恢复 Phase 2 Gate

**Files:**
- Modify: `docs/engineering/gates/phase-2/independent-review.md`
- Modify: `docs/engineering/gates/phase-2/gate-status.md`
- Modify: `docs/engineering/gates/phase-2/README.md`
- Modify when generated: `docs/traceability/phase2-contract-map.md`

**Interfaces:**
- Consumes: Tasks 1-3 的提交与完整校验结果。
- Produces: fresh-context 独立 GO/NO-GO；仅 GO 时恢复 `APPROVED / READY_FOR_PHASE_3`。

- [ ] **Step 1: 生成固定提交范围的只读审查包**

  审查范围必须从本计划开始前基线提交到 Task 3 HEAD，记录完整 SHA，不读取受保护资料。

- [ ] **Step 2: 独立复核范围、契约、迁移和状态一致性**

  必须核验 55/48/103、84/95/1、V3/WO/打卡零混入、冻结提交可重复读取、历史边界符合用户决定。

- [ ] **Step 3: 运行完整相关验证**

  Run: `python scripts/validate_prd_baseline.py`

  Run: `python scripts/validate_sds_phase2.py`

  Run: `python scripts/validate_domain_entity_migration_alignment.py`

  Run: `python -m unittest discover -s scripts/tests -p "test_*.py"`

  Run: `git diff --check`

  Expected: 全部 PASS。

- [ ] **Step 4: 根据独立结论写回 Gate**

  只有无 Critical/Required 且独立结论为 GO，才恢复 Phase 2 `APPROVED / READY_FOR_PHASE_3`；否则保持 `IN_REVIEW / NOT_READY_FOR_PHASE_3` 并列出精确阻断。

- [ ] **Step 5: 提交 Gate 结论**

  读取 `$git-commit` skill，显式提交本任务文件，不推送。

## Self-Review

- 覆盖 Phase 2 预检发现的范围漂移、数量漂移、状态漂移、校验器漂移和历史边界未决项。
- 历史边界单独成 Task 3，不阻断 Task 1/2。
- 没有把生产环境、KMS、容量、SIT/UAT、真实迁移批次提前为 SDS 阻断项。
- 没有要求读取 Excel 或受保护割接分析文档。
