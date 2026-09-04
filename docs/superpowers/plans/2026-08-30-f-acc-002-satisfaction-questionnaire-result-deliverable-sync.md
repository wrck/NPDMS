# F-ACC-002 满意度问卷、达标判定与归档同步实施计划

> **面向实施代理：** 必须使用`executing-plans`逐项执行本计划。共享API、V133、Outbox白名单和项目创建/初验完成接入串行修改；Task 1聚焦验证通过后再进入Task 2。

**计划ID：** `NPDMS-FACC002-TECHPLAN-20260830-01`

**当前状态：** `BASELINE / PASS / GO`（独立整改复审 `41f92526919e8c18b11c04f188365be2105240ac`）

**实施补充：** PRD修订010及可配置问卷SDS补充`4ecc9d3b`已GO；本计划不建立新Gate，仅按该上游契约补充模板校验、答卷验证、确定性计分和受管种子前向修订。

**目标：** 实现模板发布与冻结、满意度任务、V1受控客户答卷、不可变评分Result、整改重收、Result失效、满意度应交来源归档、历史下载与统一异步导出的完整纵向闭环。

**架构：** ACC满意度语义落在`pms-module-project`全新`satisfaction`子包；PROJ继续拥有项目任务、WorkBinding和项目范围，PLT继续拥有文件、归档及统一`ExportTask/ExportAudit`真值。客户grant只授权单一问卷；已提交Response与判定事务分段，PLT以`MANDATORY`生成唯一Result文档后，文件公共事实、Result、ResultFile和Outbox同事务提交。ACC只以`ACC/SATISFACTION_RESULT` Provider向PLT提供裁剪数据，异步导出不形成第二Task/Audit。ACC-04投影复用既有唯一应交根及来源历史表，归档失败只进入补偿。

**技术栈：** JDK 25、Spring Boot、MyBatis/MySQL 8、Flyway、PlatformCommandExecutionApi、PlatformOutboxDeliveryApi、Quartz、Vue 3、Element Plus、pnpm 9.15.5、Vitest、Chromium。

**规格：**

- `specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md`；
- `specs/features/F-ACC-002-physical-contract.json`；
- `specs/features/F-ACC-002-legacy-reuse-audit.md`；
- `docs/decisions/0041-satisfaction-questionnaire-result-and-deliverable-source.md`；
- `docs/decisions/0042-unified-business-export-task-and-audit.md`；
- `docs/design/09-database-design.md`、`10-api-design.md`、`11-event-design.md`、`13-file-design.md`、`15-cache-and-concurrency.md`、`16-exception-and-idempotency.md`；
- Feature Ready独立复审GO候选`145e4a61ea936d0679f2ec41a7d412975572e5a3`，状态回写提交`27f5bcb2`；Result生成文件Owner补充整改`afa37d66`独立复审GO；统一异步导出补充整改`1df9b392`独立复审GO，状态回写提交`9ab20d99`。

## 一、全局约束与端口

- 只实现`ACC-02@V1=FULL`和`ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`；不实现CLO/SUB消费者、ACC-04其他来源、批量下载或INT-05/INT-10连接器。
- V17/V18电子完工证明、旧问卷/回访/转包载体保持不变；不读取旧`satisfactionScore`、状态、客户确认或归档动作生成新Result。
- PLT-01统一待办完整实现不在本Feature；`SatisfactionTaskCreated`只形成受控`TodoRequested`请求和现有项目工作台入口，不新增或直写`plt_todo`，待办是否完成不得反向推进ACC。
- 不修改Yudao基础平台源码，不删除鉴权、租户、项目范围或文件范围控制；五个最小权限键保持不变，角色关系仅作正式验收配置。
- 数据查询遵守`docs/coding/database-query-interface.md`：非主键/稳定唯一键使用场景Query；动态集合、联表和锁查询进入Mapper XML；空权限集合返回空结果。
- 本Worktree独占后端端口`59340`、前端端口`19340`；前端代理与浏览器脚本只指向`http://localhost:59340`。启动前用`netstat -ano`确认端口未占用；冲突时先更新本计划及后续Task，不复用其他分支应用。MySQL`23316`、Redis`26379`继续共享仓库基础设施。
- 正式浏览器验收使用现有稳定身份`facc001acceptance`，密码只从`FACC002_BROWSER_PASSWORD`读取；管理员仅通过正式授权配置为该身份增加本Feature权限。密码不得进入迁移、计划、脚本、日志、命令回显或证据。
- 收益优先：只执行本计划第六节的聚焦测试、V133迁移验证、受影响后端构建、目标前端检查及一次真实Chromium闭环；不重复Phase 1/2/3或全仓回归。

## 二、复用与文件边界

| 现有载体 | 判定 | 计划处理 |
|---|---|---|
| PROJ `ProjectWorkBindingFactApi/Impl`、ProjectTask和初验完成事务 | `COPY_THEN_ENHANCE` | 首次时点在初验活动完成事务中调用ACC initializer；Todo或报告上传不触发 |
| PROJ `ProjectScopeApi/Impl` | `DIRECT_REUSE` | 查询用`resolveAllCurrent/resolveCurrent`；下载、管理和失效用`lockAndRevalidate`；`treeVersion`是文件`scopeVersion`唯一来源 |
| V55 `T-SAT-SURVEY→D-SAT-REPORT`、V63应交根 | `DIRECT_REUSE` | 只允许同租户同项目稳定码精确根，不新建第二应交清单 |
| F-ACC-001来源版本/附件、Outbox和归档模式 | `COPY_THEN_ENHANCE` | 复用表、Mapper和补偿语义；新增SatisfactionResult来源，不改变报告来源行为 |
| PLT上传、文件策略、Access Ticket、归档 | `COPY_THEN_ENHANCE` | 新增受控grant上传API、`createGeneratedBusinessFile`和ACC满意度策略键；生成文件复用FileUploadSession/补偿，公共事实用`sha256`，ACC落库映射`file_hash` |
| Platform命令幂等、Outbox、Quartz同步 | `COPY_THEN_ENHANCE` | 新事件进入受控白名单；满意度专用Job只领取自身事件 |
| ADR-0014与仓库导出现状 | `NO_RUNTIME_CARRIER / BUILD_APPROVED_ADR_0042` | PLT新增唯一`ExportTaskApi`、两张Task/Audit表及执行/到期Job；ACC只新增`ACC/SATISFACTION_RESULT` Provider和场景REST，不建立第二导出真值 |
| V17/V18电子完工证明栈 | `DO_NOT_REUSE / PRESERVE_EXISTING` | 旧表、接口、菜单、页面和项目详情投影不修改，不作为满意度真值 |

## 三、稳定接口与事务

### 3.1 ACC模块API

在`pms-module-project-api`新增`satisfaction`包，接口和关键类型固定为：

```java
public interface SatisfactionQuestionnaireTemplateApi {
    SatisfactionQuestionnaireTemplateFact resolvePublished(
            SatisfactionQuestionnaireTemplateQuery query);
}

public record SatisfactionQuestionnaireTemplateQuery(
        Long tenantId, String projectType, String signingMode,
        String implementationMode, String businessPurposeCode,
        String applicableTimingCode) {}

public record SatisfactionQuestionnaireTemplateFact(
        String outcome, Long templateId, Long templateRevisionId,
        Integer templateVersion, Integer ruleVersion,
        BigDecimal threshold) {}

public interface SatisfactionTaskInitializationApi {
    SatisfactionTaskInitializationResult initialize(
            SatisfactionTaskInitializationCommand command);
}

public record SatisfactionTaskInitializationCommand(
        Long tenantId, Long projectId, Long projectTaskId,
        Integer expectedProjectTaskVersion,
        String sourceOwnerContext, String sourceObjectType,
        String sourceObjectId, Integer sourceObjectVersion,
        String triggerOwnerContext, String triggerObjectType,
        String triggerFactId, Integer triggerFactVersion,
        String operationId) {}

public record SatisfactionTaskInitializationResult(
        String outcome, Long taskId, String collectionKey,
        Integer taskRevisionNo, Long questionnaireId, Integer factVersion) {}

public interface SatisfactionResultFactApi {
    SatisfactionResultFact inspect(SatisfactionResultFactQuery query);
    SatisfactionResultFact lockAndRevalidate(SatisfactionResultFactQuery query);
}

public record SatisfactionResultFactQuery(
        Long tenantId, Long resultId, Integer expectedResultVersion) {}

public record SatisfactionResultFact(
        String outcome, Long projectId, Long projectTaskId,
        Long taskId, Integer taskRevisionNo, String collectionKey,
        Long questionnaireId, Long responseId, Long resultId,
        Integer resultVersion, Long templateRevisionId,
        Integer ruleVersion, BigDecimal threshold,
        String sourceOwnerContext, String sourceObjectType,
        String sourceObjectId, Integer sourceObjectVersion,
        Boolean passed, String resultStatus, String archiveStatus,
        Integer factVersion) {}
```

- Template Provider只返回`FOUND/NO_MATCH/AMBIGUOUS/DEPENDENCY_UNAVAILABLE`；五维最高优先级并列不是任选。
- initializer为`MANDATORY`。初验完成Provider先调用`ProjectWorkBindingFactApi.lockCurrentSatisfactionTaskByProject(projectId)`，由PROJ按稳定码锁定同项目唯一`T-SAT-SURVEY`及完整冻结Fact，再把其真实taskId/version传给initializer；initializer继续调用原expected-version接口二次重验。首次revision固定1且source/trigger仍为初验活动完成Fact；相同Fact同载荷返回原结果，异载荷冲突。
- Result Fact返回task/questionnaire/response/result、模板/规则/阈值、原始source、passed、resultStatus、archiveStatus及版本；投影和未来CLO/SUB只能读取Owner Fact。

### 3.1A 可配置问卷与确定性计分

- 新增ACC内部`SatisfactionQuestionnaireDefinition`，只解析`schemaVersion=1`封闭配置包并在模板发布、Questionnaire冻结和判定三处复用同一校验器；不得执行脚本、表达式或客户端算法标识。
- 题型仅`SINGLE_CHOICE/MULTIPLE_CHOICE/RATING/TEXT`。多选`minSelections/maxSelections`为非空整数且满足`1<=min<=max<=options数量`；文本`minLength/maxLength`为非空整数且满足`0<=min<=max`；不适用参数必须缺失。
- 发布时计算每题真实最大可达分：单选/量表取最大option score，多选取全部合法去重选择集合的平均分最大值；再派生`SUM_V1/WEIGHTED_AVERAGE_V1`的scoreMax并校验threshold。最低选2项、100/0时最大分50，threshold=80必须拒绝。
- 答卷只接受`answers[{questionCode,value}]`。非法题目/选项/类型/数量/长度在Response前拒绝；缺必答项允许保存Response但强制未通过。中间值使用BigDecimal精确计算，只在最终按冻结`precision/roundingMode`舍入一次，再比较threshold。
- 已执行V133不修改；后续前向迁移只为精确受管且当前PUBLISHED的V133模板追加完整revision 2并切换current指针，普通模板、停用模板、部分命中或冲突均不得进入该分支。

### 3.2 PLT受控客户上传

对现有`FileArtifactApi`加性增加：

```java
public record BusinessGrantUploadInitializeCommand(
        Long tenantId, Long grantId, Integer grantVersion,
        String requestId, Long responseId, String policyKey, String operationId,
        String fileName, String contentType, Long fileSize) {}

public record BusinessGrantUploadInitialized(
        Long responseId, String fileSlotKey, Integer fileSequence,
        Long artifactId, Long sessionId, Instant expiresAt) {}

public record BusinessGrantUploadCompleteCommand(
        Long tenantId, Long grantId, Integer grantVersion,
        String requestId, Long responseId, String policyKey, String operationId,
        String fileSlotKey, Integer fileSequence, Long sessionId, byte[] content) {}

public record BusinessGrantFileHandle(
        String policyKey, String fileSlotKey, Integer fileSequence,
        Long artifactId, Integer versionNo, String referenceKey,
        Integer artifactVersion, Integer referenceVersion,
        Integer availabilityVersion, Long scopeVersion, String sha256) {}

public record BusinessGrantFilesRevalidationCommand(
        Long tenantId, Long grantId, Integer grantVersion,
        String requestId, Long responseId, List<BusinessGrantFileHandle> files) {}

BusinessGrantUploadInitialized initializeBusinessGrantUpload(
        BusinessGrantUploadInitializeCommand command);

FileArtifactVersionFact completeBusinessGrantUpload(
        BusinessGrantUploadCompleteCommand command);

List<BusinessGrantFileFact> lockAndRevalidateBusinessGrantFiles(
        BusinessGrantFilesRevalidationCommand command);
```

- `requestId`同时是最终Response提交幂等键和Response预留操作键。ACC在固定`PlatformCommandExecutionApi`作用域`ACC_SATISFACTION_RESPONSE_RESERVATION`中，以grant创建人作为服务端actor、`tenant+grantId+grantVersion+questionnaireId+requestId`为规范化摘要，一次性生成并持久化`responseId`；同键同摘要重放返回原ID，异摘要冲突。initialize响应返回该ID，complete和最终提交必须重放并精确匹配；`SatisfactionResponseSubmissionService`删除自行`IdWorker.getId()`路径。
- 文件`operationId`只标识单个上传会话。PLT在初始化时生成并返回稳定`fileSlotKey/fileSequence`，同operation重放返回同槽位；客户端不得自造槽位。命令不携带登录用户，不伪造Web上下文。
- 通用`FileBusinessObjectPolicyQuery/FileBusinessObjectPolicyRevalidationQuery`保持不变。新增`BusinessGrantUploadInitializePolicyQuery/BusinessGrantUploadCompletePolicyQuery/BusinessGrantFileRevalidationQuery`及`BusinessGrantUploadPolicyFact`，Provider对应方法默认失败关闭；Registry按`ACC/SATISFACTION_RESPONSE`唯一分派。ACC Provider按grant→Questionnaire→Task锁序验证tenant、grant版本/ACTIVE/有效期、requestId/responseId预留、用途/槽位和`ProjectScopeApi(PROJECT_EDIT)`；Fact冻结`grantIssuerUserId=positive(grant.creator)`及scopeVersion，不使用updater或客户端值。
- PLT仍执行大小、类型、扫描、存储、Artifact/Version/Reference和补偿。完成返回公共文件Fact；最终提交把客户端列表只作为句柄传给`lockAndRevalidateBusinessGrantFiles`，仅持久化PLT返回且与grant/version/response/policy/scope/slot精确匹配的规范Fact。ACC只保存`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/sha256`，`sha256`精确落`file_hash`。
- PLT仅把`grantIssuerUserId`作为受控grant内部责任主体写入现有`created_by/creator/updater/OperationAudit.actorId`；detail固定记录`subjectType=BUSINESS_GRANT`、grantId/version、questionnaireId、responseId、policyKey、fileSlotKey。不得建立SecurityContext、调用登录上传Controller、把grant当用户ID或解释为客户拥有`pms:file:upload`。
- 现场协助另用固定作用域`ACC_SATISFACTION_ASSISTED_RESPONSE_RESERVATION`，以正式登录actor和最终`requestId`冻结`ASSISTED+tenant+task+questionnaire+actor+requestId`并重放同一`responseId`，预留不创建空Response。新增authenticated-assisted专用初始化、完成和最终文件重验DTO/SPI/Registry/FileArtifactApi及窄PLT服务；不得复用grant Query或通用`inspect`。
- ACC Provider在initialize/complete/final三个时点按Task→Questionnaire锁定并重验当前责任人、可收集状态、预留身份与`ProjectScopeApi(PROJECT_EDIT)`的scopeVersion。PLT从SecurityContext取得actor，同时重验`pms:acceptance:satisfaction:collect`与`pms:file:upload`，复用现有UploadSession、内容校验、扫描、存储、Artifact/Version/Reference及补偿。槽位由PLT生成，完成与最终重验从Session/Reference反解；签字固定序号1，附件使用服务端序号。
- `SatisfactionResponseSubmissionService.submitAssisted`必须先重放预留，再调用专用final-revalidation并仅持久化PLT规范事实和预留ID；删除`IdWorker`新ID与`attachExistingVersions`改挂路径。既有Response重放只比较原Task/Questionnaire/actor、联系人、答案和持久化规范文件集合，不能新增或改挂文件。

### 3.3 PLT受控Result生成文件

对同一`FileArtifactApi`再加性增加：

```java
public record GeneratedBusinessFileCommand(
        Long tenantId, Long actorUserId, String operationId,
        Long resultId, Long collectionTaskId, Long questionnaireId,
        Long responseId, Integer expectedTaskVersion,
        String ownerContext, String objectType,
        String purposeCode, Long scopeVersion,
        String fileName, String contentType, byte[] content) {}

FileArtifactVersionFact createGeneratedBusinessFile(
        GeneratedBusinessFileCommand command);
```

- ACC在判定事务开始前预分配`resultId`，锁定Task→Questionnaire→Response后由服务端把`collectionTaskId/questionnaireId/responseId/expectedTaskVersion`写入命令；这些字段和resultId/actor/scopeVersion均不得进入Controller，并必须进入operation规范化摘要。命令目标只允许`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT`；`ownerContext/objectType/purposeCode`必须精确为`ACC/SATISFACTION_RESULT/SATISFACTION_RESULT_DOCUMENT`。
- `actorUserId`只能取Result形成时Task当前责任人，`scopeVersion`为`Long`且只能原样取同项目`ProjectScopeApi.treeVersion`，禁止截断、固定值或经Result版本转换。通用文件策略Query不变；新增`GeneratedBusinessFilePolicyRevalidationQuery`及Provider默认失败方法，Registry按`ACC/SATISFACTION_RESULT`唯一分派。ACC Provider在同一MANDATORY事务锁定并验证Task为PENDING_DECISION、版本/责任人/questionnaire/无Result，Questionnaire→Task与Response→Questionnaire关系、resultId/目标未占用，再用Task.projectId执行`ProjectScopeApi.lockAndRevalidate(PROJECT_EDIT)`并要求treeVersion及项目集合匹配。PLT同时以SYSTEM `PermissionApi`重验actor的既有`pms:file:upload`；不得伪造Web登录上下文。
- Provider以`MANDATORY`加入ACC判定MySQL事务，复用现有内容类型/大小/SHA-256/扫描、私有存储、Artifact/Version/Reference和审计。只返回一个`FileArtifactVersionFact`；同Result第二条文档和同operation异规范化摘要均冲突。
- 对象存储补偿顺序固定为四步：①内部`REQUIRES_NEW`先按`operationId+请求摘要`提交`FileUploadSession`预留；②另一个内部`REQUIRES_NEW`调用`FileStorageReceiptApi.store`，对象写入后把`infra_file`回执及session回执绑定一并提交，失败时保留已提交session供同operation检查/清理；③只有已提交回执存在时，Provider才以`MANDATORY`加入ACC外层事务创建Artifact/Version/Reference，随后ACC写Result/ResultFile/Outbox；④外层事务`afterCompletion`再以独立事务把session标记完成，或在回滚时恢复为可重放/待补偿。进程中断按持久session与回执恢复；放弃时由现有`FileUploadCompensationService`确认无已提交Version后删除未引用对象。不得让`FileStorageReceiptApi.store`参加外层事务后随回滚丢失`infra_file`回执，也不得生成第二文档。

### 3.4 Result、Outbox、来源与归档

1. 公开Response入口先重放`ACC_SATISFACTION_RESPONSE_RESERVATION`并要求请求responseId等于回执，再以提交事务锁定`AccessGrant→Task→Questionnaire→PLT grant文件规范事实→Response`。PLT对实际Artifact/Version/Reference、grantId/version、responseId、policyKey、scopeVersion和服务端槽位作最终锁定重验；ACC只持久化返回事实并直接使用预留responseId，不再生成ID。随后由独立Spring Bean开启判定事务，重新锁定`Task→Questionnaire→Response`并重验ProjectScope；同`questionnaireId+requestId`重放若已有Response但没有Result，复用该Response和同一判定operation继续判定，不追加第二Response。
2. 判定事务在任何Result写入前调用PROJ `ProjectWorkBindingFactApi.lockCurrentSatisfactionTask(projectId, projectTaskId)`窄方法，只接受唯一当前`T-SAT-SURVEY` Fact并冻结其`projectTaskVersion`；再调用`createGeneratedBusinessFile`，原子写Result、唯一ResultFile、Task状态、成功幂等事实和携带该版本的`SatisfactionResultVersionChanged` Outbox。失败Result把Task置FAILED；达标Result为`EFFECTIVE + passed=true`并把Task置`PENDING_ARCHIVE`，两者都必须有唯一结果文档。PROJ身份或PLT任一步失败时保留已提交Response和`PENDING_DECISION`，上述判定写入全部为零；Result形成时冻结当前责任人为`archiveActorUserId`。
3. `recollect`锁`Task链→前一Result→RemediationFact→新Task→Questionnaire→Outbox`；原source不变，新trigger固定为`ACC/SatisfactionRemediationFact`，collectionKey不变且revision+1。
4. `invalidate`锁`PROJ当前树版本→Task链→当前Result→Outbox`，只接受当前有效达标版本；关闭区间、清空marker、写失效审计和`INVALIDATED`事件，不重开旧Task。
5. 在`PlatformOutboxDeliveryApiImpl.SUPPORTED_EVENT_TYPES`只加`SatisfactionTaskCreated`和`SatisfactionResultVersionChanged`。初始化事务把已锁定PROJ Fact的`projectTaskVersion/taskCode`冻结进Task事件；`SatisfactionTaskOutboxDeliveryJob`只领取Task事件：以事件版本重验现有ProjectTask和ACC Task关系，在同一消费事务以稳定`taskId+taskRevisionNo`追加或重放`TodoRequested` Outbox；项目工作台直接按现有ProjectTask身份加载ACC Task，不建第二投影表。只有`TodoRequested`已持久化才`markDelivered`；解析/身份/写入失败均`scheduleRetry`，不得以无监听器的`ApplicationEventPublisher`调用冒充成功。PLT-01未来消费`TodoRequested`，本Feature不领取或误标该事件。`SatisfactionResultOutboxDeliveryJob`只领取Result事件；来源投影事务成功后才按消息retryCount `markDelivered`，失败以相同expectedRetryCount `scheduleRetry`。未来CLO/SUB请求不加入白名单、不由两Job领取或标记成功。
6. 来源投影不得调用生产者的取当前方法；它以事件`projectTaskVersion`调用原`lockAndRevalidateSatisfactionTask`精确重验同项目、任务、版本和`T-SAT-SURVEY`，再锁同项目唯一`D-SAT-REPORT/T-SAT-SURVEY`根。事件`resultVersion`仅写来源`source_version`，`resultFactVersion`仅传给改名后的`SatisfactionResultFactQuery.expectedFactVersion`。完整文件事件保留角色内`sequence`，同时按结果文档→签字→附件、角色内序号冻结连续唯一`sourceSequence=1..N`；来源表`attachment_sequence`只写sourceSequence，缺失、重复或不连续整体失败。RECORDED只在Fact同时命中resultId、业务版本、factVersion且EFFECTIVE+passed时置CURRENT；失效后旧RECORDED只保留非当前历史，不恢复根指针。INVALIDATED只清空精确指向自身业务版本的根指针。
7. 归档复用PLT独立ARCHIVED集合与`FileArchiveRecord`，ACTIVE Result文件继续支持历史下载。失败保持`PENDING_COMPENSATION`；成功只更新该来源，只有来源仍是根当前指针时才更新根摘要。

### 3.5 PLT统一异步导出

- 在`pms-module-platform-api`加性新增`ExportTaskApi.request/getFact/retry`及命令/Fact；PLT真实Provider拥有唯一Task/Audit。请求身份固定为`tenantId+ownerContext+exportType+actorUserId+operationId`，同operation同规范化摘要返回原Task，异摘要冲突，request本身不隐式重试。
- 在`pms-module-platform-api`新增PLT拥有的`ExportBusinessDataProvider`稳定SPI，ACC在`pms-module-project`实现真实Provider，键固定为`ACC/SATISFACTION_RESULT`。Provider在申请、生成、显式重试和下载四个时点重验`pms:acceptance:satisfaction:export`、ProjectScope、责任人、字段、文件和租户范围；PLT不得读取ACC表，ACC不得写`plt_export_*`。
- `ExportTaskExecutionJob`只以版本CAS领取`REQUESTED`。暂时生成/扫描/存储失败或Provider暂时不可用/范围版本未知写`FAILED + failure_retryable=true`；永久Provider/载荷契约错误写`FAILED + false`；授权/范围拒绝写`REJECTED`。仅原actor可携带expectedVersion调用retry，经四类控制重验后CAS回`REQUESTED`、`retry_count+1`并追加`RETRY_REQUESTED`。
- 成功文件固定为`PLATFORM/EXPORT_TASK/{taskId}/EXPORT_FILE`，复用PLT文件内容校验、私有存储和公共文件事实。`ExportFileExpirationJob`仅对成功满24小时的Task删除文件内容并转`EXPIRED`；Task与只追加Audit永久保留，FAILED/REJECTED不得过期。
- 平台公开`GET /api/v1/pms/export-tasks/{id}`、`POST .../{id}/actions/retry`、`POST .../{id}/access-ticket`；只允许原actor并按当前业务范围重验。ACC的`POST /satisfaction-results/exports`只调用`request`并返回`taskId/status/queryLocation`。

## 四、文件与迁移落位

### 4.1 后端/API文件

- 新增：`pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/`及`dto/`，承载三组稳定API和命令/Fact。
- 修改：`pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java`、`FileBusinessObjectPolicyProvider.java`；在同目录`dto/`新增grant初始化/完成/最终重验Command、Query、PolicyFact、服务端槽位Fact和`GeneratedBusinessFileCommand`。
- 新增：`pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/export/ExportTaskApi.java`、`ExportBusinessDataProvider.java`及命令/Fact/规范化请求/裁剪结果类型；ACC实现该PLT SPI，不增加平台对project-api的反向依赖。
- 修改：`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java`、FileUploadSession/补偿接入及文件策略路由，只加满意度grant上传与Result生成文件，不改变现有登录上传、ACC报告和其他目标。
- 修改：`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java`，只增加两种满意度事件白名单。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/satisfaction/`真实Provider；`service/satisfaction/`模板、任务、分段提交/判定、Result、失效、Task/Result来源投影、两类Outbox Job、归档补偿和Quartz注册器；`controller/admin/satisfaction/`及VO。
- 新增：PLT `service/export/`的`ExportTaskApiImpl`、Task/Audit应用服务、`ExportTaskExecutionJob`、`ExportFileExpirationJob`和`ExportQuartzRegistrar`；新增对应DO/Mapper/XML及平台状态/retry/access-ticket Controller。业务查询只通过唯一Provider接口，不访问ACC表。
- 新增：`SatisfactionTaskController.assistedResponse`及`SatisfactionAssistedResponseApplicationService`，复用同一Response校验/判定编排并独立保存客户联系人、签字事实与服务端协助人；新增`SatisfactionResultController.createExport`、`SatisfactionResultExportApplicationService`和`SatisfactionResultExportBusinessDataProvider`，只调用PLT统一Task并提供裁剪数据，不建立第二套导出审计。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/satisfaction/`、`dal/mysql/satisfaction/`、`src/main/resources/mapper/satisfaction/`。锁、动态集合和联表只写Mapper XML。
- 修改：`ProjectManualCreationServiceImpl`只在创建含`satisfaction_timing`任务时冻结Template Fact；`AcceptanceActivityCompletionFactApiImpl`只在完成初验且冻结时点为`AFTER_INITIAL_ACCEPTANCE`时，以现有事务调用initializer。
- 修改：`AcceptanceReportSourceProjectionService`使用的应交来源Mapper仅加性支持`source_object_type=SatisfactionResult`；报告路径测试保持不变。

### 4.2 V133前向迁移

新增`sql/migrations/V216__received_facc002_satisfaction_questionnaire_result_forward.sql`，仅接受“全部目标结构不存在”或“全部受管结构与配置完整一致”两种状态：

1. 创建模板根/修订、满意度任务、问卷、grant、Response、Response文件、Result、Result文件和RemediationFact；对`proj_project_task`加五个冻结Fact字段。字段、生成列、唯一键和`sha256→file_hash`逐项等于机器契约，不建跨Context外键。
2. 复用既有`acc_project_deliverable_source_version/source_attachment`，不创建第二来源表；预检其公共文件字段和唯一键可承接SatisfactionResult。
3. 创建PLT Owner的`plt_export_task/plt_export_audit`，字段、当前唯一、失败分类、retry水位及只追加动作精确等于ADR-0042/机器契约；两表`NEW_ONLY`，不从`plt_operation_audit`或旧文件反推。
4. 新增菜单根`930930`和五个权限菜单`930931～930935`，分别承载`pms:acceptance:satisfaction:query/manage/collect/export/download`，并通过正式关系授予受管角色`992004800002/facc001_acceptance_full`；不新增业务角色模板，不改变其他角色。
5. 以固定ID`992005900001～992005900005`新增五条启用Quartz配置：三个满意度Task/Result/归档Job及`exportTaskExecutionJob`、`exportFileExpirationJob`；前四项30秒，文件到期检查5分钟，空参数、Quartz重试0。两个窄Registrar在Quartz存在时分别按固定顺序调用`JobApi.syncEnabledJobByHandlerName`；配置缺失/重复/冲突或同步失败使启动失败。
6. 只增加隔离的示例模板候选数据用于覆盖零匹配/并列歧义/停用不参与；正式正向模板必须由公开模板管理入口创建并发布，迁移种子不得替代正向配置。
7. V1→V133和V132→V133都执行migrate/info/validate；任一表、列、菜单、Job或种子出现部分状态或非受管占用时失败关闭并从迁移前快照恢复，不修改V17/V18/V128～V132。

### 4.3 前端

- 新增`src/api/pms/project/satisfaction/index.ts`，定义模板、任务、问卷、grant、Result、文件公共Fact和稳定错误类型。
- 新增`views/pms/project/satisfaction/template/index.vue`及修订编辑器，提供正式草稿/发布入口。
- 新增`views/pms/project/satisfaction/task/index.vue`、`detail.vue`、指派/受控链接/二维码/现场协助/整改组件和Result历史抽屉；二维码只编码同一次返回的受控链接，不创建第二token或第二入口。
- 新增匿名`views/pms/project/satisfaction/questionnaire/index.vue`，只持有一次性token并显示唯一问卷；上传、签字和提交均走公开grant API。
- 修改`src/router/modules/remaining.ts`：注册隐藏静态路由`/satisfaction-questionnaires/:token`，route name固定为`PmsSatisfactionQuestionnairePublic`，直接指向匿名问卷View，不挂后台Layout或动态菜单。
- 修改`src/permission.ts`：仅当route name精确为`PmsSatisfactionQuestionnairePublic`且`token`参数为非空字符串时免登录放行；不得将`satisfaction`前缀、后台路由或后端API加入白名单，其他未认证满意度路径继续跳转登录。
- Result页面增加异步导出动作：提交`POST /satisfaction-results/exports`后展示统一`ExportTask/ExportAudit`状态，完成后经现有受权下载链取文件；页面不扩大服务端返回的项目、责任人、字段、文件或租户范围。
- 复用现有文件上传/列表/版本组件和Access Ticket；不修改旧`completion-certificate`页面，不在UI计算分数、阈值或项目范围。
- 320/768/1024/1440宽度无页面级横向溢出；页面业务错误、console error和失败请求均使Chromium脚本失败。

## 五、实施任务

### Task 1：共享契约、V133与后端纵向闭环

**Files：** 第4.1、4.2节后端/API/迁移文件及对应聚焦测试。

**Consumes：** Feature Ready基线、ProjectScope/WorkBinding、AcceptanceActivity完成Fact、PLT文件/归档、Platform命令与Outbox、既有应交根和来源历史。

**Produces：** 三组ACC模块API、受控grant上传与Result生成文件、PLT统一ExportTask/Audit公共载体、模板/任务/答卷/Result/整改/失效/导出REST、Task/Result投影与归档补偿、V133结构和正式权限/Job配置。

- [ ] **Step 1：编写聚焦失败测试并确认RED**

  新增Provider、命令、投影、乱序、grant上传、Result生成文件、现场协助、统一导出和迁移测试。计分聚焦只覆盖一条SUM、一条加权平均、多选真实最大可达分、非法答案零Response和缺必答失败Result；至少证明：五维零/并列失败；initializer无外层事务拒绝；令牌错问卷/跨租户拒绝；Response已提交后生成文件失败保持`PENDING_DECISION`且零Result；`scopeVersion(Long)`从`ProjectScopeResult.treeVersion`原样进入文件事实；session四阶段失败可补偿且无第二对象；必答/签字/阈值失败Result均有唯一文档；现场协助拒绝零写入；统一导出同operation幂等、生成前重验、可重试/不可重试/REJECTED分类、原actor expectedVersion CAS重试、下载重验和仅SUCCEEDED过期均有直接测试；整改revision2幂等；invalidate期望版本；旧RECORDED晚到不恢复；旧完工证明零读取。

- [ ] **Step 2：实现API、DO/Mapper和领域服务最小闭环**

  先实现PLT `ExportTaskApi`、两表、执行/到期Job及状态/retry/access-ticket公开入口，再实现Template/Initializer/Result Fact、客户提交、现场协助、ACC导出Provider、recollect和invalidate。模板发布、Questionnaire冻结与Result判定统一调用3.1A的配置解析/校验/计分器；客户端答案在Response前校验，服务端生成score/passed。现场协助只接受服务端认证actor；ACC导出入口只冻结场景请求并调用PLT request，四时点裁剪与失败分类按3.5执行。所有非主键查询使用场景Query，拒绝路径在Result/来源/Outbox写入前结束。

- [ ] **Step 3：接入PLT、PROJ和Outbox**

  先实现Response预留：同grant+requestId通过`PlatformCommandExecutionApi`重放同一responseId，最终Response不得再次生成ID。再加性实现grant上传初始化/完成/最终文件集合锁定重验、服务端槽位、issuer审计和默认失败Provider；直接测试证明异摘要、错ID/槽位/文件Fact及非正数issuer均在文件或Response写入前失败。现场协助使用独立authenticated-assisted预留与专用PLT上传链，最终提交重放预留、锁定实际文件事实并使用同一ID，禁止改挂其他类别文件。继续完成Result生成文件、项目创建冻结Template Fact、初验完成initializer、Task/Result/归档Job及PLT导出Job。事件Job均以真实消费事务成功作为`markDelivered`前提。

- [ ] **Step 4：实现并验证V133**

  运行空库V1→V133及V132→V133 `migrate/info/validate`，核对满意度结构、两张PLT导出表、唯一键/状态约束、五权限、五Job和受管种子；注入部分身份冲突必须在业务数据写入前失败。

- [ ] **Step 5：运行聚焦后端验证和构建**

  运行新增满意度测试、F-ACC-001来源/归档直接回归、ProjectScope/WorkBinding聚焦回归；随后执行受影响reactor Maven `package -DskipTests`。不跑全仓测试。

- [ ] **Step 6：提交Task 1并更新Feature Task检查点**

  仅暂存Task 1文件，提交一个本地逻辑提交；记录迁移版本、聚焦测试和已知限制。Task 1未通过不得开始Task 2。

### Task 2：前端与一次真实Chromium闭环

**Files：** 第4.3节前端文件、`scripts/tests/run_facc002_browser_acceptance.cjs`、一次运行直接生成的证据及当前Feature Task。

**Consumes：** Task 1公开REST、正式权限配置、V133、PLT Access Ticket和后端端口59340。

**Produces：** 模板管理、任务/Result工作台、客户问卷页面和一次可复现真实浏览器闭环。

- [x] **Step 1：确认收益优先的前端验收边界**

  不执行测试先行。本Task只以实际用户可见的正向纵向闭环作为Chromium验收主线：正式工作台现场协助、整改重收、精确匿名路由/二维码、达标Result、来源归档和异步导出。授权、租户/项目范围、幂等、失效乱序与补偿分支由已有服务端聚焦测试承担，不再在单次Chromium中重复穷举。

- [x] **Step 2：实现API与页面最小闭环**

  实现模板、任务、匿名问卷和Result页面、精确公共静态路由及守卫；任务页渲染同一受控链接二维码并提供受权现场协助，现场协助先以最终requestId预留Response，再通过authenticated-assisted专用接口上传签字/附件，最后以同一预留ID提交；Result页提交异步导出并展示统一任务状态。复用现有文件组件，按钮只按服务端允许动作渲染，错误码展示稳定业务信息。

- [x] **Step 3：运行前端聚焦验证和构建**

  运行受影响文件的聚焦静态检查及`pnpm build:local`；修复本Feature引入的失败，不扩展全前端回归。

- [x] **Step 4：准备正式运行环境**

  启动V134后的后端59340和前端19340；确认Quartz三个满意度Job及两个PLT导出Job/Trigger存在。V134只恢复被V108菜单ID冲突覆盖的既有`pms:project-task:assign`按钮载体，不预授角色；正式管理员通过公开授权配置以菜单并集赋予五个满意度权限及该既有任务指派权限，并确认当前责任人已有既有`pms:file:upload/pms:file:archive`；只检查`FACC002_BROWSER_PASSWORD`是否存在，不输出值。

- [x] **Step 5：运行一次真实Chromium纵向验收**

  管理入口创建并发布正式模板，公开创建项目并完成初验任务，由PROJ的`T-SAT-SURVEY` Owner Fact原子初始化revision1。revision1必须从正式工作台打开“现场协助”对话框，以同一requestId/responseId完成预留、签字文件上传和低分答卷提交；不得通过页面内直接fetch、内部API或手填文件事实替代用户操作。整改后创建revision2，仅通过同一受控链接的匿名token路由和二维码提交签字/附件完整的高分答卷。随后等待正式Quartz形成Result来源归档，并发起统一异步导出至SUCCEEDED、形成Access Ticket与永久Audit。同次证据必须由该页面操作直接生成，且业务错误、页面错误、控制台错误和网络失败均为0。其他可达拒绝、幂等、失效乱序和归档补偿保留现有服务端聚焦验证，不再重复扩展Chromium脚本。

- [x] **Step 6：形成Implementation Done候选**

  证据JSON、截图和数据库断言由同一次脚本直接生成，字段逐项一致且不含token/密码/完整客户答案。更新唯一`tasks/features/F-ACC-002.md`检查点，提交后按Implementation Done Gate送独立复审；GO前保持实施中。

## 六、最低验证集合

1. `python -B -m unittest scripts.tests.test_facc002_feature_contract`与Requirement追溯`--check`；
2. 新增后端聚焦单测、V133 MySQL迁移测试、FileUploadSession四阶段真实事务补偿回归、现场协助、PLT统一导出状态/重试/到期及ACC Provider范围回归、F-ACC-001来源/归档直接回归；
3. 受影响Maven reactor `package -DskipTests`；
4. 目标Vitest、`pnpm ts:check`、`pnpm build:local`；
5. 一次收益优先的真实Chromium正向闭环及其直接生成的JSON/截图/数据库事实；可达拒绝和补偿分支不在浏览器内重复穷举。
6. `git diff --check`。不重复Phase 1/2/3主门禁或全仓测试。

## 七、计划自检

- **规格覆盖：** 模板冻结、首次触发、grant/二维码/现场协助、只追加答卷与文件、判定、整改revision、失效、双向乱序、来源归档、下载、导出和权限均有落点。
- **Owner：** ACC不读PROJ/PLT表；PROJ不写ACC表；PLT不判定满意度；PLT生成文件仍重验actor与范围；Todo/通道送达不制造提交或通过。
- **历史：** Response/Result/RemediationFact只追加；失效不恢复旧版；ACTIVE历史文件不因归档失效；旧完工证明保持独立。
- **迁移：** V133新增Feature结构；V134仅恢复被V108覆盖的既有任务指派权限载体，不修改已执行迁移；来源历史表直接复用；PLT导出两表为唯一NEW_ONLY载体，不创建第二应交根、归档或导出真值。
- **权限：** 五个最小键和服务端ProjectScope/FileBusinessScope/租户控制保留；角色映射仅作正式配置。
- **收益：** 两个串行完整Task；一个前向迁移、五条必要调度配置、一次后端构建、一次前端构建和一次浏览器闭环，无低收益全仓重复验证。

## 八、Technical Plan Gate

当前状态：`BASELINE / PASS / GO`。独立整改复审已批准创建唯一`tasks/features/F-ACC-002.md`，并按本计划两个串行Task进入Implementation；本结论不代表产品代码、V133、Task完成或Implementation Done已获批准。
