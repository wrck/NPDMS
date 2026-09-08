# 项目实施交付管理平台 系统详细设计说明书（SDS 总册）

> 文档状态：`REVALIDATION_REQUIRED`
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8附录A.1的100项正式Requirement及附录A.1.1的111个目标版本切片
> Owner：SDS 总编
> 定位：本文件仅为SDS分册索引，不复制分册内容或批准状态；Phase 1/2/3按当前工程链作设计分类，不执行全量阶段准入。

## 1. 当前治理方式与历史记录

2026-09-08需求方进一步精简实施链，Phase 1/2/3均不作日常开工门禁，具体实施/验证遵循[工程链](../engineering/00-engineering-chain.md)。旧分册的状态/版本头部只说明其历史；只更新本次实际权威内容，不为普通任务补齐独立计划或阶段批准。原FAIL不改为PASS，真实业务/API/Schema及运行风险仍按影响处理。

| 阶段 | 审查状态 | 结论 | 门禁记录 |
|---|---|---|---|
| SDS Phase 1 | 设计分类 | 非阶段准入；按变更审查 | `docs/engineering/gates/phase-1/gate-status.md` |
| SDS Phase 2 | 设计分类 | 非阶段准入；按变更审查 | `docs/engineering/gates/phase-2/gate-status.md` |
| SDS Phase 3 | 设计分类 | 非阶段准入；真实运行风险按适用点验证 | `docs/engineering/gates/phase-3/gate-status.md` |

V1.7及修订007前三阶段历史审查证据保留。master修订011～013已批准的巡检差量及未受影响设计继续有效；修订017承接PR #1来源修订017及相应整改。受影响部分依据当前权威设计和实际证据判断，不以旧阶段批准或自审代替未完成的专项复核。

参考Schema只证明设计约束，不代表应用、存量升级、Feature或Release完成；本轮通用字段整改的验证结果在既有Gate中单独留痕，原修订017执行证据不覆盖。

## 2. 分册索引

| 分册 | 主题 |
| --- | --- |
| `01-requirement-traceability.md` | 需求追溯 |
| `02-domain-model.md` | 领域模型 |
| `02a-context-map.md` | 上下文映射 |
| `02b-aggregate-boundary-decisions.md` | 聚合边界决策 |
| `02c-data-ownership-matrix.md` | 数据所有权矩阵 |
| `02d-cross-context-contracts.md` | 跨上下文契约 |
| `02e-version-scope-matrix.md` | 版本范围矩阵 |
| `03-system-architecture.md` | 系统架构 |
| `04-module-design.md` | 模块设计 |
| `05-state-machine.md` | 状态机 |
| `06-workflow-design.md` | 工作流设计 |
| `07-authorization-design.md` | 权限设计 |
| `08-data-model.md` | 数据模型 |
| `08a-domain-entity-migration-alignment.md` | 领域实体迁移对齐（补充分册） |
| `09-database-design.md` | 数据库设计 |
| `10-api-design.md` | API 设计 |
| `11-event-design.md` | 事件设计 |
| `12-integration-design.md` | 集成设计 |
| `13-file-design.md` | 文件设计 |
| `14-security-design.md` | 安全设计 |
| `15-cache-and-concurrency.md` | 缓存与并发 |
| `16-exception-and-idempotency.md` | 异常与幂等 |
| `17-audit-and-observability.md` | 审计与可观测 |
| `18-deployment-design.md` | 部署设计 |
| `19-performance-design.md` | 性能设计 |
| `20-test-design.md` | 测试设计 |
| `phase-1-domain-ownership.md` | Phase 1 领域 Owner 签署 |

## 3. 基线边界

正式SDS提供已确认的实现契约，Feature Ready由其规格维护；范围明确且不依赖未决问题的Task可按工程链直接实施，不以独立Technical Plan或全阶段READY准入。本基线不授权以下事项：

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

分册实质修订先于依赖实现；本总册仅在新增/移除分册或索引关系变化时更新，不随各分册状态、PRD全文身份或日常措辞变化同步。

## 修订017文档与工程证据边界

已回写PRD及受影响SDS，并由同源生成器更新领域规格、Requirement切片覆盖和Phase 2映射。静态文档校验通过只证明本轮断言与投影一致，不证明独立设计审查、数据库、浏览器、真实集成或生产Gate通过。既有物理合同与Feature Ready按影响标记重验证，未影响Task历史Done不改写。见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。
