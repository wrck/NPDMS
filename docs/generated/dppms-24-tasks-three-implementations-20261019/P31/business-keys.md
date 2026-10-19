# P31 业务主键、原身份与配置键

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p31)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

成员/联系人分别按原成员记录id追溯；正式成员历史和客户联系人生成新主键，不按同名人员合并。

正式表物理PK/唯一索引在本任务README列出，取自2026-10-14结构快照。来源身份用于幂等追溯；不能将原数字ID充当正式主键。业务目标键、技术来源键及整表快照行序分别列出。

## 原通用引擎 / Owner适配器

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| MEMBER_HISTORY | pm_project_member | id |  | — |
| CONTACT_HISTORY | pm_project_member | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |
## 独立直写任务

| 输入object | 原sourceObject/table | sourceKey | sourceKeyColumns | 原更新时间 |
|---|---|---|---|---|
| MEMBER_HISTORY | pm_project_member | id |  | — |
| CONTACT_HISTORY | pm_project_member | id |  | — |

| 目标步骤 | 正式表 | keys（不含框架租户约束） | 写入模式 |
|---|---|---|---|
| Owner/API | 见本任务正式表 | 由Owner转换/查询与上方业务键核验，未配置通用targets | Owner基线保护 |

## C 新虚基类

当前来源技术身份为`tenant_id + task_id + connection_id + MEMBER_HISTORY + 原pm_project_member.id`；所有10/20/30/60角色均用同一种独立身份，正式主键按新行生成，不按姓名或联系方式合并。

原项目ID及规范项目身份遵循本套件批准规则。正式成员现值按租户、项目查询并用完整指纹保护；未绑定的原有同业务事实禁止接管。本套件如已有`CONTACT_HISTORY`身份则阻断自动改绑，不搬移旧联系人；新写入不再使用CONTACT_HISTORY。

正式表既有唯一键保持，成员授权始终false，不因原角色或原来源ID产生当前账号、权限或主责。当前日期缺项见[本轮报告](../base-sync-seven-blockers-followup-20261019.md)。
