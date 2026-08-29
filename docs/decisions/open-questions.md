# Open Questions

用于记录 PRD 无法支持、互相冲突或必须由业务/架构负责人确认的问题。

## 状态

- OPEN
- BLOCKED_BY_SPEC
- RESOLVED
- REJECTED

## 模板

### Q-XXXX

- Status:
- Requirement IDs:
- Area:
- Question:
- Why it blocks design/implementation:
- Options:
- Recommended technical default:
- Business decision required:
- Resolution:
- Decision owner:
- Decision date:

## Phase 2历史资料承载决策

### Q-P2-001

- Status: RESOLVED
- Requirement IDs: PRD 3.4、8.2；WO-01～WO-06、WO-08～WO-10（V3演进）；AI-MIG-000
- Area: 历史工单、工时、附件、审批和操作证据的当前承载边界
- Question: V1/V2是否提供历史工单/工时的用户只读查询、导出和附件访问，还是仅由迁移门禁保存不可变归档/来源证据？
- Why it blocks design/implementation: 只有选择用户访问能力时才需要新增正式Requirement、Owner、领域对象、目标表、API、权限和迁移映射；在未批准这些内容前不得以“历史不可删除”反向创建当前产品能力。
- Options: A. 建设V1/V2只读查询/导出；B. 仅由`AI-MIG-000`在批准真实批次内保存不可变来源载荷或受限迁移归档证据，无用户入口。
- Recommended technical default: B；符合已确认的后置/排除优先原则，也避免为尚未识别的真实来源预建空壳对象和访问契约。
- Business decision required: 当前V1/V2不需要新增确认；A属于范围扩展，只有独立PRD/Feature变更获批后才能启动。
- Resolution: 采用B。V1/V2不提供历史工单/工时的菜单、查询、导出、附件访问、领域对象、目标表或迁移映射；`pm_project_maintenance`继续`EXCLUDED/NO_MIGRATION`。真实来源未来只有进入已批准`AI-MIG-000`批次时才保存不可变来源载荷或受限归档证据。若需用户访问，必须独立批准Owner、模型、API、项目/租户数据范围、附件权限、导出审计和来源映射。
- Decision owner: 需求方（当前范围）；真实迁移批次由业务Owner、数据Owner和迁移负责人共同确认
- Decision date: 2026-08-15

## Phase 3生产与发布门禁

### Q-P3-001

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: 生产拓扑与网络信任边界（P3-E01）
- Question: 生产入口、域名/TLS终止、网络区、前后端节点、对象存储、DAC执行区和外部系统流向由哪些平台和Owner承接？
- Why it blocks design/implementation: 不阻断当前逻辑设计；未实例化时不能验证目标环境的14信任边界和18生产部署/切换步骤，因此阻断该环境部署发布。
- Options: A. 由企业现有网关/LB、证书和网络区承接并提交现状拓扑；B. 为NPDMS新建独立入口和网络区并提交新拓扑。
- Recommended technical default: A；优先复用企业已运维平台，NPDMS只登记允许流量和责任边界。
- Business decision required: 是，需技术架构/运维确认平台和Owner。
- Resolution: A；复用企业现有网关/LB、证书和网络区。依据ADR-0018，实际平台、拓扑、流量和Owner在部署阶段由P3-E01确认，不作为当前SDS基线阻断项。
- Decision owner: 需求方（方向）；技术架构、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-002

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-03
- Area: MySQL/Redis生产形态（P3-E02）
- Question: 生产MySQL 8.4与Redis 7.4采用托管服务还是自建HA，节点规格、容量和故障切换证据是什么？
- Why it blocks design/implementation: 不能证明容量、数据一致性、缓存降级和发布恢复可执行。
- Options: A. 使用企业托管HA服务；B. 自建HA集群并由DBA/运维维护。
- Recommended technical default: A；如企业无可用托管服务再选B，且必须补齐故障切换与容量验证。
- Business decision required: 是，需DBA/运维确认服务形态和容量。
- Resolution: A；使用企业托管MySQL/Redis HA服务。实际产品、规格、容量和切换证据仍由P3-E02确认。
- Decision owner: 需求方（方向）；DBA、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-003

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: 备份恢复目标（P3-E03）
- Question: 平台生产RPO/RTO、备份频率/保留、文件和密钥引用恢复顺序及演练Owner是什么？
- Why it blocks design/implementation: 不阻断SDS基线；未形成恢复设计和演练证据时，阻断恢复验收与生产发布。
- Options: A. 业务Owner先批准RPO/RTO，再由DBA/运维设计并演练；B. 直接采用现有平台默认目标并由业务Owner签署适用性。
- Recommended technical default: A；业务目标先行，避免基础设施默认值与交付业务风险不匹配。
- Business decision required: 是。
- Resolution: A；业务Owner已批准RPO不超过1小时、RTO不超过4小时（ADR-0005），并批准日备35天、月备13个月、年备7年及连续日志策略（ADR-0012）；采用同城温备作为主要恢复路径、离线冷备兜底（ADR-0013）。DBA/运维据此设计并完成隔离恢复演练。
- Decision owner: 需求方（方向）；业务Owner、DBA、运维（目标与证据）
- Decision date: 2026-08-13

### Q-P3-004

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-02、INT-12
- Area: 凭证密钥托管（P3-E04）
- Question: DeviceCredential使用哪个企业KMS/Secrets Manager或等价密钥托管，轮换、吊销和应急Owner是谁？
- Why it blocks design/implementation: 不阻断当前密钥抽象和安全逻辑设计；具体设施未实例化时设备凭证能力不得部署上线。
- Options: A. 企业KMS/Secrets Manager；B. 应用信封加密且主密钥由独立受控设施托管。
- Recommended technical default: A；只有企业当前无可用KMS时才采用B，并必须实现密钥与数据库分离、版本化和轮换演练。
- Business decision required: 是，需安全/运维确认设施和Owner。
- Resolution: A；使用企业KMS/Secrets Manager。依据ADR-0018，具体设施、访问、轮换、吊销和应急证据在部署阶段由P3-E04确认，不作为当前SDS基线阻断项。
- Decision owner: 需求方（方向）；安全、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-005

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: Telemetry与安全事件（P3-E05）
- Question: 日志、指标、Trace、告警和安全事件分别进入哪个企业平台，访问角色、留存和采样如何批准？
- Why it blocks design/implementation: 不阻断17分册设计基线；具体后端未实例化时，阻断可观测验收、高风险审计生产验收与生产发布。
- Options: A. OpenTelemetry统一采集后接入企业现有后端；B. 各信号使用现有独立Agent/平台并通过correlationId关联。
- Recommended technical default: A；若现网平台限制采用B，但必须证明跨信号追溯和权限分离。
- Business decision required: 是。
- Resolution: A；OpenTelemetry统一采集并接入企业现有后端。留存已按ADR-0006～0010分层批准：业务事实/审批历史/明确留痕操作永久不可删除，网络与安全运行日志1年，普通Trace 90天、错误/高风险Trace 180天，原始指标90天、5分钟/小时聚合13个月，调试日志默认7天且专项最长30天。生产Trace采样按ADR-0011批准：普通成功请求10%，错误/高风险/审计失败/发布迁移100%。具体后端、访问角色和告警生产证据仍由P3-E05确认。
- Decision owner: 需求方（方向）；运维、安全、合规Owner（生产证据）
- Decision date: 2026-08-13

### Q-P3-006

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-03
- Area: 近生产性能环境（P3-E06）
- Question: 是否提供独立近生产性能环境，其规格、数据集、网络、测试账号和外部依赖如何与生产对齐？
- Why it blocks design/implementation: PRD的50并发、规模树、50MB、99%和60秒指标无法形成可信验收。
- Options: A. 建独立近生产环境；B. 使用缩小环境但提供经验证的缩放模型。
- Recommended technical default: A；B只作为资源确实受限时的过渡，且不能替代关键瓶颈的生产同级验证。
- Business decision required: 是，需测试/运维/数据Owner确认资源。
- Resolution: A；建设独立近生产性能环境。实际规格、数据集、网络和账号证据仍由P3-E06确认。
- Decision owner: 需求方（方向）；测试、运维、数据Owner（环境与证据）
- Decision date: 2026-08-13

### Q-P3-007

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: INT-01～INT-12适用项
- Area: 外部接口运行参数（P3-E07）
- Question: 是否按Feature建立接口配置档案，并由每个外部系统技术Owner逐操作确认endpoint引用、认证、白名单、timeout/retry和沙箱契约？
- Why it blocks design/implementation: 没有真实参数不能联调和上线，通用默认值可能造成重复副作用或误判成功。
- Options: A. 每个Feature进入开发前逐接口登记；B. 建平台级统一登记后由Feature引用具体版本。
- Recommended technical default: B；统一注册表治理，Feature引用不可变版本并在上线前验证实际操作。
- Business decision required: 是，需集成架构和外部Owner确认治理方式。
- Resolution: B；建立平台级统一接口配置注册表，Feature引用不可变版本并在上线前验证实际操作。
- Decision owner: 需求方（方向）；集成架构、各外部系统Owner（配置与证据）
- Decision date: 2026-08-13

### Q-P3-008

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: 全部需要历史数据迁移的V1/V2 Requirement
- Area: DDL漂移与迁移基线（P3-E09 / AI-MIG-000）
- Question: 当前DDL相对旧批准证据的差异采用`ACCEPT_CURRENT`、`RESTORE_APPROVED_BASELINE`、`AMEND_CURRENT`还是逐项`DEFER`？
- Why it blocks design/implementation: 历史迁移批次尚未形成，不能执行迁移程序或数据切换；不阻断当前SDS数据模型基线。
- Options: A. 先生成逐表/列/索引/约束差异，由数据架构和业务Owner逐项裁决；B. 整体恢复旧DDL后重新评审新增实体。
- Recommended technical default: A；当前领域模型已经演进，整体恢复可能丢失已确认能力，但未经逐项批准也不能接受当前DDL。
- Business decision required: 是，需批准每项漂移结论；执行程序只读生成差异，不授权生产迁移。
- Resolution: A；继续只读生成逐表/列/索引/约束差异，由数据架构和业务Owner逐项裁决，不整体恢复旧DDL。
- Confirmed naming decision: ADR-0019；业务表删除`pms_`，统一采用`<13领域编码>_<完整领域对象名称>`；表名默认使用完整英文词，仅允许`config`、`sn`两个已登记标准缩写；字段允许使用ADR登记且含义明确的受控缩写，并在无歧义时保持简短。52张表历史命名裁决已同步到当前核心迁移DDL和派生证据，并已纳入P3-E09模型基线。
- Confirmed project identity decision: ADR-0020；同一CRM项目的多合同/多订单不派生项目编码，只有独立交付边界才拆分子项目；项目编码租户内唯一，编码命名空间与可变项目层级分离，项目移动不得改码。该决策已同步DDL、字段目录和P3-E09逐项寄存器，并已纳入模型基线。
- Confirmed customer market classification decision: ADR-0021；市场部、系统部、拓展部、子行业四维分类归CUS，CRM组合目录落`cus_market_relation`；客户和项目直接保存四组编码/名称，禁止保存`relation_id`，也不推断为组织关系。该决策已同步DDL、字段映射和P3-E09逐项寄存器，并已纳入模型基线。
- Confirmed core migration schema decision: ADR-0022；当前DDL是迁移核心子集而非平台全量模型；4张技术公告治理表属于V3设计，不进入V1/V2核心DDL；跨领域使用逻辑引用；外部键映射支持目标角色和稳定顺序；当前唯一性使用生成标记；项目、合同、订单、SN及来源键不可复用；历史异常进入迁移问题并保留逐源证据。该决策已同步核心DDL、领域实体迁移策略、字段目录和P3-E09派生证据，并已纳入模型基线。
- P3-E09 requirement-owner model decisions: ADR-0023中的Q01～Q06业务语义仍有效；交付范围采用“项目节点—订单行当前唯一主记录+多条范围明细”，订单—执行单允许多个默认主执行单关系。当前DDL由ADR-0028历史清单与ADR-0030六表差量共同覆盖：逐项寄存器2,079项中994项`ACCEPT_CURRENT`、1,085项`AMEND_CURRENT`、0项`DEFER`。需求方逐项决策与当前哈希整体一致性复审均已关闭，P3-E09当前为`MODEL_BASELINE_READY`。P3-E09不定义迁移批准哈希，历史迁移或切换继续按Release范围受`AI-MIG-000`阻断。
- V1.7 DDL delta: ADR-0025，并由ADR-0027完成割接物理模型纠偏；10表V1.7差量保留为历史已接受证据。需求方 2026-08-13 确认`pm_project_maintenance`全表不迁移，只保留顶层表级排除审计；当前不预建历史工单/工时对象或空壳表。目录快照对象/表亦按需求方决策删除，INT-05/INT-09复用基础平台主数据、`plt_sync_batch`和`plt_external_key_mapping`。ADR-0030六表加入后当前模型为66表，P3-E09经整体一致性复审恢复为`MODEL_BASELINE_READY`。仅当Release包含历史迁移或数据切换时，`AI-MIG-000`才保持`BLOCKED`直至真实批次验证通过，并只在批准窗口内执行；普通功能Release为`NOT_APPLICABLE`，任何状态均不授权旧`dppms`写入。
- Cutover flow correction: ADR-0026；`CUT-01 / CutoverTask`是P1～P6唯一割接核心任务，`CUT-11`退出当前需求和CUT领域，`WO-06`后置为工单领域V3候选。原CUT-11三表及迁移映射必须从P3-E09候选删除后重新生成证据；该项为已确认变更，不再作为开放问题。
- Cutover physical model correction: ADR-0027；原CUT-11三表已删除，逐步骤执行与稳定观察不进入当前物理模型；P4保障人员安排与P6轻量闭环的物理项保留历史接受证据。ADR-0030新增CUT-03清单三表后当前候选为66表，哈希与隔离MySQL 8.4证据已重建但整体一致性待复审；不得把需求方接受或MySQL执行PASS解释为历史迁移、切换或生产批准。
- Decision owner: 需求方（方向）；数据架构、业务Owner、迁移负责人（逐项裁决与证据）
- Decision date: 2026-08-13

## 九月首发执行信息

以下问题来自`docs/superpowers/plans/2026-08-14-september-uat-go-live-project-plan.md`。它们不改变PRD V1.7业务范围，也不阻断52项首发Feature规格编制；未在最晚安全点关闭时，只阻断对应排期、联调、UAT、迁移或发布活动。

| ID | 待确认信息 | 当前是否必须确认 | 最晚安全点 | 阻断范围 | 需要确认人 | 状态 |
|---|---|---|---|---|---|---|
| Q-REL-001 | 三个交付组的人员名单、角色和投入比例 | 是；进入P1薄切片实施前必须确认 | P1启动 | 实施排期、并行切片承诺 | 项目经理、研发与测试负责人 | OPEN_EXECUTION |
| Q-REL-002 | UAT总负责人及各领域验收代表 | 否；可先编制UAT场景和用例 | P4 UAT准入评审前 | UAT准入、执行与签署 | 业务Owner、项目经理 | OPEN_UAT |
| Q-REL-003 | CRM、基础平台、设备连接与采集平台的联调Owner和可用时间 | 是；可先使用冻结契约和Mock开发，但必须明确真实联调窗口 | P1结束前确认窗口，P2结束前完成真实联调 | 对应Feature联调与发布 | 集成架构、各外部系统Owner | OPEN_INTEGRATION |
| Q-REL-004 | UAT环境和生产环境的运维负责人 | 否；不阻断Feature设计与开发 | P4环境准备开始前 | UAT环境、生产部署与发布 | 运维负责人 | OPEN_ENVIRONMENT |
| Q-REL-005 | 九月底采用全量发布还是限定组织/项目灰度发布 | 否；不阻断Feature设计与开发 | P4发布预演前 | 发布方案、Go/No-Go | 业务Owner、项目经理、运维 | OPEN_RELEASE |
| Q-REL-006 | 首发是否执行历史数据迁移；如执行，明确范围和业务对账人 | 是；Feature开发不受阻，但迁移程序和预演不得启动 | P1结束前确定是否迁移；P4前关闭AI-MIG-000适用批次 | 历史迁移、数据切换、上线准入 | 业务Owner、数据Owner、迁移负责人 | OPEN_MIGRATION |

处理原则：Q-REL-001、Q-REL-003、Q-REL-006是近期排期输入，需要优先确认；Q-REL-002、Q-REL-004、Q-REL-005按表中最晚安全点后置。未确认项不得用虚构人员、环境参数或迁移范围填充，但不影响无关Feature继续推进。

## Feature Ready问题

### Q-FPROJ-001

- Status: RESOLVED
- Requirement IDs: PM-01、PM-03
- Area: F-PROJ-001手动项目创建与模板初始化
- Question: PRD要求“无可用模板时项目保持创建草稿且不得进入S0”，但当前SDS只定义正式Project创建为`ACTIVE / S0`。创建草稿应采用独立`ProjectCreationDraft`聚合、给Project新增`DRAFT`状态，还是不持久化表单？
- Why it blocks design/implementation: 该选择会改变草稿业务身份、状态机、API和数据库；现已由需求方确认方案B并解除阻断。
- Options: A. 独立`ProjectCreationDraft`，提交后原子生成正式Project并保留草稿审计引用；B. 校验失败不持久化，只保留客户端表单；C. 给Project新增`DRAFT`生命周期。
- Recommended technical default: 历史推荐为A；需求方最终选择B，以避免新增草稿业务对象和Project状态。
- Business decision required: 已完成。方案B：失败时不持久化Project或创建草稿；当前页面可保留内存表单供修正，刷新后不保证恢复。
- Resolution: 方案B。批准依据为`CHG-PRD-2026-08-21-001`；不新增ProjectCreationDraft、Project DRAFT状态、草稿API、表或迁移。
- Blocking scope: 已解除。F-PROJ-001按失败无持久化语义继续Feature Spec和Technical Plan。
- Decision owner: 需求方（业务语义）；PROJ领域和数据架构负责人（SDS/物理契约回写）
- Decision date: 2026-08-21

### Q-FPROJ-002

- Status: RESOLVED
- Requirement IDs: PM-03、ACC-04
- Area: F-PROJ-001手动项目创建与模板初始化
- Question: PM-03要求项目创建时按模板加载交付件，但SDS明确交付件事实归ACC Context拥有、PROJ不得直接访问ACC Repository，且跨Context契约默认最终一致。项目创建成功是否必须等待ACC交付件实例全部落地？
- Why it blocks design/implementation: 该选择会改变`POST /projects`成功语义、事务/Outbox边界、初始化状态、重试补偿和AC-FPROJ-002/008/010的验收口径；现已由需求方确认同步全有或全无并解除阻断。
- Options: A. PROJ先提交并由ACC按Outbox事件最终一致初始化，存在`PENDING`；B. 同步编排但允许超时后保持可恢复处理中；C. 只冻结要求快照，进入阶段时再创建；D. 需求方选择：PROJ同步调用ACC内部应用接口，双方同库同Spring事务，要么全部提交，要么全部回滚，不产生中间状态。
- Recommended technical default: 历史推荐为A；需求方最终选择D，以项目创建完整性优先于跨库拆分和异步可用性。
- Business decision required: 已完成。创建成功必须同时证明ACC交付件实例全部落地；任一步失败则整体回滚。
- Resolution: 方案D，详见ADR-0032。PROJ不得直接访问ACC Repository；ACC内部应用接口必须参与调用方同一数据库事务，不得使用`REQUIRES_NEW`、异步消息、Saga、初始化`PENDING`或吞异常降级。
- Blocking scope: 已解除。F-PROJ-001按同步全有或全无语义继续Technical Plan；未来拆库/拆服务必须先批准业务语义变更。
- Decision owner: 需求方（创建完成语义和用户效果）；PROJ、ACC领域负责人（契约和补偿）
- Decision date: 2026-08-21

### Q-FCUS-001

- Status: RESOLVED
- Requirement IDs: CUS-03、INT-03
- Area: F-CUS-001 CRM字段级写入与本地物理契约
- Question: CRM权威字段、平台扩展字段、内部写API、来源版本、当前CRM映射唯一键、客户主表列约束和字段映射如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. CUS公开应用接口；B. 消费集成事件；C. 集成模块直写表。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: INT-03只调用`CustomerMasterDataApi`，不得直接写CUS表；CRM权威字段采用PRD明确范围，四维市场属性保存编码/名称；按`tenantId + crmCustomerId + sourceVersion`判定新旧，`eventId`防重复，同版本不同载荷进入冲突；客户编码软删除后仍占用；核心字段主表分列，来源映射、字段历史和同步快照独立追加保存。
- Decision owner: 需求方；CUS、集成与数据架构负责人
- Decision date: 2026-08-25

### Q-FCUS-002

- Status: RESOLVED
- Requirement IDs: CUS-03、EQP-01、PM-01
- Area: F-CUS-001跨域引用守卫与客户地点命令
- Question: 删除客户前哪些Owner参与引用守卫；客户Address/Site引用如何维护？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 统一批量引用守卫；B. 各域定制；C. CUS本地投影。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 删除前检查项目、设备、联系人、领域任务和外部问题记录等全部有效业务引用；任一守卫未知、超时或不可用时失败关闭。各Owner实现统一`CustomerReferenceGuardApi`批量契约并返回类型、数量和最小摘要，CUS不得跨域查表。CUS只保存Address/Site稳定ID、类型、来源版本和有效区间，写引用前调用`AssetLocationApi`校验租户、对象类型、存在性和版本。
- Decision owner: 需求方；CUS、PROJ、AST及相关领域负责人
- Decision date: 2026-08-25

### Q-FCUS-003

- Status: RESOLVED
- Requirement IDs: CUS-03
- Area: F-CUS-001独立customer模块迁移与权限契约
- Question: 旧客户实现如何迁移，新API、API模块和权限如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 一次性切换Owner；B. 短期双写；C. 长期兼容。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 新建`pms-module-customer`和稳定`pms-module-customer-api`；以前向迁移一次性切换Owner，不设双写期。旧客户API立即退出，新路径固定为`/api/v1/pms/customers`。联系方式默认脱敏，具备敏感查看权限且命中数据范围时详情可见明文，导出另需权限并审计。权限拆分为查询、创建、更新、删除、恢复、敏感查看和导出。
- Decision owner: 需求方；CUS、PROJ、数据与权限负责人
- Decision date: 2026-08-25

### Q-FAST-001

- Status: RESOLVED
- Requirement IDs: EQP-01、EQP-04、INT-02、INT-04
- Area: F-AST-001官网信息与V1外部来源交付边界
- Question: 官网信息Owner、维护方式及外部同步未完成时的验收边界如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. KNO受控维护；B. 自动采集；C. MES提供。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 官网信息归KNO，由授权人员受控维护来源URL、核验时间、摘要和版本，V1不建设自动爬取。AST通过KNO公开查询契约按产品/设备映射读取已发布版本，无记录显示未维护。`INT-02`、`INT-04`和`EQP-04`保持独立Feature；其未完成时F-AST-001允许使用已核验种子或受控替身验收主档消费和降级，但不得宣称外部同步完成。
- Decision owner: 需求方；AST、KNO与集成负责人
- Decision date: 2026-08-25

### Q-FAST-002

- Status: RESOLVED
- Requirement IDs: EQP-01、CUS-03
- Area: F-AST-001客户直接归属时态写契约
- Question: 设备客户归属Owner、命令、状态守卫和物理模型如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. AST单一Owner；B. CUS维护；C. 跟随项目。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: AST是设备当前客户直接归属、时态历史及租用/共管关系的单一Owner；稳定命令为`POST /api/v1/pms/devices/{id}/actions/assign-customer`。停用客户禁止新归属，已有关系与历史保留并进入待核对。物理模型采用当前唯一表加统一时态关系表；项目当前客户与设备当前客户不一致时保留双方事实并创建待核对项，不自动覆盖。
- Decision owner: 需求方；AST、CUS与数据架构负责人
- Decision date: 2026-08-25

### Q-FAST-003

- Status: RESOLVED
- Requirement IDs: EQP-01、EQP-02
- Area: F-AST-001字段级API、权限与机器物理契约
- Question: 详情DTO、权限、下载、来源状态及物理模型如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 固定外壳和分Tab DTO；B. 单一大DTO；C. 动态Map。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 设备详情采用固定摘要外壳和分Tab DTO，各切片统一返回`sourceSystem/sourceVersion/dataAsOf/syncStatus`。沿用现有资产查询/维护权限，仅新增项目归属、客户归属、冲突处置和配置Log下载高风险权限；Log查看同时校验设备查询和文件查看权限。下载链接默认5分钟、可配置并绑定用户。来源状态统一为`FRESH/STALE/FAILED/PENDING_MAPPING/NOT_AVAILABLE`。物理模型采用Device主表加MES/ITR/KNO等分来源表；序列号软删除后仍占用并沿用原deviceId恢复。
- Decision owner: 需求方；AST、KNO、权限与数据架构负责人
- Decision date: 2026-08-25

## PRD V1.8修订005待裁决项

### Q-PRD-005-01

- Status: RESOLVED
- Requirement IDs: PRE-04、SCH-01
- Area: 需求分析到实施方案的字段级章节映射
- Question: PRE-04的冗余需求、安全防护需求、运维需求和日志需求，应分别自动预填到SCH-01哪一个正式章节或结构化字段？
- Why it blocks design/implementation: 已由需求方裁决关闭，不再阻断设计与实现。
- Options: A. 需求方指定四类字段到现有SCH-01章节的逐项映射；B. 批准SCH-01新增明确章节/字段并定义版本、展示和验收规则；C. 明确四类字段只作为PRE-04版本附件/引用展示，不自动预填正文。
- Recommended technical default: 在需求方裁决前不执行这四类字段的自动章节映射；项目背景、项目目标、网络拓扑和IP规划继续按PRD已明确规则引用。
- Business decision required: 已完成。
- Resolution: 冗余、安全防护、运维、日志四类字段不要求全部映射到实施方案；仅按实施方案模板中已建立的字段级显式对应关系选择性预填，未映射字段不写入SCH-01，也不作为方案缺失项，继续保留在PRE-04版本中查询与追溯。
- Blocking scope: 已解除。
- Decision owner: 需求方；PRE/SOL领域负责人参与影响分析
- Decision date: 2026-08-29

## PRD V1.8 Requirement版本切片裁决记录

> 来源：`docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`。原8项阻断对应VS-001、VS-002、VS-003、VS-005、VS-006、VS-007、VS-008、VS-009，均已由需求方于2026-08-29裁决；本节按实际VS编号保留问题、选项和最终Resolution。VS-004、VS-010、VS-011属于审查中的补充边界确认，不曾作为规格阻断，完整结论保留在裁决清单和PRD修订007中。

### Q-PRD-VS-001

- Status: RESOLVED
- Requirement IDs: PM-08
- Area: V2服务经理自动指派
- Question: V2应直接生成并生效唯一主责指派，还是仅生成候选建议并继续由授权人员确认？
- Why it blocks design/implementation: 两种结果的责任生效时间、权限、异常状态和验收完全不同，现有“建议或结果”不能派生唯一Feature。
- Options: A. 唯一匹配自动生效，异常转人工；B. 只生成建议，始终人工确认；C. V2方向移至V3。
- Recommended technical default: A；符合“自动指派”名称，同时对无唯一结果保持失败关闭。
- Business decision required: 是，对应裁决VS-001。
- Resolution: 采用A。V2按已冻结并生效的匹配规则自动形成唯一主责指派；仅在唯一候选匹配时指派自动生效。无匹配或多匹配时不得自动选定，项目保持“待指派”，转由有权人员人工处理。
- Decision owner: 需求方；PROJ与组织权限Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-002

- Status: RESOLVED
- Requirement IDs: PM-11
- Area: V2甘特图与高级编排
- Question: “高级编排”具体包含哪些业务动作、依赖规则、状态影响、权限和验收？
- Why it blocks design/implementation: 当前只有方向性名称，无法判断甘特展示、依赖维护与其他编排能力是否属于同一可验收结果。
- Options: A. V2仅甘特展示和受控依赖维护；B. 补齐完整高级编排规格；C. 整体移至V3。
- Recommended technical default: A；先形成最小可验收业务结果，其余能力另行评审。
- Business decision required: 是，对应裁决VS-002。
- Resolution: 采用A。PM-11 V2仅交付甘特展示和受控依赖维护；依赖新增、更新、删除必须校验循环引用、跨项目非法关系和版本冲突。其他“高级编排”不属于当前V2承诺，须以明确Requirement重新评审。
- Decision owner: 需求方；PROJ Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-003

- Status: RESOLVED
- Requirement IDs: EXE-05
- Area: 单机风险V2自动识别、升级和分析
- Question: Q-15中的V2方向是否仍为当前正式承诺；若保留，其触发、规则、状态、权限和验收是什么？
- Why it blocks design/implementation: EXE-05正文和A.1只定义V1台账，无法为V2派生合法Feature与完成条件。
- Options: A. 转V3；B. 本次补齐V2规格；C. 删除该方向。
- Recommended technical default: A；避免未定义自动化进入V2承诺。
- Business decision required: 是，对应裁决VS-003。
- Resolution: 采用A。EXE-05保留V1风险台账与现有协同闭环；自动识别、自动升级和分析不进入V2，作为V3跨Requirement演进方向，重新启动前须补齐规则、权限和验收。
- Decision owner: 需求方；IMP与CRM集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-005

- Status: RESOLVED
- Requirement IDs: ACC-02、INT-05、INT-10
- Area: 满意度V1核心与V2增强边界
- Question: ACC-02的V2结果是否仅为自动触达，还是另有问卷、评分、整改重收或导出差量？
- Why it blocks design/implementation: 现有V1已完整包含后四类能力，§13.2的同名V2“增强”没有差量，机械拆分会重复完成事实。
- Options: A. V2仅自动触达；B. 补齐其他真实差量；C. 删除ACC-02 V2描述。
- Recommended technical default: A；保留V1完整业务事实，V2只增加可降级通道。
- Business decision required: 是，对应裁决VS-005。
- Resolution: 采用A。ACC-02 V1保留问卷模板、实例、评分判定、整改重收、客户签字和授权导出的完整核心闭环；ACC-02 V2只增加短信/邮件和钉钉自动触达，失败时回退二维码、外发链接或现场协助，不重复定义核心问卷事实。
- Decision owner: 需求方；ACC与通知集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-006

- Status: RESOLVED
- Requirement IDs: CUT-06
- Area: V2割接多角色填写
- Question: V2要由哪些角色分别填写哪些字段，字段Owner、提交顺序、冲突规则和最终归档权限是什么？
- Why it blocks design/implementation: PRD明确要求另行确认，当前无法建立字段、权限、状态和验收契约。
- Options: A. 转V3；B. 本次补齐并保留V2；C. 删除多角色方向。
- Recommended technical default: A；V1一线统一填写已形成完整闭环。
- Business decision required: 是，对应裁决VS-006。
- Resolution: 采用A。CUT-06仅保留V1一线统一填写、闭环、归档和经定义的ITR结果出向；多角色分工填写不进入V2，转为V3跨Requirement演进方向，重新启动前须明确字段Owner、角色权限、提交顺序和冲突规则。
- Decision owner: 需求方；CUT与权限Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-007

- Status: RESOLVED
- Requirement IDs: INT-02、INT-04、EQP-07、CUT-06
- Area: ITR入向/出向与跨版本Owner
- Question: INT-02@V1、INT-04@V2、EQP-07@V2和CUT-06的公告、故障、割接触发及结果回传责任应如何唯一分配？
- Why it blocks design/implementation: 当前INT-02@V1验收V2公告和问题单结果，并混入未定义出向，造成版本倒挂和重复Owner。
- Options: A. INT-02@V1保留版本/割接入向，INT-04@V2拥有公告，INT-02@V2拥有故障入向及定义后的CUT出向；B. INT-02仅V1，其余归消费需求；C. INT-02整体移V2。
- Recommended technical default: A；按事件类型和消费版本形成可独立验收结果。
- Business decision required: 是，对应裁决VS-007。
- Resolution: 采用A。INT-02@V1只承接设备版本历史和ITR割接任务入向；INT-04@V2唯一拥有技术公告完整同步；INT-02@V2承接故障入向供EQP-07消费，并承接PRD已定义的CUT结果出向。出向失败不回滚本地CUT归档，进入重试与对账。
- Decision owner: 需求方；AST、KNO、CUT与集成Owner共同裁决
- Decision date: 2026-08-29

### Q-PRD-VS-008

- Status: RESOLVED
- Requirement IDs: INT-03、CUS-03
- Area: CRM客户同步V2治理
- Question: INT-03 V2“扩展同步治理、对账和冲突处理工作台”相对V1已有幂等、冲突、重试和对账具体新增什么？
- Why it blocks design/implementation: 没有新增动作和验收，无法判断是否存在独立V2业务结果。
- Options: A. 只保留V1，工作台转V3；B. 本次补齐V2批量治理规则；C. 将V1部分治理义务后移V2。
- Recommended technical default: A；不因页面形态重复创建业务切片。
- Business decision required: 是，对应裁决VS-008。
- Resolution: 采用A。INT-03只保留V1客户同步、幂等、冲突、重试和对账；未定义的同步治理工作台不形成V2切片，作为V3跨Requirement演进方向重新评审。
- Decision owner: 需求方；CUS与CRM集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-009

- Status: RESOLVED
- Requirement IDs: NFR-02、INS-02、INS-03
- Area: 巡检命令超时后的后续命令策略
- Question: 当前命令超时后，剩余命令应一律继续、一律停止，还是按已发布巡检规则决定？
- Why it blocks design/implementation: NFR-02规则与验收互相冲突，会产生不同设备动作、安全结果和测试判定。
- Options: A. 按已发布巡检规则决定；B. 一律继续；C. 一律停止。
- Recommended technical default: A；与规则Owner和不同设备场景相符。
- Business decision required: 是，对应裁决VS-009。
- Resolution: 采用A。巡检命令超时后，当前命令必须终止并标记失败；剩余命令是否继续严格按任务冻结的已发布巡检规则决定，并记录采用的规则版本、决定和执行结果。
- Decision owner: 需求方；SRV、PLT采集与安全Owner共同裁决
- Decision date: 2026-08-29

## F-CUT-001 Feature Ready 待裁决项

### Q-FCUT001-001

- Status: RESOLVED
- Requirement IDs: CUT-07、CUT-09
- Area: 双机部署规范性检查项权威清单与发布数量校验
- Question: 参考附件中的双机检查项内容和行数是否参与业务口径裁决或Feature Ready判断？
- Why it blocks design/implementation: 已由需求方明确关闭；参考附件只辅助理解名称、字段与界面，不拥有业务语义或验收口径。
- Options: A. 仅作参考；B. 作为第二事实源参与裁决；C. 以附件覆盖正式需求。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 采用A。PRD、SDS和Feature Spec决定业务语义与验收；XLSX/HTML可以查看和引用为实现参考，但其内容、数量或相互差异不形成需求裁决、不建立新的发布门禁，也不阻断Feature。CUT-07实现通用CRUD、版本和发布校验能力，初始化只落正式需求明确定义的字典及示例组合，不把附件行数硬编码为业务规则。
- Blocking scope: 已解除。
- Decision owner: 需求方；CUT领域负责人、测试负责人参与影响分析
- Decision date: 2026-08-29

### Q-FCUT001-002

- Status: RESOLVED
- Requirement IDs: CUT-07、CUT-04
- Area: 割接方案模板章节配置的领域与物理载体
- Question: CUT-07要求“按割接类型/级别配置方案模板章节”，应由哪个版本化对象和物理表承载？
- Why it blocks design/implementation: 已通过不增加Owner表的物理细化关闭，不改变PRD业务语义或ADR三表边界。
- Options: A. 扩展ADR/SDS，为CUT-07增加版本化方案模板章节子对象和独立物理表；B. 明确方案模板章节配置不属于CUT-07当前V1切片并修订PRD，由后续CUT-04 Feature单独拥有；C. 批准将章节Schema作为`CutoverConfigurationRevision`根内的结构化版本快照，并补齐字段、校验和消费契约。
- Recommended technical default: C。
- Business decision required: 已完成。
- Resolution: 采用C。方案模板章节以`CutoverConfigurationRevision.plan_template_section_snapshot`结构化JSON承载，随配置根统一草稿、发布、复制修订和停用；每章包含稳定章节键、标题、排序、适用割接类型代码、适用等级代码和必填标识。CUT-04只冻结所消费配置revision及章节快照，不把运行方案写回配置根。保持ADR-0031批准的三表模型，不新增第四张Owner表。
- Blocking scope: 已解除。
- Decision owner: 需求方；CUT领域负责人、数据架构负责人
- Decision date: 2026-08-29
