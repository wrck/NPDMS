# F-SOL-001 项目工期基线与版本化变更审批

> Feature实施状态：`IMPLEMENTING`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FSOL001-FEATURE-READY-20260826-01-R1`
> Implementation Done Gate：`PENDING`
> Technical Plan Gate：`PASS / NPDMS-FSOL001-TECHPLAN-20260826-01`
> 当前阻断：Task 6客户依据成功提交、Task 9对应真实场景、Task 10完整浏览器闭环及Feature Done依赖`PLT-02 / FileArtifact`真实公共事实；不阻断此前独立任务
> 当前任务：`Task 3 建立SOL持久化、状态值域和查询原语`
> Requirement ID：`PRE-01（V1）`
> Feature Spec：`specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`
> Feature物理契约：`specs/features/F-SOL-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-26-f-sol-001-project-duration-baseline-and-change-approval.md`
> 锁定规格提交：`f2c563df978b7d7b3b1de9ad245b9c485bbdbae8`

## 实施边界

- 本Feature只实现PRE-01，不包含PRE-02、PLN-01、PLN-04、部署、SIT、UAT或Release。
- SOL以`sol_construction_plan`、`sol_construction_plan_revision`、`sol_construction_plan_change`承载当前事实，不创建`sol_construction_plan_item`。
- 首次工期直接生效；后续变更通过平台BPM单节点服务经理审批。审批通过后新工期成为唯一当前版本，计划仅标记待重算，不覆盖旧施工计划。
- 旧工期倒排和计划变更仅作历史只读证据，不迁移、不双写、不作为V1.8当前真值。
- `PLT-02 / FileArtifact`未实施前保持失败关闭，不伪造文件事实；其前置独立任务继续推进。

## 任务跟踪

- [x] Task 1 建立SOL工期物理模型、配置种子和Feature工作单（PASS / `b2b019d` / 独立裁决GO）
- [x] Task 2 提供PROJ项目资格与当前参与人公共事实（PASS / `365d907` / 独立裁决GO）
- [ ] Task 3 建立SOL持久化、状态值域和查询原语
- [ ] Task 4 实现首次工期录入与查询
- [ ] Task 5 实现工期变更草稿与部分更新
- [ ] Task 6 冻结依据并提交平台BPM审批
- [ ] Task 7 同步消费BPM终态并生效工期结果
- [ ] Task 8 建设响应式项目工期界面并冻结旧PRE-01写入口
- [ ] Task 9 完成真实MySQL、事务并发和BPM集成验证
- [ ] Task 10 完成真实浏览器、独立复审和Feature Done回写

> 检查点（2026-08-26）：Task 1提交`b2b019d`经独立复审GO；V1～V91隔离MySQL迁移、根唯一键、种子幂等、迁移契约4/4、20模块Reactor编译及规格快照校验均PASS，允许推进Task 2。

> 检查点（2026-08-26）：Task 2已完成PROJ项目资格与当前参与人公共事实候选实现；公共API行为8/8、V1～V91隔离MySQL Mapper/锁定回归4/4及25模块Reactor均PASS，当前等待独立Implementation Done复审，尚未回写Task 2 PASS。

> 检查点（2026-08-26）：Task 2提交`365d9073f56ecbc5ad41cc98d2d03b78fd820169`经独立复审GO；公共契约、封闭角色值域、受信租户、项目行与主责区间锁顺序、版本/阶段守卫及范围边界均通过，允许推进Task 3。

> 检查点（2026-08-26）：Task 3已完成自然日工期规则、三表DO、封闭写接口、场景化锁定/CAS/稳定分页与流程实例查询原语；聚焦规则、Mapper接口契约及V1～V91隔离MySQL回归10/10、20模块Reactor均PASS，当前等待独立Implementation Done复审，尚未回写Task 3 PASS。
