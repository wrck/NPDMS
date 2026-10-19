# P22 原表逐字段承载清单

当前P22以[订单行延后及原部门编码报告](../base-sync-p22-order-line-preview-20261019.md)和[实际XML](../base-sync-p22-order-line-preview-20261019.sql)为准：p.column001原查询直接带入；department_id/order_line_id均先-1，后处理仅限当前独立绑定；正式订单缺失不阻断读源。当前真实预览缺原部门编码，正数量14293条；2976条零数量按已有裁决待处理。下方旧阶段流程按此修订解释。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。正式订单行缺失：首条源记录id=1的精确订单号/行号/产品码在目标匹配0条，完整项目合同关联也为0条。343858条范围中14525条缺原部门编码，其余非空编码均可按租户匹配。需正式订单输入及缺失部门业务键；数值部门ID用-1不替代缺失编码。

合并组件：P22。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_product_line.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P22 |
| pm_project_product_line.projectId | int(11) / YES | com_delivery_scope.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。；业务字段/正式关系 | P22 |
| pm_project_product_line.contractNo | varchar(45) / YES | com_sales_order.contract_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product；业务字段/正式关系 | P22 |
| pm_project_product_line.itemCode | varchar(15) / YES | com_sales_order_line.product_code ＋ com_delivery_scope_detail.product_code | 按正式订单头/行及范围关联承接，不重建独立proj_project_product；业务字段/正式关系 | P22 |
| pm_project_product_line.itemName | varchar(255) / YES | com_delivery_scope.product_desc | 按正式订单头/行及范围关联承接，不重建独立proj_project_product；业务字段/正式关系 | P22 |
| pm_project_product_line.projectQuantity | int(11) / YES | com_delivery_scope.allocated_qty | 原projectQuantity优先，NULL取正式订单行order_qty；正数量约束保持，0/负数阻断；allocation_confirmed=false；业务字段/正式关系 | P22 |
| pm_project_product_line.orderQuantity | int(11) / YES | com_sales_order_line.order_qty | 通过精确order_line_id复用，非独立范围数量镜像；历史差异不得擅自吞掉；业务字段/正式关系 | P22 |
| pm_project_product_line.deliverQuantity | int(11) / YES | com_delivery_scope.delivered_quantity | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P22 |
| pm_project_product_line.openQuantity | int(11) / YES | com_delivery_scope.open_quantity | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P22 |
| pm_project_product_line.orderNumber | varchar(25) / YES | com_sales_order_line.order_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product；业务字段/正式关系 | P22 |
| pm_project_product_line.lineNum | varchar(25) / YES | com_sales_order_line.line_no | 按正式订单头/行及范围关联承接，不重建独立proj_project_product；业务字段/正式关系 | P22 |
