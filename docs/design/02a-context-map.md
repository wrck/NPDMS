# SDS Phase 1：Context Map

> 文档状态：`REVALIDATION_REQUIRED`（修订018受影响边界已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订018（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；既有独立复审GO仅属原批准范围，当前差量须按Gate重验证
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 正式 Context 名称

`CRM/ERP/MES/ITR → 集成适配层 / ACL → Project Delivery → Preparation & Solution → Implementation Execution → Acceptance & Closure`

实施执行域向下依赖 `Device Access & Collection` 提供的 `CollectionTask` 和凭证授权，不拥有设备连接或原始采集执行；割接与巡检分别通过受控业务契约复用该 Context。V1 由现有采集平台子应用承载该 Context 的执行能力，后续可演进为内部模块，不重复建设采集引擎。

## Context 关系

| 上游 | 下游 | 关系 | 允许内容 |
|---|---|---|---|
| Preparation & Solution | Implementation Execution | Customer/Supplier | 下发已批准方案、计划和设备范围引用 |
| Implementation Execution | Acceptance & Closure | Published Language | 发布实施证据、阶段质量检查快照和阶段完成事实 |
| Implementation Execution | 基础平台能力 | Customer/Supplier | 请求文件、待办、审计、字典和权限校验 |
| Project Delivery / Preparation & Solution / Implementation Execution / Acceptance & Closure / Cutover | 基础平台能力 | Customer/Supplier | 复用已发布动态表单模板修订、通用渲染和值载体；各业务Context继续拥有自身选模、完成、审批、版本和业务校验 |
| Implementation Execution | Device Access & Collection | Customer/Supplier | 以任务级授权下发采集请求，接收结果引用；不接管原始执行 |
| Implementation Execution | Cutover | Open Host Service | 提供割接上线门禁快照，不修改割接内部状态 |
| Cutover | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Inspection | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Cutover | Project Delivery | Published Language | 发布CUT-06成功闭环与归档结果引用；失败/回退事实可查询但不得表达S4完成 |
| Customer & Relationship | Project Delivery / Asset Management | Published Language | 提供客户关系和资产关系查询 |
| Asset Management | Service Operations | Published Language | 提供设备服务状态和设备档案查询 |

Context Map 只展示 bounded context 或外部系统，不把 `CollectionTask`、`DeliveryEvidence`、`CutoverTask` 或设备凭证当作 Context 节点。基础平台能力仅作为横向能力集合标注；共享动态表单只拥有模板修订和通用实例值，不拥有消费方业务完成、审批或领域版本事实。集成适配层不拥有外部系统或业务域事实。

修订018中，PROJ向ACC提供项目上下文并通过公开命令创建/关联项目级验收；ACC向PROJ/CLO提供版本化验收事实。COM仅在冻结验收规则明确依赖范围时提供权威范围，不因阶段或导航位置形成强制依赖。受管BPM事实经集成适配层可靠捕获后调用ACC命令，BPM、PLT和视图均不成为验收Owner；这些是现有Context间的协作，不新增Context或业务工作流。

V1.8当前不包含`Work Order & Time` Context。历史工单、工时、附件、审批和审计证据通过经批准的迁移契约只读保留，不暴露当前流转能力；ACC-05持续服务跟踪仅作为V3候选，不能回流为当前Context。

## 修订017差量契约

新增/澄清的方向是SOL/IMP/CUT提供有版本事实→PROJ统一阶段编排；CLO-02批准事务→PROJ分型终态Writer；COM范围版本→PROJ任务/绑定及ACC验收覆盖；HR人事/任职引用与LDAP目录身份→SYSTEM映射。不得让事件消费者、COM入口、BPM回调成为第二个项目阶段或终态Writer。契约字段见02d，来源Owner见02c。

对应PRD审查项、派生覆盖和验证结果见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。本文不能替代Feature物理合同重验证、独立复审或运行测试。
