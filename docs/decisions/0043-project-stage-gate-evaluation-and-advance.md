# ADR-0043：项目阶段准出门禁评估与相邻推进

> 状态：`ACCEPTED`（修订011影响补充：`PROPOSED_FOR_INDEPENDENT_REVIEW`）<br>
> 日期：2026-08-31<br>
> Requirement：`PM-03@V1`<br>
> 候选 Feature：`F-PROJ-008 项目阶段准出门禁与正向推进`

## 1. 决策范围

本决策只覆盖活动项目的通用相邻阶段推进 `S0→S1、S1→S2、S2→S3、S3→S4`。`S4→S5`继续使用 F-COM-001 已批准的专用 `enter-acceptance-stage` 命令及验收范围绑定事务；`S5→S6`由 CLO、阶段回退/异常关闭/重开由 PM-10 分别拥有，均不进入本 Feature。

## 2. 权威输入与评估结果

1. 仅项目创建时从已发布模板冻结到项目实例的 `ProjectStage/ProjectGate/ProjectGateReference/ProjectTask/ProjectMilestone/ProjectDeliverable/ExecutionContract` 事实参与评估；模板当前版本、名称相似对象、旧 `pms_project_phase` 和客户端门禁结论均不是运行时真值。
2. Gate Reference 稳定类型为 `TASK/DELIVERABLE/MILESTONE/APPROVAL/PROCESS/STATE`。模板发布必须拒绝未知类型、重复引用、缺失稳定对象键、没有已登记 Owner Provider 的引用、S0～S3任一阶段缺少EXIT Gate或任一EXIT Gate没有引用；APPROVAL/PROCESS只保存BPM `processDefinitionKey`，新写`refVersion`必须为空。既有`refVersion`仅保留历史值，不参与发布、启动、节点解析或门禁判断。已发布模板修订保持不可变。运行时若实例数据损坏并出现同类空集合，稳定返回`DEPENDENCY_UNAVAILABLE / EXIT_GATE_MISSING|EXIT_GATE_REFERENCE_MISSING`，不得以空集真值放行。TASK/MILESTONE/DELIVERABLE等实例事实版本在命令锁内从精确实例取得，不能要求模板提前猜测运行时行版本。
3. PROJ直接评估本 Context 的TASK、MILESTONE、STATE；DELIVERABLE由ACC Owner公共事实接口提供；APPROVAL/PROCESS由BPM Owner的类型化Provider提供。PMS流程Owner接口按冻结的`processDefinitionKey`提供窄历史定义选择查询，并以同一key和可空的`processDefinitionId`启动：未指定定义ID时由BPM按key选取最新生效定义，授权发起人显式指定时必须从该项目Gate的查询结果选择，启动时仍重新校验该ID属于同一key且可启动。查询与启动接口位于`pms-module-project-api`，真实Provider位于`pms-module-integration`，不得修改Yudao基础源码，也不得建立PMS流程版本接口或第二版本真值。六类引用的稳定键、Owner和唯一满足谓词统一冻结在10分册；PROJ不得读取其他Context业务表，Provider不得返回或复制外域业务正文。
4. 每个引用只产生 `SATISFIED/UNSATISFIED/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`之一；同一 Gate 全部引用满足才通过，同阶段全部 EXIT Gate 通过才允许推进。已知业务未满足返回稳定未满足引用；Owner未知、不可用、重复或事实不可判定必须失败关闭，且不得伪装成业务未满足。PROCESS/APPROVAL没有精确关联实例时是`*_NOT_STARTED`，运行中、驳回或撤回均不满足，只有关联BPM实例批准完成才满足；门禁事实记录该实例实际`processDefinitionId`，完整`taskDefinitionKey`原样留痕且不得解析。
5. APPROVAL/PROCESS是PMS专用项目阶段Gate流程，其历史定义查询和启动均使用`pms:project:update + ProjectScope ACTION_MANAGE + 当前PROJECT_MANAGER`，由PROJ在调用流程Owner接口前按当前事实重验；不要求或授予`bpm:process-definition:query`全局权限。历史定义查询只按当前Gate Reference冻结key返回同租户、可启动定义的`processDefinitionId/processDefinitionKey/name/selectable`，不得暴露其他key或BPM管理正文。Yudao `startUserIds/startDeptIds`继续只约束Yudao通用流程发起入口，PMS专用Gate路径不查询或复制该事实；专用定义仍禁止BPMN UserTask使用`START_USER_SELECT(35)`，模板发布按key检查当前生效定义，查询和启动按各自实际definitionId检查。此规则不替代项目服务端授权，也不改变非Gate流程。

## 3. 命令与原子结果

`StageAdvanceCommand`固定携带 `projectId、expectedCurrentStage、expectedProjectVersion、expectedTreeVersion、Idempotency-Key`；actor与tenant只取服务端认证上下文，目标阶段由服务端从冻结阶段顺序推导，客户端不得指定下一阶段或门禁结论。

成功事务必须共同完成：当前阶段 `DONE`、相邻下一阶段 `ACTIVE`、Project `current_stage`与version CAS、EXIT Gate结果、不可变 `ProjectStageSnapshot(operationType=STAGE_ADVANCE)`、操作审计、`ProjectStageChanged` Outbox和幂等完成事实。任一门禁、版本、Owner或写入失败时以上成功事实均不得部分提交。

项目当前行先锁；随后按阶段排序锁当前/下一阶段、按 gateId 锁 EXIT Gate、按 `(gateId, refType, refCode, id)`锁引用，再按稳定对象键调用本地评估器或 `MANDATORY` Owner Provider。取得外域Owner锁后不得回到更早顺序补锁PROJ对象。

## 4. 授权

推进必须同时满足现有功能权限 `pms:project:update`、`ProjectScopeApi.ACTION_MANAGE`（`PROJECT_MANAGE`）对目标项目的当前范围事实、当前有效 `PROJECT_MANAGER`主体关系。角色—权限映射保持正式配置；本决策不新增权限键，也不以全局角色、前端入口或历史成员关系替代服务端校验。

## 5. 物理与复用裁决

- `DIRECT_REUSE`：`proj_project`、`proj_project_stage`、`proj_project_gate`、`proj_project_gate_reference`、`proj_project_task`、`proj_project_milestone`、`acc_project_deliverable`、当前执行契约、`proj_project_stage_snapshot`及既有 Outbox/审计载体。
- `COPY_THEN_ENHANCE`：既有阶段快照/事件应用模式可复制增强为 `STAGE_ADVANCE`，但不得改变已批准的 PM-10 与 S4→S5 专用路径。
- `DO_NOT_REUSE_RUNTIME / PRESERVE_EXISTING`：旧 `pms_project_phase`、旧阶段服务及旧任务/交付件平行真值。

P3-E09结论为 `NO_PHYSICAL_DELTA`：现有引用类型列可承载新增受控枚举，现有阶段快照已具备 before/after stage、门禁快照、Owner事实、treeVersion、operationId、actor和唯一键；APPROVAL/PROCESS由PMS集成Provider按冻结key和本次可空显式definitionId启动BPM实例，以固定businessKey、冻结变量和运行/历史事实形成权威关联，并以实例实际`processDefinitionId`作为定义身份，不新增PMS流程映射表或版本字段。既有流程版本列仅保留历史值，新写入为空且不参与运行判断。实现若不能从这些既有事实唯一解析精确关联，必须失败关闭并回到SDS复审，不得静默增加物理事实、推断或任选实例。

## 6. 后续 Gate

本 ADR 通过后仅允许形成 F-PROJ-008 Feature Spec；本决策不批准 Feature Ready、Technical Plan、产品代码、Flyway、Implementation Done、SIT、UAT或Release。
