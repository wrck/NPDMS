# F-CUT-006 P6割接跟踪与闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with the listed review checkpoints. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 计划 ID：`NPDMS-FCUT006-TECHPLAN-20260902-01`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Feature Ready：`READY / GO@4e390d4f`
> Feature Spec：`specs/features/F-CUT-006-p6-cutover-closure.md`
> API Contract：`specs/features/F-CUT-006-api-contract.json`
> Physical Contract：`specs/features/F-CUT-006-physical-contract.json`

**Goal：** 交付`CUT-06@V1=FULL`的CUT侧正常闭环：已有P6任务创建并保存一次性闭环草稿，以单设备采集、必需文件和人工结果形成证据，提交SUCCESS或FAILED后原子归档并释放活动设备；只有SUCCESS发布`CutoverCompleted`。

**Architecture：** 在现有`CutoverTask`、已批准`CutoverPlan`和`CutoverApproval`之上新增独立`CutoverClosure`聚合及附件、采集证据两个追加投影。所有用户写命令先经`PlatformCommandExecutionApi`认领，NEW分支按CUT锁序完成业务写；ProjectScope、PLT和INT-12只通过CUT消费端口接入，当前实施用`src/test`确定性替身跑正常闭环，不注册生产Fake或跨模块降级实现。

**Tech Stack：** JDK 25、Spring Boot、MyBatis/XML、MySQL 8.4、Flyway、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Spec：** `specs/features/F-CUT-006-p6-cutover-closure.md`

## Global Constraints

- 只覆盖`CUT-06@V1=FULL`；不实现逐步骤执行引擎、稳定观察、遗留项生命周期、ITR回传、P6职责变化回P4或V2/V3。
- 正常链固定为`P6/CLOSURE_IN_PROGRESS + DRAFT → SUBMITTED + P6/ARCHIVED`；SUCCESS和FAILED均归档并释放`active_marker`，只有SUCCESS产生`CutoverCompleted`。
- 跨模块仅保留`ProjectScopeApi`、`FileArtifactApi`和INT-12消费端口；当前正向闭环使用`src/test`受控替身，不修改PROJ、PLT、INT-12、Yudao或其他Owner。
- 受控替身不得进入`src/main`、生产Spring装配、真实浏览器证据或Implementation Done；不得返回空成功、fallback或跨模块直表结果。
- 不修改旧`pms_cut_execution/pms_cut_observation`、旧`/pms/cut-*`接口、旧Vue页面、旧权限或旧运行数据。
- 所有数据库查询遵守`docs/coding/database-query-interface.md`：主键外只收场景Query；动态集合、联表、锁查询和CAS进入Mapper XML；禁止SQL注解、`${}`、`Map`和Service拼SQL。
- Flyway只在串行合入时读取`sql/migrations`并选择实际下一个空闲版本；不预约版本，不修改已执行迁移。
- 每个Task先写能证明正常链的失败测试，再做最小实现、聚焦回归、自审和独立Gate；共享Feature/Task状态文件串行回写。

---

## 1. 正常链、事务与锁序

### 1.1 最小正常链

1. 已有`NEW_PLATFORM`任务处于`P6/CLOSURE_IN_PROGRESS`，任务负责人读取当前已批准审批实例和不可变方案revision。
2. 首次保存创建唯一DRAFT并冻结任务、审批、方案、项目及设备范围水位；后续保存只做闭环版本CAS。
3. DRAFT内按一次一个设备请求采集，受控INT-12端口返回`DISPATCH_ACCEPTED`，受控回调追加唯一`CALLBACK_SUCCEEDED`；下发失败时保留`DISPATCH_FAILED`，可经PLT受控事实追加一个人工结果。
4. 保存并锁定重验一个`POST_COLLECTION_CHECKLIST`和一个`IMPLEMENTATION_COMMITMENT`文件revision；可追加`OTHER_EVIDENCE`。
5. 提交重验任务、审批/方案、项目范围、文件事实和采集终态；SUCCESS或FAILED均把闭环置SUBMITTED、任务置ARCHIVED、追加P6历史并释放全部活动设备。
6. SUCCESS持久化`CUTOVER_CLOSURE:{closureId}:{submittedClosureVersion}`并写一个Outbox；FAILED不写该事件。

### 1.2 写命令统一顺序

1. 校验受信tenant、请求精确字段、Header、权限和correlationId；
2. `PlatformCommandExecutionApi.execute`认领`tenant+scope+Idempotency-Key`；`REPLAY_COMPLETED`直接返回，不读CUT业务行；
3. NEW内锁`cut_task`，再锁当前批准实例/方案revision、唯一闭环、附件/采集证据、ProjectScope/PLT事实和按deviceId升序的任务设备范围；
4. 写闭环、子表、任务、阶段历史、设备释放及必要Outbox；
5. 同一外层事务完成平台幂等COMPLETED和操作审计；任一步异常整体回滚。

---

## 2. Task 1：闭环领域类型、消费端口与受控替身合同

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/domain/CutoverClosureRules.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/port/CutoverClosureFilePort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/port/CutoverClosureCollectionPort.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/port/CutoverClosureOwnerFactException.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureControlledPorts.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosurePortContractTest.java`

**Interfaces：**

```java
interface CutoverClosureFilePort {
    FileFact inspect(FileExpectation expected);
    FileFact lockAndRevalidate(FileExpectation expected);
}
interface CutoverClosureCollectionPort {
    DispatchFact request(CollectionRequest request);
}
```

- [ ] 写合同失败测试，固定单`deviceId`、五种stage、两种认证联合、临时Secret不得出现在返回/摘要/日志对象，以及PLT事实的artifact/reference/version三轴和scopeVersion。
- [ ] 实现最小records、封闭枚举和规范比较；公共输入非法稳定归为CUT合同错误，Owner不可用与Owner数据损坏分离。
- [ ] 在`src/test`实现受控ProjectScope、PLT和INT-12端口：正常成功、下发失败、回调成功和人工文件均由测试显式选择；不加Spring注解。
- [ ] 运行：`mvn -pl pms-module-cutover -am -Dtest=CutoverClosurePortContractTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 搜索`src/main`确认无Fake/fallback、无INT-12凭证持久化字段；提交并申请Task 1 Contract Gate。

## 3. Task 2：三表Schema、任务归档前向约束与Mapper合同

**Files：**

- Create at serial merge: `sql/migrations/V<actual-next>__fcut006_p6_cutover_closure.sql`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/closure/CutoverClosureDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/closure/CutoverClosureAttachmentDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/closure/CutoverCollectionEvidenceDO.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/`下三个Mapper与`query/`场景对象
- Create: `pms-module-cutover/src/main/resources/mapper/closure/`下三个Mapper XML
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/CutoverTaskMapper.java`
- Modify: `pms-module-cutover/src/main/resources/mapper/taskv2/CutoverTaskMapper.xml`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/migration/Fcut006MigrationContractTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/closure/CutoverClosureMapperContractTest.java`

- [ ] 实施前读取实际最高Flyway版本；迁移先预检现有`cut_task`和stage history值，再创建三表并前向扩展`P6/ARCHIVED`、`P6_CLOSURE_SUBMITTED`，不更新业务行。
- [ ] 实现physical contract全部列、生成marker、唯一键、CHECK及无默认的不可变事实字段；SUCCESS resultRef和FAILED null规则进入数据库约束。
- [ ] 实现场景Query和XML：唯一闭环锁、版本CAS、附件稳定锁、采集dispatch/callback/manual唯一追加、未终态dispatch计数、设备升序锁与active_marker释放。
- [ ] 用`XMLMapperBuilder + BoundSql + MetaObject`固定动态集合与CAS属性名，避免文本测试漏掉MyBatis运行绑定错误。
- [ ] 用独立MySQL 8.4空卷执行全量Flyway，验证三表信息架构、唯一终态callback、单人工结果、SUCCESS/FAILED约束和P6归档转换。
- [ ] 运行聚焦测试并提交，申请Task 2 Schema/MySQL Gate。

## 4. Task 3：创建、保存、详情与文件事实正常链

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/SaveCutoverClosureCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/result/CutoverClosureCommandResult.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/view/CutoverClosureView.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryService.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationServiceTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureQueryServiceTest.java`

- [ ] 先写“P6负责人首次保存→DRAFT→再次保存CAS→详情刷新”的失败测试；夹具使用真实CUT任务/批准方案投影和Task 1受控ProjectScope/PLT端口。
- [ ] 实现`CutoverClosureRules`的三项正常性、回退判别联合、最终结果、遗留文本和附件purpose规则；普通保存明确拒绝`MANUAL_COLLECTION_RESULT`。
- [ ] 实现首次保存冻结任务、批准实例、方案revision、项目范围和设备水位；后续保存只接受相同冻结来源，不以当前Owner事实静默刷新。
- [ ] 使用平台命令包装NEW事务；create/save摘要排除correlationId和Secret，SuccessFacts保存非空规范correlationId及不含敏感正文的结果快照。
- [ ] 实现详情和allowedActions；只由任务负责人、功能权限、ACTION_VIEW/EDIT、P6状态、闭环状态和完整性投影，不读旧表。
- [ ] 运行两个聚焦测试类和真实MySQL“根+附件+平台幂等/审计同事务”用例；提交并申请Task 3 Application/MySQL Gate。

## 5. Task 4：单设备采集、回调与人工结果

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/RequestClosureCollectionCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/HandleClosureCollectionCallbackCommand.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/LinkClosureManualResultCommand.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureCollectionServiceTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureCollectionMySqlTest.java`

- [ ] 先写受控正向测试：DRAFT单设备请求得到DISPATCH_ACCEPTED、回调追加CALLBACK_SUCCEEDED、closureVersion逐次+1；纯下发失败保留DISPATCH_FAILED并可追加人工文件。
- [ ] 实现请求：闭环和任务版本CAS、设备必须属于冻结范围、一次调用一个deviceId、Secret只传同步端口；下发事实与闭环版本同事务保存。
- [ ] 实现回调：平台先认领callbackEventId；同载荷重放不访问CUT行，新事件只允许匹配DRAFT内的DISPATCH_ACCEPTED并追加唯一终态。
- [ ] 实现人工结果：锁原DISPATCH_FAILED和PLT事实，原子插入`MANUAL_COLLECTION_RESULT`附件与MANUAL_UPLOAD引用，不覆盖失败行。
- [ ] 补SUBMITTED晚到回调测试：同event重放返回既有结果，新event返回永久CLOSURE_ARCHIVED且CUT三表零写。
- [ ] 真实MySQL验证dispatch/callback/manual唯一键、并发回调仅一个终态、事务失败平台认领与业务写整体回滚；提交并申请Task 4 Collection/MySQL Gate。

## 6. Task 5：SUCCESS/FAILED提交、归档、设备释放与事件

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/command/SubmitCutoverClosureCommand.java`
- Modify: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureApplicationService.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureSubmissionTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosureSubmissionMySqlTest.java`

- [ ] 先写两条正常链：完整SUCCESS提交产生归档、释放设备和一个事件；完整FAILED提交同样归档、释放设备但事件为零。
- [ ] 实现提交守卫：DRAFT版本、P6任务版本、批准实例/方案不可变、ProjectScope当前、两类必需附件PLT重验、所有DISPATCH_ACCEPTED已有唯一终态。
- [ ] 在一个NEW事务中执行闭环CAS、任务CAS、阶段历史、全部active_marker清空和平台成功事实；并发提交只有一个成功。
- [ ] SUCCESS以提交后闭环版本生成并持久化`CUTOVER_CLOSURE:{closureId}:{submittedClosureVersion}`，Outbox原样携带；FAILED保持null。
- [ ] 真实MySQL验证闭环/任务/历史/设备/幂等/审计/Outbox的原子结果，以及任一文件或来源失败时全部零写。
- [ ] 提交并申请Task 5 Submission/MySQL Gate；该Gate通过只代表CUT受控依赖闭环，不代表生产Done。

## 7. Task 6：五路由REST、错误合同与测试激活外壳

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureController.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureRequestCodec.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureContractException.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/vo/closure/`下请求与响应VO
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureControllerContractTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureRequestCodecTest.java`

- [ ] 实现详情、保存、请求采集、关联人工结果、提交五路由及四权限；Controller只解析Header/VO、调用Service和投影CommonResult。
- [ ] 实现exact-key和严格判别联合，固定WireLong/WireDateTime、If-Match、X-Task-Version、Idempotency-Key及Secret不回显。
- [ ] 以结构化异常字段映射400/403/404/409/422/503，不按异常message猜测；INT-12不可用只影响采集请求，提交不虚构INT-12 Provider调用。
- [ ] 用test-only Configuration注册Controller与显式组装Service，MockMvc覆盖五路由正常响应、同键重放和提交后只读详情。
- [ ] 确认本Task不注册生产`@RestController/@Service/@Bean`；提交并申请Task 6 REST Contract Gate。

## 8. Task 7：受控legacy前向分类与暂停Job

**Files：**

- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/migration/LegacyCutoverClosureRowClassifier.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/migration/LegacyCutoverClosureReconciliationService.java`
- Create: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/job/LegacyCutoverClosureReconciliationJob.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/migration/LegacyCutoverClosureRowClassifierTest.java`
- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/migration/LegacyCutoverClosureReconciliationMySqlTest.java`
- Create at serial merge: `sql/migrations/V<actual-next>__fcut006_legacy_closure_job.sql`

- [ ] 实现外层事务：以固定批次身份claim STAGED_READY，分页读取PLT冻结source records，合法旧步骤追加RETAINED，损坏来源追加`FCUT006_SOURCE_RECORD_INVALID`，计数一致后complete。
- [ ] CUT生产代码不得查询`pms_cut_execution`或文件/第二数据源；Release受控导入器不在本Feature生产Bean中实现。
- [ ] 注册Job类，但Flyway种子固定`status=2/PAUSED`，不加Quartz自动同步Registrar；无暂存批次时不写业务事实。
- [ ] 真实MySQL验证`sourceCount=retainedCount+issueCount`、mappedCount=0、临时PLT失败整批回滚至可重领状态，测试fixture不作为生产迁移完成证据。
- [ ] 提交并申请Task 7 Migration/MySQL Gate。

## 9. Task 8：P6工作台与组件交互

**Files：**

- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cutover-task/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverWorkbenchSteps.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosurePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosureForm.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/components/CutoverClosureEvidencePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cutover-task/cutoverClosureComponents.spec.ts`

- [ ] 增加Wire DTO和五API调用，Snowflake ID保持string，时间统一epoch毫秒；Secret只存在请求表单瞬时状态，发送后清空。
- [ ] 在新CUT工作台P6步骤挂闭环面板；按钮只由服务端allowedActions和四权限共同投影，不修改旧`cut-task`页面。
- [ ] 实现三项结果、回退联合、遗留文本、文件选择、单设备采集、失败后人工文件和最终SUCCESS/FAILED确认；不渲染逐步骤执行或稳定观察。
- [ ] 挂载组件测试覆盖DRAFT保存、单设备采集、两类必需文件、SUCCESS/FAILED提交、归档只读及320/768/1024/1440宽度。
- [ ] 运行定向Vitest与`pnpm ts:check`；提交并申请Task 8 Frontend Gate。该Gate不用真实浏览器。

## 10. Task 9：CUT聚焦回归与真实MySQL正常闭环

**Files：**

- Create: `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/closure/CutoverClosurePositiveLoopMySqlTest.java`
- Modify only if needed for fixture compatibility: existing F-CUT-002/003/004/005 CUT-focused tests
- Update: `tasks/features/F-CUT-006.md`

- [ ] 通过真实Spring事务代理、真实MyBatis、真实`PlatformCommandExecutionApiImpl`和独立MySQL 8.4跑“P6 DRAFT→单设备采集成功→两文件→SUCCESS→ARCHIVED/释放/Outbox”。
- [ ] 同一环境跑FAILED归档、下发失败+人工结果、同键重放、并发提交和Owner失败整体回滚；跨模块事实由test-only受控端口提供。
- [ ] 复跑CUT-002至CUT-005聚焦回归，确认旧服务/页面、P2/P3/P4/P5链未被P6表和状态扩展破坏。
- [ ] 记录测试命令、版本、通过数和隔离数据库信息；不得把受控替身结果写成真实生产Provider或浏览器证据。
- [ ] 提交并申请Task 9 Backend/MySQL Gate；通过后状态只能为`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`。

## 11. Task 10：生产依赖接通、唯一装配与真实浏览器Gate

**Entry Gate：** ProjectScope、PLT和INT-12正式Provider/Adapter均已由各物理Owner通过独立合同与生产Gate；未满足时本Task保持`BLOCKED_BY_DEPENDENCY`，不得创建fallback。

**Files：**

- Create after Entry Gate: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/adapter/CutoverClosureFileApiAdapter.java`
- Create after Entry Gate: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/closure/adapter/CutoverClosureCollectionApiAdapter.java`
- Create after Entry Gate: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/config/CutoverClosureConfiguration.java`
- Modify after Entry Gate: `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/taskv2/CutoverClosureController.java` for production activation only
- Create after Entry Gate: production-context and bidirectional contract tests

- [ ] 核对三类Owner公开合同与本计划端口逐字段一致；任何差异先回规格Gate，禁止Adapter猜测或降级。
- [ ] 加入正式Adapter、唯一ApplicationService/QueryService/Controller Bean，确保test substitutes不在生产classpath装配。
- [ ] 用真实Spring上下文验证Owner失败分类、平台事务传播、INT-12回调幂等和PLT锁定重验。
- [ ] 受控启用相关Job前先证明生产消费者存在；迁移Job和外部回调Job不得因Bean装配自动启用。
- [ ] 启动宿主机后端/前端与Compose基础设施，用真实浏览器完成SUCCESS和FAILED两条P6链；证据必须使用生产Provider事实。
- [ ] 只有全部依赖、真实MySQL、真实浏览器、回归和审计证据通过后，才申请F-CUT-006 Implementation Done独立裁决。

---

## 12. Self-Review结果

- Spec覆盖：BR-FCUT006-001由Task 2/3/5覆盖；BR-FCUT006-002由Task 1/3/5/8覆盖；BR-FCUT006-003由Task 1/4/8覆盖；BR-FCUT006-004由Task 3/5/6覆盖；迁移由Task 7覆盖；生产依赖边界由Task 10覆盖。
- 正向收益优先：首个可运行链在Task 3建立DRAFT，Task 4形成采集证据，Task 5完成SUCCESS/FAILED归档；跨模块只用测试替身，不倒置实现Owner模块。
- 类型一致：单`deviceId`、`closureVersion`、`resultRef`、五种stage、五种evidenceType及三类附件purpose均与两份机器合同一致。
- 无第二Technical Plan、无预约Flyway、无旧表直读、无生产Fake、无P6职责变化回P4实现。

## 13. Gate与执行方式

本文件是F-CUT-006唯一当前Technical Plan候选。独立Technical Plan Gate GO前不得执行Task 1。GO后按Task 1→10串行实施；Task 10在生产Owner依赖形成前保持阻断，但不阻断Task 1→9使用受控替身形成CUT自有正常闭环。
