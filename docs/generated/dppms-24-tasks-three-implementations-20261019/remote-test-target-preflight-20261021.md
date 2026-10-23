# 远程测试目标连接、生效核验与24项执行前置

核验时间：2026-10-21T19:14:17.266593+08:00。工作树：`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`；分支`fix/dppms-migration-consolidation-20261012`。

**59284已切换并启动，主、从数据源均指向172.17.90.178:13306/npdms，健康检查UP。数据库认证成功，服务器实见来源npdms_app@10.210.0.11。24项真实同步及重放已获用户授权，但本次执行0、重放0、预览0：新目标缺3张套件元数据表和9张业务目标表；真实登录因缺system_users.employee_no返回500。没有把旧独立验证库的24项READY作为新测试库结果。**

## 连接与运行结果

| 项目 | 实查结果 |
|---|---|
| MySQL目标 | 172.17.90.178:13306/npdms；MySQL 8.4.11；authenticated=npdms_app@% |
| 允许来源 | 10.210.0.11；TCP实际出站及MySQL USER()观察一致 |
| 专用服务 | 127.0.0.1:59284；PID 44176；local,dac；已提交工作树对应既有冻结包，未改包 |
| 数据源 | master/slave同时切换；新套件目标跟随服务数据源，未把目标库误登记成原系统来源 |
| Redis | 127.0.0.1:24379/15，启动前为空；保留原独立验证库使用的DB14，避免跨目标复用登录或权限缓存 |
| 凭据 | Windows DPAPI CurrentUser加密，仅本地.run保存；未将明文写入代码、日志、命令行或Git |
| 自动初始化 | Flyway=false、Flowable结构更新=false、SQL init=never、Batch/Quartz JDBC初始化=never；Batch/Quartz/事件后台作业关闭 |
| 正式登录 | 使用已有文档中验证过的账号尝试1次，API code=500，Unknown column employee_no；不是数据库密码认证失败，没有重置密码或绕过登录 |
| 真实任务 | 没有注册新任务、伪造任务ID或调用execute；没有预览/真实同步/重放回执 |

本轮授权允许本测试库24项新独立任务实际执行及重放，同时禁止迁移、原任务和59191操作。该授权不将此前仅对npdms_reverify_20261014批准的列补丁转移到本库，也不改变两条串货暂缓和其他已决来源边界。

本地入口：[受保护目标配置](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/.run/base-sync-remote-test/target.encrypted.json)；[专用启动入口](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/.run/base-sync-remote-test/start-runtime.ps1)。配置和脚本均被Git忽略；运行时解密后只经子进程环境变量注入，不向共享配置模板写真实凭据。

## 已证实的结构差异

### 1. 所有24项共有的阻断

目标缺`int_base_sync_task`、`int_base_sync_run`、`int_base_sync_binding`。正式注册、运行认领、事务回执及来源指纹保护均依赖它们，不能绕过这些表直接往业务表写数据。定义依据：[现有V483](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V483__independent_base_sync_metadata.sql)，多来源目标索引及全量容量依据[现有V484](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql)。本轮没有执行这些文件。

`int_sync_connection`当前0条；合法来源连接尚未注册。须先解决登录和新元数据结构，然后才能通过正式接口创建本套件来源连接及24项新任务，不能复制或操作原任务来填前置。

### 2. 另缺9张显式业务目标表

| 任务 | 缺失表 | 既有定义追溯 |
|---|---|---|
| P20 | `proj_project_real_product` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql) |
| P21 | `proj_project_lease_product`, `proj_project_product_configuration` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql) |
| P23 | `com_contract_collection_plan` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V412__dppms_native_detail_field_completion.sql) |
| P25 | `ast_device_warranty_renewal_record` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V473__approved_device_warranty_renewal_record.sql) |
| P34 | `ast_device_factory_version_record` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V414__dppms_native_source_business_records.sql) |
| P35 | `ast_device_software_change_batch`, `ast_device_software_version_record` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V414__dppms_native_source_business_records.sql) |
| P37 | `ast_device_license_record` | [既有定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V433__approved_device_license_record.sql) |

这些是当前工作树已审任务的明确目标名与新测试库的实查差异，不能据V507版本号推断已包含本分支全部结构，也不能自动恢复、改名或改投其他业务表。DDL文件仅用于追溯，不构成本轮执行迁移的授权。

### 3. 已存在表的19个显式INSERT列缺失

| 任务 | 正式表 | 缺列 |
|---|---|---|
| P04 | `system_users` | `custom_info`, `employee_no` |
| P11 | `ast_product_official_info` | `product_category`, `product_type` |
| P15 | `com_shipment_contract_reference` | `warranty_years` |
| P15 | `com_shipment_package` | `carrier_code`, `shipment_contract_source_key` |
| P16 | `proj_project` | `custom_info`, `disabled`, `effective_from`, `effective_to`, `order_create_time` |
| P22 | `com_delivery_scope` | `allocation_confirmed`, `delivered_quantity`, `open_quantity` |
| P31 | `proj_project_member_assignment` | `email`, `member_code`, `phone_number`, `registration_source` |

其中`system_users.employee_no`已经在真实登录查询中复现报错，`custom_info`也由元数据核实缺失。没有删除DO字段或省略查询来掩盖目标结构不匹配。此处静态核查覆盖显式目标表及INSERT列，尚未进入每项业务查询、授权和逐行真实来源校验，不能视为全部差异已经穷尽。

### 4. 已决原文容量与新目标仍有差异

| 任务 | 当前目标定义 | 已审要求 |
|---|---|---|
| P10 | cus_customer_master.name VARCHAR(128) NOT NULL | 完整原名，上限255 |
| P33 | imp_eng_installation.install_location VARCHAR(255) NULL | 完整原文，上限1024；快照同时保留fallbackLocation |

ERP客户列已存在，为VARCHAR(50) NULL。没有重跑旧补丁、截断数据、填占位或将P33短列改NULL来规避容量；本库两个容量差异仍需外部结构前置处理。

## 24项实际验证状态

以下每项均因共有三张元数据表缺失而未注册、未执行、未重放。附加差异只列当前静态已证实内容；来源数据、业务授权、幂等和失败回滚尚不能验收。

| 任务 | 本目标实际运行 | 额外已证实差异 |
|---|---|---|
| P01 公司、部门及主管 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P04 账号、任职、角色、成员和范围 | 未注册 / 执行0 / 重放0 | 缺列 system_users: custom_info,employee_no |
| P08 系统字典类型及字典项 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P10 CRM客户主档 | 未注册 / 执行0 / 重放0 | cus_customer_master.name容量128→255 |
| P11 CRM产品主档 | 未注册 / 执行0 / 重放0 | 缺列 ast_product_official_info: product_category,product_type |
| P12 DPPMS缺失产品补齐 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P13 执行单头 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P14 销售订单及明细 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P15 合同及全量发货历史 | 未注册 / 执行0 / 重放0 | 缺列 com_shipment_contract_reference: warranty_years；缺列 com_shipment_package: carrier_code,shipment_contract_source_key |
| P16 项目主档及独立来源身份 | 未注册 / 执行0 / 重放0 | 缺列 proj_project: custom_info,disabled,effective_from,effective_to,order_create_time |
| P18 项目合同关系 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P19 项目相关方及历史状态属性 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P20 项目真实产品历史清单 | 未注册 / 执行0 / 重放0 | 缺表 proj_project_real_product |
| P21 租赁与配置展示缓存 | 未注册 / 执行0 / 重放0 | 缺表 proj_project_lease_product,proj_project_product_configuration |
| P22 项目产品实施范围 | 未注册 / 执行0 / 重放0 | 缺列 com_delivery_scope: allocation_confirmed,delivered_quantity,open_quantity |
| P23 合同收款计划展示缓存 | 未注册 / 执行0 / 重放0 | 缺表 com_contract_collection_plan |
| P24 合同回款信息 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P25 设备续保历史 | 未注册 / 执行0 / 重放0 | 缺表 ast_device_warranty_renewal_record |
| P30 正式发货设备基础承接 | 未注册 / 执行0 / 重放0 | 共有元数据和登录前置；尚未逐项进入业务验证 |
| P31 项目成员及最终客户联系人 | 未注册 / 执行0 / 重放0 | 缺列 proj_project_member_assignment: email,member_code,phone_number,registration_source |
| P33 设备安装历史 | 未注册 / 执行0 / 重放0 | imp_eng_installation.install_location容量255→1024 |
| P34 出厂版本历史 | 未注册 / 执行0 / 重放0 | 缺表 ast_device_factory_version_record |
| P35 软件批次及版本历史 | 未注册 / 执行0 / 重放0 | 缺表 ast_device_software_change_batch,ast_device_software_version_record |
| P37 整表许可历史 | 未注册 / 执行0 / 重放0 | 缺表 ast_device_license_record |

## 保留边界与下一步

已核对507张表清单及全列定义均保持，17张关键表结构和被核对业务/原任务计数保持；Flyway历史仍为原10条、末版507。原任务、运行、绑定和来源连接均仍0条，核心项目、合同、订单、设备、安装等被核对业务表仍0。正常登录失败可能生成框架错误/登录审计，没有将其写成业务同步。

后续顺序为：外部提供与当前工作树已审目标匹配的测试库结构 → 重新核验正式登录和授权 → 正式注册只读来源连接与24项新任务 → 按依赖真实预览、执行及重放 → 核对来源绑定、幂等、回滚和历史保护。结构兼容就绪前不自动补表、补列、调整唯一键、运行Flyway或以直接DML绕过任务流程。

本轮没有永久业务同步、原任务操作、DDL、迁移、授权修改、源数据修正或59191操作；仅启动原本未运行的59284，没有停止任何进程。

[远程实际身份与结构原件](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remote-test-20261021/target-before.json)；[服务健康、运行标志与真实连接](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remote-test-20261021/service-ready.json)；[实际登录错误](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remote-test-20261021/login-schema-failure.json)；[原任务/业务/结构保护核验](D:/CodexTaskArtifacts/project-detail-followup/base-sync-remote-test-20261021/final-verification.json)；[逐项机器可读结果](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/remote-test-target-preflight-20261021.json)。
