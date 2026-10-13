SELECT STRAIGHT_JOIN b.*,p.packdate AS shipment_time,f.contract_code AS contract_no,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END AS event_type,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END AS rma_marked
FROM fb_shipment_barcode b
STRAIGHT_JOIN fb_shipment p ON p.packlist_id=b.pack_id
STRAIGHT_JOIN fb_contract f ON f.contract_id=p.con_id
STRAIGHT_JOIN (SELECT DISTINCT pc.contractNo FROM pm_project_group_relationship m
 JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
 JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
 JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
 WHERE (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
 WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
 AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))) project_scope
 ON project_scope.contractNo=f.contract_code AND FIND_IN_SET(f.contract_code,project_scope.contractNo)