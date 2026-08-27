# Task 1: T-CP-008 创建资产及V2业务模块骨架

来源：tasks/todo.md T-CP-008；依赖 T-CP-007 已完成。

## 要求

创建 asset、outsourcing、analytics 和 integration Maven 模块；分析和集成模块不拥有核心主数据。全部模块参与根工程和 yudao-server 统一构建。遵循 docs/pms-module-boundary-and-naming.md、docs/api-design-specification.md、DEC-013；无稳定调用方时不创建空 `-api` 模块，不允许 PMS `-biz` 模块之间直接依赖。

## 验收标准

- 根 POM 声明并统一构建四个模块。
- yudao-server 按平台单体装配需要引入四个模块。
- docker/backend/Dockerfile 纳入四个模块的构建上下文。
- analytics 和 integration 只建立边界骨架，不创建或拥有客户、项目、设备等核心主数据实体与表。
- 不修改或提交 sql/mysql 下用户下载的 SQL，也不处理 tests/e2e/platform-smoke.cjs。

## 验证

- Docker/JDK25 执行聚焦 Maven 构建或验证，覆盖全部新模块。
- 运行或补充模块依赖边界检查，确认无跨 `-biz` 依赖或空 API 模块。

## 提交

通过验证后自动提交一个 Conventional Commit。提交前必须读取并遵循 C:\Users\user\.codex\skills\git-commit-general\SKILL.md。不得 push。