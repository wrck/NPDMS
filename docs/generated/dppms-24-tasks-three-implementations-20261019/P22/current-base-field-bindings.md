# P22 新虚基类实际读写字段衔接

当前P22以[订单行延后及原部门编码报告](../base-sync-p22-order-line-preview-20261019.md)和[实际XML](../base-sync-p22-order-line-preview-20261019.sql)为准：p.column001原查询直接带入；department_id/order_line_id均先-1，后处理仅限当前独立绑定；正式订单缺失不阻断读源。当前真实预览缺原部门编码，正数量14293条；2976条零数量按已有裁决待处理。下方旧阶段流程按此修订解释。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。正式订单行缺失：首条源记录id=1的精确订单号/行号/产品码在目标匹配0条，完整项目合同关联也为0条。343858条范围中14525条缺原部门编码，其余非空编码均可按租户匹配。需正式订单输入及缺失部门业务键；数值部门ID用-1不替代缺失编码。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p22)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectProductScope | STRAIGHT_JOIN l.id | sourceId |
| selectProductScope | l.projectId | originalProjectId |
| selectProductScope | canonical.projectId | canonicalProjectId |
| selectProductScope | p.projectCode | originalProjectCode |
| selectProductScope | current_project.projectName | originalProjectName |
| selectProductScope | p.column001 | originalDepartmentCode |
| selectCompanies | id | id |
| selectCompanies | compCode | code |
| selectProductScope | current_project.compId | originalCompanyId |
| selectProductScope | p.projectType | projectType |
| selectProductScope | l.contractNo | contractNo |
| selectProductScope | l.itemCode | itemCode |
| selectProductScope | l.itemName | itemName |
| selectProductScope | l.projectQuantity | projectQuantity |
| selectProductScope | l.orderQuantity | orderQuantity |
| selectProductScope | l.deliverQuantity | deliverQuantity |
| selectProductScope | l.openQuantity | openQuantity |
| selectProductScope | l.orderNumber | orderNumber |
| selectProductScope | l.lineNum | lineNum |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| com_delivery_scope.id | #{row.target.id} | insertProductScope |
| com_delivery_scope.tenant_id | #{row.target.tenantId} | insertProductScope |
| com_delivery_scope.project_id | #{row.target.projectId} | insertProductScope |
| com_delivery_scope.project_code | #{row.target.projectCode} | insertProductScope |
| com_delivery_scope.project_name | #{row.target.projectName} | insertProductScope |
| com_delivery_scope.project_company_code | #{row.target.projectCompanyCode} | insertProductScope |
| com_delivery_scope.project_company_name | #{row.target.projectCompanyName} | insertProductScope |
| com_delivery_scope.project_department_code | #{row.target.projectDepartmentCode} | insertProductScope |
| com_delivery_scope.project_department_name | #{row.target.projectDepartmentName} | insertProductScope |
| com_delivery_scope.project_customer_code | #{row.target.projectCustomerCode} | insertProductScope |
| com_delivery_scope.project_customer_name | #{row.target.projectCustomerName} | insertProductScope |
| com_delivery_scope.project_manager_employee_no | #{row.target.projectManagerEmployeeNo} | insertProductScope |
| com_delivery_scope.project_manager_name | #{row.target.projectManagerName} | insertProductScope |
| com_delivery_scope.order_line_id | #{row.target.orderLineId} | insertProductScope |
| com_delivery_scope.order_source_system | #{row.target.orderSourceSystem} | insertProductScope |
| com_delivery_scope.order_company_code | #{row.target.orderCompanyCode} | insertProductScope |
| com_delivery_scope.order_company_name | #{row.target.orderCompanyName} | insertProductScope |
| com_delivery_scope.order_type | #{row.target.orderType} | insertProductScope |
| com_delivery_scope.order_no | #{row.target.orderNo} | insertProductScope |
| com_delivery_scope.line_no | #{row.target.lineNo} | insertProductScope |
| com_delivery_scope.product_code | #{row.target.productCode} | insertProductScope |
| com_delivery_scope.product_desc | #{row.target.productDesc} | insertProductScope |
| com_delivery_scope.allocated_qty | #{row.target.allocatedQty} | insertProductScope |
| com_delivery_scope.allocation_confirmed | 0 | insertProductScope |
| com_delivery_scope.delivered_quantity | #{row.target.deliveredQuantity} | insertProductScope |
| com_delivery_scope.open_quantity | #{row.target.openQuantity} | insertProductScope |
| com_delivery_scope.scope_status | 'ACTIVE' | insertProductScope |
| com_delivery_scope.allocation_version | #{row.target.allocationVersion} | insertProductScope |
| com_delivery_scope.allocation_source | 'DPPMS' | insertProductScope |
| com_delivery_scope.change_reason | NULL | insertProductScope |
| com_delivery_scope.department_id | #{row.target.departmentId} | insertProductScope |
| com_delivery_scope.department_code | #{row.target.departmentCode} | insertProductScope |
| com_delivery_scope.department_name | #{row.target.departmentName} | insertProductScope |
| com_delivery_scope.department_version | #{row.target.departmentVersion} | insertProductScope |
| com_delivery_scope.source_evidence | #{row.target.sourceEvidence} | insertProductScope |
| com_delivery_scope.effective_from | #{row.target.effectiveFrom} | insertProductScope |
| com_delivery_scope.effective_to | NULL | insertProductScope |
| com_delivery_scope.status | 'ENABLED' | insertProductScope |
| com_delivery_scope.version | 0 | insertProductScope |
| com_delivery_scope.creator | #{row.target.creator} | insertProductScope |
| com_delivery_scope.create_time | #{row.target.createTime} | insertProductScope |
| com_delivery_scope.updater | #{row.target.updater} | insertProductScope |
| com_delivery_scope.update_time | #{row.target.updateTime} | insertProductScope |
| com_delivery_scope.deleted | 0 | insertProductScope |
| com_delivery_scope_detail.id | #{row.detail.id} | insertDetails |
| com_delivery_scope_detail.tenant_id | #{row.detail.tenantId} | insertDetails |
| com_delivery_scope_detail.delivery_scope_id | #{row.detail.deliveryScopeId} | insertDetails |
| com_delivery_scope_detail.detail_sequence | 1 | insertDetails |
| com_delivery_scope_detail.serial_no | NULL | insertDetails |
| com_delivery_scope_detail.product_code | #{row.detail.productCode} | insertDetails |
| com_delivery_scope_detail.product_name | #{row.detail.productName} | insertDetails |
| com_delivery_scope_detail.device_type_code | NULL | insertDetails |
| com_delivery_scope_detail.device_type_name | NULL | insertDetails |
| com_delivery_scope_detail.delivery_batch_no | NULL | insertDetails |
| com_delivery_scope_detail.source_record_key | #{row.detail.sourceRecordKey} | insertDetails |
| com_delivery_scope_detail.allocated_qty | #{row.detail.allocatedQty} | insertDetails |
| com_delivery_scope_detail.detail_status | 'ACTIVE' | insertDetails |
| com_delivery_scope_detail.source_snapshot | NULL | insertDetails |
| com_delivery_scope_detail.remark | NULL | insertDetails |
| com_delivery_scope_detail.version | 0 | insertDetails |
| com_delivery_scope_detail.creator | #{row.detail.creator} | insertDetails |
| com_delivery_scope_detail.create_time | #{row.detail.createTime} | insertDetails |
| com_delivery_scope_detail.updater | #{row.detail.updater} | insertDetails |
| com_delivery_scope_detail.update_time | #{row.detail.updateTime} | insertDetails |
| com_delivery_scope_detail.deleted | 0 | insertDetails |

当前Java映射：department_id固定-1；department_code/name/version来自本租户精确编码目录。非预览work末尾只调用一次backfillDepartmentIds；具体限定与参数见[实际XML](../base-sync-department-code-preview-20261019.sql)。

