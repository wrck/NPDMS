# 实际来源值的相等分组变化

仅输出聚合数量，不导出姓名、工号、联系方式等业务原值。组数减少不等于删除了记录，表示不同原值在目标规则下相等。

| 来源表 | 字段 | 原规则等价组数 | unicode_ci等价组数 |
|---|---|---:|---:|
| fb_contract | customer_name | 10919 | 10917 |
| fb_contract | project_name | 88347 | 88336 |
| fb_contract | remark | 23825 | 23821 |
| fb_items | describe_ | 28314 | 28305 |
| fb_items | itemname | 7918 | 7913 |
| pm_order_data_from_erp | customerName | 3669 | 3667 |
| pm_order_data_from_erp | projectName | 74441 | 74435 |
| pm_order_data_from_erp | orderComment | 22802 | 22791 |
| pm_order_line_from_erp | itemDesc | 5140 | 5132 |
| pm_project | projectName | 74145 | 74141 |
| pm_project | column003 | 3665 | 3663 |
| pm_project | column008 | 944 | 942 |
| pm_project | column013 | 21347 | 21343 |
| pm_project | column014 | 1245 | 1244 |
| pm_project | customerProjectName | 4874 | 4873 |
| pm_project_member | memberName | 16064 | 16060 |
| pm_project_member | phoneNum | 16843 | 16822 |
| pm_project_member | email | 2614 | 2613 |
| pm_project_product_line | itemName | 5129 | 5121 |
| pm_project_product_line_real | productSubName | 666 | 653 |
| pm_project_property_from_sms | projectName | 72834 | 72833 |
| pm_project_property_from_sms | channelName | 5165 | 5162 |
| pm_project_property_from_sms | customerProjectName | 5052 | 5051 |
| pm_project_property_from_sms | finalCustomerName | 19585 | 19582 |
| pm_project_property_from_sms | agentName | 8014 | 8010 |
| pm_project_property_from_sms | linkmanTel | 8928 | 8926 |
| pm_project_real_product_line_from_sms | productSubName | 678 | 663 |
| pm_project_related_party | partyName | 9270 | 9263 |
| pm_project_soft_version | conp | 4248 | 4247 |
| sms_ofst_contract_head_sap | project_name | 73416 | 73412 |
| sms_ofst_contract_head_sap | client_supplier_name | 3637 | 3635 |
| t_user | user_name | 351 | 350 |
| t_user | userCustom3 | 351 | 350 |
| t_user_info | workNo | 352 | 351 |
| t_user_info | realName | 340 | 339 |

## 拆分聚合补查

原查询超时的11列中，4列补查完成：合同号和RMA号相等分组保持；下列2列也发生相等分组变化。另7列仍超时。未提高原库单条查询5秒上限。

| 来源表 | 字段 | 原规则等价组数 | unicode_ci等价组数 |
|---|---|---:|---:|
| pm_project_shipment | itemName | 3211 | 3202 |
| pm_project_shipment | installAddress | 13792 | 13791 |
