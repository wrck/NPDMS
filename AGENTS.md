# 项目实施约束

## 事实来源

- 本仓库是业务、设计、实现、测试与验收证据的唯一事实源，不再维护外部规格仓快照或第二套Feature状态源。
- 权威优先级固定为：`PRD > Engineering Constitution > SDS > Feature Spec > Technical Plan > Task > Code > Test / Runtime Evidence`。下游可以细化上游，但不得静默改变业务语义。
- 修改设计或代码前，必须依次读取 `docs/baseline/prd-v1.8.md`、`docs/engineering/00-engineering-chain.md`、`docs/README.md`、相关SDS、`specs/features/`中的相关Feature Spec和`tasks/features/`中的当前Task。
- 规格与实现变更在同一分支内按上游到下游推进：先修订正式规格，再修改代码、迁移和测试，最后更新追溯投影与证据引用。禁止以代码、测试、浏览器证据或索引投影反向覆盖正式规格。
- `tasks/plan.md` 和 `tasks/todo.md` 已标记为历史材料，不再生成或驱动新开发任务。
- 本项目禁止使用项目记忆补全需求、设计或验收结论；不确定事项必须回到仓库文档或标记为【待确认】。

## 规格与实现硬规则

- 未经批准的变更请求，不得修改PRD业务语义。
- V1/V2实现不得夹带V3或`OUT_OF_SCOPE`事项。
- 不得臆造业务角色、审批节点、状态转换、阈值、Gate或数据Owner。
- 不得直接写生命周期状态绕过状态机，也不得绕过服务端授权与数据范围。
- 不得暴露或持久化明文设备密码、私钥、Token或其他Secret。
- 不得覆盖不可变历史、快照、批准版本、审计记录或来源证据。
- 通知送达和外部HTTP成功不等于业务完成，除非正式契约明确如此定义。
- 不得为了使测试通过而降低校验、授权、状态机或业务规则。
- 评审草稿、门禁证据、计划、外部输入、生成报告和临时副本必须按`docs/README.md`分类；不得在正式目录创建并行的`*-draft.md`、`*-review.md`或`*-final2.md`。

## 缺失、歧义与追溯

- 业务规则缺失、歧义或冲突时不得猜测：标记`BLOCKED_BY_SPEC`，登记到`docs/decisions/open-questions.md`，只继续不依赖该问题的独立工作。
- 每个Feature、API、数据库变更、事件、工作流和测试必须引用一个或多个Requirement ID，并维护`Requirement -> SDS -> Feature -> Code -> Test`链路。
- Feature Ready只由Feature Spec维护；Implementation Status只由当前Feature任务记录维护；并行认领、写边界、Worktree交接和master集成回执只由`tasks/delivery-units/DU-*.md`维护。一个Delivery Unit可以覆盖多个Feature或Task，但Feature仍是唯一Done单元；索引、追溯矩阵、Git、CI和浏览器结果只作投影或证据。
- 任何实现写入前必须先在master提交Delivery Unit认领，并让目标分支包含该认领提交；分支名、Worktree目录、分支内Task状态和继承提交均不构成认领。废弃路径不得承接新Feature，只能由声明`旧功能范围`的DU执行废弃补强、安全修复、历史只读、迁移解释或删除。
- 外部集成必须定义系统Owner、方向、权威字段、映射、来源键、幂等键、超时、重试、补偿、对账、降级和审计。

## 任务执行协议

- 每项任务遵循`READ -> PLAN -> IMPLEMENT -> TEST -> SELF-REVIEW -> REPORT`。
- 实施前报告修改文件、Requirement ID、领域/API/数据库/权限/状态机影响、测试和风险。
- 实施后报告完成范围、变更文件、需求覆盖、测试及结果、已知限制和后续事项。

## 技术基线

- 后端基线：`yudao-boot-mini` `master-jdk25`，锁定提交见 `docs/upstream-sources.md`。
- mini 缺失的 Yudao 模块：仅从 `YunaiV/ruoyi-vue-pro` 的锁定提交按需导入。
- 共享文件以 mini 为准，不得用完整仓库整体覆盖。
- 首期为模块化单体，由 `yudao-server` 统一装配；PMS 业务模块统一使用 `pms-module-*`。
- Yudao 平台接口保持上游定义；新增 PMS Business API 遵循 `/api/v1/pms/...` 规范。
- 模块级功能设计和实施前必须先完整审计仓库内对应旧实现，逐项判断可直接复用、需复制增强和不可复用的边界；需要增强时先复制到新的类、组件或页面后再改造，旧类、旧页面、旧接口、旧数据和原有功能保持不变，除非锁定规格另行批准修改。
- WorkBinding、自动匹配和其他动态适配属于基础能力之上的接入层；必须先从模块整体需求完成可独立使用的基础功能闭环，再接入适配层，不得按单一消费者倒置实施顺序或把完整模块拆成无法组装的零散能力。

## 运行与验证

- Docker Compose 是 MySQL、Redis 和 Flyway 本地基础设施的权威运行入口，不承载前端和后端应用。
- 后端在宿主机使用 JDK 25/Maven 构建和运行；前端在宿主机使用 Node.js/Corepack/pnpm 构建和运行。
- 宿主机应用与 Docker 基础设施的启动、端口和验证方式以 `docs/development.md` 和 `compose.yaml` 为准。
- 配置模板不得包含真实凭据；本地默认值只能用于隔离开发环境。
- 每项复杂的核心自研任务遵循复用优先、最小实现、失败测试、重构与完整验证顺序，简单任务遵循最小实现、快速验证。
- 编译、静态页面或 API 单测不能替代业务验收；UI 闭环必须由真实浏览器完成。
- 功能模块完成后必须补充初始化数据：按SDS落字典、菜单、配置等有明确定义的种子；无明确定义内容的以示例数据迁移补充（前向版本、幂等、creator标识、高段ID或专用前缀），且必须覆盖关键维度的组合情况（含精确命中、部分限定、优先级让位、无匹配与停用不参与等场景）以及对象全环节（如模板须覆盖S0~S6全阶段与阶段、任务、里程碑、交付件、门禁及门禁引用各要素类型）；受权威来源映射约束的值域（如CRM属性映射）不得臆造取值。

## 领域边界

- 项目组合与项目父子树是两个独立模型。
- 项目树和任务 WBS 都不得实现为固定层级。
- 模块间不得依赖目标模块的 `-biz`、Service、Mapper、Repository 或直接访问其业务表。
- 无稳定跨模块调用方时，不创建空的 `-api` 模块。

## 数据库查询编码

- 新增或改造数据库查询前，必须读取并遵守 `docs/coding/database-query-interface.md`。
- 除主键和稳定复合唯一键查询外，Mapper 查询方法必须只接收一个场景化 Query 对象；禁止长位置参数列表、`Map` 和万能查询对象。
- 简单单表条件使用类型安全的 `LambdaQueryWrapperX`；联表、窗口函数、动态集合、锁查询等文本 SQL 必须进入 Mapper XML。
- 禁止 SQL 注解、`${}`、`.last(...)` 及在 Service/Controller 中拼接 SQL。
- 空权限集合或空集合筛选必须返回空结果，不能因省略条件扩大查询范围；租户、权限和模块表所有权边界必须保持生效。

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

For long-running asynchronous work:

- Empty `write_stdin` polls MUST use `yield_time_ms >= 180000`;
  prefer `300000` when intermediate output is not needed.
- `functions.wait` MUST use `yield_time_ms >= 180000`.
- `functions.exec` MUST set its outer `@exec yield_time_ms` at least
  30000 ms longer than the longest nested tool wait, so the outer
  code cell does not yield first.
- Do not apply the long wait to non-empty `write_stdin` calls that
  send interactive input.
- These tools return early when the process or cell completes.

Do not wake the model merely to report that work is still running.
