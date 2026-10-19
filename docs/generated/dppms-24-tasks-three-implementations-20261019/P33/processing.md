# P33 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p33)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：完整项目范围、正式SN设备、项目选择/管理授权；设备不能用原缓存ID猜配。

后置：写历史地址原文及正式不可变地址快照；仅获准首次当前记录建立，不接管现有当前安装；地址API失败整次回滚。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原安装Owner和首次迁入/跨批归集；需要正式设备及范围。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| closeCurrentProject | update | 是 | [DeviceAssignmentMapper.xml:52](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:52) |
| updateDeviceProjectIfMatch | update | 否 | [DeviceAssignmentMapper.xml:100](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:100) |
| closeCurrentCustomer | update | 是 | [DeviceAssignmentMapper.xml:141](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:141) |
| updateDeviceCustomerIfMatch | update | 否 | [DeviceAssignmentMapper.xml:189](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml:189) |
| closeCurrent | update | 否 | [DeviceLocationMapper.xml:7](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-asset/src/main/resources/mapper/location/DeviceLocationMapper.xml:7) |
| carryHistoricalRecord | update | 否 | [InstallationMapper.xml:11](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-engineering/src/main/resources/mapper/installation/InstallationMapper.xml:11) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectInstallationMapper，范围发布前置处失败。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

完整原项目范围和原始安装历史先校验；缺设备在只读预览登记，不造ID。正式项目缺失时仅延后当前选择，既存项目仍检查选择。空/超长地址、日期、串货暂缓、原安装指纹及位置Owner写入规则保持。

[本轮真实验证及外部前置](../base-sync-five-input-dependencies-20261019.md#p33)。不是所有分支都已在真实运行到达；缺引用分支另有原生检查。

| 前后置入口 | 逻辑 | 原件 |
|---|---|---|
| createParameters | 运行内固定少量目录/参数。 | [createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:22) |
| work | 按上述预览与实际写入边界编排。 | [work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:25) |
| syncDataQuery | 执行实际来源查询，保持既定容量及范围。 | [syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:27) |
| syncDataInsertBefore | 映射/合并并核验当前目标、授权、身份与不可变历史；预览缺引用登记见本轮报告。 | [syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:28) |
| syncDataCount | 待核验的缺引用条目计入read，不虚报插入/未变。 | [syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:56) |
| syncDataInsert | 仅实际执行进入批量DML/原Owner写入；预览不写业务。 | [syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:57) |
| syncDataInsertAfter | 仅实际执行保存独立来源绑定，保留指纹/原身份保护。 | [syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P33InstallationSyncTask.java:63) |

实际SQL及完整动态节点见[C-base-sync.sql](C-base-sync.sql)、[C-base-sync-mappings.json](C-base-sync-mappings.json)。本次没有新增多表关联UPDATE；不能把Mapper声明当作已执行。
