# 施工计划菜单与重排检查（2026-10-24）

来源：需求方确认Q-PLN-MANAGEMENT-MENU-20261024，仅超级管理员、管理员、工程管理部、工程管理部主管展示独立菜单，并检查隐藏后施工计划重排。

## 实现与影响

复用permission store既有菜单展示处理方式，stagePlanMenuVisibility.ts按四类现有角色判断，仅将pms/engineering/stage-plan/index的visible置false。routerHelper仍生成原路由，只映射hidden；其他菜单、项目详情及原权限集合不变。兼具工程管理和项目经理角色时保持入口。未把ENGINEERING_MANAGEMENT_REVIEW等相似职责自动当作允许角色。

V516仅向admin、engineeManager、engineeManagerLeader补齐父目录18000及页面993109180007，共6条授权。未自动分配角色、扩大项目范围或增授create/update/submit按钮权限；超级管理员沿用默认全菜单。

项目详情ProjectSchedulePanel的“重新倒排”直接调用autoEstimateStagePlanBatch，PUT /pms/imp-stage-plan/{id}/auto-estimate使用原update权限；计划加载使用query权限。直接撤销页面的role-menu记录可能破坏query权限，因此保留项目经理/服务经理的原8条query/create/update/submit授权，独立入口单独隐藏。服务端项目授权、可编辑批次状态、任务日期和不可变批准版本保护均沿用原实现。

## 验证与边界

- Vitest 3类44项通过，含新菜单白名单、多角色、原菜单不变以及项目经理/服务经理入口隐藏后组件真实调用重排API并刷新草稿的回归；原项目计划组件历史只读、权限、日期保存/提交失败保护继续通过。该组件测试使用API替身，不等同实际数据库完成重排。
- 定向ESLint及完整vue-tsc通过；首轮类型检查发现新增测试菜单夹具的递归类型推断错误，显式声明递归Menu类型后重跑通过，未修改运行规则。
- 通过当前Docker Compose/Flyway在npdms_domain_test执行V516并再次migrate，重放无新增迁移。验证三个管理角色各2条菜单授权，项目经理/服务经理原8条权限记录逐行保持。证据.run/stage-plan-menu/migrate.log、replay.log、management-grants-after.tsv、project-role-grants-before.tsv。
- 真实浏览器连接曾拒绝，需求方手动刷新后恢复。当前z01010登录下展开“项目交付→工程实施→工程准备”，仅现场工勘与实施方案，独立阶段施工计划入口已隐藏。进入本人主责项目992203060190的施工计划，草稿35正常加载，编辑控件、保存按钮及重排按钮均保留，未出现施工计划权限拒绝。该既有草稿因页面补齐完成日期后处于未保存状态，原有dirty守卫禁用重排并显示“日期已调整，请先保存”；未为验收保存或重算实际项目日期，不能宣称真实接口完成重排。证据.run/stage-plan-menu/z01010-menu-hidden.png、.txt与z01010-existing-draft.png、.txt。四类管理角色的正向展示通过角色规则测试和实际菜单授权核对，未切换四类账号逐个登录。
