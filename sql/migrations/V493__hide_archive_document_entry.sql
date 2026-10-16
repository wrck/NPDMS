-- 隐藏归档文档菜单入口，保留权限、路由及历史数据。
UPDATE system_menu
SET visible = b'0', updater = 'archive_visibility_v493', update_time = NOW()
WHERE id = 19098 AND deleted = b'0' AND visible = b'1';
