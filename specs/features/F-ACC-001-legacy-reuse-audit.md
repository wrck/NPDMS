# F-ACC-001 旧实现复用审计

> Requirement：`ACC-03@V1`、`ACC-04@V1（初验/终验来源切片）`
> 结论：`COMPLETE / NO_PENDING_DECISION`

| ID | 现有载体 | 判定 | Feature边界 |
|---|---|---|---|
| REUSE-01 | `V17__pms_acceptance_tables.sql`的`pms_acc_acceptance`、`AcceptanceController/Service/DO/Mapper`与`yudao-ui/.../acceptance/index.vue` | `DO_NOT_REUSE` | 可更新单行与旧审批/交付齐套语义保持不变；不迁为活动、当前报告或完成事实，新类/表/页面承接F-ACC-001 |
| REUSE-02 | V17 `pms_acc_deliverable_checklist/pms_acc_archive_document/pms_acc_completion_certificate`、`DeliverableChecklistService` | `DO_NOT_REUSE` | 不承接应交身份、来源版本、归档状态或补偿水位；旧功能不改 |
| REUSE-03 | V19/V20/V35旧验收测试数据 | `DO_NOT_REUSE` | 只作旧功能回归，不从日期、状态、意见或URL补造验收人、固定附件或当前版本 |
| REUSE-04 | V63 `acc_project_deliverable`、`AccProjectDeliverableDO/Mapper` | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 直接复用唯一应交根和`uk(tenant_id,project_id,deliverable_code)`；加性增加当前来源指针/摘要并新建来源版本和附件从表，不建第二清单 |
| REUSE-05 | `ProjectManualCreationServiceImpl`、`ProjectDeliverableInitializationApplicationServiceImpl`及测试 | `COPY_THEN_ENHANCE` | 复用项目创建事务、任务/非ACC契约/里程碑和唯一应交根；按“应交根→ACC活动initializer→ACC当前契约”加性编排，不改变其他应交或由PROJ写ACC表 |
| REUSE-06 | V55 `T-INITIAL-ACCEPT/T-FINAL-ACCEPT`与`D-INITIAL-REPORT/D-FINAL-REPORT` | `DIRECT_REUSE` | 仅这四个稳定定义键可决定初验/终验绑定；禁止按中文名称、阶段或旧D-ACCEPT-REPORT推断 |
| REUSE-07 | `ProjectTaskWorkbenchController`与`ProjectTaskLifecycleService` | `COPY_THEN_ENHANCE` | 复用公开complete命令、平台幂等、PROJ锁和完成判定；为ACC WorkBinding增加Owner Provider分支，不复制第二任务状态机 |
| REUSE-08 | V63 `proj_project_task_execution_contract`与TaskCompletionEvaluation | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 保留版本化执行契约/只追加完成判定；存量两项均非终态且精确TASK_NATIVE时切换，两项均DONE/CLOSED保持，终态混合/未知失败；禁止原地改历史契约或反推活动完成 |
| REUSE-09 | F-COM-001 `AcceptanceScopeBindingApi/AcceptanceScopeGuardApi/DeliveryScopeAcceptanceLockApi`及`AcceptanceScopeBindingServiceTest` | `DIRECT_REUSE` | 纳入ACC-03两条正向绑定回归；报告/活动/归档不得成为绑定身份或触发 |
| REUSE-10 | PLT `FileArtifactApi.attachExistingVersions`、`ExistingFileReferenceTarget`及上传链 | `COPY_THEN_ENHANCE` | 只为`ACC/ACCEPTANCE_REPORT_VERSION/*/ACCEPTANCE_REPORT_ATTACHMENT`加性开放既有文件绑定目标并保留SOL/动态表单目标；ACC只保存公共版本事实，不读取内部表或ID |
| REUSE-11 | `ProjectClosureServiceImpl`及`ProjectClosureStateAdapterTest` | `DO_NOT_REUSE` | 旧终验状态消费保持不变；F-ACC-001只发CLO重校验请求，不用旧关项结果证明新报告齐套 |
| REUSE-12 | PLT `FileQueryService`、`inspectReferenceSets/lockAndRevalidateReferenceSets`与Access Ticket下载审计 | `DIRECT_REUSE` | 报告附件引用持续ACTIVE；每次查询/下载经ACC Provider、项目范围、文件范围和租户重验，不让ARCHIVED归档引用承接下载 |
| REUSE-13 | PLT现有归档状态机、`FileArchiveRecord`与`pms:file:archive`控制点 | `COPY_THEN_ENHANCE` | 加性`archiveReferenceSets`先重验完整ACTIVE附件集合，再在独立`ACCEPTANCE_REPORT_ARCHIVE`集合建立ARCHIVED引用及归档记录；失败整组回滚且附件引用不变 |
| REUSE-14 | PROJ `ProjectScopeApi`及项目树版本事实 | `DIRECT_REUSE` | ACC文件策略Provider用当前`treeVersion`作为唯一`scopeVersion`并区分PROJECT_VIEW/PROJECT_EDIT；不得用报告版本、固定值或ACC本地范围替代 |

迁移结论：V17报告/归档栈为`NEW_ONLY`，不迁旧业务行；F-PROJ-001当前任务只在稳定任务定义键、项目/任务/当前执行契约和应交根关系全部精确唯一时处理：两项均非终态原子切换，两项均终态保持旧事实，终态混合、未知、缺失、歧义或部分命中整批失败。Technical Plan不得引入名称推断、旧状态映射、内部PLT主键、长期双写或第二应交真值。
