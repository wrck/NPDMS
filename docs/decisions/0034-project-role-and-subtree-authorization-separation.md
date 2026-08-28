# ADR-0034：项目角色与项目子树授权范围分离

> 状态：`ACCEPTED`
> 日期：2026-08-24
> 适用基线：PRD V1.8
> Requirement：`PM-04`；关联`AUT-01`、`AUT-02`

## 背景

PM-04要求项目授权显式区分“当前项目”和“当前项目及全部后代”，并在授权、撤权、到期和项目移动后保持服务端数据范围正确。F-PROJ-002已建立无固定深度项目树和基础`ProjectTreeScope`，但现有实现按项目经理或服务经理角色自动穿透全部后代，无法表达同一角色的不同授权范围，也无法证明撤权、到期和跨办事处专项授权。

正式SDS已经分别定义`ProjectMemberAssignment`和基础平台`AuthorizationGrant`。若继续把范围塞入项目成员角色，会把“担任何种项目角色”和“可对哪些项目执行何种动作”重新耦合，并在授权范围扩展时产生重复角色记录。

## 决策

1. `ProjectMemberAssignment`只表达项目角色、成员和生效区间：
   - 当前有效成员关系只授予该角色在当前项目允许的动作；
   - 项目经理、一级服务经理或二级服务经理角色本身不自动获得全部后代；
   - 不在成员表增加重复的授权范围字段。
2. `AuthorizationGrant`由PLT拥有，使用`plt_authorization_grant`保存主体、资源、动作、范围、生效区间、来源、授予与撤销事实：
   - PM-04范围代码固定为`CURRENT_PROJECT`、`PROJECT_AND_DESCENDANTS`；
   - 项目动作至少区分`PROJECT_VIEW`、`PROJECT_MANAGE`；
   - 授权行保留历史，撤权关闭有效区间并记录撤销事实，不物理删除。
3. PROJ负责项目语义编排：
   - 项目授权命令先校验操作者租户、功能权限、服务经理角色和当前管理范围，再调用PLT公开`AuthorizationGrantApi`；
   - 被授权范围不得超过操作者自己的有效管理范围；
   - PLT不读取PROJ表，也不推断项目层级。
4. PROJ公开`ProjectScopeApi`作为其他业务模块复用项目范围的唯一入口：
   - 由PROJ合并当前成员关系、PLT有效授权和当前完整项目树版本；
   - 其他模块不得读取`proj_`或`plt_authorization_grant`表，也不得自行复制项目树授权算法；
   - 当前没有调用方的业务对象只登记契约，待其Feature实施时接入，不提前创建空实现。
5. 项目移动不复制或改写继承授权：
   - 权限判断始终基于最新完整`treeVersion`动态展开`PROJECT_AND_DESCENDANTS`；
   - 移动成功后旧父树不再命中，新父树仅按已有有效授权重新计算；
   - 树版本或授权版本变化使旧权限缓存失效，敏感读写仍回源校验。

## 备选方案

### 在项目成员表增加`scope_type`

不采用。该方案把角色与授权范围耦合，无法自然承载非成员专项授权，并与已批准的`AuthorizationGrant`重复。

### 由各业务模块自行实现项目范围过滤

不采用。重复算法会在树移动、撤权和缓存失效时产生不同结果，并违反跨模块数据所有权边界。

### 建设通用策略引擎

不采用。PM-04只有两种项目范围和两种必要动作，通用策略DSL、表达式执行器或多版本规则引擎超出当前需求。

## 后果与门禁

- F-PROJ-003以前向迁移建立`plt_authorization_grant`，不修改已执行迁移。
- 需要形成非空的PLT公开API和PROJ公开范围API；模块之间不得依赖对方Service、Mapper、Repository或表。
- F-PROJ-003先覆盖已实现的项目详情、项目树、进度和拆分入口；任务、设备、交付件、割接和巡检在各自Feature实施时必须接入同一`ProjectScopeApi`，在此之前PM-04不得宣称完整实现。
- 本决策是对既有SDS载体的Feature级精化，不改变Phase 1/2/3基线结论，也不批准Deployment、SIT、UAT或Release。
