-- 用户批准：个人审批提交时固定，角色待办领取后固定；旧终态历史及触发器保持不变。
ALTER TABLE cut_approval_node
  DROP CHECK chk_cut_approval_node_approver,
  ADD CONSTRAINT chk_cut_approval_node_approver CHECK ((COALESCE((
    (approval_mode_code='ROLE' AND candidate_role_id>0 AND candidate_group_key=CONCAT('ROLE:',candidate_role_id)
      AND original_approver_user_id IS NULL
      AND ((node_code='SECOND_LINE' AND candidate_role_code='probSupporter') OR (node_code='RND' AND candidate_role_code='probRD'))
      AND ((status_code='WAITING' AND current_approver_user_id IS NULL)
        OR (status_code IN ('PENDING','CANCELLED') AND (current_approver_user_id IS NULL OR current_approver_user_id>0))
        OR (status_code IN ('APPROVED','REJECTED') AND current_approver_user_id>0 AND decision_actor_user_id=current_approver_user_id)))
    OR (approval_mode_code='PERSONAL' AND candidate_role_id IS NULL AND candidate_role_code IS NULL AND candidate_group_key IS NULL
      AND ((original_approver_user_id IS NULL AND current_approver_user_id IS NULL)
        OR (original_approver_user_id>0 AND current_approver_user_id>0)))),FALSE))=TRUE);
