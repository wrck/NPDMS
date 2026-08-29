# ADR-0039：初验终验报告版本与交付件来源索引

> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 日期：2026-08-30<br>
> Requirement：`ACC-03@V1`、`ACC-04@V1`<br>
> 前置批准：下一Feature与最近Gate定位独立裁决GO（基线`21423d9c`）

## 背景

PRD要求初验、终验活动与报告版本分离：项目进入验收阶段先完成范围绑定，不创建报告；活动完成时才校验当前报告的验收时间、结论、验收人和附件。有效报告换版不得覆盖历史，并须自动维护ACC-04交付件来源索引；索引或归档失败不得回滚有效报告，而应进入补偿。

现有V17 `pms_acc_acceptance`是可更新、可删除的单行审批记录，缺少验收人、文件版本引用、不可变报告版本和当前唯一约束。其旧Service、Controller和页面还把交付件齐套作为验收通过前置，与ACC-03“报告形成后成为交付件来源”的方向相反。F-PROJ-001已经创建ACC Owner正式载体`acc_project_deliverable`，新设计不得另建平行应交清单。

## 候选决策

1. `acc_acceptance`是ACC初验/终验活动根。一个项目任务执行契约只能绑定一个活动根；根保存`project_id/project_task_id/execution_contract_id/acceptance_type/activity_status/current_report_version_id/version`。同租户、项目、报告类型唯一，同租户、项目任务唯一。`activity_status`仅为`PENDING/COMPLETED`；报告有效不自动完成活动。
2. `acc_acceptance_report_version`保存只追加报告版本。草稿允许在生效前修正；生效后字段、上传人和附件引用不可更新或删除。每个版本保存`report_version_no/acceptance_time/conclusion_code/conclusion_text/acceptor_name/effective_from/effective_to/current_marker/previous_version_id`及标准审计字段；`current_marker=1`只允许一个当前有效版本。
3. `acc_acceptance_report_attachment`保存报告版本对PLT `FileArtifact/FileVersion`的固定引用及内容哈希。形成有效版本前至少一条附件必须已完成上传、病毒/格式校验并可由ACC业务范围引用；下载每次回源项目资料范围、文件范围和租户，记录下载审计。
4. 终验报告生效前，ACC必须在同租户同项目锁定一个当前有效且四项完备的初验报告版本；缺失、失效或版本变化均拒绝终验生效，且不写当前版本、交付件请求或成功审计。
5. PROJ继续拥有`ProjectTask/TaskWorkBinding/TaskCompletionEvaluation`。初验与终验模板任务使用`targetContextCode=ACC`、`targetObjectType=AcceptanceActivity`，`targetObjectKey`绑定活动ID；ACC不写PROJ表。PROJ完成命令锁定项目任务和执行契约后，以`MANDATORY`调用`AcceptanceActivityCompletionFactApi.lockAndComplete`；ACC只在活动身份、活动版本、当前报告版本及四项完备均精确匹配时把活动置为`COMPLETED`，随后PROJ追加完成判定并把任务置为`DONE`，任一失败整体回滚。锁序固定为PROJ项目任务/执行契约→ACC活动根→当前报告版本。
6. F-COM-001已交付的`AcceptanceScopeBindingApi/AcceptanceScopeGuardApi/DeliveryScopeAcceptanceLockApi`及阶段进入、验收阶段内新范围两条原子路径直接复用并纳入ACC-03回归。报告、活动和交付件状态不得触发、补建、关闭或反推范围绑定；`Q-FCOM-002`边界不变。
7. `acc_project_deliverable`继续作为ACC项目应交实例和ACC-04唯一索引根。F-ACC-001只对`D-INITIAL-REPORT`、`D-FINAL-REPORT`两种已冻结交付件码复制增强来源字段：`source_requirement_id/source_object_type/source_object_id/source_version/file_artifact_id/file_version_id/file_hash/archive_status/archive_failure_code/archive_retry_count/archive_time`。不得以名称、任务名或旧`D-ACCEPT-REPORT`推断初验/终验类型；缺少精确应交实例时报告仍有效，但归档状态为`PENDING_COMPENSATION`。
8. 报告版本生效、替换或撤销与同事务Outbox一起提交。消费者按`tenant_id + source_object_type + source_object_id + source_version`幂等更新同一`acc_project_deliverable`来源指针和归档状态，并请求`ClosureGateRecheckRequested`。索引、文件归档或CLO消费者失败只保留失败原因和重试水位，不覆盖或删除报告版本，也不把应交实例误标为`ARCHIVED`。
9. 最小权限键为`pms:acceptance:report:query/write/complete/download`。角色映射保持正式授权配置；服务端始终执行租户、项目任务/项目树范围与FileBusinessScope，具备全部权限通过授权关系实现，不删除鉴权或租户隔离。

## 物理差量

| 表 | Feature-forward差量 | 关键约束 |
|---|---|---|
| `acc_acceptance` | 新建活动根；字段见决策1 | `uk(tenant_id, project_id, acceptance_type)`、`uk(tenant_id, project_task_id)`；类型仅`PRELIMINARY/FINAL`，状态仅`PENDING/COMPLETED` |
| `acc_acceptance_report_version` | 新建报告版本 | `uk(tenant_id, acceptance_id, report_version_no)`、`uk(tenant_id, acceptance_id, current_marker)`；有效版本四项非空，`effective_to`为空时`current_marker=1` |
| `acc_acceptance_report_attachment` | 新建固定文件版本引用 | `uk(tenant_id, report_version_id, file_artifact_id, file_version_id)`；不保存文件正文 |
| `acc_project_deliverable` | 在V63正式表上加性增加决策7字段 | 既有`uk(tenant_id, project_id, deliverable_code)`保持；来源身份唯一且只接受ACC Owner事件更新；归档失败不得写`ARCHIVED` |

不修改已执行V17、V63或当前核心DDL；未来Technical Plan只能创建新的前向Flyway。P3-E09结论为`FEATURE_FORWARD_DELTA_REQUIRED`，本ADR获独立GO前不得生成Feature Spec或实施迁移。

## 旧载体复用审计结论

| 载体 | 判定 | 边界 |
|---|---|---|
| V17 `pms_acc_acceptance`、旧Acceptance Service/Controller/UI | `DO_NOT_REUSE` | 保持旧功能和旧数据不变；不迁成当前有效报告，不推断验收人、附件、初验前置或活动完成事实；新Feature使用新类、新页面和新表 |
| V17 `pms_acc_deliverable_checklist/pms_acc_archive_document/pms_acc_completion_certificate`及旧服务 | `DO_NOT_REUSE` | 保留旧闭环消费，不承接F-ACC-001应交身份、来源索引、归档状态或补偿水位 |
| V63 `acc_project_deliverable`、ACC初始化接口和项目创建原子事务 | `DIRECT_REUSE` + `COPY_THEN_ENHANCE` | 直接复用应交实例身份、项目创建全有或全无及唯一键；新增独立来源索引服务和加性字段，不改初始化完成语义 |
| F-PROJ-007 ProjectTask执行契约、完成判定与锁定模式 | `COPY_THEN_ENHANCE` | 复用任务Owner、幂等、完成判定和行锁模式；新增ACC非原生WorkBinding Provider分支，不复制第二套任务状态机 |
| F-COM-001 AcceptanceScopeBinding真实Provider | `DIRECT_REUSE` | 仅做ACC-03回归；报告不得参与绑定身份、触发或关闭 |
| `ProjectClosureServiceImpl`对旧终验状态的消费 | `DO_NOT_REUSE` | CLO-01/02 Feature前保持不变；F-ACC-001只发重校验请求，不用旧消费证明新报告闭环 |

## 明确排除

- 不实现ACC-01、ACC-02、ACC-04其余四类来源、统一批量下载或CLO-01/02。
- 不决定`Q-FCOM-002`，不修改项目阶段进入、范围绑定或项目任务业务Owner。
- 不固定角色—权限映射，不修改Yudao基础平台，不实现第三方平台连接器。
- 不批准Feature Ready、Technical Plan、产品代码、Flyway、历史迁移、SIT、UAT或Release。
