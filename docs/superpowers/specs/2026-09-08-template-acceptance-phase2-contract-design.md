# 项目级验收接口与物理契约候选

> 文档状态：`IN_REVIEW`；候选不是正式SDS放行或可执行实施计划
> 日期：`2026-09-08`
> Question：`Q-TPLACC-001`
> Requirement：`PM-03@V1`、`PM-11@V1`、`ACC-03@V1`、`ACC-04@V1`、`COM-01@V1`
> 上游：PRD修订018、ADR-0045及已由需求方确认的`2026-09-07-template-acceptance-evolution-design.md`
> 结构化候选：`docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract.json`
> 实现审计基线：`3abdeb1e`；本稿只描述候选，不修改应用/接口源码、Flyway或历史数据
> 当前DU：`DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE`

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
| ACC | AcceptanceScopeGuardApi.checkReductionForBusiness | 对阶段来源和项目验收来源统一判断减量保护，返回明确来源类型和身份 |
| PROJ应用层 | AcceptanceWorkBindingResolver.resolve | 验证节点归属、绑定/规则版本和动作后创建或关联该项目的验收，返回受权视图引用 |

继续使用现有-api模块；候选新增`pms-module-project-api → pms-module-commerce-api`纯DTO依赖，以复用COM权威快照类型。commerce-api不反向依赖project-api，不形成-biz互依或第二套COM事实模型。实际装配/模块测试必须在实施前验证。

配置读取区分新选择和已冻结引用：frozenUse为空时要求当前可选的已发布/启用规则；frozenUse非空时由PROJ校验其确实属于指定节点/触发版本。停用不能抹去已冻结规则的历史解释，实际新写动作仍须目标Provider支持和当前授权。ACC返回allowedActions，PROJ只与节点权限及注册视图模式求交集，不根据组件可见性重建ACC权限。

### 3.1 项目验收REST

- `POST /api/v1/pms/acceptances`：项目ID、验收类型、发布规则ID及期望项目/树版本；仅配置要求COM时提供expectedScopeVersion。要求`pms:acceptance:report:write`与项目编辑范围，Idempotency-Key必需；来源固定为服务端构造的DIRECT。
- `POST /api/v1/pms/acceptances/{id}/actions/complete`：If-Match携带活动版本，正文携带期望报告版本和项目/树/适用范围版本；要求`pms:acceptance:report:complete`及项目编辑范围。
- `GET /api/v1/pms/acceptances/{id}/execution-fact`：查询权限及项目查看范围，零创建/完成副作用。
- Stage/Task的`actions/resolve-work-binding`是受控写动作；普通GET工作台不创建实体。客户端不能提交验收目标Owner、规则正文、actor、tenant或伪造来源；类型、项目、规则和BusinessViewKey从被冻结的节点绑定解析。

既有活动查询、报告草稿/发布/撤销/下载接口保留。已发布配置的业务选择UI复用模板配置基础能力；本候选不另造规则维护系统。

项目级验收还必须同步修正报告访问的旧任务前提：现有AcceptanceReportFileBusinessObjectPolicyProvider.validReport要求projectTaskId>0，新项目级根无任务时会被拒绝。候选按明确origin_kind区分新旧身份，新记录验证真实项目/报告/来源，旧LEGACY_TASK仍验证原任务来源，项目及文件授权均不删除。Mapper需投影新增来源字段；查询页面不得补造任务ID。新报告只向显式配置的项目应交要求建立来源关系，未配置目标不制造永久待补偿；已配置目标失效仍保留报告并补偿。

### 3.2 规则与事实

AcceptanceRuleSnapshot由发布配置解析，包含requiredReportFields、minimumAttachments、initialPrerequisite、completionRequirement及scopeCheck。配置Schema与字段目录封闭，拒绝任意表达式/SQL/脚本；原四项报告要求作为预置，而非硬编码全局前置。

- scopeCheck=NOT_REQUIRED：expectedScopeVersion与新业务范围列为空，不调用COM。这里为空有明确配置依据，不等于Owner不可用。
- scopeCheck=COM_CURRENT_SCOPE：期望版本必填且非负；回源锁定COM，报告版本冻结同源明细及范围版本。0＋空列表是已知空范围，不自动通过完成判定。
- 初验前置只按配置读取同项目PRELIMINARY事实，不替代终验自身条件；初验不能配置依赖自己完成。
- 报告版本是验收记录的版本，不等于必须上传文件。业务必填项和附件数量可配置；有效文件/权限、真实结论和版本身份不能伪造。
- reportEvidenceValid、activityCompleted、acceptancePassed分别返回。验收通过须有明确通过结论；选用COM覆盖条件时，还须当前范围一致。旧记录没有新版规则/范围快照时，不推测新版通过事实，旧历史查询仍保留。

FactOutcome不是业务状态：FOUND时相关身份及布尔事实必须已知；NOT_FOUND/DEPENDENCY_UNAVAILABLE时未知字段允许为空，不能填0或false冒充已判定。消费者先检查FOUND再解释事实，未知时allowedActions为空。写命令沿用现有异常分类/统一响应，不新造成功包装或泄露越权对象存在性。

## 4. 事务和锁序

### 4.1 创建或解析

1. 从认证上下文或已验证BPM来源构造TrustedActor，检查ACC功能权限。
2. PROJ公共边界先锁根/项目并校验树版本和编辑范围；NODE来源还锁对应节点及执行契约，查明其确实属于此项目。
3. 锁定发布配置并冻结ACC规则。只有scopeCheck要求COM时，才取得COM有序订单行/范围及项目水位的完整快照。
4. 锁ACC项目/类型唯一身份。不存在则创建PENDING根及适用范围绑定；存在时DIRECT另一创建意图返回冲突。NODE/BPM解析只有在projectId、acceptanceType及ruleRevisionId均与现有根一致时才关联，不把“看起来相似”的规则当作兼容，不修改原来源、规则或报告；缺少新版规则快照的旧根不能自动升级。
5. 同事务记录根、必要绑定、命令幂等与审计/Outbox；失败整体回滚。与后续阶段准出是不同完成点，已成功创建的验收不能因下游暂未通过而被删除。

不同来源/意图不能靠同项目自动合并业务内容；显式关联仅复用同一项目验收身份。不能把scopeId、nodeId或新随机重试Key变成第二验收根。

### 4.2 完成与事实重验

按PROJ项目/树 → 适用COM范围 → 有序ACC活动根（含配置要求的初验）→ 有序当前报告 → PLT文件事实的顺序锁定。验证期望版本、配置条件及报告事实后，ACC CAS推进自身PENDING→COMPLETED，追加审计和幂等结果；不直接写PROJ任务/阶段。

新非原生节点完成只读取Owner事实并执行自身状态机；旧lockAndComplete路径保留原适用范围，不让旧接口参数改空后冒充新路径。COMPLETED的历史活动不因换版/撤销/范围变化被改写成未完成；当前acceptancePassed及依赖快照资格必须重新判定。

### 4.3 COM范围变化

COM成功产生qualified范围新版本时，先形成已锁定的BusinessAcceptanceScopeSnapshot，再调用ACC refreshForBusinessScope；ACC持锁后不回调PROJ/COM补锁。未确认/冲突范围不能伪装成qualified快照，也不得为下游验收改变ERP权威接收语义。

- 遍历该项目新来源且冻结scopeCheck=COM_CURRENT_SCOPE的验收根，不能只查已有绑定行，否则会漏掉“以空范围创建、后来首次分配”的项目。
- observedScopeVersion相同则重放；传入旧版本则拒绝；新版本追加缺失分配绑定并CAS更新跟踪水位。未改变的范围行复用已有绑定，不能因不同触发原因覆盖first binding_trigger或产生冲突。
- 不要求COM证据的项目验收不加入此订阅，不因合同/订单范围变化增加新的验收义务。
- 已发布报告保留原快照。当前版本不匹配时依赖判定立即失去资格；投影可复用既有范围事件刷新，但最终命令必须回源，不能依赖过期PASSED缓存。
- 原阶段来源绑定继续参与减量保护；原Q-FCOM-002关闭/解锁未裁决范围不扩展，本候选不自动写effective_to。

## 5. 来源事件与身份

先支持仓库已有的受管PMS Gate审批来源：BpmProcessInstanceStatusEvent只作信号，集成层回读已结束且APPROVE(2)的实际Flowable实例，核对tenant、真实processDefinitionId/key、businessKey、项目/Gate引用和冻结动作配置。执行身份来自实际发起人，执行前重新检查ACC权限及项目范围；撤权或来源缺失时不得借用Job账号。

触发匹配与可靠捕获只读PROJ配置/来源并写既有Outbox，不调用ACC创建或以ACC完成回滚审批。进入消费阶段后以冻结来源和业务意图幂等执行ACC open；反复通知、页面刷新或readiness求值不会创建第二根。其他审批来源须先提供真实Owner注册契约，不假定所有BPM businessKey都能解析成项目。

原事件没有完整上下文，禁止自行补tenant=1、借线程登录用户或信任客户端origin JSON。具体监听器必须验证发送点事务与Outbox捕获的一致性；该验证是实现/集成义务，不是本候选已通过的事实。

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
