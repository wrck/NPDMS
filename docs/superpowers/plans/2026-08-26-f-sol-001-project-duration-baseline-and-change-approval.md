# F-SOL-001 项目工期基线与变更审批 Implementation Plan

> **执行要求：** 使用 `superpowers:executing-plans` 按Task顺序实施；用户已明确禁用TDD，因此每个Task先完成当前最小实现，再执行与风险相称的自动化、真实MySQL或浏览器验证。每个Task独立复审并按情况本地提交，不推送。

**Goal:** 在SOL拥有的ConstructionPlan聚合内建立项目唯一当前工期、版本化变更及平台BPM单节点审批闭环，同时保持PLN-01/PLN-04、PRE-02和PLT-02的独立边界。

**Architecture:** `pms-module-engineering`持有三张`sol_*`表、HTTP入口、应用服务与BPM终态消费；`pms-module-project-api`只前向提供项目资格/当前参与人事实；`pms-module-platform-api`继续提供既有幂等和审计；`pms-module-integration`只扩展既有`BPM_APPROVAL`已知流程键集合。SOL不读PROJ表、不修改BPM或Yudao基础框架、不提前实现计划重算。

**Tech Stack:** Java 25、Spring Boot、MyBatis/MyBatis-Plus、MySQL 8.4/Flyway、Flowable、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Specification:** `specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`、`specs/features/F-SOL-001-physical-contract.json`，锁定规格提交`f2c563df978b7d7b3b1de9ad245b9c485bbdbae8`，Feature Ready裁决`NPDMS-FSOL001-FEATURE-READY-20260826-01-R1`。

## Global Constraints

- 受管规格快照只由同步工具维护；实施期间不直接修改`specs/`、`docs/specification-baseline/manifest.json`或已执行的V1～V89迁移。
- `specs/001-project-delivery-platform/`只作历史证据，不作为实现门禁或计划驱动输入。
- 当前工作树内完成，不创建第二工作树，不带入其他工作树的Feature、数据库名、端口或计划参数。
- 不修改`yudao-framework/**`和`yudao-module-bpm/**`。平台多租户由`yudao.tenant.enable`控制；单租户关闭且上下文为空时，仅在HTTP调用范围使用现有`TenantUtils.execute(0L, ...)`模式，多租户启用或配置缺失时失败关闭。
- 新增Mapper查询先遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外只接收单一场景Query对象；复杂查询和锁定读放Mapper XML；禁止位置参数列表、`Map`、SQL注解、`${}`和`.last(...)`；空集合不得扩大范围。
- SOL只使用`PROJECT_VIEW/PROJECT_MANAGE`，不新增第三种ProjectScope动作；所有写命令按“PROJ锁定重验→SOL行锁/CAS→业务写入”排序。
- 三类事实分轴：change生命周期、plan当前revision指针、plan重算影响状态。F-SOL-001只写`PENDING_RECALCULATION`，不调用PLN-01、不改旧施工计划、不发Outbox。
- 首次创建、草稿创建和提交使用`PlatformCommandExecutionApi`；成功事实、幂等与`plt_operation_audit`同事务。失败事务回滚后使用`OperationAuditApi`记录稳定拒绝码和安全事实。
- 前端响应式支持320/768/1024/1440，优先复用Yudao组件，其次Element Plus；不堆叠内联样式，颜色/间距使用主题变量。
- 每段旧逻辑都先核对再决定复用；存在不等于已实现。拷贝复用后只保留一个当前实现，不形成新旧长期双份逻辑。
- 每个Task完成实现、验证和自审后送独立Implementation Done复审；GO后回写Task为PASS并按情况本地提交。不得把Task自测当作独立GO。

## Current Implementation Audit

1. `pms-module-engineering`已有SOL承载模块，但没有ConstructionPlan代码、`sol_construction_plan*`表或PRE-01入口。
2. V1.7 `ScheduleBackwardServiceImpl`的`ChronoUnit.DAYS.between(start,end)+1`和`end=start+days-1`思路可拷贝到纯日期规则；其阶段倒排、默认7天、缓冲天数和写`pms_project_phase`全部不复用。
3. V1.7 `PlanChangeServiceImpl`的草稿状态校验可作差距证据；其数字状态、请求内审批、直接撤回、删除/重建快照及通过后覆盖阶段计划全部不复用。
4. `ProjectProgressPolicyService`已证明可通过`BpmProcessInstanceApi`创建流程、冻结`processInstanceId`和标准`projectId(Long)`，`ProjectProgressPolicyBpmListener`可作为终态监听结构参考；F-SOL必须补非申请人主责服务经理、三种终态和plan/change锁定重验。
5. `ProjectMemberAssignmentMapper`已有当前项目成员和`assignment_type IS NULL OR PRIMARY`兼容语义，可复用查询条件；F-SOL所需公共事实接口尚不存在。
6. `PlatformCommandExecutionApi`、`OperationAuditApi`、`DictDataApi`和`ConfigApi`可直接复用；无需新增幂等、审计、字典或配置基础框架。
7. `BpmGovernanceGuardProvider`当前只查询进度策略流程键，需要改为封闭的已知PMS流程键集合并保持标准`projectId(Long)`、受信租户及未知关联失败关闭。
8. 当前仓库没有`plt_file_artifact/plt_file_version/plt_file_reference`或FileArtifact公共事实接口；`FileApi`只有上传与URL能力，不能满足稳定ID、版本和安全状态校验。F-SOL不得在SOL表或Infra URL上伪造该权威事实。
9. 前端已有项目详情惰性面板、响应式断点、`If-Match/Idempotency-Key`请求模式和平台BPM待办入口，可按现有结构增加项目工期面板；旧倒排/计划变更页面只作差距证据。

## Known Upstream Dependency

`PLT-02 / FileArtifact`尚未实施。Task 1～5以及Task 7中不涉及真实文件校验的部分可继续；Task 6只能先完成字典/配置解析、冻结字段、BPM提交主链和文件校验调用边界，默认种子`CUSTOMER_DELAY`的成功提交、Task 9真实MySQL对应场景、Task 10完整业务浏览器闭环及Feature Done必须等待PLT-02提供已批准的稳定文件ID+版本+权限/安全状态公共事实。

F-SOL-001内不得创建`plt_file_*`表、空`-api`、URL兼容字段、总是成功的适配器或假安全状态。该依赖不影响Technical Plan审查，也不阻断此前可独立验收的Task；推进到依赖点时登记`BLOCKED_BY_UPSTREAM_IMPLEMENTATION: PLT-02`并转入工程链允许的下一可执行单元。

## Reuse-First Execution Rule

每个Task实施前只做一次针对性差距核对：若旧逻辑同时满足当前Owner、状态、权限、事务和API契约，则拷贝后改名/收窄；任一项不满足，只复用局部纯函数或结构。实施完成后再执行计划内验证，不在写代码前构造推测性异常。

---

### Task 1: 建立SOL工期物理模型、配置种子和Feature工作单

**Files:**
- Create: `sql/migrations/V90__fsol001_construction_plan_duration.sql`
- Create: `sql/migrations/V91__fsol001_duration_seed.sql`
- Create: `tasks/features/F-SOL-001.md`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/enums/ErrorCodeConstants.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/ConstructionPlanMigrationContractTest.java`

**Consumes:** 机器物理契约三表、三轴状态、V1～V89不可变、字典/配置/权限和旧入口冻结规则。

- [x] **Step 1: 创建三张前向表**

V90创建`sol_construction_plan`、`sol_construction_plan_revision`、`sol_construction_plan_change`。使用`tenant_id+id`引用键及契约中的稳定唯一键、索引和SOL内部复合外键；`project_id`不建PROJ外键。plan两个revision指针物理可空以解除插入环，但应用事务提交前必须满足非空同租户同plan不变量。根唯一键精确为`uk(tenant_id,project_id)`，不含`deleted`；不创建`sol_construction_plan_item`。

- [x] **Step 2: 建立确定性种子**

V91以幂等SQL写`pms_duration_change_reason_type`、`CUSTOMER_DELAY`、配置`pms.sol.duration-change.customer-evidence-required-reason-codes=CUSTOMER_DELAY`和三项功能权限菜单。新写权限不自动授予角色。冻结旧工期倒排/计划变更PRE-01写菜单和角色关联，不删除旧表、历史数据或仍供后续PLN分析的只读证据。

- [x] **Step 3: 建立错误码和工作单**

在engineering错误码现有分段后新增`construction-plan`专用稳定错误码，覆盖不存在、参数、状态、版本、项目事实、待审冲突、BPM配置/关联、字典配置和FileArtifact不可用/无权。创建`tasks/features/F-SOL-001.md`，记录Feature Ready PASS、锁定规格提交、Technical Plan待审、Task 1～10和PLT-02依赖；不复制历史`tasks/plan.md`。

- [x] **Step 4: 实施后验证并提交**

验证V1→V91空库迁移、三表字段/唯一键/外键、同项目重复根、指针约束策略、字典/配置/权限种子幂等、V1～V89内容未变和无旧表双写。运行迁移契约测试、`mvn -pl pms-module-engineering -am -DskipTests compile`及`git diff --check`。

Expected: 迁移契约和编译PASS。提交：`feat(engineering): 建立项目工期物理基础`

---

### Task 2: 提供PROJ项目资格与当前参与人公共事实

**Files:**
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/ProjectParticipantFactApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/dto/ProjectParticipantFactQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/dto/ProjectParticipantFactRevalidationQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/dto/ProjectParticipantFact.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/participant/ProjectParticipantFactApiImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectParticipantFactLookupQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/query/ProjectParticipantFactLockQuery.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMemberAssignmentMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/participant/ProjectParticipantFactApiImplTest.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectParticipantFactMapperTest.java`

**Consumes:** `ProjectScopeApi`、`proj_project`、`proj_project_member_assignment`，不向SOL暴露DO/Mapper。

- [x] **Step 1: 定义封闭公共契约**

`inspect(query)`要求`projectId`和非空`requiredRoleCodes`；`subjectUserId`为空时只选择当前符合角色的项目经理或主责服务经理。结果返回project/user/effectiveRoleCodes/assignmentType/lifecycleStatus/currentStage/projectVersion/factVersion。角色值域只接受`PROJECT_MANAGER/SERVICE_MANAGER_L1/SERVICE_MANAGER_L2`。

`lockAndRevalidate(query)`要求projectId、userId、expectedProjectVersion、requiredLifecycleStatus、requiredRoleCodes，可选requiredCurrentStage；受信tenantId来自调用上下文且必须与query一致。任何空、越租户、版本变化、角色不符或多主责歧义失败关闭。

- [x] **Step 2: 实现当前读与锁定读**

Mapper XML按`proj_project`行先锁定，再读取当前有效成员区间；PRIMARY兼容沿用既有`assignment_type='PRIMARY' OR assignment_type IS NULL`。`checkedAt`只用于inspect时态快照；锁定重验使用服务端事务时间。空角色集合直接拒绝，不生成全量查询。

- [x] **Step 3: 实施后验证并提交**

覆盖当前项目经理、L1/L2主责、NULL主责兼容、排除协同、申请人与审批人同一用户、ACTIVE/S1、离开S1后的ACTIVE、项目版本冲突、跨租户、空角色、历史区间和并发角色变化。真实MySQL验证锁定顺序与最新事实。

Expected: 公共API和真实Mapper回归PASS，`pms-module-project-api`不依赖project biz实现。提交：`feat(project): 提供项目参与人权威事实`

---

### Task 3: 实现工期日期规则与三表持久化原语

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/constructionplan/DurationRules.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/constructionplan/ConstructionPlanDO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/constructionplan/ConstructionPlanRevisionDO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/constructionplan/ConstructionPlanChangeDO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanMapper.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanRevisionMapper.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanChangeMapper.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanLockQuery.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanVersionUpdate.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanRevisionPageQuery.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanChangeLockQuery.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanChangeVersionUpdate.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanChangePageQuery.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/query/ConstructionPlanChangeProcessQuery.java`
- Create: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanMapper.xml`
- Create: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanRevisionMapper.xml`
- Create: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanChangeMapper.xml`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/domain/constructionplan/DurationRulesTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanMapperTest.java`

**Consumes:** Task 1物理模型；旧倒排只复制自然日计算思路，不依赖旧Service/Mapper。

- [x] **Step 1: 实现纯日期规则**

`DATE_RANGE`接收start/end并按含首尾计算正整数days；`DURATION_FROM_START`接收start/days并计算`end=start+days-1`。若客户端同时提交派生值，必须与计算值一致；倒置、零/负、溢出和口径字段混用拒绝。

- [x] **Step 2: 实现场景化持久化**

提供按tenant+project唯一查询、plan/change/revision锁定读、plan/change版本CAS、最大revisionNo锁定计算、稳定revisionNo/id分页、createTime/id游标分页及processInstanceId唯一查询。提交冻结后revision工期字段无更新入口；plan无delete/rebuild入口。

- [x] **Step 3: 实施后验证并提交**

覆盖两种口径、派生冲突、稳定分页、同项目唯一根、同plan revision号、同候选唯一change、processInstanceId唯一、CAS及指针同租户同plan。运行聚焦单元/Mapper测试和模块编译。

Expected: 日期规则与持久化原语PASS。提交：`feat(engineering): 持久化项目工期版本事实`

---

### Task 4: 实现首次工期原子生效与只读查询

**Files:**
- Modify: `pms-module-engineering/pom.xml`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/ConstructionPlanApplicationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/ConstructionPlanQueryService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/command/CreateInitialDurationCommand.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/ConstructionPlanController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanCreateReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanRespVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanRevisionPageReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanRevisionRespVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanChangePageReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanChangeRespVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/ConstructionPlanApplicationServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/ConstructionPlanControllerTest.java`

**Consumes:** Task 2 PROJ事实、Task 3持久化、既有ProjectScope/PlatformCommandExecution/OperationAudit。

- [ ] **Step 1: 接入模块依赖和受信上下文**

engineering仅增加`pms-module-project-api`、`pms-module-platform-api`和本Task实际需要的依赖。Controller在`/api/v1/pms/construction-plans`实现配置感知受信租户包装，不接受tenantId/actorId自报。功能权限为query或duration-manage，服务层再次执行PROJECT_VIEW/PROJECT_MANAGE及当前参与人事实收窄。

- [ ] **Step 2: 实现首次创建**

按PROJ `lockAndRevalidate(ACTIVE,S1,PROJECT_MANAGER,expectedProjectVersion)`先锁定资格，再用`PlatformCommandExecutionApi`在同事务插plan占位、revision1、回填current/source revision指针、写`PENDING_RECALCULATION`、幂等成功和审计。任一步失败不留plan/revision/幂等成功或成功审计；同键同载荷重放，同键异载荷冲突。

- [ ] **Step 3: 实现详情与历史查询**

实现按id详情、按projectId空业务结果、revision稳定分页和change稳定分页；查询先解析PROJECT_VIEW范围，不返回文件URL。allowedActions由服务端根据资格、状态和版本生成，不根据前端角色名推断。

- [ ] **Step 4: 实施后验证并提交**

覆盖两种口径首次创建、非S1/非ACTIVE/非当前项目经理、PROJECT_VIEW/PROJECT_MANAGE、单租户0、多租户缺上下文、同键重放/冲突、故障点全回滚、详情/空结果/分页和审计安全快照。

Expected: 首次工期和只读API聚焦测试PASS。提交：`feat(engineering): 实现项目初始工期基线`

---

### Task 5: 实现变更草稿、真实部分PATCH和冻结前校验

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/command/CreateDurationChangeCommand.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/command/PatchDurationChangeCommand.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/patch/DurationChangePatch.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/ConstructionPlanController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangeCreateReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangePatchReqVO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/ConstructionPlanChangeRespVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/DurationChangeControllerTest.java`

**Consumes:** 当前生效revision、Task 2 ACTIVE项目经理事实、Task 3 CAS/分页。

- [ ] **Step 1: 创建草稿**

在PROJ锁定重验ACTIVE/current PROJECT_MANAGER后锁plan，以当前revision为base，生成下一个candidate revision和DRAFT change。通过平台命令事务冻结申请人、原因和候选工期；不设置pending指针、不创建BPM。已有其他DRAFT可并存，但一个candidate只属于一个change；待审存在时仍可查看但不得提交第二个。

- [ ] **Step 2: 实现字段存在性PATCH**

PATCH只在DRAFT且plan/change `If-Match`命中时更新实际提交字段。日期组合按合并后值重算；`reasonDetail`和材料引用提交`null`表示清空，未提交表示保持。空PATCH拒绝；冻结revision无更新入口。

- [ ] **Step 3: 实施后验证并提交**

覆盖仅日期、仅days、仅原因、清空可空材料、空PATCH、旧base、PROJECT_MANAGER/ACTIVE/版本变化、plan/change CAS、同键草稿重放/冲突和失败审计。

Expected: 草稿与部分更新聚焦测试PASS。提交：`feat(engineering): 实现工期变更草稿`

---

### Task 6: 接入BPM提交、文件事实边界和PM-10活动审批守卫

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeProperties.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/command/SubmitDurationChangeCommand.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangeSubmitReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangeSubmitRespVO.java`
- Create: `pms-module-engineering/src/main/resources/processes/pms-sol-duration-change.bpmn20.xml`
- Modify: `pms-module-engineering/pom.xml`
- Modify: `yudao-server/src/main/resources/application.yaml`
- Modify: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProvider.java`
- Modify: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/governance/BpmGovernanceGuardProviderTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeSubmitServiceTest.java`

**Consumes:** 既有BPM API、DictDataApi、ConfigApi、Task 2服务经理事实；真实FileArtifact成功路径受PLT-02依赖约束。

- [ ] **Step 1: 建立单节点BPM资源与配置**

增加流程键配置`pms.sol.duration-change.process-definition-key`，BPMN精确为开始→`serviceManagerApprove`单用户任务→结束。通过`startUserSelectAssignees`冻结一个当前主责L1/L2服务经理，排除申请人，并在change的`approver_user_id`冻结该唯一候选；不修改BPM基础模块。流程变量只含标准`projectId(Long)`、constructionPlanId、durationChangeId，businessKey使用changeId。APPROVE、REJECT、CANCEL的业务授权统一在Task 7同步终态事件入口校验，不绑定只覆盖complete路径的Flowable TaskListener。

- [ ] **Step 2: 实现提交事务**

先PROJ锁定重验ACTIVE/current PROJECT_MANAGER及唯一非申请人主责服务经理，再锁plan/change，重验base=current、DRAFT、无pending和版本。回源启用字典与配置并冻结`customer_evidence_required`。不要求材料时不得伪造引用；要求材料时只调用PLT-02批准的公共事实校验稳定artifactId/version/租户/访问权/安全状态。

创建BPM返回非空实例后才冻结process key/id、revision和evidence，迁移PENDING_APPROVAL并设置pending指针；通过平台命令事务保证同键重放返回原实例、异载荷冲突和整体回滚。PLT-02未就绪时材料必需路径稳定返回`FILE_ARTIFACT_PROVIDER_UNAVAILABLE`，不得把URL或`infra_file`当成功事实。

- [ ] **Step 3: 扩展既有BPM_APPROVAL Provider**

把单个进度策略键收敛为非空、去重、排序的封闭已知PMS流程键集合，加入F-SOL键。每个键仍只通过Flowable RuntimeService按受信tenant+active+标准projectId查询；任一已知键缺配置、查询失败或出现缺失/非Long/非法/越租户关联时整个Provider失败关闭。摘要事实加入流程定义键，避免不同流程实例ID碰撞语义。

- [ ] **Step 4: 实施后验证并提交**

先完成代码与mock边界验证：字典/配置、审批人选择、非申请人、标准变量、同键重放、BPM空ID回滚、旧base/已有pending、Provider多键/活动/终态/未知关联/跨租户。三种终态的功能权限、PROJECT_MANAGE和当前角色回滚验证由Task 7统一执行。PLT-02未就绪时只登记文件成功路径与默认种子端到端阻断，不把Task 6整体回写PASS；其余子项可独立登记PASS。

Expected: BPM与守卫子项PASS；文件成功路径状态为`BLOCKED_BY_UPSTREAM_IMPLEMENTATION: PLT-02`。完整Task提交在阻断闭环后执行：`feat(engineering): 提交工期变更审批`

---

### Task 7: 消费BPM终态并切换唯一当前工期

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeBpmListener.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeBpmAuthorizationGuard.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeBpmResultService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanMapper.java`
- Modify: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanMapper.xml`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanChangeMapper.java`
- Modify: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanChangeMapper.xml`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeBpmResultServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/DurationChangeBpmAuthorizationIntegrationTest.java`

**Consumes:** Task 6冻结流程关联、BPM状态事件、Task 2 PROJ锁定重验。

- [ ] **Step 1: 封闭事件入口**

Listener只接收配置的process definition key并同步处理processInstanceId/status/reason。Spring `ApplicationEventPublisher`在Flowable流程完成命令内同步调用该Listener；Listener或Service抛出的业务异常必须原样向上传播，使当前BPM命令与SOL写入共同回滚，禁止吞异常、异步化或`REQUIRES_NEW`。Service只接受APPROVE/REJECT/CANCEL；未知、非终态、无匹配、重复或乱序事件不推进业务状态。

- [ ] **Step 2: 在三种终态共同入口执行业务授权**

先以冻结processInstanceId读取change关联并取得projectId、applicantUserId和冻结approverUserId，不写SOL事实。APPROVE/REJECT要求当前受信登录用户等于冻结审批人、具备`pms:construction-plan:duration-approve`、PROJECT_MANAGE且仍为当前主责L1/L2服务经理；CANCEL要求当前受信登录用户等于冻结申请人、具备`pms:construction-plan:duration-manage`、PROJECT_MANAGE且仍为当前项目经理。无登录主体、BPM管理员取消、权限失效、范围失效、角色改派、申请人与审批人混同或租户不符均抛稳定业务异常。该同步守卫覆盖`taskService.complete(...)`的通过、`moveTaskToEnd(...)`的驳回和发起人流程取消，不依赖Flowable任务完成监听器。

- [ ] **Step 3: 实现终态事务**

授权守卫先通过PROJ锁定重验ACTIVE、对应当前角色和最新projectVersion事实，再锁plan/pending change并重验process/base/candidate/pending指针。APPROVE切换current revision、清pending、change=APPROVED、写审批时间/意见，并将影响状态和source revision写PENDING_RECALCULATION；REJECT要求非空意见，清pending但保留current和原影响；CANCEL仅在BPM确认申请人取消终态后写WITHDRAWN并保留current。

不调用PLN-01，不改项目阶段和旧施工计划，不写Outbox。若冻结了FileArtifact引用，终态前通过同一PLT-02公共事实重验版本仍存在且安全；Provider不可用则失败关闭并保留PENDING_APPROVAL，供平台事件重试。

- [ ] **Step 4: 实施后验证并提交**

单元覆盖三终态、驳回意见、重复/乱序、旧pending、并发终态单胜、项目版本/角色变化、PLN-01不存在仍批准、指针与审计恰一、FileArtifact失效/不可用。真实BPM集成分别执行APPROVE、REJECT和发起人CANCEL：撤销SOL功能权限、PROJECT_MANAGE或当前角色后，BPM实例/任务不得进入终态且SOL仍为PENDING_APPROVAL、current/pending指针不变；合法冻结审批人/申请人才允许BPM终态与SOL状态同事务提交。PLT-02未就绪时无文件成功子项可PASS，含文件终态仍登记同一上游阻断。

Expected: 无文件终态主链PASS，含文件主链待PLT-02。完整Task提交：`feat(engineering): 生效工期审批结果`

---

### Task 8: 建设响应式项目工期界面并冻结旧PRE-01写入口

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/construction-plan/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationHistoryDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/schedule-backward/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/plan-change/index.ts`

**Consumes:** Task 4～7 HTTP API、平台BPM既有待办/审批/撤回入口。

- [ ] **Step 1: 建立统一前端API**

类型精确表达plan、currentRevision、pendingChange、三轴状态、游标分页和allowedActions。写请求统一发送服务端版本、expectedProjectVersion、`If-Match`和`Idempotency-Key`；不传tenantId、申请人或审批人。

- [ ] **Step 2: 增加项目详情工期面板**

沿用项目详情惰性面板和`useMediaQuery`模式。展示当前起止/天数/口径、PENDING_RECALCULATION、待审摘要、revision/change历史；项目经理按allowedActions录入、建草稿、部分修改和提交。服务经理只跳转平台BPM待办处理；申请人撤回调用既有`ProcessInstanceApi.cancelProcessInstanceByStartUser`，不误用“撤回已办任务”的`BpmTaskApi.withdrawTask`。页面不存在SOL approve/reject/cancel终态接口。

- [ ] **Step 3: 冻结旧写调用**

从当前导航/按钮移除旧工期倒排apply和旧计划变更submit/approve/withdraw/delete写入口；API文件保留明确历史只读调用和后续PLN证据，不改旧表、不双写、不把旧状态映射为当前工期。

- [ ] **Step 4: 实施后验证并提交**

组件验证两种口径、部分PATCH/null清空、按钮使用allowedActions、BPM跳转、刷新持久、文件Provider不可用提示和320/768/1024/1440布局。运行组件测试、`corepack pnpm ts:check`、定向ESLint/Stylelint及`build:local`。

Expected: 组件、类型、样式和构建PASS。提交：`feat(ui): 建设响应式项目工期面板`

---

### Task 9: 完成真实MySQL、事务并发和BPM集成验证

**Files:**
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/ConstructionPlanMySqlIntegrationTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/DurationChangeBpmMySqlIntegrationTest.java`
- Modify: `tasks/features/F-SOL-001.md`

**Consumes:** Task 1～8实现；PLT-02真实公共事实是材料场景前置。

- [ ] **Step 1: 执行空库迁移与种子验收**

使用当前工作树独立Compose项目从空库执行V1～V91；验证三表、唯一键、同租户外键、两个revision指针、字典/配置/权限种子、旧表零双写和迁移幂等。

- [ ] **Step 2: 执行应用服务级真实事务验收**

装配真实ConstructionPlan/DurationChange服务、ProjectParticipantFactApi、PlatformCommandExecutionApiImpl、OperationAuditApiImpl、Flowable。覆盖首次创建全回滚、草稿/PATCH、提交BPM、同键重放/异载荷、单待审、APPROVE/REJECT/CANCEL、并发提交/终态单胜、项目/角色变化和审计恰一。

- [ ] **Step 3: 验证PM-10守卫**

活动F-SOL流程以标准projectId进入BPM_APPROVAL blocker；终态后从活动事实消失。缺失/非Long/跨租户变量失败关闭，provider版本/水位/摘要随实例新增终止变化。

- [ ] **Step 4: 处理PLT-02依赖并提交**

PLT-02就绪后补真实CUSTOMER_DELAY FileArtifact稳定ID/版本/权限/安全状态成功、失效、越权和Provider不可用场景；未就绪则Task 9不得整体PASS，但其他MySQL/BPM子项可登记PASS并继续无依赖工作。

Expected: 全部真实MySQL和BPM集成PASS后提交：`test(engineering): 验证项目工期事务闭环`

---

### Task 10: 完成真实浏览器、独立复审和Feature Done回写

**Files:**
- Create: `docs/engineering/evidence/f-sol-001-browser-evidence.json`
- Modify: `tasks/features/F-SOL-001.md`
- After independent GO, modify in specification repo: `specs/features/README.md`
- After independent GO, modify in specification repo: `docs/traceability/requirement-matrix.md`

**Consumes:** Task 1～9全部PASS、PLT-02真实文件事实、锁定Feature Spec。

- [ ] **Step 1: 执行真实浏览器闭环**

优先使用内置浏览器。验证首次录入→草稿→CUSTOMER_DELAY材料→提交→BPM主责服务经理通过，以及驳回、申请人撤回、权限负向、项目版本冲突、刷新持久、PLN-01缺失仍生效、PM-10活动审批阻断解除和320/768/1024/1440响应式。记录截图、控制台/网络错误和证据ID。

- [ ] **Step 2: 执行完整验证与边界审计**

运行受管快照校验、后端相关Reactor、前端Task 8命令、真实MySQL/BPM、`git diff --check`。确认无`yudao-framework/**`、`yudao-module-bpm/**`改动，无`sol_construction_plan_item`、无Outbox、无PRE-02/PLN-01/PLN-04实现、无旧表新写和无SOL直接审批终态入口。

- [ ] **Step 3: 请求独立Implementation Done复审**

提交Task 1～9提交链、浏览器/MySQL/BPM证据、FileArtifact真实事实、AC-FSOL001-001～013映射和边界审计。仅独立GO后将Task 10与Feature标记PASS。

- [ ] **Step 4: 回写规格并同步新基线**

独立GO后在规格仓库回写Feature索引/追溯真实提交和证据，创建新规格提交，再通过同步工具更新NPDMS受管基线。不得进入Deployment、SIT、UAT、Release。

Expected: 完整证据与独立GO成立。提交：`docs(feature): 通过 F-SOL-001 Implementation Done`

## Technical Plan Review Closure

针对`NPDMS-FSOL001-TECHPLAN-20260826-01`唯一NO-GO项，Task 6已删除只绑定`TaskListener.EVENTNAME_COMPLETE`的授权方案；Task 7改为在APPROVE、REJECT、CANCEL共同经过的同步`BpmProcessInstanceStatusEvent`入口，分别校验冻结主体、SOL功能权限、PROJECT_MANAGE和当前角色，异常向上传播并回滚BPM与SOL事务。真实集成回归明确覆盖三终态权限/范围/角色失效时两域均不提交。未修改BPM基础模块，未新增SOL终态写接口，未扩大PLT-02依赖。

## Plan Self-Review

- **Spec coverage:** Task 1～3覆盖三表、三轴、日期规则、唯一键和分页；Task 2覆盖PROJECT_VIEW/MANAGE与资格/角色锁定事实；Task 4～7覆盖首次生效、草稿、BPM提交、终态和PM-10守卫；Task 8～10覆盖旧入口冻结、响应式UI、MySQL/BPM/浏览器和AC-FSOL001-001～013。
- **Dependency direction:** engineering只依赖project-api/platform-api及现有平台API；PROJ不依赖engineering；Integration不读SOL表；BPM基础模块不改；FileArtifact由PLT-02提供，F-SOL不创建假Owner。
- **Type consistency:** projectId/planId/changeId/revisionId/userId/fileArtifactId为Long，fileVersion/planVersion/changeVersion/projectVersion为Integer，processInstanceId为String，日期为LocalDate，BPM标准projectId变量保持Long。
- **Transaction order:** 所有SOL写命令先PROJ锁定重验，再锁SOL plan/change；首次创建、草稿和提交使用平台命令事务，BPM终态按processInstanceId幂等锁定消费。
- **Placeholder scan:** 计划没有待定实现方案。PLT-02是已确认的外部实现依赖及明确失败关闭行为，不以TBD、假Provider或URL兼容层掩盖。
- **Scope scan:** 不包含PRE-02、PLN-01/02/03/04、INT-05、历史迁移、Deployment、SIT、UAT、Release；不修改V1～V89和基础框架。

## Technical Plan Gate

当前状态：`GO / NPDMS-FSOL001-TECHPLAN-20260826-01`。整改提交`5dfaf2c951ec246f4150f9ee77901dd64a97cf43`已获独立批准，允许按计划从Task 1开始实施。
