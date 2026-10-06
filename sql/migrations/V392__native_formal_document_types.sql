-- Existing implementation-plan HTML exports are now saved before download.
UPDATE plt_delivery_type
SET allowed_media_json=JSON_ARRAY_APPEND(allowed_media_json,'$','html'), updater='native-formal-document', update_time=NOW(3)
WHERE type_code='IMPLEMENTATION_PLAN' AND deleted=b'0'
  AND JSON_VALID(allowed_media_json) AND NOT JSON_CONTAINS(allowed_media_json,JSON_QUOTE('html'));

-- Engineering briefing is an existing SOL formal output (FR-ENG-006), not a new workflow.
INSERT INTO plt_delivery_type(type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT 'BRIEFING_DOCUMENT','工程交底书','工程交付','["html","pdf"]',52428800,b'1',
       'Existing SOL engineering briefing output',0,t.tenant_id,'native-formal-document',NOW(3),'native-formal-document',NOW(3),b'0'
FROM (SELECT DISTINCT tenant_id FROM plt_delivery_type WHERE type_code='IMPLEMENTATION_PLAN' AND deleted=b'0') t
WHERE NOT EXISTS(SELECT 1 FROM plt_delivery_type d WHERE d.tenant_id=t.tenant_id AND d.type_code='BRIEFING_DOCUMENT' AND d.deleted=b'0');
