# F-CUT-006 P6割接跟踪与闭环 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / REVIEW_REQUIRED`
> Requirement：`CUT-06（V1/P0）`
> Requirement切片覆盖：`CUT-06@V1=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-002`、`F-CUT-004`、`F-CUT-005`
> 支撑依赖：`ProjectScopeApi`、`FileArtifactApi`、`INT-12设备连接与采集公开契约`、平台幂等/审计/Outbox
> 机器合同：`specs/features/F-CUT-006-api-contract.json`、`specs/features/F-CUT-006-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-006-legacy-reuse-audit.md`

## 1. 业务目标

割接任务经P5全部审批通过进入P6后，由该任务的一线工程师引用已批准方案、设备范围和采集证据，统一记录割接前检查、执行、测试、回退、附件、遗留项及最终成功/失败结果。提交即形成不可变归档事实并结束本次割接流程；P6不建设逐步骤执行引擎、稳定观察期或遗留项生命周期。

## 2. Scope

### 2.1 包含

- 一任务一个P6闭环根，草稿可保存，提交后不可覆盖；
- 已批准方案、审批实例、任务设备范围和P6进入水位的冻结引用；
- 割接前、执行、测试是否正常及说明，是否回退、回退结果及原因，最终成功/失败；
- 割接后信息采集清单、实施承诺书和其他授权附件的不可变PLT文件事实引用；
- INT-12采集下发、失败、回调和人工替代证据在CUT侧的引用投影；原失败事实不得被人工结果覆盖；
- P6提交原子写闭环、任务归档、设备活动标记释放、阶段历史、平台审计和必要Outbox；仅最终成功写`CutoverCompleted`；
- 详情、保存、提交、采集请求和人工结果关联的REST/内部合同、权限、幂等、版本CAS与响应式工作台；
- CUT单元/集成及受控MySQL正向闭环可使用`src/test` ProjectScope/PLT/INT-12确定性替身。

### 2.2 不包含

- 逐方案步骤的开始、完成、失败或回退状态，`cut_execution_step`、稳定观察窗口或`cut_observation`；
- 遗留项独立任务、负责人、期限、工单或归档门禁；
- INT-12凭证库、连接器、命令执行引擎、采集原始数据Owner或生产Provider；
- INT-02@V2 ITR结果回传/失败建单、外部HTTP适配器或客户资产Owner实现；
- 多角色分工填写、V3现场编排或通用工单；
- `Q-FCUT004-001`尚未裁决的P6职责变化回P4路径；该分支保持`BLOCKED_BY_SPEC`且不阻断不发生职责变化的正常闭环；
- 修改旧`pms_cut_*` Service、Controller、页面、权限和运行数据。

## 3. 业务规则

### BR-FCUT006-001 P6准入与来源冻结

- 只有`NEW_PLATFORM`任务处于`P6/CLOSURE_IN_PROGRESS`、存在同任务已批准F-CUT-005实例及其不可变已提交方案revision时，才可创建或保存闭环草稿。
- 操作者必须是任务`ownerUserId`，同时具备`pms:cutover-task:save-closure`及当前项目`ProjectScopeApi.ACTION_EDIT`。详情只要求查询权限和`ACTION_VIEW`；写命令必须在事务内重新锁定资格。
- 首次草稿冻结`taskId/projectId/taskVersionAtP6/approvalInstanceId/approvalVersion/planRevisionId/planRevisionNo/planVersion/deviceScopeWatermark`。后续保存不得静默刷新这些来源；来源身份或水位不一致失败关闭。
- P4步骤只作为已批准方案引用展示，不复制到闭环表，不产生逐步骤动作。

### BR-FCUT006-002 闭环字段与附件

- `preCheckNormal/executionNormal/testNormal`均为必填布尔值，各自说明为0..4000字符；值为`false`时对应说明必填。
- `rollbackOccurred`必填；未发生回退时`rollbackSuccessful/rollbackReason`均为空；发生回退时二者必填，原因1..4000字符。
- `legacyItems`为0..4000字符快照文本，可空，不产生独立生命周期或阻断提交。
- `finalResult`只能`SUCCESS/FAILED`，由一线工程师明确提交，平台不得从通知、采集HTTP结果或附件自动推导。
- 提交必须各有一个`POST_COLLECTION_CHECKLIST`和`IMPLEMENTATION_COMMITMENT`当前文件revision；可附加`OTHER_EVIDENCE`。文件以`ownerContext=CUT/objectType=CUTOVER_CLOSURE/objectId=closureId`绑定，保存时inspect，提交时按冻结`artifactId/referenceKey/versionNo/FileFactVersion/scopeVersion`锁定重验。客户端不得提交URL或正文。

### BR-FCUT006-003 INT-12证据与人工降级

- CUT消费端口只接受任务、设备、采集阶段、认证方式和业务相关ID，不拥有凭证明文。临时密码只在一次同步下发调用内传递，不进入CUT草稿、日志、摘要、审计或数据库。
- 采集阶段封闭为`PRE_CHECK/EXECUTION/TEST/ROLLBACK/POST_COLLECTION`。每次正式下发结果按`collectionTaskId`唯一保存；回调按`callbackEventId`幂等追加结果引用，乱序不得覆盖已保存终态证据。
- INT-12不可用或下发失败时，保留`DISPATCH_FAILED`证据。授权工程师可关联同任务、设备、阶段和原失败`collectionTaskId`追加`MANUAL_UPLOAD`文件事实；原失败记录保持不变。
- 正常闭环的CUT代码只依赖消费端口。正式Provider未形成时，仅`src/test`替身可返回受控成功/失败事实；不得注册生产Fake、空成功或fallback，也不得据此声明生产浏览器或Implementation Done。

### BR-FCUT006-004 保存、提交与归档

- 保存只允许`DRAFT`，以闭环`If-Match`和任务`X-Task-Version`执行CAS；任务、闭环、附件/证据投影、平台幂等和审计同事务。
- 提交锁序固定为任务→审批/方案引用→闭环→附件/采集证据→ProjectScope/PLT Owner事实→设备活动范围→平台幂等/审计/Outbox。任一步失败整体回滚。
- 提交成功把闭环`DRAFT→SUBMITTED`并保存提交人、提交时间、归档时间；任务保持`currentStage=P6`并从`CLOSURE_IN_PROGRESS→ARCHIVED`，追加`P6_CLOSURE_SUBMITTED`阶段历史，同时将本任务全部`cut_task_device_scope.active_marker`置空。
- 最终`SUCCESS`同事务写唯一`CutoverCompleted` Outbox；`FAILED`同样归档但不写该事件。下游项目/资产/ITR处理失败不得回滚CUT归档。
- 相同`Idempotency-Key`同摘要返回既有结果；异摘要永久冲突，处理中返回可重试冲突。并发提交只有一个CAS胜出。

## 4. API、权限与Owner边界

- 精确REST、wire、错误、allowedActions和内部回调见`F-CUT-006-api-contract.json`。
- 权限固定为`pms:cutover-task:query-closure/save-closure/submit-closure/request-collection`四项；人工结果关联复用`save-closure`，不创建第五个业务权限。
- INT-12只保留CUT消费端口与CUT入向回调合同；PLT使用现有`FileArtifactApi`；PROJ只消费`ProjectScopeApi`。本Feature不修改这些物理Owner。
- `CutoverCompleted`只携带不可变闭环引用，不直接修改项目阶段或客户资产。

## 5. 数据与迁移

- 新增`cut_cutover_closure`、`cut_cutover_closure_attachment`、`cut_cutover_collection_evidence`三张CUT Owner表，精确字段、联合、唯一键和锁序见physical contract。
- 前向扩展`cut_task`允许`P6/ARCHIVED`，扩展阶段历史`P6_CLOSURE_SUBMITTED`，不改写现有任务；提交命令显式释放设备活动标记。
- `pms_cut_execution`是逐步骤模型，缺少闭环级唯一身份、四项完整结果、最终结果和合法PLT文件事实；当前任何旧行均不得单独迁成闭环。旧行保留并登记迁移问题，不以状态、文本、URL或时间猜测。
- `pms_cut_observation`整表排除；`pms_cut_task.actual_time/remark/status`不推导闭环。旧页面和接口保持不变。
- Flyway仅在实施串行合入时取下一空闲版本；Feature Ready不预约版本。

## 6. 验收标准

- AC-FCUT006-001：P6一线工程师可创建、保存并读取引用已批准方案的闭环草稿，页面不生成步骤执行或稳定观察动作。
- AC-FCUT006-002：三项正常性、回退判别联合、两类必需附件、遗留文本和最终结果完整校验；文件失效时提交零写。
- AC-FCUT006-003：受控INT-12成功回调可成为闭环证据；下发失败后人工文件可关联原失败任务，失败事实仍保留。
- AC-FCUT006-004：SUCCESS与FAILED均原子归档并释放活动设备；只有SUCCESS产生一个`CutoverCompleted`。
- AC-FCUT006-005：越权、非P6、来源失效、版本陈旧、同键异载荷和并发提交均零副作用；同键同载荷返回原归档结果。
- AC-FCUT006-006：CUT单元/集成与受控MySQL可用测试替身跑正常正向闭环；生产ProjectScope/PLT/INT-12未接通前不得宣称真实浏览器或Implementation Done。

## 7. Feature Ready Gate

当前：`DRAFT / NOT_READY / REVIEW_REQUIRED / NOT_STARTED`。最近Gate为本Feature规格、API/physical机器合同、旧实现复用审计和依赖边界的独立Feature Ready复审；GO前不得生成Technical Plan或实现。
