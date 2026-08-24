# F-PROJ-001 手动项目创建与模板初始化

> Feature 实施状态：`IMPLEMENTATION_COMPLETE`
> Implementation Done Gate：`BLOCKED_BY_MANAGED_TRACEABILITY_SYNC`
> 当前任务：将已完成实现与验证结果回写规格仓库，并同步新的受管快照
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> Feature Spec SHA-256：`9a2d5203c1194e9ac19a12c6ddf81c564b976cb3af24194d5d8d6a1fbf77b68e`
> Technical Plan：`docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md`
> 锁定规格提交：`052ecdb580cdbe2fe38ca7fbad771cc1bada3e5a`

## 事实边界

- 本 Feature 已从 PRD V1.8 首个 Feature 重新审计并改造，没有根据 V1.7 存量实现推定完成。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与当前门禁校验。
- `tasks/plan.md`、`tasks/todo.md`及 2026-08-21 的旧 F-PROJ-001 计划均不作为当前实施输入。
- 当前受管快照的 `specs/features/README.md` 仍写“Implementation Start Gate 未满足”，`docs/traceability/requirement-matrix.md` 仍将 PM-01、PM-03 标为 `NOT_STARTED`。两处均由 `docs/specification-baseline/manifest.json` 管理，NPDMS 不直接改写；必须在规格仓库前向修订后重新同步。

## 任务完成情况

| 任务 | 结果 |
| --- | --- |
| Task 0 锁定 V1.8 正式规格输入 | COMPLETE |
| Task 1 公司、部门编码与组织范围 | COMPLETE |
| Task 2 AST 地点核心模型 | COMPLETE |
| Task 3 地址、站点、位置树与区划映射 API | COMPLETE |
| Task 4 项目多站点与服务经理人工指派 | COMPLETE |
| Task 5 工勘、安装与设备当前位置事实 | COMPLETE |
| Task 6 组织与地点管理页面 | COMPLETE |
| Task 7 项目、工勘、安装与设备页面闭环 | COMPLETE |
| Task 8 MySQL、模块边界与全量回归 | COMPLETE |
| Task 9 真实浏览器业务验收 | COMPLETE |

## 验收跟踪

- [x] AC-FPROJ-001 候选与预览
- [x] AC-FPROJ-002 显式模板创建
- [x] AC-FPROJ-003 唯一默认模板
- [x] AC-FPROJ-004 WorkBinding 完整性
- [x] AC-FPROJ-005 V1 人工确认服务经理
- [x] AC-FPROJ-006 幂等与并发
- [x] AC-FPROJ-007 权限负向
- [x] AC-FPROJ-008 原子失败
- [x] AC-FPROJ-009 真实界面闭环
- [x] AC-FPROJ-010 创建失败无持久化

AC-FPROJ-007 原阻断已由 V1.8 组织与地点基础改造关闭：公司、部门编码、同一行公司—部门授权范围、AST 地址/站点/位置、项目多站点、区划—办事处建议和人工指派均已形成稳定 API、服务端校验、负向测试及真实浏览器证据。站点不绑定公司或办事处；自动建议只按行政区划编码和办事处部门编码映射，最终由授权人员人工确认。

## 验证证据

- 规格快照校验：PASS。
- PMS 模块边界校验：PASS。
- 后端全量回归：30 个 Reactor 模块全部 SUCCESS。
- 真实 MySQL：V1～V68 空库、重复迁移、validate 及 V63→V68 通过；定向场景 12/12 通过。
- 前端合同测试：15/15 通过。
- 前端类型检查：0 错误。
- 前端本地构建：PASS；仅保留既有 legacy CSS `*zoom` 非阻断警告。
- 真实浏览器：组织与地点、多站点项目、人工指派、工勘、安装及设备位置历史场景刷新后均通过。
- 独立规格与质量复审：当前实施计划 `PLAN COMPLETE GO`。

详细证据：

- `output/f-proj-001-v18/database-evidence.md`
- `output/f-proj-001-v18/browser-acceptance.md`
- `output/location-v18/mysql-acceptance.md`
- `output/location-v18/browser-acceptance.md`
- `output/location-v18/regression-summary.md`

## 当前门禁与下一步

代码、Schema、测试、浏览器证据和评审已满足本 Feature 的工程实现完成条件，但受管追溯尚未同步，因此 Implementation Done Gate 暂记 `BLOCKED_BY_MANAGED_TRACEABILITY_SYNC`，不得据此宣称 Deployment、SIT、UAT、Release 或治理 GO。

该阻断只影响正式门禁晋级，不回退已完成任务。可并行准备可部署制品、配置契约、前向迁移验证和本地环境复现；正式进入 Deployment Gate 前，规格仓库必须将 F-PROJ-001 实施状态及 PM-01、PM-03 的 Code/Test 追溯回写并锁定新提交，再由 NPDMS 同步受管快照。
