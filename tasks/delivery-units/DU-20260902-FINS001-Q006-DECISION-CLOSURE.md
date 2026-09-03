# DU-20260902-FINS001-Q006-DECISION-CLOSURE F-INS-001审核权限裁决收口

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`F-INS-001=TASK_COORDINATED`
> Task范围：`只关闭Q-FINS001-006并同步PRD、SDS、Feature、Technical Plan、Task和追溯投影；不修改业务代码、迁移或Yudao System`
> Owner：`Codex本次master Q-FINS001-006裁决收口会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/7a76/NPDMS/.run/q006-master`
> 认领基线：`82bad54cd3925aa14ec414c740c16e5abcae7d7c`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/**;docs/decisions/open-questions.md;docs/design/07-authorization-design.md;docs/design/08-data-model.md;docs/design/09-database-design.md;docs/design/10-api-design.md;docs/design/14-security-design.md;docs/design/15-cache-and-concurrency.md;docs/design/20-test-design.md;docs/reports/*修订013*;specs/001-project-delivery-platform/domains/SRV-*;specs/features/F-INS-001*;specs/features/README.md;docs/superpowers/plans/*f-ins-001*;scripts/tests/test_fins001_plan_and_scope.py;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-FINS001-Q006-DECISION-CLOSURE.md;tasks/delivery-units/README.md;需求/PRD-项目实施交付管理平台.md`
> 串行资源：`master PRD下一修订号;Q-FINS001-006状态;F-INS-001当前Task阻断`
> 旧功能范围：`旧pms_srv_rule、旧接口与旧页面保持PRESERVE_EXISTING；未装配InspectionRuleExplicitAuthorizationApi设计被修订013替代，本DU只记录Task 8移除要求，不修改运行代码`
> 验证：`PRD源/快照同哈希；PRD语义与基线；领域派生与Requirement追溯；F-INS规格一致性；Delivery Unit；git diff --check；五轴自审`
> 集成记录：`master修订013独立关闭Q-FINS001-006；复用System现有PermissionApi并保留超级管理员语义；Task 8实现与Feature Done保持未完成`

## 裁决

需求方确认支持System超级管理员，现有布尔权限接口满足需求。租户访问拦截器完成目标租户上下文切换后，Inspection以当前审核人直接调用`PermissionApi.hasAnyPermissions(actorId, "pms:inspection-rule:security-review")`进行目标租户全新判定；普通角色—菜单授权或System超级管理员返回`true`均可审核。`SecurityFrameworkService`的租户访问`skipPermissionCheck`只用于上下文切换，不直接形成审核结论。

审核权限返回`false`、认证用户/目标租户缺失或System异常时失败关闭。审核事实记录精确权限码与`RBAC_PERMISSION`，但现有布尔接口不提供贡献路径且可能由超级管理员命中，因此`authorizationSourceId`为空，不伪造角色—菜单来源。

## 集成边界

- 不新增System API、DTO、Mapper、表或授权状态，不修改Yudao基础平台，不允许Inspection直读`system_*`表。
- 来源分支`1895a5e7`中的新System显式事实API、排除超级管理员和`RBAC_ROLE_MENU:*`来源方案继续隔离，不接收整提交。
- 当前未装配`InspectionRuleExplicitAuthorizationApi`合同已被替代；Task 8必须从最新master删除或收口该端口，直接复用`PermissionApi`并同步测试。
- 本DU不修改Java、SQL、TypeScript/Vue业务代码。Feature保持`IMPLEMENTATION_IN_PROGRESS`，不得把规格关闭转记为生产审核入口、完整发布或Implementation Done。

## 完成口径

- `Q-FINS001-006=RESOLVED`，PRD、SDS、Feature、Plan、Task和矩阵使用同一权限判定、超级管理员、租户切换及失败关闭语义。
- `Q-FINS001-005`的最后事实选择、DRAFT限制、重审和聚合锁/CAS语义保持不变。
- PRD源文件与冻结快照字节一致，基线元数据、报告、Feature/Task矩阵和DU索引一致。
