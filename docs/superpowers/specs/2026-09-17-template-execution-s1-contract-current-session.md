# S1 接入契约与范围：模板规则、受控操作与统一执行中心

> 日期：2026-09-17
> 目标分支：`wrck/NPDMS` / `codex/liteflow-remediation`
> 读取基准：`3bd839ef62968625d78b48e8c7aeb098e296f059`
> 对应阶段：[最新实施计划](../plans/2026-09-17-template-execution-rules-and-controlled-operations-current-session.md)的 S1，不是项目生命周期 S1。
> 本阶段交付：实际接口对照、操作契约、规则时点、可信上下文和兼容边界。本文中的新增名称是后续实现合同，不表示接口已部署、注解已生效或业务测试已通过。

## 1. 范围及当前入口

只处理本会话的模板绑定—工作台办理—受控操作—结果消费链路。不继承其他计划的全模块迁移、指定试点、统一复制业务类或额外治理要求。下面列出已存在的直接消费入口，不据此建立新的业务模块实施任务。

### 1.1 已读取的源文件

| 引用 | 实际路径及用途 |
|---|---|
| SRC-TPL | [TemplateDesignerDocument](../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/TemplateDesignerDocument.java)：设计模型已有节点准入、完成、准出 ruleKey；WorkBindingSpec 还没有按操作区分的规则。 |
| SRC-TASK | [ProjectTaskBusinessController](../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/taskbusiness/ProjectTaskBusinessController.java)：任务业务上下文与候选对象读取。 |
| SRC-STAGE | [ProjectStageBusinessController](../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/stagebusiness/ProjectStageBusinessController.java)：阶段业务上下文、已有审批启动。 |
| SRC-GUARD | [ProjectBusinessExecutionApi](../../../pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectBusinessExecutionApi.java)：现有写入检查只有 projectId、Owner、对象类型和 selection，没有 operationCode。 |
| SRC-VIEW | [BusinessView registry](../../../yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/registry.ts)：已登记三个业务页面及一个平台动态表单视图。 |
| SRC-SS | [SiteSurveyEntityController](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/sitesurvey/entity/SiteSurveyEntityController.java)、[SiteSurveyEntityServiceImpl](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/sitesurvey/entity/SiteSurveyEntityServiceImpl.java)：工勘页面写入口、业务状态检查及项目重评发布。 |
| SRC-RA | [RequirementAnalysisEntityController](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/requirement/RequirementAnalysisEntityController.java)、[RequirementAnalysisEntityCommands](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisEntityCommands.java)：需求分析修订命令和既有幂等作用域。 |
| SRC-ACC | [AcceptanceReportController](../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/acceptancereport/AcceptanceReportController.java)：验收报告草稿、发布、撤销及当前权限/版本要求。 |
| SRC-EVENT | [EngineeringRuleReevaluationEvents](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/taskbusiness/EngineeringRuleReevaluationEvents.java)：当前发送 ProjectRuleReevaluationRequested，不是精确业务结果事件。 |
| SRC-SS-FACT | [SiteSurveyTaskBusinessObjectProvider](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/taskbusiness/SiteSurveyTaskBusinessObjectProvider.java)：SURVEY_CONFIRMED、SURVEY_ARCHIVED；任务和阶段事实接口。 |
| SRC-RA-FACT | [RequirementAnalysisEntityBusinessObjectProvider](../../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/taskbusiness/RequirementAnalysisEntityBusinessObjectProvider.java)：REQUIREMENT_ANALYSIS_COMPLETED；关联 objectId 使用修订身份。 |
| SRC-ACC-FACT | [AcceptanceTaskBusinessObjectProvider](../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskbusiness/AcceptanceTaskBusinessObjectProvider.java)：REPORT_EFFECTIVE 仅代表报告证据有效。 |

当前任务读取为 `GET /api/v1/pms/project-tasks/{taskId}/business/context`、`/candidates`，权限为 `pms:project-task:query`。其中 link/unlink 方法没有 HTTP 路由映射；本方案不恢复手工关联路由。

当前阶段读取为 `GET /api/v1/pms/projects/{projectId}/stages/{stageCode}/business/context`，权限为 `pms:project:query`；现有 `POST .../business/approvals` 及其 `pms:project:update` 权限保留，不改造成通用业务代理。

页面登记四元组保留：`SOL/SITE_SURVEY/SOL_SITE_SURVEY/1`、`SOL/REQUIREMENT_ANALYSIS/PROJ_REQUIREMENT_ANALYSIS/1`、`ACC/ACCEPTANCE/ACC_ACCEPTANCE_REPORT/1`、`PLATFORM/DYNAMIC_FORM_INSTANCE/PLATFORM_DYNAMIC_FORM/1`。后者是当前表单呈现适配，不能仅因已登记视图就宣称具备节点办理、结果生产或阶段完成 Provider；未接入的新能力不得假成功或隐式创建对象。

## 2. 具体操作与既有接口对照

下列 operationCode 是 S1 为后续登记确定的稳定标识，不是新增权限码。它们不改变已有 Controller 权限、业务状态、服务端对象范围或幂等作用域。列入清单不等于启用全入口强制控制；全入口控制必须在 S4 的实际统一应用入口显式声明。

### 2.1 工勘页面操作（SRC-SS）

以下路径前缀为 `/api/v1/pms/site-surveys`，Owner/实体为 `SOL/SITE_SURVEY`。

| operationCode | 当前请求 | 当前服务方法 | 保留的功能权限 |
|---|---|---|---|
| SOL.SITE_SURVEY.CREATE | POST /create | createSiteSurveyEntity | pms:eng-site-survey:create |
| SOL.SITE_SURVEY.UPDATE | PUT /update | updateSiteSurveyEntity | pms:eng-site-survey:update |
| SOL.SITE_SURVEY.DELETE | DELETE /delete?id={id} | deleteSiteSurveyEntity | pms:eng-site-survey:delete |
| SOL.SITE_SURVEY.CONFIRM | PUT /confirm?id={id} | confirmSiteSurveyEntity | pms:eng-site-survey:update |
| SOL.SITE_SURVEY.REJECT | PUT /reject?id={id} | rejectSiteSurveyEntity | pms:eng-site-survey:update |
| SOL.SITE_SURVEY.ARCHIVE | PUT /archive?id={id} | archiveSiteSurveyEntity | pms:eng-site-survey:update |

确认/驳回针对草稿，归档针对已确认对象；确认需要表单校验。更新携带业务 version；当前确认/驳回/归档 Controller 没有统一 If-Match、Idempotency-Key 头，不得把需求分析的接口特性误记到工勘。新办理通道的预期业务版本和幂等保障由 S4 适配，旧接口不被文档静默改义。

同一服务还有 `associateOutsourceRequest`、`releaseDeletedOutsourceRequest` 两个内部写入口，不能在验证注解覆盖时遗漏；这里只记录调用边界，不新增外包业务任务或 HTTP 路由。工期、地点等原业务协作不能被本次去耦一并删除。

### 2.2 需求分析修订操作（SRC-RA）

以下路径前缀为 `/api/v1/pms/requirement-analyses`，Owner/实体为 `SOL/REQUIREMENT_ANALYSIS`；四项操作均保留 `pms:requirement-analysis:manage`。

| operationCode | 当前请求 | 当前命令方法 | 当前幂等 scope |
|---|---|---|---|
| SOL.REQUIREMENT_ANALYSIS.CREATE | POST 根路径 | create | RA_ENTITY_CREATE |
| SOL.REQUIREMENT_ANALYSIS.SAVE | PATCH /{entityId}/revisions/{revisionId} | save | RA_ENTITY_SAVE |
| SOL.REQUIREMENT_ANALYSIS.COMPLETE | POST /{entityId}/revisions/{revisionId}/complete | complete | RA_ENTITY_COMPLETE |
| SOL.REQUIREMENT_ANALYSIS.COPY | POST /{entityId}/revisions/{revisionId}/copy | copy | RA_ENTITY_COPY |

创建需要 Idempotency-Key；保存、完成、复制需要 If-Match 和 Idempotency-Key。业务 entityId、revisionId、修订号和并发版本分别保留；COPY 表示生成可编辑修订，不表示再次完成。页面动作别名 CREATE_INITIAL_DRAFT/CREATE_DRAFT/PATCH_FORM/COMPLETE 由受信适配器映射，不能直接当唯一命令身份。附件仍经既有文件能力授权，不新增一个绕过文件策略的通用 payload 写入口。

### 2.3 验收报告操作（SRC-ACC）

以下路径前缀为 `/api/v1/pms/acceptances`，绑定实体身份仍为 `ACC/ACCEPTANCE`；四项操作均保留 `pms:acceptance:report:write`，不是新的项目完成权限。

| operationCode | 当前请求 | 当前命令方法 | 当前并发/幂等输入 |
|---|---|---|---|
| ACC.ACCEPTANCE_REPORT.CREATE_DRAFT | POST /{id}/report-versions | createDraft | If-Match 活动版本 |
| ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT | PATCH /{id}/report-versions/{versionId} | updateDraft | If-Match、expectedReportVersionNo |
| ACC.ACCEPTANCE_REPORT.PUBLISH | POST /{id}/report-versions/{versionId}/actions/publish | publish | If-Match、Idempotency-Key、报告及当前版本预期 |
| ACC.ACCEPTANCE_REPORT.REVOKE | POST /{id}/actions/revoke-current-version | revoke | If-Match、Idempotency-Key、当前报告版本预期 |

这些是已有验收活动上的报告操作，不是独立创建验收活动。当前 Provider 声明 REPORT_EFFECTIVE，未声明阶段完成能力；新阶段结果消费缺少实际 Provider 时应明确不可用，不恢复旧创建路由或推断“验收通过”。

## 3. 三类调用与操作登记契约

`ProjectBusinessOperationDescriptor` 由服务端真实适配器登记：operationCode、operationVersion、ownerContext、objectType、输入/输出类型、可用事实、支持的 PRE/POST 检查点。未知/重复登记、方法不匹配或引用不存在，在新契约发布/装载时拒绝。

登记不保存客户端可指定的 Java 类、Bean 名称、URL 或表达式处理器。模板引用 operationCode 和精确操作契约版本；Owner 权限动态检查，不以登记元数据授予权限。

| 调用类别（服务端判定） | 项目约束 | 身份来源与缺失处理 |
|---|---|---|
| INDEPENDENT | 未声明全入口受控时，只执行 Owner 原有授权/业务规则；不找活动任务 | 独立入口确认类别；省略 execution 不改变已声明受控方法的性质。 |
| WORKBENCH | 必须执行节点资格、绑定及配置的前后置检查 | 由项目办理路由确定；客户端预期身份须复核，不能降级独立执行。 |
| EXPLICIT_CONTROLLED | 已显式声明的业务统一入口全路径执行前后置约束 | 由方法声明确定；受信业务关联可解析，否则要求明确目标；空/多匹配拒绝。 |

控制类别不是请求 DTO 字段；不得提供 skipCheck、mode=INDEPENDENT 或规则覆盖参数。一个方法被模板引用不自动变成全入口受控；没有模板配置的显式受控调用也不能自动放行。是否将具体方法声明受控属于 S4 对实际入口的实施选择，S1 不批量启用清单中的操作。

## 4. S2 配置合同：既有节点规则与新增操作规则分开

保留现有节点 `admissionRuleKey/completionRuleKey/exitRuleKey` 和版本本地规则集合。新增能力在 WorkBinding 内以独立的可选 `operationContract` 表达，阶段、任务共用；不得把现有 `PermissionRequirement.policySnapshot` 由描述信息改作强制规则。

operationContract 的版本是该子契约版本，不自动升级整个模板 Schema。逻辑格式如下；示例不是种子或运行默认值：

```json
{
  "version": 1,
  "operations": [
    {
      "operationCode": "SOL.SITE_SURVEY.CONFIRM",
      "operationVersion": 1,
      "pre": { "mode": "RULE", "ruleKey": "confirm-admission" },
      "post": { "mode": "NONE" }
    }
  ]
}
```

PRE/POST 必须各自明确为 `NONE` 或 `RULE + ruleKey`；NONE 不允许同时携带 ruleKey，RULE 必须解析为当前冻结规则集合中的条件规则。新子契约中缺失 pre/post、重复 operationCode、未知版本和不支持的检查点均拒绝。子契约完全缺失时走旧解释器，而不是把旧项目全部放行或全部纳入新控制。

S2 发布校验同时检查操作与业务对象兼容、精确操作版本、事实来源、规则类型和引用。POST 只能使用本事务可判定的输入/结果或一致事实；依赖未来异步事件、其他未完成任务的条件应放在节点完成/准出，不能成为操作回滚条件。若当前规则引擎不能解析本地结果事实，不能放过预检或新建另一套表达式语言冒充支持。

序列化不得给旧快照补空 operationContract；旧语义哈希、已发布快照及当前运行项目不变。新配置只能通过明确的新发布/计划版本进入执行。呈现仍单独引用 businessViewSnapshot，视图开关不替代业务结果规则或显式操作控制策略。

## 5. 可信上下文及新接口边界

以下接口名与新增路由是后续实现合同，S1 不注册 Controller 或空 Provider。

| 接口 | 输入/输出 | 使用边界 |
|---|---|---|
| ProjectExecutionCapabilityApi.inspect | NodeQuery → 节点摘要、业务/项目/交集动作、每动作前置结果、阻塞码、观察版本、视图描述 | 只读；Owner 校验对象可见性；不能查询即开始办理。 |
| ProjectExecutionOperationApi.execute | BoundOperationCommand → OwnerResultRef、命令身份、提交结果 | 新项目办理通道；受信注册适配器调用 Owner 命令，不反射代理任意业务。 |
| ProjectControlledOperationGuard | 方法声明 + TrustedInvocation → 前后置检查 | 仅显式受控统一应用入口；与工作台共用同一契约解析，不用 ThreadLocal 布尔值跳过嵌套操作。 |
| ProjectExecutionResultConsumer.accept | 已提交 BusinessResultEvent → 接收/各目标证据处理结果 | 独立消费；再通过既有项目正式状态命令推进，不回写 Owner 正文。 |

新任务查询：`GET /api/v1/pms/project-tasks/{taskId}/business/execution-context`；新阶段查询：`GET /api/v1/pms/projects/{projectId}/stages/{stageCode}/business/execution-context`。不覆盖旧 `/context` 响应语义。阶段由路径编码解析实际 stageId，内部统一使用 nodeKind + nodeId，不要求虚拟任务。

新办理路由分别在上述任务/阶段 business 前缀下使用 `POST /operations/{operationCode}`。请求采用命令信封：预期 executionId/planVersionId/contractId/contractVersion/执行版本、业务 objectId/适用 revisionId/expectedBusinessVersion，以及类型化业务输入；Idempotency-Key 走请求头。创建命令没有既存 objectId 时，必须先由 Owner 验证目标项目与对象范围，生成后在 POST 核对结果归属。

tenantId、actorId 由服务端认证上下文提供；路径中的 operationCode 必须命中服务端登记并与实际处理方法相同。客户端不能自报 ownerActions、writable、授权结果或控制类别。S4 在调用方事务内重验计划、轮次、对象和版本，不能信任 GET 返回的观察值。任务与阶段身份不能同时出现；需要受控上下文时不能只传 projectId 让服务端取第一条匹配。

读取响应将 `ownerActions`、`projectActions`、`allowedActions` 分开。允许动作是授权交集及明确前置满足的结果；后置/完成依赖尚未产生事实时标记未评估，不提前返回通过。UNKNOWN、规则缺失、版本冲突、上下文缺失、多目标和明确不满足分别给出安全原因；项目规则不能补足 Owner 缺少的权限。

新统一办理入口必须在应用服务层调用 Owner 的授权检查，不能仅依赖旧 Controller 的 @PreAuthorize，尤其不能以绕开旧 Controller 为代价丢掉原权限。

## 6. 五个检查时点和事务协议

| 检查点 | 规则归属与执行时点 | 失败语义 |
|---|---|---|
| ADMISSION | 项目节点正式激活/开始前，使用原 admissionRuleKey | 该节点不进入新状态；不影响普通独立业务。 |
| PRE | WORKBENCH/EXPLICIT_CONTROLLED 的业务写入前 | 本次命令拒绝，不执行业务副作用。 |
| POST | 同一可回滚事务内业务操作后、提交前 | 业务、办理记录、成功幂等及 Outbox 一起回滚。 |
| COMPLETION | 业务结果已提交或其他相关事实变化后，使用 completionRuleKey | 结果保留，节点等待；不否定原业务成功。 |
| EXIT | 项目结束节点/推进前，使用 exitRuleKey | 项目不推进，不回滚已提交业务。 |

明确无额外规则与无法取得规则不同。校验入口只读路径和写入路径共用规则解释，但写入必须重新取事实和必要锁/版本保护。

S4 应保持“原有安全重放判定 → 必要执行锁/身份重验 → PRE → Owner 命令 → POST → Outbox → 提交”。已成功同键同请求在安全授权允许重放时返回原结果，不重复执行或因当前节点已完成而把原成功变成新失败；异请求冲突。新命令不能因复用原结果逻辑而漏做 Owner 授权。

受控操作与计划生效/返工遵守同一并发协议：返工先提交则旧身份拒绝；业务先合法提交则保留原身份。不能使用 REQUIRES_NEW 提前提交被保护的业务副作用。当前工勘 CREATE/UPDATE 可能调用多个同事务业务能力，新适配器不能在未核对事务参与之前宣称整笔可回滚。

Spring 代理存在自调用绕过限制，事务通知顺序也影响 POST 是否仍位于提交前；S4 必须以真实代理与事务验证，不凭注解外观放行。外部不可逆操作不属于本地事务回滚保证；只暴露其限制，不新增分布式事务平台。

## 7. S5 业务结果合同与旧事实适配

新结果信封至少包含 eventId/eventType/eventVersion、受信租户与 Owner、objectType/objectId、businessFactVersion、resultId/resultCode、项目/业务范围、发生时间及命令关联。适用业务修订或批次单独表达。项目执行身份不是领域结果的必填字段，受控办理的证据归属由集成侧保存。

| 结果来源 | 已读取的真实事实语义 | 不允许的转换 |
|---|---|---|
| SRC-SS-FACT | SURVEY_CONFIRMED 对应状态 1 或 3；SURVEY_ARCHIVED 对应状态 3 | 不解释为 PRE-02 实施就绪；不把旧 factVersion 中的 executionId 搬成领域结果必填。 |
| SRC-RA-FACT | REQUIREMENT_ANALYSIS_COMPLETED 对应 FROZEN 修订且有冻结主体/时间；现有 objectId 是修订 ID | 不把实体 ID、修订 ID 和任务轮次混成一个身份；草稿保存/COPY 不是完成。 |
| SRC-ACC-FACT | REPORT_EFFECTIVE 为当前报告及其证据有效 | 不等同验收活动完成或验收明确通过；当前有效性须继续重验。 |
| SRC-EVENT | ProjectRuleReevaluationRequested 仅唤醒项目重新读取事实 | 不能改个名称就当作有精确对象版本/结果的领域事件；保留旧消费者解释。 |

S5 复用事务 Outbox；事件接收、各目标证据采纳、节点正式转换分别幂等。无订阅/证据不适用不是传输失败；先有结果后有订阅必须通过已有结果核对衔接持续接收。旧确认不能覆盖较新失效；返工与换版按明确结果身份/证据政策解释，不按到达时间猜轮次。

后台消费使用明确的系统事实契约，不携带伪造用户去获取 Owner 正文；交互查询始终使用当前用户权限。历史结果或来源事件缺失时只报告不可用，不合成业务成功事实。

## 8. 兼容边界和后续阶段输入

S1 不更改旧 HTTP 入口、Bean 装配、业务权限或当前强制守卫；不会仅靠文档让普通业务自动获得独立办理。S2/S4 通过明确的新配置版本与接入路径实现行为变化，既有冻结契约继续原解释。没有新子契约不等于通过；未启用显式控制也不等于批量删除旧保护。

S2 使用第 4 节的操作子契约；S3 使用第 5 节的只读能力接口；S4 使用三类调用及同事务前后置协议；S5 使用第 7 节结果语义；S6 接入新办理通道及动作交集，查询或待办刷新不执行命令、不销毁未保存的业务表单。任何阶段不得把“可显示页面”直接当成“可执行所有业务动作”。

本次完成标准仅是 S1 的接口对照与兼容契约明确，不是 S2～S6 代码完成。后续阶段按最新计划逐阶段验证并各提交一次，不以全库审计、模块迁移或恢复旧路由为前置。

## 9. 本阶段核验记录

已读取基准 HEAD 的相关 Controller、业务命令、Provider、模板模型和视图注册；确认 14 个现有页面写操作对应的请求、方法、权限及适用版本/幂等输入，并记录两个内部工勘写入口。清单不是全仓库或全业务审计。

本阶段仅进行源代码对照、操作标识唯一性、3 类调用/5 个规则时点一致性、文档链接与空白差异检查。没有运行生产 Java/TypeScript 编译、服务端事务测试、数据库并发测试、API 或浏览器验收，T01～T20 仍待执行。执行环境限制及阶段进度统一记在最新计划 8.2。

参考：[Spring AOP 代理](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)、[Spring 事务注解](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)；仅用于 S4 代理/事务验证依据，不引入新依赖或框架升级。
