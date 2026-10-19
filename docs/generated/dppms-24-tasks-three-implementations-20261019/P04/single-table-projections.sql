-- P04 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。
-- 不投影 t_user.password：已批准排除

-- SOURCE t_user; dialect=MYSQL
SELECT
  -- t_user.user_id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`user_id` AS `raw__user_id`,
  -- t_user.user_name -> system_users.username; 跨任务参考/不在本任务重复写入
  s.`user_name` AS `raw__username`,
  -- t_user.create_by -> system_users.creator; 跨任务参考/不在本任务重复写入
  s.`create_by` AS `raw__creator`,
  -- t_user.create_time -> system_users.create_time; 跨任务参考/不在本任务重复写入
  s.`create_time` AS `raw__create_time`,
  -- t_user.update_by -> system_users.updater; 跨任务参考/不在本任务重复写入
  s.`update_by` AS `raw__updater`,
  -- t_user.update_time -> system_users.update_time; 跨任务参考/不在本任务重复写入
  s.`update_time` AS `raw__update_time`,
  -- t_user.status -> system_users.status; 跨任务参考/不在本任务重复写入
  s.`status` AS `raw__status`,
  -- t_user.needChangePwd -> system_users.custom_info.mustChangePassword; 跨任务参考/不在本任务重复写入
  s.`needChangePwd` AS `raw__needChangePwd`,
  -- t_user.loginErrorCount -> system_users.custom_info.loginErrorCount; 跨任务参考/不在本任务重复写入
  s.`loginErrorCount` AS `raw__loginErrorCount`,
  -- t_user.isSysUser -> C: system_users.custom_info.systemPresetUser；A/B历史未证明原值完整承接; 正式字段映射缺口
  s.`isSysUser` AS `raw__isSysUser`,
  -- t_user.userCustom1 -> 不迁入; 已批准排除
  s.`userCustom1` AS `raw__userCustom1`,
  -- t_user.userCustom2 -> 不迁入; 已批准排除
  s.`userCustom2` AS `raw__userCustom2`,
  -- t_user.userCustom3 -> system_users.employee_no / custom_info.employmentAssignments; 跨任务参考/不在本任务重复写入
  s.`userCustom3` AS `raw__employee_no`,
  -- t_user.userCustom4 -> system_users.custom_info.employmentAssignments[].employeeReference; 跨任务参考/不在本任务重复写入
  s.`userCustom4` AS `raw__userCustom4`,
  -- t_user.userCustom5 -> 不迁入; 已批准排除
  s.`userCustom5` AS `raw__userCustom5`
FROM `t_user` s;

-- SOURCE t_user_info; dialect=MYSQL
SELECT
  -- t_user_info.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- t_user_info.workNo -> system_users.employee_no; 跨任务参考/不在本任务重复写入
  s.`workNo` AS `raw__employee_no`,
  -- t_user_info.realName -> system_users.nickname; 跨任务参考/不在本任务重复写入
  s.`realName` AS `raw__nickname`,
  -- t_user_info.eName -> system_users.custom_info.englishName; 跨任务参考/不在本任务重复写入
  s.`eName` AS `raw__eName`,
  -- t_user_info.compID -> system_user_company_department_scope.company_id；system_user_company_department_scope.company_code；system_user_company_department_scope.company_name; 业务字段/正式关系
  s.`compID` AS `raw__company_id`,
  -- t_user_info.depID -> system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name; 业务字段/正式关系
  s.`depID` AS `raw__dept_id`,
  -- t_user_info.jobID -> system_users.post_ids（关联system_post）; 跨任务参考/不在本任务重复写入
  s.`jobID` AS `raw__post_ids`,
  -- t_user_info.reportTo -> system_users.custom_info.employmentAssignments[].reportingEmployeeReference; 跨任务参考/不在本任务重复写入
  s.`reportTo` AS `raw__reportTo`,
  -- t_user_info.wfreportTo -> system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference; 跨任务参考/不在本任务重复写入
  s.`wfreportTo` AS `raw__wfreportTo`,
  -- t_user_info.empStatus -> system_users.custom_info.employmentAssignments[].employeeStatus; 跨任务参考/不在本任务重复写入
  s.`empStatus` AS `raw__empStatus`,
  -- t_user_info.jobStatus -> system_users.custom_info.employmentAssignments[].positionStatus; 跨任务参考/不在本任务重复写入
  s.`jobStatus` AS `raw__jobStatus`,
  -- t_user_info.empType -> system_users.custom_info.employmentAssignments[].employeeType; 跨任务参考/不在本任务重复写入
  s.`empType` AS `raw__empType`,
  -- t_user_info.sex -> system_users.sex; 跨任务参考/不在本任务重复写入
  s.`sex` AS `raw__sex`,
  -- t_user_info.birthday -> 不迁入; 已批准排除
  s.`birthday` AS `raw__birthday`,
  -- t_user_info.email -> system_users.email; 跨任务参考/不在本任务重复写入
  s.`email` AS `raw__email`,
  -- t_user_info.mobile -> system_users.mobile; 跨任务参考/不在本任务重复写入
  s.`mobile` AS `raw__mobile`,
  -- t_user_info.telphone -> system_users.custom_info.officePhone; 跨任务参考/不在本任务重复写入
  s.`telphone` AS `raw__telphone`,
  -- t_user_info.avatar -> system_users.avatar; 跨任务参考/不在本任务重复写入
  s.`avatar` AS `raw__avatar`,
  -- t_user_info.remark -> system_users.remark; 跨任务参考/不在本任务重复写入
  s.`remark` AS `raw__remark`,
  -- t_user_info.state -> system_users.custom_info.employmentAssignments[].historicalEmploymentState; 跨任务参考/不在本任务重复写入
  s.`state` AS `raw__state`,
  -- t_user_info.user_id -> system_users.id; 跨任务参考/不在本任务重复写入
  s.`user_id` AS `raw__t_user_info_user_id`,
  -- t_user_info.custom1 -> 不迁入; 已批准排除
  s.`custom1` AS `raw__custom1`,
  -- t_user_info.custom2 -> 不迁入; 已批准排除
  s.`custom2` AS `raw__custom2`,
  -- t_user_info.custom3 -> system_user_company_department_scope.department_code / 正式部门上下文; 业务字段/正式关系
  s.`custom3` AS `raw__custom3`,
  -- t_user_info.custom4 -> system_users.custom_info.employmentAssignments[].projectTypeRestrictions; 跨任务参考/不在本任务重复写入
  s.`custom4` AS `raw__custom4`,
  -- t_user_info.custom5 -> system_user_company_department_scope 部门范围; 业务字段/正式关系
  s.`custom5` AS `raw__custom5`
FROM `t_user_info` s;
-- 不投影 fnd_user_info.password：已批准排除

-- SOURCE fnd_user_info; dialect=MYSQL
SELECT
  -- fnd_user_info.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fnd_user_info.username -> system_users.username; 跨任务参考/不在本任务重复写入
  s.`username` AS `raw__username`,
  -- fnd_user_info.email -> system_users.email; 跨任务参考/不在本任务重复写入
  s.`email` AS `raw__email`,
  -- fnd_user_info.dpNo -> system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name; 业务字段/正式关系
  s.`dpNo` AS `raw__dept_id`,
  -- fnd_user_info.realName -> system_users.nickname; 跨任务参考/不在本任务重复写入
  s.`realName` AS `raw__nickname`,
  -- fnd_user_info.roleIds -> system_user_role / system_user_company_department_scope 的语义角色; 业务字段/正式关系
  s.`roleIds` AS `raw__roleIds`,
  -- fnd_user_info.isemail -> system_users.custom_info.emailNotificationEnabled; 跨任务参考/不在本任务重复写入
  s.`isemail` AS `raw__isemail`,
  -- fnd_user_info.status -> system_users.status; 跨任务参考/不在本任务重复写入
  s.`status` AS `raw__status`,
  -- fnd_user_info.defaultPage -> system_users.custom_info.defaultPage; 跨任务参考/不在本任务重复写入
  s.`defaultPage` AS `raw__defaultPage`,
  -- fnd_user_info.pwdoverdue -> system_users.custom_info.passwordExpireTime; 跨任务参考/不在本任务重复写入
  s.`pwdoverdue` AS `raw__pwdoverdue`,
  -- fnd_user_info.createBy -> system_users.creator; 跨任务参考/不在本任务重复写入
  s.`createBy` AS `raw__creator`,
  -- fnd_user_info.createTime -> system_users.create_time; 跨任务参考/不在本任务重复写入
  s.`createTime` AS `raw__create_time`,
  -- fnd_user_info.updateBy -> system_users.updater; 跨任务参考/不在本任务重复写入
  s.`updateBy` AS `raw__updater`,
  -- fnd_user_info.updateTime -> system_users.update_time; 跨任务参考/不在本任务重复写入
  s.`updateTime` AS `raw__update_time`,
  -- fnd_user_info.effectiveFrom -> system_users.custom_info.effectiveFrom; 跨任务参考/不在本任务重复写入
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- fnd_user_info.effectiveTo -> system_users.custom_info.effectiveTo; 跨任务参考/不在本任务重复写入
  s.`effectiveTo` AS `raw__effectiveTo`,
  -- fnd_user_info.customInfo -> system_users.custom_info.customInfo; 跨任务参考/不在本任务重复写入
  s.`customInfo` AS `raw__customInfo`
FROM `fnd_user_info` s;

-- SOURCE t_role; dialect=MYSQL
SELECT
  -- t_role.role_id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`role_id` AS `raw__role_id`,
  -- t_role.role_name -> system_role.code; 业务字段/正式关系
  s.`role_name` AS `raw__code`,
  -- t_role.role_name_zn -> system_role.name; 业务字段/正式关系
  s.`role_name_zn` AS `raw__name`,
  -- t_role.home_page -> 不迁入; 已批准排除
  s.`home_page` AS `raw__home_page`,
  -- t_role.priority -> system_role.sort; 业务字段/正式关系
  s.`priority` AS `raw__sort`,
  -- t_role.status -> system_role.status; 业务字段/正式关系
  s.`status` AS `raw__status`,
  -- t_role.create_by -> system_role.creator; 业务字段/正式关系
  s.`create_by` AS `raw__creator`,
  -- t_role.create_time -> system_role.create_time; 业务字段/正式关系
  s.`create_time` AS `raw__create_time`,
  -- t_role.update_by -> system_role.updater; 业务字段/正式关系
  s.`update_by` AS `raw__updater`,
  -- t_role.update_time -> system_role.update_time; 业务字段/正式关系
  s.`update_time` AS `raw__update_time`,
  -- t_role.remark -> system_role.remark; 业务字段/正式关系
  s.`remark` AS `raw__remark`
FROM `t_role` s;

-- SOURCE fnd_roles; dialect=MYSQL
SELECT
  -- fnd_roles.id -> system_role.code ＋ 来源绑定; 技术身份及受控角色编码转换
  s.`id` AS `raw__code`,
  -- fnd_roles.roleName -> system_role.name; 业务字段/正式关系
  s.`roleName` AS `raw__name`,
  -- fnd_roles.defaultPage -> 不迁入; 已批准排除
  s.`defaultPage` AS `raw__defaultPage`,
  -- fnd_roles.status -> system_role.status; 业务字段/正式关系
  s.`status` AS `raw__status`,
  -- fnd_roles.effectiveFrom -> 不迁入; 已批准排除
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- fnd_roles.effectiveTo -> 不迁入; 已批准排除
  s.`effectiveTo` AS `raw__effectiveTo`,
  -- fnd_roles.createBy -> system_role.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- fnd_roles.createTime -> system_role.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- fnd_roles.updateBy -> system_role.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- fnd_roles.updateTime -> system_role.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- fnd_roles.roleRemark -> system_role.remark; 业务字段/正式关系
  s.`roleRemark` AS `raw__remark`
FROM `fnd_roles` s;

-- SOURCE t_user_role; dialect=MYSQL
SELECT
  -- t_user_role.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- t_user_role.user_id -> system_user_role.user_id; 跨任务参考/不在本任务重复写入
  s.`user_id` AS `raw__user_id`,
  -- t_user_role.role_id -> system_user_role.role_id; 跨任务参考/不在本任务重复写入
  s.`role_id` AS `raw__role_id`,
  -- t_user_role.comp_id -> system_user_company_department_scope.company_id；system_user_company_department_scope.company_code; 业务字段/正式关系
  s.`comp_id` AS `raw__company_id`
FROM `t_user_role` s;

-- SOURCE fnd_user_power; dialect=MYSQL
SELECT
  -- fnd_user_power.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fnd_user_power.fndUserId -> system_user_company_department_scope.user_id; 业务字段/正式关系
  s.`fndUserId` AS `raw__user_id`,
  -- fnd_user_power.username -> 不用于关联; 已批准不采用冗余值
  s.`username` AS `raw__username`,
  -- fnd_user_power.areapower -> system_user_company_department_scope.department_id；system_user_company_department_scope.department_code; 业务字段/正式关系
  s.`areapower` AS `raw__department_id`,
  -- fnd_user_power.createTime -> system_user_company_department_scope.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- fnd_user_power.createBy -> system_user_company_department_scope.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- fnd_user_power.updateTime -> system_user_company_department_scope.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- fnd_user_power.updateBy -> system_user_company_department_scope.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- fnd_user_power.effectiveFrom -> system_user_company_department_scope.effective_from; 业务字段/正式关系
  s.`effectiveFrom` AS `raw__effective_from`,
  -- fnd_user_power.effectiveTo -> system_user_company_department_scope.effective_to; 业务字段/正式关系
  s.`effectiveTo` AS `raw__effective_to`
FROM `fnd_user_power` s;
