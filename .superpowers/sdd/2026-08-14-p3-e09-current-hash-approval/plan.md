# P3-E09 Current-Hash Requirement Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将需求方已确认的九组推荐决策绑定当前DDL哈希写回机器契约，使全部692项DEFER进入需求方已决策、Reviewer待签署状态，不伪造最终批准。

**Architecture:** 以当前确认包为唯一逐项集合和用户确认依据；核心迁移契约保存当前哈希、九组状态和证据引用；DDL寄存器生成器按确认包精确集合写入需求方决策，Reviewer overlay与最终批准继续保持独立。所有Phase 3状态由契约和寄存器派生。

**Tech Stack:** Python 3.13、JSON/Markdown机器契约、unittest、Git。

## Global Constraints

- PRD V1.7业务语义不变，不新增表、字段、角色、状态或审批节点。
- 两份未跟踪割接原始资料不得读取、修改或暂存。
- 当前确认只代表Requirement Owner接受，不代表Reviewer签署或生产迁移批准。
- DDL SHA-256固定为`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`。
- 所有692项DEFER必须被精确覆盖；禁止按SQL类型自动接受未来新增项。

---

### Task 1: 锁定九组需求方确认契约

**Files:**
- Create: `docs/decisions/0028-p3-e09-current-hash-requirement-owner-confirmation.md`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `scripts/validate_core_migration_schema_contract.py`
- Test: `scripts/tests/test_validate_core_migration_schema_contract.py`

- [ ] 增加负向测试：确认包哈希不一致、组缺失、item集合不精确或证据文件不存在时失败。
- [ ] 写入ADR-0028，记录用户于2026-08-14接受九组推荐A，同时明确Reviewer与approved hash仍为空。
- [ ] 契约将Q07/Q08/V1.7置为`ACCEPTED`，并增加Q09～Q14精确组集合、当前DDL哈希和ADR-0028证据引用。
- [ ] 运行核心契约定点测试和validator。

### Task 2: 将全部待决策项写入逐项寄存器

**Files:**
- Modify: `scripts/generate_ddl_drift_review.py`
- Modify: `scripts/validate_ddl_item_decision_register.py`
- Test: `scripts/tests/test_generate_ddl_drift_review.py`
- Test: `scripts/tests/test_validate_ddl_item_decision_register.py`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.md`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md`

- [ ] 增加负向测试：遗漏、额外项、未来新增同类型项、证据引用缺失均失败。
- [ ] 只按确认包itemId显式集合应用`AMEND_CURRENT/REQUIREMENT_OWNER`，逐项追加ADR-0028。
- [ ] 保留所有`reviewOwner=null`、`approvedCount=0`、`approvedDdlSha256=null`。
- [ ] 重生并校验寄存器，要求`DEFER=0`且695项九组并集均有当前哈希证据。

### Task 3: 同步Phase 3门禁和正式说明

**Files:**
- Modify: `scripts/generate_phase3_evidence_packets.py`
- Modify: `scripts/validate_phase3_evidence_register.py`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`
- Modify: `docs/engineering/gates/phase-3/self-review.md`
- Modify: `docs/engineering/gates/phase-3/runtime-fact-inventory.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md`
- Regenerate: `docs/engineering/gates/phase-3/evidence-packet-templates/*`

- [ ] 将状态推进到`DECISIONS_ACCEPTED_REVIEW_PENDING`，但保持SDS基线未批准。
- [ ] 将剩余阻断准确表述为独立Reviewer逐项签署、items hash/DDL hash最终批准。
- [ ] 运行Phase 3生成器、validators和全量unittest。

### Task 4: 独立Reviewer复核与基线判定

**Files:**
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`（仅Reviewer overlay）
- Create or modify: `docs/engineering/gates/phase-3/submissions/P3-E09/*`
- Modify: `docs/engineering/gates/phase-3/independent-review.md`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`

- [ ] 由fresh-context Reviewer逐项/分组核验决策证据、DDL定义和禁止范围。
- [ ] Reviewer通过后写入每项reviewOwner和真实评审证据，再生成绑定currentDdlSha256与itemsSha256的approval。
- [ ] 重跑核心、寄存器、Phase3、SDS与全量测试；错误哈希、缺签署或缺证据必须失败。
- [ ] 只有全部门禁通过才将Phase3标为SDS基线并进入Feature Spec。
