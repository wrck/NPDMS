-- The approved inspection replacement owns /pms/inspection/* and its new permissions.
-- Retire only the four old inspection pages and their twelve buttons.
-- Keep role associations, maintenance entries, historical dictionaries and executed migrations.
-- Legacy business rows are a separately reviewed, backed-up cleanup batch; do not delete them here.
UPDATE system_menu
SET status = 1, visible = b'0', updater = 'inspection_retirement', update_time = CURRENT_TIMESTAMP
WHERE deleted = b'0'
  AND (id, permission) IN (
    (19024, 'pms:srv-task:query'),
    (19025, 'pms:srv-rule:query'),
    (19026, 'pms:srv-report:query'),
    (19027, 'pms:srv-issue:query'),
    (19079, 'pms:srv-task:create'),
    (19080, 'pms:srv-task:update'),
    (19081, 'pms:srv-task:delete'),
    (19082, 'pms:srv-rule:create'),
    (19083, 'pms:srv-rule:update'),
    (19084, 'pms:srv-rule:delete'),
    (19085, 'pms:srv-report:create'),
    (19086, 'pms:srv-report:update'),
    (19087, 'pms:srv-report:delete'),
    (19088, 'pms:srv-issue:create'),
    (19089, 'pms:srv-issue:update'),
    (19090, 'pms:srv-issue:delete')
  )
  AND (status <> 1 OR visible <> b'0');
