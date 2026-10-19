SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
 CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM fb_service r
WHERE r.barcode IS NOT NULL AND r.barcode<>''
 AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns))
