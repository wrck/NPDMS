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
| [F-SOL-001](F-SOL-001-project-duration-baseline-and-change-approval.md) | 项目工期基线与变更审批 | PRE-01（V1） | BASELINE | READY（`NPDMS-FSOL001-FEATURE-READY-20260826-01-R1`） | IMPLEMENTATION_COMPLETE（NPDMS `c417dee`；独立复审GO） |
| [F-SOL-002](F-SOL-002-site-survey-assignment-and-readiness.md) | 工勘分工信息采集与实施就绪 | PRE-02（V1） | BASELINE | READY（`NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`） | IMPLEMENTATION_COMPLETE（NPDMS `7243727f`；独立复审GO） |
| [F-SOL-003](F-SOL-003-requirement-analysis-versioning.md) | 需求分析动态表单与版本冻结 | PRE-04（V1） | BASELINE | READY（GO；整改提交`4d04dbd63bbd01683416563bece31da6cd53f849`） | NOT_STARTED（下一动作：REPLAN_REQUIRED；保留现有代码并基于新锁定基线生成全新中文Technical Plan） |
| [F-PLT-001](F-PLT-001-unified-file-identity-and-version-management.md) | 统一文件身份与版本管理 | PLT-02（V1） | BASELINE（含`CHG-PRD-2026-08-27-004`） | READY（`NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`） | IMPLEMENTATION_COMPLETE（原实现NPDMS `6d6c6ea`独立复审GO；可选扫描增量NPDMS `890196d2`、`24f3c1a4`独立复审GO） |
| [F-PLT-002](F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md) | 共享动态表单模板与实例基础能力 | SOL-01（主）、PRE-04/PM-03/PM-11（支撑） | BASELINE（业务实例API聚焦修订） | READY（原基础闭环GO保留；聚焦修订GO见`4d04dbd63bbd01683416563bece31da6cd53f849`） | IMPLEMENTATION_COMPLETE（基础闭环NPDMS `0ce68d04`；跨Context增量待F-SOL-003整体实施） |
| [F-CUS-001](F-CUS-001-customer-master-and-local-lifecycle.md) | 客户主档与本地生命周期 | CUS-03（V1） | BASELINE | READY（`SPEC-FCUS001-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `31834bc6`；受控验收种子、真实MySQL、稳定幂等、权限负向、删除恢复、真实浏览器与合并后代码审查通过） |
| [F-AST-001](F-AST-001-device-serial-archive-and-temporal-assignment.md) | 设备序列号档案与时态归属 | EQP-01（V1） | BASELINE | READY（`SPEC-FAST001-FEATURE-READY-20260825-01`） | IMPLEMENTATION_COMPLETE（NPDMS `a9f8b7c5`；自动化、真实MySQL、查询计划、真实浏览器与合并后复审通过） |
| [F-COM-001](F-COM-001-contract-order-association-and-delivery-scope-allocation.md) | 合同订单关联与交付范围分配 | COM-01（V1） | BASELINE | READY（完整全新审核GO：`c57ee7b5f5226f5dc902d817c034ff1a8f6618c3`） | NOT_STARTED |

与本批主档直接相关的`INT-02`、`INT-03`、`INT-04`及`EQP-04`同步运行闭环不属于F-CUS-001或F-AST-001，后续必须分别形成独立Feature Spec和追溯链；F-CUS-001、F-AST-001完成均不代表这些同步Feature完成。主档Feature只冻结外部副本的字段Owner、来源版本、稳定写入边界和降级展示契约。

状态和门禁遵循`docs/engineering/00-engineering-chain.md`：只有Requirement版本切片追溯、业务规则、状态、权限、API、数据变化、验收标准、依赖与物理Owner及相关Open Question全部关闭后，Feature才可进入Implementation。每个Feature只允许一个当前有效Technical Plan；多个参与者或会话通过排他认领Task和独立分支/Worktree并行实施，Task完成不产生Feature或Requirement完成状态。
