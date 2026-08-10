# 领域需求规格中文文件名迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将 13 份领域需求规格统一重命名为“领域编码-中文领域名称需求规格.md”，并同步生成器、校验器及全部文档引用。

**Architecture:** `DomainProfile.filename` 作为目标文件名唯一配置源，生成器按新配置写入中文文件名。Git 以重命名方式迁移现有文件，不保留英文副本；校验器根据同一 profile 集合检查恰好 13 个权威文件，引用扫描保证仓库规格中不存在旧路径。

**Tech Stack:** Markdown、Python 3、pytest、PowerShell、Git。

## Global Constraints

- 目标格式固定为 `领域编码-中文领域名称需求规格.md`。
- 领域编码、中文领域名称、Owner 映射、FR/BR/DR/AC 内容均不改变。
- 旧英文 `*-srs.md` 领域文件不得保留副本。
- `FR-ENG-021 到货签收管理`继续唯一归属 `IMP-现场实施需求规格.md`。
- 领域文档继续采用原分卷五段式结构。
- 不修改或暂存 `需求/数据元.xlsx`、`需求/需求细节.md`。

---

### Task 1: 用测试锁定中文文件名映射

**Files:**

- Modify: `tests/requirements/test_migrate_domain_srs.py`
- Modify: `tests/requirements/test_validate_domain_srs.py`

**Interfaces:**

- Consumes: `DOMAIN_PROFILES` 及 `validate_tree()`。
- Produces: 13 个目标文件名的确定性测试和旧英文文件拒绝测试。

- [x] **Step 1: 增加 profile 文件名断言**

  ```python
  expected = {
      "PLT": "PLT-平台公共能力需求规格.md",
      "CUS": "CUS-客户与服务关系需求规格.md",
      "PROJ": "PROJ-项目治理需求规格.md",
      "COM": "COM-合同订单履约需求规格.md",
      "SOL": "SOL-交付准备与方案需求规格.md",
      "IMP": "IMP-现场实施需求规格.md",
      "CUT": "CUT-变更切换与稳定治理需求规格.md",
      "ACC": "ACC-验收与项目闭环需求规格.md",
      "AST": "AST-资产管理需求规格.md",
      "RES": "RES-资源与外包需求规格.md",
      "SRV": "SRV-服务运营需求规格.md",
      "KNO": "KNO-技术知识治理需求规格.md",
      "ANA": "ANA-经营分析需求规格.md",
  }
  assert {profile.code: profile.filename for profile in DOMAIN_PROFILES} == expected
  ```

- [x] **Step 2: 增加校验器拒绝旧英文文件测试**

  在临时 `domains/` 中创建 `IMP-field-implementation-srs.md`，断言结果包含 `EXTRA_TARGET`，同时缺少 `IMP-现场实施需求规格.md` 时包含 `MISSING_TARGET`。

- [x] **Step 3: 运行测试确认失败**

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py tests/requirements/test_validate_domain_srs.py -q
  ```

  Expected: 文件名映射断言失败，显示当前英文文件名。

### Task 2: 更新目标文件名并迁移实际文件

**Files:**

- Modify: `scripts/requirements/migrate_domain_srs.py`
- Modify: `scripts/requirements/validate_domain_srs.py`
- Rename: `specs/001-project-delivery-platform/domains/*-srs.md`

**Interfaces:**

- Consumes: Task 1 的 13 个确定性文件名。
- Produces: `DOMAIN_PROFILES` 中文文件名配置和 13 份中文命名领域文档。

- [x] **Step 1: 修改 13 个 `DomainProfile.filename`**

  按 Task 1 的 `expected` 字典逐项替换文件名，不改变 profile 的 code、name、responsibility、fr_ids 和 evolution_ids。

- [x] **Step 2: 更新到货签收专项目标路径**

  将校验器 `_validate_receipt_owner()` 的期望文件名改为 `IMP-现场实施需求规格.md`。

- [x] **Step 3: 运行生成器写入新文件名**

  ```powershell
  python scripts/requirements/migrate_domain_srs.py `
    --root specs/001-project-delivery-platform `
    --write-domains
  ```

  Expected: 生成 13 个中文文件名。

- [x] **Step 4: 删除旧英文领域文件**

  逐项确认旧文件与对应新文件内容一致后，仅删除以下旧路径：

  ```text
  PLT-public-platform-capabilities-srs.md
  CUS-customer-and-service-relationship-srs.md
  PROJ-project-governance-srs.md
  COM-contract-and-order-fulfillment-srs.md
  SOL-delivery-preparation-and-solution-srs.md
  IMP-field-implementation-srs.md
  CUT-change-cutover-and-stability-srs.md
  ACC-acceptance-and-project-closure-srs.md
  AST-asset-management-srs.md
  RES-resource-and-outsourcing-srs.md
  SRV-service-operations-srs.md
  KNO-technical-knowledge-governance-srs.md
  ANA-business-analytics-srs.md
  ```

- [x] **Step 5: 运行测试**

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py tests/requirements/test_validate_domain_srs.py -q
  ```

  Expected: 全部 PASS。

### Task 3: 同步引用并完成全局验证

**Files:**

- Modify: `specs/001-project-delivery-platform/README.md`
- Modify: `specs/001-project-delivery-platform/appendices/acceptance-traceability.md`
- Modify: `docs/reports/2026-08-05-13-domain-srs-restructure-validation.md`
- Modify: `docs/superpowers/plans/2026-08-05-13-domain-srs-restructure.md`
- Modify: `docs/superpowers/plans/2026-08-05-regenerate-domain-volumes-in-original-format.md`

**Interfaces:**

- Consumes: Task 2 的中文文件路径。
- Produces: 所有规格索引、追溯和验证证据指向存在的中文文件。

- [x] **Step 1: 批量替换 13 个旧路径引用**

  只替换完整文件名，不替换领域代码或业务名称。迁移矩阵的证据列继续指向原 `01-` 至 `08-` 来源分卷，不改为目标领域文件。

- [x] **Step 2: 增加引用完整性扫描**

  ```powershell
  rg -n "PLT-public-platform-capabilities-srs|CUS-customer-and-service-relationship-srs|PROJ-project-governance-srs|COM-contract-and-order-fulfillment-srs|SOL-delivery-preparation-and-solution-srs|IMP-field-implementation-srs|CUT-change-cutover-and-stability-srs|ACC-acceptance-and-project-closure-srs|AST-asset-management-srs|RES-resource-and-outsourcing-srs|SRV-service-operations-srs|KNO-technical-knowledge-governance-srs|ANA-business-analytics-srs" specs docs scripts tests
  ```

  Expected: 无输出。

- [x] **Step 3: 运行完整验证**

  ```powershell
  $env:PYTHONPATH = (Get-Location).Path
  uv run --with pytest pytest tests/requirements/test_migrate_domain_srs.py tests/requirements/test_validate_domain_srs.py -q
  python scripts/requirements/validate_domain_srs.py `
    --root specs/001-project-delivery-platform
  git diff --check
  ```

  Expected: pytest 全部 PASS；领域树校验 PASS；差异检查退出码为 0。

- [x] **Step 4: 核对工作区边界**

  ```powershell
  git status --short
  ```

  Expected: 中文命名迁移及前一任务的原格式重建文件发生变化；`需求/数据元.xlsx`、`需求/需求细节.md` 仍为未跟踪且未暂存。

## 完成标准

- 13 份领域需求规格均使用“编码-中文领域名称需求规格.md”。
- `domains/` 中不存在旧英文 `*-srs.md` 文件。
- 所有内部引用均指向存在的中文文件。
- 145 项正式 FR、7 项 V3 和到货签收 Owner 校验保持通过。
