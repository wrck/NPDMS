# P30 原表逐字段承载清单

合并组件：P30。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| ast_device_shipment.id | bigint / NO | ast_device.shipment_record_id | 仅最新获准发货记录，正式id直接引用其正式记录。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.device_sn | varchar(100) / NO | ast_device.sn；ast_device_project_relationship.device_sn | 按租户＋SN精确身份。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.shipment_time | datetime(3) / YES | ast_device.shipment_time | 缺失设备从最新发货取值；已有更晚事实保护。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.package_no | varchar(128) / YES | ast_device.package_no | 从核实的最新发货取值。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.contract_no | varchar(64) / YES | ast_device.contract_no | 必须与命令已核实合同一致，已有归属冲突不抢占。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.event_type | varchar(32) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.warranty_start_date | date / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.warranty_months | int unsigned / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.rma_no | varchar(128) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.related_device_sn | varchar(100) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.source_system | varchar(32) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.source_key | varchar(128) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.source_version | varchar(64) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.source_updated_at | datetime(3) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.synced_at | datetime(3) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.sync_status | varchar(32) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.version | int unsigned / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.creator | varchar(64) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.create_time | datetime(3) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.updater | varchar(64) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.update_time | datetime(3) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.deleted | bit(1) / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.tenant_id | bigint / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.legacy_package_key | varchar(128) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.rma_related_sn | varchar(100) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.rma_marked | tinyint / NO | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.order_line_id | bigint / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.source_payload | json / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.order_no | varchar(64) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.line_no | varchar(64) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.product_code | varchar(64) / YES | ast_device.product_code | P12正式目录编码；名称/型号采用正式目录。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.secondary_sn | varchar(100) / YES | ast_device.secondary_sn | 仅当前选定发货记录且现值兼容时补缺。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.secondary_item | varchar(64) / YES | ast_device.secondary_item | 仅当前选定发货记录且现值兼容时补缺。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.internal_serial_no | varchar(128) / YES | ast_device.internal_serial_no | 仅当前选定发货记录且现值兼容时补缺。；正式发货读取/设备Owner | P30 |
| ast_device_shipment.is_rma | int / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.profit_center | varchar(32) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.sole_agent_suffix | varchar(32) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.sync_time | datetime / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| ast_device_shipment.uuid | varchar(64) / YES | P28已承接发货事实；P30核对/保持 | 本任务不把所有发货列复制到设备；importShipment可能对获准既有发货空值补缺，须与P28共同核对。；前置正式事实 | P30 |
| view_shipment_info_4_pm.contract_code | varchar(25) / YES | ast_device_shipment.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.itemCode | varchar(16) / YES | ast_device_shipment.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.itemModel | varchar(255) / YES | 产品编码关联参考；不写产品异名扩展 | 当前来源材料用于核对；生产设备名称/型号使用P12正式目录CRM优先值，消费路径待接入。；最新P12规则；消费接入待完成 | P30 |
| view_shipment_info_4_pm.itemName | varchar(255) / YES | 产品编码关联参考；不写产品异名扩展 | 当前来源材料用于核对；生产设备名称/型号使用P12正式目录CRM优先值，消费路径待接入。；最新P12规则；消费接入待完成 | P30 |
| view_shipment_info_4_pm.barcode | varchar(50) / YES | ast_device_shipment.device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.packId | varchar(64) / YES | ast_device_shipment.package_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.itemCode2 | varchar(16) / YES | ast_device_shipment.secondary_item | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.itemModel2 | varchar(255) / YES | 产品编码关联参考；不写产品异名扩展 | 当前来源材料用于核对；生产设备名称/型号使用P12正式目录CRM优先值，消费路径待接入。；最新P12规则；消费接入待完成 | P30 |
| view_shipment_info_4_pm.itemName2 | varchar(255) / YES | 产品编码关联参考；不写产品异名扩展 | 当前来源材料用于核对；生产设备名称/型号使用P12正式目录CRM优先值，消费路径待接入。；最新P12规则；消费接入待完成 | P30 |
| view_shipment_info_4_pm.barcode2 | varchar(50) / YES | ast_device_shipment.secondary_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
| view_shipment_info_4_pm.profitCenter | varchar(32) / YES | ast_device_shipment.profit_center | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P30 |
