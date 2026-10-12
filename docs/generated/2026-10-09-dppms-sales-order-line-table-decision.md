# com_sales_order_line 整表裁决

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。用户裁决：整表按建议。

已核原pm_order_line_from_erp全部16列，正式表45列＝41个既有字段＋4个本任务新增列。

## 一次确认的整表建议

撤回source_company_code、source_erp_system、legacy_record_id、original_source_sync_time四列。公司、来源更新时间复用正式字段；原source建议沿订单头裁决复用source_system保存实际SAP/D365/SMS。原自增ID仅保留既有迁移/同步技术关联；业务订单与行由正式实体承接，不用中间快照代替。无新表、列。

原单位未提供，依已裁决允许为空；原数量保持合法符号与完整十进制精度，依既有Owner确定数量状态。所有父订单、产品和交付范围关联与并发保护保留。

## 41个既有正式字段

| 正式字段 | 类型 | 处理 |
|---|---|---|
| id | bigint | 正式订单行技术主键；原自增ID仅用于既有迁移/同步来源绑定 |
| tenant_id | bigint | 迁移租户，父订单、产品和交付范围引用须同租户 |
| order_id | bigint | 精确关联同ERP系统、转换后公司码、订单类型及订单号的正式父订单，不按订单号跨公司猜配 |
| source_system | varchar(32) | 建议沿订单头已裁决语义，复用此列承接原source的SAP/D365/SMS，不新增erp_system |
| source_record_key | varchar(128) | 既有正式幂等键；2条仍含原01，但正式company_code已001。保留原键与ID，复用绑定/业务身份，不为改键创建重复行 |
| source_version | varchar(64) | 沿既有版本机制，以原syncTime生成来源版本，不用当前运行时间冒充 |
| company_id | bigint | 由可核实的正式公司关系承接，不猜公司ID |
| company_code | varchar(64) | 原compCode按既有已批准01→001、02→002、03→003、04→004转换，未知码阻断 |
| company_name | varchar(255) | 精确关联正式公司；原表没有名称，不编造 |
| order_type | varchar(32) | 按精确父订单正式类型承接，不能把发货标志或项目状态当订单类型 |
| order_no | varchar(64) | 原orderNumber直接承接，并与父订单一致 |
| line_no | varchar(32) | 原lineNum直接承接，保持行号字符串语义 |
| line_type | varchar(32) | 原lineType直接承接，沿既有0销售/1退货语义 |
| product_code | varchar(64) | 原itemCode直接承接 |
| product_model | varchar(64) | 原订单行没有型号，保留既有正式事实，不用itemDesc冒充型号 |
| product_desc | varchar(512) | 原itemDesc完整承接 |
| custom_info | json | 原customInfo完整对象；本批全NULL仍保留NULL，不复制已有业务字段到JSON |
| product_id | bigint | 按租户＋产品编码精确关联已有正式产品，不按产品名称猜配；缺少绑定的关系列明待关联 |
| bundle_code | varchar(64) | 非空原bundleCode直接承接；原空字符串也直接保存；其中2条现为NULL，补入原空字符串，不丢弃原非NULL值 |
| profit_center | varchar(64) | 原profitCenter直接承接 |
| real_execution_no | varchar(64) | 原realOrderExecNumber直接承接 |
| warranty_month | int | 原warrantyMonth直接承接；非整数/超出正式字段范围阻断，不截断 |
| customer_id | bigint | 从精确父订单及可核实正式买方绑定承接，不按名称猜配 |
| customer_code | varchar(64) | 父订单的正式买方编码；原订单行无独立客户字段 |
| customer_name | varchar(512) | 父订单正式买方名称；不以项目最终用户替代 |
| order_qty | decimal(18,6) | 原orderQuantity完整十进制承接；扩大历史时保持合法销售/退货的符号，不取绝对值 |
| open_qty | decimal(18,6) | 原openQuantity完整十进制承接，保持合法符号 |
| delivered_qty | decimal(18,6) | 沿已有Owner规则orderQuantity-openQuantity；任一原值缺失则保持未知，不冒充发货事实 |
| unit_code | varchar(32) | 原表无单位；用户已允许单位为空，保持NULL及待权威核实，不自行填台/件 |
| unit_scale | tinyint unsigned | 保留既有数量精度定义，不用数量的小数位数猜业务单位 |
| quantity_status | varchar(32) | 沿已有数量Owner语义；单位缺失时PENDING_AUTHORITY，不声称权威数量已确认 |
| source_lifecycle_status | varchar(32) | 沿已有销售/退货转换，不产生现平台审批或任务完成事实 |
| source_sync_time | datetime(3) | 既有Owner接收同步时间，不和原syncTime混用 |
| source_updated_at | datetime(3) | 原syncTime来源版本时钟；保持既有DATETIME(3)，不增加重复时钟列 |
| status | varchar(32) | 既有正式启停状态，不恢复逻辑删除记录 |
| version | int unsigned | 既有乐观锁，保持并发及交付范围冲突保护 |
| creator | varchar(64) | 沿正式Owner审计；原表没有创建人，不伪造历史人员 |
| create_time | datetime(3) | 沿正式Owner创建审计；原表没有记录创建日期，不把syncTime冒充 |
| updater | varchar(64) | 沿正式Owner审计；原表没有更新人，不伪造 |
| update_time | datetime(3) | 沿正式Owner更新审计，不混入原版本时钟 |
| deleted | tinyint | 保留正式逻辑删除及不可变历史，不能为迁移复活已删行 |

## 4个新增列撤回方案

| 当前新增列 | 正式复用点与原因 |
|---|---|
| source_company_code | 复用company_id/company_code及已确认转换 |
| source_erp_system | 建议复用source_system保存SAP/D365/SMS，和订单头一致 |
| legacy_record_id | 原技术ID由既有来源绑定承接，不新增业务镜像 |
| original_source_sync_time | 复用已有source_updated_at来源版本时钟 |

## 原表全部16列

| 原字段 | 正式承接点 | 处理或排除原因 |
|---|---|---|
| id | 既有迁移/同步来源绑定 | 技术自增ID没有订单行业务含义，不造legacy_record_id列 |
| orderNumber | order_no / order_id | 与同ERP/公司/类型正式父订单精确关联 |
| lineNum | line_no | 完整原行号 |
| itemCode | product_code / product_id | 原编码正式保存，产品关系按租户＋编码精确关联 |
| itemDesc | product_desc | 完整原产品描述 |
| orderQuantity | order_qty | 合法符号与十进制精度保留 |
| openQuantity | open_qty | 合法符号与十进制精度保留 |
| bundleCode | bundle_code | 非空组合码原值；原空字符串亦原样保存，其中2条当前NULL待补，差异明确列明 |
| warrantyMonth | warranty_month | 保修月数原值，不截断非整数 |
| lineType | line_type / order_type | 原行类型，并核对父订单类型 |
| compCode | company_code / company_id | 统一显式转换，未知阻断，原码差异列明 |
| profitCenter | profit_center | 利润中心原值 |
| realOrderExecNumber | real_execution_no | 实际执行单号原值 |
| source | source_system | 建议沿订单头用户裁决，实际ERP系统直接复用既有列 |
| customInfo | custom_info | 完整对象或原NULL |
| syncTime | source_updated_at / source_version | 原来源版本时钟，较旧版本不覆盖较新正式事实 |

## 原表值核查

105条原订单行均唯一对应当前正式订单行。除2条bundleCode空字符串在现表为NULL外，已映射业务列值均一致；建议直接补入原空字符串，原差异列明。数量按数值比较，不能把1与1.000的显示差异当成业务丢失；原单位不存在，正式105行单位均NULL。2条正式来源键仍含旧01，但正式公司码001，沿原来源键和ID复用，不据此制造重复订单行或改写不可变来源证据。

原业务值比较限本批105条；另已核查真实库全租户含逻辑删除50790条，4个镜像列各105个非NULL值，公司、实际ERP来源键和原时钟比较无差异。105条source_system仍待正式Owner从ERP补为实际系统，2条原空组合码待补承接。先完成正式承接和保护核查，才清理真实库重复列。SQL/migrations按最终裁决直接整理，不先加后删。


## 已批准实现进展

4个镜像实体字段与当前SQL新增定义已撤回，复用source_system及source_updated_at；同步复用含原公司码的正式Owner来源键与ID，原空组合码完整承接。正在验证，尚未纠正真实库、部署或完成页面验收。

## 最新定向验证

订单及订单行当前代码共108项定向测试通过，0失败、0错误、0跳过。覆盖原空字符串和描述空白保留、公司转换、旧来源键及ID复用、数量精度、回放和冲突保护。该定向结果不等于实际迁移或浏览器验收；实际迁移及浏览器验收尚未完成。真实库镜像列未删除，服务未重启。

## 实际MySQL Mapper验证

3项实际MySQL/XML验证及47项受影响定向回归通过。覆盖实际ERP来源类别与旧ERP类别兼容、跨租户/删除/错来源键排除、空筛选集合返回空、同步识别已删除身份以阻止重建，以及执行单原空字符串、正式组织关系、乐观锁和审计更新。测试数据只存在于单连接临时表，关闭连接后消失；未改写真实业务行或表结构。服务、迁移和页面验收仍待后续执行。


## 2026-10-10 正式承接及重复列清理完成

已通过既有迁移功能的定向纠正命令、COM Owner及单独的ORDER_FIELDS_V1回执补承接10个订单、105条订单行。实际来源D365直接承接到source_system；10个原空字符串订单备注、2个原空字符串组合码均原样补入，正常乐观版本各加1。原ID、来源键、审计人员和历史时间以及其他业务字段逐项核对不变；未复活旧01逻辑删除订单，不触发分配或验收事件。重复执行10个项目均REPLAY_COMPLETED；列清理后再次回放仍无业务写入。

6个订单及4个订单行镜像列已从任务库清理；当前SQL/migrations早已按裁决撤回新增定义，未增加先加后删的迁移。逐行流式备份、比较两表全部101262/50790行，剩余字段值一致；20个正式资料查询结果一致，项目及其他项目资料、Flyway原历史未变。原表完整16列及原NULL逐项核对，实际来源数据没有写入。10个项目的旧迁移回执保留，新字段纠正回执独立记录。

84项后端针对性检查、宿主机完整打包和8项页面组件检查通过。真实浏览器打开正式订单详情，可见D365、001、原客户/项目名称及毫秒日期；发现客户要求时间显示数字后已修正，可见2023-03-20 00:00:00.000。证据保存在本任务受控目录：verified-order-fields-formal-carry-20261010.json、verified-order-fields-after-mirror-cleanup-repeat-20261010.json、verified-order-mirror-finalization-20261010.json、verified-order-original-tables-current-20261010.json、verified-order-formal-fields-browser-20261010.txt。以上为本表实际边界，整体Flyway基线对齐和实施范围迁入仍在推进，不宣称生产上线完成。
