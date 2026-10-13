# 剩余业务承接收敛（2026-10-13）

本轮复用67表字段复核、已有迁移任务及已裁决规则。查询保存在迁移模板中并可编辑；不包含十项目字面量。项目类型默认10，项目ID/合同号参数留空即整表来源，前期实际运行仅使用原十项目对应参数。每次通过迁移运行调用业务Owner，保存正常绑定与运行回执；不新增承接辅助表、历史专用页面或物理业务列。

## 最新裁决

- 相关方承接；发货状态和执行状态使用正式项目实体扩展，同时保存各自的状态更新时间，不能解释为发货/实施完成时间。
- 合同回款信息复用com_contract_receivable；空合同号直接过滤，7条分别列明“用户裁决：空合同号不迁入”。合同回款计划仍为com_contract_collection_plan，不混用。
- 旧订单行的原01迁移记录按原裁决清理。236259条缺失目标的绑定均保存未转换公司码01；001的123160条有效绑定不属于清理范围，原键含01不构成删除依据。
- 串货关系本轮不迁入：其业务是实施范围SN转至指定项目，不另生成迁移转移事实。
- 工程事件本轮暂不迁入；审批过程状态不迁入，不生成任何审批完成事实。
- 相关方两个既有审计人员字段允许NULL；NULL更新时间用原创建时间。新增物理列为0。

## 项目相关方：11原字段 → proj_project_party既有20列

| 原字段 | 正式承接 | 处理 |
| --- | --- | --- |
| id | source_record_key、既有迁移绑定 | 原ID保持来源身份，不复制为全局业务主键 |
| projectId | project_id | 已有正式项目绑定，禁止自动新建项目 |
| partyRole | party_role | 原10服务渠道、20出货渠道、30代理商 |
| partyCode | party_code | 原值 |
| partyName | party_name | 原值 |
| createTime | create_time | 原历史毫秒时间 |
| createBy | creator | 可精确关联的账号使用正式user_id，否则保留原编码，NULL保持NULL |
| updateTime | update_time | 原值；NULL用本行createTime |
| updateBy | updater | 同创建人；NULL保持NULL，不自动填管理员 |
| effectiveFrom | effective_from | 原值 |
| effectiveTo | effective_to | 原值，不生成解除动作 |

既有contact_name、phone及状态不覆盖。既有记录首次纠正必须配置全20列核对所得前值；后续运行使用上次正式结果保护。新增记录version=0，已有变更递增version。正常客户与参与方区显示渠道和代理商；查询仍按原有效区间显示当前相关方。

## 合同回款信息：39原字段 → com_contract_receivable既有52列

| 原字段 | 正式字段 | 处理 |
| --- | --- | --- |
| id | source_record_key、既有迁移绑定 | 原技术身份 |
| contract_num | contract_no | NULL按明确裁决过滤；不猜合同号 |
| batch_code | source_batch_code | 原值 |
| project_name | project_name | 原值 |
| order_num | source_order_no | 原值 |
| client_supplier_code | customer_code | 原值 |
| client_supplier_name | customer_name | 原值 |
| contract_money_amount | contract_amount | DECIMAL(20,2)，不舍入 |
| delivered_money_amount | delivered_amount | 同上 |
| collected_money_amount | collected_amount | 同上 |
| collected_money_ratio | collected_ratio | DECIMAL(18,6)，保存原单位，不截到0～100 |
| receivables_money_amount | receivable_amount | DECIMAL(20,2)，不舍入 |
| over_due_money_amount | overdue_amount | 同上 |
| maketing_department_name | market_name | 原名称 |
| office_name | department_name | 原名称 |
| industry_name | industry_name | 原名称 |
| marketing_representative_name | marketing_representative_name | 原值 |
| currency_name | currency_name | 原值 |
| create_by | creator | 精确迁入账号关联；未关联原编码保留，不创建账号或授权 |
| create_time | create_time | 原历史时间 |
| update_by | updater | 同创建人 |
| update_time | update_time、source_sync_time | 原历史时间 |
| effective_from | source_effective_from | 原值 |
| effective_to | source_effective_to | 原值 |
| import_batch_num | import_batch_no | 原值 |
| contract_create_date | contract_create_time | 原值 |
| projectCode | project_code | 原值 |
| marketCode | market_code | 原值 |
| systemId | system_source_key | 原业务键，不以名称猜关联 |
| industryId | industry_code | 原值 |
| officeCode | department_code | 原值 |
| expendId | expend_source_key | 原业务键 |
| usernamec | marketing_representative_code | 原值 |
| latest_ship_date | latest_ship_time | 原值，不生成发货事件 |
| usernamec2 | secondary_representative_code | 原值 |
| systemid_o | original_division_values.systemSourceKey | 已有正式JSON属性，整数/NULL保持 |
| expendid_o | original_division_values.expendSourceKey | 同上 |
| industry_name_o | original_division_values.industryName | 原值 |
| dataSource | source_system | 原CRM/SMS类别 |

现原金额和比例均可无损保存到既有精度。合同引用仅在原合同号唯一精确匹配时补关联；否则NULL，回款信息本身不被阻断。已建立的公司、合同、部门关系保护，不覆盖ERP主档的权威字段。原39列和六种金额/比例完整承接，不写不存在的source_row_id、source_revision_hash或source_created/updated镜像。

正常项目商务查询沿已授权项目合同关系，以原合同号精确查询回款信息，不要求来源回款行先解析到主档唯一ID。金额与客户仍遵守现有敏感商务权限。

## 项目过程缓存：全部9原字段

| 原字段 | 承接/排除 | 原因 |
| --- | --- | --- |
| projectId | 正式项目实体身份 | 依既有绑定和同类型同编码最大projectId规则 |
| shipmentState | PRJ.project扩展shipment_status | 用户批准；原业务码完整保留 |
| shipmentTime | 扩展shipment_status_updated_at | 用户批准；状态更新时间 |
| executionState | 扩展execution_status | 用户批准；原业务码完整保留 |
| executionStateTime | 扩展execution_status_updated_at | 用户批准；状态更新时间 |
| projectPlanState | 暂不迁入 | 随工程过程暂缓，不伪造实施计划完成 |
| projectplanTime | 暂不迁入 | 对应工程计划缓存更新时间 |
| closeProcessState | 不迁入 | 审批过程状态明确不承接 |
| closeProcessStateTime | 不迁入 | 审批缓存更新时间，不当作闭环日期 |

使用现有plt_entity_extension_definition及plt_entity_extension_value，定义通过既有PLT API发布、PROJ Owner读写。主项目custom_info原JSON保持；状态扩展显示在正常项目档案区。

## 原01订单行绑定清理

复用迁移运行，配置原订单任务ID。只选择本租户、本连接、该原任务中object=LINE、source_object=pm_order_line_from_erp、fields_json.companyCode严格等于01的绑定。原任务必须停止运行；COM Owner再次确认目标物理行不存在（包括逻辑删除行）。任一目标存在、原任务变化或删除数量变化均阻断、回滚当前块。

只删除这些可核实旧绑定；001、其他公司、其他租户/任务、有效目标与不可变原运行回执保持。空集合返回空，重放为SKIPPED。模板SQL整表读取原01身份，不含限定十项目名单，也不把原公司01统一等同于待删除。

## 验证记录

代码、指定24306真实MySQL临时表、真实迁移和正常浏览器验收结果将在本文件下方追加。未执行的检查不写通过。6570条超出原十项目范围的项目主档仍不接管、不继续写入其关联资料。

## 实际完成结果

| 项目 | 正式任务/运行 | 实际结果 |
| --- | --- | --- |
| 合同回款信息 | 任务2109967339937804289；运行2109967565692022785 | SUCCESS，读取10、CREATED=10；独立复核原39列及正式52列，映射字段差异0 |
| 项目相关方与状态 | 任务2109968219214897153；运行2109970064863846401 | SUCCESS，读取65，新增10个项目扩展、更新55条正式相关方 |
| 原NULL审计纠正 | 同任务；运行2109974846852771841 | SUCCESS，仅23条更新人恢复原NULL，其余42条不变；完整原字段差异0，46个NULL创建人和23个NULL更新人均按原值承接 |
| 旧01迁移绑定清理 | 任务2109968221270106114；运行2109969877659475970 | SUCCESS，整表读取361032，DELETED=236259，SKIPPED=124773；不删除业务数据或原运行回执 |
| 回款重放 | 2109972301237379074 | PREVIEW_READY，UNCHANGED=10 |
| 最终项目事实重放 | 2109975267130421250 | PREVIEW_READY，UNCHANGED=65 |

旧订单任务最终保留142717条有效绑定：001为123160、002为19018、原02为8、003为531；缺失正式目标为0。原02的8条具有正式目标，按本轮限定范围保持，不归入旧01清理。

NULL审计首次运行仍被MyBatis实体填充覆盖，重放如实暴露23条重复更新。已将显式写入参数改为既有Mapper的场景化命令，独立传递原审计值；在启用实体自动填充的真实MySQL临时表中验证新增/更新均保持NULL。原失败/中间运行保留，不改旧回执；通过同任务原绑定和完整前值保护纠正，最终重放全部UNCHANGED。

后端本轮45项不同定向检查通过，含金额/比例精度、全52列真实MySQL读写、租户隔离、有效合同关系展示、已有目标阻断删除、原任务运行时阻断清理、空集合保护、项目读写权限/版本锁和实际自动填充行为。相关方审计最后修正的12项受影响检查及最终完整包均通过。前端两组件SFC编译和四文件ESLint通过。

GPT-6.1-sol轻度代理在19082真实浏览器完成正常项目52564验收：角色20/30出货渠道及下单代理商显示，与正式响应一致；合同31020230328A02的六值504960/504960/504960/100/0/0一致；发货-1、执行90及两更新时间一致。三个任务可见，配置中SQL和参数可查看；检查仅取消，不保存或执行。控制台错误和失败请求为0。清理任务执行期间配置禁用，结束后恢复并已打开验证。

公开文档不含凭据、许可码、令牌或原始密钥。逐行证据、预览、受保护基线和浏览器截图保存在既有私有B：
`remaining-receivable-all-fields-verification-20261013.json`、
`remaining-project-facts-all-fields-verification-20261013.json`、
`remaining-old01-post-check-20261013.json`、
`party-payment-facts-browser-20261013-evidence.json`、
`party-payment-facts-browser-20261013-task-configs.json`。

本轮新增业务表/物理列均为0。V461只放宽两个已批准既有审计字段；68条结构变更前现存行未变化。沿本任务既有隔离方式应用DDL，不改已存在Flyway回执；此前15处历史校验差异及449～460基线整理仍未解决，不能据本地验收宣称生产迁移基线已就绪。整表SQL与任务能力完成，本轮项目关联实际数据验证仍限定原十项目。
