# F-SOL-003 需求分析动态表单与版本冻结功能规格

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO（整改提交 4d04dbd63bbd01683416563bece31da6cd53f849）`
> Requirement：`PRE-04（V1/P0）`
> Owner Context：`SOL（交付准备与方案）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-003`、`F-PROJ-007`、`F-PLT-001`、`F-PLT-002`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 已取消输入：2026-08-27旧Technical Plan及其Implementation审查

## 1. 目标

在SOL建立PRE-04需求分析业务真值，并把已完成的F-PLT-002共享动态表单作为唯一表单载体。项目模板管理员在WorkBinding配置中选择一个明确的已发布PRE-04兼容动态表单修订；项目实例化时自动冻结该修订，项目经理进入需求分析后直接填写，不再进行项目内人工选模。

每个PRE-04业务版本一对一组合一个PLT动态表单实例：PLT拥有冻结Schema、普通值和受控FileArtifact引用，SOL拥有草稿/完成、当前有效版本、完成门禁、历史、对比、权限、审计及SCH-01稳定事实。系统保留至多一个可编辑草稿和一个当前有效完成版；完成版不可变，后续修改从当前有效版创建下一草稿并克隆PLT实例。

## 2. Scope

### 2.1 包含

- 项目模板的PRE-04 WorkBinding配置选择并冻结明确的PLT模板修订；
- PRE-04兼容配置：11项固定核心富文本字段、每项对应的受控附件字段，以及项目背景、项目目标、网络拓扑三个不可取消的必填项；
- 一个SOL需求分析版本与一个PLT业务动态表单实例的一对一组合；
- `DRAFT -> COMPLETED`、草稿/有效版双轴、完成版不可变、下一草稿克隆、历史和版本对比；
- 项目经理创建、填写、完成，获授权成员只读；
- `If-Match`、`Idempotency-Key`、稳定锁序、平台审计、失败关闭和响应式项目工作区；
- `RequirementAnalysisFactApi.inspect/lockAndRevalidate`，向后续SCH-01提供明确完成版本及其冻结表单事实；
- 在F-PLT-002形成真实跨模块调用方后增加最窄业务实例API和SOL策略Provider。

### 2.2 不包含

- PRE-03、PRE-05、SCH-01方案聚合、预填、人工覆盖、输入差异确认、方案版本或审批；
- 第二套表单设计器、SOL自有Schema/字段目录、SOL与PLT双写表单值或文件引用；
- 项目内人工模板选择、运行时换模、修改已完成版本所绑定的PLT实例；
- 服务端执行FormCreate脚本、代理任意URL，或把事件/API/iframe成功解释为PRE-04完成；
- 修改、迁移、双写或退役旧`pms_eng_requirement`、旧`pms_eng_form_*`及其页面、接口、数据和超级管理员访问；
- Deployment、SIT、UAT、Release和历史数据迁移。

## 3. 业务规则

### BR-FSOL003-001 WorkBinding自动选模与冻结

- 项目模板中的PRE-04任务唯一使用`BUSINESS_OBJECT/SOL/REQUIREMENT_ANALYSIS/PRE_04_REQUIREMENT_ANALYSIS`目标；其`bindingConfig`只冻结一个明确的`dynamicFormTemplateId/dynamicFormTemplateRevisionId/revisionNo/revisionFactVersion`，不再保存`catalogCode/catalogVersion/extensionItems[]`或第二份Schema。
- 项目模板配置界面通过PLT公开选择查询选择当前`ENABLED`且存在当前`PUBLISHED`修订的模板；这是模板管理员配置动作，不是项目用户运行时选模。
- 项目模板发布前，PROJ通过`DynamicFormBusinessInstanceApi.inspectRevisionForUsage`取得明确修订事实并要求SOL注册的`DynamicFormBusinessObjectPolicyProvider`判定`SOL/REQUIREMENT_ANALYSIS`兼容；空、草稿、停用、非当前发布修订、事实变化或不兼容均整版拒绝。
- 项目实例化时把上述明确修订事实冻结进ProjectTask执行契约。SOL创建首个草稿时使用`ProjectWorkBindingFactApi.inspect/lockAndRevalidate`取得该冻结事实；空、多记录、越租户、版本变化或事实非法失败关闭。请求不得自报模板修订。
- WorkBinding只决定明确模板修订，不授予PLT或SOL权限。模板后来停用或发布新修订不改变已经冻结的项目执行契约和既有需求分析版本。

### BR-FSOL003-002 PRE-04模板兼容规则

- 兼容模板必须包含11个稳定核心富文本字段：`PROJECT_BACKGROUND`、`PROJECT_OBJECTIVE`、`NETWORK_TOPOLOGY`、`TRANSMISSION_REQUIREMENT`、`TRAFFIC_REQUIREMENT`、`BUSINESS_REQUIREMENT`、`IP_PLANNING`、`REDUNDANCY_REQUIREMENT`、`SECURITY_PROTECTION`、`OPERATIONS_REQUIREMENT`、`LOGGING_REQUIREMENT`。
- 三个必填核心字段为`PROJECT_BACKGROUND/PROJECT_OBJECTIVE/NETWORK_TOPOLOGY`；模板不得删除、重命名、改义、改为非富文本或降低必填。
- 11个核心字段必须各自存在且仅存在一个对应`PmsFileArtifact`受控字段，编码固定为`{CORE_CODE}__ATTACHMENTS`；缺失、重复、类型错误或改码均不兼容。附件槽位必须存在，但附件值可为空，模板不得把附件数量定义为PRE-04完成必填。
- 模板可增加其他FormCreate字段、布局、联动、校验、事件、函数、API选择器、iframe、普通上传和受控文件字段。SOL不复制这些定义，只按PLT冻结Schema渲染、保存和比较；普通上传URL/JSON不是受控FileArtifact证据。
- SOL策略Provider只验证PRE-04固定业务约束和字段稳定性，不重写完整FormCreate配置，也不建立组件、脚本、URL或事件白名单。发布配置仍受F-PLT-002高信任边界约束。

### BR-FSOL003-003 PLT业务实例组合

- 每个`sol_preparation` PRE-04版本必须唯一引用一个`plt_dynamic_form_instance`；PLT实例业务键固定为`ownerContext=SOL/objectType=REQUIREMENT_ANALYSIS/objectId={preparationId}`，用户REST不得自报该键。
- SOL根保存`dynamic_form_instance_id`，并继续保存冻结项目模板修订`template_revision_id`；二者含义不得混用。跨Context只保存稳定ID，不建立物理外键。
- 首次创建由SOL外层命令预分配`preparationId`和`dynamicFormInstanceId`，首次INSERT根时即写入非空实例ID和version=1；随后PLT在同一事务按`MANDATORY`插入该明确实例ID。不得由PLT另生成ID后回填SOL根，也不得为回填额外递增SOL版本；任一策略、修订或插入失败时根、实例、成功幂等和审计全部回滚。
- PLT是FormCreate config/rules、值、实例版本及`PmsFileArtifact`引用的唯一真值。SOL不得再把字段定义、正文值或附件向量写入`sol_requirement_analysis_section`、`template_snapshot`或其他副本。
- 查询由SOL先校验项目范围和业务状态，再通过`DynamicFormBusinessInstanceApi.inspectInstance`装配明确PLT实例事实。PLT接口不自行把实例保存解释为需求分析完成。

### BR-FSOL003-004 草稿保存与文件动作

- 项目经理只能修改当前`DRAFT`对应的PLT业务实例。SOL的`PATCH /preparations/{id}/form`接收普通字段部分值，以`If-Match`携带PLT实例版本、`X-SOL-If-Match`携带SOL根版本；先锁定SOL根及Owner策略，再由PLT按CAS更新实例，事务失败保持最近一次成功值。
- 字段缺失与显式`null/false/0/空字符串/空数组`保持可区分；未知字段和`PmsFileArtifact`字段伪造由PLT拒绝。完整FormCreate客户端校验不能替代服务端PRE-04完成校验。
- `PmsFileArtifact`继续使用`PLATFORM/DYNAMIC_FORM_INSTANCE/{instanceId}/FORM_FIELD_ATTACHMENT/{fieldKey}/{slotKey}`。文件命令由F-PLT-001执行；动态表单文件Provider必须先通过SOL业务Owner策略，再锁定PLT实例/修订及文件事实。
- 当前草稿文件上传、换版或解绑一旦由F-PLT-001成功提交即成为该PLT实例当前值，不再要求SOL保存第二份附件快照，也不引入`IN_SYNC/PENDING/UNKNOWN`双真值状态机。响应未知沿用原`slotKey`和Idempotency-Key，刷新后以PLT权威引用事实恢复。
- 完成版Owner策略只允许`READ/DOWNLOAD/PREVIEW`，拒绝`UPLOAD/REFERENCE/REPLACE/DETACH`；`ARCHIVE/INVALIDATE`不由PRE-04页面授权。模板事件、函数和接口仍以当前用户浏览器权限运行，不能绕过SOL/PLT命令授权。

### BR-FSOL003-005 草稿与当前有效版本双轴

- `status_code`只表达`DRAFT/COMPLETED`；`draft_marker/effective_marker`是独立唯一事实。项目可同时拥有一个当前草稿和一个当前有效完成版。
- 初次完成时当前草稿原子转为`COMPLETED`、清除`draft_marker`并取得`effective_marker=1`。
- 创建下一草稿必须以当前有效完成版为`source_preparation_id`，预分配新根和新PLT实例ID并以非空引用创建`businessVersion+1`根，再调用PLT `cloneBusinessInstance`按`MANDATORY`写入该明确实例ID：复制冻结修订和普通值，为每个受控文件槽位生成新实例下的独立FileReference并复用同一不可变FileVersion。旧完成版实例和引用保持不变，新草稿可独立换版或解绑。
- 新草稿完成时先校验草稿与旧有效版仍为期望版本，再原子清除旧有效标记并把草稿设为新的有效完成版。失败时旧有效版、草稿和两个PLT实例均保持原状。
- 完成版SOL根和PLT实例不可修改或删除；旧根只允许在下一版本完成事务中清除`effective_marker`。

### BR-FSOL003-006 完成校验与锁定重验

- 完成要求当前项目经理、项目`ACTIVE`、当前草稿、合法冻结WorkBinding，并同时提供`If-Match=期望PLT实例版本`和`X-SOL-If-Match=期望SOL根版本`；任一版本陈旧均在业务写前返回版本冲突。首次完成另要求S1，已有有效版后的修订允许在ACTIVE项目继续。
- 三个核心富文本字段规范化后必须有可见文本；仅空标签、`&nbsp;`或零宽空白视为空。模板内其他声明式必填、类型、长度/范围、正则和枚举校验由PLT按冻结schema形成服务端校验事实；客户端事件、函数、parseFunc、远程API结果或iframe状态只影响浏览器交互，不成为服务端完成门禁，也不得降低三个核心业务必填。
- 查询基于SOL根和PLT只读事实返回`completionBlockers[]`，至少区分`REQUIRED_VALUE_MISSING`、`FORM_VALUE_INVALID`、`CONTROLLED_FILE_INVALID`和`FACT_PROVIDER_UNAVAILABLE`；不得暴露Provider原始异常。阻断非空或事实未知时不投影`COMPLETE`。
- 完成锁序固定为：全部PROJ项目/参与/WorkBinding事实 -> SOL根、当前草稿/有效版及动态表单Owner策略 -> PLT实例/冻结修订 -> F-PLT-001 Artifact→Version→Reference。所有SOL Provider调用必须在首个PLT锁前完成，之后禁止回调SOL。
- `lockAndRevalidateInstance`比较明确实例ID、Owner键、模板/修订、引擎版本、实例版本、规范化值向量和全部当前受控文件事实。任一字段、引用、FileVersion、可用性、范围或版本变化均在SOL完成CAS前失败关闭。
- 校验和重验全部成功后才冻结完成元数据并切换有效标记；失败保留最近成功草稿，不向SCH-01提供该草稿。

### BR-FSOL003-007 历史、对比与项目档案

- 查询返回当前有效版、可选当前草稿和完成历史。历史按`businessVersion DESC,id DESC`稳定游标分页；获授权成员可只读当前有效版和完成历史。
- 版本对比只允许同租户、同项目的两个完成版，或当前草稿与其来源完成版。SOL通过PLT实例事实按稳定`fieldKey`比较Schema摘要、普通值和受控文件引用，不持久化第二份差异表。
- 上级项目角色只有在PROJ明确授予后代读取范围时才能查看；角色名称不扩权。平级、无范围及跨租户请求拒绝或按数据范围语义返回不可见。
- 敏感附件下载必须同时通过SOL对象范围、动态表单Owner策略和F-PLT-001 `DOWNLOAD`，使用短时票据并审计；SOL不返回长期URL。

### BR-FSOL003-008 SCH-01稳定事实边界

- `RequirementAnalysisFactApi.inspect/lockAndRevalidate`只返回明确`COMPLETED`版本；默认当前有效版，显式preparationId可读取同项目历史完成版。草稿不能作为方案输入。
- 事实包含projectId、preparationId、businessVersion、contentVersion、projectVersion、WorkBinding版本、dynamicFormInstanceId、冻结模板修订、引擎版本、完成元数据、稳定字段值和受控文件事实；不包含SCH草稿、预填结果、输入变化决定或文件长期URL。
- 锁定重验遵循`PROJ -> SOL -> PLT动态实例 -> F-PLT-001`顺序并比较调用方inspect所得完整期望向量。历史完成版不会仅因失去`effective_marker`而失效，但返回最新当前有效版本标识。
- 本Feature不创建SCH引用、不标记输入变化、不执行预填，也不发布跨Context业务事件。后续SCH-01自行持久化所选版本及处理差异。

### BR-FSOL003-009 权限、幂等、审计和失败语义

| 能力 | 功能权限码 | ProjectStageScope | 主体约束 |
|---|---|---|---|
| 查看当前有效版、历史和对比 | `pms:requirement-analysis:query` | `PROJECT_VIEW` | 当前项目获权成员 |
| 创建、填写、完成草稿 | `pms:requirement-analysis:manage` | `PROJECT_MANAGE` | 当前有效项目经理 |
| 下载/预览附件 | SOL查询权限 + PLT文件权限 | `PROJECT_VIEW` | SOL、动态表单和F-PLT-001三重重验 |

- `allowedActions`基于功能权限、PROJ范围/经理事实、SOL状态、PLT实例事实和`completionBlockers`保守投影；命令端锁定重验，前端不得推导角色授权。
- 业务实例动作值域封闭为`CREATE/READ/PATCH/COMPLETE/CLONE_SOURCE/CLONE_TARGET/FILE_READ/FILE_WRITE`，修订用途为`REVISION_BINDING_PUBLISH/REVISION_FROZEN_USE`。查询/inspect必须冻结实际动作；Owner Provider和PLT持锁重验必须比较同一动作，禁止从READ升级为PATCH/COMPLETE或混用克隆源/目标权限。具体状态/权限映射以机器契约为准。
- 初始化、创建下一草稿和完成使用SOL外层`Idempotency-Key`。PLT只读inspect不持锁；创建、修改、克隆及两类持锁重验统一使用事务传播`MANDATORY`，无SOL外层事务即拒绝，不嵌套`PlatformCommandExecutionApi`、不建立第二幂等记录；同键重放、异载荷冲突和`IN_PROGRESS`沿用平台既有语义。
- 普通值PATCH使用PLT实例`If-Match`和SOL根版本；文件命令使用F-PLT-001自身稳定意图键。任一CAS或外部事实失败不得部分覆盖。
- 成功审计记录项目、需求分析版本、PLT实例/修订、动作、前后状态/版本、变化字段键、文件引用摘要、operationId、主体和时间；不得复制完整富文本、函数源码、接口响应或文件正文。

### BR-FSOL003-010 现有实现吸收与旧功能保持

- 直接复用当前F-SOL-003候选中的`sol_preparation`双轴版本、历史/对比入口、项目范围与经理授权、幂等/审计骨架、SCH事实接口边界及响应式项目工作区模式。
- 调整当前候选的WorkBinding解析、命令/查询/事实实现和项目工作区表单区，使其组合PLT业务实例；增强进入新的API、Provider、组件或适配层，不原位改造旧需求分析实现。
- `sol_requirement_analysis_section`、固定`RequirementAnalysisCatalog`运行时展开、SOL正文/附件快照及附件同步状态不再是新流程真值。已执行的候选迁移不原位修改；实施只以前向迁移增加组合字段并停止新流程写入该表，不把现有候选数据自动迁移成正式业务数据。
- 旧`pms_eng_requirement`及其后端、前端、CRUD、状态、菜单、数据和内置`super_admin`访问完全不变；不迁移、不双写、不解释为PRE-04当前真值。

## 4. API与模块契约

### 4.1 用户REST

所有路径继承`/api/v1/pms`并返回统一`CommonResult`。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/preparations?projectId={id}&type=PRE_04` | `GET` | 当前有效版、可见草稿摘要、PLT实例摘要、阻断和允许动作 |
| `/preparations` | `POST` | 以冻结WorkBinding自动创建首个SOL草稿和PLT实例；`Idempotency-Key` |
| `/preparations/{id}` | `GET` | 返回明确业务版本及其完整PLT冻结Schema、值、文件事实和版本 |
| `/preparations/{id}/form` | `PATCH` | 普通字段部分PATCH；`If-Match`使用PLT实例版本，`X-SOL-If-Match`使用SOL根版本 |
| `/preparations/{id}/actions/submit` | `POST` | 锁定重验完整PLT实例后完成并切换有效版；`Idempotency-Key`、`If-Match=PLT实例版本`、`X-SOL-If-Match=SOL根版本` |
| `/preparations/{id}/actions/create-draft` | `POST` | 从当前有效版克隆下一SOL草稿及PLT实例；`Idempotency-Key`、`If-Match=来源PLT实例版本`、`X-SOL-If-Match=来源SOL根版本` |
| `/preparations?projectId={id}&type=PRE_04&history=true` | `GET` | 完成历史稳定游标分页 |
| `/preparations/{id}/compare?targetPreparationId={id}` | `GET` | 按字段键和值/文件事实返回差异，不持久化 |

已取消候选的`PATCH /preparations/{id}/items/{sectionId}`不属于新锁定契约；它没有正式发布消费者，不建立兼容层。

### 4.2 PLT跨模块契约

F-PLT-002在`pms-module-platform-api`增加`DynamicFormBusinessInstanceApi`：

- `inspectRevisionForUsage/lockAndRevalidateRevisionForUsage`：返回并重验明确发布修订、完整Schema和兼容结果；
- `createBusinessInstance`：在调用方事务内为受信Owner键创建冻结实例；
- `inspectInstance/lockAndRevalidateInstance`：返回/锁定实例、修订、值和受控文件完整事实；
- `patchInstanceValues`：按CAS部分更新普通值，不接受文件字段；
- `cloneBusinessInstance`：复制冻结修订和值，并为新实例复用不可变FileVersion建立独立引用。

同时增加`DynamicFormBusinessObjectPolicyProvider`，由SOL实现`SOL/REQUIREMENT_ANALYSIS`的修订兼容、Owner读写动作、项目范围、业务状态和scopeVersion策略。所有API使用受信tenant/actor，禁止用户请求自报Owner。动作值域与API映射封闭；创建/修改/克隆和持锁重验使用`MANDATORY`，禁止`REQUIRED`自开事务、`REQUIRES_NEW`、嵌套平台幂等和由PLT构造SOL事件。

### 4.3 领域边界

- PROJ拥有项目模板、WorkBinding、项目范围和参与事实；
- PLT拥有动态表单模板/修订/实例Schema、值和受控文件组合；
- SOL拥有PRE-04生命周期、版本、完成门禁、历史、对比和SCH事实；
- F-PLT-001拥有Artifact/Version/Reference；
- 任一模块不得依赖其他Context的Service、Mapper、DO或业务表。

## 5. 数据与迁移边界

- `sol_preparation`前向增加`dynamic_form_instance_id`并为PRE-04要求非空；`template_revision_id`继续表示冻结项目模板修订，不能改义为PLT修订。
- `plt_dynamic_form_instance`既有Owner三元组用于`SOL/REQUIREMENT_ANALYSIS/{preparationId}`，不新增SOL表单值表；实例自身冻结PLT template/revision。
- SOL与PLT之间只保存稳定ID，不建物理外键；同租户Owner唯一性由PLT既有唯一键保证。
- `sol_requirement_analysis_section`保留为已取消候选迁移形成的非当前表，不再写入、查询或对外暴露；不得把它与PLT实例双写。
- 使用实施时下一未占用Flyway版本，提供PRE-04标准动态表单模板/已发布修订、WorkBinding绑定及无匹配/停用/不兼容示例；不得修改V1至当前已执行迁移。

## 6. UI

- 项目工作区保留“需求分析”入口，清晰区分当前有效完成版和草稿；项目用户不显示模板选择步骤。
- 表单区新建SOL包装组件，直接复用F-PLT-002 FormCreate codec/renderer和`PmsFileArtifact`控件；不得修改旧需求分析页面承载新功能。
- 显示冻结模板名称/修订、完成阻断、历史和版本对比；项目经理可填写、完成或创建下一草稿，获授权成员只读。
- 本地未保存普通值阻止切换版本/页面；文件命令响应未知时沿用原槽位并刷新PLT权威事实。320/768/1024/1440无页面级横向溢出。

## 7. 验收标准

- `AC-FSOL003-001`：项目模板管理员从PLT已启用发布修订中为PRE-04 WorkBinding选定明确修订；发布和项目实例化均冻结同一事实，项目用户无选模步骤。
- `AC-FSOL003-002`：模板缺任一11项核心、缺任一对应且唯一的`{CORE_CODE}__ATTACHMENTS`受控字段、三个必填被降低、核心/附件类型错误或修订非当前启用发布版时项目模板发布失败且零副作用；附件值允许为空，其他完整FormCreate能力不被SOL静默删除。
- `AC-FSOL003-003`：首次创建预分配SOL根ID与PLT实例ID，根首次INSERT即携带非空实例ID且version=1，PLT按`MANDATORY`插入同一ID；无回填CAS/额外版本递增。空/多绑定、无外层事务、事实变化、无权或非S1失败时两侧及成功事实均为零。
- `AC-FSOL003-004`：普通值部分PATCH保持`null/false/0/空字符串/空数组`语义，未知键和文件字段伪造失败；CAS冲突不覆盖最近成功值。
- `AC-FSOL003-005`：受控文件上传/换版/解绑以PLT当前引用为唯一真值，响应未知沿用原slot和幂等键恢复；SOL无附件快照或PENDING双写。
- `AC-FSOL003-006`：完成请求同时携带期望PLT实例版本与SOL根版本；任一陈旧均冲突且零写。两版本命中后才重验11项核心、11个唯一受控附件槽位、三个必填、完整实例和值/文件事实；任一缺失、失效、未知或变化时不投影/不执行COMPLETE且无成功副作用。
- `AC-FSOL003-007`：完成形成不可变有效版；创建下一草稿克隆PLT实例并复用不可变FileVersion形成独立引用，旧完成版不可换版/解绑，新草稿可独立修改。
- `AC-FSOL003-008`：新草稿完成原子切换有效标记；同项目至多一个草稿和一个有效版，历史SOL根及PLT实例不可变。
- `AC-FSOL003-009`：历史分页和对比按稳定fieldKey覆盖Schema摘要、普通值及受控文件变化，不建立第二差异真值。
- `AC-FSOL003-010`：权限、项目范围、当前经理、跨租户、同键重放/冲突/IN_PROGRESS及并发单胜均由服务端失败关闭；inspect与持锁重验动作完全相同，READ→PATCH/COMPLETE升级及CLONE_SOURCE/CLONE_TARGET互换均拒绝且零成功副作用。
- `AC-FSOL003-011`：真实MySQL证明SOL根与PLT实例创建/克隆/完成同事务；批量文件克隆N个新引用产生N个事件，重放不增，任一失败根、实例、引用、成功幂等/审计/Outbox均为零。
- `AC-FSOL003-012`：锁序证明全部PROJ和SOL Provider/Owner事实先于PLT实例/修订及全部文件锁；取得首个PLT锁后无SOL回调，反序尝试失败关闭。
- `AC-FSOL003-013`：`RequirementAnalysisFactApi`只返回/锁定明确完成版本和完整PLT事实；草稿、无权、旧期望或失效文件失败且零写，历史完成版明确返回`isCurrentEffective=false`。
- `AC-FSOL003-014`：真实浏览器完成“WorkBinding自动选模后的初次填写→文件→完成V1→克隆V2→修改完成→历史对比”，并覆盖未保存值阻断、文件响应未知、无权只读/拒绝和四视口，console/page/request意外错误为零。
- `AC-FSOL003-015`：旧需求分析、旧工程表单和BPM实现相对基线零修改；新SOL组件复用共享运行时，当前候选版本化/权限/历史能力有逐项吸收证据。
- `AC-FSOL003-016`：不宣称SCH-01、Deployment、SIT、UAT或Release完成；旧Technical Plan和旧Implementation审查不能驱动实施。

## 8. Feature Ready检查

| 检查项 | 当前结论 |
|---|---|
| PRE-04目标、11项核心、三个必填和版本规则 | PASS（PRD V1.8 §5.2.4） |
| F-PLT-002基础先行、WorkBinding自动选模、项目内无人工选模 | PASS（需求方确认方案A） |
| PLT表单真值与SOL业务真值唯一分工 | PASS（本候选） |
| 跨模块API、Owner策略、事务和锁序 | PASS（本候选） |
| 现有版本化成果吸收与旧实现保持不变 | PASS（本候选） |
| 独立Feature Ready裁决 | PASS（GO；整改提交`4d04dbd63bbd01683416563bece31da6cd53f849`） |

结论：`BASELINE / READY`。独立Feature Ready已批准本次F-PLT-002跨Context聚焦修订及F-SOL-003组合边界；锁定本次规格提交并同步NPDMS后，基于该新基线生成全新的中文Technical Plan并独立送审。本裁决不授权沿用旧计划、产品实施、部署、系统集成测试、用户验收测试或发布。
