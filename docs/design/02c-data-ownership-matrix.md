# SDS Phase 1：数据 Owner 矩阵

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8机器差量校验已完成，待fresh-context独立复审
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 数据事实 | Owner | 其他领域使用方式 |
|---|---|---|
| 项目、阶段、ProjectTask树、TASK_NATIVE通用任务事实、任务工作绑定和完成判定快照 | Project Delivery | TASK_NATIVE由ProjectTask自身承载；其他绑定业务对象仍由对应Context拥有，项目工作台通过查询/API装载，不复制业务正文 |
| 到货、安装、实施结果解释、实施风险、质量检查、实施证据语义 | Implementation Execution | 通过查询、快照或事件引用；IMP-02安全检查不属于当前V1/V2 |
| 设备身份、档案、客户/项目归属、安装位置、RMA/维保基本信息、ConfigurationLog原始文件、不可变解析版本和设备关联 | AST / Asset Management | IMP发布采集业务结果和来源引用；AST/EQP-02接收后统一管理原始文件、解析版本与关联；MES、ITR、备件来源字段只读 |
| 凭证、授权、采集任务、外部执行状态、原始结果引用、回调证据 | Device Access & Collection | 实施、割接、巡检通过任务契约使用；现有采集模块或子应用作为实现载体 |
| CUT-01核心任务、问卷评估、P3调研清单及采集结果业务解释、方案、审批和P6闭环记录 | CUT | DAC只拥有CollectionTask技术执行与结果引用；项目只读任务进度和成功闭环结果；WO-06工单语义不进入CUT |
| 巡检任务、规则、报告和问题 | Inspection（SRV） | 通过设备和 Device Access & Collection 任务契约使用 |
| 设备服务状态 | Service Operations（SRV） | 消费设备、客户和项目闭环事实；ACC-05持续服务跟踪仅为V3候选 |
| 交付件齐套、验收、归档、闭环和交维 | ACC | 读取实施证据，不覆盖原始事实 |
| 满意度任务、问卷版本、客户答案、签字和评分判定 | ACC | CLO-01和SUB-03只引用有效判定，不修改客户事实 |
| ERP合同、订单行及项目交付范围分配 | Contract & Fulfillment（COM） | 合同和订单必要主数据同步到本地；ERP为权威来源，平台维护范围分配事实；不建立COM-02履约回写/对账聚合 |

`CollectionTask` 不属于 Implementation Execution；实施执行域只拥有业务结果解释和证据关联。日常业务查询优先读取本地同步副本；同步失败时展示最近成功版本、截止时间和同步状态。

历史工单、工时、附件、审批和明确要求留存的操作由迁移资料库只读持有，保留来源键和原始状态，不属于当前业务Context的数据Owner范围。
