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

## 适配Pass 2：隔离已被主干后继实现取代的旧COM组

- 仅恢复 `pms-module-commerce`、两份旧COM重排迁移及对应旧COM专项脚本。
- ACC、CUT、IMP、INT、CUS和Infra流式文件代码继续保留。
- Requirement追溯生成退出码：`0`
- Maven受影响Reactor构建退出码：`1`

### Requirement追溯日志
```text
WROTE docs/traceability/requirement-matrix.md
WROTE docs/traceability/requirement-version-coverage.json
```

### Maven日志
```text
[INFO] 
[INFO] ----------------< cn.iocoder.boot:yudao-module-system >-----------------
[INFO] Building yudao-module-system 2026.06-jdk25-SNAPSHOT              [27/33]
[INFO]   from yudao-module-system/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-system ---
[INFO] Copying 38 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-system ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-system:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-system ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 439 source files with javac [target 25] to target/classes
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/convert/auth/AuthConvert.java:[82,26] Unmapped target property: "socialType".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/convert/auth/AuthConvert.java:[84,23] Unmapped target property: "createIp".
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/redis/oauth2/OAuth2AccessTokenRedisDAO.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/redis/oauth2/OAuth2AccessTokenRedisDAO.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java: /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-system ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 13 source files with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-system ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-system/target/yudao-module-system-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] ------------------< cn.iocoder.boot:yudao-module-bpm >------------------
[INFO] 
[INFO] Building yudao-module-bpm 2026.06-jdk25-SNAPSHOT                 [28/33]
[INFO] ----------------< cn.iocoder.boot:pms-module-customer >-----------------
[INFO] Building pms-module-customer 2026.06-jdk25-SNAPSHOT              [29/33]
[INFO]   from yudao-module-bpm/pom.xml
[INFO]   from pms-module-customer/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] ----------------< cn.iocoder.boot:pms-module-platform >-----------------
[INFO] Building pms-module-platform 2026.06-jdk25-SNAPSHOT              [30/33]
[INFO]   from pms-module-platform/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-customer ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-customer ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-customer:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-customer ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 62 source files with javac [target 25] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-customer ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-customer ---
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-platform ---
[INFO] Copying 23 resources from src/main/resources to target/classes
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 23 source files with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-platform ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-platform:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-platform ---
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-customer ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-customer ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-customer/target/pms-module-customer-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-bpm ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/resources
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-bpm ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-bpm:jar:2026.06-jdk25-SNAPSHOT...
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 234 source files with javac [target 25] to target/classes
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 223 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java: /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-platform ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-platform ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 54 source files with javac [target 25] to target/test-classes
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/task/BpmProcessInstanceConvert.java:[124,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/definition/BpmProcessDefinitionConvert.java:[97,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-bpm ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 19 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-platform ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-platform ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-platform/target/pms-module-platform-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] -----------------< cn.iocoder.boot:pms-module-cutover >-----------------
[INFO] Building pms-module-cutover 2026.06-jdk25-SNAPSHOT               [31/33]
[INFO]   from pms-module-cutover/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-cutover ---
[INFO] Copying 26 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-cutover ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-cutover:jar:2026.06-jdk25-SNAPSHOT...
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-bpm ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-bpm ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/target/yudao-module-bpm-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 343 source files with javac [target 25] to target/classes
[INFO] 
[INFO] -----------------< cn.iocoder.boot:pms-module-project >-----------------
[INFO] Building pms-module-project 2026.06-jdk25-SNAPSHOT               [32/33]
[INFO]   from pms-module-project/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-project ---
[INFO] Copying 30 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-project ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-project:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-project ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 683 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-cutover ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 95 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Recompile with -Xlint:unchecked for details.
[INFO] -------------------------------------------------------------
[ERROR] COMPILATION ERROR : 
[INFO] -------------------------------------------------------------
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java:[110,27] method readSeedMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut004MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java:[100,27] method readSeedMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut005MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java:[85,27] method readJobMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut006MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java:[105,27] method readExternalJobMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut008MigrationContractTest
[INFO] 4 errors 
[INFO] -------------------------------------------------------------
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-project ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-project ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 139 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-project ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-project ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-project/target/pms-module-project-2026.06-jdk25-SNAPSHOT.jar
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  0.687 s]
[INFO] yudao-framework .................................... SUCCESS [  0.072 s]
[INFO] yudao-common ....................................... SUCCESS [ 45.028 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [ 13.927 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [ 15.240 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [01:14 min]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [ 18.410 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [ 42.894 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  5.459 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  0.974 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  6.260 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [ 13.169 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  1.317 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [ 15.807 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  7.020 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.758 s]
[INFO] yudao-module-infra ................................. SUCCESS [ 37.699 s]
[INFO] yudao-module-system ................................ SUCCESS [ 33.297 s]
[INFO] yudao-module-bpm ................................... SUCCESS [ 11.015 s]
[INFO] pms-module-customer-api ............................ SUCCESS [ 19.407 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  6.866 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.917 s]
[INFO] pms-module-asset-api ............................... SUCCESS [  0.896 s]
[INFO] pms-module-customer ................................ SUCCESS [  2.973 s]
[INFO] pms-module-engineering-api ......................... SUCCESS [ 11.510 s]
[INFO] pms-module-commerce-api ............................ SUCCESS [ 11.509 s]
[INFO] pms-module-integration-api ......................... SUCCESS [ 11.434 s]
[INFO] pms-module-platform ................................ SUCCESS [ 10.880 s]
[INFO] pms-module-project ................................. SUCCESS [ 13.889 s]
[INFO] pms-module-engineering ............................. SKIPPED
[INFO] pms-module-cutover-api ............................. SUCCESS [  0.858 s]
[INFO] pms-module-cutover ................................. FAILURE [  7.620 s]
[INFO] pms-module-integration ............................. SUCCESS [ 33.897 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  04:45 min (Wall Clock)
[INFO] Finished at: 2026-09-03T16:17:11Z
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.14.0:testCompile (default-testCompile) on project pms-module-cutover: Compilation failure: Compilation failure: 
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java:[110,27] method readSeedMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut004MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java:[100,27] method readSeedMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut005MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java:[85,27] method readJobMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut006MigrationContractTest
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java:[105,27] method readExternalJobMigration() is already defined in class cn.iocoder.yudao.module.pms.cutover.migration.Fcut008MigrationContractTest
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :pms-module-cutover
```

## 适配Pass 3：最终源码树与非冲突hunk复核

- 代码Head：`963c734880a20f65c734c4bc346e30c8a81ae4fc`
- 对三条来源分支的最终源码树重新三方比较；冲突文件内可独立应用的hunk继续接收。
- 仅保留逐文件冲突；不形成整提交或整分支拒绝。
- 四个CUT迁移合同测试的重复辅助方法已去重，保留当前主干迁移路径。
- Requirement追溯生成退出码：`0`
- Maven受影响Reactor构建退出码：`1`

### Maven日志
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

### 追溯日志
```text
WROTE docs/traceability/requirement-matrix.md
WROTE docs/traceability/requirement-version-coverage.json
```

## 适配Pass 4：Reactor模块去重与重建

- 仅去除根POM中重复的模块登记，不移除任何已接收源码。
- Maven受影响Reactor构建退出码：`1`
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

## 适配Pass 5：精确去除第二个Reactor模块登记

- 精确前置条件：同一integration-api模块行恰好出现两次。
- 处理：仅删除第二次出现；业务源码与依赖内容未回退。
- Maven受影响Reactor构建退出码：`0`
```text
[INFO] Copying 103 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-infra ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-infra:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-infra ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 213 source files with javac [target 25] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-integration ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-integration/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-integration ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 3 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProviderTest.java: /home/runner/work/NPDMS/NPDMS/pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProviderTest.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProviderTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-integration ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-integration ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-integration/target/pms-module-integration-2026.06-jdk25-SNAPSHOT.jar
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/convert/config/ConfigConvert.java:[26,14] Unmapped target properties: "createTime, updateTime, creator, updater, deleted, type, transMap".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/convert/codegen/CodegenConvert.java:[34,20] Unmapped target properties: "createTime, updateTime, creator, updater, deleted, id, dataSourceConfigId, scene, remark, moduleName, businessName, className, classComment, author, templateType, frontType, parentMenuId, masterTableId, subJoinColumnId, subJoinMany, treeParentColumnId, treeNameColumnId, transMap".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/convert/codegen/CodegenConvert.java:[47,21] Unmapped target properties: "createTime, updateTime, creator, updater, deleted, id, tableId, ordinalPosition, dictType, example, createOperation, updateOperation, listOperation, listOperationCondition, listOperationResult, htmlType, transMap".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/convert/file/FileConfigConvert.java:[20,18] Unmapped target property: "master".
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-infra ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-infra ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 4 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-infra ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-infra ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-infra/target/yudao-module-infra-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] ----------------< cn.iocoder.boot:yudao-module-system >-----------------
[INFO] Building yudao-module-system 2026.06-jdk25-SNAPSHOT              [27/33]
[INFO]   from yudao-module-system/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-system ---
[INFO] Copying 38 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-system ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-system:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-system ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 439 source files with javac [target 25] to target/classes
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/convert/auth/AuthConvert.java:[82,26] Unmapped target property: "socialType".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/convert/auth/AuthConvert.java:[84,23] Unmapped target property: "createIp".
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/redis/oauth2/OAuth2AccessTokenRedisDAO.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/redis/oauth2/OAuth2AccessTokenRedisDAO.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java: /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/oauth2/OAuth2OpenController.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-system ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 13 source files with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-system ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-system/target/yudao-module-system-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] 
[INFO] ------------------< cn.iocoder.boot:yudao-module-bpm >------------------
[INFO] ----------------< cn.iocoder.boot:pms-module-platform >-----------------
[INFO] 
[INFO] Building pms-module-platform 2026.06-jdk25-SNAPSHOT              [29/33]
[INFO] ----------------< cn.iocoder.boot:pms-module-customer >-----------------
[INFO] Building yudao-module-bpm 2026.06-jdk25-SNAPSHOT                 [28/33]
[INFO] Building pms-module-customer 2026.06-jdk25-SNAPSHOT              [30/33]
[INFO]   from pms-module-platform/pom.xml
[INFO]   from yudao-module-bpm/pom.xml
[INFO]   from pms-module-customer/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-customer ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-customer ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-customer:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-customer ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 62 source files with javac [target 25] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-customer ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-customer ---
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-platform ---
[INFO] Copying 23 resources from src/main/resources to target/classes
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 23 source files with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-platform ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-platform:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-platform ---
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-customer ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-customer ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-customer/target/pms-module-customer-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-bpm ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/resources
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-bpm ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-bpm:jar:2026.06-jdk25-SNAPSHOT...
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 234 source files with javac [target 25] to target/classes
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 223 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java: /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-platform ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-platform ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 54 source files with javac [target 25] to target/test-classes
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/task/BpmProcessInstanceConvert.java:[124,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/definition/BpmProcessDefinitionConvert.java:[97,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-bpm ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 19 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-bpm ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ yudao-module-bpm ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/target/yudao-module-bpm-2026.06-jdk25-SNAPSHOT.jar
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-platform ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-platform ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-platform/target/pms-module-platform-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] 
[INFO] -----------------< cn.iocoder.boot:pms-module-project >-----------------
[INFO] -----------------< cn.iocoder.boot:pms-module-cutover >-----------------
[INFO] Building pms-module-project 2026.06-jdk25-SNAPSHOT               [31/33]
[INFO] Building pms-module-cutover 2026.06-jdk25-SNAPSHOT               [32/33]
[INFO]   from pms-module-cutover/pom.xml
[INFO]   from pms-module-project/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-cutover ---
[INFO] Copying 26 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-cutover ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-cutover:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-project ---
[INFO] Copying 30 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-project ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-project:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-project ---
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 343 source files with javac [target 25] to target/classes
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 683 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-cutover ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 95 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-cutover ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-cutover ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-cutover/target/pms-module-cutover-2026.06-jdk25-SNAPSHOT.jar
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-project ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-project ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 139 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-project ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-project ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-project/target/pms-module-project-2026.06-jdk25-SNAPSHOT.jar
[INFO] 
[INFO] ---------------< cn.iocoder.boot:pms-module-engineering >---------------
[INFO] Building pms-module-engineering 2026.06-jdk25-SNAPSHOT           [33/33]
[INFO]   from pms-module-engineering/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-engineering ---
[INFO] Copying 17 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-engineering ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-engineering:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-engineering ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 433 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalDifferenceScopeCodec.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalDifferenceScopeCodec.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java: /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-engineering ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-engineering ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 72 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-engineering ---
[INFO] Tests are skipped.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ pms-module-engineering ---
[INFO] Building jar: /home/runner/work/NPDMS/NPDMS/pms-module-engineering/target/pms-module-engineering-2026.06-jdk25-SNAPSHOT.jar
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  2.362 s]
[INFO] yudao-framework .................................... SUCCESS [  0.086 s]
[INFO] yudao-common ....................................... SUCCESS [ 44.189 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [ 10.783 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [ 14.926 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [01:17 min]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [ 18.022 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [ 42.349 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  5.266 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  1.105 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  2.712 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [ 12.316 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  1.313 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [ 14.837 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  6.435 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.742 s]
[INFO] yudao-module-infra ................................. SUCCESS [ 37.162 s]
[INFO] yudao-module-system ................................ SUCCESS [ 31.023 s]
[INFO] yudao-module-bpm ................................... SUCCESS [ 10.703 s]
[INFO] pms-module-customer-api ............................ SUCCESS [ 18.988 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  3.994 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.965 s]
[INFO] pms-module-asset-api ............................... SUCCESS [  0.951 s]
[INFO] pms-module-customer ................................ SUCCESS [  2.857 s]
[INFO] pms-module-engineering-api ......................... SUCCESS [ 11.952 s]
[INFO] pms-module-commerce-api ............................ SUCCESS [ 11.951 s]
[INFO] pms-module-integration-api ......................... SUCCESS [ 11.862 s]
[INFO] pms-module-platform ................................ SUCCESS [ 11.008 s]
[INFO] pms-module-project ................................. SUCCESS [ 13.962 s]
[INFO] pms-module-engineering ............................. SUCCESS [  8.920 s]
[INFO] pms-module-cutover-api ............................. SUCCESS [  0.657 s]
[INFO] pms-module-cutover ................................. SUCCESS [  9.595 s]
[INFO] pms-module-integration ............................. SUCCESS [ 33.257 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  04:30 min (Wall Clock)
[INFO] Finished at: 2026-09-03T16:41:39Z
[INFO] ------------------------------------------------------------------------
```

## 适配Pass 7：迁移Owner收敛与专项验证

- V207仅保留三张独立INT边缘/审计表。
- V208删除：其PLT列、索引与消费确认表已由V203等价且更完整地提供。
| 检查 | 退出码 |
|---|---:|
| `commerce_unit` | `1` |
| `customer_contract` | `0` |
| `cutover_contract` | `0` |
| `engineering_contract` | `0` |
| `platform_unit` | `0` |
| `python_contract` | `1` |
| `migrations` | `1` |
| `commerce_mysql` | `99` |
| `engineering_mysql` | `99` |
| `frontend_install` | `0` |
| `frontend_type` | `2` |
| `frontend_test` | `1` |
| `frontend_build` | `1` |

### commerce_unit
```text
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
[ERROR] /home/runner/work/NPDMS/NPDMS/pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java:[412,21] cannot find symbol
[ERROR]   symbol:   method setEffectiveFrom(java.time.LocalDateTime)
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

### customer_contract
```text
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-spring-boot-starter-biz-tenant:jar:2026.06-jdk25-SNAPSHOT...
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-biz-data-permission/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] No tests to run.
[INFO] 
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-test ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-test ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-test/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-test ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-test ---
[INFO] No tests to run.
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-biz-tenant/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] No tests to run.
[INFO] 
[INFO] --------< cn.iocoder.boot:yudao-spring-boot-starter-websocket >---------
[INFO] Building yudao-spring-boot-starter-websocket 2026.06-jdk25-SNAPSHOT [20/23]
[INFO]   from yudao-framework/yudao-spring-boot-starter-websocket/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-spring-boot-starter-websocket ---
[INFO] Copying 1 resource from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-spring-boot-starter-websocket ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-spring-boot-starter-websocket:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-websocket ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-websocket ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-websocket/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-websocket ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-websocket ---
[INFO] No tests to run.
[INFO] 
[INFO] -----------------< cn.iocoder.boot:yudao-module-infra >-----------------
[INFO] Building yudao-module-infra 2026.06-jdk25-SNAPSHOT               [21/23]
[INFO]   from yudao-module-infra/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-infra ---
[INFO] Copying 103 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-infra ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-infra:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-infra ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-infra ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:yudao-module-system >-----------------
[INFO] Building yudao-module-system 2026.06-jdk25-SNAPSHOT              [22/23]
[INFO]   from yudao-module-system/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-system ---
[INFO] Copying 38 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-system ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-system:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-system ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:pms-module-customer >-----------------
[INFO] Building pms-module-customer 2026.06-jdk25-SNAPSHOT              [23/23]
[INFO]   from pms-module-customer/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-customer ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-customer ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-customer:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-customer ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 62 source files with javac [target 25] to target/classes
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit
WARNING: Please consider reporting this to the maintainers of class lombok.permit.Permit
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-customer ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-customer ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 23 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-customer ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] Artifact org.opentest4j:opentest4j:jar:1.3.0 is present in the local repository, but cached from a remote repository ID that is unavailable in current build context, verifying that is downloadable from [central (https://repo.maven.apache.org/maven2, default, releases)]
[INFO] Artifact org.apiguardian:apiguardian-api:jar:1.1.2 is present in the local repository, but cached from a remote repository ID that is unavailable in current build context, verifying that is downloadable from [central (https://repo.maven.apache.org/maven2, default, releases)]
[INFO] Artifact org.opentest4j:opentest4j:jar:1.3.0 is present in the local repository, but cached from a remote repository ID that is unavailable in current build context, verifying that is downloadable from [central (https://repo.maven.apache.org/maven2, default, releases)]
[INFO] Artifact org.apiguardian:apiguardian-api:jar:1.1.2 is present in the local repository, but cached from a remote repository ID that is unavailable in current build context, verifying that is downloadable from [central (https://repo.maven.apache.org/maven2, default, releases)]
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactApiContractTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.082 s -- in cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactApiContractTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  0.269 s]
[INFO] yudao-framework .................................... SUCCESS [  0.085 s]
[INFO] yudao-common ....................................... SUCCESS [  0.623 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [  0.295 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [  0.261 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [  0.340 s]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [  0.379 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [  0.434 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  0.219 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  0.184 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  0.142 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [  0.304 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  0.157 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [  0.208 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  0.178 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.116 s]
[INFO] yudao-module-infra ................................. SUCCESS [  0.303 s]
[INFO] yudao-module-system ................................ SUCCESS [  0.312 s]
[INFO] pms-module-customer-api ............................ SUCCESS [  0.707 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  0.191 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.190 s]
[INFO] pms-module-asset-api ............................... SUCCESS [  0.208 s]
[INFO] pms-module-customer ................................ SUCCESS [ 10.090 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.462 s (Wall Clock)
[INFO] Finished at: 2026-09-03T16:53:59Z
[INFO] ------------------------------------------------------------------------
```

### cutover_contract
```text
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-websocket ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-websocket ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-websocket/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-websocket ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-websocket ---
[INFO] No tests to run.
[INFO] 
[INFO] -----------------< cn.iocoder.boot:yudao-module-infra >-----------------
[INFO] Building yudao-module-infra 2026.06-jdk25-SNAPSHOT               [22/25]
[INFO]   from yudao-module-infra/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-infra ---
[INFO] Copying 103 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-infra ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-infra:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-infra ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-infra ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:yudao-module-system >-----------------
[INFO] Building yudao-module-system 2026.06-jdk25-SNAPSHOT              [23/25]
[INFO]   from yudao-module-system/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-system ---
[INFO] Copying 38 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-system ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-system:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-system ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:pms-module-platform >-----------------
[INFO] Building pms-module-platform 2026.06-jdk25-SNAPSHOT              [24/25]
[INFO]   from pms-module-platform/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-platform ---
[INFO] Copying 23 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-platform ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-platform:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-platform ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-platform ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-platform ---
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit
WARNING: Please consider reporting this to the maintainers of class lombok.permit.Permit
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 54 source files with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-cutover-api ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-cutover-api/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-cutover-api ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 1 source file with javac [target 25] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-cutover-api ---
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-platform ---
[INFO] 
[INFO] -----------------< cn.iocoder.boot:pms-module-cutover >-----------------
[INFO] Building pms-module-cutover 2026.06-jdk25-SNAPSHOT               [25/25]
[INFO]   from pms-module-cutover/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-cutover ---
[INFO] Copying 26 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-cutover ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-cutover:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 343 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/configuration/CutoverConfigurationServiceImpl.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-cutover ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-cutover ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 95 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProviderTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-cutover ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running cn.iocoder.yudao.module.pms.cutover.migration.Fcut006MigrationContractTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.152 s -- in cn.iocoder.yudao.module.pms.cutover.migration.Fcut006MigrationContractTest
[INFO] Running cn.iocoder.yudao.module.pms.cutover.migration.Fcut005MigrationContractTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in cn.iocoder.yudao.module.pms.cutover.migration.Fcut005MigrationContractTest
[INFO] Running cn.iocoder.yudao.module.pms.cutover.migration.Fcut008MigrationContractTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in cn.iocoder.yudao.module.pms.cutover.migration.Fcut008MigrationContractTest
[INFO] Running cn.iocoder.yudao.module.pms.cutover.migration.Fcut004MigrationContractTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in cn.iocoder.yudao.module.pms.cutover.migration.Fcut004MigrationContractTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  0.255 s]
[INFO] yudao-framework .................................... SUCCESS [  0.174 s]
[INFO] yudao-common ....................................... SUCCESS [  0.449 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [  0.389 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [  0.173 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [  0.462 s]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [  0.426 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [  0.421 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  0.169 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  0.213 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  0.141 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [  0.206 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  0.192 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [  0.182 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  0.185 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.114 s]
[INFO] yudao-module-infra ................................. SUCCESS [  0.341 s]
[INFO] yudao-module-system ................................ SUCCESS [  0.317 s]
[INFO] pms-module-customer-api ............................ SUCCESS [  0.625 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  0.120 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.232 s]
[INFO] pms-module-integration-api ......................... SUCCESS [  0.624 s]
[INFO] pms-module-platform ................................ SUCCESS [  5.165 s]
[INFO] pms-module-cutover-api ............................. SUCCESS [  2.979 s]
[INFO] pms-module-cutover ................................. SUCCESS [ 10.290 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  18.825 s (Wall Clock)
[INFO] Finished at: 2026-09-03T16:54:19Z
[INFO] ------------------------------------------------------------------------
```

### engineering_contract
```text
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-engineering-api ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-engineering-api/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-engineering-api ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-engineering-api ---
[INFO] No tests to run.
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] 
[INFO] 
[INFO] ------------------< cn.iocoder.boot:yudao-module-bpm >------------------
[INFO] ----------------< cn.iocoder.boot:pms-module-platform >-----------------
[INFO] Building yudao-module-bpm 2026.06-jdk25-SNAPSHOT                 [26/29]
[INFO] Building pms-module-platform 2026.06-jdk25-SNAPSHOT              [27/29]
[INFO]   from yudao-module-bpm/pom.xml
[INFO]   from pms-module-platform/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-platform ---
[INFO] Copying 23 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-platform ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-platform:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-platform ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-platform ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-platform ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 54 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-platform ---
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-bpm ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/resources
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-bpm ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-bpm:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 223 source files with javac [target 25] to target/classes
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/definition/BpmProcessDefinitionConvert.java:[97,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[WARNING] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/convert/task/BpmProcessInstanceConvert.java:[124,10] Unmapped target properties: "type, version, name, key, categoryName, formName, suspensionState, deploymentTime, bpmnXml".
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-bpm ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-bpm ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 19 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java uses or overrides a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/framework/flowable/core/candidate/expression/BpmTaskAssignLeaderExpressionTest.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-bpm ---
[INFO] 
[INFO] -----------------< cn.iocoder.boot:pms-module-project >-----------------
[INFO] Building pms-module-project 2026.06-jdk25-SNAPSHOT               [28/29]
[INFO]   from pms-module-project/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-project ---
[INFO] Copying 30 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-project ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-project:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-project ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 683 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectscope/ProjectTreeScopeService.java: Recompile with -Xlint:deprecation for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-project ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-project ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 139 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectclosure/ProjectClosureStateAdapterTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-project ---
[INFO] 
[INFO] ---------------< cn.iocoder.boot:pms-module-engineering >---------------
[INFO] Building pms-module-engineering 2026.06-jdk25-SNAPSHOT           [29/29]
[INFO]   from pms-module-engineering/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-engineering ---
[INFO] Copying 17 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-engineering ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-engineering:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-engineering ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 433 source files with javac [target 25] to target/classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalDifferenceScopeCodec.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalDifferenceScopeCodec.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java: /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java uses unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-engineering ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-engineering ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 72 source files with javac [target 25] to target/test-classes
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationServiceTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationServiceTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-engineering ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessApiContractTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.076 s -- in cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessApiContractTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  0.268 s]
[INFO] yudao-framework .................................... SUCCESS [  0.133 s]
[INFO] yudao-common ....................................... SUCCESS [  0.443 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [  0.184 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [  0.250 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [  0.475 s]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [  0.338 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [  0.466 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  0.147 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  0.237 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  0.137 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [  0.189 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  0.193 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [  0.216 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  0.240 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.196 s]
[INFO] yudao-module-infra ................................. SUCCESS [  0.362 s]
[INFO] yudao-module-system ................................ SUCCESS [  0.372 s]
[INFO] yudao-module-bpm ................................... SUCCESS [ 36.135 s]
[INFO] pms-module-customer-api ............................ SUCCESS [  0.672 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  0.166 s]
[INFO] pms-module-project-api ............................. SUCCESS [  0.189 s]
[INFO] pms-module-asset-api ............................... SUCCESS [  0.187 s]
[INFO] pms-module-engineering-api ......................... SUCCESS [  3.282 s]
[INFO] pms-module-commerce-api ............................ SUCCESS [  0.667 s]
[INFO] pms-module-integration-api ......................... SUCCESS [  0.535 s]
[INFO] pms-module-platform ................................ SUCCESS [  4.647 s]
[INFO] pms-module-project ................................. SUCCESS [ 12.213 s]
[INFO] pms-module-engineering ............................. SUCCESS [ 10.523 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:02 min (Wall Clock)
[INFO] Finished at: 2026-09-03T16:55:23Z
[INFO] ------------------------------------------------------------------------
```

### platform_unit
```text
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-test ---
[INFO] No tests to run.
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-spring-boot-starter-biz-tenant:jar:2026.06-jdk25-SNAPSHOT...
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-biz-data-permission/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-biz-data-permission ---
[INFO] No tests to run.
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-biz-tenant/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-biz-tenant ---
[INFO] No tests to run.
[INFO] 
[INFO] --------< cn.iocoder.boot:yudao-spring-boot-starter-websocket >---------
[INFO] Building yudao-spring-boot-starter-websocket 2026.06-jdk25-SNAPSHOT [18/21]
[INFO]   from yudao-framework/yudao-spring-boot-starter-websocket/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-spring-boot-starter-websocket ---
[INFO] Copying 1 resource from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-spring-boot-starter-websocket ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-spring-boot-starter-websocket:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-spring-boot-starter-websocket ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-spring-boot-starter-websocket ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-framework/yudao-spring-boot-starter-websocket/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-spring-boot-starter-websocket ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-spring-boot-starter-websocket ---
[INFO] No tests to run.
[INFO] 
[INFO] -----------------< cn.iocoder.boot:yudao-module-infra >-----------------
[INFO] Building yudao-module-infra 2026.06-jdk25-SNAPSHOT               [19/21]
[INFO]   from yudao-module-infra/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-infra ---
[INFO] Copying 103 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-infra ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-infra:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-infra ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/yudao-module-infra/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-infra ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-infra ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:yudao-module-system >-----------------
[INFO] Building yudao-module-system 2026.06-jdk25-SNAPSHOT              [20/21]
[INFO]   from yudao-module-system/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ yudao-module-system ---
[INFO] Copying 38 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ yudao-module-system ---
[INFO] Generating flattened POM of project cn.iocoder.boot:yudao-module-system:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ yudao-module-system ---
[INFO] Copying 4 resources from src/test/resources to target/test-classes
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ yudao-module-system ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ yudao-module-system ---
[INFO] 
[INFO] ----------------< cn.iocoder.boot:pms-module-platform >-----------------
[INFO] Building pms-module-platform 2026.06-jdk25-SNAPSHOT              [21/21]
[INFO]   from pms-module-platform/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ pms-module-platform ---
[INFO] Copying 23 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- flatten:1.7.2:flatten (flatten) @ pms-module-platform ---
[INFO] Generating flattened POM of project cn.iocoder.boot:pms-module-platform:jar:2026.06-jdk25-SNAPSHOT...
[INFO] 
[INFO] --- compiler:3.14.0:compile (default-compile) @ pms-module-platform ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ pms-module-platform ---
[INFO] skip non existing resourceDirectory /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.0:testCompile (default-testCompile) @ pms-module-platform ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 54 source files with javac [target 25] to target/test-classes
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit
WARNING: Please consider reporting this to the maintainers of class lombok.permit.Permit
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Some input files use or override a deprecated API.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormApplicationMySqlIntegrationTest.java: Recompile with -Xlint:deprecation for details.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Some input files use unchecked or unsafe operations.
[INFO] /home/runner/work/NPDMS/NPDMS/pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactApiImplTest.java: Recompile with -Xlint:unchecked for details.
[INFO] 
[INFO] --- surefire:3.5.3:test (default-test) @ pms-module-platform ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running cn.iocoder.yudao.module.pms.platform.service.outbox.PlatformOutboxDeliveryApiImplTest
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (/home/runner/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.454 s -- in cn.iocoder.yudao.module.pms.platform.service.outbox.PlatformOutboxDeliveryApiImplTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for yudao 2026.06-jdk25-SNAPSHOT:
[INFO] 
[INFO] yudao .............................................. SUCCESS [  0.251 s]
[INFO] yudao-framework .................................... SUCCESS [  0.105 s]
[INFO] yudao-common ....................................... SUCCESS [  0.572 s]
[INFO] yudao-spring-boot-starter-web ...................... SUCCESS [  0.380 s]
[INFO] yudao-spring-boot-starter-security ................. SUCCESS [  0.288 s]
[INFO] yudao-spring-boot-starter-mybatis .................. SUCCESS [  0.350 s]
[INFO] yudao-spring-boot-starter-redis .................... SUCCESS [  0.327 s]
[INFO] yudao-spring-boot-starter-mq ....................... SUCCESS [  0.456 s]
[INFO] yudao-spring-boot-starter-job ...................... SUCCESS [  0.247 s]
[INFO] yudao-spring-boot-starter-biz-tenant ............... SUCCESS [  0.214 s]
[INFO] yudao-spring-boot-starter-websocket ................ SUCCESS [  0.138 s]
[INFO] yudao-spring-boot-starter-monitor .................. SUCCESS [  0.326 s]
[INFO] yudao-spring-boot-starter-biz-ip ................... SUCCESS [  0.219 s]
[INFO] yudao-spring-boot-starter-excel .................... SUCCESS [  0.215 s]
[INFO] yudao-spring-boot-starter-test ..................... SUCCESS [  0.123 s]
[INFO] yudao-spring-boot-starter-biz-data-permission ...... SUCCESS [  0.182 s]
[INFO] yudao-module-infra ................................. SUCCESS [  0.305 s]
[INFO] yudao-module-system ................................ SUCCESS [  0.318 s]
[INFO] pms-module-platform-api ............................ SUCCESS [  0.197 s]
[INFO] pms-module-integration-api ......................... SUCCESS [  0.560 s]
[INFO] pms-module-platform ................................ SUCCESS [  7.568 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  10.933 s (Wall Clock)
[INFO] Finished at: 2026-09-03T16:55:35Z
[INFO] ------------------------------------------------------------------------
```

### python_contract
```text
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase1.py", line 638, in test_project_manager_may_query_closed_projects
    self.assertEqual([], errors)
AssertionError: Lists differ: [] != ["cross-context contracts must have unique[123 chars]4']"]

Second list contains 1 additional elements.
First extra element 0:
"cross-context contracts must have unique Producer rows and exact Requirement traceability; malformed=[] invalid=[] contractErrors=[] missingLinks=['CUT-02', 'CUT-04']"

- []
+ ['cross-context contracts must have unique Producer rows and exact Requirement '
+  'traceability; malformed=[] invalid=[] contractErrors=[] '
+  "missingLinks=['CUT-02', 'CUT-04']"]

======================================================================
FAIL: test_test_pass_statement_may_explicitly_deny_release_meaning (test_validate_sds_phase1.ValidateSdsPhase1Test.test_test_pass_statement_may_explicitly_deny_release_meaning)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase1.py", line 647, in test_test_pass_statement_may_explicitly_deny_release_meaning
    self.assertEqual([], errors)
AssertionError: Lists differ: [] != ["cross-context contracts must have unique[123 chars]4']"]

Second list contains 1 additional elements.
First extra element 0:
"cross-context contracts must have unique Producer rows and exact Requirement traceability; malformed=[] invalid=[] contractErrors=[] missingLinks=['CUT-02', 'CUT-04']"

- []
+ ['cross-context contracts must have unique Producer rows and exact Requirement '
+  'traceability; malformed=[] invalid=[] contractErrors=[] '
+  "missingLinks=['CUT-02', 'CUT-04']"]

======================================================================
FAIL: test_valid_fence_info_characters_do_not_expose_code_table (test_validate_sds_phase1.ValidateSdsPhase1Test.test_valid_fence_info_characters_do_not_expose_code_table) (opening='```~markdown')
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase1.py", line 514, in test_valid_fence_info_characters_do_not_expose_code_table
    self.assertEqual([], errors)
AssertionError: Lists differ: [] != ["cross-context contracts must have unique[123 chars]4']"]

Second list contains 1 additional elements.
First extra element 0:
"cross-context contracts must have unique Producer rows and exact Requirement traceability; malformed=[] invalid=[] contractErrors=[] missingLinks=['CUT-02', 'CUT-04']"

- []
+ ['cross-context contracts must have unique Producer rows and exact Requirement '
+  'traceability; malformed=[] invalid=[] contractErrors=[] '
+  "missingLinks=['CUT-02', 'CUT-04']"]

======================================================================
FAIL: test_valid_fence_info_characters_do_not_expose_code_table (test_validate_sds_phase1.ValidateSdsPhase1Test.test_valid_fence_info_characters_do_not_expose_code_table) (opening='~~~`markdown`')
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase1.py", line 514, in test_valid_fence_info_characters_do_not_expose_code_table
    self.assertEqual([], errors)
AssertionError: Lists differ: [] != ["cross-context contracts must have unique[123 chars]4']"]

Second list contains 1 additional elements.
First extra element 0:
"cross-context contracts must have unique Producer rows and exact Requirement traceability; malformed=[] invalid=[] contractErrors=[] missingLinks=['CUT-02', 'CUT-04']"

- []
+ ['cross-context contracts must have unique Producer rows and exact Requirement '
+  'traceability; malformed=[] invalid=[] contractErrors=[] '
+  "missingLinks=['CUT-02', 'CUT-04']"]

======================================================================
FAIL: test_current_facc001_report_contract_is_complete (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_facc001_report_contract_is_complete)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 839, in test_current_facc001_report_contract_is_complete
    self.assertEqual([], MODULE.validate_facc001_report_contract(repository_root))
AssertionError: Lists differ: [] != ['F-ACC-001 report contract missing: docs/[7999 chars]ord']

Second list contains 73 additional elements.
First extra element 0:
'F-ACC-001 report contract missing: docs/design/08-data-model.md: 活动根以PROJ ProjectTask/WorkBinding为唯一外部身份'

Diff is 8756 characters long. Set self.maxDiff to None to see it.

======================================================================
FAIL: test_current_fcom001_acceptance_stage_binding_contract_is_complete (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_fcom001_acceptance_stage_binding_contract_is_complete)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 829, in test_current_fcom001_acceptance_stage_binding_contract_is_complete
    self.assertEqual([], MODULE.validate_fcom001_acceptance_stage_binding(repository_root))
AssertionError: Lists differ: [] != ['F-COM-001 acceptance-stage contract miss[2715 chars]ock']

Second list contains 21 additional elements.
First extra element 0:
'F-COM-001 acceptance-stage contract missing: docs/design/08-data-model.md: 项目验收阶段快照对DeliveryScope及其分配版本的锁定事实'

Diff is 2992 characters long. Set self.maxDiff to None to see it.

======================================================================
FAIL: test_current_fcom001_contract_admin_scope_is_complete (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_fcom001_contract_admin_scope_is_complete)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 834, in test_current_fcom001_contract_admin_scope_is_complete
    self.assertEqual([], MODULE.validate_fcom001_contract_admin_scope(repository_root))
AssertionError: Lists differ: [] != ['F-COM-001 contract-admin scope missing: [2057 chars].md']

Second list contains 19 additional elements.
First extra element 0:
'F-COM-001 contract-admin scope missing: docs/design/02d-cross-context-contracts.md: OrganizationScopeApi.getActiveScopes(userId)'

Diff is 2304 characters long. Set self.maxDiff to None to see it.

======================================================================
FAIL: test_current_fcom001_v70_required_target_mappings_are_complete (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_fcom001_v70_required_target_mappings_are_complete)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 824, in test_current_fcom001_v70_required_target_mappings_are_complete
    self.assertEqual([], MODULE.validate_fcom001_v70_required_mappings(repository_root))
AssertionError: Lists differ: [] != ['F-COM-001 V70 required target mapping mi[890 chars]nce']

Second list contains 10 additional elements.
First extra element 0:
'F-COM-001 V70 required target mapping missing or changed: com_sales_order_line.status'

Diff is 1032 characters long. Set self.maxDiff to None to see it.

======================================================================
FAIL: test_current_v18_baseline_state_is_coherent_and_ready_for_phase3_design (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_v18_baseline_state_is_coherent_and_ready_for_phase3_design)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 803, in test_current_v18_baseline_state_is_coherent_and_ready_for_phase3_design
    self.assertEqual([], MODULE.validate(repository_root))
AssertionError: Lists differ: [] != ['Phase 2 migration gate evidence does not[22118 chars]态推断']

Second list contains 204 additional elements.
First extra element 0:
'Phase 2 migration gate evidence does not match current contract: docs/engineering/gates/phase-2/README.md expected=93对象/107来源绑定/1排除源'

Diff is 24072 characters long. Set self.maxDiff to None to see it.

======================================================================
FAIL: test_current_v18_migration_gate_evidence_matches_generated_contract (test_validate_sds_phase2.ValidateSdsPhase2Test.test_current_v18_migration_gate_evidence_matches_generated_contract)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase2.py", line 819, in test_current_v18_migration_gate_evidence_matches_generated_contract
    self.assertEqual([], MODULE.validate_v18_migration_gate_evidence(repository_root))
AssertionError: Lists differ: [] != ['Phase 2 migration gate evidence does not[371 chars]排除源']

Second list contains 3 additional elements.
First extra element 0:
'Phase 2 migration gate evidence does not match current contract: docs/engineering/gates/phase-2/README.md expected=93对象/107来源绑定/1排除源'

- []
+ ['Phase 2 migration gate evidence does not match current contract: '
+  'docs/engineering/gates/phase-2/README.md expected=93对象/107来源绑定/1排除源',
+  'Phase 2 migration gate evidence does not match current contract: '
+  'docs/engineering/gates/phase-2/gate-status.md expected=93对象/107来源绑定/1排除源',
+  'Phase 2 migration gate evidence does not match current contract: '
+  'docs/engineering/gates/phase-2/self-review.md expected=93对象/107来源绑定/1排除源']

======================================================================
FAIL: test_current_v18_revision_007_state_is_coherent_and_ready (test_validate_sds_phase3.Phase3ValidatorTest.test_current_v18_revision_007_state_is_coherent_and_ready)
----------------------------------------------------------------------
Traceback (most recent call last):
  File "/home/runner/work/NPDMS/NPDMS/scripts/tests/test_validate_sds_phase3.py", line 446, in test_current_v18_revision_007_state_is_coherent_and_ready
    self.assertEqual([], VALIDATOR.validate(repository_root))
AssertionError: Lists differ: [] != ['COM-01 Phase 3 PRD acceptance must exact[429 chars]ock']

Second list contains 6 additional elements.
First extra element 0:
'COM-01 Phase 3 PRD acceptance must exactly match the authoritative PRD block'

- []
+ ['COM-01 Phase 3 PRD acceptance must exactly match the authoritative PRD block',
+  'COM-01 declares unknown target table for its objects: com_order_line',
+  'PM-03 Phase 3 PRD acceptance must exactly match the authoritative PRD block',
+  'SCH-05 Phase 3 PRD acceptance must exactly match the authoritative PRD block',
+  'EXE-06 declares unknown target table for its objects: '
+  'proj_project_stage_snapshot',
+  'INS-09 Phase 3 PRD acceptance must exactly match the authoritative PRD block']

----------------------------------------------------------------------
Ran 716 tests in 33.732s

FAILED (failures=26, errors=31)
```

### migrations
```text
APPLY sql/migrations/V43__pms_dict_types.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V44__pms_supplement_dict_types.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V45__pms_supplement_dict_types2.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V46__pms_project_risk_phase_issue_dict.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V47__pms_project_template.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V48__pms_team_member_user_id_nullable.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V49__pms_project_missing_permissions.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V50__retire_excluded_cutover_runtime_surfaces.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V51__retire_semantic_rework_maintenance_runtime_surfaces.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V52__proj_project_template_foundation.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V53__retire_legacy_template_runtime_surfaces.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V54__fpm03_template_demo_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V55__fpm03_template_demo_seed_fullchain.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V56__fpm03_template_draft_backfill.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V57__proj_project_manual_creation.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V58__freeze_legacy_project_runtime_surfaces.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V59__fpm01_manual_match_demo_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V60__fpm02_project_tree_progress.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V61__fpm02_project_tree_demo_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V62__fpm02_project_tree_fullchain_template.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V63__fproj001_v18_atomic_project_creation.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V64__system_company_department_scope.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V65__asset_location_core.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V66__project_site_location_resolution.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V67__engineering_asset_location_fact.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V68__organization_location_menu_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V69__organization_location_single_tenant_defaults.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V70__commerce_delivery_scope_slice.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V71__fproj002_split_tree_progress_carriers.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V72__fproj002_v18_seed_and_menu.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V73__fproj002_v18_visibility_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V74__fproj002_v18_organization_scope_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V75__fproj002_v18_parent_template_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V76__fproj002_project_code_sequence_repair.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V77__fproj003_authorization_grant.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V78__fproj003_authorization_seed_and_menu.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V79__fproj003_authorization_demo_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V80__fproj004_template_match_history.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V81__fproj004_template_match_seed_and_permission.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V82__fproj004_project_category_deduplicate.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V83__fproj005_service_manager_assignment.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V84__fproj005_service_manager_notification_template.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V85__fproj005_tenant_consistent_acceptance_project.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V86__fproj006_project_governance_foundation.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V87__fproj006_project_governance_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V88__fproj007_project_task_runtime.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V89__fproj007_project_task_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V90__fsol001_construction_plan_duration.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V91__fsol001_duration_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V92__fplt001_file_artifact.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V93__fplt001_file_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V94__quartz_2_5_2_mysql_schema.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V95__fsol001_file_artifact_freeze.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V96__fsol002_preparation_readiness.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V97__fsol002_preparation_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V98__fplt001_optional_security_scan.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V99__fsol003_requirement_analysis.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V100__fsol003_requirement_analysis_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V101__fsol003_retire_legacy_requirement_role_grants.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V102__fplt002_dynamic_form.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V103__fplt002_dynamic_form_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V104__fsol003_dynamic_form_composition.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V105__fsol003_dynamic_form_composition_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V106__fcus001_customer_master.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V107__fcus001_customer_classification_scope.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V108__fcus001_customer_menu_and_permissions.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V109__fast001_device_master_and_source_facts.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V110__fast001_device_shipments_and_software_versions.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V111__fast001_device_temporal_assignments.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V112__fast001_device_relationship_location_warranty.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V113__fast001_device_download_grant.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V114__fast001_legacy_equipment_forward_migration.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V115__fast001_device_ancestor_projection_operations.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V116__fast001_device_ancestor_projection_event_watermark.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V117__fast001_device_ancestor_projection_job.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V118__fast001_device_menu_permissions_and_legacy_access.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V119__fast001_device_acceptance_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V120__fast001_browser_acceptance_users.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V121__fast001_browser_acceptance_login_names.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V122__fast001_customer_summary_acceptance_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V123__fcus001_acceptance_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V128__fcut001_cutover_configuration.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V129__fcut001_cutover_configuration_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V130__fcut001_cutover_configuration_menu_parent.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V131__fcut001_cutover_configuration_auto_increment.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V132__fcut001_matrix_contract.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
APPLY sql/migrations/V160__fcom001_contract_order_scope_forward_migration.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
migration_state
FCOM001_STATE_FRESH_V123
APPLY sql/migrations/V161__fcom001_permissions_menu_and_acceptance_seed.sql
mysql: [Warning] Using a password on the command line interface can be insecure.
ERROR 1364 (HY000) at line 172: Field 'source_system' doesn't have a default value
FAILED sql/migrations/V161__fcom001_permissions_menu_and_acceptance_seed.sql
```

### commerce_mysql
```text
not run: migration chain failed
```

### engineering_mysql
```text
not run: migration chain failed
```

### frontend_install
```text
Lockfile is up to date, resolution step is skipped
Progress: resolved 1, reused 0, downloaded 0, added 0
Packages: +1053
++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
Progress: resolved 1053, reused 0, downloaded 42, added 38
Progress: resolved 1053, reused 0, downloaded 128, added 123
Progress: resolved 1053, reused 0, downloaded 240, added 239
Progress: resolved 1053, reused 0, downloaded 354, added 347
Progress: resolved 1053, reused 0, downloaded 622, added 625
Progress: resolved 1053, reused 0, downloaded 705, added 709
Progress: resolved 1053, reused 0, downloaded 927, added 930
Progress: resolved 1053, reused 0, downloaded 1048, added 1052
Progress: resolved 1053, reused 0, downloaded 1049, added 1053, done
.../es5-ext@0.10.64/node_modules/es5-ext postinstall$  node -e "try{require('./_postinstall')}catch(e){}" || exit 0
.../node_modules/@parcel/watcher install$ node scripts/build-from-source.js
.../node_modules/core-js-pure postinstall$ node -e "try{require('./postinstall')}catch(e){}"
.../es5-ext@0.10.64/node_modules/es5-ext postinstall: Done
.../esbuild@0.27.3/node_modules/esbuild postinstall$ node install.js
.../node_modules/@parcel/watcher install: Done
.../node_modules/core-js-pure postinstall: Done
.../esbuild@0.27.3/node_modules/esbuild postinstall: Done

dependencies:
+ @element-plus/icons-vue 2.3.2
+ @form-create/designer 3.4.0
+ @form-create/element-ui 3.2.38
+ @iconify/utils 3.1.0
+ @iconify/vue 5.0.1
+ @microsoft/fetch-event-source 2.0.1
+ @videojs-player/vue 1.0.0
+ @vueuse/core 14.3.0
+ @wangeditor-next/editor 5.7.0
+ @wangeditor-next/editor-for-vue 5.1.14
+ @wangeditor-next/plugin-mention 2.0.0
+ @zxcvbn-ts/core 3.0.4
+ animate.css 4.1.1
+ axios 1.16.0
+ benz-amr-recorder 1.1.5
+ bpmn-js-token-simulation 0.39.3
+ camunda-bpmn-moddle 7.0.1
+ cropperjs 2.1.1
+ crypto-js 4.2.0
+ dayjs 1.11.20
+ dhtmlx-gantt 9.1.4
+ diagram-js 15.14.0
+ driver.js 1.4.0
+ echarts 6.0.0
+ echarts-wordcloud 2.1.0
+ element-plus 2.13.7
+ fast-xml-parser 4.5.6
+ highlight.js 11.11.1
+ jsbarcode 3.12.3
+ jsencrypt 3.5.4
+ jsoneditor 10.4.3
+ livekit-client 2.19.1
+ lodash-es 4.18.1
+ markdown-it 14.1.1
+ markmap-common 0.18.9
+ markmap-lib 0.18.12
+ markmap-toolbar 0.18.12
+ markmap-view 0.18.12
+ min-dash 5.0.0
+ mitt 3.0.1
+ nprogress 0.2.0
+ pinia 3.0.4
+ pinia-plugin-persistedstate 4.7.1
+ qrcode 1.5.4
+ qs 6.15.1
+ snabbdom 3.6.3
+ sortablejs 1.15.7
+ steady-xml 0.1.0
+ tyme4ts 1.4.6
+ url 0.11.4
+ video.js 8.23.8
+ vue 3.5.34
+ vue-dompurify-html 5.3.0
+ vue-i18n 11.4.0
+ vue-router 5.0.6
+ vue-types 6.0.0
+ vue3-print-nb 0.1.4
+ vue3-signature 0.4.4
+ vuedraggable 4.1.0
+ web-storage-cache 1.1.1
+ xml-js 1.6.11

devDependencies:
+ @commitlint/cli 20.5.3
+ @commitlint/config-conventional 20.5.3
+ @iconify/json 2.2.470
+ @types/jsoneditor 9.9.6
+ @types/lodash-es 4.17.12
+ @types/node 25.6.0
+ @types/nprogress 0.2.3
+ @types/qrcode 1.5.6
+ @types/qs 6.15.0
+ @unocss/eslint-config 66.6.8
+ @unocss/eslint-plugin 66.6.8
+ @unocss/transformer-variant-group 66.6.8
+ @vitejs/plugin-vue 6.0.6
+ @vitejs/plugin-vue-jsx 5.1.5
+ autoprefixer 10.5.0
+ bpmn-js 18.16.1
+ bpmn-js-properties-panel 5.54.0
+ consola 3.4.2
+ eslint 10.3.0
+ eslint-plugin-vue 10.9.1
+ lint-staged 16.4.0
+ postcss 8.5.14
+ postcss-html 1.8.1
+ postcss-scss 4.0.9
+ prettier 3.8.3
+ prettier-eslint 16.4.2
+ rimraf 6.1.3
+ rollup 4.60.3
+ sass 1.99.0
+ stylelint 17.11.0
+ stylelint-config-html 1.1.0
+ stylelint-config-recommended 18.0.0
+ stylelint-config-standard 40.0.0
+ stylelint-order 8.1.1
+ typescript 6.0.3
+ typescript-eslint 8.59.2
+ unocss 66.6.8
+ unplugin-auto-import 21.0.0
+ unplugin-element-plus 0.11.2
+ unplugin-vue-components 32.0.0
+ vite 8.1.4
+ vite-plugin-compression 0.5.1
+ vite-plugin-svg-icons-ng 1.9.0
+ vitest 4.1.11
+ vue-eslint-parser 10.4.0
+ vue-tsc 3.2.8

Done in 9.4s
```

### frontend_type
```text
src/views/wms/order/check/CheckOrderForm.vue(252,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(253,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(254,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(255,26): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(256,32): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(262,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(271,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/check/CheckOrderForm.vue(276,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(277,22): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(278,26): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderForm.vue(279,27): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/check/CheckOrderForm.vue(283,28): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/check/CheckOrderForm.vue(312,23): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(313,79): error TS2345: Argument of type 'unknown' is not assignable to parameter of type 'CheckOrderFormDetail'.
  Type 'unknown' is not assignable to type 'CheckOrderDetailVO'.
src/views/wms/order/check/CheckOrderForm.vue(315,20): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(316,67): error TS2345: Argument of type 'unknown' is not assignable to parameter of type 'CheckOrderFormDetail'.
  Type 'unknown' is not assignable to type 'CheckOrderDetailVO'.
src/views/wms/order/check/CheckOrderForm.vue(318,21): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(319,69): error TS2345: Argument of type 'unknown' is not assignable to parameter of type 'CheckOrderFormDetail'.
  Type 'unknown' is not assignable to type 'CheckOrderDetailVO'.
src/views/wms/order/check/CheckOrderForm.vue(321,25): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(322,24): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(327,29): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderForm.vue(343,11): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/CheckOrderForm.vue(372,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/CheckOrderForm.vue(666,3): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/CheckOrderPrint.vue(100,59): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/check/CheckOrderPrint.vue(104,59): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/check/CheckOrderPrint.vue(107,56): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/check/CheckOrderPrint.vue(155,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderPrint.vue(156,24): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderPrint.vue(158,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderPrint.vue(159,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/CheckOrderPrint.vue(197,19): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderPrint.vue(208,33): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderPrint.vue(209,44): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/check/CheckOrderPrint.vue(211,30): error TS2304: Cannot find name 'computed'.
src/views/wms/order/check/CheckOrderPrint.vue(212,41): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/check/CheckOrderPrint.vue(217,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/CheckOrderPrint.vue(218,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/CheckOrderPrint.vue(234,11): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/check/index.vue(419,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/check/index.vue(420,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/check/index.vue(432,29): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(441,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(442,14): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(443,15): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(466,21): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/check/index.vue(467,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(468,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/check/index.vue(569,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(575,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(581,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/check/index.vue(615,1): error TS2304: Cannot find name 'onMounted'.
src/views/wms/order/movement/MovementOrderDetail.vue(96,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderDetail.vue(97,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderDetail.vue(98,20): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderDetail.vue(104,20): error TS2304: Cannot find name 'computed'.
src/views/wms/order/movement/MovementOrderForm.vue(206,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/movement/MovementOrderForm.vue(207,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/movement/MovementOrderForm.vue(209,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(210,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(211,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(212,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(213,26): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(214,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(224,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/movement/MovementOrderForm.vue(230,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(231,28): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderForm.vue(233,24): error TS2304: Cannot find name 'computed'.
src/views/wms/order/movement/MovementOrderForm.vue(238,29): error TS2304: Cannot find name 'computed'.
src/views/wms/order/movement/MovementOrderForm.vue(244,22): error TS2304: Cannot find name 'computed'.
src/views/wms/order/movement/MovementOrderForm.vue(501,3): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/movement/MovementOrderPrint.vue(71,59): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/movement/MovementOrderPrint.vue(75,56): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/movement/MovementOrderPrint.vue(110,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderPrint.vue(111,24): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderPrint.vue(113,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderPrint.vue(114,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/MovementOrderPrint.vue(126,19): error TS2304: Cannot find name 'computed'.
src/views/wms/order/movement/MovementOrderPrint.vue(135,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/movement/MovementOrderPrint.vue(136,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/movement/MovementOrderPrint.vue(152,11): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/movement/index.vue(384,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/movement/index.vue(385,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/movement/index.vue(405,29): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(415,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(416,14): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(417,15): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(439,21): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/movement/index.vue(440,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(441,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/movement/index.vue(519,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(525,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(531,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/movement/index.vue(565,1): error TS2304: Cannot find name 'onMounted'.
src/views/wms/order/receipt/ReceiptOrderDetail.vue(118,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderDetail.vue(119,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderDetail.vue(120,20): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderDetail.vue(121,20): error TS2304: Cannot find name 'computed'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(215,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(216,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(218,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(219,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(220,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(221,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(222,26): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(223,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(235,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(241,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(242,22): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(244,24): error TS2304: Cannot find name 'computed'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(249,29): error TS2304: Cannot find name 'computed'.
src/views/wms/order/receipt/ReceiptOrderForm.vue(488,3): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(75,59): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(79,56): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(114,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(115,24): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(117,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(118,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(130,19): error TS2304: Cannot find name 'computed'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(139,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(140,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/receipt/ReceiptOrderPrint.vue(156,11): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/receipt/index.vue(431,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/receipt/index.vue(432,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/receipt/index.vue(454,29): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(465,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(466,14): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(467,15): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(491,21): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/receipt/index.vue(492,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(493,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/receipt/index.vue(576,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(582,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(588,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/receipt/index.vue(622,1): error TS2304: Cannot find name 'onMounted'.
src/views/wms/order/shipment/ShipmentOrderDetail.vue(118,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderDetail.vue(119,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderDetail.vue(120,20): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderDetail.vue(121,20): error TS2304: Cannot find name 'computed'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(226,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(227,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(229,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(230,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(231,21): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(232,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(233,26): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(234,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(246,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(252,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(253,28): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(255,24): error TS2304: Cannot find name 'computed'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(260,29): error TS2304: Cannot find name 'computed'.
src/views/wms/order/shipment/ShipmentOrderForm.vue(518,3): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(75,59): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(79,56): error TS18046: 'detail' is of type 'unknown'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(114,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(115,24): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(117,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(118,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(130,19): error TS2304: Cannot find name 'computed'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(139,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(140,9): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/shipment/ShipmentOrderPrint.vue(156,11): error TS2304: Cannot find name 'nextTick'.
src/views/wms/order/shipment/index.vue(431,17): error TS2304: Cannot find name 'useMessage'.
src/views/wms/order/shipment/index.vue(432,15): error TS2304: Cannot find name 'useI18n'.
src/views/wms/order/shipment/index.vue(454,29): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(465,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(466,14): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(467,15): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(491,21): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/shipment/index.vue(492,23): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(493,19): error TS2304: Cannot find name 'reactive'.
src/views/wms/order/shipment/index.vue(576,17): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(582,19): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(588,18): error TS2304: Cannot find name 'ref'.
src/views/wms/order/shipment/index.vue(622,1): error TS2304: Cannot find name 'onMounted'.
 ELIFECYCLE  Command failed with exit code 2.
```

### frontend_test
```text

[1m[30m[46m RUN [49m[39m[22m [36mv4.1.11 [39m[90m/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3[39m

 [31m❯[39m src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts [2m([22m[2m0 test[22m[2m)[22m

[31m⎯⎯⎯⎯⎯⎯[39m[1m[41m Failed Suites 1 [49m[22m[31m⎯⎯⎯⎯⎯⎯⎯[39m

[41m[1m FAIL [22m[49m src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts[2m [ src/views/pms/cutover/cutover-task/cutoverTaskInteraction.spec.ts ][22m
[31m[1mTypeError[22m: Unknown file extension ".css" for /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/element-plus@2.13.7_typescript@6.0.3_vue@3.5.34_typescript@6.0.3_/node_modules/element-plus/theme-chalk/base.css[39m
[31m[2m⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[1/1]⎯[22m[39m


[2m Test Files [22m [1m[31m1 failed[39m[22m[90m (1)[39m
[2m      Tests [22m [2mno tests[22m
[2m   Start at [22m 16:57:57
[2m   Duration [22m 1.06s[2m (transform 325ms, setup 0ms, import 0ms, tests 0ms, environment 0ms)[22m

```

### frontend_build
```text

> yudao-ui-admin-vue3@2026.06-snapshot build:test /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3
> pnpm icons:check && node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build --mode test


> yudao-ui-admin-vue3@2026.06-snapshot icons:check /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3
> node scripts/generate-iconify-collections.cjs --check

Offline Iconify collection is current (36 collections).
[36mvite v8.1.4 [32mbuilding client environment for test...[36m[39m
[2Ktransforming...[33m[1m(!) %VITE_APP_TITLE% is not defined in env variables found in /index.html. Is the variable mistyped?[22m[39m
[33m[1m(!) %VITE_APP_TITLE% is not defined in env variables found in /index.html. Is the variable mistyped?[22m[39m
✓ 8518 modules transformed.
[31m✗[39m Build failed in 22.27s
[31merror during build:
[31mBuild failed with 1 error:

[plugin vite:vue] /home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue:244:6
SyntaxError: [vue/compiler-sfc] Identifier 'handleApprovalWorkspaceChanged' has already been declared. (244:6)

/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue
495|    if (reassignmentQueueVisible.value) await loadReassignmentQueue()
496|  }
497|  const handleApprovalWorkspaceChanged = async () => {
   |        ^
498|    await loadPage()
499|    if (todoVisible.value) await loadApprovalTodos()
    at constructor (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:365:19)
    at TypeScriptParserMixin.raise (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:6616:19)
    at TypeScriptScopeHandler.checkRedeclarationInScope (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:1619:19)
    at TypeScriptScopeHandler.declareName (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:1585:12)
    at TypeScriptScopeHandler.declareName (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:4892:11)
    at TypeScriptParserMixin.declareNameFromIdentifier (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7584:16)
    at TypeScriptParserMixin.checkIdentifier (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7580:12)
    at TypeScriptParserMixin.checkLVal (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:7517:12)
    at TypeScriptParserMixin.parseVarId (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13429:10)
    at TypeScriptParserMixin.parseVarId (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9769:11)
    at TypeScriptParserMixin.parseVar (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13400:12)
    at TypeScriptParserMixin.parseVarStatement (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13247:10)
    at TypeScriptParserMixin.parseVarStatement (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9425:31)
    at TypeScriptParserMixin.parseStatementContent (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12868:23)
    at TypeScriptParserMixin.parseStatementContent (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:9525:18)
    at TypeScriptParserMixin.parseStatementLike (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12784:17)
    at TypeScriptParserMixin.parseModuleItem (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12761:17)
    at TypeScriptParserMixin.parseBlockOrModuleBlockBody (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13333:36)
    at TypeScriptParserMixin.parseBlockBody (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:13326:10)
    at TypeScriptParserMixin.parseProgram (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12639:10)
    at TypeScriptParserMixin.parseTopLevel (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:12629:25)
    at TypeScriptParserMixin.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:14505:25)
    at TypeScriptParserMixin.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:10143:18)
    at Object.parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@babel+parser@7.29.3/node_modules/@babel/parser/lib/index.js:14539:38)
    at parse (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:19874:25)
    at new ScriptCompileContext (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:19891:53)
    at Object.compileScript (/home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vue+compiler-sfc@3.5.34/node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js:24921:15)
    at resolveScript (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:276:36)
    at genScriptCode (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1430:17)
    at transformMain (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1312:53)
    at TransformPluginContextImpl.handler (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/@vitejs+plugin-vue@6.0.6_vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0__sg2c2qr7e3zvnmuvffxjczpbta/node_modules/@vitejs/plugin-vue/dist/index.mjs:1714:27)
    at TransformPluginContextImpl.handler (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33254:13)
    at plugin (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/bindingify-input-options-XPJLJOD0.mjs:1511:30)
    at plugin.<computed> (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/bindingify-input-options-XPJLJOD0.mjs:1959:18)[31m
    at aggregateBindingErrorsIntoJsError (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/error-BHRSI0R7.mjs:48:18)
    at unwrapBindingResult (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/error-BHRSI0R7.mjs:18:128)
    at #build (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/rolldown@1.1.5/node_modules/rolldown/dist/shared/rolldown-build-CtPvmZgJ.mjs:3276:34)
    at async buildEnvironment (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33011:66)
    at async Object.build (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33433:19)
    at async Object.buildApp (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/chunks/node.js:33430:153)
    at async CAC.<anonymous> (file:///home/runner/work/NPDMS/NPDMS/yudao-ui/yudao-ui-admin-vue3/node_modules/.pnpm/vite@8.1.4_@types+node@25.6.0_esbuild@0.27.3_jiti@2.6.1_sass@1.99.0_terser@5.46.2_yaml@2.8.4/node_modules/vite/dist/node/cli.js:776:3) {
  errors: [Getter/Setter]
}[39m
 ELIFECYCLE  Command failed with exit code 1.
```
