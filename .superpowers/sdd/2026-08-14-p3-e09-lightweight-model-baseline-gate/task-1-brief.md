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

