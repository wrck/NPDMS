### Task 3: 按需求方决定收口历史工单与工时边界

**Files:**
- Modify: `docs/design/08a-domain-entity-migration-alignment.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/13-file-design.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/engineering/gates/phase-2/gate-status.md`
- Modify: `docs/engineering/gates/phase-2/self-review.md`
- Test: `scripts/tests/test_validate_sds_phase2.py`

**Interfaces:**
- Consumes: 需求方对历史工单/工时采用 A 用户可查询导出或 B 仅迁移归档证据的明确决定。
- Produces: 与决定一致的 API、文件权限、迁移对象和 Gate 状态。

- [ ] **Step 1: 写所选方案的负向测试**

  若选择 B：断言 `/historical-work-orders`、`/historical-time-records` 及对应 V1/V2 用户文件入口出现即 FAIL；只允许 AI-MIG-000 的不可变来源证据。若选择 A：先登记正式 Requirement ID 和 Owner，测试必须要求只读 API、导出审计、项目数据范围和附件权限全部存在。

- [ ] **Step 2: 运行定点测试确认红灯**

  Run: `python -m unittest scripts.tests.test_validate_sds_phase2 -v`

- [ ] **Step 3: 最小修改所选边界**

  不把历史资料恢复为可流转 WorkOrder 聚合；不创建当前写状态机。方案 B 删除未获需求支持的用户 API/文件入口并保留迁移排除/归档审计；方案 A 仅在正式变更批准后落位完整只读能力。

- [ ] **Step 4: 关闭开放问题并验证**

  Run: `python scripts/validate_sds_phase2.py`

  Run: `git diff --check`

  Expected: PASS。

- [ ] **Step 5: 自审并提交**

  读取 `$git-commit` skill，显式提交本任务文件。

---

