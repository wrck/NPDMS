# F-COM-001 合同订单副本与交付范围管理 Implementation Plan

> Technical Plan Gate：`REVIEW_REQUIRED`
> **实施代理必读：** 只有本计划通过独立 Technical Plan Gate 后才可执行。执行时使用 `executing-plans` 按Task排他认领；公共API、Flyway、权限/菜单种子与共享错误码串行合入。按本目标已确认的执行方式，先完成每个最小实现，再补聚焦验证；不得在功能尚未实现时以失败测试驱动或阻断编码。

**Goal:** 交付 `COM-01@V1=FULL` 的完整COM纵向闭环：接收ERP合同/订单权威副本，核对人工候选，按项目经理和公司范围管理交付范围，并向IMP/ACC提供可锁定重验的当前已分配范围。

**Architecture:** 在既有 `pms-module-commerce-api` 与 `pms-module-commerce` 上前向扩展，不修改旧CRM合同页面，也不改变 `getAvailableSlices/previewSplit/applySplit` 的F-PROJ-002既有语义。COM拥有十张Owner/支撑表、项目级持久水位、批次接收幂等、候选核对、范围状态机和Outbox；PROJ/SYSTEM/AST只通过公开API提供主体、数据范围、设备和地点事实。ERP连接器留在INT-01，F-COM-001只实现可由生产集成Owner调用的本地接收端。

**Tech Stack:** Java 25、Spring Boot、MyBatis Plus/XML、MySQL 8.4、Flyway、平台幂等/审计/Outbox、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose与真实浏览器。

**Spec:** `specs/features/F-COM-001-contract-order-and-delivery-scope.md`；`specs/features/F-COM-001-physical-contract.json`；`specs/features/F-COM-001-legacy-reuse-audit.md`；Feature Ready状态提交`8ca36560`；适用SDS与`docs/coding/database-query-interface.md`。

## Global Constraints

- 只实施F-COM-001与`COM-01@V1=FULL`；COM-02、V2自动指派、V3和第三方ERP连接器均排除。
- 附件/XLSX只可参考名称和界面样式，不参与来源版本、状态、数量、迁移、冲突或Gate裁决。
- 旧CRM合同页面、旧权限、旧表和旧接口保持不变；需要增强时新增COM类/页面，不把CRM字段升级为ERP权威事实。
- `getAvailableSlices/previewSplit/applySplit`保持原调用方与响应语义；`getAssignedScope`是独立的项目当前已分配范围契约，禁止由可分割余量降级适配。
- sourceVersion为1..64字符、sourceWatermark为1..128字符的不透明规范字符串；禁止数字或字典序比较。
- 项目范围写入固定要求功能权限、`ProjectScopeApi.ACTION_EDIT`与current `PROJECT_MANAGER`；合同管理固定要求功能权限与`OrganizationScopeApi`有效companyCode范围。
- 任一当前CONFLICT使`getAssignedScope`整体失败；不返回缩小后的部分或空成功。
- Flyway不预约版本号；每次实际串行合入前读取`sql/migrations`并使用下一个未占用版本。
- 本计划通过前不实施。计划通过后，单元/组件替身只允许在测试装配；真实MySQL和浏览器正向证据必须使用生产COM服务与正式PROJ/SYSTEM/AST Provider。
- INT-01连接器缺失不阻断COM本地接收、人工候选和范围闭环，但阻断真实ERP外部联调证据；它不阻断F-COM-001本地Implementation Done。

## 文件与职责地图

| 单元 | 主要文件 | 职责 |
|---|---|---|
| 公开契约 | `pms-module-commerce-api/.../api/authority/*`、`.../api/scope/*` | ERP批次接收、当前范围读取、稳定错误和DTO |
| 来源与候选 | `pms-module-commerce/.../service/authority/*` | Owner副本CAS、人工候选追加与正式Owner关联 |
| 范围领域 | `.../domain/scope/*`、`.../service/scope/*` | 分配/释放/冲突、项目水位、项目经理资格 |
| 持久化 | `.../dal/dataobject/*`、`.../dal/mysql/*`、`.../resources/mapper/*` | 十表映射、资格谓词、稳定锁序与分页 |
| REST | `.../controller/admin/commerce/*` | 八类工作台资源/动作、五权限与错误映射 |
| 前端 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/*` | 合同订单与交付范围工作台 |
| 迁移/种子 | `sql/migrations/V{next}__*.sql` | Schema、旧行核对、字典菜单权限，串行合入 |

---

### Task 1：公开API与错误合同

**Files:**

- Modify: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeApi.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/AssignedDeliveryScopeResult.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/dto/AssignedDeliveryScopeLine.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeFactException.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/CommerceAuthorityIngestApi.java`
- Create: `pms-module-commerce/pms-module-commerce-api/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/dto/*.java`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/api/CommercePublicContractTest.java`

**Interfaces:**

- Produces: `AssignedDeliveryScopeResult getAssignedScope(Long projectId, Long expectedScopeVersion)`。
- Produces: `CommerceAuthorityBatchResult ingestBatch(CommerceAuthorityBatchCommand command)`。
- Errors: `INVALID_REQUEST/TENANT_CONTEXT_MISMATCH/PROJECT_NOT_VISIBLE_OR_INELIGIBLE/SCOPE_STALE/SCOPE_CONFLICT/OWNER_DATA_CORRUPTED/PROVIDER_UNAVAILABLE`及来源版本三类冲突。

- [ ] 新增API/DTO和构造校验；`getAssignedScope`行固定`scopeId/scopeDetailId/orderLineId/quantity/unitCode/productCode/modelCode/serialNumbers`，ID稳定排序，SN比较键为`trim + Locale.ROOT uppercase`。
- [ ] 批次命令固定tenant/eventId/batchId/sourceSystem/sourceWatermark、四类事实数组、occurredAt/correlationId；每个事实包含`sourceKey/expectedPreviousSourceVersion/sourceVersion`及锁定Owner字段。
- [ ] 结果固定`ACCEPTED/ACCEPTED_NO_CHANGE/EVENT_REPLAYED`；冲突通过稳定异常分类返回，不用异常消息猜测。
- [ ] 保留既有三个DeliveryScope方法签名、DTO和行为不变。
- [ ] 实现后运行API合同测试，覆盖非法结构、Long/Decimal类型、同版本重放判定顺序、稳定排序和禁止DO/敏感正文。
- [ ] Gate：公共API机器合同独立Code Review；通过前不实施Task 3～6消费路径。

### Task 2：十表Schema与V70前向兼容

**Files:**

- Create at serial merge: `sql/migrations/V{next}__fcom001_contract_order_scope_schema.sql`
- Create/Modify: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/dataobject/**/*.java`
- Create: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001MigrationContractTest.java`
- Create: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001MigrationMySqlTest.java`

**Interfaces:**

- Produces: 十表物理模型及`com_delivery_scope_project_version`项目水位。
- Consumes: `specs/features/F-COM-001-physical-contract.json` schemaVersion 2。

- [ ] 在实际合入时取下一个Flyway版本；新增`com_contract/com_sales_order/com_sales_order_contract_relation/com_project_contract_relation/com_authority_candidate/com_delivery_scope_project_version`，前向扩展V70四表。
- [ ] `com_order_line`只新增nullable `model_code/source_lifecycle_status`；保留既有`source_updated_at/quantity_status/quantity`及NULL/0语义，禁止`authority_status`双写。
- [ ] `com_delivery_scope_detail`只新增nullable单位、产品/型号与结构化地点列；保留既有`serial_no/detail_status/source_snapshot`。
- [ ] `source_evidence`继续可空；旧行不写虚构核对状态。当前范围资格完全由nullable事实谓词决定，缺失行原值保留并由PLT迁移问题留证。
- [ ] 修改current marker前执行SQL内前置校验；存在同tenant/orderLine/project多个ACTIVE/CONFLICT当前行时SIGNAL且不改结构。新表达式把ACTIVE或CONFLICT且effective_to IS NULL标为当前。
- [ ] 新detail约束采用`unit_code IS NULL OR qualified-rules`，保证旧NULL行可迁、新业务完整行受约束；SN非空时allocated_qty必须为1。
- [ ] 实现后用隔离MySQL 8.4验证空库全量迁移、V70合法/NULL/0旧行升级、重复当前行失败且无结构变化、repair后重跑和十表约束。
- [ ] Gate：Schema/迁移合同独立Code Review与MySQL Gate。

### Task 2A：PLT迁移证据Owner支撑合同与实现

**Files:**

- Modify: `docs/design/02d-cross-context-contracts.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `specs/features/F-COM-001-physical-contract.json`
- Create: `specs/features/F-COM-001-migration-evidence-api-contract.json`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/PlatformMigrationEvidenceApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/migration/dto/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiImpl.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/migration/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/migration/*.java`
- Create: `pms-module-platform/src/main/resources/mapper/migration/*.xml`
- Create at serial merge: `sql/migrations/V{next}__platform_migration_evidence.sql`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/migration/PlatformMigrationEvidenceApiTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/migration/PlatformMigrationEvidenceMySqlTest.java`

**Interfaces:**

- Produces: PLT物理Owner的批次创建/暂存就绪/核对领取/最终完成、逐源行追加/游标查询、外部键映射追加、迁移问题追加/关闭最窄API。
- Owns: `plt_migration_source_record/plt_external_key_mapping/plt_migration_issue/plt_migration_batch`；COM不得直接访问这些表。

- [ ] 先形成独立公共机器合同并送Contract Gate，固定受信tenant、batchId、sourceSystem/table/pk、不可变sourcePayload、mapping/issue状态、幂等键、批次计数和失败分类；不承接COM字段判定。批次状态封闭为`IMPORTING/STAGED_READY/RECONCILING/COMPLETED/FAILED`，其中`STAGED_READY`仅表示来源暂存已校验，`COMPLETED`仅表示COM核对终结。
- [ ] Contract Gate GO后由PLT Owner创建四表及DO/Mapper/Service；动态集合、批次锁和状态汇总SQL进入XML，不把PLT DO暴露给COM。
- [ ] API动作固定为`createImportBatch/appendSourceRecord/markStagedReady/claimStagedBatch/appendExternalMapping/appendMigrationIssue/completeReconciliation`。导入器仅可在`IMPORTING`追加source；`markStagedReady`校验manifest、行数/hash后冻结source集合并进入`STAGED_READY`，不得写mapping/issue或提前`COMPLETED`。
- [ ] `claimStagedBatch`只领取`STAGED_READY`并在调用方同一外层事务内进入`RECONCILING`；COM逐行追加mapping/issue/retained结果后，`completeReconciliation`校验`source=mapped+issue+retained`及各计数并原子进入最终`COMPLETED`。任一步失败使整事务回滚到`STAGED_READY`；`FAILED`不可领取，`COMPLETED`后禁止追加、关闭、重算或覆盖结果。
- [ ] append source record以`tenant+batch+system+table+pk`幂等；同键异原值冲突。external mapping、issue和retained结果均使用来源行稳定引用，不覆盖前批次。
- [ ] 实现后验证真实MySQL租户隔离、重放/冲突、问题关闭审计、批次并发结束与外层事务回滚。
- [ ] Gate：PLT Owner Contract Gate后再做Provider Code Review/MySQL Gate；未通过时Task 8保持`BLOCKED_BY_DEPENDENCY`，不得由COM直写平台表。

### Task 3：ERP批次接收与Owner副本CAS

**Files:**

- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/authority/CommerceAuthorityIngestApiImpl.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestService.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/AuthorityPayloadCanonicalizer.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/query/*.java`
- Create: `pms-module-commerce/src/main/resources/mapper/authority/*.xml`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityIngestServiceTest.java`

**Interfaces:**

- Consumes: Task 1 `CommerceAuthorityIngestApi`。
- Produces: 已确认COM Owner副本、关系与来源变化触发的范围冲突/项目水位。

- [ ] 受信tenant校验先于平台命令认领；eventId作为幂等key，完整规范批次载荷作为digest，correlationId原样进入成功事实。
- [ ] 按对象类型、sourceKey稳定顺序锁Owner；不存在仅expectedPrevious=null创建；同版本先比较冻结Owner载荷，同载荷OBJECT_REPLAY、异载荷永久冲突；不同版本才执行前驱CAS。
- [ ] 全对象重放只完成新event幂等/审计并返回ACCEPTED_NO_CHANGE；混合创建/更新返回ACCEPTED；任何冲突全批回滚，不写部分关系、scopeVersion或Outbox。
- [ ] 来源取消/退货/减量扫描受影响当前范围，先结束原ACTIVE有效区间再追加同一业务链的CONFLICT历史并递增项目水位，始终保持每个tenant/orderLine/project最多一条当前ACTIVE或CONFLICT；不发布新公共冲突事件，不削减数量。
- [ ] 实现后验证事件重放/异载荷、对象重放/异载荷、前驱错配、乱序批次、混合批次、来源减量和事务回滚。
- [ ] Gate：ERP本地接收Code Review/聚焦测试 Gate；不需要INT-01网络连接器。

### Task 4：人工候选、关系核对与公司范围

**Files:**

- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateService.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/authorization/CompanyScopeGuard.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/authority/AuthorityCandidateMapper.java`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/authority/CommerceAuthorityCandidateServiceTest.java`

**Interfaces:**

- Consumes: SYSTEM `OrganizationScopeApi.getActiveScopes(actorId)`。
- Produces: append-only PLATFORM_MANUAL候选和对既有CONFIRMED Owner的MATCHED引用。

- [ ] 新增候选时服务端写objectType、不可变来源键/版本、payload、证据引用、submittedBy/At；客户端不能写matched/decided字段。
- [ ] 查询与核对前取受信actor的有效公司范围，Owner companyCode必须精确命中；空范围返回空或拒绝，不能扩大。
- [ ] reconcile只允许关联同tenant/company/objectType且已CONFIRMED的Owner表/id/sourceVersion；不得创建Owner、改候选来源键或复制payload。
- [ ] 同候选版本重复同载荷重放，异载荷冲突；MATCHED/REJECTED历史不可覆盖。
- [ ] 实现后验证无项目关联合同的公司裁剪、跨公司/跨租户、失效范围、错误Owner状态、重放和并发决定。

### Task 5：项目范围分配、释放与冲突状态机

**Files:**

- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/domain/scope/DeliveryScopeStateMachine.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/domain/scope/DeliveryScopeValidationRules.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/ProjectScopeQualificationAdapter.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeviceAndLocationFactAdapter.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandService.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommands.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/CommerceDeliveryScopeCommandMapper.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/query/CommerceDeliveryScopeCommandQuery.java`
- Create: `pms-module-commerce/src/main/resources/mapper/scope/CommerceDeliveryScopeCommandMapper.xml`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeCommandServiceTest.java`
- Existing regression: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/DeliveryScopeServiceTest.java`

**Interfaces:**

- Consumes: PROJ Participant/Scope、AST DeviceScopeFact/AssetLocation、平台幂等审计。
- Produces: ACTIVE/RELEASED/CONFLICT历史、qualified detail、项目scopeVersion与Assigned/Released Outbox。

- [ ] `CommerceDeliveryScopeCommandService`是F-COM-001 REST与工作台写命令的唯一新入口，承接项目水位、完整明细、冲突历史、平台幂等/审计和Outbox；它不被旧`DeliveryScopeApi.previewSplit/applySplit`调用。
- [ ] 既有`DeliveryScopeService`、`DeliveryScopeApiImpl`中三个旧方法及其Mapper调用路径保持源码和副作用不变；不得在旧路径接入项目水位、新明细或新冲突行为。若两套服务需要相同纯校验，只允许新建无数据库/事务/事件副作用的`DeliveryScopeValidationRules`，旧服务是否改为调用该规则不属于本Feature，默认不修改。
- [ ] 用户写命令同时锁定重验功能权限外的current PROJECT_MANAGER和ACTION_EDIT；全局角色、普通参与或单独ACTION_EDIT均不足。
- [ ] 锁序固定：项目水位→orderLineId升序→scopeId→detailId；文本SQL、集合和FOR UPDATE全部放XML，Mapper只接收场景化Query。
- [ ] 只允许qualified CONFIRMED/ACTIVE订单行分配；主/明细数量一致、单位精度一致，项目间总量不超权威数量。
- [ ] 明确SN通过AST解析到同tenant/project且去重，每个SN detail数量1；无SN数量不调用设备API。地点使用稳定site/location，文本降级必须UNRESOLVED。
- [ ] apply同事务追加范围/明细、项目水位、平台幂等/审计和Assigned/Released Outbox；失败零业务副作用。
- [ ] S5/S6/关闭项目或验收保护下的减少转CONFLICT而非静默释放；冲突解除必须引用新ERP版本或明确释放证据。
- [ ] 实现后以新`CommerceDeliveryScopeCommandServiceTest`验证并发超配、权限、项目版本、SN/地点、释放保护和Outbox；原样复跑既有`DeliveryScopeServiceTest`固定旧三个API返回、幂等键、Outbox和数据库副作用均未改变。

### Task 6：getAssignedScope生产Provider

**Files:**

- Modify: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/api/scope/DeliveryScopeApiImpl.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/AssignedDeliveryScopeQueryService.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/dal/mysql/scope/query/AssignedDeliveryScopeQuery.java`
- Create: `pms-module-commerce/src/main/resources/mapper/scope/AssignedDeliveryScopeMapper.xml`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/AssignedDeliveryScopeQueryServiceTest.java`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/scope/AssignedDeliveryScopeMySqlTest.java`

**Interfaces:**

- Produces: Task 1正式`getAssignedScope`生产Bean，可供F-IMP-002 Task 12消费。

- [ ] tenant取运行上下文，项目为正数并通过ACTION_VIEW；null expected为read-only inspect，返回不存在水位时的确定性version 0空结果。
- [ ] 非null expected在事务内锁项目水位；水位行不存在且expected=0时以唯一键创建version 0行并持锁，expected非0则STALE。随后按orderLine/scope/detail稳定顺序锁投影；任何不匹配返回SCOPE_STALE。
- [ ] 查询只返回完整资格谓词命中的ACTIVE行；任何当前CONFLICT先整体返回SCOPE_CONFLICT，禁止部分成功。
- [ ] 每个scope/detail独立成行，不聚合不同维度；SN规范化去重、稳定排序，有SN时数量等于SN数。
- [ ] Owner缺失、重复、主明细数量不等、单位/产品/型号/SN损坏返回OWNER_DATA_CORRUPTED；数据库/事务不可用只在包住代理边界后映射PROVIDER_UNAVAILABLE。
- [ ] 实现后用真实Spring代理/MySQL验证空范围、水位变化、冲突、旧不合格行排除、锁等待和事务异常分类。
- [ ] Gate：独立Provider Code Review/MySQL Gate。通过后F-IMP-002可接通COM Adapter，但F-COM-001仍未整体Done。

### Task 7：工作台应用查询、REST与错误映射

**Files:**

- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/commerce/*.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/commerce/vo/*.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/workbench/*.java`
- Modify: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/enums/ErrorCodeConstants.java`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/commerce/CommerceWorkbenchControllerTest.java`

**Interfaces:**

- Produces: `/api/v1/pms/contracts`、`/sales-orders`、`/delivery-scopes`查询/详情，preview/apply和candidate reconcile动作。

- [ ] 列表统一PageParam/PageResult并限制页大小，排序追加id；空公司/项目范围返回空，不省略IN条件。
- [ ] 服务端allowedActions与功能权限、公司/项目范围、current PROJECT_MANAGER、状态、冲突和版本守卫同构。
- [ ] apply/reconcile要求Idempotency-Key与If-Match；客户端不得提交tenant、actor、Owner状态、版本分配或决定人字段。
- [ ] 结构化异常映射400/403/404/409/422/503 CommonResult，区分版本陈旧、幂等冲突、范围冲突、Owner损坏与Provider不可用；不修改Yudao全局处理器。
- [ ] 实现后用真实MockMvc覆盖Header、JSON严格字段、权限拒绝、空范围、错误HTTP/envelope和旧CRM路由零变化。

### Task 8：逐行旧数据核对与PLT迁移证据

**Files:**

- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/CommerceLegacyReconciliationService.java`
- Create: `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/migration/CommerceLegacyReconciliationJob.java`
- Create: `tools/migration/commerce-legacy-source-import/pom.xml`
- Create: `tools/migration/commerce-legacy-source-import/src/main/java/cn/iocoder/yudao/tools/migration/commerce/CommerceLegacySourceImportMain.java`
- Create: `tools/migration/commerce-legacy-source-import/src/main/java/cn/iocoder/yudao/tools/migration/commerce/CommerceLegacySourceImportRunner.java`
- Create: `tools/migration/commerce-legacy-source-import/src/main/java/cn/iocoder/yudao/tools/migration/commerce/CommerceLegacySourceManifest.java`
- Modify: `pom.xml`
- Create: `tools/migration/commerce-legacy-source-import/README.md`
- Test: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/service/migration/CommerceLegacyReconciliationServiceTest.java`
- Test: `tools/migration/commerce-legacy-source-import/src/test/java/cn/iocoder/yudao/tools/migration/commerce/CommerceLegacySourceImportTest.java`

- [ ] 采用独立Release迁移工具，不建立legacy datasource。Release Owner先从遗留系统按批准窗口导出四个UTF-8 JSONL文件和一个manifest；manifest精确包含`releaseId/tenantId/sourceSystem/sourceTable/filePath/rowCount/exportedAt/schemaVersion/contentSha256`，文件每行必须包含原始主键及原字段名/值。工具只读取显式绝对路径，不扫描目录、不下载附件、不连接遗留库。
- [ ] `tools/migration/commerce-legacy-source-import`是独立Maven模块，依赖`pms-module-platform`但不被`yudao-server`依赖；`CommerceLegacySourceImportMain`以`WebApplicationType.NONE`启动专用Spring上下文，`Runner`只调用Task 2A `PlatformMigrationEvidenceApi`，不注册到正常服务Bean或Job。运行必须显式提供`--spring.config.additional-location=<approved-local-config>`、`--pms.migration.manifest=<absolute-path>`和`--pms.migration.tenant-id=<id>`，manifest tenant必须匹配后才设置受信TenantContext；随后创建`IMPORTING`批次，按sourceTable/sourcePk升序追加不可变source record，校验行数/hash后仅推进为`STAGED_READY`；校验或API失败写`FAILED`审计，不写COM表、不提前形成最终完成结果。
- [ ] 导入器只接受四个锁定表名及对应精确字段集合，缺主键、重复主键、字段漂移、行数/hash不符或API失败使当前批次FAILED；同release/table manifest重跑复用同一导入幂等键，同键异文件永久冲突。跨文件不使用分布式事务，各表批次独立且失败表不得被COM消费。
- [ ] COM正常生产Bean不包含遗留连接配置、DataSource、Mapper或文件读取器。`CommerceLegacyReconciliationJob`只通过Task 2A API领取`STAGED_READY`批次；同一外层事务内进入`RECONCILING`、分页读取其冻结source、写COM目标和PLT mapping/issue/retained结果，并在计数相等后原子转最终`COMPLETED`。失败整体回滚到`STAGED_READY`供同批重试；`FAILED/COMPLETED`均不可领取。不存在正式`STAGED_READY`批次时领取返回空，Job保持`PAUSED`且不产生业务或批次副作用。
- [ ] `sms_ofst_contract_head_sap`因无公司、生命周期和单调来源版本只留问题；`pm_order_data_from_erp`缺稳定版本/生命周期时不建CONFIRMED Owner；订单行缺单位/模型/生命周期时排除。
- [ ] `pm_project_product_line.projectQuantity`空值率100%，禁止以order/deliver/openQuantity替代；当前审计行全部形成确定性问题，不生成DeliveryScope。
- [ ] 合格行记录external key mapping；不可迁行保留source record并写issue；batch保存抽取、合格、迁入、问题计数。重跑幂等，不双写旧表。
- [ ] 实现后分层验证：导入器用受控合成JSONL验证schema/行数/hash/重放/失败，但该fixture只证明工具行为，不得标记生产迁移完成；COM MySQL测试从PLT API暂存记录验证四源逐字段映射、问题原因、重复扫描和旧行排除。
- [ ] 只有Release Owner提供带批准releaseId、来源导出记录、manifest、导入命令输出、PLT batchId及最终`COMPLETED`核对计数的真实制品，Task 11才可记录“生产历史迁移证据”。当前Release若明确不含历史迁移/数据切换，则该证据为`NOT_APPLICABLE`且Job继续PAUSED；若包含而无正式制品或只有`STAGED_READY`未终结，则阻断Release/迁移完成声明，不得用fixture替代。

### Task 9：字典、菜单、权限与暂停Job种子

**Files:**

- Create at serial merge: `sql/migrations/V{next}__fcom001_contract_order_scope_seed.sql`
- Modify: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001MigrationContractTest.java`

- [ ] 幂等登记来源权威、生命周期、关系、范围、候选状态字典和五项权限；只使用Feature Spec封闭值。
- [ ] 在既有PMS商务/项目体系新增一个COM工作台菜单，不写角色授权，不改CRM合同菜单与权限。
- [ ] 以确定性高段ID登记`commerceLegacyReconciliationJob`为PAUSED；未完成真实迁移前置核验不得启用。
- [ ] 不播种ERP确认业务事实、自动指派或测试完成状态；示例数据只可使用专用高段ID/seed creator且不能进入生产真值。
- [ ] 实现后验证空库迁移、重复脚本、数量/唯一性、Job暂停和旧菜单不变。

### Task 10：新COM前端工作台

**Files:**

- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/components/*.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/commerceInteraction.ts`
- Test: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/commerce/*.spec.ts`

- [ ] 实现合同/订单只读列表详情、人工候选、项目范围、剩余量、冲突和分配/释放对话框；不修改旧CRM页面。
- [ ] 操作入口同时按服务端allowedActions与五权限投影；Owner字段只读，人工候选与正式Owner明确区分。
- [ ] WireLong安全保存Snowflake ID，Decimal不经JS浮点改写；Idempotency-Key/If-Match在未知响应时保留，409刷新完整聚合。
- [ ] 页面不下载或复制ERP附件正文；附件/文件只保存稳定PLT引用。
- [ ] 组件在320/768/1024/1440无页面级横向溢出，冲突和PENDING_AUTHORITY不可误显示为可分配。
- [ ] 实现后挂载组件验证权限/allowedActions、候选核对、分配/释放、重放恢复、冲突阻断、WireLong/Decimal和四档布局；运行Vitest、ts:check、定向lint与build:local。

### Task 11：真实MySQL、生产联调与浏览器闭环

**Files:**

- Create: `pms-module-commerce/src/test/java/cn/iocoder/yudao/module/pms/commerce/FCom001ApplicationMySqlTest.java`
- Create: `docs/superpowers/evidence/f-com-001/implementation-evidence.md`
- Modify: `tasks/features/F-COM-001.md`

- [ ] 使用独立Compose MySQL 8.4空卷从V1迁到当前版本，验证十表、CAS、项目水位、唯一键、旧行资格、Outbox和并发超配。
- [ ] 以生产API本地调用`ingestBatch`形成真实COM Owner副本；这证明COM本地接收，不冒充ERP网络联调。
- [ ] 核验PROJ/SYSTEM/AST生产Provider后完成候选→正式Owner关联、项目经理分配、`getAssignedScope`正向与来源减量冲突负向；缺Provider时保持BLOCKED_BY_DEPENDENCY，不注册Fake。
- [ ] 如当前Release包含历史迁移且已有正式Release导出manifest、导入器审计和`STAGED_READY` PLT batch，才可受控启用核对Job；Job完成后记录最终`COMPLETED`批次、mapping/issue/retained计数。不包含历史迁移时登记`NOT_APPLICABLE`并保持PAUSED，包含但暂存证据不完备时阻断迁移/Release完成。任何场景都不得以测试fixture冒充生产迁移证据。
- [ ] 为当前工作树分配不冲突前后端端口，前端代理同分支后端；记录端口、提交、进程与DB版本。
- [ ] 真实浏览器覆盖公司范围、候选核对、项目经理分配/释放、同键重放、旧版本刷新、冲突整体阻断、越权、空范围与四档响应式；检查console/page error/network。
- [ ] INT-01外部连接器未形成时证据明确标记`EXTERNAL_ERP_INTEGRATION_NOT_TESTED`，不把本地API调用伪装为外部联调。

### Task 12：回归、追溯与Implementation Done Gate

**Files:**

- Modify: `tasks/features/F-COM-001.md`
- Modify by generator: `docs/traceability/requirement-matrix.md`
- Modify by generator: `docs/traceability/requirement-version-coverage.json`
- Modify: `docs/superpowers/evidence/f-com-001/implementation-evidence.md`

- [ ] 复跑commerce-api/commerce Reactor、既有DeliveryScope回归、MySQL、前端组件、ts/build与真实浏览器证据；旧CRM和F-PROJ-002三接口必须保持可用。
- [ ] 对照AC-FCOM001-001～011逐项链接代码、测试、MySQL和浏览器证据；机械PASS不能替代业务闭环。
- [ ] 确认未实现COM-02、V2自动指派、第三方连接器、Yudao修改或跨模块读表；`.run`和本地运行制品不提交。
- [ ] 只有全部本地COM闭环、生产Owner依赖和独立Code Review通过后，才把Task实施状态候选改为`IMPLEMENTATION_COMPLETE`并送独立Implementation Done Gate。
- [ ] INT-01外部联调单独保持未完成证据，不反向阻断已明确排除的网络连接器；若Release范围要求真实ERP联调，则由Release Gate继续阻断。

## 任务依赖与串行合入

1. Task 1公开合同先行；Task 2 COM Schema、Task 2A PLT支撑Schema与Task 9种子分别串行定号。
2. Task 3/4可在Task 1/2后并行；Task 5依赖Task 2及PROJ/AST合同；Task 6依赖Task 2/5。
3. Task 7依赖Task 3～6应用能力；Task 8依赖Task 2/2A/3，可与Task 7并行。
4. Task 10依赖Task 7稳定REST；Task 11依赖Task 3～10及生产PROJ/SYSTEM/AST Provider。
5. 每个Task独立Code Review通过后才进入下游；共享API、Flyway、菜单/权限、错误码和Task状态文件禁止并行写。

## Plan自审

- 覆盖：Feature Scope、六条业务规则、状态、API/事件、十表迁移、受控遗留源摄取、UI与AC-FCOM001-001～011均有对应Task。
- 复用：旧`DeliveryScopeService`及三个API路径不承接F-COM-001新副作用；新命令入口固定为`CommerceDeliveryScopeCommandService`。
- 排除：未包含COM-02、自动指派、第三方连接器、CRM页面改造、ACC/IMP业务写入或Yudao平台改造。
- 无占位业务选择：Flyway仅用`V{next}`表达合入时定号，不是预约；所有状态、权限、错误、版本与候选规则来自锁定Feature/physical contract。
- 类型一致：公开`getAssignedScope`、`ingestBatch`与后续Provider/Controller使用同一Task 1 DTO；sourceVersion长度与V70列均为64。

## Gate与执行交接

当前计划仅为`REVIEW_REQUIRED`候选。最近Gate是本计划的独立Technical Plan复审；GO后才可按Task 1→12执行。计划GO不等于任何Task或Feature Implementation Done。
