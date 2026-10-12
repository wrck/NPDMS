# 用户角色联合承接：t_* 与 fnd_* 重新规划

已按用户裁决撤回此前system_users八列、system_role三列和pms_user_service_scope新表提案。角色结合t_role，服务范围改为复用system_user_company_department_scope。旧提案只作为撤回记录保留，未形成结构批准。

现有迁移与同步功能：预览→业务Owner写入→绑定与审计→分页重试/重放；10项目验证后按同配置扩大历史范围，不依赖手工DML。

## 既有业务表职责

- system_users：统一账号与通用个人信息；内部员工工号需真实EHR身份，旧手工workNo不能直接充作工号。
- system_role：以t_role定义为基础，角色码与中文名分别对应code/name；fnd角色逐项语义匹配。
- system_user_role：正式账号与角色关系；不能将来源公司限定关系直接提升成全局权限。
- system_user_company_department_scope：同条保存公司、部门、角色上下文；用户已确认可服务范围与授权范围同义，统一承接范围授权，不新增范围类型。

## 实库与旧实现核对

七表共96原字段。t_user=341、t_user_info=345、t_role=14、t_user_role=829、fnd_user_info=880、fnd_roles=18、fnd_user_power=880。旧密码值未读取。

t_user_info不是简单的一人一条：340个账号一条，1个账号两条；旧代码明确支持按公司选人员资料，另3条无账号关联。193个t_user账号能由工号匹配EHR；149条已关联资料未匹配EHR，旧创建链会给外部账号填用户名作为workNo。不能把所有t_user_info.workNo都当真实工号。

isSysUser非0在旧Shiro中代表跨公司过滤，不等于服务账号。来源-1公司的2条关系均为该类账号；NULL公司的5条来自普通账号，不默认全公司授权。

19条fnd/t账号候选重合中有6条启停冲突；用户确认核实同一人后以fnd为主、t_*补缺，六项状态按fnd。admin/sse的身份差异另待裁决。fnd与t角色中文完全同名仅3项；其他角色及字段差异继续登记，不按ID或近似名称合并。

## system_user_company_department_scope 整表规划（范围与访问方式已确认，账号角色映射待裁决）

用户确认“可服务范围”和“授权范围”是一个含义。撤回scope_type及为区分服务覆盖提出的四列可空变更；未实施任何该类结构修改。不建pms_user_service_scope。

fnd独有账号跨公司范围明确为001、002，逐公司复用既有公司、部门、角色关系，不授权其他公司。内部、外部用户都只按明确具体项目授予权限，范围不自动产生办事处内项目权限。功能、字段和租户约束继续生效；账号分类与角色映射仍待核实。

| 本表字段 | 当前定义/可空 | 本轮处理 |
|---|---|---|
| `id` | `bigint` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `tenant_id` | `bigint` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `user_id` | `bigint` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `company_id` | `bigint` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `company_code` | `varchar(64)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `company_name` | `varchar(128)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `department_id` | `bigint` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `department_code` | `varchar(64)` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `department_name` | `varchar(128)` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `scope_role` | `varchar(32)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `is_primary` | `bit(1)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `effective_from` | `datetime(3)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `effective_to` | `datetime(3)` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `status` | `tinyint` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `version` | `int unsigned` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `creator` | `varchar(64)` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `create_time` | `datetime(3)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `updater` | `varchar(64)` / YES | 复用既有正式字段；本轮无新增列或可空变更 |
| `update_time` | `datetime(3)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |
| `deleted` | `bit(1)` / NO | 复用既有正式字段；本轮无新增列或可空变更 |

## t_user 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `user_id` | `int(11)` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `user_name` | `varchar(25)` | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容仍待裁决。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin历史审计关联另待指定。 | USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING |
| `password` | `varchar(100)` | 排除旧凭据；使用受控认证/激活 | 已有身份规则明确旧MD5类凭据不迁入，读取列、证据和日志均排除。排除原因明列，不将旧凭据保存到JSON。 | EXCLUDED_BY_CONFIRMED_CREDENTIAL_RULE |
| `create_by` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `create_time` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `update_by` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `update_time` | `datetime` | create_time/update_time | 用户批准所选主账号原更新时间NULL时用原创建时间初始化；原NULL保留来源证据，其他历史时间不变。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `status` | `smallint(1)` | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。 | USER_APPROVED_STATUS_CONVERSION |
| `needChangePwd` | `bit(1)` | system_users.custom_info.mustChangePassword | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `loginErrorCount` | `int(1)` | system_users.custom_info.loginErrorCount | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `isSysUser` | `smallint(1)` | 公司角色授权的来源范围判定 | 旧Shiro中非0绕过公司过滤，是跨公司授权标记；不等于SERVICE账号。2条-1角色来自该类账号，不能默认授予新平台超级管理员。 | AUTHORIZATION_ADJUDICATION_PENDING |
| `userCustom1` | `varchar(50)` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `userCustom2` | `varchar(50)` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `userCustom3` | `varchar(50)` | 工号/账号别名的来源关联 | EHR创建链填workNo；手工外部创建链填username。原341非空，联合人员目录分类，不新增user_custom_3。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `userCustom4` | `int(11)` | 人员目录与旧流程身份映射 | 原340非零；EHR创建链填empID，手工创建填账号ID。是不同来源身份，不能无条件当员工ID或新任务办理人。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `userCustom5` | `int(11)` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |

## t_user_info 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `id` | `int(11)` | 人员/账号公司上下文的来源身份绑定 | 旧代码以人员记录ID参与旧流程身份，不等于新system_users.id；保留身份映射，不重新激活旧工作流。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `workNo` | `varchar(25)` | system_users.employee_no（真实内部工号）；外部账号别名/身份绑定 | 旧手工创建外部账号会把用户名填入workNo，不能把这类值当内部工号。 | USER_APPROVED_EMPLOYEE_NO_REFERENCE_VERIFICATION_REQUIRED |
| `realName` | `varchar(50)` | system_users.nickname | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `eName` | `varchar(50)` | system_users.custom_info.englishName | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `compID` | `int(11)` | system_user_company_department_scope.company_id/code/name | 保留用户所属公司事实，经实际来源公司解析及01→001等显式规则；不是t_user_role的授权公司替代值。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `depID` | `int(11)` | system_users.dept_id；system_user_company_department_scope.department_id/code/name | 通过来源部门目录和EHR同步绑定解析，不复用旧ID，不由部门猜公司；主部门选取与多公司资料冲突需明确。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `jobID` | `int(11)` | system_post / system_user_post（条件性复用） | 先核EHR岗位与正式岗位的业务编码/含义；无对应不创建伪岗位，不把jobStatus混成岗位角色。 | OWNER_REFERENCE_MATCH_PENDING |
| `reportTo` | `int(11)` | system_users.custom_info.employmentAssignments[].reportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `wfreportTo` | `int(11)` | system_users.custom_info.employmentAssignments[].functionalReportingEmployeeReference | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `empStatus` | `int(11)` | system_users.custom_info.employmentAssignments[].employeeStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `jobStatus` | `int(11)` | system_users.custom_info.employmentAssignments[].positionStatus | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `empType` | `int(11)` | system_users.custom_info.employmentAssignments[].employeeType | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `sex` | `smallint(1)` | system_users.sex | 按现有性别枚举转换，旧1男/0女，不直接套EHR的1男/2女。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `birthday` | `date` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `email` | `varchar(50)` | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `mobile` | `varchar(50)` | system_users.mobile | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `telphone` | `varchar(50)` | system_users.custom_info.officePhone | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `avatar` | `varchar(500)` | system_users.avatar | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `remark` | `varchar(100)` | system_users.remark | 账号及公司上下文核实后复用；同一人以fnd资料为主、t_*补缺，不任意取第一行。身份差异与当前正式资料冲突单列，不未经授权覆盖现存账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `state` | `int(11)` | system_users.custom_info.employmentAssignments[].historicalEmploymentState | 按公司任职业务属性承接，保留来源值及可核实引用；不压成单一公司或虚造当前EHR任职/审批事实。未精确关联的引用保持待关联，不丢弃。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `user_id` | `int(11)` | system_users.id | 经t_user来源绑定关联；明确一人可有多公司资料，三条无账号关联不造用户名/账号。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `custom1` | `int(11)` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `custom2` | `int(11)` | 不迁入 | 原表核验本批无业务值（生日全部NULL、列明预留字段无非空/非零业务值），用户明确裁决不迁入；后续全历史扩展必须重新核验，有业务值时阻断并重新裁决。 | EXCLUDED_BY_USER_DECISION |
| `custom3` | `varchar(50)` | system_user_company_department_scope.department_code / 正式部门上下文 | 旧创建链明确是二级办事处编码，不必等于实际主部门；原341非空，同一公司配对保存，不新增custom3镜像。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `custom4` | `varchar(50)` | system_users.custom_info.employmentAssignments[].projectTypeRestrictions | 完整承接原项目类型准入值及已核实转换，历史限制不授予办事处或具体项目访问权；未知类型不得猜配。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `custom5` | `varchar(4096)` | system_user_company_department_scope 部门范围 | 原341非空；与该条userinfo明确公司组合，去空去重/别名映射，不能跨公司套用。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |

## t_role 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `role_id` | `int(11)` | system_role.id 的来源绑定 | 14角色独立来源ID，不能和fnd_roles同数字ID归并。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `role_name` | `varchar(100)` | system_role.code | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `role_name_zn` | `varchar(100)` | system_role.name | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `home_page` | `varchar(100)` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 | EXCLUDED_BY_USER_DECISION |
| `priority` | `int(11)` | system_role.sort（需确认转换） | 旧role查询按priority降序，且决定最高角色首页；现sort用于排序，须明确转换与既有角色冲突，不能直接当作同义字段。 | CONVERSION_ADJUDICATION_PENDING |
| `status` | `smallint(1)` | system_users.status / system_role.status | 先区分账号、角色；原1有效→0启用，原0无效→1停用。t_user原2为锁定，不能丢失锁定含义。 | CONVERSION_OR_CONFLICT_ADJUDICATION_PENDING |
| `create_by` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `create_time` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `update_by` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `update_time` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `remark` | `varchar(255)` | system_role.remark | 以t_role为基础；现有角色同义匹配须显式映射，不以源码英文code直接命中新平台super_admin。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |

## t_user_role 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `id` | `int(11)` | 角色关联来源身份绑定 | 829条原关系，一条关系可关联正式公司角色事实；来源ID不冒充新user_role.id。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `user_id` | `int(11)` | system_users.id | 经账号来源身份绑定，不能直接使用旧user_id。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `role_id` | `int(11)` | system_role.id / scope_role | 经角色业务码映射，不能直接套旧role_id或只按中文名授予权限。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `comp_id` | `int(11)` | system_user_company_department_scope.company_id/code/name | 822条明确公司授权保留；2条-1且isSysUser=1为跨公司来源事实，5条NULL普通账号范围不明，不默认全公司。 | SCOPE_REUSE_WITH_EXCEPTIONS |

## fnd_user_info 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `id` | `int(8)` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `username` | `varchar(128)` | system_users.username | 用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容仍待裁决。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin历史审计关联另待指定。 | USER_APPROVED_ACCOUNT_SCOPE_LOGIN_INPUT_PENDING |
| `password` | `varchar(32)` | 排除旧凭据；使用受控认证/激活 | 已有身份规则明确旧MD5类凭据不迁入，读取列、证据和日志均排除。排除原因明列，不将旧凭据保存到JSON。 | EXCLUDED_BY_CONFIRMED_CREDENTIAL_RULE |
| `email` | `varchar(128)` | system_users.email | 用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 | USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS |
| `dpNo` | `varchar(25)` | system_user_company_department_scope.department_id/code/name | 自身部门加入可服务/范围集合；不是工号，没有公司事实时不伪配公司。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `realName` | `varchar(128)` | system_users.nickname | fnd独有账号按外部账号；与t_user同义字段冲突须依据用户裁决，不能覆盖当前人员事实。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `roleIds` | `varchar(64)` | system_user_role / system_user_company_department_scope 的语义角色 | 按已裁决32项角色映射及fnd常量语义字符串编码关联system_user_role；停用角色不迁入，明确超级管理员例外；不据旧菜单关系授予其他菜单按钮。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `isemail` | `int(11)` | system_users.custom_info.emailNotificationEnabled | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `status` | `int(1)` | system_users.status | 用户批准fnd优先；原1→0启用、0→1停用；t独有锁定2→停用1，原值保存在custom_info.legacyUserStatus；未知值阻断。 | USER_APPROVED_STATUS_CONVERSION |
| `defaultPage` | `varchar(255)` | system_users.custom_info.defaultPage | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `pwdoverdue` | `datetime` | system_users.custom_info.passwordExpireTime | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `createBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `createTime` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateTime` | `datetime` | create_time/update_time | 用户批准所选主账号原更新时间NULL时用原创建时间初始化；原NULL保留来源证据，其他历史时间不变。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `effectiveFrom` | `datetime` | system_users.custom_info.effectiveFrom | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `effectiveTo` | `datetime` | system_users.custom_info.effectiveTo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |
| `customInfo` | `json` | system_users.custom_info.customInfo | 明确业务属性保存到正式账号JSON；原customInfo完整对象置于customInfo属性，NULL/false/0均保留，不混入密码，不据旧认证历史激活当前认证或权限。 | USER_APPROVED_FORMAL_JSON_ATTRIBUTE |

## fnd_roles 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `id` | `int(6)` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `roleName` | `varchar(64)` | system_role.name / 明确的业务角色语义映射 | 与t_role中文完全同名仅普通用户、项目管理员、项目经理3项；同名不证明授权范围一致，其他15项不静默丢弃。 | ROLE_SEMANTIC_MAPPING_PENDING |
| `defaultPage` | `varchar(255)` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 | EXCLUDED_BY_USER_DECISION |
| `status` | `int(1)` | system_users.status / system_role.status | 先区分账号、角色；原1有效→0启用，原0无效→1停用。t_user原2为锁定，不能丢失锁定含义。 | CONVERSION_OR_CONFLICT_ADJUDICATION_PENDING |
| `effectiveFrom` | `datetime` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 | EXCLUDED_BY_USER_DECISION |
| `effectiveTo` | `datetime` | 不迁入；用户明确裁决 | 用户明确选择不迁入，保持system_role既有结构；不新增替代列，不写入备注或快照充作业务承接。原始来源证据保留。 | EXCLUDED_BY_USER_DECISION |
| `createBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `createTime` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateTime` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `roleRemark` | `varchar(200)` | system_role.remark | 角色同义合并时差异列为冲突，不能覆盖t_role主定义。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |

## fnd_user_power 原字段完整映射审查

| 原字段 | 原类型 | 正式承接点/候选 | 处理与未映射原因 | 状态 |
|---|---|---|---|---|
| `id` | `int(11)` | 现有来源身份绑定→正式对象ID | 来源系统＋表/对象＋来源主键；不得将不同表同数值ID视为同一对象。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `fndUserId` | `int(11)` | system_user_company_department_scope.user_id | 经fnd账号来源ID绑定，880条无孤儿。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `username` | `varchar(25)` | system_users.username（来源关联校验） | 用户确认第470条以fndUserId=473外键为准，正式范围关联pms_tbpublic；tbpublic是冲突的冗余旧名称，记录差异和来源证据，不产生另一条账号关联。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `areapower` | `varchar(4096)` | system_user_company_department_scope.department_id/code/name | 按集合正式承接既有范围授权，fnd独有账号公司001、002；内外用户都按具体项目授予访问权，范围不自动授予办事处内项目权限。 | USER_RANGE_RULE_CONFIRMED_MAPPING_PENDING |
| `createTime` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `createBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateTime` | `datetime` | create_time/update_time | 保留原历史时间，不用本次运行时间覆盖。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `updateBy` | `varchar(25)` | creator/updater | 账号迁移后按明确来源身份关联正式用户；空原值不伪造操作者；本次运行另记操作者。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `effectiveFrom` | `datetime` | system_user_company_department_scope.effective_from | 复用既有范围关系有效期字段；开始值为空或多来源有效期冲突时登记问题，不伪造历史时间。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |
| `effectiveTo` | `datetime` | system_user_company_department_scope.effective_to | 复用既有范围关系有效期字段；开始值为空或多来源有效期冲突时登记问题，不伪造历史时间。 | EXISTING_FORMAL_FIELD_OR_OWNER_REUSE |

## 已确认的账号合并规则

同一人以fnd为主、t_*补缺。c00733来源233/408合并为一个停用账号，主资料取408；w02459来源118/119合并为一个停用账号，主资料取119。各自所有来源绑定与正式范围关系保留，不从停用数据生成有效授权。范围470已确认以fndUserId=473为准，关联pms_tbpublic，冗余tbpublic不改绑账号。

## 尚未实施

本次只完成联合只读审查和重新规划。没有新增列、没有新增表、没有迁入账号或授权。八项原人员字段、项目类型限制、旧认证与默认入口等缺口尚无完整同义正式承接，继续交用户裁决；不可将它们压入remark、原表镜像或中间快照计为完成。

## 最新账号范围及联系字段裁决

用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容仍待裁决。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin历史审计关联另待指定。

用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。

以上覆盖旧账号合并/重复联系人提案；原清单与来源证据保留。逐表当前规则见[system_users整表清单](2026-10-09-dppms-system-users-table-decision.md)，待裁决问题已发送，未答不实施。实际库尚无本轮身份、权限或结构写入。

## 最后五项用户字段与关联裁决

- 保留选中*_ss、*_supp原登录名；登录与账号维护输入兼容这些形式。注册规则不在此裁决范围。
- 旧admin不迁入账号资料，仅将历史创建/更新人关联当前admin ID1；不导入旧角色或授权；目标缺失或绑定改变时阻断。
- 用户明确例外：原值@dptech.com直接承接正式email，不置空、不改写、不用custom_info替代。普通新建/修改邮箱格式校验保持。
- 正式email仅去除来源尾部制表符，原值完整留来源证据。
- z04214、w04218、y04223、w04261、z04264迁入正式用户角色关系，原公司NULL留证据；不新增公司或办事处授权。

以上覆盖此前同项待裁决建议；来源原始记录不改写。代码接入及正式迁移结果另行核验，不代表已经落库。
