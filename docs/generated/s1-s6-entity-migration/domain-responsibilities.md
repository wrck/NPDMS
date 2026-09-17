# S1～S6 旧实现领域职责划分

## 1. 范围、依据与交付边界

- 日期：2026-09-17。
- 仓库：`wrck/NPDMS`；实施分支：`codex/s1-s6-business-entity-migration`。
- 取证代码基线：`b2f0045e0bbfb767c086249b4d25c43a469cada3`；分支起点：`d13798671f1ce0ca44f5d17a88c92fe579f92eb9`。
- 本轮任务：先从旧实现确定业务职责归属，再进入领域内的聚合、实体及表结构设计。本轮不执行后面三个设计步骤，不继续复制业务实现。
- 依据：锁定提交下的旧建表迁移、服务代码及依赖、目录、阶段任务配置。按需求方指示不比对 PRD，不借此改写现有业务规则、权限或状态语义。
- 本文是代码取证后的职责划分结果，不是正式规格替代品，不修改既有 Owner 编码、运行注册或认领关系。

本文中的 D01～D11 是业务职责分类标识，T01～T02 是技术支撑边界。它们不等于已批准的 Maven 模块、微服务、限界上下文、聚合或表前缀；物理组织须在后续领域内建模后确定。

已列举 17 份历史建表迁移中的 69 个不同旧表名，并结合当前承接代码识别边界。69 不是运行数据库表总数，也不是全仓库所有迁移中的 PMS 表总数。本轮没有连接数据库或完成全仓库逐方法、逐字段、运行路径审计；不能用本表直接宣布某模块具备迁移准入或验收条件。

需求分析、现场工勘仍按需求方确认排除重复迁移，但要保留其对其他领域提供事实的边界。旧表中未证明已承接的其他业务分支不能随整表排除。

## 2. 划分依据

职责归属以“谁维护业务对象、解释业务规则、作出业务结论、承担后续变更责任”为依据，而不是以页面位置、阶段编号或表名为依据。

同一业务的创建、修改、提交、审批结果处理、发布、版本与历史属于同一业务职责链；不能将这些动作拆成互不相干的模块。包含多种业务的旧表，也不应机械地对应一个新聚合。

S1～S6 是业务执行顺序。业务可以跨阶段使用，领域结果可以被多个阶段消费，阶段编排不因此获得业务表写权限。下文的“归属”描述后续实现的责任边界，不授权现在跨模块访问旧表。

## 3. 业务职责图

| 标识 | 业务职责域 | 负责的业务与结果 | 不负责的业务 | 当前实现位置与证据 |
|---|---|---|---|---|
| D01 | 项目治理与交付编排 | 项目身份、项目组合、项目树、成员与项目范围；模板、任务、依赖、排期、计划变更、执行轮次与进度；消费业务事实进行阶段推进和正常闭环；启动协调 | 不生产需求、方案、签收或验收的专业结论；不把 WorkBinding 当业务实体；项目组合不等于项目树 | `project/service` 下 project、portfolio、projecttree、projectmember、projecttemplate、projectplan、runtimegraph、normalclosure 等；V5/V7/V8/V21/V22/V23/V47 |
| D02 | 工程分析与实施准备 | 需求与接口规划、现场条件、有效工期事实、实施方案及来源基线、工程交底、资源就绪判断、项目级技术风险与公告预检查；这些业务的语义模板和准备表单 | 不拥有采购/外包履约、设备主档、项目排期和项目生命周期；不把发布公告与项目检查混为同一业务 | `engineering/service` 下 requirement、sitesurvey、constructionplan、solution、briefing、preparation、resource、risk、announcementcheck、doctemplate、formtemplate、forminstance；V10/V27/V30/V36 |
| D03 | 资源供应与外包协同 | 外包申请、领料、外采、换货、授权申请与借货准备；资源需求、申请处置、履约反馈与业务来源关系 | 不因入口来自工勘就归工勘所有；不直接改变商务交付范围、设备资产状态或业务任务完成状态；设备授权不是用户权限授权 | 旧实现仍在 `engineering/service` 下 outsource、materialrequisition、externalprocurement、materialexchange、authorization；V25/V30。当前 outsourcing 模块只有骨架，不能据模块名宣称已承接 |
| D04 | 工程实施与现场交付 | 到货核验、签收和差异、硬件安装、配置调试、业务联调、实施问题整改；现场督导、培训交付行为的职责归口 | 不拥有商务订单或设备主数据，不作项目初验/终验结论，不管理割接专有回退生命周期 | `engineering/service` 下 arrival、arrivalacceptance、installation、configuration、jointtest、issue；V10。督导和培训当前取证仅定位到阶段任务配置，独立业务实现待定位 |
| D05 | 割接 | 割接任务、调研清单、风险评估、割接方案、分级审批、执行/回退、稳定观察、遗留项与割接收口 | 不等于项目 WBS 任务，也不等于普通实施方案；割接完成不自动等于项目完成 | `cutover/service` 下 task、taskv2、risk、checklist、plan、approval、closure、spare 等；V12 |
| D06 | 验收与交付评价 | 完工证明与客户确认、初验/终验、验收报告与结论、验收交付件完整性判定、满意度结果及其评价处置 | 不生产安装/培训事实，不接管项目关闭状态，不因存在附件就判定验收通过 | `project/service` 下 completioncertificate、acceptance、acceptancereport、acceptancescope、deliverablechecklist、satisfaction；V17 |
| D07 | 交付资料与归档 | 汇集和引用各领域已形成的交付件、归档目录、资料版本及归档操作 | 不拥有被归档文件代表的业务结论；不重写原业务历史；不负责通用文件字节存储或安全扫描 | `engineering/service/deliverable`、`project/service/archivedocument`；V10/V17 |
| D08 | 运维服务与服务保障 | 巡检任务、规则、在线/离线执行、报告、服务问题整改、服务承诺；转维保的服务承接责任 | 不拥有客户身份、原验收结论或设备主数据；不依据只读维保查询推定续保写权限 | `service/service` 下 srvtask、srvrule、inspectionrule、srvexecution、srvofflinefile、srvreport、srvissue；旧转维保、维保状态和服务等级表须继续分解混合职责；V14/V17/V21 |
| D09 | 客户与联系人 | 客户/联系人身份、联系关系及客户分类属性 | 不拥有项目成员任命、项目数据授权、商务合同或巡检/服务承诺的执行结果 | 当前 `customer/service` 下 customer、contact、history、location、query、security 等；另有 project 模块中的旧 customer/customercontact；V3 |
| D10 | 商务合同与交付范围 | 合同、订单及项目分配的商务交付范围，提供可追溯的范围事实 | 不以订单状态代替现场签收；不因采购存在金额就接管全部资源申请；不接管客户主数据 | `commerce/service` 下 contract、scope、authority、authorization、sync；到货应用使用 DeliveryScopePort 消费商务范围 |
| D11 | 产品资产与技术资料 | 设备/SN、产品型号、版本、位置、配置档案、设备相关技术公告和停产停维资料；设备维保事实查询 | 不拥有项目技术处置、实施完成、采购批准、验收结论；维保查询不是续保命令 | `asset/service` 下 device、equipment、version、location、configurationlog、warranty 等；旧公告位于 engineering；V6/V30 |

### 技术支撑边界

| 标识 | 边界 | 负责 | 明确不负责 |
|---|---|---|---|
| T01 | 平台通用能力与流程执行 | 通用文件、表单渲染、扩展字段、版本支撑、审计、幂等、事件发件箱、业务视图装载；BPM 的通用流程执行 | 业务正文含义、领域审批生效规则、领域状态变更和完成结论仍由对应业务负责。现有工程文档模板不是因可渲染就整体转归平台 |
| T02 | 外部集成协同 | 连接、来源映射、同步批次、游标、幂等、重试、传输结果和对账技术证据 | 接收数据的业务领域校验并决定业务状态；发送成功、通知送达、HTTP 成功均不能自行提升为业务完成 |

T01 对应现有 platform 的 audit/command/dynamicform/entity/file/outbox/businessview 等能力及 BPM 执行机制；T02 对应 integration 的 sync/governance 等技术实现。统计与看板是上述领域的读取投影，不另外成为业务事实写入者。

## 4. 旧表到职责的逐项登记

这里登记的是来源，不是新表设计。每个旧表只登记一个主审查归口；混合职责在备注中拆开，不允许据此建立多领域共享写入。

| 主归口 | 已核实旧表名 | 来源迁移 | 边界说明 |
|---|---|---|---|
| D01 | `pms_project` | V5/V7 | 身份和治理归项目；其中合同、发货、来源字段必须按商务/集成事实区分，不把旧宽表整体扩为新聚合 |
| D01 | `pms_project_team_member`、`pms_project_tree_change_batch` | V7 | 项目任职与项目树变更；不替代客户联系人或系统用户 |
| D01 | `pms_project_task`、`pms_project_task_dependency`、`pms_project_phase_template`、`pms_project_phase`、`pms_project_risk` | V8 | 编排、排期和治理风险；不代替专业领域结果 |
| D01 | `pms_project_portfolio`、`pms_project_portfolio_member`、`pms_project_portfolio_rule` | V21 | 项目组合独立于项目父子树 |
| D01 | `pms_team_batch_change`、`pms_team_batch_change_item` | V22 | 项目任职批量变更，不是通用用户管理 |
| D01 | `pms_schedule_backward`、`pms_schedule_backward_item` | V22 | 倒排建议与阶段排期，不等于有效工期事实 |
| D01 | `pms_plan_change_request`、`pms_plan_change_phase_snapshot`、`pms_project_governance_action` | V23 | 计划变更和生命周期治理 |
| D01 | `pms_project_template` | V47 | 业务编排模板，不是表单模板或文档模板 |
| D01 | `pms_acc_project_closure` | V17 | 虽有 acc 前缀，核心是项目收口审批，应与 normalclosure 对照而非另造关闭入口 |
| D02 | `pms_eng_site_survey` | V10 | 现场工勘已确认承接，本轮不重迁；只检查跨域事实边界 |
| D02 | `pms_eng_requirement` | V10/V36 | 同表 BUSINESS 与 INTERFACE；不能将未证明承接的接口规划随需求分析整体排除 |
| D02 | `pms_eng_solution`、`pms_eng_solution_source` | V10/V36 | 实施方案及引用来源，不取得来源对象的写权限 |
| D02 | `pms_eng_resource_ready` | V10 | 消费资源、批准、窗口等事实作就绪判断，不拥有资源供应全生命周期 |
| D02 | `pms_eng_briefing` | V27 | 技术交底内容、来源快照、审核发布；不是启动会或通用文件 |
| D02 | `pms_eng_form_template`、`pms_eng_form_instance` | V27 | 准备业务表单含业务状态；通用渲染可以复用，业务责任不能上交给通用表单引擎 |
| D02 | `pms_eng_risk`、`pms_eng_announcement_check` | V30 | 项目级技术风险与命中后的处理；CRM 同步状态另属 T02 技术证据 |
| D02 | `pms_eng_doc_template`、`pms_eng_doc_template_version` | V36 | 需求/方案的章节、裁剪、适用性语义归工程分析；通用渲染和文件服务归 T01 |
| D03 | `pms_eng_outsource_request`、`pms_eng_material_requisition`、`pms_eng_external_procurement`、`pms_eng_material_exchange` | V25 | 外包/领料/外采/换货申请与协同；外部系统权威值和传输状态须分别解释 |
| D03 | `pms_eng_authorization` | V30 | FORMAL/TEMPORARY/LOAN；申请和借货准备与资产实际授权事实分开，不是访问控制 |
| D04 | `pms_eng_arrival`、`pms_eng_installation`、`pms_eng_configuration`、`pms_eng_joint_test`、`pms_eng_issue` | V10 | 实施事实和问题处置，不替代设备主档、商务范围或客户验收结论 |
| D05 | `pms_cut_task`、`pms_cut_risk`、`pms_cut_plan`、`pms_cut_execution`、`pms_cut_observation` | V12 | 独立割接责任链；项目只编排与消费其结果 |
| D06 | `pms_acc_completion_certificate`、`pms_acc_acceptance`、`pms_acc_deliverable_checklist` | V17 | 客户确认、初终验与验收完整性判定；不把培训和文件存储吸收到验收里 |
| D07 | `pms_eng_deliverable`、`pms_acc_archive_document` | V10/V17 | 资料归集、来源引用、归档，不夺取原业务结论的责任 |
| D08 | `pms_srv_task`、`pms_srv_rule`、`pms_srv_execution`、`pms_srv_offline_file`、`pms_srv_report`、`pms_srv_issue` | V14 | 运维任务、规则及服务闭环；离线文件的业务解析结果不是通用文件对象 |
| D08 | `pms_srv_maintenance`、`pms_acc_maintenance_transition` | V14/V17 | 服务保障审查归口；验收前置事实归 D06，设备维保档案查询归 D11；生效/续保命令的最终写入权威待完整调用链审计 |
| D08 | `pms_customer_service_level` | V21 | 同表含分类标签、响应时限和主动服务；分类属性与服务承诺须分解，不能等同一个客户等级字段 |
| D09 | `pms_customer`、`pms_customer_contact` | V3 | 客户与联系人主数据，不因旧代码处于 project 目录归入项目核心 |
| D11 | `pms_equipment`、`pms_equipment_version`、`pms_equipment_config_log` | V6 | 设备身份、历史与配置档案；引用工程活动不改变事实主责 |
| D11 | `pms_eng_announcement` | V30 | 公告无 project_id，以型号、影响版本和有效期描述技术资料，与项目预检查分开 |
| T02 | `pms_project_sync_batch`、`pms_project_sync_detail` | V5 | 同步过程记录；项目对象的验证与落账仍归 D01 |

计数：D01 20、D02 12、D03 5、D04 5、D05 5、D06 3、D07 2、D08 9、D09 2、D11 4、T02 2，共 69 个不同表名。D10、T01 有已读取代码/接口边界，但在这份历史表清单中不虚构对应表名。

满意度存在于当前 `project/service/satisfaction`；启动会、现场督导、培训存在于阶段配置。不能为了让表格齐全，编造它们的旧 PMS 表名，也不能因为本表未列出就排除这些业务。

## 5. 必须先拆清的职责冲突

### 5.1 工程交底、启动会和文档平台

旧 BriefingService 的内容、模板/前序快照、审核、发布属于 D02。启动会的分工、目标共识属于 D01。文件生成、存储是 T01 提供的能力；正式资料归集是 D07。三者可以关联，不能因都输出文档而合并成一个业务。

`b2f0045e` 的工程交底独立副本保留，不回滚、不继续扩张、不作为后续“一旧表一新实体”的建模范式。`sol_engineering_briefing` 的命名和结构不能倒逼本轮领域划分。后续先在 D02 内审查交底与方案、准备、来源版本的关系，再判断副本怎样调整；不修改已执行迁移，不用未验证的部署假设决定迁移版本处置。

### 5.2 有效工期、施工排期、实施方案和割接方案

ConstructionPlanApplicationService 实际包含 DurationRules、初始有效工期和冻结修订，并消费 ProjectEndDateApi。旧 schedule_backward 与 plan_change 处理阶段排期与调整，归 D01。前者作为 D02 的工程工期事实提供方，后者消费事实形成项目计划；项目截止约束仍由项目负责。

施工计划办理可能跨这两类职责，但不能把一个 UI 办理过程直接定义成跨域巨型聚合。实施方案技术内容与批准基线归 D02；割接步骤、回退和观察方案归 D05。名称都含“计划/方案”不是合并依据。

### 5.3 就绪与资源供应

D02 判定现场准备是否就绪；D03 管理外包、采购、领料、换货等申请与反馈。工勘发现资源缺口可以触发申请，但工勘不获得供应业务表的写权。资源申请批准不等于到货，资源就绪不等于实施完成。

### 5.4 公告、项目预检查、风险和问题

公告主数据的型号、版本、生效期归 D11；某项目是否命中及怎样处理归 D02。项目风险归 D01，工程技术风险归 D02，安装调试问题归 D04，割接风险归 D05，巡检问题归 D08。允许形成联合视图，不建立一个绕过各自状态规则的通用风险写入口。

### 5.5 到货、资产和验收

ArrivalAcceptanceApplicationService 通过 DeliveryScopePort、DeviceScopeFactPort、ProjectQualificationPort、FileArtifactFactPort 消费外部事实，并形成自己的签收/差异记录。这分别对应 D10、D11、D01、T01 与 D04 的责任边界。

到货验收是对到货与交付范围的核验，项目初验/终验是 D06 的客户验收结论。不得把 D04 移入 D06 仅因为名字中都有“验收”；也不得由 D04 直接改商务范围以消除差异。

### 5.6 验收、闭环、归档和转维保

D06 生产验收及交付评价事实；D01 在检查项目条件、有效业务事实和流程结果后管理项目关闭。NormalClosureApplicationService 已体现项目侧条件检查、快照和真实 BPM 结果处理，不能另造不复核事实的关闭通道。

D07 负责资料引用与归档，不能以“文件上传完毕”代替验收或闭环。旧 maintenance_transition 同时含 acceptance_id、生效、到期和续保，必须在 D06 的验收前提、D08 的服务承接、D11 的设备保障档案之间拆清责任。已读取的 DeviceWarrantyQueryService 只提供查询，不能据此判定续保写入者；相关写命令与外部权威来源继续列为待审项。

### 5.7 客户服务等级、业务表单与通用机制

旧 customer_service_level 的分类标签与响应时限、主动服务并非同一职责。D09 负责客户分类含义，D08 负责服务承诺；对应配置与写入契约尚需细审，不能直接复用一个字段覆盖两者。

工程文档模板的 REQUIREMENT/SOLUTION 分类、章节裁剪和适用条件属于 D02。准备表单实例还拥有提交、审核、驳回等业务语义，不能仅改表名就转给 T01。平台的表单/扩展/文件/版本机制可共享，业务内容和完成判定不共享所有权。

## 6. S1～S6 与职责的关系

这里依据 `scripts/revise_fproj009_sample_tasks.py` 的 WORK/tokens 配置，而不是重新比对 PRD。配置中的 DRAFT 不创建实体或完成事实，也不能作为实现完成证据。

| 阶段 | 已识别工作 | 职责归口与跨域输入 |
|---|---|---|
| S1 | 现场工勘、需求分析、KICKOFF | 前两项 D02，按既有确认不重迁；KICKOFF 的项目启动协调归 D01。INTERFACE 分支须独立核对 |
| S2 | PLN01 施工计划制定 | D01 管排期/变更/执行计划，消费 D02 有效工期及项目截止约束；具体办理拆分不先假设新聚合 |
| S3 | PLAN 实施方案编审 | D02 负责技术内容、来源与批准基线，消费资产和资源等事实 |
| S4 | EXE01～EXE04、SERVICE | D04 负责到货、安装、调试、联调、现场督导；消费 D03/D10/D11 的供应、范围、资产事实。D05 仅在实际割接业务适用时接入 |
| S5 | ACC01、INITIAL、ACC02、FINAL | ACC01 的培训交付职责归 D04；初终验与满意度评价归 D06，消费培训及实施证据，不复制其写模型 |
| S6 | CLO02 | D01 管项目闭环，消费 D06 验收评价、D07 资料及适用服务承接事实；不将整个闭环归入验收表 |

KICKOFF、SERVICE、ACC01 目前只确认了配置职责和相关资料线索，未在已读范围确认完整独立业务链。它们需要继续定位原实现；找不到时应登记“待定位/需补齐”，而不是复制通用任务或上传页面后称为业务迁移完成。

## 7. 现有承接代码的处理

| 当前代码 | 本轮处理结论 |
|---|---|
| 需求分析、现场工勘实体分离 | 保留已确认成果，只说明 D02 与消费方的职责；不重新迁移，不把排除范围扩到未核实的接口规划 |
| 工程交底 entity 副本 | 暂停继续接入，作为 D02 的实现候选审查；不据已建新表冻结聚合边界 |
| constructionplan/DurationChange | 已定位有效工期职责，不将其误报为完整施工排期模块 |
| arrivalacceptance 应用与命令服务 | 已有签收/差异/证据设计及跨域端口；源码中的生产装配待接通说明仍须核验，不因存在新类宣布可投用，也不另造重复模型 |
| project/normalclosure | 项目治理域的承接候选，保留真实 BPM 和前置事实复核，不以 acc 表前缀重新归属 |
| project/acceptancereport、satisfaction | 按 D06 对照既有实现继续审查；当前目录不等于业务领域归属 |
| asset/warranty | 已读范围为维保事实查询；写入者、续保和转维保语义未确认，不扩大查询接口的责任 |
| pms-module-outsourcing | 递归目录中只有 POM 和 package-info；不宣称该模块已承接旧外包链 |

## 8. 后续领域内审计与建模顺序

领域职责 → 领域内业务能力和旧行为清单 → 生命周期及事务不变量 → 聚合边界 → 实体/值对象/引用 → 表结构与前向承接 → 独立业务页面/API → 模板、任务、阶段消费者。

进入某个迁移单元前，必须补齐其完整旧 Controller、Service、DO、Mapper/XML、API、页面、定时任务/事件/回调及直接消费方审计，列出全部操作、权限、状态、并发、附件/版本、外部副作用和来源保护。本文只解决前面的职责归属，不替代这一步。

同一能力若分散在旧类和已有新类，先合并行为审计清单，再决定直接复用、复制增强、留存历史或补齐缺失；不能重新复制出第三套事实源。共用技术能力通过既有接口复用；新业务运行路径不得依赖旧业务实体或借用旧表写入。

后续仍按单一业务迁移单元独立中文提交；提交前重读目标 HEAD，保留并行变更；提交后核验旧类、旧页面、旧 API、旧表与旧入口没有被修改。需求分析和现场工勘的既有成果继续保留。本轮文档提交不占用任何模块“迁移完成”的名额。

## 9. 取证索引与未执行项

以下路径均相对于仓库根，读取基线见第 1 节。SQL 主要用于来源及职责识别，不据历史 DDL 判断运行库当前结构。

| 证据 | 仓库路径或读取范围 |
|---|---|
| 17 份旧建表迁移 | `sql/migrations/` 下 V3__pms_customer_and_contact、V5__pms_project_master_sync、V6__pms_asset_equipment、V7__pms_project_tree_and_team、V8__pms_project_wbs_phase_risk、V10__pms_engineering_tables、V12__pms_cutover_tables、V14__pms_service_tables、V17__pms_acceptance_tables、V21__pms_portfolio_and_service_level、V22__pms_batch_change_and_schedule、V23__pms_plan_change_and_governance、V25__pms_outsource_material_procurement_exchange、V27__pms_engineering_briefing_and_form、V30__pms_eng_risk_announcement_authorization、V36__pms_doc_template_structured、V47__pms_project_template，后缀均为 `.sql` |
| 阶段工作配置 | `scripts/revise_fproj009_sample_tasks.py`，1～180 行；代码检索只作定位，随后按锁定提交读取 |
| 实施方案行为 | `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/solution/SolutionServiceImpl.java`，1～190 行 |
| 有效工期边界 | 同工程 service 目录下 `constructionplan/ConstructionPlanApplicationService.java`，1～160 行及 constructionplan 目录 |
| 签收依赖与职责 | 同工程 service 目录下 `arrivalacceptance/ArrivalAcceptanceApplicationService.java`，1～160 行；`ArrivalAcceptanceCommandService.java`，1～120 行及目录 |
| 业务文档模板 | 同工程 service 目录下 `doctemplate/DocTemplateService.java`；V36 表结构 |
| 项目闭环边界 | `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/normalclosure/NormalClosureApplicationService.java`，1～125 行及目录 |
| 设备维保查询 | `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyQueryService.java`，全文及目录 |
| 当前模块分布 | project、engineering、cutover、service、asset、customer、commerce 的相关 service 目录；platform-api、integration 根目录；outsourcing 模块完整递归目录 |
| 工程交底旧实现及副本限制 | `docs/generated/s1-s6-entity-migration/briefing.md`；其记录的旧 Controller/VO/Service/Mapper/DO/API/页面和 V27/V218 已在前序取证，b2f0045e 仅增加独立副本，旧来源未改 |

已完成清单内部核对：69 个表名无重复主归类。该核对只验证这份人工取证清单的内部一致性，不表示自动全库扫描通过。

未执行：运行数据库结构盘点、剩余迁移文件的全量表名扫描、所有模块逐字段/逐方法/装配调用链核验、完整构建、业务测试和浏览器验收。本轮不创建新业务类、表、接口或页面，不执行数据库迁移，不修改运行配置，不晋级任何业务验收状态。
