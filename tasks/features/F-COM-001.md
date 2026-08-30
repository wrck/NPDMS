# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`FEATURE_READY`

## 当前检查点

基线`eda54bd0`；最近Gate为F-COM-001 Feature Ready；已确认COM-01无现行Feature且现有F-PROJ-002切片不能替代项目当前已分配范围；候选覆盖完整合同/订单副本、范围分配/释放、冲突、工作台和`getAssignedScope`，ERP连接器仅预留接口；下一步独立复审Feature/物理/迁移/复用边界。

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
