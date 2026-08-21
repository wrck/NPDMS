# F-PROJ-001 手动项目创建与模板初始化

> Feature 状态：`IN_PROGRESS_V1_8_REVALIDATION`
> 当前任务：`Task 9真实MySQL测试基座已建立；固定端口13306被其他工作树容器占用，等待隔离环境后执行事务/并发证据`
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
| `Task 6` | `IMPLEMENTED_REVISION_WATERMARK_MASTER_DATA_BLOCKED` |
| `Task 7` | `IMPLEMENTED_VERSIONED_ASSIGNMENT_RANGE_BLOCKED` |
| `Task 8` | `IMPLEMENTED_BROWSER_ACCEPTANCE_PENDING` |
| `Task 9` | `IN_PROGRESS_ENVIRONMENT_BLOCKED` |
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

平台幂等成功、操作审计与`ProjectCreated` Outbox现已由正式创建应用服务纳入同一事务，旧Controller事务外保存及吞异常路径已移除。创建提交已改为`templateRevisionId + candidateWatermark`重算校验，不再按模板ID重新选择latest revision。

Task 7指派入口现已强制`Idempotency-Key`与`If-Match`，以`WHERE id=? AND version=?`条件递增Project版本；服务端再次校验`pms:project:assign`功能权限，关闭同Project同角色的重叠旧区间并追加带层级/办事处/地点责任快照的新区间。幂等成功、操作审计与`ProjectServiceManagerAssigned` Outbox复用平台事实事务边界；仅确认服务经理不会把主责状态从`UNASSIGNED`误改为已指派。

Task 8前端候选选择已从template级切换为`templateRevisionId`，创建原样提交`candidateWatermark`；表单不写本地存储，同一未修改请求复用幂等Key，任一输入或revision变化后自动生成新Key。指派交互提交必需幂等头与Project版本，版本冲突时重新加载Project再要求用户确认；前端构建和5项事后测试通过。全仓`ts:check`仍被既有auto-import声明缺失阻断，目标Feature文件筛查无新增类型错误。

Task 6后置阻断：当前仓库没有实施地点权威主数据接口；基础平台部门API也不暴露可比较版本，无法在不臆造语义的前提下完成客户/办事处/实施地点稳定ID、版本及数据范围的全量服务端校验。该缺口不阻断版本化指派、前端非主数据部分及后续故障注入准备，但在接口补齐前Task 6和AC-FPROJ-007不得判定完成。

Task 9环境阻断：当前仓库Compose规定的MySQL固定端口`13306`被`E:\AICoding\Projects\NPDMS`工作树的`npdms-t8-mysql-1`占用。本工作树未复用该数据库、未停止跨工作树容器，也未擅自修改固定端口；真实MySQL集成测试已编译但尚未执行，AC-FPROJ-002/004/006/008/010不得据此判定完成。

Task 10前置验证：后端Reactor测试`144`项中`141`项通过，`3`项真实MySQL IT因`skipITs`门禁跳过；仓库基线规则校验通过，规格快照校验仍有两个受管文件SHA-256不匹配。前端计划命令`typecheck/build`与当前脚本不一致；实际`ts:check`仍被全仓既存错误阻断，本Feature相关路径无类型错误，`build:local`通过。验证中发现的子项目旧创建签名回归已由`697c384`修复并通过17项定向后端测试、相关ESLint与前端构建。

当前仍未完成主数据范围收紧、前端向导、真实MySQL故障注入与浏览器验收，因此所有AC继续保持未勾选。受管Feature Spec旧计划引用及规格基线哈希异常继续作为独立门禁记录。
