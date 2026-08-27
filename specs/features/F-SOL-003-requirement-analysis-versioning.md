# F-SOL-003 需求分析在线填写与版本冻结 Feature Spec

> 文档状态：`IN_REVIEW`
> Feature Ready：`NOT_READY / PENDING_INDEPENDENT_REVIEW`
> Requirement：`PRE-04（V1/P0）`
> Owner Context：`SOL（交付准备与方案）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-003`、`F-PROJ-007`、`F-PLT-001`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 边界裁决：`GO / NPDMS-FSOL003-BOUNDARY-20260827-01`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

在SOL建立PRE-04需求分析业务真值：项目经理按项目冻结模板填写11项V1核心内容及批准扩展项，保存草稿并在必填内容和精确FileArtifact附件事实校验通过后生成不可变已完成版本。系统同时保留至多一个可编辑草稿和一个当前有效已完成版本，提供历史、版本对比及稳定只读/锁定事实，供后续SCH-01明确引用；不在本Feature实现方案预填、输入变化处理或审批。

## 2. Scope

### 2.1 包含

- 项目维度版本化PRE-04聚合，区分“当前可编辑草稿”和“当前有效已完成版本”；
- 11项固定V1核心内容：项目背景、项目目标、网络拓扑、传输需求、流量需求、业务需求、IP规划、冗余需求、安全防护、运维需求、日志需求；
- 项目背景、项目目标、网络拓扑三个不可取消的V1必填项；每项支持富文本内容和零到多个精确FileArtifact附件；
- 项目冻结模板中的批准扩展项；扩展项冻结字段编码、名称、类型、必填、字典选项版本和排序，不建设运行时Schema设计器；
- `DRAFT -> COMPLETED`、已完成版本不可变、从当前有效完成版创建下一草稿、历史查询与稳定版本对比；
- 当前项目经理创建、编辑和提交，获授权项目成员只读；上级项目范围、跨租户、敏感附件下载继续由既有PROJ/PLT契约控制；
- `If-Match`、`Idempotency-Key`、稳定锁序、平台审计、拒绝事实及响应式项目工作区入口；
- `RequirementAnalysisFactApi.inspect/lockAndRevalidate`，只向后续SCH-01提供明确已完成版本及冻结正文/附件事实。

### 2.2 不包含

- PRE-03物料换货、PRE-05工程交底书、SOL-01通用动态表单、PLT-01统一待办；
- SCH-01方案聚合、方案预填、人工覆盖、输入差异确认、新方案版本或方案审批；
- 任意脚本、表达式、用户自定义组件、运行时表单发布入口或V2`DynamicFormSchema`平台；
- 项目模板设计/发布界面、第二套任务树、`TASK_NATIVE`正文或直接写PROJ表；
- 文件正文、URL、对象存储位置、MinIO/INFRA事实；SOL只保存F-PLT-001精确引用；
- 旧`pms_eng_requirement`或`pms_eng_form_*`双写/自动迁移；AI-MIG-000、Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FSOL003-001 项目冻结模板与初始化

- 项目模板任务定义必须唯一生产`workBindingType=BUSINESS_OBJECT`、`targetContext=SOL`、`targetObjectType=REQUIREMENT_ANALYSIS`、`targetObjectKey=PRE_04_REQUIREMENT_ANALYSIS`。项目创建时将`catalogCode=PRE_04_REQUIREMENT_ANALYSIS/catalogVersion=1`、11项核心目录和`extensionItems[]`原样冻结到现有执行契约`binding_parameter_snapshot`。
- SOL使用现有`ProjectWorkBindingFactApi.inspect/lockAndRevalidate`按受信租户、projectId和上述目标四元组精确取得当前执行契约；空、多记录、越租户、版本变化或冻结配置非法均失败关闭。SOL不读取PROJ表。
- 核心项编码、名称、类型和三个必填规则由本Feature固定，模板不得删除、重命名、改义或降低必填。扩展项编码不得与核心项重复，字段类型封闭为`RICH_TEXT/TEXT/NUMBER/BOOLEAN/SINGLE_SELECT/MULTI_SELECT`；选择类在项目模板发布时通过SYSTEM公开`DictDataApi`校验字典及选项均启用，并冻结`dictionaryType`和按code排序的`code/label optionSnapshot`，运行期只认该快照。现有字典API没有版本事实，因此不保存或臆造`dictionaryVersion`；其他类型不得携带字典或选项。任意脚本、表达式、未知字段或重复编码均拒绝，SOL不得直读SYSTEM表。
- 首次创建要求项目`ACTIVE+S1`、当前项目经理和合法冻结绑定；同一项目最多一个PRE-04草稿。初始化原子创建`businessVersion=1`草稿及全部冻结章节，同键同载荷返回原草稿。
- 核心富文本与扩展文本按服务端固定安全规则规范化并过滤危险标记；模板或请求不能降低该规则。附件不是富文本内嵌URL，统一使用PLT精确引用。

### BR-FSOL003-002 草稿与章节保存

- 每个草稿精确包含11项核心章节及冻结扩展章节。核心章节字段类型固定为`RICH_TEXT`；每个章节均可保存零到多个附件引用。
- 项目经理只能修改当前`DRAFT`。章节PATCH按字段存在性更新内容或附件；空PATCH拒绝，未提交字段保持原值，显式空值按字段类型保存为空。完成版本没有更新或删除入口。
- PRE-04文件业务键唯一冻结为`ownerContext=SOL/objectType=REQUIREMENT_ANALYSIS_SECTION/objectId={sectionId}/purposeCode=SECTION_ATTACHMENT/referenceKey={slotKey}`。`slotKey`为非空UUID：同一章节新增附件意图创建一个新槽位并在响应未知时保留该键，原位换版沿用该键；同一章节内不得重复。Provider声明`cardinality=MULTIPLE`，未知用途、空键或其他对象类型失败关闭。
- 草稿附件冻结`artifactId+versionNo+referenceKey+fileFactVersion+scopeVersion`，不得保存URL、正文或INFRA对象键。草稿章节对当前项目经理允许`UPLOAD/REFERENCE/REPLACE/DETACH/READ/DOWNLOAD/PREVIEW`；完成章节仅允许`READ/DOWNLOAD/PREVIEW`，明确拒绝`UPLOAD/REFERENCE/REPLACE/DETACH`；`ARCHIVE/INVALIDATE`不由SOL页面授权。所有写动作要求`manage+PROJECT_MANAGE`和当前项目经理，读动作要求对应SOL/PLT功能权限与`PROJECT_VIEW`。上传/引用/解绑失败保留最近一次成功草稿和原附件集合，不产生半更新。
- 每次成功PATCH递增章节版本、聚合CAS版本和PRE-04专用`content_version`并记录前后摘要；同一聚合并发保存使用`If-Match`，后提交者冲突而非覆盖。完成后`content_version`永久冻结，不因以后清除`effective_marker`而变化。

### BR-FSOL003-003 草稿与当前有效版本双轴

- `status_code`只表达`DRAFT/COMPLETED`；`draft_marker`和`effective_marker`是两个独立唯一事实。项目可同时拥有一个当前草稿和一个当前有效已完成版本，历史完成版两个标记均为空。
- 初次完成时，当前草稿原子转为`COMPLETED`、清除`draft_marker`并取得`effective_marker=1`。已有有效版时，创建新草稿必须以该完成版为`source_preparation_id`，复制冻结模板和正文，并通过PLT公共`FileArtifactApi.attachExistingVersions`为每个新章节建立全新的精确FileReference：目标使用新sectionId和服务端生成的新`slotKey`，但指向来源引用已经锁定重验的同一不可变`artifactId+versionNo`。不得复用旧章节完整业务槽位，也不得重新上传同一文件；原有效版及其FileReference保持不变，新草稿可在自己的槽位独立换版或解绑。
- 创建下一草稿使用当前有效版`businessVersion+1`，同一项目不能并行存在两个草稿。模板后续变更不反向覆盖该项目已冻结的PRE-04目录；新草稿沿用来源完成版的冻结目录。新根行、章节和全部新FileReference在同一事务中成功或回滚，任一来源事实变化、目标策略拒绝或引用冲突均不得留下草稿、章节或新引用。
- 新草稿完成时按稳定锁序同时锁定草稿与当前有效版，先校验双方仍是期望版本，再清除旧有效标记并把草稿转为新的有效完成版；事务失败时旧有效版和草稿均保持原状。
- 已完成行的章节、正文、附件和完成元数据不可更新或删除；根行只允许在下一版本完成的原子事务中清除`effective_marker`，不得改变其业务内容。创建草稿、完成或并发失败均不得覆盖历史。

### BR-FSOL003-004 完成校验与文件冻结

- 完成命令要求当前项目经理、项目`ACTIVE`、当前草稿、期望聚合版本和合法冻结模板；首次完成仍要求项目处于S1，已有有效版后的修订允许在ACTIVE项目继续，以支持后续明确引用版本发生变化。
- 项目背景、项目目标和网络拓扑规范化后不得为空；模板标记为必填的扩展项按冻结字段类型校验。选择值必须命中冻结字典选项，未知值拒绝。
- 所有已保存附件必须在同一事务内通过F-PLT-001 `lockAndRevalidate`精确重验当前版本、业务引用、可用性和范围版本。失效、越权、版本变化、未完成上传或Provider异常均失败关闭，不生成COMPLETED版本。
- 网络拓扑正文必填；PRD未要求拓扑附件必有，因此所有章节均允许零附件，且模板扩展项不得新增附件必填语义；一旦提交引用，上传或重验失败仍阻断完成。
- 校验与文件重验全部成功后才冻结章节、完成时间/操作者和业务版本；失败保留最近成功草稿，记录稳定未通过项，不向SCH-01提供该草稿。

### BR-FSOL003-005 历史、版本对比与项目档案

- 查询返回当前有效版、可选当前草稿和历史完成版本；草稿仅对当前项目经理可编辑，获授权成员只读当前有效版和完成历史。
- 历史按`businessVersion DESC,id DESC`稳定游标分页。版本对比只允许同租户、同项目、同PRE-04类型的两个完成版或当前草稿与其来源完成版，按`sectionCode`返回新增、删除、内容变化和附件引用变化，不产生持久化差异表。
- 上级项目角色只有在现有ProjectStageScope明确授予后代项目读取范围时才能查看具体版本；角色名称本身不扩权。平级、无范围及跨租户请求统一拒绝或按数据范围友好语义返回不可见。
- 网络拓扑、IP规划和安全防护附件下载必须同时通过SOL对象范围与PLT`DOWNLOAD`动作，生成短时访问票据并由平台审计留痕；SOL响应不返回长期URL。

### BR-FSOL003-006 SCH-01稳定事实边界

- SOL在`pms-module-engineering-api`公开`RequirementAnalysisFactApi.inspect`与`lockAndRevalidate`。默认返回项目当前有效`COMPLETED`版本；显式preparationId可读取该项目任一完成历史，不能返回草稿作为方案输入。
- 事实包含projectId、preparationId、businessVersion、contentVersion、projectVersion、template/config版本、completedAt、`isCurrentEffective/currentEffectivePreparationId/currentEffectiveBusinessVersion`、按sectionCode排序的结构化正文与精确附件事实；不包含文件URL、SCH草稿、预填结果或输入变化决定。
- `lockAndRevalidate`要求携带上述期望标识、内容版本和结构化事实向量，按`PROJ项目范围 -> SOL完成版/章节与当前有效指针 -> PLT精确文件`锁序同步重验；任一为空、越租户、非COMPLETED、正文/附件事实变化均失败关闭。显式历史完成版不会仅因失去`effective_marker`而失效，接口返回最新`isCurrentEffective`及当前有效版本标识，由后续SCH-01决定输入变化语义。
- inspect纯只读。该公共契约只证明一个明确完成版本当前可读/可锁定，不创建SCH引用、不标记“输入已变化”、不重试方案预填，也不解释为SCH-01已就绪。后续SCH-01负责持久化所选preparationId/businessVersion并处理引用、预填和差异。
- 本Feature不发布跨Context业务事件、不建设Outbox；锁定SDS为同步查询/命令边界。

### BR-FSOL003-007 权限与动作投影

| 能力 | 功能权限码 | ProjectStageScope | 主体约束 |
|---|---|---|---|
| 查看当前有效版、完成历史和版本对比 | `pms:requirement-analysis:query` | `PROJECT_VIEW` | 当前项目获授权成员；具体后代范围由PROJ事实决定 |
| 创建/编辑/完成草稿 | `pms:requirement-analysis:manage` | `PROJECT_MANAGE` | 当前有效项目经理；初次创建/完成另校验S1 |
| 下载/预览附件 | SOL查询权限 + PLT `pms:file:download`/`pms:file:preview` | `PROJECT_VIEW` | SOL用途Provider与PLT同时重验 |

- 查询中的`allowedActions`基于受信tenant/actor、功能权限、当前ProjectStageScope、当前项目经理事实、草稿/有效版状态及版本保守计算；未知或Provider不可用时为空。命令端继续锁定重验，前端不得自行比较角色推导授权。
- 请求不得自报tenant、actor角色、项目经理或后代范围。

### BR-FSOL003-008 幂等、并发、审计与失败语义

- 初始化、从有效版创建草稿和完成命令使用`Idempotency-Key`；已完成同键同规范化载荷重放原结果，同键异载荷冲突。既有PLATFORM返回`Decision.IN_PROGRESS`且无响应载荷，因此进行中重复映射为稳定`PMS-PLATFORM-COMMAND-IN-PROGRESS`业务错误并且无成功副作用，不伪造operation响应。PATCH使用`If-Match`且不以新随机键掩盖版本冲突。
- 稳定锁序为`PROJ项目/参与事实 -> SOL项目+PRE-04根行/草稿/有效版/章节及全部文件业务Provider -> PLT精确文件事实`。创建新草稿在同一事务插入新根行/章节后，先按稳定键锁定重验全部来源READ与目标REFERENCE Provider，再进入PLT锁阶段并建立目标引用；任一步失败连同新行整体回滚。完成命令在业务CAS前完成全部文件重验。
- 成功审计记录项目、草稿/有效版、业务版本、章节变化摘要、附件精确引用摘要、动作、前后状态/版本、operationId、主体和时间。正文只记录受控摘要，不把敏感富文本或附件内容复制进审计。
- 权限、状态、校验、文件或版本失败不得产生完成事实、有效标记切换或成功幂等结果；事务回滚后使用平台公共审计记录稳定拒绝码和必要安全事实。

## 4. API与模块契约

所有HTTP路径继承`/api/v1/pms`，返回平台统一`CommonResult`和稳定`PMS-SOL-*`业务错误码。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/preparations?projectId={id}&type=PRE_04` | `GET` | 返回当前有效完成版、可见草稿摘要、版本和允许动作；无记录为空业务结果 |
| `/preparations` | `POST` | 创建首个PRE-04草稿；输入projectId/type，必填`Idempotency-Key`，服务端冻结WorkBinding配置 |
| `/preparations/{id}` | `GET` | 返回一个草稿或完成版及按sectionCode排序的章节；附件只返回精确元数据 |
| `/preparations/{id}/items/{sectionId}` | `PATCH` | 当前项目经理按字段存在性保存章节内容/附件；必填`If-Match`，空PATCH拒绝 |
| `/preparations/{id}/actions/submit` | `POST` | 校验并将当前草稿原子转为新的有效`COMPLETED`；必填`Idempotency-Key/If-Match` |
| `/preparations/{id}/actions/create-draft` | `POST` | 仅从当前有效完成版创建下一草稿；同键重放当前草稿，必填`Idempotency-Key/If-Match` |
| `/preparations?projectId={id}&type=PRE_04&history=true` | `GET` | `businessVersion,id`稳定游标分页完成历史；草稿不混入完成历史 |
| `/preparations/{id}/compare?targetPreparationId={id}` | `GET` | 返回同项目版本的章节与附件差异，不持久化差异 |

请求/响应使用明确VO；PATCH区分字段未提供与显式空值。写接口不接收tenantId、actorRole、effectiveMarker、statusCode、businessVersion或completedBy等服务端事实。

### 4.1 `RequirementAnalysisFactApi`

- `inspect(RequirementAnalysisFactQuery)`输入受信`projectId`及可选`preparationId`，返回明确完成版本、内容版本、当前有效关系、项目版本、模板配置版本、完成元数据、结构化章节事实和附件事实向量。
- `lockAndRevalidate(RequirementAnalysisFactRevalidationQuery)`输入`projectId/preparationId/expectedBusinessVersion/expectedContentVersion/expectedProjectVersion/expectedTemplateRevision/expectedFactVector`；全部为服务端消费者此前inspect所得事实，不接受调用方自报tenant或角色。
- 两个接口均只读，不写PRE-04或SCH表。无当前有效版时inspect返回明确空业务结果；锁定重验则失败关闭。

### 4.2 PLT既有版本附加命令边界

- F-SOL-003在既有`pms-module-platform-api`的`FileArtifactApi`增加窄公共命令`attachExistingVersions(AttachExistingFileVersionsCommand)`，只用于把已存在且可用的不可变FileVersion附加到新的业务对象槽位，不创建Artifact/FileVersion、不读取正文、不重新上传。
- 每个命令项输入来源完整稳定键、`artifactId/versionNo/expectedFileFactVersion/expectedScopeVersion`，以及目标`SOL/REQUIREMENT_ANALYSIS_SECTION/{newSectionId}/SECTION_ATTACHMENT/{newSlotKey}`和目标`expectedScopeVersion`；tenant/actor来自受信上下文。批量命令严格分两阶段：第一阶段把全部来源`READ`和目标`REFERENCE`业务Provider请求按`ownerContext/objectType/objectId/purposeCode/referenceKey/action`排序并逐一锁定重验；第二阶段才把全部PLT锁按Artifact ID、`artifactId/versionNo`、完整Reference稳定键分别排序，依次取得Artifact→Version→Reference锁并比较来源事实。目标槽位锁后按“不存在则插入、已指向同一artifact/version则重放、指向不同版本则冲突”处理，返回目标`fileFactVersion/scopeVersion`；取得任何PLT锁后不得回调业务Provider。
- 批量命令加入调用方事务，全部目标引用与SOL新草稿/章节原子成功或回滚；每个新引用保留既有`FileReferenceAttached`审计/Outbox事实。来源与目标完整稳定键必须不同，来源完成版Provider为只读且目标草稿Provider可写；后续目标换版/解绑只改变新引用，不得更新或分离来源完成版引用。

### 4.3 WorkBinding与领域边界

- PRE-04是SOL业务事实，不是`TASK_NATIVE`正文。项目工作区可通过F-PROJ-007的`BUSINESS_OBJECT`绑定装载SOL组件，但allowedActions、详情和完成事实必须回源SOL。
- SOL只消费PROJ `ProjectWorkBindingFactApi/ProjectParticipantFactApi/ProjectScopeApi`和PLT公开文件API，不依赖其Service、Mapper、DO或业务表。
- 后续SCH-01只消费`RequirementAnalysisFactApi`；F-SOL-003不访问或写入Solution表。

## 5. 数据与物理边界

机器契约：`specs/features/F-SOL-003-physical-contract.json`。

- 前向扩展既有`sol_preparation`以支持`PRE_04_REQUIREMENT_ANALYSIS`及草稿/有效双标记；PRE-02现有状态、约束和current语义必须保持不变。
- 前向新建`sol_requirement_analysis_section`保存冻结章节定义、正文值、精确附件引用及章节CAS；不复用PRE-02工勘确认、来源、豁免或就绪状态。
- SOL内部使用`tenant_id`复合外键；project、user和FileArtifact为跨Context稳定引用，不建物理外键。
- Mapper仅暴露场景化insert、稳定查询、锁定读和专用CAS；不继承通用CRUD，不提供完成历史更新/删除。
- 使用实施时下一未占用Flyway版本；不修改V1～当前已执行迁移。旧表只作迁移差距证据，不双写、不自动切换历史数据。

## 6. UI

- 项目工作区新增“需求分析”入口，清晰区分当前有效完成版与未完成草稿；展示11项核心内容、模板扩展项、附件、完成阻断、历史和版本对比。
- 项目经理可继续草稿或从有效版创建下一草稿；获授权成员只读。完成按钮只在服务端投影允许动作时显示，缺少必填、附件失效或版本冲突显示稳定原因。
- 富文本使用既有受控编辑器和安全渲染；文件复用统一上传/版本组件，不在正文中拼接长期下载URL。
- 320/768/1024/1440无页面级横向溢出；窄屏按章节卡片/抽屉展示，刷新后草稿、有效版、附件和允许动作与服务端一致。

## 7. 验收标准

- `AC-FSOL003-001`：合法项目冻结WorkBinding可幂等创建businessVersion=1草稿及11项核心章节；空/多绑定、非法扩展、脚本字段、越租户或非S1首次创建均失败且零业务副作用。
- `AC-FSOL003-002`：三个核心必填不可被模板取消；核心/扩展类型、字典选项和富文本安全规则按冻结配置校验，模板变化不覆盖已建版本。
- `AC-FSOL003-003`：项目经理可按字段存在性和If-Match保存内容/附件；无权、空PATCH、并发冲突或上传绑定失败保留最近成功草稿。
- `AC-FSOL003-004`：完成命令在必填/模板/文件精确重验全部通过后原子形成不可变COMPLETED；任一失败不切换状态/有效标记，不产生成功幂等或审计。
- `AC-FSOL003-005`：已有有效版时创建下一草稿不影响旧有效输入；新草稿完成时有效标记原子切换且历史不可变，同项目至多一个草稿和一个有效版。
- `AC-FSOL003-006`：历史稳定分页和版本对比覆盖内容新增/删除/变化及附件引用变化，不生成第二份差异真值。
- `AC-FSOL003-007`：当前项目经理写入/完成、授权成员只读、后代范围受PROJ授权、平级/无权/跨租户拒绝；敏感附件下载由SOL+PLT双重授权并留痕。
- `AC-FSOL003-008`：初始化、创建下一草稿和完成支持已完成同键同载荷重放、异载荷冲突、进行中稳定IN_PROGRESS错误且无响应/成功副作用，以及同项目并发单胜；锁序为PROJ→SOL→PLT。
- `AC-FSOL003-009`：`RequirementAnalysisFactApi`只返回/锁定明确COMPLETED版本及精确附件事实；草稿、旧内容期望、失效文件或事实变化失败关闭且零写入；历史完成版仍可读取/锁定并明确返回`isCurrentEffective=false`及最新有效版本标识。
- `AC-FSOL003-010`：F-SOL-003不创建SCH引用、预填、差异状态、方案版本或事件；SCH-01未实施不影响PRE-04完成版本成立。
- `AC-FSOL003-011`：全新MySQL从V1迁移至实施版本，验证条件约束、复合外键、草稿/有效唯一键、历史不可变、CAS、回滚、权限及至少含无扩展/多类型扩展/停用字典拒绝的种子组合。
- `AC-FSOL003-012`：真实浏览器覆盖初次填写完成、从有效版创建草稿并完成切换、历史对比、无权只读/拒绝、附件失败恢复及320/768/1024/1440响应式状态持久化；console/page error为零。
- `AC-FSOL003-013`：完成版含多个附件时创建下一草稿，PLT为每个新章节建立新完整稳定键的独立FileReference并复用同一不可变FileVersion；旧完成版引用不可换版/解绑，新草稿引用可独立换版/解绑。并发验收断言全部来源READ/目标REFERENCE Provider先按稳定键锁定，随后全部PLT锁按稳定顺序执行Artifact→Version→Reference，且取得PLT锁后无Provider回调；来源变化、目标拒绝、反序尝试或批量中任一失败时草稿、章节、新引用、成功审计/事件全部为零，同目标同版本重放不重复创建、不同版本冲突。

## 8. Feature Ready检查

| 检查项 | 当前结论 |
|---|---|
| PRE-04目标、11项核心、三个必填与版本规则 | PASS（PRD V1.8 §5.2.4） |
| 独立Feature与上下游边界 | PASS（`NPDMS-FSOL003-BOUNDARY-20260827-01`） |
| 草稿/有效双轴、数据和文件冻结 | CANDIDATE |
| 权限、HTTP与SCH-01公共事实契约 | CANDIDATE |
| 幂等、并发、审计、负向与响应式验收 | CANDIDATE |
| 独立Feature Ready裁决 | PENDING |

结论：`IN_REVIEW / NOT_READY`。仅允许提交本Feature Spec、机器物理契约、索引和PRE-04追溯候选进行独立Feature Ready评审；未取得GO前不得生成Technical Plan、Task或实施代码。本候选不重开已通过PRD/SDS门禁，也不代表Implementation、Deployment、SIT、UAT或Release通过。
