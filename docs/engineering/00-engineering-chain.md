# 项目实施交付管理平台工程化实施链 V1.6

> 文档状态：正式工程链<br>
> 适用基线：`需求/PRD-项目实施交付管理平台.md` V1.6<br>
> 基线快照：`docs/baseline/prd-v1.6.md`<br>
> 需求追溯：`docs/traceability/requirement-matrix.md`<br>
> 唯一正式工程链文件：本文件；`docs/engineering/archive/` 仅保留历史工程链稿，不作为执行依据；过程门禁证据统一位于 `docs/engineering/gates/`。

## 1. 目标

将 PRD 转换为可设计、可实现、可测试、可审计、可发布的工程资产，并让 Codex 在明确 Gate 下推进，而不是直接从整份 PRD 批量生成代码。

本版本以 PRD V1.6 的 115 项 V1/V2 正式需求为工程输入；V3 与 OUT_OF_SCOPE 仅保留演进/排除追溯，不得进入当前实现范围。需求基线、领域归属和下游资产状态以基线快照及追溯矩阵为准。

### 1.1 当前资料优先级

1. `需求/PRD-项目实施交付管理平台.md`：唯一业务语义基线。
2. `docs/baseline/prd-v1.6.md` 与 `docs/baseline/requirement-baseline.yaml`：冻结快照和范围元数据。
3. `docs/design/`：本阶段生成并通过评审的 SDS。
4. `docs/decisions/*.md`：已批准决策与待确认问题。
5. `specs/001-project-delivery-platform/domains/*.md`：历史领域规格，仅作参考；与 V1.6 PRD 冲突时不得直接采用。

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

Gate：

- NFR 有技术实现与验证方案
- 发布、迁移、回退可执行
- 安全与审计不存在明显缺口
- 测试覆盖正常/异常/权限拒绝/幂等/并发

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
