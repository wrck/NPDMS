-- 当前实现优先见 ../base-sync-department-code-preview-20261019.sql 与 .md；本文件以下为早期冻结SQL。
-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P19 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectProjectParty] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P19SourceMapper.xml
SELECT STRAIGHT_JOIN r.id AS sourceId,r.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,p.projectType,r.partyRole,r.partyCode,r.partyName,r.createTime,r.createBy AS source_creator,r.updateTime,r.updateBy AS source_updater,r.effectiveFrom,r.effectiveTo,0 AS state FROM pm_project_related_party r
JOIN pm_project p ON p.projectId=r.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' ORDER BY r.id LIMIT #{limit};

-- [selectProjectState] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P19SourceMapper.xml
SELECT STRAIGHT_JOIN r.projectId AS sourceId,r.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,p.projectType,r.projectPlanState,r.projectplanTime,r.shipmentState,r.shipmentTime,r.executionState,r.executionStateTime,r.closeProcessState,r.closeProcessStateTime,1 AS state FROM pm_project_state r
JOIN pm_project p ON p.projectId=r.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' AND p.projectId=canonical.projectId ORDER BY r.projectId LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$TargetMapper.selectParties; pms-module-integration/src/main/resources/mapper/basesync/P19TargetMapper.xml:3; kind=select
SELECT `id`,`tenant_id`,`project_id`,`party_role`,`party_code`,`party_name`,`contact_name`,`phone`,`source_system`,`source_table`,`source_record_key`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS' AND source_table='pm_project_related_party'
 /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND source_record_key IN  /* FOREACH collection=query.keys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$TargetMapper.insertProjectParty; pms-module-integration/src/main/resources/mapper/basesync/P19TargetMapper.xml:5; kind=insert
INSERT INTO proj_project_party (`id`,`tenant_id`,`project_id`,`party_role`,`party_code`,`party_name`,`contact_name`,`phone`,`source_system`,`source_table`,`source_record_key`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.partyRole},#{row.target.partyCode},#{row.target.partyName},#{row.target.contactName},#{row.target.phone},#{row.target.sourceSystem},#{row.target.sourceTable},#{row.target.sourceRecordKey},#{row.target.effectiveFrom},#{row.target.effectiveTo},#{row.target.status},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$TargetMapper.updateProjectParty; pms-module-integration/src/main/resources/mapper/basesync/P19TargetMapper.xml:6; kind=update
UPDATE proj_project_party SET `project_id`=#{row.target.projectId},`party_role`=#{row.target.partyRole},`party_code`=#{row.target.partyCode},`party_name`=#{row.target.partyName},`effective_from`=#{row.target.effectiveFrom},`effective_to`=#{row.target.effectiveTo},`version`=#{row.target.version},`creator`=#{row.target.creator},`create_time`=#{row.target.createTime},`updater`=#{row.target.updater},`update_time`=#{row.target.updateTime} WHERE tenant_id=#{context.tenantId} AND id=#{row.target.id} AND version=#{row.expectedVersion} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
