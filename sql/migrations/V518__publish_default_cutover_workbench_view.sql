-- Approved initialization repair: the default CUT approval workbench must be usable.
-- Preserve its ID because published templates already reference this exact revision.
-- Only the untouched V502 PAGE seed may transition from draft to published.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS migrate_v518_default_cutover_view;
DELIMITER $$
CREATE PROCEDURE migrate_v518_default_cutover_view()
migration_block: BEGIN
    DECLARE v_id BIGINT DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    SELECT id INTO v_id FROM plt_business_view_revision
    WHERE id=996610195020037 AND tenant_id=1 AND deleted=0 FOR UPDATE;
    IF v_id IS NULL THEN
        COMMIT;
        LEAVE migration_block;
    END IF;
    IF EXISTS (SELECT 1 FROM plt_business_view_revision WHERE id=v_id
               AND (published_at IS NOT NULL OR disabled_at IS NOT NULL)) THEN
        COMMIT;
        LEAVE migration_block;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM plt_business_view_revision WHERE id=v_id AND tenant_id=1
          AND CAST(entity_type AS BINARY)=CAST('CUTOVER_APPROVAL' AS BINARY)
          AND CAST(view_key AS BINARY)=CAST('CUT_CUTOVER_APPROVAL_WORKBENCH' AS BINARY)
          AND revision_no=1 AND version=0
          AND CAST(owner_context AS BINARY)=CAST('CUT' AS BINARY)
          AND CAST(view_source AS BINARY)=CAST('PAGE' AS BINARY)
          AND dynamic_form_revision_id IS NULL
          AND CAST(component_key AS BINARY)=CAST('CUT_CUTOVER_APPROVAL_WORKBENCH' AS BINARY)
          AND CAST(component_version AS BINARY)=CAST('1' AS BINARY)
          AND context_schema=CAST('{"type":"object","required":["projectId"],"properties":{"projectId":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[1-9][0-9]*$"}]}}}' AS JSON)
          AND supported_actions=CAST('["QUERY","CREATE"]' AS JSON)
          AND CAST(query_provider_key AS BINARY)=CAST('CUT_CUTOVER_APPROVAL_WORKBENCH_QUERY' AS BINARY)
          AND CAST(command_provider_key AS BINARY)=CAST('CUT_CUTOVER_APPROVAL_WORKBENCH_COMMAND' AS BINARY)
          AND CAST(permission_provider_key AS BINARY)=CAST('CUT_CUTOVER_APPROVAL_WORKBENCH_PERMISSION' AS BINARY)
          AND CAST(creator AS BINARY)=CAST('migration-v502-59299-config' AS BINARY)
          AND CAST(updater AS BINARY)=CAST('migration-v502-59299-config' AS BINARY)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V518: default cutover view changed; preserve and publish through Owner validation';
    END IF;
    IF EXISTS (SELECT 1 FROM plt_business_view_revision WHERE tenant_id=1
               AND entity_type='CUTOVER_APPROVAL' AND view_key='CUT_CUTOVER_APPROVAL_WORKBENCH'
               AND id<>v_id AND deleted=0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V518: additional cutover view revisions exist; preserve publication choice';
    END IF;
    -- This PAGE provider has no form/workflow dependency. Keep business fields and creator intact.
    -- Authentication, task/project scope and CUT commands remain checked by the existing Providers.
    UPDATE plt_business_view_revision
    SET published_at=CURRENT_TIMESTAMP(6),version=version+1,updater='migration-v518-default-cutover-view'
    WHERE id=v_id AND tenant_id=1 AND deleted=0 AND published_at IS NULL
      AND disabled_at IS NULL AND version=0;
    IF ROW_COUNT()<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V518: cutover view publication conflict';
    END IF;
    COMMIT;
END$$
DELIMITER ;
CALL migrate_v518_default_cutover_view();
DROP PROCEDURE migrate_v518_default_cutover_view;
