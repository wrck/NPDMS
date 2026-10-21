-- Read-only P14 review. S01-S08: dppms source, two already-split tables only.
-- T01-T05: dedicated npdms_reverify_20261014. No DML, DDL, migration, sync or preview.
-- Company mapping in product identity: 01/02/03/04 => 001/002/003/004.
-- User adjudicated MAX(orderExecNumber), not MAX(orderNumber), for header execution.

-- S01_source_row_counts
SELECT 'pm_order_data_from_erp' table_name,COUNT(*) original_rows FROM dppms.pm_order_data_from_erp
UNION ALL SELECT 'pm_order_line_from_erp',COUNT(*) FROM dppms.pm_order_line_from_erp;

-- S02_header_duplicate_keys_and_approved_max_execution
SELECT source,compCode,orderType,orderNumber,COUNT(*) original_rows,
 MAX(orderExecNumber) selected_execution_no,
 COUNT(DISTINCT CAST(JSON_ARRAY(orderExecNumber) AS BINARY)) execution_variants,
 COUNT(DISTINCT CAST(JSON_ARRAY(orderCreateTime,customerRequireTime,customerCode,customerName,projectName,orderComment,salesType,contractNo,syncTime) AS BINARY)) other_payload_variants,
 GROUP_CONCAT(id ORDER BY id) original_source_ids
FROM dppms.pm_order_data_from_erp
GROUP BY source,compCode,orderType,orderNumber,CAST(source AS BINARY),CAST(orderNumber AS BINARY)
HAVING COUNT(*)>1 ORDER BY source,compCode,orderType,orderNumber;

-- S03_concrete_original_headers
SELECT id,source,compCode,orderType,orderNumber,contractNo,orderExecNumber,
 orderCreateTime,customerRequireTime,customerCode,customerName,projectName,orderComment,salesType,syncTime
FROM dppms.pm_order_data_from_erp
WHERE id IN (9134,9135,9386,9387,9388,9555,9556,9557,9558,44008) ORDER BY id;

-- S04_line_duplicate_keys
SELECT source,compCode,lineType,orderNumber,lineNum,COUNT(*) original_rows,
 COUNT(DISTINCT CAST(JSON_ARRAY(itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,realOrderExecNumber,syncTime) AS BINARY)) core_payload_variants,
 GROUP_CONCAT(id ORDER BY id) original_source_ids
FROM dppms.pm_order_line_from_erp
GROUP BY source,compCode,lineType,orderNumber,lineNum,CAST(source AS BINARY),CAST(orderNumber AS BINARY),CAST(lineNum AS BINARY)
HAVING COUNT(*)>1 ORDER BY source,compCode,lineType,orderNumber,lineNum;

-- S05_all_missing_order_number_lines
SELECT id,source,compCode,lineType,orderNumber,lineNum,realOrderExecNumber,
 itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,syncTime
FROM dppms.pm_order_line_from_erp
WHERE orderNumber IS NULL OR TRIM(orderNumber)='' ORDER BY id;

-- S06_concrete_original_lines
SELECT id,source,compCode,lineType,orderNumber,lineNum,realOrderExecNumber,
 itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,syncTime
FROM dppms.pm_order_line_from_erp
WHERE id IN (29102,29103,35158,35159,37034,37035,257796,257797,367456) ORDER BY id;

-- S07_null_or_empty_execution_headers
SELECT id,source,compCode,orderType,orderNumber,orderExecNumber,contractNo,syncTime
FROM dppms.pm_order_data_from_erp WHERE orderExecNumber IS NULL OR orderExecNumber='' ORDER BY id;

-- S08_same_order_number_different_type_example
SELECT l.id line_source_id,l.source,l.compCode,l.lineType,l.orderNumber,l.lineNum,l.realOrderExecNumber,
 h.id head_source_id,h.orderType head_order_type,h.orderExecNumber head_execution_no
FROM dppms.pm_order_line_from_erp l
LEFT JOIN dppms.pm_order_data_from_erp h
 ON h.source=l.source AND h.compCode=l.compCode AND h.orderNumber=l.orderNumber
WHERE l.id=367456 ORDER BY h.id;

-- T01_formal_unique_keys
SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) index_columns
FROM information_schema.statistics WHERE TABLE_SCHEMA='npdms_reverify_20261014'
AND TABLE_NAME IN ('com_sales_order','com_sales_order_line') AND NON_UNIQUE=0
GROUP BY TABLE_NAME,INDEX_NAME,NON_UNIQUE ORDER BY TABLE_NAME,INDEX_NAME;

-- T02_formal_key_columns
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLLATION_NAME,COLUMN_COMMENT
FROM information_schema.columns WHERE TABLE_SCHEMA='npdms_reverify_20261014'
AND TABLE_NAME IN ('com_sales_order','com_sales_order_line')
AND COLUMN_NAME IN ('tenant_id','id','source_system','source_record_key','company_code','order_type','order_no','execution_no','order_id','line_no','real_execution_no')
ORDER BY TABLE_NAME,ORDINAL_POSITION;

-- T03_formal_existing_orders
SELECT id,tenant_id,source_system,source_record_key,company_code,order_type,order_no,execution_no,source_updated_at,status,deleted
FROM npdms_reverify_20261014.com_sales_order ORDER BY id;

-- T04_formal_existing_lines
SELECT id,tenant_id,order_id,source_system,source_record_key,company_code,order_type,order_no,line_no,real_execution_no,product_code,source_updated_at,deleted
FROM npdms_reverify_20261014.com_sales_order_line ORDER BY id;

-- T05_formal_foreign_keys
SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA='npdms_reverify_20261014' AND REFERENCED_TABLE_NAME IS NOT NULL
AND (TABLE_NAME IN ('com_sales_order','com_sales_order_line') OR REFERENCED_TABLE_NAME IN ('com_sales_order','com_sales_order_line'))
ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION;
