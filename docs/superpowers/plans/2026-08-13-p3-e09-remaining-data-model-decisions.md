# P3-E09 Remaining Data Model Decisions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的P3-E09剩余数据模型决策固化为ADR、机器契约、核心迁移DDL和可复现门禁证据。

**Architecture:** 当前`project-order-physical-schema.mysql.sql`定位为迁移核心子集，不冒充平台全量物理模型。机器契约统一声明表范围、跨领域引用、外部键一源多目标、当前记录唯一性、编码归一化、业务键永久占用、V3排除和历史异常隔离；生成器据此重建DDL哈希及迁移证据，未获Reviewer签署的项目继续保持`DEFER`。

**Tech Stack:** Markdown ADR/SDS、JSON机器契约、MySQL 8.4评审DDL、Python 3校验与生成脚本、Git。

## Global Constraints

- PRD V1.6是业务语义最高来源；V3不得进入V1/V2实施DDL。
- 旧`dppms`只读，不执行旧库DDL/DML，不使用跨数据库SQL。
- 物理表使用ADR-0019领域编码命名；业务系统名称`pms`不进入目标表前缀。
- 跨领域只保存逻辑引用，不建立物理外键或级联写入。
- 项目、合同、订单、SN和来源稳定键在删除、停用或关闭后不得复用。
- 历史异常进入`plt_migration_issue`并保留逐源行证据，不修改源数据、不静默删除、不放宽目标约束。
- 每个任务完成校验后立即独立提交，不主动推送。

---

### Task 1: 建立剩余数据模型决策契约

**Files:**
- Create: `docs/decisions/0022-core-migration-schema-and-key-policy.md`
- Create: `docs/traceability/core-migration-schema-contract.json`
- Create: `scripts/validate_core_migration_schema_contract.py`
- Create: `scripts/tests/test_validate_core_migration_schema_contract.py`
- Modify: `docs/decisions/open-questions.md`

**Interfaces:**
- Consumes: ADR-0019～ADR-0021、PRD INT-04与KNO-V3-01～08、当前数据库命名契约和评审DDL。
- Produces: `validate_contract(contract, ddl) -> list[str]`，供本地和门禁校验复用。

- [ ] **Step 1: 编写失败测试**

  测试必须拒绝：核心DDL包含4张V3治理表；跨领域`FOREIGN KEY`；`plt_external_key_mapping`缺少`target_role/target_sequence`；业务唯一键包含`deleted`；时态当前唯一性直接依赖可空`effective_to`；契约把当前表清单声明为平台全量模型。

- [ ] **Step 2: 运行定点测试确认失败**

  Run: `python -B -m unittest scripts.tests.test_validate_core_migration_schema_contract`

  Expected: FAIL，因为校验器和机器契约尚不存在。

- [ ] **Step 3: 实现最小契约与校验器**

  契约固定：`coverage=CORE_MIGRATION_SUBSET`；4张KNO治理表为`V3_DESIGN_ONLY`；跨领域引用策略为`LOGICAL_REFERENCE`；外部键映射角色默认`PRIMARY`、顺序从0开始；编码规范区分业务编码、外部不透明键和名称；永久业务键唯一约束不得包含`deleted`；历史异常策略为`MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE`。

- [ ] **Step 4: 运行定点测试确认通过**

  Run: `python -B -m unittest scripts.tests.test_validate_core_migration_schema_contract`

  Expected: PASS。

- [ ] **Step 5: 提交Task 1**

  Commit: `docs(data-model): 固化核心迁移模型边界`

### Task 2: 应用DDL边界并重建P3-E09证据

**Files:**
- Modify: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Modify: `docs/traceability/database-naming-contract.json`
- Modify: `scripts/validate_database_naming_contract.py`
- Modify: `scripts/generate_ddl_drift_review.py`
- Modify: `scripts/generate_target_field_catalog.py`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`
- Regenerate: `specs/001-project-delivery-platform/evidence/migration/*`

**Interfaces:**
- Consumes: `core-migration-schema-contract.json`和Task 1校验器。
- Produces: 不含V3治理表和跨领域物理外键、支持一源多目标角色/顺序的核心迁移DDL，以及同哈希的字段目录、映射、漂移、约束和逐项裁决证据。

- [ ] **Step 1: 修改DDL并保留逻辑引用字段**

  删除4个`kno_*technical_advisory*`建表块和全部跨领域外键约束；不删除其逻辑ID列、索引或应用层引用语义。给`plt_external_key_mapping`增加`target_role varchar(32) not null default 'PRIMARY'`、`target_sequence int unsigned not null default 0`，将唯一键扩展为来源、目标角色、目标顺序及目标对象，并增加非负CHECK。

- [ ] **Step 2: 同步机器生成器和正式SDS**

  数据库命名契约保留ADR-0019的历史52表裁决，同时增加`implementationScope`，使4张KNO表不再被要求出现在核心DDL。SDS明确V2逻辑契约不等于在本迁移子集中提前落V3治理表。

- [ ] **Step 3: 重建目标字段和P3-E09证据**

  Run: `python -B scripts/generate_target_field_catalog.py`

  Run: `python -B scripts/generate_ddl_drift_review.py`

  Run: `python -B scripts/generate_ddl_model_decision_catalog.py`

  Run: `python -B scripts/generate_phase3_evidence_packets.py`

  Expected: 全部派生文件使用同一新DDL哈希；移除表的历史来源仍保留为来源证据或明确的V3/后续Feature落位，不静默丢失。

- [ ] **Step 4: 执行全量校验**

  Run: `python -B scripts/validate_core_migration_schema_contract.py`

  Run: `python -B scripts/validate_database_naming_contract.py`

  Run: `python -B scripts/validate_ddl_item_decision_register.py`

  Run: `python -B scripts/validate_sds_phase2.py`

  Run: `python -B scripts/validate_sds_phase3.py`

  Run: `python -B -m unittest discover -s scripts/tests -p "test_*.py"`

  Run: `git diff --check`

  Expected: 全部PASS；P3-E09仍保持`OPEN`，只等待尚未签署的剩余模型项与Reviewer证据。

- [ ] **Step 5: 提交Task 2**

  Commit: `docs(data-model): 收敛核心迁移DDL边界`
