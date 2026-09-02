# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`IMPLEMENTATION`
> Technical Plan Gate：`PASS / GO`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

唯一Technical Plan在锁定提交`8f5ec1c1`通过独立正式裁决，Gate为`PASS / GO`。实施状态仍`NOT_STARTED`；最近Gate为Task 1“COM公开API与错误机器合同”独立Contract/Code Review Gate，未通过前不得进入后续实现。

`Q-GOV-20260901-002`已选择本COM-B任务链为唯一后续实施基础。COM-A分支任务与Done证据为`SUPERSEDED / DO_NOT_MERGE`，不得继续实施、合并或用于推导本Feature状态；COM-B未Git继承COM-A。

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
