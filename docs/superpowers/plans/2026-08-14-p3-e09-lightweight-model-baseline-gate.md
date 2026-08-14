# P3-E09轻量模型基线门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以最小改动解除P3-E09对SDS数据模型基线的不合理阻断，同时继续禁止未经真实批次验证的历史迁移和数据切换。

**Architecture:** 复用现有DDL哈希、1,883项逐项裁决、MySQL 8.4执行证据和独立复审结果，不新增批准平台或双状态机。Phase 3只区分“模型可用于设计”和“迁移仍不可执行”，最终正式制品由一次Git基线提交冻结。

**Tech Stack:** Python 3.13、现有JSON机器契约、Git、`unittest`。

## Global Constraints

- 不修改PRD、60表DDL、领域模型、API、权限、状态机或业务流程。
- 不新增OA、电子签名、四角色附件、迁移批准模板或通用证据系统。
- `approvedDdlSha256`保持为空，AI-MIG-000、历史迁移和数据切换继续阻断。
- Q08的122项仅为候选索引，继续受Feature查询计划和P3-E06性能验收约束。
- 不读取、不修改、不暂存两份受保护的未跟踪原始资料。
- 使用`py -3.13`；显式暂存；不推送。

---

### Task 1: 删除SDS模型基线的过重签署依赖

**Files:**
- Modify: `scripts/p3e09_approval_policy.py`
- Modify: `scripts/validate_ddl_item_decision_register.py`
- Modify: `scripts/validate_phase3_evidence_register.py`
- Modify: `scripts/validate_phase3_evidence_submission.py`
- Modify: `scripts/tests/test_p3e09_approval_policy.py`
- Modify: `scripts/tests/test_validate_ddl_item_decision_register.py`
- Modify: `scripts/tests/test_validate_phase3_evidence_register.py`
- Modify: `scripts/tests/test_validate_phase3_evidence_submission.py`

**Interfaces:**
- Consumes: 当前DDL哈希、Items哈希、Item ID集合哈希、`DEFER=0`、MySQL 8.4证据和独立复审GO。
- Produces: `validate_model_baseline(register: dict, evidence: dict) -> list[str]`；不产生迁移批准结果。

- [ ] **Step 1: 写模型与迁移范围隔离的失败测试**

```python
def test_complete_model_evidence_allows_sds_without_migration_approval(self):
    payload = self.complete_model_payload()
    payload["approvedDdlSha256"] = None
    errors = VALIDATOR.validate_model_baseline(payload)
    self.assertEqual([], errors)

def test_model_readiness_does_not_allow_migration(self):
    item = self.complete_model_item()
    self.assertNotIn("DATA_MODEL_BASELINE", item["blocks"])
    self.assertIn("HISTORICAL_DATA_MIGRATION", item["blocks"])
    self.assertIn("DATA_CUTOVER", item["blocks"])
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_p3e09_approval_policy scripts.tests.test_validate_ddl_item_decision_register scripts.tests.test_validate_phase3_evidence_register scripts.tests.test_validate_phase3_evidence_submission -v`

Expected: FAIL，现有逻辑仍把四角色签署和`approvedDdlSha256`作为SDS模型基线条件。

- [ ] **Step 3: 最小化批准策略**

保留模型事实校验，移除SDS路径中的`signoffs`、`attestationMethod`、四份附件和最终迁移批准哈希要求。不得新增替代性角色矩阵、审批节点或批准记录结构。

- [ ] **Step 4: 运行定点测试**

Run: `py -3.13 -m unittest scripts.tests.test_p3e09_approval_policy scripts.tests.test_validate_ddl_item_decision_register scripts.tests.test_validate_phase3_evidence_register scripts.tests.test_validate_phase3_evidence_submission -v`

Expected: PASS；模型证据完整即可解除SDS模型阻断，迁移阻断仍存在。

- [ ] **Step 5: 提交策略简化**

```bash
git add scripts/p3e09_approval_policy.py scripts/validate_ddl_item_decision_register.py scripts/validate_phase3_evidence_register.py scripts/validate_phase3_evidence_submission.py scripts/tests/test_p3e09_approval_policy.py scripts/tests/test_validate_ddl_item_decision_register.py scripts/tests/test_validate_phase3_evidence_register.py scripts/tests/test_validate_phase3_evidence_submission.py
git commit -m "refactor(gate): 轻量化P3-E09模型门禁"
```

### Task 2: 同步机器契约和Phase 3状态

**Files:**
- Modify: `scripts/generate_phase3_evidence_packets.py`
- Modify: `scripts/sync_phase3_p3e09_requirement_confirmation.py`
- Modify: `scripts/tests/test_generate_phase3_evidence_packets.py`
- Modify: `scripts/tests/test_sync_phase3_p3e09_requirement_confirmation.py`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`

**Interfaces:**
- Consumes: Task 1模型基线校验结果。
- Produces: P3-E09对`PHASE_3_BASELINE / DATA_MODEL_BASELINE`解除阻断，对`HISTORICAL_DATA_MIGRATION / DATA_CUTOVER`继续阻断。

- [ ] **Step 1: 写Phase 3派生状态测试**

```python
def test_p3e09_is_model_ready_and_migration_pending(self):
    item = GENERATOR.build_packets()["P3-E09"]
    self.assertEqual("MODEL_BASELINE_READY", item["confirmedFacts"]["modelDecisionStatus"])
    self.assertIsNone(item["confirmedFacts"]["approvedDdlSha256"])
    self.assertEqual(
        {"HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"},
        set(item["blocks"]),
    )
```

- [ ] **Step 2: 运行红灯测试**

Run: `py -3.13 -m unittest scripts.tests.test_generate_phase3_evidence_packets scripts.tests.test_sync_phase3_p3e09_requirement_confirmation -v`

Expected: FAIL，现有状态仍等待外部签署或使用共用批准语义。

- [ ] **Step 3: 修改生成和同步逻辑**

状态只由现有模型事实派生；`approvedDdlSha256`不得因模型Ready被填充。旧模板删除四角色签署字段并明确标注“不得用于授权历史迁移”。

- [ ] **Step 4: 重生成并验证正式制品**

Run: `py -3.13 scripts/generate_phase3_evidence_packets.py`

Run: `py -3.13 scripts/sync_phase3_p3e09_requirement_confirmation.py`

Run: `py -3.13 scripts/validate_core_migration_schema_contract.py`

Run: `py -3.13 scripts/validate_ddl_item_decision_register.py`

Run: `py -3.13 scripts/validate_phase3_evidence_register.py`

Expected: 全部PASS；1,883项裁决和当前DDL哈希不变；迁移阻断仍为OPEN。

- [ ] **Step 5: 提交状态同步**

```bash
git add scripts/generate_phase3_evidence_packets.py scripts/sync_phase3_p3e09_requirement_confirmation.py scripts/tests/test_generate_phase3_evidence_packets.py scripts/tests/test_sync_phase3_p3e09_requirement_confirmation.py docs/traceability/core-migration-schema-contract.json specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json docs/engineering/gates/phase-3/phase3-evidence-register.json docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json
git commit -m "docs(gate): 同步P3-E09模型基线状态"
```

### Task 3: 统一文档并发布模型基线

**Files:**
- Modify: `docs/engineering/gates/phase-3/README.md`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`
- Modify: `docs/engineering/gates/phase-3/submissions/README.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/decisions/open-questions.md`

**Interfaces:**
- Consumes: Tasks 1-2的最终模型状态。
- Produces: 一致的正式口径和一次明确的模型基线Git提交。

- [ ] **Step 1: 删除过重方案残留表述**

现行文档不再要求四角色外部附件、OA、电子签名、独立批准JSON、迁移批准状态机或双确认提交。迁移部分只保留“未经真实批次验证不得执行”的硬规则。

- [ ] **Step 2: 运行冲突扫描**

Run: `rg -n "四角色外部|attestationMethod|APPROVAL_SYSTEM_RECORD|MANUAL_SIGNED_RECORD|MIGRATION_RELEASE_APPROVED" scripts docs specs/001-project-delivery-platform/evidence/migration`

Expected: 现行规则零命中；历史说明如保留，必须明确标注已废止。

- [ ] **Step 3: 运行全量验收**

Run: `py -3.13 -m unittest discover -s scripts/tests -p "test_*.py" -v`

Run: `py -3.13 scripts/validate_core_migration_schema_contract.py`

Run: `py -3.13 scripts/validate_ddl_item_decision_register.py`

Run: `py -3.13 scripts/validate_phase3_evidence_register.py`

Run: `py -3.13 scripts/validate_sds_phase3.py`

Run: `git diff --check`

Expected: 全部PASS；DDL和逐项裁决无业务变化。

- [ ] **Step 4: 独立复审最小风险点**

复审只回答四个问题：模型事实是否完整；DDL变化能否使门禁失效；SDS Ready是否误放行迁移；是否仍有无直接收益的治理结构。结论为GO后进入下一步。

- [ ] **Step 5: 创建模型基线提交**

```bash
git add docs/engineering/gates/phase-3/README.md docs/engineering/gates/phase-3/gate-status.md docs/engineering/gates/phase-3/submissions/README.md docs/design/08-data-model.md docs/design/09-database-design.md docs/decisions/open-questions.md
git commit -m "docs(gate): 发布P3-E09模型基线"
```

该Git提交冻结当前模型制品；不再额外生成批准记录、签署附件或迁移审批材料。
