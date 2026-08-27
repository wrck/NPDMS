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

