-- P18 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_contract] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT c.id AS id,
  c.contractNo AS contractNo,
  c.projectGroupCode AS projectGroupCode,
  c.createTime AS createTime,
  c.createBy AS createBy,
  c.updateTime AS updateTime,
  c.updateBy AS updateBy,
  m.id AS member_id,
  m.projectGroupCode AS member_projectGroupCode,
  m.projectCode AS member_projectCode,
  m.mergeBranchMark AS member_mergeBranchMark,
  m.smsProjectCode AS member_smsProjectCode,
  m.createTime AS member_createTime,
  m.createBy AS member_createBy,
  m.updateTime AS member_updateTime,
  m.updateBy AS member_updateBy,
  g.id AS group_id,
  g.projectGroupCode AS group_projectGroupCode,
  g.projectGroupName AS group_projectGroupName,
  g.projectType AS group_projectType,
  g.createTime AS group_createTime,
  g.createBy AS group_createBy,
  g.updateTime AS group_updateTime,
  g.updateBy AS group_updateBy,
  p.projectId AS originalProjectId,
  p.projectCode AS originalProjectCode,
  IF(p.projectId IS NULL,0,1) AS projectCandidateCount,
  CONCAT(c.id,':',COALESCE(m.id,0)) AS relationOriginalKey,
  (SELECT COUNT(*) FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)) AS formalGroupSize,
  IF(RIGHT(CAST(km.projectCode AS BINARY),4)=CAST('-his' AS BINARY),'HISTORICAL','ACTIVE') AS formalStatus,
  COALESCE(kc.createTime,km.createTime) AS formalCreatedAt,
  COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS formalCreator,
  COALESCE(uc.updateTime,um.updateTime) AS formalUpdatedAt,
  COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS formalUpdater,
  IF((SELECT COUNT(*) FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY))=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=IF(RIGHT(CAST(km.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)
    AND COALESCE(d.createTime,dm.createTime) <=> COALESCE(kc.createTime,km.createTime)
    AND NOT (CAST(COALESCE(NULLIF(d.createBy,''),NULLIF(dm.createBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS BINARY)))) AS formalCreateConflicts,
  IF((SELECT COUNT(*) FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY))=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=IF(RIGHT(CAST(km.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)
    AND COALESCE(d.updateTime,dm.updateTime) <=> COALESCE(uc.updateTime,um.updateTime)
    AND NOT (CAST(COALESCE(NULLIF(d.updateBy,''),NULLIF(dm.updateBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS BINARY)))) AS formalUpdateConflicts,
  kc.id AS canonical_id,
  kc.contractNo AS canonical_contractNo,
  kc.projectGroupCode AS canonical_projectGroupCode,
  kc.createTime AS canonical_createTime,
  kc.createBy AS canonical_createBy,
  kc.updateTime AS canonical_updateTime,
  kc.updateBy AS canonical_updateBy,
  km.id AS canonical_member_id,
  km.projectGroupCode AS canonical_member_projectGroupCode,
  km.projectCode AS canonical_member_projectCode,
  km.mergeBranchMark AS canonical_member_mergeBranchMark,
  km.smsProjectCode AS canonical_member_smsProjectCode,
  km.createTime AS canonical_member_createTime,
  km.createBy AS canonical_member_createBy,
  km.updateTime AS canonical_member_updateTime,
  km.updateBy AS canonical_member_updateBy,
  kg.id AS canonical_group_id,
  kg.projectGroupCode AS canonical_group_projectGroupCode,
  kg.projectGroupName AS canonical_group_projectGroupName,
  kg.projectType AS canonical_group_projectType,
  kg.createTime AS canonical_group_createTime,
  kg.createBy AS canonical_group_createBy,
  kg.updateTime AS canonical_group_updateTime,
  kg.updateBy AS canonical_group_updateBy,
  p.projectId AS canonical_originalProjectId,
  p.projectCode AS canonical_originalProjectCode,
  CONCAT(LPAD(kc.id,20,'0'),':',LPAD(COALESCE(km.id,0),20,'0'),':',
    IF(c.id=kc.id AND COALESCE(m.id,0)=COALESCE(km.id,0),0,1),':',
    LPAD(c.id,20,'0'),':',LPAD(COALESCE(m.id,0),20,'0')) AS relationSourceKey
FROM pm_project_contract c
LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectType=g.projectType AND p.projectCode=REPLACE(m.projectCode,'-his','')
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)

LEFT JOIN pm_project_contract kc ON kc.id=(SELECT pick_c.id FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) ORDER BY CONCAT(IF(RIGHT(CAST(pick_m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(pick_c.createTime,pick_m.createTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(pick_c.id,20,'0'),LPAD(COALESCE(pick_m.id,0),20,'0')) ASC LIMIT 1)
LEFT JOIN pm_project_group_relationship km ON km.id=(SELECT pick_m.id FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) ORDER BY CONCAT(IF(RIGHT(CAST(pick_m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(pick_c.createTime,pick_m.createTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(pick_c.id,20,'0'),LPAD(COALESCE(pick_m.id,0),20,'0')) ASC LIMIT 1)
LEFT JOIN pm_project_group kg ON kg.projectGroupCode=kc.projectGroupCode AND CAST(kg.projectGroupCode AS BINARY)=CAST(kc.projectGroupCode AS BINARY)
LEFT JOIN pm_project_contract uc ON uc.id=(SELECT pick_c.id FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) ORDER BY CONCAT(1-IF(RIGHT(CAST(pick_m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(pick_c.updateTime,pick_m.updateTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(pick_c.id,20,'0'),LPAD(COALESCE(pick_m.id,0),20,'0')) DESC LIMIT 1)
LEFT JOIN pm_project_group_relationship um ON um.id=(SELECT pick_m.id FROM pm_project_contract pick_c LEFT JOIN pm_project_group pick_g ON pick_g.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_g.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) LEFT JOIN pm_project_group_relationship pick_m ON pick_m.projectGroupCode=pick_c.projectGroupCode AND CAST(pick_m.projectGroupCode AS BINARY)=CAST(pick_c.projectGroupCode AS BINARY) WHERE pick_c.contractNo <=> c.contractNo AND CAST(pick_c.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY) AND CAST(pick_g.projectType AS BINARY) <=> CAST(g.projectType AS BINARY) AND CAST(REPLACE(pick_m.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) ORDER BY CONCAT(1-IF(RIGHT(CAST(pick_m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(pick_c.updateTime,pick_m.updateTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(pick_c.id,20,'0'),LPAD(COALESCE(pick_m.id,0),20,'0')) DESC LIMIT 1)
WHERE (g.id IS NULL OR FIND_IN_SET(g.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRelationMapper.selectIdentity; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:12; kind=select
SELECT id, tenant_id, project_id, contract_id, relation_role, source_system, source_table,
               source_record_key, effective_from, effective_to, status, version,
               creator, create_time, updater, update_time, deleted
        FROM com_project_contract_relation
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND contract_id = #{query.contractId} AND relation_role = 'RELATED';

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRelationMapper.selectIdentityForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:15; kind=select
SELECT id, tenant_id, project_id, contract_id, relation_role, source_system, source_table,
               source_record_key, effective_from, effective_to, status, version,
               creator, create_time, updater, update_time, deleted
        FROM com_project_contract_relation
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND contract_id = #{query.contractId} AND relation_role = 'RELATED' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRelationContractMapper.selectMigrationIdentity; pms-module-integration/src/main/resources/mapper/sync/DirectRelationContractMapper.xml:4; kind=select
SELECT id,tenant_id,deleted,company_code,contract_no FROM com_contract
  WHERE tenant_id=#{query.tenantId} AND company_code=#{query.companyCode} AND contract_no=#{query.contractNo} AND deleted=FALSE
  ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRelationContractMapper.selectByIdForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectRelationContractMapper.xml:9; kind=select
SELECT id,tenant_id,deleted,company_code,contract_no FROM com_contract
  WHERE tenant_id=#{query.tenantId} AND id=#{query.contractId} AND deleted=FALSE FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRelationMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:18; kind=insert
INSERT INTO com_project_contract_relation
        (id,tenant_id,project_id,contract_id,relation_role,source_system,source_table,source_record_key,
         effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted)
        VALUES (#{row.id},#{row.tenantId},#{row.projectId},#{row.contractId},#{row.relationRole},#{row.sourceSystem},
                #{row.sourceTable},#{row.sourceRecordKey},#{row.effectiveFrom},#{row.effectiveTo},#{row.status},
                #{row.version},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRelationMapper.correctHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:27; kind=update
UPDATE com_project_contract_relation
        SET creator=#{write.after.creator}, create_time=#{write.after.createTime},
            updater=#{write.after.updater}, update_time=#{write.after.updateTime},
            effective_from=#{write.after.effectiveFrom}, version=#{write.after.version}
        WHERE tenant_id=#{write.before.tenantId} AND id=#{write.before.id} AND version=#{write.before.version}
          AND deleted=0;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRelationMapper.restoreWithdrawn; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRelationMapper.xml:36; kind=update
UPDATE com_project_contract_relation
        SET deleted=0, version=#{write.after.version}, update_time=update_time
        WHERE tenant_id=#{write.before.tenantId} AND id=#{write.before.id}
          AND version=#{write.before.version} AND deleted=1;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
