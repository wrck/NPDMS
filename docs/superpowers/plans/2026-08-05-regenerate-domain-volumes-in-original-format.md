# 13 领域文档按原分卷格式重建 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将 13 份领域需求文档从通用 SRS 12 章结构恢复为原 8 个需求分卷采用的五段式 Markdown 格式，同时保持 145 项正式 FR 的唯一 Owner 和原始需求语义。

**Architecture:** 继续使用 `migrate_domain_srs.py` 解析原 8 个分卷和维护 13 领域 Owner 映射，但将渲染层替换为原分卷格式渲染器。领域文档只承载领域边界、需求清单、原格式 FR 单元、V3 演进范围和验收门禁；跨领域编排继续保留在总体规格及迁移矩阵。校验器改为验证原分卷结构、唯一性和专项 Owner 规则，不再依赖通用 SRS 模板。

**Tech Stack:** Markdown、Python 3、pytest、PowerShell、Git。

## Global Constraints

- 本次禁止使用 `create-software-spec-docs` 技能、通用 SRS 模板及其模板校验器。
- 13 领域代码、名称和唯一 Owner 映射保持当前已确认结果。
- `FR-ENG-021 到货签收管理` 唯一归属 `IMP 现场实施`。
- FR、BR、DR、AC 编号及确定性需求语义保持不变。
- 每项正式 FR 恢复原分卷的元数据和 10 个业务小节，不新增无来源字段。
- V3 条目只保留范围与演进方向，不生成正式详细 FR，不纳入当前开发验收。
- 除技术选型外统一使用“基础平台”；推导内容标记 `【建议】`，不确定内容标记 `【待确认】`。
- 不修改原始 Excel、HTML、DOCX、DDL 和用户未跟踪的 `需求/数据元.xlsx`、`需求/需求细节.md`。

---

### Task 1: 用测试锁定原分卷文档结构

**Files:**

- Modify: `tests/requirements/test_migrate_domain_srs.py`

**Interfaces:**

- Consumes: `render_srs(profile, requirements, evolution_items)`。
- Produces: 原分卷格式的结构契约测试，供 Task 2 的渲染器实现使用。

- [x] **Step 1: 将示例渲染测试改为原格式断言**

  对 `FR-TST-001` 断言以下结构：

  ```python
  assert "# TST领域需求规格：示例领域" in rendered
  assert "## 1. 领域目标与边界" in rendered
  assert "## 2. 需求清单" in rendered
  assert "## 3. 详细功能规格" in rendered
  assert "## FR-TST-001 示例功能" in rendered
  assert "### 业务目标" in rendered
  assert "### 权限、通知与审计" in rendered
  assert "### 验收标准" in rendered
  assert "# 5. 领域验收门禁" in rendered
  assert "## 文档控制" not in rendered
  assert "#### 基本信息" not in rendered
  ```

- [x] **Step 2: 增加 FR 内容保持测试**

  逐项断言原元数据、10 个小节、BR/DR/AC 和段落内容只做“基础平台”术语归一，不改变业务语义；断言正式 FR 使用二级标题，V3 使用三级标题。

- [x] **Step 3: 运行测试并确认按预期失败**

  Run:

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py -q
  ```

  Expected: 原格式断言失败，失败位置指向当前 12 章渲染结果。

### Task 2: 将生成器切换为原分卷格式

**Files:**

- Modify: `scripts/requirements/migrate_domain_srs.py`
- Test: `tests/requirements/test_migrate_domain_srs.py`

**Interfaces:**

- Consumes: `LegacyRequirement`、`EvolutionItem`、`DomainProfile` 和当前 `DOMAIN_PROFILES`。
- Produces: `render_srs(profile, requirements, evolution_items) -> str`，返回原分卷五段式 Markdown。

- [x] **Step 1: 实现正式 FR 原格式渲染函数**

  新增：

  ```python
  ORIGINAL_SECTION_ORDER = (
      "业务目标", "前置条件", "主流程", "分支与异常", "状态流转",
      "业务规则", "数据要求", "权限、通知与审计", "输出与后置条件", "验收标准",
  )

  def _render_original_requirement(requirement: LegacyRequirement) -> str:
      metadata_names = (
          "用例编号", "来源需求", "所属版本", "优先级／复杂度",
          "参与角色", "业务场景", "来源标识",
      )
      metadata = "<br>\n".join(
          f"**{name}：** {_metadata(requirement, name)}"
          for name in metadata_names
      )
      sections = "\n\n".join(
          f"### {name}\n\n{_section(requirement, name)}"
          for name in ORIGINAL_SECTION_ORDER
      )
      return _normalize_business_terms(
          f"## {requirement.fr_id} {requirement.title}\n\n{metadata}\n\n{sections}"
      )
  ```

  输出 `## FR-*` 标题、7 行粗体元数据和 10 个 `###` 小节；字段值及段落正文来自解析后的 legacy 内容，并经过 `_normalize_business_terms()`。

- [x] **Step 2: 实现需求清单和 V3 原格式渲染**

  正式需求清单固定列为“功能编号、来源、名称、版本、优先级”；V3 条目使用原来的三级标题和六个项目字段。只有领域包含演进项时才输出 `# 4. V3演进范围`。

- [x] **Step 3: 重写 `render_srs()` 外层结构**

  固定输出：文档标题和简要元数据、`## 1`、`## 2`、`## 3`、可选 `# 4`、`# 5`。删除通用 SRS 的文档控制、角色场景、业务模型、产品架构、专项需求、NFR、风险和推荐编号等渲染内容。

- [x] **Step 4: 运行生成器测试**

  Run:

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py -q
  ```

  Expected: 全部 PASS。

### Task 3: 将全局校验器切换为原分卷结构规则

**Files:**

- Modify: `scripts/requirements/validate_domain_srs.py`
- Modify: `tests/requirements/test_validate_domain_srs.py`

**Interfaces:**

- Consumes: `DOMAIN_PROFILES`、迁移矩阵及 `domains/*-srs.md`。
- Produces: `validate_tree(root, allow_missing_targets=False, legacy_root=None) -> list[str]`，校验原格式结构和全局唯一性。

- [x] **Step 1: 编写失败测试覆盖原格式定义识别**

  测试构造 `## FR-TST-001`、`### 业务规则` 和 `### 验收标准`，要求校验器识别正式定义；构造缺少 `## 2. 需求清单` 或 `# 5. 领域验收门禁` 的领域文档，要求返回 `MISSING_ORIGINAL_SECTION`。

- [x] **Step 2: 调整正式定义识别表达式**

  将 FR 定义识别从 `^### FR-*` 改为 `^## FR-*`；BR、DR、AC 继续按原分卷列表格式识别。V3 的 `^### FR-*` 只计演进项引用，不计正式 FR 定义。

- [x] **Step 3: 增加原分卷结构校验**

  每份领域 SRS 必须包含：

  ```text
  ## 1. 领域目标与边界
  ## 2. 需求清单
  ## 3. 详细功能规格
  # 5. 领域验收门禁
  ```

  同时禁止出现 `## 文档控制`、`## 12. 需求追溯与基线检查` 和 `#### 基本信息`。

- [x] **Step 4: 增加到货签收专项校验**

  断言 `FR-ENG-021` 的正式定义只出现在 `IMP-现场实施需求规格.md`，且不出现在 `AST-资产管理需求规格.md`。

- [x] **Step 5: 运行校验器测试**

  Run:

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_validate_domain_srs.py -q
  ```

  Expected: 全部 PASS。

### Task 4: 重建 13 份领域文档并更新验证证据

**Files:**

- Regenerate: `specs/001-project-delivery-platform/domains/*-srs.md`
- Modify: `specs/001-project-delivery-platform/README.md`
- Modify: `docs/reports/2026-08-05-13-domain-srs-restructure-validation.md`

**Interfaces:**

- Consumes: Task 2 的原格式渲染器和 Task 3 的校验规则。
- Produces: 13 份原分卷格式领域文档及可复核验证报告。

- [x] **Step 1: 运行生成器重建领域文档**

  Run:

  ```powershell
  python scripts/requirements/migrate_domain_srs.py `
    --root specs/001-project-delivery-platform `
    --write-domains
  ```

  Expected: 输出 13 条以 `WROTE` 开头并以 `-srs.md` 结尾的写入记录。

- [x] **Step 2: 更新 README 的格式说明**

  明确 13 份领域文档采用原分卷五段式结构；总体规格负责跨域编排；迁移矩阵负责唯一 Owner 追溯；删除“严格通用 SRS 模板”相关说明。

- [x] **Step 3: 更新验证报告**

  将“13/13 通用 SRS 模板校验”替换为“13/13 原分卷结构校验”，记录 FR 145、唯一 145、重复 0，并记录 `FR-ENG-021` 在 IMP 为 1、在 AST 为 0。

- [x] **Step 4: 运行完整自动化验证**

  Run:

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py tests/requirements/test_validate_domain_srs.py -q
  python scripts/requirements/validate_domain_srs.py `
    --root specs/001-project-delivery-platform
  git diff --check
  ```

  Expected: pytest 全部 PASS；领域树输出 `PASS: domain-oriented SRS migration model is valid`；差异检查退出码为 0。

- [x] **Step 5: 核对工作树边界**

  Run:

  ```powershell
  git status --short
  ```

  Expected: 仅生成器、校验器、测试、13 份领域文档、README、计划和验证报告发生变化；`需求/数据元.xlsx`、`需求/需求细节.md` 仍为未跟踪且未暂存。

## 完成标准

- 13 份领域文档均采用原分卷五段式结构，不包含通用 SRS 12 章结构。
- 145 项正式 FR、全部 BR/DR/AC 保持唯一且可追溯。
- 7 项 V3 演进内容只在演进范围出现一次。
- 到货签收唯一归属 IMP，AST 不重复定义。
- 自动化测试、领域树校验和 `git diff --check` 全部通过。
