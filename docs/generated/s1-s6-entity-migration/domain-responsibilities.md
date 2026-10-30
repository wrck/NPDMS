# S1～S6 旧实现到 PRD 既有领域的归属映射

## 1. 本轮纠正与依据

- 仓库：`wrck/NPDMS`；实施分支：`codex/s1-s6-business-entity-migration`。
- 本轮读取基线：`1e7025a5e65b1cd705697bcd7157536f2c10fb21`；原代码取证基线：`b2f0045e0bbfb767c086249b4d25c43a469cada3`。
- 需求方已明确：领域在 PRD 中已有划分。本轮工作是将旧实现映射回既有领域，不是重新划分领域。
- 撤销上一版自定义的 D01～D11、T01～T02 作为当前归属依据；原内容由 Git 历史保留，不形成第二套领域目录。
- 本文是迁移审计工作记录，不是新的 PRD、SDS、Owner 决策或模块设计。不修改既有正式领域编码、领域边界、业务规则和版本范围。

权威输入：

1. [PRD V1.8](../../baseline/prd-v1.8.md)：§1.4.3 范围、Owner 与版本治理；§3.5 领域边界与数据 Owner；§3.6 事件；§3.7 不变量，以及具体业务所在章节。
2. [已确认的 PRD-derived 领域 Owner 映射](../../design/phase-1-domain-ownership.md)：§1 判定规则、§2 13 个领域与 Requirement 的映射。
3. [现有领域模型](../../design/02-domain-model.md)、[现有数据 Owner 矩阵](../../design/02c-data-ownership-matrix.md)：仅用于解释既有 Context 和事实写入边界，不能反向覆盖 PRD。
4. 旧 Controller、Service、DO、Mapper/XML、API、页面及消费者：用于还原真实已有行为、约束和副作用，不用于重定义领域 Owner。

此前“不比对 PRD、先基于代码迁移”不解释为可以重新定义领域。本轮只用 PRD 及已确认映射锁定领域和权威边界；旧功能盘点仍以代码为依据，不展开全量 PRD 功能补建，不顺便改造业务规则。发现真实冲突时登记差异，保留旧实现，只阻断依赖该冲突的新功能，不以旧代码覆盖 PRD，也不把旧功能静默删去。

## 2. 沿用的 13 个领域 Owner

下表摘录既有映射用于定位，不重新作出划分。领域、Requirement 前缀、Bounded Context、Maven 模块、包路径和聚合是不同层次；它们不要求一一对应。

| 既有 Owner | 职责定位 | 已确认正式需求归属 |
|---|---|---|
| PROJ | 项目治理与交付编排 | PM-01～PM-11、PROJ-12、INT-01 |
| SOL | 工前准备、施工计划与实施方案 | PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01 |
| IMP | 实施执行与实施证据 | EXE-01～EXE-06、IMP-01 |
| ACC | 培训、验收、满意度、归档、闭环与静态服务交接 | ACC-01～ACC-04、ACC-06、CLO-01～CLO-02 |
| CUT | 割接任务、评估、清单、方案、审批、闭环及配置 | CUT-01～CUT-10 |
| SRV | 巡检服务与设备服务状态 | INS-01～INS-09、SRV-01 |
| CUS | 客户、联系人、客户关系与服务等级 | CUS-01～CUS-04、INT-03 |
| AST | 设备与资产档案、配置 Log、归属及保障基本事实 | EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06 |
| COM | 合同订单同步副本与项目交付范围分配 | COM-01 |
| RES | 服务商、转包、价格与付款门禁 | RES-01、SUB-01～SUB-05、INT-07 |
| ANA | 项目状态统计和组合经营分析 | RPT-02、ANA-01 |
| PLT | 平台公共能力、身份授权、文件、变更、通知和采集编排 | PLT-01～PLT-02、AUT-01～AUT-02、CHG-01、NFR-01～NFR-03、INT-05、INT-09、INT-10、INT-12 |
| KNO | 技术公告引用、版本影响和知识事实同步 | INT-04 |

CLO 是闭环业务职责及需求前缀，其正式 Requirement Owner 为 ACC，不新增第十四个顶层领域。Inspection、Service Operations 是 SRV 内部的既有 Context；Device Access & Collection 对应 PLT 的 INT-12，不另造业务 Owner。集成适配器的物理位置不决定需求归属；INT-01/02/03/04/12 等按既有映射分别归属实际领域。

## 3. 对上一版的具体纠正

| 事项 | 正确归属与边界 | 本轮处理 |
|---|---|---|
| 施工计划与有效工期 | PLN、PRE 属 SOL；PROJ 持有项目、节点、执行编排和项目状态 | 不再将施工计划整个归入 PROJ，也不将两个领域合成一个跨域聚合；具体更新通过既有契约衔接 |
| 培训 | ACC-01 归 ACC | 不因培训发生在现场或由工程人员办理而归入 IMP |
| 闭环检查、快照、审批及最终记录 | CLO-01/02 归 ACC 内闭环职责；PROJ 通过公开 Writer 持有项目退出与生命周期字段 | `project/normalclosure` 的目录不改变业务 Owner；不增设第二个关闭入口，PM-10 异常关闭继续归 PROJ |
| 交付件 | IMP 生产实施原始证据；ACC 负责交付件索引、齐套、审核及归档业务；PLT 负责实际文件及通用归档记录 | 不再新设独立“交付资料域”；索引、来源业务结论和文件物理对象不得相互覆盖 |
| 客户服务等级 | CUS 维护等级、策略和有效期；其他领域消费触发时冻结的事实 | 不再从 CUS 拆给自定义服务保障领域 |
| 技术公告 | KNO/INT-04 持有平台内技术公告引用和同步职责；外部 ITR 原始公告、CRM 停产停维事实保留原 Owner | 不整体归 AST；AST/CUT 等只是相关消费方，项目预检查与处置另按其正式需求归属 |
| 资源准备、外包与换货 | PRE 的准备/协调职责归 SOL；RES/SUB 拥有正式转包业务；OA、ERP、备件、授权和财务的权威事实不被平台取代 | 不将所有旧申请单塞入一个新供应领域；先核实旧行为对应的是准备协调还是转包业务 |
| 单机风险 | 实施单机风险按 EXE-05 归 IMP；项目风险、割接风险和巡检问题分别按既有业务解释 | 不因为旧类位于 engineering/risk 就全部归 SOL，也不合成通用风险写模型 |
| 巡检和续保 | 巡检归 SRV；AST 负责既定设备保障基本事实；ACC-06 为静态交接，持续服务跟踪和续保经营不因旧表存在自动恢复 | 历史状态和数据保留；是否进入新独立业务必须遵守既有正式范围，不把续保列为默认待补建能力 |
| 通用采集与业务采集结果 | PLT/INT-12 持有凭证、任务及通用回调；IMP/CUT/SRV 各自解释业务结果；AST/EQP-02 统一管理配置 Log | 不把采集传输成功当作业务完成，不按 integration 目录另定 Owner |

以上是归属纠正，不代表各模块已完成行为审计、聚合设计或功能验收。上一版将培训划给工程实施、将闭环全部划给项目治理、将客户服务等级拆给服务保障的结论不再适用。

## 4. 保留的旧表取证清单

清单继续保留前序取证中的 69 个不同旧表名，来自 17 份历史迁移。它不是运行库全量盘点，也没有补做全仓库扫描。这里的“审计入口”仅用于安排领域内审查：一个旧宽表可以包含不同 Owner 的事实，须按行为和事实拆解；列出多个相关领域不允许共同写同一权威事实。

| 审计入口 | 旧表取证线索 | 来源 | 当前定位及待审边界 |
|---|---|---|---|
| PROJ | `pms_project` | V5/V7 | 项目身份与治理；合同、发货、外部属性按原 Owner 分别解释 |
| PROJ | `pms_project_team_member`、`pms_project_tree_change_batch` | V7 | 项目任职、树变更，不替代客户联系人或系统用户 |
| PROJ | `pms_project_task`、`pms_project_task_dependency`、`pms_project_phase_template`、`pms_project_phase`、`pms_project_risk` | V8 | 任务与阶段编排、依赖、项目风险；专业业务结果仍由其 Owner 产生 |
| PROJ | `pms_project_portfolio`、`pms_project_portfolio_member`、`pms_project_portfolio_rule` | V21 | PROJ-12 项目组合；ANA 只读分析，不接管成员写入 |
| PROJ | `pms_team_batch_change`、`pms_team_batch_change_item` | V22 | 项目任职批量变更 |
| SOL，协作 PROJ | `pms_schedule_backward`、`pms_schedule_backward_item` | V22 | 施工计划倒排建议与明细；节点承载不转移 PLN 业务 Owner |
| SOL / PROJ / PLT，按行为拆解 | `pms_plan_change_request`、`pms_plan_change_phase_snapshot` | V23 | 需区分 PLN 计划业务、PROJ 节点更新和 CHG 公共变更流程；尚未完成逐命令映射，不整表指定最终新聚合 |
| PROJ | `pms_project_governance_action` | V23 | PM-10 回退/异常关闭，不代替 CLO 正常闭环 |
| PROJ | `pms_project_template` | V47 | 编排模板，不是工程文档模板或通用表单模板 |
| ACC，协作 PROJ | `pms_acc_project_closure` | V17 | 闭环申请及结果按 CLO 解释；PROJ 写入生命周期字段，不因代码目录改变 Owner |
| SOL | `pms_eng_site_survey` | V10 | 现场工勘已确认承接，不重迁；资产位置事实通过 AST 契约处理 |
| SOL | `pms_eng_requirement` | V10/V36 | 同表 BUSINESS/INTERFACE；需求分析排除不自动排除未核实接口规划 |
| SOL | `pms_eng_solution`、`pms_eng_solution_source` | V10/V36 | 实施方案和来源引用，不取得来源事实写权限 |
| SOL | `pms_eng_resource_ready` | V10 | 准备就绪判断，资源来源及批准结果保持外部/相关领域 Owner |
| SOL | `pms_eng_briefing` | V27 | PRE-05 交底内容、来源、审核及发布；不等于启动会或通用文件 |
| SOL，复用 PLT | `pms_eng_form_template`、`pms_eng_form_instance` | V27 | 准备表单业务语义归 SOL；共享格式、渲染和通用实例能力须对照 PLT，不能复制第二套公共平台 |
| IMP | `pms_eng_risk` | V30 | 对照 EXE-05 单机风险及原处置行为；来源同步不取代业务结论 |
| SOL，引用 KNO/AST | `pms_eng_announcement_check` | V30 | 项目预检查与处置按准备业务核对；公告、设备原始事实只引用 |
| SOL，复用 PLT | `pms_eng_doc_template`、`pms_eng_doc_template_version` | V36 | 需求/方案章节、裁剪和适用性；与公共模板机制分清，不另设文档领域 |
| RES，协作 SOL | `pms_eng_outsource_request` | V25 | 完整旧申请与正式 SUB 转包行为仍需逐项对应；工勘触发只是来源，不直接认定整表是完整转包聚合 |
| SOL，引用外部权威 | `pms_eng_material_requisition`、`pms_eng_external_procurement`、`pms_eng_material_exchange` | V25 | 领料、外采、换货准备与协同；OA/CRM/ERP/备件原始结果不得由平台伪造 |
| SOL / AST / PLT，按行为拆解 | `pms_eng_authorization` | V30 | 准备申请、设备授权查询及身份访问授权分别解释；FORMAL/TEMPORARY/LOAN 不等于用户权限，不按整表制造新 Owner |
| IMP | `pms_eng_arrival`、`pms_eng_installation`、`pms_eng_configuration`、`pms_eng_joint_test`、`pms_eng_issue` | V10 | 现场实施及问题行为；引用 COM 范围、AST 设备与 PLT 文件，不写对方事实 |
| CUT | `pms_cut_task`、`pms_cut_risk`、`pms_cut_plan`、`pms_cut_execution`、`pms_cut_observation` | V12 | 全量记录旧行为，但旧逐步骤/观察生命周期不能据表存在自动进入现行 CUT；保护历史并登记差异，不自行扩建 |
| ACC | `pms_acc_completion_certificate`、`pms_acc_acceptance`、`pms_acc_deliverable_checklist` | V17 | 客户确认、验收与齐套判断；培训虽未列旧表仍归 ACC |
| IMP，协作 ACC/PLT | `pms_eng_deliverable` | V10 | 区分实施来源证据上传、ACC 汇总/归档业务与 PLT 文件；旧 archive 字段名不决定新写入职责 |
| ACC，复用 PLT | `pms_acc_archive_document` | V17 | 交付件索引和归档业务引用公共文件，不新增独立归档业务领域 |
| SRV | `pms_srv_task`、`pms_srv_rule`、`pms_srv_execution`、`pms_srv_offline_file`、`pms_srv_report`、`pms_srv_issue` | V14 | 巡检任务、规则、执行、解析、报告与问题；按 INS 现行范围审查旧行为，不能泛化成通用工单 |
| AST / ACC / SRV，按事实和版本拆解 | `pms_srv_maintenance`、`pms_acc_maintenance_transition` | V14/V17 | 设备保障事实、静态交接、服务状态及旧续保行为混合；保留历史，禁止自动恢复续保经营或持续跟踪 |
| CUS | `pms_customer_service_level` | V21 | 客户服务等级、策略及生效区间；旧主动服务字段不授权建设 V3 主动服务功能 |
| CUS | `pms_customer`、`pms_customer_contact` | V3 | 外部客户主数据副本和平台联系关系按字段 Owner 区分 |
| AST | `pms_equipment`、`pms_equipment_version`、`pms_equipment_config_log` | V6 | 设备主档、历史与配置档案；保持 MES/ITR 来源权威 |
| KNO | `pms_eng_announcement` | V30 | 技术公告引用和同步；旧发布/停用操作不授权恢复 V3 技术公告治理 |
| PROJ 的 INT-01，集成适配协作 | `pms_project_sync_batch`、`pms_project_sync_detail` | V5 | 同步过程证据不是新增领域；项目业务写入归 PROJ |

计数仅核对来源清单的 69 个不同表名，不再按自定义职责域统计。COM、ANA、PLT 等即使在这份历史表清单中没有独立表，也仍保留其既有领域身份，不为填满清单编造表名。

满意度已有 `project/service/satisfaction` 线索；KICKOFF、SERVICE、ACC01 已在任务样例配置中定位。配置只作入口线索，不证明独立业务存在、也不决定 Owner；尤其 ACC01 必须回归 ACC。KICKOFF、SERVICE 的完整独立业务链仍待定位，不为了迁移清单齐全强造聚合。

## 5. 本轮采用的实施顺序

```text
PRD 已确定的领域职责（输入，不重划）
→ 领域内完整行为与约束审计
→ 聚合边界
→ 实体与值对象
→ 表结构与存量承接
→ 独立业务功能
```

| 步骤 | 必须查清或交付的内容 | 不得替代为 |
|---|---|---|
| 既有领域归属 | 引用正式领域编码、所属需求、事实 Owner；旧代码路径只是来源 | 新建职责域、按 S1～S6 或表名前缀重新划分 |
| 完整行为与约束审计 | 旧 Controller/Service/DO/Mapper/XML/API/页面、子对象、定时任务、事件、审批回调、直接消费者；全操作、状态、权限/租户、事务/并发、幂等、版本/附件、外部副作用、失败与历史保护 | 只读主表、只查 CRUD、只看 WorkBinding；仅凭本文归属就标记审计完成 |
| 聚合边界 | 在既有领域及设计约束内验证业务身份、生命周期、事务一致性和不变量；判断现有承接是否可用；细化不足，不重新造一套已存在模型 | 一张旧表、一个页面、一个任务节点机械等于一个聚合；直接复制出第三套事实源 |
| 实体与值对象 | 根与子实体的身份、值对象语义、不可变修订、跨聚合引用；共用机制与领域规则分离 | 先写 DO 再补业务解释；继承或直接委托旧业务类假装隔离 |
| 表结构与存量承接 | 从已确认模型确定表、键、约束和版本；保留来源键、原状态、引用关系及历史；定义幂等、冲突、失败、回滚和核对 | 仅改表名、无证据批量搬运、覆盖旧表或让历史记录冒充当前已完成事实 |
| 独立业务功能 | 新 Controller/Service/API/页面及适用配置基础能力能够独立办理完整业务；旧功能逐项对应，新副本针对性验证 | 只有空壳、只增加 WorkBinding、只满足单一消费者或仅通过编译 |

本轮范围止于独立业务功能。模板、任务、阶段和 WorkBinding 属后续消费适配，不作为这一轮先行实现或绕过独立业务的理由。

“每个模块独立 1 个 commit”按完成的业务迁移单元执行，不按上述六步各切一个不完整模块提交。每次提交前重读目标分支 HEAD，保留并行变更；提交后核验旧实现未修改。此次文档纠正单独提交，不冒充业务迁移 commit。

## 6. 已有成果与保护范围

- 需求分析和现场工勘保留需求方已确认的实体分离成果，不重复迁移；旧表中尚未证明承接的其他分支仍需审计。
- `b2f0045e` 工程交底副本暂不扩张，归 SOL/PRE-05 审计；不是后续聚合/实体/表的既定范式。本轮不回滚它、不调整其表或菜单。
- 施工计划、到货验收、验收报告、满意度、闭环等已有新实现均先纳入本领域审计；已确认的聚合及契约优先复用，不因缺少接入而复制第三套模型。
- 旧类、旧页面、旧 API、旧表、旧入口和存量业务历史不变；后续增强只落新副本。发现旧代码与现行范围冲突，保留源与差异，按获授权范围处理新实现。
- `project/normalclosure` 是 ACC/CLO 业务承接审计对象，需分清 PROJ Writer 协作；`asset/warranty` 已读查询不能推定续保写入权。
- `pms-module-outsourcing` 前序仅定位到骨架，不因领域已有命名就认定功能完成。

## 7. 取证来源与完成口径

### 本轮读取

- `docs/baseline/prd-v1.8.md`：范围治理、功能架构及 §3.5～§3.7 相关章节；未通读无关需求，未开展全量 PRD 功能差距审计。
- `docs/design/phase-1-domain-ownership.md` 全文；`02-domain-model.md` 1～110 行；`02c-data-ownership-matrix.md` 全文。
- 仓库 `AGENTS.md` 和 `docs/README.md` 的相关规则；本文件原版取证范围及来源清单。

### 沿用的代码取证线索（不升级为本轮完整审计）

17 份旧迁移位于 `sql/migrations/`：V3__pms_customer_and_contact、V5__pms_project_master_sync、V6__pms_asset_equipment、V7__pms_project_tree_and_team、V8__pms_project_wbs_phase_risk、V10__pms_engineering_tables、V12__pms_cutover_tables、V14__pms_service_tables、V17__pms_acceptance_tables、V21__pms_portfolio_and_service_level、V22__pms_batch_change_and_schedule、V23__pms_plan_change_and_governance、V25__pms_outsource_material_procurement_exchange、V27__pms_engineering_briefing_and_form、V30__pms_eng_risk_announcement_authorization、V36__pms_doc_template_structured、V47__pms_project_template，均为 `.sql` 文件。

| 线索 | 前序实际读取范围 |
|---|---|
| 阶段任务 | `scripts/revise_fproj009_sample_tasks.py` 1～180 行；只用于入口定位，不以样例配置决定 Owner 或通用规则 |
| 工程方案 | engineering 的 `service/solution/SolutionServiceImpl.java` 1～190 行 |
| 工期 | engineering 的 `service/constructionplan/ConstructionPlanApplicationService.java` 1～160 行及目录 |
| 到货 | engineering 的 `service/arrivalacceptance/ArrivalAcceptanceApplicationService.java` 1～160 行、`ArrivalAcceptanceCommandService.java` 1～120 行及目录 |
| 文档模板 | engineering 的 `service/doctemplate/DocTemplateService.java` 全文和 V36 |
| 闭环 | project 的 `service/normalclosure/NormalClosureApplicationService.java` 1～125 行及目录 |
| 设备保障 | asset 的 `service/warranty/DeviceWarrantyQueryService.java` 全文及目录 |
| 模块位置 | project、engineering、cutover、service、asset、customer、commerce 相关 service 目录；platform-api、integration 根目录；outsourcing 递归目录 |
| 交底 | [工程交底取证与限制](briefing.md)记录的旧 Controller、VO、Service、DO、Mapper、API、页面及 V27/V218 |

本轮只纠正归属依据、保留来源线索并明确六步顺序；没有完成任一待迁移模块的完整行为审计、聚合设计或独立功能。未执行业务代码变更、数据库迁移、完整构建、运行测试或浏览器验收；不晋级任何功能状态。13 个领域来自既有确认记录，不把本轮文档核对描述为重新批准领域或重新验证全部正式需求。
