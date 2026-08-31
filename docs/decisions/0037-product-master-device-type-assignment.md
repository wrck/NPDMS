# ADR-0037 割接消费既有设备产品类型公开事实

> 状态：`RESOLVED_BY_EXISTING_FEATURE / PENDING_INTEGRATION_REVIEW`
> Requirement：`EQP-01`、`CUT-01`、`CUT-03`、`CUT-07`
> 关联问题：`Q-FCUT003-001`

## 决策

1. F-AST-002 已承接产品主数据受控副本和设备当前产品类型查询；CUT 不再扩展 `DeviceScopeFactApi`，不实现产品类型 Owner、同步、映射或值域。
2. CUT 预留内部 `CutoverDeviceProductTypePort`，语义对齐 `AssetProductTypeApi.getAuthorizedDeviceProductType(AuthorizedDeviceProductTypeQuery)`：输入为受信当前操作人和经设备范围事实解析后的稳定 `deviceIds`；结果只使用公开合同已有的 `deviceId/productTypeCode/enabled/sourceVersion/resolutionStatus/syncStatus/lastSuccessfulSyncTime/fromLastSuccessfulCopy`。
3. F-CUT-002 创建新任务时只冻结已获授权、已解析、启用且同步状态满足正式合同的 `productTypeCode` 与 `sourceVersion`；F-CUT-003 只消费任务设备范围中冻结、去重并稳定排序的编码，不在P3重新查询AST当前事实。
4. 产品编码、产品型号、CONP类型、字典默认值、客户端输入和测试种子均不得推导产品类型。CUT不得读取AST表或建立第二套产品类型映射。
5. F-AST-002公开查询目前不提供写事务内的期望版本锁定重验。CUT不得虚构`sourceKey/assignmentVersion/watermark`；生产任务创建的原子接线方案须在集成Gate由AST/CUT Owner独立复审。

## 实施与替身边界

- 当前分支不合并、复制或重做其他分支中的F-AST-002实现，只保留CUT消费端口。
- CUT单元/集成测试可在`src/test`显式组装受控替身，返回与公开结果同形的完整正向事实，以推进任务创建快照和P3匹配闭环。
- 替身不得进入生产装配，不得冒充AST权威事实或真实浏览器证据。
- F-AST-002代码尚未集成以及生产原子重验合同尚未通过，只阻断生产Adapter、生产任务创建装配、真实浏览器闭环和Implementation Done，不阻断CUT内核、REST/UI候选及聚焦测试。

## 禁止项

- 禁止修改或复制F-AST-002 Owner实现；
- 禁止向`DeviceScopeFactApi`追加产品类型字段；
- 禁止用现有公开合同不存在的来源键、赋值版本或水位补造快照；
- 禁止修改已执行迁移或为存量任务回填推断值。
