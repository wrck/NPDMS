# P14 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectOrder | SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.orderType,h.orderNumber) AS CHAR),256) | source_key |
| selectOrder | CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'<br> WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'<br> WHEN h.orderType NOT IN ('0','1') OR h.orderType IS NULL THEN 'ORDER_TYPE_UNMAPPED'<br> WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'<br> WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'<br> WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'<br> WHEN h.customerCode IS NOT NULL AND h.customerCode<>'' AND (NOT(h.customerCode REGEXP '^[A-Za-z]') OR CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))>5) THEN 'ERP_CUSTOMER_CODE_FORMAT_INVALID' ELSE NULL END | issue |
| selectOrder | CASE h.`source` WHEN 'SAP' THEN 'SAP' WHEN 'D365' THEN 'D365' WHEN 'SMS' THEN 'SMS' END | source_system |
| selectOrder | h.`company_code` | company_code |
| selectOrder | h.`orderNumber` | order_no |
| selectOrder | CONCAT('DPPMS\|',h.source,'\|',h.company_code,'\|',h.orderType,'\|',h.orderNumber) | source_record_key |
| selectOrder | DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') | source_version |
| selectOrder | h.`migrationVersion` | source_updated_at |
| selectOrder | NOW(3) | source_sync_time |
| selectOrder | h.`customInfo` | custom_info |
| selectOrder | h.`orderType` | order_type |
| selectOrder | h.`salesType` | sales_type |
| selectOrder | h.`contractNo` | contract_no |
| selectOrder | h.`orderExecNumber` | execution_no |
| selectOrder | CASE WHEN h.customerCode IS NULL OR h.customerCode='' THEN h.customerCode<br> WHEN h.customerCode REGEXP '^[A-Za-z]' AND CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))<=5<br> THEN CONCAT(LEFT(h.customerCode,1),LPAD(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)),5,'0')) ELSE NULL END | customer_code |
| selectOrder | h.`customerName` | customer_name |
| selectOrder | h.`projectName` | source_project_name |
| selectOrder | h.`orderComment` | order_comment |
| selectOrder | h.`orderCreateTime` | order_create_time |
| selectOrder | h.`customerRequireTime` | customer_required_time |
| selectOrder | CASE h.`orderType` WHEN '0' THEN 'ACTIVE' WHEN '1' THEN 'RETURNED' END | source_lifecycle_status |
| selectOrder | CASE h.`orderType` WHEN '0' THEN 'ENABLED' WHEN '1' THEN 'DISABLED' END | status |
| selectLine | SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.lineType,h.orderNumber,h.lineNum) AS CHAR),256) | source_key |
| selectLine | CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'<br> WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'<br> WHEN h.lineType NOT IN ('0','1') OR h.lineType IS NULL THEN 'ORDER_TYPE_UNMAPPED'<br> WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'<br> WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'<br> WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'<br> WHEN h.lineNum IS NULL OR TRIM(h.lineNum)='' THEN 'BUSINESS_KEY_MISSING'<br> WHEN h.lineType='0' AND (h.orderQuantity<0 OR h.openQuantity<0 OR h.orderQuantity-h.openQuantity<0) THEN 'ACTIVE_QUANTITY_NEGATIVE'<br> WHEN h.warrantyMonth<0 THEN 'WARRANTY_MONTH_NEGATIVE' ELSE NULL END | issue |
| selectLine | CASE h.`source` WHEN 'SAP' THEN 'SAP' WHEN 'D365' THEN 'D365' WHEN 'SMS' THEN 'SMS' END | source_system |
| selectLine | h.`company_code` | company_code |
| selectLine | h.`orderNumber` | order_no |
| selectLine | CONCAT('DPPMS\|',h.source,'\|',h.company_code,'\|',h.lineType,'\|',h.orderNumber,'\|',h.lineNum) | source_record_key |
| selectLine | DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') | source_version |
| selectLine | h.`migrationVersion` | source_updated_at |
| selectLine | NOW(3) | source_sync_time |
| selectLine | h.`customInfo` | custom_info |
| selectLine | h.`lineType` | order_type |
| selectLine | h.`lineType` | line_type |
| selectLine | h.`lineNum` | line_no |
| selectLine | h.`itemCode` | product_code |
| selectLine | h.`itemDesc` | product_desc |
| selectLine | h.`bundleCode` | bundle_code |
| selectLine | h.`profitCenter` | profit_center |
| selectLine | h.`realOrderExecNumber` | real_execution_no |
| selectLine | h.`warrantyMonth` | warranty_month |
| selectLine | h.`orderQuantity` | order_qty |
| selectLine | h.`openQuantity` | open_qty |
| selectLine | (h.orderQuantity-h.openQuantity) | delivered_qty |
| selectLine | CASE h.`lineType` WHEN '0' THEN 'ACTIVE' WHEN '1' THEN 'RETURNED' END | source_lifecycle_status |
| selectLine | CASE h.`lineType` WHEN '0' THEN 'ENABLED' WHEN '1' THEN 'DISABLED' END | status |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| com_sales_order.id | #{row.order.id} | insertOrder |
| com_sales_order.tenant_id | #{row.order.tenantId} | insertOrder |
| com_sales_order.source_system | #{row.order.sourceSystem} | insertOrder |
| com_sales_order.source_record_key | #{row.order.sourceRecordKey} | insertOrder |
| com_sales_order.source_version | #{row.order.sourceVersion} | insertOrder |
| com_sales_order.company_id | #{row.order.companyId} | insertOrder |
| com_sales_order.company_code | #{row.order.companyCode} | insertOrder |
| com_sales_order.company_name | #{row.order.companyName} | insertOrder |
| com_sales_order.order_type | #{row.order.orderType} | insertOrder |
| com_sales_order.order_no | #{row.order.orderNo} | insertOrder |
| com_sales_order.sales_type | #{row.order.salesType} | insertOrder |
| com_sales_order.contract_no | #{row.order.contractNo} | insertOrder |
| com_sales_order.execution_no | #{row.order.executionNo} | insertOrder |
| com_sales_order.customer_id | #{row.order.customerId} | insertOrder |
| com_sales_order.customer_code | #{row.order.customerCode} | insertOrder |
| com_sales_order.customer_name | #{row.order.customerName} | insertOrder |
| com_sales_order.source_project_name | #{row.order.sourceProjectName} | insertOrder |
| com_sales_order.order_comment | #{row.order.orderComment} | insertOrder |
| com_sales_order.order_amount | #{row.order.orderAmount} | insertOrder |
| com_sales_order.currency_code | #{row.order.currencyCode} | insertOrder |
| com_sales_order.authority_status | #{row.order.authorityStatus} | insertOrder |
| com_sales_order.source_lifecycle_status | #{row.order.sourceLifecycleStatus} | insertOrder |
| com_sales_order.order_create_time | #{row.order.orderCreateTime} | insertOrder |
| com_sales_order.customer_required_time | #{row.order.customerRequiredTime} | insertOrder |
| com_sales_order.custom_info | #{row.order.customInfo} | insertOrder |
| com_sales_order.source_sync_time | #{row.order.sourceSyncTime} | insertOrder |
| com_sales_order.source_updated_at | #{row.order.sourceUpdatedAt} | insertOrder |
| com_sales_order.status | #{row.order.status} | insertOrder |
| com_sales_order.version | #{row.order.version} | insertOrder |
| com_sales_order.creator | #{row.order.creator} | insertOrder |
| com_sales_order.create_time | #{row.order.createTime} | insertOrder |
| com_sales_order.updater | #{row.order.updater} | insertOrder |
| com_sales_order.update_time | #{row.order.updateTime} | insertOrder |
| com_sales_order.deleted | #{row.order.deleted} | insertOrder |
| com_sales_order_line.id | #{row.line.id} | insertLine |
| com_sales_order_line.tenant_id | #{row.line.tenantId} | insertLine |
| com_sales_order_line.order_id | #{row.line.orderId} | insertLine |
| com_sales_order_line.source_system | #{row.line.sourceSystem} | insertLine |
| com_sales_order_line.source_record_key | #{row.line.sourceRecordKey} | insertLine |
| com_sales_order_line.source_version | #{row.line.sourceVersion} | insertLine |
| com_sales_order_line.company_id | #{row.line.companyId} | insertLine |
| com_sales_order_line.company_code | #{row.line.companyCode} | insertLine |
| com_sales_order_line.company_name | #{row.line.companyName} | insertLine |
| com_sales_order_line.order_type | #{row.line.orderType} | insertLine |
| com_sales_order_line.order_no | #{row.line.orderNo} | insertLine |
| com_sales_order_line.line_no | #{row.line.lineNo} | insertLine |
| com_sales_order_line.line_type | #{row.line.lineType} | insertLine |
| com_sales_order_line.product_code | #{row.line.productCode} | insertLine |
| com_sales_order_line.product_model | #{row.line.productModel} | insertLine |
| com_sales_order_line.product_desc | #{row.line.productDesc} | insertLine |
| com_sales_order_line.custom_info | #{row.line.customInfo} | insertLine |
| com_sales_order_line.product_id | #{row.line.productId} | insertLine |
| com_sales_order_line.bundle_code | #{row.line.bundleCode} | insertLine |
| com_sales_order_line.profit_center | #{row.line.profitCenter} | insertLine |
| com_sales_order_line.real_execution_no | #{row.line.realExecutionNo} | insertLine |
| com_sales_order_line.warranty_month | #{row.line.warrantyMonth} | insertLine |
| com_sales_order_line.customer_id | #{row.line.customerId} | insertLine |
| com_sales_order_line.customer_code | #{row.line.customerCode} | insertLine |
| com_sales_order_line.customer_name | #{row.line.customerName} | insertLine |
| com_sales_order_line.order_qty | #{row.line.orderQty} | insertLine |
| com_sales_order_line.open_qty | #{row.line.openQty} | insertLine |
| com_sales_order_line.delivered_qty | #{row.line.deliveredQty} | insertLine |
| com_sales_order_line.unit_code | #{row.line.unitCode} | insertLine |
| com_sales_order_line.unit_scale | #{row.line.unitScale} | insertLine |
| com_sales_order_line.quantity_status | #{row.line.quantityStatus} | insertLine |
| com_sales_order_line.source_lifecycle_status | #{row.line.sourceLifecycleStatus} | insertLine |
| com_sales_order_line.source_sync_time | #{row.line.sourceSyncTime} | insertLine |
| com_sales_order_line.source_updated_at | #{row.line.sourceUpdatedAt} | insertLine |
| com_sales_order_line.status | #{row.line.status} | insertLine |
| com_sales_order_line.version | #{row.line.version} | insertLine |
| com_sales_order_line.creator | #{row.line.creator} | insertLine |
| com_sales_order_line.create_time | #{row.line.createTime} | insertLine |
| com_sales_order_line.updater | #{row.line.updater} | insertLine |
| com_sales_order_line.update_time | #{row.line.updateTime} | insertLine |
| com_sales_order_line.deleted | #{row.line.deleted} | insertLine |
