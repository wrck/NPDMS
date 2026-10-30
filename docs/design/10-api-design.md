# SDS Phase 2：API 设计

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；接口组在第 5～14 节回指具体 Requirement
> Owner：SDS Phase 2 应用与接口架构
> 前置设计：`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`

## 1. API 边界与版本

2026-10-27合同号来源裁决：`GET /api/v1/pms/projects/office-readonly-search`参数`projectName`、`contractNo`（至少一项完整精确值，两项均填取交集）、原分页参数；仅在当前启用经理的同一有效公司部门范围内查询。名称或合同号任选一项精确匹配；合同号可匹配PROJ主档登记号，或经COM公开Owner契约解析的商务当前有效关联合同号，两项均提供时名称与合同号AND。结果`visibility=OFFICE_READONLY`，不形成普通项目集合。默认`/pms/projects/page`、`statistics`与项目树取消项目经理默认办事处概况。

商务合同关联公共Owner契约为`ProjectContractQueryApi.findProjectIdsByExactContractNo(ExactContractQuery)`：只返回当前租户、当前有效关联合同号对应的候选项目ID，合同号按完整字节值精确比较，并复用当前三条有效关系链。候选ID仅供PROJ筛选，不能授予权限；PROJ查询仍须按同一条有效公司部门范围行及精确项目条件取交集，并在每次读取时重新校验身份、租户、范围、名称/合同号原始条件和目标，任何Owner/范围不可用均失败关闭。COM商务DAL及业务表归COM所有，PROJ消费者不得跨域读取其表。

独立只读页面每次业务GET携带`X-Pms-Office-Project`、URL编码的`X-Pms-Office-Name`及`X-Pms-Office-Contract`，服务端重验上述原始条件与授权，条件并非可长期复用的签名Grant；没有条件、伪造项目/主体、过期/停用组织范围均拒绝。有效上下文只为该项目的VIEW生效，关联只读CTE携带Owner生成的单个`officeReadonlyProjectId`；默认构造为空，消费者不得自行构造。非GET及非JSON业务读端点、下载/导出/内容/打印/预签名端点拒绝，不授予EDIT/MANAGE或更改流程候选。普通本人/显式授权入口和服务经理区域范围保持。

该通道还逐请求校验`pms:project:query`，返回`Cache-Control: no-store`；只读响应剔除旧文件直链和办理凭证，保留文件身份、名称及版本元数据，不修改原记录。正常业务响应不受影响，接口完成或异常后清除请求内上下文。

2026-10-26区域权限补齐：`ProjectViewReadScopeApi.ReadScope`增加Owner解析的只读`organizations: List<Organization(companyId, departmentId)>`，只承载有效服务经理完整范围；公司级departmentId为空。保留既有五参数构造，其组织范围为空，不能隐式提权。PROJ固定SQL按每行公司与可选部门共同匹配当前项目，不直读SYSTEM表；prepare、树发现和最终项目范围解析分别调用SYSTEM公开资格/范围API，失败不降级为租户全量。项目经理概况范围不进入该关联读取契约或`ProjectScopeApi.resolveAllCurrent`的完整项目集合。

`GET /pms/projects/page`列表行新增`visibility=FULL|SUMMARY`；SUMMARY只返回SDS07批准的概况白名单，服务端在展示增强后再次按白名单构造响应。列表统计包含概况；客户、合同、成员、设备、业务属性、超期及私有时间筛选仅返回完整范围结果。`GET /pms/projects/{id}`、成员、实例、站点、下载、关联对象和写入口继续要求原完整范围；概况页面使用列表已返回数据，不通过详情接口扩大权限。

组织来源完整范围按actionCode区别：PROJECT_VIEW可覆盖关闭项目；PROJECT_EDIT/PROJECT_MANAGE额外要求项目ACTIVE。关联只读上下文仅按VIEW解释，不把它作为写操作的资格事实。

【建议】新契约使用逻辑基础路径 `/pms/v1`；网关或基础平台统一前缀不写入领域契约。现有 `/pms/*` Controller 作为兼容适配入口，逐项代理到新的应用命令/查询，不再直接形成第二套业务规则。

- 用户端、响应式 Web 和未来桌面端复用同一领域 API。
- 外部系统回调、内部 Context 调用和用户 API 使用不同认证入口与执行身份。
- API 版本只处理兼容演进，不用于绕过 Requirement 变更。
- V3 和 OUT_OF_SCOPE 能力不得通过隐藏接口提前发布。

### 1.1 领域模块路径与命名规范

新增 PMS Business API 固定使用物理前缀 `/api/v1/pms/{业务实体}`（实体复数、kebab-case；状态命令遵循第3节 `POST /{resource}/{id}/actions/{command}`），与工程基线的 V2 规范一致。

存量旧链 `/pms/*` 为冻结兼容入口，不新增；同一物理模块内的旧链路径、菜单权限标识和表名必须使用一致的领域前缀，迁移或归属修正调整时同步对齐（示例 V253）：

| 物理模块（领域） | 旧链 URL 前缀 | 旧链权限前缀 | 表名前缀 |
|---|---|---|---|
| pms-module-project（PROJ） | `/pms/*`（存量无前缀） | `pms:{实体}:{动作}` | `proj_*`；`pms_*` 存量待逐项判定 |
| pms-module-engineering（SOL/IMP/KNO/RES/PLT 多域） | SOL：`/pms/sol-*`；IMP：`/pms/imp-*`；KNO：`/pms/kno-*`；RES：`/pms/res-*`；PLT：`/pms/plt-*` | 各领域前缀同 URL | SOL/IMP（工程实施领域）：`{sol|imp}_eng_{实体}`（保留 eng_ 段）；PLT/KNO/RES（非工程实施领域）：`{plt|kno|res}_{实体}`（无 eng_ 段，V258） |
| pms-module-acceptance（ACC） | `/pms/acc-*` | `pms:acc-*:*` | `acc_*` |
| pms-module-cutover（CUT） | `/pms/cut-*` | `pms:cut-*:*` | `cut_*` |
| pms-module-customer（CUS） | `/pms/customers`（存量无前缀） | `pms:{实体}:{动作}` | `cus_*` |

存量旧链按承接状态分级处置：**承接判定以功能完整替代为准，表或接口骨架存在不等于实现**。有功能完整承接的旧链保持冻结（不改名、不改语义）；无承接的旧链必须原地改为领域模型（表名改领域前缀、归属正确模块，URL/权限同步领域前缀；撞名实体加 `_record` 区分，V254/V255 为示例）。**eng 不是领域**：pms-module-engineering 物理承载 SOL 与 IMP 两个 bounded context，其表名、URL 与权限使用 `sol_`/`imp_` 领域前缀；存量 `pms_eng_*` 表逐项按领域对齐。领域归属修正迁移的代码必须落入目标模块后立即按本表对齐三项前缀，不得保留源模块命名。新链表名按 09 分册各 Context 目标表组命名（`acc_/cut_/cus_/imp_/sol_/srv_/com_/plt_/proj_`）。

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
| `/projects` | `POST`, `GET` | 创建、分页查询项目 | 创建需幂等键及必填非空白createReason；公司与办事处部门按同一组织范围校验；请求包含零到多个站点及一个可选主站点，或显式未解析文本降级；F-PROJ-004生效后所有成功创建须记录AUTO_UNIQUE或EXPLICIT_SELECTION的INITIAL_CREATE历史；查询服务端过滤 ProjectTreeScope；FB-03批准差量：分页增加可选customerCode，非空时按项目主档客户编码精确匹配，与其他条件取交集，空值不筛选，不扩大租户或项目权限范围；FB-02增加可选overdueOnly，不填为全部，true筛选存在未完成超期节点的ACTIVE项目，false筛选当前不超期项目，均与其他条件取交集 |
| `/projects/{id}` | `GET`, `PATCH` | 项目详情、可编辑属性 | `PATCH`不能修改状态、父节点、来源权威字段及四项模板输入属性；四属性只能走PM-07受控命令并追加历史；列表与详情状态展示增加overdueNodes/completedOverdueNodes，包含节点类型、ID、名称、计划结束、计划验收、完成时间和两个超期标识，复用既有项目授权；截止当日不超期，完成停止两类，历史只读 |
| `/projects/{id}/workspace` | `GET` | 项目概览六页签、Stage→ProjectTask导航和投影水位 | 不返回第二套导航真值；按ProjectTreeScope裁剪，任务子树按需加载 |
| `/projects/{id}/gantt` | `GET` | V2按同一ProjectTask、计划字段和TaskDependency返回甘特投影 | 只读展示，不建立第二套任务事实；按ProjectTreeScope裁剪 |
| `/projects/{id}/tree` | `GET` | 直接下级、全部后代、完整上级链、指定业务层级或节点定位 | 先执行ProjectTreeScope；返回同一完整`treeVersion`、裁剪结果和稳定游标 |
| `/projects/{id}/actions/move` | `POST` | 移动到目标父项目 | `Idempotency-Key`、`If-Match`、无环校验、树变更批次和新`treeVersion` |
| `/projects/{id}/actions/classify` | `POST` | 四项模板输入的受控人工调整 | 以5.5为唯一详细契约；追加MANUAL_ADJUSTMENT历史，不更换冻结模板 |
| `/projects/{id}/actions/assign-manager` | `POST` | 既有PM-08服务经理指派/改派 | 保留原生产契约；不把此接口现状当成联合指派或多项目经理已实现 |
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

PM-01创建请求可携带`serviceManagerUserId`作为人工确认的一级服务经理稳定用户ID；不传时不创建指派。传入时复用既有创建应用服务的指派权限、启用人员和公司/办事处范围校验，在创建事务内追加责任关系与审计；不以文本地点自动选人，也不代替项目经理或其他主责指派。

### 5.1 PM-02 项目拆分、树版本与进度契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-split-requests` | `POST` | 输入父项目、组合方案和稳定`clientItemKey`；返回草稿ID与`draftVersion` | 工程管理部或获授权服务经理；父项目ProjectTreeScope、租户和功能权限同时满足 |
| `/project-split-requests/{id}` | `GET`, `PUT` | 读取/更新草稿、范围项和逐项校验结果 | APPLIED不可编辑；`If-Match`校验草稿版本；失败草稿保留 |
| `/project-split-requests/{id}/actions/{preview|validate}` | `POST` | 服务端返回子项目方案、Commerce/AST/组织校验结果、`previewHash`与各Owner水位 | 无业务写入；客户端预览摘要不能作为确认依据；权威范围不可用时禁止确认 |
| `/project-split-requests/{id}/actions/apply` | `POST` | `Idempotency-Key`、`If-Match`及父项目/范围/树期望版本；返回全部子项目、范围分配、`changeBatchId/treeVersion` | 服务端重新校验；全部子项目、模板实例、范围、树、审计、Outbox和幂等完成点原子提交 |
| `/projects/{id}/tree` | `GET` | `queryType=CHILDREN/DESCENDANTS/ANCESTORS/BUSINESS_LEVEL/LOCATE/FULL`；`FULL`一次返回锚点所在根项目的全部可见分支，不使用游标、不受分页大小截断；其FULL可见节点附带`displayDetails.office/serviceManager/projectManager`（当前有效经理姓名）；其他查询保持原分页契约；返回`treeVersion/items/nextCursor/updating` | 固定同一完整版本；服务端先执行租户与ProjectTreeScope；无权路径只返回必要占位；根概要和占位节点不返回办事处或经理信息；FULL拒绝游标和业务层级筛选 |
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

设备清单的SQL关联读取使用PROJ公开`ProjectViewReadScopeApi.prepare(ProjectAllScopeQuery)`及API资源中的固定`ProjectViewReadScopeSql.commonTables`，PLT有效授权条件由API固定`ProjectViewGrantReadSql.effectiveGrants`提供；消费者只组合Owner维护的片段，不自行查询对方表、不传入SQL文本或表名。上下文只含当前租户/主体、既有管理员身份、已复核根树版本与读取时点，不枚举全部项目ID。COM通过`ContractDeviceVisibilityApi.prepareReadScope`及固定`ContractDeviceReadScopeSql.commonTables`组合正式项目可见范围，仍要求原合同号唯一、公司冲突保护和既有有效项目合同关系；AST在SQL中按原文精确匹配合同，计数与分页复用同一授权CTE。该契约仅供关联只读，不替代修改操作的Owner授权和版本重校验。

### 5.3 PM-05 借货项目转销契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-conversions` | `POST` | 输入 sourceProjectId、targetProjectId、formalSalesBusinessId、对象复用清单、逐台设备处置、Idempotency-Key；返回 conversionId、状态和逐项结果 | 调用人同时具备源/目标项目管理权限；目标为有效正式销售项目；同一源项目无其他生效目标；幂等键为源项目+正式销售业务ID |
| `/project-conversions/{id}` | `GET` | 返回源/目标、处理中/部分失败/待处理/已完成状态、成功/失败对象汇总、来源版本和设备处置结果 | 只返回同时满足源/目标项目数据范围的内容；敏感对象继续执行原对象权限 |
| `/project-conversions/{id}/actions/retry-failed` | `POST` | 输入 expectedVersion 和失败 itemIds；返回原批次的新版本及逐项结果 | 仅重试失败/待处理项；成功引用/副本不得重复生成；设备归属重新校验 assignmentVersion |

对象清单的 `handlingMode` 只能是 `READ_ONLY_REFERENCE` 或 `DERIVED_COPY`；默认前者。派生副本必须返回 `sourceObjectId/sourceVersion/derivedObjectId`。只有所有项成功后服务端才完成转销并归档源项目，不提供客户端直接设置完成/归档状态的接口。

### 5.4 PM-06 同一项目范围追加 API

#### 既有关联合同订单范围初始化与同步（2026-10-12专项批准）

PM-01合同建项在原创建事务绑定合同后调用COM `ProjectContractScopeApi.initialize`：按项目—合同—全部订单—订单行取源，初始化当前可承接数量；PROJ传入初始化完成后的真实项目版本及树版本。COM以MANDATORY加入事务，合同绑定、项目、初始范围、明细、版本、审计及Outbox全有或全无。无已确认订单时保持既有离线建项边界，不以人工数量补造范围。

`POST /api/v1/pms/project-contract-scopes/{projectId}/actions/preview`只计算差异；`POST .../actions/synchronize`根据已预览的sourceDigest同步。请求携带expectedProjectVersion、expectedTreeVersion、expectedScopeVersion；确认另需sourceDigest及Idempotency-Key。tenant/actor来自登录上下文，入口要求`pms:project:update`及`pms:commerce:scope:query`；实际新增、调整、释放仍逐类校验COM既有`scope:assign/adjust/release`权限。数据范围要求当前根项目MANAGE、活动生命周期和合同可见性。建项自动初始化属于已授权建项事务的派生事实。子项目不能通过同步入口扩为整份合同范围。

来源仅限项目已显式关联的合同；不接受客户端订单ID或分配数量。按订单行稳定ID去重，读取全部订单而非属性建议所选主订单；父项目目标数量为ERP有效数量减其他项目当前承接数量。只追加/替换父项目当前范围，子项目及其历史不改。取消、减量涉及其他项目承接量、序列号明细或验收保护时明确拒绝；未知依赖失败关闭。来源、项目、树、范围或占用在预览后变化，确认拒绝并要求重算。

成功同步关闭被替换区间、追加新范围与来源证据，同一次命令只递增一次项目范围版本；无差异不推进版本。同键同载荷重放返回原结果，异载荷拒绝。原始订单及ERP字段只读。新增合同的PM-06申请、批准、任务与验收覆盖规则仍由下文契约承接；订单同步不声明新增范围已实施或已验收。

Requirement：PM-06@V2、COM-01@V1。`POST /api/v1/pms/projects/{id}/scope-append-requests`创建待评估申请；`POST /api/v1/pms/scope-append-requests/{id}/actions/submit`冻结申请及审批实际定义；`POST .../actions/apply`只消费已批准且未失效版本。

Body包含expectedProjectVersion、expectedScopeVersion及ERP合同/订单行稳定引用、数量、单位；tenant、actor和项目由服务端确定，Header为If-Match、Idempotency-Key。返回申请、原/新scopeVersion、Owner事实版本、逐阶段影响、未满足项及被新建任务/交付件/验收绑定的引用。

PROJ先锁当前项目及有效责任；COM在同一事务锁唯一项目水位和按稳定键排序的订单行，重验可用数量、冲突和来源版本。随后PROJ写补充任务/门禁，ACC写精确范围绑定。任一步失败整体回滚；批准仍可追溯但不产生半生效范围。已闭环项目、跨项目范围、超量、旧版本和重复异载荷均拒绝。已批准方案、报告和旧范围不可覆盖。

唯一新增范围事件为COM的ProjectScopeAppendApplied，事务提交后供读模型和对账使用，不作为异步补齐强制范围/任务的借口。PM-05仍使用独立转销契约；旧project-phase-groups端点只属于历史设计，不是PM-06候选接口。

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
| `/acceptances`、`/acceptances/{id}/report-versions` | 查询活动/版本、create/update draft、`publish-version`、`revoke-current-version`、`complete-activity` | 草稿可与旧EFFECTIVE并存，发布/替换原子切当前，撤销不恢复旧版；修订018的报告完备及初验前置按冻结配置，证据有效不等于通过。现有任务绑定接口保留原适用范围，独立创建/完成/范围契约见Q-TPLACC-001，报告不反推范围绑定 |
| `/acceptances/{id}/actions/send-confirmation` | `POST` | ACC-01 V2按短信/邮件和钉钉推送培训确认链接；分别记录受理/送达，送达不等于客户确认，失败保留V1链接/扫码入口 |
| `/delivery-artifacts` | `check-completeness`、`review`、`archive` | 齐套、审核、归档是不同命令；文件版本固定 |
| `/closure-gates/{projectId}` | `GET` | 返回所有后代项目的门禁快照和水位 |
| `/project-closures` | `create`、`submit`、`review`、`complete` | complete 发布事件请求 Project 关闭，不直写 Project 表 |
| `/service-handovers` | create、`submit`、`accept` | 只做持续服务交接，不提供 renew/续保接口 |
| `/satisfaction-questionnaire-templates`、`/satisfaction-questionnaire-templates/{id}/revisions` | GET/POST | `query`读取根和修订；`manage`创建根/草稿修订。修订命令携带五维适用条件、优先级和唯一`schemaVersion=1`配置包，不接受PUBLISHED状态、current指针或服务端审计字段 |
| `/satisfaction-questionnaire-templates/{id}/revisions/{revisionId}/actions/publish` | POST | `manage`权限、expectedRevisionVersion和Idempotency-Key；服务端验证受控题型/策略/舍入、封闭Schema、编码唯一、分值/weight/threshold可判定及五维歧义后原子发布修订并切换根指针，失败保持DRAFT |
| `/satisfaction-tasks` | list/detail | 按租户、项目范围和责任人范围读取；由受信业务时点或下述受控手动入口初始化 |
| `/satisfaction-tasks/actions/start?projectId={id}` | POST | 2026-09-20需求方批准的手动首轮入口（ACC-02/F-ACC-002）；要求`manage`、`PROJECT_EDIT`与`Idempotency-Key`，actor/tenant从认证上下文取得。复用项目冻结问卷、责任人和交付件关联，无需等待初验；返回`CREATED/REPLAYED`及taskId/questionnaireId/collectionKey/taskRevisionNo/taskVersion。手动来源为`ACC/SatisfactionManualInitiation`；项目任务事实锁内与自动入口共同查重，已有首轮直接返回、不重复发布创建事件，失败/失效仍走整改重收 |
| `/satisfaction-tasks/start-options?projectId={id}` | GET | 2026-09-21补充（ACC-02/F-ACC-002）：`manage`和`PROJECT_EDIT`；返回是否已有冻结配置及当前项目可选未完成任务。无配置时上述start额外接受`projectTaskId/templateId/revisionId`，必须明确选择当前发布修订；已有配置时这些参数不得替换原配置。通过PROJ `ProjectManualSatisfactionApi.options/freeze`校验项目归属、锁定项目和首次冻结，不修改生命周期；ACC `SatisfactionQuestionnaireTemplateApi.inspectPublished`只接受当前发布版，已有冻结读接口可读SUPERSEDED历史版。没有任务交付件时ACC创建`D-SAT-MANUAL-{projectTaskId}`可选报告载体；有唯一载体时复用，歧义时拒绝。与问卷及首轮创建同事务回滚 |
| `/satisfaction-tasks/{id}/actions/{assign|recollect}` | POST | 指派只接受获授权项目成员；recollect要求前一失败/失效Result、`remediationRequestId`和整改证据，在ACC事务内先形成不可变`SatisfactionRemediationFact`，再以该Fact为新trigger创建同collectionKey的`taskRevisionNo+1` Task/Questionnaire；source仍为首任务原始业务Fact。同整改request/Fact同载荷重放返回原revision，异载荷冲突，不回退旧状态 |
| `/satisfaction-tasks/{id}/access-grants` | POST | 创建V1受控链接；二维码仅表示同一链接；令牌只返回一次，库内仅存摘要，V2自动发送不在本Feature实现 |
| `/satisfaction-questionnaires/{token}` | GET | 令牌只读返回唯一ACTIVE问卷的冻结题目和必要展示事实，不返回项目其他数据或内部规则实现 |
| `/satisfaction-questionnaires/{token}/files` | init-upload、complete-upload | init携带最终提交共用的`requestId`和单文件`operationId`；ACC先验证ACTIVE grant并从既有Response预留幂等事实取得服务端`responseId`，PLT返回服务端槽位身份，只允许签字/附件目标和安全元数据 |
| `/satisfaction-questionnaires/{token}/responses` | POST | 请求体携带初始化返回的`responseId`、同一`requestId`、答案及待核对文件句柄；ACC重放Response预留并要求ID相等，调用PLT最终锁定重验后才使用该ID追加Response。答案仅允许`{"answers":[{"questionCode":"...","value":...}]}`；服务端按冻结配置校验和判定，客户不能提交score/passed/threshold/weight/strategy或覆盖旧答案 |
| `/satisfaction-tasks/{id}/assisted-response-reservations` | POST | 已认证当前责任人以最终`requestId`预留并重放服务端`responseId`；不创建空Response |
| `/satisfaction-tasks/{id}/assisted-files` | POST | 已认证当前责任人初始化现场协助签字/附件槽位；服务端生成`fileSlotKey/fileSequence` |
| `/satisfaction-tasks/{id}/assisted-files/{sessionId}/complete` | POST | 已认证当前责任人完成同一槽位上传；重验双权限、Owner、项目范围和会话身份 |
| `/satisfaction-tasks/{id}/assisted-responses` | POST | 重放同一预留并最终锁定重验PLT规范文件事实后提交；记录协助人与客户联系人 |
| `/satisfaction-results` | GET、export | 只读Result；导出按项目、字段、文件与租户裁剪并保存条件/范围/文件/下载审计 |
| `/satisfaction-results/{id}/actions/invalidate` | POST | ACC Owner失效当前有效达标Result；要求`pms:acceptance:satisfaction:manage`、`ProjectScopeApi(PROJECT_EDIT)`、`expectedResultVersion`、非空失效原因和`Idempotency-Key`。只关闭Result区间并写失效审计/Outbox，不重开或改写Task、Questionnaire、Response和历史文件 |

F-ACC-001仅冻结`pms:acceptance:report:query/write/complete/download`四个最小权限键。写入/换版只允许项目经理项目范围，完成命令同时校验ProjectTask范围与活动版本；查询、历史版本和单文件下载分别执行项目树范围、FileBusinessScope和租户隔离。角色—权限映射保持正式授权配置，不以“全权限”删除服务端鉴权。

F-ACC-002冻结`pms:acceptance:satisfaction:query/manage/collect/export/download`五个最小权限键。`manage`控制指派、访问授权、Result失效和整改重收，`collect`只控制已认证现场协助；客户令牌不取得后台权限，只能访问唯一问卷的GET/文件上传/提交。所有后台路径保留ProjectScope、责任人、字段、FileBusinessScope和租户控制点；角色映射保持可配置。

满意度异步导出不得只创建ACC内适配器。`POST /satisfaction-results/exports`以服务端tenant/actor和Idempotency-Key调用PLT `ExportTaskApi.request`，固定`ownerContext=ACC/exportType=SATISFACTION_RESULT`并返回`taskId/status/queryLocation`。PLT公开`GET /api/v1/pms/export-tasks/{id}`、`POST /api/v1/pms/export-tasks/{id}/actions/retry`与`POST /api/v1/pms/export-tasks/{id}/access-ticket`；三者只允许原申请actor并通过唯一ACC `ExportBusinessDataProvider`重验`pms:acceptance:satisfaction:export`、ProjectScope、责任人、字段、文件和租户范围。retry还要求`expectedVersion`且仅把`FAILED + failure_retryable=true`以CAS恢复为REQUESTED并递增retry_count；同operation request只返回原Task，不隐式触发重试。Provider在申请时返回规范化条件/授权快照，在Job生成时重新裁剪数据，在重试/下载时重验当前权限；PLT不得读取ACC表，ACC不得写`plt_export_*`。缺Provider、重复Provider、权限撤销、范围漂移或任务过期稳定拒绝且不泄露对象存在性。

`invalidate`只接受服务端认证用户，tenant/actor不从请求体读取。ACC先以PROJ `ProjectScopeApi`按`PROJECT_EDIT`重验项目范围，再按Task链→Result锁序校验该Result为当前`EFFECTIVE + passed`且`expectedResultVersion`一致；在同一`PlatformCommandExecutionApi`事务中将其置`INVALIDATED`、关闭`effective_to`、清空current marker、保存`invalidation_reason_code`、可选原因摘要、操作者和时间并写`SatisfactionResultVersionChanged(INVALIDATED)` Outbox。旧Task状态、Questionnaire、Response、评分、签字和文件事实均保持不变；整改必须另走`recollect`新建revision。相同幂等键同载荷返回原结果，异载荷冲突；非当前、版本/范围冲突或Provider不可用时零写入。来源投影对称处理失效事件乱序与旧RECORDED重试：INVALIDATED只能撤销仍指向该Result版本的当前指针；旧RECORDED重试置CURRENT前必须按Result ID/version调用`SatisfactionResultFactApi`重验，已INVALIDATED或已有更新当前Result时只保留非当前历史及归档资格，不得恢复根指针。

ACC模块API固定为：`SatisfactionQuestionnaireTemplateApi.resolvePublished`在项目创建时唯一返回模板修订Fact；初验活动完成时先调用PROJ `ProjectWorkBindingFactApi.lockCurrentSatisfactionTaskByProject(projectId)`，由PROJ在受信租户中按稳定码`T-SAT-SURVEY`锁定同项目唯一当前任务及完整冻结Fact，再把返回的`projectTaskId/projectTaskVersion`传给`SatisfactionTaskInitializationApi.initialize`；initializer以MANDATORY加入首次触发事务并继续调用原`lockAndRevalidateSatisfactionTask`精确重验，首次source/trigger仍为初验活动完成Fact。缺失、重复、事实不完整或无当前责任人使初验活动、PROJ任务、满意度Task/Questionnaire和成功Outbox整体回滚。ACC返回分配的collectionKey/taskRevisionNo；整改不复用外部初始化接口，而由ACC `recollect`以不可变`SatisfactionRemediationFact`创建下一revision；`SatisfactionResultFactApi.inspect/lockAndRevalidate`向未来CLO/SUB返回不可变结果和版本。Result事件生产者在写Result前调用现有`ProjectWorkBindingFactApi.lockCurrentSatisfactionTask(projectId, projectTaskId)`窄方法，由PROJ在受信租户上下文锁定唯一当前行并仅对`T-SAT-SURVEY`返回现有`ProjectSatisfactionTaskFact`；消费者不得调用取当前方法，必须以事件冻结的`projectTaskVersion`调用原`lockAndRevalidateSatisfactionTask`精确重验。未交付来源Owner只能预留调用接口，不能由ACC推断业务时点。

模板配置包与判定契约固定如下：`scoring.ruleVersion`是该修订的不可变规则身份并投影到`rule_version`；题型目录为`SINGLE_CHOICE/MULTIPLE_CHOICE/RATING/TEXT`；策略目录为`SUM_V1/WEIGHTED_AVERAGE_V1`；舍入目录为`HALF_UP/HALF_EVEN/DOWN`且precision为0..2。选项score、weight、scoreMin/scoreMax和threshold均使用可无损转`DECIMAL(7,2)`的十进制字符串，scoreMin固定0。MULTIPLE_CHOICE必须携带非空整数且满足`1<=minSelections<=maxSelections<=options数量`；TEXT必须携带非空整数且满足`0<=minLength<=maxLength`；不适用参数必须缺失。单选/量表最大可达分取最大option score，多选最大可达分取全部合法去重选择集合的option score平均值之最大值；scoreMax按策略由各题最大可达分确定，threshold必须位于0..scoreMax。`SUM_V1`禁止weight并求计分题之和；`WEIGHTED_AVERAGE_V1`要求全部计分题正weight并计算加权平均；多选答卷题得分取所选option score平均。未答计分题为0，文本题不计分；最终仅舍入一次，舍入后比较threshold，必答或签字门禁失败强制passed=false。模板公共GET向客户只返回code/title/type/required、option code/label及输入约束，不返回option score、weight、策略或threshold。

ACC-04满意度来源投影的输入固定携带`projectId/projectTaskId/projectTaskVersion/taskRevisionNo/collectionKey/resultId/resultVersion/resultFactVersion`。`projectTaskVersion`只能由Result事务内的PROJ生产者冻结方法取得，不使用ACC Task版本、当前版本查询或默认值。`resultVersion`是只追加业务来源版本并写`source_version`；`resultFactVersion`是Result当前提交后的乐观版本，只传给`SatisfactionResultFactQuery.expectedFactVersion`做Owner状态重验。投影必须以事件任务版本通过PROJ Owner原`lockAndRevalidateSatisfactionTask`重验该任务稳定码为`T-SAT-SURVEY`，并精确锁定同租户同项目`deliverable_code=D-SAT-REPORT`且`task_code=T-SAT-SURVEY`的唯一`acc_project_deliverable`根；缺失、重复、版本冲突或身份不一致返回稳定DEPENDENCY/IDENTITY错误并保留待补偿，不按名称或其他根降级。

满意度文件策略键为`ACC/SATISFACTION_RESPONSE/{responseId}/SATISFACTION_SIGNATURE|SATISFACTION_ATTACHMENT`、`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT|SATISFACTION_ARCHIVE`。外部受控上传由PLT加性`FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload/lockAndRevalidateBusinessGrantFiles`承接。ACC把公开提交的`requestId`同时作为Response预留操作键，通过`PlatformCommandExecutionApi`固定作用域`ACC_SATISFACTION_RESPONSE_RESERVATION`保存`responseId`回执；规范化摘要固定含tenant、grantId/version、questionnaireId和requestId，同键同摘要返回原ID、异摘要冲突。文件init另用最长32字符的`operationId`幂等并返回服务端`fileSlotKey/fileSequence`；槽位键使用紧凑稳定编码且不得超过ACC来源附件`reference_key`的64字符物理边界。最终提交重放预留并调用PLT锁定重验，只有规范事实精确匹配grantId/version、responseId、policyKey、scopeVersion、槽位和实际Artifact/Version/Reference时才写Response/ResponseFile；客户端文件Fact只是句柄。现场协助使用独立固定作用域`ACC_SATISFACTION_ASSISTED_RESPONSE_RESERVATION`，摘要固定`submitChannel=ASSISTED/tenantId/taskId/questionnaireId/actorUserId/requestId`，同一正式责任人同键同摘要只重放同一`responseId`。ACC不得预写空Response，最终提交不得重新分配ID。内部上传、Access Ticket下载和归档继续复用现有接口。ACC不保存PLT内部主键、不建设第二文件真值。

通用`FileBusinessObjectPolicyQuery/FileBusinessObjectPolicyRevalidationQuery`保持不变。`FileBusinessObjectPolicyProvider`加性提供默认失败的`initializeBusinessGrantUploadPolicy/lockAndRevalidateBusinessGrantUpload/lockAndRevalidateBusinessGrantFiles`，分别接收类型化`BusinessGrantUploadInitializePolicyQuery/BusinessGrantUploadCompletePolicyQuery/BusinessGrantFileRevalidationQuery`；PLT Registry按`ACC/SATISFACTION_RESPONSE`唯一分派。ACC Provider按grant→Questionnaire→Task锁序验证同租户、grant版本、ACTIVE/有效期、策略用途、Response预留、槽位和项目范围。返回`BusinessGrantUploadPolicyFact`必须含正数`grantIssuerUserId`、scopeVersion及上述稳定身份；issuer唯一取grant创建时`creator`，不取updater或客户端值。

正式身份现场协助不得复用上述grant Query，也不得退回通用文件策略Query。`FileArtifactApi`加性提供`initializeAuthenticatedAssistedUpload/completeAuthenticatedAssistedUpload/lockAndRevalidateAuthenticatedAssistedFiles`，`FileBusinessObjectPolicyProvider`提供对应的类型化initialize/complete/final-revalidation Query/Fact且默认失败关闭，Registry仍按唯一`ACC/SATISFACTION_RESPONSE`分派。ACC Provider在三个时点均按Task→Questionnaire锁定，重放并核对requestId/responseId预留、当前责任人、可收集状态、用途及`ProjectScopeApi(PROJECT_EDIT)`的scopeVersion；PLT从SecurityContext取得正式actor并同时重验`pms:acceptance:satisfaction:collect`与`pms:file:upload`。PLT只返回由UploadSession/Reference反解的服务端槽位和规范公共文件事实；签字固定序号1，附件使用服务端序号。最终`assisted-responses`只持久化该规范事实，禁止`attachExistingVersions`、客户端改挂引用或重放分支新增文件。

PLT不为grant创建登录上下文，也不让客户取得`pms:file:upload`。它把Provider冻结的`grantIssuerUserId`仅作为受控授权的内部责任主体写入既有`created_by/creator/updater/OperationAudit.actorId`；审计detail固定含`subjectType=BUSINESS_GRANT`、grantId/version、questionnaireId、responseId、policyKey、fileSlotKey。完整token、正文和客户身份不得进入审计。grant失效、版本/预留/槽位/范围冲突或Provider未知时，必须在对象存储、Artifact/Version/Reference及Response之前失败关闭。

ACC生成Result文档必须调用PLT加性`FileArtifactApi.createGeneratedBusinessFile`，不得调用`FileUploadApplicationService`或PLT表。命令输入固定为`tenantId/actorUserId/operationId/resultId/collectionTaskId/questionnaireId/responseId/expectedTaskVersion/ownerContext=ACC/objectType=SATISFACTION_RESULT/purposeCode=SATISFACTION_RESULT_DOCUMENT/scopeVersion/fileName/contentType/content`；四项Owner身份仅由ACC在锁定Task→Questionnaire→Response后构造，不进入Controller请求体，并与其他命令事实共同进入`operationId`规范化请求摘要。目标引用键由PLT按`ACC/SATISFACTION_RESULT/{resultId}/SATISFACTION_RESULT_DOCUMENT`构造，客户端不能覆盖。`actorUserId`取Result形成时当前责任人，`scopeVersion`取同一项目`ProjectScopeApi.treeVersion`。

通用`FileBusinessObjectPolicyQuery/FileBusinessObjectPolicyRevalidationQuery`保持不变；`FileBusinessObjectPolicyProvider`加性提供默认失败关闭的生成文件专用方法，输入类型固定为`GeneratedBusinessFilePolicyRevalidationQuery`并携带上述Owner身份。PLT Registry仍按`ACC/SATISFACTION_RESULT`唯一分派Provider并校验返回`scopeVersion`。ACC Provider以同一`MANDATORY`外层事务锁定并重验：Task同租户、状态`PENDING_DECISION`、版本匹配、当前责任人等于actor、questionnaireId匹配且无Result；Questionnaire与Task关系精确；Response与Questionnaire关系精确；预分配resultId及PLT目标未被其他事实占用。随后用Task.projectId调用`ProjectScopeApi.lockAndRevalidate(PROJECT_EDIT)`，要求返回treeVersion等于scopeVersion且fullProjectIds包含项目。任一不一致在对象存储和Result写入前失败；resultId只作预分配目标与幂等绑定，不作已落库Owner入口。

`createGeneratedBusinessFile`使用`MANDATORY`加入ACC判定外层事务。PLT先复用现有FileUploadSession/operation摘要形成持久补偿会话并写受大小限制的对象，再在外层事务创建Artifact/Version/Reference；Result、ResultFile、成功幂等事实和Result Outbox共同提交。外层回滚时会话保留可重试/待补偿语义，同operation同摘要复用会话和存储回执，既不创建第二Artifact也不产生第二引用；放弃或失败会话由现有上传补偿删除未引用对象。PLT任一步失败时Response保持已提交、Task保持PENDING_DECISION，Result及Outbox零写入。

ACC报告附件的文件策略键固定为`ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}/ACCEPTANCE_REPORT_ATTACHMENT`，归档键固定为同对象下的`ACCEPTANCE_REPORT_ARCHIVE`。ACC `FileBusinessObjectPolicyProvider`把报告版本解析为不可变`projectId/projectTaskId`，使用PROJ `ProjectScopeApi`执行`PROJECT_VIEW`或`PROJECT_EDIT`并把返回`treeVersion`作为唯一`scopeVersion`；归档集合的`ARCHIVE`只接受ACC受信补偿消费者。新上传继续走PLT现有`init-upload/complete-upload`；`ExistingFileReferenceTarget`加性支持唯一ACC目标`ACC/ACCEPTANCE_REPORT_VERSION/*/ACCEPTANCE_REPORT_ATTACHMENT`并保留现有SOL/动态表单目标，绑定既有文件仍调用`attachExistingVersions`。发布/完成调用`lockAndRevalidateReferenceSets`，查询/下载调用`inspectReferenceSets`和现有Access Ticket REST。ACC不持有PLT内部ID，不建设文件代理。

PLT加性公开`FileArtifactApi.archiveReferenceSets(ArchiveFileReferenceSetsCommand)`，输入为`operationId/archiveBatchId/businessDecisionRef/actorUserId`、附件/归档两个完整集合键、期望`scopeVersion`和按`referenceKey`稳定排序的附件公共事实集合。`actorUserId`只允许取对应EFFECTIVE报告版本在发布命令中冻结的服务端认证用户，不接受Job线程上下文、客户端覆盖或技术默认值。PLT保留既有`pms:file:archive`服务端权限和租户校验：用SYSTEM `PermissionApi.hasAnyPermissions(actorUserId, "pms:file:archive")`校验功能权限，并以同一tenant和该用户执行FileBusinessScope重验；持锁确认附件集合仍全为ACTIVE后，在`ACCEPTANCE_REPORT_ARCHIVE`集合按相同artifactId/versionNo/referenceKey创建独立引用，将目标引用置ARCHIVED并追加`FileArchiveRecord.archivedBy=actorUserId`，报告附件ACTIVE引用保持不变。整组同事务且同批同摘要幂等；ACC只在整组成功后写`ARCHIVED`，否则保持`PENDING_COMPENSATION`。不得伪造Web登录上下文或取消服务端鉴权。

物理模块固定：`FileArtifactApi.archiveReferenceSets`声明在现有`pms-module-platform-api`、Provider在`pms-module-platform`；`AcceptanceActivityInitializationApi`与`AcceptanceActivityCompletionFactApi`声明在现有`pms-module-project-api`的ACC契约包，真实Provider、ACC文件策略Provider及活动/报告实现位于`pms-module-project`的ACC子包。该物理合置不改变ACC业务Owner，PROJ编排不得调用其Service/Mapper或直接访问ACC表。

PROJ继续拥有任务命令，ACC拥有验收业务命令与事实。修订018允许Stage/Task通过WorkBinding办理明确验收实体，或仅通过CompletionRule引用结果。独立创建/办理/事实读取及范围的新参数与事务由Q-TPLACC-001锁定，不以删去旧必填参数代替设计。

既有任务绑定路径（只保留原契约与历史适用性，不冒充修订018独立入口）：非`TASK_NATIVE`初验/终验任务固定`targetContextCode=ACC/targetObjectType=AcceptanceActivity/targetObjectKey=acceptanceId`；PROJ锁定任务及执行契约后调用`AcceptanceActivityCompletionFactApi.lockAndComplete(tenantId, projectId, projectTaskId, executionContractId, acceptanceId, expectedActivityVersion, expectedReportVersion, operationId)`。原返回`COMPLETED/REPORT_INCOMPLETE/IDENTITY_MISMATCH/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`及事实版本，成功才追加TaskCompletionEvaluation并置任务DONE；新独立命令与结果语义须另行复核。ACC不得直接更新PROJ任务、阶段或WorkBinding，视图不另写状态。

既有项目创建接口的原适用契约（新独立验收路径不以此为唯一入口）：PROJ使用ACC公开`AcceptanceActivityInitializationApi.initialize`，ACC以MANDATORY加入同一事务。先生成任务、非ACC执行契约和里程碑，ACC初始化精确应交根，PROJ预分配executionContractId，再传projectId/projectTaskId/taskDefinitionKey/executionContractId/acceptanceType/deliverableCode/templateRevision。原接口仅识别两组既定任务码映射并返回acceptanceId/activityVersion，PROJ随后追加当前契约，任一步失败整体回滚。原接口、错误语义与历史保留；Q-TPLACC-001明确新的可选节点来源、延迟解析和范围契约后才能扩展，不能伪造S5、任务或目标ID。

存量切换使用同一initializer的受管批次入口并按项目成对处理：无两项精确任务保持不变；部分/重复/缺应交根或当前契约非V63 `TASK_NATIVE`整批失败；两项均非终态时原子关闭旧契约并创建两个活动及ACC当前契约；两项均为`DONE/CLOSED`时整项目保持旧契约和历史且不创建活动；终态/非终态混合时整批失败，未知状态同样失败关闭。无匹配“保持不变”不得解释为精确任务缺件时跳过。

`publish-version`要求草稿版本与期望当前版本，首次生效或替换时原子切换状态/区间和活动当前指针，并把服务端认证用户写入不可变`publisher_user_id`；`revoke-current-version`要求期望当前版本，原子置REVOKED并清空指针，不自动恢复旧版或改写发布人。两命令通过`PlatformCommandExecutionApi`与`AcceptanceReportVersionChanged` Outbox同事务提交；事件明确`EFFECTIVE/REPLACED/REVOKED`、`publisherActorUserId`、当前/前一版本及完整有序附件集合。`AcceptanceReportOutboxDeliveryJob`通过`PlatformOutboxDeliveryApi`只领取`AcceptanceReportVersionChanged`；来源投影事务成功后才`markDelivered(eventId, expectedRetryCount)`，异常时不得先标成功，必须`scheduleRetry(eventId, expectedRetryCount, nextRetryTime)`。该Job不领取`ClosureGateRecheckRequested`，CLO Feature未交付时不得把它标记已投递。来源消费者只追加`acc_project_deliverable_source_version/source_attachment`并切换既有根指针，替换保留旧关系，撤销使旧关系失效；归档补偿使用事件冻结发布人调用PLT。索引、文件归档或CLO消费者失败不回滚报告版本，当前来源保持`PENDING_COMPENSATION`并重试；活动完成不把`ARCHIVED`作为第五项。

历史 `/pms/acc-maintenance-transition/*` 的 create/renew/activate 等入口必须在兼容切换后冻结，不映射为新 ServiceHandover 命令。

## 9. CUT：割接 API

适用 Requirement：CUT-01～CUT-10。

| 路径 | 命令/查询 | 关键约束 |
|---|---|---|
| `/cutover-tasks` | create、list | 来源键幂等；列表支持项目、P2～P6阶段、状态、listKind和分页，包含已归档任务；项目查看范围与当前审批资格分别校验，审批专属行只返回受限摘要 |
| `/cutover-tasks/statistics` | GET | CUT-01 V2按授权可见的CutoverTask聚合首页KPI | 只读聚合，不改变任务状态或P1～P6流程，不返回无权任务明细 |
| `/cutover-tasks/{id}` | GET detail | 按任务ID查询统一详情；不将审批资格扩展为普通项目查看权限 |
| `/cutover-tasks/{id}/assessment` | PUT save draft；`actions/submit` POST | 一线提交问卷与人工等级；保存与提交均携带If-Match、Assessment-If-Match和Idempotency-Key；用服经理在P5复核，不新增P2审批 |
| `/cutover-tasks/{id}/actions/revise-assessment`、`actions/revise-checklist`、`actions/withdraw-plan` | POST | 任务负责人携带If-Match、Idempotency-Key、projectScopeVersion及reason；按目标阶段对应写权限校验。审批前生成新草稿并使下游失效留存；P5在同一事务撤回未结束审批、取消未办节点后修订。已批准或归档不可调用 |
| `/cutover-tasks/{id}/revision-options` | GET | 具有评估或清单写权限且在项目编辑范围内的任务负责人，查询同一配置当前生效的已发布版本；已批准或归档任务不可使用 |
| `/cutover-tasks/{id}/checklist` | detail、save draft、submit | P3同一工作台返回checklistId/version、inputSnapshotHash、匹配项、界面格式、当前选择结果、CollectionTask/结果引用和重新匹配差异；D级不存在该资源 | save/submit携带If-Match与Idempotency-Key；提交只读取当前适用项和当前选择结果，全部必填满足后冻结版本 |
| `/cutover-tasks/{id}/checklist/actions/rematch` | POST | 输入checklistVersion、inputSnapshotHash和新维度，预览或应用差异 | 保留stableItemKey未变的有效答案；移出项仅留历史，不进入当前提交；已提交版本不得原位重匹配 |
| `/cutover-tasks/{id}/checklist/revisions` | GET | 返回当前及历史清单修订；清单detail、matching-options、external-state以可选checklistId读取同任务历史。历史访问仍要求当前项目查看范围，不开放历史写入 |
| `/cutover-tasks/{id}/checklist/actions/export` | POST | CUT-03 V2导出当前授权清单版本 | 按清单项、设备和字段权限裁剪；导出不改变清单、任务或流程状态 |
| `/cutover-tasks/{id}/checklist/items/{stableItemKey}/collection-requests` | POST | 关联平台正式采集执行，携带现行任务、清单版本与Idempotency-Key；绑定清单项和冻结设备范围，技术成功不直接判定采集项通过 |
| `/cutover-tasks/{id}/plan` | GET、PUT；`actions/{create-draft|download-draft|submit|revise}` POST | 文件/安全/归属/人工确认校验；不强制解析全部模板字段；审批使用独立审批实例资源 |
| `/cutover-tasks/{id}/plan/support-arrangements/{arrangementId}` | PATCH contacts | 联系人、联系方式、到位时间变化留痕不重审；角色/职责变化必须生成新方案revision并重走P5，未裁决的P6回退仍保持规格阻塞 |
| `/cutover-tasks/{id}/closure/actions/link-collection-execution` | POST | 以entry和executionId关联平台正式执行，携带If-Match、X-Task-Version和Idempotency-Key；CUT不接收凭证明文，失败无日志的执行保留失败事实 |
| `/cutover-approvals/{approvalId}`、`/cutover-approvals/{approvalId}/actions/{approve|reject|reassign}` | GET、POST | 按审批实例及当前角色/人员资格校验；任一评审项为否必须驳回并填写原因；通知和审批队列进入同一审批实例详情，提醒失败不改变审批状态 |
| `/cutover-tasks/{id}/closure` | save、submit、detail | 保存P6结果与INT-12证据引用；提交即归档；失败不发布CutoverCompleted |
| `/cutover-configurations/revisions`、`/cutover-configurations/revisions/{revisionId}` | GET、POST、PUT；`actions/{copy|validate|publish|disable}` POST | 创建、保存、复制、发布、停用携带Idempotency-Key；修改既有修订携带If-Match；发布版本不可覆盖，校验为只读操作 |

方案 GET 的 `generatedDraft` 返回当前已保存正文对应的生成文件精确引用和生成时间（未生成时为 null），结构沿用 `DownloadCutoverPlanDraftResult`。生成成功同事务保存引用，不推进方案版本或审批状态；保存新正文后按新正文版本查询，无对应生成记录时返回 null；旧生成记录、平台文件及审计保留。重开页面恢复该事实，文件展示与下载仍走 PLT 的实时授权和可用性校验。

在线方案渲染将拓扑/组网配置中的PNG、JPEG通过PLT `AuthenticatedFileContentApi`按原工件版本和正式DOWNLOAD权限有界读取（沿用50MiB平台上限），验证Owner完整业务键及原摘要后内嵌DOCX；非图片保留名称/版本引用。草稿生成与提交后导出使用同一附件解析器和渲染器。生成DOCX记录内部渲染格式属性；再次生成检查该属性，旧格式仅追加新生成引用，不修改旧文件、审计或正文版本，新的生成操作身份包含渲染格式以避免复用旧的幂等文件。前端“生成并下载”始终由后端确认格式，文件卡片仍可读取其精确历史版本。只读导出无业务写入，但图片读取需在普通事务中完成PLT既有文件授权锁定，不设置数据库只读事务。

已提交在线方案下载使用 `GET /api/v1/pms/cutover-tasks/{taskId}/plan/submitted-file`，要求 `pms:cutover-task:download-plan`、既有任务可见范围及 `If-Match` 当前方案版本。返回DOCX附件（`Cache-Control: no-store`）；只读取已提交方案原始正文、步骤和保障安排，复用既有方案渲染器，不创建平台文件、不推进版本或审批状态，不重验后续变化的项目成员或前序提交条件。上传方案仍以冻结的工件和版本走PLT下载；草稿生成接口保持原契约。

以上为真实物理前缀`/api/v1/pms`下的割接资源，不再叠加`/admin-api`。割接路径接入既有身份、租户、权限及访问日志链；其他模块的路径行为保持既有契约。HTTP响应由割接VO显式投影，Long和时间沿用Yudao序列化，错误携带稳定reasonCode/recoveryAction；版本冲突刷新，未确定结果以同一幂等键及原始载荷重试，刷新失败时暂停后续写入。旧任务、方案、风险接口已退役，不提供兼容读写入口。

2026-10-15已确认的主动修订合同见`Q-CUTOVER-ACTIVE-REVISION-20261015`。`WITHDRAWN`为审批终止事实，单独保存撤回人、时间、原因；原已办意见及来源修订保持不变。重新提交创建新审批实例，沿方案来源修订找到前序审批并建立替代关系。修订失败时评估、清单、方案、审批和任务阶段在同一事务回滚；重复命令返回首次结果。

P2/P3主动修订请求可选`configurationRevisionId`，不传则保留任务当前配置。选择新配置时，在同一事务锁定并验证同一配置的有效发布修订及当前类型、组网值，然后更新当前任务的精确配置引用；原清单与方案继续保存原配置引用。P3随后重新匹配，不把旧清单直接标成新配置；按稳定采集项编码和相同字段定义承接填报及正式文件引用，字段变化的项重新填写，自动查询和采集重新确认。P4方案撤回入口不得单独变更配置。

P3生成及重新匹配的`matchingInput`包含`cutoverType/networkMode/deviceTypeCodes`，按任务冻结配置校验取值。类型或组网变化以任务版本CAS更新；所有匹配维度存于清单关系表。`PROJECT_MAJOR_LEVEL`只从PROJ已有`ProjectBusinessContextApi.inspect/lockAndRevalidate`的`Context.majorProjectLevel`取得，锁定范围及项目版本后冻结`matchingProjectVersion`；不得采信客户端传入级别或跨模块查表。该值沿CRM权威字典映射，不把空值、无或未映射值推断为重大项目；配置草稿按用户裁决仅将正式办事处级、市场级、公司级关联重大项目备件项。

PROJ公开`ProjectCutoverContextFactApi.inspect(ProjectCutoverContextFactQuery)`与`lockAndRevalidate(ProjectCutoverContextFactRevalidationQuery)`。inspect Query固定携带正数`tenantId/projectId`；重验Query携带从前次`FOUND`原样复制的完整`ExpectedProjectCutoverContextFact`，tenant必须与受信运行时上下文一致。`FOUND`返回同一ProjectMaster行的`tenantId/projectId/projectVersion/projectCode/projectName/customerId/customerCode/customerName/departmentId/departmentCode/departmentName`完整事实；三个编码字段最大64字符，`projectName/customerName/departmentName`最大255字符。`NOT_FOUND/INACTIVE`无Fact，写重验的`VERSION_CONFLICT`返回当前完整Fact。锁定方法以`MANDATORY`加入CUT写事务并锁定项目当前行，锁后逐字段比较完整Expected与currentFact；任一字段变化均返回`VERSION_CONFLICT`，Expected只作并发守卫，CUT只冻结Owner返回的currentFact。Owner损坏和Provider不可用分别使用公共稳定失败。客户字段和部门字段是项目发生时展示快照，CUT仍分别以`ProjectScopeApi.treeVersion`做用户范围重验、以`CustomerServiceLevelFactApi`取得当前服务等级；不得跨Context读表、拼接Summary或以projectVersion替代treeVersion。精确机器合同见`specs/features/F-CUT-002-project-context-fact-contract.json`。当前master尚未包含公共Java接口或生产Provider。

PROJ为F-CUT-005预留`ProjectCutoverServiceManagerFactApi.inspectCurrent/lockAndRevalidate`：按受信tenant和projectId返回并冻结当前唯一PRIMARY `SERVICE_MANAGER_L1/L2`、roleCode、projectVersion与participantFactVersion；零个或多个当前事实均为`NOT_UNIQUE`，不得任选人员。锁定重验以`MANDATORY`加入CUT事务，变化返回携带当前`FOUND/NOT_UNIQUE`的`STALE`；服务经理节点改派目标必须等于再次锁定的唯一当前事实。该能力复用ProjectParticipant聚合且不创建独立Feature或表，精确候选合同见`specs/features/F-CUT-005-candidate-owner-contract.json`；当前master尚未包含公共Java接口或生产Provider。

## 10. SRV：巡检与服务状态 API

适用 Requirement：INS-01～INS-09、SRV-01。

| Context | API | 约束 |
|---|---|---|
| Inspection | `/inspection-rules`、`/{id}/revisions`、`/revisions/{revisionId}/actions/{validate|record-security-review|publish|disable}`、`/selectable` | 创建稳定身份要求检测ID和规则名称；草稿允许其余八字段及命令/判定/产品适用关系为空或不完整，`validate/publish`按发布完整性返回字段级错误；阈值数据类型只接受`NUMBER`；`record-security-review`在目标租户上下文以当前审核人直接调用System现有`PermissionApi.hasAnyPermissions`判定专用权限，普通授权或System超级管理员返回`true`均放行，`false`或异常失败关闭；安全审核只接受`PASSED/REJECTED`并追加绑定当前revision与命令/正则摘要，发布按`reviewed_at DESC, id DESC`最后结论判定且仅`PASSED`放行；发布revision只读；选择查询按授权设备的AST产品类型过滤；任务冻结规则版本 |
| Inspection | `/inspection-tasks`、`/{id}/actions/{precheck|dispatch|complete|archive}` | 在线通过 DAC；离线文件走受控上传；模式互斥 |
| Inspection | `/inspection-reports/{id}/versions` | 生成/发布报告版本，原始采集结果只引用 |
| Inspection | `/service-issues`、`/{id}/actions/{remediate|review|close|mark-false-positive}` | 问题闭环和误报留痕 |
| Service Operations | `/devices/{deviceId}/service-status` | V2 只读客观状态与来源，不提供续保空间/续保率接口 |

### 巡检专项接口细化（2026-10-15 用户批准）

巡检为手工建单的独立业务入口，仅引用项目、客户和设备资料；不创建项目事件任务，不接入 WorkBinding/BusinessView 或回写项目进度。保留 PM-10 已有 `INSPECTION` 只读事实提供方。页面为 `/pms/inspection/{workbench,tasks,rules}` 和隐藏的 `/pms/inspection/tasks/:taskId`。

2026-10-28需求方批准正式判定来源调整：在线、离线均交UMC判定，在线仅由DAC采集日志；先交付离线，再接在线。原平台逻辑及其历史保留。任务详情默认使用从现有页面复制增强的UMC适配视图，`engine=platform`可查看原平台界面。新增`POST /inspection-tasks/{id}/umc/actions/{start-offline|fetch-result|complete-execution|adopt-reports}`，沿用任务执行/报告生成权限、项目及租户范围、If-Match和Idempotency-Key；原上传入口及脱敏/日志归档复用。执行尝试冻结providerCode，UMC新尝试不要求平台规则修订；原结果表仅增加UMC来源报告和外部规则结果引用，完整结构仍由原报告快照拥有，不建立UMC规则库或伪造规则修订。获取结果同时登记正式判定引用并结束该设备UMC执行；不支持、错误、未判定或缺失设备不构成完整执行。全部设备完整后经原状态机进入REPORT_PENDING，显式采用全设备UMC报告集合后进入ANNOTATION_PENDING；集合引用各设备不可变报告和DOC，不拼造单份外部报告。ReportView.componentReportIds与files展示全设备原件，results使用真实本地结果主键供原标注、待办及闭环引用；UMC级别保留独立来源，不改写成平台严重级别。已完成结果、正式报告及下载只使用本地归档。

2026-10-28需求方明确UMC待办不强制填写处理进展：`POST /service-issues/{id}/actions/record-process` 的 `content` 对服务端确认属于同租户、同任务UMC正式结果的待办允许为空，前提是本次附有有效处理证据；既有平台待办仍要求处理文字。空文字保存为空字符串，不生成虚构说明；无文字且无证据拒绝且不新增记录。处理证据仍按原归属、版本及摘要核验，闭环申请/确认的说明、有效证据、权限、版本和状态机规则继续有效。普通未闭环情况备注可选，无变化不发送保存请求；申请或确认时该输入承接正式闭环说明，仍须填写。


- Business API 的物理前缀为 `/api/v1/pms`，不叠加 `/admin-api`；沿用平台身份、租户、`CommonResult`、分页和 Long/时间序列化。写命令携带 `Idempotency-Key`，修改已有任务或规则携带 `If-Match`。同键重试固定原载荷与原版本，未知执行结果不换键重新执行。
- 任务 `POST /inspection-tasks`、`PUT /inspection-tasks/{id}/preparation`；`POST .../actions/{start-attempt|assign|cancel}`。设备清单在创建时冻结，方式和规则修订在执行尝试开始时冻结。日志、结果、报告、标注及处理记录分别保留版本；报告文件格式为 PDF/DOCX/XML，人工上传兼容 DOC。
- 2026-10-27 UMC首轮消费契约：`pms-module-integration-api`提供`UmcInspectionApi.inspect(Request)`，Request包含tenant/task/attempt作用域、稳定requestId、已授权脱敏且符合UMC文件名约定的日志，以及可选检查点。Result同时返回UMC DOC原件/摘要、结构化报告DTO和原始JSON/摘要；DTO保持设备→规则→指标→值层次及外部ID精度，support/error/result独立。Failure包含安全错误码和最近检查点；调用方在长事务之外执行并保留检查点后恢复。它是服务端内部协议能力，不是匿名REST文件上传入口，也不直接形成平台“可用报告”或改变检测/归档状态；原有报告三格式及人工路径不变。
- 2026-10-28执行结果回传补齐：既有任务详情增加`umcResults`，从原报告结果快照按tenant/task/attempt/execution/device和原LOG精确版本关联后返回设备、全部规则及其指标/值/命令输出；保留未完成状态及失败说明，不以DOC成功替代结果。原`refresh-log-parse`对已关联UMC报告的执行记录读取本地已完成结果，或恢复原未完成意图；未关联UMC的旧执行继续原解析路径及原权限。前端上传后留在巡检执行页，UMC全部规则以明确来源合并展示在既有“巡检判定结果”，展开保留完整指标、空值、未支持、执行错误和原结论。该投影不伪造平台ruleRevisionId，不复制UMC模型入库，不改变原平台冻结规则判定及正式生命周期门槛。
- 2026-10-28 UMC统一多份日志上传：`POST /api/v1/pms/inspection-tasks/{taskId}/offline-log-batches`兼容原`file`，新增重复multipart `files`（二者互斥），`attemptId`及按设备ID关联的`sourceHosts`不变；新多文件输入仅用于UMC尝试。一份或多份`.log/.txt/.zip`及UMC合规原名文件统一按主SN拆分匹配，输入总量20MiB、展开及拆分累计50MiB，最多1024个输入/展开条目；同设备跨文件的命令按上传顺序合并一次执行。幂等摘要包含有序原文件名和字节SHA-256，重排或改名视为不同输入，重放不重复登记。所有文件完成解析、设备匹配和本批设备IP覆盖后才保存脱敏日志及执行；任一格式、SN、来源IP或脱敏失败整批拒绝。单设备任务且只有一份严格UMC原名日志时可从文件名补IP；多设备仍使用明确来源映射，不猜测日志正文IP。随后沿用UMC调用、完整正式结果登记与原表归档，不新增业务表或配置资产副作用。
- 2026-10-28 UMC集成既有离线巡检：沿用`/api/v1/pms/inspection-tasks/{taskId}/offline-logs`和`/offline-log-batches`，分别补充日志来源设备IP `sourceHost`、按deviceId关联的`sourceHosts`。原上传先完成权限、版本、当前离线尝试、设备归属及INT-12脱敏，保存原执行记录和受控日志；事务提交后复用该日志调用UMC，不另设上传入口或另存一份输入。UMC提交核对冻结设备SN，绑定原executionId。写请求沿用If-Match和Idempotency-Key。原任务详情的reports返回报告版本及附件元数据；`GET /api/v1/pms/inspection-reports/{reportId}/result?taskId=...`从本地查询结果，`POST /{reportId}/actions/retry-external`仅恢复原未完成记录，已完成记录返回本地结果。DOC经PLT `NativeGeneratedFileApi`归档，purpose为`UMC_REPORT`，分类沿用`INSPECTION_REPORT`；只将本次报告结果保存至既有srv_inspection_report_revision.result_json，恢复上下文及原执行日志引用保存至source_context_json，附件表只登记DOC，不复制UMC台账、规则或指标模型，不创建业务结果JSON文件。DOC可用与结果入库均成功才将原报告版本标记AVAILABLE；可重试失败保留GENERATING及失败说明，完成历史不更新。列表、详情、刷新及下载均只读本地。原表三个可空结果字段及UMC来源CHECK通过V520前向变更，受保护环境执行须已有明确授权。该接入不绕过巡检状态机、不替换平台冻结检测结论、不自动改currentReportId；原平台基础报告及人工报告继续沿用原规则。
- AST 在既有 `ProjectDeviceSelectionApi` 增加对象查询 `pageSelection(SelectionQuery)`，返回授权项目及其关联合同的分页设备资料。写入仍使用 Owner 的 `validateSelection` 锁定复核，不从客户端设备名称或 SN 建立真值。
- 2026-10-28 TF-043（2026-10-30按用户统一查询要求更新）：既有管理接口 `GET /admin-api/pms/asset/devices/archive-page` 在 `selectionProjectId` 场景增加可选 `keyword`，单一输入框按“SN包含关键词 OR 当前产品型号包含关键词”检索；`keyword` 不允许脱离项目选择场景使用。继续要求 `pms:device:query`、当前租户及PROJ解析的项目/有效关联合同范围。包括空keyword在内的业务候选由项目、授权合同两个索引分支直接返回分页所需设备字段，各分支应用同一筛选，再UNION去重、按ID倒序分页；不先取ID再JOIN设备表。total使用相同分支/筛选的ID UNION计数，不重新读取设备。原独立sn/name/productModel/contractNo筛选继续按AND收窄，独立productModel也针对当前展示型号，覆盖原先无keyword时仅查设备原型号的旧口径；无selectionProjectId的普通档案入口不变。当前型号保留设备非空原型号优先，空值按主档物理唯一键 `(tenant_id, product_code)`、设备编码原值及逻辑删除直接关联；遵循该唯一键排序规则，不新增来源/状态过滤或重建产品聚合，不写回设备或历史快照。项目内输入触发至结果实际可见须≤1秒，验收包含HTTP范围解析、防抖与选项渲染，SQL耗时或HTTP成功均不单独代表达标。
- 新巡检设备候选由AST在Mapper XML中合并当前项目及关联合同的设备ID，去重后按原ID倒序分页；各分支和回表均保留租户、删除及筛选条件，空项目范围返回空，既有设备页面查询保持原行为。产品类型读取沿用ADR-0036的受信模块主体及当前用户设备范围双重校验；local配置仅登记INSPECTION的`PRODUCT_TYPE_READ_CODES`和`DEVICE_PRODUCT_TYPE_READ`两个读动作，不授予导入或业务写入权限，也不替代权威产品类型与设备映射。
- 离线人员在平台外执行命令，上传 `.log/.txt/.zip`（50 MiB）。日志进入存储前使用 DAC 现有 `DeviceLogInputNormalizer` 脱敏；增加经 namespace/project 授权的无持久化 `POST /api/v1/log-parses/actions/normalize`，由 INT `DeviceLogParsingApi.normalize` 调用。返回脱敏命令块、识别的 SN 与警告，不能据此宣称设备执行已验证。输入原文不进入 NPDMS 数据库、审计或文件存储。脱敏后文件由 PLT 注册，执行提交使用精确文件版本及任务/设备/尝试上下文；不要求用户提供结构化结果包、脚本或脚本摘要。
- 2026-10-16 用户批准的多设备上传扩展：新增 `POST /inspection-tasks/{taskId}/offline-log-batches`（multipart，`attemptId`、`file`，沿用执行权限、If-Match、Idempotency-Key），支持 `.log/.txt/.zip`、20 MiB；保留原单设备上传50 MiB及可选命令提示。经 INT `DeviceOpsLogSplitApi` 调用既有 DAC `/api/v1/npdms/log-splits`，按SN拆分并脱敏；所有SN须唯一对应本任务冻结设备，缺失、歧义、越界或任一日志无效均在登记前整批拒绝，不新增任务设备。逐设备进入现有 SRV 执行、命令分片、`DeviceLogParsingApi`解析及冻结规则判定；同一设备跨ZIP成员的日志合并为一次执行，原历史保留。该入口不调用配置调试导入、AST日志归档或设备事实投影，不生成配置记录、不更新设备事实；拆分和解析成功不代替巡检闭环。
- PLT 增加 `AuthenticatedFileContentApi.read(ReadCommand)`，复用 `FileArtifactApi` 的当前权限、对象策略和精确版本重验后读取有界字节；不向消费者暴露存储表、路径或凭据。SRV 文件策略键为 `SRV/INSPECTION_TASK/{taskId}`，用途区分 `LOG/REPORT/EVIDENCE/ATTACHMENT`；LOG 仅由受控脱敏流程注册，禁止通用上传绕过。历史访问必须命中 SRV 冻结的 artifact/version/referenceKey。
- 普通附件由 `GET /api/v1/pms/inspection-tasks/{taskId}/attachments` 返回PLT当前授权引用的工件、版本、文件名及可用性；写入复用平台文件接口及SRV对象策略，不新建文件存储或附件业务表。客户获准证明可选，不影响执行前置条件；已归档/取消任务禁止附件修改。
- PROJ `ProjectBusinessContextApi.currentActorScope(ActorQuery)` 一次解析当前可见项目，并按当前有效的服务经理主成员事实返回管理范围；管理员可见范围不自动成为管理范围。`searchOptions(OptionsQuery)` 在Owner内部按当前授权、租户、逻辑删除、名称/编码包含匹配及固定排序查询最多100项。SRV工作台和列表消费批量范围，禁止按租户每个项目逐项重查成员；选中对象的写入仍使用原版本化上下文和锁定复核，两个只读接口不替代写入授权。
- 报告、标注、跟踪和最终归档由 SRV 服务事务控制；工程师申请归档，当前项目管理范围内的服务经理最终执行。CUS 通过公开来源引用 API 保存原任务、报告、问题和处理记录引用，失败不能写入已归档。文件和客户资产引用均不代替 SRV 业务完成条件。

Inspection复用System既有`PermissionApi.hasAnyPermissions(Long userId, String... permissions)`，不新增System接口、DTO或查询。审核入口必须从受信认证上下文取得`userId`，在租户访问拦截器已设置的目标`TenantContextHolder`内仅传入精确权限码`pms:inspection-rule:security-review`；不得接收客户端提交的租户、审核人或替代权限码。该直接调用不经过`SecurityFrameworkService`的租户访问`skipPermissionCheck`短路，返回`true`时沿用System既有角色—菜单与超级管理员语义，返回`false`或抛出异常时Inspection不追加审核事实。布尔结果只记录`authorizationType=RBAC_PERMISSION`，`authorizationSourceId`为空。现有内部`InspectionRuleExplicitAuthorizationApi`不得继续扩展为第二套契约，Task 8应删除或收口该未装配端口。

历史工单、工时及其附件在V1/V2不提供用户查询、导出或文件访问API。`AI-MIG-000`在已批准真实批次内保存的不可变来源载荷或受限迁移归档仅用于迁移对账、问题调查和来源审计，不是SRV业务API；未来用户访问能力必须通过独立PRD/Feature变更重新批准。

## 11. CUS、AST、COM、RES 与 KNO API

### 设备管理三维数据范围补充（2026-09-21 用户批准）

AST 设备列表、详情和档案复用同一授权谓词：当前租户内，公司/部门授权 **OR** 项目授权 **OR** 合同授权。公司与部门必须匹配同一条有效组织授权；无部门限定的公司级授权覆盖公司范围。任何一个维度不可用只关闭该维度，不借异常扩大范围。

- PROJ `ProjectDeviceOrganizationApi.visibleProjectIds(tenantId,userId)` 返回组织授权覆盖的项目 ID；`getOrganizations(tenantId,projectIds)` 批量返回项目当前公司/部门事实，空集合返回空。
- COM `ContractDeviceVisibilityApi.getVisibleContractNumbers(tenantId,userId)` 复用合同数据范围，排除租户内不能唯一定位的同号合同；`getOrganizationVisibleContractNumbers(tenantId,userId)` 返回公司/部门范围覆盖且归属唯一的合同号，包含已解析的发货合同；`getOrganizations(tenantId,contractNumbers)` 批量返回合同当前组织事实。
- 上述为模块间只读接口，由 AST 以服务端登录用户/租户调用。组织事实用于 AST 当前归属投影的事务内重建，列表/详情读取固化投影；AST 不直接查询 PROJ/COM 业务表。设备列表及详情增加 `organization`：公司 ID/名称、部门 ID/编码/名称、来源 `PROJECT/CONTRACT/UNRESOLVED`。不保存新的归属历史或改写项目/合同事实。
- 同日查询优化补充：`ast_device` 固化 `company_id/company_name/department_id/department_code/department_name/organization_source/organization_updated_at`，新增 `(tenant_id,deleted,company_id,department_id,id)` 索引。公司/部门授权仍实时读取 SYSTEM，项目与合同独立 OR 授权不变。
- AST `DeviceOrganizationProjectionApi.refresh(Refresh)` 接收受信租户及设备 ID/项目 ID/合同号集合，只从 Owner 当前事实重建本模块投影，空集合无操作；与来源变更共用事务，失败回滚。INT `GenericSyncTargetsChanged` 在分块写入后、提交前同步发布，携带租户与目标变更前后事实；预览、冲突和无变更不发布，监听失败回滚整个分块。AST 监听设备及合同变更，避免在通用引擎添加实体适配器。
- `POST /api/v1/pms/devices/actions/rebuild-organizations?afterId=0` 使用当前服务端租户和既有 `pms:integration:configure` 权限，每次最多重建 500 台，返回 `afterId/processed/updated` 并记录操作审计；仅派生字段重建，不修改来源历史或业务状态。此运维能力不扩展普通设备用户的数据范围。
- COM 候选范围重载 `getVisibleContractNumbers(tenantId,userId,candidates)` 只校验设备实际关联的合同，null/空候选均返回空；聚合和冲突检查仍覆盖同号所有来源。AST 先筛出尚未命中固化组织范围的候选合同，再在数据库执行完整权限并集与分页，禁止分页后过滤。



COM-01按PRD修订014补齐受信ERP批次字段：销售订单增加可空`salesType/sourceProjectName/orderComment/orderCreateTime/customerRequiredTime`，订单行增加可空`lineType/bundleCode/profitCenter/realExecutionNo/warrantyMonth`，沿用已有物理列长度和数量精度。旧调用缺失字段保持NULL；同来源版本改变任一字段仍是载荷冲突。仅ERP来源可进入权威接收端口，人工依据继续走候选接口，不晋升为ERP事实。

COM-01范围预览`POST /delivery-scopes/actions/preview`在调整场景成对携带`deliveryScopeId/expectedAllocationVersion`；两者均缺省仍为新增预览。调整目标必须属于当前租户、项目和订单行且为匹配版本的当前范围，可用量为订单数量减去其他当前范围的占用，不能把被调整范围自身当作新增冲突。减量预览复用ACC绑定守卫，预览不写业务事实；实际调整仍重新锁定并执行原有守卫。

COM合同不可见或项目关联无管理范围时保持拒绝和零写入，沿用统一响应的权限错误业务码`403`，不以未处理异常返回`500`，也不披露无权资源是否存在。

| Owner | Requirement | API | 关键边界 |
|---|---|---|---|
| CUS | CUS-01～CUS-04、INT-03 | `/customers`、`/customer-contacts`、`/customer-relationships` | CRM权威字段只读；临时客户显式标记来源；客户地址/站点只保存AST稳定引用 |
| AST | EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06 | `/devices`、`/devices/{id}/archive`、`/devices/{id}/assignment-history`、`/asset-locations/addresses`、`/asset-locations/sites`、`/asset-locations/sites/{id}/tree`、`/asset-locations/area-department-mappings`、`/rma-replacements` | 设备归属用`actions/assign-project`；地点由AST拥有；站点不绑定公司/部门；设备当前位置由已确认安装/迁移/拆除事实生效 |
| COM | COM-01 | `/contracts`、`/sales-orders`、`/order-lines`、`/commerce-authority/import-batches`、`/commerce-authority-candidates`、`/delivery-scopes` | ERP合同/订单/订单行核心字段只读；COM维护人工待核对候选、显式对账、项目范围水位和范围分配/调整/释放。地点只返回项目办事处发生时快照，实施站点/位置由IMP/AST独立提供 |
| RES | RES-01、SUB-01～SUB-05、INT-07 | `/suppliers`、`/subcontract-requests`、`/payment-gates` | 备件业务由外部系统承接；财务结果只回写引用 |
| KNO | INT-04 | `/technical-notices`、`/technical-notices/{id}/references` | V2 仅 ITR 同步查询与业务引用；无本地 publish/disable API |

工勘项目地点选择使用`GET /api/v1/pms/projects/{projectId}/customer-overview/locations`，要求`pms:project:query + pms:customer:query`。CUS复用PROJ当前VIEW及关联客户检查，只接受项目ID，不接受客户端customerId；返回关联客户ID/名称、该客户启用站点及所引用地址、客户当前ADDRESS引用。可选siteId必须属于上述站点集合，才经AST公开契约读取任意深度位置树。租户/操作者来自登录上下文；无关联客户返回空集合，越项目/跨租户/他客站点拒绝，不回退全局查询。AST按受信tenantId/customerId提供只读启用站点列表，保留AST全局管理API权限不变；此查询不授予地点管理权限，原工勘保存和地址/站点写入规则不变。

TF-018只读客户编码解析：`CustomerQueryApi.getProjectCustomer(ProjectQuery)`仅供客户概览和地点GET，Query包含`tenantId/actorUserId/projectId`，不能指定客户ID或编码。CUS绑定当前登录主体、登录租户及当前租户，要求客户查询功能权限，从PROJ公开范围API取得当前FULL项目VIEW和树版本，再由`ProjectBusinessContextApi.inspect`按同一主体、项目及树版本回源唯一客户绑定；优先既有内部客户ID，只有ID缺失才按绑定编码同租户唯一查询。结果带项目ID与可空客户摘要；未关联返回空客户，来源失败、绑定客户缺失/删除或跨租户拒绝，停用客户的既有引用仍可读。该方法不回填项目，不是联系人或客户写入授权；原`ProjectContactContextApi.inspect/lockForWrite/associateCustomer`和全局`getCustomerByCode/lockCustomerByCode`完全保留，所有写/新关系路径继续使用原客户目录范围、锁和状态校验。

TF-018项目主联系人预加载：项目联系人`GET /context`、列表及列表脱敏使用独立只读上下文；旧上下文正常返回时保留全部既有能力，仅其稳定`PROJECT_CUSTOMER_UNAVAILABLE(1014001003)`失败可再通过上述项目绑定只读API重验。PROJ既有`ProjectBusinessContextApi.Context`增加从同一ProjectMaster行原样读取的`lifecycleStatus`展示字段，CUS只读结果随之带项目版本和生命周期；不据此新增状态准入或写权限。重验成功后只返回`canManage=false/canViewHistory=false`，仅可读本项目启用且未删除的联系记录，且无明文字段资格，即使另有敏感查看功能权限也须脱敏。其他范围、租户、绑定与来源失败继续拒绝。`sources/history`与所有联系人、客户及关联写命令保持原授权，不把项目只读客户身份转换成目录、新关系、维护或恢复资格。

联系人列表查询返回`PageRead`，携带查询时及查询后的授权交集；响应脱敏复用该结果，不另取一个可升级的上下文。客户绑定变化或历史可见性收窄时整次拒绝，不将已查出的停用/历史行仅脱敏后返回；初次只读降级不能因后续目录权限恢复取得本次明文资格。`sources/history`响应前只使用原严格`context`再次校验各自管理/历史资格，任何目录或范围收窄均拒绝，绝不走只读降级。此重验不引入锁表或写权限变更。

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
- `ProjectDeliveryScopeQualificationFactApi.inspect/lockAndRevalidate`：PROJ在受信租户下为COM交付范围写命令返回current项目经理、生命周期/阶段、项目/参与者/树版本和直管目标项目`ACTION_EDIT`组合事实；该编辑资格只由锁定目标项目行的current manager证明，不读取授权Grant或后代范围。锁定重验按根项目→目标项目→当前树版本执行，并比较经理、根身份及全部冻结轴。三类闭环终态保留最后真实阶段，不限制为S6，树版本必须为正；存在的当前Owner事实必须先通过结构校验，损坏时返回Owner损坏，只有结构合法后与冻结事实不一致才返回`FACT_STALE`。当前只有公共接口和机器合同，未注册生产Provider；
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
| PLT | SOL-01公共基础切片、PRE-04组合依赖 | 原动态表单用户REST保持不变；内部`DynamicFormBusinessInstanceApi`提供用途检查/锁定重验、业务上下文创建/读取/复制/完整重验及实体数据只读投影 | 业务动作封闭并由inspect冻结；创建/修改/复制/持锁重验为`MANDATORY`，无外层事务拒绝；调用方预分配实例ID；Owner Provider先于PLT锁，PLT不拥有消费方状态 |
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

F-SOL-003现已形成首个真实调用方，因此F-PLT-002前向增加`DynamicFormBusinessInstanceApi`与`DynamicFormBusinessObjectPolicyProvider`。动作封闭为修订发布/冻结使用及实例CREATE/READ/PATCH/COMPLETE/CLONE_SOURCE/CLONE_TARGET/FILE_READ/FILE_WRITE，inspect与锁定重验不得换动作。对受信业务Owner实例，动态表单文件Provider将`ARCHIVE/INVALIDATE`委托为`FILE_WRITE`；它们只能从F-PLT-001现有文件管理REST进入，命令端另行校验`pms:file:archive`，手工实例和动态表单页面均不获得该能力。SOL经该API组合表单/文件上下文，并通过inspectEntityData传入实体普通值生成只读校验事实；PLT不持久化传入正文，撤除业务普通值patchInstanceValues出口。需求分析普通PATCH仍走SOL /preparations/{id}/form，写sol_preparation.entity_value_json并递增实体content_version/version；If-Match仍校验文件上下文，X-SOL-If-Match校验实体版本。业务上下文无用户REST，不得访问PLATFORM Service、Mapper或表。外层SOL命令拥有唯一幂等和业务审计，PLT写方法/持锁重验必须加入既有事务。

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

### 14.1 可配置同步的证据复用策略

2026-10-09 需求方授权优化400万级设备序列号/发货同步。`/api/v1/pms/integration/tasks` 的 definition 增加可选 `evidencePolicy`：缺失、空值或 `FULL` 保持原完整逐轮证据行为；`REUSE_UNCHANGED` 仅允许 `TABLE_MAPPING + STREAMING_CURSOR + RETAIN`，不允许清空、重置映射或接管。

复用只发生在目标规划已经完整检查并返回 `UNCHANGED`、目标身份与现有绑定相同、完整来源载荷及转换后事实与既有绑定语义相等时；缺少可验证旧事实、源字段变化、目标变化、异常、跳过或身份不一致均不复用。被复用行保留既有 `last_run_id` 作为最近落证批次，当前运行记录完整读取/未变化计数及额外的 `EVIDENCE_REUSED` 计数（后者是未变化子集，不能再计入总处理行数）。新源事实仍通过原 PLT 证据接口落库；不修改、删除或重分类旧证据。预览不写目标或绑定；比较时忽略仅供预览展示的 `_previewVirtualIds` 标记。

目标写入、必要的新证据、绑定更新和分块断点仍同事务提交，任一步失败全部回滚。客户端可显式选择策略，旧任务不会被后台静默修改。绑定的热存储可使用可逆紧凑编码去掉重复源字段；公共查询仍返回原 `fieldsJson` 结构，不能将内部编码泄漏为接口破坏。

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
- `GET /api/v1/pms/projects/{projectId}/customer-overview`由CUS提供项目客户只读摘要：要求`pms:project:query`及`pms:customer:query`，经`CustomerQueryApi.getProjectCustomer`复用PROJ公开FULL VIEW与绑定事实只读契约，再按绑定唯一客户ID读取同租户客户。请求不接受客户ID或编码；未绑定客户返回null，绑定客户不存在时沿用客户不存在错误。响应复用`CustomerRespVO`及既有联系方式脱敏，不附带其他项目、设备、地点或全局变更历史；停用客户的既有项目引用继续展示。客户主档全局查询与修改范围不因此扩大；PUT及联系人写入继续使用原`ProjectContactContextApi`。
- `PUT /api/v1/pms/projects/{projectId}/customer-overview`由CUS维护当前项目唯一关联客户的平台备注，仅接受`remark`，要求`pms:project:query`及`pms:customer:update`，必须携带`If-Match`客户版本和`Idempotency-Key`。CUS通过PROJ既有范围契约解析并锁定重验当前MANAGE完整项目范围，再由`ProjectContactContextApi.inspect`取得同租户唯一客户；不接受客户端客户ID、不修改项目绑定或CRM权威字段。复用CUS更新、乐观锁、幂等、历史、审计及事件；项目未关联、范围撤销、非ACTIVE项目和旧版本均拒绝且无成功副作用。该入口不授予客户主档全局范围，也不覆盖既有历史。
- 设备客户归属命令固定为`POST /api/v1/pms/devices/{id}/actions/assign-customer`，携带目标客户、关系类型、原因、`If-Match`和幂等键。
- 设备详情采用固定摘要外壳和分Tab DTO；每个Tab统一返回`sourceSystem/sourceVersion/dataAsOf/syncStatus`。官网信息通过`KnowledgePublicProductInfoQueryApi`查询KNO已发布版本。
- 配置Log下载链接默认5分钟、可配置、绑定当前用户和文件；每次生成前重新校验设备查询与文件下载权限。

### 5.6 PM-03 冻结阶段图推进与BPM定义身份

Requirement：PM-03@V1、PM-11@V1。沿用`GET /api/v1/pms/projects/{id}/stage-advance-readiness`和`POST /api/v1/pms/projects/{id}/actions/advance-stage`，但不再把S0～S3或数字相邻阶段作为合法全集。目标由冻结ProjectStageTransition唯一解析，客户端不得指定targetStage。

Readiness返回当前节点、唯一目标或冲突候选、current/target Stage版本、transition版本、绑定/规则版本及Owner事实版本；只是预览。命令以If-Match、Idempotency-Key和expectedCurrentStage/expectedTreeVersion重验；先检查当前CompletionRule与EXIT，再解析唯一后置，再检查目标ENTRY。解析为空/多义、Owner未知或版本变化不写阶段。

统一锁序：PROJ根/项目→按ID排序的当前与目标Stage→transition/执行契约→Gate/Reference→公开Owner Fact。当冻结配置指定同事务的验收范围绑定动作时，COM锁当前scopeVersion，ACC精确绑定该范围，二者以MANDATORY加入同一事务；任何ACC/COM失败均不关闭原阶段、不改变current_stage。旧COM enter-acceptance-stage仅可委托PROJ命令，不保留第二个阶段Writer。独立触发与该推进事务不是同一完成点，其身份、动作前置及锁序须由Q-TPLACC-001定稿，不因目标为S5隐式触发。

成功同事务写当前Stage完成、目标Stage活动、Project.current_stage/version、不可变StageSnapshot、审计、Outbox及幂等结果。当前为终点时返回可申请闭环，不创建虚假S6。计划/方案后期换版仅触发重算，不隐式回退。

Gate审批沿用ProjectStageGateProcessOwnerApi：只冻结processDefinitionKey；默认最新可启动定义，授权人可选同key历史processDefinitionId；记录实际定义ID和完整taskDefinitionKey，禁止独立PMS流程版本。六类Owner谓词和已有授权继续保留在F-PROJ-008契约，不因图改造扩大权限。

错误沿用BUSINESS_GATE、VERSION_CONFLICT、DEPENDENCY_UNAVAILABLE，未知不等于未配置。修订018的图推进及验收范围物理/API差量见09和Q-TPLACC-001；旧NO_PHYSICAL_DELTA只作历史证据，不授权把旧阶段绑定命令用于独立验收。

#### 六类Owner事实与BPM窄接口（保留现有安全契约，补充ENTRY上下文）

`GET /api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/process-definitions`列出该Gate可选择的BPM定义。PROJ先锁定项目、Gate与Reference，并重验`pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`；随后只以受信tenant和Reference冻结的`processDefinitionKey`调用Owner。响应按BPM定义身份稳定排序，仅返回`processDefinitionId/processDefinitionKey/name/selectable`，且只包含同租户、同key、当前可启动并通过本Gate定义约束的记录；不得复用或要求管理端`bpm:process-definition:query`，不得返回其他流程管理字段。

`POST /api/v1/pms/projects/{id}/stage-gates/{gateReferenceId}/actions/start-process`使用`If-Match + Idempotency-Key`启动关联Gate流程。Body只允许可空`processDefinitionId`：为空时按Gate Reference冻结的`processDefinitionKey`选择最新生效定义；非空时仅作为授权用户从上述同key列表选择的历史定义ID，服务端仍必须重验定义ID归属和可启动状态。tenant、actor、definitionKey、businessKey及系统变量均由服务端构造，客户端不得覆盖；规范摘要包含可空processDefinitionId，同operation异选择稳定冲突。

##### Gate Reference Owner Fact SPI

既有`pms-module-project-api`加性声明类型化SPI，稳定方法为：

```java
String providerKey();
ProjectStageGateFact lockAndRevalidate(ProjectStageGateFactQuery query);
```

`ProjectStageGateFactQuery`只由PROJ服务端构造，固定包含`tenantId/projectId/currentStageCode/evaluatedStageId/evaluatedStageCode/gatePurpose/gateId/gateCode/gateVersion/gateReferenceId/gateReferenceVersion/refType/refCode`；其中Gate、Reference版本来自已锁定实例，`refCode`来自创建时冻结值，既有`refVersion`不得进入Query。`ProjectStageGateFact`固定返回`providerKey/refType/ownerObjectKey/ownerBusinessVersion/factVersion/outcome/unmetCode`，三个Owner身份/版本字段均为非空String：本地/ACC行用稳定ID、业务状态和十进制row.version，BPM用processInstanceId、实际processDefinitionId和`status:endTime`（运行中使用`status:startTime`）组成可重验事实版本，不使用摘要或当前时间。outcome封闭为`SATISFIED/UNSATISFIED/VERSION_CONFLICT/DEPENDENCY_UNAVAILABLE`。Provider以`MANDATORY`加入推进事务；Registry只接受02d分册的固定一对一providerKey，不接受调用方指定、通配或重复实现。

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

模板发布验证实际配置的CompletionRule、ENTRY/EXIT及其Reference/Provider，不强制S0～S3每阶段至少一个EXIT Gate。明确未配置某业务门禁不等于已引用门禁缺失；后者仍拒绝。新APPROVAL/PROCESS引用不写refVersion，定义Key检查继续拒绝START_USER_SELECT(35)。修订018只是目标契约，旧测试/接口须按实际配置语义重新验证。

## PM-03 业务视图注册API与单一模板接入（2026-09-08）

本节承接需求方批准的PAGE/DYNAMIC_FORM复用方案及SDS08/09。PLT管理注册事实，领域Owner管理页面中的对象与命令。视图注册不会产生实体、Project、任务完成或阶段推进。

### 业务视图管理

基路径为`/api/v1/pms/business-views`，受信tenant/actor取服务端认证上下文，不接受Body覆盖。管理读取要求`pms:business-view:query`，创建/修改/复制要求`pms:business-view:manage`，发布/停用分别要求`pms:business-view:publish`和`pms:business-view:disable`；对应Owner组件Provider还须重验该Owner已有配置权限。首批需求分析页面的实体Owner为SOL（页面物理目录不决定Owner），注册由SOL组件Provider校验既有项目模板配置权限，业务办理继续执行SOL原权限；PLATFORM动态表单注册叠加现有动态表单模板维护/发布权限，不新建业务角色。

| 方法与路径 | 请求和结果 |
|---|---|
| `GET /business-views` | pageNo/pageSize、可选entityType/viewSource，返回PageResult修订摘要，含id/entityType/viewKey/revisionNo/ownerContext/viewSource/componentKey/componentVersion/dynamicFormRevisionId/version/status/allowedActions；固定按entityType/viewKey/revisionNo/id排序 |
| `GET /business-views/components` | 返回代码已部署Provider发布的受控组件描述：身份、Owner、实体类型、来源、精确组件版本、contextSchema、supportedActions及三个Provider键；不存在或冲突目录项不供选择 |
| `GET /business-views/{id}` | 返回同租户精确注册修订、上下文Schema及受控动作；停用版仍可作历史解释，不重新赋予执行权限 |
| `POST /business-views` | Body为entityType/viewKey、componentKey/componentVersion及可空dynamicFormRevisionId；Owner、source、schema、actions、Provider键由服务端受控目录决定。Idempotency-Key必填；同身份首次revisionNo=1，仅不存在时创建 |
| `PUT /business-views/{id}` | 仅草稿可整体更换组件选择及表单修订；identity不可改；If-Match、Idempotency-Key必填；成功version+1 |
| `POST /business-views/{id}/actions/copy` | 以If-Match、Idempotency-Key从精确源版复制下一修订，Body为空；identity不变，锁定同身份全部修订后以max(revisionNo)+1分配，唯一键防并发；已存在草稿时拒绝，不覆盖 |
| `POST /business-views/{id}/actions/validate` | 只读校验当前草稿及真实目录/Provider/表单事实，返回valid和有序issues(field/code/message)；不写发布、实体或完成事实 |
| `POST /business-views/{id}/actions/publish` | If-Match、Idempotency-Key，重新校验完整配置与依赖；原子写发布时间、version、审计及幂等结果；失败保持草稿 |
| `POST /business-views/{id}/actions/disable` | If-Match、Idempotency-Key，仅发布版；追加disabledAt/version/审计，不改变正文或其他版本；历史可查 |

创建/复制/更新/发布/停用使用PlatformCommandExecutionApi；幂等作用域包含tenant、actor和动作，规范摘要包括目标id、期望版本及本次意图，不含服务端随机结果。同键同摘要返回原结果、异摘要冲突；操作前仍重验当前功能权限。无业务通知消费者时不造Outbox事件。锁序为稳定视图身份修订→选中修订→依赖的PLT动态表单模板/修订（按既有Owner顺序），不能持表单锁后回头申请注册身份锁。跨实例业务读取不进入注册写事务。

受控目录由已部署Java Provider给出，与浏览器组件键精确映射对应；配置Body不能提交Owner/Provider/Schema或任意组件路径。PAGE组件无需创建新业务页面，直接适配既有页面。DYNAMIC_FORM组件必须引用有效表单发布修订，注册检查只查看配置，不读或复制实例填写值；新模板发布/实际实例使用仍按DynamicFormBusinessInstanceApi的用途和Owner策略重新验证。

错误使用通用分类及本功能稳定错误码：参数/字段组合无效、注册不存在（含跨租户）、版本冲突、注册状态非法、引用/组件不可用和幂等冲突。不得把依赖未知解释成可用或“未配置”。权限失败使用既有403语义，不泄露目标对象存在性。

### 可复用定义与模板组合管理

PROJ的`/api/v1/pms/delivery-definitions`承载八类定义修订，不是第二个模板根。GET分页及GET/{id}精确查询要求既有`pms:project-template:query`；POST创建和PUT/{id}草稿替换、POST/{id}/actions/copy要求`pms:project-template:update`；发布/停用沿用`pms:project-template:publish|disable`。写入均带Idempotency-Key，更新/复制/发布/停用带If-Match。创建指定definitionKind/definitionCode/schemaVersion/payload，revisionNo由服务端分配；引用由`references[{referenceKey,targetRevisionId}]`精确表达，同租户、目标已发布/未停用、无重复槽位、自环或循环。已发布正文不能编辑，停用只影响新引用；复制同身份下一修订不覆盖旧版。

schemaVersion=1的payload按kind封闭校验：STAGE/TASK至少含name、默认workBinding/permissionPolicy/completionRule引用槽位；STAGE还含stageCode及start/terminal标志；TASK的parentTaskCode为模板组合层关系，不用固定深度。WORK_BINDING含bindingType、instanceResolutionStrategy、可空businessViewRevisionId、targetContextCode/targetObjectType/targetObjectKey及contextMapping；原生绑定不含外部目标，其余绑定必须有已发布BusinessView及受控上下文。COMPLETION_RULE含已注册predicate或ALL/ANY组合及结构化参数，不解释任意脚本。PERMISSION_POLICY含requiredActions数组，只声明需求。DELIVERABLE含scope(STAGE/TASK)、deliverableType、required、minimumQuantity、allowedSources、outputType与confirmationRule；必需要求数量大于0，选传允许0，不能由零数量绕过显式必需。GATE含gateType和非空references；MILESTONE含name和criteria。模板插槽覆盖不得覆盖Owner、Provider或字段权限。

现有模板资源统一为`/api/v1/pms/project-templates`，已有旧前缀若保留只委托同一服务。既有分页/身份/草稿/发布/停用/历史/匹配契约保留，增加POST/{id}/actions/validate只读结构检查及POST/{id}/actions/copy新建身份草稿。草稿content增加transitions，元素为transitionCode/fromStageCode/toStageCode/conditionRuleRevisionId/priority/default/revisionNo；Stage明细增加start/terminal与精确stageDefinitionRevisionId/工作绑定/权限/完成规则引用；其他明细引用对应definitionRevisionId。所有引用发布前解析为精确不可变快照；不能使用模板当前指针覆盖旧项目。

模板发布锁定template→draft→引用定义（稳定ID）→PLT注册及表单精确修订；冻结图/定义/引用和模板ACTIVE同事务，失败不产生半个发布版本。新发布只接受显式关系图，sortOrder只作显示，不产生关系。未配置全局审批不强制流程Key；实际Gate/规则缺失、类型不匹配、Provider不可用仍拒绝。当前项目初始化同步消费精确关系图，复制边为ProjectStageTransition并冻结Stage/Task执行契约，全部与原创建事务共同完成；历史缺图的切换不得自动推断，须先完成实际来源映射与授权。

### 业务视图公开查询与宿主

`BusinessViewQueryApi`位于`pms-module-platform-api`：按受信tenant与精确revisionId读取发布事实；用途为NEW_REFERENCE时须未停用，HISTORICAL_REFERENCE允许已停用发布版；DRAFT均不可作为绑定引用。`getRevision`只读，`lockAndRevalidate`以MANDATORY加入已鉴权模板发布事务，按注册身份/修订锁定并重验期望注册version、发布/停用状态及真实组件/表单依赖，再返回精确事实；未知、漂移或不可用拒绝，禁止PROJ直接读PLT表。多引用模板使用`lockAndRevalidateAll(List<Query>)`：先读取精确ID定位身份，按entityType/viewKey排序锁全部注册身份（各身份revisionNo/id顺序），重验全部NEW_REFERENCE/期望version/发布可用；再由现有PLATFORM动态表单Provider只读解析所有表单templateId/revisionId，先按templateId排序锁全部模板，再按templateId/revisionId排序锁全部修订，最后逐项重验目录及依赖并按输入顺序返回。重复引用去重；未知DYNAMIC_FORM Provider没有已实现批量锁协议时明确拒绝，PAGE无表单锁。单引用锁委托同一批量流程，不发生注册A→表单A→注册B反向取锁；不新建通用事务框架。响应只包含注册元数据和精确配置，不读取领域实体。模板配置人员需具备模板查询/维护权限，运行期则先校验项目/节点/Owner对象权限，不能要求业务用户获得全局注册管理权限。

实际宿主接收冻结revisionId、节点/目标稳定引用及由Owner端返回的allowedActions。PAGE适配器调用原领域API；DYNAMIC_FORM适配器使用原PLT手工实例API或对应业务Owner表单API，二者不得混用。节点上下文和模板参数不是授权真值，页面挂载只读，保存/完成/创建仍由Owner按当前版本及授权办理。`changed`触发重新读取，`dirty-change`保护节点切换，不回写完成状态。

新增专用页面只实现受控组件上下文契约、Owner配置Provider和前端精确键映射，然后注册发布；不向模板引擎加入页面专用条件，不允许任意URL、eval或反射加载。

## COM-01 公司范围查询与关系维护

`ContractProjectScope`从SYSTEM现有`OrganizationScopeApi.getActiveScopes(subjectUserId)`取得当前有效scope的非空companyCode原值集合；租户和主体取受信上下文。合同目录、详情、销售订单、订单行和项目—合同关系维护共用该集合，SQL必须保持精确字符串相等，空集合不得省略条件成为全量。

关系写入前重新读取scope，按ERP合同公司编码重验；重验失败不写关系、成功幂等、Outbox或成功审计。成功时仅把命中scope ID/version按稳定顺序写既有审计。合同金额等敏感字段另需`pms:commerce:contract:sensitive-read`，该权限不替代公司范围。

## COM-01 验收范围绑定（修订018差量待定稿）

现有`ProjectAcceptanceStageFactApi`、`bindForStageEntry`和`bindEffectiveScope`仅承载原阶段快照及范围版本语义，并不创建验收报告。修订018确认独立验收不以S5为前提，但保留PROJ项目上下文、COM数量/范围真值和ACC绑定Owner；新受控范围动作的身份、权限、前置、事务、锁序、重放及范围变更失效契约必须先完成Q-TPLACC-001，不伪造阶段快照、不由报告状态补建绑定。

锁序固定为PROJ项目当前行→COM订单行（适用时）→COM `DeliveryScope`当前行（按稳定ID）→ACC `AcceptanceScopeBinding`。Q-FCOM-002关闭前不执行退出/回退绑定关闭或解锁。

当前阶段准出和目标准入分别以`gatePurpose=EXIT/ENTRY`及实际被评估Stage身份调用同一Provider；`currentStageCode`只保留项目当前阶段上下文，不能替代目标Stage。Gate、Reference均须属于被评估阶段的冻结定义；不能把源阶段通过结果复用为目标准入。模板发布校验实际被引用阶段的CompletionRule、适用ENTRY/EXIT Gate及所有Reference和Provider，未实例化阶段不得生成强前置；配置缺失、Provider未知和空引用不得以空集真值绕过PRD强制条件。BPM定义检查继续拒绝不支持的START_USER_SELECT(35)，不新增PMS流程版本。

## 修订017差量契约

| 既有命令/契约 | 修订017最小数据与处理 |
|---|---|
| `POST /api/v1/pms/projects/{id}/actions/advance-stage` | If-Match/Idempotency-Key及expectedCurrentStage/expectedTreeVersion/expectedGraphVersion；目标由冻结图唯一解析；响应含transitionId/graphVersion/源准出和目标准入结果。实际锁序及事务见05。 |
| 阶段readiness | 返回实际目标或可恢复缺口，不产生授权或完成事实；无S5/S6不得虚构节点。 |
| COM验收阶段入口 | 旧接口保留原适用契约；新验收业务动作按配置及Owner契约执行，不直接写current_stage，也不把S5当成终验实体前提。 |
| PM-06范围追加 | projectId、baseScopeVersion、合同/订单行/数量、影响及审批引用；服务端重验后才切范围、补任务/绑定；新旧范围模型见08。 |
| ACC报告/验收Fact | 当前报告版本、结论、reportEvidenceValid、acceptancePassed、精确scopeVersion/范围及来源文件；不接受客户端直接指定通过。 |
| CLO-01/02 | closureType、真实closedFromStage、Gate快照及来源/项目版本；提交时重新鉴权和重验快照，不能补造阶段。 |
| CollectionTask创建 | PUBLISHED_TEMPLATE引用或仅EXE-03 APPROVED_BUSINESS_SNAPSHOT引用；P3精确上下文；预检及正式执行分别签发授权（12）。 |
| 文件回调 | 来源身份、签名、对象/任务权限与幂等均独立校验，失败不产生可引用文件（13）。 |

本节只更新契约，不声明Provider、公开Java接口、OpenAPI或数据库已实现；受影响物理合同及Feature Ready必须重验证。已有路由继续受原功能权限和领域Owner约束，不增加通用绕过入口。

对应PRD审查项、派生覆盖和验证结果见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。本文不能替代Feature物理合同重验证、独立复审或运行测试。

### 修订019 PM-01项目级成员指派

原尚未实施的`AssignInitialProjectManager`和单人首次-only请求不再作为编码合同。修订019要求项目级联合/单独指派、多个PROJECT_MANAGER成员及一个当前主责，在各业务阶段按权限调整；不映射固定ProjectTask，不通过通用任务complete改变人员。

PROJ成员时态关系承载完整名单；`proj_project.manager_id`及其姓名/工号字段仅保留当前主责投影，主责必须指向有效经理成员，投影与责任变化同事务维护。不能为了兼容旧消费者只选第一名经理或把其他经理忽略。联合命令须逐项授权、版本/幂等控制、整体回滚及记录成员和主责前后值；不回写冻结审批、模板或任务历史。

Q-FPROJ-010已确认候选为同公司、可跨部门的有效在职人员，并由下述通用公司/角色用户资格查询承接。集合指派、增补/移除/主责切换的写命令仍由PROJ拥有，不因提供查询自动执行指派。F-PROJ-001承接新闭环，F-PROJ-005保留服务经理职责及其原规则，F-PROJ-007不承担指派前置。F-PROJ-008及跨Owner消费者区分主责责任引用与授权用的有效角色成员集合；同角色操作权限相同，不能只允许主责经理操作。原固定任务方案不恢复。

#### 通用公司/角色用户资格查询（PM-01、INT-09；Q-FPROJ-010）

当前经理读模型在同一只读REPEATABLE_READ事务中读取Project与成员，避免并发调整产生旧主责/新名单。联合响应的serviceManager只包含assignmentId/effectiveFrom/previousPrimaryManagerId/currentPrimaryManagerId，不返回子步骤的版本或指派状态。

项目基础成员页面新增`GET /projects/{id}/project-manager-candidates`，仅接收keyword/pageNo/pageSize；要求`pms:project:assign + MANAGE`，公司和PROJECT_MANAGER角色由服务端项目事实确定，返回通用资格PageResult，不接收客户端公司/角色覆盖。`GET /projects/{id}/project-managers`要求`pms:project:query + VIEW`，返回与经理写响应同形的当前有效集合及Project主责引用/version（changed=false）；历史复用既有`GET /projects/{id}/members`，不以成员加入时的PRIMARY标记推断当前主责。

联合/分次入口为`POST /projects/{id}/actions/update-members`，要求同一If-Match、Idempotency-Key和`pms:project:assign + MANAGE`。Body沿用项目经理addUserIds/removeUserIds/primaryUserId/reason，增加可选serviceManager对象（levelCode/managerId/siteId/assignmentType/departmentId/departmentCode，语义与旧服务经理指派相同）；省略表示不调整服务经理。服务端先获得原项目管理授权及根锁，再在同一REQUIRED事务顺序执行服务经理和项目经理命令，任一失败全部回滚，保留两类审计/Outbox及一次联合请求幂等结果。原服务经理命令每次版本+1，经理集合实质变化再+1；响应serviceManager及projectManagers中后者version是最终If-Match版本，不向客户端暴露中间状态。外层幂等键稳定覆盖整个意图，内部子命令键仅在首次执行回调内生成，不能被客户端单独重试；重放不再次执行子命令。联合操作中服务经理改派可能移除操作者原成员资格，后续经理写入复用变更前已锁定的管理授权，不再以本事务中途关系重新拒绝已授权的整次请求；独立经理写入口仍完整授权。

成员工号及主责工号投影可空；上游资格结果没有权威工号时，新成员工号及其主责投影置空，不能将username推断为工号，也不能保留上一主责的工号。

项目经理写入使用`POST /projects/{id}/actions/update-project-managers`，要求既有`pms:project:assign + MANAGE`、If-Match和Idempotency-Key。Body为`addUserIds/removeUserIds`（省略为空）、可选`primaryUserId`（省略保留）和非空reason；增删集合不能相交。剩余成员非空时主责必须属于其中，删除原主责且仍有成员须显式选择新主责；全部移除则清空主责。仅真正新增及新选主责回源公司/PROJECT_MANAGER资格；移除不因旧人资格失效而受阻。同键同请求重放，新键无实质变化返回原版本/changed=false且无变更事件。锁序为现有授权守卫的项目树根→目标Project→有效经理成员；在一个事务追加/结束成员区间、维护主责ID/姓名/工号投影、重算双主责状态并增加一次项目版本、记录审计与ProjectManagersChanged Outbox。主责切换不结束仍在任的成员区间，不修改成员加入时的责任类型/组织快照，当前主责唯一以Project引用为准。响应为projectId/version/primaryUserId/assignmentStatus/changed及有效经理列表；不写阶段、生命周期或任务。通知投递接入不在本后端增量，不把Outbox写入当送达。

修订019角色事实消费细则：`ProjectParticipantFactApi.inspect`指定subjectUserId以及`lockAndRevalidate`指定userId时，按有效PROJECT_MANAGER成员区间判定，不以manager_id或PRIMARY责任类型限制角色操作；返回命中成员的实际责任类型（历史空值沿原PRIMARY归一），该类型不授予或削减项目经理权限。不指定用户的查询继续承接既有主责通知引用，不从多经理中任选一人，也不得用于用户授权。重验保留Project→成员锁序、项目版本/生命周期和调用方指定阶段检查，不将角色API硬编码为仅S1可用。阶段写命令同样核对有效成员，其他权限与门禁不变。

SYSTEM在已有PMS组织扩展`OrganizationScopeApi`上提供加法方法`pageCompanyRoleUsers(CompanyRoleUserPageReqDTO)`；不是PROJ专用项目经理接口，不修改上游`AdminUserApi/DeptApi/PermissionApi`定义，也不放宽现有`pageActiveUsers`的公司+部门语义。PROJ等业务调用方先执行自己的功能权限与业务对象数据范围校验，再调用此内部公共接口；不授予全局用户管理权限，不允许客户端覆盖租户上下文。

| 请求字段 | 语义 |
|---|---|
| `companyId` | 必填正数，指定公司；PROJ从受权项目读取，不信任客户端换公司的输入 |
| `roleCode` | 必填非空稳定代码，精确对应上游公司授权记录的`scope_role`；PM消费者请求PROJECT_MANAGER，接口本身不硬编码角色 |
| `userIds` | 可空：null查询符合公司/角色的用户；非空集合只查询这些用户；空集合返回空，不省略过滤扩大范围 |
| `keyword` | 可空，沿用用户名/显示名包含搜索；不拼接客户端SQL |
| `pageNo/pageSize` | 沿用平台分页，页码至少1，页大小1～100；按稳定userId排序 |

响应为`PageResult<CompanyRoleUserRespDTO>`，每个用户只出现一次，最小字段为`userId/username/nickname/companyId/roleCode`。多个部门授权记录不能让同一用户重复、重复计数或跨页遗漏；不返回密码、Token或与资格查询无关的用户字段。

资格谓词在SYSTEM统一维护：受信租户下用户和公司有效，且同一条当前有效的`UserCompanyDepartmentScope`同时匹配指定companyId和roleCode，授权记录未删除/未停用、已生效且未到期。部门不作为本接口的限定条件，不把来自不同公司或不同记录的角色与公司拼成授权；项目成员表不参与候选查询。指定用户但无有效组合授权返回空，参数非法返回既有INVALID_ARGUMENT、公司无效返回ORG_SCOPE_INVALID，上游不可用保留真实错误，不降为“无权限/无人选”。

候选分页与提交重验使用同一查询：提交时以相同companyId/roleCode及本次待新增用户集合回源查询，完整检查所有分页结果，任一用户不再符合即拒绝写入。页面旧结果不授予指派资格；取得资格也不等于已成为项目成员。该查询只读，不创建成员、任务、授权Grant、通知或业务成功事件，不建立新的通用权限表、快照系统或hash指纹。

实现查询复制既有候选SQL中可复用部分后增强为独立场景Query/XML；公司与角色必须落在同一授权记录谓词，用户去重与COUNT使用同一条件。必要测试只覆盖精确公司/角色、跨部门、多记录去重、错误公司/角色、过期/停用、空userIds、分页及旧服务经理查询不变。当前为待实现合同，不宣称接口已装配或Runtime通过。


## 现场培训 PDF 导出补充（2026-09-20）

- Requirement：ACC-01；Feature：F-ACC-005；依据：用户明确要求PDF下载与管理员统一配置的独立打印模板。
- `GET /api/v1/pms/training-records/{id}/pdf`：管理端鉴权，沿用部署的admin-api入口前缀及租户头；权限`pms:imp-training:query`。按现有培训查询取得租户内记录，不存在拒绝；返回`application/pdf`、`Content-Disposition: attachment`、`Cache-Control: no-store`。只读生成，不更新文件、版本、归档或状态。
- 既有培训create/update增加可选`printTemplateId`，get/page增加`printTemplateId`及`printRevisionId`。草稿首次选择/更换模板时，通过PLT既有`inspectCurrentRevisionForUsage`与`lockAndRevalidateRevisionForUsage`获取并锁定当前发布修订，在培训保存事务内存储验证后的布局快照。普通保存保留已绑定快照；已外发/已确认仍按原状态规则拒绝编辑。
- IMP注册provider key `(IMP, TRAINING_PRINT)`、usage `TRAINING_PRINT`。复用PLT公开API和现有模板权限，新打印模板使用原生FormCreate配置与规则（分类TRAINING_PRINT_FORM），绑定时校验培训名称、内容、时间和签字图片字段并保存完整快照；管理端培训get响应增加printLayoutSnapshot供预览导出，公开响应不包含此字段。旧trainingPrint配置保持兼容。模板CRUD、发布、启停及并发语义保持原契约，不新增PLT接口或跨模块表访问。原PDF接口遇到原生FormCreate快照返回业务错误，提示在前端打印预览生成PDF。
- 既有无快照记录使用内置标准版式，不回写历史；旧HTML生成及归档接口保留。业务字段、版式约束、迁移和验证见[F-ACC-005](../../specs/features/F-ACC-005-training-pdf-print-template.md)。


## 统一设备采集公共接口（2026-09-21 专项批准）

Requirement：INT-12、EXE-03、EXE-04、NFR-02。管理端部署前缀仍为 `admin-api`。租户来自认证上下文；页面不得指定权威业务项目、完成方式或日志消费者。

| 公共路径 `/api/v1/pms/device-collection` | 方法与契约 |
|---|---|
| `/sources/{entry}/{objectId}` | GET 来源上下文；只接受已注册的 `center`、`configuration`、`joint-test` |
| `/sources/{entry}/{objectId}/executions` | GET 分页历史；POST 接收 requestKey、expectedVersion、协议、临时目标/密码或 credentialId，以及 templateId 或获准的手工 commands；center 额外选择 deviceId |
| `/sources/{entry}/{objectId}/executions/by-request-key` | GET 按原请求查询；响应不明时只查此键，不重发命令 |
| `/sources/{entry}/{objectId}/executions/{id}/cancel` | POST 请求取消，只有 DAC 终态或持久化未下发取消证明可结束任务 |
| `/sources/{entry}/{objectId}/executions/{id}/consume` | POST 兼容原关联动作，现为显式重试业务回传；以冻结来源和当前业务授权写入业务日志，再确认成功消费，不推进业务状态 |
| `/sources/{entry}/{objectId}/executions/{id}/download` | POST 返回按原来源查询权限和项目范围校验的短时文件访问地址 |
| `/templates` | GET 按用途/协议/发布状态筛选；POST 保存草稿或新修订；标识+修订唯一 |
| `/templates/{id}/publish`、`/retire` | POST，带当前 version；发布请求开始后内容冻结，停用不修改历史 |
| `/connections` | GET 当前项目下本人连接；POST 显式验证保存并建立本人精确范围授权；秘密 write-only |
| `/connections/usable` | GET 当前项目、设备、协议和模板的唯一有效授权连接 |
| `/connections/{id}/grants` | GET 创建人查询授权；POST 指定 userId、templateId、expiresAt，设备和协议从连接冻结 |
| `/connections/{id}/grants/{grantId}/revoke`、`/connections/{id}/disable` | POST 撤销/停用，由后台对账取消仍在执行的任务 |

旧配置路径 `/api/v1/pms/implementation/configurations/{id}/collections` 保留并转接同一应用服务，原 requestKey 与命令历史不变。重新执行传 `retryOfId`，必须引用当前来源内已终结的任务，并使用新 requestKey；不会自动复用临时秘密。业务联调只接受已发布模板；配置手工命令只接受临时秘密。未接入 CUT/SRV 来源失败关闭，不能绕过业务授权。

权限：独立采集 `pms:device-collection:query/execute`；模板 `pms:collection-template:query/create/update/publish/use`；保存连接 `pms:device-credential:query/create/update/grant/use`。配置/联调执行和查询继续使用原 IMP 权限；日志下载额外遵守 `pms:file:download`。默认授权仅给创建人，不为管理员或项目成员隐式授予凭证使用权。所有 mutation 均重验权限；模板发布、业务版本、幂等冲突和授权失效通过稳定业务错误码 `1010005000` 返回可操作的安全提示。

创建连接的响应不含秘密或 DAC 引用；待完成记录仅向创建人提供原注册键、模板和有效期，允许重新输入密码继续原请求。时间沿用平台 EpochMillis JSON 约定；Long 标识沿用平台安全序列化，不按 JavaScript 浮点数截断。

### 直接设备日志解析（2026-10-06 专项批准）

`/api/v1/pms/device-collection/log-parses`：POST 提交，GET 按 `projectId` 分页查询；GET `/{id}` 查询任务和结构化结果。上传请求为 `requestKey/projectId/inputName/inputText/commandHint?`；已采集日志请求为 `requestKey/projectId?/entry/objectId/executionId`，两种来源互斥。上传限制为非空文本、UTF-8 最多 8 MiB，前端支持 UTF-8 与 GB18030 解码。`requestKey` 为 16–64 位字母/数字/连字符，同键异内容拒绝。

独立上传沿用采集 query/execute 与当前项目范围；业务日志沿用来源 query/update、当前项目/设备绑定及 `pms:file:download`。来源文件必须可用，跨租户、来源已迁移或文件已隔离拒绝。分页默认只返回独立上传；业务来源历史必须显式传完整 `entry/objectId/executionId`。响应含输入文件名、来源执行/文件版本、解析状态、发布版本、警告和可选结构化结果；查询不重新提交。此接口是业务授权入口，调用的独立 DAC 接口为通用 `/api/v1/log-parses`，使用原生 `sourceCollectionId`，不新增 `/npdms/` 路径。详见集成设计 11.7。

### 业务实体自动接收采集日志（2026-09-21 后续批准）

`GET /api/v1/pms/implementation/{entry}/{objectId}/collection-logs?pageNo=1&pageSize=10` 由 IMP 提供；`entry` 仅支持 `configuration` 和 `joint-test`。使用原业务查询权限与当前项目范围，按实体当前项目/设备筛选本实体已接收日志。返回分页元数据：业务实体、项目/设备、执行/任务标识、命令、协议、技术结果、失败类别、不可变文件/结果版本、回传时间；不返回秘密或持久化下载地址。文件预览/下载仍通过统一采集的受权下载接口，继续校验业务归属与文件权限。

内部 `CollectionBusinessResultReceiver` 为业务 Owner 的自动接收端口：成功、失败、超时和取消的安全日志均可追加；租户+任务+结果版本幂等，同键不同绑定/文件拒绝。隔离文件不交付。当前 IMP 实现不得更改配置/联调状态、覆盖手工附件；只有业务接收落库后才通过既有消费 API 完成成功任务。PLT 使用原 Outbox 保证重试，浏览器关闭不影响回传。CUT/SRV 没有接收器时不得代为写入其业务实体。


### 命令与模板解耦（2026-09-21 需求方确认）

独立采集、配置调试和业务联调均允许直接输入命令，模板为可选命令来源；选择模板时仍只执行已发布版本，服务端冻结实际命令。手工命令同时支持临时密码和已保存连接。覆盖此前独立采集/业务联调必须模板、手工命令仅临时凭证的限制，不扩展巡检或割接业务。

保存连接和新增使用授权不要求 templateId；省略时创建明确的不限模板授权（command_template_id=`*`），仍限定用户、项目、设备、协议和有效期。旧模板限定授权及旧空值不自动扩大；原待完成保存请求保留注册摘要和模板。重叠授权拒绝，执行前与执行中继续校验撤销、到期、停用及连接版本。无需修改历史任务或数据库结构。

### 割接调研命令与保存连接（2026-10-16 需求方确认）

`cutover-checklist` 调研采集入口允许手工命令，复用统一采集的来源授权、设备范围、幂等下发、不可变日志和 CUT 结果消费；手工命令不表示采集项或 P3 已完成。调研弹窗支持显式验证并保存连接：用户填写连接名称和授权到期时间，调用既有 `/connections`，仅在 DAC 加密保存成功后建立本人使用授权；密码只在当前交互和请求中使用，不回显或进入业务日志。

调研 `/collection-requests` 关联手工执行时 `commandTemplateId=null`；模板执行仍为正整数模板标识。CUT 保留独立的执行记录、设备、状态、日志及结果版本列；PLT 适配器核验当前项目/设备及精确来源：无模板时只能关联本清单项的 `manual-{checklistItemId}` 执行，不能接受其他项的手工执行或把模板执行冒充手工执行。手工执行不使用虚构模板 ID，不改变旧模板执行的请求摘要、结果或历史。

`GET /api/v1/pms/device-collection/templates` 增加可选 `projectId/deviceId` 筛选对，必须同时提供；服务端校验当前项目查看权限与设备归属后，按正式资产型号保留通用模板及精确型号匹配模板。设备执行入口使用 `publishedOnly=true` 和当前用途。未选择设备时不展示执行模板候选；管理端原有无设备查询保持原义。执行时继续独立校验模板发布状态、用途和设备型号，分别提示未发布/停用、入口不适用、设备型号不适用；不放宽原有校验，不改写已发布模板或历史执行。


### 需求分析展示投影（2026-09-22，PRE-04 / F-SOL-003）

按需求方明确的能力与展示分离原则，新增只读`GET /api/v1/pms/requirement-analyses/revisions/{revisionId}/presentations`，返回本版本可用的展示候选及可选默认模板；可选stageId/taskId互斥，仅指定默认展示上下文，不改变业务对象。使用既有需求分析query权限、当前租户及项目数据范围；不提供通过展示切换写业务数据的出口。候选字段须映射到SOL既有实体/扩展定义，附件使用SOL稳定业务用途码，停用/未发布或不兼容模板不参与。客户端基础业务表单始终可用；显示选择只影响当前页面。

PLT共享`EntityPresentationApi.list(Query(target, actor, categoryCode))`基于实体Owner读取权限列出同租户启用模板的当前发布修订，返回模板标识/名称与只读Schema，不创建或修改表单绑定、扩展定义或业务对象。SOL负责用途/字段兼容及别名映射。来源模板的历史绑定保留为来源证据，不决定操作资格、成果有效性或附件归属。


### 实施方案分级复审接入（2026-09-22，SCH-05 / F-PROJ-009）

新增 `/api/v1/pms/solution-reviews`：GET 按 projectId、solutionId 读取版本关联；POST 按 expectedVersion、processDefinitionId 提交，服务经理节点沿用本项目唯一有效服务经理解析，重大方案工程管理部节点按2026-10-29需求方裁决指定现有`engineeManager`角色共享复审，不指定个人，也不要求`pms:sol-solution:major-review`唯一持有人。同租户任一当前有效角色成员均可办理当前角色待办，发起人属于该角色时允许本人复审；提交人不能从请求指定候选人员。GET `/definition` 返回部署定义和职责元数据；POST `/refresh` 回源重验 BPM 终态；POST `/revise` 从终态复制新的方案草稿。普通查询继续使用方案 query 权限与项目范围；创建、修改正文仍使用原 create/update 权限、当前项目经理资格和租户，不因复审角色取得写正文权限，旧接口不能修改正在分级审批的对象。重复同版本、同定义的请求返回同一在审记录，冲突请求拒绝；新重大复审只保存节点的角色或候选组引用及实际办理人审计，不冻结整组候选人员。

SOL 通过新增 BPM `SolutionReviewBpmApi` 读取实际定义、发起和读取历史。初审候选人沿用项目服务经理关系重验；重大复审通过既有`engineeManager`角色候选组共享办理，读取和办理前由服务端重验当前租户、有效角色成员及当前待办资格，管理员身份或服务经理身份不能替代该角色资格。定义和完整任务 key、职责均来自模型；新实例保留角色候选组引用，不以发起时人员名单代替当前角色资格。当前角色待办成员无项目普通VIEW时，仅能读取及办理该待办所需资料，不扩展普通项目可见范围。只有真实结束且所需人工审核历史完整才返回批准；结果在 BPM 原事务内经受信事件由 SOL CAS 形成批准基线及交付件，不直接推进项目阶段。既有冻结来源、批准版本、旧实例的身份链接和人工历史不回写或自动换绑。

### 项目工前确认历史（2026-09-22，F-PROJ-009 更正）

需求方明确从代码中剔除“历史工前确认记录”。`/api/v1/pms/projects/{projectId}/preparation-confirmation` 的 GET / POST 均删除，不再提供兼容入口；对应业务服务、表单策略、实体和 Mapper 一并移除。原数据库记录及已执行迁移保留归档，不再由应用读取。

工前数据统一沿用 SOL 工勘原有读写、字段目录、表单绑定及确认命令。独立支线的显示条件由工勘表单修订配置，不新增项目字段来源或跨模块读表接口。

方案复审另提供 GET `/api/v1/pms/solution-reviews/source?businessKey=...`，返回当前租户对应真实审批关联的只读冻结方案正文，供 BPM 审批页查看。除原项目范围读取外，允许当前`engineeManager`角色复审待办成员读取办理该待办所需资料；即使没有项目普通VIEW，也须逐次核对当前角色与当前待办关联，不得只凭businessKey、角色名称或历史候选身份取得其他方案或普通项目资料。引用来源、批注及其附件沿同一待办边界校验字段和文件权限，不授予一般项目查询、正文修改或泛化下载权限。

GET `/api/v1/pms/solution-reviews/policy?projectId=...` 只读预览服务器计算的审核级别与不可用原因。内部 `ProjectFieldRuleApi` 按项目冻结模板修订和规则 key 读取既有发布程序，只接受实体 FIELD/CONSTANT 条件，不从请求接收业务事实或重新编译历史规则。读接口校验当前主体与项目范围；提交用事务锁定项目版本的求值接口。返回配置是否存在、各规则三值结果及规则/项目事实快照。无配置与配置不完整分开处理，未知结果不按普通审批放行。

未绑定模板的导入旧项目返回未配置并保留原 SOL 行为；存在部分模板引用而无法验证精确修订时仍返回不可用，不据损坏引用选择普通审核。读取服务在业务调用时解析原发布 Reader，避免发布校验器发现 SOL Provider 时产生初始化循环依赖。

### 许可历史受控迁移（2026-10-11需求方整表批准）

许可历史迁移（2026-10-11需求方16列整表批准）：POST `/api/v1/pms/projects/{projectId}/legacy-detail/license-previews` 和 `/license-imports`复用项目来源快照身份；请求仅包含来源引用和AES-256-GCM密文封装，不接收许可明文。需要既有迁移执行及项目管理权限，AST加入迁移事务完整保存正式许可实体及来源绑定；相同来源重放只读回原ID，源变化、后续目标变化或密文认证失败阻断。缺少唯一设备/合同关系保留NULL并返回待关联原因，不创建对象或激活许可。GET `/api/v1/pms/projects/{projectId}/associated-data/records?kind=licenses`复用项目查询权限及PROJECT_VIEW范围，只返回正式非秘密字段及已加密状态，不返回密文、MAC、密钥版本或原码。历史缓存重建ID的持续同步不启用。

### 设备软件版本维护及具名历史（2026-10-10需求方整表批准）

AST拥有软件当前值和正式历史。软件由项目人员维护或既有日志解析回填，与ITR无关；BOOT取Basic Bootloader。复用当前26列；正式历史31列和旧八项正式实体扩展，详见F-AST-001本次批准增量。历史仅追加，不开放修改或删除。

GET `/api/v1/pms/devices/{deviceId}/software-version` 返回具名当前值、并发version、登记依据时间及服务端计算的canMaintain；需要既有`pms:device:query`及设备可见范围。POST其`/registrations`接收projectId、expectedVersion、requestKey及具名values，需要既有`pms:device:update`，AST事务中通过PROJ解析并锁定PROJECT_EDIT范围，再锁设备、复核实际归属和当前version。同键同设备/项目/操作者/内容返回原recordId；异内容拒绝。更新失败则初始记录、本次记录、当前值和设备投影全部回滚。首次已有当前值而无对应正式历史时保留真实CURRENT_BASELINE，原effective_from为空则recorded_at为空，不伪装历史发生时间。

GET其`/history?pageNo=1&pageSize=20`返回正式记录、可见前记录具名值及旧八项扩展；页大小1—100，按recorded_at、id倒序。历史项目按服务端PROJECT_VIEW范围裁剪，当前设备可见不代表所有历史项目可见，空权限集合返回空。前记录跨租户/跨设备/不可见不得返回。旧迁入记录不推断previous_record_id，不改当前值；正式扩展只允许经验证的一次性迁移初始化，之后只读。

既有日志接收/解析契约保持；AST采用日志接收时间与当前登记时间比较，迟到旧日志保留既有解析事实且不回退当前值。新日志仅更新实际解析出的字段，缺项保留当前值，并与手工维护使用同一锁顺序及当前/历史原子写入规则。parsed_fact_id必须来自既有真实解析事实，不能把旧logId或文件ID充当解析事实；旧软件资料只做一次性迁移。
# 2026-10-11 DPPMS既有发货与订单行引用补正

本任务已批准的发货、正式订单行和设备业务实体完整承接包括其正式引用；不新增物理字段或表。仅对不可变来源、成功绑定、当前完整目标字段和正式业务身份均可核实的记录，由迁移入口补齐引用。PROJ不直接查询或写入COM/AST表。

内部COM的DppmsCommerceEntityResolveApi新增lockShipmentLines：按已经核实的成功绑定目标ID锁定正式订单行，核对租户、明确ERP来源、原公司码的既定转换、订单号、行号和原inventTransId。输入身份来自服务端完整来源及成功回执，不能接受客户端任意目标ID；任何不匹配阻断。

内部AST的DppmsShipmentReferenceCorrectionApi提供preview/correct/verify：按来源发货键、SN、成功迁入设备及发货ID锁定正式记录，校验项目归属、最新发货及所有现有投影值。订单行引用仅可由NULL补齐；设备发货引用仅可由不存在的旧ID补正为同一最新事实，已存在但不同的引用阻断。预览保存发货39列和设备53列全部原值；写入前重新比较，任何后续修改阻断。只修改这两种引用，原业务值、历史审计和版本保持，事务失败整体回滚。重放复核完整执行后字段，不能仅凭回执返回成功。

新增PROJ迁移入口位于现有 `/api/v1/pms/projects/{projectId}/legacy-detail` 下的 shipment-reference-previews / shipment-reference-corrections，均需要pms:integration:execute、pms:project:update以及既有项目可管理范围。请求只允许当前sourceId/snapshotId/checksum及原发货键；目标ID由成功回执和正式Owner核实，旧来源及回执不修改。执行形成独立幂等回执，不修改项目状态或当前分配/验收事实。请求/响应访问日志关闭，避免整行基线进入日志。


# 2026-10-11 已批准的DPPMS成员历史预览

POST `/api/v1/pms/projects/{projectId}/legacy-detail/member-history-previews`复用迁移/同步来源请求（sourceId、snapshotId、checksum）。需要pms:integration:execute及pms:project:update，并由PROJ核对项目管理权限、租户、当前完整来源快照与全部15原字段；请求与响应关闭内容日志。只返回原人员身份、当前正式绑定、有效区间、SYSTEM角色/适用公司资格结果以及现存同角色成员冲突，不创建账号或角色、不改授权或主责。

用户已确认成员表29既有列＋member_code、phone_number、email、registration_source、authorization_confirmed五列。专用迁移Owner可核验ACTIVE及已批准的正常/异常闭环项目；普通成员动作的ACTIVE校验保持。当前成员/授权/任务资格消费者只使用authorization_confirmed=true且有效的记录，未确认历史可在历史页查看并保持只读。主责冲突与其他来源主体/角色继续待裁决；本接口成功不表示已完成成员迁入。完整整表裁决见docs/generated/2026-10-11-dppms-project-member-table-decision.md。

POST `member-import-previews`返回项目版本、既有成员34列及其校验值，并核验原15字段和正式账号/角色/适用公司资格。POST `member-history-imports`接收source、expectedProjectVersion、existingMembersChecksum、已裁决的primaryServiceManagerCode/primaryProjectManagerCode和closeCurrentMemberIds，使用相同功能与项目管理授权。只允许结束已明确裁决的管理员当前主责区间并保留历史；替代人员必须通过普通Owner的相同资格规则。未确认历史不产生授权，9条最终客户人员单独等待CUS方案。

初次迁入使用每来源项目固定、跨操作人共享的命令身份。变更请求或换来源快照不能创建第二套成员；持续同步尚未启用。成员、主责正式引用、来源绑定、回执及审计在同一事务提交；失败整体回滚。重放必须核对原15字段、不可变绑定、正式成员与已结束区间的全部字段及项目执行后事实，后续改变阻断。使用独立迁移参数保留原NULL审计，普通自动填充保持；NULL更新时间取原创建时间，两者原空则保持NULL。SYSTEM提供精确账号的真实工号，不从登录名截取。该历史入口不触发生命周期、任务、审批或规则完成事件。

## 2026-10-12 已批准的全局字典类型迁移契约

SYSTEM公共SystemDictionaryTypeMigrationApi的preview/apply仅承接既有system_dict_type的11列；INT通过受控运行和现有绑定调用，不访问SYSTEM业务表。`GET /api/v1/pms/integration/templates/dppms-dictionary-types`按原类型业务编码提供31个原定义及49个缺定义分组，后者以原编码暂作名称，不造中文。名称/编码沿正式字典唯一规则核验，权限同时要求迁移执行及普通系统字典创建；既有类型不接管，绑定后的全部11列或原材料有变化即阻断。

原status管理标记及effectiveFrom/effectiveTo按需求方明确裁决不迁入，不写启停或备注。31个原定义保留历史时间及精确账号引用，NULL更新日期用原创建日期、NULL人员保留；49个缺定义分组使用实际迁入运行的操作者及时间，重放沿首次正式承接审计，不随新运行改写。显式历史插入避免框架补入管理员；全局模型无tenant_id，当前调用租户、操作者和运行仍须验证。失败与绑定/回执在同一事务回滚，不改角色、状态机或权限。

## 2026-10-12 已批准的主SN与附加SN关系复用

- 业务Owner：AST；既有实体为 `ast_device_relationship`，19列复用，无新增表列。原8列完整映射及日期规则见整表裁决。来源没有项目字段，实体和返回结果均不伪造项目归属。
- 查询入口 `GET /api/v1/pms/device-source-relationships`：`projectId` 与 `deviceId` 必须且只能指定一个。项目入口先校验项目可见性，使用COM公共合同查询API取得精确合同号，并限定AST当前设备/有效项目设备关系；空权限、空合同返回空结果。设备入口先复用设备可见性Owner，再从正式设备解析SN，按该SN读取两侧关系及合同历史。
- 迁移接入复用DataSyncAdapter、运行及绑定模型：`GET /api/v1/pms/integration/templates/dppms-device-relationships`提供完整原8列的受控模板。AST公共API的previewRelationships/applyRelationships返回逐原键的正式关系前后值，既有importRelationships保持兼容。运行操作者从当前成功校验的INT运行创建人解析，禁止猜配调度账号；基线仅ONCE/UPSERT/RETAIN，流式按块保留原空值及全字段绑定保护。未裁决的自关联不在来源SQL中默默过滤；已有关系审计差异阻断，限定存量纠正另按裁决执行。
- AST迁入API只允许迁移执行权限、同租户和事务内调用；来源与绑定仍由迁移/同步功能保存。原日期严格解析并保留毫秒，空更新时间用原创建时间。存量业务或审计差异阻断，普通迁入不吸收人工修改。
- 不生成装配、设备归属、当前附加SN缓存或其他授权。65046条存量审计须另行逐键预览裁决；360条自关联仍等待需求方裁决，禁止自关联约束保持。

## 2026-10-12 已批准的最终客户联系人迁入契约

POST `/api/v1/pms/projects/{projectId}/legacy-detail/final-customer-contact-previews`接收既有来源sourceId/snapshotId/checksum，返回项目版本、精确原项目FINAL_CUSTOMER相关方、原联系人数量与CUS现存关系的完整字段校验值。POST同路径下`final-customer-contact-imports`仅接收source、expectedProjectVersion、projectPartyId、existingContactsChecksum；需迁移执行、项目管理权限和同租户项目范围。请求/响应内容日志关闭。原表15字段须逐值验证不可变身份及行校验，不接受客户端任意联系人值。

PROJ通过公开ProjectContactContextApi的inspectFinalCustomer/lockHistoricalFinalCustomer提供唯一、同项目、经DPPMS项目头来源绑定的最终客户相关方，不按名称认定购货方客户；专用迁入允许已批准的ACTIVE/正常闭环/异常闭环项目，普通联系人维护继续要求ACTIVE和既有管理授权。CUS公开FinalCustomerContactMigrationApi的preview/carry/verify拥有业务写入和完整目标保护，不跨模块访问表。

正式项目联系人复用既有24列并仅新增5个已裁决业务字段；未精确关联客户主档的最终客户联系关系保留NULL引用，必须具备准确相关方且非主联系人。其历史区间、联系方式、原审计保存，原NULL更新时间用原创建时间、原NULL更新人不自动填充。正常维护不得修改、删除或恢复这类只读历史，不创建客户主档、账号/权限、主联系人或当前任务完成事实；普通来源引用、主联系人唯一性和启停规则保持。项目联系人查询返回明确最终客户单位、原编码、有效期和只读标记，客户敏感字段继续经既有Controller权限脱敏。

初次迁入每原项目固定幂等身份；证据、正式实体和回执同事务，失败整体回滚。重放复核准确相关方、项目版本、完整正式字段及来源映射，后续变化阻断。持续联系人同步未启用。

## 2026-10-12 已批准的项目合同关系迁入契约

整表目标解析补充：ProjectContractRelationMigrationApi.resolveReferences以受信租户/执行人/迁移Owner和精确来源项目绑定引用＋合同号为输入，COM通过PROJ既有inspectBindings读取当前正式项目身份、版本及公司，再以正式公司＋合同号查询现有合同。返回逐来源的正式项目ID/当前版本/合同ID；缺失、多义、无管理权或公司范围为空即阻断，不新建主档，不从名称猜配，不用原组模型取代目标模型。INT默认模板不再要求逐行LOOKUP配置contractId/expectedProjectVersion；已有显式配置如保留，必须与Owner解析结果一致。apply仍按既有Owner复核项目绑定、当前版本、合同公司及17列，解析不是写入授权或不可变来源事实的替代。

审计按需求方最新整表裁决：pm_project_contract主值、pm_project_group_relationship同名字段补缺，未选不同成员审计逐字段列排除原因；mergeBranchMark不迁入，新拆分使用正式主子项目功能。sys、异常数据处理仅在专用迁移审计字段保留原码，其他已迁用户写user_id，其他未关联人员阻断；无新账号或授权，不修改正常Yudao审计填充。

GET `/api/v1/pms/integration/templates/dppms-project-contract-relations`需迁移配置权限，提供现有任务/运行功能使用的ONCE/UPSERT/RETAIN模板。完整读取pm_project_contract七列、组成员九列及组八列；来源复合键为合同关系ID＋组成员ID。未解析的桥保留并阻断，不能用过滤、去重、失效后缀猜配替代裁决；原桥独立属性仍待明确业务承接。

INT公开适配器调用COM公开ProjectContractRelationMigrationApi的preview/apply；COM负责完整17列正式关系，新增表/列/扩展均为0。需迁移执行及合同关联权限，通过PROJ公开上下文Owner核验原项目绑定、准确编码、当前版本及管理权；合同按精确ID/号、项目公司和操作者既有公司授权核实。原非空审计账号由SYS公开历史身份API精确关联，NULL保持；原NULL更新时间用原创建时间。

COM apply必须加入INT外层事务，所有本批来源、资格及完整正式前值在第一条写入前校验；绑定、正式关系及运行证据共同提交或回滚。既有关系只在另行裁决的完整reviewedBefore与当前17列完全相同时限定纠正，且不能以此改写来源、项目/合同/角色、有效结束日期或状态。只纠正原人员、创建/更新时间和开始日期，并递增并发版本；原成功办理审计记录保持。普通重放要求上次正式17列及完整原字段均未变，不提供忽略审计差异、复活失效关系或清空来源缺项的选项。

2026-10-14需求方另行批准按原ID恢复已撤回的6,462条关系和6,470条来源绑定：6,408条仍为ACTIVE，54条仍为HISTORICAL。Entry可携带明确的Restoration撤回前值；INT必须通过原任务的成功创建及重放回执核对撤回绑定ID、目标ID、完整26个来源字段和原归并事实。COM要求撤回17列仅比原迁入值多一次删除标记及版本推进，且与当前来源语义相同；锁定当前17列后仅恢复deleted并推进version，保留业务、有效区间、创建及更新审计。已恢复的相同目标只接受精确恢复后值，支持重复来源跨块恢复原绑定；其他后续变化阻断。普通迁入仍拒绝恢复，限定恢复不能同时adoptExisting或reviewedBefore，也不批准新增项目、合同或其他关联层。恢复绑定与COM写入沿用同一事务，旧回执和来源证据保持不可变。

现存24条及10条具体历史纠正另按完整预览保护；全历史重复关系、失效桥和桥表剩余业务属性继续等待裁决，不启用持续全量同步。

2026-10-12需求方批准历史关系复用既有17列，并随后明确所有迁入数据进入正式业务展示区：HISTORICAL表示原历史项目合同关联，RELATED角色和原创建日期保持，原无结束日期则NULL。原-his桥经精确项目绑定承接历史性质，具体已批准的身份例外及缺组排除放在版本化任务SQL参数和migrationDecision LOOKUP配置中，不在Owner写原ID或日期名单；原26字段与配置证据不一致即阻断。相同租户、项目绑定与版本、执行及关联权限、精确合同公司和完整17列保护保持；不允许接管当前关系或改写当前/历史状态。GET `/api/v1/pms/contracts/project/{projectId}/relations`采用PageParam／PageResult、每页1～200条，复用合同查询权限并同时核验项目VIEW及合同公司范围，空公司范围返回空列表，按创建时间＋ID稳定排序。在同一正式合同展示区域返回当前及历史关系业务/审计、合同号/名称及公司，不返回技术绑定，不据历史关系授予当前合同访问权；正常关联和项目创建遇同身份历史关系明确拒绝当前重放。当前商务授权查询和唯一约束保持。

2026-10-12需求方批准通用同类重复规则：相同原五字段、精确项目/完整桥和合同资格一致时，共用一条正式关系并承接最早原创建时间，每个原ID＋成员ID独立绑定，其他创建时间明确列明不独立迁入正式审计的原因。新来源模板从完整原表按实际组合索引精确计算duplicateGroupSize，必须读齐组后才合并，不采用时间差阈值，不跨不完整分页猜测、不新增物理列。旧冻结任务定义和回执保持；实际新增历史项目及afxx主体依然需要各自正式迁移绑定和审批边界。

## 2026-10-12 已批准的基础字典项迁入契约

GET `/api/v1/pms/integration/templates/dppms-dictionary-data`需迁移配置权限，提供ONCE/UPSERT/RETAIN模板，读取fnd_basic_data全部12列及532条，不在SQL中滤掉空ID114。SYSTEM公开SystemDictionaryDataMigrationApi的preview/apply复用system_dict_data全部14列，新增表/列/扩展均为0；INT只通过该API写入，来源、逐值回执及绑定沿用现有迁移功能。

权限同时要求迁移执行及普通系统字典创建，核实运行租户、来源Owner、配置版本及操作者。正式字典类型必须唯一、编码精确且启用；真实数据库字符比较和业务键唯一规则保持。全部待写记录先核对，既有字典项不自动接管；成功绑定后的完整14列、全部原12列或历史审计变化阻断。SYSTEM写入与INT来源绑定/回执同事务，必须REPEATABLE_READ，失败整体回滚，不改普通字典维护行为或全局模型。

原NULL排序取0，NULL创建时间用实际首次迁入时间，原NULL更新时间取创建时间，原NULL人员保持；重放保留首次初始化时间。仅原ID542的创建人system按独立裁决保留原编码，不生成账号或授权，其他人员精确关联既有SYS历史账号。原有效区间用于已批准的首次启停转换，结束项停用，不执行旧入口。basicDataAttri1/effectiveFrom/effectiveTo已明确不迁入，逐列列明缺同义字段及用户排除原因；来源证据不称为业务承接。

ID114只在原分组23和全部业务/审计属性仍为空时显式返回SKIPPED及排除原因，不创建字典项或目标绑定；若后续出现业务值则阻断，不能自动沿旧排除裁决丢弃新事实。运行界面分别展示14个正式字段和12个原字段，跳过记录不显示虚构的正式字段。

## 2026-10-12 已批准的主附SN限定历史审计纠正契约

POST `/api/v1/pms/integration/runs/{id}/recover`复用既有中断恢复能力，要求`pms:integration:execute`，仅查询当前租户的指定主运行。沿用既有两分钟判定；当前适配器执行锁仍被占用、任务运行归属或配置版本改变时阻断。持有同一执行锁和事务后恢复批处理元数据，保存失败回执并解除该任务的运行归属，保留已提交分块、来源证据、绑定及断点；不启动调度、不处理其他运行、不写业务表。已经结束的主运行重复请求返回false，不覆盖其回执；不存在或跨租户请求拒绝。重试仍使用既有POST runs及retryOf，不绕过配置、来源和目标保护。

GET `/api/v1/pms/integration/templates/dppms-device-relationship-audit-correction`沿用迁移配置权限、ONCE/UPSERT/RETAIN及流式分块，全部读取fb_shipment_barcode_relation八列。独立适配器不改变普通关系迁入的审计差异阻断规则。仅已裁决原键可经既有LOOKUP映射输入完整原字段、目标19列、旧绑定及旧成功回执的保护指纹；运行租户、配置版本、操作者、原连接/任务及成功状态必须准确匹配，原任务不得正在执行。

INT只读取并锁定自己拥有的旧任务、回执、绑定；AST公开DppmsDeviceSourceRelationshipApi的previewAuditCorrection/applyAuditCorrection核验权限及完整19列后，只纠正原create_time/update_time并递增version。目标关系、ID、操作者、有效区间及原来源身份保持。整块先全部核对，再写入；AST必须加入INT事务，目标、纠正证据、绑定与断点共同提交或回滚，原回执及旧绑定保持。后续任一字段改变阻断，重放核对本纠正任务保存的完整19列，不再次递增版本。

已裁决360条两侧SN及产品相同的原记录，必须准确原键和八列仍符合裁决，明确返回SKIPPED及排除原因，不创建关系、放宽自关联禁止或静默在SQL过滤。未裁决的新原键、已裁决原字段改变，均阻断。新增业务表、列及扩展均为0。

## 2026-10-12 整表项目正式创建及来源身份契约

项目任务读取pm_project，按原类型＋精确原编码采用最大projectId；所有原ID的关联经通用身份规划归入规范主档。SQL初始文本复制到既有Source.sql，可编辑和版本冻结；项目类型及验证项目ID使用参数，最终执行参数projectTypes=10。安服两类型的通用设计和Owner规则完整实施、测试，但本次不迁入数据。

ProjectMasterMigrationApi.preview/apply复用既有项目字段。新来源仅在无目标、无上次结果且不接管既有项目时创建；完整归属复用正常创建权限及操作人公司/部门范围，精确解析正式目录。用户批准原公司或办事处NULL时保留NULL：仅既有super_admin且同时具备pms:integration:execute、pms:project:create的执行人可迁入，不生成范围或成员授权，不能猜配NULL或清空已有正式归属；非空原键缺目录及未知公司码仍阻断。创建独立根、保留原码及原历史审计，source_type=MIGRATION，不实例化当前模板、计划、任务或审批。原关闭日期缺失保持NULL；安服及用服状态采用已裁决的类型对应。普通创建和关闭行为保持。现存初次接管继续复核原证据及版本；已有绑定的整表项目按目标ID和完整上次结果保护，不要求先建立迁移快照表。新增表列为0。

INT公开内部DppmsProjectBindingApi.resolve：Query仅含受信tenantId、来源连接ID、原项目键；只读本模块正式迁移绑定，核对DPPMS_PROJECT_MASTER、原37字段及正式项目事实中的ID、原类型/编码、目标编码，多个不一致绑定阻断。返回精确ProjectBinding引用，缺失不伪造。PROJ/COM只经公开API使用，不读取INT业务表。

整表身份映射任务DPPMS_PROJECT_IDENTITY复用INT来源绑定，无新增表列。可编辑project-identities查询保留每个原ID、类型、原码及同类型同码的最大规范ID，主档先迁入。每个原ID绑定已迁入的规范正式项目，不以技术绑定承接业务属性；非规范主档属性不独立覆盖正式字段的原因沿已裁决整表清单记录。相同身份重放不得重绑，来源类型/编码或目标变更阻断。

ProjectMasterMigrationApi.verifyBindings/lockBindings接收BindingVerification(tenantId,actorId,projects)，最多500个完整Facts且正式ID不重复；PROJ核验管理权限、租户、删除标记和全部正式主档事实。前者只读，后者必须参与INT事务并锁定正式项目，避免身份绑定提交前目标被修改；不初始化状态、不修改业务或权限。DppmsProjectBindingApi可解析身份别名，但必须再次核对规范主档完整绑定；缺失或相互矛盾的主档/别名阻断。

身份映射另复用ProjectMasterMigrationApi.inspectBindings(IdentityInspection)：最多500个IdentityTarget(projectId,projectType,projectCode)，由PROJ核对精确身份、权限及租户并返回当前完整Facts，随后lockBindings保护本次读取的当前事实。已迁项目的正常成员、计划、审计及版本变化保留，不要求回到旧迁入版本。INT别名仅保存原身份四项及正式项目身份引用，不能保存这些当前Facts作为业务承接或覆盖原主档迁移回执。实际编码/类型或当前读取到提交间的变化仍阻断。

新历史根在同一INT事务内复用正式ProjectTreeProjectionService.publish，按正常根创建规则发布treeVersion=1、唯一根节点及其自关联路径，使正式查看/管理可用。复用已有树版本/路径表，不增加字段，不将项目组变成父子关系；投影或事务事件失败须整块回滚主档与绑定。仅新建时初始化，既有根及重放不重建其有效投影，不实例化计划、阶段或任务。

ProjectScopeMigrationContextApi.Query及ProjectContractRelationMigrationApi.Entry追加可空sourceConnectionId；已有调用不传时保持既有来源校验，通用INT任务传实际连接ID后必须经上述绑定API，不回退猜配。PROJ仍核验项目管理权、租户、删除、项目版本、正式类型/编码和办事处。COM原26字段原样保留，合同关系使用绑定的正式项目编码，允许已裁决尾号调整且不把原码覆盖成目标码。整个绑定查询和业务写入共享迁移事务；来源身份不作为业务数据承接点。

2026-10-13合同关系上下文补充：PROJ提供公开内部`ProjectMigrationIdentityContextApi.resolve/lockAndRevalidate`，输入场景Query为tenantId、actorId、projectId、expectedProjectVersion、sourceProjectKey及可空sourceConnectionId，返回正式项目ID、当前版本、编码、类型及公司编码/名称。PROJ重验项目管理权、租户、删除、当前版本和准确来源绑定；旧快照调用与实际连接绑定调用分别沿既有Owner校验，不能相互回退。lockAndRevalidate必须参与调用方事务并锁住项目。COM合同关系Owner复用此身份上下文，并自行核验合同身份、项目公司和操作者公司授权；关系的正式字段不依赖实施办事处快照，因此不要求原办事处非空。原`ProjectScopeMigrationContextApi`继续为实施范围提供完整办事处目录/版本及相同守卫，其接口与消费者不变。两个上下文均不创建项目、授予范围、改生命周期或访问其他模块业务表。

2026-10-13项目产品整表任务复用`GET /api/v1/pms/integration/templates/dppms-project-product-scopes`及既有`DppmsProjectProductScopeApi.preview/apply`。初始SQL完整读取pm_project_product_line的11列并关联原pm_project类型、编码，复制到可编辑任务SQL；projectTypes=10，projectIds空值为整表，10项目验证只设置参数。缺项目原行保留并阻断。INT使用正式迁入身份及当前项目版本，COM复核正式办事处、合同订单行、正数量和历史保护；数量按原项目数量优先、正式订单数量补缺。已裁决allocation_confirmed=false，既有正常分配容量、版本及当前槽位约束保持，无新增表列。

2026-10-13真实产品清单任务使用`GET /api/v1/pms/integration/templates/dppms-project-real-products`生成可编辑SQL。原pm_project_product_line_real全部12列与上游pm_project_real_product_line_from_sms全部10列按既定九项业务值精确关联，NULL与空串分别保留；上游不是唯一匹配时阻断。公开内部`ProjectRealProductMigrationApi.preview/apply(Command)`仅接入现有`ProjectRealProductImportService`的转换、正式表写入及完整字段重放保护，Command携带`ProjectMigrationIdentityContextApi.Query`和原/上游字段、目标ID、正式前值。PROJ复核管理权限、租户、来源项目绑定及版本；INT保存技术绑定和运行回执，业务值写入现有19列正式实体，不使用迁移快照承接。首次迁入采用真实迁入操作者和时间，零/负数量按已裁决清单事实保留，不触发分配、交付或退货。ONCE任务禁止清表、重置绑定或接管未绑定现存业务行；已有绑定只在原字段及全部正式前值未变时重放。

2026-10-13租赁与配置复用`GET /api/v1/pms/integration/templates/dppms-project-associated-products`及公开内部`ProjectAssociatedProductMigrationApi.preview/apply(Command)`。Command由INT沿同一项目身份上下文构造，含原项目编码、既有领域Kind、完整11原字段及来源绑定/正式前值；PROJ分别委托既有租赁、配置Owner，转换和入库均不另造一套实现。初始SQL按旧DAO的原项目编码首段关联，同类型同码取最大projectId，projectTypes/projectIds为任务参数；旧页面的最大执行单筛选不用于整表迁移，全部执行单及重复原行保留。租赁分类/类型/编码/型号/名称及租赁月数沿已裁决正式列，配置原BOM路径/父码/层级保留，查询派生路径继续计算。均无新增物理表列或历史展示块。

两张来源没有主键，任务Source加性提供可空`sourceKeyColumns`，指定全部原字段及准确来源项目身份。既有一次性采集器的排序UTF-8 JSON、SHA-256和出现次序生成技术键，NULL/空串及不同项目分别保持；不写原库、不制造业务ID、不去重。该配置仅允许ONCE/SNAPSHOT整批读取，禁止增量、分页/流式恢复和源主键覆盖；普通有主键任务保持原逻辑。来源缺列、重复列或覆盖原列均阻断。技术键和回执不作为业务承接点；业务字段仍全部由原Owner写入正式实体。

2026-10-13展示规则按需求方补充裁决：已完整同步的业务值直接读取正式附表，不反查原始主档或按业务编码解析主档唯一键作为展示前提。租赁、配置页面沿旧DAO按商务项目编码分组取最大执行单号；使用正式表内的相同字段进行筛选，并按现有项目ID/租户/授权限定。配置排序保留项目编码、分组和BOM路径，三个路径派生值沿原规则计算。旧执行单及重复行完整留在正式表，展示筛选不改变迁移覆盖范围、来源绑定或原值。真实产品仍按正式项目关联直接分页，无产品目录解析。

Command复用既有迁入对象并补实际sourceConnectionId，旧调用保留。Baseline的可空formalFieldsJson仅作为已核验完整正式前值输入，不是业务承接；来自旧成功迁入的44个实体字段，必须完整含NULL，并逐项与当前范围核对。只有完整前值相同时保留原范围的项目/办事处快照，项目后续合法经理变更不改写旧范围；原范围自身的任意后续变化仍阻断。正常项目授权和当前版本仍经PROJ Owner核验。日期前值支持既有毫秒时间戳及无损ISO日期，技术绑定复用INT原11列和正式Owner回执，事务内共同提交；不得从当前业务表临时生成前值接管记录。

普通`GET /api/v1/pms/delivery-scopes`原查询/权限/历史筛选保持，响应追加contractNo、orderQuantity、unitCode；产品名称复用productDesc。COM只对已授权范围页的ID集合，经同租户正式范围→订单行→订单头关系读取，不增加业务表或物理列，不访问其他模块表；空ID集合返回空，缺失正式订单不隐藏范围自身记录，单位可空。普通实施范围表格直接展示这些字段，不另建迁移历史块。Controller和查询服务之间使用PageQuery对象，分页和可见项目仍由服务端确定。


2026-10-13当前实现未提供新增项目及关联的受控撤销API；该业务决策仍待需求方回复，不以设计文本推定已批准。6570个范围异常项目不接管、不新增关联、不执行撤销。来源创建回执和原审计保留，原十项目验证范围保持。

2026-10-13剩余业务承接：INT通过`ContractReceivableMigrationApi`向COM写既有52列回款信息，原39列完整映射、空合同号按裁决过滤；通过`ProjectFactsMigrationApi`向PROJ写既有20列相关方及已批准的四项正式状态扩展。PROJ的`ProjectEntityFieldProvider`复用正常项目读写授权、租户和版本锁，不生成内容修订或流程完成。显式审计写入使用场景命令，防止实体自动填充覆盖原NULL。整表SQL、验证参数、完整字段及实际运行见[承接清单](../generated/2026-10-13-dppms-migration-mapping/remaining-carry-implementation.md)。

INT旧01绑定清理只读COM公开`OrderLineMigrationPresenceApi`确认目标不存在，再删除自身已裁决的绑定；存在正式目标、原任务正在运行、租户/连接不符或空筛选均不能扩大删除范围。旧运行回执和有效绑定保持。

2026-10-16账号基础任务的合并预览：SYSTEM角色、用户角色关系和公司部门范围迁入API追加带`SystemAccountMigrationPreview`的预览入口，依次复用同轮账号、角色、关系Owner的成功预览结果。上下文仅在内存中属于同一tenant/owner；新对象使用负数临时引用，既有正数身份仍经数据库校验，不能用预览上下文替换现存身份。公司、部门、历史前值、角色状态及正式授权规则不变。正式`apply`不接收此上下文，必须在同一事务中按账号→角色→关系→范围依次校验并写入真实对象；临时引用不得成为正式ID或来源绑定。旧独立任务入口保持原行为；本轮任一阶段冲突阻断后续阶段，预览不调用写入后回滚。

P01追加`t_company`原ID/原公司码的只读来源别名组件。该引用沿P04既有`DPPMS_COMPANY_CODE`转换精确关联SYSTEM正式公司，仅登记INT来源绑定；P01的EHR公司主档仍保持其既有TRIM映射，不套用该引用转换。缺失、重复或已变化的正式公司不能新增临时公司或改绑。P04通过INT本地别名及SYSTEM当前组织目录批量重验公司/部门类型、ID和编码；未完成P01别名时阻断，不在P04重读原公司/部门。INT来源绑定查询对旧账号、角色、关系和范围适配器增加P04合并任务别名解析，保留原查询结果并按对象类型隔离，租户、连接、来源系统、删除和空集合限制不变；历史来源回执不覆盖。


### 2026-10-16 P04 独立直写任务入口

`GET /api/v1/pms/integration/templates/dppms-account-foundation-direct?connectionId=...` 沿用配置权限与连接校验，返回 `DPPMS_ACCOUNT_FOUNDATION_DIRECT` 独立任务类型；原 P04 模板和入口保留。仅返回模板，不保存或执行。新类型复用已有账号、角色、关系及范围的来源解释和裁决，使用专用 MyBatis XML 直接持久化；不调用原迁移 Owner 的写入接口。原公开组织查询与凭据撤销能力继续按原契约使用。正式执行仍由统一运行服务控制权限、租户、配置版本、事务、绑定和证据。

`GET /api/v1/pms/integration/templates/dppms-dictionaries-direct?connectionId=...` 沿用配置权限与连接校验，仅返回 `DPPMS_DICTIONARIES_DIRECT` 独立模板。原字典类型/字典项和归并入口保留。直写任务复用既有审计人员精确关联、缺失分组、空记录114排除及不接管全局字典的裁决，通过专用 XML 顺序承接类型与字典项；预览只在当前调用内使用已验证的拟建类型，不写正式字典。正式写入要求活动的 REPEATABLE_READ 事务，类型、项及统一运行回执失败整体回滚，仍要求原迁移执行和系统字典创建权限。

新模板各来源以原自增主键读取，任职/账号及关系在本轮完整快照内核对；共享来源只读一次供相关对象使用，不按业务码或修改时间排序。保持 ONCE/UPSERT/RETAIN、不清表、不重置绑定、全量核验及原容量限制。预览不写业务表；账号、角色、角色关系、公司部门范围及运行回执同事务提交，失败整体回滚。新旧任务别名保持独立；既有消费者可按租户、连接、对象类型和原键查询新直写绑定，歧义仍拒绝。P01 主管仅使用已核实 EHR 账号回执，不因新增类型扩大人员关联范围。

### 2026-10-16 P15 本地合同派生与发货收尾

`GET /api/v1/pms/integration/templates/dppms-delivery?connectionId=...` 要求原配置权限并校验连接，仅返回已批准的五对象配置，不保存或执行任务。合同经 `DppmsLocalContractSourceApi` 读取本地权威订单；四类远端原始发货事实沿指定来源连接读取。原模板资源、原任务定义和历史运行保留。运行执行人仅由服务端运行创建人传递，既不保存进配置，也不写回冻结运行快照。

`DppmsContractCompletionApi.complete` 是 COM 内部受控收尾接口，要求既有迁移执行权限、真实执行人、租户一致和现有事务。INT 只提供已限定的本轮合同、发货合同引用、包裹 ID，以及完整可用的本地发货/回款输入 ID；COM 不读取 INT 绑定表，INT 不直接更新 COM 业务表。SYSTEM 正式组织目录经既有公开 API 一次读取，COM 在同一事务连接创建本轮组织和唯一合同临时目录，批量回填引用后对发货、回款各汇总一次。字段优先级、逐字段最新非空排序、唯一合同限定及不清空未匹配引用的规则保持已批准 SQL；临时表在 `finally` 释放。

P15 全部来源完成且无未承接或冲突行之后才允许收尾；P24 输入必须来自同租户、同连接、当前配置的完整成功根运行，运行中、失败、部分读取和合同范围过滤均不能作为完整输入。既有符合条件的本地 P24 快照直接复用，不为消费而重跑生产者。新事件补齐本地精确父链后冻结来源投影；旧不可变事件沿既存父快照验证，同时逐项校验全部原始字段，不能用当前父记录改写历史。`readProjection` 只允许三类 COM 对象及最多 500 个正式 ID；收尾后 INT 仅刷新本轮最新绑定的 `_postCompletion` 投影，原始来源、旧运行和审计证据不改写。正式变更由既有 Asset Owner 刷新设备组织投影；收尾或投影失败不标记运行成功。

### 2026-10-16 P01 公司部门直写任务入口

`GET /api/v1/pms/integration/templates/dppms-organization-direct?connectionId=...` 沿用 `pms:integration:configure` 权限，仅返回独立的 `DPPMS_ORGANIZATION_DIRECT` 配置，不保存任务、不启动同步。用户已批准此 P01 入口直接通过 INT 的专用 Mapper 写 `system_company/system_dept`，原 `DPPMS_ORGANIZATION` 与已验证模板不变。来源主键、映射裁决和原来源别名保持；读取采用 ONCE/SNAPSHOT，禁止清表、重置绑定和拆树分块提交。任务保存、预览、执行及运行审计沿用现有接口和权限；目标写入、绑定及证据同事务提交，现有正式定义不覆盖，仅允许在本任务创建的部门上续补空主管。其他迁移任务不因本入口改变写入边界。

P01 双线入口补充：`GET /api/v1/pms/integration/templates/dppms-organization` 原样返回既有 P01 归并模板（`DPPMS_ORGANIZATION`），与直写入口 `dppms-organization-direct`（`DPPMS_ORGANIZATION_DIRECT`）并列，均要求原配置权限并校验来源连接。既有 `/templates/ehr`、`/templates/dppms-departments`、`/templates/dppms-ehr-departments` 保留。前端“任务类型”分别加载对应模板；新增直写任务沿用现有独立任务身份规则，不自动替换原任务或转换历史绑定。


### P30 设备基础与显式项目指派入口

`GET /api/v1/pms/integration/templates/dppms-device-bootstrap` 只返回基础承接定义，要求配置权限，不保存配置。普通同步仅由 Asset Owner 承接本地发货及正式产品目录，不自动赋项目。

`POST /api/v1/pms/integration/device-assignments/preview` 和 `/apply` 是同一 P30 任务的显式一次性指派阶段，要求执行权限及实际登录用户。请求包括 taskId、expectedVersion、非空且至多500个唯一 deviceSns、明确 effectiveAt；apply 还须提交 preview 返回的 expectedDigest。该范围和时间不进入任务默认配置。

INT 锁定 P30 与同连接唯一 P33 任务，要求 P33 当前配置最近正式根批次完整成功且没有 ISSUE/CONFLICT/SKIPPED，核对冻结来源定义，分页读取 INT 自有安装绑定。按 P16 当前发布范围解析原项目身份，经 Project Owner 检查及锁定、Engineering Owner 核验不可变安装历史，再调用既有 Asset 指派 Owner；不回读 pm_project_shipment，不跨模块查表。缺建档、缺安装历史、多项目、失效身份或摘要变化均拒绝，整组正式指派在同一事务内完成。原安装合同逐条接受最新发货与既有归属校验，不按后写顺序抢占归属。

preview 不写业务，apply 重算并比较涵盖来源批次、身份发布、原历史、当前设备版本和生效时间的摘要；新旧归属历史及正式命令幂等审计由既有 Asset/平台 Owner 保存。该入口独立于普通同步 SUCCESS，调用方不能把基础承接成功理解为项目指派完成。


### P31 成员与最终客户联系人历史 Owner

`GET /api/v1/pms/integration/templates/dppms-member-history` 要求配置权限，只返回候选定义。适配器 `DPPMS_MEMBER_HISTORY` 只读一次 `pm_project_member`，本地按角色互斥路由 `MEMBER_HISTORY` 和 `CONTACT_HISTORY`，两者保持独立目标及来源映射。项目范围和别名由 P16 当前完整发布提供；保留原 projectType 为 originalProjectType，先核验原类型、原项目编码及正式项目身份，不能用补齐值掩盖原类型冲突。

PROJ `ProjectMemberSourceHistoryApi` 接收 tenant、actor、formal projectId 与最多200条原15字段及历史目标基线，必须加入INT事务。默认历史承接不授当前权限、不选择主责、不关闭现有区间、不更新项目经理。原10/20/30复用正式角色定义、精确历史账号及既有区间规则；无账号历史允许 userId=NULL，原审计账号仍须精确解析，NULL/空串保持。已绑定历史只核对全部正式字段，人工修改或删除拒绝覆盖。

role60 调用既有 CUS 最终客户联系人 Owner，完整校验原联系人、项目最终客户关系和审计，保存 CUS 正式联系事实，不创建账号角色或客户主档。CUS 增加 `validateContacts(Command)`，与 `carry` 共用完整来源验证，预览不插入；重放由原 `verify` 核对正式指纹。当前授权或主责变更继续走既有正式成员加入/经理指派，或原逐项目、带明确批准及前值检查的专用迁入命令；未授权历史不原地转为授权，原试点裁决不放入日常任务配置。


### P35 软件批次与版本历史统一入口

`POST /api/v1/pms/integration/templates/dppms-software-history` 要求配置权限，接收 connectionId 与已发布 extensionDefinitionId，只返回定义，不创建扩展定义或保存任务；缺少定义时可生成未补齐前置的离线候选，运行必须拒绝。AST仍校验定义属于本租户、AST/SoftwareVersionRecord及准确的八个原备份/变化属性。

适配器 `DPPMS_SOFTWARE_HISTORY` 一份父批次查询归集其原logId子版本，两类对象共享来源读取及P16范围，不重读pm_project。根批次范围由P16完整发布前置筛选，父子原projectId必须一致；读取器补齐的原项目编码与类型沿本组传给内层原字段，INT再次向当前P16及Project Owner核验身份。父9列、子28列分别保留不可变原始快照；空父子、跨项目和原字段变化不凭旧ID兜底。

`DppmsAssetSourceHistoryApi.previewSourceHistory` 与原正式组合命令共用Owner规则，返回专用临时Preview，负数只表示本轮新对象，不进入正式ExternalTargetMapping或绑定。预览不INSERT父子、不写扩展值、不发布定义；旧正式及纠正调用保持原正数目标契约。八属性校验复用SoftwareVersionLegacyExtensionService，已有不可变扩展必须一致。

正式命令在本次父子组事务中先建立或核对父批次，再按原logId解析本次正式父ID和承接版本。历史记录不改变当前设备软件投影，不发送升级完成事件；原审计、全部正式字段、扩展值与既有删除/授权保护继续由AST Owner校验。


### P37 受控许可读取与统一Owner入口

`POST /api/v1/pms/integration/templates/dppms-secure-license` 要求配置权限、关闭请求体日志，只返回一次性候选定义。请求包括connectionId、现有许可Owner所需ownerProjectId和明确批准的许可原ID及七项非敏感冻结字段；不接收licenseCode、密钥或Secret。Owner项目上下文用于现有授权及合同解析，不加到许可来源SQL或P16项目范围，也不作为许可事实归属。

新增专用 `SECURE_LICENSE` 只允许 `DPPMS_SECURE_LICENSE / ONCE / SNAPSHOT / RETAIN`，单次至多200条，禁止清空、重置、通用SQL、任意字段映射及空范围退化全量。批准原ID与冻结字段须一致，读取前核对执行者、精确八列来源Schema；在既有同一只读一致性事务中按approvedLicenseIds参数化读取，不按项目查询来源。缺少明确范围、Owner上下文、真实Secret配置或Schema不一致必须阻断。

专用读取器只在受控内存接触许可码，随后按原Python捕获及现有Asset密码器的固定初次捕获命名空间 `DPPMS-LICENSE|tenant|1|sourceId` 生成AES-256-GCM密文及HMAC；可变缓冲区在成功或失败后清零。密钥仅使用既有 `pms.license-history` Secret配置，不进入任务。通用快照、PLT证据和映射接收到的只有七项非敏感字段及密文/keyId/MAC；错误不保留敏感原因。运行结果与绑定不展示密文或明文，记录不可变原字段与MAC。重放比较原非敏感事实和MAC，随机nonce变化不当作许可业务变化。

INT批量调用现有Commerce精确合同解析及Asset许可Owner，保留项目授权、来源幂等、全字段和密文认证及删除保护。ownerProjectId不是新增来源过滤门槛。未唯一匹配的设备/合同沿原Owner明确保存pendingAssociations，运行成功只表示历史承接，不能表述关联已完成；不猜配目标ID。许可Owner及INT绑定写入在同一事务内，失败不发布SUCCESS。临时执行actor不写入保存定义或冻结配置。

### 2026-10-16 P10/P12 独立直写模板入口

`GET /api/v1/pms/integration/templates/crm-customers-direct?connectionId=...` 返回独立 `CRM_CUSTOMER_DIRECT`；`GET /api/v1/pms/integration/templates/dppms-products-direct?connectionId=...` 返回独立 `DPPMS_PRODUCT_CATALOG_DIRECT`。两者要求 `pms:integration:configure` 并校验连接，只返回模板，不保存或执行。原CRM客户和DPPMS产品模板保留。P10通过私有专用XML承接CRM客户及来源映射，保留源版本和平台字段保护；P12仅补齐缺失正式产品，不覆盖既有CRM目录。正式写入仍经过原任务执行权限、租户、活动运行/配置版本及实际REPEATABLE_READ事务，正式变更与统一绑定回执保持原框架原子性。

### 2026-10-16 P13 独立执行单直写模板入口

`GET /api/v1/pms/integration/templates/dppms-execution-orders-direct?connectionId=...` 要求原配置权限并校验连接，返回独立 `DPPMS_CRM_EXECUTION_ORDER_DIRECT`，不保存或执行。来源沿用已审核P13整表最新修订和有效项目范围，根游标使用原id。私有INT实现和专用XML承接原执行单头字段，原COM入口保持。组织引用使用公开组织查询；原组织缺失时保留来源码及已批准九个部门别名转换。正式写入要求原执行权限、有效租户/任务/运行/配置版本及REPEATABLE_READ事务，保护已有状态、AF证据、主项目及非本来源字段。

### 2026-10-16 P24 独立合同回款信息直写模板入口

`GET /api/v1/pms/integration/templates/dppms-receivables-direct?connectionId=...` 要求原配置权限并校验连接，返回独立 `DPPMS_CONTRACT_RECEIVABLE_DIRECT`，不保存或执行。源SQL沿用已审核有效项目及合同范围，读取按原id；39原字段和52正式字段并发保护不变。INT私有持久化及XML承接原已批准规则，原COM入口保持。正式写入保留执行及合同查询权限、原审计关联、空合同号过滤、原基线保护和当前主数据关联；实际事务中失败回滚，不生成收款或验收完成动作。

### 2026-10-16 P25 独立设备维保延期直写模板入口

`GET /api/v1/pms/integration/templates/dppms-warranty-renewals-direct?connectionId=...` 沿用配置权限和连接校验，返回独立 `DPPMS_WARRANTY_RENEWAL_DIRECT`，不保存或执行。INT私有类及XML承接既有十字段、参考年数转月数、实际日期保持、原ID与SN组合身份、完整基线和乐观版本保护；原AST入口保留。fb_service无主键或自增列，已审核的组合身份读取保持。正式写入仍要求迁移及设备资料权限、有效运行和实际REPEATABLE_READ事务，写入失败整批回滚，不改变设备当前业务状态。

### P16 项目主档直写配置入口（2026-10-16）

`GET /api/v1/pms/integration/templates/dppms-projects-direct?connectionId=...` 沿用配置权限及连接校验，返回独立 `DPPMS_PROJECT_MASTER_DIRECT`，不保存或执行。源SQL与已审核原37列、按原projectId读取、规范主档及全来源身份聚合保持。INT私有类和XML承接原项目创建、基线保护及裁决恢复规则；创建根项目时在同一事务写入首版树版本、自身路径并追加平台规则重算事件，任一失败整体回滚。历史状态仅按已批准的迁移初始化规则承接，不生成当前任务、审批或分配完成事实。既有项目继续通过公开项目范围契约校验管理权限；恢复复用原ID并核对撤回回执、全字段指纹、原版本及业务引用。原PROJ入口与模板保留。来源绑定查询允许这一独立直写适配器的两种项目对象，租户、来源连接、原键及冲突校验不变。

### P18 项目合同关系直写配置入口（2026-10-16）

`GET /api/v1/pms/integration/templates/dppms-project-contract-relations-direct?connectionId=...` 沿用配置权限和连接校验，返回独立 `DPPMS_PROJECT_CONTRACT_RELATION_DIRECT`。源SQL、完整项目桥、重复关系裁决与原组合身份保持；INT私有持久化直接承接关系，17列前值、正式合同公司授权、项目身份版本、历史审计、限定纠正和恢复保护不变。不保存或执行任务，原COM功能保持。

### P19 项目相关方及状态直写配置入口（2026-10-16）

`GET /api/v1/pms/integration/templates/dppms-project-facts-direct?connectionId=...` 沿用配置权限和连接校验，返回独立 `DPPMS_PROJECT_FACTS_DIRECT`。相关方由INT专用XML直写，状态四字段通过原平台扩展契约承接，保留定义、属性版本、完整前值及项目范围保护。原项目相关方和状态Owner入口保持，不发布扩展定义、不生成当前审批或计划完成事实。

### P22 项目产品实施范围直写配置入口（2026-10-16）

`GET /api/v1/pms/integration/templates/dppms-product-scopes-direct?connectionId=...` 沿用配置权限和连接校验，返回独立 `DPPMS_PROJECT_PRODUCT_SCOPE_DIRECT`。INT私有SQL在实际事务中承接原11字段、范围和产品数量明细，保留精确合同/订单行、数量及原审计基线保护；现有范围未变时仅按原规则补缺失明细。不确认分配、推进额度或制造SN，原COM功能保持。上述三个入口均只返回定义，不保存或执行。

`GET /api/v1/pms/integration/templates/dppms-real-products-direct?connectionId=...` 沿用配置权限和连接校验，仅返回独立 `DPPMS_PROJECT_REAL_PRODUCT_DIRECT` 定义，不保存或执行。任务私有SQL承接真实产品清单，保留原12字段、唯一上游10字段、整数数量、完整前值及已有清单禁止追加未绑定记录规则；原PROJ入口保持。

以下独立模板GET均沿用 `pms:integration:configure` 及连接校验，只返回定义，不保存或执行：

| `/api/v1/pms/integration/templates/` 后缀 | 独立适配器 |
|---|---|
| `dppms-device-base-direct` | `DPPMS_DEVICE_BOOTSTRAP_DIRECT` |
| `dppms-member-history-direct` | `DPPMS_MEMBER_HISTORY_DIRECT` |
| `dppms-installations-direct` | `DPPMS_INSTALLATION_HISTORY_DIRECT` |
| `dppms-factory-history-direct` | `DPPMS_FACTORY_HISTORY_DIRECT` |
| `dppms-software-history-direct` | `DPPMS_SOFTWARE_HISTORY_DIRECT` |
| `dppms-secure-license-direct` | `DPPMS_SECURE_LICENSE_DIRECT` |

`DppmsSoftwareHistoryExtensionApi` 是AST公开的迁移扩展初始化接口，仅接受 `Initialization(tenantId, actorId, recordId, definitionId, values)` 对象；`preview`验证定义与原值，`initialize`经原SoftwareVersionLegacyExtensionService的专用Provider上下文初始化八属性。两者必须加入调用事务，保留原授权、实体版本与定义版本校验，不导入软件主记录、不发布定义、不提供普通可编辑历史入口。INT私有SQL负责主批次及版本，基础扩展存储继续由PLT契约维护。


### 原迁移任务的来源隔离与续保资格读取（2026-10-17）

`DppmsProjectBindingApi.BatchQuery.originalLine()` 显式限定原任务来源绑定；原任务消费者不得以独立直写任务的回执替代本线前置。既有未限定调用保留双线兼容行为，独立任务身份及入口保持。INT内部 `IdentitySources/EhrEmployeeUsers` 使用相同显式限定，并保留租户、连接、来源对象、空集合与冲突保护。

`WarrantyShipmentEligibilityApi.resolve(Query(tenantId, actorId, deviceSns))` 是AST只读批量接口，每次最多500个SN、空集合返回空。AST读取本地DPPMS逐次发货的原 `rma_no` 与包裹来源键，再调用COM `ShipmentSourceFactsApi` 获取精确父链合同类型（Fact新增可空 `contractType`，既有三字段构造继续可用）。仅原RMA号为NULL、空串或字符串null且合同类型0/11的本地事实满足资格；缺发货或父链返回明确issue，不猜配，不做项目授权、指派或续保写入。P25原任务远端只采集fb_service，再按本地资格交回既有续保Owner；已绑定来源资格变化拒绝覆盖。

### 设备配置日志文件归档（2026-10-06，EXE-03 / EQP-02 文件增量）

新增 `/api/v1/pms/device-configuration-log-archives`：POST `/imports` 以 `application/octet-stream` 接收UTF-8日志字节流，query传 `projectId`、UUID `operationId` 和 `fileName`；GET `/imports/{id}` 返回不可变导入结果及关联文件；GET `/latest?projectId` 仅返回当前项目已归档日志的设备及各自最新可用日志；设备没有可用文件时保留最新归档记录并标记不可用，不返回无归档设备；GET `/history?projectId&deviceId` 返回全部设备文件版本；GET `/{fileId}/download` 重验权限后签发原 PLT 下载票据。导入需要 `pms:device-log-archive:import` 与项目 EDIT 范围，查询需要 query 与项目 VIEW 范围，下载另须 download 和文件平台权限。导入返回 `ARCHIVED` 或 `REJECTED` 处理结果；它们不是配置生命周期状态或配置完成事实。拒绝结果不包含原始内容或Secret。入口最多从内存流读取20MiB加一个超限判断字节，拒绝空输入及超限输入，不使用会将原文落盘的multipart临时文件；保留操作审计但关闭请求/响应正文记录。

INT公开 `DeviceOpsLogSplitApi.split(Command)`，DAC实现内容拆分；命令携带当前租户、项目、输入格式与瞬态内容。结果只包含脱敏完整来源、按唯一整机序列号分组的完整日志及脱敏数量。调用方仍须独立授权与校验设备归属。AST公开 `DeviceConfigurationLogArchiveApi.receiveCollection` 承接IMP验证后的配置结果；PLT公开 `DeviceLogFileApi.readCollection/store` 重验源任务、结果、当前IMP Owner和发起人，复用原PLT文件校验/扫描/存储/引用与统一材料。后台接入使用原任务显式actor，不伪造登录主体。Infra以新的内部 `FileStorageContentApi.read` 读取已登记的receipt内容，核对当前租户、长度和摘要；PLT必须先按已授权FileVersion定位该receipt。此API不创建HTTP入口，不改变上游FileApi，不允许业务模块直接调用Infra Service或读取其表。

DAC新增 POST `/api/v1/npdms/log-splits`，只接受 `TERMINAL_TEXT` 或已验签来源的 `DAC_CALLBACK` 内容，重验JWT execute scope、namespace和project claim；不下发设备命令。输入上限复用采集日志20MiB限制。422仅返回固定失败code及可选内容块序号，不回显日志。配置采集成功不等于此归档成功，归档成功不等于业务配置完成。

采集回传沿用原 `pms:imp-configuration:update` 消费权限及当前项目EDIT范围，PLT通过原配置来源适配器的CONSUME入口锁定重验；该入口不向主体授予手工日志导入权限。手工导入还须 `pms:file:upload`，在调用DAC前重验；下载还须 `pms:file:download`。新的归档权限由已有授权管理显式配置，迁移只初始化菜单，不自动给业务角色授权。归档时间沿用平台EpochMillis，标识沿用Long安全序列化。

### 日志解析记录与在网版本（2026-10-07）

按用户明确确认，在网版本由最新设备日志解析结果生成，来源为 `DEVICE_LOG`。AST 通过 `DeviceLogParsedFactApi.record` 在项目 EDIT 范围内按租户、序列号及当前项目关联设备，保存不可变解析事实，并在同一事务中更新设备软件版本及在网版本投影；不以 ITR 事件时间判断日志新旧。归档日志按设备版本序号排序，其他上传/采集日志按原始来源接收时间排序，重新解析旧日志不改变其来源时间。未识别软件版本、序列号不匹配及历史日志分别展示结果，不伪装成版本更新成功。

GET `/api/v1/pms/device-configuration-log-archives/{fileId}/parse` 返回每设备归档文件的解析状态、关联结果和版本更新状态；GET `/pms/asset/devices/{id}/log-parses` 返回授权设备的结构化解析记录。归档事务提交后创建独立解析任务，后台在原租户和原操作人授权范围内查询结果并投影；不重新执行设备命令。解析 SN 与不可变归档 SN 不一致、文件不可用或文件归属不一致时禁止更新。原历史来源与 ITR 事件记录不被改写。没有正式语义字段时不猜测 CONP 派生属性或 BOOT 版本。

已批准补充（2026-10-16）：GET `/{fileId}/parse-result` 在当前租户、项目 VIEW 范围及原文件有效性校验后，优先返回 AST 本地不可变完整解析快照，不依赖 DAC 实时在线；状态查询对已完成且已投影的本地结果同样不回查 DAC。首次读取原成功任务或新任务成功关联时，将 DAC 原完整脱敏结果补存到 `ast_device_log_parse_snapshot`，明确保存原任务、解析版本和文件来源绑定，仅插入，不覆盖。补存只查询原任务，不提交解析、不连接设备、不回写历史版本；未补存且 DAC 不可用时如实提示待恢复补存。原租户、范围、文件归属/隔离及设备 SN 校验继续生效。独立上传和命令采集解析由 PLT 在原解析行保存一次成功响应（含原警告），详情同样先鉴权、后读本地；列表不加载完整结果正文，原来源及版本变化继续拒绝读取。


### 项目安装批量创建（2026-10-08用户确认）

`POST /api/v1/pms/implementation/installation-records/batch`：接收 `projectId`、非空且不重复的 `equipmentIds` 及共用安装信息、`installLocation`、可选 `locationMaintenance`，返回按设备顺序排列的安装记录ID。权限为 `pms:imp-installation:create` 且当前项目具有 `PROJECT_EDIT` 完整范围；服务端整体校验设备归属，在一个事务内新增安装记录，地址/站点/任意深度位置链只维护一次，后续记录引用相同地点ID与版本。任一步失败整批回滚，不改变旧记录、既有单条API或安装状态机。手动地点沿用 `UNRESOLVED` 兼容文本语义。前端只提交一次请求，不循环调用单条新增API。

2026-10-08用户追加确认：项目安装批量保存即安装完成。批量事务创建记录后，服务端衔接现有开始、完成状态动作并执行AST设备位置生效；任一设备的位置校验或生效失败也整批回滚。前端不再提供开始/结束动作，历史记录从设备操作列进入只读抽屉，地址变更通过新增安装记录保留历史；旧单条API及旧记录状态不做追溯改写。

### 2026-10-09 序列号分页批量展示

GET `/pms/asset/devices/page` 增补bootVersion、cpldVersion、warrantyMonths、extendedCount，沿用原设备可见范围；boot/cpld只取DEVICE_LOG来源，续保次数统计全部未删除续保记录，列表分页不逐台读取设备详情。

GET `/api/v1/pms/implementation/installation-records/current-addresses`：projectId及equipmentIds（1～200个正整数）返回当前已完成且effectiveFrom存在、effectiveTo为空的设备安装地址。要求pms:imp-installation:query和项目VIEW范围，租户及项目条件在服务端生效；无匹配为空，不返回历史或其他项目数据。序列号页每个不超过200条的分页只需设备页与当前地址2次业务请求，旧详情、维保和安装历史接口保留。

### 2026-10-15 割接详情式新建与项目已发货设备选择

需求方确认：新建割接任务直接进入详情式新建页，支持先选项目或先输入设备序列号；项目资料自动回填，设备序列号、产品编码、产品型号独立按行展示。仅新建阶段可增删设备，创建后的设备范围保持只读；原创建事务、活动设备唯一性、来源版本及项目嵌入入口的执行上下文校验继续生效。

- CUT 新增只读 `GET /api/v1/pms/cutover-tasks/creation-projects?keyword=...`、`GET .../creation-projects/{projectId}` 和 `GET .../creation-projects/{projectId}/devices?keyword=...&pageNo=...&pageSize=...`。均要求 `pms:cutover-task:create`；项目选项和详情只在当前租户、当前主体 `ACTION_EDIT` 范围内返回。设备分页由 AST 提供，固定限制当前直接归属项目、未删除、非空序列号且 `shipmentTime` 存在，不能借用关联合同扩大到其他项目。
- CUT 新增只读 `POST /api/v1/pms/cutover-tasks/creation-devices/resolve`，输入 `serialNumbers`及可选`projectId`，复用原上下文解析与授权，返回项目候选及设备产品资料。已选项目时，服务端按当前主体重新确认该项目PROJECT_EDIT完整范围后，复用既有DeviceScopeFactApi按本次SN集合读取并校验项目/状态，不展开全局项目发现；无projectId仍保留原独立向导发现及多候选选择。该端点使用独立场景请求解码，旧`actions/resolve-create-context`只允许serialNumbers的精确字段契约保持；无发货事实的设备不能成为本选择器的可选项。唯一项目候选自动回填，多候选仍显式选择，不能静默丢弃其他项目设备。提交继续使用既有创建接口及其来源水位。
- PROJ 在既有 `ProjectBusinessContextApi` 增加 `presentation(Query)`，返回经项目范围校验的当前项目展示资料及有效成员名称；独立于冻结 `Context`，不改变既有来源相等性、快照或锁定契约。AST 在既有 `ProjectDeviceShipmentApi` 增加 `pageDevices(DeviceQuery)`，对象传参并返回分页结果；租户及当前主体必须有效，Owner 再校验项目可见范围。
- 查询不创建任务、不修改项目或设备归属。产品编码、产品型号从 AST 读取，项目名称、经理、销售、行业及级别从 PROJ 读取，客户服务等级沿用 CUS 正式事实；缺失信息不猜测。旧接口、旧新建向导原件及已创建任务历史保留。

### 2026-10-15 新建割接与巡检任务自动命名

需求方确认：两个新建界面不显示任务名称输入项。CUT手工新建由创建事务在锁定项目并生成实际taskNo后写入`项目名称-割接-taskNo`；INS由创建事务写入`项目名称-巡检-taskCode`，不再使用计划日期或客户端名称。名称使用授权Owner项目事实，幂等重放返回原任务及原单号，不重新命名既有对象。

- 两类手工创建请求不再要求taskName；旧字段如仍传入也不替代服务端生成。CUT项目操作入口未提供来源名称时同样生成；已明确传入名称的外部来源保留原契约，其他来源准入、授权及事实重验不变。
- 保留单号现有`CUT-...`/`INS-...`格式。项目名最长255字符，业务名称2字符、两个分隔符及单号最长23字符，名称存储容量为282字符。前向迁移仅扩容CUT/SRV主表及AST/CUS后续结果/历史投影的名称列，不更新既有名称或来源证据。INS准备保存及CUS历史接收校验同步支持新容量。

### 2026-10-15 项目选择资料统一能力

需求方确认：授权项目搜索、项目背景信息及项目已发货设备查询属于PROJ公共只读能力，CUT、INS复用同一套接口与前端查询逻辑，不以消费模块权限或创建资格限制公共候选。

- `GET /api/v1/pms/projects/options?keyword=...`：当前登录主体在当前租户的项目VIEW范围内搜索名称/编号，最多100项；无可见项目返回空，不扩大范围。keyword最多128字符。
- `GET /api/v1/pms/projects/{projectId}/context`：VIEW授权后返回`{project, customerServiceLevel}`。project复用`ProjectBusinessContextApi.Presentation`，成员名称仅取当前有效事实；客户服务等级来自CUS正式API，未配置或提供方不可用返回null，不伪造等级或版本。显示层沿用既定默认等级文案，不能当作已配置事实或冻结快照。
- `GET /api/v1/pms/projects/{projectId}/devices?keyword=...&pageNo=1&pageSize=20`：VIEW授权后通过AST `ProjectDeviceShipmentApi.pageDevices`取得`{list,total}`，每行含deviceId、serialNumber、productCode、productModel。pageNo>=1，pageSize为1～100，keyword最多128字符；只含当前直接归属项目、未删除、非空SN且有shipmentTime的设备，不扩大到关联合同或历史关系。
- 三个入口均要求有效登录；tenantId/actorId由服务端当前上下文取得并校验，不接受客户端指定。项目列表及选中项目授权仍由PROJ负责，发货事实和设备明细由AST负责，禁止跨模块直读表。
- CUT、INS新建页面直接消费以上公共API和共享项目卡片，不额外要求`pms:project:query`。旧业务候选入口保留原契约，新页面不再调用。CUT的序列号跨项目解析、创建资格、EDIT授权、冻结来源及提交重验，INS的创建权限、设备锁定/归属/发货/产品类型重验均留在各自业务服务，不因候选可见而放宽。
- 本次只读查询不新增数据表、种子、状态机或历史写入，也不改写既有任务、方案及审批快照。

### 2026-10-09 设备当前安装地址投影统一

设备当前安装地址统一由ast_device.location_snapshot.fallbackLocation承载。AST在原EquipmentLocationEffectiveCommand生效事务中按IMP传入locationText补齐当前快照，保留其他结构化内容；手工地址只生成fallbackLocation，不臆造站点引用。位置历史仍保存命令的原始快照，安装记录及已有历史不改写。

设备分页沿用locationSnapshot，序列号详情直接读取fallbackLocation，不调用地址补齐接口。V420曾增加独立文本列；V421按当前设备指针、租户、设备、项目、安装来源及有效时间严格匹配当前已完成安装，补齐当前快照后移除该冗余列。已执行迁移历史不修改。批量地址查询接口保留兼容，序列号当前页为单请求。
### 2026-10-09 阶段状态日期展示

现有 `GET /api/v1/pms/projects/{id}/instances` 的阶段视图增补 `id` 和 `acceptanceTime`，分别来自已有阶段实例主键、计划验收时间；原 `planEndTime` 保留。沿用原项目VIEW范围与日期序列化，不从建议日期或项目工勘日期推断验收截止。阶段状态页通过实例ID关联项目查询返回的超期事实，显示两类独立标识；计划或任务更新后沿用原刷新链读取最新实例与事实。

2026-10-09 FB-18批准差量：`POST /api/v1/pms/configuration-log-imports` 接收项目ID、幂等operationId、文件名和二进制日志（沿用AST 20 MiB限制），要求配置创建、日志导入、文件上传及项目EDIT范围。IMP通过AST `DeviceConfigurationLogImportApi.importLog`接收不可变归档文件ID、设备ID、SN及可用性，逐设备生成待调试记录；上传不完成调试。`validateFile`只校验当前身份、项目设备归属和引用可用性，不返回日志内容。重复导入复用原配置记录。两模块在原本地事务内失败回滚，禁止IMP直接读AST表。

### 2026-10-09 施工计划审批成果自动关联

施工计划审批生效继续沿用原BPM回调、输入校验及生效事务；在原changed通道登记PLN/STAGE_PLAN/CONSTRUCTION_PLAN_APPROVED成果。成果身份为不可变批准批次ID和原生效时间，仅最新生效且当前工期修订一致的批次为CURRENT，旧批次不得充当当前证据。跨租户、项目及精确身份不符拒绝，未批准或来源不完整不伪造成果。

沿用PROJECT有效冻结计划中的automaticSources；按需求方追加确认，若冻结任务明确绑定PLN/STAGE_PLAN，并在完成规则中引用交付件，且该交付件属于同阶段同任务并允许BUSINESS_RESULT，也作为施工计划成果的自动关联目标。不按名称、阶段编号或固定交付件编码猜测，不改写冻结模板。原Outbox持久化投递至成果归集服务，按事件和要求幂等登记、提交；关联落库才算完成。新批准版本通过原提交置换保留旧材料和提交历史，不新增审批或直接写项目生命周期状态。

### 2026-10-09 FB-03 发货状态筛选

`GET /api/v1/pms/projects` 增加可选布尔参数 `hasShippedDevices`：不填为全部，`true` 为存在已发货序列号，`false` 为不存在。项目含已发货与未发货设备时只属于 `true`；无设备项目属于 `false`。与现有客户、项目状态、超期、成员及设备等条件取交集，不改变分页及权限范围。

PROJ 先解析当前用户可见项目，经过成员条件收窄，再调用 AST `ProjectDeviceShipmentApi.resolveShippedProjectIds(Query)`；Query 包含当前租户和候选项目集合。AST 只返回本租户当前主档归属在候选集合内、未删除、非空序列号且存在发货时间的项目ID；无候选不查询。身份或租户不符、缺失候选集合均拒绝，不能按空结果减集扩大“未发货”范围。AST 负责设备事实查询，PROJ 负责项目范围；不跨模块访问业务表。既有项目状态同时作为实施状态，流程状态当前不新增筛选。

### 2026-10-09 FB-17 现有安装批量保存自动元数据

`POST /api/v1/pms/implementation/installation-records/batch` 的新记录安装人员取当前认证用户、安装时间取本次服务端保存时间。客户端人员/时间字段不影响落库值；未认证在写入或地点生效前拒绝。继续沿用整体设备范围校验、项目EDIT范围、既有状态动作、位置生效和整批回滚。单条旧接口及已存在历史记录不改写；界面人员只读、时间自动，现场照片和安装位置能力保留。

### 2026-10-09 到货签收统一文件引用回显

现有 GET `/pms/imp-arrival/get` 增补 signDocumentArtifactId、signDocumentVersionNo、signDocumentReferenceVersion，取自 PLT FileArtifactApi 对 IMP/ARRIVAL/{id}/ARRIVAL_SIGN_DOCUMENT 的当前授权引用事实，不读取其他模块表、不持久化文件访问地址。项目签收工作台保存时先建立真实待签收记录，以固定 arrival-sign-document 引用键完成统一文件上传，再复用现有 update/sign 命令登记材料和完成签收。项目交付件关联由既定 IMP.ARRIVAL_SIGN_DOCUMENT 自动归集规则承接；附件 URL 历史字段仍只读保留。

### 2026-10-09 施工计划提交时填写调整理由

PUT `/pms/imp-stage-plan/{id}/submit` 增补可选参数 adjustmentReason，项目不存在任何已生效施工计划时允许不填写；已有生效计划时服务端要求本次提交携带非空白理由，并在同一审批发起事务内写入当前可编辑批次 remark。不得以草稿已有 remark 代替本次理由，旧生效版本不改写。GET `/pms/imp-stage-plan/get` 返回 adjustmentReasonRequired，由服务端项目内已生效批次事实判断，避免分页漏掉历史生效计划。项目工作台与独立页面均将理由输入移至提交弹窗，草稿保存不要求填写理由。原授权、服务经理校验、日期/任务校验、版本冲突与审批后生效流程保持。

### 2026-10-20 施工计划已完成项默认日期

`ProjectStagePlanApi.readCompletedDates(CompletedDatesQuery)` 在原租户、项目校验后，只读返回本项目DONE阶段、任务的节点ID及实际开始/结束日期，缺失日期保持空。施工计划详情仅对草稿、已驳回批次附加`completedDates`，审批中、已生效历史不附加当前完成事实；GET不写入。新建草稿、重新倒排的阶段默认优先取实际日期，任务仅补齐空日期。页面加载可编辑草稿时按节点ID补齐空日期，并将仍等于系统建议的阶段默认日期替换为实际日期；不同于建议的已有填写保留，回填标记待保存。已完成任务不再套用阶段默认区间或被自动平移。实际时间、审批历史、既有权限、项目范围、版本及提交日期约束均不变。


### 2026-10-09 FB-05交付范围的订单合同只读回显

`GET /api/v1/pms/delivery-scopes` 增补可为空的`contractNo`，由COM使用当前已授权分页范围ID、当前服务端租户，通过范围→订单行→订单的稳定ID读取。关联表租户和逻辑删除全部生效；空范围失败关闭，来源缺失不补项目主合同。该字段为只读当前订单事实，不修改范围、订单、审批或历史快照；分页、项目VIEW权限和原响应字段保持。工程物料选择消费此字段，产品名称/型号使用既有`productDesc`，不把换货目标产品快照当作原订单产品。

### 2026-10-22 交付件自动归集查看标识

`GET /pms/projects/{id}/instances` 的交付件、`GET /api/v1/pms/project-tasks/{id}/workbench` 的交付件及 `GET /api/v1/pms/projects/{projectId}/deliverables/{id}` 详情增补只读布尔字段 `automaticCollection`。该标识复用当前有效冻结计划的明确自动来源、原生任务归集规则，以及验收报告/满意度的既有交付件绑定，按项目批量生成；不以材料是否已经生成判断，也不修改冻结配置或持久化新业务字段。

按需求方确认，自动归集项（包括同时允许手工来源的项）在清单仅保留查看，详情隐藏上传、关联业务成果和提交材料入口；纯手动交付件保留原提交行为。该字段仅控制界面展示，不授予权限；既有服务端授权、项目范围、并发和历史保护继续生效，原业务归集和历史文件访问契约保持不变。

### 安装记录业务成果自动归集（2026-10-09）

安装成果来源 IMP/INSTALLATION/INSTALLATION_COMPLETED 复用现有成果查询和提交契约。仅项目全部设备均有当前有效、已完成且地址非空的原生安装记录时形成当前成果；以该设备集合最新完成记录的原生编号作为 objectId/resultId，以全部当前记录编号、设备编号与版本生成 observationVersion，formedAt 保留该记录的原生生效时间。空项目、缺记录、未完成、失效或地址缺失不形成成果，旧完成编号不可充当当前证据，不声明历史枚举或全量变更覆盖。

安装完成命令在原事务内追加原生事件与成果日志；部分完成仅记录变化，不声明项目成果形成。匹配有效冻结计划中绑定安装页面、完成规则明确引用且允许 BUSINESS_RESULT 的交付件，或原显式 automaticSources；不修改冻结计划。复用原归集、交付件判定和任务状态机，仍需原任务已提交及其他完成条件满足。


### 2026-10-10 完工证明与满意度成套客户确认（需求方批准）

- `POST /api/v1/pms/satisfaction-tasks/{id}/access-grants` 新创建链接必须提供 `completionCertificateId`；认证操作者须有满意度管理、完工证明查询/审核权限及当前项目管理范围。证明必须是同项目、待客户确认的明确记录；按 `collectionKey` 固定关联，一次完工只对应一条调查链。旧客户链接不扩张授权；未配对历史链继续原契约，建立配对后旧单问卷入口须拒绝单侧提交。
- `GET /api/v1/pms/satisfaction-questionnaires/{token}` 成套链接追加裁剪后的 `completionCertificate` 冻结展示快照。不得读取其他项目证明、临时URL或秘密字段。
- 原 `POST .../{token}/responses` 统一提交两份表单；请求须确认 `confirmCompletionCertificate=true`，一次SIGNATURE文件事实共同绑定本次证明快照及问卷/答卷。服务端复核授权、租户、版本、状态；答卷、成套签字事实、完工客户确认同事务。失败不消费链接，不留下单侧提交。评分及Result生成继续采用原独立可重试事务。
- 原证明审批/归档状态机不新增状态、不追认历史；整改沿用同collectionKey和冻结证明，追加签字/答卷事实，不覆盖首次证明确认。

- `GET /api/v1/pms/satisfaction-tasks/{id}/completion-certificate` 以既有满意度manage和项目范围返回已配对证明快照或null；已配对时重推和整改固定复用原证明，不重新选择其他完工。


### 2026-10-10 完工证明与满意度成套确认工作台

按需求方最终确认，完工证明保留原界面，满意度填写保留DemoV2原表矩阵；满意度管理、查看、打印及两份文件保存参考现场培训。ACC新增 `/api/v1/pms/completion-confirmations`，沿用原完工证明权限与当前项目范围，不新增角色。GET分页接收原分页筛选及 `survey`：调查列表只列已实际关联问卷的证明，空可见范围返回空。GET `/{id}` 返回证明、最新调查和整改历史；问卷、答案、评分和签字按原轮次只读展示。

POST新增及PUT `/{id}` 保存草稿；`satisfactionTemplateId`可选，显式提供时须为本租户已发布模板，两个打印模板ID可选，选择时通过PLT当前已发布修订校验并冻结版式。显式清除打印模板选择恢复内置版式，已外发证明禁止修改。原完工证明页面沿用原保存接口，不要求选择调查；POST `/{id}/actions/issue` 默认查找本租户已发布的 `COMPLETION_SATISFACTION_STANDARD` 原表模板，不按其他模板优先级推断。该命令在同一事务中检查当前ACTIVE项目与授权、创建并冻结一份调查、提交证明、关联两表并生成7天客户链接。重新外发沿用原调查，撤销旧ACTIVE链接；不重复创建调查。首次推送还须沿用满意度manage权限及完工证明audit权限，任何一步失败整体回滚。POST `/{id}/actions/void`仅草稿/已外发允许，撤销ACTIVE链接；DELETE仅草稿允许。历史已确认/已归档事实不改写。

客户沿用匿名业务Grant受控签字文件上传，在同次答卷提交中确认两表。新工作台要求PNG签字回显数据与PLT锁定的签字文件摘要一致；客户端传入图片不能替代平台文件事实。证明确认、答卷、关联签字及两份不可变HTML交付文件在同一事务内保存；失败回滚，重试复用原准备的文件事实。已消费的成套链接只读展示该Grant自身的答案、签字与确认时间，不能再次提交，也不能读取另一Grant的签字。整改新增轮次保留首次完工确认，旧单问卷/代录入口不得单侧提交已成套的调查。

POST `/{id}/documents`接收multipart PDF、kind（COMPLETION/SATISFACTION）、expectedVersion与Idempotency-Key；下载前调用PLT原生生成文件API登记不可变文件及交付件。技术大小上限沿用PLT 50MiB，不增加业务评分或阈值。GET `/{id}/files/{materialId}`在当前证明与项目VIEW授权下生成受控下载票据。文件Owner为ACC/COMPLETION_CONFIRMATION，交付件原类型为COMPLETION_CERTIFICATE，通过purpose区分两份文档及HTML/PDF。打印只读入口不授予生成写权限。


原表冻结13题：10项评分等权平均，满分100、80分及以上达标；购买意愿与两项意见不计分。购买意愿在UI提供“是/否/其他”和其他说明，存储为TEXT答案以复用既有未计分文本契约，不进入计分分母。其余已有问卷的题型、规则和冻结历史不变。原表未发布时推送失败，不创建半份调查。

原完工证明 `PUT /pms/acc-completion-certificate/reject` 保留原audit授权与1→4状态机；已配对记录在同一事务撤销关联ACTIVE链接，状态/版本冲突时不撤销仍有效的授权。客户已经打开页面也不能再初始化签字上传或统一提交。

### 2026-10-10 项目选择性返工并生效修复计划

`POST /api/v1/pms/projects/{projectId}/plan/draft/{draftId}/rework-apply` 将既有选择性返工与项目独立计划生效合并为一个事务，用于已完成任务的业务绑定修复。须同时具备 `pms:project-plan:manage`、`pms:project-plan:rework` 和当前项目编辑范围；租户取服务端上下文。请求头 `Idempotency-Key` 非空且最多128字符；请求体包含完整 `expectedPreview` 和 `rework`（既有返工请求的 planVersionId、expectedProjectVersion、selectedNodeKeys、expectedExecutions、reason；项目ID仅取路径）。

锁定后要求修复预览逐项一致，返工使用同一生效计划和项目版本；仅允许所选节点的 UPDATE 与关联 REEVALUATE，不接纳新增/删除节点或交付件、里程碑、门禁定义变化。返工前预览唯一可暂存的问题是所选节点 binding 的 `STARTED_BINDING_IMMUTABLE`；不豁免其他编译问题。先通过原返工服务建立新轮次，再重新编译、预览并通过原计划生效服务提交，任一步失败整体回滚。必要父阶段仍由原返工规划器确定，须在 expectedExecutions 中显式确认，不扩大其他返工范围。

响应包含 `plan`（生效结果）和 `rework`（新旧轮次关联）。幂等重放不重复返工；相同键不同请求拒绝。原完成轮次、原冻结计划及完成证据保持，当前轮次按修复计划运行；原规则重判事件只能在整个事务提交后投递，不能在返工与修复之间误用旧完成规则。本入口不替代普通计划生效、返工接口，不发布项目模板。

### 2026-10-11 项目联系人完成事实（CUS-04 / PRE_CONTACT）

需求方确认：项目当前至少存在一条启用、未删除、属于项目关联客户且满足现有联系人内容校验的项目联系人记录，即满足“用户联系人获取”完成条件，不要求主联系人标识、再次创建或手工提交。客户通讯录记录未引用到项目时不参与；停用、删除、其他项目或其他客户的记录不参与。此变更只作用于当前及后续执行，不撤销或覆盖已完成历史，阶段准入、退出、门禁及子任务保护继续生效。

`ProjectContactCompletionApi.lockAndInspect(Query(tenantId,projectId,customerId,customerCode))` 为内部无人值守事实查询，CUS拥有查询和有效性判定；PROJ调用方须保留当前项目、任务及执行轮次的锁和授权边界。加入既有事务（MANDATORY），校验服务端租户，只锁定该项目、该客户、启用且未删除的项目联系人记录。项目customerId优先；无ID时只以租户内稳定customerCode解析，不按名称匹配，不写回项目。无关联或无有效记录返回completed=false，Owner查询失败不视为完成。响应只含completed及contactId/version证据，不携带姓名、电话或邮箱。

独立完成条件 `PROJECT_CONTACT_EXISTS`，parameters为空，只用于客户联系人PAGE任务的独立完成规则。完成命令读取CUS锁定事实，保留contactId/version到当前轮次结果证据，经原状态机SUBMIT→COMPLETE完成；该条件不替代其他PAGE/TASK_NATIVE的本轮手工提交要求。联系人新增/引用、修改、删除和恢复在原命令事务内写入既有 `ProjectRuleReevaluationRequested` Outbox；事务回滚不发送，提交后重新读取事实，不由CUS直接改项目状态。计划生效的既有重判机制使已有有效记录也参与判定。原联系人接口、项目客户归属及已发布模板不变。

2026-10-15经需求方确认，新增独立完成条件`PROJECT_END_USER_EXISTS`，parameters为空，仅用于项目成员PAGE（`/pms/project-management/project-master-detail`）任务的独立完成规则。读取本项目、本租户当前有效`END_USER`成员区间并锁定，至少一条有效外部人员记录才满足；轮次证据保留区间ID/版本，经原SUBMIT→COMPLETE状态机完成。沿用成员变更Outbox重新判定，不改联系人条件。统一成员写接口增补`externalPerson: {name, companyName?, mobile?, email?}`：`END_USER`要求该对象且`userId`为空，其余角色要求有效`userId`且不接受外部资料；列表继续返回姓名、单位、电话、邮箱，外部人员联系方式来自PROJ自己的区间资料。
2026-10-17经需求方确认，项目成员补齐与项目客户联系人同口径的部门、职务字段。`externalPerson`增补可选`department`、`title`（各最多64字符）；仅作为PROJ外部最终用户的区间资料，不创建系统部门关系、不授予系统身份，也不与CUS联系人按姓名匹配或互相写入。列表沿用`departmentName`展示内部成员既有部门及外部成员填写部门，并新增可空`title`；未记录时显示“—”，不以项目角色推断职务。编辑仍关闭原区间并新增区间，历史原值不回填或覆盖；原项目范围、版本、幂等和状态约束不变。

PROJ公共`ProjectBusinessContextApi`增补冻结实施前置查询及锁定重验，使用现有Query/Revalidation上下文，返回计划ID、计划版本、是否显式配置及必选EXE来源码；不得让IMP直接读取PROJ表。IMP公开就绪上下文增补可空计划适用事实，历史无该字段仍要求EXE_01～04全量完成；显式不适用项使用`NOT_APPLICABLE`并禁止携带伪造的完成对象、水位或重开标记。CUT继续消费IMP就绪/重验接口，既有任务冻结快照不被重写。

### 2026-10-11 任务审批首次发起准入预览

需求方确认修复割接审批入口：未满足准入时禁止首次提交并显示前置条件，保留既有准入规则。`GET /api/v1/pms/project-tasks/{taskId}/workbench` 在审批绑定原本允许START时返回 `startAdmission`，包含 `outcome`（MATCHED/NOT_MATCHED/UNKNOWN）及 `conditions`（label、outcome）。PROJ复用命令START的当前生效计划、冻结规则和事实求值；仅MATCHED保留START，未满足或不可用都移除该操作，不改变其他操作。项目范围受限的任务可见者只获总体结果，不返回其他任务名称。

此预览只读，不激活节点、不写轮次、规则历史或完成证据，不能代替提交时的锁内版本、权限、准入复核。前端按预览显示未满足条件，不把未知结果当作已满足；当前轮次已经启动后的APPROVAL重提仍沿用原契约。模板配置、审批人和流程定义不变。

### 2026-10-13 割接参考V2工作台与标准方案表格

`GET /api/v1/pms/cutover-workbench/tasks` 接收场景化列表查询 `listKind=TODO|SELF_CREATED`、可选projectId/currentStage/taskStatus及pageNo/pageSize；复用 `pms:cutover-task:query`、当前租户与项目VIEW范围。TODO使用服务端当前可办理集合，SELF_CREATED限定自建来源；筛选在分页前执行，空项目或待办集合返回空。响应为PageResult，每项含原任务Summary及持久化creator对应的creatorName，不将当前owner推定为创建人。项目范围拒绝返回原CUT任务错误契约的403分类。

现有P4阶段方案content继续区分标准、D级简易和完整文件。标准方案新增可选rollbackDescription；steps每行新增可选details（stepName/testResult/businessImpactTime/operator/operationTime）；overview新增可选deviceDetails，行包含deviceId/productModel/currentVersion/targetVersion/remark，deviceId必须属于方案冻结deviceSummary且不能重复。它们是CUT方案记录，不修改AST设备属性，不新增必填门禁；原步骤正文校验保持，D级不接收标准表格details。

步骤正文及补充列按sectionCode/stepNo保存于结构化步骤明细；HTTP仍返回steps.details，暂存、重开、修订派生、审批冻结及Word导出消费同一份字段。依需求方随后确认的整模块持久化整改，固定业务字段不再写入content_snapshot JSON，数据库采用前向迁移补充列及关系。旧方案缺少补充字段时保留原正文，已提交及批准版本仍不可覆盖。

2026-10-13 结构化存储实施：CUT 的配置维度、字段树、选项、绑定条件和外部字段映射由明细表提供，既有 HTTP JSON 对象仅作为传输格式。IMP `ImplementationReadinessApi.readSnapshot(ImplementationReadinessSnapshotQuery)` 按 `tenantId/projectId/snapshotId/snapshotVersion` 读取精确不可变快照，校验当前租户与项目查看范围；不重新评估、不回写、不以最新快照替代。CUT 通过该公共 API 解析任务/评估的就绪引用，再核对其设备集合；执行前仍调用 `lockAndRevalidate` 核实当前前序事实。全表及字段定义见 [割接结构化存储方案](../superpowers/specs/2026-10-13-cutover-structured-storage.md)。

P3外部数据端口以正式Provider目录提供接口、参数及响应字段定义，配置界面不得录入任意URL、凭据或未声明字段。设备版本、加载和反馈命令沿用原任务负责人、项目范围、清单草稿及版本校验；实际调用在事务外执行，返回后重新校验，期间来源变化仅追加历史结果，不替换当前答案。成功零条仍需显式保存反馈，调用失败不能当作零条；人工替代必须由配置允许，并引用当前设备/版本上下文中的真实失败查询。没有正式Provider时返回明确未接通状态，不模拟外部成功。详情只读原始公告，反馈版本引用首次原始行而不覆盖原文。

2026-10-16 需求方确认：设备序列号清单展示当前软件版本。`ProjectDeviceShipmentApi.Device` 及项目设备分页响应增加可空 `currentSoftwareVersion`，来源为 AST 设备的 `conpVersion`。P3 草稿通过 `DeviceQueryApi` 读取同一设备的型号和当前版本，只读展示，不接受人工改写；升级目标版本仍由 CUT 维护。设备版本变化后旧公告查询不再满足当前上下文，必须重新核查；提交在原事务内冻结设备型号、当前版本和目标版本，已提交及历史修订读取原快照。缺少版本保持空值，来源不可用失败关闭，不回退到旧人工版本。

### 2026-10-13 割接二线与研发角色共享审批

需求方指定二线 `probSupporter`、研发 `probRD`，确认按角色审批、不指定具体人员，并随后否决冻结人员池的设计。新节点以明确的列保存角色/候选组引用，由BPM原生candidateGroups建立一个未领取的共享待办；不将角色展开为全量candidateUsers，不保存候选人员清单或整组成员水位。待办读取及办理使用SYSTEM提供的当前有效成员资格，角色无人时待办保留，不跳过或自动通过。原个人审批、旧流程定义及不可变历史保留。

P5详情Node保留可选 `candidateRoleCode`；共享节点办理前originalApproverUserId/currentApproverUserId均为空，决定后currentApproverUserId为实际办理人。我的待办、首页待办及详情读取均以当前有效BPM任务及CUT当前节点为依据；客户端不选择或回传角色成员，角色节点不接受个人改派。现有approve/reject、身份、版本和五项评审不变。办理事务重新核对并锁定该操作者当前资格，领取与决定同事务，记录实际办理人及其当时资格来源；其他成员并发办理不能形成第二个决定。越权尝试不得将共享审批写为HOLD。

BPM新定义的二线/研发使用原生候选组共享任务；现有平台角色策略、角色查询API和任务服务按职责复用，不能随机指定一个人替代角色共享待办。CUT不直接访问SYSTEM或Flowable业务表。领取与决定仅允许经CUT业务校验的同一事务；通用BPM领取、审批或变量修改不能绕过CUT。流程初始化产生新定义版本，既有实例仍引用原定义。通知按发送时有效的收件资格生成幂等消息记录；消息收件记录不作为审批候选名单，通知送达不等于审批完成。流程保存业务身份和角色引用等明确的标量变量，不序列化全量人员池。

2026-10-27按TF-062沟通结果增加内部只读`BpmProjectSummaryProvider`：BPM只在待办/已办的原授权分页完成后，传入受信`tenantId`及本页`authorizedProcessInstanceIds`；无HTTP入口，不接受客户端任意流程ID。CUT实现仅通过本模块流程关联与同租户审批实例读取原冻结`projectName`，返回`processInstanceId/projectName`，不查当前项目名称、不改历史。空集合返回空，失效/缺失关联不补造；BPM仅将匹配本页流程的项目名称附加到既有摘要，不改变分页、总数、原摘要及办理权限。其他流程无Provider结果时维持原展示。

首页任务摘要保留 `approvalOnly`：角色待办通过专用审批页读取冻结资料。首页以原项目范围或精确待办任务ID限定分页及计数；自建、创建、P4、P6写入仍用原权限。角色临时读取只在未暂停、未替换的PENDING审批根及其当前有效角色待办存在时成立，节点未轮到、已办理或实例关闭后不再授予；不扩大到完整项目、同项目其他任务或历史审批。冻结文件关系按明确的列/明细保存，继续由PLT核验当前权限、完整业务引用键、固定工件和文件版本及可用性。CUT读取授权水位只用于事务内复核，不绑定文件登记时范围版本；闭环附件和文件写入不由角色待办授权。

V463仅为既有角色补充必要入口与查询/审批权限；V464补充PLT文件下载与预览按钮权限，Owner策略继续限定冻结文件只读范围。不分配用户、修改role.data_scope或PROJ项目成员。租户范围与服务端当前角色资格持续校验。

2026-10-20 审批原始资料显示修复：现有审批详情及决定响应仅在已授权的 `FULL` 投影中追加 `sourcePresentation:{matchingOptions,answerSchemas,incomplete}`。显示说明按审批冻结的 checklistId、同租户同任务清单及原配置修订读取；字段须与冻结事实的采集项ID、稳定键、类型、定义ID及版本一致，answerSchemas以字符串checklistItemId为键。答案始终来自sourceSnapshot，不读取当前清单结果、不改变历史快照。仅补充显示说明失败时保留已授权审批事实并返回incomplete=true；受限投影和审批无权请求不读取或返回这些说明。审批页不再因普通任务查询菜单存在而请求普通清单接口，原普通任务查询仍保留项目范围校验。



### 2026-10-14 模板正常闭环任务完成契约

2026-10-25批准的内部重验补充：`TaskBusinessObjectProvider.lockForApprovedClosure(ClosureRevalidation)`只供已验证的正常闭环审批回调使用，不提供HTTP入口。上下文携带原`ClosureExitCommand`、当前关联任务、对象及预期事实版本；PROJ仍锁项目/任务/冻结契约/关联并核对聚合事实版本。默认实现保留Owner原有`lockAndRevalidate`。ACC满意度适配重验锁定的IN_REVIEW申请、原快照摘要、项目/树版本、实际材料审核人及BPM双人工批准/冻结候选人证据，再按原租户、项目读取范围锁定关联结果、校验签字/达标/归档和版本；返回内部事实不携带操作授权。采集工作队列`get`及通用锁定命令的受派人限制不变，不改受派人、快照、结果、权限或历史。失败仍整体回滚，不能将该通道用于关联/解绑或普通页面办理。

本次需求方明确S6任务在正常闭环审批通过后完成。新增无参数条件 `NORMAL_CLOSURE_APPROVED`，仅允许独立作为正常闭环PAGE任务的完成规则，专用入口为`/pms/project/normal-closure`并复用现有正常闭环面板；每个模板最多一个，必须位于终态阶段并冻结原有人工正常闭环策略，不得同时配置规则自动闭环。该条件不对手工提交、普通规则重评估、HTTP成功或审批发起返回已满足。

ACC沿用现有正常闭环检查、申请、BPM事实与审批回调接口；PROJ在申请检查中仅对这一精确冻结契约的当前在办任务做审批后完成预判，原始未完成状态、契约及版本进入检查证据摘要。其他任务、子项目、阶段条件、退出门禁、业务事实和成果重验全部保留。审批回调验证真实BPM结果、审核人和原检查摘要后，调用PROJ任务生命周期服务，通过冻结状态机完成该任务并追加执行轮次、完成评估及事件，再关闭阶段和项目；任一步失败整体回滚。拒绝、撤回、陈旧来源、跨租户或错误审核人不得完成任务。无该专用任务的既有模板和原厂督导S5路径维持原检查语义；已冻结历史不回写。


### 2026-10-14 到货、安装、培训与割接审批任务事实

复用 `TaskBusinessObjectProvider` 的候选分页、当前事实、事务内锁定重验和当前执行轮次接口，新增以下Owner适配。既有 `CUT/CUTOVER_TASK/CUTOVER_COMPLETED` 保持P6成功归档语义。

| Owner / 对象 | 完成事实 | 本次五类草稿口径 |
| --- | --- | --- |
| IMP / ARRIVAL | ARRIVAL_SIGNED | 本项目现有到货签收记录status=1；至少一条关联记录达标 |
| IMP / INSTALLATION | PROJECT_INSTALLATION_COMPLETED | 本项目当前设备集合非空，每台设备都有当前有效的已完成安装记录和安装位置；复用原全设备结果读取 |
| IMP / TRAINING | TRAINING_CONFIRMED | 本项目现场培训经现有客户确认操作形成CONFIRMED；至少一条关联记录达标；草稿、已外发和已作废均不通过 |
| CUT / CUTOVER_APPROVAL | CUTOVER_APPROVED | 本项目割接任务当前提交方案的正式审批实例为APPROVED，决定时间存在且未被替代；至少一条关联记录达标 |

项目割接审批办理补充独立视图 `CUT_CUTOVER_APPROVAL_WORKBENCH@1`，页面 `/pms/cutover/approval-workbench`，Owner/实体为 `CUT/CUTOVER_APPROVAL`，支持 QUERY、CREATE。创建操作 `CUT.CUTOVER_APPROVAL.CREATE@1` 复用既有项目割接 intake 的输入、幂等及权限/数据范围/实施前置契约，来源保持 PROJECT_EVENT；返回 objectType 为 CUTOVER_APPROVAL、objectId 为割接任务 ID。后续方案与审批仍由 CUT 原生命令办理，完成事实仍为 CUTOVER_APPROVED。既有 `CUT_CUTOVER_APPROVAL@1` 只读注册及冻结绑定不变，新视图仅经新草稿或正式计划修订启用。

正常闭环上下文与锁定复核统一读取项目 activePlanVersionId 指向的本租户、本项目 EFFECTIVE 计划 executionSnapshot.closurePolicy；仅无生效计划指针的旧项目沿用建项 closurePolicySnapshot。有指针但计划身份/状态无效时拒绝，冻结规则为空时不得回退旧规则。闭环来源证据包含计划 ID、修订号；规则与历史快照不回写，计划变更后必须重新检查和申请，原审批不得解释为批准新计划。

交互读取保留当前功能权限与完整项目数据范围，未授权或空范围不能成为候选；自动完成必须锁定并重验本次PROJ执行轮次及对应Owner对象。模板和已执行历史只冻结引用、业务版本与完成证据，不复制签名、Token或客户外发秘密。到货签收、培训客户确认和CUT最终审批的成功事务追加独立重评事件，重评重新读取事实；原业务事件、通知和状态机保持不变。

V478补充四个高段ID、幂等的业务视图种子，只登记现有业务办理入口。五类项目模板仍为未发布草稿，业务视图注册不发布项目模板。

### 2026-10-15 巡检专项客户归档引用

按本次已批准的巡检专项口径，SRV持有巡检任务、冻结规则、执行、结果、报告、标注及处理记录。CUS新增内部 `CustomerInspectionHistoryApi.register(ArchiveCommand)`，只能加入调用方事务，校验当前租户、操作者、客户可见范围、原项目范围及当前服务经理资格。SRV在已锁定的任务内先重验有效报告、完整标注、全部跟踪闭环及冻结处理证据，再登记引用并经状态机归档；引用失败整体回滚。工程师申请与服务经理归档分别使用 `POST /api/v1/pms/inspection-tasks/{taskId}/actions/request-archive|archive`，沿用If-Match和Idempotency-Key。

CUS仅保存原任务及最终任务版本、当前报告、原项目和客户标识，以及设备、历次报告、执行、结果、标注、问题、处理记录和证据的精确来源ID。固定关系存列或明细表，不复制SRV业务正文，不修改项目进度，不引入WorkBinding。每租户每任务只能登记一份不可变归档引用；同来源版本与内容可重放，内容不同拒绝。项目后续调整客户不重写原任务客户。客户删除守卫保留该引用。

2026-10-28需求方要求核对管理员权限并完成正式归档：System既有`super_admin`具备归档功能权限，且PROJ当前`PROJECT_MANAGE`完整范围包含原项目时，允许执行巡检最终归档；这是一项管理操作能力，不产生服务经理成员资格。任务详情新增`canArchive`布尔投影，`manager`继续只表达实际服务经理身份。SRV写入口与CUS登记入口均独立重验受信租户、操作者、功能权限和项目范围；SRV取得任务锁后再次检查管理员能力。普通用户仍按当前服务经理资格办理，仅有VIEW、任务负责身份或归档按钮权限不产生该资格。该能力只增加归档及其必要的任务详情读取，不增加其他Owner写动作、审批候选资格，不改变申请、闭环、证据、版本、幂等与不可变历史校验。

`GET /admin-api/pms/customers/{customerId}/inspection-history` 复用客户查看权限和客户数据范围，并以操作者当前可见的原项目集合在分页前筛选；空范围返回空。详情仍由原SRV接口再次授权，引用链接不授予巡检或文件访问权限。客户历史与巡检已归档内容只读，下载精确工件版本，不用当前文件替代历史版本。

巡检误报接口位于 `/api/v1/pms/inspection-rules/feedback`：按标注ID保留每次误报历史，最新标注不再为误报或报告被新版本替代时标为历史，不删除。管理员的有效性确认独立追加修订，If-Match为该标注最新确认修订号（未确认是0），不改巡检已归档真值。查询按当前项目范围裁剪；普通查询只返回本人标注，具有`pms:inspection-feedback:analyze`时可分析授权项目汇总。`details`字段权限控制设备标识、型号、原始命令和输出摘要。

统计按规则修订分别展示，统计期为检出结果生成时间，分母为同修订的异常检出结果数，分子为其中当前报告最新误报标注且经管理员确认有效的不同结果数；历史标注与重复报告不重复计数。确认筛选只裁剪反馈列表，不改变统计分母。结果同时返回统计期和本次数据截止时间，查询失败不发布部分比率。规则优化仍通过现有复制修订草稿及发布流程完成。

Excel/CSV导出先创建来源快照记录，再生成平台文件；记录筛选列、截止时间、条数、精确标注/确认引用和当前/历史状态。单次10000行及50MB是导出资源上限，超过时明确失败并要求收窄筛选，不静默截断。失败可重试，下载重新核对原导出人、当前分析/导出/字段权限和全部来源项目范围。固定业务筛选及来源关系均使用列和明细，不存JSON业务结构。

巡检在线来源绑定SRV的单次设备执行记录，预检与正式执行各自拥有独立记录和CollectionTask。Owner通过统一采集`Source`返回可选的冻结执行规格：`PRECHECK`只有连接与认证测试、命令列表为空；`INSPECTION`按SRV冻结规则顺序形成整批命令。按2026-10-15用户补充裁决，本次暂不实现逐命令超时与继续策略，正式执行复用现有整批下发和整体超时配置`manual-execution-timeout-seconds`；未来确有独立超时需求时可拆分采集任务。规则原有版本与配置历史保留。未提供该规格的既有实施、割接与独立中心沿用原语义。命令与用途均由Owner决定，HTTP输入不能替换冻结内容。

PLT在创建任务前先解析当次临时认证或重新核验保存凭证，再将不含Secret的实际连接快照交由Owner核对预检设备、操作者、端点、认证模式、用户名、凭证版本及授权引用。正式执行授权独立签发；临时密码不进入幂等摘要、持久化、审计或队列，完成请求后清空。用途单独存列，并进入DAC幂等指纹和任务HMAC授权绑定。DAC的预检工作分支仅调用协议连接测试，不执行巡检命令；正式执行使用原有整体超时行为。


2026-10-16新建割接等级确认最小输入：项目、已发货设备、等级问卷。创建请求的 `configurationCode/background/cutoverType/networkMode/scheduledTime` 可缺省或为空；未提供配置时，服务端重查创建时点有效发布配置，仅唯一时采用，零个或多个均返回配置冲突，不自行挑选。既有显式配置及来源调用保留范围校验。A/B/C在调研选定类型、组网、设备类型并保存结构化割接背景；D级跳过调研且无需这三个匹配字段。方案与审批不得继续把创建时背景和计划时间作为必填事实；所有方案（在线标准、D级简易、完整上传）增加单个 `scheduledTime` 计划割接时间输入；审批采用该明确输入值，原明细计划表保留，A/B时限继续按该值执行，不以当前时间或虚构默认值填补。D级来源允许匹配类型及计划时间为空，不影响人工等级、审批权限、状态机和不可变历史。


### 2026-10-19 割接服务等级来源接入

`CustomerServiceLevelCurrentApi` 由 CUS 实现，独立于尚未实施的时态等级版本 API。`inspect(Query)` 查询现有 `cus_customer_service_level` 的当前唯一有效记录。查询携带受信 tenant/customer，Fact 明确区分 `AVAILABLE/NOT_CONFIGURED`，包含来源记录 ID、行版本、等级代码和可空起止日期，不伪造版本修订 ID。当前日期按服务端时钟；状态为已生效，日期区间两端包含当天，空端不限；多条有效记录返回数据冲突，客户不存在或 Provider 失败均不返回未维护。现有维护功能与角色授权不改动，CUT 仅调用公开 API。

CUT P2 保存体增加可空 `manualCustomerServiceLevelCode`，只在真实未维护时接收已启用字典值；已维护时拒绝人工覆盖。查询返回原客户来源事实及评估的手选值/操作者/时间；保存后沿用该快照，客户后续等级变化不触发刷新、重新保存或提交阻断。只补齐创建阶段未维护时的手动取值，不增加动态跟随规则。新增列保存来源记录引用和手选审计，既有不可变 JSON 历史保留。该差量不新增 CUS 维护入口或全员/全模板查询权限。

### 2026-10-17 工勘删除与当前业务关联的事务边界

SOL工勘Owner在原生删除授权、项目MANAGE范围、执行入口、草稿状态、委外引用及行版本校验全部通过并删除成功后，在同一事务调用PROJ公开内部端口 `ProjectBusinessObjectDeletionApi.unlinkDeletedObject(DeletedObject)`。该端口不开放HTTP入口，租户和操作者来自当前认证上下文，重验项目范围；只按项目、Owner、对象类型及对象ID精确关闭有效计划下当前未结束阶段/任务轮次的活动关联，沿用关联版本CAS与审计。已完成轮次、旧计划、其他对象及关联原行均保留；清理冲突必须与工勘删除和回执整体回滚。异步规则重评继续使用原事件，不再承担删除回执返回前的引用清理。

### F-SOL-001 工期审批任务意见事实

2026-10-19按用户授权修复后续工期审批意见回写。BPM新增内部只读`DurationChangeBpmApi.getApprovalOpinion(ApprovalOpinionQuery)`，查询对象包含受信租户、冻结流程实例ID/定义key/业务changeId及冻结审批人ID。BPM校验租户一致、流程已通过并结束、业务关联一致，读取唯一已完成且通过的`serviceManagerApprove`任务意见。关联不存在、不一致或不唯一时失败关闭；真实未填写的可选同意意见可为空，不用流程结束原因兜底。SOL完成原授权、当前状态及冻结事实校验后调用该契约，不直接访问BPM Service或表。

同意终态在BPM原事务`beforeCommit`阶段处理，使任务历史已由引擎写入且BPM与SOL仍同事务提交/回滚；驳回与撤回沿用原流程结束原因和同步处理。平台既有状态事件定义及流程节点、候选策略、路由均不变；重复终态不查询补填意见、不覆盖历史。本次无新增HTTP接口、数据表、权限或初始化配置。


### 交付件历史材料文件入口补充（2026-10-19）

GET `/api/v1/pms/projects/{projectId}/deliverables/{id}` 的历史材料行增补可空 `fileKey`（ownerContext、objectType、objectId、purposeCode、referenceKey）。仅在原项目与交付件查询授权通过，且当前租户引用对应冻结工件及版本时投影原文件业务锚；不改历史、不生成下载地址、不授予文件权限。预览、下载和版本历史仍走 PLT 文件接口及原业务授权；无法解析引用时保留历史文件名和版本，不臆造访问入口。

### 2026-10-20 项目当前模板绑定迁移

按需求方授权，项目可通过新的项目计划修订更换当前模板绑定，项目类别等属性保持不变。创建时的`lifecycleTemplateId/lifecycleTemplateRevisionNo/lifecycleTemplateRevisionId`仍为不可变来源历史；当前绑定由`activePlanVersionId`所指生效计划的`sourceTemplateRevisionId`承载，旧计划及其来源不回写。不能仅修改显示名称或将原来源字段覆盖为新模板。

`POST /api/v1/pms/projects/{projectId}/plan/draft/{draftId}/template-binding` 输入`expectedVersion/expectedBasePlanVersionId/targetTemplateRevisionId`及`Idempotency-Key`，要求既有`pms:project-plan:manage`和当前项目编辑范围。服务端锁定当前项目和目标模板，目标须为同租户启用模板的已发布V2修订，使用项目现有事实执行目标版本冻结的匹配规则；不修改属性以满足模板，不回退到旧四维匹配器。校验草稿、基线及版本后，将目标发布设计和来源修订ID原子写入指定草稿，并记录原/新来源审计。接口不发布模板、不生效计划、不改任务或业务记录。后续项目专属调整仍受原保存、影响预览与生效保护约束，已开始节点不能直接删除、换绑定或移阶段；生效失败不切换当前绑定。

计划查询的`effective/draft`加性返回`templateBinding`（`templateId/revisionId/revisionNo/name`），按该计划来源修订读取，缺失来源不猜测。页面展示生效计划的当前绑定，不将未生效草稿显示为已绑定。公共模板、立项来源及各旧计划历史保持独立。
### 指定存量项目一次性新轮次的交付隔离（2026-10-20）

依据 Q-PROJECT-RESTART-RESULT-20261020，本次仅对获授权的六个项目执行一次。项目集合与目标模板作为执行输入，不在产品代码中固化项目ID、项目编码、租户、模板映射或日期标记。PROJ 在项目锁、授权、状态、版本和幂等校验后，通过 ACC/PLT Owner API 创建新轮次，不直接访问其他模块表；不新增日常重置页面或独立重置机制。

PLT 的模板冻结交付要求增加 `archived_reopen_snapshot_id`：0 为当前要求，非0引用既有受控重开快照。内部 `archiveForProjectReopen(ProjectReopenArchive)` 接收项目、重开快照ID及预期当前要求ID集合，完整比对并原子归档；保留原要求、材料、提交、确认及来源字段，不撤回旧成果。当前身份、列表和完成判定只使用当前要求，新计划以相同交付编码创建新的要求ID。历史ID可读，但不允许重新提交、确认、修改、撤回或刷新历史完成状态。此内部能力不公开为通用重开入口，也不以归档代替上游状态机动作或授权。

PROJ 复用 `actions/reopen` 命令与 `proj_project_stage_snapshot` 的 REOPEN 快照，保留前后计划、目标模板、时间和不可变旧运行证据，不新增重置台账或独立重置入口。提供 `targetTemplateRevisionId` 时校验正常结项、最新已发布且匹配的模板，并从S0建立新轮；未提供时沿用原异常关闭快照恢复路径。跨模块通过 `ProjectReopenScopeApi` 读取正常结项至ACTIVE/S0的最新REOPEN快照，不依赖特定原因码、日期或项目白名单；未执行此操作的项目保持现有行为。可独立建单的 Owner 通过 `TaskBusinessObjectProvider.handlingCreatedAt` 提供同租户同项目办理记录的原始创建时间；固定主记录的验收业务取当前报告版本创建时间，不取旧记录更新时间或审批时间。未知时间不得算作新记录。关联、人工引用和完成判定均排除重开前对象，不能仅排除旧任务已关联对象。

人员指派与安装等复用原项目对象的业务，按当前执行轮次重新读取并校验 Owner 当前真实完成事实，不继承旧任务完成状态。2026-10-20需求方纠正：不增加“确认本轮已核对”按钮、确认接口或额外确认凭证门槛；保留既有授权、执行轮次、事实版本和业务完成校验。新旧关联与执行历史继续隔离，可独立建单业务的新记录要求不变。

### 2026-10-20 任务业务结果主动刷新

既有“刷新业务结果”入口对可办理的业务绑定任务调用 `POST /api/v1/pms/project-tasks/{taskId}/business/refresh`；请求体为 `expectedTaskVersion/expectedContractVersion`，`If-Match` 与合同版本一致。租户、操作者和项目由服务端确定。须具备 `pms:project-task:execute`、当前任务可见及办理范围、Owner 查询权限；锁定项目、任务和当前合同后重新校验版本及可写执行轮次。仅 `IN_PROGRESS/PENDING_ACCEPT` 任务参与，未开始及已结束任务的页面刷新保持只读。

服务端在同一事务中复用自动关联同步及 `completeFromBusinessResult`，按当前计划、Owner 事实、门禁及冻结状态机判定，返回 `completed/unknown`。不新增人工确认凭证、不改业务数据、不跳过开始或完成规则。完成命令仍使用既有执行轮次幂等键与审计/Outbox；页面刷新本身只追加操作者刷新审计。重复请求在版本过期或任务已经结束时拒绝，客户端须重新读取，不自动重放过期请求；条件未满足的重复刷新不会推进状态。GET 上下文不产生写入。上下文 `allowedActions` 的 `REFRESH_RESULTS` 仅表示可执行上述重评；没有该操作时按钮仅重读现有业务上下文。


### 2026-10-20 割接未提交草稿来源更新

割接详情 `GET /api/v1/pms/cutover-tasks/{id}` 增加可空 `assessmentSubmissionBlockReason`，仅用于解释 P2 当前提交阻断原因，不作为授权或状态字段。未提交草稿详情读取最新项目和原选设备来源；GET 不更新业务表。既有评估保存及提交接口沿原授权、租户、任务/评估版本、幂等和状态机执行，在事务中锁定本次读取的最新项目范围、项目版本与设备归属版本；创建时的旧通用项目版本不再永久阻塞草稿。不能自动增删、替换设备或接纳跨项目设备。保存更新当前草稿及其设备事实，提交冻结本次来源，已提交历史不改写；项目客户变化须更新来源并重新确认服务等级。


### 2026-10-20 保障人员选择差量（需求方批准）

标准方案仅客户、一线必填，二线和研发可选；已添加行保持完整性校验。客户可选择该项目全部当前有效成员或手填；一线、二线、研发分别选择当前租户启用的 PROJECT_MANAGER、probSupporter、probRD 系统角色成员。
新增只读 GET `/api/v1/pms/cutover-tasks/{taskId}/support-person-choices?roleCode=...`，沿用割接查询权限并校验任务租户及项目范围，返回 `[{reference:{sourceCode,id,version},name,phone}]`；sourceCode 仅 PROJECT_MEMBER 或 SYSTEM_USER。
标准方案人员行及批准后联系人 PATCH 可带可空 `personReference`，原 customerContactReference 用于保留既有历史，不与新引用同时使用。CUT 以可空关系列保存人员来源，不以 JSON 保存固定关系；保存重验所选来源，提交要求工程师选择实际用户并重验角色，姓名不得伪造。成员失效、改角色、用户停用或版本变化均要求重选。客户手填仍需完整姓名和电话。所有授权、项目范围、幂等与根版本并发校验保留；历史来源不回填，批准后变更继续追加原审计链。


### 2026-10-20 割接共享审批直接办理恢复

根据用户随后确认的恢复要求，共享待办直接返回既有 `APPROVE/REJECT` 动作，打开后填写完整五项评审并提交，不再暴露独立 `actions/claim` API。`approve/reject` 保持既有权限、版本和幂等契约，在同一事务锁定操作者当前角色资格、领取原生任务并完成审批决定，失败一并回滚，不留下单独领取状态。既有已领取节点继续由固定人员办理，不重新核验项目成员资格；仍检查账号有效、租户及审批权限。改派仅面向已分配/既有已领取的非终态节点，保持已有原因、版本、审计及原子性契约，历史审批和领取记录保留。


### 2026-10-20 审批人员查看完整割接任务（需求方批准）

审批人员可从FULL审批页进入完整任务只读视图。原任务详情、清单/修订/匹配选项/外部状态、方案及闭环GET接受普通查询权限或query-approval，但服务端逐次校验同租户同任务：普通项目范围，或者本人当前有效审批待办，或者有明确决定时间和已通过/驳回状态的本人实际办理记录。未领取角色成员仅当前共享待办期间可看；实际办理过的启用人员在审批结束、角色变化或驳回重提后仍可只读查看这项任务；其他任务和整个项目不因此可见。无项目范围的任务、方案和闭环查询不返回写操作，所有写接口继续原权限、项目范围、负责人、版本及状态校验。审批原页继续展示原冻结版本，完整任务页面明确为只读当前任务。

完整任务附件READ/PREVIEW/DOWNLOAD复用PLT业务引用校验及CUT逐次读取/锁定授权，保持精确工件、文件版本、引用和可用性校验；不授予文件写权限。历史审批及来源快照不回写。已实际办理人员的原审批FULL投影延续只读访问，不能因历史参与而重新办理已结束节点。


### 2026-10-20 受控Word方案在线预览（需求方批准保留分页、图片和版式）

公共文件查询为可用Word版本投影PREVIEW动作。新增POST `/api/v1/pms/files/{artifactId}/preview-content`，使用原文件访问请求体（versionNo/operationCode=PREVIEW/ownerContext/objectType/objectId/purposeCode/referenceKey），须具备pms:file:preview且通过Owner精确版本读取、锁定及当前可用性校验。服务器有界读取不超过原50MiB平台上限；Word由本机LibreOffice无界面转换为PDF，原生PDF/图片/文本原样返回受控内容，转换不登记新工件/版本、不修改原件，不调用公共Office在线服务。每次转换独立临时目录及用户配置，禁用宏和链接更新，超时/失败关闭并清理；返回前重新校验授权。响应Content-Type对应派生内容、Cache-Control:no-store、nosniff，成功/失败追加FILE_PREVIEW审计；不公开存储位置或凭据。已有下载及原生格式短时访问票据保持原契约。CUT只接入既有公共文件组件，不实现独立方案预览页。


完整任务GET新增 `approvalReadOnly`，仅通过审批资格而无普通项目范围时为true，allowedActions为空；project追加来源办事处departmentName。该视图读取CUT自己的原记录、冻结设备和已保存的前序就绪引用，不重新调用IMP、PROJ或CUS接口获取其权限范围外的当前资料；就绪引用仅包含已保存的decision/snapshotId/version/projectId/deviceIds，不将缺失的IMP明细伪造为空结果或重新评估。前端据此强制任务各步骤只读，并跳过普通项目/成员接口。普通项目查询及实施条件复核仍保留原Owner授权链。

### 2026-10-20 服务经理改派候选选择

需求方确认保留服务经理资格限制，不允许把服务经理节点改给不具备资格的管理员。新增 `GET /api/v1/pms/cutover-approvals/{approvalId}/reassignment-service-manager`，须具备 `pms:cutover-task:reassign-approval`，按受信租户和精确审批实例校验存在未处理服务经理节点后，复用PROJ当前唯一主责服务经理事实，返回 `CommonResult<WireLong|null>`。无唯一有效人选返回null，提供方失败返回原错误，不回退为全部用户或冻结旧审批人；不返回项目业务快照。读取只是选择依据，提交仍按原锁定重验、版本及状态规则判断资格。`REASSIGN_TARGET_NOT_OPEN`保留机器错误码和HTTP409，msg显示具体业务拒绝原因。

### 割接方案复用调研附件（2026-10-20）

PLT既有内部`FileArtifactApi.attachExistingVersions`增加受支持目标`CUT/CUTOVER_PLAN/FULL_PLAN`，保持UUID引用键、精确源版本重验、源不可变/目标可变策略、类型/大小、范围权限与同事务审计。CUT方案文件策略允许既有清单证据类别作为方案引用来源；不增加上传权限或匿名访问。方案保存仅从其冻结清单ID/版本中的现网拓扑绑定到空的割接前拓扑，源引用不改写。方案文件消费必须按当前任务完整业务键查询，不能将同一工件的最早引用当作唯一归属；公开文件接口及响应结构不变。

### 本机 DAC 服务发现（2026-10-21）

仅 desktop 运行配置提供回环发现端点。1.1.0 起候选地址为 `http://127.0.0.1:8280`～`:8289`，`GET /api/v1/desktop/status?nonce=<64位小写十六进制>` 使用协议 3，响应包括 `product=npdms-dac`、`status=READY|STARTING`、`serverUrl`、`webOrigin`、实际 `port`、`publicKey`、`instanceId` 和 `signature`；具体签名字节与首次配对契约见 F-INT-012 Feature Spec 的“本机客户端端口与配对增量”。首次身份确认由用户点击已安装启动器完成，经短时、绑定用户和租户的服务端连接握手中转签名证明，免输配对码；具体 connections API 见同一 Feature Spec。旧只读 `GET /desktop-pairing` 指纹端点保留且不开放 CORS，正常页面不再调用。网页仅存公钥，使用随机挑战验证已确认身份后才允许本机业务操作。1.0.x 的协议 2 / 48182 为旧客户端行为，新网页不降级发现或绕过配对。发现端点不返回账户、凭据或任务数据且禁止缓存；CORS 仍限制精确配置网页来源，Host 与远端地址仍须回环；所有业务接口继续认证并校验设备/项目范围。探测失败不能区分未安装、服务停止或浏览器权限拒绝，不能伪报安装状态。独立服务器 DAC 不适用客户端候选端口。

desktop 的 `GET /desktop-status` 提供本机只读 HTML 状态页，无需登录，复用上述发现接口展示就绪状态和配置的环境地址；禁止缓存和被框架嵌入，沿用回环 Host 校验。只开放此页面的 GET，不开放其他通用页面或业务操作。交互式安装成功后等待页面可访问并用默认浏览器自动打开；静默安装不打开浏览器。

本机保存连接的读取、保存票据增加`connectionScope(deviceId, protocol)`，项目取已授权来源，设备由来源适配器校验后签入票据，协议限SSH2/TELNET；本机不接受浏览器自报的归属。保存连接的项目、设备关联单独落本机关系表；历史无关联连接保持原件但不参与匹配。读取与执行均强制校验范围：有设备时按设备和协议匹配，不回退项目；没有设备时按项目与协议匹配，仍保留环境、租户命名空间与用户隔离。窗口重新打开时单条自动选中，多条仅提供选择，回显名称、地址、端口、用户名而不返回密码；当前范围与用户主动切换认证方式不得被后台刷新覆盖。

客户端注册 `npdms-dac://start/`，仅允许启动固定的 NpdmsDac Windows 服务，不接受任意命令、地址、文件路径或凭据。页面须由用户点击触发协议，随后有界轮询发现端点；不得自动循环唤起外部应用，不以服务进程存在代替业务就绪。

NPDMS 增加 `POST /api/v1/pms/device-collection/sources/{entry}/{objectId}/desktop-executions`，沿用网页登录、来源执行权限、项目范围、期望版本及请求幂等键，仅接受连接元数据及本机保存连接引用/版本，不接受秘密。返回原执行记录与短期操作票据。`POST .../desktop-access` 在当前来源范围内签发本机任务读取或个人连接读取/保存的操作票据；连接保存/使用同时要求原设备凭据权限。原服务器执行 API 保持原契约。

DAC 的 desktop 业务路由通过 `X-NPDMS-DAC-Ticket` 向已配置 NPDMS 的 `POST /api/v1/pms/integration/device-ops/desktop/tickets/resolve` 解析授权，保留租户、使用人、项目、任务、命令、连接版本及操作绑定；60秒窗口外拒绝。浏览器不向回环端点发送完整登录 Token。解析后的主体进入原 DAC 授权和执行引擎，本机任务使用原任务号幂等提交。

本机通过任务限定回传凭证主动报告接受状态并轮询取消，终态通过原 multipart 结果协议送达 `POST /api/v1/pms/integration/device-ops/desktop/callbacks`。接收仍验证固定任务/collection、结果序号/版本、日志大小/摘要和不可变 Receipt，并复用原 PLT 文件接收、状态机及业务消费。平台只读本地上报投影，不尝试连接现场回环地址；离线不推断失败，也不重发命令。

响应不明时可通过 `POST .../desktop-executions/{executionId}/resume` 重新核验原使用人及当前来源授权，继续原任务；不创建新幂等键。DAC 在提交前调用 `POST .../desktop/tasks/{taskId}/claim`，以本机持久化的 executionId 独占认领；另一客户端、已取消任务或缺少明确 `accepted=true` 的业务回执均不得下发。后续 `/observations`、`/control`、`/cancelled-before-submission` 仅接受该任务限定凭证，不能授予新执行；取消证明仍由原状态机收敛。


### 2026-10-22 既有未绑定项目首次模板分配

`POST /api/v1/pms/projects/{projectId}/plan/initialize` 补充已有项目首次绑定模板并实例化计划的入口。沿用 `pms:project-plan:manage`、服务端租户及当前项目编辑范围；请求体为 `expectedProjectVersion`、`expectedTemplateRevisionId`，请求头为非空且不超过128字符的 `Idempotency-Key`。不接收模板ID、项目属性覆盖、人员指派或生命周期状态。

锁定项目后重验授权、版本和ACTIVE状态，要求所有原模板引用和当前计划为空，且无任何计划、阶段、任务、里程碑、交付件或门禁实例（包括已删除历史）。目标为同租户启用模板的当前已发布V2修订，提交时按项目真实属性重新求值，只允许唯一命中且与请求修订一致；无匹配、多匹配、过期发布或停用模板拒绝，不兜底、不覆盖旧绑定。候选范围由启用状态与冻结规则决定，不硬编码模板ID、名称、数量或项目白名单。

同事务首次冻结模板来源与闭环策略，按完整发布快照生成阶段、任务、状态机引用、执行契约、WBS闭包、里程碑、交付件及门禁引用，再生成有效计划和执行轮次、结果订阅与定时规则。阶段仅由原准入服务正常激活，不直接改写项目生命周期、指派、编码、树、来源或业务事实。命令幂等、审计及初始化整体提交或回滚；相同请求重放返回原结果，不重复生成计划；不同键再次初始化拒绝。响应为 `projectId/planVersionId/templateId/templateRevisionId`。此入口不更换已有计划，不绕过历史保护，不承接旧功能新Feature。

### 培训打印模板只读选用（2026-10-22）

`GET /api/v1/pms/dynamic-form-templates/training-print-selection`：沿用`pms:imp-training:query`，服务端可信租户/用户上下文，标准分页上限。PLT仅返回当前租户`TRAINING_PRINT_FORM`分类中已启用且具有当前发布修订的模板名称、模板/修订ID、发布时间及表单配置/规则；不返回草稿、其他分类或管理动作，不授予通用模板查询/管理权限。前端按发布时间默认选择最新项；空列表使用服务端现场培训标准表单PDF，查询失败明确报错。既有通用模板接口权限和发布/启停契约保持不变。

### 交付件关联业务数据类型范围（2026-10-22）

需求方要求界面“关联业务成果”改为“关联业务数据”，仅显示当前业务需要关联的类型。既有交付件`result-types`接口只返回当前项目有效冻结计划为该交付件声明的来源类型：复用既有显式来源及原生任务来源匹配，不按显示名称、阶段编号或固定业务类型臆测。未允许BUSINESS_RESULT或无匹配来源时返回空列表，不降级为全类型。`result-candidates`及新提交同样验证所选类型属于该范围；既有提交重放、授权、项目范围、成果精确版本复核和不可变提交历史保持原语义。


2026-10-22 阶段状态查看时间口径（用户确认）：`GET /api/v1/pms/projects/{projectId}/node-executions/history` 增加轮次 `stageCode` 和只读 `submissionTime {at, source, label}`。原 `submittedAt/submittedBy/submissionNote` 仍只代表真实手工提交；有手工提交时优先显示。自动完成按冻结完成依据读取业务开始/生效或结束/确认时间，标明来源；`endedAt` 继续代表正式状态转换时间，不替换为业务日期。结果订阅读取原完成收据所指不可变扫描的形成时间，不能改用最新扫描；业务绑定仅通过 Owner 公共 `historicalHandlingTime(HandlingTimeQuery)` 或精确版本重验后的结果契约读取。人员与最终用户引用原冻结成员区间；原生阶段按本计划必需任务的业务依据汇总。缺失、外项目、版本不匹配或无法证明的历史业务时间不猜测、不落库回填，不改写轮次、历史快照、状态机或权限规则。


2026-10-22 完工证明到货日期：IMP新增内部只读 `SignedArrivalDateApi.latest(Query{tenantId,projectId})`，仅查询原 `imp_eng_arrival` 当前项目、同租户、未删除、状态“已签收”且实际到货时间非空的记录，返回最大实际到货日期或null。ACC通过公开API消费，授权后提供 `GET /api/v1/pms/completion-confirmations/project-arrival-date?projectId=...`，允许既有完工证明查询/创建/编辑权限并保留项目可见范围；不授予到货维护权限。完工证明详情增加有效arrivalDate，草稿优先从IMP读取（缺失时按下述补充采用手填值），外发/确认后仅从原成套不可变CertificateSnapshot读取；外发新增快照冻结arrivalDate，缺字段的旧快照按空值读取。不更新既有快照；手填列按下述补充裁决新增。

### 2026-10-22 项目状态卡聚合查询

经需求方确认，新增只读 `GET /admin-api/pms/projects/statistics`，与既有 `/admin-api/pms/projects/page` 使用同一 Controller 前缀及 `pms:project:query` 权限，租户和主体均来自服务端。只在 `ProjectTreeScopeService` 当前 `PROJECT_VIEW` 完整可见集合内聚合，空范围返回全零，不接受客户端权限集合或列表筛选条件。返回 `total/overdue/s0/s1/s2/s3/s4/s5/s6/normalClosed/noTrackingClosed/exceptionClosed` 数字字段：total含全部可见未删除项目，s0～s6限定ACTIVE及current_stage，三种闭环按原生命周期值精确计数；overdue复用原分页当前超期判定，同项目多个超期节点仅计一次，已完成历史不参与。顶部卡片维持独立于列表筛选的现有口径，用一次聚合替代逐卡分页；原分页、状态展示、授权与超期历史保持不变。

### 2026-10-22 完工证明无签收日期时的手填补充

完工证明保存请求允许可空 `arrivalDate`。ACC只在当前项目无可用签收日期且 `itemArrival=是` 时将其保存为 `acc_completion_certificate.manual_arrival_date DATE NULL`；不得覆盖已签收来源。草稿详情返回有效 `arrivalDate` 与 `arrivalDateManualAllowed`；自动来源优先，缺失时采用手填值。外发快照仍冻结有效日期，历史快照和文件不更新。

### 2026-10-22 设备管理名称筛选与首屏加载

需求方确认项目、客户、办事处同时作为常用筛选和列表名称展示，隐藏页面“新增设备”按钮。既有 `GET /admin-api/pms/asset/devices/page` 增补可选 `projectName/customerName/departmentName`（最长128字符）：按名称包含匹配；空白忽略，与原SN、设备名称、状态、产品编码、projectId/customerId及服务端三路可见范围取交集，先筛选再分页、排序不变。办事处仅来自设备现有固化组织 `department_name`，不按设备所属项目重猜历史归属。合同候选解析使用同一名称限制；有名称过滤时不强制无筛选覆盖索引，允许优化器选择原精确归属索引。

列表响应增补可空 `projectName/customerName`，ID字段继续供已有消费者使用，设备管理界面不显示ID。AST只为已经授权的当前设备页批量读取名称，PROJ复用 `ProjectDeviceOrganizationApi.getOrganizations` 增补projectName，CUS复用 `CustomerQueryApi.getCustomers`，不逐设备读取详情、不直接联表跨模块。两Owner既有公开接口分别增加 `findProjectIdsByName(NameQuery)`、`findCustomerIdsByName(NameQuery)`，仅返回当前租户、未删除主档的筛选候选ID；候选不授予业务读取权限，AST仍重验原设备组织/项目/合同范围，空候选必须空结果，不得退化为忽略条件。名称缺失显示“-”，不显示ID或从其他关系臆造名称。详情与编辑、状态变更、关系历史及现有写权限保留，详情组件仅在用户打开时加载；设备创建API和其他调用方不因本页隐藏按钮而改变。

### 本机DAC任务复核的显式发起人（2026-10-22）

AST `ProjectDeviceSelectionApi.validateSelection(AuthorizedSelection{tenantId,actorId,projectId,deviceIds})` 供已校验任务的内部调用方复核设备，PROJ `ProjectDeviceSelectionContextApi.getContractNumbers(Authorization{tenantId,actorId,projectId})` 按显式主体重验当前项目范围和租户。原网页登录入口保持原校验。PLT采集中心从冻结任务的AuthorizationQuery传递发起人，不要求客户端提交网页登录Token，不设置模拟登录态，不弱化项目/设备/凭据/版本/票据校验。原任务继续使用原platformTaskId和本机幂等键；已接受的请求不能重复执行。前端本机失败提示区分授权、参数和网络未知结果，不输出请求或凭据。

### 2026-10-23 设备列表项目两行与详情链接

需求方要求项目列分两行展示项目名称和项目编码，并提供现有项目详情链接。设备分页只读响应增补可空 `projectCode`，复用PROJ既有 `ProjectDeviceOrganizationApi.getOrganizations` 同次批量查询的project_code，不从ID/名称推算，不增加逐行HTTP请求。名称/编码缺失显示“-”；仅有关联项目且至少一项真实展示事实时生成 `/pms/project-management/project-master-detail?projectId={projectId}` 链接，Long项目ID原样传递，目标详情继续执行原项目权限校验。该字段只读，不改项目关系、历史快照或设备查询权限。

### 2026-10-23 设备列表移除维保月数

按需求方批准，`GET /admin-api/pms/asset/devices/page` 列表响应移除 `warrantyMonths`；列表SQL取消关联 `ast_device_warranty`。`warrantyStartDate/warrantyEndDate/warrantyStatus` 继续直接读取 `ast_device`。分页、总数、筛选、排序、租户与组织/项目/合同可见范围、延保次数及详情维保查询保持原语义。

### 2026-10-23 割接读取性能契约补充

`DeviceQueryApi.getProjectDeviceSummaries(ProjectDeviceInstallationQuery)`批量读取当前直接归属项目的设备摘要，复用现有DeviceSummaryDTO；查询租户必须为当前租户。资产当前项目可见范围包含该项目时，同一次读取只解析一次项目范围，再按租户、项目和未删除条件读取设备；该条件是既有单设备可见性OR规则的充分条件。项目范围不包含该项目时，按原组织/合同/项目可见性在数据库批量读取指定设备，缺项仍按原设备顺序调用getDevice抛原错误，任一设备无权读取仍失败，不返回部分成功替代完整范围。没有跨请求权限缓存。IMP覆盖查询保留原锁定项目设备集合与逐项租户、项目、归属版本核对，不更改覆盖/完成判定。

2026-10-28项目内设备查询复用差量：`DeviceQueryApi.getVisibleDeviceSummaries(DeviceSummaryBatchQuery)`仅预取当前主体可见的指定设备摘要；tenantId/actorId须与当前受信租户和登录主体一致，缺失或不符失败关闭。deviceIds必传，空集合返回空；数据库对租户、未删除、指定IDs及原项目/合同/组织OR范围同时过滤，不返回无权/不存在条目。可选projectId仅为查询提示：PROJ按当前主体和租户重新确认目标PROJECT_VIEW完整范围后，以tenant/project/本次IDs单表读取；命中行跳过全局范围准备，只有缺项再走原OR范围；无FULL时整批走原OR范围，不凭提示授权。单getDevice复用相同受权直读，缺项仍为AST_EQUIPMENT_NOT_EXISTS；消费者仍按原顺序处理缺项、归属/版本及发货校验。DeviceSummaryDTO加性提供可空name/locationSnapshot，仅表示当前可见设备原字段，不改写历史快照。

`POST /admin-api/pms/asset/devices/current-summaries`复用现有`pms:device:query`，请求包含deviceIds数组及可选projectId提示，租户/主体从服务端取得，不接收权限集合；projectId不是授权凭据，AST必须按当前主体重新确认PROJECT_VIEW FULL后，才以tenant/project/本次IDs单表读取当前直接归属设备，不要求合同号非空、不附加来源或状态过滤。已命中行保留，只有缺项再按既有项目/有效关联/合同/组织OR授权查询；无FULL时整批沿原授权。返回上述已授权摘要列表，无权/不存在条目缺省以保持原批次部分补空语义。该读取不授予下载、编辑、任务办理或跨项目权限。Long标识沿既有大整数字符串序列化，前端保留number|string原值并以String精确匹配，不经Number/parseInt转换。CUT的P2/P4保持原ID/SN校验及可编辑边界，P3额外保持当前项目匹配，历史非草稿仍读取冻结内容。

### 2026-10-24 设备合同授权保留公司部门约束

Q-AST-DEVICE-CONTRACT-SCOPE-20261024经需求方确认：设备组织与明确项目授权继续独立OR；设备合同分支必须在COM提供的唯一、无公司冲突的合同组织事实中匹配同一条SYSTEM当前有效范围，公司相同且部门级授权须匹配部门ID或同公司精确部门编码，公司级授权不限定部门。禁止仅取部门级范围的companyCode放行整公司设备；项目—合同关联也不能绕过该合同组织约束。设备当前直接项目或有效设备参与关系命中明确项目授权时仍独立可见。列表、筛选、详情、档案、日志及下载复用相同判定，空组织范围或COM不可用关闭合同分支。COM合同目录、订单及敏感字段权限继续依原契约，实际用户范围、历史与设备组织投影不回填。

COM ContractDeviceVisibilityApi.ReadScope在设备专用只读上下文携带同一行companyId、departmentId、departmentCode的组织组合；配套Owner XML使用该组合匹配合同主档与发货合同的既有归属聚合。getVisibleContractNumbers两重载用于设备时执行相同组合限制并保留原合同号唯一、冲突、原文匹配、租户与空候选保护。此收敛替代设备合同分支仅companyCode的旧读取，COM其他查询契约不变。

### 2026-10-25 已关联成果的闭环锁定读取（已由批准闭环专用重验替代）

2026-10-31合并51a798245后，以下通用lockLinkedFact方案不再作为当前接口；使用源分支lockForApprovedClosure及其审批实例、冻结快照和审核人校验。普通关联及锁定仍执行lockAndRevalidate的原授权。

TaskBusinessObjectProvider.lockLinkedFact(Context, objectId, expectedVersion)仅供已校验项目任务、当前契约及活动关联的完成/闭环事实复核：沿用inspect的当前用户、租户、项目查看范围，锁定范围、业务对象及成果并核对版本，不创建或修改业务对象、关联或权限。默认沿用lockAndRevalidate的更严格限制；ACC满意度按项目可见性复核既有关联成果，不借用仅调查经办人工作队列查询。关联命令仍调用原lockAndRevalidate，工作队列经办人限制及命令授权保持不变。

### 2026-10-23 用户公司部门范围维护（需求方本次会话批准）

SYSTEM继续拥有既有`UserCompanyDepartmentScope`，用户管理新增“公司部门范围”入口。该补齐不改变`OrganizationScopeApi`、公司部门匹配、合同公司范围、项目资格及设备组织授权的既定含义，不由部门树推导公司。

| PMS Business API（统一admin-api前缀） | 方法 | 契约 |
| --- | --- | --- |
| `/api/v1/pms/user-organization-scopes` | GET | `userId`必填，`status`可选0/1；PageParam/PageResult，稳定按ID倒序，含停用历史。查看权限`system:user-scope:query`或维护权限`system:user-scope:manage`；目标用户须通过SYSTEM既有用户数据权限。 |
| 同上 | POST | 需维护权限；公司必选、部门可空；提交组织ID、既定scopeRole、isPrimary、生效及可空到期时间。组织编码/名称由SYSTEM回源，新增启用范围并返回ID。不得接受客户端租户、组织快照或状态。 |
| `/{id}/actions/revise` | POST | 需维护权限及完整保存体、expectedVersion；范围必须属于userId。事务内按版本停用原行并新增修订行，返回新ID；不覆盖原组织、角色、主标记、时间区间，失败整体回滚。 |
| `/{id}/actions/disable` | POST | 需维护权限，提交userId、expectedVersion；按版本停用并递增版本，保留历史。已停用或陈旧版本返回明确冲突；主数据停用不阻止撤权。 |

新增及修订要求用户/公司启用，部门存在时启用且具有非空统一编码；部门ID/编码同源保存。生效区间为左闭右开，到期须晚于生效；不新增范围角色枚举或主范围唯一性规则。角色编码为已有资格接口使用的稳定scopeRole（非空、最长32），维护不授予全局功能角色。租户由框架上下文隔离，用户/部门数据范围继续生效；越权/跨租户不可读写，不提供删除接口。业务写入沿用SYSTEM操作日志，记录操作者、时间、用户、原范围与修订范围ID；范围原行持续保留。

### 2026-10-24 设备合同授权保留公司部门约束

Q-AST-DEVICE-CONTRACT-SCOPE-20261024经需求方确认：设备组织与明确项目授权继续独立OR；设备合同分支必须在COM提供的唯一、无公司冲突的合同组织事实中匹配同一条SYSTEM当前有效范围，公司相同且部门级授权须匹配部门ID或同公司精确部门编码，公司级授权不限定部门。禁止仅取部门级范围的companyCode放行整公司设备；项目—合同关联也不能绕过该合同组织约束。设备当前直接项目或有效设备参与关系命中明确项目授权时仍独立可见。列表、筛选、详情、档案、日志及下载复用相同判定，空组织范围或COM不可用关闭合同分支。COM合同目录、订单及敏感字段权限继续依原契约，实际用户范围、历史与设备组织投影不回填。

COM ContractDeviceVisibilityApi.ReadScope在设备专用只读上下文携带同一行companyId、departmentId、departmentCode的组织组合；配套Owner XML使用该组合匹配合同主档与发货合同的既有归属聚合。getVisibleContractNumbers两重载用于设备时执行相同组合限制并保留原合同号唯一、冲突、原文匹配、租户与空候选保护。此收敛替代设备合同分支仅companyCode的旧读取，COM其他查询契约不变。
<!-- 2026-10-27：当前用户确认的任务维护与方案提交边界。 -->

### 任务完成后的持续维护与实施方案提交前置条件（2026-10-27）

- 实施方案草稿允许提前维护；提交审核必须满足当前有效计划中该业务任务冻结的准入规则。不能按阶段编号、展示顺序或可选任务推导额外依赖。已完成任务不恢复执行轮次、不改写历史完成事实。
- PROJ 提供 `ProjectTaskPrerequisiteApi`：按项目和业务 Owner/对象类型定位当前计划内唯一任务，复用冻结准入规则；返回 `allowed/blockers`。无法确定当前计划、契约或规则时不放行。未纳入该计划任务绑定的独立业务维持原入口权限。
- SOL 新增 `GET /api/v1/pms/solution-reviews/submission-readiness?projectId=...`，沿用方案查询权限及项目可见范围；POST 提交在原事务中重新加项目锁校验，发生于冻结来源、变更方案状态和启动审批之前。原提交/开始评审入口同样校验实施方案，防止绕过。
- 已完成安装任务提供当前项目的安装地址维护入口；新增安装记录维护生效历史，不修改完成记录。配置/联调日志仍通过原日志归档入口持续追加。业务权限、项目数据范围、停用视图和显式只读限制继续生效。

### 当前设备型号的唯一主档补充（2026-10-28修订）

需求方在TF-043继续明确按正式主档唯一键关联，替代此前TF-048的CRM/ACTIVE筛选、逐字节归一聚合规则：AST当前设备摘要、项目设备候选和已发货设备检索的productModel，在设备原值为空（含全空白）时采用同租户、未删除产品主档的非空型号；已有设备非空型号原样优先。设备产品编码沿用trim口径，与主档 `UNIQUE(tenant_id, product_code)` 按该键排序规则直接关联，主档索引列不包TRIM/CAST，不按产品分组、MAX/COUNT或任意排序兜底，不新增来源/状态限定。数据唯一性由真实主档约束保证，不能用LIMIT择一隐藏完整性错误。候选显示与型号过滤复用同一SQL表达式，计数与分页不放大设备行数；原有权限、项目与已发货条件保持。仅改变当前读取，设备主档、锁定命令、版本历史、已批准方案及其他不可变快照不回填。

### 项目设备导出（TF-039，2026-10-27需求方批准）

`GET /api/v1/pms/projects/{projectId}/equipment/export`由COM输出设备清单，`GET /api/v1/pms/projects/{projectId}/serials/export`由AST输出序列号详情。后者支持`sn`、`name`、`productCode`，匹配语义与当前页签一致；均不接受客户端行数据或分页上限。固定列xlsx，完整读取成功后才开始文件响应；后台按游标分批取数。独立权限`pms:project-device:export`与`pms:device:query`同时具备，且当前PROJ完整PROJECT_VIEW范围包含项目才可导出；权限种子授予PROJECT_MANAGER与现有SERVICE_MANAGER系列角色。办事处只读通道禁止导出。

设备清单仍按当前ACTIVE且未结束的交付范围、精确产品编码聚合，引用同租户订单行并去重，并保留COM既有公司/合同项目订单行读取范围；未知或无权读取的订单数量为空而非0，不以设备台数代替。产品名称/型号复用既有产品列表的按编码精确匹配、ID倒序首条展示口径，通过AST公开产品展示快照批量契约读取，不改变TF-048设备型号补充的唯一有效规则。序列号导出仅含当前项目内可见设备的既有展示列，未接入字段留空，不含密码/日志内容或下载凭据。

### 安装位置引用批准方案（TF-040，2026-10-27）

`GET /api/v1/pms/implementation/projects/{projectId}/planned-installation-addresses`只读返回当前项目最终批准方案的`solutionId/solutionVersion/solutionName`和`addresses[{productCode,location}]`，无最终批准方案返回null。沿用实施就绪Owner的方案选择顺序及重大方案最终审批判定；同时要求安装查询、方案查询权限和当前项目完整可见范围。只读取原部署位置，不写安装记录或设备。

新增安装时按产品编码trim后精确匹配；同一产品只有唯一位置且整个所选设备批次位置一致，才允许用户显式填入草稿。缺编码、无匹配或多位置不选任意值，原安装记录与生效历史不回填。

2026-10-28需求方批准TF-040按旧分组过滤逻辑实现，并进一步要求公共复用、数据库执行、信任主档及使用当前字段命名。AST公开`ProductFilterGroup`：`productCode/productModel/productDesc/productCategory/productType`各有Like/In/NotIn，子组为`alternativeGroups`；不保留旧item字段或拼写别名。SQL公共片段`pms.asset.ProductFilters`以`product`为主档别名，可由资产域其他Owner查询直接include，全部值使用参数绑定，不做Java产品匹配或聚合/强制转换防御。

`DeviceQueryApi.findProjectDeviceIdsByProductFilter(ProjectDeviceProductQuery)`为已获项目授权的跨模块调用方提供独立可用查询。Query包含当前`tenantId/projectId/productFilters/matchMode`；`MATCHING`返回条件为真的项目设备ID，`NOT_MATCHING`返回为假的ID，SQL未知值不进入任一结果。默认构造使用MATCHING；空条件返回空，跨租户/缺失项目/缺失模式拒绝；按设备ID升序。产品主档只按租户、产品编码及逻辑删除直接关联，不默认限制外部系统来源或产品状态，不增加按产品分组、MAX、COUNT、CAST或Java二次筛选。来源只用于追溯，状态也不自动成为业务准入条件；仅当某个业务明确需要时，通过对应Query可选参数表达来源/状态筛选，不在公共实现中写死。当前无此筛选需求，不增设未使用的参数。原设备锁定、展示、换货快照API不变。

IMP安装地址仅作为消费者读取参数`pms.installation.address.product.filters`的`productFilters`并用NOT_MATCHING取得免填ID；不把安装地址语义放入AST通用条件或SQL。`InstallationAddressCompletionApi.Fact`及GET `installation-address-completion`增加`requiredDevices/exemptedDevices/exemptDeviceIds`，`addressedDevices`为必填范围中已有地址数，`totalDevices`仍为全部设备数；完成条件为项目非空且必填全部有地址。原未配置行为不变，服务端每次重新读取参数核对，前端不得提交豁免标记替代判定。


### TF-011 项目列表人员筛选与成员授权确认（2026-10-30 用户裁决）

`GET /admin-api/api/v1/pms/projects/member-filter-options`复用既有ProjectSelectionController，仅接受field（MANAGER/SERVICE_MANAGER/SALES）、keyword（最多64字符）、pageNo及pageSize（1~100）；要求pms:project:query，额外主体、租户、授权IDs、项目范围参数拒绝。返回既有CommonResult<PageResult<ActiveUserSelectionApi.MemberOption>>（id/username/nickname/deptId/deptName；旧User契约不变），不修改旧simple-list与成员指派资格API。

用户批准将上述既有HTTP接口的候选改为当前租户全部生效指定系统角色用户，不再先收敛到当前主体可见项目的成员。内部新增`ProjectMemberRoleSelectionApi`，复制并复用新增成员底层SYSTEM的启用用户、未删除绑定、启用且未删除系统角色和显式同租户资格；不调用需要具体项目与写权限的详情成员HTTP，也不带其项目公司qualification。PROJ根据field固定映射MANAGER→既有MANAGEMENT_CODES（PROJECT_MANAGER、SERVICE_MANAGER及历史L1/L2别名）、SERVICE_MANAGER→SERVICE_CODES、SALES→SALES_CODES。SYSTEM对允许的角色代码白名单校验，空角色集合返回空；一次角色集合EXISTS查询完成COUNT、ID升序及数据库分页，不分别分页后Java拼接。候选查询重验当前tenant/login及pms:project:query，角色、主体、租户、项目范围不开放给HTTP输入。原ActiveUserSelectionApi与成员资格SQL不改。

项目列表的实际数据查询保持原规则：先resolveAllFullProjectIds，再按所选人员及对应原成员角色集合intersectMemberProjects求交。候选人员可见不产生任何项目读取、成员、指派或审批资格；选中没有合法项目交集的人员返回空项目集，不能放宽项目范围或空集条件。

历史专项实现保留：ProjectMemberIdentityScopeProvider专项SPI、PROJ普通FULL有效成员实现及原ActiveUserSelectionApi.pageProjectMembers不删除、不改语义；它们继续保留原tenant/actor及查询权限重验、Office/SUMMARY空候选和Owner范围限定。当前列表候选HTTP改用新角色API，不再调用该历史SPI。历史受限夹具与验收只证明当时版本，不能作为新角色候选全集预期。

用户裁决“未确认不授予，正常情况，这里的有效期应该和参与授权保持一致”：所有通过成员身份形成的项目FULL、任务读取/办理/经理资格均要求authorization_confirmed=1且处同一有效成员区间；旧Java FULL与公开Owner SQL一致。事实查询继续展示/筛选未确认成员，不新增完成Gate，不修改独立PLT Grant、组织、创建人只读、SYSTEM角色、显式任务指派或独立审批的既定来源。

新角色API明确DataPermission(enable=true)防止继承调用方的目录豁免，仅角色用户及COUNT/LIMIT SQL临时executeIgnore并finally恢复；显式当前租户和角色条件始终生效。部门名匹配和本页补名保持原Dept数据权限；有keyword时只从该角色集合的用户部门IDs检索可见部门，零keyword不扫描部门，零页不补名。姓名、账号、邮箱及可见部门名在同一COUNT/PAGE条件内匹配；响应仍只返回原MemberOption，不暴露邮箱、手机号。前端保留原UserSelect/scopedSource及HTTP路径，不新增平行选择组件；缓存标签不产生资格。旧授权SPI及原API继续按其原FULL候选规则执行。

新角色候选的实际账号、分页与浏览器一秒目标仍按本次运行验证，历史受限Owner耗时不直接套用；合成SQL与编译/单测不代表SLA通过，不展开全量用户后Java过滤。

部门名沿原SYSTEM DeptDataPermission范围展示和匹配：keyword非空时，新角色查询只在当前租户已通过角色资格的用户中SQL取distinct dept_id；原FULL查询仍仅在PROJ已裁决用户IDs中取部门。随后在普通部门权限下按这些部门IDs+名称匹配，COUNT/PAGE同一WHERE包含匹配部门IDs。同一请求复用已按普通权限取得的名称，分页后只补本页缺失名称，不额外要求部门启用（保持原getDeptMap行为）。零keyword省部门搜索，零页不补名；空集合关闭。ignore不包含Dept查询，退出恢复原上下文；保留Scoped部门标签，没有全量用户加载或Java用户过滤。

授权资格差量（NO-GO整改）：既有ProjectParticipantFactApi增加inspectAuthorization(ProjectParticipantFactQuery)、lockAndRevalidateAuthorization(ProjectParticipantFactRevalidationQuery)，复用原Fact返回及Query；前者要求显式subject并使用Owner当前时间，后者沿原project版本/生命周期/阶段与DB当前时间、user/role/id锁顺序。两者只接收已确认且当前有效成员，不能从manager_id指针回退出授权。原inspect/lockAndRevalidate事实方法、历史及主责指针解释保持。调用方仍独立校验功能权限与项目scope；新端口本身不形成Grant。阶段流程启动、闭环检查/提交、现行需求实体和动态表单经理准入使用授权端口；超管独立权限的真实主责经理证据分支保留。

### PMS现行业务人员邮箱搜索（TF-010，2026-10-30）

`GET /admin-api/api/v1/pms/user-selections`服务于原先采用普通启用用户目录的业务选择。要求已登录且登录主体租户与当前租户一致，沿用原`simple-list`无`deptId`分支的候选范围，不增加`system:user:query`管理权限要求。通过SYSTEM独立`BusinessUserSelectionApi`，在显式`DataPermission(enable=true)`上下文调用原`AdminUserService.getUserListByStatus(ENABLE)`与`DeptService.getDeptMap`，保留用户和部门的租户、逻辑删除及部门/本人数据权限，不用忽略数据权限的身份API补人；部门不额外新增停用过滤。原上游`UserController`、`UserSimpleRespVO`及接口不变。

接受`keyword`（最多64字符）、`pageNo`（从1开始）、`pageSize`（1~100）与可选`userIds`（最多100个正整数）。先在上述原候选集合中按姓名、账号、邮箱或可见部门名进行忽略大小写的包含匹配，再保留原列表顺序分页。`userIds`仅与该原候选集合求交，用于已选人员回显；未指定时不加ID过滤，显式空集返回空，不能扩大范围。当前实现复用原目录服务加载与部门补名，不新增数据库查询或缓存；不宣称大目录检索性能已验收。

返回`CommonResult<PageResult<Option>>`，选项仅含`id/nickname/username/deptId/deptName`，用户名用于统一显示“用户名-姓名”，不返回邮箱、手机号或其他账号详情。邮箱只参与服务端搜索。不可见、停用、删除和其他租户用户不因匹配或指定ID而补入；目录失败不回退管理分页或其他更大目录。该API不提供项目成员、角色、任务分派或审批资格，后续业务写入继续使用各Owner现有授权。首次接入项目阶段现行工勘和培训人员选择；配置调试人员按2026-10-31需求方确认改为本项目当前有效成员，复用经项目范围授权的成员接口，仅可选择关联了系统账号的成员，按用户去重；历史已选人员仍可回显。其他上游、旧入口及已有专项候选接口保持原契约。

配置调试新建及切换项目仍默认本人，切换项目时清空设备；默认值不构成成员资格。新建或更换项目、调试人员时，保存事务内通过PROJ `ProjectMemberSelectionApi.requireCurrentUser`锁定项目范围和当前有效成员重验，资格失效时拒绝写配置记录和日志归档。校验与原CURRENT成员范围一致，不限为项目经理或服务经理；未选调试人员的原可选语义不变。同项目中未变更的历史已选人员允许继续保留，不改历史，不凭历史人员扩大新候选范围。

培训历史人员回显优先复用已授权培训记录的`trainerName`姓名快照，不依赖启用人员目录；该快照只用于原已选值显示，不补入候选搜索。保留原人员ID编辑草稿时不提交或改写姓名快照，变更人员仍使用原服务保存逻辑和权限。缺失姓名快照的历史记录不臆造姓名，也不扩大人员目录读取范围。

组织限定候选增量：当前服务经理公司/办事处及L1/L2站点选择、工勘分工负责人和阶段任务指派候选改用独立内部`OrganizationUserSelectionApi.page(OrganizationUserCandidatePageReqDTO)`，复用既有请求、响应及场景Query。复制原组织候选读取后，只在原姓名/账号的关键字条件内增加邮箱包含匹配；公司、部门ID与编码、有效授权区间、启用/未删除、原租户及数据权限链路、分页上限和ID稳定排序保持。邮箱仅参与SQL筛选，不增加响应字段。旧`OrganizationScopeApi.pageActiveUsers`及其SQL保留原行为，`hasScope`等写入资格校验仍沿原接口；专项候选不接通用用户目录，不因邮箱命中建立成员、指派或授权资格。



### TF-022 工期办理资格与区域服务经理（2026-10-29 用户确认差量）

服务经理作为区域负责人可办理本区域内项目经理的工期操作；系统角色不能单独形成资格，也不因此伪造项目成员。PROJ新增内部场景化 `ProjectDurationOperatorApi.inspect(Query)` / `lockAndRevalidate(RevalidationQuery)`。Query含受信tenantId、projectId及当前登录userId，Owner验证租户和当前身份；Fact只返回projectId/userId/lifecycleStatus/projectVersion及真实basis，区分 `CONFIRMED_PROJECT_PM` 与 `REGIONAL_SERVICE_MANAGER`。PM分支要求同项目本人、authorization_confirmed、ACTIVE及当前有效区间；区域分支按锁定项目的公司和部门调用SYSTEM，不直接访问SYSTEM表。既有 `ProjectParticipantFactApi` 的展示、参与事实和审批事实语义不变。

SYSTEM在既有 `OrganizationScopeApi` 增加 `inspectRoleScope(OrganizationRoleScopeQuery)` 与 `lockRoleScope(OrganizationRoleScopeQuery)`，返回当前真实范围行或null。Query含受信tenantId/currentUserId、项目companyId/可空departmentId及requiredRoleCodes。范围与角色语义复用既有PROJ组织范围：启用用户、启用且未删除的SERVICE_MANAGER或既有L1/L2系统角色，以及同用户同租户同一条启用有效范围行中的SERVICE_CODES；指定部门精确匹配，授权行departmentId为空覆盖该公司全部部门。不得交叉拼接不同公司的范围，不增加companyCode/departmentCode及公司部门主档启用新约束。SYSTEM用户目录的普通部门可见范围仅在这两个精确主体的内部资格方法局部关闭，显式租户、主体、角色和组织范围条件始终生效，退出恢复原数据权限上下文。

PROJ与SYSTEM写重验方法均以MANDATORY加入原调用方事务。先锁项目并核对ACTIVE/projectVersion，再锁真实PM授权行或SYSTEM范围/用户/用户角色/角色匹配事实；SYSTEM当前读绕开请求缓存，与既有范围停用/修订及角色撤销写同一事实行形成竞争保护。读预检不授权写。SOL初次工期、变更create/patch/submit、allowedActions、该工期变更附件和申请人本人BPM撤回统一消费该资格，仍分别要求原功能权限及完整PROJECT_MANAGE（文件只读仍要求原VIEW与文件Owner规则）。附件读取保留原项目PM/服务经理审批人读取分支，仅增加经新Owner确认的区域服务经理分支；不扩展其他业务文件。

本差量不更改阶段规则、审批节点/审批人选择、冻结审批人、本人撤回、状态机、CAS、幂等、文件安全或不可变历史。写资格不能替代审批资格；不得据此迁移历史或宣称部署、原问题样本或浏览器业务闭环完成。


TF-022入口整改补充：项目详情的阶段任务读取复用既有`ProjectOrganizationScopeService`的区域PROJECT_MANAGE集合，区域读取不会把`projectManager`身份置真。已冻结为`BUSINESS_OBJECT / PLN / STAGE_PLAN / STAGE_PLAN / PLN_STAGE_PLAN_APPROVAL / REFERENCE_EXISTING`的施工计划任务，可由该区域服务经理取得该Owner业务办理上下文；仍要求原任务查询/执行功能、对应工期或施工计划业务功能、EDIT范围、ACTIVE与原非终态规则，以及当前执行轮次writable/版本。`ProjectTaskBusinessService.executionAllowed`和`ProjectOperationContextResolver.permitted`只在该精确绑定使用这项增量。通用TaskExecutionPolicy、任务START/COMPLETE/审批、关系维护writable与结果刷新executable均保持原规则，不伪造任务受派身份或项目成员。

首次工期另调用`ProjectDurationOperatorApi.lockInitialAndRevalidate`：保留既有“项目已有当前PM”前置。旧首次入口的ParticipantFact锁调用明确传入当前actor，原实现只有subject为空才允许manager_id投影回退，因此区域分支不得单凭manager_id代替有效PM成员事实。新首次方法在原事务与项目锁内额外确认同项目当前已确认、ACTIVE且处有效区间的PROJECT_MANAGER行；缺失拒绝。后续工期变更、附件和撤回不增加此首次专用前置。专项SQL和Owner实现由原模块持有，无跨业务表访问。

### TF-024 项目产品型号引用（2026-10-30）

`GET /api/v1/pms/delivery-scopes` 的可空 `productModel`、`productName` 表示当前 CRM 产品主档展示值，不是范围、订单行或方案的历史快照。2026-10-30需求方明确要求在查询源头SQL直接关联主档，并批准本次对模块表读取限制的特定例外：仅COM交付范围当前分页及同范围产品清单导出，可只读关联AST的 `ast_product_official_info`；其他跨域表访问不因此放开，表Owner仍为AST，集成同步仍是原写入来源。两查询保留原功能权限、租户、项目VIEW及各自订单行权限条件，以 `product.tenant_id = scope.tenant_id AND product.product_code = scope.product_code AND product.deleted = 0` 原生等值关联，沿已认证的 `UNIQUE(tenant_id, product_code)` 保持一范围一行。禁止程序二次反查、TRIM、产品关联类型/字符集转换、重复型号投影和臆造的择一防御。主档无记录保留空值，不新增来源/启停状态过滤；数据库错误向调用方传播，不能伪装成成功的空字段。

项目详情产品清单直接使用上述受授权展示字段，名称仍优先范围 `productDesc`。实施方案引用保留现有产品集合、设备型号优先、SN 和数量口径，仅在设备型号缺失时使用范围返回的主档型号；范围读取失败显示加载失败。两入口均不调用要求换货权限的产品目录接口，不增加角色权限，不改范围 DO、订单事实、既有方案正文或不可变历史；方案内容仅在用户显式引用后进入当前编辑草稿。

同类设备当前展示统一在原授权源SQL关联同租户主档：设备列表、项目候选、已发货设备、单设备及授权批量/项目摘要使用同一关联，型号、产品名称、产品描述各保留设备非空原值优先，否则使用主档原值；主档仅按租户、产品编码及逻辑删除关联，不新增来源或启停过滤，不再调用补查后在Java合并。序列号清单导出复用当前产品名称投影，维保、网络、权限和游标不变。产品编码与型号在前端引用、SN归集的编码键、清单及导出分组中保留原文，不做trim，不能把 `A` 与 ` A ` 合并；SN自身规则、订单号与描述展示、公司/合同授权的既定逐字比较不变。按2026-10-30用户裁决，TF-043项目选择的独立productModel筛选也统一当前型号，keyword是否为空不再改变型号来源，其他权限和筛选条件不变。

当前摘要 `DeviceSummaryDTO` / 前端 `DeviceSummaryVO` 增加可空 `productDesc`，单设备详情的summary、授权批量current-summaries及项目摘要均传递同一当前描述；既有字段和Long序列化不变。DeviceTag和联调两页的只读型号/SN展示复用受原权限约束的 `GET /admin-api/pms/asset/devices/{id}` summary，不将展示值合并进业务保存对象。`archive-record`继续返回设备档案原始值，仅补充原始 `productDesc` 返回字段；档案编辑、写入、锁定、版本及历史读取仍使用原链路，不使用当前展示值回填。物料设备选择器“型号”仅展示范围的主档 `productModel`，缺失显示 `-`，不将productDesc当作型号；名称、描述及保存引用语义不变。

物理前提已于2026-10-30按用户授权完成：目标178库五个产品编码列统一为 `utf8mb4_unicode_ci`，现17个product_code列字符集/排序集一致；五表完整行摘要、其他列、索引、外键、触发器和Flyway记录均未改变，交付范围分页与导出的生产XML原生关联已通过。执行及校验见[五列执行收据](../generated/2026-10-30-feedback-followup/product-code5-execution-result.json)和[原生关联收据](../generated/2026-10-30-feedback-followup/product-code5-post-native-joins-readonly.json)。此前1267是调整前历史证据，保留在原跟进记录，不再作为当前阻塞；既有缺主档记录保留空值，不清洗历史或通过SQL转换、程序反查、连接排序集规避。数据库/SQL验证不代替业务页面验收或部署证明。
