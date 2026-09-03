# F-IMP-002 到货签收与里程碑事实 Technical Plan

> Technical Plan Gate：`PASS / GO`（独立最小整改复审；锁定提交 `e0184ac4`）
> **实施代理必读：** 只有本计划通过独立 Technical Plan Gate 后才可执行。执行时使用 `executing-plans`，按 Task 排他认领；共享 API、Flyway、菜单种子、错误码和事件契约串行合入。任何受控替身只存在于测试装配，不得进入生产 Bean、真实 MySQL 正向证据或浏览器验收。

**目标：** 交付 IMP Owner 的 EXE-01 到货签收完整闭环：按 COM 应到范围和 AST 稳定设备事实创建多批签收，处理部分到货、差异、拒收、补签和明确豁免，由项目经理最终确认不可变批次，发布 EXE-01 DeliveryEvidence，并向 EXE-02/EXE-06提供可锁定重验的项目级 `ArrivalAcceptanceFactApi`。

**架构：** 在 `pms-module-engineering` 新建 `ArrivalAcceptance` 聚合，不改旧 `pms_eng_arrival` 运行表、旧 REST 和旧页面。五张 IMP Owner 表承载批次、明细、差异版本及 EXE-01 证据修订；应用服务通过公开 Business API 编排 COM、AST、PROJ、PLT，禁止跨模块读表。PROJ `ProjectParticipantFactApi`证明项目 `ACTIVE/S4`、项目内角色和项目版本，`ProjectScopeApi.ACTION_EDIT`只补充数据范围。写命令使用聚合 CAS、平台幂等、稳定锁序、Outbox 和审计；ACC入向回执复用平台持久幂等记录作为 Inbox。项目事实从当前有效范围与已确认来源批次实时计算，不另造第二完成真值。

**技术栈：** Java 25、Spring Boot、MyBatis Plus/XML、MySQL 8.4、Flyway、平台命令幂等/审计/Outbox、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose 真实基础设施与 Playwright/Chrome 真实浏览器。

**锁定输入：** Feature Ready 锁定提交 `4b5a2ac9`；状态回写提交 `6c18f794`；`EXE-01@V1=FULL`；`specs/features/F-IMP-002-arrival-acceptance.md`；`specs/features/F-IMP-002-legacy-reuse-audit.md`；`specs/features/F-IMP-002-physical-contract.json`；`specs/features/F-IMP-002-arrival-fact-contract.json`；适用 PRD/SDS；`docs/coding/database-query-interface.md`。

## 固定边界与完成口径

- 只实施 F-IMP-002。安装、配置、联调、IMP-01其他交付件、ACC审核/归档 Owner、COM范围维护、AST归属写入、CUT状态和 V2/V3 均排除。
- 旧 `ArrivalController/ArrivalService/ArrivalMapper`、`pms_eng_arrival`、`pms_eng_deliverable`、旧 `/pms/eng-arrival` API 和旧 `pms/engineering/arrival/index.vue` 保持行为不变；正式能力使用新类、表、API和页面。
- 附件只保存 PLT `FileReference`/版本引用，不保存原始 URL 为权威事实，不重复下载或复制二进制。
- XLSX/附件只可参考名称、说明和界面呈现；不参与状态、数量、规则、差异裁决或 Gate 判断。
- 自动指派属于 V2，本 Feature 不生成、推荐或执行自动指派。
- COM `DeliveryScopeApi`、AST `DeviceScopeFactApi`、PROJ `ProjectScopeApi`、PLT `FileArtifactApi` 与 ACC 回执的生产 Provider 未形成时，允许完成不依赖生产事实的代码和受控替身单元/集成测试，但不得声明 Implementation Done，也不得形成真实浏览器正向闭环证据。
- Implementation Done 必须同时满足 AC-FIMP002-001～007、真实 MySQL、生产 Provider 契约验证、真实浏览器正负向闭环及独立复审 GO；HTTP 200、编译通过、替身测试或事件已发送均不能单独证明完成。
- Flyway 不预约版本号。Schema 与种子/Job迁移分别作为两个串行合入单元，只在各自实际合入前读取 `sql/migrations` 并取当时下一个未占用版本；计划中的 `V{next}`、`V{next+1}`只是逻辑占位，不是预留号。

## 复用裁定

| 来源 | 裁定 | 本 Feature 用法 |
|---|---|---|
| 旧 Arrival 后端、表、REST | `COPY_THEN_ENHANCE` | 仅借鉴基础 CRUD 组织和项目列表体验；新建聚合、状态机、权限、版本、差异、证据和事实 API，不修改旧路径 |
| 旧 Arrival 前端 | `COPY_THEN_ENHANCE` | 新页面保留项目筛选和列表交互意图，改为批次详情、明细、差异、证据、allowedActions 和显式命令 |
| 旧 tinyint 状态、数量、异常文本、测试种子 | `NOT_REUSABLE_AS_FACT` | 仅作迁移来源字段；不得直接推出 `ACCEPTED`、有效差异处置或项目完成 |
| `pms_eng_deliverable` RECEIPT | `CONDITIONAL_FORWARD` | 仅同租户、来源到货可解析且已有有效 PLT FileReference 时迁证据引用；其他类型由 IMP-01 Owner 处理 |
| ProjectParticipantFact、ProjectScope、FileArtifact、平台幂等/审计/Outbox | `DIRECT_REUSE` | 项目资格/阶段/版本读取Participant事实，数据范围读取ACTION_EDIT；其他只调用公开契约，不复制 Owner 数据，不引入跨模块 `-biz` 依赖 |

---

### Task 1：公共事实契约与生产依赖适配

**Files:**

- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApi.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/dto/ArrivalAcceptanceFactQuery.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/dto/ArrivalAcceptanceFactRevalidationQuery.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/dto/ArrivalAcceptanceFact.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/dto/ArrivalScopeWatermark.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/dto/ArrivalQuantityScopeFact.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeliveryScopePort.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeviceScopeFactPort.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/FileArtifactFactPort.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/ProjectQualificationPort.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/DeliveryScopeApiAdapter.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/DeviceScopeFactApiAdapter.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/FileArtifactApiAdapter.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ProjectQualificationApiAdapter.java`
- Modify: `pms-module-engineering/pom.xml`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactContractTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/ArrivalAcceptanceOwnerAdapterTest.java`

- [ ] 先写机器契约测试，固定 `inspect/lockAndRevalidate` 输入、三种 decision、稳定升序 `sourceAcceptanceIds`、单调 `factVersion`、结构化 watermark、范围结果和禁止字段。
- [ ] 新增最窄 API DTO；不暴露 DO、签收人隐私、文件正文或持久下载地址。
- [ ] 四个生产 Adapter 分别只调用 COM `DeliveryScopeApi`、AST `DeviceScopeFactApi`、PLT `FileArtifactApi`、PROJ `ProjectParticipantFactApi/ProjectScopeApi`。`DeliveryScopeApiAdapter`必须等待 COM 物理 Owner 的正式 `getAssignedScope(projectId, expectedScopeVersion)`合入后接通；当前 `getAvailableSlices/previewSplit/applySplit`只表达父项目可分割余量，不含项目当前已分配范围、产品/型号和明确SN，禁止作为替代或降级来源。AST Adapter 同样必须等 `T-FIMP001-AST-01` 公共 API 合入，不复制旧 `AssetDeviceScopeApi`。生产装配不存在 fallback、空集合放行或 Fake Bean。
- [ ] `ProjectQualificationPort.inspect`返回 lifecycle/currentStage/effectiveRoleCodes/projectVersion/factVersion。现有 `ProjectParticipantFactApiImpl` 的 `requiredCurrentStage`校验只接受S1，因此适配器不得错误传S4：写事务调用 `lockAndRevalidate(requiredLifecycleStatus=ACTIVE, requiredCurrentStage=null, expectedProjectVersion=冻结值)`，在同一外层事务和PROJ锁仍持有时立即比较返回的 `currentStage=S4`、`factVersion=冻结值`，不匹配则在任何业务写前回滚。最终确认额外要求 actor 本人的 `PROJECT_MANAGER`；`ACTION_EDIT`只证明数据范围。
- [ ] 测试 Fake 只放 `src/test`。任一公共 API 缺失、未知、过期或不可用均失败关闭；缺生产 Provider 时相关 Task 标记 `BLOCKED_BY_DEPENDENCY`，不注册替代实现。
- [ ] `ArrivalAcceptanceOwnerAdapterTest`固定 COM 正式契约：返回项目当前有效已分配订单行、数量、单位、产品/型号、明确SN及scopeVersion；待权威确认、取消、退货和已释放量必须排除，expectedScopeVersion过期失败关闭。另断言当前 `getAvailableSlices/previewSplit/applySplit`没有任何到 `DeliveryScopePort` 的生产适配路径。
- [ ] 验证：`mvn -pl pms-module-engineering-api,pms-module-engineering -am -Dtest=ArrivalAcceptanceFactContractTest test`。

### Task 2：五表 Schema 与应用级 CURRENT_FORWARD 核对

**Files:**

- Create at serial merge: `sql/migrations/V{next}__fimp002_arrival_acceptance.sql`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/migration/ArrivalLegacyReconciliationService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/migration/ArrivalLegacyReconciliationJob.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/LegacyArrivalReconciliationQuery.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/migration/ArrivalLegacyReconciliationServiceTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationMySqlTest.java`

- [ ] Schema Flyway 只建表、约束和索引，不读取 `pms_eng_*`，也不调用 COM/AST/PLT。按 physical contract 建立五表，包含租户复合身份、批次编码、当前版本唯一键、追加修订唯一键、CAS版本、Yudao审计/逻辑删除列和必要外键。
- [ ] `ArrivalAcceptance` 保存冻结的 COM 范围版本、结构化订单范围快照、AST 设备归属版本向量、项目事实版本和迁移核对状态；禁止用摘要代替业务 watermark。
- [ ] `imp_delivery_evidence`在五表边界内增加 `acc_last_published_at/acc_next_retry_at/acc_retry_count/acc_last_event_id`；`imp_arrival_acceptance`增加 `legacy_source_id/migration_reason_code`。这些是既有 Owner 根的调度/迁移支撑字段，不新增 Owner 表。明细与差异使用 `current_marker`；证据 revision 插入后不可更新/删除。
- [ ] Schema 合入并且 COM/AST/PLT 生产 Provider 可用后，`ArrivalLegacyReconciliationJob`才按 `tenant_id,id`游标分批调用应用服务。逐行资格固定为：租户、项目、批次编码、发生时间有效且同租户；COM返回真实 `deliveryScopeVersion`和结构化范围；旧设备存在时必须解析到AST稳定设备及归属版本；RECEIPT必须关联该到货来源且已存在有效PLT FileReference。任一 Owner 不可用只延后该行，不写目标。
- [ ] 旧行本身没有物流单号，绝不把 `code`、旧说明、0、空JSON或构造值填成 `logistics_no/scopeWatermark`。只有全部五表NOT NULL字段都来自旧可证明字段、真实Owner事实或经正式业务录入后，才可创建目标根；否则保留旧行，并通过既有 `OperationAuditApi`以 `operationCode=FIMP002_LEGACY_ARRIVAL_RECONCILE`、`aggregateType=LegacyArrival`、`aggregateKey={legacyId}`、`resultCode=PENDING_RECONCILIATION|REJECTED`持久记录原因码和已证明字段，不创建半合法目标。
- [ ] 可创建目标的旧行只进入 `DRAFT/PENDING_RECONCILIATION`，保存真实 watermark、`legacy_source_id`和原因码；仍不得产生 `CONFIRMED/projectFactVersion/ACCEPTED`。同一 `tenantId+legacySourceId`幂等，重跑不双写。RECEIPT证据只有已映射 FileReference 时追加 revision；旧URL、旧归集/作废状态不迁。
- [ ] 定向验证测试种子中的旧 `status=1/2`、原始 URL、无法解析设备及非 RECEIPT 交付件不会形成 `ACCEPTED/ARCHIVED`。
- [ ] 验证分两层：全新 MySQL 从 V1 迁到当前版本只检查五表 Schema 和旧表零修改；应用核对测试使用生产契约或受控测试 Fake 验证逐行资格、平台审计处置、重复扫描幂等和不可迁行不落目标。Fake 结果不能作为生产迁移完成证据。

### Task 3：持久化映射与稳定锁序

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/*.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/*.java`
- Create: `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/*.xml`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapperContractTest.java`

- [ ] 为五表建立独立 DO/Mapper；分页、范围汇总、当前版本、迁移扫描和锁查询分别使用场景化 Query 对象。
- [ ] 联表、集合、动态条件、事实汇总和 `FOR UPDATE` 全部进入 XML；禁止 SQL 注解、`${}`、`.last(...)`、长位置参数、`Map` 和 Service 拼 SQL。
- [ ] 锁顺序固定为 `deviceId -> orderLineId -> arrivalAcceptanceId -> evidenceId`；锁查询只在事务中调用并以 `ForUpdate` 命名。
- [ ] 空可见项目集合和空必填范围直接返回空结果，不能省略条件扩大租户/权限范围。
- [ ] 验证 Mapper 合约、稳定排序、多条完整性异常、跨租户隔离和并发锁序。

### Task 4：到货领域规则、批次状态机与项目事实计算

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalAcceptanceRules.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalAcceptanceStateMachine.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalFactCalculator.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalAcceptanceRulesTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalAcceptanceStateMachineTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/domain/arrivalacceptance/ArrivalFactCalculatorTest.java`

- [ ] 先覆盖多批、部分签收、明确设备、无 SN 数量、重复设备、超量、单位不符、空范围、跨项目设备和证据缺失的失败/成功规则。
- [ ] 精确实现 `DRAFT -> DIFFERENCE_PENDING|PARTIALLY_ACCEPTED|ACCEPTED -> CONFIRMED`；`CONFIRMED` 只代表批次最终确认。
- [ ] 差异处置仅允许 `SUPPLEMENTED/REJECTED/EXEMPTED/CLOSED` 追加版本；拒收仍未满足，豁免必须明确范围、批准事实、证据和有效期。
- [ ] 更正、补签、关闭和豁免失效创建关联 successor DRAFT，原 CONFIRMED 不回退；影响项目事实时递增 `factVersion` 并标记旧事实 reopened。
- [ ] 项目事实只统计 CONFIRMED 批次中的 ACCEPTED 明细和未过期明确豁免，返回 `ACCEPTED/NOT_ACCEPTED/STALE` 及完整未满足范围。

### Task 5：草稿、提交、差异与确认应用服务

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceQueryService.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommands.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceViews.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/enums/ErrorCodeConstants.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandServiceTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceQueryServiceTest.java`

- [ ] 创建/更新只允许授权现场成员操作本人 DRAFT；项目和设备范围由服务端 PROJ/COM/AST 事实决定，客户端 tenant、actor、状态和 allowedActions 均不可信。
- [ ] create先以 `ProjectParticipantFactApi.inspect(projectId, subjectUserId=null, requiredRoleCodes={PROJECT_MANAGER})`取得当前项目经理与项目事实，确认项目 `ACTIVE/S4` 并冻结 `projectVersion/factVersion`，同时要求调用 actor 命中 `ProjectScopeApi.ACTION_EDIT`；update、submit、raise/resolve 在写事务中对已冻结的项目经理事实执行上述锁定重验，confirm则以调用 actor 和 `{PROJECT_MANAGER}`重验。S3未审核通过、非S4、无当前项目经理、项目已非ACTIVE或任一版本陈旧均在业务写前拒绝。
- [ ] `submit` 同时锁定重验 PROJ、COM/AST范围和 PLT 文件事实，再按领域规则计算候选状态；失败保留最近已提交草稿值但不推进状态、不产生确认事实。
- [ ] `confirm` 只允许 `effectiveRoleCodes`包含 `PROJECT_MANAGER`且 `ProjectScopeApi.ACTION_EDIT`命中的本人负责项目，要求 `Idempotency-Key/If-Match`及已冻结项目版本；全局角色或ACTION_EDIT单独命中均不足。同键同规范化命令重放，同键异命令冲突，旧版本无副作用。
- [ ] 确认事务原子追加批次/明细/事实版本、冻结证据 revision、审计和 Outbox；任一 Owner 事实过期或不可用整体回滚。
- [ ] `raise/resolve-difference` 只追加明确版本；补签、拒收保持、豁免和 successor DRAFT 走显式命令，不提供通用状态 PATCH。
- [ ] 查询返回批次、明细、差异、证据摘要、当前版本和服务端 `allowedActions`，分页按数据范围与稳定排序执行。

### Task 6：DeliveryEvidence、Outbox/Inbox 与双阶段重试

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceService.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/event/ImplementationEvidencePublishedMessage.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/event/ArtifactAcceptedMessage.java`
- Create: `pms-module-engineering-api/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/event/ArtifactArchivedMessage.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceEventFactory.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceOutboxDeliveryJob.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArtifactCallbackEventListener.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArtifactCallbackHandler.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceRetryJob.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceRetryService.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceServiceTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceOutboxDeliveryJobTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArtifactCallbackHandlerTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/evidence/ArrivalEvidenceRetryJobTest.java`

- [ ] 草稿上传/换版只接收 PLT 返回的稳定 `artifactId/versionNo/referenceKey/fileFactVersion`，追加 EXE-01 revision；不接收原始 URL，不下载文件正文。
- [ ] confirm通过 `PlatformCommandExecutionApi.SuccessFacts.businessEvents`与业务提交同事务写 `ImplementationEvidencePublished` Outbox；事件 payload 冻结同一 `evidenceId/revision/artifactId/fileVersion`。平台幂等 scope 固定为 `IMP:ARRIVAL_CONFIRM:{tenantId}:{acceptanceId}:{actorId}:{Idempotency-Key}`。排队成功仅到 `PUBLISHED_PENDING_ACC`。
- [ ] `ArrivalEvidenceOutboxDeliveryJob`使用 `PlatformOutboxDeliveryApi.claimDue`只领取 `ImplementationEvidencePublished`，校验 tenant/eventId/payload 后用生产 `ApplicationEventPublisher`发布 `ImplementationEvidencePublishedMessage`；成功 `markDelivered`，异常按平台 `retryCount/nextRetryTime`指数退避（1、2、4…最多60分钟）调用 `scheduleRetry`。它是模块化单体内正式传输适配，不创建ACC业务事实。
- [ ] ACC Owner 后续从自己的事务/Outbox发布 `ArtifactAcceptedMessage/ArtifactArchivedMessage`；IMP生产 `ArtifactCallbackEventListener`只消费这两个公开消息并调用 handler，不实现伪ACC Producer。生产契约未形成时消息不会到达，IMP等待态和重试继续保留，Implementation Done仍阻断。
- [ ] 入向持久 Inbox 明确复用 `PlatformCommandExecutionApi`/既有 `plt_idempotency_record`，不新增表：scopeCode=`IMP:ARRIVAL_EVIDENCE_CALLBACK:{eventType}`、actorId=`0`、key=`eventId`、requestDigest=规范化回执载荷；业务状态推进、平台幂等完成点和安全审计同事务。同 eventId同载荷重放首次结果，异载荷冲突；回执仍以 `tenantId/evidenceId/revision` 关联，旧序/错配只审计。
- [ ] Accepted 前失败使用 `ARCHIVE_PENDING_RETRY`；Accepted 后 Archived 超时使用 `ARCHIVE_ACK_PENDING_RETRY`；两者均重发同一 revision，不创建新 revision、不回滚签收事实。
- [ ] 匹配 Archived 从 `ACCEPTED_PENDING_ARCHIVE|ARCHIVE_ACK_PENDING_RETRY` 幂等进入 `ARCHIVED`；重复 Accepted 在 Accepted 后等待态自环。
- [ ] `ArrivalEvidenceRetryJob`通过 Mapper XML `FOR UPDATE SKIP LOCKED`按 `acc_next_retry_at,evidence_id`并发领取到期根；`ArrivalEvidenceRetryService`以 scopeCode=`IMP:ARRIVAL_EVIDENCE_RETRY`、key=`evidenceId:revision:status:retryCount`执行平台幂等命令，同事务增加 retryCount、计算1～60分钟退避、设置 nextRetryAt并写同 revision 的新 Outbox eventId。任务重入或多实例只能一个成功。

### Task 7：ArrivalAcceptanceFactApi 生产实现

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImpl.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiImplTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/api/arrival/ArrivalAcceptanceFactApiMySqlTest.java`

- [ ] `inspect` 无副作用读取当前 COM/AST 水位与 IMP 已确认来源，按设备/订单行稳定排序计算项目事实。
- [ ] `lockAndRevalidate` 比较期望 factVersion 与 watermark，按锁定顺序重验；范围、归属、事实版本、豁免有效性或 reopened 任一变化返回 STALE。
- [ ] 多批来源按稳定 ID 升序完整返回，不压缩成伪来源；缺失、未知、不可用、越权和版本冲突失败关闭。
- [ ] 用真实 MySQL 验证并发确认/重开时旧消费者水位失效，且只读 API 不写审计或第二完成表。

### Task 8：REST 契约、权限与错误映射

**Files:**

- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceController.java`
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/vo/*.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceControllerContractTest.java`

- [ ] 实现 Feature Spec 锁定的 `/api/v1/pms/arrival-acceptances` GET/POST、详情 PATCH 和四个 action；不复用旧 `/pms/eng-arrival` 路径。
- [ ] Header、状态码和错误体明确区分校验、权限、范围过期、If-Match 冲突、幂等冲突、Provider 不可用和证据不可用。
- [ ] 权限固定为 physical contract 五项，后端每个入口均执行功能权限与数据范围；前端按钮隐藏不能替代服务端授权。
- [ ] 合约测试覆盖 allowedActions、分页上限、空范围空页、跨租户/越权、旧版本和同键异请求无副作用。

### Task 9：菜单、权限、Job注册与可迁移示例边界

**Files:**

- Create at serial merge: `sql/migrations/V{next+1}__fimp002_arrival_acceptance_seed.sql`
- Modify: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceMigrationContractTest.java`

- [ ] 在现有现场实施菜单体系新增一个到货签收可见菜单和五项锁定权限，不写角色授权，不覆盖旧到货菜单/权限。
- [ ] 以确定性高段ID和 `handler_name` 幂等登记初始暂停的 `arrivalEvidenceOutboxDeliveryJob`（每30秒）、`arrivalEvidenceRetryJob`（每分钟）与 `arrivalLegacyReconciliationJob`；ACC生产`ImplementationEvidencePublished`消费者、契约测试和同步传播边界成立后，才在同一启用提交中显式启用证据投递与证据重试Job并加入Quartz启动同步；Task 12核验生产COM/AST/PLT Provider和迁移前置条件后才显式启用旧数据核对Job。Job重试业务字段在 `imp_delivery_evidence`，Outbox传输重试字段继续由平台 Outbox 持有。
- [ ] 仅播种正式定义的字典状态、差异类型和菜单资源；无正式名称或值域的业务数据不臆造。
- [ ] 示例数据如需用于隔离验收，必须使用专用高段 ID/前缀、`creator='seed'`、可重放且不覆盖用户事实；旧测试种子不得升级为已接受项目事实。
- [ ] 验证迁移前向、重复执行、旧菜单仍可访问和没有自动指派资源。

### Task 10：新前端工作台与组件测试

**Files:**

- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/arrival-acceptance/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalAcceptanceForm.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalLineEditor.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalDifferencePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/components/ArrivalEvidencePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.spec.ts`

- [ ] 新页面提供授权项目分页、批次创建/详情、设备或数量明细、差异/处置、证据 revision、部分/完整候选状态和项目经理确认；生命周期只走服务端 action。
- [ ] 文件面板复用 PLT 文件 API/组件并保存稳定引用，不展示可编辑原始 URL；上传失败和 ACC 待重试与签收事实分开呈现。
- [ ] `If-Match` 与幂等键在响应未知时保留，409 后刷新完整聚合，不在本地猜测成功状态。
- [ ] 按服务端 allowedActions 和权限控制入口，同时验证直接请求仍由后端拒绝。
- [ ] 组件测试覆盖部分签收、差异、补签、豁免失效、旧版本刷新、长文件名和 320/768/1024/1440 响应式；旧 arrival 页面零修改。
- [ ] 验证：定向 Vitest、`corepack pnpm ts:check`、定向 ESLint/Stylelint、`corepack pnpm build:local`。

### Task 11：后端聚焦回归与真实 MySQL 闭环

**Files:**

- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceApplicationMySqlTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/arrivalacceptance/ArrivalAcceptanceConcurrencyMySqlTest.java`

- [ ] 使用 Docker Compose 权威 MySQL/Redis 和从 V1 升级的空库验证五表、迁移、事务、事实版本与事件恢复。
- [ ] 覆盖多批部分到货、全量确认、单项未到、差异/拒收、补签、有效/过期豁免、COM/AST 水位变化、并发确认、同键重放/冲突、越权和跨租户。
- [ ] 覆盖确认事务中 PLT/Owner 重验失败时批次、事实、证据、Outbox 和审计均无半写；Accepted/Archived 重复、乱序、超时可幂等恢复。
- [ ] 覆盖非项目经理、非 `ACTIVE/S4`、陈旧 projectVersion/factVersion、只有全局角色或只有 ACTION_EDIT 的直接请求，断言批次、事实、证据、Outbox、成功审计均零副作用。
- [ ] 运行 engineering-api/engineering Reactor 测试和既有旧 Arrival 定向回归，证明旧代码、表和接口行为未改变。

### Task 12：生产 Provider 联调与真实浏览器验收

**Files:**

- Create: `docs/superpowers/evidence/f-imp-002/implementation-evidence.md`
- Modify: `tasks/features/F-IMP-002.md`

- [ ] 先核验 COM/AST/PROJ/PLT/ACC 生产 Provider 与锁定公开契约一致；缺任一生产输入时在 Task 记录 `BLOCKED_BY_DEPENDENCY`，保留已完成的独立工作，但不得以 Fake 继续正向验收。
- [ ] COM/AST/PLT生产核对通过并备份旧源后，显式启用 `arrivalLegacyReconciliationJob`，记录扫描水位、逐原因数量、平台审计引用和目标 `legacy_source_id`；未通过时保持暂停，不手工改库、不把待核对行算作迁移完成。
- [ ] 为当前工作树重新分配不冲突的前后端端口，前端代理指向同分支后端；记录端口、进程、提交和数据库版本，不复用其他 Feature 应用端口。
- [ ] 真实登录完成创建草稿、部分签收、差异、补签/明确豁免、项目经理确认、刷新恢复、证据发布与 ACC 回执待重试/归档展示。
- [ ] 浏览器负向覆盖越权、旧 If-Match、同键异请求、范围/归属变化、文件失效、空范围和 Provider 失败；检查 console、page error、关键网络请求及页面响应式。
- [ ] 真实浏览器正向必须由生产 COM/AST/PLT/ACC 事实驱动；不得直接造 IMP 快照、改库状态或把受控替身当证据。

### Task 13：Feature 自审、追溯与 Implementation Done Gate

**Files:**

- Modify: `docs/traceability/requirement-matrix.md`
- Modify: `features/feature-status.json`
- Modify: `tasks/features/F-IMP-002.md`
- Modify: `docs/superpowers/evidence/f-imp-002/implementation-evidence.md`

- [ ] 对照 AC-FIMP002-001～007、旧实现审计、物理契约和 FactApi 契约逐条自审；运行 `git diff --check`、追溯生成检查、Phase 2/3 校验、后端/前端测试、真实 MySQL 和浏览器矩阵。
- [ ] 代码评审重点检查模块 Owner、SQL 查询规则、状态机入口、服务端授权、幂等/CAS、不可变历史、回执乱序、旧实现零修改和测试 Fake 未进入生产装配。
- [ ] 证据绑定候选提交、数据库迁移、Provider 版本、端口、测试命令和浏览器结果，并明确仍未覆盖 Deployment/SIT/UAT/Release。
- [ ] 送独立 Implementation Done 复审；只有正式 GO 后才把 Task/Feature/Requirement 投影更新为完成。NO-GO 时保持未完成并只整改裁决项。

## 验证矩阵

| 验收点 | 主证据 |
|---|---|
| AC-FIMP002-001 多批/部分/全量范围 | 领域测试、真实 MySQL、真实浏览器 |
| AC-FIMP002-002 超量/重复/跨租户/空范围/Owner失败 | 服务测试、权限合约、事务回滚 |
| AC-FIMP002-003 状态、差异、补签、豁免、重开 | 状态机测试、事实版本并发、浏览器 |
| AC-FIMP002-004 权限、If-Match、幂等 | Controller合约、MySQL并发、直接请求负向 |
| AC-FIMP002-005 FileReference与ACC双阶段重试 | PLT契约验证、Outbox/Inbox测试、真实回执联调 |
| AC-FIMP002-006 旧数据迁移与旧功能不变 | 全新库迁移、待核对结果、旧Arrival回归 |
| AC-FIMP002-007 真实基础设施与浏览器 | Docker Compose MySQL/Redis、生产Provider、真实浏览器 |

## 明确排除

- 修改旧 `pms_eng_arrival/pms_eng_deliverable` 业务行为、旧页面或旧 API；
- 直接读 COM/AST/PROJ/PLT/ACC 表或依赖其 `-biz`、Service、Mapper；
- 把发货、装箱、设备归属、旧 tinyint、事件投递成功或测试种子解释为已签收/已归档；
- 重复下载/复制附件、保存原始 URL 为权威证据、覆盖历史 revision；
- 安装、配置、联调业务 Feature、IMP-01其他交付件、ACC审核归档 Owner、CUT状态、自动指派、V2/V3；
- Deployment、SIT、UAT、Release 结论。
