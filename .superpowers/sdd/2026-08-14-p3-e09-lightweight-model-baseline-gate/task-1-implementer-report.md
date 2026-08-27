# Task 1 实现报告：P3-E09 轻量模型基线门禁

## 范围

- Requirement：P3-E09 SDS 数据模型基线；不改变 PRD 业务需求。
- 修改：四个 P3-E09 validator/policy 及其对应单元测试。
- N/A：领域模型、60 表 DDL、API、权限、业务状态机、业务流程、历史迁移和数据切换执行。

## 实现结果

- 以 `validate_model_baseline(register, evidence)` 替代四角色签署、附件哈希、`attestationMethod` 和最终迁移批准记录。
- 模型基线继续校验当前 DDL、Items、Item-ID 集合哈希、`DEFER=0`、MySQL 8.4 执行哈希与独立复审 `GO`。
- `approvedDdlSha256` 在 SDS 模型路径中必须为空；P3-E09 仅保留 `HISTORICAL_DATA_MIGRATION` 和 `DATA_CUTOVER` 阻断。

## 验证

- PASS：`py -3.13 -m unittest scripts.tests.test_p3e09_approval_policy scripts.tests.test_validate_ddl_item_decision_register scripts.tests.test_validate_phase3_evidence_register scripts.tests.test_validate_phase3_evidence_submission -v`（44 tests）。
- PASS：`py -3.13 -m compileall -q scripts/p3e09_approval_policy.py scripts/validate_ddl_item_decision_register.py scripts/validate_phase3_evidence_register.py scripts/validate_phase3_evidence_submission.py`。
- PASS：`py -3.13 scripts/validate_ddl_item_decision_register.py`。
- PASS：`git diff --check`。

## 已知限制与后续

- 当前正式 Phase 3 证据寄存器仍使用 Task 1 之前的 P3-E09 blocks/model status，因此 `py -3.13 scripts/validate_phase3_evidence_register.py` 按预期报出 block/model-state 不匹配；Task 2 负责同步生成器和正式制品。
- 本任务未修改受保护未跟踪资料，未修改 `progress.md`，也未执行迁移、数据切换或推送。

## Round 1：独立评审 NO-GO 修复

### 影响与修复

- 恢复 `targetCatalogDdlSha256`、`mappingDdlSha256`、`validationDdlSha256` 和 `manifestDdlSha256` 与 `currentDdlSha256` 的逐字段绑定；继续同时校验 Items、Item-ID 与 MySQL 8.4 哈希。
- 轻量独立复审现在要求 `decisionOwner != reviewOwner`、`independentReviewResult=GO`，并要求 `independentReviewRef` 指向仓库内已有的正式 gate/ADR 文件、列入 `evidenceRefs` 且正文包含“独立复审”和“GO”。未重新引入四角色、附件或迁移批准哈希。
- 更新 `test_validate_sds_phase3` 的 P3-E09 fixture，仅保留 `HISTORICAL_DATA_MIGRATION` 与 `DATA_CUTOVER` 两个 blocks。

### Round 1 验证

- PASS：定点 52 tests（含每个正式制品哈希漂移、同人自证、非 GO、缺失/无效独立复审引用负测）。
- PASS：`py -3.13 -m unittest discover -s scripts/tests -v`（171 tests）。
- PASS：`py -3.13 scripts/validate_ddl_item_decision_register.py`、`compileall`、`git diff --check`。
- 已知限制不变：正式 Phase 3 寄存器和文档仍由 Task 2/3 同步；因此 `py -3.13 scripts/validate_sds_phase3.py` 仅报告旧 P3-E09 blocks/model-state 与本 Task 校验器不匹配。
## Round 2：正式独立复审结论解析修复

### 影响与修复

- 根因：正式引用正文曾以“包含独立复审”和“包含 GO”判断，`NO-GO` 会命中 `GO` 子串。
- 修复：仅接受明确的整行结构化 `结论：GO` 或 `独立复审结论：GO`；正文出现 `NO-GO`、`NO_GO` 或 `NO GO` 时显式拒绝。无结构化结论的普通 GO 文本不再作为通过依据。
- 边界不变：不恢复四角色签署、附件、批准哈希或其他批准体系；历史迁移和数据切换 blocks 保持不变。

### Round 2 验证

- PASS：新增正式 gate 正文 `独立复审结论：NO-GO` 拒绝、明确 `独立复审结论：GO` 通过、模糊 GO 文本拒绝三组测试。
- 待执行：Task 1 定点、全量 unittest、相关 validators、compileall 与 diff-check；结果将在本轮提交后补充。
- PASS：Round 2 Task 1 定点 unittest 55 项、`py -3.13 -m unittest discover -s scripts/tests` 全量 174 项、DDL 决策寄存器 validator 与 compileall。
- PASS：`git diff --check`。`validate_phase3_evidence_register.py` 与 `validate_sds_phase3.py` 仍仅报告 Task 2/3 尚未同步的旧 P3-E09 blocks/model-state 两项预期不匹配；本轮未修改正式制品。
## Round 3：复审结论、真实路径与空哈希事实加固

### 影响与修复

- 独立复审正文只接受整行 `结论：GO`（需配合独立复审标记）或 `独立复审结论：GO`，继续拒绝 `NO-GO`、`NO_GO`、`NO GO` 和模糊的 GO 文本。
- `independentReviewRef` 解析后必须位于仓库真实 `docs/engineering/gates/` 或 `docs/decisions/` 目录；`docs/decisions/../../AGENTS.md` 等路径穿越被拒绝。
- `approvedDdlSha256` 现在是 P3-E09 的显式必填事实，必须存在且为 null 或空字符串；缺失不再被当作空值。
- 未恢复四角色签署、附件、审批哈希或迁移批准体系；历史迁移与数据切换 blocks 未变。

### Round 3 验证

- PASS：明确 GO、NO-GO、模糊 GO、真实路径穿越、缺失空哈希事实的新增边界测试。
- 待执行：Task 1 定点、全量 unittest、相关 validators、compileall 与 diff-check；结果将在提交前补充。
- 更正：单行 `结论：GO` 与 `独立复审结论：GO` 都是允许的明确结论格式；无需额外文本标记。
- PASS：Round 3 Task 1 定点 unittest 60 项、`py -3.13 -m unittest discover -s scripts/tests` 全量 179 项、DDL 决策寄存器 validator 与 compileall。
- PASS：`git diff --check`。`validate_phase3_evidence_register.py` 与 `validate_sds_phase3.py` 仍仅报告 Task 2/3 未同步的旧 P3-E09 blocks/model-state 两项预期不匹配；本轮未修改正式制品。
## Round 4：P3-E09 空批准哈希提交包修复

### 影响与修复

- 根因：提交包通用 `nonempty` 规则将已显式存在的 `approvedDdlSha256: null` 或空字符串误判为缺失，导致 P3-E09 处于提交状态时不可通过。
- 修复：将必填字段的存在性和非空值校验分离。P3-E09 的 `approvedDdlSha256` 必须有 key，允许 null 或空字符串；非空值仍被专用规则拒绝。共享 policy 的同一约束保持不变。

### Round 4 验证

- PASS：submission 端到端覆盖缺失 FAIL、null PASS、空字符串 PASS、非空 FAIL。
- PASS：Round 4 Task 1 定点 unittest 62 项、`py -3.13 -m unittest discover -s scripts/tests` 全量 181 项、DDL 决策寄存器 validator、compileall 与 `git diff --check`。
- 已知限制不变：正式 Phase 3 寄存器和 SDS 仍由 Task 2/3 同步，现状 validator 只报告旧 P3-E09 blocks/model-state 的两项预期不匹配。
