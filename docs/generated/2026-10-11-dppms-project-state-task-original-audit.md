# 原项目状态及计划任务与现有模型只读核查

状态：SOURCE_AND_EXISTING_STRUCTURE_VERIFIED / WHOLE_TABLE_PLAN_PENDING。领域已批准启动，但本文件不批准新增表列、字典、生命周期更新、任务/审批完成或现平台执行初始化。

## 原项目状态不是历史事件流水

实际pm_project_state为9列、80245条，本批10条；projectId是唯一主键，没有独立id。原insert_project_state/update_pm_project_state按项目插入或覆盖四种最新状态及各自更新时间，它是项目状态缓存，不是多行状态转换历史。不能据一条当前旧状态造出过去状态转换序列。

| 原字段 | 类型 | 原含义及承接缺口 |
|---|---|---|
| projectId | INT | 原项目键，须复用正式来源绑定 |
| projectPlanState | VARCHAR(10) | 工程计划状态，不能当作当前生命周期状态 |
| projectplanTime | DATETIME | 工程计划状态更新时间；10159条NULL，不能当计划开始时间 |
| shipmentState | VARCHAR(11) | -1已发、1未发、2部分；原查询按项目产品数量计算，不能变成当前验收事实 |
| shipmentTime | DATETIME | 发货状态更新时间；不是原发货日期 |
| executionState | VARCHAR(45) | 旧实施状态，与新阶段/任务状态映射待明确 |
| executionStateTime | DATETIME | 实施状态更新时间；60500条NULL，不造完成时间 |
| closeProcessState | VARCHAR(45) | 旧闭环流程状态，不能据此完成现审批节点 |
| closeProcessStateTime | DATETIME | 闭环流程状态更新时间；不是已裁决原projectCloseTime的替代 |

已批准项目主表100/20的状态映射继续保持，本表另有四类状态，不能把主表裁决扩大到它们。proj_task_state_transition实际保存revision_id、动作、条件和from/to定义，是状态机规则表，不是旧状态事件承接表。

## 原计划任务及实际字段

pm_project_task实际28列、129626条，本批37条；旧页面/先前返回的26列不等于完整原表。全量主体10有89628条、20有39591条、afss有391条、afxx有16条；不能把所有主体按相同projectId挂到售后项目。

本批37条全部类型09、status=0，但32条有eventActualFinishDate。不能把原0直接转为新PENDING_ASSIGN，也不能忽略历史完成日期；旧任务状态与新任务状态机必须独立明确。全源还有100、50、afApproveTask、end、reject，未臆造对应状态。

全量parentId、planStartTime、planEndTime、priority全部NULL；不存在本来源中需要重建的父子任务链。原工程事件日期eventPlanHappenDateENG与款项事件日期eventPlanHappenDate各自有值，不能合并丢失其中一类。原updateTime/updateBy各108895条NULL，effectiveFrom全部非NULL、effectiveTo有21880条非NULL。

| 原字段 | 原类型 | 已有字段候选/明确缺项 |
|---|---|---|
| taskId | int(11) | 迁移来源绑定；非正式任务码 |
| projectId | int(11) | project_id，需来源项目精确绑定 |
| projectType | varchar(25) | 主体分类需真实项目核实，不按同数字ID挂靠 |
| contractNo | varchar(45) | 原合同事实；当前执行表contract_id需要COM核实，缺失不可猜配 |
| taskTypeCode | varchar(45) | 原任务类型分组，与taskTypeId组合查原字典；不是当前阶段码 |
| taskTypeId | varchar(25) | 原分组内业务键；不是当前任务ID或模板定义ID |
| taskName | varchar(255) | 当前name有同义候选；129219条原NULL，原查询从字典取得名称，须区分原值与派生显示 |
| eventPlanHappenDate | datetime | 原款项计划发生日期；不能与工程计划或acceptance_time直接混用 |
| eventPlanHappenDateENG | datetime | 原工程计划发生日期；与原款项日期独立，承接点待明确 |
| planStartTime | datetime | plan_start_time有同义候选，全源NULL；不造历史开始日期 |
| planEndTime | datetime | plan_end_time有同义候选，全源NULL；不从其他日期自动覆盖 |
| actualStartTime | datetime | actual_start_time有同义候选，不生成当前节点启动动作 |
| eventActualFinishDate | datetime | actual_end_time有完成日期候选；不据此生成当前Gate或审批完成事实 |
| priority | varchar(25) | priority有候选，全源NULL；原字符串值域和正式整数值域不能凭名称推定 |
| progress | int(3) | progress有同义进度候选，不作为完成状态或授权依据 |
| progressDesc | varchar(255) | 历史进度说明，现任务没有同义独立字段；不挪用description覆盖其他文本 |
| status | varchar(25) | 原状态保留及转换待裁决，不能直接写正式状态机状态 |
| parentId | int(11) | parent_task_id有同义关系候选，全源NULL；本来源没有需重建的父子层级 |
| remark | text | 原备注业务文本；现任务description不是已批准的同义承接点 |
| createTime | datetime | create_time候选，全源非NULL，保持原历史时间 |
| createBy | varchar(45) | creator候选，精确关联已迁账号；未匹配编码处理待明确 |
| updateTime | datetime | update_time候选，108895条NULL，与正式非空约束处理待裁决 |
| updateBy | varchar(45) | updater候选，108895条NULL，不自动填admin |
| effectiveFrom | datetime | 原记录有效期，现正式任务没有同义区间列；不能替代计划或执行日期 |
| effectiveTo | datetime | 原记录有效期结束，不能仅靠deleted或status代替原日期 |
| visibleFlag | varchar(2) | 原1可见、2不可见，8579条NULL；不是授权或当前任务取消状态 |
| deliverFileIds | varchar(255) | 原交付文件引用，需精确核实文件及正式交付关系；不造验收满足事实 |
| customInfo | json | 完整原JSON对象，现任务没有同义列；不能以执行result_snapshot代替业务承接 |

## 现有正式任务表全部42列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| project_id | bigint | NO |
| task_code | varchar(64) | NO |
| name | varchar(128) | NO |
| parent_task_code | varchar(64) | YES |
| parent_task_id | bigint | YES |
| root_task_id | bigint | NO |
| tree_depth | int unsigned | NO |
| business_level_code | varchar(64) | YES |
| milestone_id | bigint | YES |
| plan_start_time | datetime(3) | YES |
| plan_end_time | datetime(3) | YES |
| actual_start_time | datetime(3) | YES |
| actual_end_time | datetime(3) | YES |
| progress | decimal(5,2) | NO |
| state_machine_revision_id | bigint | NO |
| stage_code | varchar(32) | NO |
| priority | int | NO |
| sort_order | int | NO |
| estimated_hours | decimal(6,1) | YES |
| satisfaction_timing | varchar(32) | YES |
| acc_satisfaction_template_id | bigint | YES |
| template_revision_id | bigint | YES |
| template_version | int | YES |
| satisfaction_rule_version | varchar(64) | YES |
| satisfaction_threshold | decimal(7,2) | YES |
| description | longtext | YES |
| source_definition_id | bigint | YES |
| status | varchar(32) | NO |
| version | int unsigned | NO |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |
| deleted_time | datetime | YES |
| tenant_id | bigint | NO |
| description_format | varchar(16) | NO |
| active_task_code | varchar(64) | YES |
| suggested_start_time | datetime(3) | YES |
| suggested_end_time | datetime(3) | YES |
| acceptance_time | datetime(3) | YES |

ProjectTaskInstanceDO及当前实例化模型冻结模板/状态机等事实；原任务没有这些定义。只看到有name、日期、progress并不足以确认全部旧任务可合法直接插入当前实例，不能编造stage_code、source_definition_id、state_machine_revision_id或冻结模板。

## 现有执行记录全部25列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| tenant_id | bigint | NO |
| project_id | bigint | NO |
| plan_version_id | bigint | NO |
| node_key | varchar(128) | NO |
| node_kind | varchar(16) | NO |
| node_instance_id | bigint | NO |
| contract_id | bigint | NO |
| round_no | int | NO |
| current_marker | tinyint | YES |
| status | varchar(16) | NO |
| admitted_at | datetime(3) | YES |
| started_at | datetime(3) | YES |
| started_plan_version_id | bigint | YES |
| submitted_at | datetime(3) | YES |
| submitted_by | bigint | YES |
| submission_note | varchar(2000) | YES |
| ended_at | datetime(3) | YES |
| result_snapshot | json | YES |
| version | int | NO |
| creator | varchar(64) | NO |
| create_time | datetime(3) | NO |
| updater | varchar(64) | NO |
| update_time | datetime(3) | NO |
| deleted | bit(1) | NO |

该表依赖plan_version_id、node_key、node_kind、node_instance_id和轮次，原任务没有当前计划/执行契约身份。不得把旧完成日期直接转换成现平台有效执行或Gate满足；result_snapshot也不能作为全部原字段的业务承接替代。

## 现有状态机转换定义全部17列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| revision_id | bigint | NO |
| from_status_code | varchar(64) | NO |
| action_code | varchar(64) | NO |
| to_status_code | varchar(64) | NO |
| standard_status_mapping | varchar(64) | NO |
| allowed_role_code | varchar(128) | NO |
| entry_condition | json | NO |
| exit_condition | json | NO |
| version | int unsigned | NO |
| creator | varchar(64) | YES |
| create_time | datetime(3) | NO |
| updater | varchar(64) | YES |
| update_time | datetime(3) | NO |
| deleted | bit(1) | NO |
| deleted_time | datetime(3) | YES |
| tenant_id | bigint | NO |

废弃pms_project_task_retired仅允许存量查询，DO明确禁止新代码引用，不能因为旧字段相似就承接新迁移。后续先明确正式历史计划事实的业务用途及缺项方案，所有新增表列/扩展逐整表提交；本轮没有原字段获准排除，也未把全NULL字段自动判为可删除。

证据：任务私有project-state-task-original-readonly-20261011.json，包含真实SHOW FULL COLUMNS、全量计数/NULL统计、本批原行及现有三表字段。旧PMS-struts/config-ibaits/sql-map-project-config.xml:2777、2782、2800、3847、3862；当前ProjectTaskInstanceDO/ProjectTaskRetiredDO。schemaWrites=0、businessWrites=0。只读候选不代表正式承接或当前功能验收完成。

## 2026-10-12 原写入链路及现有施工计划补核

本批37条完整28字段与先前材料逐条一致。全历史类型与主体分别为：projectType=10／taskTypeCode=09共89628条；20／29共39591条；afss／afProjectTaskType共391条；afxx／afProjectTaskType共16条。不能把售前及不同业务主体直接挂到售后项目。

ProjectServiceImpl.editProjectPlan先按项目将旧pm_project_task记录写入effectiveTo、updateTime及updateBy，再按事件和合同新增计划行。插入单独保存eventPlanHappenDate、eventPlanHappenDateENG、eventActualFinishDate；前两者是款项计划与工程计划，不能合并。visibleFlag由合同及款项日期规则决定，不表示账号权限。上传交付资料会更新eventActualFinishDate并重新求当前计划状态；原实际日期不是当前新任务审批或Gate完成的凭据。

pm_project_state的insert/update只保存每类最新值及当次NOW()，不存在过去每次状态事件。发货状态查询按原项目产品openQuantity/orderQuantity计算；计划状态根据有效计划及完成日期查询；闭环流程状态在项目不予跟踪或已闭环时强制50。实施状态有手工业务更新、交付条件及真实fnd_sys_arg后置配置，不能把四类状态一概当成当前新系统可完全重算的值。

真实来源字典明确executionState=90为“维护阶段”（11717条），不能猜成“已关闭”；7为“挂起状态”（2条）。工程计划状态40～48及闭环流程10、15、20、30、40、50分别按真实原字典解释，不能因数字同名直接映射当前生命周期。实际后置配置pm.project.state.update.rules.config存在并启用executionStateUpdate，引用executionState、prevExecutionState；该配置不作为授权执行脚本迁入平台。

现有StagePlanBatchServiceImpl.createDraft读取正式冻结项目路径和生效工期，形成审批批次；审批生效后经ProjectStagePlanApi写阶段/任务计划。旧表没有该冻结版本、阶段实例或批准批次身份，不能调用当前推算或审批来补造。sol_stage_plan_item只有DATE类型起止日期，没有同义的合同事件计划/实际完成DATETIME字段；task_plans_json及input_snapshot不能替代具名业务属性承接旧28字段。以下完整现有结构仅作复用核查，不批准新增字段或改约束。

### sol_stage_plan_batch 实际全部23列

| 字段 | 类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| project_id | bigint | NO |
| status | tinyint | NO |
| duration_revision_id | bigint | YES |
| remark | varchar(500) | YES |
| bpm_process_key | varchar(64) | YES |
| bpm_process_instance_id | varchar(64) | YES |
| submitted_at | datetime(3) | YES |
| submitted_by | bigint | YES |
| effective_at | datetime(3) | YES |
| effective_by | bigint | YES |
| reject_reason | varchar(500) | YES |
| version | int | NO |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |
| tenant_id | bigint | NO |
| calculated_start | date | YES |
| calculated_end | date | YES |
| task_plans_json | longtext | YES |
| input_snapshot | longtext | YES |

### sol_stage_plan_item 实际全部17列

| 字段 | 类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| batch_id | bigint | NO |
| phase_id | bigint | NO |
| phase_code | varchar(64) | YES |
| phase_name | varchar(128) | NO |
| sort | int | NO |
| suggested_start | date | YES |
| suggested_end | date | YES |
| plan_start | date | YES |
| plan_end | date | YES |
| remark | varchar(500) | YES |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |
| tenant_id | bigint | NO |

### sol_construction_plan 实际全部13列

| 字段 | 类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| project_id | bigint | NO |
| current_duration_revision_id | bigint | YES |
| pending_change_id | bigint | YES |
| plan_recalculation_status_code | varchar(32) | NO |
| plan_recalculation_source_revision_id | bigint | YES |
| version | int unsigned | NO |
| creator | varchar(64) | YES |
| create_time | datetime(3) | NO |
| updater | varchar(64) | YES |
| update_time | datetime(3) | NO |
| deleted | bit(1) | NO |
| tenant_id | bigint | NO |

本次补核材料为任务私有project-state-plan-owner-readonly-20261012.json，含实际列定义、原状态值分布、真实字典、原任务分类及本批28字段。仅只读，businessWrites=0、schemaWrites=0。工程计划的正式承接方案及旧状态属性处理仍待整表裁决。

### 既有项目custom_info中已承接的实施属性

原后置脚本已实际核对：executionStateUpdate读取项目实施状态，写原项目customInfo的executionState、executionStateTime及prevExecutionState；后者被写为当次实施状态，不能将字段名称当作一条过去状态事件。挂起/解挂还分别写suspendedStateTime、resumedStateTime，并调用通知；通知不等于业务完成。这些规则没有迁入执行。

本批51304、52443、53649的三个上述属性已随获准原customInfo完整进入正式proj_project.custom_info，逐值一致，无新增字段、属性配置或状态写入。51304为80／2025-10-26 20:23:34；52443为90／2025-11-24 08:49:28；53649为90／2025-08-17 22:27:06。前两项pm_project_state实施时间与JSON一致；53649的pm_project_state.executionStateTime是2025-08-17 22:27:05，与原JSON相差1秒，是两次原写入产生的独立值，不能将其合并或宣称该缓存时间也已原样承接。其余7个试点原JSON没有这些键，不能默认用当前新状态补齐。其他计划/发货/闭环状态及时间仍未获正式承接或排除裁决。
