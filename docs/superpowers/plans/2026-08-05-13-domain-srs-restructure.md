# 13 领域需求规格重构实施计划

> **执行要求：** 使用 `executing-plans` 工作流逐项实施并校验；本任务在现有隔离 worktree 内执行。

**目标：** 依据《项目实施交付管理平台规格体系调整原则（13领域版）》将现有 145 项正式 FR 按唯一 Owner 拆分到 13 个业务领域，保持 FR/BR/AC 编号和内容可追溯，并明确 `FR-ENG-021 到货签收管理` 的 Owner 为 `IMP 现场实施`。

**方法：** 保留当前 8 个需求分卷作为迁移来源；建立一份完整的 FR Owner 迁移台账；由脚本从来源分卷提取完整 FR 单元并按 Owner 生成 13 份领域 SRS；总体规格仅编排跨领域场景；验收追溯表改指向新 Owner 文档。领域间只记录依赖、输入、输出和事件，不复制业务规则或验收标准。

**约束：** Markdown 是开发事实源；不修改原始 Excel/HTML/DOCX/DDL；不使用项目记忆；除技术选型外统一称“基础平台”；推导内容标记 `【建议】`，不确定项标记 `【待确认】`。

---

## 任务 1：建立迁移基线和唯一 Owner 模型

**文件：**

- 新增：`scripts/requirements/split_domain_srs.py`
- 新增：`specs/001-project-delivery-platform/appendices/domain-requirement-ownership-matrix.md`

- [x] 从 8 个现有领域分卷解析全部正式 FR，并校验总数为 145。
- [x] 配置 13 个目标领域以及每项 FR 的唯一 Owner。
- [x] 明确 `FR-ENG-021` 归属 `IMP`，不得归入 `AST`。
- [x] 校验 Owner 映射无遗漏、无重复、无未知 FR。

## 任务 2：生成 13 份领域需求规格

**文件：**

- 新增/重建：`specs/001-project-delivery-platform/domains/*-srs.md`

- [x] 按 Owner 将每个正式 FR 的完整定义迁入唯一领域文档。
- [x] 保持 FR、BR、DR、AC 原编号和需求语义。
- [x] 为每个领域补充职责、边界、核心对象和跨域依赖；不复制其他 Owner 的规则。
- [x] 使用通用 SRS 模板校验每份领域文档结构。

## 任务 3：更新总体规格和领域导航

**文件：**

- 修改：`specs/001-project-delivery-platform/00-master-spec.md`
- 修改：`specs/001-project-delivery-platform/README.md`

- [x] 在总体规格中呈现 13 领域地图。
- [x] 保留项目交付、割接、巡检的页面/工作台编排，并通过 Owner 引用领域需求。
- [x] 增加页面—Owner—数据来源和生命周期事件关系。
- [x] 将 README 的权威阅读入口切换为 13 份领域 SRS 和迁移台账。

## 任务 4：更新全局追溯并验证

**文件：**

- 修改：`specs/001-project-delivery-platform/appendices/acceptance-traceability.md`
- 新增：`docs/reports/2026-08-05-13-domain-srs-restructure-validation.md`

- [x] 将所有追溯记录的需求文档定位更新为目标 Owner 领域文档。
- [x] 校验 145 项正式 FR 在新领域文档中恰好各出现一次。
- [x] 校验每个 BR、DR、AC 仅跟随其 FR 出现在一个 Owner 文档中。
- [x] 校验到货签收只在 IMP 完整定义，AST 仅作为资产数据依赖方。
- [x] 运行 Markdown/模板检查和 `git diff --check`，形成可复核报告。

## 完成标准

- 13 份领域 SRS 构成新的领域化需求入口。
- 145 项正式 FR 的唯一 Owner 覆盖率为 100%，重复归属为 0。
- `FR-ENG-021 到货签收管理` 的 Owner 为 IMP。
- 总体规格保持业务场景连续，但不重复领域业务规则。
- 原始资料和用户未跟踪文件未被修改。
