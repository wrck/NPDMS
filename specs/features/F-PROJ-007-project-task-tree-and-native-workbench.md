# F-PROJ-007 项目任务树与原生任务工作台 Feature Spec

> 文档状态：`DRAFT / REVIEW_PENDING`
> Feature Ready：`NOT_READY / 待独立裁决`
> Requirement：`PM-11（V1层级模型与基础查询）`
> 关联Requirement：`PM-02`、`PM-03`、`PM-04`、`PM-08`；不宣称关联Requirement完成
> Owner Context：`PROJ（项目治理）`
> 前置Feature：`F-PROJ-001`～`F-PROJ-006`均已完成
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成；V1.7任务实现只作差距与迁移证据

## 1. 目标

在V1.8统一`ProjectTask`真值上提供不限固定深度的任务树、基础依赖、责任指派、受控状态流转和Stage→ProjectTask工作台。V1首先闭合`TASK_NATIVE`真实执行链；非原生WorkBinding只提供稳定宿主契约和失败关闭注册边界，由后续拥有真实业务对象的Feature前向接入，不创建无生产者适配器。

## 2. Scope

### 2.1 包含

- 以`proj_project_task`为唯一当前任务真值，支持模板实例任务和授权人工任务；
- 任意深度任务创建、直接下级/全部后代/完整上级链/业务层级查询、搜索定位和受控移动；
- 基础任务依赖、当前唯一负责人及责任区间、计划/实际时间、进度和通用详情；
- 核心状态机、租户级扩展状态版本发布和任务创建时版本冻结；
- `TASK_NATIVE`的指派、开始、提交验收、完成和关闭，以及完成判定追加事实；
- 叶子项目的任务进度计算和版本化`ProjectProgressFact`，供F-PROJ-002逐级汇总；
- Stage→ProjectTask导航、六页签项目概览中的“项目任务”页签和响应式任务工作台；
- WorkBinding/PermissionPolicy/CompletionRule宿主注册契约；未注册、无权、不可用或事实版本未知时失败关闭；
- 租户、ProjectTreeScope、功能权限、主体约束、幂等、乐观锁、审计和Outbox事件；
- V1.7 `pms_project_task`运行入口退役和已执行V63完成规则编码的前向修正。

### 2.2 不包含

- V2甘特图、关键路径、资源拉平、批量排程和高级依赖编排；
- PM-09人员批量变更、跨项目自动调度或服务经理自动指派；
- 替后续SOL/IMP/CUT/ACC等Feature建设业务对象、动态表单、审批页面或业务完成规则；
- 无真实生产者的非原生绑定适配器、空`-api`模块或跨域表直查；
- 历史外部`pm_project_task`数据切换、AI-MIG-000、Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FPROJ007-001 当前真值与创建

- `proj_project_task`是V1.8唯一当前任务真值；`pms_project_task`及其Controller/Service/Mapper/UI不得继续写入，也不得与新表双写。
- 项目创建继续从冻结模板版本原子实例化任务和执行契约；人工创建必须属于`ACTIVE`项目、指定`stageCode`和可选直接父任务，默认生成`TASK_NATIVE`执行契约。
- `taskCode`在租户和项目内稳定唯一。结构父关系使用`parent_task_id`；既有`parent_task_code`仅保留模板实例化来源快照，不再作为移动后的关系真值。
- 创建或移动时服务端计算`root_task_id/tree_depth`并更新`proj_task_tree_path`。结构深度不设业务上限，业务层级编码与结构深度分离。
- 任务创建、树投影、当前执行契约、幂等成功、审计和必要Outbox必须在同一事务提交；任一失败不留下无契约任务或半棵树。

### BR-FPROJ007-002 查询与权限裁剪

- 查询支持`DIRECT_CHILDREN/ALL_DESCENDANTS/ANCESTOR_CHAIN/BUSINESS_LEVEL/LOCATE`五种稳定模式；默认只加载直接下级，并使用稳定游标分页。
- 每次查询先按F-PROJ-003 `ProjectTreeScope(VIEW)`获得项目范围，再按任务授权裁剪。空项目范围或空任务范围必须返回空，不能省略条件扩大查询。
- 搜索命中深层节点时返回目标和必要祖先占位；占位只含任务ID、父任务ID和层级，不泄露无权名称、责任人、描述或业务绑定。
- 项目工作区导航只由`ProjectStage + ProjectTask`当前真值投影，不维护第二套任务导航表。六页签项目概览与任务导航相互独立。
- 查询响应返回`taskTreeVersion/projectionWatermark`。缓存只能保存获权摘要；写命令及工作台允许操作必须回源重验。

### BR-FPROJ007-003 移动与基础依赖

- 移动必须提交`If-Match`任务版本、`expectedTaskTreeVersion`、`Idempotency-Key`和非空原因；同键同请求重放首次结果，同键异请求冲突。
- 源任务和目标父任务必须同租户、同项目且均在调用者`MANAGE`范围；目标不得是自身或后代。项目已关闭、任务已完成/关闭、存在进行中审批或已完成里程碑约束时拒绝。
- 移动在一个事务内更新邻接关系、受影响闭包路径、根/深度、项目任务树版本及审计；失败保持原父关系、路径和版本不变。
- `proj_task_dependency`只表达基础前后置依赖，不替代父子关系。V1支持`FINISH_TO_START/START_TO_START/FINISH_TO_FINISH/START_TO_FINISH`稳定类型并拒绝自依赖、跨项目依赖和依赖环；移动不自动增删依赖。

### BR-FPROJ007-004 指派与责任区间

- 同一任务同一时点最多一个有效负责人。首次指派、转派或关闭责任区间必须在`proj_project_task_assignment`追加时态事实，不覆盖历史。
- 项目经理可在本人管理项目内指派；服务经理只能在已获`MANAGE`的项目范围调整跨区域责任；工程师不能指派本人或他人。
- 候选用户必须来自基础平台组织用户公开接口，并校验有效用户、公司/部门组织范围和同租户。不得由请求自报租户，也不得直查SYSTEM用户、公司或部门表。
- 指派成功将`PENDING_ASSIGN`推进至`PENDING_START`并发布一条`TaskAssigned`；转派保持合法当前状态，不伪造开始或完成事实。

### BR-FPROJ007-005 状态机与完成判定

- 核心标准状态固定为`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT/DONE/CLOSED`，不得删除、复用或改义。
- 核心迁移为：指派`PENDING_ASSIGN→PENDING_START`，开始`PENDING_START→IN_PROGRESS`，提交`IN_PROGRESS→PENDING_ACCEPT`，完成`PENDING_ACCEPT→DONE`，授权关闭从未终态进入`CLOSED`。未知状态、未知动作或非法迁移均失败关闭。
- 授权管理员可维护租户级扩展中间状态；发布版本必须给出标准状态映射、允许迁移、适用角色和进入/退出条件。已发布版本不可覆盖，新任务冻结当前发布版本，存量任务不自动换版。
- 每个可执行任务必须恰有一个当前`ExecutionContract`，原子冻结WorkBinding、PermissionPolicy、CompletionRule、可选GateRef和状态机版本。
- `TASK_NATIVE`完成只校验任务自身必填事实、子任务/依赖/门禁和当前状态。当前V1.8状态真值为`DONE`；已执行V63中`requiredStatus=COMPLETED`必须用新Flyway前向修正为`DONE`，不得修改V63。
- 每次完成命令都追加`TaskCompletionEvaluation`，冻结任务版本、契约版本、规则版本、事实版本、结果和未满足项；成功判定、状态迁移、审计和`TaskCompleted`同事务提交。

### BR-FPROJ007-006 WorkBinding宿主边界

- WorkBinding类型保持`TASK_NATIVE/BUSINESS_OBJECT/BUSINESS_COMPONENT/DYNAMIC_FORM/APPROVAL/COMPOSITE`稳定值域。`TASK_NATIVE`不得携带外部目标；其他类型必须引用受信任注册项和Owner稳定对象。
- 本Feature实现工作台宿主、注册表、允许操作合并和失败关闭，不为没有真实生产者的类型创建假数据或占位成功Provider。
- 非原生绑定只有在Owner Feature提供正式公开契约、真实生产路径和验收证据后才可注册启用。PROJ不得依赖其`-biz`、Service、Mapper、Repository或业务表。
- 工作台响应仅返回通用任务摘要、受信任组件/对象引用、服务端计算的允许操作及事实版本，不返回任意脚本、前端路径或外域业务正文。
- 非原生绑定的完成必须回源Owner事实；目标不存在、无权、不可用、版本失效或状态未知时任务保持原状态，不能退化为TASK_NATIVE完成。

### BR-FPROJ007-007 权限矩阵

| 能力 | 功能权限码 | ProjectTreeScope动作 | 附加主体约束 |
|---|---|---|---|
| 工作区、任务树、详情 | `pms:project-task:query` | `VIEW` | 工程师仅可查看获权任务链；祖先占位不含业务正文 |
| 创建及基础信息维护 | `pms:project-task:create` / `pms:project-task:update` | `MANAGE` | 当前项目经理；状态、父节点、负责人不走普通PATCH |
| 移动与依赖维护 | `pms:project-task:move` | `MANAGE` | 当前项目经理；源和目标同项目 |
| 指派/转派 | `pms:project-task:assign` | `MANAGE` | 当前项目经理；获权服务经理仅调整跨区域责任 |
| 本人任务执行 | `pms:project-task:execute` | `EDIT` | 当前有效负责人；只执行允许的start/submit和通用执行字段 |
| 验收完成/关闭 | `pms:project-task:complete` | `MANAGE` | 当前项目经理或规则明确的验收主体 |
| 状态机配置发布 | `pms:project-task-state:manage` | 平台租户范围 | 授权管理员；不因此获得项目任务数据权限 |

- 前后端使用同一权限码；后端按租户、功能权限、ProjectTreeScope、任务主体、状态和版本依次重验。角色名称和按钮可见性均不是授权真值。
- 旧`pms:project-task:delete`不进入V1.8当前能力；任务以受控`CLOSED`保留追溯，不物理删除或软删除有效业务事实。

### BR-FPROJ007-008 幂等、事件与审计

- 所有状态改变、创建、移动、依赖和指派命令必须使用平台幂等能力；同键异载荷拒绝，进行中重复返回冲突，不能重复产生责任区间、判定、审计或事件。
- `TaskAssigned`冻结任务、项目、负责人、责任区间、任务版本和发生时间；`TaskCompleted`冻结任务、项目、完成判定ID、契约/事实版本和发生时间。
- 事件通过平台Outbox提交，通知失败不回滚任务事实，消费者按eventId幂等。
- 创建、移动、依赖、指派、状态变化、路径前后值、状态机/契约版本和失败原因写`plt_operation_audit`；失败审计不得产生成功业务副作用。

### BR-FPROJ007-009 任务与叶子项目进度

- `TASK_NATIVE`任务在待分配/待开始时进度为0；当前有效负责人可在进行中维护0～99；提交待验收后冻结为99；只有完成判定成功进入`DONE`时置100。`CLOSED`保留关闭前进度，不冒充完成。
- 父任务进度只从其适用叶子任务派生，不允许再把父任务自身进度重复计入。叶子项目进度同样只聚合该项目当前适用叶子任务。
- 有正数`estimated_hours`的适用叶子任务按冻结预估工时加权；若全部适用叶子任务均未配置正数工时，则等权。部分任务缺少正数工时时按等权单位1参与，避免静默从分母消失。
- 没有任何适用叶子任务时不生成0进度事实，而是保持“进度事实缺失”；F-PROJ-002据此形成PENDING，不用兼容`proj_project.progress`替代。
- 每次任务进度、适用性或终态事实变化后，在同一事务增加`proj_project.task_progress_version`并追加一条`proj_project_progress_fact(fact_source_type=PROJECT_TASK, fact_source_id=projectId)`；来源水位冻结任务树版本、进度版本和参与任务数。旧事实不覆盖，F-PROJ-002只消费最新合法版本。

## 4. API契约

所有路径继承`/api/v1/pms`前缀；错误继续使用平台统一`CommonResult`和稳定业务错误码。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/projects/{id}/workspace` | `GET` | 返回六页签摘要、Stage→ProjectTask导航、`taskTreeVersion/projectionWatermark`；按VIEW裁剪 |
| `/projects/{id}/tasks` | `POST` | Header必填`Idempotency-Key`；创建TASK_NATIVE任务，输入`taskCode/name/stageCode/parentTaskId/businessLevelCode/plan/description`，服务端生成版本和执行契约 |
| `/projects/{id}/tasks` | `GET` | 参数`mode/parentTaskId/taskId/businessLevelCode/keyword/cursor/pageSize`；默认DIRECT_CHILDREN；稳定排序和游标 |
| `/project-tasks/{id}` | `GET`, `PATCH` | 查询/修改通用基础信息；PATCH必填`If-Match`且不能修改状态、父节点、负责人、执行契约或来源 |
| `/project-tasks/{id}/workbench` | `GET` | 返回通用详情、执行契约摘要、绑定类型、允许操作、事实版本与可恢复错误；不返回任意脚本或外域正文 |
| `/project-tasks/{id}/actions/move` | `POST` | Header必填`Idempotency-Key/If-Match`；输入`targetParentTaskId/expectedTaskTreeVersion/reason` |
| `/project-tasks/{id}/actions/assign` | `POST` | Header必填`Idempotency-Key/If-Match`；输入`assigneeUserId/reason`，服务端校验候选及主体 |
| `/project-tasks/{id}/dependencies` | `POST`, `GET` | 创建/查询基础依赖；写入需幂等、任务版本和无环校验 |
| `/project-tasks/{id}/actions/{start|submit|complete|cancel}` | `POST` | Header必填`Idempotency-Key/If-Match`；complete另含`executionContractId/contractVersion/factObjectKey/factVersion`，服务端重验并追加判定 |
| `/project-task-state-machines` | `GET`, `POST` | 查询当前发布版本或创建草稿；仅状态机配置权限，不授予项目数据权限 |
| `/project-task-state-machines/{id}/actions/publish` | `POST` | Header必填`Idempotency-Key/If-Match`；完整校验标准映射、迁移、角色及门禁后发布新版本 |

列表和树查询必须限制`pageSize`并使用稳定`sortOrder,id`游标；不得一次返回整棵5万节点任务树。普通PATCH、前端组件显示或HTTP成功均不得改变状态。

## 5. 数据与物理边界

机器契约：`specs/features/F-PROJ-007-physical-contract.json`。

- 复用并前向扩展`proj_project_task`、`proj_project_template_task_definition`和`proj_project_task_execution_contract`；新增`proj_task_tree_path`、`proj_task_dependency`、`proj_project_task_assignment`、`proj_project_task_completion_evaluation`及任务状态机版本表。
- `proj_project.task_tree_version/task_progress_version`分别是项目内任务树和任务进度当前水位，独立于项目父子树`treeVersion`和Project聚合版本；对应事务成功时单调递增。
- 当前责任以未结束`proj_project_task_assignment`区间为真值，不在任务行和关系表双写两个当前负责人。
- `pms_project_task/pms_project_task_dependency`冻结为历史迁移输入，不再是API或UI真值；没有稳定项目映射的记录不得自动并入。历史外部数据切换仍服从AI-MIG-000。
- 所有DDL及数据修正使用新Flyway版本，不修改V8、V57或V63；新增查询遵守场景Query、XML动态集合和空范围返回空结果规则。

## 6. UI

- 项目详情保留基本信息、项目树、团队成员、项目任务、设备清单、实施范围六页签；项目任务页复用Yudao/Element Plus Tree、Table、Drawer、Descriptions、Form和权限组件。
- Stage为一级导航，任务树按需展开；右侧工作台保留通用信息并按受信任绑定结果装载执行区。TASK_NATIVE直接显示通用执行表单。
- 320/768/1024/1440宽度无页面级横向溢出；窄屏树与详情使用抽屉/分段切换，样式使用Element Plus主题变量并减少内联样式。
- 旧V1.7独立“任务WBS”页面退役，不保留第二套写入口；如需兼容入口，只允许跳转到新项目任务页且不保留旧API调用。

## 7. 验收标准

- `AC-FPROJ007-001`：模板实例和人工创建任务均写`proj_project_task`且恰有一个当前执行契约；旧表无新写入，失败不产生孤儿任务或半完成树。
- `AC-FPROJ007-002`：直接下级、全部后代、祖先链、业务层级和搜索定位在任意深度正确；权限裁剪不泄露正文，空范围返回空。
- `AC-FPROJ007-003`：合法移动原子更新邻接、闭包、根/深度和树版本；循环、跨租户、跨项目、越权、终态、审批/里程碑阻断和版本冲突均无成功副作用。
- `AC-FPROJ007-004`：基础依赖拒绝自依赖、跨项目和依赖环；移动不隐式改变依赖。
- `AC-FPROJ007-005`：任务同一时点最多一个负责人，指派/转派保留责任区间；跨租户、无效用户、无组织范围或越权候选被拒绝。
- `AC-FPROJ007-006`：核心状态和扩展状态发布/冻结符合规则；`TASK_NATIVE`按`DONE`完成，V63的`COMPLETED`不一致以前向迁移闭环。
- `AC-FPROJ007-007`：完成命令按任务、契约、规则和事实版本追加判定；未满足、旧版本、重复异载荷或未知绑定均不推进任务。
- `AC-FPROJ007-008`：工作区只使用Stage→ProjectTask投影；未注册非原生绑定明确失败关闭，不伪造业务页面或完成事实。
- `AC-FPROJ007-009`：TaskAssigned/TaskCompleted、审计和幂等与任务事务一致；通知重试不重复业务事实。
- `AC-FPROJ007-010`：叶子任务按冻结工时/等权规则计算任务及叶子项目进度并追加版本化ProjectProgressFact；父任务不重复计数、CLOSED不冒充100、无适用任务保持事实缺失。
- `AC-FPROJ007-011`：200万任务数据规模、单任务树5万节点、直接子节点2000、测试深度30下，权限过滤后的基础查询P95≤2秒；超过深度30仍保持关系和权限正确并按需加载。
- `AC-FPROJ007-012`：真实MySQL验证迁移、唯一约束、移动回滚、责任区间、完成判定、进度事实和无副作用；真实浏览器验证创建→指派→开始→提交→完成、移动、查询、刷新持久化、权限负向和四档响应式。
- `AC-FPROJ007-013`：不宣称非原生Owner Feature、PM-09、V2甘特图/高级编排、AI-MIG、Deployment、SIT、UAT或Release完成。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，不把失败测试作为实现前置；每个Task完成后按风险补齐自动化回归。完成证据至少包括API/服务自动化、任务树与状态机契约、真实MySQL、并发/幂等/权限负向、任务树规模性能、事件重试、真实浏览器响应式闭环和独立代码评审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PM-11 V1与V2边界 | PASS（候选） |
| V1.7双模型与旧入口处置 | PASS（候选） |
| 任务树、依赖、责任区间与状态机 | PASS（候选） |
| 任务与叶子项目进度口径 | PENDING（待独立裁决） |
| TASK_NATIVE与非原生宿主边界 | PASS（候选） |
| API、权限、并发、事件和UI验收 | PASS（候选） |
| 独立Feature Ready裁决 | PENDING |

结论：`DRAFT / REVIEW_PENDING`。独立Feature Ready裁决GO前不得标记READY、不得生成Technical Plan或实施代码；不重开已通过的PRD/SDS门禁，不得根据V1.7现有实现直接勾选任何AC。
