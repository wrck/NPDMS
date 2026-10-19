# P08 新虚基类实际读写字段衔接

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p08)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectDictionaryType | id | raw_id |
| selectDictionaryType | dataTypeCode | data_type_code |
| selectDictionaryType | dataTypeName | data_type_name |
| selectDictionaryType | status | raw_status |
| selectDictionaryType | createTime | create_time |
| selectDictionaryType | createBy | raw_creator |
| selectDictionaryType | updateTime | update_time |
| selectDictionaryType | updateBy | raw_updater |
| selectDictionaryType | effective | effective |
| selectDictionaryItem | id | raw_id |
| selectDictionaryItem | dataTypeCode | data_type_code |
| selectDictionaryItem | basicDataId | basic_data_id |
| selectDictionaryItem | basicDataName | basic_data_name |
| selectDictionaryItem | basicDataAttri1 | basic_data_attri1 |
| selectDictionaryItem | sortId | sort_id |
| selectDictionaryItem | createTime | create_time |
| selectDictionaryItem | createBy | raw_creator |
| selectDictionaryItem | updateTime | update_time |
| selectDictionaryItem | updateBy | raw_updater |
| selectDictionaryItem | effective | effective |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
