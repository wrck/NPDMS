-- The requester confirmed the existing probSupporter/probRD roles for shared CUT approvals.
-- A role node has no original individual assignee; a completed decision records its actual actor.
-- Keep the existing individual-node invariant and all other decision/history constraints.
ALTER TABLE cut_approval_node
DROP CHECK chk_cut_approval_node_approver,
ADD CONSTRAINT chk_cut_approval_node_approver CHECK (
    IF(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(candidate_fact_snapshot, '$.approvalMode')) = 'ROLE', FALSE),
       COALESCE(original_approver_user_id IS NULL
           AND ((node_code = 'SECOND_LINE'
                   AND JSON_UNQUOTE(JSON_EXTRACT(candidate_fact_snapshot, '$.roleGroupCode')) = 'probSupporter')
                OR (node_code = 'RND'
                   AND JSON_UNQUOTE(JSON_EXTRACT(candidate_fact_snapshot, '$.roleGroupCode')) = 'probRD'))
           AND ((status_code IN ('WAITING', 'PENDING', 'CANCELLED') AND current_approver_user_id IS NULL)
                OR (status_code IN ('APPROVED', 'REJECTED') AND current_approver_user_id > 0)), FALSE),
       (original_approver_user_id IS NULL AND current_approver_user_id IS NULL)
       OR COALESCE(original_approver_user_id > 0 AND current_approver_user_id > 0, FALSE)) = TRUE
);

-- Grant only the entry and permissions needed to read and decide an authorized approval.
-- The existing BPM todo opens the approval form; do not grant the ordinary CUT workbench query.
-- User-role assignments, role data scopes, project membership and historical approvals are unchanged.
INSERT INTO system_role_menu
(role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT role.id, menu.id, 'cut-role-20261013', NOW(), 'cut-role-20261013', NOW(), b'0', role.tenant_id
FROM system_role role
JOIN system_menu menu
  ON menu.id IN (1185, 1200, 1207)
     OR menu.permission IN ('pms:cutover-task:query-approval', 'pms:cutover-task:approve',
                            'pms:file:query', 'bpm:task:query', 'bpm:process-instance:query')
WHERE role.tenant_id = 1
  AND role.code IN ('probSupporter', 'probRD')
  AND role.status = 0 AND role.deleted = b'0'
  AND menu.status = 0 AND menu.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu present
      WHERE present.tenant_id = role.tenant_id AND present.role_id = role.id
        AND present.menu_id = menu.id AND present.deleted = b'0'
  );
