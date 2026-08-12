# SDS Phase 1：模块设计

| 模块 | 职责 | 主要聚合 | 入向依赖 | 出向事件/服务 | 禁止依赖 |
|---|---|---|---|---|---|
| 项目治理 | 创建、指派、模板、项目树、任务和项目状态 | Project、ProjectTask、ProjectTemplate | CRM/ERP、组织主数据 | ProjectCreated、ProjectStageChanged | 直接改设备/合同库 |
| 交付准备与方案 | 工勘、需求分析、计划、方案审核 | Preparation、Plan、Solution | Project、字典、文件 | PlanApproved、SolutionApproved | 直接改项目状态 |
| 实施执行 | 到货签收、安装、配置Log、联调、风险、质量安全、实施阶段交付件/证据上传 | ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、ImplementationSafetyCheck、DeliveryEvidence | Project、Device、CollectionTask、File | DeliveryEvidenceUploaded、ImplementationQualityChecked、ImplementationSafetyChecked | 直接执行设备命令、直接改变验收归档状态 |
| 验收与闭环 | 培训、满意度、验收、交付件齐套校验、审核、统一归档、闭环交接 | Acceptance、Closure、Artifact | Project、DeliveryEvidence、File、Questionnaire | ArtifactAccepted、ProjectClosed、ServiceHandoverCreated | 直接改财务事实、直接修改现场实施原始证据 |
| 割接 | 任务、评估、采集清单、方案、审批、执行 | CutoverTask、CutoverPlan | Project、Device、CollectionTask | CutoverApproved、CutoverCompleted | 直接访问采集引擎 |
| 巡检与服务 | 巡检任务、规则、报告、问题和服务状态 | InspectionTask、InspectionRule、ServiceIssue | Device、CollectionTask、UMC | InspectionCompleted、IssueCreated | 读取凭证明文 |
| 客户资产 | 客户、联系人、设备档案和资产关系 | Customer、Device、AssetRelation | CRM、MES、项目事件 | CustomerUpdated、DeviceAssigned | 直接改变项目流程 |
| 合同履约 | 合同、订单行、交付范围、履约对账 | Contract、OrderLine、DeliveryScope | ERP、Project | ScopeAllocated、FulfillmentReconciled | 直接写 ERP |
| 资源外包 | 服务商、转包、付款门禁 | Supplier、SubcontractRequest、PaymentGate | OA、Project、Questionnaire | SubcontractApproved | 直接放行付款 |
| 经营分析 | 指标、组合和经营视图 | PortfolioView、MetricSnapshot | 各域只读事件 | ReportGenerated | 任何交易写操作 |
| 平台公共能力 | 待办、文件、授权、变更、字典和审计 | Todo、FileArtifact、Grant、ChangeRequest | 所有模块 | TodoCreated、AuditRecorded | 拥有业务域状态 |
| 集成适配 | 外部同步、回调、失败补偿和对账 | ExternalMapping、CallbackRecord | 外部系统 | Domain command/event | 直接写业务表 |

## 服务边界

所有公共服务必须暴露应用级命令/查询；领域内部规则由聚合或领域服务执行。跨模块写入必须携带 Requirement ID、操作者、数据范围和幂等键。
