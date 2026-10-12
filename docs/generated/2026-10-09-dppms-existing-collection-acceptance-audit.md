# 合同回款验收日期既有承接链路核对

核对日期：2026-10-09。范围：指定 project-detail-followup 工作树、已确认的 DPPMS 原库及本任务 npdms_domain_test 数据库。只读核对，无业务数据写入、DDL、服务重启或部署；不作为真实页面验收结论。

结论：项目实施计划使用的回款节点计划验收日期已有正式字段与同步 Owner，应复用。完整合同回款计划的全部原字段没有被这条日期同步链路承接；不能据此认定无需其他业务承接，也不能跳过用户整表裁决直接建设新表。

用户明确业务定义：`com_contract_receivable` 是合同回款信息，不是合同回款计划。它仅作为相关信息表核对，不列为原回款计划的承接候选，不通过追加计划字段改变其业务定义。

## 现成链路及物理表

`pm_pb_plan_from_sms.eventPlanHappenDate` → INT 的 `PAYMENT_PLAN_ACCEPTANCE` → `ProjectPaymentAcceptanceApi` → 阶段或任务的 `acceptance_time` → `ProjectStagePlanApiImpl.calculateSchedule` → 实施计划建议日期及验收上限。

| 表与字段 | 已核实的业务含义 | 复用边界 |
|---|---|---|
| `proj_project_stage.acceptance_time` | 阶段计划验收时间，实际库为 DATETIME(3) | 实施计划将其作为验收日期及适用倒排规则的日期输入；不能当实际验收完成时间 |
| `proj_project_task.acceptance_time` | 任务计划验收时间，实际库为 DATETIME(3) | 进入计划输入快照，任务计划页面也使用它；阶段倒排计算直接读取阶段字段，不宣称由任务日期自动汇总到阶段 |
| `proj_payment_acceptance_binding` | 当前节点与集成来源的所有权绑定，实际库10列 | 保存租户、节点类型/ID、来源 Owner/key 及绑定审计；没有验收日期列，不是完整合同回款计划业务实体 |
| `com_contract_receivable` | 合同回款信息，包含合同金额、已发货、已回款、应收、逾期等信息，实际库52列 | 业务定义不是回款计划；不作为原计划16列的承接候选，不追加计划字段改变该定义 |
| `com_payment_plan` | 本任务此前新增的26列实体，实际库仍存在 | 不能当作本功能原先就有的业务表；已有数据保护，纠正结构尚待裁决 |
| `com_contract_collection_plan` | 用户指定的新承接名称 | 实际库尚不存在；此前21列方案没有获得整表批准 |

同时核对实际库全部表名称及注释：未发现另一个现成的合同回款事件计划表。仓库有上游 CRM 回款计划前端与菜单种子，但实际任务库没有 `crm_receivable_plan`，所查实施计划链路也未调用它；不能把页面或菜单当作已有业务表。

## 取值与更新规则

1. `PaymentPlanSyncTemplate` 从原表取 `eventPlanHappenDate`。参考事件需唯一命中当前有效的 `fnd_basic_data` 09类记录；目标 STAGE/TASK 和节点业务编码必须显式配置，不能猜配。
2. `PaymentAcceptanceMapper` 按同租户、合同号及阶段/任务编码匹配当前 ACTIVE 项目节点，要求唯一命中。不唯一、缺节点、绑定被其他来源占用、未允许接管现有日期等情形返回问题，不任选一条。
3. 正式 Owner 仅刷新当前计划验收时间并维护版本及绑定，不更新实际结束时间，不生成验收或收款完成事实，不改写已生效施工计划的快照。
4. `calculateSchedule` 读取阶段 `acceptance_time`；`StageSuggestionRules` 的 `PMS_IMPORTED` 规则优先使用带入验收日期，缺失时按既定工期锚点回退。其他签约方式及参照阶段规则按现有配置处理，不能把所有建议结束日期描述成验收日期的直接复制。
5. 该同步匹配当前没有公司条件，且只处理 ACTIVE 项目。扩大历史迁移时，公司消歧、一个合同关联多个项目、已闭环项目，以及以 NOW() 判断原字典有效期的历史边界仍需明确，不能把当前节点匹配规则直接当成全历史合同计划承接规则。

## 原16列在既有日期链路中的覆盖

以下“待承接”表示现成日期链路没有完整业务承接点，不是丢弃裁决。原值继续保护；不能以绑定审计或迁移快照替代来源业务属性。

| 原字段 | 既有日期链路中的用途/承接情况 | 完整原字段承接结论 |
|---|---|---|
| `id` | 来源 key、INT 绑定及节点输入绑定 | 技术关联可复用，不覆盖正式主键 |
| `contractNo` | 精确匹配项目合同号 | 匹配用途存在；合同计划自身的完整关系仍待整表规划 |
| `batchCode` | 未使用 | 待承接，未批准排除 |
| `basicDataName` | 未使用 | 待承接，未批准排除 |
| `referenceEventName` | 09类事件校验及显式节点映射 | 转换用途存在；原事件名称没有单独正式业务持久化点，待承接 |
| `eventPlanHappenDate` | 正式阶段/任务 `acceptance_time` | 适用节点日期可复用；不等于所有计划行均已完整承接 |
| `afterDaysNum` | 未使用 | 待承接，未批准排除 |
| `eventActualFinishDate` | 未使用 | 待承接，不能替代计划验收日期或生成当前完成事实 |
| `marketingFeedback` | 未使用 | 待承接，未批准排除 |
| `createBy` | 未使用；绑定创建人是同步操作者标识 | 来源人员待承接，不能用绑定审计替代 |
| `createTime` | 未使用；绑定时间是本次绑定时间 | 来源历史时间待承接 |
| `updateBy` | 未使用；节点更新人是同步标识 | 来源人员待承接，不能用节点更新审计替代 |
| `updateTime` | 未使用；节点更新时间是当前更新时刻 | 来源历史时间待承接 |
| `effectiveFrom` | 未作为计划有效期承接 | 待承接，未批准排除 |
| `effectiveTo` | 未作为计划有效期承接 | 待承接，未批准排除 |
| `dataSource` | 未使用 | 待承接，未批准排除 |

## 原表与目标库只读数据核对

- 原表共35,501条，其中计划事件日期非空3,628条，实际事件日期非空9,128条。
- 按现有模板当前有效09类规则唯一命中30,932条，其中计划事件日期非空3,333条。这是模板适用计数，不代表这些行已迁入。
- 本批10条原计划的计划事件日期全部NULL，实际事件日期6条非NULL。保留计划NULL，不借实际日期补值。
- 实际库租户1未删除的阶段中5条具有计划验收日期，任务中1条具有计划验收日期；绑定表6条（5阶段、1任务）均有目标和日期。
- 实际配置的日期同步任务为 `DETAIL_ACCEPTANCE_ISOLATED`，来源 `codex_detail_payment_source`；上述存在数据只能证明现成结构和隔离验证配置存在，不能计为本批DPPMS计划已同步或真实业务页面验收通过。

私有只读证据：`native-followup-private/read-existing-collection-acceptance-20261009.json`。不包含凭据，原库及目标业务写入均为0。

## 代码证据

- `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/PaymentPlanSyncTemplate.java`
- `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/sync/PaymentPlanAcceptanceAdapter.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/stageplan/ProjectPaymentAcceptanceApiImpl.java`
- `pms-module-project/src/main/resources/mapper/projectmanual/PaymentAcceptanceMapper.xml`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/stageplan/ProjectStagePlanApiImpl.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/domain/projectschedule/StageSuggestionRules.java`
- `sql/migrations/V247__project_execution_node_times.sql`、`V314__stage_plan_calculation_snapshot.sql`、`V315__payment_plan_acceptance_source_binding.sql`、`V355__stage_suggestion_rules.sql`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectSchedulePanel.vue`、`SchedulePlanningTable.vue`
- 旧实现 `D:/EclipseWorkspace/Parctice/PMS/PMS-struts/src/com/dp/plat/job/PlanGetBySMS.java:94`：按参考事件查基础数据，再按合同及任务类型更新 `pm_project_task.eventPlanHappenDate`，与原回款计划保存分开；不复制旧实现的拼接SQL、任取首行或清表做法。
