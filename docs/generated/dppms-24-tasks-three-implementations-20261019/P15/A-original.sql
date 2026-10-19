-- P15 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [CONTRACT] OWNER_OR_CONTROLLED_INPUT; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json
-- readMode=LOCAL_OWNER; object=CONTRACT; 本地/受控Owner输入，实际Mapper查询及Java调用见本方式的查询和处理逻辑。;

-- [SHIPMENT_CONTRACT] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json
SELECT f.*, f.contract_id AS sync_evidence_key,
 CASE WHEN f.contract_id IS NULL OR TRIM(f.contract_id)=''
      THEN 'SOURCE_KEY_MISSING: fb_contract.contract_id' ELSE NULL END AS sync_source_issue
FROM fb_contract f
WHERE (:contractNos='' OR FIND_IN_SET(f.contract_code,:contractNos));

-- [PACKAGE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json
SELECT p.* FROM fb_shipment p;

-- [DEVICE_SN] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json
SELECT b.*,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END AS event_type,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END AS rma_marked
FROM fb_shipment_barcode b;

-- [DEVICE_RELATION] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P15.json
SELECT r.* FROM fb_shipment_barcode_relation r WHERE r.sn1<>r.sn2;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.selectContractsForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:21; kind=select
SELECT c.id FROM com_contract c JOIN tmp_dppms_contract_completion_scope b
 ON b.tenant_id=c.tenant_id AND b.target_id=c.id AND b.kind='CONTRACT'
 WHERE c.tenant_id=#{query.tenantId} AND c.deleted=0 AND c.master_source_system='DPPMS_ERP'
 ORDER BY c.id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.countValidInputs; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:27; kind=select
SELECT COUNT(*) FROM tmp_dppms_contract_completion_scope b WHERE b.tenant_id=#{query.tenantId} AND (
 (b.kind='CONTRACT' AND EXISTS(SELECT 1 FROM com_contract c WHERE c.tenant_id=b.tenant_id AND c.id=b.target_id AND c.deleted=0 AND c.master_source_system='DPPMS_ERP')) OR
 (b.kind IN ('SHIPMENT','CURRENT_SHIPMENT') AND EXISTS(SELECT 1 FROM com_shipment_contract_reference s WHERE s.tenant_id=b.tenant_id AND s.id=b.target_id AND s.deleted=0 AND s.source_system='DPPMS')) OR
 (b.kind='PACKAGE' AND EXISTS(SELECT 1 FROM com_shipment_package p WHERE p.tenant_id=b.tenant_id AND p.id=b.target_id AND p.deleted=0 AND p.source_system='DPPMS')) OR
 (b.kind='RECEIVABLE' AND EXISTS(SELECT 1 FROM com_contract_receivable r WHERE r.tenant_id=b.tenant_id AND r.id=b.target_id AND r.deleted=0 AND r.source_system='DPPMS')));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.selectAffectedContractNumbers; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:34; kind=select
SELECT DISTINCT COALESCE(c.contract_no,s.contract_no,parent.contract_no)
 FROM tmp_dppms_contract_completion_scope b
 LEFT JOIN com_contract c ON b.kind='CONTRACT' AND c.tenant_id=b.tenant_id AND c.id=b.target_id
  AND c.deleted=0 AND c.master_source_system='DPPMS_ERP'
 LEFT JOIN com_shipment_contract_reference s ON b.kind='CURRENT_SHIPMENT' AND s.tenant_id=b.tenant_id
  AND s.id=b.target_id AND s.deleted=0 AND s.source_system='DPPMS'
 LEFT JOIN com_shipment_package p ON b.kind='PACKAGE' AND p.tenant_id=b.tenant_id AND p.id=b.target_id
  AND p.deleted=0 AND p.source_system='DPPMS'
 LEFT JOIN com_shipment_contract_reference parent ON parent.tenant_id=p.tenant_id AND parent.source_system=p.source_system
  AND parent.source_record_key=p.shipment_contract_source_key AND parent.deleted=0
 WHERE b.tenant_id=#{query.tenantId} AND b.kind IN ('CONTRACT','CURRENT_SHIPMENT','PACKAGE');

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.selectCompanyChangeNumbers; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:47; kind=select
SELECT t.contract_no FROM com_contract t
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=t.tenant_id AND b.target_id=t.id AND b.kind='CONTRACT'
JOIN tmp_dppms_review_unique_company c ON c.tenant_id=t.tenant_id AND c.code=t.company_code
WHERE t.tenant_id=#{query.tenantId} AND t.deleted=0 AND t.master_source_system='DPPMS_ERP'
 AND (NOT(t.company_id <=> c.org_id) OR NOT(t.company_name <=> c.name));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.selectFieldChangeNumbers; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:52; kind=select
SELECT c.contract_no FROM com_contract c
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=c.tenant_id AND b.target_id=c.id AND b.kind='CONTRACT'
JOIN tmp_dppms_review_unique_contract u ON u.tenant_id=c.tenant_id AND u.contract_id=c.id
LEFT JOIN tmp_dppms_contract_shipment_fields s ON s.tenant_id=c.tenant_id AND s.contract_no=c.contract_no
LEFT JOIN tmp_dppms_contract_receivable_fields r ON r.tenant_id=c.tenant_id AND r.contract_no=c.contract_no
WHERE c.tenant_id=#{query.tenantId} AND c.deleted=b'0' AND c.master_source_system='DPPMS_ERP'
 AND (NOT(c.currency_name <=> COALESCE(r.currency_name,c.currency_name))
 OR NOT(c.currency_code <=> COALESCE(r.currency_name,c.currency_code))
 OR NOT(c.contract_amount <=> COALESCE(r.contract_amount,c.contract_amount))
 OR NOT(c.contract_create_time <=> COALESCE(r.contract_create_time,c.contract_create_time))
 OR NOT(c.project_name <=> COALESCE(s.project_name,r.project_name,c.project_name))
 OR NOT(c.project_code <=> COALESCE(r.project_code,c.project_code))
 OR NOT(c.market_code <=> COALESCE(s.market_code,r.market_code,c.market_code))
 OR NOT(c.market_name <=> COALESCE(s.market_name,r.market_name,c.market_name))
 OR NOT(c.department_code <=> COALESCE(s.department_code,r.department_code,c.department_code))
 OR NOT(c.department_name <=> COALESCE(r.department_name,c.department_name))
 OR NOT(c.industry_code <=> COALESCE(r.industry_code,c.industry_code))
 OR NOT(c.industry_name <=> COALESCE(r.industry_name,c.industry_name))
 OR NOT(c.marketing_representative_code <=> COALESCE(r.marketing_representative_code,c.marketing_representative_code))
 OR NOT(c.marketing_representative_name <=> COALESCE(r.marketing_representative_name,c.marketing_representative_name))
 OR NOT(c.secondary_representative_code <=> COALESCE(r.secondary_representative_code,c.secondary_representative_code))
 OR NOT(c.system_name <=> COALESCE(s.system_name,c.system_name)));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.countShipmentChanges; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:74; kind=select
SELECT COUNT(*) FROM com_shipment_contract_reference s
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=s.tenant_id AND b.target_id=s.id AND b.kind='CURRENT_SHIPMENT'
LEFT JOIN tmp_dppms_review_unique_contract c ON c.tenant_id=s.tenant_id AND c.contract_no=s.contract_no
LEFT JOIN tmp_dppms_review_unique_company co ON co.tenant_id=s.tenant_id AND co.code=c.company_code
LEFT JOIN tmp_dppms_review_unique_department d ON d.tenant_id=s.tenant_id AND d.code=s.department_code
WHERE s.tenant_id=#{query.tenantId} AND s.deleted=0 AND s.source_system='DPPMS'
 AND (NOT(s.contract_id <=> COALESCE(c.contract_id,s.contract_id))
 OR NOT(s.company_code <=> COALESCE(c.company_code,s.company_code))
 OR NOT(s.company_id <=> COALESCE(co.org_id,s.company_id))
 OR (co.org_id IS NOT NULL AND NOT(s.company_name <=> co.name))
 OR NOT(s.department_id <=> COALESCE(d.org_id,s.department_id))
 OR (d.org_id IS NOT NULL AND NOT(s.department_name <=> d.name)));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.countPackageChanges; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:86; kind=select
SELECT COUNT(*) FROM com_shipment_package p
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=p.tenant_id AND b.target_id=p.id AND b.kind='PACKAGE'
JOIN com_shipment_contract_reference c ON c.tenant_id=p.tenant_id AND c.source_system=p.source_system
 AND c.source_record_key=p.shipment_contract_source_key AND c.deleted=0
WHERE p.tenant_id=#{query.tenantId} AND p.deleted=0 AND p.source_system='DPPMS' AND NOT(p.shipment_contract_ref_id <=> c.id);

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.selectProjection; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:208; kind=select
/* CHOOSE  */ /* WHEN test=query.target.name() == 'CONTRACT' */ SELECT id,company_id AS companyId,company_code AS companyCode,company_name AS companyName,
 department_code AS departmentCode,department_name AS departmentName,version,
 currency_name AS currencyName,currency_code AS currencyCode,contract_amount AS contractAmount,contract_create_time AS contractCreateTime,
 project_name AS projectName,project_code AS projectCode,market_code AS marketCode,market_name AS marketName,
 industry_code AS industryCode,industry_name AS industryName,marketing_representative_code AS marketingRepresentativeCode,
 marketing_representative_name AS marketingRepresentativeName,secondary_representative_code AS secondaryRepresentativeCode,system_name AS systemName
 FROM com_contract WHERE tenant_id=#{query.tenantId} AND deleted=0 AND master_source_system='DPPMS_ERP' /* END WHEN */ 
  /* WHEN test=query.target.name() == 'SHIPMENT_CONTRACT' */ SELECT id,company_id AS companyId,company_code AS companyCode,company_name AS companyName,contract_id AS contractId,
 department_id AS departmentId,department_code AS departmentCode,department_name AS departmentName
 FROM com_shipment_contract_reference WHERE tenant_id=#{query.tenantId} AND deleted=0 AND source_system='DPPMS' /* END WHEN */ 
  /* OTHERWISE  */ SELECT id,shipment_contract_ref_id AS shipmentContractReferenceId FROM com_shipment_package
 WHERE tenant_id=#{query.tenantId} AND deleted=0 AND source_system='DPPMS' /* END OTHERWISE */ /* END CHOOSE */ 
  /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN
  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsLocalContractSourceMapper.selectPage; pms-module-commerce/src/main/resources/mapper/migration/DppmsLocalContractSourceMapper.xml:4; kind=select
SELECT MIN(x.company_code) AS company_code, MIN(x.contract_no) AS contract_no,
      MIN(NULLIF(x.customer_code,'')) AS customer_code, MIN(NULLIF(x.customer_name,'')) AS customer_name,
      CASE WHEN MAX(x.customer_code_invalid)=1 THEN 'ERP_CUSTOMER_CODE_FORMAT_INVALID'
        WHEN COUNT(DISTINCT CAST(NULLIF(x.customer_code,'') AS BINARY))>1
          OR COUNT(DISTINCT CAST(NULLIF(x.customer_name,'') AS BINARY))>1 THEN 'CONTRACT_CUSTOMER_CONFLICT'
        ELSE NULL END AS migration_issue
    FROM (
      SELECT o.company_code,o.contract_no,o.customer_name,
        CASE WHEN o.customer_code IS NULL OR o.customer_code='' THEN o.customer_code
          WHEN o.customer_code REGEXP '^[A-Za-z]'
            AND CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(o.customer_code,2)))<=5
          THEN CONCAT(LEFT(o.customer_code,1),LPAD(TRIM(LEADING '0' FROM SUBSTRING(o.customer_code,2)),5,'0'))
          ELSE NULL END AS customer_code,
        CASE WHEN o.customer_code IS NOT NULL AND o.customer_code<>''
          AND (NOT(o.customer_code REGEXP '^[A-Za-z]')
            OR CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(o.customer_code,2)))>5)
          THEN 1 ELSE 0 END AS customer_code_invalid
      FROM com_sales_order o
      WHERE o.tenant_id=#{query.tenantId} AND o.deleted=0
        AND o.source_system IN ('SAP','D365','SMS')
        AND o.company_code IS NOT NULL AND o.company_code<>''
        AND o.contract_no IS NOT NULL AND o.contract_no<>''
       /* IF test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ AND o.contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
       /* IF test=query.afterCompany != null */ AND (CAST(o.company_code AS BINARY)>CAST(#{query.afterCompany} AS BINARY)
          OR CAST(o.company_code AS BINARY)=CAST(#{query.afterCompany} AS BINARY)
            AND CAST(o.contract_no AS BINARY)>CAST(#{query.afterContract} AS BINARY)) /* END IF */ 
    ) x
    GROUP BY CAST(x.company_code AS BINARY),CAST(x.contract_no AS BINARY)
    ORDER BY CAST(x.company_code AS BINARY),CAST(x.contract_no AS BINARY) LIMIT 500;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.insertScope; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:5; kind=insert
INSERT INTO tmp_dppms_contract_completion_scope(tenant_id,kind,target_id) VALUES
  /* FOREACH collection=query.rows; item=row; separator=, */ (#{query.tenantId},#{row.kind},#{row.id}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.insertCompanies; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:11; kind=insert
INSERT INTO tmp_dppms_review_unique_company(tenant_id,code,org_id,name) VALUES
  /* FOREACH collection=query.rows; item=row; separator=, */ (#{query.tenantId},#{row.code},#{row.id},#{row.name}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.insertDepartments; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:14; kind=insert
INSERT INTO tmp_dppms_review_unique_department(tenant_id,code,org_id,name) VALUES
  /* FOREACH collection=query.rows; item=row; separator=, */ (#{query.tenantId},#{row.code},#{row.id},#{row.name}) /* repeat with separator=,; END FOREACH */;

-- A:GenericSyncJdbcStore.insertBatch:CONTRACT:com_contract; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_lifecycle_status`, `status`, `company_code`, `contract_no`, `customer_code`, `customer_name`, `master_source_record_key`, `master_source_system`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_lifecycle_status, :status, :company_code, :contract_no, :customer_code, :customer_name, :master_source_record_key, :master_source_system);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- A:GenericSyncJdbcStore.insertBatch:SHIPMENT_CONTRACT:com_shipment_contract_reference; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_contract_reference` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `contract_no`, `department_code`, `contract_type`, `customer_name`, `project_name`, `warranty_years`, `market_code`, `market_name`, `system_source_key`, `system_name`, `remark`, `source_record_key`, `source_system`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :contract_no, :department_code, :contract_type, :customer_name, :project_name, :warranty_years, :market_code, :market_name, :system_source_key, :system_name, :remark, :source_record_key, :source_system);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- A:GenericSyncJdbcStore.insertBatch:PACKAGE:com_shipment_package; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_package` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `package_no`, `shipment_time`, `warranty_start_time`, `warranty_end_time`, `receiver_name`, `express_no`, `carrier_name`, `source_system`, `source_record_key`, `carrier_code`, `shipment_contract_source_key`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :package_no, :shipment_time, :warranty_start_time, :warranty_end_time, :receiver_name, :express_no, :carrier_name, :source_system, :source_record_key, :carrier_code, :shipment_contract_source_key);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- A:GenericSyncJdbcStore.insertBatch:DEVICE_SN:ast_device_shipment; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_shipment` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `sync_status`, `device_sn`, `shipment_time`, `package_no`, `legacy_package_key`, `contract_no`, `event_type`, `warranty_start_date`, `warranty_months`, `rma_no`, `rma_related_sn`, `rma_marked`, `source_system`, `source_key`, `source_updated_at`, `source_payload`, `order_no`, `line_no`, `product_code`, `secondary_sn`, `secondary_item`, `internal_serial_no`, `is_rma`, `profit_center`, `sole_agent_suffix`, `sync_time`, `uuid`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :sync_status, :device_sn, :shipment_time, :package_no, :legacy_package_key, :contract_no, :event_type, :warranty_start_date, :warranty_months, :rma_no, :rma_related_sn, :rma_marked, :source_system, :source_key, :source_updated_at, :source_payload, :order_no, :line_no, :product_code, :secondary_sn, :secondary_item, :internal_serial_no, :is_rma, :profit_center, :sole_agent_suffix, :sync_time, :uuid);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- A:GenericSyncJdbcStore.insertBatch:DEVICE_RELATION:ast_device_relationship; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_relationship` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `source_device_sn`, `target_device_sn`, `relationship_type`, `contract_no`, `effective_from`, `source_system`, `source_key`, `source_version`, `source_item`, `target_item`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :source_device_sn, :target_device_sn, :relationship_type, :contract_no, :effective_from, :source_system, :source_key, :source_version, :source_item, :target_item);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.updateContractCompanies; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:91; kind=update
UPDATE com_contract t
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=t.tenant_id AND b.target_id=t.id AND b.kind='CONTRACT'
JOIN tmp_dppms_review_unique_company c ON c.tenant_id=t.tenant_id AND c.code=t.company_code
SET t.company_id=c.org_id,t.company_name=c.name,t.version=t.version+1
WHERE t.tenant_id=#{query.tenantId} AND t.deleted=0 AND t.master_source_system='DPPMS_ERP'
 AND (NOT(t.company_id <=> c.org_id) OR NOT(t.company_name <=> c.name));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.updateShipmentReferences; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:97; kind=update
UPDATE com_shipment_contract_reference s
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=s.tenant_id AND b.target_id=s.id AND b.kind='CURRENT_SHIPMENT'
LEFT JOIN tmp_dppms_review_unique_contract c ON c.tenant_id=s.tenant_id AND c.contract_no=s.contract_no
LEFT JOIN tmp_dppms_review_unique_company co ON co.tenant_id=s.tenant_id AND co.code=c.company_code
LEFT JOIN tmp_dppms_review_unique_department d ON d.tenant_id=s.tenant_id AND d.code=s.department_code
SET s.contract_id=COALESCE(c.contract_id,s.contract_id),
 s.company_code=COALESCE(c.company_code,s.company_code),s.company_id=COALESCE(co.org_id,s.company_id),
 s.company_name=CASE WHEN co.org_id IS NOT NULL THEN co.name ELSE s.company_name END,
 s.department_id=COALESCE(d.org_id,s.department_id),
 s.department_name=CASE WHEN d.org_id IS NOT NULL THEN d.name ELSE s.department_name END
WHERE s.tenant_id=#{query.tenantId} AND s.deleted=0 AND s.source_system='DPPMS'
 AND (NOT(s.contract_id <=> COALESCE(c.contract_id,s.contract_id))
 OR NOT(s.company_code <=> COALESCE(c.company_code,s.company_code))
 OR NOT(s.company_id <=> COALESCE(co.org_id,s.company_id))
 OR (co.org_id IS NOT NULL AND NOT(s.company_name <=> co.name))
 OR NOT(s.department_id <=> COALESCE(d.org_id,s.department_id))
 OR (d.org_id IS NOT NULL AND NOT(s.department_name <=> d.name)));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.updatePackageReferences; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:114; kind=update
UPDATE com_shipment_package p
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=p.tenant_id AND b.target_id=p.id AND b.kind='PACKAGE'
JOIN com_shipment_contract_reference c ON c.tenant_id=p.tenant_id AND c.source_system=p.source_system
 AND c.source_record_key=p.shipment_contract_source_key AND c.deleted=0
SET p.shipment_contract_ref_id=c.id
WHERE p.tenant_id=#{query.tenantId} AND p.deleted=0 AND p.source_system='DPPMS' AND NOT(p.shipment_contract_ref_id <=> c.id);

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.updateContractFields; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:170; kind=update
UPDATE com_contract c
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=c.tenant_id AND b.target_id=c.id AND b.kind='CONTRACT'
JOIN tmp_dppms_review_unique_contract u ON u.tenant_id=c.tenant_id AND u.contract_id=c.id
LEFT JOIN tmp_dppms_contract_shipment_fields s ON s.tenant_id=c.tenant_id AND s.contract_no=c.contract_no
LEFT JOIN tmp_dppms_contract_receivable_fields r ON r.tenant_id=c.tenant_id AND r.contract_no=c.contract_no
SET c.currency_name=COALESCE(r.currency_name,c.currency_name),
 c.currency_code=COALESCE(r.currency_name,c.currency_code),
 c.contract_amount=COALESCE(r.contract_amount,c.contract_amount),
 c.contract_create_time=COALESCE(r.contract_create_time,c.contract_create_time),
 c.project_name=COALESCE(s.project_name,r.project_name,c.project_name),
 c.project_code=COALESCE(r.project_code,c.project_code),
 c.market_code=COALESCE(s.market_code,r.market_code,c.market_code),
 c.market_name=COALESCE(s.market_name,r.market_name,c.market_name),
 c.department_code=COALESCE(s.department_code,r.department_code,c.department_code),
 c.department_name=COALESCE(r.department_name,c.department_name),
 c.industry_code=COALESCE(r.industry_code,c.industry_code),
 c.industry_name=COALESCE(r.industry_name,c.industry_name),
 c.marketing_representative_code=COALESCE(r.marketing_representative_code,c.marketing_representative_code),
 c.marketing_representative_name=COALESCE(r.marketing_representative_name,c.marketing_representative_name),
 c.secondary_representative_code=COALESCE(r.secondary_representative_code,c.secondary_representative_code),
 c.system_name=COALESCE(s.system_name,c.system_name),c.version=c.version+1
WHERE c.tenant_id=#{query.tenantId} AND c.deleted=b'0' AND c.master_source_system='DPPMS_ERP'
 AND (NOT(c.currency_name <=> COALESCE(r.currency_name,c.currency_name))
 OR NOT(c.currency_code <=> COALESCE(r.currency_name,c.currency_code))
 OR NOT(c.contract_amount <=> COALESCE(r.contract_amount,c.contract_amount))
 OR NOT(c.contract_create_time <=> COALESCE(r.contract_create_time,c.contract_create_time))
 OR NOT(c.project_name <=> COALESCE(s.project_name,r.project_name,c.project_name))
 OR NOT(c.project_code <=> COALESCE(r.project_code,c.project_code))
 OR NOT(c.market_code <=> COALESCE(s.market_code,r.market_code,c.market_code))
 OR NOT(c.market_name <=> COALESCE(s.market_name,r.market_name,c.market_name))
 OR NOT(c.department_code <=> COALESCE(s.department_code,r.department_code,c.department_code))
 OR NOT(c.department_name <=> COALESCE(r.department_name,c.department_name))
 OR NOT(c.industry_code <=> COALESCE(r.industry_code,c.industry_code))
 OR NOT(c.industry_name <=> COALESCE(r.industry_name,c.industry_name))
 OR NOT(c.marketing_representative_code <=> COALESCE(r.marketing_representative_code,c.marketing_representative_code))
 OR NOT(c.marketing_representative_name <=> COALESCE(r.marketing_representative_name,c.marketing_representative_name))
 OR NOT(c.secondary_representative_code <=> COALESCE(r.secondary_representative_code,c.secondary_representative_code))
 OR NOT(c.system_name <=> COALESCE(s.system_name,c.system_name)));

-- A:GenericSyncJdbcStore.updateBatch:CONTRACT:com_contract; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_contract` SET `customer_code` = CASE `id` WHEN :id THEN :customer_code ELSE `customer_code` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- A:GenericSyncJdbcStore.updateBatch:SHIPMENT_CONTRACT:com_shipment_contract_reference; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_shipment_contract_reference` SET `contract_no` = CASE `id` WHEN :id THEN :contract_no ELSE `contract_no` END, `department_code` = CASE `id` WHEN :id THEN :department_code ELSE `department_code` END, `contract_type` = CASE `id` WHEN :id THEN :contract_type ELSE `contract_type` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, `project_name` = CASE `id` WHEN :id THEN :project_name ELSE `project_name` END, `warranty_years` = CASE `id` WHEN :id THEN :warranty_years ELSE `warranty_years` END, `market_code` = CASE `id` WHEN :id THEN :market_code ELSE `market_code` END, `market_name` = CASE `id` WHEN :id THEN :market_name ELSE `market_name` END, `system_source_key` = CASE `id` WHEN :id THEN :system_source_key ELSE `system_source_key` END, `system_name` = CASE `id` WHEN :id THEN :system_name ELSE `system_name` END, `remark` = CASE `id` WHEN :id THEN :remark ELSE `remark` END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- A:GenericSyncJdbcStore.updateBatch:PACKAGE:com_shipment_package; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_shipment_package` SET `shipment_time` = CASE `id` WHEN :id THEN :shipment_time ELSE `shipment_time` END, `warranty_start_time` = CASE `id` WHEN :id THEN :warranty_start_time ELSE `warranty_start_time` END, `warranty_end_time` = CASE `id` WHEN :id THEN :warranty_end_time ELSE `warranty_end_time` END, `receiver_name` = CASE `id` WHEN :id THEN :receiver_name ELSE `receiver_name` END, `express_no` = CASE `id` WHEN :id THEN :express_no ELSE `express_no` END, `carrier_name` = CASE `id` WHEN :id THEN :carrier_name ELSE `carrier_name` END, `carrier_code` = CASE `id` WHEN :id THEN :carrier_code ELSE `carrier_code` END, `shipment_contract_source_key` = CASE `id` WHEN :id THEN :shipment_contract_source_key ELSE `shipment_contract_source_key` END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createScope; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:4; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_contract_completion_scope (tenant_id BIGINT NOT NULL,kind VARCHAR(20) NOT NULL,target_id BIGINT NOT NULL,PRIMARY KEY(tenant_id,kind,target_id));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createCompanies; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:9; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_review_unique_company (tenant_id BIGINT NOT NULL,code VARCHAR(128) NOT NULL,org_id BIGINT NOT NULL,name VARCHAR(255),PRIMARY KEY(tenant_id,code));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createDepartments; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:10; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_review_unique_department (tenant_id BIGINT NOT NULL,code VARCHAR(128) NOT NULL,org_id BIGINT NOT NULL,name VARCHAR(255),PRIMARY KEY(tenant_id,code));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createUniqueContracts; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:17; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_review_unique_contract AS
 SELECT tenant_id,contract_no,MIN(id) AS contract_id,MIN(company_code) AS company_code FROM com_contract
 WHERE tenant_id=#{query.tenantId} AND deleted=0 GROUP BY tenant_id,contract_no HAVING COUNT(*)=1;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createShipmentFields; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:120; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_contract_shipment_fields AS
SELECT s.tenant_id,s.contract_no,
 SUBSTRING(MAX(CASE WHEN s.`project_name` IS NOT NULL AND CAST(s.`project_name` AS CHAR)<>''
  THEN CONCAT(RPAD(s.source_record_key,64,' '),CAST(s.`project_name` AS CHAR)) END),65) AS `project_name`,
 SUBSTRING(MAX(CASE WHEN s.`market_code` IS NOT NULL AND CAST(s.`market_code` AS CHAR)<>''
  THEN CONCAT(RPAD(s.source_record_key,64,' '),CAST(s.`market_code` AS CHAR)) END),65) AS `market_code`,
 SUBSTRING(MAX(CASE WHEN s.`market_name` IS NOT NULL AND CAST(s.`market_name` AS CHAR)<>''
  THEN CONCAT(RPAD(s.source_record_key,64,' '),CAST(s.`market_name` AS CHAR)) END),65) AS `market_name`,
 SUBSTRING(MAX(CASE WHEN s.`department_code` IS NOT NULL AND CAST(s.`department_code` AS CHAR)<>''
  THEN CONCAT(RPAD(s.source_record_key,64,' '),CAST(s.`department_code` AS CHAR)) END),65) AS `department_code`,
 SUBSTRING(MAX(CASE WHEN s.`system_name` IS NOT NULL AND CAST(s.`system_name` AS CHAR)<>''
  THEN CONCAT(RPAD(s.source_record_key,64,' '),CAST(s.`system_name` AS CHAR)) END),65) AS `system_name`
FROM com_shipment_contract_reference s
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=s.tenant_id AND b.target_id=s.id AND b.kind='SHIPMENT'
WHERE s.tenant_id=#{query.tenantId} AND s.deleted=b'0' AND s.source_system='DPPMS'
GROUP BY s.tenant_id,s.contract_no;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.createReceivableFields; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:136; kind=runtime_temp_ddl
CREATE TEMPORARY TABLE tmp_dppms_contract_receivable_fields AS
SELECT s.tenant_id,s.contract_no,
 SUBSTRING(MAX(CASE WHEN s.`currency_name` IS NOT NULL AND CAST(s.`currency_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`currency_name` AS CHAR)) END),40) AS `currency_name`,
 SUBSTRING(MAX(CASE WHEN s.`contract_amount` IS NOT NULL AND CAST(s.`contract_amount` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`contract_amount` AS CHAR)) END),40) AS `contract_amount`,
 SUBSTRING(MAX(CASE WHEN s.`contract_create_time` IS NOT NULL AND CAST(s.`contract_create_time` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`contract_create_time` AS CHAR)) END),40) AS `contract_create_time`,
 SUBSTRING(MAX(CASE WHEN s.`project_name` IS NOT NULL AND CAST(s.`project_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`project_name` AS CHAR)) END),40) AS `project_name`,
 SUBSTRING(MAX(CASE WHEN s.`project_code` IS NOT NULL AND CAST(s.`project_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`project_code` AS CHAR)) END),40) AS `project_code`,
 SUBSTRING(MAX(CASE WHEN s.`market_code` IS NOT NULL AND CAST(s.`market_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`market_code` AS CHAR)) END),40) AS `market_code`,
 SUBSTRING(MAX(CASE WHEN s.`market_name` IS NOT NULL AND CAST(s.`market_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`market_name` AS CHAR)) END),40) AS `market_name`,
 SUBSTRING(MAX(CASE WHEN s.`department_code` IS NOT NULL AND CAST(s.`department_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`department_code` AS CHAR)) END),40) AS `department_code`,
 SUBSTRING(MAX(CASE WHEN s.`department_name` IS NOT NULL AND CAST(s.`department_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`department_name` AS CHAR)) END),40) AS `department_name`,
 SUBSTRING(MAX(CASE WHEN s.`industry_code` IS NOT NULL AND CAST(s.`industry_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`industry_code` AS CHAR)) END),40) AS `industry_code`,
 SUBSTRING(MAX(CASE WHEN s.`industry_name` IS NOT NULL AND CAST(s.`industry_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`industry_name` AS CHAR)) END),40) AS `industry_name`,
 SUBSTRING(MAX(CASE WHEN s.`marketing_representative_code` IS NOT NULL AND CAST(s.`marketing_representative_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`marketing_representative_code` AS CHAR)) END),40) AS `marketing_representative_code`,
 SUBSTRING(MAX(CASE WHEN s.`marketing_representative_name` IS NOT NULL AND CAST(s.`marketing_representative_name` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`marketing_representative_name` AS CHAR)) END),40) AS `marketing_representative_name`,
 SUBSTRING(MAX(CASE WHEN s.`secondary_representative_code` IS NOT NULL AND CAST(s.`secondary_representative_code` AS CHAR)<>''
  THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(s.source_sync_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(s.source_record_key,20,'0')),CAST(s.`secondary_representative_code` AS CHAR)) END),40) AS `secondary_representative_code`
FROM com_contract_receivable s
JOIN tmp_dppms_contract_completion_scope b ON b.tenant_id=s.tenant_id AND b.target_id=s.id AND b.kind='RECEIVABLE'
WHERE s.tenant_id=#{query.tenantId} AND s.deleted=b'0' AND s.source_system='DPPMS'
GROUP BY s.tenant_id,s.contract_no;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropReceivableFields; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:233; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_contract_receivable_fields;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropShipmentFields; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:234; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_contract_shipment_fields;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropUniqueContracts; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:235; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_review_unique_contract;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropDepartments; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:236; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_review_unique_department;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropCompanies; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:237; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_review_unique_company;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsContractCompletionMapper.dropScope; pms-module-commerce/src/main/resources/mapper/migration/DppmsContractCompletionMapper.xml:238; kind=runtime_temp_ddl
DROP TEMPORARY TABLE IF EXISTS tmp_dppms_contract_completion_scope;
