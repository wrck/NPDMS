# P31 新虚基类实际读写字段衔接

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p31)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

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
| selectMemberContact | m.effective | effective |

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
| cus_project_customer_contact_relation.id | #{row.contact.id} | insertContacts |
| cus_project_customer_contact_relation.project_id | #{row.contact.projectId} | insertContacts |
| cus_project_customer_contact_relation.customer_id | #{row.contact.customerId} | insertContacts |
| cus_project_customer_contact_relation.customer_contact_id | #{row.contact.customerContactId} | insertContacts |
| cus_project_customer_contact_relation.name | #{row.contact.name} | insertContacts |
| cus_project_customer_contact_relation.department | #{row.contact.department} | insertContacts |
| cus_project_customer_contact_relation.title | #{row.contact.title} | insertContacts |
| cus_project_customer_contact_relation.mobile | #{row.contact.mobile} | insertContacts |
| cus_project_customer_contact_relation.phone | #{row.contact.phone} | insertContacts |
| cus_project_customer_contact_relation.email | #{row.contact.email} | insertContacts |
| cus_project_customer_contact_relation.primary_flag | #{row.contact.primaryFlag} | insertContacts |
| cus_project_customer_contact_relation.primary_set_time | #{row.contact.primarySetTime} | insertContacts |
| cus_project_customer_contact_relation.status | #{row.contact.status} | insertContacts |
| cus_project_customer_contact_relation.remark | #{row.contact.remark} | insertContacts |
| cus_project_customer_contact_relation.version | #{row.contact.version} | insertContacts |
| cus_project_customer_contact_relation.creator | #{row.contact.creator} | insertContacts |
| cus_project_customer_contact_relation.create_time | #{row.contact.createTime} | insertContacts |
| cus_project_customer_contact_relation.updater | #{row.contact.updater} | insertContacts |
| cus_project_customer_contact_relation.update_time | #{row.contact.updateTime} | insertContacts |
| cus_project_customer_contact_relation.deleted | #{row.contact.deleted} | insertContacts |
| cus_project_customer_contact_relation.deleted_by | #{row.contact.deletedBy} | insertContacts |
| cus_project_customer_contact_relation.deleted_at | #{row.contact.deletedAt} | insertContacts |
| cus_project_customer_contact_relation.tenant_id | #{row.contact.tenantId} | insertContacts |
| cus_project_customer_contact_relation.active_primary_project_id | #{row.contact.activePrimaryProjectId} | insertContacts |
| cus_project_customer_contact_relation.project_party_id | #{row.contact.projectPartyId} | insertContacts |
| cus_project_customer_contact_relation.contact_code | #{row.contact.contactCode} | insertContacts |
| cus_project_customer_contact_relation.registration_source | #{row.contact.registrationSource} | insertContacts |
| cus_project_customer_contact_relation.effective_from | #{row.contact.effectiveFrom} | insertContacts |
| cus_project_customer_contact_relation.effective_to | #{row.contact.effectiveTo} | insertContacts |
