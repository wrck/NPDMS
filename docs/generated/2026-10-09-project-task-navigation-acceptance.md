# 项目任务入口隐藏验收

用户确认同时隐藏项目详情及左侧导航中的项目任务入口。项目主详情与承接详情概览移除tasks导航项；旧tab=tasks回退基本信息，任务面板实现、任务数据及业务接口保留。

V419仅把项目任务菜单visible设为0，保留原status、角色权限与任务记录。已通过指定npdms_domain_test的Compose Flyway限定迁移执行，无后端重启。左侧旧菜单执行前已为status=1；本次使可见配置一致。

真实Chromium登录项目1002，概览项目任务按钮不存在、左侧项目任务菜单不存在、旧任务页签深链选中基本信息。证据：.run/dac-host/project-task-navigation-acceptance/acceptance.json及截图。相关ESLint无错误；承接详情4项既有模板格式警告未改动。未创建或修改任务业务数据。
