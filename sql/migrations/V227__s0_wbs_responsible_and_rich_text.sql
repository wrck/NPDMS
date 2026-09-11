-- S0专项裁决08/10：负责人独立时态关系；原指派办理链路和原说明内容保持。
CREATE TABLE proj_project_task_responsible (
  id bigint NOT NULL,
  project_id bigint NOT NULL,
  project_task_id bigint NOT NULL,
  user_id bigint NOT NULL,
  effective_from datetime(6) NOT NULL,
  effective_to datetime(6) DEFAULT NULL,
  assigned_by bigint NOT NULL,
  reason varchar(500) NOT NULL DEFAULT '',
  current_marker tinyint GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL AND deleted=0 THEN 1 ELSE NULL END) STORED,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_proj_task_responsible_current (tenant_id,project_task_id,current_marker),
  KEY idx_proj_task_responsible_user (tenant_id,user_id,project_id,effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务协调负责人责任区间，不授予办理或审批权限';

ALTER TABLE proj_project_task
  MODIFY COLUMN description LONGTEXT NULL COMMENT '任务说明，格式由description_format明确',
  ADD COLUMN description_format varchar(16) NOT NULL DEFAULT 'PLAIN' COMMENT 'PLAIN保留旧纯文本；HTML为安全富文本';
