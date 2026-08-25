# F-PROJ-007 项目任务树与原生任务工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本工程链在当前会话内联执行，不启用子代理。

**Goal:** 按PRD V1.8实现任意深度项目任务树、基础依赖、时态指派、版本化状态机、TASK_NATIVE工作台和叶子项目任务进度事实，并退役V1.7任务写入口。

**Architecture:** `proj_project_task`继续作为唯一当前任务真值，邻接关系与闭包路径共同支持按需树查询和原子移动；PROJ应用服务通过`PlatformCommandExecutionApi`统一完成幂等、审计与Outbox。状态机版本、责任区间、完成判定和项目进度事实均前向追加；非原生WorkBinding只保留注册宿主和失败关闭边界，不创建无生产者适配器。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md`

## Global Constraints

- 规格仓库提交锁定为`5f37b2d1adf4666ccfc595f0acf1829cd323e44f`；受管快照只由同步工具维护。
- 已通过的PRD/SDS/Feature Ready门禁不重开；本计划只推进F-PROJ-007 Implementation，不生成Deployment、SIT、UAT或Release材料。
- V1.7 `pms_project_task`、`pms_project_task_dependency`、旧Controller/Service/Mapper/UI只作差距审计输入；不得据此勾选V1.8验收项，不双写、不推断迁移映射。
- 每个Task写代码前先逐方法核对旧实现与V1.8规则；语义、权限、租户、并发和物理真值均满足时优先原位复用，旧包必须退役时先最小拷贝可复用逻辑再改造，禁止重复重写相同算法。
- 复用不等于已实现：每段复用逻辑仍须绑定本Feature AC并重新验证；拷贝后的最终提交只保留一个当前实现，不能形成长期新旧双份逻辑。
- 用户已禁用测试驱动顺序；每个Task先完成最小实现，再补风险匹配的自动化回归，计划中的测试不是实现前置失败测试。
- 不修改`yudao-framework`及Yudao基础框架；租户启停沿用基础框架现有参数和语法，不引入模块私有租户机制。
- 跨模块只使用Owner公开API；不得依赖目标模块`-biz`、Service、Mapper、Repository或业务表，不创建无稳定调用方的空`-api`模块。
- `proj_project_task`是唯一当前真值；结构关系以`parent_task_id`为准，`parent_task_code`只保留模板来源快照。
- 核心状态固定为`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT/DONE/CLOSED`；任何未知状态、动作、映射或事实失败关闭。
- TASK_NATIVE完成规则统一为`requiredStatus=DONE`；通过V88之后的新Flyway前向修正V63已执行数据，不修改V63。
- 项目任务树版本`task_tree_version`和任务进度版本`task_progress_version`与项目父子树版本、Project CAS版本相互独立。
- 所有命令使用`Idempotency-Key`；修改任务行使用`If-Match`，移动另校验`expectedTaskTreeVersion`，冲突不得产生成功责任区间、完成判定、审计或事件。
- 新增数据库查询必须遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外只接收场景Query；复杂、动态集合、递归、锁查询进入Mapper XML；空范围返回空。
- 权限以服务端`功能权限 + ProjectTreeScope + 任务主体 + 状态/版本`为真值；前端按钮可见性不能替代授权。
- UI优先复用Yudao现有页面组件，其次Element Plus；支持320/768/1024/1440视口、主题变量和无页面级横向溢出，减少内联样式。
- 每个Task完成自动化验证和自审后按情况独立本地提交，不推送。

## Current Implementation Audit

| 现有资产 | F-PROJ-007处置 |
|---|---|
| `ProjectTaskInstanceDO` / `ProjectTaskInstanceMapper` | 继续映射`proj_project_task`并前向扩展；不另建同义当前任务DO或表 |
| `ProjectTaskExecutionContractDO` / `TaskExecutionContractFactory` | 复用当前执行契约；人工任务默认原子创建TASK_NATIVE契约 |
| 旧`projecttask`包及独立“任务WBS”页面 | 当前写`pms_project_task`和整数状态；实施完成前退役写入口，允许受控跳转新页 |
| `ProjectTaskGovernanceGuardProvider` | 当前读取旧表；改为读取`proj_project_task`终态真值，避免治理守卫漏检 |
| `ProjectProgressSnapshotService` / `proj_project_progress_fact` | 复用F-PROJ-002消费端；新增PROJECT_TASK生产事实和来源水位，不回写兼容`proj_project.progress` |
| `ProjectTreeScopeService` | 复用VIEW/EDIT/MANAGE项目范围；任务查询再叠加任务主体裁剪 |
| `PlatformCommandExecutionApi` | 复用同事务幂等、`plt_operation_audit`和Outbox写入 |
| `ProjectServiceManagerQueryService`及SYSTEM公开API | 复用`AdminUserApi/DeptApi/CompanyApi`和组织候选分页，不直查SYSTEM表 |
| V57/V63迁移 | 只作已执行物理事实；用新迁移加字段、表、种子和`COMPLETED -> DONE`修正 |

## Reuse-First Execution Rule

每个Task开始时先完成一次聚焦复用判断，并把结论写入该Task自审：

1. 读取该Task直接相关的旧Controller/Service/Mapper/XML/UI和测试，不扫描无关模块；
2. 对照Feature Spec逐项判断物理真值、状态、权限、租户、幂等、并发和返回契约是否一致；
3. 完全一致的逻辑直接调用或迁移原方法；部分一致的先最小拷贝可用算法，再替换旧表、旧状态和旧权限边界；不一致的才重新实现；
4. 用差异测试证明改造后的当前路径满足V1.8，并在旧写入口退役后删除本次拷贝造成的临时重复；不得因旧测试通过直接勾选AC。

优先复用顺序：现有V1.8 `projectmanual/projecttree/projectprogress/platform command`能力 > 同工作树V1.7算法 > 新实现。不得从其他工作树复制环境参数、数据库名、端口、计划或Feature状态。

---

### Task 1: 建立任务树、状态机和责任事实物理基础

**Files:**
- Create: `sql/migrations/V88__fproj007_project_task_runtime.sql`
- Create: `sql/migrations/V89__fproj007_project_task_seed.sql`
- Create: `scripts/tests/test_fproj007_v18_migration.py`

**Interfaces:**
- Consumes: F-PROJ-007物理契约中的表、字段、唯一键、状态、权限和菜单边界。
- Produces: `proj_project_task`/`proj_project`前向列、6张闭包/依赖/责任/完成判定/状态机新表、默认状态机、存量任务状态机版本回填、权限菜单和V63规则修正。

- [ ] **Step 1: 编写V88前向结构迁移**

为`proj_project_task`增加`parent_task_id/root_task_id/tree_depth/business_level_code/milestone_id/plan_start_time/plan_end_time/actual_start_time/actual_end_time/progress/state_machine_revision_id`；为`proj_project`增加`task_tree_version/task_progress_version`。创建物理契约列出的6张新表：`proj_task_tree_path`、`proj_task_dependency`、`proj_project_task_assignment`、`proj_project_task_completion_evaluation`、`proj_task_state_machine_revision`、`proj_task_state_transition`，并建立唯一键、外键和查询索引；不修改V1～V87。

- [ ] **Step 2: 在V88修正规则与模板实例路径**

仅将TASK_NATIVE当前契约或模板定义中`TASK_NATIVE_STATUS.requiredStatus=COMPLETED`的JSON前向修正为`DONE`；从稳定的`project_id/task_code/parent_task_code`回填当前模板实例任务的`parent_task_id/root_task_id/tree_depth`和自反/祖先闭包。无法唯一解析的记录让迁移失败，不猜测绑定。

- [ ] **Step 3: 编写V89幂等种子**

为每个现有租户追加且只追加一个默认PUBLISHED核心状态机版本，迁移精确为4条线性动作：`PENDING_ASSIGN --ASSIGN--> PENDING_START`、`PENDING_START --START--> IN_PROGRESS`、`IN_PROGRESS --SUBMIT--> PENDING_ACCEPT`、`PENDING_ACCEPT --COMPLETE--> DONE`，以及4条CANCEL动作：`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT --CANCEL--> CLOSED`。随后按同租户确定性回填所有存量`proj_project_task.state_machine_revision_id`到该租户默认已发布版本，并以迁移断言保证同租户且无NULL。写入稳定权限和新项目任务页菜单；停用旧写菜单及`pms:project-task:delete`角色关联，不删除历史表。

- [ ] **Step 4: 增加迁移契约回归**

`test_fproj007_v18_migration.py`断言：V1～V87哈希不变、恰有6张新表、无固定层级列/约束、共享审计表仍为`plt_operation_audit`、旧任务表无新增写触发器、核心状态精确为4条线性迁移加4条CANCEL迁移、存量任务版本同租户且无NULL、权限精确、修正规则只命中TASK_NATIVE。

- [ ] **Step 5: 验证并提交**

Run: `python -m unittest scripts.tests.test_fproj007_v18_migration`

Run: `mvn.cmd -pl pms-module-project -am '-DskipTests' compile`

Expected: 迁移契约PASS，Reactor编译成功。提交：`feat(project): 建立项目任务运行时物理基础`

---

### Task 2: 实现当前任务、闭包路径和版本水位持久化

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectTaskInstanceDO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectMasterDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/ProjectTaskTreePathDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskRuntimeMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskTreePathMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectTaskTreeQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectTaskMoveLockQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectTaskStructureUpdate.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectTaskTreeVersionUpdate.java`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectTaskRuntimeMapper.xml`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectTaskTreePathMapper.xml`

**Interfaces:**

```java
List<ProjectTaskInstanceDO> selectTree(ProjectTaskTreeQuery query);
ProjectTaskMoveLocks selectMoveLocks(ProjectTaskMoveLockQuery query);
int updateStructureIfMatch(ProjectTaskStructureUpdate update);
int incrementTaskTreeVersion(ProjectTaskTreeVersionUpdate update);
void rebuildMovedSubtreePaths(ProjectTaskStructureUpdate update);
```

- [ ] **Step 1: 扩展当前DO而不复制真值**

在`ProjectTaskInstanceDO`和`ProjectMasterDO`映射V88新增列；`version/taskTreeVersion/taskProgressVersion`继续使用显式CAS，不启用全局`@Version`拦截器。

- [ ] **Step 2: 实现五种树查询**

`ProjectTaskTreeQuery`封装租户、项目范围、任务范围、查询模式、父/目标/业务层级、关键词、稳定游标和`pageSize`；XML分别实现`DIRECT_CHILDREN/ALL_DESCENDANTS/ANCESTOR_CHAIN/BUSINESS_LEVEL/LOCATE`，按`sort_order,id`稳定排序，空范围直接返回空。

- [ ] **Step 3: 实现锁顺序和闭包更新**

移动统一按Project→源任务→目标父任务→移动子树的顺序加锁；XML删除受影响的旧跨界闭包、插入新祖先组合并更新子树`root_task_id/tree_depth`，最后CAS递增`task_tree_version`。

- [ ] **Step 4: 补持久化回归**

新增`ProjectTaskRuntimeMapperTest`覆盖深度30、直接下级/后代/祖先/定位、空集合、跨租户、稳定游标、循环目标识别、CAS冲突和失败后闭包未变。

- [ ] **Step 5: 验证并提交**

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskRuntimeMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS，未查询或写入`pms_project_task`。提交：`feat(project): 提供当前任务树持久化`

---

### Task 3: 实现依赖、责任区间、状态机版本和完成判定持久化

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/ProjectTaskDependencyDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/ProjectTaskAssignmentDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/ProjectTaskCompletionEvaluationDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/TaskStateMachineRevisionDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/taskworkbench/TaskStateTransitionDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskDependencyMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskAssignmentMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskCompletionEvaluationMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/TaskStateMachineMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/TaskStateMachinePublishedQuery.java`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectTaskDependencyMapper.xml`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectTaskAssignmentMapper.xml`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/TaskStateMachineMapper.xml`

**Interfaces:**

```java
boolean existsDependencyPath(TaskDependencyPathQuery query);
ProjectTaskAssignmentDO selectCurrentForUpdate(TaskAssignmentLockQuery query);
TaskStateMachineDefinition selectPublished(TaskStateMachinePublishedQuery query);
TaskStateTransitionDO requireTransition(Long revisionId, String fromStatus, String action);
int insertEvaluation(ProjectTaskCompletionEvaluationDO evaluation);
```

- [ ] **Step 1: 映射append-only与时态对象**

责任转派在同一服务端时点关闭旧`effective_to/current_marker`并插入新行；完成判定只插入不覆盖；已发布状态机版本和迁移不可更新业务内容。

- [ ] **Step 2: 实现依赖无环查询**

依赖写查询使用场景对象和XML递归CTE，只允许四个稳定依赖类型，拒绝自身、跨租户、跨项目和任何可达回路；移动命令不触碰依赖行。

- [ ] **Step 3: 实现状态机读取与发布持久化**

默认读取当前已发布版本；新任务冻结revisionId。发布以CAS把草稿变为PUBLISHED，校验核心状态不可缺失或改义、每个动作映射唯一、未知角色/条件拒绝；不自动升级存量任务。

- [ ] **Step 4: 补持久化回归并提交**

覆盖同任务最多一个当前负责人、历史不复活、依赖环、核心映射、并发发布、完成判定幂等唯一键和跨租户拒绝。

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskDependencyMapperTest,ProjectTaskAssignmentMapperTest,TaskStateMachineMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 持久化任务责任与状态规则`

---

### Task 4: 提供项目工作区、任务树、详情和工作台只读API

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/ProjectTaskWorkbenchController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskTreeQueryReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskNodeRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskDetailRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskWorkbenchRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectWorkspaceRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskQueryService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskBindingHostRegistry.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskBindingHostProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskBindingInspectionQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskBindingInspection.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskNativeBindingHostProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskWorkbenchActor.java`

**Interfaces:**

```java
record TaskWorkbenchActor(Long tenantId, Long actorId, String correlationId) {}
record TaskQueryResult(List<ProjectTaskNodeRespVO> rows, String nextCursor,
        long taskTreeVersion, String projectionWatermark) {}
record TaskBindingInspection(String bindingType, Set<String> allowedActions,
        String factVersion, String recoverableError) {}

ProjectWorkspaceRespVO getWorkspace(Long projectId, TaskWorkbenchActor actor);
TaskQueryResult getTasks(Long projectId, ProjectTaskTreeQueryReqVO request, TaskWorkbenchActor actor);
ProjectTaskDetailRespVO getTask(Long taskId, TaskWorkbenchActor actor);
ProjectTaskWorkbenchRespVO getWorkbench(Long taskId, TaskWorkbenchActor actor);
Optional<TaskBindingHostProvider> providerFor(String bindingType);
TaskBindingInspection inspect(TaskBindingInspectionQuery query);
```

- [ ] **Step 1: 建立只读Controller契约**

路径固定为`/api/v1/pms/projects/{id}/workspace`、`/projects/{id}/tasks`、`/project-tasks/{id}`和`/project-tasks/{id}/workbench`；列表限制`pageSize`，返回`taskTreeVersion/projectionWatermark`。

- [ ] **Step 2: 叠加项目和任务数据范围**

先用`ProjectTreeScopeService`取得VIEW范围，再按当前负责人/祖先占位规则裁剪；空项目或任务范围直接返回空。LOCATE只给无权祖先返回`taskId/parentTaskId/treeDepth`，不返回名称、责任人、描述或绑定正文。

- [ ] **Step 3: 建立WorkBinding注册宿主**

在本Task创建并注册只读`TaskNativeBindingHostProvider`，读取冻结执行契约、当前任务状态、负责人和版本，计算`inspect/allowedActions`，但不执行写动作。未注册、不可用、无权、事实未知返回稳定`recoverableError`和空`allowedActions`；不得返回任意前端路径、脚本或外域正文。由此Task 4可独立验收TASK_NATIVE工作台只读链，Task 7只增加动作命令与完成判定。

- [ ] **Step 4: 补Controller/服务回归并提交**

覆盖五种模式、空范围、深层定位占位、未注册绑定失败关闭、跨租户、权限码与ProjectTreeScope动作一致。

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskQueryServiceTest,ProjectTaskWorkbenchControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 提供项目任务工作区查询`

---

### Task 5: 实现任务创建、基础更新、移动和依赖命令

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskCreateReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskUpdateReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskMoveReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskDependencyReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskCommandService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/command/ProjectTaskCommands.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/command/TaskCommandResult.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectmanual/TaskExecutionContractFactory.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/ProjectTaskWorkbenchController.java`

**Interfaces:**

```java
record TaskCommandResult(Long taskId, int taskVersion, long taskTreeVersion,
        String status, String replayDecision) {}

TaskCommandResult create(CreateTaskCommand command);
TaskCommandResult update(UpdateTaskCommand command);
TaskCommandResult move(MoveTaskCommand command);
TaskCommandResult addDependency(AddDependencyCommand command);
```

- [ ] **Step 1: 实现人工任务原子创建**

校验ACTIVE项目、MANAGE、当前项目经理、阶段、父任务和项目内`taskCode`唯一；同事务插入任务、自反/祖先路径、默认TASK_NATIVE执行契约并递增树版本。任何一步失败不留下孤儿任务或半棵树。

- [ ] **Step 2: 实现基础信息PATCH**

只允许名称、业务层级、计划时间、优先级、排序和描述；明确拒绝状态、父节点、负责人、执行契约和来源字段；以`If-Match`执行任务CAS。

- [ ] **Step 3: 实现移动和依赖命令**

移动校验同租户/项目、MANAGE、当前项目经理、源/目标状态、非自身/后代、审批/里程碑阻断和`expectedTaskTreeVersion`。依赖命令校验四类值域与无环；两者都通过`PlatformCommandExecutionApi`提交幂等和审计。

- [ ] **Step 4: 补命令回归并提交**

覆盖创建回滚、同键重放/异载荷冲突、跨租户/跨项目、越权、终态、移动环、版本冲突、依赖环及无成功副作用。

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskCommandServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 实现任务结构变更命令`

---

### Task 6: 实现任务候选查询、指派和转派

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskAssigneeCandidateReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskAssigneeCandidateRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskAssignReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskAssignmentService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/event/ProjectTaskOutboxDeliveryJob.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/event/TaskAssignedMessage.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/ProjectTaskWorkbenchController.java`
- Modify: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/outbox/dto/PlatformOutboxClaimQuery.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/outbox/query/DueOutboxListQuery.java`
- Modify: `pms-module-platform/src/main/resources/mapper/outbox/PlatformOutboxDeliveryMapper.xml`
- Modify: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImplTest.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectServiceManagerNotificationJob.java`

**Interfaces:**

```java
// GET /api/v1/pms/project-tasks/{id}/assignee-candidates
PageResult<ProjectTaskAssigneeCandidateRespVO> getAssigneeCandidates(Long taskId,
        ProjectTaskAssigneeCandidateReqVO request, TaskWorkbenchActor actor);
TaskCommandResult assign(AssignTaskCommand command);

record PlatformOutboxClaimQuery(LocalDateTime dueAt, int limit,
        Set<String> eventTypes) {}
```

- [ ] **Step 1: 复用SYSTEM公开候选并提供PROJ HTTP路由**

增加`GET /api/v1/pms/project-tasks/{id}/assignee-candidates`，请求使用`ProjectTaskAssigneeCandidateReqVO`分页，响应为`PageResult<ProjectTaskAssigneeCandidateRespVO>`。路由要求`pms:project-task:assign`并在服务端重验项目`MANAGE`；按受信租户、项目公司/部门组织范围和关键词调用`AdminUserApi/DeptApi/CompanyApi`及现有组织候选分页能力，不接受请求自报租户，不直查SYSTEM表。

- [ ] **Step 2: 实现主体权限与候选重验**

当前项目经理可在MANAGE范围指派；服务经理只能在其获权项目范围处理跨区域责任；工程师拒绝。提交时重新校验用户有效、公司/部门归属、同租户和任务版本。

- [ ] **Step 3: 原子维护责任区间和状态**

同一事务关闭旧区间并插入新区间；首次指派把`PENDING_ASSIGN`推进`PENDING_START`，转派保持当前合法状态；通过平台命令事实发布单个`TaskAssigned`。

- [ ] **Step 4: 最小扩展PLATFORM公共Outbox领取并增加TaskAssigned处理入口**

把`PlatformOutboxClaimQuery`和`DueOutboxListQuery`从硬编码单一事件改为调用方显式提交非空、封闭的`eventTypes`集合；PLATFORM当前只接受`ProjectServiceManagerAssigned/TaskAssigned/TaskCompleted`，未知值失败关闭。Mapper XML按集合领取到期PENDING事件，仍按租户、到期时间、ID稳定加锁。同步改造既有`ProjectServiceManagerNotificationJob`只领取`ProjectServiceManagerAssigned`。新增`ProjectTaskOutboxDeliveryJob`只领取`TaskAssigned`，校验冻结payload后发布带`eventId`的`TaskAssignedMessage`到本地应用事件入口；发布异常调用`scheduleRetry`，成功调用`markDelivered`。不新增通知模板、收件人或业务副作用。

- [ ] **Step 5: 补回归并提交**

覆盖候选HTTP分页、assign权限+MANAGE、首次指派、转派、历史保留、同一时点唯一负责人、无效/跨租户/无组织范围/越权候选、重放和并发冲突；覆盖事件类型集合封闭、TaskAssigned领取、发布失败重试、成功CAS交付、已交付eventId不再领取以及服务经理旧事件仍可领取。

Run: `mvn.cmd -pl pms-module-project,pms-module-platform -am '-Dtest=ProjectTaskAssignmentServiceTest,ProjectTaskWorkbenchControllerTest,ProjectTaskOutboxDeliveryJobTest,PlatformOutboxDeliveryApiImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 实现任务责任指派`

---

### Task 7: 实现TASK_NATIVE动作命令、完成判定和状态机管理

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskActionReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/TaskStateMachineSaveReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/TaskStateMachineController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/TaskStateMachineService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/event/TaskCompletedMessage.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/ProjectTaskWorkbenchController.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/event/ProjectTaskOutboxDeliveryJob.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/provider/ProjectTaskGovernanceGuardProvider.java`

**Interfaces:**

```java
TaskCommandResult act(TaskActionCommand command);
TaskStateMachineDefinition getPublished(Long tenantId);
TaskStateMachineDefinition publish(PublishTaskStateMachineCommand command);
```

- [ ] **Step 1: 实现核心动作和值域封闭**

只接受`start/submit/complete/cancel`；按冻结状态机执行`PENDING_START→IN_PROGRESS→PENDING_ACCEPT→DONE`和4个非终态→CLOSED。execute只允许当前负责人，complete/cancel只允许MANAGE且满足规则主体。START以服务端事务时点写`actual_start_time`（已有值不覆盖）；CANCEL写`actual_end_time`，不得由客户端提交实际时间。

- [ ] **Step 2: 实现TASK_NATIVE完成判定**

complete重验任务、执行契约、状态机、子任务、依赖、门禁、契约版本和任务版本；每次命令追加判定。成功时以同一服务端事务时点写`actual_end_time`，并将判定、DONE、进度100、审计和单个`TaskCompleted`同事务提交；失败判定不推进任务。

- [ ] **Step 3: 实现状态机草稿与发布**

只允许`pms:project-task-state:manage`租户管理员；发布版本append-only，核心状态不可删改，未知映射/角色/条件失败关闭，新任务才冻结新版本。

- [ ] **Step 4: 扩展项目任务Outbox处理入口**

将Task 6的`ProjectTaskOutboxDeliveryJob`领取集合扩展为`TaskAssigned/TaskCompleted`，按事件类型反序列化并发布带`eventId`的`TaskCompletedMessage`；未知类型、非法payload和发布异常失败关闭并安排重试，成功才CAS标记DELIVERED。不发送通知、不创建模板或推断收件人。

- [ ] **Step 5: 修正治理守卫当前真值**

`ProjectTaskGovernanceGuardProvider`改为只读取`proj_project_task`并按`DONE/CLOSED`解释终态；删除其对旧`pms_project_task`状态整数的依赖，保持公共守卫契约不变。

- [ ] **Step 6: 补回归并提交**

覆盖主链、START实际开始时间、DONE/CLOSED实际结束时间、取消、未知动作/状态、非负责人、无权完成、旧契约/事实、未满足项、重复提交、发布冻结、TaskCompleted领取/失败重试/eventId交付CAS和治理守卫不漏检。

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskLifecycleServiceTest,TaskStateMachineServiceTest,ProjectTaskOutboxDeliveryJobTest,ProjectTaskGovernanceGuardProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 闭合原生任务状态与完成判定`

---

### Task 8: 生产叶子项目任务进度事实

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskProgressService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/command/UpdateTaskProgressCommand.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ApplicableLeafTaskProgressQuery.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectTaskRuntimeMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectTaskRuntimeMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectprogress/ProjectProgressFactMapper.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskCommandService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/vo/ProjectTaskUpdateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskworkbench/ProjectTaskWorkbenchController.java`

**Interfaces:**

```java
Optional<ProjectProgressFact> recompute(Long tenantId, Long projectId,
        long expectedTaskProgressVersion, LocalDateTime occurredAt);
List<ApplicableLeafTaskProgress> selectApplicableLeaves(ApplicableLeafTaskProgressQuery query);
TaskCommandResult updateProgress(UpdateTaskProgressCommand command);
```

- [ ] **Step 1: 在既有PATCH下增加互斥的进度执行分支**

`PATCH /api/v1/pms/project-tasks/{id}`继续使用`If-Match`。当请求仅含`progress`时路由到`updateProgress`；`progress`与名称、业务层级、计划时间、优先级、排序或描述任一字段混合提交立即拒绝。进度分支只允许`IN_PROGRESS`、当前有效负责人、`pms:project-task:execute`和ProjectTreeScope `EDIT`同时满足，取值为0～99；租户、负责人、状态和任务版本均在写入前回源重验。

- [ ] **Step 2: 实现叶子任务参与集合和权重**

只选择当前适用叶子任务；正数`estimated_hours`按工时权重，缺失/非正数按1，全部缺失时等权。父任务不重复计数；CLOSED保留关闭前进度且不视为100。

- [ ] **Step 3: 原子写任务进度、递增水位并追加事实**

进度PATCH在同一事务内执行任务版本CAS、写0～99、递增`task_progress_version`并追加`fact_source_type=PROJECT_TASK/fact_source_id=projectId`事实和审计。其他适用性或终态变化也走同一重算入口；来源水位冻结`taskTreeVersion/taskProgressVersion/participantCount`，无参与叶子任务不生成0事实。任一步失败时任务进度、水位和事实均不变。

- [ ] **Step 4: 接入F-PROJ-002消费契约**

保持`ProjectProgressSnapshotService`只消费最新合法版本；确认任务事实缺失继续形成PENDING，不用`proj_project.progress`兼容字段替代。

- [ ] **Step 5: 补回归并提交**

覆盖PATCH仅progress成功、混合资料+progress拒绝、非IN_PROGRESS、非当前负责人、缺少execute/EDIT、越界值、旧If-Match及无副作用；覆盖工时加权、部分缺失、全缺失等权、父任务去重、PENDING/IN_PROGRESS/PENDING_ACCEPT/DONE/CLOSED、空事实和并发水位冲突。

Run: `mvn.cmd -pl pms-module-project -am '-Dtest=ProjectTaskProgressServiceTest,ProjectTaskWorkbenchControllerTest,ProjectProgressSnapshotServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: 聚焦测试PASS。提交：`feat(project): 生产项目任务进度事实`

---

### Task 9: 建设响应式任务工作台并退役旧写入口

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/task-workbench/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTaskPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTaskTree.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTaskWorkbenchDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTaskPanel.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-task/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-task/index.ts`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttask/ProjectTaskController.java`

**Interfaces:**

```ts
export const getProjectWorkspace: (projectId: number) => Promise<ProjectWorkspace>
export const getProjectTasks: (projectId: number, params: TaskTreeQuery) => Promise<CursorResult<TaskNode>>
export const getTaskWorkbench: (taskId: number) => Promise<TaskWorkbench>
export const getTaskAssigneeCandidates: (taskId: number, params: CandidatePageQuery) => Promise<PageResult<TaskAssigneeCandidate>>
export const updateTaskProgress: (taskId: number, progress: number, version: number) => Promise<TaskCommandResult>
export const executeTaskAction: (taskId: number, action: TaskAction, command: TaskActionCommand) => Promise<TaskCommandResult>
```

- [ ] **Step 1: 实现六页签中的项目任务页**

在详情页加入“项目任务”页签；Stage一级导航、任务树按需展开、右侧详情/窄屏Drawer。复用Yudao权限组件、Element Plus Tree/Table/Drawer/Descriptions/Form，使用主题变量。

- [ ] **Step 2: 实现服务端真值驱动的操作**

创建、移动、指派、start/submit/complete/cancel只使用服务端`allowedActions`；指派弹窗通过Task 6的`GET /project-tasks/{id}/assignee-candidates`分页加载候选，不复用服务经理专属路由。IN_PROGRESS进度编辑只调用同一PATCH的progress-only分支并传`If-Match`，不与资料字段混合。状态命令传`Idempotency-Key/If-Match`及树版本，不从角色名或按钮可见性推断权限。

- [ ] **Step 3: 退役V1.7写入口**

旧独立“任务WBS”页面改为携带projectId跳转新页，不再调用旧create/update/delete/move API；后端旧Controller移除写路由或稳定返回退役错误，只保留确有消费者的历史只读查询。不得写`pms_project_task`。

- [ ] **Step 4: 补组件验证并提交**

覆盖按需加载、候选分页、进度0～99、进度与资料分离提交、刷新持久化、失败关闭、服务端允许动作、窄屏Drawer和旧入口跳转；定向运行组件测试、类型检查、ESLint、Stylelint和本地构建。

Run: `corepack pnpm vitest run src/views/pms/project/project-master-detail/components/ProjectTaskPanel.spec.ts`

Run: `corepack pnpm ts:check`

Run: `corepack pnpm eslint src/views/pms/project/project-master-detail/components/ProjectTask*.vue src/api/pms/project/task-workbench/index.ts`

Run: `corepack pnpm stylelint 'src/views/pms/project/project-master-detail/components/ProjectTask*.vue'`

Run: `corepack pnpm build:local`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

Expected: 定向组件、类型、样式和构建PASS。提交：`feat(ui): 建设响应式项目任务工作台`

---

### Task 10: 完成真实数据库、规模性能、浏览器和Feature Done证据

**Files:**
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskWorkbenchMySqlTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskTreePerformanceTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskOutboxDeliveryMySqlTest.java`
- Create: `tasks/evidence/f-proj-007-browser-regression.json`
- Modify: `tasks/features/F-PROJ-007.md`

**Interfaces:**
- Consumes: Task 1～9全部提交和锁定Feature Spec。
- Produces: AC-FPROJ007-001～013的自动化、真实MySQL、性能、真实浏览器和独立评审证据。

- [ ] **Step 1: 在全新隔离MySQL执行迁移与业务闭环**

使用当前工作树独立Compose项目从空库执行V1～V89；验证模板/人工创建、闭包、依赖、责任区间、状态机、进度PATCH、完成判定、项目进度事实、唯一约束、事务回滚和旧表零新写入。对`TaskAssigned/TaskCompleted`真实Outbox行模拟首次发布失败、到期重试和成功交付，证明eventId不变、DELIVERED后不再领取且任务/责任/完成判定等业务事实不重复。

- [ ] **Step 2: 执行规模与并发验收**

在200万任务、单树5万节点、直接子节点2000、深度30数据下，对权限过滤后的基础查询记录P95≤2秒；验证更深层级保持正确且按需加载。并发覆盖移动、指派、完成、状态机发布、进度水位CAS和同eventId Outbox领取/交付CAS。

- [ ] **Step 3: 执行真实浏览器闭环**

优先使用内置浏览器；验证创建→候选分页→指派→开始并记录实际开始时间→进度0～99→提交→完成并记录实际结束时间、取消写结束时间、移动、五种查询、刷新持久化、权限负向、未知绑定失败关闭及320/768/1024/1440响应式。记录截图、控制台/网络错误和证据ID。

- [ ] **Step 4: 执行完整验证与独立复审**

Run: `mvn.cmd -pl pms-module-project -am test`

Run: `python scripts/validate_specification_baseline.py`

Run: `git diff --check`

Expected: 后端Reactor、受管快照、前端Task 9命令、真实MySQL、Outbox失败重试/eventId幂等、性能和浏览器证据全部PASS；基础框架目录无变更。按规定格式请求独立Implementation Done裁决。

- [ ] **Step 5: 回写并提交**

独立GO后将`tasks/features/F-PROJ-007.md`回写为Implementation Done PASS，并在规格仓库追溯矩阵/Feature索引回写真实NPDMS提交和证据，再同步新规格基线。提交：`docs(feature): 通过 F-PROJ-007 Implementation Done`

---

## Technical Plan Review Closure

针对裁决`NPDMS-FPROJ007-TECHPLAN-20260825-01`的四项NO-GO整改：

1. Task 1已明确6张新表、4条线性迁移、4条CANCEL迁移，以及默认PUBLISHED版本创建后对全部存量任务执行同租户、无NULL的确定性回填；
2. `TaskNativeBindingHostProvider`的只读inspect/allowedActions创建与注册已前移至Task 4，Task 7不再成为Task 4前置；
3. Task 6增加候选分页HTTP路由及assign+MANAGE守卫，Task 9增加前端API；Task 8增加progress-only PATCH、execute+EDIT+当前负责人守卫及同事务进度事实，Task 7明确实际开始/结束时间；
4. Task 6最小扩展PLATFORM公共Outbox按封闭事件集合领取并建立TaskAssigned处理入口，Task 7加入TaskCompleted，Task 10验证失败重试、eventId交付幂等和不重复业务事实，不新增通知模板或收件人。

## Plan Self-Review

- Spec coverage：Task 1～3覆盖6张新表、任意深度树、依赖、责任和状态机；Task 4覆盖查询及TASK_NATIVE只读Provider；Task 5～8覆盖全部写命令、候选HTTP链、实际时间、进度PATCH、TASK_NATIVE、Outbox投递和进度事实；Task 9～10覆盖旧入口退役、响应式UI及AC-FPROJ007-001～013。
- Scope control：未纳入V2甘特图、资源拉平、PM-09、非原生Owner实现、AI-MIG、Deployment、SIT、UAT或Release。
- Type consistency：所有后端当前任务类型统一复用`ProjectTaskInstanceDO`；新服务统一使用`taskworkbench`包；状态、权限、事件、API路径与Feature物理契约一致。
- Placeholder scan：计划不含待定实现项；真实证据ID只在Task 10执行后由浏览器运行事实生成，不在计划阶段伪造。
