# 项目真实产品清单整表裁决

状态：WHOLE_TABLE_APPROVED / PILOT_VERIFIED。用户已回复“19列及上述处理整表按建议”，批准本报告整表结构、实际迁入审计及零/负数量原样承接。已完成19列结构、正式Owner、迁移/同步接入及本批10项目验收。业务归属沿用户已明确的“附加项目关联子表”，不把它并入实施范围或产品主目录。全历史扩大、持续刷新及整体Flyway基线尚未完成。

原表 `pm_project_product_line_real` 实际有 **12列、7848条**，此前11列统计遗漏了技术ID的计数，应以本报告实查为准。本批10项目为0条。裁决前 `proj_project_real_product` 是本任务先前新增的21列表、0条；目前已按本次裁决整理为正式19列，不能将裁决前的结构当作平台原有实体。

旧刷新程序先从SMS/CRM得到产品清单，再按执行单、合同和项目组关系关联项目；执行单匹配依次采用原值、忽略版本、再忽略类型。程序会TRUNCATE重建并重新产生ID。新同步不能把这个自增ID作为跨刷新稳定业务身份，也不能直接复制旧程序的宽松关联或全表删除。

## 最终整表：19列

以下列已获用户整表批准。复用现有物理字段17列、source_project_code更名project_code、撤回3个技术镜像列，批准新增物理列只有data_source。保持业务实体完整承接，不保存原表整行JSON。

| 最终列 | 类型/可空 | 处理及依据 |
|---|---|---|
| id | BIGINT/否 | 正式实体ID，沿Owner生成；原技术ID由迁移绑定承接 |
| tenant_id | BIGINT/否 | 沿迁移任务租户，隔离不变 |
| project_id | BIGINT/否 | 原projectId精确关联正式项目；未匹配阻断 |
| contract_no | TEXT/是 | 原contractNo原样，空字符串不转NULL；不按名称挂靠合同 |
| project_code | TEXT/是 | 现source_project_code更名，承接原清单商务项目编码；不以正式项目主表编码替代 |
| execution_no | TEXT/是 | 原orderExecNumber原样，不再次删除版本或替换类型 |
| product_category_name | TEXT/是 | 原productFirstName，保持原一级产品名称/分类文字，不关联猜造分类字典 |
| product_name | TEXT/是 | 原productName，清单自身事实，不覆盖当前产品目录 |
| product_sub_code | TEXT/是 | 原productSubCode，不将自增ID作为产品码 |
| product_sub_model | TEXT/是 | 原productSubModel，清单自身型号，不以当前目录覆盖 |
| product_sub_name | TEXT/是 | 原productSubName，清单自身名称，不以当前目录覆盖 |
| quantity | DECIMAL(24,6)/是 | 原num整数精确保存，原NULL/0保持；负数事实已批准原样保留，不能生成分配或交付数量 |
| memo | TEXT/是 | 原memo说明，原NULL与空字符串分别保留；不借此存其他字段 |
| data_source | VARCHAR(25)/是 | **唯一批准新增物理列**；承接上游原dataSource，不以技术source_system替代 |
| creator | VARCHAR(64)/否 | 原表无创建人，建议实际迁入操作者，不冒充历史人员 |
| create_time | DATETIME(3)/否 | 原表无创建日期，建议实际迁入时间，不用项目创建日期代替 |
| updater | VARCHAR(64)/否 | 初建同实际迁入操作者；后续正式更新沿Owner审计 |
| update_time | DATETIME(3)/否 | 初建同实际迁入时间；不编造来源更新时间 |
| deleted | BIT(1)/否 | 正式逻辑删除；不因旧刷新清空自动删除正式记录 |

原21列逐项覆盖：上述复用/更名18列＋撤回的source_system、source_table、source_record_key；再新增前不存在的data_source，最终19列。技术来源、批次、原ID、校验和及运行回执仍由既有INT/PLT迁移绑定承接，不能冒充业务字段。

## 原表全部12列

| 原字段 | 原类型 | 正式承接/原因 |
|---|---|---|
| id | INT | 技术身份进入迁移绑定；旧刷新会重排，不新增业务镜像 |
| projectId | INT | project_id |
| contractNo | VARCHAR(25) | contract_no |
| projectCode | VARCHAR(255) | project_code |
| orderExecNumber | VARCHAR(255) | execution_no |
| productFirstName | VARCHAR(255) | product_category_name |
| productName | VARCHAR(128) | product_name |
| productSubCode | VARCHAR(255) | product_sub_code |
| productSubModel | VARCHAR(255) | product_sub_model |
| productSubName | VARCHAR(255) | product_sub_name |
| num | INT | quantity |
| memo | MEDIUMTEXT | memo；本表最长92字符，TEXT可完整保存，扩大时校验长度不截断 |

除memo外本次全历史各列均非NULL；memo7285条非NULL。0数量51条，负数量2条。12列业务组合无重复；全部7848个原projectId均能找到旧项目，但清单projectCode与对应旧项目主表projectCode **全部不同**，不能用主表编码覆盖商务编码。

## 上游原表全部10列

`pm_project_real_product_line_from_sms` 有9992条；9项共享业务属性projectCode、orderExecNumber、productFirstName、productName、productSubCode、productSubModel、productSubName、num、memo分别映射上表同义列。唯一额外原列dataSource为VARCHAR(25)，9992条均为SMS，建议新增正式data_source承接。

逐条以全部9项业务属性精确比较（文本使用BINARY，NULL用NULL安全相等），7848条项目清单均唯一匹配一条上游原行，未匹配0、多匹配0，故可承接其真实dataSource，不根据类名猜系统。上游未形成项目清单的行不伪造项目关联，属于上游全局目录/同步边界的后续规划，原库不删除、完整登记待承接；本表裁决不批准丢弃它们。

## 已批准事项

1. 上述19列获批准；唯一新增物理列data_source，其余沿复用、更名及三列技术镜像撤回方案。没有项目清单数据的本批10个项目不制造示例业务行。
2. 原表无审计字段，按实际迁入人员/时间初始化正式审计。
3. 原样保留51条0及2条负数量，仅作真实产品清单自身事实，不生成实施分配、交付、验收或退货完成事实，不自行取绝对值或丢弃。

持续刷新稳定身份/数量更新和删除规则另行规划裁决，未确认前只开放已核实输入的预览、迁入和重放，不启用自动刷新。本次实施限于已批准19列及处理，本批10项目来源为0条，不制造业务行。

旧实现依据：D:/EclipseWorkspace/Parctice/PMS/PMS-struts/src/com/dp/plat/job/GainPrjRealProjectLineBySMS.java；config-ibaits/sql-map-refresh-data-common-config.xml:454、477、529；项目读取sql-map-project-config.xml:4146。完整原结构、非空统计和关系核查证据为任务私有real-product-whole-table-readonly-20261010.json，writes=0。

## 实施及验收结果

最终实体只保留上表19列。原21列表全表为空，无外键、视图、触发器、存储程序或映射依赖；核查并备份后撤回source_system/source_table/source_record_key，更名project_code，加入data_source。V412只保留最终CREATE定义，不追加“新增再删除”的SQL，未改写数据库Flyway历史。

源适配器分别读取主表12列和上游10列，以9项共享属性进行大小写敏感、NULL安全的精确关联；缺失、多匹配、遗漏字段或敏感内容均阻断。上游来源及技术键使用既有迁移证据与绑定，业务属性实际写入正式19列。正式查询沿项目授权、租户和逻辑删除边界分页；重复执行完整核对原映射、全部业务值及首次落库审计，不覆盖人工修改。项目已有迁入清单时，未绑定的新采集阻断，等待持续刷新规则裁决；不复制旧TRUNCATE或猜测ID重排后的关系。

验证通过：88项相关后端回归、5项真实MySQL Owner/XML测试、28项源适配器测试、完整后端打包及前端字段文件ESLint。MySQL测试使用连接内临时表，覆盖零/负/NULL数量、空字符串、审计精度、租户/项目/删除/大小写边界、重放、手工变更阻断和回滚，未向永久业务表留下测试行。另通过实际源适配器核对4条原始零/负/NULL样本及其唯一上游行。

本批10项目原表为0条，已取得10个空迁入回执；最终运行包上再次验证10个空预览、10个REPLAY_COMPLETED及10个正式分页查询。真实浏览器重开项目51922，选择“真实产品清单”，商务项目编码标题、正常空结果及刷新通过。没有制造业务行。现有安装、位置、设备、项目、实施范围、来源头、不可变快照/原记录及Flyway历史全量保护核对不变。

证据：任务私有real-product-approved-pre-schema-20261010.json、verified-real-product-approved-schema-20261010.json、verified-real-product-approved-pilot-20261010.json、verified-real-product-final-runtime-20261010.json、verified-real-product-final-browser-20261010.txt及real-product-owner-mysql-20261010.log。

本次不表示7848条已全量迁入，也不表示整体生产上线就绪。完整源契约另发现登记的view_warranty不存在、实际view_warranty_source只有31列，已登记DPPMS-WARRANTY-SOURCE-044等待用户裁决；默认完整采集保持失败关闭，不能把本次两张真实产品原表的独立核查当作全部来源表通过。全历史刷新身份、数量更新/删除和整体Flyway基线继续待处理。
