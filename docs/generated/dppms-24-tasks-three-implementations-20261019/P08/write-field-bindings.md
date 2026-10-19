# P08 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| system_dict_data.id | #{command.row.id} | insertHistorical |
| system_dict_data.sort | #{command.row.sort} | insertHistorical |
| system_dict_data.label | #{command.row.label} | insertHistorical |
| system_dict_data.value | #{command.row.value} | insertHistorical |
| system_dict_data.dict_type | #{command.row.dictType} | insertHistorical |
| system_dict_data.status | #{command.row.status} | insertHistorical |
| system_dict_data.color_type | #{command.row.colorType} | insertHistorical |
| system_dict_data.css_class | #{command.row.cssClass} | insertHistorical |
| system_dict_data.remark | #{command.row.remark} | insertHistorical |
| system_dict_data.creator | #{command.row.creator} | insertHistorical |
| system_dict_data.create_time | #{command.row.createTime} | insertHistorical |
| system_dict_data.updater | #{command.row.updater} | insertHistorical |
| system_dict_data.update_time | #{command.row.updateTime} | insertHistorical |
| system_dict_data.deleted | #{command.row.deleted} | insertHistorical |
| system_dict_type.id | #{command.row.id} | insertHistorical |
| system_dict_type.name | #{command.row.name} | insertHistorical |
| system_dict_type.type | #{command.row.type} | insertHistorical |
| system_dict_type.status | #{command.row.status} | insertHistorical |
| system_dict_type.remark | #{command.row.remark} | insertHistorical |
| system_dict_type.creator | #{command.row.creator} | insertHistorical |
| system_dict_type.create_time | #{command.row.createTime} | insertHistorical |
| system_dict_type.updater | #{command.row.updater} | insertHistorical |
| system_dict_type.update_time | #{command.row.updateTime} | insertHistorical |
| system_dict_type.deleted | #{command.row.deleted} | insertHistorical |
| system_dict_type.deleted_time | #{command.row.deletedTime} | insertHistorical |
## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| system_dict_data.id | #{command.row.id} | insertHistorical |
| system_dict_data.sort | #{command.row.sort} | insertHistorical |
| system_dict_data.label | #{command.row.label} | insertHistorical |
| system_dict_data.value | #{command.row.value} | insertHistorical |
| system_dict_data.dict_type | #{command.row.dictType} | insertHistorical |
| system_dict_data.status | #{command.row.status} | insertHistorical |
| system_dict_data.color_type | #{command.row.colorType} | insertHistorical |
| system_dict_data.css_class | #{command.row.cssClass} | insertHistorical |
| system_dict_data.remark | #{command.row.remark} | insertHistorical |
| system_dict_data.creator | #{command.row.creator} | insertHistorical |
| system_dict_data.create_time | #{command.row.createTime} | insertHistorical |
| system_dict_data.updater | #{command.row.updater} | insertHistorical |
| system_dict_data.update_time | #{command.row.updateTime} | insertHistorical |
| system_dict_data.deleted | #{command.row.deleted} | insertHistorical |
| system_dict_type.id | #{command.row.id} | insertHistorical |
| system_dict_type.name | #{command.row.name} | insertHistorical |
| system_dict_type.type | #{command.row.type} | insertHistorical |
| system_dict_type.status | #{command.row.status} | insertHistorical |
| system_dict_type.remark | #{command.row.remark} | insertHistorical |
| system_dict_type.creator | #{command.row.creator} | insertHistorical |
| system_dict_type.create_time | #{command.row.createTime} | insertHistorical |
| system_dict_type.updater | #{command.row.updater} | insertHistorical |
| system_dict_type.update_time | #{command.row.updateTime} | insertHistorical |
| system_dict_type.deleted | #{command.row.deleted} | insertHistorical |
| system_dict_type.deleted_time | #{command.row.deletedTime} | insertHistorical |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| system_dict_type.id | #{row.target.id} | insertDictionaryType |
| system_dict_type.name | #{row.target.name} | insertDictionaryType |
| system_dict_type.type | #{row.target.type} | insertDictionaryType |
| system_dict_type.status | #{row.target.status} | insertDictionaryType |
| system_dict_type.remark | #{row.target.remark} | insertDictionaryType |
| system_dict_type.creator | #{row.target.creator} | insertDictionaryType |
| system_dict_type.create_time | #{row.target.createTime} | insertDictionaryType |
| system_dict_type.updater | #{row.target.updater} | insertDictionaryType |
| system_dict_type.update_time | #{row.target.updateTime} | insertDictionaryType |
| system_dict_type.deleted | #{row.target.deleted} | insertDictionaryType |
| system_dict_type.deleted_time | #{row.target.deletedTime} | insertDictionaryType |
| system_dict_data.id | #{row.target.id} | insertDictionaryItem |
| system_dict_data.sort | #{row.target.sort} | insertDictionaryItem |
| system_dict_data.label | #{row.target.label} | insertDictionaryItem |
| system_dict_data.value | #{row.target.value} | insertDictionaryItem |
| system_dict_data.dict_type | #{row.target.dictType} | insertDictionaryItem |
| system_dict_data.status | #{row.target.status} | insertDictionaryItem |
| system_dict_data.color_type | #{row.target.colorType} | insertDictionaryItem |
| system_dict_data.css_class | #{row.target.cssClass} | insertDictionaryItem |
| system_dict_data.remark | #{row.target.remark} | insertDictionaryItem |
| system_dict_data.creator | #{row.target.creator} | insertDictionaryItem |
| system_dict_data.create_time | #{row.target.createTime} | insertDictionaryItem |
| system_dict_data.updater | #{row.target.updater} | insertDictionaryItem |
| system_dict_data.update_time | #{row.target.updateTime} | insertDictionaryItem |
| system_dict_data.deleted | #{row.target.deleted} | insertDictionaryItem |
