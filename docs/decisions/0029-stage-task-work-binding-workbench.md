# ADR-0029：Stage—ProjectTask工作台与业务绑定

## 状态

`ACCEPTED`

## 日期

2026-08-20

## 需求依据

- PRD V1.8：`PM-03`、`PM-11`、`CUT-01`、`CUT-03`、`INT-12`。
- 已确认界面设计：项目一级导航使用Stage，二级业务导航使用ProjectTask；任务执行直接操作真实业务实体、组件、动态表单或审批，不维护第二套业务导航。
- 既有业务约束：项目和任务均不限制固定层级；割接保持P1～P6，CUT-03与设备采集协作不得演变为独立采集阶段或通用工单。

## 决策

1. 项目模板版本的执行编排结构统一为`StageDefinition → TaskDefinition`。每个可执行`TaskDefinition`包含必填`WorkBinding`、`PermissionPolicy`、`CompletionRule`和可选`GateRef`。
2. 项目创建时把定义冻结并实例化为项目阶段与ProjectTask。项目工作区导航从实例投影生成，不新增`NavigationDefinition`或其他与任务树重复的配置模型。
3. ProjectTask是执行编排节点，也通过默认`TASK_NATIVE`绑定承载通用任务详情基础功能。`WorkBinding`统一支持`TASK_NATIVE`、`BUSINESS_OBJECT`、`BUSINESS_COMPONENT`、`DYNAMIC_FORM`、`APPROVAL`和`COMPOSITE`；每个ProjectTask必须且只能有一个当前有效绑定。`TASK_NATIVE`以ProjectTask自身为业务实体，其他类型在同一任务详情工作台加载目标业务执行区。
4. Stage→ProjectTask是界面信息架构，不是业务树深度。二级任务区域可以按需展开任意深度ProjectTask树；项目概览作为独立入口，固定展示基本信息、项目树、团队成员、项目任务、设备清单、实施范围。
5. ProjectTask完成统一由`CompletionRule`判定：`TASK_NATIVE`校验任务自身事实和合法状态，其他类型校验目标业务事实、审批结果、表单提交版本、子任务或门禁快照。非`TASK_NATIVE`任务不得用通用完成动作绕过目标事实；服务端完成命令必须重新校验任务、绑定、规则及适用的事实版本。
6. 割接仍以CUT-01承载P1～P6。P1是任务接入入口，任务详情工作台显示P2～P6五个处理步骤；CUT-03在同一P3工作台完成规则匹配、控件填写、CollectionTask下发和结果回填，不新增采集阶段。

## 权限与边界

- `PermissionPolicy`是策略引用，不授予权限。每次读取或操作都由服务端结合用户、租户、项目树、ProjectTask、绑定目标和目标对象状态重新计算。
- 业务组件注册表只登记受信任组件键和支持的绑定类型，不允许模板配置任意脚本、前端路径或跨Context Repository。
- DAC拥有凭证、授权、CollectionTask下发和回调；CUT拥有采集项匹配、结果解释和提交门禁。技术回调成功不等于采集项业务通过。

## 影响

- PRD和13领域规格补充PM-03、PM-11、CUT-01、CUT-03语义，但正式需求数量、优先级、S0～S6和P1～P6编号不变。
- Phase 1/2 SDS同步领域、状态、流程、权限、逻辑数据、API、事件、并发与测试契约。
- 本ADR不直接批准表结构变化。`WorkBinding`、`CompletionRule`及CUT-03结果绑定的物理承载必须在Phase 2差量设计中明确，并重新执行P3-E09模型一致性校验后才能进入DDL。

## 明确排除

- 保留`TASK_NATIVE`通用任务详情基础能力，但不建设第二套项目业务导航配置，也不以通用详情替代其他绑定类型的业务执行。
- 不把两级导航解释为任务只能有两层。
- 不新增割接采集阶段、采集工单、逐步骤执行状态机或稳定观察。
- 不因工作台直接渲染而绕过业务Owner API、状态机、数据权限或秘密保护。
