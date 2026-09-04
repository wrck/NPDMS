# F-CUT-002 割接任务接入与人工分级

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO`（锁定基线`cad8088a`）
> Technical Plan Gate：`PASS / GO@8eb36222`
> Implementation Done Gate：`BLOCKED_BY_DEPENDENCY`
> `pms_cut_task -> cut_task` Migration Contract Gate：`PASS`（`36d1b37f`）
> API/Physical Machine Contract Gate：`PASS / b7f49166`
> Requirement：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-002-cutover-intake-and-manual-assessment.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源PLT V144/CUT V146/V149 -> master V176/V177/V180`
> master复验：`PLT 28项（跳过MySQL 4）、CUT API 6项、CUT 242项（跳过MySQL 27）、CUT前端68项均零失败；生产Owner、真实MySQL与真实浏览器未在本DU复验，状态保持IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`
> 外部硬依赖：PROJ、IMP、AST、CUS、PLT生产Provider（仅阻断生产装配、真实浏览器和Implementation Done）
> 既有复用基线：`F-PROJ-003`已交付的`ProjectScopeApi`；其余跨模块能力仅预留消费端口并在`src/test`受控模拟，不由CUT实现Owner Provider

## 当前最小工作单元

- Task 1：`PASS / COMPLETE@9b1a613e`（独立整体状态关闭`GO`）。
- Task 2：`PASS / GO@d80ace31 / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`。CUT自有新工作台、创建向导、P2人工分级和P4只读阶段投影已通过独立复审（`9f791d64 + 0c18ed0f`）；真实Spring事务、MyBatis、平台幂等/审计与MySQL 8.4下的受控Owner正向链覆盖A级进入P3、D级进入P4及提交重放，并通过独立Backend/MySQL Gate。生产Adapter、唯一应用装配、真实浏览器及Implementation Done继续等待PROJ/IMP/AST/CUS/PLT正式Owner事实。

## 已完成

- 接受独立裁决：EXE-06拆为独立IMP Feature；CUT-01与CUT-02覆盖均为`PARTIAL`。
- 已固定一线自建P1→P2问卷人工判级→A/B/C进入P3、D进入P4的最小业务闭环。
- 已明确来源幂等、活动设备范围唯一性、IMP快照重验、权限、API、数据和UI边界。
- 已完成本Feature的后端、前端、配置、运行数据/迁移、状态机、权限和测试复用审计；结论为`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`。
- 已纠正CUT物理Owner为`cut_task/cut_assessment`，并将`CutoverAssessment`与旧`pms_cut_risk`解耦为`NEW_ONLY`。
- `pms_cut_task -> cut_task`字段、旧状态只读化、完整性资格和不可迁行处置的机器合同已在`36d1b37f`通过独立迁移Contract Gate；旧类型/组网仅存legacy raw，新路径事实保持空，Owner暂时失败与确定性不匹配分流，PLT批次事务和11组生成投影已锁定。
- 独立裁决已确认F-IMP-003～005未Ready不应永久阻断CUT；跨模块接口冻结后，Feature Ready可允许CUT在非生产装配中使用受控模拟完成自身正向闭环。
- ITR/项目事件Producer、P3以后、V2/V3、自动指派和通用工单动作均排除。

## 依赖边界

- F-CUT-002完整API/物理机器合同已通过，不再阻断Feature Ready。
- AST的`DeviceScopeFactApi`公开合同、Owner Provider及IMP消费适配已分别通过独立Gate；该项不再是F-CUT-002规格阻断，但生产装配与真实依赖闭环仍按各Owner任务状态判定。
- `ImplementationReadinessApi`与CUS `CustomerServiceLevelFactApi`公共机器合同均已通过。生产Provider继续只阻断生产装配、真实浏览器和Implementation Done，不授权CUT重复建设。

## 验收分层

- CUT单元/集成测试可使用受控`ImplementationReadinessApi`替身验证消费边界。
- CUT隔离真实MySQL单元/集成可使用`src/test`受控正向模拟；真实浏览器、生产装配和Implementation Done必须使用生产Owner事实，替身、手工SQL、附件或测试种子不得替代。

> 检查点：Task 2受控正向闭环Backend/MySQL Gate已在`d80ace31`独立复审`PASS / GO`；隔离MySQL 8.4空卷执行152个迁移至V156后2/2通过，另有CUT-002应用正向单元3/3通过。当前最近Gate为生产Owner依赖满足后的Adapter与唯一装配Entry Gate；Feature保持`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`，不得用替身形成生产装配或真实浏览器证据。

## 三分支按提交时间代码事实重放（2026-09-04）

> 状态以提交源码、测试、迁移、前端与构建文件为事实依据；Feature未关闭的Gate继续保留。

- 原实施状态记录：`> Feature实施状态：IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
- 当前实施状态：代码已接收；未完成Feature保持 `IN_PROGRESS`。
- 已接收代码路径：`13`
- 已处理来源提交：`5`
- 来源分支：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。
- 接收原则：按提交时间逐提交重放；任何单文件或单hunk冲突均不阻断其他模块代码。
- 完整逐提交、逐文件记录：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

- `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/CutoverTaskIntakeException.java`
- `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/dto/CutoverTaskIntakeCommand.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverTaskRequestCodec.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskv2/CutoverTaskDO.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/taskv2/CutoverTaskDeviceScopeDO.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskDeviceScopeMapper.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/query/LegacyCutoverTargetIdentityQuery.java`
- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/taskv2/migration/LegacyCutoverReconciliationService.java`
- `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskDeviceScopeMapper.xml`
- `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml`
- `sql/migrations/V146__fcut002_cutover_task_intake.sql`
- `sql/migrations/V149__fcut002_task_origin_assessment_checks.sql`
