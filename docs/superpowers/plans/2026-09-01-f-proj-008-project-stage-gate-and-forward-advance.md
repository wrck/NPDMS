# F-PROJ-008 项目阶段准出门禁与正向推进 Technical Plan

> Technical Plan ID：`NPDMS-FPROJ008-TECHPLAN-20260901-01`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Feature：`F-PROJ-008`（`BASELINE / READY / NOT_STARTED`）
> Requirement：`PM-03@V1=PARTIAL`
> 基线：`d78e06403d1104fb7d383ee893d2c83f7697c38c`
> **实施代理必读：** 只有本计划获得独立 Technical Plan GO 后才能创建 Feature Task 和修改产品代码。执行顺序固定为先完成正向实现，再补最小直接验证，最后完成一次真实 Chromium 闭环；不得为尚未实现的能力先制造失败测试，也不得扩充低收益负向矩阵。

## 1. 目标与完成边界

唯一业务闭环：

```text
项目工作区读取当前阶段 EXIT Gate
→ 按冻结 Reference 查询 Owner 事实
→ 对 PROCESS/APPROVAL 查询可选定义并启动默认最新或所选历史定义
→ Owner 业务完成后刷新 readiness
→ 项目经理提交相邻推进
→ 原子写当前 Stage DONE、下一 Stage ACTIVE、Gate PASSED、Project.currentStage、StageSnapshot、审计、Outbox
→ 工作区显示新阶段和 STAGE_ADVANCE 历史
```

实施只覆盖 S0→S1、S1→S2、S2→S3、S3→S4。S4→S5继续由 F-COM-001 专用入口处理；回退、异常关闭、重开继续由 F-PROJ-006 处理。

物理结论固定为`NO_PHYSICAL_DELTA`：本计划不新增数据库对象、不新增 Flyway、不增加权限键、不修改 Yudao BPM API/Service/Mapper/DO，不解析 `taskDefinitionKey`，不新增 PMS `processDefinitionVersion/refVersion` 输入。BPM 默认按冻结 `processDefinitionKey`选最新生效定义；授权用户可显式选择同 key 的历史 `processDefinitionId`；流程实例的实际 `processDefinitionId`即冻结身份。

## 2. 当前实现盘点与复用结论

| 现有载体 | 结论 | 本计划用法 |
|---|---|---|
| `ProjectMasterMapper`、`ProjectStageInstanceMapper`、`ProjectGateInstanceMapper`、`ProjectGateReferenceInstanceMapper`、`ProjectMilestoneInstanceMapper` | `DIRECT_REUSE` | 补场景化 Query/XML 锁与 CAS，不复制阶段模型 |
| `proj_project_task`、`proj_project_milestone` | `DIRECT_REUSE` | PROJ 本地 TASK/MILESTONE Owner Fact |
| `acc_project_deliverable`及现有 DO/Mapper | `DIRECT_REUSE_AS_ACC_OWNER` | 由 ACC Provider 锁定唯一根；StageAdvance Service 不读 ACC 表 |
| `ProjectScopeApi`、当前 `PROJECT_MANAGER` 成员事实 | `DIRECT_REUSE` | readiness 用 VIEW；启动和推进用 MANAGE + 当前项目经理 |
| `PlatformCommandExecutionApi`、`ProjectStageSnapshot`、`ProjectGovernanceApplicationService`模式 | `COPY_THEN_ENHANCE` | 新建独立 StageAdvance 应用服务，保持 PM-10 命令不变 |
| `pms-module-integration` Flowable `RepositoryService/RuntimeService/HistoryService` | `COPY_THEN_ENHANCE` | 实现窄 Gate 定义查询、启动和实例事实，不调用 Yudao 内部 Service/Mapper/DO |
| `ProjectMasterController`实例视图、项目详情工作区 | `COPY_THEN_ENHANCE` | 增加阶段门禁面板和正向操作，不新建平行项目工作台 |
| `pms_project_phase`、PM-10治理命令、F-COM-001 S4→S5 | `DO_NOT_REUSE_RUNTIME` | 保持原行为，不形成第二阶段真值 |

## 3. 模块与事务设计

### 3.1 公共 Owner 契约

在 `pms-module-project-api` 新增两个窄接口：

```java
interface ProjectStageGateFactProviderApi {
    String providerKey();
    ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query);
}

interface ProjectStageGateProcessOwnerApi {
    ProjectStageGateProcessDefinitionFact inspectDefinitionKey(
            ProjectStageGateProcessDefinitionQuery query);
    List<ProjectStageGateProcessDefinitionFact> listSelectableDefinitions(
            ProjectStageGateProcessDefinitionSelectionQuery query);
    ProjectStageGateProcessStartFact startProcess(ProjectStageGateProcessStartCommand command);
}
```

`ProjectStageGateFact`只返回稳定 Owner 身份、业务版本、factVersion、封闭 outcome 和 unmetCode。`ProjectStageGateProcessDefinitionFact`只返回定义 ID/key/name/selectable；不返回独立版本号。

`pms-module-integration`增加对`pms-module-project-api`的单向依赖。Project 模块不得依赖 Integration 实现；统一装配只注入公共接口。

### 3.2 readiness 与推进锁序

readiness 是只读预览，加载受信 tenant 下 Project、当前/下一 Stage、当前阶段全部 EXIT Gate/Reference，再按固定 `(gate.sort/id → reference.refType/refCode/id)` 顺序调用 Provider，返回有序结果。

推进事务固定锁序：

```text
Project 当前行
→ 当前/下一 Stage（sortOrder/id）
→ 当前 Stage EXIT Gate（gateId）
→ Gate Reference（refType/refCode/id）
→ Owner 稳定对象
```

推进成功在同一 MySQL 事务内完成：当前 Stage=`DONE`、下一 Stage=`ACTIVE`、全部 EXIT Gate=`PASSED`、Project.current_stage/version、append-only `STAGE_ADVANCE` Snapshot、操作审计、`ProjectStageChanged` Outbox和幂等完成点。任何门禁未满足、Owner不可判定或版本漂移均零阶段写入。

### 3.3 BPM 原生定义身份

`ProjectStageGateProcessOwnerApi`由`pms-module-integration`实现：

- `inspectDefinitionKey`供模板发布校验冻结 key 有可启动定义，禁止 BPMN `START_USER_SELECT(35)`；
- `listSelectableDefinitions`按受信 tenant + 冻结 key 返回可启动定义，稳定排序，不授予或复用 BPM 全局查询权限；
- `startProcess`未选 ID 时由 RepositoryService按 key选最新生效定义，显式 ID 时重验同 tenant、同 key、可启动；
- RuntimeService按实际 definition ID 启动，固定 businessKey=`PROJECT_STAGE_GATE:{gateReferenceId}`，服务端写 tenant/project/stage/gate/reference/refType/refCode/actor/actualProcessDefinitionId及既有 BPM 系统变量；
- Flowable authenticated user 在 `try/finally` 中设置和清理；同 operation 同摘要重放原实例，异摘要冲突；
- BPM Fact按 businessKey读取运行/历史尝试，以 startTime + processInstanceId 稳定选择最新，核对冻结变量及实际 definition ID；状态只认整数 1/2/3/4。

不新增 BPM 映射表，不修改 Yudao 通用发起入口；既有 `ref_version`只保留历史，不参与任何调用。

## 4. 串行实施任务

### Task 1：完成六类 Gate Owner 与模板发布校验

**后端文件：**

- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/ProjectStageGateFactProviderApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/ProjectStageGateProcessOwnerApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateFactQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateFact.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateProcessDefinitionQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateProcessDefinitionSelectionQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateProcessDefinitionFact.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateProcessStartCommand.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/stagegate/dto/ProjectStageGateProcessStartFact.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectStageGateProviderRegistry.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectLocalStageGateFactProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance/ProjectDeliverableStageGateFactProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/stagegate/query/ProjectLocalGateFactQuery.java`
- Create: `pms-module-project/src/main/resources/mapper/stagegate/ProjectLocalStageGateFactMapper.xml`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/stagegate/FlowableProjectStageGateProvider.java`
- Modify: `pms-module-integration/pom.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/ProjectTemplateServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`

**实施：**

1. 先实现公共 DTO/SPI、固定 providerKey Registry 和 PROJ TASK/MILESTONE/STATE Provider。
2. 实现 ACC DELIVERABLE Provider，按 tenant+project+deliverableCode 锁定唯一 `acc_project_deliverable`根，只接受`ACCEPTED`。
3. 实现 Flowable Gate Provider的定义检查、可选定义列表、启动和最新实例事实；删除任何独立版本解释。
4. 在模板发布既有校验链中增加 S0～S3 EXIT Gate非空、Reference完整、固定 Provider存在和 PROCESS/APPROVAL definitionKey可用检查；失败继续保留草稿。
5. 实现完成后补直接单测，覆盖六类正向满足谓词、显式历史 definition ID、默认最新定义、同 operation 重放，以及空 Gate/Owner未知这两个会放行错误阶段的核心拒绝。

**验证：**

```powershell
mvn.cmd -pl pms-module-project,pms-module-integration -am '-Dtest=ProjectStageGateProviderRegistryTest,ProjectLocalStageGateFactProviderTest,ProjectDeliverableStageGateFactProviderTest,FlowableProjectStageGateProviderTest,ProjectTemplateStageGateValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -pl pms-module-project,pms-module-integration -am -DskipTests package
```

### Task 2：完成 readiness、流程启动 REST 与原子相邻推进

**后端文件：**

- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagegate/ProjectStageAdvanceController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagegate/vo/ProjectStageAdvanceReadinessRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagegate/vo/ProjectStageGateProcessDefinitionRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagegate/vo/ProjectStageGateProcessStartReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagegate/vo/ProjectStageAdvanceReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectStageReadinessService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/ProjectStageAdvanceApplicationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/command/ProjectStageAdvanceCommand.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/stagegate/command/ProjectStageAdvanceResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectStagePairForUpdateQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectExitGateForUpdateQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectGateReferenceForUpdateQuery.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectStageInstanceMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectGateInstanceMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectGateReferenceInstanceMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectGateInstanceMapper.xml`
- Create: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectStageInstanceMapper.xml`
- Create: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectGateReferenceInstanceMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectgovernance/ProjectStageSnapshotRules.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`

**实施：**

1. 实现 readiness，返回当前/下一阶段、Project/tree版本、有序 Gate/Reference结果和服务端 allowedActions；不签发推进凭据。
2. 实现 Gate定义查询与启动 Controller。Controller先完成`pms:project:update`，Service再重验 MANAGE和当前有效`PROJECT_MANAGER`；请求只接受可空`processDefinitionId`。
3. 实现`advance-stage`命令：Controller只接 expectedCurrentStage/expectedTreeVersion，目标阶段服务端按冻结 sort推导；S4稳定拒绝并返回专用入口提示。
4. 复用`PlatformCommandExecutionApi`实现请求摘要、审计、`ProjectStageChanged`和重放；实现锁内全量 Owner重验及 Stage/Gate/Project/Snapshot的同事务写入。
5. 代码形成后补服务与真实 MySQL 直接回归，证明一次 S0→S1成功只形成一组业务事实；关键失败只覆盖 Owner未满足和版本漂移的零写入。

**验证：**

```powershell
mvn.cmd -pl pms-module-project -am '-Dtest=ProjectStageReadinessServiceTest,ProjectStageAdvanceApplicationServiceTest,ProjectStageAdvanceControllerTest,ProjectStageAdvanceMySqlIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -pl pms-module-project,pms-module-integration -am -DskipTests package
```

### Task 3：接入项目工作区并完成一次真实正向验收

**前端与验收文件：**

- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectStageGatePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectStageGatePanel.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Create: `scripts/tests/run_fproj008_browser_acceptance.cjs`
- Create after runtime: `tasks/evidence/f-proj-008-browser-acceptance.json`
- Modify after Technical Plan GO: `tasks/features/F-PROJ-008.md`

**实施：**

1. 在现有项目详情工作区增加“阶段门禁”入口与面板，显示当前阶段、相邻目标、Gate/Reference有序结果和依赖未知/业务未满足的不同状态。
2. PROCESS/APPROVAL未启动时，项目经理可保持“默认最新”或从同Gate历史定义列表选择一个`processDefinitionId`；发起后显示流程实例并刷新 readiness。
3. readiness全部满足时显示推进按钮；提交使用服务端返回的Project/tree版本、`If-Match`和`Idempotency-Key`，成功刷新项目详情、Stage任务导航和 STAGE_ADVANCE历史。S4只展示F-COM-001专用入口提示。
4. 页面正向功能完成后补一个聚焦组件测试，再执行 TypeScript检查和本地构建。
5. 真实 Chromium只证明一条高收益正向链：使用正式BPM管理入口准备同 key 可启动定义（如环境已有则直接复用），由正式项目经理从项目工作区查看 Gate→选择默认最新或一个历史定义→发起并按既有BPM工作台完成审批→刷新readiness→推进一个相邻阶段→查看新阶段和STAGE_ADVANCE历史。不得直接改库、调用内部Service或构造第二套验收页面。

**验证：**

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
corepack pnpm vitest run src/views/pms/project/project-master-detail/components/ProjectStageGatePanel.spec.ts
corepack pnpm ts:check
corepack pnpm build:local
Set-Location ../../..
node scripts/tests/run_fproj008_browser_acceptance.cjs
```

Chromium证据只记录：当前/目标阶段、所选与实际processDefinitionId、流程实例ID、门禁满足结果、推进后的Project/Stage/Gate/Snapshot/Audit/Outbox身份，以及页面控制台/请求错误集合。权限、空Gate、Owner不可用和并发零写入由Task 1/2直接测试承担，不扩展浏览器异常矩阵。

## 5. 验收与提交顺序

1. Technical Plan独立GO后才创建唯一`tasks/features/F-PROJ-008.md`。
2. Task 1→Task 2→Task 3串行实施；每个Task先完成正向实现，再运行本Task列出的聚焦验证并提交。
3. 不重跑Phase 1/2/3或CUT全仓门禁；最终只执行受影响模块package、前端build、F-PROJ-008聚焦契约、Requirement追溯和`git diff --check`。
4. 一次Chromium正向闭环通过后申请独立Implementation Done；GO前不得回写完成状态。

## 6. 风险与回退

- Flowable Definition查询/启动/历史事实无法按冻结key和实际definitionId唯一关联时，停止Task 1并回到SDS Gate；不得补PMS映射表或版本字段。
- Owner Provider缺失、重复或事实不完整时失败关闭；不得绕过到本地跨Context读表。
- 应用代码可按Task提交回退；无Flyway和物理数据变更。Stage推进成功产生的Snapshot、审计和Outbox属于不可变业务历史，不通过Git回退或SQL删除撤销。
- 并行CUT和其他Feature只读比对；本计划不修改其Task、迁移、页面或证据，不以回退共享代码解决冲突。

## 7. Plan Self-Review

- 覆盖：六类Owner、模板发布、历史定义查询、默认/显式定义启动、readiness、S0～S4相邻推进、快照/审计/事件、工作区与浏览器正向闭环均有唯一实施落点。
- 边界：无Yudao源码、无CUT、无Flyway、无新权限、无S4→S5、无第三方审批实现。
- BPM身份：只使用definitionKey、显式可空processDefinitionId和实例实际definitionId；无PMS版本模型，无taskDefinitionKey解析。
- 顺序：实现优先，测试后置且聚焦；浏览器只保留一条正向业务链。
- 状态：当前仅为`REVIEW_REQUIRED`候选；本文件不授权创建Task或进入Implementation。
