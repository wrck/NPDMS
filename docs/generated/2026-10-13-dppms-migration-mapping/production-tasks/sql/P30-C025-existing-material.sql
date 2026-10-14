-- 已有材料采集查询；尚非已保存全量任务，未执行。
-- 生产需覆盖P17全部原项目身份/批准设备或许可范围，不能只遍历规范最大projectId。
SELECT `id`,`pack_id`,`item`,`barcode`,`com_barcode`,`rma_no`,`isRMA`,`item2`,`barcode2`,`orderNumber`,`lineNum`,`profitCenter`,`soleAgentSuffix`,`warrantyStartDate`,`warrantyMonth`,`rmaBarcode`,`updateTime`,`syncTime`,`uuid` FROM `fb_shipment_barcode` WHERE `id` IN (:sourceShipmentIds);
