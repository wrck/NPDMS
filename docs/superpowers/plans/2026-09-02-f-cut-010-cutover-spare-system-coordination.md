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
- 先完成可运行正向能力，再补本Feature风险直接对应的聚焦验证；每个Task独立Code Review后才进入下一Task。

---

## 1. 文件与模块责任

| 责任 | 文件或目录 | 处理 |
|---|---|---|
| CUT公共回调合同 | `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/` | 新增`CutoverSpareCallbackApi`、绑定/状态命令、结果和封闭公共异常；不暴露DO |
| INT-06消费端口 | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/SpareApplicationGateway.java` | 定义`initiate/queryStatus`精确命令、结果和异常；无生产实现 |
| PLT消费端口 | `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/CutoverSpareFilePort.java` | 最窄`inspect/lockAndRevalidate`，固定CUT文件scope；生产Adapter须消费`FileArtifactApi`，不读PLT表 |
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

### 2.3 用户命令事务

- `initiate/refresh/addEvidence`使用`PlatformCommandExecutionApi.execute`；平台幂等记录、CUT业务写和SuccessFacts在同一事务。
- 外部`initiate/queryStatus`调用发生在CUT数据库事务外；先持久化稳定意图，再调用Provider，再以身份/version CAS收口，超时保留同requestId重试。
- PLT文件先inspect，进入写事务后以冻结轴lockAndRevalidate；失败发生在证据插入前。
- 锁序固定：`cut_task -> PROJ/AST Owner facts -> cut_spare_application_reference -> cut_spare_status_revision current`；回调只锁申请引用和当前revision。

## 3. Task 1：公共回调合同、INT端口与领域Codec

**Files:**
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApi.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackException.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/dto/*.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/SpareApplicationGateway.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/SpareNeedSnapshotCodec.java`
- Test: `pms-module-cutover-api/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApiContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/SpareNeedSnapshotCodecTest.java`

**Produces:** 后续Schema、应用和回调实现唯一使用的Java合同；不产生Bean或业务写。

- [ ] 先写合同测试，固定所有record精确字段、trim/长度、WireLong/时间、封闭结果与异常；运行并确认因类型缺失失败。
- [ ] 实现最小公共API/DTO和INT端口类型；不增加INT模块实现、HTTP客户端或第三方配置。
- [ ] 实现`SpareNeedSnapshotCodec`，固定ASSESSMENT与CHECKLIST_RISK判别联合、稳定排序和P3 `item.id + item.version`。
- [ ] 运行合同/Codec测试并执行`mvn -pl pms-module-cutover-api,pms-module-cutover -am -DskipITs -Dtest=CutoverSpareCallbackApiContractTest,SpareNeedSnapshotCodecTest test`；预期全部PASS。
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
- [ ] 先写迁移/Mapper失败测试，固定`REQUEST_PENDING/EXTERNAL_REFERENCED/RETRY_PENDING`、状态current marker、文件版本轴、trim与不可变历史约束。
- [ ] 实现DO、单场景Query和XML锁/CAS；禁止SQL注解、`${}`、Map参数和跨Context表。
- [ ] 用`XMLMapperBuilder + BoundSql + MetaObject`验证动态参数；隔离MySQL 8.4从空库升级并验证同platform request、外部request/application、statusVersion唯一性。
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
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java`

**Produces:** 工作台详情和P5 `FULL`安全摘要；不提供写命令。

- [ ] 写失败测试：P2真/P3假、P2假/P3真、双来源、无来源，以及P3无结果仍以item version成立。
- [ ] 实现Assembler，只读取CUT现有assessment/checklist Mapper与当前未失效事实；未知形状失败关闭。
- [ ] 实现详情稳定排序和`INITIATE/REFRESH/ADD_EVIDENCE` allowedActions投影；required=false不返回INITIATE。
- [ ] 给`ApprovalDetail`增加`SpareSupportApprovalSummary`，仅`full(...)`读取；final/reassignment records保持原字段集合。
- [ ] 测试安全裁剪：不含allowedActions、launchUrl、platform/external request id、内部行id、PLT版本轴和原始响应正文。
- [ ] 提交并申请Task 3 Query/P5 Projection Gate。

## 6. Task 4：发起、刷新和人工证据应用服务

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/InitiateSpareApplicationCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/RefreshSpareApplicationCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/command/AddSpareManualEvidenceCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/result/CutoverSpareCommandResults.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/port/CutoverSpareFilePort.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareApplicationServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/ControlledSpareApplicationGateway.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/ControlledCutoverSpareFilePort.java`

**Produces:** 显式组装即可运行的CUT写编排；`src/main`无INT Fake，无完整生产Bean。

- [ ] 先以失败测试固定授权、ownerUserId、If-Match、需求快照、任务/设备Owner重验、平台幂等重放与同键冲突。
- [ ] 实现稳定platformRequestId先持久化、事务外Provider调用、事务内身份CAS收口；launch-only为REQUEST_PENDING，已有申请号为EXTERNAL_REFERENCED。
- [ ] 实现refresh，只查询已绑定申请号；高版本追加、低版本审计且不回退，同版本异载荷冲突。
- [ ] 实现人工证据的PLT inspect→锁定重验→不可变插入，scope固定`CUT/CUTOVER_TASK/{taskId}/SPARE_MANUAL_EVIDENCE/READ`。
- [ ] 验证Provider超时复用同requestId、PLT失败零证据写、平台SuccessFacts失败全事务回滚；测试替身只位于`src/test`。
- [ ] 提交并申请Task 4 Application/Idempotency Gate。

## 7. Task 5：首次引用绑定与状态回调

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackHandler.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/spare/CutoverSpareCallbackApiImpl.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackHandlerTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/spare/CutoverSpareCallbackMySqlTest.java`

**Produces:** CUT拥有的内部回调路径；未接INT生产调用方也可由合同测试和受控调用验证。

- [ ] 写失败测试覆盖首次绑定、同值重放、不同申请号、错tenant/system/externalRequestId、绑定后仍REQUEST_PENDING数据损坏。
- [ ] 以平台Inbox语义实现绑定，确保申请号和状态在同一CAS中更新；BindingResult固定返回EXTERNAL_REFERENCED。
- [ ] 实现状态回调eventId幂等、版本序列、current marker切换和审计详情；回调不得改变P2～P6状态。
- [ ] 真实MySQL验证并发首次绑定仅一个成功、同版本重放一行、异载荷回滚平台认领/业务写/审计。
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

- [ ] 写真实MockMvc失败测试，固定Header缺失/非法、exactKeys、WireLong、权限、404/409/422/503和CommonResult ErrorData。
- [ ] 实现detail/initiate/refresh/addEvidence，correlationId来自受信请求上下文，不作为业务请求字段；五权限只新增`manage-spare`。
- [ ] 以稳定异常类型映射幂等冲突、版本陈旧、业务门禁、PLT/INT Provider不可用和外部身份冲突；禁止按消息文本猜测。
- [ ] 验证错误零副作用及成功响应精确字段；不注册生产Controller Bean。
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
- [ ] 验证并发绑定/回调、平台失败回滚和三个Owner表不可变历史；清理独立容器/网络/卷。
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
