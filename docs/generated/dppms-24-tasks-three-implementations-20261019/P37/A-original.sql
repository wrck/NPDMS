-- P37 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [LICENSE_HISTORY] OWNER_OR_CONTROLLED_INPUT; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P37.json
-- readMode=SECURE_LICENSE; object=LICENSE_HISTORY; 本地/受控Owner输入，实际Mapper查询及Java调用见本方式的查询和处理逻辑。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectDeviceForUpdate; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:5; kind=select
SELECT id,
               sn,
               project_id,
               project_assignment_version,
               customer_id,
               customer_assignment_version,
               tenant_id
        FROM ast_device
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.deviceId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectCurrentProject; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:21; kind=select
SELECT id,
               device_sn,
               project_id,
               relationship_type,
               effective_from,
               effective_to,
               assignment_version,
               reason,
               operation_id,
               source_system,
               source_key,
               source_version,
               version,
               tenant_id
        FROM ast_device_project_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = (
              SELECT sn
              FROM ast_device
              WHERE tenant_id = #{query.tenantId}
                AND id = #{query.deviceId}
                AND deleted = b'0'
          )
          AND relationship_type = 'DIRECT'
          AND effective_to IS NULL
          AND deleted = b'0'
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectCurrentCustomer; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:110; kind=select
SELECT id,
               device_sn,
               customer_id,
               relationship_type,
               effective_from,
               effective_to,
               assignment_version,
               reason,
               operation_id,
               source_system,
               source_key,
               source_version,
               version,
               tenant_id
        FROM ast_device_customer_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = (
              SELECT sn
              FROM ast_device
              WHERE tenant_id = #{query.tenantId}
                AND id = #{query.deviceId}
                AND deleted = b'0'
          )
          AND relationship_type = 'DIRECT'
          AND effective_to IS NULL
          AND deleted = b'0'
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectProjectHistoryList; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:199; kind=select
SELECT id,
               device_sn,
               project_id,
               relationship_type,
               effective_from,
               effective_to,
               assignment_version,
               reason,
               operation_id,
               source_system,
               source_key,
               source_version,
               version,
               tenant_id
        FROM ast_device_project_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND relationship_type = 'DIRECT'
          AND deleted = b'0'
        ORDER BY effective_from DESC,
                 id DESC
        LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectProjectHistoryCount; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:225; kind=select
SELECT COUNT(*)
        FROM ast_device_project_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND relationship_type = 'DIRECT'
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectCustomerRelationshipList; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:234; kind=select
SELECT id,
               device_sn,
               customer_id,
               relationship_type,
               effective_from,
               effective_to,
               assignment_version,
               reason,
               operation_id,
               source_system,
               source_key,
               source_version,
               version,
               tenant_id
        FROM ast_device_customer_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND deleted = b'0'
        ORDER BY effective_from DESC,
                 id DESC
        LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectCustomerRelationshipCount; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:259; kind=select
SELECT COUNT(*)
        FROM ast_device_customer_relationship
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectProjectionDeviceForUpdate; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:267; kind=select
SELECT id
        FROM ast_device
        WHERE tenant_id = #{tenantId}
          AND sn = #{deviceSn}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.existsAncestorProjectionOperation; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:276; kind=select
SELECT EXISTS(
            SELECT 1
            FROM ast_device_ancestor_projection_operation
            WHERE tenant_id = #{tenantId}
              AND event_id = #{eventId}
              AND deleted = b'0'
        );

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.selectLatestAncestorProjectionOperation; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:286; kind=select
SELECT id,
               event_id,
               operation_id,
               device_sn,
               project_id,
               tree_version,
               assignment_version,
               tenant_id
        FROM ast_device_ancestor_projection_operation
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND deleted = b'0'
        ORDER BY assignment_version DESC,
                 tree_version DESC,
                 id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper.selectIdentityForUpdate; pms-module-asset/src/main/resources/mapper/device/DeviceMigrationIdentityMapper.xml:4; kind=select
SELECT * FROM ast_device WHERE tenant_id=#{query.tenantId} AND sn=#{query.deviceSn} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.selectRecordForUpdate; pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.selectVisiblePage; pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml:13; kind=select
SELECT id,tenant_id,device_id,device_sn,contract_id,contract_no,contract_type,product_code,product_model,
            license_status,creator,create_time,updater,update_time,deleted
        FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND deleted=0
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */
        ORDER BY id LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.countVisible; pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml:19; kind=select
SELECT COUNT(*) FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND deleted=0
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper.selectLatest; pms-module-asset/src/main/resources/mapper/shipment/DeviceShipmentMapper.xml:5; kind=select
SELECT id,
               device_sn,
               legacy_package_key,
               rma_related_sn,
               rma_marked,
               order_no,
               line_no,
               order_line_id,
               source_payload,
               product_code,
               secondary_sn,
               secondary_item,
               internal_serial_no,
               is_rma,
               profit_center,
               sole_agent_suffix,
               sync_time,
               uuid,
               shipment_time,
               package_no,
               contract_no,
               event_type,
               warranty_start_date,
               warranty_months,
               rma_no,
               related_device_sn,
               source_system,
               source_key,
               source_version,
               source_updated_at,
               synced_at,
               sync_status,
               version,
               creator,
               create_time,
               updater,
               update_time,
               deleted,
               tenant_id
        FROM ast_device_shipment
        WHERE tenant_id = #{query.tenantId}
          AND device_sn = #{query.deviceSn}
          AND deleted = b'0'
        ORDER BY shipment_time DESC, id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionMapper.selectIdentity; pms-module-asset/src/main/resources/mapper/version/DeviceFactoryVersionMapper.xml:4; kind=select
SELECT * FROM ast_device_factory_version WHERE tenant_id=#{query.tenantId} AND device_sn=#{query.deviceSn} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper.selectByIdForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceFactoryVersionRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_factory_version_record WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper.selectGlobalRecordForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceFactoryVersionRecordMapper.xml:8; kind=select
SELECT * FROM ast_device_factory_version_record
        WHERE tenant_id=#{query.tenantId} AND project_id IS NULL AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareChangeBatchMapper.selectLegacyForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareChangeBatchMapper.xml:4; kind=select
SELECT * FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.recordId} AND deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareChangeBatchMapper.selectRecordForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareChangeBatchMapper.xml:17; kind=select
SELECT * FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND id=#{query.recordId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper.selectForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareVersionRecordMapper.xml:16; kind=select
SELECT *
        FROM ast_device_software_version_record
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.recordId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper.selectCurrentPredecessor; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareVersionRecordMapper.xml:25; kind=select
SELECT * FROM ast_device_software_version_record
    WHERE tenant_id=#{query.tenantId} AND device_id=#{query.deviceId} AND deleted=0
      AND data_source=#{query.dataSource} AND recorded_at <=> #{query.recordedAt}
     /* IF test=query.requestKey != null */ AND request_key=#{query.requestKey} /* END IF */ 
    ORDER BY create_time DESC, id DESC LIMIT 1;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyChangeRecordMapper.selectSourceForUpdate; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyChangeRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_change_record WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_key=#{query.sourceKey}
          AND source_checksum=#{query.sourceChecksum} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyChangeRecordMapper.selectProjectPage; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyChangeRecordMapper.xml:9; kind=select
SELECT f.*
        FROM ast_device_warranty_change_record f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='CHANGE' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0
        ORDER BY f.id LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyChangeRecordMapper.countProject; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyChangeRecordMapper.xml:17; kind=select
SELECT COUNT(*)
        FROM ast_device_warranty_change_record f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='CHANGE' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyFactProjectMapper.selectProjectRelationForUpdate; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyFactProjectMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_fact_project WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND fact_type=#{query.factType} AND fact_id=#{query.factId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper.selectSourceIdentity; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_record
        WHERE tenant_id=#{query.tenantId} AND source_system=#{query.sourceSystem} AND source_key=#{query.sourceKey}
     FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRegistrationMapper.selectSourceForUpdate; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRegistrationMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_registration WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_key=#{query.sourceKey}
          AND source_checksum=#{query.sourceChecksum} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRegistrationMapper.selectProjectPage; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRegistrationMapper.xml:9; kind=select
SELECT f.*
        FROM ast_device_warranty_registration f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='REGISTRATION' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0
        ORDER BY f.id LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRegistrationMapper.countProject; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRegistrationMapper.xml:17; kind=select
SELECT COUNT(*)
        FROM ast_device_warranty_registration f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='REGISTRATION' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyServiceRecordMapper.selectSourceForUpdate; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyServiceRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_service_record WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_key=#{query.sourceKey}
          AND source_checksum=#{query.sourceChecksum} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyServiceRecordMapper.selectProjectPage; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyServiceRecordMapper.xml:9; kind=select
SELECT f.*
        FROM ast_device_warranty_service_record f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='SERVICE' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0
        ORDER BY f.id LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyServiceRecordMapper.countProject; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyServiceRecordMapper.xml:17; kind=select
SELECT COUNT(*)
        FROM ast_device_warranty_service_record f
        JOIN ast_device_warranty_fact_project r ON r.tenant_id=f.tenant_id AND r.fact_id=f.id
          AND r.fact_type='SERVICE' AND r.deleted=0
        WHERE f.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND f.deleted=0;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectExecutionForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:4; kind=select
SELECT * FROM com_crm_execution_order WHERE tenant_id=#{query.tenantId} AND id=#{query.id}
          AND primary_project_id=#{query.projectId} AND deleted=0
          AND CAST(source_system AS BINARY)=CAST(#{query.sourceSystem} AS BINARY)
          AND CAST(execution_no AS BINARY)=CAST(#{query.executionNo} AS BINARY) FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectOrderForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:35; kind=select
SELECT * FROM com_sales_order WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectLineForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:38; kind=select
SELECT * FROM com_sales_order_line WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectPackageForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:48; kind=select
SELECT DISTINCT p.* FROM com_shipment_package p JOIN com_shipment_contract_reference sr ON sr.id=p.shipment_contract_ref_id AND sr.tenant_id=p.tenant_id AND sr.deleted=0
        JOIN com_project_contract_relation cr ON cr.contract_id=sr.contract_id AND cr.tenant_id=p.tenant_id AND cr.deleted=0
        WHERE p.tenant_id=#{query.tenantId} AND cr.project_id=#{query.projectId} AND p.deleted=0
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP) AND p.source_system='DPPMS' AND p.package_no=#{query.packageNo}
          AND EXISTS (SELECT 1 FROM com_contract c WHERE c.id=sr.contract_id
              AND c.tenant_id=p.tenant_id AND c.deleted=0 AND c.contract_no=#{query.contractNo}) FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectPackageContractIdentity; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:53; kind=select
SELECT r.source_system AS sourceSystem,r.source_record_key AS sourceRecordKey
        FROM com_shipment_package p JOIN com_shipment_contract_reference r
          ON r.id=p.shipment_contract_ref_id AND r.tenant_id=p.tenant_id AND r.deleted=0
        WHERE p.tenant_id=#{query.tenantId} AND p.id=#{query.id} AND p.deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectPlans; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:72; kind=select
SELECT p.* FROM com_contract_collection_plan p
        WHERE p.tenant_id=#{query.tenantId} AND p.deleted=0
          AND EXISTS (SELECT 1 FROM com_contract c
              JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id
              WHERE c.contract_no=p.contract_no AND CAST(c.contract_no AS BINARY)=CAST(p.contract_no AS BINARY)
                AND c.tenant_id=p.tenant_id AND c.deleted=0
                AND (SELECT COUNT(*) FROM com_contract same_no WHERE same_no.tenant_id=p.tenant_id AND same_no.deleted=0
                     AND same_no.contract_no=p.contract_no AND CAST(same_no.contract_no AS BINARY)=CAST(p.contract_no AS BINARY))=1
                AND cr.project_id=#{query.projectId} AND cr.deleted=0 AND cr.status='ACTIVE'
                AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
                AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP))
        ORDER BY p.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.countPlans; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:76; kind=select
SELECT COUNT(*) FROM com_contract_collection_plan p
        WHERE p.tenant_id=#{query.tenantId} AND p.deleted=0
          AND EXISTS (SELECT 1 FROM com_contract c
              JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id
              WHERE c.contract_no=p.contract_no AND CAST(c.contract_no AS BINARY)=CAST(p.contract_no AS BINARY)
                AND c.tenant_id=p.tenant_id AND c.deleted=0
                AND (SELECT COUNT(*) FROM com_contract same_no WHERE same_no.tenant_id=p.tenant_id AND same_no.deleted=0
                     AND same_no.contract_no=p.contract_no AND CAST(same_no.contract_no AS BINARY)=CAST(p.contract_no AS BINARY))=1
                AND cr.project_id=#{query.projectId} AND cr.deleted=0 AND cr.status='ACTIVE'
                AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
                AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectPackages; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:79; kind=select
SELECT DISTINCT p.* FROM com_shipment_package p JOIN com_shipment_contract_reference sr ON sr.id=p.shipment_contract_ref_id AND sr.tenant_id=p.tenant_id AND sr.deleted=0
        JOIN com_project_contract_relation cr ON cr.contract_id=sr.contract_id AND cr.tenant_id=p.tenant_id AND cr.deleted=0
        WHERE p.tenant_id=#{query.tenantId} AND cr.project_id=#{query.projectId} AND p.deleted=0
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP) ORDER BY p.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.countPackages; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:82; kind=select
SELECT COUNT(DISTINCT p.id) FROM com_shipment_package p JOIN com_shipment_contract_reference sr ON sr.id=p.shipment_contract_ref_id AND sr.tenant_id=p.tenant_id AND sr.deleted=0
        JOIN com_project_contract_relation cr ON cr.contract_id=sr.contract_id AND cr.tenant_id=p.tenant_id AND cr.deleted=0
        WHERE p.tenant_id=#{query.tenantId} AND cr.project_id=#{query.projectId} AND p.deleted=0
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP);

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectExecutions; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:99; kind=select
SELECT * FROM com_crm_execution_order WHERE tenant_id=#{query.tenantId}
          AND primary_project_id=#{query.projectId} AND deleted=0
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.countExecutions; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:104; kind=select
SELECT COUNT(*) FROM com_crm_execution_order WHERE tenant_id=#{query.tenantId}
          AND primary_project_id=#{query.projectId} AND deleted=0;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectOrders; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:108; kind=select
SELECT o.* FROM com_sales_order o WHERE o.tenant_id=#{query.tenantId} AND o.deleted=0 AND EXISTS (
        SELECT 1 FROM com_contract c JOIN com_project_contract_relation pcr ON pcr.contract_id=c.id AND pcr.tenant_id=c.tenant_id
        WHERE c.tenant_id=o.tenant_id AND c.deleted=0 AND pcr.deleted=0 AND pcr.project_id=#{query.projectId}
          AND pcr.status='ACTIVE' AND (pcr.effective_from IS NULL OR pcr.effective_from <= CURRENT_TIMESTAMP)
          AND (pcr.effective_to IS NULL OR pcr.effective_to > CURRENT_TIMESTAMP)
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
      )
      ORDER BY o.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.countOrders; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:112; kind=select
SELECT COUNT(*) FROM com_sales_order o WHERE o.tenant_id=#{query.tenantId} AND o.deleted=0 AND EXISTS (
        SELECT 1 FROM com_contract c JOIN com_project_contract_relation pcr ON pcr.contract_id=c.id AND pcr.tenant_id=c.tenant_id
        WHERE c.tenant_id=o.tenant_id AND c.deleted=0 AND pcr.deleted=0 AND pcr.project_id=#{query.projectId}
          AND pcr.status='ACTIVE' AND (pcr.effective_from IS NULL OR pcr.effective_from <= CURRENT_TIMESTAMP)
          AND (pcr.effective_to IS NULL OR pcr.effective_to > CURRENT_TIMESTAMP)
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
      );

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.selectLines; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:115; kind=select
SELECT l.* FROM com_sales_order_line l JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id
      WHERE o.tenant_id=#{query.tenantId} AND o.deleted=0 AND EXISTS (
        SELECT 1 FROM com_contract c JOIN com_project_contract_relation pcr ON pcr.contract_id=c.id AND pcr.tenant_id=c.tenant_id
        WHERE c.tenant_id=o.tenant_id AND c.deleted=0 AND pcr.deleted=0 AND pcr.project_id=#{query.projectId}
          AND pcr.status='ACTIVE' AND (pcr.effective_from IS NULL OR pcr.effective_from <= CURRENT_TIMESTAMP)
          AND (pcr.effective_to IS NULL OR pcr.effective_to > CURRENT_TIMESTAMP)
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
      ) AND l.deleted=0
      ORDER BY l.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceDetailMapper.countLines; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceDetailMapper.xml:120; kind=select
SELECT COUNT(*) FROM com_sales_order_line l JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id
      WHERE o.tenant_id=#{query.tenantId} AND o.deleted=0 AND EXISTS (
        SELECT 1 FROM com_contract c JOIN com_project_contract_relation pcr ON pcr.contract_id=c.id AND pcr.tenant_id=c.tenant_id
        WHERE c.tenant_id=o.tenant_id AND c.deleted=0 AND pcr.deleted=0 AND pcr.project_id=#{query.projectId}
          AND pcr.status='ACTIVE' AND (pcr.effective_from IS NULL OR pcr.effective_from <= CURRENT_TIMESTAMP)
          AND (pcr.effective_to IS NULL OR pcr.effective_to > CURRENT_TIMESTAMP)
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
      ) AND l.deleted=0;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectShipmentLineForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:4; kind=select
SELECT * FROM com_sales_order_line WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectContractIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:7; kind=select
SELECT DISTINCT c.id FROM com_contract c
        JOIN com_project_contract_relation r ON r.contract_id=c.id AND r.tenant_id=c.tenant_id
        WHERE c.tenant_id=#{query.tenantId} AND r.project_id=#{query.projectId} AND c.contract_no=#{query.contractNo}
          AND c.deleted=b'0' AND r.deleted=b'0' AND r.status='ACTIVE'
          AND (r.effective_from IS NULL OR r.effective_from <= #{query.at})
          AND (r.effective_to IS NULL OR r.effective_to > #{query.at});

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectOrderIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:15; kind=select
SELECT id FROM com_sales_order WHERE tenant_id=#{query.tenantId}
          AND (source_system='ERP' OR (source_system IN ('SAP','D365','SMS')
            AND source_record_key LIKE CONCAT('DPPMS|',source_system,'|%')))
          AND source_record_key=#{query.sourceKey} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectLineIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:21; kind=select
SELECT id FROM com_sales_order_line WHERE tenant_id=#{query.tenantId}
          AND (source_system='ERP' OR (source_system IN ('SAP','D365','SMS')
            AND source_record_key LIKE CONCAT('DPPMS|',source_system,'|%')))
          AND source_record_key=#{query.sourceKey} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectExecutionIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:27; kind=select
SELECT id FROM com_crm_execution_order WHERE tenant_id=#{query.tenantId} AND source_system=#{query.sourceSystem}
          AND execution_no=#{query.executionNo} AND primary_project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectPackageIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:31; kind=select
SELECT id FROM com_shipment_package WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND package_no=#{query.packageNo} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectAssociatedOrderIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:35; kind=select
SELECT DISTINCT o.id FROM com_sales_order o
        JOIN com_contract c ON c.tenant_id=o.tenant_id AND c.deleted=0 AND c.contract_no=#{query.contractNo}
        JOIN com_project_contract_relation pcr ON pcr.contract_id=c.id AND pcr.tenant_id=o.tenant_id AND pcr.deleted=0
        WHERE o.tenant_id=#{query.tenantId} AND pcr.project_id=#{query.projectId}
          AND o.deleted=0 AND o.status='ENABLED' AND (o.source_system='ERP' OR o.source_system=#{query.source})
          AND o.order_no=#{query.number} AND o.order_type=#{query.type}
          AND pcr.status='ACTIVE'
          AND (pcr.effective_from IS NULL OR pcr.effective_from <= #{query.at})
          AND (pcr.effective_to IS NULL OR pcr.effective_to > #{query.at})
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r
                WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id AND r.contract_id=c.id AND r.deleted=0
                  AND r.effective_from <= #{query.at} AND (r.effective_to IS NULL OR r.effective_to > #{query.at}))
          )
          AND CAST(o.company_code AS BINARY)=CAST(#{query.companyCode} AS BINARY)
          
          AND (o.source_record_key=CONCAT('DPPMS|',#{query.source},'|',#{query.companyCode},'|',#{query.type},'|',#{query.number})
            OR o.source_record_key=CONCAT('DPPMS|',#{query.source},'|',#{query.rawCompanyCode},'|',#{query.type},'|',#{query.number}));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.selectAssociatedLineIds; pms-module-commerce/src/main/resources/mapper/migration/DppmsCommerceEntityMapper.xml:56; kind=select
SELECT id FROM com_sales_order_line WHERE tenant_id=#{query.tenantId} AND order_id=#{query.orderId}
          AND line_no=#{query.lineNo} AND deleted=0
          AND (source_system='ERP' OR (source_system IN ('SAP','D365','SMS')
            AND source_record_key LIKE CONCAT('DPPMS|',source_system,'|%')));

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectBySourceForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:4; kind=select
SELECT * FROM com_sales_order_line
        WHERE tenant_id = #{query.tenantId}
          AND source_system = #{query.sourceSystem}
          AND source_record_key = #{query.sourceRecordKey}
          AND deleted = 0
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectByCompanyScope; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:14; kind=select
SELECT l.* FROM com_sales_order_line l
        WHERE l.tenant_id = #{query.tenantId} AND l.deleted = 0
          AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(l.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr ON pcr.tenant_id = ocr.tenant_id
                     AND pcr.contract_id = ocr.contract_id AND pcr.deleted = 0
                     AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = l.tenant_id AND ocr.order_id = l.order_id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          )
         /* IF test=query.orderId != null */ AND l.order_id = #{query.orderId} /* END IF */ 
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(l.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.orderType != null and query.orderType != '' */ AND l.order_type = #{query.orderType} /* END IF */ 
         /* IF test=query.orderNo != null and query.orderNo != '' */ AND l.order_no = #{query.orderNo} /* END IF */ 
         /* IF test=query.lineNo != null and query.lineNo != '' */ AND l.line_no = #{query.lineNo} /* END IF */ 
         /* IF test=query.productCode != null and query.productCode != '' */ AND l.product_code = #{query.productCode} /* END IF */ 
         /* IF test=query.quantityStatus != null and query.quantityStatus != '' */ AND l.quantity_status = #{query.quantityStatus} /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND l.status = #{query.status} /* END IF */ 
        ORDER BY l.order_id, l.line_no, l.id
        LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectByIds; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:48; kind=select
SELECT * FROM com_sales_order_line
        WHERE tenant_id = #{query.tenantId}
          AND deleted = 0
          AND id IN
           /* FOREACH collection=query.orderLineIds; item=orderLineId; open=(; separator=,; close=) */ (#{orderLineId}) /* repeat with separator=,; END FOREACH */ 
        ORDER BY id ASC;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectCountByCompanyScope; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:60; kind=select
SELECT COUNT(*) FROM com_sales_order_line l
        WHERE l.tenant_id = #{query.tenantId} AND l.deleted = 0
          AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(l.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr ON pcr.tenant_id = ocr.tenant_id
                     AND pcr.contract_id = ocr.contract_id AND pcr.deleted = 0
                     AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = l.tenant_id AND ocr.order_id = l.order_id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          )
         /* IF test=query.orderId != null */ AND l.order_id = #{query.orderId} /* END IF */ 
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(l.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.orderType != null and query.orderType != '' */ AND l.order_type = #{query.orderType} /* END IF */ 
         /* IF test=query.orderNo != null and query.orderNo != '' */ AND l.order_no = #{query.orderNo} /* END IF */ 
         /* IF test=query.lineNo != null and query.lineNo != '' */ AND l.line_no = #{query.lineNo} /* END IF */ 
         /* IF test=query.productCode != null and query.productCode != '' */ AND l.product_code = #{query.productCode} /* END IF */ 
         /* IF test=query.quantityStatus != null and query.quantityStatus != '' */ AND l.quantity_status = #{query.quantityStatus} /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND l.status = #{query.status} /* END IF */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectByIdsForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:91; kind=select
SELECT * FROM com_sales_order_line
        WHERE tenant_id = #{query.tenantId}
          AND deleted = 0
          AND id IN
           /* FOREACH collection=query.orderLineIds; item=orderLineId; open=(; separator=,; close=) */ (#{orderLineId}) /* repeat with separator=,; END FOREACH */ 
        ORDER BY id ASC
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectDistinctRealExecutionNos; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:103; kind=select
SELECT DISTINCT l.real_execution_no FROM com_sales_order_line l
        WHERE l.tenant_id = #{query.tenantId} AND l.deleted = 0
          AND l.real_execution_no IS NOT NULL
          AND l.order_id IN
           /* FOREACH collection=query.orderIds; item=orderId; open=(; separator=,; close=) */ (#{orderId}) /* repeat with separator=,; END FOREACH */ 
        ORDER BY l.real_execution_no;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.selectMigrationTargetIdsForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderLineMapper.xml:111; kind=select
SELECT id FROM com_sales_order_line WHERE tenant_id=#{query.tenantId}
         /* CHOOSE  */ /* WHEN test=query.targetIds != null and !query.targetIds.isEmpty() */ AND id IN  /* FOREACH collection=query.targetIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectBySourceForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:4; kind=select
SELECT * FROM com_sales_order
        WHERE tenant_id = #{query.tenantId}
          AND source_system = #{query.sourceSystem}
          AND source_record_key = #{query.sourceRecordKey}
          AND deleted = 0
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectByCompanyScope; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:42; kind=select
SELECT o.* FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId}
          AND o.deleted = 0
          /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(o.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr
                      ON pcr.tenant_id = ocr.tenant_id AND pcr.contract_id = ocr.contract_id
                     AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = o.tenant_id AND ocr.order_id = o.id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(o.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.orderNo != null and query.orderNo != '' */ AND o.order_no LIKE CONCAT('%', #{query.orderNo}, '%') /* END IF */ 
         /* IF test=query.orderType != null and query.orderType != '' */ AND o.order_type = #{query.orderType} /* END IF */ 
         /* IF test=query.customerKeyword != null and query.customerKeyword != '' */ AND (o.customer_code LIKE CONCAT('%', #{query.customerKeyword}, '%')
            OR o.customer_name LIKE CONCAT('%', #{query.customerKeyword}, '%')) /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND o.status = #{query.status} /* END IF */ 
        ORDER BY o.order_no, o.id
        LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectCountByCompanyScope; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:68; kind=select
SELECT COUNT(*) FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId} AND o.deleted = 0
          /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(o.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr
                      ON pcr.tenant_id = ocr.tenant_id AND pcr.contract_id = ocr.contract_id
                     AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = o.tenant_id AND ocr.order_id = o.id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(o.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.orderNo != null and query.orderNo != '' */ AND o.order_no LIKE CONCAT('%', #{query.orderNo}, '%') /* END IF */ 
         /* IF test=query.orderType != null and query.orderType != '' */ AND o.order_type = #{query.orderType} /* END IF */ 
         /* IF test=query.customerKeyword != null and query.customerKeyword != '' */ AND (o.customer_code LIKE CONCAT('%', #{query.customerKeyword}, '%') OR o.customer_name LIKE CONCAT('%', #{query.customerKeyword}, '%')) /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND o.status = #{query.status} /* END IF */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectRelatedByContract; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:81; kind=select
SELECT o.* FROM com_sales_order o
        JOIN com_order_contract_relation r ON r.tenant_id = o.tenant_id AND r.order_id = o.id AND r.deleted = 0
        WHERE o.tenant_id = #{query.tenantId} AND r.contract_id = #{query.contractId} AND o.deleted = 0
        ORDER BY o.order_no, o.id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectCreationOrdersByContract; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:88; kind=select
SELECT o.* FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId} AND o.deleted = 0
          AND o.status = 'ENABLED' AND o.order_type = '0'
          AND (
           /* IF test=query.contractNo != null and query.contractNo != '' and query.companyCode != null and query.companyCode != '' */ (o.contract_no IS NOT NULL
            AND CAST(o.contract_no AS BINARY) = CAST(#{query.contractNo} AS BINARY)
            AND CAST(o.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY)) /* END IF */ 
           /* IF test=query.contractNo != null and query.contractNo != '' and query.companyCode != null and query.companyCode != '' */ OR /* END IF */ 
          EXISTS (SELECT 1 FROM com_order_contract_relation r
                  WHERE r.tenant_id = o.tenant_id AND r.order_id = o.id AND r.deleted = 0
                    AND r.contract_id = #{query.contractId}
                    AND r.effective_from <= CURRENT_TIMESTAMP
                    AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
        ORDER BY o.order_create_time, o.order_no, o.id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectRootReadPage; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:108; kind=select
SELECT o.* FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId} AND o.deleted = 0
        /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(o.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr
                      ON pcr.tenant_id = ocr.tenant_id AND pcr.contract_id = ocr.contract_id
                     AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = o.tenant_id AND ocr.order_id = o.id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.afterId != null */ AND (o.order_no > #{query.afterCode}
            OR (o.order_no = #{query.afterCode} AND o.id > #{query.afterId})) /* END IF */ 
        ORDER BY o.order_no, o.id
        LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectDetailByCompanyScope; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:121; kind=select
SELECT o.* FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId} AND o.id = #{query.orderId} AND o.deleted = 0
        /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(o.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_order_contract_relation ocr
                    JOIN com_project_contract_relation pcr
                      ON pcr.tenant_id = ocr.tenant_id AND pcr.contract_id = ocr.contract_id
                     AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                   WHERE ocr.tenant_id = o.tenant_id AND ocr.order_id = o.id AND ocr.deleted = 0
                     AND pcr.project_id IN
                      /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectCreationOrdersForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:128; kind=select
SELECT o.* FROM com_sales_order o
        WHERE o.tenant_id = #{query.tenantId} AND o.deleted = 0
          AND o.status = 'ENABLED' AND o.order_type = '0'
          AND (
           /* IF test=query.contractNo != null and query.contractNo != '' and query.companyCode != null and query.companyCode != '' */ (o.contract_no IS NOT NULL
            AND CAST(o.contract_no AS BINARY) = CAST(#{query.contractNo} AS BINARY)
            AND CAST(o.company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY)) /* END IF */ 
           /* IF test=query.contractNo != null and query.contractNo != '' and query.companyCode != null and query.companyCode != '' */ OR /* END IF */ 
          EXISTS (SELECT 1 FROM com_order_contract_relation r
                  WHERE r.tenant_id = o.tenant_id AND r.order_id = o.id AND r.deleted = 0
                    AND r.contract_id = #{query.contractId}
                    AND r.effective_from <= CURRENT_TIMESTAMP
                    AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
        ORDER BY o.order_create_time, o.order_no, o.id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectCreationRelationsByContract; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:149; kind=select
SELECT * FROM com_order_contract_relation
        WHERE tenant_id = #{query.tenantId} AND contract_id = #{query.contractId}
          AND deleted = 0 AND effective_from <= CURRENT_TIMESTAMP
          AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP)
        ORDER BY id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.selectCreationRelationsForUpdate; pms-module-commerce/src/main/resources/mapper/order/SalesOrderMapper.xml:156; kind=select
SELECT * FROM com_order_contract_relation
        WHERE tenant_id = #{query.tenantId} AND contract_id = #{query.contractId}
          AND deleted = 0 AND effective_from <= CURRENT_TIMESTAMP
          AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP)
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.selectSourcePage; pms-module-platform/src/main/resources/mapper/migration/ExternalKeyMappingMapper.xml:16; kind=select
SELECT id,batch_id,source_record_id,result_type,target_context,target_object_type,
               target_table,target_id,target_role,target_sequence,tenant_id,create_time,
               update_time,creator,updater,deleted
        FROM plt_external_key_mapping FORCE INDEX (idx_plt_external_mapping_batch)
        WHERE tenant_id = #{query.tenantId} AND batch_id = #{query.batchId} AND deleted = 0
         /* CHOOSE  */ /* WHEN test=query.sourceRecordIds != null and query.sourceRecordIds.size() > 0 */ AND source_record_id IN
                 /* FOREACH collection=query.sourceRecordIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper.selectByTenantAndIdForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationBatchMapper.xml:6; kind=select
SELECT * FROM plt_migration_batch
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.batchId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper.selectNextStagedForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationBatchMapper.xml:15; kind=select
SELECT * FROM plt_migration_batch
        WHERE tenant_id = #{query.tenantId}
          AND owner_context_code = #{query.ownerContextCode}
          AND purpose_code = #{query.purposeCode}
          AND batch_status = 'STAGED_READY'
          AND deleted = b'0'
          AND source_system IN
           /* FOREACH collection=query.sourceSystems; item=value; open=(; separator=,; close=) */ (#{value}) /* repeat with separator=,; END FOREACH */ 
          AND source_table IN
           /* FOREACH collection=query.sourceTables; item=value; open=(; separator=,; close=) */ (#{value}) /* repeat with separator=,; END FOREACH */ 
        ORDER BY create_time ASC, id ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectByBatchAndIdForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:16; kind=select
SELECT * FROM plt_migration_source_record
        WHERE tenant_id = #{query.tenantId}
          AND batch_id = #{query.batchId}
          AND id = #{query.sourceRecordId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectCursorPage; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:26; kind=select
SELECT s.*,
               CASE
                   WHEN EXISTS (SELECT 1 FROM plt_external_key_mapping m
                                WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                                  AND m.result_type = 'MAPPED' AND m.deleted = b'0') THEN 'MAPPED'
                   WHEN EXISTS (SELECT 1 FROM plt_migration_issue i
                                WHERE i.tenant_id = s.tenant_id AND i.source_record_id = s.id
                                  AND i.deleted = b'0') THEN 'ISSUE'
                   WHEN EXISTS (SELECT 1 FROM plt_external_key_mapping m
                                WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                                  AND m.result_type = 'RETAINED' AND m.deleted = b'0') THEN 'RETAINED'
                   ELSE NULL
               END AS result_type
        FROM plt_migration_source_record s
        WHERE s.tenant_id = #{query.tenantId}
          AND s.batch_id = #{query.batchId}
          AND s.deleted = b'0'
           /* IF test=query.afterSourceRecordId != null */ AND s.id > #{query.afterSourceRecordId} /* END IF */ 
        ORDER BY s.id ASC
        LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectClassificationSummary; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:52; kind=select
SELECT COUNT(*) AS source_count,
               COALESCE(SUM(has_mapped), 0) AS mapped_count,
               COALESCE(SUM(has_issue), 0) AS issue_count,
               COALESCE(SUM(has_retained), 0) AS retained_count,
               COALESCE(SUM(has_mapped + has_issue + has_retained = 0), 0) AS unclassified_count,
               COALESCE(SUM(has_mapped + has_issue + has_retained > 1), 0) AS conflicting_count
        FROM (
            SELECT s.id,
                   EXISTS (SELECT 1 FROM plt_external_key_mapping m FORCE INDEX (uk_plt_external_mapping_result)
                           WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                             AND m.result_type = 'MAPPED' AND m.deleted = b'0') AS has_mapped,
                   EXISTS (SELECT 1 FROM plt_migration_issue i FORCE INDEX (uk_plt_migration_issue_key)
                           WHERE i.tenant_id = s.tenant_id AND i.source_record_id = s.id
                             AND i.deleted = b'0') AS has_issue,
                   EXISTS (SELECT 1 FROM plt_external_key_mapping m FORCE INDEX (uk_plt_external_mapping_result)
                           WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                             AND m.result_type = 'RETAINED' AND m.deleted = b'0') AS has_retained
            FROM plt_migration_source_record s FORCE INDEX (idx_plt_migration_source_cursor)
            WHERE s.tenant_id = #{query.tenantId}
              AND s.batch_id = #{query.batchId}
              AND s.deleted = b'0'
        ) classified;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.countBatchSources; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:79; kind=select
SELECT COUNT(*) FROM plt_migration_source_record
    WHERE tenant_id = #{query.tenantId} AND batch_id = #{query.batchId} AND deleted = 0;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectRecordForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml:4; kind=select
SELECT * FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectBusinessRecordsForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml:7; kind=select
SELECT * FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND CAST(project_code AS BINARY) <=> CAST(#{query.projectCode} AS BINARY)
          AND CAST(execution_no AS BINARY) <=> CAST(#{query.executionNo} AS BINARY)
          AND CAST(product_category AS BINARY) <=> CAST(#{query.productCategory} AS BINARY)
          AND CAST(product_type AS BINARY) <=> CAST(#{query.productType} AS BINARY)
          AND CAST(product_code AS BINARY) <=> CAST(#{query.productCode} AS BINARY)
          AND CAST(product_model AS BINARY) <=> CAST(#{query.productModel} AS BINARY)
          AND CAST(product_name AS BINARY) <=> CAST(#{query.productName} AS BINARY)
          AND quantity <=> #{query.quantity}
          AND CAST(memo AS BINARY) <=> CAST(#{query.memo} AS BINARY)
          AND lease_duration <=> #{query.leaseDuration}
          AND CAST(data_source AS BINARY) <=> CAST(#{query.dataSource} AS BINARY)
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.countAllProjectRecords; pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml:22; kind=select
SELECT COUNT(*) FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectProjectPage; pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml:35; kind=select
SELECT c.* FROM (
            SELECT *, MAX(execution_no) OVER (PARTITION BY project_code) AS display_execution_no
            FROM proj_project_lease_product
            WHERE tenant_id=#{query.tenantId} AND deleted=b'0'
              AND project_code=#{query.sourceProjectCode}
              AND CAST(project_code AS BINARY)=CAST(#{query.sourceProjectCode} AS BINARY)
        ) c
        WHERE c.execution_no=c.display_execution_no
        ORDER BY c.execution_no,c.product_code,c.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.countProject; pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml:39; kind=select
SELECT COUNT(*) FROM (
            SELECT *, MAX(execution_no) OVER (PARTITION BY project_code) AS display_execution_no
            FROM proj_project_lease_product
            WHERE tenant_id=#{query.tenantId} AND deleted=b'0'
              AND project_code=#{query.sourceProjectCode}
              AND CAST(project_code AS BINARY)=CAST(#{query.sourceProjectCode} AS BINARY)
        ) c
        WHERE c.execution_no=c.display_execution_no;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectRecordForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:4; kind=select
SELECT * FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectBusinessRecordsForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:7; kind=select
SELECT * FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND CAST(project_code AS BINARY) <=> CAST(#{query.projectCode} AS BINARY)
          AND CAST(execution_no AS BINARY) <=> CAST(#{query.executionNo} AS BINARY)
          AND item_group <=> #{query.itemGroup}
          AND CAST(product_code AS BINARY) <=> CAST(#{query.productCode} AS BINARY)
          AND CAST(parent_code AS BINARY) <=> CAST(#{query.parentCode} AS BINARY)
          AND quantity <=> #{query.quantity}
          AND CAST(bom_path AS BINARY) <=> CAST(#{query.bomPath} AS BINARY)
          AND CAST(product_model AS BINARY) <=> CAST(#{query.productModel} AS BINARY)
          AND CAST(product_description AS BINARY) <=> CAST(#{query.productDescription} AS BINARY)
          AND level <=> #{query.level}
          AND CAST(data_source AS BINARY) <=> CAST(#{query.dataSource} AS BINARY)
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.countAllProjectRecords; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:22; kind=select
SELECT COUNT(*) FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectProjectPage; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:35; kind=select
SELECT c.* FROM (
            SELECT *, MAX(execution_no) OVER (PARTITION BY project_code) AS display_execution_no
            FROM proj_project_product_configuration
            WHERE tenant_id=#{query.tenantId} AND deleted=b'0'
              AND project_code=#{query.sourceProjectCode}
              AND CAST(project_code AS BINARY)=CAST(#{query.sourceProjectCode} AS BINARY)
        ) c
        WHERE c.execution_no=c.display_execution_no
        ORDER BY c.execution_no,c.item_group,c.bom_path,c.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.countProject; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:39; kind=select
SELECT COUNT(*) FROM (
            SELECT *, MAX(execution_no) OVER (PARTITION BY project_code) AS display_execution_no
            FROM proj_project_product_configuration
            WHERE tenant_id=#{query.tenantId} AND deleted=b'0'
              AND project_code=#{query.sourceProjectCode}
              AND CAST(project_code AS BINARY)=CAST(#{query.sourceProjectCode} AS BINARY)
        ) c
        WHERE c.execution_no=c.display_execution_no;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectPathNodes; pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml:42; kind=select
SELECT * FROM proj_project_product_configuration
        WHERE tenant_id=#{query.tenantId} AND deleted=b'0'
          AND project_code=#{query.sourceProjectCode}
          AND CAST(project_code AS BINARY)=CAST(#{query.sourceProjectCode} AS BINARY)
         /* CHOOSE  */ /* WHEN test=query.paths.size() > 0 */ AND (
                 /* FOREACH collection=query.paths; item=path; separator= OR  */ (CAST(project_code AS BINARY) <=> CAST(#{path.projectCode} AS BINARY)
                     AND CAST(execution_no AS BINARY) <=> CAST(#{path.executionNo} AS BINARY)
                     AND item_group <=> #{path.itemGroup}
                     AND CAST(bom_path AS BINARY) <=> CAST(#{path.bomPath} AS BINARY)) /* repeat with separator= OR ; END FOREACH */ 
                ) /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectRecordForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:4; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId}
          AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectBusinessRecordsForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:8; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND CAST(contract_no AS BINARY) <=> CAST(#{query.contractNo} AS BINARY)
          AND CAST(project_code AS BINARY) <=> CAST(#{query.projectCode} AS BINARY)
          AND CAST(execution_no AS BINARY) <=> CAST(#{query.executionNo} AS BINARY)
          AND CAST(product_category_name AS BINARY) <=> CAST(#{query.productCategoryName} AS BINARY)
          AND CAST(product_name AS BINARY) <=> CAST(#{query.productName} AS BINARY)
          AND CAST(product_sub_code AS BINARY) <=> CAST(#{query.productSubCode} AS BINARY)
          AND CAST(product_sub_model AS BINARY) <=> CAST(#{query.productSubModel} AS BINARY)
          AND CAST(product_sub_name AS BINARY) <=> CAST(#{query.productSubName} AS BINARY)
          AND quantity <=> #{query.quantity}
          AND CAST(memo AS BINARY) <=> CAST(#{query.memo} AS BINARY)
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectProjectPage; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:22; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countAllProjectRecords; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:26; kind=select
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countProject; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:29; kind=select
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSource; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectProjectSources; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:12; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSnapshot; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:15; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND id=#{query.id};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectBatch; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:18; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND source_id=#{query.sourceId} AND batch_key=#{query.batchKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.countRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:42; kind=select
SELECT COUNT(*) FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:45; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceLicenseRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:49; kind=select
SELECT r.* FROM proj_legacy_detail_record r
    JOIN proj_legacy_detail_snapshot s ON s.id=r.snapshot_id AND s.tenant_id=r.tenant_id
    WHERE r.tenant_id=#{query.tenantId} AND s.tenant_id=#{query.tenantId} AND s.source_id=#{query.sourceId}
      AND r.domain_code='licenses'
     /* IF test=query.afterId != null */ AND r.id>#{query.afterId} /* END IF */ 
    ORDER BY r.id LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectEndDateForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:17; kind=select
SELECT * FROM proj_project WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId}
          AND deleted = b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectListByIdsForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:28; kind=select
SELECT *
        FROM proj_project
        WHERE id IN
         /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ 
          AND deleted = b'0'
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectVisiblePage; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:104; kind=select
SELECT p.*
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
          AND p.deleted = b'0'
          AND p.id IN
           /* FOREACH collection=query.visibleProjectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
           /* IF test=query.projectNameKeyword != null and query.projectNameKeyword != '' */ AND p.project_name LIKE CONCAT('%', #{query.projectNameKeyword}, '%') /* END IF */ 
           /* IF test=query.projectCodePrefix != null and query.projectCodePrefix != '' */ AND p.project_code LIKE CONCAT(#{query.projectCodePrefix}, '%') /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.status != null and query.status != '' and query.status.matches('S[0-6]') */ AND p.current_stage = #{query.status}
                  AND p.lifecycle_status = 'ACTIVE' /* END WHEN */ 
               /* OTHERWISE  */ /* IF test=query.status != null and query.status != '' */ AND p.lifecycle_status = #{query.status} /* END IF */ /* END OTHERWISE */ /* END CHOOSE */ 
           /* IF test=query.signingMethod != null and query.signingMethod != '' */ AND p.signing_method = #{query.signingMethod} /* END IF */ 
           /* IF test=query.projectCategory != null and query.projectCategory != '' */ AND p.project_category = #{query.projectCategory} /* END IF */ 
           /* IF test=query.implementationMode != null and query.implementationMode != '' */ AND p.implementation_mode = #{query.implementationMode} /* END IF */ 
           /* IF test=query.majorProjectLevel != null and query.majorProjectLevel != '' */ AND p.major_project_level = #{query.majorProjectLevel} /* END IF */ 
           /* IF test=query.contractNoKeyword != null and query.contractNoKeyword != '' */ AND p.contract_no LIKE CONCAT('%', #{query.contractNoKeyword}, '%') /* END IF */ 
           /* IF test=query.departmentId != null */ AND p.department_id = #{query.departmentId} /* END IF */ 
           /* IF test=query.companyId != null */ AND p.company_id = #{query.companyId} /* END IF */ 
           /* IF test=query.createTimeStart != null */ AND p.create_time >= #{query.createTimeStart} /* END IF */ 
           /* IF test=query.createTimeEnd != null */ AND p.create_time < #{query.createTimeEnd} /* END IF */ 
           /* IF test=query.closeTimeStart != null */ AND p.project_close_time >= #{query.closeTimeStart} /* END IF */ 
           /* IF test=query.closeTimeEnd != null */ AND p.project_close_time < #{query.closeTimeEnd} /* END IF */ 
           /* IF test=query.refreshTimeStart != null */ AND p.project_refresh_time >= #{query.refreshTimeStart} /* END IF */ 
           /* IF test=query.refreshTimeEnd != null */ AND p.project_refresh_time < #{query.refreshTimeEnd} /* END IF */ 
           /* IF test=query.agentServiceProviderKeyword != null and query.agentServiceProviderKeyword != '' */ AND EXISTS (
                  SELECT 1 FROM proj_project_party pp
                  WHERE pp.project_id = p.id
                    AND pp.tenant_id = #{query.tenantId}
                    AND pp.deleted = b'0'
                    AND pp.status = 'ACTIVE'
                    AND (pp.effective_from IS NULL OR pp.effective_from <= #{query.effectiveAt})
                    AND (pp.effective_to IS NULL OR pp.effective_to > #{query.effectiveAt})
                    AND pp.party_role IN ('AGENT', 'SERVICE_PROVIDER')
                    AND (pp.party_name LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%')
                      OR pp.party_code LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%'))
              ) /* END IF */ 
        ORDER BY p.id DESC;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.selectSourceForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:5; kind=select
SELECT * FROM proj_project_party
        WHERE tenant_id = #{query.tenantId} AND source_system = #{query.sourceSystem}
          AND source_table = #{query.sourceTable} AND source_record_key = #{query.sourceRecordKey}
          AND party_role = #{query.partyRole}
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.selectMigrationSourceForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:12; kind=select
SELECT * FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_record_key=#{query.sourceKey} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:ast_device_license_record; pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/license/DeviceLicenseRecordDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device_license_record` (`id`, `tenant_id`, `device_id`, `device_sn`, `contract_id`, `contract_no`, `contract_type`, `product_code`, `product_model`, `license_status`, `license_code_ciphertext`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_device_id, :orm_device_sn, :orm_contract_id, :orm_contract_no, :orm_contract_type, :orm_product_code, :orm_product_model, :orm_license_status, :orm_license_code_ciphertext, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
