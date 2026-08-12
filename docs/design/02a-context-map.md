# SDS Phase 1：Context Map

## 正式 Context 名称

`Preparation & Solution → Implementation Execution → Acceptance & Closure`

实施执行域向下依赖平台公共能力提供的 `CollectionTask` 和凭证授权，不拥有设备连接或原始采集执行；割接与巡检分别通过受控业务契约复用采集能力。

## Context 关系

| 上游 | 下游 | 关系 | 允许内容 |
|---|---|---|---|
| Preparation & Solution | Implementation Execution | Customer/Supplier | 下发已批准方案、计划和设备范围引用 |
| Implementation Execution | Acceptance & Closure | Published Language | 发布实施证据、质量/安全检查快照和阶段完成事实 |
| Implementation Execution | Platform Governance | Customer/Supplier | 请求文件、待办、审计、字典和权限校验 |
| Implementation Execution | Device Access & Collection（PLT能力） | Anti-Corruption Layer | 以任务级授权下发采集请求，接收结果引用 |
| Implementation Execution | Cutover | Open Host Service | 提供割接上线门禁快照，不修改割接内部状态 |
| Cutover/Inspection | Device Access & Collection（PLT能力） | Customer/Supplier | 复用统一采集任务与回调契约 |

Context Map 只展示 bounded context，不把 `CollectionTask`、`CutoverTask` 或设备凭证当作 Context 节点。
