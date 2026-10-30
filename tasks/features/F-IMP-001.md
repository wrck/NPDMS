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
> 公共就绪API合同：`specs/features/F-IMP-001-readiness-api-contract.json`（`PASS / 38fc0d9d`；仅公开接口，不代表快照Provider完成）
> 来源审计：`codex/f-cut-001-matrices@eda54bd0`

## 已实际实现并进入master

- `ImplementationReadinessApi`、结构化查询/重验DTO、封闭失败类型和契约测试；公开机器合同已通过，CUT消费既有接口，不另建就绪合同；
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

## 2026-10-28 项目内设备查询性能修复（旧功能范围）

- 需求方授权统一核查项目详情阶段任务涉及的设备读取。本增量仅优化现有 `ImplementationReadinessService` 的设备事实读取，不实现上述尚未完成范围，不改变Feature裁决。
- 常规就绪读取已先校验目标项目完整 `PROJECT_VIEW/PROJECT_EDIT` 范围，再由AST现有锁接口按受信租户和当前直接项目返回设备ID、SN及归属版本。现在直接消费该锁定事实，移除每台设备再次调用通用详情的重复读取；同服务的审批重验路径已有相同消费方式。项目授权、原设备锁、期望SN/归属版本比较、适用前置、批准方案及不可变快照保持原语义。
- 先失败后通过：新增多设备上下文及无逐台查询、缺完整项目范围拒绝、锁内归属版本变化拒绝三项。旧实现33项中两项按预期失败；修复后ImplementationReadinessService/Rules/Validation/APIContract四套件33项全部通过，使用固定JDK25/Maven及工作树独占锁。日志 `.run/feedback-20261028/tests/readiness-locked-facts-before.log`、`readiness-locked-facts-after.log`。
- 验证边界：已证明代码行为与重复调用消除；尚未部署或完成真实浏览器业务链，未执行迁移、DDL或服务操作，不据此宣称项目内所有设备查询均达到1秒。

## 2026-10-28 安装成果候选同事务锁结果复用（旧功能范围）

- 项目详情安装成果候选经`InstallationTaskBusinessObjectProvider.candidates/current`调用`InstallationBusinessResultSource.inventory`，原路径已经锁定当前项目设备和安装行，随后private read又重复相同两套FOR UPDATE。现在仅在此同一@Transactional调用栈传递私有已锁上下文复用结果；独立inspect、lockAndInspect、declaresFormation仍按原顺序当次锁取，不引入跨请求缓存，不修改Owner API或SQL。
- 原selectById、租户/项目/类型/身份校验、历史拒绝、游标/对象过滤、requirements当次豁免与版本读取、全项目完成集合和观察版本计算保持。178库只读核对会话/全局REPEATABLE-READ及既有项目索引，未修改隔离级别或锁条件。
- 新增锁次数行为测试先复现重复读取：2类14项中1项因两次设备锁失败；修复后14/14通过，含完整Observation等价、两种锁各一次、当次规则版本变化和新增未完成设备阻断。既有地址/过期/空设备/身份/历史/游标及Task Provider事务调用测试保留。日志`.run/feedback-20261028/tests/installation-result-locked-context-before.log`、`installation-result-locked-context-after.log`，物理只读证据`acceptance/installation-result-lock-contract-readonly.json`。独立审查核对通过；未部署/重启/迁移、未改状态或不可变历史。
