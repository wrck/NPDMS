# P30 业务主键、原身份与配置键

[返回本任务](README.md)

正式设备tenant_id + device_sn；来源是本地正式DPPMS发货事实，不从原缓存ID直接生成设备身份。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| DEVICE_BASE | ast_device_shipment | deviceSn |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| DEVICE_BASE | ast_device_shipment | deviceSn |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

对象键和来源键的实际保存表达式如下；正式行业务定位/幂等冲突继续受目标查询和指纹保护，不能只凭一个原ID覆盖现存行。

| 实现位置 | 身份/键原表达式 |
|---|---|
| [P30DeviceBaseSyncTask.java:38](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:38) | `var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("DEVICE_BASE",serials));` |
| [P30DeviceBaseSyncTask.java:62](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:62) | `row.setSourceSystem("DPPMS");row.setSourceKey("SN:"+row.getSn());row.setSourceVersion("BASE_P30:"+context.startedAt());row.setSyncedAt(context.startedAt());row.setSyncStatus("SUCCESS");row.setRemark("DPPMS设备基础承接");` |
| [P30DeviceBaseSyncTask.java:72](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P30DeviceBaseSyncTask.java:72) | `.map(row->new SyncIdentityStore.Save("DEVICE_BASE",row.getDeviceSn(),row.getTarget().getId(),fingerprint(row.getTarget()),null)).toList());` |
