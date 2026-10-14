-- 已有材料采集查询；尚非已保存全量任务，未执行。
-- 生产需覆盖P17全部原项目身份/批准设备或许可范围，不能只遍历规范最大projectId。
SELECT `serial_number`,`conp`,`cpld`,`boot`,`pcb` FROM `fb_soft_version` WHERE `serial_number` IN (:shipmentAndInstallationSerials);
