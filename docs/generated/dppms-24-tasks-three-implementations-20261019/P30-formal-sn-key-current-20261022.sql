-- Read-only documentation; named parameters must be supplied by the reviewed caller.
-- Existing formal SN collation defines identity. No source SN normalization or history update.
SELECT id,tenant_id,device_sn,source_system,source_key,contract_no,package_no,shipment_time,product_code
 FROM (
  SELECT s.id,s.tenant_id,s.device_sn,s.source_system,s.source_key,s.contract_no,s.package_no,s.shipment_time,s.product_code,
   ROW_NUMBER() OVER(PARTITION BY s.device_sn ORDER BY s.shipment_time DESC,s.id DESC) AS latest_rank,
   MAX(CASE WHEN s.source_system='DPPMS' THEN 1 ELSE 0 END) OVER(PARTITION BY s.device_sn) AS has_dppms
  FROM ast_device_shipment s WHERE s.tenant_id=:tenant_id AND s.deleted=FALSE
 ) ranked WHERE latest_rank=1 AND has_dppms=1 ORDER BY device_sn LIMIT :limit;

-- Existing device matching preserves each original request key.
SELECT d.id,d.sn,requests.requested_sn
FROM ast_device d
JOIN (VALUES ROW('SN-a'),ROW('sn-A')) requests(requested_sn)
 ON d.sn=CONVERT(requests.requested_sn USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE d.tenant_id=:tenant_id;
