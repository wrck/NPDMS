# P31 新虚基类实际读写字段衔接

当前版本：2026-10-19T17:55:45.626704+08:00。角色60按既有批准承接PROJ成员历史；本页仅更新C，A/B历史映射保留。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectMemberContact | m.id | sourceId |
| selectMemberContact | m.projectId | originalProjectId |
| selectMemberContact | m.projectType | projectType |
| selectMemberContact | m.memberRole | memberRole |
| selectMemberContact | m.memberCode | memberCode |
| selectMemberContact | m.memberName | memberName |
| selectMemberContact | m.phoneNum | phoneNum |
| selectMemberContact | m.email | email |
| selectMemberContact | m.fromFlag | fromFlag |
| selectMemberContact | m.createTime | createTime |
| selectMemberContact | m.createBy | source_creator |
| selectMemberContact | m.updateTime | updateTime |
| selectMemberContact | m.updateBy | source_updater |
| selectMemberContact | m.effectiveFrom | effectiveFrom |
| selectMemberContact | m.effectiveTo | effectiveTo |

原角色10/20/30映射既有销售/服务经理/项目经理；60保留历史角色60。原项目身份按已决规则承接，姓名、编码、电话、邮箱、来源、原有效期及审计进入成员字段；user_id和employee_no不猜填，authorization_confirmed=false。

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| proj_project_member_assignment.id | #{row.member.id} | insertMembers |
| proj_project_member_assignment.project_id | #{row.member.projectId} | insertMembers |
| proj_project_member_assignment.user_id | #{row.member.userId} | insertMembers |
| proj_project_member_assignment.employee_no | #{row.member.employeeNo} | insertMembers |
| proj_project_member_assignment.member_name | #{row.member.memberName} | insertMembers |
| proj_project_member_assignment.company_id | #{row.member.companyId} | insertMembers |
| proj_project_member_assignment.company_code | #{row.member.companyCode} | insertMembers |
| proj_project_member_assignment.company_name | #{row.member.companyName} | insertMembers |
| proj_project_member_assignment.department_id | #{row.member.departmentId} | insertMembers |
| proj_project_member_assignment.department_code | #{row.member.departmentCode} | insertMembers |
| proj_project_member_assignment.department_name | #{row.member.departmentName} | insertMembers |
| proj_project_member_assignment.member_role | #{row.member.memberRole} | insertMembers |
| proj_project_member_assignment.assignment_type | #{row.member.assignmentType} | insertMembers |
| proj_project_member_assignment.site_id | #{row.member.siteId} | insertMembers |
| proj_project_member_assignment.responsibility | #{row.member.responsibility} | insertMembers |
| proj_project_member_assignment.change_reason | #{row.member.changeReason} | insertMembers |
| proj_project_member_assignment.effective_from | #{row.member.effectiveFrom} | insertMembers |
| proj_project_member_assignment.effective_to | #{row.member.effectiveTo} | insertMembers |
| proj_project_member_assignment.status | #{row.member.status} | insertMembers |
| proj_project_member_assignment.version | #{row.member.version} | insertMembers |
| proj_project_member_assignment.creator | #{row.member.creator} | insertMembers |
| proj_project_member_assignment.create_time | #{row.member.createTime} | insertMembers |
| proj_project_member_assignment.updater | #{row.member.updater} | insertMembers |
| proj_project_member_assignment.update_time | #{row.member.updateTime} | insertMembers |
| proj_project_member_assignment.deleted | #{row.member.deleted} | insertMembers |
| proj_project_member_assignment.deleted_time | #{row.member.deletedTime} | insertMembers |
| proj_project_member_assignment.tenant_id | #{row.member.tenantId} | insertMembers |
| proj_project_member_assignment.remark | #{row.member.remark} | insertMembers |
| proj_project_member_assignment.end_reason | #{row.member.endReason} | insertMembers |
| proj_project_member_assignment.member_code | #{row.member.memberCode} | insertMembers |
| proj_project_member_assignment.phone_number | #{row.member.phoneNumber} | insertMembers |
| proj_project_member_assignment.email | #{row.member.email} | insertMembers |
| proj_project_member_assignment.registration_source | #{row.member.registrationSource} | insertMembers |
| proj_project_member_assignment.authorization_confirmed | #{row.member.authorizationConfirmed} | insertMembers |

没有CUS联系人写入或多表业务更新。前后置与本轮日期阻断见[当前报告](../base-sync-seven-blockers-followup-20261019.md)。
