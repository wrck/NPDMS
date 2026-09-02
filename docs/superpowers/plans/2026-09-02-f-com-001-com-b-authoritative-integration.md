# F-COM-001 COM-B权威增量集成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以当前PRD的AST结构化地点语义选择COM-B为F-COM-001唯一后续实现线，将其已通过Gate且可构建的Task 1～4增量选择性集成到`master`，并把COM-A标记为被替代。

**Architecture:** `master`仍是唯一状态和集成分支；源分支只提供不可变候选提交。先在权威Open Question、Feature Spec/Task和Delivery Unit中记录COM-B选择，再按“规格/计划→公共契约→Schema→迁移证据依赖→ERP批次→人工候选”分批接收；原分支已判定NO-GO的Task 5及CUT/PROJ后续提交不进入本轮。

**Tech Stack:** Git、Java 25、Maven、MyBatis Plus、Flyway、Vue 3、Python治理校验器。

**Spec:** `specs/features/F-COM-001-contract-order-and-delivery-scope.md`

## Global Constraints

- Requirement固定为`COM-01@V1`；地点唯一权威遵循当前PRD的`site_id/site_location_id`，文本地点仅为`UNRESOLVED`受控降级。
- COM-B并未Git继承COM-A；业务选择记录为“COM-B替代COM-A”，不得伪造继承关系或把COM-A的Done证据转记到COM-B。
- 只接收源线`c21745a9..3e26a537`中通过Gate的Task 1～4及其必要依赖；`18237796`保持`NO-GO / REVIEW_REQUIRED`。
- `9d029976/319a616e/86ea27de`的PROJ资格公共契约已由`master@f1cf7920`适配接收，不重复合并。
- 不整支合并`codex/f-cut-001-matrices`或`codex/f-proj-008-stage-advance`，不接收CUT代码，不修改其他Worktree的脏改动。
- `master`可记录`INTEGRATED_PARTIAL`，但必须保持可构建，不得把F-COM-001投影为Implementation Done或提前开放未闭合入口。

---

### Task 1: 建立权威选择与排他认领

**Files:**
- Create: `tasks/delivery-units/DU-20260902-FCOM001-COMB-INTEGRATION.md`
- Modify: `tasks/delivery-units/README.md`
- Modify: `docs/decisions/open-questions.md`
- Modify: `tasks/features/README.md`

**Interfaces:**
- Consumes: `master@870071f4`、用户选择COM-B的业务指令、`Q-GOV-20260901-002`。
- Produces: `F-COM-001=FEATURE_EXCLUSIVE`有效认领，以及COM-A `SUPERSEDED / DO_NOT_MERGE`裁决。

- [ ] **Step 1: 创建Delivery Unit并重建索引**

  Run: `python -B scripts/validate_delivery_units.py --write-index`

  Expected: 新DU为`CLAIMED`且索引校验通过。

- [ ] **Step 2: 提交master认领**

  仅提交本计划、DU和DU索引；认领提交必须先于规格或代码写入。

- [ ] **Step 3: 关闭双实现Open Question**

  将`Q-GOV-20260901-002`关闭为COM-B权威、COM-A被替代；明确该决定不证明两线继承，也不自动解除ACC-001/002及`Q-GOV-20260901-001`。

### Task 2: 接收COM-B正式规格与Technical Plan

**Files:**
- Create/Modify: `specs/features/F-COM-001-contract-order-and-delivery-scope.md`
- Create/Modify: `specs/features/F-COM-001-physical-contract.json`
- Create/Modify: `specs/features/F-COM-001-legacy-reuse-audit.md`
- Create/Modify: `tasks/features/F-COM-001.md`
- Create/Modify: `docs/superpowers/plans/2026-08-30-f-com-001-contract-order-delivery-scope.md`
- Modify: `docs/design/02d-cross-context-contracts.md`
- Modify: `docs/design/07-authorization-design.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/12-integration-design.md`
- Modify: `docs/design/16-exception-and-idempotency.md`

**Interfaces:**
- Consumes: 源提交`c21745a9..f309c9f3`和当前PRD COM-01。
- Produces: `BASELINE / READY`的COM-B Feature Spec、通过Gate的唯一Technical Plan及`IN_PROGRESS` Feature Task。

- [ ] **Step 1: 按源提交顺序接收规格修订**

  逐提交审查共享SDS和投影冲突；当前master事实优先，禁止旧分支覆盖新工程链或其他Feature状态。

- [ ] **Step 2: 校验Feature物理契约与追溯**

  Run: `python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check`

  Expected: COM-01映射可生成且F-COM-001仍为`IN_PROGRESS`。

### Task 3: 接收Task 1～2公共契约、Schema与迁移证据依赖

**Files:**
- Modify: `pms-module-commerce-api/**`
- Modify: `pms-module-commerce/**`
- Modify: `pms-framework/pms-common/**`
- Modify: `sql/migrations/**`
- Modify: 相关模块测试与POM

**Interfaces:**
- Consumes: `5abbc82b..b711389d`和`2141204d..e507eae0`。
- Produces: COM公开事实合同、十表物理模型及COM迁移所需的迁移证据公共能力。

- [ ] **Step 1: 接收并审查COM公开契约**

  保持现有`DeliveryScopeApi`兼容；错误分类、租户边界和跨模块依赖必须与当前master一致。

- [ ] **Step 2: 接收并审查十表Schema**

  迁移文件只允许前向新增；若源Flyway编号与master冲突，保留SQL语义并在master重新编号。

- [ ] **Step 3: 接收必要迁移证据能力**

  只接收COM迁移实际消费的API、表、Provider和测试，不扩大为通用迁移框架。

- [ ] **Step 4: 构建Task 1～2增量**

  Run: `mvn -pl pms-module-commerce-api,pms-module-commerce -am -DskipTests compile`

  Expected: `BUILD SUCCESS`。

### Task 4: 接收Task 3～4业务增量

**Files:**
- Modify: `pms-module-commerce/**`
- Modify: 相关测试、迁移与规格任务记录

**Interfaces:**
- Consumes: `dd0a26ee..3e26a537`。
- Produces: ERP权威批次接收、人工权威候选及关系核对闭环；不包含Task 5范围命令。

- [ ] **Step 1: 接收关系来源身份修订与ERP批次**

  验证旧版本、同版本异载荷、批次事务和PENDING_AUTHORITY边界。

- [ ] **Step 2: 接收人工候选核对**

  验证人工事实不能冒充ERP确认、公司/项目空范围不放大、幂等与审计同事务。

- [ ] **Step 3: 明确排除Task 5**

  确认`18237796`及后续CUT/PROJ提交未进入暂存区；Feature Task停在Task 4通过、Task 5待整改。

### Task 5: 验证、Code Review与集成回执

**Files:**
- Modify: `tasks/features/README.md`
- Modify: `tasks/delivery-units/DU-20260902-FCOM001-COMB-INTEGRATION.md`
- Modify: `tasks/delivery-units/README.md`

**Interfaces:**
- Consumes: master最终选择性集成树。
- Produces: `INTEGRATED_PARTIAL`回执和可继续Task 5整改的新基线。

- [ ] **Step 1: 运行受影响模块验证**

  运行COM API/Biz及迁移证据相关聚焦测试、Maven构建、Flyway/追溯/DU校验；失败按源代码、集成冲突或既有基线分别归因。

- [ ] **Step 2: 完成五轴Code Review**

  检查正确性、架构边界、权限/租户与敏感字段、查询与锁序性能、测试有效性；Required问题关闭前不提交代码。

- [ ] **Step 3: 更新权威状态并提交**

  F-COM-001保持`IN_PROGRESS / INTEGRATED_PARTIAL`，COM-A保持被替代，DU记录实际master提交与剩余Task 5～8；不推送远端。
