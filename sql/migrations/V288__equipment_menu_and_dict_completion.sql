-- =============================================================================
-- V288: 设备菜单归位与 String 状态字典补齐
-- 背景：设备承接迁移（V285 编号，内容为 equipment -> device 迁移）在并行工作区
--       先行应用时仅覆盖建表与数据段，菜单归位、权限改名与 pms_device_status
--       字典段未生效；本迁移以幂等语句补齐，与 V285 对应段内容一致。
-- 编号说明：pms_device_status 字典使用高段 ID；低段 21510-21513 已被
--           pms_batch_change_status 占用，2061 已被其 dict_type 占用，不可复用。
-- =============================================================================

-- 菜单权限并入设备权限族（pms:equipment:* -> pms:device:*）
UPDATE `system_menu` SET `permission` = 'pms:device:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment:query' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:create', `update_time` = NOW() WHERE `permission` = 'pms:equipment:create' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:update', `update_time` = NOW() WHERE `permission` = 'pms:equipment:update' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:delete', `update_time` = NOW() WHERE `permission` = 'pms:equipment:delete' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:status-change', `update_time` = NOW() WHERE `permission` = 'pms:equipment:status-change' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device-version:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment-version:query' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment-config:query' AND `deleted` = b'0';

-- 菜单归位：设备档案页与配置日志页并入 device 域（组件文件已随旧链删除，不改则页面 404）
UPDATE `system_menu` SET `path` = 'device-archive', `component` = 'pms/asset/device/archive/index',
  `component_name` = 'PmsAssetDeviceArchive', `update_time` = NOW() WHERE `id` = 19001 AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = 'device-config-log', `component` = 'pms/asset/device/config-log/index',
  `component_name` = 'PmsAssetDeviceConfigLog', `update_time` = NOW() WHERE `id` = 19008 AND `deleted` = b'0';

-- 设备档案状态字典（String 值域，与 ast_device.status 存量值一致；原 pms_equipment_status Integer 字典保留为历史）
-- 本字典数据由本迁移全权维护：先清理本字典已有行（含早前误用低段 ID 的残留），再幂等插入高段五条。
DELETE FROM `system_dict_data` WHERE `dict_type` = 'pms_device_status';

INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(993109100201, 'PMS-设备档案状态', 'pms_device_status', 0, '设备档案状态（ast_device 承载，String 值域）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(993109101101, 0, '在库', 'IN_STOCK', 'pms_device_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(993109101102, 1, '在用', 'IN_USE', 'pms_device_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(993109101103, 2, '故障', 'FAULT', 'pms_device_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(993109101104, 3, '维修中', 'REPAIRING', 'pms_device_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(993109101105, 4, '已报废', 'RETIRED', 'pms_device_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
