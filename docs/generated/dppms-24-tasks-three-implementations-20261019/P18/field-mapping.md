# P18 原表逐字段承载清单

合并组件：P18。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_contract.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P18 |
| pm_project_contract.contractNo | varchar(45) / NO | com_project_contract_relation.contract_id | 公司＋原合同号精确解析正式合同，禁止同号跨公司任选；业务字段/正式关系 | P18 |
| pm_project_contract.projectGroupCode | varchar(45) / NO | com_project_contract_relation.project_id；com_project_contract_relation.contract_id | 原组→成员→同类型编码MAX(projectId)→正式项目，展开合同关系；旧组不是新模型权威；业务字段/正式关系 | P18 |
| pm_project_contract.createTime | datetime / YES | com_project_contract_relation.create_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_contract.createBy | varchar(45) / YES | com_project_contract_relation.creator | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_contract.updateTime | datetime / YES | com_project_contract_relation.update_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_contract.updateBy | varchar(45) / YES | com_project_contract_relation.updater | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_group.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P18 |
| pm_project_group.projectGroupCode | varchar(45) / NO | com_project_contract_relation的精确展开依据 | 仅组身份用于展开，不创建新合同组或项目组合；关联依据 | P18 |
| pm_project_group.projectGroupName | varchar(45) / YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体；已批准排除 | P18 |
| pm_project_group.projectType | varchar(25) / YES | proj_project.project_type（原查询匹配约束） | 按原类型匹配规范项目；不独立复制组类型；关联依据 | P18 |
| pm_project_group.createTime | datetime / YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体；已批准排除 | P18 |
| pm_project_group.createBy | varchar(15) / YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体；已批准排除 | P18 |
| pm_project_group.updateTime | datetime / YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体；已批准排除 | P18 |
| pm_project_group.updateBy | varchar(15) / YES | 不迁入 | 用户批准不独立承接：旧组只用于共享合同查询，组名称/审计不是正式关系同一主体；不创建组实体；已批准排除 | P18 |
| pm_project_group_relationship.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P18 |
| pm_project_group_relationship.projectGroupCode | varchar(45) / NO | com_project_contract_relation.contract_id | 关联pm_project_contract.contractNo后精确正式合同绑定；业务字段/正式关系 | P18 |
| pm_project_group_relationship.projectCode | varchar(45) / YES | com_project_contract_relation.project_id；com_project_contract_relation.status | 还原所有-his；类型＋编码最大projectId；-his表示HISTORICAL不造解除日期，当前来源优先；跨组完整归并规则尚未完成；业务字段/正式关系 | P18 |
| pm_project_group_relationship.mergeBranchMark | varchar(45) / YES | 不迁入 | 用户不迁入旧拆分备注；拆分采用新系统正式主子项目功能，不据旧备注改名/造树；已批准排除 | P18 |
| pm_project_group_relationship.smsProjectCode | varchar(45) / YES | com_crm_execution_order.project_code（正式合同→订单→执行单链） | 原CRM商务编码及尾号依据；该完整正式链尚待核对，不加项目扩展/列；正式链映射尚未闭合 | P18 |
| pm_project_group_relationship.createTime | datetime / YES | com_project_contract_relation.create_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_group_relationship.createBy | varchar(45) / YES | com_project_contract_relation.creator | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_group_relationship.updateTime | datetime / YES | com_project_contract_relation.update_time | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
| pm_project_group_relationship.updateBy | varchar(45) / YES | com_project_contract_relation.updater | 合同关系审计为主、成员同名补缺；归并最早创建/最近非空更新；时间并列人员不同阻断；未选不同值逐字段列原因；sys/异常数据处理保留原码；业务字段/正式关系 | P18 |
