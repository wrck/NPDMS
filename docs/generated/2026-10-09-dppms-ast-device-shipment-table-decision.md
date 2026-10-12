# G003：ast_device_shipment 整表裁决

已核对目标46列（39个既有字段、7个本任务新增字段）、原条码表19列、原装箱单8列、原发货视图11列及产品目录4列。用户已裁决按建议1、2、3处理：直接使用既有实体字段，不新增列。7列新增SQL和持久化代码已撤回，实际数据库未清理。

视图定义已证实：fb_shipment_barcode.item/item2分别按产品编码关联fb_items.item；itemModel/itemModel2来自itemname，itemName/itemName2来自describe_。这些是联表目录属性，不能声称为独立记录的发货时名称。

建议本表不新增列，撤回以下7列；必须先在正式业务字段/产品目录中完成承接、核对全表已有值和依赖，再清理重复列。产品目录来源名称及型号的具体物理结构尚未裁决，不能先删除名称数据。装箱单其余新增列仍属于G008，不能由本表确认自动批准。

全表新增列非NULL统计曾超时，目前结果未知；本清单不以“本批为空”证明全表可删除。

## 本任务新增7列的已批准撤回方案

| 新增列 | 建议及原因 |
|---|---|
| source_primary_material_id | 建议撤回：fb_items.id只是技术自增ID，用户指定产品按租户+产品编码唯一关联；不建立以该ID为业务键的关联。 |
| source_secondary_material_id | 建议撤回：同上；副产品同样以secondary_item关联目录。 |
| source_product_model | 建议撤回：视图itemModel实际来自fb_items.itemname，应由正式产品目录及已裁决来源属性承接，不复制到发货表。 |
| source_product_name | 建议撤回：视图itemName实际来自fb_items.describe_，同上。 |
| secondary_product_model | 建议撤回：视图itemModel2实际来自副产品fb_items.itemname，关联副产品目录。 |
| secondary_product_name | 建议撤回：视图itemName2实际来自副产品fb_items.describe_，关联副产品目录。 |
| source_updated_time | 建议撤回：原updateTime复用既有source_updated_at；先核全部已有值并补正式字段，不能丢弃非NULL历史值。 |

## 39个既有字段完整复用

| 正式字段 | 来源/处理 |
|---|---|
| id | 目标技术主键；原id仅作来源幂等身份，不覆盖目标主键 |
| device_sn | fb_shipment_barcode.barcode，主设备序列号 |
| shipment_time | fb_shipment.packdate，原发货时间 |
| package_no | fb_shipment_barcode.pack_id / fb_shipment.packlist_id，装箱单号 |
| contract_no | fb_contract.contract_code，经fb_shipment.con_id精确关联 |
| event_type | 沿既有同步定义；有rma_no时UNCLASSIFIED，否则SHIPMENT，不虚构已确认RMA替换 |
| warranty_start_date | fb_shipment_barcode.warrantyStartDate，条码级保修开始日期 |
| warranty_months | fb_shipment_barcode.warrantyMonth，原保修月数；负数不能写unsigned，遇到时阻断裁决 |
| rma_no | fb_shipment_barcode.rma_no，原RMA单号 |
| related_device_sn | 既有已确认设备关系字段；rmaBarcode写rma_related_sn，不能据来源候选伪造已确认替换关系 |
| source_system | DPPMS，正式记录来源标识 |
| source_key | fb_shipment_barcode.id，既有来源幂等身份 |
| source_version | 既有同步版本，不能混用业务时间 |
| source_updated_at | fb_shipment_barcode.updateTime，唯一正式来源更新时间承接点 |
| synced_at | 既有本平台同步时钟，原syncTime另落sync_time |
| sync_status | 既有同步结果；异常不可声明SUCCESS |
| version | 既有乐观锁版本，不写旧ID或源时间 |
| creator | 现有写入Owner的审计；原条码表没有创建人字段 |
| create_time | 现有本平台发货记录创建审计；原条码表没有创建时间字段 |
| updater | 现有写入Owner的审计；原条码表没有更新人字段 |
| update_time | 现有本平台记录更新审计；原updateTime另落source_updated_at |
| deleted | 现有逻辑删除，不从rma标志推断删除 |
| tenant_id | 当前已授权迁移租户；不得跨租户关联 |
| legacy_package_key | fb_shipment_barcode.pack_id；保留原装箱单身份，不将解析失败视为承接完成 |
| rma_related_sn | fb_shipment_barcode.rmaBarcode，来源RMA设备序列号候选 |
| rma_marked | 沿既有同步SQL由rma_no是否存在生成，区别于原isRMA标志 |
| order_line_id | 由订单号、行号、产品码和公司关联唯一正式订单行；不唯一登记待关联，原订单/行号仍写正式字段 |
| source_payload | 仅作为既有来源证据；不能代替以下业务字段承接 |
| order_no | fb_shipment_barcode.orderNumber，销售订单号 |
| line_no | fb_shipment_barcode.lineNum，订单行号，整数转字符串 |
| product_code | fb_shipment_barcode.item，主产品编码；租户+编码关联正式产品目录 |
| secondary_sn | fb_shipment_barcode.barcode2，副设备序列号 |
| secondary_item | fb_shipment_barcode.item2，副产品编码；租户+编码关联正式产品目录 |
| internal_serial_no | fb_shipment_barcode.com_barcode，组合设备序列号 |
| is_rma | fb_shipment_barcode.isRMA，保留原RMA标识，不覆盖rma_marked推导值 |
| profit_center | fb_shipment_barcode.profitCenter，利润中心 |
| sole_agent_suffix | fb_shipment_barcode.soleAgentSuffix，总代订单号后缀 |
| sync_time | fb_shipment_barcode.syncTime，原系统同步时间 |
| uuid | fb_shipment_barcode.uuid，原UUID |

## 原表 fb_shipment_barcode（19列）

| 原字段 | 原类型 | 正式承接/排除原因 |
|---|---|---|
| id | bigint(20) | source_key及同步绑定，不复制目标id |
| pack_id | varchar(64) | package_no、legacy_package_key；关联正式装箱单 |
| item | varchar(16) | product_code及主产品目录 |
| barcode | varchar(50) | device_sn |
| com_barcode | varchar(50) | internal_serial_no |
| rma_no | varchar(64) | rma_no |
| isRMA | int(11) | is_rma |
| item2 | varchar(16) | secondary_item及副产品目录 |
| barcode2 | varchar(50) | secondary_sn |
| orderNumber | varchar(32) | order_no及订单关联 |
| lineNum | int(11) | line_no及订单行关联 |
| profitCenter | varchar(32) | profit_center |
| soleAgentSuffix | varchar(32) | sole_agent_suffix |
| warrantyStartDate | date | warranty_start_date |
| warrantyMonth | int(11) | warranty_months |
| rmaBarcode | varchar(50) | rma_related_sn，不据候选自动制造替换关系 |
| updateTime | datetime | source_updated_at |
| syncTime | datetime | sync_time |
| uuid | varchar(64) | uuid |

## 原表 fb_shipment（8列）

| 原字段 | 原类型 | 正式承接/排除原因 |
|---|---|---|
| packlist_id | varchar(64) | 发货package_no/legacy_package_key；正式com_shipment_package.package_no |
| con_id | varchar(64) | 经fb_contract.contract_id精确关联发货contract_no；装箱单合同引用字段属G008另行整表裁决 |
| packdate | datetime | 发货shipment_time及装箱单shipment_time |
| warrantyStartTime | datetime | 正式com_shipment_package.warranty_start_time，不冒充条码级保修开始日期 |
| warrantyEndTime | datetime | 正式com_shipment_package.warranty_end_time |
| receiveName | text | 正式com_shipment_package.receiver_name |
| emsNum | text | 正式com_shipment_package.express_no |
| emsCompany | text | 正式com_shipment_package.carrier_name，精确解码承运商；新增承运商列属于G008待整表裁决，不默认批准 |

## 原表 view_shipment_info_4_pm（11列）

| 原字段 | 原类型 | 正式承接/排除原因 |
|---|---|---|
| contract_code | varchar(25) | 正式contract_no |
| itemCode | varchar(16) | 正式product_code及产品目录 |
| itemModel | varchar(255) | 主产品目录来源型号（fi.itemname） |
| itemName | varchar(255) | 主产品目录来源名称/描述（fi.describe_） |
| barcode | varchar(50) | device_sn |
| packId | varchar(64) | package_no/legacy_package_key |
| itemCode2 | varchar(16) | secondary_item及副产品目录 |
| itemModel2 | varchar(255) | 副产品目录来源型号（fi2.itemname） |
| itemName2 | varchar(255) | 副产品目录来源名称/描述（fi2.describe_） |
| barcode2 | varchar(50) | secondary_sn |
| profitCenter | varchar(32) | profit_center |

## 原表 fb_items（4列）

| 原字段 | 原类型 | 正式承接/排除原因 |
|---|---|---|
| id | int(11) | 用户已说明没有业务意义；不迁入产品业务键，旧技术ID列明排除原因 |
| item | varchar(25) | 正式产品目录product_code，租户+编码唯一关联 |
| describe_ | varchar(255) | 目录完整来源名称/描述；已批准保留来源名称，物理承接字段整表另交用户确认 |
| itemname | varchar(255) | 目录完整来源型号；物理承接字段整表另交用户确认 |

组合设备序列号、副产品编码/序列号、销售订单号/行号、利润中心、保修月数、RMA设备序列号均复用以上既有业务列，不以source_payload或迁移快照替代。迁移中的错误、歧义和未关联事实继续列明，不能擅自丢弃。

## 2026-10-10 已批准方案的实际清理

需求方已批准上述1、2、3项；产品目录来源名称及型号另已批准复用正式实体扩展。本次在任务库 `24306/npdms_domain_test` 完成以下核验和结构纠正：

- 按主键分批、在一致性快照中扫描全部 **4,194,867** 条发货记录，覆盖7个待撤回列，不限于本批10个项目。
- **1,284** 条记录的主产品原名称、型号、来源更新时间非NULL；其名称与型号全部精确存在于同租户、同产品编码的正式产品目录 `dppms_catalog_labels` 属性，更新时间全部等于既有 `source_updated_at`。其余4个重复列全表均NULL。
- 初次只读目录检查误用了 `revision_id IS NULL`；核实PLT正式Owner以 `revision_id=0` 存储当前值后，复用完整扫描结果重新核对全部1,284条，未匹配数为0。原检查证据保留。
- 执行前复核目录版本、原值、任务依赖、索引及数据库依赖，并完整备份有值记录的46列。以 `ALGORITHM=INSTANT` 原子删除7列，未复制或重建大表。
- 实际表由46列恢复为 **39列**；总记录数不变，全部1,284条其余正式字段逐项不变。未写来源库、未改Flyway执行历史。
- 清理前后真实设备发货接口分别核验10个不同产品编码，均返回正式目录及完整DPPMS名称/型号；页面验收单独记录，不以接口检查替代。
- 真实浏览器通过“客户与资产→设备管理”，按来源SN查询、打开详情和“发货事实”；设备02051508D22C000072的DPPMS名称/型号、组合SN、订单SO065355、行10707794及保修月数36均与来源及正式接口一致。页面原名称由正式目录实体扩展返回，不再读取已撤回列。私有页面记录为 `shipment-formal-page-after-cleanup-20261010.txt`。

本项只关闭发货表7列纠正；装箱单、维保、安装及其他关联表的整表裁决和验收分别跟踪。私有核验记录：`reviewed-shipment-mirror-formal-directory-20261010.json`、`verified-shipment-mirror-final-schema-20261010T005009150311.json`、`verified-shipment-formal-label-api-before-cleanup-20261010.json`、`verified-shipment-formal-label-api-after-cleanup-20261010.json`。
