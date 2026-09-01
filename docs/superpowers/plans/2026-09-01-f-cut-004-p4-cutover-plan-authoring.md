# F-CUT-004 P4割接方案编制与版本提交 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with the listed review checkpoints.
>
> 计划 ID：`NPDMS-FCUT004-TECHPLAN-20260901-01`
> Technical Plan Gate：`PASS / GO@9ef7545d`
> Feature Ready：`READY / GO@644816f2`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> API Contract：`specs/features/F-CUT-004-api-contract.json`
> Physical Contract：`specs/features/F-CUT-004-physical-contract.json`
> Approval Owner Contract：`specs/features/F-CUT-005-approval-owner-contract.json`

**Goal：** 交付`CUT-04@V1=FULL`的CUT侧正向闭环：工程师在P4创建、保存和下载标准/简易/完整文件方案，提交不可变revision并与审批启动同成同败；审批驳回、来源失效或职责变化后派生新revision，批准后仅联系人字段可审计变更。

**Architecture：** 在既有`CutoverTask/CutoverAssessment/CutoverChecklist`之下新增独立`CutoverPlan`聚合。`cut_plan_revision`保存根、冻结来源和文件事实，`cut_step`与`cut_cutover_support_arrangement`分别作为步骤和保障职责唯一真值。写命令复用F-CUT-002/003的Owner锁序、平台幂等与任务阶段CAS；PLT和F-CUT-005只通过公开端口消费。生产依赖未接通时，仅在`src/test`显式装配确定性受控替身完成CUT正向单元/集成链，不注册生产Fake/fallback。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Spec：** `specs/features/F-CUT-004-p4-cutover-plan-authoring.md`

## Global Constraints

- 只覆盖`CUT-04@V1=FULL`；不实现F-CUT-005审批节点、待办、通知、审批按钮，不进入CUT-06执行或V2/V3。
- 不重复COM-01或其他跨模块业务。PROJ、AST、PLT、F-CUT-005只保留/消费已锁定接口；缺生产Provider时允许`src/test`受控替身跑CUT正向闭环。
- 受控替身不得进入`src/main`、Spring生产装配、真实浏览器或Implementation Done证据；不得返回空集合放行或跨表降级。
- 不修改旧`pms_cut_plan`、`/pms/cut-plan`、旧Vue页面、旧权限、旧表或Yudao基础平台。
- 只新增`cut_plan_revision`、`cut_step`、`cut_cutover_support_arrangement`三张CUT业务表；下载与联系人变更审计复用平台操作审计，不建第四张CUT表。
- `content_snapshot`只保存标准根概述/风险措施或简易模式判别；步骤与保障安排只从子表读取，不双写JSON。
- 所有新增查询遵守`docs/coding/database-query-interface.md`：场景Query单参数，联表/动态集合/锁查询进入Mapper XML，禁止SQL注解、`${}`、`Map`和Service拼SQL。
- Flyway文件仅在实际串行合入时读取`sql/migrations`并选下一个空闲版本；计划不预约版本、不修改V146/V149或任何已执行迁移。
- 每个Task先完成最小正向实现，再补实现后的单元测试与正向闭环验证；不执行RED、预期失败或负向异常组合测试。
- 每个Task完成后只提交本Task文件并申请对应Gate；共享Task/Feature状态文件串行更新，不与其他Feature并行改写。

---

## 1. 实施边界与锁序

### 1.1 正向业务链

1. P4任务负责人读取当前方案；无方案时按等级选择`ONLINE_TEMPLATE_STANDARD`或`ONLINE_TEMPLATE_SIMPLE_D`由服务端创建空骨架，选择`FULL_FILE_UPLOAD`时连同完整文件期望事实与人工归属确认创建唯一合法DRAFT。
2. 保存命令一次CAS原子更新根、步骤和保障安排；上传模式冻结PLT单文件事实，在线模式按严格联合保存内容。
3. 下载命令基于当前DRAFT生成文件并把文件事实、操作人、revision和时间写入平台审计，不推进状态。
4. 提交命令锁定所有来源与方案子行，验证完整性，调用`CutoverApprovalFactApi.start`，再把revision置`SUBMITTED`并将任务P4→P5；任一步失败整体回滚。
5. F-CUT-005返回`REJECTED`或来源失效暂停后，CUT创建引用原revision的新DRAFT；旧方案与审批事实不覆盖。
6. `APPROVED`后只允许以方案根`If-Match`修改姓名、电话和到位时间；职责变化必须新建revision并重走P5。

### 1.2 统一锁序

所有写命令在受信tenant、Header和功能权限校验后进入`PlatformCommandExecutionApi`，NEW分支外层事务固定：

1. `CutoverProjectScopePort.lockAndRevalidate(..., ACTION_EDIT, expectedProjectScopeVersion)`；
2. 按命令需要重验既有项目/设备/配置/PLT事实；
3. 锁`cut_task`并核对任务负责人、origin、stage/status和taskVersion；
4. 锁当前最终`cut_assessment`，A/B/C再锁当前`cut_cutover_checklist`，D断言无清单；
5. 锁`cut_plan_revision`，再按`sectionCode/stepNo`和`roleCode`稳定顺序锁子行；
6. 提交/来源失效时调用MANDATORY的F-CUT-005端口并写任务阶段历史；
7. 同事务完成平台成功事实、审计与Outbox。

---

## 2. Task 1：CUT-05审批消费合同Java类型与CUT端口

**Produces：** 与`F-CUT-005-approval-owner-contract.json`逐字段一致的Java合同；只提供消费边界和测试替身，不提供生产审批实现。

**Files:**

- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApi.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactException.java`
- Create: `pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/approval/dto/`下合同中的Command、Query、Fact和Result records
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/api/approval/CutoverApprovalFactApiContractTest.java`

**Steps:**

- [ ] 最小实现接口、records和公共异常；构造器在公共输入边界稳定返回`INVALID_REQUEST`，不泄漏NPE，不实现Bean。
- [ ] 在`src/test`创建`ControlledCutoverApprovalFactApi`测试夹具：可确定性返回PENDING/REJECTED/APPROVED/PAUSED链，保持同键重放；禁止放入`src/main`。
- [ ] 实现完成后补合同测试，反射固定四个方法签名、record精确字段、四态枚举、稳定错误码和MANDATORY语义说明；运行：`mvn -pl pms-module-cutover -am -Dtest=CutoverApprovalFactApiContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 执行`mvn -pl pms-module-cutover-api,pms-module-cutover -am -DskipTests package`。
- [ ] 自审：搜索`@Service|@Component|@Bean`确认本Task没有审批生产Provider或Fake；提交并申请Task 1 Contract Gate。

---

## 3. Task 2：三表Schema、阶段前向约束与Mapper合同

**Produces：** 三表、P4/P5/P6阶段约束和计划Mapper的可执行物理基础；不写业务Service。

**Files:**

- Create at serial merge: `sql/migrations/V<next>__fcut004_p4_cutover_plan.sql`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/planv2/CutoverPlanRevisionDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/planv2/CutoverPlanStepDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/planv2/CutoverSupportArrangementDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/`下三个Mapper及`query/`场景对象
- Create: `pms-module-cutover/src/main/resources/mapper/planv2/`下三个Mapper XML
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/planv2/CutoverPlanMapperContractTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java`

**Steps:**

- [ ] 实施前重新读取最高Flyway版本并使用实际下一个空闲号。迁移先在任何DDL前预检`cut_task/cut_task_stage_history`现值，再创建三表并以前向方式替换阶段CHECK；不更新业务行。
- [ ] 实现DO、场景Query、Mapper XML：当前revision、revisionNo分配、根CAS、子行稳定锁/替换、直接后继查询和只读历史投影。
- [ ] 实现完成后补迁移/Mapper合同测试，固定physical contract全部列、联合CHECK、唯一键、P4/P5/P6与四个history trigger，以及XML动态集合和CAS参数绑定。
- [ ] 用独立MySQL 8.4空卷执行全量Flyway，正向验证三表信息架构、CHECK/唯一键以及P4→P5/P5→P4可用字段。
- [ ] 运行聚焦测试：`mvn -pl pms-module-cutover -am -Dtest=Fcut004MigrationContractTest,CutoverPlanMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 自审无跨Context表查询、无已执行迁移修改；提交并申请Task 2 Schema/MySQL Gate。

---

## 4. Task 3：严格内容Codec、来源冻结与文件事实端口

**Produces：** 三种可写方案联合、响应只读legacy联合、来源快照和PLT文件事实的唯一机器解释。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanFilePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanSourcePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/port/CutoverPlanOwnerFactException.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/domain/CutoverPlanContentCodecTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanControlledPorts.java`

**Steps:**

- [ ] 实现exact-key草稿Codec、提交完整性校验与规范序列化；草稿允许项目说明为空、计划/步骤/风险/保障为合法子集，已出现元素仍完整；生成初稿/提交才校验全量。数组保留业务顺序，步骤按section/stepNo、保障按role稳定排序，未知/缺键失败关闭。
- [ ] `CutoverPlanSourcePort`只组合既有CUT-002/003只读投影：任务、评估、清单、项目/设备、配置和模板章节；不查询COM、不访问其他模块表。
- [ ] `CutoverPlanFilePort`定义inspect/lock/downloadDraft最窄事实。生产Adapter只可直接调用`FileArtifactApi`已存在的公开方法；若“生成初稿”缺PLT公开创建合同，保持该Adapter分支`BLOCKED_BY_DEPENDENCY`，测试用受控端口返回确定文件事实，不修改PLT。
- [ ] 实现完成后补聚焦测试：A/B/C标准内容完整装配、D仅OPERATION/ROLLBACK、完整文件事实冻结、步骤/保障只从子表组装，并确认Owner原值保留且比较使用合同规范键。
- [ ] 自审无URL、文件正文、客户端扫描结论或重复子表JSON；提交并申请Task 3 Domain/Port Gate。

---

## 5. Task 4：创建、保存与详情正向闭环

**Produces：** 通过显式测试装配运行“P4读取→创建DRAFT→原子保存→刷新详情”的CUT自有闭环。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/CreateCutoverPlanDraftCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/SaveCutoverPlanDraftCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/result/CutoverPlanCommandResult.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/view/CutoverPlanViews.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryService.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationServiceTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanQueryServiceTest.java`

**实施前合同补全：** `createDraft`使用封闭请求联合：在线模式仅`editMode`，完整文件模式必须连同完整`fileArtifactFact`与`ownershipConfirmed=true`进入创建事务并以PLT锁定事实落库。`PlanSourceSnapshot`冻结完整`failedRiskFacts`；后续保存、初稿生成和提交从持久快照重建`SourceFacts`，不得从风险措施缺行或当前Owner查询猜测冻结集合。本补全先过独立机器合同Gate，再开始本Task运行实现。

**Steps:**

- [ ] 实现命令边界：受信tenant、非空规范`Idempotency-Key/correlationId`、task/plan版本；digest排除correlationId但SuccessFacts保留它。
- [ ] 实现create/save NEW事务：复用1.2锁序、冻结完整失败风险集合、revisionNo单调分配、唯一当前DRAFT、根版本CAS、子行整组替换；在线模式生成空骨架，上传模式在事务内锁定重验请求文件事实；任何子行失败整体回滚。
- [ ] 实现详情与`allowedActions`，只依据服务端权限、任务负责人、stage、plan状态和审批投影；LEGACY_FORWARD只读。
- [ ] 实现完成后用Task 3受控端口补正向测试：A级标准草稿、D级简易草稿、D级完整文件草稿，保存后根/步骤/保障原子一致，详情按子表组装。
- [ ] 运行：`mvn -pl pms-module-cutover -am -Dtest=CutoverPlanApplicationServiceTest,CutoverPlanQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 增加真实MySQL聚焦测试，证明根、步骤、保障及平台幂等/审计同事务，保存重放不重复。
- [ ] 提交并申请Task 4 Application/MySQL Gate；本Gate不注册生产Service Bean。

---

## 6. Task 5：初稿下载、提交P5与来源失效

**Produces：** 受控Owner装配下完成“下载不推进→提交同成同败→来源失效暂停并返回P4”的正向链。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/DownloadCutoverPlanDraftCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/SubmitCutoverPlanCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/InvalidateCutoverPlanSourceCommand.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java`
- Modify: `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanSubmissionTest.java`

**Steps:**

- [ ] 先以受控PLT和CUT-05实现写正向测试：下载生成文件事实但plan/task版本不推进；提交得到PENDING审批并P4→P5；来源失效变INVALIDATED、审批PAUSED且P5→P4。
- [ ] 实现完整性校验：标准方案六类步骤、全部当前未通过风险一一措施、四类保障；简易D两类步骤；上传模式有效文件事实与人工归属确认。
- [ ] 实现下载SuccessFacts，detail snapshot固定actor、planRevision、文件事实和时间，不把下载成功当提交。
- [ ] 实现submit外层事务：锁完整来源与子行，调用MANDATORY `start`，写approval identity，revision DRAFT→SUBMITTED，任务P4→P5并追加`P4_PLAN_SUBMITTED`；同键重放返回原审批。
- [ ] 实现内部来源失效命令：同事务调用`pauseForSourceInvalidation`、SUBMITTED→INVALIDATED、任务P5→P4并追加`P5_SOURCE_INVALIDATED`；不恢复旧事实。
- [ ] 真实MySQL正向验证方案提交、审批PENDING、任务P5、阶段历史和平台成功事实在同一事务结果中一致。
- [ ] 提交并申请Task 5 Submission/MySQL Gate；受控审批替身只存在测试源码。

---

## 7. Task 6：修订链与批准后联系人变更

**Produces：** 驳回/失效/职责变化派生新DRAFT；批准方案只变更联系人类字段并留平台前后审计。

**实施定点裁决：** `DUTY_CHANGED`因`Q-FCUT004-001`缺少P6→P4物理Owner、历史触发器及旧批准/闭环处置而保持`BLOCKED_BY_SPEC`；本Task当前只实施REJECTED、SOURCE_REPLACED与批准后联系人PATCH，不用其他已批准转换替代职责变化。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/ReviseCutoverPlanCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/command/PatchApprovedContactCommand.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApplicationService.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanRevisionLifecycleTest.java`

**Steps:**

- [ ] 实现revise：锁当前任务、来源revision和审批事实，复制不可变正文/步骤/职责到新DRAFT，重新冻结当前来源，不覆盖旧revision。
- [ ] 实现PATCH，不接受arrangementVersion；锁根和目标保障行，任一步失败使根、子行、审计整体回滚。
- [ ] 实现完成后补正向生命周期测试：REJECTED派生revisionNo+1；PAUSED_SOURCE_INVALIDATED派生SOURCE_REPLACED并把旧approval传给替代start；APPROVED职责变化派生DUTY_CHANGED。
- [ ] 实现完成后补联系人PATCH测试：根`If-Match`单一版本Owner，CAS根+1后只更新personName/phone/arrivalTime；role/duty保持不变；SuccessFacts保存before/after、actor、reason、correlationId和时间。
- [ ] 用受控审批事实跑“提交→APPROVED→联系人PATCH”和“提交→REJECTED→新DRAFT→替代提交”两条CUT正向链。
- [ ] 提交并申请Task 6 Lifecycle/MySQL Gate。

---

## 8. Task 7：七路由REST、精确错误合同与测试激活外壳

**Produces：** 七个用户REST的可测试Controller候选；生产依赖未齐时不激活正式Controller。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanController.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanContractException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/plan/`下七路由请求/响应VO
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanControllerContractTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanRequestCodecTest.java`

**Steps:**

- [ ] 实现七路由与四权限注解；Controller只解析Header/VO、调用Service和投影CommonResult，不拼业务状态。
- [ ] 实现RequestCodec与结构化异常投影，固定顶层/嵌套判别联合exact keys、WireLong/WireDateTime、Header及稳定错误合同；异常映射按结构字段，不按message猜测。
- [ ] 实现完成后用测试激活外壳真实MockMvc验证七条正向路由的请求、Header、CommonResult与响应DTO。
- [ ] 确认Controller在本Task不加生产`@RestController/@Component`，或仅通过test-only configuration注册；正式装配留Task 12。
- [ ] 运行：`mvn -pl pms-module-cutover -am -Dtest=CutoverPlanControllerContractTest,CutoverPlanRequestCodecTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 提交并申请Task 7 REST Contract Gate。

---

## 9. Task 8：受控legacy前向迁移与暂停Job

**Produces：** 只通过`PlatformMigrationEvidenceApi`消费STAGED_READY批次的CUT迁移路径；正常生产不读`pms_cut_plan`。

**Files:**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanRowConverter.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanReconciliationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/job/LegacyCutoverPlanReconciliationJob.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanRowConverterTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/migration/LegacyCutoverPlanReconciliationMySqlTest.java`
- Create at serial merge: `sql/migrations/V<next>__fcut004_legacy_plan_job.sql`

**Steps:**

- [ ] 实现服务：外层事务claim STAGED_READY批次，分页读PLT冻结source records，通过既有`pms_cut_task`外部映射解析目标任务，写LEGACY_FORWARD root/steps并追加mapping/issue/retained，计数一致后complete。
- [ ] 正常CUT代码不得查询`pms_cut_plan`；受控Release导入器属于发布工具边界，不在本Feature生产Bean内实现legacy datasource。
- [ ] 注册Job代码但以实际下一个空闲Flyway版本在`infra_job`幂等种子为`status=2/PAUSED`；不加Quartz自动同步Registrar。
- [ ] 实现完成后补converter测试，固定合格legacy source snapshot及四字段→四step映射；旧审批字段只能进入迁移证据。
- [ ] 真实MySQL正向验证合格行从STAGED_READY完成LEGACY_FORWARD root/steps、PLT mapping和批次COMPLETED；测试fixture只证明转换，不作为生产迁移完成证据。
- [ ] 提交并申请Task 8 Migration/MySQL Gate。

---

## 10. Task 9：P4工作台与组件交互

**Produces：** 现有割接任务工作台中的P4方案面板，完整覆盖七路由与服务端allowedActions。

**Files:**

- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverWorkbenchSteps.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverPlanPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverPlanEditor.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverSupportArrangements.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverPlanComponents.spec.ts`

**Steps:**

- [ ] 先挂载组件写交互测试：A/B/C标准模式、D简易/上传模式、步骤与四类保障、下载、提交、驳回修订、批准联系人PATCH。
- [ ] API类型严格实现WireLong和epoch毫秒，不把Snowflake ID转JavaScript number；请求只发送合同exact keys与Header。
- [ ] 面板只消费`allowedActions`与四项权限，不根据角色/状态猜按钮；P4编辑，P5/P6只读审批投影。
- [ ] 文件操作复用现有PLT上传/引用组件，只传artifact/reference/version事实，不传URL或正文。
- [ ] 所有业务写复用统一write barrier：未知响应/处理中保留同Idempotency-Key；业务成功后刷新失败只重试刷新，不重发命令。
- [ ] 运行组件测试、`pnpm ts:check`和`pnpm build:local`；320/768/1024/1440布局使用真实mount断言，不用源码字符串匹配。
- [ ] 提交并申请Task 9 Frontend Gate；不启动真实浏览器。

---

## 11. Task 10：字典、菜单与权限种子

**Produces：** P4方案所需封闭字典和四个权限的幂等初始化，不给通用角色自动授权。

**Files:**

- Create at serial merge: `sql/migrations/V<next>__fcut004_plan_seed.sql`
- Modify: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut004MigrationContractTest.java`

**Steps:**

- [ ] 从API/physical contract逐项列出plan状态、edit mode、section、support role、revision reason及四权限；不增加审批状态字典替代F-CUT-005事实。
- [ ] 在实际串行合入时取得下一个空闲版本，幂等插入字典与`query-plan/save-plan/download-plan/submit-plan`按钮；不写`system_role_menu`。
- [ ] 用MySQL验证原脚本重复执行数量稳定、旧`pms:cut-plan:*`和旧菜单不变。
- [ ] 提交并申请Task 10 Seed Gate。

---

## 12. Task 11：CUT受控正向集成回归

**Produces：** 在生产依赖接通前，以test-only Owner实现证明CUT自有正向闭环和事务边界。

**Files:**

- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanPositiveLoopMySqlTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanApprovalLifecycleMySqlTest.java`
- Modify: `tasks/features/F-CUT-004.md`

**Steps:**

- [ ] 用test-only PROJ/PLT/F-CUT-005确定性实现组装真实Spring事务、生产`PlatformCommandExecutionApiImpl`、真实MyBatis和MySQL。
- [ ] 跑标准链：P4创建→保存根/步骤/保障→下载→提交P5，核对审批PENDING、平台幂等/审计、任务历史。
- [ ] 跑简易D与完整文件链，确认D无清单、简易仅两类步骤、上传只冻结文件事实。
- [ ] 跑修订链：REJECTED→新DRAFT→替代PENDING；来源失效→INVALIDATED/PAUSED/P4→替代提交；APPROVED联系人PATCH不重审。
- [ ] 只核对每条正向链的方案、任务、审批、平台成功事实和审计结果完整一致。
- [ ] 运行CUT聚焦后端、前端组件、Flyway validate和受影响reactor package；记录精确测试数与报告路径。
- [ ] Task 11通过后仅把CUT候选记为`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，不得宣称生产装配、浏览器或Implementation Done。

---

## 13. Task 12：生产依赖接通、唯一装配与真实验收

**Produces：** 正式Owner均可用后的唯一生产Service/Controller装配与浏览器正向证据；依赖缺失时保持`BLOCKED_BY_DEPENDENCY`。

**Files:**

- Create only after owner gates: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/adapter/CutoverPlanFileApiAdapter.java`
- Create only after owner gates: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/config/CutoverPlanConfiguration.java`
- Modify only after owner gates: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverPlanController.java`
- Create after production assembly: `scripts/tests/run_fcut004_browser_acceptance.cjs`
- Modify: `tasks/features/F-CUT-004.md`

**Steps:**

- [ ] Gate前置：F-CUT-005生产`CutoverApprovalFactApi`已通过Owner Gate；PLT能正式生成/冻结初稿文件并通过合同测试；F-CUT-002/003所需生产Owner已接通。
- [ ] 同一提交接入正式Adapter、唯一Application/Query Service Bean和唯一`@RestController`；缺任一Provider时Spring失败关闭，不注册fallback。
- [ ] 用真实Spring上下文正向验证F-CUT-004→F-CUT-005 MANDATORY事务传播及同事务完成结果。
- [ ] 激活legacy Job须另经正式迁移批次/Release证据Gate；未激活不阻断新平台P4正向链，但阻断“历史迁移完成”声明。
- [ ] 启动真实MySQL/Redis、宿主机后端与前端，用正式权限和生产Provider完成标准P4→P5正向浏览器链；审批后续状态只由F-CUT-005正式实现提供。
- [ ] 核对数据库根/子表、任务历史、平台幂等/审计和文件事实；浏览器HTTP 200本身不作为完成证据。
- [ ] 更新Task和追溯，申请F-CUT-004 Implementation Done独立裁决。生产审批或PLT生成事实未形成时，本Task保持`BLOCKED_BY_DEPENDENCY`。

---

## 14. 验证矩阵与完成口径

| 层级 | 必须证明 | 可用替身 | 完成含义 |
|---|---|---|---|
| 机器合同 | Java/JSON/DDL/REST字段与封闭联合一致 | 不适用 | 对应Contract Gate可审 |
| CUT单元/组件 | 内容Codec、命令、状态、页面交互 | `src/test`受控Owner | CUT实现候选，不是生产闭环 |
| CUT真实MySQL集成 | 三表、任务阶段、幂等、审计及同事务正向结果 | `src/test`受控Owner | `IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES` |
| 生产Spring/浏览器 | 正式PROJ/PLT/F-CUT-005事实和真实权限 | 禁止 | 才可申请Implementation Done |

Feature完成必须同时满足：

1. 七路由和四权限全部可达，旧`/pms/cut-plan`零修改；
2. 三种编辑模式、来源冻结、下载、提交、修订和联系人PATCH符合机器合同；
3. 方案提交、审批启动、任务P4→P5和平台成功事实同事务；
4. legacy路径只经PLT受控批次且Job激活状态有正式证据；
5. 正式Provider和真实浏览器正向链完成，不以Fake、手工SQL或内部调用替代。

## 15. 风险与串行资产

- **F-CUT-005尚未实现：** 不阻断Task 1～11的CUT受控正向实现，持续阻断Task 12生产装配、审批联调、真实浏览器和Implementation Done。
- **PLT初稿生成合同可能不足：** 先保留`CutoverPlanFilePort`并用test-only实现验证CUT；不得修改PLT或用上传回显冒充生成事实，生产接线等待PLT Owner Gate。
- **Flyway竞争：** Task 2/8/10各自在落文件前重新取实际下一个空闲号并串行提交，绝不预约或改写已执行版本。
- **共享CUT文件：** `CutoverTaskMapper.xml`、前端cutover-task工作台、Feature Task只由当前Task排他修改；其他CUT Feature若并行，先完成只读差异核对再串行合入。
- **回退：** 未执行迁移可删除候选；已执行后仅新增前向纠正。业务回退不删除revision、步骤、保障、迁移证据、平台审计或审批事实。

## 16. Technical Plan Gate

当前结论：`PASS / GO@9ef7545d`。允许按Task 1→12及各自Gate执行；每个Task先完成最小正向实现，再补实现后的单元测试与正向闭环验证。跨模块受控替身边界不等于生产依赖完成。
