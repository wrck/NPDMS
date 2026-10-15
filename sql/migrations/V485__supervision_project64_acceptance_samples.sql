-- User-approved isolated project 64 synthetic acceptance fixture (2026-10-15).
-- Source is ACCEPTANCE_SAMPLE, not ERP or actual logistics. No device endpoint or secrets.
INSERT INTO ast_device (id,tenant_id,sn,name,product_code,product_model,product_name,
 project_id,project_assignment_version,customer_id,customer_assignment_version,
 shipment_time,package_no,shipment_record_id,status,remark,
 source_system,source_key,source_version,sync_status,creator,updater)
SELECT 970485640000000001,p.tenant_id,'QA-SUPERVISION-64-20261015',
 '督导闭环验收虚拟设备','QA-SUPERVISION-64','QA-VIRTUAL-FW','非实物验收样例',
 p.id,1,p.customer_id,1,'2026-10-15 23:00:00','QA-SUPERVISION-64',970485640000000002,
 'IN_STOCK','隔离验收样例，发货为合成测试事实，不代表真实物流，不执行设备命令',
 'SUPERVISION_ACCEPTANCE','QA-SUPERVISION-64-20261015','1','NOT_APPLICABLE',
 'supervision_acceptance','supervision_acceptance'
FROM proj_project p WHERE p.id=2110018733130579974 AND p.tenant_id=1
 AND p.project_code='PJT2026000064' AND p.deleted=b'0'
 AND NOT EXISTS(SELECT 1 FROM ast_device WHERE id=970485640000000001
 OR (tenant_id=1 AND sn='QA-SUPERVISION-64-20261015'));
INSERT INTO ast_device_shipment (id,tenant_id,device_sn,shipment_time,package_no,event_type,
 source_system,source_key,source_version,source_updated_at,synced_at,sync_status,creator,updater)
SELECT 970485640000000002,tenant_id,sn,shipment_time,package_no,'ACCEPTANCE_SAMPLE',
 source_system,'QA-SUPERVISION-64-SHIPMENT','1',shipment_time,CURRENT_TIMESTAMP(3),'NOT_APPLICABLE',
 'supervision_acceptance','supervision_acceptance'
FROM ast_device WHERE id=970485640000000001 AND source_system='SUPERVISION_ACCEPTANCE'
 AND NOT EXISTS(SELECT 1 FROM ast_device_shipment WHERE id=970485640000000002
 OR (tenant_id=1 AND source_system='SUPERVISION_ACCEPTANCE' AND source_key='QA-SUPERVISION-64-SHIPMENT'));
INSERT INTO ast_device_project_relationship (id,tenant_id,device_sn,project_id,relationship_type,
 effective_from,assignment_version,reason,operation_id,source_system,source_key,source_version,creator,updater)
SELECT 970485640000000003,tenant_id,sn,project_id,'DIRECT','2026-10-15 23:00:00',1,
 '本地项目64合成验收样例初始化','QA-SUPERVISION-64-PROJECT',source_system,
 'QA-SUPERVISION-64-PROJECT','1','supervision_acceptance','supervision_acceptance'
FROM ast_device WHERE id=970485640000000001 AND source_system='SUPERVISION_ACCEPTANCE'
 AND NOT EXISTS(SELECT 1 FROM ast_device_project_relationship WHERE id=970485640000000003
 OR (tenant_id=1 AND source_system='SUPERVISION_ACCEPTANCE' AND source_key='QA-SUPERVISION-64-PROJECT'));
