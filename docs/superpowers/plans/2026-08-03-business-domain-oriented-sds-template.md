# Business-Domain-Oriented SDS Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有软件方案设计说明书模板重构为适用于大型业务功能模块的业务领域导向模板，并同步交付一致的Markdown和Word版本。

**Architecture:** 以领域定位、业务能力、业务对象、业务规则、业务场景和跨领域协作为SDS主干；流程、状态、数据、接口和技术机制作为支撑设计。Markdown是模板内容基线，Word从Markdown生成并保持标题、表格和字段一致。

**Tech Stack:** Markdown、Python 3、python-docx、OOXML、Git。

## Global Constraints

- 只修改SDS模板，不修改SRS和TAS模板。
- 模板必须保持行业、技术栈、架构风格和业务生命周期中立。
- 大功能模块必须使用`BD-{领域}-{序号}`设计单元，允许包含多个子能力、对象和流程。
- 流程与状态是业务能力的支撑内容，不得重新成为文档主轴。
- API参数、物理DDL、部署脚本和代码结构允许引用专项附件。
- Markdown与Word的章节、字段和表格必须一致。
- Word使用A4页面、真实标题样式、自动目录、固定表格几何和页码域。
- 当前工作区中的`需求/数据元.xlsx`与`需求/需求细节.md`不在变更范围内。

---

### Task 1: 重构业务领域导向SDS Markdown模板

**Files:**
- Modify: `docs/templates/software-development/02-software-solution-design-template.md`
- Reference: `docs/superpowers/specs/2026-08-03-business-domain-oriented-sds-template-design.md`

**Interfaces:**
- Consumes: 已确认的SDS重构设计稿及现有SRS/SDS/TAS文档边界。
- Produces: 作为Word生成输入的完整业务领域SDS Markdown模板。

- [ ] **Step 1: 调整文档定位和使用规则**

将文档定位修改为“业务能力如何组织并形成整体解决方案”，明确技术详细设计可以拆分为专项附件，保留需求基线和设计版本字段。

- [ ] **Step 2: 建立总体业务领域结构**

按以下顺序组织一级章节：

1. 文档说明与设计输入。
2. 业务背景、设计目标与约束。
3. 领域定位、范围与上下文。
4. 业务能力地图与子模块划分。
5. 角色、责任与核心场景。
6. 领域模型与核心业务对象。
7. 生命周期、状态与业务规则。
8. 大功能模块／业务能力详细方案。
9. 跨领域协作与外部依赖。
10. 业务配置、策略与扩展机制。
11. 数据、指标、权限与合规。
12. 流程、交互与用户体验。
13. 应用与技术实现映射。
14. 非功能、运维、迁移与发布。
15. 风险、决策、追溯与评审。

- [ ] **Step 3: 建立大功能模块设计单元**

新增`BD-XXX-001 〈业务领域／功能模块名称〉`模板，完整包含业务定位、范围边界、能力分解、角色场景、核心对象、规则与不变量、生命周期、跨域协作、差异化配置、数据指标、权限合规、实现映射和追溯。

- [ ] **Step 4: 将流程控制降为支撑内容**

把原有“核心处理逻辑、时序、事务、异常与降级”从文档主体前部移至业务能力单元或技术实现映射章节；保留正常、分支、异常、回退和补偿设计能力。

- [ ] **Step 5: 执行Markdown结构检查**

运行：

```powershell
rg -n "^#{1,4} " docs/templates/software-development/02-software-solution-design-template.md
rg -n "待补充|待完善|稍后处理" docs/templates/software-development/02-software-solution-design-template.md
```

预期：章节完整；除模板定义的“待确认事项”外不存在未说明占位；SRS和TAS文件无变更。

- [ ] **Step 6: 提交Markdown重构**

```powershell
git add -- docs/templates/software-development/02-software-solution-design-template.md
git commit -m "docs(sds): 重构业务领域方案模板"
```

### Task 2: 同步生成SDS Word模板

**Files:**
- Modify: `docs/templates/software-development/02-software-solution-design-template.docx`
- Temporary: 任务专用Python生成脚本，生成完成后删除，不纳入提交。

**Interfaces:**
- Consumes: Task 1输出的SDS Markdown模板。
- Produces: 与Markdown结构一致的SDS Word模板。

- [ ] **Step 1: 从Markdown生成Word正文**

使用工作区捆绑Python与`python-docx`读取Markdown，映射标题、正文、提示块、清单和表格；不得手工维护一份与Markdown脱离的Word内容。

- [ ] **Step 2: 应用Word模板样式**

使用A4、25.4毫米页边距、Calibri 11磅与微软雅黑中文字体；应用真实Heading 1至Heading 4样式、自动目录、页眉、`PAGE/NUMPAGES`页码域和固定DXA表格几何。

- [ ] **Step 3: 校验Markdown与Word对应关系**

使用`python-docx`提取Word标题，与Markdown的二至四级标题逐项比较；预期无缺失标题，Word额外标题仅允许“目录”。

- [ ] **Step 4: 提交Word模板**

```powershell
git add -- docs/templates/software-development/02-software-solution-design-template.docx
git commit -m "docs(sds): 同步业务领域方案Word模板"
```

### Task 3: 完成模板质量检查和交付

**Files:**
- Verify: `docs/templates/software-development/02-software-solution-design-template.md`
- Verify: `docs/templates/software-development/02-software-solution-design-template.docx`

**Interfaces:**
- Consumes: Task 1与Task 2的最终文件。
- Produces: 可交付的结构检查、版式检查及变更说明。

- [ ] **Step 1: 校验DOCX包和表格几何**

使用ZIP完整性检查、`python-docx`和文档技能的`table_geometry.py`验证DOCX包、A4页面、标题样式、表格宽度、网格及单元格宽度。

- [ ] **Step 2: 执行渲染检查**

优先使用文档技能的`render_docx.py`生成逐页PNG并检查封面、目录、长表格、标题分页、页眉和页脚。若环境缺少LibreOffice或Microsoft Word，记录为视觉QA限制并执行完整结构校验，不得声称已经通过逐页渲染。

- [ ] **Step 3: 检查范围纪律**

运行：

```powershell
git status --short
git diff --stat HEAD~2..HEAD
```

预期：只有SDS Markdown、SDS Word及已确认设计／计划文档发生变化；SRS、TAS和用户原始资料未修改。

- [ ] **Step 4: 提供交付说明**

列出SDS Markdown与Word路径，说明业务领域导向变化、未修改内容、校验结果及视觉渲染限制。
