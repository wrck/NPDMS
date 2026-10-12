# 维护记录原表完整核对与暂缓迁入裁决

状态：USER_DEFERRED。用户已明确“这个是维护记录表，并不是维保表。该项暂不迁入”。此前以维保服务记录命名的建设建议撤回，不再作为实施依据。

暂停 `pm_project_maintenance` 的业务迁入和补写；全部44个原字段核对保留，不能把来源快照计作正式业务承接。本任务此前已落库17条维护记录，经逐条55列核对与原核对证据一致，用户进一步裁决“仅保留裁决和迁移方案”，据此核查后撤回本任务引入的业务数据、结构及代码。真实保修/维保其他来源继续已批准工作。

## 必须先区分的业务主体

旧MaintenanceAction明确区分projectType=10售后、20售前、30非业务、40自定义。20使用pm_presales_project_header.presalesId；30/40的projectId初始化为-1，不是可关联的新平台项目外键。

| 原类型 | 含义 | 全历史记录数 | 原关联核对 |
|---|---|---:|---|
| 10 | 售后项目 | 181525 | 85条无法匹配pm_project_header；待关联，不能新造项目 |
| 20 | 售前项目 | 12122 | 全部精确匹配pm_presales_project_header，不是售后孤立项目 |
| 30 | 非业务事项 | 38480 | 无真实项目外键，不挂靠其他项目 |
| 40 | 自定义事项 | 61303 | 无真实项目外键，不挂靠其他项目 |

本次已批准10个项目内有17条，均有可核实售后项目头。全历史293430条，不把其他三类误归到本批项目。用户裁决本表暂不迁入；四类统计仅保留只读核对依据。未来重启本领域须另行确定业务模型并提交整表裁决。

## 全部44个原字段

有用原字段不会自行丢弃；技术主键依既有INT绑定承接。下表列出全历史非NULL数量，空字符串仍计为非NULL，不能据此认定没有业务值。

| 原字段 | 原类型 | 可空 | 非NULL条数 |
|---|---|---|---:|
| `id` | `int(11)` | NO | 293430 |
| `projectId` | `int(11)` | NO | 293430 |
| `projectCode` | `varchar(45)` | NO | 293430 |
| `projectName` | `varchar(200)` | YES | 293430 |
| `projectType` | `int(11)` | NO | 293430 |
| `projectExecutionState` | `varchar(45)` | YES | 293430 |
| `contractNo` | `varchar(255)` | YES | 293430 |
| `officeCode` | `varchar(25)` | YES | 290521 |
| `compId` | `int(2)` | YES | 293430 |
| `type` | `varchar(45)` | YES | 254951 |
| `category` | `varchar(45)` | YES | 292705 |
| `subCategory` | `varchar(45)` | YES | 292705 |
| `processTime` | `datetime` | YES | 293430 |
| `processDesc` | `varchar(2048)` | YES | 292705 |
| `processStep` | `varchar(2048)` | YES | 292705 |
| `remainProblem` | `varchar(2048)` | YES | 0 |
| `transitHour` | `float` | YES | 293430 |
| `processHour` | `float` | YES | 293430 |
| `itemModel` | `varchar(255)` | YES | 248499 |
| `softVersion` | `varchar(255)` | YES | 248499 |
| `enabledFeatures` | `varchar(255)` | YES | 248499 |
| `customTos` | `varchar(512)` | YES | 292705 |
| `customCcs` | `varchar(512)` | YES | 292705 |
| `hasReport` | `bit(1)` | NO | 293430 |
| `quesnaireId` | `int(11)` | YES | 194534 |
| `deliverFileIds` | `varchar(255)` | YES | 293430 |
| `warrantyStatus` | `varchar(25)` | YES | 194611 |
| `industryName` | `varchar(25)` | YES | 47387 |
| `userOffice` | `varchar(25)` | YES | 293430 |
| `year` | `int(4)` | YES | 241058 |
| `quarter` | `int(1)` | YES | 241058 |
| `month` | `int(2)` | YES | 241058 |
| `wsCount` | `int(2)` | YES | 688 |
| `wafCount` | `int(2)` | YES | 19 |
| `wsYearCount` | `int(2)` | YES | 231351 |
| `wafYearCount` | `int(2)` | YES | 231351 |
| `warrantyInfo` | `varchar(4096)` | YES | 143543 |
| `serviceInfo` | `varchar(2048)` | YES | 1500 |
| `remark` | `varchar(2048)` | YES | 293430 |
| `createTime` | `datetime` | YES | 293430 |
| `createBy` | `varchar(45)` | YES | 293429 |
| `updateTime` | `datetime` | YES | 39321 |
| `updateBy` | `varchar(45)` | YES | 39321 |
| `customInfo` | `json` | YES | 57039 |

## 此前已建55列全部列明（未获整表批准）

以下55列仅记录撤回前的错误结构，以便追溯；它们已不在当前SQL定义或业务实体中。未来迁移必须重新核对既有正式字段，不据此清单恢复原结构。

| 当前字段 | 实际类型 | 可空 |
|---|---|---|
| `id` | `bigint` | NO |
| `tenant_id` | `bigint` | NO |
| `project_id` | `bigint` | NO |
| `source_system` | `varchar(32)` | NO |
| `source_table` | `varchar(64)` | NO |
| `source_record_key` | `varchar(128)` | NO |
| `legacy_maintenance_id` | `bigint` | YES |
| `source_project_id` | `bigint` | YES |
| `source_project_code` | `text` | YES |
| `source_project_name` | `text` | YES |
| `source_project_type` | `int` | YES |
| `source_project_execution_state` | `text` | YES |
| `contract_no` | `text` | YES |
| `office_code` | `text` | YES |
| `source_company_id` | `int` | YES |
| `service_type` | `text` | YES |
| `service_category` | `text` | YES |
| `service_sub_category` | `text` | YES |
| `process_time` | `datetime(3)` | YES |
| `process_desc` | `text` | YES |
| `process_step` | `text` | YES |
| `remain_problem` | `text` | YES |
| `transit_hour` | `double` | YES |
| `process_hour` | `double` | YES |
| `item_model` | `text` | YES |
| `soft_version` | `text` | YES |
| `enabled_features` | `text` | YES |
| `custom_tos` | `text` | YES |
| `custom_ccs` | `text` | YES |
| `has_report` | `int` | YES |
| `source_questionnaire_id` | `bigint` | YES |
| `source_deliver_file_ids` | `text` | YES |
| `warranty_status` | `text` | YES |
| `industry_name` | `text` | YES |
| `user_office` | `text` | YES |
| `service_year` | `int` | YES |
| `service_quarter` | `int` | YES |
| `service_month` | `int` | YES |
| `ws_count` | `int` | YES |
| `waf_count` | `int` | YES |
| `ws_year_count` | `int` | YES |
| `waf_year_count` | `int` | YES |
| `warranty_info` | `text` | YES |
| `service_info` | `text` | YES |
| `remark` | `text` | YES |
| `source_created_at` | `datetime(3)` | YES |
| `source_created_by` | `text` | YES |
| `source_updated_at` | `datetime(3)` | YES |
| `source_updated_by` | `text` | YES |
| `custom_info` | `json` | YES |
| `creator` | `varchar(64)` | NO |
| `create_time` | `datetime(3)` | NO |
| `updater` | `varchar(64)` | NO |
| `update_time` | `datetime(3)` | NO |
| `deleted` | `bit(1)` | NO |

## 暂缓前发现的事实（本轮不据此建设）

- 售后记录与项目头的编码、类型、公司、办事处在可匹配记录中一致；历史项目名称有1处不同，不能把它无声覆盖或删除。
- 原创建时间全有值；更新时间全历史254109条为NULL，本批14条为NULL。正式update_time不允许NULL，需同表裁决；不能自动写迁入时间或沿用其他表裁决。
- 原创建人有1条NULL。当前正式creator/updater不允许NULL，迁入账号和空值规则仍需核对；不能自动补管理员。
- quesnaireId和deliverFileIds是原问卷/交付件引用，不能将原主键直接当现平台对象ID。既有问卷历史领域尚未获本轮启动裁决，记录引用也不得据此生成新结果。
- 原报告标记、维保统计、版本及处理过程保留原义，不把旧提交动作当现流程完成。

证据：原表SHOW FULL COLUMNS及全历史只读统计；旧PMS-struts/src/com/dp/plat/maintenance/action/MaintenanceAction.java第222及358—380行，config-ibaits/sql-map-maintenance-config.xml；私有maintenance-whole-table-readonly-20261009.json、maintenance-full-history-profile-20261009.json。原库写入0。

## 已落库17条处置清单

2026-10-10只读核对：以下17条全部55列未变化。用户裁决“仅保留裁决和迁移方案”后，已核实全部55列未变化、无外键/视图/例程/触发器依赖，撤回以下17条及本任务新增业务表。原来源记录、快照、迁移回执及映射前后比对不变。以下清单是撤回证据，不代表当前正式承接。

| 序号 | 当前记录ID | 当前项目ID | 原记录ID | 处理日期 |
|---:|---|---|---|---|
| 1 | 2108242385634779137 | 992203060189 | 205564 | 2025-02-10 00:00:00 |
| 2 | 2108242385722859522 | 992203060189 | 220252 | 2025-05-08 00:00:00 |
| 3 | 2108242385798356993 | 992203060189 | 220681 | 2025-05-09 00:00:00 |
| 4 | 2108243292506218497 | 992203060190 | 194064 | 2024-10-18 00:00:00 |
| 5 | 2108243292556550146 | 992203060190 | 208081 | 2025-02-24 00:00:00 |
| 6 | 2108243292611076097 | 992203060190 | 208455 | 2025-02-25 00:00:00 |
| 7 | 2108243292678184961 | 992203060190 | 250137 | 2025-10-24 00:00:00 |
| 8 | 2108243324089327617 | 992203060191 | 156580 | 2023-11-06 00:00:00 |
| 9 | 2108243324135464962 | 992203060191 | 179431 | 2024-04-12 00:00:00 |
| 10 | 2108243324194185217 | 992203060191 | 193712 | 2024-10-15 00:00:00 |
| 11 | 2108243324248711170 | 992203060191 | 200093 | 2024-11-04 00:00:00 |
| 12 | 2108243324286459905 | 992203060191 | 234245 | 2025-07-17 00:00:00 |
| 13 | 2108243324332597250 | 992203060191 | 234246 | 2025-07-18 00:00:00 |
| 14 | 2108243324378734593 | 992203060191 | 234380 | 2025-07-21 00:00:00 |
| 15 | 2108243324420677634 | 992203060191 | 235431 | 2025-07-22 00:00:00 |
| 16 | 2108243324466814977 | 992203060191 | 238919 | 2025-08-12 00:00:00 |
| 17 | 2108243441550811137 | 992203060198 | 130457 | 2023-03-27 00:00:00 |

## 仅保留的后续迁移方案

本轮不建设维护记录业务实体，不提供正式资料查询入口，不启用该来源的业务迁入或持续同步。当前承接状态明确为暂不迁入，旧成功回执仅作为不可变历史证据，不作为当前承接完成依据。

若后续获准启动：先按售后、售前、非业务、自定义四类主体确定正式业务归属，再结合现有实体提交整表字段映射；44个原字段必须全部列明，复用同义正式字段，新表/新列逐表裁决。公司、用户、部门、角色复用已经确认的INT迁移转换与绑定；问卷和文件须核实真实目标引用，不能把旧ID直接当新ID。历史处理过程不自动生成当前任务、审批或问卷完成事实。全量记录刷新及来源身份稳定性须先核实，迁移和同步过程复用INT与领域Owner。

## 本轮撤回验证

- 相关25项回归检查通过，包括暂缓状态、无维护业务查询、项目授权及既有回款纠正。
- 完整后端打包通过，维护记录DO、Mapper、Service及XML已从运行包移除。
- 本任务开发库24306/npdms_domain_test撤回17条和新增表；未修改Flyway执行历史，整体开发基线对齐仍待处理，不据本项声称生产上线完成。
- 私有证据：reviewed-maintenance-withdrawal-20261010.json、verified-maintenance-withdrawal-20261010T000905876156.json及对应撤回前备份；原库写入0。

- 运行核验通过：10项目共17维护来源均为USER_DEFERRED_MAINTENANCE_RECORD、正式目标为空；真实浏览器的系统资料入口不含维护记录，历史详情明确显示“仅保留裁决和迁移方案”。回款计划10项目/10条/19字段正式查询回归通过。证据：verified-maintenance-deferred-runtime-20261010.json、maintenance-deferred-browser-20261010.txt、maintenance-deferred-browser-detail-20261010.txt。
