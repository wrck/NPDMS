-- SN 在租户内唯一（uk_device_sn / uk_ast_device_shipment_source / uk_ast_device_relationship_source），
-- 设备域同步以来源键与唯一键保证引用完整性，不为外键而设置外键（用户裁定 2026-09-24）。
-- 发货记录与设备关系先于主档行落库不再被外键阻断；关系查询 JOIN ast_device 由应用语义兜底。
ALTER TABLE `ast_device_shipment` DROP FOREIGN KEY `fk_ast_device_shipment_device`;
ALTER TABLE `ast_device_shipment` DROP FOREIGN KEY `fk_ast_device_shipment_related_device`;
ALTER TABLE `ast_device_relationship` DROP FOREIGN KEY `fk_ast_device_relationship_source_device`;
ALTER TABLE `ast_device_relationship` DROP FOREIGN KEY `fk_ast_device_relationship_target_device`;
