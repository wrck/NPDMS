-- =============================================================================
-- V58: 冻结旧项目链写入口（F-PM01 存量冻结，对齐 V50 退役语义）
--
-- 与基础平台 MenuService 删除语义一致：先逻辑撤销角色菜单关系，再逻辑删除菜单。
-- 本迁移只处理权限和菜单配置，不删除、改名或迁移任何旧 pms_project 业务表及历史数据
-- （pms_project 数据冻结只读，迁移处置待 AI-MIG-000）。
--
-- 冻结对象：
-- - 18050 项目创建 / 18051 项目更新 / 18052 项目删除（V49 旧按钮）：
--   旧写端点随 F-PM01 T5 退役；新链无删除端点，18052 一并退役不承接；
-- - 18021 项目指派（V7 旧按钮）：旧指派语义退役，新链 18070 承接；
-- - 18011 项目总览（V9 页面，V40 已归入 19261）：仅隐藏入口（visible=0，不删除），
--   页面保留列表渲染供直链只读过渡，读端点 /pms/project/get、/pms/project/page 保留。
-- =============================================================================

-- 1. 撤销旧写按钮的全部角色授权
UPDATE `system_role_menu`
SET `deleted` = b'1',
    `updater` = 'system',
    `update_time` = NOW()
WHERE `menu_id` IN (18050, 18051, 18052, 18021)
  AND `deleted` = b'0';

-- 2. 逻辑删除旧写按钮（BR-1 旁路入口阻断）
UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `deleted` = b'1',
    `updater` = 'system',
    `update_time` = NOW()
WHERE `id` IN (18050, 18051, 18052, 18021)
  AND `deleted` = b'0';

-- 3. 隐藏旧项目总览入口（不删除：页面与读端点保留只读过渡）
UPDATE `system_menu`
SET `visible` = b'0',
    `updater` = 'system',
    `update_time` = NOW()
WHERE `id` = 18011
  AND `deleted` = b'0';
