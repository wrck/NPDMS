# Codex Prompt：SDS Phase 1 Domain Model Refactoring

## 目标

基于当前 `docs/design/02-domain-model.md` 和《SDS Phase 1 领域模型完整评审建议》，修订领域模型。

不要进入数据库设计、API 设计或业务编码。

## 必须读取

1. `AGENTS.md`
2. PRD baseline
3. `docs/design/02-domain-model.md`
4. `docs/engineering/gates/phase-1/archive/02-domain-model-full-review.md`

## 必须完成

1. 将 `Field Execution` 全部统一为：
   - English: `Implementation Execution`
   - 中文：`实施执行域`
   - 代码模块建议：`implementation`

2. 新增：
   - `Device Access & Collection`
   - `Work Order & Time`

3. `FieldQualityCheck` 标记：
   - P1
   - V2
   - NOT_REQUIRED_FOR_V1_GATE

4. 修订 Customer & Asset：
   - 明确 CRM/MES/ITR/Platform 字段级 Owner
   - 不得将外部权威字段声明为平台 Owner

5. 修订 `Contract & Fulfillment`：
   - 选择：
     A. `Commercial Reference & Fulfillment`
     B. 合并分配规则到 Project Delivery，仅保留外部 Reference
   - 给出选择理由

6. 重构 `Platform Governance`：
   不再作为万能业务 Context，拆出逻辑平台能力。

7. 明确 `Inspection & Service` 中 Service 的业务范围；
   如 PRD 不能证明同一一致性边界，则改为 `Inspection`。

8. 建立唯一 Aggregate Owner Matrix。

9. 对所有 Candidate Aggregate 执行 Boundary Decision：
   - independent lifecycle
   - invariant
   - concurrency
   - transaction boundary
   - independent access
   - reference/projection possibility

10. 决定 `DeliveryEvidence`：
    - Aggregate Root
    或
    - source aggregate 内 `EvidenceReference`
    并记录理由。

11. 重画 Context Map：
    - 不使用 Aggregate 名作为 Context 节点；
    - 标注 upstream/downstream；
    - 标注 ACL / Customer-Supplier / Published Language 等关系；
    - 明确 Device Access & Collection 是 IMP/CUT/INS 公共能力。

12. 扩展跨域契约：
    - no cross-context repository
    - no cross-context direct DB update
    - state owner
    - outbox/inbox
    - idempotency
    - event version
    - trace
    - snapshot/version
    - authorization context
    - file reference
    - external status mapping

13. 建立 V1/V2 Scope Matrix。

## 输出文件

修订：

`docs/design/02-domain-model.md`

新增：

`docs/design/02a-context-map.md`
`docs/design/02b-aggregate-boundary-decisions.md`
`docs/design/02c-data-ownership-matrix.md`
`docs/design/02d-cross-context-contracts.md`
`docs/design/02e-version-scope-matrix.md`
`docs/engineering/gates/phase-1/context-refinement-review.md`

## Gate

完成后执行自审：

- 每个 Aggregate 是否恰好一个 Owner？
- 是否仍有外部 Owner 被平台宣称拥有？
- 是否存在跨 Context Repository？
- 是否存在 V2 能力进入 V1 Gate？
- 是否存在 Aggregate 名被当作 Context？
- 是否仍有未定义 Service 范围？
- 是否存在 BLOCKED_BY_SPEC？

只有全部通过才将：

`Domain Model Status = PASS_FOR_PHASE_2`

否则保持：

`CONDITIONAL_PASS`

完成后停止，不进入 Phase 2。
