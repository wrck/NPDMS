# P13 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `com_crm_execution_order` (`id`, `tenant_id`, `source_system`, `execution_no`, `project_code`, `project_name`, `primary_project_id`, `sales_rep_code`, `sales_rep_name`, `sales_rep_phone`, `market_code`, `market_name`, `system_source_key`, `system_code`, `system_name`, `expend_source_key`, `expend_code`, `expend_name`, `industry_code`, `industry_name`, `department_id`, `department_code`, `department_name`, `service_type_name`, `channel_name`, `engineering_fee_raw`, `engineering_fee`, `source_object_id`, `apply_type`, `company_id`, `company_code`, `company_name`, `customer_project_name`, `final_customer_name`, `agent_name`, `project_manager_code`, `project_manager_name`, `decision_path`, `required_in_date`, `receiver_name`, `receiver_contact`, `receiver_address`, `loan_reason`, `project_type`, `major_project_level`, `project_amount`, `af_project_amount`, `submit_time`, `predicted_bid_time`, `contact_name`, `contact_phone`, `af_evidence_status`, `source_sync_time`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_source_system, :orm_execution_no, :orm_project_code, :orm_project_name, :orm_primary_project_id, :orm_sales_rep_code, :orm_sales_rep_name, :orm_sales_rep_phone, :orm_market_code, :orm_market_name, :orm_system_source_key, :orm_system_code, :orm_system_name, :orm_expend_source_key, :orm_expend_code, :orm_expend_name, :orm_industry_code, :orm_industry_name, :orm_department_id, :orm_department_code, :orm_department_name, :orm_service_type_name, :orm_channel_name, :orm_engineering_fee_raw, :orm_engineering_fee, :orm_source_object_id, :orm_apply_type, :orm_company_id, :orm_company_code, :orm_company_name, :orm_customer_project_name, :orm_final_customer_name, :orm_agent_name, :orm_project_manager_code, :orm_project_manager_name, :orm_decision_path, :orm_required_in_date, :orm_receiver_name, :orm_receiver_contact, :orm_receiver_address, :orm_loan_reason, :orm_project_type, :orm_major_project_level, :orm_project_amount, :orm_af_project_amount, :orm_submit_time, :orm_predicted_bid_time, :orm_contact_name, :orm_contact_phone, :orm_af_evidence_status, :orm_source_sync_time, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_crm_execution_order.tenant_id | #{row.tenantId} | insertRow |
| com_crm_execution_order.version | 0 | insertRow |
| com_crm_execution_order.creator | #{row.creator} | insertRow |
| com_crm_execution_order.updater | #{row.updater} | insertRow |
| com_crm_execution_order.create_time | CURRENT_TIMESTAMP | insertRow |
| com_crm_execution_order.update_time | CURRENT_TIMESTAMP | insertRow |
| com_crm_execution_order.deleted | FALSE | insertRow |
| com_crm_execution_order.source_system | #{row.sourceSystem} | insertRow |
| com_crm_execution_order.execution_no | #{row.executionNo} | insertRow |
| com_crm_execution_order.project_code | #{row.projectCode} | insertRow |
| com_crm_execution_order.project_name | #{row.projectName} | insertRow |
| com_crm_execution_order.sales_rep_code | #{row.salesRepCode} | insertRow |
| com_crm_execution_order.sales_rep_name | #{row.salesRepName} | insertRow |
| com_crm_execution_order.market_code | #{row.marketCode} | insertRow |
| com_crm_execution_order.market_name | #{row.marketName} | insertRow |
| com_crm_execution_order.system_source_key | #{row.systemSourceKey} | insertRow |
| com_crm_execution_order.system_name | #{row.systemName} | insertRow |
| com_crm_execution_order.expend_source_key | #{row.expendSourceKey} | insertRow |
| com_crm_execution_order.expend_name | #{row.expendName} | insertRow |
| com_crm_execution_order.industry_code | #{row.industryCode} | insertRow |
| com_crm_execution_order.industry_name | #{row.industryName} | insertRow |
| com_crm_execution_order.department_id | #{row.departmentId} | insertRow |
| com_crm_execution_order.department_code | #{row.departmentCode} | insertRow |
| com_crm_execution_order.department_name | #{row.departmentName} | insertRow |
| com_crm_execution_order.service_type_name | #{row.serviceTypeName} | insertRow |
| com_crm_execution_order.channel_name | #{row.channelName} | insertRow |
| com_crm_execution_order.engineering_fee_raw | #{row.engineeringFeeRaw} | insertRow |
| com_crm_execution_order.engineering_fee | #{row.engineeringFee} | insertRow |
| com_crm_execution_order.source_object_id | #{row.sourceObjectId} | insertRow |
| com_crm_execution_order.apply_type | #{row.applyType} | insertRow |
| com_crm_execution_order.company_id | #{row.companyId} | insertRow |
| com_crm_execution_order.company_code | #{row.companyCode} | insertRow |
| com_crm_execution_order.company_name | #{row.companyName} | insertRow |
| com_crm_execution_order.customer_project_name | #{row.customerProjectName} | insertRow |
| com_crm_execution_order.final_customer_name | #{row.finalCustomerName} | insertRow |
| com_crm_execution_order.agent_name | #{row.agentName} | insertRow |
| com_crm_execution_order.major_project_level | #{row.majorProjectLevel} | insertRow |
| com_crm_execution_order.project_amount | #{row.projectAmount} | insertRow |
| com_crm_execution_order.submit_time | #{row.submitTime} | insertRow |
| com_crm_execution_order.predicted_bid_time | #{row.predictedBidTime} | insertRow |
| com_crm_execution_order.contact_name | #{row.contactName} | insertRow |
| com_crm_execution_order.contact_phone | #{row.contactPhone} | insertRow |
| com_crm_execution_order.source_sync_time | #{row.sourceSyncTime} | insertRow |
| com_crm_execution_order.status | #{row.status} | insertRow |
| com_crm_execution_order.af_evidence_status | #{row.afEvidenceStatus} | insertRow |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_crm_execution_order.id | #{row.target.id} | insertExecutionOrder |
| com_crm_execution_order.tenant_id | #{row.target.tenantId} | insertExecutionOrder |
| com_crm_execution_order.version | 0 | insertExecutionOrder |
| com_crm_execution_order.creator | #{row.target.creator} | insertExecutionOrder |
| com_crm_execution_order.updater | #{row.target.updater} | insertExecutionOrder |
| com_crm_execution_order.create_time | #{row.target.createTime} | insertExecutionOrder |
| com_crm_execution_order.update_time | #{row.target.createTime} | insertExecutionOrder |
| com_crm_execution_order.deleted | FALSE | insertExecutionOrder |
| com_crm_execution_order.source_system | #{row.target.sourceSystem} | insertExecutionOrder |
| com_crm_execution_order.execution_no | #{row.target.executionNo} | insertExecutionOrder |
| com_crm_execution_order.project_code | #{row.target.projectCode} | insertExecutionOrder |
| com_crm_execution_order.project_name | #{row.target.projectName} | insertExecutionOrder |
| com_crm_execution_order.sales_rep_code | #{row.target.salesRepCode} | insertExecutionOrder |
| com_crm_execution_order.sales_rep_name | #{row.target.salesRepName} | insertExecutionOrder |
| com_crm_execution_order.market_code | #{row.target.marketCode} | insertExecutionOrder |
| com_crm_execution_order.market_name | #{row.target.marketName} | insertExecutionOrder |
| com_crm_execution_order.system_source_key | #{row.target.systemSourceKey} | insertExecutionOrder |
| com_crm_execution_order.system_name | #{row.target.systemName} | insertExecutionOrder |
| com_crm_execution_order.expend_source_key | #{row.target.expendSourceKey} | insertExecutionOrder |
| com_crm_execution_order.expend_name | #{row.target.expendName} | insertExecutionOrder |
| com_crm_execution_order.industry_code | #{row.target.industryCode} | insertExecutionOrder |
| com_crm_execution_order.industry_name | #{row.target.industryName} | insertExecutionOrder |
| com_crm_execution_order.department_id | #{row.target.departmentId} | insertExecutionOrder |
| com_crm_execution_order.department_code | #{row.target.departmentCode} | insertExecutionOrder |
| com_crm_execution_order.department_name | #{row.target.departmentName} | insertExecutionOrder |
| com_crm_execution_order.service_type_name | #{row.target.serviceTypeName} | insertExecutionOrder |
| com_crm_execution_order.channel_name | #{row.target.channelName} | insertExecutionOrder |
| com_crm_execution_order.engineering_fee_raw | #{row.target.engineeringFeeRaw} | insertExecutionOrder |
| com_crm_execution_order.engineering_fee | #{row.target.engineeringFee} | insertExecutionOrder |
| com_crm_execution_order.source_object_id | #{row.target.sourceObjectId} | insertExecutionOrder |
| com_crm_execution_order.apply_type | #{row.target.applyType} | insertExecutionOrder |
| com_crm_execution_order.company_id | #{row.target.companyId} | insertExecutionOrder |
| com_crm_execution_order.company_code | #{row.target.companyCode} | insertExecutionOrder |
| com_crm_execution_order.company_name | #{row.target.companyName} | insertExecutionOrder |
| com_crm_execution_order.customer_project_name | #{row.target.customerProjectName} | insertExecutionOrder |
| com_crm_execution_order.final_customer_name | #{row.target.finalCustomerName} | insertExecutionOrder |
| com_crm_execution_order.agent_name | #{row.target.agentName} | insertExecutionOrder |
| com_crm_execution_order.major_project_level | #{row.target.majorProjectLevel} | insertExecutionOrder |
| com_crm_execution_order.project_amount | #{row.target.projectAmount} | insertExecutionOrder |
| com_crm_execution_order.submit_time | #{row.target.submitTime} | insertExecutionOrder |
| com_crm_execution_order.predicted_bid_time | #{row.target.predictedBidTime} | insertExecutionOrder |
| com_crm_execution_order.contact_name | #{row.target.contactName} | insertExecutionOrder |
| com_crm_execution_order.contact_phone | #{row.target.contactPhone} | insertExecutionOrder |
| com_crm_execution_order.source_sync_time | #{row.target.sourceSyncTime} | insertExecutionOrder |
| com_crm_execution_order.status | #{row.target.status} | insertExecutionOrder |
| com_crm_execution_order.af_evidence_status | #{row.target.afEvidenceStatus} | insertExecutionOrder |
