### Task 2: 完成 P3-E09 逐项裁决和批准哈希

**Files:**
- Modify: `scripts/generate_ddl_drift_review.py`
- Modify: `scripts/validate_ddl_item_decision_register.py`
- Modify: `scripts/tests/test_generate_ddl_drift_review.py`
- Modify: `scripts/tests/test_validate_ddl_item_decision_register.py`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`
- Create: `docs/engineering/gates/phase-3/submissions/p3-e09-approved.json`

**Interfaces:**
- Consumes: Task 1 独立复审为 `GO`的对象完整性报告、该报告确认的当前 DDL SHA-256、ADR-0019～ADR-0025 和需求方对新差量字段/约束清单的确认；不消费旧报告、固定表/列/约束数或过期哈希。
- Produces: 每个 DDL 项均具有最终 decision、decisionOwner、reviewOwner、evidenceRefs；`approvedDdlSha256` 精确等于当前 DDL 哈希。

- [ ] **Step 1: 生成待确认的具体差量清单**

  清单按表列出新增字段、唯一键、时态/不可变约束、普通索引、来源映射和建议裁决；禁止只给表名或笼统问题。若存在可改变业务含义的选项，先向需求方确认后再继续。

- [ ] **Step 2: 编写批准完整性负向测试**

  测试必须拒绝：任一项 `DEFER`、空 Owner、空证据引用、批准哈希与当前 DDL 不同、独立复审结论非 GO、提交证据缺失或属于旧基线。

- [ ] **Step 3: 生成逐项最终裁决**

  `MATCH` 项沿用已批准基线；ADR 明确新增/修改/删除项使用对应 ADR 裁决；Q07 技术完整性约束和 Q08 候选索引分别保留 Feature 查询计划/P3-E06 下游验证，不把候选索引误写成性能已通过。任何无法回指 PRD/SDS/ADR 的项继续 `DEFER` 并停止批准。

- [ ] **Step 4: 执行独立数据模型复审并生成批准证据**

  复审至少核对对象完整性、字段语义、唯一性、时态、不变历史、状态守卫、跨域引用、迁移来源、索引合理性、MySQL 8.4 执行和派生哈希一致性；结论非 GO 时不得写批准哈希。

- [ ] **Step 5: 执行 Task 2 校验并提交**

  Run: `python -B scripts/validate_ddl_item_decision_register.py`

  Run: `python -B scripts/validate_phase3_evidence_submission.py docs/engineering/gates/phase-3/submissions/p3-e09-approved.json`

  Run: `python -B -m unittest discover -s scripts/tests -p "test_*.py"`

  Run: `git diff --check`

  Expected: 全部 PASS；P3-E09 状态为 `CLOSED`，只关闭 SDS 数据模型、历史迁移实施和切换对应的当前阻断，不关闭 P3-E01～P3-E08 下游门禁。

  Commit: `docs(gate): 批准P3-E09数据模型基线`

