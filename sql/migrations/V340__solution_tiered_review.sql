-- SOL links immutable source versions to real BPM instances. Existing solutions and approvals are preserved.
CREATE TABLE sol_solution_review (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  project_id bigint NOT NULL,
  solution_id bigint NOT NULL,
  source_version int NOT NULL,
  request_version int NOT NULL,
  process_definition_id varchar(128) NOT NULL,
  process_instance_id varchar(64) DEFAULT NULL,
  business_key varchar(128) NOT NULL,
  candidates_json json NOT NULL,
  status varchar(16) NOT NULL,
  reviews_json json DEFAULT NULL,
  submitted_by bigint NOT NULL,
  submitted_at datetime NOT NULL,
  completed_at datetime DEFAULT NULL,
  approved_version int DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sol_review_solution (tenant_id, solution_id),
  UNIQUE KEY uk_sol_review_instance (tenant_id, process_instance_id),
  CONSTRAINT ck_sol_review_status CHECK (status IN ('RUNNING','APPROVE','REJECT','CANCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实施方案分级审批版本关联';

-- The user designated Engineering Management as the reviewer. Membership is managed, never granted to all users.
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, tenant_id, deleted)
SELECT 992209220340, '工程管理部方案复审', 'ENGINEERING_MANAGEMENT_REVIEW', 60, 5, '[]', 0, 2,
       '重大实施方案复审；仍须项目范围，成员由权限管理配置', 'seed_solution_review', 'seed_solution_review', 1, b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE tenant_id = 1 AND code = 'ENGINEERING_MANAGEMENT_REVIEW' AND deleted = b'0');

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT 992209220341, '工程管理部方案复审', 'pms:sol-solution:major-review', 3, 80, m.id, '', '', '', '',
       0, b'0', b'1', b'1', 'seed_solution_review', 'seed_solution_review', b'0'
FROM system_menu m WHERE m.permission = 'pms:sol-solution:query' AND m.type = 2 AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'pms:sol-solution:major-review' AND deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, tenant_id)
SELECT r.id, m.id, 'seed_solution_review', 'seed_solution_review', r.tenant_id
FROM system_role r JOIN system_menu m ON m.permission IN ('pms:sol-solution:major-review', 'pms:sol-solution:query') AND m.deleted = b'0'
WHERE r.tenant_id = 1 AND r.code = 'ENGINEERING_MANAGEMENT_REVIEW' AND r.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.tenant_id = r.tenant_id AND rm.deleted = b'0');
