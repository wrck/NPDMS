# F-CUT-005 P5分级审批 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 计划 ID：`NPDMS-FCUT005-TECHPLAN-20260901-01`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Feature Ready：`READY / GO@2e3fdba3`
> Feature Spec：`specs/features/F-CUT-005-p5-graded-approval.md`
> API Contract：`specs/features/F-CUT-005-api-contract.json`
> Physical Contract：`specs/features/F-CUT-005-physical-contract.json`
> Candidate Contract：`specs/features/F-CUT-005-candidate-owner-contract.json`
> Approval Owner Contract：`specs/features/F-CUT-005-approval-owner-contract.json`

**Goal：** 交付`CUT-05@V1=FULL`的CUT侧P5正向闭环：P4提交原子创建按A/B/C/D等级冻结的串行审批，逐节点完成五项评审与服务经理复核，驳回返回P4，全部通过进入P6，并保留改派、待办、通知与不可变审批历史。

**Architecture：** 在既有`CutoverTask/CutoverAssessment/CutoverChecklist/CutoverPlan`之后新增独立`CutoverApproval`聚合。五张CUT表分别拥有审批根、串行节点、五项评审、改派历史和站内通知状态；所有命令以平台幂等事务、聚合CAS和稳定锁序提交。PROJ/SYSTEM候选与ProjectScope只通过CUT消费端口调用；生产Provider未齐时，仅在`src/test`显式装配合同一致的受控事实，完整生产Bean、真实浏览器和Implementation Done保留到依赖接通Gate。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Yudao CommonResult/权限注解、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Spec：** `specs/features/F-CUT-005-p5-graded-approval.md`

## Global Constraints

- 只覆盖`CUT-05@V1=FULL`；不实现CUT-06执行闭环、V2提前时间判断、短信/邮件/钉钉或通用审批引擎。
- 不重复COM-01或其他跨模块业务；不修改Yudao基础模块。PROJ/SYSTEM只预留物理Owner接口与合入顺序，CUT测试使用合同一致的受控替身跑正常正向闭环。
- 受控替身只允许在`src/test`显式组装；不得进入`src/main`生产Bean、空集合fallback、真实浏览器或Implementation Done证据。
- 不修改旧`pms_cut_task`、`pms_cut_plan`、旧`/pms/cut-*`接口、旧Vue页面、旧审批字段或旧数据；不迁移、不双写、不升级旧审批事实。
- `ApprovalSourceSnapshot`只从同事务锁定的CUT-002/003/004事实形成；详情只读冻结JSON，不刷新当前Owner。合法`networkMode=null`原值保存，不补默认或推导值。
- SYSTEM先返回完整候选集，CUT再逐候选叠加项目`ACTION_VIEW`并判断交集唯一；全局多人但项目交集唯一必须成功。INITIATOR固定重验`ACTION_EDIT`。
- 通过与驳回使用不同请求类型：APPROVE必须五项全YES且服务经理CONFIRMED；REJECT必须存在NO或服务经理NOT_REASONABLE。
- 审批命令只原子追加`cut_approval_notification=PENDING`；通知Provider由提交后的独立投递任务调用，失败转`PENDING_RETRY`，不得回滚或改写审批决定。
- 所有新增查询遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外使用单一场景Query；动态集合、联表、锁查询进入Mapper XML；禁止SQL注解、`${}`、`Map`和Service拼SQL。
- Flyway仅在实际串行合入时重新读取`sql/migrations`并选下一个空闲版本；计划不预约版本，不修改V146～V152或其他已执行迁移。
- 每个Task先形成可运行的最小正向路径，再补与锁定合同直接相关的失败/并发测试；不制造与当前系统不可达的负向组合。
- 每个Task独立提交并申请Gate；Feature/Task状态、Flyway、公共错误码、菜单权限和共享前端API文件串行写入。

---

## 1. 正向链与统一锁序

### 1.1 正向链

1. F-CUT-004提交方案调用现有`CutoverApprovalFactApi.start`；CUT锁定任务、评估、A/B/C清单、方案、INITIATOR范围及完整候选事实，冻结`ApprovalSourceSnapshot`和route snapshot。
2. A/B/C/D分别创建4/3/2/2节点；INITIATOR为首个真实PENDING节点，后续节点WAITING。候选交集不唯一时根保持PENDING+hold且不产生可执行待办。
3. 当前审批人提交五项评审与反馈；SERVICE_MANAGER额外提交P2复核。中间通过只激活下一节点，驳回原子返回P4，末节点通过原子进入P6并写一个`CutoverApproved`。
4. 管理员通过最窄改派队列和`REASSIGNMENT_ONLY`投影处理WAITING/PENDING节点，不能查看冻结业务正文或代理审批。
5. 节点激活/改派只写PENDING通知；提交后的独立Job调用站内信Provider并把结果写为SENT或PENDING_RETRY。

### 1.2 写事务锁序

1. 受信tenant、Header、correlationId和功能权限在平台幂等认领前校验；
2. `PlatformCommandExecutionApi.execute` NEW分支进入外层事务；
3. 锁`cut_task`，再锁最终`cut_assessment`，A/B/C锁提交`cut_cutover_checklist`及当前item/result，D断言无清单；
4. 锁`cut_plan_revision`及方案子行，形成精确审批来源快照；
5. 锁`cut_approval_instance`，再按nodeNo锁节点；
6. PROJ服务经理事实、SYSTEM角色组/成员/用户状态事实和ProjectScope按候选userId升序锁定重验；
7. CAS节点、审批根与任务阶段；写评审、改派、PENDING通知、阶段历史、平台SuccessFacts及`CutoverApproved` Outbox；
8. 任一步失败使平台认领、审批、任务、审计、通知行与Outbox整体回滚。

---

## Task 1：CUT领域合同、候选消费端口与快照Codec

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/domain/CutoverApprovalRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/domain/CutoverApprovalSourceSnapshotCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/port/ProjectCutoverServiceManagerPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/port/CutoverApprovalRoleCandidatePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/port/CutoverApprovalProjectScopePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/port/CutoverApprovalOwnerFactException.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalControlledPorts.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/domain/CutoverApprovalSourceSnapshotCodecTest.java`

**Interfaces:**
- Consumes: `F-CUT-005-api-contract.json`、`F-CUT-005-candidate-owner-contract.json`及既有CUT-002/003/004 Mapper只读事实。
- Produces: `ProjectCutoverServiceManagerPort.inspectCurrent/lockAndRevalidate`、`CutoverApprovalRoleCandidatePort.inspectCandidates/lockAndRevalidate/lockExplicitCandidate`、`CutoverApprovalProjectScopePort.inspect/lockAndRevalidate`与规范快照JSON。

- [ ] **Step 1: 建立精确Java值对象与端口签名**

```java
interface CutoverApprovalRoleCandidatePort {
    CandidateSet inspectCandidates(long tenantId, String roleGroupCode);
    CandidateRevalidation lockAndRevalidate(CandidateSet expected);
    ExplicitCandidate lockExplicitCandidate(long tenantId, String roleGroupCode, long subjectUserId);
}
```

构造器固定正数、长度、稳定排序、完整候选集、用户ID唯一和公共错误归因；端口自身不实现生产Bean。

- [ ] **Step 2: 实现精确ApprovalSourceSnapshot Codec**

Codec只接受项目十一字段、可空networkMode、稳定排序的风险/业务调研结果、P2五维+等级以及F-CUT-004完整方案事实；序列化后再解析必须逐字段相等，未知/缺键拒绝。

- [ ] **Step 3: 增加受控正向端口**

`CutoverApprovalControlledPorts`确定性返回：一个PROJ服务经理、SYSTEM多个全局候选但项目交集唯一、稳定treeVersion；另提供显式可控的NOT_UNIQUE和STALE事实。类保持package-private或test-only，不使用Spring生产注解。

- [ ] **Step 4: 运行聚焦测试**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalSourceSnapshotCodecTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；合法`networkMode=null`、多候选唯一交集和快照round-trip均成立。

- [ ] **Step 5: 自审并提交Task 1 Gate**

搜索`@Service|@Component|@Bean`、SYSTEM/PROJ表名和旧接口，确认没有生产Provider、跨模块直表或fallback。

---

## Task 2：五表Schema、DO、Mapper与锁查询

**Files:**
- Create at serial merge: `sql/migrations/`下实际下一空闲版本的F-CUT-005 approval schema迁移
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/approval/`下五个DO
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/`下五个Mapper与`query/`场景对象
- Create: `pms-module-cutover/src/main/resources/mapper/approval/`下Mapper XML
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalMapperContractTest.java`

**Interfaces:**
- Consumes: `F-CUT-005-physical-contract.json`。
- Produces: 五表物理基础、按任务/方案/节点锁定、分页待办/改派候选、节点CAS、通知领取及版本更新。

- [ ] **Step 1: 串行确定迁移号并先写Schema**

迁移创建`cut_approval_instance/node/review_item/reassignment/notification`，逐项落physical列、生成marker、唯一键和CHECK；不写旧表，不更新既有业务行。

- [ ] **Step 2: 实现DO和单场景Query**

```java
public record ApprovalNodeLockQuery(Long tenantId, Long approvalInstanceId, Integer nodeNo) {}
public record ApprovalTodoPageQuery(Long tenantId, Long currentUserId, Integer offset, Integer pageSize) {}
public record ApprovalNotificationClaimQuery(LocalDateTime dueAt, Integer batchSize) {}
```

- [ ] **Step 3: 在XML实现动态/锁查询**

任务/实例/节点锁遵守1.2；待办与改派分页稳定排序；通知领取用`FOR UPDATE SKIP LOCKED`；所有SQL显式tenant/deleted，空集合返回空。

- [ ] **Step 4: 补合同测试并运行**

```powershell
mvn -pl pms-module-cutover -am -Dtest=Fcut005MigrationContractTest,CutoverApprovalMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

测试通过XMLMapperBuilder+BoundSql固定参数名，不只检查XML字符串。

- [ ] **Step 5: 隔离MySQL 8.4验证**

使用独立Compose项目、端口和空卷全量迁移；验证五表、CHECK、同实例唯一PENDING节点、同方案唯一审批及通知deliveryKey唯一。清理专用容器/网络/卷后提交Task 2 Schema/MySQL Gate。

---

## Task 3：审批启动、来源冻结与公开FactApi Provider候选

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalSourceAssembler.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiImpl.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalStartServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiImplTest.java`

**Interfaces:**
- Consumes: 既有`CutoverApprovalFactApi` DTO、Task 1端口、Task 2 Mapper、CUT-002/003/004锁定事实。
- Produces: 非Spring注册的生产路径类，实现`start/inspect/lockAndRevalidate/pauseForSourceInvalidation`。

- [ ] **Step 1: 先写A/B/C/D启动正向测试**

测试固定4/3/2/2路由、INITIATOR真实首节点、完整候选交集、精确来源快照、可执行首节点的PENDING通知行和同键重放；hold实例不写通知、不产生todo。

- [ ] **Step 2: 实现start事务**

```java
public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
    return commandExecutionApi.execute(scope(command), digest(command), () -> startNew(command));
}
```

`startNew`按1.2锁序组装来源、路由和节点；NOT_UNIQUE写hold且无可执行todo；Provider不可用抛稳定异常并使F-CUT-004提交整体回滚。

- [ ] **Step 3: 实现FactApi其余方法**

`inspect`只读聚合根；`lockAndRevalidate`比较完整公开fact；`pauseForSourceInvalidation`把PENDING实例和开放节点终结为暂停/取消，不自行修改F-CUT-004正文。

- [ ] **Step 4: 保持类可代理但不生产注册**

实现类不设`final`，事务方法保留Spring代理可用性；本Task不加`@Service/@Component/@Bean`，测试显式构造或test-only注册。

- [ ] **Step 5: 运行测试并申请Task 3 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalStartServiceTest,CutoverApprovalFactApiImplTest,CutoverApprovalFactApiContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

## Task 4：通过/驳回状态机与任务P4/P6原子迁移

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/command/ApproveCutoverApprovalCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/command/RejectCutoverApprovalCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/result/CutoverApprovalCommandResult.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java`
- Modify: `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalDecisionServiceTest.java`

**Interfaces:**
- Consumes: Task 3聚合、Task 1 Owner重验、`PlatformCommandExecutionApi`。
- Produces: `approve`和`reject`应用命令、P5→P4/P6阶段迁移及`CutoverApproved`业务事件。

- [ ] **Step 1: 固定动作判别联合**

```java
record ApproveCutoverApprovalCommand(List<ReviewItemInput> reviewItems,
                                     AssessmentReviewInput assessmentReview, String feedback) {}
record RejectCutoverApprovalCommand(List<ReviewItemInput> reviewItems,
                                    AssessmentReviewInput assessmentReview, String feedback) {}
```

Approve全YES/服务经理CONFIRMED；Reject存在NO或服务经理NOT_REASONABLE；错配稳定返回`DECISION_ACTION_RESULT_MISMATCH`。

- [ ] **Step 2: 实现中间通过**

锁定当前审批人与完整Owner/ProjectScope事实，追加五项决定，当前PENDING→APPROVED、下一WAITING→PENDING、根version+1并追加下一通知PENDING。

- [ ] **Step 3: 实现驳回与末节点通过**

Reject把未来WAITING→CANCELLED、根REJECTED、任务P5→P4并写历史；final approve把根APPROVED、任务P5→P6并通过SuccessFacts只写一个`CutoverApproved`。

- [ ] **Step 4: 补平台事务与CAS测试**

覆盖同键重放、并发单胜、任一CAS=0整体回滚、SuccessFacts保存correlationId且业务digest排除它。

- [ ] **Step 5: 运行并申请Task 4 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalDecisionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

## Task 5：详情、本人待办、改派队列与管理员改派

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/view/CutoverApprovalViews.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/command/ReassignCutoverApprovalCommand.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalApplicationService.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalQueryServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalReassignmentTest.java`

**Interfaces:**
- Consumes: Task 2分页/锁查询、Task 1候选端口。
- Produces: `FULL|FINAL_RESULT_ONLY|REASSIGNMENT_ONLY`三种投影、本人待办、管理员队列和改派命令。

- [ ] **Step 1: 实现三种投影**

FULL只从冻结sourceSnapshot和节点历史组装；FINAL_RESULT_ONLY不加载快照/评审正文；REASSIGNMENT_ONLY只加载任务及WAITING/PENDING节点元数据。

- [ ] **Step 2: 实现稳定分页**

本人待办按createdAt/instanceId，管理员候选按createdAt/instanceId/nodeNo；Provider不可用时本人待办整页失败，不返回部分结果。

- [ ] **Step 3: 实现allowedActions同构守卫**

APPROVE/REJECT与Task 4命令使用同一规则；管理员只得到REASSIGN，原发起人/只读成员不因可见性获得审批动作。

- [ ] **Step 4: 实现改派**

锁根/节点；INITIATOR目标ACTION_EDIT、SERVICE_MANAGER目标等于当前唯一事实、SECOND_LINE/RND目标通过显式角色和ACTION_VIEW；追加reassignment和PENDING通知，不覆盖originalApprover。

- [ ] **Step 5: 测试并申请Task 5 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalQueryServiceTest,CutoverApprovalReassignmentTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

## Task 6：站内通知提交后投递与暂停Job

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverApprovalNotificationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/job/CutoverApprovalNotificationJob.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/approval/CutoverApprovalNotificationMapper.java`
- Modify: `pms-module-cutover/src/main/resources/mapper/approval/CutoverApprovalNotificationMapper.xml`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/notification/CutoverApprovalNotificationServiceTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/job/CutoverApprovalNotificationJobTest.java`

**Interfaces:**
- Consumes: `NotifyMessageSendApi.sendSingleMessageToAdmin`、Task 2通知Mapper。
- Produces: 提交后PENDING→SENT/PENDING_RETRY投递路径；不改变审批事实。

- [ ] **Step 1: 固定投递键与模板输入**

```java
String deliveryKey = "CUT_APPROVAL:" + instanceId + ":" + nodeNo + ":" + nodeVersion;
```

模板参数只含任务/节点展示字段和平台内链接，不复制方案正文、SN或联系方式。

- [ ] **Step 2: 实现批量领取和投递**

Job仅领取PENDING或到期PENDING_RETRY；成功写messageId/sentAt，失败保存稳定error、retryCount和nextRetryAt；按更新前retryCount使用1/2/4/8/16/32/60分钟退避并封顶60分钟，同deliveryKey不新增第二行。该间隔是技术重试水位，不是业务审批期限或SLA。

- [ ] **Step 3: 验证审批命令不调用Provider**

Task 3～5测试断言业务命令只插通知行且Notify API零调用；Job测试单独断言Provider异常不会修改审批根/节点。

- [ ] **Step 4: 保持生产Job未激活**

本Task实现`JobHandler`类但不加Quartz同步Registrar、不插启用seed；生产激活留Task 12。

- [ ] **Step 5: 运行测试并申请Task 6 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalNotificationServiceTest,CutoverApprovalNotificationJobTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

## Task 7：六路由REST、严格请求Codec与错误合同

**Files:**
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalController.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalRequestCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalContractException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/approval/`下请求/响应VO
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalControllerContractTest.java`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverApprovalRequestCodecTest.java`

**Interfaces:**
- Consumes: Task 4/5服务。
- Produces: detail、myTodos、reassignmentCandidates、approve、reject、reassign六路由的测试激活候选。

- [ ] **Step 1: 实现VO与exact-key Codec**

ApproveRequest与RejectRequest分开；五项固定顺序；WireLong/WireDateTime、Header、缺/多键和判别联合在Controller前失败关闭。

- [ ] **Step 2: 实现六路由和三权限**

`query-approval/approve/reassign-approval`分别注解；Controller只解析受信上下文、调用Service和投影CommonResult，不拼状态或按异常message分类。

- [ ] **Step 3: 映射精确HTTP错误**

固定400/403/404/409/422/503/500的ErrorData；通知Provider失败不存在于审批命令映射。

- [ ] **Step 4: 使用test-only Controller外壳跑MockMvc**

验证六条正向路由、管理员REASSIGNMENT_ONLY、动作结果错配422、Owner stale 409和Provider unavailable 503。

- [ ] **Step 5: 确认不生产激活并申请Task 7 Gate**

Controller本Task不加生产`@RestController/@Component/@Bean`；测试通过test-only Configuration注册。

---

## Task 8：P5审批工作台与组件交互

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverWorkbenchSteps.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalDecisionForm.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverApprovalReassignmentPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverApprovalComponents.spec.ts`

**Interfaces:**
- Consumes: Task 7 API；既有P4工作台和PLT文件只读组件。
- Produces: P5完整/最终结果/管理员改派三种UI与五项评审交互。

- [ ] **Step 1: 先挂载组件固定正向交互**

覆盖A/B/C/D路由、当前审批人五项评审、服务经理复核、驳回返回P4、全部通过P6、管理员队列/改派。

- [ ] **Step 2: 实现Wire类型和冻结快照展示**

Snowflake ID保持string，时间为epoch毫秒双向格式化；networkMode null显示“未配置”而不补业务码；FULL只展示服务端sourceSnapshot。

- [ ] **Step 3: 只消费allowedActions和权限**

按钮不根据角色名、当前节点文字或前端状态猜测；FINAL_RESULT_ONLY/REASSIGNMENT_ONLY绝不渲染冻结方案和评审正文。

- [ ] **Step 4: 复用统一write barrier**

未知响应/处理中保留同一Idempotency-Key；业务成功后刷新失败只重试刷新，禁止重发通过/驳回/改派。

- [ ] **Step 5: 验证组件和布局**

运行定向Vitest、`pnpm ts:check`和`pnpm build:local`；320/768/1024/1440通过真实mount验证，不使用源码字符串匹配。

---

## Task 9：字典、菜单、权限、通知模板与暂停Job种子

**Files:**
- Create at serial merge: `sql/migrations/`下实际下一空闲版本的F-CUT-005 seed迁移
- Modify: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut005MigrationContractTest.java`

**Interfaces:**
- Consumes: API/physical封闭枚举和六路由。
- Produces: P5字典、三个权限、工作台入口、站内信模板及PAUSED通知Job。

- [ ] **Step 1: 列出唯一种子集合**

审批/节点/评审/hold/通知状态字典；`query-approval/approve/reassign-approval`三个权限；既有割接任务工作台下的P5入口；站内信模板；`cutoverApprovalNotificationJob`。

- [ ] **Step 2: 串行创建幂等seed**

Job固定`status=2/PAUSED`、`cron=0/30 * * * * ?`；不写`system_role_menu`，不修改旧菜单/权限，不注册Quartz自动同步。

- [ ] **Step 3: 验证重复执行**

隔离MySQL中原脚本重复执行后数量稳定、角色授权为0、Job仍PAUSED、旧菜单和旧权限未变。

- [ ] **Step 4: 运行迁移合同并申请Task 9 Gate**

```powershell
mvn -pl pms-module-cutover -am -Dtest=Fcut005MigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

---

## Task 10：真实MySQL受控正向闭环与并发

**Files:**
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalPositiveLoopMySqlTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverApprovalConcurrencyMySqlTest.java`
- Modify: `tasks/features/F-CUT-005.md`

**Interfaces:**
- Consumes: Task 1受控Owner、Task 2～6生产路径类、生产`PlatformCommandExecutionApiImpl`、真实MyBatis/MySQL。
- Produces: `IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`候选证据；不构成生产闭环。

- [ ] **Step 1: 显式test-only Spring装配**

注册受控PROJ/SYSTEM/ProjectScope、真实CUT服务、事务代理、生产平台幂等/审计/Outbox；不扫描或注册生产fallback。

- [ ] **Step 2: 跑A/B/C/D正向链**

从F-CUT-004 P4提交开始，逐节点通过，核对4/3/2/2节点、PENDING通知、任务P5→P6、一个CutoverApproved及完整SuccessFacts。

- [ ] **Step 3: 跑驳回与替代链**

合法NO或服务经理NOT_REASONABLE驳回，核对P5→P4；派生新P4 revision后创建线性替代审批，旧快照/意见不变。

- [ ] **Step 4: 跑候选与并发链**

SYSTEM多人但项目交集唯一成功；交集不唯一产生hold且无todo；并发approve/reject/reassign仅一个CAS成功，失败方平台认领、业务写、通知和Outbox整体回滚。

- [ ] **Step 5: 跑通知独立失败链**

审批命令成功后再执行通知Job；Provider失败只转PENDING_RETRY，任务/审批/事件保持提交结果。

- [ ] **Step 6: 汇总聚焦验证并申请Task 10 Gate**

运行CUT后端聚焦、迁移、前端组件、受影响reactor package和前端build；记录精确测试数与报告路径。

---

## Task 11：CUT-04生产交接候选与唯一装配前置核验

**Files:**
- Create only after dependencies pass: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/config/CutoverApprovalConfiguration.java`
- Modify only after dependencies pass: `tasks/features/F-CUT-004.md`
- Modify: `tasks/features/F-CUT-005.md`
- Test: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/approval/CutoverPlanApprovalSpringPropagationTest.java`

**Interfaces:**
- Consumes: PROJ服务经理Provider Gate、SYSTEM候选Provider Gate、ProjectScope生产事实、Task 3 FactApi Provider候选、F-CUT-004生产装配前提。
- Produces: 唯一生产`CutoverApprovalFactApi`与F-CUT-004 submit传播候选；依赖缺失时不创建Configuration文件。

- [ ] **Step 1: 逐项只读核验生产依赖**

确认PROJ/SYSTEM公开合同与Provider均已独立GO；任何一项缺失，Task保持`BLOCKED_BY_DEPENDENCY`并停止生产装配。

- [ ] **Step 2: 依赖齐备后加入唯一Bean**

```java
@Bean
CutoverApprovalFactApi cutoverApprovalFactApi(CutoverApprovalFactApiImpl implementation) {
    return implementation;
}
```

不得并存第二Bean、Fake或旧接口降级。

- [ ] **Step 3: 真实Spring传播测试**

验证P4 submit→approval start共享事务；Owner失败时方案仍DRAFT/任务仍P4/无审批；成功时方案SUBMITTED/任务P5/审批PENDING同成同败。

- [ ] **Step 4: 独立申请生产装配Gate**

Gate未通过前不激活Controller/Job、不运行真实浏览器、不更新Implementation Done。

---

## Task 12：生产Controller/Job激活、真实浏览器与Implementation Done

**Files:**
- Modify only after Task 11 GO: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/config/CutoverApprovalConfiguration.java`
- Modify only after Task 11 GO: 实际下一空闲Flyway的Job启用迁移
- Modify only after all acceptance passes: `tasks/features/F-CUT-005.md`
- Modify only as generated projection: `docs/traceability/requirement-matrix.md`
- Modify only as generated projection: `docs/traceability/requirement-version-coverage.json`

**Interfaces:**
- Consumes: Task 1～11全部GO、生产PROJ/SYSTEM/Notify Provider、F-CUT-004生产依赖。
- Produces: 唯一生产REST/Service/FactApi/Job装配、真实浏览器证据和最终Feature Gate候选。

- [ ] **Step 1: 激活唯一Controller/Service/FactApi**

只在所有生产Owner齐备后注册；启动时断言唯一Bean且没有测试替身/fallback。

- [ ] **Step 2: 激活通知Job**

使用新的前向迁移把唯一Job从PAUSED改为NORMAL，并在同一提交加入Quartz同步；真实Spring验证发送成功/失败传播边界。

- [ ] **Step 3: 运行真实MySQL与浏览器正向验收**

真实用户权限、真实PROJ/SYSTEM候选、真实P4方案和站内信Provider下跑A/B/C/D、驳回、改派、通知失败重试；不得用fixture/Fake作为浏览器数据源。

- [ ] **Step 4: 完整回归与证据归档**

运行受影响后端reactor、Flyway validate、前端type/build、浏览器网络/DOM/数据库断言；记录版本、命令、报告和截图路径。

- [ ] **Step 5: 独立申请Implementation Done Gate**

只有独立GO后才把Feature实施状态改为`IMPLEMENTATION_COMPLETE`并由生成器更新CUT-05@V1覆盖；测试数、HTTP 200或页面可见均不能单独替代该Gate。

---

## Plan Self-Review

- Spec coverage：Task 1覆盖精确来源/候选合同；Task 2覆盖五表；Task 3覆盖启动与公开Fact；Task 4覆盖评审/状态/事件；Task 5覆盖查询/改派/可见性；Task 6覆盖通知；Task 7/8覆盖REST/UI；Task 9覆盖种子；Task 10覆盖受控闭环；Task 11/12覆盖生产接通与验收。
- Scope：未实现COM、CUT-06、V2通知/提前判断、通用审批引擎、旧数据升级或Yudao基础修改。
- Type consistency：API操作数固定六个；三种viewMode、三类候选端口、五张表、三个权限及4/3/2/2路由在各Task命名一致。
- Placeholder scan：Flyway号明确由串行合入时读取仓库确定，不在计划中预约；所有阻断均有唯一后续Gate和具体输入。
- Production boundary：Task 1～10允许受控替身形成CUT正常闭环；Task 11前不注册完整生产Bean，Task 12前不激活Controller/Job或真实浏览器。
