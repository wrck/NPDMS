-- P20 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_product_line_real] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT l.*, p.projectCode AS originalProjectCode, p.projectType,
       u.projectCode AS upstream_projectCode, u.orderExecNumber AS upstream_orderExecNumber,
       u.productFirstName AS upstream_productFirstName, u.productName AS upstream_productName,
       u.productSubCode AS upstream_productSubCode, u.productSubModel AS upstream_productSubModel,
       u.productSubName AS upstream_productSubName, u.num AS upstream_num,
       u.memo AS upstream_memo, u.dataSource AS upstream_dataSource,
       COALESCE(u.matchCount,0) AS upstreamMatchCount
FROM pm_project_product_line_real l
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) p ON p.projectId=l.projectId
LEFT JOIN (
  SELECT MIN(projectCode) AS projectCode, MIN(orderExecNumber) AS orderExecNumber,
    MIN(productFirstName) AS productFirstName, MIN(productName) AS productName,
    MIN(productSubCode) AS productSubCode, MIN(productSubModel) AS productSubModel,
    MIN(productSubName) AS productSubName, MIN(num) AS num, MIN(memo) AS memo,
    CASE WHEN COUNT(*)=1 THEN MAX(dataSource) ELSE NULL END AS dataSource, COUNT(*) AS matchCount
  FROM pm_project_real_product_line_from_sms
  GROUP BY CAST(projectCode AS BINARY), CAST(orderExecNumber AS BINARY),
    CAST(productFirstName AS BINARY), CAST(productName AS BINARY), CAST(productSubCode AS BINARY),
    CAST(productSubModel AS BINARY), CAST(productSubName AS BINARY), num, CAST(memo AS BINARY)
) u ON u.projectCode <=> l.projectCode
 AND CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
 AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
 AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
 AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY)
 AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
 AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
 AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
 AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes));

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper.selectRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectRealProductMapper.xml:4; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId}
          AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper.selectBusinessRecordsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectRealProductMapper.xml:8; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND CAST(contract_no AS BINARY) <=> CAST(#{query.contractNo} AS BINARY)
          AND CAST(project_code AS BINARY) <=> CAST(#{query.projectCode} AS BINARY)
          AND CAST(execution_no AS BINARY) <=> CAST(#{query.executionNo} AS BINARY)
          AND CAST(product_category_name AS BINARY) <=> CAST(#{query.productCategoryName} AS BINARY)
          AND CAST(product_name AS BINARY) <=> CAST(#{query.productName} AS BINARY)
          AND CAST(product_sub_code AS BINARY) <=> CAST(#{query.productSubCode} AS BINARY)
          AND CAST(product_sub_model AS BINARY) <=> CAST(#{query.productSubModel} AS BINARY)
          AND CAST(product_sub_name AS BINARY) <=> CAST(#{query.productSubName} AS BINARY)
          AND quantity <=> #{query.quantity}
          AND CAST(memo AS BINARY) <=> CAST(#{query.memo} AS BINARY)
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper.countAllProjectRecords; pms-module-integration/src/main/resources/mapper/sync/DirectRealProductMapper.xml:22; kind=select
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectRealProductMapper.insert; pms-module-integration/src/main/resources/mapper/sync/DirectRealProductMapper.xml:25; kind=insert
INSERT INTO proj_project_real_product (id,tenant_id,project_id,contract_no,project_code,execution_no,product_category_name,product_name,product_sub_code,product_sub_model,product_sub_name,quantity,memo,data_source,creator,create_time,updater,update_time,deleted) VALUES (#{row.id},#{row.tenantId},#{row.projectId},#{row.contractNo},#{row.projectCode},#{row.executionNo},#{row.productCategoryName},#{row.productName},#{row.productSubCode},#{row.productSubModel},#{row.productSubName},#{row.quantity},#{row.memo},#{row.dataSource},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
