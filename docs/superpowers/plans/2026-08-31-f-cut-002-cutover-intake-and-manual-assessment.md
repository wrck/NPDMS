# F-CUT-002 割接任务接入与人工分级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Each Task must pass its own independent Gate before the next Task changes shared contracts, Flyway, menus, or production assembly.
>
> Technical Plan Gate：`REVIEW_REQUIRED`

**Goal:** 交付 CUT Owner 的首个正向业务闭环：一线工程师解析有权项目与设备，自建唯一割接任务进入 P2，暂存并提交人工 A/B/C/D 分级，A/B/C 进入 P3、D 进入 P4。

**Architecture:** 在 `pms-module-cutover` 内新建 `CutoverTask` 聚合、四张 CUT Owner 表、新应用服务和新 REST/UI，不修改旧 `CutTask` CRUD、旧表、旧路由或旧页面。CUT 通过本模块消费端口调用 PROJ、AST、CUS、IMP 公共事实；缺少生产 Provider 时只在 `src/test` 显式装配受控正向替身，完整生产 Bean、Controller 和浏览器闭环留到依赖接通 Task。

**Tech Stack:** Java 25、Spring Boot、MyBatis Plus/XML、MySQL 8.4、Flyway、平台命令幂等/审计、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose 与真实浏览器验收。

**Spec:** `specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`；机器输入为 `specs/features/F-CUT-002-physical-contract.json`、`specs/features/F-CUT-002-rest-api-contract.json`、`specs/features/F-CUT-002-customer-service-level-fact-contract.json` 和已通过的 `ImplementationReadinessApi` 公共合同。

## Global Constraints

- Requirement 只覆盖 `CUT-01@V1=PARTIAL`、`CUT-02@V1=PARTIAL`；P3清单以后、P5复核、P6闭环、ITR/项目事件 Producer、V2/V3均排除。
- 旧 `CutTaskController/CutTaskService/CutTaskMapper/CutTaskDO`、`pms_cut_task/risk/plan`、旧 `/pms/cut-task` 和旧 `views/pms/cutover/cut-task` 行为保持不变。
- 新写路径只访问 CUT Owner 表；跨模块只调用公开 Business API，不依赖其他 Context 的 Service、Mapper、DO 或业务表。
- 受控替身只能位于 `src/test` 或前端测试装配；不得进入 `src/main`、生产 Bean、真实浏览器证据或 Implementation Done 证据。
- 按用户锁定顺序先实现当前正向能力，再执行该能力实现后的聚焦单元测试与正向流程验证；只保留直接证明授权、版本、幂等、事务零副作用所必需的守卫测试。
- Mapper 除主键和稳定复合唯一键外只接收一个场景 Query；动态集合、联表、锁查询和 `FOR UPDATE` 全部进入 XML，禁止 SQL 注解、`${}`、`.last(...)`、`Map` 和跨 Context 联表。
- Flyway 不预约版本号；每个串行迁移合入前读取 `sql/migrations`，使用当时下一个未占用版本。本文的 `V{next}` 只表示合入动作，不是已占用编号。
- Feature Implementation Done 必须等待 IMP/CUS 生产 Provider、完整 PROJ项目上下文事实、真实 MySQL、生产装配和真实浏览器验收；测试替身闭环只能证明 CUT 自有实现。

## File Map

- `service/taskintake/port`：CUT 对 PROJ/AST/CUS/IMP 的最窄消费接口。
- `service/taskintake/adapter`：只调用已冻结公共 API 的生产适配器；缺 Owner 合同的适配器不注册 Bean。
- `domain/taskintake`：任务、评估、阶段转换及快照规则，不访问数据库或 Spring。
- `dal/dataobject/taskintake`、`dal/mysql/taskintake`、`mapper/taskintake`：四张新表与场景查询。
- `service/taskintake`：P1上下文、自建、P2暂存/提交、列表/详情和内部接入编排。
- `controller/admin/cutovertask`：六条新 REST 路由与局部错误合同；Task 12 前不注册生产 Controller。
- `yudao-ui/.../cutover/cutover-task`：新工作台；旧 `cut-task` 页面零修改。

---

### Task 1: CUT 消费端口与受控正向装配边界

**Files:**

- Modify: `pms-module-cutover/pom.xml`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/port/CutoverProjectScopePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/port/CutoverProjectContextPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/port/CutoverDeviceScopePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/port/CutoverCustomerLevelPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/port/CutoverReadinessPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/ProjectScopeApiAdapter.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/DeviceScopeFactApiAdapter.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/CustomerServiceLevelFactApiAdapter.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/ImplementationReadinessApiAdapter.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/CutoverOwnerAdapterTest.java`

**Interfaces:**

- Consumes: `ProjectScopeApi.ACTION_VIEW/ACTION_EDIT`、`DeviceScopeFactApi.resolveBySerials/lockAndRevalidate`、`CustomerServiceLevelFactApi.inspectCurrent/lockAndRevalidate`、`ImplementationReadinessApi.inspect/lockAndRevalidate`。
- Produces: CUT 内部 `ProjectScopeFact`、`ProjectContextFact`、`DeviceScopeFact`、`CustomerLevelFact`、`ReadinessFact`，供 Task 5/6 编排。

- [ ] 新增五个端口及不可变 record。成功事实必须保留 Owner 的完整身份和水位；例如：

```java
public interface CutoverReadinessPort {
    ReadinessFact inspect(Long projectId, List<DeviceIdentity> devices);
    ReadinessFact lockAndRevalidate(ReadinessExpectation expected);
}

public record ReadinessExpectation(Long projectId, Long snapshotId,
        Long snapshotVersion, List<DeviceIdentity> devices) {}
```

- [ ] `ProjectScopeApiAdapter`只负责 ACTION_VIEW/ACTION_EDIT 范围和 `scopeVersion`；`DeviceScopeFactApiAdapter`使用与 AST 一致的 `trim + Locale.ROOT uppercase` SN 比较键，但快照保留 Owner 原值。
- [ ] CUS与IMP适配器原样构造已通过合同的完整 expected fact；不得把 `CustomerSummaryDTO`、旧就绪校验或当前 inspect 结果回填成冻结期望。
- [ ] `CutoverProjectContextPort`固定返回 `projectId/projectCode/projectName/officeCode/officeName/customerId/projectScopeVersion`。当前没有一个已冻结的PROJ公共事实能完整提供该结果，因此本Task只定义CUT端口和 `src/test` 正向替身，不创建生产Adapter、`@Component`或跨表读取。
- [ ] 完成实现后新增 `CutoverOwnerAdapterTest`，验证成功事实原样映射、SN规范身份、READY/AVAILABLE与稳定水位；同时验证公共 Provider 不可用时保留稳定错误分类，不写业务状态。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverOwnerAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `CUT Owner Consumption Adapter Contract/Code Review`；未通过不进入聚合实现。

### Task 2: 四表 Schema 与旧任务只读前向入口

**Files:**

- Create at serial merge: `sql/migrations/V{next}__fcut002_cutover_task_intake.sql`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskintake/CutoverTaskDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskintake/CutoverTaskDeviceScopeDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskintake/CutoverTaskStageHistoryDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskintake/CutoverAssessmentDO.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/taskintake/Fcut002MigrationContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/taskintake/Fcut002SchemaMySqlTest.java`

**Interfaces:**

- Consumes: `specs/features/F-CUT-002-physical-contract.json` 四表列、联合、唯一键和 CHECK。
- Produces: `cut_task`、`cut_task_device_scope`、`cut_task_stage_history`、`cut_assessment` 物理载体。

- [ ] 合入时确定唯一实际版本，一次前向迁移建立四表；完整实现 `taskOrigin/intakeSourceType`、`NEW_PLATFORM/LEGACY_FORWARD`、活动设备 marker、评估 current marker、阶段历史只追加和乐观锁约束。
- [ ] 旧 `pms_cut_task` 不在 Schema 迁移中转换为 READY/可办理任务，不修改、删除或双写；四表不从测试种子、旧状态或默认值补造客户、设备、IMP快照或评估。
- [ ] `CutoverTaskDO`与旧 `CutTaskDO`使用不同包和类名；不得修改旧 Mapper/Service/Controller。
- [ ] 完成DDL和DO后再新增静态合同与隔离MySQL测试，验证新路径四表、活动设备唯一、来源联合、评估版本唯一、历史不可更新及旧三表结构/数据不变。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=Fcut002MigrationContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Run with isolated MySQL 8.4: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=false' '-Dtest=Fcut002SchemaMySqlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 Schema/迁移合同与隔离MySQL Gate`。

### Task 3: 场景 Mapper、投影与全局锁序

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/CutoverTaskCommandMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/CutoverTaskQueryMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/CutoverAssessmentMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/CutoverStageHistoryMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/query/CutoverTaskLockQuery.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/query/ActiveDeviceScopeLockQuery.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/query/CutoverTaskPageQuery.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/query/CutoverTaskDetailQuery.java`
- Create: `pms-module-cutover/src/main/resources/mapper/taskintake/CutoverTaskCommandMapper.xml`
- Create: `pms-module-cutover/src/main/resources/mapper/taskintake/CutoverTaskQueryMapper.xml`
- Create: `pms-module-cutover/src/main/resources/mapper/taskintake/CutoverAssessmentMapper.xml`
- Create: `pms-module-cutover/src/main/resources/mapper/taskintake/CutoverStageHistoryMapper.xml`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/CutoverTaskMapperContractTest.java`

**Interfaces:**

- Produces: Task 5/6所需的分页、详情、当前评估、活动设备、聚合CAS和追加历史操作。

- [ ] 锁序固定为 `projectId -> deviceId ASC -> cutoverTaskId -> currentAssessmentId`；所有生产写命令使用同序，禁止调用旧 `CutTaskMapper`。
- [ ] `selectActiveDeviceScopesForUpdate`按 tenant/project/deviceId集合完整锁定，空设备集合在Service边界拒绝；`selectTaskForUpdate`和`selectCurrentAssessmentForUpdate`只在事务中调用。
- [ ] 分页先由 `ProjectScopeApi.ACTION_VIEW`得到可见项目集合；空集合直接返回空页，排序固定 `scheduled_time DESC,id DESC`。
- [ ] 完成Mapper/XML后新增真实 `XMLMapperBuilder + BoundSql + MetaObject` 测试，固定动态集合参数、tenant/deleted条件、稳定排序、`FOR UPDATE`和禁止 `${}`。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverTaskMapperContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 Mapper/锁序 Contract Gate`。

### Task 4: CUT领域状态机与快照规则

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverTaskStateMachine.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverAssessmentRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverContextSnapshotRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverSerialIdentity.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverTaskStateMachineTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/domain/taskintake/CutoverAssessmentRulesTest.java`

**Interfaces:**

- Produces: `P1_ACCEPTED -> P2/GRADE_CONFIRMING`、A/B/C→P3/SURVEYING、D→P4/PLAN_DRAFTING，及固定问卷快照验证。

- [ ] 实现 `CUT_P2_MANUAL_ASSESSMENT@1` 四个精确答案键、DRAFT可空、SUBMITTED全非空、人工等级A/B/C/D及 `simpleFlow = grade == D`。
- [ ] 实现状态机纯函数：

```java
public StageTransition submit(String currentStage, String taskStatus, String manualGrade) {
    return "D".equals(manualGrade)
            ? new StageTransition("P2", "P4", "PLAN_DRAFTING")
            : new StageTransition("P2", "P3", "SURVEYING");
}
```

- [ ] 实现 project/devices/readiness/customer 四段上下文严格JSON结构；SUBMITTED只接受 READY且unmetCodes为空、CUS AVAILABLE，禁止用户输入替代Owner事实。
- [ ] 完成领域实现后新增正向A/B/C/D转换、DRAFT暂存、READY+AVAILABLE提交测试，以及直接证明缺答案/非READY/NOT_CONFIGURED不得推进的守卫测试。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverTaskStateMachineTest,CutoverAssessmentRulesTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 Domain Rules Code Review Gate`。

### Task 5: P1上下文解析与一线自建正向闭环

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskCommands.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskViews.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskNumberGenerator.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/enums/ErrorCodeConstants.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskApplicationServiceTest.java`

**Interfaces:**

- Consumes: Task 1端口、Task 3 Mapper、`PlatformCommandExecutionApi`。
- Produces: `resolveCreateContext`与`createSelfCreated`。

- [ ] `resolveCreateContext`规范化并去重1..500个SN，调用AST解析完整设备集合，按projectId分组，以ACTION_EDIT过滤候选，再读取ProjectContext/CUS/IMP inspect；返回所有候选且不代选。
- [ ] `createSelfCreated`在平台命令NEW事务内按固定锁序重验ACTION_EDIT、完整项目上下文、AST设备归属、CUS完整前次事实和IMP明确READY快照，随后锁活动设备范围。
- [ ] 服务端生成taskNo、NEW_PLATFORM/SELF_CREATED、P2/GRADE_CONFIRMING；原子写一条根、全部活动设备、P1_ACCEPTED历史和平台成功事实。命令摘要排除correlationId，SuccessFacts保留同一受信correlationId。
- [ ] 同键同规范业务载荷返回同一任务；同键异载荷、活动设备冲突、Owner事实过期或不可用在业务写前失败，平台命令与四表零部分成功。
- [ ] 完成正向实现后使用 `src/test` 显式构造的PROJ/AST/CUS/IMP正向替身验证：多项目候选明确选择一个项目→创建唯一任务→P2，重放仍返回同一taskId。替身不声明 `@Component/@Bean`。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverTaskApplicationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 P1 Positive Closure Code Review Gate`。

### Task 6: P2问卷暂存、提交与P3/P4分支

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverAssessmentApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverAssessmentCommands.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverAssessmentApplicationServiceTest.java`

**Interfaces:**

- Consumes: Task 1端口、Task 3 Mapper、Task 4状态机、平台幂等。
- Produces: `saveAssessment`、`submitAssessment`。

- [ ] `saveAssessment`锁任务与当前DRAFT；首次创建version 1、后续按 `If-Match + Assessment-If-Match` CAS更新。服务端刷新project/device/readiness/customer inspect上下文，允许NOT_READY或NOT_CONFIGURED，仅保存草稿，不推进任务。
- [ ] `submitAssessment`锁任务、当前DRAFT和设备，再按冻结值执行PROJ/AST/CUS/IMP lockAndRevalidate；只有完整答案、人工等级、READY和AVAILABLE进入状态机。
- [ ] 同一事务把评估标为不可变SUBMITTED、设置task.manualGrade/currentAssessmentId、任务CAS、追加P2_ASSESSMENT_SUBMITTED历史和平台成功事实；A/B/C进入P3，D进入P4。
- [ ] 完成实现后用同一组受控正向替身跑两条业务链：A→P3/SURVEYING与D→P4/PLAN_DRAFTING；再验证草稿从NOT_CONFIGURED刷新为AVAILABLE后可提交，且提交重放不新增历史。
- [ ] 只补直接守护正向原子性的测试：任一冻结水位陈旧或Provider不可用时任务、评估、历史与成功事实均不改变。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverAssessmentApplicationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 P2→P3/P4 Positive Closure Code Review Gate`。

### Task 7: 列表、详情、allowedActions 与内部接入API

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskQueryService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/taskintake/CutoverTaskIntakeApi.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/taskintake/CutoverTaskIntakeCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/taskintake/CutoverTaskIntakeResult.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskIntakeService.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/CutoverTaskQueryServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/taskintake/CutoverTaskIntakeApiContractTest.java`

**Interfaces:**

- Produces: 稳定分页/详情、P2～P6固定工作台、`SAVE_ASSESSMENT/SUBMIT_ASSESSMENT`及ITR/PROJECT_EVENT内部命令边界。

- [ ] 查询以ACTION_VIEW裁剪可见项目；详情不取写锁。`LEGACY_FORWARD`只读投影始终返回空allowedActions。
- [ ] allowedActions逐项同构功能权限、ACTION_EDIT、task owner、P2/GRADE_CONFIRMING、当前DRAFT与只读Owner inspect；真正写命令不信任该投影并独立锁定重验。
- [ ] `CutoverTaskIntakeApi.create`固定ITR/PROJECT_EVENT严格联合、来源幂等和受信handlingEngineerUserId；只复用Task 5的共同创建内核，不实现Producer、HTTP客户端、指派或领取。
- [ ] 完成实现后验证分页稳定顺序、P2～P6骨架、正向allowedActions和两种内部来源重放；不创建任何模拟Producer。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverTaskQueryServiceTest,CutoverTaskIntakeApiContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 Query/Internal API Code Review Gate`。

### Task 8: 六路由REST合同与局部错误映射

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/CutoverTaskController.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/CutoverTaskHttpException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/vo/CutoverCreateContextReqVO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/vo/CutoverTaskCreateReqVO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/vo/CutoverAssessmentSaveReqVO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/vo/CutoverTaskPageReqVO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/vo/CutoverTaskRespVO.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/CutoverTaskControllerContractTest.java`

**Interfaces:**

- Consumes: Task 5/6/7服务。
- Produces: `/api/v1/pms/cutover-tasks` 六路由与四权限键。

- [ ] 实现精确Request/Response、WireLong、epoch毫秒时间、`Idempotency-Key/If-Match/Assessment-If-Match`和未知字段失败关闭；客户端不能提交tenant/actor/customer/taskNo/status/gradeOnTask。
- [ ] 错误类型按机器合同稳定映射400/403/404/409/422/503及恢复动作，不按异常message猜测，不修改全局Yudao处理器。
- [ ] Task 12前Controller类不加 `@RestController/@Component`，测试通过test-only配置挂载真实Service与受控Owner替身。
- [ ] 完成HTTP实现后使用MockMvc覆盖P1创建→P2详情、保存→A提交到P3、D提交到P4，以及Header、版本和Owner失败的精确envelope；这些守卫测试只证明正式错误合同和零副作用。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverTaskControllerContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 REST Code Review/MockMvc Gate`。

### Task 9: CURRENT_FORWARD 应用核对与PLT证据

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/migration/CutoverLegacyReconciliationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/migration/CutoverLegacyReconciliationJob.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskintake/query/LegacyCutoverReconciliationQuery.java`
- Create: `pms-module-cutover/src/main/resources/mapper/taskintake/LegacyCutoverReconciliationMapper.xml`
- Create at serial merge: `sql/migrations/V{next}__fcut002_legacy_reconciliation_job.sql`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/migration/CutoverLegacyReconciliationServiceTest.java`

**Interfaces:**

- Consumes: `PlatformMigrationEvidenceApi`与已暂存STAGED_READY来源批次；不读取外部遗留库。
- Produces: 合格 `LEGACY_FORWARD/LEGACY_UNKNOWN`只读根或PLT mapping/issue分类。

- [ ] Job初始以 `infra_job.status=2` 幂等登记；无正式暂存批次时保持PAUSED，不注册Quartz激活器。
- [ ] 在调用方外层事务中claim批次，逐行读取当前库旧 `pms_cut_task`，按已通过映射判定；合格行只写一条只读 `cut_task`，不写设备、评估或阶段历史。
- [ ] Owner暂时不可用使批次整体回滚到STAGED_READY；确定性源损坏、Owner不匹配或目标冲突追加PLT issue；完整计数后才COMPLETED。
- [ ] 完成实现后验证各 disposition、重放、旧表零修改和只读任务无allowedActions。测试fixture只证明代码，不冒充生产迁移完成证据。
- [ ] Run: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=true' '-Dtest=CutoverLegacyReconciliationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 CURRENT_FORWARD Code Review Gate`。

### Task 10: 菜单字典与新前端工作台

**Files:**

- Create at serial merge: `sql/migrations/V{next}__fcut002_menu_and_dictionary.sql`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/CutoverCreateDialog.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/CutoverAssessmentPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/CutoverWorkbenchSteps.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/CutoverTaskDetailDrawer.vue`
- Test: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/__tests__/cutover-task.spec.ts`

**Interfaces:**

- Consumes: Task 8六路由与服务端allowedActions。
- Produces: 新菜单、四权限与P1→P2→P3/P4响应式工作台。

- [ ] Flyway幂等登记 `pms:cutover-task:query/create/save-assessment/submit-assessment`；不改旧 `pms:cut-task:*`、旧菜单或角色菜单关系。
- [ ] 创建页先输入SN并展示全部候选，多候选必须显式选择；NOT_CONFIGURED只提示并允许创建/草稿，不允许提交。
- [ ] 详情固定显示P2～P6；只按allowedActions与权限显示保存/提交，P3/P4/P5/P6未实现操作不渲染伪按钮。
- [ ] 时间统一epoch毫秒转换，Snowflake Long保持WireLong，不转JavaScript Number；命令成功后刷新失败只重试刷新，不重发业务命令。
- [ ] 完成组件实现后真实mount四组件，覆盖候选选择、A→P3、D→P4、幂等恢复、Owner刷新、320/768/1024/1440布局；旧页面测试原样复跑。
- [ ] Run: `pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 vitest run src/views/pms/cutover/cutover-task/__tests__/cutover-task.spec.ts`
- [ ] Run: `pnpm.cmd --dir yudao-ui/yudao-ui-admin-vue3 ts:check`
- [ ] Gate: `F-CUT-002 Frontend Code Review/Component Gate`。

### Task 11: CUT自有真实MySQL正向闭环

**Files:**

- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/taskintake/CutoverTaskApplicationMySqlTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/taskintake/CutoverTaskConcurrencyMySqlTest.java`
- Modify: `tasks/features/F-CUT-002.md`

**Interfaces:**

- Consumes: Tasks 1-10；Owner事实使用test-only确定性正向Provider。
- Produces: CUT四表、平台幂等/审计与事务正向证据；不产生生产Owner完成证据。

- [ ] 使用独立Compose项目、空MySQL 8.4卷和真实Spring事务/MyBatis/`PlatformCommandExecutionApiImpl`；test-only配置装配PROJ/AST/CUS/IMP正向Provider。
- [ ] 正向链一：多项目SN解析→明确选择→创建P2→草稿→A级提交→P3；正向链二：D级提交→P4。断言根、设备、评估、历史、成功审计和幂等事实一致。
- [ ] 并发验证同一设备双创建最多一个成功、同一评估版本双提交只形成一条迁移历史；失败方平台认领与业务写整体回滚。
- [ ] 复跑旧 `CutTaskStatusRulesTest`、`CutTaskMapperContractTest`及旧Service测试，证明旧运行面未改变。
- [ ] Run with isolated MySQL 8.4: `mvn.cmd -pl pms-module-cutover -am '-DskipITs=false' '-Dtest=CutoverTaskApplicationMySqlTest,CutoverTaskConcurrencyMySqlTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- [ ] Gate: `F-CUT-002 CUT-owned MySQL Positive Closure Gate`；通过后仍不得声明Feature Done。

### Task 12: 生产依赖接通、唯一Bean与真实浏览器

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskintake/adapter/ProjectContextApiAdapter.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/config/CutoverTaskIntakeConfiguration.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/cutovertask/CutoverTaskController.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/taskintake/CutoverTaskProductionAssemblyTest.java`
- Modify: `tasks/features/F-CUT-002.md`

**Interfaces:**

- Consumes: 生产 `ProjectScopeApi`、完整PROJ项目/办事处/客户上下文公开事实、`DeviceScopeFactApi`、`CustomerServiceLevelFactApi`、`ImplementationReadinessApi`。
- Produces: 唯一生产ApplicationService/QueryService/Controller/Internal API Bean与真实浏览器路径。

- [ ] 本Task开始前逐项核验生产Provider和双向契约测试已合入；缺任一项保持 `BLOCKED_BY_DEPENDENCY`，不注册fallback、空结果Provider或Fake。
- [ ] PROJ物理Owner必须先提供可冻结/重验的完整项目上下文公共事实；`ProjectContextApiAdapter`只调用该正式契约。现有零散summary/旧guard不能拼接替代，CUT不得读PROJ/SYSTEM表。
- [ ] 在同一依赖接通提交中给四个Owner Adapter和CUT服务配置唯一生产Bean，再激活Controller；启动测试断言无重复Bean、无测试类进入main装配、Owner不可用失败关闭。
- [ ] 真实浏览器只使用IMP生产Provider产生的READY快照与CUS生产AVAILABLE事实，完成P1→P2→P3和P1→P2→P4；验证租户、权限、幂等、并发与响应式布局。
- [ ] 只有真实Provider、真实MySQL、浏览器证据、旧运行面回归及独立Implementation Done复审全部通过，才更新F-CUT-002为完成；否则Task保持受依赖阻断。
- [ ] Gate: `F-CUT-002 Production Assembly/Browser/Implementation Done Gate`。

## Plan Self-Review

- Spec coverage：Task 1锁消费边界；Task 2/3覆盖四表与锁序；Task 4覆盖状态/问卷；Task 5/6覆盖P1→P2→P3/P4；Task 7/8覆盖查询、内部API与REST；Task 9覆盖CURRENT_FORWARD；Task 10覆盖UI/菜单；Task 11/12分离受控替身证据与生产验收。
- Scope check：没有实现IMP/CUS/AST/PROJ Owner、ITR/项目事件Producer、P3清单以后、P5/P6、V2/V3或旧CRUD增强。
- Type consistency：任务ID/Owner ID使用Long，聚合version使用Integer，Owner factVersion/snapshotVersion使用Long；设备和来源集合稳定排序，时间经REST使用epoch毫秒。
- Test timing：每个Task先完成其正向实现，再新增和执行该Task的聚焦测试；未把未实现能力的预期失败当作进度证据。
