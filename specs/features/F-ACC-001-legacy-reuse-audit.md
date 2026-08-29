# F-ACC-001 旧实现复用审计

> Requirement：`ACC-03@V1`、`ACC-04@V1（初验/终验来源切片）`
> 结论：`COMPLETE / NO_PENDING_DECISION`

| ID | 现有载体 | 判定 | Feature边界 |
|---|---|---|---|
| REUSE-01 | `V17__pms_acceptance_tables.sql`的`pms_acc_acceptance`、`AcceptanceController/Service/DO/Mapper`与`yudao-ui/.../acceptance/index.vue` | `DO_NOT_REUSE` | 可更新单行与旧审批/交付齐套语义保持不变；不迁为活动、当前报告或完成事实，新类/表/页面承接F-ACC-001 |
| REUSE-02 | V17 `pms_acc_deliverable_checklist/pms_acc_archive_document/pms_acc_completion_certificate`、`DeliverableChecklistService` | `DO_NOT_REUSE` | 不承接应交身份、来源版本、归档状态或补偿水位；旧功能不改 |
| REUSE-03 | V19/V20/V35旧验收测试数据 | `DO_NOT_REUSE` | 只作旧功能回归，不从日期、状态、意见或URL补造验收人、固定附件或当前版本 |
| REUSE-04 | V63 `acc_project_deliverable`、`AccProjectDeliverableDO/Mapper` | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 直接复用唯一应交根和`uk(tenant_id,project_id,deliverable_code)`；加性增加当前来源指针/摘要并新建来源版本和附件从表，不建第二清单 |
| REUSE-05 | `ProjectDeliverableInitializationApplicationServiceImpl`及测试 | `COPY_THEN_ENHANCE` | 复用PROJ创建事务中的全有或全无和批量完整性；只为精确D-INITIAL/D-FINAL扩展ACC活动/来源初始化，不改变其他应交 |
| REUSE-06 | V55 `T-INITIAL-ACCEPT/T-FINAL-ACCEPT`与`D-INITIAL-REPORT/D-FINAL-REPORT` | `DIRECT_REUSE` | 仅这四个稳定定义键可决定初验/终验绑定；禁止按中文名称、阶段或旧D-ACCEPT-REPORT推断 |
| REUSE-07 | `ProjectTaskWorkbenchController`与`ProjectTaskLifecycleService` | `COPY_THEN_ENHANCE` | 复用公开complete命令、平台幂等、PROJ锁和完成判定；为ACC WorkBinding增加Owner Provider分支，不复制第二任务状态机 |
| REUSE-08 | `proj_project_task_execution_contract`与TaskCompletionEvaluation | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 保留版本化执行契约/只追加完成判定；精确任务追加ACC当前契约，禁止原地改历史契约或从TASK_NATIVE结果反推活动完成 |
| REUSE-09 | F-COM-001 `AcceptanceScopeBindingApi/AcceptanceScopeGuardApi/DeliveryScopeAcceptanceLockApi`及`AcceptanceScopeBindingServiceTest` | `DIRECT_REUSE` | 纳入ACC-03两条正向绑定回归；报告/活动/归档不得成为绑定身份或触发 |
| REUSE-10 | PLT `FileArtifact/FileVersion`、FileBusinessScope与下载审计 | `DIRECT_REUSE` | 保存固定引用和哈希；每次下载重验范围，不复制文件正文或建设第二文件库 |
| REUSE-11 | `ProjectClosureServiceImpl`及`ProjectClosureStateAdapterTest` | `DO_NOT_REUSE` | 旧终验状态消费保持不变；F-ACC-001只发CLO重校验请求，不用旧关项结果证明新报告齐套 |

迁移结论：V17报告/归档栈为`NEW_ONLY`，不迁旧业务行；F-PROJ-001当前任务只在稳定任务定义键、项目/任务/当前执行契约和应交根关系全部精确唯一时追加ACC活动与新执行契约，任一缺失、歧义或部分命中整批失败。Technical Plan不得引入名称推断、旧状态映射、长期双写或第二应交真值。
