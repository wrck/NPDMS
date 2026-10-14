-- P25 / fb_service | 原任务 2110010787726274561 v5
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
 CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
 FROM fb_service r WHERE :projectTypes='' AND :projectIds=''
 AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns))
 UNION ALL
 SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
 CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
 FROM (
 SELECT DISTINCT rn.barcode FROM (SELECT DISTINCT barcode FROM fb_service
 WHERE barcode IS NOT NULL AND barcode<>'' AND (:deviceSns='' OR FIND_IN_SET(barcode,:deviceSns))) rn
 STRAIGHT_JOIN fb_shipment_barcode sb ON sb.barcode=rn.barcode
 STRAIGHT_JOIN (SELECT DISTINCT s.packlist_id FROM (SELECT DISTINCT pc.contractNo FROM pm_project_group_relationship m
 JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
 JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
 JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
 WHERE (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
 WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
 AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))) pc STRAIGHT_JOIN fb_contract c ON c.contract_code=pc.contractNo STRAIGHT_JOIN fb_shipment s ON s.con_id=c.contract_id WHERE c.contract_type IN (0,11)) packages ON packages.packlist_id=sb.pack_id
 WHERE (:projectTypes<>'' OR :projectIds<>'')
 AND (sb.rma_no IS NULL OR sb.rma_no='' OR sb.rma_no='null')
 ) selected STRAIGHT_JOIN fb_service r ON r.barcode=selected.barcode
 WHERE :deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns);
