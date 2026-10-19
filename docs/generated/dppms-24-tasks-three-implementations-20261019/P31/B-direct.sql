-- P31 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [MEMBER_HISTORY] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT m.*,m.projectType AS originalProjectType FROM pm_project_member m;

-- [CONTACT_HISTORY] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- queryRef=MEMBER_HISTORY; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectContactHistoryMapper.selectProjectForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectContactHistoryMapper.xml:4; kind=select
SELECT * FROM cus_project_customer_contact_relation
    WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectMemberHistoryMapper.selectProjectForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectMemberHistoryMapper.xml:4; kind=select
SELECT * FROM proj_project_member_assignment
    WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSource; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSourceForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectRecords; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:17; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectContactHistoryMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectContactHistoryMapper.xml:8; kind=insert
INSERT INTO cus_project_customer_contact_relation
    (id,project_id,customer_id,customer_contact_id,name,department,title,mobile,phone,email,primary_flag,primary_set_time,
     status,remark,version,creator,create_time,updater,update_time,deleted,deleted_by,deleted_at,tenant_id,
     project_party_id,contact_code,registration_source,effective_from,effective_to)
    VALUES
    (#{entry.values.id},#{entry.values.projectId},#{entry.values.customerId},#{entry.values.customerContactId},
     #{entry.values.name},#{entry.values.department},#{entry.values.title},#{entry.values.mobile},#{entry.values.phone},#{entry.values.email},
     #{entry.values.primaryFlag},#{entry.values.primarySetTime},#{entry.values.status},#{entry.values.remark},#{entry.values.version},
     #{entry.values.creator},#{entry.values.createTime},#{entry.values.updater},#{entry.values.updateTime},#{entry.values.deleted},
     #{entry.values.deletedBy},#{entry.values.deletedAt},#{entry.values.tenantId},#{entry.values.projectPartyId},#{entry.values.contactCode},
     #{entry.values.registrationSource},#{entry.values.effectiveFrom},#{entry.values.effectiveTo});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectMemberHistoryMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectMemberHistoryMapper.xml:9; kind=insert
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

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
