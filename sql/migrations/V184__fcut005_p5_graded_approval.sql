CREATE TABLE `cut_approval_instance` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `plan_revision_id` bigint NOT NULL,
  `plan_revision_no` int NOT NULL,
  `assessment_id` bigint NOT NULL,
  `assessment_version` int NOT NULL,
  `checklist_id` bigint DEFAULT NULL,
  `checklist_version` int DEFAULT NULL,
  `grade_code` varchar(8) NOT NULL,
  `initiator_user_id` bigint NOT NULL,
  `initiator_project_scope_version` bigint NOT NULL,
  `source_snapshot_version` int NOT NULL,
  `source_snapshot` json NOT NULL,
  `route_snapshot` json NOT NULL,
  `status_code` varchar(40) NOT NULL,
  `hold_reason_code` varchar(40) DEFAULT NULL,
  `current_node_no` int DEFAULT NULL,
  `previous_approval_instance_id` bigint DEFAULT NULL,
  `replacement_approval_instance_id` bigint DEFAULT NULL,
  `decision_at` datetime(3) DEFAULT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_approval_task_plan` (`tenant_id`,`task_id`,`plan_revision_id`),
  UNIQUE KEY `uk_cut_approval_previous` (`tenant_id`,`previous_approval_instance_id`),
  KEY `idx_cut_approval_project_status` (`tenant_id`,`project_id`,`status_code`,`id`),
  CONSTRAINT `fk_cut_approval_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `fk_cut_approval_plan` FOREIGN KEY (`plan_revision_id`) REFERENCES `cut_plan_revision` (`id`),
  CONSTRAINT `chk_cut_approval_values` CHECK (`plan_revision_no` > 0 AND `assessment_version` > 0
    AND `initiator_user_id` > 0 AND `initiator_project_scope_version` >= 0
    AND `source_snapshot_version` > 0 AND `version` >= 0
    AND (`current_node_no` IS NULL OR `current_node_no` > 0)),
  CONSTRAINT `chk_cut_approval_grade` CHECK (`grade_code` IN ('A','B','C','D')),
  CONSTRAINT `chk_cut_approval_checklist` CHECK (
    COALESCE((`grade_code` IN ('A','B','C') AND `checklist_id` > 0 AND `checklist_version` > 0), FALSE)
    OR COALESCE((`grade_code` = 'D' AND `checklist_id` IS NULL AND `checklist_version` IS NULL), FALSE)),
  CONSTRAINT `chk_cut_approval_status` CHECK (`status_code` IN
    ('PENDING','PAUSED_SOURCE_INVALIDATED','APPROVED','REJECTED')),
  CONSTRAINT `chk_cut_approval_hold` CHECK (
    (`hold_reason_code` IS NULL)
    OR (`status_code` = 'PENDING' AND `hold_reason_code` IN
      ('ROUTE_CANDIDATE_NOT_UNIQUE','APPROVER_UNAVAILABLE'))),
  CONSTRAINT `chk_cut_approval_decision` CHECK (
    COALESCE((`status_code` = 'APPROVED' AND `decision_at` IS NOT NULL AND `rejection_reason` IS NULL), FALSE)
    OR COALESCE((`status_code` = 'REJECTED' AND `decision_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`rejection_reason`)) BETWEEN 1 AND 1000), FALSE)
    OR COALESCE((`status_code` IN ('PENDING','PAUSED_SOURCE_INVALIDATED')
      AND `decision_at` IS NULL AND `rejection_reason` IS NULL), FALSE)),
  CONSTRAINT `chk_cut_approval_replacement` CHECK (
    (`previous_approval_instance_id` IS NULL OR `previous_approval_instance_id` > 0)
    AND (`replacement_approval_instance_id` IS NULL OR `replacement_approval_instance_id` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-005 P5分级审批实例';

CREATE TABLE `cut_approval_node` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `approval_instance_id` bigint NOT NULL,
  `node_no` int NOT NULL,
  `node_code` varchar(32) NOT NULL,
  `status_code` varchar(16) NOT NULL,
  `original_approver_user_id` bigint DEFAULT NULL,
  `current_approver_user_id` bigint DEFAULT NULL,
  `candidate_fact_snapshot` json NOT NULL,
  `project_scope_version` bigint DEFAULT NULL,
  `assessment_review_decision_code` varchar(24) DEFAULT NULL,
  `assessment_review_reason` varchar(1000) DEFAULT NULL,
  `feedback` varchar(1000) DEFAULT NULL,
  `decision_at` datetime(3) DEFAULT NULL,
  `pending_marker` tinyint GENERATED ALWAYS AS
    (CASE WHEN `status_code` = 'PENDING' THEN 1 ELSE NULL END) STORED,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_approval_node_no` (`tenant_id`,`approval_instance_id`,`node_no`),
  UNIQUE KEY `uk_cut_approval_pending_node` (`tenant_id`,`approval_instance_id`,`pending_marker`),
  KEY `idx_cut_approval_todo` (`tenant_id`,`current_approver_user_id`,`status_code`,`id`),
  CONSTRAINT `fk_cut_approval_node_instance` FOREIGN KEY (`approval_instance_id`) REFERENCES `cut_approval_instance` (`id`),
  CONSTRAINT `chk_cut_approval_node_values` CHECK (`node_no` > 0 AND `version` >= 0
    AND (`project_scope_version` IS NULL OR `project_scope_version` >= 0)),
  CONSTRAINT `chk_cut_approval_node_code` CHECK (`node_code` IN
    ('INITIATOR','SERVICE_MANAGER','SECOND_LINE','RND')),
  CONSTRAINT `chk_cut_approval_node_status` CHECK (`status_code` IN
    ('WAITING','PENDING','APPROVED','REJECTED','CANCELLED')),
  CONSTRAINT `chk_cut_approval_node_approver` CHECK (
    (`original_approver_user_id` IS NULL AND `current_approver_user_id` IS NULL)
    OR COALESCE((`original_approver_user_id` > 0 AND `current_approver_user_id` > 0), FALSE)),
  CONSTRAINT `chk_cut_approval_node_decision` CHECK (
    COALESCE((`status_code` IN ('APPROVED','REJECTED') AND `decision_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`feedback`)) BETWEEN 1 AND 1000), FALSE)
    OR (`status_code` IN ('WAITING','PENDING','CANCELLED') AND `decision_at` IS NULL AND `feedback` IS NULL)),
  CONSTRAINT `chk_cut_approval_assessment_review` CHECK (
    (`node_code` <> 'SERVICE_MANAGER' AND `assessment_review_decision_code` IS NULL
      AND `assessment_review_reason` IS NULL)
    OR (`node_code` = 'SERVICE_MANAGER' AND `assessment_review_decision_code` IS NULL
      AND `assessment_review_reason` IS NULL)
    OR (`node_code` = 'SERVICE_MANAGER' AND `assessment_review_decision_code` = 'CONFIRMED'
      AND `assessment_review_reason` IS NULL)
    OR COALESCE((`node_code` = 'SERVICE_MANAGER' AND `assessment_review_decision_code` = 'NOT_REASONABLE'
      AND `status_code` = 'REJECTED'
      AND CHAR_LENGTH(TRIM(`assessment_review_reason`)) BETWEEN 1 AND 1000), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-005审批节点';

CREATE TABLE `cut_approval_review_item` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `approval_instance_id` bigint NOT NULL,
  `approval_node_id` bigint NOT NULL,
  `item_code` varchar(32) NOT NULL,
  `decision_code` varchar(8) NOT NULL,
  `unreasonable_reason` varchar(1000) DEFAULT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_approval_review_item` (`tenant_id`,`approval_node_id`,`item_code`),
  KEY `idx_cut_approval_review_instance` (`tenant_id`,`approval_instance_id`,`approval_node_id`),
  CONSTRAINT `fk_cut_approval_review_instance` FOREIGN KEY (`approval_instance_id`) REFERENCES `cut_approval_instance` (`id`),
  CONSTRAINT `fk_cut_approval_review_node` FOREIGN KEY (`approval_node_id`) REFERENCES `cut_approval_node` (`id`),
  CONSTRAINT `chk_cut_approval_review_code` CHECK (`item_code` IN
    ('PREPARATION','BUSINESS_TEST','EXECUTION','ROLLBACK','OTHER')),
  CONSTRAINT `chk_cut_approval_review_decision` CHECK (
    (`decision_code` = 'YES' AND `unreasonable_reason` IS NULL)
    OR COALESCE((`decision_code` = 'NO'
      AND CHAR_LENGTH(TRIM(`unreasonable_reason`)) BETWEEN 1 AND 1000), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-005服务经理复核项';

CREATE TABLE `cut_approval_reassignment` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `approval_instance_id` bigint NOT NULL,
  `approval_node_id` bigint NOT NULL,
  `reassignment_no` int NOT NULL,
  `from_approver_user_id` bigint DEFAULT NULL,
  `to_approver_user_id` bigint NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `candidate_fact_snapshot` json NOT NULL,
  `operated_by` bigint NOT NULL,
  `operated_at` datetime(3) NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_approval_reassignment_no` (`tenant_id`,`approval_node_id`,`reassignment_no`),
  KEY `idx_cut_approval_reassignment_instance` (`tenant_id`,`approval_instance_id`,`approval_node_id`),
  CONSTRAINT `fk_cut_approval_reassignment_instance` FOREIGN KEY (`approval_instance_id`) REFERENCES `cut_approval_instance` (`id`),
  CONSTRAINT `fk_cut_approval_reassignment_node` FOREIGN KEY (`approval_node_id`) REFERENCES `cut_approval_node` (`id`),
  CONSTRAINT `chk_cut_approval_reassignment_values` CHECK (`reassignment_no` > 0
    AND `to_approver_user_id` > 0 AND `operated_by` > 0
    AND CHAR_LENGTH(TRIM(`reason`)) BETWEEN 1 AND 1000
    AND (`from_approver_user_id` IS NULL
      OR (`from_approver_user_id` > 0 AND `from_approver_user_id` <> `to_approver_user_id`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-005审批改派历史';

CREATE TABLE `cut_approval_notification` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `approval_instance_id` bigint NOT NULL,
  `approval_node_id` bigint NOT NULL,
  `recipient_user_id` bigint NOT NULL,
  `delivery_key` varchar(128) NOT NULL,
  `template_code` varchar(64) NOT NULL,
  `status_code` varchar(24) NOT NULL,
  `message_id` bigint DEFAULT NULL,
  `retry_count` int NOT NULL,
  `next_retry_at` datetime(3) DEFAULT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  `sent_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_approval_notification_delivery` (`tenant_id`,`delivery_key`),
  KEY `idx_cut_approval_notification_due` (`tenant_id`,`status_code`,`next_retry_at`,`id`),
  CONSTRAINT `fk_cut_approval_notification_instance` FOREIGN KEY (`approval_instance_id`) REFERENCES `cut_approval_instance` (`id`),
  CONSTRAINT `fk_cut_approval_notification_node` FOREIGN KEY (`approval_node_id`) REFERENCES `cut_approval_node` (`id`),
  CONSTRAINT `chk_cut_approval_notification_values` CHECK (`recipient_user_id` > 0
    AND CHAR_LENGTH(TRIM(`delivery_key`)) BETWEEN 1 AND 128
    AND CHAR_LENGTH(TRIM(`template_code`)) BETWEEN 1 AND 64
    AND `retry_count` >= 0 AND `version` >= 0),
  CONSTRAINT `chk_cut_approval_notification_status` CHECK (
    (`status_code` = 'PENDING' AND `message_id` IS NULL AND `next_retry_at` IS NULL
      AND `last_error_code` IS NULL AND `sent_at` IS NULL)
    OR COALESCE((`status_code` = 'PENDING_RETRY' AND `message_id` IS NULL
      AND `next_retry_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`last_error_code`)) BETWEEN 1 AND 64 AND `sent_at` IS NULL), FALSE)
    OR COALESCE((`status_code` = 'SENT' AND `message_id` > 0 AND `next_retry_at` IS NULL
      AND `last_error_code` IS NULL AND `sent_at` IS NOT NULL), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-005审批通知投递';
