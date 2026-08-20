# 项目实施交付管理平台 系统详细设计说明书（SDS 总册）

> 文档状态：`REVALIDATION_REQUIRED`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求
> Owner：SDS 总编
> 定位：本文件是 SDS 各分册的总册与索引，不复制分册内容；分册结论以其自身和对应门禁记录为准。

## 1. 基线结论

| 阶段 | 审查状态 | 结论 | 门禁记录 |
|---|---|---|---|
| SDS Phase 1 | `BASELINE` | `READY_FOR_PHASE_2_V1.8` | `docs/engineering/gates/phase-1/gate-status.md` |
| SDS Phase 2 | `BASELINE` | `READY_FOR_PHASE_3_V1.8` | `docs/engineering/gates/phase-2/gate-status.md` |
| SDS Phase 3 | `REVALIDATION_REQUIRED` | `NOT_READY_FOR_SDS_BASELINE_V1.8` | `docs/engineering/gates/phase-3/gate-status.md` |

V1.7三阶段历史审查证据保留。V1.8 Phase 1/2已完成差量复审；Phase 3完成前，本总册及状态为`IN_REVIEW`/`REVALIDATION_REQUIRED`/`DEFERRED_TO_PHASE_3`的Phase 3分册不得作为新增Feature或实现的当前放行依据。

## 2. 分册索引

| 分册 | 主题 | 状态 |
|---|---|---|
| `01-requirement-traceability.md` | 需求追溯 | `BASELINE` |
| `02-domain-model.md` | 领域模型 | `BASELINE` |
| `02a-context-map.md` | 上下文映射 | `BASELINE` |
| `02b-aggregate-boundary-decisions.md` | 聚合边界决策 | `BASELINE` |
| `02c-data-ownership-matrix.md` | 数据所有权矩阵 | `BASELINE` |
| `02d-cross-context-contracts.md` | 跨上下文契约 | `BASELINE` |
| `02e-version-scope-matrix.md` | 版本范围矩阵 | `BASELINE` |
| `03-system-architecture.md` | 系统架构 | `BASELINE` |
| `04-module-design.md` | 模块设计 | `BASELINE` |
| `05-state-machine.md` | 状态机 | `BASELINE` |
| `06-workflow-design.md` | 工作流设计 | `BASELINE` |
| `07-authorization-design.md` | 权限设计 | `BASELINE` |
| `08-data-model.md` | 数据模型 | `BASELINE` |
| `08a-domain-entity-migration-alignment.md` | 领域实体迁移对齐（补充分册） | `BASELINE` |
| `09-database-design.md` | 数据库设计 | `BASELINE` |
| `10-api-design.md` | API 设计 | `BASELINE` |
| `11-event-design.md` | 事件设计 | `BASELINE` |
| `12-integration-design.md` | 集成设计 | `BASELINE` |
| `13-file-design.md` | 文件设计 | `BASELINE` |
| `14-security-design.md` | 安全设计 | `BASELINE` |
| `15-cache-and-concurrency.md` | 缓存与并发 | `BASELINE` |
| `16-exception-and-idempotency.md` | 异常与幂等 | `BASELINE` |
| `17-audit-and-observability.md` | 审计与可观测 | `BASELINE` |
| `18-deployment-design.md` | 部署设计 | `BASELINE` |
| `19-performance-design.md` | 性能设计 | `BASELINE` |
| `20-test-design.md` | 测试设计 | `BASELINE` |
| `phase-1-domain-ownership.md` | Phase 1 领域 Owner 签署 | `OWNER_SIGNED` |

## 3. 基线边界

本基线授权 Feature Spec 与实现使用上述设计契约，不授权以下事项：

1. 宣称可部署、专项验收通过或生产发布；生产证据按部署/发布门禁登记。
2. 执行历史数据迁移或数据切换；只有发布包含该范围时，`AI-MIG-000`才是Release前置门禁，且须在真实批次验证通过后的批准窗口内执行；普通功能发布不受此门禁阻断。
3. 恢复任何已被运行时退役排除的入口；退役处置见实现基线清单。

## 4. 下游证据门禁

Phase 3 证据项按“返工收益+最晚安全点”归属下游门禁，SDS 基线不阻断：

| 证据项 | 状态 | 实际阻断点 |
|---|---|---|
| P3-E01 运行事实 | `DOWNSTREAM-GATED` | 生产部署、生产发布 |
| P3-E02 数据HA | `DOWNSTREAM-GATED` | 生产部署、性能验收、生产发布 |
| P3-E03 恢复目标 | `DOWNSTREAM-GATED` | 恢复验收、生产发布 |
| P3-E04 设备凭证 | `DOWNSTREAM-GATED` | 设备凭证能力、生产发布 |
| P3-E05 可观测 | `DOWNSTREAM-GATED` | 可观测验收、高风险审计生产验收、生产发布 |
| P3-E06 性能环境 | `DOWNSTREAM-GATED` | 性能验收、生产发布 |
| P3-E07 联调 | `DOWNSTREAM-GATED` | 对应Feature联调、发布 |
| P3-E08 前端类型 | `DOWNSTREAM-GATED` | 前端Feature验收、发布 |
| P3-E09 模型基线 | `MODEL_BASELINE_READY` | 正式独立复审已GO；历史数据迁移实施、数据切换按Release范围另行门禁 |

## 5. 修订规则

分册修订必须先进入本仓库正式变更与提交，再由实现仓库锁定新提交重新同步；本总册只随分册状态或阶段结论变化而修订。
