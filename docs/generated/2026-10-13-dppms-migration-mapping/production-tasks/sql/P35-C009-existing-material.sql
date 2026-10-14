-- 已有材料采集查询；尚非已保存全量任务，未执行。
-- 生产需覆盖P17全部原项目身份/批准设备或许可范围，不能只遍历规范最大projectId。
SELECT `id`,`projectId`,`changeVersion`,`changeRemark`,`latest`,`createBy`,`createTime`,`updateBy`,`updateTime` FROM `pm_project_soft_change_logs` WHERE `projectId`=:projectId;
