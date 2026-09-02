# F-CUT-007 割接首页授权KPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. 本计划按已批准的“先完成正常正向闭环，再做聚焦验证”执行，不使用测试先行。

**Goal:** 在统一割接工作台为当前用户展示授权范围内的待办、已归档、审批中、驳回待修改四项实时KPI，且不改变任何P1～P6事实。

**Architecture:** CUT先以`ProjectScopeApi.ACTION_VIEW`取得可见项目，再用CUT场景化批量查询读取任务和当前阶段事实。P2～P6现有查询服务与KPI共用同一组纯动作策略；KPI把跨模块资格需要一次性提交给CUT消费端口，受控测试替身返回正常事实，生产适配器留到Owner依赖接通提交。REST与页面只消费不可变KPI投影，不增加表、缓存、事件或命令。

**Tech Stack:** Java 25、Spring MVC、MyBatis XML、JUnit 5、MySQL 8.4、Vue 3、TypeScript、Element Plus、Vitest。

**Spec:** `specs/features/F-CUT-007-cutover-dashboard-kpis.md`

## Global Constraints

- Requirement固定为`CUT-01@V2=FULL`，不得修改P1～P6状态机或既有动作语义。
- COM-01及其他跨模块Owner不在本Feature实现；只保留CUT消费端口与`src/test`受控正常事实。
- 生产代码不得注册Fake、fallback、空成功Provider、完整ApplicationService/Controller Bean。
- `todoCount`只能复用现有P2～P6真实写动作守卫；禁止阶段/状态/负责人/权限粗略近似、逐任务HTTP和N+1。
- 空`ACTION_VIEW`项目集合必须在Mapper前短路为全0；任一已调用Owner失败必须保留既有reasonCode和实际ownerContext，并使整份KPI失败。
- 四项按`taskId`分别去重且允许重叠；只读查询不得写审计、Outbox、状态、缓存或快照。
- 旧`CutTaskController/CutTaskServiceImpl`、旧`cut-task/index.vue`、旧权限和旧数据零修改。
- 先实现正常正向闭环，再补合同、聚焦回归、受控MySQL和组件交互验证。

---

## 文件结构

### 新增后端文件

- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/CutoverDashboardQueryService.java`：KPI只读编排、批次归并、计数和服务端时间。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/CutoverDashboardException.java`：封闭错误类别、reasonCode、recoveryAction和ownerContext。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/view/CutoverDashboardKpiView.java`：五字段不可变结果。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/model/CutoverDashboardCandidate.java`：CUT批量候选及当前阶段事实引用。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/model/CutoverDashboardActionFacts.java`：已解析Owner资格事实，不包含正文或秘密。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/policy/CutoverP2P3ActionPolicy.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/policy/CutoverP4ActionPolicy.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/policy/CutoverP5ActionPolicy.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/policy/CutoverP6ActionPolicy.java`：现有详情与KPI共用的纯动作判定。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/port/CutoverDashboardActionFactPort.java`：一次请求整批资格事实的CUT消费端口。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/port/CutoverDashboardOwnerFactException.java`：携带实际物理Owner和既有稳定reasonCode。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/dashboard/CutoverDashboardCandidateMapper.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/dashboard/query/CutoverDashboardCandidateQuery.java`
- `pms-module-cutover/src/main/resources/mapper/dashboard/CutoverDashboardCandidateMapper.xml`：可见项目内批量候选、当前审批与阶段事实查询。
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverDashboardController.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverDashboardRequestContext.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/CutoverDashboardResponse.java`

### 修改后端文件

- `CutoverTaskQueryService.java`：P2/P3详情改调共享策略，公开批量事实装配所需的CUT内部方法。
- `CutoverPlanQueryService.java`：P4详情改调共享策略。
- `CutoverApprovalQueryService.java`：`myTodos`和详情改调共享P5策略。
- `CutoverClosureQueryService.java`：P6详情改调共享策略。
- 相关Mapper仅补场景化批量读取方法；不改变任何写SQL。

### 前端文件

- 修改`yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`：新增`CutoverDashboardKpiData`与`getCutoverDashboardKpis()`。
- 新增`yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverDashboardKpis.vue`：四张只读卡片和生成时间。
- 修改`yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`：与列表并行加载KPI，失败时显示错误而非伪0。

---

## Task 1：共享P2～P6动作策略与批量事实合同

**Interfaces**

- `CutoverDashboardActionFactPort.inspectBatch(BatchQuery)`：输入受信tenantId、actorId和稳定按taskId排序的候选需要；输出按taskId唯一的`CutoverDashboardActionFacts`。
- `BatchQuery`不得为空、不得重复taskId；候选需要只包含Owner查询键、冻结版本和阶段，不包含附件正文、SN全集或秘密。
- `CutoverDashboardOwnerFactException(category, reasonCode, ownerContext, cause)`只允许`OWNER_PROVIDER_UNAVAILABLE/OWNER_DATA_CORRUPTED`和已批准Owner集合。
- 四个Policy均提供`Set<String> allowedActions(Candidate, ActionFacts, PermissionFacts)`；同一方法由现有详情服务和KPI调用。

- [ ] 新增dashboard model、port、exception和四个Policy，迁移现有私有`allowedActions`条件，动作集合严格等于Feature合同。
- [ ] 修改四个现有QueryService使用Policy；保持原详情、`myTodos`、动作顺序和返回结构不变。
- [ ] 受控实现只放`src/test/.../service/dashboard/ControlledCutoverDashboardActionFactPort.java`，一次返回完整正常事实；`src/main`不提供实现或Bean。
- [ ] 完成正常路径后运行现有QueryService聚焦回归，证明重构前后动作投影一致。
- [ ] 增加`CutoverDashboardPolicyTest`，只固定正常P2～P6批准动作集合及同任务多动作去重所需输入。
- [ ] 提交并申请Task 1共享动作语义/Port Contract Gate。

**Verify**

```powershell
mvn -pl pms-module-cutover -am -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=CutoverTaskQueryServiceTest,CutoverPlanQueryServiceTest,CutoverApprovalQueryServiceTest,CutoverClosureQueryServiceTest,CutoverDashboardPolicyTest" test
```

## Task 2：批量候选查询与KPI服务正常链

**Interfaces**

- `CutoverDashboardCandidateMapper.selectBatch(CutoverDashboardCandidateQuery)`按`taskId ASC`返回当前CUT候选；Query只接收tenantId、非空visibleProjectIds和游标taskId，禁止Map或长参数。
- `CutoverDashboardQueryService.inspect(long tenantId, long actorId, PermissionFacts permissions)`返回`CutoverDashboardKpiView(long todoCount,long archivedCount,long approvingCount,long rejectedPendingModificationCount,LocalDateTime generatedAt)`。
- ActionFact端口一次接收全部结构候选；返回集合缺项、重复或身份错配为对应真实Owner损坏，不能当无动作。

- [ ] 实现Mapper/XML：显式tenant/deleted/visible project条件，读取任务、当前未替换审批身份及各阶段当前事实引用；不读取旧表。
- [ ] 实现服务正常链：先`resolveAllCurrent(actorId,ACTION_VIEW)`；空集合直接返回四项0；非空按稳定游标批量读取，汇总三项状态KPI并收集TODO候选。
- [ ] 将TODO候选一次性交给ActionFact端口，调用Task 1 Policy，按taskId去重；P5输入必须来自与`myTodos`相同的当前节点/候选资格事实。
- [ ] 实现Owner异常透传：保留既有reasonCode和实际ownerContext；CUT投影损坏才转`ownerContext=CUT`；任何失败丢弃已算计数。
- [ ] 完成正常服务后增加`CutoverDashboardQueryServiceTest`，覆盖四项正向计数、指标重叠、合法空范围全0、任务去重和单次批量事实调用。
- [ ] 增加`CutoverDashboardMapperContractTest`，用`XMLMapperBuilder + BoundSql + MetaObject`固定动态项目集合、游标、当前审批谓词及空集合不执行SQL。
- [ ] 提交并申请Task 2 Query/Mapper Gate。

**Verify**

```powershell
mvn -pl pms-module-cutover -am -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=CutoverDashboardQueryServiceTest,CutoverDashboardMapperContractTest" test
```

## Task 3：KPI REST与真实错误Envelope

**Interfaces**

- `GET /api/v1/pms/cutover-dashboard/kpis`
- 权限：`@PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")`
- 成功：`CommonResult<CutoverDashboardKpiData>`，精确五字段。
- 失败：HTTP 403/500/503的`CommonResult<ErrorData>.data`非空，精确四字段；不返回计数。

- [ ] 实现无`@RestController/@Component`的Controller候选、受信RequestContext和响应VO；测试通过显式Bean激活外壳装配。
- [ ] 将`CutoverDashboardException`无损映射到封闭ErrorData；权限拒绝为403，Provider不可用为503，Owner损坏为500。
- [ ] 不捕获并重分类未知RuntimeException，不按消息文本猜Owner。
- [ ] 正向接口完成后增加Controller合同与MockMvc测试，固定200响应的五字段、字段类型和正常KPI投影。
- [ ] 提交并申请Task 3 REST Contract/Code Review Gate。

**Verify**

```powershell
mvn -pl pms-module-cutover -am -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=CutoverDashboardControllerContractTest,CutoverDashboardControllerMockMvcTest" test
```

## Task 4：统一工作台KPI卡片正常链

**Interfaces**

- `getCutoverDashboardKpis(): Promise<CutoverDashboardKpiData>`调用`/api/v1/pms/cutover-dashboard/kpis`。
- `CutoverDashboardKpis.vue` props为`data: CutoverDashboardKpiData | null`、`loading: boolean`、`error: string | null`；不发业务命令、不改变列表筛选。

- [ ] 在API客户端新增WireLong计数类型和KPI读取函数，不把字符串Long强转JavaScript number。
- [ ] 实现四张只读卡片：待办、已归档、审批中、驳回待修改；格式化`generatedAt`，无数据时显示加载态，错误时显示明确失败而非0。
- [ ] 将卡片挂入新`cutover-task/index.vue`；初次进入与手工刷新并行加载列表/KPI，P1～P6业务写成功后的既有工作台刷新同时刷新KPI。
- [ ] KPI刷新失败不得回滚或重发已成功业务命令；沿用当前工作台写后刷新屏障，只重试读刷新。
- [ ] 正向页面完成后补真实mount组件交互测试，固定四卡渲染、WireLong字符串、生成时间和业务写成功后的只读刷新。
- [ ] 提交并申请Task 4 Frontend Code Review/Component Gate。

**Verify**

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm exec vitest --config src/views/pms/cutover/cutover-task/vitest.config.mjs run
pnpm ts:check
```

## Task 5：受控正向MySQL与统一页面闭环

**Files**

- 新增`pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/dashboard/CutoverDashboardPositiveLoopMySqlTest.java`。
- 新增`yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverDashboardIntegration.spec.ts`。
- 修改`tasks/features/F-CUT-007.md`记录各Gate和证据。

- [x] 用独立MySQL 8.4空卷执行全量Flyway；不新增F-CUT-007迁移。
- [x] 在真实MyBatis与Spring只读事务中准备可见项目的P2/P4/P5/P6/ARCHIVED任务，以受控ActionFact端口提供正常跨模块事实，验证四项计数、重叠和任务/审批/方案/闭环前后不变。
- [x] 真实页面测试挂载生产`cutover-task/index.vue`，使用同形受控API返回KPI与现有任务数据，验证首次加载、P2～P6写后刷新及归档后卡片变化均由真实页面接线触发。
- [x] 正常闭环实现完成后运行适用的CUT单元测试与既有聚焦回归，确认旧页面、旧接口和F-CUT-002～006动作无回归；不扩展失败注入或未实现功能验证。
- [ ] 独立审查通过后把Feature实施状态最多回写为`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`；生产Owner适配、Controller/Service Bean、真实浏览器和Implementation Done继续`BLOCKED_BY_DEPENDENCY`。

**Verify**

```powershell
mvn -pl pms-module-cutover -am -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=CutoverDashboard*Test,CutoverTaskQueryServiceTest,CutoverPlanQueryServiceTest,CutoverApprovalQueryServiceTest,CutoverClosureQueryServiceTest" test
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm exec vitest --config src/views/pms/cutover/cutover-task/vitest.config.mjs run
pnpm ts:check
```

## Gate与完成边界

- 每个Task独立提交并经Code Review Gate；公共文件、生产装配和状态资产串行合入。
- Task 1～5通过只证明CUT自有KPI在受控跨模块事实下的正常闭环，不等于生产Provider完成。
- 生产`CutoverDashboardActionFactPort`适配、唯一QueryService/Controller Bean和真实浏览器验收必须在相关Owner生产契约已接通后另行审查。
- 本Feature不等待或重复实现COM-01；COM不属于KPI Owner依赖。
- `Implementation Done`要求生产Owner、真实权限上下文、真实MySQL和浏览器共同证明四项KPI，当前计划不提前解除该Gate。
