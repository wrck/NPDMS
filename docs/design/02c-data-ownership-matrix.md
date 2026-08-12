# SDS Phase 1：数据 Owner 矩阵

| 数据事实 | Owner | 其他领域使用方式 |
|---|---|---|
| 到货、安装、实施结果解释、实施风险、质量/安全检查、实施证据语义 | Implementation Execution | 通过查询、快照或事件引用 |
| 设备身份、档案、客户/项目归属、安装位置、RMA/维保基本信息、配置Log关联 | AST / Asset Management | 实施和服务只引用；MES、ITR、备件必要主数据同步到本地，来源字段只读 |
| 凭证、授权、采集任务、外部执行状态、原始结果引用、回调证据 | Device Access & Collection | 实施、割接、巡检通过任务契约使用；现有采集模块或子应用作为实现载体 |
| 割接任务、评估、方案、审批、执行 | CUT | 实施只提供上线门禁快照 |
| 工单、责任区间和工时 | Work Order & Time（SRV） | 项目读取工单与工时快照，不直接修改工单状态 |
| 巡检任务、规则、报告和问题 | Inspection（SRV） | 通过设备和 Device Access & Collection 任务契约使用 |
| 设备服务状态和持续服务跟踪 | Service Operations（SRV） | 消费设备、客户和项目闭环事实 |
| 交付件齐套、验收、归档、闭环和交维 | ACC | 读取实施证据，不覆盖原始事实 |
| 合同、订单行、交付范围和履约对账 | Contract & Fulfillment（COM） | 合同和订单必要主数据同步到本地；ERP为权威来源，平台维护范围分配和履约事实 |

`CollectionTask` 不属于 Implementation Execution；实施执行域只拥有业务结果解释和证据关联。日常业务查询优先读取本地同步副本；同步失败时展示最近成功版本、截止时间和同步状态。
