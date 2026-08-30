# ADR-0042：统一业务导出任务与永久审计载体

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 日期：2026-08-30<br>
> Requirement：`ACC-02@V1`，并落实ADR-0014、ADR-0016<br>
> 触发：F-ACC-002 Technical Plan整改复审指出仓库尚无可调用的`ExportTask/ExportAudit`公共载体

## 决策

1. PLT拥有唯一`ExportTask/ExportAudit`聚合、异步执行调度、导出文件技术生命周期和永久审计；ACC只拥有满意度查询语义与字段裁剪，不新建领域导出任务或第二审计真值。
2. 在既有`pms-module-platform-api`加性公开`ExportTaskApi.request/getFact/retry`；请求固定携带服务端认证得到的`tenantId/actorUserId`、`operationId`、`ownerContext/exportType`、规范化业务条件和请求字段/文件意图。ACC固定使用`ACC/SATISFACTION_RESULT`，客户端不得覆盖租户、actor、Owner或导出类型。
3. PLT按`ownerContext+exportType`选择唯一`ExportBusinessDataProvider`。ACC Provider在申请、异步生成和下载三个时点分别重验`pms:acceptance:satisfaction:export`、PROJ `ProjectScopeApi`、责任人、字段、文件及租户范围；返回规范化条件、范围版本和已裁剪内容。零Provider、重复Provider、权限/范围未知或不可用均失败关闭，不复制业务范围算法到PLT。
4. `operationId+规范化请求摘要`在同租户、同actor、同导出类型内幂等：同载荷返回原Task，异载荷冲突。状态只允许`REQUESTED -> GENERATING -> SUCCEEDED|FAILED|REJECTED`、`FAILED(retryable=true) -> REQUESTED`和`SUCCEEDED -> EXPIRED`；FAILED/REJECTED不得进入EXPIRED，下载是追加审计动作。
5. PLT新增唯一`plt_export_task`和只追加`plt_export_audit`。Task保存Owner键、actor、请求摘要、规范化条件/范围/字段/文件快照、scopeVersion、状态、结果数量、公共文件事实、`failure_code/failure_retryable/retry_count`和24小时到期时间；Audit保存申请、开始、成功、失败、拒绝、重试申请、每次下载和到期清理。两表不保存业务秘密或完整敏感答卷。
6. `ExportTaskExecutionJob`只领取`REQUESTED`并以Task版本CAS推进；生成前重验Provider，成功文件固定为`PLATFORM/EXPORT_TASK/{taskId}/EXPORT_FILE`，复用PLT内容检查、私有存储、FileArtifact/FileVersion/FileReference和公共文件事实。暂时生成、扫描或存储不可用写`FAILED + failure_retryable=true`；载荷无法生成或Provider契约永久错误写`FAILED + false`；权限/范围拒绝写`REJECTED`。三类都追加安全失败审计，不创建第二Task或第二文件真值。
7. 平台公开`GET /api/v1/pms/export-tasks/{id}`、`POST /api/v1/pms/export-tasks/{id}/actions/retry`和`POST /api/v1/pms/export-tasks/{id}/access-ticket`。retry只允许原申请actor对`FAILED + retryable=true`调用，重新执行三类权限/范围校验后以expected Task version CAS回到`REQUESTED`、递增`retry_count`、清空当前失败字段并追加`RETRY_REQUESTED`；并发、非可重试、REJECTED/SUCCEEDED/EXPIRED或版本不符均拒绝。同operation再次request只返回原Task，不隐式重试。状态查询与下载同样只允许原actor；文件未成功、已到期或权限变化时拒绝且不泄露对象存在性。
8. `ExportFileExpirationJob`在成功后24小时删除文件内容、把Task推进`EXPIRED`并追加清理审计；Task与Audit永久保留。清理失败保持可重试，不伪造已过期完成。
9. F-ACC-002的`POST /api/v1/pms/satisfaction-results/exports`只调用`ExportTaskApi.request`并返回`taskId/status/queryLocation`；页面轮询平台状态并通过平台Access Ticket下载。角色—权限映射继续正式配置，不新增审批、业务角色或第三方连接器。

## P3-E09 Feature-forward差量

| 表 | 关键字段 | 约束与语义 |
|---|---|---|
| `plt_export_task` | `tenant_id/owner_context/export_type/operation_id/request_digest/actor_user_id/filter_snapshot/scope_snapshot/requested_fields_snapshot/include_files/scope_version/task_status/result_count/artifact_id/file_version_no/reference_key/artifact_version/reference_version/availability_version/file_hash/expires_at/failure_code/failure_retryable/retry_count/version`及标准审计字段 | `uk(tenant_id, owner_context, export_type, actor_user_id, operation_id)`；FAILED必须有失败码/可重试标记，其他状态清空两者；retry_count从0递增；成功文件公共事实整组同时为空或同时非空；Task不逻辑删除 |
| `plt_export_audit` | `tenant_id/export_task_id/audit_sequence/action_code/actor_user_id/detail_snapshot/occurred_at`及创建审计字段 | `uk(tenant_id, export_task_id, audit_sequence)`；只追加，动作固定REQUESTED/GENERATION_STARTED/SUCCEEDED/FAILED/REJECTED/RETRY_REQUESTED/DOWNLOADED/EXPIRED，不另建清理审计 |

两表均为`NEW_ONLY / FEATURE_FORWARD_MIGRATION`，不从`plt_operation_audit`、旧导出文件或业务表反推历史Task；不修改已执行迁移。当前Gate只锁定公共契约与物理目标，不批准Flyway或实现。

## 明确排除

- 不在PLT实现ACC字段含义、满意度评分或项目范围算法。
- 不允许业务Context直写`plt_export_*`或PLT读取ACC业务表。
- 不把同步HTTP文件流、临时内存Job、`plt_operation_audit.detail_snapshot`或浏览器本地文件冒充统一异步导出载体。
- 不批准F-ACC-002 Technical Plan GO、Feature Task、产品代码、V133、运行迁移或后续Gate。
