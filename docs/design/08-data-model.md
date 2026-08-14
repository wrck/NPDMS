# SDS Phase 2：数据模型

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：PRD V1.7 附录 A.1 的全部 103 项 V1/V2 正式需求；本分册按 Owner 和聚合给出数据落位，逐项链接见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 2 数据架构；业务 Owner 沿用 `docs/design/phase-1-domain-ownership.md` 的已签署结论
> 前置设计：`02-domain-model.md`、`02b-aggregate-boundary-decisions.md`、`05-state-machine.md`、`07-authorization-design.md`
> 实现证据：`docs/engineering/gates/phase-2/implementation-fact-inventory.md`
> 领域实体迁移对齐：`08a-domain-entity-migration-alignment.md`

## 1. 设计目标与边界

本分册定义业务事实的唯一 Owner、聚合内实体和值对象、跨域引用、同步副本、快照、版本和历史模型。它不定义物理表名、字段类型和索引细节；这些在 `09-database-design.md` 落位。

全部显式领域数据对象的旧数据元、旧库、当前实现来源及`STRUCTURED/RELATION/EXTERNAL_SYNC/CURRENT_FORWARD/REBUILD/NEW_ONLY/EXCLUDED`迁移策略见08a；本分册中的领域实体不得在迁移实施时因“核心链之外”被遗漏。

设计遵循以下原则：

1. 同一个业务事实只有一个写 Owner；跨 Context 只能保存逻辑引用、必要同步副本或带来源版本的不可变快照。
2. 项目和任务均不限制固定层级。层级类型可使用基础平台字典扩展，但父子无环、终态和迁移守卫不由字典决定。
3. 设备同一时点只能有一个当前项目归属；主子项目统计通过归属项目的祖先链汇总，不制造多份“当前归属”。
4. CRM、MES、ITR、财务等外部系统拥有其权威字段；平台同步交付闭环所需的本地副本，不能把所有查询变成运行期接口调用。
5. 状态变更必须通过聚合命令和受控 transition；显示名称、可扩展业务类型和标记含义优先使用可配置字典。
6. 文件正文、设备凭证明文和外部原始大结果不嵌入业务聚合；业务对象保存受控引用和校验信息。
7. 历史、已批准版本、归档版本、来源证据和审计事实不可覆盖。

### 1.1 数据元与历史迁移证据的使用边界

本模型同时受 PRD 与既有结构化数据证据约束。数据元和旧库证据用于证明“对象、字段、关系、来源血缘与异常类型确实存在”，但不能覆盖 PRD，也不能把旧系统技术表或历史缺陷直接升级为新平台业务模型。

| 证据 | 当前可用事实 | 在本模型中的用法 | 禁止推断 |
|---|---|---|---|
| `specs/001-project-delivery-platform/evidence/data-elements/manifest.json`及同目录 JSONL | 数据元 Excel 哈希为`4250DD8D...A0116`；结构化覆盖151行数据元表、197条语义记录及活动结构页 | 字段语义、中文名称、来源单元格和旧物理字段的第一查询入口；结构化证据不足或源哈希变化时才回查 Excel | 仅凭字段同名合并客户、项目、合同、设备或人员 |
| `semantic-data-element-canonical.jsonl` | 108个归并语义数据元，含客户/联系人/项目/公司部门/设备等目标语义 | 校验核心对象必须具备的可查询字段及关系落位 | 将数据元中的展示分组直接当成聚合边界 |
| `core-field-mapping.jsonl`及摘要 | 18张核心旧表、326字段曾完成逐字段处置；旧记录要求完整保留来源载荷 | 识别结构化事实、关系解析、血缘和仅载荷字段；形成迁移问题而非丢行 | 把历史`326/326`当成当前目标 DDL 已放行 |
| `legacy-data-element-business-object-mapping.md`与`project-order-migration-mapping.md` | 项目—合同—订单行—设备链、重复/多义/空键和数量分配已有证据结论 | 约束目标关系、不变量、问题分类和迁移顺序 | 用旧项目组推断项目树/项目组合，或用执行单/合同后缀推断订单行归属 |
| `ddl-drift-review.md` | 当前核心迁移 DDL 哈希`5EB974...E4249`与旧目录哈希`2B2069...BF33`不同；ADR-0028已按当前哈希接受Q07的257项技术约束、Q08的122项候选索引及V1.7/Q09～Q14清单，并保留Feature/P3-E06性能验证；隔离MySQL 8.4.10执行PASS，当前新候选待fresh review | 数据模型为`MODEL_BASELINE_REVIEW_PENDING`，上一轮GO不覆盖本轮复审校验变更，不得作为SDS/Feature输入；`approvedDdlSha256`显式为空且仅属未来历史迁移门禁，历史迁移和数据切换继续阻断 | 把候选索引误称为性能已验收，或将模型候选、语法执行成功误称为当前迁移已获批准 |

数据元、旧库结构和 PRD 发生冲突时：业务语义以 PRD/批准决策为准；旧库物理事实以最终只读一致性抽取为准；无法裁决的记录进入迁移问题，不由实现按字段名、后缀、最大 ID 或任意候选值猜测。

### 1.2 迁移证据反推的核心业务不变量

1. 客户、联系人、项目的编码、名称、市场行业四维分类、联系方式、客户项目名称、办事处、公司和部门是独立语义；项目分类不得代替客户分类，公司与部门必须作为同一业务上下文关系共同解析。
2. 项目与合同、合同与订单均允许多对多；订单头不能固化唯一合同，项目实施最小范围落到 ERP 订单行及其分配数量。
3. CRM 执行单是辅助关联证据，不是订单行实施范围的权威父对象；没有 CRM 配置不能阻断 ERP 订单行实施。
4. 旧项目组只作为解析旧项目合同关系的技术桥，不生成项目父子关系、项目组合或多期项目关系；历史项目无明确父子证据时作为独立根项目迁移。
5. 同一订单行可以分配给多个正式子项目；缺少逐项目分配数量时保留待补数量事实，不进入实施量、完成率和验收统计。
6. SN 主档与发货/RMA/返还/再发放事件分离。同一 SN 的多次源记录不能被去重删除；当前项目归属由完整有效区间计算，历史项目关系保留。
7. 项目编码遵循ADR-0020：多合同、多订单不派生新项目编码；只有独立交付边界才拆分子项目。`root_id`表示可变化的当前项目树根，`code_root_id`表示不可变编码命名空间，两者不得混用。
8. 市场行业分类遵循ADR-0021：市场部、系统部、拓展部、子行业分别保存编码和名称；客户与项目直接保存这八个快照字段，不保存`relation_id`，也不得将四维分类推断为组织关系。
9. 主 SN—附加 SN 是合同维度的设备关系；附加 SN 是独立设备身份，不能作为主设备第二物料字段。RMA 替换关系与附加 SN 关系类型分离。
10. 旧状态值、旧审计字段和旧主键只形成映射/血缘证据；不直接成为新状态代码、新平台审计主体或目标主键。
11. 核心迁移模型遵循ADR-0022：跨领域只保存逻辑引用；一源多目标映射保存目标角色与稳定顺序；当前唯一性使用生成标记；项目、合同、订单、SN和来源键不可复用；历史冲突只进入迁移问题并保留逐源证据。
12. Q03当前关系遵循ADR-0023：设备直接项目归属、客户主联系人、项目同角色主公司部门关系保持当前唯一；交付范围按项目节点—订单行保持一条当前主记录并以多条明细表达地点、产品/设备类型、数量和批次；订单—CRM执行单为多对多，允许同一订单存在多个默认主执行单关系。

## 2. 数据分类与引用方式

| 数据类型 | 定义 | 写入方式 | 跨域消费方式 |
|---|---|---|---|
| Owner Fact | Context 对业务事实拥有最终解释权 | 聚合命令、状态迁移或同步适配器 | ID 引用、查询接口、领域事件 |
| External Master Copy | 外部系统权威、平台为交付使用保存的必要副本 | 按来源键和来源版本幂等同步 | 本地查询；展示来源和同步水位 |
| Immutable Snapshot | 在审批、任务、报告或对账时冻结的业务视图 | 创建后不改写，变更产生新版本 | 按快照 ID 消费 |
| Projection | 为树、统计、搜索或看板生成的可重建数据 | 事件驱动或批量重建 | 只读，不作为交易真值 |
| Reference | 指向其他 Context 或外部系统对象的逻辑标识 | 校验存在性后保存 | 不建立跨 Context 级联写入 |
| Audit / Evidence | 谁在何时以何权限执行了什么动作及结果 | 追加写入 | 授权查询，不参与业务状态直改 |

跨 Context 引用统一保存 `targetContext`、`targetType`、`targetId`；需要稳定展示时同时冻结名称、编码等最小快照。外部引用另保存 `sourceSystem`、`sourceKey`、`sourceVersion`、`sourceUpdatedAt` 和 `syncedAt`。

## 3. 全局标识、版本与来源元数据

所有持久化聚合至少具备：

| 元数据 | 作用 | 约束 |
|---|---|---|
| `id` | 平台内部稳定标识 | 创建后不可变 |
| `tenantId` | 租户隔离 | 业务唯一键必须包含租户维度 |
| `businessCode` | 人可识别编码 | 仅在 PRD 要求或现行业务已有编码时使用 |
| `version` | 聚合乐观并发版本 | 每次有效写入递增，不作为业务版本名称 |
| `statusCode` | 生命周期状态代码 | 只能由状态机迁移；不得由通用字典接口新增可执行状态 |
| `creator/createdAt/updater/updatedAt` | 基础审计 | 服务端生成 |
| `sourceSystem/sourceKey/sourceVersion` | 外部来源与幂等键 | External Master Copy 必填 |
| `effectiveFrom/effectiveTo` | 时态事实有效区间 | 历史区间不得重叠；当前记录 `effectiveTo` 为空 |
| `migrationSourceRecordId` | 一次性迁移来源证据引用 | 只用于迁移对象；指向不可变原始载荷和校验和，不替代结构化字段 |
| `externalKeyRef` | 旧主键/外部稳定键映射 | 旧 ID 不复用为目标 ID；多源归并仍保留每条来源映射 |

【建议】业务版本使用独立 `revisionNo/revisionStatus`，与并发 `version` 分离。已提交、已批准、已发布和已归档 revision 不原位改写；修订产生新 revision，并以 `supersedesRevisionId` 形成链。

## 4. Project Delivery 数据模型

适用 Requirement：PM-01～PM-11、PROJ-12、INT-01。

| 聚合/实体 | 类型 | Owner 事实 | 关键关系与不变量 |
|---|---|---|---|
| Project | 聚合根 | 项目身份、四维业务分类、行业四级快照、负责人、生命周期、来源映射 | `parentProjectId` 可空；父子无环；不限制深度；CRM项目编码默认复用；合同/订单/执行单独立关联；签约方式、项目类别、实施方式、重大项目级别分别保存且Owner不可混用 |
| ProjectHierarchy | 聚合内关系 | 当前父子关系、根节点、层级类型 | 层级类型来自字典；结构变更必须经过 MoveProject 并校验无环 |
| ProjectAncestorProjection | 可重建投影 | 祖先/后代查询路径 | 【建议】保存 ancestor、descendant、distance；不是项目真值 |
| ProjectTemplate | 聚合根 | 项目模板、阶段模板、任务模板和适用条件 | 已发布模板不可覆盖；项目实例冻结所用模板版本 |
| ProjectTask | 聚合根 | 任务身份、负责人、计划、状态和层级 | `parentTaskId` 可空；无固定深度；层级与依赖关系正交 |
| TaskAncestorProjection | 可重建投影 | 任务祖先/后代查询路径 | 【建议】支持权限过滤、批量统计和树分页 |
| TaskDependency | 关系实体 | 前置/后置依赖及依赖类型 | 不得用父子层级替代依赖；依赖图不得产生受控规则禁止的循环 |
| ProjectMemberAssignment | 时态关系 | 项目角色、成员及生效区间 | 批量变更追加历史；角色来自已确认业务角色或基础平台权限映射 |
| ProjectPortfolio | 聚合根 | 项目组合定义和成员关系 | 不改变成员项目 Owner；组合指标读取快照 |
| ProjectStageSnapshot | 不可变快照 | 项目在阶段切换时的门禁输入和结果 | 阶段回退保留原快照并生成新快照 |
| BorrowedProjectConversion | 聚合根 | PM-05 转销批次、源/目标项目、正式销售业务、处理状态和汇总 | 源借货项目与目标正式项目保持独立；同一源项目只允许一个生效转销目标；全部对象成功前不得归档源项目 |
| ConversionItem | 聚合内实体 | 一个实施对象的处理方式、来源版本、目标引用/副本、结果和失败原因 | 默认 `READ_ONLY_REFERENCE`；仅需继续编辑的派生草稿使用 `DERIVED_COPY`，且保存来源对象 ID/版本 |
| ConversionDeviceDisposition | 聚合内实体 | 设备继续借测、转入正式项目或已归还的逐台处置 | 转入时调用 AST 唯一归属命令；部分失败不把失败设备展示为目标项目已接收 |
| MultiPhaseProjectGroup | 聚合根 | PM-06 多期群组、关系类型、版本和展示口径 | 群组不替代各期项目，不合并项目编码、合同、状态或审计历史 |
| MultiPhaseProjectMember | 聚合内实体 | 项目、期次号、展示顺序、有效区间和来源关系 | 同一关系类型下项目仅属于一个有效群组；群组内期次号唯一；禁止循环前后期关系 |
| CrossPhaseContentReference | 聚合内关系 | 客户、设备视图、拓扑、方案等来源项目/版本及派生对象引用 | 引用只读；修改在新期生成派生版本，不回写历史期次 |

项目树查询规则：

- 权限“包含子项目”读取 `ProjectAncestorProjection`，不得递归逐层调用接口。
- 父项目的直接子项目快照与全后代闭环门禁是不同口径，必须在查询模型中显式命名。
- 移动项目节点后，项目真值和投影采用同一变更批次号；投影未完成时返回“结构更新中”或读取上一完整版本，不得返回半棵树。

PM-05 是对象级可恢复转销过程，不是 Project 的普通状态更新：`BorrowedProjectConversion` 以 `sourceProjectId + formalSalesBusinessId` 幂等，状态使用 PRD 已定义的处理中、部分失败/待处理、已完成；对象项逐项保存结果。只有目标正式项目已由有效 CRM/ERP 销售业务建立、全部对象校验成功且设备处置完成后，才将源项目转为只读归档。

PM-06 是多期关系聚合，不复用父子项目树或项目组合冒充。群组查询按用户对各期项目的交集权限裁剪，缺失期次必须标记“不完整”；跨期设备视图按 Device ID 去重，并区分当前归属、历史参与和跨期复用。

## 5. Preparation & Solution 数据模型

适用 Requirement：PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01。

| 聚合/实体 | Owner 事实 | 版本与证据 |
|---|---|---|
| Preparation | 工勘、需求分析、资源就绪、交底和准备结论 | 每次提交冻结表单模板版本、填写值和附件引用 |
| ConstructionPlan | 工期、计划项、里程碑、计划变更申请 | 计划基线不可覆盖；变更保存前后差异和审批引用 |
| Solution | 方案正文元数据、来源、评审状态和适用范围 | 草稿可修改；提交/批准后生成不可变 revision |
| DynamicFormSchema | 可配置准备表单结构 | 【建议】版本发布后不可改；实例记录 schemaVersion |
| DynamicFormInstance | 一次业务填写和校验结果 | 业务字段以结构化值保存；大文件使用 FileReference |

Preparation 与 Solution 可以部署在同一物理模块，但各自通过应用服务维护聚合；不得由表单引擎直接更新 Project 状态。

## 6. Implementation Execution 数据模型

适用 Requirement：EXE-01～EXE-06、IMP-01～IMP-02。

| 聚合根 | 聚合内实体/值对象 | Owner 事实 | 跨域引用 |
|---|---|---|---|
| ArrivalAcceptance | ArrivalLine、ArrivalDifference、SignerSnapshot | 到货批次、实收数量、差异、签收结果和证据 | Project、Device、OrderLine、FileReference |
| InstallationRecord | InstallationItem、LocationSnapshot、InstallationEvidence | 一次安装记录、位置、结果、照片和确认 | Project、Device、ArrivalAcceptance |
| ConfigurationCollectionResult | ParseAttempt、ResultReference、ParserVersion、ComponentParseCandidate | 配置 Log 回调、原始整机证据、框/槽/板卡解析候选、解析版本和业务确认 | CollectionTask、Device、DeviceComponentRelation、FileReference |
| JointDebuggingResult | DebuggingItem、IssueReference、ResultReference | 联调输入、结论、问题引用和确认 | CollectionTask、Device、ProjectTask |
| ImplementationRisk | RiskTag、RiskTreatment | 单机/现场风险、等级、处置和关闭证据 | Project、Device；不复用 CUT 风险状态 |
| ImplementationQualityCheck | QualityItem、Remediation、ReviewRecord | 阶段质量检查、整改和复核结论 | Project、现场批次、FileReference |
| ImplementationSafetyCheck | SafetyItem、SafetyRemediation、SafetyExemption | 安全检查、阻断、整改、复核和豁免审批引用 | Project、现场批次、关联作业 |
| DeliveryEvidence | EvidenceRevision、UploadAttempt | IMP 阶段交付件身份、版本、来源和上传结果 | FileArtifact；ACC 只审核/归档引用 |

关键不变量：

- 到货差异、安装历史、采集解析失败、质量整改和安全阻断分别属于独立聚合，不合并为一个“现场执行单”。
- ConfigurationCollectionResult 与 JointDebuggingResult 消费 DAC 结果，但不持有连接参数和凭证明文。
- ConfigurationCollectionResult完整保留原始整机Log；框/槽/板卡解析候选由AST确认后形成DeviceComponentRelation。自动匹配与人工绑定均保留来源、解析版本和证据引用。
- 安全高风险阻断关联作业；只有 PRD 已定义的整改复核或豁免结果才能解除，不新增抽象审批角色。
- IMP 上传交付件，ACC 负责齐套审核和归档；文件二进制只有一个 FileArtifact 身份。

## 7. Acceptance & Closure 数据模型

适用 Requirement：ACC-01～ACC-06、CLO-01～CLO-02。

| 聚合/实体 | Owner 事实 | 不变量 |
|---|---|---|
| Acceptance | 验收范围、验收项、结论、客户确认和问题引用 | 验收结论不覆盖现场原始证据 |
| SatisfactionCollection | 满意度任务、冻结问卷、客户答卷、签字、评分判定和整改重收版本 | 答案、签字、评分及历史版本不可覆盖；未达标新建任务和问卷版本 |
| DeliveryArtifact | 应交清单、实际证据引用、齐套结果、审核和归档状态 | 审核与归档追加记录；不得修改 IMP 原文件历史 |
| ProjectClosure | 闭环申请、门禁快照、审核结论和完成事件 | 全部后代项目按既定门禁满足后才能完成闭环 |
| ClosureGateSnapshot | 闭环时交付件、问题、有效满意度结果和材料状态快照 | 不可变；重新提交生成新快照；不保存回访审批节点 |
| ServiceHandover | 遗留问题、设备、客户、责任方和持续服务交接结果 | 不包含续保年限、续保动作或续保报表 |

历史 `pms_acc_maintenance_transition` 不能作为目标聚合继续扩展；其可用交接事实通过前向迁移映射到 ServiceHandover，续保字段只保留兼容读取，不进入新写接口。

## 8. Cutover、Inspection 与 Service Operations

适用 Requirement：CUT-01～CUT-10、INS-01～INS-09、SRV-01。

| Context | 聚合根 | Owner 事实 | 关键边界 |
|---|---|---|---|
| Cutover | CutoverTask | P1～P6统一任务身份、来源上下文、人工等级和阶段状态 | 不派生CUT保障工单；状态仅由P1～P6业务结果推进 |
| Cutover | CutoverAssessment | 问卷版本、项目输入上下文、人工选择、人工等级和P5复核引用 | 自动建议等级仅V3；P2不增加审批节点 |
| Cutover | CutoverPlan | 调研项、风险项、操作/验证/回退清单、附件、保障人员安排和批准版本 | 清单是方案内容而非执行状态；职责变化新建revision，联系人类变化留前后审计 |
| Cutover | CutoverSupportArrangement | 方案版本下的保障人员、联系信息、到位时间、角色和任务职责 | `CutoverPlan`从属明细，不是独立任务或状态机；联系人类变化留前后审计，职责变化随新方案revision重审 |
| Cutover | CutoverClosure | 割接前/执行/测试结果、回退说明、附件、遗留项文本、INT-12结果引用和最终成功/失败 | P6提交即归档；遗留项无独立状态/责任/门禁；不保存逐步骤执行或稳定观察 |
| Inspection | InspectionTask | 任务、模式、设备范围、规则快照和状态 | 在线/离线互斥；在线通过 DAC 下发 |
| Inspection | InspectionRule | 可执行规则、参数和版本 | 任务冻结规则版本；规则发布后不可覆盖 |
| Inspection | InspectionReport | 结果摘要、异常、来源和报告版本 | 外部原始数据保存引用；报告可重建但已发布版本不可覆盖 |
| Inspection | ServiceIssue | 巡检问题、整改、复核和关闭证据 | 不与通用工单状态混写 |
| Service Operations | ServiceStatus | 设备客观服务状态和来源提示 | 不提供续保空间或续保率管理 |
| Service Operations | ServiceHandoverReference | 对 ACC 交接结果的只读引用和处理状态 | 不回写 ACC 交接原记录 |

## 9. Customer、Asset、Commerce 与 Resource

### 9.1 Customer & Relationship

适用 Requirement：CUS-01～CUS-04、INT-03。

| 对象 | 类型 | 规则 |
|---|---|---|
| Customer | External Master Copy / 临时主数据 | CRM 客户以 sourceKey 幂等同步；临时客户显式标记来源，合并不删除历史引用 |
| MarketRelation | External Master Copy | CRM同步市场部、系统部、拓展部、子行业的编码与名称组合目录；CUS拥有本地同步副本，不把组合目录解释为组织树 |
| CustomerContact | External Master Copy / 平台补充事实 | 权威字段不被平台覆盖；项目联系角色为独立时态关系 |
| CustomerRelationshipSnapshot | 不可变快照 | 项目、验收、巡检等业务发生时冻结必要联系信息 |

Customer与Project均直接保存`marketCode/marketName/systemCode/systemName/expendCode/expendName/industryCode/industryName`八个业务字段。`MarketRelation`只提供CRM组合目录、同步对账与候选选择，不是客户或项目的持久化外键目标；三者均禁止保存`relationId`。历史变更通过同步证据、审计和业务快照追溯，不依赖可变目录记录ID串联。

### 9.2 Asset Management

适用 Requirement：EQP-01～EQP-07、AST-01～AST-02、INT-02、INT-06。

| 聚合/实体 | 类型 | 规则 |
|---|---|---|
| Device | 聚合根 / External Master Copy | MES/ITR 权威身份字段保留来源；平台拥有项目归属、档案补充和业务关联 |
| DeviceArchive | 聚合内实体 | 安装位置、客户关系、配置 Log 引用、项目关联等平台档案信息 |
| DeviceComponentRelation | 时态关系 | 机框序列号、槽位、板卡序列号/型号、关系来源、生效区间和解析证据；同一机框槽位同一时点最多一个当前板卡，换板结束旧关系并新增 |
| DeviceCurrentAssignment | 当前唯一关系 | 同一 tenant/device 只有一个有效当前项目；指向实际归属项目，不复制到祖先项目 |
| DeviceAssignmentHistory | 时态历史 | 每次划转关闭旧区间并新增区间；有效区间不得重叠 |
| DeviceAncestorProjection | 可重建投影 | 【建议】将当前归属映射到项目祖先，用于任意上级项目统计；不代表多重归属 |
| MaintenanceFact | 客观基本事实 | 起止日期、服务等级、来源和按规则计算的客观状态；不保存续保动作和人工覆盖结果 |
| RMAReplacement | 聚合内历史 | 原设备、新设备、替换原因、时间和关联凭证；不覆盖原设备身份 |
| AssetSyncSnapshot | 不可变同步快照 | 保存来源版本、水位、校验摘要和字段差异 |

设备归属变更与项目树移动可能并发，二者必须使用独立版本控制并在投影层按批次重算；业务查询必须能识别投影水位。

历史业务事实不可删除是 PRD 第 8.2 节的治理规则，但不构成当前预建历史工单/工时对象或空壳表的依据。当前未识别并确认真实来源；将来只有在来源识别且需求方确认迁移后，才通过独立变更重新建模。

### 9.3 Contract & Fulfillment

适用 Requirement：COM-01～COM-02。

| 聚合/实体 | Owner 事实 | 规则 |
|---|---|---|
| Contract | ERP 合同同步副本和 CRM 经营引用 | ERP 拥有所属公司、合同编号、金额等核心字段；CRM 经营状态单独映射，平台不修改来源权威事实 |
| SalesOrder | ERP 订单/订单行同步副本和 CRM 履约关联 | 按 ERP 订单/行来源键、版本幂等同步；CRM 引用不得覆盖产品、数量、金额等 ERP Owner 字段 |
| OrderLine | 物料/服务行、数量和交付维度 | 数量不可由项目分配反向改写 |
| DeliveryScope | 订单行到实际承接项目节点的当前范围主记录 | 对应历史迁移语义`ProjectOrderLineScope`；同一项目节点—订单行同一时点只有一条当前主记录，保存分配总量、范围状态和来源证据；同一订单行可拆分多个项目但有效分配量不得超配；缺数量为待补数量，不计入交付统计；分配、释放均留历史 |
| DeliveryScopeDetail | 交付范围按地点、产品/设备类型和批次拆分的明细 | 一个当前DeliveryScope可包含多条明细；明细数量合计必须等于主记录分配数量。形成独立负责人、计划、验收或闭环边界时，不继续堆叠明细，而是分配到独立子项目 |
| FulfillmentSnapshot | 到货、安装、验收等履约汇总 | 按业务事件生成带口径版本的快照 |
| ReconciliationRecord | 平台与外部系统差异、处理和结果 | HTTP 成功不等于对账完成；支持重复执行幂等 |

### 9.4 Supplier & Subcontract

适用 Requirement：RES-01、SUB-01～SUB-05、INT-07。

| 聚合/实体 | Owner 事实 | 规则 |
|---|---|---|
| Supplier | 服务商档案、资质同步副本和可用状态 | 资质原件用 FileReference；状态来源可追溯 |
| SubcontractRequest | 转包范围、责任方、审批引用和生效区间 | 子项目只是可选业务关联，不改变项目树 Owner |
| PaymentGate | 付款前置条件快照和财务结果引用 | 平台不拥有付款事实；财务成功需回写/对账确认 |

备件采购、库存和出入库由外部系统承接，不在本 Context 重建备件业务。

## 10. Analytics、基础平台与 Knowledge Reference

### 10.1 Analytics

适用 Requirement：RPT-01、RPT-02、RPT-04、ANA-01。

| 对象 | 类型 | 规则 |
|---|---|---|
| MetricDefinition | 口径元数据 | 【建议】保存指标代码、口径版本、单位、粒度和来源，不允许同版本静默改公式 |
| MetricSnapshot | 不可变快照 | 保存统计时点、数据水位、项目树版本、范围和结果 |
| PortfolioView | 可重建投影 | 组合、组织和项目树范围只读展示；不回写交易表 |

周报、日报不作为独立需求或数据聚合；需要周期展示时复用指标快照查询。

### 10.2 基础平台公共能力

适用 Requirement：PLT-01～PLT-02、AUT-01～AUT-02、CHG-01、NFR-01～NFR-03、INT-05、INT-09、INT-10。

| 聚合/实体 | Owner 事实 | 规则 |
|---|---|---|
| Todo | 待办身份、业务引用和同步状态 | 待办完成不等于业务完成；业务状态由 Owner Context 确认 |
| AuthorizationGrant | 通用业务授权范围、有效期和撤销 | 不替代 DeviceCredential 的专用授权边界 |
| ChangeRequest | 变更申请、差异、审批引用和执行结果 | 版本变更作为低优先级独立能力，能后置的后置 |
| FileArtifact | 文件身份、内容版本、哈希和存储引用 | 详见 13；正文不复制进多个领域表 |
| AuditRecord | 主体、动作、对象、结果和关联 ID | Word 文件本身无需内容审计；业务动作和文件版本操作仍留痕 |

INT-05/INT-09 的人员、部门、岗位使用基础平台现有主数据；同步批次/水位和稳定来源键分别由已有 `plt_sync_batch`、`plt_external_key_mapping` 承载。不建立独立目录快照对象或替代表；基础平台主数据的前向实现仍由相应 Feature 管理。

### 10.3 Knowledge Reference

适用 Requirement：INT-04。

| 对象 | 类型 | 规则 |
|---|---|---|
| TechnicalNoticeReference | External Master Copy | V2 保存 ITR 公告标识、标题、适用型号/版本、严重度、来源版本和同步水位 |
| NoticeBusinessReference | 关系实体 | 业务对象引用某个同步版本；不在 V1/V2 提供本地发布/停用治理 |

KNO-V3-01～08 仅定义后续治理演进，不进入本分册的 V1/V2 可实施数据模型。

ADR-0022进一步明确：4张公告编写、阅读确认、产品影响和设备命中治理表不进入当前核心迁移DDL。INT-04的`TechnicalNoticeReference`仍是V2正式逻辑对象，其最小同步副本由INT-04 Feature以前向迁移单独设计，不得把V3会签、治理任务和处置统计提前落表。

## 11. Device Access & Collection 数据模型

适用 Requirement：INT-12、CUT-06、INS-02、INS-04、NFR-02，以及 IMP 的 EXE-03～EXE-04 采集入口。

| 聚合/实体 | Owner 事实 | 安全与生命周期约束 |
|---|---|---|
| DeviceCredential | 凭证元数据、密文引用、协议和创建人 | 默认仅创建人可用；不得持久化明文；轮换生成新版本 |
| CredentialGrant | 用户、设备、协议、命令模板、有效期五元组授权 | 未明确授权时只有创建人可用；撤销不改写历史任务快照 |
| CollectionTask | 业务来源、设备、命令模板、认证方式、临时登录用户名或凭证引用、幂等键、授权快照、完成模式和映射状态 | 临时输入只保存登录用户名、不保存密码；显式保存为凭证成功后，本次任务切换为凭证模式并记录新凭证 ID、版本及创建人默认授权快照 |
| DispatchAttempt | 外部任务号、请求摘要、次数、结果和时间 | 超时可重试；敏感字段不进入摘要和错误详情 |
| CallbackRecord | 原始回调摘要、外部状态原值、映射状态和幂等处理结果 | 重复回调只处理一次；冲突回调进入人工/对账队列 |
| CollectionResultReference | 外部结果、FileArtifact 或受控对象存储引用 | IMP/CUT/INS 只读取其授权范围内的结果引用 |

两种连接方式：

1. 已保存凭证：受信任执行边界在运行时解密并向现有采集平台下发，业务应用和用户界面只见掩码。
2. 临时输入：用户名/密码可在单次请求中受 TLS 保护传入；任务保存临时登录用户名用于审计，但密码不持久化、不写日志、不进入事件。
3. 临时输入并保存为凭证：用户显式选择后，平台先在同一业务命令中加密创建 DeviceCredential 和默认仅创建人可用的授权，再以新凭证创建 CollectionTask；任务认证方式为 `SAVED_CREDENTIAL`，记录 credentialId、credentialVersion 和 grantSnapshotId，不再记录为临时认证。凭证创建或加密失败时整个任务创建失败，不得退化为未获用户同意的临时任务。

CollectionTask 必须在创建时冻结完成模式：

- IMP、CUT、Inspection 入口使用 `BUSINESS_CONSUMPTION`，并冻结 `requiredConsumerContext/sourceObjectType/sourceObjectId`。只有相同任务、结果版本和业务对象的 `CollectionResultConsumed` 被幂等确认后，成功任务才能进入 `COMPLETED`。
- 独立中心使用 `CALLBACK_TERMINAL`；按 PRD INT-12，在没有 IMP/CUT/SRV 业务单据时，有效成功终态回调即可完成并保存通用结果。该模式不得被业务入口选用。
- 外部失败、取消或安全异常分别进入 `FAILED`、`CANCELLED` 或 `SECURITY_EXCEPTION`，不得发布或伪装为 `CollectionCompleted`。

现有采集能力可作为模块或子应用按本 Context 纳入；平台不重建协议连接与原始采集引擎，但必须拥有凭证、授权、业务任务、下发记录和数据回调闭环。

## 12. 版本、快照、历史与删除规则

| 场景 | 当前事实 | 历史保留 |
|---|---|---|
| 聚合并发修改 | 当前行 + 乐观版本 | 冲突请求不覆盖，返回当前版本 |
| 项目/任务层级变更 | 当前父子关系 | 变更批次、操作者、前后父节点、原因和投影水位 |
| 设备项目归属变更 | DeviceCurrentAssignment | 完整有效区间历史，禁止物理删除 |
| 方案/规则/模板/报告 | 当前可用 revision | 已提交、批准、发布、归档 revision 永久保留 |
| 外部主数据同步 | 最新本地副本 | 同步批次、来源版本、水位、差异和失败记录 |
| 审批/门禁 | 聚合当前状态 | 不可变输入快照、结论、意见和流程实例引用 |
| 文件 | 当前业务引用 | 内容版本、哈希、替换关系、归档和访问记录 |
| 凭证 | 当前可用密文版本 | 创建、轮换、授权、撤销和使用审计；不保留明文 |

业务历史、审计、快照和已批准版本使用逻辑失效或归档，不允许普通删除。基础平台的逻辑删除字段不能作为隐藏不可变证据的手段。

## 13. 字典、状态与业务标记

| 可配置字典 | 固定契约 |
|---|---|
| 项目层级类型、任务层级类型、风险类型、证据分类、动作类型、方向、来源类型、展示标签 | 状态代码、合法迁移、终态、迁移守卫、权限校验、幂等规则 |

新增字典值只能扩展分类和展示含义，不能自动获得状态迁移能力。状态机需要扩展时必须经过需求变更和兼容性评审；旧状态值映射到当前状态时保留 `legacyStatusCode` 和映射版本。

## 14. 跨 Context 关系总表

| 来源 | 目标 | 保存形式 | 禁止行为 |
|---|---|---|---|
| 任意业务 Context | Project / Device / Customer | 逻辑 ID + 必要名称快照 | 跨域 Repository 更新主档 |
| IMP/CUT/INS | Device Access & Collection | CollectionTaskId、授权快照 ID、结果引用 | 读取凭证明文或直接操作外部引擎 |
| IMP | ACC | DeliveryEvidenceId + immutable revision | ACC 覆盖 IMP 原始证据 |
| ACC | Project | ProjectClosureCompleted 事件 | ACC 直接写 Project 状态字段 |
| Project/Asset/ACC | Analytics | 领域事件/快照水位 | 看板回写交易状态 |
| CRM/MES/ITR/财务 | 对应 Owner Context | External Master Copy + SyncBatch | 运行期无缓存地逐次远程查询全部主数据 |

## 15. Phase 2 数据模型门禁结论

| 门禁项 | 结论 | 证据 |
|---|---|---|
| 数据 Owner 唯一 | PASS | 第 4～11 节按 Context 明确 Owner Fact、同步副本和引用 |
| 任意层级可实现 | PASS | Project/Task 当前关系 + 可重建祖先投影，不固定深度 |
| 设备唯一当前归属及多级统计 | PASS | DeviceCurrentAssignment + History + AncestorProjection |
| 主数据本地同步 | PASS | External Master Copy 与来源/水位元数据 |
| 版本、快照、历史可实现 | PASS | 第 3、12 节 |
| 历史实现漂移有目标落位 | PASS | MaintenanceFact、ServiceHandover、TechnicalNoticeReference、DeviceCredential |

本分册可进入数据库物理化设计评审；最终 `BASELINE` 仍依赖 09～16 分册一致性校验和 Phase 2 独立复审。
