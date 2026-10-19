# P31 原表逐字段承载清单

合并组件：P31, P32。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_member.id | int(11) / NO | INT/PLT来源绑定 | 原成员id保留独立来源身份，不覆盖正式成员主键。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.projectId | int(11) / YES | proj_project_member_assignment.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 通过P17全部原项目身份精确解析正式项目，不能直写旧projectId。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.projectType | varchar(25) / YES | 项目身份约束 | 原类型10与项目绑定共同核实；不挂靠同名其他类型项目。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.memberRole | varchar(45) / YES | proj_project_member_assignment.member_role | P31原10/20/30沿已批准正式角色；P32原60保留历史最终客户联系人含义，不生成账号角色。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.memberCode | varchar(45) / YES | proj_project_member_assignment.member_code；proj_project_member_assignment.user_id | 原编码原样保存，user_id仅有精确来源账号绑定时关联；P32无账号仍可迁历史。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.memberName | varchar(45) / YES | proj_project_member_assignment.member_name | 原姓名，不用当前账号姓名覆盖，不以姓名匹配账号。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.phoneNum | varchar(20) / YES | proj_project_member_assignment.phone_number | 原电话原样保存，不凭格式猜移动/固定电话，不用于合并人员或账号。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.email | varchar(45) / YES | proj_project_member_assignment.email | 原邮箱保留NULL/空串；不覆盖SYS当前联系方式。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.fromFlag | varchar(2) / YES | proj_project_member_assignment.registration_source | 1项目信息、2手工成员，保留原值；不解释为内外部或授权。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.createTime | datetime / YES | proj_project_member_assignment.create_time | 原历史时间；未授权历史允许原NULL，已授权仍按既有Owner规则。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.createBy | varchar(45) / YES | proj_project_member_assignment.creator | 沿既定精确审计账号绑定；原NULL/空值保持，不自动填管理员。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.updateTime | datetime / YES | proj_project_member_assignment.update_time | 原更新时间；空时取本行原创建时间，两者均空保持NULL，原NULL由来源证据保留。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.updateBy | varchar(15) / YES | proj_project_member_assignment.updater | 沿既定精确审计账号绑定；原NULL/空值保持。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.effectiveTo | datetime / YES | proj_project_member_assignment.effective_to | 保留原结束；不编造退出日期隔离权限，authorization_confirmed独立控制。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.effectiveFrom | datetime / YES | proj_project_member_assignment.effective_from | 保留原开始，不用迁入时间替代。；P31既有；P32最新裁决待实现 | P31 |
| pm_project_member.id | int(11) / NO | INT/PLT来源绑定 | 原成员id保留独立来源身份，不覆盖正式成员主键。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.projectId | int(11) / YES | proj_project_member_assignment.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 通过P17全部原项目身份精确解析正式项目，不能直写旧projectId。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.projectType | varchar(25) / YES | 项目身份约束 | 原类型10与项目绑定共同核实；不挂靠同名其他类型项目。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.memberRole | varchar(45) / YES | proj_project_member_assignment.member_role | 只处理原60，保留历史最终客户联系人含义；现代码尚无该成员历史接入，待实现，不能用TEAM_MEMBER代替。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.memberCode | varchar(45) / YES | proj_project_member_assignment.member_code；proj_project_member_assignment.user_id | 原编码原样保存，user_id仅有精确来源账号绑定时关联；P32无账号仍可迁历史。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.memberName | varchar(45) / YES | proj_project_member_assignment.member_name | 原姓名，不用当前账号姓名覆盖，不以姓名匹配账号。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.phoneNum | varchar(20) / YES | proj_project_member_assignment.phone_number | 原电话原样保存，不凭格式猜移动/固定电话，不用于合并人员或账号。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.email | varchar(45) / YES | proj_project_member_assignment.email | 原邮箱保留NULL/空串；不覆盖SYS当前联系方式。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.fromFlag | varchar(2) / YES | proj_project_member_assignment.registration_source | 1项目信息、2手工成员，保留原值；不解释为内外部或授权。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.createTime | datetime / YES | proj_project_member_assignment.create_time | 原历史时间；未授权历史允许原NULL，已授权仍按既有Owner规则。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.createBy | varchar(45) / YES | proj_project_member_assignment.creator | 沿既定精确审计账号绑定；原NULL/空值保持，不自动填管理员。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.updateTime | datetime / YES | proj_project_member_assignment.update_time | 原更新时间；空时取本行原创建时间，两者均空保持NULL，原NULL由来源证据保留。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.updateBy | varchar(15) / YES | proj_project_member_assignment.updater | 沿既定精确审计账号绑定；原NULL/空值保持。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.effectiveTo | datetime / YES | proj_project_member_assignment.effective_to | 保留原结束；不编造退出日期隔离权限，authorization_confirmed独立控制。；P31既有；P32最新裁决待实现 | P32 |
| pm_project_member.effectiveFrom | datetime / YES | proj_project_member_assignment.effective_from | 保留原开始，不用迁入时间替代。；P31既有；P32最新裁决待实现 | P32 |
