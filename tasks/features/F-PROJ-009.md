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

## 2026-09-09候选回执

配置端候选已提交到codex/pre-s0-template-foundation：8a5c2803，尚未集成master。包含八类精确定义、显式图、发布引用/规则目标校验、复制、现有模板页面和视图选择，不建第二模板根。图领域56项、后端配置/匹配82项、Controller路由10项、前端29项测试通过；最终类型与构建通过。V206及19项迁移结构测试中的相关子集只证明候选结构，未实际执行V206。

未完成：Q-FPROJ009-001存量43个ACTIVE项目缺图的来源/批次裁决，初始化和统一推进消费、正式种子及MySQL升级/业务浏览器验收。候选不得在缺少V206的运行库直接启动，也不把新模板交给旧排序推进引擎。正式集成须把候选与消费者一次性按单模型衔接，不能建立长期新旧算法开关。Implementation Done保持NOT_READY；已有Feature Task不因本候选完成自动晋级。

认领和共享文件交接只由`DU-20260908-TEMPLATE-BUSINESS-VIEW.md`维护，工作树交付时无未提交实现。
