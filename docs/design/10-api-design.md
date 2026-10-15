# SDS Phase 2：API 设计

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；接口组在第 5～14 节回指具体 Requirement
> Owner：SDS Phase 2 应用与接口架构
> 前置设计：`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`

## 1. API 边界与版本

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

- Business API 的物理前缀为 `/api/v1/pms`，不叠加 `/admin-api`；沿用平台身份、租户、`CommonResult`、分页和 Long/时间序列化。写命令携带 `Idempotency-Key`，修改已有任务或规则携带 `If-Match`。同键重试固定原载荷与原版本，未知执行结果不换键重新执行。
- 任务 `POST /inspection-tasks`、`PUT /inspection-tasks/{id}/preparation`；`POST .../actions/{start-attempt|assign|cancel}`。设备清单在创建时冻结，方式和规则修订在执行尝试开始时冻结。日志、结果、报告、标注及处理记录分别保留版本；报告文件格式为 PDF/DOCX/XML，人工上传兼容 DOC。
- AST 在既有 `ProjectDeviceSelectionApi` 增加对象查询 `pageSelection(SelectionQuery)`，返回授权项目及其关联合同的分页设备资料。写入仍使用 Owner 的 `validateSelection` 锁定复核，不从客户端设备名称或 SN 建立真值。
- 新巡检设备候选由AST在Mapper XML中合并当前项目及关联合同的设备ID，去重后按原ID倒序分页；各分支和回表均保留租户、删除及筛选条件，空项目范围返回空，既有设备页面查询保持原行为。产品类型读取沿用ADR-0036的受信模块主体及当前用户设备范围双重校验；local配置仅登记INSPECTION的`PRODUCT_TYPE_READ_CODES`和`DEVICE_PRODUCT_TYPE_READ`两个读动作，不授予导入或业务写入权限，也不替代权威产品类型与设备映射。
- 离线人员在平台外执行命令，上传 `.log/.txt/.zip`（50 MiB）。日志进入存储前使用 DAC 现有 `DeviceLogInputNormalizer` 脱敏；增加经 namespace/project 授权的无持久化 `POST /api/v1/log-parses/actions/normalize`，由 INT `DeviceLogParsingApi.normalize` 调用。返回脱敏命令块、识别的 SN 与警告，不能据此宣称设备执行已验证。输入原文不进入 NPDMS 数据库、审计或文件存储。脱敏后文件由 PLT 注册，执行提交使用精确文件版本及任务/设备/尝试上下文；不要求用户提供结构化结果包、脚本或脚本摘要。
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


### 需求分析展示投影（2026-09-22，PRE-04 / F-SOL-003）

按需求方明确的能力与展示分离原则，新增只读`GET /api/v1/pms/requirement-analyses/revisions/{revisionId}/presentations`，返回本版本可用的展示候选及可选默认模板；可选stageId/taskId互斥，仅指定默认展示上下文，不改变业务对象。使用既有需求分析query权限、当前租户及项目数据范围；不提供通过展示切换写业务数据的出口。候选字段须映射到SOL既有实体/扩展定义，附件使用SOL稳定业务用途码，停用/未发布或不兼容模板不参与。客户端基础业务表单始终可用；显示选择只影响当前页面。

PLT共享`EntityPresentationApi.list(Query(target, actor, categoryCode))`基于实体Owner读取权限列出同租户启用模板的当前发布修订，返回模板标识/名称与只读Schema，不创建或修改表单绑定、扩展定义或业务对象。SOL负责用途/字段兼容及别名映射。来源模板的历史绑定保留为来源证据，不决定操作资格、成果有效性或附件归属。


### 实施方案分级复审接入（2026-09-22，SCH-05 / F-PROJ-009）

新增 `/api/v1/pms/solution-reviews`：GET 按 projectId、solutionId 读取版本关联；POST 按 expectedVersion、processDefinitionId 提交，审批人由系统按节点职责自动解析（服务经理节点取本项目唯一有效服务经理，工程管理部节点取 `pms:sol-solution:major-review` 显式授权的唯一持有人），提交人不再指定候选人，且不排除申请人本人。GET `/definition` 返回部署定义和职责元数据；POST `/refresh` 回源重验 BPM 终态；POST `/revise` 从终态复制新的方案草稿。查询使用方案 query 权限与项目范围；写入使用相应 create/update 权限、当前项目经理资格和租户，旧接口不能修改正在分级审批的对象。重复同版本、同定义的请求返回同一在审记录，冲突请求拒绝；解析结果（节点→审批人）随在审记录落库供审计。

SOL 通过新增 BPM `SolutionReviewBpmApi` 读取实际定义、发起和读取历史，BPM 原创建、权限、任务处理与历史不改写。初审候选人重验项目服务经理关系；工程管理部复审须独立显式权限 `pms:sol-solution:major-review` 及项目范围。定义和完整任务 key、职责均来自模型；候选人通过 BPM identity links 冻结，任务操作前重新校验。只有真实结束且所需人工审核历史完整才返回批准；结果在 BPM 原事务内经受信事件由 SOL CAS 形成批准基线及交付件，不直接推进项目阶段。

### 项目工前确认历史（2026-09-22，F-PROJ-009 更正）

需求方明确从代码中剔除“历史工前确认记录”。`/api/v1/pms/projects/{projectId}/preparation-confirmation` 的 GET / POST 均删除，不再提供兼容入口；对应业务服务、表单策略、实体和 Mapper 一并移除。原数据库记录及已执行迁移保留归档，不再由应用读取。

工前数据统一沿用 SOL 工勘原有读写、字段目录、表单绑定及确认命令。独立支线的显示条件由工勘表单修订配置，不新增项目字段来源或跨模块读表接口。

方案复审另提供 GET `/api/v1/pms/solution-reviews/source?businessKey=...`，仅返回当前租户、当前项目范围内对应真实审批关联的只读方案正文，供 BPM 审批页查看。

GET `/api/v1/pms/solution-reviews/policy?projectId=...` 只读预览服务器计算的审核级别与不可用原因。内部 `ProjectFieldRuleApi` 按项目冻结模板修订和规则 key 读取既有发布程序，只接受实体 FIELD/CONSTANT 条件，不从请求接收业务事实或重新编译历史规则。读接口校验当前主体与项目范围；提交用事务锁定项目版本的求值接口。返回配置是否存在、各规则三值结果及规则/项目事实快照。无配置与配置不完整分开处理，未知结果不按普通审批放行。

未绑定模板的导入旧项目返回未配置并保留原 SOL 行为；存在部分模板引用而无法验证精确修订时仍返回不可用，不据损坏引用选择普通审核。读取服务在业务调用时解析原发布 Reader，避免发布校验器发现 SOL Provider 时产生初始化循环依赖。

### 设备配置日志文件归档（2026-10-06，EXE-03 / EQP-02 文件增量）

新增 `/api/v1/pms/device-configuration-log-archives`：POST `/imports` 以 `application/octet-stream` 接收UTF-8日志字节流，query传 `projectId`、UUID `operationId` 和 `fileName`；GET `/imports/{id}` 返回不可变导入结果及关联文件；GET `/latest?projectId` 仅返回当前项目已归档日志的设备及各自最新可用日志；设备没有可用文件时保留最新归档记录并标记不可用，不返回无归档设备；GET `/history?projectId&deviceId` 返回全部设备文件版本；GET `/{fileId}/download` 重验权限后签发原 PLT 下载票据。导入需要 `pms:device-log-archive:import` 与项目 EDIT 范围，查询需要 query 与项目 VIEW 范围，下载另须 download 和文件平台权限。导入返回 `ARCHIVED` 或 `REJECTED` 处理结果；它们不是配置生命周期状态或配置完成事实。拒绝结果不包含原始内容或Secret。入口最多从内存流读取20MiB加一个超限判断字节，拒绝空输入及超限输入，不使用会将原文落盘的multipart临时文件；保留操作审计但关闭请求/响应正文记录。

INT公开 `DeviceOpsLogSplitApi.split(Command)`，DAC实现内容拆分；命令携带当前租户、项目、输入格式与瞬态内容。结果只包含脱敏完整来源、按唯一整机序列号分组的完整日志及脱敏数量。调用方仍须独立授权与校验设备归属。AST公开 `DeviceConfigurationLogArchiveApi.receiveCollection` 承接IMP验证后的配置结果；PLT公开 `DeviceLogFileApi.readCollection/store` 重验源任务、结果、当前IMP Owner和发起人，复用原PLT文件校验/扫描/存储/引用与统一材料。后台接入使用原任务显式actor，不伪造登录主体。Infra以新的内部 `FileStorageContentApi.read` 读取已登记的receipt内容，核对当前租户、长度和摘要；PLT必须先按已授权FileVersion定位该receipt。此API不创建HTTP入口，不改变上游FileApi，不允许业务模块直接调用Infra Service或读取其表。

DAC新增 POST `/api/v1/npdms/log-splits`，只接受 `TERMINAL_TEXT` 或已验签来源的 `DAC_CALLBACK` 内容，重验JWT execute scope、namespace和project claim；不下发设备命令。输入上限复用采集日志20MiB限制。422仅返回固定失败code及可选内容块序号，不回显日志。配置采集成功不等于此归档成功，归档成功不等于业务配置完成。

采集回传沿用原 `pms:imp-configuration:update` 消费权限及当前项目EDIT范围，PLT通过原配置来源适配器的CONSUME入口锁定重验；该入口不向主体授予手工日志导入权限。手工导入还须 `pms:file:upload`，在调用DAC前重验；下载还须 `pms:file:download`。新的归档权限由已有授权管理显式配置，迁移只初始化菜单，不自动给业务角色授权。归档时间沿用平台EpochMillis，标识沿用Long安全序列化。

### 日志解析记录与在网版本（2026-10-07）

按用户明确确认，在网版本由最新设备日志解析结果生成，来源为 `DEVICE_LOG`。AST 通过 `DeviceLogParsedFactApi.record` 在项目 EDIT 范围内按租户、序列号及当前项目关联设备，保存不可变解析事实，并在同一事务中更新设备软件版本及在网版本投影；不以 ITR 事件时间判断日志新旧。归档日志按设备版本序号排序，其他上传/采集日志按原始来源接收时间排序，重新解析旧日志不改变其来源时间。未识别软件版本、序列号不匹配及历史日志分别展示结果，不伪装成版本更新成功。

GET `/api/v1/pms/device-configuration-log-archives/{fileId}/parse` 返回每设备归档文件的解析状态、关联结果和版本更新状态；GET `/pms/asset/devices/{id}/log-parses` 返回授权设备的结构化解析记录。归档事务提交后创建独立解析任务，后台在原租户和原操作人授权范围内查询结果并投影；不重新执行设备命令。解析 SN 与不可变归档 SN 不一致、文件不可用或文件归属不一致时禁止更新。原历史来源与 ITR 事件记录不被改写。没有正式语义字段时不猜测 CONP 派生属性或 BOOT 版本。

### 项目安装批量创建（2026-10-08用户确认）

`POST /api/v1/pms/implementation/installation-records/batch`：接收 `projectId`、非空且不重复的 `equipmentIds` 及共用安装信息、`installLocation`、可选 `locationMaintenance`，返回按设备顺序排列的安装记录ID。权限为 `pms:imp-installation:create` 且当前项目具有 `PROJECT_EDIT` 完整范围；服务端整体校验设备归属，在一个事务内新增安装记录，地址/站点/任意深度位置链只维护一次，后续记录引用相同地点ID与版本。任一步失败整批回滚，不改变旧记录、既有单条API或安装状态机。手动地点沿用 `UNRESOLVED` 兼容文本语义。前端只提交一次请求，不循环调用单条新增API。

2026-10-08用户追加确认：项目安装批量保存即安装完成。批量事务创建记录后，服务端衔接现有开始、完成状态动作并执行AST设备位置生效；任一设备的位置校验或生效失败也整批回滚。前端不再提供开始/结束动作，历史记录从设备操作列进入只读抽屉，地址变更通过新增安装记录保留历史；旧单条API及旧记录状态不做追溯改写。

### 2026-10-09 序列号分页批量展示

GET `/pms/asset/devices/page` 增补bootVersion、cpldVersion、warrantyMonths、extendedCount，沿用原设备可见范围；boot/cpld只取DEVICE_LOG来源，续保次数统计全部未删除续保记录，列表分页不逐台读取设备详情。

GET `/api/v1/pms/implementation/installation-records/current-addresses`：projectId及equipmentIds（1～200个正整数）返回当前已完成且effectiveFrom存在、effectiveTo为空的设备安装地址。要求pms:imp-installation:query和项目VIEW范围，租户及项目条件在服务端生效；无匹配为空，不返回历史或其他项目数据。序列号页每个不超过200条的分页只需设备页与当前地址2次业务请求，旧详情、维保和安装历史接口保留。

### 2026-10-15 割接详情式新建与项目已发货设备选择

需求方确认：新建割接任务直接进入详情式新建页，支持先选项目或先输入设备序列号；项目资料自动回填，设备序列号、产品编码、产品型号独立按行展示。仅新建阶段可增删设备，创建后的设备范围保持只读；原创建事务、活动设备唯一性、来源版本及项目嵌入入口的执行上下文校验继续生效。

- CUT 新增只读 `GET /api/v1/pms/cutover-tasks/creation-projects?keyword=...`、`GET .../creation-projects/{projectId}` 和 `GET .../creation-projects/{projectId}/devices?keyword=...&pageNo=...&pageSize=...`。均要求 `pms:cutover-task:create`；项目选项和详情只在当前租户、当前主体 `ACTION_EDIT` 范围内返回。设备分页由 AST 提供，固定限制当前直接归属项目、未删除、非空序列号且 `shipmentTime` 存在，不能借用关联合同扩大到其他项目。
- CUT 新增只读 `POST /api/v1/pms/cutover-tasks/creation-devices/resolve`，输入 `serialNumbers`，复用原上下文解析与授权，返回项目候选及设备产品资料；无发货事实的设备不能成为本选择器的可选项。唯一项目候选自动回填，多候选仍显式选择，不能静默丢弃其他项目设备。提交继续使用既有创建接口及其来源水位。
- PROJ 在既有 `ProjectBusinessContextApi` 增加 `presentation(Query)`，返回经项目范围校验的当前项目展示资料及有效成员名称；独立于冻结 `Context`，不改变既有来源相等性、快照或锁定契约。AST 在既有 `ProjectDeviceShipmentApi` 增加 `pageDevices(DeviceQuery)`，对象传参并返回分页结果；租户及当前主体必须有效，Owner 再校验项目可见范围。
- 查询不创建任务、不修改项目或设备归属。产品编码、产品型号从 AST 读取，项目名称、经理、销售、行业及级别从 PROJ 读取，客户服务等级沿用 CUS 正式事实；缺失信息不猜测。旧接口、旧新建向导原件及已创建任务历史保留。

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


### 2026-10-09 FB-05交付范围的订单合同只读回显

`GET /api/v1/pms/delivery-scopes` 增补可为空的`contractNo`，由COM使用当前已授权分页范围ID、当前服务端租户，通过范围→订单行→订单的稳定ID读取。关联表租户和逻辑删除全部生效；空范围失败关闭，来源缺失不补项目主合同。该字段为只读当前订单事实，不修改范围、订单、审批或历史快照；分页、项目VIEW权限和原响应字段保持。工程物料选择消费此字段，产品名称/型号使用既有`productDesc`，不把换货目标产品快照当作原订单产品。

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

### 2026-10-13 割接二线与研发角色共享审批

需求方指定二线 `probSupporter`、研发 `probRD`，确认按角色审批、不指定具体人员，并随后否决冻结人员池的设计。新节点以明确的列保存角色/候选组引用，由BPM原生candidateGroups建立一个未领取的共享待办；不将角色展开为全量candidateUsers，不保存候选人员清单或整组成员水位。待办读取及办理使用SYSTEM提供的当前有效成员资格，角色无人时待办保留，不跳过或自动通过。原个人审批、旧流程定义及不可变历史保留。

P5详情Node保留可选 `candidateRoleCode`；共享节点办理前originalApproverUserId/currentApproverUserId均为空，决定后currentApproverUserId为实际办理人。我的待办、首页待办及详情读取均以当前有效BPM任务及CUT当前节点为依据；客户端不选择或回传角色成员，角色节点不接受个人改派。现有approve/reject、身份、版本和五项评审不变。办理事务重新核对并锁定该操作者当前资格，领取与决定同事务，记录实际办理人及其当时资格来源；其他成员并发办理不能形成第二个决定。越权尝试不得将共享审批写为HOLD。

BPM新定义的二线/研发使用原生候选组共享任务；现有平台角色策略、角色查询API和任务服务按职责复用，不能随机指定一个人替代角色共享待办。CUT不直接访问SYSTEM或Flowable业务表。领取与决定仅允许经CUT业务校验的同一事务；通用BPM领取、审批或变量修改不能绕过CUT。流程初始化产生新定义版本，既有实例仍引用原定义。通知按发送时有效的收件资格生成幂等消息记录；消息收件记录不作为审批候选名单，通知送达不等于审批完成。流程保存业务身份和角色引用等明确的标量变量，不序列化全量人员池。

首页任务摘要保留 `approvalOnly`：角色待办通过专用审批页读取冻结资料。首页以原项目范围或精确待办任务ID限定分页及计数；自建、创建、P4、P6写入仍用原权限。角色临时读取只在未暂停、未替换的PENDING审批根及其当前有效角色待办存在时成立，节点未轮到、已办理或实例关闭后不再授予；不扩大到完整项目、同项目其他任务或历史审批。冻结文件关系按明确的列/明细保存，继续由PLT核验当前权限、完整业务引用键、固定工件和文件版本及可用性。CUT读取授权水位只用于事务内复核，不绑定文件登记时范围版本；闭环附件和文件写入不由角色待办授权。

V463仅为既有角色补充必要入口与查询/审批权限；V464补充PLT文件下载与预览按钮权限，Owner策略继续限定冻结文件只读范围。不分配用户、修改role.data_scope或PROJ项目成员。租户范围与服务端当前角色资格持续校验。


### 2026-10-14 模板正常闭环任务完成契约

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

`GET /admin-api/pms/customers/{customerId}/inspection-history` 复用客户查看权限和客户数据范围，并以操作者当前可见的原项目集合在分页前筛选；空范围返回空。详情仍由原SRV接口再次授权，引用链接不授予巡检或文件访问权限。客户历史与巡检已归档内容只读，下载精确工件版本，不用当前文件替代历史版本。

巡检误报接口位于 `/api/v1/pms/inspection-rules/feedback`：按标注ID保留每次误报历史，最新标注不再为误报或报告被新版本替代时标为历史，不删除。管理员的有效性确认独立追加修订，If-Match为该标注最新确认修订号（未确认是0），不改巡检已归档真值。查询按当前项目范围裁剪；普通查询只返回本人标注，具有`pms:inspection-feedback:analyze`时可分析授权项目汇总。`details`字段权限控制设备标识、型号、原始命令和输出摘要。

统计按规则修订分别展示，统计期为检出结果生成时间，分母为同修订的异常检出结果数，分子为其中当前报告最新误报标注且经管理员确认有效的不同结果数；历史标注与重复报告不重复计数。确认筛选只裁剪反馈列表，不改变统计分母。结果同时返回统计期和本次数据截止时间，查询失败不发布部分比率。规则优化仍通过现有复制修订草稿及发布流程完成。

Excel/CSV导出先创建来源快照记录，再生成平台文件；记录筛选列、截止时间、条数、精确标注/确认引用和当前/历史状态。单次10000行及50MB是导出资源上限，超过时明确失败并要求收窄筛选，不静默截断。失败可重试，下载重新核对原导出人、当前分析/导出/字段权限和全部来源项目范围。固定业务筛选及来源关系均使用列和明细，不存JSON业务结构。

巡检在线来源绑定SRV的单次设备执行记录，预检与正式执行各自拥有独立记录和CollectionTask。Owner通过统一采集`Source`返回可选的冻结执行规格：`PRECHECK`只有连接与认证测试、命令列表为空；`INSPECTION`按SRV冻结规则顺序形成整批命令。按2026-10-15用户补充裁决，本次暂不实现逐命令超时与继续策略，正式执行复用现有整批下发和整体超时配置`manual-execution-timeout-seconds`；未来确有独立超时需求时可拆分采集任务。规则原有版本与配置历史保留。未提供该规格的既有实施、割接与独立中心沿用原语义。命令与用途均由Owner决定，HTTP输入不能替换冻结内容。

PLT在创建任务前先解析当次临时认证或重新核验保存凭证，再将不含Secret的实际连接快照交由Owner核对预检设备、操作者、端点、认证模式、用户名、凭证版本及授权引用。正式执行授权独立签发；临时密码不进入幂等摘要、持久化、审计或队列，完成请求后清空。用途单独存列，并进入DAC幂等指纹和任务HMAC授权绑定。DAC的预检工作分支仅调用协议连接测试，不执行巡检命令；正式执行使用原有整体超时行为。
