# P18 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p18)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：项目组、成员桥、规范项目与合同精确匹配；活动来源优先，最早创建、最新更新及同刻人员冲突规则。

后置：写正式关系及各原来源追溯；真实项目、合同、公司授权不可省，不借预览父ID。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

当前原线完整组输入新工厂；ONLY_FULL_GROUP_BY修复只有常量派生表检查，未真实全量验收新候选。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| correctHistorical | update | 否 | [ProjectContractRelationMigrationMapper.xml:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:27) |
| restoreWithdrawn | update | 否 | [ProjectContractRelationMigrationMapper.xml:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:36) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProjectRelationMapper；82676条预览均ISSUE(PROJECT_BINDING_MISSING)。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| correctHistorical | update | 否 | [DirectProjectRelationMapper.xml:27](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:27) |
| restoreWithdrawn | update | 否 | [DirectProjectRelationMapper.xml:36](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:36) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

四份单表/既有一层范围查询精确归并；按原审计选最早创建和最新更新。当前非空公司授权始终有效；只读预览可登记缺公司/合同，不生成目标ID或虚假动作数。实际写入及原绑定异常继续拒绝。

[本轮真实验证及外部前置](../base-sync-five-input-dependencies-20261019.md#p18)。不是所有分支都已在真实运行到达；缺引用分支另有原生检查。

| 前后置入口 | 逻辑 | 原件 |
|---|---|---|
| createParameters | 运行内固定少量目录/参数。 | [createParameters](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:18) |
| work | 按上述预览与实际写入边界编排。 | [work](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:22) |
| syncDataQuery | 执行实际来源查询，保持既定容量及范围。 | [syncDataQuery](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:24) |
| syncDataInsertBefore | 映射/合并并核验当前目标、授权、身份与不可变历史；预览缺引用登记见本轮报告。 | [syncDataInsertBefore](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:59) |
| syncDataCount | 待核验的缺引用条目计入read，不虚报插入/未变。 | [syncDataCount](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:102) |
| syncDataInsert | 仅实际执行进入批量DML/原Owner写入；预览不写业务。 | [syncDataInsert](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:103) |
| syncDataInsertAfter | 仅实际执行保存独立来源绑定，保留指纹/原身份保护。 | [syncDataInsertAfter](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P18ProjectContractSyncTask.java:104) |

实际SQL及完整动态节点见[C-base-sync.sql](C-base-sync.sql)、[C-base-sync-mappings.json](C-base-sync-mappings.json)。本次没有新增多表关联UPDATE；不能把Mapper声明当作已执行。
