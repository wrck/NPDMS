-- V294: 迁移域字段命名与注释统一（2026-09-18需求方指令：正式执行前将V294~V297合并为本迁移，每表一条最终状态SQL）
-- 每张受影响表仅一条ALTER，直接呈现相对V293之后状态的最终变更（列名更正一步到位带最终注释，注释列单条MODIFY），
-- 不保留中间态。裁决依据：docs/decisions/open-questions.md Q-MIG-DIM-001~005。
-- 规格权威DDL：specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql。
-- 列名更正的表均未写入业务数据或仅改注释，不丢失信息；com_crm_execution_*未建表，建表时直接采用修订后规格DDL。

-- FK引用列的注释性变更需临时关闭会话级外键检查；列定义与最终定义一致，FK保持不变
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE acc_project_deliverable
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE acc_satisfaction_collection_task
    MODIFY COLUMN `prior_task_id` bigint NULL COMMENT '整改前序满意度任务逻辑引用',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目逻辑引用',
    MODIFY COLUMN `source_object_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源对象逻辑引用',
    MODIFY COLUMN `source_object_version` bigint NOT NULL COMMENT '冻结来源业务对象版本',
    MODIFY COLUMN `task_revision_no` int NOT NULL COMMENT '整改重收任务序号';

ALTER TABLE acc_satisfaction_questionnaire
    MODIFY COLUMN `frozen_question_json` json NOT NULL COMMENT '冻结题目、必答项与分值规则',
    MODIFY COLUMN `rule_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '冻结满意度规则版本',
    MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '问卷模板逻辑引用';

ALTER TABLE acc_satisfaction_response
    MODIFY COLUMN `questionnaire_id` bigint NOT NULL COMMENT '问卷实例逻辑引用',
    MODIFY COLUMN `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提交幂等键';

ALTER TABLE acc_satisfaction_result
    MODIFY COLUMN `questionnaire_id` bigint NOT NULL COMMENT '问卷实例逻辑引用',
    MODIFY COLUMN `response_id` bigint NOT NULL COMMENT '答卷逻辑引用';

ALTER TABLE ast_device_version
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `device_id` bigint NOT NULL COMMENT '设备ID',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '更新人';

ALTER TABLE com_contract
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '签约公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '签约公司ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '签约公司名称',
    MODIFY COLUMN `contract_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '合同名称',
    MODIFY COLUMN `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '合同编号',
    MODIFY COLUMN `contract_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '合同类型编码',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `currency_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '币种编码',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_id` bigint NULL COMMENT '客户ID',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `effective_date` date NULL COMMENT '生效日期',
    MODIFY COLUMN `expiry_date` date NULL COMMENT '失效日期',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `master_source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '主档来源记录键',
    MODIFY COLUMN `master_source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主档来源系统',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE com_contract_receivable
    DROP COLUMN `marketing_department_id`,
    DROP COLUMN `system_department_id`,
    DROP COLUMN `expansion_department_id`,
    DROP COLUMN `original_system_department_source_key`,
    DROP COLUMN `original_expansion_department_source_key`,
    DROP COLUMN `original_industry_name`,
    CHANGE COLUMN `marketing_department_code` `market_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场部编码',
    CHANGE COLUMN `marketing_department_name` `market_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场部名称',
    CHANGE COLUMN `office_department_id` `department_id` bigint NULL COMMENT '办事处ID',
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '办事处编码',
    CHANGE COLUMN `office_department_name` `department_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '办事处名称',
    CHANGE COLUMN `system_department_source_key` `system_source_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部来源键',
    CHANGE COLUMN `system_department_code` `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部编码',
    CHANGE COLUMN `system_department_name` `system_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部名称',
    CHANGE COLUMN `expansion_department_source_key` `expend_source_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '拓展部来源键',
    CHANGE COLUMN `expansion_department_code` `expend_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '拓展部编码',
    CHANGE COLUMN `expansion_department_name` `expend_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '拓展部名称',
    ADD COLUMN `original_division_values` json NULL COMMENT '创建时行业划分原值JSON' AFTER `secondary_representative_code`,
    MODIFY COLUMN `collected_amount` decimal(20,2) NOT NULL COMMENT '已收金额',
    MODIFY COLUMN `collected_ratio` decimal(18,6) NULL COMMENT '已收比例',
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '公司ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `company_resolution_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司解析来源',
    MODIFY COLUMN `contract_amount` decimal(20,2) NOT NULL COMMENT '合同金额',
    MODIFY COLUMN `contract_create_time` datetime(3) NULL COMMENT '合同创建时间',
    MODIFY COLUMN `contract_id` bigint NULL COMMENT '合同ID',
    MODIFY COLUMN `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '合同编号',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `currency_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '币种名称',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `delivered_amount` decimal(20,2) NOT NULL COMMENT '已发货金额',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `import_batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '导入批次编号',
    MODIFY COLUMN `industry_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '行业编码',
    MODIFY COLUMN `industry_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '行业名称',
    MODIFY COLUMN `latest_ship_time` datetime(3) NULL COMMENT '最近发货时间',
    MODIFY COLUMN `mapping_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_COMPANY' COMMENT '映射状态',
    MODIFY COLUMN `marketing_representative_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场代表编码',
    MODIFY COLUMN `marketing_representative_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场代表名称',
    MODIFY COLUMN `overdue_amount` decimal(20,2) NULL COMMENT '逾期金额',
    MODIFY COLUMN `project_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目编码',
    MODIFY COLUMN `project_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
    MODIFY COLUMN `receivable_amount` decimal(20,2) NULL COMMENT '应收金额',
    MODIFY COLUMN `secondary_representative_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '辅助代表编码',
    MODIFY COLUMN `source_batch_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源批次编码',
    MODIFY COLUMN `source_effective_from` datetime(3) NULL COMMENT '来源生效开始时间',
    MODIFY COLUMN `source_effective_to` datetime(3) NULL COMMENT '来源生效结束时间',
    MODIFY COLUMN `source_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源订单编号',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源记录稳定唯一键',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人';

ALTER TABLE com_delivery_scope
    CHANGE COLUMN `office_department_id` `department_id` bigint NOT NULL COMMENT '办事处ID',
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '办事处编码',
    CHANGE COLUMN `office_department_name` `department_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '办事处名称',
    CHANGE COLUMN `office_department_version` `department_version` int unsigned NOT NULL COMMENT '办事处版本',
    MODIFY COLUMN `allocated_qty` decimal(18,6) NOT NULL COMMENT '实施数量',
    MODIFY COLUMN `allocation_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分配来源',
    MODIFY COLUMN `change_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更原因',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `effective_from` datetime(3) NOT NULL COMMENT '生效开始时间',
    MODIFY COLUMN `effective_to` datetime(3) NULL COMMENT '生效结束时间',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料编码',
    MODIFY COLUMN `item_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料描述',
    MODIFY COLUMN `line_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单行号',
    MODIFY COLUMN `order_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单公司编码',
    MODIFY COLUMN `order_company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '订单公司名称',
    MODIFY COLUMN `order_line_id` bigint NOT NULL COMMENT '订单行ID',
    MODIFY COLUMN `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单编号',
    MODIFY COLUMN `order_source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单来源系统编码',
    MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单类型编码',
    MODIFY COLUMN `project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目编码',
    MODIFY COLUMN `project_company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目公司编码',
    MODIFY COLUMN `project_company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目公司名称',
    MODIFY COLUMN `project_customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目客户编码',
    MODIFY COLUMN `project_customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目客户名称',
    MODIFY COLUMN `project_department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目部门编码',
    MODIFY COLUMN `project_department_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目部门名称',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
    MODIFY COLUMN `project_manager_employee_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目负责人工号',
    MODIFY COLUMN `project_manager_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目负责人姓名',
    MODIFY COLUMN `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
    MODIFY COLUMN `scope_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实施范围状态',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE com_delivery_scope_detail
    MODIFY COLUMN `allocated_qty` decimal(18,6) NOT NULL COMMENT '分配数量',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `delivery_batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '交付批次编号',
    MODIFY COLUMN `delivery_scope_id` bigint NOT NULL COMMENT '交付范围主ID',
    MODIFY COLUMN `detail_sequence` int unsigned NOT NULL COMMENT '交付范围明细序号',
    MODIFY COLUMN `device_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '设备类型编码',
    MODIFY COLUMN `device_type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '设备类型名称快照',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '产品编码',
    MODIFY COLUMN `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '产品名称快照',
    MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '交付范围明细备注',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '明细来源记录稳定键',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人';

ALTER TABLE com_order_contract_relation
    MODIFY COLUMN `contract_id` bigint NOT NULL COMMENT '合同ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '订单ID',
    MODIFY COLUMN `relation_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RELATED' COMMENT '关系角色编码',
    MODIFY COLUMN `relation_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关系来源',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人';

ALTER TABLE com_project_contract_relation
    MODIFY COLUMN `contract_id` bigint NOT NULL COMMENT '合同ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `effective_from` datetime(3) NULL COMMENT '生效开始时间',
    MODIFY COLUMN `effective_to` datetime(3) NULL COMMENT '生效结束时间',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
    MODIFY COLUMN `relation_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RELATED' COMMENT '关系角色编码',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源记录稳定唯一键',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `source_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源系统物理表名',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE com_sales_order
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '公司ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_id` bigint NULL COMMENT '客户ID',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `customer_required_time` datetime(3) NULL COMMENT '客户要求时间',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `order_comment` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '订单说明',
    MODIFY COLUMN `order_create_time` datetime(3) NULL COMMENT '订单创建时间',
    MODIFY COLUMN `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单编号',
    MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单类型编码',
    MODIFY COLUMN `sales_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '销售类型编码',
    MODIFY COLUMN `source_project_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源项目名称',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE com_sales_order_line
    MODIFY COLUMN `bundle_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '套件编码',
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '父订单所属公司主档ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_id` bigint NULL COMMENT '父订单客户主档ID',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `delivered_qty` decimal(18,6) NULL COMMENT 'ERP订单行当前累计发货数量',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料编码',
    MODIFY COLUMN `item_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料描述',
    MODIFY COLUMN `line_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '行编号',
    MODIFY COLUMN `line_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '行类型编码',
    MODIFY COLUMN `open_qty` decimal(18,6) NULL COMMENT 'ERP订单行当前未执行数量',
    MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '订单ID',
    MODIFY COLUMN `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单编号',
    MODIFY COLUMN `order_qty` decimal(18,6) NULL COMMENT 'ERP订单行下单数量',
    MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单类型编码',
    MODIFY COLUMN `product_id` bigint NULL COMMENT '产品ID',
    MODIFY COLUMN `profit_center` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '利润中心',
    MODIFY COLUMN `real_execution_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '实际执行单编号',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单来源系统',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    MODIFY COLUMN `warranty_month` int NULL COMMENT '质保期限月数';

ALTER TABLE com_shipment_contract_reference
    DROP COLUMN `marketing_department_id`,
    DROP COLUMN `system_department_id`,
    CHANGE COLUMN `office_department_id` `department_id` bigint NULL COMMENT '办事处ID',
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '办事处编码',
    CHANGE COLUMN `office_department_name` `department_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '办事处名称',
    CHANGE COLUMN `marketing_department_code` `market_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场部编码',
    CHANGE COLUMN `marketing_department_name` `market_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '市场部名称',
    CHANGE COLUMN `system_department_source_key` `system_source_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部来源键',
    CHANGE COLUMN `system_department_code` `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部编码',
    CHANGE COLUMN `system_department_name` `system_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统部名称',
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '公司ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `contract_id` bigint NULL COMMENT '合同ID',
    MODIFY COLUMN `contract_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '合同编号',
    MODIFY COLUMN `contract_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '合同类型编码',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `mapping_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    MODIFY COLUMN `project_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
    MODIFY COLUMN `remark` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源记录稳定唯一键',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `warranty_flag` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '质保标志：0否，1是';

ALTER TABLE com_shipment_package
    MODIFY COLUMN `carrier_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '承运商名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `express_no` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '快递编号',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `mapping_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    MODIFY COLUMN `package_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '装箱单编号',
    MODIFY COLUMN `receiver_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '收件人名称',
    MODIFY COLUMN `shipment_contract_ref_id` bigint NULL COMMENT '发货合同归属ID',
    MODIFY COLUMN `shipment_time` datetime(3) NULL COMMENT '发货时间',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源记录稳定唯一键',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `warranty_end_time` datetime(3) NULL COMMENT '质保结束时间',
    MODIFY COLUMN `warranty_start_time` datetime(3) NULL COMMENT '质保开始时间';

ALTER TABLE cus_customer_contact
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '创建人',
    MODIFY COLUMN `customer_id` bigint NOT NULL COMMENT '客户ID',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '邮箱',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '电话',
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '更新人',
    MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE cus_market_relation
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `expend_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '拓展部编码',
    MODIFY COLUMN `expend_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '拓展部名称',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `industry_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行业编码',
    MODIFY COLUMN `industry_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行业名称',
    MODIFY COLUMN `market_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市场部编码',
    MODIFY COLUMN `market_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '市场部名称',
    MODIFY COLUMN `system_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统部编码',
    MODIFY COLUMN `system_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统部名称',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '更新人';

ALTER TABLE cut_cutover_checklist
    MODIFY COLUMN `assessment_id` bigint NOT NULL COMMENT 'CUT-02等级评估逻辑引用',
    MODIFY COLUMN `config_gap_snapshot` json NULL COMMENT '配置缺口快照',
    MODIFY COLUMN `config_revision_snapshot` json NOT NULL COMMENT '采集项与匹配配置revision快照',
    MODIFY COLUMN `cutover_task_id` bigint NOT NULL COMMENT 'CUT-01割接任务逻辑引用',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `input_snapshot` json NOT NULL COMMENT 'P3规则匹配输入冻结快照',
    MODIFY COLUMN `match_trace` json NOT NULL COMMENT '逐项规则匹配轨迹',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `version` int NOT NULL COMMENT '草稿重匹配乐观锁版本';

ALTER TABLE cut_cutover_checklist_item
    MODIFY COLUMN `checklist_id` bigint NOT NULL COMMENT 'CUT-03清单版本ID',
    MODIFY COLUMN `command_template_id` bigint NULL COMMENT 'DAC命令模板逻辑引用',
    MODIFY COLUMN `custom_creator_user_id` bigint NULL COMMENT '自定义项创建人',
    MODIFY COLUMN `device_id` bigint NULL COMMENT '适用设备逻辑引用',
    MODIFY COLUMN `display_condition_snapshot` json NULL COMMENT '冻结显示条件',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `interface_schema_snapshot` json NOT NULL COMMENT '冻结界面与输入Schema',
    MODIFY COLUMN `item_definition_id` bigint NULL COMMENT '系统采集项定义逻辑引用',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `version` int NOT NULL COMMENT '草稿阶段乐观锁版本';

ALTER TABLE cut_cutover_checklist_item_result
    MODIFY COLUMN `answer_snapshot` json NOT NULL COMMENT '结构化答案冻结快照',
    MODIFY COLUMN `checklist_item_id` bigint NOT NULL COMMENT 'CUT-03清单项ID',
    MODIFY COLUMN `collection_result_reference_id` bigint NULL COMMENT 'DAC结果稳定引用',
    MODIFY COLUMN `collection_task_id` bigint NULL COMMENT 'DAC CollectionTask逻辑引用',
    MODIFY COLUMN `fact_description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文本事实说明',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `query_condition_snapshot` json NULL COMMENT '外部加载查询条件脱敏快照',
    MODIFY COLUMN `selection_ended_at` datetime(3) NULL COMMENT '结束当前选择时间',
    MODIFY COLUMN `selection_started_at` datetime(3) NOT NULL COMMENT '成为当前选择时间',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID';

ALTER TABLE cut_cutover_closure
    MODIFY COLUMN `plan_revision_id` bigint NOT NULL COMMENT '已批准CUT-04方案版本';

ALTER TABLE cut_cutover_support_arrangement
    MODIFY COLUMN `plan_revision_id` bigint NOT NULL COMMENT 'CUT-04方案版本逻辑引用';

ALTER TABLE fcom001_v70_com_delivery_scope_detail
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '办事处编码';

ALTER TABLE plt_external_key_mapping
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `target_id` bigint NULL COMMENT '目标ID',
    MODIFY COLUMN `target_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '业务角色',
    MODIFY COLUMN `target_sequence` int NULL COMMENT '目标顺序',
    MODIFY COLUMN `target_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '目标表',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

ALTER TABLE plt_migration_issue
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `candidate_target_ids` json NOT NULL COMMENT '目标记录ID数组JSON',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `issue_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题类型编码',
    MODIFY COLUMN `raw_business_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '原始业务键',
    MODIFY COLUMN `raw_payload` json NULL COMMENT '原始字段和值JSON',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

ALTER TABLE plt_migration_source_record
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `source_business_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源记录可读业务键',
    MODIFY COLUMN `source_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源记录SHA-256校验值',
    MODIFY COLUMN `source_payload` json NOT NULL COMMENT '来源记录原值JSON',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `source_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源系统物理表名',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

ALTER TABLE proj_project
    MODIFY COLUMN `business_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '业务类型编码',
    MODIFY COLUMN `code_root_id` bigint NOT NULL COMMENT '创建时命名空间根项目ID',
    MODIFY COLUMN `code_rule_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'V1' COMMENT '项目编码生成规则版本',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '主责公司ID',
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `department_id` bigint NULL COMMENT '主责部门ID',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `implementation_mode` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '实施模式编码',
    MODIFY COLUMN `lifecycle_template_id` bigint NULL COMMENT '生命周期模板ID',
    MODIFY COLUMN `major_project_level` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '重大项目级别',
    MODIFY COLUMN `manager_id` bigint NULL COMMENT '主负责人用户ID',
    MODIFY COLUMN `parent_id` bigint NULL COMMENT '父记录ID',
    MODIFY COLUMN `project_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目分类',
    MODIFY COLUMN `project_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目编码',
    MODIFY COLUMN `project_sequence` int unsigned NOT NULL DEFAULT 0 COMMENT '永久流水号：0表示独立命名空间',
    MODIFY COLUMN `project_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'STANDARD' COMMENT '项目类型编码',
    MODIFY COLUMN `root_id` bigint NOT NULL COMMENT '项目树根节点项目ID',
    MODIFY COLUMN `sales_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '销售类型编码',
    MODIFY COLUMN `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MANUAL' COMMENT '项目创建来源类型',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `tree_depth` int unsigned NOT NULL DEFAULT 0 COMMENT '节点层级深度缓存：根节点为0',
    MODIFY COLUMN `tree_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目祖先路径缓存',
    MODIFY COLUMN `tree_sort` int NOT NULL DEFAULT 0 COMMENT '排序值',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新人';

ALTER TABLE proj_project_company_department_relation
    MODIFY COLUMN `company_id` bigint NULL COMMENT '平台公司主档ID',
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `department_id` bigint NULL COMMENT '部门ID',
    MODIFY COLUMN `effective_to` datetime(3) NULL COMMENT '生效结束时间',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `is_primary` tinyint NOT NULL DEFAULT 0 COMMENT '同一业务范围内是否为主记录：0否，1是',
    MODIFY COLUMN `relation_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务角色',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新人';

ALTER TABLE proj_project_member_assignment
    MODIFY COLUMN `company_id` bigint NULL COMMENT '加入时公司ID',
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `effective_to` datetime(3) NULL COMMENT '生效结束时间',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `member_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '成员角色编码',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新人';

ALTER TABLE proj_project_party
    MODIFY COLUMN `contact_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '联系人名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `effective_from` datetime(3) NULL COMMENT '生效开始时间',
    MODIFY COLUMN `effective_to` datetime(3) NULL COMMENT '生效结束时间',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `party_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '参与方编码',
    MODIFY COLUMN `party_name` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '参与方名称',
    MODIFY COLUMN `party_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '参与方角色编码',
    MODIFY COLUMN `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '联系人电话',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
    MODIFY COLUMN `source_record_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源记录稳定唯一键',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `source_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统物理表名',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

ALTER TABLE proj_project_portfolio
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '更新人',
    MODIFY COLUMN `valid_from` date NULL COMMENT '有效开始时间',
    MODIFY COLUMN `valid_to` date NULL COMMENT '有效结束时间';

ALTER TABLE proj_project_portfolio_member
    MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '创建人',
    MODIFY COLUMN `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `portfolio_id` bigint NOT NULL COMMENT '项目组合ID',
    MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '更新人';

ALTER TABLE proj_project_split_item
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '办事处编码';

ALTER TABLE proj_project_split_scope
    CHANGE COLUMN `office_department_code` `department_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '办事处编码';

ALTER TABLE proj_project_task_completion_evaluation
    MODIFY COLUMN `execution_contract_id` bigint NOT NULL COMMENT '执行契约版本逻辑引用',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `project_task_id` bigint NOT NULL COMMENT 'ProjectTask逻辑引用',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID';

ALTER TABLE proj_project_task_execution_contract
    MODIFY COLUMN `binding_parameter_snapshot` json NOT NULL COMMENT '冻结绑定参数与Schema版本',
    MODIFY COLUMN `completion_rule_snapshot` json NOT NULL COMMENT '冻结完成规则与Schema版本',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `template_task_definition_id` bigint NULL COMMENT '来源模板任务定义逻辑引用',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '受控换绑乐观锁版本';

ALTER TABLE proj_project_template_task_definition
    MODIFY COLUMN `binding_config` json NOT NULL COMMENT '受控绑定参数与Schema版本',
    MODIFY COLUMN `completion_rule_config` json NOT NULL COMMENT '完成规则配置与Schema版本',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务定义名称',
    MODIFY COLUMN `template_revision_id` bigint NOT NULL COMMENT '项目模板发布版本逻辑引用',
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID';
