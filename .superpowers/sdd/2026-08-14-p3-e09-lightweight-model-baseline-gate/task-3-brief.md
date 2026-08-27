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
