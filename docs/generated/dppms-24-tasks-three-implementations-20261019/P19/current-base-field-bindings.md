# P19 新虚基类实际读写字段衔接

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p19)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectProjectParty | STRAIGHT_JOIN r.id | sourceId |
| selectProjectParty | r.projectId | originalProjectId |
| selectProjectParty | canonical.projectId | canonicalProjectId |
| selectProjectParty | p.projectCode | originalProjectCode |
| selectProjectParty | p.projectType | projectType |
| selectProjectParty | r.partyRole | partyRole |
| selectProjectParty | r.partyCode | partyCode |
| selectProjectParty | r.partyName | partyName |
| selectProjectParty | r.createTime | createTime |
| selectProjectParty | r.createBy | source_creator |
| selectProjectParty | r.updateTime | updateTime |
| selectProjectParty | r.updateBy | source_updater |
| selectProjectParty | r.effective | effective |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| proj_project_party.id | #{row.target.id} | insertProjectParty |
| proj_project_party.tenant_id | #{row.target.tenantId} | insertProjectParty |
| proj_project_party.project_id | #{row.target.projectId} | insertProjectParty |
| proj_project_party.party_role | #{row.target.partyRole} | insertProjectParty |
| proj_project_party.party_code | #{row.target.partyCode} | insertProjectParty |
| proj_project_party.party_name | #{row.target.partyName} | insertProjectParty |
| proj_project_party.contact_name | #{row.target.contactName} | insertProjectParty |
| proj_project_party.phone | #{row.target.phone} | insertProjectParty |
| proj_project_party.source_system | #{row.target.sourceSystem} | insertProjectParty |
| proj_project_party.source_table | #{row.target.sourceTable} | insertProjectParty |
| proj_project_party.source_record_key | #{row.target.sourceRecordKey} | insertProjectParty |
| proj_project_party.effective_from | #{row.target.effectiveFrom} | insertProjectParty |
| proj_project_party.effective_to | #{row.target.effectiveTo} | insertProjectParty |
| proj_project_party.status | #{row.target.status} | insertProjectParty |
| proj_project_party.version | #{row.target.version} | insertProjectParty |
| proj_project_party.creator | #{row.target.creator} | insertProjectParty |
| proj_project_party.create_time | #{row.target.createTime} | insertProjectParty |
| proj_project_party.updater | #{row.target.updater} | insertProjectParty |
| proj_project_party.update_time | #{row.target.updateTime} | insertProjectParty |
| proj_project_party.deleted | #{row.target.deleted} | insertProjectParty |
