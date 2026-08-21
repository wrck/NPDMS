# 割接流程基线纠偏实施计划

> **For agentic workers:** 按任务顺序执行；每个任务完成 READ → IMPLEMENT → TEST → SELF-REVIEW → REPORT，并单独提交。禁止读取 Excel；`需求/割接0807需求分析报告.md` 只读且不纳入提交。

**Goal:** 以《割接0807需求分析报告》第七部分业务流程及用户已确认决策为高置信输入，纠正 CUT 当前 PRD、SDS、追溯和数据模型中的工单语义误植与执行编排扩张。

**Architecture:** 保留 CUT-01 作为承载 P1～P6 的核心 `CutoverTask`；CUT-11 移出 CUT/V1/V2/当前 DDL并后置到工单领域。P4 操作、验证、回退清单是方案内容，P6 是轻量结果闭环；设备连接与采集只通过 INT-12 保留凭证、下发、回调和结果引用。

**Tech Stack:** Markdown PRD/SDS、Python 3 校验与生成脚本、MySQL 8.4 DDL、JSON/JSONL 机器契约、Git。

## Global Constraints

- 业务优先级：本轮用户确认决策 > `需求/割接0807需求分析报告.md` 第七部分 > PRD V1.7 当前文本 > SDS/Feature/DDL。
- 禁止查看或引用任何 Excel/XLSX；双机检查项有效数量保持 `BLOCKED_BY_SPEC`，不得使用此前 Excel 推导数值。
- CUT-01 是核心割接任务，必须保留；CUT-11 是后置工单候选，不进入当前 V1/V2、SDS 当前对象或 DDL。
- 等级评估为问卷；平台项目数据提供上下文；一线提交问卷和人工等级，用服经理在 P5 复核，不增加 P2 审批节点；自动建议等级仅为 V3。
- P5 任一合理性评审项不通过即驳回，原因必填。
- A/B 级专项提前时间规则保留为 V2，按割接类型和自然日计算；不得与平台通用时效/SLA/三级审批混合。
- P3 无匹配规则时允许一线补充自定义项并标记配置缺口，不阻断主流程。
- 上传完整方案仅校验文件、安全、方案归属和人工确认，不强制解析在线模板字段。
- 保障人员一般信息在审批后可修改且留痕；角色或任务职责变化必须创建新方案版本并按原等级重新执行 P5。
- P6 提交即归档并结束；遗留项是闭环记录，不建立独立生命周期或归档阻断。
- 外部备件系统拥有备件业务；CUT 仅保留跳转、外部引用及必要只读状态/证据。
- 不修改或提交两个当前未跟踪输入：`需求/割接0807需求分析报告.md`、Office 临时锁文件。

---

### Task 1: PRD V1.7 割接业务基线纠偏

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/baseline/prd-v1.7.md`
- Modify: `docs/baseline/requirement-baseline.yaml`
- Modify: `docs/baseline/change-log.md`
- Create: `docs/decisions/0026-cutover-flow-business-baseline-correction.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/traceability/business-feedback-change-map.md`
- Test: `scripts/tests/test_validate_prd_baseline.py`
- Test: `scripts/tests/test_validate_prd_semantics.py`

**Produces:** 经哈希冻结的 PRD V1.7 修订基线；CUT-01～10 当前范围与后置工单候选边界；供后续生成器消费的唯一业务语义。

- [ ] 添加负向语义校验：当前 V1/V2 不得出现 CUT-11、割接保障工单、逐步骤执行状态、稳定观察、遗留项阻断归档和 CUT 本地备件生命周期；必须保留 CUT-01、P1～P6、专项提前时间规则和 INT-12 结果引用。
- [ ] 运行定点测试，确认旧 PRD 因上述越界语义失败。
- [ ] 修订 CUT 总览、CUT-01～10、EXE-06、MVP、需求清单、版本说明和历史映射；CUT-11 回退为后置工单领域候选，不占当前 V1/V2 Requirement 数量。
- [ ] 固化已确认流程：P2人工等级、P3缺口降级、P4上传校验、保障人员变更、P5否项驳回、A/B自然日专项判断、P6提交归档。
- [ ] 新增 ADR-0026 明确其取代 ADR-0024 中“WO-06→CUT-11”的当前实施决定，但不改写 ADR-0024 历史内容。
- [ ] 将修订后的源 PRD 原字节复制为 `docs/baseline/prd-v1.7.md`，重算并更新 `requirement-baseline.yaml` SHA-256 与正式需求计数。
- [ ] 运行 `python scripts/validate_prd_semantics.py`、`python scripts/validate_prd_baseline.py`、`python scripts/validate_prd_domain_generation.py` 和相关 unittest。
- [ ] 自审 PRD 中 CUT、WO、EXE-06、MVP、追溯计数是否一致；执行 `git diff --check`。
- [ ] 读取 `$git-commit` skill，按精确文件清单提交 Task 1，不纳入未跟踪输入文件。

### Task 2: 13领域规格与SDS业务/实现契约同步

**Files:**
- Regenerate: `specs/001-project-delivery-platform/domains/CUT-变更切换与稳定治理需求规格.md`
- Regenerate/Modify: `specs/001-project-delivery-platform/domains/SRV-服务运营需求规格.md`
- Modify: `docs/traceability/requirement-matrix.md`
- Modify: `docs/design/01-requirement-traceability.md`
- Modify: `docs/design/02-domain-model.md`
- Modify: `docs/design/02a-context-map.md`
- Modify: `docs/design/02b-aggregate-boundary-decisions.md`
- Modify: `docs/design/02c-data-ownership-matrix.md`
- Modify: `docs/design/02d-cross-context-contracts.md`
- Modify: `docs/design/02e-version-scope-matrix.md`
- Modify: `docs/design/04-module-design.md`
- Modify: `docs/design/05-state-machine.md`
- Modify: `docs/design/06-workflow-design.md`
- Modify: `docs/design/07-authorization-design.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/11-event-design.md`
- Modify as needed: `docs/design/12-integration-design.md`, `13-file-design.md`, `15-cache-and-concurrency.md`, `16-exception-and-idempotency.md`, `17-audit-and-observability.md`, `20-test-design.md`
- Modify: `scripts/generate_requirement_traceability.py`
- Modify: `scripts/generate_phase2_contract_map.py`
- Test: affected generator and SDS validation tests under `scripts/tests/`

**Produces:** Requirement → SDS → API/Event/Data/Test 精确链；CUT-01作为核心任务，后置工单候选不污染CUT当前设计。

- [ ] 添加负向测试：生成器不得为当前范围生成 CUT-11、CutoverSupportTask、ResponsibilityInterval、步骤执行/观察 API 或事件。
- [ ] 重新生成13领域需求，验证 CUT 当前正式需求减少且后置工单候选进入 SRV/V3 或 OUT_OF_SCOPE 追溯，不成为当前正式需求。
- [ ] 修订 Phase 1：聚合只保留 CutoverTask 及问卷、采集、方案、审批、闭环从属事实；删除 CUT-11 状态机/工作流/权限。
- [ ] 修订 Phase 2：删除步骤 start/complete/fail、observation、support-task actions/events；保留方案清单、闭环结果、INT-12 下发/回调引用和专项提前时间计算契约。
- [ ] 明确保障人员一般信息修改 command 与留痕；角色/职责变更走新方案版本及原等级 P5，不建立独立保障任务。
- [ ] 重生成需求矩阵和 Phase 2 contract map，确保所有当前 CUT Requirement 精确回指正式章节。
- [ ] 运行领域生成、Phase 1/2 SDS validator、相关 unittest 和 `git diff --check`。
- [ ] 读取 `$git-commit` skill，提交 Task 2。

### Task 3: 当前DDL与迁移契约收缩

**Files:**
- Modify: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `docs/traceability/domain-object-table-map.json`
- Modify: `docs/traceability/domain-entity-migration-contract.json`
- Modify: `docs/traceability/domain-entity-migration-contract.md`
- Modify: `docs/design/08a-domain-entity-migration-alignment.md`
- Modify: `scripts/validate_core_migration_schema_contract.py`
- Modify: `scripts/validate_database_naming_contract.py`
- Modify: `scripts/generate_domain_entity_migration_contract.py`
- Modify: `scripts/generate_target_field_catalog.py`
- Modify: relevant tests under `scripts/tests/`
- Modify: ADR/P3-E09 current delta documentation referencing the three removed tables

**Produces:** 当前DDL不含后置工单表；对象—目标表集合精确；删除结果由机器负向门禁锁定。

- [ ] 添加负向测试：三张 `cut_cutover_support_*` 表、对象映射、字段目录或当前 requirementRefs 任一残留都必须失败。
- [ ] 从 DDL 删除 `cut_cutover_support_task`、`cut_cutover_support_history`、`cut_cutover_support_responsibility_interval`；不得替换为其他工单表。
- [ ] 从核心合同、命名合同、对象表映射、领域迁移合同、字段目录生成器和P3-E09当前差量中删除对应当前对象。
- [ ] 审计 `cut_execution_step`、`cut_observation`：若仅为未落DDL的目标设计则从当前设计/契约删除；方案步骤的数据承载保持为方案清单，不误删。
- [ ] 重建领域契约与对象表映射，校验目标表精确集合；后置工单候选必须是 `NONE_NEW/OUT_OF_SCOPE` 且无当前 targetTables。
- [ ] 运行核心迁移、数据库命名、领域迁移对齐、市场关系与相关 unittest。
- [ ] 读取 `$git-commit` skill，提交 Task 3。

### Task 4: 派生证据重建、MySQL执行与门禁复审

**Files:**
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/*.json*`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/*.jsonl`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/*.md`
- Modify: `docs/engineering/gates/phase-3/` current status/submission evidence as required
- Modify: Phase 3 register/submission manifests that bind the DDL hash

**Produces:** 新DDL哈希、MySQL 8.4执行证据、确定性派生证据和复审结论。

- [ ] 依次运行字段目录、DDL drift、DDL model decision、Phase 3 packet生成器；不得手工编辑派生JSONL。
- [ ] 在隔离 MySQL 8.4 执行新DDL，记录表/列/约束数量及SHA-256；验证三张后置工单表不存在。
- [ ] 运行全部正式 validator、全部 `scripts/tests` unittest、生成器 `--check`、`git diff --check`。
- [ ] 进行独立只读复审，重点核验 CUT-01仍存在、CUT-11只后置、P1～P6无执行编排扩张、专项提前时间规则未被误删。
- [ ] 修复复审发现的实质问题并重跑受影响校验；P3-E09状态只按真实证据更新。
- [ ] 读取 `$git-commit` skill，提交 Task 4；不推送。

## Self-Review

- Spec coverage：全部已确认业务决策分别落入 Task 1；下游映射、DDL和证据分别由 Task 2～4覆盖。
- Scope：原始报告和Excel临时文件只读；不处理巡检、维保、通用工单实现或其他领域增强。
- Ambiguity：双机检查项数量保持阻塞；未把“任务”一词等同于工单，CUT-01明确保留。
- Rollback：每个任务独立提交；业务基线、SDS、DDL/证据可按任务级提交回退。
