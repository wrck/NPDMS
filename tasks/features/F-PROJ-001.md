# F-PROJ-001 手动项目创建与模板初始化

> Feature 状态：`IN_PROGRESS_V1_8_REVALIDATION`
> 当前任务：`全新V1.8计划Task 1～5已完成实现；Task 4真实MySQL全失败点证据留待Task 9，下一步进入Task 6 API/权限边界`
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> Feature Spec SHA-256：`86f175e05eb578cc35bae9b64715955b123bce584836c328be87b64c929e9431`
> Technical Plan（当前实施分支）：`docs/superpowers/plans/2026-08-21-f-proj-001-v18-revalidation-and-atomic-remediation.md`
> 失效引用：受管Feature Spec中的旧Technical Plan路径不得恢复、复用或作为实施输入，等待规格仓库前向修订后重新同步。
> 锁定规格提交：`9087469316ec5ba321b34f09fc601d98c30a3d2b`

## 任务授权与事实边界

本文件只以锁定的 Feature Spec 及其引用的 PRD V1.8、工程链、SDS 和 ADR 为实施输入。`tasks/plan.md` 与 `tasks/todo.md` 是历史材料，不能授权本 Feature 的设计、实现、验收或发布。

现有 V1.7 手动创建项目和模板基座仅是复用审计证据，状态为 `V1_7_REVALIDATION_REQUIRED`；不得据此将 PM-01、PM-03 或本 Feature 标记为已实现。

## Start Gate（Task 2 及后续实施前）

- [x] 规格快照的 `source.commit` 为锁定的 40 位提交，且基线校验通过。
- [x] Feature Spec 路径及 SHA-256 已锁定。
- [x] 实施仓库存在可验证的后端、前端、Flyway 和测试工程，且已在隔离分支核对。
- [x] 已审计 `proj_*` 现有实现与本 Feature 的差量；没有将 V1.7 复用证据误作实现结论。
- [x] PROJ 与 ACC 位于同一模块、同一数据源和Spring事务管理器；ACC初始化接口以`MANDATORY`强制加入创建事务。真实MySQL回滚证据仍留待集成测试门禁。
- [x] 当前Schema、模块边界、权限与API实施输入已按锁定规格核对；未定义权威值域未作臆造。

## 后续任务标识

| 标识 | 状态 |
| --- | --- |
| `Task 2` | `COMPLETED_V1_8_DELTA_AUDIT` |
| `Task 3` | `COMPLETED_ACC_OWNER_INTERFACE` |
| `Task 4` | `IMPLEMENTED_MYSQL_FAILURE_INJECTION_PENDING_TASK_9` |
| `Task 5` | `COMPLETED_PLATFORM_FACTS_ATOMIC` |
| `Task 6` | `NOT_STARTED` |
| `Task 7` | `NOT_STARTED` |
| `Task 8` | `NOT_STARTED` |
| `Task 9` | `NOT_STARTED` |
| `Task 10` | `NOT_STARTED` |

## 验收跟踪

- [ ] AC-FPROJ-001 候选与预览
- [ ] AC-FPROJ-002 显式模板创建
- [ ] AC-FPROJ-003 唯一默认模板
- [ ] AC-FPROJ-004 WorkBinding 完整性
- [ ] AC-FPROJ-005 V1 人工确认服务经理
- [ ] AC-FPROJ-006 幂等与并发
- [ ] AC-FPROJ-007 权限负向
- [ ] AC-FPROJ-008 原子失败
- [ ] AC-FPROJ-009 真实界面闭环
- [ ] AC-FPROJ-010 创建失败无持久化

## 当前结论

已从PRD V1.8重新审计V1.7数据库、后端、API、权限、前端和测试，未将任何现有实现判定为AC完成。全新计划Task 1～3已完成前向Schema、V1.8任务执行定义/契约工厂及ACC Owner同事务接口；Task 4已完成S0阻断、三状态初始化、逐任务契约落库和创建路径停止写旧PROJ交付件表。

高可信差异包括：PROJ直接写`proj_project_deliverable`违反ACC Owner边界；成功幂等记录位于创建事务外且失败被吞掉；模板提交时重新选择latest revision；Project状态未分离为`ACTIVE / S0 / UNASSIGNED`；模板任务与实例缺少WorkBinding/PermissionPolicy/CompletionRule/GateRef执行契约；指派缺少必需`Idempotency-Key`、`If-Match`和数据范围校验；前端修正失败输入后仍复用旧Key；审计与Outbox不存在同事务成功证据。

平台幂等成功、操作审计与`ProjectCreated` Outbox现已由正式创建应用服务纳入同一事务，旧Controller事务外保存及吞异常路径已移除。当前仍未完成版本化指派、完整API权限/主数据收紧、前端向导、真实MySQL故障注入与浏览器验收，因此所有AC继续保持未勾选。受管Feature Spec旧计划引用及规格基线哈希异常继续作为独立门禁记录。
