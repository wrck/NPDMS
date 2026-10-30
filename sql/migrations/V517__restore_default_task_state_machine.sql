-- F-PROJ-007: system default rules must be usable after initialization.
-- Forward repair of V502 only; retain its draft and every historical row.
-- Any existing publication (including a scheduled one) wins. Never publish custom drafts.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS migrate_v517_default_task_state;
DELIMITER $$
CREATE PROCEDURE migrate_v517_default_task_state()
migration_block: BEGIN
    DECLARE v_revision_no BIGINT;
    DECLARE v_now DATETIME(3);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS v517_expected_transition;
        RESIGNAL;
    END;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM proj_task_state_machine_revision
               WHERE tenant_id=1 AND deleted=0 AND status='PUBLISHED') THEN
        COMMIT;
        LEAVE migration_block;
    END IF;
    -- Environments without the V502 seed are outside this repair's scope.
    IF NOT EXISTS (SELECT 1 FROM proj_task_state_machine_revision
                   WHERE id=996610195020063 AND tenant_id=1 AND deleted=0
                     AND BINARY creator=BINARY 'migration-v502-59299-config') THEN
        COMMIT;
        LEAVE migration_block;
    END IF;
    IF (SELECT COUNT(*) FROM proj_task_state_machine_revision WHERE tenant_id=1 AND deleted=0) <> 1
       OR NOT EXISTS (SELECT 1 FROM proj_task_state_machine_revision
          WHERE id=996610195020063 AND tenant_id=1 AND status='DRAFT' AND revision_no=1
            AND version=0 AND published_by IS NULL AND published_at IS NULL
            AND effective_from='1970-01-01 08:00:00'
            AND BINARY updater=BINARY 'migration-v502-59299-config') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V517: default task state draft changed; preserve and resolve through native configuration';
    END IF;
    CREATE TEMPORARY TABLE v517_expected_transition AS
    SELECT 'PENDING_ASSIGN' AS `from_status_code`, 'ASSIGN' AS `action_code`,
           'PENDING_START' AS `to_status_code`,
           'CURRENT_PROJECT_MANAGER_OR_AUTHORIZED_SERVICE_MANAGER_FOR_CROSS_REGION' AS `allowed_role_code`,
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:assign') AS `entry_condition`,
           JSON_OBJECT('schemaVersion', 1, 'currentAssignmentRequired', TRUE) AS `exit_condition`
    UNION ALL
    SELECT 'PENDING_START', 'START', 'IN_PROGRESS', 'CURRENT_EFFECTIVE_ASSIGNEE',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:execute'),
           JSON_OBJECT('schemaVersion', 1, 'actualStartTimeRequired', TRUE)
    UNION ALL
    SELECT 'IN_PROGRESS', 'SUBMIT', 'PENDING_ACCEPT', 'CURRENT_EFFECTIVE_ASSIGNEE',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:execute'),
           JSON_OBJECT('schemaVersion', 1, 'progress', 99)
    UNION ALL
    SELECT 'PENDING_ACCEPT', 'COMPLETE', 'DONE', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'completionRuleSatisfied', TRUE)
    UNION ALL
    SELECT 'PENDING_ASSIGN', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'PENDING_START', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'IN_PROGRESS', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE)
    UNION ALL
    SELECT 'PENDING_ACCEPT', 'CANCEL', 'CLOSED', 'CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER',
           JSON_OBJECT('schemaVersion', 1, 'permissionCode', 'pms:project-task:complete'),
           JSON_OBJECT('schemaVersion', 1, 'reasonRequired', TRUE);
    IF (SELECT COUNT(*) FROM proj_task_state_transition
        WHERE tenant_id=1 AND revision_id=996610195020063) <> 8
       OR (SELECT COUNT(*) FROM proj_task_state_transition actual
           JOIN v517_expected_transition expected
             ON BINARY actual.from_status_code=BINARY expected.from_status_code
            AND BINARY actual.action_code=BINARY expected.action_code
            AND BINARY actual.to_status_code=BINARY expected.to_status_code
            AND BINARY actual.standard_status_mapping=BINARY expected.to_status_code
            AND BINARY actual.allowed_role_code=BINARY expected.allowed_role_code
            AND actual.entry_condition=CAST(expected.entry_condition AS JSON)
            AND actual.exit_condition=CAST(expected.exit_condition AS JSON)
           WHERE actual.tenant_id=1 AND actual.revision_id=996610195020063
             AND actual.deleted=0 AND actual.version=0
             AND BINARY actual.creator=BINARY 'migration-v502-59299-config'
             AND BINARY actual.updater=BINARY 'migration-v502-59299-config') <> 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V517: default task transitions changed; no configuration published';
    END IF;
    IF EXISTS (SELECT 1 FROM proj_task_state_machine_revision WHERE id=996610195170001)
       OR EXISTS (SELECT 1 FROM proj_task_state_transition
                  WHERE id BETWEEN 996610195170002 AND 996610195170009) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V517: reserved task state ID collision';
    END IF;
    SET v_revision_no=(SELECT COALESCE(MAX(revision_no),0)+1
                       FROM proj_task_state_machine_revision WHERE tenant_id=1);
    SET v_now=CURRENT_TIMESTAMP(3);
    INSERT INTO proj_task_state_machine_revision
        (id,revision_no,status,effective_from,published_by,published_at,version,creator,updater,tenant_id)
    VALUES (996610195170001,v_revision_no,'PUBLISHED',v_now,NULL,v_now,0,
            'migration-v517-default-task-state','migration-v517-default-task-state',1);
    INSERT INTO proj_task_state_transition
        (id,revision_id,from_status_code,action_code,to_status_code,standard_status_mapping,
         allowed_role_code,entry_condition,exit_condition,version,creator,updater,tenant_id)
    SELECT 996610195170001+ROW_NUMBER() OVER (ORDER BY from_status_code,action_code),
           996610195170001,from_status_code,action_code,to_status_code,to_status_code,
           allowed_role_code,entry_condition,exit_condition,0,
           'migration-v517-default-task-state','migration-v517-default-task-state',1
    FROM v517_expected_transition;
    DROP TEMPORARY TABLE v517_expected_transition;
    COMMIT;
END$$
DELIMITER ;
CALL migrate_v517_default_task_state();
DROP PROCEDURE migrate_v517_default_task_state;
