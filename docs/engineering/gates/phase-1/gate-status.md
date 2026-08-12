# SDS Phase 1 Review

> 审查状态：`IN_REVIEW`
> 依据：PRD V1.6、基线快照、正式工程链 V1.6
> 结论：`NOT_READY_FOR_PHASE_2`
> Phase 1硬门禁：领域Owner签署、实现工作包登记、采集平台集成形态确认，三项缺一不可；本轮已确认 Owner 映射和 V1 子应用集成，当前仅实现工作包登记仍阻塞。
> 独立第三方评审：`NO-GO`；详见 `docs/engineering/gates/phase-1/independent-review.md`。

## 1. 审查清单

| 检查项 | 结果 | 说明 |
|---|---|---|
| 115 项正式需求进入追溯 | PASS | 追溯矩阵 115/115，V1 57、V2 58；已补齐模块、聚合、状态机/工作流、权限、计划API、数据对象和测试类别工作映射 |
| V1/V2/V3/OUT_OF_SCOPE 边界 | PASS | V3 和排除项未进入当前实现设计 |
| Bounded Context | PASS | 已按 PRD-derived Owner 工作映射拆分，并由需求方确认；细化 Context 与平台能力边界已回写 |
| 聚合边界 | PASS-WITH-FOLLOWUP | 项目、设备、采集、割接、巡检已分离；需在 Phase 2 落实表级边界 |
| 跨模块 Repository 访问 | PASS | SDS 约束为应用服务/事件，不允许直接访问 |
| 核心生命周期状态机 | PASS-WITH-FOLLOWUP | 已定义核心状态和门禁；状态字典初始值与扩展映射需形成配置数据设计 |
| Workflow 与状态机分离 | PASS | 审批节点不直接替代业务状态 |
| 权限覆盖 | PASS-WITH-FOLLOWUP | 已覆盖功能、数据、操作、字段和设备临时授权；需 Phase 2 形成策略接口 |
| 外部系统 Owner | PASS | 外部系统只通过适配器/契约提供事实，平台不接管其内部业务 |
| 是否发明业务规则 | PASS-WITH-FOLLOWUP | 文档中的架构选择以【建议】或【待确认】标记，未写入 PRD 业务规则 |
| BLOCKED_BY_SPEC | ABSENT | 当前未发现需要回到 PRD/决策记录处理的业务语义冲突 |
| BLOCKED_BY_EVIDENCE | PRESENT | 实现工作包的仓库、提交和数据库环境仍未登记 |
| 是否足以进入 Data/API/Integration 设计 | NO | 需要先登记实现仓库、基础平台锁定提交和数据库目标环境 |

独立评审补充：Implementation Execution bounded context 可保留。命名后的 Context 整改已完成：新增 Device Access & Collection 作为正式采集 Context，并允许现有采集模块/子应用作为实现载体；SRV 内部拆为 Work Order & Time、Inspection、Service Operations；CUS/AST分别拆为 Customer & Relationship、Asset Management；COM保留必要主数据本地同步副本；Closure统一为 ProjectClosure。详见 `docs/engineering/gates/phase-1/context-refinement-review.md`。

本轮确认记录：Q1 已确认当前 13 个领域 Owner 映射；Q3 已确认 V1 优先采用现有采集平台子应用集成。Q2 仅完成实现工作包建议，实际仓库、提交号和数据库环境仍待登记。

## 2. 阻塞项

| 编号 | 当前状态 | 阻塞内容 | 影响 | 解除条件 |
|---|---|---|---|---|
| BLOCKED-SDS-01 | RESOLVED_BY_REQUESTER | 13 个领域 Owner 已形成 PRD-derived 工作映射，并由需求方确认 | 影响模块归属、数据 Owner、API 责任和权限边界 | 将确认来源纳入责任人名册或签署记录 |
| BLOCKED-SDS-02 | OPEN | 实现工作包尚未登记实现仓库、基础平台锁定提交和数据库目标环境 | 影响 Phase 2 技术契约与迁移设计 | 登记 `implementationRepo`、`platformCommit`、`databaseTarget`、`baseCommit` 和可复现构建入口 |
| BLOCKED-SDS-03 | RESOLVED_BY_REQUESTER | V1 优先采用现有采集平台子应用集成；任务授权、执行身份和回调责任已按 PRD/SDS 确认 | 影响部署、鉴权、任务下发和回调边界 | Phase 2 登记具体网络端点、部署清单和接口契约 |

## 2.1 Q2 实现工作包建议

| 字段 | 建议值 | 当前登记状态 |
|---|---|---|
| `implementationRepo` | `E:\AICoding\Projects\NPDMS` | 已登记；路径存在，但当前仓库尚无有效 Git 提交 |
| `branch/worktree` | `master`（当前目录） | 已核验；尚未形成可锁定的实现基线 |
| `baseCommit` | 实现仓库导入基础骨架后形成的锁定起点提交 | 【待登记】 |
| `platformCommit` | yudao master-jdk25 对应的实际 SHA，不使用浮动分支名 | 【待登记】 |
| `databaseTarget` | 独立 PMS 业务数据库和环境标识；不写入旧库 | 【建议】 |
| `buildEntry` | 后端 Maven、前端 pnpm、仓库测试脚本的可复现命令集合 | 【建议】 |
| `releaseId` | 与构建、迁移、测试和发布证据关联的唯一批次号 | 【建议】 |

Q2 路径核验记录：`E:\AICoding\Projects\NPDMS` 当前可访问，但 `HEAD` 不是有效提交，工作树文件均未纳入提交。因此本次仅完成 `implementationRepo` 和 `branch/worktree` 登记；`baseCommit`、平台锁定提交、数据库目标和构建入口仍需基于该仓库的有效提交重新核验，`BLOCKED-SDS-02` 保持 `OPEN`。

## 3. 风险

- 直接沿用旧领域规格会把历史边界、旧编号或过时流程带入新设计。
- 在未锁定实现仓库前生成数据库或 API 细节，会形成不可验证的伪契约。
- 项目、任务无限层级和设备单时点归属必须在 Phase 2 设计索引、约束和并发策略，否则查询和统计可能退化。

## 4. 阶段结论

Phase 1 的 Owner 映射和 V1 子应用集成已确认，但 `BLOCKED-SDS-02` 尚未关闭；当前不得标记 `APPROVED`，不得进入 Phase 2 数据/API/集成详细设计，也不得开始编码。只有实现工作包完成登记、无 `BLOCKED_BY_SPEC`/`BLOCKED_BY_EVIDENCE`，并重新完成本审查后，才可将状态改为 `APPROVED`。
