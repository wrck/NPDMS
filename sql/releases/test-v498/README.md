# 正式测试环境空库发布包：V498

本包将截至 V498 的 421 份 Flyway SQL 整合为一个空库基线。适用 **MySQL 8.4 / Flyway 11.10.5**，由独立 Flyway Compose 客户端执行；不启动或重启应用。

## 交付内容

| 文件 | 用途 |
| --- | --- |
| `migrations/B498__npdms_formal_test_baseline.sql` | 最终 461 张表、238 个触发器、29 张基础种子表中的 4,596 条数据 |
| `bootstrap-admin-and-storage.sql` | 部署时设置管理员 BCrypt 密码哈希和数据库文件存储访问地址 |
| `verify.sql` | 初始化后的只读核对：结构数量、种子数量、业务表为空、物理外键及逻辑引用 |
| `compose.yaml`、`.env.example` | 指定目标数据库的 Flyway 执行入口；不内置连接凭据或默认数据库 |
| `source-manifest.json` | 本包实际来源文件、筛选条件、数据数量和延后配置项 |
| `templates/project-delivery-source.json` | 正式项目模板的只读来源原件，**不自动导入数据库** |

SQL 来自本次专用临时库对仓库迁移的完整演练，不是现有业务库备份。按需求方确认排除 V310 退役的 29 张旧表及其 87 个只读触发器，其余表的最终列、索引、外键、CHECK、生成列及触发器保留；不在目标库重新执行历史 ALTER、数据回填或示例数据写入。目标自增序列从空库/实际保留的种子开始，不继承演练库示例数据推高的序列。

## 数据范围

按需求方确认，保留平台字典、当前模块菜单与权限、8 个已有正式角色、默认租户、管理员身份、业务配置、已实现的定时任务、交付件类型、业务视图、正式动态表单及其原修订、正式满意度问卷、已发布割接配置及结构化明细。

以下数据不导入：演示项目、客户、设备、订单、合同、来源系统业务数据、验收用户/角色、实例、审批运行记录、上传文件、通知送达记录、审计/迁移台账、Token、外部数据源及存储/短信/邮件/社交平台凭据；独立示例模板、负向测试表单、未批准的模板草稿和示例规则也不导入。V310 退役的 29 张 `pms_*_retired` 表及其触发器不在空库中创建；完整排除清单见 `source-manifest.json` 的 `excluded_legacy_objects`。此筛选不修改原历史迁移，也不对已有数据库执行删除。

部署配置的具体处理：

- 只保留管理员 `id=1 / admin`，初始化为停用；密码字段为不可登录标记。没有可用于登录的默认密码，也不复制源库用户资料、组织及用户业务范围。
- 保留管理端内部登录必需的 OAuth `default` 客户端，清除源示例 secret，外部授权类型清空；内部管理端登录调用仍使用既有服务。对外 OAuth 客户端由目标环境单独配置。
- 不复制 `system.user.init-password` 的明文值、开发监控 URL 和示例存储配置。新建用户所需的初始密码设置由管理员通过现有平台入口配置。
- 文件存储由第二步初始化为上游数据库存储提供方，必须传入目标环境实际后端公共访问地址，不包含任何外部存储密钥。
- 不导入源码中找不到处理类的 `cutoverRuntimeApprovalNotificationJob`、`arrivalLegacyReconciliationJob`、`legacyCutoverPlanReconciliationJob`、`legacyCutoverClosureReconciliationJob`。其余任务保持来源启停状态。
- 菜单 `19265` 是已删除的旧巡检父节点，但仍被保留的历史权限节点引用；保留其原有删除和隐藏标记，仅保持引用完整，不恢复旧导航。

## 执行顺序

1. 由目标环境 DBA 准备**独立空库**，字符集 `utf8mb4`、默认排序规则 `utf8mb4_unicode_ci`。确认库名与连接地址。迁移账号需要建表、索引、外键、临时表、触发器及基础数据写权限；若开启 binlog，触发器创建权限按该环境 DBA 规则配置。
2. 从 `.env.example` 另存本目录 `.env`，填写实际 JDBC URL、账号、密码；本包已忽略 `.env`。从容器访问宿主机数据库时使用实际可达地址，不能把容器内 `localhost` 当作宿主机。
3. 在仓库根目录执行：

```powershell
$releaseDir = Join-Path (Get-Location) 'sql/releases/test-v498'
docker compose --env-file "$releaseDir/.env" -f "$releaseDir/compose.yaml" run --rm migrate info
docker compose --env-file "$releaseDir/.env" -f "$releaseDir/compose.yaml" run --rm migrate migrate
docker compose --env-file "$releaseDir/.env" -f "$releaseDir/compose.yaml" run --rm migrate validate
```

成功后历史表应出现 `version=498 / type=SQL_BASELINE / success=1`。再次 `migrate` 应报告无待执行迁移。

4. 通过受控部署通道，在**同一个 MySQL 会话**先设置下面两个变量，再执行 `bootstrap-admin-and-storage.sql`。BCrypt 哈希由目标环境密码工具生成，成本参数至少 10；本包不接收或生成明文管理员密码。变量不得写回发布文件或输出到共享日志。

```sql
-- 以下仅表示变量契约；占位内容不能直接执行。
SET @npdms_admin_password_hash = '<部署时提供的全新BCrypt哈希>';
SET @npdms_public_backend_origin = '<实际http或https后端访问地址，不带末尾斜杠>';
-- 随后在同一会话执行 bootstrap-admin-and-storage.sql 的完整内容。
```

使用遇错即停的批处理方式提交完整 SQL，不使用 `--force` 或忽略 SQL 错误的执行器。变量不合法、管理员已初始化或已有存储配置时，脚本必须报错停止；它不会重置已有管理员密码。成功时管理员启用，并新增唯一的数据库文件存储。

5. 在同一目标库执行 `verify.sql`。旧表残留、旧触发器残留、种子/空表差异、引用完整性这四组差异查询均应返回 **0 行**；结构数量应为 461 张业务/平台表和 238 个触发器，管理员应为 `CONFIGURED / status=0`，文件存储数量应为 1。Flyway 历史表不计入 461 张表。
6. 再按目标环境流程部署后端和前端、配置真实组织和人员、部署 BPM 定义及所需采集模板，完成真实浏览器业务验收。本包没有替这些操作生成成功证据。

## 项目模板的已确认边界

唯一保留为正式来源的项目模板 `TPL-DELIVERY-FLOW-20260926`（根 `993009900011`、已发布修订 `993009900015`）含有源环境的精确审批定义 ID：

`pms-cutover-done-approval:1:7251f330-ba71-11f1-ae93-00ff0b78abd7`

代码明确要求按精确流程定义 ID 校验，不会改用目标环境的最新版本。因此需求方确认：**该项目模板及其修订不写入空库**，只读原件保存在 `templates/project-delivery-source.json`。文档和执行快照原值保留，没有改写批准历史、编造目标流程 ID 或直接复制 Flowable 运行表。

目标环境先通过 BPM 入口部署所需流程，包括来源闭环键 `PMS_MINIMAL_NORMAL_CLOSURE`；再以原件为参考，通过项目模板设计器创建新模板/修订，选择目标环境实际审批定义，完成依赖校验并发布。基线初始化后项目模板数量为 **0**，在完成此步骤前不能把项目自动选模/实例化视为就绪。采集模板来源均为示例草稿，本包同样不把它们当作正式已发布配置。

## 旧域移除与应用兼容边界

需求方已确认从空库基线中排除全部 29 张旧表及对应 87 个触发器，不采用暂留 6 张表的过渡方案。本次只调整数据库发布包及验证资产，菜单种子和应用代码保持原值。

当前源码仍通过旧 Controller、Service、Mapper 查询以下 6 张表：`pms_project_retired`、`pms_project_phase_retired`、`pms_project_task_retired`、`pms_project_team_member_retired`、`pms_plan_change_request_retired`、`pms_plan_change_phase_snapshot_retired`。旧项目、阶段、任务树、团队、全景及计划变更接口在此空库中没有对应存储；调用这些路径会出现表不存在错误。基线菜单 `19151`（历史计划变更）和 `18017`（项目风险）仍启用，前者加载历史列表、两者选择项目时仍消费旧查询。

因此数据库迁移通过不代表当前应用已经兼容该基线。应用投用前需要单独处理这些旧页面和接口的退役或改接，并进行真实浏览器验收；共用权限（如 `pms:project:query`）仍服务新项目入口，不能随旧菜单一并删除。本次没有以空结果替身掩盖缺失依赖，也没有恢复旧表写入。

## 版本与后续升级

- 本包来源为工作树快照，截止版本 V498；其中 V497、V498 在取样时尚未提交。完整文件清单在 `source-manifest.json`，不代表这些未提交文件已成为 Git 发布版本。
- 历史演练在 V374 前仅于本次临时库将 `acc_project_deliverable.deliverable_code` 对齐为 `utf8mb4_unicode_ci`，解决 JSON_TABLE 比较的排序规则冲突；各迁移会话使用相同连接排序规则。仓库历史迁移文件未改动。整合 SQL 已包含此最终列定义。
- `B498` 是会真实执行 SQL 的 [Flyway baseline migration](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations)，应使用 `migrate`，不能用 `baseline` 命令代替初始化。
- 该包只用于新空库。已有数据库继续原有迁移入口；不要给已有数据库套本包，也不要使用 `repair`、`clean`、关闭校验或手写历史记录处理冲突。
- 本包一旦用于环境初始化，`B498` 内容即冻结。以后把正式的 `V499` 及更高版本迁移加入本包 `migrations` 目录，沿用同一入口执行。不得重生成并覆盖已执行的 `B498`。
- MySQL DDL 不支持整包事务回滚。失败时保留日志，核对本次新库的部分落地状态；只有经该环境明确批准，才能重建这个新库后重试，不自动清理任何数据库。

实际验证结果见 [发布验证记录](../../../docs/generated/2026-10-17-flyway-test-v498-release.md)。238 个触发器的名称、目标表、作用及 SQL 行号见 [触发器清单](../../../docs/generated/2026-10-17-flyway-trigger-inventory.md)。其中 2 个项目触发器会在阶段/任务完成时追加完成与逾期留痕，其余用于拒写或完整性约束；不是所有触发器都只有拦截作用。
