# NPDMS 运行标识统一实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在首次 Git 基线提交前，将运行环境、默认数据库和本地配置统一为 NPDMS，并排除本地凭据和真实第三方密钥。

**Architecture:** 只改运行配置边界，通过机械替换同步 Compose、脚本、测试和直接引用文档；业务模块、包名、API、Requirement ID 与业务字典保持不变。使用现有基础设施校验脚本和定向检索验证配置闭环。

**Tech Stack:** Docker Compose、Spring Boot YAML、PowerShell、Node.js E2E、Git

## Global Constraints

- 环境变量前缀统一为 `NPDMS_*`。
- 默认数据库名统一为 `npdms`。
- Spring 本地开发 Profile 统一为 `npdms-dev`。
- `.env` 与 `application-npdms-dev.yaml` 不进入 Git。
- 不修改 `pms-module-*`、Java `*.pms.*`、`/api/v1/pms`、Requirement ID 和业务字典类型。
- 首次提交不得包含真实密码、Token、私钥、第三方 API Key、日志或构建产物。

---

### Task 1: 统一运行环境和数据库标识

**Files:**
- Modify: `.env.example`
- Modify: `compose.yaml`
- Modify: `docker/scripts/new-local-env.ps1`
- Modify: `yudao-server/src/main/resources/application-docker.yaml`
- Modify: `scripts/checkpoint-smoke-test.ps1`
- Modify: `tests/e2e/platform-smoke.cjs`
- Modify: `tests/e2e/platform-access-journey.cjs`
- Modify: `tests/e2e/verify-test-foundation.ps1`
- Modify: `tests/infrastructure/verify-docker-baseline.ps1`

**Interfaces:**
- Consumes: 当前 `PMS_*` 环境变量及 `pms_platform` 默认数据库。
- Produces: 对应的 `NPDMS_*` 环境变量和 `npdms` 默认数据库。

- [x] 定位运行文件中的 `${PMS_*}`、`$env:PMS_*`、`process.env.PMS_*` 和 `pms_platform`。
- [x] 将同一变量在生产方、消费方和测试断言中同步改名，不建立旧变量兼容别名。
- [x] 运行 `docker compose --env-file .env.example config --quiet`，预期因空凭据被拒绝。
- [x] 使用生成的本地 `.env` 运行 `docker compose config --quiet`，预期通过。

### Task 2: 统一本地 Profile 并收敛敏感配置

**Files:**
- Modify: `.gitignore`
- Rename locally: `yudao-server/src/main/resources/application-pms-dev.yaml` to `yudao-server/src/main/resources/application-npdms-dev.yaml`
- Modify: `yudao-server/src/main/resources/application.yaml`
- Modify: direct configuration references under `docs/` and `specs/`

**Interfaces:**
- Consumes: `pms-dev` Profile、本地凭据配置和基础平台示例第三方密钥。
- Produces: `npdms-dev` Profile、被 Git 忽略的本地配置和环境变量化的第三方凭据。

- [x] 在确认目标文件不存在后移动本地 Profile 文件，并同步忽略规则。
- [x] 将配置中看似真实的第三方密钥改为环境变量占位，必需凭据不设置可误用默认值。
- [x] 定向检索 `application-pms-dev`、`pms-dev` 和真实密钥模式，预期正式文件中无残留。

### Task 3: 更新说明、验证边界并完成首次提交

**Files:**
- Modify: `docs/development.md`
- Modify: `docs/upstream-sources.md`
- Modify: directly affected configuration appendices under `specs/`
- Verify: all staged files

**Interfaces:**
- Consumes: Task 1 和 Task 2 的最终运行标识。
- Produces: 可复现的首次 Git 基线提交。

- [x] 更新仅直接说明环境变量、数据库和 Profile 的文档，不机械改写历史 PRD 或业务术语。
- [x] 运行旧运行标识定向检索，允许受保护的业务标识，禁止旧环境变量、默认库和 Profile 残留。
- [x] 运行 `git diff --cached --check`、敏感信息扫描及仓库既有配置校验。
- [x] 显式暂存正式文件，确认 `.env`、本地 Profile、日志、构建产物和过程缓存未进入索引。
- [x] 使用 Conventional Commit 消息创建首次提交，并用 `git log --oneline -1` 验证。
