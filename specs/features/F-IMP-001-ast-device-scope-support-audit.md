# F-IMP-001 AST设备范围事实支撑审计

> 支撑Task：`T-FIMP001-AST-01`
> 物理Owner：`AST（资产管理）`
> 消费Feature：`F-IMP-001`、`F-IMP-002`、`F-CUT-002`
> 物理契约：`N/A（复用F-AST-001现有Device聚合与ast_device，不新增表）`

## 1. 裁决与范围

`DeviceScopeFactApi`只是AST现有Device聚合对IMP/CUT开放的批量范围事实契约，不新增业务聚合、生命周期、数据或用户闭环，不能独立形成Feature Done。原`F-AST-002`撤销；该能力改由F-IMP-001内的AST物理Owner支撑Task交付，AST拥有API、代码、表查询和合入顺序，IMP/CUT只消费公开契约。

审计覆盖F-AST-001规格、`ast_device`物理表、Device DO、公开API/DTO、归属并发实现与测试，以及旧`AssetDeviceScopeApi`。本审计不修改F-AST-001已完成业务边界，也不把支撑Task状态计入EQP-01完成度。

## 2. 当前可用事实

| 对象 | 当前证据 | 判定 |
|---|---|---|
| `ast_device` | 租户内SN唯一；保存稳定`id/sn/project_id/project_assignment_version`，迁移与种子均按该表约束 | 可复用为生产Owner事实，不新增表 |
| `DeviceDO` | 映射`ast_device`并暴露`projectId/projectAssignmentVersion` | 可由AST内部查询实现复用；不得跨模块暴露DO |
| `DeviceQueryApi.getDevice` | 按稳定`deviceId`返回`DeviceSummaryDTO`，包含租户、SN、当前项目和归属版本 | 可复用DTO字段语义；单条无批量规范化、范围水位和锁定重验，不能直接满足支撑契约 |
| 归属写入与并发测试 | AST在同一事务维护当前投影和归属版本，并有真实MySQL并发验证 | 可作为`lockAndRevalidate`版本校验依据 |
| 旧`AssetDeviceScopeApi.validateAssignableSerials` | 读取旧设备模型，只返回缺失/不可用/重复分类 | 不可装配为生产`DeviceScopeFactApi`，也不得把旧设备状态或测试种子升级为当前归属真值 |

## 3. 支撑契约边界

- `resolveBySerials`输入受信`tenantId/projectId`与规范化SN集合，返回稳定有序的`deviceId/sn/currentProjectId/projectAssignmentVersion`和结构化`scopeWatermark`；不存在、跨租户、重复、停用或不属于项目均失败关闭。
- `lockAndRevalidate`输入期望设备ID、项目、逐设备归属版本和水位，按稳定`deviceId`顺序锁定AST当前投影，返回`VALID/STALE/INVALID`；归属变化、缺失、停用或范围变化不得返回VALID。
- 契约只证明设备身份和当前直接项目归属，不替代`ProjectScopeApi.ACTION_EDIT`主体授权，不证明订单应到数量、到货签收、安装或割接就绪。
- API模块和实现文件由AST Owner维护；IMP/CUT不得依赖AST `-biz`、Service、Mapper、DO或`ast_device`表。
- 本支撑Task不新增表、不迁移数据、不建立Provider专属状态；审计、锁顺序和批量查询使用F-AST-001现有Device及归属事实。

## 4. 实施与验收边界

- 合入顺序：先由AST Owner交付公开API与生产Provider，再装配F-IMP-001/F-IMP-002，最后供F-CUT-002真实运行消费。
- 相关Feature通过Feature Ready后可在消费者单元/集成测试中使用受控替身；替身不得进入生产装配、生成正式事实或支撑真实浏览器正向闭环。
- 支撑验收覆盖乱序/重复SN规范化、单项缺失、跨租户、非本项目、归属版本变化、并发重验和空范围失败关闭；生产验证必须读取F-AST-001现有Device聚合。

结论：现有F-AST-001生产Device聚合具备稳定身份和归属版本基础；最近缺口是AST Owner的批量范围机器契约和锁定重验实现，不是新的业务Feature或新表。
