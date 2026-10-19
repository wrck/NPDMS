# P33 原表逐字段承载清单

合并组件：P33。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_shipment.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P33 |
| pm_project_shipment.projectId | int(11) / YES | imp_eng_installation.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 原项目通过正式绑定关联，不取设备后来的归属代替；业务字段/正式关系 | P33 |
| pm_project_shipment.barcode | varchar(25) / YES | imp_eng_installation.equipment_id；ast_device.sn（精确关联） | 原barcode精确核实SN再关联正式设备id；不能将原SN写入equipment_id。；业务字段/正式关系 | P33 |
| pm_project_shipment.itemCode | varchar(25) / YES | 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致 | 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致；业务字段/正式关系 | P33 |
| pm_project_shipment.itemModel | varchar(255) / YES | 正式产品目录关联；本任务不新增安装型号镜像 | 原试点/旧全量统计为NULL，仅作为当时事实；生产若非空按P12正式目录规则核对，不据旧统计丢弃。；业务字段/正式关系 | P33 |
| pm_project_shipment.itemName | varchar(255) / YES | ast_product_official_info.product_name（目录关联读取） | 按产品编码使用CRM正式名称；P12最新裁决不再保留DPPMS异名扩展，本字段不写安装镜像。；目录引用；待移除旧标签依赖 | P33 |
| pm_project_shipment.receiveName | varchar(255) / YES | 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对 | 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对；业务字段/正式关系 | P33 |
| pm_project_shipment.emsNum | varchar(255) / YES | 正式com_shipment_package.express_no | 正式com_shipment_package.express_no；业务字段/正式关系 | P33 |
| pm_project_shipment.emsCompany | varchar(15) / YES | 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代 | 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代；业务字段/正式关系 | P33 |
| pm_project_shipment.packdate | datetime / YES | 正式发货及装箱单shipment_time，不推导安装日期 | 正式发货及装箱单shipment_time，不推导安装日期；业务字段/正式关系 | P33 |
| pm_project_shipment.contractNo | varchar(50) / YES | 正式发货contract_no及合同关系，不盲目删-C后缀 | 正式发货contract_no及合同关系，不盲目删-C后缀；业务字段/正式关系 | P33 |
| pm_project_shipment.installAddress | text / YES | imp_eng_installation.install_location；imp_eng_installation.location_snapshot ＋ ast_address.full_address | 原地址完整保存；正常地址目录与设备位置由各自Owner承接；业务位置快照是既有正式安装属性，不是迁移中间快照；业务字段/正式关系 | P33 |
| pm_project_shipment.chProjectId | int(11) / YES | 本轮暂不迁入 | 用户已批准串货/转移业务本轮不迁；原字段/证据保留，不产生新转移事实。；已批准暂缓 | P33 |
| pm_project_shipment.chContractNo | varchar(50) / YES | 本轮暂不迁入 | 用户已批准串货/转移业务本轮不迁；原字段/证据保留，不产生新转移事实。；已批准暂缓 | P33 |
| pm_project_shipment.transferProjectId | int(11) / YES | 本轮暂不迁入 | 用户已批准串货/转移业务本轮不迁；原字段/证据保留，不产生新转移事实。；已批准暂缓 | P33 |
| pm_project_shipment.transferContractNo | varchar(50) / YES | 本轮暂不迁入 | 用户已批准串货/转移业务本轮不迁；原字段/证据保留，不产生新转移事实。；已批准暂缓 | P33 |
| pm_project_shipment.transferFlag | varchar(2) / YES | 本轮暂不迁入 | 用户已批准串货/转移业务本轮不迁；原字段/证据保留，不产生新转移事实。；已批准暂缓 | P33 |
| pm_project_shipment.createTime | datetime / YES | imp_eng_installation.create_time；imp_eng_installation.install_time；imp_eng_installation.effective_from | 有地址则status=2；原创建时间表示安装/生效；20已有较新安装取其生效为历史结束，其余928补当前空位置；业务字段/正式关系 | P33 |
| pm_project_shipment.createBy | varchar(25) / YES | imp_eng_installation.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P33 |
| pm_project_shipment.updateTime | datetime / YES | imp_eng_installation.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P33 |
| pm_project_shipment.updateBy | varchar(25) / YES | imp_eng_installation.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P33 |
