-- CUT结构化存储：经2026-10-13需求方批准的75表方案之新增表与明确字段。
-- 本迁移不清空、不改写任何既有JSON、批准版本、状态或审计原件。
-- 后续投影与切换迁移必须在同一批准部署窗口内完成；运行期不提供JSON双写/回退。
-- 字段命名遵循ADR-0019；已有列的更名及生成表达式在最终切换迁移中一并处理。

-- 配置修订根：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_configuration_revision`
    ADD COLUMN `navigation_target_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '原导航目标CURRENT_STAGE_WORKBENCH或TASK_OVERVIEW',
    ADD COLUMN `validation_valid_flag` BIT(1) NULL DEFAULT NULL COMMENT '最近一次完整性校验结论',
    ADD COLUMN `current_validation_no` INT NULL DEFAULT NULL COMMENT '最近一次校验序号；问题明细按该序号读取',
    ADD COLUMN `validate_time` DATETIME(3) NULL DEFAULT NULL COMMENT '最近一次校验时间',
    ADD COLUMN `validate_user_id` BIGINT NULL DEFAULT NULL COMMENT '最近一次校验人';

-- 统一采集项定义修订：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_checklist_item_definition_revision`
    ADD COLUMN `external_interface_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '正式集成目录的接口标识',
    ADD COLUMN `external_manual_fallback_flag` BIT(1) NULL DEFAULT NULL COMMENT '外部加载失败时是否允许人工填写/证据替代';

-- CUT-07：配置维度
CREATE TABLE `cut_configuration_dimension` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `configuration_revision_id` BIGINT NOT NULL COMMENT '所属配置修订',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
    `dimension_name` LONGTEXT NOT NULL COMMENT '维度名称',
    `data_type_code` VARCHAR(32) NOT NULL COMMENT '维度值数据类型',
    `value_source` VARCHAR(128) NOT NULL COMMENT '权威字典/公开来源引用',
    `business_owner` VARCHAR(64) NOT NULL COMMENT '已确认的数据Owner',
    `context_path` VARCHAR(255) NOT NULL COMMENT '受控任务上下文取值路径',
    `dictionary_snapshot_key` VARCHAR(128) NULL DEFAULT NULL COMMENT '旧字典引用快照的原键；仅引用型历史不补造值',
    `enabled_flag` BIT(1) NOT NULL COMMENT '是否启用',
    `sort_order` INT NOT NULL COMMENT '顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`configuration_revision_id`,`dimension_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置维度';

-- CUT-07：配置冻结字典值
CREATE TABLE `cut_configuration_dictionary_value` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `configuration_revision_id` BIGINT NOT NULL COMMENT '所属配置修订',
    `dict_type` VARCHAR(128) NOT NULL COMMENT '字典类型',
    `value_code` VARCHAR(128) NOT NULL COMMENT '当时实际字典值',
    `label` LONGTEXT NULL DEFAULT NULL COMMENT '当时实际标签；不从当前字典倒填',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `source_status_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '原件已保存的来源状态',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`configuration_revision_id`,`dict_type`,`value_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置冻结字典值';

-- CUT-04/07：配置方案章节
CREATE TABLE `cut_configuration_plan_section` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `configuration_revision_id` BIGINT NOT NULL COMMENT '所属配置修订',
    `stable_section_key` VARCHAR(96) NOT NULL COMMENT '稳定章节键',
    `title` LONGTEXT NOT NULL COMMENT '章节标题',
    `required_flag` BIT(1) NOT NULL COMMENT '是否必填',
    `sort_order` INT NOT NULL COMMENT '章节顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`configuration_revision_id`,`stable_section_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置方案章节';

-- CUT-04/07：章节适用集合
CREATE TABLE `cut_configuration_plan_section_scope` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `plan_section_id` BIGINT NOT NULL COMMENT '章节ID',
    `scope_axis_code` VARCHAR(32) NOT NULL COMMENT 'CUTOVER_TYPE或CUTOVER_LEVEL',
    `scope_value` VARCHAR(64) NOT NULL COMMENT '该轴允许的权威编码',
    `sort_order` INT NOT NULL COMMENT '原集合顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`plan_section_id`,`scope_axis_code`,`scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节适用集合';

-- CUT-07：配置校验问题明细
CREATE TABLE `cut_configuration_validation_issue` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `configuration_revision_id` BIGINT NOT NULL COMMENT '所属配置修订',
    `validation_no` INT NOT NULL COMMENT '本次校验序号',
    `issue_no` INT NOT NULL COMMENT '问题顺序',
    `location` VARCHAR(512) NOT NULL COMMENT '配置位置',
    `message` LONGTEXT NOT NULL COMMENT '校验问题说明',
    `validate_time` DATETIME(3) NOT NULL COMMENT '校验时间',
    `validate_user_id` BIGINT NOT NULL COMMENT '校验人',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`configuration_revision_id`,`validation_no`,`issue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置校验问题明细';

-- CUT-03/07/09/10：统一结构化字段定义
CREATE TABLE `cut_checklist_field_definition` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `item_definition_revision_id` BIGINT NULL DEFAULT NULL COMMENT '系统采集项既有定义修订ID',
    `custom_checklist_item_id` BIGINT NULL DEFAULT NULL COMMENT '任务自定义清单项ID',
    `parent_field_id` BIGINT NULL DEFAULT NULL COMMENT '父定义节点',
    `node_path` VARCHAR(512) NOT NULL COMMENT '修订内稳定元数据路径',
    `node_kind_code` VARCHAR(32) NOT NULL COMMENT 'ROOT、FIELD或COLUMN',
    `field_code` VARCHAR(96) NULL DEFAULT NULL COMMENT '对外字段编码；兼容原code/key来源',
    `source_name` LONGTEXT NULL DEFAULT NULL COMMENT '原字段name',
    `label` LONGTEXT NULL DEFAULT NULL COMMENT '显示标签',
    `data_type_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '控件/数据类型；包含TABLE',
    `required_flag` BIT(1) NOT NULL COMMENT '是否必填',
    `max_length` INT NULL DEFAULT NULL COMMENT '已有定义声明的长度限制',
    `value_source_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '已有受控选项/数据来源编码',
    `sort_order` INT NOT NULL COMMENT '节点顺序',
    `builtin_field_code` VARCHAR(96) NULL DEFAULT NULL COMMENT '服务端声明的固定业务语义键；不是SQL列名',
    `extension_field_key` VARCHAR(96) NULL DEFAULT NULL COMMENT '真正自定义字段键；不得占用内置键',
    `visible_when_field_path` VARCHAR(512) NULL DEFAULT NULL COMMENT '原visibleWhen.field对应的同对象字段路径',
    `visible_equals_type_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '原equals的标量类型',
    `visible_equals_text` LONGTEXT NULL DEFAULT NULL COMMENT '字符串相等条件',
    `visible_equals_number` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '数字相等条件',
    `visible_equals_boolean` BIT(1) NULL DEFAULT NULL COMMENT '布尔相等条件',
    `visible_equals_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间相等条件',
    `source_file` LONGTEXT NULL DEFAULT NULL COMMENT '原检查来源文件',
    `source_sheet` LONGTEXT NULL DEFAULT NULL COMMENT '原检查来源页签',
    `source_row` INT NULL DEFAULT NULL COMMENT '原检查来源行',
    `source_original_name` LONGTEXT NULL DEFAULT NULL COMMENT '原检查项名称',
    `source_part` INT NULL DEFAULT NULL COMMENT '原拆分序号',
    `source_split_flag` BIT(1) NULL DEFAULT NULL COMMENT '是否由原条目拆分',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`item_definition_revision_id`,`node_path`),
    UNIQUE KEY `uk_business_2` (`tenant_id`,`custom_checklist_item_id`,`node_path`),
    CONSTRAINT `chk_checklist_field_definition_1` CHECK ((item_definition_revision_id IS NOT NULL) <> (custom_checklist_item_id IS NOT NULL)),
    CONSTRAINT `chk_checklist_field_definition_2` CHECK (builtin_field_code IS NULL OR extension_field_key IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一结构化字段定义';

-- CUT-07：字段选项
CREATE TABLE `cut_checklist_field_option` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `field_definition_id` BIGINT NOT NULL COMMENT '字段定义ID',
    `option_no` INT NOT NULL COMMENT '选项顺序',
    `label` LONGTEXT NULL DEFAULT NULL COMMENT '原选项标签',
    `value_type_code` VARCHAR(32) NOT NULL COMMENT '动态标量实际类型；只用于允许配置的元数据/参数',
    `value_text` LONGTEXT NULL DEFAULT NULL COMMENT '字符串型标量实际值',
    `value_number` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '数字型标量实际值',
    `value_boolean` BIT(1) NULL DEFAULT NULL COMMENT '布尔型标量实际值',
    `value_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间型标量实际值',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`field_definition_id`,`option_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段选项';

-- CUT-03/07：规则维度允许值
CREATE TABLE `cut_checklist_binding_condition` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `binding_rule_revision_id` BIGINT NOT NULL COMMENT '规则修订ID',
    `configuration_dimension_id` BIGINT NOT NULL COMMENT '维度定义ID',
    `value_no` INT NOT NULL COMMENT '同维度允许值次序',
    `value_type_code` VARCHAR(32) NOT NULL COMMENT '动态标量实际类型；只用于允许配置的元数据/参数',
    `value_text` LONGTEXT NULL DEFAULT NULL COMMENT '字符串型标量实际值',
    `value_number` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '数字型标量实际值',
    `value_boolean` BIT(1) NULL DEFAULT NULL COMMENT '布尔型标量实际值',
    `value_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间型标量实际值',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`binding_rule_revision_id`,`configuration_dimension_id`,`value_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则维度允许值';

-- CUT-03/07/09：外部参数与返回映射
CREATE TABLE `cut_checklist_external_mapping` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `item_definition_revision_id` BIGINT NOT NULL COMMENT '所属采集项定义修订',
    `mapping_direction_code` VARCHAR(16) NOT NULL COMMENT 'PARAM或RETURN',
    `mapping_no` INT NOT NULL COMMENT '映射顺序',
    `source_path` VARCHAR(512) NOT NULL COMMENT '受控上下文/外部响应字段路径',
    `target_key` VARCHAR(128) NOT NULL COMMENT '参数名或服务端声明的结果业务键',
    `value_type_code` VARCHAR(32) NOT NULL COMMENT '正式契约声明的数据类型',
    `required_flag` BIT(1) NOT NULL COMMENT '是否必需',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`item_definition_revision_id`,`mapping_direction_code`,`target_key`),
    CONSTRAINT `chk_checklist_external_mapping_1` CHECK (mapping_direction_code IN ('PARAM','RETURN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部参数与返回映射';

-- 割接任务：只增加原快照中已定义的业务列。
ALTER TABLE `cut_task`
    ADD COLUMN `project_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源项目编码；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源项目名称；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `customer_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源客户编码',
    ADD COLUMN `customer_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源客户名称',
    ADD COLUMN `department_id` BIGINT NULL DEFAULT NULL COMMENT '来源部门ID',
    ADD COLUMN `department_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源部门编码',
    ADD COLUMN `department_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源部门名称',
    ADD COLUMN `customer_fact_status_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '原客户等级事实状态；原无事实时不造默认状态',
    ADD COLUMN `service_level_revision_id` BIGINT NULL DEFAULT NULL COMMENT '客户服务等级修订引用',
    ADD COLUMN `service_level_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源服务等级编码',
    ADD COLUMN `customer_fact_version` BIGINT NULL DEFAULT NULL COMMENT 'CustomerLevelFact.factVersion；保留long类型',
    ADD COLUMN `service_level_effective_start_time` DATETIME(3) NULL DEFAULT NULL COMMENT '来源等级生效时间',
    ADD COLUMN `service_level_effective_end_time` DATETIME(3) NULL DEFAULT NULL COMMENT '来源等级失效时间',
    ADD COLUMN `readiness_decision_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '所引用就绪快照的原结论；独立准入不造READY';

-- 评估修订：只增加原快照中已定义的业务列。
ALTER TABLE `cut_assessment`
    ADD COLUMN `business_importance_level` VARCHAR(64) NULL DEFAULT NULL COMMENT '填写值：业务重要程度',
    ADD COLUMN `operation_complexity_level` VARCHAR(64) NULL DEFAULT NULL COMMENT '填写值：操作复杂程度',
    ADD COLUMN `hidden_risk_level` VARCHAR(64) NULL DEFAULT NULL COMMENT '填写值：隐患程度',
    ADD COLUMN `spare_part_applied_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否申请备件',
    ADD COLUMN `intake_source_type_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '本次评估绑定的准入来源类型；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `implementation_readiness_snapshot_id` BIGINT NULL DEFAULT NULL COMMENT 'IMP不可变就绪快照ID',
    ADD COLUMN `implementation_readiness_snapshot_version` BIGINT NULL DEFAULT NULL COMMENT 'IMP快照版本',
    ADD COLUMN `readiness_decision_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '原就绪结论',
    ADD COLUMN `project_id` BIGINT NULL DEFAULT NULL COMMENT '来源项目ID；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源项目编码；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源项目名称；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `customer_id` BIGINT NULL DEFAULT NULL COMMENT '来源客户ID；原无客户时为空',
    ADD COLUMN `customer_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源客户编码',
    ADD COLUMN `customer_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源客户名称',
    ADD COLUMN `department_id` BIGINT NULL DEFAULT NULL COMMENT '来源部门ID',
    ADD COLUMN `department_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源部门编码',
    ADD COLUMN `department_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源部门名称',
    ADD COLUMN `project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `customer_fact_status_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '原客户等级事实状态；原无事实时不造默认状态',
    ADD COLUMN `service_level_revision_id` BIGINT NULL DEFAULT NULL COMMENT '客户服务等级修订引用',
    ADD COLUMN `service_level_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源服务等级编码',
    ADD COLUMN `customer_fact_version` BIGINT NULL DEFAULT NULL COMMENT 'CustomerLevelFact.factVersion；保留long类型',
    ADD COLUMN `service_level_effective_start_time` DATETIME(3) NULL DEFAULT NULL COMMENT '来源等级生效时间',
    ADD COLUMN `service_level_effective_end_time` DATETIME(3) NULL DEFAULT NULL COMMENT '来源等级失效时间';

-- CUT-02；CUT-03：评估冻结设备事实
CREATE TABLE `cut_assessment_device` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `assessment_id` BIGINT NOT NULL COMMENT '既有评估修订ID',
    `task_device_scope_id` BIGINT NULL DEFAULT NULL COMMENT '对应原任务范围行',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `project_id` BIGINT NOT NULL COMMENT '当时归属项目',
    `sn` VARCHAR(128) NOT NULL COMMENT '当时序列号',
    `project_assignment_version` BIGINT NOT NULL COMMENT '当时归属水位',
    `device_type_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '原设备类型编码',
    `device_type_source_version` VARCHAR(128) NULL DEFAULT NULL COMMENT '原设备类型来源版本，不透明字符串',
    `product_type_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '原产品类型编码',
    `product_type_source_version` VARCHAR(128) NULL DEFAULT NULL COMMENT '原产品类型来源版本，不透明字符串',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`assessment_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评估冻结设备事实';

-- 统一清单修订：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_checklist`
    ADD COLUMN `source_task_version` BIGINT NULL DEFAULT NULL COMMENT '生成时任务并发版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '生成时项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `configuration_revision_id` BIGINT NULL DEFAULT NULL COMMENT '消费的配置修订ID；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `configuration_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '消费的配置编码；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `configuration_revision_no` INT NULL DEFAULT NULL COMMENT '消费的配置修订号；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `config_gap_reason_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '原缺口原因编码';

-- 统一结果主表：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_checklist_item_result`
    ADD COLUMN `field_definition_id` BIGINT NULL DEFAULT NULL COMMENT '写入时的精确根字段定义；清单项重新匹配后仍按此定义解释旧结果，新结果必填',
    ADD COLUMN `base_checklist_result_id` BIGINT NULL DEFAULT NULL COMMENT '被分析/采用的不可变原结果ID',
    ADD COLUMN `device_id` BIGINT NULL DEFAULT NULL COMMENT '单设备来源对应的设备；多设备外部查询按查询关系逐个保存',
    ADD COLUMN `command_template_id` BIGINT NULL DEFAULT NULL COMMENT '本次实际采集命令模板ID',
    ADD COLUMN `collection_log_id` BIGINT NULL DEFAULT NULL COMMENT '本次采用的采集回执',
    ADD COLUMN `collection_status_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '采用时采集状态事实',
    ADD COLUMN `collection_failure_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '采用时采集失败编码',
    ADD COLUMN `original_failed_collection_task_id` BIGINT NULL DEFAULT NULL COMMENT '人工替代对应的失败采集任务',
    ADD COLUMN `original_failed_result_version` BIGINT NULL DEFAULT NULL COMMENT '原失败结果版本',
    ADD COLUMN `original_failure_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '原失败原因';

-- CUT-03：清单匹配输入维度
CREATE TABLE `cut_checklist_input_dimension` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_id` BIGINT NOT NULL COMMENT '清单修订ID',
    `configuration_dimension_id` BIGINT NOT NULL COMMENT '维度定义ID',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '原维度编码',
    `value_no` INT NOT NULL COMMENT '同维度值次序',
    `value_type_code` VARCHAR(32) NOT NULL COMMENT '动态标量实际类型；只用于允许配置的元数据/参数',
    `value_text` LONGTEXT NULL DEFAULT NULL COMMENT '字符串型标量实际值',
    `value_number` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '数字型标量实际值',
    `value_boolean` BIT(1) NULL DEFAULT NULL COMMENT '布尔型标量实际值',
    `value_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间型标量实际值',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_id`,`configuration_dimension_id`,`value_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='清单匹配输入维度';

-- CUT-03：实际命中规则与选择
CREATE TABLE `cut_checklist_match_candidate` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_id` BIGINT NOT NULL COMMENT '清单修订ID',
    `stable_item_key` VARCHAR(96) NOT NULL COMMENT '采集项稳定键',
    `item_definition_revision_id` BIGINT NOT NULL COMMENT '精确定义修订ID',
    `binding_rule_revision_id` BIGINT NOT NULL COMMENT '精确规则修订ID',
    `binding_rule_version` BIGINT NOT NULL COMMENT '匹配时规则版本',
    `priority` INT NOT NULL COMMENT '匹配时优先级',
    `required_result_flag` BIT(1) NOT NULL COMMENT '本次规则要求的必选性',
    `selected_flag` BIT(1) NOT NULL COMMENT '本次是否采用该候选',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_id`,`binding_rule_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实际命中规则与选择';

-- CUT-03/09：P3设备及软件版本上下文
CREATE TABLE `cut_checklist_device_context` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_id` BIGINT NOT NULL COMMENT '清单修订ID',
    `assessment_device_id` BIGINT NOT NULL COMMENT '评估冻结设备行',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `product_model` LONGTEXT NULL DEFAULT NULL COMMENT '该设备实际型号上下文',
    `current_software_version` LONGTEXT NULL DEFAULT NULL COMMENT '填写/选择的当前软件版本',
    `target_software_version` LONGTEXT NULL DEFAULT NULL COMMENT '版本升级时填写/选择的目标版本',
    `sort_order` INT NOT NULL COMMENT '显示顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='P3设备及软件版本上下文';

-- CUT-03/09/10；PLT文件：统一结果文件引用
CREATE TABLE `cut_checklist_result_file` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '统一结果ID',
    `custom_result_row_id` BIGINT NULL DEFAULT NULL COMMENT '真正自定义表格中的所属行；固定表格目前无文件列，不加多态表名/行ID',
    `field_definition_id` BIGINT NULL DEFAULT NULL COMMENT '所属文件字段定义；整个FILE项时引用根定义',
    `file_role_code` VARCHAR(64) NOT NULL COMMENT '正常结果文件或人工降级证据的既有角色',
    `file_no` INT NOT NULL COMMENT '本结果内文件序号',
    `file_artifact_id` BIGINT NOT NULL COMMENT 'PLT工件ID；文件本体仍由PLT保存',
    `file_version_no` INT NOT NULL COMMENT '固定文件版本号',
    `file_reference_key` VARCHAR(128) NOT NULL COMMENT 'PLT业务引用键',
    `file_artifact_fact_version` BIGINT NOT NULL COMMENT '文件工件事实水位',
    `file_reference_fact_version` BIGINT NOT NULL COMMENT '文件引用事实水位',
    `file_availability_fact_version` BIGINT NOT NULL COMMENT '文件可用性事实水位',
    `file_scope_version` BIGINT NOT NULL COMMENT '登记时范围版本；不据此否定当前合法下载',
    `file_sha256` CHAR(64) NOT NULL COMMENT '已有文件内容摘要',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`file_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一结果文件引用';

-- CUT-03/07/09：外部加载记录
CREATE TABLE `cut_checklist_external_query` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_item_id` BIGINT NOT NULL COMMENT '发起加载的清单项',
    `item_definition_revision_id` BIGINT NOT NULL COMMENT '使用的定义修订',
    `device_id` BIGINT NOT NULL COMMENT '查询对应设备',
    `interface_code` VARCHAR(128) NOT NULL COMMENT '使用的正式接口标识',
    `query_no` INT NOT NULL COMMENT '项内查询序号',
    `version_context_code` VARCHAR(32) NOT NULL COMMENT 'CURRENT或TARGET等正式受控查询语境',
    `queried_software_version` LONGTEXT NULL DEFAULT NULL COMMENT '本次实际查询软件版本',
    `product_model` LONGTEXT NULL DEFAULT NULL COMMENT '本次实际设备型号',
    `request_id` VARCHAR(128) NOT NULL COMMENT '本次调用的幂等/跟踪身份',
    `query_user_id` BIGINT NOT NULL COMMENT '发起查询人',
    `start_time` DATETIME(3) NOT NULL COMMENT '开始时间',
    `finish_time` DATETIME(3) NULL DEFAULT NULL COMMENT '完成时间',
    `succeeded_flag` BIT(1) NULL DEFAULT NULL COMMENT 'NULL未完成、true成功、false失败；不是CUT业务状态',
    `failure_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '实际失败编码',
    `failure_detail` LONGTEXT NULL DEFAULT NULL COMMENT '脱敏失败说明',
    `returned_row_count` INT NULL DEFAULT NULL COMMENT '成功返回的实际行数；0不能与失败混用',
    `loaded_checklist_result_id` BIGINT NULL DEFAULT NULL COMMENT '首次装载的统一结果ID',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_item_id`,`query_no`),
    UNIQUE KEY `uk_business_2` (`tenant_id`,`request_id`),
    CONSTRAINT `chk_checklist_external_query_1` CHECK ((succeeded_flag IS NULL AND finish_time IS NULL AND returned_row_count IS NULL) OR (succeeded_flag = b'1' AND finish_time IS NOT NULL AND returned_row_count IS NOT NULL AND returned_row_count >= 0 AND failure_code IS NULL) OR (succeeded_flag = b'0' AND finish_time IS NOT NULL AND failure_code IS NOT NULL AND returned_row_count IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部加载记录';

-- CUT-03/07/09：外部查询实际参数
CREATE TABLE `cut_checklist_external_query_parameter` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `external_query_id` BIGINT NOT NULL COMMENT '所属查询',
    `mapping_id` BIGINT NOT NULL COMMENT '使用的配置映射',
    `parameter_name` VARCHAR(128) NOT NULL COMMENT '正式接口参数名',
    `value_type_code` VARCHAR(32) NOT NULL COMMENT '动态标量实际类型；只用于允许配置的元数据/参数',
    `value_text` LONGTEXT NULL DEFAULT NULL COMMENT '字符串型标量实际值',
    `value_number` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '数字型标量实际值',
    `value_boolean` BIT(1) NULL DEFAULT NULL COMMENT '布尔型标量实际值',
    `value_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间型标量实际值',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`external_query_id`,`parameter_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部查询实际参数';

-- CUT-03/09：结果采用的查询集合
CREATE TABLE `cut_checklist_result_query_reference` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '统一结果ID',
    `external_query_id` BIGINT NOT NULL COMMENT '被采用的查询记录',
    `reference_purpose_code` VARCHAR(32) NOT NULL COMMENT '正常来源或人工替代失败来源',
    `sort_order` INT NOT NULL COMMENT '查询来源展示顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`external_query_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结果采用的查询集合';

-- CUT-03/10：割接背景答案
CREATE TABLE `cut_result_cutover_background` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `solves_online_issue_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否解决网上问题',
    `issue_ticket_no` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：问题工单号',
    `issue_handler` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：工单处理人',
    `repeat_cutover_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否二次及以上割接',
    `first_cutover_owner` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：首次割接保障人',
    `background_description` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：割接背景表述',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='割接背景答案';

-- CUT-03/10：业务简述答案
CREATE TABLE `cut_result_business_summary` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `business_description` TEXT NULL DEFAULT NULL COMMENT '填写值：业务简述；按精确定义保留原长度限制',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务简述答案';

-- CUT-03/10：业务影响范围答案
CREATE TABLE `cut_result_impact_scope` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `impact_scope_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '填写值：原选项的实际值，不重编码',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务影响范围答案';

-- CUT-03/10：连续性要求答案
CREATE TABLE `cut_result_continuity_requirement` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `continuity_requirement_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '填写值：原连续性选项实际值',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续性要求答案';

-- CUT-03/10：中断次数答案
CREATE TABLE `cut_result_interruption` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `interruption_count` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '填写值：中断次数；保持现有NUMBER语义，不新增整数/阈值限制',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='中断次数答案';

-- CUT-03/09：风险及双机检查反馈
CREATE TABLE `cut_result_check_feedback` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `check_normal_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否正常/符合本次部署要求',
    `executed_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否执行；仅对应定义包含该字段时使用',
    `remark` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：事实说明与特殊情况备注',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风险及双机检查反馈';

-- CUT-03/08/09：重大割接备件判断答案
CREATE TABLE `cut_result_spare_reference` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `major_cutover_flag` BIT(1) NULL DEFAULT NULL COMMENT '填写值：是否重大割接',
    `spare_reference_text` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：备件引用或人工准备说明',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='重大割接备件判断答案';

-- CUT-03/09：公告查询总体反馈
CREATE TABLE `cut_result_bulletin_summary` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `has_bulletins_flag` BIT(1) NULL DEFAULT NULL COMMENT '实际是否存在公告；成功查询可据列表计算，失败不能写false',
    `manual_query_basis` LONGTEXT NULL DEFAULT NULL COMMENT '人工填写的查询版本/来源/时间依据；自动查询依据由查询关系提供',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公告查询总体反馈';

-- CUT-03/10：设备部署位置结果行
CREATE TABLE `cut_result_device_location` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `device_type` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：设备类型',
    `sn` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：序列号',
    `device_name` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：设备名称',
    `location` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：部署/安装位置',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`row_no`),
    CONSTRAINT `chk_result_device_location_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备部署位置结果行';

-- CUT-03/10：接口互联规划结果行
CREATE TABLE `cut_result_interface_plan` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `local_device` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：本端设备',
    `local_interface` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：本端接口',
    `remote_interface` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：对端接口',
    `interface_type` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：接口类型',
    `cable_model` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：线缆或模块型号',
    `remark` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：本行备注',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`row_no`),
    CONSTRAINT `chk_result_interface_plan_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口互联规划结果行';

-- CUT-03/10：IP与网关规划结果行
CREATE TABLE `cut_result_ip_plan` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `device_name` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：设备名称',
    `interface_name` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：接口',
    `ip_address` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：IP地址',
    `subnet_mask` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：掩码',
    `vrrp_ip` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：VRRP地址',
    `gateway` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：网关',
    `remark` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：本行备注',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`row_no`),
    CONSTRAINT `chk_result_ip_plan_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP与网关规划结果行';

-- CUT-03/10：性能调研结果行
CREATE TABLE `cut_result_performance` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `sn` LONGTEXT NULL DEFAULT NULL COMMENT '填写值：设备序列号',
    `concurrent_connections` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '填写值：并发连接数',
    `new_connections` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '填写值：新建连接数',
    `cpu_usage` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '填写值：CPU使用率',
    `memory_usage` DECIMAL(65,30) NULL DEFAULT NULL COMMENT '填写值：内存使用率',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`row_no`),
    CONSTRAINT `chk_result_performance_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='性能调研结果行';

-- CUT-03/09：技术公告及逐条反馈结果行
CREATE TABLE `cut_result_technical_bulletin` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `external_query_id` BIGINT NULL DEFAULT NULL COMMENT '原始公告所来自的查询；须在本结果采用的查询集合内',
    `source_bulletin_row_id` BIGINT NULL DEFAULT NULL COMMENT '反馈版本引用的首次外部原始行；同本表自关联，不指向反馈行',
    `external_record_id` VARCHAR(128) NULL DEFAULT NULL COMMENT '正式来源提供的记录ID，不以行号伪造',
    `external_record_version` VARCHAR(128) NULL DEFAULT NULL COMMENT '正式来源提供的源版本',
    `bulletin_number` LONGTEXT NULL DEFAULT NULL COMMENT '实际公告编号；外部原始值只读',
    `bulletin_subject` LONGTEXT NULL DEFAULT NULL COMMENT '实际公告主题；外部原始值只读',
    `bulletin_description` LONGTEXT NULL DEFAULT NULL COMMENT '实际公告描述；外部原始值只读',
    `bulletin_solution` LONGTEXT NULL DEFAULT NULL COMMENT '实际解决方案；外部原始值只读',
    `involved_flag` BIT(1) NULL DEFAULT NULL COMMENT '工程师填写值：本次是否涉及',
    `measures` LONGTEXT NULL DEFAULT NULL COMMENT '工程师填写值：本次采取措施',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`row_no`),
    CONSTRAINT `chk_result_technical_bulletin_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技术公告及逐条反馈结果行';

-- CUT-03/07/10：真正自定义的对象/单值答案
CREATE TABLE `cut_result_custom_value` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `field_definition_id` BIGINT NOT NULL COMMENT '精确自定义根/对象字段定义',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '仅真正自定义的非表格字段值；内置业务键和文件关系禁止进入',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`),
    CONSTRAINT `chk_result_custom_value_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='真正自定义的对象/单值答案';

-- CUT-03/07/10：真正自定义的表格结果行
CREATE TABLE `cut_result_custom_row` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `checklist_result_id` BIGINT NOT NULL COMMENT '所属统一结果版本；与根同事务写入',
    `table_field_definition_id` BIGINT NOT NULL COMMENT '整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义',
    `row_no` INT NOT NULL COMMENT '同一结果、同一表格内的行号',
    `parent_custom_row_id` BIGINT NULL DEFAULT NULL COMMENT '自定义嵌套表格的父行；同结果且无环',
    `parent_custom_row_key` BIGINT GENERATED ALWAYS AS (COALESCE(parent_custom_row_id,0)) STORED COMMENT '生成列；无父行为0，其余为父行ID；生成表达式 COALESCE(parent_custom_row_id,0)',
    `extension_values` JSON NULL DEFAULT NULL COMMENT '该自定义表格一行的真正自定义字段值；不装整张固定业务表',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`checklist_result_id`,`table_field_definition_id`,`parent_custom_row_key`,`row_no`),
    CONSTRAINT `chk_result_custom_row_1` CHECK (extension_values IS NULL OR JSON_TYPE(extension_values) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='真正自定义的表格结果行';

-- 割接方案修订：只增加原快照中已定义的业务列。
ALTER TABLE `cut_plan_revision`
    ADD COLUMN `source_task_version` BIGINT NULL DEFAULT NULL COMMENT '提交来源任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_project_id` BIGINT NULL DEFAULT NULL COMMENT '来源项目ID；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_project_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_cutover_type_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '来源割接类型，决定模板章节适用性；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `legacy_task_id` BIGINT NULL DEFAULT NULL COMMENT '旧方案原始任务ID；与目标任务身份分别保留',
    ADD COLUMN `legacy_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '旧方案原始编码',
    ADD COLUMN `legacy_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '旧方案原始名称',
    ADD COLUMN `legacy_grade_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '旧方案原始等级',
    ADD COLUMN `legacy_remark` TEXT NULL DEFAULT NULL COMMENT '旧方案原始备注',
    ADD COLUMN `project_description` TEXT NULL DEFAULT NULL COMMENT '填写值：项目情况/方案概述',
    ADD COLUMN `rollback_description` TEXT NULL DEFAULT NULL COMMENT '填写值：回退方案说明',
    ADD COLUMN `customer_contact_id` BIGINT NULL DEFAULT NULL COMMENT 'CUS客户联系人公开引用',
    ADD COLUMN `customer_contact_version` BIGINT NULL DEFAULT NULL COMMENT '联系人事实版本',
    ADD COLUMN `file_artifact_fact_version` BIGINT NULL DEFAULT NULL COMMENT '完整上传文件的工件事实水位',
    ADD COLUMN `file_reference_fact_version` BIGINT NULL DEFAULT NULL COMMENT '完整上传文件的引用事实水位',
    ADD COLUMN `file_availability_fact_version` BIGINT NULL DEFAULT NULL COMMENT '完整上传文件的可用性水位';

-- 方案步骤：只增加原快照中已定义的业务列。
ALTER TABLE `cut_step`
    ADD COLUMN `step_name` TEXT NULL DEFAULT NULL COMMENT '填写值：步骤名称',
    ADD COLUMN `test_result` TEXT NULL DEFAULT NULL COMMENT '填写值：测试结果',
    ADD COLUMN `business_impact_time` TEXT NULL DEFAULT NULL COMMENT '填写值：业务影响时间说明',
    ADD COLUMN `operator_name` TEXT NULL DEFAULT NULL COMMENT '填写值：操作人员说明',
    ADD COLUMN `operation_time` TEXT NULL DEFAULT NULL COMMENT '填写值：操作时间说明，保持自由文本';

-- 方案保障安排基线：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_support_arrangement`
    ADD COLUMN `current_support_contact_change_id` BIGINT NULL DEFAULT NULL COMMENT '当前采用的批准后联系信息变更记录';

-- CUT-04：方案计划安排行
CREATE TABLE `cut_plan_schedule` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `plan_revision_id` BIGINT NOT NULL COMMENT '既有方案修订ID',
    `sequence_no` INT NOT NULL COMMENT '计划行序号',
    `plan_time` DATETIME(3) NOT NULL COMMENT '填写值：计划时间',
    `content` TEXT NOT NULL COMMENT '填写值：计划内容',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`plan_revision_id`,`sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='方案计划安排行';

-- CUT-04：方案设备资料行
CREATE TABLE `cut_plan_device` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `plan_revision_id` BIGINT NOT NULL COMMENT '既有方案修订ID',
    `assessment_device_id` BIGINT NULL DEFAULT NULL COMMENT '评估冻结设备行',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `sn` VARCHAR(128) NOT NULL COMMENT '来源序列号',
    `project_assignment_version` BIGINT NOT NULL COMMENT '来源归属水位',
    `device_type_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源设备类型',
    `device_type_source_version` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源设备类型版本',
    `product_model` TEXT NULL DEFAULT NULL COMMENT '填写值：方案设备型号',
    `current_software_version` TEXT NULL DEFAULT NULL COMMENT '填写值：方案当前版本',
    `target_software_version` TEXT NULL DEFAULT NULL COMMENT '填写值：方案目标版本',
    `remark` TEXT NULL DEFAULT NULL COMMENT '填写值：备注',
    `sort_order` INT NOT NULL COMMENT '顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`plan_revision_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='方案设备资料行';

-- CUT-04/05；PLT文件：方案固定槽位文件
CREATE TABLE `cut_plan_file` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `plan_revision_id` BIGINT NOT NULL COMMENT '既有方案修订ID',
    `file_role_code` VARCHAR(64) NOT NULL COMMENT '既有附件槽位',
    `file_artifact_id` BIGINT NOT NULL COMMENT 'PLT工件ID；文件本体仍由PLT保存',
    `file_version_no` INT NOT NULL COMMENT '固定文件版本号',
    `file_reference_key` VARCHAR(128) NOT NULL COMMENT 'PLT业务引用键',
    `file_artifact_fact_version` BIGINT NOT NULL COMMENT '文件工件事实水位',
    `file_reference_fact_version` BIGINT NOT NULL COMMENT '文件引用事实水位',
    `file_availability_fact_version` BIGINT NOT NULL COMMENT '文件可用性事实水位',
    `file_scope_version` BIGINT NOT NULL COMMENT '登记时范围版本；不据此否定当前合法下载',
    `file_sha256` CHAR(64) NOT NULL COMMENT '已有文件内容摘要',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`plan_revision_id`,`file_role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='方案固定槽位文件';

-- CUT-04/05：方案逐项风险应对
CREATE TABLE `cut_plan_risk_mitigation` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `plan_revision_id` BIGINT NOT NULL COMMENT '既有方案修订ID',
    `checklist_result_id` BIGINT NOT NULL COMMENT '当时采用的统一结果ID',
    `checklist_item_id` BIGINT NOT NULL COMMENT '对应采集项',
    `checklist_result_version` INT NOT NULL COMMENT '当时结果版本号',
    `mitigation` TEXT NULL DEFAULT NULL COMMENT '填写值：本项应对措施；草稿尚未填写时为空，风险来源引用仍保留',
    `sort_order` INT NOT NULL COMMENT '顺序',
    `creator` VARCHAR(64) NOT NULL COMMENT '创建审计账号；服务端写入',
    `create_time` DATETIME(3) NOT NULL COMMENT '创建审计时间',
    `updater` VARCHAR(64) NOT NULL COMMENT '更新审计账号；服务端写入',
    `update_time` DATETIME(3) NOT NULL COMMENT '更新审计时间',
    `deleted` BIT(1) NOT NULL COMMENT '草稿明细逻辑删除标记；已发布或已提交历史不开放删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`plan_revision_id`,`checklist_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='方案逐项风险应对';

-- CUT-04/05：批准后保障联系信息变更
CREATE TABLE `cut_plan_support_contact_change` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `support_arrangement_id` BIGINT NOT NULL COMMENT '原保障安排',
    `change_no` INT NOT NULL COMMENT '变更序号',
    `previous_change_id` BIGINT NULL DEFAULT NULL COMMENT '前一次联系信息变更',
    `before_person_name` VARCHAR(128) NOT NULL COMMENT '变更前人员',
    `before_phone` VARCHAR(64) NOT NULL COMMENT '变更前电话',
    `before_arrive_time` DATETIME(3) NOT NULL COMMENT '变更前到位时间',
    `before_customer_contact_id` BIGINT NULL DEFAULT NULL COMMENT '客户角色变更前CUS联系人引用',
    `before_customer_contact_version` BIGINT NULL DEFAULT NULL COMMENT '客户角色变更前CUS联系人版本',
    `after_person_name` VARCHAR(128) NOT NULL COMMENT '填写值：变更后人员',
    `after_phone` VARCHAR(64) NOT NULL COMMENT '填写值：变更后电话',
    `after_arrive_time` DATETIME(3) NOT NULL COMMENT '填写值：变更后到位时间',
    `after_customer_contact_id` BIGINT NULL DEFAULT NULL COMMENT '客户角色变更后CUS联系人引用',
    `after_customer_contact_version` BIGINT NULL DEFAULT NULL COMMENT '客户角色变更后CUS联系人版本',
    `reason` TEXT NULL DEFAULT NULL COMMENT '填写值：变更原因；当前接口必填，历史原件及既有无原因参数接口缺失时不补造',
    `operate_user_id` BIGINT NOT NULL COMMENT '实际操作者',
    `operate_time` DATETIME(3) NOT NULL COMMENT '实际操作时间',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`support_arrangement_id`,`change_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批准后保障联系信息变更';

-- 审批实例：只增加原快照中已定义的业务列。
ALTER TABLE `cut_approval_instance`
    ADD COLUMN `source_task_version` BIGINT NULL DEFAULT NULL COMMENT '提交时任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_plan_version` BIGINT NULL DEFAULT NULL COMMENT '提交时方案并发版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_cutover_type_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '当时割接类型；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_network_mode_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '当时组网模式',
    ADD COLUMN `source_schedule_time` DATETIME(3) NULL DEFAULT NULL COMMENT '当时计划时间；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `lead_rule_version` VARCHAR(128) NULL DEFAULT NULL COMMENT '提前时间规则版本',
    ADD COLUMN `lead_timezone_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '提前时间计算时区',
    ADD COLUMN `lead_plan_submit_time` DATETIME(3) NULL DEFAULT NULL COMMENT '方案提交时间',
    ADD COLUMN `lead_required_days` INT NULL DEFAULT NULL COMMENT '原规则所需自然日数',
    ADD COLUMN `lead_actual_natural_days` INT NULL DEFAULT NULL COMMENT '原实际自然日数',
    ADD COLUMN `lead_late_submission_flag` BIT(1) NULL DEFAULT NULL COMMENT '原提前时间不足结论',
    ADD COLUMN `project_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源项目编码；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源项目名称；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `customer_id` BIGINT NULL DEFAULT NULL COMMENT '来源客户ID；原无客户时为空',
    ADD COLUMN `customer_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源客户编码',
    ADD COLUMN `customer_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源客户名称',
    ADD COLUMN `department_id` BIGINT NULL DEFAULT NULL COMMENT '来源部门ID',
    ADD COLUMN `department_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '来源部门编码',
    ADD COLUMN `department_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '来源部门名称',
    ADD COLUMN `project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '来源项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- 审批节点：只增加原快照中已定义的业务列。
ALTER TABLE `cut_approval_node`
    ADD COLUMN `approval_mode_code` VARCHAR(16) NULL DEFAULT NULL COMMENT 'ROLE或PERSONAL技术办理模式，不新增业务审批节点；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `candidate_role_id` BIGINT NULL DEFAULT NULL COMMENT 'SYSTEM实际角色ID',
    ADD COLUMN `candidate_role_code` VARCHAR(64) NULL DEFAULT NULL COMMENT 'SYSTEM实际角色编码',
    ADD COLUMN `candidate_group_key` VARCHAR(128) NULL DEFAULT NULL COMMENT 'BPM原生候选组键',
    ADD COLUMN `qualification_kind_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '单人资格来源种类：发起人项目权限或当前项目服务经理',
    ADD COLUMN `qualification_outcome_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '服务经理来源FOUND/NOT_UNIQUE等原结论',
    ADD COLUMN `qualification_role_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '当时实际服务经理职责编码',
    ADD COLUMN `qualification_project_version` BIGINT NULL DEFAULT NULL COMMENT '当时项目版本',
    ADD COLUMN `qualification_participant_fact_version` BIGINT NULL DEFAULT NULL COMMENT '当时参与事实版本',
    ADD COLUMN `qualification_required_action_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '发起人需要的ACTION_EDIT等既有动作',
    ADD COLUMN `qualification_allowed_flag` BIT(1) NULL DEFAULT NULL COMMENT '发起人当时项目授权结论',
    ADD COLUMN `qualification_check_time` DATETIME(3) NULL DEFAULT NULL COMMENT '资格事实核验时间',
    ADD COLUMN `decision_actor_user_id` BIGINT NULL DEFAULT NULL COMMENT '真正完成本节点的办理人',
    ADD COLUMN `decision_role_id` BIGINT NULL DEFAULT NULL COMMENT '办理时实际角色ID',
    ADD COLUMN `decision_membership_version` BIGINT NULL DEFAULT NULL COMMENT '办理时单人成员关系水位',
    ADD COLUMN `decision_user_status_version` BIGINT NULL DEFAULT NULL COMMENT '办理时用户有效性水位',
    ADD COLUMN `decision_check_time` DATETIME(3) NULL DEFAULT NULL COMMENT '决定事务资格核验时间';

-- 个人节点改派历史：只增加原快照中已定义的业务列。
ALTER TABLE `cut_approval_reassignment`
    ADD COLUMN `qualification_kind_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '单人资格来源种类：发起人项目权限或当前项目服务经理',
    ADD COLUMN `qualification_outcome_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '服务经理来源FOUND/NOT_UNIQUE等原结论',
    ADD COLUMN `qualification_role_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '当时实际服务经理职责编码',
    ADD COLUMN `qualification_project_version` BIGINT NULL DEFAULT NULL COMMENT '当时项目版本',
    ADD COLUMN `qualification_participant_fact_version` BIGINT NULL DEFAULT NULL COMMENT '当时参与事实版本',
    ADD COLUMN `qualification_required_action_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '发起人需要的ACTION_EDIT等既有动作',
    ADD COLUMN `qualification_allowed_flag` BIT(1) NULL DEFAULT NULL COMMENT '发起人当时项目授权结论',
    ADD COLUMN `qualification_check_time` DATETIME(3) NULL DEFAULT NULL COMMENT '资格事实核验时间',
    ADD COLUMN `project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '接收人当时项目范围版本';

-- CUT-05；CUT-03：审批冻结结果集合
CREATE TABLE `cut_approval_result_reference` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `approval_instance_id` BIGINT NOT NULL COMMENT '审批实例',
    `checklist_item_id` BIGINT NOT NULL COMMENT '采集项',
    `checklist_result_id` BIGINT NOT NULL COMMENT '本次冻结的统一结果主表ID',
    `result_version` INT NOT NULL COMMENT '本次冻结的结果版本号',
    `required_flag` BIT(1) NOT NULL COMMENT '本次提交时是否必选',
    `sort_order` INT NOT NULL COMMENT '展示顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`approval_instance_id`,`checklist_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批冻结结果集合';

-- 闭环附件：只增加原快照中已定义的业务列。
ALTER TABLE `cut_cutover_closure_attachment`
    ADD COLUMN `file_artifact_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件工件事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `file_reference_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件引用事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `file_availability_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件可用性事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- 割接采集公共回执：只增加原快照中已定义的业务列。
ALTER TABLE `cut_collection_log`
    ADD COLUMN `source_context_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '回执声明的业务上下文；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_object_type_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '回执声明的业务对象类型；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `project_id` BIGINT NULL DEFAULT NULL COMMENT '回执项目；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `actor_user_id` BIGINT NULL DEFAULT NULL COMMENT '原发起人；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `protocol_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '已使用协议；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `external_status_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '实际外部执行状态；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `failure_category_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '实际失败分类',
    ADD COLUMN `command_text` LONGTEXT NULL DEFAULT NULL COMMENT '按PLT契约可保存的脱敏命令文本；不得含凭据',
    ADD COLUMN `template_name` LONGTEXT NULL DEFAULT NULL COMMENT '本次命令模板名称';

-- CUT-06：闭环冻结设备范围
CREATE TABLE `cut_closure_device` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `closure_id` BIGINT NOT NULL COMMENT '闭环ID',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `project_id` BIGINT NOT NULL COMMENT '当时所属项目',
    `sn` VARCHAR(128) NOT NULL COMMENT '当时序列号',
    `project_assignment_version` BIGINT NOT NULL COMMENT '当时归属水位',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`closure_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='闭环冻结设备范围';

-- 外部备件申请引用：只增加原快照中已定义的业务列。
ALTER TABLE `cut_spare_application_reference`
    ADD COLUMN `need_required_flag` BIT(1) NULL DEFAULT NULL COMMENT '当时备件需求判断结论；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `task_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '当时割接任务号；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `source_task_version` BIGINT NULL DEFAULT NULL COMMENT '当时任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `correlation_id` VARCHAR(128) NULL DEFAULT NULL COMMENT '原请求跟踪标识；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- 外部备件状态修订：只增加原快照中已定义的业务列。
ALTER TABLE `cut_spare_status_revision`
    ADD COLUMN `source_payload_digest` CHAR(64) NULL DEFAULT NULL COMMENT '规范化接收载荷摘要，仅保持幂等冲突检测，不替代业务字段；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- 备件人工证据：只增加原快照中已定义的业务列。
ALTER TABLE `cut_spare_manual_evidence`
    ADD COLUMN `file_artifact_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件工件事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `file_reference_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件引用事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `file_availability_fact_version` BIGINT NULL DEFAULT NULL COMMENT '文件可用性事实水位；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- CUT-08；CUT-02/03：备件需求来源
CREATE TABLE `cut_spare_need_source` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `application_reference_id` BIGINT NOT NULL COMMENT '外部申请引用',
    `source_type_code` VARCHAR(32) NOT NULL COMMENT 'ASSESSMENT或CHECKLIST_RISK',
    `assessment_id` BIGINT NULL DEFAULT NULL COMMENT '评估来源ID',
    `assessment_version` BIGINT NULL DEFAULT NULL COMMENT '评估来源版本',
    `checklist_item_id` BIGINT NULL DEFAULT NULL COMMENT '当时适用的重大备件采集项',
    `checklist_item_version` BIGINT NULL DEFAULT NULL COMMENT '该清单项当时的并发版本；与结果修订无关',
    `spare_part_applied_flag` BIT(1) NULL DEFAULT NULL COMMENT '当时评估备件判断',
    `applicable_flag` BIT(1) NULL DEFAULT NULL COMMENT '当时清单项适用事实',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`application_reference_id`,`assessment_id`),
    UNIQUE KEY `uk_business_2` (`tenant_id`,`application_reference_id`,`checklist_item_id`),
    CONSTRAINT `chk_spare_need_source_1` CHECK ((source_type_code = 'ASSESSMENT' AND assessment_id IS NOT NULL AND assessment_version IS NOT NULL AND assessment_version > 0 AND spare_part_applied_flag = b'1' AND spare_part_applied_flag IS NOT NULL AND checklist_item_id IS NULL AND checklist_item_version IS NULL AND applicable_flag IS NULL) OR (source_type_code = 'CHECKLIST_RISK' AND checklist_item_id IS NOT NULL AND checklist_item_version IS NOT NULL AND checklist_item_version >= 0 AND applicable_flag = b'1' AND applicable_flag IS NOT NULL AND assessment_id IS NULL AND assessment_version IS NULL AND spare_part_applied_flag IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备件需求来源';

-- CUT-08：备件请求设备范围
CREATE TABLE `cut_spare_request_device` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `application_reference_id` BIGINT NOT NULL COMMENT '外部申请引用',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `sn` VARCHAR(128) NOT NULL COMMENT '当时序列号',
    `project_assignment_version` BIGINT NOT NULL COMMENT '当时归属水位',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`application_reference_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备件请求设备范围';

-- IMP割接就绪快照：只增加原快照中已定义的业务列。
ALTER TABLE `imp_cutover_readiness_snapshot`
    ADD COLUMN `project_scope_version` BIGINT NULL DEFAULT NULL COMMENT '冻结项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `approved_plan_id` BIGINT NULL DEFAULT NULL COMMENT '原已批准实施计划ID；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `approved_plan_version` BIGINT NULL DEFAULT NULL COMMENT '原计划版本；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- EXE-06：IMP就绪冻结设备
CREATE TABLE `imp_cutover_readiness_device` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `snapshot_id` BIGINT NOT NULL COMMENT '原IMP快照ID',
    `device_id` BIGINT NOT NULL COMMENT '设备ID',
    `sn` VARCHAR(128) NOT NULL COMMENT '来源序列号',
    `project_assignment_version` BIGINT NOT NULL COMMENT '来源归属水位',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`snapshot_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IMP就绪冻结设备';

-- EXE-06：IMP实施完成来源
CREATE TABLE `imp_cutover_readiness_source` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `snapshot_id` BIGINT NOT NULL COMMENT '原IMP快照ID',
    `source_code` VARCHAR(32) NOT NULL COMMENT 'EXE_01～EXE_04原来源',
    `completion_status_code` VARCHAR(32) NOT NULL COMMENT '原来源完成状态',
    `fact_version` BIGINT NOT NULL COMMENT '原来源事实版本',
    `reopened_flag` BIT(1) NOT NULL COMMENT '原来源是否重开',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`snapshot_id`,`source_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IMP实施完成来源';

-- EXE-06：IMP来源业务对象集合
CREATE TABLE `imp_cutover_readiness_source_object` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `source_fact_id` BIGINT NOT NULL COMMENT '所属快照来源事实',
    `source_object_id` BIGINT NOT NULL COMMENT '原来源对象ID',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`source_fact_id`,`source_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IMP来源业务对象集合';

-- EXE-06：IMP来源水位集合
CREATE TABLE `imp_cutover_readiness_source_watermark` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `source_fact_id` BIGINT NOT NULL COMMENT '所属快照来源事实',
    `axis_code` VARCHAR(64) NOT NULL COMMENT '原水位轴',
    `object_id` BIGINT NOT NULL COMMENT '原水位对象',
    `fact_version` BIGINT NOT NULL COMMENT '该轴/对象的原水位',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`source_fact_id`,`axis_code`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IMP来源水位集合';

-- EXE-06：IMP未满足原因集合
CREATE TABLE `imp_cutover_readiness_unmet` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `snapshot_id` BIGINT NOT NULL COMMENT '原IMP快照ID',
    `unmet_code` VARCHAR(128) NOT NULL COMMENT '原未满足原因编码',
    `sort_order` INT NOT NULL COMMENT '原顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`snapshot_id`,`unmet_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IMP未满足原因集合';

-- AST设备割接成功记录：只增加原快照中已定义的业务列。
ALTER TABLE `ast_device_cutover_result`
    ADD COLUMN `task_no` VARCHAR(64) NULL DEFAULT NULL COMMENT '割接任务号；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `task_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '割接任务名称；历史原件缺失时可空，新业务按既有来源/提交规则必填',
    ADD COLUMN `submit_user_id` BIGINT NULL DEFAULT NULL COMMENT '原闭环提交人；历史原件缺失时可空，新业务按既有来源/提交规则必填';

-- CUT-06；AST/PLT：AST设备割接文件事实
CREATE TABLE `ast_device_cutover_result_file` (
    `id` BIGINT NOT NULL COMMENT '本表主键',
    `tenant_id` BIGINT NOT NULL COMMENT '所属租户；必须与父对象一致',
    `device_cutover_result_id` BIGINT NOT NULL COMMENT '本设备的AST割接结果根',
    `file_source_code` VARCHAR(32) NOT NULL COMMENT 'COLLECTION或MANUAL',
    `collection_stage_code` VARCHAR(64) NOT NULL COMMENT '来源采集/证据阶段',
    `actor_user_id` BIGINT NULL DEFAULT NULL COMMENT '原采集操作者',
    `platform_task_id` VARCHAR(128) NULL DEFAULT NULL COMMENT '原平台采集任务',
    `result_version` BIGINT NULL DEFAULT NULL COMMENT '原平台结果版本',
    `file_version_id` BIGINT NULL DEFAULT NULL COMMENT '原采集文件版本ID',
    `file_artifact_id` BIGINT NULL DEFAULT NULL COMMENT '人工附件工件ID',
    `file_version_no` INT NULL DEFAULT NULL COMMENT '人工附件固定版本',
    `file_reference_key` VARCHAR(128) NULL DEFAULT NULL COMMENT '人工附件业务引用',
    `file_sha256` CHAR(64) NULL DEFAULT NULL COMMENT '原附件摘要',
    `sort_order` INT NOT NULL COMMENT '原文件顺序',
    `create_user_id` BIGINT NOT NULL COMMENT '创建人；历史投影由迁移执行身份记录，不冒充原业务办理人',
    `create_time` DATETIME(3) NOT NULL COMMENT '本行创建时间；原业务发生时间另按来源字段保留',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`tenant_id`,`id`),
    UNIQUE KEY `uk_business_1` (`tenant_id`,`device_cutover_result_id`,`platform_task_id`,`result_version`,`file_version_id`),
    UNIQUE KEY `uk_business_2` (`tenant_id`,`device_cutover_result_id`,`file_artifact_id`,`file_version_no`,`file_reference_key`),
    CONSTRAINT `chk_device_cutover_result_file_1` CHECK ((file_source_code = 'COLLECTION' AND platform_task_id IS NOT NULL AND result_version IS NOT NULL AND file_version_id IS NOT NULL AND file_artifact_id IS NULL AND file_version_no IS NULL AND file_reference_key IS NULL AND file_sha256 IS NULL) OR (file_source_code = 'MANUAL' AND file_artifact_id IS NOT NULL AND file_version_no IS NOT NULL AND file_reference_key IS NOT NULL AND file_sha256 IS NOT NULL AND platform_task_id IS NULL AND result_version IS NULL AND file_version_id IS NULL AND actor_user_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AST设备割接文件事实';

ALTER TABLE `ast_device_cutover_result` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_approval_instance` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_assessment` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_collection_log` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_binding_rule_revision` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item_definition_revision` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item_result` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_closure` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_configuration_revision` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_support_arrangement` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_revision` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_spare_application_reference` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `cut_task_device_scope` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_snapshot` ADD UNIQUE KEY `uk_cut_structured_owner_id` (`tenant_id`,`id`);

ALTER TABLE `cut_configuration_dimension` ADD CONSTRAINT `fk_cut_structured_1` FOREIGN KEY (`tenant_id`,`configuration_revision_id`) REFERENCES `cut_cutover_configuration_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_configuration_dictionary_value` ADD CONSTRAINT `fk_cut_structured_2` FOREIGN KEY (`tenant_id`,`configuration_revision_id`) REFERENCES `cut_cutover_configuration_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_configuration_plan_section` ADD CONSTRAINT `fk_cut_structured_3` FOREIGN KEY (`tenant_id`,`configuration_revision_id`) REFERENCES `cut_cutover_configuration_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_configuration_plan_section_scope` ADD CONSTRAINT `fk_cut_structured_4` FOREIGN KEY (`tenant_id`,`plan_section_id`) REFERENCES `cut_configuration_plan_section` (`tenant_id`,`id`);
ALTER TABLE `cut_configuration_validation_issue` ADD CONSTRAINT `fk_cut_structured_5` FOREIGN KEY (`tenant_id`,`configuration_revision_id`) REFERENCES `cut_cutover_configuration_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_field_definition` ADD CONSTRAINT `fk_cut_structured_6` FOREIGN KEY (`tenant_id`,`item_definition_revision_id`) REFERENCES `cut_cutover_checklist_item_definition_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_field_definition` ADD CONSTRAINT `fk_cut_structured_7` FOREIGN KEY (`tenant_id`,`custom_checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_field_definition` ADD CONSTRAINT `fk_cut_structured_8` FOREIGN KEY (`tenant_id`,`parent_field_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_field_option` ADD CONSTRAINT `fk_cut_structured_9` FOREIGN KEY (`tenant_id`,`field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_binding_condition` ADD CONSTRAINT `fk_cut_structured_10` FOREIGN KEY (`tenant_id`,`binding_rule_revision_id`) REFERENCES `cut_cutover_checklist_binding_rule_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_binding_condition` ADD CONSTRAINT `fk_cut_structured_11` FOREIGN KEY (`tenant_id`,`configuration_dimension_id`) REFERENCES `cut_configuration_dimension` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_mapping` ADD CONSTRAINT `fk_cut_structured_12` FOREIGN KEY (`tenant_id`,`item_definition_revision_id`) REFERENCES `cut_cutover_checklist_item_definition_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_assessment_device` ADD CONSTRAINT `fk_cut_structured_13` FOREIGN KEY (`tenant_id`,`assessment_id`) REFERENCES `cut_assessment` (`tenant_id`,`id`);
ALTER TABLE `cut_assessment_device` ADD CONSTRAINT `fk_cut_structured_14` FOREIGN KEY (`tenant_id`,`task_device_scope_id`) REFERENCES `cut_task_device_scope` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist` ADD CONSTRAINT `fk_cut_structured_15` FOREIGN KEY (`tenant_id`,`configuration_revision_id`) REFERENCES `cut_cutover_configuration_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item_result` ADD CONSTRAINT `fk_cut_structured_16` FOREIGN KEY (`tenant_id`,`field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item_result` ADD CONSTRAINT `fk_cut_structured_17` FOREIGN KEY (`tenant_id`,`base_checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_checklist_item_result` ADD CONSTRAINT `fk_cut_structured_18` FOREIGN KEY (`tenant_id`,`collection_log_id`) REFERENCES `cut_collection_log` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_input_dimension` ADD CONSTRAINT `fk_cut_structured_19` FOREIGN KEY (`tenant_id`,`checklist_id`) REFERENCES `cut_cutover_checklist` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_input_dimension` ADD CONSTRAINT `fk_cut_structured_20` FOREIGN KEY (`tenant_id`,`configuration_dimension_id`) REFERENCES `cut_configuration_dimension` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_match_candidate` ADD CONSTRAINT `fk_cut_structured_21` FOREIGN KEY (`tenant_id`,`checklist_id`) REFERENCES `cut_cutover_checklist` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_match_candidate` ADD CONSTRAINT `fk_cut_structured_22` FOREIGN KEY (`tenant_id`,`item_definition_revision_id`) REFERENCES `cut_cutover_checklist_item_definition_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_match_candidate` ADD CONSTRAINT `fk_cut_structured_23` FOREIGN KEY (`tenant_id`,`binding_rule_revision_id`) REFERENCES `cut_cutover_checklist_binding_rule_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_device_context` ADD CONSTRAINT `fk_cut_structured_24` FOREIGN KEY (`tenant_id`,`checklist_id`) REFERENCES `cut_cutover_checklist` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_device_context` ADD CONSTRAINT `fk_cut_structured_25` FOREIGN KEY (`tenant_id`,`assessment_device_id`) REFERENCES `cut_assessment_device` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_result_file` ADD CONSTRAINT `fk_cut_structured_26` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_result_file` ADD CONSTRAINT `fk_cut_structured_27` FOREIGN KEY (`tenant_id`,`custom_result_row_id`) REFERENCES `cut_result_custom_row` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_result_file` ADD CONSTRAINT `fk_cut_structured_28` FOREIGN KEY (`tenant_id`,`field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_query` ADD CONSTRAINT `fk_cut_structured_29` FOREIGN KEY (`tenant_id`,`checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_query` ADD CONSTRAINT `fk_cut_structured_30` FOREIGN KEY (`tenant_id`,`item_definition_revision_id`) REFERENCES `cut_cutover_checklist_item_definition_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_query` ADD CONSTRAINT `fk_cut_structured_31` FOREIGN KEY (`tenant_id`,`loaded_checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_query_parameter` ADD CONSTRAINT `fk_cut_structured_32` FOREIGN KEY (`tenant_id`,`external_query_id`) REFERENCES `cut_checklist_external_query` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_external_query_parameter` ADD CONSTRAINT `fk_cut_structured_33` FOREIGN KEY (`tenant_id`,`mapping_id`) REFERENCES `cut_checklist_external_mapping` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_result_query_reference` ADD CONSTRAINT `fk_cut_structured_34` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_checklist_result_query_reference` ADD CONSTRAINT `fk_cut_structured_35` FOREIGN KEY (`tenant_id`,`external_query_id`) REFERENCES `cut_checklist_external_query` (`tenant_id`,`id`);
ALTER TABLE `cut_result_cutover_background` ADD CONSTRAINT `fk_cut_structured_36` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_business_summary` ADD CONSTRAINT `fk_cut_structured_37` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_impact_scope` ADD CONSTRAINT `fk_cut_structured_38` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_continuity_requirement` ADD CONSTRAINT `fk_cut_structured_39` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_interruption` ADD CONSTRAINT `fk_cut_structured_40` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_check_feedback` ADD CONSTRAINT `fk_cut_structured_41` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_spare_reference` ADD CONSTRAINT `fk_cut_structured_42` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_bulletin_summary` ADD CONSTRAINT `fk_cut_structured_43` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_device_location` ADD CONSTRAINT `fk_cut_structured_44` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_device_location` ADD CONSTRAINT `fk_cut_structured_45` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_interface_plan` ADD CONSTRAINT `fk_cut_structured_46` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_interface_plan` ADD CONSTRAINT `fk_cut_structured_47` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_ip_plan` ADD CONSTRAINT `fk_cut_structured_48` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_ip_plan` ADD CONSTRAINT `fk_cut_structured_49` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_performance` ADD CONSTRAINT `fk_cut_structured_50` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_performance` ADD CONSTRAINT `fk_cut_structured_51` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_technical_bulletin` ADD CONSTRAINT `fk_cut_structured_52` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_technical_bulletin` ADD CONSTRAINT `fk_cut_structured_53` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_technical_bulletin` ADD CONSTRAINT `fk_cut_structured_54` FOREIGN KEY (`tenant_id`,`external_query_id`) REFERENCES `cut_checklist_external_query` (`tenant_id`,`id`);
ALTER TABLE `cut_result_technical_bulletin` ADD CONSTRAINT `fk_cut_structured_55` FOREIGN KEY (`tenant_id`,`source_bulletin_row_id`) REFERENCES `cut_result_technical_bulletin` (`tenant_id`,`id`);
ALTER TABLE `cut_result_custom_value` ADD CONSTRAINT `fk_cut_structured_56` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_custom_value` ADD CONSTRAINT `fk_cut_structured_57` FOREIGN KEY (`tenant_id`,`field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_custom_row` ADD CONSTRAINT `fk_cut_structured_58` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_result_custom_row` ADD CONSTRAINT `fk_cut_structured_59` FOREIGN KEY (`tenant_id`,`table_field_definition_id`) REFERENCES `cut_checklist_field_definition` (`tenant_id`,`id`);
ALTER TABLE `cut_result_custom_row` ADD CONSTRAINT `fk_cut_structured_60` FOREIGN KEY (`tenant_id`,`parent_custom_row_id`) REFERENCES `cut_result_custom_row` (`tenant_id`,`id`);
ALTER TABLE `cut_cutover_support_arrangement` ADD CONSTRAINT `fk_cut_structured_61` FOREIGN KEY (`tenant_id`,`current_support_contact_change_id`) REFERENCES `cut_plan_support_contact_change` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_schedule` ADD CONSTRAINT `fk_cut_structured_62` FOREIGN KEY (`tenant_id`,`plan_revision_id`) REFERENCES `cut_plan_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_device` ADD CONSTRAINT `fk_cut_structured_63` FOREIGN KEY (`tenant_id`,`plan_revision_id`) REFERENCES `cut_plan_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_device` ADD CONSTRAINT `fk_cut_structured_64` FOREIGN KEY (`tenant_id`,`assessment_device_id`) REFERENCES `cut_assessment_device` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_file` ADD CONSTRAINT `fk_cut_structured_65` FOREIGN KEY (`tenant_id`,`plan_revision_id`) REFERENCES `cut_plan_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_risk_mitigation` ADD CONSTRAINT `fk_cut_structured_66` FOREIGN KEY (`tenant_id`,`plan_revision_id`) REFERENCES `cut_plan_revision` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_risk_mitigation` ADD CONSTRAINT `fk_cut_structured_67` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_risk_mitigation` ADD CONSTRAINT `fk_cut_structured_68` FOREIGN KEY (`tenant_id`,`checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_support_contact_change` ADD CONSTRAINT `fk_cut_structured_69` FOREIGN KEY (`tenant_id`,`support_arrangement_id`) REFERENCES `cut_cutover_support_arrangement` (`tenant_id`,`id`);
ALTER TABLE `cut_plan_support_contact_change` ADD CONSTRAINT `fk_cut_structured_70` FOREIGN KEY (`tenant_id`,`previous_change_id`) REFERENCES `cut_plan_support_contact_change` (`tenant_id`,`id`);
ALTER TABLE `cut_approval_result_reference` ADD CONSTRAINT `fk_cut_structured_71` FOREIGN KEY (`tenant_id`,`approval_instance_id`) REFERENCES `cut_approval_instance` (`tenant_id`,`id`);
ALTER TABLE `cut_approval_result_reference` ADD CONSTRAINT `fk_cut_structured_72` FOREIGN KEY (`tenant_id`,`checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`tenant_id`,`id`);
ALTER TABLE `cut_approval_result_reference` ADD CONSTRAINT `fk_cut_structured_73` FOREIGN KEY (`tenant_id`,`checklist_result_id`) REFERENCES `cut_cutover_checklist_item_result` (`tenant_id`,`id`);
ALTER TABLE `cut_closure_device` ADD CONSTRAINT `fk_cut_structured_74` FOREIGN KEY (`tenant_id`,`closure_id`) REFERENCES `cut_cutover_closure` (`tenant_id`,`id`);
ALTER TABLE `cut_spare_need_source` ADD CONSTRAINT `fk_cut_structured_75` FOREIGN KEY (`tenant_id`,`application_reference_id`) REFERENCES `cut_spare_application_reference` (`tenant_id`,`id`);
ALTER TABLE `cut_spare_need_source` ADD CONSTRAINT `fk_cut_structured_76` FOREIGN KEY (`tenant_id`,`assessment_id`) REFERENCES `cut_assessment` (`tenant_id`,`id`);
ALTER TABLE `cut_spare_need_source` ADD CONSTRAINT `fk_cut_structured_77` FOREIGN KEY (`tenant_id`,`checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`tenant_id`,`id`);
ALTER TABLE `cut_spare_request_device` ADD CONSTRAINT `fk_cut_structured_78` FOREIGN KEY (`tenant_id`,`application_reference_id`) REFERENCES `cut_spare_application_reference` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_device` ADD CONSTRAINT `fk_cut_structured_79` FOREIGN KEY (`tenant_id`,`snapshot_id`) REFERENCES `imp_cutover_readiness_snapshot` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_source` ADD CONSTRAINT `fk_cut_structured_80` FOREIGN KEY (`tenant_id`,`snapshot_id`) REFERENCES `imp_cutover_readiness_snapshot` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_source_object` ADD CONSTRAINT `fk_cut_structured_81` FOREIGN KEY (`tenant_id`,`source_fact_id`) REFERENCES `imp_cutover_readiness_source` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_source_watermark` ADD CONSTRAINT `fk_cut_structured_82` FOREIGN KEY (`tenant_id`,`source_fact_id`) REFERENCES `imp_cutover_readiness_source` (`tenant_id`,`id`);
ALTER TABLE `imp_cutover_readiness_unmet` ADD CONSTRAINT `fk_cut_structured_83` FOREIGN KEY (`tenant_id`,`snapshot_id`) REFERENCES `imp_cutover_readiness_snapshot` (`tenant_id`,`id`);
ALTER TABLE `ast_device_cutover_result_file` ADD CONSTRAINT `fk_cut_structured_84` FOREIGN KEY (`tenant_id`,`device_cutover_result_id`) REFERENCES `ast_device_cutover_result` (`tenant_id`,`id`);
