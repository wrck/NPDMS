# F-PROJ-002 项目拆分、项目树与进度汇总

> Feature 实施状态：`TECHNICAL_PLAN_READY`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`NOT_EVALUATED`
> 当前阻断：无
> 当前任务：Task 1 锁定 Feature 前向物理与机器契约
> Requirement ID：`PM-02`
> 关联契约：`PM-04` 项目树数据范围、`COM-01` 交付范围、`CLO-02` 闭环守卫
> Feature Spec：`specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`
> Feature Spec SHA-256：`3280e0654694f7a3def4ad0885bf051629fa2d7c31b49c93e46f613ed75181d2`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-002-project-split-tree-and-progress-aggregation.md`
> 锁定规格提交：`b453cb0b80804e288be360b50ee0bfef6809b798`

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

新的 V1.8 Technical Plan 已基于当前 Schema、模块边界、API、前后端页面和测试完成存量审计，并覆盖组合拆分草稿、原子批量应用、完整项目树版本、权限裁剪、版本化权重、进度快照、全部后代闭环守卫、响应式页面和真实浏览器验收。下一步执行 Task 1；该任务是Feature-forward契约锁定，不重新打开SDS审核门禁。
