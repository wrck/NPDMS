# F-AST-002 设备产品类型受控副本与公开查询

> Feature实施状态：`NOT_STARTED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无规格阻断；Feature Ready已通过`NPDMS-FAST002-FEATURE-READY-20260830-01`，下一步仅允许生成并评审唯一Technical Plan，计划通过前不得进入Implementation
> Requirement ID：`EQP-01（V1/P0）`
> Feature Spec：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`
> 关联消费：`F-INS-001`发布与工程师选择外部Gate
> 适用基线：`CHG-PRD-2026-08-30-010`

## 当前最小工作单元

- 基于已通过Feature Ready的规格生成并评审唯一Technical Plan。计划必须限定为AST本地受控副本、来源证据、公开API、权限、迁移和测试，不得加入CRM/MES连接器；Technical Plan Gate通过前不得进入Implementation。

## 实施范围

- 产品类型受控副本及来源映射。
- 设备当前产品类型引用与解析状态。
- `pms-module-asset-api`公开查询契约和DTO。
- 按编码批量查询及按授权设备查询。
- 租户、设备和数据范围守卫。
- 停用历史解释、来源降级、并发与审计。
- 前向Flyway、后端测试、真实MySQL和契约消费验证。

## 明确排除

- CRM/MES网络连接、认证、调度、游标、重试、补偿和对账。
- EQP-04连接器Implementation Done。
- 产品类型自由维护、猜测映射或示例值种子。
- Inspection业务表、规则发布、任务选择或设备连接采集实现。

## 完成条件

- 全部实现直接追溯`EQP-01@V1`合法子闭环和`CHG-PRD-2026-08-30-010`。
- `AssetProductTypeApi`契约、权限负向、未知/停用、空范围、来源降级和并发测试通过。
- 真实MySQL前向迁移与约束验证通过。
- F-INS-001消费契约可在不直读AST表、不依赖连接器的情况下验证。
- 更新Feature索引与Requirement追溯后，才可记录唯一Implementation Done Gate。