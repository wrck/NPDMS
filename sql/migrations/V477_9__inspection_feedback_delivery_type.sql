-- 巡检误报导出来源：与 InspectionFeedbackExportFilePolicy 的 CSV/XLSX 策略一致。
-- 仅补充基线租户1缺失的配置，不覆盖既有编码（含停用或逻辑删除记录）。
INSERT INTO plt_delivery_type
  (type_code,name,category,allowed_media_json,max_size_bytes,enabled,remark,version,tenant_id,creator,updater,deleted)
SELECT 'INS.EXPORT','巡检误报导出','巡检服务',
  '["text/csv","text/plain","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"]',
  52428800,b'1','巡检误报导出来源；业务授权、文件类型及不可变引用仍由SRV和PLT校验',
  0,tenant.id,'inspection-native-files-20261015','inspection-native-files-20261015',b'0'
FROM system_tenant tenant
WHERE tenant.id=1 AND tenant.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM plt_delivery_type actual
    WHERE actual.tenant_id=tenant.id AND actual.type_code='INS.EXPORT');
