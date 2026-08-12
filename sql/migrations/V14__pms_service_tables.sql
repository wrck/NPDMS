-- ====================================================================
-- T-V1-SRV-A / T-V1-SRV-B
-- 巡检维保域：巡检任务、规则、执行记录、离线文件、报告、问题与整改、维保状态
-- 注：超过 varchar(500) 的长文本字段统一使用 TEXT，避免 MySQL 行大小 65535 限制。
-- ====================================================================

-- 1. 巡检任务主表（FR-SRV-001 / FR-SRV-002 / FR-SRV-003 / FR-SRV-006）
CREATE TABLE pms_srv_task (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  equipment_id bigint DEFAULT NULL COMMENT '设备编号',
  code varchar(64) NOT NULL COMMENT '巡检任务编码，项目内唯一',
  name varchar(128) NOT NULL COMMENT '巡检任务名称',
  inspection_mode varchar(16) NOT NULL DEFAULT 'ONLINE' COMMENT '巡检方式 ONLINE 在线 / OFFLINE 离线',
  source_type varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 PROJECT 项目 / PLAN 服务计划 / MANUAL 手工',
  source_id bigint DEFAULT NULL COMMENT '来源业务编号',
  scheduled_time datetime DEFAULT NULL COMMENT '计划巡检时间',
  actual_time datetime DEFAULT NULL COMMENT '实际巡检时间',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1待执行 2执行中 3待确认 4已完成 5已取消',
  account_check_result varchar(1000) DEFAULT NULL COMMENT '设备账号有效性检查结果',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_task_code (project_id, code),
  KEY idx_pms_srv_task_project (project_id),
  KEY idx_pms_srv_task_status (project_id, status),
  KEY idx_pms_srv_task_equipment (equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检任务主表';

-- 2. 巡检规则库（FR-SRV-004）
CREATE TABLE pms_srv_rule (
  id bigint NOT NULL AUTO_INCREMENT,
  code varchar(64) NOT NULL COMMENT '规则编码，全局唯一',
  name varchar(128) NOT NULL COMMENT '规则名称',
  rule_type varchar(32) NOT NULL DEFAULT 'ONLINE' COMMENT '规则类型 ONLINE 在线 / OFFLINE 离线',
  rule_version varchar(32) NOT NULL DEFAULT '1.0.0' COMMENT '规则版本号',
  content text DEFAULT NULL COMMENT '规则内容（CLI命令、解析表达式、阈值、严重级别等）',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已停用',
  effective_time datetime DEFAULT NULL COMMENT '生效时间',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_rule_code (code),
  KEY idx_pms_srv_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检规则库';

-- 3. 在线巡检执行记录（FR-SRV-006）
CREATE TABLE pms_srv_execution (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属巡检任务编号',
  code varchar(64) NOT NULL COMMENT '执行编码，任务内唯一',
  rule_id bigint DEFAULT NULL COMMENT '关联规则编号',
  execution_time datetime DEFAULT NULL COMMENT '执行时间',
  executor_user_id bigint DEFAULT NULL COMMENT '执行人',
  result text DEFAULT NULL COMMENT '执行结果',
  exception_record text DEFAULT NULL COMMENT '异常记录',
  evidence_url varchar(500) DEFAULT NULL COMMENT '证据附件',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2已完成 3异常',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_execution_code (task_id, code),
  KEY idx_pms_srv_execution_task (task_id),
  KEY idx_pms_srv_execution_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线巡检执行记录';

-- 4. 离线巡检文件（FR-SRV-007）
CREATE TABLE pms_srv_offline_file (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属巡检任务编号',
  code varchar(64) NOT NULL COMMENT '文件编码，任务内唯一',
  file_url varchar(500) NOT NULL COMMENT '文件存储地址',
  file_size bigint DEFAULT NULL COMMENT '文件大小（字节）',
  file_checksum varchar(128) DEFAULT NULL COMMENT '文件校验值',
  parse_status tinyint NOT NULL DEFAULT 0 COMMENT '0待解析 1解析中 2解析成功 3解析失败',
  parse_result text DEFAULT NULL COMMENT '解析结果',
  error_detail text DEFAULT NULL COMMENT '错误明细',
  parsed_by bigint DEFAULT NULL COMMENT '解析人',
  parsed_time datetime DEFAULT NULL COMMENT '解析时间',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_offline_file_code (task_id, code),
  KEY idx_pms_srv_offline_file_task (task_id),
  KEY idx_pms_srv_offline_file_parse (task_id, parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线巡检文件';

-- 5. 巡检报告（FR-SRV-008）
CREATE TABLE pms_srv_report (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属巡检任务编号',
  code varchar(64) NOT NULL COMMENT '报告编码，任务内唯一',
  report_type varchar(32) NOT NULL DEFAULT 'STANDARD' COMMENT '报告类型 STANDARD 标准 / PDF / DOC / XML',
  content text DEFAULT NULL COMMENT '报告内容',
  snapshot text DEFAULT NULL COMMENT '巡检快照',
  generated_by bigint DEFAULT NULL COMMENT '生成人',
  generated_time datetime DEFAULT NULL COMMENT '生成时间',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已生成 2已归档',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_report_code (task_id, code),
  KEY idx_pms_srv_report_task (task_id),
  KEY idx_pms_srv_report_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检报告';

-- 6. 巡检问题与整改（FR-SRV-009 / FR-SRV-011 / FR-SRV-012）
CREATE TABLE pms_srv_issue (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属巡检任务编号',
  code varchar(64) NOT NULL COMMENT '问题编码，任务内唯一',
  name varchar(255) NOT NULL COMMENT '问题名称',
  description text DEFAULT NULL COMMENT '问题描述',
  severity varchar(8) NOT NULL DEFAULT 'M' COMMENT '严重程度 H 高 / M 中 / L 低',
  owner_user_id bigint DEFAULT NULL COMMENT '责任人',
  deadline datetime DEFAULT NULL COMMENT '整改截止时间',
  solution text DEFAULT NULL COMMENT '整改方案',
  verify_result text DEFAULT NULL COMMENT '验证结果',
  verified_by bigint DEFAULT NULL COMMENT '验证人',
  verified_time datetime DEFAULT NULL COMMENT '验证时间',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待分派 1已分派 2待验证 3已关闭 4已取消',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_issue_code (task_id, code),
  KEY idx_pms_srv_issue_task (task_id),
  KEY idx_pms_srv_issue_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检问题与整改';

-- 7. 维保状态（FR-SRV-018）
CREATE TABLE pms_srv_maintenance (
  id bigint NOT NULL AUTO_INCREMENT,
  equipment_id bigint NOT NULL COMMENT '设备编号',
  project_id bigint DEFAULT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '维保记录编码，设备内唯一',
  start_date date DEFAULT NULL COMMENT '维保开始日期',
  end_date date DEFAULT NULL COMMENT '维保结束日期',
  maintenance_status tinyint NOT NULL DEFAULT 0 COMMENT '0未生效 1生效中 2即将过期 3已过期 4已续保',
  service_level varchar(32) DEFAULT NULL COMMENT '服务等级',
  auto_calculated bit(1) NOT NULL DEFAULT b'1' COMMENT '是否自动计算',
  manual_override bit(1) NOT NULL DEFAULT b'0' COMMENT '是否手工覆盖',
  override_by bigint DEFAULT NULL COMMENT '覆盖人',
  override_time datetime DEFAULT NULL COMMENT '覆盖时间',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_srv_maintenance_code (equipment_id, code),
  KEY idx_pms_srv_maintenance_equipment (equipment_id),
  KEY idx_pms_srv_maintenance_project (project_id),
  KEY idx_pms_srv_maintenance_status (maintenance_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维保状态';
