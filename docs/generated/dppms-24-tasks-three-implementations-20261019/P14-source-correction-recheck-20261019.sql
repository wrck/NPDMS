-- P14 source correction recheck: seven executed read-only SELECT statements.
-- Source business data: two adjudicated already-split tables only.
-- T01: dedicated npdms_reverify_20261014 metadata. No sync, DML or DDL.

-- S01_source_correction_check
SELECT COUNT(*) source_rows, SUM(orderNumber IS NULL OR TRIM(orderNumber)='') missing_order_number_rows FROM dppms.pm_order_line_from_erp;

-- S02_source_correction_check
SELECT id,source,compCode,lineType,orderNumber,lineNum,realOrderExecNumber,itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,syncTime FROM dppms.pm_order_line_from_erp WHERE id IN (29102,29103,47615,92527,100698,100699,100700,100701,101146,106713,109591,109592,109593,109594,109595,109596,109597,109598,109599,109600,109790,111179,111180,111181,111182,111183,114045,114046,114047,114048,114049,114050,114051,114052,114053,114054,115283,115284,115285,129698) ORDER BY id;

-- S03_S01_current_order_heads
SELECT `id`,`source`,`compCode`,`orderType`,`orderNumber`,`contractNo`,`orderExecNumber`,`orderCreateTime`,`customerRequireTime`,`customerCode`,`customerName`,`projectName`,`orderComment`,`salesType`,`syncTime` FROM dppms.pm_order_data_from_erp ORDER BY id;

-- S04_S02_current_duplicate_line_keys
SELECT source,compCode,lineType,orderNumber,lineNum,COUNT(*) original_rows,
 COUNT(DISTINCT CAST(JSON_ARRAY(itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,realOrderExecNumber) AS BINARY)) payload_variants
 FROM dppms.pm_order_line_from_erp
 GROUP BY source,compCode,lineType,orderNumber,lineNum,CAST(source AS BINARY),CAST(compCode AS BINARY),CAST(orderNumber AS BINARY),CAST(lineNum AS BINARY)
 HAVING COUNT(*)>1 ORDER BY source,compCode,lineType,orderNumber,lineNum;

-- S05_S03_current_order_lines
SELECT `id`,`source`,`compCode`,`lineType`,`orderNumber`,`lineNum`,`realOrderExecNumber`,`itemCode`,`itemDesc`,`orderQuantity`,`openQuantity`,`bundleCode`,`warrantyMonth`,`profitCenter`,`syncTime` FROM dppms.pm_order_line_from_erp ORDER BY id;

-- S06_same_number_head_originals
SELECT id,source,compCode,orderType,orderNumber,orderExecNumber,contractNo FROM dppms.pm_order_data_from_erp WHERE id IN (7642,12117,23022,25189,25190,25311,26761,27489,27535,27891,28607,28910,32230,44008,44062,44063,44064,44197,44198,44199,44200,44262,44263,44289,44291,44398,44402,44409,44410,44461,44462,44463,44464,44478,44481,44602,44603,44634,44639,44640,44652,44656,44657,44900,44901,45059,45077,45078,45083,45084,45120,45121,45122,45123,45124,45125,45361,45362,45396,45397,45398,45469,45545,45546,45561,45574,45575,45718,45910,45911,45912,45913,45914,46123,46124,46125,46126,46425,46715,46716,46717,46718,46719,46720,46721,46722,46778,46779,46780,46781,46805,46806,47367,47472,47879,47880,47881,47882,47883,47884,47885,84877) ORDER BY id;

-- T01_formal_checks
SELECT tc.TABLE_NAME,tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA='npdms_reverify_20261014' AND tc.TABLE_NAME IN ('com_sales_order','com_sales_order_line') AND tc.CONSTRAINT_TYPE='CHECK' ORDER BY tc.TABLE_NAME,tc.CONSTRAINT_NAME;
