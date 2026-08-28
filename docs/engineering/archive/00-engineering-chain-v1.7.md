# 项目实施交付管理平台工程化实施链 V1.7

> 文档状态：`SUPERSEDED`<br>
> 适用基线：`需求/PRD-项目实施交付管理平台.md` V1.7<br>
> 基线快照：`docs/baseline/prd-v1.7.md`<br>
> 需求追溯：`docs/traceability/requirement-matrix.md`<br>
> 替代版本：`docs/engineering/00-engineering-chain.md` V1.8；本文件仅作历史依据，不再作为执行入口。

## 1. 目标

将 PRD 转换为可设计、可实现、可测试、可审计、可发布的工程资产，并让 Codex 在明确 Gate 下推进，而不是直接从整份 PRD 批量生成代码。

本版本以 PRD V1.7 的 104 项 V1/V2 正式需求为工程输入；V3 与 OUT_OF_SCOPE 仅保留演进/排除追溯，不得进入当前实现范围。需求基线、领域归属和下游资产状态以基线快照及追溯矩阵为准。

### 1.1 当前资料优先级

1. `需求/PRD-项目实施交付管理平台.md`：唯一业务语义基线。
2. `docs/baseline/prd-v1.7.md` 与 `docs/baseline/requirement-baseline.yaml`：冻结快照和范围元数据。
3. `docs/design/`：本阶段生成并通过评审的 SDS。
4. `docs/decisions/*.md`：已批准决策与待确认问题。
5. `specs/001-project-delivery-platform/domains/*.md`：PRD派生的13领域需求规格；与 V1.7 PRD 冲突时以PRD为准并重新生成，不允许直接改写派生文件。

过程审查和门禁证据不再与正式 SDS 混放：

- `docs/engineering/gates/`：各阶段的门禁、独立评审、评审输入和历史归档。
- `docs/engineering/gates/phase-1/gate-status.md`：Phase 1 当前门禁汇总及放行状态。
- `docs/engineering/gates/phase-1/input/`：外部/上游评审输入，保持原始内容。
- `docs/engineering/gates/phase-1/archive/`：已替代的历史评审材料，只用于追溯。

门禁证据不具有独立的业务或设计权威性；评审结论必须经确认后回写正式 SDS、决策记录及当前阶段 `gate-status.md`，才能改变工程状态。

所有文档目录均遵循 [`docs/README.md`](../README.md) 的分类、状态、晋级和归档规则。新增工程资产前必须先完成文档分类；正式目录中不得出现未登记的评审稿、计划稿、输入稿或临时副本。

旧 `specs` 的领域 Owner 映射也只是迁移参考，须在 SDS Phase 1 重新确认，不得把旧规格直接当作当前设计输入。

## 2. 总链路

PRD Baseline
-> SDS Phase 1
-> SDS Review 1
-> SDS Phase 2
-> SDS Review 2
-> SDS Phase 3
-> SDS Baseline
-> Feature Spec
-> Implementation Plan
-> Tasks
-> Codex Implementation
-> Automated Test
-> Review
-> SIT
-> UAT
-> Release

## 3. SDS 三阶段

### Phase 1：总体与业务结构

输出：

- requirement traceability
- domain model
- system architecture
- module design
- state-machine design
- workflow design
- authorization design

Gate：

- V1/V2/V3/OUT_OF_SCOPE 边界正确
- bounded context 清晰
- 核心聚合及责任清晰
- workflow 与 state machine 职责分离
- 功能/数据/操作/字段/临时授权均有落点
- 不产生 PRD 外业务规则

### Phase 2：实现契约

输出：

- data model
- domain entity migration alignment（08a，作为data model稳定补充分册）
- database design
- API design
- event design
- integration design
- file design
- exception/idempotency design
- cache/concurrency design

Gate：

- 数据 Owner 明确
- 版本、快照、历史、审计可实现
- API 可追溯 Requirement ID
- 状态变化通过 command/transition 实现
- 事件定义 producer/consumer/idempotency
- 外部接口具备 timeout/retry/reconciliation/degradation

### Phase 3：运行与发布保障

输出：

- security design
- audit and observability
- deployment design
- performance design
- test design
- production evidence register与逐Owner证据包
- Phase 3 gate status、自审和独立复审

Gate：

- NFR 有技术实现与验证方案
- 发布、迁移、回退可执行
- 安全与审计不存在明显缺口
- 测试覆盖正常/异常/权限拒绝/幂等/并发
- P3-E01～E09均已定义证据契约、Owner、验收标准和最晚安全门禁，不把部署实例或运行结果前置为SDS阻断
- `phase3-evidence-register.json.overallStatus = READY_FOR_SDS_BASELINE`表示证据契约不再阻断逻辑设计，不表示可生产发布

约束：

- ADR确认的架构方向不等于生产事实通过；不得据此将`OPEN`直接改为`VERIFIED`。
- 阻断必须落在最晚且仍能避免风险的阶段：设计缺口阻断SDS，契约缺口阻断Feature Ready，环境实例缺口阻断部署，运行验证缺口阻断验收/发布，迁移证据缺口只阻断历史迁移与切换。
- 门禁位置同时考虑返工收益：会改变领域边界、数据模型、API、权限、状态机、基础技术契约，且提前确认能显著减少返工的事项，应前置到SDS或Feature Ready；只填写既定契约下的厂商实例、环境参数、容量、Owner和运行报告，不得无收益地前置。
- 判断顺序：先判断“不确定项是否会改变设计”，再判断“提前确认是否显著降低返工”，最后选择“仍可避免不可逆风险的最晚阶段”。不得仅因未来发布必需就默认阻断当前阶段。
- 每项工程门禁、设计分册、证据模板和自动化必须能明确对应至少一项直接收益：提升业务功能或交付质量、降低确定性返工、保护不可逆操作、提高验证效率。无法说明直接收益的治理资产不得新增。
- 优先复用Git、现有机器契约、测试和正式文档已经保存的事实；不得重复抄写元数据、预建尚无真实业务批次的审批流程，或为未来可能性建设与当前系统设计和实现无关的重型治理结构。
- 采用满足当前风险控制的最小方案；后续风险出现真实触发条件时再增量增强，不以形式完整代替业务与功能价值。
- P3-E01～E06属于部署、专项验收或生产发布门禁；P3-E07按Feature阻塞真实联调和上线；P3-E08阻塞前端Feature验收或发布。P3-E09中的表、字段和约束漂移会改变数据模型，前置阻断SDS数据模型基线；通过后仍继续约束历史迁移实施与切换。
- Owner提交必须从`docs/engineering/gates/phase-3/evidence-packet-templates/`复制到版本化`submissions/`，通过机器校验并保留独立复核记录。
- 证据契约、逻辑控制或验收规则缺失时，SDS Phase 3保持`IN_REVIEW`。仅实际设施名称、环境参数或运行报告尚未产生时，不阻断SDS总册及无关Feature Spec，但对应下游门禁保持`OPEN`。

## 4. SDS 完成后的研发循环

每个 Feature：

Feature Spec
-> Technical Plan
-> Tasks
-> Code
-> Unit Test
-> Integration Test
-> Authorization Negative Test
-> Business Negative Test
-> Review
-> Merge

## 5. Definition of Ready

Feature 进入开发前必须满足：

- Requirement ID 明确
- Scope 明确
- Feature Spec 完成
- Business Rules 完整
- State Model 确定
- Permission 确定
- Domain Model 确定
- API 确定
- Data Change 确定
- Integration Contract 确定
- Acceptance Criteria 确定
- Out of Scope 确定
- 无阻塞型 Open Question

否则标记 `NOT_READY`。

## 6. Definition of Done

- Build Pass
- Unit Test Pass
- Integration Test Pass
- Authorization Negative Test Pass
- Business Rule Test Pass
- API Contract Pass
- DB Migration Verified
- Audit Verified
- Logging Verified
- No Secret Leakage
- Requirement Traceability Updated
- Documentation Updated

## 7. 第一条 Vertical Slice

优先跑通平台骨架，而不是先做所有表或所有 Controller。

建议：

认证/登录
-> 客户基础数据
-> 手动创建项目
-> 选择项目模板
-> 实例化阶段/里程碑/任务/交付件
-> 人工指派服务经理
-> 项目详情
-> 项目树
-> 权限
-> 审计

完成后验证：

UI -> API -> Application -> Domain -> Repository -> DB -> Permission -> Audit -> Test
