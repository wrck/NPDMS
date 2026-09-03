# F-CUT-008 旧实现与现有实现复用审计

> Requirement：`CUT-05@V2`
> 结论状态：`BASELINE / GO @ d9b43077`

| 资产 | 当前事实 | 判定 | F-CUT-008边界 |
|---|---|---|---|
| `F-CUT-005`审批实例、节点、命令和状态机 | 已实现A/B/C/D串行审批、P4/P6迁移和受控正向闭环 | `REUSE_AND_ENHANCE` | 只在审批创建时追加冻结判断、在节点通知创建时追加外部渠道记录；不改路由、评审或状态迁移 |
| `CutoverApprovalSourceSnapshotCodec` | 冻结任务、清单、评估和方案内容，V1精确结构不可随意扩写 | `REUSE_WITH_SEPARATE_EXTENSION` | 提前时间使用独立`lead_time_snapshot`，不改写V1来源快照或历史实例 |
| `cut_approval_notification`与站内投递 | 已拥有站内消息PENDING/SENT/PENDING_RETRY及同键重试 | `REUSE_AND_ENHANCE_SCHEMA` | 既有记录回填IN_PLATFORM且原事实不变；外部渠道复用行级调度模型，不把站内成功解释为外部成功 |
| `CutoverApprovalPanel.vue` | 已展示完整冻结审批页和五项评审 | `COPY_COMPONENT_THEN_INTEGRATE` | 新增独立提前时间卡片组件并由面板组合；不复制或改义审批表单 |
| 旧`/pms/cut-task`页面与`pms_cut_task/pms_cut_plan`审批字段 | 旧CRUD/单字段意见，不具备冻结规则版本或渠道记录 | `NOT_REUSABLE` | 保持原页面、接口和数据不变；不迁移、不双写、不升级为V2事实 |
| `NotifyMessageSendApi` | 站内消息Owner接口 | `DIRECT_REUSE_FOR_IN_PLATFORM_ONLY` | 继续承担站内通知，不用于伪造短信/邮件/钉钉发送结果 |
| INT-10/INT-05 | 当前工作树没有可依赖的生产发送Provider | `RESERVED_PORT_ONLY` | CUT定义最窄消费端口与受控测试实现；不修改Yudao或第三方模块，不注册生产Fake/fallback |
| 附件/参考表 | 可能提供界面名称或样式参考 | `REFERENCE_ONLY` | 不参与阈值、渠道、数量、完成或阻断裁决；正式十类阈值仅取PRD |

## 审计结论

本Feature是现有P5完整审批的V2纵向增强，不重建第二套审批或通知模块。最小实现应新增纯规则计算器、独立快照Codec、外部通知端口/投递服务和独立UI卡片，仅在现有审批创建与节点激活事务中接入；所有V1实例、旧页面、旧表与站内通知语义保持不变。
