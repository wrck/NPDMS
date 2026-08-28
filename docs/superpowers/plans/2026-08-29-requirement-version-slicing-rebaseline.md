# PRD V1.8 Requirement版本切片修订007与重新基线化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将需求方对 VS-001～VS-011 的统一裁决及“配置基础能力优先、明确后置项除外”原则一次性回写 PRD，建立100项Requirement的111个目标版本切片自动派生底座，并形成 `CHG-PRD-2026-08-29-007` 正式基线。

**Architecture:** PRD正文和附录A仍是业务语义唯一权威；附录A.1保留100项Requirement主交付版本，新增A.1.1定义11个补充版本切片。Feature Spec以机器可读覆盖声明负责Requirement切片到Feature的映射，Feature Task只负责实施完成事实；生成器从三类权威输入派生JSON和Markdown投影，不保留人工版本或状态覆盖。

**Tech Stack:** Markdown、Python 3.13、现有PRD领域生成与基线验证脚本

**Inputs:**

- `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`
- `docs/superpowers/specs/2026-08-29-requirement-version-slicing-review-design.md`

## Global Constraints

- 不新增或删除正式Requirement；正式数量保持100项，主交付版本保持V1 53项、V2 47项。
- 不改变Requirement优先级和领域Owner。
- 正式覆盖键总数固定为111：V1切片53个，V2切片58个。
- 已明确标为V2、V3或后置的配置能力保持原版本；未明确延后的动态模板、动态表单、规则匹配及其配置能力必须不晚于首个消费版本，并作为所属版本首批基础能力。
- CUT-07、CUT-09、CUT-10为V1割接基础能力并优先实施；删除未定义的V2“规则配置与使用效率增强”，不形成当前V3承诺。
- V3跨Requirement演进方向由2项增至5项，只新增EXE-05、CUT-06、INT-03；编号V3需求仍为31项。
- 用户未要求Git提交，本计划不执行`git commit`或`git push`。

---

### Task 1: 固化裁决及正式化口径

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/superpowers/specs/2026-08-29-requirement-version-slicing-review-design.md`

- [ ] **Step 1:** 将VS-001～VS-011全部标记为需求方已裁决，并记录最终业务口径。
- [ ] **Step 2:** 关闭Q-PRD-VS-001～008，保留裁决证据和日期。
- [ ] **Step 3:** 将审核结果收敛为84项`KEEP_SINGLE`、11项`SPLIT_BY_BUSINESS_OUTCOME`、5项`COLOCATE_WITH_SHARED_OUTCOME`、0项`BLOCKED_BY_SPEC`。
- [ ] **Step 4:** 把“配置基础能力优先、明确后置项除外”加入切片判定规则。

### Task 2: 回写PRD修订007业务语义

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

- [ ] **Step 1:** 增加修订007记录并保持完整目录。
- [ ] **Step 2:** 回写PM-08、PM-11、EXE-05、ACC-01、ACC-02、CUT-05、CUT-06、CUT-07/09/10、INT-02、INT-03、INT-04、EQP-07、NFR-02及E2E验收边界。
- [ ] **Step 3:** 明确未后置配置基础能力的版本先后规则；不改动已明确V2/V3/后置的配置能力。
- [ ] **Step 4:** 更新13.2版本规划、A.1正式索引、A.2统计、A.3.2跨需求演进和附录D决策索引。
- [ ] **Step 5:** 新增A.1.1目标版本切片，定义100个主切片和11个补充V2切片，总计111个唯一覆盖键。

### Task 3: 建立Requirement切片自动派生底座

**Files:**
- Modify: `specs/features/*.md`（14个当前正式Feature Spec，仅增加覆盖声明或修正版本键）
- Modify: `scripts/generate_requirement_traceability.py`
- Modify: `scripts/tests/test_generate_requirement_traceability.py`
- Create: `docs/traceability/requirement-version-coverage.json`
- Modify: `docs/traceability/requirement-matrix.md`

- [ ] **Step 1:** 在每个Feature Spec登记明确的`Requirement@版本=FULL|PARTIAL`覆盖声明；关联或支撑Requirement不得冒充覆盖。
- [ ] **Step 2:** 从PRD A.1/A.1.1读取111个切片，从Feature Spec读取映射，从Task读取`IMPLEMENTATION_COMPLETE`事实。
- [ ] **Step 3:** 删除`VERSION_SLICE_OVERRIDES`和`TRANSITIONAL_STATUS_PROJECTIONS`，禁止手工回填覆盖状态。
- [ ] **Step 4:** 生成111行Markdown矩阵和机器可读JSON投影，校验无重复、无孤儿、无V3/OUT_OF_SCOPE泄漏。
- [ ] **Step 5:** 更新回归测试，覆盖100项/111切片、映射完整性、Task缺失不计完成、PARTIAL不误闭合和`--check`漂移检测。

### Task 4: 形成修订007基线与下游受检投影

**Files:**
- Create: `docs/baseline/prd-v1.8-amendment-007-requirement-version-slicing-and-derived-coverage.md`
- Modify: `docs/baseline/prd-v1.8.md`
- Modify: `docs/baseline/README.md`
- Modify: `docs/baseline/requirement-baseline.yaml`
- Modify: `docs/baseline/baseline-signoff.md`
- Modify: `docs/baseline/change-log.md`
- Create: `docs/reports/2026-08-29-PRD-V1.8修订007基线变更报告.md`
- Modify: `docs/engineering/00-engineering-chain.md`
- Modify: `docs/traceability/business-feedback-change-map.md`
- Modify: `specs/001-project-delivery-platform/domains/*.md`

- [ ] **Step 1:** 生成修订007影响说明和基线变更报告。
- [ ] **Step 2:** 将已验证PRD源文件机械同步为正式快照，确保字节一致和SHA-256一致。
- [ ] **Step 3:** 更新基线注册、签署、变更日志和工程链当前口径。
- [ ] **Step 4:** 从PRD重新生成13个领域需求投影，标识受影响SDS/Feature需差异复核，不自动宣称下游已重新批准。

### Task 5: 验证完整性、语义与可重现性

**Files:**
- Modify: `scripts/validate_prd_baseline.py`
- Modify: `scripts/validate_prd_domain_generation.py`
- Test: all changed artifacts

- [ ] **Step 1:** 更新基线验证器，校验100项、主版本53/47、111切片53/58、5项跨需求V3方向及源/快照哈希一致。
- [ ] **Step 2:** 更新领域生成验证器，校验EXE-05、CUT-06、INT-03只进入对应V3演进投影，CUT-07/09/10不被虚构为V3。
- [ ] **Step 3:** 运行PRD语义、领域生成、追溯生成及其测试。
- [ ] **Step 4:** 运行`git diff --check`并检查变更范围；任何失败先修复再交付。

## Verification Commands

```powershell
py -3.13 -B scripts/generate_prd_domain_requirements.py --prd 需求/PRD-项目实施交付管理平台.md --output specs/001-project-delivery-platform/domains
py -3.13 -B scripts/generate_requirement_traceability.py
py -3.13 -B scripts/validate_prd_semantics.py
py -3.13 -B scripts/validate_prd_baseline.py
py -3.13 -B scripts/validate_prd_domain_generation.py
py -3.13 -B -m unittest scripts.tests.test_generate_requirement_traceability
git diff --check
git status --short
```

Expected: 所有校验通过；PRD源文件与基线快照哈希一致；Requirement集合为100、切片集合为111且唯一；生成器`--check`无漂移；无人工版本/状态覆盖常量。
