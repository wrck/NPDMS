-- P13 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [EXECUTION_ORDER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P13.json
SELECT r.*
FROM pm_project_property_from_sms r
JOIN (
  SELECT CAST(dataSource AS BINARY) AS sourceKey,
    CAST(orderExecNumber AS BINARY) AS executionKey, MAX(id) AS selectedId
  FROM pm_project_property_from_sms
  GROUP BY CAST(dataSource AS BINARY), CAST(orderExecNumber AS BINARY)
) chosen ON chosen.selectedId=r.id;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.selectIncoming; pms-module-commerce/src/main/resources/mapper/executionorder/CrmExecutionOrderMapper.xml:5; kind=select
SELECT * FROM com_crm_execution_order
        WHERE tenant_id = #{query.tenantId}
          AND source_system = #{query.sourceSystem}
         /* CHOOSE  */ /* WHEN test=query.executionNumbers != null and !query.executionNumbers.isEmpty() */ AND execution_no IN
                 /* FOREACH collection=query.executionNumbers; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.selectActiveByExecutionNos; pms-module-commerce/src/main/resources/mapper/executionorder/CrmExecutionOrderMapper.xml:17; kind=select
SELECT * FROM com_crm_execution_order
        WHERE tenant_id = #{query.tenantId} AND deleted = 0 AND status = 'ACTIVE'
           /* CHOOSE  */ /* WHEN test=query.executionNos != null and !query.executionNos.isEmpty() */ AND execution_no IN
                   /* FOREACH collection=query.executionNos; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY submit_time DESC, id DESC;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.selectActiveForUpdate; pms-module-commerce/src/main/resources/mapper/executionorder/CrmExecutionOrderMapper.xml:41; kind=select
SELECT * FROM com_crm_execution_order
        WHERE tenant_id = #{query.tenantId} AND deleted = 0 AND status = 'ACTIVE'
           /* CHOOSE  */ /* WHEN test=query.executionNos != null and !query.executionNos.isEmpty() */ AND execution_no IN
                   /* FOREACH collection=query.executionNos; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY submit_time DESC, id DESC
        FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:com_crm_execution_order; pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/executionorder/CrmExecutionOrderDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `com_crm_execution_order` (`id`, `tenant_id`, `source_system`, `execution_no`, `project_code`, `project_name`, `primary_project_id`, `sales_rep_code`, `sales_rep_name`, `sales_rep_phone`, `market_code`, `market_name`, `system_source_key`, `system_code`, `system_name`, `expend_source_key`, `expend_code`, `expend_name`, `industry_code`, `industry_name`, `department_id`, `department_code`, `department_name`, `service_type_name`, `channel_name`, `engineering_fee_raw`, `engineering_fee`, `source_object_id`, `apply_type`, `company_id`, `company_code`, `company_name`, `customer_project_name`, `final_customer_name`, `agent_name`, `project_manager_code`, `project_manager_name`, `decision_path`, `required_in_date`, `receiver_name`, `receiver_contact`, `receiver_address`, `loan_reason`, `project_type`, `major_project_level`, `project_amount`, `af_project_amount`, `submit_time`, `predicted_bid_time`, `contact_name`, `contact_phone`, `af_evidence_status`, `source_sync_time`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_source_system, :orm_execution_no, :orm_project_code, :orm_project_name, :orm_primary_project_id, :orm_sales_rep_code, :orm_sales_rep_name, :orm_sales_rep_phone, :orm_market_code, :orm_market_name, :orm_system_source_key, :orm_system_code, :orm_system_name, :orm_expend_source_key, :orm_expend_code, :orm_expend_name, :orm_industry_code, :orm_industry_name, :orm_department_id, :orm_department_code, :orm_department_name, :orm_service_type_name, :orm_channel_name, :orm_engineering_fee_raw, :orm_engineering_fee, :orm_source_object_id, :orm_apply_type, :orm_company_id, :orm_company_code, :orm_company_name, :orm_customer_project_name, :orm_final_customer_name, :orm_agent_name, :orm_project_manager_code, :orm_project_manager_name, :orm_decision_path, :orm_required_in_date, :orm_receiver_name, :orm_receiver_contact, :orm_receiver_address, :orm_loan_reason, :orm_project_type, :orm_major_project_level, :orm_project_amount, :orm_af_project_amount, :orm_submit_time, :orm_predicted_bid_time, :orm_contact_name, :orm_contact_phone, :orm_af_evidence_status, :orm_source_sync_time, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.updatePrimaryProjectIfUnbound; pms-module-commerce/src/main/resources/mapper/executionorder/CrmExecutionOrderMapper.xml:30; kind=update
UPDATE com_crm_execution_order
        SET primary_project_id = #{query.projectId}, updater = #{query.updater},
            update_time = CURRENT_TIMESTAMP(3)
        WHERE tenant_id = #{query.tenantId} AND id = #{query.id} AND deleted = 0 AND status = 'ACTIVE'
          AND CAST(company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY)
          AND CAST(execution_no AS BINARY) = CAST(#{query.executionNo} AS BINARY)
          AND CAST(source_system AS BINARY) = CAST(#{query.sourceSystem} AS BINARY)
           /* IF test=query.companyId != null */ AND company_id = #{query.companyId} /* END IF */ 
          AND (primary_project_id IS NULL OR primary_project_id = #{query.projectId});

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
