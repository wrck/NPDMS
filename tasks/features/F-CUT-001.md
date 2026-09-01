# F-CUT-001 割接统一配置版本与采集项基础

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`MASTER_REVALIDATION`
> Feature Ready Gate：`PASS / NPDMS-FCUT001-FEATURE-READY-20260830-02`
> Technical Plan Gate：`PASS / NPDMS-FCUT001-TECHPLAN-20260830-02`
> Implementation Done Gate：`PENDING_MASTER_FINAL_DOD / 历史候选证据不直接反推master Done`
> Requirement ID：`CUT-07（V1/P0）`、`CUT-09（V1/P0）`、`CUT-10（V1/P1）`
> Feature Spec：`specs/features/F-CUT-001-cutover-unified-configuration-foundation.md`
> 复用审计：`specs/features/F-CUT-001-legacy-reuse-audit.md`
> Feature物理契约：`specs/features/F-CUT-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md`
> Open Questions：`Q-FCUT001-001`、`Q-FCUT001-002`、`Q-FCUT001-003`均已关闭，`Q-FCUT001-004`已拒绝为阻断

## 当前最小工作单元

- `DU-20260901-FCUT001-INTEGRATION`正在专属工作树选择性适配候选`08457e39..72ccb83f`。实现与历史验收证据已进入复验范围；只有候选提交边界、最新master构建、后端/前端测试、迁移与最终运行DoD全部裁决后，才能恢复Implementation Done。

## master协调与分支候选

- 2026-09-01 16:59:30 +08:00审计截点的`TECHNICAL_PLAN_READY`结论是冻结历史；当前权威状态由本Task更新为`IN_PROGRESS / MASTER_REVALIDATION`，Requirement覆盖继续保持`NOT_STARTED`。
- `codex/integrate-f-cut-001@72ccb83f8052`仅作为干净候选来源；`codex/f-cut-001-matrices@85b93828eb04`继承候选后继续实施其他Feature，二者都不是master状态源。
- 当前有效认领是`DU-20260901-FCUT001-INTEGRATION`；只迁移F-CUT-001代码、迁移、测试和证据，候选Task、Feature索引与追溯投影不覆盖master。
- 下一动作：完成最新master上的后端、前端、迁移与追溯验证，提交候选适配并经master Code Review选择性集成；最终运行DoD仍由master另行裁决。

## 已完成

- 已读取CUT-07及CUT-09/CUT-10依赖约束、ADR-0031、SDS物理Owner和数据库查询规范。
- 已完成旧task/risk/plan后端、前端、迁移和测试审计，结论为`NEW_ONLY / PRESERVE_LEGACY`。
- 已按用户确认收敛事实源：XLSX/HTML可作为名称、字段和界面参考，但不参与业务裁决、不形成不一致决策或发布门禁。
- 已形成F-CUT-001草案、复用边界、API草案、权限、状态、不变量和验收标准。
- `Q-FCUT001-001`、`Q-FCUT001-002`已关闭，Feature Ready已通过。
- 已实现配置根、统一采集项、绑定规则三表聚合及草稿、发布、停用、复制修订状态机；已发布版本不可修改，写命令使用`If-Match`并发控制。
- 已实现动态维度、三类采集项、方案章节、字典/外部来源配置、逐项发布预检以及服务端权威字典标签快照。
- 已实现独立割接配置菜单、查询/维护/发布/停用权限和管理界面；旧`pms_cut_task/risk/plan`后端、页面、接口与数据均未修改。
- 已新增并执行`V128`～`V131`前向迁移，在隔离MySQL 8.4数据库`npdms_fcut001`验证至版本131。
- 后端模块17项测试全部通过，其中F-CUT-001新增领域/服务测试10项；前端新增文件ESLint、Prettier、Stylelint和全仓TypeScript检查通过，完整本地构建通过。
- 已在后端`59380`、前端`19181`完成真实浏览器验收：已发布只读、复制保留子项、无效草稿定位错误、发布拒绝且仍为草稿；1440/1024/768/320四档视口通过，控制台错误、页面错误和失败HTTP响应均为0。
- 历史候选已实现24类普通风险、五类双机检查`17/25/23/24/8 = 97`项及12类调研的同聚合编辑、联合预检和原子发布；`businessCategoryCode`与绑定级`requiredResult`通过V132前向契约完整往返。
- 历史候选后端相关Reactor共112项测试通过（0失败、0错误、2跳过），其中`pms-module-cutover` 38项全部通过；前端矩阵Vitest 6项、TypeScript、ESLint、Stylelint及生产构建通过。
- 历史候选在专用Compose项目`npdms-e-fcut001-test`完成V1～V132迁移，并完成24/24、五类97/97、缺项拒绝、覆盖缺口、调研必填、背景依赖、发布历史只读、无权限拒绝与多视口真实浏览器验收。
- 候选验收证据已纳入复验输入：`docs/engineering/evidence/f-cut-001-runtime-evidence.json`、`output/f-cut-001-v18/browser-current/result.json`及同目录截图；这些历史结果不替代最新master最终DoD。
- 专属适配分支Code Review补齐同项同维度组合仅改优先级仍可重复发布、风险错误索引受前置停用规则偏移两类缺口，并新增3项失败回归；修正后CUT模块41项测试全部通过。
- 最新master适配内容的后端Reactor共115项测试通过（0失败、0错误、2跳过）；前端矩阵Vitest 6项、变更文件ESLint/Prettier/Stylelint、全仓TypeScript检查和`build:local`均通过。
- 工程治理49项追溯/Delivery Unit/旧实现清单回归、追溯只读重建、DU边界和旧实现清单校验通过；Feature仍因下述初始化迁移与最终运行DoD缺口保持`IN_PROGRESS`。

## 阻断

当前无业务语义阻断；`CHG-PRD-2026-08-30-008`已确认双机检查合计97项，扩展Feature Spec已于2026-08-30取得需求方确认，Technical Plan Gate已通过。

Implementation Done仍受以下工程证据约束：

- V129只有3条CUT-07最小示例，V132只增加两列，尚无幂等前向示例迁移覆盖24类普通风险、五类97项、12类调研及关键匹配组合；隔离浏览器验收数据不能代替仓库初始化数据。
- 候选分支的MySQL和浏览器结果属于历史证据；选择性集成后的最终master仍需在独立运行环境重验迁移与真实浏览器DoD。

## 已知边界

- XLSX/HTML仅作为名称、字段和界面参考，不参与需求裁决、不形成不一致结论，也不作为完成门禁。
- V2自动指派未提前实施；F-CUT-001只覆盖CUT-07/09/10的V1统一配置、风险与调研矩阵基础。
- 浏览器及迁移数据位于隔离验收库，不作为正式业务数据。
