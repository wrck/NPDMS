# F-PROJ-005 服务经理人工指派与责任分布

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ005-FEATURE-READY-20260825-01`
> Implementation Done Gate：`PENDING`
> 当前阻断：无
> 当前任务：Task 3 改造PROJ指派事务与状态语义
> Requirement ID：`PM-08`（仅V1人工指派）
> Feature Spec：`specs/features/F-PROJ-005-service-manager-manual-assignment.md`
> Feature物理契约：`specs/features/F-PROJ-005-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-005-service-manager-manual-assignment.md`
> 锁定规格提交：`9c55a7b965cadd85e893bab92c2def5881490cb7`

## 事实边界

- V1只实现人工候选、即时指派/改派、责任分布和通知；不实现自动指派。
- `ASSIGNED`要求有效主责服务经理与有效项目经理同时存在；本Feature不实现PM-11。
- 成员时间表保留历史，通知使用Outbox快照和SYSTEM持久deliveryKey，不新增历史或通知重试表。
- 用户已禁用测试驱动顺序，但每个Task完成前执行风险匹配验证。
- 当前只推进Implementation，不准备Deployment、SIT、UAT或Release材料。

## 任务跟踪

- [x] Task 1 建立SYSTEM与成员物理基础（迁移契约5/5、25模块Reactor编译通过）
- [x] Task 2 提供SYSTEM候选与幂等站内信API（定向测试8/8、18模块Reactor通过）
- [ ] Task 3 改造PROJ指派事务与状态语义
- [ ] Task 4 增加候选与责任分布查询API
- [ ] Task 5 完成Outbox站内信投递闭环
- [ ] Task 6 改造响应式界面并完成Feature验收
