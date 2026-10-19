# P01 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| system_dept.id | #{department.id} | insertHistoricalDepartment |
| system_dept.code | #{department.code} | insertHistoricalDepartment |
| system_dept.name | #{department.name} | insertHistoricalDepartment |
| system_dept.parent_id | #{department.parentId} | insertHistoricalDepartment |
| system_dept.sort | #{department.sort} | insertHistoricalDepartment |
| system_dept.leader_user_id | #{department.leaderUserId} | insertHistoricalDepartment |
| system_dept.phone | #{department.phone} | insertHistoricalDepartment |
| system_dept.email | #{department.email} | insertHistoricalDepartment |
| system_dept.status | #{department.status} | insertHistoricalDepartment |
| system_dept.version | #{department.version} | insertHistoricalDepartment |
| system_dept.creator | #{department.creator} | insertHistoricalDepartment |
| system_dept.create_time | #{department.createTime} | insertHistoricalDepartment |
| system_dept.updater | #{department.updater} | insertHistoricalDepartment |
| system_dept.update_time | #{department.updateTime} | insertHistoricalDepartment |
| system_dept.deleted | FALSE | insertHistoricalDepartment |
| system_dept.tenant_id | #{tenantId} | insertHistoricalDepartment |

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `system_company` (`id`, `tenant_id`, `code`, `name`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_code, :orm_name, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| system_company.id | #{write.node.id} | insertCompany |
| system_company.code | #{write.node.code} | insertCompany |
| system_company.name | #{write.node.name} | insertCompany |
| system_company.status | #{write.node.status} | insertCompany |
| system_company.version | #{write.node.version} | insertCompany |
| system_company.creator | #{write.node.creator} | insertCompany |
| system_company.create_time | #{write.node.createTime} | insertCompany |
| system_company.updater | #{write.node.updater} | insertCompany |
| system_company.update_time | #{write.node.updateTime} | insertCompany |
| system_company.tenant_id | #{write.tenantId} | insertCompany |
| system_company.deleted | FALSE | insertCompany |
| system_dept.id | #{write.node.id} | insertDepartment |
| system_dept.code | #{write.node.code} | insertDepartment |
| system_dept.name | #{write.node.name} | insertDepartment |
| system_dept.parent_id | #{write.node.parentId} | insertDepartment |
| system_dept.sort | #{write.node.sort} | insertDepartment |
| system_dept.leader_user_id | #{write.node.leaderUserId} | insertDepartment |
| system_dept.phone | #{write.node.phone} | insertDepartment |
| system_dept.email | #{write.node.email} | insertDepartment |
| system_dept.status | #{write.node.status} | insertDepartment |
| system_dept.version | #{write.node.version} | insertDepartment |
| system_dept.creator | #{write.node.creator} | insertDepartment |
| system_dept.create_time | #{write.node.createTime} | insertDepartment |
| system_dept.updater | #{write.node.updater} | insertDepartment |
| system_dept.update_time | #{write.node.updateTime} | insertDepartment |
| system_dept.tenant_id | #{write.tenantId} | insertDepartment |
| system_dept.deleted | FALSE | insertDepartment |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
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
