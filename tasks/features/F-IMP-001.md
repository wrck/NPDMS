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

- 关闭Feature Ready前置：冻结EXE-01～04 Owner Feature Spec、公开事实契约、字段/状态/完整性映射与AST设备范围稳定事实契约。
- Feature Ready后可生成Technical Plan并用受控替身实施不依赖生产事实的部分；生产Owner事实未形成前不声明Implementation Done。

## 已完成

- 依据独立裁决将EXE-06拆为独立IMP Feature，并从F-CUT-002覆盖中移除。
- 已纠正SDS下游投影：EXE-06物理表统一为`imp_implementation_readiness_snapshot`。
- 已完成旧arrival/installation/configuration/jointtest后端、前端、迁移、测试和公共API复用审计。
- 已形成DRAFT Feature Spec、物理契约、公开API边界、权限、幂等和验收分层。

## 阻断

- EXE-01@V1、EXE-02@V1、EXE-03@V1、EXE-04@V1均为`NOT_STARTED`，对应Owner Feature Spec和公开事实契约尚未锁定。
- `ImplementationReadinessSnapshot`迁移策略为`REBUILD_AFTER_OWNERS`，旧`pms_eng_*`状态不能直接升级。
- AST现有按SN校验接口不返回冻结设备范围所需的稳定设备ID与归属版本；需AST Owner先锁定公开契约，生产实现是Implementation Done硬依赖。

## 验证边界

- 当前只验证规格生成一致性和Feature资产结构。
- 后续CUT单元/集成可用受控替身；真实MySQL和浏览器正向验收必须使用IMP生产Provider从真实Owner事实生成的快照。
