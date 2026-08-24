# F-PROJ-001 手动项目创建与模板初始化

> Feature 实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Implementation Done Gate：`PASS`
> 当前阻断：无
> 当前任务：F-PROJ-002 已达到 Feature Ready，转入全新 V1.8 Technical Plan
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> Feature Spec SHA-256：`566fdcf3f82fe26fa1121c37e50d622080c70f225c6134ad8dbc25adbb17dd97`
> Technical Plan：`docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md`
> 锁定规格提交：`b453cb0b80804e288be360b50ee0bfef6809b798`

## 事实边界

- 本 Feature 已从 PRD V1.8 首个 Feature 重新审计并改造，没有根据 V1.7 存量实现推定完成。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与当前门禁校验。
- `tasks/plan.md`、`tasks/todo.md`及 2026-08-21 的旧 F-PROJ-001 计划均不作为当前实施输入。
- 当前受管快照已回写 F-PROJ-001、PM-01、PM-03 的实施完成状态与 Code/Test 证据，规格仓库与 NPDMS 进度一致。
- F-PROJ-001 的局部实施计划完成不表示总体 Implementation Phase 完成。按 PRD 顺序，PM-01/PM-03 之后首个尚未实施的 P0 Requirement 是 PM-02；形成其正式 Feature Spec 是下一正常任务，不构成阻断。Feature Spec 提交并同步前不得开始 PM-02 代码改造，所有当前范围 Feature 完成前不得进入 Deployment。

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

代码、Schema、测试、浏览器证据、评审及受管追溯均已满足本 Feature 的工程实现完成条件，Implementation Done Gate 为 `PASS`。该结论不代表 Deployment、SIT、UAT、Release 或治理 GO。

当前阻断为无。PM-02 正式 Feature Spec 已达到 `BASELINE / READY` 并同步到锁定提交 `b453cb0b80804e288be360b50ee0bfef6809b798`。总体 Implementation Phase 继续转入 F-PROJ-002 全新 V1.8 Technical Plan；旧 F-PM02 Spec、Technical Plan 与现有代码只作存量审计输入，不得据此判断已实现。
