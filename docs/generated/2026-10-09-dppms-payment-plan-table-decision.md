> 本建议已撤回：用户指定表名contract_collection_plan。当前整表方案见[21列完整清单](2026-10-09-dppms-contract-collection-plan-table-decision.md)，字段仍待裁决。

# com_payment_plan 回款计划整表裁决

状态：等待用户整表裁决，未按本建议改表或写入数据。

本表是本任务先前建立的26列实体，并非既有业务表。完整核查原pm_pb_plan_from_sms的16列及已有合同应收模型后，建议将其确认为独立正式回款计划实体，保留22列，撤回4个重复来源审计列。

用户已明确：既有com_contract_receivable是合同回款信息，不是合同回款计划，不列为原计划的承接候选，也不追加计划字段改变其业务定义。原计划没有其必填合同金额、发货金额、收款金额，不能人为填零来制造收款事实。当前mini没有装配crm_receivable_plan Owner，遗留菜单不等于已有可用实体。

## 一次确认事项

1. 正式回款计划子表保留下面22列，不增加当前流程状态，不将原事件完成时间转换为现平台验收或回款到账完成事实。
2. 原4项审计复用creator/create_time/updater/update_time，撤回source_created_at/source_created_by/source_updated_at/source_updated_by。历史日期用原值；NULL更新时间用原创建时间并列明，原创建时间NULL保持待裁决。
3. 可核实原人员通过已批准SYS迁移身份关联；CrmInterfaceUser、crmapp没有获准迁入账号，建议原服务操作者编码作为正式creator/updater历史字符串保存，不创建账号、不授权。
4. 合同按正式身份关联；计划可以通过已有项目合同关系关联多个项目，不按项目复制同一条合同计划。

## 建议22列

| 正式字段 | 类型 | 含义与来源 |
|---|---|---|
| id | bigint | 正式技术主键；原id由既有INT来源绑定关联 |
| tenant_id | bigint | 迁移租户，所有查询及引用保持租户隔离 |
| contract_id | bigint | 精确正式合同关系；原合同号不能跨公司猜配 |
| project_id | bigint | 关联迁移项目；共享合同计划沿正式项目合同关系供其他来源项目查询，不重复复制业务计划 |
| contract_no | text | 原contractNo完整保存 |
| batch_code | text | 原batchCode完整保存 |
| plan_name | text | 原basicDataName完整保存 |
| reference_event_name | text | 原referenceEventName完整保存 |
| event_planned_at | datetime(3) | 原eventPlanHappenDate完整保存 |
| after_days | decimal(24,6) | 原afterDaysNum完整十进制保存 |
| event_actual_finished_at | datetime(3) | 原eventActualFinishDate历史业务日期，不生成当前完成事实 |
| marketing_feedback | text | 原marketingFeedback完整保存 |
| data_source | text | 原dataSource完整保存 |
| effective_from | datetime(3) | 原effectiveFrom |
| effective_to | datetime(3) | 原effectiveTo |
| source_system | varchar(128) | 集成来源DPPMS，供既有幂等查询使用，不代替data_source业务类别 |
| source_record_key | varchar(128) | 原技术id的来源身份，用既有INT同步绑定复用；无另外legacy_record_id镜像 |
| creator | varchar(64) | 可核实账号关联SYS正式用户ID；无迁入账号的原服务操作者编码保留为历史审计字符串，不创建账号、不授予权限，待用户本次裁决 |
| create_time | datetime(3) | 保留原创建时间，不以本次迁移时间覆盖；全历史原创建时间NULL记录另列待裁决，不编造 |
| updater | varchar(64) | 可核实账号关联SYS正式用户ID；本批CrmInterfaceUser、crmapp为原服务操作者，建议未关联时原码保留正式审计字段，不伪造账号 |
| update_time | datetime(3) | 保留原更新时间；若原值NULL，建议用该记录原createTime初始化并列明原NULL，不使用当前运行时间 |
| deleted | bit(1) | 正式逻辑删除；原表无删除标志，沿既有历史实体创建规则初始化，不生成现平台审批状态 |

## 撤回4个重复列

| 当前重复列 | 正式复用 |
|---|---|
| source_created_at | create_time |
| source_created_by | creator |
| source_updated_at | update_time |
| source_updated_by | updater |

## 原表全部16列

| 原字段 | 正式承接点 | 处理或排除原因 |
|---|---|---|
| id | 既有INT来源绑定 / source_record_key | 原自增ID仅用于幂等及来源关系，不作新增业务属性或覆盖正式主键 |
| contractNo | contract_no / contract_id | 合同号原值，关联同租户且公司已按批准规则转换的精确正式合同；不按名称猜配 |
| batchCode | batch_code | 完整原计划批次 |
| basicDataName | plan_name | 完整原回款计划事件名称，不当成施工任务 |
| referenceEventName | reference_event_name | 完整原参考事件名称 |
| eventPlanHappenDate | event_planned_at | 完整原计划事件日期 |
| afterDaysNum | after_days | 完整原间隔天数，允许来源小数；超精度阻断，不取整 |
| eventActualFinishDate | event_actual_finished_at | 历史事件实际日期，不据此生成现平台施工、验收、审批或回款到账完成事实 |
| marketingFeedback | marketing_feedback | 完整销售反馈，不塞备注或快照代替 |
| createBy | creator | 可核实账号关联SYS正式用户ID；无迁入账号的原服务操作者编码保留为历史审计字符串，不创建账号、不授予权限，待用户本次裁决 |
| createTime | create_time | 保留原创建时间，不以本次迁移时间覆盖；全历史原创建时间NULL记录另列待裁决，不编造 |
| updateBy | updater | 可核实账号关联SYS正式用户ID；本批CrmInterfaceUser、crmapp为原服务操作者，建议未关联时原码保留正式审计字段，不伪造账号 |
| updateTime | update_time | 保留原更新时间；若原值NULL，建议用该记录原createTime初始化并列明原NULL，不使用当前运行时间 |
| effectiveFrom | effective_from | 完整原业务有效期开始；不激活新流程 |
| effectiveTo | effective_to | 完整原业务有效期结束；不伪造当前完成状态 |
| dataSource | data_source | 完整原业务来源类别；与source_system=DPPMS的集成来源含义区分 |

## 只读证据

本批10条来源对应10条现实体，全表10条。现业务列及来源审计镜像值差异：{}；正式审计日期与原值不一致：{"create_time": 10, "update_time": 10}，须纠正，不能将镜像保留当成正式审计承接。

原全表统计：{"rows_count": 35501, "null_create_time": 0, "null_update_time": 0}。当前数据存在不代表已获结构批准或完成验收。需先完成SYS身份迁移、来源关系核查及正式Owner纠正，再受保护清理重复列和整理SQL；本次数据库写入0。
