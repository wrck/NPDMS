# P15 新虚基类实际读写字段衔接

当前C代码投影。原表值经Java归并/转换/授权核验后再进入正式INSERT；本表不宣称同名即可直接写入。预览三份合同原表输入与实际写入本地合同输入分开，正式输入未由预览创建。设备关系的raw_relationship_created_at由relationshipTime精确解析后赋给effectiveFrom；发货时间/合同号由两个原键投影在流式回调前补入，均不是正式业务ID回查。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectContract | CAST(JSON_ARRAY(o.company_code,o.contract_no) AS CHAR) | raw_key |
| selectContract | o.migration_issue | issue |
| selectContract | o.company_code | company_code |
| selectContract | o.contract_no | contract_no |
| selectContract | o.customer_code | customer_code |
| selectContract | o.customer_name | customer_name |
| selectContract | 'DPPMS_ERP' | master_source_system |
| selectContract | CAST(JSON_ARRAY(o.company_code,o.contract_no) AS CHAR) | master_source_record_key |
| selectContract | rr.currency_name | currency_name |
| selectContract | rr.currency_name | currency_code |
| selectContract | rr.contract_amount | contract_amount |
| selectContract | rr.contract_create_time | contract_create_time |
| selectContract | COALESCE(ss.project_name,rr.project_name) | project_name |
| selectContract | rr.project_code | project_code |
| selectContract | COALESCE(ss.market_code,rr.market_code) | market_code |
| selectContract | COALESCE(ss.market_name,rr.market_name) | market_name |
| selectContract | COALESCE(ss.department_code,rr.department_code) | department_code |
| selectContract | rr.department_name | department_name |
| selectContract | rr.industry_code | industry_code |
| selectContract | rr.industry_name | industry_name |
| selectContract | rr.marketing_representative_code | marketing_representative_code |
| selectContract | rr.marketing_representative_name | marketing_representative_name |
| selectContract | rr.secondary_representative_code | secondary_representative_code |
| selectContract | ss.system_name | system_name |
| selectShipmentContract | f.contract_id | raw_key |
| selectShipmentContract | f.warranty | raw_warranty |
| selectShipmentContract | 'DPPMS' | source_system |
| selectShipmentContract | f.contract_id | source_record_key |
| selectShipmentContract | f.contract_code | contract_no |
| selectShipmentContract | f.office_code | department_code |
| selectShipmentContract | CAST(f.contract_type AS CHAR) | contract_type |
| selectShipmentContract | f.customer_name | customer_name |
| selectShipmentContract | f.project_name | project_name |
| selectShipmentContract | f.marketCode | market_code |
| selectShipmentContract | f.marketName | market_name |
| selectShipmentContract | CAST(f.systemId AS CHAR) | system_source_key |
| selectShipmentContract | f.systemName | system_name |
| selectShipmentContract | f.remark | remark |
| selectPackage | p.packlist_id | raw_key |
| selectPackage | 'DPPMS' | source_system |
| selectPackage | p.packlist_id | source_record_key |
| selectPackage | p.packlist_id | package_no |
| selectPackage | p.packdate | shipment_time |
| selectPackage | p.warrantyStartTime | warranty_start_time |
| selectPackage | p.warrantyEndTime | warranty_end_time |
| selectPackage | p.receiveName | receiver_name |
| selectPackage | p.emsNum | express_no |
| selectPackage | p.emsCompany | carrier_code |
| selectPackage | p.con_id | shipment_contract_source_key |
| selectShipmentEvent | CAST(b.id AS CHAR) | raw_key |
| selectShipmentEvent | 'DPPMS' | source_system |
| selectShipmentEvent | CAST(b.id AS CHAR) | source_key |
| selectShipmentEvent | b.barcode | device_sn |
| selectShipmentEvent | b.pack_id | package_no |
| selectShipmentEvent | b.pack_id | legacy_package_key |
| selectShipmentEvent | CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END | event_type |
| selectShipmentEvent | CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END | rma_marked |
| selectShipmentEvent | b.warrantyStartDate | warranty_start_date |
| selectShipmentEvent | b.warrantyMonth | warranty_months |
| selectShipmentEvent | b.rma_no | rma_no |
| selectShipmentEvent | b.rmaBarcode | rma_related_sn |
| selectShipmentEvent | b.updateTime | source_updated_at |
| selectShipmentEvent | b.orderNumber | order_no |
| selectShipmentEvent | CAST(b.lineNum AS CHAR) | line_no |
| selectShipmentEvent | b.item | product_code |
| selectShipmentEvent | b.barcode2 | secondary_sn |
| selectShipmentEvent | b.item2 | secondary_item |
| selectShipmentEvent | b.com_barcode | internal_serial_no |
| selectShipmentEvent | b.isRMA | is_rma |
| selectShipmentEvent | b.profitCenter | profit_center |
| selectShipmentEvent | b.soleAgentSuffix | sole_agent_suffix |
| selectShipmentEvent | b.syncTime | sync_time |
| selectShipmentEvent | b.uuid | uuid |
| selectDeviceRelationship | CAST(r.id AS CHAR) | raw_key |
| selectDeviceRelationship | 'DPPMS' | source_system |
| selectDeviceRelationship | CAST(r.id AS CHAR) | source_key |
| selectDeviceRelationship | r.sn1 | source_device_sn |
| selectDeviceRelationship | r.sn2 | target_device_sn |
| selectDeviceRelationship | 'EXTRA_SN' | relationship_type |
| selectDeviceRelationship | r.contract | contract_no |
| selectDeviceRelationship | r.createtime | raw_relationship_created_at |
| selectDeviceRelationship | r.updatetime | source_version |
| selectDeviceRelationship | r.item1 | source_item |
| selectDeviceRelationship | r.item2 | target_item |
| selectContractCustomers | CAST(JSON_ARRAY(CASE r.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE r.compCode END,r.contractNo) AS CHAR) | raw_key |
| selectContractCustomers | CASE r.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE r.compCode END | company_code |
| selectContractCustomers | r.contractNo | contract_no |
| selectContractCustomers | r.customerName | customer_name |
| selectContractCustomers | CASE WHEN r.customerCode IS NULL OR r.customerCode='' THEN r.customerCode<br> WHEN r.customerCode REGEXP '^[A-Za-z]' AND CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(r.customerCode,2)))<=5<br> THEN CONCAT(LEFT(r.customerCode,1),LPAD(TRIM(LEADING '0' FROM SUBSTRING(r.customerCode,2)),5,'0')) ELSE NULL END | customer_code |
| selectContractCustomers | CASE WHEN r.customerCode IS NOT NULL AND r.customerCode<>'' AND (NOT(r.customerCode REGEXP '^[A-Za-z]') OR CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(r.customerCode,2)))>5) THEN 'ERP_CUSTOMER_CODE_FORMAT_INVALID' ELSE NULL END | issue |
| selectContractShipmentFields | s.contract_code | contract_no |
| selectContractShipmentFields | SUBSTRING(MAX(CASE WHEN s.`project_name` IS NOT NULL AND CAST(s.`project_name` AS CHAR)<>'' THEN CONCAT(RPAD(s.contract_id,64,' '),CAST(s.`project_name` AS CHAR)) END),65) | project_name |
| selectContractShipmentFields | SUBSTRING(MAX(CASE WHEN s.`marketCode` IS NOT NULL AND CAST(s.`marketCode` AS CHAR)<>'' THEN CONCAT(RPAD(s.contract_id,64,' '),CAST(s.`marketCode` AS CHAR)) END),65) | market_code |
| selectContractShipmentFields | SUBSTRING(MAX(CASE WHEN s.`marketName` IS NOT NULL AND CAST(s.`marketName` AS CHAR)<>'' THEN CONCAT(RPAD(s.contract_id,64,' '),CAST(s.`marketName` AS CHAR)) END),65) | market_name |
| selectContractShipmentFields | SUBSTRING(MAX(CASE WHEN s.`office_code` IS NOT NULL AND CAST(s.`office_code` AS CHAR)<>'' THEN CONCAT(RPAD(s.contract_id,64,' '),CAST(s.`office_code` AS CHAR)) END),65) | department_code |
| selectContractShipmentFields | SUBSTRING(MAX(CASE WHEN s.`systemName` IS NOT NULL AND CAST(s.`systemName` AS CHAR)<>'' THEN CONCAT(RPAD(s.contract_id,64,' '),CAST(s.`systemName` AS CHAR)) END),65) | system_name |
| selectContractReceivableFields | s.contract_num | contract_no |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`currency_name` IS NOT NULL AND CAST(s.`currency_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`currency_name` AS CHAR)) END),40) | currency_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`contract_money_amount` IS NOT NULL AND CAST(s.`contract_money_amount` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`contract_money_amount` AS CHAR)) END),40) | contract_amount |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`contract_create_date` IS NOT NULL AND CAST(s.`contract_create_date` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`contract_create_date` AS CHAR)) END),40) | contract_create_time |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`project_name` IS NOT NULL AND CAST(s.`project_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`project_name` AS CHAR)) END),40) | project_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`projectCode` IS NOT NULL AND CAST(s.`projectCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`projectCode` AS CHAR)) END),40) | project_code |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`marketCode` IS NOT NULL AND CAST(s.`marketCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`marketCode` AS CHAR)) END),40) | market_code |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`maketing_department_name` IS NOT NULL AND CAST(s.`maketing_department_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`maketing_department_name` AS CHAR)) END),40) | market_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`officeCode` IS NOT NULL AND CAST(s.`officeCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`officeCode` AS CHAR)) END),40) | department_code |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`office_name` IS NOT NULL AND CAST(s.`office_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`office_name` AS CHAR)) END),40) | department_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`industryId` IS NOT NULL AND CAST(s.`industryId` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`industryId` AS CHAR)) END),40) | industry_code |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`industry_name` IS NOT NULL AND CAST(s.`industry_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`industry_name` AS CHAR)) END),40) | industry_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`usernamec` IS NOT NULL AND CAST(s.`usernamec` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`usernamec` AS CHAR)) END),40) | marketing_representative_code |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`marketing_representative_name` IS NOT NULL AND CAST(s.`marketing_representative_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`marketing_representative_name` AS CHAR)) END),40) | marketing_representative_name |
| selectContractReceivableFields | SUBSTRING(MAX(CASE WHEN s.`usernamec2` IS NOT NULL AND CAST(s.`usernamec2` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(s.id AS CHAR),20,'0')),CAST(s.`usernamec2` AS CHAR)) END),40) | secondary_representative_code |
| selectShipmentPackageReferences | packlist_id | source_key |
| selectShipmentPackageReferences | con_id | contract_source_key |
| selectShipmentPackageReferences | packdate | shipment_time |
| selectShipmentContractReferences | contract_id | source_key |
| selectShipmentContractReferences | contract_code | contract_no |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
