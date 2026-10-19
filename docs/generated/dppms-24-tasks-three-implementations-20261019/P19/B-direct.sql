-- P19 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_related_party] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_related_party r
JOIN (SELECT original.*,canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (
  SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,
    MAX(projectId) AS projectId
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.typeKey <=> CAST(original.projectType AS BINARY)
  AND canonical.codeKey <=> CAST(original.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE current_project.disabled=0 AND current_project.effectiveTo IS NULL
  AND original.disabled=0 AND original.effectiveTo IS NULL
  AND FIND_IN_SET(current_project.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) p ON p.projectId=r.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes);

-- [pm_project_state] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT s.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_state s
JOIN (SELECT original.*,canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (
  SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,
    MAX(projectId) AS projectId
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.typeKey <=> CAST(original.projectType AS BINARY)
  AND canonical.codeKey <=> CAST(original.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE current_project.disabled=0 AND current_project.effectiveTo IS NULL
  AND original.disabled=0 AND original.effectiveTo IS NULL
  AND FIND_IN_SET(current_project.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) p ON p.projectId=s.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND p.projectId=p.canonicalProjectId;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSource; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSourceForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectRecords; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:17; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.selectMigrationSourceForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:4; kind=select
SELECT * FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_record_key=#{query.sourceKey} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.insertMigrationParty; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:8; kind=insert
INSERT INTO proj_project_party (id, tenant_id, project_id, party_role, party_code, party_name, contact_name, phone, source_system, source_table, source_record_key, effective_from, effective_to, status, version, creator, create_time, updater, update_time, deleted)
        VALUES (#{command.row.id}, #{command.row.tenantId}, #{command.row.projectId}, #{command.row.partyRole}, #{command.row.partyCode}, #{command.row.partyName}, #{command.row.contactName}, #{command.row.phone}, #{command.row.sourceSystem}, #{command.row.sourceTable}, #{command.row.sourceRecordKey}, #{command.row.effectiveFrom}, #{command.row.effectiveTo}, #{command.row.status}, #{command.row.version}, #{command.creator}, #{command.row.createTime}, #{command.updater}, #{command.row.updateTime}, #{command.row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.updateMigrationParty; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:12; kind=update
UPDATE proj_project_party SET project_id=#{command.row.projectId}, party_role=#{command.row.partyRole}, party_code=#{command.row.partyCode}, party_name=#{command.row.partyName}, contact_name=#{command.row.contactName}, phone=#{command.row.phone}, source_system=#{command.row.sourceSystem}, source_table=#{command.row.sourceTable}, source_record_key=#{command.row.sourceRecordKey}, effective_from=#{command.row.effectiveFrom}, effective_to=#{command.row.effectiveTo}, status=#{command.row.status}, version=#{command.row.version}, creator=#{command.creator}, create_time=#{command.row.createTime}, updater=#{command.updater}, update_time=#{command.row.updateTime}, deleted=#{command.row.deleted}
        WHERE tenant_id=#{command.row.tenantId} AND id=#{command.row.id} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
