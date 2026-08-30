# F-IMP-001 割接上线实施就绪快照

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NO-GO`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`EXE-06@V1=PARTIAL`
> Feature Spec：`specs/features/F-IMP-001-implementation-readiness-snapshot.md`
> 复用审计：`specs/features/F-IMP-001-legacy-reuse-audit.md`
> 物理契约：`specs/features/F-IMP-001-physical-contract.json`
> 消费Feature：`F-CUT-002`

## 当前最小工作单元

- `T-FIMP001-AST-01 Public Machine Contract Gate`：锁定提交`c5f7ecda`已独立复审`PASS / GO`；正式SDS、机器JSON、AST API/DTO、公共失败和Contract测试已冻结，未包含Mapper/Provider。
- AST Owner Provider锁定提交`69d37400`已独立复审`PASS / GO`；IMP消费错误映射Contract Gate锁定提交`36f44719`亦为`PASS / GO`。当前进入`DeviceScopeFactApiAdapter`失败测试优先实现与独立Code Review；F-IMP-003～005仍等待Feature Ready评审。
- Feature Ready后可生成Technical Plan并用受控替身实施不依赖生产事实的部分；生产Owner事实未形成前不声明Implementation Done。

## 已完成

- 依据独立裁决将EXE-06拆为独立IMP Feature，并从F-CUT-002覆盖中移除。
- 已纠正SDS下游投影：EXE-06物理表统一为`imp_implementation_readiness_snapshot`。
- 已完成旧arrival/installation/configuration/jointtest后端、前端、迁移、测试和公共API复用审计。
- 已形成DRAFT Feature Spec、物理契约、公开API边界、权限、幂等和验收分层。
- 已将副作用评估从旧只读GET拆为`POST actions/evaluate`，并在正式SDS锁定查询/历史/重验、并发、幂等、审计和测试边界。
- 已新建F-IMP-002～005 DRAFT Feature Spec，登记稳定Owner契约及合入顺序。
- 已撤销不具备独立业务闭环的F-AST-002，将`DeviceScopeFactApi`纠正为本Feature下由AST物理Owner交付的`T-FIMP001-AST-01`支撑Task；已核验F-AST-001现有Device聚合和归属版本可复用且无需新表。

## 阻断

- F-IMP-002已为`BASELINE / READY / NOT_STARTED`并冻结到货公开事实；F-IMP-003～005仍为`DRAFT / NOT_READY / NOT_STARTED`，其公开事实契约尚未通过独立评审。
- `ImplementationReadinessSnapshot`迁移策略为`REBUILD_AFTER_OWNERS`，旧`pms_eng_*`状态不能直接升级。
- AST现有Device聚合、`T-FIMP001-AST-01`公开契约与生产Provider均已通过；IMP消费Adapter、COM/EXE事实与后续聚合仍是Implementation Done硬依赖。

## AST物理Owner支撑Task

- `T-FIMP001-AST-01`：基于F-AST-001现有`ast_device`和归属版本交付`DeviceScopeFactApi.resolveBySerials/lockAndRevalidate`；不新增表、不迁移数据、不产生独立Feature状态。
- 审计：`specs/features/F-IMP-001-ast-device-scope-support-audit.md`。
- 机器契约：`specs/features/F-IMP-001-device-scope-fact-contract.json`（`PASS`）。
- Contract Gate、Provider Gate及IMP消费错误映射Gate均已通过；当前只允许实现公开事实到内部Port的原样映射，不注册Fake/fallback或Task 12完整生产装配。
- 合入顺序：AST API/Provider → IMP消费者装配 → CUT真实消费；消费者不得跨模块读取AST表或内部实现。

## 验证边界

- 当前只验证规格生成一致性和Feature资产结构。
- 后续CUT单元/集成可用受控替身；真实MySQL和浏览器正向验收必须使用IMP生产Provider从真实Owner事实生成的快照。
