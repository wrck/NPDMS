# F-PROJ-006 项目回退、异常关闭与受控重开 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本工程链在当前会话内联执行，不启用子代理。

**Goal:** 按PRD V1.8实现项目回退、异常关闭、受控重开、完整树与跨域事实守卫、append-only治理历史及响应式操作界面。

**Architecture:** PROJ拥有项目状态、成员责任区间和共享阶段快照；各事实Owner通过公开只读API返回版本化阻断事实，PROJ签发不透明守卫令牌并在写入前重验。写命令复用PLATFORM命令执行契约，在同一事务提交Project CAS、成员区间、动作快照、幂等成功、审计和Outbox；旧`pms_project_governance_action`仅作审计输入，不再承载V1.8写入。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md`

## Global Constraints

- 规格仓库提交锁定为`cb55c7478378ed769f0a4fd401fabb8840017242`，NPDMS基线提交为`8d6e7e76c213b0fdf1dc7f82e40a8620d1acb43d`；受管快照只由同步工具维护。
- 已通过的PRD/SDS门禁不重开；本计划只推进F-PROJ-006 Implementation，不生成Deployment、SIT、UAT或Release材料。
- V1.7代码、页面、测试和`pms_project_governance_action`只是差距审计输入；不得据此勾选任何V1.8验收项，不再向旧表写入新治理动作。
- 用户已禁用测试驱动顺序；每个Task先完成最小实现，再补齐风险匹配的自动化、真实MySQL或真实浏览器验证。
- 不修改`yudao-framework`及其他基础框架实现；跨模块只使用Owner公开API，不依赖目标Service、Mapper、Repository或业务表。
- 回退保持`lifecycle_status=ACTIVE`，将`current_stage=S0`、`assignment_status=UNASSIGNED`并结束全部当前有效服务经理PRIMARY/COLLABORATOR区间；项目经理事实、任务、文档、设备引用和历史进度不删除。
- 异常关闭只写`EXCEPTION_CLOSED`并保留关闭前阶段；NORMAL_CLOSED只由CLO-02产生，F-PROJ-006不得创建正常闭环或重开NORMAL_CLOSED。
- 重开只消费最近一次未消费的异常关闭快照，恢复`ACTIVE + 关闭前可恢复阶段 + UNASSIGNED`；不自动恢复外部任务或已关闭成员区间。
- `proj_project_stage_snapshot`保持共享唯一键`uk(tenant_id, project_id, stage_code, snapshot_no)`；PM-10字段只做前向可空加法，另加`uk(tenant_id, operation_id)`。
- 回退/关闭请求必须提交`Idempotency-Key`、`If-Match`和最近守卫查询的`guardToken`；Project CAS与树/提供方事实重验同时通过才允许写入。
- 提供方缺失、超时、未知状态或版本/水位/摘要变化一律失败关闭；冲突不得生成成功快照、成功幂等记录或成功事件，允许记录拒绝审计。
- 新查询遵循`docs/coding/database-query-interface.md`：场景Query对象、复杂/锁SQL进入Mapper XML、空权限集合返回空结果、稳定排序、服务端租户与数据范围。
- UI优先复用Yudao组件，其次Element Plus；支持320/768/1024/1440视口、主题变量和无页面级横向溢出，不以页面内联样式固化主题。
- 每个Task验证后按情况独立本地提交，不推送。

## Current Implementation Audit

| 现有资产 | V1.8处置 |
|---|---|
| `ProjectGovernanceServiceImpl`与旧CRUD页面 | 旧逻辑写整数`status`、清空项目经理且没有完整守卫；停止新写，仅保留历史只读入口或下线菜单 |
| `ProjectClosureGuardService` | 复用完整树读取与权限裁剪思路；改造成F-PROJ-006守卫，不把一次性`treeVersion`检查当作可提交令牌 |
| `ProjectTreeVersionDO/ProjectTreeScopeService` | 复用当前完整树版本和VIEW/MANAGE范围；命令提交前重新读取并比较版本 |
| `ProjectMasterDO` | 复用`currentStage/lifecycleStatus/assignmentStatus/version`，新增单一场景CAS更新，不回写兼容`status`作为业务真值 |
| `ProjectMemberAssignmentDO` | 复用时态历史；只结束当前服务经理责任区间，不删除、不复活历史行 |
| `PlatformCommandExecutionApi` | 复用同事务幂等、`plt_operation_audit`与单个Outbox写入；按动作生成稳定响应和事件payload |
| `proj_project_stage_snapshot` | 当前未落物理表；按已批准共享模型前向创建，不改成PM-10专属动作表 |
| `ProjectClosureGuardPanel.vue` | 现有面板只服务CLO-02资格检查；新增独立治理面板，不混淆正常闭环与异常关闭 |
| DAC采集提供方 | 仓库内暂无权威适配实现证据；Task 3先核对现有外部适配配置，缺失时注册UNAVAILABLE事实并继续其他任务，但Feature Done前必须接入可验证的Owner事实来源 |

---

### Task 1: 建立共享快照与权限物理基础

**Files:**
- Create: `sql/migrations/V86__fproj006_project_governance_foundation.sql`
- Create: `sql/migrations/V87__fproj006_project_governance_seed.sql`
- Create: `scripts/tests/test_fproj006_v18_migration.py`
- Create: `tasks/features/F-PROJ-006.md`

**Interfaces:**
- Consumes: F-PROJ-006物理契约的共享表、权限码和事件定义。
- Produces: `proj_project_stage_snapshot`共享表、PM-10可空列、双唯一键、原因字典、稳定权限与菜单种子。

- [ ] **Step 1: 编写V86前向迁移**

创建共享`proj_project_stage_snapshot`，公共列至少为`tenant_id/project_id/stage_code/snapshot_no`；PM-10动作列使用规格中的19个可空字段。建立`uk(tenant_id, project_id, stage_code, snapshot_no)`与`uk(tenant_id, operation_id)`，不得修改V1～V85。

- [ ] **Step 2: 编写V87幂等种子**

写入回退/异常关闭/重开原因字典和`pms:project:governance:query`、`pms:project:rollback`、`pms:project:close`、`pms:project:reopen`权限。旧`pms:project-governance:*`写菜单停用或移除角色关联，历史查询不删除。

- [ ] **Step 3: 增加迁移契约验证**

断言共享唯一键未被替换、PM-10字段物理可空、审计表名为`plt_operation_audit`、未创建独立治理动作/责任工单表、V1～V85哈希未改变。

- [ ] **Step 4: 验证并提交**

Run: `python -m unittest scripts.tests.test_fproj006_v18_migration`

Run: `mvn.cmd -pl pms-module-project -am -DskipTests compile`

Expected: 迁移契约和Reactor编译PASS。提交：`feat(project): 建立项目异常治理物理基础`

---

### Task 2: 实现共享快照持久化与Project原子更新

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectgovernance/ProjectStageSnapshotDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectgovernance/ProjectStageSnapshotMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectgovernance/query/ProjectGovernanceHistoryPageQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectgovernance/query/ProjectExceptionCloseSnapshotQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectGovernanceStateUpdate.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectServiceManagerIntervalClose.java`
- Create: `pms-module-project/src/main/resources/mapper/projectgovernance/ProjectStageSnapshotMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMasterMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml`

**Interfaces:**
- Produces:

```java
int updateGovernanceStateIfMatch(ProjectGovernanceStateUpdate query);
int closeEffectiveServiceManagerAssignments(ProjectServiceManagerIntervalClose query);
PageResult<ProjectStageSnapshotDO> selectGovernanceHistoryPage(ProjectGovernanceHistoryPageQuery query);
ProjectStageSnapshotDO selectLatestReusableExceptionCloseForUpdate(ProjectExceptionCloseSnapshotQuery query);
```

- [ ] **Step 1: 映射共享快照DO**

DO覆盖共享键和全部PM-10可空字段；Java层按`ROLLBACK/EXCEPTION_CLOSE/REOPEN`分别校验应用必填，不给非PM-10行增加全局非空假设。

- [ ] **Step 2: 增加场景化锁查询和历史分页**

复杂查询进入Mapper XML；历史按`operated_at desc, id desc`稳定分页。重开锁查询只返回本项目最近、未被成功REOPEN引用的EXCEPTION_CLOSE快照。

- [ ] **Step 3: 增加Project CAS与成员区间关闭**

单条XML更新同时校验`tenant_id/id/version/lifecycle_status`并写三个状态轴和`version+1`。成员更新只结束当前有效SERVICE_MANAGER_L1/L2的PRIMARY/COLLABORATOR区间，使用服务端事务时点且保持锁顺序稳定。

- [ ] **Step 4: 自动化验证并提交**

覆盖共享行兼容、动作必填、历史分页、同一关闭快照最多一次重开、CAS冲突和成员历史不删除。提交：`feat(project): 提供异常治理状态持久化`

---

### Task 3: 固化守卫公共契约与PROJ事实

**Files:**
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/guard/ProjectGovernanceGuardQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/guard/ProjectGovernanceBlocker.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/guard/ProjectGovernanceProviderFact.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/guard/ProjectGovernanceGuardProviderApi.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/provider/ProjectTreeGovernanceGuardProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/provider/ProjectTaskGovernanceGuardProvider.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectgovernance/query/ProjectTaskGovernanceGuardQuery.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projecttask/ProjectTaskMapper.java`
- Create: `pms-module-project/src/main/resources/mapper/projecttask/ProjectTaskMapper.xml`

**Interfaces:**

```java
record ProjectGovernanceGuardQuery(Long tenantId, Set<Long> projectIds,
                                   String action, LocalDateTime checkedAt) {}
record ProjectGovernanceProviderFact(String provider, String factVersion,
        String watermark, String factDigest, List<ProjectGovernanceBlocker> blockers) {}
interface ProjectGovernanceGuardProviderApi {
    String providerCode();
    ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query);
}
```

- [ ] **Step 1: 固化公共只读事实形状**

字段固定为`provider/factVersion/watermark/factDigest/blockers`，阻断项只暴露`objectType/objectId/status/code/summary`。`projectIds`为空时各Provider返回空事实，不执行全量查询。

- [ ] **Step 2: 实现PROJECT_TREE与PROJECT_TASK事实**

PROJ按同一完整树版本读取全部目标节点，任务查询使用场景Query和XML；进行中/受阻/待验证等非终态任务形成阻断，摘要按数据范围裁剪。

- [ ] **Step 3: 验证并提交**

覆盖完整树版本、任务非终态阻断、空集合、跨租户、版本与摘要稳定性。提交：`feat(project): 提供项目治理本域守卫事实`

---

### Task 4: 提供CUT与INSPECTION守卫事实

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/governance/CutoverGovernanceGuardProvider.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/task/query/CutoverGovernanceGuardQuery.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/task/CutTaskMapper.java`
- Create: `pms-module-cutover/src/main/resources/mapper/task/CutTaskMapper.xml`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/api/governance/InspectionGovernanceGuardProvider.java`
- Create: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/srvtask/query/InspectionGovernanceGuardQuery.java`
- Modify: `pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/srvtask/SrvTaskMapper.java`
- Create: `pms-module-service/src/main/resources/mapper/srvtask/SrvTaskMapper.xml`
- Modify: `pms-module-cutover/pom.xml`
- Modify: `pms-module-service/pom.xml`

**Interfaces:** Consumes Task 3的`ProjectGovernanceGuardProviderApi`；Produces Provider代码`CUTOVER/INSPECTION`。

- [ ] **Step 1: 实现CUTOVER事实**

CUT内部按租户和projectIds读取割接任务；非终态形成阻断，使用任务版本与更新时间生成稳定水位和摘要，空集合返回空事实。

- [ ] **Step 2: 实现INSPECTION事实**

SRV内部按相同契约读取巡检任务；只由SRV解释终态，不让PROJ复制状态枚举或访问Mapper。

- [ ] **Step 3: 验证并提交**

覆盖无任务、非终态、终态、未知状态、跨租户、空集合和摘要稳定性。提交：`feat(project): 接入割接巡检治理守卫`

---

### Task 5: 提供BPM与COLLECTION集成守卫

**Files:**
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProvider.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/governance/CollectionGovernanceGuardProvider.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/governance/CollectionGovernanceGuardProperties.java`
- Modify: `pms-module-integration/pom.xml`
- Modify: `yudao-server/src/main/resources/application-local.yaml`
- Modify: `.env.example`

**Interfaces:** Consumes Task 3共享守卫契约；Produces Provider代码`BPM_APPROVAL/COLLECTION`。

- [ ] **Step 1: 实现BPM适配**

通过PMS集成适配层调用Flowable公开能力生成项目审批事实，不修改`yudao-module-bpm`、不直查BPM表；活动实例形成阻断，实例版本/更新时间形成水位和摘要。

- [ ] **Step 2: 核对并实现COLLECTION适配**

先从仓库配置和现有外部子应用契约确认权威端点；使用`NPDMS_COLLECTION_GUARD_BASE_URL`配置只读客户端。未配置、超时或响应未知时返回`PROVIDER_UNAVAILABLE`阻断，禁止以“仓库没有表”推导无任务。

- [ ] **Step 3: 验证并提交**

覆盖活动/完成审批、采集无任务/在途、未配置、超时、乱序水位、空集合和跨租户。提交：`feat(integration): 提供审批采集治理守卫`

> 检查点：若COLLECTION权威端点仍无仓库或环境证据，登记为Feature Done前阻断并继续Task 6～9；不得伪造“无采集任务”正向事实。

---

### Task 6: 签发并重验不透明守卫令牌

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardTokenService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceProviderRegistry.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/config/ProjectGovernanceGuardProperties.java`
- Modify: `yudao-server/src/main/resources/application-local.yaml`
- Modify: `.env.example`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceGuardServiceTest.java`

**Interfaces:**

```java
ProjectGovernanceGuardResult evaluate(Long projectId, GovernanceAction action, Actor actor);
VerifiedGuard verifyAndRevalidate(String guardToken, Long projectId,
                                  GovernanceAction action, Integer expectedProjectVersion,
                                  Actor actor);
```

- [ ] **Step 1: 聚合完整树与全部必需Provider**

按`PROJECT_TREE/BPM_APPROVAL/PROJECT_TASK/CUTOVER/COLLECTION/INSPECTION`固定集合执行；任一缺失、异常、超时、未知状态都形成阻断，不能因其他Provider通过而降级。

- [ ] **Step 2: 签发不透明令牌**

令牌冻结`tenantId/projectId/action/projectVersion/treeRootProjectId/treeVersion/providerFacts/checkedAt`，使用Feature专用环境变量`NPDMS_PROJECT_GOVERNANCE_GUARD_SIGNING_KEY`提供HMAC密钥；配置模板只声明变量，不写真实密钥，响应和日志不暴露密钥或可修改明文。

- [ ] **Step 3: 提交前重新读取并逐项比较**

重新读取最新完整树和全部Provider事实，比较每个冻结claim；Project版本、树版本、factVersion、watermark或factDigest任一变化均返回VERSION_CONFLICT且不进入成功事务。

- [ ] **Step 4: 验证并提交**

覆盖篡改令牌、跨租户重放、动作错配、树T到T+1、Provider水位变化、超时和无副作用。提交：`feat(project): 增加版本化项目治理守卫`

---

### Task 7: 实现回退命令闭环

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/command/RollbackProjectCommand.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/command/GovernanceActionResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceServiceImpl.java`
- Test: focused service and real-MySQL concurrency tests

**Interfaces:**

```java
GovernanceActionResult rollback(RollbackProjectCommand command, Actor actor);
record RollbackProjectCommand(Long projectId, Integer expectedVersion,
        String guardToken, String reasonCode, String reasonDetail,
        String reassignmentRequirement, String idempotencyKey, String requestDigest) {}
```

- [ ] **Step 1: 执行稳定权限与主体守卫**

服务端依次校验租户、`pms:project:rollback`、MANAGE范围、当前有效PRIMARY服务经理、ACTIVE状态、If-Match和guardToken，不信任前端按钮状态。

- [ ] **Step 2: 原子执行回退**

在同一事务写`ACTIVE/S0/UNASSIGNED`、结束全部当前服务经理区间、追加ROLLBACK快照、幂等成功、`plt_operation_audit`和`ProjectStageChanged` Outbox；项目经理及既有交付事实保持不变。

- [ ] **Step 3: 验证幂等与并发**

覆盖同键同摘要重放、同键异摘要冲突、Project CAS冲突、守卫过期、同版本并发唯一成功和失败零成功副作用。

- [ ] **Step 4: 提交**

提交：`feat(project): 实现项目回退命令闭环`

---

### Task 8: 实现异常关闭与受控重开

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/command/ExceptionCloseProjectCommand.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/command/ReopenProjectCommand.java`
- Modify: `ProjectGovernanceApplicationService.java`
- Extend: `ProjectStageSnapshotMapper.java` and XML
- Test: focused service and real-MySQL concurrency tests

**Interfaces:**

```java
GovernanceActionResult close(ExceptionCloseProjectCommand command, Actor actor);
GovernanceActionResult reopen(ReopenProjectCommand command, Actor actor);
```

- [ ] **Step 1: 原子执行异常关闭**

校验`pms:project:close + MANAGE + 工程管理部关闭岗`及最新守卫，将ACTIVE改为EXCEPTION_CLOSED，保留阶段，结束服务经理区间并保存businessBasis/legacyItems快照；发布`ProjectClosed`。

- [ ] **Step 2: 实现受控重开**

校验`pms:project:reopen + MANAGE + 工程管理部关闭岗`；只锁定并消费最近有效异常关闭快照，恢复ACTIVE、快照beforeStage和UNASSIGNED，不恢复外部任务或历史成员区间，发布`ProjectStageChanged`。

- [ ] **Step 3: 验证负向状态与并发**

覆盖NORMAL_CLOSED拒绝、非最近/已消费快照拒绝、两次并发重开最多一个成功、关闭守卫变化拒绝及全部失败零成功副作用。

- [ ] **Step 4: 提交**

提交：`feat(project): 完成异常关闭与受控重开`

---

### Task 9: 提供治理API与append-only历史查询

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/ProjectGovernanceCommandController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectGovernanceGuardRespVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectRollbackReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectExceptionCloseReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectReopenReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectGovernanceHistoryPageReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/vo/ProjectGovernanceHistoryRespVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projectgovernance/ProjectGovernanceController.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectgovernance/ProjectGovernanceHistoryQueryService.java`
- Modify frontend API: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Test controller contract and permission tests

**Interfaces:**
- `GET /pms/projects/{id}/governance-guard?action=...`
- `POST /pms/projects/{id}/actions/rollback`
- `POST /pms/projects/{id}/actions/close`
- `POST /pms/projects/{id}/actions/reopen`
- `GET /pms/projects/{id}/governance-history`

- [ ] **Step 1: 固化HTTP契约**

写请求从Header读取`Idempotency-Key/If-Match`，回退/关闭请求体必含guardToken和动作字段；历史分页设置页大小上限并稳定排序。

- [ ] **Step 2: 对齐前后端权限真值**

Controller使用四个稳定权限码；服务端再次执行租户、功能权限、ProjectTreeScope、主体、状态、版本和业务守卫。旧CRUD写路由停止提供，历史读取不转换旧表为V1.8当前事实。

- [ ] **Step 3: 验证并提交**

覆盖400/403/404/409/422分类、权限矩阵、未授权平级不泄露及字段映射。提交：`feat(project): 提供项目异常治理接口`

---

### Task 10: 完成响应式治理界面与Feature验收

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectGovernancePanel.vue`
- Create: `ProjectGovernancePanel.spec.ts` beside the component
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify or retire write actions in `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-governance/index.vue`
- Modify: `tasks/features/F-PROJ-006.md`

**Interfaces:** Consumes Task 9 API；前端不缓存权限、树版本或Provider事实作为提交真值。

- [ ] **Step 1: 实现治理面板**

按当前状态和权限展示回退、异常关闭、重开入口；先查询守卫并分页展示阻断摘要，再收集原因、重新指派要求、业务依据或遗留事项。提交前携带服务端guardToken，不自行重算allowed。

- [ ] **Step 2: 实现append-only历史视图**

按动作显示前后阶段、生命周期、指派状态、原因、树版本、操作者和时间；敏感业务依据仍由后端原项目权限裁剪。

- [ ] **Step 3: 执行自动化和真实MySQL**

执行PROJ、PLATFORM及守卫Provider定向测试和模块回归；全新隔离库执行V1～V87；验证回退/关闭/重开事务、并发、跨租户、树T→T+1和Provider水位变化无副作用。

- [ ] **Step 4: 使用真实浏览器验收**

优先内置浏览器，必要时使用已授权外部Chromium。验证守卫、回退、关闭、重开、历史、刷新持久化、错误提示以及320/768/1024/1440视口；检查主题切换、横向溢出、控制台和失败请求。

- [ ] **Step 5: 独立Implementation Done复审并提交**

将自动化、MySQL、浏览器、COLLECTION权威来源和遗留边界写入Task记录，取得独立GO后回写Feature状态；不生成Deployment/SIT/UAT/Release材料。提交：`feat(project): 完成项目异常治理闭环`

---

## Checkpoints

- Task 1～2：共享物理契约、Project CAS和历史持久化经真实MySQL验证，旧迁移未修改。
- Task 3～6：所有必需Provider均返回版本化事实；缺失/超时失败关闭，旧树或旧事实令牌不可提交。
- Task 7～9：三类命令、权限、幂等、事务和历史API闭环，失败不产生成功副作用。
- Task 10：自动化、空库迁移、真实浏览器、四档响应式和独立复审全部通过。

## Risks and Handling

| 风险 | 影响 | 处理 |
|---|---|---|
| COLLECTION权威来源尚无仓库证据 | 不能可信判定无在途采集 | Task 5先核对现有适配；未配置时明确UNAVAILABLE并失败关闭，Feature Done前必须补齐可验证来源 |
| BPM现有公开API只支持创建/触发 | 不能直接按项目读取审批水位 | 在PMS集成适配层使用Flowable公开能力，不修改基础模块、不直查表；版本/摘要由适配层稳定生成 |
| 树在守卫后并发变化 | 旧检查可能误关闭新增ACTIVE后代 | guardToken冻结treeVersion，命令提交前重读最新完整树并逐claim比较 |
| 共享快照被收窄成PM-10表 | 破坏PM-03/EXE-06后续复用 | 保持共享键和公共列；PM-10列物理可空，动作必填只在应用层验证 |
| 旧治理页面继续写旧事实 | 形成双写和状态冲突 | V87停用旧写权限；Controller和UI只保留历史只读，不自动转换旧记录 |

## Self-Review

- Spec覆盖：状态轴、权限、完整树、跨域阻断、守卫令牌、Project CAS、成员时态、共享快照、幂等、审计、Outbox、历史查询和响应式UI均有对应Task。
- 禁止能力：计划不含CLO-02正常闭环、NORMAL_CLOSED重开、自动终止/恢复外部任务、新责任工单表、新状态轴或旧Flyway修改。
- 类型一致性：`guardToken/treeVersion/providerFacts/reasonCode/reasonDetail/operationId`在物理、服务、HTTP和UI间使用同一语义。
- 占位扫描：无延后填充项；COLLECTION未知不被猜测为成功，而是有明确失败关闭行为和Feature Done门禁。
