# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@644816f2`
> Technical Plan Gate：`PASS / GO@9ef7545d`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`

## 当前最小工作单元

- Task 1～10均已通过；当前进入Task 11 CUT受控正向集成回归；DUTY_CHANGED按`Q-FCUT004-001`保持`BLOCKED_BY_SPEC`。
- 后续按计划先完成每个Task最小正向实现，再补正向验证；生产CUT-05/PLT Provider缺失继续阻断生产装配、浏览器和Implementation Done。

## Gate清单

- [x] 独立Feature边界裁决：CUT-04独立于CUT-05。
- [x] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过（`87b0b066`）。
- [x] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过（`87b0b066`）。
- [x] API/Physical/Legacy Machine Contract Gate通过（`87b0b066`）。
- [x] Feature Ready最终裁决通过（状态基线`644816f2`）。
- [x] 唯一Technical Plan独立复审通过（`9ef7545d`）。

## Task 1：CUT-05审批消费Java合同

状态：`PASS / GO@38fd6cfd`

- [x] 实现`CutoverApprovalFactApi`、精确Command/Query/Fact/Result records与稳定公共异常。
- [x] 在`src/test`提供确定性受控审批事实实现并补实现后的合同测试（5/5通过）。
- [x] 通过独立Contract/Code Review Gate；不注册生产审批Bean（`GO@38fd6cfd`）。

> 检查点：独立复审确认四方法、精确records、封闭状态/错误与`src/test`正向链成立，无生产Provider或fallback；Task 1 Gate通过。

## Task 2：三表Schema、阶段前向约束与Mapper合同

状态：`PASS / GO@e8134586`

- [x] 使用实际下一空闲Flyway版本`V150`前向创建三表并收敛P4/P5/P6阶段约束。
- [x] 实现DO、场景Query、Mapper XML及迁移/Mapper合同测试（6/6通过）。
- [x] 通过独立Schema/迁移Gate；不写业务Service（`GO@ddda602f`）。

> 检查点：独立复审确认V150、三表、Mapper及两值CHECK约束成立；Task 2 Gate通过，进入Task 3。

> 迁移整改检查点：Task 4隔离MySQL 8.4.10空库复验发现原V150的`chk_cut_stage_trigger`顶层`COALESCE`被判为非布尔表达式；`e8134586`仅追加显式`= TRUE`，新空卷V1→V150、三表和原七条转换复验通过，独立裁决恢复Task 2 `PASS / GO`。

## Task 3：内容Codec、来源冻结与PLT文件事实消费端口

状态：`PASS / GO@b6ca8f71`

- [x] 实现三种可写方案联合、legacy只读联合及严格内容Codec。
- [x] 实现来源冻结和值对象，并预留最窄`CutoverPlanFilePort`。
- [x] 使用`src/test`受控端口完成正向聚焦测试并通过独立Domain/Port Gate（5/5通过，`GO@b6ca8f71`）。

> 检查点：独立复审确认DRAFT/完整性分层、D模板集合语义和正向受控测试成立；Task 3 Gate通过。生产PLT初稿生成仍阻断后续生产闭环。

## Task 4：创建、保存与详情正向闭环

状态：`PASS / GO@08d98e0b`

- [x] 根据独立裁决形成`createDraft`封闭请求联合与`PlanSourceSnapshot.failedRiskFacts`完整冻结规格候选。
- [x] 补齐创建事务的`ASSESSMENT_STALE/CHECKLIST_STALE`来源陈旧错误闭包；D级不产生清单陈旧。
- [x] Task 4可执行性机器合同最小补丁独立复审通过（`GO@94157db2`）。
- [x] 实现创建/保存命令、版本与幂等事务边界；同键同载荷重放在可变Owner/聚合重验前返回首次结果。
- [x] 实现当前详情、legacy只读投影与服务端`allowedActions`。
- [x] 使用Task 3受控端口完成标准/简易/上传正向聚焦测试及真实MySQL原子性证据，并通过独立Application/MySQL Gate（`GO@08d98e0b`）。

> 检查点：独立复审确认NEW_PLATFORM来源快照规范投影闭包，Task 4 Application/MySQL Gate通过。正向聚焦测试17/17、隔离MySQL真实平台命令1/1；生产Service Bean、PLT初稿生成及CUT-05审批Provider仍不接通，受控替身只用于`src/test`。

## Task 5：初稿下载、提交P5与来源失效

状态：`PASS / GO@d559c02c`

- [x] 实现下载初稿文件事实但不推进plan/task版本（`bd4ead05`）。
- [x] 实现提交与CUT-05审批启动同成同败、DRAFT→SUBMITTED、P4→P5及阶段历史（`a9b87b47`）。
- [x] 实现来源失效暂停审批、SUBMITTED→INVALIDATED、P5→P4及阶段历史（`fe50aaf9`）。
- [x] 使用受控PLT/CUT-05端口完成正向链并通过独立Submission/MySQL Gate（`GO@d559c02c`）。

> 检查点：独立复审确认下载前锁定重验冻结来源、提交/失效状态与事务闭环成立；正向聚焦12/12、隔离MySQL 2/2通过。Task 5 Gate通过，生产PLT/CUT-05仍阻断生产装配、浏览器和Implementation Done。

## Task 6：修订链与批准后联系人变更

状态：`PASS / GO@c6c295cb / DUTY_CHANGED_BLOCKED_BY_SPEC`

- [x] REJECTED派生新DRAFT并保留旧提交/审批历史。
- [x] PAUSED_SOURCE_INVALIDATED派生SOURCE_REPLACED，新revision提交时引用旧审批实例。
- [x] 派生DRAFT以当前锁定Owner事实重建设备/风险投影，只继承允许的用户内容；等级/编辑方式或模板不可唯一映射时插入前失败关闭。
- [x] APPROVED后仅联系人、电话、到位时间按方案根If-Match更新并保存平台前后审计。
- [ ] `DUTY_CHANGED`等待`Q-FCUT004-001`锁定P6→P4 Owner、历史触发器与旧批准/闭环处置；不得实现替代迁移。
- [x] 通过Task 6 Lifecycle/MySQL独立Gate（`GO@d4a827c0+c6c295cb`）。

> 检查点：独立整体Gate确认REJECTED、SOURCE_REPLACED、当前Owner投影重建、替代审批及批准后联系人PATCH闭环成立；非IT聚焦20/20、隔离MySQL 8.4应用3/3通过。Task 6 Gate通过；DUTY_CHANGED继续作为已登记规格阻断排除在本Task已授权实现范围外。

## Task 7：七路由REST与测试激活外壳

状态：`PASS / GO@359be6bd`

- [x] 实现详情、创建草稿、保存、下载、提交、批准后联系人PATCH与修订七条路由。
- [x] 四项权限键只声明在Controller方法；Controller不注册生产`@RestController/@Component/@Bean`。
- [x] Header、WireLong/WireDateTime、顶层判别联合及稳定错误Envelope由HTTP边界解析。
- [x] 创建时项目范围水位由服务端当前事实取得；保存时使用草稿冻结水位重验，不增加未锁定HTTP字段。
- [x] 测试专用`@RestController`外壳以MockMvc跑通七条正向路由；生产无Fake/fallback。
- [x] 通过Task 7 REST Contract独立Gate（`GO@359be6bd`）。

> 检查点：独立复审确认submit任务CAS、P4来源Provider、PLT空/损坏事实及WireLong/受信租户边界均已闭合；七路由正向及Controller/Codec/Application聚焦测试14/14通过，Task 7 Gate通过。Task 12前继续不激活生产Controller与完整Service装配。

## Task 8：受控legacy前向迁移与暂停Job

状态：`PASS / GO@258549d8 / PRODUCTION_ACTIVATION_BLOCKED_BY_MAPPING_PROVIDER`

- [x] 只通过`PlatformMigrationEvidenceApi`消费`STAGED_READY`的`pms_cut_plan`冻结来源，正常CUT代码不读取旧表。
- [x] 预留`LegacyCutoverTaskMappingPort`解析已确认的`pms_cut_task -> cut_task`目标映射；CUT只按解析后的目标ID锁表，`src/test`受控实现完成正向闭环，生产不注册Fake/fallback。
- [x] 合格来源形成只读`LEGACY_FORWARD`根与四类步骤，并在同一事务追加PLT mapping、核对计数及完成批次。
- [x] 使用实际下一空闲版本`V151`幂等登记`legacyCutoverPlanReconciliationJob`为`status=2/PAUSED`；不注册Quartz启动同步，正式映射Provider接通前不装配生产Handler。
- [x] Converter/Service/Migration合同聚焦测试5/5通过；隔离MySQL 8.4空卷V1→V151及应用正向1/1通过。
- [x] 通过Task 8 Migration/MySQL独立Gate（`GO@258549d8`）。

> 检查点：独立复审确认目标ID、冻结旧任务ID与F-CUT-002正式映射版本在同一行锁内闭合；生产迁移服务不直读`pms_cut_plan`，跨模块映射按最窄端口受控模拟。测试fixture仅证明转换和事务闭环，不作为生产迁移完成证据；正式映射Provider继续阻断生产激活。

## Task 9：P4工作台与组件交互

状态：`PASS / GO@b51963ff`

- [x] 后端只读投影补齐六类`allowedActions`及CUT-05审批事实，独立实施前Gate通过（`GO@5fee04d1`）。
- [x] 在现有割接工作台挂载P4/P5/P6方案面板，覆盖标准、D级简易与完整文件三种编辑模式。
- [x] 七条API按WireLong、epoch毫秒、精确Header和PLT文件事实接线，不提交URL或文件正文。
- [x] 操作按钮只消费服务端`allowedActions`及四项功能权限；统一write barrier保证成功后刷新失败不重发业务命令。
- [x] 使用真实组件mount完成正向交互测试；Task 9 Frontend Gate通过（`GO@b51963ff`）。

> 检查点：独立复审确认批准联系人实际编辑、多步骤稳定身份、标准方案三个PLT文件槽及初稿访问票据下载闭环成立；聚焦组件24/24、类型检查与生产构建通过。组件不注册后端生产Controller、Service或跨模块Fake/fallback，不启动真实浏览器；生产PLT/CUT-05 Provider继续阻断生产装配、浏览器正向闭环与Implementation Done。

## Task 10：字典、菜单与权限种子

状态：`PASS / GO@501cae2a`

- [x] 使用实际下一空闲版本`V152`幂等登记方案状态、编辑方式、六章节、保障角色与修订原因字典。
- [x] 在既有割接任务工作台下新增查询、保存、下载初稿、提交四项权限；不写`system_role_menu`，不修改旧菜单或旧权限。
- [x] 保持Task 8旧方案核对Job为`status=2/PAUSED`，不注册Quartz同步，不激活跨模块生产依赖。
- [x] 迁移合同聚焦测试6/6通过；隔离MySQL 8.4空卷V1→V152、V152重复执行及种子数量/零角色授权验证通过。
- [x] 通过Task 10 Seed/MySQL独立Gate（`GO@501cae2a`）。

> 检查点：独立复审确认V152只新增CUT方案封闭字典和四项权限；隔离库重复执行后字典类型5、字典项21、权限4、角色授权0，legacy方案核对Job仍为PAUSED。Task 10 Gate通过；生产PLT/CUT-05 Provider、浏览器与Implementation Done边界不变。

## Task 11：CUT受控正向集成回归

状态：`CODE_REVIEW_REQUIRED / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES_CANDIDATE`

- [x] 复用Task 4～6已通过的真实Spring事务、生产`PlatformCommandExecutionApiImpl`、真实MyBatis与MySQL测试装配，不重复创建第二套集成框架。
- [x] 标准方案补齐初稿下载后提交P5，并核对方案、任务、审批PENDING、阶段历史、平台幂等与审计事实。
- [x] 补齐D级简易方案与完整文件方案真实MySQL正向链：D只保存OPERATION/ROLLBACK且无清单/保障；上传模式只冻结PLT文件事实且无在线子项。
- [x] 复用已通过的REJECTED修订、来源失效/替代提交与APPROVED联系人PATCH回归，所有跨模块Owner仅在`src/test`显式装配。
- [x] CUT方案聚焦后端46/46（另5项MySQL按`skipITs=true`预期跳过）、隔离MySQL应用5/5、前端组件24/24、类型检查、后端package与前端build均通过；Flyway空卷已验证至V152。
- [ ] 通过Task 11 CUT受控正向集成回归独立Gate。

> 检查点候选：Task 11只证明CUT自有代码在受控Owner下形成正向事务闭环，候选状态最多为`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`。生产PLT/CUT-05及F-CUT-002/003所需Owner、唯一生产装配、真实浏览器与Implementation Done继续留待Task 12。
