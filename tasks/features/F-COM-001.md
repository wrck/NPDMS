# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`TECHNICAL_PLAN`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

基线`a0cf6d06`的Technical Plan独立复审为`NO-GO`；A/B定点整改中：新COM命令服务与旧`DeliveryScopeService`隔离，Release导入器先经PLT暂存，COM仅消费正式`COMPLETED`批次；无正式批次时Job保持暂停。最近Gate为A/B最小复审，实施仍`NOT_STARTED`。

## Gate输入

- `specs/features/F-COM-001-contract-order-and-delivery-scope.md`
- `specs/features/F-COM-001-physical-contract.json`
- `specs/features/F-COM-001-legacy-reuse-audit.md`
- PRD `COM-01`、SDS Commerce分册、正式迁移契约及当前`pms-module-commerce`实现

## 状态约束

- 只允许生成并送审唯一当前Technical Plan；Technical Plan GO前不修改DDL、后端、前端、菜单或测试；
- 附件/XLSX只可参考名称与样式，不参与业务、数量、迁移或不一致裁决；
- 不实现V2自动指派、COM-02或第三方ERP连接器；
- 本Feature生产Provider形成前，F-IMP-002 Task 12继续`BLOCKED_BY_DEPENDENCY`。
