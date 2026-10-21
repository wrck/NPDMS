-- P14-1 decision evidence: READ ONLY; no DDL/DML, procedure call, migration or sync.
-- Run on 127.0.0.1:24306 / npdms_reverify_20261014 only.

-- T01_actual_formal_columns
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.columns
WHERE TABLE_SCHEMA = 'npdms_reverify_20261014'
  AND TABLE_NAME IN ('com_sales_order', 'com_sales_order_line', 'com_crm_execution_order')
  AND COLUMN_NAME IN ('id', 'tenant_id', 'source_system', 'source_record_key',
      'company_code', 'order_type', 'order_no', 'order_id', 'line_no',
      'execution_no', 'real_execution_no', 'primary_project_id')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- T02_actual_unique_keys
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS index_columns
FROM information_schema.statistics
WHERE TABLE_SCHEMA = 'npdms_reverify_20261014'
  AND TABLE_NAME IN ('com_sales_order', 'com_sales_order_line')
  AND NON_UNIQUE = 0
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

-- T03_carrier_presence
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.tables
WHERE TABLE_SCHEMA = 'npdms_reverify_20261014'
  AND TABLE_NAME IN ('com_sales_order', 'com_sales_order_line', 'com_crm_execution_order',
      'com_execution_order_merge_batch', 'com_execution_order_merge_member',
      'com_sales_order_execution_relation')
ORDER BY TABLE_NAME;

-- T04_sample_formal_orders
SELECT id, tenant_id, source_system, company_code, order_type,
       order_no, execution_no, status, source_record_key
FROM npdms_reverify_20261014.com_sales_order
WHERE tenant_id = 1
  AND order_no IN ('SO074723', 'SO075153', 'SO075153-L', 'SO075423', 'SO075423-L')
ORDER BY order_no, source_system, company_code, order_type, id;
