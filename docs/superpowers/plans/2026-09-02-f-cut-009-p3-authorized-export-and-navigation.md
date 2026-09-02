# F-CUT-009 P3授权清单导出与受控流程跳转 Implementation Plan

> 计划 ID：`NPDMS-FCUT009-TECHPLAN-20260902-01`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Feature Ready：`READY / GO@51239c53`
> Feature Spec：`specs/features/F-CUT-009-p3-authorized-export-and-navigation.md`
> API Contract：`specs/features/F-CUT-009-api-contract.json`
> Physical Contract：`specs/features/F-CUT-009-physical-contract.json`

**Goal：** 在不改变F-CUT-003清单提交、幂等、必填校验和`SURVEYING -> PLAN_DRAFTING`事实的前提下，交付“冻结配置决定提交后界面去向 → 获权用户导出当前业务调研/风险考察XLSX”的CUT完整正向闭环。

**Architecture：** 导航规则作为F-CUT-001配置修订根的一个可空JSON字段前向接入，由CUT内核解析为无条件双目标决定；导出复用F-CUT-003同一获权详情投影，由CUT专用导出服务生成即时XLSX并调用平台审计。ProjectScope只作为既有跨模块消费端口，`src/test`受控替身可驱动正向闭环，生产不注册Fake/fallback。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Apache POI/Yudao Excel基础能力、Vue 3、TypeScript、Element Plus、pnpm。

## 1. 全局边界

- 只覆盖`CUT-03@V2=FULL`；导出仅含`BUSINESS_SURVEY`和`RISK`，不得包含`DUAL_MACHINE_CHECK`。
- 导航目标只允许`CURRENT_STAGE_WORKBENCH/TASK_OVERVIEW`；无条件、无优先级、无多规则，不参与状态CAS、提交摘要或门禁。
- `navigation_rule_snapshot`属于CUT配置根；实施落迁移时才使用实际下一个空闲Flyway号，不预约、不修改V128～当前已执行迁移。
- 历史修订不回填，null/字段缺失固定解释为`CURRENT_STAGE_WORKBENCH`。
- create/copy继续返回`CommonResult<Long>`，update继续返回`CommonResult<Boolean>`；只给完整配置请求和`CutoverConfigurationRespVO`加`navigationRule`。
- 导出不创建PLT制品，不读取文件正文，不输出文件Fact、引用键、采集任务/结果身份或外部原始响应。
- 生产依赖未接通时，F-CUT-003/F-CUT-009 REST与页面只通过显式测试装配验证；不得增加生产Fake、fallback或跨模块直表。
- 先完成可运行正向能力，再补与本Feature风险直接对应的聚焦测试；不以未实现的负向矩阵阻断正向闭环。
- 新增查询遵守`docs/coding/database-query-interface.md`；能复用F-CUT-003获权详情投影时不新增Mapper，确需专用批量查询时使用场景Query和Mapper XML。

## 2. 文件与模块责任

| 责任 | 文件或目录 | 处理 |
|---|---|---|
| 导航物理列 | `sql/migrations/V{actual}__fcut009_p3_export_navigation.sql` | 在配置修订根增加JSON NULL无默认列及封闭JSON CHECK；历史保持null |
| 配置聚合接线 | `.../dal/dataobject/configuration/CutoverConfigurationRevisionDO.java`、配置VO、`CutoverConfigurationServiceImpl.java` | create/update/detail/copy/validate/publish/disable原路径加性接入，不改变既有响应类型 |
| 导航规则内核 | `.../service/configuration/CutoverNavigationRuleCodec.java`、`.../service/checklist/CutoverNavigationDecisionPolicy.java` | 严格JSON编解码、null默认、双目标决定；不得写任务或清单 |
| 导出内核 | `.../service/checklist/CutoverChecklistExportService.java`、`CutoverChecklistWorkbookWriter.java`、`result/` | 复用获权详情，筛选两类项，按锁定十列和稳定顺序生成字节流 |
| 导出REST | `.../controller/admin/taskv2/CutoverChecklistController.java`、`vo/checklist/` | 增加`POST .../actions/export`；校验版本并返回固定Content-Type/Content-Disposition |
| 提交响应 | `ChecklistCommandResult.java`、`CutoverChecklistApplicationService.submit`、`CutoverNavigationDecisionQueryService.java` | 非事务公共外壳等待平台业务事务提交后再加性组装`NavigationDecision`；提交业务写与摘要保持原样 |
| 平台审计 | `OperationAuditApi`八参数重载 | 以CutoverChecklist聚合身份记录导出成功及taskId/checklistVersion/两Sheet行数，不记录答案、SN或证据身份 |
| 配置与P3界面 | 现有`cutover-config`编辑器、`CutoverChecklistPanel.vue`与CUT API客户端 | 配置双目标；提交后按服务端决定导航；当前版本提供导出动作 |
| 聚焦证据 | CUT后端/MySQL/前端同目录测试 | 受控PROJ替身正向链、真实MySQL前向列、XLSX合同、组件交互 |

## 3. 核心实现决策

### 3.1 导航配置与提交决定

- `NavigationRuleSnapshot`精确JSON为`{"target":"CURRENT_STAGE_WORKBENCH|TASK_OVERVIEW"}`或SQL NULL；Codec拒绝额外键、数组、条件、优先级和未知目标。
- create/update完整请求必须显式携带`navigationRule`键，值可为null；copy原样复制列；validate/publish复用既有修订状态和CAS。
- `CutoverChecklistApplicationService.submit`改为无`@Transactional`的公共编排外壳；它仍调用`PlatformCommandExecutionApi.execute`，由该生产代理的REQUIRED事务独占完成`submitOnce`、幂等记录、提交审计、阶段历史和全部F-CUT-003业务写。不得增加自调用事务Bean或把平台execute包回CUT外层事务。
- `execute`返回即表示该平台事务已经提交。公共外壳对`NEW`与`REPLAY_COMPLETED`先按既有规则取得基础`ChecklistCommandResult`（重放仍设`replayed=true`），再由`CutoverNavigationDecisionQueryService.decide(tenantId, taskId)`读取任务冻结`configurationRevisionId`和不可变规则并加性组装决定；导航字段不进入平台持久的原提交响应、请求摘要或SuccessFacts。
- 导航读取/解析发生在已提交事务之外，异常不得回滚已提交业务事实。Controller把它分类为“业务写结果未知需刷新”，前端写屏障只调用既有任务详情GET与清单GET；确认任务已为P4后安全落到`TASK_OVERVIEW`并提示导航配置不可用。该落点是客户端恢复动作，不伪造服务端NavigationDecision；不得重发submit或改读当前发布修订。
- null与历史缺列结果固定为`CURRENT_STAGE_WORKBENCH`；页面只解释目标，不自行推导P4状态。

### 3.2 XLSX授权投影

- `CutoverChecklistExportService.export(tenantId, actorId, taskId, checklistVersion, correlationId)`先调用同一F-CUT-003获权详情入口；ProjectScope仍由该入口校验`ACTION_VIEW`。
- 只接受当前未失效`DRAFT/SUBMITTED`且版本精确相等的清单。筛选`applicable=true`且类型为`BUSINESS_SURVEY/RISK`的行。
- 工作簿固定两Sheet：`业务调研`、`风险考察`。每Sheet固定十列表头；空Sheet只写表头；按`sortOrder(null=0), stableItemKey`排序。
- `DIRECT`答案按原获权文本写入明确的POI字符串单元格，绝不创建公式单元格；`=,+,-,@`前缀仍作为普通文本保存且显示值不改写。
- `MANUAL/COLLECTION/EXTERNAL`只输出锁定常量；证据状态独立输出封闭文案。未知来源、未授权字段或投影损坏整次失败，不生成半个文件。
- 文件名固定`cutover-checklist-{taskId}-v{checklistVersion}.xlsx`；响应生成成功后只调用`OperationAuditApi`八参数重载：`record(tenantId, actorId, trustedCorrelationId, "CUTOVER_CHECKLIST_EXPORTED", "CutoverChecklist", String.valueOf(checklistId), "SUCCESS", safeDetail)`。`trustedCorrelationId`来自`CutoverChecklistRequestContext`，`checklistId`来自获权详情当前清单，`safeDetail`只含taskId、checklistVersion、businessSurveyRowCount、riskRowCount。

### 3.3 事务与依赖

- 导航配置保存沿用F-CUT-001事务与锁序；不新增导航表或第二配置聚合。
- 导出业务表只读，不触碰`updated_at/version`。八参数审计成功后Controller才返回字节响应；审计失败时本次下载失败，不把无审计字节响应视为成功，也不得使用会固定成`ProjectSplitRequest`的Long requestId重载。
- 生产仅消费既有ProjectScope公开端口；`src/test`提供确定性ACTION_VIEW结果，证明CUT编排，不证明PROJ生产Provider。
- F-CUT-003控制器仍受其生产Owner装配边界约束；本Feature不得借新增导出端点提前注册完整生产Controller。

## 4. Task 1：导航列、配置聚合与决定内核

**Produces：** 可由测试装配运行的配置修订导航规则和提交后决定，不改变既有状态机。

- [ ] 在实际串行合入时选取下一Flyway号，增加`navigation_rule_snapshot JSON NULL`和双目标/单键CHECK；同步DO。
- [ ] 给现有配置完整请求及详情响应加`navigationRule`，保持create/update/copy/publish/disable端点外壳和返回类型。
- [ ] 实现严格Codec与`CutoverNavigationDecisionPolicy`；copy继承、null默认和发布不可变沿用现有修订事务。
- [ ] 将submit收敛为非事务外壳，保持`PlatformCommandExecutionApi.execute`为唯一业务事务Owner；加性扩展外部返回并在execute提交后组装导航，确保业务结果仍是`SUBMITTED + PLAN_DRAFTING`且导航不进平台原响应、摘要或SuccessFacts。
- [ ] 完成后补聚焦验证：真实MySQL空历史升级、create→detail→copy→detail→publish；NEW与REPLAY_COMPLETED均在平台事务提交后返回两个目标/null默认并保留replayed；导航读取失败时业务/幂等/审计已提交，后续只刷新不重发submit。

## 5. Task 2：授权XLSX导出内核与REST

**Produces：** 获权用户可下载当前版本的两Sheet工作簿，且CUT业务事实零写。

- [ ] 实现ExportService/WorkbookWriter，复用F-CUT-003详情投影，不新增跨模块查询或文件Provider。
- [ ] 增加严格导出请求VO和Controller方法，固定文件名、响应头和稳定错误分类。
- [ ] 以受信correlationId、`aggregateType=CutoverChecklist`和当前checklistId接入`OperationAuditApi`八参数重载，审计只保存安全计数与业务身份，不保存答案或证据身份。
- [ ] 完成后补聚焦测试：DIRECT文本、MANUAL/COLLECTION/EXTERNAL固定显示、两Sheet/十列/顺序/空Sheet；DUAL_MACHINE_CHECK缺席；导出前后业务表和值版本不变。
- [ ] 用受控ProjectScope替身和真实MySQL完成“当前清单详情→导出→审计”正向链；不把替身当生产完成证据。

## 6. Task 3：配置UI、P3导出与受控正向闭环

**Produces：** 管理员可配置双目标，一线在P3提交后按服务端决定导航并下载当前清单。

- [ ] 在现有配置页面增加单选目标，完整保存/复制/发布后保持；不增加条件编辑器。
- [ ] 在P3清单面板按权限和当前清单状态展示导出动作，使用WireLong与固定文件名下载。
- [ ] 提交成功后只消费`navigationDecision.target`进行路由；导航读取失败进入共享写屏障，只刷新任务详情/清单，确认P4后落到任务总览并提示，不得重发提交。
- [ ] 完成后补组件交互测试：两个目标、null默认、NEW/重放返回、导出下载、提交后导航、导航失败只刷新并安全落到任务总览；运行定向Vitest和`pnpm ts:check`。
- [ ] 汇总CUT服务、真实MySQL和组件级受控正向链：“A/B/C清单提交进入P4→服务端导航决定→两Sheet授权导出”。生产Owner未接通时保持`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，不得声明真实浏览器或Implementation Done。

## 7. 验证与完成口径

候选只需回答五个交付问题：

1. 前向迁移是否只增加一个可空JSON列并保持历史null可读；
2. 配置现有端点形状是否不变，规则能否保存、复制、发布并由冻结revision读取；
3. 清单提交是否仍只产生原业务事实，并返回无状态副作用的导航决定；
4. XLSX是否严格为两Sheet十列、获权值、稳定排序且不含双机/证据身份；
5. 受控跨模块替身下能否完成CUT正常正向链且业务表导出零写。

生产ProjectScope及F-CUT-003完整装配、真实浏览器和Implementation Done另受依赖Gate约束，不以Fake、单测、HTTP 200或XLSX生成成功替代。

## 8. 风险与回退

- **配置列尚未存在：** 只用新Flyway前向增加；迁移未执行可删除候选，执行后只允许前向纠正。
- **导出泄漏：** WorkbookWriter只接收授权显示投影，不接收原始文件Fact DTO；未知来源失败关闭。
- **提交重复：** 导航/刷新失败不得重新执行提交；前端写后刷新屏障复用现有CUT模式。
- **生产依赖未接通：** 受控替身只用于`src/test`，生产无Bean/fallback；Feature保持未Done。
- **共享Flyway竞争：** 落迁移前重查最高版本并使用实际下一号，计划文件不预留固定版本。

## 9. Technical Plan Gate

当前：`REVIEW_REQUIRED`。最近Gate为本唯一Technical Plan独立复审；GO前不得实施Task 1～3。
