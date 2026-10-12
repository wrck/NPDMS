# 本地 DPPMS 项目关联资料专项验收

> 2026-10-09补充：本记录是V411历史资料证据功能的当时验证，不证明V4正式业务承接完成。后续V4存在重复结构及正式字段错误，完成结论已撤回，见[纠正审查](2026-10-09-dppms-v4-carry-correction.md)。

日期：2026-10-08。归属：专项实现与验证结果，存放于 `docs/generated/`。
范围来源：需求方在项目创建迁移续作中确认的 10 个既有本地项目，以及“旧交付件暂不同步”的明确边界。

## 交付结果

10 个项目已同步 44 类历史资料，共 5432 条领域记录；每个项目具有独立来源绑定和一个当前不可变快照。重复批次重放 10 次、相同内容重读 10 次，均未增加快照。领域记录数包含不同来源表或视图对同一业务事实的表示，不能作为独立设备数量。

普通项目详情和继承项目详情均增加“DPPMS 关联资料”入口，提供 8 组类别、来源计数、分页、全部字段抽屉、上级/下级记录及问卷引用跳转。查询绑定项目、租户、来源及快照，历史状态、历史人员和审批意见按来源展示。

54 条旧交付件及其文件内容在本次范围之外。历史通知中关于上传交付件的文字属于通知记录；页面另有“旧交付件及附件暂未同步”提示。

当前 10 个项目仍为 ACTIVE、UNASSIGNED、S0，分类为 9 个 GENERAL、1 个 ENGINEERING。前后比对覆盖 120 张项目/租户范围表的计数，以及项目、人员、任务、Gate、资产、交付件和商务关联等 16 组完整行数据，计数和内容均无变化。

## 来源审计和实现依据

旧系统入口为 `ProjectAction.updateProject` / `projectmodify.jsp`；审计同时读取 `ProjectServiceImpl`、`config-ibaits/sql-map-project-config.xml` 及回访、分包、维护相关查询。以实际执行的项目/合同/订单/问卷/流程关联确定范围；已注释的旧流程分支未作为当前来源查询。

- 原项目、项目组与合同按确认的关系绑定；执行单、订单和发货事实使用已批准来源清单中的明确来源编号。
- 订单行关联同时使用订单号、公司和来源；历史设备查询保留显示合同号以及旧服务派生的合同号规则。
- 问卷回填模板、题目和选项；审批意见使用精确流程键与对象编号，避免不同业务复用编号时串入。
- 无唯一键的来源行按脱敏后的内容及重复出现次数保留，重复原始行不被静默删除；来源外键保留在字段中。
- 来源查询由固定表、字段和条件组成，并运行在只读一致性事务中。目标写入统一经过已授权 API 和 `ProjectLegacyDetailService`。
- 敏感文本在形成载荷和复合键之前隐藏，服务端再次检查并记录隐藏字段；原始 BIT(1) 字节保存在快照中，页面显示可读的 0/1。

服务端检查项目管理范围、来源合同关联、完整领域计数、根记录、父记录存在性、幂等批次、旧读取时间与期望快照版本；写入来源、快照、记录及更新当前版本在同一事务中完成。父关系的来源外键语义由本次固定来源适配器解析，并已逐条核对；服务端自身的父关系检查止于快照内存在性。扩展其他适配器时需单独确认其外键解析规则。

ISO 读取时间使用字段级 Jackson 3 反序列化，覆盖框架默认的时间戳读取行为。分页过程中保留同一快照和筛选范围的总数，避免真实分页控件将第二页回退到第一页；项目或领域切换时取消旧响应的更新资格。

相关来源定义在 `pms-module-project/src/main/resources/projectlegacy/dppms-detail-domains.json`，固定源查询在 `scripts/dppms_project_detail_sync.py`，目标查询在 `ProjectLegacyDetailMapper.xml`，两个详情入口共用 `ProjectLegacyDetailPanel.vue` 和 `ProjectLegacyRecordDetails.vue`。

## 领域覆盖

字段列表示完整详情抽屉的目录字段数；记录数是本次 10 个项目的合计。零条领域已查询并纳入快照计数。

| 类别 | 资料 | 来源表或视图 | 字段 | 记录 |
|---|---|---|---:|---:|
| 项目与人员 | 原项目基本信息 | `pm_project_header` | 32 | 10 |
| 项目与人员 | 原项目状态记录 | `pm_project_state` | 9 | 10 |
| 项目与人员 | 历史相关方 | `pm_project_related_party` | 11 | 55 |
| 项目与人员 | 历史人员与联系方式 | `pm_project_member` | 15 | 70 |
| 商务资料 | 原项目组关系 | `pm_project_group_relationship` | 9 | 10 |
| 商务资料 | 来源合同关联 | `pm_project_contract` | 7 | 10 |
| 商务资料 | 来源执行单 | `pm_project_property_from_sms` | 32 | 10 |
| 商务资料 | 来源销售订单 | `pm_order_data_from_erp` | 15 | 10 |
| 商务资料 | 来源订单行 | `pm_order_line_from_erp` | 15 | 105 |
| 商务资料 | 原项目产品 | `pm_project_product_line` | 11 | 105 |
| 商务资料 | 来源销售计划 | `pm_pb_plan_from_sms` | 16 | 10 |
| 设备与软件 | 来源发货事实 | `fb_shipment_barcode` | 19 | 1284 |
| 设备与软件 | 历史设备安装及转移 | `pm_project_shipment` | 21 | 948 |
| 设备与软件 | 历史软件变更批次 | `pm_project_soft_change_logs` | 9 | 6 |
| 设备与软件 | 历史软件版本 | `pm_project_soft_version` | 27 | 659 |
| 实施与维护 | 历史计划任务 | `pm_project_task` | 26 | 37 |
| 实施与维护 | 历史维护记录 | `pm_project_maintenance` | 39 | 17 |
| 回访与审批 | 历史回访 | `pm_cl_callback` | 13 | 4 |
| 回访与审批 | 历史回访问卷关联 | `pm_cl_callback_quesnaire` | 12 | 4 |
| 回访与审批 | 历史问卷答卷 | `pm_cl_quesnaire_result_header` | 10 | 21 |
| 回访与审批 | 历史问卷答案 | `pm_cl_quesnaire_result_line` | 13 | 87 |
| 回访与审批 | 历史审批意见 | `fnd_act_hi_comment` | 12 | 33 |
| 分包资料 | 历史分包 | `pm_subcontract_project_header` | 25 | 3 |
| 分包资料 | 历史分包付款 | `pm_subcontract_project_payment` | 12 | 5 |
| 分包资料 | 历史分包报价 | `pm_subcontract_project_price` | 13 | 3 |
| 工单与许可 | 历史关联工单 | `pm_project_incident_table_from_itr` | 35 | 9 |
| 工单与许可 | 历史许可信息（不含许可密钥） | `pm_project_license_info_from_license` | 7 | 153 |
| 其他历史 | 历史通知记录 | `pm_project_notification` | 6 | 126 |
| 其他历史 | 历史周报 | `pm_project_weekly` | 14 | 0 |
| 其他历史 | 历史周报内容 | `pm_project_weekly_content` | 9 | 0 |
| 其他历史 | 历史周报反馈 | `pm_project_weekly_feedback` | 5 | 0 |
| 其他历史 | 历史指导与反馈 | `pm_project_instruction` | 11 | 0 |
| 商务资料 | 来源真实产品清单 | `pm_project_product_line_real` | 12 | 0 |
| 商务资料 | 来源租赁产品 | `pm_project_product_lease_line_from_crm` | 11 | 0 |
| 商务资料 | 来源产品配置层级 | `pm_project_product_config_level_info_from_crm` | 11 | 0 |
| 实施与维护 | 历史监理 | `pm_project_supervision` | 16 | 0 |
| 设备与软件 | 来源发货物流 | `view_shipment_ems_4_pm` | 6 | 19 |
| 设备与软件 | 来源发货产品及子设备 | `view_shipment_info_4_pm` | 11 | 1284 |
| 设备与软件 | 来源维保信息 | `view_warranty` | 36 | 118 |
| 设备与软件 | 来源主子设备关系 | `fb_shipment_barcode_relation` | 8 | 0 |
| 设备与软件 | 来源出厂软件版本 | `fb_soft_version` | 5 | 19 |
| 回访与审批 | 历史问卷模板 | `pm_cl_quesnaire_template_header` | 14 | 8 |
| 回访与审批 | 历史问卷题目 | `pm_cl_quesnaire_template_line` | 15 | 48 |
| 回访与审批 | 历史问卷选项 | `pm_cl_quesnaire_template_options` | 13 | 132 |
| 合计 | 44 类 | | | 5432 |

## 项目核对

公开记录采用匿名项目编号。来源编号、客户清单、实际记录内容和快照校验值保留在本地私有证据中。

| 匿名项目 | 当前快照记录 | 浏览器验收 |
|---|---:|---|
| MIG-P01 | 2377 | 通过 |
| MIG-P02 | 1167 | 通过 |
| MIG-P03 | 716 | 通过 |
| MIG-P04 | 95 | 通过 |
| MIG-P05 | 305 | 通过 |
| MIG-P06 | 21 | 通过 |
| MIG-P07 | 300 | 通过 |
| MIG-P08 | 65 | 通过 |
| MIG-P09 | 184 | 通过 |
| MIG-P10 | 202 | 通过 |

## 实际验证

| 验证 | 结果与覆盖范围 |
|---|---|
| 后端专项测试 | 19 条通过：规范化 8、服务规则 9、指定本地 MySQL 集成 2；无跳过 |
| MySQL 事务 | 实际 Spring 事务代理和 Mapper XML；强制版本更新冲突后，来源/快照/记录均回滚；保留旧快照并校验租户隔离 |
| 来源适配器 | 6 条通过：订单行范围、重复无键行、脱敏、跨表同编号、合同派生规则、问卷关系 |
| 前端运行时 | 6 条通过：精确字符串编号、固定快照、真实 ElPagination 延迟第二页、旧响应清理、错误和未同步状态、关联筛选/引用 |
| 全量记录 | 5432 条脱敏后来源值与目标 JSON 逐项相等，字段差异 0；父关系和读取时间一致 |
| 实际接口 | 10 项目 × 44 域 = 440 次数量核对；分页与父记录筛选正确 |
| 失败路径 | 同批次改内容、错误期望快照、旧读取时间、跨项目来源/快照、越界分页、匿名与其他租户访问共 8 类；拒绝请求未产生写入 |
| 真实浏览器 | 正常登录；逐个打开 10 项目，核对 440 个类别计数并展开每个项目的 32 个基本字段；继承详情另核对 44 类与全部基本字段 |
| 浏览器明细 | 状态 9 字段、订单 15 字段、问卷回答 13 字段、保修 36 字段、出厂软件 5 字段；订单到订单行、回答到准确题目/选项跳转；原停用标记显示 0 |
| 浏览器分页与空状态 | 38 条订单行第一页 20、第二页 18，编号不重叠；零条历史周报显示明确空状态；当前详情页控制台错误 0 |
| 构建 | 后端相关模块编译打包通过；最终前端 Vite 构建通过。本机默认并发曾触及内存限制，最终采用 RAYON_NUM_THREADS=2、3072 MB Node 堆完成 |
| 完整前端类型检查 | 66 项错误，分布于 26 个文件；这些文件均与本分支原 HEAD 内容相同，本次修改/新增文件无诊断 |

真实浏览器的交互、字段和 DOM 证据已取得；当前浏览器截图工具返回零尺寸视口，未取得像素布局截图。临时视口设置已恢复。

## 本地应用和复跑入口

本次目标为已指定的本地 `npdms_domain_test`。仅应用 `V411__project_legacy_detail_evidence.sql` 新建三张资料证据表。将本分支尚未执行的 V400 资料表草案顺延到 V411，避开目标已经执行的另一份 V400；已执行的迁移历史保持原记录。

候选前端：`http://127.0.0.1:19082`；候选后端：`http://127.0.0.1:59281/admin-api`。两个入口分别为 `/pms/project-management/project-master-detail`、`/pms-inheritance/project-detail`，均支持 `projectId` 和 `tab=legacy-detail`。候选服务通过新增隔离端口启动。

来源读取配置通过 `DPPMS_HOST`、`DPPMS_PORT`、`DPPMS_USER`、`DPPMS_PASSWORD`、`DPPMS_DATABASE` 环境变量提供；应用模式另使用 `NPDMS_ACCESS_TOKEN`、`NPDMS_TENANT_ID`。来源和目标凭据不写入清单、命令行或日志。

```powershell
# 私有清单路径和批次键替换为已确认值；省略 --apply 时仅作来源读取预演
python scripts/dppms_project_detail_sync.py --manifest '私有清单绝对路径' --batch-key '本次批次键'
python scripts/dppms_project_detail_sync.py --manifest '私有清单绝对路径' --batch-key '本次批次键' --api-base http://127.0.0.1:59281/admin-api --apply

mvn -pl pms-module-project -am test "-Dtest=LegacyDetailNormalizerTest,ProjectLegacyDetailServiceTest,ProjectLegacyDetailMySqlTest" "-Dsurefire.failIfNoSpecifiedTests=false"
python -m unittest discover -s scripts/tests -p test_dppms_project_detail_sync.py -v
# 在前端目录执行
pnpm exec vitest run --config vitest.pms-file.config.ts src/views/pms/project/project-master-detail/components/projectLegacyDetail.runtime.spec.ts
```

MySQL 两条集成测试默认跳过；实际验收通过环境变量 `NPDMS_LEGACY_DETAIL_MYSQL=true` 以及指定本地 URL/账号配置启用，使用独立合成来源并清理自身数据。执行其他环境需要单独确认环境范围。

本记录的通过结论限于上述 10 项目、本地目标和专项交互。完整类型检查的既有错误仍需后续治理；生产环境迁移和全库回归属于后续范围。

## 集成交接

本任务增量提交继承 `6e4408bb04315f022633785b79d36def047a9dd8` 中的资料后端基础；目标 `fix/visio-template-followup` 的只读检查显示该基础尚未纳入。限定 24 文件的集成补丁覆盖资料后端完整基础、本次实现和验收记录。两个已有详情入口通过三方合并保留目标分支的其他变更。

补丁基于目标提交 `27e04716dd26bc6864e1b47c71396fc0910b4849`，已在独立临时索引中通过 `git apply --cached --check`，差异仅涉及清单中的 24 个文件。目标分支与其工作树未改动；本次未执行目标分支上的编译或运行验收。合入和合入后的运行验证由主任务继续处理。

## 官方实现参考

- [Vue watcher cleanup](https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup)：处理切换项目/领域时的旧异步响应。
- [Element Plus table](https://element-plus.org/en-US/component/table.html)：表格、行键和明细操作。
- [Spring Boot JSON](https://docs.spring.io/spring-boot/reference/features/json.html)：当前 Jackson 3 应用集成。
- [MyBatis XML](https://mybatis.org/mybatis-3/sqlmap-xml.html)：参数化目标查询和映射。
