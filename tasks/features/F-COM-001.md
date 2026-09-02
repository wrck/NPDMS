# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`REBASELINING`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`REQUIREMENT_CONVERGENCE`
> Technical Plan Gate：`SUPERSEDED`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

需求方已确认COM-A与COM-B承载不同需求，要求按Requirement整体合并。COM-B单线Task和Technical Plan停止，实施状态保持`NOT_STARTED`；未完成的Task 1 cherry-pick已中止，没有COM-B业务代码进入master。当前先形成统一PRD、Feature Spec、物理契约和实施计划，再选择性接收代码。

COM-A与COM-B不存在Git继承关系，历史完成证据不得相互转记。统一实现以COM-A闭环为基础，吸收COM-B的批量来源、人工候选/对账、项目范围版本、当前范围查询与冲突处理；AST地点迁出COM，PLT迁移证据独立落位。

## Gate输入

- `specs/features/F-COM-001-contract-order-and-delivery-scope.md`
- `specs/features/F-COM-001-physical-contract.json`
- `specs/features/F-COM-001-legacy-reuse-audit.md`
- PRD `COM-01`、SDS Commerce分册、正式迁移契约及当前`pms-module-commerce`实现

## 状态约束

- 只允许按已通过的唯一Technical Plan执行；Task 1公共API与错误机器合同先行，公共API、Flyway、权限/菜单种子与共享错误码串行合入；
- 附件/XLSX只可参考名称与样式，不参与业务、数量、迁移或不一致裁决；
- 不实现V2自动指派、COM-02或第三方ERP连接器；
- 本Feature生产Provider形成前，F-IMP-002 Task 12继续`BLOCKED_BY_DEPENDENCY`。
