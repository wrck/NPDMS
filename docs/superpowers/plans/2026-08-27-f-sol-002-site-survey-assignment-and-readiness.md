# F-SOL-002 工勘分工信息采集与实施就绪 Implementation Plan

> **执行要求：** 使用 `executing-plans` 按Task顺序实施；用户已明确禁用TDD，因此每个Task先完成需求范围内的正向实现，再执行与风险相称的自动化、真实MySQL或浏览器验证。每个Task独立复审并按情况本地提交，不推送。

**Goal:** 在SOL建立从项目模板冻结PRE-02工勘项，到逐项指派、填写、证据、确认、来源、豁免及不可变实施就绪快照的完整闭环，并向后续S4提供可锁定重验的公共事实。

**Architecture:** `pms-module-engineering`持有六张`sol_*`表、命令、查询、文件策略与页面；新`pms-module-engineering-api`只承载已有明确IMP消费方的就绪API及来源Provider SPI。PROJ继续持有ProjectTask ExecutionContract，并通过窄公共API暴露冻结PRE-02绑定事实；PLT持有文件身份，OA/INT-05持有来源流程。所有跨Context交互为同步命令或查询，不新增事件。

**Tech Stack:** Java 25、Spring Boot、MyBatis/MyBatis-Plus、MySQL 8.4/Flyway、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Specification:** `specs/features/F-SOL-002-site-survey-assignment-and-readiness.md`、`specs/features/F-SOL-002-physical-contract.json`；规格提交`e9e3122b33dbc34179d89326f5caa7972365f074`；NPDMS同步提交`b558a28`；Feature Ready裁决`NPDMS-FSOL002-FEATURE-READY-20260827-01-R2`。

## Global Constraints

- 受管规格快照只由同步工具维护；实施期间不直接修改`specs/**`、`docs/specification-baseline/manifest.json`或已执行的V1～V95迁移。
- `specs/001-project-delivery-platform/`只作历史证据，不作为实施校验门禁；不使用历史项目记忆补全需求。
- 当前工作树内完成，不创建第二工作树，不带入其他工作树的Feature、数据库、端口或计划参数；不修改用户自有`AGENTS.md`。
- 不修改`yudao-framework/**`和`yudao-module-bpm/**`。单租户/多租户继续复用当前配置感知受信上下文模式。
- 新增查询先遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外只接收单一场景Query；复杂查询和锁定读进入Mapper XML；禁止长位置参数、`Map`、SQL注解、`${}`和`.last(...)`；空集合不得扩大范围。
- SOL只使用`PROJECT_VIEW/PROJECT_MANAGE`；命令锁序保持PROJ范围/绑定事实→SOL准备事实→PLT精确文件→来源Provider。
- READY只来自当前不可变快照。SOL输入命令可直接失效为NOT_READY；inspect/revalidate纯只读；只有显式evaluate追加快照。
- 旧`pms_eng_site_survey`只作字段、地点维护和界面结构复用证据，不作当前真值、不双写、不证明已实现。
- 前端支持320/768/1024/1440，优先复用Yudao组件，其次Element Plus；使用主题变量，不堆叠内联样式。
- 每个Task实施前只核对一次可复用旧逻辑；满足Owner、状态、权限、事务和API时拷贝收窄，否则只复用局部纯逻辑。先完成主线，非阻断分支和异常验证后置到该Task验证阶段。
- 每个Task完成实现、验证和自审后送独立Implementation Done复审；GO后回写PASS并按情况本地提交。不得把自测当作独立GO。

## Current Implementation Audit

1. `pms-module-engineering`已有V1.7 `SiteSurveyDO/Mapper/Service/Controller`和旧页面，单表状态、通用CRUD、文件URL式字段及旧写权限均不满足V1.8；`EngineeringLocationFactService`及AST地点维护可继续复用。
2. 当前迁移最高为V95；V90～V95已实现F-SOL-001/F-PLT-001，FileArtifact公共`inspect/lockAndRevalidate`可直接消费，不再存在PLT-02上游阻断。
3. `proj_project_task_execution_contract`已冻结target四元组、`binding_parameter_snapshot`、contractVersion及当前物理键；当前缺少PRE-02精确公共查询与发布校验，不需要新WorkBinding表。
4. 项目模板保存/发布已能持久化`TemplateDefinitionContent.TaskDef`及绑定JSON；应在既有发布校验中增加PRE-02封闭Schema，不重做模板管理。
5. `ProjectScopeApi`、`ProjectParticipantFactApi`、平台命令幂等、`plt_operation_audit`和FileArtifact策略Provider均已有可复用模式。
6. 当前没有`pms-module-engineering-api`。后续IMP S4已有稳定消费契约，因此本Feature可建立窄API模块；不得把SOL Service/Mapper暴露给消费者。
7. INT-05尚未实施。生产环境无来源Provider时，OA必需项保持阻断；无OA项和合法豁免路径可完成。本Feature不等待、不伪造OA成功，也不宣称INT-05完成。

---

### Task 1: 建立PRE-02六表、字典权限与Feature工作单

**Files:**
- Create: `sql/migrations/V96__fsol002_preparation_readiness.sql`
- Create: `sql/migrations/V97__fsol002_preparation_seed.sql`
- Create: `tasks/features/F-SOL-002.md`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/enums/ErrorCodeConstants.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/preparation/PreparationMigrationContractTest.java`

**Interfaces:** Consumes锁定物理契约；produces六表、六类项字典、由既有`infra_config`承载的固定表单目录、模板示例、四项功能权限和稳定错误码。

- [ ] **Step 1: 创建六张前向表**

V96按物理契约创建`sol_preparation`、`sol_preparation_item`、`sol_dynamic_form_instance`、`sol_preparation_source_reference`、`sol_preparation_item_waiver`、`sol_preparation_readiness_snapshot`。SOL内部引用全部使用`tenant_id`复合外键；project/user/FileArtifact/OA来源不建跨Context外键。`current_marker`为历史可空当前标记，快照无update/delete入口。

- [ ] **Step 2: 创建确定性初始化数据**

V97幂等写六类批准项编码`POWER/NETWORK_PORT/FIBER/CABINET/NETWORK_CABLE/OPTICAL_MODULE`、四项权限，并在既有`infra_config`唯一写入稳定键`pms.sol.preparation.site-survey.form-catalog.v1`。该配置是V1固定表单目录的唯一权威载体，且必须适配既有`infra_config.value VARCHAR(500)`：根字段仅允许`schemaVersion=1/catalogCode=PRE_02_SITE_SURVEY/catalogVersion=1/commonFields/forms`；`commonFields`只保存一次V1通用现场情况字段，field仅允许`fieldCode/fieldType/required/maxLength/options/sortOrder`，`fieldType`封闭为`TEXT/NUMBER/BOOLEAN/SINGLE_SELECT/MULTI_SELECT`；每个form仅允许`formCode/formVersion=1`并复用同一`commonFields`。无脚本、表达式或运行时发布入口。`forms`必须精确覆盖六类基准项且`formCode`唯一；V1扩展项只能引用目录中已存在的固定form，不可动态扩Schema。只为现有seed-owned DRAFT模板定义插入`PRE_02_SITE_SURVEY`的BUSINESS_OBJECT示例配置，覆盖必需证据、无来源、OA来源、可豁免和停用项组合；不修改PUBLISHED模板、不自动授权角色、不臆造CRM值。

- [ ] **Step 3: 建立工作单和错误码**

错误码覆盖绑定缺失/歧义、固定Schema、状态、版本、负责人、文件、来源、豁免和就绪冲突。`tasks/features/F-SOL-002.md`只记录锁定规格、计划Gate和Task 1～10，不复制历史计划。

- [ ] **Step 4: 实施后验证并提交**

空库V1→V97验证六表、唯一键、复合外键、current唯一、快照不可变、固定目录配置键唯一且JSON Schema封闭、种子幂等及V1～V95未变；运行迁移契约、模块编译和`git diff --check`。

Expected: 迁移与种子契约PASS。提交：`feat(engineering): 建立工勘准备物理基础`

---

### Task 2: 提供PROJ冻结WorkBinding公共事实

**Files:**
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/dto/ProjectWorkBindingFactQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/dto/ProjectWorkBindingFactRevalidationQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/dto/ProjectWorkBindingFact.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectWorkBindingFactMapper.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectWorkBindingFactLookupQuery.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/query/ProjectWorkBindingFactLockQuery.java`
- Create: `pms-module-project/src/main/resources/mapper/taskworkbench/ProjectWorkBindingFactMapper.xml`
- Modify: `pms-module-project/pom.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/template/TemplatePublishValidator.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/workbinding/ProjectWorkBindingFactApiImplTest.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/dal/mysql/taskworkbench/ProjectWorkBindingFactMapperTest.java`

**Interfaces:** Consumes既有`ConfigApi.getConfigValueByKey`读取V1固定表单目录；produces `inspect(ProjectWorkBindingFactQuery)` and `lockAndRevalidate(ProjectWorkBindingFactRevalidationQuery)` over existing ProjectTask ExecutionContract.

- [ ] **Step 1: 固化模板生产映射**

发布校验只对目标四元组`BUSINESS_OBJECT/SOL/SITE_SURVEY_PREPARATION/PRE_02_SITE_SURVEY`解析封闭`schemaVersion=1`配置，并通过`ConfigApi.getConfigValueByKey("pms.sol.preparation.site-survey.form-catalog.v1")`读取唯一目录；每个适用项的`formCode/formVersion`必须精确命中目录中的V1定义，同时校验六类/批准扩展项唯一、证据/来源/豁免策略和审批角色。目录缺失、非法JSON、版本不为1、表单未命中、任意脚本、未知键或重复项均拒绝发布；其他绑定类型沿用既有校验，不新增模板表或通用设计器。

- [ ] **Step 2: 实现精确公开查询**

inspect按受信tenant+projectId+目标四元组+current_marker联结ProjectTask与ExecutionContract，要求恰一条；返回project/task/contract/template definition标识与版本及解析后快照。空、多记录、非当前、越租户或非法JSON失败关闭。

- [ ] **Step 3: 实现锁定重验**

revalidate按Project→ProjectTask→`tenant_id+project_task_id+current_marker`锁定ExecutionContract，核验请求中的projectTaskId、executionContractId、expectedProjectTaskVersion、expectedContractVersion、expectedProjectVersion及目标四元组；锁保持到调用方事务提交。

- [ ] **Step 4: 实施后验证并提交**

覆盖模板发布合法/非法配置、项目创建冻结、精确单条、0/多记录、TASK_NATIVE排除、版本变化、跨租户及锁定当前读。不得修改ProjectTask业务状态或新建绑定表。

Expected: PROJ公共契约与模板冻结链PASS。提交：`feat(project): 暴露工勘绑定冻结事实`

---

### Task 3: 实现SOL六表持久化原语与固定表单规则

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/preparation/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/query/*.java`
- Create: `pms-module-engineering/src/main/resources/mapper/preparation/*.xml`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/preparation/PreparationStateRules.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/preparation/FixedSurveyFormRules.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/preparation/FixedSurveyFormCatalogProvider.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/preparation/FixedSurveyFormCatalog.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/domain/preparation/PreparationRulesTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/PreparationMapperContractTest.java`

**Interfaces:** Produces显式insert、精确查询、稳定分页、FOR UPDATE当前读、版本CAS、current切换和不可变快照追加原语。

- [ ] **Step 1: 建立DO与封闭Mapper接口**

六个Mapper不继承通用CRUD；只暴露当前准备查询、item/form/source/waiver场景查询、锁定读、字段存在性更新、current切换、input/readiness版本CAS和快照追加。历史版本、冻结表单和快照无通用update/delete入口。

- [ ] **Step 2: 实现固定表单与状态纯规则**

`FixedSurveyFormCatalogProvider`只通过既有`ConfigApi`和稳定键`pms.sol.preparation.site-survey.form-catalog.v1`读取Task 1目录，按Task 1封闭Schema解析并返回精确`formCode+formVersion`及唯一`commonFields`；缺失、非法、未知字段类型、重复字段或版本不匹配均失败关闭。实例化把form身份与`commonFields`组合为完整Schema并冻结到`sol_dynamic_form_instance.schema_snapshot`，运行期校验只使用该冻结快照，配置后续变化不改写既有实例。固定Schema仅接受批准字段类型、必填/枚举/长度规则和排序稳定value snapshot，不执行表达式或脚本。Preparation×Item状态规则精确实现DRAFT/PENDING_CONFIRMATION/CONFIRMED/RETURNED及item适用性/确认分轴。

- [ ] **Step 3: 实现稳定分页和当前约束**

items按`sort_order,item_code,id`，waiver按`waiver_no,id`，snapshot按`snapshot_no,id`稳定分页；空权限/空项目集合直接空结果。current切换在同一事务先清旧marker再插下一DRAFT，CAS失败整体回滚。

- [ ] **Step 4: 实施后验证并提交**

覆盖Mapper方法集合、租户条件、冻结无写入口、状态合法迁移、分页、current唯一、CAS、快照只增，以及固定目录合法读取、缺失/非法/版本不匹配失败关闭和实例化后配置变化不改写冻结Schema。运行聚焦测试和模块编译。

Expected: 六表原语与规则PASS。提交：`feat(engineering): 持久化工勘准备事实`

---

### Task 4: 实现模板初始化、当前准备查询与历史投影

**Files:**
- Create: `pms-module-engineering-api/pom.xml`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/preparation/PreparationInitializationApi.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/preparation/dto/PreparationInitializationCommand.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/preparation/dto/PreparationInitializationResult.java`
- Modify: `pom.xml`
- Modify: `pms-module-project/pom.xml`
- Modify: `pms-module-engineering/pom.xml`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationInitializationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/preparation/PreparationInitializationApiImpl.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationQueryService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/*.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationServiceImpl.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationInitializationServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationControllerTest.java`
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/ProjectManualCreationMySqlIntegrationTest.java`

**Interfaces:** Consumes Task 2 WorkBinding事实、ProjectScope/ParticipantFact、Task 3 Mapper；produces `PreparationInitializationApi.initialize(command)` and current preparation/detail/items/history HTTP query。

- [ ] **Step 1: 实现幂等初始化主线**

建立窄engineering-api模块，先只放初始化命令且不提供HTTP入口。`PreparationInitializationCommand`只含受信`projectId/projectTaskId/executionContractId/expectedProjectVersion/expectedProjectTaskVersion/expectedContractVersion/triggerType/idempotencyKey/operationId/actorUserId`；tenant只取上下文，`triggerType`封闭为`PROJECT_CREATION/AUTHORIZED_RECOVERY`，不接受模板、角色或表单Schema自报。项目创建在ProjectTask与ExecutionContract冻结后同步调用`PreparationInitializationApi.initialize`；服务锁定ProjectWorkBinding及项目资格，并在同一模块化单体事务创建businessVersion 1 current DRAFT、item和Task 3读取的固定form实例，冻结task/contract/template标识与配置。

项目创建外层编排器按冻结标识生成稳定`Idempotency-Key=PRE02_INIT:{projectId}:{executionContractId}:{contractVersion}`，`operationId`单独用于命令与审计关联；严格沿用既有平台四段幂等作用域`tenantId+PREPARATION_INITIALIZE+actorUserId+idempotencyKey`，业务载荷事实固定为上述project/task/contract标识及三个expected版本。同一操作者同键同载荷由`PlatformCommandExecutionApi`重放原结果，异载荷冲突；不得虚构、固定或复用其他操作者身份。`PreparationInitializationApiImpl`和平台幂等/成功审计使用默认`REQUIRED`加入项目创建外层事务，禁止`REQUIRES_NEW`、异常吞并或异步提交；任一初始化失败必须共同回滚项目、任务、ExecutionContract、Preparation、幂等成功记录和成功审计。

`AUTHORIZED_RECOVERY`仍使用同一确定性键和原冻结业务载荷，只允许具备`pms:preparation-survey:manage + PROJECT_MANAGE + 当前PROJECT_MANAGER`的受信操作者从内部运维服务调用。不同操作者形成新的四段命令作用域；服务必须先完成权限、Project/WorkBinding锁定重验，再以Preparation稳定业务键`tenantId+projectId+PRE_02_SITE_SURVEY+businessVersion=1`锁定查询：若既有行的executionContractId及冻结版本与命令完全一致，直接返回既有结果，不进入平台初始化命令、不新增Preparation/item/form、幂等成功记录或`PREPARATION_INITIALIZE`成功审计，仅以真实当前actor追加一次`PREPARATION_INITIALIZATION_RECOVERY/NO_CHANGE`审计；若不存在才在该当前actor作用域执行初始化；既有行与冻结事实不一致则失败关闭并记录真实actor的REJECTED审计。不新增用户初始化HTTP或前端按钮。

- [ ] **Step 2: 实现只读投影**

实现按projectId+PRE_02当前查询、详情、items及快照/豁免历史空业务结果和稳定分页；query使用`PROJECT_VIEW`，响应只返回服务端allowedActions，不返回文件URL、OA原单据或内部Mapper事实。

- [ ] **Step 3: 处理无绑定的明确边界**

既有项目没有PRE-02 ExecutionContract时查询稳定返回`WORK_BINDING_NOT_AVAILABLE`，不从项目名、旧工勘表或模板身份猜测，不在SOL侧补写PROJ任务。历史项目迁移保留AI-MIG-000边界。

- [ ] **Step 4: 实施后验证并提交**

覆盖合法初始化、同actor同键同载荷重放、同actor同键异载荷冲突、跨actor授权恢复返回同一Preparation且不重复item/form/幂等成功记录/初始化成功审计、恢复审计actor为真实当前操作者、绑定/项目版本变化、0/多绑定、非经理、单/多租户上下文，以及项目创建外层事务中项目/任务/契约/Preparation/平台幂等与成功审计共同提交或共同回滚和稳定查询。

Expected: 初始化与查询API主线PASS。提交：`feat(engineering): 初始化项目工勘准备`

---

### Task 5: 实现逐项指派、填写与精确文件证据

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationItemApplicationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationFilePolicyProvider.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/command/PatchPreparationItemCommand.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationItemPatchReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationAssigneeCandidateReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationAssigneeCandidateRespVO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationController.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationItemApplicationServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationFilePolicyProviderTest.java`

**Interfaces:** ConsumesProjectParticipantFactApi、FileArtifactApi及FileBusinessObjectPolicyProvider SPI；produces字段存在性item/form/evidence PATCH。

- [ ] **Step 1: 实现项目经理指派与适用性管理**

项目经理可修改负责人、适用性、外包和结构化地点引用；每次只更新实际提交字段，空PATCH拒绝。候选分页复用SYSTEM公开`OrganizationUserCandidateApi`及项目所属公司/部门范围，服务端再次校验用户启用、同租户和组织范围；空范围返回空，不扩大为全租户。写成功递增item/form及preparation inputVersion并使旧快照非当前。

- [ ] **Step 2: 实现负责人填写**

`pms:preparation-survey:fill+PROJECT_VIEW+当前item负责人`只可写本人REQUIRED项的固定表单值、现场结论和证据槽位；表单按Task 3 Schema校验。地点维护继续调用既有AST公开能力，不复制地点表。

- [ ] **Step 3: 接入FileArtifact精确事实**

文件对象稳定键使用`ownerContext=SOL/objectType=SITE_SURVEY_ITEM/objectId=itemId/purposeCode=SITE_SURVEY_EVIDENCE/referenceKey`。保存冻结artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion；Provider按现有PLT调用链实现封闭动作矩阵：`CREATE_ARTIFACT→UPLOAD`、`ADD_VERSION→REPLACE`、SOL保存或提交冻结精确版本→`REFERENCE`、解绑→`DETACH`、元数据读取→`READ`、短时下载→`DOWNLOAD`、短时预览→`PREVIEW`。UPLOAD/REPLACE/REFERENCE/DETACH要求DRAFT、当前item负责人及`fill+PROJECT_VIEW`，READ/DOWNLOAD/PREVIEW要求项目可见范围与对应PLT功能权限；未知动作、动作与调用场景不符、非当前负责人、状态或版本变化均失败关闭。不保存URL或正文，不新增同义文件流程。

- [ ] **Step 4: 实施后验证并提交**

覆盖经理/负责人边界、跨项目/租户、字段存在性/null清空、Schema、多个证据槽位，以及真实PLT `CREATE_ARTIFACT/ADD_VERSION/REFERENCE/DETACH/READ/DOWNLOAD/PREVIEW`逐动作调用、未知动作拒绝、文件失效/错配/版本冲突、inputVersion和成功/拒绝审计。

Expected: 指派、填写和文件主线PASS。提交：`feat(engineering): 完成工勘逐项填写`

---

### Task 6: 实现提交、逐项确认及退回新版本

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationReviewService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/command/PreparationReviewCommand.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationReviewReqVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationReviewServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/preparation/PreparationReviewMySqlIntegrationTest.java`

**Interfaces:** Produces submit/confirm/confirm-not-applicable/return状态命令及精确复制重置矩阵。

- [ ] **Step 1: 冻结提交版本**

项目经理提交时锁PROJ范围、current preparation和全部item/form，重验必填/文件及无非法N/A，冻结表单并将Preparation置PENDING_CONFIRMATION。缺少来源终态不阻止进入评审，但会在就绪时形成阻断。

- [ ] **Step 2: 实现逐项确认与聚合确认**

项目经理确认REQUIRED项或确认NOT_APPLICABLE；最后未决项确认时同事务把Preparation置CONFIRMED。已确认表单、证据、负责人和来源输入无普通修改入口。

- [ ] **Step 3: 实现退回和下一current版本**

从PENDING_CONFIRMATION或CONFIRMED退回指定item：旧item/preparation=RETURNED、清旧current，创建businessVersion+1 DRAFT。已确认未退回项保持确认事实；退回项重置PENDING/DRAFT；未决项保持；来源仅复制引用/last-success并置UNKNOWN；豁免按project+itemCode沿用历史。

- [ ] **Step 4: 实施后验证并提交**

真实MySQL覆盖最后一项聚合、N/A、PENDING和CONFIRMED退回、复制/重置矩阵、current唯一、故障点全回滚、幂等和并发单胜。

Expected: Preparation×Item联合状态机PASS。提交：`feat(engineering): 完成工勘确认与退回`

---

### Task 7: 实现就绪计算、不可变快照与SOL公共API

**Files:**
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/readiness/SiteSurveyReadinessApi.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/readiness/dto/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/readiness/SiteSurveyReadinessApiImpl.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationReadinessService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationController.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationReadinessServiceTest.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/readiness/SiteSurveyReadinessApiImplTest.java`

**Interfaces:** Produces显式evaluate、纯只读inspect/lockAndRevalidate和结构化ReadinessFactVector。

- [ ] **Step 1: 建立窄engineering-api模块**

在Task 4已建立的API模块中增加readiness契约；模块继续只依赖通用DTO/validation，不依赖engineering biz。定义spec锁定的inspect/revalidation查询、事实向量、blocker和响应；不暴露DO、Mapper或Service。

- [ ] **Step 2: 实现无OA正向就绪主线**

显式evaluate锁PROJ→SOL→PLT，要求Preparation CONFIRMED、全部适用项确认、固定表单/证据有效；无来源要求项不伪造来源。排序构造结构化向量，相同向量重放最新快照，变化才追加并CAS指针/readinessVersion。

- [ ] **Step 3: 实现纯只读公共重验**

inspect实时计算并比较最新快照；外部文件/范围变化返回snapshotCurrent=false/NOT_READY但不写。lockAndRevalidate要求全部expected版本和expectedFactVector，持锁至调用方提交；非current、非READY、向量变化或Provider异常失败关闭。

- [ ] **Step 4: 实施后验证并提交**

覆盖READY、各项阻断、同向量重放、外部文件变化、并发evaluate、只读零写入、跨租户、版本冲突和真实MySQL快照不可变。

Expected: 无OA主线及公共就绪契约PASS。提交：`feat(engineering): 提供工勘就绪权威事实`

---

### Task 8: 接入来源引用、异常持久化与逐项豁免

**Files:**
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/source/PreparationSourceFactProvider.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/source/dto/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceProviderRegistry.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationWaiverService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationReadinessService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/PreparationController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationSourceRefreshReqVO.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/PreparationWaiverReqVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/preparation/PreparationSourceAndWaiverServiceTest.java`

**Interfaces:** Produces来源Provider SPI、显式refresh和豁免申请/提交/批准/驳回/撤回；consumes当前角色事实。

- [ ] **Step 1: 定义来源Provider封闭契约**

Provider按sourceType精确单一注册，inspect/revalidate返回normalizedResult、factVersion、watermark；0/多Provider、未知状态、越租户或异常失败关闭。生产无INT-05实现时Registry返回稳定UNAVAILABLE，不注册假成功Provider。

- [ ] **Step 2: 实现来源刷新异常持久化**

成功refresh写SYNCED current与last-success；首次失败写ERROR/UNKNOWN且current三字段为空；后续失败清current、保留last-success仅显示。Service捕获Provider失败并提交异常事实/拒绝审计后，Controller返回稳定失败；无成功幂等或成功审计。

- [ ] **Step 3: 实现逐项豁免**

项目经理申请/提交/撤回；审批人需`waiver-approve+PROJECT_VIEW+冻结当前角色`且非申请人。批准豁免只替代冻结blocker codes，按project+itemCode跨Preparation版本有效；到期/撤回使快照过期并阻断。

- [ ] **Step 4: 接入完整就绪向量并验证**

evaluate纳入source current事实和有效waiver；OA必需项在无Provider时NOT_READY，有效豁免或无OA项可READY。测试使用test-scope Provider证明成功/版本变化，生产仍无假INT-05实现。

Expected: 来源与豁免边界PASS，不宣称INT-05完成。提交：`feat(engineering): 完成工勘来源与豁免`

---

### Task 9: 建设响应式工勘准备界面并退役旧写入口

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/preparation/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectPreparationPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/PreparationItemDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/PreparationWaiverDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/PreparationReadinessDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectPreparationPanel.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-detail/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/site-survey/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/site-survey/index.ts`

**Interfaces:** ConsumesTask 4～8 HTTP API和现有PLT上传组件；produces项目工作区PRE-02主界面。

- [ ] **Step 1: 建立统一前端契约**

类型精确表达current Preparation、item/form/evidence、source、waiver、blocker、snapshot和allowedActions；写请求统一发送If-Match、Idempotency-Key及expected版本，不传tenant/角色/READY结果。

- [ ] **Step 2: 实现主线交互**

项目创建成功后展示已由内部事务自动初始化的Preparation；若历史项目没有冻结绑定或内部恢复被拒绝，只展示稳定阻断原因，不提供初始化按钮。项目经理指派/确认，负责人填写固定表单和上传证据，项目经理提交/退回/N-A，来源刷新、豁免和显式就绪评估在同一项目面板衔接。来源异常显示last-success但明确“不可用于就绪”。

- [ ] **Step 3: 退役旧V1.7写入口**

从导航/项目旧详情移除`pms_eng_site_survey`创建、更新、删除、确认、驳回、归档调用；保留必要历史只读证据和AST地点维护入口，不双写、不把旧状态映射为PRE-02。

- [ ] **Step 4: 实施后验证并提交**

运行运行时组件测试、`corepack pnpm ts:check`、定向ESLint/Stylelint和`build:local`；覆盖解绑/重绑证据、刷新恢复、权限按钮和320/768/1024/1440布局。

Expected: UI主线和旧写退役PASS。提交：`feat(ui): 建设工勘准备工作区`

---

### Task 10: 完成真实MySQL、浏览器、独立复审与Feature回写

**Files:**
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/preparation/PreparationMySqlIntegrationTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/preparation/PreparationReadinessMySqlIntegrationTest.java`
- Create: `docs/engineering/evidence/f-sol-002-browser-evidence.json`
- Modify: `tasks/features/F-SOL-002.md`
- After independent GO, modify in specification repository: `specs/features/README.md`
- After independent GO, modify in specification repository: `scripts/generate_requirement_traceability.py`
- After independent GO, regenerate: `docs/traceability/requirement-matrix.md`

**Interfaces:** ConsumesTask 1～9全部实现；producesImplementation Done证据与新规格基线。

- [ ] **Step 1: 执行空库与应用服务真实验收**

独立Compose空库执行V1→V97，装配真实INFRA ConfigApi、PROJ/PLT/SOL Mapper及平台幂等审计。通过公开模板保存/发布→建项目冻结ExecutionContract并在同一事务自动初始化→指派/填写/文件→提交/确认→evaluate，验证固定目录唯一读取、READY与不可变快照；故障点全回滚、同键重放/异载荷冲突、并发单胜、跨租户和审计恰一。

- [ ] **Step 2: 验证来源、豁免与失效恢复**

使用test-scope权威Provider验证OA成功、版本/水位变化、首次/后续异常持久化；生产无Provider保持阻断。验证有效豁免替代指定阻断、到期/撤回恢复NOT_READY，以及文件失效/范围变化只读重验失败且显式evaluate追加NOT_READY。

- [ ] **Step 3: 执行真实浏览器闭环**

优先使用内置浏览器。完成模板发布→新项目自动初始化工勘→指派→负责人填写/证据创建及换版→逐项确认→来源/豁免→READY；再验证解绑重绑同一文件槽位、短时预览/下载、退回新版本、文件/来源/豁免失效、权限负向、刷新持久及四档响应式，记录HTTP、console/page error和截图证据。

- [ ] **Step 4: 完整验证、独立复审和回写**

运行受管快照、相关Reactor、前端Task 9命令、真实MySQL、`git diff --check`及边界检查。独立GO后回写Task/Feature、规格索引和PRE-02追溯，锁定新规格并同步NPDMS；不进入Deployment、SIT、UAT或Release。

Expected: AC-FSOL002-001～012完整证据与独立GO。提交：`docs(feature): 通过 F-SOL-002 Implementation Done`

## Plan Self-Review

- **Spec coverage:** Task 1～3覆盖六表、种子、状态与持久化；Task 2闭合ExecutionContract生产/读取；Task 4～6闭合初始化、指派、填写、文件、确认和退回；Task 7～8闭合快照、公共API、来源与豁免；Task 9～10闭合UI、旧入口、MySQL、浏览器和追溯。
- **Mainline order:** 无OA正向主线在Task 7先闭合；来源和豁免分支Task 8接入，不把未实施INT-05前置为全Feature阻断。
- **Dependency direction:** engineering只依赖project-api/platform-api；PROJ持有ExecutionContract；PLT持有文件；来源SPI位于engineering-api且无生产假Provider；IMP未来只依赖engineering-api。
- **State consistency:** READY仅由current snapshot产生；input mutation直接失效，外部变化只读失败关闭，evaluate唯一追加快照；退回原子切换下一current DRAFT。
- **Type consistency:** 项目、任务、contract、preparation、item、artifact和snapshot标识为Long；业务/输入/就绪/contract版本为Integer；source watermark为String；事实向量为结构化DTO，不使用无决策价值的哈希。
- **Scope:** 不建设通用Schema设计器、OA流程、第二任务树、S4命令、跨Context事件或历史自动迁移；不修改基础框架/BPM/已执行迁移。
- **Placeholder scan:** 计划无待定实现方案；INT-05缺失的生产行为已唯一锁定为UNAVAILABLE并由无OA/豁免路径继续。

## Technical Plan Gate

当前状态：`GO`。独立裁决：`NPDMS-FSOL002-TECHPLAN-20260827-01-R2`；批准提交：`8e7df1c1695325efae4393bf0e8a27b815df025b`。允许从Task 1开始实施，不重开PRD、SDS或Feature Ready。
