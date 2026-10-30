-- Forward repair for environments baselined after V398 without its material CAS column.
-- Preserve material identity, business revision, file version and all existing evidence.
DROP PROCEDURE IF EXISTS migrate_v519_delivery_material_version;
DELIMITER $$
CREATE PROCEDURE migrate_v519_delivery_material_version()
BEGIN
    DECLARE v_columns INT DEFAULT 0;
    DECLARE v_compatible INT DEFAULT 0;
    SELECT COUNT(*), COALESCE(SUM(DATA_TYPE = 'bigint' AND IS_NULLABLE = 'NO'
        AND COLUMN_DEFAULT = '0'), 0)
      INTO v_columns, v_compatible
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plt_delivery_material' AND COLUMN_NAME = 'version';
    IF v_columns = 0 THEN
        ALTER TABLE plt_delivery_material ADD COLUMN version BIGINT NOT NULL DEFAULT 0
            COMMENT '公共材料操作版本；独立于文件版本及业务修订';
    ELSEIF v_compatible <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V519 delivery material version column is incompatible';
    END IF;
END$$
DELIMITER ;
CALL migrate_v519_delivery_material_version();
DROP PROCEDURE migrate_v519_delivery_material_version;
