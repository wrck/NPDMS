# ADR-0041：满意度问卷、判定与交付件来源

> 状态：`ACCEPTED`<br>
> Result失效命令补充：`ACCEPTED`（整改提交`c1e7354c`独立复审GO）<br>
> Result生成文件Owner补充：`ACCEPTED`（整改提交`afa37d66`独立复审GO）<br>
> 可配置问卷与确定性计分补充：`ACCEPTED`（整改提交`4ecc9d3b`独立复审GO）<br>
> 日期：2026-08-30<br>
> Requirement：`ACC-02@V1`、`ACC-04@V1`（仅满意度来源）<br>
> 前置批准：F-ACC-002边界与最近Gate定位独立裁决GO（基线`7f3e3c62`）

## 背景

PRD要求在项目冻结模板配置的业务时点形成满意度领域任务和冻结问卷，V1支持二维码、手工受控链接与现场协助；客户答卷、签字、附件、评分和整改版本不得覆盖。只有有效且达标结果可以进入ACC-04满意度来源，归档失败保留结果并补偿。现有SDS只给出四张粗粒度候选表，尚未锁定触发Owner、外发身份、文件公共事实、当前结果、归档补偿和消费者Fact。

旧`pm_cl_quesnaire_*`缺少可证明的必答规则、客户签字、文件公共版本、项目任务触发和稳定通过值映射；`pm_cl_callback*`及`pm_subcontract_project_callback`只能证明部分旧关系。F-ACC-002不得从这些字段推断客户答案、签字或通过结果。

## 候选决策

1. ACC拥有`SatisfactionCollectionTask`、问卷模板及发布修订、冻结问卷、访问授权、答卷、签字/附件公共文件事实、不可变判定和整改版本。PROJ继续拥有ProjectTask、项目模板任务快照、业务时点与项目范围；PLT Todo完成不表示客户已提交或结果达标。
2. ACC以`SatisfactionQuestionnaireTemplateApi.resolvePublished`按项目类别、签约方式、实施方式、业务用途和适用时点精确解析一个已发布修订。PROJ在项目创建事务内调用并把`templateId/templateRevisionId/templateVersion/ruleVersion/threshold`冻结到ProjectTask；零匹配或多匹配整批失败，不按任务名、任务码或默认模板推断。
3. 当冻结业务时点到达时，PROJ或持有该业务时点的受信Owner调用`SatisfactionTaskInitializationApi.initialize`。ACC必须通过PROJ `ProjectWorkBindingFactApi`重验同租户ProjectTask、项目、冻结模板事实、时点和当前责任人；接口以`MANDATORY`加入调用方事务。首次任务由ACC分配稳定`collectionKey`和`taskRevisionNo=1`，其`sourceOwnerContext/sourceObjectType/sourceObjectId/sourceObjectVersion`与`triggerOwnerContext/triggerObjectType/triggerFactId/triggerFactVersion`均冻结原始业务时点Fact。`source*`在整个收集链不变，只表达原始业务对象；`trigger*`只表达本次revision获准创建的事实。来源Owner未交付的调用方只保留接口，不在本Feature实现。
4. V1外发只创建ACC受控访问授权：同一ACTIVE问卷可生成手工链接，二维码只是该链接的表示；令牌只在创建响应中返回一次，库内仅保存不可逆摘要、授权版本、有效期和状态，日志/审计不得保存完整令牌。现场协助使用已认证且具`collect`权限的项目成员，不伪造客户账号。INT-10短信/邮件和INT-05钉钉仅保留V2发送接口，送达不等于提交或通过。
5. 客户提交以`questionnaireId + requestId`幂等。ACC先锁定访问授权、任务和问卷，再通过PLT公共文件接口重验本次签字与附件集合，随后只追加Response和Result；重复同键同载荷返回首次结果，同键异载荷冲突。必答缺失、签字无效或文件范围不一致形成不可变失败判定，不能通过修改原答卷修复。
6. 满意度文件策略键固定为`ACC/SATISFACTION_RESPONSE/{responseId}/SATISFACTION_SIGNATURE`和`SATISFACTION_ATTACHMENT`，判定文档键为`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT`，归档键为同Result下的`SATISFACTION_ARCHIVE`。ACC只保存PLT公共`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/sha256`，不得保存PLT内部FileVersion/FileReference主键。
7. PLT加性公开受信`FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload`，仅供ACC已验证的ACTIVE访问授权上传签字或附件；命令携带grantId/grantVersion、目标策略键、幂等键和安全文件元数据，不伪造登录用户。ACC文件策略Provider以授权版本、问卷/Response预分配身份、租户和项目范围判定，PLT仍执行内容、大小、类型、病毒、引用版本和审计规则。内部现场协助继续使用现有认证上传REST。
8. Result是不可变判定。`passed=true`只在答卷有效、必答完整、签字有效且分数达到冻结阈值时产生；Result状态为`EFFECTIVE/INVALIDATED`，失效关闭区间但不删除。相同`collectionKey`至多一个未关闭的有效达标结果；失效不恢复旧结果。未达标或失效后，`recollect`必须在ACC内先追加唯一`SatisfactionRemediationFact`，精确引用前一失败/失效Result和整改证据，再原子创建同一`collectionKey`、`taskRevisionNo=prior+1`的新Task与Questionnaire；新Task的`source*`复制首任务原始来源，`triggerOwnerContext=ACC/triggerObjectType=SatisfactionRemediationFact`且`triggerFactId/version`精确引用该整改Fact，并通过`priorTaskId/priorQuestionnaireId`形成链。同一整改Fact重放返回已创建revision，异载荷冲突；客户端不得提供或覆盖collectionKey/revision/source/trigger身份。
9. 判定成功后ACC生成不可变满意度结果文档，并通过`PlatformCommandExecutionApi`与Result事务同提交写`SatisfactionResultVersionChanged`。ACC-04投影只处理有效达标结果：先以同租户、同项目和ProjectTask稳定码`T-SAT-SURVEY`重验来源任务，再精确命中唯一`acc_project_deliverable(deliverable_code=D-SAT-REPORT, task_code=T-SAT-SURVEY)`根，复用F-ACC-001来源版本/附件表，来源类型固定`SatisfactionResult`，保存结果文档、签字和客户附件的完整有序公共文件事实。根缺失、重复、项目或任务身份不一致时投影失败并进入补偿；禁止按中文名称、其他交付件或任选根推断。未达标/失效只保留来源历史并清空当前指针，不得形成当前有效满意度交付件。
10. 来源投影或PLT归档失败不回滚Result，来源保持`PENDING_COMPENSATION`。归档使用持续ACTIVE的结果文件集合和独立`SATISFACTION_ARCHIVE`集合；成功后才写`ARCHIVED`，历史下载继续读取ACTIVE集合。归档actor冻结为形成Result时的当前责任人，PLT按该用户重验既有`pms:file:archive`、项目/文件范围和租户；撤权或Provider不可用继续待补偿，不伪造Job用户。
11. `SatisfactionResultOutboxDeliveryJob`只领取`SatisfactionResultVersionChanged`，ACC-04投影事务成功后才`markDelivered`，失败按同一retryCount执行`scheduleRetry`。`ClosureGateRecheckRequested`和未来SUB重校验请求不由该Job领取或误标成功。
12. ACC公开`SatisfactionResultFactApi.inspect/lockAndRevalidate`，返回稳定task/questionnaire/response/result、模板/规则/阈值、来源业务对象及各自版本，以及`passed/resultStatus/archiveStatus`。CLO-01和SUB-03未来只消费该不可变事实；本Feature不实现闭环或付款门禁。
13. 最小权限键为`pms:acceptance:satisfaction:query/manage/collect/export/download`。服务端分别执行项目范围、责任人范围、字段范围、FileBusinessScope和租户隔离；客户令牌仅能访问其唯一问卷及上传/提交动作。角色—权限映射保持正式授权配置，具备全部权限通过授权关系实现，不删除鉴权。
14. V1由ACC Owner提供`POST /api/v1/pms/satisfaction-results/{id}/actions/invalidate`关闭当前有效达标Result。命令只接受服务端认证用户，要求既有`pms:acceptance:satisfaction:manage`、PROJ `ProjectScopeApi(PROJECT_EDIT)`通过、非空`Idempotency-Key`、`expectedResultVersion`及失效原因；客户端不得提供tenant、操作者、collectionKey或来源身份。只允许`EFFECTIVE + passed=true + effective_to is null + current_marker=1`迁为`INVALIDATED`，原子写`effective_to/invalidated_by_user_id/invalidated_at/invalidation_reason_code/invalidation_reason_summary`并清空current marker；评分、答卷、签字、附件、Task和Questionnaire历史均不改写，也不重开旧Task。相同幂等键同载荷返回原结果，异载荷冲突，非当前、版本不符或范围不符均零写入。
15. Result失效与`SatisfactionResultVersionChanged(changeType=INVALIDATED)`通过`PlatformCommandExecutionApi`同一ACC事务提交；事件冻结失效原因、操作者和时间。ACC-04投影处理任何`RECORDED`前必须以Result ID/version调用`SatisfactionResultFactApi`重验ACC Owner当前事实：只有该精确版本仍为EFFECTIVE且passed、且不存在更新的当前Result时才能置CURRENT；若已INVALIDATED或已有更新结果，延迟/重试的旧RECORDED只能幂等保留为非当前历史和历史归档输入，不得设置或恢复根当前指针。INVALIDATED仅在应交根当前指针仍指向该Result及版本时清空指针并把来源关系置`REVOKED`，不得清除更新来源。历史来源、ACTIVE文件引用及既有归档记录保持可下载，待补偿的该来源仍可完成历史归档。Result事务提交后`SatisfactionResultFactApi`立即返回INVALIDATED，未来CLO/SUB消费者必须重验Owner事实。整改重收继续以该INVALIDATED Result追加RemediationFact和下一revision。
16. PLT在现有`FileArtifactApi`加性公开`createGeneratedBusinessFile`，仅供ACC生成`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT`。命令冻结当前租户、Result形成时当前责任人`actorUserId`、稳定`operationId`、精确目标键、`scopeVersion`、安全文件元数据及受大小限制的服务端生成内容；actor、目标和scopeVersion不得由客户、Job线程或伪造Web登录上下文覆盖。PLT以`MANDATORY`加入ACC判定外层MySQL事务，按actor重验既有`pms:file:upload`、租户和FileBusinessScope，复用现有内容类型/大小/SHA-256/扫描、对象存储、Artifact/Version/Reference及审计链并返回唯一`FileArtifactVersionFact`。同Result只允许一条RESULT_DOCUMENT；`operationId+规范化请求摘要`同载荷返回原事实、异载荷冲突。
17. 生成内容写对象存储前，PLT复用现有`FileUploadSession`形成可补偿的持久会话和稳定operation绑定；Artifact/Version/Reference与ACC Result、ResultFile、成功幂等事实、Result Outbox在外层事务同成同败。若对象已写入而外层事务回滚，会话恢复为可重试/待补偿状态：同operation和摘要重用原会话及存储回执，不创建第二Artifact/Reference；放弃或校验失败由既有`FileUploadCompensationService`删除未引用对象并终止会话，清理失败继续可对账重试。PLT授权、范围、内容、存储或生成任一步失败时不得写Result、ResultFile、成功幂等事实或Result Outbox；已提交Response保持不变，Task保持`PENDING_DECISION`，允许同一业务意图重试。
18. 模板修订的`frozen_question_json`使用唯一`schemaVersion=1`配置包：根只允许`schemaVersion/questions/scoring`；题目按顺序保存稳定`code/title/type/required`及类型参数，计分配置保存`ruleVersion/strategy/scoreMin/scoreMax/precision/roundingMode/threshold`。V1的scoreMin固定为0，scoreMax由题目分值及策略确定并在发布时校验；受控题型仅`SINGLE_CHOICE/MULTIPLE_CHOICE/RATING/TEXT`，受控策略仅`SUM_V1/WEIGHTED_AVERAGE_V1`，舍入仅`HALF_UP/HALF_EVEN/DOWN`；不得执行脚本、表达式或客户端算法标识。
19. `SINGLE_CHOICE`答案为一个optionCode；`MULTIPLE_CHOICE`答案为去重optionCode数组，`minSelections/maxSelections`均为必填非空整数且满足`1<=minSelections<=maxSelections<=options数量`，未回答的可选题以整题缺失表达，已回答时选择数量必须落入该闭区间；`RATING`答案为一个受控等级code。三类题目的每个option保存稳定code/label及非负decimal score。`TEXT`答案为字符串，`minLength/maxLength`均为必填非空整数且满足`0<=minLength<=maxLength`，不参与计分。类型不适用的参数必须缺失，不得以null或默认值代替；题目/option编码在修订内唯一，至少一题参与计分。
20. `SUM_V1`把所有计分题得分相加且禁止配置weight；`WEIGHTED_AVERAGE_V1`要求每个计分题具有正weight，按`sum(questionScore*weight)/sum(weight)`计算。单选/量表题得分为所选option score，多选题得分为所选option score算术平均；未回答的可选计分题及缺失的必答计分题按0计入，文本题不进入公式。所有中间值使用十进制精确运算，只在最终总分按`precision(0..2)`和`roundingMode`舍入一次，再以舍入后值与threshold比较；必答缺失或签字无效时无论分数均`passed=false`。
21. 模板发布必须验证配置包字段封闭、编码唯一、类型参数完整且非空、ruleVersion非空、option与非负score合法、MULTIPLE_CHOICE选择区间、TEXT长度区间、SUM无weight、加权策略weight完整、precision/roundingMode受控。单选/量表题最大可达分为最大option score；多选题最大可达分为所有满足`minSelections..maxSelections`的合法去重选择集合中option score算术平均的最大值，不得用最大单个option score代替；因此最低选2项且分值为100/0时该题最大可达分为50，threshold=80必须拒绝发布。`SUM_V1.scoreMax`必须等于各计分题最大可达分之和，`WEIGHTED_AVERAGE_V1.scoreMax`必须等于各计分题最大可达分的加权平均，threshold必须位于0..scoreMax。失败保持DRAFT且不得由默认值补齐。发布后配置包不可改。Questionnaire规范化后完整冻结该配置包，同时把`scoring.threshold`投影到`frozen_threshold`、把`scoring.ruleVersion`投影到`rule_version`；三者不一致即身份冲突。
22. 客户答卷JSON根只允许`answers`，每项只允许`questionCode/value`；客户端不得提交score、passed、threshold、weight、strategy或option score。未知/重复题目、未知/重复选项、类型错误、越界选择或文本长度非法在Response写入前拒绝；结构合法但缺必答题时保存不可变Response并按第20条形成失败Result。相同Questionnaire配置和规范化答案必须产生相同score/passed。

## 状态、事务与锁序

- Task在业务时点到达并初始化后从`PENDING_ASSIGNMENT -> PENDING_COLLECTION -> PENDING_DECISION -> FAILED|PASSED -> PENDING_ARCHIVE -> ARCHIVED`流转；业务时点到达前只有PROJ冻结ProjectTask，不预建ACC领域Task；整改不回退旧Task，而是新建revision。
- Questionnaire：`ACTIVE -> SUBMITTED|INVALIDATED|EXPIRED`；一个Questionnaire只接受一个首次有效requestId结果，失败判定也不可覆盖。
- 触发锁序：PROJ ProjectTask/WorkBinding事实→ACC Task→Questionnaire。
- 提交锁序：ACC AccessGrant→Task→Questionnaire→PLT签字/附件引用→Response→Result→Outbox。
- 失效锁序：PROJ ProjectScope事实重验→ACC Task链→当前Result→Outbox；不锁定或改写Questionnaire/Response/文件引用。
- 归档锁序：ACC交付件根/来源版本→PLT结果文件集合及归档集合→ACC归档投影。任一事务内不得反向加锁。

## P3-E09 Feature-forward差量

| 表 | 差量 | 关键约束 |
|---|---|---|
| `acc_satisfaction_questionnaire_template` | 新建ACC模板根 | `uk(tenant_id, template_code)`；当前发布指针与草稿分离 |
| `acc_satisfaction_questionnaire_template_revision` | 新建只追加修订，保存五维适用条件、优先级、题目JSON、阈值、规则版本、发布区间 | `uk(tenant_id, template_id, revision_no)`；同一确定输入至多一个最高优先级发布修订，歧义失败 |
| `proj_project_task` | 加性冻结满意度模板修订ID/版本、规则版本和阈值 | 仅`satisfaction_timing`非空任务允许；值必须来自ACC解析Fact，不由PROJ推断 |
| `acc_satisfaction_collection_task` | 增加`project_task_id/source_owner_context/source_object_type/source_object_id/source_object_version/trigger_owner_context/trigger_object_type/trigger_fact_id/trigger_fact_version/collection_key/task_revision_no/prior_task_id/assigned_by_user_id` | `uk(tenant_id, collection_key, task_revision_no)`；`uk(tenant_id, project_task_id, trigger_owner_context, trigger_object_type, trigger_fact_id, trigger_fact_version)`；首次source=trigger，整改保持source不变并以唯一整改Fact作为trigger |
| `acc_satisfaction_remediation_fact` | 新建只追加整改Fact，保存`prior_result_id/remediation_revision_no/remediation_request_id/evidence_summary/evidence_file_fact_version/completed_by/completed_at/fact_version` | `uk(tenant_id, prior_result_id, remediation_revision_no)`、`uk(tenant_id, prior_result_id, remediation_request_id)`；形成后不可更新/删除，同键同载荷幂等 |
| `acc_satisfaction_questionnaire` | 增加`questionnaire_status/access_scope_version` | 状态受控；冻结题目/阈值/规则不可更新 |
| `acc_satisfaction_access_grant` | 新建受控链接授权 | token摘要唯一；状态`ACTIVE/CONSUMED/REVOKED/EXPIRED`；完整令牌不落库 |
| `acc_satisfaction_response` | 移除泛化`signature_ref/attachment_refs_json`目标语义，增加提交渠道、客户联系人引用和协助人 | `uk(tenant_id, questionnaire_id, request_id)`；答卷只追加 |
| `acc_satisfaction_response_file` | 新建签字/客户附件公共文件事实 | role=`SIGNATURE/ATTACHMENT`；签字恰一条，附件按sequence唯一 |
| `acc_satisfaction_result` | 增加`collection_key/result_status/effective_from/effective_to/current_marker/archive_actor_user_id/deliverable_source_version_id/invalidated_by_user_id/invalidated_at/invalidation_reason_code/invalidation_reason_summary`及补偿字段，Feature目标不使用旧模型`archive_artifact_id/archive_payload_sha256` | 判定业务字段只追加；仅允许当前有效达标Result以正式失效命令一次性关闭区间并记录原因；`uk(tenant_id, collection_key, current_marker)`只标记未关闭EFFECTIVE且passed的当前结果 |
| `acc_satisfaction_result_file` | 新建结果文档及冻结来源文件集合 | role=`RESULT_DOCUMENT/SIGNATURE/ATTACHMENT`；完整有序PLT公共文件事实 |
| `acc_project_deliverable*` | 直接复用F-ACC-001根、来源版本、来源附件和补偿字段 | 仅精确`deliverable_code=D-SAT-REPORT/task_code=T-SAT-SURVEY`且同租户同项目的唯一根接受`source_object_type=SatisfactionResult`；当前指针只指有效达标结果，根缺失/重复/错配失败关闭 |

可配置问卷补充不新增表或列：`acc_satisfaction_questionnaire_template_revision.frozen_question_json`与`acc_satisfaction_questionnaire.frozen_question_json`承载相同完整配置包，现有`frozen_threshold/rule_version`为强一致投影，Result继续保存最终`score/threshold/rule_version`。P3-E09结论为`NO_STRUCTURAL_DELTA / FORWARD_MANAGED_SEED_REVISION_REQUIRED`：已提交或执行的V133保持不可变；后续前向迁移只对V133精确受管且当前PUBLISHED的三组根追加revision 2完整配置、关闭旧revision 1有效区间并原子更新current_revision_id，旧行保留且普通业务模板不参与。停用种子不提升为发布配置，部分命中或身份冲突整批失败。

当前核心DDL不原地修改；未来Technical Plan只能创建新的前向Flyway。原F-ACC-002总体P3-E09仍为`FEATURE_FORWARD_DELTA_REQUIRED`；本补充仅为`NO_STRUCTURAL_DELTA / FORWARD_MANAGED_SEED_REVISION_REQUIRED`。当前补充获独立GO前不得同步Feature/Technical Plan或实现。

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
