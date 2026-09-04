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

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`3`
- 已接收或已确认主干等价路径数：`8`
- 仍需逐路径适配记录数：`0`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `0c92f3fc1c8b50fa9b26c85bae8faa8f70bc9c92`
- `38fc0d9d6189f3860ce951174e04f3f3cdc4f162`
- `891c6fa4feb595eecfd2752d807f1c11db07805b`

## 代码事实时间序重放检查点（2026-09-04）

> 依据三个来源分支的实际提交代码逐项记录；代码接收不自动构成 Implementation Done。

- 来源分支：`codex/f-cut-001-matrices`
- 代码事实记录：`25` 个提交-路径组合
- 重放顺序：全局提交时间、来源稳定顺序、分支拓扑顺序。
- 接收范围：全部模块；冲突只保留到具体文件或 hunk，不形成整提交、整模块或整分支拒绝。
- 详细清单：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv` 与稳定化报告。
