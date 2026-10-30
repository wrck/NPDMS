# 公司来源UNION ALL补齐及004可见范围核查

核验时间：2026-10-23T18:53:40.741294+08:00。工作树fix/dppms-migration-consolidation-20261012，目标仅172.17.90.178:13306/npdms。

按本轮最新指令，只调整新P01公司来源及必要来源身份区分：ehr_company UNION ALL t_company，来源查询中统一01→001、02→002、03→003、04→004及T表状态1→正式启用0。取消T表重复独立查询，从同一份结果派生原别名校验。EHR完整定义继续优先；T表ID不进入EHR部门compID映射，避免不同来源同号ID串联。既有FND004补齐保持，其他任务、授权和业务关系不调整。

实际公司来源SQL：

```sql
SELECT compID AS id,compCode AS code,compName AS name,CAST(isDisabled AS UNSIGNED) AS disabled,'ehr_company' AS source_table,compCode AS original_code
FROM ehr_company
UNION ALL
SELECT id,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE compCode END AS code,compName AS name,CASE CAST(state AS UNSIGNED) WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END AS disabled,'t_company' AS source_table,compCode AS original_code
FROM t_company
LIMIT #{limit};
```

真实来源查询读取EHR3条、T3条，T当前仅01/02/03，无04；同码归并后保留原三家公司，004继续使用此前已批准的FND原ID4定义。没有向原始来源表增删改数据，也不将T表不存在的004写成已读到的记录。实际正式目录仍为001—004四家、总目录209条。

| 阶段 | Run | 状态 | 读取 | 新增 | 更新 | 未变化 |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 真实预览 | `2113583800482410497` | PREVIEW_READY | 209 | 0 | 0 | 209 |
| 实际执行 | `2113583803363897345` | COMPLETED | 209 | 0 | 0 | 209 |
| 重放 | `2113583805150670849` | COMPLETED | 209 | 0 | 0 | 209 |

三个阶段删除均0；28项定向检查通过，包含T004来源承接、EHR同码整条优先、两个来源同号ID隔离与批量来源SQL。当前账号真实浏览器在系统管理→公司管理查询004，显示“迪普科技（香港）有限公司”、状态“开启”、共1条；公司simple-list接口返回四家公司且含004。

## 公司目录与订单可见性边界

- 当前账号为tenant1/admin/ID1，启用且有有效super_admin角色，角色data_scope=1；不是仅凭用户名推断管理员。
- 该账号在system_user_company_department_scope中连同失效、已删除记录均为0；没有可恢复的旧公司授权。其他用户的公司范围不能转给admin。历史裁决只把旧admin关联为历史操作者，不导入旧角色或授权（open-questions.md“旧admin不迁入账号资料”原裁决保持）。
- 项目Owner对当前租户super_admin已有全部未删除项目的查看规则。正式项目分页实际返回79429；admin没有独立成员或显式项目授权记录，不妨碍此既有项目豁免生效。
- 销售订单读取条件为“当前有效公司编码集合命中”或“订单—合同关系连到当前ACTIVE项目—合同关系且项目可见”。公司范围没有super_admin→全部公司豁免。ADR-0038禁止用功能权限、部门、角色替代公司范围，也禁止空范围全量兜底。
- 004四订单的公司引用已完整承接，但com_order_contract_relation为0；两份合同95020260904A01、95020260928A01虽已在正式com_contract，公司均004，却无正式项目合同关系；原始pm_project_contract也没有这两个合同号。因此不能凭相同合同号、执行单或项目全量可见虚构关系。当前订单列表按004查询仍为0，符合现有筛选；未声明订单列表可见性通过。

依据代码：ContractAccessService.currentCompanyCodes/currentProjectIds、SalesOrderMapper.rootReadScope、ProjectTreeScopeService.isTenantSuperAdmin/resolveAllFullProjectIds、OrganizationScopeApiImpl.getActiveScopes及UserCompanyDepartmentScopeMapper.selectActiveListByUserId。有效公司范围读取按主体、租户、删除、启用及日期判断；部门和scopeRole不在这个读取阶段追加过滤。原始依据/运行数据见本轮机器证据。

## 若以后需要新增授权的最小现有范围（本轮不执行）

现有模型下，可见004商务目录所需的最小公司集合是仅004：tenant1、user1、company_id=2113578382599356417、company_code=004，角色只能引用账号已具备的正式角色，部门可为空，生效起止须由授权方明确。不需要增加001—003范围、新角色或项目全量授权。公司范围不是仅4张订单的只读授权；它会与账号现有功能权限共同生效，可能覆盖004的其他商务读取或维护。不能将单条范围登记表述成“四单只读”。已有Owner迁入接口仅用于具备真实来源和历史审计的范围承接，不能借同步任务为当前账号临时扩权。

项目路径本身已有查看授权；缺的是这两个合同真实项目关系的来源记录，新增项目授权不会补出关系。若要仅以真实项目路径查看，需提供正式关系依据，仍按现有精确规则承接；不能为回显创建无依据关系。按用户最新“其他都不需要调整”，上述授权与关联均只登记。

公司、部门全字段哈希、现有209条P01来源绑定、004订单的公司引用/类型/版本/来源/审计字段、账号角色、公司范围与平台授权全字段哈希、其他任务Run、任务配置、原任务三表计数和Flyway历史均保持。只重启59284加载来源修复，自动迁移保持关闭；未修改原任务、执行DDL/Flyway或其他迁移，未操作59191。

完整证据见[来源合并、回显及授权核查机器记录](base-sync-company-source-union-20261023.json)；私有原始查询及运行证据位于`D:/CodexTaskArtifacts/project-detail-followup/company004-visibility-audit-20261023/`。上一轮004正式承接与四订单回填结果沿用，未重复执行P14。
