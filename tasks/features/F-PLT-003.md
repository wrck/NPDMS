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

## 2026-10-24 默认割接审批视图初始化补齐

需求方批准V518发布原样V502默认PAGE视图，保留原ID、配置正文与历史。真实MySQL隔离验证17项通过（同时复验V517 20项），V517/V518已迁移当前测试库，validate及重复migrate通过。admin浏览器模板匹配/新建项目实例化成功，独立审批入口及任务内割接列表可加载；任务规则区暴露既有模板缺少业务视图冻结快照的`VIEW_NOT_BOUND`，尚不构成完整业务视图验收。证据、保护范围和待补缺口统一见[迁移与浏览器记录](../../docs/generated/project-template-transfer-20261024/v518-deployment-browser-acceptance.md)，不晋级整体Implementation状态。

后续授权补齐五套模板51处业务视图快照，经既有API发布v2，保留v1及旧实例。admin浏览器新建84573并验证割接入口不再出现VIEW_NOT_BOUND，过期快照发布拒绝及历史保护通过；详见[快照补齐验收](../../docs/generated/project-template-transfer-20261024/template-view-snapshot-v2-acceptance.md)。
