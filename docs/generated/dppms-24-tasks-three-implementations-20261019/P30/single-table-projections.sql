-- P30 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE ast_device_shipment; dialect=MYSQL
SELECT
  -- ast_device_shipment.id -> ast_device.shipment_record_id; 正式发货读取/设备Owner
  s.`id` AS `raw__shipment_record_id`,
  -- ast_device_shipment.device_sn -> ast_device.sn；ast_device_project_relationship.device_sn; 正式发货读取/设备Owner
  s.`device_sn` AS `raw__sn`,
  -- ast_device_shipment.shipment_time -> ast_device.shipment_time; 正式发货读取/设备Owner
  s.`shipment_time` AS `raw__shipment_time`,
  -- ast_device_shipment.package_no -> ast_device.package_no; 正式发货读取/设备Owner
  s.`package_no` AS `raw__package_no`,
  -- ast_device_shipment.contract_no -> ast_device.contract_no; 正式发货读取/设备Owner
  s.`contract_no` AS `raw__contract_no`,
  -- ast_device_shipment.event_type -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`event_type` AS `raw__event_type`,
  -- ast_device_shipment.warranty_start_date -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`warranty_start_date` AS `raw__warranty_start_date`,
  -- ast_device_shipment.warranty_months -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`warranty_months` AS `raw__warranty_months`,
  -- ast_device_shipment.rma_no -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`rma_no` AS `raw__rma_no`,
  -- ast_device_shipment.related_device_sn -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`related_device_sn` AS `raw__related_device_sn`,
  -- ast_device_shipment.source_system -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`source_system` AS `raw__source_system`,
  -- ast_device_shipment.source_key -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`source_key` AS `raw__source_key`,
  -- ast_device_shipment.source_version -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`source_version` AS `raw__source_version`,
  -- ast_device_shipment.source_updated_at -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`source_updated_at` AS `raw__source_updated_at`,
  -- ast_device_shipment.synced_at -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`synced_at` AS `raw__synced_at`,
  -- ast_device_shipment.sync_status -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`sync_status` AS `raw__sync_status`,
  -- ast_device_shipment.version -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`version` AS `raw__version`,
  -- ast_device_shipment.creator -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`creator` AS `raw__creator`,
  -- ast_device_shipment.create_time -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`create_time` AS `raw__create_time`,
  -- ast_device_shipment.updater -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`updater` AS `raw__updater`,
  -- ast_device_shipment.update_time -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`update_time` AS `raw__update_time`,
  -- ast_device_shipment.deleted -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`deleted` AS `raw__deleted`,
  -- ast_device_shipment.tenant_id -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`tenant_id` AS `raw__tenant_id`,
  -- ast_device_shipment.legacy_package_key -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`legacy_package_key` AS `raw__legacy_package_key`,
  -- ast_device_shipment.rma_related_sn -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`rma_related_sn` AS `raw__rma_related_sn`,
  -- ast_device_shipment.rma_marked -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`rma_marked` AS `raw__rma_marked`,
  -- ast_device_shipment.order_line_id -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`order_line_id` AS `raw__order_line_id`,
  -- ast_device_shipment.source_payload -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`source_payload` AS `raw__source_payload`,
  -- ast_device_shipment.order_no -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`order_no` AS `raw__order_no`,
  -- ast_device_shipment.line_no -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`line_no` AS `raw__line_no`,
  -- ast_device_shipment.product_code -> ast_device.product_code; 正式发货读取/设备Owner
  s.`product_code` AS `raw__product_code`,
  -- ast_device_shipment.secondary_sn -> ast_device.secondary_sn; 正式发货读取/设备Owner
  s.`secondary_sn` AS `raw__secondary_sn`,
  -- ast_device_shipment.secondary_item -> ast_device.secondary_item; 正式发货读取/设备Owner
  s.`secondary_item` AS `raw__secondary_item`,
  -- ast_device_shipment.internal_serial_no -> ast_device.internal_serial_no; 正式发货读取/设备Owner
  s.`internal_serial_no` AS `raw__internal_serial_no`,
  -- ast_device_shipment.is_rma -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`is_rma` AS `raw__is_rma`,
  -- ast_device_shipment.profit_center -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`profit_center` AS `raw__profit_center`,
  -- ast_device_shipment.sole_agent_suffix -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`sole_agent_suffix` AS `raw__sole_agent_suffix`,
  -- ast_device_shipment.sync_time -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`sync_time` AS `raw__sync_time`,
  -- ast_device_shipment.uuid -> P28已承接发货事实；P30核对/保持; 前置正式事实
  s.`uuid` AS `raw__uuid`
FROM `ast_device_shipment` s;

-- SOURCE view_shipment_info_4_pm; dialect=MYSQL
SELECT
  -- view_shipment_info_4_pm.contract_code -> ast_device_shipment.contract_no; 业务字段/正式关系
  s.`contract_code` AS `raw__contract_no`,
  -- view_shipment_info_4_pm.itemCode -> ast_device_shipment.product_code; 业务字段/正式关系
  s.`itemCode` AS `raw__product_code`,
  -- view_shipment_info_4_pm.itemModel -> 产品编码关联参考；不写产品异名扩展; 最新P12规则；消费接入待完成
  s.`itemModel` AS `raw__itemModel`,
  -- view_shipment_info_4_pm.itemName -> 产品编码关联参考；不写产品异名扩展; 最新P12规则；消费接入待完成
  s.`itemName` AS `raw__itemName`,
  -- view_shipment_info_4_pm.barcode -> ast_device_shipment.device_sn; 业务字段/正式关系
  s.`barcode` AS `raw__device_sn`,
  -- view_shipment_info_4_pm.packId -> ast_device_shipment.package_no; 业务字段/正式关系
  s.`packId` AS `raw__package_no`,
  -- view_shipment_info_4_pm.itemCode2 -> ast_device_shipment.secondary_item; 业务字段/正式关系
  s.`itemCode2` AS `raw__secondary_item`,
  -- view_shipment_info_4_pm.itemModel2 -> 产品编码关联参考；不写产品异名扩展; 最新P12规则；消费接入待完成
  s.`itemModel2` AS `raw__itemModel2`,
  -- view_shipment_info_4_pm.itemName2 -> 产品编码关联参考；不写产品异名扩展; 最新P12规则；消费接入待完成
  s.`itemName2` AS `raw__itemName2`,
  -- view_shipment_info_4_pm.barcode2 -> ast_device_shipment.secondary_sn; 业务字段/正式关系
  s.`barcode2` AS `raw__secondary_sn`,
  -- view_shipment_info_4_pm.profitCenter -> ast_device_shipment.profit_center; 业务字段/正式关系
  s.`profitCenter` AS `raw__profit_center`
FROM `view_shipment_info_4_pm` s;
