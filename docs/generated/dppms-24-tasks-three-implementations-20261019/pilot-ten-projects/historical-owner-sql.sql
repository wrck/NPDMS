-- 历史Owner/编排直接引用Mapper声明，gitRef=e21f02d2d
-- 不是SQL执行日志；BaseMapper动态INSERT无独立XML时见Owner源码，不伪造完整运行SQL。
-- MyBatis动态分支只作展示，完整节点在historical-owner-mapper-nodes.json；2026-10-19未执行。

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.selectRecordForUpdate; source=pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml
SELECT * FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.selectVisiblePage; source=pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml
SELECT id,tenant_id,device_id,device_sn,contract_id,contract_no,contract_type,product_code,product_model,
            license_status,creator,create_time,updater,update_time,deleted
        FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND deleted=0
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */
        ORDER BY id LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper.countVisible; source=pms-module-asset/src/main/resources/mapper/license/DeviceLicenseRecordMapper.xml
SELECT COUNT(*) FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND deleted=0
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectRecordForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml
SELECT * FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectBusinessRecordsForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml
SELECT * FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND BINARY project_code <=> BINARY #{query.projectCode}
          AND BINARY execution_no <=> BINARY #{query.executionNo}
          AND BINARY product_category <=> BINARY #{query.productCategory}
          AND BINARY product_type <=> BINARY #{query.productType}
          AND BINARY product_code <=> BINARY #{query.productCode}
          AND BINARY product_model <=> BINARY #{query.productModel}
          AND BINARY product_name <=> BINARY #{query.productName}
          AND quantity <=> #{query.quantity}
          AND BINARY memo <=> BINARY #{query.memo}
          AND lease_duration <=> #{query.leaseDuration}
          AND BINARY data_source <=> BINARY #{query.dataSource}
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.countAllProjectRecords; source=pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml
SELECT COUNT(*) FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.selectProjectPage; source=pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml
SELECT * FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper.countProject; source=pms-module-project/src/main/resources/mapper/associated/ProjectLeaseProductMapper.xml
SELECT COUNT(*) FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectRecordForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT * FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectBusinessRecordsForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT * FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND BINARY project_code <=> BINARY #{query.projectCode}
          AND BINARY execution_no <=> BINARY #{query.executionNo}
          AND item_group <=> #{query.itemGroup}
          AND BINARY product_code <=> BINARY #{query.productCode}
          AND BINARY parent_code <=> BINARY #{query.parentCode}
          AND quantity <=> #{query.quantity}
          AND BINARY bom_path <=> BINARY #{query.bomPath}
          AND BINARY product_model <=> BINARY #{query.productModel}
          AND BINARY product_description <=> BINARY #{query.productDescription}
          AND level <=> #{query.level}
          AND BINARY data_source <=> BINARY #{query.dataSource}
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.countAllProjectRecords; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT COUNT(*) FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectProjectPage; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT * FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.countProject; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT COUNT(*) FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper.selectPathNodes; source=pms-module-project/src/main/resources/mapper/associated/ProjectProductConfigurationMapper.xml
SELECT * FROM proj_project_product_configuration
        WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
         /* CHOOSE  */ /* WHEN test=query.paths.size() > 0 */ AND (
                 /* FOREACH collection=query.paths; item=path; separator= OR  */ (BINARY project_code <=> BINARY #{path.projectCode}
                     AND BINARY execution_no <=> BINARY #{path.executionNo}
                     AND item_group <=> #{path.itemGroup}
                     AND BINARY bom_path <=> BINARY #{path.bomPath}) /* repeat with separator= OR ; END FOREACH */ 
                ) /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectRecordForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId}
          AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectBusinessRecordsForUpdate; source=pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND BINARY contract_no <=> BINARY #{query.contractNo}
          AND BINARY project_code <=> BINARY #{query.projectCode}
          AND BINARY execution_no <=> BINARY #{query.executionNo}
          AND BINARY product_category_name <=> BINARY #{query.productCategoryName}
          AND BINARY product_name <=> BINARY #{query.productName}
          AND BINARY product_sub_code <=> BINARY #{query.productSubCode}
          AND BINARY product_sub_model <=> BINARY #{query.productSubModel}
          AND BINARY product_sub_name <=> BINARY #{query.productSubName}
          AND quantity <=> #{query.quantity}
          AND BINARY memo <=> BINARY #{query.memo}
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectProjectPage; source=pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countAllProjectRecords; source=pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countProject; source=pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSource; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceForUpdate; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectProjectSources; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSnapshot; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND id=#{query.id};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectBatch; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND source_id=#{query.sourceId} AND batch_key=#{query.batchKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.insertSource; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
INSERT INTO proj_legacy_detail_source(id,tenant_id,project_id,source_system,source_project_key,source_contract_no,current_snapshot_id,version)
    VALUES(#{id},#{tenantId},#{projectId},'DPPMS',#{sourceProjectKey},#{sourceContractNo},NULL,#{version});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.appendSnapshot; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
INSERT INTO proj_legacy_detail_snapshot(id,tenant_id,source_id,batch_key,checksum,source_read_at,captured_at,operator_user_id,domain_counts_json,record_count)
    VALUES(#{id},#{tenantId},#{sourceId},#{batchKey},#{checksum},#{sourceReadAt},#{capturedAt},#{operatorUserId},#{domainCountsJson},#{recordCount});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.appendRecord; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
INSERT INTO proj_legacy_detail_record(id,tenant_id,snapshot_id,domain_code,source_table,source_key,parent_domain,parent_source_key,source_updated_at,checksum,payload_json,redacted_fields_json)
    VALUES(#{id},#{tenantId},#{snapshotId},#{domainCode},#{sourceTable},#{sourceKey},#{parentDomain},#{parentSourceKey},#{sourceUpdatedAt},#{checksum},#{payloadJson},#{redactedFieldsJson});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.updateHead; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
UPDATE proj_legacy_detail_source SET current_snapshot_id=#{query.snapshotId},version=version+1
    WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND version=#{query.expectedVersion};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.countRecords; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT COUNT(*) FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectRecords; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceLicenseRecords; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml
SELECT r.* FROM proj_legacy_detail_record r
    JOIN proj_legacy_detail_snapshot s ON s.id=r.snapshot_id AND s.tenant_id=r.tenant_id
    WHERE r.tenant_id=#{query.tenantId} AND s.tenant_id=#{query.tenantId} AND s.source_id=#{query.sourceId}
      AND r.domain_code='licenses'
     /* IF test=query.afterId != null */ AND r.id>#{query.afterId} /* END IF */ 
    ORDER BY r.id LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentity; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentityForUpdate; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectExactCodeIds; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml
SELECT id FROM proj_project WHERE tenant_id=#{query.tenantId} AND project_code=#{query.projectCode}
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.initializeFormalIfMatch; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml
UPDATE proj_project SET
      project_code=#{query.facts.projectCode}, project_name=#{query.facts.projectName},
      company_id=#{query.facts.companyId}, company_code=#{query.facts.companyCode}, company_name=#{query.facts.companyName},
      project_type=#{query.facts.projectType}, project_category=#{query.facts.projectCategory},
      signing_method=#{query.facts.signingMethod}, implementation_mode=#{query.facts.implementationMode},
      customer_project_name=#{query.facts.customerProjectName}, sales_type=#{query.facts.salesType},
      major_project_level=#{query.facts.majorProjectLevel},
      market_name=#{query.facts.marketName}, system_name=#{query.facts.systemName},
      expend_name=#{query.facts.expendName}, industry_name=#{query.facts.industryName},
      not_track_reason=#{query.facts.notTrackReason},
      project_start_time=#{query.facts.projectStartTime}, project_refresh_time=#{query.facts.projectRefreshTime},
      project_close_time=#{query.facts.projectCloseTime}, order_create_time=#{query.facts.orderCreateTime},
      custom_info=#{query.facts.customInfo}, disabled=#{query.facts.disabled},
      effective_from=#{query.facts.effectiveFrom}, effective_to=#{query.facts.effectiveTo},
      creator=#{query.facts.creator}, create_time=#{query.facts.createTime},
      updater=#{query.facts.updater}, update_time=#{query.facts.updateTime},
      lifecycle_status=#{query.facts.lifecycleStatus}, current_stage=#{query.facts.currentStage},
      closed_at=#{query.facts.closedAt}, version=version+1
    WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
      AND lifecycle_status=#{query.expectedLifecycleStatus} AND current_stage=#{query.expectedCurrentStage}
      AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.insertHistoricalRoot; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml
INSERT INTO proj_project (
      id,tenant_id,project_code,project_name,code_root_id,project_sequence,code_rule_version,
      root_id,tree_path,tree_depth,tree_sort,department_id,department_code,department_name,
      company_id,company_code,company_name,project_type,project_category,signing_method,implementation_mode,
      customer_project_name,sales_type,major_project_level,market_name,system_name,expend_name,industry_name,
      not_track_reason,project_start_time,project_refresh_time,project_close_time,order_create_time,custom_info,
      disabled,effective_from,effective_to,creator,create_time,updater,update_time,
      source_type,status,lifecycle_status,current_stage,closed_at,assignment_status,version,deleted
    ) VALUES (
      #{query.facts.id},#{query.facts.tenantId},#{query.facts.projectCode},#{query.facts.projectName},#{query.facts.id},0,'V1',
      #{query.facts.id},'',0,0,#{query.departmentId},#{query.departmentCode},#{query.departmentName},
      #{query.facts.companyId},#{query.facts.companyCode},#{query.facts.companyName},#{query.facts.projectType},
      #{query.facts.projectCategory},#{query.facts.signingMethod},#{query.facts.implementationMode},
      #{query.facts.customerProjectName},#{query.facts.salesType},#{query.facts.majorProjectLevel},#{query.facts.marketName},
      #{query.facts.systemName},#{query.facts.expendName},#{query.facts.industryName},#{query.facts.notTrackReason},
      #{query.facts.projectStartTime},#{query.facts.projectRefreshTime},#{query.facts.projectCloseTime},
      #{query.facts.orderCreateTime},#{query.facts.customInfo},#{query.facts.disabled},#{query.facts.effectiveFrom},#{query.facts.effectiveTo},
      #{query.facts.creator},#{query.facts.createTime},#{query.facts.updater},#{query.facts.updateTime},
      'MIGRATION',#{query.facts.lifecycleStatus},#{query.facts.lifecycleStatus},#{query.facts.currentStage},
      #{query.facts.closedAt},'UNASSIGNED',#{query.facts.version},b'0'
    );

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.selectProjectForUpdate; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml
SELECT * FROM proj_project_member_assignment
    WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.insertHistorical; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml
INSERT INTO proj_project_member_assignment
    (id,tenant_id,project_id,user_id,employee_no,member_name,company_id,company_code,company_name,
     department_id,department_code,department_name,member_role,assignment_type,site_id,responsibility,
     change_reason,remark,end_reason,effective_from,effective_to,status,version,creator,create_time,
     updater,update_time,deleted,deleted_time,member_code,phone_number,email,registration_source,authorization_confirmed)
    VALUES
    (#{entry.values.id},#{entry.values.tenantId},#{entry.values.projectId},#{entry.values.userId},#{entry.values.employeeNo},#{entry.values.memberName},
     #{entry.values.companyId},#{entry.values.companyCode},#{entry.values.companyName},#{entry.values.departmentId},#{entry.values.departmentCode},
     #{entry.values.departmentName},#{entry.values.memberRole},#{entry.values.assignmentType},#{entry.values.siteId},#{entry.values.responsibility},
     #{entry.values.changeReason},#{entry.values.remark},#{entry.values.endReason},#{entry.values.effectiveFrom},#{entry.values.effectiveTo},#{entry.values.status},
     #{entry.values.version},#{entry.values.creator},#{entry.values.createTime},#{entry.values.updater},#{entry.values.updateTime},b'0',#{entry.values.deletedTime},#{entry.values.memberCode},
     #{entry.values.phoneNumber},#{entry.values.email},#{entry.values.registrationSource},#{entry.values.authorizationConfirmed});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.closeApprovedInterval; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml
UPDATE proj_project_member_assignment SET effective_to=#{query.effectiveTo},end_reason=#{query.reason},
      version=version+1,updater=#{query.actor},update_time=#{query.effectiveTo}
    WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.memberId}
      AND version=#{query.expectedVersion} AND authorization_confirmed=b'1' AND status='ACTIVE'
      AND (effective_to IS NULL OR effective_to>#{query.effectiveTo}) AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper.updateApprovedPrimary; source=pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml
UPDATE proj_project SET manager_id=#{query.managerId},manager_name=#{query.managerName},
      manager_employee_no=#{query.employeeNo},assignment_status=#{query.assignmentStatus},version=version+1,
      update_time=update_time
    WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
      AND lifecycle_status=#{query.expectedLifecycle} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.correctCustomerIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project SET customer_id=#{query.customerId}, customer_code=#{query.customerCode}, customer_name=#{query.customerName},
          version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP(3)
        WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
          AND lifecycle_status='ACTIVE' AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.associateCustomerIfMissing; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project SET customer_id=#{query.customerId}, customer_code=#{query.customerCode}, customer_name=#{query.customerName},
          version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP
        WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
          AND lifecycle_status='ACTIVE' AND deleted=b'0' AND customer_id IS NULL;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectEndDateForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
SELECT * FROM proj_project WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId}
          AND deleted = b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateEndDateIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project SET project_end_date = #{query.endDate}, version = version + 1,
            updater = #{query.updater}, update_time = CURRENT_TIMESTAMP
        WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId} AND version = #{query.expectedVersion}
          AND lifecycle_status = 'ACTIVE' AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectListByIdsForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
SELECT *
        FROM proj_project
        WHERE id IN
         /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ 
          AND deleted = b'0'
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.incrementVersionIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET version = version + 1
        WHERE id = #{projectId}
          AND version = #{expectedVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateAssignmentStatusIfVersion; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET assignment_status = #{query.assignmentStatus}
        WHERE id = #{query.projectId}
          AND version = #{query.version}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateBusinessAttributesIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET signing_method = #{query.signingMethod},
            project_category = #{query.projectCategory},
            implementation_mode = #{query.implementationMode},
            major_project_level = #{query.majorProjectLevel},
            version = version + 1
        WHERE id = #{query.projectId}
          AND tenant_id = #{query.tenantId}
          AND version = #{query.expectedVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateGovernanceStateIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET current_stage = #{query.currentStage},
            lifecycle_status = #{query.lifecycleStatus},
            assignment_status = #{query.assignmentStatus},
            version = version + 1,
            updater = #{query.updater},
            update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.projectId}
          AND version = #{query.expectedVersion}
          AND lifecycle_status = #{query.expectedLifecycleStatus}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.advanceStageIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET current_stage = #{query.targetStage}, version = version + 1,
            updater = #{query.updater}, update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId} AND id = #{query.projectId}
          AND version = #{query.expectedVersion} AND current_stage = #{query.expectedCurrentStage}
          AND lifecycle_status = 'ACTIVE' AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateManagerMembersIfMatch; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
UPDATE proj_project
        SET manager_id=#{query.managerId}, manager_name=#{query.managerName},
            manager_employee_no=#{query.managerEmployeeNo}, assignment_status=#{query.assignmentStatus},
            version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP
        WHERE id=#{query.projectId} AND tenant_id=#{query.tenantId}
          AND version=#{query.expectedVersion} AND lifecycle_status='ACTIVE' AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectVisiblePage; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml
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

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentResponsibilityForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT *
        FROM proj_project_member_assignment
        WHERE project_id = #{query.projectId}
          AND member_role = #{query.memberRole}
           /* CHOOSE  */ /* WHEN test=query.assignmentType == "PRIMARY" */ AND (assignment_type = 'PRIMARY' OR assignment_type IS NULL) /* END WHEN */ 
               /* OTHERWISE  */ AND assignment_type = #{query.assignmentType} /* END OTHERWISE */ /* END CHOOSE */ 
          AND ((site_id = #{query.siteId}) OR (site_id IS NULL AND #{query.siteId} IS NULL))
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveByUserForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT *
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodeCount; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT COUNT(*)
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodePage; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT p.id, p.project_code, p.project_name, p.parent_id, p.tree_path, p.tree_depth,
               p.tree_sort, p.assignment_status
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0'
        ORDER BY p.tree_path, p.tree_sort, p.id
        LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentServiceManagerAssignments; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT id, project_id, user_id, employee_no, member_name, department_id, department_code,
               department_name, member_role, assignment_type, site_id, change_reason, effective_from
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id IN
           /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
          AND member_role IN ('SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2')
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY project_id, member_role, site_id, department_id,
                 CASE WHEN assignment_type = 'PRIMARY' OR assignment_type IS NULL THEN 0 ELSE 1 END,
                 id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFacts; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
           /* IF test=query.subjectUserId != null */ AND user_id = #{query.subjectUserId} /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY user_id, member_role, id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFactsForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= CURRENT_TIMESTAMP(3))
          AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP(3))
        ORDER BY user_id, member_role, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.closeEffectiveServiceManagerAssignments; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
UPDATE proj_project_member_assignment
        SET effective_to = #{query.closedAt},
            version = version + 1,
            updater = #{query.updater},
            update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND member_role IN
           /* FOREACH collection=query.serviceRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ 
          AND (assignment_type IN ('PRIMARY', 'COLLABORATOR') OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.closedAt})
          AND (effective_to IS NULL OR effective_to > #{query.closedAt});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectProjectManagersForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND member_role='PROJECT_MANAGER' AND authorization_confirmed = b'1' AND status='ACTIVE' AND deleted=b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveMemberIdentityForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND member_role IN
           /* FOREACH collection=query.roleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ 
          AND authorization_confirmed = b'1' AND status = 'ACTIVE' AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.selectSourceForUpdate; source=pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml
SELECT * FROM proj_project_party
        WHERE tenant_id = #{query.tenantId} AND source_system = #{query.sourceSystem}
          AND source_table = #{query.sourceTable} AND source_record_key = #{query.sourceRecordKey}
          AND party_role = #{query.partyRole}
        FOR UPDATE;
