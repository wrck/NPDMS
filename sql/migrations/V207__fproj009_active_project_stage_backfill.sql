-- PM-03 / PM-01: user-authorized isolated fixture repair, 2026-09-09.
-- Source is the project's exact published template, never parent inheritance or stage-number inference.
-- No existing Project, Stage, task, approval, completed fact or published template is updated.

DELIMITER $$
DROP PROCEDURE IF EXISTS `fproj009_backfill_known_active_stages`$$
CREATE PROCEDURE `fproj009_backfill_known_active_stages`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_project BIGINT;
    DECLARE v_tenant BIGINT;
    DECLARE v_template BIGINT;
    DECLARE v_revision INT;
    DECLARE v_slot INT;
    DECLARE v_current VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    DECLARE v_version INT;
    DECLARE v_current_count INT;
    DECLARE v_conflicts INT;
    DECLARE v_missing INT;
    DECLARE v_inserted INT;
    DECLARE v_before JSON;
    DECLARE v_after JSON;
    DECLARE targets CURSOR FOR
        SELECT p.id, p.tenant_id, p.lifecycle_template_id, p.lifecycle_template_revision_no,
               p.current_stage, p.version,
               CASE
                   WHEN p.tenant_id=0 AND p.id BETWEEN 920001 AND 920006 THEN p.id-920001
                   WHEN p.tenant_id=0 AND p.id=992002000000 THEN 6
                   WHEN p.tenant_id=1 AND p.id BETWEEN 992203060001 AND 992203060003 THEN p.id-992203060001+7
               END
        FROM proj_project p
        WHERE p.deleted=b'0' AND p.lifecycle_status='ACTIVE'
          AND ((p.tenant_id=0 AND p.id BETWEEN 920001 AND 920006
                AND p.lifecycle_template_id=910001 AND p.lifecycle_template_revision_no=2)
            OR (p.tenant_id=0 AND p.id=992002000000
                AND p.lifecycle_template_id=910008 AND p.lifecycle_template_revision_no=1)
            OR (p.tenant_id=1 AND p.id BETWEEN 992203060001 AND 992203060003
                AND p.lifecycle_template_id=992203040001 AND p.lifecycle_template_revision_no=2))
        ORDER BY p.tenant_id,p.id FOR UPDATE;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    OPEN targets;
    target_loop: LOOP
        FETCH targets INTO v_project,v_tenant,v_template,v_revision,v_current,v_version,v_slot;
        IF done=1 THEN LEAVE target_loop; END IF;

        SELECT COUNT(*) INTO v_current_count
        FROM proj_project_template_revision r
        JOIN proj_project_template_stage_definition d ON d.template_revision_id=r.id
            AND d.tenant_id=r.tenant_id AND d.deleted=b'0'
        WHERE r.tenant_id=v_tenant AND r.template_id=v_template AND r.revision_no=v_revision
            AND r.status='PUBLISHED' AND r.deleted=b'0' AND d.stage_code=v_current;
        IF v_current_count<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FPROJ009 exact template/current-stage source is missing or ambiguous';
        END IF;

        SELECT COUNT(*) INTO v_conflicts
        FROM proj_project_stage s
        WHERE s.tenant_id=v_tenant AND s.project_id=v_project AND s.deleted=b'0'
          AND ((s.stage_code=v_current AND s.status<>'ACTIVE')
            OR (s.stage_code<>v_current AND s.status='ACTIVE'));
        IF v_conflicts<>0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FPROJ009 existing stage state conflicts with current project stage';
        END IF;

        SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('id',id,'stageCode',stage_code,'status',status,
                                                  'version',version,'sourceDefinitionId',source_definition_id)),JSON_ARRAY())
        INTO v_before FROM proj_project_stage
        WHERE tenant_id=v_tenant AND project_id=v_project AND deleted=b'0';

        SELECT COUNT(*) INTO v_missing
        FROM proj_project_template_revision r
        JOIN proj_project_template_stage_definition d ON d.template_revision_id=r.id
            AND d.tenant_id=r.tenant_id AND d.deleted=b'0'
        WHERE r.tenant_id=v_tenant AND r.template_id=v_template AND r.revision_no=v_revision
            AND r.status='PUBLISHED' AND r.deleted=b'0'
            AND NOT EXISTS (SELECT 1 FROM proj_project_stage s
                WHERE s.tenant_id=v_tenant AND s.project_id=v_project AND s.stage_code=d.stage_code AND s.deleted=b'0');

        IF v_missing>0 THEN
            IF EXISTS (SELECT 1 FROM proj_project_stage s
                JOIN proj_project_template_revision r ON r.tenant_id=v_tenant AND r.template_id=v_template
                    AND r.revision_no=v_revision AND r.status='PUBLISHED' AND r.deleted=b'0'
                JOIN proj_project_template_stage_definition d ON d.template_revision_id=r.id
                    AND d.tenant_id=r.tenant_id AND d.deleted=b'0'
                WHERE s.id=994009100000+v_slot*10+CAST(SUBSTRING(d.stage_code,2) AS UNSIGNED)
                  AND NOT (s.tenant_id=v_tenant AND s.project_id=v_project AND s.stage_code=d.stage_code
                    AND s.creator='fproj009-stage-repair' AND s.deleted=b'0')) THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FPROJ009 reserved stage ID collision';
            END IF;

            INSERT INTO proj_project_stage
                (id,project_id,stage_code,name,sort_order,entry_criteria,exit_criteria,source_definition_id,
                 status,version,creator,create_time,updater,update_time,deleted,tenant_id)
            SELECT 994009100000+v_slot*10+CAST(SUBSTRING(d.stage_code,2) AS UNSIGNED),v_project,
                   d.stage_code,d.name,d.sort_order,d.entry_criteria,d.exit_criteria,d.id,
                   CASE WHEN d.stage_code=v_current THEN 'ACTIVE' ELSE 'PENDING' END,0,
                   'fproj009-stage-repair',NOW(),'fproj009-stage-repair',NOW(),b'0',v_tenant
            FROM proj_project_template_revision r
            JOIN proj_project_template_stage_definition d ON d.template_revision_id=r.id
                AND d.tenant_id=r.tenant_id AND d.deleted=b'0'
            WHERE r.tenant_id=v_tenant AND r.template_id=v_template AND r.revision_no=v_revision
                AND r.status='PUBLISHED' AND r.deleted=b'0'
                AND NOT EXISTS (SELECT 1 FROM proj_project_stage s
                    WHERE s.tenant_id=v_tenant AND s.project_id=v_project AND s.stage_code=d.stage_code AND s.deleted=b'0');
            SET v_inserted=ROW_COUNT();
            IF v_inserted<>v_missing THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FPROJ009 stage insertion count changed';
            END IF;

            SELECT JSON_ARRAYAGG(JSON_OBJECT('id',id,'stageCode',stage_code,'status',status,
                                             'version',version,'sourceDefinitionId',source_definition_id))
            INTO v_after FROM proj_project_stage
            WHERE tenant_id=v_tenant AND project_id=v_project AND deleted=b'0';

            INSERT INTO plt_operation_audit
                (operation_code,aggregate_type,aggregate_key,actor_id,correlation_id,result_code,
                 detail_snapshot,occurred_at,creator,tenant_id)
            VALUES ('PROJECT_TEMPLATE_STAGE_BACKFILL','Project',CAST(v_project AS CHAR),0,
                    CONCAT('FPROJ009-STAGE-BACKFILL-20260909:',v_tenant,':',v_project),'SUCCESS',
                    JSON_OBJECT('authorization','USER_REQUEST_20260909','executionIdentity','FLYWAY',
                                'templateId',v_template,'templateRevisionNo',v_revision,
                                'projectVersionUnchanged',v_version,'currentStageUnchanged',v_current,
                                'insertedStageCount',v_inserted,'before',v_before,'after',v_after),
                    NOW(3),'fproj009-stage-repair',v_tenant);
        END IF;
    END LOOP;
    CLOSE targets;
    COMMIT;
END$$
CALL `fproj009_backfill_known_active_stages`()$$
DROP PROCEDURE `fproj009_backfill_known_active_stages`$$
DELIMITER ;
