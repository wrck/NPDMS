# F-PLT-003 业务视图注册与页面/动态表单接入

> Feature实施状态：`NOT_STARTED`
> Implementation Done Gate：`NOT_READY`
> Requirement ID：`PM-03`
> Feature Spec：`specs/features/F-PLT-003-business-view-registration.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 当前范围

需求方已批准单一模板升级及业务视图复用现有页面/动态表单。PLT持有视图稳定身份、精确版本、受控组件/Provider引用及发布停用规则；不复制领域业务数据。业务视图的注册与WorkBinding实例解析、CompletionRule业务完成判定分离。

## 当前任务

- [ ] 明确PAGE/DYNAMIC_FORM正式API、物理字段、上下文、Owner/权限与历史保护。
- [ ] 注册版本领域模型、存储、查询、复制、校验、发布、停用。
- [ ] 复用页面与动态表单渲染能力，形成共用宿主与最小接入示例。
- [ ] 权限、并发/幂等、失败/历史保护、MySQL、迁移及真实浏览器验收。

认领及共享文件交接只由`DU-20260908-TEMPLATE-BUSINESS-VIEW.md`维护。当前仅登记已批准任务，不据此声明Feature Ready、接口完成或功能验收通过。
