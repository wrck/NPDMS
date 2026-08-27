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

