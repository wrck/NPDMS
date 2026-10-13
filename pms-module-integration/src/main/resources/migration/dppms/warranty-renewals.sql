SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
       CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM fb_service r
WHERE :projectTypes='' AND :projectIds=''
  AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns))
UNION ALL
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
       CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM (
    SELECT DISTINCT sb.barcode
    FROM pm_project_group_relationship m
    JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
    JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
    JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
    JOIN fb_contract c ON c.contract_code=pc.contractNo
    JOIN fb_shipment s ON s.con_id=c.contract_id
    JOIN fb_shipment_barcode sb ON sb.pack_id=s.packlist_id
    WHERE (:projectTypes<>'' OR :projectIds<>'')
      AND (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
      AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
      AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
          WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
            AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))
      AND c.contract_type IN (0,11) AND sb.barcode IS NOT NULL AND sb.barcode<>''
      AND (sb.rma_no IS NULL OR sb.rma_no='' OR sb.rma_no='null')
) selected STRAIGHT_JOIN fb_service r ON r.barcode=selected.barcode
WHERE :deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns)
