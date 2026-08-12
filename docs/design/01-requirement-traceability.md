# SDS Phase 1：需求追溯

## 1. 依据与边界

- 业务基线：`docs/baseline/prd-v1.6.md`，对应 PRD V1.6。
- 追溯主表：`docs/traceability/requirement-matrix.md`，覆盖 115 项 V1/V2 正式需求。
- 旧 `specs/001-project-delivery-platform/domains/` 仅用于发现历史领域映射和差异，不作为需求语义来源。
- V3 22 项与 OUT_OF_SCOPE 9 项仅保留边界追溯，不进入当前实现设计。

## 2. Phase 1 追溯链

每项正式需求必须沿以下链路落位：

`Requirement ID → PRD业务域 → Phase 1领域 → 模块 → 聚合根 → 状态机/工作流 → 权限模型 → 计划API/事件 → 计划数据对象 → 测试类别`

本阶段已补齐模块、聚合、状态机/工作流、权限、计划 API、计划数据对象和测试类别的工作映射；下游 API、数据表和测试用例在 Phase 2/3 生成前仍使用 `NOT_STARTED`，不得伪装成已实现资产。

## 3. 领域重确认工作表

| Phase 1领域 | 业务责任 | PRD需求范围（初步） | 主要聚合根 | Owner状态 |
|---|---|---|---|---|
| PROJ 项目治理 | 项目创建、主子项目、模板、任务、指派、项目状态 | PM-01～PM-11、PROJ-12 | Project、ProjectTemplate、ProjectTask | OWNER_SIGNED |
| SOL 交付准备与方案 | 工勘、需求分析、计划、实施方案 | PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05 | Preparation、ConstructionPlan、ImplementationPlan | OWNER_SIGNED |
| IMP 实施执行 | 到货、安装、配置Log、联调、风险、质量安全检查、实施阶段交付件上传 | EXE-01～EXE-06、IMP-01～IMP-02 | ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、ImplementationSafetyCheck、DeliveryEvidence | OWNER_SIGNED |
| ACC 验收与闭环 | 培训、满意度、验收、交付件齐套校验、审核、统一归档、项目闭环、持续服务交接 | ACC-01～ACC-06、CLO-01～CLO-06 | Acceptance、DeliveryArtifact、ProjectClosure、ServiceHandover | OWNER_SIGNED |
| CUT 割接与稳定治理 | 割接任务、分级、采集清单、方案、审批、执行和配置 | CUT-01～CUT-10 | CutoverTask、CutoverAssessment、CutoverPlan、CutoverExecution | OWNER_SIGNED |
| SRV 服务运营 | 工单、工时、巡检、问题闭环和设备服务状态；内部 Context 拆为 Work Order & Time、Inspection、Service Operations | WO-01～WO-06、INS-01～INS-09、SRV-01 | WorkOrder、TimeClaim、InspectionTask、ServiceIssue、ServiceStatus | OWNER_SIGNED |
| CUS 客户与服务关系 | 客户、联系人、客户关系和 CRM 同步副本 | CUS-01～CUS-04 | Customer、Contact、AssetRelation、CustomerSyncSnapshot | OWNER_SIGNED |
| AST 资产管理 | 设备序列号、设备档案、配置Log、维保客观状态和来源同步副本 | EQP-01～EQP-07、AST-01～AST-02 | Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot | OWNER_SIGNED |
| COM 合同订单履约 | 合同/订单本地同步副本、交付范围、履约回写与对账 | COM-01～COM-02 | Contract、SalesOrder、OrderLine、DeliveryScope、FulfillmentSnapshot、ReconciliationRecord | OWNER_SIGNED |
| RES 资源与外包 | 服务商、转包申请与付款回访门禁 | RES-01、SUB-01～SUB-05 | Supplier、SubcontractRequest、PaymentGate | OWNER_SIGNED |
| ANA 经营分析 | 项目组合和工时/状态/人效统计 | ANA-01、RPT-01～RPT-04 | PortfolioView、MetricSnapshot | OWNER_SIGNED |
| PLT 平台公共能力 | 待办、文件身份版本、变更、授权、NFR公共约束及 Device Access & Collection 采集编排 | PLT-01～PLT-02、AUT-01～AUT-02、CHG-01、NFR-01～NFR-03、INT-12 | Todo、FileArtifact、AuthorizationGrant、ChangeRequest、DeviceCredential、CredentialGrant、CollectionTask | OWNER_SIGNED |
| KNO 技术知识治理 | 当前仅承接已确认的技术公告同步边界 | INT-04 | TechnicalNoticeReference | OWNER_SIGNED |

上表是基于 PRD V1.6 的 Phase 1 工作映射。发生跨域需求时，选择一个数据 Owner 作为唯一归属，其余领域以引用关系记录；旧规格不覆盖该映射。

## 4. 覆盖结论

- 正式需求覆盖：115/115（由追溯矩阵生成器校验）。
- 需求缺失：0。
- 设计资产完成度：Phase 1 尚未完成，SDS/Feature/API/Data/Test 均为 `NOT_STARTED` 或 `PLANNED`。
- 不能从旧规格直接推导的内容：数据库字段、接口契约、事件名称、组件拆分、性能实现和测试脚本。
