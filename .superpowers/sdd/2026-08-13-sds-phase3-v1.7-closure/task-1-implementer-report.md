# Task 1 实现报告：V1.7 P3-E09 物理模型差量

## 实现范围

- 新增ADR-0025，固化8个对象对应的13张精确目标表、业务键、版本/时态、不可变历史、来源证据、跨域逻辑引用、状态机边界和逐来源迁移处置。
- 扩展核心迁移机器契约与数据库命名契约；新增`validate_v17_delta(contract, object_table_map, ddl) -> list[str]`。
- 将核心候选DDL由50表扩展为63表，并重建目标字段目录、DDL漂移、逐项决策寄存器、MySQL 8.4隔离执行证据和Phase 3证据包。
- 更新数据库SDS、开放问题和Phase 3运行保障计划。P3-E09继续保持`BLOCKED_BY_REVIEW`，未生成`approvedDdlSha256`，未授权生产迁移或切换。
- 全程未对旧`dppms`执行DDL/DML，也未使用跨数据库SQL。

## 需求覆盖

| 范围 | Requirement IDs | 物理对象 |
|---|---|---|
| 配置Log解析与板卡候选 | EXE-03、EQP-01、EQP-02、EQP-03、EQP-05、EQP-07 | `imp_configuration_collection_parse_attempt`、`imp_configuration_component_candidate`、`ast_device_component_relation` |
| 满意度收集、闭环与转包门禁事实 | ACC-02、CLO-01、CLO-02、SUB-03、SUB-04 | `acc_satisfaction_collection_task`、`acc_satisfaction_questionnaire`、`acc_satisfaction_response`、`acc_satisfaction_result` |
| 割接保障任务、动作历史与责任区间 | CUT-11 | `cut_cutover_support_task`、`cut_cutover_support_history`、`cut_cutover_support_responsibility_interval` |
| 旧工单/工时只读历史 | SRV-01 | `srv_historical_work_order`、`srv_historical_time_record` |
| HR通讯录同步快照 | INT-05 | `plt_directory_sync_snapshot` |

没有新增API、角色、授权规则、状态机状态、流程节点或跨Context物理外键。

## 修改文件

### 决策、SDS和机器契约

- `docs/decisions/0025-v1.7-p3-e09-ddl-delta.md`
- `docs/decisions/open-questions.md`
- `docs/design/09-database-design.md`
- `docs/traceability/core-migration-schema-contract.json`
- `docs/traceability/database-naming-contract.json`
- `docs/superpowers/plans/2026-08-13-sds-phase3-runtime-release-assurance.md`
- `docs/superpowers/plans/2026-08-13-sds-phase3-v1.7-closure.md`（控制器新增计划，按要求保留）

### DDL、验证器、生成器与测试

- `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- `scripts/validate_core_migration_schema_contract.py`
- `scripts/validate_database_naming_contract.py`
- `scripts/generate_target_field_catalog.py`
- `scripts/generate_ddl_drift_review.py`
- `scripts/generate_ddl_model_decision_catalog.py`
- `scripts/generate_phase3_evidence_packets.py`
- `scripts/tests/test_validate_core_migration_schema_contract.py`
- `scripts/tests/test_validate_database_naming_contract.py`
- `scripts/tests/test_generate_ddl_model_decision_catalog.py`
- `scripts/tests/test_generate_phase3_evidence_packets.py`

### 重建证据

- `specs/001-project-delivery-platform/evidence/migration/complete-migration-summary.json`
- `specs/001-project-delivery-platform/evidence/migration/core-field-mapping-summary.json`
- `specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json`
- `specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json`
- `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- `specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md`
- `specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json`
- `specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-canonical.jsonl`
- `specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-mapping.jsonl`
- `specs/001-project-delivery-platform/evidence/migration/target-field-catalog-summary.json`
- `specs/001-project-delivery-platform/evidence/migration/target-field-catalog.jsonl`
- `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`（8份模板与manifest已由生成器重建，只有P3-E09内容发生差异）

## 负向测试

验证器现在拒绝：

- 13张目标表任一缺失；
- 对象—目标表映射被其他既有表替换；
- V3设计表混入；
- 内联或`ALTER TABLE`形式的跨Context外键；
- 历史只读表出现当前`status_code`、软删除或其他可变技术字段；
- 满意度问卷/答卷/结果和割接历史/责任区间出现软删除、乐观锁或更新审计字段；
- 满意度版本序号、割接历史序号、责任区间序号、来源键和设备当前槽位等关键唯一约束缺失；
- 设备当前槽位标记依赖状态/软删除，或当前唯一键粒度错误。

测试先以`validate_v17_delta`缺失得到预期红灯（新增调用点10个ERROR），实现后定点测试19项全部通过。

## 规模与同源哈希

- 当前DDL SHA-256：`03D0819B92964948044F186829D1F2E6A77C25A7B66126B12A5141D97535B125`
- MySQL 8.4.10隔离执行：63表、1,242列、335项主键/唯一键/外键/CHECK，PASS。
- 物理目录：461项约束/索引，其中63个主键、130个唯一键、48个同域外键、94个CHECK、126个普通索引。
- 逐项寄存器：1,905项；517项`AMEND_CURRENT`，1,388项`DEFER`，`approvedCount=0`。
- 核心契约、目标字段目录摘要、DDL漂移、逐项寄存器、隔离执行证据和P3-E09模板的当前DDL哈希一致；`approvedDdlSha256`保持空值。

## 验证结果

- `generate_target_field_catalog.py`：PASS，重建11个文件。
- `generate_ddl_drift_review.py`：PASS，63表/1,242列/461项约束索引/1,905项寄存器。
- `generate_ddl_model_decision_catalog.py`：PASS。
- `validate_mysql_ddl_execution.py`：PASS，MySQL 8.4.10隔离执行63表。
- `generate_phase3_evidence_packets.py`：PASS，重建8份模板和manifest。
- `validate_core_migration_schema_contract.py`：PASS。
- `validate_database_naming_contract.py`：PASS。
- `validate_ddl_item_decision_register.py`：PASS。
- `validate_sds_phase2.py`：PASS，104条需求追溯。
- `validate_sds_phase3.py`：PASS，104条验证映射。
- 全量`unittest`：119项初跑发现2个旧规模硬编码，修复后新增至121项并全部PASS。
- `git diff --check`：PASS。

## 自审结论与已知限制

五轴自审未发现未解决的正确性、架构、安全或性能阻断项。没有新增依赖或秘密信息；结构化大JSON均由仓库生成器或Node/Python读取处理。

已知限制：P3-E09仍缺数据架构Owner、业务Owner和迁移Owner的全量逐项签署以及`approvedDdlSha256`；Q08的126项索引仍需Feature查询计划与P3-E06近生产压测验证。因此当前结果只是可执行候选基线，不是生产迁移批准，不得关闭P3-E09。
