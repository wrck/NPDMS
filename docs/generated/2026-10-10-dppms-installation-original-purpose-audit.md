# 安装地址原业务作用复核

状态：READ_ONLY_AUDIT；本文件不批准新增表、列、状态、审计覆盖或删除。实施范围已按裁决迁入；本项继续独立核对，不修改原库或正式业务表。

旧系统 pm_project_shipment 是项目设备发货关联、安装地址保存和串货转移的共用表。SaveInstallAdress.action 从发货清单选择设备后保存地址；插入时复制发货产品和物流属性，创建时间为记录插入时间。既有行修改地址的 SQL 只更新 installAddress，没有更新任何审计时间。原表没有安装完成时间、安装状态或安装操作人；不能由 packdate、createTime 或 updateTime 推导这些事实。

转移动作同时更新/创建转出与转入项目的设备记录：transferFlag 默认 -1，转出 1，转入 0；chProjectId/chContractNo 指向转出侧，transferProjectId/transferContractNo 指向接收侧。接收合同使用 -C 后缀，并在项目合同关系登记。后续承接须结合这些正式关系，不能仅改设备当前归属替代两侧历史关联。

当前原表21列；imp_eng_installation 当前53列，imp_installation_address_source_fact 当前39列。HEAD安装实体已有安装位置、地址/地点引用、有效期、安装时间/人、状态与审计字段；历史来源标记和镜像属性属于本任务改动，尚须整表裁决，不将“原值已存入来源事实表”作为整改验收。

只读核对10个原项目的直接关联记录共948条；该边界不等于此前按设备及合同汇入的全部历史事实，也不冒充全历史验收。完整原始行受控保存在任务私有证据，本文不列个人地址和人员值。

## 全部原字段

| 原字段 | 类型 | 本次直接关联非空数 | 含义与承接复核点 |
|---|---|---:|---|
| `id` | int(11) | 948 | 原技术ID；迁移来源绑定，不能当新业务主键直接推导身份 |
| `projectId` | int(11) | 948 | 原关联项目；精确项目绑定，不按设备当前位置替换原项目 |
| `barcode` | varchar(25) | 948 | 设备序列号；通过正式设备关联，核对既有 equipment_id |
| `itemCode` | varchar(25) | 948 | 原发货产品编码；复核正式设备/发货及产品目录关系 |
| `itemModel` | varchar(255) | 0 | 当时产品型号；实际表字段须完整核对，旧插入 SQL 未填此列 |
| `itemName` | varchar(255) | 948 | 当时复制的产品名称；核对正式发货事实/目录来源业务属性，不能静默覆盖当前目录 |
| `receiveName` | varchar(255) | 948 | 当时复制的收货人；复核正式物流/装箱单同义字段 |
| `emsNum` | varchar(255) | 948 | 当时复制的运单号；复核正式装箱单关系与 express_no |
| `emsCompany` | varchar(15) | 948 | 当时复制的物流公司值；核对编码/名称实际类型和承运商转换 |
| `packdate` | datetime | 948 | 发货/包装日期；不推导安装完成时间 |
| `contractNo` | varchar(50) | 948 | 该侧项目设备合同；包含串货 -C 合同语义，不能盲目去后缀 |
| `installAddress` | text | 948 | 所保存的安装地址；复核既有 install_location 与正式地址关系 |
| `chProjectId` | int(11) | 0 | 转出项目引用；精确绑定，缺失关联保留待裁决 |
| `chContractNo` | varchar(50) | 0 | 转出合同引用；结合正式合同关系承接 |
| `transferProjectId` | int(11) | 0 | 接收项目引用；精确绑定，缺失关联保留待裁决 |
| `transferContractNo` | varchar(50) | 0 | 接收合同引用；结合 -C 串货合同及正式关系承接 |
| `transferFlag` | varchar(2) | 948 | 默认 -1、转出 1、转入 0；须作为转移业务事实复核，不能生成当前流程完成事实 |
| `createTime` | datetime | 948 | 原记录插入时间；不是安装完成时间或地址最后更改时间 |
| `createBy` | varchar(25) | 948 | 原记录创建操作者；复核已迁入的用户绑定 |
| `updateTime` | datetime | 0 | 实际原列；所查旧地址/转移更新 SQL 均未写本列，不推导动作时间 |
| `updateBy` | varchar(25) | 0 | 实际原列；所查旧地址/转移更新 SQL 均未写本列，不能编造操作者 |

## 当前实体全部字段（结构未裁决）

### imp_eng_installation

| 当前字段 | 类型 | 可空 |
|---|---|---|
| `id` | bigint | NO |
| `project_id` | bigint | NO |
| `code` | varchar(64) | NO |
| `equipment_id` | bigint | NO |
| `install_location` | varchar(255) | YES |
| `address_id` | bigint | YES |
| `address_version` | int unsigned | YES |
| `site_id` | bigint | YES |
| `site_version` | int unsigned | YES |
| `site_location_id` | bigint | YES |
| `site_location_version` | int unsigned | YES |
| `location_resolution_status` | varchar(16) | NO |
| `address_snapshot` | text | YES |
| `location_snapshot` | text | YES |
| `effective_from` | datetime(3) | YES |
| `effective_to` | datetime(3) | YES |
| `current_equipment_id` | bigint | YES |
| `install_time` | datetime | YES |
| `installer_user_id` | bigint | YES |
| `environment_check` | varchar(1000) | YES |
| `spec_check` | varchar(1000) | YES |
| `photo_url` | varchar(500) | YES |
| `result` | varchar(1000) | YES |
| `status` | tinyint | NO |
| `remark` | varchar(500) | YES |
| `version` | int | NO |
| `creator` | varchar(64) | YES |
| `create_time` | datetime | NO |
| `updater` | varchar(64) | YES |
| `update_time` | datetime | NO |
| `deleted` | bit(1) | NO |
| `tenant_id` | bigint | NO |
| `source_product_code` | text | YES |
| `source_product_model` | text | YES |
| `source_product_name` | text | YES |
| `source_receiver_name` | text | YES |
| `source_express_no` | text | YES |
| `source_carrier_name` | text | YES |
| `source_shipment_time` | datetime(3) | YES |
| `source_contract_no` | text | YES |
| `changed_project_key` | text | YES |
| `changed_contract_no` | text | YES |
| `transfer_project_key` | text | YES |
| `transfer_contract_no` | text | YES |
| `source_transfer_flag` | int | YES |
| `source_created_at` | datetime(3) | YES |
| `source_created_by` | text | YES |
| `source_updated_at` | datetime(3) | YES |
| `source_updated_by` | text | YES |
| `source_history` | bit(1) | NO |
| `source_record_key` | varchar(128) | YES |
| `source_device_sn` | varchar(128) | YES |
| `source_project_key` | varchar(128) | YES |

### imp_installation_address_source_fact

| 当前字段 | 类型 | 可空 |
|---|---|---|
| `id` | bigint | NO |
| `tenant_id` | bigint | NO |
| `project_id` | bigint | NO |
| `source_project_key` | varchar(128) | NO |
| `source_system` | varchar(32) | NO |
| `source_key` | varchar(128) | NO |
| `source_version` | varchar(128) | YES |
| `source_read_at` | datetime(3) | NO |
| `source_checksum` | char(64) | NO |
| `equipment_id` | bigint | YES |
| `address_id` | bigint | YES |
| `address_version` | bigint | YES |
| `address_snapshot` | json | YES |
| `source_id` | int | YES |
| `source_project_id` | int | YES |
| `source_device_sn` | varchar(25) | YES |
| `install_location` | text | YES |
| `source_product_code` | varchar(25) | YES |
| `source_product_model` | varchar(255) | YES |
| `source_product_name` | varchar(255) | YES |
| `source_receiver_name` | varchar(255) | YES |
| `source_express_no` | varchar(255) | YES |
| `source_carrier_name` | varchar(15) | YES |
| `source_shipment_time` | datetime(3) | YES |
| `source_contract_no` | varchar(50) | YES |
| `changed_project_key` | int | YES |
| `changed_contract_no` | varchar(50) | YES |
| `transfer_project_key` | int | YES |
| `transfer_contract_no` | varchar(50) | YES |
| `source_transfer_flag` | int | YES |
| `source_created_at` | datetime(3) | YES |
| `source_created_by` | varchar(25) | YES |
| `source_updated_at` | datetime(3) | YES |
| `source_updated_by` | varchar(25) | YES |
| `creator` | varchar(64) | NO |
| `create_time` | datetime(3) | NO |
| `updater` | varchar(64) | NO |
| `update_time` | datetime(3) | NO |
| `deleted` | bit(1) | NO |

### ast_device_location

| 当前字段 | 类型 | 可空 |
|---|---|---|
| `id` | bigint | NO |
| `device_sn` | varchar(100) | NO |
| `site_id` | bigint | YES |
| `site_location_id` | bigint | YES |
| `resolution_status` | varchar(16) | NO |
| `location_snapshot` | text | YES |
| `effective_from` | datetime(3) | NO |
| `effective_to` | datetime(3) | YES |
| `installation_id` | bigint | YES |
| `source_system` | varchar(32) | NO |
| `source_key` | varchar(128) | NO |
| `source_version` | varchar(64) | YES |
| `current_device_sn` | varchar(100) | YES |
| `version` | int unsigned | NO |
| `creator` | varchar(64) | NO |
| `create_time` | datetime(3) | NO |
| `updater` | varchar(64) | NO |
| `update_time` | datetime(3) | NO |
| `deleted` | bit(1) | NO |
| `tenant_id` | bigint | NO |

## 原实现证据

- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/WebContent/sys/module/shipmentInfo.jsp:11，保存安装地址入口；第19行 SaveInstallAdress.action。
- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/src/com/dp/plat/service/ProjectServiceImpl.java:1323、1372，已有设备记录改地址，缺失行复制发货资料；第1448行附近区分转出与转入。
- D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml:2815，地址更新只写 installAddress；2820插入复制产品/物流并写 NOW()/createBy。
- 同一 SQL 文件2985，转移更新不写审计时间；2994插入双侧转移事实；3015登记接收项目串货合同。

## 现有发货、装箱单及目录的逐行对应核对

本次948条均按原barcode＋contractNo精确对应一条未删除正式ast_device_shipment，并经其package_no对应一条DPPMS正式com_shipment_package。原itemCode与正式发货product_code、原packdate与两实体shipment_time、原receiveName/emsNum/emsCompany与装箱单receiver_name/express_no/carrier_name均逐行一致，没有发现差异。这些已存在的正式字段可承接本批复制值，不需要为安装实体再加产品、收货人、运单、承运商或发货日期镜像列。本核对不批准新关系字段，也不把仅靠设备当前归属查询替代来源项目关联。

948条原itemCode都有右侧填充空格。正式发货保留原编码；目录关联沿现有DeviceShipmentProductQueryService.directoryCode规则去首尾空格，76个编码均唯一匹配现有正式产品。本批948条itemName与原fb_items.describe_、以及该正式产品既有dppms_catalog_labels.productName均精确一致；旧名称已经由获准正式实体扩展承接。本批itemModel全部NULL，不能把后来目录型号伪称为当时记录的已知型号。

原安装地址以及转出/接收项目、合同、transferFlag的正式业务归属仍未裁决。原审计字段必须沿来源账号绑定和历史时间处理，不能由发货或装箱单的审计替代。当前安装实体的待安装状态和安装完成动作含义与原表保存地址的事实不同，不能仅撤回镜像后即声称整改完成。下一步需核对已有设备项目关系和地址关系的完整字段，再一起提交目标表方案；未明确映射不丢弃原值、不新增结构。

受控只读证据：installation-existing-business-fit-readonly-v2-20261010.json、installation-product-name-fit-readonly-v2-20261010.json；两者writes=0。首次核对脚本分别把BIT正常值的十六进制字符串当真值、忽略原编码填充空格，造成错误的缺匹配统计；已纠正脚本并重新核对，首次文件留存但不作为结论或迁移依据。实际原表、正式表、历史回执均未修改。

## 当前地址边界补核

本批948个原序列号全部唯一对应正式设备，当前项目均与来源项目绑定一致；没有缺失设备或当前项目冲突。每台本批来源仅一个地址，全部长度不超过255，transferFlag均为-1。正式地址目录ast_address已具备detail_address/full_address等同义字段，可复用原文本，不猜省市或经纬度。

其中20台有当前ast_device_location并指向正式已完成安装记录；全部20个当前安装地址与原地址不同，其余928台没有当前设备位置记录。原表没有地址更新日期，不能按其插入日期判断哪个地址较新，也不能把保存地址当作待安装状态或安装完成。现有设备位置生效Owner会关闭当前位置并改写设备投影，原有安装Owner会初始化待安装及安装时间，均不能未经业务裁决直接调用。

业务归属已裁决：用户选择“承接正式历史地址关联，保留当前位置”，并回复“不新增字段”。随后明确指出应复用已有安装历史记录；[新地址实体方案](2026-10-10-dppms-device-address-record-table-decision.md)已撤回，当前方向为复用 imp_eng_installation 原32列。20个当前位置和928个空当前位置均保持，不生成安装完成事实。缺失状态、日期及原NULL更新时间的正式约束冲突另行整表裁决。证据：installation-address-cardinality-readonly-20261010.json、installation-current-location-boundary-readonly-20261010.json，均writes=0。
