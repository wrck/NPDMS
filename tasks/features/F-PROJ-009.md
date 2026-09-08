# F-PROJ-009 项目交付模板配置中心

> Feature实施状态：`IN_PROGRESS`
> Implementation Done Gate：`NOT_READY`
> Requirement ID：`PM-03`
> Feature Spec：`specs/features/F-PROJ-009-project-delivery-template-configuration-center.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 当前范围

需求方批准直接升级现有ProjectTemplate身份、版本、编辑与发布入口；不建立LEGACY/GRAPH双模板、不从sortOrder推断缺失关系图、不覆盖已发布或项目冻结历史。定义库、阶段关系、Stage/Task绑定、交付件要求及规则按最新PM-03实现；页面/表单引用由PLT业务视图注册供给。

## 当前任务

- [ ] 正式落位可复用定义、模板组合、图、发布校验及直接消费者契约。
- [ ] 升级现有模板定义、复制、引用、图校验、发布、停用与匹配预演。
- [ ] 升级现有模板管理入口与视图选择器，不创建平行模板页面。
- [ ] 同步必要初始化、工作区与阶段图直接消费者；涉及既有Feature的增量在其当前Task中记录，不宣称其全部Done。
- [ ] 受管种子、失败测试、MySQL/迁移、真实浏览器与历史保护验证。

## 限制

Q-TPLACC-001仅阻断独立验收创建/范围绑定及依赖接入；本Feature不实现这些路径，不借模板升级实现S0～S6各领域业务。缺少显式关系图的历史项目须核对真实来源及迁移影响，不自动补造边或静默降为只读。

认领和共享文件交接只由`DU-20260908-TEMPLATE-BUSINESS-VIEW.md`维护。当前仅登记已批准任务，不据此声明Feature Ready或功能完成。
