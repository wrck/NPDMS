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
- Why it blocks design/implementation: 无法验证发布失败或灾难后的业务恢复，不可批准Phase 3。
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
- Why it blocks design/implementation: 17分册无法落地，高风险审计fail-closed和NFR证据无法验证。
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
- P3-E09 requirement-owner model decisions: ADR-0023中的Q01～Q06业务语义仍有效；交付范围采用“项目节点—订单行当前唯一主记录+多条范围明细”，订单—执行单允许多个默认主执行单关系。ADR-0028已绑定当前DDL哈希接受`Q07 A、Q08 A、V1.7 A、Q09～Q14 A`九组完整清单：逐项寄存器1,883项中994项`ACCEPT_CURRENT`、889项`AMEND_CURRENT`、0项`DEFER`。需求方逐项决策缺口已关闭，独立整体一致性复审为`IN_REVIEW`；`approvedDdlSha256`显式为空且仅由未来历史迁移门禁管理，fresh reviewer给出`GO`前不得放行P3-E09模型基线、历史迁移或切换。
- V1.7 DDL delta: ADR-0025，并由ADR-0027完成割接物理模型纠偏；当前候选为60表、10表V1.7差量。需求方 2026-08-13 确认`pm_project_maintenance`全表不迁移，只保留顶层表级排除审计；当前不预建历史工单/工时对象或空壳表。目录快照对象/表亦按需求方决策删除，INT-05/INT-09复用基础平台主数据、`plt_sync_batch`和`plt_external_key_mapping`。当前哈希与规模以重建证据为准；P3-E09为`MODEL_BASELINE_REVIEW_PENDING`，不放行SDS/Feature模型输入；`AI-MIG-000`、历史迁移和数据切换仍`OPEN`，未经真实批次验证不得执行，也不授权旧`dppms`写入。
- Cutover flow correction: ADR-0026；`CUT-01 / CutoverTask`是P1～P6唯一割接核心任务，`CUT-11`退出当前需求和CUT领域，`WO-06`后置为工单领域V3候选。原CUT-11三表及迁移映射必须从P3-E09候选删除后重新生成证据；该项为已确认变更，不再作为开放问题。
- Cutover physical model correction: ADR-0027；原CUT-11三表已删除，逐步骤执行与稳定观察不进入当前物理模型；P4保障人员安排与P6轻量闭环的当前物理项已通过ADR-0028九组清单获得Requirement Owner接受。当前候选为60表、10表V1.7差量，哈希与隔离MySQL 8.4证据已重建并纳入模型基线；不得把需求方接受或模型基线解释为历史迁移、切换或生产批准。
- Decision owner: 需求方（方向）；数据架构、业务Owner、迁移负责人（逐项裁决与证据）
- Decision date: 2026-08-13
