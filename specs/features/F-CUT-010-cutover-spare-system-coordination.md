# F-CUT-010 割接备件系统协同 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / REVIEW_REQUIRED`
> Requirement：`CUT-08（V2/P2）`
> Requirement切片覆盖：`CUT-08@V2=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-002`、`F-CUT-003`
> 关联Feature：`F-CUT-005（P5只读展示）`、`F-CUT-006（不建立备件门禁）`
> 支撑依赖：`INT-06 SpareApplicationGateway`、`ProjectScopeApi`、`FileArtifactApi`
> 机器合同：`specs/features/F-CUT-010-api-contract.json`、`specs/features/F-CUT-010-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-010-legacy-reuse-audit.md`

## 1. 业务目标

当P2问卷或P3风险事实表明割接需要备件保障时，割接一线工程师可从现有任务工作台携带已授权任务、项目和设备上下文发起外部备件协同。CUT保存外部申请引用、只读状态版本和人工证据，在P5展示保障情况；备件系统不可用时保留失败并允许同意图重试或线下证据补录，不在CUT重建备件业务。

## 2. Scope

### 2.1 包含

- 从当前已提交P2评估的`sparePartApplied=true`，或当前P3清单存在适用的`MAJOR_PROJECT_SPARES`系统匹配风险项，形成服务端`SpareNeedSnapshot`；
- 任务一线负责人在`ProjectScopeApi.ACTION_EDIT`及冻结设备归属仍有效时发起协同；
- 通过INT-06预留端口提交任务、项目、设备和需求来源快照，接收外部请求标识、可选跳转地址、可选外部申请号及原始状态；
- 外部申请引用、回调或刷新产生的不可变状态revision、同步失败/重试、对账冲突和平台审计；
- 关联PLT不可变文件事实作为人工证据，人工证据来源明确且不冒充接口回填；
- 任务工作台备件协同区及P5完整审批详情只读投影；
- CUT单元、集成、真实MySQL和组件验证使用`src/test`确定性INT-06/PROJ/PLT替身完成正常正向闭环。

### 2.2 不包含

- INT-06连接器、认证、第三方HTTP、备件系统页面、生产Provider或生产Fake/fallback；
- 备件型号、数量、库存、审批、发货、到货、领用、借还、补库、调拨或RMA业务明细；
- 修改P2判级、P3清单、P5审批决定、P6准入/提交/归档状态机；
- 根据外部状态自动通过/驳回审批或阻止P6；
- 修改旧`pms_cut_*`、旧页面、旧接口、旧运行数据或Yudao基础平台；
- INT-06授权/UMC子能力、AST设备档案RMA同步及V3能力。

## 3. 业务规则

### BR-FCUT010-001 备件需求来源

- `SpareNeedSnapshot.required=true`只允许由两类当前CUT事实得出：当前已提交P2评估`answerSnapshot.sparePartApplied=true`；或当前未失效P3清单包含`applicableFlag=true/sourceCode=SYSTEM_MATCHED/stableItemKey=MAJOR_PROJECT_SPARES`的风险项。
- 两类来源可同时存在，按`sourceType/sourceId/sourceVersion`稳定排序并冻结；CUT不解析参考附件、自由文本或未知清单项猜测需求。
- `required=false`时只允许查询既有历史引用和证据，不允许发起新外部申请。D级无P3清单时仍可由P2事实发起。

### BR-FCUT010-002 发起与授权

- 仅`NEW_PLATFORM`任务的当前`ownerUserId`可发起；必须同时具备`pms:cutover-task:manage-spare`、项目`ACTION_EDIT`，并锁定重验任务冻结的全部设备仍直接归属该项目。
- 发起命令携带`Idempotency-Key`、任务聚合版本和受信请求上下文`correlationId`。同键同业务载荷重放返回同一`applicationReferenceId`；同键异载荷永久冲突；处理中返回可重试冲突。
- CUT在调用INT-06前写入稳定平台`requestId`和请求上下文快照。超时或结果未知不得生成第二个requestId；后续重试复用同一意图和requestId。
- INT-06成功响应允许`launchUrl`和`externalApplicationNo`分别为空，但二者不得同时为空。只有非空外部申请号才建立外部业务引用；仅有跳转地址时保持`REQUEST_PENDING`等待回写。

### BR-FCUT010-003 外部引用与状态快照

- 外部引用按`tenantId + externalSystemCode + externalApplicationNo`唯一；同一任务可存在多个不同外部申请，平台requestId不可复用到其他任务或意图。
- 回调按受信租户、外部系统、外部申请号、`eventId`和正数`statusVersion`处理。相同eventId同载荷重放，异载荷冲突；相同申请版本同载荷重放，异载荷冲突；低版本记录为旧序且不改变当前快照；高版本只追加并切换当前标记。
- 状态revision保存外部原值、经过边界校验的只读JSON、外部发生时间、平台接收/查询时间和来源方式`CALLBACK|REFRESH|INITIATE_RESPONSE`。CUT不把原值映射为本地库存、审批、到货或领用状态。
- 显式刷新只能查询已存在外部申请号的引用。Provider不可用或返回身份/版本损坏时保留当前快照并记录失败，不返回空成功。

### BR-FCUT010-004 人工证据降级

- 外部不可用、未返回申请号或线下办理时，一线负责人可关联PLT已冻结的文件事实并填写不超过1000字符说明；证据revision只追加，不覆盖原文件事实。
- PLT事实固定`ownerContext=CUT/objectType=CUTOVER_TASK/objectId=taskId/purposeCode=SPARE_MANUAL_EVIDENCE/requiredAction=READ`，文件期望版本由服务端锁定重验。
- 人工证据不改变外部申请引用、状态版本或同步结果，也不生成“已到货/已就位/已完成”事实。恢复后只能通过正式外部申请号和版本事实核验。

### BR-FCUT010-005 P5展示与流程隔离

- P5完整审批详情显示`required`、外部系统、申请号、原始状态、查询时间、同步状态和人工证据摘要；不显示文件内部键、第三方原始响应正文或完整设备凭证。
- 备件投影不进入五项评审结果、`allowedActions`、审批通过/驳回守卫、P6准入或归档命令。外部失败不回滚既有P2～P6业务事实。
- 列表和详情按`applicationReferenceId ASC`、状态版本升序、证据创建时间/id升序稳定返回。

## 4. 状态、权限与一致性

- CUT只拥有集成同步状态：`REQUEST_PENDING`、`EXTERNAL_REFERENCED`、`RETRY_PENDING`；这些不是备件业务状态。
- `initiate/refresh/addEvidence`均使用平台幂等与操作审计；回调使用eventId Inbox语义。业务写与平台成功事实同一事务，外部调用不纳入CUT数据库事务。
- 新权限仅为`pms:cutover-task:manage-spare`；查询继续复用`pms:cutover-task:query + ACTION_VIEW`，P5审批查看继续复用现有审批详情权限。
- 锁序固定为任务根→项目/设备Owner重验→申请引用→当前状态revision；回调锁申请引用→当前状态revision，不锁P2/P3/P5/P6业务行。

## 5. API与数据

- 用户REST、回调合同、WireLong/时间、错误Envelope和恢复动作见`F-CUT-010-api-contract.json`。
- 三张CUT Owner表、JSON结构、唯一键、可空联合、版本和迁移边界见`F-CUT-010-physical-contract.json`。
- 生产INT-06合同未形成时，CUT只实现消费端口和测试替身；不得把测试URL、固定外部单号或手工SQL作为生产证据。

## 6. 验收标准

- 合法P2备件需求任务由负责人发起，受控INT-06返回外部申请号/跳转地址，CUT保存引用并在工作台和P5显示；同键重放不重复创建。
- 合法P3`MAJOR_PROJECT_SPARES`适用任务即使P2为否，也可形成明确双来源中的P3需求并发起。
- 匹配回调或刷新以更高版本追加状态快照；重复版本不重复写，旧版本不回退当前展示。
- INT-06不可用时保留`RETRY_PENDING`，同requestId可恢复；人工证据可追加但不冒充外部成功，P5/P6流程不被错误阻断。
- CUT聚焦测试、隔离MySQL与挂载组件交互形成一次受控正向闭环；生产Provider和真实浏览器仍是Implementation Done前独立证据。

## 7. Feature Ready Gate

当前状态：`NOT_READY / REVIEW_REQUIRED`。

送审输入：本Feature Spec、两份机器合同、旧实现复用审计、适用SDS修订、Requirement追溯和Task记录。通过前不得生成Technical Plan或实施。
