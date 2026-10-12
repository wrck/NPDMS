# system_users 联合整表字段裁决

用户已批准：本表仅新增 employee_no、custom_info；任职资料、多公司任职及项目类型限制按明确业务属性承接。全部60原字段列明；两列旧密码不读取，其余58列已只读核对。8个正式部门已按裁决补齐。用户任务2108524822998773762、运行2108539862736879617成功读取1566条并合并迁入1176个正式账号；角色/范围未授予。实库核对发现557处原NULL更新人被框架填成“1”，用户已批准仅对这些明确目标进行受控纠正；普通同步保持人工变化阻断。详见[历史更新人纠正](2026-10-09-dppms-user-null-updater-correction.md)。纠正和主管引用尚在处理，不能将运行SUCCESS表述为整体验收完成。

已批准：主账号原更新时间为空时用原创建时间初始化；原NULL保留来源证据。内外用户均按具体项目授权，公司/部门范围不自动授予办事处项目访问。

## 既有20列与已批准处理

| 字段 | 当前定义 | 承接/限制 |
|---|---|---|
| `id` | `bigint` | 正式用户主键，全部来源身份用既有迁移绑定关联，不直接套用旧ID。 |
| `username` | `varchar(30)` | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。 |
| `password` | `varchar(100)` | 旧值不读取、不迁入；受控认证激活，不使用共享默认密码。 |
| `nickname` | `varchar(30)` | 同人fnd.realName优先、t_user_info.realName补缺；用户确认w00343、j04650、l02826三项例外，姓名及工号取EHR员工表。既有容量足够。 |
| `remark` | `varchar(500)` | 仅已有同义备注；不压入无法匹配的字段。 |
| `dept_id` | `bigint` | 按原部门编码/正式绑定解析主部门；多公司任职事实不压成单一部门。 |
| `post_ids` | `varchar(255)` | EHR岗位与system_post业务编码精确关联，未匹配不造伪岗位。 |
| `email` | `varchar(50)` | 同人fnd优先、t_*补缺；复用既有容量及格式规则。用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 |
| `mobile` | `varchar(11)` | 来源手机号仅按已核实规则去多余ASCII空格；保留原值证据，不截断或猜补。用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 |
| `sex` | `tinyint` | 按原性别定义转换，不混用t_user_info的0女与EHR的2女。 |
| `avatar` | `varchar(512)` | 复用正式头像字段，源本批全NULL，不造头像。 |
| `status` | `tinyint` | 用户批准同人fnd为主、t补缺；原1→启用0、0→停用1；t独有原2→停用1，custom_info保存原锁定值；未知状态阻断。 |
| `login_ip` | `varchar(50)` | 没有对应历史登录事实，不伪造。 |
| `login_date` | `datetime` | 没有对应历史登录事实，不把迁移时间当登录。 |
| `creator` | `varchar(64)` | 来源账号身份关联正式用户，自引用及互相引用在批次内解析；原NULL/空值保留。被范围排除的旧admin仍有99处选中账号审计引用，正式操作者映射待用户裁决。 |
| `create_time` | `datetime` | 主账号原历史时间；本次运行另记审计。 |
| `updater` | `varchar(64)` | 同creator身份关联；不得替换成迁移执行人。旧admin历史身份关联待裁决。 |
| `update_time` | `datetime` | 主账号原更新时间；用户批准原值NULL时仅用原创建时间初始化，原NULL列入来源证据，不用运行时间。 |
| `deleted` | `bit(1)` | 停用不推导逻辑删除。 |
| `tenant_id` | `bigint` | 既有租户隔离，身份与工号匹配不跨租户。 |

## 仅两项新增结构

| 字段 | 定义 | 业务含义 |
|---|---|---|
| `employee_no` | `VARCHAR(50) NULL` | 真实内部工号；外部手工workNo=username不写本字段，原身份仍经来源绑定关联。 |
| `custom_info` | `JSON NULL` | 账号扩展业务属性；完整保存原customInfo对象，并以独立明确属性承接用户逐项批准的其他账号字段。旧认证历史不控制新凭据。 |

已撤回列提案：`account_type`、`english_name`、`office_phone`、`email_notification_enabled`、`effective_from`、`effective_to`。不得进入SQL结构变更。原提案保留在JSON审查证据中。

## 全部60个原字段

| 原表.字段 | 正式承接/排除 | 处理与原因 | 当前裁决状态 |
|---|---|---|---|
| `t_user.user_id` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.user_name` | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。 | USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING |
| `t_user.password` | 排除旧凭据；使用受控认证/激活 | 已有身份规则明确旧MD5类凭据不迁入，读取列、证据和日志均排除。排除原因明列，不将旧凭据保存到JSON。 | EXCLUDED_BY_CONFIRMED_CREDENTIAL_RULE |
| `t_user.create_by` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.create_time` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.update_by` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.update_time` | create_time/update_time | 用户批准所选主账号原更新时间NULL时用原创建时间初始化；原NULL保留来源证据，其他历史时间不变。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.status` | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。 | USER_APPROVED_STATUS_CONVERSION |
| `t_user.needChangePwd` | system_users.custom_info.mustChangePassword | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user.loginErrorCount` | system_users.custom_info.loginErrorCount | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user.isSysUser` | 公司角色授权的来源范围判定 | 旧Shiro中非0绕过公司过滤，是跨公司授权标记；不等于SERVICE账号。2条-1角色来自该类账号，不能默认授予新平台超级管理员。 | AUTHORIZATION_ADJUDICATION_PENDING |
| `t_user.userCustom1` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user.userCustom2` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user.userCustom3` | 工号/账号别名的来源关联 | EHR创建链填workNo；手工外部创建链填username。原341非空，联合人员目录分类，不新增user_custom_3。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.userCustom4` | 人员目录与旧流程身份映射 | 原340非零；EHR创建链填empID，手工创建填账号ID。是不同来源身份，不能无条件当员工ID或新任务办理人。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user.userCustom5` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user_info.id` | 人员/账号公司上下文的来源身份绑定 | 旧代码以人员记录ID参与旧流程身份，不等于新system_users.id；保留身份映射，不重新激活旧工作流。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.workNo` | system_users.employee_no（真实内部工号）；外部账号别名/身份绑定 | 旧手工创建外部账号会把用户名填入workNo，不能把这类值当内部工号。 | USER_APPROVED_EMPLOYEE_NO_REFERENCE_VERIFICATION_REQUIRED |
| `t_user_info.realName` | system_users.nickname | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.eName` | system_users.custom_info.englishName | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.compID` | system_user_company_department_scope.company_id/code/name | 保留用户所属公司事实，经实际来源公司解析及01→001等显式规则；不是t_user_role的授权公司替代值。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.depID` | system_users.dept_id；system_user_company_department_scope.department_id/code/name | 通过来源部门目录和EHR同步绑定解析，不复用旧ID，不由部门猜公司；主部门选取与多公司资料冲突需明确。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.jobID` | system_post / system_user_post（条件性复用） | 先核EHR岗位与正式岗位的业务编码/含义；无对应不创建伪岗位，不把jobStatus混成岗位角色。 | OWNER_REFERENCE_MATCH_PENDING |
| `t_user_info.reportTo` | system_users.custom_info.employmentAssignments[].reportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.wfreportTo` | system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.empStatus` | system_users.custom_info.employmentAssignments[].employeeStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.jobStatus` | system_users.custom_info.employmentAssignments[].positionStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.empType` | system_users.custom_info.employmentAssignments[].employeeType | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.sex` | system_users.sex | 按现有性别枚举转换，旧1男/0女，不直接套EHR的1男/2女。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.birthday` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user_info.email` | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `t_user_info.mobile` | system_users.mobile | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `t_user_info.telphone` | system_users.custom_info.officePhone | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.avatar` | system_users.avatar | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.remark` | system_users.remark | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.state` | system_users.custom_info.employmentAssignments[].historicalEmploymentState | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.user_id` | system_users.id | 经t_user来源绑定关联；明确一人可有多公司资料，三条无账号关联不造用户名/账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.custom1` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user_info.custom2` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `t_user_info.custom3` | system_user_company_department_scope.department_code / 正式部门上下文 | 旧创建链明确是二级办事处编码，不必等于实际主部门；原341非空，同一公司配对保存，不新增custom3镜像。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `t_user_info.custom4` | system_users.custom_info.employmentAssignments[].projectTypeRestrictions | 完整承接原项目类型准入值及已核实转换，历史限制不授予办事处或具体项目访问权；未知类型不得猜配。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `t_user_info.custom5` | system_user_company_department_scope 部门范围 | 原341非空；与该条userinfo明确公司组合，去空去重/别名映射，不能跨公司套用。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.id` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.username` | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。 | USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING |
| `fnd_user_info.password` | 排除旧凭据；使用受控认证/激活 | 已有身份规则明确旧MD5类凭据不迁入，读取列、证据和日志均排除。排除原因明列，不将旧凭据保存到JSON。 | EXCLUDED_BY_CONFIRMED_CREDENTIAL_RULE |
| `fnd_user_info.email` | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `fnd_user_info.dpNo` | system_user_company_department_scope.department_id/code/name | 自身部门加入可服务/范围集合；不是工号，没有公司事实时不伪配公司。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.realName` | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.roleIds` | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.isemail` | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `fnd_user_info.status` | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。 | USER_APPROVED_STATUS_CONVERSION |
| `fnd_user_info.defaultPage` | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `fnd_user_info.pwdoverdue` | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `fnd_user_info.createBy` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.createTime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.updateBy` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.updateTime` | create_time/update_time | 用户批准所选主账号原更新时间NULL时用原创建时间初始化；原NULL保留来源证据，其他历史时间不变。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fnd_user_info.effectiveFrom` | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `fnd_user_info.effectiveTo` | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `fnd_user_info.customInfo` | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |

## 当前裁决与引用关联

账号范围、联系人重复、登录兼容、旧admin审计关联、5条NULL公司角色关系及3条孤立资料排除均已裁决。人员/公司/部门/岗位仍须通过正式Owner及来源目录核实，不能仅据来源同数值ID关联。

孤立t_user_info 1000186、1000188、1000189明确不迁入：无账号外键且不在已批准账号范围；其他孤立资料仍登记待关联，不套用本批排除。

[联系人和登录名完整清单](2026-10-09-dppms-user-contact-and-login-decision.md)

## 实现及验证边界

SYSTEM Owner复用正式字段，密码/登录事实不进入迁移DTO；创建使用独立不可获知的激活材料，现存凭据保持。整批写入与来源绑定/运行证据共用事务，重放检查人工修改，普通新增/变更联系方式保持唯一规则，迁移允许共用联系值，既有租户容量规则生效。用户JSON属性4项检查、账号计划/事务14项检查已通过；真实MySQL迁移、服务切换和浏览器验收尚未执行。

## 最新账号范围与联系字段裁决

用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容已批准。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin仅审计关联当前admin ID1已批准。

用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。

完整选中/排除证据见[账号范围清单](2026-10-09-dppms-user-selected-scope-decision.md)。隔离验证33项通过；正式账号Owner14项包含真实H2 Mapper事务、回滚、共用联系人落正式列和停用撤销会话调用。尚未执行实际MySQL账号写入或浏览器验收。

实际Java提取模板已在来源库只读执行，60个原字段中的58个获准读取字段无遗漏，旧密码两列排除。人员JSON不要求不存在的t_user_info.empID，使用经核实的账号/EHR人员关系；修正后的4项JSON检查通过。目录引用核对见[公司与人员引用审查](2026-10-09-dppms-user-directory-reference-audit.md)。

短信登录补齐既有停用规则：停用账号记录拒绝日志，不签发令牌或更新成功登录时间；启用且唯一匹配的账号沿原短信流程。两项针对性认证检查及五项联系字段检查通过；此修复未部署到当前后端。

## 三项姓名及工号例外裁决

用户确认三组同人，nickname及employee_no取实际ehr_employee.name/workNo，经ehr_login员工外键核实；其余账号fnd主、t补缺规则保持。原名字差异保留来源证据，不能用登录目录冗余姓名覆盖员工表姓名。

| 账号 | EHR员工外键 | 正式nickname | 正式employee_no |
|---|---:|---|---|
| w00343 | 74 | 王俊珂 | 00343 |
| j04650 | 4804 | 金燕玲 | 04650 |
| l02826 | 2770 | 乐征侨 | 02826 |

对应原姓名差异见[目录引用核对](2026-10-09-dppms-user-directory-reference-audit.md)。这是已批准字段的映射例外，没有新增表或列；实际迁移、同步接入及落库仍待剩余业务裁决和运行验证。

## 最后五项用户字段与关联裁决

- 保留选中*_ss、*_supp原登录名；登录与账号维护输入兼容这些形式。注册规则不在此裁决范围。
- 旧admin不迁入账号资料，仅将历史创建/更新人关联当前admin ID1；不导入旧角色或授权；目标缺失或绑定改变时阻断。
- 用户明确例外：原值@dptech.com直接承接正式email，不置空、不改写、不用custom_info替代。普通新建/修改邮箱格式校验保持。
- 正式email仅去除来源尾部制表符，原值完整留来源证据。
- z04214、w04218、y04223、w04261、z04264迁入正式用户角色关系，原公司NULL留证据；不新增公司或办事处授权。

以上覆盖此前同项待裁决建议；来源原始记录不改写。代码接入及正式迁移结果另行核验，不代表已经落库。

## 当前执行边界（2026-10-09）

用户限定仅迁移首字母+工号、*_ss、*_supp，保留原字符串；t329条、fnd866条及330条任职合并1176个账号。登录/账号维护兼容已批准；旧admin仅99处历史审计引用关联当前admin ID1，不迁入资料、角色或范围。

实际原表解析确认1176个账号、1525个来源身份、823个可核实EHR工号；角色原关系t792条、fnd1140条。尚未部署或正式写入，迁移绑定、真实MySQL承接与页面验收仍须完成。
