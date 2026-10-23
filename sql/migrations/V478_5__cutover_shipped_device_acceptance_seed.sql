-- 用户授权补充割接验收设备。仅匹配既有演示项目及其演示设备；不改真实发货或现有历史。
-- 本样例无真实设备端点，不能用于实际设备命令执行。
INSERT INTO ast_device (
    id, tenant_id, sn, name, product_code, product_model, product_name,
    project_id, project_assignment_version, customer_id, customer_assignment_version,
    shipment_time, package_no, shipment_record_id, status, remark,
    source_system, source_key, source_version, sync_status,
    company_id, company_name, department_id, department_code, department_name,
    organization_source, organization_updated_at, creator, updater
)
SELECT 970478500000000001, p.tenant_id, 'QA-CUT-SHIPPED-20261015-01', '割接已发货验收样例设备',
       'QA-CUT-PRODUCT', 'QA-CUT-MODEL', '割接验收样例（非实物）',
       p.id, 1, p.customer_id, 1,
       '2026-10-15 09:00:00', 'QA-CUT-PACKAGE-20261015', 970478500000000002,
       'IN_STOCK', '用户授权补充的验收样例；发货为测试来源事实，不代表真实物流，不执行设备命令',
       'CUTOVER_ACCEPTANCE', 'QA-CUT-SHIPPED-20261015-01', '1', 'NOT_APPLICABLE',
       d.company_id, d.company_name, d.department_id, d.department_code, d.department_name,
       d.organization_source, CURRENT_TIMESTAMP(3), 'cutover_acceptance_seed', 'cutover_acceptance_seed'
FROM proj_project p JOIN ast_device d ON d.project_id = p.id AND d.tenant_id = p.tenant_id
WHERE p.id = 1001 AND p.tenant_id = 1 AND p.project_code = 'PROJ-2026-001' AND p.deleted = b'0'
  AND d.id = 2001 AND d.sn = 'SN-EQP-EXP-1001-1' AND d.deleted = b'0'
  AND d.department_code = 'OFFICE-HZ-DEMO'
  AND NOT EXISTS (SELECT 1 FROM ast_device WHERE id = 970478500000000001
                  OR (tenant_id = 1 AND sn = 'QA-CUT-SHIPPED-20261015-01'));

INSERT INTO ast_device_shipment (
    id, tenant_id, device_sn, shipment_time, package_no, event_type,
    source_system, source_key, source_version, source_updated_at, synced_at, sync_status, creator, updater
)
SELECT 970478500000000002, tenant_id, sn, shipment_time, package_no, 'ACCEPTANCE_SAMPLE',
       'CUTOVER_ACCEPTANCE', 'QA-CUT-SHIPMENT-20261015-01', '1', shipment_time, CURRENT_TIMESTAMP(3),
       'NOT_APPLICABLE', 'cutover_acceptance_seed', 'cutover_acceptance_seed'
FROM ast_device WHERE id = 970478500000000001 AND source_system = 'CUTOVER_ACCEPTANCE'
  AND NOT EXISTS (SELECT 1 FROM ast_device_shipment WHERE id = 970478500000000002
                  OR (tenant_id = 1 AND source_system = 'CUTOVER_ACCEPTANCE' AND source_key = 'QA-CUT-SHIPMENT-20261015-01'));

INSERT INTO ast_device_project_relationship (
    id, tenant_id, device_sn, project_id, relationship_type, effective_from, assignment_version,
    reason, operation_id, source_system, source_key, source_version, creator, updater
)
SELECT 970478500000000003, tenant_id, sn, project_id, 'DIRECT', '2026-10-15 09:00:00', 1,
       '用户授权割接验收样例初始化', 'QA-CUT-PROJECT-20261015-01', 'CUTOVER_ACCEPTANCE',
       'QA-CUT-PROJECT-20261015-01', '1', 'cutover_acceptance_seed', 'cutover_acceptance_seed'
FROM ast_device WHERE id = 970478500000000001 AND source_system = 'CUTOVER_ACCEPTANCE'
  AND NOT EXISTS (SELECT 1 FROM ast_device_project_relationship WHERE id = 970478500000000003
                  OR (tenant_id = 1 AND source_system = 'CUTOVER_ACCEPTANCE' AND source_key = 'QA-CUT-PROJECT-20261015-01'));

INSERT INTO ast_device_customer_relationship (
    id, tenant_id, device_sn, customer_id, relationship_type, effective_from, assignment_version,
    reason, operation_id, source_system, source_key, source_version, creator, updater
)
SELECT 970478500000000004, tenant_id, sn, customer_id, 'DIRECT', '2026-10-15 09:00:00', 1,
       '用户授权割接验收样例初始化', 'QA-CUT-CUSTOMER-20261015-01', 'CUTOVER_ACCEPTANCE',
       'QA-CUT-CUSTOMER-20261015-01', '1', 'cutover_acceptance_seed', 'cutover_acceptance_seed'
FROM ast_device WHERE id = 970478500000000001 AND source_system = 'CUTOVER_ACCEPTANCE' AND customer_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ast_device_customer_relationship WHERE id = 970478500000000004
                  OR (tenant_id = 1 AND source_system = 'CUTOVER_ACCEPTANCE' AND source_key = 'QA-CUT-CUSTOMER-20261015-01'));
