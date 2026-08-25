# F-PROJ-007 项目任务树与原生任务工作台

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FPROJ007-FEATURE-READY-20260825-01`
> Implementation Done Gate：`NOT_STARTED`
> Technical Plan Gate：`PASS / NPDMS-FPROJ007-TECHPLAN-20260825-01-R1`
> 当前阻断：`待独立复审：Task 4只读API与TASK_NATIVE Provider`
> 当前任务：`Task 4 提供项目工作区、任务树、详情和工作台只读API`
> Requirement ID：`PM-11（V1）`
> Feature Spec：`specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md`
> Feature物理契约：`specs/features/F-PROJ-007-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-007-project-task-tree-and-native-workbench.md`
> 锁定规格提交：`5f37b2d1adf4666ccfc595f0acf1829cd323e44f`

## 当前Gate工作单元

- [x] 从PRD/SDS定位PM-11 V1边界
- [x] 核对V1.7 `pms_project_task`与V1.8 `proj_project_task`双模型
- [x] 形成Feature Spec、物理契约和追溯候选
- [x] 获得独立Feature Ready裁决
- [x] 基于锁定基线全新生成Technical Plan
- [x] 闭环首次计划复审四项NO-GO
- [x] 获得Technical Plan独立裁决
- [x] Task 1 Implementation Done（独立裁决GO；`63c442b`、`20edc84`、`7637add`）
- [x] Task 2 Implementation Done（独立裁决GO；`fa55d74`、`ef4e955`）
- [x] Task 3 Implementation Done（独立裁决GO；`8c2f0f1`）
- [ ] Task 4 Implementation Done（实现及本地验证完成，待独立裁决）

> 检查点（2026-08-25）：Task 4四个只读API、五模式分页、ProjectTreeScope+责任范围裁剪及TASK_NATIVE失败关闭宿主已实现；单元7/7、空库V1→V89和真实MySQL7/7、25模块构建及基线校验PASS；待独立复审。
