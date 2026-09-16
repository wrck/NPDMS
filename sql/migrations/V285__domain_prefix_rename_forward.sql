-- 前向纠正迁移：低代码/集成/工作流表按业务领域前缀重命名（low_*/int_*/wf_*）
-- 前向幂等：仅当旧表存在且新表不存在时重命名；不删除数据，不破坏历史
DROP TABLE IF EXISTS `pms_domain_prefix_pairs`;
CREATE TABLE `pms_domain_prefix_pairs` (
    `old_table` VARCHAR(128) NOT NULL,
    `new_table` VARCHAR(128) NOT NULL,
    PRIMARY KEY (`old_table`)
) ENGINE=InnoDB;
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_approval_chain', 'low_approval_chain');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_backup_record', 'low_backup_record');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_collaboration_session', 'low_collaboration_session');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_comment', 'low_comment');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_component_meta', 'low_component_meta');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_config_audit_log', 'low_config_audit_log');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_config_template', 'low_config_template');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_config_version', 'low_config_version');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_connector', 'low_connector');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_datasource', 'low_datasource');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_ddl_backup', 'low_ddl_backup');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_ddl_execution_log', 'low_ddl_execution_log');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_edit_lock', 'low_edit_lock');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_entity', 'low_entity');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_field', 'low_field');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_form', 'low_form');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_gray_release', 'low_gray_release');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_import_task', 'low_import_task');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_list', 'low_list');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_microflow', 'low_microflow');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_microflow_execution_log', 'low_microflow_execution_log');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_microflow_version', 'low_microflow_version');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_process_binding', 'low_process_binding');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_process_sla_record', 'low_process_sla_record');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_publish_record', 'low_publish_record');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_related_page', 'low_related_page');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_relation', 'low_relation');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_rule', 'low_rule');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_rule_test_case', 'low_rule_test_case');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_tab', 'low_tab');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_trigger', 'low_trigger');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_lowcode_trigger_execution_log', 'low_trigger_execution_log');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_sync_connection', 'int_sync_connection');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_sync_task', 'int_sync_task');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_sync_run', 'int_sync_run');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_sync_binding', 'int_sync_binding');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_INSTANCE', 'int_batch_JOB_INSTANCE');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_EXECUTION', 'int_batch_JOB_EXECUTION');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_EXECUTION_PARAMS', 'int_batch_JOB_EXECUTION_PARAMS');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_EXECUTION_CONTEXT', 'int_batch_JOB_EXECUTION_CONTEXT');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_INSTANCE_SEQ', 'int_batch_JOB_INSTANCE_SEQ');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_JOB_EXECUTION_SEQ', 'int_batch_JOB_EXECUTION_SEQ');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_STEP_EXECUTION', 'int_batch_STEP_EXECUTION');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_STEP_EXECUTION_CONTEXT', 'int_batch_STEP_EXECUTION_CONTEXT');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_int_batch_STEP_EXECUTION_SEQ', 'int_batch_STEP_EXECUTION_SEQ');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_integration_log', 'int_log');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_d365_purchase_receipt', 'int_d365_purchase_receipt');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_d365_invoice', 'int_d365_invoice');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_approval_record', 'wf_approval_record');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_approval_node', 'wf_approval_node');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_approval_history', 'wf_approval_history');
INSERT INTO `pms_domain_prefix_pairs` (`old_table`, `new_table`) VALUES ('pms_approval_field_permission', 'wf_approval_field_permission');

DELIMITER $$

DROP PROCEDURE IF EXISTS `pms_domain_prefix_rename` $$
CREATE PROCEDURE `pms_domain_prefix_rename` ()
BEGIN
    DECLARE old_name VARCHAR(128);
    DECLARE new_name VARCHAR(128);
    DECLARE done INT DEFAULT 0;
    DECLARE cur CURSOR FOR SELECT old_table, new_table FROM pms_domain_prefix_pairs;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    rename_loop: LOOP
        FETCH cur INTO old_name, new_name;
        IF done = 1 THEN
            LEAVE rename_loop;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = old_name) THEN
            IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = new_name) THEN
                SET @ddl = CONCAT('RENAME TABLE `', old_name, '` TO `', new_name, '`');
                PREPARE stmt FROM @ddl;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            END IF;
        END IF;
    END LOOP;
    CLOSE cur;
END $$

DELIMITER ;

CALL `pms_domain_prefix_rename`();
DROP PROCEDURE IF EXISTS `pms_domain_prefix_rename`;
DROP TABLE IF EXISTS `pms_domain_prefix_pairs`;
