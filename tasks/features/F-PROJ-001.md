# F-PROJ-001 手动项目创建与模板初始化

> Feature 状态：`NOT_STARTED`
> 当前任务：`Task 1 已完成：V1.8 Feature 基线已锁定`
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> Feature Spec SHA-256：`86f175e05eb578cc35bae9b64715955b123bce584836c328be87b64c929e9431`
> Technical Plan（规格仓库）：`docs/superpowers/plans/2026-08-21-f-proj-001-manual-project-creation-and-template-initialization.md`
> 锁定规格提交：`9087469316ec5ba321b34f09fc601d98c30a3d2b`

## 任务授权与事实边界

本文件只以锁定的 Feature Spec 及其引用的 PRD V1.8、工程链、SDS 和 ADR 为实施输入。`tasks/plan.md` 与 `tasks/todo.md` 是历史材料，不能授权本 Feature 的设计、实现、验收或发布。

现有 V1.7 手动创建项目和模板基座仅是复用审计证据，状态为 `V1_7_REVALIDATION_REQUIRED`；不得据此将 PM-01、PM-03 或本 Feature 标记为已实现。

## Start Gate（Task 2 及后续实施前）

- [x] 规格快照的 `source.commit` 为锁定的 40 位提交，且基线校验通过。
- [x] Feature Spec 路径及 SHA-256 已锁定。
- [ ] 实施仓库存在可验证的后端、前端、Flyway 和测试工程，且工作树隔离、干净。
- [ ] 已审计 `proj_*` 现有实现与本 Feature 的差量；没有将 V1.7 复用证据误作实现结论。
- [ ] PROJ 与 ACC 可共享同一 MySQL 事务资源和 Spring 事务管理器；否则不得开始或发布。
- [ ] Task 2 所需的当前 Schema、模块边界、权限与 API 实施输入已按锁定规格核对。

## 后续任务标识

| 标识 | 状态 |
| --- | --- |
| `Task 2` | `NOT_STARTED` |
| `Task 3` | `NOT_STARTED` |
| `Task 4` | `NOT_STARTED` |
| `Task 5` | `NOT_STARTED` |
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

Task 1 只完成规格基线锁定和后续任务入口建立；不包含业务代码、Flyway、旧 Feature 改造、功能验收、UAT 或发布。
