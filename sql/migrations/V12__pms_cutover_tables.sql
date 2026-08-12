-- ====================================================================
-- T-V1-CUT-A / T-V1-CUT-B
-- 割接域：割接任务、风险调研、方案、执行记录、稳定观察
-- 注：超过 varchar(500) 的长文本字段统一使用 TEXT，避免 MySQL 行大小 65535 限制。
-- ====================================================================

-- 1. 割接任务主表（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）
CREATE TABLE pms_cut_task (
  id bigint NOT NULL AUTO_INCREMENT,
  project_id bigint NOT NULL COMMENT '所属项目编号',
  code varchar(64) NOT NULL COMMENT '割接任务编码，项目内唯一',
  name varchar(128) NOT NULL COMMENT '割接任务名称',
  cutover_type varchar(32) NOT NULL DEFAULT 'REPLACE' COMMENT '割接类型 REPLACE 替换 / ACCESS 入网 / UPGRADE 升级 / DRILL 演练 / CONFIG 配置变更',
  network_mode varchar(32) DEFAULT NULL COMMENT '组网模式 VSM / DUAL / CLUSTER / SINGLE',
  source_type varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 PROJECT 项目 / ITR ITR工单 / MANUAL 手工',
  source_id bigint DEFAULT NULL COMMENT '来源业务编号',
  risk_level varchar(8) NOT NULL DEFAULT 'C' COMMENT '割接等级评估 A/B/C/D',
  scheduled_time datetime DEFAULT NULL COMMENT '计划割接时间',
  actual_time datetime DEFAULT NULL COMMENT '实际割接时间',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1准备中 2待评审 3待执行 4执行中 5稳定观察 6已完成 7已回退 8已终止',
  approval_opinion varchar(1000) DEFAULT NULL COMMENT '评审意见',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_cut_task_code (project_id, code),
  KEY idx_pms_cut_task_project (project_id),
  KEY idx_pms_cut_task_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接任务主表';

-- 2. 割接风险与调研动态清单（FR-CUT-004 / FR-CUT-006）
CREATE TABLE pms_cut_risk (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属割接任务编号',
  code varchar(64) NOT NULL COMMENT '风险编码，任务内唯一',
  name varchar(255) NOT NULL COMMENT '风险/调研项名称',
  risk_type varchar(32) NOT NULL DEFAULT 'RISK' COMMENT 'RISK 风险 / SURVEY 调研',
  description text DEFAULT NULL COMMENT '风险/调研描述',
  impact text DEFAULT NULL COMMENT '影响分析',
  mitigation text DEFAULT NULL COMMENT '缓解措施',
  owner_user_id bigint DEFAULT NULL COMMENT '责任人',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已闭环 3已挂起',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_cut_risk_code (task_id, code),
  KEY idx_pms_cut_risk_task (task_id),
  KEY idx_pms_cut_risk_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接风险与调研清单';

-- 3. 割接方案（FR-CUT-008 / FR-CUT-009）
CREATE TABLE pms_cut_plan (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属割接任务编号',
  code varchar(64) NOT NULL COMMENT '方案编码，任务内唯一',
  name varchar(128) NOT NULL COMMENT '方案名称',
  pre_check text DEFAULT NULL COMMENT '割接前检查项',
  `procedure` text DEFAULT NULL COMMENT '割接步骤',
  verification text DEFAULT NULL COMMENT '业务测试与验证',
  rollback text DEFAULT NULL COMMENT '回退方案',
  level varchar(8) DEFAULT NULL COMMENT '方案等级 A/B/C/D',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1待评审 2已通过 3已驳回 4已终止',
  approved_by bigint DEFAULT NULL COMMENT '审核人',
  approved_time datetime DEFAULT NULL COMMENT '审核时间',
  approval_opinion varchar(1000) DEFAULT NULL COMMENT '审核意见',
  baseline_version int DEFAULT NULL COMMENT '基线版本号（审核通过后冻结）',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_cut_plan_code (task_id, code),
  KEY idx_pms_cut_plan_task (task_id),
  KEY idx_pms_cut_plan_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接方案';

-- 4. 割接执行记录（FR-CUT-011 / FR-CUT-012）
CREATE TABLE pms_cut_execution (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属割接任务编号',
  code varchar(64) NOT NULL COMMENT '执行编码，任务内唯一',
  step_name varchar(255) NOT NULL COMMENT '步骤名称',
  operator_user_id bigint DEFAULT NULL COMMENT '操作人',
  operation_time datetime DEFAULT NULL COMMENT '操作时间',
  result text DEFAULT NULL COMMENT '执行结果',
  exception_record text DEFAULT NULL COMMENT '异常记录',
  evidence_url varchar(500) DEFAULT NULL COMMENT '证据附件',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2已通过 3失败 4已回退',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_cut_execution_code (task_id, code),
  KEY idx_pms_cut_execution_task (task_id),
  KEY idx_pms_cut_execution_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='割接执行记录';

-- 5. 稳定观察与遗留项（FR-CUT-013 / FR-CUT-014）
CREATE TABLE pms_cut_observation (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL COMMENT '所属割接任务编号',
  code varchar(64) NOT NULL COMMENT '观察编码，任务内唯一',
  observation_start datetime DEFAULT NULL COMMENT '观察开始时间',
  observation_end datetime DEFAULT NULL COMMENT '观察结束时间',
  observer_user_id bigint DEFAULT NULL COMMENT '观察人',
  leftover_items text DEFAULT NULL COMMENT '遗留项清单',
  leftover_status tinyint NOT NULL DEFAULT 0 COMMENT '遗留项状态 0无遗留 1待处理 2已闭环',
  conclusion text DEFAULT NULL COMMENT '观察结论',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0观察中 1已通过 2异常 3已归档',
  remark varchar(500) DEFAULT NULL,
  version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pms_cut_observation_code (task_id, code),
  KEY idx_pms_cut_observation_task (task_id),
  KEY idx_pms_cut_observation_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='稳定观察与遗留项';
