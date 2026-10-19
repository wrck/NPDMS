-- P04 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectLegacyUsers] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT user_id AS id,user_name AS username,create_by AS creator,create_time AS createTime,update_by AS updater,update_time AS updateTime,status,CAST(needChangePwd AS UNSIGNED) AS needChangePwd,loginErrorCount,isSysUser,userCustom1,userCustom2,userCustom3,userCustom4,userCustom5,0 AS fnd FROM t_user LIMIT #{limit};

-- [selectFndUsers] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT id,username,email,dpNo,realName,roleIds,isemail,status,defaultPage,pwdoverdue,createBy AS creator,createTime,updateBy AS updater,updateTime,effectiveFrom,effectiveTo,customInfo,1 AS fnd FROM fnd_user_info LIMIT #{limit};

-- [selectEmployments] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT `id` AS id,`workNo` AS workNo,`realName` AS realName,`eName` AS eName,`compID` AS compID,`depID` AS depID,`jobID` AS jobID,`reportTo` AS reportTo,`wfreportTo` AS wfreportTo,`empStatus` AS empStatus,`jobStatus` AS jobStatus,`empType` AS empType,`sex` AS sex,`birthday` AS birthday,`email` AS email,`mobile` AS mobile,`telphone` AS telphone,`avatar` AS avatar,`remark` AS remark,`state` AS state,`user_id` AS userId,`custom1` AS custom1,`custom2` AS custom2,`custom3` AS custom3,`custom4` AS custom4,`custom5` AS custom5 FROM t_user_info LIMIT #{limit};

-- [selectLegacyRoles] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT role_id AS id,role_name AS rawCode,role_name_zn AS name,remark,priority,status,create_by AS creator,create_time AS createTime,update_by AS updater,update_time AS updateTime,0 AS fnd FROM t_role LIMIT #{limit};

-- [selectFndRoles] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT id,roleName AS name,roleRemark AS remark,status,createBy AS creator,createTime,updateBy AS updater,updateTime,1 AS fnd FROM fnd_roles LIMIT #{limit};

-- [selectMemberships] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT id,user_id,role_id,comp_id AS companyId FROM t_user_role LIMIT #{limit};

-- [selectPowers] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT id,fndUserId,username,areapower,createBy AS creator,createTime,updateBy AS updater,updateTime,effectiveFrom,effectiveTo FROM fnd_user_power LIMIT #{limit};

-- [selectLogins] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT account,empID AS employeeId,workNo,name FROM ehr_login LIMIT #{limit};

-- [selectEmployees] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT empID AS id,workNo,name FROM ehr_employee LIMIT #{limit};

-- [selectDepartments] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT depID AS id,depCode AS code,depName AS name FROM ehr_department LIMIT #{limit};

-- [selectJobs] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT jobID AS id,jobCode AS code,jobName AS name FROM ehr_job LIMIT #{limit};

-- [selectCompanies] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P04SourceMapper.xml
SELECT id,compCode AS code,compName AS name FROM t_company LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectUsers; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:4; kind=select
SELECT `id`,`username`,`nickname`,`remark`,`dept_id`,`post_ids`,`email`,`mobile`,`sex`,`avatar`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`,`employee_no`,`custom_info` FROM system_users WHERE tenant_id=#{query.tenantId} /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectRoles; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:7; kind=select
SELECT `id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id` FROM system_role WHERE tenant_id=#{query.tenantId} /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectMemberships; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:10; kind=select
SELECT `id`,`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id` FROM system_user_role WHERE tenant_id=#{query.tenantId} /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectScopes; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:12; kind=select
SELECT `id`,`tenant_id`,`user_id`,`company_id`,`company_code`,`company_name`,`department_id`,`department_code`,`department_name`,`scope_role`,`is_primary`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM system_user_company_department_scope WHERE tenant_id=#{query.tenantId} /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectDirectory; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:14; kind=select
SELECT id,code,name,'COMPANY' AS kind FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE UNION ALL SELECT id,code,name,'DEPARTMENT' AS kind FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE UNION ALL SELECT id,code,name,'POST' AS kind FROM system_post WHERE tenant_id=#{query.tenantId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.selectAccountQuota; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:15; kind=select
SELECT account_count FROM system_tenant WHERE id=#{query.tenantId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.oneSourcePerTargetIndex; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:16; kind=select
SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='int_base_sync_binding' AND index_name='uk_int_base_sync_binding_target' AND non_unique=0;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.insertUser; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:5; kind=insert
INSERT INTO system_users (`id`,`username`,`nickname`,`remark`,`dept_id`,`post_ids`,`email`,`mobile`,`sex`,`avatar`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`,`employee_no`,`custom_info`,password) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.user.id},#{row.user.username},#{row.user.nickname},#{row.user.remark},#{row.user.deptId},#{row.user.postIds},#{row.user.email},#{row.user.mobile},#{row.user.sex},#{row.user.avatar},#{row.user.status},#{row.user.creator},#{row.user.createTime},#{row.user.updater},#{row.user.updateTime},#{row.user.deleted},#{row.user.tenantId},#{row.user.employeeNo},#{row.user.customInfo},#{row.user.initialPasswordHash}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.insertRole; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:8; kind=insert
INSERT INTO system_role (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.role.id},#{row.role.name},#{row.role.code},#{row.role.sort},#{row.role.dataScope},#{row.role.dataScopeDeptIds},#{row.role.status},#{row.role.type},#{row.role.remark},#{row.role.creator},#{row.role.createTime},#{row.role.updater},#{row.role.updateTime},#{row.role.deleted},#{row.role.tenantId}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.insertMembership; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:11; kind=insert
INSERT INTO system_user_role (`id`,`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.membership.id},#{row.membership.userId},#{row.membership.roleId},#{row.membership.creator},#{row.membership.createTime},#{row.membership.updater},#{row.membership.updateTime},#{row.membership.deleted},#{row.membership.tenantId}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.insertScope; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:13; kind=insert
INSERT INTO system_user_company_department_scope (`id`,`tenant_id`,`user_id`,`company_id`,`company_code`,`company_name`,`department_id`,`department_code`,`department_name`,`scope_role`,`is_primary`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.scope.id},#{row.scope.tenantId},#{row.scope.userId},#{row.scope.companyId},#{row.scope.companyCode},#{row.scope.companyName},#{row.scope.departmentId},#{row.scope.departmentCode},#{row.scope.departmentName},#{row.scope.scopeRole},#{row.scope.isPrimary},#{row.scope.effectiveFrom},#{row.scope.effectiveTo},#{row.scope.status},#{row.scope.version},#{row.scope.creator},#{row.scope.createTime},#{row.scope.updater},#{row.scope.updateTime},#{row.scope.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.insertPosts; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:16; kind=insert
INSERT INTO system_user_post (`id`,`user_id`,`post_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.id},#{row.userId},#{row.postId},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted},#{row.tenantId}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.updateUser; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:6; kind=update
UPDATE system_users SET `nickname`=#{row.user.nickname},`remark`=#{row.user.remark},`dept_id`=#{row.user.deptId},`post_ids`=#{row.user.postIds},`email`=#{row.user.email},`mobile`=#{row.user.mobile},`sex`=#{row.user.sex},`avatar`=#{row.user.avatar},`status`=#{row.user.status},`creator`=#{row.user.creator},`create_time`=#{row.user.createTime},`updater`=#{row.user.updater},`update_time`=#{row.user.updateTime},`employee_no`=#{row.user.employeeNo},`custom_info`=#{row.user.customInfo} WHERE tenant_id=#{context.tenantId} AND id=#{row.user.id} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P04AccountSyncTask$TargetMapper.updateRole; pms-module-integration/src/main/resources/mapper/basesync/P04TargetMapper.xml:9; kind=update
UPDATE system_role SET `name`=#{row.role.name},`sort`=#{row.role.sort},`remark`=#{row.role.remark},`creator`=#{row.role.creator},`create_time`=#{row.role.createTime},`updater`=#{row.role.updater},`update_time`=#{row.role.updateTime} WHERE tenant_id=#{context.tenantId} AND id=#{row.role.id} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
