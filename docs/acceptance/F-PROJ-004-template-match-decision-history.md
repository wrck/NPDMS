# F-PROJ-004 模板匹配决策历史与属性影响识别验收证据

> 状态：`APPROVED / GO NPDMS-FPROJ004-IMPLEMENTATION-DONE-20260825-07`
> Requirement：`PM-07`（PROJ子切片）
> 规格基线：`79125ceac092f7b586c66bbd251e9eb93ba894a2`
> 实施提交：`8e466f8`、`61f7dbc`、`2f7bb6d`、`8e64cd7`、`f033989`
> 状态回写：规格仓库`58a5719`，受管快照已锁定该提交

## 结论

F-PROJ-004约定的四属性判定、append-only模板匹配决策历史、首次创建原子写入、创建后只识别影响且不重实例化、受控查询与响应式页面已经完成实现和验证。当前不声明INT自动建项、CHG处理、PM-08、E2E、SIT、UAT、Deployment或Release完成。

独立裁决确认Task 6无必须修改项；V82边界、AC-001～012证据链及未越界结论通过复核。

## 自动化与物理验证

- 后端全量：`mvn.cmd -pl pms-module-project -am test`，Reactor成功；PROJ 253个测试，0失败，15个需显式MySQL环境的测试跳过。
- MySQL专项：`ProjectManualCreationMySqlIntegrationTest`与`ProjectAttributeCorrectionMySqlIntegrationTest`共14/14通过，覆盖10个创建故障点整体回滚、属性历史失败回滚、并发版本竞争、冻结模板与实例事实不变、无CHG Outbox。
- 迁移：隔离环境`npdms-50eb`完成Flyway V1～V82 migrate/validate；迁移契约6/6通过。V82仅停用V52遗留的重复`GENERAL/ENGINEERING`字典记录，未重写项目事实；SQL复核两个合法值均只保留1条启用记录。
- 前端：Node契约测试24/24、Vitest组件测试13/13、`pnpm build:prod`通过；构建仅保留既有标题变量与遗留CSS兼容警告。
- 边界扫描：未发现同义四属性列、独立属性历史表、分类状态/案例/影响表、重实例化入口或CHG事件；本轮PROJ实现未引入其他上下文DO、Mapper、Service或业务表访问。

## AC映射

| AC | 结果 | 主要证据 |
|---|---|---|
| AC-FPROJ004-001 | PASS | `rootCreationRejectsMajorProjectLevelBeforePlatformExecution`、`manualAttributesRejectMajorLevelAndTreeCategoryCodes`；创建UI不提交重大级别 |
| AC-FPROJ004-002 | PASS | V81停用MAIN/SUB且不更新`proj_project`；V82收敛重复合法字典项；迁移契约6/6 |
| AC-FPROJ004-003 | PASS | MySQL创建集成测试10/10，覆盖Project、历史、冻结、实例及所有故障点整体回滚 |
| AC-FPROJ004-004 | PASS | `creationBlockedWhenNoTemplateMatch`、`creationBlockedOnSamePriorityMultiMatch`、合法显式选择测试；浏览器覆盖无匹配、多匹配阻断及显式创建 |
| AC-FPROJ004-005 | PASS | `initialHistoryFreezesDecisionAndTrimsReason`；浏览器创建项目`992002000069`物理历史为`INITIAL_CREATE/MULTIPLE_MATCHES/EXPLICIT_SELECTION`并冻结模板910004、修订911009 |
| AC-FPROJ004-006 | PASS | `ProjectAttributeCorrectionMySqlIntegrationTest` 4/4；浏览器产生`NO_MATCH`和`MULTIPLE_MATCHES`两条`MANUAL_ADJUSTMENT`历史 |
| AC-FPROJ004-007 | PASS | MySQL验证调整前后冻结模板及阶段、任务、里程碑、交付件、门禁、门禁引用计数不变且Outbox不增加 |
| AC-FPROJ004-008 | PASS | 权限、空范围、非手工项目CRM Owner、版本冲突与未注册服务身份负向测试；无会话调用返回业务码401且无写入 |
| AC-FPROJ004-009 | PASS | 真实Chromium完成唯一命中、无匹配、多匹配、显式创建、合法修正、3条历史刷新保持；320/768/1024/1440无页面级溢出，无本Feature控制台错误或5xx |
| AC-FPROJ004-010 | PASS | Feature Task与实现计划明确保持INT、CHG、PM-08、E2E及后续阶段未完成 |
| AC-FPROJ004-011 | PASS | 历史表业务字段无更新接口；Mapper契约只暴露append/read；浏览器3条历史刷新保持且operationId稳定唯一 |
| AC-FPROJ004-012 | PASS | `genericUpdateCannotCarryBusinessAttributes`、受信任来源修正专项测试；通用PUT不接收四属性，来源命令仅改CRM Owner维度并追加历史 |

## 浏览器验收事实

- 唯一匹配：`DIRECT_SIGN + ENGINEERING + DIRECT_SERVICE`，自动允许下一步。
- 无匹配：`CHANNEL_SIGN + ENGINEERING`，禁止创建。
- 多匹配：`CHANNEL_SIGN + GENERAL + DIRECT_SERVICE`，未选择时禁止创建，选择修订911009后创建成功。
- 项目`992002000069`历史由首次创建1条和人工调整2条组成；刷新前后均显示3条，无任务相关控制台错误或失败响应。
- 未登录直接调用属性判定接口：HTTP 200承载Yudao统一响应，业务码`401`、消息“账号未登录”，无副作用。

## 已登记的前置Feature问题

浏览器首次创建项目后，发现 F-PROJ-001 创建路径未同步建立项目树版本与自路径，且统一范围服务未合并创建人基础查看范围，导致创建者无法立即打开新项目详情。本轮为继续只读验证，曾向项目 `992002000069` 写入隔离验收夹具；该夹具不是 F-PROJ-004 实现证据。该问题已于 2026-08-25 回到 F-PROJ-001 原边界单独修复并完成真实页面刷新验证，未夹带进模板匹配决策历史。

## 回退

应用代码按本Feature五个提交逆序回退；数据库不回滚已执行迁移，若需恢复字典显示，另建前向迁移。历史事实保持append-only，不删除、不更新。
