# 测试环境历史已保存任务清单

[返回历史补充](README.md)

来源：[existing-sync-task-inventory-20261013.json](C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/existing-sync-task-inventory-20261013.json)；checkedAt=`2026-10-13T14:45:56.126272`，共41项。清单包含基础同步、专项样本、审计纠正和后续整表化配置，**不是41项都在原十项目执行成功**。定义版本可能晚于最初试点，精确早期版本见冻结定义附件。后续新增的相关方、回款、续保任务及另一批十项目，分别在处理逻辑和新增批次表列出。

| 历史任务ID | 历史名称 | 版本 | 适配器 | 来源对象 |
|---|---|---|---|---|
| 2109865270430134273 | DPPMS项目租赁及产品配置（整表，10项目验证） | 0 | DPPMS_PROJECT_ASSOCIATED_PRODUCTS | pm_project_product_lease_line_from_crm, pm_project_product_config_level_info_from_crm |
| 2109865269478027265 | DPPMS项目真实产品清单（整表，10项目验证） | 0 | DPPMS_PROJECT_REAL_PRODUCT | pm_project_product_line_real |
| 2109829769958453249 | DPPMS项目产品实施范围（整表，10项目验证） | 1 | DPPMS_PROJECT_PRODUCT_SCOPE | pm_project_product_line |
| 2109622534918856706 | DPPMS identity-carried 整表迁移验证 | 1 | DPPMS_PROJECT_IDENTITY | pm_project_identity |
| 2109382583085117441 | DPPMS 已裁决65046条关系审计纠正＋360条明确排除 | 2 | DPPMS_DEVICE_RELATIONSHIP_AUDIT_CORRECTION | fb_shipment_barcode_relation |
| 2109371486114320385 | DPPMS 已裁决字典项531条（保持14列） | 0 | DPPMS_DICTIONARY_DATA | fnd_basic_data |
| 2109363204544983041 | DPPMS contract-reference-ready 整表迁移验证 | 5 | DPPMS_PROJECT_CONTRACT_RELATION | pm_project_contract |
| 2109351154645135362 | DPPMS 已裁决基础字典类型（原31＋缺失49） | 0 | DPPMS_DICTIONARY_TYPES | fnd_basic_data_type |
| 2108799116748152833 | 已裁决发货合同空保修年数纠正 | 0 | DPPMS_SHIPMENT_CONTRACT_WARRANTY_CORRECTION | SHIPMENT_CONTRACT |
| 2108608417343934465 | DPPMS master-authorized-valid 整表迁移验证 | 5 | DPPMS_PROJECT_MASTER | pm_project_header |
| 2108574253093683202 | DPPMS collection-approved-actors 整表迁移验证 | 6 | DPPMS_CONTRACT_COLLECTION_PLAN | pm_pb_plan_from_sms |
| 2108569376783273985 | DPPMS 已裁决 user-scopes | 0 | DPPMS_USER_SCOPES | t_user, t_user_info, fnd_user_info, t_user_role, fnd_user_power |
| 2108568095503093761 | DPPMS 已裁决 user-roles | 0 | DPPMS_USER_ROLES | T_USER_ROLE, FND_USER_ROLE |
| 2108567804242235394 | DPPMS 13个角色25处空审计人员纠正（待裁决） | 0 | DPPMS_ROLE_AUDIT_CORRECTION | fnd_roles |
| 2108554583850524673 | DPPMS 已裁决 roles | 0 | DPPMS_ROLES | T_ROLE, FND_ROLE |
| 2108545067259985922 | DPPMS 已裁决557处历史更新人纠正 | 1 | DPPMS_USER_AUDIT_CORRECTION | fnd_user_info |
| 2108538095265562625 | DPPMS 已裁决 ehr-departments | 0 | DPPMS_EHR_DEPARTMENT_COMPLETION | ehr_department |
| 2108534088732168194 | DPPMS 已有正式产品整表原名称型号承接 | 1 | DPPMS_PRODUCT_CATALOG | fb_items |
| 2108524822998773762 | DPPMS 已裁决 users | 0 | DPPMS_USERS | t_user, t_user_info, fnd_user_info |
| 2108524501517955073 | DPPMS department-163130 来源迁移验证 | 1 | DPPMS_DEPARTMENTS | fnd_department |
| 2107046961754501121 | DPPMS 专项异常保留样本（每来源500条） | 1 | TABLE_MAPPING | CONTRACT, SHIPMENT_CONTRACT, PACKAGE, DEVICE_SN, DEVICE_RELATION |
| 2107041701984034818 | DPPMS 专项并发样本 DEVICE_RELATION（2000条） | 0 | TABLE_MAPPING | DEVICE_RELATION |
| 2107041701736570882 | DPPMS 专项并发样本 DEVICE_SN（2000条） | 0 | TABLE_MAPPING | DEVICE_SN |
| 2107041701468135425 | DPPMS 专项并发样本 PACKAGE（2000条） | 0 | TABLE_MAPPING | PACKAGE |
| 2107041701187117058 | DPPMS 专项并发样本 CONTRACT（2000条） | 0 | TABLE_MAPPING | CONTRACT |
| 2107040712333811713 | DPPMS 专项有效主键样本（每来源500条） | 1 | TABLE_MAPPING | CONTRACT, SHIPMENT_CONTRACT, PACKAGE, DEVICE_SN, DEVICE_RELATION |
| 2107037031798468609 | DPPMS 完整同步专项预览（每来源500条） | 1 | TABLE_MAPPING | CONTRACT, SHIPMENT_CONTRACT, PACKAGE, BARCODE, DEVICE_RELATION |
| 2107037031219654657 | DPPMS 合同、装箱单与发货记录完整同步 | 6 | TABLE_MAPPING | CONTRACT, SHIPMENT_CONTRACT, PACKAGE, DEVICE_SN, DEVICE_RELATION |
| 2104524842835423234 | 发货条码原样承接 | 0 | TABLE_MAPPING | SHIPMENT_BARCODE_RAW, SHIPMENT_BARCODE_RELATION_RAW |
| 2102825819916980226 | 设备序列号同步 | 10 | TABLE_MAPPING | DEVICE_SN, DEVICE_RELATION |
| 2102221608959250434 | CRM客户信息同步 | 2 | CRM_CUSTOMER | CUSTOMER |
| 2102221608179109889 | CRM产品信息同步 | 1 | TABLE_MAPPING | PRODUCT |
| 2101891886242299906 | DPPMS 发货合同、主档、装箱单及条码完整同步 | 6 | TABLE_MAPPING | SHIPMENT_CONTRACT, CONTRACT, SHIPMENT_CONTRACT_LINK, PACKAGE, BARCODE |
| 2101875785110306818 | 发货条码同步 | 7 | TABLE_MAPPING | BARCODE |
| 2101875784728625154 | 合同装箱单同步 | 1 | TABLE_MAPPING | PACKAGE |
| 2101875784317583361 | 合同主档生成 | 4 | TABLE_MAPPING | CONTRACT |
| 2101857698189352962 | 发货合同同步 | 5 | TABLE_MAPPING | SHIPMENT_CONTRACT |
| 2101625228492091393 | DPPMS execution-canonical 来源迁移验证 | 8 | DPPMS_CRM_EXECUTION_ORDER | EXECUTION_ORDER |
| 2101613039156846593 | DETAIL_ACCEPTANCE_ISOLATED | 0 | PAYMENT_PLAN_ACCEPTANCE | ACCEPTANCE |
| 2101587859478949890 | DPPMS 订单原字段受保护承接 order-field-valid-efficient | 27 | DPPMS_ERP_ORDER | ORDER, LINE |
| 2101562667583217666 | EHR 公司与部门同步 | 16 | EHR_ORGANIZATION | COMPANY, DEPARTMENT |

SQL入口：[41任务快照查询](saved-task-snapshot-source-queries.sql)；[早期选定冻结版本](first-ten-frozen-source-queries.sql)；[定义/运行元数据](task-definitions-and-runs.json)。参数LOOKUP原值、全目标前值、密码和许可原文均不导出。

