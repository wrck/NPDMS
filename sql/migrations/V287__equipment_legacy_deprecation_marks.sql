-- V261: pms_equipment 旧链废弃标记（需求方2026-09-18指示：完全迁移后为原对象添加废弃标注，便于后续清理）
-- 功能/界面/接口/表关联已全部由 ast_device 体系承接（V114 前向迁移 + V259 承接迁移），
-- 本迁移仅为旧表与旧字典追加可读废弃标记，不删除数据、不改变结构。

-- 旧设备档案主表：历史数据保留为 V114/V259 前向迁移的来源证据，业务读写已全部走 ast_device
ALTER TABLE `pms_equipment` COMMENT = '[DEPRECATED 2026-09-18] pms_equipment 旧链设备档案，已由 ast_device 承接，仅保留历史数据作为前向迁移来源证据，待清理';

-- 旧设备版本历史表：版本记录已由 ast_device_version 承接（V259），本表仅保留历史证据
ALTER TABLE `pms_equipment_version` COMMENT = '[DEPRECATED 2026-09-18] pms_equipment_version 旧链版本历史，已由 ast_device_version 承接，仅保留历史数据，待清理';

-- 旧 Integer 状态字典：前端已切换 pms_device_status（String 值域），旧字典仅历史展示
UPDATE `system_dict_type` SET `remark` = '[DEPRECATED 2026-09-18] 已由 pms_device_status 承接，待清理',
  `update_time` = NOW() WHERE `type` = 'pms_equipment_status' AND `deleted` = b'0';
