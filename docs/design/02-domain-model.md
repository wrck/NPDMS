# SDS Phase 1：领域模型

> 文档状态：`IN_REVIEW`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8机器差量校验已完成，待fresh-context独立复审
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 1. 建模原则

领域边界从 PRD V1.8 的业务责任、数据 Owner、生命周期和权限范围推导；旧 `specs` 仅作为交叉检查材料。项目、割接、巡检属于同一平台，但不是同一个聚合，不能通过跨域 Repository 直接修改。一个需求领域可以包含多个 bounded context；领域编码仍是需求 Owner 和规格分册边界。`Implementation Execution`（实施执行域）是现场实施 bounded context，不是单一聚合根；其内部聚合按现场事实、采集结果、质量检查和交付证据分别维护，IMP-02安全检查不属于当前V1/V2正式范围。

## 2. Bounded Context

| Context / 能力层 | Owner数据 | 允许引用 | 禁止直接修改 |
|---|---|---|---|
| Project Delivery | 项目、模板、阶段、任务 | 客户、合同、设备、交付件 | 客户主数据、设备主档 |
| Preparation & Solution | 工勘、需求分析、计划、方案 | 项目、模板、人员 | 项目主状态 |
| Implementation Execution | 到货、安装、采集结果、质量检查、实施阶段交付件/证据上传 | 项目、设备、采集任务、文件服务 | 设备主档核心身份、验收归档状态；安全检查不属于当前V1/V2 |
| Acceptance & Closure | 培训、验收、满意度收集、交付件齐套校验、审核、统一归档和闭环交接 | 项目、实施证据、方案、问题、文件 | 外部合同/财务事实、现场实施原始证据 |
| Cutover | CUT-01核心任务、问卷评估、调研清单、方案、分级审批和P6闭环结果 | 项目、设备、采集任务、风险/调研字典 | 采集执行引擎、设备凭证明文、通用工单及WO-06保障工单 |
| Inspection | INS-01～INS-09 巡检任务、规则、报告和问题闭环 | 项目、设备、采集结果 | 设备凭证授权、外部UMC原始数据 |
| Service Operations | SRV-01 设备服务状态；ACC-05持续服务跟踪仅为V3演进方向 | 设备、客户、服务交接事实 | 设备主档核心身份、外部服务原始数据；当前不建立持续服务跟踪对象 |
| Customer & Relationship | 客户同步副本、临时客户、联系人和客户关系 | 合同、项目、设备 | CRM权威字段、项目交付状态 |
| Asset Management | 设备同步副本、设备档案、归属、维保基本信息和配置Log关联 | 项目、客户、采集结果 | MES/ITR权威字段、项目交付状态 |
| Contract & Fulfillment | ERP合同、销售订单、订单行同步副本及项目交付范围分配 | 项目、设备、ERP来源版本 | 财务系统账务；当前不建立独立履约回写/对账业务聚合 |
| Supplier & Subcontract | 服务商、转包、付款门禁 | 项目、合同、问卷 | 财务付款结果 |
| Analytics | 指标快照、组合视图 | 各域只读事实 | 任何业务交易 |
| 基础平台能力（非业务 Context） | 待办、文件、授权、变更、字典、审计 | 各域标识和事件 | 业务域聚合内部状态 |
| Device Access & Collection | 设备凭证、授权、采集任务、外部状态原值、结果引用和回调证据 | 项目、设备、业务对象、命令模板 | IMP/CUT/INS业务结论、设备主档、外部采集引擎 |
| 集成适配层（非业务 Context） | 外部来源映射、回调、同步记录 | 各域命令/查询接口 | 外部系统数据库 |

## 3. 聚合与不变量

| 聚合根 | 关键不变量 |
|---|---|
| Project | 项目编码唯一；CRM来源默认沿用CRM项目编码；合同、订单、执行单通过关系关联；签约方式、项目类别、实施方式、重大项目级别分别保存且Owner不可混用；父子关系无环且层级不设固定深度；`current_stage`仅取S0～S6，`lifecycle_status`独立取ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED，`assignment_status`独立维护，`display_status`只读派生；CLO-02唯一产生NORMAL_CLOSED，PM-10唯一产生EXCEPTION_CLOSED |
| ProjectTask | 任务父子关系无环且不限制深度；每个可执行任务必须且只能冻结一个当前WorkBinding、PermissionPolicy、CompletionRule和可选GateRef，未指定其他业务绑定时使用TASK_NATIVE；状态变化必须经过受控 transition，完成必须由对应绑定事实和规则判定；查询按项目树索引和权限范围过滤 |
| Device | 序列号/设备身份唯一；同一时点同一设备只能有一个当前项目归属；机框、槽位、板卡当前关系唯一且按生效区间保留换板历史；历史归属通过关系版本保留 |
| DeviceCredential | 默认仅创建人可用；授权绑定用户、设备、协议、命令模板和有效期；任何业务不得读取明文 |
| CollectionTask | 幂等键唯一；临时密码不落库；外部状态原值保留；回调重复不得重复消费 |
| CutoverTask | P1～P6共享同一任务身份；P1作为任务接入入口、详情工作台展示P2～P6五个处理步骤；P3在同一工作台完成匹配、填写、CollectionTask下发和结果回填；一线提交问卷与人工等级，用服经理在P5复核；P5任一评审项为否必须驳回；P6提交后归档且历史不可覆盖 |
| CutoverClosure | 保存P6四类结果、回退说明、附件、遗留项文本及INT-12证据引用；不建立逐步骤执行、稳定观察或遗留项独立生命周期 |
| InspectionTask | 在线/离线模式互斥；规则版本冻结到任务；报告生成和问题闭环可追溯 |
| SatisfactionCollection | 任务冻结问卷模板、题目、分值和阈值版本；客户有效答卷、签字和达标结果不可覆盖；未达标必须整改后创建新任务和新问卷版本 |
| ProjectClosure | 交付件、有效满意度结果（模板要求时）、材料审核等门禁全部满足后才能闭环；CLO-02完成后形成NORMAL_CLOSED不可变闭环事实，PM-10异常关闭不复用该聚合终态 |

## 3.1 Implementation Execution 内部聚合拆分

| 聚合根 | 负责事实 | 不负责 | 拆分理由 |
|---|---|---|---|
| ArrivalAcceptance | 到货数量、签收结果、签收人、签收证据 | 设备主档身份、验收最终结论 | 到货可独立补录/审核，和安装、配置不共用事务 |
| InstallationRecord | 安装位置、照片、安装结果、设备关联 | 到货签收状态、设备主档核心字段 | 安装可重复记录并保留历史，生命周期独立 |
| ConfigurationCollectionResult | 配置Log采集结果、解析状态、结果引用 | 设备连接执行、凭证明文、通用任务下发 | 采集执行由外部平台负责，结果消费与现场业务解释分离 |
| JointDebuggingResult | 业务联调配置收集结果、联调结论、结果引用 | 设备连接执行、割接执行、项目闭环审批 | 与配置Log采集目的、结果解释和门禁不同 |
| ImplementationRisk | 单机风险标记、风险等级、处置记录 | 割接风险项和项目闭环审批 | 风险事实与质量安全检查不是同一责任边界 |
| ImplementationQualityCheck | 阶段质量检查、整改项、复核结论 | 项目闭环审批、交付件最终归档 | 质量检查有独立责任人与整改状态 |
| DeliveryEvidence | 实施阶段交付件、来源记录、版本、文件哈希、上传状态 | 验收归档审批 | IMP上传；ACC审核、齐套校验和归档 |

这些聚合通过项目ID、设备ID、任务ID或证据引用关联，不在一个跨聚合事务中强制同步；阶段门禁使用查询快照或领域事件校验完整性。

## 3.2 Stage—ProjectTask工作台模型

```text
ProjectTemplateVersion
└─ StageDefinition
   └─ TaskDefinition
      ├─ WorkBinding
      ├─ PermissionPolicy
      ├─ CompletionRule
      └─ GateRef（可选）

ProjectInstance
└─ ProjectStage
   └─ ProjectTask（任意深度）
      └─ 冻结的工作绑定与完成规则快照
```

- 项目工作区一级导航来自ProjectStage，二级业务导航区域来自ProjectTask；二级区域可继续按需展开任务子树，不限制ProjectTask深度。
- ProjectTask是执行编排聚合，也是`TASK_NATIVE`的默认业务实体。`TASK_NATIVE`直接使用ProjectTask通用基础字段和自身状态机；其他绑定只保存稳定目标引用、受信任组件键/表单版本和必要参数快照，真实数据与状态仍由Owner Context维护。
- `WorkBinding`统一支持`TASK_NATIVE`、`BUSINESS_OBJECT`、`BUSINESS_COMPONENT`、`DYNAMIC_FORM`、`APPROVAL`和`COMPOSITE`。工作台始终显示任务通用基础信息；`TASK_NATIVE`加载通用任务执行区，其他类型按服务端授权结果加载目标业务执行区并进入查看、编辑、创建、填写或审批模式。
- 项目概览是独立项目级投影，固定聚合基本信息、项目树、团队成员、项目任务、设备清单和实施范围，不作为TaskDefinition重复配置。
- CUT-03的清单和CollectionTask关联仍从属于CUT-01的P3业务阶段；界面合并不产生新的业务阶段或聚合Owner。

## 4. Context Map

`CRM/ERP → 集成适配层 → Project Delivery → Preparation/Solution → Implementation Execution → Acceptance/Closure`

`Project Delivery ↔ Implementation Execution → DeliveryEvidence → Acceptance & Closure`

`Project Delivery ↔ Cutover ↔ Device Access & Collection → External Collection Platform`

`Project/Device → Inspection → Device Access & Collection / UMC / Customer & Relationship`

`Cutover → Project Delivery` 只发布CUT-06成功闭环结果引用；`Service Operations` 消费项目闭环和设备服务事实。ACC-05仅作为V3持续服务跟踪方向保留，当前不形成ServiceFollowUp对象；WO-06后置为工单领域V3候选，不形成当前可流转Context、聚合或CUT事件。

`基础平台能力`通过命令、查询、事件和统一权限服务横向支撑各 Context，不作为万能业务 Context，也不拥有业务域交易数据；`集成适配层`只负责协议、映射、幂等、重试和对账。

## 5. 跨域契约

- 只通过应用服务、查询接口或内部事件引用其他 Context。
- 外部系统来源字段保留来源系统、来源单号、版本和同步时间；平台不覆盖外部 Owner 数据。
- 设备凭证授权快照随采集任务保存，撤销不改写历史执行事实。
