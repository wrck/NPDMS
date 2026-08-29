# ADR-0041：满意度问卷、判定与交付件来源

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 日期：2026-08-30<br>
> Requirement：`ACC-02@V1`、`ACC-04@V1`（仅满意度来源）<br>
> 前置批准：F-ACC-002边界与最近Gate定位独立裁决GO（基线`7f3e3c62`）

## 背景

PRD要求在项目冻结模板配置的业务时点形成满意度领域任务和冻结问卷，V1支持二维码、手工受控链接与现场协助；客户答卷、签字、附件、评分和整改版本不得覆盖。只有有效且达标结果可以进入ACC-04满意度来源，归档失败保留结果并补偿。现有SDS只给出四张粗粒度候选表，尚未锁定触发Owner、外发身份、文件公共事实、当前结果、归档补偿和消费者Fact。

旧`pm_cl_quesnaire_*`缺少可证明的必答规则、客户签字、文件公共版本、项目任务触发和稳定通过值映射；`pm_cl_callback*`及`pm_subcontract_project_callback`只能证明部分旧关系。F-ACC-002不得从这些字段推断客户答案、签字或通过结果。

## 候选决策

1. ACC拥有`SatisfactionCollectionTask`、问卷模板及发布修订、冻结问卷、访问授权、答卷、签字/附件公共文件事实、不可变判定和整改版本。PROJ继续拥有ProjectTask、项目模板任务快照、业务时点与项目范围；PLT Todo完成不表示客户已提交或结果达标。
2. ACC以`SatisfactionQuestionnaireTemplateApi.resolvePublished`按项目类别、签约方式、实施方式、业务用途和适用时点精确解析一个已发布修订。PROJ在项目创建事务内调用并把`templateId/templateRevisionId/templateVersion/ruleVersion/threshold`冻结到ProjectTask；零匹配或多匹配整批失败，不按任务名、任务码或默认模板推断。
3. 当冻结业务时点到达时，PROJ或持有该业务时点的受信Owner调用`SatisfactionTaskInitializationApi.initialize`。ACC必须通过PROJ `ProjectWorkBindingFactApi`重验同租户ProjectTask、项目、冻结模板事实、时点和当前责任人；接口以`MANDATORY`加入调用方事务，按`projectTaskId + triggerOwnerContext + triggerFactId + triggerFactVersion`幂等创建唯一任务和问卷。来源Owner未交付的调用方只保留接口，不在本Feature实现。
4. V1外发只创建ACC受控访问授权：同一ACTIVE问卷可生成手工链接，二维码只是该链接的表示；令牌只在创建响应中返回一次，库内仅保存不可逆摘要、授权版本、有效期和状态，日志/审计不得保存完整令牌。现场协助使用已认证且具`collect`权限的项目成员，不伪造客户账号。INT-10短信/邮件和INT-05钉钉仅保留V2发送接口，送达不等于提交或通过。
5. 客户提交以`questionnaireId + requestId`幂等。ACC先锁定访问授权、任务和问卷，再通过PLT公共文件接口重验本次签字与附件集合，随后只追加Response和Result；重复同键同载荷返回首次结果，同键异载荷冲突。必答缺失、签字无效或文件范围不一致形成不可变失败判定，不能通过修改原答卷修复。
6. 满意度文件策略键固定为`ACC/SATISFACTION_RESPONSE/{responseId}/SATISFACTION_SIGNATURE`和`SATISFACTION_ATTACHMENT`，判定文档键为`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT`，归档键为同Result下的`SATISFACTION_ARCHIVE`。ACC只保存PLT公共`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/sha256`，不得保存PLT内部FileVersion/FileReference主键。
7. PLT加性公开受信`FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload`，仅供ACC已验证的ACTIVE访问授权上传签字或附件；命令携带grantId/grantVersion、目标策略键、幂等键和安全文件元数据，不伪造登录用户。ACC文件策略Provider以授权版本、问卷/Response预分配身份、租户和项目范围判定，PLT仍执行内容、大小、类型、病毒、引用版本和审计规则。内部现场协助继续使用现有认证上传REST。
8. Result是不可变判定。`passed=true`只在答卷有效、必答完整、签字有效且分数达到冻结阈值时产生；Result状态为`EFFECTIVE/INVALIDATED`，失效关闭区间但不删除。相同`collectionKey`至多一个未关闭的有效达标结果；失效不恢复旧结果。未达标或失效后的整改必须提供整改事实，创建新Task、新Questionnaire和新Result，并通过`priorTaskId/priorQuestionnaireId`形成链。
9. 判定成功后ACC生成不可变满意度结果文档，并通过`PlatformCommandExecutionApi`与Result事务同提交写`SatisfactionResultVersionChanged`。ACC-04投影只处理有效达标结果：复用`acc_project_deliverable`唯一应交根及F-ACC-001已建立的来源版本/附件表，来源类型固定`SatisfactionResult`，保存结果文档、签字和客户附件的完整有序公共文件事实；未达标/失效只保留来源历史并清空当前指针，不得形成当前有效满意度交付件。
10. 来源投影或PLT归档失败不回滚Result，来源保持`PENDING_COMPENSATION`。归档使用持续ACTIVE的结果文件集合和独立`SATISFACTION_ARCHIVE`集合；成功后才写`ARCHIVED`，历史下载继续读取ACTIVE集合。归档actor冻结为形成Result时的当前责任人，PLT按该用户重验既有`pms:file:archive`、项目/文件范围和租户；撤权或Provider不可用继续待补偿，不伪造Job用户。
11. `SatisfactionResultOutboxDeliveryJob`只领取`SatisfactionResultVersionChanged`，ACC-04投影事务成功后才`markDelivered`，失败按同一retryCount执行`scheduleRetry`。`ClosureGateRecheckRequested`和未来SUB重校验请求不由该Job领取或误标成功。
12. ACC公开`SatisfactionResultFactApi.inspect/lockAndRevalidate`，返回稳定task/questionnaire/response/result、模板/规则/阈值、来源业务对象及各自版本，以及`passed/resultStatus/archiveStatus`。CLO-01和SUB-03未来只消费该不可变事实；本Feature不实现闭环或付款门禁。
13. 最小权限键为`pms:acceptance:satisfaction:query/manage/collect/export/download`。服务端分别执行项目范围、责任人范围、字段范围、FileBusinessScope和租户隔离；客户令牌仅能访问其唯一问卷及上传/提交动作。角色—权限映射保持正式授权配置，具备全部权限通过授权关系实现，不删除鉴权。

## 状态、事务与锁序

- Task在业务时点到达并初始化后从`PENDING_ASSIGNMENT -> PENDING_COLLECTION -> PENDING_DECISION -> FAILED|PASSED -> PENDING_ARCHIVE -> ARCHIVED`流转；业务时点到达前只有PROJ冻结ProjectTask，不预建ACC领域Task；整改不回退旧Task，而是新建revision。
- Questionnaire：`ACTIVE -> SUBMITTED|INVALIDATED|EXPIRED`；一个Questionnaire只接受一个首次有效requestId结果，失败判定也不可覆盖。
- 触发锁序：PROJ ProjectTask/WorkBinding事实→ACC Task→Questionnaire。
- 提交锁序：ACC AccessGrant→Task→Questionnaire→PLT签字/附件引用→Response→Result→Outbox。
- 归档锁序：ACC交付件根/来源版本→PLT结果文件集合及归档集合→ACC归档投影。任一事务内不得反向加锁。

## P3-E09 Feature-forward差量

| 表 | 差量 | 关键约束 |
|---|---|---|
| `acc_satisfaction_questionnaire_template` | 新建ACC模板根 | `uk(tenant_id, template_code)`；当前发布指针与草稿分离 |
| `acc_satisfaction_questionnaire_template_revision` | 新建只追加修订，保存五维适用条件、优先级、题目JSON、阈值、规则版本、发布区间 | `uk(tenant_id, template_id, revision_no)`；同一确定输入至多一个最高优先级发布修订，歧义失败 |
| `proj_project_task` | 加性冻结满意度模板修订ID/版本、规则版本和阈值 | 仅`satisfaction_timing`非空任务允许；值必须来自ACC解析Fact，不由PROJ推断 |
| `acc_satisfaction_collection_task` | 增加`project_task_id/trigger_owner_context/trigger_fact_id/trigger_fact_version/collection_key/assigned_by_user_id` | 触发事实复合唯一；整改revision只追加 |
| `acc_satisfaction_questionnaire` | 增加`questionnaire_status/access_scope_version` | 状态受控；冻结题目/阈值/规则不可更新 |
| `acc_satisfaction_access_grant` | 新建受控链接授权 | token摘要唯一；状态`ACTIVE/CONSUMED/REVOKED/EXPIRED`；完整令牌不落库 |
| `acc_satisfaction_response` | 移除泛化`signature_ref/attachment_refs_json`目标语义，增加提交渠道、客户联系人引用和协助人 | `uk(tenant_id, questionnaire_id, request_id)`；答卷只追加 |
| `acc_satisfaction_response_file` | 新建签字/客户附件公共文件事实 | role=`SIGNATURE/ATTACHMENT`；签字恰一条，附件按sequence唯一 |
| `acc_satisfaction_result` | 增加`collection_key/result_status/effective_from/effective_to/current_marker/archive_actor_user_id/deliverable_source_version_id`及补偿字段，Feature目标不使用旧模型`archive_artifact_id/archive_payload_sha256` | 结果只追加；`uk(tenant_id, collection_key, current_marker)`只标记未关闭EFFECTIVE且passed的当前结果 |
| `acc_satisfaction_result_file` | 新建结果文档及冻结来源文件集合 | role=`RESULT_DOCUMENT/SIGNATURE/ATTACHMENT`；完整有序PLT公共文件事实 |
| `acc_project_deliverable*` | 直接复用F-ACC-001根、来源版本、来源附件和补偿字段 | 仅`source_object_type=SatisfactionResult`；当前指针只指有效达标结果 |

当前核心DDL不原地修改；未来Technical Plan只能创建新的前向Flyway。P3-E09结论为`FEATURE_FORWARD_DELTA_REQUIRED`，本ADR获独立GO前不得创建Feature Spec。

## 旧载体复用与迁移判定

| 载体 | 判定 | 边界 |
|---|---|---|
| PROJ `satisfaction_timing`模板定义/ProjectTask快照、ProjectWorkBindingFactApi、ProjectScopeApi | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 复用时点、任务Owner、范围和锁定模式；只增加ACC模板解析Fact冻结与满意度WorkBinding，不建立第二套PROJ任务 |
| F-ACC-001 `acc_project_deliverable*`、PLT文件公共事实/归档/Access Ticket、Outbox投递模式 | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 直接复用根、来源历史、文件身份、归档与重试；只加满意度策略键和外部业务授权上传，不改变报告来源行为 |
| `pm_cl_quesnaire_template_*`、`pm_cl_quesnaire_result_*` | `DO_NOT_REUSE_RUNTIME / PRESERVE_RAW_FOR_AI_MIG_000` | 不进入F-ACC-002正向写路径；字段映射和值域未确认前不迁为客户答案、签字、有效Result或当前交付件 |
| `pm_cl_callback*`、`pm_subcontract_project_callback`、`pm_presales_project_callback`、`pm_project_warranty_callback`、`pm_project_maintenance*`、`pm_project_supervision`、`pm_daily_report`中的问卷引用/缓存分数/回访状态 | `DO_NOT_REUSE / PRESERVE_RAW_FOR_AI_MIG_000` | 只作为待确认来源关系或汇总证据；不得反推业务时点、客户提交、签字、整改、评分或通过结果 |
| 旧竣工/关项页面的`satisfactionScore` | `DO_NOT_REUSE` | 保持旧页面和字段不变，不作为ACC-02 Result、阈值或CLO/SUB门禁事实 |

## 明确排除

- 不实现ACC-04其他五类来源、统一批量下载、CLO-01、SUB-03或其消费结果。
- 不实现INT-10/INT-05第三方发送、送达回调或连接器；只保留V2接口边界。
- 不固定角色—权限映射，不修改Yudao基础平台，不把Todo完成解释为满意度完成。
- 不批准Feature Ready、Technical Plan、产品代码、Flyway、历史迁移、SIT/UAT或Release。
