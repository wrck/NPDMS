# SDS Phase 1：需求追溯

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8独立复审GO，当前分册已纳入正式基线
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 1. 依据与边界

- 业务基线：`docs/baseline/prd-v1.8.md`，对应 PRD V1.8。
- 追溯主表：`docs/traceability/requirement-matrix.md`，覆盖 100 项 V1/V2 正式需求。
- `specs/001-project-delivery-platform/domains/` 为当前PRD派生的13领域规格，不得脱离PRD手工改变业务语义。
- 范围统计：V1 53 项、V2 47 项、V1/V2 100 项；已编号V3 31 项、跨需求演进方向2项、OUT_OF_SCOPE 9 项。
- V3 与 OUT_OF_SCOPE 仅保留边界追溯，不进入当前实现设计。

## 2. Phase 1 追溯链

每项正式需求必须沿以下链路落位：

`Requirement ID → PRD业务域 → Phase 1领域 → 模块 → 聚合根 → 状态机/工作流 → 权限模型 → 计划API/事件 → 计划数据对象 → 测试类别`

V1.8已重建100项工作追溯索引；V1.7的模块、聚合、状态机/工作流、权限、计划API和数据对象映射须按差量重验证。PM-01、PM-03既有Feature链接保留为实施事实，但不代表其已通过V1.8影响审查。

## 3. 领域重确认工作表

| Phase 1领域 | V1.8业务责任 | V1.8正式需求范围 | V1.8聚合根 | V1.8 Owner状态 |
|---|---|---|---|---|
| PROJ 项目治理 | 项目创建、主子项目、模板、任务、指派、项目状态 | PM-01～PM-11、PROJ-12、INT-01 | Project、ProjectTemplate、ProjectTask、TaskWorkBinding、TaskCompletionRule | OWNER_CONFIRMED / BASELINE |
| SOL 交付准备与方案 | 工勘、需求分析、计划、实施方案 | PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01 | Preparation、ConstructionPlan、ImplementationPlan | OWNER_CONFIRMED / BASELINE |
| IMP 实施执行 | 到货、安装、配置Log、联调、风险、质量检查、实施阶段交付件上传 | EXE-01～EXE-06、IMP-01 | ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、DeliveryEvidence | OWNER_CONFIRMED / BASELINE |
| ACC 验收与闭环 | 培训、满意度收集、验收、交付件齐套校验、审核、统一归档、项目闭环、静态服务交接 | ACC-01～ACC-04、ACC-06、CLO-01～CLO-02 | Acceptance、SatisfactionCollection、DeliveryArtifact、ProjectClosure、ServiceHandover | OWNER_CONFIRMED / BASELINE |
| CUT 割接与稳定治理 | 割接任务、问卷分级、P3动态清单与采集结果、方案、分级审批、P6闭环和配置 | CUT-01～CUT-10 | CutoverTask、CutoverAssessment、CutoverChecklist、CutoverPlan、CutoverClosure | OWNER_CONFIRMED / BASELINE |
| SRV 服务运营 | 巡检、问题闭环和设备服务状态；内部 Context 拆为 Inspection、Service Operations | INS-01～INS-09、SRV-01 | InspectionTask、ServiceIssue、ServiceStatus | OWNER_CONFIRMED / BASELINE |
| CUS 客户与服务关系 | 客户、联系人、客户关系和 CRM 同步副本 | CUS-01～CUS-04、INT-03 | Customer、Contact、AssetRelation、CustomerSyncSnapshot | OWNER_CONFIRMED / BASELINE |
| AST 资产管理 | 设备序列号、设备档案、配置Log、维保客观状态和来源同步副本 | EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06 | ConfigurationLog、Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot | OWNER_CONFIRMED / BASELINE |
| COM 合同订单履约 | ERP权威合同订单引用和平台交付范围分配 | COM-01 | Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail | OWNER_CONFIRMED / BASELINE |
| RES 资源与外包 | 服务商、转包申请与付款满意度门禁 | RES-01、SUB-01～SUB-05、INT-07 | Supplier、SubcontractRequest、PaymentGate | OWNER_CONFIRMED / BASELINE |
| ANA 经营分析 | 项目组合和项目状态统计 | ANA-01、RPT-02 | PortfolioView、MetricSnapshot | OWNER_CONFIRMED / BASELINE |
| PLT 平台公共能力 | 待办、文件身份版本、变更、授权、NFR公共约束及 Device Access & Collection 采集编排 | PLT-01～PLT-02、AUT-01～AUT-02、CHG-01、NFR-01～NFR-03、INT-05、INT-09、INT-10、INT-12 | Todo、FileArtifact、AuthorizationGrant、ChangeRequest、DeviceCredential、CredentialGrant、CollectionTask | OWNER_CONFIRMED / BASELINE |
| KNO 技术知识治理 | 当前仅承接已确认的技术公告同步边界 | INT-04 | TechnicalNoticeReference | OWNER_CONFIRMED / BASELINE |

上表已按PRD V1.8正式范围和`phase-1-domain-ownership.md`完成100项唯一Owner机器对齐；发生跨域需求时只保留一个数据Owner，其余领域以引用关系记录。当前结论仍须通过fresh-context独立复审，旧规格不得覆盖V1.8业务语义。

## 4. 覆盖结论

- 正式需求索引覆盖：100/100；13个Owner唯一映射和Phase 1核心边界通过机器校验及fresh-context独立复审。
- 需求缺失：0。
- 设计资产完成度：V1.7正式分册保留为历史输入；V1.8 Phase 1为`BASELINE`，Phase 2/3仍为`REVALIDATION_REQUIRED`，以对应门禁为准。
- 不能从旧规格直接推导的内容：数据库字段、接口契约、事件名称、组件拆分、性能实现和测试脚本。
