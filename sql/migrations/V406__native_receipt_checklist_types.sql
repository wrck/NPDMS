-- Existing receipt and passed checklist outputs; no file or requirement is fabricated.
INSERT INTO plt_delivery_type(type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT s.type_code,s.name,'交付资料','[]',52428800,b'1',s.remark,0,t.tenant_id,'native-receipt-checklist',NOW(3),'native-receipt-checklist',NOW(3),b'0'
FROM (SELECT DISTINCT tenant_id FROM plt_delivery_type WHERE deleted=b'0') t
CROSS JOIN (SELECT 'RECEIPT' type_code,'签收单' name,'Existing signed arrival business result' remark
 UNION ALL SELECT 'DELIVERABLE_CHECKLIST','交付件核对清单','Existing passed native checklist business result') s
WHERE NOT EXISTS(SELECT 1 FROM plt_delivery_type d WHERE d.tenant_id=t.tenant_id AND d.type_code=s.type_code AND d.deleted=b'0');
