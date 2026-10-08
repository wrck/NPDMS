-- Q-MIG-COLLATION-20260930-001: approved 2026-10-08.
-- Pre-V374 alignment for the existing utf8mb4_unicode_ci database baseline.
-- Only the non-binary delivery-code column involved in JSON_TABLE equality changes.
-- Preserve binary/Flowable columns, all historical scripts, data identities and history.
-- An existing installation past V374 needs its own audited forward lineage; do not
-- enable outOfOrder or repair history merely to apply this prerequisite retroactively.
DROP PROCEDURE IF EXISTS npdms_delivery_collation_v373_5;
DELIMITER $$
CREATE PROCEDURE npdms_delivery_collation_v373_5()
BEGIN
    DECLARE current_collation VARCHAR(64);
    DECLARE column_note TEXT;
    IF @@collation_database <> 'utf8mb4_unicode_ci' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V373.5 requires the approved utf8mb4_unicode_ci database baseline';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'acc_project_deliverable'
          AND column_name = 'deliverable_code' AND data_type = 'varchar'
          AND character_maximum_length = 64 AND character_set_name = 'utf8mb4'
          AND is_nullable = 'NO' AND column_default IS NULL AND extra = '') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V373.5 unexpected delivery code column definition';
    END IF;
    SELECT collation_name, column_comment INTO current_collation, column_note
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'acc_project_deliverable'
      AND column_name = 'deliverable_code';
    IF current_collation NOT IN ('utf8mb4_unicode_ci', 'utf8mb4_0900_ai_ci') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V373.5 refuses to change an unapproved or binary comparison column';
    END IF;
    IF current_collation <> 'utf8mb4_unicode_ci' THEN
        IF EXISTS (SELECT 1 FROM acc_project_deliverable
            GROUP BY tenant_id, project_id, deliverable_code COLLATE utf8mb4_unicode_ci
            HAVING COUNT(*) > 1) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V373.5 delivery code uniqueness conflicts under unicode_ci';
        END IF;
        SET @npdms_delivery_collation_sql = CONCAT(
            'ALTER TABLE acc_project_deliverable MODIFY COLUMN deliverable_code ',
            'VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ',
            QUOTE(column_note));
        PREPARE npdms_delivery_collation_statement FROM @npdms_delivery_collation_sql;
        EXECUTE npdms_delivery_collation_statement;
        DEALLOCATE PREPARE npdms_delivery_collation_statement;
    END IF;
END$$
DELIMITER ;
CALL npdms_delivery_collation_v373_5();
DROP PROCEDURE npdms_delivery_collation_v373_5;

-- Earlier SET NAMES statements also changed implicit CAST(... AS CHAR) collation.
-- Restore the approved database semantics for the immediately following V374.
SET SESSION collation_connection = @@collation_database;
