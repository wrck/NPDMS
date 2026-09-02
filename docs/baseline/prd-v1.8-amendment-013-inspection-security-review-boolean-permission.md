# PRD V1.8修订013：巡检安全审核复用现有布尔权限

> 变更编号：`CHG-PRD-2026-09-02-013`<br>
> 状态：`APPROVED / MERGED_INTO_BASELINE`<br>
> 影响Requirement：`INS-03`、`INS-09`<br>
> 关联裁决：`Q-FINS001-006`，需求方于2026-09-02确认支持超级管理员并复用现有布尔权限接口

## 1. 决议

1. 租户访问拦截器完成目标租户上下文切换后，Inspection从受信认证上下文取得当前审核人，并直接调用System现有公开`PermissionApi.hasAnyPermissions(actorId, "pms:inspection-rule:security-review")`重新执行目标租户权限判定。
2. System返回`true`即满足记录安全审核的权限条件。该结果同时承认目标租户内显式角色—菜单授权和System既有超级管理员语义；Inspection不再解析具体角色贡献路径，也不额外排除超级管理员。
3. `SecurityFrameworkService.hasPermission/hasAnyPermissions`中的`skipPermissionCheck`用于租户访问上下文切换期间的默认放行，不是安全审核结论。生产审核入口不得只依赖该短路结果，必须在目标租户上下文建立后调用`PermissionApi`完成本次全新判定。
4. 当前审核人或目标租户上下文缺失、System返回`false`或调用异常时失败关闭，不得追加审核事实。
5. 审核事实保存目标`tenantId`、`reviewedBy`、精确`permissionCode`和`authorizationType=RBAC_PERMISSION`。现有布尔接口不提供角色—菜单贡献路径，且`true`可能来自超级管理员，因此`authorizationSourceId`保持为空，不得伪造`RBAC_ROLE_MENU:*`、角色编码或角色名称。
6. 本裁决不新增System API、DTO、Mapper、数据表或授权状态，不修改Yudao基础平台，不允许Inspection直读`system_*`表、硬编码角色或建立第二套权限模型。
7. 当前Inspection内部`InspectionRuleExplicitAuthorizationApi`及“必须返回显式来源事实”的守卫合同已被本裁决替代，不得继续作为Task 8实施基础。Task 8应改为直接消费`PermissionApi`，删除或收口该未装配端口并同步测试；这不属于修改旧`pms_srv_rule`功能。
8. Q-FINS001-005的最后审核事实、DRAFT限制、重审和聚合锁/CAS语义保持不变。修订013只关闭Q-FINS001-006，不表示Task 8代码、生产入口、完整发布或Feature Implementation Done已经完成。

## 2. 不变范围

- 不新增Requirement、业务角色、审批节点、多人会签、规则状态、API动作、数据表、第三方能力或外部连接器。
- 不改变System现有`hasAnyPermissions`的超级管理员、角色—菜单、缓存和租户过滤语义。
- 不把租户访问`skipPermissionCheck`视为缺陷，也不修改租户切换拦截器、Token、认证过滤器或通用权限注解。
- 不修改既有审核事实、已发布revision、历史任务快照或审计记录。
- 旧`pms_srv_rule`、旧接口、旧页面继续保留；新审核与发布能力不得建立在旧载体上。

## 3. 验收边界

- 普通用户在目标租户内通过角色—菜单取得`pms:inspection-rule:security-review`时，`PermissionApi`返回`true`并允许追加审核事实。
- 目标租户超级管理员调用同一接口返回`true`时允许追加审核事实；记录`authorizationType=RBAC_PERMISSION`且`authorizationSourceId`为空。
- 租户访问拦截器完成切换后必须发生一次目标租户`PermissionApi`判定；仅命中`SecurityFrameworkService`的`skip`不得形成审核事实。
- 无权限、无认证用户、无目标租户或System异常时拒绝且不产生审核事实。
- 审核事实不得伪造角色、菜单或授权关系来源；最后事实选择仍按修订012执行。
- 本修订完成只表示Q-FINS001-006业务与技术边界关闭，不表示代码、迁移、SIT、UAT、Release或Implementation Done完成。
