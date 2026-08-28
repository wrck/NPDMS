# F-PROJ-003 项目子树授权与统一数据范围 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY`
> Requirement：`PM-04`
> Requirement切片覆盖：`PM-04@V1=PARTIAL`
> 关联契约：`AUT-01`、`AUT-02`的`AuthorizationGrant`最小公开载体；本Feature不宣称完整实现AUT审批和外部授权流程
> Owner Context：`PROJ（项目治理）`；授权事实Owner为`PLT（平台公共能力）`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 前置Feature：`F-PROJ-002`（`IMPLEMENTATION_COMPLETE / PASS`）
> Technical Plan：由NPDMS锁定本Feature规格提交后重新生成；不得沿用V1.7计划或根据现有实现直接判定完成
> 实施状态：`IMPLEMENTATION_COMPLETE / PASS`
> 实施证据：NPDMS `9ab894f`（Task、自动化、真实MySQL、真实浏览器与Implementation Done记录）

## 1. 业务价值与目标

服务经理可以在本人有权管理的项目子树内，把当前项目或当前项目及全部后代的查看、管理权限显式授予用户，并可按有效期撤销。平台在每次查询和命令中合并当前项目成员关系、有效授权和当前完整项目树版本，使平级项目默认隔离，项目移动、撤权或到期后不继续沿用旧范围。

本Feature形成以下闭环：

```text
项目角色与当前项目基础范围
-> 服务经理显式授予当前项目或项目子树权限
-> 服务端计算ProjectTreeScope
-> 项目详情/树/进度/拆分入口统一过滤
-> 撤权、到期或项目移动
-> 新版本范围即时生效并保留审计历史
```

## 2. 权威输入与约束

- `docs/baseline/prd-v1.8.md`的PM-04；
- `docs/engineering/00-engineering-chain.md`的Feature Ready、DoR与DoD；
- `docs/design/04-module-design.md`、`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`15-cache-and-concurrency.md`和`16-exception-and-idempotency.md`；
- `docs/traceability/phase2-contract-map.md`的PM-04、AUT-01和AUT-02契约；
- ADR-0034项目角色与项目子树授权范围分离。

如本Feature与上述资产冲突，按`PRD > 工程链 > SDS > Feature Spec > Technical Plan > Task > Code`处理。V1.7代码、数据库、页面和测试只作复用审计证据，每项能力仍须从未完成状态重新检查和改造。

## 3. Scope

### 3.1 包含范围

- 项目成员角色与项目授权范围分离；
- 服务经理在本人管理范围内复用既有指派命令授予PRD已定义项目角色；
- `CURRENT_PROJECT`、`PROJECT_AND_DESCENDANTS`两种项目授权范围；
- `PROJECT_VIEW`、`PROJECT_MANAGE`两种项目动作；
- 授权创建、分页查询、详情、撤权、有效期和追加历史；
- 授权人只能在本人有效管理范围内授权，且不能把更大范围或更高动作授予他人；
- 当前项目成员基础可见性、显式授权和项目树版本的统一`ProjectTreeScope`计算；
- 项目移动后基于新完整树版本重新计算范围，不保留旧父树继承权限；
- 项目详情、工作台、项目树、进度、闭环守卫和拆分入口的服务端统一过滤；
- PLT授权公开API与PROJ项目范围公开API，不允许跨模块读取Service、Mapper、Repository或表；
- 项目详情授权维护界面及桌面、窄桌面、平板和手机响应式验收；
- `pms_project_authorization_action`、`pms_project_authorization_scope`字典、菜单权限和覆盖有效/到期/撤权场景的示例授权数据；
- 授权拒绝、撤权、到期、缓存失效、幂等、并发和审计验证。

### 3.2 Out of Scope

- AUT-01完整OA审批、外部授权系统确认、文件正文和授权申请流程；
- 通用策略DSL、表达式引擎或可编程权限规则；
- 任务、设备、交付件、割接和巡检尚未实施Feature的业务页面与业务事实；这些Feature实施时必须接入本Feature的`ProjectScopeApi`；
- 新增PRD之外的角色、审批节点或项目状态；
- 历史生产数据迁移、Deployment、SIT、UAT和Release。

## 4. 业务规则

### BR-FPROJ003-001 角色与范围分离

- 当前有效`ProjectMemberAssignment`只表达项目角色，并授予该角色在当前项目允许的动作。
- 项目经理、一级服务经理和二级服务经理角色不能自动穿透全部后代。
- 非项目成员可以通过显式`AuthorizationGrant`取得受控项目范围，但不会因此成为项目角色或取得未授权动作。

### BR-FPROJ003-002 授权边界

- PM-04只允许`CURRENT_PROJECT`和`PROJECT_AND_DESCENDANTS`，客户端不能提交路径、深度或项目ID集合扩大范围。
- 只有具备授权维护功能权限、有效服务经理角色及目标项目`PROJECT_MANAGE`范围的主体可以指派PRD已定义项目角色，或授予、撤销项目权限。
- 授权对象必须与锚点项目同租户；授予的动作和范围不得超过授权人自己的有效范围。
- 项目经理和普通成员不能授予、撤销或扩大项目权限。

### BR-FPROJ003-003 授权生命周期

- 授权创建保存主体、锚点项目、动作、范围、生效区间、来源、授权人和版本。
- 撤权关闭当前有效区间并追加撤销人、时间和原因；历史行不物理删除或覆盖为另一授权。
- 未到生效时间、已到期、已撤销或停用授权不参与权限计算。
- 同一幂等键同请求返回原结果；同键不同请求拒绝。并发重复授权只能形成一个当前有效事实。

### BR-FPROJ003-004 统一范围计算

- PROJ合并当前成员基础范围和PLT有效授权，并在当前完整`treeVersion`展开全部后代。
- `PROJECT_MANAGE`包含同范围的查看能力，但`PROJECT_VIEW`不能执行管理命令。
- 空成员和空授权集合必须返回空范围，禁止退化为租户全量。
- 平级项目默认互不可见；为展示有权节点的祖先路径只可返回F-PROJ-002定义的结构占位，不返回名称、进度、任务、设备、交付件或其他业务明细。

### BR-FPROJ003-005 移动、撤权与缓存

- 项目移动成功后权限按新完整树版本重新计算；旧父项目的后代展开结果立即失效。
- 授权不复制到后代项目，也不因移动改写锚点或历史。
- 缓存键必须包含租户、主体、动作、授权版本和完整树版本；授权创建、撤权、到期扫描或树版本切换后旧缓存不得继续放行敏感访问。
- 缓存不可用时回源数据库，不能绕过授权。

### BR-FPROJ003-006 跨模块边界

- PLT拥有`AuthorizationGrant`及其Repository，只通过`AuthorizationGrantApi`提供授权写入和查询。
- PROJ拥有项目树语义和`ProjectScopeApi`，合并授权事实后对其他模块提供项目范围结果。
- 其他模块不得直接访问`plt_authorization_grant`、`proj_project_member_assignment`、`proj_project_tree_path`或复制范围算法。

## 5. API契约

所有Business API使用`/api/v1/pms`前缀；Yudao管理端运行时统一叠加`/admin-api`。

### 5.1 项目授权管理

| 路径 | 操作 | 输入/输出 | 守卫 |
|---|---|---|---|
| `/projects/{projectId}/actions/assign-manager` | `POST` | 用户、PRD已定义项目角色和生效区间；返回成员角色区间版本 | 服务经理、功能权限、同租户和目标项目管理范围；角色本身不产生后代范围 |
| `/projects/{projectId}/authorization-grants` | `POST` | `subjectUserId/actionCode/scopeCode/effectiveFrom/effectiveTo/reason`；返回授权ID和版本 | `Idempotency-Key`；服务经理、功能权限、同租户和不超授权范围 |
| `/projects/{projectId}/authorization-grants` | `GET` | 按主体、动作、范围、状态和有效时点分页；返回当前与历史授权摘要 | 仅返回调用者可管理范围内授权；空范围返回空页 |
| `/project-authorization-grants/{grantId}` | `GET` | 返回授权、授予和撤销摘要 | 越权按不存在处理，不泄露授权存在性 |
| `/project-authorization-grants/{grantId}/actions/revoke` | `POST` | `reason`和期望版本；返回撤权版本与失效时间 | `Idempotency-Key`、`If-Match`；已撤权同请求重放原结果 |

### 5.2 内部公开契约

- `AuthorizationGrantApi`：PLT提供创建、撤销和按主体/资源/动作/有效时点查询；不接受PROJ路径或树深度。
- `ProjectScopeApi`：PROJ接收主体、锚点项目、动作和期望树版本，返回完整树版本、可完全访问项目ID集合及必要结构占位集合；空权限明确返回空集合。
- Business API和内部API使用同一错误分类：未认证、无权限、版本冲突、幂等冲突、授权无效和树版本陈旧不得折叠为通用失败。

## 6. 数据变化与Owner边界

机器可读契约：`specs/features/F-PROJ-003-physical-contract.json`。

- PLT以前向迁移新增`plt_authorization_grant`，保存授权当前事实与历史；
- PROJ继续使用既有`proj_project_member_assignment`、`proj_project_tree_version`和`proj_project_tree_path`，不增加重复范围列；
- 初始化数据以前向幂等迁移增加项目授权动作/范围字典、授权维护菜单与权限，并使用专用高段ID准备当前项目、全部后代、到期、撤权和无匹配示例；不得臆造外部授权系统值域；
- 授权范围是PLT事实，项目树展开是PROJ计算结果；双方通过公开API协作；
- 授权、撤权和拒绝均写操作审计；授权事实不通过事件解释为业务完成；
- 不修改任何已执行Flyway迁移。

## 7. 事务、幂等、并发与错误语义

- PLT在本地事务内原子提交授权/撤权、幂等完成点和审计；PROJ授权命令先完成边界校验再同步调用PLT公开API。
- 同一主体、资源、动作和范围只允许一个当前有效授权；并发创建由数据库当前标记唯一约束裁决。
- 撤权使用授权版本条件；版本冲突返回当前摘要，不覆盖并发结果。
- 授权命令成功但缓存失效通知失败时，敏感读写必须回源验证，不能继续信任旧缓存。
- 无权限、跨租户、超范围、过期和撤权请求不产生有效授权、成功幂等记录或其他业务副作用；允许保存脱敏拒绝审计。

## 8. 用户界面

- 项目详情在现有页面增加“项目授权”入口，展示用户、动作、范围、有效期和状态；
- 创建授权使用Yudao已有表单、用户选择、权限控制和分页组件，缺失时使用Element Plus组件；
- 撤权必须二次确认并填写原因，不提供物理删除；
- 桌面使用表格，窄桌面、平板和手机允许表格区域横向滚动或卡片化，但页面级不得横向溢出；
- 使用主题变量和统一间距，不堆叠内联样式，不新增第二套主题体系。

## 9. 验收标准

### AC-FPROJ003-001 当前项目与后代范围

同一用户在项目A拥有`CURRENT_PROJECT`时只能读取A；改为或新增`PROJECT_AND_DESCENDANTS`后可读取A全部后代，但仍不能读取A的祖先或平级项目。

### AC-FPROJ003-002 角色不自动扩大范围

项目经理或服务经理仅有当前成员关系而无后代授权时，查询子项目被拒绝且不返回名称或业务明细。

### AC-FPROJ003-003 授权不得越界

服务经理只能在本人`PROJECT_MANAGE`子树内指派PRD已定义项目角色和授权，不能跨租户、跨平级项目或授予比本人更大的动作和范围；项目角色本身不自动获得后代范围，失败无有效成员或授权副作用。

### AC-FPROJ003-004 撤权与到期

撤权或到期后下一次敏感查询和命令立即拒绝，旧缓存不能继续放行；授权历史和撤销原因仍可由有权主体查询。

### AC-FPROJ003-005 项目移动后重算

子树移动到新父项目后，旧父项目基于后代授权不再访问该子树；新父项目只有存在相应有效授权时才能访问。移动失败时原范围不变。

### AC-FPROJ003-006 幂等与并发

同键同请求授权或撤权返回原结果，同键不同请求拒绝；并发重复授权只形成一个当前有效事实，并发撤权不覆盖成功历史。

### AC-FPROJ003-007 当前业务入口统一过滤

项目详情、工作台、项目树、进度、闭环守卫和拆分入口对同一主体、动作、授权版本和树版本得出一致范围；空范围不返回租户数据。

### AC-FPROJ003-008 模块边界

PROJ不访问PLT授权表，PLT不访问PROJ项目表，其他模块只依赖公开API；源码检查不存在跨模块Service、Mapper、Repository或业务表访问。

### AC-FPROJ003-009 真实浏览器与响应式

真实浏览器完成授权、范围切换、负向访问、撤权和刷新后事实保持；四类视口无页面级横向溢出，控制台和网络无未解释错误。

## 10. 测试与证据要求

| 类别 | 最小证据 |
|---|---|
| 业务规则 | 两种范围、两种动作、成员基础范围、授权越界、撤权、到期和移动重算 |
| 数据库 | 前向迁移、当前授权唯一约束、有效区间、历史不可删除、动作/范围字典、菜单权限、组合示例数据和空集合不扩权 |
| API | 请求/响应、分页、错误分类、`Idempotency-Key`、`If-Match`和内部公开契约 |
| 权限 | 平级隔离、跨租户、角色不自动穿透、超范围授权和越权授权详情 |
| 并发缓存 | 重复授权/撤权、版本冲突、树版本切换、授权收缩和Redis不可用回源 |
| 浏览器 | 授权到撤权完整闭环、四类视口、刷新保持、控制台与失败请求解释 |
| 回归 | F-PROJ-001创建/指派与F-PROJ-002拆分、树、进度和闭环守卫保持通过 |

用户已禁用测试驱动实施顺序；Technical Plan不得强制先制造失败测试，但每个任务提交前仍必须执行与风险匹配的自动化、真实MySQL和真实浏览器验证。

## 11. Definition of Ready

| DoR项 | 证据 | 状态 |
|---|---|---|
| Requirement与业务价值 | 本文第1～3节 | PASS |
| Business Rules与Permission | 本文第4节 | PASS |
| API、Data Change与模块边界 | 本文第5～7节、ADR-0034 | PASS |
| Acceptance Criteria | 本文第9节 | PASS |
| UI与验证 | 本文第8～10节 | PASS |
| 相关Open Question | `open-questions.md`无PM-04未关闭业务决策 | PASS |
| 前置Feature | F-PROJ-002 `IMPLEMENTATION_COMPLETE / PASS` | PASS |
| 书面规格复核 | 需求方于2026-08-24确认本文范围、动作、模块边界和验收标准 | PASS |

结论：`BASELINE / READY`。NPDMS须锁定本次规格提交并重新生成V1.8 Technical Plan；不得根据F-PROJ-002已有实现直接勾选本Feature AC。

## 12. 追溯与完成边界

| Requirement | 本Feature规则/AC | 实施声明 |
|---|---|---|
| PM-04 | BR-FPROJ003-001～006；AC-FPROJ003-001～009 | 完成项目角色、显式子树授权及当前PROJ入口统一范围切片；其他业务对象接入前不宣称PM-04完整实现 |
| AUT-01/AUT-02 | BR-FPROJ003-003/006；AC-FPROJ003-003/004/006/008 | 只实现PM-04需要的同步AuthorizationGrant载体和查询，不实现V2 OA申请及外部授权闭环 |

## 13. Open Questions

当前无会改变本Feature业务语义、Owner、权限动作、授权范围或验收标准的未关闭问题。Java类型、迁移版本、索引名称、页面组件复用点和运行路由由Technical Plan基于NPDMS当前实现锁定，不构成Feature Ready阻断。
