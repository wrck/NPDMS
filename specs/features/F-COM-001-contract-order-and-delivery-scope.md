# F-COM-001 合同订单副本与交付范围管理 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO`
> 实施状态：`NOT_STARTED`
> Requirement：`COM-01（V1）`
> Requirement切片覆盖：`COM-01@V1=FULL`
> 关联Requirement：`INT-01`、`PM-02`、`PM-04`、`EXE-01`、`ACC-04`；不宣称关联Requirement完成
> Owner Context：`COM（合同订单履约）`
> 外部协作Context：`PROJ`、`AST`、`IMP`、`ACC`、`PLT`、ERP集成Owner
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> Technical Plan：Feature Ready状态回写后生成一个当前有效计划并独立送审

## 1. 业务目标

形成可独立使用的COM-01 V1闭环：平台保存ERP合同、销售订单和订单行的只读权威副本，合同管理员完成来源核对与项目关联，项目经理在授权项目内分配、调整和释放订单行交付范围；系统校验权威数量、地点、设备/型号维度、并发版本和已进入验收的保护边界，并向PROJ、IMP、ACC提供稳定的当前已分配范围事实。

ERP不可用不阻断无关项目内部流程。无权威数量时记录保持`PENDING_AUTHORITY`，可以查看和核对，但不得进入可分配量、正式验收范围或`getAssignedScope`成功结果。第三方ERP连接、认证、调度和对账运行不在本Feature实现，只冻结COM接收本地权威副本的公开输入契约。

## 2. Scope

### 2.1 包含

- ERP合同、销售订单、订单行本地只读副本及来源键、来源版本、同步时间、权威状态；
- 授权人工补录的待核对候选，明确标记人工来源和证据，不冒充ERP确认事实；
- 合同—订单多对多关系、项目—合同关联及项目交付范围查询；
- 订单行到项目的分配、释放、调整和冲突冻结，保存主记录与地点/产品/型号/设备/SN明细；
- 合同订单与范围管理工作台：列表、详情、来源状态、已分配明细、剩余量和冲突处理入口；
- `DeliveryScopeApi.getAvailableSlices/previewSplit/applySplit/getAssignedScope`稳定公开契约；
- 数量、单位精度、结构化地点、明确SN、项目资格、版本、幂等、权限、审计和Outbox；
- ERP取消、退货、减量或版本变化导致的超分配识别与`CONFLICT`冻结，不静默删减已生效范围；
- 旧来源数据的证据化前向迁移和不可迁行隔离；
- 菜单、权限、字典、真实MySQL与真实浏览器正向闭环。

### 2.2 不包含

- ERP/CRM网络连接器、认证、调度、游标、传输重试和跨系统对账运行；
- COM-02履约回写、发货回执、财务付款、开票、库存或备件业务；
- 修改ERP权威合同、订单、产品、数量、金额或来源状态；
- V2自动指派、自动范围匹配或V3能力；
- 直接访问PROJ、AST、IMP或ACC业务表；
- 把CRM合同页面、附件/XLSX、测试种子或旧状态值升级为ERP权威事实；
- Deployment、SIT、UAT、Release或F-IMP-002 Implementation Done声明。

## 3. 业务规则

### BR-FCOM001-001 权威身份与来源

- 合同业务身份为`tenantId + companyCode + contractNo`；销售订单和订单行按ERP稳定来源键幂等，来源版本只能前进，重复同版本同摘要为重放，同版本异内容进入冲突隔离。
- ERP拥有合同、订单、订单行、产品、数量、金额和来源状态；COM只保存只读副本。`authorityStatus`只表达数量是否已获权威确认，`sourceLifecycleStatus`独立表达`ACTIVE/CANCELLED/RETURNED`，两者不得互相推断。CRM经营引用独立保存，不覆盖ERP字段。
- 授权人工补录只追加`com_authority_candidate`候选，保存对象类型、不可变`PLATFORM_MANUAL`来源键/版本、完整候选载荷、证据引用和操作人。核对只能把候选关联到已经存在且为`CONFIRMED`的ERP Owner事实，候选进入`MATCHED`但不改来源键、不复制或晋升为权威主档；拒绝保留历史。
- 外部Owner不可用时保留最近一次成功副本及状态，不用空响应覆盖；来源缺失或损坏失败关闭。

### BR-FCOM001-002 关系与查询

- 合同与销售订单使用关系记录表达多对多，不在订单头固化唯一合同；项目可关联多个合同/订单，实际交付边界以订单行到项目的当前DeliveryScope为准。
- 合同管理员只能维护其`OrganizationScopeApi`当前有效公司范围内的关联、候选核对和冲突处置；Owner `companyCode`必须精确命中同一有效范围行，无项目关联合同也不得越出该公司范围。项目范围写入必须同时满足功能权限、`ProjectScopeApi.ACTION_EDIT`和`ProjectParticipantFactApi`对受信actor返回的current `PROJECT_MANAGER`事实；普通参与、全局角色或单独ACTION_EDIT均不能替代项目经理。
- 项目、合同、订单、订单行任一不可见时返回不可见/不存在，不通过全局角色或前端按钮扩大数据范围。

### BR-FCOM001-003 范围分配

- 分配命令携带受信租户、项目、期望`scopeVersion`、幂等键和明细；明细至少包含订单行、数量、单位及地点或产品/型号/明确SN维度。
- COM按稳定订单行ID升序锁定；仅`authorityStatus=CONFIRMED`且来源当前有效的订单行进入可分配量。有效分配总量不得超过ERP有效数量，单位和精度必须一致。
- 同一项目—订单行同一时点最多一条当前主记录；明细数量合计必须等于主记录数量。SN按AST正式规范化身份去重，并通过AST公开契约校验租户和项目归属。
- 地点优先保存AST稳定`siteId/siteLocationId`；只有站点未维护时可保存文本降级并标记`UNRESOLVED`。未解析文本不得参与正式站点统计或结构化权限判断。
- `previewSplit`只返回校验结果和当前版本，不写业务事实；`applySplit`在同一事务写范围、明细、审计、幂等完成点和`DeliveryScopeAssigned/Released` Outbox。

### BR-FCOM001-004 释放、调整与来源变化

- 释放和调整必须携带原因、期望版本与幂等键；不删除或覆盖历史，而是结束当前有效区间并追加新版本。
- 项目已进入S5/S6或已正常/异常关闭时，不允许静默减少。命令进入`CONFLICT`并返回受影响范围，由授权人员在保留历史的前提下处置。
- ERP取消、退货、减量或来源版本变化使现有分配超量时，COM冻结受影响范围为`CONFLICT`并通知项目经理；不得按比例削减、删除历史或把冲突范围继续作为当前已分配事实。
- 冲突解除只能基于新的ERP确认版本、明确释放/重分配结果和审计原因；不能用附件、备注或测试数据推断。

### BR-FCOM001-005 当前范围事实

- `getAssignedScope(projectId, expectedScopeVersion)`从受信租户上下文执行；项目必须为正数并通过`ProjectScopeApi.ACTION_VIEW`。`expectedScopeVersion=null`是只读inspect；非空时先锁项目水位，再按订单行、范围、明细ID升序锁当前投影并比较期望版本。
- 返回行以`scopeId+scopeDetailId`为不可歧义分组键，不把同一订单行的不同产品、型号或地点静默聚合；每行包含订单行、数量、单位、产品/型号和明确SN。SN比较键为`trim + Locale.ROOT uppercase`，有SN时每个SN占数量1且数量等于SN数；无SN表示订单型号数量。
- `PENDING_AUTHORITY`、取消、退货、已释放、过期或旧行待核对不进入结果；但项目存在任一未解决`CONFLICT`时整体返回`SCOPE_CONFLICT`，禁止部分或空成功。项目真实无范围返回持久水位和明确空结果。
- 独立`com_delivery_scope_project_version`保存项目水位；当前集合、返回载荷、冲突冻结/解除或清空的任何变化均在同一事务单调加一且不删除水位行。未知/过期返回`SCOPE_STALE`；输入、租户、项目资格、Owner损坏和Provider不可用使用机器合同封闭分类。
- IMP/ACC只消费COM公开事实；COM不写到货、验收或归档状态。

### BR-FCOM001-006 权限、幂等和审计

- 权限固定为`pms:commerce:contract-order:query`、`pms:commerce:authority:reconcile`、`pms:commerce:delivery-scope:query`、`pms:commerce:delivery-scope:allocate`、`pms:commerce:delivery-scope:release`。
- 功能权限、项目数据范围、当前项目主体事实和业务状态守卫必须同时成立。空范围返回空，不省略条件扩大结果。
- 同键同摘要重放返回首次结果；同键异摘要为永久冲突；处理中可重试同键。失败不得产生部分范围、版本、审计或Outbox。
- 审计保存来源键/版本、分配前后数量、项目/订单行、地点解析状态、冲突原因、操作者、关联ID和时间；不记录附件正文、ERP敏感原文或完整SN清单到普通日志。

## 4. 状态与流程

| 对象 | 状态 | 允许转换 |
|---|---|---|
| 来源权威 | `PENDING_AUTHORITY` | `-> CONFIRMED`；Owner确认失败保持原状态 |
| 来源权威 | `CONFIRMED` | 新来源版本可保持`CONFIRMED`或因取消/退货/减量触发范围冲突 |
| DeliveryScope | `ACTIVE` | `-> RELEASED`或`-> CONFLICT` |
| DeliveryScope | `CONFLICT` | 证据化重分配后追加新的`ACTIVE/RELEASED`版本；原冲突历史不覆盖 |
| DeliveryScope | `RELEASED` | 终态；新分配创建新版本，不复活旧行 |

正向主流程：来源副本确认 → 合同/订单项目关联 → 预览 → 项目经理确认分配 → 生成当前范围和Outbox → 工作台/公开API读取。异常来源变化独立进入冲突处置，不阻断无关项目和订单行。

## 5. API与事件

### 5.1 用户REST

- `GET /api/v1/pms/contracts`、`GET /contracts/{id}`：只读查询合同及来源状态；
- `GET /api/v1/pms/sales-orders`、`GET /sales-orders/{id}`：只读查询订单、订单行和合同关系；
- `GET /api/v1/pms/delivery-scopes`、`GET /delivery-scopes/{id}`：按授权项目查询当前/历史范围、剩余量和冲突；
- `POST /api/v1/pms/delivery-scopes/actions/preview`：预览分配/释放，无写入；
- `POST /api/v1/pms/delivery-scopes/actions/apply`：按幂等键、`If-Match`和服务端项目事实完成分配/释放；
- `POST /api/v1/pms/commerce-authority-candidates/{id}/actions/reconcile`：合同管理员确认待核对候选与正式Owner来源的关联，不直接编辑权威字段。

### 5.2 跨Context契约

- 扩展既有`DeliveryScopeApi`新增`getAssignedScope(projectId, expectedScopeVersion)`；返回稳定DTO，不暴露DO、来源正文或内部状态。
- ERP集成Owner只通过`CommerceAuthorityIngestApi.ingestBatch`提交受信租户、eventId/batchId、来源水位、合同/订单/订单行/关系精确DTO、发生时间和correlationId。来源版本是1～64字符、批次水位是1～128字符的不透明规范字符串，禁止数字或字典序比较。锁定对象后按固定顺序判定：当前不存在时仅expected前驱为null可创建；incoming版本等于当前版本时先比较冻结规范载荷，同载荷为`OBJECT_REPLAY`、异载荷永久冲突，不执行前驱CAS；版本不同时才要求expected前驱精确命中当前版本后更新。全对象重放返回`ACCEPTED_NO_CHANGE`，重放与创建/更新混合且无冲突返回`ACCEPTED`；任一冲突全批回滚。新event可完成自身幂等/审计，但对象重放不改Owner、范围版本或业务Outbox。网络连接、认证、轮询、传输重试和外部对账仍由INT-01负责。
- PROJ资格复用正式公开事实；AST地点/SN校验复用正式公开API。不得降级为直接跨表查询。

### 5.3 事件

- `DeliveryScopeAssigned/Released`与范围事实同事务进入COM Outbox，至少冻结`eventId/tenantId/orderLineId/projectId/scopeId/scopeVersion/allocatedQty/dimensionDigest/occurredAt`。
- 来源取消/减量导致冻结时只保存COM冲突事实、项目水位、审计及既有通知边界；F-COM-001不新增未在事件SDS批准的公共冲突事件。事件送达不等于消费者业务完成，重复/乱序不得覆盖更高版本。

## 6. 数据与迁移边界

- COM物理Owner为`com_contract`、`com_sales_order`、合同订单关系、项目合同关系、`com_authority_candidate`、`com_order_line`、`com_delivery_scope`、`com_delivery_scope_detail`、`com_delivery_scope_project_version`和`com_outbox_event`。
- V70已有订单行/范围/明细/Outbox只作为F-PROJ-002切片，允许在保持既有接口语义下前向扩展；不得修改已执行迁移。
- `sms_ofst_contract_head_sap`、`pm_order_data_from_erp`、`pm_order_line_from_erp`、`pm_project_product_line`只按正式迁移契约逐行映射：原值进入`plt_migration_source_record`，合格目标进入`plt_external_key_mapping`，不可迁原因进入`plt_migration_issue`，批次数量和状态进入`plt_migration_batch`。无法证明公司+合同号、订单业务键、订单行键、项目、数量或关系完整性时不创建权威目标事实。
- V70 `com_order_line.quantity_status`继续是`CONFIRMED/PENDING_AUTHORITY`唯一数量权威字段，不改名、不新增`authority_status`双写。`source_updated_at`以及detail的`serial_no/detail_status/source_snapshot`均为既有列；真正新增的型号、来源生命周期和范围维度列对既有行可空。仓库不新增`PENDING_RECONCILIATION`业务字段：旧行是否可用只由机器合同的完整资格谓词判定，不合格行保持原值、由`plt_migration_issue`留证并从当前范围排除。
- V70允许订单行数量为NULL或0、范围`source_evidence`为NULL、旧detail缺新维度；前向迁移保留这些可空性，不直接增加会使现存数据失败的全表约束。只对满足完整资格谓词的行执行当前范围查询和新增业务写守卫；任何缺失事实不得用默认值补齐。范围current marker的表达式调整前先失败关闭校验重复当前行。
- 审计事实显示`pm_project_product_line.projectQuantity`无已填充值；`orderQuantity/deliverQuantity/openQuantity`均只保留原值，绝不替代项目分配量。因而当前这批旧范围行在取得逐行权威证据前全部进入迁移问题。
- V72种子仅是受控测试数据，不作为生产Owner来源或迁移事实。
- 旧CRM合同表/页面属于CRM业务，不迁移、不双写、不作为COM合同主档；现有旧入口保持不变。

## 7. UI

- 新建COM合同订单与交付范围工作台，不修改旧CRM合同页面；
- 列表展示来源状态、合同/订单/订单行只读字段、项目当前分配、剩余量和冲突标识；
- 分配界面先展示权威数量和其他项目已分配明细，再填写项目范围、数量、地点和维度；确认后刷新服务端当前事实；
- `PENDING_AUTHORITY`只能核对，不能出现正式分配按钮；`CONFLICT`明确展示受影响范围和恢复动作；
- 320/768/1024/1440宽度无页面级横向溢出，ID保持WireLong字符串安全，不下载或复制ERP附件正文。

## 8. 验收标准

- `AC-FCOM001-001`：合同、订单、订单行按正式来源键和版本幂等落库，ERP字段只读，人工候选不进入可分配量。
- `AC-FCOM001-002`：项目经理可在授权项目完成订单行预览和确认分配，主/明细数量一致，成功后工作台及`getAssignedScope`返回同一当前事实。
- `AC-FCOM001-003`：超配、单位/精度错误、地点或SN无效、项目无权、版本陈旧均在写入前拒绝且零业务副作用。
- `AC-FCOM001-004`：同键重放不重复范围、Outbox或审计；同键异载荷冲突；并发分配最多一个按期望版本成功。
- `AC-FCOM001-005`：来源取消、退货或减量造成超分配时范围进入`CONFLICT`，不静默削减；`getAssignedScope`对该项目整体失败关闭，不返回缩小范围；无关项目内部流程继续。
- `AC-FCOM001-006`：S5/S6或已有验收保护的范围不能静默减少，释放/调整保留旧版本、原因与冲突证据。
- `AC-FCOM001-007`：`getAssignedScope`返回产品/型号/明确SN、数量、单位和版本，排除待核对、冲突、取消、退货和已释放范围；版本变化失败关闭。
- `AC-FCOM001-008`：合同管理员、项目经理权限与数据范围符合PRD；空范围不放大，跨租户不可见，ERP字段无业务写入口。
- `AC-FCOM001-009`：旧来源只迁移可证明行，不可迁行有明确问题证据；旧CRM页面和F-PROJ-002既有接口保持可用。
- `AC-FCOM001-010`：真实MySQL验证唯一键、锁序、版本、Outbox和迁移；真实浏览器完成来源确认→分配→当前范围读取的正向闭环及四档响应式。
- `AC-FCOM001-011`：完成不宣称INT-01连接器、COM-02、V2自动指派、ACC/IMP业务状态、外部联调或Release完成。

## 9. Feature Ready Gate

最终结论：`READY / GO`。独立正式裁决以锁定基线`2fed46d4`确认完整COM-01边界、五权限、来源状态与范围状态、`getAssignedScope`、PROJ/SYSTEM/AST依赖、ERP接收、旧实现复用、十表Owner/支撑物理合同和逐行迁移处置均已锁定，且无COM-01未决问题。下一Gate为唯一Technical Plan独立复审；本GO不授权DDL或实现。生产ERP连接器未形成不阻断COM本地闭环计划与后续编码，但继续阻断真实外部联调证据。
