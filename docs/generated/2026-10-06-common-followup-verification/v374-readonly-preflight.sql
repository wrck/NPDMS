START TRANSACTION READ ONLY;
SELECT VERSION() AS mysql_version, @@collation_server AS server_collation, @@collation_connection AS connection_collation, @@default_collation_for_utf8mb4 AS implicit_utf8mb4_collation;
SELECT TABLE_NAME,COLUMN_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='plt_delivery_material' AND COLUMN_NAME='business_object_id') OR (TABLE_NAME='acc_project_deliverable' AND COLUMN_NAME='deliverable_code'));
SELECT COLLATION(jt.code) AS json_table_code_collation,COERCIBILITY(jt.code) AS json_table_code_coercibility,COLLATION(CAST(jt.ref AS CHAR)) AS cast_collation,COERCIBILITY(CAST(jt.ref AS CHAR)) AS cast_coercibility FROM JSON_TABLE('[{"code":"probe","ref":1}]','$[*]' COLUMNS(code VARCHAR(64) PATH '$.code',ref BIGINT PATH '$.ref')) jt;
SELECT version,success FROM flyway_schema_history WHERE version IN ('373','374','398') ORDER BY installed_rank;
ROLLBACK;
