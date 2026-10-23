-- 来源：仅dppms两张已拆分表；SELECT只读，不回查借货来源、不修正数据。
-- 实际核查使用下列两个单表投影各读一次，在内存按完整键分类，避免逐行回查。
SELECT id,source AS source_system,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE compCode END AS company_code,CAST(orderType AS CHAR) AS order_type,orderNumber AS order_no,orderExecNumber AS execution_no,contractNo AS contract_no FROM pm_order_data_from_erp;

SELECT id,source AS source_system,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE compCode END AS company_code,CAST(lineType AS CHAR) AS line_type,orderNumber AS order_no,CAST(lineNum AS CHAR) AS line_no,realOrderExecNumber AS execution_no,orderQuantity AS order_qty,openQuantity AS open_qty FROM pm_order_line_from_erp;

-- 逐行精确父头缺项诊断：lineType必须参与匹配，不允许同号跨类型。
SELECT l.id, l.source, l.compCode, l.lineType, l.orderNumber, l.lineNum,
       l.realOrderExecNumber, l.orderQuantity, l.openQuantity
FROM pm_order_line_from_erp l
LEFT JOIN pm_order_data_from_erp h
 ON BINARY h.source=BINARY l.source
 AND BINARY (CASE h.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE h.compCode END)
   =BINARY (CASE l.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE l.compCode END)
 AND h.orderType=l.lineType
 AND BINARY h.orderNumber=BINARY l.orderNumber
WHERE h.id IS NULL
ORDER BY l.id;

-- 原始示例：同号不代表同一类型订单。只查看，不能拿下列头给类型1的行补父。
SELECT id,source,compCode,orderType,orderNumber,orderExecNumber,contractNo
FROM pm_order_data_from_erp WHERE id=12117;
SELECT id,source,compCode,lineType,orderNumber,lineNum,realOrderExecNumber,orderQuantity,openQuantity
FROM pm_order_line_from_erp WHERE id=47373;

-- 目标库npdms中的正式父头匹配示例；实际Mapper查询后还执行独立绑定/指纹保护。
SELECT id,tenant_id,source_system,company_code,order_type,order_no,deleted
FROM com_sales_order
WHERE tenant_id=1 AND BINARY source_system=BINARY 'D365'
 AND BINARY company_code=BINARY '001' AND order_type='1'
 AND BINARY order_no=BINARY 'SO079848';

-- 正式承载限制核验：只读。order_id可空；order_type必填且语义为父订单类型。
SHOW CREATE TABLE com_sales_order_line;
