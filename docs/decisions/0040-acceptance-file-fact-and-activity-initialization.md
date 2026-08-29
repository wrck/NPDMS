# ADR-0040：验收附件公共事实与活动初始化

> 状态：`ACCEPTED`<br>
> 日期：2026-08-30<br>
> Requirement：`ACC-03@V1`、`ACC-04@V1`<br>
> 触发：F-ACC-001 Feature Ready独立审核NO-GO；本ADR只细化ADR-0039的文件边界和活动初始化，不改变PRD业务语义

## 背景

ADR-0039已锁定初验/终验活动、不可变报告版本和交付件来源索引，但候选Feature使用了PLT未公开的`file_version_id`，也没有锁定ACC文件Owner策略、归档调用以及项目创建时活动ID和执行契约的生成顺序。存量V63又已为全部ProjectTask初始化`TASK_NATIVE`当前执行契约，不能把终态任务静默切换成可完成的ACC活动。

## 候选决策

1. ACC只保存PLT稳定公共文件事实：`artifactId/versionNo/referenceKey/fileFactVersion(artifactVersion/referenceVersion/availabilityVersion)/scopeVersion/sha256`。PLT内部`FileVersion.id/FileReference.id`不得进入ACC表、事件或跨Context接口，ACC不得读取PLT表。
2. ACC实现`FileBusinessObjectPolicyProvider`，同一报告版本使用两个固定集合：持续访问键为`ownerContext=ACC/objectType=ACCEPTANCE_REPORT_VERSION/objectId=reportVersionId十进制字符串/purposeCode=ACCEPTANCE_REPORT_ATTACHMENT`，归档键仅把`purposeCode`改为`ACCEPTANCE_REPORT_ARCHIVE`。`referenceKey`由服务端为每个附件槽生成并持久化；同一文件在两个集合复用同一UUID referenceKey。Provider由报告版本解析不可变`projectId/projectTaskId`，再调用PROJ `ProjectScopeApi`；其`scopeVersion`精确等于返回的当前`treeVersion`。
3. 文件动作映射固定：附件集合的`READ/DOWNLOAD`要求`PROJECT_VIEW`，`UPLOAD/REFERENCE/REPLACE/DETACH`要求`PROJECT_EDIT`；归档集合只允许ACC受信补偿消费者执行`ARCHIVE`，不提供下载。所有路径同时保留PLT文件功能权限与租户隔离，具备全部权限只通过正式授权配置实现。
4. 新文件继续使用PLT现有上传REST；`ExistingFileReferenceTarget`加性支持唯一ACC目标`ACC/ACCEPTANCE_REPORT_VERSION/*/ACCEPTANCE_REPORT_ATTACHMENT`且保留既有SOL与动态表单目标，绑定既有版本仍调用`FileArtifactApi.attachExistingVersions`。报告发布、活动完成和后续下载只对持续访问集合使用`lockAndRevalidateReferenceSets/inspectReferenceSets`及现有Access Ticket REST；报告附件ACTIVE引用保持不变，不建设ACC文件代理。
5. PLT加性公开`FileArtifactApi.archiveReferenceSets`窄接口：命令包含`operationId/archiveBatchId/businessDecisionRef`、附件集合键、归档集合键、期望`scopeVersion`和完整期望公共文件事实。PLT持锁重验附件ACTIVE集合后，按每项相同artifactId/versionNo/referenceKey在`ACCEPTANCE_REPORT_ARCHIVE`集合创建独立归档引用，追加不可变`FileArchiveRecord`并把归档引用置`ARCHIVED`；不得改变附件集合引用状态。整组同事务、同批同摘要幂等，部分集合、版本漂移或Provider不可用失败关闭。
6. PLT的归档引用和`FileArchiveRecord`是文件归档Owner事实；持续访问的报告附件引用保持`ACTIVE`，因此历史报告继续通过现有下载链访问。ACC `archive_status/archive_failure_code/archive_retry_count/archive_time`仅是来源索引和补偿投影；仅在整组归档引用及记录成功后写`ARCHIVED`，失败保持`PENDING_COMPENSATION`。
7. ACC加性公开`AcceptanceActivityInitializationApi.initialize`并以`MANDATORY`加入项目创建事务。PROJ先生成ProjectTask和非ACC执行契约、里程碑，再调用既有ACC `ProjectDeliverableInitializationApplicationService`形成应交根；PROJ为精确验收任务预分配`executionContractId`后调用activity initializer。输入逐项包含`projectId/projectTaskId/taskDefinitionKey/executionContractId/acceptanceType/deliverableCode/templateRevision`。ACC精确校验应交根后创建PENDING活动并返回`acceptanceId/activityVersion`；PROJ随后以返回ID追加当前执行契约`ACC/AcceptanceActivity/acceptanceId`。任一步失败使项目、任务、应交根、活动和执行契约整体回滚；PROJ不得直接写ACC表。
8. 新项目只接受两组冻结映射：`T-INITIAL-ACCEPT→PRELIMINARY→D-INITIAL-REPORT`、`T-FINAL-ACCEPT→FINAL→D-FINAL-REPORT`。缺失、部分、重复、类型或应交码不一致整批失败；不得按名称、URL或旧`D-ACCEPT-REPORT`推断。
9. 存量切换按项目成对判定：两项精确任务均不存在时保持不变；仅一项、重复、缺应交根、当前契约缺失或非V63 `TASK_NATIVE`时整批失败；两项均为`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT`且当前契约均为V63 `TASK_NATIVE`时，原子创建两个PENDING活动、关闭旧契约区间并追加ACC当前契约；两项精确任务均为`DONE/CLOSED`时整项目保持旧契约和历史不变且不创建活动；终态与非终态混合时整批失败，未知状态同样失败。不得覆盖终态历史或制造不可完成的孤立活动。

## 物理差量

`acc_acceptance_report_attachment`与`acc_project_deliverable_source_attachment`均保存`file_artifact_id bigint`、`file_version_no int unsigned`、`reference_key varchar(64)`、`artifact_version/reference_version/availability_version int unsigned`、`scope_version bigint`和`file_hash char(64)`；不保存PLT内部`file_version_id/reference_id`。既有报告、来源版本、当前唯一和历史约束不变。

本差量不修改已执行V17/V63、当前产品代码或核心DDL；未来Feature Technical Plan只能新增前向迁移。PLT窄归档API与ACC initializer在实现前必须先由本Gate独立复审通过。

## 边界

- 不修改PRD、F-COM-001范围绑定路径、ACC-04其他来源、CLO实现或第三方归档连接器。
- 不新增角色模板，不取消PLT/ACC权限、项目范围或租户隔离。
- 不以本ADR状态提前批准F-ACC-001 Feature Ready、Technical Plan、代码或Flyway。
