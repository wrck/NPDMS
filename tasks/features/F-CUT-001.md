# F-CUT-001 割接统一配置版本与采集项基础

> Feature实施状态：`TECHNICAL_PLAN_READY`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FCUT001-FEATURE-READY-20260830-02`
> Technical Plan Gate：`PASS / NPDMS-FCUT001-TECHPLAN-20260830-02`
> Implementation Done Gate：`REOPENED / 原CUT-07证据保留但不再代表扩展Scope完成`
> Requirement ID：`CUT-07（V1/P0）`、`CUT-09（V1/P0）`、`CUT-10（V1/P1）`
> Feature Spec：`specs/features/F-CUT-001-cutover-unified-configuration-foundation.md`
> 复用审计：`specs/features/F-CUT-001-legacy-reuse-audit.md`
> Feature物理契约：`specs/features/F-CUT-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-cut-001-risk-survey-matrices.md`
> Open Questions：`Q-FCUT001-001`、`Q-FCUT001-002`、`Q-FCUT001-003`均已关闭

## 当前最小工作单元

- `DU-20260901-FCUT001-INTEGRATION`登记分支候选`08457e39..72ccb83f`，但尚未合入master；先更新到最新master并核对提交边界，再串行集成、执行Feature完整验证和Code Review。完成这些步骤前不得恢复Implementation Done。

## master协调与分支候选

- 2026-09-01 16:59:30 +08:00审计截点，master权威状态仍为`TECHNICAL_PLAN_READY`，Requirement覆盖保持`NOT_STARTED`。
- `codex/integrate-f-cut-001@72ccb83f8052`是干净的完成候选；`codex/f-cut-001-matrices@85b93828eb04`只继承该候选后继续实施其他Feature，不能作为F-CUT-001的第二份状态源。
- 原实施早于Delivery Unit规则，不能倒签活动认领；`INTEGRATION_CANDIDATE`只登记审计与待集成范围，不追认分支内Implementation Done。
- 下一动作：在master串行集成窗口核对候选相对最新master的公共契约、Flyway、共享文件与回归边界；只有最终合入状态通过完整DoD后才能更新本文件的Implementation Done Gate。

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
- 验收证据：`docs/engineering/evidence/f-cut-001-runtime-evidence.json`。

## 阻断

当前无业务语义阻断；`CHG-PRD-2026-08-30-008`已确认双机检查合计97项，扩展Feature Spec已于2026-08-30取得需求方确认，Technical Plan Gate已通过。

## 已知边界

- XLSX/HTML仅作为名称、字段和界面参考，不参与需求裁决、不形成不一致结论，也不作为完成门禁。
- V2自动指派未提前实施；F-CUT-001只完成CUT-07统一配置版本与采集项基础。
- 浏览器及迁移数据位于隔离验收库，不作为正式业务数据。
