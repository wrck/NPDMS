-- Approved 2026-10-22: only the five isolated v2 acceptance projects 68-72.
-- Synthetic shipment fixtures; no real ERP/order/logistics or lifecycle state changes.
-- Exact project/device/SN/tenant guards; existing shipment data remains untouched.

-- PJT2026000068: V2DIRECT68-20261022-01
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970512000000000068, d.tenant_id, d.sn, '2026-10-22 04:00:00',
  'QA-V2-68-SAMPLE', 'ACCEPTANCE_SAMPLE', 'V2_ACCEPTANCE',
  'V2-68-BROWSER-20261022-SHIPMENT', '1', '2026-10-22 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'v2_runtime_acceptance', 'v2_runtime_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2113097124874821633 AND d.tenant_id=1
  AND d.sn='V2DIRECT68-20261022-01' AND d.project_id=2110018733130579978
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000068' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970512000000000068 OR
      (tenant_id=1 AND source_system='V2_ACCEPTANCE' AND source_key='V2-68-BROWSER-20261022-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970512000000000068 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='v2_runtime_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2113097124874821633 AND d.tenant_id=1
  AND d.sn='V2DIRECT68-20261022-01' AND d.project_id=2110018733130579978
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='V2_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL AND d.deleted=b'0';

-- PJT2026000069: V2-CHANNEL69-SYNTH-20261022
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970512000000000069, d.tenant_id, d.sn, '2026-10-22 04:00:00',
  'QA-V2-69-SAMPLE', 'ACCEPTANCE_SAMPLE', 'V2_ACCEPTANCE',
  'V2-69-BROWSER-20261022-SHIPMENT', '1', '2026-10-22 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'v2_runtime_acceptance', 'v2_runtime_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2113098096749584386 AND d.tenant_id=1
  AND d.sn='V2-CHANNEL69-SYNTH-20261022' AND d.project_id=2110018733130579979
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000069' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970512000000000069 OR
      (tenant_id=1 AND source_system='V2_ACCEPTANCE' AND source_key='V2-69-BROWSER-20261022-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970512000000000069 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='v2_runtime_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2113098096749584386 AND d.tenant_id=1
  AND d.sn='V2-CHANNEL69-SYNTH-20261022' AND d.project_id=2110018733130579979
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='V2_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL AND d.deleted=b'0';

-- PJT2026000070: V2SUP70-20261022-01
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970512000000000070, d.tenant_id, d.sn, '2026-10-22 04:00:00',
  'QA-V2-70-SAMPLE', 'ACCEPTANCE_SAMPLE', 'V2_ACCEPTANCE',
  'V2-70-BROWSER-20261022-SHIPMENT', '1', '2026-10-22 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'v2_runtime_acceptance', 'v2_runtime_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2113092774395912194 AND d.tenant_id=1
  AND d.sn='V2SUP70-20261022-01' AND d.project_id=2110018733130579980
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000070' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970512000000000070 OR
      (tenant_id=1 AND source_system='V2_ACCEPTANCE' AND source_key='V2-70-BROWSER-20261022-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970512000000000070 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='v2_runtime_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2113092774395912194 AND d.tenant_id=1
  AND d.sn='V2SUP70-20261022-01' AND d.project_id=2110018733130579980
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='V2_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL AND d.deleted=b'0';

-- PJT2026000071: V2-CHANNEL71-SYNTH-20261022
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970512000000000071, d.tenant_id, d.sn, '2026-10-22 04:00:00',
  'QA-V2-71-SAMPLE', 'ACCEPTANCE_SAMPLE', 'V2_ACCEPTANCE',
  'V2-71-BROWSER-20261022-SHIPMENT', '1', '2026-10-22 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'v2_runtime_acceptance', 'v2_runtime_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2113095549745897474 AND d.tenant_id=1
  AND d.sn='V2-CHANNEL71-SYNTH-20261022' AND d.project_id=2110018733130579981
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000071' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970512000000000071 OR
      (tenant_id=1 AND source_system='V2_ACCEPTANCE' AND source_key='V2-71-BROWSER-20261022-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970512000000000071 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='v2_runtime_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2113095549745897474 AND d.tenant_id=1
  AND d.sn='V2-CHANNEL71-SYNTH-20261022' AND d.project_id=2110018733130579981
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='V2_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL AND d.deleted=b'0';

-- PJT2026000072: V2DIRECT72-20261022-01
INSERT INTO ast_device_shipment
  (id, tenant_id, device_sn, shipment_time, package_no, event_type,
   source_system, source_key, source_version, source_updated_at, synced_at,
   sync_status, creator, updater)
SELECT 970512000000000072, d.tenant_id, d.sn, '2026-10-22 04:00:00',
  'QA-V2-72-SAMPLE', 'ACCEPTANCE_SAMPLE', 'V2_ACCEPTANCE',
  'V2-72-BROWSER-20261022-SHIPMENT', '1', '2026-10-22 04:00:00', CURRENT_TIMESTAMP(3),
  'NOT_APPLICABLE', 'v2_runtime_acceptance', 'v2_runtime_acceptance'
FROM ast_device d JOIN proj_project p ON p.id=d.project_id AND p.tenant_id=d.tenant_id
WHERE d.id=2113095223215136769 AND d.tenant_id=1
  AND d.sn='V2DIRECT72-20261022-01' AND d.project_id=2110018733130579982
  AND d.source_system='PLATFORM_MANUAL'
  AND p.project_code='PJT2026000072' AND d.deleted=b'0' AND p.deleted=b'0'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment
    WHERE id=970512000000000072 OR
      (tenant_id=1 AND source_system='V2_ACCEPTANCE' AND source_key='V2-72-BROWSER-20261022-SHIPMENT'));

UPDATE ast_device d
JOIN ast_device_shipment s ON s.id=970512000000000072 AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn
SET d.shipment_time=s.shipment_time, d.shipment_record_id=s.id,
    d.package_no=s.package_no, d.updater='v2_runtime_acceptance', d.update_time=CURRENT_TIMESTAMP(3)
WHERE d.id=2113095223215136769 AND d.tenant_id=1
  AND d.sn='V2DIRECT72-20261022-01' AND d.project_id=2110018733130579982
  AND d.source_system='PLATFORM_MANUAL'
  AND s.source_system='V2_ACCEPTANCE' AND s.event_type='ACCEPTANCE_SAMPLE'
  AND d.shipment_time IS NULL AND d.shipment_record_id IS NULL AND d.package_no IS NULL AND d.deleted=b'0';
