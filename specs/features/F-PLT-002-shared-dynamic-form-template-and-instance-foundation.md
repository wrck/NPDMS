# F-PLT-002 共享动态表单模板与实例基础能力功能规格

> 文档状态：`BASELINE（F-SOL-003真实调用方聚焦前向修订已就绪）`
> 原功能就绪：`READY / GO NPDMS-FPLT002-FEATURE-READY-20260828-01-R1`
> 聚焦修订就绪：`READY / GO（整改提交 4d04dbd63bbd01683416563bece31da6cd53f849）`
> 原基础闭环实施：`IMPLEMENTATION_COMPLETE / NPDMS-FPLT002-IMPLEMENTATION-20260827-02-R2`
> 本次修订门禁：`PENDING`；仅新增业务实例公共边界，不重开已完成的手工动态表单闭环
> 主需求：`SOL-01（V2/P1）`
> 支撑需求：`PRE-04（V1/P0）`、`PM-03（V1/P0）`、`PM-11（V1/P1）`
> 所有者上下文：`PLT（基础平台动态表单能力）`
> 前置功能：`F-PLT-001`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`及本功能随附的聚焦前向修订
> 需求方决策：先建设不裁剪FormCreate能力的共享模板配置与手工实例闭环，再接入WorkBinding和PRE-04版本化
> 技术计划：功能就绪独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

在基础平台建立可独立使用、可被后续业务模块复用的动态表单能力：获权管理员使用仓库已经装配的完整FormCreate设计器配置模板，发布不可变模板修订；普通获权用户浏览、预览并手工选择当前可用模板，创建冻结到明确修订的表单实例，动态渲染、填写、保存和刷新恢复。

原功能已闭合“配置模板 → 发布修订 → 手工选模板 → 创建实例 → 动态填写 → 保存并刷新”的正向链路。F-SOL-003现已形成第一个真实跨模块调用方，本次聚焦前向修订仅增加业务实例公共边界，使PRE-04能够组合已经完成的共享能力；不得把PRE-04生命周期下沉到PLT，也不得重开或改写手工实例闭环。

## 2. 范围

### 2.1 包含

- PLT拥有的动态表单模板、草稿修订、不可变已发布修订和当前发布指针；
- 模板列表、元数据、完整FormCreate设计器、预览、创建下一草稿、发布、停用和重新启用；
- 首版不裁剪设计器：保留当前FormCreate全部内置控件及仓库增强控件，保留iframe、接口选择器、联动、事件、函数和自定义解析函数配置；
- 当前可用已发布模板的手工选择，以及冻结明确模板修订的PLT手工表单实例；
- 实例动态渲染、普通JSON值填写、CAS保存、刷新恢复和服务端`allowedActions`；
- 注册`PmsFileArtifact`受控文件字段，同时保留现有普通上传、图片上传等FormCreate控件；
- 租户隔离、功能权限、模板发布者高信任边界、幂等、并发、平台审计和响应式页面；
- 设计前旧实现审计已完成并锁定于[`F-PLT-002-legacy-form-reuse-audit.md`](F-PLT-002-legacy-form-reuse-audit.md)；实施逐项按`DIRECT_REUSE / COPY_THEN_ENHANCE / DO_NOT_REUSE`映射执行，旧实现保持不变。
- 面向已确认的F-SOL-003真实调用方，新增窄`DynamicFormBusinessInstanceApi`与`DynamicFormBusinessObjectPolicyProvider`：按业务Owner创建、读取、修改、复制和锁定重验动态表单实例，且继续复用同一PLT实例真值与F-PLT-001文件引用事实。

### 2.2 不包含

- 除F-SOL-003已锁定PRE-04组合边界外的WorkBinding自动匹配、ProjectTask通用工作台适配或任务完成判定；
- PRE-04生命周期、十一项业务语义、项目经理权限、完成版本、历史对比和SCH-01输入引用；这些仍全部由SOL拥有；
- SCH-01/SCH-04、IMP-01、ACC-02、CUT-03/CUT-07/CUT-09/CUT-10的业务模板、规则、状态或实例接入；
- 运行时表单设计器白名单、iframe域名白名单、接口目录白名单、事件/函数静态分析、沙箱执行或模板审批流；后续收紧必须另行前向修订；
- 服务端执行模板脚本、服务端代理模板配置的接口请求，或把模板事件执行成功解释为业务命令成功；
- 动态表单实例提交/审批/完成状态机、实例不可变业务版本、实例对比、批量导入导出或模板删除；
- 修改或迁移BPM表单、旧`pms_eng_form_template/pms_eng_form_instance`、旧需求分析页面、类、接口、数据、菜单和权限；
- 部署、系统集成测试、用户验收测试、发布及历史数据迁移。

## 3. 业务与实现规则

### BR-FPLT002-001 模板身份、修订和可用性

- `DynamicFormTemplate`是租户内稳定模板身份，模板编码唯一；名称、分类和说明属于当前元数据，按`If-Match`更新并审计，不改变已发布修订内容。
- 新建模板时以`DISABLED`可用性原子创建`revisionNo=1`的唯一当前`DRAFT`修订。草稿可保存FormCreate表单配置和规则；同一模板至多一个草稿。发布不会隐式启用，只有明确启用命令才能进入手工选择。
- 发布把期望草稿原子转为`PUBLISHED`并切换模板的唯一当前发布指针。已发布修订的配置、规则、引擎版本、发布者和发布时间不可更新或删除；后续修改从当前发布修订复制成下一草稿。
- 模板可用性独立取`ENABLED/DISABLED`。停用只阻止新实例选择和创建，不改变已发布修订，也不影响既有实例按冻结修订查看和保存；重新启用后继续以同一当前发布修订供选择。
- 发布新修订只影响其后新建实例；既有实例不自动升级、不重解释、不改写值。没有当前已发布修订的模板不能启用供选。

### BR-FPLT002-002 完整FormCreate配置与首版不收紧

- 新页面必须复用仓库现有`@form-create/designer`、`@form-create/element-ui`、`useFormCreateDesigner`、编码/解码工具及全局组件注册，不复制或改写第三方设计器引擎。
- 发布修订完整冻结`formConfJson/formRulesJson/engineCode/designerVersion/rendererVersion`。首版不建立组件、属性、iframe URL、接口URL、HTTP GET/POST、事件或函数白名单，不删除FormCreate当前可见配置项。
- 当前仓库增强控件必须继续可用：富文本、普通文件/单图/多图上传、字典、用户、部门、区域、接口选择器、iframe；同时新增`PmsFileArtifact`控件。内置布局、输入、选择、联动、校验、事件和函数能力继续保留。
- 服务端发布边界只校验请求类型、JSON可解析、规则根为数组、配置根为对象、可填写字段键非空且不重复、引擎标识与当前支持版本存在；不解析、重写、净化或静默删除事件/函数/接口/iframe配置。客户端自动修复重复字段键不能替代服务端发布拒绝。
- 模板配置的事件、函数和接口解析函数是获权模板管理员发布的高信任客户端代码，在预览和实例页按FormCreate运行时于当前登录用户浏览器执行。它不能绕过后端接口鉴权、租户和数据范围；浏览器CORS、CSP及目标站点iframe策略仍真实生效。PLT不提供服务端脚本执行或任意URL代理。
- 外部接口响应和iframe内容不是PLT业务真值。实例保存只持久化最终提交的表单值；事件、函数、接口或iframe加载成功均不得自动形成其他Context业务成功事实。
- 将来增加限制时必须形成新规格和新模板修订；不得原位改写已发布修订或既有实例的冻结引擎/config/rules。

### BR-FPLT002-003 预览、手工选择与实例冻结

- 模板列表稳定分页并明确显示`DRAFT/PUBLISHED`修订、`ENABLED/DISABLED`可用性、当前发布修订号和允许动作。预览必须使用待预览的明确修订，不以模板当前指针替代请求修订。
- 手工选择只返回当前`ENABLED`且存在当前`PUBLISHED`修订的模板；返回模板ID、模板编码/名称、当前发布修订ID/号、引擎版本和展示摘要，不返回其他租户模板。
- 创建实例请求携带选择结果中的明确`templateRevisionId`和模板期望版本。服务端锁定模板及修订，确认仍为该模板当前发布修订且模板仍启用后，创建实例并冻结模板ID、修订ID/号和引擎版本；选择后模板被停用或发布指针变化时返回版本冲突，不静默改用新修订。
- 用户REST只创建`ownerContext=PLATFORM/objectType=MANUAL_DYNAMIC_FORM`的手工实例；owner/object/purpose不允许请求自报。F-SOL-003通过本次修订新增的Owner Provider和受信内部API接入，二者不得混用。
- 同一创建意图在响应未知后必须保留`Idempotency-Key`。同键同规范化载荷重放原实例，同键异载荷冲突，进行中重复返回既有平台稳定`IN_PROGRESS`业务错误且无第二实例。

### BR-FPLT002-004 实例动态渲染与保存

- 实例详情始终读取自身冻结修订并返回完整FormCreate config/rules、普通字段值、FileArtifact字段当前事实、实例版本和`allowedActions`；不回读模板当前发布指针解释既有实例。一个实例的全部`PmsFileArtifact`字段必须组装为一次F-PLT-001 `inspectReferenceSets`批量读取并按fieldKey映射，禁止逐字段查询。
- 普通字段值保存为JSON对象，字段缺失与显式`null/false/0/空字符串/空数组`保持可区分；服务端不把合法假值当作未填写。PATCH只更新请求中出现的普通字段，并拒绝实例没有冻结字段键的值。
- `PmsFileArtifact`字段不接受通过普通PATCH伪造文件值；它由专用控件调用F-PLT-001上传、换版、解绑和读取接口，实例详情按冻结字段键组装当前`ACTIVE`精确文件事实。
- 每次成功PATCH以`If-Match`递增实例版本并记录字段键级变化摘要；并发后提交者返回版本冲突，不覆盖先提交值。失败保持最近一次成功值。
- 本功能不提供实例提交、完成、删除或模板切换命令。一个实例创建后永久绑定原修订；需要不同模板时创建新实例。

### BR-FPLT002-005 FileArtifact字段与普通上传控件并存

- `PmsFileArtifact`字段在F-PLT-001中的业务键固定为`ownerContext=PLATFORM/objectType=DYNAMIC_FORM_INSTANCE/objectId={instanceId}/purposeCode=FORM_FIELD_ATTACHMENT/{fieldKey}/referenceKey={slotKey}`；`slotKey`为客户端为同一上传意图稳定保留的UUID。
- PLT的动态表单文件Provider只对当前租户、存在的实例、冻结修订中真实存在的`PmsFileArtifact`字段和当前主体动作授权。手工实例创建者在具备实例编辑及对应文件权限时可上传、换版和解绑；获权只读用户只能读取、下载或预览。
- 实例详情中的受控文件值仅投影`artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/status`，不返回存储键或永久URL。文件访问、扫描事实、版本、短时票据和审计继续由F-PLT-001负责。
- 现有FormCreate普通上传、图片上传控件继续保留，其URL/普通JSON值只作为表单普通值，不得被后续业务功能当作已冻结FileArtifact证据。需要业务附件版本和权限真值时必须选择`PmsFileArtifact`字段。
- 文件命令成功而普通值PATCH失败不会伪造实例保存；FileArtifact字段直接以当前PLT引用事实展示，响应未知按原slotKey重试。实例没有完成/发布状态，因此本功能不再复制一套附件快照或PENDING状态机。

### BR-FPLT002-006 权限与动作投影

| 能力 | 功能权限码 | 主体约束 |
|---|---|---|
| 查看模板及发布修订 | `pms:dynamic-form-template:query` | 当前租户获权主体 |
| 创建/编辑模板草稿 | `pms:dynamic-form-template:manage` | 当前租户获权模板管理员 |
| 发布、停用、启用 | `pms:dynamic-form-template:publish` | 当前租户获权模板发布者；属于高信任客户端代码发布权限 |
| 查看/选择/创建手工实例 | `pms:dynamic-form-instance:query/create` | 当前租户获权主体 |
| 编辑手工实例 | `pms:dynamic-form-instance:update` | 实例创建者且实例版本匹配 |
| 受控文件动作 | 实例权限 + F-PLT-001对应权限 | 动态表单文件Provider和F-PLT-001均通过 |

- 页面按钮不构成授权。查询返回的`allowedActions`按受信租户、功能权限、模板/修订/实例状态、创建者和版本精确计算；命令端在锁定后重新校验。
- 模板权限不授予业务Context权限，模板事件/接口选择器也不能获得当前用户原本没有的服务端操作权。请求不得自报tenantId、actorUserId、角色或权限。
- 不在本功能创建业务角色或默认授予普通角色；迁移只创建菜单与权限资源，内置超级管理员沿平台既有规则访问，其他角色由现有权限管理显式授权。

### BR-FPLT002-007 幂等、并发、审计与失败语义

- 新建模板、创建下一草稿、发布、停用/启用和创建实例使用平台幂等记录；同键同载荷重放原结果，同键异载荷冲突，`IN_PROGRESS`无成功副作用。模板草稿、模板元数据和实例PATCH使用`If-Match`。
- 稳定锁序为`DynamicFormTemplate -> current draft/current published revision -> DynamicFormInstance -> F-PLT-001 business namespace/file facts`。普通实例PATCH不调用文件服务；FileArtifact专用命令遵循F-PLT-001既有Provider→Artifact→Version→Reference顺序。
- 发布事务先验证期望模板/草稿版本和基本结构，再冻结修订、切换当前发布指针、写幂等成功和审计；任一步失败时旧当前发布修订继续有效，新修订保持草稿且不产生发布成功事实。
- 成功审计记录模板/修订/实例、动作、前后状态与版本、字段键变化摘要、引擎版本、operationId、主体和时间。审计不得复制完整函数源码、富文本、接口响应、iframe正文、表单完整值或文件正文。
- 权限、状态、版本、JSON结构或文件Provider失败不得产生成功幂等、发布指针切换、第二实例或普通值覆盖；事务回滚后按平台既有拒绝审计记录稳定错误码。

### BR-FPLT002-008 旧实现复用与后续接入边界

- 设计前审计已经完成，唯一实施映射见[`F-PLT-002-legacy-form-reuse-audit.md`](F-PLT-002-legacy-form-reuse-audit.md)：BPM的`fc-designer`、`useFormCreateDesigner`、FormCreate编码/解码与全局运行时装配直接复用；BPM页面级配置及保存/复制/恢复体验、旧PMS表单的FormCreate载荷/冻结意图/CAS与列表交互复制到新的PLT目标后增强；旧工程域状态机、原始JSON编辑、旧CRUD/API/表及业务条件不复用。
- 旧需求分析11项标签、Editor交互和项目入口的复用决策也已冻结：PLT只负责无损承载动态schema和富文本运行时，PRE-04业务模板与WorkBinding项目入口待F-SOL-003重规划后复制增强；F-PLT-002不提前硬编码业务字段或修改旧入口。
- 禁止直接改造旧实体、Service、Controller、API文件、路由或页面来承载本功能，禁止新旧双写、自动迁移或让旧表成为PLT新真值。旧功能、数据、菜单和内置超级管理员访问保持原状。
- F-PLT-002实施完成后，F-SOL-003必须基于该锁定能力重新修订功能规格和生成全新技术计划：PRE-04仍由项目WorkBinding自动确定并冻结模板，用户无项目内手工选模步骤；本功能的手工选择只是基础能力闭环和后续其他场景入口。
- 后续SCH、IMP、ACC、CUT消费者分别拥有自身业务状态、必填、评分、审批、完成和版本语义；共享动态表单只提供模板修订、渲染和值/文件字段载体，不得把通用实例保存解释为领域业务完成。

### BR-FPLT002-009 业务实例公共边界

- F-SOL-003是首个已确认的跨模块调用方。PLT在`pms-module-platform-api`提供`DynamicFormBusinessInstanceApi`，仅包含：明确发布修订用途检查与锁定重验、业务实例创建、读取、普通值CAS更新、整实例复制及完整实例锁定重验；调用方不得依赖PLT Service、Mapper或表。
- 用途检查区分`REVISION_BINDING_PUBLISH`与`REVISION_FROZEN_USE`：项目模板发布时要求修订仍为当前启用发布版；项目已经冻结后只重验明确不可变修订、用途兼容和事实版本，模板后来停用或发布新修订不得破坏既有项目创建业务实例。
- 业务实例继续写入`plt_dynamic_form_instance`，其Owner稳定键为`tenantId/ownerContext/objectType/objectId`。手工实例REST仍只创建`PLATFORM/MANUAL_DYNAMIC_FORM`，不能接收客户端自报Owner；受信业务API才可创建SOL等上下文实例。
- PLT只拥有冻结模板修订、完整FormCreate schema、普通值和受控文件组合事实。Owner Context拥有查看、编辑、完成、不可变、历史和用途兼容规则；PLT不得增加PRE-04状态或把实例保存解释为业务完成。
- `inspectInstance/lockAndRevalidateInstance`同时返回基于冻结schema和值计算的声明式校验结果。服务端只执行可稳定解释的必填、JSON类型、长度/数值范围、正则和枚举约束；浏览器事件、函数、parseFunc、远程API结果和iframe状态不作为服务端完成真值。消费Context可在此基础上增加自身业务必填，但不得把仅客户端函数校验宣称为服务端门禁。
- 业务实例动作值域封闭为`CREATE/READ/PATCH/COMPLETE/CLONE_SOURCE/CLONE_TARGET/FILE_READ/FILE_WRITE`，修订用途动作封闭为`REVISION_BINDING_PUBLISH/REVISION_FROZEN_USE`。每个API及文件Provider按机器契约映射唯一动作，inspect冻结该动作，持锁重验必须使用同一动作、主体、Owner和scopeVersion，禁止调用方在inspect后升级动作。
- 只读inspect不持锁；`lockAndRevalidateRevisionForUsage/createBusinessInstance/patchInstanceValues/cloneBusinessInstance/lockAndRevalidateInstance`及Owner持锁重验一律使用事务传播`MANDATORY`，无调用方事务必须拒绝。它们不建立第二幂等记录、不嵌套`PlatformCommandExecutionApi`、不使用`REQUIRES_NEW`；SOL外层命令负责业务幂等与业务审计，PLT只写自身实际文件引用产生的PLT事件。
- 创建与复制由调用方同时预分配SOL业务ID和PLT实例ID，并把非空实例ID随SOL根首次INSERT及业务API命令提交；PLT只插入该明确ID，不生成后回填SOL根、不额外递增SOL版本。任一失败时预分配ID可废弃，但SOL根、PLT实例及成功事实必须全部回滚。
- `cloneBusinessInstance`复制冻结修订和普通值，并为目标Owner创建独立FileReference指向来源不可变FileVersion。PLT内部复用F-PLT-001 `FileEventFactory.referenceAttached`并通过事务参与型`PlatformTransactionalOutboxWriter`写事件：每个实际新增引用恰一`FileReferenceAttached`，同目标同版本重放不新增，任一项失败时目标实例、引用、事件及外层成功事实共同回滚；禁止`REQUIRES_NEW`。

### BR-FPLT002-010 Owner策略、文件组合与锁序

- PLT定义`DynamicFormBusinessObjectPolicyProvider`，由消费Context按稳定Provider键实现。F-SOL-003实现`SOL/REQUIREMENT_ANALYSIS`，负责用途兼容、项目范围、当前经理、草稿可写、完成版只读及稳定`scopeVersion`；未知或不可用时失败关闭。
- 所有跨Context业务实例命令分两阶段：先按完整Owner稳定键排序完成全部Owner Provider检查或锁定重验；随后才按稳定顺序取得PLT实例/修订锁及F-PLT-001 `Artifact → Version → Reference`锁。取得首个PLT锁后禁止再次回调任何Owner Provider。
- 动态表单文件Provider处理业务实例时，必须先委托相同Owner Provider校验动作和scopeVersion，再进入F-PLT-001锁；手工实例仍沿用PLT自身创建者和功能权限规则。
- `lockAndRevalidateInstance`必须比较Owner、冻结修订、引擎版本、实例版本、规范化普通值及全部受控文件字段的完整ACTIVE集合。空集合具有明确空事实；新增、缺失、换版、解绑、状态或三段文件事实版本变化均冲突。

## 4. API契约

所有路径继承`/api/v1/pms`，使用统一`CommonResult`和稳定错误码；分页使用`pageNo/pageSize`及稳定`id`次序。输入、输出和物理稳定键详见机器契约。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/dynamic-form-templates` | `GET` | 分页查询模板、草稿/当前发布摘要、可用性和`allowedActions` |
| `/dynamic-form-templates` | `POST` | `Idempotency-Key`；创建模板及revision 1草稿 |
| `/dynamic-form-templates/{templateId}` | `GET/PATCH` | 查询详情；PATCH只改元数据并要求`If-Match` |
| `/dynamic-form-templates/{templateId}/revisions` | `POST` | `Idempotency-Key`；从当前发布修订创建唯一下一草稿 |
| `/dynamic-form-template-revisions/{revisionId}` | `GET/PATCH` | 查询明确修订；仅DRAFT允许PATCH完整FormCreate config/rules，要求`If-Match` |
| `/dynamic-form-template-revisions/{revisionId}/actions/publish` | `POST` | `Idempotency-Key + If-Match`；冻结修订并切换当前发布指针 |
| `/dynamic-form-templates/{templateId}/actions/{enable|disable}` | `POST` | `Idempotency-Key + If-Match`；只改变新实例可选性 |
| `/dynamic-form-templates/selection` | `GET` | 只返回当前启用且有当前发布修订的模板选择项 |
| `/dynamic-form-instances` | `GET/POST` | 分页查询本人可见手工实例；POST按明确当前发布修订幂等创建 |
| `/dynamic-form-instances/{instanceId}` | `GET/PATCH` | 返回冻结schema和值；PATCH普通字段要求`If-Match`，拒绝文件字段伪造 |

用户REST保持原样，不新增业务实例REST。F-SOL-003作为真实调用方后，新增`DynamicFormBusinessInstanceApi`及`DynamicFormBusinessObjectPolicyProvider`内部边界；SOL只能经该边界组合PLT实例，不能依赖PLT Service、Mapper或表。方法、载荷、事务与锁序以机器契约为准。

## 5. 数据与物理边界

机器契约：`specs/features/F-PLT-002-physical-contract.json`。

- 前向新建`plt_dynamic_form_template`、`plt_dynamic_form_template_revision`和`plt_dynamic_form_instance`；全部tenant-aware，业务唯一键和高频索引包含tenantId。
- 模板根保存当前发布修订指针和可用性；revision保存完整不可变FormCreate schema事实；instance保存冻结revision引用和普通字段值，不复制模板当前指针。
- 已发布修订不提供UPDATE/DELETE业务入口。实例不建立PLT业务状态机或历史表；保存通过version CAS。业务Context可用新Owner实例表达自身版本，但不得反向覆盖任何实例的冻结修订。
- 手工实例与业务实例共用`plt_dynamic_form_instance`；手工实例Owner由REST固定，业务实例Owner只由受信API建立。消费Context只保存`dynamicFormInstanceId`逻辑引用，不建立跨Context物理外键，不复制schema、值或附件真值。
- 不修改BPM、旧PMS表和已执行迁移；NPDMS实施时使用下一个未占用Flyway版本，并补菜单/权限及最小示例模板、启用/停用、无发布修订不参与选择等初始化数据。

## 6. UI

- 新建PLT动态表单模板列表、模板编辑器/预览和手工实例列表/选择/填写页面；不得复用旧路由或原位修改旧页面。
- 设计器直接复用现有`fc-designer`及`useFormCreateDesigner`，右侧配置、联动、事件、校验、录入和多端预览保持开启。
- 实例使用同一FormCreate renderer，保存后刷新必须恢复相同普通值和受控FileArtifact事实；`false/0/空数组`显示不丢失。
- 320/768/1024/1440无页面级横向溢出；窄屏模板列表与实例表单可重排，设计器在窄屏可明确提示使用桌面宽度而不能让实例填写页失效。
- 页面明确提示：模板事件/函数以当前用户权限在浏览器执行；普通上传控件不是受控FileArtifact证据；停用模板不影响既有实例。

## 7. 验收标准

- `AC-FPLT002-001`：新建模板以DISABLED原子产生revision 1草稿；同一模板只有一个草稿，模板编码租户内唯一，同键重放不重复，发布后仍须明确启用才可选择。
- `AC-FPLT002-002`：设计器保留FormCreate全部当前内置及仓库增强控件，iframe、任意配置的GET/POST接口选择器、联动、事件、函数和parseFunc可保存、重新打开、预览和在实例运行；服务端不静默删除配置。
- `AC-FPLT002-003`：发布后revision不可修改；创建下一草稿并发布只切换新实例当前指针，既有实例仍按原revision渲染和值不变。
- `AC-FPLT002-004`：停用模板立即从手工选择中消失且不能新建实例，既有实例仍可查看和保存；重新启用后恢复选择，不生成新revision。
- `AC-FPLT002-005`：用户从选择页预览并选择明确当前revision创建实例，动态填写各类普通值并保存；刷新后文本、富文本、选择、布尔false、数值0、空值语义和布局保持一致。
- `AC-FPLT002-006`：`PmsFileArtifact`字段通过F-PLT-001上传/换版/解绑并刷新显示精确事实；普通PATCH不能伪造该值，普通上传控件仍可用但界面和契约不把URL值宣称为受控文件证据。
- `AC-FPLT002-007`：无模板权限、无发布权限、非实例创建者、跨租户、陈旧If-Match、同键异载荷和进行中重复全部失败且无发布指针、第二实例或值覆盖副作用；服务端`allowedActions`与命令授权一致。
- `AC-FPLT002-008`：模板事件/API/iframe不能绕过目标后端鉴权；PLT不执行服务端模板代码、不代理URL，浏览器CORS/CSP/iframe拒绝被如实呈现且不伪造表单保存或其他业务成功。
- `AC-FPLT002-009`：真实MySQL验证唯一草稿、当前发布指针、不可变revision、模板停用、CAS、幂等、回滚、租户隔离及FileArtifact Provider；空库Flyway migrate/info/validate通过。
- `AC-FPLT002-010`：真实浏览器在320/768/1024/1440完成模板配置→预览→发布→启用→手工选择→实例填写→FileArtifact→保存→刷新闭环，并记录网络、console/page error和截图；无当前功能意外错误。
- `AC-FPLT002-011`：BPM、旧`pms_eng_form_*`和旧需求分析类、页面、接口、数据及功能保持不变；新实现位于新的PLT类/页面，代码和测试可逐项追到已完成审计的映射ID，增强只发生在复制的新实现或明确的新PLT组合层上。
- `AC-FPLT002-012`：不宣称WorkBinding、PRE-04版本化、SCH/IMP/ACC/CUT消费者、部署、系统集成测试、用户验收测试或发布完成；旧F-SOL-003技术计划不得继续使用。
- `AC-FPLT002-013`：F-SOL-003预分配PLT实例ID，通过`DynamicFormBusinessInstanceApi`在同一外层事务以`MANDATORY`创建、CAS修改、复制并锁定重验业务实例；无外层事务拒绝，用户REST和手工实例语义零变化，SOL不直读PLT表。
- `AC-FPLT002-014`：复制业务实例时，N个实际新FileReference产生N个`FileReferenceAttached`；同目标同版本重放事件不增；批量中途失败时目标实例、FileReference、Outbox及外层成功幂等/审计均为零。
- `AC-FPLT002-015`：跨Context命令按封闭动作映射冻结Owner策略，持锁阶段重验同一动作；先全量完成按Owner稳定键排序的Provider锁定重验，再统一获取PLT实例/修订与Artifact→Version→Reference锁，首个PLT锁后无Provider回调。动作升级、Provider未知、完整值或文件集合漂移均失败且零成功副作用。

## 8. 测试与证据

按用户指定方式先完成一个整体正向闭环，再集中执行完整验证，不以每个微步骤单独形成阶段性PASS。功能完成至少需要：模板/修订/实例应用测试、权限与幂等负向、真实MySQL迁移/事务/并发、FormCreate组件运行时测试、F-PLT-001字段集成、前端类型检查与构建、真实浏览器四视口闭环，以及独立代码复审。

旧实现审计已由[`F-PLT-002-legacy-form-reuse-audit.md`](F-PLT-002-legacy-form-reuse-audit.md)完成并成为锁定实现输入。实施完成证据必须证明三组旧路径相对实施基线零修改、旧路由/API/页面仍按原权限工作，并将新实现逐项追到审计映射ID；不得通过修改旧实现来使新测试通过。

## 9. 就绪定义

| 项目 | 当前状态 |
|---|---|
| 共享PLT Owner与业务Context状态边界 | PASS（本候选） |
| 模板、修订、实例、FileArtifact字段物理契约 | PASS（本候选） |
| 首版完整FormCreate设计器及高信任发布边界 | PASS（需求方已选择A，本候选固化） |
| 手工选择与实例保存正向闭环 | PASS（本候选） |
| WorkBinding/PRE-04及其他消费者排除边界 | PASS（本候选） |
| 设计前旧实现完整审计及保持不变/复制增强映射 | PASS（本候选审计附件、机器契约及NPDMS根级实施约束） |
| 独立功能就绪裁决 | PASS（`NPDMS-FPLT002-FEATURE-READY-20260828-01-R1`） |

结论：`BASELINE / READY`。功能就绪已由独立裁决 `NPDMS-FPLT002-FEATURE-READY-20260828-01-R1` 批准；须锁定本次规格提交并同步NPDMS后，全新生成并独立评审技术计划。本裁决不是技术计划、实施、部署、系统集成测试、用户验收测试或发布授权。
