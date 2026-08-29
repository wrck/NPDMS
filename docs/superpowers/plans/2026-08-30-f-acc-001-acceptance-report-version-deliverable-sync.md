# F-ACC-001 初验/终验报告版本与交付件同步实施计划

> **面向实施代理：** 必须使用`executing-plans`逐项执行本计划。共享API、V128和既有项目创建/任务完成路径串行修改；不得并行写这些文件。每个实施Task完成聚焦验证后再进入下一Task。

**计划ID：** `NPDMS-FACC001-TECHPLAN-20260830-01`

**当前状态：** `BASELINE / PASS / GO`（独立整改复审 `fca9626c4fce4ccf4b03efdebe997343ce7b5a42`）

**目标：** 实现初验/终验活动、草稿与不可变报告版本、终验前初验守卫、PROJ任务同事务完成、既有应交根来源同步、PLT独立归档补偿和历史附件下载的完整纵向闭环。

**架构：** ACC语义实现在`pms-module-project`的全新`acceptancereport`子包，旧V17验收栈保持不变；PROJ仍拥有项目任务与执行契约，PLT仍拥有文件引用和归档事实。报告事务通过Platform Outbox发布版本变更，ACC消费者只追加应交来源历史，归档失败保持报告有效并由同一来源版本幂等补偿。

**技术栈：** JDK 25、Spring Boot、MyBatis/MySQL 8、Flyway、PlatformCommandExecutionApi、PlatformOutboxDeliveryApi、Vue 3、Element Plus、pnpm 9.15.5、Vitest、Chromium。

**规格：**

- `specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md`；
- `specs/features/F-ACC-001-physical-contract.json`；
- `specs/features/F-ACC-001-legacy-reuse-audit.md`；
- `docs/decisions/0039-acceptance-report-version-and-deliverable-index.md`、`0040-acceptance-file-fact-and-activity-initialization.md`；
- `docs/design/09-database-design.md`、`10-api-design.md`、`13-file-design.md`、`15-cache-and-concurrency.md`、`16-exception-and-idempotency.md`；
- Feature Ready独立复审GO提交`bde0feac019baf820634ecc6a0e88272672b601d`，状态回写提交`9f3d3110`；归档操作者与Outbox投递SDS补充GO提交`701bdf701539a0d65f3c67eb10aa0605de58c4a7`，状态回写提交`7f4cfa7a`。

## 一、全局约束与端口

- 只实现`ACC-03@V1=FULL`及`ACC-04@V1`初验/终验来源切片；不夹带ACC-01/02、其余四类交付件来源、CLO业务实现、统一批量下载或Q-FCOM-002退出/回退规则。
- 不修改V17/V63/V124～V127，不迁旧V17业务行，不覆盖终态任务、报告版本、应交来源、归档记录、审计或范围绑定历史。
- 不修改Yudao基础平台源码。PLT改动只限PMS文件公共API和Provider；第三方归档平台只保留既有接口边界，不实现连接器。
- 四个ACC最小权限键、既有`pms:project-task:execute`、`pms:file:archive`、文件功能权限、项目范围和租户隔离全部保留。正式验收身份可配置全部相关键，不固化业务角色模板。
- 数据库查询遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外使用场景Query；动态集合、锁和联表进入Mapper XML；禁止SQL注解、`${}`、`.last(...)`、`Map`和长位置参数。
- 本Worktree独占后端端口`59330`、前端端口`19330`；前端代理和浏览器脚本只指向`http://localhost:59330`。执行启动前用`netstat -ano`复核两端口未占用；若冲突，先更新本计划与Task中的两端口再启动，不复用其他分支进程。MySQL`23316`、Redis`26379`继续共享仓库固定测试基础设施。
- 浏览器身份固定稳定用户ID`992004800001`、用户名`facc001acceptance`；密码只从`FACC001_BROWSER_PASSWORD`读取并由正式管理员改密API配置，脚本、迁移、计划、命令回显和证据不得出现明文。
- 收益优先：不重复Phase 1/2/3全量测试，不执行与本Feature无直接失败结果的全仓测试。必须通过的集合仅为本计划第六节所列聚焦单测、V128 MySQL验证、目标前端测试、一次真实Chromium闭环和规格追溯检查。

## 二、现有载体与改造边界

| 现有资产 | 判定 | 计划处理 |
|---|---|---|
| V17 `pms_acc_acceptance`、旧Controller/Service/UI | `DO_NOT_REUSE` | 字节和行为保持不变；新REST、类、表、页面使用`acceptancereport`命名 |
| V63 `acc_project_deliverable` | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 保留唯一应交根；只加当前来源指针/归档摘要和两个只追加来源从表 |
| `ProjectManualCreationServiceImpl`、应交initializer | `COPY_THEN_ENHANCE` | 在应交根形成后调用ACC initializer，再追加ACC当前执行契约；全部加入原事务 |
| `ProjectTaskLifecycleService`、执行契约、完成判定 | `COPY_THEN_ENHANCE` | 保留PROJ锁、幂等和状态机；ACC Provider只返回完成事实 |
| F-COM范围绑定三个公开API/Provider | `DIRECT_REUSE` | 只运行两条正向回归；报告代码不得调用或写绑定 |
| `ExistingFileReferenceTarget`、`FileArtifactApi` | `COPY_THEN_ENHANCE` | 加性放行唯一ACC附件键并增加整组归档方法；保留现有SOL/动态表单目标 |
| `FileQueryService`、Access Ticket | `DIRECT_REUSE` | 只读取ACTIVE附件集合；ARCHIVED归档集合不承接下载 |
| `FileArchiveRecord`与现有归档控制点 | `COPY_THEN_ENHANCE` | 独立归档集合整组创建ARCHIVED引用和记录；失败整组回滚 |
| `ProjectScopeApi` | `DIRECT_REUSE` | ACC文件Provider以当前`treeVersion`作为唯一`scopeVersion` |

## 三、稳定接口与事务

### 3.1 ACC活动API

在`pms-module-project-api`新增`api/acceptanceactivity`包，签名固定为：

```java
public interface AcceptanceActivityInitializationApi {
    AcceptanceActivityInitializationResult initialize(
            AcceptanceActivityInitializationCommand command);
}

public record AcceptanceActivityInitializationCommand(
        Long tenantId, Long projectId, Long projectTaskId,
        String taskDefinitionKey, Long executionContractId,
        String acceptanceType, String deliverableCode,
        Integer templateRevision) {}

public record AcceptanceActivityInitializationResult(
        String outcome, Long acceptanceId, Integer activityVersion) {}

public interface AcceptanceActivityCompletionFactApi {
    AcceptanceActivityCompletionFact lockAndComplete(
            AcceptanceActivityCompletionCommand command);
}

public record AcceptanceActivityCompletionCommand(
        Long tenantId, Long projectId, Long projectTaskId,
        Long executionContractId, Long acceptanceId,
        Integer expectedActivityVersion, Integer expectedReportVersion,
        String operationId) {}

public record AcceptanceActivityCompletionFact(
        String outcome, Long acceptanceId, Integer activityVersion,
        Long reportVersionId, Integer reportVersion) {}
```

- 两个Provider都位于`pms-module-project`全新`api/acceptanceactivity`包，并使用`MANDATORY`；没有外层事务直接拒绝。
- initializer只接受两组冻结映射：`T-INITIAL-ACCEPT/PRELIMINARY/D-INITIAL-REPORT`和`T-FINAL-ACCEPT/FINAL/D-FINAL-REPORT`。ACC锁定精确应交根后创建PENDING活动，返回原值`acceptanceId/activityVersion`。
- completion只返回`COMPLETED/REPORT_INCOMPLETE/IDENTITY_MISMATCH/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`；仅`COMPLETED`允许PROJ追加TaskCompletionEvaluation并将任务置DONE。

### 3.2 PLT文件API

修改现有`FileArtifactApi`并新增DTO：

```java
public record ArchiveFileReferenceSetsCommand(
        String operationId, String archiveBatchId, String businessDecisionRef,
        Long actorUserId,
        FileReferenceSetKey attachmentSetKey, FileReferenceSetKey archiveSetKey,
        Long expectedScopeVersion,
        List<FileArtifactVersionFact> orderedExpectedPublicFileFacts) {}

public record FileArchiveReferenceSetFact(
        String archiveBatchId, FileReferenceSetKey archiveSetKey,
        List<FileArtifactVersionFact> archivedFacts) {}

FileArchiveReferenceSetFact archiveReferenceSets(
        ArchiveFileReferenceSetsCommand command);
```

- `ExistingFileReferenceTarget`只加性接受`ACC/ACCEPTANCE_REPORT_VERSION/*/ACCEPTANCE_REPORT_ATTACHMENT`；请求仍不得指定租户或操作者。
- ACC `FileBusinessObjectPolicyProvider`从报告版本取得不可变`projectId/projectTaskId`，把文件查询中的`subjectUserId`原值传给`ProjectScopeApi`：READ/DOWNLOAD使用PROJECT_VIEW，UPLOAD/REFERENCE/REPLACE/DETACH使用PROJECT_EDIT，scopeVersion只取返回`treeVersion`。
- 报告首次发布或替换时只从服务端认证上下文取用户并写不可变`publisher_user_id`；DRAFT为空，撤销沿用原发布人。归档补偿把该值原样作为`actorUserId`，不得接收客户端覆盖、借用Job用户或伪造Web登录上下文。
- `archiveReferenceSets`通过SYSTEM `PermissionApi.hasAnyPermissions(actorUserId, "pms:file:archive")`重验当前功能权限，并以同一tenant和actor重验FileBusinessScope；随后锁定完整ACTIVE附件集合，再按相同`artifactId/versionNo/referenceKey`在`ACCEPTANCE_REPORT_ARCHIVE`创建独立引用、置ARCHIVED并追加`FileArchiveRecord.archivedBy=actorUserId`。附件引用不得变化；任一步失败归档引用和记录整组回滚，ACC保持`PENDING_COMPENSATION`。

### 3.3 报告、交付件与Outbox事务

1. 草稿创建/修改锁活动和草稿，只允许DRAFT；绑定附件后逐项冻结PLT公共事实，不保存内部ID。
2. 首次发布/替换/撤销锁序固定`ACC活动→旧当前（适用）→目标草稿（适用）→附件ACTIVE集合→初验当前（终验适用）`。版本状态、发布人、活动指针和`AcceptanceReportVersionChanged`通过现有`PlatformCommandExecutionApi`同事务写Outbox。
3. 在`PlatformOutboxDeliveryApiImpl.SUPPORTED_EVENT_TYPES`只加性登记`AcceptanceReportVersionChanged`，不登记`ClosureGateRecheckRequested`。新增`AcceptanceReportOutboxDeliveryJob`，以`PlatformOutboxClaimQuery(..., Set.of("AcceptanceReportVersionChanged"))`领取；反序列化并校验tenant、发布人、版本及完整附件后调用来源投影事务，事务返回成功才把`message.retryCount()`作为`expectedRetryCount`调用`markDelivered`，异常按同一计数调用`scheduleRetry`且不得先标成功。
4. 来源投影以`reportVersionId`幂等更新既有应交根、来源版本和完整附件集合；失败不回滚报告。归档补偿按来源版本稳定顺序、使用事件冻结`publisherActorUserId`调用PLT，成功后才把ACC投影置ARCHIVED。`ClosureGateRecheckRequested`只由报告事务写Outbox，本Feature不领取、不标记已投递、不写CLO表或解释为闭环通过。
5. 报告、来源索引、归档任务均禁止调用AcceptanceScopeBinding API；F-COM两条绑定路径只作回归。

## 四、文件与数据落位

### 4.1 后端与API

- 新增：`pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/`及`dto/`，承载上述两个API和命令/结果。
- 修改：`pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java`及同目录`dto/ExistingFileReferenceTarget.java`；新增同DTO目录`ArchiveFileReferenceSetsCommand.java`、`FileArchiveReferenceSetFact.java`。
- 修改：`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java`、`FileQueryService.java`，以及`dal/mysql/file`和`src/main/resources/mapper/file`中的FileReference/FileArchive Mapper；整组归档显式使用`actorUserId`和SYSTEM `PermissionApi`，只增加ACC目标与整组归档，不改变既有目标语义。
- 修改：`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java`，仅在现有受控事件白名单加`AcceptanceReportVersionChanged`；公开API签名和其他事件不变。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/AcceptanceActivityInitializationApiImpl.java`、`AcceptanceActivityCompletionFactApiImpl.java`、`AcceptanceReportFileBusinessObjectPolicyProvider.java`。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancereport/`、`dal/mysql/acceptancereport/`和`src/main/resources/mapper/acceptancereport/`，分别承载活动、报告版本、报告附件、来源版本和来源附件；现有`AcceptanceDO/Mapper`保持不变。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/`中的`AcceptanceReportCommandService`、`AcceptanceReportQueryService`、`AcceptanceReportSourceProjectionService`、`AcceptanceReportOutboxDeliveryJob`、`AcceptanceReportArchiveCompensationJob`及事件DTO。
- 新增：`pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/acceptancereport/`及VO，实现Feature Spec锁定的查询、草稿、发布、撤销和下载REST；服务端读取租户/操作者、`If-Match`和`Idempotency-Key`。
- 修改：`ProjectManualCreationServiceImpl.java`和`ProjectTaskLifecycleService.java`，只接入批准的initializer/completion Provider，不复制ACC规则或直接访问ACC表。

### 4.2 V128/V129/V130/V131前向迁移

新增`sql/migrations/V128__facc001_acceptance_report_version_forward.sql`，顺序固定：

1. 在任何DDL/DML前同时预检存量V63应交根、精确任务对、当前执行契约和状态分区，以及下列全部受管固定ID/编码无人占用；部分/重复/非TASK_NATIVE/终态混合/未知状态或任一受管身份冲突直接`SIGNAL`，两项均终态或均不存在记录为保持集合。
2. 创建`acc_acceptance`、`acc_acceptance_report_version`（含`publisher_user_id`）、`acc_acceptance_report_attachment`、`acc_project_deliverable_source_version`、`acc_project_deliverable_source_attachment`；字段、生成列、唯一键和公共文件事实逐项等于机器契约，不建PROJ/PLT外键。对`acc_project_deliverable`加性增加`current_source_version_id/archive_status`，两列初始可空。
3. 在统一转换前建立一组完整受管输入：用户/角色`992004800001`，项目`992004000001`（`projectCode=FACC001-ACCEPTANCE-001`，独立`rootId/codeRootId`均指向自身，生命周期有效且当前阶段S5），树版本/自身路径/项目经理成员`992004300001/992004300002/992004300003`，初验/终验任务`992004100001/992004100002`，应交根`992004200001/992004200002`，V63 `TASK_NATIVE`当前执行契约`992004400001/992004400002`；项目树仅含自身深度0且pathCount=1，成员以当前有效`PROJECT_MANAGER`关联受管用户，两个任务均为`PENDING_ACCEPT`并分别精确使用`T-INITIAL-ACCEPT/T-FINAL-ACCEPT`，应交根分别为`D-INITIAL-REPORT/D-FINAL-REPORT`。初始必须无报告、无活动、无ACC契约；所有行使用`creator=facc001_seed`并形成可由`ProjectScopeApi`解析的完整PROJ范围事实。
4. 使用同一存量转换算法处理普通合格项目与上述受管输入：只为“两项均非终态且当前契约均为精确V63 TASK_NATIVE”的项目成对创建PENDING活动、关闭旧契约有效区间并追加ACC当前契约。受管活动固定`992004500001/992004500002`，新ACC契约固定`992004400003/992004400004`，分别与任务、应交根、`PRELIMINARY/FINAL`精确一一对应；两项均终态和两项均不存在不写。转换后逐项目断言活动数、任务/应交/活动/契约父子关系、当前契约唯一、终态零修改，以及受管项目恰有两个PENDING活动、两个ACC当前契约且仍无报告。
5. 写入四个ACC最小权限键及菜单`930920～930924`；受管用户名`facc001acceptance`、角色码`facc001_acceptance_full`。角色取得ACC四键、`pms:project-task:execute`、所需文件键与项目范围；该关系只服务正式验收配置，不定义业务角色模板。
6. Flyway失败重试仅接受“V128未开始”或“全部目标结构、受管输入及统一转换完整”两种状态；任何部分表、部分列、部分受管父子关系、部分任务对切换或非受管占用失败关闭并从迁移前数据库快照恢复，不在脚本中临时补写、推断或修补历史。
7. 真实认证复核发现Yudao会递归过滤父级未授权的菜单；V128已执行且保持不可变。新增`V129__facc001_acceptance_role_menu_ancestor_fix.sql`，仅为受管角色补齐ACC入口、项目任务链和文件链所需的6个既有父级菜单`19260/19266/19261/18000/1243/2`。迁移精确校验受管角色、菜单父子关系和启用状态；仅接受“6条均不存在”后原子插入或“6条均已存在”幂等复核，部分状态失败关闭，不新增权限键、菜单、角色或业务角色模板。
8. 真实完成链复核发现V128活动仍持有已关闭的旧`TASK_NATIVE`契约ID；V128/V129保持不可变。新增`V130__facc001_acceptance_activity_contract_identity_fix.sql`，仅处理`creator=v128-facc001`且同租户、同项目、同任务、`targetObjectKey=acceptanceId`精确匹配的活动。只接受“已指向唯一当前ACC契约”或“仍指向同任务已关闭旧TASK_NATIVE契约”两种状态，后者原子纠正`execution_contract_id`；部分、重复、错身份或未知来源在更新前整批失败。
9. 真实Outbox链复核发现两个ACC `JobHandler`缺少正式调度配置和启动同步。新增`V131__facc001_acceptance_report_jobs.sql`，以固定高段ID配置`acceptanceReportOutboxDeliveryJob`和`acceptanceReportArchiveCompensationJob`两条启用任务，30秒周期、空参数、Quartz重试为0；仅接受全无后成对插入或完整一致幂等复核。新增`AcceptanceReportQuartzRegistrar`，Quartz存在时按“事件投递→归档补偿”固定顺序复用`JobApi.syncEnabledJobByHandlerName`同步，任一同步失败直接使启动失败；Quartz未装配时不调用。两个Handler保持`@TenantJob`多租户路径，并与现有File Outbox一致，在正式单租户配置下显式进入tenant 0后执行，禁止无租户上下文访问业务表。

### 4.3 前端

- 新增`yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/acceptance-report/index.ts`，定义活动、版本、附件公共事实、动作和稳定错误类型。
- 新增`src/views/pms/project/acceptance-report/index.vue`、`detail.vue`、`ReportDraftEditor.vue`、`ReportVersionHistoryDrawer.vue`。
- 复用`PmsFileUploader/PmsFileReferenceList/PmsFileVersionDrawer`，目标固定为ACC附件键；不修改旧`views/pms/project/acceptance/index.vue`。
- UI只渲染服务端允许动作；320/768/1024/1440无页面级横向溢出。归档状态不得禁用历史下载，任务完成按钮仍从项目任务工作台进入。

## 五、实施任务

### Task 1：共享契约、V128/V129与后端正向闭环

**Files：** 第4.1和4.2节后端/API/迁移文件，以及对应聚焦测试。

**Consumes：** 已有`ProjectScopeApi`、Platform命令幂等/Outbox、V63应交根和执行契约、PLT文件上传/查询/归档载体。

**Produces：** 两个ACC活动Provider、PLT整组归档API、报告REST、不可变报告/来源历史、V128可执行前向结构与受管验收事实，以及V129受管身份父级菜单授权闭包。

- [ ] **Step 1：写聚焦失败测试并确认RED**

  新增`AcceptanceActivityInitializationApiImplTest`、`AcceptanceActivityCompletionFactApiImplTest`、`AcceptanceReportCommandServiceTest`、`AcceptanceReportSourceProjectionServiceTest`、`AcceptanceReportOutboxDeliveryJobTest`、`AcceptanceReportFileBusinessObjectPolicyProviderTest`、`Facc001MigrationContractTest`；扩展`PlatformOutboxDeliveryApiImplTest`、`FileArtifactApiImplTest`、`ProjectManualCreationServiceImplTest`、`ProjectTaskLifecycleServiceTest`。RED必须来自目标API/状态/表缺失，不把装配错误当业务RED。聚焦断言必须覆盖归档actor重验、Job只领报告事件、成功后mark/失败retry、CLO事件不被领取，以及ACC任务分别缺少两个权限中的任一项均零写入。

- [ ] **Step 2：实现PLT加性文件契约**

  先完成ACC附件目标、ProjectScope文件Provider、公共事实重验和独立归档集合；验证现有SOL/动态表单目标仍通过，ACTIVE附件下载在归档前后结果相同，归档部分失败零新记录。

- [ ] **Step 3：实现ACC活动、报告和应交来源**

  按第3.3节实现草稿、发布、替换、撤销、终验守卫、专用Outbox投递、事件投影与补偿；稳定错误分类固定BUSINESS_GATE、VERSION_CONFLICT、DEPENDENCY_UNAVAILABLE，所有拒绝零报告/来源/Outbox写入。`ClosureGateRecheckRequested`只保留待消费Outbox事实。

- [ ] **Step 4：接入PROJ创建与任务完成**

  项目创建严格按“任务/非ACC契约/里程碑→应交根→活动→ACC契约”。任务完成先锁定PROJ任务和当前执行契约；若契约目标为`ACC/AcceptanceActivity`，则在调用ACC Provider或写TaskCompletionEvaluation/任务状态前，使用当前认证用户分别校验`pms:project-task:execute`和`pms:acceptance:report:complete`且两者必须都通过。缺任一权限时任务、判定和活动零写入；`TASK_NATIVE`保持现有Controller OR及Service行为。随后才按“PROJ任务/契约→ACC活动→当前报告”完成；缺报告或四项不全不完成任务，进入验收阶段仍不要求报告。

- [ ] **Step 5：实现并验证V128/V129/V130/V131**

  先在当前V127备份上验证预检和两类保持集合，再按“目标结构→完整受管PROJ/V63输入→统一成对转换→权限配置→父菜单授权闭包→活动当前契约身份纠偏→正式Job配置”执行；覆盖空库V1→V131、既有V130→V131、终态混合失败、两项均终态零修改、两项均非终态成对切换、V128活动只指向当前ACC契约、两个Quartz任务启动同步及`migrate/info/validate`。

- [ ] **Step 6：运行Task 1聚焦集合并提交**

```powershell
mvn.cmd -pl pms-module-platform,pms-module-project -am `
  "-Dtest=PlatformOutboxDeliveryApiImplTest,FileArtifactApiImplTest,AcceptanceActivityInitializationApiImplTest,AcceptanceActivityCompletionFactApiImplTest,AcceptanceReportCommandServiceTest,AcceptanceReportSourceProjectionServiceTest,AcceptanceReportOutboxDeliveryJobTest,AcceptanceReportFileBusinessObjectPolicyProviderTest,ProjectManualCreationServiceImplTest,ProjectTaskLifecycleServiceTest,Facc001MigrationContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn.cmd -pl pms-module-platform,pms-module-project -am -DskipTests package
```

  只暂存Task 1文件并形成单一实现提交；不得更新Implementation Done。

### Task 2：公开UI、真实验收与Implementation Done候选

**Files：** 第4.3节前端；`scripts/tests/run_facc001_browser_acceptance.cjs`；`docs/engineering/evidence/f-acc-001-browser-evidence.json`；Task 1必要的MySQL聚焦集成测试。

**Consumes：** Task 1全部公开REST、V128受管身份/项目/活动、PLT Access Ticket与归档事实。

**Produces：** 报告正向UI、一次真实浏览器证据、F-COM绑定回归结果和Implementation Done送审候选。

- [ ] **Step 1：以前端失败测试锁定页面行为**

  覆盖草稿编辑、当前/历史版本、终验守卫提示、归档补偿展示、归档后下载和服务端允许动作；旧V17页面测试保持不变。

- [ ] **Step 2：实现新API客户端和页面**

  接通全部Feature REST和PLT上传/下载组件；不在前端推断项目范围、报告完备、当前版本、归档完成或任务可完成。

- [ ] **Step 3：运行目标前端验证**

```powershell
Push-Location yudao-ui/yudao-ui-admin-vue3
corepack pnpm vitest run --config vitest.pms-file.config.ts `
  src/views/pms/project/acceptance-report src/components/PmsFileArtifact
corepack pnpm ts:check
corepack pnpm build:local
Pop-Location
```

- [ ] **Step 4：运行真实MySQL与F-COM直接回归**

```powershell
mvn.cmd -pl pms-module-platform,pms-module-project,pms-module-commerce -am `
  "-Dtest=Facc001ApplicationMySqlIntegrationTest,FileArtifactEndToEndMySqlIntegrationTest,AcceptanceScopeBindingServiceTest,ProjectAcceptanceStageEntryServiceTest,CommerceDeliveryScopeCommandServiceTest" `
  "-DskipITs=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

  必须确认MySQL测试非SKIPPED；只验证报告版本/历史、任务同成同败、归档补偿、历史下载及F-COM阶段进入/阶段内新范围两条既有绑定路径，不重跑COM完整浏览器套件。

- [ ] **Step 5：使用本分支独占端口完成一次Chromium闭环**

```powershell
$env:NPDMS_BROWSER_API_URL = 'http://localhost:59330/admin-api'
$env:NPDMS_BROWSER_APP_URL = 'http://localhost:19330'
$faccBrowserPassword = [Environment]::GetEnvironmentVariable('FACC001_BROWSER_PASSWORD')
if ([string]::IsNullOrWhiteSpace($faccBrowserPassword)) { throw 'FACC001_BROWSER_PASSWORD is required' }
node scripts/tests/run_facc001_browser_acceptance.cjs
```

  正式身份完成初验草稿→发布V1→创建V2草稿→替换→历史V1下载→终验发布→任务完成→撤销当前；数据库核对报告历史、任务完成判定、应交来源、独立ARCHIVED归档引用、FileArchiveRecord和Outbox。必要负向只保留三项：缺四项报告不能完成任务、跨项目/租户不能查询下载、归档Provider失败不回滚有效报告且可重试。浏览器console/page/request意外错误必须为空。

- [ ] **Step 6：规格检查、证据、提交与送审**

```powershell
python -m unittest scripts.tests.test_facc001_feature_contract
python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check
git diff --check
```

  记录公开身份、最终刷新事实、MySQL事实引用和三项负向结果；不得记录密码、Token或完整商务载荷。更新`tasks/features/F-ACC-001.md`唯一检查点（不超过300字），提交Implementation Done候选并独立送审；GO前保持实施中。

## 六、计划自检

- **规格覆盖：** 报告四状态、当前唯一、替换/撤销、终验守卫、PROJ任务完成、应交来源、专用Outbox投递、归档补偿、下载、权限和F-COM绑定回归均有实施及验收步骤。
- **Owner：** ACC不读PROJ/PLT表；PROJ不写ACC表；PLT不判定报告状态；报告不触发范围绑定。
- **历史：** 有效报告和来源只追加；撤销不恢复旧版；归档不改变ACTIVE附件；终态任务不切换。
- **迁移：** V17/V63及已执行迁移不改；V128生成活动/契约，V129补父菜单授权闭包，V130纠正活动当前契约，V131成对配置并启动同步两个ACC任务。
- **权限：** ACC任务完成在识别执行契约后强制两个最小键同时具备；归档显式actor仍重验功能/租户/文件范围；角色映射保持配置化。
- **验证：** 后端聚焦测试后执行受影响reactor `package`，前端Vitest/类型检查后执行`build:local`；不重复Phase 1/2/3或全仓回归。
- **收益：** 两个完整Task；无全仓重复测试、无第三方连接器、无低收益异常枚举、无第二验收或应交真值。

## 七、Technical Plan Gate

当前状态：`BASELINE / PASS / GO`。独立整改复审已批准创建唯一`tasks/features/F-ACC-001.md`，并按本计划两个串行Task进入Implementation；本结论不代表产品代码、V128、Task完成或Implementation Done已获批准。
