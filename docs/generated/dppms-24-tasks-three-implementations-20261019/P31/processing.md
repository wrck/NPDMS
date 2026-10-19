# P31 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p31)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

以下A/B段保留原实现流程；当前C按本页末尾及[本轮报告](../base-sync-seven-blockers-followup-20261019.md)解释，角色60不依赖最终客户相关方。

后置：成员授权标志保持false；联系人不接管当前主联系人；保留原人员编码，可空user_id/employee_no不猜填。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原历史Owner与role60联系人同次采集；当前授权仍是另一批准路径。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| closeApprovedInterval | update | 否 | [ProjectMemberHistoryMigrationMapper.xml:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/legacydetail/ProjectMemberHistoryMigrationMapper.xml:23) |
| closeEffectiveServiceManagerAssignments | update | 否 | [ProjectMemberAssignmentMapper.xml:146](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:146) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectMemberHistory/ContactHistory Mapper；缺V482及旧完整范围发布，真实业务未到达。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

1. 预览从原库读取与新P16相同的完整有效项目范围，正式执行仍要求本套件P16完整COMPLETED及当前项目授权。不得把原发布表或旧任务绑定当作授权。
2. 依原项目键分段流式读取15个原字段。每批只查询同租户选中项目的现存成员、本任务自身MEMBER_HISTORY及受保护CONTACT_HISTORY身份；没有CUS或最终客户查询。
3. 10/20/30映射既有角色，60保留原值。姓名、原编码、电话、邮箱、登记来源、有效区间和审计按原规则承接。授权标志false、无精确账号时user_id空；不猜当前组织历史、不改主责或系统角色。
4. 倒置日期及审计精度校验保持；152条原日期问题未解决前，全量真实预览仍失败，不能因联系人依赖已修复便标记通过。
5. 同一独立绑定必须匹配完整目标指纹及原事实；人工变化、原有业务行接管或旧CONTACT_HISTORY自动搬移均拒绝。仅新行按一批一条INSERT进入34列成员表。
6. 成功后仅保存本套件MEMBER_HISTORY来源身份；预览不执行INSERT或绑定。业务UPDATE、多表关联回填、当前授权及客户联系人后处理均无本任务SQL。失败同事务回滚。

当前实际SQL及动态节点见[C-base-sync.sql](C-base-sync.sql)和[C-base-sync-mappings.json](C-base-sync-mappings.json)。本轮26项定向检查及真实终态见[本轮报告](../base-sync-seven-blockers-followup-20261019.md)。
