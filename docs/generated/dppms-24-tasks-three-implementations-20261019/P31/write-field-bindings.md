# P31 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_member_assignment.id | #{entry.values.id} | insertHistorical |
| proj_project_member_assignment.tenant_id | #{entry.values.tenantId} | insertHistorical |
| proj_project_member_assignment.project_id | #{entry.values.projectId} | insertHistorical |
| proj_project_member_assignment.user_id | #{entry.values.userId} | insertHistorical |
| proj_project_member_assignment.employee_no | #{entry.values.employeeNo} | insertHistorical |
| proj_project_member_assignment.member_name | #{entry.values.memberName} | insertHistorical |
| proj_project_member_assignment.company_id | #{entry.values.companyId} | insertHistorical |
| proj_project_member_assignment.company_code | #{entry.values.companyCode} | insertHistorical |
| proj_project_member_assignment.company_name | #{entry.values.companyName} | insertHistorical |
| proj_project_member_assignment.department_id | #{entry.values.departmentId} | insertHistorical |
| proj_project_member_assignment.department_code | #{entry.values.departmentCode} | insertHistorical |
| proj_project_member_assignment.department_name | #{entry.values.departmentName} | insertHistorical |
| proj_project_member_assignment.member_role | #{entry.values.memberRole} | insertHistorical |
| proj_project_member_assignment.assignment_type | #{entry.values.assignmentType} | insertHistorical |
| proj_project_member_assignment.site_id | #{entry.values.siteId} | insertHistorical |
| proj_project_member_assignment.responsibility | #{entry.values.responsibility} | insertHistorical |
| proj_project_member_assignment.change_reason | #{entry.values.changeReason} | insertHistorical |
| proj_project_member_assignment.remark | #{entry.values.remark} | insertHistorical |
| proj_project_member_assignment.end_reason | #{entry.values.endReason} | insertHistorical |
| proj_project_member_assignment.effective_from | #{entry.values.effectiveFrom} | insertHistorical |
| proj_project_member_assignment.effective_to | #{entry.values.effectiveTo} | insertHistorical |
| proj_project_member_assignment.status | #{entry.values.status} | insertHistorical |
| proj_project_member_assignment.version | #{entry.values.version} | insertHistorical |
| proj_project_member_assignment.creator | #{entry.values.creator} | insertHistorical |
| proj_project_member_assignment.create_time | #{entry.values.createTime} | insertHistorical |
| proj_project_member_assignment.updater | #{entry.values.updater} | insertHistorical |
| proj_project_member_assignment.update_time | #{entry.values.updateTime} | insertHistorical |
| proj_project_member_assignment.deleted | b'0' | insertHistorical |
| proj_project_member_assignment.deleted_time | #{entry.values.deletedTime} | insertHistorical |
| proj_project_member_assignment.member_code | #{entry.values.memberCode} | insertHistorical |
| proj_project_member_assignment.phone_number | #{entry.values.phoneNumber} | insertHistorical |
| proj_project_member_assignment.email | #{entry.values.email} | insertHistorical |
| proj_project_member_assignment.registration_source | #{entry.values.registrationSource} | insertHistorical |
| proj_project_member_assignment.authorization_confirmed | #{entry.values.authorizationConfirmed} | insertHistorical |
## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| cus_project_customer_contact_relation.id | #{entry.values.id} | insertHistorical |
| cus_project_customer_contact_relation.project_id | #{entry.values.projectId} | insertHistorical |
| cus_project_customer_contact_relation.customer_id | #{entry.values.customerId} | insertHistorical |
| cus_project_customer_contact_relation.customer_contact_id | #{entry.values.customerContactId} | insertHistorical |
| cus_project_customer_contact_relation.name | #{entry.values.name} | insertHistorical |
| cus_project_customer_contact_relation.department | #{entry.values.department} | insertHistorical |
| cus_project_customer_contact_relation.title | #{entry.values.title} | insertHistorical |
| cus_project_customer_contact_relation.mobile | #{entry.values.mobile} | insertHistorical |
| cus_project_customer_contact_relation.phone | #{entry.values.phone} | insertHistorical |
| cus_project_customer_contact_relation.email | #{entry.values.email} | insertHistorical |
| cus_project_customer_contact_relation.primary_flag | #{entry.values.primaryFlag} | insertHistorical |
| cus_project_customer_contact_relation.primary_set_time | #{entry.values.primarySetTime} | insertHistorical |
| cus_project_customer_contact_relation.status | #{entry.values.status} | insertHistorical |
| cus_project_customer_contact_relation.remark | #{entry.values.remark} | insertHistorical |
| cus_project_customer_contact_relation.version | #{entry.values.version} | insertHistorical |
| cus_project_customer_contact_relation.creator | #{entry.values.creator} | insertHistorical |
| cus_project_customer_contact_relation.create_time | #{entry.values.createTime} | insertHistorical |
| cus_project_customer_contact_relation.updater | #{entry.values.updater} | insertHistorical |
| cus_project_customer_contact_relation.update_time | #{entry.values.updateTime} | insertHistorical |
| cus_project_customer_contact_relation.deleted | #{entry.values.deleted} | insertHistorical |
| cus_project_customer_contact_relation.deleted_by | #{entry.values.deletedBy} | insertHistorical |
| cus_project_customer_contact_relation.deleted_at | #{entry.values.deletedAt} | insertHistorical |
| cus_project_customer_contact_relation.tenant_id | #{entry.values.tenantId} | insertHistorical |
| cus_project_customer_contact_relation.project_party_id | #{entry.values.projectPartyId} | insertHistorical |
| cus_project_customer_contact_relation.contact_code | #{entry.values.contactCode} | insertHistorical |
| cus_project_customer_contact_relation.registration_source | #{entry.values.registrationSource} | insertHistorical |
| cus_project_customer_contact_relation.effective_from | #{entry.values.effectiveFrom} | insertHistorical |
| cus_project_customer_contact_relation.effective_to | #{entry.values.effectiveTo} | insertHistorical |
| proj_project_member_assignment.id | #{entry.values.id} | insertHistorical |
| proj_project_member_assignment.tenant_id | #{entry.values.tenantId} | insertHistorical |
| proj_project_member_assignment.project_id | #{entry.values.projectId} | insertHistorical |
| proj_project_member_assignment.user_id | #{entry.values.userId} | insertHistorical |
| proj_project_member_assignment.employee_no | #{entry.values.employeeNo} | insertHistorical |
| proj_project_member_assignment.member_name | #{entry.values.memberName} | insertHistorical |
| proj_project_member_assignment.company_id | #{entry.values.companyId} | insertHistorical |
| proj_project_member_assignment.company_code | #{entry.values.companyCode} | insertHistorical |
| proj_project_member_assignment.company_name | #{entry.values.companyName} | insertHistorical |
| proj_project_member_assignment.department_id | #{entry.values.departmentId} | insertHistorical |
| proj_project_member_assignment.department_code | #{entry.values.departmentCode} | insertHistorical |
| proj_project_member_assignment.department_name | #{entry.values.departmentName} | insertHistorical |
| proj_project_member_assignment.member_role | #{entry.values.memberRole} | insertHistorical |
| proj_project_member_assignment.assignment_type | #{entry.values.assignmentType} | insertHistorical |
| proj_project_member_assignment.site_id | #{entry.values.siteId} | insertHistorical |
| proj_project_member_assignment.responsibility | #{entry.values.responsibility} | insertHistorical |
| proj_project_member_assignment.change_reason | #{entry.values.changeReason} | insertHistorical |
| proj_project_member_assignment.remark | #{entry.values.remark} | insertHistorical |
| proj_project_member_assignment.end_reason | #{entry.values.endReason} | insertHistorical |
| proj_project_member_assignment.effective_from | #{entry.values.effectiveFrom} | insertHistorical |
| proj_project_member_assignment.effective_to | #{entry.values.effectiveTo} | insertHistorical |
| proj_project_member_assignment.status | #{entry.values.status} | insertHistorical |
| proj_project_member_assignment.version | #{entry.values.version} | insertHistorical |
| proj_project_member_assignment.creator | #{entry.values.creator} | insertHistorical |
| proj_project_member_assignment.create_time | #{entry.values.createTime} | insertHistorical |
| proj_project_member_assignment.updater | #{entry.values.updater} | insertHistorical |
| proj_project_member_assignment.update_time | #{entry.values.updateTime} | insertHistorical |
| proj_project_member_assignment.deleted | b'0' | insertHistorical |
| proj_project_member_assignment.deleted_time | #{entry.values.deletedTime} | insertHistorical |
| proj_project_member_assignment.member_code | #{entry.values.memberCode} | insertHistorical |
| proj_project_member_assignment.phone_number | #{entry.values.phoneNumber} | insertHistorical |
| proj_project_member_assignment.email | #{entry.values.email} | insertHistorical |
| proj_project_member_assignment.registration_source | #{entry.values.registrationSource} | insertHistorical |
| proj_project_member_assignment.authorization_confirmed | #{entry.values.authorizationConfirmed} | insertHistorical |
## 新虚基类套件

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
