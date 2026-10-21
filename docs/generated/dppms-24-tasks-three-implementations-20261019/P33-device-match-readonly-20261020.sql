-- Read-only audit examples. Source dppms and target npdms_reverify_20261014 use separate existing connections.
-- Do not join across instances or execute synchronization, DDL, INSERT, UPDATE or DELETE.

-- Target: actual BASE_P33 preview lookup predicate. Production uses batches of <=500 distinct raw SNs.
SELECT id, sn
FROM `npdms_reverify_20261014`.`ast_device`
WHERE tenant_id = 1 AND deleted = FALSE
  AND sn IN ('02050055D147DC0010', '02050055D147DC0004', '02010009D147000102', '02010009D147000100', '02010005T144000019', '02010075Y14A004396 ', '01100107D184000007', '31020151111E');
-- Actual task uses AND 1=0 for an empty list. Preview has no FOR UPDATE.

-- Target diagnostic only; never adopt a deleted or other-tenant identity.
SELECT id,tenant_id,sn,CAST(deleted AS UNSIGNED) AS deleted
FROM `npdms_reverify_20261014`.`ast_device`
WHERE sn IN ('02050055D147DC0010', '02050055D147DC0004', '02010009D147000102', '02010009D147000100', '02010005T144000019', '02010075Y14A004396 ', '01100107D184000007', '31020151111E');

-- Source: original SN, exact bytes and source identity; no text trimming or data changes.
SELECT id AS source_id,projectId AS original_project_id,barcode AS source_sn,HEX(barcode) AS source_sn_hex
FROM `dppms`.`pm_project_shipment`
WHERE id IN (1, 2, 3, 4, 5, 44556, 44581, 44583, 44589, 44590, 55383, 55384, 55385, 55387, 55388, 55389, 55390, 56240, 60335, 67185, 67186, 67187, 67188, 67189, 67190, 67191, 67192, 67193, 67194, 67195, 67196, 67197, 68590, 68689, 72828, 139767, 208442, 488787, 1161000, 1161002) ORDER BY id;

-- P30's authoritative input is formal DPPMS shipments; this currently returns no rows.
SELECT MIN(device_sn) AS device_sn
FROM `npdms_reverify_20261014`.`ast_device_shipment`
WHERE tenant_id=1 AND deleted=FALSE AND source_system='DPPMS'
GROUP BY CAST(device_sn AS BINARY)
ORDER BY CAST(device_sn AS BINARY);

-- Source: the one internal-only SN has seven original shipment rows with seven primary SNs.
SELECT id,barcode AS primary_sn,barcode2 AS secondary_sn,com_barcode AS internal_sn
FROM `dppms`.`fb_shipment_barcode`
WHERE com_barcode='01100107D184000007' ORDER BY id;

-- Read-only scalar probe using a real source/candidate pair; creates no formal device row.
SELECT CONVERT('02010075Y14A004396 ' USING utf8mb4) COLLATE utf8mb4_unicode_ci =
       CONVERT('02010075Y14A004396' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS sql_equal,
       CAST('02010075Y14A004396 ' AS BINARY)=CAST('02010075Y14A004396' AS BINARY) AS raw_equal;
