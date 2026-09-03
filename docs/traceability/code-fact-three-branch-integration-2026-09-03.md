# 三分支代码事实选择性合入报告

- 基线：`99283780ce302b2d03027e59411a526db263ecea`
- 代码整合Head：`3eede84b50393058827230b874ab60bef0615f31`
- 来源：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`
- 原则：局部不符合项仅阻断对应文件；其他提交、文件继续接收。
- Feature状态：保持原值；接收代码不自动提升Implementation Done。

## 决策汇总

| 决策 | 数量 |
|---|---:|
| `APPLIED_CODE` | 198 |
| `APPLIED_CONFLICT_NEW_FILE` | 2 |
| `APPLIED_MIGRATION_RENUMBERED` | 9 |
| `CONFLICT_OURS_ADAPT_REQUIRED` | 851 |
| `EXCLUDED_DUPLICATE_OWNER` | 10 |
| `EXCLUDED_NON_CODE` | 1450 |
| `MIGRATION_ADAPT_REQUIRED` | 14 |
| `NOOP_MIGRATION_EXACT` | 21 |
| `NO_NET_CODE_CHANGE` | 484 |

## Feature汇总

| Feature | 已接收路径记录 | 冲突/适配记录 |
|---|---:|---:|
| F-ACC-001 | 0 | 27 |
| F-ACC-002 | 19 | 91 |
| F-AST-001 | 0 | 0 |
| F-AST-002 | 0 | 0 |
| F-COM-001 | 0 | 0 |
| F-CUT-001 | 19 | 22 |
| F-CUT-002 | 2 | 39 |
| F-CUT-003 | 2 | 69 |
| F-CUT-004 | 11 | 62 |
| F-CUT-005 | 2 | 114 |
| F-CUT-006 | 6 | 41 |
| F-CUT-007 | 0 | 6 |
| F-CUT-008 | 0 | 0 |
| F-CUT-009 | 0 | 0 |
| F-CUT-010 | 2 | 9 |
| F-CUT-011 | 0 | 0 |
| F-IMP-001 | 12 | 0 |
| F-IMP-002 | 4 | 140 |
| F-IMP-003 | 0 | 0 |
| F-IMP-004 | 0 | 0 |
| F-IMP-005 | 0 | 0 |
| F-INT-012 | 0 | 20 |
| F-PROJ-006 | 0 | 0 |
| UNMAPPED | 130 | 237 |

## 主干净新增/修改代码路径

- `"\351\234\200\346\261\202/PRD-\351\241\271\347\233\256\345\256\236\346\226\275\344\272\244\344\273\230\347\256\241\347\220\206\345\271\263\345\217\260.md"`
- `"docs/reports/2026-08-29-PRD-V1.8\344\277\256\350\256\242008\345\237\272\347\272\277\345\217\230\346\233\264\346\212\245\345\221\212.md"`
- `"docs/reports/2026-08-29-PRD-V1.8\344\277\256\350\256\242009\345\237\272\347\272\277\345\217\230\346\233\264\346\212\245\345\221\212.md"`
- `"docs/reports/2026-08-30-PRD-V1.8\344\277\256\350\256\242010\345\237\272\347\272\277\345\217\230\346\233\264\346\212\245\345\221\212.md"`
- `"specs/001-project-delivery-platform/domains/ACC-\351\252\214\346\224\266\344\270\216\351\241\271\347\233\256\351\227\255\347\216\257\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `"specs/001-project-delivery-platform/domains/COM-\345\220\210\345\220\214\350\256\242\345\215\225\345\261\245\347\272\246\351\234\200\346\261\202\350\247\204\346\240\274.md"`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/authority/ContractDO.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/authority/SalesOrderDO.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/scope/DeliveryScopeDetailDO.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/scope/OrderLineDO.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/scope/ProjectContractRelationDO.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/CommerceDeliveryScopeCommandMapper.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/query/CommerceDeliveryScopeCommandQuery.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/domain/scope/DeliveryScopeStateMachine.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/domain/scope/DeliveryScopeValidationRules.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandException.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandService.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommands.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeviceAndLocationFactAdapter.java`
- `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/ProjectScopeQualificationAdapter.java`
- `pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml`
- `pms-module-commerce/src/main/resources/mapper/scope/CommerceDeliveryScopeCommandMapper.xml`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001MigrationContractTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001MigrationMySqlTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001RelationIdentityMigrationContractTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/api/CommercePublicContractTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/CommerceDeliveryScopeCommandMapperContractTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeAuditContractTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandMySqlTest.java`
- `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeviceAndLocationFactAdapterTest.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/CustomerServiceLevelFactApi.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/CustomerServiceLevelFactException.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/dto/CustomerServiceLevelFact.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/dto/CustomerServiceLevelFactQuery.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/dto/CustomerServiceLevelFactResult.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/dto/CustomerServiceLevelFactRevalidationQuery.java`
- `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/dto/ExpectedCustomerServiceLevelFact.java`
- `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/api/servicelevel/CustomerServiceLevelFactApiContractTest.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApi.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessException.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessContextFact.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessResult.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessRevalidationQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessSnapshotFact.java`
- `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalLineMapper.xml`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApiContractTest.java`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/preparation/PreparationMySqlIntegrationTest.java`
- `pms-module-platform/pom.xml`
- `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImplTest.java`
- `pom.xml`
- `scripts/generate_domain_entity_migration_contract.py`
- `scripts/generate_phase2_contract_map.py`
- `scripts/tests/test_fcom001_feature_contract.py`
- `scripts/tests/test_fcom001_v126_migration.py`
- `scripts/tests/test_fcom001_v127_migration.py`
- `scripts/tests/test_fcut002_migration_contract.py`
- `scripts/tests/test_fcut003_feature_contract.py`
- `scripts/tests/test_fcut004_feature_contract.py`
- `scripts/tests/test_prd_com_acceptance_scope_trigger.py`
- `scripts/tests/test_prd_satisfaction_questionnaire_configuration.py`
- `scripts/tests/test_specification_baseline.py`
- `scripts/tests/test_validate_domain_entity_migration_alignment.py`
- `scripts/tests/test_validate_sds_phase2.py`
- `scripts/validate_sds_phase2.py`
- `scripts/validate_sds_phase3.py`
- `sql/migrations/V204__received_fcom001_contract_order_scope_forward_migration.sql`
- `sql/migrations/V205__received_facc001_acceptance_report_version_forward.sql`
- `sql/migrations/V206__received_facc002_satisfaction_questionnaire_result_forward.sql`
- `sql/migrations/V207__received_device_ops_integration_foundation.sql`
- `sql/migrations/V208__received_device_ops_callback_consumption.sql`
- `sql/migrations/V209__received_fcom001_contract_order_scope_schema.sql`
- `sql/migrations/V210__received_fcut003_p3_dynamic_checklist.sql`
- `sql/migrations/V211__received_fcut004_p4_cutover_plan.sql`
- `sql/migrations/V212__received_fcut006_p6_cutover_closure.sql`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/FileClient.java`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/db/DBFileClient.java`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/ftp/FtpFileClient.java`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/local/LocalFileClient.java`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/s3/S3FileClient.java`
- `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/file/core/client/sftp/SftpFileClient.java`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`

## 未自动接收项

以下项只在对应文件/迁移层阻断，不构成整分支拒绝：

- `codex/f-acc-001-sds@26531772ea6f` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(sds): 锁定COM办事处与验收守卫差量
- `codex/f-acc-001-sds@20f03ba316ca` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(sds): 补齐COM V70必填字段转换
- `codex/f-acc-001-sds@b17ae89f92b0` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(sds): 锁定验收阶段范围绑定时序
- `codex/f-acc-001-sds@bcbeffd60804` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 整改COM Feature物理契约
- `codex/f-acc-001-sds@7ed8801a536f` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 补齐真实Provider复用审计
- `codex/f-acc-001-sds@dbfc8e557185` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 锁定冲突通知与序列号校验
- `codex/f-acc-001-sds@c57ee7b5f522` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 提交合同订单Feature完整复审
- `codex/f-acc-001-sds@ead6c8bf3eca` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 回写合同订单Feature Ready裁决
- `codex/f-acc-001-sds@3412e3839777` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(commerce): 隔离V72受管种子转换
- `codex/f-acc-001-sds@c541126b644f` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/CommerceAuthorityWriteApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同范围协作契约
- `codex/f-acc-001-sds@c541126b644f` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/AuthorityWriteResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同范围协作契约
- `codex/f-acc-001-sds@c541126b644f` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/CommerceAuthorityWriteCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同范围协作契约
- `codex/f-acc-001-sds@c541126b644f` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/commerce/dto/ProjectOfficeFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同范围协作契约
- `codex/f-acc-001-sds@c541126b644f` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/Fcom001PublicApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同范围协作契约
- `codex/f-acc-001-sds@3b9e680a0ede` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/commerce/ProjectOfficeFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 提供合同范围项目事实
- `codex/f-acc-001-sds@3b9e680a0ede` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/commerce/ProjectOfficeFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 提供合同范围项目事实
- `codex/f-acc-001-sds@6490d44c035b` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancescope/AcceptanceScopeBindingDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 新增验收范围锁定事实
- `codex/f-acc-001-sds@6490d44c035b` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 新增验收范围锁定事实
- `codex/f-acc-001-sds@6490d44c035b` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptancescope/AcceptanceScopeBindingServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 新增验收范围锁定事实
- `codex/f-acc-001-sds@9fd37981611e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/DeliveryScopeMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 提供验收范围稳定锁读
- `codex/f-acc-001-sds@9fd37981611e` `pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 提供验收范围稳定锁读
- `codex/f-acc-001-sds@43d63dcd50b6` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/migration/Fcom001MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现V124原子前向切换
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/contract/ContractDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/order/SalesOrderDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/order/SalesOrderLineDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/contract/ContractMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/contract/ProjectContractRelationMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/contract/query/ContractCompanyScopeQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/contract/query/ContractDetailScopeQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/SalesOrderLineMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/SalesOrderMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/query/SalesOrderCompanyScopeQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/query/SalesOrderLineCompanyScopeQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractAccessService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractRelationCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/resources/mapper/contract/ProjectContractRelationMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractAccessServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@aabf19d70097` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractRelationCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现权威副本与合同公司范围
- `codex/f-acc-001-sds@cc03787ec9c7` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 补齐无SN范围产品事实
- `codex/f-acc-001-sds@cc03787ec9c7` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 补齐无SN范围产品事实
- `codex/f-acc-001-sds@cc03787ec9c7` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 补齐无SN范围产品事实
- `codex/f-acc-001-sds@cc03787ec9c7` `sql/migrations/V125__fcom001_permissions_menu_and_acceptance_seed.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/SalesOrderLineMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/DeliveryScopeMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeCompatibilityService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeCompatibilityServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@05af6f3685ce` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通范围Owner版本与兼容分配
- `codex/f-acc-001-sds@63b6efc0a149` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/DeliveryScopeMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 冻结ERP减量冲突范围
- `codex/f-acc-001-sds@63b6efc0a149` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 冻结ERP减量冲突范围
- `codex/f-acc-001-sds@63b6efc0a149` `pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 冻结ERP减量冲突范围
- `codex/f-acc-001-sds@63b6efc0a149` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityWriteServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 冻结ERP减量冲突范围
- `codex/f-acc-001-sds@b6c0176c9ad0` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现范围调整与释放命令
- `codex/f-acc-001-sds@b6c0176c9ad0` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeAssignCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现范围调整与释放命令
- `codex/f-acc-001-sds@b6c0176c9ad0` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeChangeCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现范围调整与释放命令
- `codex/f-acc-001-sds@b6c0176c9ad0` `pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现范围调整与释放命令
- `codex/f-acc-001-sds@b6c0176c9ad0` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现范围调整与释放命令
- `codex/f-acc-001-sds@f25e0ebfd38c` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeCompatibilityService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 原子绑定验收阶段内新范围
- `codex/f-acc-001-sds@f25e0ebfd38c` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 原子绑定验收阶段内新范围
- `codex/f-acc-001-sds@f25e0ebfd38c` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeCompatibilityServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 原子绑定验收阶段内新范围
- `codex/f-acc-001-sds@21c07181b666` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectStageInstanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 原子进入项目验收阶段
- `codex/f-acc-001-sds@21c07181b666` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectStageStatusUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 原子进入项目验收阶段
- `codex/f-acc-001-sds@21c07181b666` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectgovernance/ProjectStageSnapshotRules.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 原子进入项目验收阶段
- `codex/f-acc-001-sds@21c07181b666` `pms-module-project/src/main/resources/mapper/projectmanual/ProjectStageInstanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 原子进入项目验收阶段
- `codex/f-acc-001-sds@a8418dbb6800` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/order/SalesOrderMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 收紧合同访问与关系范围
- `codex/f-acc-001-sds@a8418dbb6800` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractAccessService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 收紧合同访问与关系范围
- `codex/f-acc-001-sds@a8418dbb6800` `pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 收紧合同访问与关系范围
- `codex/f-acc-001-sds@a8418dbb6800` `pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 收紧合同访问与关系范围
- `codex/f-acc-001-sds@a8418dbb6800` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractAccessServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 收紧合同访问与关系范围
- `codex/f-acc-001-sds@06593a427960` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增交付范围授权查询
- `codex/f-acc-001-sds@06593a427960` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增交付范围授权查询
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/contract/ContractController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/order/OrderController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/DeliveryScopeController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/vo/DeliveryScopeAdjustReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/vo/DeliveryScopeAssignReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/vo/DeliveryScopePreviewReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/vo/DeliveryScopeReleaseReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopePreviewCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@76a3dfc7721e` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 接通商务与验收阶段REST
- `codex/f-acc-001-sds@65639c9c913c` `scripts/tests/test_fcom001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 完成V125权限与验收种子
- `codex/f-acc-001-sds@65639c9c913c` `sql/migrations/V125__fcom001_permissions_menu_and_acceptance_seed.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@835a1a57f17b` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同与交付范围工作台
- `codex/f-acc-001-sds@835a1a57f17b` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/commercePages.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同与交付范围工作台
- `codex/f-acc-001-sds@835a1a57f17b` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/contracts/detail.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同与交付范围工作台
- `codex/f-acc-001-sds@835a1a57f17b` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/delivery-scope/DeliveryScopeEditor.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同与交付范围工作台
- `codex/f-acc-001-sds@835a1a57f17b` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/delivery-scope/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增合同与交付范围工作台
- `codex/f-acc-001-sds@ac8a6c9a39ed` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/Fcom001ApplicationMySqlIntegrationTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 修复V125并补全真实事务验证
- `codex/f-acc-001-sds@0eb01df12559` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/authority/CommerceAuthorityImportController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增权威批次受控导入
- `codex/f-acc-001-sds@0eb01df12559` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/authority/vo/CommerceAuthorityImportBatchReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增权威批次受控导入
- `codex/f-acc-001-sds@0eb01df12559` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityImportApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增权威批次受控导入
- `codex/f-acc-001-sds@0eb01df12559` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/authority/CommerceAuthorityImportControllerTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增权威批次受控导入
- `codex/f-acc-001-sds@0eb01df12559` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityImportApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 新增权威批次受控导入
- `codex/f-acc-001-sds@7d578e3749e8` `sql/migrations/V126__fcom001_stage_entry_acceptance_seed.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@32092b115a32` `sql/migrations/V127__fcom001_acceptance_identity_authorization_fix.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@3a78e2beee6e` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/contract/ContractController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 隔离合同商务敏感字段
- `codex/f-acc-001-sds@3a78e2beee6e` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/contract/ContractControllerTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 隔离合同商务敏感字段
- `codex/f-acc-001-sds@a57c23c861d1` `scripts/tests/run_fcom001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(commerce): 完成真实浏览器验收
- `codex/f-acc-001-sds@5e56728152f6` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 闭合公开读取与业务拒绝语义
- `codex/f-acc-001-sds@5e56728152f6` `pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 闭合公开读取与业务拒绝语义
- `codex/f-acc-001-sds@5e56728152f6` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 闭合公开读取与业务拒绝语义
- `codex/f-acc-001-sds@5e56728152f6` `scripts/tests/run_fcom001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 闭合公开读取与业务拒绝语义
- `codex/f-acc-001-sds@20bca44b9aa1` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 形成验收报告版本SDS差量
- `codex/f-acc-001-sds@5c1e1ff2498a` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合报告换版与撤销契约
- `codex/f-acc-001-sds@b628ee3eb189` `scripts/tests/test_facc001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 形成报告版本Feature候选
- `codex/f-acc-001-sds@bde0feac019b` `scripts/tests/test_facc001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合Feature文件与活动契约
- `codex/f-acc-001-sds@9f3d31100c58` `scripts/tests/test_facc001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 回写Feature Ready裁决
- `codex/f-acc-001-sds@fca9626c4fce` `scripts/tests/test_facc001_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合报告Technical Plan执行边界
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/ArchiveFileReferenceSetsCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/ExistingFileReferenceTarget.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/dto/AcceptanceActivityCompletionCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancereport/AcceptanceReportVersionDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance/AccProjectDeliverableMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/AcceptanceActivityMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/AcceptanceReportVersionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/resources/mapper/acceptance/AccProjectDeliverableMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/resources/mapper/acceptancereport/AcceptanceActivityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@fec7c69e3892` `pms-module-project/src/main/resources/mapper/acceptancereport/AcceptanceReportVersionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告文件与活动Owner基础
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/AcceptanceActivityCompletionFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancereport/ProjectDeliverableSourceVersionDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance/AccProjectDeliverableMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/AcceptanceActivityMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/ProjectDeliverableSourceVersionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportArchiveCompensationJob.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportArchiveCompensationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportOutboxDeliveryJob.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/resources/mapper/acceptance/AccProjectDeliverableMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/resources/mapper/acceptancereport/AcceptanceActivityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/main/resources/mapper/acceptancereport/ProjectDeliverableSourceVersionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/AcceptanceActivityCompletionFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@8d582aea1aa4` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportOutboxDeliveryJobTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现报告版本与归档补偿
- `codex/f-acc-001-sds@e31f08b31fe2` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 接入验收活动执行契约
- `codex/f-acc-001-sds@e31f08b31fe2` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 接入验收活动执行契约
- `codex/f-acc-001-sds@e31f08b31fe2` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 接入验收活动执行契约
- `codex/f-acc-001-sds@229e9f4b946c` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/migration/Facc001MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告版本前向迁移
- `codex/f-acc-001-sds@ba8a4def8583` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 开放验收报告管理接口
- `codex/f-acc-001-sds@ba8a4def8583` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 开放验收报告管理接口
- `codex/f-acc-001-sds@0b1671cb93a8` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/Facc001ApplicationMySqlIntegrationTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告页面与真实验收准备
- `codex/f-acc-001-sds@0b1671cb93a8` `scripts/tests/run_facc001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 建立报告页面与真实验收准备
- `codex/f-acc-001-sds@3a27eeffb1f0` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/migration/Facc001MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 收口正式验收迁移与调度
- `codex/f-acc-001-sds@3a27eeffb1f0` `scripts/tests/run_facc001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 收口正式验收迁移与调度
- `codex/f-acc-001-sds@3a27eeffb1f0` `sql/migrations/V129__facc001_acceptance_role_menu_ancestor_fix.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@3a27eeffb1f0` `sql/migrations/V130__facc001_acceptance_activity_contract_identity_fix.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@3a27eeffb1f0` `sql/migrations/V131__facc001_acceptance_report_jobs.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@41a71649420e` `pms-module-project/src/main/resources/mapper/acceptancereport/ProjectDeliverableSourceVersionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 闭合归档补偿与正式验收证据
- `codex/f-acc-001-sds@41a71649420e` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/migration/Facc001MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 闭合归档补偿与正式验收证据
- `codex/f-acc-001-sds@41a71649420e` `scripts/tests/run_facc001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 闭合归档补偿与正式验收证据
- `codex/f-acc-001-sds@41a71649420e` `sql/migrations/V132__facc001_acceptance_project_query_permission.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-acc-001-sds@fea30001c987` `scripts/tests/run_facc001_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(acceptance): 补齐跨范围下载验收证据
- `codex/f-acc-001-sds@54ec4e00789b` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 锁定满意度问卷与归档来源契约
- `codex/f-acc-001-sds@b98d0caafb72` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合满意度应交根与整改身份
- `codex/f-acc-001-sds@38901259fd59` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 形成满意度Feature契约
- `codex/f-acc-001-sds@145e4a61ea93` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合满意度Feature契约边界
- `codex/f-acc-001-sds@27f5bcb2c451` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 回写满意度Feature Ready裁决
- `codex/f-acc-001-sds@a55567ce12c9` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 锁定满意度结果生成文件契约
- `codex/f-acc-001-sds@700b659d7e11` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(platform): 锁定统一异步导出公共契约
- `codex/f-acc-001-sds@41f92526919e` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 闭合满意度实施计划导出链
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/export/ExportTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/export/ExportFileExpirationJob.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/export/ExportTaskExecutionJob.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/export/ExportTaskExecutionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/export/PlatformExportFilePolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/export/PlatformExportFileWriter.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@a43ed4c1c7e7` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/migration/Facc002MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现统一异步导出基础能力
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/dto/SatisfactionResultFactQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/AcceptanceActivityCompletionFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/SatisfactionTaskInitializationApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionCollectionTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionQuestionnaireMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionQuestionnaireTemplateMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionQuestionnaireTemplateRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectWorkBindingFactMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionCollectionTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionQuestionnaireTemplateMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionQuestionnaireTemplateRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectWorkBindingFactMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/AcceptanceActivityCompletionFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/SatisfactionTaskInitializationApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@1cb0461f3424` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/migration/Facc002MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 初始化满意度采集任务
- `codex/f-acc-001-sds@fab9e06f98d4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/SatisfactionResultFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度结果Owner事实
- `codex/f-acc-001-sds@fab9e06f98d4` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResultMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度结果Owner事实
- `codex/f-acc-001-sds@fab9e06f98d4` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionResultMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度结果Owner事实
- `codex/f-acc-001-sds@a276347d44fa` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度受控访问链接
- `codex/f-acc-001-sds@a276347d44fa` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度受控访问链接
- `codex/f-acc-001-sds@a276347d44fa` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionAccessGrantMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度受控访问链接
- `codex/f-acc-001-sds@a276347d44fa` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionAccessGrantService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度受控访问链接
- `codex/f-acc-001-sds@a276347d44fa` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionAccessGrantMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 提供满意度受控访问链接
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionCollectionTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResponseFileMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResponseMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionCollectionTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionResponseMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@120605575c50` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 持久化满意度不可变答卷
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileBusinessObjectPolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileBusinessObjectPolicyRegistry.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResultMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultFilePolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionResultMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@b9c0686a1615` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultFilePolicyProviderTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现Result文件Owner重验
- `codex/f-acc-001-sds@369c92bd21e9` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 支持Result生成文件原子持久化
- `codex/f-acc-001-sds@369c92bd21e9` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileUploadSessionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 支持Result生成文件原子持久化
- `codex/f-acc-001-sds@369c92bd21e9` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 支持Result生成文件原子持久化
- `codex/f-acc-001-sds@369c92bd21e9` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 支持Result生成文件原子持久化
- `codex/f-acc-001-sds@369c92bd21e9` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileContractAndMapperTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 支持Result生成文件原子持久化
- `codex/f-acc-001-sds@9f178dc322db` `scripts/generate_domain_entity_migration_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 锁定可配置问卷计分契约
- `codex/f-acc-001-sds@b7231e73eacd` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/satisfaction/SatisfactionQuestionnaireDefinition.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现可配置问卷确定性计分
- `codex/f-acc-001-sds@b7231e73eacd` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现可配置问卷确定性计分
- `codex/f-acc-001-sds@b7231e73eacd` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 实现可配置问卷确定性计分
- `codex/f-acc-001-sds@98feb4564a8c` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionCollectionTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 形成满意度判定结果事务
- `codex/f-acc-001-sds@98feb4564a8c` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResultFileMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 形成满意度判定结果事务
- `codex/f-acc-001-sds@98feb4564a8c` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 形成满意度判定结果事务
- `codex/f-acc-001-sds@98feb4564a8c` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 形成满意度判定结果事务
- `codex/f-acc-001-sds@e83cda3ff05d` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通公开满意度答卷判定
- `codex/f-acc-001-sds@e83cda3ff05d` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/vo/SatisfactionPublicResponseSubmitReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通公开满意度答卷判定
- `codex/f-acc-001-sds@e83cda3ff05d` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionPublicSubmissionApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通公开满意度答卷判定
- `codex/f-acc-001-sds@e83cda3ff05d` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通公开满意度答卷判定
- `codex/f-acc-001-sds@e83cda3ff05d` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionPublicSubmissionApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通公开满意度答卷判定
- `codex/f-acc-001-sds@4f6d51bd6786` `scripts/tests/test_facc002_feature_contract.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — docs(acceptance): 锁定grant上传Response身份
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileBusinessObjectPolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/BusinessGrantFileUploadService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileBusinessObjectPolicyRegistry.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/BusinessGrantFileUploadServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionQuestionnairePublicController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseFilePolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionPublicSubmissionApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseFilePolicyProviderTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@98e4ae22a7f2` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通受控问卷文件上传
- `codex/f-acc-001-sds@57b2dcd223b9` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/BusinessGrantFileUploadService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 绑定grant上传重放身份
- `codex/f-acc-001-sds@57b2dcd223b9` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/BusinessGrantFileUploadServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 绑定grant上传重放身份
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultQuartzRegistrar.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultSourceProjectionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/event/SatisfactionResultVersionChangedMessage.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@0f3769755f59` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultSourceProjectionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果来源投影
- `codex/f-acc-001-sds@4a84f6f9e649` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance/AccProjectDeliverableMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 清理失效满意度来源指针
- `codex/f-acc-001-sds@4a84f6f9e649` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultSourceProjectionMySqlIntegrationTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(acceptance): 清理失效满意度来源指针
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/SatisfactionTaskInitializationApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/satisfaction/SatisfactionResultMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseFilePolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultDecisionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResultFilePolicyProvider.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/main/resources/mapper/satisfaction/SatisfactionResultMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@6ec1b2459a43` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseFilePolicyProviderTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度待办与归档补偿
- `codex/f-acc-001-sds@0ffaebe3d1c3` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionTemplateController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度模板管理
- `codex/f-acc-001-sds@e3cc9eed1982` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度任务管理
- `codex/f-acc-001-sds@e3cc9eed1982` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionTaskManagementService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度任务管理
- `codex/f-acc-001-sds@d00501f486af` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionResultController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度结果管理
- `codex/f-acc-001-sds@5f4054e56799` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/satisfaction/SatisfactionTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通现场协助答卷
- `codex/f-acc-001-sds@5f4054e56799` `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/satisfaction/SatisfactionResponseSubmissionService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通现场协助答卷
- `codex/f-acc-001-sds@0b832c37d0bf` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/satisfaction/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度前端闭环
- `codex/f-acc-001-sds@0b832c37d0bf` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/satisfaction/TaskPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 接通满意度前端闭环
- `codex/f-acc-001-sds@486727a3a856` `scripts/tests/run_facc002_browser_acceptance.cjs` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(acceptance): 完成满意度纵向闭环
- `codex/f-acc-001-sds@486727a3a856` `sql/migrations/V134__restore_project_task_assign_permission.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-integration/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/collection/CollectionTaskDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/CollectionBatchMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/CollectionTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/CredentialTokenService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/resources/mapper/collection/CollectionBatchMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `pms-module-platform/src/main/resources/mapper/collection/CollectionTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/ArtifactFileApi.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileCreateCommand.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileVersionDTO.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileArtifactDO.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileVersionDO.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileArtifactMapper.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileVersionMapper.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileApiImpl.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileService.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@842580591170` `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileServiceTest.java` — `EXCLUDED_DUPLICATE_OWNER` — feat(integration): 建立 Device Ops 集成基础能力
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `pms-module-platform/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现设备凭证授权与一次性取密
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现设备凭证授权与一次性取密
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/CollectionTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现设备凭证授权与一次性取密
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `pms-module-platform/src/main/resources/mapper/collection/CollectionTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现设备凭证授权与一次性取密
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/DeviceCredentialControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 实现设备凭证授权与一次性取密
- `prereq-parallel-check-kKiAdn@d2d1765ffe14` `sql/migrations/V105__device_ops_credentials_and_dispatch.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-cut-001-matrices@e08898b57e6c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 扩展矩阵类别与绑定必填契约
- `codex/f-cut-001-matrices@c0dcf2051a0e` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 联合校验风险与调研矩阵发布
- `codex/f-cut-001-matrices@1a61ea895a2d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-config/components/cutoverMatrix.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 增加风险与调研矩阵编辑界面
- `codex/f-cut-001-matrices@f8a83538cd03` `tests/e2e/fcut001_browser_acceptance.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(test): 添加f-cut-001端到端浏览器验收测试
- `codex/f-cut-001-matrices@54383436951d` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/FileArtifactApiAdapter.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接入到货签收项目与文件事实
- `codex/f-cut-001-matrices@54383436951d` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ProjectQualificationApiAdapter.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接入到货签收项目与文件事实
- `codex/f-cut-001-matrices@54383436951d` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/FileArtifactFactPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接入到货签收项目与文件事实
- `codex/f-cut-001-matrices@54383436951d` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/ProjectQualificationPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接入到货签收项目与文件事实
- `codex/f-cut-001-matrices@54383436951d` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ArrivalAcceptanceOwnerAdapterTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接入到货签收项目与文件事实
- `codex/f-cut-001-matrices@2bb1dbc0b34a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收Owner表
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/ArrivalAcceptanceDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/ArrivalDifferenceDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/DeliveryEvidenceDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/DeliveryEvidenceRevisionDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalDifferenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalLineMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/DeliveryEvidenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/ArrivalPageQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalDifferenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalLineMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@c071450250a7` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立到货签收持久化映射
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalDifferenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalLineMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalDifferenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@8370d0f19be6` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 补齐到货签收项目事实查询
- `codex/f-cut-001-matrices@b563913fad5c` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ProjectQualificationApiAdapter.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 分离签收经理事实与操作范围
- `codex/f-cut-001-matrices@b563913fad5c` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/ProjectQualificationPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 分离签收经理事实与操作范围
- `codex/f-cut-001-matrices@b563913fad5c` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ArrivalAcceptanceOwnerAdapterTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 分离签收经理事实与操作范围
- `codex/f-cut-001-matrices@a3099280c782` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 持久化签收项目资格版本
- `codex/f-cut-001-matrices@08ee613b59ea` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收草稿创建核心
- `codex/f-cut-001-matrices@08ee613b59ea` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeliveryScopePort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收草稿创建核心
- `codex/f-cut-001-matrices@08ee613b59ea` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeviceScopeFactPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收草稿创建核心
- `codex/f-cut-001-matrices@08ee613b59ea` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收草稿创建核心
- `codex/f-cut-001-matrices@0cf2ba79aea9` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 补全签收资格版本重验投影
- `codex/f-cut-001-matrices@0cf2ba79aea9` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 补全签收资格版本重验投影
- `codex/f-cut-001-matrices@0cf2ba79aea9` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ArrivalAcceptanceOwnerAdapterTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 补全签收资格版本重验投影
- `codex/f-cut-001-matrices@dd374c5f8ae4` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 冻结签收证据文件事实版本
- `codex/f-cut-001-matrices@774aeb11bd66` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收提交锁定支撑
- `codex/f-cut-001-matrices@774aeb11bd66` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收提交锁定支撑
- `codex/f-cut-001-matrices@774aeb11bd66` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收提交锁定支撑
- `codex/f-cut-001-matrices@0a56196a3864` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收提交核心
- `codex/f-cut-001-matrices@0a56196a3864` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收提交核心
- `codex/f-cut-001-matrices@0267ef4d0d4f` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 累计签收历史确认范围
- `codex/f-cut-001-matrices@0267ef4d0d4f` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 累计签收历史确认范围
- `codex/f-cut-001-matrices@65a6b395e692` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 校正签收差异事实版本契约
- `codex/f-cut-001-matrices@f1ecb73d7a75` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 累计签收有效豁免范围
- `codex/f-cut-001-matrices@f1ecb73d7a75` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 累计签收有效豁免范围
- `codex/f-cut-001-matrices@6ce766596c81` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收事实版本分配集合
- `codex/f-cut-001-matrices@6ce766596c81` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收事实版本分配集合
- `codex/f-cut-001-matrices@6ce766596c81` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 建立签收事实版本分配集合
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/DeliveryEvidenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/DeliveryEvidencePublishUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@18c2b0ec569f` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收确认事务
- `codex/f-cut-001-matrices@7ea868e86cb5` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceOutboxDeliveryJob.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据暂停投递任务
- `codex/f-cut-001-matrices@7ea868e86cb5` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据暂停投递任务
- `codex/f-cut-001-matrices@7ea868e86cb5` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceOutboxDeliveryJobTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据暂停投递任务
- `codex/f-cut-001-matrices@ddc928d02f19` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/DeliveryEvidenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据回执消费
- `codex/f-cut-001-matrices@ddc928d02f19` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArtifactCallbackHandler.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据回执消费
- `codex/f-cut-001-matrices@ddc928d02f19` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据回执消费
- `codex/f-cut-001-matrices@ddc928d02f19` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据回执消费
- `codex/f-cut-001-matrices@ddc928d02f19` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArtifactCallbackHandlerTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据回执消费
- `codex/f-cut-001-matrices@351d8bbb0aa4` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 冻结签收证据发布关联链
- `codex/f-cut-001-matrices@351d8bbb0aa4` `sql/migrations/V138__fimp002_evidence_correlation.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-cut-001-matrices@e34930bc2c2c` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 修复签收关联迁移重跑约束
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/DeliveryEvidenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceRetryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceRetryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@1eec2fbc12fc` `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现签收证据双阶段重试
- `codex/f-cut-001-matrices@9561384bda94` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛签收证据重试事务
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalDifferenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@ce0447ecb867` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收项目事实查询
- `codex/f-cut-001-matrices@dfcc224c842a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货事实范围与陈旧判定
- `codex/f-cut-001-matrices@dfcc224c842a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeliveryScopePort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货事实范围与陈旧判定
- `codex/f-cut-001-matrices@dfcc224c842a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeviceScopeFactPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货事实范围与陈旧判定
- `codex/f-cut-001-matrices@dfcc224c842a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/OwnerFactVersionMismatchException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货事实范围与陈旧判定
- `codex/f-cut-001-matrices@dfcc224c842a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货事实范围与陈旧判定
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalDifferenceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommands.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalDifferenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalLineMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@80a8d4221a2a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收 Task 5B 基础命令
- `codex/f-cut-001-matrices@0564ec7e1194` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 修正到货后继批次唯一模型
- `codex/f-cut-001-matrices@0564ec7e1194` `sql/migrations/V141__fimp002_successor_batch_identity.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-cut-001-matrices@0750182e7a50` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 拒绝空批次根标记
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalDifferenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@935324cf381a` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收后继链
- `codex/f-cut-001-matrices@1b2b6a752def` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收后继运行边界
- `codex/f-cut-001-matrices@1b2b6a752def` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收后继运行边界
- `codex/f-cut-001-matrices@1b2b6a752def` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceSuccessorMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收后继运行边界
- `codex/f-cut-001-matrices@808151ce4662` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 补全到货命令关联事实
- `codex/f-cut-001-matrices@c649c4245b3e` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收口到货签收应用动作
- `codex/f-cut-001-matrices@fb69dbcc07c8` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收REST契约
- `codex/f-cut-001-matrices@fb69dbcc07c8` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收REST契约
- `codex/f-cut-001-matrices@b63b5a0c4781` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收REST边界
- `codex/f-cut-001-matrices@b63b5a0c4781` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收REST边界
- `codex/f-cut-001-matrices@b63b5a0c4781` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceContractException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收REST边界
- `codex/f-cut-001-matrices@b63b5a0c4781` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeviceScopeFactPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收REST边界
- `codex/f-cut-001-matrices@b63b5a0c4781` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收REST边界
- `codex/f-cut-001-matrices@871cfcbb5e5c` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 初始化到货签收运行资源
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/arrival-acceptance/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalAcceptanceForm.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalDifferencePanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalEvidencePanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalLineEditor.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@9e42fbc3279d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 实现到货签收前端工作台
- `codex/f-cut-001-matrices@35c0db90a3ad` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收前端契约
- `codex/f-cut-001-matrices@35c0db90a3ad` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收前端契约
- `codex/f-cut-001-matrices@35c0db90a3ad` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛到货签收前端契约
- `codex/f-cut-001-matrices@99bc69ff4c0a` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 统一到货签收写前刷新屏障
- `codex/f-cut-001-matrices@99bc69ff4c0a` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 统一到货签收写前刷新屏障
- `codex/f-cut-001-matrices@99bc69ff4c0a` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 统一到货签收写前刷新屏障
- `codex/f-cut-001-matrices@9edd1a41d1e5` `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/DeviceScopeInvalidItem.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 冻结设备范围事实公共契约
- `codex/f-cut-001-matrices@9edd1a41d1e5` `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/DeviceScopeResolutionResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 冻结设备范围事实公共契约
- `codex/f-cut-001-matrices@9edd1a41d1e5` `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/DeviceScopeRevalidationQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 冻结设备范围事实公共契约
- `codex/f-cut-001-matrices@9edd1a41d1e5` `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/DeviceScopeRevalidationResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 冻结设备范围事实公共契约
- `codex/f-cut-001-matrices@9edd1a41d1e5` `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceScopeFactApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 冻结设备范围事实公共契约
- `codex/f-cut-001-matrices@4e558659d524` `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceScopeFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 实现设备范围事实Provider
- `codex/f-cut-001-matrices@4e558659d524` `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceScopeFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 实现设备范围事实Provider
- `codex/f-cut-001-matrices@4e558659d524` `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceScopeFactApiMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(asset): 实现设备范围事实Provider
- `codex/f-cut-001-matrices@6793c1d96efc` `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接通设备范围事实消费适配
- `codex/f-cut-001-matrices@6793c1d96efc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接通设备范围事实消费适配
- `codex/f-cut-001-matrices@6793c1d96efc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接通设备范围事实消费适配
- `codex/f-cut-001-matrices@6793c1d96efc` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(engineering): 接通设备范围事实消费适配
- `codex/f-cut-001-matrices@9a4763e85d10` `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(engineering): 收敛设备范围身份与锁定语义
- `codex/f-cut-001-matrices@5abbc82ba866` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/CommerceAuthorityBatchCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM公开事实合同
- `codex/f-cut-001-matrices@5abbc82ba866` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/CommerceContractFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM公开事实合同
- `codex/f-cut-001-matrices@5abbc82ba866` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/CommerceOrderLineFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM公开事实合同
- `codex/f-cut-001-matrices@5abbc82ba866` `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/CommerceSalesOrderFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM公开事实合同
- `codex/f-cut-001-matrices@5abbc82ba866` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/api/CommercePublicContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM公开事实合同
- `codex/f-cut-001-matrices@ae1968c63af6` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/authority/AuthorityCandidateDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM十表物理模型
- `codex/f-cut-001-matrices@ae1968c63af6` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/authority/SalesOrderContractRelationDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 建立COM十表物理模型
- `codex/f-cut-001-matrices@2141204dc257` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/PlatformMigrationEvidenceException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据公共合同
- `codex/f-cut-001-matrices@2141204dc257` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/dto/MarkStagedReadyCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据公共合同
- `codex/f-cut-001-matrices@2141204dc257` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/dto/MigrationBatchFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据公共合同
- `codex/f-cut-001-matrices@2141204dc257` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/api/migration/PlatformMigrationEvidenceContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据公共合同
- `codex/f-cut-001-matrices@2141204dc257` `scripts/generate_requirement_traceability.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据公共合同
- `codex/f-cut-001-matrices@bf85007f2ec9` `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/dto/MarkStagedReadyCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(platform): 收敛迁移证据合同阻断
- `codex/f-cut-001-matrices@bf85007f2ec9` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/api/migration/PlatformMigrationEvidenceContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(platform): 收敛迁移证据合同阻断
- `codex/f-cut-001-matrices@58aedbb24134` `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/migration/PlatformMigrationEvidenceMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(platform): 建立迁移证据四表Schema
- `codex/f-cut-001-matrices@dd0a26eed23a` `sql/migrations/V145__fcom001_order_contract_relation_source_identity.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/ContractAuthorityMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/OrderLineAuthorityMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/SalesOrderAuthorityMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/query/ContractAuthorityUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/query/OrderLineAuthorityUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/query/SalesOrderAuthorityUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/AuthorityPayloadCanonicalizer.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/resources/mapper/authority/AuthorityScopeImpactMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/resources/mapper/authority/ContractAuthorityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/resources/mapper/authority/OrderContractRelationAuthorityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/resources/mapper/authority/OrderLineAuthorityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/main/resources/mapper/authority/SalesOrderAuthorityMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@d8a275619ab2` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现ERP权威批次接收
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/AuthorityCandidateOwnerFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/query/AuthorityCandidateDecisionUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/main/resources/mapper/authority/AuthorityCandidateMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@7c8b11fec472` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现人工权威候选核对
- `codex/f-cut-001-matrices@f76525efcb72` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(commerce): 收敛候选载荷幂等边界
- `codex/f-cut-001-matrices@18237796431c` `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现项目交付范围命令
- `codex/f-cut-001-matrices@18237796431c` `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(commerce): 实现项目交付范围命令
- `codex/f-cut-001-matrices@9d029976fdee` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/dto/ProjectDeliveryScopeQualificationFact.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 新增交付范围资格公共契约
- `codex/f-cut-001-matrices@9d029976fdee` `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/dto/ProjectDeliveryScopeQualificationRevalidationQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 新增交付范围资格公共契约
- `codex/f-cut-001-matrices@9d029976fdee` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/ProjectDeliveryScopeQualificationFactApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(project): 新增交付范围资格公共契约
- `codex/f-cut-001-matrices@319a616e0a13` `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/ProjectDeliveryScopeQualificationFactApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(project): 收敛交付范围资格机器契约
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover-api/pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/CutoverTaskIntakeException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/dto/CutoverTaskIntakeCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/CutoverTaskIntakeApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskv2/CutoverTaskDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskv2/CutoverTaskDeviceScopeDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskDeviceScopeMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/command/CreateCutoverTaskCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskDeviceScopeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@93accdd2cda6` `pom.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立任务接入与人工分级内核
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverCreateWizard.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverWorkbenchSteps.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@9b3644d9e8ee` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 新增割接任务工作台页面
- `codex/f-cut-001-matrices@f7d2a39414a3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补全任务查询与创建上下文
- `codex/f-cut-001-matrices@f7d2a39414a3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/view/CutoverTaskViews.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补全任务查询与创建上下文
- `codex/f-cut-001-matrices@f7d2a39414a3` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补全任务查询与创建上下文
- `codex/f-cut-001-matrices@93b2ff0422fb` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): 验证P2人工分级正向交互
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/LegacyCutoverMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@9655336151af` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通旧任务迁移正向批次
- `codex/f-cut-001-matrices@3daef0f5a656` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐旧任务确定性迁移分类
- `codex/f-cut-001-matrices@3daef0f5a656` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐旧任务确定性迁移分类
- `codex/f-cut-001-matrices@3daef0f5a656` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐旧任务确定性迁移分类
- `codex/f-cut-001-matrices@146254d84201` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/configuration/CutoverConfigurationRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立冻结配置匹配内核
- `codex/f-cut-001-matrices@146254d84201` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistConfigurationQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立冻结配置匹配内核
- `codex/f-cut-001-matrices@146254d84201` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立冻结配置匹配内核
- `codex/f-cut-001-matrices@146254d84201` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverFrozenConfiguration.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立冻结配置匹配内核
- `codex/f-cut-001-matrices@146254d84201` `pms-module-cutover/src/main/resources/mapper/configuration/CutoverConfigurationRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立冻结配置匹配内核
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistItemMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistItemResultMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistItemMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistItemResultMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@799b01873210` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P3动态清单物理基础
- `codex/f-cut-001-matrices@7e4709684075` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): 固定P3清单物理合同
- `codex/f-cut-001-matrices@7e4709684075` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/FCut003MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): 固定P3清单物理合同
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistItemResultMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/configuration/CutoverConfigurationRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/port/CutoverCollectionPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/result/ChecklistCommandResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistItemResultMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/resources/mapper/configuration/CutoverConfigurationRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@76928bf8593c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 打通P3动态清单命令闭环
- `codex/f-cut-001-matrices@aa29efcb1df3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/checklist/CutoverChecklistItemMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 完成P3清单重匹配与查询
- `codex/f-cut-001-matrices@aa29efcb1df3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 完成P3清单重匹配与查询
- `codex/f-cut-001-matrices@aa29efcb1df3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/result/CutoverChecklistView.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 完成P3清单重匹配与查询
- `codex/f-cut-001-matrices@aa29efcb1df3` `pms-module-cutover/src/main/resources/mapper/checklist/CutoverChecklistItemMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 完成P3清单重匹配与查询
- `codex/f-cut-001-matrices@aa29efcb1df3` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 完成P3清单重匹配与查询
- `codex/f-cut-001-matrices@37723669b6dd` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结设备类型并驱动P3匹配
- `codex/f-cut-001-matrices@37723669b6dd` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/FCutDeviceTypeSnapshotMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结设备类型并驱动P3匹配
- `codex/f-cut-001-matrices@37723669b6dd` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结设备类型并驱动P3匹配
- `codex/f-cut-001-matrices@37723669b6dd` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结设备类型并驱动P3匹配
- `codex/f-cut-001-matrices@372f6895f614` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 固定评估产品类型历史投影
- `codex/f-cut-001-matrices@372f6895f614` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 固定评估产品类型历史投影
- `codex/f-cut-001-matrices@abc5b534edd6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverChecklistController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/checklist/CutoverChecklistReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverChecklistControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistField.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverChecklistComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@abc5b534edd6` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接入P3清单正向工作台候选
- `codex/f-cut-001-matrices@c8c75ce5f992` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 修复P3清单面板与答案往返
- `codex/f-cut-001-matrices@c8c75ce5f992` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverChecklistComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 修复P3清单面板与答案往返
- `codex/f-cut-001-matrices@c8c75ce5f992` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 修复P3清单面板与答案往返
- `codex/f-cut-001-matrices@c8c75ce5f992` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 修复P3清单面板与答案往返
- `codex/f-cut-001-matrices@c8c75ce5f992` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 修复P3清单面板与答案往返
- `codex/f-cut-001-matrices@148af0e859d4` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/result/CollectionRequestCommandResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverChecklistControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistField.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@148af0e859d4` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverChecklistComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 补齐P3自定义项与采集闭环
- `codex/f-cut-001-matrices@23dff6cdcd3c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口P3采集异步结果链
- `codex/f-cut-001-matrices@23dff6cdcd3c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口P3采集异步结果链
- `codex/f-cut-001-matrices@23dff6cdcd3c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口P3采集异步结果链
- `codex/f-cut-001-matrices@23dff6cdcd3c` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverChecklistPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口P3采集异步结果链
- `codex/f-cut-001-matrices@2c898d661abd` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverCreateWizard.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@2c898d661abd` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通任务创建六路由候选
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/CutoverTaskIntakeApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskErrorData.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskRequestCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@97ac132d20a6` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskRequestCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务创建候选门禁
- `codex/f-cut-001-matrices@a5734d00e857` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/CutoverTaskQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务动作与迁移归类
- `codex/f-cut-001-matrices@a5734d00e857` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收口任务动作与迁移归类
- `codex/f-cut-001-matrices@9b1a613ed54d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/FCut002PhysicalConstraintMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 补齐任务来源与评估约束
- `codex/f-cut-001-matrices@9f791d64aa1e` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 收口任务工作台正向交互
- `codex/f-cut-001-matrices@9f791d64aa1e` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 收口任务工作台正向交互
- `codex/f-cut-001-matrices@9f791d64aa1e` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 收口任务工作台正向交互
- `codex/f-cut-001-matrices@38fd6cfd2c9e` `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/dto/CutoverApprovalStartCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 预留割接审批事实合同
- `codex/f-cut-001-matrices@38fd6cfd2c9e` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/ControlledCutoverApprovalFactApi.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 预留割接审批事实合同
- `codex/f-cut-001-matrices@38fd6cfd2c9e` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 预留割接审批事实合同
- `codex/f-cut-001-matrices@e2fa3cdd8440` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案物理基础
- `codex/f-cut-001-matrices@e2fa3cdd8440` `pms-module-cutover/src/main/resources/mapper/planv2/CutoverPlanRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案物理基础
- `codex/f-cut-001-matrices@e2fa3cdd8440` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案物理基础
- `codex/f-cut-001-matrices@e2fa3cdd8440` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案物理基础
- `codex/f-cut-001-matrices@ddda602faadc` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收紧P4方案空值约束
- `codex/f-cut-001-matrices@3a32c4f7435a` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案内容消费模型
- `codex/f-cut-001-matrices@3a32c4f7435a` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanOwnerFactException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案内容消费模型
- `codex/f-cut-001-matrices@3a32c4f7435a` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanSourcePort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案内容消费模型
- `codex/f-cut-001-matrices@3a32c4f7435a` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4方案内容消费模型
- `codex/f-cut-001-matrices@b6ca8f7172e1` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanSourcePort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 分离P4草稿与提交完整性
- `codex/f-cut-001-matrices@b6ca8f7172e1` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 分离P4草稿与提交完整性
- `codex/f-cut-001-matrices@e81345864b7d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 显式布尔化P4阶段迁移检查
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/CreateCutoverPlanDraftCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/SaveCutoverPlanDraftCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/view/CutoverPlanView.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@f3e81acd231c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P4草稿正向闭环
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@b2aba46255a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合P4草稿应用审查缺口
- `codex/f-cut-001-matrices@c9de3fac88aa` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P4来源与legacy投影
- `codex/f-cut-001-matrices@bd4ead05eb83` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4初稿下载闭环
- `codex/f-cut-001-matrices@bd4ead05eb83` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanSubmissionTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4初稿下载闭环
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/main/resources/mapper/planv2/CutoverPlanRevisionMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@a9b87b478231` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanSubmissionTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案提交至P5
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanRevisionMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@fe50aaf9ab99` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanSubmissionTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5来源失效回退
- `codex/f-cut-001-matrices@d4a827c0149f` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现方案修订与联系人变更
- `codex/f-cut-001-matrices@d4a827c0149f` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现方案修订与联系人变更
- `codex/f-cut-001-matrices@d4a827c0149f` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanRevisionLifecycleTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现方案修订与联系人变更
- `codex/f-cut-001-matrices@c6c295cb56f8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 重建修订草稿来源投影
- `codex/f-cut-001-matrices@e38aaa8aad4d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案七路由候选
- `codex/f-cut-001-matrices@e38aaa8aad4d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案七路由候选
- `codex/f-cut-001-matrices@e38aaa8aad4d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestContext.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案七路由候选
- `codex/f-cut-001-matrices@e38aaa8aad4d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案七路由候选
- `codex/f-cut-001-matrices@e38aaa8aad4d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P4方案七路由候选
- `codex/f-cut-001-matrices@7c0cba236053` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P4方案REST机器合同
- `codex/f-cut-001-matrices@7c0cba236053` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P4方案REST机器合同
- `codex/f-cut-001-matrices@7c0cba236053` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P4方案REST机器合同
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/migration/query/LegacyCutoverPlanTargetQuery.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanReconciliationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/main/resources/mapper/planv2/LegacyCutoverPlanReconciliationMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004LegacyPlanMigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanReconciliationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@40dfd80c61da` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanReconciliationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现旧方案前向核对
- `codex/f-cut-001-matrices@5fee04d10cf3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): project plan allowed actions
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverPlanEditor.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverPlanPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverSupportArrangements.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverPlanComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@4734752e7e74` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P4 plan workbench
- `codex/f-cut-001-matrices@1b461a54a6aa` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): cover controlled plan loops
- `codex/f-cut-001-matrices@b78120e99f21` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批领域合同
- `codex/f-cut-001-matrices@b78120e99f21` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/domain/CutoverApprovalSourceSnapshotCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批领域合同
- `codex/f-cut-001-matrices@b78120e99f21` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/port/CutoverApprovalOwnerFactException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批领域合同
- `codex/f-cut-001-matrices@b78120e99f21` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/domain/CutoverApprovalSourceSnapshotCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批领域合同
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/approval/CutoverApprovalInstanceDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/approval/CutoverApprovalNotificationDO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalInstanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalNodeMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalNotificationMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/query/ApprovalNodeStatusUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/query/ApprovalNotificationDeliveryUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalInstanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalNodeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalNotificationMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@73a4f4aba31c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立P5审批物理基础
- `codex/f-cut-001-matrices@367438e602d0` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 对齐P5审批审计类型与排序
- `codex/f-cut-001-matrices@f3177ec606a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批启动与事实Provider
- `codex/f-cut-001-matrices@f3177ec606a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批启动与事实Provider
- `codex/f-cut-001-matrices@f3177ec606a8` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalSourceAssembler.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批启动与事实Provider
- `codex/f-cut-001-matrices@f3177ec606a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiImplTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批启动与事实Provider
- `codex/f-cut-001-matrices@f3177ec606a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批启动与事实Provider
- `codex/f-cut-001-matrices@df406b0c6ec2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P5审批启动运行契约
- `codex/f-cut-001-matrices@df406b0c6ec2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P5审批启动运行契约
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/command/ApproveCutoverApprovalCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/command/RejectCutoverApprovalCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/result/CutoverApprovalDecisionResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@8791b4cc169d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批决定闭环
- `codex/f-cut-001-matrices@3e022c68e8ef` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P5审批决定运行契约
- `codex/f-cut-001-matrices@3e022c68e8ef` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/result/CutoverApprovalDecisionResult.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P5审批决定运行契约
- `codex/f-cut-001-matrices@3e022c68e8ef` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛P5审批决定运行契约
- `codex/f-cut-001-matrices@6dae8751bb34` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 完成审批候选交集重验
- `codex/f-cut-001-matrices@6dae8751bb34` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 完成审批候选交集重验
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalInstanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/projection/ApprovalTodoPageRow.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/view/CutoverApprovalViews.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalInstanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalNodeMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@d689703262ac` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批查询投影
- `codex/f-cut-001-matrices@baaabcc4b22b` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalInstanceMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批管理员改派
- `codex/f-cut-001-matrices@baaabcc4b22b` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批管理员改派
- `codex/f-cut-001-matrices@baaabcc4b22b` `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalInstanceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批管理员改派
- `codex/f-cut-001-matrices@baaabcc4b22b` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批管理员改派
- `codex/f-cut-001-matrices@baaabcc4b22b` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalReassignmentTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批管理员改派
- `codex/f-cut-001-matrices@0142e01e8240` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批查询与改派一致性
- `codex/f-cut-001-matrices@0142e01e8240` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批查询与改派一致性
- `codex/f-cut-001-matrices@0142e01e8240` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批查询与改派一致性
- `codex/f-cut-001-matrices@0142e01e8240` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalReassignmentTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批查询与改派一致性
- `codex/f-cut-001-matrices@2414ac3828ad` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverApprovalNotificationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现审批通知提交后投递
- `codex/f-cut-001-matrices@2414ac3828ad` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverApprovalNotificationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现审批通知提交后投递
- `codex/f-cut-001-matrices@daee020ba85e` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现分级审批六路由REST
- `codex/f-cut-001-matrices@daee020ba85e` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/approval/CutoverApprovalResponses.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现分级审批六路由REST
- `codex/f-cut-001-matrices@daee020ba85e` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现分级审批六路由REST
- `codex/f-cut-001-matrices@daee020ba85e` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现分级审批六路由REST
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@0f98d5277aa2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛审批响应与错误合同
- `codex/f-cut-001-matrices@eefb40eccdcc` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 支持审批决定幂等重放
- `codex/f-cut-001-matrices@eefb40eccdcc` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 支持审批决定幂等重放
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalDecisionForm.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalReassignmentPanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverTaskInteraction.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@327d14778d91` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 实现P5审批工作台
- `codex/f-cut-001-matrices@ac8a6e5f47f7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 闭合审批工作台交互
- `codex/f-cut-001-matrices@9e812f9c8cbe` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): close controlled approval loop
- `codex/f-cut-001-matrices@9e812f9c8cbe` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalSourceAssembler.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): close controlled approval loop
- `codex/f-cut-001-matrices@9e812f9c8cbe` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): close controlled approval loop
- `codex/f-cut-001-matrices@a7e5b7b356cc` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): preserve uploaded approval content
- `codex/f-cut-001-matrices@124295f9bae4` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/domain/CutoverClosureRules.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure consumer ports
- `codex/f-cut-001-matrices@124295f9bae4` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/port/CutoverClosureCollectionPort.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure consumer ports
- `codex/f-cut-001-matrices@124295f9bae4` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/port/CutoverClosureOwnerFactException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure consumer ports
- `codex/f-cut-001-matrices@124295f9bae4` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureControlledPorts.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure consumer ports
- `codex/f-cut-001-matrices@124295f9bae4` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosurePortContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure consumer ports
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/CutoverClosureAttachmentMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/CutoverClosureMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/main/resources/mapper/closure/CutoverClosureAttachmentMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/main/resources/mapper/closure/CutoverClosureMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/CutoverClosureMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@cb5098f36239` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure schema
- `codex/f-cut-001-matrices@6c3dd42472dc` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): preserve P6 closure identity case
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/CutoverClosureMapper.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/query/CutoverClosureDraftUpdate.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/domain/CutoverClosureRules.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/view/CutoverClosureView.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/resources/mapper/closure/CutoverClosureAttachmentMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/main/resources/mapper/closure/CutoverClosureMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@6d6f0e46e22d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure draft flow
- `codex/f-cut-001-matrices@4d7e42350eab` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): align P6 draft lifecycle
- `codex/f-cut-001-matrices@4d7e42350eab` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): align P6 draft lifecycle
- `codex/f-cut-001-matrices@a99ddf5b726f` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): map project scope failures
- `codex/f-cut-001-matrices@55dc8e4996b3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 collection flow
- `codex/f-cut-001-matrices@55dc8e4996b3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 collection flow
- `codex/f-cut-001-matrices@55dc8e4996b3` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/LinkClosureManualResultCommand.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 collection flow
- `codex/f-cut-001-matrices@55dc8e4996b3` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 collection flow
- `codex/f-cut-001-matrices@55dc8e4996b3` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 collection flow
- `codex/f-cut-001-matrices@c6fe303e2ed9` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): stabilize collection result identity
- `codex/f-cut-001-matrices@3a790c417433` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): submit P6 closure
- `codex/f-cut-001-matrices@3a790c417433` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): submit P6 closure
- `codex/f-cut-001-matrices@df1b78304ec2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureController.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): expose P6 closure routes
- `codex/f-cut-001-matrices@df1b78304ec2` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): expose P6 closure routes
- `codex/f-cut-001-matrices@df1b78304ec2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): expose P6 closure routes
- `codex/f-cut-001-matrices@df1b78304ec2` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureRequestCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): expose P6 closure routes
- `codex/f-cut-001-matrices@97ee0904a20d` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): preserve closure error identity
- `codex/f-cut-001-matrices@c8a793f048b7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosureEvidencePanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure workbench
- `codex/f-cut-001-matrices@c8a793f048b7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosureForm.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure workbench
- `codex/f-cut-001-matrices@c8a793f048b7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosurePanel.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure workbench
- `codex/f-cut-001-matrices@c8a793f048b7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverClosureComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure workbench
- `codex/f-cut-001-matrices@c8a793f048b7` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add P6 closure workbench
- `codex/f-cut-001-matrices@d3161d9d105d` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): verify checklist positive loop
- `codex/f-cut-001-matrices@ec268ab9befc` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverChecklistComponents.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): verify checklist frontend loop
- `codex/f-cut-001-matrices@4d54f4e1c486` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverFullFlowPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): close controlled p1-p6 workflow
- `codex/f-cut-001-matrices@d584263f8d7a` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverControlledUiIntegration.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): verify controlled p1-p6 ui flow
- `codex/f-cut-001-matrices@b3c50150761d` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverControlledUiIntegration.spec.ts` — `CONFLICT_OURS_ADAPT_REQUIRED` — test(cutover): exercise unified p1-p6 workbench
- `codex/f-cut-001-matrices@7d6a2886c06a` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/port/CutoverDashboardOwnerFactException.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add dashboard action policy contract
- `codex/f-cut-001-matrices@a96b0b6fdcd9` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — refactor(cutover): share dashboard action policies
- `codex/f-cut-001-matrices@a96b0b6fdcd9` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/CutoverDashboardPolicyTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — refactor(cutover): share dashboard action policies
- `codex/f-cut-001-matrices@b9a3ab3259bd` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): share dashboard approval eligibility
- `codex/f-cut-001-matrices@22ce22c6e913` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/CutoverDashboardQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): aggregate authorized dashboard kpis
- `codex/f-cut-001-matrices@da1ea93e2cfa` `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): add authorized dashboard cards
- `codex/f-cut-001-matrices@3f87003b69bf` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 扩展P5提醒物理模型
- `codex/f-cut-001-matrices@3f87003b69bf` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 扩展P5提醒物理模型
- `codex/f-cut-001-matrices@3f87003b69bf` `sql/migrations/V157__fcut008_p5_lead_time_notification.sql` — `MIGRATION_ADAPT_REQUIRED` — old version or mixed/existing table owner
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/approval/CutoverApprovalResponses.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/view/CutoverApprovalViews.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalControllerContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@2011aa5a4baa` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 冻结P5提前时间判断
- `codex/f-cut-001-matrices@1edc713e5975` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 统一P4提交与审批冻结时间
- `codex/f-cut-001-matrices@aa2376d2d5ba` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 创建P5外部提醒请求
- `codex/f-cut-001-matrices@aa2376d2d5ba` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverExternalNotificationRequestFactory.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 创建P5外部提醒请求
- `codex/f-cut-001-matrices@aa2376d2d5ba` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 创建P5外部提醒请求
- `codex/f-cut-001-matrices@aa2376d2d5ba` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalExternalNotificationCreationTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 创建P5外部提醒请求
- `codex/f-cut-001-matrices@aa2376d2d5ba` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 创建P5外部提醒请求
- `codex/f-cut-001-matrices@8889fe968477` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 持久化P5提醒关联链
- `codex/f-cut-001-matrices@373a78839a09` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 增加P5外部提醒投递候选
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/configuration/vo/CutoverConfigurationSaveReqVO.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverNavigationRuleCodec.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut009MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@706b6c2a7c9c` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverNavigationRuleCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 接通P3提交导航决定
- `codex/f-cut-001-matrices@c7d3aa0fcf60` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistPositiveLoopMySqlTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 补齐导航规则门禁闭环
- `codex/f-cut-001-matrices@523520c9dc79` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistExportService.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 增加授权清单导出
- `codex/f-cut-001-matrices@1a37a546f4d9` `scripts/tests/test_validate_domain_entity_migration_alignment.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛备件协同Feature合同
- `codex/f-cut-001-matrices@1a37a546f4d9` `scripts/tests/test_validate_sds_phase2.py` — `CONFLICT_OURS_ADAPT_REQUIRED` — fix(cutover): 收敛备件协同Feature合同
- `codex/f-cut-001-matrices@aa803a52e342` `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/dto/SpareCallbackContractRules.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同公共合同
- `codex/f-cut-001-matrices@aa803a52e342` `pms-module-cutover-api/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApiContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同公共合同
- `codex/f-cut-001-matrices@aa803a52e342` `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/SpareApplicationGateway.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同公共合同
- `codex/f-cut-001-matrices@aa803a52e342` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/SpareNeedSnapshotCodecTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同公共合同
- `codex/f-cut-001-matrices@750ef1abf8a8` `pms-module-cutover/src/main/resources/mapper/spare/CutoverSpareManualEvidenceMapper.xml` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同持久化合同
- `codex/f-cut-001-matrices@750ef1abf8a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/CutoverSpareMapperContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同持久化合同
- `codex/f-cut-001-matrices@750ef1abf8a8` `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut010MigrationContractTest.java` — `CONFLICT_OURS_ADAPT_REQUIRED` — feat(cutover): 建立备件协同持久化合同

## Requirement追溯生成

- 退出码：`2`
```text
usage: generate_requirement_traceability.py [-h] --prd PRD --domains DOMAINS
                                            [--features FEATURES]
                                            [--tasks TASKS]
                                            [--feature-index FEATURE_INDEX]
                                            --output OUTPUT
                                            [--coverage-output COVERAGE_OUTPUT]
                                            [--check]
generate_requirement_traceability.py: error: the following arguments are required: --prd, --domains, --output
```

## 主干适配构建

- Maven退出码：`1`
```text
[INFO] Scanning for projects...
[ERROR] [ERROR] Some problems were encountered while processing the POMs:
[ERROR] 'modules.module[14]' specifies duplicate child module pms-module-integration/pms-module-integration-api @ cn.iocoder.boot:yudao:${revision}, /home/runner/work/NPDMS/NPDMS/pom.xml, line 28, column 17
 @ 
[ERROR] The build could not read 1 project -> [Help 1]
[ERROR]   
[ERROR]   The project cn.iocoder.boot:yudao:2026.06-jdk25-SNAPSHOT (/home/runner/work/NPDMS/NPDMS/pom.xml) has 1 error
[ERROR]     'modules.module[14]' specifies duplicate child module pms-module-integration/pms-module-integration-api @ cn.iocoder.boot:yudao:${revision}, /home/runner/work/NPDMS/NPDMS/pom.xml, line 28, column 17
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/ProjectBuildingException
```

## 适配Pass 1：主干基线恢复与构建

- 已恢复：根POM、正式PRD、ACC/COM领域规格及误带入的旧基线报告。
- Requirement追溯生成退出码：`1`
- Maven受影响Reactor构建退出码：`1`

### Requirement追溯生成日志
```text
domain specification root not found: docs/design/phase-1-domain-ownership.md
```

### Maven日志
```text
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[288,12] cannot find symbol
[ERROR]   symbol:   method setStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[294,12] cannot find symbol
[ERROR]   symbol:   method setCompanyCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[295,12] cannot find symbol
[ERROR]   symbol:   method setOrderNo(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[296,12] cannot find symbol
[ERROR]   symbol:   method setOrderType(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[297,12] cannot find symbol
[ERROR]   symbol:   method setCustomerCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[298,12] cannot find symbol
[ERROR]   symbol:   method setCustomerName(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[299,12] cannot find symbol
[ERROR]   symbol:   method setOrderAmount(java.math.BigDecimal)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[300,12] cannot find symbol
[ERROR]   symbol:   method setCurrencyCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[301,12] cannot find symbol
[ERROR]   symbol:   method setAuthorityStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[302,12] cannot find symbol
[ERROR]   symbol:   method setStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[308,12] cannot find symbol
[ERROR]   symbol:   method setSourceSystem(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[310,12] cannot find symbol
[ERROR]   symbol:   method setSourceVersion(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[311,12] cannot find symbol
[ERROR]   symbol:   method setOrderId(java.lang.Long)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[313,12] cannot find symbol
[ERROR]   symbol:   method setItemCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[314,12] cannot find symbol
[ERROR]   symbol:   method setItemDesc(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[315,12] cannot find symbol
[ERROR]   symbol:   method setProductCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[316,12] cannot find symbol
[ERROR]   symbol:   method setModelCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[318,12] cannot find symbol
[ERROR]   symbol:   method setOpenQty(java.math.BigDecimal)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[319,12] cannot find symbol
[ERROR]   symbol:   method setDeliveredQty(java.math.BigDecimal)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[320,12] cannot find symbol
[ERROR]   symbol:   method setUnitCode(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[321,12] cannot find symbol
[ERROR]   symbol:   method setUnitScale(java.lang.Integer)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[322,12] cannot find symbol
[ERROR]   symbol:   method setQuantityStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[323,12] cannot find symbol
[ERROR]   symbol:   method setSourceLifecycleStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[324,12] cannot find symbol
[ERROR]   symbol:   method setStatus(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[325,12] cannot find symbol
[ERROR]   symbol:   method setSourceUpdatedAt(java.time.LocalDateTime)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[332,12] cannot find symbol
[ERROR]   symbol:   method setContractId(java.lang.Long)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[334,12] cannot find symbol
[ERROR]   symbol:   method setRelationRole(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[335,12] cannot find symbol
[ERROR]   symbol:   method setRelationSource(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[336,12] cannot find symbol
[ERROR]   symbol:   method setSourceSystem(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[337,12] cannot find symbol
[ERROR]   symbol:   method setSalesOrderSourceKey(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[338,12] cannot find symbol
[ERROR]   symbol:   method setContractSourceKey(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[339,12] cannot find symbol
[ERROR]   symbol:   method setSourceVersion(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[340,12] cannot find symbol
[ERROR]   symbol:   method setSourceEvidence(java.lang.String)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[341,12] cannot find symbol
[ERROR]   symbol:   method setEffectiveFrom(java.time.LocalDateTime)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[342,12] cannot find symbol
[ERROR]   symbol:   method setEffectiveTo(java.time.LocalDateTime)
[ERROR]   location: variable row of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[362,58] invalid method reference
[ERROR]   cannot find symbol
[ERROR]     symbol:   method getAllocatedQty()
[ERROR]     location: class cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[368,57] invalid method reference
[ERROR]   cannot find symbol
[ERROR]     symbol:   method getId()
[ERROR]     location: class cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[373,50] cannot find symbol
[ERROR]   symbol:   method getDeliveryScopeId()
[ERROR]   location: variable detail of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[379,79] cannot find symbol
[ERROR]   symbol:   method getId()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[379,95] cannot find symbol
[ERROR]   symbol:   method getVersion()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[383,43] cannot find symbol
[ERROR]   symbol:   method getOrderLineId()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[384,41] cannot find symbol
[ERROR]   symbol:   method getProjectId()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[385,43] cannot find symbol
[ERROR]   symbol:   method getProjectCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[386,43] cannot find symbol
[ERROR]   symbol:   method getProjectName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[387,50] cannot find symbol
[ERROR]   symbol:   method getProjectCompanyCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[388,50] cannot find symbol
[ERROR]   symbol:   method getProjectCompanyName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[389,53] cannot find symbol
[ERROR]   symbol:   method getProjectDepartmentCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[390,53] cannot find symbol
[ERROR]   symbol:   method getProjectDepartmentName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[391,51] cannot find symbol
[ERROR]   symbol:   method getProjectCustomerCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[392,51] cannot find symbol
[ERROR]   symbol:   method getProjectCustomerName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[393,56] cannot find symbol
[ERROR]   symbol:   method getProjectManagerEmployeeNo()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[394,50] cannot find symbol
[ERROR]   symbol:   method getProjectManagerName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[395,49] cannot find symbol
[ERROR]   symbol:   method getOrderSourceSystem()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[396,48] cannot find symbol
[ERROR]   symbol:   method getOrderCompanyCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[397,48] cannot find symbol
[ERROR]   symbol:   method getOrderCompanyName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[398,41] cannot find symbol
[ERROR]   symbol:   method getOrderType()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[399,39] cannot find symbol
[ERROR]   symbol:   method getOrderNo()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[400,38] cannot find symbol
[ERROR]   symbol:   method getLineNo()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[401,40] cannot find symbol
[ERROR]   symbol:   method getItemCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[402,44] cannot find symbol
[ERROR]   symbol:   method getAllocatedQty()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[403,21] cannot find symbol
[ERROR]   symbol:   method setScopeStatus(java.lang.String)
[ERROR]   location: variable conflict of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[404,49] cannot find symbol
[ERROR]   symbol:   method getAllocationVersion()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[405,21] cannot find symbol
[ERROR]   symbol:   method setAllocationSource(java.lang.String)
[ERROR]   location: variable conflict of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[406,21] cannot find symbol
[ERROR]   symbol:   method setChangeReason(java.lang.String)
[ERROR]   location: variable conflict of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[407,50] cannot find symbol
[ERROR]   symbol:   method getOfficeDepartmentId()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[408,52] cannot find symbol
[ERROR]   symbol:   method getOfficeDepartmentCode()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[409,52] cannot find symbol
[ERROR]   symbol:   method getOfficeDepartmentName()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[410,55] cannot find symbol
[ERROR]   symbol:   method getOfficeDepartmentVersion()
[ERROR]   location: variable active of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[411,21] cannot find symbol
[ERROR]   symbol:   method setSourceEvidence(java.lang.String)
[ERROR]   location: variable conflict of type cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :pms-module-commerce
```
