# ADR-0037 产品主数据设备类型赋值与割接快照

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`
> Requirement：`EQP-01`、`CUT-01`、`CUT-03`、`CUT-07`
> 关联问题：`Q-FCUT003-001`

## 决策

1. 产品主数据是“产品编码对应设备类型编码”的业务来源 Owner；来源事实必须包含稳定产品主键、产品编码、`deviceTypeCode`和来源版本。产品编码、产品型号、CONP 类型或客户端输入均不得自行推导设备类型。
2. SYSTEM 继续拥有 `pms_device_type` 允许值、名称和启停状态，只校验产品主数据给出的编码，不拥有具体设备或产品的类型赋值。
3. AST Device 聚合保存产品主数据分类的当前投影及来源键、来源版本和赋值版本，并通过现有 `DeviceScopeFactApi` 加性返回；CUT 不直连产品主数据、不读取 AST 表，也不建立第二套类型映射。
4. F-CUT-002 创建新割接任务时，要求范围内每台设备均有当前可核验且命中 SYSTEM 有效值域的设备类型事实，并把编码、产品主数据来源键/版本和 AST 赋值版本冻结到 `cut_task_device_scope`。任一设备未解析、来源冲突或字典值无效时任务整体不创建。
5. F-CUT-003 只以任务设备范围中冻结的去重设备类型编码集合参与 P3 动态匹配；产品主数据或字典后续变化不改写既有任务或清单。
6. 已执行迁移保持不可变。下一前向迁移只增加 AST 当前投影与 CUT 冻结快照字段；既有设备和既有 `NEW_PLATFORM` 任务没有精确产品主数据来源事实时字段保持空并阻断进入完整设备类型匹配，不按产品编码、型号、CONP、字典默认值或测试种子回填。`LEGACY_FORWARD`继续不生成设备范围。

## 接口与失败语义

- `DeviceScopeFact.Device`与`DeviceScopeRevalidationQuery.ExpectedDevice`加性携带`deviceTypeCode/deviceTypeSourceKey/deviceTypeSourceVersion/deviceTypeAssignmentVersion`；水位继续为结构化向量，并加入`deviceTypeAssignmentVersion`，不使用摘要。
- AST重验同一设备的设备类型编码或来源身份变化时返回`STALE`及当前完整事实；来源损坏返回`OWNER_DATA_CORRUPTED`。设备范围本身可供其他消费者使用，但F-CUT-002必须把设备类型事实缺失或无效转换为业务门禁并零写入任务。
- 产品主数据同步、连接器、认证、调度和历史批次不属于本决策；本决策只冻结平台内消费契约与前向数据边界。

## 实施与替身边界

- 本决策不授权实现产品主数据、AST Provider或SYSTEM新能力；跨模块只保留公开接口和稳定事实结构。
- F-CUT-002/F-CUT-003可先按该接口完成CUT自有领域、应用和查询闭环，并在`src/test`使用受控正向替身提供完整设备类型事实；替身不得进入生产装配。
- 跨模块生产Provider未形成只阻断CUT生产Adapter接通、真实浏览器正向证据和Implementation Done，不阻断CUT内核、REST/UI候选及聚焦测试继续推进。
- 受控替身不得被登记为产品主数据同步、AST正式投影或SYSTEM权威校验证据。

## 禁止项

- 禁止把 `productCode/productModel/conpType` 直接等同或映射为 `deviceTypeCode`；
- 禁止 CUT 维护产品—设备类型映射或访问产品主数据/AST业务表；
- 禁止修改已执行迁移或为存量行补默认设备类型；
- 禁止因设备类型字典停用而改写既有任务快照和已生成清单。
