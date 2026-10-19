# P34 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectFactoryHistory | `serial_number` | device_sn |
| selectFactoryHistory | `conp` | conp_version |
| selectFactoryHistory | `cpld` | cpld_version |
| selectFactoryHistory | `boot` | boot_version |
| selectFactoryHistory | `pcb` | pcb_version |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
