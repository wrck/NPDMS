# 24项正式执行前置核清与P33/P22受影响真实预览

核验时间：2026-10-20T14:56:47.396413+08:00。工作树：`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`，分支：`fix/dppms-migration-consolidation-20261012`。仅独立新虚基类套件。

**P33尾空格SN匹配和P22明细绑定键两处实现缺陷已修复；52项原生/定向检查通过，受影响P33/P22真实预览均PREVIEW_READY。24项最新回执仍为24 READY、0 FAILED；本轮仅重跑两项，其余22项沿用此前全量复验。13项有已识别正式执行阻断，P30为空输入，其余10项未发现专属阻断。所有项目仍须满足当前租户、功能/数据授权、所有权及历史保护，不能将预览就绪写成正式同步可执行。**

本轮未执行永久业务写入、DDL、Flyway/迁移、真实同步、正式回填、授权或来源修正；原任务完全隔离，测试库与59191未操作。仅依既有授权重载59284两次。两条串货继续暂缓。

## 两处实现缺陷及修复

### P33：按原请求SN保存SQL返回的匹配关系

旧查询返回正式`d.sn`，Java以该值建Map却按原`s.barcode`取值。在当前正式SN列`utf8mb4_unicode_ci`下，尾空格等字符串可由SQL匹配，但原样Java键不同。26个实际尾空格SN涉及27条来源，属于实现缺陷，不能要求先清洗来源。

现在SQL同时返回原请求`requested_sn`和正式`id/sn`；Java按原请求键定位设备，再按设备ID及正式SN验证当前项目设备选择。同一设备对应多个原请求字符串不再误报重复候选；同一请求出现多个正式候选仍拒绝。原SN、来源指纹、当前选择授权和不可变历史保护均保持。没有TRIM、改码、替换内部码、跨租户查找或补造ID。

[P33实现](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java)；[P33目标Mapper](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P33TargetMapper.xml)。批量匹配仅一层派生表，每批最多500个原SN，值全部绑定参数：

```sql
SELECT d.id, d.sn, requests.requested_sn
FROM ast_device d
JOIN (VALUES ROW(?), ROW(?)) AS requests(requested_sn)
  ON d.sn = CONVERT(requests.requested_sn USING utf8mb4)
             COLLATE utf8mb4_unicode_ci
WHERE d.tenant_id = ? AND d.deleted = FALSE;
-- 实际写入分支保留 FOR UPDATE；空/NULL集合使用 WHERE 1=0 的空派生表。
```

使用现有目标MySQL支持的[VALUES行构造语法](https://dev.mysql.com/doc/refman/8.4/en/values.html)，没有新增JSON承载或持久化字段。真实MySQL的500参数批次、引号/反斜杠参数、空集合、FOR UPDATE及本仓库SQL解析器均已验证。

### P22：独立P14明细绑定必须按line_type计算

P14已审头键使用`order_type`、行键使用`line_type`。P22后置关联仍用头类型计算SALES_LINE来源键，会使合法的“行类型与父头类型不同”记录无法关联。只把绑定哈希字段改成`l.line_type`，实际父头关系仍核对`l.order_type`。没有把类型差异设成新约束，也没有绕开真正的父ID、当前租户、同连接、独立任务、COMPLETED非预览运行及本次绑定范围。

[P22 Mapper](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml)中修正后的键：

```sql
SHA2(CAST(JSON_ARRAY(l.source_system, l.company_code, l.line_type,
                     l.order_no, l.line_no) AS CHAR), 256)
```

两个原生失败测试先复现旧键问题，再验证正确line_type键可回填、错误父类型键不能冒充。正式库未执行该UPDATE；测试仅使用连接本地TEMPORARY影子表。

## 受影响预览与性能

| 项目 | 新Run | 终态 / 读取 | API耗时 | 说明 |
|---|---|---|---|---|
| P33 | 2112436849851088898 | PREVIEW_READY / 1,035,329 | 190.174秒 | 插入/更新/未变/删除计划均0；正式设备仍缺项，串货2条暂缓 |
| P22 | 2112437662510071809 | PREVIEW_READY / 343,858 | 22.326秒 | 候选插入340,882仅为预览；未执行回填 |

P33此前全量预览143.172秒。首版修复使用UNION ALL批量请求，已完成一次真实预览（Run 2112432818269286402，288.990秒），计数及告警相同；观察到耗时回退后改为VALUES批量请求，最终耗时如上。两次真实回执均保留，性能为本机本次实测，不构成跨环境保证。没有修改数据库版本、解析器或安全拦截器。

P33最终告警：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P33_TRANSFER_DEFERRED:2;P33_FORMAL_DEVICE_PENDING:1035329;P33_BLANK_ADDRESS_FILTERED_AFTER_READ:2386`。正式DPPMS发货0，当前租户有效设备36条，1,018,498个原SN没有正式匹配，因此全量预览不能直接证明27条尾空格来源已在正式数据中关联成功；关联成功和保护分支由真实MySQL临时夹具证明。

P22最终告警：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P22_DEPARTMENT_ID_POSTFILL_PENDING:-1;P22_ORDER_LINE_ID_POSTFILL_PENDING:-1;P22_EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference;P22_ZERO_QUANTITY_PENDING:2976;P22_DEPARTMENT_SOURCE_MISSING_PENDING:14293;P22_EXECUTION_SCHEMA_REQUIRED:department_pending_reference`。空部门原值、两个-1及零数量处置沿用已有审核，不生成占位资料；正式结构前置继续登记。

## 24项逐项正式执行前置

以下“未发现专属阻断”只反映本轮实现、当前结构与最新预览；未调用任何实际执行入口。表内Run均为最新preview=true回执，P22/P33为本轮新Run，其余22项为此前已完成全量回执。

| 任务与代码 | 最新Run / 真实读取 | 正式执行阻断及分类 | 已决非阻断项与可执行处理方向 |
|---|---|---|---|
| [P01 公司、部门及主管](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java) | 2112425268933046274 / 208 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 8项主管待关联只登记，不阻断目录承接；保留可空主管引用；当前正式目录、父子关系和本套件指纹保护继续生效。 |
| [P04 账号、任职、角色、成员和范围](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P04AccountSyncTask.java) | 2112425270786928641 / 12,632 | 结构：int_base_sync_binding仍有目标唯一索引，阻断多来源共用同一正式目标。 | 任职历史按既有custom_info.employmentAssignments裁决承接，不再列规格缺失；isSysUser保留为系统预设用户，不代表管理员；26个排除账号、3个孤立档案是既有处置；仅列既有V484索引差异，未执行DDL；源唯一约束及目标/账号保护保持。 |
| [P08 系统字典类型及字典项](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java) | 2112425274729574401 / 612 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 两张来源各读取一次，缺失类型在本次运行内编排；当前未发现专属正式执行前置；保留类型和值域、来源及目标保护。 |
| [P10 CRM客户主档](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java) | 2112425275853647873 / 59,544 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | name已为VARCHAR(255)，erp_customer_code为VARCHAR(50) NULL；已决原名完整承接；当前预览59,544条无容量/来源告警，不再重复提出名称或ERP列裁决。 |
| [P11 CRM产品主档](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P11CrmProductSyncTask.java) | 2112425360834441218 / 8,443 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 预览只报真实读数，upsert不伪造目标插入/更新数；当前未发现专属正式执行前置；保留来源、租户、删除与已审空值不覆盖规则。 |
| [P12 DPPMS缺失产品补齐](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P12SupplementProductSyncTask.java) | 2112425362642186241 / 34,976 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 仅补缺失产品；不以回查非必要业务ID阻断原始承接；当前未发现专属正式执行前置，不扩大为覆盖既有产品。 |
| [P13 执行单头](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P13ExecutionOrderSyncTask.java) | 2112425366081515522 / 99,895 | 结构：同P04的多来源目标唯一索引。 | 2,625条在已审项目范围外属于范围排除；原始读取99,895条，写入预测留待执行，不是数据丢失；结构差异单列；不重开已决来源范围或生成虚假表头数量。 |
| [P14 销售订单及明细](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java) | 2112425412210470914 / 480,044 | 数据/关联：1,580条缺少可用真实父头ID；本次逐条回查337条仅有同号其他类型头、1,243条无同来源/公司/订单号头。 | 头、行各自业务键及原类型保留；执行单取MAX(orderExecNumber)已闭合；这些引用不阻断预览；不同类型不能任意选父头，实际写入仍需真实父ID；不再要求类型差异重新批准预览；列原ID和两表原值依据，未回查借货来源或修改归父逻辑。 |
| [P15 合同及全量发货历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P15DeliverySyncTask.java) | 2112425707665633281 / 4,708,483 | 正式输入：实际分支要求本套件P24非预览COMPLETED，当前为0；合同归并消费本套件P14正式订单与P24正式回款；当前没有正式完成运行或独立绑定。 | 来源预览4,708,483条不等于正式输入就绪；选客按已审订单类型及最大订单号；列明正式输入链；不伪造完成回执，不调用真实同步填前置。 |
| [P16 项目主档及独立来源身份](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P16ProjectSyncTask.java) | 2112426738885603329 / 158,871 | 结构：同P04的多来源目标唯一索引，79,442个来源别名需独立保留。 | 原project_id原样承接、既有项目身份及当前管理权限保护保持；不因原ID规则再次编号；只列现有索引与已批准多来源承接的差异，不接管旧绑定。 |
| [P18 项目合同关系](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java) | 2112427289358643202 / 82,676 | 结构：同P04的多来源目标唯一索引；授权：回执001=80,374、002=1,804、003=195待公司授权；另8条原公司码缺失，合同引用待补82,381条；数据：真实父项目/合同与当前业务授权仍需存在。 | 上述数量重叠，不能相加为缺失总数；整表来源审核不是正式COM授权；沿ADR-0038及既有整表预览裁决；列缺公司码/正式合同范围，不自动授权或猜公司。 |
| [P19 项目相关方及历史状态属性](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P19ProjectFactsSyncTask.java) | 2112427334816509953 / 186,118 | 正式数据：缺失的真实父项目必须到位，既存项目须通过当前MANAGE范围。 | 4个历史状态拓展属性已批准暂不承接，不是待创建属性定义；保留相关方原始资料；等待合法正式父项目，不要求旧发布或原任务绑定。 |
| [P20 项目真实产品历史清单](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P20RealProductSyncTask.java) | 2112427367251062786 / 7,818 | 正式数据：同P19的真实父项目及当前管理范围。 | 产品原始业务键承接，非必要业务ID不前置；不额外要求P30设备或旧10项目试点数据。 |
| [P21 租赁与配置展示缓存](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P21ProjectCacheSyncTask.java) | 2112427404450344962 / 1,190 | 结构：proj_project_lease_product.project_id和proj_project_product_configuration.project_id仍为NOT NULL。 | 两列按既有规则应可空；不补造项目ID，不要求先创建项目主档；列既有V481的两列兼容差异，本轮未执行DDL；租户范围内缓存刷新逻辑保持。 |
| [P22 项目产品实施范围](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java) | 2112437662510071809 / 343,858 | 结构：order_line_id即时外键及3个订单快照列非空，不能直接承接-1/NULL；部门code/name/version三列非空，不能承接原空部门资料；正式数据：真实父项目及当前管理范围。 | 部门ID和订单行ID先-1、后置统一回填；订单行缺失不是原表直写前置；14,293条空部门资料保留原值；2,976条零数量按已决规则不落业务表，均不新建裁决；修复P22绑定键仍使用order_type的实现缺陷，改用已审P14的line_type；后置仍只允许同租户/连接/新任务/本次运行及COMPLETED非预览绑定；没有执行正式UPDATE。 |
| [P23 合同收款计划展示缓存](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P23CollectionPlanSyncTask.java) | 2112427469931819010 / 35,501 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 非必要业务ID不回填；原时间可空按正式列及原值核验；当前未发现专属正式执行前置；保留租户范围缓存刷新边界。 |
| [P24 合同回款信息](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java) | 2112427474067402754 / 81,547 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 保留原dataSource及原用户名；不因CRM来源错误要求DPPMS标记或回查账号ID；当前未发现专属正式执行前置；必须保护原回款记录及本套件来源身份。 |
| [P25 设备续保历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P25WarrantySyncTask.java) | 2112427498130124802 / 462,213 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 原表批量承接，设备ID可空，不要求先有P30设备；当前未发现专属正式执行前置；日期、精度、复合来源身份及历史保护继续生效。 |
| [P30 正式发货设备基础承接](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java) | 2112427552333115394 / 0 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 当前有效DPPMS正式发货0，导致0行空作业；代码登记告警而非抛出阻断；正式产品资料用于可选补充，缺失并非无条件前置；多义仍拒绝；要验证设备覆盖必须先有合法正式DPPMS发货事实；本轮不填数或调用P15真实同步。 |
| [P31 项目成员及最终客户联系人](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P31MemberContactSyncTask.java) | 2112427553113255938 / 381,195 | 正式输入：仅本套件完整BASE_P16非预览COMPLETED及绑定范围，当前为0；来源有效性：152条日期倒置只获准原值预览；真实写入仍拒绝。 | 历史user_id可空，不产生当前授权；不要求先建客户主体来承接历史最终客户联系人；保留152条原日期及既有处理边界，不修改日期/CHECK，不重复问已决预览规则。 |
| [P33 设备安装历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java) | 2112436849851088898 / 1,035,329 | 正式输入：同P31的完整独立P16范围；真实设备及当前项目设备选择；设备缺项：1,035,329条安装行涉及1,018,498个原SN，当前正式设备未就绪；已决暂缓：sourceId1161000、1161002两条串货保持暂缓，未获正式处理前不真实执行该范围。 | 26个尾空格SN涉及27行属于本次已修复匹配问题，不要求先清洗源SN；地址非空原文保存到install_location和fallbackLocation快照，UNRESOLVED；不以地址管理记录创建为前置；修复SQL与Java匹配键，按设备ID/正式SN核对当前选择；保留原SN及指纹；内部码1条对应7个主SN、另外5个缺当前已审核上游主SN依据，分别保留真实身份缺项。 |
| [P34 出厂版本历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P34FactoryHistorySyncTask.java) | 2112428241000083457 / 165,801 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | 可空设备ID及原SN按既有规则承接，不要求先有P30设备引用；当前未发现专属正式执行前置；保留不可变历史和来源绑定保护。 |
| [P35 软件批次及版本历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P35SoftwareHistorySyncTask.java) | 2112428258494525442 / 1,586,578 | 正式输入：同P31的完整独立P16范围，当前为0。 | 8个备份/镜像等拓展属性已批准暂不承接；批次和版本在本次工作内关联，不要求外部预造历史批次；保留源批次/明细同项目一致性及历史保护，不把暂缓扩展列为正式数据缺项。 |
| [P37 整表许可历史](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java) | 2112428520672079874 / 475,468 | 当前未发现专属阻断；通用权限、来源及历史保护继续生效。 | device_id、contract_id正式列均允许NULL；各475,468条关联待补只是可选后续事项，不是执行阻断；sourceId472122的空许可码已批准按原空字符串加密保留，不是异常过滤条件；密钥与475,468条批准事实清单为真实配置前置，当前冻结环境已加载并完成全量预览；不回查旧试点Owner或项目，不创建占位码，不补造设备/合同ID。 |

## 正式结构、数据与授权依据

### 结构/配置兼容：不是来源数据缺项

实查库`npdms_reverify_20261014`，Flyway最后成功版本473。本轮只读取得定义；已批准的两个容量列此前已完成，本轮没有DDL。

| 范围 | 当前正式定义 | 与已审承接方式的差异 | 既有依据 / 后续处理 |
|---|---|---|---|
| P04/P13/P16/P18 | int_base_sync_binding目标(tenant_id,task_id,object_key,target_id)仍为唯一索引 | 合法多个来源身份归并同一目标会失败；P16有79,442个来源别名 | [既有V484第4行目标索引定义](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql)；仅单列兼容差异，不运行整份迁移 |
| P21 | 两张展示缓存的project_id均NOT NULL | 已审缓存允许没有正式业务ID | [既有V481第34、35行](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/sql/migrations/V481__sync_decided_configuration_contracts.sql)；两列可空兼容另行处理，不补造ID |
| P22 | order_line_id即时外键；订单source_system/company_code/order_type和部门code/name/version均NOT NULL | 已审订单ID=-1、缺关联快照NULL、缺原部门码保留原值，当前物理约束不能承接 | 仅登记最小结构兼容前置，不能以环境修复代码自动ALTER、关闭外键或填占位；关联缺失仍按已审后置处理 |
| P10 / P33 | name VARCHAR(255) NOT NULL / install_location VARCHAR(1024) NULL | 已兼容已决完整原文承接 | 本轮未再改结构，无容量告警 |
| P37 | device_id / contract_id均BIGINT NULL | 可空后补关联已获正式列支持 | 两个475,468条待关联告警不列为阻断 |

[完整元数据、外键、目标输入与正式完成运行核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/formal-preconditions.json)。当前独立非预览COMPLETED运行及来源绑定均0，不得复用原任务回执或绑定补齐。

### 正式数据、有效性与合法上下文：不能由实现猜补

- **P14**：只读取已拆分`pm_order_data_from_erp`与`pm_order_line_from_erp`，对最新日志1,580个原ID逐条回查，差异0；337条仅有同来源/公司/订单号的其他类型头，1,243条连同号头也没有。头、行原类型及各自业务键维持审核结果，差异不阻断预览；正式行仍需合法真实父ID。需要权威父关联事实或缺失的拆分头输入，不自动跨类型归父，不回查总代借货来源。[全部1,580条原值和分类](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/P14-pending-reference-recheck.json)。
- **P15/P31/P33/P35**：当前缺本套件正式输入而非读源失败。P15代码要求P24正式完成并消费本套件P14/P24；P31/P33/P35要求本套件P16完整非预览完成及来源范围。只能在另获实际同步授权的阶段生成合法正式输入，本轮不运行它们、不伪造COMPLETED或接管旧绑定。
- **P18**：001/002/003公司分别80,374/1,804/195组关系待合法公司上下文，另8组缺原公司码；正式合同引用待补82,381组，这些集合重叠不能相加。ADR-0038的当前公司授权和正式MANAGE边界继续生效，整表来源批准不自动生成权限。[已有正式依据、代码与原缺项](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/P18-company-authorization-audit-preview-20261020.md)。
- **P19/P20/P22**：执行路径需要真实父项目和当前管理范围。当前租户有效项目仅41，不能把原project_id原样承接等同所有父项目已正式建立。原ID规则继续保留，不新增账号ID、旧试点Owner或镜像前置。
- **P31**：152条原日期倒置仅获准保留原值做预览；正式写入前需权威有效期事实或后续明确处置。日期/CHECK不改，不重复询问已决预览方式。
- **P33**：1,035,329条安装涉及1,018,498原SN，当前合法正式设备缺失。1,018,466个SN有原主发货原文；26个尾空格SN的27条来源由本次实现修复消除比较误报，不再列为清洗前置；内部码01100107D184000007对应7个不同主SN，仍缺权威身份关联；另外5个SN只是在本次barcode/barcode2/com_barcode未见，需权威主SN来源证据，不能断言外部全部不存在。两条串货1161000/1161002继续暂缓，无新归属裁决。[全部32个例外SN、33个原ID与匹配SQL](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/docs/generated/dppms-24-tasks-three-implementations-20261019/P33-formal-device-reference-audit-20261020.md)。
- **P30**：当前正式DPPMS发货0导致0行空预览，既存3条FAST001_TEST_MES发货（2有效、1删除）不是本套件DPPMS输入。0行可以是成功空作业，不能称为设备覆盖验证完成。

### 已裁决的事项不重新转为前置

P04任职历史和isSysUser含义，P10原名完整承接及ERP列，原用户名与原project_id，P22空部门/-1/零数量，P33空地址过滤、原文install_location与fallbackLocation快照、UNRESOLVED及不创建地址管理记录，P19/P35暂缓拓展，P25/P34可空设备ID，P37整表及空许可码加密，均沿用既有结论。没有重复提出业务确认。

## 验证、运行包与隔离证明

| 检查 | 实际结果与边界 |
|---|---|
| P33SnMatchingMySqlTest | 7 tests / 0 failures / 0 errors / 0 skipped |
| NewHistoryTaskMySqlTest | 17 tests / 0 failures / 0 errors / 0 skipped |
| P22DepartmentPostfillMySqlTest | 19 tests / 0 failures / 0 errors / 0 skipped |
| P22SnapshotCollationMySqlTest | 3 tests / 0 failures / 0 errors / 0 skipped |
| P14BusinessKeysMySqlTest | 6 tests / 0 failures / 0 errors / 0 skipped |
| 全字段数据 | 169张原任务/业务/绑定表相同；原46任务、209运行、211绑定未改 |
| 结构与保护 | 473张表结构无差异，180项保护无差异；仅正常认证/审计表AUTO_INCREMENT计数变化单列 |
| 旧回执及配置 | 原143条BASE回执逐字段摘要不变，任务配置不变，无在途任务；新增3条均为preview=true PREVIEW_READY（P33两次、P22一次） |
| 冻结包 | 与上轮P18冻结包相比只有8个独立P33类族/P33与P22 XML条目不同；其他包条目完全相同，最终包与本工作树已测产物一致 |
| 专用运行 | 59284 PID 25744；local,dac；Flyway/Flowable结构更新、Quartz/Batch和事件后台作业关闭，迁移容器exited |

原生测试的写入、索引变更与回放只作用于单连接TEMPORARY影子表，连接关闭即消失；未作用正式业务表。本轮无UI变更，验证为真实登录后的服务端preview流程，没有把HTTP 200或编译当成业务写入完成。

[全部52项检查和日志](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/targeted-tests.json)；[修复前P33失败复现](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/baseline-failing-test.json)；[修复前P22失败复现](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/P22-line-key-baseline-failing-tests.txt)；[全字段保护证明](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/verification-proof.json)；[最终冻结包差异白名单](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/cumulative-candidate-manifest.json)；[P33最终真实回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/P33-preview.json)；[P22最终真实回执](D:/CodexTaskArtifacts/project-detail-followup/base-sync-execution-prerequisites-p33-sn-20261020/P22-preview.json)。

完整24项Run、分类、代码行证据与保护原件索引见[base-sync-execution-prerequisites-p33-sn-20261020.json](base-sync-execution-prerequisites-p33-sn-20261020.json)。历史失败及中间回执保留，不覆盖原始证据。
