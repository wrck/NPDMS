-- User approved on 2026-10-16: one project65 synthetic shipment and its device projection.
-- One synthetic shipment for the existing project 65 acceptance-only device.
-- ACCEPTANCE_SAMPLE is not ERP or actual logistics. No device commands or secrets.
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970486650000000002, d.tenant_id, d.sn, '2026-10-16 04:00:00',
  'QA-DIRECT65-SAMPLE', 'ACCEPTANCE_SAMPLE', 'DIRECT_ACCEPTANCE',
  'DIRECT65-LAB-20261016-SHIPMENT', '1', '2026-10-16 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'direct65_acceptance', 'direct65_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2110820647055495169 AND d.tenant_id=1
  AND d.sn='DIRECT65-LAB-20261016-01' AND d.project_id=2110018733130579975
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000065' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970486650000000002 OR
      (tenant_id=1 AND source_system='DIRECT_ACCEPTANCE' AND source_key='DIRECT65-LAB-20261016-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970486650000000002 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='direct65_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2110820647055495169 AND d.tenant_id=1
  AND d.sn='DIRECT65-LAB-20261016-01' AND d.project_id=2110018733130579975
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='DIRECT_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.deleted=b'0';
