# F-IMP-001 割接上线实施就绪快照

> Feature实施状态：`IN_PROGRESS`
> 实施子状态：`FOUNDATION_IMPLEMENTED / CORE_FEATURE_PENDING`
> 总体工程阶段：`IMPLEMENTATION_PARTIAL`
> Feature Ready Gate：`NO-GO（核心聚合尚未Ready）`
> Implementation Done Gate：`NOT_READY`
> Requirement：`EXE-06@V1=PARTIAL`
> Feature Spec：`specs/features/F-IMP-001-implementation-readiness-snapshot.md`
> 物理契约：`specs/features/F-IMP-001-physical-contract.json`
> AST支撑合同：`specs/features/F-IMP-001-device-scope-fact-contract.json`
> 来源审计：`codex/f-cut-001-matrices@eda54bd0`

## 已实际实现并进入master

- `DeviceScopeFactApi`、DTO、稳定失败类型及契约测试；
- AST生产Owner `DeviceScopeFactApiImpl`、稳定SN规范化、项目/设备归属版本水位与锁定重验；
- AST Mapper查询与事务边界，以及单元、Mapper合同和真实MySQL候选测试；
- F-IMP-002对该公开事实的消费边界；
- 不新增第二设备Owner表，不允许IMP/CUT跨模块读取AST表。

上述内容属于`T-FIMP001-AST-01`物理Owner支撑Task，已经存在真实代码，不能再标记为纯`NOT_STARTED`。

## 尚未完成

- `ImplementationReadinessSnapshot`核心聚合、迁移、应用服务和生产Provider；
- 对EXE-01～EXE-04、COM、PROJ、AST及批准方案水位的完整快照生成与锁定重验；
- 正式REST/UI、生产装配、真实浏览器与Feature Implementation Done。

## 当前裁决

本Feature主状态统一为`IN_PROGRESS`，子状态为`FOUNDATION_IMPLEMENTED / CORE_FEATURE_PENDING`。AST支撑代码可以被F-IMP-002、F-CUT-002复用，但不得将支撑Task完成外推为EXE-06完整Requirement完成。

## 代码事实选择性合入检查点（2026-09-03，ACC/INT/CUT三分支）

> 依据提交代码事实记录；Feature状态保持原值，代码接收不自动构成Implementation Done。

- 来源分支：`codex/f-cut-001-matrices`
- 本轮接收路径数：`8`
- 接收粒度：提交、文件；单个冲突或不符合文件不阻断同分支其他实现。
- 冲突与适配项见 `docs/traceability/code-fact-three-branch-integration-2026-09-03.md`。

已接收路径：

- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApi.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessException.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessContextFact.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessResult.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessRevalidationQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessSnapshotFact.java`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApiContractTest.java`
