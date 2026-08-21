# 项目实施约束

## 事实来源

- 规格仓库是业务与设计唯一事实源；本地规格快照是锁定实现输入，不是独立事实源。
- 当前实现输入由 `docs/specification-baseline/manifest.json` 的 `source.commit` 决定；受管文件必须通过同步工具校验。
- 禁止在NPDMS直接修改受管快照；规格变更必须先进入规格仓库，再锁定新提交并重新同步。
- 修改设计或代码前，必须从当前快照依次读取PRD、工程链、相关SDS、Feature Spec和当前Task。
- `tasks/plan.md` 和 `tasks/todo.md` 已标记为历史材料，不再生成或驱动新开发任务。
- 本项目禁止使用项目记忆补全需求、设计或验收结论；不确定事项必须回到仓库文档或标记为【待确认】。

## 技术基线

- 后端基线：`yudao-boot-mini` `master-jdk25`，锁定提交见 `docs/upstream-sources.md`。
- mini 缺失的 Yudao 模块：仅从 `YunaiV/ruoyi-vue-pro` 的锁定提交按需导入。
- 共享文件以 mini 为准，不得用完整仓库整体覆盖。
- 首期为模块化单体，由 `yudao-server` 统一装配；PMS 业务模块统一使用 `pms-module-*`。
- Yudao 平台接口保持上游定义；新增 PMS Business API 遵循 `/api/v1/pms/...` 规范。

## 运行与验证

- Docker Compose 是 MySQL、Redis 和 Flyway 本地基础设施的权威运行入口，不承载前端和后端应用。
- 后端在宿主机使用 JDK 25/Maven 构建和运行；前端在宿主机使用 Node.js/Corepack/pnpm 构建和运行。
- 宿主机应用与 Docker 基础设施的启动、端口和验证方式以 `docs/development.md` 和 `compose.yaml` 为准。
- 配置模板不得包含真实凭据；本地默认值只能用于隔离开发环境。
- 每项自研任务遵循失败测试、最小实现、重构与完整验证顺序。
- 编译、静态页面或 API 单测不能替代业务验收；UI 闭环必须由真实浏览器完成。

## 领域边界

- 项目组合与项目父子树是两个独立模型。
- 项目树和任务 WBS 都不得实现为固定层级。
- 模块间不得依赖目标模块的 `-biz`、Service、Mapper、Repository 或直接访问其业务表。
- 无稳定跨模块调用方时，不创建空的 `-api` 模块。

# AI编码行为

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
