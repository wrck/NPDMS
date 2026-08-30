# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`READY / GO`
> Feature实施状态：`IN_PROGRESS`
> 当前阶段：`IMPLEMENTATION`
> Technical Plan Gate：`PASS / GO`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`

## 当前检查点

Task 2A已`PASS / GO`。Task 3实施前发现关系DTO双来源键与V143单一source_key不可同时执行，独立裁决采用双来源键方案；Task 3保持`BLOCKED_BY_SPEC`。最近Gate为关系二元来源身份Contract/Schema复审，通过前不写运行服务、不启动Task 8，也不解除下游依赖。

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
