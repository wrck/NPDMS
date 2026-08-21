-- F-PROJ-001 / PM-01
-- Preserve the integration ledgers while removing the final pms_project* physical names.

RENAME TABLE
    pms_project_sync_batch TO proj_project_sync_batch,
    pms_project_sync_detail TO proj_project_sync_detail;
