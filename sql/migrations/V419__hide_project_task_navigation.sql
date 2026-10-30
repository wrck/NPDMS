-- 用户批准隐藏项目任务导航，保留任务、菜单权限及业务数据。
UPDATE system_menu
SET visible = b'0', updater = 'hide-project-task-navigation', update_time = NOW()
WHERE deleted = b'0' AND type = 2 AND component = 'pms/project/project-task/index'
  AND visible <> b'0';
