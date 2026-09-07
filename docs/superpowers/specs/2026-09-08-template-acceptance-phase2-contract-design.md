# 项目级验收接口与物理契约候选

> 文档状态：`IN_REVIEW`；候选不是正式SDS放行或可执行实施计划
> 日期：`2026-09-08`
> Question：`Q-TPLACC-001`
> Requirement：`PM-03@V1`、`PM-11@V1`、`ACC-03@V1`、`ACC-04@V1`、`COM-01@V1`
> 上游：PRD修订018、ADR-0045及已由需求方确认的`2026-09-07-template-acceptance-evolution-design.md`
> 结构化候选：`docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract.json`
> 实现审计基线：`3abdeb1e`；本稿只描述候选，不修改应用/接口源码、Flyway或历史数据
> 当前DU：`DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE`
> 当前修订：`R2`；独立任务`01a07ce8-42fa-7dd2-8991-094d0c15cc6c`对`3d82db0b`返回候选技术GO；接收记录见`docs/engineering/gates/phase-2/input/template-acceptance-r2-independent-review.md`，不代表正式Phase或Q关闭

## 1. 主体先确定：这是项目验收

需求方补充“验收主要是项目层面的验收”。本候选因此只覆盖项目级ACC-03：项目关联必需，初验/终验是项目验收类型，业务主身份保持`tenant_id + project_id + acceptance_type`。不按Stage、ProjectTask、WorkBinding、订单行或设备创建不同验收根，不增加多轮验收模型；需要换版时沿用报告版本。

独立性是“不依附S5或固定任务才存在”，不是脱离项目。Stage/Task组织办理、责任和导航；WorkBinding连接同一项目验收实体；BusinessView呈现其Owner允许的操作；CompletionRule/Gate引用结果。多个入口指向同一明确项目验收，不产生第二套任务或验收事实。

COM交付范围只是可配置证据依赖，不是所有项目验收的统一创建前置。规则未要求数量/范围覆盖时，只按项目验收记录及配置事实办理，不调用COM；要求时才锁定COM真实范围和版本。项目授权、实体身份及审计始终保留。

## 2. 当前实现决定的最小差量

| 当前事实 | 必要处置 |
|---|---|
| V166的acc_acceptance已有项目/类型唯一，任务ID和执行契约ID必填 | 复用根与唯一性；新记录解除任务存在前提，但增加明确来源和规则快照，不是单纯改空字段 |
| 原initializer固定T-INITIAL-ACCEPT/T-FINAL-ACCEPT | 保留旧调用及历史解释；新项目验收类型来自发布配置或明确创建请求，不从任务码推断 |
| getAssignedScope返回全局scopeVersion和数量明细，不含逐范围allocationVersion | 复用查询投影/校验逻辑，在现有COM验收锁API新增阶段无关方法；禁止拿全局版本冒充分配版本 |
| 旧COM验收锁API要求projectStageSnapshotId | 原方法原样保留；新方法不接收或伪造阶段快照 |
| acc_acceptance_scope_binding仅有阶段快照来源 | 加性引入验收活动来源，两种来源互斥；既有绑定和减量保护不能丢失 |
| BPM状态事件只有id/key/status/reason/businessKey | 事件仅作信号；集成层回源核实租户、项目、实际定义和发起人，不信任缺省线程身份 |
| report附件scope_version表示授权范围 | 新业务字段明确叫project_scope_version，不混用权限树版本和COM版本 |

证据入口：`AcceptanceActivityInitializationApiImpl`、`AcceptanceActivityCompletionFactApiImpl`、`AcceptanceScopeBindingService`、`AcceptanceStageBindingCoordinator`、`AssignedDeliveryScopeQueryService`、`DeliveryScopeAcceptanceLockApiImpl`、V160/V166及`BpmProcessInstanceStatusEvent`。测试输入已有对应Initializer/Completion/ScopeBinding/Coordinator/COM Lock测试；本轮未重跑业务测试。

## 3. 候选接口与职责

JSON中的records/enums列出精确输入输出字段；此处解释边界。所有新增接口均为候选，不声称当前已装配。

| Owner | 契约 | 职责 |
|---|---|---|
| PROJ | ProjectAcceptanceContextApi.lockAndRevalidate | 锁定可信项目及树/范围授权，重验项目版本；写动作只允许ACTIVE，不以S5或任务存在为前置 |
| PROJ配置基础 | DeliveryDefinitionFactApi.lockPublished | 读取并锁定已发布、兼容ACC Schema的配置；不给业务调用者模板管理权，不接收客户端规则正文 |
| COM | DeliveryScopeAcceptanceLockApi.lockCurrentForBusiness | 返回项目scopeVersion、逐范围allocationVersion和数量明细的同源快照，MANDATORY加入调用方事务 |
| ACC | AcceptanceLifecycleApi.open / complete | 创建/解析项目验收、按冻结规则完成；ACC独占自身状态写入 |
| ACC | AcceptanceExecutionFactApi.inspect / lockAndRevalidate | 只读返回证据有效、活动完成、验收通过及各版本；锁定重验也不创建实体或推动状态 |
| ACC | AcceptanceScopeBindingApi.refreshForBusinessScope | 接收COM写事务已锁定的完整快照，更新适用项目验收的范围跟踪和绑定，不创建验收根 |
| ACC | AcceptanceScopeBindingApi.lockScopeParticipants | 在COM持锁后、任何范围绑定守卫/写入前，一次按有序ID预锁全部受影响项目的新来源验收根；只锁定，不创建或修改业务 |
| ACC | AcceptanceScopeGuardApi.checkReductionForBusiness | 对阶段来源和项目验收来源统一判断减量保护，返回明确来源类型和身份 |
| PROJ应用层 | AcceptanceWorkBindingResolver.resolve | 验证节点归属、绑定/规则版本和动作后创建或关联该项目的验收，返回受权视图引用 |

继续使用现有-api模块；候选新增`pms-module-project-api → pms-module-commerce-api`纯DTO依赖，以复用COM权威快照类型。commerce-api不反向依赖project-api，不形成-biz互依或第二套COM事实模型。实际装配/模块测试必须在实施前验证。

配置读取区分新选择和已冻结引用：frozenUse为空时要求当前可选的已发布/启用规则；frozenUse非空时由PROJ校验其确实属于指定节点/触发版本。停用不能抹去已冻结规则的历史解释，实际新写动作仍须目标Provider支持和当前授权。ACC返回allowedActions，PROJ只与节点权限及注册视图模式求交集，不根据组件可见性重建ACC权限。

### 3.1 项目验收REST

- `POST /api/v1/pms/acceptances`：项目ID、验收类型、发布规则ID及期望项目/树版本；仅配置要求COM时提供expectedScopeVersion。要求`pms:acceptance:report:write`与项目编辑范围，Idempotency-Key必需；来源固定为服务端构造的DIRECT。
- `POST /api/v1/pms/acceptances/{id}/actions/complete`：If-Match携带活动版本，正文携带期望报告版本和项目/树/适用范围版本；要求`pms:acceptance:report:complete`及项目编辑范围。
- `GET /api/v1/pms/acceptances/{id}/execution-fact`：查询权限及项目查看范围，零创建/完成副作用。
- Stage/Task的`actions/resolve-work-binding`是受控写动作；普通GET工作台不创建实体。客户端不能提交验收目标Owner、规则正文、actor、tenant或伪造来源；类型、项目、规则和BusinessViewKey从被冻结的节点绑定解析。

既有活动查询、报告草稿/发布/撤销/下载路径保留，但新来源报告不能沿用旧服务的全局PENDING、必需初验及非空附件断言；具体分派见3.3。已发布配置的业务选择UI复用模板配置基础能力；本候选不另造规则维护系统。

项目级验收还必须同步修正报告访问的旧任务前提：现有AcceptanceReportFileBusinessObjectPolicyProvider.validReport要求projectTaskId>0，新项目级根无任务时会被拒绝。候选按明确origin_kind区分新旧身份，新记录验证真实项目/报告/来源，旧LEGACY_TASK仍验证原任务来源，项目及文件授权均不删除。Mapper需投影新增来源字段；查询页面不得补造任务ID。新报告只向显式配置的项目应交要求建立来源关系，未配置目标不制造永久待补偿；已配置目标失效仍保留报告并补偿。

### 3.2 规则与事实

AcceptanceRuleSnapshot由发布配置解析，包含requiredReportFields、minimumAttachments、initialPrerequisite、completionRequirement及scopeCheck。配置Schema与字段目录封闭，拒绝任意表达式/SQL/脚本；原四项报告要求作为预置，而非硬编码全局前置。

- scopeCheck=NOT_REQUIRED：本验收的expectedScopeVersion与新业务范围列为空，不为本验收直接调用COM。若另行配置初验前置，则消费初验按其自身冻结规则产生的事实；该明确依赖所需的COM重验不是给本验收暗加范围规则。完全未引用COM事实或这类前置时不调用COM。这里为空有明确配置依据，不等于Owner不可用。
- scopeCheck=COM_CURRENT_SCOPE：期望版本必填且非负；回源锁定COM，报告版本冻结同源明细及范围版本。0＋空列表是已知空范围，不自动通过完成判定。
- 初验前置只按配置读取同项目PRELIMINARY事实，不替代终验自身条件；初验不能配置依赖自己完成。
- 报告版本是验收记录的版本，不等于必须上传文件。业务必填项和附件数量可配置；有效文件/权限、真实结论和版本身份不能伪造。
- reportEvidenceValid、activityCompleted、acceptancePassed分别返回。验收通过须有明确通过结论；选用COM覆盖条件时，还须当前范围一致。旧记录没有新版规则/范围快照时，不推测新版通过事实，旧历史查询仍保留。

FactOutcome不是业务状态：FOUND时相关身份及布尔事实必须已知；NOT_FOUND/DEPENDENCY_UNAVAILABLE时未知字段允许为空，不能填0或false冒充已判定。消费者先检查FOUND再解释事实，未知时allowedActions为空。写命令沿用现有异常分类/统一响应，不新造成功包装或泄露越权对象存在性。

### 3.3 报告命令与完成后的换版（R1-02）

复用现有五个报告路径，新增来源由ACC项目验收报告应用服务承接，LEGACY_TASK仍走原业务语义；不按客户端开关选择新旧。JSON给出Create/Update/Publish/RevokeReport命令和返回类型，所有新来源写命令要求原`pms:acceptance:report:write`、当前项目编辑范围、ACTIVE项目及Idempotency-Key。

| 动作 | 新来源活动/报告状态 | 期望版本及同事务效果 |
|---|---|---|
| 创建草稿 | PENDING或COMPLETED；可并存多个DRAFT | 校验活动、项目、树及适用COM版本；冻结根的规则、当前适用范围和显式应交目标；生成报告序号，活动version CAS递增 |
| 修改草稿 | PENDING或COMPLETED；仅DRAFT | 校验活动版本、报告ID/序号、项目/树版本；content为完整替换对象，允许尚未填完；不更新规则/范围/目标快照，活动version CAS递增 |
| 发布/替换 | PENDING或COMPLETED；仅DRAFT可变EFFECTIVE | 校验活动、草稿序号、期望当前报告ID（首次为NULL）、项目/树版本；按冻结规则重验必填项、初验、COM及完整附件集合；旧EFFECTIVE转SUPERSEDED，切换当前指针并CAS活动版本 |
| 撤销当前 | PENDING或COMPLETED；当前EFFECTIVE | 校验活动、当前报告ID/序号、项目/树版本；置REVOKED并清空当前指针，CAS活动版本；不要求已经失效的COM、初验或文件重新合格才能撤销 |

报告序号不是草稿编辑版本：每次新来源草稿内容写入都CAS活动version，防止同一草稿旧内容覆盖；PLT附件变更由其集合期望事实防覆盖，发布输入必须携带完整`FileReferenceSetExpectation`，key只能等于当前报告附件键、expectedScopeVersion必须等于当前授权树版本。服务端回源加锁比较全部有序事实；已知空集合可发布，缺失/不可用集合不可当空。字段可缺省的草稿不是有效报告，发布仍逐项执行冻结业务条件。无附件不等于跳过附件集合的授权/锁定检查。

创建草稿时仅冻结适用COM范围；修改草稿及撤销不依赖COM。发布和活动完成时，FINAL仅在initialPrerequisite非NONE时读取同项目初验；按EVIDENCE_VALID或ACCEPTANCE_PASSED精确判定，初验自己的适用COM依赖也纳入预锁集合。NONE时不查询或要求初验。规则与范围不可通过PATCH刷新；范围A变A+B后显式新建草稿。换版、撤销和范围失效不回退历史COMPLETED，也不重复触发旧初验完成的ACC-02后置动作。当前验收通过重新计算，不能把历史完成位当成当前通过。

LEGACY_TASK保留原报告业务条件、原任务完成/满意度联动及原响应语义；共享锁序必须按4.0修正，不能以“旧接口保留”保留已证实的反向锁。对旧实例放宽完成后换版或回填新规则不属于本候选。

### 3.4 零附件和多个应交目标（R1-02、R1-04）

新来源报告冻结`deliverable_target_snapshot`（有序去重的deliverableId/requirementRevisionId）；来源为项目已经实例化的显式应交要求，不接受客户端目标列表。空列表表示未配置目标，而不是投影丢失。发布/撤销继续使用AcceptanceReportVersionChanged，新增schemaVersion=2、originKind、ruleRevisionId及完整deliverableTargets；旧无schemaVersion消息按v1原规则处理。

v2事件零附件只在报告冻结规则允许且报告事实匹配时合法。投影为每个声明目标建立同一报告版本的来源关系，附件索引可为零行，不复制报告/文件。无目标直接完成投影；目标已声明但暂不可用保持补偿，不删除该目标。来源关系唯一键继续包含deliverableId，不能改成reportVersionId唯一。

零附件关系的archive_status使用加性值`NOT_REQUIRED`，表示无文件归档工作；archive_time为空、retry_count=0且无失败码，不调用PLT造空文件/空归档记录，不误标ARCHIVED或永久PENDING_COMPENSATION。有附件仍逐项归档、失败补偿；撤销关系仍为REVOKED/INVALID，历史文件引用保持。应交业务完成仍按其自己的完成规则，NOT_REQUIRED不是验收或应交通过。

`ProjectDeliverableSourceVersionMapper.selectByReportVersionId(selectOne)`必须改为场景Query的批量列表查询（tenantId、已授权reportVersionIds），空ID集合直接空结果，按reportVersionId、deliverableId、sourceVersionId排序。GET report-versions在原字段上加`deliverableSources[]`与`archiveSummary`，一次批量查询并按报告分组，不取LIMIT 1、不逐报告查询来源关系。

每个声明目标均返回deliverableId/requirementRevisionId、可空sourceVersionId/relationStatus及该关系自己的archiveStatus/failureCode/retryCount；已声明未投影返回PENDING_COMPENSATION及空sourceVersionId，不能伪装未配置。archiveSummary：无目标NOT_CONFIGURED；全部同值时返回该值；混合值MIXED。旧三个标量archive字段仅在恰一关系时返回该关系值，零/多关系时返回NULL；新视图必须使用数组/汇总，旧单目标响应不变。无目标与目标尚未投影的判断以冻结目标清单而非查询行数决定。

### 3.5 多目标共同归档身份（R2-01）

仅新来源、非空附件报告适用。ACC向现有`ArchiveFileReferenceSetsCommand`传递下列完整共同身份；不能仅共享文件集合、仍按sourceVersionId分配归档批次。

| 字段 | 同一报告所有应交关系的固定值 |
|---|---|
| operationId / archiveBatchId | 均为`ACC-REPORT-ARCHIVE:{reportVersionId}`；只含不可变报告版本ID，不含deliverableId/sourceVersionId、补偿次数或当前操作者 |
| businessDecisionRef | `ACC-REPORT:{reportVersionId}` |
| actorUserId | 该报告版本不可变publisher_user_id；不是来源关系创建人或Job用户 |
| attachmentSetKey / archiveSetKey | 均为`ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}`；purpose分别为ACCEPTANCE_REPORT_ATTACHMENT和ACCEPTANCE_REPORT_ARCHIVE |
| expectedScopeVersion | 报告发布时冻结的完整附件事实所共有的授权scopeVersion；不换成COM版本，不在每次补偿中另取新值冒充旧事实 |
| orderedExpectedPublicFileFacts | 从该报告不可变附件事实按原sequence读取完整集合；每个关系的附件投影须逐项一致，缺失或不同则补偿失败，不能取关系子集归档 |

tenant取当前受信上下文并与报告/来源关系一致。先按4.0锁项目、活动、报告及本次目标关系，再调用PLT；所有目标竞争同一报告/归档集合锁。A首次成功形成报告级FileArchiveRecord；B（包括乱序/重试）使用相同批次、决定、actor、scope及文件事实命中原记录，通过既有requireArchiveReplay后只更新B自己的补偿投影。不得因A已ARCHIVED就跳过B的当前权限/文件重验直接伪报成功，也不新增第二份文件或归档记录。

LEGACY_TASK继续使用原`ACC-ARCHIVE:{sourceVersionId}`批次/操作键、原报告决定及原发布人，不改写历史、不套用新批次重放旧归档。零附件仍按3.4为NOT_REQUIRED，不向要求非空集合的PLT接口发空命令。新报告换版使用新reportVersionId自然形成另一归档身份，不能跨报告合并。

## 4. 事务和锁序

### 4.0 所有共享写路径的闭合锁序（R1-01）

统一顺序为：PROJ树根/项目/树版本 → 所需Stage/Task/执行契约及冻结定义 → COM订单行 → COM范围行/明细及保护前驱 → COM项目水位 → ACC活动根 → ACC报告 → ACC范围绑定/应交根/来源关系 → PLT文件集合与文件事实。各层先一次查明参与集合，再按稳定ID有序锁定；多项目先锁全部树根，再锁非根项目。PROJ同层沿用其权威树锁序。允许省略不访问的层，不允许持有后层锁后新增前层锁。

命令入口可先做无锁身份定位，但必须在PROJ锁后重验身份/版本；初验根及其当前报告、旧任务/执行契约、旧初验完成所需满意度节点等全部在进入ACC锁前确定并预锁。初验根不存在也按项目锁下的唯一身份检查，不能发布时在PLT之后再补取初验。回源PLT策略保留ProjectScope/FileBusinessScope权限检查，只重验本事务已持有的相同PROJ锁，不补锁另一个项目。

覆盖入口必须同时登记到后续实施切片：新open/complete/resolve、旧createDraft/updateDraft/publish/revoke、旧任务lockAndComplete、新旧报告附件绑定/移除、来源投影及ArchiveCompensationService。归档当前先锁应交关系再通过PLT反锁PROJ，同样必须前移项目/报告锁；跨报告文件集合操作先收集全部所属项目。原流程内已由PROJ持锁的调用复用同一事务，不开启新事务另取同一锁。PLT幂等占位是命令最外层前缀；成功审计/Outbox在领域写之后，不在持锁中嵌套申请另一命令的幂等占位。

COM交互命令/拆分保留PROJ前缀；ERP权威接收可只持COM及后层锁，绝不反调PROJ或因ACC业务条件拒收权威事实。该例外是省略不访问层，不是COM→PROJ。不能以死锁重试、删授权或仅调整新入口代替上述路径收敛。

范围写入口必须提前收集最终完整项目快照需要的所有订单行/当前范围（包含计划新增行），按COM顺序锁定并校验水位/成员集合；不能只锁修改行，持ACC锁后再补查整项目的COM锁。进入ACC层时先调用`lockScopeParticipants(tenantId, projectIds, operationId)`，一次按活动ID锁定全部受影响项目的新来源根（返回空根集合合法），然后才调用旧bindEffectiveScope、新减量守卫及最终refresh。否则“先锁绑定作守卫，再刷新时补锁活动根”仍会倒序。发现前置成员变化须在业务写入前重新开始事务，不在后层锁中补锁；这不是用死锁重试掩盖反序。

### 4.1 创建或解析

1. 从认证上下文或已验证BPM来源构造TrustedActor，检查ACC功能权限。
2. PROJ公共边界先锁根/项目并校验树版本和编辑范围；NODE来源还锁对应节点及执行契约，查明其确实属于此项目。
3. 锁定发布配置并冻结ACC规则。只有scopeCheck要求COM时，才取得COM有序订单行/范围及项目水位的完整快照。
4. 锁ACC项目/类型唯一身份。不存在则创建PENDING根及适用范围绑定；存在时DIRECT另一创建意图返回冲突。NODE/BPM解析只有在projectId、acceptanceType及ruleRevisionId均与现有根一致时才关联，不把“看起来相似”的规则当作兼容，不修改原来源、规则或报告；缺少新版规则快照的旧根不能自动升级。
5. 同事务记录根、必要绑定、命令幂等与审计/Outbox；失败整体回滚。与后续阶段准出是不同完成点，已成功创建的验收不能因下游暂未通过而被删除。

不同来源/意图不能靠同项目自动合并业务内容；显式关联仅复用同一项目验收身份。不能把scopeId、nodeId或新随机重试Key变成第二验收根。

### 4.2 完成与事实重验

按4.0取得PROJ项目/树、适用COM范围、有序ACC活动根（含配置要求的初验）、有序当前报告及PLT文件事实。验证期望版本、配置条件及报告事实后，ACC CAS推进自身PENDING→COMPLETED，追加审计和幂等结果；不直接写PROJ任务/阶段。

新非原生节点完成只读取Owner事实并执行自身状态机；旧lockAndComplete路径保留原适用范围，不让旧接口参数改空后冒充新路径。COMPLETED的历史活动不因换版/撤销/范围变化被改写成未完成；当前acceptancePassed及依赖快照资格必须重新判定。

### 4.3 COM范围变化

COM成功产生qualified范围新版本时，先形成已锁定的BusinessAcceptanceScopeSnapshot，再调用ACC refreshForBusinessScope；ACC持锁后不回调PROJ/COM补锁。未确认/冲突范围不能伪装成qualified快照，也不得为下游验收改变ERP权威接收语义。

- 遍历该项目新来源且冻结scopeCheck=COM_CURRENT_SCOPE的验收根，不能只查已有绑定行，否则会漏掉“以空范围创建、后来首次分配”的项目。
- observedScopeVersion相同则重放；传入旧版本则拒绝；新版本追加缺失分配绑定并CAS更新跟踪水位。未改变的范围行复用已有绑定，不能因不同触发原因覆盖first binding_trigger或产生冲突。
- 不要求COM证据的项目验收不加入此订阅，不因合同/订单范围变化增加新的验收义务。
- 已发布报告保留原快照。当前版本不匹配时依赖判定立即失去资格；投影可复用既有范围事件刷新，但最终命令必须回源，不能依赖过期PASSED缓存。
- 原阶段来源绑定继续参与减量保护；原Q-FCOM-002关闭/解锁未裁决范围不扩展，本候选不自动写effective_to。

### 4.4 范围替换的保护前驱与全写路径（R1-03）

资格与保护独立。COM在`com_delivery_scope`加性保存不可变`protection_lineage_kind=ROOT/REPLACEMENT/UNRESOLVED`及`predecessor_scope_id`。明确新分配是ROOT；同项目/订单行替换（含ERP的CONFLICT_FROZEN、DIRECT_ADJUST和拆分父项目余量）必须记录真实前驱ID，前驱的分配版本从不可变原行读取。不是新增一套数量/验收绑定，也不把冲突行写成qualified。

COM在自身范围锁下生成`DeliveryScopeProtectionLineageFact`，包括当前scopeId/allocationVersion、同tenant/project/orderLine的全部可信前驱版本及lineageComplete。ACC新守卫接收该Owner事实，按scopeId/allocationVersion匹配当前或祖先上仍有效的两类绑定：任一确定有效保护即LOCKED；完整链且无保护才UNLOCKED；链不完整或相关绑定不能判定且无已知LOCKED则UNKNOWN。返回匹配的protectedScopeId/allocationVersion，不能冒充当前范围上已有绑定。保护查询不要求前驱仍ACTIVE，也不回调COM补锁。

旧行默认UNRESOLVED，不因为新增列NULL就认定ROOT；仅可用既有明确创建/替换记录证明ROOT或唯一前驱。证据不足保留UNKNOWN，限制该行减量，不能按数量、时间或相似字段猜测历史。已知有效绑定仍直接LOCKED。上线前须核对实际存量受影响行并按Schema审阅受控处理；这是现有替换记录缺乏来源证明的处置，不改变ERP接收、不开放自动解锁。

| 当前写路径 | 保护检查/血缘 | 成功后的资格刷新 |
|---|---|---|
| CommerceDeliveryScopeCommandService.assign | 新独立分配ROOT；不调用减量守卫 | 全部范围写/水位推进后，对项目生成完整qualified快照并同步refresh；空范围首配也覆盖 |
| adjust/release及其preview | COM先锁真实链；减量/释放调用新守卫，LOCKED或UNKNOWN拒绝；adjust替换记录前驱 | 成功后最终水位和完整qualified快照同步refresh；release后的已知空集合也刷新；不可qualified时不伪造刷新 |
| CommerceAuthorityIngestService.freezeAffectedScopes | ERP权威正常接收，S1→S2冲突替换同事务记录S1前驱；不调用用户减量守卫或写新qualified绑定 | SOURCE_CONFLICT推进COM水位；旧报告当前资格回源失败，保护仍沿S1生效。只有产生可验证qualified新版本才调用refresh，且ACC不反调PROJ |
| DeliveryScopeCompatibilityService.applySplit（PM-02真实调用方） | 写子项目/释放父范围前，逐原父scope按实际留存量调用新守卫；总量不变也不豁免父项目减量。失败使整项拆分回滚；父余量记前驱，已获UNLOCKED的子项目首次分配为ROOT | 全部父/子水位推进后，按项目ID向每个qualified项目同步refresh；受保护父范围不能通过换ID/迁到子项目绕过 |

项目范围追加及未来修订017消费者必须复用上述已收敛的COM写入口；不得另写表跳过同事务守卫/水位/刷新。旧DeliveryScopeService已废弃且当前API未调用，保持禁用接入，不在本轮恢复。所有当前减量消费者切换到携带可信链的新守卫；旧checkReduction保留原签名但不再作为范围变更的最终授权，无法证明无前驱保护时只能UNKNOWN。阶段来源的旧绑定全部参加新守卫，不能只检查新验收来源。

## 5. 来源事件与身份

### 5.1 发起时冻结，结束事务只捕获信号（R1-05）

先支持现有受管PMS Gate。PROJ在启动审批命令中加性提供服务端构造的`AcceptanceApprovalTriggerSnapshot`（未配置时NULL），集成Provider写入保留变量`pmsAcceptanceTriggerSnapshot`并禁止普通variables覆盖。包含schemaVersion、commandVersion、tenant/project/Gate/Reference、triggerRevisionId、验收类型、ruleRevisionId、完整规则快照、原发起人、原项目/树/适用scope版本及PROJ冻结的intentKey。重试不得读取当前新规则替换这些字段，原无快照流程不追溯补发。

integration注册同步ApplicationEvent监听器，仅处理实际PMS Gate且APPROVE(2)信号。监听器必须处于Flowable结束处理的原Spring事务、同数据源事务管理器内；用运行/历史实例和保留变量校验来源身份，**此时不要求历史endTime已经可见**。它先以PlatformCommandExecutionApi同事务写`AcceptanceApprovalSignalCaptured`到既有Outbox；不使用@Async、AFTER_COMMIT首次落库或REQUIRES_NEW捕获，也不调用ACC或锁PROJ业务行。无法可靠读取已配置触发或捕获落库失败必须使该审批事务回滚；这是可靠捕获失败，不是验收业务门禁回滚已完成审批。

捕获幂等scopeCode=`PMS_ACC_APPROVAL_CAPTURE`、actorId=经实际实例核实的startUserId，完整captureKey=`AAP:{tenantId}:{processInstanceId}:{triggerRevisionId}`（原生实例ID长度≤64、完整键≤128）。captureKey只进入幂等键及载荷，不再作为Outbox eventId。eventId使用首次新执行中生成的标准带连字符UUID字符串（36字符），满足现有`plt_outbox_event.event_id VARCHAR(64)`，不扩列、不截断业务身份，不增加哈希框架。

精确捕获顺序：先构造不含eventId的`AcceptanceApprovalCaptureRequest`，以其完整冻结字段按PLT既有协议生成请求摘要；调用PlatformCommandExecutionApi.execute，responseType为`AcceptanceApprovalCaptureResult`。仅获得新执行的operation回调内生成UUID并返回captureKey/eventId；successFactsFactory使用这个结果创建`AcceptanceApprovalSignalCaptured`完整payload，payload.eventId、BusinessEvent.eventId及Outbox列三者相同，payload.captureKey保留完整业务身份。响应JSON、成功幂等、审计及事件在原BPM事务共同提交。同键同请求重放直接返回原captureKey/eventId，不再运行回调或生成事件；异载荷/处理中不生成UUID，不把随机eventId加入请求摘要导致伪冲突。若整个事务回滚，则没有已提交的捕获身份，重试仍使用原captureKey，首次成功提交可生成新的UUID。

载荷继续包含冻结触发全文、实际definitionId/key、businessKey、signalStatus=2和processInstanceId，不伪造尚未核实的approvedAt。业务intentKey保持PROJ启动时冻结值；消费者重算captureKey核对来源组合，并校验Outbox信封eventId等于payload.eventId。ACC消费操作键仍用这次已固化的短eventId，不能把captureKey截短后使用或在投递重试时重新生成ID。

### 5.2 提交后独立核验和消费

只由integration专用消费者通过PlatformOutboxDeliveryApi领取该eventType。消费时回读**已经提交、endTime非空且PROCESS_STATUS=2**的精确实例，验证真实租户/发起人、definitionId/key、businessKey及全部冻结保留变量；approvedAt来自该实例endTime，不用收到信号的时间。未结束、未知、身份不符或不支持commandVersion均不调用ACC、不ACK，按同一eventId保存失败审计并scheduleRetry，交Owner处理，不借Job用户。

核验成功后独立ACC事务按原actor重验当前权限、项目可编辑性及冻结引用；当前项目/树/适用COM版本由Owner重读后作为本次OpenAcceptanceCommand的期望版本（保留捕获时原版本用于审计，不修改原payload）。允许重试更新本次并发期望，不允许更新ruleRevisionId、triggerRevisionId、intentKey或来源身份；规则停用按冻结引用解释。租户/来源异常、撤权或永久业务冲突保留未投递及失败审计，不伪报成功。只有open已提交CREATED/RESOLVED_EXISTING，或同键同载荷重放已提交结果，才markDelivered(eventId, expectedRetryCount)。

为防ACK丢失后重新组装当前版本造成幂等异载荷，消费入口先用固定捕获payload与eventId进入PLT命令幂等包络；新执行才读取当前版本并执行ACC内部同事务open业务体，open不再嵌套另一个幂等占位。成功结果和ACC根/绑定同事务持久化；重放返回原结果并重新完成ACK，不重新选规则或创建。BPM事务只承诺可靠信号，ACC事务才承诺验收创建；审批通过从不表示验收完成。

发送点在BpmProcessInstanceServiceImpl结束回调内、publisher为同步publishEvent已由源码核对；原事务是否覆盖Flowable与PLT数据源、回滚/崩溃/ACK丢失的实际行为须在后续同库集成测试证明，本轮仅冻结明确设计，不声称该运行验证已完成。其他审批来源须先提供Owner注册契约，不按任意businessKey猜项目。

## 6. 物理差量候选

具体字段、类型、可空、索引及约束见JSON的storage；此处不分配Flyway号，不执行ALTER。

### acc_acceptance

保留原项目/类型唯一。project_task_id、execution_contract_id允许为空但仅作LEGACY_TASK来源；增加origin_kind、origin_key、origin_snapshot、rule_revision_id、rule_snapshot及observed_scope_version。

LEGACY_TASK必须保留原正数任务/契约ID，旧规则/范围快照不得推造。DIRECT/NODE/BPM_APPROVAL必须没有旧任务身份列，来源和规则快照完整；NODE细节只在来源快照及PROJ绑定中保存，不成为根身份。仅配置要求COM时observed_scope_version非空，否则必须为空。规则和来源快照插入后不可覆盖。

### acc_acceptance_scope_binding

增加可空acceptance_activity_id，并使project_stage_snapshot_id可空。两者恰有一个正数，另一个为空；保留旧阶段唯一键，新增`tenant+project+acceptance_activity_id+delivery_scope_id+scope_allocation_version`唯一键。保留LOCKED及禁止自动解锁约束，新触发码为ACCEPTANCE_OPEN/ACCEPTANCE_SCOPE_EFFECTIVE。

新旧源DTO必须可判别，不把acceptanceId放进projectStageSnapshotId。COM减量守卫的新方法同时识别两种来源；旧接口继续服务原调用链。绑定表不保存第二份可修改数量。

来源互斥CHECK须显式包含IS NOT NULL，不能只写“字段>0”而让SQL的UNKNOWN放过双空行；精确候选表达式见JSON的storage.acc_acceptance_scope_binding.sqlSourceCheck。实际SQL执行与唯一键验证仍属于后续Schema审阅，不由本文宣称通过。

### acc_acceptance_report_version

新增验收规则版本/快照，以及按配置适用的project_scope_version/delivery_scope_snapshot；新规则不要求COM时业务范围两列为空，新规则本身仍必须冻结。EFFECTIVE及历史版本的快照不可改；旧报告新增列可空且不回填推断事实。

报告草稿冻结适用规则及范围，发布时重验当前版本；范围已变化时显式创建新的报告草稿，不静默把旧A改成A+B。PLT附件中的scope_version继续表示授权版本，不能复用为业务数量范围版本。

R1补充`deliverable_target_snapshot JSON NULL`：新来源草稿必填JSON数组（可空数组），冻结已实例化要求身份/版本；旧报告留NULL，不补造历史配置。活动已有version承接新来源草稿CAS，不新增重复草稿版本列。`acc_project_deliverable_source_version`和应交根现有archive_status加性支持NOT_REQUIRED，无文件归档时archive_time为空；关系状态不增加新的业务完成值。

### com_delivery_scope

R1只加两个保护来源列：`protection_lineage_kind VARCHAR(16) NOT NULL DEFAULT 'UNRESOLVED'`与`predecessor_scope_id BIGINT NULL`。ROOT/UNRESOLVED必须无前驱；REPLACEMENT必须有正数且非自身前驱，同tenant/project/orderLine由COM事务重验；前驱及分配版本不可覆写或删除，不建立跨Owner外键。现有主键足以逐前驱解析，不另建血缘/兼容平台。旧行不默认无保护，Schema计划必须展示具体历史证据与未能核实的限制。

## 7. 旧实例与依赖边界

- 不修改V160/V166及既有历史数据；未来通过新前向迁移加性变更，旧任务源、旧报告、文件引用和绑定必须可回读。
- 不给旧报告/旧终验补造新规则、范围或明确通过事实。旧root的原关系仍由旧契约解释，新增绑定不能借复用根覆盖原配置。
- published definition消费者API及Stage/Task解析器属于模板配置基础能力的接口依赖，必须在基础闭环完成后接入；不得通过假Provider验证独立验收Done。
- 当前candidate只覆盖项目级验收，不建设“任意实体验收平台”、独立审批引擎或设备级/订单行级验收根；到货批次验收等原领域模型不受影响。

## 8. 进入实施计划前的审阅结果要求

1. 按既有顺序完成受影响Phase 1边界复核，再把候选具体接口/字段回写正式SDS Phase 2；候选不能直接晋级。
2. 审阅JSON全部记录/接口引用、单向-api依赖、三类来源、范围可选条件、空范围后新增、重复触发、旧绑定保护及锁序。
3. 形成对应物理合同/参考DDL并完成适用P3-E09及Schema检查；本轮没有执行数据库或迁移。
4. 明确F-ACC-001、PROJ和COM各自承担的切片及唯一当前Technical Plan；不能把纯接口/表作为Feature Done。

候选用例见JSON.requiredAcceptanceCases，当前均NOT_RUN。结构自审不替代运行、独立复审或需求方对新增详细契约的审阅。Q-TPLACC-001保持OPEN/BLOCKED_BY_SPEC；它不阻断不依赖此契约的模板基础配置。

## 9. R1复审定位

| 原NO-GO | 当前契约落点 | 实施时必须验证的真实失败路径（本轮NOT_RUN） |
|---|---|---|
| R1-01 / P1 | 4.0、JSON.sharedLockProtocol | 旧publish/归档与新complete/resolve交错，不能出现ACC/应交锁→PROJ反序；FINAL初验根先于报告/文件锁 |
| R1-02 / P1 | 3.3、3.4、报告命令/状态/物理差量 | 无初验规则、零附件发布及投影；COMPLETED后A→A+B新报告、撤销；LEGACY_TASK原语义保持 |
| R1-03 / P1 | 4.4、COM前驱列及可信链守卫 | 已保护S1在ERP 10→8替换S2后普通减量仍拒绝；PM-02父范围拆分不能绕过两类保护；成功写最终版本刷新 |
| R1-04 / P2 | 3.4、列表Query/响应及空目标语义 | 同报告两个应交关系、不同归档状态可查询，不selectOne或复制文件；尚未投影不能当未配置 |
| R1-05 / P2 | 5.1/5.2、冻结触发/捕获载荷 | 结束回调endTime尚不可见仍同事务捕获；BPM回滚不留信号；捕获后崩溃、规则改版、ACK丢失均重放原意图 |

复核证据为当前仓库的AcceptanceReportCommandService、AcceptanceReportFileBusinessObjectPolicyProvider、ProjectTreeScopeService、AcceptanceReportSourceProjectionService/ArchiveCompensationService/QueryService、ProjectDeliverableSourceVersionMapper、CommerceAuthorityIngestService、CommerceDeliveryScopeCommandService、DeliveryScopeCompatibilityService及其PM-02真实调用、BpmProcessInstanceServiceImpl/EventPublisher、FlowableProjectStageGateProvider和PLT命令/Outbox接口。独立任务对`a606e3c4`确认R1-01/02/03契约层已解决；R1-04查询基数已解决、R1-05主要事务设计已解决，但仍有以下两项P2。该裁决不代表已运行并发、数据库或业务验收。

## 10. R2修订及提交前自审

R2只处理同一独立任务的两项剩余意见（锁定R1输入`a606e3c44ea2f437856aba5cb02efd983868803b`）：R2-01为多目标共同archiveBatchId及完整归档命令身份，R2-02为捕获幂等键与Outbox事件标识的长度/重放一致性。需求方要求先完成本轮自审再提交复审；已解决的锁序、报告生命周期、范围保护以及既有业务语义不重写。

自审追踪`AcceptanceReportArchiveCompensationService.archive`→`FileArtifactApiImpl.archiveReferenceSets/requireArchiveReplay`→V92归档唯一键，以及`PlatformCommandExecutionApiImpl.execute/persistSuccess`→`PlatformTransactionalOutboxWriter.write`→V63 Outbox/幂等列。检查重点是两目标命令所有重放字段相同、旧路径不换批次、UUID只在新执行回调生成、请求摘要不含随机结果、事件列/信封/payload/重放结果一致。

自审状态：已完成一轮，未发现本轮两处修订遗留的契约级问题（主任务自审，不是独立批准）。50个记录类型、10个接口/应用服务、16个方法及10条REST的类型/字段引用检查通过；PLT归档命令八个字段均已对应。内存契约样例中同报告两个来源/重复执行的命令元组相同，换报告身份不同；19位报告ID的归档批次长38字符，完整AAP最大支持组合长108字符、eventId固定36字符，分别符合128/128/64列约束。

自审同时确认捕获请求不含随机eventId、事件载荷保留全部请求字段、结果保存两个身份，并逐项追踪提交/回滚/同键重放/ACK丢失路径；R1已解决契约、既有REST、业务身份及原物理差量未被改写。源路径与六文件认领/差异检查另记本DU。真实双目标归档、事务崩溃、数据库/DDL及业务运行验证仍NOT_RUN；本节不签署独立GO，不关闭Q-TPLACC-001或正式Gate。
