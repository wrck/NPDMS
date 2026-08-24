# F-PROJ-002 项目拆分、项目树与进度汇总

> Feature 实施状态：`IMPLEMENTATION_IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`NOT_EVALUATED`
> 当前阻断：无
> 当前任务：Task 11 初始化数据、全量验证与V1.8验收闭环
> Requirement ID：`PM-02`
> 关联契约：`PM-04` 项目树数据范围、`COM-01` 交付范围、`CLO-02` 闭环守卫
> Feature Spec：`specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`
> Feature Spec SHA-256：`dfd09ac27e6ede86a640ec610e4eade8c9b5b55d902f3272f93f29de5b44395f`
> Feature 物理契约：`specs/features/F-PROJ-002-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-002-project-split-tree-and-progress-aggregation.md`
> 锁定规格提交：`44dd2aea08f82fa691c7236de0e5c617bcd701b4`

## 事实边界

- PRD V1.8、工程链、已批准 SDS Phase 1/2/3 和本 Feature Spec 是正式输入；SDS 不需要重新审核。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与当前门禁校验。
- V1.7 的 F-PM02 Spec、Technical Plan、代码、迁移、页面和测试只作复用审计证据，不能产生完成结论。
- Technical Plan 必须逐项把存量实现分类为复用、改造、退役或缺失，并从首个任务重新验证。
- 用户已禁用测试驱动顺序；任务仍需完成风险匹配的自动化、数据库或真实浏览器验证。
- 当前仍处于 Implementation，不进入 Deployment、SIT、UAT 或 Release。

## Feature Ready 证据

- Requirement、业务价值、Scope、业务规则、状态与命令、权限、API、数据变化和验收标准均已闭合。
- PM-02 与 PM-04、COM-01、CLO-02 的切片边界已明确，不宣称完整实现关联 Requirement。
- 相关 Open Question 无会改变业务语义、Owner、权限或状态模型的未关闭项。
- 规格仓库 Phase 1、Phase 2、Phase 3 校验及追溯生成器检查通过。
- F-PROJ-001 前置 Feature 已达到 `IMPLEMENTATION_COMPLETE / PASS`。

## 下一步

Task 10 已完成V1.8拆分草稿、五类项目树查询、进度策略与闭环守卫的响应式工作台；旧`/pms/project-tree`和无版本`child-weights`前端入口已退役，写请求携带幂等键和版本条件。静态合同测试、TypeScript检查和生产构建通过；真实浏览器与数据库闭环按计划归入Task 11统一验收。下一步执行Task 11，补齐初始化数据、全量回归、性能证据和V1.8验收闭环。

## 已登记的非阻断问题

- BPM公共流程状态事件当前不携带最终审批人编号，无法可靠填写`approved_by`；本任务保留流程实例、审批完成时间和完整策略版本证据，不用发起人或系统用户伪造审批人。该字段补齐需BPM公共事件提供权威最终审批人后前向修正，不影响策略审批、生效区间和快照链继续实施。
- 当前项目权威状态仅有`ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED`，现有闭环审批也没有独立的暂停事实源。守卫将未关闭且非闭环审批中的项目保守归类为`EXECUTING`，因此暂停不会被误放行；待后续执行域提供权威暂停状态后，`ClosureStatePort.PAUSED`可返回精确阻断类型。该展示粒度不影响当前门禁正确性。
