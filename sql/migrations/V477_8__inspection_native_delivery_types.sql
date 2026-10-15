-- 巡检原生文件来源目录：与 InspectionFilePolicy 的四种用途及媒体策略一致。
-- 补充本地业务基线租户1的初始化遗漏；不建立交付要求、不改变业务完成条件。
-- 既有编码（含已停用或逻辑删除的配置）保持原样，由配置Owner决定是否恢复。
INSERT INTO plt_delivery_type
  (type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,updater,deleted)
SELECT seed.type_code,seed.name,'巡检服务',seed.allowed_media_json,52428800,b'1',
  '巡检原生文件来源；业务授权、文件类型及不可变引用仍由SRV和PLT校验',0,tenant.id,
  'inspection-native-files-20261015','inspection-native-files-20261015',b'0'
FROM system_tenant tenant
CROSS JOIN (
  SELECT 'INS.LOG' AS type_code,'巡检日志' AS name,
    '["text/plain","application/zip","application/json"]' AS allowed_media_json
  UNION ALL SELECT 'INS.REPORT','巡检报告',
    '["application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/xml","text/xml"]'
  UNION ALL SELECT 'INS.EVIDENCE','巡检处理证据',
    '["application/pdf","image/png","image/jpeg","text/plain","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document"]'
  UNION ALL SELECT 'INS.ATTACHMENT','巡检附件',
    '["application/pdf","image/png","image/jpeg","text/plain","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document"]'
) seed
WHERE tenant.id=1 AND tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_type actual
    WHERE actual.tenant_id=tenant.id AND actual.type_code=seed.type_code);
