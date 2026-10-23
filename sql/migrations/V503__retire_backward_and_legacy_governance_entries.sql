-- Remove only retired application entry points; retain rows, grants and business history.
UPDATE system_menu
SET status = 1, visible = b'0', updater = 'V503', update_time = CURRENT_TIMESTAMP
WHERE deleted = b'0'
  AND component IN (
    'pms/engineering/schedule-backward/index',
    'pms/project/project-governance/index'
  )
  AND (status <> 1 OR visible <> b'0');
