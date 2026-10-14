# 37项生产迁移：完整来源、目标、SQL、映射及处理规则

整理日期：2026-10-14。范围沿用现行生产任务目录，包含刚确认的P12 CRM名称优先及P32历史联系人作为项目成员规则。

**29项已有任务基线：23项专用适配器、6项通用表映射；另8项缺统一全量任务入口。** 专用适配器仍复用统一INT任务运行机制。

## 阅读入口

- [当前37项任务目录与依赖](../../../superpowers/plans/2026-10-14-dppms-production-migration.md)
- [承接过程共同处理规则与通用/专用边界](processing-rules.md)
- [结构化总索引](manifest.json)；每项JSON包括全部当前业务映射、ENUM/LOOKUP、目标步骤、目标字段及索引。
- [来源/目标完整结构](schema.json)：源71个表/视图1,107列，目标43表1,105列（含2张附带表），均为只读元数据。
- [正式Owner实际SQL](owner-sql/README.md)：43份当前Mapper XML副本，保留事务内查询/写入条件；不能直接执行。
- [本轮文档核对结果](verification.json)：列明SQL/配置/字段/链接检查与未执行的业务验证。
- [旧系统原始查询SQL及include索引](../README.md)：保留旧634条查询来源；本包SQL目录按当前任务版本重新提取，不把旧SQL合集冒充最新配置。

每项页面均包含：实际来源表/引用表、完整SQL与参数、查询字段→适配器/通用步骤映射、原表逐字段→正式承接点、全部主要目标列和唯一键、Owner调用路径、逐项处理规则、通用任务取舍及现有缺口。

## 任务逐项入口

| 编号 / 业务任务 | 主要来源 | 目标 | 当前任务实现 | 代码/生产差异 |
| --- | --- | --- | --- | --- |
| [P01 公司与基础部门](tasks/P01.md) | ["ehr_company","ehr_department"] | ["system_company","system_dept"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P02 已批准的FND补充部门](tasks/P02.md) | ["fnd_department"] | ["system_dept"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P03 八个EHR补充部门及主管补关联](tasks/P03.md) | ["ehr_department"] | ["system_dept"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P04 账号与任职资料](tasks/P04.md) | ["t_user","t_user_info","fnd_user_info"] | ["system_users"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P05 角色定义及来源角色绑定](tasks/P05.md) | ["t_role","fnd_roles"] | ["system_role"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P06 用户角色关系](tasks/P06.md) | ["t_user_role","fnd_user_info"] | ["system_user_role"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P07 用户公司与部门范围](tasks/P07.md) | ["t_user","t_user_info","fnd_user_info","t_user_role","fnd_user_power"] | ["system_user_company_department_scope"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P08 基础字典类型](tasks/P08.md) | ["fnd_basic_data_type"] | ["system_dict_type"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P09 基础字典项](tasks/P09.md) | ["fnd_basic_data"] | ["system_dict_data"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P10 CRM客户主档](tasks/P10.md) | ["Account"] | ["cus_customer_master"] | 专用适配器 | 当前preview未做CUS Owner等价目标验证 |
| [P11 CRM正式产品目录](tasks/P11.md) | ["DPtech_v_product_info_single"] | ["ast_product_official_info"] | 当前通用TABLE_MAPPING | 已有通用映射；生产正式Owner接入尚待完成 |
| [P12 DPPMS缺失产品补齐与编码关联](tasks/P12.md) | ["fb_items"] | ["ast_product_official_info"] | 专用适配器 | 移除产品扩展依赖待实现 |
| [P13 CRM执行单](tasks/P13.md) | ["pm_project_property_from_sms"] | ["com_crm_execution_order"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P14 销售订单与订单行](tasks/P14.md) | ["pm_order_data_from_erp","pm_order_line_from_erp"] | ["com_sales_order","com_sales_order_line"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P15 正式合同主档](tasks/P15.md) | ["pm_order_data_from_erp","sms_ofst_contract_head_sap"] | ["com_contract"] | 当前通用TABLE_MAPPING | 已有通用映射；生产正式Owner接入尚待完成 |
| [P16 规范项目主档与基础最终客户资料](tasks/P16.md) | ["pm_project"] | ["proj_project","proj_project_party"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P17 全部原项目身份桥接](tasks/P17.md) | ["pm_project"] | ["int_sync_binding"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P18 项目合同关系及正式商务关联](tasks/P18.md) | ["pm_project_contract","pm_project_group","pm_project_group_relationship"] | ["com_project_contract_relation"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P19 项目相关方与发货/执行状态资料](tasks/P19.md) | ["pm_project_related_party","pm_project_state"] | ["proj_project_party","plt_entity_extension_value"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P20 项目真实产品](tasks/P20.md) | ["pm_project_product_line_real"] | ["proj_project_real_product"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P21 项目租赁产品与产品配置](tasks/P21.md) | ["pm_project_product_lease_line_from_crm","pm_project_product_config_level_info_from_crm"] | ["proj_project_lease_product","proj_project_product_configuration"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P22 项目产品实施范围](tasks/P22.md) | ["pm_project_product_line"] | ["com_delivery_scope","com_delivery_scope_detail"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P23 合同回款计划](tasks/P23.md) | ["pm_pb_plan_from_sms"] | ["com_contract_collection_plan"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P24 合同实际回款信息](tasks/P24.md) | ["sms_ofst_contract_head_sap"] | ["com_contract_receivable"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P25 设备逐次续保记录](tasks/P25.md) | ["fb_service"] | ["ast_device_warranty_renewal_record"] | 专用适配器 | 按现行任务条目补齐并重验 |
| [P26 发货合同引用与保修年数](tasks/P26.md) | ["fb_contract"] | ["com_shipment_contract_reference","com_contract"] | 当前通用TABLE_MAPPING | 已有通用映射；生产正式Owner接入尚待完成 |
| [P27 装箱单与物流](tasks/P27.md) | ["fb_shipment","view_shipment_ems_4_pm"] | ["com_shipment_package"] | 当前通用TABLE_MAPPING | 已有通用映射；生产正式Owner接入尚待完成 |
| [P28 设备发货事实](tasks/P28.md) | ["fb_shipment_barcode"] | ["ast_device_shipment"] | 当前通用TABLE_MAPPING | 已有通用映射；生产正式Owner接入尚待完成 |
| [P29 主SN与附加SN关系](tasks/P29.md) | ["fb_shipment_barcode_relation"] | ["ast_device_relationship"] | 当前通用TABLE_MAPPING | 已有专用DPPMS_DEVICE_RELATIONSHIP，生产优先复用 |
| [P30 设备主档及项目归属衔接](tasks/P30.md) | ["ast_device_shipment","view_shipment_info_4_pm"] | ["ast_device","ast_device_project_relationship"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |
| [P31 项目成员历史与获准授权](tasks/P31.md) | ["pm_project_member"] | ["proj_project_member_assignment"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |
| [P32 历史最终客户联系人作为项目成员](tasks/P32.md) | ["pm_project_member"] | ["proj_project_member_assignment"] | 有Owner，缺全量任务 | 改PROJ成员历史待实现，旧CUS分流不再用于新批次 |
| [P33 普通安装历史及获准当前位置](tasks/P33.md) | ["pm_project_shipment"] | ["imp_eng_installation","ast_device_location","ast_address","ast_device"] | 有Owner，缺全量任务 | 现Owner为既有安装纠正；生产首次创建入口待补 |
| [P34 出厂软件版本记录](tasks/P34.md) | ["fb_soft_version"] | ["ast_device_factory_version_record"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |
| [P35 项目软件变更批次](tasks/P35.md) | ["pm_project_soft_change_logs"] | ["ast_device_software_change_batch"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |
| [P36 设备软件版本历史](tasks/P36.md) | ["pm_project_soft_version"] | ["ast_device_software_version_record","plt_entity_extension_value"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |
| [P37 设备许可历史](tasks/P37.md) | ["pm_project_license_info_from_license"] | ["ast_device_license_record"] | 有Owner，缺全量任务 | 按现行任务条目补齐并重验 |

## 现状与生产规则分开读取

原配置中的SQL、参数和字段转换按版本保留，方便复核“现在怎么做”；最新生产规则写在逐项处理和改造要求中。P12当前SQL仍带扩展定义，另附已确认规则查询；P32现代码仍走CUS专用路径，新目标已改成员历史，均没有伪装成已实现。

当前通用任务也有租户、白名单、唯一键、前值、NULL、关联和分页保护。需要正式Owner是当前业务边界要求，不应简化为“通用任务没有任何保护”或“多表就必须专用”。尚未做生产Owner接入的通用任务仍如实标出。

旧目标恢复/纠正大字典不作为生产业务映射复制。其字段与数量列在每项JSON；业务枚举、18项逐源排除、540项审计原码等完整保留。来源/目标元数据与SQL不包含密码、许可码或连接凭据。

## 验证边界

本轮验证覆盖37项唯一编号、29项原任务基线/8项缺口、SQL与所引配置对象对应、完整业务映射字典、主要原表逐字段列示、全部主要目标字段/索引及代码/文档链接；同时校正P12/P32和旧EHR统计格等明确过时映射。元数据只读，不查询或输出业务秘密。

未执行迁移业务SQL、业务预览、迁入、DDL或服务操作；未修改应用代码。CRM全表类型及生产库基线仍需对应环境核实。本包可用于评审/补任务，不能直接提交为可执行配置或作为写入授权。
