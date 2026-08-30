# F-AST-002 设备产品类型受控副本与公开查询

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> Technical Plan Gate：`PASS / NPDMS-FAST002-TECHPLAN-20260830-01`；身份契约差量`PASS / NPDMS-FAST002-IDENTITY-CONTRACT-DELTA-20260830-FINAL`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无；Task 1代码整改复审`GO / NPDMS-FAST002-TASK1-REMEDIATION-REVIEW-20260830-01`，允许提交该逻辑单元；提交完成后进入Task 2，后续Task未验证内容不得提前宣称完成
> Requirement ID：`EQP-01（V1/P0）`
> Feature Spec：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ast-002-device-product-type-copy-and-public-query.md`
> 锁定规格提交：`a52b22b4`
> 关联消费：`F-INS-001`发布与工程师选择外部Gate
> 适用基线：`CHG-PRD-2026-08-30-010`

## 当前最小工作单元

- Task 1公开契约与输入守卫单元已通过独立代码复审，当前只完成提交和工作树核验；提交后最近一个前置已满足但尚未通过的单元为Task 2三表前向Schema与DO。

## 已完成

- Feature Ready：`GO NPDMS-FAST002-FEATURE-READY-20260830-01`。
- 唯一Technical Plan已覆盖公开API、三表、权限、来源顺序、冲突事务、批量授权范围、测试、真实MySQL和追溯收口。
- 首轮独立复审发现来源顺序、设备复合外键、关联设备范围、测试节奏和冲突事务五项问题；已按正式SDS、Feature Spec和计划顺序整改。
- 整改复审：`GO NPDMS-FAST002-TECHPLAN-20260830-01`；只放行Implementation，不代表Implementation Done或后续发布Gate。
- Task 1独立代码复审发现自由`serviceIdentity`只能做白名单声明、不能证明调用主体；已登记Q-FAST002-001并接受高可信裁决ADR-0036。两轮NO-GO整改闭合专用适配器、不可绕过验证和受控导入主体后，设计差量终审`GO NPDMS-FAST002-IDENTITY-CONTRACT-DELTA-20260830-FINAL`。
- Task 1代码已交付两个公开Query/Result契约、包级栈式调用上下文、不可变主体授权注册表、租户/动作/委托用户守卫和错误码；15项定向测试、模块编译、SDS与追溯检查通过，独立代码复审`GO NPDMS-FAST002-TASK1-REMEDIATION-REVIEW-20260830-01`。Inspection专用适配器、空设备范围查询和受控导入仍分别保留在Task 8、Task 5和Task 4。

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