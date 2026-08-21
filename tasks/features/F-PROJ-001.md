# F-PROJ-001 手动项目创建与模板初始化

> Feature 状态：`IN_PROGRESS_V1_8_REVALIDATION`
> 当前任务：`Task 6功能权限已补齐；AC-FPROJ-007权威主数据范围阻断后置登记`
> Requirement ID：`PM-01`、`PM-03`
> 历史 Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> 历史 Feature Spec SHA-256：`86f175e05eb578cc35bae9b64715955b123bce584836c328be87b64c929e9431`
> Technical Plan（当前实施分支）：`docs/superpowers/plans/2026-08-21-f-proj-001-v18-revalidation-and-atomic-remediation.md`
> 失效引用：受管Feature Spec中的旧Technical Plan路径不得恢复、复用或作为实施输入，等待规格仓库前向修订后重新同步。
> 锁定规格提交：`9087469316ec5ba321b34f09fc601d98c30a3d2b`

## 任务授权与事实边界

本轮工程链以当前Technical Plan和任务内已确认的V1.8变更为实施输入。自2026-08-22起，`specs/001-project-delivery-platform/`仅作历史参考，不再执行其规格基线校验，也不以该目录哈希差异判定当前工程门禁。`tasks/plan.md` 与 `tasks/todo.md` 同样不能授权本 Feature 的设计、实现、验收或发布。

现有 V1.7 手动创建项目和模板基座仅是复用审计证据，状态为 `V1_7_REVALIDATION_REQUIRED`；不得据此将 PM-01、PM-03 或本 Feature 标记为已实现。

## Start Gate（Task 2 及后续实施前）

- [x] 历史规格快照的 `source.commit` 与 Feature Spec SHA-256 已登记，仅用于追溯。
- [x] 实施仓库存在可验证的后端、前端、Flyway 和测试工程，且已在隔离分支核对。
- [x] 已审计 `proj_*` 现有实现与本 Feature 的差量；没有将 V1.7 复用证据误作实现结论。
- [x] PROJ 与 ACC 位于同一模块、同一数据源和Spring事务管理器；ACC初始化接口以`MANDATORY`强制加入创建事务，九个真实MySQL故障点的回滚证据已通过。
- [x] 当前Schema、模块边界、权限与API实施输入已按锁定规格核对；未定义权威值域未作臆造。

## 后续任务标识

| 标识 | 状态 |
| --- | --- |
| `Task 2` | `COMPLETED_V1_8_DELTA_AUDIT` |
| `Task 3` | `COMPLETED_ACC_OWNER_INTERFACE` |
| `Task 4` | `COMPLETED_MYSQL_FAILURE_INJECTION` |
| `Task 5` | `COMPLETED_PLATFORM_FACTS_ATOMIC` |
| `Task 6` | `IMPLEMENTED_FUNCTION_PERMISSION_MASTER_DATA_BLOCKED` |
| `Task 7` | `IMPLEMENTED_VERSIONED_ASSIGNMENT_RANGE_BLOCKED` |
| `Task 8` | `IMPLEMENTED_BROWSER_ACCEPTANCE_PENDING` |
| `Task 9` | `COMPLETED_REAL_MYSQL_ATOMICITY_CONCURRENCY` |
| `Task 10` | `COMPLETED_EVIDENCE_AC007_BLOCKED` |

## 验收跟踪

- [x] AC-FPROJ-001 候选与预览
- [x] AC-FPROJ-002 显式模板创建
- [x] AC-FPROJ-003 唯一默认模板
- [x] AC-FPROJ-004 WorkBinding 完整性
- [x] AC-FPROJ-005 V1 人工确认服务经理
- [x] AC-FPROJ-006 幂等与并发
- [ ] AC-FPROJ-007 权限负向
- [x] AC-FPROJ-008 原子失败
- [x] AC-FPROJ-009 真实界面闭环
- [x] AC-FPROJ-010 创建失败无持久化

## 当前结论

已从PRD V1.8重新审计V1.7数据库、后端、API、权限、前端和测试，未将任何现有实现判定为AC完成。全新计划Task 1～3已完成前向Schema、V1.8任务执行定义/契约工厂及ACC Owner同事务接口；Task 4已完成S0阻断、三状态初始化、逐任务契约落库和创建路径停止写旧PROJ交付件表。

高可信差异包括：PROJ直接写`proj_project_deliverable`违反ACC Owner边界；成功幂等记录位于创建事务外且失败被吞掉；模板提交时重新选择latest revision；Project状态未分离为`ACTIVE / S0 / UNASSIGNED`；模板任务与实例缺少WorkBinding/PermissionPolicy/CompletionRule/GateRef执行契约；指派缺少必需`Idempotency-Key`、`If-Match`和数据范围校验；前端修正失败输入后仍复用旧Key；审计与Outbox不存在同事务成功证据。

平台幂等成功、操作审计与`ProjectCreated` Outbox现已由正式创建应用服务纳入同一事务，旧Controller事务外保存及吞异常路径已移除。创建提交已改为`templateRevisionId + candidateWatermark`重算校验，不再按模板ID重新选择latest revision。

Task 7指派入口现已强制`Idempotency-Key`与`If-Match`，以`WHERE id=? AND version=?`条件递增Project版本；服务端再次校验`pms:project:assign`功能权限，关闭同Project同角色的重叠旧区间并追加带层级/办事处/地点责任快照的新区间。幂等成功、操作审计与`ProjectServiceManagerAssigned` Outbox复用平台事实事务边界；仅确认服务经理不会把主责状态从`UNASSIGNED`误改为已指派。

Task 8前端候选选择已从template级切换为`templateRevisionId`，创建原样提交`candidateWatermark`；表单不写本地存储，同一未修改请求复用幂等Key，任一输入或revision变化后自动生成新Key。指派交互提交必需幂等头与Project版本，版本冲突时重新加载Project再要求用户确认；前端构建和5项事后测试通过。全仓`ts:check`仍被既有auto-import声明缺失阻断，目标Feature文件筛查无新增类型错误。

Task 6后置阻断：当前仓库没有实施地点权威主数据接口；基础平台部门API也不暴露可比较版本，无法在不臆造语义的前提下完成客户/办事处/实施地点稳定ID、版本及数据范围的全量服务端校验。该缺口不阻断版本化指派、前端非主数据部分及后续故障注入准备，但在接口补齐前Task 6和AC-FPROJ-007不得判定完成。

Task 6可授权部分已继续补齐：新增统一的`ProjectCreationAuthorizationService`，创建和指派应用入口均在幂等占用、审计、Outbox及业务写入前执行服务层功能权限校验，不再只依赖Controller注解。事后测试覆盖创建与指派的权限允许/拒绝及拒绝时零副作用；定向25项全部通过，项目模块非IT回归151项中148项通过、3项按`skipITs`跳过。该进展不替代办事处/实施地点/跨租户数据范围验收，AC-FPROJ-007继续保持未完成。

Task 9已完成：按授权停止旧`npdms-t8-mysql-1`后，使用当前工作树隔离的`npdms-50eb` Compose项目、MySQL 8.4.10与独立数据卷执行验证；旧容器及其数据卷未删除、未复用。Flyway 63个迁移校验通过，九个原子失败点和两个幂等并发场景共11项真实MySQL测试全部通过；全量后端Reactor 19个模块成功，项目模块154项测试全部通过。详细证据见`output/f-proj-001-v18/database-evidence.md`。

Task 10前置验证：全量后端Reactor 19个模块成功，项目模块154项测试全部通过，包含11项真实MySQL IT；仓库基线规则校验通过。前端计划命令`typecheck/build`与当前脚本不一致；实际`ts:check`仍被全仓既存错误阻断，本Feature相关路径无类型错误，`build:local`通过。验证中发现的子项目旧创建签名回归已由`697c384`修复并通过17项定向后端测试、相关ESLint与前端构建。按最新指示，历史规格目录不再参与实施校验或门禁判定。

Task 10真实浏览器验收已完成。唯一默认模板创建得到`920052 / PJT2026000008`，详情刷新后保持；真实MySQL落库为7个阶段、24个任务、8个里程碑、24个当前执行契约、17个ACC交付件、14个门禁和23个门禁引用，并存在同事务完成幂等、成功审计和`ProjectCreated` Outbox。人工选模项目`920055 / PJT2026000011`通过两个同优先级候选显式选择创建，随后验证了服务经理指派的必需Header、`If-Match`冲突重新加载和成功重试。

浏览器首次创建暴露出单租户运行关闭租户拦截器时任务执行契约遗漏`tenant_id`；已把Controller解析出的租户从应用服务显式传播到Project草稿和任务执行契约，并补充事后测试。修复后项目模块`154/154 PASS`，其中真实MySQL IT `11/11 PASS`。无模板和陈旧候选场景均保留页面内存输入、刷新即清空，数据库未产生失败Project。详细证据见`output/f-proj-001-v18/browser-acceptance.md`。

AC-FPROJ-001～006、008～010已有可复核证据并完成勾选；AC-FPROJ-007仍因实施地点权威主数据接口和可比较版本缺失保持未完成。该阻断已后置登记，不回退已完成任务，也不把Feature、UAT、发布或治理门禁标记为GO。历史规格及其中的旧计划引用仅保留追溯价值，不参与当前门禁。
