# F-COM-001 合同订单副本与交付范围管理任务

> Requirement：`COM-01@V1`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Feature实施状态：`NOT_STARTED`
> 当前阶段：`FEATURE_READY`

## 当前检查点

基线`ed96361d`；F-COM-001 Feature Ready剩余B/C整改中。ERP版本改为不透明值+前驱CAS；V70现列与新增列已分离，旧行通过nullable资格谓词+PLT问题排除，不虚构核对状态或默认补值；下一步验证后复审这两项。

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
