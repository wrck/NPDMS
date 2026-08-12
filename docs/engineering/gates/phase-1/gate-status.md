# SDS Phase 1 Review

> 审查状态：`IN_REVIEW`
> 依据：PRD V1.6、基线快照、正式工程链 V1.6
> 结论：`NOT_READY_FOR_PHASE_2`
> Phase 1硬门禁：领域Owner签署、实现工作包登记、采集平台集成形态确认，三项缺一不可；三项现均已完成，等待最终独立复审确认。
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
| BLOCKED_BY_EVIDENCE | ABSENT | 实现仓库、锁定提交、平台来源、数据库目标、统一证据批次号及前端可复现构建均已登记并核验 |
| 是否足以进入 Data/API/Integration 设计 | NO | 三项硬门禁已闭合，但仍需完成最终独立复审并将 Phase 1 状态改为 `APPROVED` |

独立评审补充：Implementation Execution bounded context 可保留。命名后的 Context 整改已完成：新增 Device Access & Collection 作为正式采集 Context，并允许现有采集模块/子应用作为实现载体；SRV 内部拆为 Work Order & Time、Inspection、Service Operations；CUS/AST分别拆为 Customer & Relationship、Asset Management；COM保留必要主数据本地同步副本；Closure统一为 ProjectClosure。详见 `docs/engineering/gates/phase-1/context-refinement-review.md`。

本轮确认记录：Q1 已确认当前 13 个领域 Owner 映射；Q3 已确认 V1 优先采用现有采集平台子应用集成。Q2 已登记实现仓库、锁定基线提交、基础平台来源和 NPDMS 开发数据库目标；需求方已确认统一证据批次号 `NPDMS-SDS-P1-20260812-01`，前端冻结安装与生产构建已在宿主机通过，构建配置已提交。

## 2. 阻塞项

| 编号 | 当前状态 | 阻塞内容 | 影响 | 解除条件 |
|---|---|---|---|---|
| BLOCKED-SDS-01 | RESOLVED_BY_REQUESTER | 13 个领域 Owner 已形成 PRD-derived 工作映射，并由需求方确认 | 影响模块归属、数据 Owner、API 责任和权限边界 | 将确认来源纳入责任人名册或签署记录 |
| BLOCKED-SDS-02 | RESOLVED_BY_EVIDENCE_AND_REQUESTER | 实现工作包、锁定提交、数据库目标、可复现构建入口及 `releaseId` 均已登记并核验 | 影响 Phase 2 技术契约与构建基线复核 | 已解除；最终独立复审需复核本节证据 |
| BLOCKED-SDS-03 | RESOLVED_BY_REQUESTER | V1 优先采用现有采集平台子应用集成；任务授权、执行身份和回调责任已按 PRD/SDS 确认 | 影响部署、鉴权、任务下发和回调边界 | Phase 2 登记具体网络端点、部署清单和接口契约 |

## 2.1 Q2 实现工作包建议

| 字段 | 建议值 | 当前登记状态 |
|---|---|---|
| `implementationRepo` | `E:\AICoding\Projects\NPDMS` | 已登记并核验 |
| `branch/worktree` | `master`（基线分支，当前目录） | 已核验；Phase 2 实现切片应从该基线创建独立短期分支/工作树 |
| `baseCommit` | `3c54ee1bb3c1d2fa4bad958ea6691956a7ac2464` | 已登记；包含根基线 `1a93fad14aa0cadcf9300535aa7b9b6617b9c3aa` 及 pnpm 9 宿主机构建配置，核验时工作树干净 |
| `platformCommit` | mini `e6d814cb59cfc204f02aa2516799073382aba801`；BPM 补充来源 `a6558325b0f09017f531f1e5891613ef9b468132`；前端来源 `2d028c8f7a14dd2e532ac1a76d1fdf58840dc621` | 已按实现仓库 `docs/upstream-sources.md` 登记；基础平台主来源为 yudao-boot-mini `master-jdk25` 锁定提交 |
| `databaseTarget` | `NPDMS-DEV-LOCAL` / `mysql:8.4` / 数据库 `npdms` / Compose 项目 `npdms` | 已按 `compose.yaml` 与 `.env.example` 核验；凭据仅通过 `NPDMS_*` 环境变量注入，不写入旧库 |
| `buildEntry` | 后端：JDK 25，`mvn clean verify -B`；前端宿主机：Node 24.11.1、pnpm 9.15.5，`corepack pnpm install --frozen-lockfile --prefer-offline && corepack pnpm build:prod`；基础设施：`.\tests\infrastructure\verify-docker-baseline.ps1` | 后端 30/30 Reactor 模块、基础设施静态基线校验、前端冻结安装和生产构建均已通过；前端 `corepack pnpm ts:check` 暴露首次基线既有类型债务，需在进入前端功能实现前单独治理，不作为 Q2 仓库登记阻塞 |
| `releaseId` | `NPDMS-SDS-P1-20260812-01` | `RESOLVED_BY_REQUESTER`；作为本轮构建、迁移、测试和门禁证据的统一批次号 |

Q2 核验记录：`E:\AICoding\Projects\NPDMS` 当前可访问，锁定基线 `HEAD` 为 `3c54ee1bb3c1d2fa4bad958ea6691956a7ac2464`，核验时工作树干净且未配置远端。实现基线统一使用 `NPDMS_*` 环境变量、`npdms` 数据库、`npdms` Compose 项目名和 `npdms-dev` 本地 Profile；本地 `.env` 与本地 Profile 均由 `.gitignore` 排除。前端 `pnpm-lock.yaml` 已存在于首次基线；本轮在宿主机补充 `packageManager=pnpm@9.15.5` 和单包工作区声明后，冻结安装、离线复核和生产构建通过，共享依赖 Store 为 `E:\.pnpm-store\v3`，项目本地仅保留被 Git 忽略的 `node_modules` 链接结构与 `dist-prod` 构建输出。`BLOCKED-SDS-02` 已解除。

前端质量跟进：`corepack pnpm ts:check` 当前失败，错误涉及首次基线已有的 PMS 页面类型契约、组件导入、字典常量和未使用变量等；生产构建成功不等于类型检查通过。该问题应建立独立质量治理任务，在进入前端功能实现前分批修复，不得通过关闭类型检查或放宽 TypeScript 规则规避。

## 3. 风险

- 直接沿用旧领域规格会把历史边界、旧编号或过时流程带入新设计。
- 在未锁定实现仓库前生成数据库或 API 细节，会形成不可验证的伪契约。
- 项目、任务无限层级和设备单时点归属必须在 Phase 2 设计索引、约束和并发策略，否则查询和统计可能退化。

## 4. 阶段结论

Phase 1 的 Owner 映射、V1 子应用集成、Q2 实现基线和统一证据批次号均已登记，三项硬门禁已闭合，当前不存在 `BLOCKED_BY_SPEC` 或 `BLOCKED_BY_EVIDENCE`。由于上一轮独立第三方结论为 `NO-GO`，本文件仍保持 `IN_REVIEW / NOT_READY_FOR_PHASE_2`；完成最终独立复审并确认无新增阻塞后，方可将状态改为 `APPROVED`。
