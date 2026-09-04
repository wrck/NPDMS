# ACC / INT / CUT三分支按提交时间代码事实重放

- 基线：`220486237b9570ab3d2b0663df39c89be2a5ec69`
- 唯一来源提交：`572`
- 原则：所有模块代码均在接收范围；局部冲突不阻断其他模块、文件或提交。
- Feature：已接收代码据实登记；未关闭Gate保持IN_PROGRESS。

## 决策统计

| 决策 | 数量 |
|---|---:|
| `APPLIED_ADD_ADD` | 129 |
| `APPLIED_CODE_CHANGE` | 229 |
| `APPLIED_NON_CONFLICTING_HUNKS` | 52 |
| `APPLIED_SOURCE_FILE` | 1 |
| `APPLIED_THREE_WAY` | 1 |
| `CONFLICTING_HUNKS_PENDING` | 677 |
| `EXCLUDED_SOURCE_METADATA` | 1448 |
| `RESOLVED_BOTH_DELETED` | 1 |

## Feature代码事实

| Feature | 路径 | 提交 |
|---|---:|---:|
| F-ACC-001 | 32 | 23 |
| F-ACC-002 | 28 | 18 |
| F-AST-001 | 1 | 1 |
| F-COM-001 | 58 | 28 |
| F-CUT-001 | 12 | 16 |
| F-CUT-002 | 13 | 5 |
| F-CUT-003 | 16 | 10 |
| F-CUT-004 | 12 | 11 |
| F-CUT-005 | 19 | 11 |
| F-CUT-006 | 17 | 11 |
| F-CUT-007 | 1 | 1 |
| F-CUT-008 | 4 | 4 |
| F-CUT-010 | 5 | 3 |
| F-IMP-001 | 8 | 3 |
| F-IMP-002 | 5 | 8 |
| F-INT-012 | 5 | 1 |
| UNMAPPED | 63 | 47 |

## 规范化处理

- `/home/runner/work/NPDMS/NPDMS/pom.xml` — `REMOVE_DUPLICATE_MODULE` — `pms-module-integration/pms-module-integration-api`
- `sql/migrations/V104__device_ops_integration_foundation.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V204__received_device_ops_integration_foundation.sql`
- `sql/migrations/V105__device_ops_credentials_and_dispatch.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V205__received_device_ops_credentials_and_dispatch.sql`
- `sql/migrations/V106__device_ops_callback_consumption.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V206__received_device_ops_callback_consumption.sql`
- `sql/migrations/V124__fcom001_contract_order_scope_forward_migration.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V207__received_fcom001_contract_order_scope_forward_migration.sql`
- `sql/migrations/V125__fcom001_permissions_menu_and_acceptance_seed.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V208__received_fcom001_permissions_menu_and_acceptance_seed.sql`
- `sql/migrations/V126__fcom001_stage_entry_acceptance_seed.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V209__received_fcom001_stage_entry_acceptance_seed.sql`
- `sql/migrations/V127__fcom001_acceptance_identity_authorization_fix.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V210__received_fcom001_acceptance_identity_authorization_fix.sql`
- `sql/migrations/V128__facc001_acceptance_report_version_forward.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V211__received_facc001_acceptance_report_version_forward.sql`
- `sql/migrations/V129__facc001_acceptance_role_menu_ancestor_fix.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V212__received_facc001_acceptance_role_menu_ancestor_fix.sql`
- `sql/migrations/V130__facc001_acceptance_activity_contract_identity_fix.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V213__received_facc001_acceptance_activity_contract_identity_fix.sql`
- `sql/migrations/V131__facc001_acceptance_report_jobs.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V214__received_facc001_acceptance_report_jobs.sql`
- `sql/migrations/V132__facc001_acceptance_project_query_permission.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V215__received_facc001_acceptance_project_query_permission.sql`
- `sql/migrations/V133__fimp002_arrival_acceptance.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V134__fimp002_project_qualification_versions.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V135__fimp002_file_fact_versions.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V136__fimp002_nullable_difference_fact_version.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V137__fimp002_arrival_evidence_outbox_job.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V138__fimp002_evidence_correlation.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V139__fimp002_arrival_evidence_retry_job.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V133__facc002_satisfaction_questionnaire_result_forward.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V216__received_facc002_satisfaction_questionnaire_result_forward.sql`
- `sql/migrations/V140__fimp002_task5b_successor_fact_impact.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V141__fimp002_successor_batch_identity.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V142__fimp002_arrival_acceptance_seed.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V143__fcom001_contract_order_scope_schema.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V217__received_fcom001_contract_order_scope_schema.sql`
- `sql/migrations/V144__platform_migration_evidence.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V145__fcom001_order_contract_relation_source_identity.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V218__received_fcom001_order_contract_relation_source_identity.sql`
- `sql/migrations/V134__restore_project_task_assign_permission.sql` — `RENUMBER_ACTIVE_MIGRATION` — `sql/migrations/V219__received_restore_project_task_assign_permission.sql`
- `sql/migrations/V146__fcut002_cutover_task_intake.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V147__fcut003_p3_dynamic_checklist.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V148__fcut_device_product_type_snapshot.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V149__fcut002_task_origin_assessment_checks.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V150__fcut004_p4_cutover_plan.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V151__fcut004_legacy_plan_job.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V152__fcut004_plan_seed.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V153__fcut005_p5_graded_approval.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V154__fcut005_p5_approval_seed.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V155__fcut006_p6_cutover_closure.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V156__fcut006_legacy_closure_job.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V157__fcut008_p5_lead_time_notification.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V158__fcut008_notification_correlation_provenance.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V159__fcut008_external_notification_job_seed.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V160__fcut009_navigation_rule.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``
- `sql/migrations/V161__fcut010_spare_system_coordination.sql` — `REMOVE_EXACT_DUPLICATE_MIGRATION` — ``

## Requirement追溯重建

- generate退出码：`0`
- --check退出码：`0`
```text
WROTE docs/traceability/requirement-matrix.md
WROTE docs/traceability/requirement-version-coverage.json
[PASS] requirement traceability is current: docs/traceability/requirement-matrix.md / docs/traceability/requirement-version-coverage.json
```
