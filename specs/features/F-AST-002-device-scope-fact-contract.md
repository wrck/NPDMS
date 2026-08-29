# F-AST-002 设备范围事实解析与重验 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY`
> Requirement：`EQP-01（V1/P0）`
> Requirement切片覆盖：`EQP-01@V1=PARTIAL`
> Owner Context：`AST（资产管理）`
> 前置Feature：`F-AST-001`、`F-PROJ-003`
> 消费Feature：`F-IMP-001`、`F-CUT-002`

## 1. 业务目标与范围

在不创建第二份设备主档或归属事实的前提下，由AST将租户内序列号集合解析为稳定`deviceId/currentProjectId/projectAssignmentVersion`，并为长流程提供按期望版本的锁定重验。本Feature不修改归属、不授权、不读旧`pms_equipment`作为新Owner真值。

## 2. 规则与公开契约

- 序列号去除空白后按AST正式业务身份规则匹配；空、重复、缺失、停用/退役、跨租户、归属不明或不属于目标项目均失败关闭。
- `DeviceScopeFactApi.resolveBySerials`输入`tenantId/projectId/serialNumbers`，返回规范化有序的`deviceId/sn/currentProjectId/projectAssignmentVersion/customerId/customerAssignmentVersion`和整体`scopeVersion`；错误分类不泄露其他租户设备存在性。
- `DeviceScopeFactApi.lockAndRevalidate`输入期望设备ID、项目、每设备归属版本和`scopeVersion`，按稳定`deviceId`顺序锁定并返回`VALID/STALE/INVALID`；不替代PROJ主体授权。
- 调用方先用`ProjectScopeApi.ACTION_EDIT`确认本人参与、负责或明确授权项目，再使用AST契约确认设备归属；任一空范围返回空/拒绝，不扩大到租户全量。

## 3. 实现与验收边界

- 新契约由AST现有`Device`聚合、当前项目/客户投影和归属版本提供，不新增物理表、兼容视图或双写。
- 旧`AssetDeviceScopeApi.validateAssignableSerials`只作为复用审计反例：它只返回缺失/不可用/重复分类且读旧设备表，不得装配为生产`DeviceScopeFactApi`。
- 验收覆盖SN精确解析、多设备有序返回、归属版本变化、项目变化、停用/退役、越权/跨租户、并发锁定、空范围和真实MySQL契约。

## 4. Feature Ready Gate

`NOT_READY`：须确认F-AST-001生产Device聚合与归属版本的当前可用性，审批`DeviceScopeFactApi` Schema、锁定顺序、错误分类和与`ProjectScopeApi.ACTION_EDIT`的合入顺序。
