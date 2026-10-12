# com_contract_collection_plan 整表裁决

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。用户回复“19列及上述处理整表按建议”；本清单19列、7列撤回、合同待关联及服务操作者审计处理已批准。先前21列未批准建议已被替代。持续同步身份问题仍待回复。

本表业务主体为合同回款计划。`com_contract_receivable` 已由用户明确为合同回款信息，不是计划，不向它追加计划字段。实施计划现成的阶段/任务 `acceptance_time` 继续复用，完整计划与日期消费分别承担职责。

## 本次整表建议

- 保留已有10条计划业务记录及正式ID；纠正为用户指定表名，不通过新建另一套记录再删除原记录完成改名。当前业务表26列，建议最终19列。
- 原16列全部列明；15项原业务/审计属性入正式列，技术ID由既有INT绑定关联。不增加任何source_*镜像列，也不把完整计划写入快照充当业务承接。
- 原basicDataName对应旧CRM/SMS的money_item_name，是回款款项名称；建议collection_item_name。reference_event_name单独保存参考事件，不混用。
- 原afterDaysNum为int(11)，建议after_days INT；不采用先前无依据的小数类型。字符串容量至少覆盖原列定义，不以本批实际最大长度缩短。
- contract_id建议允许NULL：无法唯一匹配正式合同时完整计划仍进入正式业务表并登记待关联，不挂靠任何项目；不以名称或合同号跨公司任选一条。原表没有公司字段，不另猜公司，不给现表加公司镜像。
- 可核实原人员先关联已批准迁入的SYS账号；未迁入的服务操作者CrmInterfaceUser、crmapp建议保留原审计字符串，不伪造账号和授权。该例外已随19列获得用户批准。
- 原创建/更新时间直接复用正式审计列；本全表均非NULL，本批10条当前正式时间与原值不同，需经纠正Owner恢复原历史值。不会使用本次迁移时刻替代。

## 全部19列

| 列 | 类型 | 可空 | 含义及处理 |
|---|---|---|---|
| `id` | `bigint` | 否 | 正式技术主键；已有10条记录保留ID，原自增ID仅由既有INT绑定关联 |
| `tenant_id` | `bigint` | 否 | 当前迁移租户；查询、写入、合同和绑定均隔离 |
| `contract_id` | `bigint` | 是 | 精确正式合同引用；无法唯一匹配时完整计划先保留，引用为空且登记待关联，不按名称或跨公司猜配 |
| `contract_no` | `varchar(64)` | 是 | 原合同号；原列varchar(25)，不裁剪、不按当前项目代码替代 |
| `batch_code` | `varchar(32)` | 是 | 原计划批次；原列varchar(10) |
| `collection_item_name` | `varchar(64)` | 是 | 回款款项名称，如初验款、终验款；原money_item_name，不命名为计划事件 |
| `reference_event_name` | `varchar(64)` | 是 | 原参考事件，如初验、终验、到货验收；与回款款项分开 |
| `event_planned_at` | `datetime(3)` | 是 | 原事件计划发生日期；可经既有Owner供适用阶段/任务的计划验收日期取值，NULL保持NULL |
| `after_days` | `int` | 是 | 原后推天数，原int(11)；原样保留整数、0、NULL，不增加小数精度或自动算回款到期日 |
| `event_actual_finished_at` | `datetime(3)` | 是 | 原事件实际完成日期；不作为回款到账日期或当前验收完成凭据 |
| `marketing_feedback` | `varchar(2000)` | 是 | 完整销售反馈，按原列容量保存 |
| `data_source` | `varchar(25)` | 是 | 实际业务来源CRM/SMS，原值保存；本全表为CRM，不另设DPPMS来源镜像 |
| `effective_from` | `datetime(3)` | 是 | 原记录有效期开始，原样保留 |
| `effective_to` | `datetime(3)` | 是 | 原记录有效期结束，NULL保持NULL，不自动转换为当前流程状态 |
| `creator` | `varchar(64)` | 是 | 可核实人员关联已迁入SYS用户；CrmInterfaceUser/crmapp无账号时建议保留原审计字符串，本表已批准 |
| `create_time` | `datetime(3)` | 否 | 原创建时间，本全表无NULL；不使用迁移时刻。扩大数据时遇NULL阻断并登记，不编造 |
| `updater` | `varchar(64)` | 是 | 原更新人按同一审计规则处理；不凭服务操作者原码创建账号或授权 |
| `update_time` | `datetime(3)` | 否 | 原更新时间，本全表无NULL；不使用运行时间。未来来源NULL的处理另行裁决 |
| `deleted` | `bit(1)` | 否 | 正式逻辑删除，新历史记录初始为0；不以有效期或日期推断完成、审批或到账状态 |

## 原16列完整对照

| 原列 | 原类型 | 正式承接点 | 处理 |
|---|---|---|---|
| `id` | `int(11)` | int_sync_binding.source_key → target_id（不新增业务来源ID列） | 正式技术主键；已有10条记录保留ID，原自增ID仅由既有INT绑定关联；旧系统刷新会删除重插并重置ID，不能宣称该ID跨刷新稳定，需按下面的同步保护边界处理 |
| `contractNo` | `varchar(25)` | contract_no / contract_id | 原合同号；原列varchar(25)，不裁剪、不按当前项目代码替代 |
| `batchCode` | `varchar(10)` | batch_code | 原计划批次；原列varchar(10) |
| `basicDataName` | `varchar(20)` | collection_item_name | 回款款项名称，如初验款、终验款；原money_item_name，不命名为计划事件 |
| `referenceEventName` | `varchar(20)` | reference_event_name | 原参考事件，如初验、终验、到货验收；与回款款项分开 |
| `eventPlanHappenDate` | `datetime` | event_planned_at | 原事件计划发生日期；可经既有Owner供适用阶段/任务的计划验收日期取值，NULL保持NULL |
| `afterDaysNum` | `int(11)` | after_days | 原后推天数，原int(11)；原样保留整数、0、NULL，不增加小数精度或自动算回款到期日 |
| `eventActualFinishDate` | `datetime` | event_actual_finished_at | 原事件实际完成日期；不作为回款到账日期或当前验收完成凭据 |
| `marketingFeedback` | `varchar(2000)` | marketing_feedback | 完整销售反馈，按原列容量保存 |
| `createBy` | `varchar(45)` | creator | 可核实人员关联已迁入SYS用户；CrmInterfaceUser/crmapp无账号时建议保留原审计字符串，本表已批准 |
| `createTime` | `datetime` | create_time | 原创建时间，本全表无NULL；不使用迁移时刻。扩大数据时遇NULL阻断并登记，不编造 |
| `updateBy` | `varchar(45)` | updater | 原更新人按同一审计规则处理；不凭服务操作者原码创建账号或授权 |
| `updateTime` | `datetime` | update_time | 原更新时间，本全表无NULL；不使用运行时间。未来来源NULL的处理另行裁决 |
| `effectiveFrom` | `datetime` | effective_from | 原记录有效期开始，原样保留 |
| `effectiveTo` | `datetime` | effective_to | 原记录有效期结束，NULL保持NULL，不自动转换为当前流程状态 |
| `dataSource` | `varchar(25)` | data_source | 实际业务来源CRM/SMS，原值保存；本全表为CRM，不另设DPPMS来源镜像 |

## 撤回的7列及原因

| 当前列 | 处理及原因 |
|---|---|
| `project_id` | 原计划归属合同，不绑定首次迁移项目；通过既有正式项目—合同关系查询 |
| `source_created_at` | 复用create_time，先保护并纠正原历史值 |
| `source_created_by` | 复用creator，按本表审计裁决处理 |
| `source_updated_at` | 复用update_time，先保护并纠正原历史值 |
| `source_updated_by` | 复用updater，按本表审计裁决处理 |
| `source_system` | 删除DPPMS技术来源镜像；原业务来源由data_source承接，连接及来源对象由INT绑定记录 |
| `source_record_key` | 原技术id移到既有INT绑定关联，不新增record_key别名 |

## 原表全量核对及真实限制

- 来源pm_pb_plan_from_sms共35,501条、16列；dataSource全部为CRM。款项值为初验款、到货款、终验款、质保款、预付，以及25条NULL；参考事件12条NULL均保留。
- afterDaysNum为整数，本全表0—3870，无NULL、无负数；原定义允许NULL及有符号整数，迁移不添加只凭当前样本推导的非负限制。
- 原创建、更新和有效期开始均非NULL；有效期结束全部NULL。字段空值保持原义，不臆造失效或到账事实。
- 合同号＋批次＋款项＋参考事件＋数据来源有597组、1,253条重复，不能创建该组合的唯一索引或据此合并计划。
- 旧CRM同步先删除对应来源记录后插入，PlanGetFromCRM还重置表自增ID；原id不保证跨刷新稳定。历史基线内可用既有INT绑定关联每条原记录，但扩大持续同步须确认稳定来源身份。ID复用及候选键重复不得自动重绑、覆盖或丢弃；该规则仍属BLOCKED_BY_SPEC，不能宣称持续同步已生产可用。

## 与实施计划的关系

合同计划完整保存原计划事件日期。适用事件经已存在的PAYMENT_PLAN_ACCEPTANCE / ProjectPaymentAcceptanceApi刷新正式阶段或任务acceptance_time，实施计划仍从既有字段取值，不新建验收日期镜像。原计划日期NULL保持NULL；实际事件日期不能代替计划日期、实际收款日期或当前验收完成事实。

当前节点同步要求显式事件→阶段/任务映射、租户与合同节点唯一匹配、ACTIVE项目、绑定所有权和版本保护；多项目共享合同、公司消歧和历史闭环节点边界尚未确认，不能让本次整表批准自动扩大这些规则。

## 实施与验证边界

本文件整表方案已获用户批准。实施复用既有迁移/同步执行、绑定及正式COM Owner；通过正式项目—合同关系查询完整计划。对旧10条先保护原值和关系，再纠正结构及依赖；SQL/migrations直接整理为最终批准定义，不写先加后删迁移。已执行迁移和实库校验状态单独核对，不能盲目repair。旧V4迁移凭据、批准快照及来源证据不改写。

19列DO、COM Owner和Mapper、INT模板与适配器、同步事实核验API及项目纠正入口已经实现。旧PaymentPlanDO/Mapper及原生直接写入已撤回；原生入口遇到计划明确提示先执行正式同步，不创建替代记录。项目计划查询改读com_contract_collection_plan，通过既有项目—合同关系取值，不以单项目列过滤，不从快照返回业务数据。新增纠正凭据使用COLLECTION_PLAN_V1，保留原V4等不可变凭据。

累计75项相关验证通过：COM Owner 8、真实MySQL临时表Mapper 4、COM迁移/分页/合同范围 28、原项目迁移 13、关联资料 4、新计划纠正 5、INT计划适配器 8、同步事实核验 4、原库只读模板 1。原库模板实际保留35,501条及16原列；同名未裁决的FND账号禁止回退t账号。临时表验证19列、重复业务行分别保存、原NULL/空串和毫秒历史、租户隔离、事务回滚。既有正式ID通过可映射的existingPlanId配置明确接管，原自增ID不能当正式主键。

10条既有记录已由正式同步任务2108574253093683202承接：预览2108576748264140801为UPDATED10，执行2108576833605644289为SUCCESS/UPDATED10；重复预览2108577146274230273为UNCHANGED10。全部19个正式字段和16个原字段逐项核对，正式ID、原日期、NULL及空字符串均保留。l02536、l03302通过已完成SYS迁移绑定关联；l01663在两个原用户表及正式账号中均不存在，用户明确批准仅原ID10725、14943、28926、31337的8处创建/更新人保留原编码，不新建账号或权限。

10条计划分布于本批4个项目，其他6个项目原计划为0条，未伪造空来源迁移回执。4个项目通过现有项目纠正入口建立COLLECTION_PLAN_V1凭据，并重复调用得到REPLAY_COMPLETED；10条状态均指向正式com_contract_collection_plan。原20个批次及其来源记录、外部映射逐字段不变，10个项目的来源快照和原记录不改写，计划表在凭据关联期间无变化。私有证据approved-collection-project-proof-20261009.json。

实际库已完成获准的SYS两列、项目五列准备，并将旧com_payment_plan保留10条ID和数据原样更名为com_contract_collection_plan。正式计划绑定、人员审计纠正、项目纠正凭据及重复预览已完成。项目详情的相关方查询误引用4个不存在且未批准的审计镜像列，已撤回该引用并通过真实MySQL验证；后端59282恢复，前端19082保持运行。

真实浏览器已通过4个项目全部10条计划的列表及详情核对，每条15项展示属性与已核实的正式表一致；l01663原编码、NULL日期、销售反馈和历史时间均按裁决展示。现场发现创建/更新时间显示为毫秒数字，已修正并在真实详情重验。证据collection-plan-browser-details-20261009.json。

随后执行获准的7列清理，实际表已为最终19列。contract_no、batch_code、reference_event_name、marketing_feedback同步调整为已批准的VARCHAR容量；删除依赖撤回列的uk_source和idx_project，保留主键及正式合同查询索引。执行前核对全表10条19项正式值、来源绑定、原快照和记录、原20批次及其映射，无外键、视图、例程或触发器依赖。单条原子ALTER后，上述正式值及所有被保护证据逐字段不变；DDL业务写入0，未调整Flyway历史。证据verified-collection-final-schema-20261009T235006952070.json。

清理后10个项目的正式资料接口再次逐项核对19字段通过，真实页面刷新及重开详情通过。当前SQL/migrations已直接保留最终19列定义，没有追加删除迁移。实际阶段/任务acceptance_time消费及全历史持续同步稳定身份仍待处理；Flyway历史校验与整理后的开发基线对齐未完成，不盲目repair，不能宣称整项生产迁移已完成。

已批准28项部门及后续8项缺失部门均已正式承接；1176个批准范围内账号及来源绑定、用户角色和公司部门资格范围已完成实际同步并核对。用户前置已解除；公司部门资格不据此扩大项目访问范围。

证据：旧PMS-struts/config-ibaits/sql-map-refresh-data-crm-config.xml的select/insertContractCollectionPlanFromCRM及updatePmProjectTaskEventPlanDate；旧PlanGetBySMS.java的money_item_name映射；原库SHOW FULL COLUMNS及全表只读统计；现成日期链路见[核对报告](2026-10-09-dppms-existing-collection-acceptance-audit.md)。私有证据read-collection-plan-profile-20261009.json；本次原库写入0；实际库准备和28部门写入边界见上文。
