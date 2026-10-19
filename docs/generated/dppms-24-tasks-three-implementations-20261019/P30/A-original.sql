-- P30 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [DEVICE_BASE] OWNER_OR_CONTROLLED_INPUT; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P30.json
-- readMode=LOCAL_OWNER; object=DEVICE_BASE; 本地/受控Owner输入，实际Mapper查询及Java调用见本方式的查询和处理逻辑。;

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

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper.selectBatchForUpdate; pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:22; kind=select
SELECT id,tenant_id,project_id,contract_no,company_id,company_name,department_id,department_code,department_name,organization_source,organization_updated_at
    FROM ast_device WHERE tenant_id=#{query.tenantId} AND deleted=0 AND id > #{query.afterId}
     /* IF test=!query.rebuildAll */ AND (
         /* IF test=query.deviceIds != null and !query.deviceIds.isEmpty() */ id IN  /* FOREACH collection=query.deviceIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */  OR /* END IF */ 
         /* IF test=query.projectIds != null and !query.projectIds.isEmpty() */ project_id IN  /* FOREACH collection=query.projectIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */  OR /* END IF */ 
         /* IF test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ (project_id IS NULL AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ ) OR /* END IF */ 
        1=0
      ) /* END IF */ 
    ORDER BY id LIMIT #{query.limit} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper.selectStoredOrganizations; pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:26; kind=select
SELECT id,company_id,company_name,department_id,department_code,department_name,organization_source
    FROM ast_device WHERE tenant_id=#{query.tenantId} AND deleted=0 AND id > #{query.afterId}
     /* IF test=!query.rebuildAll */ AND (
         /* IF test=query.deviceIds != null and !query.deviceIds.isEmpty() */ id IN  /* FOREACH collection=query.deviceIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */  OR /* END IF */ 
         /* IF test=query.projectIds != null and !query.projectIds.isEmpty() */ project_id IN  /* FOREACH collection=query.projectIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */  OR /* END IF */ 
         /* IF test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ (project_id IS NULL AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ ) OR /* END IF */ 
        1=0
      ) /* END IF */ 
    ORDER BY id LIMIT #{query.limit};

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

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper.selectSourceIdentity; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_record
        WHERE tenant_id=#{query.tenantId} AND source_system=#{query.sourceSystem} AND source_key=#{query.sourceKey}
     FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.insertProjectRelationship; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:66; kind=insert
INSERT INTO ast_device_project_relationship (
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
        ) VALUES (
            #{deviceSn},
            #{projectId},
            #{relationshipType},
            #{effectiveFrom},
            #{effectiveTo},
            #{assignmentVersion},
            #{reason},
            #{operationId},
            #{sourceSystem},
            #{sourceKey},
            #{sourceVersion},
            #{version},
            #{tenantId}
        );

-- BaseMapper.insert:ast_device; pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/device/DeviceDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device` (`id`, `sn`, `name`, `product_code`, `product_model`, `product_name`, `product_desc`, `shipment_time`, `package_no`, `contract_no`, `shipment_record_id`, `project_id`, `project_assignment_version`, `customer_id`, `customer_assignment_version`, `site_id`, `site_location_id`, `location_resolution_status`, `location_snapshot`, `location_effective_from`, `location_record_id`, `warranty_start_date`, `warranty_end_date`, `warranty_status`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`, `status`, `remark`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`, `internal_serial_no`, `secondary_sn`, `secondary_item`, `company_id`, `company_name`, `department_id`, `department_code`, `department_name`, `organization_source`, `organization_updated_at`)
VALUES (:orm_id, :orm_sn, :orm_name, :orm_product_code, :orm_product_model, :orm_product_name, :orm_product_desc, :orm_shipment_time, :orm_package_no, :orm_contract_no, :orm_shipment_record_id, :orm_project_id, :orm_project_assignment_version, :orm_customer_id, :orm_customer_assignment_version, :orm_site_id, :orm_site_location_id, :orm_location_resolution_status, :orm_location_snapshot, :orm_location_effective_from, :orm_location_record_id, :orm_warranty_start_date, :orm_warranty_end_date, :orm_warranty_status, :orm_conp_version, :orm_conp_type, :orm_conp_series, :orm_conp_mark, :orm_status, :orm_remark, :orm_source_system, :orm_source_key, :orm_source_version, :orm_source_updated_at, :orm_synced_at, :orm_sync_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id, :orm_internal_serial_no, :orm_secondary_sn, :orm_secondary_item, :orm_company_id, :orm_company_name, :orm_department_id, :orm_department_code, :orm_department_name, :orm_organization_source, :orm_organization_updated_at);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.closeCurrentProject; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:52; kind=update
UPDATE ast_device_project_relationship r
        JOIN ast_device d
          ON d.tenant_id = r.tenant_id
         AND d.sn = r.device_sn
        SET r.effective_to = #{effectiveTo},
            r.assignment_version = #{assignmentVersion}
        WHERE r.tenant_id = #{tenantId}
          AND d.id = #{deviceId}
          AND r.relationship_type = 'DIRECT'
          AND r.effective_to IS NULL
          AND r.deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.updateDeviceProjectIfMatch; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:100; kind=update
UPDATE ast_device
        SET project_id = #{update.projectId},
            project_assignment_version = #{update.newAssignmentVersion}
        WHERE tenant_id = #{update.tenantId}
          AND id = #{update.deviceId}
          AND project_assignment_version = #{update.expectedAssignmentVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.closeCurrentCustomer; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:141; kind=update
UPDATE ast_device_customer_relationship r
        JOIN ast_device d
          ON d.tenant_id = r.tenant_id
         AND d.sn = r.device_sn
        SET r.effective_to = #{effectiveTo},
            r.assignment_version = #{assignmentVersion}
        WHERE r.tenant_id = #{tenantId}
          AND d.id = #{deviceId}
          AND r.relationship_type = 'DIRECT'
          AND r.effective_to IS NULL
          AND r.deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper.updateDeviceCustomerIfMatch; pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:189; kind=update
UPDATE ast_device
        SET customer_id = #{update.customerId},
            customer_assignment_version = #{update.newAssignmentVersion}
        WHERE tenant_id = #{update.tenantId}
          AND id = #{update.deviceId}
          AND customer_assignment_version = #{update.expectedAssignmentVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper.updateOrganizationBatch; pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:30; kind=update
UPDATE ast_device SET company_id=#{query.companyId},company_name=#{query.companyName},
      department_id=#{query.departmentId},department_code=#{query.departmentCode},department_name=#{query.departmentName},
      organization_source=#{query.source},organization_updated_at=CURRENT_TIMESTAMP(3)
    WHERE tenant_id=#{query.tenantId} AND deleted=0
      AND id IN  /* FOREACH collection=deviceIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper.updateOrganizationRows; pms-module-asset/src/main/resources/mapper/device/DeviceOrganizationMapper.xml:40; kind=update
/* CHOOSE  */ /* WHEN test=rows != null and !rows.isEmpty() */ UPDATE ast_device SET
          company_id=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.companyId} /* repeat with separator=; END FOREACH */ 
            ELSE company_id END,
          company_name=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.companyName} /* repeat with separator=; END FOREACH */ 
            ELSE company_name END,
          department_id=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.departmentId} /* repeat with separator=; END FOREACH */ 
            ELSE department_id END,
          department_code=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.departmentCode} /* repeat with separator=; END FOREACH */ 
            ELSE department_code END,
          department_name=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.departmentName} /* repeat with separator=; END FOREACH */ 
            ELSE department_name END,
          organization_source=CASE id
             /* FOREACH collection=rows; item=row */ WHEN #{row.deviceId} THEN #{row.source} /* repeat with separator=; END FOREACH */ 
            ELSE organization_source END,
          organization_updated_at=CURRENT_TIMESTAMP(3)
        WHERE tenant_id=#{tenantId} AND deleted=0
          AND id IN  /* FOREACH collection=rows; item=row; open=(; close=); separator=, */ (#{row.deviceId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
       /* OTHERWISE  */ UPDATE ast_device SET id=id WHERE tenant_id=#{tenantId} AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
