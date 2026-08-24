# F-PROJ-002 项目拆分、项目树与进度汇总

> Feature 实施状态：`IMPLEMENTATION_IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`NOT_EVALUATED`
> 当前阻断：无
> 当前任务：Task 9 提供全部后代闭环守卫
> Requirement ID：`PM-02`
> 关联契约：`PM-04` 项目树数据范围、`COM-01` 交付范围、`CLO-02` 闭环守卫
> Feature Spec：`specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`
> Feature Spec SHA-256：`dfd09ac27e6ede86a640ec610e4eade8c9b5b55d902f3272f93f29de5b44395f`
> Feature 物理契约：`specs/features/F-PROJ-002-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-002-project-split-tree-and-progress-aggregation.md`
> 锁定规格提交：`0be4056e6334be4c5b0c9ae9810bd04c782f81c1`

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

Task 8 已完成不可变权重策略修订、BPM提交与重复/乱序回调处理、直接子项目逐级快照、`PENDING`缺失语义及正式API；V1.7旧权重/进度运行入口已退役。自动化规则、服务、API合同与全模块回归通过，MySQL用例按既定停库约束跳过并留待Task 11数据库闭环统一执行。下一步执行Task 9，提供当前完整树版本下的全部后代闭环守卫。

## 已登记的非阻断问题

- BPM公共流程状态事件当前不携带最终审批人编号，无法可靠填写`approved_by`；本任务保留流程实例、审批完成时间和完整策略版本证据，不用发起人或系统用户伪造审批人。该字段补齐需BPM公共事件提供权威最终审批人后前向修正，不影响策略审批、生效区间和快照链继续实施。
