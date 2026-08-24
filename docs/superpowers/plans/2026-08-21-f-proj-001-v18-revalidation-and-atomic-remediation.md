# F-PROJ-001 V1.8 Revalidation and Atomic Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将V1.7手动项目创建和模板基座按PRD V1.8重新检查并改造为一次事务内完成的正式项目创建闭环，满足`ACTIVE / S0 / UNASSIGNED`、任务执行契约、ACC交付件Owner边界、平台幂等/审计/Outbox及真实浏览器验收。

**Architecture:** 保留可证明符合V1.8的项目编码、模板发布快照和项目树能力，通过新的前向Flyway补齐V1.8物理承载；创建命令由PROJ应用服务统一编排，并同步调用以`Propagation.MANDATORY`参与同一MySQL事务的ACC内部应用接口。Controller只做协议转换，幂等成功记录、审计、Outbox以及全部Owner事实与业务事务共同提交或回滚。

**Tech Stack:** Java 25、Spring Boot、Spring Transaction、MyBatis-Plus、Flyway、MySQL 8、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、pnpm、真实浏览器。

**Spec:** `specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`（锁定规格提交`9087469316ec5ba321b34f09fc601d98c30a3d2b`）

## Global Constraints

- 本计划是PRD V1.8重新审计后的新实施输入；不得读取、恢复或复用`docs/superpowers/plans/2026-08-21-f-proj-001-manual-project-creation-and-template-initialization.md`。
- 受管Feature Spec中的旧计划路径属于待规格仓库前向修订的失效引用，不得在NPDMS直接修改该受管快照。
- 自2026-08-22起，`specs/001-project-delivery-platform/`仅作历史参考；实施过程不再运行其规格基线校验，也不以该目录的哈希差异判定当前工程门禁。
- 现有V1.7类、表、页面和测试仅是复用审计证据；本轮按用户指示禁用测试驱动，先完成V1.8差异审计和最小改造，再补充事后测试与完整验证，不得因已有实现或已有测试直接判定完成。
- 不修改已执行的`V57__proj_project_manual_creation.sql`；所有数据库变化使用下一个可用的前向迁移版本。
- 正式创建不得产生Project草稿、`DRAFT`/`INITIALIZING`状态、Saga、异步补偿或浏览器持久化草稿。
- PROJ不得直接访问ACC Repository；ACC初始化接口必须以`Propagation.MANDATORY`加入调用方事务并传播所有异常。
- 每个可执行ProjectTask必须且只能有一个当前执行契约；`TASK_NATIVE`不得保存外部目标。
- `tasks/plan.md`和`tasks/todo.md`是历史材料，不生成、不更新、不用于实施判断。
- 每个任务完成前执行其定向测试；Feature完成前执行模块测试、前端类型检查、Flyway验证、事务集成测试和真实浏览器验收。
- 所有`git commit`必须先重新加载`C:/Users/user/.codex/skills/git-commit-general/SKILL.md`。

## File Structure

- `sql/migrations/V63__fproj001_v18_atomic_project_creation.sql`：只承载V1.8前向扩列、新表、约束和可证明的`TASK_NATIVE`回填。
- `pms-module-project/.../projectmanual/command/ManualProjectCreateCommand.java`：稳定创建命令，不让Controller直接拼装DO。
- `pms-module-project/.../projectmanual/command/ManualProjectCreateResult.java`：稳定创建结果和首次幂等响应快照。
- `pms-module-project/.../projectmanual/ProjectManualCreationApplicationService.java`：唯一正式创建事务入口。
- `pms-module-project/.../acceptance/application/ProjectDeliverableInitializationApplicationService.java`：PROJ可调用的ACC内部应用接口。
- `pms-module-project/.../acceptance/application/ProjectDeliverableInitializationApplicationServiceImpl.java`：ACC Owner写入及完整数量校验，事务传播为MANDATORY。
- `pms-module-project/.../projectmanual/TaskExecutionContractFactory.java`：校验并冻结WorkBinding、PermissionPolicy、CompletionRule和GateRef。
- `pms-module-project/.../projectmanual/ProjectCreationAuthorizationService.java`：创建与指派的数据范围校验，不把Controller注解当完整授权。
- `pms-module-project/.../projectmanual/ProjectCreationPlatformFactService.java`：同事务写`plt_idempotency_record`、`plt_operation_audit`、`plt_outbox_event`。
- `yudao-ui/.../src/api/pms/project/projects/index.ts`：revision级创建、必需Header和版本化指派契约。
- `yudao-ui/.../src/views/pms/project/projects/index.vue`：仅内存表单、新Key重试、revision预览和错误闭环。

---

### Task 1: 锁定V1.8差异清单与数据库前向契约

**Files:**
- Create: `sql/migrations/V63__fproj001_v18_atomic_project_creation.sql`
- Create: `scripts/tests/test_fproj001_v18_migration.py`
- Modify: `tasks/features/F-PROJ-001.md`

**Interfaces:**
- Consumes: Feature Spec第9节、ADR-0030、ADR-0032及当前V52/V57 Schema。
- Produces: `proj_project`三状态兼容字段、模板任务V1.8定义字段、`proj_project_task_execution_contract`、ACC Owner交付件表及平台事实表的可执行Schema。

- [x] **Step 1: 写失败的迁移契约测试**

```python
def test_v63_contains_v18_atomic_creation_contract():
    sql = MIGRATION.read_text(encoding="utf-8").lower()
    for token in (
        "lifecycle_status", "current_stage", "assignment_status",
        "proj_project_task_execution_contract", "work_binding_type_code",
        "permission_policy_ref", "completion_rule_type_code",
        "plt_idempotency_record", "plt_operation_audit", "plt_outbox_event",
    ):
        assert token in sql
    assert "alter table `proj_project`" in sql
    assert "create table" in sql
```

- [x] **Step 2: 运行测试确认因V63不存在而失败**

Run: `py -3.13 -m unittest scripts.tests.test_fproj001_v18_migration -v`

Expected: FAIL，明确报告V63迁移不存在或V1.8契约字段缺失。

- [x] **Step 3: 编写前向迁移**

迁移必须包含以下不可降级的核心形态，并从目标DDL复制完整列、约束和索引，不修改V52/V57：

```sql
ALTER TABLE `proj_project`
  ADD COLUMN `lifecycle_status` VARCHAR(32) NULL,
  ADD COLUMN `current_stage` VARCHAR(32) NULL,
  ADD COLUMN `assignment_status` VARCHAR(32) NULL;

CREATE TABLE `proj_project_task_execution_contract` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `project_task_id` BIGINT NOT NULL,
  `template_task_definition_id` BIGINT NULL,
  `work_binding_type_code` VARCHAR(32) NOT NULL,
  `permission_policy_ref` VARCHAR(255) NOT NULL,
  `completion_rule_type_code` VARCHAR(32) NOT NULL,
  `contract_version` INT NOT NULL,
  `effective_from` DATETIME(3) NOT NULL,
  `effective_to` DATETIME(3) NULL,
  `current_marker` TINYINT GENERATED ALWAYS AS (IF(`effective_to` IS NULL, 1, NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proj_task_contract_version` (`tenant_id`, `project_task_id`, `contract_version`),
  UNIQUE KEY `uk_proj_task_contract_current` (`tenant_id`, `project_task_id`, `current_marker`)
);
```

历史任务只允许按ADR-0030回填显式`TASK_NATIVE`版本1，不依据名称、菜单、URL或历史状态推断其他绑定。旧`status`不原地改义：仅S0～S6可确定性映射为`ACTIVE + current_stage`，`MAINT`等无V1.8等价语义的旧值保持待迁移，不臆造状态。交付件前向复制到`acc_project_deliverable`后，PROJ新创建路径停止写`proj_project_deliverable`。

- [x] **Step 4: 更新本地Feature任务跟踪**

将Technical Plan指向本文件；将Task 2标记为完成并登记V1.7差异矩阵、当时发现的规格校验哈希不一致和受管Feature旧引用。不得把任何AC标记为完成。

- [x] **Step 5: 验证迁移契约与MySQL执行**

Run: `py -3.13 -m unittest scripts.tests.test_fproj001_v18_migration -v`

Run: `py -3.13 scripts/validate_mysql_ddl_execution.py`

Expected: 定向契约测试PASS；MySQL验证不存在语法、重复索引或不可执行生成列。

### Task 2: 将模板任务定义升级为V1.8执行定义

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projecttemplate/ProjectTemplateTaskDefinitionDO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/TemplateDefinitionContent.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectmanual/ProjectTaskExecutionContractDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectTaskExecutionContractMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectmanual/TaskExecutionContractFactory.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/domain/projectmanual/TaskExecutionContractFactoryTest.java`
- Modify: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/domain/template/TemplatePublishValidatorTest.java`

**Interfaces:**
- Consumes: `TemplateDefinitionContent.TaskDef`和发布revision。
- Produces: `TaskExecutionContractFactory#create(Long, TaskDef, LocalDateTime)`，为每个任务生成且只生成一个当前契约。

- [x] **Step 1: 写绑定类型和规则完整性的失败测试**

```java
@Test
void taskNativeRejectsExternalTarget() {
    TaskDef task = validTaskNative();
    task.setTargetObjectKey("foreign-1");
    assertThrows(ServiceException.class, () -> factory.create(1L, task, NOW));
}

@Test
void executableTaskRequiresBindingPermissionAndCompletionRule() {
    TaskDef task = validTaskNative();
    task.setPermissionPolicyRef(null);
    assertThrows(ServiceException.class, () -> factory.create(1L, task, NOW));
}
```

- [x] **Step 2: 运行测试确认现有定义缺字段而失败**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TaskExecutionContractFactoryTest,TemplatePublishValidatorTest test`

- [x] **Step 3: 扩展定义并实现最小校验工厂**

```java
public ProjectTaskExecutionContractDO create(Long projectTaskId, TaskDef definition, LocalDateTime now) {
    bindingValidator.validate(definition);
    ProjectTaskExecutionContractDO contract = BeanUtils.toBean(definition, ProjectTaskExecutionContractDO.class);
    contract.setProjectTaskId(projectTaskId);
    contract.setContractVersion(1);
    contract.setEffectiveFrom(now);
    contract.setEffectiveTo(null);
    return contract;
}
```

类型注册表必须精确支持`TASK_NATIVE`、`BUSINESS_OBJECT`、`BUSINESS_COMPONENT`、`DYNAMIC_FORM`、`APPROVAL`、`COMPOSITE`，且不接受任意脚本、Repository名或前端路径。

- [x] **Step 4: 运行模板发布与契约工厂测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TaskExecutionContractFactoryTest,TemplatePublishValidatorTest test`

Expected: PASS，并覆盖缺绑定、非法目标、不可解析CompletionRule和失效GateRef。

### Task 3: 建立ACC Owner交付件同步接口

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptance/AccProjectDeliverableDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptance/AccProjectDeliverableMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance/application/ProjectDeliverableInitializationApplicationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptance/application/ProjectDeliverableInitializationApplicationServiceImpl.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/acceptance/application/ProjectDeliverableInitializationApplicationServiceImplTest.java`

**Interfaces:**
- Consumes: `InitializeProjectDeliverablesCommand(projectId, templateRevisionId, definitions)`。
- Produces: `DeliverableInitializationResult(expectedCount, insertedCount)`；数量不一致或写入异常必须抛出。

- [x] **Step 1: 写事务传播与少写失败测试**

```java
@Test
void implementationRequiresExistingTransaction() throws Exception {
    Transactional tx = implementationMethod().getAnnotation(Transactional.class);
    assertEquals(Propagation.MANDATORY, tx.propagation());
}

@Test
void partialInsertIsRejected() {
    when(mapper.insertBatch(anyCollection())).thenReturn(1);
    assertThrows(ServiceException.class, () -> service.initialize(commandWithTwoDeliverables()));
}
```

- [x] **Step 2: 运行测试确认接口不存在而失败**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectDeliverableInitializationApplicationServiceImplTest test`

- [x] **Step 3: 实现ACC内部应用接口**

```java
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command) {
    List<AccProjectDeliverableDO> rows = assembler.from(command);
    int inserted = mapper.insertBatch(rows);
    if (inserted != rows.size()) {
        throw exception(PROJECT_DELIVERABLE_INITIALIZATION_INCOMPLETE);
    }
    return new DeliverableInitializationResult(rows.size(), inserted);
}
```

本任务只建立ACC Owner接口与强制事务边界；旧PROJ实例载体的移除由Task 4原子编排接管，避免在调用方切换前破坏现有链路。

- [x] **Step 4: 运行ACC定向测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectDeliverableInitializationApplicationServiceImplTest test`

Expected: PASS；无事务调用、少写、重复码和Mapper异常均向调用方传播。

### Task 4: 重构正式创建为单一原子应用事务

> 执行顺序注记（2026-08-21）：Task 4核心项目事实已先改造；其`IDEMPOTENCY_SUCCESS / AUDIT / OUTBOX`失败点依赖Task 5平台事实服务，先实施Task 5后再回填Task 4全失败点验证，不作为当前阻断。

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ManualProjectCreateCommand.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ManualProjectCreateResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectmanual/TemplateInstantiator.java`
- Modify: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImplTest.java`

**Interfaces:**
- Consumes: `create(command, actor)`，其中command包含必需Idempotency-Key、规范化摘要、稳定主数据引用、`templateRevisionId`和候选水位。
- Produces: 首次成功或同键重放的`ManualProjectCreateResult`。

- [ ] **Step 1: 写原子失败参数化测试**

```java
@ParameterizedTest
@EnumSource(FailurePoint.class)
void everyFailurePointRollsBackAllFacts(FailurePoint point) {
    failureInjector.failAt(point);
    assertThrows(RuntimeException.class, () -> applicationService.create(command, actor));
    assertNoProjectFacts(command.idempotencyKey());
}
```

FailurePoint必须包含`STAGE`、`TASK`、`MILESTONE`、`GATE`、`CONTRACT`、`ACC_DELIVERABLE`、`IDEMPOTENCY_SUCCESS`、`AUDIT`、`OUTBOX`。

- [x] **Step 2: 写S0和状态分离失败测试**

```java
@Test
void rejectsTemplateWhoseActiveStageIsNotS0() {
    assertThrows(ServiceException.class, () -> service.create(commandUsingTemplateStartingAtS2(), actor));
}
```

- [x] **Step 3: 运行测试确认V1.7实现失败**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectManualCreationServiceImplTest test`

- [x] **Step 4: 实现事务编排并移除Controller外事务事实**

```java
@Transactional(rollbackFor = Exception.class)
public ManualProjectCreateResult create(ManualProjectCreateCommand command, Actor actor) {
    return idempotency.execute(command.scope(actor), command.requestDigest(), () -> {
        authorization.checkCreate(actor, command);
        FrozenTemplate frozen = templates.revalidateAndFreeze(command);
        ProjectMasterDO project = projects.insertActiveS0Unassigned(command, frozen);
        ProjectInstantiation facts = instantiator.instantiate(frozen, project.getId());
        projectFacts.insert(facts.withoutDeliverables());
        contracts.insertAll(contractFactory.createAll(facts.tasks(), frozen));
        DeliverableInitializationResult acc = deliverables.initialize(facts.deliverableCommand());
        ManualProjectCreateResult result = resultAssembler.assemble(project, facts, acc);
        platformFacts.appendSuccess(command, actor, result);
        return result;
    });
}
```

模板提交时必须按`templateRevisionId + candidateWatermark`重新验证，不得再次选择latest revision。阶段初始化只允许S0为ACTIVE，其余PENDING。

- [x] **Step 5: 运行创建服务测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectManualCreationServiceImplTest,TemplateInstantiatorTest test`

Expected: PASS，且旧测试中“最小sort非S0仍ACTIVE”和“PROJ直接写交付件”断言已被V1.8反例替换。

### Task 5: 平台幂等、审计和Outbox同事务化

> 执行方式调整（2026-08-21）：用户明确禁用测试驱动；本任务改为先实现、后补行为验证与回归，不再保留“先运行确认旧实现失败”的强制顺序。

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCreationPlatformFactService.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCreationPlatformFactServiceTest.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/IdempotencyRecordService.java`
- Delete: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/IdempotencyRecordServiceImpl.java`

**Interfaces:**
- Consumes: `IdempotencyScope(tenantId, route, actorId, key)`和规范化请求摘要。
- Produces: `NEW`、`REPLAY_COMPLETED`、`CONFLICT`、`IN_PROGRESS`判定以及事务内成功事实。

- [x] **Step 1: 补充同键判定和写入失败验证**

```java
@Test
void sameKeyDifferentDigestConflictsWithoutExecutingSupplier() { /* assert conflict and zero calls */ }

@Test
void auditOrOutboxFailurePropagates() { /* mapper throws; assert create transaction fails */ }
```

- [x] **Step 2: 旧服务差异沿用Task 2审计证据（失败优先步骤按用户指示取消）**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectCreationPlatformFactServiceTest test`

- [x] **Step 3: 实现平台事实写入**

成功幂等记录保存响应引用/快照；审计保存主体、租户、correlationId、Key摘要、创建原因摘要、revision及实例数量；Outbox保存唯一`ProjectCreated`事件。任何Mapper异常必须传播，禁止`catch/log/continue`。

- [x] **Step 4: 运行平台事实测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectCreationPlatformFactServiceTest test`

### Task 6: 收紧API、主数据和权限边界

> 执行注记（2026-08-22）：按用户指示禁用测试驱动，先实现再补事后测试。创建与指派已统一通过`ProjectCreationAuthorizationService`执行服务层功能权限校验，权限拒绝发生在幂等占用和业务写入之前；定向25项全部通过，项目模块非IT回归151项中148项通过、3项按`skipITs`跳过。普通成员和仅模板维护权限的功能权限边界已具备服务层拒绝能力；未授权办事处、未授权实施地点、跨租户主体以及客户/办事处/地点稳定ID和版本仍依赖尚未定义完整的权威主数据契约，因此本Task继续保持未完成。

> 阻断审计（2026-08-22）：剩余范围不是可由实现层自行补齐的普通缺陷。客户、办事处和实施地点的稳定身份、可比较版本、租户/组织/地点授权关系以及失效判定会改变API、权限和集成契约，必须先由规格仓库回写相关SDS、Open Question、追溯矩阵和Feature Spec，再锁定新`source.commit`并同步受管快照。当前NPDMS不得直接修改这些受管文件，也不得把实施地点等同于部门或继续接受名称/编码作为权威事实。仓库同时不存在下一个达到Feature Ready的独立Feature Spec，因此没有可绕过本阻断继续实施的后续Feature。推荐由平台主数据Owner明确办事处与实施地点的权威Owner、稳定ID/版本、查询/校验契约和数据范围规则；同步新规格基线后再恢复Task 6、Task 7和AC-FPROJ-007。

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterController.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectCreateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectAssignManagerReqVO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCreationAuthorizationService.java`
- Modify: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterControllerContractTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectCreationAuthorizationServiceTest.java`

**Interfaces:**
- Consumes: 必需`Idempotency-Key`；指派额外消费必需`If-Match`。
- Produces: 创建响应包含`ACTIVE / S0 / UNASSIGNED`、Project version、冻结revision、数量和详情链接。

- [ ] **Step 1: 写Header、revision和权限负向失败测试**

```java
assertRequiredHeader("createProject", "Idempotency-Key");
assertRequiredHeader("assignManager", "Idempotency-Key");
assertRequiredHeader("assignManager", "If-Match");
```

权限测试覆盖普通成员、仅模板维护权限、未授权办事处、未授权实施地点和跨租户主体。

- [ ] **Step 2: 运行Controller契约和权限测试确认失败**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectMasterControllerContractTest,ProjectCreationAuthorizationServiceTest test`

- [ ] **Step 3: 修改请求契约和Controller**

```java
public CommonResult<ProjectCreateRespVO> createProject(
        @RequestHeader("Idempotency-Key") @NotBlank String key,
        @Valid @RequestBody ProjectCreateReqVO request) {
    return success(applicationService.create(map(request, key), currentActor()));
}
```

请求增加`templateRevisionId`、`candidateWatermark`、客户/办事处/实施地点稳定ID及版本；移除仅凭名称/编码冒充权威主数据的路径。Controller不再查询或保存幂等记录。

- [ ] **Step 4: 实现服务端数据范围校验并运行测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectMasterControllerContractTest,ProjectCreationAuthorizationServiceTest test`

### Task 7: 服务经理确认增加幂等、版本和责任范围

> 执行注记（2026-08-21）：按用户指示禁用测试驱动，本Task测试均在实现后补充。`Idempotency-Key`、`If-Match`条件版本、功能权限、时态关系、审计与Outbox已实现并通过事后测试；office/location数据范围拒绝依赖Task 6已登记的权威主数据接口缺口，继续后置，Task 7不据此宣告完整完成。

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectAssignManagerReqVO.java`
- Modify: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImplTest.java`

**Interfaces:**
- Consumes: `AssignServiceManagerCommand(projectId, expectedVersion, roleCode, userId, levelCode, officeId, locationId, effectiveFrom, idempotencyKey)`。
- Produces: 新Project version、当前关系ID和仍按规则派生的assignment status。

- [ ] **Step 1: 写版本冲突、范围拒绝和UNASSIGNED测试**（版本冲突与UNASSIGNED已完成；范围拒绝后置）

```java
@Test
void serviceManagerOnlyDoesNotMarkProjectAssigned() {
    AssignManagerResult result = service.assign(commandAtVersion(0), actor);
    assertEquals("UNASSIGNED", result.assignmentStatus());
}
```

- [ ] **Step 2: 运行测试确认旧签名和无版本更新失败**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectManualCreationServiceImplTest test`

- [x] **Step 3: 实现条件更新、时态关系、审计和幂等**

Project版本更新必须使用`WHERE id=? AND version=?`；更新行数为0返回`VERSION_CONFLICT`。关闭旧区间、追加新区间、Project投影、幂等与审计同事务提交。

- [x] **Step 4: 运行指派测试**

Run: `mvn -pl pms-module-project -am -DskipITs -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ProjectManualCreationServiceImplTest test`

### Task 8: 改造Vue创建向导和指派交互

> 执行注记（2026-08-21）：按用户指示禁用测试驱动，生产代码完成后使用Node 24内置测试运行器补充事后测试，避免为单个纯状态测试引入新的前端测试框架依赖。revision候选、水位提交、页面内存态幂等键、If-Match指派和版本冲突重载已实现；主数据范围校验仍受Task 6阻断。全量`pnpm ts:check`被仓库既有auto-import声明缺失阻断，但目标文件筛查无错误，`build:local`通过。

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.spec.ts`

**Interfaces:**
- Consumes: revision级候选、预览、创建和版本化指派API。
- Produces: 仅当前页面内存表单；失败修正后新Key；刷新不可恢复；无本地持久化。

- [x] **Step 1: 写前端事后测试**

```ts
it('regenerates Idempotency-Key after a correctable failed request', async () => {
  api.createProject.mockRejectedValueOnce(validationError)
  await submitCreate()
  editForm()
  await submitCreate()
  expect(sentKeys[1]).not.toBe(sentKeys[0])
})

it('never persists the creation form', () => {
  expect(source).not.toMatch(/localStorage|sessionStorage|indexedDB/i)
})
```

- [ ] **Step 2: 运行前端测试确认现有同一Key行为失败**

Run: `pnpm exec vitest run src/views/pms/project/projects/index.spec.ts`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

- [x] **Step 3: 修改API类型和向导状态机**

候选行以`templateRevisionId`为选择值并保存`candidateWatermark`；提交失败保留reactive内存表单和逐项错误。仅对同一未修改请求的网络重试复用Key；任一输入或revision修改后生成新Key。

- [x] **Step 4: 运行定向测试、目标类型筛查与构建**

Run: `node --test src/views/pms/project/projects/index.spec.ts`

Run: `pnpm build:local`

Run: `pnpm exec vitest run src/views/pms/project/projects/index.spec.ts`

Run: `pnpm typecheck`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

Expected: PASS，且生产代码无`localStorage`、`sessionStorage`、`IndexedDB`草稿写入。

### Task 9: 验证同库事务、并发和故障注入

> 执行注记（2026-08-22）：已在当前工作树隔离的`npdms-50eb` Compose项目、
> MySQL 8.4.10和63个Flyway迁移上完成真实执行。九个FailurePoint的事务回滚、
> 同Key同摘要成功重放及同Key不同摘要冲突共11项测试全部通过；全量后端Reactor
> 19个模块成功，项目模块154项测试全部通过。证据见
> `output/f-proj-001-v18/database-evidence.md`。旧`npdms-t8-mysql-1`仅按授权停止，
> 未删除或复用其数据库与数据卷。

**Files:**
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationMySqlIntegrationTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationConcurrencyMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: Docker Compose MySQL/Flyway及完整创建应用服务。
- Produces: AC-FPROJ-002/004/006/008/010的数据库证据。

- [x] **Step 1: 写真实MySQL原子失败测试**

每个FailurePoint执行后查询Project、Stage、Task、Milestone、Gate、执行契约、ACC交付件、成功幂等、成功审计和ProjectCreated Outbox，全部断言为0。

- [x] **Step 2: 写相同Key并发测试**

使用两个并发连接提交同Key同摘要，断言一个Project、一个编码、一个成功幂等事实；同Key不同摘要断言一个成功和一个幂等冲突。

- [x] **Step 3: 运行集成测试**

Run: `mvn -pl pms-module-project -am -DskipITs=false -DfailIfNoTests=false -Dtest=ProjectManualCreationMySqlIntegrationTest,ProjectManualCreationConcurrencyMySqlIntegrationTest test`

Expected: PASS；不得以H2、Mock Mapper或HTTP 200替代。

### Task 10: 全量验证与真实浏览器验收

> 执行注记（2026-08-22）：不依赖运行环境的验证已执行。后端Reactor测试
> `144`项中`141 PASS / 3 MySQL IT按开关跳过`；仓库基线规则PASS。计划中的`pnpm typecheck`和`pnpm build`
> 不是当前`package.json`脚本，实际`pnpm ts:check`因全仓既存类型错误失败，但本Feature
> 相关路径无类型错误，`pnpm build:local`成功。检查中发现并修复子项目仍调用旧创建
> 签名的问题（提交`697c384`）：根项目保留候选水位校验，子项目继承父模板时允许水位
> 为空，并复用页面内存幂等键。上述结果不替代MySQL IT或浏览器验收。
> Task 9随后已在隔离MySQL环境完成：定向真实MySQL测试11项全部通过，全量后端
> Reactor 19个模块成功，项目模块154项测试全部通过。Task 10继续执行真实浏览器闭环。
> 按2026-08-22最新指示，`specs/001-project-delivery-platform/`仅作历史参考，
> 不再运行其规格基线校验或把历史哈希差异列为当前门禁。
> 真实浏览器验收已完成唯一默认、revision预览、人工选模、无模板、多候选、
> 陈旧候选失败保留、刷新清空、详情刷新、执行契约数量、服务经理指派及
> `If-Match`冲突重试。浏览器首次暴露单租户关闭租户拦截器时执行契约遗漏
> `tenant_id`，已显式传播命令租户并补充事后测试；修复后全量项目模块
> `154/154 PASS`。AC-FPROJ-007仍受权威主数据接口缺失阻断，不越权放行。

**Files:**
- Create: `output/f-proj-001-v18/browser-acceptance.md`
- Create: `output/f-proj-001-v18/database-evidence.md`
- Modify: `tasks/features/F-PROJ-001.md`

**Interfaces:**
- Consumes: 已启动的宿主机前后端和Docker基础设施。
- Produces: AC-FPROJ-001～010逐项证据；未通过项保持未完成。

- [x] **Step 1: 执行后端与仓库验证**

Run: `mvn -pl pms-module-project -am test`

Run: `py -3.13 scripts/validate_repository_baseline_rules.py`

不得修改历史规格目录来迎合实现；该目录也不再参与本轮实施与验收结论。

- [x] **Step 2: 执行前端验证**

Run: `pnpm typecheck`

Run: `pnpm build`

Workdir: `yudao-ui/yudao-ui-admin-vue3`

- [x] **Step 3: 启动并完成真实浏览器闭环**

依次验证：候选过滤、revision预览、创建成功、详情刷新、执行契约数量、服务经理确认、If-Match冲突、无模板/多默认/模板失效失败、失败后内存保留、刷新后表单消失、浏览器存储无草稿。

同时记录页面正文、控制台错误、失败请求、根节点内容、权限按钮、后端审计、Outbox及数据库持久化结果。

- [x] **Step 4: 更新Feature跟踪但不越权放行**

只有证据充分的AC才能勾选。实现自测完成不等于UAT、发布或治理门禁GO；历史规格目录不参与当前放行判断。

## Self-Review Result

- Spec coverage: Tasks 2/3/4覆盖AC-001～004，Tasks 5/6/7覆盖AC-005～008，Tasks 8～10覆盖AC-009/010及全链证据。
- Placeholder scan: 未发现禁用占位表达、跨任务省略描述或缺失验证命令。
- Type consistency: 创建入口统一使用`ManualProjectCreateCommand/Result`；ACC统一使用`InitializeProjectDeliverablesCommand/DeliverableInitializationResult`；指派统一使用`AssignServiceManagerCommand/Result`。
- Boundary check: 未修改受管Feature Spec，未编辑V57，未创建草稿模型，未把ACC Repository暴露给PROJ。
