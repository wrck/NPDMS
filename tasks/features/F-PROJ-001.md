# F-PROJ-001 手动创建项目与模板初始化实施任务

## 锁定输入

- Requirement IDs：`PM-01`、`PM-03`
- Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
- Feature Spec SHA-256：`86f175e05eb578cc35bae9b64715955b123bce584836c328be87b64c929e9431`
- 规格源提交：`9087469316ec5ba321b34f09fc601d98c30a3d2b`
- 源技术计划：`docs/superpowers/plans/2026-08-21-f-proj-001-manual-project-creation-and-template-initialization.md`
- 实现基线：`fd3978bad2955263a653900d04ab39b09cc05abf`
- 实现分支：`codex/f-proj-001-atomic-alignment`

`tasks/plan.md`、`tasks/todo.md`及其他旧任务记录仅为历史资料，不能授权或改变本 Feature 的实施范围。

## Start Gate

- [x] 从允许的本地提交创建独立干净 worktree，未修改原脏工作区。
- [x] 未使用被排除的工程链分支或其文件内容。
- [x] 规格源输入已形成本地提交。
- [x] V1.8、PRD 增量、ADR-0029/0030/0032 与 Feature Spec 已进入只读快照。
- [ ] Task 0 最小核心前向割接完成，正式写模型在全新 `npms` 数据库验证通过。
- [ ] 旧 `pms_project*` 正式写路径扫描为零命中。
- [ ] Java 以 `-Xlint:deprecation` 编译且无废弃引用警告。

## 实施任务

- [ ] Task 0：建立允许的本地基线，盘点并优先复用活跃实现，完成最小核心割接（Schema与命名空间割接已完成；创建入口切换待后续步骤）。
- [x] Task 1：锁定 V1.8 Feature 规格快照并登记当前任务。
- [x] Task 2：增加 Feature 前向数据库契约（`f153493`，V60 已在独立 `npms` 空库迁移并复跑通过）。
- [x] Task 3：实现 PLT 事务支持（编码、幂等、审计、Outbox 的强制事务与回滚验证已完成）。
- [ ] Task 4：实现已发布模板候选与预览查询。
- [ ] Task 5：实现 ACC 强制事务边界。
- [ ] Task 6：实现手动创建项目的单事务编排。
- [ ] Task 7：发布业务 API 与服务端授权契约。
- [ ] Task 8：实现 V1 服务经理人工确认。
- [ ] Task 9：用原子创建流程替换旧创建对话框。
- [ ] Task 10：完成原子性、真实浏览器行为及需求追踪验证。

## 验收项

- [ ] AC-FPROJ-001
- [ ] AC-FPROJ-002
- [ ] AC-FPROJ-003
- [ ] AC-FPROJ-004
- [ ] AC-FPROJ-005
- [ ] AC-FPROJ-006
- [ ] AC-FPROJ-007
- [ ] AC-FPROJ-008
- [ ] AC-FPROJ-009
- [ ] AC-FPROJ-010

## 实施约束

- 项目、任务、执行契约与 ACC 交付件创建必须加入同一 MySQL/Spring 事务；全部成功才提交，任一失败全部回滚。
- 不持久化创建草稿或初始化中间状态，不以异步补建、Saga 或最终一致性降级替代同步原子创建。
- 先盘点活跃业务并复用、改造；禁止先删除再重建相同能力。
- 仅在替代入口和全部消费者切换后标记旧代码为 `@Deprecated(forRemoval = false, since = "F-PROJ-001")`，禁止继续引用，暂不删除文件。
- 文件一经标记废弃，后续不得读取正文；只允许路径级引用扫描与 Java 编译 lint。
- 只允许本地提交；禁止推送、UAT或发布。

## 活跃能力复用盘点

| 能力 | 当前载体 | 处置 |
|---|---|---|
| 项目编码、来源键及客户存在性校验 | `ProjectServiceImpl` | `REUSE_AND_ADAPT`：迁入正式创建命令及租户内编码规则 |
| 服务端创建权限 | `ProjectController`的`pms:project:create` | `REUSE_AS_IS`：保持服务端最终授权依据 |
| 启用模板查询与模板快照 | `ProjectTemplateServiceImpl`、`TemplateSnapshot` | `REUSE_AND_ADAPT`：升级为四维候选、revision和受控预览 |
| 阶段、任务、团队实例化 | `ProjectTemplateServiceImpl` | `REUSE_AND_ADAPT`：纳入单事务并补齐执行契约、ACC交付件 |
| 任意层级任务树 | `ProjectTaskServiceImpl` | `REUSE_AS_IS`：保留树构建、移动和环校验 |
| 旧通用创建及从模板创建入口 | `ProjectController`、`ProjectTemplateController` | `DEPRECATE_AFTER_CUTOVER`：替代入口和前端消费者切换后再标废弃，当前不得提前删除 |

本盘点未发现已经标记`@Deprecated`的项目Java文件；后续文件一经标记废弃，不再读取其正文。
