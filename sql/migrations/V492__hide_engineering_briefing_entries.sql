-- 隐藏工程交底菜单入口；保留权限、路由、业务数据及历史。
UPDATE system_menu
SET visible = b'0', updater = 'briefing_visibility_v492', update_time = NOW()
WHERE id IN (19196, 993109170001) AND deleted = b'0' AND visible = b'1';
