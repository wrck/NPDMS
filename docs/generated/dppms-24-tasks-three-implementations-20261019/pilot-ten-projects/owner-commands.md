# 历史命令入口与Owner代码

[返回历史补充](README.md)

以下代码是保存点`e21f02d2d`的原件摘录，静态复制到本报告供审阅，未编译或执行。来源路径说明模块归属；点击源码快照可阅读当时处理逻辑。当前工作树同名文件可能已有后来修复。

统一旧前缀：`/api/v1/pms/projects/{projectId}/legacy-detail`。Owner命令不是可据此恢复启用的工作台任务；本次只读查阅。

| POST子路径 | Controller调用 |
|---|---|
| /final-customer-contact-previews | finalContacts.preview |
| /final-customer-contact-imports | finalContacts.migrate |
| /member-import-previews | memberImports.preview |
| /member-history-imports | memberImports.migrate |
| /member-history-previews | memberHistory.preview |
| /license-previews | licenses.preview |
| /license-imports | licenses.migrate |
| /shipment-reference-previews | shipmentReferences.preview |
| /shipment-reference-corrections | shipmentReferences.correct |
| /software-event-withdrawal-previews | softwareEventWithdrawal.preview |
| /software-event-withdrawals | softwareEventWithdrawal.withdraw |
| /software-history-correction-previews | softwareHistory.preview |
| /software-history-corrections | softwareHistory.correct |
| /lease-product-previews | leaseProducts.preview |
| /lease-product-imports | leaseProducts.reconcile |
| /product-configuration-previews | productConfigurations.preview |
| /product-configuration-imports | productConfigurations.reconcile |
| /real-product-previews | realProducts.preview |
| /real-product-imports | realProducts.reconcile |
| /installation-record-previews | installationRecords.preview |
| /installation-record-corrections | installationRecords.reconcile |
| /factory-record-corrections | factoryRecords.reconcile |
| /execution-field-corrections | executionFields.reconcile |
| /product-scope-previews | productScopes.preview |
| /product-scope-corrections | productScopes.reconcile |
| /order-field-corrections | orderFields.reconcile |
| /project-header-corrections | service.correctProjectHeader |
| /collection-plan-corrections | collectionPlans.reconcile |
| /native-imports | service.migrate |
| /factory-corrections | service.correctFactoryVersions |
| /carrier-corrections | service.correctCarrierNames |
| /installation-candidate-corrections | service.correctInstallationCandidates |

## 当时实现原件

| 源码快照 | 历史仓库路径 |
|---|---|
| [DppmsLicenseRecordService.java.txt](historical-code/DppmsLicenseRecordService.java.txt) | pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/license/DppmsLicenseRecordService.java |
| [DppmsContractCollectionPlanSyncAdapter.java.txt](historical-code/DppmsContractCollectionPlanSyncAdapter.java.txt) | pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsContractCollectionPlanSyncAdapter.java |
| [DppmsProjectContractRelationSyncAdapter.java.txt](historical-code/DppmsProjectContractRelationSyncAdapter.java.txt) | pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectContractRelationSyncAdapter.java |
| [DppmsProjectIdentitySyncAdapter.java.txt](historical-code/DppmsProjectIdentitySyncAdapter.java.txt) | pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectIdentitySyncAdapter.java |
| [DppmsProjectMasterSyncAdapter.java.txt](historical-code/DppmsProjectMasterSyncAdapter.java.txt) | pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java |
| [ProjectLegacyEntityMigrationController.java.txt](historical-code/ProjectLegacyEntityMigrationController.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/legacydetail/ProjectLegacyEntityMigrationController.java |
| [ProjectContractCollectionPlanReconciliationService.java.txt](historical-code/ProjectContractCollectionPlanReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectContractCollectionPlanReconciliationService.java |
| [ProjectExecutionFieldReconciliationService.java.txt](historical-code/ProjectExecutionFieldReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectExecutionFieldReconciliationService.java |
| [ProjectFactoryRecordImportService.java.txt](historical-code/ProjectFactoryRecordImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectFactoryRecordImportService.java |
| [ProjectFactoryRecordReconciliationService.java.txt](historical-code/ProjectFactoryRecordReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectFactoryRecordReconciliationService.java |
| [ProjectFinalCustomerContactImportService.java.txt](historical-code/ProjectFinalCustomerContactImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectFinalCustomerContactImportService.java |
| [ProjectInstallationRecordReconciliationService.java.txt](historical-code/ProjectInstallationRecordReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectInstallationRecordReconciliationService.java |
| [ProjectLeaseProductImportService.java.txt](historical-code/ProjectLeaseProductImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLeaseProductImportService.java |
| [ProjectLegacyEntityMigrationService.java.txt](historical-code/ProjectLegacyEntityMigrationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLegacyEntityMigrationService.java |
| [ProjectLicenseRecordMigrationService.java.txt](historical-code/ProjectLicenseRecordMigrationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectLicenseRecordMigrationService.java |
| [ProjectMasterMigrationService.java.txt](historical-code/ProjectMasterMigrationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMasterMigrationService.java |
| [ProjectMemberHistoryImportService.java.txt](historical-code/ProjectMemberHistoryImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMemberHistoryImportService.java |
| [ProjectMemberHistoryMigrationService.java.txt](historical-code/ProjectMemberHistoryMigrationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectMemberHistoryMigrationService.java |
| [ProjectOrderFieldReconciliationService.java.txt](historical-code/ProjectOrderFieldReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectOrderFieldReconciliationService.java |
| [ProjectProductConfigurationImportService.java.txt](historical-code/ProjectProductConfigurationImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductConfigurationImportService.java |
| [ProjectProductScopeReconciliationService.java.txt](historical-code/ProjectProductScopeReconciliationService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectProductScopeReconciliationService.java |
| [ProjectRealProductImportService.java.txt](historical-code/ProjectRealProductImportService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectRealProductImportService.java |
| [ProjectShipmentReferenceCorrectionService.java.txt](historical-code/ProjectShipmentReferenceCorrectionService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectShipmentReferenceCorrectionService.java |
| [ProjectSoftwareHistoryCorrectionService.java.txt](historical-code/ProjectSoftwareHistoryCorrectionService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSoftwareHistoryCorrectionService.java |
| [ProjectSoftwareLegacyEventWithdrawalService.java.txt](historical-code/ProjectSoftwareLegacyEventWithdrawalService.java.txt) | pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/legacydetail/ProjectSoftwareLegacyEventWithdrawalService.java |

直接引用的Mapper：[历史SQL声明](historical-owner-sql.sql)及[完整动态节点](historical-owner-mapper-nodes.json)。ORM写入没有独立SQL声明时以对应Owner/DO为准，不编造SQL。方法及行号索引见[结构化代码索引](historical-code-index.json)。

