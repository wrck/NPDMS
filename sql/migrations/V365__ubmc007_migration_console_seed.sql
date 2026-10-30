-- UBMC-007 迁移工具台与初始化配置（P11）：
-- 1) 补齐 P05/P09 公共能力缺失的权限种子（过程定义、结果订阅）；
-- 2) 新增迁移工具台权限（query/stage/reconcile）；
-- 3) 在“项目交付（新功能）”目录下补可见菜单：统一业务实体、结果订阅、迁移工具。
-- 仅落菜单与权限，不臆造角色授权；超级管理员按平台既有旁路访问。
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT * FROM (
    SELECT 970000000000099201 AS id, '结果订阅查询' AS name, 'pms:result-subscription:query' AS permission,
           3 AS type, 9990 AS sort, 0 AS parent_id, '' AS path, '' AS icon, NULL AS component, NULL AS component_name,
           0 AS status, b'0' AS visible, b'1' AS keep_alive, b'1' AS always_show,
           'seed_ubmc007' AS creator, 'seed_ubmc007' AS updater, b'0' AS deleted
    UNION ALL SELECT 970000000000099202, '结果订阅办理', 'pms:result-subscription:operate', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 970000000000099211, '过程定义查询', 'pms:process-definition:query', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 970000000000099212, '过程定义办理', 'pms:process-definition:operate', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 970000000000099221, '迁移台账查询', 'pms:migration:query', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 970000000000099222, '迁移暂存办理', 'pms:migration:stage', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 970000000000099223, '迁移对账办理', 'pms:migration:reconcile', 3, 9990, 0, '', '', NULL, NULL, 0, b'0', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
) seed_rows
WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.id = seed_rows.id AND m.deleted = b'0')
  AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission = seed_rows.permission AND m.deleted = b'0');

-- 可见菜单挂在既有“项目交付（新功能）”目录（993109100501，/pms-inheritance）下。
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT * FROM (
    SELECT 993109100511 AS id, '统一业务实体' AS name, 'pms:business-model:query' AS permission,
           2 AS type, 20 AS sort, 993109100501 AS parent_id, 'business-entity' AS path, '' AS icon,
           'pms/platform/businessEntity/index' AS component, 'PmsBusinessEntityMenu' AS component_name,
           0 AS status, b'1' AS visible, b'1' AS keep_alive, b'1' AS always_show,
           'seed_ubmc007' AS creator, 'seed_ubmc007' AS updater, b'0' AS deleted
    UNION ALL SELECT 993109100512, '结果订阅', 'pms:result-subscription:query', 2, 21, 993109100501, 'result-subscription', '',
           'pms/platform/resultSubscription/index', 'PmsResultSubscriptionMenu', 0, b'1', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
    UNION ALL SELECT 993109100513, '迁移工具', 'pms:migration:query', 2, 22, 993109100501, 'migration', '',
           'pms/platform/migration/index', 'PmsMigrationConsole', 0, b'1', b'1', b'1', 'seed_ubmc007', 'seed_ubmc007', b'0'
) seed_rows
WHERE EXISTS (SELECT 1 FROM system_menu g WHERE g.id = 993109100501 AND g.deleted = b'0')
  AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.id = seed_rows.id AND m.deleted = b'0')
  AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.component = seed_rows.component AND m.deleted = b'0');
