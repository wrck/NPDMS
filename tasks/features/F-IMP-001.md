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

## 三分支按提交时间代码事实重放（2026-09-04）

> 状态以提交源码、测试、迁移、前端与构建文件为事实依据；Feature未关闭的Gate继续保留。

- 原实施状态记录：`> Feature实施状态：IN_PROGRESS`
- 当前实施状态：代码已接收；未完成Feature保持 `IN_PROGRESS`。
- 已接收代码路径：`8`
- 已处理来源提交：`3`
- 来源分支：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。
- 接收原则：按提交时间逐提交重放；任何单文件或单hunk冲突均不阻断其他模块代码。
- 完整逐提交、逐文件记录：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApi.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessException.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessContextFact.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessResult.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessRevalidationQuery.java`
- `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/dto/ImplementationReadinessSnapshotFact.java`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/implementationreadiness/ImplementationReadinessApiContractTest.java`
