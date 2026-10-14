-- 已有材料采集查询；尚非已保存全量任务，未执行。
-- 生产需覆盖P17全部原项目身份/批准设备或许可范围，不能只遍历规范最大projectId。
SELECT `contract_code`,`itemCode`,`itemModel`,`itemName`,`barcode`,`packId`,`itemCode2`,`itemModel2`,`itemName2`,`barcode2`,`profitCenter` FROM `view_shipment_info_4_pm` WHERE `contract_code` IN (:contractNumbersAndOriginalNormalizedVariants);
