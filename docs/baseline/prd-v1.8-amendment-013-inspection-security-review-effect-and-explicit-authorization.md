# PRD V1.8批准修订013：巡检安全审核生效与显式授权

> 修订编号：`CHG-PRD-2026-09-02-013`<br>
> 批准日期：2026-09-02<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-09-01-012`<br>
> 关联裁决：`NPDMS-Q-FINS001-005-GO-20260902-01`、`NPDMS-Q-FINS001-006-GO-20260902-01`

## 1. 权威来源

- 前置正式底稿：PRD V1.8修订012。
- 既有业务语义：INS-03/09要求命令与正则在发布前由审批/任务角色组完成安全审核，只有绑定当前revision及当前内容摘要的`PASSED`结论允许发布。
- 待关闭歧义：同一revision与摘要存在多条审核事实时缺少当前结论选择规则；现有通用权限布尔判断会对超级管理员或权限跳过上下文放行，不能形成可审计的显式审核授权事实。
- 批准裁决：采用Q-FINS001-005/006方案A，冻结最后审核事实生效语义，并由System提供最小公开只读显式RBAC授权事实契约。

## 2. 批准结论

1. 安全审核事实只追加、不覆盖；同一租户、同一revision和同一命令/正则内容摘要按`reviewed_at DESC, id DESC`确定唯一当前结论。最后一条为`PASSED`才允许发布，最后一条为`REJECTED`则拒绝；后续事实只替代前序事实的当前效力，不改写历史。
2. 内容摘要变化或形成新revision必须重新审核。审核人权限后续撤销不追溯修改既有历史事实；如需撤销当前结论，追加新的`REJECTED`事实。
3. `pms:inspection-rule:security-review`专用权限包是PRD审批/任务角色组在本Feature中的正式机器映射；记录安全审核不再额外解析实例候选人、固定角色编码、会签人数或票数阈值。
4. 安全审核授权只接受System公开只读API返回的当前租户、当前认证用户和专用权限码的显式`RBAC_PERMISSION`事实。该事实必须来自有效用户、有效用户—角色关系、有效角色、有效角色—菜单关系、有效菜单和精确权限码匹配。
5. 超级管理员身份、`skipPermissionCheck`、空权限码、通配或前缀权限、维护权、发布权及其他隐式放行均不得生成安全审核授权事实。
6. System公开查询只允许当前认证用户查询当前租户自身的单一权限码，不支持代理查询其他用户或租户；请求租户、用户与服务端认证上下文不一致时拒绝。
7. 同一用户存在多条合法授权路径时，System按`role_id ASC, menu_id ASC, user_role.id ASC, role_menu.id ASC`选择唯一事实；同一权限码存在多个有效菜单时适用相同顺序，不依赖数据库自然顺序。
8. `authorizationType`固定为`RBAC_PERMISSION`。System自然取得稳定`userRoleId`与`roleMenuId`时，`authorizationSourceId`为`RBAC_ROLE_MENU:{userRoleId}:{roleMenuId}`；确实无法稳定提供关系主键时允许为空，不得以角色编码、角色名称或猜造值替代。
9. 无显式事实、System契约异常或返回租户、用户、权限码不一致时，Inspection失败关闭且不得记录审核事实。
10. 本修订只批准Yudao System为上述契约增加最小公开API、响应DTO、实现及必要的内部显式关系查询；不改变现有`hasAnyPermissions`、超级管理员一般权限、Token、OAuth、`LoginUser`、认证过滤器、数据权限框架或Controller契约，不建设通用授权审批平台。
11. Inspection不得直读`system_*`表，不得硬编码业务角色；System不得承载Inspection审核结论、摘要或发布规则。
12. 本修订不新增Requirement、业务角色、审批节点、多人会签、生命周期状态、第三方平台能力或外部连接器，不覆盖已发布revision、历史审核、审计或任务快照。

## 3. Requirement与影响边界

- 直接细化：`INS-03@V2`规则安全审核、发布放行和权限边界。
- 关联细化：`INS-09@V2`命令/正则内容摘要审核与版本发布。
- 支撑边界：`NFR-02@V2`最小权限、失败关闭和审计可解释性。
- System物理Owner：显式RBAC授权事实公开契约及关系有效性查询。
- Inspection消费者：当前审核入口、授权事实一致性复核、审核事实追加和发布时最后结论选择。
- 不影响：INS-02执行、AST产品类型契约、规则状态机、第三方采集平台、旧巡检规则接口与页面。

## 4. 验收边界

- 同revision同摘要追加`PASSED -> REJECTED`后必须拒绝发布；追加`REJECTED -> PASSED`后方可恢复发布资格；`reviewed_at`相同时按`id DESC`确定最后事实。
- 旧摘要的`PASSED`不得授权新摘要，新revision不得复用旧revision审核。
- 当前有效用户通过至少一条有效用户—角色—菜单路径精确取得专用权限时可记录审核；多路径命中始终返回同一稳定来源。
- 仅具备超级管理员、权限跳过、维护权或发布权，或用户、角色、菜单及任一关系停用时，不得记录审核。
- 请求租户或用户与当前认证上下文不一致、权限码为空或非精确匹配时，System拒绝且Inspection不产生审核事实。
- System无事实、异常或响应主体不一致时失败关闭；历史审核事实保持不可变。
- 本修订完成不代表System API、Inspection适配器、安全审核入口、发布闭环、Deployment、SIT、UAT或Release完成。

## 5. 基线关系与下游落位

本修订合并至`需求/PRD-项目实施交付管理平台.md`，并冻结为`docs/baseline/prd-v1.8.md`。两份文件必须保持一致。

下游正式落位：

- 授权与安全：`docs/design/07-authorization-design.md`、`docs/design/14-security-design.md`。
- 数据与查询：`docs/design/08-data-model.md`、`docs/design/09-database-design.md`。
- API契约：`docs/design/10-api-design.md`。
- 测试：`docs/design/20-test-design.md`。
- 阶段Gate：`docs/engineering/gates/phase-1/gate-status.md`、`docs/engineering/gates/phase-2/gate-status.md`、`docs/engineering/gates/phase-3/gate-status.md`。
- Feature Ready与实施：`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`、`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md`、`tasks/features/F-INS-001.md`。
- 问题关闭：`docs/decisions/open-questions.md`中的`Q-FINS001-005/006`。
