DROP PROCEDURE IF EXISTS `fins001_assert_zero_legacy_conversion`;
DELIMITER $$
CREATE PROCEDURE `fins001_assert_zero_legacy_conversion`()
BEGIN
    DECLARE formal_source_column_count int DEFAULT 0;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'pms_srv_rule'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-INS-001 requires the preserved pms_srv_rule source table';
    END IF;

    SELECT COUNT(*)
    INTO formal_source_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'pms_srv_rule'
      AND column_name IN (
          'category_code',
          'severity_code',
          'inspection_item',
          'expected_result_regex',
          'threshold_data_type',
          'threshold_operator',
          'threshold_value',
          'threshold_unit',
          'stable_command_key',
          'execution_order',
          'timeout_seconds',
          'continue_on_timeout',
          'product_type_code',
          'security_review_reference',
          'security_review_conclusion'
      );

    IF formal_source_column_count = 15 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-INS-001 legacy source contract changed; reassess forward migration';
    END IF;

    SELECT
        'INS-03 INS-09' AS requirement_ids,
        'eligible legacy rows = 0' AS eligible_legacy_rows,
        'target inserts = 0' AS target_inserts,
        'legacy updates = 0' AS legacy_updates;
END$$
DELIMITER ;
CALL `fins001_assert_zero_legacy_conversion`();
DROP PROCEDURE IF EXISTS `fins001_assert_zero_legacy_conversion`;
