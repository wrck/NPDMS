# SDS Phase 1 独立第三方评审报告

> 评审方式：独立只读评审<br>
> 评审依据：PRD V1.6、基线快照、正式工程链、Phase 1 SDS 工作稿及追溯矩阵<br>
> 当前评审结论：`GO`，`READY_FOR_PHASE_2`（2026-08-13 定点复审）<br>
> 历史评审结论：下文保留各轮 `NO-GO` 及其整改证据，不作为当前放行状态<br>
> 评审范围：正确性、架构边界、可追溯性、权限安全、阶段门禁

## 1. 总体结论

Phase 1 的需求覆盖、领域/聚合责任、状态与流程边界、授权落点、跨 Context 契约方向及 Q2 实现证据链已通过最终定点复审。历史发现和中间 `NO-GO` 结论保留于下文，用于说明修复路径。

## 2. Critical：阻塞项

| 编号 | 问题 | 证据 | 影响 | 处理要求 |
|---|---|---|---|---|
| C-01 | 三个 Phase 1 硬门禁均未关闭 | `docs/engineering/gates/phase-1/gate-status.md:6,29-31,41` | Owner、实现边界和集成边界都不可复核 | 关闭 Owner 签署、实现工作包登记、采集平台集成形态确认后再复审 |
| C-02 | 工程链与根 AGENTS.md 的基线路径冲突 | `AGENTS.md:17` 仍读取不存在的 `docs/baseline/prd-v1.4.md`；正式工程链指向 V1.6 | 第三方无法按唯一规则复现基线读取和审查 | 统一根规则为 `docs/baseline/prd-v1.6.md`，或明确 AGENTS.md 已废止并保留唯一入口 |
| C-03 | INT-12 集成形态仍未决 | `docs/design/03-system-architecture.md:3-5,9-17,34-36` | 鉴权、任务下发、回调、网络边界和执行身份无法形成 Phase 2 契约 | 架构负责人确认子应用/模块形态及接口契约 Owner |

## 3. Required：进入 Phase 2 前必须修正

| 编号 | 问题 | 证据 | 影响 | 处理要求 |
|---|---|---|---|---|
| R-01 | Field Execution 需求被机械映射到全部五个聚合 | `docs/traceability/requirement-matrix.md` 中 EXE/IMP 行；`scripts/generate_requirement_traceability.py` 的 IMP 默认映射 | Requirement→Aggregate→API/Data 责任不精确 | 至少按 EXE-01 到货、EXE-02 安装、EXE-03/04采集结果、EXE-05风险、EXE-06割接门禁、IMP-01/02质量安全逐项拆分 |
| R-02 | 四个现场实施聚合没有完整状态机 | `docs/design/02-domain-model.md:37-47` 声明独立聚合；`docs/design/05-state-machine.md` 未定义 ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、FieldQualityCheck | Phase 2 无法确定状态字段、迁移、终态和事件 | 为每个聚合补核心状态、迁移守卫、角色、门禁和事件；或明确其为无状态事实记录 |
| R-03 | IMP-01/IMP-02 整改、复核、豁免/阻断工作流缺失 | `docs/design/06-workflow-design.md` 未列现场质量/安全检查流程；PRD对应验收要求整改复核和高风险阻断 | 质量安全能力无法形成可执行流程 | 增加检查提交→复核→整改→复核通过/阻断的工作流；豁免必须有明确角色、依据和审计 |
| R-04 | 权限设计仍停留在抽象 Scope | `docs/design/07-authorization-design.md:9-28` | 未落到项目树后代、设备当前归属、订单交付范围、现场批次和凭证五元组 | 建立操作级权限矩阵，覆盖 EXE-03/04、CUT-06、INS-02/04 和凭证选择/临时输入/保存为凭证 |

## 4. Optional：不阻塞但应纳入后续

| 编号 | 建议 |
|---|---|
| O-01 | 追溯矩阵“来源追溯”列目前多数为空，应补充稳定 PRD 章节锚点或需求正文定位 |
| O-02 | `gate-status.md` 的聚合边界检查应显式登记 R-01/R-02，不应只写 PASS-WITH-FOLLOWUP |
| O-03 | Phase 1 领域 Owner 签署后需重新生成矩阵，并将 `Phase1-WORKING` 转为正式 SDS 版本引用 |

## 4.1 当前处置状态（2026-08-12复核）

以下内容保留原始评审发现，同时记录本轮确认后的处置状态：

| 原编号 | 当前状态 | 处置证据 |
|---|---|---|
| C-01 | PARTIALLY_RESOLVED | Q1、Q3 已确认；Q2 实现工作包登记仍为 `BLOCKED-SDS-02`，见 `gate-status.md` |
| C-02 | RESOLVED | `AGENTS.md` 已统一读取 `docs/baseline/prd-v1.6.md`；修正提交 `14a92fd` |
| C-03 | RESOLVED_BY_REQUESTER | Q3 已确认 V1 优先采用现有采集平台子应用，具体端点和部署清单留到 Phase 2 |
| R-01 | VERIFIED | EXE-01～EXE-06、IMP-01/02 已逐项映射到明确聚合，见 `docs/traceability/requirement-matrix.md:48-53,130-131` |
| R-02 | VERIFIED_WITH_REPAIR | Implementation Execution 事实聚合已有状态机；本轮补充 Device 事实边界并合并 CollectionTask 重复定义 |
| R-03 | VERIFIED | 实施质量/安全检查已具备提交、复核、整改、豁免/阻断流程，见 `docs/design/06-workflow-design.md:17-18` |
| R-04 | VERIFIED | 已形成实施执行操作矩阵及凭证五元组约束，见 `docs/design/07-authorization-design.md:17-34` |

## 5. 正向确认

- PRD 正式需求覆盖为 115/115，V1 57 项、V2 58 项。
- PRD 语义校验、13领域校验、单元测试和格式校验均通过。
- `Field Execution` 作为 bounded context 本身不算过大；拆分为 ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、FieldQualityCheck、DeliveryEvidence 的方向合理。
- IMP 上传、ACC 审核/齐套校验/归档的职责划分符合当前 PRD 语义。

## 6. 原始评审判定

`NO-GO`。原始评审要求在 C-01～C-03 和 R-01～R-04 关闭并完成独立复审前：

- 不得生成 Phase 2 数据库、API、事件和集成详细契约；
- 不得把旧 `specs` 直接当作设计基线；
- 不得开始正式代码实现；
- 继续保留 `docs/engineering/gates/phase-1/gate-status.md` 的 `NOT_READY_FOR_PHASE_2` 状态。

## 7. 当前复审判定

`NO-GO`。C-02、C-03 和 R-01～R-04 已有处置证据；但 C-01 仍因 `BLOCKED-SDS-02` 的实现工作包登记未完成而部分阻塞。当前仍不得生成 Phase 2 数据库、API、事件和集成详细契约，也不得开始正式代码实现。

## 8. 2026-08-13 最终独立复审发现

### 8.1 复审结论

`NO-GO`。未发现 Critical，但下列 Required 项在定点复审通过前仍阻止 Phase 2。

| 编号 | 发现 | 处置状态 | 修复证据 |
|---|---|---|---|
| R-N01 | 实施执行授权矩阵发明/错配审批角色 | `FIXED_PENDING_REREVIEW` | `docs/design/07-authorization-design.md` 已将到货、安装确认回归项目经理，删除采集结果的未定义人工审批节点 |
| R-N02 | `CollectionTaskRequested` 生产者/消费者方向写反 | `FIXED_PENDING_REREVIEW` | `docs/design/02d-cross-context-contracts.md` 已改为业务 Context 发起、Device Access & Collection 消费，另建 `CollectionTaskAccepted` 表达接受回执 |
| R-N03 | Q2 运行边界、导入状态、缓存事实与 `releaseId` 证据链冲突 | `FIXED_PENDING_REREVIEW` | 实施仓库 `856d052`；`docs/engineering/gates/phase-1/q2-evidence-manifest.json`；业务 `baseCommit=3c54ee1...` |
| R-N04 | 正式 SDS 头部元数据、矩阵 SDS 链接和 Owner 状态未同步 | `FIXED_PENDING_REREVIEW` | `docs/design/01～07`及`02a～02e`已增加 PRD/Requirement/状态/Owner；生成器输出稳定 SDS 链接；`requirement-baseline.yaml` 改为 `OWNER_SIGNED` |

### 8.2 历史项状态

`C-01=PARTIALLY_RESOLVED`（Q2 修复待定点复审）；`C-02=RESOLVED`；`C-03=RESOLVED`；`R-01=RESOLVED`；`R-02=RESOLVED`；`R-03=RESOLVED`；`R-04=PARTIALLY_RESOLVED`（角色错配已修复，待定点复审）。

### 8.3 非 Phase 1 阻塞的质量债务

`corepack pnpm ts:check` 当前失败，生产构建通过不能覆盖该事实。它不属于 Phase 1 Gate，但必须作为任何前端 Feature 实现前的质量门禁，不得关闭检查或放宽 TypeScript 规则。

## 9. 2026-08-13 最终定点复审

| 项目 | 最终状态 | 复审证据 |
|---|---|---|
| R-N01 | `RESOLVED` | 到货、安装、配置采集和联调权限与 PRD 一致，未保留配置采集人工审批发明 |
| R-N02 | `RESOLVED` | `CollectionTaskRequested` 仅作为业务 Context 到 Device Access & Collection 的请求；`CollectionTaskAccepted` 已统一为采集 Context 出向事件 |
| R-N03 | `RESOLVED` | 实施仓库 `856d052`、`q2-evidence-manifest.json`、`baseCommit=3c54ee1...` 与宿主机应用/Docker 基础设施边界一致 |
| R-N04 | `RESOLVED` | 12 份正式 SDS 元数据完整；115 行矩阵均含可解析 SDS 链接；Owner 状态为 `OWNER_SIGNED` |

最终结论：`GO`。Q1、Q2、Q3 均为 `PASS`；C-01～C-03、R-01～R-04、R-N01～R-N04 全部关闭；Phase 1 为 `APPROVED / READY_FOR_PHASE_2`。`ts:check` 继续作为前端 Feature 实现前的强制前置门禁，不影响 Phase 1 放行。
