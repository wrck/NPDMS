-- Retire application entries backed by the six removed V310 project tables.
-- Preserve menu rows, role bindings and shared permissions used by the new domain.
-- Existing historical tables/data are not changed; empty B498 does not create them.
UPDATE system_menu
SET status = 1, visible = b'0', updater = 'V499', update_time = CURRENT_TIMESTAMP
WHERE deleted = b'0'
  AND component IN (
    'pms/project/project/index',
    'pms/project/project-detail/index',
    'pms/project/project-phase/index',
    'pms/project/project-task/index',
    'pms/project/project-team/index',
    'pms/project/project-panoramic/index',
    'pms/project/plan-change/index'
  )
  AND (status <> 1 OR visible <> b'0');
