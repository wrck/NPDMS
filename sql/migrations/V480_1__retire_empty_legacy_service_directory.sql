-- Retire only the empty legacy inspection/maintenance directory.
-- Current inspection navigation and business data are not part of this migration.
UPDATE system_menu AS parent
LEFT JOIN system_menu AS child
  ON child.parent_id = parent.id AND child.deleted = b'0' AND child.status = 0
SET parent.status = 1, parent.visible = b'0', parent.deleted = b'1',
    parent.updater = 'inspection_retirement', parent.update_time = CURRENT_TIMESTAMP
WHERE parent.id = 19265 AND parent.parent_id = 18000 AND parent.path = 'service'
  AND parent.type = 1 AND parent.component IS NULL AND child.id IS NULL
  AND (parent.status <> 1 OR parent.visible <> b'0' OR parent.deleted <> b'1');

UPDATE system_role_menu
SET deleted = b'1', updater = 'inspection_retirement', update_time = CURRENT_TIMESTAMP
WHERE menu_id = 19265 AND deleted = b'0'
  AND EXISTS (SELECT 1 FROM system_menu
              WHERE id = 19265 AND path = 'service' AND type = 1
                AND status = 1 AND visible = b'0' AND deleted = b'1');
