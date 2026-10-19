# P34 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device_factory_version_record` (`id`, `tenant_id`, `project_id`, `device_sn`, `conp_version`, `boot_version`, `cpld_version`, `pcb_version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_project_id, :orm_device_sn, :orm_conp_version, :orm_boot_version, :orm_cpld_version, :orm_pcb_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_factory_version_record.id | #{row.id} | insert |
| ast_device_factory_version_record.tenant_id | #{row.tenantId} | insert |
| ast_device_factory_version_record.project_id | #{row.projectId} | insert |
| ast_device_factory_version_record.device_sn | #{row.deviceSn} | insert |
| ast_device_factory_version_record.conp_version | #{row.conpVersion} | insert |
| ast_device_factory_version_record.boot_version | #{row.bootVersion} | insert |
| ast_device_factory_version_record.cpld_version | #{row.cpldVersion} | insert |
| ast_device_factory_version_record.pcb_version | #{row.pcbVersion} | insert |
| ast_device_factory_version_record.creator | #{row.creator} | insert |
| ast_device_factory_version_record.create_time | #{row.createTime} | insert |
| ast_device_factory_version_record.updater | #{row.updater} | insert |
| ast_device_factory_version_record.update_time | #{row.updateTime} | insert |
| ast_device_factory_version_record.deleted | #{row.deleted} | insert |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_factory_version_record.id | #{row.target.id} | insertFactoryHistory |
| ast_device_factory_version_record.tenant_id | #{row.target.tenantId} | insertFactoryHistory |
| ast_device_factory_version_record.project_id | #{row.target.projectId} | insertFactoryHistory |
| ast_device_factory_version_record.device_sn | #{row.target.deviceSn} | insertFactoryHistory |
| ast_device_factory_version_record.conp_version | #{row.target.conpVersion} | insertFactoryHistory |
| ast_device_factory_version_record.boot_version | #{row.target.bootVersion} | insertFactoryHistory |
| ast_device_factory_version_record.cpld_version | #{row.target.cpldVersion} | insertFactoryHistory |
| ast_device_factory_version_record.pcb_version | #{row.target.pcbVersion} | insertFactoryHistory |
| ast_device_factory_version_record.creator | #{row.target.creator} | insertFactoryHistory |
| ast_device_factory_version_record.create_time | #{row.target.createTime} | insertFactoryHistory |
| ast_device_factory_version_record.updater | #{row.target.updater} | insertFactoryHistory |
| ast_device_factory_version_record.update_time | #{row.target.updateTime} | insertFactoryHistory |
| ast_device_factory_version_record.deleted | #{row.target.deleted} | insertFactoryHistory |
