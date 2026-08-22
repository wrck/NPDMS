-- V68: 组织与AST地点管理菜单及代表性数据（INT-09 / PM-01 / PM-08 / EXE-02 / EQP-01）
-- 代表性数据仅用于0→1开发验证；使用930800高段ID和固定creator，不臆造CRM属性。

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(930800, '公司管理', '', 2, 5, 1, 'company', 'ep:office-building', 'system/company/index', 'SystemCompany', 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930801, '公司查询', 'system:company:query', 3, 1, 930800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930802, '公司新增', 'system:company:create', 3, 2, 930800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930803, '公司修订', 'system:company:update', 3, 3, 930800, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930810, '地址管理', 'pms:asset-location:query', 2, 20, 19260, 'asset-address', 'ep:map-location', 'pms/asset/location/address/index', 'PmsAssetAddress', 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930811, '站点管理', 'pms:asset-location:query', 2, 21, 19260, 'asset-site', 'ep:location', 'pms/asset/location/site/index', 'PmsAssetSite', 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930812, '服务办事处映射', 'pms:asset-location:query', 2, 22, 19260, 'area-department', 'ep:connection', 'pms/asset/location/area-department/index', 'PmsAssetAreaDepartment', 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930813, '地点维护', 'pms:asset-location:update', 3, 1, 930810, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930814, '站点位置维护', 'pms:asset-location:update', 3, 1, 930811, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0'),
(930815, '映射维护', 'pms:asset-location:update', 3, 1, 930812, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'v68-org-location', NOW(), 'v68-org-location', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `parent_id`=VALUES(`parent_id`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `updater`='v68-org-location', `update_time`=NOW(), `deleted`=b'0';

INSERT IGNORE INTO `system_company` (`id`, `tenant_id`, `code`, `name`, `status`, `version`, `creator`, `updater`, `deleted`)
VALUES (930800, 1, 'DPTECH-DEMO', '迪普科技示例公司', 0, 0, 'v68-org-location', 'v68-org-location', b'0');

INSERT IGNORE INTO `system_dept` (`id`, `code`, `name`, `parent_id`, `sort`, `status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
VALUES (930801, 'OFFICE-HZ-DEMO', '杭州服务办事处', 100, 68, 0, 0, 'v68-org-location', 'v68-org-location', b'0', 1);

INSERT IGNORE INTO `ast_address` (`id`, `tenant_id`, `country_code`, `country_name`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail_address`, `full_address`, `longitude`, `latitude`, `status`, `version`, `creator`, `updater`, `deleted`)
VALUES (930810, 1, 'CN', '中国', '330000', '浙江省', '330100', '杭州市', '330106', '西湖区', '文三路示例号', '中国浙江省杭州市西湖区文三路示例号', 120.1300000, 30.2700000, 0, 0, 'v68-org-location', 'v68-org-location', b'0');

INSERT IGNORE INTO `ast_site` (`id`, `tenant_id`, `code`, `name`, `customer_id`, `address_id`, `site_type`, `status`, `version`, `creator`, `updater`, `deleted`) VALUES
(930811, 1, 'SITE-HZ-DC-A', '杭州数据中心A站', NULL, 930810, 'DATA_CENTER', 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930812, 1, 'SITE-HZ-OFFICE-B', '杭州办公站B', NULL, 930810, 'OFFICE', 0, 0, 'v68-org-location', 'v68-org-location', b'0');

INSERT IGNORE INTO `ast_site_location` (`id`, `tenant_id`, `site_id`, `parent_id`, `code`, `name`, `location_type`, `tree_path`, `tree_depth`, `tree_sort`, `status`, `version`, `creator`, `updater`, `deleted`) VALUES
(930820, 1, 930811, NULL, 'CAMPUS-A', 'A园区', 'CAMPUS', '/', 0, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930821, 1, 930811, 930820, 'BUILDING-1', '1号楼', 'BUILDING', '/930820/', 1, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930822, 1, 930811, 930821, 'FLOOR-3', '3层', 'FLOOR', '/930820/930821/', 2, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930823, 1, 930811, 930822, 'ROOM-301', '301机房', 'ROOM', '/930820/930821/930822/', 3, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930824, 1, 930811, 930823, 'RACK-A01', 'A01机柜', 'RACK', '/930820/930821/930822/930823/', 4, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930825, 1, 930811, 930824, 'U-20', '20U', 'U_POSITION', '/930820/930821/930822/930823/930824/', 5, 10, 0, 0, 'v68-org-location', 'v68-org-location', b'0');

INSERT IGNORE INTO `ast_area_department_mapping` (`id`, `tenant_id`, `area_code`, `area_level`, `mapping_type`, `department_code`, `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`) VALUES
(930830, 1, '330106', 'DISTRICT', 'SERVICE_OFFICE', 'OFFICE-HZ-DEMO', '2026-01-01 00:00:00.000', NULL, 0, 0, 'v68-org-location', 'v68-org-location', b'0'),
(930831, 1, '330108', 'DISTRICT', 'SERVICE_OFFICE', 'OFFICE-HZ-DEMO', '2026-01-01 00:00:00.000', NULL, 1, 0, 'v68-org-location', 'v68-org-location', b'0');

INSERT IGNORE INTO `ast_location_source_mapping` (`id`, `tenant_id`, `source_system`, `object_type`, `source_key`, `source_version`, `address_id`, `site_id`, `site_location_id`, `match_status`, `location_resolution_status`, `last_synced_at`, `version`, `creator`, `updater`, `deleted`)
VALUES (930840, 1, 'PMS', 'LOCATION_DEMO', 'UNRESOLVED-930840', 'v1', NULL, NULL, NULL, 'PENDING', 'UNRESOLVED', NOW(3), 0, 'v68-org-location', 'v68-org-location', b'0');
