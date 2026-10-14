# 旧系统迁移表与正式业务表承接清单

**2026-10-14生产任务明细入口：**[37项完整来源、目标、SQL、字段映射及承接规则](production-tasks/README.md)，含当前任务版本、专用/通用适配器取舍、Owner实际SQL及实现缺口。新批次按最新P12“同编码CRM名称优先、不写产品扩展”和P32“历史联系人迁项目成员历史”执行；下文及旧附件保留其原日期、原执行范围和历史结果，不能据此恢复已替代的写入路径。

整理日期：2026-10-13。范围是本次DPPMS项目历史迁移涉及的全部原表/视图及用户、角色、部门引用，不是整个旧库所有无关表。共 **67个来源表/视图、1031个原字段**；每个原字段均列示承接点或没有承接的原因。

包含原52表/视图审查清单，补充原始pm_project、项目组、用户角色/范围、部门及EHR身份引用，另纳入39列SMS合同头来源。pm_project_header与pm_project分别列示：前者是旧用服视图，正式整表项目迁移必须用后者。EHR账号/员工仅用于身份核实，未把全部EHR任职属性算作迁入完成。

映射基线采用已提交代码 **e21f02d2d**和最新用户裁决。实际结果覆盖2026-10-09至12成功证据，并补入[2026-10-13续办进展](../2026-10-13-dppms-migration-progress.md)及其原回执。今天已读取补充来源结构；目标MySQL在读取运行记录时断连，后续24306拒绝连接，所以本报告**不声称2026-10-13实时核对目标记录通过**。当前未提交的新代码、仅预演候选及未经批准范围的数据变化不提升为业务完成。

**范围异常单列：**续办记录确认6570个新项目主档创建未经用户批准，已停止；不能将其及依赖扩大范围的合同/计划迁入记为本次批准完成。本报告只列已经发生的数据变化及原回执，不批准继续、删除或回滚。

## 阅读入口

最新实际续办：[续保及多合同十项目验证](../2026-10-13-dppms-renewal-multi-contract-validation.md)已经完成，新增222条续保及22条合同关系等正式数据，6,026项字段比较无差异。`fb_service`的逐字段文档和结构化清单已更新为223条批准范围实绩（含此前单条）；下方早期复核叙述仍保留其时间边界。

- [当前只读复核](current-state-review.json)：逐项核对67表、1,031字段、41个已保存任务，补入本次实际来源/目标表与较新回执。原逐字段表保留其基线，不能继续把其旧状态当成当前完成状态。
- [续查结果](continued-review.md)：补入全部字段结构、相关方审计与正常页面遗漏、用服工程事件含义、串货双向核对，以及236259处缺失订单行目标的实时复核。

本次确认仍有原过程状态、原任务/里程碑、相关方完整角色及历史审计、SMS合同回款信息、续保/保修变更事实的承接缺口，另有30条安装串货关系的5字段和账号isSysUser属性未完整承接。相关方已有55条、SMS部分属性已复用合同，不能误报为全部没有承接；warranty_info当前来源0，不构成实际记录漏迁。来源数量是整表总量，不自动扩大本轮用服项目写入范围。

订单行已有较新142697条未变化重放回执，合同正式归并和产品附表的整表任务已接入；这几项不再按旧文字重复实现，也不据此宣称所有历史数据已完成。ehr_department有18处目标单元格误写为非空计数，应订正清单格式，已获不迁入裁决的字段不作为新业务遗漏。复核仅增加报告，未执行迁移、重放、结构变更或业务写入。

- [全部原字段逐项映射](field-mapping.md)：按原表列全字段、类型、正式承接点、转换和未承接原因。
- [结构化清单](table-field-mapping.json)：与逐字段文档一致，方便后续逐表执行/核对。
- [旧系统原始查询SQL](sql/legacy-original-queries.sql)及[语句索引](sql/legacy-query-index.json)：634条SELECT，标原文件、原语句ID、行号和涉及表。
- [原查询引用片段](sql/legacy-query-fragments.sql)：保留原include所需SQL片段。
- [原视图定义/关联SQL](sql/legacy-view-definitions.sql)：保留既有SHOW CREATE VIEW材料，补足视图背后的原表关系；仅供查阅，不执行DDL。
- [实际迁移来源SQL](sql/migration-source-queries.sql)及[任务索引](sql/migration-query-index.json)：37段已保存配置查询，加4份已提交整表模板。
- [原表逐列审查SELECT](sql/raw-table-audit-selects.sql)：补全字段审查入口，单独标为审查语句，不能替代旧查询或正式任务。

- [既有材料采集查询](sql/legacy-material-capture-queries.sql)：补齐安装、软件、许可等实际项目迁移入口使用的来源条件；参数表示改为命名集合，其余保留原采集规则。它不是已完成整表迁移任务的证明。

旧查询保留原动态标签、#参数#、#{参数}、include和原条件，不能当作去掉条件后的可直接执行整包。实际任务SQL保留其原验证范围，含试点限制的语句不会伪装成已经实现/执行全历史。普通审查SELECT排除旧password与明文licenseCode；许可仅通过正式受控加密采集。没有执行这些附件SQL。

旧SQL读取D:/IDEAWorkspace/PMS和历次审查引用的D:/EclipseWorkspace/Parctice/PMS：同语句ID且原文完全相同只留一份；同ID但不同查询原文分别保留真实路径。不把两个代码副本混成同一个文件版本。

## 已完成实际承接清单

下列“完成”仅对应列出的真实批准范围。结构/接口可用、本批0条、来源证据留存不等于非空数据已迁入；最后的全表状态表另列未完成项。

| 已完成内容 | 原始表 | 新系统正式表 | 实际核验范围 |
|---|---|---|---|
| 账号资料 | t_user、t_user_info、fnd_user_info | system_users | 1176账号，employee_no/custom_info及审计；限定557处更新人纠正；其余字段缺口见逐项表 |
| 角色定义 | t_role、fnd_roles | system_role | 31来源绑定＝25新角色＋6复用；停用排除；13角色25处审计纠正 |
| 用户角色关系 | t_user_role、fnd_user_info.roleIds | system_user_role | 1932来源关系→1927新增正式对；空公司不授公司范围 |
| 公司部门范围 | fnd_user_power、t_user_info、t_user_role | system_user_company_department_scope | 9492绑定→9477新增范围；001/002跨公司；仅按项目查看授权 |
| 缺失部门补齐 | fnd_department、ehr_department | system_dept | 28项＋8项；16列不变，不使用额外扩展承接缺项 |
| 字典类型 | fnd_basic_data_type、fnd_basic_data | system_dict_type | 31原定义＋49缺失分组＝80；重放80未变化 |
| 字典项 | fnd_basic_data | system_dict_data | 531新增，原ID114明确排除；重放531未变化 |
| 项目基本资料/最终用户 | pm_project（旧材料来自pm_project_header） | proj_project、proj_project_party | 10项目；原编码、类型、历史状态、审计和原JSON；不代表80,170主档已迁 |
| 项目合同关系 | pm_project_contract、pm_project_group_relationship、pm_project_group | com_project_contract_relation | 10正式关系/审计纠正及重放；跨组通用归并仍未完成 |
| 订单头 | pm_order_data_from_erp | com_sales_order | 2026-10-13：91161来源/91060已有正式订单字段承接及重放；411冲突另列；新建/删除0 |
| 订单行 | pm_order_line_from_erp | com_sales_order_line | 原105试点已承接；2026-10-13全历史只有预演候选，尚未正式写入 |
| CRM执行单 | pm_project_property_from_sms | com_crm_execution_order | 2026-10-13：102520来源→83462既有正式执行单；更新/重放及当前保护字段核验 |
| 合同回款计划 | pm_pb_plan_from_sms | com_contract_collection_plan | 10计划，参数化查询和重放；不使用com_contract_receivable代替 |
| 项目产品→实施范围 | pm_project_product_line | com_delivery_scope、com_delivery_scope_detail，关联com_sales_order_line | 105范围＋105产品明细；false不占当前订单分配数量 |
| 发货事实 | fb_shipment_barcode，view_shipment_info_4_pm | ast_device_shipment，正式产品目录 | 1284有值本批字段核对；4,194,867全表7个重复列核查/清理，二者验证范围分开 |
| 产品目录 | fb_items | ast_product_official_info、plt_entity_extension_value | 原104编码（补5）；2026-10-13扩至8418已有正式目录来源标签，新增0；26558缺目录待规则 |
| 装箱单及物流 | fb_shipment，view_shipment_ems_4_pm | com_shipment_package | 19装箱单8原字段；carrier_code和carrier_name分别保存 |
| 发货合同引用/保修年数 | fb_contract | com_shipment_contract_reference | 120638引用29字段保护及年数纠正；1空业务身份行仍待处理 |
| 主附设备关系 | fb_shipment_barcode_relation | ast_device_relationship | 65046关系8原/19正式字段，日期限定纠正/重放；360自关联获准排除 |
| 出厂软件版本 | fb_soft_version | ast_device_factory_version_record；冲突保护既有主档 | 19正式记录；全历史重复SN冲突不覆盖主档 |
| 安装及当前位置 | pm_project_shipment | imp_eng_installation、ast_device_location、ast_address | 948安装历史，保20较新、补928空位置；全历史串货尚未承接 |
| 软件版本历史/批次 | pm_project_soft_version、pm_project_soft_change_logs | ast_device_software_version_record、ast_device_software_change_batch、正式实体扩展 | 659记录＋5272旧八项属性＋6批次；一次性迁入；635错误旧事件限定撤回 |
| 许可历史 | pm_project_license_info_from_license | ast_device_license_record | 153条16列加密承接，受控内存比对/重放/页面通过；475468全历史未迁 |
| 项目人员及最终客户联系人 | pm_project_member | proj_project_member_assignment、cus_project_customer_contact_relation | 61人员（23确认授权/38历史）＋9联系人；6管理员旧区间保留 |

## 所有来源表的承接状态

| 来源表/视图 | 原字段数 | 正式业务表 | 当前结果及边界 | 状态 |
|---|---|---|---|---|
| ehr_department | 24 | system_dept | 8个补齐项中5项以EHR为主，另3项以fnd为主；完整24列裁决；16列不变 | 已实际承接（批准范围） |
| ehr_employee | 31 | system_users | 作为真实姓名、工号及账号身份外键核对；不表示EHR全部员工/任职属性已独立迁入 | 引用核对；不独立迁移整表 |
| ehr_login | 7 | system_users | 作为真实姓名、工号及账号身份外键核对；不表示EHR全部员工/任职属性已独立迁入 | 引用核对；不独立迁移整表 |
| fb_contract | 12 | com_shipment_contract_reference | 120638条引用及年数纠正核对；原额外1条无业务身份尚待处理，不记为整表无缺项完成 | 已实际承接（批准范围） |
| fb_items | 4 | ast_product_official_info、plt_entity_extension_value | 2026-10-13已有8418正式产品原标签承接，8314更新/104未变化，重放8418未变化；原21列保护、新增0；另26558缺目录未创建。 | 已完成可核实原记录承接；缺项另列 |
| fb_service | 10 | 尚无完整已确认承接主体 | 本批来源0；维保计算结果不独立同步不等于原权利/登记/变更事实获准丢弃；原附加原型不是已批准承接 | 未完成正式承接 |
| fb_shipment | 8 | com_shipment_package | 19装箱单全部8原字段核对及重放；最终20列含carrier_code；物流名称/编码分别保存 | 已承接10项目；全历史未执行 |
| fb_shipment_barcode | 19 | ast_device_shipment | 1284本批有值行正式字段核对；全表4,194,867行7个镜像列扫描/清理完成，不等于全历史逐业务字段验收 | 已承接10项目；全历史未执行 |
| fb_shipment_barcode_relation | 8 | ast_device_relationship | 65406来源→65046正式关系＋360已批准自关联排除；限定日期纠正、重放与原8/正式19列核对完成 | 已实际承接（批准范围） |
| fb_soft_version | 5 | ast_device_factory_version_record、ast_device_factory_version | 19正式版本记录，主档冲突不覆盖；全历史重复SN的完整保留规则已批准 | 已承接10项目；全历史未执行 |
| fnd_act_hi_comment | 12 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| fnd_basic_data | 12 | system_dict_data | 532来源→531新增、1无业务项排除；重放531未变化；既有14列不变 | 已实际承接（批准范围） |
| fnd_basic_data_type | 10 | system_dict_type | 31原定义＋49缺失分组=80；重放UNCHANGED80；既有11列不变 | 已实际承接（批准范围） |
| fnd_department | 9 | system_dept | 原28＋后续8中的3项补齐；2026-10-13另补163130一项并重放；补部门不代表授组织范围。 | 已实际承接（批准范围） |
| fnd_roles | 11 | system_role | 31条来源角色绑定，25新角色＋6复用；停用不迁；13角色25处NULL审计已纠正 | 已实际承接（批准范围） |
| fnd_user_info | 18 | system_users、system_user_company_department_scope、system_user_role | 合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行 | 已实际承接（批准范围） |
| fnd_user_power | 10 | system_user_company_department_scope | 9492范围绑定→9477新增正式范围；空范围、指定账号及范围排除按裁决；不直接赋办事处全部项目查看权 | 已实际承接（批准范围） |
| pm_cl_callback | 13 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_callback_quesnaire | 12 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_quesnaire_result_header | 10 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_quesnaire_result_line | 13 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_quesnaire_template_header | 14 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_quesnaire_template_line | 15 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_cl_quesnaire_template_options | 13 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_order_data_from_erp | 16 | com_sales_order | 2026-10-13已有正式订单字段承接91161来源，91060正式订单改变且无新建/删除；重放91161未变化，411冲突来源保持待裁决。 | 已完成可核实原记录承接；缺项另列 |
| pm_order_line_from_erp | 16 | com_sales_order_line | 截至2026-10-12试点105行已承接；2026-10-13整表预演仍未正式写入：旧目标缺失236259、冲突/父单缺失等待处理，候选数不算迁入数。 | 已承接10项目；全历史未执行 |
| pm_pb_plan_from_sms | 16 | com_contract_collection_plan | 2026-10-13续办正式计划5330＝原10＋4353＋967，重放5330未变化；30171缺审计账号待裁决；扩大范围相关写入待核定。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project | 37 | proj_project、proj_project_party | 2026-10-13续办实际新增6570主档（不在已确认迁入范围，已停止待处置）；加原10共6580规范主档，仍非80170全量。不能计作批准完成。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project_contract | 7 | com_project_contract_relation | 2026-10-13续办6480来源已重放，新增6462正式关系/6470绑定；依赖扩大项目范围的写入待核定处置，不计已批准全历史完成。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project_group | 8 | com_project_contract_relation | 只作类型/关联展开，不建组实体；续办扩大关系写入的范围问题仍待处理，原5属性不迁裁决保持。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project_group_relationship | 9 | com_project_contract_relation | 原合同桥沿正式关系承接；续办已扩至6480来源，smsProjectCode正式链全源73644匹配，其他缺失/多义仍阻断；扩大范围待核定。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project_header | 37 | proj_project、proj_project_party | 同pm_project：原10项目已验收；续办6570新增主档范围未经批准，待处置。后续整表源仍必须pm_project。 | 原试点完成；续办扩大写入待范围处置 |
| pm_project_incident_table_from_itr | 37 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_instruction | 11 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_license_info_from_license | 8 | ast_device_license_record | 153条16列认证加密承接、解密比对/重放/非秘密页面通过；475468全历史未迁 | 已承接10项目；全历史未执行 |
| pm_project_maintenance | 44 | 尚无完整已确认承接主体 | 用户明确为维护记录而非维保；本轮暂不迁入，仅保留裁决和迁移方案 | 按裁决暂缓 |
| pm_project_member | 15 | proj_project_member_assignment、cus_project_customer_contact_relation | 70原成员→61正式人员（23已确认授权、38未确认历史）＋9最终客户联系人；管理员6区间保留历史 | 已承接10项目；全历史未执行 |
| pm_project_notification | 6 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_product_config_level_info_from_crm | 11 | proj_project_product_configuration | 19列已实现，10项目0条；完整历史层级及缺父节点原样保留规则，不补造节点 | 已实现；试点原数据0条 |
| pm_project_product_lease_line_from_crm | 11 | proj_project_lease_product | 19列已实现，10项目0条；454条全历史未迁入；productCategory/productType依裁决 | 已实现；试点原数据0条 |
| pm_project_product_line | 11 | com_delivery_scope、com_delivery_scope_detail、com_sales_order_line | 105历史实施范围＋105产品数量明细；allocation_confirmed=false；全历史0数量及重复行尚需统一处理 | 已承接10项目；全历史未执行 |
| pm_project_product_line_real | 12 | proj_project_real_product | 19列正式实体/Owner已实现，10项目0条；全历史7848项目清单/9992上游行未实际迁入 | 已实现；试点原数据0条 |
| pm_project_property_from_sms | 32 | com_crm_execution_order | 2026-10-13全源102520原行映射83462既有正式执行单，102411更新/109未变化，重放102520未变化；主项目/状态及保护字段保持。 | 已完成可核实原记录承接；缺项另列 |
| pm_project_real_product_line_from_sms | 10 | proj_project_real_product | 19列正式实体/Owner已实现，10项目0条；全历史7848项目清单/9992上游行未实际迁入 | 已实现；试点原数据0条 |
| pm_project_related_party | 11 | 尚无完整已确认承接主体 | 整表partyRole/身份/区间尚未落实；主档column013最终客户及角色60联系人已承接，不代表此独立原表全部承接 | 未完成正式承接 |
| pm_project_shipment | 21 | imp_eng_installation、ast_device_location、ast_address、ast_device | 948正式安装历史：有地址=已安装，创建时间作安装/生效时间；保20较新位置、补928空位置；串货全历史关系未完成 | 已承接10项目；全历史未执行 |
| pm_project_soft_change_logs | 9 | ast_device_software_change_batch | 6条11列正式批次；原审计和NULL处理核对，旧标记不生成升级完成 | 已承接10项目；全历史未执行 |
| pm_project_soft_version | 28 | ast_device_software_version_record、plt_entity_extension_value | 659条31列正式记录＋5272旧Bak/Change属性；一次性迁移，后续在正常软件维护业务执行 | 已承接10项目；全历史未执行 |
| pm_project_state | 9 | 尚无完整已确认承接主体 | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 未完成正式承接 |
| pm_project_supervision | 17 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_task | 28 | 尚无完整已确认承接主体 | 已批准启动整表规划；现有阶段/任务缺项及历史状态规则未完整裁决实施 | 未完成正式承接 |
| pm_project_weekly | 14 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_weekly_content | 9 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_project_weekly_feedback | 5 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_subcontract_project_header | 28 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_subcontract_project_payment | 13 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| pm_subcontract_project_price | 13 | 尚无完整已确认承接主体 | 额外历史领域未获本轮启动裁决；所有原字段列示，来源证据不计为正式承接 | 按裁决暂缓 |
| sms_ofst_contract_head_sap | 39 | 尚无完整已确认承接主体 | 39原字段已纳入审查；合同头修订领域已选，但完整正式业务字段规划/迁入未完成 | 未完成正式承接 |
| t_role | 11 | system_role | 31条来源角色绑定，25新角色＋6复用；停用不迁；13角色25处NULL审计已纠正 | 已实际承接（批准范围） |
| t_user | 16 | system_users、system_user_company_department_scope、system_user_role | 合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行 | 已实际承接（批准范围） |
| t_user_info | 26 | system_users、system_user_company_department_scope、system_user_role | 合并1176账号；仅首字母＋工号、*_ss、*_supp；1527绑定含特殊审计引用；557处限定审计纠正已执行 | 已实际承接（批准范围） |
| t_user_role | 4 | system_user_role | 1932来源关系→1927新增正式用户角色对；5条空公司关系不新增公司授权 | 已实际承接（批准范围） |
| view_shipment_ems_4_pm | 6 | com_shipment_package | 19装箱单全部8原字段核对及重放；最终20列含carrier_code；物流名称/编码分别保存 | 已承接10项目；全历史未执行 |
| view_shipment_info_4_pm | 11 | ast_device_shipment、ast_product_official_info、plt_entity_extension_value | 不另复制视图；本批1284行通过正式发货、目录及目录标签承接 | 已承接10项目；全历史未执行 |
| view_warranty | 36 | ast_device_warranty_record | 149条现存记录/结构暂保留（118此前派生＋31原历史）；维保计算结果不独立同步；当前原view_warranty来源缺失不得自动替换 | 现存保留；不独立同步 |
| warranty_change_logs | 8 | 尚无完整已确认承接主体 | 本批来源0；维保计算结果不独立同步不等于原权利/登记/变更事实获准丢弃；原附加原型不是已批准承接 | 未完成正式承接 |
| warranty_info | 15 | 尚无完整已确认承接主体 | 本批来源0；维保计算结果不独立同步不等于原权利/登记/变更事实获准丢弃；原附加原型不是已批准承接 | 未完成正式承接 |

## 必须明确保留的缺口

1. 合同关系：最新当前来源优先、最早创建/最近更新规则及跨组归并已批准；续办实现/测试及6480来源实际结果单独记录，仍不代表全历史完成。smsProjectCode链73644匹配，另4577原码缺失、4708缺正式合同、284链未匹配、241多义，完整链未闭合。
2. 真实产品、租赁产品、配置层级：正式实体及原字段映射已实现，但10项目均0条；非空全历史数据未迁。真实产品上游没有形成项目清单的资料不能套用已关联清单承接结论。
3. 安装：本批串货属性为空/-1；全历史非空转出/接收项目合同关系及超长/空地址尚未完成承接，不能忽略。
4. 用户：isSysUser原标记已读取，但已提交custom_info拼装没有同名或accountType承接，未证明已保存；userCustom3/4经人员目录引用核对的结果不等于原属性全值都已经写入。逐字段明确这类边界，不再猜一个不存在的JSON属性。
5. 原项目状态/任务、相关方、SMS合同头修订仍未完整实施。回访/问卷/答案/审批/监理/周报/通知/指导/ITR/分包付款报价等额外历史领域按用户选择暂缓，不称快照为业务承接。维护记录明确本轮不迁。
6. 维保：它是发货/期限计算结果，不独立同步；既有149记录及结构暂保留。fb_service、warranty_info、warranty_change_logs的原服务权利/登记/变更并不因此自动获得丢弃批准。旧view_warranty36列契约当前连接缺失，按用户裁决保留原材料，不自动换成31列视图。
7. 生产Flyway历史定义清理及基线对齐尚未通过；本清单不宣称生产上线就绪或全历史完成。

## 通用转换和关联规则

- 公司：原业务码01→001、02→002、03→003、04→004；未知非空码阻断。旧公司ID先经原目录转业务码，再精确正式ID，不按同数值或部门猜公司。
- 项目：使用pm_project，按类型＋原编码取最大projectId；子表原身份归规范主档。实际迁用服类型10；跨类型同码设计按规范ID升序、后续尾号递增，重放复用既有绑定。
- 合同/订单/执行单：正式关系由Owner按租户、公司、业务键和真实引用核实；旧组仅解释原关联，不生成新合同组/项目组合。
- 审计：平台一般保存登录user_id的字符串，旧用户名/人员编码必须经正式用户绑定转换。NULL不自动填管理员；仅各表已批准例外可直接保存sys、异常数据处理、system、l01663、CrmInterfaceUser、crmapp等原码。NULL更新时间仅按对应表裁决用原创建时间，不能把规则未经批准扩大到所有表。
- 来源原ID一般进入既有迁移/同步绑定，不能在业务表复制source_*镜像；来源材料不是承接实体。已批准正式custom_info和实体扩展是具名业务属性，不能包住所有已存在字段。
- 本轮是只读整理及文档生成，没有提交新任务、修改业务代码、执行数据写入、DDL或服务切换。
# 最新收敛

[剩余业务整表承接与原字段映射](remaining-carry-implementation.md)。该文档记录最新裁决，前面的只读复核保留为发现时的证据，不能将已裁决排除重新列为漏迁。
