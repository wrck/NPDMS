# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@644816f2`
> Technical Plan Gate：`PASS / GO@9ef7545d`
> Implementation Done Gate：`BLOCKED_BY_SPEC（Q-FCUT004-001） / BLOCKED_BY_DEPENDENCY（Task 12）`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源V150/V151/V152 -> master V181/V182/V183`
> master复验：`CUT共享后端242项（跳过MySQL 27）与前端68项零失败；Q-FCUT004-001、生产依赖、真实MySQL与真实浏览器缺口不因代码入主干关闭`

## 当前最小工作单元

- Task 1～11均已通过；当前进入Task 12生产依赖接通前置核验；DUTY_CHANGED按`Q-FCUT004-001`保持`BLOCKED_BY_SPEC`。
- CUT自有实现已在受控Owner下形成正向闭环；生产CUT-05/PLT及F-CUT-002/003所需Owner缺失继续阻断生产装配、浏览器和Implementation Done。

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

状态：`PASS / GO@0e9065fa / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`

- [x] 复用Task 4～6已通过的真实Spring事务、生产`PlatformCommandExecutionApiImpl`、真实MyBatis与MySQL测试装配，不重复创建第二套集成框架。
- [x] 标准方案补齐初稿下载后提交P5，并核对方案、任务、审批PENDING、阶段历史、平台幂等与审计事实。
- [x] 补齐D级简易方案与完整文件方案真实MySQL正向链：D只保存OPERATION/ROLLBACK且无清单/保障；上传模式只冻结PLT文件事实且无在线子项。
- [x] 补齐REJECTED→新DRAFT→保存→替代PENDING真实MySQL链，并复用来源失效/替代提交与APPROVED联系人PATCH回归；所有跨模块Owner仅在`src/test`显式装配。
- [x] CUT方案聚焦后端46/46（另5项MySQL按`skipITs=true`预期跳过）、隔离MySQL应用6/6、前端组件24/24、类型检查、后端package与前端build均通过；Flyway空卷已验证至V152。
- [x] 通过Task 11 CUT受控正向集成回归独立Gate（`GO@0e9065fa`）。

> 检查点：独立复审确认Task 11证明CUT自有代码在受控Owner下形成正向事务闭环，Feature实施状态更新为`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`。生产PLT/CUT-05及F-CUT-002/003所需Owner、唯一生产装配、真实浏览器与Implementation Done继续留待Task 12。

## Task 12：生产依赖接通、唯一装配与真实验收

状态：`BLOCKED_BY_DEPENDENCY / NO-GO@b058edb3`

- [ ] 核验F-CUT-005生产`CutoverApprovalFactApi` Owner Gate及PLT正式初稿生成/冻结合同是否已通过。
- [ ] 核验F-CUT-002/003所需生产Owner与唯一生产装配前提；缺任一项时保持失败关闭，不注册fallback。
- [ ] 正式Owner齐备后，另行提交唯一Service/Controller装配、真实Spring传播、浏览器与数据库证据。

> 检查点：独立前置核验确认F-CUT-005生产Owner、CUT渲染与PLT文件创建/冻结交接合同、F-CUT-002/003生产Owner接线均未形成；F-CUT-004无自身整改项，保持失败关闭。当前受控替身不得用于生产装配、Job激活或真实浏览器验收；最近CUT主线Gate为F-CUT-005完整Feature Ready。

## 2026-10-20 保障人员未变更时自动回填

- 用户明确未变更的项目成员或系统人员不应要求重新选择。草稿候选异步加载后，以来源和人员ID识别同一人；当前角色候选中姓名一致、仅引用版本更新时，自动带入当前版本，不覆盖已填电话、职责及到位时间。
- 不按同名绑定手填客户，不自动替换已移除或不具备对应角色的人员；只读历史及已批准联系人不自动更新。服务端保存、提交的版本、项目范围、角色资格及启用状态校验保持原样。
- 新增自动回填、失效/跨角色排除、历史保护检查，失败用例先复现再修复。方案、保障与审批组件合计78项通过；受影响保障文件 ESLint 0错误、5条警告，审批改派相关文件0错误、0警告。证据：`.run/cutover-support-autofill-tests-before.log`、`.run/cutover-approval-support-ui-final-tests.log`、`.run/cutover-support-autofill-eslint.log`、`.run/cutover-reassignment-ui-eslint.log`。
- 浏览器在任务 `2112439350652284928` 验证周文成、王薇仅因资料水位变化而误报失效，修复后无需重选即可保存，重新打开无失效提示、无未保存变更。程少勇已不在有效客户候选中，按用户单独确认将本轮客户改为王刚后成功提交第4版方案；未替换历史方案或审批快照。

## 2026-10-20 填写中切换编制方式及日期时间选择

- 根据用户确认，可编辑草稿增加在线与完整文件上传互切入口，直接复用既有保存接口、等级约束及PLT上传器；未上传或未确认使用权不能保存，变更上传版本后重新确认。在线表单按冻结来源初始化，原上传附件保持平台引用并显示为参考，不转换文档正文。在当前会话互切保留填写及计划时间，重开按平台完整方案业务键恢复参考附件，已提交和历史只读不自动恢复当前附件或提供切换。
- 标准方案执行及回退的操作时间、割接后保障时间采用与到位时间同类日期时间选择器，新值保持既有字符串契约；原自由文本显示原填写，不自动推断日期。业务影响时间保留时长含义。返回、刷新在统一底栏左侧，业务动作在右侧。
- 定向组件与详情47项通过；聚焦vue-tsc及变更文件ESLint均无错误，diff检查通过。文件状态读取失败重试、未上传/未确认禁止保存、A/D模板分流、原附件重开恢复、历史不可编辑、旧文本保护、日期选择写入均有覆盖。另修复本次附件恢复导致的字段顺序及Long表现差异误报未保存，增加回归检查。日志：`.run/cutover-plan-mode-final-ui-tests.log`、`.run/cutover-plan-mode-final-typecheck.log`、`.run/cutover-plan-mode-final-eslint.log`。
- 真实浏览器使用现有专用验收草稿 `2110696055924260864`，上传此前自建的两页Word验收文档，完成在线→完整文件保存→在线保存→重开；原四条模拟步骤与计划时间保留，原附件V1显示为参考，无误报未保存。复验中旧标签触发版本冲突，服务端409拒绝覆盖；以新读取的当前版本完成后续保存。未提交审批、执行割接或修改用户原任务的审批快照。原任务已提交方案的操作时间、回退及保障日期控件只读显示已核对。本次没有后端源代码变更或服务重启。

## 2026-10-20 割接卡片统一折叠展开

- 用户要求所有割接内容卡片支持折叠。统一将工作台列表、筛选、项目、设备、评估、调研分类、方案、审批与闭环卡片改为默认展开的原生details/summary，已有闭环采集记录和P5提前时间卡片统一交互及箭头样式。折叠保留内部表单及文件组件，不销毁输入或上传状态，不改变底部业务操作、权限、状态或历史记录；不增加折叠状态持久化。方案保障校验定位会先展开祖先卡片，避免错误字段被隐藏。
- 8组原有相关组件检查143项通过，新增校验定位检查及既有校验合计4项通过；聚焦类型检查通过，变更文件ESLint无错误、闭环组件既有4条换行警告，diff检查通过。日志：`.run/cutover-card-collapse-ui-tests.log`、`.run/cutover-card-collapse-validation-tests.log`、`.run/cutover-card-collapse-types.log`、`.run/cutover-card-collapse-final-eslint.log`。
- 真实浏览器在审批任务2112439350652284928逐一折叠及展开所有审批卡片，78个字段组件保持挂载且值一致，底栏保留；调研七张卡片独立折叠正常、无遗留不可折叠内容卡片；闭环任务2110653642904092672两张卡片均可折叠，保存、提交仍在公共底栏，原禁用状态保留；工作台列表折叠展开正常。无业务保存、提交、审批或后端重启。

## 2026-10-20 方案提交后保留下载

- 用户要求提交后仍显示下载。方案页和完整审批页均在公共操作栏保留“下载方案”，复用同一下载函数。上传模式以当前提交修订的冻结工件/版本走PLT访问票据；在线模式新增只读DOCX导出，复用既有渲染器，无需预先生成草稿、不新增平台工件、不修改方案、审批或生成记录。导出原始保障行，不使用批准后的联系人调整覆盖提交内容。
- 新增GET `/api/v1/pms/cutover-tasks/{taskId}/plan/submitted-file`：既有下载权限、租户/项目或精确审批任务可见范围、SUBMITTED状态、当前修订与If-Match版本均生效。附件名称解析严格核对CUT、任务、用途、业务键及可用性；返回DOCX attachment、no-store、nosniff。前端点击时重读当前方案并核对修订，避免旧审批页误下新方案，失败保留按钮可重试，不调用保存、提交或草稿生成。
- 后端查询/控制器/渲染18项先通过，追加跨任务附件名称拒绝后查询13项通过（有效用例合计19）；前端方案/审批85项通过，追加切换修订后拒绝下载检查后方案41项通过（有效用例合计86）。首轮新导出检查因只读取DOCX表格而遗漏段落中的原步骤失败，修正为同时读取段落和表格后通过，正文/原联系人保护断言保留。聚焦类型及ESLint无错误，diff检查通过。日志：`.run/cutover-submitted-download-backend-final.log`、`.run/cutover-submitted-download-scope-tests.log`、`.run/cutover-submitted-download-ui-final.log`、`.run/cutover-submitted-download-revision-ui-tests.log`、`.run/cutover-submitted-download-types.log`、`.run/cutover-submitted-download-final-eslint.log`。
- 真实浏览器在任务2112439350652284928确认审批页及方案页均显示下载，方案页下载按钮唯一、仍无保存按钮。遵照用户此前“暂不重启”，未部署新接口，未声称在线方案二进制下载或本地文件落盘验收完成；没有审批/方案写入。本次后端增量仍待部署及在线真实下载复验，旧冻结包不代表本次源码。

## 2026-10-20 在线方案生成内嵌附件图片

- 用户要求在线编辑生成时将附件图片内嵌正文。复用原方案渲染器及平台AuthenticatedFileContentApi，新增CUT内同一附件解析器供草稿生成和已提交在线导出调用。PNG/JPEG按原工件、版本、业务键和摘要，在对应拓扑/组网配置章节显示；保持纵横比并限于正文可用宽高，不拉伸、不写远程图片链接，保留名称及版本。非图片不读取正文，保留引用说明。平台DOWNLOAD权限、原50MiB上限、可用性、内容校验与锁定均生效；坏图/拒绝读取导致整次生成失败。
- 新格式DOCX写入内部渲染标记；生成按钮不绕过服务端直接复用旧缓存。服务端识别旧格式后按新渲染身份生成并追加记录，同格式继续复用，不覆盖原生成记录、方案或历史。提交后导出继续无业务写入，但改为普通事务承接PLT读取所需的既有授权锁。
- 初次后端执行因其他构建持锁未启动；等待原构建结束后沿用工作树锁和固定工具链执行。附件解析、渲染、查询与生成提交26项通过，覆盖跨任务/Owner/用途/摘要/权限拒绝、非图片、PNG/JPEG原字节、纵横比例与页面尺寸、坏图、旧缓存追加以及新格式复用。前端方案46项通过，补充已显示旧文件仍必须请求后端确认格式后方案42项通过（有效用例合计47）。ESLint与diff检查无错误。日志：`.run/cutover-plan-inline-images-tests.log`、`.run/cutover-plan-inline-images-ui-tests.log`、`.run/cutover-plan-inline-images-cache-ui-tests.log`、`.run/cutover-plan-inline-images-eslint.log`。
- 由真实POI渲染器生成`.run/cutover-image-plan-acceptance/embedded-plan.docx`，ZIP/OOXML检查确认PNG、JPEG原字节及r:embed，非远程引用。使用现有本机LibreOffice转换PDF，再以Poppler渲染两页逐页目视检查，横图及竖图完整、标题与图片同页、比例和文件版本标注正常。证据同目录embedded-plan.pdf、page-1.png、page-2.png。未重启或部署、未改动用户已上传图片或历史方案；真实浏览器生成下载复验待部署。

### 2026-10-20 方案设备清单自动回填

- 在线标准草稿复用AST公开设备查询，按任务冻结的设备ID和序列号逐台匹配，只补齐空的涉及产品型号与在网软件版本（productModel/conpVersion）。已有填写、目标版本及备注保留，结果经既有草稿保存入库。查询失败保留人工填写并提示；部分失败不影响其他设备，不混用身份不符的响应。不增加接口、权限或数据范围，不修改提交快照；D级及完整上传模式不增加设备章节，上传转在线标准时同样回填。
- 修复前4个挂载用例失败；修复后方案组件55项通过，覆盖回填保存回显、非空保留、部分失败逐台对应、身份不符、无查询权限、只读、提交后保护，以及上传切换在线/D级差异。ESLint及定向vue-tsc通过。日志：.run/cutover-plan-device-autofill-before.log、.run/cutover-plan-device-autofill-tests.log、.run/cutover-plan-device-autofill-eslint.log、.run/cutover-plan-device-autofill-types.log。
- 真实浏览器标准验收任务2109853246593855488由上传临时切至在线，设备1001的型号USG6680-AC自动带入，设备公开资料conpVersion为空，因此在网软件版本保持空。普通保存后GET方案v4含精确deviceDetails，重开页面回显一致；随后通过原上传模式恢复原附件V1、使用权确认及计划时间，GET方案v5正文与复验前v3完全一致，状态仍DRAFT、阶段未变，未提交审批。证据：.run/cutover-plan-device-autofill-browser.json。当前用户链接2110696055924260864为D级简易草稿，保留既有简易结构与步骤。本轮不涉及后端部署或重启。
- 额外打开已提交任务2112439350652284928时，页面显示“操作失败，系统异常”，本轮未进入其只读方案，不能将该项记为浏览器通过；只读不回填边界已由挂载组件用例验证，未扩大本次范围修改该运行异常。

### 2026-10-20 从调研回填方案升级版本

- 在既有空值回填中增加升级版本信息，读取方案sourceSnapshot.checklistId对应的调研清单external-state，核对清单ID、设备ID及序列号，只带入非空targetSoftwareVersion；已有方案目标版本、备注及历史内容保留。CUT读取与AST查询权限分开，目标版本读取失败或身份不符保留人工填写并提示，不扩大权限，不新建接口或改写调研来源。
- 新增5个定向用例，修复前5项失败，修复后方案组件60项通过；覆盖精确清单、多设备对应、非空保留、无AST权限仍可按CUT授权读取目标、来源失败/清单或设备不符/未填写目标不伪造值，以及既有只读和提交后保护。ESLint与定向vue-tsc通过，git diff检查通过。证据：.run/cutover-plan-target-version-before.log、.run/cutover-plan-target-version-tests.log、.run/cutover-plan-target-version-eslint.log、.run/cutover-plan-target-version-types.log。
- 浏览器任务2109853246593855488引用清单2110629748705050624，设备1001调研目标sd自动带入升级版本信息，普通保存后的方案v6包含targetVersion=sd，重开页面回显一致。将本地输入改为sd-已有填写保留验收并往返切换编制模式后保持不变，未把该临时输入写入方案。随后恢复原上传附件、使用权与计划时间，v7正文与复验前v5完全相同，未提交审批。实际恢复对比及网络/表单值记录见.run/cutover-plan-target-version-browser.json；本轮无后端部署或重启。

### 2026-10-20 调研拓扑及前序带入核对

- 用户明确要求拓扑和组网配置复用调研，并随后澄清“没有不带入”。现有已发布调研定义只有现网拓扑SURVEY_CURRENT_TOPOLOGY，故仅对应方案割接前拓扑；无组网配置或割接后拓扑来源时保留原上传方式，不生成替代文档，不挪用友商配置或接口/IP/VLAN规划。只补方案空字段，已填写的概述及文件保留，前序原件及已提交历史不改写。
- 附件保存接入既有草稿事务，以方案来源快照指定的已提交清单ID/版本读取文件，通过PLT既有版本附加能力建立目标方案引用，同工件精确版本，不复制字节或覆盖源引用。读取页面仅在空槽显示来源预览，不在GET中建立绑定；保存前置判断包含待带入文件，生成/离页仍先保存。新增绑定目标仅CUT/CUTOVER_PLAN/FULL_PLAN；原权限、源不可变、目标可变、文件类型、大小、版本、范围和审计校验保留。方案读取改用精确任务业务键，避免多引用时误取工件最早的清单引用；源清单失效或不可读时不新带入，仍保留既有“草稿可暂存、提交重验来源”规则。
- 类似项核对如下。已有任务背景初始化与方案非空概述保留；空概述带入调研割接背景表述和业务简述并保留来源标签。设备范围/序列号、型号/在网版本、目标升级版本沿用已实现的来源带入。风险项已有冻结失败事实引用，措施仍由办理人填写。割接后拓扑、组网配置、执行步骤/结果、回退方案、保障职责/到位时间及计划表无同义前序值，保持填写，不以连通性测试附件冒充测试结果，也不从当前设备资料推断操作安排。
- 验证：CUT相关40项通过；增加“前序失效不阻止暂存”后仅重跑受影响4项，总有效CUT覆盖41项。PLT既有附件服务3项通过，总后端44项。前端挂载组件62项、定向vue-tsc及ESLint通过。两轮编译失败分别为新增断言缺少静态导入、任务上下文链式调用和JsonNode显式转换，均在范围内修复；失败日志保留，不记为通过。证据：.run/cutover-plan-survey-prefill-backend-tests.log、.run/cutover-plan-survey-prefill-backend-retest.log、.run/cutover-plan-survey-prefill-backend-final.log、.run/cutover-plan-survey-prefill-source-state-tests.log、.run/cutover-plan-survey-prefill-ui-tests.log、.run/cutover-plan-survey-prefill-eslint.log、.run/cutover-plan-survey-prefill-types.log。
- 真实浏览器独立标签读取任务2109853246593855488当前方案v8及其清单2110629748705050624，概述显示“割接背景：sd / 涉及业务：ds”；源清单无现网拓扑文件、无组网配置或割接后拓扑项，三个附件槽保持空，无虚构文件。观察到方案v8由当前实际数据提供，本轮独立标签没有方案PUT/POST/PATCH/DELETE，不恢复或覆盖其他页面正在维护的版本，标签已关闭。证据：.run/cutover-plan-survey-prefill-browser.json。附件正向绑定、保存、预览/生成的真实部署闭环尚未验收；此前用户选择暂不重启，本轮未部署或重启59191/DAC，不能将新增后端行为记为已在运行服务生效。

### 2026-10-21 文件改为页内弹窗预览

- 按用户要求，普通文件引用和精确版本引用统一复用PmsFilePreviewDialog，使用顶层append-to-body弹窗，不新开文件标签、不导航第三方页面。复用既有鉴权预览接口，仍传入当前业务键和精确文件版本；前端菜单权限及服务端allowedActions保持原限制，下载操作沿用原实现。未修改BPM或新增后端接口。
- 实际浏览器发现原生内嵌PDF呈空白，改为固定版本pdfjs-dist 6.4.299本地Worker和Canvas渲染；Word沿用现有后端转换PDF，弹窗内分页查看。图片使用本地Blob，文本按纯文本显示，不执行HTML。关闭时销毁PDF加载/渲染任务并释放图片URL；过期响应不打开弹窗或覆盖新文件；转换、读取或渲染失败在弹窗内重试。
- 共享文件组件27项、割接角色动作4项通过，包含授权拒绝、精确版本、失败重试、关闭后的迟到响应、PDF分页/销毁、纯文本不执行、图片URL释放、原下载及上传区域回归，以及旧预览返回不能解除正在进行的下载锁。定向vue-tsc、ESLint、Stylelint和diff检查通过。日志：.run/file-preview-dialog-tests.log、.run/file-preview-dialog-role-tests.log、.run/file-preview-dialog-types.log、.run/file-preview-dialog-eslint-final.log、.run/file-preview-dialog-stylelint.log。
- 真实浏览器在2109853246593855488预览原上传Word方案V1，16页文档封面文字、内嵌图片及翻页正常；390px窄屏画布宽294px、内容区宽326px，无横向溢出，关闭后再次打开成功。在2112435383465652224预览表格中image (1).png V1，2048×2048图片加载正常，预览弹窗不嵌在表格内。页面地址及标签页数量保持不变，仅使用独立验收标签，结束后关闭；无方案保存、提交或审批操作。证据：.run/file-preview-dialog-browser.json。本轮仅前端改动，未重启59191或DAC，不将此前待部署的后端增量记为已验收。
