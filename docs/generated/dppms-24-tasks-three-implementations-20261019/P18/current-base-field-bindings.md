# P18 新虚基类实际读写字段衔接

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p18)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectContracts | id | contractSourceId |
| selectContracts | contractNo | contractNo |
| selectContracts | projectGroupCode | projectGroupCode |
| selectContracts | createTime | contractCreateTime |
| selectContracts | createBy | contractCreateBy |
| selectContracts | updateTime | contractUpdateTime |
| selectContracts | updateBy | contractUpdateBy |
| selectGroups | id | groupId |
| selectGroups | projectGroupCode | groupProjectGroupCode |
| selectGroups | projectGroupName | groupProjectGroupName |
| selectGroups | projectType | projectType |
| selectGroups | createTime | groupCreateTime |
| selectGroups | createBy | groupCreateBy |
| selectGroups | updateTime | groupUpdateTime |
| selectGroups | updateBy | groupUpdateBy |
| selectMembers | id | memberId |
| selectMembers | projectGroupCode | memberProjectGroupCode |
| selectMembers | projectCode | memberProjectCode |
| selectMembers | mergeBranchMark | mergeBranchMark |
| selectMembers | smsProjectCode | smsProjectCode |
| selectMembers | createTime | memberCreateTime |
| selectMembers | createBy | memberCreateBy |
| selectMembers | updateTime | memberUpdateTime |
| selectMembers | updateBy | memberUpdateBy |
| selectProjects | p.projectId | originalProjectId |
| selectProjects | p.projectCode | originalProjectCode |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| com_project_contract_relation.id | #{row.target.id} | insertProjectContract |
| com_project_contract_relation.tenant_id | #{row.target.tenantId} | insertProjectContract |
| com_project_contract_relation.project_id | #{row.target.projectId} | insertProjectContract |
| com_project_contract_relation.contract_id | #{row.target.contractId} | insertProjectContract |
| com_project_contract_relation.relation_role | #{row.target.relationRole} | insertProjectContract |
| com_project_contract_relation.source_system | #{row.target.sourceSystem} | insertProjectContract |
| com_project_contract_relation.source_table | #{row.target.sourceTable} | insertProjectContract |
| com_project_contract_relation.source_record_key | #{row.target.sourceRecordKey} | insertProjectContract |
| com_project_contract_relation.effective_from | #{row.target.effectiveFrom} | insertProjectContract |
| com_project_contract_relation.effective_to | #{row.target.effectiveTo} | insertProjectContract |
| com_project_contract_relation.status | #{row.target.status} | insertProjectContract |
| com_project_contract_relation.version | #{row.target.version} | insertProjectContract |
| com_project_contract_relation.creator | #{row.target.creator} | insertProjectContract |
| com_project_contract_relation.create_time | #{row.target.createTime} | insertProjectContract |
| com_project_contract_relation.updater | #{row.target.updater} | insertProjectContract |
| com_project_contract_relation.update_time | #{row.target.updateTime} | insertProjectContract |
| com_project_contract_relation.deleted | #{row.target.deleted} | insertProjectContract |
