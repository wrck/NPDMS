# 出厂软件版本整表复核与全历史缺口

状态：RETENTION_RULE_APPROVED / TABLE_STRUCTURE_WAITING_USER。用户已裁决“完整保留正式版本记录，冲突不自动覆盖主档”；该答复不批准表结构。本文件继续列示5个原字段、23列既有主档及19列本任务记录表，新增表/列、更名、撤回与项目关联方案等待整表裁决。实施范围版本唯一键已另获批准并完成本批迁入。

原表fb_soft_version共5列，本批19条原行与现有ast_device_factory_version的五项业务字段逐值一致；本任务引入的ast_device_factory_version_record也为19条，复制值没有差异。正式出厂主档已承接本批版本值，新引入记录表的业务必要性、项目关联及结构仍须整表裁决，不能以其已落库或原回执成功代替批准。

全历史165801行、165409个按字节区分的序列号；392个序列号各有2行，共784行。其中288个序列号有不同版本组合，其余104个组合相同。原表没有技术主键、创建/更新时间、版本顺序或操作者。不能按返回顺序选最新，不去重、不按本批19条无冲突推断全历史可单行合并。后续需先裁决多版本业务模型和当前版本选择依据，不能写入覆盖现有唯一设备主档。

## 全部5原字段

| 原字段 | 原类型 | 正式既有字段 | 本轮核对 |
|---|---|---|---|
| `serial_number` | varchar(100) | ast_device_factory_version.device_sn | 本批19行逐值一致；NULL与空字符串不互换 |
| `conp` | varchar(100) | ast_device_factory_version.conp_version | 本批19行逐值一致；NULL与空字符串不互换 |
| `cpld` | varchar(100) | ast_device_factory_version.cpld_version | 本批19行逐值一致；NULL与空字符串不互换 |
| `boot` | varchar(100) | ast_device_factory_version.boot_version | 本批19行逐值一致；NULL与空字符串不互换 |
| `pcb` | varchar(100) | ast_device_factory_version.pcb_version | 本批19行逐值一致；NULL与空字符串不互换 |

## 现有正式出厂主档全部23列

| 字段 | 当前类型 | 可空 |
|---|---|---|
| `id` | bigint | NO |
| `device_sn` | varchar(100) | NO |
| `conp_version` | varchar(255) | YES |
| `conp_type` | varchar(100) | YES |
| `conp_series` | varchar(100) | YES |
| `conp_mark` | varchar(255) | YES |
| `boot_version` | varchar(255) | YES |
| `cpld_version` | varchar(255) | YES |
| `pcb_version` | varchar(255) | YES |
| `customized` | bit(1) | YES |
| `source_system` | varchar(32) | NO |
| `source_key` | varchar(128) | NO |
| `source_version` | varchar(64) | YES |
| `source_updated_at` | datetime(3) | YES |
| `synced_at` | datetime(3) | YES |
| `sync_status` | varchar(32) | NO |
| `version` | int unsigned | NO |
| `creator` | varchar(64) | NO |
| `create_time` | datetime(3) | NO |
| `updater` | varchar(64) | NO |
| `update_time` | datetime(3) | NO |
| `deleted` | bit(1) | NO |
| `tenant_id` | bigint | NO |

既有conp_type/conp_series/conp_mark/customized不在这张原表，不根据版本文本自行解析或填写。原表没有审计时间，不将source_read_at或迁入时间伪称原创建时间。当前主档来源身份、同步状态及审计保持既有含义；跨来源或非空版本冲突不覆盖。

## 本任务引入记录表全部19列（尚未裁决）

| 当前字段 | 当前类型 | 可空 |
|---|---|---|
| `id` | bigint | NO |
| `tenant_id` | bigint | NO |
| `project_id` | bigint | NO |
| `source_project_key` | varchar(128) | NO |
| `source_system` | varchar(32) | NO |
| `source_key` | varchar(128) | NO |
| `source_version` | varchar(128) | YES |
| `source_read_at` | datetime(3) | NO |
| `source_checksum` | char(64) | NO |
| `serial_number` | varchar(100) | YES |
| `conp` | varchar(100) | YES |
| `boot` | varchar(100) | YES |
| `cpld` | varchar(100) | YES |
| `pcb` | varchar(100) | YES |
| `creator` | varchar(64) | NO |
| `create_time` | datetime(3) | NO |
| `updater` | varchar(64) | NO |
| `update_time` | datetime(3) | NO |
| `deleted` | bit(1) | NO |

该记录表的project_id/source_project_key来自提取上下文，不属于原fb_soft_version五列。source_read_at/checksum等属于来源读取和幂等信息，不代表设备出厂时间或版本先后。不能仅把conp/boot/cpld/pcb改名为正式字段就认定全表符合业务模型；未裁决前不扩写或删除这19条，不覆盖不可变来源证据。

私有只读证据：factory-original-and-formal-schema-readonly-20261010.json、factory-full-history-duplicate-profile-readonly-20261010.json；writes=0。以上未重新执行任何迁移、未修改业务结构、代码或历史回执。

## 项目查询关联的新增核查

本批19个序列号全有正式出厂主档；原表全历史没有NULL或空序列号。19条项目上下文记录中，只有12条精确对应未删除的正式设备及相同当前项目，也只有12条通过现有正式项目合同关系对应发货事实。因此不能仅改为“查当前项目设备”就删除记录表的项目引用，否则7条本批资料会失去项目查询路径。该缺口不授权新建设备、猜配其他序列号或改当前归属。

受控证据：factory-existing-project-device-fit-readonly-20261010.json、factory-existing-project-contract-shipment-fit-readonly-20261010.json。均只读；版本字段本身19条没有缺失。
