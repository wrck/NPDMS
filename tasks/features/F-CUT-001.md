# F-CUT-001 割接统一配置版本与采集项基础

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_DONE`
> Feature Ready Gate：`PASS / NPDMS-FCUT001-FEATURE-READY-20260830-02`
> Technical Plan Gate：`PASS / NPDMS-FCUT001-TECHPLAN-20260830-02`
> Implementation Done Gate：`PASS / NPDMS-FCUT001-IMPLEMENTATION-DONE-20260830-02`
> Requirement ID：`CUT-07（V1/P0）`、`CUT-09（V1/P0）`、`CUT-10（V1/P1）`
> Feature Spec：`specs/features/F-CUT-001-cutover-unified-configuration-foundation.md`
> 复用审计：`specs/features/F-CUT-001-legacy-reuse-audit.md`
> Feature物理契约：`specs/features/F-CUT-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md`
> Open Questions：`Q-FCUT001-001`、`Q-FCUT001-002`、`Q-FCUT001-003`均已关闭

## 当前最小工作单元

- F-CUT-001已完成CUT-07/09/10联合实现与验收；后续Feature必须重新通过自己的Feature Ready Gate，不由本Feature状态外推。

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
- 已实现24类普通风险、五类双机检查`17/25/23/24/8 = 97`项及12类调研的同聚合编辑、联合预检和原子发布；`businessCategoryCode`与绑定级`requiredResult`通过V132前向契约完整往返。
- 后端相关Reactor共112项测试通过（0失败、0错误、2跳过），其中`pms-module-cutover` 38项全部通过；前端矩阵Vitest 6项、TypeScript、ESLint、Stylelint及生产构建通过。
- 已在专用Compose项目`npdms-e-fcut001-test`完成V1～V132迁移；隔离数据库`npdms_fcut001_test`、后端`61280`、前端`20082`均未复用其他工作树端口或数据。
- 真实浏览器验收覆盖24/24、五类97/97、16/17拒绝且旧发布版继续有效、全场景覆盖缺口、调研必填空值、背景依赖错误、修复后界面发布、发布历史只读、无权限路由拒绝及1440/1024/768/320四档视口；控制台错误和失败请求均为0。
- 验收证据：`docs/engineering/evidence/f-cut-001-runtime-evidence.json`、`output/f-cut-001-v18/browser-current/result.json`及同目录截图。

## 阻断

无。

## 已知边界

- XLSX/HTML仅作为名称、字段和界面参考，不参与需求裁决、不形成不一致结论，也不作为完成门禁。
- V2自动指派未提前实施；F-CUT-001只完成CUT-07/09/10的V1统一配置、风险与调研矩阵基础。
- 浏览器及迁移数据位于隔离验收库，不作为正式业务数据。
