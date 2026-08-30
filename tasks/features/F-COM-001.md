# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`FEATURE_READY`

## 当前检查点

基线`c21745a9`；F-COM-001 Feature Ready整改中。已锁定项目级持久水位、冲突整体失败、原子ERP接收、人工候选只关联正式Owner、V70 `quantity_status`兼容、PLT逐行迁移及项目经理/公司范围联合授权，并移除未批准冲突事件；下一步验证A-E规格闭环后复审。

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
