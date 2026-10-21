-- P14 current adjudicated business sources: already split legacy order data.
-- These are direct row projections for source/key review, not a migration or write script.
-- No upstream ERP, lend lookup, stored procedure, resplitting, grouping or primary selection.
SELECT id, source, compCode, orderType, orderNumber,
       contractNo, orderExecNumber, syncTime
FROM dppms.pm_order_data_from_erp
ORDER BY id;

SELECT id, source, compCode, lineType, orderNumber, lineNum,
       realOrderExecNumber, itemCode, orderQuantity, openQuantity, syncTime
FROM dppms.pm_order_line_from_erp
ORDER BY id;
