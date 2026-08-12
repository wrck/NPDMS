-- ====================================================================
-- T-V1-PROJ-003 / T-V1-PROJ-004 / T-V1-PROJ-005
-- 项目分类、指派、团队、非固定项目树、拆分/合并/子树移动
-- 幂等迁移：使用 PREPARE/EXECUTE 兼容首次执行与已部分应用状态
-- ====================================================================

-- 1. 扩展 pms_project 表字段：parent_id
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'parent_id');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN parent_id bigint DEFAULT NULL AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- root_id
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'root_id');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN root_id bigint DEFAULT NULL AFTER parent_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- path
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'path');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN path varchar(512) DEFAULT ''/'' AFTER root_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- depth
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'depth');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN depth int NOT NULL DEFAULT 0 AFTER path', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sort
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'sort');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN sort int NOT NULL DEFAULT 0 AFTER depth', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- category
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'category');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN category varchar(64) DEFAULT NULL AFTER sort', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- major_project_flag
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'major_project_flag');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN major_project_flag bit(1) NOT NULL DEFAULT b''0'' AFTER category', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- manager_user_id
SET @col := (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND column_name = 'manager_user_id');
SET @sql := IF(@col = 0, 'ALTER TABLE pms_project ADD COLUMN manager_user_id bigint DEFAULT NULL AFTER major_project_flag', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 索引处理：旧 idx_pms_project_root 指向 root_project_id，需替换为指向新 root_id
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND index_name = 'idx_pms_project_root'
             AND column_name = 'root_project_id');
SET @sql := IF(@idx > 0, 'ALTER TABLE pms_project DROP INDEX idx_pms_project_root', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND index_name = 'idx_pms_project_root');
SET @sql := IF(@idx = 0, 'ALTER TABLE pms_project ADD KEY idx_pms_project_root (root_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND index_name = 'idx_pms_project_parent');
SET @sql := IF(@idx = 0, 'ALTER TABLE pms_project ADD KEY idx_pms_project_parent (parent_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'pms_project' AND index_name = 'idx_pms_project_path');
SET @sql := IF(@idx = 0, 'ALTER TABLE pms_project ADD KEY idx_pms_project_path (path(255))', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 项目团队成员表（FR-PROJ-013）
CREATE TABLE IF NOT EXISTS pms_project_team_member (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL,
  user_id bigint NOT NULL,
  role_code varchar(64) NOT NULL COMMENT '角色编码如 PROJECT_MANAGER/SERVICE_MANAGER/ENGINEER',
  role_name varchar(64) DEFAULT NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  remark varchar(500) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_project_team (project_id, user_id, role_code),
  KEY idx_pms_project_team_project (project_id),
  KEY idx_pms_project_team_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目团队成员';

-- 4. 项目树变更批次表（FR-PROJ-003 拆分/合并/移动审计）
CREATE TABLE IF NOT EXISTS pms_project_tree_change_batch (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_no varchar(64) NOT NULL,
  operation_type varchar(32) NOT NULL COMMENT 'SPLIT/MERGE/MOVE',
  source_project_id bigint NOT NULL,
  target_project_id bigint DEFAULT NULL,
  reason varchar(500) DEFAULT NULL,
  affected_count int NOT NULL DEFAULT 0,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
  approval_instance_id varchar(64) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_project_tree_change_batch_no (batch_no),
  KEY idx_pms_project_tree_change_source (source_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目树变更批次';

-- 5. 菜单权限（FR-PROJ-010 / FR-PROJ-012 / FR-PROJ-013 / FR-PROJ-002 / FR-PROJ-009 / FR-PROJ-003）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
(18020, '项目分类管理', 'pms:project:query', 3, 20, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18021, '项目指派', 'pms:project:assign', 3, 21, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18022, '项目团队管理', 'pms:project-team:query', 3, 22, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18023, '团队成员维护', 'pms:project-team:create', 3, 23, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18024, '项目树管理', 'pms:project-tree:query', 3, 24, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18025, '项目移动', 'pms:project-tree:move', 3, 25, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name), permission=VALUES(permission), update_time=NOW(), deleted=b'0';
