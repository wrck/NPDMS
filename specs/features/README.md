# Feature Spec 索引

本目录保存经SDS基线派生的正式Feature Spec。Feature Spec只拆解已批准需求与设计，不得改变PRD业务语义、领域Owner、权限或状态模型。

本索引是投影视图，不是独立状态源。`规格状态`、`Feature Ready`和`实施状态`是三个不同维度：规格状态与Feature Ready以对应Feature Spec为权威，实施状态以当前Feature实施任务记录为权威；Git、CI、测试、真实浏览器和评审结论只作为证据引用。索引与权威来源冲突时必须纠正本索引，不得反向修改权威事实或再建立Capability状态。

每个参与Requirement覆盖计算的Feature Spec必须声明一行机器可读的`Requirement切片覆盖`，格式为`Requirement@V1|V2=FULL|PARTIAL`，多项使用中文分号分隔。`FULL`表示该Feature完整覆盖该目标版本切片，`PARTIAL`表示只覆盖其中一个合法子闭环；关联、支撑或依赖关系不得自动视为覆盖。`scripts/generate_requirement_traceability.py`把该声明与`tasks/features/F-*.md`中的实施状态合并，派生111个正式目标版本切片的覆盖状态。Feature实施完成只会使声明的子闭环生效，不会自动关闭未映射义务；不存在权威任务记录时也不得仅凭Feature Spec中的历史说明宣布切片完成。

| Feature | 名称 | Requirement | 规格状态 | Feature Ready | 实施状态 |
|---|---|---|---|---|---|
| [F-PROJ-001](F-PROJ-001-manual-project-creation-and-template-initialization.md) | 手动项目创建与模板初始化 | PM-01、PM-03 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `1c76050`） |
| [F-PROJ-002](F-PROJ-002-project-split-tree-and-progress-aggregation.md) | 项目拆分、项目树与进度汇总 | PM-02 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `57923b1`） |
| [F-PROJ-003](F-PROJ-003-project-subtree-authorization-and-unified-scope.md) | 项目子树授权与统一数据范围 | PM-04 | BASELINE | READY | IMPLEMENTATION_COMPLETE（NPDMS `9ab894f`） |
| [F-PROJ-004](F-PROJ-004-project-business-attribute-classification.md) | 项目业务属性判定、模板匹配历史与影响识别 | PM-07 | BASELINE | READY（`NPDMS-FPROJ004-FEATURE-READY-20260825-06`） | IMPLEMENTATION_COMPLETE（`NPDMS-FPROJ004-IMPLEMENTATION-DONE-20260825-07`；仅PROJ子切片） |
| [F-PROJ-005](F-PROJ-005-service-manager-manual-assignment.md) | 服务经理人工指派与责任分布 | PM-08（V1） | BASELINE | READY（`NPDMS-FPROJ005-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `25230ce`；整改复审GO） |
| [F-PROJ-006](F-PROJ-006-project-rollback-exception-close-and-reopen.md) | 项目回退、异常关闭与受控重开 | PM-10（V1） | BASELINE | READY（`NPDMS-FPROJ006-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `fc9f8b1`；独立复审GO） |
| [F-PROJ-007](F-PROJ-007-project-task-tree-and-native-workbench.md) | 项目任务树与原生任务工作台 | PM-11（V1） | BASELINE | READY（`NPDMS-FPROJ007-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `b559978`；独立复审GO） |
| [F-PROJ-008](F-PROJ-008-project-stage-gate-and-forward-advance.md) | 项目阶段准出门禁与正向推进 | PM-03（V1，PARTIAL） | BASELINE | READY / GO（master修订009选择收敛） | IN_PROGRESS（Task 1-2已集成；尚未进入Implementation Done；Task 3受`Q-FPROJ-009`阻断） |
| [F-SOL-001](F-SOL-001-project-duration-baseline-and-change-approval.md) | 项目工期基线与变更审批 | PRE-01（V1） | BASELINE | READY（`NPDMS-FSOL001-FEATURE-READY-20260826-01-R1`） | IMPLEMENTATION_COMPLETE（NPDMS `c417dee`；独立复审GO） |
| [F-SOL-002](F-SOL-002-site-survey-assignment-and-readiness.md) | 工勘分工信息采集与实施就绪 | PRE-02（V1） | BASELINE | READY（`NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`） | IMPLEMENTATION_COMPLETE（NPDMS `7243727f`；独立复审GO） |
| [F-SOL-003](F-SOL-003-requirement-analysis-versioning.md) | 需求分析动态表单与版本冻结 | PRE-04（V1） | BASELINE | READY（GO；整改提交`4d04dbd63bbd01683416563bece31da6cd53f849`） | IMPLEMENTATION_COMPLETE（`NPDMS-FSOL003-DYNAMICFORM-IMPLEMENTATION-20260828-01-R1`；Requirement覆盖仍按PARTIAL映射派生） |
| [F-PLT-001](F-PLT-001-unified-file-identity-and-version-management.md) | 统一文件身份与版本管理 | PLT-02（V1） | BASELINE（含`CHG-PRD-2026-08-27-004`） | READY（`NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`） | IMPLEMENTATION_COMPLETE（原实现NPDMS `6d6c6ea`独立复审GO；可选扫描增量NPDMS `890196d2`、`24f3c1a4`独立复审GO） |
| [F-PLT-002](F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md) | 共享动态表单模板与实例基础能力 | SOL-01（主）、PRE-04/PM-03/PM-11（支撑） | BASELINE（业务实例API聚焦修订） | READY（原基础闭环GO保留；聚焦修订GO见`4d04dbd63bbd01683416563bece31da6cd53f849`） | IMPLEMENTATION_COMPLETE（基础闭环NPDMS `0ce68d04`；F-SOL-003跨Context消费闭环已独立Done） |
| [F-CUS-001](F-CUS-001-customer-master-and-local-lifecycle.md) | 客户主档与本地生命周期 | CUS-03（V1） | BASELINE | READY（`SPEC-FCUS001-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `31834bc6`；受控验收种子、真实MySQL、稳定幂等、权限负向、删除恢复、真实浏览器与合并后代码审查通过） |
| [F-AST-001](F-AST-001-device-serial-archive-and-temporal-assignment.md) | 设备序列号档案与时态归属 | EQP-01（V1） | BASELINE | READY（`SPEC-FAST001-FEATURE-READY-20260825-01`） | REVALIDATION_REQUIRED（已补master Task；历史证据`a9f8b7c5`不反推Done） |
| [F-AST-002](F-AST-002-device-product-type-copy-and-public-query.md) | 设备产品类型受控副本与公开查询 | EQP-01（V1局部） | BASELINE | READY / GO（master修订011关闭编号冲突） | IN_PROGRESS（代码已集成；待master真实MySQL与独立Done裁决） |
| [F-ACC-001](F-ACC-001-acceptance-report-version-and-deliverable-sync.md) | 初验/终验报告版本与交付件同步 | ACC-03（V1）、ACC-04（V1局部） | BASELINE | READY / GO（master修订011关闭编号冲突） | IN_PROGRESS（代码已集成；待master真实MySQL、Chromium与独立Done裁决） |
| [F-ACC-002](F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md) | 满意度问卷、达标判定与归档同步 | ACC-02（V1）、ACC-04（V1局部） | BASELINE | READY / GO（master修订011关闭编号冲突） | IN_PROGRESS（代码已集成；待F-ACC-001及自身运行复验与独立Done裁决） |
| [F-COM-001](F-COM-001-contract-order-association-and-delivery-scope-allocation.md) | 合同订单关联与交付范围管理（[COM-B历史规格](F-COM-001-contract-order-and-delivery-scope.md)） | COM-01（V1） | BASELINE | READY / REQUIREMENT_CONVERGENCE_APPROVED | IN_PROGRESS（master选择性集成与重新验证中） |
| [F-CUT-001](F-CUT-001-cutover-unified-configuration-foundation.md) | 割接统一配置版本、风险与调研矩阵基础 | CUT-07/09/10（V1） | BASELINE（Scope重开已确认） | READY（`NPDMS-FCUT001-FEATURE-READY-20260830-02`） | IN_PROGRESS（`master@c61e5b1e`已部分集成；V133示例迁移与master最终运行DoD前不恢复Done） |
| [F-CUT-002](F-CUT-002-cutover-intake-and-manual-assessment.md) | 割接任务接入与人工分级 | CUT-01/CUT-02（V1局部） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY（代码回执`c9066332`） |
| [F-CUT-003](F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md) | P3动态采集清单、直接填写与人工降级 | CUT-03（V1） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY（代码回执`c9066332`） |
| [F-CUT-004](F-CUT-004-p4-cutover-plan-authoring.md) | P4割接方案编制与版本提交 | CUT-04（V1） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_SPEC / BLOCKED_BY_DEPENDENCY（代码回执`c9066332`） |
| [F-CUT-005](F-CUT-005-p5-graded-approval.md) | P5分级审批 | CUT-05（V1） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY（代码回执`c9066332`） |
| [F-CUT-006](F-CUT-006-p6-cutover-closure.md) | P6割接跟踪与闭环 | CUT-06（V1） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY（代码回执`c9066332`） |
| [F-CUT-007](F-CUT-007-cutover-dashboard-kpis.md) | 割接首页授权KPI | CUT-01（V2） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / NOT_READY（代码回执`c9066332`） |
| [F-CUT-008](F-CUT-008-p5-lead-time-and-external-reminders.md) | P5提前时间判断与外部提醒 | CUT-05（V2） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / NOT_READY（代码回执`c9066332`） |
| [F-CUT-009](F-CUT-009-p3-authorized-export-and-navigation.md) | P3授权清单导出与受控流程跳转 | CUT-03（V2） | BASELINE | READY / GO | IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / NOT_READY（代码回执`c9066332`） |
| [F-CUT-010](F-CUT-010-cutover-spare-system-coordination.md) | 割接备件系统协同 | CUT-08（V2） | BASELINE | READY / GO | IN_PROGRESS / IMPLEMENTATION_TASK_4（仅Task 1～3进入`c9066332`） |
| [F-INS-001](F-INS-001-inspection-rule-version-and-field-configuration-foundation.md) | 巡检规则版本与字段配置基础 | INS-03（V2局部）、INS-09（V2） | BASELINE | READY / GO | IMPLEMENTATION_IN_PROGRESS（代码回执`6eb7c89e`；Q-FINS001-005/006阻断完整发布） |

与本批主档直接相关的`INT-02`、`INT-03`、`INT-04`及`EQP-04`同步运行闭环不属于F-CUS-001或F-AST-001，后续必须分别形成独立Feature Spec和追溯链；F-CUS-001、F-AST-001完成均不代表这些同步Feature完成。主档Feature只冻结外部副本的字段Owner、来源版本、稳定写入边界和降级展示契约。

状态和门禁遵循`docs/engineering/00-engineering-chain.md`：只有Requirement版本切片追溯、业务规则、状态、权限、API、数据变化、验收标准、依赖与物理Owner及相关Open Question全部关闭后，Feature才可进入Implementation。每个Feature只允许一个当前有效Technical Plan；多个参与者或会话必须先在master登记Delivery Unit，再按Feature、Task或跨Feature工作包使用独立分支/Worktree并行实施，DU或Task完成均不产生Feature或Requirement完成状态。

Feature任务、分支候选、认领缺口和master集成判定见[`tasks/features/README.md`](../../tasks/features/README.md)。该矩阵是审计投影，不会把分支自报状态提升为master状态。
