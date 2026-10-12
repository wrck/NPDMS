# G008：com_shipment_package 整表裁决

状态：APPLIED_AND_VERIFIED（2026-10-11，本任务59282实例）。纠正前目标表共22列：19个既有字段、3个本任务新增列；现保留20列（19既有＋已批准carrier_code）。原fb_shipment全部8列、原发货物流视图全部6列、精确承运商词条原表全部12列均已核对。本批10个项目有19个装箱单，原表及正式表均19条；19/19原con_id与正式发货合同归属记录来源键一致。

用户裁决：其余整表按建议处理；carrier_code新增物理列，与carrier_name一样。复用19个既有字段，保留已批准carrier_code VARCHAR(256) NULL业务列，撤回source_contract_key及source_carrier_name两个重复列。原编码与精确解码名称分别保存，不再使用carrier_code实体扩展提案。合同原键由shipment_contract_ref_id正式关系承接，并在写入前锁定核对归属记录的来源键和来源系统。

原物流视图已证实：emsCompany经fnd_basic_data的dataTypeCode=01、basicDataId精确解码为basicDataName，无法命中时返回原编码。本批0→顺丰、12→跨越速运、2→齐普生、7→新杰物流。编码不是本平台公司编码，不执行01→001公司规则。

代码和当前SQL重复新增定义已纠正；2026-10-11完成全表保护、既有迁移Owner核验与两个重复列清理，保留carrier_code。原词典附加属性、排序、日期及人员属于已选择的“来源基础数据”领域，全部登记待该表裁决，本表不替代该领域承接。

## 全部19个既有字段

| 字段 | 类型 | 处理 |
|---|---|---|
| id | bigint | 目标技术主键，不把原packlist_id写为目标ID |
| tenant_id | bigint | 当前迁移租户，合同归属和装箱单必须同租户 |
| shipment_contract_ref_id | bigint | 经正式com_shipment_contract_reference关联；原con_id由该记录的既有source_record_key及INT绑定精确回溯，不重复加合同原键列 |
| package_no | varchar(128) | fb_shipment.packlist_id，正式装箱单业务编号 |
| shipment_time | datetime(3) | 原packdate原值，毫秒精度不截断 |
| warranty_start_time | datetime(3) | 原warrantyStartTime原值，不替代条码级保修日期 |
| warranty_end_time | datetime(3) | 原warrantyEndTime原值；先核查既有结束不得早于开始约束，违反时阻断，不删值或造日期 |
| receiver_name | varchar(512) | 原receiveName原样承接 |
| express_no | varchar(512) | 原emsNum原样承接 |
| carrier_name | varchar(256) | 原视图按emsCompany精确关联fnd_basic_data(dataTypeCode=01,basicDataId)，用basicDataName；缺项沿原视图返回原码，不猜名称，列明未解码 |
| mapping_status | varchar(32) | 沿已有合同归属映射事实，无精确关联不能标为MAPPED |
| source_system | varchar(32) | 既有来源身份DPPMS，非重复业务镜像 |
| source_record_key | varchar(128) | 既有装箱单幂等键packlist_id，非目标主键 |
| source_sync_time | datetime(3) | 既有平台同步时间；原表无来源同步/创建日期，不伪造历史时间 |
| creator | varchar(64) | 迁移执行正式账号，原表没有创建人；不编造原操作者 |
| create_time | datetime(3) | 本平台正式记录创建时间，原表没有创建时间，不声称原历史审计 |
| updater | varchar(64) | 迁移执行正式账号，原表没有更新人 |
| update_time | datetime(3) | 本平台正式记录维护时间，原表没有更新时间；重跑无变化不重写 |
| deleted | tinyint | 现有逻辑删除字段；不自动复活已删除正式记录 |

## 新增列整表裁决结果

| 字段 | 类型 | 处理 |
|---|---|---|
| source_contract_key | varchar(128) | 撤回：复用shipment_contract_ref_id精确关系；19/19本批原con_id与正式归属记录来源键一致；全表关系核对完成后清理，不能仅按本批推断全表 |
| carrier_code | varchar(256) | 用户批准物理业务列，原emsCompany编码完整承接，与carrier_name名称分别保存 |
| source_carrier_name | text | 撤回：承运商名称复用carrier_name，不保存重复名称列 |

## 原fb_shipment全部8列

| 原字段 | 处理或待确认原因 |
|---|---|
| packlist_id | package_no及现有来源幂等身份 |
| con_id | shipment_contract_ref_id精确关系及归属记录既有来源键，不丢弃合同关联 |
| packdate | shipment_time |
| warrantyStartTime | warranty_start_time |
| warrantyEndTime | warranty_end_time |
| receiveName | receiver_name |
| emsNum | express_no |
| emsCompany | carrier_code物理业务列完整承接原编码，carrier_name完整承接精确解码名称 |

## 原view_shipment_ems_4_pm全部6列

| 原字段 | 处理或待确认原因 |
|---|---|
| contract_code | 正式发货合同归属/合同主档contract_no，经原con_id精确关联 |
| receiveName | receiver_name（同一原表字段，不再加镜像） |
| emsNum | express_no（同上） |
| packdate | shipment_time（同上） |
| emsCompany | carrier_name，来源词典/原码回退规则如上 |
| packId | package_no（同一原packlist_id） |

## 原fnd_basic_data全部12列

| 原字段 | 处理或待确认原因 |
|---|---|
| id | 词典技术主键，关联词条证据；完整基础数据主档另按已选择第4域整表裁决，不复制进装箱单 |
| dataTypeCode | 原承运商类型01，精确解码限定，不当作公司码转换 |
| basicDataId | 原emsCompany编码；本表已批准carrier_code物理业务列正式承接 |
| basicDataName | 本表carrier_name完整承接名称 |
| basicDataAttri1 | 原词条附加属性已读原表，完整基础数据领域待整表确认；不冒充承运商名称，不丢弃 |
| sortId | 原词典排序，完整基础数据领域待整表确认，不写装箱单排序 |
| createTime | 原词条创建时间，完整基础数据领域待整表确认，不冒充发货日期或装箱单审计 |
| createBy | 原词条创建人，完整基础数据领域待整表确认，不冒充收货人 |
| updateTime | 原词条更新时间，完整基础数据领域待整表确认，不冒充发货更新时间 |
| updateBy | 原词条更新人，完整基础数据领域待整表确认，不冒充装箱单更新人 |
| effectiveFrom | 原词条有效起始，完整基础数据领域待整表确认，不改写质保起始 |
| effectiveTo | 原词条有效结束，完整基础数据领域待整表确认，不改写质保结束 |


## 纠正实现与全表保护核查

全表156368行（包含各租户和删除行），source_contract_key/source_carrier_name各19个非NULL，分别19/19与正式合同归属来源键及carrier_name精确一致。未发现这两列的索引、外键、视图、触发器或当前同步任务依赖，现有重复值已保留任务私有核查证据。代码与当前SQL保留用户批准carrier_code物理列，撤回两个镜像；写入前通过COM Owner锁定核对正式合同归属。原30项针对性测试结果仍适用，本次复用已打包部署的既有代码，没有新增生产代码。

2026-10-11重新读取原fb_shipment19×8完整字段，与不可变原材料和正式日期、收货人、运单、承运商编码及合同关系逐值核对。已有数据全部一致，无需更改正式业务值；通过既有carrier-corrections迁移入口及COM Owner执行10个项目核验命令，随后才清理两个重复物理列。清理前后对全156368行20个保留字段分2000行校验全部值，包含各租户、逻辑删除行、ID及审计；结果一致。原索引及Flyway执行历史保持。清理后10次命令重放均REPLAY_COMPLETED。任务私有shipment-package-final-approved-state-20261011.json保留19条被撤回镜像原值、全部8原字段、逐页全值保护及10次正式命令回执。真实页面最终验收另记下文；不以本次本地结构完成宣称生产历史基线已对齐。

19082真实浏览器从项目列表筛选并打开51304正式项目，进入“DPPMS关联资料→系统项目资料→装箱单”，四条正式包件日期、收货人、运单及跨越速运名称正常读回；打开实体详情，承运商编码12与名称分别显示。设备发货事实页也重新查询正式装箱单号及销售订单等字段，未出现删除列的查询错误。私有页面证据shipment-package-final-formal-table-browser-20261011.txt、shipment-package-final-formal-detail-browser-20261011.txt。另在基本信息页观察到“阶段状态加载失败”，该错误不归入装箱单通过范围，独立核查，不宣称整个项目详情已验收。
