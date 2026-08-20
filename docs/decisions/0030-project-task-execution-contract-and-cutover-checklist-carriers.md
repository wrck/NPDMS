# ADR-0030：ProjectTask执行契约与CUT-03清单物理承载

## 状态

`ACCEPTED`

## 日期

2026-08-20

## 需求依据

- PRD V1.8：`PM-03`、`PM-11`、`CUT-03`、`INT-12`。
- ADR-0029：每个ProjectTask必须且只能有一个当前WorkBinding；默认`TASK_NATIVE`承载通用任务详情，其他类型按绑定关系操作真实业务实体；完成必须依据分类型CompletionRule和Owner事实。
- CUT-03：P3在同一工作台生成版本化采集清单，允许直接填写、自动采集、外部加载和人工降级；DAC拥有技术任务与技术状态，CUT只解释和选择结果。

## 问题

ADR-0029已经确定逻辑模型，但原Phase 2未定义以下物理承载，无法直接进入表级设计和Feature实现：

1. 模板任务定义如何冻结WorkBinding、PermissionPolicy、CompletionRule和GateRef；
2. ProjectTask如何保留唯一当前执行契约及版本历史；
3. 完成命令如何保存本次使用的任务、契约、Owner事实与判定版本；
4. CUT-03如何保存清单版本、采集项定义快照和追加式结果，同时不复制DAC技术状态；
5. 既有ProjectTask和`pms_cut_risk`如何前向对齐而不制造历史事实。

## 决策

### 1. ProjectTask执行契约

- 新增`proj_project_template_task_definition`，保存模板revision下的Stage/TaskDefinition、WorkBinding、PermissionPolicy、CompletionRule、可选GateRef和定义版本。模板revision发布后不可原位更新。
- 新增`proj_project_task_execution_contract`，以`project_task_id + contract_version`保存任务实例采用的完整执行契约；有效期关闭与新版本写入原子执行，数据库约束至多一个当前版本，应用服务保证任何可执行任务恰好一个当前版本。
- 新增`proj_project_task_completion_evaluation`，追加保存完成命令使用的任务版本、执行契约版本、Owner事实版本、判定结果、未满足项、门禁快照和幂等键；成功判定与ProjectTask状态迁移同事务提交。
- `TASK_NATIVE`的目标Context、对象、组件、动态表单和审批引用必须为空；其他类型的必填引用由类型约束与服务端注册表联合验证。

### 2. CUT-03清单

- 新增`cut_cutover_checklist`，保存CutoverTask下的P3输入快照、规则/配置revision、匹配轨迹、配置缺口、提交及失效事实。
- 新增`cut_cutover_checklist_item`，保存版本内稳定项键、采集项定义版本、界面与条件快照、工作方式、必填性、设备/命令模板引用、自定义来源和适用性。
- 新增`cut_cutover_checklist_item_result`，追加保存直接填写、自动采集、外部加载或人工降级结果，并以`selection_started_at/selection_ended_at`表达受控选择区间；结果正文不可覆盖，只有切换命令可以关闭旧选择区间；只引用CollectionTask和结果版本，不保存DAC的技术状态副本。
- 草稿重新匹配以`checklist_version + input_snapshot_hash`控制并发；已提交清单不可原位修改，需创建新版本。D级割接不创建清单。

### 3. 前向初始化与迁移边界

- 既有ProjectTask在前向迁移中初始化为显式`TASK_NATIVE`执行契约版本1；不得根据任务名称、菜单、模块、URL或历史完成状态推断其他业务绑定、规则或判定快照。
- `TaskCompletionEvaluation`只从新平台完成命令开始追加，不为历史已完成任务伪造判定事实。
- 当前`pms_cut_risk`仅进入字段级评审：可证明的任务引用、原编码/名称/类型、说明和填写事实可候选映射；采集项版本、界面Schema、绑定规则、必填性、CollectionTask、自动结果、业务通过和配置缺口均不得推断。

## 并发、权限与异常

- 完成命令必须提交`taskVersion`、`executionContractId/contractVersion`、适用的`factObjectKey/factVersion`和`Idempotency-Key`；服务端锁定任务并回读当前执行契约与Owner事实后判定。
- 工作台展示不授予权限；服务端按项目树、ProjectTask、绑定类型、目标对象和状态重新授权。任何获权反查索引都不能成为跨Context Repository访问入口。
- CUT自动采集失败后允许授权人员在同一事务关闭旧选择区间并追加人工降级结果，但必须保留自动失败事实和人工证据；唯一当前选择冲突时整体回滚，不得把原CollectionTask改写为成功。
- 版本冲突、绑定缺失、目标无权或事实不满足时不推进任务/清单状态，不以通知送达、HTTP成功或组件加载成功代替业务完成。

## 影响与门禁

- Phase 2逻辑和物理设计、API、事件、并发、异常、追溯及领域迁移契约必须同步以上六张表和四类领域对象。
- 本ADR批准物理模型及目标DDL承载，不直接创建或修改实现仓库Flyway，不授权历史迁移或数据切换。
- Phase 3已将六张表纳入目标DDL和P3-E09逐项寄存器；正式独立复审已GO、模型基线已发布为`MODEL_BASELINE_READY`。只有对应Release包含历史迁移或数据切换时，`AI-MIG-000`才在Release批准窗口内成为前置门禁。

## Phase 3落位事实

- 当前目标DDL SHA-256：`6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`。
- ADR-0030六表差量精确覆盖196个DDL item，排序itemId集合SHA-256为`2EEE779BD667B1B1BDD1C11B0A4548A6E28E2BA706366C205AAD73F90C220629`。
- 当前DDL共66表、1,382列；隔离MySQL 8.4.10执行通过。逐项寄存器共2,079项、`DEFER=0`。
- 上述结果形成已复审的SDS数据模型事实；P3-E09为`MODEL_BASELINE_READY`，不生成迁移批准哈希，不执行AI-MIG、历史回填或数据切换。

## 明确排除

- 不新增第二套通用任务业务正文、通用工单或可绕过Owner事实的完成入口。
- 不固定项目或任务层级。
- 不新增割接采集阶段、逐步骤执行状态机、稳定观察、结果中转页或DAC技术状态副本。
- 不把目标DDL中的表视为已执行实现仓库Flyway或生产数据库迁移。
