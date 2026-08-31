# ADR-0043：项目阶段准出门禁评估与相邻推进

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 日期：2026-08-31<br>
> Requirement：`PM-03@V1`<br>
> 候选 Feature：`F-PROJ-008 项目阶段准出门禁与正向推进`

## 1. 决策范围

本决策只覆盖活动项目的通用相邻阶段推进 `S0→S1、S1→S2、S2→S3、S3→S4`。`S4→S5`继续使用 F-COM-001 已批准的专用 `enter-acceptance-stage` 命令及验收范围绑定事务；`S5→S6`由 CLO、阶段回退/异常关闭/重开由 PM-10 分别拥有，均不进入本 Feature。

## 2. 权威输入与评估结果

1. 仅项目创建时从已发布模板冻结到项目实例的 `ProjectStage/ProjectGate/ProjectGateReference/ProjectTask/ProjectMilestone/ProjectDeliverable/ExecutionContract` 事实参与评估；模板当前版本、名称相似对象、旧 `pms_project_phase` 和客户端门禁结论均不是运行时真值。
2. Gate Reference 稳定类型为 `TASK/DELIVERABLE/MILESTONE/APPROVAL/PROCESS/STATE`。模板发布必须拒绝未知类型、重复引用、缺失稳定对象键、缺失该refType契约要求的`refVersion`或没有已登记 Owner Provider 的引用；已发布模板修订保持不可变。TASK/MILESTONE/DELIVERABLE等实例事实版本在命令锁内从精确实例取得，不能要求模板提前猜测运行时行版本。
3. PROJ直接评估本 Context 的 TASK、MILESTONE、STATE；DELIVERABLE由 ACC Owner 公共事实接口提供；APPROVAL/PROCESS由相应业务 Owner 的稳定事实 Provider 提供。PROJ不得读取其他 Context 业务表，Provider不得返回或复制外域业务正文。
4. 每个引用只产生 `SATISFIED/UNSATISFIED/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`之一；同一 Gate 全部引用满足才通过，同阶段全部 EXIT Gate 通过才允许推进。已知业务未满足返回稳定未满足引用；Owner未知、不可用或事实不可判定必须失败关闭，且不得伪装成业务未满足。

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

P3-E09结论为 `NO_PHYSICAL_DELTA`：现有引用类型列可承载新增受控枚举，现有阶段快照已具备 before/after stage、门禁快照、Owner事实、treeVersion、operationId、actor和唯一键。实现不得因此新增第二门禁结果表、阶段历史表或迁移脚本。

## 6. 后续 Gate

本 ADR 通过后仅允许形成 F-PROJ-008 Feature Spec；本决策不批准 Feature Ready、Technical Plan、产品代码、Flyway、Implementation Done、SIT、UAT或Release。
