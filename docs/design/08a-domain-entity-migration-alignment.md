# SDS Phase 2补充分册：领域实体迁移对齐

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8、SDS Phase 1/2 BASELINE
> Requirement ID：附录A.1全部100项V1/V2正式需求
> Owner：SDS数据架构与数据迁移架构；业务语义Owner继承`phase-1-domain-ownership.md`
> 目标：使每个Phase 2领域数据对象都有明确的历史来源、当前实现来源、迁移策略或“不迁移”结论。

本分册是业务解释和迁移边界摘要，不以对象级复合策略代替实施契约。机器真值为`docs/traceability/domain-entity-migration-contract.json`，其人读版为`docs/traceability/domain-entity-migration-contract.md`；对象数量、来源数量和目标表映射以本轮生成结果为准。每个对象独立声明Owner、Requirement ID、目标表、来源证据、处置、转换、映射状态和Gate；`domain-object-table-map.json`提供对象到09目标表的精确机器映射。V1.8已移除COM-02、IMP-02，ACC-05转为V3；三者不得继续生成当前V1/V2实体、目标表或用户入口。已排除的历史工单/工时和目录同步对象仍只作为受控来源证据，不进入当前迁移对象集合。

## 1. 证据和使用规则

本分册不重新解析原始Excel，默认按以下顺序读取：

1. `specs/001-project-delivery-platform/evidence/data-elements/manifest.json`及结构化JSONL；
2. `specs/001-project-delivery-platform/evidence/migration/*mapping*.jsonl`及迁移摘要；
3. `legacy-data-element-business-object-mapping.md`、`project-order-migration-mapping.md`等已整理结论；
4. 实现仓库`E:\AICoding\Projects\NPDMS`提交`856d052`中的现行表和迁移；
5. 只有结构化证据不足、源Excel哈希变化或需核验Excel专有语义时才回查原Excel。

旧库和当前实现只能证明来源事实，不拥有新模型业务语义。冲突时以PRD和批准决策为准；无可靠对应关系时使用`PENDING_SOURCE_CONFIRMATION`或`NEW_ONLY`，不得按表名相似、字段后缀、最大ID或任意一条候选记录猜测。

下文表格中的`A+B`仅表示该对象存在多类来源的业务摘要，不表示一行数据同时执行多个互斥处置，也不是迁移程序输入。实际处置必须按显式契约逐来源读取。例如ServiceHandover的可证明交接字段为`CURRENT_FORWARD`，续保字段为`EXCLUDED`；MetricSnapshot的Owner事实为`REBUILD`，周报日报来源为`EXCLUDED`；DeviceCredential的新数据为`NEW_ONLY`，历史`license_key`为`PENDING_SOURCE_CONFIRMATION`；TechnicalNoticeReference的ITR来源为`EXTERNAL_SYNC`，当前本地公告为`COMPATIBILITY_ONLY`。

## 2. 迁移策略代码

| 代码 | 含义 | 目标处理 |
|---|---|---|
| `STRUCTURED` | 来源字段可证明为目标业务事实 | 归一后进入正式列，同时保留来源记录/外部键 |
| `RELATION` | 来源用于解析目标关系 | 解析成功建关系；失败写迁移问题，不创建伪关系 |
| `SNAPSHOT` | 只能证明发生时视图或历史展示值 | 进入不可变快照/兼容历史，不覆盖当前主档 |
| `EXTERNAL_SYNC` | 外部系统继续权威 | 通过稳定接口或只读增量同步形成本地副本；不以一次性迁移夺取Owner |
| `CURRENT_FORWARD` | 当前NPDMS已有可复用结构 | 使用新Flyway前向迁移/适配，不修改已执行迁移 |
| `REBUILD` | 可重建投影/汇总 | 从迁移后的Owner事实重算，不迁旧缓存/汇总值为真值 |
| `COMPATIBILITY_ONLY` | 旧数据需留查但不进入新写模型 | 只读兼容、来源载荷或隔离档案，不提供新业务入口 |
| `NEW_ONLY` | 无可靠历史来源或是新业务能力 | 新平台启用后产生；不得用无关旧表填充 |
| `PENDING_SOURCE_CONFIRMATION` | 发现候选来源但语义/键/状态未证明 | 可建导入框架，不能生成正式业务记录 |
| `EXCLUDED` | PRD明确排除或旧能力越界 | 不进入目标菜单/API/状态机/统计；必要时仅留来源证据 |

所有`STRUCTURED/RELATION/SNAPSHOT`迁移均同时写`pms_migration_source_record`和`pms_external_key_map`；未成功形成正式事实的源记录必须写`pms_migration_issue`或批准的`COMPATIBILITY_ONLY/EXCLUDED`终态。

## 3. Project Delivery

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `Project` | 旧`pm_project`；当前`proj_project` | STRUCTURED+CURRENT_FORWARD | 迁编码、名称、客户、行业、实施方式、级别、生命周期时间和来源；空名称进入待补问题 |
| `ProjectHierarchy` | 当前`pms_project.parent_id/root_id/path/depth`；旧库无等价正式树 | CURRENT_FORWARD | 旧项目默认根节点；旧项目组、名称、地区、编码不推断父子 |
| `ProjectAncestorProjection` | 当前路径字段/目标闭包投影 | REBUILD | 按迁移后邻接真值重建并记录treeVersion，不迁旧path为独立真值 |
| `ProjectTemplate` | 当前`pms_project_template`；旧`fnd_basic_prjstate`仅状态配置 | CURRENT_FORWARD+PENDING_SOURCE_CONFIRMATION | 当前模板前向对齐；旧状态配置不得冒充项目模板 |
| `ProjectTemplateMatchHistory` | 当前无可证明的等价权威历史 | NEW_ONLY / FEATURE_FORWARD_MIGRATION(PM-07) | 仅从新平台INITIAL_CREATE、SOURCE_CORRECTION、MANUAL_ADJUSTMENT命令追加；不得从现有异步操作日志反推 |
| `ProjectTask` | 旧`pm_project_task`；当前`proj_project_task` | STRUCTURED+CURRENT_FORWARD | 迁任务、计划、责任和历史状态映射；父子与依赖分离 |
| `TaskWorkBinding` | 当前ProjectTask无可证明的统一绑定字段 | NEW_ONLY | 前向初始化为`TASK_NATIVE`版本1；不得按任务名称、历史菜单或模块名猜测业务对象/组件/表单/审批绑定 |
| `TaskCompletionRule` | 当前ProjectTask无可证明的分类型完成规则 | NEW_ONLY | 与WorkBinding同版本原子生成；TASK_NATIVE使用任务自身守卫，其他类型只由新模板或批准换绑命令产生 |
| `TaskCompletionEvaluation` | 当前无独立完成判定事实 | NEW_ONLY | 新平台完成命令开始追加；不得用历史已完成状态反推目标事实、规则版本或判定快照 |
| `TaskAncestorProjection` | 当前任务path；目标闭包投影 | REBUILD | 按任务邻接关系重建，不固定深度 |
| `TaskDependency` | 当前`pms_project_task_dependency`；旧结构证据不足 | CURRENT_FORWARD | 迁当前有效依赖；旧任务先不从前后日期推断依赖 |
| `ProjectMemberAssignment` | 旧`pm_project_member`；当前项目团队/成员表 | STRUCTURED+RELATION+CURRENT_FORWARD | 解析用户、角色、有效期；旧审计与当前成员分离，同用户多角色不丢失 |
| `ProjectPortfolio` | 当前`pms_project_portfolio*`；旧`pm_project_group*` | CURRENT_FORWARD | 当前组合迁移；旧项目组只作合同技术桥，禁止迁为组合 |
| `ProjectStageSnapshot` | 旧`pm_project_state`、项目日志；当前阶段/状态表 | SNAPSHOT+CURRENT_FORWARD | 仅可证明的阶段事实/时间形成快照；未知状态隔离，不补造历史门禁 |
| `BorrowedProjectConversion` | 售前借货、SAP核销、CRM借货/RMA候选表 | PENDING_SOURCE_CONFIRMATION | 必须证明源项目、正式销售业务和处理批次后迁；否则只留来源记录 |
| `ConversionItem` | 借货项目产品/核销明细候选 | PENDING_SOURCE_CONFIRMATION | 逐项决定只读引用/派生副本；禁止按同名对象自动复制 |
| `ConversionDeviceDisposition` | SN发货/RMA/返还/项目归属事件 | RELATION+PENDING_SOURCE_CONFIRMATION | 只有完整设备事件链可决定继续借测/转入/归还；冲突逐设备隔离 |
| `MultiPhaseProjectGroup` | 无可靠旧等价对象 | NEW_ONLY | 不复用父子树、项目组合或旧项目组 |
| `MultiPhaseProjectMember` | 无可靠旧等价对象 | NEW_ONLY | 启用后按期次和有效区间产生 |
| `CrossPhaseContentReference` | 无可靠旧等价对象 | NEW_ONLY | 新期派生时保存来源版本，不回填猜测关系 |

## 4. Preparation & Solution

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `Preparation` | 当前`pms_eng_site_survey/requirement/resource_ready/briefing` | CURRENT_FORWARD+STRUCTURED | 按准备类型归入统一聚合但保留来源类型、ID、状态原值和附件引用；不得合并覆盖多次提交 |
| `ConstructionPlan` | 旧/当前项目任务计划、当前计划变更与倒排表 | STRUCTURED+CURRENT_FORWARD | 可证明的基线、计划项和变更分别迁移；统计耗时表不反推计划审批 |
| `Solution` | 当前`pms_eng_solution`及来源表 | CURRENT_FORWARD+STRUCTURED | 草稿/提交/批准/发布状态版本化映射；文件转FileArtifact引用 |
| `PreparationDynamicFormInstance` | 已有新平台`sol_dynamic_form_instance` | NEW_ONLY | 仅保留F-SOL-002已经形成的工勘表单事实；PRE-04不复用或改写该表，不迁移或双写旧`pms_eng_form_instance` |
| `DynamicFormTemplate` | 旧`pms_eng_form_template`仅作实现审计证据 | NEW_ONLY+COMPATIBILITY_ONLY | PLT新模板由新命令创建；可复制旧交互优点，但旧表、接口、页面和数据原样保留，不迁移、不双写 |
| `DynamicFormTemplateRevision` | 无可靠可迁移来源 | NEW_ONLY | 只由PLT草稿/发布命令形成，不从旧模板臆造发布修订 |
| `DynamicFormInstance` | 旧`pms_eng_form_instance`仅作实现审计证据 | NEW_ONLY+COMPATIBILITY_ONLY | PLT手工实例及受信业务Owner实例由新命令/API创建；旧实例原样保留且不成为PLT真值，不自动迁移现有候选PRE-04数据 |

## 5. Implementation Execution

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `ArrivalAcceptance` | 当前`pms_eng_arrival`；COM DeliveryScope与旧发货/装箱事实作范围对账 | CURRENT_FORWARD+RELATION | 仅迁可证明的批次身份、项目、时间、操作者引用和说明；旧设备先映射AST稳定ID，URL先转有效FileReference。旧0/1/2状态、数量、说明、发货/装箱或种子均不能单独产生ACCEPTED；缺应到范围、设备/数量、证据或差异完整性的行保留旧记录并待核对 |
| `InstallationRecord` | 当前`pms_eng_installation`；旧`pm_project_shipment`安装地址 | CURRENT_FORWARD+SNAPSHOT | 当前安装事实迁移；旧安装地址只能形成发生时位置候选，不能补造确认结论 |
| `ConfigurationCollectionResult` | 当前`pms_eng_configuration`、设备配置Log；外部采集结果 | CURRENT_FORWARD+EXTERNAL_SYNC | 保存任务/设备/结果版本/解析尝试和文件引用；不迁连接秘密 |
| `JointDebuggingResult` | 当前`pms_eng_joint_test` | CURRENT_FORWARD | 按业务任务和结果版本迁移，问题只保存引用 |
| `ImplementationRisk` | 当前`pms_eng_risk`、项目风险候选 | CURRENT_FORWARD+STRUCTURED | 风险与处置历史追加迁移；不与CUT风险混表 |
| `ImplementationQualityCheck` | 当前未发现完整质量检查/整改/复核模型 | NEW_ONLY | 启用后产生，不用到货/安装结果冒充质量复核 |
| `ImplementationReadinessSnapshot` | 到货、安装、方案、风险、质量/安全等目标事实 | REBUILD | 从迁移后Owner事实按口径生成；旧项目状态缓存不作为快照真值 |

## 6. Acceptance & Closure

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `Acceptance` | 当前`acc_acceptance` | CURRENT_FORWARD+STRUCTURED | 按验收范围、版本、结论和确认迁移；原实施证据仅引用 |
| `SatisfactionCollection` | `pm_cl_quesnaire_template_*`、`pm_cl_quesnaire_result_*`、`pm_cl_callback*`、`pm_subcontract_project_callback` | STRUCTURED+RELATION+PENDING_SOURCE_CONFIRMATION | 模板、题目、选项、答卷和评分按原版本迁移；业务对象关系只有证据完整时建立；不从回访/审批状态反推客户答案或通过结果 |
| `DeliveryArtifact` | 当前交付清单/归档/完工证明、旧基础交付模板和转包交付件 | CURRENT_FORWARD+RELATION | 文件身份、清单项、审核与归档分离；不能只迁URL而丢业务类型/版本 |
| `ProjectClosure` | 当前`acc_project_closure` | CURRENT_FORWARD | 迁已有申请/结论/时间；无法重建原门禁输入时标记历史快照不完整 |
| `ClosureGateSnapshot` | 当前闭环/交付清单/问题事实 | REBUILD+SNAPSHOT | 当前状态按新事实重建；历史只冻结可证明输入，不补造通过项 |
| `ServiceHandover` | 当前`pms_acc_maintenance_transition`中的可证明交接事实 | CURRENT_FORWARD+COMPATIBILITY_ONLY | 只迁遗留问题/持续服务交接；续保年限、续保动作和续保状态不进入新写模型 |

## 7. Cutover、Inspection & Service

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `CutoverTask` | 旧`pms_cut_task`前向迁入`cut_task` | CURRENT_FORWARD | 合法旧身份仅迁成`LEGACY_FORWARD/LEGACY_UNKNOWN`只读投影；旧类型/组网只进legacy raw字段，当前字典字段及负责人、客户、IMP快照、项目水位、人工等级、当前评估均为空；旧`0..8`不映射新流程状态，不生成设备范围/P2评估/阶段历史；损坏、软删除、项目无法解析或身份冲突行保留旧表并记录迁移问题 |
| `CutoverAssessment` | 新建`cut_assessment`；无可证明历史来源 | NEW_ONLY | 只从新平台P2问卷与人工等级命令创建；`pms_cut_risk`是旧风险/调研运行记录，不迁入评估聚合 |
| `CutoverChecklist` | 当前`pms_cut_risk`中的风险/调研候选记录；新平台CUT-03清单版本 | CURRENT_FORWARD+FIELD_LEVEL_REVIEW | 只迁可证明的任务引用、原编码/名称/类型、说明和填写事实；不得推断采集项版本、界面Schema、绑定规则、必填性、CollectionTask、自动结果、业务通过或配置缺口；新清单版本和结果引用由前向Feature产生 |
| `CutoverConfigurationRevision` | 无可靠旧来源；新平台CUT-07 | NONE_NEW+FEATURE_FORWARD_MIGRATION | 仅由CUT-07 Feature新建配置、采集项定义和绑定规则版本；类型/组网/设备复用基础平台字典，不从旧方案或风险项反推配置主数据 |
| `CutoverPlan` | 当前`pms_cut_plan` | CURRENT_FORWARD | 迁计划revision/步骤/审批引用；执行冻结已批准版本 |
| `CutoverSupportArrangement` | 当前`pms_cut_plan`中可证明的保障人员字段；缺少逐字段证据时不迁 | CURRENT_FORWARD+FIELD_LEVEL_REVIEW | 作为`CutoverPlan`从属明细；不得推导派单、状态、当前责任人或责任区间 |
| `CutoverClosure` | 当前`pms_cut_execution`中可证明的P6结果字段 | CURRENT_FORWARD+FIELD_LEVEL_REVIEW | 只迁割接前/执行/测试结果、回退说明、附件、遗留项文本和最终结果；`pms_cut_execution_step`、`pms_cut_observation`不进入当前目标，不能把步骤状态或观察状态改名迁入 |
| `InspectionTask` | 当前`pms_srv_task/execution/offline_file` | CURRENT_FORWARD | 当前巡检结构前向迁移；不从已排除维护表导入任何记录 |
| `InspectionRule` | 当前`pms_srv_rule` | CURRENT_FORWARD | 发布revision迁移；任务冻结所用版本 |
| `InspectionReport` | 当前`pms_srv_report`及外部采集报告 | CURRENT_FORWARD+EXTERNAL_SYNC | 发布版本只追加；原始结果保存受控引用 |
| `ServiceIssue` | 当前`pms_srv_issue`、ITR问题候选 | CURRENT_FORWARD+EXTERNAL_SYNC | 按来源类型区分巡检问题与ITR问题；问题Owner不因项目引用改变 |
| `ServiceStatus` | `fb_service`、`view_warranty*`、`warranty_info/change_logs`及当前维保表的客观字段 | STRUCTURED+EXTERNAL_SYNC | 只计算客观在保/在维/停产停维提示；续保动作、空间和报表全部排除 |

`pm_project_maintenance` 全表按需求方 2026-08-13 确认执行 `EXCLUDED/NO_MIGRATION`，仅保留表级排除审计，不挂到任何业务对象，不生成字段绑定。PRD 第 8.2 节“历史事实不可删除”是保存义务而非当前产品能力授权：V1/V2不建立历史工单/工时迁移对象、空壳表、菜单、查询、导出或附件入口。只有真实来源被识别并纳入已批准的`AI-MIG-000`批次时，才可把来源业务键、原状态、原责任、附件引用、审批和操作记录作为不可变来源载荷或受限迁移归档证据保存；该证据不挂接当前业务对象，也不产生用户访问契约。未来若需用户查询，必须通过独立PRD/Feature变更重新批准Owner、模型、API、权限、导出审计及来源映射。

## 8. Customer、Asset、Commerce、Resource

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `Customer` | 数据元`pm_account`候选、项目/CRM冗余、当前`pms_customer` | EXTERNAL_SYNC+CURRENT_FORWARD | CRM权威字段按sourceKey同步；临时客户显式来源，禁止只按名称合并 |
| `CustomerContact` | 数据元`pm_account_contact`、项目/CRM联系人、当前联系人表 | EXTERNAL_SYNC+CURRENT_FORWARD | 迁姓名/部门/职位/电话/邮箱/地址及来源键；主联系人是时态关系 |
| `CustomerRelationshipSnapshot` | 迁移后的客户/联系人/项目关系 | REBUILD+SNAPSHOT | 按业务发生时生成，不把当前主档反写历史 |
| `CustomerServiceLevelRevision` | 无可靠旧来源；新平台CUS-02 | NONE_NEW+FEATURE_FORWARD_MIGRATION | 仅从新平台等级变更命令生成有效区间和策略快照；不得从联系人、关系快照或客户名称推断历史等级 |
| `Device` | `fb_shipment_barcode`主SN/物料、MES/ITR、当前`ast_device` | STRUCTURED+EXTERNAL_SYNC+CURRENT_FORWARD | SN主档去重但源行不删除；权威字段保留来源版本 |
| `DeviceArchive` | 当前设备版本/配置Log、旧软件版本/安装地址/配置数据元 | CURRENT_FORWARD+STRUCTURED | 版本、配置、位置按历史/来源分表；JSON不替代高频查询字段 |
| `DeviceComponentRelation` | 配置Log解析结果、既有设备关系数据元及人工核对证据 | STRUCTURED+RELATION+PENDING_SOURCE_CONFIRMATION | 保存机框SN、槽位、板卡SN/型号、来源和生效区间；只有可证明关系进入当前/历史关系，多义记录进入待匹配且保留原始Log |
| `DeviceCurrentAssignment` | `pm_project_shipment`及当前设备项目关系 | RELATION+CURRENT_FORWARD | 仅完整事件链形成当前唯一归属；多义/区间冲突进入问题 |
| `AssetSyncSnapshot` | MES/ITR同步批次和字段差异 | EXTERNAL_SYNC+SNAPSHOT | 保存水位、来源版本、校验摘要，不覆盖平台归属事实 |
| `MaintenanceFact` | 条码维保字段、`fb_service/view_warranty*`及当前维保客观字段 | STRUCTURED+EXTERNAL_SYNC | 迁起止日期、等级、来源和规则版本；人工续保覆盖只留兼容证据 |
| `RMAReplacement` | 条码`rma_no/rmaBarcode`、RMA申请/维修报告/备件候选 | RELATION+PENDING_SOURCE_CONFIRMATION | RMA替换与附加SN关系分型；行为码未确认时不计算当前设备状态 |
| `Contract` | SAP回款、ERP订单合同关系；`fb_contract`仅发货归属 | STRUCTURED+RELATION+EXTERNAL_SYNC | 业务键为租户+公司+合同号；发货合同不得生成主档 |
| `SalesOrder` | `pm_order_data_from_erp` | STRUCTURED+EXTERNAL_SYNC | 按来源+公司+订单类型+订单号归并；冲突不取最大ID |
| `OrderLine` | `pm_order_line_from_erp` | STRUCTURED+EXTERNAL_SYNC | 订单行稳定键、数量和正负方向保留；空键/多义进入问题 |
| `DeliveryScope` | `pm_project_product_line`及明确订单行/项目关系 | STRUCTURED+RELATION | 必须保存项目、订单行、分配量和状态；缺量待补且不计统计，多项目分配防超配 |
| `Supplier` | 旧`pm_subcontract_facilitator`、当前外协/供应商候选 | STRUCTURED+CURRENT_FORWARD | 迁服务商主档和资质版本；备件供应商业务仍由外部系统承接 |
| `SubcontractRequest` | 旧`pm_subcontract_project_header/line/price/callback`、当前外协申请 | STRUCTURED+CURRENT_FORWARD | 迁申请、范围、价格版本和审批引用；不改变项目树 |
| `PaymentGate` | 旧转包付款及财务同步表 | STRUCTURED+EXTERNAL_SYNC | 迁已批准前置证据和外部结果引用；HTTP/同步成功不等于付款确认 |

## 9. Analytics、Platform、DAC、Knowledge

| 数据对象 | 来源证据/现有实体 | 策略 | 迁移落位与禁止推断 |
|---|---|---|---|
| `MetricSnapshot` | 迁移后Owner事实；旧周报/缓存/耗时统计 | REBUILD+EXCLUDED | 指标按口径版本重算；周报日报明确排除，不迁为指标真值 |
| `PortfolioView` | ProjectPortfolio+MetricSnapshot | REBUILD | 只读重建并按权限裁剪 |
| `Todo` | 当前统一待办、Activiti运行/历史任务 | PENDING_SOURCE_CONFIRMATION+CURRENT_FORWARD | 只迁仍有效且能回指业务Owner节点的待办；流程任务完成不等于业务完成 |
| `AuthorizationGrant` | 当前通用授权、OA引用、旧License授权候选 | CURRENT_FORWARD+PENDING_SOURCE_CONFIRMATION | 完整授权码不迁明文；不能证明用途/范围的license只留隔离证据 |
| `ChangeRequest` | 当前计划变更及治理动作 | CURRENT_FORWARD+PENDING_SOURCE_CONFIRMATION | 只迁能证明目标对象/版本/审批的变更；计划变更不自动升级为通用变更 |
| `FileArtifact` | 当前业务文档/版本、交付件、模板、外协文件及旧文件元数据 | CURRENT_FORWARD+STRUCTURED | 内容、哈希、版本、业务引用分别迁；缺正文/哈希进入问题，不复制多份身份 |
| `AuditRecord` | 旧项目日志、Activiti评论/历史、当前操作审计 | SNAPSHOT+CURRENT_FORWARD | 旧证据按`LEGACY_*`来源只读导入；不得伪装为新平台审计或改业务状态 |
| `DeviceCredential` | 当前未发现安全等价来源；旧`license_key`用途不明 | NEW_ONLY+PENDING_SOURCE_CONFIRMATION | 只有可证明为连接凭证且可安全重加密的记录才可专项迁移；否则不导入 |
| `CredentialGrant` | 无可靠旧五元组授权 | NEW_ONLY | 创建人默认权限和后续五元组授权从新平台产生 |
| `CollectionTask` | 外部采集平台任务、当前配置/巡检/割接入口候选 | EXTERNAL_SYNC+NEW_ONLY | 历史任务只在外部稳定键和回调证据完整时导入引用；临时密码永不迁移 |
| `CollectionResultReference` | 外部采集结果、配置Log/报告文件 | EXTERNAL_SYNC+RELATION | 迁外部对象键、文件引用、hash和消费版本，不把大结果复制入业务表 |
| `TechnicalNoticeReference` | ITR/`prob_*`及当前本地公告实现 | EXTERNAL_SYNC+COMPATIBILITY_ONLY | V2以ITR同步为权威；旧本地公告只读隔离，不能获得本地发布/停用能力 |

### 9.1 08数据模型内部/建议实体补充

这些实体未在100项Phase 2映射中独立列名，但已在08数据模型中承担正式明细、历史、投影、关系或支撑职责，因此同样纳入迁移覆盖。建议实体仅登记新建边界，不因此升级为PRD承诺。CUT领域的保障人员安排是`CutoverPlan`从属数据，不是独立任务聚合；WO-06后置到工单领域且不进入当前迁移覆盖。V1.8移除的对象不得以“建议实体”名义回流。

| 数据对象 | 来源摘要 | 迁移边界 |
|---|---|---|
| `DeliveryEvidence` | 当前`pms_eng_deliverable`及各实施业务证据 | 前向迁移证据身份、不可变版本、文件引用和上传结果；F-IMP-002只锁定RECEIPT且来源可解析到到货记录的EXE-01切片，复用已映射PLT FileReference而不重复下载；旧归集状态不推导ACC accepted/archived，其他类型留待IMP-01 Owner Feature |
| `DeviceAssignmentHistory` | 旧项目设备关系 | 仅从可解析的设备、项目、时间和转移证据构造不重叠区间 |
| `DeviceAncestorProjection` | 归属历史+项目树 | `REBUILD`；按树版本和归属版本重建，不形成多重归属 |
| `MetricDefinition` | 【建议】无批准历史口径来源 | `NEW_ONLY`；不得按旧报表名猜测公式，建议模型获批后才创建版本 |
| `NoticeBusinessReference` | 当前公告检查/业务关系 | 仅兼容可解析引用；不得获得本地公告发布权 |
| `DispatchAttempt` | 新平台下发过程 | `NEW_ONLY`；历史外部日志无稳定任务键时不补造 |
| `CallbackRecord` | 新平台接收回调 | `NEW_ONLY`；不得从业务结果行反推历史回调 |

## 10. 明确排除与不得误迁移

| 旧数据/能力 | 处理 |
|---|---|
| `pm_project_weekly*`、日报/周报 | `EXCLUDED`；可留历史查阅，不生成正式MetricSnapshot |
| 维保档案、续保年限/动作/空间、续保率及过保空间报表 | `EXCLUDED`；仅客观设备MaintenanceFact和服务交接可结构化迁移 |
| 工单时效、平台通用割接时效 | `EXCLUDED`；不生成目标阈值、考核字段或状态 |
| 历史工单/工时用户查询、导出和附件入口 | V1/V2不建设；`AI-MIG-000`仅在批准批次内保存不可变来源载荷或受限归档证据，不形成领域对象、目标表映射或用户访问契约 |
| 备件采购、库存、出入库 | `EXCLUDED`于本平台；只迁外部业务引用、RMA关系和对账键 |
| 本地技术公告发布/停用/治理 | V1/V2 `COMPATIBILITY_ONLY`；V3是否启用另行批准 |
| Activiti任务/通知送达/HTTP 2xx | 仅流程/传输证据，不迁为业务完成状态 |
| 旧密码、私钥、Token、用途不明license | 不迁明文；无法安全分类和重加密时只保留受控非秘密证据 |

## 11. 迁移顺序和Gate

```text
平台身份、公司/部门和字典映射
  -> Customer / Project / ProjectMember
  -> Contract / SalesOrder / OrderLine / DeliveryScope
  -> Device / DeviceAssignment / RMA / MaintenanceFact
  -> Preparation / Solution / ProjectTask
  -> Implementation / Cutover / Inspection / Acceptance / Resource
  -> File / Todo / Audit / Integration reference
  -> Project/Task ancestor、Readiness、Fulfillment、Metric等投影重建
```

领域迁移切换必须同时满足：

1. 只有发布包含历史迁移或数据切换时，`AI-MIG-000`才作为Release前置门禁；它须在真实批次中完成范围、水位、程序、校验、演练、对账和回退验证，迁移或切换只能在批准窗口内执行；普通功能发布记为`NOT_APPLICABLE`；
2. 本分册每个数据对象均有策略，所有`PENDING_SOURCE_CONFIRMATION`已关闭或被批准为不迁移；
3. 每条源记录形成目标事实、迁移问题或批准终态之一，不得静默丢失；
4. 每个领域分别校验数量、唯一键、关系孤儿、状态/字典、金额/数量、文件hash和权限范围；
5. 可重建投影在Owner事实迁移完成后生成，并以数据水位验证，不导入旧缓存冒充真值；
6. 旧库保持只读，新旧库物理分离，不执行跨库SQL；失败批次在新库隔离/作废后以新batch重做。

## 12. 当前结论

Phase 2的全部显式数据对象已获得迁移策略入口；核心链具有结构化证据，当前实现领域实体采用前向迁移，证据不足或新增能力已明确隔离。本文保持`BASELINE ADDENDUM`；实际字段级映射、源表最终水位和生产迁移执行`DEFERRED_TO_AI_MIG_000`，应用Schema前向迁移发布`DEFERRED_TO_FEATURE_INTEGRATION`，不能用本文替代生产迁移放行。
