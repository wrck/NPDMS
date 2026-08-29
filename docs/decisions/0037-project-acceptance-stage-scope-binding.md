# ADR-0037：项目验收阶段驱动范围绑定

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 日期：2026-08-29<br>
> Requirement：`COM-01@V1`、`ACC-03@V1`<br>
> 前置批准：`CHG-PRD-2026-08-29-009`

## 背景

PRD修订009确认项目进入其设定的验收阶段时，全部当前有效DeliveryScope分配版本同步进入验收范围；项目已处验收阶段时，新范围版本生效也须同步绑定。项目阶段进入是先手事实，进入时不要求创建或补齐初验/终验报告；报告只在对应验收活动申请完成时要求完备。原SDS把绑定定义为“验收单绑定”并强制`acceptance_id`，与该语义冲突。

## 候选决策

1. `AcceptanceScopeBinding`是ACC拥有的项目验收阶段范围事实，独立于初验/终验`Acceptance`报告；以不可变`ProjectStageSnapshot`证明项目阶段来源，不保存`acceptance_id`。
2. PROJ进入验收阶段时持有项目当前行锁，同步调用ACC；ACC经COM公开接口按稳定ID锁定项目全部当前有效范围，追加`PROJECT_STAGE_ENTRY`绑定后PROJ才完成阶段进入。
3. COM使验收阶段内新范围版本生效时，先经PROJ公开接口锁定并读取当前验收阶段快照，再同步调用ACC追加`SCOPE_VERSION_EFFECTIVE`绑定。
4. 统一锁顺序为PROJ项目当前行→COM订单行（适用时）→COM范围当前行→ACC绑定；全部Provider使用同一MySQL事务资源和`MANDATORY`传播，任一失败整体回滚。
5. `ProjectStageChanged`只作提交后通知和投影，不触发或补建绑定；初验/终验报告状态不进入绑定身份、唯一键、触发或锁链。
6. `Q-FCOM-002`关闭前不自动关闭、解锁或改写绑定；本ADR不决定退出、回退或再次进入的最终业务规则。

本ADR获批后仅取代ADR-0036第5项中“COM范围→ACC绑定”的旧锁序及把绑定视为验收单从属事实的下游表达；ADR-0036关于办事处、数量、来源版本、范围历史、V70转换和Q-FCOM-001的其余结论保持不变。

## 物理差量

`acc_acceptance_scope_binding`保存`project_id/project_stage_snapshot_id/delivery_scope_id/scope_allocation_version/binding_trigger/binding_status/effective_from/effective_to/acceptance_fact_version/version`及标准租户审计字段；唯一键为`tenant_id + project_id + project_stage_snapshot_id + delivery_scope_id + scope_allocation_version`。初始状态为`LOCKED`，事实版本为1，`effective_to`为空；不建跨Context物理外键。

## 明确排除

- 不决定`Q-FCOM-001`合同管理员数据范围。
- 不决定`Q-FCOM-002`退出/回退/再次进入语义。
- 不批准Feature Ready、Technical Plan、产品代码、Flyway、历史迁移、SIT、UAT或Release。
- 不修改Yudao基础平台，不实现第三方平台连接器。
