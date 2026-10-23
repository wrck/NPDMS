# npdms远程测试库最小DDL审阅清单

目标：`172.17.90.178:13306/npdms`。取数：2026-10-21T20:01:03.119448+08:00。当前工作树`fix/dppms-migration-consolidation-20261012`，HEAD `e4135f96c`。

**本轮仅SELECT/SHOW和离线整理，DDL、迁移、同步、任务写入和服务操作均0。A稿25条DDL：13张缺表直接按最终定义创建、19个缺列、8个既存列定义修改及必要索引/CHECK。B稿仅列P22尚无正式DDL承载的结构例外，SQL全部注释，不混入A。**

- [A：完整最小补齐DDL（25条）](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/remote-test-minimal-ddl-A-20261021.review.sql)。
- [B：P22结构例外候选（全注释、未确认）](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/remote-test-minimal-ddl-B-p22-exception-20261021.review.sql)。
- [逐条定义来源、字段和离线核对清单](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/remote-test-minimal-ddl-review-20261021.json)。

本稿不修改同步逻辑、迁移原件、工作树服务配置、原任务或59191。所有SQL对象显式限定`npdms`，没有DML、来源清洗、占位父记录、授权写入、禁用外键检查、禁用CHECK或Flyway历史写入。

## 一、13张缺表

此前仅扫描各Pxx目标Mapper时列为12张；这次纳入共享项目身份查询，补充发现`proj_legacy_detail_source`。它是新任务现有身份保护查询的依赖，不是重启旧任务或导入旧来源证据。

| A编号 | 缺表 | 当前正式定义与折叠项 |
|---|---|---|
| A01 | `int_base_sync_task` | [V483__independent_base_sync_metadata.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V483__independent_base_sync_metadata.sql:3)；[V484__base_sync_aliases_and_full_shipment_limit.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql:9)；max_rows CHECK上限直接为500万，不插入/调整任务配置 |
| A02 | `int_base_sync_run` | [V483__independent_base_sync_metadata.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V483__independent_base_sync_metadata.sql:24) |
| A03 | `int_base_sync_binding` | [V483__independent_base_sync_metadata.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V483__independent_base_sync_metadata.sql:53)；[V484__base_sync_aliases_and_full_shipment_limit.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql:3)；来源唯一保持，目标索引为普通索引，允许已审多来源归并 |
| A04 | `proj_legacy_detail_source` | [V411__project_legacy_detail_evidence.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V411__project_legacy_detail_evidence.sql:3) |
| A05 | `proj_project_real_product` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:36) |
| A06 | `proj_project_lease_product` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:57)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:34)；project_id可空及已有正式缓存查询索引 |
| A07 | `proj_project_product_configuration` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:78)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:34)；project_id可空及已有正式缓存查询索引 |
| A08 | `com_contract_collection_plan` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:107)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:37)；原创建/更新时间可空及已有合同号查询索引 |
| A09 | `ast_device_software_change_batch` | [V414__dppms_native_source_business_records.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V414__dppms_native_source_business_records.sql:53) |
| A10 | `ast_device_software_version_record` | [V414__dppms_native_source_business_records.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V414__dppms_native_source_business_records.sql:14) |
| A11 | `ast_device_factory_version_record` | [V414__dppms_native_source_business_records.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V414__dppms_native_source_business_records.sql:69)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:47)；device_sn可空 |
| A12 | `ast_device_license_record` | [V433__approved_device_license_record.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V433__approved_device_license_record.sql:2) |
| A13 | `ast_device_warranty_renewal_record` | [V473__approved_device_warranty_renewal_record.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V473__approved_device_warranty_renewal_record.sql:2) |

最小范围只取实际依赖的CREATE，不运行其整份历史迁移：不创建V411中的snapshot/record，不创建V414中其他保修镜像/来源事实表，不写菜单、示例、原任务或历史回执。P25按V473的原SN续保实体，不另加device_id；P37保留许可密文列及可空设备/合同引用。

三张BASE表直接折叠V484的最终定义：任务容量CHECK上限500万，绑定源唯一、目标普通索引。没有先创建错误约束再删除，也没有提高任何任务实际容量或注册任务。P21的project_id、P23的原时间、P34的原SN可空等后续已定义规则直接折入CREATE。

## 二、19个缺列

| 正式表 | 必补列及定义 | 来源 |
|---|---|---|
| `system_users` | `employee_no VARCHAR(50) NULL`<br>`custom_info JSON NULL` | [V419__approved_user_employee_no_and_custom_info.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V419__approved_user_employee_no_and_custom_info.sql:4)；[V419__approved_user_employee_no_and_custom_info.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V419__approved_user_employee_no_and_custom_info.sql:5) |
| `ast_product_official_info` | ``product_category` varchar(100) DEFAULT NULL`<br>``product_type` varchar(100) DEFAULT NULL` | [V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:9)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:10) |
| `com_shipment_contract_reference` | `warranty_years INT NULL` | [V258__project_party_and_contract_source_tables.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V258__project_party_and_contract_source_tables.sql:152)；[V294__migration_field_naming_and_comment_alignment.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V294__migration_field_naming_and_comment_alignment.sql:319) |
| `com_shipment_package` | `carrier_code VARCHAR(256) NULL`<br>`shipment_contract_source_key VARCHAR(128) NULL` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:106)；[V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:30) |
| `proj_project` | `custom_info JSON NULL`<br>`order_create_time DATETIME(3) NULL`<br>`disabled BIT(1) NULL`<br>`effective_from DATETIME(3) NULL`<br>`effective_to DATETIME(3) NULL` | [V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:101)；[V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:102)；[V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:103)；[V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:104)；[V412__dppms_native_detail_field_completion.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql:105) |
| `com_delivery_scope` | `allocation_confirmed BIT(1) NOT NULL DEFAULT b'1'`<br>`delivered_quantity DECIMAL(18,6) NULL`<br>`open_quantity DECIMAL(18,6) NULL` | [V421__approved_dppms_delivery_scope_attributes.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:4)；[V421__approved_dppms_delivery_scope_attributes.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:5)；[V421__approved_dppms_delivery_scope_attributes.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:6) |
| `proj_project_member_assignment` | `member_code VARCHAR(45) NULL`<br>`phone_number VARCHAR(20) NULL`<br>`email VARCHAR(45) NULL`<br>`registration_source VARCHAR(2) NULL` | [V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:4)；[V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:5)；[V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:6)；[V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:7) |

`system_users.employee_no`缺失已在上一轮真实登录复现。增加该列及custom_info遵循V419，不通过删DO字段或省略查询绕过。项目/账号JSON列只补已有结构，不恢复已批准暂缓的拓展属性承接。产品表已有`crm_product_code/product_sub_code`予以保留，不猜测它们可以替代本工作树正式`product_category/product_type`，不改名、不回填。

## 三、8个既存列定义修改

| 表/列 | 当前定义 | 本稿最小修改 | 正式依据 |
|---|---|---|
| `com_delivery_scope.current_order_line_id` | `(case when ((`deleted` = 0) and (`effective_to` is null)) then `order_line_id` else NULL end)` | `current_order_line_id BIGINT GENERATED ALWAYS AS (CASE WHEN allocation_confirmed=b'1' AND deleted=0 AND effective_to IS NULL THEN order_line_id ELSE NULL END) STORED` | [V421__approved_dppms_delivery_scope_attributes.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:7) |
| `proj_project_member_assignment.create_time` | `datetime NOT NULL` | `create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，未确认历史可保留原NULL'` | [V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:10) |
| `proj_project_member_assignment.update_time` | `datetime NOT NULL` | `update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，未确认历史可保留原NULL'` | [V434__approved_project_member_history_fields.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V434__approved_project_member_history_fields.sql:11) |
| `proj_project_party.creator` | `varchar(64) NOT NULL` | `creator VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人'` | [V461__preserve_project_party_nullable_audit.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V461__preserve_project_party_nullable_audit.sql:3) |
| `proj_project_party.updater` | `varchar(64) NOT NULL` | `updater VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人'` | [V461__preserve_project_party_nullable_audit.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V461__preserve_project_party_nullable_audit.sql:4) |
| `com_sales_order_line.order_id` | `bigint NOT NULL` | `order_id BIGINT NULL COMMENT '订单ID'` | [V481__sync_decided_configuration_contracts.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql:27) |
| `cus_customer_master.name` | `varchar(128) NOT NULL` | ``name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称'` | [P10-P33-column-capacity.review.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/P10-P33-column-capacity.review.sql:6) |
| `imp_eng_installation.install_location` | `varchar(255) NULL` | ``install_location` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置'` | [P10-P33-column-capacity.review.sql](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/P10-P33-column-capacity.review.sql:11) |

P10/P33仅扩长度，字符集、排序规则、NULL/NOT NULL、默认值及注释按本目标SHOW CREATE保留：

```sql
ALTER TABLE `npdms`.`cus_customer_master`
  MODIFY COLUMN `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称';

ALTER TABLE `npdms`.`imp_eng_installation`
  MODIFY COLUMN `install_location` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置';
```

255/1024原文承接规则已经裁决，本轮不重复确认规则；此前只允许修改npdms_reverify_20261014的执行授权不会自动转移到本库，这两条仍只是待确认DDL。

成员历史两列时间允许显式原NULL，同时按V434新增`ck_proj_member_confirmed_identity_audit`，保护已确认授权必须有真实user_id与审计时间。既存`user_id`已可空、`authorization_confirmed`列和最终用户身份CHECK已存在，不重复添加、不覆盖。`chk_proj_member_dates`保持，既决152条日期倒置的“只预览保留”不会变成真实写入许可。

P19只把creator/updater按V461改为可空并保留现有字符集/排序，不放宽其创建/更新时间；不新增猜测性的时间例外。

## 四、必要索引、生成列及CHECK

| 范围 | 当前情况 | 最小处理与保护边界 |
|---|---|---|
| P11产品码 | 仅有来源复合唯一键，缺租户＋产品码唯一键；当前数据0 | 按V481增加uk_product_official_tenant_code，不修改既存产品码/名称/技术规格类型，它们已为100/500/LONGTEXT |
| P14父引用索引 | uk_sales_order_line(tenant_id,order_id,line_no)同时支撑现有父订单外键 | A14先增加普通idx_sales_order_line_parent(tenant_id,order_id)，A23再替换旧业务唯一键；父头外键不删 |
| P14行业务唯一键 | 旧唯一键是父ID＋行号；历史V481候选写order_type | 依据用户后续明确的行键，新增tenant_id＋source_system＋company_code＋line_type＋order_no＋line_no唯一键；不再使用已被后续裁决替代的order_type定义 |
| P22分配占用 | current_order_line_id及uk_scope_version未考虑allocation_confirmed | 按V421补列的同时更新生成表达式、版本唯一键；未确认范围不占用当前/版本订单分配槽。已有idx_scope_order_line支撑FK，不需另加索引 |
| P31已确认成员 | 存在authorization_confirmed列，缺V434的身份与时间CHECK | 补CHECK；保留原日期和最终用户CHECK，不以补空字段降低当前授权保护 |

MySQL要求外键有可用的前缀索引；本稿以新增等效索引保留P14外键，不使用关闭外键检查来删索引。[MySQL 8.4正式说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)。生成列表达式变更可能重建表，不把本稿声明为已完成在线DDL验证；[生成列ALTER说明](https://dev.mysql.com/doc/refman/8.4/en/alter-table-generated-columns.html)。

订单头业务唯一键、租户行唯一键、其他外键和CHECK均保留。各新增表的索引直接取原定义及已定义后续修改，不额外引入业务唯一约束。

## 五、B：P22未由正式DDL闭合的结构冲突

原业务裁决已明确：保留原空部门资料，两个ID先-1，按本次独立绑定统一后置回填；本轮不重开该业务问题。物理约束则仍为：

| 项目 | 当前定义 | 对既决直写的影响 |
|---|---|---|
| fk_scope_order_line | (tenant_id,order_line_id)即时引用正式订单行 | 事务内首次INSERT的-1没有父记录便被拒绝，不能靠事务结束前回填解决 |
| 订单快照3列 | order_source_system、order_company_code、order_type均NOT NULL | 订单尚未解析时的原NULL不能写入 |
| 部门3列 | department_code、department_name、department_version均NOT NULL | 已决空部门资料无法原样承接 |

现有工作树正式DDL中没有删除上述外键或放宽这六列的定义。因此没有冒充“已有正式定义”塞入A。B给出一个最小技术例外候选：删该单一外键、仅六列改NULL，保持order_line_id/department_id为NOT NULL、主键/唯一键/索引、正数量及日期CHECK。所有SQL行均注释，未执行。

该候选会使整张范围表的普通业务写入也失去数据库级订单行参照保护，应用检查不是等价替代；是否采用此结构例外，或由正式Owner提供其他承载方案，仍须明确结构决定。本稿不自建-1父订单、占位部门、临时业务表或新状态，也不改同步逻辑。即使A完成，P22真实执行仍不能据此放行。MySQL拒绝无父键子行的正式依据见[外键规则](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)。

## 六、此次明确不做的变更

- ERP列及已存在的CRM字段、产品100/500/LONGTEXT容量保持，不运行整份V481。
- 不删既有crm_product_code/product_sub_code、external_mobile/external_email/title等本目标已有字段。
- 不复制本地库全部结构、不修改任何已有任务/运行/绑定，不创建原任务或来源连接记录。
- 不把缺公司授权、正式输入、原日期有效性和两条串货暂缓当作DDL问题处理。
- 不恢复未被新套件查询的旧快照/镜像表，不承接已暂缓拓展属性。

## 七、验证与确认边界

A稿13个CREATE与8个普通ALTER可被现有SQLGlot解析为类型节点；另外4个MySQL专有多动作ALTER被该离线解析器降为Command，逐条以正式定义和MySQL文档核对。25条均有目标表、字段和来源记录，但没有在任何数据库执行，包括临时表；不将离线整理冒充MySQL引擎执行通过。

SQL全部限定npdms，A无DML和禁用约束语句；B有效SQL为0。确认时可明确“A25条是否整体采用、B是否接受结构例外”，此处只交付审阅件，不发起执行。后续即使DDL获批，仍需新来源连接、新任务注册、正式授权与数据前置、真实预览/执行/重放，不能据补齐结构宣布24项功能验收。

[本轮只读结构及计数原件](D:/CodexTaskArtifacts/project-detail-followup/remote-ddl-review-20261021/target-current.json)；[只读及未执行保护证明](D:/CodexTaskArtifacts/project-detail-followup/remote-ddl-review-20261021/review-proof.json)；[生成与逐条来源清单](D:/CodexTaskArtifacts/project-detail-followup/remote-ddl-review-20261021/ddl-manifest.json)；核查脚本：[inspect-target-schema.py](D:/CodexTaskArtifacts/project-detail-followup/remote-ddl-review-20261021/inspect-target-schema.py)、[build-ddl.py](D:/CodexTaskArtifacts/project-detail-followup/remote-ddl-review-20261021/build-ddl.py)。
