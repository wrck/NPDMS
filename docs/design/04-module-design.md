# SDS Phase 1：模块设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8独立复审GO，当前分册已纳入正式基线
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 模块 | 职责 | 主要聚合 | 入向依赖 | 出向事件/服务 | 禁止依赖 |
|---|---|---|---|---|---|
| 项目治理 | 创建、指派、模板、项目树、Stage→ProjectTask工作台投影、TASK_NATIVE通用任务执行、其他业务绑定与完成判定、项目状态 | Project、ProjectTask、ProjectTemplate | CRM/ERP、组织主数据、非TASK_NATIVE绑定业务查询/API | ProjectCreated、ProjectStageChanged、TaskCompleted | 直接改设备/合同库、复制非TASK_NATIVE绑定业务正文、维护第二套导航树 |
| 交付准备与方案 | 工勘、需求分析、计划、方案审核 | Preparation、Plan、Solution | Project、字典、文件 | PlanApproved、SolutionApproved | 直接改项目状态 |
| 实施执行 | 到货签收、安装、配置Log采集业务结果、联调、风险、阶段质量检查、实施阶段交付件/证据上传 | ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、DeliveryEvidence | Project、Device、CollectionTask、File | ConfigurationLogPublished、DeliveryEvidenceUploaded、ImplementationQualityChecked | 直接执行设备命令、拥有ConfigurationLog原始文件/不可变解析版本、直接改变验收归档状态；IMP-02安全检查不属于当前模块 |
| 验收与闭环 | 培训、满意度收集、验收、交付件齐套校验、审核、统一归档、闭环交接 | Acceptance、SatisfactionCollection、ProjectClosure、Artifact | Project、DeliveryEvidence、File、Questionnaire | SatisfactionResultRecorded、ArtifactAccepted、ProjectClosureCompleted、ServiceHandoverCreated | 直接改财务事实、直接修改现场实施原始证据 |
| 割接 | CUT-01任务、问卷评估、P3同工作台清单匹配/填写/采集回填、方案、审批和P6闭环 | CutoverTask、CutoverAssessment、CutoverPlan、CutoverClosure | Project、Device、CollectionTask | CutoverApproved、CutoverCompleted | 直接访问采集引擎、建设通用工单或独立采集阶段、逐步骤执行或稳定观察 |
| 巡检 | INS-01～INS-09巡检任务、规则、报告和问题 | InspectionTask、InspectionRule、ServiceIssue | Device、Device Access & Collection、UMC | InspectionCompleted、IssueCreated | 读取凭证明文 |
| 服务运营 | SRV-01设备服务状态；ACC-05持续服务跟踪仅为V3方向 | ServiceStatus、ServiceHandoverReference | Device、Customer、ProjectClosureCompleted、ServiceHandoverCreated | ServiceStatusChanged | 当前不创建持续服务跟踪对象，不直接改变设备主档核心身份 |
| 客户与关系 | 客户、联系人、客户关系和同步副本 | Customer、Contact、AssetRelation | CRM、Project、Asset Management | CustomerUpdated、CustomerSyncCompleted | 直接改变项目流程 |
| 资产管理 | 设备档案、归属、维保基本信息、ConfigurationLog原始文件/不可变解析版本和同步副本 | ConfigurationLog、Device、DeviceArchive、RMAReplacement | ConfigurationLogPublished、MES、ITR、备件、Project | ConfigurationLogVersionPublished、DeviceAssigned、AssetSyncCompleted | 改写IMP实施结论、直接改变项目状态 |
| 合同履约 | ERP合同、订单行同步副本及项目交付范围主记录与明细 | Contract、OrderLine、DeliveryScope、DeliveryScopeDetail | ERP、Project | ScopeAllocated、ScopeReleased | 直接写 ERP；不创建COM-02履约回写/对账业务聚合 |
| 资源外包 | 服务商、转包、付款满意度门禁 | Supplier、SubcontractRequest、PaymentGate | OA、Project、SatisfactionCollection | SubcontractApproved | 直接放行付款、修改满意度事实 |
| 经营分析 | 项目组合、项目状态和经批准指标视图 | PortfolioView、MetricSnapshot | 各域只读事件 | ReportGenerated | 任何交易写操作、以其他事实伪造工时/人效指标 |
| 设备连接与采集 | 凭证、授权、采集任务、外部状态原值、结果引用和回调证据 | DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord | 外部采集平台、IMP、CUT、Inspection | CollectionTaskAccepted、CollectionResultAvailable | 不重复建设连接和原始采集引擎 |
| 基础平台能力 | 待办、文件、授权、变更、字典和审计通用能力 | Todo、FileArtifact、Grant、ChangeRequest | 所有模块 | TodoCreated、AuditRecorded | 不拥有业务域状态 |
| 集成适配 | 外部同步、回调、失败补偿和对账 | ExternalMapping、CallbackRecord | 外部系统 | Domain command/event | 直接写业务表 |

## 服务边界

所有公共服务必须暴露应用级命令/查询；领域内部规则由聚合或领域服务执行。跨模块写入必须携带 Requirement ID、操作者、数据范围和幂等键。
