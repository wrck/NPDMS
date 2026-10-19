# P01 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectCompanies | compID | id |
| selectCompanies | compCode | code |
| selectCompanies | compName | name |
| selectCompanies | CAST(isDisabled AS UNSIGNED) | disabled |
| selectDepartments | depID | id |
| selectDepartments | depCode | code |
| selectDepartments | depName | name |
| selectDepartments | compID | companyId |
| selectDepartments | adminID | parentId |
| selectDepartments | depGrade | grade |
| selectDepartments | director | director |
| selectDepartments | depProperty | property |
| selectDepartments | depNum | number |
| selectDepartments | xOrder | sortOrder |
| selectDepartments | remark | remark |
| selectDepartments | depCustom1 | custom1 |
| selectDepartments | depCustom3 | custom3 |
| selectDepartments | depCustom5 | custom5 |
| selectDepartments | CAST(isDisabled AS UNSIGNED) | disabled |
| selectFnd | id | id |
| selectFnd | departmentNum | code |
| selectFnd | departmentName | name |
| selectFnd | status | status |
| selectFnd | createTime | createTime |
| selectFnd | updateTime | updateTime |
| selectFnd | effective | effective |
| selectCompanyAliases | id | id |
| selectCompanyAliases | compCode | code |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| system_company.id | #{row.target.id} | insertCompanies |
| system_company.tenant_id | #{row.target.tenantId} | insertCompanies |
| system_company.code | #{row.target.code} | insertCompanies |
| system_company.name | #{row.target.name} | insertCompanies |
| system_company.status | #{row.target.status} | insertCompanies |
| system_company.version | #{row.target.version} | insertCompanies |
| system_company.creator | #{row.target.creator} | insertCompanies |
| system_company.create_time | #{row.target.createTime} | insertCompanies |
| system_company.updater | #{row.target.updater} | insertCompanies |
| system_company.update_time | #{row.target.updateTime} | insertCompanies |
| system_company.deleted | #{row.target.deleted} | insertCompanies |
| system_dept.id | #{row.target.id} | insertDepartments |
| system_dept.tenant_id | #{row.target.tenantId} | insertDepartments |
| system_dept.code | #{row.target.code} | insertDepartments |
| system_dept.name | #{row.target.name} | insertDepartments |
| system_dept.status | #{row.target.status} | insertDepartments |
| system_dept.version | #{row.target.version} | insertDepartments |
| system_dept.creator | #{row.target.creator} | insertDepartments |
| system_dept.create_time | #{row.target.createTime} | insertDepartments |
| system_dept.updater | #{row.target.updater} | insertDepartments |
| system_dept.update_time | #{row.target.updateTime} | insertDepartments |
| system_dept.deleted | #{row.target.deleted} | insertDepartments |
| system_dept.parent_id | #{row.target.parentId} | insertDepartments |
| system_dept.sort | #{row.target.sort} | insertDepartments |
| system_dept.leader_user_id | #{row.target.leaderUserId} | insertDepartments |
| system_dept.phone | #{row.target.phone} | insertDepartments |
| system_dept.email | #{row.target.email} | insertDepartments |
