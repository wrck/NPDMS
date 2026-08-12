# SDS Phase 1 独立第三方评审报告

> 评审方式：独立只读评审<br>
> 评审依据：PRD V1.6、基线快照、正式工程链、Phase 1 SDS 工作稿及追溯矩阵<br>
> 评审结论：`NO-GO`，不得进入 Phase 2<br>
> 评审范围：正确性、架构边界、可追溯性、权限安全、阶段门禁

## 1. 总体结论

Phase 1 已具备需求数量覆盖和初步领域/聚合设计，但尚未达到可进入数据、API、集成详细设计的退出标准。当前问题不是 PRD 需求缺失，而是 Phase 1 的工程映射仍存在精度不足和真实前置条件未闭环。

## 2. Critical：阻塞项

| 编号 | 问题 | 证据 | 影响 | 处理要求 |
|---|---|---|---|---|
| C-01 | 三个 Phase 1 硬门禁均未关闭 | `docs/design/phase-1-review.md:6,29-31,41` | Owner、实现边界和集成边界都不可复核 | 关闭 Owner 签署、实现工作包登记、采集平台集成形态确认后再复审 |
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
| O-02 | `phase-1-review.md` 的聚合边界检查应显式登记 R-01/R-02，不应只写 PASS-WITH-FOLLOWUP |
| O-03 | Phase 1 领域 Owner 签署后需重新生成矩阵，并将 `Phase1-WORKING` 转为正式 SDS 版本引用 |

## 5. 正向确认

- PRD 正式需求覆盖为 115/115，V1 57 项、V2 58 项。
- PRD 语义校验、13领域校验、单元测试和格式校验均通过。
- `Field Execution` 作为 bounded context 本身不算过大；拆分为 ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、FieldQualityCheck、DeliveryEvidence 的方向合理。
- IMP 上传、ACC 审核/齐套校验/归档的职责划分符合当前 PRD 语义。

## 6. 最终判定

`NO-GO`。在 C-01～C-03 和 R-01～R-04 关闭并完成独立复审前：

- 不得生成 Phase 2 数据库、API、事件和集成详细契约；
- 不得把旧 `specs` 直接当作设计基线；
- 不得开始正式代码实现；
- 继续保留 `docs/design/phase-1-review.md` 的 `NOT_READY_FOR_PHASE_2` 状态。
