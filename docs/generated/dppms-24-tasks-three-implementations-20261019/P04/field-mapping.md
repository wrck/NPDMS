# P04 原表逐字段承载清单

合并组件：P04, P05, P06, P07。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| t_user.user_id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P04 |
| t_user.user_name | varchar(25) / YES | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING；业务字段/正式关系 | P04 |
| t_user.password | varchar(100) / YES | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则；已批准排除 | P04 |
| t_user.create_by | varchar(25) / YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| t_user.create_time | datetime / NO | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| t_user.update_by | varchar(25) / YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| t_user.update_time | datetime / YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| t_user.status | smallint(1) / NO | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION；业务字段/正式关系 | P04 |
| t_user.needChangePwd | bit(1) / NO | system_users.custom_info.mustChangePassword | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user.loginErrorCount | int(1) / NO | system_users.custom_info.loginErrorCount | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user.isSysUser | smallint(1) / NO | C: system_users.custom_info.systemPresetUser；A/B历史未证明原值完整承接 | 2026-10-18用户明确为系统预设用户；C保留原值，不授予管理员权限；C已修复且真实预览通过；A/B不借用C结果 | P04 |
| t_user.userCustom1 | varchar(50) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user.userCustom2 | varchar(50) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user.userCustom3 | varchar(50) / YES | system_users.employee_no / custom_info.employmentAssignments | 经EHR真实工号核实；外部用户名不当工号；业务字段/正式关系 | P04 |
| t_user.userCustom4 | int(11) / YES | system_users.custom_info.employmentAssignments[].employeeReference | 作为EHR员工外键核对，保存已核实任职员工引用；不同创建链ID不能无条件当员工ID；未核实原值不宣称已保存；业务字段/正式关系 | P04 |
| t_user.userCustom5 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user_info.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P04 |
| t_user_info.workNo | varchar(25) / NO | system_users.employee_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.realName | varchar(50) / NO | system_users.nickname | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.eName | varchar(50) / YES | system_users.custom_info.englishName | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.compID | int(11) / NO | system_user_company_department_scope.company_id；system_user_company_department_scope.company_code；system_user_company_department_scope.company_name | 原公司外键经t_company及批准四码映射关联正式公司；P04只作账户组织参考，正式公司范围由P07写入。 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P04 |
| t_user_info.depID | int(11) / NO | system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name | 精确原部门/正式绑定，账号主部门按fnd优先、t补缺；P07另行承接获准公司部门范围，不用旧部门ID直写。；业务字段/正式关系 | P04 |
| t_user_info.jobID | int(11) / NO | system_users.post_ids（关联system_post） | 通过EHR岗位编码精确核对正式岗位；未匹配不伪造岗位；业务字段/正式关系 | P04 |
| t_user_info.reportTo | int(11) / YES | system_users.custom_info.employmentAssignments[].reportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.wfreportTo | int(11) / YES | system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.empStatus | int(11) / NO | system_users.custom_info.employmentAssignments[].employeeStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.jobStatus | int(11) / YES | system_users.custom_info.employmentAssignments[].positionStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.empType | int(11) / YES | system_users.custom_info.employmentAssignments[].employeeType | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.sex | smallint(1) / YES | system_users.sex | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.birthday | date / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user_info.email | varchar(50) / YES | system_users.email | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.mobile | varchar(50) / YES | system_users.mobile | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.telphone | varchar(50) / YES | system_users.custom_info.officePhone | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.avatar | varchar(500) / YES | system_users.avatar | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.remark | varchar(100) / YES | system_users.remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.state | int(11) / YES | system_users.custom_info.employmentAssignments[].historicalEmploymentState | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.user_id | int(11) / YES | system_users.id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P04 |
| t_user_info.custom1 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user_info.custom2 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P04 |
| t_user_info.custom3 | varchar(50) / YES | system_user_company_department_scope.department_code / 正式部门上下文 | 旧创建链明确是二级办事处编码，不必等于实际主部门；原341非空，同一公司配对保存，不新增custom3镜像。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P04 |
| t_user_info.custom4 | varchar(50) / YES | system_users.custom_info.employmentAssignments[].projectTypeRestrictions | 完整承接原项目类型准入值及已核实转换，历史限制不授予办事处或具体项目访问权；未知类型不得猜配。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_user_info.custom5 | varchar(4096) / YES | system_user_company_department_scope 部门范围 | 原341非空；与该条userinfo明确公司组合，去空去重/别名映射，不能跨公司套用。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P04 |
| fnd_user_info.id | int(8) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P04 |
| fnd_user_info.username | varchar(128) / NO | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING；业务字段/正式关系 | P04 |
| fnd_user_info.password | varchar(32) / NO | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则；已批准排除 | P04 |
| fnd_user_info.email | varchar(128) / NO | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。；USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS；业务字段/正式关系 | P04 |
| fnd_user_info.dpNo | varchar(25) / YES | system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name | 原办事处码经批准目录精确解析；P04形成主部门，P07形成获准范围，不据部门猜公司或扩大授权。；业务字段/正式关系 | P04 |
| fnd_user_info.realName | varchar(128) / NO | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P04 |
| fnd_user_info.roleIds | varchar(64) / YES | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P04 |
| fnd_user_info.isemail | int(11) / YES | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| fnd_user_info.status | int(1) / YES | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION；业务字段/正式关系 | P04 |
| fnd_user_info.defaultPage | varchar(255) / YES | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| fnd_user_info.pwdoverdue | datetime / YES | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| fnd_user_info.createBy | varchar(25) / YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| fnd_user_info.createTime | datetime / YES | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| fnd_user_info.updateBy | varchar(25) / YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| fnd_user_info.updateTime | datetime / YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P04 |
| fnd_user_info.effectiveFrom | datetime / YES | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| fnd_user_info.effectiveTo | datetime / YES | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| fnd_user_info.customInfo | json / YES | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE；业务字段/正式关系 | P04 |
| t_role.role_id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P05 |
| t_role.role_name | varchar(100) / YES | system_role.code | 按已裁决DppmsRoleCodes.T_CODES复用同义正式角色：项目经理/服务经理关联既有正式编码，超级管理员授平台超级管理员；不是所有原字符串无转换直写；业务字段/正式关系 | P05 |
| t_role.role_name_zn | varchar(100) / YES | system_role.name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P05 |
| t_role.home_page | varchar(100) / YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接；已批准排除 | P05 |
| t_role.priority | int(11) / YES | system_role.sort | 100-priority，沿已批准排序规则；业务字段/正式关系 | P05 |
| t_role.status | smallint(1) / YES | system_role.status | 停用来源角色不迁；启用1→正式0；超级管理员明确关联平台超级管理员角色；业务字段/正式关系 | P05 |
| t_role.create_by | varchar(25) / YES | system_role.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| t_role.create_time | datetime / YES | system_role.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| t_role.update_by | varchar(25) / YES | system_role.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| t_role.update_time | datetime / YES | system_role.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| t_role.remark | varchar(255) / YES | system_role.remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P05 |
| fnd_roles.id | int(6) / NO | system_role.code ＋ 来源绑定 | 按已裁决常量语义转换小驼峰字符串，复用DppmsRoleCodes.FND_CODES；绝不用原ID或常量名作为角色编码；技术身份及受控角色编码转换 | P05 |
| fnd_roles.roleName | varchar(64) / NO | system_role.name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P05 |
| fnd_roles.defaultPage | varchar(255) / YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接；已批准排除 | P05 |
| fnd_roles.status | int(1) / NO | system_role.status | 停用来源角色不迁；启用1→正式0；超级管理员明确关联平台超级管理员角色；业务字段/正式关系 | P05 |
| fnd_roles.effectiveFrom | datetime / YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接；已批准排除 | P05 |
| fnd_roles.effectiveTo | datetime / YES | 不迁入 | 用户保持system_role既有结构并明确不迁入；没有同义正式字段，不借备注承接；已批准排除 | P05 |
| fnd_roles.createBy | varchar(25) / YES | system_role.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| fnd_roles.createTime | datetime / YES | system_role.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| fnd_roles.updateBy | varchar(25) / YES | system_role.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| fnd_roles.updateTime | datetime / YES | system_role.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P05 |
| fnd_roles.roleRemark | varchar(200) / YES | system_role.remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P05 |
| t_user_role.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P06 |
| t_user_role.user_id | int(11) / NO | system_user_role.user_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系；业务字段/正式关系 | P06 |
| t_user_role.role_id | int(11) / NO | system_user_role.role_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系；业务字段/正式关系 | P06 |
| t_user_role.comp_id | int(11) / YES | system_user_company_department_scope.company_id；system_user_company_department_scope.company_code | NULL公司只迁用户角色不新增公司范围；非空按实际目录和四项显式码转换 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.id | int(8) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P06 |
| fnd_user_info.username | varchar(128) / NO | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.password | varchar(32) / NO | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则；已批准排除 | P06 |
| fnd_user_info.email | varchar(128) / NO | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。；USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.dpNo | varchar(25) / YES | system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name | 原办事处码经批准目录精确解析；P04形成主部门，P07形成获准范围，不据部门猜公司或扩大授权。 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.realName | varchar(128) / NO | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.roleIds | varchar(64) / YES | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P06 |
| fnd_user_info.isemail | int(11) / YES | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.status | int(1) / YES | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.defaultPage | varchar(255) / YES | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.pwdoverdue | datetime / YES | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.createBy | varchar(25) / YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.createTime | datetime / YES | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.updateBy | varchar(25) / YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.updateTime | datetime / YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.effectiveFrom | datetime / YES | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.effectiveTo | datetime / YES | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| fnd_user_info.customInfo | json / YES | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P06 |
| t_user.user_id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P07 |
| t_user.user_name | varchar(25) / YES | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.password | varchar(100) / YES | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则；已批准排除 | P07 |
| t_user.create_by | varchar(25) / YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.create_time | datetime / NO | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.update_by | varchar(25) / YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.update_time | datetime / YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.status | smallint(1) / NO | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.needChangePwd | bit(1) / NO | system_users.custom_info.mustChangePassword | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.loginErrorCount | int(1) / NO | system_users.custom_info.loginErrorCount | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.isSysUser | smallint(1) / NO | C: system_users.custom_info.systemPresetUser；A/B历史未证明原值完整承接 | 2026-10-18用户明确为系统预设用户；C保留原值，不授予管理员权限；C已修复且真实预览通过；A/B不借用C结果 | P07 |
| t_user.userCustom1 | varchar(50) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user.userCustom2 | varchar(50) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user.userCustom3 | varchar(50) / YES | system_users.employee_no / custom_info.employmentAssignments | 经EHR真实工号核实；外部用户名不当工号 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.userCustom4 | int(11) / YES | system_users.custom_info.employmentAssignments[].employeeReference | 作为EHR员工外键核对，保存已核实任职员工引用；不同创建链ID不能无条件当员工ID；未核实原值不宣称已保存 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user.userCustom5 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user_info.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P07 |
| t_user_info.workNo | varchar(25) / NO | system_users.employee_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.realName | varchar(50) / NO | system_users.nickname | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.eName | varchar(50) / YES | system_users.custom_info.englishName | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.compID | int(11) / NO | system_user_company_department_scope.company_id；system_user_company_department_scope.company_code；system_user_company_department_scope.company_name | 原公司外键经t_company及批准四码映射关联正式公司；P04只作账户组织参考，正式公司范围由P07写入。；业务字段/正式关系 | P07 |
| t_user_info.depID | int(11) / NO | system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name | 精确原部门/正式绑定，账号主部门按fnd优先、t补缺；P07另行承接获准公司部门范围，不用旧部门ID直写。；业务字段/正式关系 | P07 |
| t_user_info.jobID | int(11) / NO | system_users.post_ids（关联system_post） | 通过EHR岗位编码精确核对正式岗位；未匹配不伪造岗位 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.reportTo | int(11) / YES | system_users.custom_info.employmentAssignments[].reportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.wfreportTo | int(11) / YES | system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.empStatus | int(11) / NO | system_users.custom_info.employmentAssignments[].employeeStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.jobStatus | int(11) / YES | system_users.custom_info.employmentAssignments[].positionStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.empType | int(11) / YES | system_users.custom_info.employmentAssignments[].employeeType | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.sex | smallint(1) / YES | system_users.sex | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.birthday | date / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user_info.email | varchar(50) / YES | system_users.email | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.mobile | varchar(50) / YES | system_users.mobile | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.telphone | varchar(50) / YES | system_users.custom_info.officePhone | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.avatar | varchar(500) / YES | system_users.avatar | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.remark | varchar(100) / YES | system_users.remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.state | int(11) / YES | system_users.custom_info.employmentAssignments[].historicalEmploymentState | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.user_id | int(11) / YES | system_users.id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.custom1 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user_info.custom2 | int(11) / YES | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。；EXCLUDED_BY_USER_DECISION；已批准排除 | P07 |
| t_user_info.custom3 | varchar(50) / YES | system_user_company_department_scope.department_code / 正式部门上下文 | 旧创建链明确是二级办事处编码，不必等于实际主部门；原341非空，同一公司配对保存，不新增custom3镜像。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P07 |
| t_user_info.custom4 | varchar(50) / YES | system_users.custom_info.employmentAssignments[].projectTypeRestrictions | 完整承接原项目类型准入值及已核实转换，历史限制不授予办事处或具体项目访问权；未知类型不得猜配。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_info.custom5 | varchar(4096) / YES | system_user_company_department_scope 部门范围 | 原341非空；与该条userinfo明确公司组合，去空去重/别名映射，不能跨公司套用。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P07 |
| fnd_user_info.id | int(8) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P07 |
| fnd_user_info.username | varchar(128) / NO | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。；USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.password | varchar(32) / NO | 不迁入 | 旧认证凭据不读取、不迁入、不存JSON；沿新平台受控激活/认证规则；已批准排除 | P07 |
| fnd_user_info.email | varchar(128) / NO | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。；USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.dpNo | varchar(25) / YES | system_users.dept_id；system_user_company_department_scope.department_id；system_user_company_department_scope.department_code；system_user_company_department_scope.department_name | 原办事处码经批准目录精确解析；P04形成主部门，P07形成获准范围，不据部门猜公司或扩大授权。；业务字段/正式关系 | P07 |
| fnd_user_info.realName | varchar(128) / NO | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.roleIds | varchar(64) / YES | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。；EXISTING_FORMAL_FIELD_OR_OWNER_REUSE；业务字段/正式关系 | P07 |
| fnd_user_info.isemail | int(11) / YES | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.status | int(1) / YES | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。；USER_APPROVED_STATUS_CONVERSION 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.defaultPage | varchar(255) / YES | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.pwdoverdue | datetime / YES | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.createBy | varchar(25) / YES | system_users.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.createTime | datetime / YES | system_users.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.updateBy | varchar(25) / YES | system_users.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.updateTime | datetime / YES | system_users.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.effectiveFrom | datetime / YES | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.effectiveTo | datetime / YES | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| fnd_user_info.customInfo | json / YES | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。；USER_APPROVED_FORMAL_JSON_ATTRIBUTE 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_role.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P07 |
| t_user_role.user_id | int(11) / NO | system_user_role.user_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_role.role_id | int(11) / NO | system_user_role.role_id | 按已批准账号/角色绑定关联；同一用户＋角色共用正式关系 本任务只作来源身份或范围参考；实际写入属于清单内相应账号/角色/范围任务。；跨任务参考/不在本任务重复写入 | P07 |
| t_user_role.comp_id | int(11) / YES | system_user_company_department_scope.company_id；system_user_company_department_scope.company_code | NULL公司只迁用户角色不新增公司范围；非空按实际目录和四项显式码转换；业务字段/正式关系 | P07 |
| fnd_user_power.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P07 |
| fnd_user_power.fndUserId | int(11) / YES | system_user_company_department_scope.user_id | 经P04 fnd_user_info来源绑定解析正式用户；以原外键为准，不以冗余username另配账号。；业务字段/正式关系 | P07 |
| fnd_user_power.username | varchar(25) / YES | 不用于关联 | 以fndUserId外键为准；冗余名称差异列明，不另关联账号；已批准不采用冗余值 | P07 |
| fnd_user_power.areapower | varchar(4096) / YES | system_user_company_department_scope.department_id；system_user_company_department_scope.department_code | 跨公司限定001、002；空areapower109条明确排除：空范围没有附加授权事实；konglinghui_supp范围不迁；内部外部仅按项目授予查看权；业务字段/正式关系 | P07 |
| fnd_user_power.createTime | datetime / YES | system_user_company_department_scope.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P07 |
| fnd_user_power.createBy | varchar(25) / YES | system_user_company_department_scope.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P07 |
| fnd_user_power.updateTime | datetime / YES | system_user_company_department_scope.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P07 |
| fnd_user_power.updateBy | varchar(25) / YES | system_user_company_department_scope.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P07 |
| fnd_user_power.effectiveFrom | datetime / YES | system_user_company_department_scope.effective_from | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P07 |
| fnd_user_power.effectiveTo | datetime / YES | system_user_company_department_scope.effective_to | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P07 |
