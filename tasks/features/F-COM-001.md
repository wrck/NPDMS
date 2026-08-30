# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`IN_PROGRESS`
> 当前阶段：`IMPLEMENTATION`
> Technical Plan Gate：`PASS / GO`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

Task 2十表Schema与V70前向兼容已在`4996c754`通过并完成状态回写。Task 2A `PlatformMigrationEvidenceApi`公共机器合同候选已形成：仅含SDS、机器合同、公开API/DTO/稳定错误与合同测试，不含Provider、Mapper、DDL或生产Bean；当前Gate为`REVIEW_REQUIRED`，尚未授权PLT实现。

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
