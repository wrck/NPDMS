# F-PROJ-002 项目拆分、项目树与进度汇总

> Feature 实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`PASS`
> 当前阻断：无
> 当前任务：F-PROJ-002已完成，按工程链定位下一Feature
> Requirement ID：`PM-02`
> 关联契约：`PM-04` 项目树数据范围、`COM-01` 交付范围、`CLO-02` 闭环守卫
> Feature Spec：`specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md`
> Feature Spec SHA-256：`dfd09ac27e6ede86a640ec610e4eade8c9b5b55d902f3272f93f29de5b44395f`
> Feature 物理契约：`specs/features/F-PROJ-002-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-002-project-split-tree-and-progress-aggregation.md`
> 锁定规格提交：`52dffd8286e619576086a72ab66bd6b050e80354`

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

## Task 11完成结论

V72～V76已覆盖组合范围、权限、深度30、进度策略、待计算、闭环阻断、组织范围、模板绑定及编码流水；空库、V69→V76、重复迁移和当前库均通过Flyway验证。后端全量回归、静态合同、TypeScript、生产构建、20万项目性能数据集和真实浏览器四档响应式闭环全部通过。AC-FPROJ002-001～012均形成当前V1.8证据，Implementation Done Gate为`PASS`。

## 验收跟踪

- [x] AC-FPROJ002-001 组合拆分与预览
- [x] AC-FPROJ002-002 校验失败保留草稿且无业务副作用
- [x] AC-FPROJ002-003 原子批量创建
- [x] AC-FPROJ002-004 任意深度与无环移动
- [x] AC-FPROJ002-005 五类项目树查询
- [x] AC-FPROJ002-006 权限与有限可见性
- [x] AC-FPROJ002-007 权重与审批版本
- [x] AC-FPROJ002-008 进度待计算
- [x] AC-FPROJ002-009 全部后代闭环守卫
- [x] AC-FPROJ002-010 幂等、并发与完整版本
- [x] AC-FPROJ002-011 规模性能
- [x] AC-FPROJ002-012 真实浏览器与响应式

下一步仍处于Implementation Phase：按规格仓库工程链定位下一个正式Feature并执行Feature Ready，不进入Deployment、SIT、UAT或Release。

## 已登记的非阻断问题

- BPM公共流程状态事件当前不携带最终审批人编号，无法可靠填写`approved_by`；本任务保留流程实例、审批完成时间和完整策略版本证据，不用发起人或系统用户伪造审批人。该字段补齐需BPM公共事件提供权威最终审批人后前向修正，不影响策略审批、生效区间和快照链继续实施。
- 当前项目权威状态仅有`ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED`，现有闭环审批也没有独立的暂停事实源。守卫将未关闭且非闭环审批中的项目保守归类为`EXECUTING`，因此暂停不会被误放行；待后续执行域提供权威暂停状态后，`ClosureStatePort.PAUSED`可返回精确阻断类型。该展示粒度不影响当前门禁正确性。
