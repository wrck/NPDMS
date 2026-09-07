-- Disable Yudao module menus whose backend modules are not assembled in yudao-server.
-- Keep system, infrastructure, BPM, and PMS menus enabled.
UPDATE `system_menu`
SET `status` = 1,
    `updater` = 'system',
    `update_time` = NOW()
WHERE (`id`, `path`) IN (
    (1117, '/pay'),
    (1281, '/report'),
    (2262, '/member'),
    (2362, '/mall'),
    (2084, '/mp'),
    (2397, '/crm'),
    (2563, '/erp'),
    (6400, '/wms'),
    (5100, '/mes'),
    (2758, '/ai'),
    (4000, '/iot'),
    (6500, '/im')
)
  AND `parent_id` = 0
  AND `deleted` = b'0';
