# F-PLT-002 共享动态表单模板与实例基础能力实施计划

> **实施代理必读：** 执行本计划时必须使用 `executing-plans` 技能。先完成一个集成的正向实施闭环，再集中执行整体验证。不得把本功能拆成按层交付的任务、逐步门禁评审或碎片提交。

**目标：** 交付由PLT拥有的共享动态表单基础能力：获权管理员配置并发布不可变的FormCreate模板修订；获权用户人工选择当前启用修订；冻结实例能够渲染、保存和刷新普通值及受控FileArtifact字段。

**架构：** 在`pms-module-platform`内新增三张租户感知的PLT表和一组REST/UI能力。直接复用仓库现有FormCreate设计器、渲染器、编解码工具及全局组件装配，只为`PmsFileArtifact`新增PLT组合层。PLT拥有模板身份、不可变修订和手工实例，既有F-PLT-001服务继续拥有文件Artifact/Version/Reference真值。在出现真实跨模块调用方前不创建公共模块API。

**技术栈：** Java 25、Spring Boot、MyBatis/XML、Flyway/MySQL、PLATFORM命令幂等与审计、基于MinIO及可选ClamAV的F-PLT-001、Vue 3.5.34、TypeScript、Element Plus 2.13.7、`@form-create/designer` 3.4.0、`@form-create/element-ui` 3.2.38、pnpm 9.15.5及Docker Compose基础设施。

**锁定输入：** 规格源提交`a04aa0fa25194ca0cd5e157d7c16c3c42a26ff7f`、NPDMS受管同步提交`af428bab02ba5b388a71723dba97882ad6a6ddb4`、功能就绪裁决`NPDMS-FPLT002-FEATURE-READY-20260828-01-R1`、`specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`、`specs/features/F-PLT-002-physical-contract.json`、`specs/features/F-PLT-002-legacy-form-reuse-audit.md`、PRD V1.8中的SOL-01/PRE-04/PM-03/PM-11、SDS Phase 1/2/3及`docs/coding/database-query-interface.md`。

## 一、执行方式与固定边界

- 一次打通“模板元数据和修订设计 → 预览 → 发布 → 启用 → 人工选择 → 冻结实例 → 动态渲染 → 普通值/FileArtifact录入 → 保存 → 刷新”完整路径；所有层连接完成前始终视为同一实施候选。
- 实施过程中只把小范围编译或类型校正作为反馈，不为持久化、后端、前端或测试分别创建阶段性PASS、独立评审或提交。完整路径形成后再集中执行候选级验证矩阵。
- 不在NPDMS直接修改受管规格。出现新契约需要时，必须先回到规格仓库前向修订。
- 不实现WorkBinding匹配、项目模板绑定、PRE-04业务或版本化接入、ProjectTask工作台适配、领域完成/审批、完整SOL-01或SCH/IMP/ACC/CUT行为。
- 不修改或迁移BPM FormCreate代码、旧`pms_eng_form_template/pms_eng_form_instance`、旧需求分析类、API、路由、页面、表、数据、菜单、权限或行为；不迁移、不双写旧数据。
- 首版保持高信任且不裁剪：保留FormCreate全部当前内置能力和仓库增强能力，包括iframe、任意配置的GET/POST接口选择器、事件、函数及`parseFunc`；不新增组件、属性、URL或脚本白名单，不增加沙箱或审批流。
- 后续限制必须形成新规格和新发布修订，不得反向改写已发布修订或冻结实例。

## 二、锁定复用映射

| 审计ID | 决策 | F-PLT-002明确目标 |
|---|---|---|
| BPM-01、BPM-02、BPM-03、BPM-04 | 直接复用 | 导入现有`fc-designer`、`useFormCreateDesigner`、`encodeConf`/`encodeFields`/`decodeFields`/`setConfAndFields`，依赖现有`setupFormCreate`；不修改这些文件。 |
| BPM-05、BPM-06、BPM-07、BPM-08 | 复制后增强 | 新`DynamicFormTemplateEditor.vue`复制完整设计器配置和明确的保存、重开、预览体验，但以携带`If-Match`的PLT明确DRAFT修订为目标；下一草稿由服务端命令复制。 |
| PMS-02至PMS-06、PMS-08 | 复制后增强 | 新PLT模板、修订、实例模型和页面保留FormCreate载荷、冻结修订意图、CAS及列表/选择交互，同时把修订状态与模板可用性分离。 |
| REQ-02、REQ-03、REQ-04A | 复制后增强 | 新渲染器保留Editor、选择/列表反馈和响应式布局意图，不复制固定PRE-04字段、项目上下文或本地快照兜底。 |
| BPM-09、PMS-01、PMS-07、PMS-09至PMS-12、REQ-01、REQ-04至REQ-06 | 本功能不复用 | BPM表/状态、`productType`、原始JSON文本编辑、旧提交/审批/删除流程、PRE-04标签、项目入口、WorkBinding、旧REST及旧表真值均不得进入新实现。 |

实施证据必须把每个复制目标追溯到上述审计ID，并证明三组旧实现路径相对`af428bab`零差异。

## 三、整体实施闭环

- [ ] **一次完成F-PLT-002完整正向实施闭环**

### 1. 持久化、迁移与确定性种子

新增`sql/migrations/V102__fplt002_dynamic_form.sql`，按以下精确定义建立表和约束：

- `plt_dynamic_form_template`：应用分配的`BIGINT`主键、租户、稳定`template_code`、可变名称/分类/说明、`ENABLED|DISABLED`、当前已发布修订指针、整数CAS版本及Yudao审计/逻辑删除列；建立`uk(tenant_id,template_code)`、`uk(tenant_id,id)`及锁定的可用性、名称和指针索引。
- `plt_dynamic_form_template_revision`：应用分配的主键、租户/模板/修订号、`DRAFT|PUBLISHED`、可空`draft_marker`、来源修订、JSON配置/规则、精确引擎/设计器/渲染器版本、发布事实、CAS版本及审计/逻辑删除列；建立锁定的修订唯一键、单草稿唯一键、租户内复合外键及索引。仅DRAFT的`draft_marker=1`；发布时清为`NULL`。
- `plt_dynamic_form_instance`：预分配应用主键、租户、生成的实例编码/名称、服务端拥有的`PLATFORM/MANUAL_DYNAMIC_FORM/{instanceId}`绑定、冻结模板/修订/修订号/引擎版本、普通值JSON、`created_by`、CAS版本及审计/逻辑删除列；建立实例编码、所有者对象唯一键及租户内修订外键。

所有新聚合行使用`@TableId(type = IdType.INPUT)`和`IdWorker.getId()`，保证插入服务端所有者绑定前已得到手工实例ID。先建立无当前指针的模板表，再建立修订和实例外键，最后增加模板的租户内当前修订外键；不得以不受约束的跨租户指针弱化循环所有权。JSON列以`{}`或`[]`初始化，不使用可空占位。已发布行没有更新或删除Mapper路径。

字段类型固定为：所有ID、租户和主体引用使用`BIGINT`；编码字段`VARCHAR(64)`；名称`VARCHAR(128)`；说明`VARCHAR(512)`；所有者上下文`VARCHAR(32)`；对象类型`VARCHAR(64)`；对象ID`VARCHAR(128)`；状态/可用性`VARCHAR(16)`；引擎编码`VARCHAR(64)`；设计器/渲染器版本`VARCHAR(32)`；修订号/CAS版本`INT`；草稿标记`TINYINT NULL`；配置/规则/值使用原生`JSON`；发布/审计时间`DATETIME`；Yudao创建人/更新人`VARCHAR(64)`、逻辑删除`BIT(1)`。统一使用`utf8mb4_unicode_ci`，无自增列、无兼容性生成列。

新增`sql/migrations/V103__fplt002_dynamic_form_seed.sql`，模板ID使用`992202010001..3`，修订ID使用`992202020001..3`，字典ID使用`992202030001`，菜单ID使用`198800..198805`，并满足：

- 向`pms_file_category`加入`DYNAMIC_FORM_ATTACHMENT`，不改变F-PLT-001状态或存储；MinIO和可选ClamAV继续作为继承的基础设施。
- 在现有`19271`“文档表单”组下新增可见菜单`198800 / dynamic-form-template / pms/platform/dynamic-form/template/index`，权限为`pms:dynamic-form-template:query`，子权限为`198801 manage`、`198802 publish`；新增可见菜单`198803 / dynamic-form-instance / pms/platform/dynamic-form/instance/index`，权限为`pms:dynamic-form-instance:query`，子权限为`198804 create`、`198805 update`。只允许这六项锁定权限资源，不写`system_role_menu`，不创建角色。
- 插入`PLT_EXAMPLE_GENERAL_FORM`启用且指向当前已发布修订，包含代表性的文本、富文本、选择、布尔、数值、布局、普通上传和`PmsFileArtifact`规则；插入`PLT_EXAMPLE_DISABLED_FORM`作为不可选择的停用已发布示例；插入`PLT_EXAMPLE_DRAFT_FORM`作为不可选择的仅草稿示例。统一使用`creator='seed'`及精确引擎版本。菜单和字典可以确定性更新；示例模板/修订只能不存在时插入，重复执行不得覆盖既有PUBLISHED载荷，也不得重置用户后续修改的指针或可用性。不播种PRE-04、WorkBinding或其他领域值。

新增`pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormMigrationContractTest.java`，断言表/约束/索引名称、V102先于V103、恰好六项权限、无角色授权及三类选择状态示例。

### 2. PLT持久化与Schema模型

新增以下租户自有数据对象：

- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormTemplateDO.java`
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormTemplateRevisionDO.java`
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormInstanceDO.java`

在`dal/mysql/dynamicform/`新增`DynamicFormTemplateMapper.java`、`DynamicFormTemplateRevisionMapper.java`、`DynamicFormInstanceMapper.java`，在`src/main/resources/mapper/dynamicform/`新增对应XML，并在`dal/mysql/dynamicform/query/`新增以下单一场景记录：

- `DynamicFormTemplatePageQuery`、`DynamicFormTemplateRowQuery`、`DynamicFormTemplateLockQuery`、`DynamicFormTemplateVersionUpdate`；
- `DynamicFormRevisionListQuery`、`DynamicFormRevisionRowQuery`、`DynamicFormRevisionLockQuery`、`DynamicFormDraftCreateQuery`、`DynamicFormRevisionPublishUpdate`；
- `DynamicFormInstancePageQuery`、`DynamicFormInstanceRowQuery`、`DynamicFormInstanceLockQuery`、`DynamicFormInstanceValueUpdate`。

分页摘要与稳定排序、修订列表、锁定读取、租户复合身份校验和CAS更新均进入XML。简单主键或稳定复合唯一键读取可使用`LambdaQueryWrapperX`。Mapper方法不得接受长位置参数列表、`Map`或万能查询对象；禁止SQL注解、`${}`、`.last(...)`、Service内拼SQL及跨模块读表。空租户/权限范围必须返回空结果。

新增`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java`，只负责锁定的结构模型：

- 配置必须解析为一个JSON对象，规则必须解析为一个JSON数组；
- 按文档顺序递归遍历每条规则对象及全部嵌套`children`数组；
- 每个非空`field`均视为值字段并在整个修订内唯一；只有精确`type=PmsFileArtifact`才是受控文件输入；
- 受控文件字段键禁止包含`/`，且`FORM_FIELD_ATTACHMENT/{fieldKey}`必须符合F-PLT-001既有`purpose_code VARCHAR(64)`及REST限制；固定前缀22字符，因此受控`fieldKey`最长42字符。未知规则/组件对象原样保留，不检查或删除URL、事件、函数、iframe或`parseFunc`内容；
- DRAFT保存和发布时均要求`FORM_CREATE_ELEMENT_PLUS`、设计器`3.4.0`、渲染器`3.2.38`，并为命令、查询和文件Provider返回有序的普通字段键、文件字段键集合。

在`pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/enums/ErrorCodeConstants.java`中按物理契约顺序新增`1_010_003_000..010`：模板不存在、模板编码冲突、模板停用、当前修订变化、草稿已存在、修订非草稿、Schema无效、字段重复、实例不存在、未知实例字段、文件字段必须使用File API。另以`1_010_003_011`定义`DYNAMIC_FORM_VERSION_CONFLICT`，承载契约通用`VERSION_CONFLICT`。复用既有全局`FORBIDDEN`、`PLATFORM_COMMAND_KEY_CONFLICT`和`PLATFORM_COMMAND_IN_PROGRESS`，不重复定义。

### 3. 命令、事务、授权与审计

在`service/dynamicform/`新增`DynamicFormCommandService.java`、`DynamicFormQueryService.java`、`DynamicFormActionProjection.java`、`DynamicFormCommands.java`和`DynamicFormViews.java`。`DynamicFormCommands`定义主体及创建模板、修改元数据、创建修订、修改修订、发布修订、设置可用性、创建实例、修改实例记录；`DynamicFormViews`定义服务层模板/修订/选择/实例视图，禁止Service依赖Controller VO。在`pms-module-platform/pom.xml`中显式依赖既有SYSTEM权限API、Web/Security、校验和事务设施，不依赖其他PMS模块的`-biz`代码。

`DynamicFormCommandService`必须执行以下精确事务：

1. **创建模板：** 规范化并校验请求，占用`PLT:DYNAMIC_FORM:TEMPLATE_CREATE`，插入DISABLED模板和revision 1 DRAFT（`{}`/`[]`及锁定引擎版本），在同一事务持久化受控成功审计和幂等结果。
2. **修改模板元数据：** 按租户/ID锁定，校验管理权限及`If-Match`，只应用具备存在性语义的`templateName/categoryCode/description`，CAS模板后写一条安全成功审计。显式`description:null`清空，未出现则保持不变。
3. **创建下一草稿：** 占用`PLT:DYNAMIC_FORM:REVISION_CREATE`，依次锁定模板、当前草稿和当前已发布行，要求模板期望版本匹配且不存在草稿，从不可变当前已发布载荷复制`revisionNo+1`并记录成功事实。没有已发布修订时此命令无效；初始创建已经负责revision 1。
4. **修改修订：** 依次锁定模板和明确修订，要求管理权限、DRAFT状态及修订`If-Match`；结构校验后整体替换config/rules/engine元组，CAS草稿，并只审计ID、引擎版本及有序字段键。
5. **发布修订：** 占用`PLT:DYNAMIC_FORM:REVISION_PUBLISH`，依次锁定模板和明确DRAFT，要求发布权限及修订`If-Match`，重复全部结构/字段/引擎校验；把DRAFT CAS为不可变PUBLISHED并清除`draft_marker`，切换模板当前指针、递增模板版本，最后写成功幂等和审计。失败时旧指针和DRAFT不变。
6. **启用/停用模板：** 为目标状态分别占用幂等范围，锁定模板，要求发布权限和模板`If-Match`；只有存在当前PUBLISHED修订时才可启用，CAS可用性，不创建修订。
7. **创建手工实例：** 占用`PLT:DYNAMIC_FORM:INSTANCE_CREATE`，依次锁定模板和请求修订，要求创建权限、期望模板版本、ENABLED且明确修订仍为当前PUBLISHED；预分配实例ID，绑定`PLATFORM/MANUAL_DYNAMIC_FORM/{id}`，生成`DFI-{id}`，冻结修订/引擎事实，以`{}`初始化值并记录成功事实。不得替换为较新修订。
8. **修改实例：** 按租户/ID锁定，要求更新权限、创建者身份及实例`If-Match`；请求必须为非空JSON对象，拒绝未知键和受控文件键，只合并出现的普通键并保留`null/false/0/""/[]`，仅执行一次CAS，只审计有序提交键及前后版本。

六类幂等命令使用既有`PlatformCommandExecutionApi`。请求摘要由显式规范化`LinkedHashMap`构造，包含目标ID、期望版本和规范化载荷后计算SHA-256；不接受客户端摘要。`Decision.CONFLICT`与`Decision.IN_PROGRESS`映射为各自既有错误。命令执行器外使用`TransactionTemplate`，回滚后才按既有应用服务模式记录稳定拒绝审计。PATCH命令参与单一事务，并通过`OperationAuditApi`记录成功；不另建幂等记录。

每条成功明细必须包含`operationId`、主体、聚合ID、前后状态或版本，以及适用时的有序变更字段键。禁止把完整config/rules、事件/函数源码、富文本、表单值、API/iframe内容、文件正文、MinIO键、永久URL或Provider异常写入审计。本功能不产生动态表单Outbox事件。

`DynamicFormActionProjection`结合`PermissionApi`及当前行事实，只返回查询时真实可执行动作：`PATCH_TEMPLATE`、`CREATE_REVISION`、`PATCH_REVISION`、`PUBLISH_REVISION`、`ENABLE`、`DISABLE`、`CREATE_INSTANCE`、`PATCH_INSTANCE`。查询或权限事实失败时返回空动作集。Controller仍执行功能权限校验，每条命令在锁定后再次授权。

### 4. 不建立第二文件真值的FileArtifact组合

新增`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormFilePolicyProvider.java`，为精确`PLATFORM/DYNAMIC_FORM_INSTANCE`实现既有`FileBusinessObjectPolicyProvider`。

- 只解析`FORM_FIELD_ATTACHMENT/{fieldKey}`和UUID `referenceKey`；加载当前租户实例及冻结修订，通过`DynamicFormSchemaService`要求字段为精确受控文件字段。
- `UPLOAD/REFERENCE/REPLACE/DETACH`要求`pms:dynamic-form-instance:update`且`created_by`等于当前主体；`READ/DOWNLOAD/PREVIEW`要求`pms:dynamic-form-instance:query`。F-PLT-001端点仍独立校验对应`pms:file:*`权限。
- 返回不可变冻结`template_revision_id`作为`scopeVersion`，并返回`referenceMutability=MUTABLE`、`cardinality=MULTIPLE`、分类`DYNAMIC_FORM_ATTACHMENT`、50MB、`INTERNAL`，以及精确MIME集合：`application/pdf`、`image/jpeg`、`image/png`、`text/plain`、`application/msword`、`application/vnd.openxmlformats-officedocument.wordprocessingml.document`、`application/vnd.ms-excel`、`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`、`application/vnd.ms-powerpoint`、`application/vnd.openxmlformats-officedocument.presentationml.presentation`。普通实例PATCH不得使有效文件引用过期。`lockAndRevalidate`先锁定实例，比较期望冻结修订ID，再复核权限、创建者和Schema后返回同一事实；未知身份、权限或Schema均失败关闭。
- 同时实现单引用和引用集合检查/锁定重验，使实例详情可一次读取全部受控字段。Provider先取得实例锁，F-PLT-001随后取得Artifact → Version → Reference锁，之后不得调用其他上下文。

`DynamicFormQueryService`读取冻结修订并发现全部受控字段用途。存在一个或多个受控字段时，必须对整个实例恰好调用一次`FileArtifactApi.inspectReferenceSets`，要求每个purpose均有结果，并允许获权purpose返回显式空`activeFacts`数组。无受控字段时不得调用FileArtifact，因为既有集合查询明确要求至少一个集合键，此时直接返回空受控事实映射。结果按`fieldKey`映射ACTIVE事实，只返回`artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/status`；禁止写入`value_json`或暴露存储键/URL。Provider不可用时详情读取失败关闭，不得伪造空证据。

专用`PmsFileArtifact`组件继续调用既有上传、加版本、解绑、读取和访问票据命令，并为同一上传意图稳定保留`slotKey` UUID。文件成功但普通值PATCH失败时，文件仍从当前FileReference真值可见，并使用原slot/意图重试；实例不增加附件快照或PENDING状态。

### 5. REST契约与响应投影

在`pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/`新增以下Controller及VO：

- `DynamicFormTemplateController.java`，配套`DynamicFormTemplatePageReqVO`、`DynamicFormTemplateCreateReqVO`、具备字段存在性语义的`DynamicFormTemplatePatchReqVO`、`DynamicFormTemplateRespVO`、`DynamicFormRevisionPatchReqVO`、`DynamicFormRevisionRespVO`；
- `DynamicFormInstanceController.java`，配套`DynamicFormInstancePageReqVO`、`DynamicFormInstanceCreateReqVO`、`DynamicFormInstancePatchReqVO`、`DynamicFormInstanceRespVO`、受控`DynamicFormFileFactRespVO`。

只开放锁定的`/api/v1/pms`模板分页/创建/详情/元数据PATCH、修订创建/详情/PATCH/发布、启用/停用、选择、实例分页/创建/详情/PATCH端点。租户和主体来自`TenantContextHolder`、`SecurityFrameworkUtils`；请求VO不定义禁止自报字段。所有锁定CAS端点必须提供整数`If-Match`头，只有锁定的幂等端点要求`Idempotency-Key`。选择GET要求实例查询权限，只有当前具备创建权限时才投影`CREATE_INSTANCE`。模板和实例分页均使用锁定稳定排序。

Controller契约测试必须断言方法、路径、头、权限注解、禁止请求字段、`CommonResult/PageResult`形状，以及元数据字段“未出现”和“显式null”的区别。本功能不向`pms-module-platform-api`新增动态表单公共契约。

### 6. 完整FormCreate配置与手工实例界面

新增`yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/dynamic-form/index.ts`，为全部REST端点定义精确请求/响应类型、结构化`allowedActions`、数值CAS版本及受控文件事实。客户端不得发送租户、主体、所有者绑定、生成编码、修订状态或发布事实。

在`src/views/pms/platform/dynamic-form/`新增以下PLT界面：

- `template/index.vue`：响应式模板列表、元数据创建/编辑、修订状态与可用性双标签、版本历史，以及由服务端动作投影驱动的按钮；
- `template/DynamicFormTemplateEditor.vue`：针对明确修订的全屏设计器/预览，使用完整复制的BPM `designerConfig`、`useFormCreateDesigner`及PLT受控字段注册；以`If-Match`保存DRAFT，响应未知时重新读取，PUBLISHED只读预览；
- `instance/index.vue`：响应式实例列表及启用模板选择/预览；创建始终发送选中的`templateRevisionId`和`expectedTemplateVersion`；
- `instance/DynamicFormInstanceForm.vue`：用FormCreate渲染冻结修订，把服务端文件事实注入受控字段，只收集真实变化的普通键，通过渲染器校验，以`If-Match`执行PATCH，并在刷新后恢复相同值；
- `components/PmsFileArtifactField.vue`、`components/usePmsFileArtifactDesignerRule.ts`、`components/registerDynamicFormComponents.ts`、`components/dynamicFormCodec.ts`、`components/dynamicFormRuntime.ts`。

`dynamicFormCodec.ts`必须复用既有编解码工具，同时把其字符串数组表达适配为REST JSON数组契约。`dynamicFormRuntime.ts`深拷贝冻结规则，递归向精确`PmsFileArtifact`规则注入实例ID、字段键、当前事实和读写动作，并在不改变持久化修订的情况下把这些键排除出普通PATCH。

`PmsFileArtifactField.vue`组合既有`PmsFileUploader`、`PmsFileReferenceList`和版本/访问流程，不拥有第二份文件值；响应未知时保留同一slot/幂等键。`registerDynamicFormComponents.ts`只在新页面加载时注册PLT组件，不修改`src/plugins/formCreate/index.ts`或共享`useFormCreateDesigner.ts`。

客户端为每个完整的创建模板、创建修订、发布、可用性变更和创建实例意图保留一个稳定幂等键。响应未知的重试复用该键；成功响应或明确改变载荷/目标后才开始新意图。界面只使用服务端返回的`allowedActions`，显示高信任浏览器代码提示，区分普通上传与受控FileArtifact证据，并说明停用模板不改变既有实例。

320px下模板设计器可以明确建议使用桌面宽度，但模板列表和实例填写不得产生页面级横向溢出；768/1024/1440下编辑器、预览、动作和版本身份必须可见。只增加两个由新菜单驱动的页面组件，不修改旧BPM、工程、需求分析或项目详情页面。

### 7. 随实施建立的聚焦自动化覆盖

在`pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/`新增：

- `DynamicFormSchemaServiceTest`：嵌套字段发现、重复/空字段键、受控键包含斜杠、精确引擎版本、未知组件保留，以及函数/API/iframe内容不变；
- `DynamicFormCommandServiceTest`：全部状态、权限、版本、幂等、审计分支及安全明细边界；
- `DynamicFormQueryServiceTest`：稳定列表/动作、冻结修订渲染、全部受控purpose一次批量文件调用、每个purpose显式空ACTIVE事实、无受控字段时零调用，以及Provider失败；
- `DynamicFormFilePolicyProviderTest`：精确命名空间、字段、动作、创建者、权限、scope版本决策及实例锁先于文件锁；
- `DynamicFormControllerContractTest`：上述REST契约；
- `DynamicFormApplicationMySqlIntegrationTest`：在同一Spring上下文使用真实Mapper、Service、`PlatformCommandExecutionApiImpl`、`OperationAuditApiImpl`、FileArtifact API/Provider注册表及MySQL事务。

MySQL应用测试必须证明：模板编码唯一、草稿唯一、已发布修订不可变、指针/可用性行为、启用选择漂移拒绝、新发布后既有实例仍冻结、普通`false/0/null/空值`语义、并发发布和实例PATCH均仅一个胜者、同键完成重放、异载荷冲突、预置IN_PROGRESS、租户与创建者隔离、`plt_operation_audit.detail_snapshot`精确安全、文件事实一次批量读取，以及回滚后无部分模板/修订/实例、成功幂等、成功审计或指针变更。

在新组件旁新增前端运行时测试：`dynamicFormCodec.runtime.spec.ts`、`DynamicFormTemplateEditor.runtime.spec.ts`、`DynamicFormInstanceForm.runtime.spec.ts`、`PmsFileArtifactField.runtime.spec.ts`、`dynamicFormPages.spec.ts`。使用Vue客户端渲染器覆盖真实组件分支，禁止仅做静态文本匹配。覆盖完整设计器菜单/配置、保存/重开/预览、递归文件规则注入、`false/0/null/空数组`保留、真实部分PATCH、响应未知意图稳定、`allowedActions`及四个响应式分支。

## 四、整体验证与验收

- [ ] **完整路径实施结束后集中执行一次整体验证，形成一个候选提交并请求一次实施完成评审**

以下验证只针对完整候选执行。任何失败均使同一候选返回实施，不形成已通过的子任务或部分门禁。

1. 运行动态表单后端聚焦测试、Vue运行时测试及受影响的PLATFORM/FileArtifact测试；运行`corepack pnpm ts:check`、目标ESLint/Stylelint/Prettier检查和`corepack pnpm build:local`；最后按仓库规则执行JDK 25完整Maven Reactor测试/构建。
2. 只启动仓库权威Docker Compose基础设施。重建隔离MySQL数据库并从V1迁移至V103，记录Flyway migrate/info/validate、约束/索引/种子，以及宿主机后端/前端构建身份。浏览器文件存储使用真实MinIO，同时验证默认`scanStatus=SKIPPED`和配置可选ClamAV后的`PASSED`，不改变继承的文件状态机。
3. 通过公开REST完成正向闭环及MySQL矩阵：创建 → 设计 → 重开 → 预览 → 发布 → 启用 → 选择 → 创建冻结实例 → 保存普通值 → 上传/换版/解绑受控文件 → 刷新 → 发布新修订 → 证明旧实例继续冻结 → 停用/重新启用选择。验证权限、跨租户隔离、陈旧CAS、选择漂移、幂等重试、IN_PROGRESS、回滚及安全审计事实。
4. 在真实浏览器的320/768/1024/1440视口重复公开闭环，覆盖全部当前内置/增强控件、富文本、普通上传、`PmsFileArtifact`、iframe、API GET/POST、联动、校验、事件/函数/`parseFunc`、响应未知重试、`false/0/null/空数组`值、修订历史、停用/启用及旧实例不可变渲染。故意构造的目标API未授权、CORS/CSP/iframe失败必须保持为浏览器/目标系统失败，不得产生PLT或其他领域成功事实。
5. 使用获权只读用户、非创建者更新尝试及第二租户，验证UI动作投影和服务端拒绝均无成功副作用。在`docs/engineering/evidence/f-plt-002-browser-evidence.json`及`docs/engineering/evidence/f-plt-002-browser/`记录HTTP状态、最终刷新状态、意外console/page/request错误数及版本化截图；预期负向请求必须标注，意外错误必须为零。
6. 在同一浏览器/应用运行中打开并操作既有BPM表单列表/编辑器、旧PMS表单模板/实例页面和API，以及旧需求分析/项目入口，沿用其原权限。记录对已审计旧后端、前端和菜单路径执行`git diff --exit-code af428bab --`的结果，并证明新PLT命令未改变旧表行数和旧API响应。
7. 运行受管规格基线校验、仓库基线规则、架构/模块边界、迁移检查及`git diff --check`。确认候选中没有WorkBinding/PRE-04消费代码、动态表单公共模块API、旧实现修改、角色授权或部署、系统集成测试、用户验收测试、发布声明。

全部证据通过后，更新唯一F-PLT-002检查点（不超过300个中文字符），只暂存本功能实施/证据文件，形成一个实施候选提交，并以该精确提交申请独立实施完成评审。只有裁决GO后才能前向回写状态和追溯。

## 五、计划自检

- **规格覆盖：** 集成路径覆盖BR/AC-FPLT002-001至012：模板身份、不可变修订、独立可用性、完整FormCreate、人工选择、冻结实例、普通值/FileArtifact值、授权与动作、幂等/CAS/审计、迁移、响应式浏览器验收及旧实现不变。
- **所有权：** PLT拥有模板/修订/实例真值；F-PLT-001拥有Artifact/Version/Reference及MinIO/扫描事实；SYSTEM提供权限事实。任何模块均不读取其他上下文的表，也不创建空公共API。
- **状态与锁序：** 命令保持`DynamicFormTemplate → 当前DRAFT/PUBLISHED Revision → DynamicFormInstance → 文件Provider → Artifact → Version → Reference`。已发布修订字段没有写路径；模板可用性不改变历史修订或实例事实。
- **复用：** 直接复用稳定FormCreate基础设施，只把已审计的页面级行为复制到新PLT目标；BPM、旧PMS表单和需求分析保持不变。
- **高信任边界：** 当前完整设计器界面继续可用。服务端只校验结构、身份和版本，不执行、代理、剥离或静默改写管理员配置的浏览器代码。
- **执行粒度：** 只有两个可执行检查项：一个完整实施闭环和一次整体验证/送审；任何微功能均不作为独立交付。

## 六、技术计划门禁

当前状态：`PENDING_INDEPENDENT_REVIEW`。只有针对本计划已提交版本取得独立GO后，才允许进入产品实施。
