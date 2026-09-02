# F-CUT-008 P5提前时间判断与外部提醒 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / REVIEW_REQUIRED`
> Requirement：`CUT-05（V2/P0）`
> Requirement切片覆盖：`CUT-05@V2=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-005（P5分级审批）`
> 支撑依赖：`INT-10短信/邮件发送契约`、`INT-05钉钉通知契约`
> 机器合同：`specs/features/F-CUT-008-api-contract.json`、`specs/features/F-CUT-008-physical-contract.json`、`specs/features/F-CUT-008-external-notification-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-008-legacy-reuse-audit.md`

## 1. 业务目标

在现有P5分级审批正向闭环上，为A/B级审批实例冻结并展示专项提前时间判断；在首节点、下一节点和改派节点激活后继续保留站内消息，同时形成短信、邮件和钉钉提醒请求。提前时间只提供审批参考，外部提醒失败不回滚、不延迟也不改变审批事实。

## 2. Scope

### 2.1 包含

- 十类正式割接类型与1/2/3/5/7自然日阈值的封闭规则版本；
- A/B级在P4提交并创建P5实例时，以方案提交时间和计划操作时间计算、冻结提前时间判断；
- P5完整审批页展示规则版本、要求天数、实际自然日差和“是否未按规定提前提交”；
- 首节点、下一节点和改派节点激活时，在既有站内通知之外追加短信、邮件、钉钉三类外部提醒请求；
- 外部提醒端口、渠道级幂等、受理/明确失败/结果未知、重试及审计；
- CUT单元、集成、真实MySQL和组件测试中以受控端口实现完成A/B正常审批提醒闭环。

### 2.2 不包含

- 修改A/B/C/D审批路由、五项评审、驳回/通过状态机或P4/P6职责；
- 对C/D级计算、展示或持久化提前时间判断；
- 把判断结果解释为SLA、准入门禁、自动驳回或审批决定；
- 实现INT-10、INT-05或任何短信、邮件、钉钉第三方Provider；
- 通用通知平台、Yudao基础模块、手机号/邮箱/钉钉账号主数据；
- CUT-03@V2导出/流程跳转、CUT-08备件系统集成及任何V3能力。

## 3. 业务规则

### BR-FCUT008-001 封闭规则与自然日计算

- 规则版本固定为`CUT_LEAD_TIME_R034_V1`，映射为：`DEVICE_REPLACE_WHOLE=5`、`DEVICE_REPLACE_BOARD=3`、`DEVICE_REPLACE_VENDOR=7`、`DEVICE_ONBOARD=7`、`VERSION_UPGRADE=2`、`DISASTER_RECOVERY_DRILL=2`、`CONFIGURATION_CHANGE=2`、`NETWORK_TOPOLOGY_CHANGE=3`、`VERSION_PATCH=2`、`SIGNATURE_UPGRADE=1`。
- A/B级在`CutoverApprovalFactApi.start`事务内，从已锁定任务取得`cutoverType/scheduledTime`，从本次已提交不可变方案revision取得`submittedAt`。两者按平台业务时区`Asia/Shanghai`转换为业务日期，`actualNaturalDays=scheduledDate.toEpochDay-planSubmittedDate.toEpochDay`。
- `actualNaturalDays >= requiredDays`时`lateSubmission=false`；否则为`true`。负值仍按公式保存并判定为迟交，不修改计划操作时间或阻止P5创建。
- 十类之外的A/B割接类型视为Owner数据损坏，P4提交整体失败；不得补默认阈值。C/D不读取规则、不计算，物理快照保持空。
- 冻结快照精确包含`ruleVersion/timezoneId/cutoverType/scheduledTime/planSubmittedAt/requiredDays/actualNaturalDays/lateSubmission`，创建后不可覆盖；方案驳回后新revision创建的新审批实例重新计算，旧实例不变。

### BR-FCUT008-002 审批展示与决定隔离

- 现有审批详情的`FULL`投影增加`leadTimeCompliance`：A/B为非空冻结快照，C/D及旧V1审批实例为JSON null；`FINAL_RESULT_ONLY`和`REASSIGNMENT_ONLY`不暴露该字段。
- 提前时间判断不进入五项评审、`allowedActions`、通过/驳回守卫、审批路由或任务状态CAS。审批人可以参考该字段，但不得由系统自动生成评审结论或反馈意见。
- 查询只读取`cut_approval_instance.lead_time_snapshot`，不得按当前时间、当前任务或当前方案重新计算。

### BR-FCUT008-003 外部提醒请求

- F-CUT-005原`IN_PLATFORM`通知保持每个激活节点必建且语义不变。V2对同一激活节点另外追加`SMS`、`EMAIL`、`DINGTALK`各一条CUT渠道记录；外部渠道不可用不影响站内待办和站内通知。
- 外部渠道记录与节点激活/改派在同一业务事务中追加为`PENDING`；不得在审批写事务内调用第三方Provider。唯一键为`CUT_APPROVAL_EXT:{approvalInstanceId}:{nodeNo}:{nodeVersion}:{channel}`。
- 发送端口只接收受信租户、recipientUserId、渠道、模板、deliveryKey、审批任务链接、correlationId和必要非敏感变量。手机号、邮箱和钉钉账号由对应外部Owner解析，CUT不接收、不持久化、不直读其表。
- Provider明确受理后记录`ACCEPTED`及providerReferenceId；明确失败记录`PENDING_RETRY`和退避时间；调用结果不确定记录`DELIVERY_UNKNOWN`且不自动重复发送。上述状态都不改变审批、节点、待办或站内消息。
- `PENDING/PENDING_RETRY`以同一deliveryKey重试；Provider未形成时生产外部投递Job保持暂停，不注册Fake或fallback。受控替身仅存在于测试装配。

### BR-FCUT008-004 幂等、并发与审计

- 同一节点版本和渠道只允许一条外部提醒记录；审批命令重放不得追加第二条。节点改派产生新nodeVersion和新deliveryKey，旧记录保留。
- 渠道投递领取按`tenantId + dueAt + id`稳定排序并使用`FOR UPDATE SKIP LOCKED`；状态/version CAS与尝试结果同事务，重复Job不得重复受理同一条记录。
- 审计保存审批实例/节点/规则版本、提前时间输入与结果、渠道、recipientUserId、deliveryKey、providerReferenceId、尝试时间、结果、重试次数、错误码及correlationId；不保存完整业务正文或联系方式。

## 4. API与集成合同

- 用户REST不新增写命令。`GET /api/v1/pms/cutover-tasks/{taskId}/approval`的`FULL`投影按`F-CUT-008-api-contract.json`增加`leadTimeCompliance`，其他F-CUT-005路由、Header、权限和错误语义不变。
- 外部提醒端口见`F-CUT-008-external-notification-contract.json`。`SMS/EMAIL`由INT-10物理Owner实现，`DINGTALK`由INT-05物理Owner实现；本Feature只拥有CUT请求、渠道记录、调度和消费端口。
- 生产Provider缺失不允许空成功、伪providerReferenceId或同步改成站内消息；站内消息本来就是独立且必保留的V1降级路径。

## 5. 数据与迁移

- `cut_approval_instance`前向增加`lead_time_enabled BIT(1) NOT NULL`与`lead_time_snapshot JSON NULL`。既有行确定性标记`lead_time_enabled=0`且快照为空；迁移完成后移除默认值。新A/B实例为1且快照非空，新C/D实例为0且快照为空。
- `cut_approval_notification`前向增加`channel_code`、`provider_reference_id`和`last_attempt_at`，扩展渠道/状态约束。既有记录确定性回填`channel_code=IN_PLATFORM`，不改deliveryKey、messageId、状态或历史时间；外部记录不得填写`message_id`。
- 不新增业务表，不迁移或双写`pms_cut_task/pms_cut_plan`，不修改已经执行的迁移；Flyway编号只在实际串行合入时确定。

## 6. 验收标准

- AC-FCUT008-001：十类阈值逐项匹配；A/B在`差值=阈值-1/阈值/阈值+1`分别得到`true/false/false`，规则版本和输入快照不可变。
- AC-FCUT008-002：C/D和旧V1实例不计算且详情返回null；提前时间结果任意取值都不改变审批动作、五项评审、状态或P6推进。
- AC-FCUT008-003：节点激活原子形成一个站内通知和SMS/EMAIL/DINGTALK三条外部请求；业务命令重放不重复，改派只为新节点版本形成新请求。
- AC-FCUT008-004：三类外部渠道在受控替身下可受理并留providerReferenceId；明确失败可按同键重试，未知结果不自动重发，任一路径都不回滚审批。
- AC-FCUT008-005：P5页面对A/B展示冻结判断，对C/D不展示；组件正向交互不依赖真实第三方账号或网络。
- AC-FCUT008-006：真实MySQL证明快照联合约束、旧行前向兼容、渠道唯一性、并发领取单胜及审批事实前后不变。

## 7. Feature Ready Gate

当前：`DRAFT / NOT_READY / REVIEW_REQUIRED / NOT_STARTED`。最近Gate为Feature Ready独立复审；通过前不得生成Technical Plan或实施。
