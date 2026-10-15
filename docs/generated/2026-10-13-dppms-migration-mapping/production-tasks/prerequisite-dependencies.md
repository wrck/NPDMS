# 组织、账号及审计前置依赖矩阵

仅P06/P07要求当前版本真实非preview SUCCESS完整证据，其余按本行目录/绑定校验。相对链接以最终仓库OUT报告目录解析。

| 任务 | 真实校验 / 正式Owner | 预览边界 | 具体缺项 |
| --- | --- | --- | --- |
| P01 | 同批组织及补齐部门父节点上下文；SYSTEM ManagedOrganizationApi / SystemDepartmentMigrationApi | 合并协调器先组织preview，再经SystemDepartmentMigrationApi.previewAfterOrganization接收同批已校验拟建父节点；不生成正式ID/来源绑定，8主管待正式账号。 | 8主管待P04正式账号/来源绑定后同P01回补；phase4当前包组织/父alias预览已通过 |
| P04 | 任职资料公司/部门/岗位必须解析为现有正式目录；SYSTEM CompanyApi / SystemDepartmentMigrationApi / PostApi / SystemUserMigrationApi | 不检查P01成功Run；但preview同一execute(false)先调用正式目录API，因此未落地的P01拟创建结果不能替代目录。 | 正式公司/部门/岗位目录精确解析 |
| P05 | 角色审计人员依赖DPPMS_USERS正式来源绑定；INT SyncBindingMapper→SYSTEM SystemRoleMigrationApi | 不检查成功Run本身；selectIdentitySources需要同租户/连接/适配器的非删除正式绑定且targetId存在。preview在Owner.preview前执行该校验。 | DPPMS_USERS正式来源绑定及有效targetId，角色原审计来源精确匹配 |
| P06 | EVIDENCE来源读取需要前置当前版本真实完整成功迁入；INT SyncSourceEvidenceReader→PLT PlatformMigrationEvidenceApi；SYSTEM SystemUserRoleMigrationApi | 仅FND_USER_ROLE的fnd_user_info来源引用P04(DPPMS_USERS)当前版真实非preview full SUCCESS；T_USER_ROLE本任务SQL。另需正式用户/角色绑定，不要求P05角色定义成功Run。 | P04当前版完整成功证据与正式用户/角色绑定；不包含P05 SUCCESS Run前置 |
| P07 | EVIDENCE来源及正式账号/组织范围引用；INT SyncSourceEvidenceReader→PLT PlatformMigrationEvidenceApi；SYSTEM SystemUserScopeMigrationApi | 前三对象引用P04(DPPMS_USERS)；t_user_role引用P06(DPPMS_USER_ROLES)的T_USER_ROLE当前版真实非preview full SUCCESS；fnd_user_power本任务SQL。 | P04和P06当前版真实完整SUCCESS快照，以及正式组织/账号绑定 |
| P08 | 原字典审计账号须在正式历史用户目录精确存在；SYSTEM SystemUserMigrationApi.getHistoricalIdentitiesByUsername→SystemDictionaryDataMigrationApi | 不强制DPPMS_USERS成功Run；依赖正式用户历史身份API，原账号匹配集合须完整且唯一。 | 正式历史用户名精确、唯一且完整匹配 |
| P13 | companyCode正式组织目录精确解析缺失；COM DppmsExecutionOrganizationReferences→SYSTEM ManagedOrganizationApi.list | 不要求DPPMS公司来源绑定/成功Run；提供方通过ManagedOrganizationApi.list读取正式公司节点并按code关联，缺失抛DPPMS_EXECUTION_COMPANY_UNRESOLVED。报告全50页99895归因为该原因。 | 公司码必须命中正式租户公司目录 |
| P15 | 正式system_company关联；当前GenericSyncEngine lookup，正式COM/AST Owner入口仍待接入 | onMissing=RETAIN保留警告，可返回CREATED；不代表公司关联就绪 | 10000样本中814 ISSUE全为CONTRACT_CUSTOMER_CONFLICT且814唯一(object,sourceKey)；另9186 CREATED有system_company未命中，仅样本不能外推3847355 CREATED警告数 |
| P16 | 规范项目审计账号依赖DPPMS_USERS正式来源绑定；INT SyncBindingMapper→PROJ ProjectMasterMigrationApi | 不检查成功Run本身；与P05类似要求正式来源绑定，缺失在Owner.preview前阻断。原同名/来源身份也先核对，不按姓名猜配。 | 原creator/updater对应DPPMS_USERS正式审计绑定 |
| P23 | 回款计划原creator/updater依赖正式用户审计绑定；INT SyncBindingMapper→COM ContractCollectionPlanMigrationApi | 不检查成功Run本身；查询DPPMS_USERS来源绑定，有targetId且全部获准原身份精确解析。 | 原creator/updater对应DPPMS_USERS正式审计绑定 |


代码证据：

本轮未改变各行正式前置校验；P01预览不写正式组织或用户绑定，已有前置阻断继续引用真实回执，不为无变化重复预览。

P15证据：[P15.json:38](../../../../pms-module-integration/src/main/resources/migration/dppms/tasks/P15.json#L38)；[P15.json:303](../../../../pms-module-integration/src/main/resources/migration/dppms/tasks/P15.json#L303)；[GenericSyncEngine.java:180](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/generic/GenericSyncEngine.java#L180)。保留RETAIN及正式Owner缺口，不改规则。
P01：[DppmsConsolidation.java:40](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsConsolidation.java#L40)；[SystemDepartmentMigrationApiImpl.java:28](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemDepartmentMigrationApiImpl.java#L28)；[DppmsEhrDepartmentCompletionAdapter.java:65](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsEhrDepartmentCompletionAdapter.java#L65)。P01主键/nullID/context及父alias保护已在phase4当前060604包真实PREVIEW_READY180；拟CREATED180不是正式写入，8主管仍待关联
P04：[DppmsUserSyncAdapter.java:104](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java#L104)；[DppmsUserSyncAdapter.java:180](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserSyncAdapter.java#L180)；[CompanyApiImpl.java:23](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/company/CompanyApiImpl.java#L23)；[CompanyServiceImpl.java:37](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/company/CompanyServiceImpl.java#L37)；[CompanyServiceImpl.java:102](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/company/CompanyServiceImpl.java#L102)
P05：[DppmsRoleSyncAdapter.java:107](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsRoleSyncAdapter.java#L107)；[SyncBindingMapper.xml:21](../../../../pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml#L21)
P06：[SyncSourceEvidenceReader.java:33](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/SyncSourceEvidenceReader.java#L33)；[DppmsUserRoleSyncAdapter.java:87](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserRoleSyncAdapter.java#L87)；[SyncRunMapper.java:11](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/SyncRunMapper.java#L11)
P07：[SyncSourceEvidenceReader.java:33](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/SyncSourceEvidenceReader.java#L33)；[DppmsUserScopeSyncAdapter.java:84](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java#L84)；[DppmsUserScopeSyncAdapter.java:144](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsUserScopeSyncAdapter.java#L144)
P08：[DppmsDictionaryDataSyncAdapter.java:58](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsDictionaryDataSyncAdapter.java#L58)；[SystemUserMigrationApiImpl.java:46](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/migration/SystemUserMigrationApiImpl.java#L46)
P13：[DppmsExecutionOrganizationReferences.java:18](../../../../pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/DppmsExecutionOrganizationReferences.java#L18)；[ManagedOrganizationApiImpl.java:27](../../../../yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/organization/ManagedOrganizationApiImpl.java#L27) 技术状态为PREVIEW_READY/read99895＋ISSUE99895（公司未解析），不是技术FAILED。
P16：[DppmsProjectMasterSyncAdapter.java:170](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsProjectMasterSyncAdapter.java#L170)
P23：[DppmsContractCollectionPlanSyncAdapter.java:98](../../../../pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/DppmsContractCollectionPlanSyncAdapter.java#L98)

主管批准闭环：P01组织 → P04账号/正式来源绑定 → 同一P01主管回补，8条保持待关联，不拆新任务；PREVIEW_READY180不表示主管完成。
[P01.md:8](tasks/P01.md#L8)
[open-questions.md:2196](../../../decisions/open-questions.md#L2196)

按37→合并业务归属取真实runtime版本；P17只保留退役历史。P11 Owner与6无入口保持。
