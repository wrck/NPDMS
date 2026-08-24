# F-PROJ-003 项目子树授权与统一数据范围

> Feature实施状态：`IMPLEMENTATION_IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`NOT_READY`
> 当前阻断：无
> 当前任务：Task 3 发布ProjectScopeApi并重写统一范围计算
> Requirement ID：`PM-04`
> 关联契约：`AUT-01`、`AUT-02`的最小AuthorizationGrant载体
> Feature Spec：`specs/features/F-PROJ-003-project-subtree-authorization-and-unified-scope.md`
> Feature物理契约：`specs/features/F-PROJ-003-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-003-project-subtree-authorization-and-unified-scope.md`
> 锁定规格提交：`69b2b48320a858f91d938ae6914146681c97fb0a`

## 事实边界

- PRD V1.8、工程链、已批准SDS和F-PROJ-003 Feature Spec是正式输入；不重新审核SDS总体门禁。
- `specs/001-project-delivery-platform/`只作历史参考，不参与实施校验。
- F-PROJ-002现有项目树和范围代码必须重新检查并改造，不能据此判断F-PROJ-003已实现。
- 用户已禁用测试驱动顺序，但每项任务仍需风险匹配的自动化、MySQL或真实浏览器验证。
- 当前处于Implementation，不准备Deployment、SIT、UAT或Release方案。

## 任务跟踪

- [x] Task 1 建立PLT模块并归位平台事实所有权
- [x] Task 2 实现PLT AuthorizationGrant物理模型与公开API
- [ ] Task 3 发布ProjectScopeApi并重写统一范围计算
- [ ] Task 4 实现项目授权命令、查询与越界守卫
- [ ] Task 5 将当前PROJ入口统一到动作化ProjectScope
- [ ] Task 6 补齐字典、菜单、示例授权和迁移验证
- [ ] Task 7 实现响应式项目授权维护界面
- [ ] Task 8 完成真实闭环验证与Implementation Done证据

## 验收跟踪

- [ ] AC-FPROJ003-001 当前项目与后代范围
- [ ] AC-FPROJ003-002 角色不自动扩大范围
- [ ] AC-FPROJ003-003 授权不得越界
- [ ] AC-FPROJ003-004 撤权与到期
- [ ] AC-FPROJ003-005 项目移动后重算
- [ ] AC-FPROJ003-006 幂等与并发
- [ ] AC-FPROJ003-007 当前业务入口统一过滤
- [ ] AC-FPROJ003-008 模块边界
- [ ] AC-FPROJ003-009 真实浏览器与响应式

## 已登记的非阻断边界

- 任务、设备、交付件、割接和巡检尚未实施的业务对象在其Feature中接入`ProjectScopeApi`；本Feature不提前创建这些页面或业务事实，也不据此阻断当前PROJ闭环。
- AUT-01/AUT-02完整OA申请、外部授权确认和文件正文属于后续范围；本Feature只实现PM-04所需同步授权事实。
