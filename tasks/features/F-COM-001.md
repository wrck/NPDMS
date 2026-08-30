# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`IN_PROGRESS`
> 当前阶段：`IMPLEMENTATION`
> Technical Plan Gate：`PASS / GO`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

Task 3在`d8a27561`通过独立Code Review与真实MySQL Gate，状态`PASS / GO`。Task 4“人工候选、关系核对与公司范围”运行候选已完成：新增候选追加、公司范围守卫、既有CONFIRMED Owner关联、拒绝及可见列表，并按首次复审收敛递归JSON规范载荷与companyCode输入边界；候选状态保持`REVIEW_REQUIRED`。聚焦非IT测试33/33、隔离MySQL 8.4测试4/4通过；最近Gate为Task 4最小整改复审，Task 8暂不并行写共享资产，下游依赖仍未解除。

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
