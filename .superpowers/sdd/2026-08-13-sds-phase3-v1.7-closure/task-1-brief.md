### Task 1: 补齐 V1.7 P3-E09 物理模型差量

**Files:**
- Create: `docs/decisions/0025-v1.7-p3-e09-ddl-delta.md`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `scripts/validate_core_migration_schema_contract.py`
- Modify: `scripts/tests/test_validate_core_migration_schema_contract.py`
- Modify: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/superpowers/plans/2026-08-13-sds-phase3-runtime-release-assurance.md`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/*`
- Regenerate: `docs/engineering/gates/phase-3/evidence-packet-templates/*`

**Interfaces:**
- Consumes: `docs/traceability/domain-entity-migration-contract.json`、`docs/traceability/domain-object-table-map.json`、ADR-0019～ADR-0024、PRD V1.7 的 `ACC-02/CLO-01/CLO-02/SUB-03/SUB-04/CUT-11/SRV-01/EQP-01/EQP-02/EQP-03/EQP-05/EQP-07/EXE-03/INT-05`。
- Produces: `validate_v17_delta(contract, object_table_map, ddl) -> list[str]`；当前核心 DDL 从 50 表扩展为 63 表，并保持所有派生证据使用同一 SHA-256。

- [ ] **Step 1: 编写 V1.7 差量契约失败测试**

  测试必须拒绝：13 张表任一缺失；对象—目标表映射被替换为其他已存在表；V3/OUT_OF_SCOPE 表混入；跨 Context 外键；历史只读表暴露可变状态或软删除业务语义；满意度答卷/结果、割接责任历史和设备组件时态关系缺少不可覆盖约束。

- [ ] **Step 2: 运行定点测试确认当前 DDL 不满足差量**

  Run: `python -B -m unittest scripts.tests.test_validate_core_migration_schema_contract`

  Expected: 新增差量用例 FAIL，明确列出缺失目标表而不是笼统报错。

- [ ] **Step 3: 固化 ADR 和机器契约**

  契约必须逐对象登记以下精确表集合：

  - `ConfigurationCollectionResult`：`imp_configuration_collection_parse_attempt`、`imp_configuration_component_candidate`
  - `SatisfactionCollection`：`acc_satisfaction_collection_task`、`acc_satisfaction_questionnaire`、`acc_satisfaction_response`、`acc_satisfaction_result`
  - `CutoverSupportTask`：`cut_cutover_support_task`、`cut_cutover_support_history`
  - `ResponsibilityInterval`：`cut_cutover_support_responsibility_interval`
  - `HistoricalWorkOrderRecord`：`srv_historical_work_order`
  - `HistoricalTimeRecord`：`srv_historical_time_record`
  - `DeviceComponentRelation`：`ast_device_component_relation`
  - `DirectorySyncSnapshot`：`plt_directory_sync_snapshot`

  ADR 必须说明业务键、版本/时态、不可变历史、来源证据、跨域逻辑引用、状态机边界和旧数据逐来源处置，不得把设计字段伪装为 PRD 新需求。

- [ ] **Step 4: 实现 13 张表的最小完整 DDL**

  - 配置解析：解析尝试按 `collection_result_id + attempt_no` 唯一；候选保存机框 SN、槽位、板卡 SN/型号、解析版本、匹配状态和证据引用，不覆盖原始 Log，不直接改写已生效设备关系。
  - 满意度：任务冻结问卷版本和阈值；问卷、答卷、签字、评分结果追加保存；整改重收生成新任务/版本，不覆盖旧答案和判定。
  - 割接保障：任务保存割接逻辑引用、时间窗、状态机版本和当前责任人；历史与责任区间只追加；接管/转交结束旧区间并新增，挂起不结束责任区间。
  - 历史工单/工时：按来源系统和来源业务键永久唯一，保存原类型/状态/责任人、结构化查询字段、不可变原始载荷及来源哈希；不提供当前业务流转状态，不以 `deleted` 删除历史。
  - 设备组件：保存机框设备、槽位、板卡设备或 SN/型号、关系来源、证据及生效区间；同一机框槽位同一时点最多一个当前板卡，换板结束旧关系并新增。
  - HR 快照：按来源系统和来源键唯一，保存来源版本、人员、组织、岗位、在离职状态、同步批次和水位；同步停用不删除历史责任引用。

- [ ] **Step 5: 重建并执行证据链**

  Run: `python -B scripts/generate_target_field_catalog.py`

  Run: `python -B scripts/generate_ddl_drift_review.py`

  Run: `python -B scripts/generate_ddl_model_decision_catalog.py`

  Run: `python -B scripts/validate_mysql_ddl_execution.py`

  Run: `python -B scripts/generate_phase3_evidence_packets.py`

  Expected: 63 表全部在隔离 MySQL 8.4 执行成功；DDL、字段目录、漂移寄存器、执行证据和 P3-E09 模板 SHA-256 完全一致。

- [ ] **Step 6: 执行 Task 1 全量校验**

  Run: `python -B scripts/validate_core_migration_schema_contract.py`

  Run: `python -B scripts/validate_database_naming_contract.py`

  Run: `python -B scripts/validate_ddl_item_decision_register.py`

  Run: `python -B scripts/validate_sds_phase2.py`

  Run: `python -B scripts/validate_sds_phase3.py`

  Run: `python -B -m unittest discover -s scripts/tests -p "test_*.py"`

  Run: `git diff --check`

  Expected: 全部 PASS；P3-E09 仍为 `BLOCKED_BY_REVIEW`，不因 DDL 可执行而提前批准。

- [ ] **Step 7: 完成实现复审、任务复审并提交**

  Commit: `docs(data-model): 补齐V1.7核心迁移DDL差量`

