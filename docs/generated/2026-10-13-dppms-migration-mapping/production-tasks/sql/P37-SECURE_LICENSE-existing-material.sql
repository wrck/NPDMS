-- 已有材料采集查询；尚非已保存全量任务，未执行。
-- 生产需覆盖P17全部原项目身份/批准设备或许可范围，不能只遍历规范最大projectId。
SELECT id,licenseCode,sn,specModel,contract,contractType,item,status FROM pm_project_license_info_from_license WHERE id IN (:approvedLicenseIds) ORDER BY id;
