-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P20 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectRealProduct] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P20SourceMapper.xml
SELECT l.id AS sourceId,l.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,p.projectType,
l.contractNo AS contractNo,
l.projectCode AS projectCode,
l.orderExecNumber AS executionNo,
l.productFirstName AS productCategoryName,
l.productName AS productName,
l.productSubCode AS productSubCode,
l.productSubModel AS productSubModel,
l.productSubName AS productSubName,
l.num AS quantity,
l.memo AS memo,u.dataSource,COALESCE(u.matchCount,0) AS upstreamMatchCount,
u.projectCode AS upstreamProjectCode,
u.orderExecNumber AS upstreamExecutionNo,
u.productFirstName AS upstreamProductCategoryName,
u.productName AS upstreamProductName,
u.productSubCode AS upstreamProductSubCode,
u.productSubModel AS upstreamProductSubModel,
u.productSubName AS upstreamProductSubName,
u.num AS upstreamQuantity,
u.memo AS upstreamMemo
FROM pm_project_product_line_real l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
LEFT JOIN (
 SELECT MIN(projectCode) AS projectCode,MIN(orderExecNumber) AS orderExecNumber,MIN(productFirstName) AS productFirstName,MIN(productName) AS productName,
 MIN(productSubCode) AS productSubCode,MIN(productSubModel) AS productSubModel,MIN(productSubName) AS productSubName,MIN(num) AS num,MIN(memo) AS memo,
 CASE WHEN COUNT(*)=1 THEN MAX(dataSource) ELSE NULL END AS dataSource,COUNT(*) AS matchCount
 FROM pm_project_real_product_line_from_sms GROUP BY CAST(projectCode AS BINARY),CAST(orderExecNumber AS BINARY),CAST(productFirstName AS BINARY),CAST(productName AS BINARY),CAST(productSubCode AS BINARY),CAST(productSubModel AS BINARY),CAST(productSubName AS BINARY),num,CAST(memo AS BINARY)
) u ON CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY) AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY) AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY) AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY) AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY) AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY) AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY) AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY)
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10'
ORDER BY l.id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P20RealProductSyncTask$TargetMapper.selectProjects; pms-module-integration/src/main/resources/mapper/basesync/P20TargetMapper.xml:3; kind=select
SELECT id,tenant_id,project_id,contract_no,project_code,execution_no,product_category_name,product_name,product_sub_code,product_sub_model,product_sub_name,quantity,memo,data_source,creator,create_time,updater,update_time,deleted FROM proj_project_real_product WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND project_id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P20RealProductSyncTask$TargetMapper.insertRealProduct; pms-module-integration/src/main/resources/mapper/basesync/P20TargetMapper.xml:5; kind=insert
INSERT INTO proj_project_real_product (id,tenant_id,project_id,contract_no,project_code,execution_no,product_category_name,product_name,product_sub_code,product_sub_model,product_sub_name,quantity,memo,data_source,creator,create_time,updater,update_time,deleted) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.contractNo},#{row.target.projectCode},#{row.target.executionNo},#{row.target.productCategoryName},#{row.target.productName},#{row.target.productSubCode},#{row.target.productSubModel},#{row.target.productSubName},#{row.target.quantity},#{row.target.memo},#{row.target.dataSource},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
