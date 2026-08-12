-- Source: sql/mysql/bpm-2025-10-04.sql
-- Scope: BPM business table definitions only. Demo records and destructive DROP statements are intentionally excluded.

CREATE TABLE `bpm_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `name` varchar(30) NULL DEFAULT '' COMMENT '分类名',
  `code` varchar(30) NULL DEFAULT '' COMMENT '分类标志',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '分类描述',
  `status` tinyint NULL DEFAULT NULL COMMENT '分类状态',
  `sort` int NULL DEFAULT NULL COMMENT '分类排序',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程分类';

CREATE TABLE `bpm_form` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL COMMENT '表单名',
  `status` tinyint NOT NULL COMMENT '开启状态',
  `conf` text NOT NULL COMMENT '表单的配置',
  `fields` text NOT NULL COMMENT '表单项的数组',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 表单定义表';

CREATE TABLE `bpm_oa_leave` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '请假表单主键',
  `user_id` bigint NOT NULL COMMENT '申请人的用户编号',
  `type` tinyint NOT NULL COMMENT '请假类型',
  `reason` varchar(200) NOT NULL COMMENT '请假原因',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `day` tinyint NOT NULL COMMENT '请假天数',
  `status` tinyint NOT NULL COMMENT '审批结果',
  `process_instance_id` varchar(64) NULL DEFAULT NULL COMMENT '流程实例的编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA 请假申请表';

CREATE TABLE `bpm_process_definition_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `process_definition_id` varchar(64) NOT NULL COMMENT '流程定义的编号',
  `model_id` varchar(64) NOT NULL COMMENT '流程模型的编号',
  `model_type` tinyint NOT NULL DEFAULT 10 COMMENT '流程模型的类型',
  `category` varchar(64) NOT NULL COMMENT '流程分类的编码',
  `icon` varchar(512) NULL DEFAULT NULL COMMENT '图标',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '描述',
  `form_type` tinyint NOT NULL COMMENT '表单类型',
  `form_id` bigint NULL DEFAULT NULL COMMENT '表单编号',
  `form_conf` text NULL COMMENT '表单的配置',
  `form_fields` text NULL COMMENT '表单项的数组',
  `form_custom_create_path` varchar(255) NULL DEFAULT NULL COMMENT '自定义表单的提交路径',
  `form_custom_view_path` varchar(255) NULL DEFAULT NULL COMMENT '自定义表单的查看路径',
  `simple_model` text NULL COMMENT 'SIMPLE 设计器模型数据 JSON 格式',
  `sort` bigint NULL DEFAULT 0 COMMENT '排序值',
  `visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可见',
  `start_user_ids` varchar(256) NULL DEFAULT NULL COMMENT '可发起用户编号数组',
  `start_dept_ids` varchar(256) NULL DEFAULT NULL COMMENT '可发起部门编号数组',
  `manager_user_ids` varchar(256) NULL DEFAULT NULL COMMENT '可管理用户编号数组',
  `allow_cancel_running_process` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否允许撤销审批中的申请',
  `allow_withdraw_task` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否允许审批人撤回任务',
  `process_id_rule` varchar(255) NULL DEFAULT NULL COMMENT '流程 ID 规则',
  `auto_approval_type` tinyint NOT NULL DEFAULT 0 COMMENT '自动去重类型',
  `title_setting` varchar(512) NULL DEFAULT NULL COMMENT '标题设置',
  `summary_setting` varchar(512) NULL DEFAULT NULL COMMENT '摘要设置',
  `process_before_trigger_setting` varchar(1024) NULL DEFAULT NULL COMMENT '流程前置通知设置',
  `process_after_trigger_setting` varchar(1024) NULL DEFAULT NULL COMMENT '流程后置通知设置',
  `task_before_trigger_setting` varchar(1024) NULL DEFAULT NULL COMMENT '任务前置通知设置',
  `task_after_trigger_setting` varchar(1024) NULL DEFAULT NULL COMMENT '任务后置通知设置',
  `print_template_setting` varchar(4096) NULL DEFAULT NULL COMMENT '自定义打印模板设置',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程定义的信息表';

CREATE TABLE `bpm_process_expression` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL DEFAULT '' COMMENT '表达式名字',
  `status` tinyint NOT NULL COMMENT '表达式状态',
  `expression` varchar(1024) NOT NULL COMMENT '表达式',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程表达式表';

CREATE TABLE `bpm_process_instance_copy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户编号，被抄送人',
  `start_user_id` bigint NOT NULL DEFAULT 0 COMMENT '发起流程的用户编号',
  `process_instance_id` varchar(64) NOT NULL DEFAULT '' COMMENT '流程实例的编号',
  `process_instance_name` varchar(64) NOT NULL DEFAULT '' COMMENT '流程实例的名字',
  `process_definition_id` varchar(64) NOT NULL COMMENT '流程定义的编号',
  `category` varchar(64) NOT NULL COMMENT '流程定义的分类',
  `activity_id` varchar(64) NOT NULL DEFAULT '' COMMENT '流程活动的编号',
  `activity_name` varchar(64) NOT NULL COMMENT '流程活动的名字',
  `task_id` varchar(64) NULL DEFAULT '' COMMENT '流程任务的编号',
  `reason` varchar(256) NULL DEFAULT '' COMMENT '抄送意见',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程实例抄送表';

CREATE TABLE `bpm_process_listener` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(30) NOT NULL DEFAULT '' COMMENT '监听器名字',
  `type` varchar(255) NOT NULL COMMENT '监听器类型',
  `status` tinyint NOT NULL COMMENT '监听器状态',
  `event` varchar(30) NOT NULL DEFAULT '' COMMENT '监听事件',
  `value_type` varchar(64) NOT NULL DEFAULT '' COMMENT '监听器值类型',
  `value` varchar(1024) NOT NULL COMMENT '监听器值',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程监听器表';

CREATE TABLE `bpm_user_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(30) NOT NULL DEFAULT '' COMMENT '组名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '描述',
  `user_ids` varchar(1024) NULL DEFAULT NULL COMMENT '成员编号数组',
  `status` tinyint NOT NULL COMMENT '状态（0正常 1停用）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 用户组表';
