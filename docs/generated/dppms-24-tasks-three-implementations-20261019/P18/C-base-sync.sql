-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P18 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectContracts] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P18SourceMapper.xml
SELECT id AS contractSourceId,contractNo,projectGroupCode,createTime AS contractCreateTime,createBy AS contractCreateBy,updateTime AS contractUpdateTime,updateBy AS contractUpdateBy FROM pm_project_contract ORDER BY id LIMIT #{limit};

-- [selectGroups] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P18SourceMapper.xml
SELECT id AS groupId,projectGroupCode AS groupProjectGroupCode,projectGroupName AS groupProjectGroupName,projectType,createTime AS groupCreateTime,createBy AS groupCreateBy,updateTime AS groupUpdateTime,updateBy AS groupUpdateBy FROM pm_project_group WHERE projectType='10' ORDER BY id LIMIT #{limit};

-- [selectMembers] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P18SourceMapper.xml
SELECT id AS memberId,projectGroupCode AS memberProjectGroupCode,projectCode AS memberProjectCode,mergeBranchMark,smsProjectCode,createTime AS memberCreateTime,createBy AS memberCreateBy,updateTime AS memberUpdateTime,updateBy AS memberUpdateBy FROM pm_project_group_relationship ORDER BY id LIMIT #{limit};

-- [selectProjects] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P18SourceMapper.xml
SELECT p.projectId AS originalProjectId,p.projectCode AS originalProjectCode FROM pm_project p JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen ON chosen.projectId=p.projectId WHERE p.disabled=0 AND p.effectiveTo IS NULL AND p.projectType='10' ORDER BY p.projectId LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$TargetMapper.selectContracts; pms-module-integration/src/main/resources/mapper/basesync/P18TargetMapper.xml:4; kind=select
SELECT id,company_code,contract_no,deleted FROM com_contract WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND ( /* FOREACH collection=query.keys; item=key; separator= OR  */ (company_code=#{key.companyCode} AND contract_no=#{key.contractNo}) /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$TargetMapper.selectRelations; pms-module-integration/src/main/resources/mapper/basesync/P18TargetMapper.xml:5; kind=select
SELECT id,tenant_id,project_id,contract_id,relation_role,source_system,source_table,source_record_key,effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted FROM com_project_contract_relation WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND ( /* FOREACH collection=query.keys; item=key; separator= OR  */ (project_id=#{key.projectId} AND contract_id=#{key.contractId} AND relation_role='RELATED') /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$TargetMapper.oneSourcePerTargetIndex; pms-module-integration/src/main/resources/mapper/basesync/P18TargetMapper.xml:7; kind=select
SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='int_base_sync_binding' AND index_name='uk_int_base_sync_binding_target' AND non_unique=0;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$TargetMapper.insertProjectContract; pms-module-integration/src/main/resources/mapper/basesync/P18TargetMapper.xml:6; kind=insert
INSERT INTO com_project_contract_relation (id,tenant_id,project_id,contract_id,relation_role,source_system,source_table,source_record_key,effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.contractId},#{row.target.relationRole},#{row.target.sourceSystem},#{row.target.sourceTable},#{row.target.sourceRecordKey},#{row.target.effectiveFrom},#{row.target.effectiveTo},#{row.target.status},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
