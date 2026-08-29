# F-AST-002 设备范围事实解析与重验

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NOT_READY`
> Requirement：`EQP-01@V1=PARTIAL`
> Feature Spec：`specs/features/F-AST-002-device-scope-fact-contract.md`

## 当前最小工作单元

- 审评`DeviceScopeFactApi.resolveBySerials/lockAndRevalidate`及其与`ProjectScopeApi.ACTION_EDIT`的边界。
- 核验F-AST-001生产Device聚合可用性；旧`AssetDeviceScopeApi`不得代替新契约。
- Ready前不进入Technical Plan或实现。
