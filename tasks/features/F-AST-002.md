# F-AST-002 设备产品类型受控副本与公开查询

> Feature实施状态：`TECHNICAL_PLAN_READY`
> Technical Plan Gate：`PASS / NPDMS-FAST002-TECHPLAN-20260830-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无规格或计划阻断；Feature Ready与Technical Plan Gate均已通过，允许按唯一Technical Plan进入Implementation
> Requirement ID：`EQP-01（V1/P0）`
> Feature Spec：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ast-002-device-product-type-copy-and-public-query.md`
> 锁定规格提交：`a52b22b4`
> 关联消费：`F-INS-001`发布与工程师选择外部Gate
> 适用基线：`CHG-PRD-2026-08-30-010`

## 当前最小工作单元

- Technical Plan Gate已通过；按唯一Technical Plan从公开契约、服务身份与输入守卫开始实施首个可独立验证单元，完成最小实现、定向测试、验证和提交后再进入下一单元。

## 已完成

- Feature Ready：`GO NPDMS-FAST002-FEATURE-READY-20260830-01`。
- 唯一Technical Plan已覆盖公开API、三表、权限、来源顺序、冲突事务、批量授权范围、测试、真实MySQL和追溯收口。
- 首轮独立复审发现来源顺序、设备复合外键、关联设备范围、测试节奏和冲突事务五项问题；已按正式SDS、Feature Spec和计划顺序整改。
- 整改复审：`GO NPDMS-FAST002-TECHPLAN-20260830-01`；只放行Implementation，不代表Implementation Done或后续发布Gate。

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