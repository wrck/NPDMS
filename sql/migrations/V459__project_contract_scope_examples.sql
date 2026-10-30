-- Dedicated contract/order examples for root initialization, multi-order splitting and subsequent source synchronization.
-- Synthetic sources only: no project, lifecycle, child allocation or acceptance fact is written here.
-- Existing versions are retained. Re-running this seed never resets quantities changed through the source import API.
INSERT INTO com_contract
  (id, tenant_id, company_code, company_name, contract_no, contract_name,
   master_source_system, master_source_record_key, master_source_version,
   authority_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200301, 1, 'DPTECH-DEMO', '迪普科技示例公司', 'CSCOPE-QA-20261012',
  '合同多订单范围同步验收样例（非真实商业合同）', 'ERP', 'seed:contract-scope-20261012:contract', '1',
  'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_contract WHERE id = 993101200301
  AND master_source_record_key = 'seed:contract-scope-20261012:contract');

INSERT INTO com_sales_order
  (id, tenant_id, source_system, source_record_key, source_version, company_code, company_name,
   order_type, order_no, contract_no, source_project_name, order_comment, order_create_time,
   authority_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200302, 1, 'ERP', 'seed:contract-scope-20261012:order-a', '1', 'DPTECH-DEMO', '迪普科技示例公司',
  '0', 'CSCOPE-QA-20261012-A', 'CSCOPE-QA-20261012', '合同多订单主子项目验收 20261012',
  '合同范围同步隔离样例，非真实商业订单', '2026-10-12 00:00:00',
  'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_sales_order WHERE id = 993101200302
  AND source_record_key = 'seed:contract-scope-20261012:order-a');

INSERT INTO com_sales_order
  (id, tenant_id, source_system, source_record_key, source_version, company_code, company_name,
   order_type, order_no, contract_no, source_project_name, order_comment, order_create_time,
   authority_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200303, 1, 'ERP', 'seed:contract-scope-20261012:order-b', '1', 'DPTECH-DEMO', '迪普科技示例公司',
  '0', 'CSCOPE-QA-20261012-B', 'CSCOPE-QA-20261012', '合同多订单主子项目验收 20261012',
  '同合同第二张订单；相同产品使用独立订单行，非真实商业订单', '2026-10-12 00:01:00',
  'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_sales_order WHERE id = 993101200303
  AND source_record_key = 'seed:contract-scope-20261012:order-b');

INSERT INTO com_sales_order_line
  (id, tenant_id, order_id, source_system, source_record_key, source_version, company_code, company_name,
   order_type, order_no, line_no, product_code, product_desc, order_qty, open_qty, delivered_qty,
   unit_code, unit_scale, quantity_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200311, 1, 993101200302, 'ERP', 'seed:contract-scope-20261012:line-a1', '1', 'DPTECH-DEMO', '迪普科技示例公司',
  '0', 'CSCOPE-QA-20261012-A', '10', 'AR6280', '第一订单路由器范围验收样例', 10, 10, 0,
  'SET', 0, 'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_sales_order_line WHERE id = 993101200311
  AND source_record_key = 'seed:contract-scope-20261012:line-a1');

INSERT INTO com_sales_order_line
  (id, tenant_id, order_id, source_system, source_record_key, source_version, company_code, company_name,
   order_type, order_no, line_no, product_code, product_desc, order_qty, open_qty, delivered_qty,
   unit_code, unit_scale, quantity_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200312, 1, 993101200303, 'ERP', 'seed:contract-scope-20261012:line-b1', '1', 'DPTECH-DEMO', '迪普科技示例公司',
  '0', 'CSCOPE-QA-20261012-B', '10', 'AR6280', '第二订单同型号独立行验收样例', 6, 6, 0,
  'SET', 0, 'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_sales_order_line WHERE id = 993101200312
  AND source_record_key = 'seed:contract-scope-20261012:line-b1');

INSERT INTO com_sales_order_line
  (id, tenant_id, order_id, source_system, source_record_key, source_version, company_code, company_name,
   order_type, order_no, line_no, product_code, product_desc, order_qty, open_qty, delivered_qty,
   unit_code, unit_scale, quantity_status, source_lifecycle_status, status, creator, updater)
SELECT 993101200313, 1, 993101200303, 'ERP', 'seed:contract-scope-20261012:line-b2', '1', 'DPTECH-DEMO', '迪普科技示例公司',
  '0', 'CSCOPE-QA-20261012-B', '20', 'AR6280', '第二订单另一独立行验收样例', 4, 4, 0,
  'SET', 0, 'CONFIRMED', 'ACTIVE', 'ENABLED', 'project_contract_scope_qa', 'project_contract_scope_qa'
WHERE NOT EXISTS (SELECT 1 FROM com_sales_order_line WHERE id = 993101200313
  AND source_record_key = 'seed:contract-scope-20261012:line-b2');
