# F-CUT-008 P5提前时间判断与外部提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 计划 ID：`NPDMS-FCUT008-TECHPLAN-20260902-01`
> Technical Plan Gate：`PASS / GO @ e09b150a`
> Feature Ready：`READY / GO @ d9b43077`
> Feature Spec：`specs/features/F-CUT-008-p5-lead-time-and-external-reminders.md`
> API Contract：`specs/features/F-CUT-008-api-contract.json`
> Physical Contract：`specs/features/F-CUT-008-physical-contract.json`
> External Port Contract：`specs/features/F-CUT-008-external-notification-contract.json`

**Goal：** 交付`CUT-05@V2=FULL`的CUT侧正向闭环：A/B审批实例冻结并展示十类专项提前时间判断，所有P5节点在正确激活时保留站内消息并追加SMS/EMAIL/DINGTALK请求，以受控端口验证受理、重试和未知结果且不改变审批。

**Architecture：** 在既有F-CUT-005聚合上做前向扩展，不建立第二套审批。纯领域计算器从已锁定任务和方案时间形成不可变JSON快照；现有通知表增加渠道投影并以独立外部投递服务消费，站内投递按`IN_PLATFORM`隔离。INT-10/INT-05只表现为CUT消费端口，生产Provider和Job激活不在本Feature实现范围。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Spec：** `specs/features/F-CUT-008-p5-lead-time-and-external-reminders.md`

## Global Constraints

- 只覆盖`CUT-05@V2=FULL`，不得拆成单独计算器Feature或第三方Provider Feature。
- 十类阈值、`CUT_LEAD_TIME_R034_V1`、`Asia/Shanghai`、A/B适用及`actualNaturalDays < requiredDays`必须原样实现；C/D和旧实例返回null。
- 提前时间仅展示，不得进入五项评审、allowedActions、路由、通过/驳回或P4/P6状态迁移。
- 初始PENDING、下一节点实际激活、当前PENDING节点改派才创建外部通知；WAITING节点改派零通知，后续激活使用届时recipient/nodeVersion。
- 站内消息继续使用现有`NotifyMessageSendApi`和V1 deliveryKey；外部渠道只使用`CUT_APPROVAL_EXT:{instanceId}:{nodeNo}:{nodeVersion}:{channel}`。
- INT-10/INT-05只预留接口；受控替身只放`src/test`，不得注册生产Fake/fallback、修改Yudao或直读外部Owner表。
- Provider明确失败、请求合同错误和Owner事实损坏统一进入同key `PENDING_RETRY`；未知结果进入`DELIVERY_UNKNOWN`且不自动重发。
- 先完成每个Task的最小正向实现，再补覆盖该已实现路径的聚焦测试；不以未实现功能的RED测试或无收益负向矩阵阻断正向闭环。
- 新查询遵守`docs/coding/database-query-interface.md`；站内和外部领取使用不同场景Query，XML显式tenant/deleted/channel并稳定排序。
- Flyway只在实际串行合入时读取`sql/migrations`并选下一个空闲版本，不修改V153/V154或其他已执行迁移，不预约版本号。
- `.run/backend.pid`、`.run/frontend.pid`现有删除不属于本Feature，任何Task均不得暂存或提交。

---

## 正向链与事务边界

1. P4提交继续进入F-CUT-005 `start`外层事务；锁定任务和SUBMITTED方案后，A/B计算并编码快照，C/D写禁用/null。
2. 创建节点时先保留现有站内通知，再按`SMS,EMAIL,DINGTALK`固定顺序追加外部PENDING行；审批事务不调用外部端口。
3. 审批详情只解码根表快照；通过、驳回、改派和任务阶段沿用现有状态机。
4. 外部投递批次独立锁渠道行，调用受控端口后CAS到ACCEPTED、PENDING_RETRY或DELIVERY_UNKNOWN；审批根、节点和任务保持不变。
5. UI只在FULL投影且快照非空时展示判断卡片；现有评审表单不接收该字段。

---

### Task 1：提前时间领域规则与不可变快照Codec

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeCalculator.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeCompliance.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodec.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeCalculatorTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/leadtime/CutoverLeadTimeSnapshotCodecTest.java`

**Interfaces:**
- Consumes: grade、task cutoverType/scheduledTime、plan submittedAt。
- Produces: `LeadTimeCompliance calculate(String grade,String cutoverType,LocalDateTime scheduledAt,LocalDateTime submittedAt)`及`encode/decode`。

- [ ] **Step 1: 实现封闭值对象与计算器**

```java
public record CutoverLeadTimeCompliance(String ruleVersion, String timezoneId, String cutoverType,
        long scheduledTime, long planSubmittedAt, int requiredDays,
        int actualNaturalDays, boolean lateSubmission) {}

public CutoverLeadTimeCompliance calculate(String grade, String cutoverType,
        LocalDateTime scheduledAt, LocalDateTime submittedAt) {
    // C/D由调用方跳过；A/B只查十类不可变Map并按Asia/Shanghai业务日期相减。
}
```

Map严格使用API合同十个代码；不读取字典标签、附件或当前时间，不提供默认阈值。

- [ ] **Step 2: 实现精确JSON Codec**

`encode`稳定输出八个exact keys；`decode`拒绝缺键/额外键、未知版本/类型及不一致的`lateSubmission`，round-trip逐字段相等。

- [ ] **Step 3: 补已实现正向边界测试**

覆盖十类映射及每种阈值的`required-1/required/required+1`；验证跨午夜按自然日而非24小时；C/D跳过由调用服务测试覆盖。

- [ ] **Step 4: 运行聚焦测试**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverLeadTimeCalculatorTest,CutoverLeadTimeSnapshotCodecTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: 自审并提交Task 1 Gate**

确认计算器无Spring/Mapper/Clock依赖，规则版本与十类代码逐项等于机器合同。

---

### Task 2：两表前向Schema、DO与渠道隔离Mapper

**Files:**
- Create at serial merge: `sql/migrations/`下实际下一空闲版本的`fcut008_p5_lead_time_notification.sql`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/approval/CutoverApprovalInstanceDO.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/approval/CutoverApprovalNotificationDO.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalNotificationMapper.java`
- Modify: `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalNotificationMapper.xml`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/query/ExternalApprovalNotificationClaimQuery.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/query/ExternalApprovalNotificationDeliveryUpdate.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java`
- Modify test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java`

**Interfaces:**
- Consumes: F-CUT-008 physical contract。
- Produces: 新列、站内/外部独立领取及外部状态CAS。

- [ ] **Step 1: 编写可执行前向ALTER**

迁移先以nullable列加入，确定性回填既有实例`lead_time_enabled=b'0'`、既有通知`channel_code='IN_PLATFORM'`，再收紧NOT NULL且移除默认；删除并重建通知状态CHECK，增加提前时间联合CHECK。不得历史计算、改deliveryKey/messageId/status或补外部事实。

- [ ] **Step 2: 同步DO字段**

```java
// CutoverApprovalInstanceDO
private Boolean leadTimeEnabled;
private String leadTimeSnapshot;
// CutoverApprovalNotificationDO
private String channelCode;
private String providerReferenceId;
private LocalDateTime lastAttemptAt;
```

- [ ] **Step 3: 隔离站内与外部领取**

先保持迁移后既有V1写路径可用：`CutoverApprovalApplicationService.startNew`创建根时显式写`leadTimeEnabled=false/leadTimeSnapshot=null`；现有首节点、下一节点和PENDING改派站内通知入口全部显式写`channelCode=IN_PLATFORM`。Task 2 Gate必须先证明迁移后既有F-CUT-005正常创建/审批仍可写入，Task 3再把A/B根升级为真实V2快照，Task 4再追加外部渠道行；不得依赖数据库默认或把兼容写入口留到后续Task。

现有`selectDueForUpdateSkipLocked`必须增加`channel_code='IN_PLATFORM'`；新增`selectExternalDueForUpdateSkipLocked(query)`只取三外部渠道的PENDING/到期PENDING_RETRY，排除DELIVERY_UNKNOWN。两个Query均按`COALESCE(next_retry_at,create_time),id`排序并在XML使用`FOR UPDATE SKIP LOCKED`。

- [ ] **Step 4: 实现外部CAS更新**

`updateExternalDeliveryIfMatch`同时约束tenant/id/channel/expectedStatus/version，并写providerReferenceId、retry字段、lastAttemptAt；不得修改messageId或sentAt。

- [ ] **Step 5: 验证迁移与Mapper**

```powershell
mvn -pl pms-module-cutover -am -Dtest=Fcut008MigrationContractTest,CutoverApprovalMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

隔离MySQL 8.4从V156基线升级，证明旧行回填、A/B/C/D联合、四渠道状态、站内查询不领取外部行及重复迁移无额外变更。

---

### Task 3：审批创建冻结与FULL详情投影

**Files:**
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/view/CutoverApprovalViews.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/approval/CutoverApprovalResponses.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalControllerContractTest.java`

**Interfaces:**
- Consumes: Task 1 calculator/codec、锁定Task/Plan DO。
- Produces: 根字段持久化与`ApprovalDetail.leadTimeCompliance`。

- [ ] **Step 1: 在start事务冻结快照**

`startNew`在审批insert前读取锁定task.scheduledTime/cutoverType和plan.submittedAt：A/B设置enabled=true和编码JSON；C/D设置false/null。任一A/B来源缺失或类型不在十类映射时使P4提交整体回滚。

- [ ] **Step 2: 扩展领域与Controller投影**

```java
public record ApprovalDetail(...,
        CutoverApprovalSourceSnapshotCodec.ApprovalSourceSnapshot sourceSnapshot,
        CutoverLeadTimeCompliance leadTimeCompliance,
        LocalDateTime decisionAt, String rejectionReason, List<String> allowedActions) {}
```

FULL读取根快照；FINAL_RESULT_ONLY/REASSIGNMENT_ONLY保持既有exact keys。Approve/reject响应沿用同一FULL映射。

- [ ] **Step 3: 证明决定隔离**

正常A/B启动展示快照；C/D和迁移前实例为null。使用相同评审输入分别对late=true/false实例通过，断言节点/任务结果一致。

- [ ] **Step 4: 运行聚焦测试**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalStartServiceTest,CutoverApprovalQueryServiceTest,CutoverApprovalControllerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: 申请Task 3 Gate**

复核详情无当前时间重算、旧V1 snapshot未改形、非FULL投影未泄漏判断。

---

### Task 4：节点激活的三渠道请求创建

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverExternalNotificationRequestFactory.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalExternalNotificationCreationTest.java`

**Interfaces:**
- Consumes: approval root/node/task与notification Mapper。
- Produces: `appendForActivatedNode(root,node,actor,now)`，固定三渠道PENDING行。

- [ ] **Step 1: 实现确定性三渠道工厂**

```java
private static final List<String> CHANNELS = List.of("SMS", "EMAIL", "DINGTALK");
String deliveryKey(long instanceId, int nodeNo, int nodeVersion, String channel) {
    return "CUT_APPROVAL_EXT:" + instanceId + ":" + nodeNo + ":" + nodeVersion + ":" + channel;
}
```

每行模板`CUT_APPROVAL_PENDING_V2`、status=PENDING、retryCount=0、messageId/provider引用/尝试/重试字段为空。

- [ ] **Step 2: 接入三个合法时点**

首节点创建、下一节点WAITING→PENDING成功后、当前PENDING节点改派CAS成功后调用工厂；调用位置紧随既有站内通知insert并处于同一外层事务。

- [ ] **Step 3: 保持WAITING改派零通知**

改派分支以锁定前态判断：WAITING只追加reassignment并更新approver/version；其以后激活时由激活分支读取最新recipient和version创建通知。

- [ ] **Step 4: 验证正常链**

测试A路由：首节点4条通知；中间通过为下一节点新增4条；PENDING改派新增4条且旧记录保留；WAITING改派行数不变，后续激活才新增并指向新recipient。

- [ ] **Step 5: 运行并申请Task 4 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalExternalNotificationCreationTest,CutoverApprovalDecisionServiceTest,CutoverApprovalReassignmentTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

### Task 5：外部消费端口、投递服务与暂停Job候选

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/CutoverExternalApprovalNotificationPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/ExternalApprovalNotificationRequest.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/ExternalApprovalNotificationResult.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/CutoverExternalApprovalNotificationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/CutoverExternalApprovalNotificationTransactionExecutor.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/job/CutoverExternalApprovalNotificationJob.java`
- Create test config: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/ControlledExternalNotificationTransactionConfiguration.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/ControlledCutoverExternalApprovalNotificationPort.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/external/CutoverExternalApprovalNotificationServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/job/CutoverExternalApprovalNotificationJobTest.java`

**Interfaces:**
- Consumes: Task 2外部领取/CAS、机器合同精确请求联合。
- Produces: 受控可组装的发送服务；不产生生产Provider Bean。

- [ ] **Step 1: 定义最窄端口与结果联合**

```java
public interface CutoverExternalApprovalNotificationPort {
    ExternalApprovalNotificationResult send(ExternalApprovalNotificationRequest request);
}
public sealed interface ExternalApprovalNotificationResult {
    record Accepted(String providerReferenceId, LocalDateTime acceptedAt) implements ExternalApprovalNotificationResult {}
    record DeliveryUnknown(String providerReferenceId) implements ExternalApprovalNotificationResult {}
    record ExplicitFailure(String errorCode) implements ExternalApprovalNotificationResult {}
}
```

Request只含合同字段，不含phone/email/ding account/token。

- [ ] **Step 2: 实现唯一事务执行边界**

`CutoverExternalApprovalNotificationService`作为无事务公共Facade调用独立`CutoverExternalApprovalNotificationTransactionExecutor`；Executor的`deliverBatch` public方法标注`@Transactional`（默认REQUIRED），事务必须在`selectExternalDueForUpdateSkipLocked`前开始，并在每行端口结果完成`updateExternalDeliveryIfMatch`后才提交。领取后从锁定CUT根/节点/任务组装请求；Accepted→ACCEPTED，Unknown→DELIVERY_UNKNOWN，ExplicitFailure/端口异常/请求或Owner损坏→PENDING_RETRY。退避复用现有1/2/4/8/16/32/60分钟规则并保持同deliveryKey；任何CAS=0使该批事务回滚，不得在锁释放后盲写。

- [ ] **Step 3: 保持生产装配边界**

Facade、Executor和Job均不加`@Service/@Component`，没有端口Provider、可选注入、空成功或旧Notify API降级。受控MySQL测试用test-only `@Configuration`分别声明Facade、Executor和Controlled Port Bean，并从Spring上下文取得经过真实事务代理的Executor；禁止显式`new Executor`冒充锁测试。未来INT-10/INT-05接通提交再注册唯一生产装配。

- [ ] **Step 4: 验证三种正常结果**

受控端口分别返回Accepted、ExplicitFailure、DeliveryUnknown，断言状态、providerReferenceId、重试字段和审批根/节点版本完全不变；同批次并发领取只处理一次。

- [ ] **Step 5: 运行并申请Task 5 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverExternalApprovalNotificationServiceTest,CutoverExternalApprovalNotificationJobTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

### Task 6：P5提前时间展示卡片

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverLeadTimeComplianceCard.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalPanel.vue`
- Modify test: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts`
- Reuse test config: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/vitest.config.mjs`

**Interfaces:**
- Consumes: `CutoverApprovalDetail.leadTimeCompliance`。
- Produces: A/B只读参考卡片；无写事件。

- [ ] **Step 1: 增加Wire类型**

```ts
export interface CutoverLeadTimeCompliance {
  ruleVersion: 'CUT_LEAD_TIME_R034_V1'
  timezoneId: 'Asia/Shanghai'
  cutoverType: string
  scheduledTime: WireDateTime
  planSubmittedAt: WireDateTime
  requiredDays: number
  actualNaturalDays: number
  lateSubmission: boolean
}
```

`CutoverApprovalDetail`增加`leadTimeCompliance: CutoverLeadTimeCompliance | null`，其他联合成员不增加该字段。

- [ ] **Step 2: 实现只读卡片**

展示计划操作时间、方案提交时间、要求/实际自然日及“是/否”；迟交使用警示样式，非迟交使用成功样式。卡片无按钮、表单、allowedAction或业务请求。

- [ ] **Step 3: 接入FULL面板**

`v-if="view.leadTimeCompliance"`时渲染；C/D/旧实例null时不保留空占位，不改现有五项评审和服务经理复核布局。

- [ ] **Step 4: 运行组件正向测试**

挂载A/B详情验证时间格式和迟交/合规文本，挂载null详情验证不展示；触发原approve事件并断言请求体不含leadTime字段。

```powershell
pnpm vitest run --config src/views/pms/cutover/cutover-task/vitest.config.mjs src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts
pnpm ts:check
```

- [ ] **Step 5: 申请Task 6 Gate**

确认没有修改旧`cut-task`页面、附件逻辑、权限或第三方UI。

---

### Task 7：暂停Job种子与迁移合同

**Files:**
- Create at serial merge: `sql/migrations/`下实际下一空闲版本的`fcut008_external_notification_job_seed.sql`
- Modify test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut008MigrationContractTest.java`

**Interfaces:**
- Consumes: Task 5 Job handler name `cutoverExternalApprovalNotificationJob`。
- Produces: 唯一PAUSED `infra_job`记录；不触发Quartz。

- [ ] **Step 1: 幂等登记暂停Job**

使用确定性高段ID、handler_name=`cutoverExternalApprovalNotificationJob`、status=2、cron=`0/30 * * * * ?`；重复执行保持唯一且不得把既有暂停行改为启用。

- [ ] **Step 2: 保持无生产同步**

不增加Quartz Registrar，不调用`JobApi.syncEnabledJobByHandlerName`，不修改现有F-CUT-005站内Job状态。

- [ ] **Step 3: 验证种子**

隔离MySQL全量迁移并重复执行本seed，断言一个PAUSED外部Job、既有站内Job不变、零业务审批/通知行。

- [ ] **Step 4: 运行迁移合同**

```powershell
mvn -pl pms-module-cutover -am -Dtest=Fcut008MigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 5: 申请Task 7 Gate**

生产激活必须等待INT-10/INT-05适配器与真实传播测试，不能在本Feature内自行解除。

---

### Task 8：真实MySQL受控正向闭环与Feature候选收口

**Files:**
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverLeadTimeExternalNotificationPositiveLoopMySqlTest.java`
- Modify: `tasks/features/F-CUT-008.md`
- Generated: `docs/traceability/requirement-matrix.md`
- Generated: `docs/traceability/requirement-version-coverage.json`

**Interfaces:**
- Consumes: Task 1～7最终合入状态。
- Produces: CUT侧正常闭环证据和Implementation候选；不伪造外部Provider完成。

- [ ] **Step 1: 在隔离MySQL 8.4完成A/B正常链**

以真实F-CUT-005 Service/MyBatis/平台幂等审计创建A/B实例：分别验证阈值边界快照、初始/下一节点四渠道记录、全部审批到P6、每个节点一个站内和三个外部请求。

- [ ] **Step 2: 用受控端口完成渠道结果**

同一闭环中使SMS=ACCEPTED、EMAIL先PENDING_RETRY后同键ACCEPTED、DINGTALK=DELIVERY_UNKNOWN；断言审批实例、节点、评审、任务状态和`CutoverApproved`事件在投递前后完全相同。

- [ ] **Step 3: 验证C/D与历史兼容**

C/D正常审批不产生提前时间快照但仍产生外部请求；迁移前A/B实例快照保持null；WAITING改派零通知并在后续激活使用最新recipient/nodeVersion。

- [ ] **Step 4: 联跑适用回归**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverLeadTimeCalculatorTest,CutoverLeadTimeSnapshotCodecTest,Fcut008MigrationContractTest,CutoverApprovalStartServiceTest,CutoverApprovalDecisionServiceTest,CutoverApprovalReassignmentTest,CutoverApprovalQueryServiceTest,CutoverApprovalNotificationServiceTest,CutoverExternalApprovalNotificationServiceTest,CutoverLeadTimeExternalNotificationPositiveLoopMySqlTest -Dsurefire.failIfNoSpecifiedTests=false test
pnpm vitest run --config src/views/pms/cutover/cutover-task/vitest.config.mjs src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts
pnpm ts:check
```

- [ ] **Step 5: 自审、追溯与最终Gate**

运行traceability生成/check、Phase 2/3和`git diff --check`；确认无INT/Yudao修改、生产Fake/Bean、Job激活或旧页面变更。Task状态只能记录`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，不得以本证据声明真实Provider、真实浏览器或Implementation Done。

---

## 计划自审结果

- Spec覆盖：BR-FCUT008-001→Task 1/3；BR-002→Task 3/6；BR-003→Task 4/5/7；BR-004→Task 2/5/8；全部六项AC均有对应Task。
- 类型一致：Java/JSON/TypeScript字段均使用八键`LeadTimeCompliance`；渠道、状态、deliveryKey在Schema、创建和投递任务中同名。
- 复用边界：只增强F-CUT-005当前新平台路径；旧表、旧页面、站内Provider和Yudao保持原义。
- 无第二份现行计划、无未定义Provider实现、无预约Flyway版本、无附件反向口径。

## Gate与执行边界

- 当前最近Gate：本唯一Technical Plan独立复审。
- Plan GO后按Task 1→2→3→4→5→6→7→8推进；共享Flyway、Task状态和前端API串行写入。
- 每个Task完成适用聚焦验证后提交并申请独立Gate；简单纯状态回写可自行完成。
- INT-10/INT-05生产Provider、外部Job激活、真实渠道联调和真实浏览器完整闭环不在本计划授权范围。
