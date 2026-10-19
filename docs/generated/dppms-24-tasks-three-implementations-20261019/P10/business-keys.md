# P10 业务主键、原身份与配置键

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。

[返回本任务](README.md)

来源CRM + AccountId；正式客户按tenant_id + code及有效cus_customer_external_mapping精确定位，不按姓名合并。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| CUSTOMER | DPtech_v_account | AccountId |  | ModifiedOn |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| CUSTOMER | DPtech_v_account | AccountId |  | ModifiedOn |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P10CrmCustomerSyncTask.java:32](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:32) | `var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("CUSTOMER",keys));` |
| [P10CrmCustomerSyncTask.java:71](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:71) | `next.setSourceSystem("CRM");next.setSourceKey(source.getSourceKey());next.setSourceVersion(source.getSourceVersion());` |
| [P10CrmCustomerSyncTask.java:89](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:89) | `.map(row->new SyncIdentityStore.Save("CUSTOMER",row.getSourceKey(),row.getTarget().getId(),fingerprint(row.getTarget(),row.getMapping()),row.getPreviousFingerprint())).toList());` |
| [P10CrmCustomerSyncTask.java:122](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P10CrmCustomerSyncTask.java:122) | `row.setErpCustomerCode(source.getErpCustomerCode());row.setSourceKey(source.getSourceKey());row.setSourceVersion(source.getSourceVersion());row.setDataAsOf(source.getDataAsOf());` |
