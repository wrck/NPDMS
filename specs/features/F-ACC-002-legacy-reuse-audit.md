# F-ACC-002 旧实现复用审计

> Requirement：`ACC-02@V1`、`ACC-04@V1（满意度来源切片）`
> 结论：`COMPLETE / NO_PENDING_DECISION_FOR_NEW_ONLY_PATH`

| ID | 现有载体 | 判定 | Feature边界 |
|---|---|---|---|
| REUSE-01 | 旧`pm_cl_quesnaire_template_header/line/options`与`pm_cl_quesnaire_result_header/line` | `DO_NOT_REUSE_RUNTIME / PRESERVE_RAW` | 缺稳定必答、客户身份、签字公共文件和通过值映射；等待AI-MIG-000，不迁为当前问卷/答卷/Result |
| REUSE-02 | `pm_cl_callback*`、`pm_subcontract_project_callback` | `DO_NOT_REUSE / PRESERVE_RAW` | 只保留关系/状态证据，不推断业务时点、客户提交、整改、评分或通过 |
| REUSE-03 | `pm_presales_project_callback`、`pm_project_warranty_callback`、`pm_project_maintenance*`、`pm_project_supervision`、`pm_daily_report`问卷引用/缓存分数 | `DO_NOT_REUSE / PRESERVE_RAW` | 不进入F-ACC-002正向路径；旧页面、报表和字段保持不变 |
| REUSE-04 | PROJ `proj_project_task`、`ProjectWorkBindingFactApi/Impl`、Mapper与聚焦测试 | `COPY_THEN_ENHANCE` | 直接复用项目/任务/版本/当前责任人重验；加性冻结ACC模板Fact，不复制第二项目任务真值 |
| REUSE-05 | V55 `T-SAT-SURVEY→D-SAT-REPORT`、V63 `acc_project_deliverable` | `DIRECT_REUSE` | 仅同租户同项目稳定码精确关系可承接满意度来源；禁止名称、其他根或任意选择 |
| REUSE-06 | `ProjectDeliverableInitializationApplicationServiceImpl`与`AccProjectDeliverableMapper` | `DIRECT_REUSE` | 项目创建继续形成唯一应交根；F-ACC-002不新建平行应交清单 |
| REUSE-07 | F-ACC-001 `acc_project_deliverable_source_version/source_attachment`、来源投影与历史补偿模式 | `COPY_THEN_ENHANCE` | 复用来源历史、完整文件集合、当前指针和补偿语义；只加SatisfactionResult来源，不改变报告来源行为 |
| REUSE-08 | PLT `FileArtifactApi/Impl`、`FileQueryService`、Access Ticket与文件策略Provider | `COPY_THEN_ENHANCE` | 复用内部正式上传/查询/下载；加性业务grant上传只接受ACC已验证授权，不伪造登录或读取PLT表 |
| REUSE-09 | PLT归档状态机、`FileArchiveRecord`、`archiveReferenceSets`及`pms:file:archive` | `DIRECT_REUSE` | Result ACTIVE文件历史下载与独立ARCHIVED集合分离；归档actor和范围每次重验 |
| REUSE-10 | `PlatformCommandExecutionApi`、`PlatformOutboxDeliveryApi`及F-ACC-001专用投递模式 | `DIRECT_REUSE + COPY_THEN_ENHANCE` | 复用事务Outbox/claim/mark/retry；新增Job只领取SatisfactionResultVersionChanged，不误标CLO/SUB事件 |
| REUSE-11 | PLT Todo及PROJ任务工作台 | `DIRECT_REUSE` | 只投影责任人待办和业务工作台入口；Todo完成不得反向制造客户提交或通过 |
| REUSE-12 | 旧关项与转包满意度状态消费者 | `DO_NOT_REUSE` | 本Feature只公开不可变Result Fact；不改CLO/SUB状态、不把旧缓存值冒充Owner Fact |
| REUSE-13 | Yudao基础平台认证、权限与租户控制点 | `DIRECT_REUSE_NO_SOURCE_CHANGE` | 使用现有控制点；不得修改Yudao源码、删除鉴权或固定角色映射。ADR-0014逻辑决策本身不是可运行导出载体 |
| REUSE-14 | F-ACC-001 `AcceptanceActivityCompletionFactApi`真实Provider与初验活动完成事务 | `COPY_THEN_ENHANCE` | 复用已交付初验完成Fact作为`AFTER_INITIAL_ACCEPTANCE`正向触发；只在冻结时点匹配时同事务调用满意度initializer，不以报告上传、Todo或项目阶段推断 |
| REUSE-15 | PROJ `ProjectScopeApi/ProjectScopeApiImpl`、`ProjectTreeScopeService`及树版本测试 | `DIRECT_REUSE` | 查询/导出使用当前项目集合，下载和失效写入按`PROJECT_VIEW/PROJECT_EDIT`重验；返回`treeVersion`作为文件策略唯一`scopeVersion`，ACC不复制项目树/授权算法或跨Context读表 |
| REUSE-16 | V17 `pms_acc_completion_certificate`、`CompletionCertificateController/Service/ServiceImpl/DO/Mapper`、V18菜单19094/19100～19104、旧API、`completion-certificate`页面及项目详情投影 | `DO_NOT_REUSE / PRESERVE_EXISTING` | 旧电子完工证明及其客户确认、归档、状态和`satisfactionScore`继续保持原行为；不得作为F-ACC-002 Task/Questionnaire/Result、阈值、满意度来源或CLO/SUB事实，不修改旧表、接口、菜单和页面 |
| REUSE-17 | PLT `FileUploadApplicationService`、`FileUploadSession`、`FileUploadCompensationService`及Artifact/Version/Reference持久化链 | `COPY_THEN_ENHANCE_BEHIND_PUBLIC_API` | 只在PLT内部复用生成文档的内容校验、存储、幂等与未引用对象补偿；ACC仅调用加性`FileArtifactApi.createGeneratedBusinessFile`，不得依赖PLT Service/Mapper/表或伪造登录上下文 |
| REUSE-18 | 仓库现状中的ADR-0014及全量Java/XML/SQL导出载体检索 | `NO_RUNTIME_CARRIER / BUILD_APPROVED_ADR_0042` | 当前没有`ExportTask/ExportAudit`公共API、物理表或执行Job可直接复用；按ADR-0042在PLT唯一新增`ExportTaskApi`、`ExportBusinessDataProvider`、`plt_export_task/plt_export_audit`和执行/到期Job，ACC只提供`ACC/SATISFACTION_RESULT`场景Provider，不新建第二导出真值 |

迁移结论：F-ACC-002正向事实与PLT统一导出载体均为`NEW_ONLY / FEATURE_FORWARD_MIGRATION`；所有旧问卷、回访和转包载体保持原始状态并交由`AI-MIG-000`后续确认，V17/V18电子完工证明栈保持独立可达。Technical Plan不得引入旧值映射、名称/分数/状态推断、长期双写、第二应交根、第二导出Task/Audit、PLT内部主键，或把旧完工证明事实升级为新满意度真值。

## 代码事实实施状态（2026-09-04三分支重放）

- 已接收代码路径：`28`。
- 已处理来源提交：`18`。
- 实施状态：已实现切片进入集成分支；未关闭Gate时Feature保持 `IN_PROGRESS`。
- 追溯明细：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。
