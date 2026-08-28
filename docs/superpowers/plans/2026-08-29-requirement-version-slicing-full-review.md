# PRD V1.8 Requirement 业务版本切片全面审查实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 PRD V1.8 附录 A.1 的 100 项正式 Requirement 完成逐项业务版本切片审查、跨需求复核，并输出需求方可直接裁决的非基线材料。

**Architecture:** 先以 PRD 业务结果为唯一切片依据建立 100/100 台账，再按端到端业务链检查强依赖、版本倒挂、共享能力和外部集成降级。裁决前不修改 PRD、正式 SDS、追溯生成器或实施状态；裁决后的回写和自动派生底座作为独立后续计划执行。

**Tech Stack:** Markdown、Python 3.13（只读提取与完整性校验）、现有 PRD/SDS/Feature/Task 文档

**Spec:** `docs/superpowers/specs/2026-08-29-requirement-version-slicing-review-design.md`

## Global Constraints

- 业务语义权威顺序为 PRD > Engineering Constitution > SDS > Feature Spec > Task > Code/Test。
- 当前只审查 PRD V1.8 修订006附录 A.1 的 100 项 V1/V2正式 Requirement。
- 切片必须形成可独立交付、独立验收且有实际业务价值的结果，不按字段或技术层拆分。
- Requirement ID、领域 Owner 和已批准业务规则保持不变；任何调整只作为 `【建议】` 或 `【待确认】`。
- V3 与 `OUT_OF_SCOPE` 只用于检查边界，不进入当前切片。
- 不修改 `docs/baseline/`、`需求/`、正式 SDS、Feature Spec、Task、生成器和追溯矩阵。
- 用户未要求 Git 提交，本计划不执行 `git commit`。

---

### Task 1: 固化审查边界与产物结构

**Files:**
- Create: `docs/superpowers/specs/2026-08-29-requirement-version-slicing-review-design.md`
- Create: `docs/superpowers/plans/2026-08-29-requirement-version-slicing-full-review.md`
- Create: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Create: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD V1.8修订006、工程链、文档治理规则、用户确认的双轮审查设计。
- Produces: 非基线审查报告和裁决清单的固定结构。

- [x] **Step 1: 记录已确认的审查方法**

  固化业务结果切片规则、两轮审查流程、裁决门禁和 PRE-04 字段映射边界。

- [ ] **Step 2: 创建100项审查台账表头**

  表头固定为：序号、Requirement、当前版本、业务结果、建议切片、强依赖/降级、结论、裁决、依据。

- [ ] **Step 3: 创建裁决清单表头**

  表头固定为：裁决编号、Requirement、现状、建议、业务理由、影响、可选项、建议选择、回写位置、状态。

### Task 2: 审查项目立项、工前准备与施工计划

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD 第四章、第五章，PROJ/SOL SDS，相关 Feature Spec 与 Task。
- Produces: COM-01、PM-01～11、PROJ-12、PRE-01～05、SOL-01、PLN-01～04 的逐项结论。

- [ ] **Step 1: 审查S0业务结果与版本边界**
- [ ] **Step 2: 审查S1工前准备业务结果与版本边界**
- [ ] **Step 3: 审查S2施工计划业务结果与版本边界**
- [ ] **Step 4: 记录本组版本变化和歧义裁决项**

### Task 3: 审查方案、实施、验收与闭环

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD 第六章、第七章，SOL/IMP/ACC SDS，相关 Feature Spec 与 Task。
- Produces: SCH-01～05、EXE-01～06、IMP-01、ACC-01～04、ACC-06、CLO-01～02 的逐项结论。

- [ ] **Step 1: 审查S3方案业务结果与版本边界**
- [ ] **Step 2: 审查S4实施业务结果与版本边界**
- [ ] **Step 3: 审查S5验收与S6闭环业务结果**
- [ ] **Step 4: 记录本组版本变化和歧义裁决项**

### Task 4: 审查资源、客户、设备与分析

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD 第八章、第九章，RES/CUS/AST/ANA SDS，相关 Feature Spec 与 Task。
- Produces: RES-01、SUB-01～05、CUS-01～04、EQP-01～05、EQP-07、SRV-01、AST-01～02、RPT-02、ANA-01 的逐项结论。

- [ ] **Step 1: 审查服务商和转包闭环**
- [ ] **Step 2: 审查客户与联系人业务结果**
- [ ] **Step 3: 审查设备、配置Log、服务和维保结果**
- [ ] **Step 4: 审查统计与经营看板边界并登记裁决项**

### Task 5: 审查割接与巡检业务链

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD 第十章、第十一章，CUT/SRV SDS及割接修订决策。
- Produces: CUT-01～10、INS-01～09 的逐项结论。

- [ ] **Step 1: 按P1～P6复核割接主链和D级简易流程**
- [ ] **Step 2: 审查割接V2增强和外部集成边界**
- [ ] **Step 3: 审查在线/离线巡检及问题闭环**
- [ ] **Step 4: 记录本组版本变化和歧义裁决项**

### Task 6: 审查集成、NFR、授权、变更与平台公共能力

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: PRD 第十二章、PLT/KNO/各消费领域 SDS、相关 Feature Spec 与 Task。
- Produces: INT-01～07、INT-09、INT-10、INT-12、NFR-01～03、AUT-01～02、CHG-01、PLT-01～02 的逐项结论。

- [ ] **Step 1: 按外部Owner、方向和降级路径审查10项正式集成需求**
- [ ] **Step 2: 审查NFR是否为业务结果必要约束或独立版本增强**
- [ ] **Step 3: 审查授权、变更、待办与文件公共业务结果**
- [ ] **Step 4: 记录本组版本变化和歧义裁决项**

### Task 7: 完成跨Requirement第二轮复核

**Files:**
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Modify: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`
- Modify: `docs/decisions/open-questions.md`（仅在存在真实规格阻断时）

**Interfaces:**
- Consumes: Task 2～6 的100项逐项结论。
- Produces: 强依赖图、版本倒挂检查、共享能力边界、裁决项去重及阻断问题。

- [ ] **Step 1: 复核S0～S6端到端闭环**
- [ ] **Step 2: 复核割接P1～P6和巡检端到端闭环**
- [ ] **Step 3: 复核外部集成降级、共享能力和Feature完成边界**
- [ ] **Step 4: 合并重复裁决并登记真实BLOCKED_BY_SPEC项**

### Task 8: 验证100/100完整性和非基线边界

**Files:**
- Test: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片全面审核报告.md`
- Test: `docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`

**Interfaces:**
- Consumes: 最终审查报告和裁决清单。
- Produces: 可交付的100项完整性证据和干净差异。

- [ ] **Step 1: 校验Requirement集合**

  从PRD附录A.1与审查台账分别提取Requirement ID，要求两边均为100项、无重复，集合差为空。

- [ ] **Step 2: 校验候选标签和裁决引用**

  所有变更建议必须包含`【建议】`或`【待确认】`；所有需裁决台账行必须能定位一个裁决编号。

- [ ] **Step 3: 校验正式基线未被修改**

  Run:

  ```powershell
  git diff --name-only -- docs/baseline 需求 docs/design specs/features tasks/features scripts docs/traceability
  ```

  Expected: 无输出。

- [ ] **Step 4: 检查格式和修改范围**

  Run:

  ```powershell
  git diff --check
  git status --short
  ```

  Expected: 无空白错误；差异只包含审查设计、实施计划、审核报告、裁决清单，以及确有阻断时的`open-questions.md`。
