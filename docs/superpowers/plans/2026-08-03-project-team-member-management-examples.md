# 项目团队成员管理文档示例实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于当前项目交付平台资料和既有通用模板，生成“项目团队成员管理”模块的 SRS、SDS、TAS 三份可追溯 Markdown 示例。

**Architecture:** 三份文档分别承担需求定义、业务领域方案和测试验收职责，通过统一编号链建立追溯。每份文档按对应模板裁剪，不保留无意义空章节，不将未确认事项转化为承诺需求。

**Tech Stack:** Markdown、Git、PowerShell、ripgrep；方案实现背景为 Yudao `master-jdk25`。

## Global Constraints

- 只使用当前工作区和当前对话中的项目资料，禁止使用项目记忆。
- 示例只描述“当前建设版本”，不得强制拆分 V1、V2、V3。
- 当前范围仅包括成员查询、添加、项目角色设置、有效期调整、移除、权限校验和操作留痕。
- 不纳入组织架构、用户账号、平台角色维护、人力排班、工时成本、批量调整、跨项目复制、智能推荐、项目树、任务 WBS 和桌面客户端专项设计。
- 不确定内容标记为【待确认】，产品建议标记为【建议】。
- 本次只交付 Markdown，不生成 Word 文档。
- 不修改或提交 `需求/数据元.xlsx` 和 `需求/需求细节.md`。

---

## 文件结构

- Create: `docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md`
  - 定义业务目标、范围、角色、场景、功能需求、业务规则、数据要求、非功能要求和需求验收标准。
- Create: `docs/examples/project-team-member-management/02-project-team-member-management-sds-example.md`
  - 定义领域边界、能力结构、对象模型、生命周期、业务不变量、权限方案和产品技术映射。
- Create: `docs/examples/project-team-member-management/03-project-team-member-management-tas-example.md`
  - 定义测试范围、策略、环境、数据、门禁、测试用例、UAT 和追溯矩阵。

### Task 1: 建立统一事实表和追溯编号

**Files:**
- Read: `docs/superpowers/specs/2026-08-03-project-team-member-management-example-design.md`
- Read: `docs/templates/software-development/01-software-requirements-specification-template.md`
- Read: `docs/templates/software-development/02-software-solution-design-template.md`
- Read: `docs/templates/software-development/03-software-test-and-acceptance-template.md`
- Read: `specs/001-project-delivery-platform/00-master-spec.md`
- Read: `specs/001-project-delivery-platform/01-platform-and-permission.md`
- Read: `specs/001-project-delivery-platform/02-project-initiation.md`
- Read: `specs/001-project-delivery-platform/appendices/permission-matrix.md`
- Read: `specs/001-project-delivery-platform/appendices/module-boundary-and-naming.md`

**Interfaces:**
- Consumes: 已确认设计范围和当前工作区的项目事实。
- Produces: 三份示例共同使用的术语、角色、范围和编号集合。

- [ ] **Step 1: 提取模板章节和项目事实**

  读取三份模板的全部章节，并从项目规格中只提取项目团队、项目权限、PMS 数据所有权和响应式 Web 约束。

- [ ] **Step 2: 固化统一编号集合**

  采用以下编号：`BRQ-TEAM-001`、`UR-TEAM-001` 至 `UR-TEAM-003`、`FR-TEAM-001` 至 `FR-TEAM-006`、`BR-TEAM-001` 起、`AC-TEAM-001` 起、`BD-TEAM-001`、`DS-TEAM-001` 起、`TC-TEAM-001` 起和 `UAT-TEAM-001` 起。

- [ ] **Step 3: 明确待确认项**

  统一保留三个待确认问题：同一成员能否承担多个项目角色、项目必备角色规则、关键角色移除是否强制交接。任何文档不得自行给出确定答案。

- [ ] **Step 4: 验证事实边界**

  Run:

  ```powershell
  rg -n "项目团队|项目成员|数据权限|响应式|PMS.*拥有" specs/001-project-delivery-platform
  ```

  Expected: 所采用的项目事实均能在当前规格中定位；未定位内容必须标记【待确认】或删除。

### Task 2: 编写 SRS 示例

**Files:**
- Create: `docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md`
- Read: `docs/templates/software-development/01-software-requirements-specification-template.md`

**Interfaces:**
- Consumes: Task 1 的范围、角色、术语及 `BRQ/UR/FR/BR/AC` 编号。
- Produces: SDS 和 TAS 的权威需求输入。

- [ ] **Step 1: 建立裁剪后的 SRS 结构**

  保留文档控制、背景与目标、范围、角色、业务场景、总体需求、详细功能需求、数据需求、外部依赖、非功能需求、约束、风险待确认和追溯章节；删除与本模块无关的空章节。

- [ ] **Step 2: 编写需求层级**

  将业务需求分解为可管理项目团队、维护有效成员关系和按授权操作三类用户目标；功能需求覆盖查询、添加、角色设置、有效期调整、移除及留痕与权限校验。

- [ ] **Step 3: 编写规则和需求验收标准**

  每个 FR 至少对应一个 `AC-TEAM-*`；权限、重复成员、无效有效期、移除关键角色等场景必须具备反向标准。验收标准只描述可观察结果，不写测试步骤或数据库实现。

- [ ] **Step 4: 执行 SRS 边界检查**

  Run:

  ```powershell
  rg -n "CREATE TABLE|Controller|Mapper|DTO|VARCHAR|/api/" docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md
  ```

  Expected: 无匹配；SRS 不包含具体实现设计。

### Task 3: 编写 SDS 示例

**Files:**
- Create: `docs/examples/project-team-member-management/02-project-team-member-management-sds-example.md`
- Read: `docs/templates/software-development/02-software-solution-design-template.md`
- Read: `docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md`

**Interfaces:**
- Consumes: SRS 中的 `FR/BR/AC` 及项目平台边界。
- Produces: TAS 使用的 `BD/DS` 设计项和风险测试点。

- [ ] **Step 1: 建立业务领域导向的 SDS 结构**

  保留设计输入、领域定位、能力地图、角色场景、领域模型、生命周期与业务规则、大功能模块详细方案、跨域协作、权限与留痕、流程交互、产品技术映射、非功能响应和追溯章节。

- [ ] **Step 2: 编写领域方案**

  以“项目团队成员关系”为核心对象，明确用户主档只引用、项目角色属于项目上下文、成员有效期影响当前有效成员资格，移除采用失效语义并保留历史留痕。

- [ ] **Step 3: 编写实现映射**

  将业务能力映射到 `pms-module-project`、Yudao 用户组织能力、响应式 Web 页面和受控业务接口。具体表名和接口字段仅在有当前依据时填写，否则使用逻辑对象和契约描述，不创造物理设计。

- [ ] **Step 4: 执行 SDS 边界检查**

  Run:

  ```powershell
  rg -n "FR-TEAM-|BR-TEAM-|AC-TEAM-|BD-TEAM-|DS-TEAM-|【待确认】" docs/examples/project-team-member-management/02-project-team-member-management-sds-example.md
  ```

  Expected: 所有设计项均能回溯需求，三个待确认问题保持未决状态。

### Task 4: 编写 TAS 示例

**Files:**
- Create: `docs/examples/project-team-member-management/03-project-team-member-management-tas-example.md`
- Read: `docs/templates/software-development/03-software-test-and-acceptance-template.md`
- Read: `docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md`
- Read: `docs/examples/project-team-member-management/02-project-team-member-management-sds-example.md`

**Interfaces:**
- Consumes: SRS 的 `FR/AC` 和 SDS 的 `BD/DS`。
- Produces: 可执行的 `TC/UAT` 以及需求—设计—测试追溯矩阵。

- [ ] **Step 1: 建立裁剪后的 TAS 结构**

  保留测试输入、范围策略、环境数据、准入准出、功能/数据/权限/异常测试、具体用例、缺陷回归、UAT、报告和追溯章节。

- [ ] **Step 2: 设计测试数据**

  至少包含两个项目、三个候选用户、项目经理/普通成员/无项目权限用户三种权限身份、有效/未生效/已失效三类成员关系，以及重复添加场景。关键角色移除只验证当前已确认的通用权限、留痕和状态一致性；是否强制交接不预设结论。

- [ ] **Step 3: 编写测试用例和 UAT**

  为六项 FR 建立正常用例；为权限越权、重复添加、非法有效期和并发修改建立反向用例。关键角色移除的交接规则标记为【待确认】，不得编写带确定业务结论的验收用例。每个已确定用例包含前置条件、数据、操作步骤、预期结果和对应编号。

- [ ] **Step 4: 执行 TAS 可执行性检查**

  Run:

  ```powershell
  rg -n "前置条件|测试数据|操作步骤|预期结果|FR-TEAM-|AC-TEAM-|DS-TEAM-|TC-TEAM-|UAT-TEAM-" docs/examples/project-team-member-management/03-project-team-member-management-tas-example.md
  ```

  Expected: 测试用例具备可执行条件，且能回溯需求与设计。

### Task 5: 三文档一致性与读者测试

**Files:**
- Modify: `docs/examples/project-team-member-management/01-project-team-member-management-srs-example.md`
- Modify: `docs/examples/project-team-member-management/02-project-team-member-management-sds-example.md`
- Modify: `docs/examples/project-team-member-management/03-project-team-member-management-tas-example.md`

**Interfaces:**
- Consumes: Task 2 至 Task 4 的三份完整草稿。
- Produces: 无占位符、无职责混淆、追溯闭合的最终示例。

- [ ] **Step 1: 检查占位符和推测标识**

  Run:

  ```powershell
  rg -n "TBD|TODO|〈填写〉|\[To be written\]|请填写|待补充" docs/examples/project-team-member-management
  ```

  Expected: 无匹配。实际未决内容只允许使用【待确认】。

- [ ] **Step 2: 检查版本表述**

  Run:

  ```powershell
  rg -n "V1|V2|V3" docs/examples/project-team-member-management
  ```

  Expected: 无产品分期表述；文档版本统一使用 `V0.1`，适用版本统一使用“当前建设版本”。

- [ ] **Step 3: 检查追溯闭合**

  逐项确认 `FR-TEAM-001` 至 `FR-TEAM-006` 均在 SRS 定义、在 SDS 映射、在 TAS 形成测试；所有 `AC-TEAM-*` 至少映射一个 `TC-TEAM-*` 或 `UAT-TEAM-*`。

- [ ] **Step 4: 执行 Markdown 和 Git 差异检查**

  Run:

  ```powershell
  git diff --check -- docs/examples/project-team-member-management
  ```

  Expected: 无行尾空格或补丁格式错误。

- [ ] **Step 5: 执行独立读者测试**

  由无当前对话上下文的审阅者回答：模块范围是什么、平台角色与项目角色有何区别、成员有效期如何影响资格、移除为何保留历史、三份文档如何追溯。若答案无法从文档直接取得，修订对应章节。

- [ ] **Step 6: 提交最终示例**

  在提交前加载 `git-commit`，只暂存三份示例文件，检查 staged diff 和敏感文件，然后使用与仓库历史一致的 Conventional Commit 信息提交；不得推送。
