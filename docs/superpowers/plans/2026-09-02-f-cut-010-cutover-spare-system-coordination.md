# F-CUT-010 割接备件系统协同 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 计划 ID：`NPDMS-FCUT010-TECHPLAN-20260902-01`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Feature Ready：`READY / GO@c4b1a939`

**Goal:** 交付`CUT-08@V2=FULL`的CUT完整正向闭环：从P2/P3权威事实识别备件需求，发起外部协同，保存外部申请引用与不可变状态版本，追加人工证据，并在任务工作台和P5完整详情安全展示。

**Architecture:** CUT新增独立`spare`应用切片和三张Owner表，不修改旧`pms_cut_*`路径，也不在CUT重建库存、审批、运输或RMA业务。INT-06只以`SpareApplicationGateway`消费端口和CUT拥有的`CutoverSpareCallbackApi`回调合同接入；生产INT-06 Provider不存在时，`src/test`确定性替身驱动正常闭环，生产不注册Fake/fallback。

**Tech Stack:** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vitest、平台幂等/操作审计、PLT `FileArtifactApi`。

**Spec:** `specs/features/F-CUT-010-cutover-spare-system-coordination.md`

## Global Constraints

- 只覆盖`CUT-08@V2=FULL`；`F-CUT-010`是Feature编号，不是`CUT-10`需求。
- 备件需求只来自当前已提交P2评估`sparePartApplied=true`或当前适用P3 `MAJOR_PROJECT_SPARES`系统匹配清单项；P3来源版本固定为清单项自身`id + version`。
- CUT只保存平台请求、外部引用、原始状态revision和PLT人工证据，不保存型号、数量、库存、审批、发货、到货、领用、借还、调拨或RMA事实。
- `bindExternalReference`首次绑定必须在同一CUT事务/CAS内执行`REQUEST_PENDING -> EXTERNAL_REFERENCED`；同值重放零业务写。
- P5只扩展现有`FULL`详情；`FINAL_RESULT_ONLY`和`REASSIGNMENT_ONLY`不得出现备件字段，备件事实不参与审批决定和P6门禁。
- 生产INT-06 Provider、PROJ/AST/PLT正式接线和唯一生产装配未形成时，服务、Controller和页面只通过显式测试装配验证；不得注册生产Fake/fallback。
- 新查询遵守`docs/coding/database-query-interface.md`：场景Query单参数，动态集合/锁查询进入Mapper XML，显式tenant/deleted条件。
- Flyway只在实际串行合入时选择下一个空闲版本；不修改已执行迁移，不预约V161。
- 每个Task先完成已批准的最小正向实现，再运行与该正向路径直接相关的合同、Mapper、单元、MySQL或组件验证；不得把RED、拒绝矩阵、故障注入或未实现能力测试作为编码前置或Gate条件。

---

## 1. 文件与模块责任

| 责任 | 文件或目录 | 处理 |
|---|---|---|
| CUT公共回调合同 | `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/` | 新增`CutoverSpareCallbackApi`、绑定/状态命令、结果和封闭公共异常；不暴露DO |
| INT-06消费端口 | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/SpareApplicationGateway.java` | 定义`initiate/queryStatus`精确命令、结果和异常；无生产实现 |
| PLT消费端口 | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/CutoverSpareFilePort.java` | Task 1先定义最窄`inspect/lockAndRevalidate`；Task 3查询冻结artifact/version的授权displayName，Task 4写前锁定重验；生产Adapter须消费`FileArtifactApi`，不读PLT表 |
| 领域与编排 | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/` | 需求快照、发起/刷新/证据、首次绑定、状态回调、查询、稳定错误分类 |
| CUT物理Owner | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/`、`pms-module-cutover/src/main/resources/mapper/spare/` | 三张Owner表、场景Query、锁查询和条件CAS |
| REST | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverSpareController.java`、`pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/spare/` | 四个用户端点、严格请求、真实HTTP错误Envelope；不承接内部回调 |
| P5安全投影 | `CutoverApprovalQueryService.java`、`CutoverApprovalViews.java`、`CutoverApprovalResponses.java` | 只给`ApprovalDetail/FULL`增加`spareSupport`，其他view不变 |
| 工作台UI | `src/api/pms/cutover/cutover-task/index.ts`、`components/CutoverSpareSupportPanel.vue`、`index.vue` | 需求、申请、状态、证据及allowedActions；无第三方业务页面 |
| Schema/种子 | `sql/migrations/V{actual}__fcut010_spare_system_coordination.sql` | 三表、唯一键/CHECK、一个权限种子；无历史回填、无角色授权 |
| 证据 | CUT后端测试、隔离MySQL、CUT Vitest | 受控INT-06/PROJ/AST/PLT正常闭环；不冒充生产Provider或真实浏览器 |

## 2. 锁定接口与事务

### 2.1 INT-06消费端口

```java
public interface SpareApplicationGateway {
    SpareInitiationProviderResult initiate(SpareInitiationCommand command);
    SpareStatusProviderResult queryStatus(SpareStatusQuery query);
}
```

- `SpareInitiationCommand`精确携带受信tenant、一次分配的platformRequestId、任务/项目/设备冻结事实、`SpareNeedSnapshot`和correlationId。
- 接受结果必须含不可变`externalRequestId`，且`externalApplicationNo/launchUrl`至少一个非空；只含launchUrl时落`REQUEST_PENDING`。
- `queryStatus`只允许已绑定申请号；结果的system/request/application身份必须与查询完全相等。
- `SpareApplicationGatewayException`封闭`PROVIDER_UNAVAILABLE/OWNER_DATA_CORRUPTED/REFERENCE_IDENTITY_CONFLICT/STATUS_VERSION_CONFLICT`；CUT不得按异常消息分类。

### 2.2 CUT回调API

```java
public interface CutoverSpareCallbackApi {
    SpareExternalReferenceBindingResult bindExternalReference(SpareExternalReferenceBindingCommand command);
    SpareStatusCallbackResult acceptStatus(SpareStatusCallbackCommand command);
}
```

- 公共Facade先校验受信`TenantContextHolder`，再认领平台Inbox；缺失/错租户零平台认领、零业务访问。
- 绑定操作锁`tenantId + platformRequestId`；首次绑定以单CAS原子写申请号和`EXTERNAL_REFERENCED`，同值重放返回同一引用。
- 状态回调锁外部引用，按正数`statusVersion`追加不可变revision；低版本只审计、同版本同载荷重放、同版本异载荷永久冲突。
- 公共API实现不读取INT表，不调用第三方，不自行切换租户；生产Bean装配与完整CUT服务一起留到依赖接通Gate。

### 2.3 用户命令两阶段事务与重放恢复

- `initiate`第一阶段固定使用`scope=CUT:SPARE_INITIATE_INTENT`和用户`Idempotency-Key`调用`PlatformCommandExecutionApi.execute`。该平台事务只完成权限/Owner重验、稳定`platformRequestId`分配、`REQUEST_PENDING`意图插入和Intent SuccessFacts；不得调用INT-06。`REPLAY_COMPLETED`返回同一`applicationReferenceId/platformRequestId`。
- 第一阶段提交后，公共编排读取当前意图：已有`externalRequestId`表示Provider结果已收口，直接返回当前投影；仍无`externalRequestId`的`REQUEST_PENDING/RETRY_PENDING`才在事务外以同一platformRequestId调用`SpareApplicationGateway.initiate`。
- Provider接受结果由第二阶段`scope=CUT:SPARE_INITIATE_RESULT`、`key=platformRequestId`的独立`PlatformCommandExecutionApi.execute`收口；digest覆盖规范化结果，事务内以身份/version CAS写外部request/application/launch/status和Result SuccessFacts。同平台请求出现不同Provider结果形成永久冲突；同结果重放只返回当前投影，不重复revision。
- Provider暂时失败或结果未知由`scope=CUT:SPARE_INITIATE_ATTEMPT`、`key=applicationReferenceId:retryCount`的独立平台事务锁当前意图，原子写`RETRY_PENDING/retryCount+1/lastFailure`和Attempt SuccessFacts；不完成Result scope。用户以原Idempotency-Key重放时第一阶段返回既有意图，随后仍用同platformRequestId再次调用Provider，避免永久停留。
- `refresh`第一阶段固定使用`scope=CUT:SPARE_REFRESH_INTENT`和用户`Idempotency-Key`，事务内只验证已绑定引用、冻结`applicationReferenceId/externalApplicationNo/expectedStatusVersion`并完成Intent SuccessFacts；不调用INT-06。重放取得同一冻结查询身份。
- refresh第一阶段提交后，若当前statusVersion已经高于冻结expectedStatusVersion，则直接返回当前投影；否则在事务外用同一申请身份调用`queryStatus`。结果统一经`scope=CUT:SPARE_STATUS_RESULT`、`key=applicationReferenceId:statusVersion`收口，事务内追加revision、切换current pointer并完成Result SuccessFacts；暂时失败经`scope=CUT:SPARE_REFRESH_ATTEMPT`、`key=applicationReferenceId:retryCount`原子写`RETRY_PENDING/retryCount+1`，原Intent重放可再次查询。
- `addEvidence`是单阶段平台事务：PLT先inspect，`PlatformCommandExecutionApi.execute`事务内以冻结轴lockAndRevalidate后插入证据并完成SuccessFacts。
- 锁序固定：`cut_task -> PROJ/AST Owner facts -> cut_spare_application_reference -> cut_spare_status_revision current`；回调只锁申请引用和当前revision。

## 3. Task 1：公共回调合同、INT端口与领域Codec

**Files:**
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApi.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackException.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/dto/*.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/SpareApplicationGateway.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/CutoverSpareFilePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/SpareNeedSnapshotCodec.java`
- Test: `pms-module-cutover-api/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApiContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/SpareNeedSnapshotCodecTest.java`

**Produces:** 后续Schema、应用和回调实现唯一使用的Java合同；不产生Bean或业务写。

- [ ] 实现最小公共API/DTO、INT端口和`CutoverSpareFilePort`类型；文件端口的`inspect/lockAndRevalidate`返回`artifactId/referenceKey/versionNo/FileFactVersion/scopeVersion/displayName`，不增加INT模块实现、HTTP客户端或第三方配置。
- [ ] 实现`SpareNeedSnapshotCodec`，固定ASSESSMENT与CHECKLIST_RISK判别联合、稳定排序和P3 `item.id + item.version`。
- [ ] 实现完成后补合同/Codec单元验证，固定record字段、文件displayName事实、trim/长度、WireLong/时间及正向编解码；执行`mvn -pl pms-module-cutover-api,pms-module-cutover -am -DskipITs -Dtest=CutoverSpareCallbackApiContractTest,SpareNeedSnapshotCodecTest test`并确认PASS。
- [ ] 提交并申请Task 1 Contract/Code Review Gate；GO前不建表。

## 4. Task 2：三表Schema、DO与Mapper合同

**Files:**
- Create: `sql/migrations/V{actual}__fcut010_spare_system_coordination.sql`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/spare/CutoverSpareApplicationReferenceDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/spare/CutoverSpareStatusRevisionDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/spare/CutoverSpareManualEvidenceDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/CutoverSpareApplicationReferenceMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/CutoverSpareStatusRevisionMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/CutoverSpareManualEvidenceMapper.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/query/SpareApplicationQueries.java`
- Create: `pms-module-cutover/src/main/resources/mapper/spare/*.xml`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut010MigrationContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/spare/CutoverSpareMapperContractTest.java`

**Produces:** `cut_spare_application_reference`、`cut_spare_status_revision`、`cut_spare_manual_evidence`和运行时可解析Mapper。

- [ ] 在合入时重查最高Flyway号，写三表DDL、唯一键、封闭CHECK和`pms:cutover-task:manage-spare`幂等权限种子；不授角色、不回填历史。
- [ ] 实现DO、单场景Query和XML锁/CAS；禁止SQL注解、`${}`、Map参数和跨Context表。
- [ ] 实现完成后用静态合同固定`REQUEST_PENDING/EXTERNAL_REFERENCED/RETRY_PENDING`、current marker、文件版本轴与不可变历史约束。
- [ ] 用`XMLMapperBuilder + BoundSql + MetaObject`验证正向动态参数；隔离MySQL 8.4从空库升级并写入一次合法意图、引用、状态revision和人工证据。
- [ ] 提交并申请Task 2 Schema/Mapper/MySQL Gate。

## 5. Task 3：需求快照、查询与P5安全投影

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareNeedAssembler.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareQueryService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/view/CutoverSpareViews.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/view/CutoverApprovalViews.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/approval/CutoverApprovalResponses.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareQueryServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/ControlledCutoverSpareFilePort.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java`

**Produces:** 工作台详情和P5 `FULL`安全摘要；不提供写命令。

- [ ] 实现Assembler，只读取CUT现有assessment/checklist Mapper与当前未失效事实；未知形状失败关闭。
- [ ] 实现详情稳定排序和`INITIATE/REFRESH/ADD_EVIDENCE` allowedActions投影；required=false不返回INITIATE。
- [ ] 查询人工证据时用冻结`artifactId/versionNo/referenceKey/FileFactVersion/scopeVersion`调用`CutoverSpareFilePort.inspect`，`displayName`只取返回的授权`FileArtifactVersionFact.name`投影；显式受控端口用于Task测试，禁止从referenceKey猜名称或新增名称列。
- [ ] 给`ApprovalDetail`增加`SpareSupportApprovalSummary`，仅`full(...)`读取；final/reassignment records保持原字段集合。
- [ ] 实现完成后验证P2来源、P3无result来源、双来源，以及带PLT displayName的工作台/P5 FULL正向投影和安全裁剪。
- [ ] 提交并申请Task 3 Query/P5 Projection Gate。

## 6. Task 4：发起、刷新和人工证据应用服务

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/InitiateSpareApplicationCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/RefreshSpareApplicationCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/AddSpareManualEvidenceCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/result/CutoverSpareCommandResults.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/ControlledSpareApplicationGateway.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/ControlledCutoverSpareFilePort.java`

**Produces:** 显式组装即可运行的CUT写编排；`src/main`无INT Fake，无完整生产Bean。

- [ ] 按2.3实现`InitiateIntentExecutor/InitiateResultExecutor/SpareRetryExecutor`和公共编排：Intent平台事务提交后才调用Provider，Result与Attempt各用独立scope/key收口；launch-only为REQUEST_PENDING，已有申请号为EXTERNAL_REFERENCED。
- [ ] 按2.3实现refresh Intent与Status Result两阶段；只查询已绑定申请号，高版本追加并成为当前revision。
- [ ] 实现人工证据的PLT inspect→锁定重验→不可变插入，scope固定`CUT/CUTOVER_TASK/{taskId}/SPARE_MANUAL_EVIDENCE/READ`。
- [ ] 实现完成后用受控Provider验证正常initiate的INT调用发生在Intent事务提交后、Result事务收口、同用户键重放返回同一platformRequestId；再验证正常refresh和人工证据追加。测试替身只位于`src/test`。
- [ ] 提交并申请Task 4 Application/Idempotency Gate。

## 7. Task 5：首次引用绑定与状态回调

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackHandler.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApiImpl.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackHandlerTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackMySqlTest.java`

**Produces:** CUT拥有的内部回调路径；未接INT生产调用方也可由合同测试和受控调用验证。

- [ ] 以平台Inbox语义实现绑定，确保申请号和状态在同一CAS中更新；BindingResult固定返回EXTERNAL_REFERENCED。
- [ ] 实现状态回调eventId幂等、版本序列、current marker切换和审计详情；回调不得改变P2～P6状态。
- [ ] 实现完成后用单元与真实MySQL验证一次正常首次绑定、同值重放和一次递增状态回调，断言申请引用、current revision和P2～P6事实正确。
- [ ] 保持实现类无生产`@Service/@Component/@Bean`，直至完整依赖装配Gate；提交并申请Task 5 Callback Gate。

## 8. Task 6：严格REST和错误合同

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverSpareController.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverSpareRequestCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverSpareContractException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/spare/CutoverSpareRequests.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/spare/CutoverSpareResponses.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/enums/ErrorCodeConstants.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverSpareControllerMockMvcTest.java`

**Produces:** 四个用户REST操作的可执行外壳；仍通过测试配置显式装配。

- [ ] 实现detail/initiate/refresh/addEvidence，correlationId来自受信请求上下文，不作为业务请求字段；五权限只新增`manage-spare`。
- [ ] 以稳定异常类型映射幂等冲突、版本陈旧、业务门禁、PLT/INT Provider不可用和外部身份冲突；禁止按消息文本猜测。
- [ ] 实现完成后用真实MockMvc验证四个正常端点的Header、exactKeys、WireLong和CommonResult成功Envelope；不注册生产Controller Bean。
- [ ] 提交并申请Task 6 REST/MockMvc Gate。

## 9. Task 7：工作台UI与组件交互

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverSpareSupportPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverSpareComponents.spec.ts`

**Produces:** CUT任务工作台内的备件协同区，不新增第三方业务页面。

- [ ] 定义WireLong/WireDateTime类型和四端点客户端；所有ID保持string安全，不做`Number(...)`转换。
- [ ] 真实mount组件，按服务端allowedActions与`manage-spare`共同显示发起/刷新/证据按钮；只读状态不推导“已就位/已完成”。
- [ ] 使用现有文件上传组件提交冻结PLT事实，不提交URL；launchUrl只作为受信HTTPS跳转且不进入P5摘要。
- [ ] 复用写屏障：命令成功后刷新失败时下一次写只重试详情刷新，不重复业务命令；RETRY_SAME_KEY保留原幂等键。
- [ ] 覆盖320/768/1024/1440布局、键盘焦点、P2/P3需求、launch-only后绑定、状态刷新、人工证据和Snowflake字符串。
- [ ] 运行CUT定向Vitest与`pnpm ts:check`；提交并申请Task 7 UI Gate。

## 10. Task 8：真实MySQL受控正向闭环与状态收口

**Files:**
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSparePositiveLoopMySqlTest.java`
- Modify: `tasks/features/F-CUT-010.md`

**Produces:** CUT自有闭环证据和清晰依赖状态，不冒充生产INT-06或真实浏览器。

- [ ] 在隔离MySQL 8.4全量Flyway上显式组装真实Mapper、平台幂等/审计和受控INT/PROJ/AST/PLT端口。
- [ ] 跑通`P2或P3需求 -> initiate launch-only -> bindExternalReference -> status callback/refresh -> manual evidence -> workbench/P5 FULL`。
- [ ] 断言FINAL_RESULT_ONLY/REASSIGNMENT_ONLY无备件字段，P5决定/P6门禁及旧`pms_cut_*`表零变化。
- [ ] 验证同键正常重放不重复业务行、三个Owner表保留不可变历史；清理独立容器/网络/卷。
- [ ] 汇总后端、MySQL、MockMvc、Vitest证据并申请Task 8 Gate；若生产Provider未接通，Feature只可记`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`。

## 11. 验证命令与完成口径

```powershell
mvn -pl pms-module-cutover-api,pms-module-cutover -am -DskipITs -Dtest=CutoverSpareCallbackApiContractTest,SpareNeedSnapshotCodecTest,CutoverSpareMapperContractTest,CutoverSpareQueryServiceTest,CutoverSpareApplicationServiceTest,CutoverSpareCallbackHandlerTest,CutoverSpareControllerMockMvcTest test
mvn -pl pms-module-cutover -DskipITs=false -Dtest=CutoverSparePositiveLoopMySqlTest test
pnpm --dir yudao-ui/yudao-ui-admin-vue3 exec vitest run --config src/views/pms/cutover/cutover-task/vitest.config.mjs src/views/pms/cutover/cutover-task/cutoverSpareComponents.spec.ts
pnpm --dir yudao-ui/yudao-ui-admin-vue3 ts:check
py -3 -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --features specs/features --tasks tasks/features --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json --check
git diff --check
```

受控正向闭环通过只证明CUT实现可运行。以下任一缺失时不得声明Implementation Done：生产INT-06 Provider及双向合同传播、正式PROJ/AST/PLT装配、唯一生产Service/Callback/Controller Bean、真实MySQL生产接线回归、真实浏览器正向闭环。

## 12. 风险与回退

- **外部结果未知：** 不创建第二requestId；保留同意图重试，禁止用HTTP 200推断业务完成。
- **申请号晚绑定：** 只经受信回调原子迁移状态；不允许用户输入申请号或修改外部身份。
- **P3无结果：** 需求来源使用清单项自身version，不为通过发起而补造result。
- **P5流程污染：** 安全摘要只读组装，不写审批source snapshot，不进入allowedActions或决定守卫。
- **生产依赖缺失：** 端口和测试替身可提交，生产无Fake/fallback；状态明确阻断Done。
- **共享Flyway竞争：** 实施迁移前重查实际最高版本；若冲突则顺延文件号，不修改已执行脚本。

## 13. Technical Plan Gate

当前：`REVIEW_REQUIRED`。本计划只授权送独立Technical Plan复审；GO前不得实施Task 1或任何DDL、后端、前端、测试代码。
