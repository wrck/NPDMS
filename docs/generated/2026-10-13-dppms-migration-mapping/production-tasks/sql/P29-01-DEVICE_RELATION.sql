-- P29 / DEVICE_RELATION | 原任务 2107037031219654657 v14
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT after_sales_scope.* FROM (SELECT * FROM fb_shipment_barcode_relation WHERE sn1 <> sn2) after_sales_scope
JOIN (SELECT DISTINCT pc.contractNo FROM pm_project_group_relationship m
 JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
 JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
 JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
 WHERE (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
 WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
 AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))) project_scope ON project_scope.contractNo=after_sales_scope.`contract` AND FIND_IN_SET(after_sales_scope.`contract`,project_scope.contractNo);
