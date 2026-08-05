# 领域化 SRS 直接迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 直接依据现有 148 条来源需求、145 项正式 FR、7 项 V3 演进范围、原始结构化资料和页面证据，将当前规格迁移为“系统场景层＋13 个领域责任 SRS”的 Markdown 权威规格体系。

**Architecture:** 系统层负责总体范围、生命周期、跨领域工作台和领域地图；领域层按唯一业务责任集中定义对象、规则、状态、数据与验收。现有详细 FR 通过确定映射机械转换为严格 SRS 模板单元，保留 legacy 编号和追溯；旧分卷移动到 `legacy/`，新目录一次切换为权威入口，不设置额外用户复审门禁。

**Tech Stack:** Markdown、Python 3、pytest、PowerShell、ripgrep、Git；项目内 `create-software-spec-docs` 模板和校验脚本。

## Global Constraints

- 只使用当前工作区、当前对话及用户明确指定的资料，禁止读取、引用或更新项目记忆。
- 双层边界遵循 `docs/superpowers/specs/2026-08-05-domain-oriented-srs-system-design-principles.md`。
- Markdown 是主要开发资料和需求事实源；本次不生成或修改 Word。
- 148 条 `REQ-*` 必须全部获得目标需求、演进范围、延期或排除结论。
- 145 项正式 FR 必须全部迁移且只能有一个权威定义；7 项 V3 内容只保留范围与演进方向，不纳入当前开发验收。
- 语义不变的 FR/BR/DR/AC 保留现有编号；不为适配新领域代码批量重编号。
- 每个目标 FR、BR、DR、AC 和核心对象只能有一个 Owner；其他领域通过业务协作契约引用。
- 领域划分不由 `13领域需求迁移矩阵.md` 决定；本计划的 13 个领域由现有对象、规则、状态和职责聚合推导，数量相同仅属结果巧合。
- 项目交付、割接和巡检 HTML 仅证明可见页面、动作、步骤和门禁；不单独证明隐藏业务规则。
- 推导内容标记 `【建议】`；影响范围、规则、状态或验收的不确定内容标记 `【待确认】`。
- 除技术选型章节外统一使用“基础平台”；不得在业务正文重复具体技术产品名。
- 每份领域 SRS 严格保留通用 SRS 模板固定章节；每项正式 FR 具有模板规定的 12 个子节和表格结构。
- SRS 验收标准只表达可观察业务结果，不混入测试执行步骤、物理表、代码类、部署或未经确认的 API 路径。
- 原始 Excel、HTML、DOCX、VSDX、物理 DDL 和用户未跟踪文件保持只读。
- 不暂存 `docs/需求分析模版和示例.zip`、`需求/数据元.xlsx`、`需求/需求细节.md`。
- 每次提交前必须读取并遵循 `$git-commit` skill；禁止 `git add .`、`git add -A` 和自动推送。

---

## 目标领域与确定映射

|领域代码|领域名称|权威责任|迁移范围|
|---|---|---|---|
|PLT|公共平台能力|身份、权限、流程、文件、通知、审计、字典和通用集成治理|FR-PLT-001～011|
|PROJ|项目治理|客户项目上下文、项目组合、项目树、任务 WBS、团队、阶段计划、风险和项目关闭控制|FR-PROJ-001～026|
|ENG|工程交付|工勘、需求分析、实施准备、方案、到货、安装、配置、联调、质量与现场问题|FR-ENG-001～029|
|CUT|割接管理|割接准备、评估、方案、审批、执行、回退、观察和闭环|FR-CUT-001～015|
|ACC|验收与闭环|培训、满意度、初终验、交付件、闭环审批和转维护|FR-ACC-001～010|
|INS|巡检服务|巡检创建、规则、在线/离线执行、报告、整改和巡检闭环|FR-SRV-001～012|
|SVC|服务工单与维保|工单、时效、问题关联、维保、续保、回访和主动服务|FR-SRV-013～024|
|AST|设备资产|设备/SN、版本、配置日志、安装位置和设备档案|FR-RES-001～004|
|TIM|工时管理|考勤、工作记录、工时申报和审批|FR-RES-005～007|
|OUT|服务商与外包|服务商、转包、合同订单回写、付款、余额和回访门禁|FR-RES-008～014|
|SPT|备件与 RMA|RMA、备件库、好坏件、借用补库、转移交接和替换维保|FR-RES-015～019、FR-RES-021；演进 FR-RES-020|
|TEC|技术公告|公告编制、影响版本、会签、检索、命中、工单关联和统计|FR-RES-022～029|
|ANA|经营分析|项目组合经营、工时人效和跨领域只读分析|FR-ANA-001～002；演进 FR-ANA-003～008|

项目交付页面属于系统场景编排，不另设“页面领域”；客户/用户单位权威主数据来自外部客户系统，PROJ 只拥有项目交付上下文、联系人引用、服务等级和聚合展示规则。

---

## 目标文件结构

```text
specs/001-project-delivery-platform/
├── README.md
├── system/
│  ├── 00-system-srs.md
│  ├── domain-map.md
│  └── scenario-orchestration.md
├── domains/
│  ├── PLT-public-platform-capabilities-srs.md
│  ├── PROJ-project-governance-srs.md
│  ├── ENG-engineering-delivery-srs.md
│  ├── CUT-cutover-management-srs.md
│  ├── ACC-acceptance-and-closure-srs.md
│  ├── INS-inspection-service-srs.md
│  ├── SVC-service-and-maintenance-srs.md
│  ├── AST-device-asset-srs.md
│  ├── TIM-worktime-management-srs.md
│  ├── OUT-outsourcing-management-srs.md
│  ├── SPT-spare-parts-and-rma-srs.md
│  ├── TEC-technical-bulletin-srs.md
│  └── ANA-business-analytics-srs.md
├── appendices/
│  ├── source-ledger.md
│  ├── requirement-migration.md
│  ├── domain-collaboration.md
│  ├── page-domain-mapping.md
│  ├── data-dictionary.md
│  └── acceptance-traceability.md
└── legacy/
   ├── README.md
   └── 00-master-spec.md ～ 08-analytics-and-integration.md
```

现有 API、物理 DDL、迁移设计和技术证据附录继续留在 `appendices/`，不复制到领域 SRS 正文。

---

### Task 1: 建立确定迁移模型和自动校验

**Files:**
- Create: `scripts/requirements/migrate_domain_srs.py`
- Create: `scripts/requirements/validate_domain_srs.py`
- Create: `tests/requirements/test_migrate_domain_srs.py`
- Create: `tests/requirements/test_validate_domain_srs.py`
- Create: `specs/001-project-delivery-platform/appendices/source-ledger.md`
- Create: `specs/001-project-delivery-platform/appendices/requirement-migration.md`
- Modify: `docs/superpowers/specs/2026-08-05-domain-oriented-srs-system-design-principles.md`
- Add: `docs/superpowers/plans/2026-08-05-domain-oriented-srs-direct-migration.md`

**Interfaces:**
- Consumes: 8 个当前领域分卷、148 条来源追溯、13 个确定领域映射和通用 SRS 模板。
- Produces: `parse_legacy_fr(path: Path) -> list[LegacyRequirement]`、`render_srs(profile: DomainProfile, requirements: list[LegacyRequirement]) -> str`、`validate_tree(root: Path) -> list[str]`；迁移矩阵每个正式 FR 恰好一行。

- [ ] 编写失败测试：验证 10 子节 legacy FR 能被转换成 12 子节模板 FR，基本信息由原有粗体元数据生成，输入表由既有前置条件/数据要求组织，BR/DR/AC 编号保持不变。
- [ ] 运行 `python -m pytest tests/requirements/test_migrate_domain_srs.py tests/requirements/test_validate_domain_srs.py -q`，确认因脚本不存在而失败。
- [ ] 实现 13 个领域 profile 和上述确定 FR 范围；正式 FR 使用二级标题解析，V3 三级标题解析为演进表，不生成正式详细 FR。
- [ ] 实现模板渲染：固定章节与模板完全一致，每个正式 FR 使用三级标题，子节顺序为基本信息、业务目标、触发条件与前置条件、输入、主流程、分支与异常、状态流转、业务规则、数据要求、权限通知与业务留痕、输出与后置条件、验收标准。
- [ ] 实现校验：148 REQ 覆盖、145 正式 FR 唯一、7 演进项唯一、13 领域文件、FR/BR/DR/AC 无重复权威定义、无悬空引用、无模板占位符、业务正文无受限技术产品名。
- [ ] 建立 `source-ledger.md`，登记用户确认、当前规格、项目交付 Excel/HTML、割接巡检资料、目标表结构证据和参考矩阵，并标明证据等级与使用边界。
- [ ] 建立 `requirement-migration.md`，逐项记录 legacy FR、来源 REQ、目标领域、处置 `MOVE`、编号动作“保留”和证据；7 个演进项单列为“不纳入当前开发验收”。
- [ ] 运行单元测试，预期全部 PASS；运行 `python scripts/requirements/validate_domain_srs.py --root specs/001-project-delivery-platform --allow-missing-targets`，预期只报告尚未生成目标文档。
- [ ] 读取 `$git-commit` skill，仅提交本任务文件，提交信息 `docs(spec): 建立领域 SRS 迁移模型`。

---

### Task 2: 生成 13 份严格模板领域 SRS

**Files:**
- Create: `specs/001-project-delivery-platform/domains/PLT-public-platform-capabilities-srs.md`
- Create: `specs/001-project-delivery-platform/domains/PROJ-project-governance-srs.md`
- Create: `specs/001-project-delivery-platform/domains/ENG-engineering-delivery-srs.md`
- Create: `specs/001-project-delivery-platform/domains/CUT-cutover-management-srs.md`
- Create: `specs/001-project-delivery-platform/domains/ACC-acceptance-and-closure-srs.md`
- Create: `specs/001-project-delivery-platform/domains/INS-inspection-service-srs.md`
- Create: `specs/001-project-delivery-platform/domains/SVC-service-and-maintenance-srs.md`
- Create: `specs/001-project-delivery-platform/domains/AST-device-asset-srs.md`
- Create: `specs/001-project-delivery-platform/domains/TIM-worktime-management-srs.md`
- Create: `specs/001-project-delivery-platform/domains/OUT-outsourcing-management-srs.md`
- Create: `specs/001-project-delivery-platform/domains/SPT-spare-parts-and-rma-srs.md`
- Create: `specs/001-project-delivery-platform/domains/TEC-technical-bulletin-srs.md`
- Create: `specs/001-project-delivery-platform/domains/ANA-business-analytics-srs.md`
- Modify: `scripts/requirements/migrate_domain_srs.py`
- Modify: `tests/requirements/test_migrate_domain_srs.py`

**Interfaces:**
- Consumes: Task 1 的确定领域 profiles、legacy 解析器和通用模板。
- Produces: 13 份可独立评审的完整 SRS；145 项正式 FR 全部只出现一次，7 项 V3 仅出现在非本期范围和演进方向表。

- [ ] 运行迁移脚本生成 13 份领域 SRS，禁止手工复制大段公共模板内容到多个领域。
- [ ] 为每个领域填写业务背景、目标、范围、非范围、角色、核心对象、生命周期、功能架构、专项需求、权限/NFR、风险和追溯；没有来源支持的优化标 `【建议】`，不确定规则标 `【待确认】`。
- [ ] 将 legacy 粗体元数据转换为“基本信息”表；来源、版本、优先级、角色、场景和依赖不得丢失。
- [ ] 将 legacy “前置条件”拆入触发/权限/数据/外部依赖，将已有数据项组织为输入表和数据要求表，不创造物理字段。
- [ ] 将 `FR-RES-020`、`FR-ANA-003～008` 写入对应文档的非本期范围和演进方向，明确“不纳入当前开发验收”。
- [ ] 对 PLT 文档只保留公共机制；业务特定 CRM/ERP/ITR 交互放在对象 Owner 领域的外部交互要求中。
- [ ] 逐份运行 `.codex/skills/create-software-spec-docs/scripts/validate_documents.py --kind srs --input <文件>`，13 份全部退出 0。
- [ ] 运行迁移与校验单元测试，预期全部 PASS；运行 `python scripts/requirements/validate_domain_srs.py --root specs/001-project-delivery-platform --allow-missing-system`，预期只报告系统层和公共映射尚未生成。
- [ ] 读取 `$git-commit` skill，仅提交 13 份领域 SRS 及必要脚本/测试修订，提交信息 `docs(spec): 生成领域责任 SRS`。

---

### Task 3: 建立系统场景层

**Files:**
- Create: `specs/001-project-delivery-platform/system/00-system-srs.md`
- Create: `specs/001-project-delivery-platform/system/domain-map.md`
- Create: `specs/001-project-delivery-platform/system/scenario-orchestration.md`

**Interfaces:**
- Consumes: 13 份领域 SRS、现有主规格、项目交付/割接/巡检页面证据。
- Produces: 系统目标与范围、推荐生命周期、项目类型差异、13 领域 Owner 图和三个跨领域工作台编排；详细业务规则只引用领域 FR。

- [ ] 按通用 SRS 模板编写 `00-system-srs.md`，包含行业类型、角色、核心对象、推荐生命周期、项目类型模型、系统级 NFR 和唯一系统场景 FR `FR-SYS-001 跨领域工作台编排`。
- [ ] 在 `domain-map.md` 记录 13 个领域的一句话责任、核心对象、输入输出、上游下游和唯一 Owner；使用 Mermaid 展示领域协作，不写物理 API 或数据库表。
- [ ] 在 `scenario-orchestration.md` 分别描述项目交付、割接、巡检的正常、异常、审批、变更和关闭流程，每个动作引用目标领域 FR。
- [ ] 明确项目组合、项目树和任务 WBS 均为非固定层级；项目是系统核心数据，同一系统内不再描述为外部 PMS 集成。
- [ ] 明确响应式 Web 优先，后续多端及桌面客户端属于演进方向；系统层不把 V1/V2/V3 固化为文档结构。
- [ ] 运行 SRS 模板校验和 `python scripts/requirements/validate_domain_srs.py --root specs/001-project-delivery-platform --allow-missing-appendices`，预期只报告公共附录与旧入口尚未切换。
- [ ] 读取 `$git-commit` skill，仅提交系统层文件，提交信息 `docs(spec): 建立系统场景层规格`。

---

### Task 4: 完成领域协作、页面映射和全局追溯

**Files:**
- Create: `specs/001-project-delivery-platform/appendices/domain-collaboration.md`
- Create: `specs/001-project-delivery-platform/appendices/page-domain-mapping.md`
- Modify: `specs/001-project-delivery-platform/appendices/data-dictionary.md`
- Modify: `specs/001-project-delivery-platform/appendices/acceptance-traceability.md`
- Modify: `scripts/requirements/validate_domain_srs.py`
- Modify: `tests/requirements/test_validate_domain_srs.py`

**Interfaces:**
- Consumes: 系统层、13 份领域 SRS、来源台账、迁移矩阵和页面证据。
- Produces: 唯一协作契约、页面—场景—FR—数据 Owner 映射、统一术语和 `REQ → FR/NFR → AC` 全局追溯。

- [ ] 建立跨领域协作表，字段固定为协作编号、发起领域、Owner 领域、业务触发、输入语义、输出语义、失败结果、一致性要求、权限边界和追溯来源。
- [ ] 建立项目交付页面映射，覆盖总览、客户资产全景、项目跟踪、团队、SN、配置日志、联系人、工勘、物料、需求分析、施工计划、实施方案、到货、安装、配置、联调、割接、培训满意度、初终验和交付件。
- [ ] 建立割接页面“采集→分析/定级→申请→审批→执行/回退→观察→闭环”和巡检页面“准备→在线/离线执行→报告→整改→关闭”映射。
- [ ] 统一“客户/用户单位”“客户资产库/用户资产库”“设备/资产”“项目/项目节点/任务”术语，并记录外部主数据与本系统上下文的 Owner 边界。
- [ ] 重建 148 条来源需求的全局追溯，正式需求指向唯一领域 FR 和 AC；7 项 V3 指向演进范围且无当前确定性 AC。
- [ ] 扩展校验器检查协作双方存在、页面动作无悬空 FR、数据 Owner 存在、148 REQ 全覆盖、145 正式 FR 和所有 AC 唯一。
- [ ] 运行单元测试和全局校验，预期只报告 README/legacy 切换未完成。
- [ ] 读取 `$git-commit` skill，仅提交本任务文件，提交信息 `docs(spec): 完善领域协作与全局追溯`。

---

### Task 5: 切换权威入口并归档旧分卷

**Files:**
- Modify: `specs/001-project-delivery-platform/README.md`
- Create: `specs/001-project-delivery-platform/legacy/README.md`
- Move: `specs/001-project-delivery-platform/00-master-spec.md` → `specs/001-project-delivery-platform/legacy/00-master-spec.md`
- Move: `specs/001-project-delivery-platform/01-platform-and-permission.md` → `specs/001-project-delivery-platform/legacy/01-platform-and-permission.md`
- Move: `specs/001-project-delivery-platform/02-project-initiation.md` → `specs/001-project-delivery-platform/legacy/02-project-initiation.md`
- Move: `specs/001-project-delivery-platform/03-planning-and-execution.md` → `specs/001-project-delivery-platform/legacy/03-planning-and-execution.md`
- Move: `specs/001-project-delivery-platform/04-cutover-and-stabilization.md` → `specs/001-project-delivery-platform/legacy/04-cutover-and-stabilization.md`
- Move: `specs/001-project-delivery-platform/05-acceptance-and-closure.md` → `specs/001-project-delivery-platform/legacy/05-acceptance-and-closure.md`
- Move: `specs/001-project-delivery-platform/06-inspection-and-maintenance.md` → `specs/001-project-delivery-platform/legacy/06-inspection-and-maintenance.md`
- Move: `specs/001-project-delivery-platform/07-assets-and-outsourcing.md` → `specs/001-project-delivery-platform/legacy/07-assets-and-outsourcing.md`
- Move: `specs/001-project-delivery-platform/08-analytics-and-integration.md` → `specs/001-project-delivery-platform/legacy/08-analytics-and-integration.md`

**Interfaces:**
- Consumes: 完整新规格树和全局校验结果。
- Produces: 新双层目录成为唯一权威入口；旧文件可追溯但不再参与编号唯一性和开发输入。

- [ ] 核验 9 个源文件的绝对路径均位于当前规格目录，再使用 `git mv` 移入 `legacy/`，不得移动公共技术附录。
- [ ] `legacy/README.md` 写明旧分卷只用于历史追溯，不得作为开发、设计、测试和验收的权威输入。
- [ ] 重写根 `README.md`：阅读顺序为系统 SRS→领域地图→场景编排→领域 SRS→公共附录；列出 13 个领域文件、技术附录边界和 Markdown SSOT 规则。
- [ ] 将基础平台具体产品和 `master-jdk25` 仅保留在“技术选型/技术规范适用关系”章节，其余位置统一称“基础平台”。
- [ ] 更新校验器忽略 `legacy/` 的权威编号统计，但检查迁移矩阵能回溯到 legacy 文件。
- [ ] 运行全局校验，预期输出 `PASS: domain-oriented SRS migration is complete`；运行 `git diff --check` 退出 0。
- [ ] 读取 `$git-commit` skill，仅提交 README、legacy 移动和校验器修订，提交信息 `docs(spec): 切换领域化规格权威入口`。

---

### Task 6: 全量验证和交付差异说明

**Files:**
- Create: `docs/reports/2026-08-05-domain-srs-migration-validation.md`
- Modify: `tests/requirements/test_validate_domain_srs.py`
- Modify: `scripts/requirements/validate_domain_srs.py`

**Interfaces:**
- Consumes: 完整新规格树、legacy、迁移矩阵及所有校验结果。
- Produces: 可复核的验证报告、迁移前后直观差异图、计数结果和残余 `【待确认】` 清单。

- [ ] 运行全部单元测试，记录命令、通过数和失败数。
- [ ] 对 13 份领域 SRS 和系统 SRS 逐份运行模板校验，记录每份 unit_count 和 passed 状态。
- [ ] 运行全局校验，记录 148 REQ、145 正式 FR、7 演进项、13 领域、FR/BR/DR/AC 重复数和悬空引用数。
- [ ] 使用 Mermaid 绘制“原 8 个混合分卷→系统场景层＋13 个责任领域”的迁移差异，并列出主要拆分：巡检/维保、设备/工时/外包/备件/公告、分析/通用集成。
- [ ] 列出所有 `【待确认】`，区分“不会阻塞当前迁移”和“进入开发前必须确认”；不将待确认项写成已通过验收。
- [ ] 检查用户未跟踪文件仍未暂存，`git diff --check` 退出 0，工作树只剩预期文件。
- [ ] 读取 `$git-commit` skill，仅提交验证报告和必要测试/校验器修订，提交信息 `docs(spec): 验证领域化规格迁移`。

---

## 完成标准

- 新双层目录是唯一 Markdown 权威规格入口；旧分卷仅在 `legacy/` 追溯。
- 13 份领域 SRS 与 1 份系统 SRS 全部通过严格模板校验。
- 148 条来源需求覆盖率 100%；145 项正式 FR 迁移率 100%；7 项 V3 全部仅作为演进方向。
- 每个正式 FR、BR、DR、AC 和核心对象只有一个 Owner，无交叉重复权威定义。
- 项目交付、割接、巡检页面的每个确定业务动作均可定位到目标 FR 和数据 Owner。
- 公共平台能力、经营分析和业务特定外部交互边界清晰；除技术选型外统一使用“基础平台”。
- 原始资料、Word、Excel、HTML、VSDX、DDL 和用户未跟踪文件未被修改。
