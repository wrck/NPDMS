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
