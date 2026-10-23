-- 仅修正三个系统菜单的显示名称；保留菜单ID、路由及权限。
UPDATE system_menu
SET name = '交付件汇总', updater = 'menu_label_v491', update_time = NOW()
WHERE id = 993109180001 AND name = '交付件汇总（6.4）' AND deleted = b'0';

UPDATE system_menu
SET name = '现场培训', updater = 'menu_label_v491', update_time = NOW()
WHERE id = 993109180002 AND name = '现场培训（6.1）' AND deleted = b'0';

UPDATE system_menu
SET name = '阶段施工计划', updater = 'menu_label_v491', update_time = NOW()
WHERE id = 993109180007 AND name = '阶段施工计划（3.1）' AND deleted = b'0';
