-- Existing native archive/certificate outputs; no business workflow or requirements are invented.
-- Archive file formats/5 MiB mirror the existing UploadFile entry; certificate is a business result.
INSERT INTO plt_delivery_type(type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT s.type_code,s.name,'交付资料',s.media,5242880,b'1',s.remark,0,t.tenant_id,'native-acceptance-document',NOW(3),'native-acceptance-document',NOW(3),b'0'
FROM (SELECT DISTINCT tenant_id FROM plt_delivery_type WHERE deleted=b'0') t
CROSS JOIN (
 SELECT 'ARCHIVE_DOCUMENT' type_code,'交付资料归档' name,'["application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint","text/plain","application/pdf"]' media,'Existing archive-document native files/result' remark
 UNION ALL SELECT 'COMPLETION_CERTIFICATE','完工证明','[]','Existing confirmed/archived completion certificate business result'
) s
WHERE NOT EXISTS(SELECT 1 FROM plt_delivery_type d WHERE d.tenant_id=t.tenant_id AND d.type_code=s.type_code AND d.deleted=b'0');
