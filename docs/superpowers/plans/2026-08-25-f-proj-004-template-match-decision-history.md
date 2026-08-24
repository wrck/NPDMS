# F-PROJ-004 模板匹配决策历史与属性影响识别 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本工程链在当前会话内联执行，不启用子代理。

**Goal:** 按PRD V1.8完成模板匹配前四属性判定、不可变匹配决策历史，以及创建后属性变化的只读影响识别。

**Architecture:** PROJ继续复用`proj_project`四个当前值列和既有`TemplateMatcher`，新增单一append-only事实`proj_project_template_match_history`。首次创建在现有平台幂等事务内同时写Project、决策历史、模板冻结和实例化；创建后通过独立受控命令更新当前值并追加影响评估历史，绝不替换冻结模板或实例事实。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `specs/features/F-PROJ-004-project-business-attribute-classification.md`

## Global Constraints

- 锁定规格提交为`79125ceac092f7b586c66bbd251e9eb93ba894a2`，本地受管快照由NPDMS提交`6727d01`同步；不得直接修改受管规格文件。
- `specs/001-project-delivery-platform/`只作历史参考，不参与实施校验。
- V1.7字段、匹配器、页面和测试只作复用审计证据；必须逐项检查、改造和重新验证，不能据已有实现判定完成。
- 用户已禁用测试驱动顺序；不先制造失败测试，但每个Task结束前必须补齐并运行风险匹配的自动化验证。
- 只复用`proj_project.signing_method/project_category/implementation_mode/major_project_level`，不得增加同义当前值字段。
- 唯一新增业务事实表是`proj_project_template_match_history`；不得新增属性历史表、分类状态轴、分类案例、独立影响表、重实例化接口或CHG Outbox。
- 创建后属性变化只更新四属性当前值并追加历史；`lifecycle_template_id/revision_no`及阶段、任务、里程碑、交付件、门禁事实保持不变。
- `operation_id`稳定且唯一；`trace_id/audit_log_id`仅可选关联，异步系统操作日志缺失不得影响业务事务成功。
- 新查询遵循`docs/coding/database-query-interface.md`；分页查询只接收场景化Query对象，排序使用白名单，空ProjectTreeScope返回空结果。
- 已执行Flyway V1～V79不修改；本Feature从V80开始只做前向、幂等迁移，不自动映射或静默清空存量`MAIN/SUB`及手工项目非空重大级别。
- UI复用现有项目创建和详情页面，优先使用Yudao组件，其次Element Plus；支持320/768/1024/1440视口、主题变量和无页面级横向溢出。
- 每个Task验证通过后创建独立本地提交，不推送；当前只推进Implementation、Automated Verification与Code Review，不生成Deployment、SIT、UAT或Release材料。

## 存量实现审计

| 资产 | 分类 | V1.8处置 |
|---|---|---|
| `proj_project`四属性列 | `REUSE_WITH_GUARDS` | 保留列名；手工创建拒绝非空重大级别，通用PUT不得修改四属性 |
| `TemplateMatcher`和`ProjectTemplateService.matchPreview` | `REUSE_WITH_VERSION_EVIDENCE` | 只保留一套算法；决策历史冻结候选摘要、水位和matcher版本 |
| `ProjectManualCreationServiceImpl.selectTemplate` | `ADAPT` | 返回完整匹配决策，不再只返回模板ID/revision/loadMethod |
| `PlatformCommandExecutionApi` | `REUSE` | 继续承载创建与调整命令的事务幂等；历史写入同一事务 |
| `system_operate_log` | `OPTIONAL_LINK_ONLY` | 不作为权威追溯，不阻断业务提交，不新建通用属性历史表 |
| `pms_project_category`中的`MAIN/SUB` | `RETIRE_AS_CANDIDATE` | 前向迁移停用旧候选，保留原记录证据；当前合法值仅`GENERAL/ENGINEERING` |
| 项目创建/详情页面 | `REUSE_WITH_ADAPTATION` | 创建隐藏重大级别并补原因校验；详情增加属性调整和历史分页 |

---

### Task 1: 建立历史事实表、值域纠偏和持久化模型

**Files:**
- Create: `sql/migrations/V80__fproj004_template_match_history.sql`
- Create: `sql/migrations/V81__fproj004_template_match_seed_and_permission.sql`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/projectattribute/ProjectTemplateMatchHistoryDO.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectattribute/ProjectTemplateMatchHistoryMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectattribute/query/ProjectTemplateMatchHistoryPageQuery.java`
- Create: `scripts/tests/test_fproj004_v18_migration.py`
- Create: `tasks/features/F-PROJ-004.md`

**Interfaces:**
- Consumes: 物理契约`ProjectTemplateMatchHistory.physicalFields`和既有`proj_project`四属性。
- Produces:

```java
public interface ProjectTemplateMatchHistoryMapper extends BaseMapperX<ProjectTemplateMatchHistoryDO> {
    default ProjectTemplateMatchHistoryDO selectByOperationId(Long tenantId, String operationId);
    default ProjectTemplateMatchHistoryDO selectByIdempotencyKey(Long tenantId, Long projectId, String idempotencyKey);
    PageResult<ProjectTemplateMatchHistoryDO> selectPage(ProjectTemplateMatchHistoryPageQuery query);
}

public record ProjectTemplateMatchHistoryPageQuery(
        Long tenantId, Long projectId, PageParam pageParam,
        String triggerType, String matchResult, String impactResult,
        LocalDateTime occurredAtBegin, LocalDateTime occurredAtEnd,
        String orderBy, Boolean ascending) {}
```

- [x] **Step 1: 新增V80历史表与约束**

建立契约列、通用审计列、`uk(tenant_id,operation_id)`、`uk(tenant_id,project_id,idempotency_key)`、项目时间分页索引；表注释明确永久保留和只插入。数据库约束覆盖trigger/result矩阵中可稳定表达的固定值，复杂条件由领域校验承担。

- [x] **Step 2: 新增V81值域、权限和组合种子**

停用`pms_project_category`的`MAIN/SUB`候选，幂等保证`GENERAL/ENGINEERING`启用；增加`pms:project:classify`按钮权限。种子覆盖唯一命中、部分限定优先级让位、无匹配、多匹配和停用模板不参与，但不改写任何存量项目错误值。

- [x] **Step 3: 实现DO、Mapper和类型安全分页**

DO逐列映射物理契约；简单单表分页使用`LambdaQueryWrapperX`固定`project_id/tenant_id`，排序通过Java字段白名单映射方法引用，禁止`${}`和`.last(...)`。

- [x] **Step 4: 补迁移契约测试并验证**

Run: `mvn.cmd -pl pms-module-project -am -DskipTests compile`

Run: `& '<bundled-python>' -m unittest scripts.tests.test_fproj004_v18_migration`

Expected: 编译及迁移契约测试PASS；测试证明没有`proj_project_business_attribute_history`或其他禁建表。

- [x] **Step 5: 更新Task状态并提交**

提交信息：`feat(project): 建立模板匹配决策历史事实`

---

### Task 2: 实现统一属性判定、匹配决策和历史构造

**Files:**
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectattribute/ProjectAttributeSnapshot.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectattribute/TemplateMatchDecision.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectattribute/TemplateMatchDecisionRules.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/ProjectAttributeResolutionService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/ProjectTemplateMatchHistoryService.java`
- Create tests under: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/domain/projectattribute/`
- Create tests under: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/`

**Interfaces:**
- Consumes: `ProjectTemplateService.matchPreview(signingMethod, projectCategory, implementationMode, majorProjectLevel)`。
- Produces:

```java
public record ProjectAttributeSnapshot(
        String signingMethod, String projectCategory,
        String implementationMode, String majorProjectLevel) {}

public record TemplateMatchDecision(
        String matchResult, String candidateDigest, String matcherVersion,
        String decisionMode, Long matchedTemplateId,
        Long matchedTemplateRevisionId, Integer matchedTemplateRevisionNo) {}

public interface ProjectAttributeResolutionService {
    TemplateMatchDecision resolveInitial(ProjectAttributeSnapshot attributes,
                                         Long selectedRevisionId,
                                         String candidateWatermark);
    TemplateMatchDecision evaluateImpact(ProjectAttributeSnapshot attributes);
}

public interface ProjectTemplateMatchHistoryService {
    ProjectTemplateMatchHistoryDO appendInitial(InitialMatchHistoryCommand command);
    ProjectTemplateMatchHistoryDO appendImpact(ImpactMatchHistoryCommand command);
}
```

- [x] **Step 1: 实现四属性快照和值域守卫**

手工来源只接受非空签约方式、`GENERAL/ENGINEERING`项目类别、非空实施方式和SQL NULL重大级别；原因统一trim后校验非空。快照JSON固定`schemaVersion=1`和四个稳定字段名。

- [x] **Step 2: 适配现有TemplateMatcher产生确定性决策**

唯一候选未显式选择返回`AUTO_UNIQUE`；任何显式合法候选返回`EXPLICIT_SELECTION`；无匹配和未选择的多匹配抛既有业务异常。候选摘要对排序后的候选稳定字段做SHA-256，matcher版本使用代码常量并写入历史。

- [x] **Step 3: 实现INITIAL_CREATE与IMPACT_EVALUATION历史构造**

按trigger/result矩阵校验所有必填、必空和固定值；`appendInitial/appendImpact`只能调用Mapper.insert，不暴露update/delete方法；operationId、reason、operatorId和idempotency证据必填。

- [x] **Step 4: 运行领域与服务测试**

Run: `mvn.cmd -pl pms-module-project -Dtest=TemplateMatchDecisionRulesTest,ProjectAttributeResolutionServiceTest,ProjectTemplateMatchHistoryServiceTest test`

Expected: 三类matchResult、两种首次决策、三种trigger及非法矩阵全部覆盖并PASS。

- [x] **Step 5: 更新Task状态并提交**

提交信息：`feat(project): 实现属性判定与匹配历史构造`

---

### Task 3: 将首次匹配历史纳入项目创建原子事务

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectCreateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectCreateRespVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ManualProjectCreateCommand.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/command/ManualProjectCreateResult.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationApplicationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterController.java`
- Modify tests: `ProjectManualCreationApplicationServiceTest.java`, `ProjectManualCreationServiceImplTest.java`, `ProjectManualCreationMySqlIntegrationTest.java`, `ProjectManualCreationConcurrencyMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2的`resolveInitial`和`appendInitial`。
- Produces: `ManualProjectCreateResult`新增`matchResult`、`decisionMode`和`matchOperationId`。

- [x] **Step 1: 在事务前验证创建原因和手工重大级别**

Controller保留字段用于服务端负向验证，但前端不提交；Application Service在进入`PlatformCommandExecutionApi.execute`前拒绝null/空白`creationReason`以及任何非空`majorProjectLevel`。

- [x] **Step 2: 用统一判定结果替换只返回模板ID的选择逻辑**

根项目调用`resolveInitial`并把已选revision传给实例化；子项目继承冻结模板的现有路径不调用匹配器，也不得伪造PM-07匹配决策。Task与验收材料明确本Feature INITIAL_CREATE闭环针对执行四属性模板匹配的正式根项目创建路径。

- [x] **Step 3: 在同一事务追加INITIAL_CREATE历史**

Project取得稳定ID后、事务返回前写历史；历史使用Project最终四属性、冻结revision、创建幂等键/摘要、认证用户ID、creationReason和actor correlationId。任何历史插入、实例化或站点绑定失败必须整体回滚。

- [x] **Step 4: 扩展创建响应与原子性验证**

验证AUTO_UNIQUE、合法EXPLICIT_SELECTION、无匹配、多匹配未选择、非空重大级别、历史插入失败回滚、实例化失败回滚、幂等重放和并发唯一性。

Run: `mvn.cmd -pl pms-module-project -Dtest=ProjectManualCreationApplicationServiceTest,ProjectManualCreationServiceImplTest,ProjectManualCreationMySqlIntegrationTest,ProjectManualCreationConcurrencyMySqlIntegrationTest test`

- [x] **Step 5: 更新Task状态并提交**

提交信息：`feat(project): 原子记录首次模板匹配决策`

---

### Task 4: 实现创建后属性修正、只读影响评估和历史查询API

**Files:**
- Create command records under: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/command/`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/ProjectAttributeClassificationApplicationService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/ProjectAttributeSourceCorrectionService.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectattribute/ProjectTemplateMatchHistoryQueryService.java`
- Create request/response VOs under: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectMasterController.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/vo/ProjectUpdateReqVO.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/projectmanual/ProjectMasterMapper.java`
- Modify: `pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java`
- Create focused service/controller/MySQL tests.

**Interfaces:**
- Produces:

```java
public record ManualProjectAttributeAdjustmentCommand(
        Long projectId, Integer expectedVersion,
        String signingMethod, String projectCategory, String implementationMode,
        String adjustmentReason, String idempotencyKey, String requestDigest) {}

public record ProjectAttributeSourceCorrectionCommand(
        Long projectId, Integer expectedVersion,
        String signingMethod, String implementationMode, String majorProjectLevel,
        String sourceOwner, String sourceSystem, String sourceKey,
        String sourceEventId, String sourceVersion, LocalDateTime sourceOccurredAt,
        String sourceValueDigest, String mappingVersion, String correctionReason,
        String idempotencyKey, String requestDigest, String serviceIdentity) {}
```

- [x] **Step 1: 封闭通用PUT的四属性写入口**

`ProjectUpdateReqVO`不接受四属性；Service即使收到DO载荷也从当前值覆盖四属性，确保只有本Task两个命令可修改。

- [x] **Step 2: 实现人工调整命令**

校验`pms:project:classify`、ProjectTreeScope、If-Match、可维护维度、非空原因和幂等；事务内锁定Project当前版本、保存before/after快照、更新既有列、只读评估并追加`MANUAL_ADJUSTMENT`历史。

- [x] **Step 3: 实现受信任来源修正命令**

仅接受已定位projectId和CRM Owner维度；把serviceIdentity解析为已注册稳定服务主体ID，拒绝无法解析的身份；来源版本、键、事件、时间、摘要、映射版本和原因全部必填。不得实现来源查找、重试或对账。

- [x] **Step 4: 实现历史分页与不可变守卫**

`GET /projects/{id}/template-match-history`先校验VIEW scope，再以场景化Query分页；`POST /projects/{id}/actions/classify`使用Idempotency-Key与If-Match。创建后UNIQUE/NO_MATCH/MULTIPLE_MATCHES只写相应impactResult，冻结模板及实例表行数和内容不变。

- [x] **Step 5: 运行权限、幂等、并发和MySQL测试**

Run: `mvn.cmd -pl pms-module-project -Dtest=ProjectAttributeClassificationApplicationServiceTest,ProjectAttributeSourceCorrectionServiceTest,ProjectTemplateMatchHistoryQueryServiceTest,ProjectAttributeCorrectionMySqlIntegrationTest,ProjectMasterControllerContractTest test`

Expected: 跨租户、越权、业务用户写重大级别、版本冲突、幂等冲突和重复进行中均无有效副作用。

- [x] **Step 6: 更新Task状态并提交**

提交信息：`feat(project): 增加属性修正与匹配影响识别`

---

### Task 5: 改造创建与详情界面

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectAttributePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectTemplateMatchHistoryPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Add Vitest specs beside the two new components and update `projects/index.spec.ts`.

**Interfaces:**
- Consumes: Task 3创建响应和Task 4的classify/history API。
- Produces: 无独立分类工作台；在现有详情中展示四属性、冻结模板与匹配历史。

- [x] **Step 1: 修正创建表单**

重大级别只显示“不适用”说明且不进入提交载荷；项目类别仅`GENERAL/ENGINEERING`；creationReason去空白校验；创建成功显示matchResult、matchDecisionMode和operationId摘要。

- [x] **Step 2: 实现响应式属性调整面板**

使用`ElDescriptions`、`ElDrawer`、`ElForm`和权限指令；项目经理/服务经理只读，具备`pms:project:classify`时显示调整入口；提交携带If-Match和稳定幂等键。

- [x] **Step 3: 实现响应式匹配历史分页**

768及以上视口使用`ElTable`，320窄屏使用卡片化描述列表；筛选triggerType/matchResult/impactResult/time，展示前后快照、候选结果、模板修订、影响、原因、操作者、operationId和时间。

- [x] **Step 4: 运行前端验证**

Run from `yudao-ui/yudao-ui-admin-vue3`: `node --test src/views/pms/project/projects/index.spec.ts`

Run: `pnpm exec vitest run src/views/pms/project/project-master-detail/components/ProjectAttributePanel.spec.ts src/views/pms/project/project-master-detail/components/ProjectTemplateMatchHistoryPanel.spec.ts`

Run: `pnpm exec vue-tsc --noEmit`

- [x] **Step 5: 更新Task状态并提交**

提交信息：`feat(ui): 展示项目属性与模板匹配历史`

---

### Task 6: 完成全链验证、评审与Implementation Done证据

**Files:**
- Create: `docs/acceptance/F-PROJ-004-template-match-decision-history.md`
- Modify: `tasks/features/F-PROJ-004.md`
- Modify after implementation evidence exists: Feature实施状态只能先回写规格仓库，再通过受管同步进入NPDMS。

- [x] **Step 1: 执行后端和迁移全量验证**

Run: `mvn.cmd -pl pms-module-project -am test`

Run: `docker compose run --rm flyway migrate`

Run: `docker compose run --rm flyway validate`

在隔离MySQL验证V1～V82、错误存量值未被自动改写、历史唯一约束、append-only应用边界和回滚原子性。

- [x] **Step 2: 执行前端构建与回归**

Run from `yudao-ui/yudao-ui-admin-vue3`: 对5个`node:test`契约文件执行`node --test`，对3个组件规格执行`pnpm exec vitest run <spec files>`；两类运行器不得混跑。

Run: `pnpm build:prod`

- [x] **Step 3: 使用真实浏览器完成业务闭环**

优先使用Codex内置浏览器；若内置交接不可用再使用已获许可的外部浏览器。验证唯一自动命中、合法显式选择、无/多匹配拒绝、历史查询、人工调整、越权拒绝、刷新保持，以及320/768/1024/1440无页面级溢出和主题一致。

- [x] **Step 4: 完成代码评审和边界扫描**

确认无同义四属性列、无属性历史表、无分类状态/案例/影响表、无重新实例化入口、无CHG事件；确认模块没有跨上下文DO/Mapper/业务表访问。

- [x] **Step 5: 形成验收证据并关闭Task**

验收文档逐项映射`AC-FPROJ004-001`～`012`到测试、MySQL事实、浏览器步骤和提交。只把PM-07的PROJ子切片标为Implementation Done；INT、CHG、PM-08和E2E继续保持未完成。

- [x] **Step 6: 提交实施闭环材料**

提交信息：`docs(feature): 完成 F-PROJ-004 实施闭环`

## Self-Review

- Spec coverage：12项AC分别落在Tasks 1～6；单表、四属性复用、首次事务、创建后影响识别、权限、幂等、MySQL和真实浏览器均有明确任务。
- Forbidden scope：计划不创建分类状态轴、分类案例、独立影响表、属性历史表、重实例化接口或CHG Outbox。
- Type consistency：创建使用`creationReason`兼容现有VO；历史关联使用`operationId`必填，`traceId/auditLogId`可选；创建后评估的`decisionMode`为空。
- Execution order：先物理载体，再领域决策，再创建原子接入，再创建后命令，最后UI与闭环；每个Task可独立评审和提交。
