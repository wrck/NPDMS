-- ====================================================================
-- T-V1-PROJ-006 / T-V1-PROJ-007 / T-V1-PROJ-008 / T-V1-PROJ-009
-- 非固定任务 WBS、阶段模板、项目阶段、阶段门禁、项目风险、项目全景
-- ====================================================================

-- 1. 任务 WBS 主表（非固定层级，物化路径）
CREATE TABLE pms_project_task (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  parent_id bigint DEFAULT NULL COMMENT '父任务编号，根任务为 NULL',
  root_id bigint DEFAULT NULL COMMENT '根任务编号，根任务为自身 id',
  path varchar(512) DEFAULT '/' COMMENT '物化路径，格式 /{rootId}/.../{selfId}/',
  depth int NOT NULL DEFAULT 0 COMMENT '路径深度，根任务为 0',
  sort int NOT NULL DEFAULT 0 COMMENT '同级排序号',
  name varchar(128) NOT NULL COMMENT '任务名称',
  code varchar(64) DEFAULT NULL COMMENT '任务编码，项目内唯一（不为空时唯一）',
  description varchar(500) DEFAULT NULL COMMENT '任务描述',
  owner_user_id bigint DEFAULT NULL COMMENT '负责人用户编号',
  assignee_user_id bigint DEFAULT NULL COMMENT '执行人用户编号',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1待处理 2进行中 3受阻 4待验证 5已完成 6已取消',
  priority tinyint DEFAULT 0 COMMENT '优先级',
  plan_start_time datetime DEFAULT NULL COMMENT '计划开始时间',
  plan_end_time datetime DEFAULT NULL COMMENT '计划结束时间',
  actual_start_time datetime DEFAULT NULL COMMENT '实际开始时间',
  actual_end_time datetime DEFAULT NULL COMMENT '实际结束时间',
  estimated_hours decimal(10,2) DEFAULT NULL COMMENT '预估工时',
  actual_hours decimal(10,2) DEFAULT NULL COMMENT '实际工时',
  progress int DEFAULT 0 COMMENT '进度 0-100',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_project_task_project (project_id),
  KEY idx_pms_project_task_parent (parent_id),
  KEY idx_pms_project_task_path (path(255)),
  UNIQUE KEY uk_pms_project_task_code (project_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目任务 WBS';

-- 2. 任务前后置依赖表（与父子结构分离，承载 FS/SS/FF/SF 等依赖类型）
CREATE TABLE pms_project_task_dependency (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  predecessor_id bigint NOT NULL COMMENT '前置任务编号',
  successor_id bigint NOT NULL COMMENT '后置任务编号',
  dependency_type varchar(32) DEFAULT 'FINISH_TO_START' COMMENT '依赖类型 FINISH_TO_START/START_TO_START/FINISH_TO_FINISH/START_TO_FINISH',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_project_task_dependency (predecessor_id, successor_id),
  KEY idx_pms_project_task_dependency_successor (successor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目任务前后置依赖';

-- 3. 阶段模板表（按项目类型组织）
CREATE TABLE pms_project_phase_template (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(128) NOT NULL COMMENT '模板阶段名称',
  code varchar(64) NOT NULL COMMENT '模板阶段编码，全局唯一',
  project_type varchar(64) DEFAULT NULL COMMENT '适用项目类型',
  description varchar(500) DEFAULT NULL COMMENT '描述',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  sort int NOT NULL DEFAULT 0 COMMENT '排序号',
  entry_criteria varchar(500) DEFAULT NULL COMMENT '准入条件',
  exit_criteria varchar(500) DEFAULT NULL COMMENT '退出条件',
  responsible_role varchar(64) DEFAULT NULL COMMENT '负责角色编码',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_project_phase_template_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目阶段模板';

-- 4. 项目阶段实际表（从模板实例化或手工创建）
CREATE TABLE pms_project_phase (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  template_id bigint DEFAULT NULL COMMENT '来源阶段模板编号',
  name varchar(128) NOT NULL COMMENT '阶段名称',
  code varchar(64) NOT NULL COMMENT '阶段编码，项目内唯一',
  sort int NOT NULL DEFAULT 0 COMMENT '排序号',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0未开始 1进行中 2已完成 3已跳过',
  suggested_start_time datetime DEFAULT NULL COMMENT '建议开始时间',
  suggested_end_time datetime DEFAULT NULL COMMENT '建议结束时间',
  plan_start_time datetime DEFAULT NULL COMMENT '计划开始时间',
  plan_end_time datetime DEFAULT NULL COMMENT '计划结束时间',
  actual_start_time datetime DEFAULT NULL COMMENT '实际开始时间',
  actual_end_time datetime DEFAULT NULL COMMENT '实际结束时间',
  deviation_reason varchar(500) DEFAULT NULL COMMENT '偏差原因',
  entry_criteria varchar(500) DEFAULT NULL COMMENT '准入条件',
  exit_criteria varchar(500) DEFAULT NULL COMMENT '退出条件',
  responsible_role varchar(64) DEFAULT NULL COMMENT '负责角色编码',
  responsible_user_id bigint DEFAULT NULL COMMENT '负责用户编号',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_project_phase_project (project_id),
  UNIQUE KEY uk_pms_project_phase (project_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目阶段';

-- 5. 项目风险登记册
CREATE TABLE pms_project_risk (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  title varchar(200) NOT NULL COMMENT '风险标题',
  risk_level varchar(32) NOT NULL COMMENT '风险等级 HIGH/MEDIUM/LOW',
  risk_type varchar(64) DEFAULT NULL COMMENT '风险类型',
  cause varchar(500) DEFAULT NULL COMMENT '风险原因',
  impact varchar(500) DEFAULT NULL COMMENT '风险影响',
  mitigation varchar(1000) DEFAULT NULL COMMENT '缓解措施',
  contingency varchar(1000) DEFAULT NULL COMMENT '应急措施',
  owner_user_id bigint DEFAULT NULL COMMENT '风险负责人',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0已识别 1处理中 2已关闭 3已发生',
  warning_threshold varchar(200) DEFAULT NULL COMMENT '预警阈值',
  review_notes varchar(500) DEFAULT NULL COMMENT '复核备注',
  identified_at datetime DEFAULT NULL COMMENT '识别时间',
  closed_at datetime DEFAULT NULL COMMENT '关闭时间',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_pms_project_risk_project (project_id),
  KEY idx_pms_project_risk_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目风险';

-- 6. 菜单权限（父节点 18000 项目中心）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
(18030, '任务WBS管理', 'pms:project-task:query', 3, 30, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18031, '任务创建', 'pms:project-task:create', 3, 31, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18032, '任务更新', 'pms:project-task:update', 3, 32, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18033, '任务删除', 'pms:project-task:delete', 3, 33, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18034, '阶段模板管理', 'pms:phase-template:query', 3, 34, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18035, '阶段模板维护', 'pms:phase-template:create', 3, 35, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18036, '项目阶段管理', 'pms:project-phase:query', 3, 36, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18037, '项目阶段维护', 'pms:project-phase:update', 3, 37, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18038, '阶段门禁校验', 'pms:project-phase:gate', 3, 38, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18039, '项目风险管理', 'pms:project-risk:query', 3, 39, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18040, '风险维护', 'pms:project-risk:create', 3, 40, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18041, '项目全景', 'pms:project-panoramic:query', 3, 41, 18000, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name), permission=VALUES(permission), update_time=NOW(), deleted=b'0';
