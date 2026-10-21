-- P14-1 decision evidence: READ ONLY; no DDL/DML, procedure call, migration or sync.
-- Run on the existing MySQL 5.7 dppms source connection.

-- S01_source_objects
SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT
FROM information_schema.tables
WHERE TABLE_SCHEMA = 'dppms'
  AND TABLE_NAME IN ('pm_order_data_from_erp_source',
      'pm_order_data_from_sap_source', 'pm_project_soleagent_lend_from_sms',
      'pm_order_data_from_erp', 'pm_order_line_from_erp')
ORDER BY TABLE_NAME;

-- S02_upstream_original_heads
SELECT id, source, compCode, orderType, orderNumber, contractNo,
       orderExecNumber, orderExecNumberShort, customerCode, customerName, salesType
FROM dppms.pm_order_data_from_erp_source
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075423')
ORDER BY orderNumber, id;

-- S03_original_lend_records
SELECT id, soleAgentLendId, `contract`, orderCodes,
       orderExecNumber, orderExecNumberShort, profitCenter, dataSource
FROM dppms.pm_project_soleagent_lend_from_sms
WHERE `contract` IN ('16202523112915N', '16200523112415N', '16100023121116N')
ORDER BY `contract`, id;

-- S04_processed_order_heads
SELECT id, source, compCode, orderType, orderNumber, contractNo,
       orderExecNumber, customerCode, customerName, salesType, syncTime
FROM dppms.pm_order_data_from_erp
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075153-L',
                      'SO075423', 'SO075423-L')
ORDER BY orderNumber, id;

-- S05_original_routine_trace_examples
SELECT a.id AS erp_source_id, a.orderNumber AS erp_order_no,
       a.contractNo AS erp_contract_no,
       a.orderExecNumber AS erp_execution_no,
       a.orderExecNumberShort AS erp_execution_short,
       b.id AS lend_row_id, b.soleAgentLendId, b.orderCodes,
       b.orderExecNumber AS lend_execution_no,
       b.orderExecNumberShort AS lend_execution_short,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN CONCAT(a.orderNumber, '-L') ELSE a.orderNumber END AS result_order_no,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN CONCAT(b.`contract`, '-L') ELSE a.contractNo END AS result_contract_no,
       CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
            THEN 'SMS' ELSE a.source END AS result_source,
       IFNULL(b.orderExecNumber, a.orderExecNumber) AS result_execution_no,
       IFNULL(b.orderExecNumberShort, a.orderExecNumberShort) AS result_execution_short
FROM dppms.pm_order_data_from_sap_source a
LEFT JOIN dppms.pm_project_soleagent_lend_from_sms b
       ON b.`contract` = a.contractNo
WHERE a.orderNumber IN ('SO074723', 'SO075153', 'SO075423')
  AND (b.id IS NULL
       OR (a.source = 'SAP' AND
           SUBSTR(b.orderCodes, 1, 6) <>
           SUBSTR(b.orderCodes, LOCATE(',', b.orderCodes) + 1, 6))
       OR (a.source <> 'SAP' AND b.id IS NOT NULL))
ORDER BY a.orderNumber, a.id, b.id;

-- S06_original_order_lines
SELECT id, source, compCode, lineType, orderNumber, lineNum,
       realOrderExecNumber, itemCode, orderQuantity, openQuantity
FROM dppms.pm_order_line_from_erp
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075153-L',
                      'SO075423', 'SO075423-L')
ORDER BY orderNumber, lineNum, id;

-- S07_line_execution_groups
SELECT source, compCode, lineType, orderNumber, realOrderExecNumber,
       COUNT(*) AS original_rows,
       COUNT(DISTINCT CAST(lineNum AS BINARY)) AS different_line_numbers
FROM dppms.pm_order_line_from_erp
WHERE orderNumber IN ('SO074723', 'SO075153', 'SO075153-L',
                      'SO075423', 'SO075423-L')
GROUP BY source, compCode, lineType, orderNumber, realOrderExecNumber,
         CAST(source AS BINARY), CAST(compCode AS BINARY),
         CAST(orderNumber AS BINARY), CAST(realOrderExecNumber AS BINARY)
ORDER BY orderNumber, realOrderExecNumber;

-- S08_all_multi_execution_orders
SELECT source, compCode, orderType, orderNumber,
       COUNT(*) AS original_rows,
       COUNT(DISTINCT CAST(orderExecNumber AS BINARY)) AS full_execution_count,
       GROUP_CONCAT(DISTINCT orderExecNumber ORDER BY orderExecNumber) AS full_execution_numbers,
       COUNT(DISTINCT CAST(JSON_ARRAY(orderCreateTime, customerRequireTime,
           customerCode, customerName, projectName, orderComment,
           salesType, customInfo, contractNo) AS BINARY)) AS non_execution_variants
FROM dppms.pm_order_data_from_erp
GROUP BY source, compCode, orderType, orderNumber,
         CAST(source AS BINARY), CAST(compCode AS BINARY), CAST(orderNumber AS BINARY)
HAVING COUNT(DISTINCT CAST(orderExecNumber AS BINARY)) > 1
ORDER BY source, orderNumber, orderType;

-- S09_profile_all_169_by_source_short
SELECT x.source, x.compCode, x.orderType, x.orderNumber,
       COUNT(*) AS original_rows,
       COUNT(DISTINCT CAST(x.orderExecNumber AS BINARY)) AS full_execution_count,
       COUNT(DISTINCT CAST(x.orderExecNumberShort AS BINARY)) AS short_execution_count,
       GROUP_CONCAT(DISTINCT x.orderExecNumber ORDER BY x.orderExecNumber) AS full_execution_numbers,
       GROUP_CONCAT(DISTINCT x.orderExecNumberShort ORDER BY x.orderExecNumberShort) AS short_execution_numbers
FROM (
    SELECT CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
                THEN 'SMS' ELSE a.source END AS source,
           a.compCode, a.orderType,
           CASE WHEN b.orderExecNumber IS NOT NULL AND b.orderExecNumberShort <> a.orderExecNumberShort
                THEN CONCAT(a.orderNumber, '-L') ELSE a.orderNumber END AS orderNumber,
           IFNULL(b.orderExecNumber, a.orderExecNumber) AS orderExecNumber,
           IFNULL(b.orderExecNumberShort, a.orderExecNumberShort) AS orderExecNumberShort
    FROM dppms.pm_order_data_from_sap_source a
    LEFT JOIN dppms.pm_project_soleagent_lend_from_sms b
           ON b.`contract` = a.contractNo
    WHERE a.id IN (9131,9222,9338,9339,9370,9514,9522,9535,9550,9566,9604,9666,9695,9696,9719,9772,9778,9808,9812,9860,9879,9905,9921,9922,9968,9974,9975,10040,10041,10049,10050,10097,10193,10201,10202,10203,10224,10225,10226,10227,10228,10229,10230,10231,10266,10540,10543,10890,10892,11418,11419,11420,11538,11539,11540,11543,11544,11651,11850,11851,11977,11978,11981,11990,11991,11992,11997,11998,11999,12000,12001,12005,12006,12007,12008,12009,12015,12016,13521,13522,13523,13524,13525,13526,13527,13528,13531,13532,13533,13534,13535,13536,13537,13538,13539,13540,13541,13542,13543,15784,15785,15791,15792,15808,15809,15810,15827,15828,16105,16106,16107,16108,16135,16136,16137,17023,17233,17245,17246,17265,17266,17268,17269,17270,17271,17272,17273,19739,19778,19789,19811,19872,19906,21104,21105,21106,21493,21494,21495,21496,21497,21498,21499,21500,21501,21509,21510,21511,21512,22010,22011,22015,22026,22027,22028,22029,22030,34791,34895,34896,62917,83723,86059,86060,88122)
      AND (b.id IS NULL
       OR (a.source = 'SAP' AND
           SUBSTR(b.orderCodes, 1, 6) <>
           SUBSTR(b.orderCodes, LOCATE(',', b.orderCodes) + 1, 6))
       OR (a.source <> 'SAP' AND b.id IS NOT NULL))
) x
WHERE x.orderNumber IN ('10446-L','10447-L','32905-L','SO002173','SO002443','SO002444','SO074723','SO074890','SO075102','SO075103','SO075153','SO075153-L','SO075413','SO075423-L','SO075439-L','SO075470-L','SO075486-L','SO075558','SO075681-L','SO075729-L','SO075730','SO075781-L','SO075852-L','SO075859-L','SO075949-L','SO075972-L','SO076026','SO076026-L','SO076054-L','SO076097-L','SO076114-L','SO076115','SO076226','SO076232-L','SO076234-L','SO076337-L','SO076338-L','SO076352-L','SO076353-L','SO076437-L','SO076622-L','SO076634','SO076635','SO076636','SO076659','SO076660','SO076662','SO076663','SO076664','SO076665','SO076666','SO076667','SO076807-L','SO077323-L','SO077326-L','SO078075-L','SO078077-L','SO079171-L','SO079172-L','SO079173-L','SO079370','SO079371','SO079372','SO079375-L','SO079376-L','SO079573','SO079573-L','SO079941-L','SO079942-L','SO080153','SO080154','SO080157-L','SO080166-L','SO080168-L','SO080169-L','SO080175','SO080176','SO080177','SO080178','SO080179','SO080183-L','SO080184-L','SO080185-L','SO080186-L','SO080187-L','SO080193-L','SO080194-L','SO083271-L','SO083272-L','SO083275-L','SO083276-L','SO083277-L','SO083278-L','SO083281-L','SO083282-L','SO083285-L','SO083286-L','SO083287-L','SO083288-L','SO083289-L','SO083290-L','SO083291-L','SO083292-L','SO083293-L','SO083294-L','SO083295-L','SO083296-L','SO083297-L','SO087506-L','SO087507-L','SO087514-L','SO087515-L','SO087531-L','SO087532-L','SO087533-L','SO087550-L','SO087551-L','SO088002-L','SO088003-L','SO088004-L','SO088005-L','SO088036-L','SO088037-L','SO088038-L','SO089407-L','SO089734','SO089734-L','SO089746-L','SO089747-L','SO089766-L','SO089767-L','SO089769-L','SO089770-L','SO089771-L','SO089772-L','SO089773-L','SO089775-L','SO094097-L','SO094162-L','SO094174-L','SO094207-L','SO094339-L','SO094388-L','SO096837-L','SO096838-L','SO096839-L','SO097580-L','SO097581-L','SO097582-L','SO097583-L','SO097584-L','SO097585-L','SO097586-L','SO097587-L','SO097588-L','SO097596-L','SO097597-L','SO097598-L','SO097599-L','SO098493-L','SO098494-L','SO098502-L','SO098535-L','SO098536-L','SO098537-L','SO098538-L','SO098539-L','Z108-L','Z462-L')
GROUP BY x.source, x.compCode, x.orderType, x.orderNumber,
         CAST(x.source AS BINARY), CAST(x.compCode AS BINARY), CAST(x.orderNumber AS BINARY)
HAVING COUNT(DISTINCT CAST(x.orderExecNumber AS BINARY)) > 1
ORDER BY short_execution_count, x.source, x.orderNumber, x.orderType;
