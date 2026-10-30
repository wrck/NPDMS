-- Approved 2026-10-22: project managers may delete completion/training drafts.
-- Native services retain project scope and draft-state enforcement.
UPDATE system_role_menu grant_row
JOIN system_role role_row ON role_row.id=grant_row.role_id AND role_row.tenant_id=grant_row.tenant_id
JOIN system_menu action_row ON action_row.id=grant_row.menu_id
SET grant_row.deleted=b'0',grant_row.updater='pm-draft-delete',grant_row.update_time=NOW()
WHERE role_row.code='PROJECT_MANAGER' AND role_row.status=0 AND role_row.deleted=b'0'
  AND action_row.permission IN ('pms:acc-completion-certificate:delete','pms:imp-training:delete')
  AND action_row.status=0 AND action_row.deleted=b'0' AND grant_row.deleted=b'1';

INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT role_row.id,action_row.id,'pm-draft-delete','pm-draft-delete',b'0',role_row.tenant_id
FROM system_role role_row
JOIN system_menu action_row ON action_row.permission IN ('pms:acc-completion-certificate:delete','pms:imp-training:delete')
  AND action_row.status=0 AND action_row.deleted=b'0'
WHERE role_row.code='PROJECT_MANAGER' AND role_row.status=0 AND role_row.deleted=b'0'
  AND NOT EXISTS(SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id=role_row.id AND existing.menu_id=action_row.id AND existing.tenant_id=role_row.tenant_id);
