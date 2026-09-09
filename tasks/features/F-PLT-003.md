# F-PLT-003 业务视图注册与页面/动态表单接入

> Feature实施状态：`IN_PROGRESS`
> Implementation Done Gate：`NOT_READY`
> Requirement ID：`PM-03`
> Feature Spec：`specs/features/F-PLT-003-business-view-registration.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 当前范围

需求方已批准单一模板升级及业务视图复用现有页面/动态表单。PLT持有视图稳定身份、精确版本、受控组件/Provider引用及发布停用规则；不复制领域业务数据。业务视图的注册与WorkBinding实例解析、CompletionRule业务完成判定分离。

## 当前任务

- [x] PAGE/DYNAMIC_FORM正式API、物理字段、上下文、Owner/权限与历史保护已落位。
- [x] 注册版本领域模型、存储、查询、复制、校验、发布、停用已进入master。
- [x] 共用宿主复用SOL需求分析页面与PLT动态表单，保持Owner API及未保存内容保护；新增页面通过受控映射/注册接入。
- [x] 定向权限、CAS/幂等、批量锁、失败回滚及历史保护验证通过；V204→V205升级、info/validate/重复migrate通过。
- [ ] 登录后真实浏览器注册/页面/表单闭环、空库迁移与最终业务种子验收未完成。当前缺可用验收登录凭据，未猜测或重置密码；空库验证未获测试数据重建授权，未清库。

实现与整改已选择性进入master：90a8b774、5ae654ef。53项PLT单元/API/批锁测试、5项固定MySQL事务/并发/审计失败回滚测试及4项SOL Provider测试通过；45项前端交互整改回归通过。独立审查4项问题已修复且仅差异复核确认解决。主代理最终类型检查通过，构建通过；浏览器实际只到登录表单，不能替代登录后的业务验收。Implementation Done保持NOT_READY。

认领、环境、命令/日志及剩余范围见`DU-20260908-TEMPLATE-BUSINESS-VIEW.md`。运行期Stage/Task实际接入还依赖模板候选及Q-FPROJ009-001，不因共用宿主组件完成自动视为业务闭环。
