# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`FEATURE_READY`

## 当前检查点

基线`862b47ec`；F-COM-001 Feature Ready仅剩B项。已锁定同版本先比载荷、不同版本才做前驱CAS；全对象重放为ACCEPTED_NO_CHANGE，混合重放/更新为ACCEPTED，冲突全批回滚且重放不改Owner/范围版本/Outbox；下一步单点复审。

## Gate输入

- `specs/features/F-COM-001-contract-order-and-delivery-scope.md`
- `specs/features/F-COM-001-physical-contract.json`
- `specs/features/F-COM-001-legacy-reuse-audit.md`
- PRD `COM-01`、SDS Commerce分册、正式迁移契约及当前`pms-module-commerce`实现

## 状态约束

- Feature Ready GO前不生成Technical Plan，不修改DDL、后端、前端、菜单或测试；
- 附件/XLSX只可参考名称与样式，不参与业务、数量、迁移或不一致裁决；
- 不实现V2自动指派、COM-02或第三方ERP连接器；
- 本Feature生产Provider形成前，F-IMP-002 Task 12继续`BLOCKED_BY_DEPENDENCY`。
