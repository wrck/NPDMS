# SDS Phase 2：API 设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8及批准增量`CHG-PRD-2026-08-23-002`
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；接口组在第 5～14 节回指具体 Requirement
> Owner：SDS Phase 2 应用与接口架构
> 前置设计：`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`

## 1. API 边界与版本

【建议】新契约使用逻辑基础路径 `/pms/v1`；网关或基础平台统一前缀不写入领域契约。现有 `/pms/*` Controller 作为兼容适配入口，逐项代理到新的应用命令/查询，不再直接形成第二套业务规则。

- 用户端、响应式 Web 和未来桌面端复用同一领域 API。
- 外部系统回调、内部 Context 调用和用户 API 使用不同认证入口与执行身份。
- API 版本只处理兼容演进，不用于绕过 Requirement 变更。
- V3 和 OUT_OF_SCOPE 能力不得通过隐藏接口提前发布。

## 2. 通用请求与响应

### 2.1 请求头

| Header | 场景 | 规则 |
|---|---|---|
| `X-Request-Id` | 全部 | 可由客户端传入；缺失时服务端生成并回传 |
| `Idempotency-Key` | 创建、状态命令、外部下发、回调重放 | 同一作用域内唯一；同键不同请求摘要返回冲突 |
| `If-Match` | 修改聚合、移动树节点、状态命令 | 值为当前聚合版本；不匹配拒绝覆盖 |
| `X-Source-System` | 受信任同步/回调 | 必须与执行身份绑定，不能由普通用户伪造 |

租户、用户、组织、项目数据范围和执行服务身份从服务端认证上下文取得，不接受请求体中的同名字段覆盖。

### 2.2 成功响应

在基础平台统一响应包装内，业务数据至少返回 `requestId`、资源 `id`、`version`、`statusCode`；异步命令返回 `operationId`、`acceptedAt` 和查询位置。批量操作逐项返回 `itemKey/status/errorCode`，不得用整体成功掩盖部分失败。

### 2.3 错误响应

| 字段 | 说明 |
|---|---|
| `code` | 稳定机器错误码 |
| `message` | 可展示、无秘密的说明 |
| `requestId` | 追踪标识 |
| `fieldErrors` | 输入字段错误，不回显秘密值 |
| `currentVersion/currentStatus` | 并发或状态冲突时返回 |
| `retryable` | 是否允许按原幂等键重试 |
| `detailsRef` | 受权用户查询详细证据的引用，不直接泄露内部报文 |

## 3. 命令、查询与状态迁移规范

- 查询使用 `GET`；新建草稿/事实使用 `POST`；普通可编辑字段使用 `PATCH` + `If-Match`。
- 状态变化统一使用 `POST /{resource}/{id}/actions/{command}`，例如 `actions/submit`、`actions/review`，不提供通用 `status` 更新接口。
- 删除仅适用于 PRD 允许删除的草稿或配置；已提交、批准、发布、执行、归档和审计事实使用撤销、作废、失效或新 revision。
- 树查询支持 `scope=self|children|descendants`，服务端强制数据范围；大型子树使用稳定游标和 `treeVersion`。
- 所有列表必须有确定性排序；默认按 `updateTime desc, id desc`，树路径按业务顺序和 ID 稳定排序。

## 4. 授权决策顺序

每个业务 API 依次校验：认证主体 → 租户 → 功能权限 → 数据范围 → 聚合当前状态 → 对象/设备/文件/凭证专用权限 → 并发版本 → 业务守卫。任何一步拒绝都不执行后续写入。

| 范围 | 典型接口 | 服务端证据 |
|---|---|---|
| ProjectTreeScope | 项目、任务、组合和阶段 | 当前项目树版本、主体项目角色和后代范围 |
| ProjectDeviceScope | 设备、实施、割接、巡检 | DeviceCurrentAssignment + 项目祖先投影水位 |
| ContractProjectScope | 合同订单范围 | 合同/订单授权 + DeliveryScope |
| BusinessObjectDeviceCredentialScope | 采集任务 | 来源业务对象、设备、协议、命令模板、用户和有效期五元组 |
| FileBusinessScope | 下载、预览、替换、归档 | FileReference 所指业务对象的实时权限 |

## 5. PROJ：项目治理 API

适用 Requirement：PM-01～PM-11、PROJ-12、INT-01。

| 路径 | 方法/命令 | 作用 | 关键约束 |
|---|---|---|---|
| `/projects` | `POST`, `GET` | 创建、分页查询项目 | 创建需幂等键及必填非空白createReason；公司与办事处部门按同一组织范围校验；请求包含零到多个站点及一个可选主站点，或显式未解析文本降级；F-PROJ-004生效后所有成功创建须记录AUTO_UNIQUE或EXPLICIT_SELECTION的INITIAL_CREATE历史；查询服务端过滤 ProjectTreeScope |
| `/projects/{id}` | `GET`, `PATCH` | 项目详情、可编辑属性 | `PATCH`不能修改状态、父节点、来源权威字段及四项模板输入属性；四属性只能走PM-07受控命令并追加历史 |
| `/projects/{id}/workspace` | `GET` | 项目概览六页签、Stage→ProjectTask导航和投影水位 | 不返回第二套导航真值；按ProjectTreeScope裁剪，任务子树按需加载 |
| `/projects/{id}/gantt` | `GET` | V2按同一ProjectTask、计划字段和TaskDependency返回甘特投影 | 只读展示，不建立第二套任务事实；按ProjectTreeScope裁剪 |
| `/projects/{id}/tree` | `GET` | 直接下级、全部后代、完整上级链、指定业务层级或节点定位 | 先执行ProjectTreeScope；返回同一完整`treeVersion`、裁剪结果和稳定游标 |
| `/projects/{id}/actions/move` | `POST` | 移动到目标父项目 | `Idempotency-Key`、`If-Match`、无环校验、树变更批次和新`treeVersion` |
| `/projects/{id}/actions/classify` | `POST` | 四项模板输入的受控人工调整 | 以5.5为唯一详细契约；追加MANUAL_ADJUSTMENT历史，不更换冻结模板 |
| `/projects/{id}/actions/assign-manager` | `POST` | 指派项目经理/服务经理 | 仅使用 PRD 已定义角色和规则 |
| `/projects/{id}/actions/rollback` | `POST` | 受控阶段回退 | 保存原因、目标阶段和新门禁快照 |
| `/projects/{id}/actions/close` | `POST` | 接收闭环完成后的关闭命令 | 只能由闭环契约触发或满足相同门禁的授权入口 |
| `/projects/{id}/members:batch-change` | `POST` | 人员批量变更 | 逐项结果、有效期和历史，不覆盖原记录 |
| `/projects/{id}/tasks` | `POST`, `GET` | 创建/查询任意层级任务 | 任务父节点可空；不限制深度 |
| `/project-tasks/{id}` | `GET`, `PATCH` | 任务详情与可编辑属性 | 状态和父节点不可普通修改 |
| `/project-tasks/{id}/workbench` | `GET` | 返回任务通用基础信息、executionContractId/contractVersion、WorkBinding类型、允许操作和完成规则摘要；TASK_NATIVE不返回外部目标，其他类型返回必要的目标稳定引用、受信任组件键/表单/审批引用 | 每次按任务、项目树、绑定类型及适用的目标对象和状态重新授权；不返回任意脚本或跨域数据正文 |
| `/project-tasks/{id}/dependencies` | `GET`, `POST`, `PATCH`, `DELETE` | V2受控新增、更新、删除前后置依赖 | 依赖与父子层级正交；写命令要求`If-Match`与幂等键，服务端校验两端任务权限、项目范围和禁止的依赖环；不改变任务层级 |
| `/project-tasks/{id}/actions/move` | `POST` | 移动任务节点 | 无环、树版本、项目范围校验 |
| `/project-tasks/{id}/actions/{submit|start|complete|cancel}` | `POST` | 任务状态命令；complete必须提交taskVersion、executionContractId/contractVersion、适用的factObjectKey/factVersion和Idempotency-Key | 按05状态机守卫执行；TASK_NATIVE按任务自身事实完成，其他绑定由服务端回源Owner事实并追加TaskCompletionEvaluation；不能用通用按钮绕过目标业务 |
| `/project-templates` | CRUD + `actions/publish` | 项目/阶段/任务模板；发布请求逐个TaskDefinition提交WorkBinding、PermissionPolicy、CompletionRule和可选GateRef | 已发布 revision 只读；缺失绑定/规则、绑定字段与类型不一致、目标未发布或GateRef无效时整版拒绝 |
| `/project-portfolios` | CRUD + `actions/publish` | 项目组合 | V2；成员项目只引用不改 Owner |

外部项目同步不直接暴露为普通用户 CRUD；由第 12 分册定义的 CRM/ERP 适配器调用内部 upsert 命令并保存来源版本。

F-PROJ-001创建和指派不得接受无来源的数值`officeId/locationId`。公司、办事处部门和结构化站点均使用稳定ID、编码和版本；未维护站点时返回`locationResolutionStatus=UNRESOLVED`，未解析文本不参与自动办事处解析或结构化权限判断。`assign-manager`可携带`siteId/departmentCode`，区划映射只提供候选，V1最终值由授权人员人工确认。

### 5.1 PM-02 项目拆分、树版本与进度契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-split-requests` | `POST` | 输入父项目、组合方案和稳定`clientItemKey`；返回草稿ID与`draftVersion` | 工程管理部或获授权服务经理；父项目ProjectTreeScope、租户和功能权限同时满足 |
| `/project-split-requests/{id}` | `GET`, `PUT` | 读取/更新草稿、范围项和逐项校验结果 | APPLIED不可编辑；`If-Match`校验草稿版本；失败草稿保留 |
| `/project-split-requests/{id}/actions/{preview|validate}` | `POST` | 服务端返回子项目方案、Commerce/AST/组织校验结果、`previewHash`与各Owner水位 | 无业务写入；客户端预览摘要不能作为确认依据；权威范围不可用时禁止确认 |
| `/project-split-requests/{id}/actions/apply` | `POST` | `Idempotency-Key`、`If-Match`及父项目/范围/树期望版本；返回全部子项目、范围分配、`changeBatchId/treeVersion` | 服务端重新校验；全部子项目、模板实例、范围、树、审计、Outbox和幂等完成点原子提交 |
| `/projects/{id}/tree` | `GET` | `queryType=CHILDREN/DESCENDANTS/ANCESTORS/BUSINESS_LEVEL/LOCATE`、业务层级、游标；返回`treeVersion/items/nextCursor/updating` | 游标固定在同一完整版本；服务端先执行租户与ProjectTreeScope；无权路径只返回必要占位 |
| `/projects/{id}/actions/move` | `POST` | 目标父项目、原因、`Idempotency-Key`、`If-Match`；返回`changeBatchId/treeVersion` | 按稳定ID锁定，拒绝自身、后代、跨租户和陈旧版本；移动不改项目编码命名空间 |
| `/projects/{id}/progress-policies` | `POST`, `GET` | 创建/查询直接子项目权重和分母口径版本 | 草稿完整覆盖直接子项目；权重合计100%；默认等权也形成版本；历史版本只读 |
| `/progress-policies/{id}/actions/submit` | `POST` | 提交配置化BPM审批；返回流程实例和策略版本 | `If-Match`；批准回调幂等；未批准版本不影响当前计算 |
| `/projects/{id}/progress` | `GET` | 返回策略版本、树版本、事实水位、READY/PENDING、汇总结果、缺失项和直接子项目解释 | 任一必要事实缺失时PENDING，不以0或旧快照替代；按ProjectTreeScope裁剪明细 |
| `/projects/{id}/closure-guard` | `GET` | 返回当前完整树版本、是否可进入CLO-02、未满足后代和待计算项目 | 只提供PROJ守卫，不创建、批准或完成闭环；未授权后代不泄露敏感字段 |

拆分草稿允许办事处部门编码，不接受通过`addressId`反推办事处。SN由AST公开契约校验，DeliveryScope由COM公开契约预览和分配；PROJ不得直接访问AST/COM Repository或表。管理端实际路由由`/admin-api/pms`装配，上述资源语义映射到对外`/api/v1/pms`时保持字段、版本和错误分类一致。

### 5.2 PM-04 项目子树授权契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/projects/{projectId}/actions/assign-manager` | `POST` | 用户、PRD已定义项目角色和生效区间；返回成员角色区间版本 | 服务经理、功能权限、同租户和目标项目管理范围；角色本身不产生后代范围 |
| `/projects/{projectId}/authorization-grants` | `POST`, `GET` | 创建时输入主体用户、动作、范围、生效区间和原因；查询按主体、动作、范围、状态和有效时点分页 | 创建要求`Idempotency-Key`且不得超出授权人范围；查询空范围返回空页 |
| `/project-authorization-grants/{grantId}` | `GET` | 返回授权、授予和撤销摘要 | 越权按不存在处理，不泄露授权存在性 |
| `/project-authorization-grants/{grantId}/actions/revoke` | `POST` | 原因和期望版本；返回撤权版本与失效时间 | `Idempotency-Key`、`If-Match`；同请求重放原结果 |

PLT公开`AuthorizationGrantApi`完成授权创建、撤销和按主体/资源/动作/有效时点查询，不读取项目树。PROJ公开`ProjectScopeApi`，合并当前成员关系、PLT有效授权和当前完整项目树版本；其他模块不得访问双方Service、Mapper、Repository或表。

### 5.3 PM-05 借货项目转销契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-conversions` | `POST` | 输入 sourceProjectId、targetProjectId、formalSalesBusinessId、对象复用清单、逐台设备处置、Idempotency-Key；返回 conversionId、状态和逐项结果 | 调用人同时具备源/目标项目管理权限；目标为有效正式销售项目；同一源项目无其他生效目标；幂等键为源项目+正式销售业务ID |
| `/project-conversions/{id}` | `GET` | 返回源/目标、处理中/部分失败/待处理/已完成状态、成功/失败对象汇总、来源版本和设备处置结果 | 只返回同时满足源/目标项目数据范围的内容；敏感对象继续执行原对象权限 |
| `/project-conversions/{id}/actions/retry-failed` | `POST` | 输入 expectedVersion 和失败 itemIds；返回原批次的新版本及逐项结果 | 仅重试失败/待处理项；成功引用/副本不得重复生成；设备归属重新校验 assignmentVersion |

对象清单的 `handlingMode` 只能是 `READ_ONLY_REFERENCE` 或 `DERIVED_COPY`；默认前者。派生副本必须返回 `sourceObjectId/sourceVersion/derivedObjectId`。只有所有项成功后服务端才完成转销并归档源项目，不提供客户端直接设置完成/归档状态的接口。

### 5.4 PM-06 多期项目契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-phase-groups` | `POST`, `GET` | 创建/查询群组；输入关系类型、名称、首期项目和期次号 | 关系类型来自字典；调用人具有涉及项目权限；跨租户禁止 |
| `/project-phase-groups/{id}/actions/add-phase` | `POST` | 输入 projectId、phaseNo、displayOrder、expectedVersion；返回 groupVersion/memberVersion | 同关系类型下项目未加入其他有效群组；期次唯一；关系无环 |
| `/project-phase-groups/{id}/actions/remove-phase` | `POST` | 关闭成员有效区间并返回新版本 | 不删除项目事实、历史引用或已发布汇总快照 |
| `/project-phase-groups/{id}/phases` | `GET` | 按期次返回独立项目状态、来源版本、设备分类和资料差异，附 completeScope 标识 | 只返回用户有权期次；缺失期次标记不完整，不按零值汇总 |
| `/project-phase-groups/{id}/actions/derive-content` | `POST` | 输入 sourceProjectId/sourceObjectType/sourceObjectId/sourceVersion/targetProjectId；返回派生对象和来源关系 | 只允许 PRD 指定的客户视图、拓扑、方案和设备视图复用；派生修改不回写来源 |

### 5.5 PM-07属性判定与匹配历史契约

| 路径 | 操作 | 输入/输出 | 守卫 |
|---|---|---|---|
| 既有`/projects/{id}` | `GET` | 继续返回当前四属性 | 不增加重复当前属性资源 |
| 既有`/projects` | `POST` | 必填非空白createReason；候选结果与`AUTO_UNIQUE/EXPLICIT_SELECTION`决策；返回Project、冻结模板和INITIAL_CREATE历史operationId | 原因trim后为空在事务前拒绝；无匹配拒绝；多匹配必须显式选择本次合法候选；Project、历史、模板冻结和实例化同事务 |
| `/projects/{id}/template-match-history` | `GET` | 按触发类型、匹配结果、影响结论、operationId和时间分页；返回可用的traceId/auditLogId关联 | ProjectTreeScope；排序白名单；越权按不存在 |
| `/projects/{id}/actions/classify` | `POST` | 允许修正的维度及必填非空白adjustmentReason；返回当前四属性、新增历史ID及operationId | 原因trim后为空在事务前拒绝；`Idempotency-Key`、`If-Match`、项目处置权限；业务用户不得写CRM重大级别；不重新实例化；不以异步系统日志写入作为成功条件 |
| 内部`ProjectAttributeSourceCorrectionCommand` | 应用命令 | 已定位projectId、CRM Owner字段、来源键/事件/版本/发生时间/原值摘要、必填非空白correctionReason、幂等键和服务身份 | 原因trim后为空在事务前拒绝；仅受信任INT服务；serviceIdentity须映射为稳定已注册服务主体ID作为operatorId；同事务更新当前值并追加SOURCE_CORRECTION历史；不负责来源定位、重试或对账 |

PROJ内部`ProjectAttributeResolutionService`供手工创建与未来CRM自动创建编排复用，输出确定属性输入后调用既有TemplateMatcher。无匹配，或多匹配但未显式选择本次合法候选时，首次创建整体失败；创建后classify只追加重新评估历史。集合响应统一分页；写命令同键同摘要重放、同键不同摘要冲突，进行中重复返回409。

### 5.6 PM-08服务经理人工指派契约

| 接口 | 输入/输出 | 业务守卫 |
|---|---|---|
| `GET /projects/{id}/service-manager-candidates` | `siteId/departmentCode/keyword/pageNo/pageSize`；返回候选分页 | 校验租户、Project公司、实际节点/站点、部门映射及MANAGE范围；合法精确范围无人员返回空页，不跨部门回退 |
| `POST /projects/{id}/actions/assign-manager` | `userId/levelCode/assignmentType/siteId/departmentId/departmentCode/changeReason`及`Idempotency-Key/If-Match`；返回关系ID、服务端`effectiveFrom`、Project版本和状态 | V1禁止客户端预约生效；提交时重验启用用户、公司、部门ID/编码及有效范围；Project CAS后检查重叠主责；改派同一时点关闭旧区间并新增关系 |
| `GET /projects/{rootId}/service-manager-responsibilities` | 按实际节点分页返回站点/部门、当前主责、协同和节点状态 | ProjectTreeScope裁剪；不生成隐式关系或后代授权 |

SYSTEM公开`OrganizationScopeApi.pageActiveUsers(OrganizationUserCandidatePageReqDTO)`：请求必填`companyId/departmentId/departmentCode/pageNo/pageSize`，关键字可空，页大小1～100，租户来自受信任上下文；响应`PageResult`项为`userId/username/nickname/employeeNo/companyId/departmentId/departmentCode/departmentName`。参数非法返回`INVALID_ARGUMENT`，组织ID/编码冲突或主数据不可用返回`ORG_SCOPE_INVALID`，合法范围无人员返回空页。PROJ只调用公开API，不访问SYSTEM Service/Mapper/表。

`NotifyMessageSendApi`为请求增加可空`deliveryKey`；现有调用可空，PM-08必须传Outbox `eventId`。SYSTEM持久去重后，一致重放返回首次消息ID，不一致重放返回投递键冲突。通知失败不回滚已提交指派，Outbox退避重试。

## 6. SOL：交付准备与方案 API

适用 Requirement：PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01。

| 资源 | 路径与命令 | 关键约束 |
|---|---|---|
| Preparation | `/preparations`、`/{id}/form`、`/{id}/actions/{submit|confirm|return|create-draft}` | PRE-04按WorkBinding自动冻结PLT修订且项目用户不选模板；SOL组合唯一PLT业务实例，完成前锁定重验完整schema/值/文件事实；新版本复制为新Owner实例而不覆盖旧完成版 |
| ConstructionPlan | `/construction-plans`, `/{id}/revisions`, `/{id}/actions/{submit|approve|reject}` | 批准 revision 不可覆盖；计划变更保存前后差异 |
| Schedule | `/schedules`, `/{id}/actions/{calculate|apply}` | 计算结果是候选快照，只有 apply 命令改变计划 |
| Solution | `/solutions`, `/{id}/revisions`, `/{id}/actions/{submit|approve|reject|publish}` | 提交/批准/发布均需 If-Match 和文件引用校验 |

SOL不再拥有通用`/form-schemas`或`/form-instances`。PRE-04及其他SOL Feature以后通过PLATFORM锁定模板修订和通用渲染能力，并继续由SOL API拥有业务提交、完成、审批、历史和下游引用语义。

## 7. IMP：现场实施 API

适用 Requirement：EXE-01～EXE-06、IMP-01。

| 聚合 | API | 状态命令/特殊约束 |
|---|---|---|
| ArrivalAcceptance | `/arrival-acceptances` | `confirm`、`raise-difference`、`resolve-difference`；最终确认按 PRD 由项目经理执行 |
| InstallationRecord | `/installation-records` | `submit`、`confirm`、`return`；确认/退回按 PRD 由项目经理执行 |
| ConfigurationCollectionResult | `/configuration-results`、`/devices/{id}/component-relations` | `consume-callback` 为内部命令；解析候选可待匹配/人工绑定；绑定通过AST命令结束旧关系并新增时态关系，不修改原始Log |
| JointDebuggingResult | `/debugging-results` | 关联 CollectionTask；记录联调结论和问题引用 |
| ImplementationRisk | `/implementation-risks` | `raise`、`treat`、`close`；不调用 CUT 风险状态接口 |
| ImplementationQualityCheck | `/quality-checks` | `submit`、`review`、`complete-remediation`、`re-review` |
| DeliveryEvidence | `/implementation-evidence`, `/{id}/versions` | 上传/替换草稿；ACC 审核归档，不由 IMP 调归档命令 |
| Readiness | `/implementation-readiness/{projectId}` | 只读门禁查询，返回快照版本与未满足项；供 CUT 执行前校验 |

到货、安装、质量和安全接口按聚合独立分页和状态；不得恢复为一个通用“现场执行单” CRUD。

F-IMP-002的无用户主体豁免到期命令使用PROJ支撑Task `T-FIMP002-PROJ-01`公开的`ProjectSystemQualificationFactApi.lockCurrentForSystem`。该操作只接收项目ID和固定`ACTIVE/S4`资格要求，在同一事务中按根项目、目标项目、当前活动树版本顺序锁定，并返回当前项目经理、项目版本、参与者事实版本与树版本；不接收用户主体、`ACTION_EDIT`或消费方冻结版本，不改变`ProjectParticipantFactApi/ProjectScopeApi`的用户授权语义。

## 8. ACC：验收与项目闭环 API

适用 Requirement：ACC-01～ACC-06、CLO-01～CLO-02。

| 路径 | 命令 | 约束 |
|---|---|---|
| `/acceptances` | create/update draft、`submit`、`confirm`、`return` | 客户确认和项目审核分别留痕；不覆盖 IMP 证据 |
| `/acceptances/{id}/actions/send-confirmation` | `POST` | ACC-01 V2按短信/邮件和钉钉推送培训确认链接；分别记录受理/送达，送达不等于客户确认，失败保留V1链接/扫码入口 |
| `/delivery-artifacts` | `check-completeness`、`review`、`archive` | 齐套、审核、归档是不同命令；文件版本固定 |
| `/closure-gates/{projectId}` | `GET` | 返回所有后代项目的门禁快照和水位 |
| `/project-closures` | `create`、`submit`、`review`、`complete` | complete 发布事件请求 Project 关闭，不直写 Project 表 |
| `/service-handovers` | create、`submit`、`accept` | 只做持续服务交接，不提供 renew/续保接口 |
| `/satisfaction-tasks` | create、assign、send、recollect、list/detail | 创建时冻结问卷模板/阈值；未达标只能整改后新建任务和问卷版本；V2 `send`复用短信/邮件和钉钉通知，只增加自动触达，不复制问卷、评分、整改、签字或导出事实 |
| `/satisfaction-questionnaires/{token}/responses` | submit | 一次性实例、必答/签字校验和幂等提交；客户答案不可由内部用户修改 |
| `/satisfaction-results` | GET、export | 只读判定；导出按数据/字段/文件权限裁剪并生成导出审计 |

历史 `/pms/acc-maintenance-transition/*` 的 create/renew/activate 等入口必须在兼容切换后冻结，不映射为新 ServiceHandover 命令。

## 9. CUT：割接 API

适用 Requirement：CUT-01～CUT-10。

| 路径 | 命令/查询 | 关键约束 |
|---|---|---|
| `/cutover-tasks` | create、list、detail | 来源键幂等；项目/设备归属校验 |
| `/cutover-dashboard/kpis` | GET | CUT-01 V2按授权可见的CutoverTask聚合首页KPI | 只读聚合，不改变任务状态或P1～P6流程，不返回无权任务明细 |
| `/cutover-tasks/{id}/assessment` | save draft、submit | 一线提交问卷与人工等级；用服经理在P5复核，不新增P2审批 |
| `/cutover-tasks/{id}/checklist` | detail、save draft、submit | P3同一工作台返回checklistId/version、inputSnapshotHash、匹配项、界面格式、当前选择结果、CollectionTask/结果引用和重新匹配差异；D级不存在该资源 | save/submit携带If-Match与Idempotency-Key；提交只读取当前适用项和当前选择结果，全部必填满足后冻结版本 |
| `/cutover-tasks/{id}/checklist/actions/rematch` | POST | 输入checklistVersion、inputSnapshotHash和新维度，预览或应用差异 | 保留stableItemKey未变的有效答案；移出项仅留历史，不进入当前提交；已提交版本不得原位重匹配 |
| `/cutover-tasks/{id}/checklist/actions/export` | POST | CUT-03 V2导出当前授权清单版本 | 按清单项、设备和字段权限裁剪；导出不改变清单、任务或流程状态 |
| `/cutover-tasks/{id}/checklist/items/{itemId}/actions/request-collection` | POST | 输入checklistVersion、itemVersion、deviceId、commandTemplateId和Idempotency-Key，为设备采集项创建DAC CollectionTask | 绑定任务、清单版本、采集项、设备和命令模板；DAC回调只生成技术结果，CUT经版本匹配后追加/选择ItemResult，不直接判定采集项通过 |
| `/cutover-tasks/{id}/plan-revisions` | create、submit、approve、reject | 文件/安全/归属/人工确认校验；不强制解析全部模板字段 |
| `/cutover-tasks/{id}/support-arrangements` | update contacts / revise duties | 联系人、联系方式、到位时间变化留痕不重审；角色/职责变化必须生成新方案revision并重走P5 |
| `/cutover-tasks/{id}/actions/request-collection` | POST | 兼容非清单级采集入口 | 新P3采集项使用item级入口；均不读取凭证明文，不创建独立采集阶段 |
| `/cutover-tasks/{id}/approval-actions/{approve|reject}` | POST | 按人工等级和冻结路由校验节点；任一评审项为否必须驳回并填写原因；V2对A/B级校验专项提前时间并按INT-10/INT-05发送已定义提醒，提醒失败不改变审批状态 |
| `/cutover-tasks/{id}/closure` | save、submit、detail | 保存P6结果与INT-12证据引用；提交即归档；失败不发布CutoverCompleted |
| `/cutover-config/{types|network-modes|checklist-items|binding-rules|navigation-rules}` | CRUD + `actions/publish` | CUT-07/09/10的V1动态模板、表单和匹配配置先于或不晚于首个消费能力交付；CUT-03 V2可增加受控跳转规则 | 发布版本不可覆盖；稳定编码、引用启用状态、条件可判定性和目标流程状态不合法时整版拒绝 |

PROJ公开`ProjectCutoverContextFactApi.inspect(ProjectCutoverContextFactQuery)`与`lockAndRevalidate(ProjectCutoverContextFactRevalidationQuery)`。inspect Query固定携带正数`tenantId/projectId`；重验Query携带从前次`FOUND`原样复制的完整`ExpectedProjectCutoverContextFact`，tenant必须与受信运行时上下文一致。`FOUND`返回同一ProjectMaster行的`tenantId/projectId/projectVersion/projectCode/projectName/customerId/customerCode/customerName/departmentId/departmentCode/departmentName`完整事实；三个编码字段最大64字符，`projectName/customerName/departmentName`最大255字符。`NOT_FOUND/INACTIVE`无Fact，写重验的`VERSION_CONFLICT`返回当前完整Fact。锁定方法以`MANDATORY`加入CUT写事务并锁定项目当前行，锁后逐字段比较完整Expected与currentFact；任一字段变化均返回`VERSION_CONFLICT`，Expected只作并发守卫，CUT只冻结Owner返回的currentFact。Owner损坏和Provider不可用分别使用公共稳定失败。客户字段和部门字段是项目发生时展示快照，CUT仍分别以`ProjectScopeApi.treeVersion`做用户范围重验、以`CustomerServiceLevelFactApi`取得当前服务等级；不得跨Context读表、拼接Summary或以projectVersion替代treeVersion。精确机器合同见`specs/features/F-CUT-002-project-context-fact-contract.json`。当前master尚未包含公共Java接口或生产Provider。

PROJ为F-CUT-005预留`ProjectCutoverServiceManagerFactApi.inspectCurrent/lockAndRevalidate`：按受信tenant和projectId返回并冻结当前唯一PRIMARY `SERVICE_MANAGER_L1/L2`、roleCode、projectVersion与participantFactVersion；零个或多个当前事实均为`NOT_UNIQUE`，不得任选人员。锁定重验以`MANDATORY`加入CUT事务，变化返回携带当前`FOUND/NOT_UNIQUE`的`STALE`；服务经理节点改派目标必须等于再次锁定的唯一当前事实。该能力复用ProjectParticipant聚合且不创建独立Feature或表，精确候选合同见`specs/features/F-CUT-005-candidate-owner-contract.json`；当前master尚未包含公共Java接口或生产Provider。

## 10. SRV：巡检与服务状态 API

适用 Requirement：INS-01～INS-09、SRV-01。

| Context | API | 约束 |
|---|---|---|
| Inspection | `/inspection-rules`、`/{id}/revisions`、`/revisions/{revisionId}/actions/{validate|record-security-review|publish|disable}`、`/selectable` | 创建稳定身份要求检测ID和规则名称；草稿允许其余八字段及命令/判定/产品适用关系为空或不完整，`validate/publish`按发布完整性返回字段级错误；阈值数据类型只接受`NUMBER`；安全审核结论只接受`PASSED/REJECTED`并绑定当前revision与命令/正则摘要，只有`PASSED`允许发布；发布revision只读；选择查询按授权设备的AST产品类型过滤；任务冻结规则版本 |
| Inspection | `/inspection-tasks`、`/{id}/actions/{precheck|dispatch|complete|archive}` | 在线通过 DAC；离线文件走受控上传；模式互斥 |
| Inspection | `/inspection-reports/{id}/versions` | 生成/发布报告版本，原始采集结果只引用 |
| Inspection | `/service-issues`、`/{id}/actions/{remediate|review|close|mark-false-positive}` | 问题闭环和误报留痕 |
| Service Operations | `/devices/{deviceId}/service-status` | V2 只读客观状态与来源，不提供续保空间/续保率接口 |

历史工单、工时及其附件在V1/V2不提供用户查询、导出或文件访问API。`AI-MIG-000`在已批准真实批次内保存的不可变来源载荷或受限迁移归档仅用于迁移对账、问题调查和来源审计，不是SRV业务API；未来用户访问能力必须通过独立PRD/Feature变更重新批准。

## 11. CUS、AST、COM、RES 与 KNO API

| Owner | Requirement | API | 关键边界 |
|---|---|---|---|
| CUS | CUS-01～CUS-04、INT-03 | `/customers`、`/customer-contacts`、`/customer-relationships` | CRM权威字段只读；临时客户显式标记来源；客户地址/站点只保存AST稳定引用 |
| AST | EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06 | `/devices`、`/devices/{id}/archive`、`/devices/{id}/assignment-history`、`/asset-locations/addresses`、`/asset-locations/sites`、`/asset-locations/sites/{id}/tree`、`/asset-locations/area-department-mappings`、`/rma-replacements` | 设备归属用`actions/assign-project`；地点由AST拥有；站点不绑定公司/部门；设备当前位置由已确认安装/迁移/拆除事实生效 |
| COM | COM-01 | `/contracts`、`/sales-orders`、`/order-lines`、`/commerce-authority/import-batches`、`/commerce-authority-candidates`、`/delivery-scopes` | ERP合同/订单/订单行核心字段只读；COM维护人工待核对候选、显式对账、项目范围水位和范围分配/调整/释放。地点只返回项目办事处发生时快照，实施站点/位置由IMP/AST独立提供 |
| RES | RES-01、SUB-01～SUB-05、INT-07 | `/suppliers`、`/subcontract-requests`、`/payment-gates` | 备件业务由外部系统承接；财务结果只回写引用 |
| KNO | INT-04 | `/technical-notices`、`/technical-notices/{id}/references` | V2 仅 ITR 同步查询与业务引用；无本地 publish/disable API |

设备归属命令 `POST /devices/{id}/actions/assign-project` 必须携带 `If-Match`、目标项目和原因；返回新的 `assignmentVersion` 和异步投影 `operationId`。上级项目统计读取设备祖先投影，不创建第二条归属。

跨模块只调用`AssetLocationApi`：

- `maintain(command)`：在授权项目的工勘/安装中维护Address/Site/SiteLocation，返回稳定ID和版本；
- `getAddress/getSite/getSiteLocation/getLocationTree`：按租户、状态、版本和调用方业务范围查询；
- `resolveDepartment(areaCode, areaLevel)`：仅精确查询`SERVICE_OFFICE`有效映射，缺失或停用返回无候选，不向父级回退；
- `validateSites(siteIds)`：批量校验站点状态、版本和租户；
- `effectEquipmentLocation(command)`：以安装业务键幂等使设备当前位置生效，写设备版本历史；失败回滚调用方安装完成事务。

F-PROJ-002另使用以下Owner公开契约：

- `AssetDeviceScopeApi.validateAssignableSerials(tenantId, parentProjectId, serialNumbers)`：AST返回SN存在性、租户和当前可分配结论及失败SN；不返回凭证明文或敏感设备详情；
- `DeliveryScopeApi.getAvailableSlices(parentProjectId, expectedScopeVersion)`：COM返回当前可分配订单行、数量、维度和权威版本；`PENDING_AUTHORITY`数量不进入结果；
- `DeliveryScopeApi.getAssignedScope(projectId, expectedScopeVersion)`：租户取受信上下文，项目须通过`ProjectScopeApi.ACTION_VIEW`。期望版本为null时只读inspect；非null时按订单行→范围→明细→项目水位稳定锁序重验（写命令在进入COM前已先锁PROJ项目行，ACC绑定最后执行）。返回行按`scopeId+scopeDetailId`分组并稳定排序，不聚合不同产品/型号/序列号主体；地点只返回主范围的项目办事处发生时快照，不返回AST站点或文本位置。待核对、取消、退货或释放量排除，但存在任一未解决冲突时整体失败关闭。持久项目水位覆盖空结果，版本陈旧、冲突、Owner损坏及Provider不可用分别返回稳定分类；
- `ProjectDeliveryScopeQualificationFactApi.inspect/lockAndRevalidate`：PROJ在受信租户下为COM交付范围写命令返回current项目经理、生命周期/阶段、项目/参与者/树版本和直管目标项目`ACTION_EDIT`组合事实；该编辑资格只由锁定目标项目行的current manager证明，不读取授权Grant或后代范围。锁定重验按根项目→目标项目→当前树版本执行，并比较经理、根身份及全部冻结轴。`NORMAL_CLOSED`只允许S6，树版本必须为正；存在的当前Owner事实必须先通过结构校验，损坏时返回Owner损坏，只有结构合法后与冻结事实不一致才返回`FACT_STALE`。当前只有公共接口和机器合同，未注册生产Provider；
- `DeliveryScopeApi.previewSplit(command)`：COM只校验组合、单位精度、重复和超配，不写范围事实；
- `DeliveryScopeApi.applySplit(command)`：COM按稳定订单行顺序锁定并在调用方事务中分配/释放范围、递增`scopeVersion`、写`DeliveryScopeAssigned/Released` Outbox；同键重放不重复分配。

PROJ只能依赖上述API及DTO，COM/AST实现不得回调PROJ Mapper、Repository或业务表。公开契约不可用时可继续保存/修正拆分草稿，但禁止确认应用，不把待核对数量视为可分配量。

AST不得依赖IMP的Service、Mapper、Repository或业务表。IMP保存安装事实和位置快照，AST只消费公开命令参数。

## 12. ANA 与公共能力 API

| Owner | Requirement | API | 规则 |
|---|---|---|---|
| ANA | RPT-02、ANA-01 | `/analytics/metrics`、`/analytics/portfolios/{id}` | 返回 `metricVersion/dataWatermark/treeVersion`；只读 |
| PLT | PLT-01 | `/todos`、`/{id}/actions/complete` | 待办完成回调业务 Owner；不能自行宣告业务成功 |
| PLT | PLT-02 | `/files:init-upload`、`/files/{id}:complete-upload`、`/files/{id}/versions`、`/file-references` | 文件 API 详见 13；下载实时校验业务权限 |
| PLT | SOL-01公共基础切片、PRE-04组合依赖 | 原动态表单用户REST保持不变；内部`DynamicFormBusinessInstanceApi`提供用途检查/锁定重验、业务实例创建/读取/CAS修改/复制/完整重验 | 业务动作封闭并由inspect冻结；创建/修改/复制/持锁重验为`MANDATORY`，无外层事务拒绝；调用方预分配实例ID；Owner Provider先于PLT锁，PLT不拥有消费方状态 |
| PLT | AUT-01～AUT-02 | `/authorization-grants`、`/authorization-grants/{id}/actions/revoke`；内部`AuthorizationGrantApi` | 创建需幂等键，查询分页，撤权需期望版本；通用授权不代替DAC凭证授权 |
| PLT | CHG-01 | `/change-requests`、状态命令 | 低优先级独立能力，按版本范围后置实施 |
| SYSTEM | INT-09 | `/system/companies`、`/system/departments` | Company与Department独立；Department响应包含统一`code`和`version`；办事处按Department表达 |
| SYSTEM | INT-09、PM-01 | 内部`CompanyApi/DeptApi/OrganizationScopeApi` | 按稳定ID/编码查询；用户公司—部门范围必须命中同一有效行，不由部门推导公司 |

PLT内部`PlatformMigrationEvidenceApi`由PLT物理Owner前向交付，边界如下：

- 八个写动作固定为`createImportBatch/appendSourceRecord/markStagedReady/claimStagedBatch/appendExternalMapping/appendMigrationIssue/completeReconciliation/closeMigrationIssue`；另有只读`pageSourceRecords`按`sourceRecordId`升序读取冻结来源，页大小为1～500，空页不放大范围；
- 所有输入tenant必须与`TenantContextHolder`一致。批次由`ownerContextCode + purposeCode + releaseId + sourceSystem + sourceTable`定位，重跑通过幂等键判定，不以文件路径或当前时间作为业务身份；
- `appendSourceRecord`只允许在`IMPORTING`追加，且命令的tenant、`sourceSystem/sourceTable`必须与锁定批次完全一致；不一致分别形成租户、来源身份或批次状态冲突；
- `markStagedReady`是严格判别联合：`READY`携带并校验manifest事实后进入`STAGED_READY`；`FAIL_IMPORT`禁止携带成功manifest事实，必须携带封闭导入失败码并进入`FAILED`。可重试基础设施失败不得写成终止导入失败；
- `claimStagedBatch`要求调用方已有外层事务，按`createTime,id`稳定领取一个`STAGED_READY`批次并返回权威版本。后续分类登记和`completeReconciliation`加入同一事务，失败整体回滚到领取前；
- `appendExternalMapping`为严格判别联合：`MAPPED`必须携带稳定排序的至少一个目标，`RETAINED`禁止携带目标。映射和问题只能引用同tenant、同`RECONCILING`批次的冻结来源；
- `completeReconciliation`按唯一来源行重算并要求`sourceCount = mappedCount + issueCount + retainedCount`且每个来源恰有一种最终分类，再以领取版本一次CAS进入`COMPLETED`；完成后禁止追加来源、改变分类、覆盖映射或重算批次；
- `closeMigrationIssue`只允许最终`COMPLETED`批次的当前`OPEN`问题以CAS关闭；同键同载荷重放返回原结果，同键异载荷冲突，需要重新迁移时创建引用原问题的新批次。

周报/日报不提供独立 API；周期性展示复用指标快照。

F-SOL-003现已形成首个真实调用方，因此F-PLT-002前向增加`DynamicFormBusinessInstanceApi`与`DynamicFormBusinessObjectPolicyProvider`。动作封闭为修订发布/冻结使用及实例CREATE/READ/PATCH/COMPLETE/CLONE_SOURCE/CLONE_TARGET/FILE_READ/FILE_WRITE，inspect与锁定重验不得换动作。对受信业务Owner实例，动态表单文件Provider将`ARCHIVE/INVALIDATE`委托为`FILE_WRITE`；它们只能从F-PLT-001现有文件管理REST进入，命令端另行校验`pms:file:archive`，手工实例和动态表单页面均不获得该能力。SOL只能经该API组合实例；业务实例无用户REST，不得访问PLATFORM Service、Mapper或表。外层SOL命令拥有唯一幂等和业务审计，PLT写方法/持锁重验必须加入既有事务。

## 13. Device Access & Collection API

适用 Requirement：INT-12、EXE-03～EXE-04、CUT-06、INS-02、INS-04、NFR-02。

### 13.1 凭证与授权

| 路径 | 操作 | 安全响应 |
|---|---|---|
| `/device-credentials` | `POST`, `GET list` | 创建请求可含秘密；响应只返回 ID、名称、协议、掩码、版本、创建人和状态 |
| `/device-credentials/{id}` | `GET` | 永不返回密码、私钥、Token 或可逆密文 |
| `/device-credentials/{id}/actions/rotate` | `POST` | 生成新版本；旧任务仍引用旧授权快照 |
| `/device-credentials/{id}/actions/revoke` | `POST` | 阻止新任务；运行中任务按实际停止点留痕 |
| `/device-credentials/{id}/grants` | `POST`, `GET` | 用户、设备、协议、命令模板、有效期五元组完整校验 |

### 13.2 创建采集任务

`POST /collection-tasks` 支持二选一认证：

```json
{
  "sourceContext": "IMP",
  "sourceObjectType": "ConfigurationCollectionResult",
  "sourceObjectId": "123",
  "deviceId": "456",
  "protocolCode": "SSH",
  "commandTemplateId": "789",
  "commandTemplateVersion": 3,
  "authentication": {
    "mode": "SAVED_CREDENTIAL",
    "credentialId": "101",
    "credentialVersion": 2
  }
}
```

或：

```json
{
  "sourceContext": "INS",
  "sourceObjectType": "InspectionTask",
  "sourceObjectId": "124",
  "deviceId": "456",
  "protocolCode": "SSH",
  "commandTemplateId": "789",
  "commandTemplateVersion": 3,
  "authentication": {
    "mode": "TEMPORARY_INPUT",
    "username": "write-only",
    "password": "write-only",
    "saveAsCredential": true,
    "credentialName": "explicit-name"
  }
}
```

临时密码是 write-only，请求日志、审计详情、错误、事件和任务响应均不出现。未选择保存时，任务以 `TEMPORARY_INPUT` 创建并保存 `temporaryUsername` 用于审计。`saveAsCredential=true` 时，平台在同一业务命令中先创建加密凭证及默认仅当前用户可用的授权，再以 `SAVED_CREDENTIAL` 创建本次任务；响应返回新 `credentialId`、`credentialVersion` 和任务所冻结的 `grantSnapshotId`。凭证创建失败则整个请求失败且不创建任务，不得静默按临时模式继续。

| 路径 | 操作 | 规则 |
|---|---|---|
| `/collection-tasks` | `POST`, `GET` | 创建需 Idempotency-Key；批量按设备产生独立任务 |
| `/collection-tasks/{id}` | `GET` | 返回外部状态原值、映射状态和结果引用，不返回秘密 |
| `/collection-tasks/{id}/actions/dispatch` | `POST` | 仅 DAC 执行身份；签发任务级短期执行授权 |
| `/collection-tasks/{id}/actions/retry` | `POST` | 创建引用原任务的新任务；临时密码必须重新输入 |
| `/internal/dac/callbacks/{provider}` | `POST` | 验签、来源身份、callback 幂等和顺序校验 |
| `/internal/collection-tasks/{id}/actions/confirm-consumption` | `POST` | IMP/CUT/Inspection 以 `consumerContext + consumerObjectType + consumerObjectId + resultVersion` 幂等确认消费；必须匹配任务冻结的必要消费者 |

任务响应必须返回 `completionMode`。业务入口只能创建 `BUSINESS_CONSUMPTION`：成功回调生成结果引用后保持 `RESULT_AVAILABLE`，直至匹配的消费确认到达。独立中心由服务端固定为 `CALLBACK_TERMINAL`，有效成功终态回调可直接完成；失败、取消和安全异常不得调用消费确认，也不得转换为成功完成。

## 14. 集成入口与降级 API

适用 Requirement：INT-01～INT-07、INT-09～INT-10、INT-12，以及对应领域 Requirement。

- 外部同步使用 `/internal/integrations/{system}/{object}:sync` 或受控消息 Consumer；普通用户不可调用。
- HR目录同步统一通过内部契约 `/integration/hr/directory` 接收必要人员、组织、岗位和任职状态的增量/全量批次；按来源键、来源版本与批次幂等，不返回为业务授权成功。
- 人工补录/平台记录是独立降级命令，必须保存来源和原因，不伪造外部 sourceKey。
- 重试沿用原同步批次/幂等范围；补偿和对账 API 只对集成运维角色开放。
- CRM 是统一系统名称；文档和 API 不再使用 SMS 表示另一套客户系统。
- HTTP 2xx 只表示请求被接收或传输成功，业务完成以领域回写、回调或对账结果为准。

## 15. 兼容、废弃与契约测试

### 15.1 历史接口处置

| 历史模式 | 目标处置 |
|---|---|
| 通用 `/create`、`/update` | 草稿兼容；状态字段在适配层移除/拒绝 |
| `/submit`、`/approve` 等状态接口 | 代理到新 command，并要求版本与幂等键 |
| `/delete` | 仅允许可删除草稿；历史事实返回不可删除错误 |
| `/acc-maintenance-transition/renew` | 明确废弃并冻结，不映射到 ServiceHandover |
| 本地技术公告 create/publish/disable | V1/V2 冻结；只保留受控历史查询 |

### 15.2 契约测试最低集合

每组 API 至少覆盖：正常、输入错误、功能权限拒绝、数据范围拒绝、非法状态、版本冲突、幂等重放、同键异请求、部分失败、外部超时和敏感字段不泄露。树移动另测成环拒绝与投影水位；设备归属另测并发唯一；DAC 另测临时密码不落库/不入日志/不入事件。

## 16. API 门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| API 可追溯 Requirement | PASS | 第 5～14 节每个接口组明确 Requirement 范围 |
| 状态只能通过命令改变 | PASS | 第 3 节及各聚合 actions 接口 |
| 服务端授权不可绕过 | PASS | 第 4 节；租户/项目/设备/文件/凭证范围 |
| 幂等和并发输入明确 | PASS | Idempotency-Key、If-Match、错误响应 |
| 敏感信息不回显 | PASS | 第 13 节 write-only 临时秘密和掩码响应 |
| 已排除/后置能力无新接口 | PASS | 无续保、周报日报、工单时效、历史工单/工时用户访问和本地公告治理入口 |

本分册可进入事件、集成、文件和异常幂等交叉评审；接口正式发布前仍需生成 OpenAPI、契约测试和兼容清单。

## 11.1 F-CUS-001与F-AST-001稳定API增量

- CUS业务API固定使用`/api/v1/pms/customers`。旧project客户API立即退出，不保留转发或双写实现。
- AST当前设备业务API固定使用`/api/v1/pms/devices`；旧`/pms/equipment`继续提供历史查询。普通角色的旧写入口退役，`super_admin`可对旧设备模型执行创建、更新、删除和状态变更，但不得代理或双写AST。
- 客户动作路径为`/{id}/actions/{disable|delete|restore}`；删除必须完成全部`CustomerReferenceGuardApi`检查。
- 客户地点路径为`/{id}/locations`，仅维护CUS对Address/Site的引用，写入前调用AST校验。
- 设备客户归属命令固定为`POST /api/v1/pms/devices/{id}/actions/assign-customer`，携带目标客户、关系类型、原因、`If-Match`和幂等键。
- 设备详情采用固定摘要外壳和分Tab DTO；每个Tab统一返回`sourceSystem/sourceVersion/dataAsOf/syncStatus`。官网信息通过`KnowledgePublicProductInfoQueryApi`查询KNO已发布版本。
- 配置Log下载链接默认5分钟、可配置、绑定当前用户和文件；每次生成前重新校验设备查询与文件下载权限。

## F-PROJ-008 阶段推进API基线（GO）

### Readiness query

`GET /api/v1/pms/projects/{id}/stage-advance-readiness`只返回当前阶段、服务端推导的相邻目标、Project/tree版本以及有序Gate/Ref结果；该结果是预览，不授权推进，命令仍须重新锁定重验。

### Advance command

`POST /api/v1/pms/projects/{id}/actions/advance-stage`

- Header：`If-Match`提供`expectedProjectVersion`，`Idempotency-Key`必填；
- Body：`expectedCurrentStage`、`expectedTreeVersion`；不得提交目标阶段、actor、tenant或门禁结论；
- Success：`projectId/beforeStage/afterStage/projectVersion/stageSnapshotId/gateEvaluationSummary`；
- 仅接受当前S0～S3并由服务端推导相邻S1～S4。S4→S5返回专用路径提示，不代理调用验收入口。

内部 `StageAdvanceCommand`的规范摘要冻结tenant、actor、projectId、期望阶段/Project/tree版本。相同键同摘要返回原结果；同键异摘要冲突。

`GET /api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/process-definitions`列出该Gate可选择的BPM定义。PROJ先锁定项目、Gate与Reference，并重验`pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`；随后只以受信tenant和Reference冻结的`processDefinitionKey`调用Owner。响应按BPM定义身份稳定排序，仅返回`processDefinitionId/processDefinitionKey/name/selectable`，且只包含同租户、同key、当前可启动并通过本Gate定义约束的记录；不得复用或要求管理端`bpm:process-definition:query`，不得返回其他流程管理字段。

`POST /api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/actions/start-process`使用`If-Match + Idempotency-Key`启动关联Gate流程。Body只允许可空`processDefinitionId`：为空时按Gate Reference冻结的`processDefinitionKey`选择最新生效定义；非空时仅作为授权用户从上述同key列表选择的历史定义ID，服务端仍必须重验定义ID归属和可启动状态。tenant、actor、definitionKey、businessKey及系统变量均由服务端构造，客户端不得覆盖；规范摘要包含可空processDefinitionId，同operation异选择稳定冲突。

### Gate Reference Owner Fact SPI

既有`pms-module-project-api`加性声明类型化SPI，稳定方法为：

```java
String providerKey();
ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query);
```

`ProjectStageGateFactQuery`只由PROJ服务端构造，固定包含`tenantId/projectId/currentStageCode/gateId/gateCode/gateVersion/gateReferenceId/gateReferenceVersion/refType/refCode`；其中Gate、Reference版本来自已锁定实例，`refCode`来自创建时冻结值，既有`refVersion`不得进入Query。`ProjectStageGateFact`固定返回`providerKey/refType/ownerObjectKey/ownerBusinessVersion/factVersion/outcome/unmetCode`，三个Owner身份/版本字段均为非空String：本地/ACC行用稳定ID、业务状态和十进制row.version，BPM用processInstanceId、实际processDefinitionId和`status:endTime`（运行中使用`status:startTime`）组成可重验事实版本，不使用摘要或当前时间。outcome封闭为`SATISFIED/UNSATISFIED/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`。Provider以`MANDATORY`加入推进事务；Registry只接受02d分册的固定一对一providerKey，不接受调用方指定、通配或重复实现。

| refType / providerKey | 精确Owner对象键与版本 | 唯一`SATISFIED`谓词 | 其他稳定结果 |
|---|---|---|---|
| `TASK / PROJ_TASK` | 同租户同项目唯一`task_code=refCode`的ProjectTask；`factVersion=row.version` | `status=DONE` | `CLOSED -> TASK_CLOSED_NOT_COMPLETED`；其余已知状态`TASK_NOT_DONE`；缺失/重复/未知状态为依赖不可判定 |
| `MILESTONE / PROJ_MILESTONE` | 同租户同项目唯一`milestone_code=refCode`的ProjectMilestone；`factVersion=row.version` | `status=ACHIEVED` | `PENDING -> MILESTONE_NOT_ACHIEVED`；缺失/重复/未知状态为依赖不可判定 |
| `DELIVERABLE / ACC_DELIVERABLE` | ACC中同租户同项目唯一`deliverable_code=refCode`的应交根；`factVersion=row.version` | `status=ACCEPTED` | `PENDING/SUBMITTED -> DELIVERABLE_NOT_ACCEPTED`；缺失/重复/未知状态为依赖不可判定 |
| `STATE / PROJ_STATE` | 受控状态目录项；V1阶段完成码仅允许`S0_COMPLETED`～`S6_COMPLETED`并精确映射同项目对应ProjectStage，`factVersion=stage.version` | 对应Stage `status=DONE` | 其他已知状态`STATE_NOT_REACHED`；未登记状态码在模板发布时拒绝，运行时出现则依赖不可判定 |
| `APPROVAL / BPM_APPROVAL` | `refCode=processDefinitionKey`，按固定businessKey/变量唯一关联最新审批尝试并读取实例实际processDefinitionId | 关联实例已结束且状态原值等于`BpmProcessInstanceStatusEnum.APPROVE(2)` | 未启动、`RUNNING(1)`、`REJECT(3)`、`CANCEL(4)`分别为`APPROVAL_NOT_STARTED/RUNNING/REJECTED/CANCELLED`；定义key/实例身份冲突、多个活动实例或未知状态为依赖不可判定 |
| `PROCESS / BPM_PROCESS` | `refCode=processDefinitionKey`，按固定businessKey/变量唯一关联最新流程尝试并读取实例实际processDefinitionId | 关联实例已结束且状态原值等于`BpmProcessInstanceStatusEnum.APPROVE(2)` | 未启动、`RUNNING(1)`、`REJECT(3)`、`CANCEL(4)`分别为`PROCESS_NOT_STARTED/RUNNING/REJECTED/CANCELLED`；定义key/实例身份冲突、多个活动实例或未知状态为依赖不可判定 |

APPROVAL/PROCESS启动使用同一公共模块的PMS窄Owner命令：

```java
ProjectStageGateProcessDefinitionFact inspectDefinitionKey(
        ProjectStageGateProcessDefinitionQuery query);
List<ProjectStageGateProcessDefinitionFact> listSelectableDefinitions(
        ProjectStageGateProcessDefinitionSelectionQuery query);
ProjectStageGateProcessStartFact startProcess(ProjectStageGateProcessStartCommand command);
```

检查Query只携带受信tenant及Gate Reference冻结的`processDefinitionKey`；同一适配器检查该key存在当前生效定义，并检查其BPMN，存在任一UserTask候选策略`START_USER_SELECT(35)`即返回不支持。历史选择Query同样只携带受信tenant和冻结key，Provider按租户与key裁剪Flowable定义，并逐项计算`selectable`；只返回当前可启动且BPMN满足Gate约束的定义身份，不返回Flowable version作为PMS输入。Gate Reference既有`refVersion`只保留历史，新写为空且不进入接口；不新增BPM版本字段、版本表、解析规则或第二映射真值。

Command仅由PROJ服务端构造，包含`tenantId/actorUserId/projectId/currentStageCode/gateId/gateReferenceId/refType/processDefinitionKey/selectedProcessDefinitionId?/businessKey/variables/operationId`；businessKey必须等于`PROJECT_STAGE_GATE:{gateReferenceId}`，variables只允许Gate冻结身份，客户端除可选定义ID外不得覆盖。调用前PROJ必须重新校验`pms:project:update`、`ProjectScopeApi.ACTION_MANAGE`和当前有效`PROJECT_MANAGER`，三者是PMS专用Gate流程的唯一正式发起授权；缺任一项不调用Owner。Yudao `startUserIds/startDeptIds`继续只约束通用Yudao发起入口，PMS不查询或复制该事实；启动时再次检查本次实际定义的BPMN不存在`START_USER_SELECT(35)`。

Fact返回`processInstanceId/processDefinitionId/processDefinitionKey/businessKey/outcome=STARTED|REPLAYED`，不返回独立版本号。接口位于`pms-module-project-api`，`pms-module-integration`增加对该API模块的单向依赖：selectedProcessDefinitionId为空时使用Flowable RepositoryService按key取得最新生效定义，非空时按ID取得定义并校验同key和可启动状态，再由RuntimeService按实际definitionId启动。启动前通过Flowable `Authentication.setAuthenticatedUserId(String.valueOf(actorUserId))`设置发起人并在finally清除；服务端强制写入`PROCESS_START_USER_ID=actorUserId`、`PROCESS_STATUS=RUNNING(1)`及`_FLOWABLE_SKIP_EXPRESSION_ENABLED=true`。Command不提供`startUserSelectAssignees`，Gate冻结variables若占用任一系统变量即拒绝。同operation同摘要重放原Fact且不得再次启动，异摘要冲突。不得依赖Yudao BPM内部Service/Mapper/DO，不得修改Yudao API/Service，也不得解析processDefinitionId或taskDefinitionKey生成PMS版本。

BPM事实Provider与上述启动Provider可由同一集成适配器承接，只通过Flowable运行/历史事实按businessKey锁定/查询全部尝试，逐项校验租户、项目、Gate、Reference、定义key和实例实际processDefinitionId。允许驳回/撤回后重新发起时，以`startTime + processInstanceId`确定唯一最新尝试；多个活动实例、变量缺失或不一致均`DEPENDENCY_UNAVAILABLE`。状态只读取`BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS`的整数原值：1/2/3/4分别对应RUNNING/APPROVE/REJECT/CANCEL，`factVersion`使用该整数原值及startTime/endTime，不比较不存在的字符串状态。不存在实例必须返回业务未满足`*_NOT_STARTED`，不得解释为通过。

模板发布还必须验证S0～S3每阶段至少一个EXIT Gate、每Gate至少一个引用、Provider存在性，并拒绝新APPROVAL/PROCESS引用写入refVersion；APPROVAL/PROCESS通过`inspectDefinitionKey`验证当前生效Flowable定义存在且不含`START_USER_SELECT(35)`。运行时零EXIT Gate或零引用分别返回`EXIT_GATE_MISSING/EXIT_GATE_REFERENCE_MISSING`且outcome为`DEPENDENCY_UNAVAILABLE`。
