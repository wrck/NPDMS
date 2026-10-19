# P15 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| tmp_dppms_contract_completion_scope.tenant_id | #{query.tenantId} | insertScope |
| tmp_dppms_contract_completion_scope.kind | #{row.kind} | insertScope |
| tmp_dppms_contract_completion_scope.target_id | #{row.id} | insertScope |
| tmp_dppms_review_unique_company.tenant_id | #{query.tenantId} | insertCompanies |
| tmp_dppms_review_unique_company.code | #{row.code} | insertCompanies |
| tmp_dppms_review_unique_company.org_id | #{row.id} | insertCompanies |
| tmp_dppms_review_unique_company.name | #{row.name} | insertCompanies |
| tmp_dppms_review_unique_department.tenant_id | #{query.tenantId} | insertDepartments |
| tmp_dppms_review_unique_department.code | #{row.code} | insertDepartments |
| tmp_dppms_review_unique_department.org_id | #{row.id} | insertDepartments |
| tmp_dppms_review_unique_department.name | #{row.name} | insertDepartments |

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_lifecycle_status`, `status`, `company_code`, `contract_no`, `customer_code`, `customer_name`, `master_source_record_key`, `master_source_system`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_lifecycle_status, :status, :company_code, :contract_no, :customer_code, :customer_name, :master_source_record_key, :master_source_system);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_contract_reference` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `contract_no`, `department_code`, `contract_type`, `customer_name`, `project_name`, `warranty_years`, `market_code`, `market_name`, `system_source_key`, `system_name`, `remark`, `source_record_key`, `source_system`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :contract_no, :department_code, :contract_type, :customer_name, :project_name, :warranty_years, :market_code, :market_name, :system_source_key, :system_name, :remark, :source_record_key, :source_system);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_package` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `package_no`, `shipment_time`, `warranty_start_time`, `warranty_end_time`, `receiver_name`, `express_no`, `carrier_name`, `source_system`, `source_record_key`, `carrier_code`, `shipment_contract_source_key`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :package_no, :shipment_time, :warranty_start_time, :warranty_end_time, :receiver_name, :express_no, :carrier_name, :source_system, :source_record_key, :carrier_code, :shipment_contract_source_key);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_shipment` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `sync_status`, `device_sn`, `shipment_time`, `package_no`, `legacy_package_key`, `contract_no`, `event_type`, `warranty_start_date`, `warranty_months`, `rma_no`, `rma_related_sn`, `rma_marked`, `source_system`, `source_key`, `source_updated_at`, `source_payload`, `order_no`, `line_no`, `product_code`, `secondary_sn`, `secondary_item`, `internal_serial_no`, `is_rma`, `profit_center`, `sole_agent_suffix`, `sync_time`, `uuid`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :sync_status, :device_sn, :shipment_time, :package_no, :legacy_package_key, :contract_no, :event_type, :warranty_start_date, :warranty_months, :rma_no, :rma_related_sn, :rma_marked, :source_system, :source_key, :source_updated_at, :source_payload, :order_no, :line_no, :product_code, :secondary_sn, :secondary_item, :internal_serial_no, :is_rma, :profit_center, :sole_agent_suffix, :sync_time, :uuid);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_relationship` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `source_device_sn`, `target_device_sn`, `relationship_type`, `contract_no`, `effective_from`, `source_system`, `source_key`, `source_version`, `source_item`, `target_item`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :source_device_sn, :target_device_sn, :relationship_type, :contract_no, :effective_from, :source_system, :source_key, :source_version, :source_item, :target_item);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_lifecycle_status`, `status`, `company_code`, `contract_no`, `customer_code`, `customer_name`, `master_source_record_key`, `master_source_system`, `currency_name`, `currency_code`, `contract_amount`, `contract_create_time`, `project_name`, `project_code`, `market_code`, `market_name`, `department_code`, `department_name`, `industry_code`, `industry_name`, `marketing_representative_code`, `marketing_representative_name`, `secondary_representative_code`, `system_name`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_lifecycle_status, :status, :company_code, :contract_no, :customer_code, :customer_name, :master_source_record_key, :master_source_system, :currency_name, :currency_code, :contract_amount, :contract_create_time, :project_name, :project_code, :market_code, :market_name, :department_code, :department_name, :industry_code, :industry_name, :marketing_representative_code, :marketing_representative_name, :secondary_representative_code, :system_name, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_contract_reference` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `contract_no`, `company_code`, `department_code`, `contract_type`, `customer_name`, `project_name`, `warranty_years`, `market_code`, `market_name`, `system_source_key`, `system_name`, `remark`, `source_record_key`, `source_system`, `contract_id`, `company_id`, `company_name`, `department_id`, `department_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :contract_no, :company_code, :department_code, :contract_type, :customer_name, :project_name, :warranty_years, :market_code, :market_name, :system_source_key, :system_name, :remark, :source_record_key, :source_system, :contract_id, :company_id, :company_name, :department_id, :department_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_package` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `package_no`, `shipment_time`, `warranty_start_time`, `warranty_end_time`, `receiver_name`, `express_no`, `carrier_name`, `source_system`, `source_record_key`, `carrier_code`, `shipment_contract_ref_id`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :package_no, :shipment_time, :warranty_start_time, :warranty_end_time, :receiver_name, :express_no, :carrier_name, :source_system, :source_record_key, :carrier_code, :shipment_contract_ref_id);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_shipment` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `sync_status`, `device_sn`, `shipment_time`, `package_no`, `legacy_package_key`, `contract_no`, `event_type`, `warranty_start_date`, `warranty_months`, `rma_no`, `rma_related_sn`, `rma_marked`, `source_system`, `source_key`, `source_updated_at`, `source_payload`, `order_no`, `line_no`, `product_code`, `secondary_sn`, `secondary_item`, `internal_serial_no`, `is_rma`, `profit_center`, `sole_agent_suffix`, `sync_time`, `uuid`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :sync_status, :device_sn, :shipment_time, :package_no, :legacy_package_key, :contract_no, :event_type, :warranty_start_date, :warranty_months, :rma_no, :rma_related_sn, :rma_marked, :source_system, :source_key, :source_updated_at, :source_payload, :order_no, :line_no, :product_code, :secondary_sn, :secondary_item, :internal_serial_no, :is_rma, :profit_center, :sole_agent_suffix, :sync_time, :uuid);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_relationship` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `source_device_sn`, `target_device_sn`, `relationship_type`, `contract_no`, `effective_from`, `source_system`, `source_key`, `source_version`, `source_item`, `target_item`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :source_device_sn, :target_device_sn, :relationship_type, :contract_no, :effective_from, :source_system, :source_key, :source_version, :source_item, :target_item);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_contract.id | #{row.target.id} | insertContract |
| com_contract.tenant_id | #{row.target.tenantId} | insertContract |
| com_contract.company_id | #{row.target.companyId} | insertContract |
| com_contract.company_code | #{row.target.companyCode} | insertContract |
| com_contract.company_name | #{row.target.companyName} | insertContract |
| com_contract.contract_no | #{row.target.contractNo} | insertContract |
| com_contract.master_source_system | #{row.target.masterSourceSystem} | insertContract |
| com_contract.master_source_record_key | #{row.target.masterSourceRecordKey} | insertContract |
| com_contract.master_source_version | #{row.target.masterSourceVersion} | insertContract |
| com_contract.contract_type | #{row.target.contractType} | insertContract |
| com_contract.customer_id | #{row.target.customerId} | insertContract |
| com_contract.customer_code | #{row.target.customerCode} | insertContract |
| com_contract.customer_name | #{row.target.customerName} | insertContract |
| com_contract.contract_name | #{row.target.contractName} | insertContract |
| com_contract.contract_amount | #{row.target.contractAmount} | insertContract |
| com_contract.currency_code | #{row.target.currencyCode} | insertContract |
| com_contract.currency_name | #{row.target.currencyName} | insertContract |
| com_contract.contract_create_time | #{row.target.contractCreateTime} | insertContract |
| com_contract.authority_status | #{row.target.authorityStatus} | insertContract |
| com_contract.source_lifecycle_status | #{row.target.sourceLifecycleStatus} | insertContract |
| com_contract.effective_date | #{row.target.effectiveDate} | insertContract |
| com_contract.expiry_date | #{row.target.expiryDate} | insertContract |
| com_contract.project_name | #{row.target.projectName} | insertContract |
| com_contract.project_code | #{row.target.projectCode} | insertContract |
| com_contract.market_code | #{row.target.marketCode} | insertContract |
| com_contract.market_name | #{row.target.marketName} | insertContract |
| com_contract.department_code | #{row.target.departmentCode} | insertContract |
| com_contract.department_name | #{row.target.departmentName} | insertContract |
| com_contract.system_code | #{row.target.systemCode} | insertContract |
| com_contract.system_name | #{row.target.systemName} | insertContract |
| com_contract.expend_code | #{row.target.expendCode} | insertContract |
| com_contract.expend_name | #{row.target.expendName} | insertContract |
| com_contract.industry_code | #{row.target.industryCode} | insertContract |
| com_contract.industry_name | #{row.target.industryName} | insertContract |
| com_contract.marketing_representative_code | #{row.target.marketingRepresentativeCode} | insertContract |
| com_contract.marketing_representative_name | #{row.target.marketingRepresentativeName} | insertContract |
| com_contract.secondary_representative_code | #{row.target.secondaryRepresentativeCode} | insertContract |
| com_contract.source_sync_time | #{row.target.sourceSyncTime} | insertContract |
| com_contract.source_updated_at | #{row.target.sourceUpdatedAt} | insertContract |
| com_contract.status | #{row.target.status} | insertContract |
| com_contract.version | #{row.target.version} | insertContract |
| com_contract.creator | #{row.target.creator} | insertContract |
| com_contract.create_time | #{row.target.createTime} | insertContract |
| com_contract.updater | #{row.target.updater} | insertContract |
| com_contract.update_time | #{row.target.updateTime} | insertContract |
| com_contract.deleted | #{row.target.deleted} | insertContract |
| com_shipment_contract_reference.id | #{row.target.id} | insertShipmentContract |
| com_shipment_contract_reference.tenant_id | #{row.target.tenantId} | insertShipmentContract |
| com_shipment_contract_reference.contract_id | #{row.target.contractId} | insertShipmentContract |
| com_shipment_contract_reference.contract_no | #{row.target.contractNo} | insertShipmentContract |
| com_shipment_contract_reference.company_id | #{row.target.companyId} | insertShipmentContract |
| com_shipment_contract_reference.company_code | #{row.target.companyCode} | insertShipmentContract |
| com_shipment_contract_reference.company_name | #{row.target.companyName} | insertShipmentContract |
| com_shipment_contract_reference.department_id | #{row.target.departmentId} | insertShipmentContract |
| com_shipment_contract_reference.department_code | #{row.target.departmentCode} | insertShipmentContract |
| com_shipment_contract_reference.department_name | #{row.target.departmentName} | insertShipmentContract |
| com_shipment_contract_reference.contract_type | #{row.target.contractType} | insertShipmentContract |
| com_shipment_contract_reference.customer_name | #{row.target.customerName} | insertShipmentContract |
| com_shipment_contract_reference.project_name | #{row.target.projectName} | insertShipmentContract |
| com_shipment_contract_reference.market_code | #{row.target.marketCode} | insertShipmentContract |
| com_shipment_contract_reference.market_name | #{row.target.marketName} | insertShipmentContract |
| com_shipment_contract_reference.system_source_key | #{row.target.systemSourceKey} | insertShipmentContract |
| com_shipment_contract_reference.system_code | #{row.target.systemCode} | insertShipmentContract |
| com_shipment_contract_reference.system_name | #{row.target.systemName} | insertShipmentContract |
| com_shipment_contract_reference.warranty_years | #{row.target.warrantyYears} | insertShipmentContract |
| com_shipment_contract_reference.remark | #{row.target.remark} | insertShipmentContract |
| com_shipment_contract_reference.mapping_status | #{row.target.mappingStatus} | insertShipmentContract |
| com_shipment_contract_reference.source_system | #{row.target.sourceSystem} | insertShipmentContract |
| com_shipment_contract_reference.source_record_key | #{row.target.sourceRecordKey} | insertShipmentContract |
| com_shipment_contract_reference.source_sync_time | #{row.target.sourceSyncTime} | insertShipmentContract |
| com_shipment_contract_reference.creator | #{row.target.creator} | insertShipmentContract |
| com_shipment_contract_reference.create_time | #{row.target.createTime} | insertShipmentContract |
| com_shipment_contract_reference.updater | #{row.target.updater} | insertShipmentContract |
| com_shipment_contract_reference.update_time | #{row.target.updateTime} | insertShipmentContract |
| com_shipment_contract_reference.deleted | #{row.target.deleted} | insertShipmentContract |
| com_shipment_package.id | #{row.target.id} | insertPackage |
| com_shipment_package.tenant_id | #{row.target.tenantId} | insertPackage |
| com_shipment_package.shipment_contract_ref_id | #{row.target.shipmentContractRefId} | insertPackage |
| com_shipment_package.package_no | #{row.target.packageNo} | insertPackage |
| com_shipment_package.shipment_time | #{row.target.shipmentTime} | insertPackage |
| com_shipment_package.warranty_start_time | #{row.target.warrantyStartTime} | insertPackage |
| com_shipment_package.warranty_end_time | #{row.target.warrantyEndTime} | insertPackage |
| com_shipment_package.receiver_name | #{row.target.receiverName} | insertPackage |
| com_shipment_package.express_no | #{row.target.expressNo} | insertPackage |
| com_shipment_package.carrier_name | #{row.target.carrierName} | insertPackage |
| com_shipment_package.mapping_status | #{row.target.mappingStatus} | insertPackage |
| com_shipment_package.source_system | #{row.target.sourceSystem} | insertPackage |
| com_shipment_package.source_record_key | #{row.target.sourceRecordKey} | insertPackage |
| com_shipment_package.source_sync_time | #{row.target.sourceSyncTime} | insertPackage |
| com_shipment_package.creator | #{row.target.creator} | insertPackage |
| com_shipment_package.create_time | #{row.target.createTime} | insertPackage |
| com_shipment_package.updater | #{row.target.updater} | insertPackage |
| com_shipment_package.update_time | #{row.target.updateTime} | insertPackage |
| com_shipment_package.deleted | #{row.target.deleted} | insertPackage |
| com_shipment_package.carrier_code | #{row.target.carrierCode} | insertPackage |
| com_shipment_package.shipment_contract_source_key | #{row.target.shipmentContractSourceKey} | insertPackage |
| ast_device_shipment.id | #{row.target.id} | insertShipmentEvent |
| ast_device_shipment.device_sn | #{row.target.deviceSn} | insertShipmentEvent |
| ast_device_shipment.shipment_time | #{row.target.shipmentTime} | insertShipmentEvent |
| ast_device_shipment.package_no | #{row.target.packageNo} | insertShipmentEvent |
| ast_device_shipment.contract_no | #{row.target.contractNo} | insertShipmentEvent |
| ast_device_shipment.event_type | #{row.target.eventType} | insertShipmentEvent |
| ast_device_shipment.warranty_start_date | #{row.target.warrantyStartDate} | insertShipmentEvent |
| ast_device_shipment.warranty_months | #{row.target.warrantyMonths} | insertShipmentEvent |
| ast_device_shipment.rma_no | #{row.target.rmaNo} | insertShipmentEvent |
| ast_device_shipment.related_device_sn | #{row.target.relatedDeviceSn} | insertShipmentEvent |
| ast_device_shipment.source_system | #{row.target.sourceSystem} | insertShipmentEvent |
| ast_device_shipment.source_key | #{row.target.sourceKey} | insertShipmentEvent |
| ast_device_shipment.source_version | #{row.target.sourceVersion} | insertShipmentEvent |
| ast_device_shipment.source_updated_at | #{row.target.sourceUpdatedAt} | insertShipmentEvent |
| ast_device_shipment.synced_at | #{row.target.syncedAt} | insertShipmentEvent |
| ast_device_shipment.sync_status | #{row.target.syncStatus} | insertShipmentEvent |
| ast_device_shipment.version | #{row.target.version} | insertShipmentEvent |
| ast_device_shipment.creator | #{row.target.creator} | insertShipmentEvent |
| ast_device_shipment.create_time | #{row.target.createTime} | insertShipmentEvent |
| ast_device_shipment.updater | #{row.target.updater} | insertShipmentEvent |
| ast_device_shipment.update_time | #{row.target.updateTime} | insertShipmentEvent |
| ast_device_shipment.deleted | #{row.target.deleted} | insertShipmentEvent |
| ast_device_shipment.tenant_id | #{row.target.tenantId} | insertShipmentEvent |
| ast_device_shipment.legacy_package_key | #{row.target.legacyPackageKey} | insertShipmentEvent |
| ast_device_shipment.rma_related_sn | #{row.target.rmaRelatedSn} | insertShipmentEvent |
| ast_device_shipment.rma_marked | #{row.target.rmaMarked} | insertShipmentEvent |
| ast_device_shipment.order_line_id | #{row.target.orderLineId} | insertShipmentEvent |
| ast_device_shipment.source_payload | #{row.target.sourcePayload} | insertShipmentEvent |
| ast_device_shipment.order_no | #{row.target.orderNo} | insertShipmentEvent |
| ast_device_shipment.line_no | #{row.target.lineNo} | insertShipmentEvent |
| ast_device_shipment.product_code | #{row.target.productCode} | insertShipmentEvent |
| ast_device_shipment.secondary_sn | #{row.target.secondarySn} | insertShipmentEvent |
| ast_device_shipment.secondary_item | #{row.target.secondaryItem} | insertShipmentEvent |
| ast_device_shipment.internal_serial_no | #{row.target.internalSerialNo} | insertShipmentEvent |
| ast_device_shipment.is_rma | #{row.target.isRma} | insertShipmentEvent |
| ast_device_shipment.profit_center | #{row.target.profitCenter} | insertShipmentEvent |
| ast_device_shipment.sole_agent_suffix | #{row.target.soleAgentSuffix} | insertShipmentEvent |
| ast_device_shipment.sync_time | #{row.target.syncTime} | insertShipmentEvent |
| ast_device_shipment.uuid | #{row.target.uuid} | insertShipmentEvent |
| ast_device_relationship.id | #{row.target.id} | insertDeviceRelationship |
| ast_device_relationship.source_device_sn | #{row.target.sourceDeviceSn} | insertDeviceRelationship |
| ast_device_relationship.target_device_sn | #{row.target.targetDeviceSn} | insertDeviceRelationship |
| ast_device_relationship.relationship_type | #{row.target.relationshipType} | insertDeviceRelationship |
| ast_device_relationship.contract_no | #{row.target.contractNo} | insertDeviceRelationship |
| ast_device_relationship.effective_from | #{row.target.effectiveFrom} | insertDeviceRelationship |
| ast_device_relationship.effective_to | #{row.target.effectiveTo} | insertDeviceRelationship |
| ast_device_relationship.source_system | #{row.target.sourceSystem} | insertDeviceRelationship |
| ast_device_relationship.source_key | #{row.target.sourceKey} | insertDeviceRelationship |
| ast_device_relationship.source_version | #{row.target.sourceVersion} | insertDeviceRelationship |
| ast_device_relationship.version | #{row.target.version} | insertDeviceRelationship |
| ast_device_relationship.creator | #{row.target.creator} | insertDeviceRelationship |
| ast_device_relationship.create_time | #{row.target.createTime} | insertDeviceRelationship |
| ast_device_relationship.updater | #{row.target.updater} | insertDeviceRelationship |
| ast_device_relationship.update_time | #{row.target.updateTime} | insertDeviceRelationship |
| ast_device_relationship.deleted | #{row.target.deleted} | insertDeviceRelationship |
| ast_device_relationship.tenant_id | #{row.target.tenantId} | insertDeviceRelationship |
| ast_device_relationship.source_item | #{row.target.sourceItem} | insertDeviceRelationship |
| ast_device_relationship.target_item | #{row.target.targetItem} | insertDeviceRelationship |
