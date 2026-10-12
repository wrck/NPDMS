# 既有安装记录表完整承接裁决

状态：WHOLE_TABLE_APPROVED / PILOT_VERIFIED。用户回复“按1，第一小点有安装地址就表明已安装”：整表按建议，状态例外改为有地址则已安装，复用既有状态2；不调整status为可空。随后明确“有创建时间，存在的记录都是有效记录”，并批准“保留20个较新位置，补齐928个空位置”：原createTime承接install_time/effective_from，旧历史结束时间取较新安装生效时间；通过正式位置Owner补齐空当前位置。目标为既有 `imp_eng_installation`，不新建业务表、不新增列。`ast_device_address_record` 新实体方案已撤回，不再将任务新增来源事实表改名作为承接。

原业务、原表21列、纠正前53列的完整核对见 [安装地址原业务复核](2026-10-10-dppms-installation-original-purpose-audit.md)。既有安装实体32列如下；本任务追加21列已退出最终结构。此文件记录用户裁决及本批验收，不修改PRD、状态机或普通安装流程。

## 既有32列逐项处理

| 正式列 | 当前类型/可空 | 建议处理 |
|---|---|---|
| id | BIGINT/否 | 保留本任务已写入安装记录ID，逐条核对来源绑定；不另建第二套记录 |
| project_id | BIGINT/否 | 原projectId精确关联正式项目，不取设备后来归属替代 |
| code | VARCHAR(64)/否 | 保留已有唯一安装编码；后续新迁入沿既有编码Owner生成规则，不能取原技术ID代替业务码 |
| equipment_id | BIGINT/否 | 原barcode精确关联正式设备，本批948台均匹配 |
| install_location | VARCHAR(255)/是 | 原installAddress原样承接，本批均在255字符内；全历史6111条超长值不截断，待单独裁决 |
| address_id | BIGINT/是 | 复用经核对的正式地址目录引用，不猜行政区划 |
| address_version | INT UNSIGNED/是 | 保留承接时正式地址版本，核对范围；不是原地址更新次数 |
| site_id | BIGINT/是 | 原表没有地点事实，保持NULL，不造地点 |
| site_version | INT UNSIGNED/是 | 同上，保持NULL |
| site_location_id | BIGINT/是 | 原表没有具体机房/位置事实，保持NULL |
| site_location_version | INT UNSIGNED/是 | 同上，保持NULL |
| location_resolution_status | VARCHAR(16)/否 | 沿既有地点解析含义；仅有地址原文、没有地点及具体位置，采用UNRESOLVED，不代表没有地址 |
| address_snapshot | TEXT/是 | 复用既有地址业务快照格式，fullAddress保留完整原文；目录后续修改不能覆盖历史 |
| location_snapshot | TEXT/是 | 沿既有地点快照结构，保存地址引用及fallbackLocation原文，site/siteLocation为空；不存原表整行或迁移回执 |
| effective_from | DATETIME(3)/是 | 已批准用原createTime承接 |
| effective_to | DATETIME(3)/是 | 已批准20台取较新安装effective_from，其他928台保持NULL |
| current_equipment_id | BIGINT/生成列 | 保留既有生成规则；20条较早记录有结束时间，不占当前槽位；928条无较新记录的有效安装占当前槽位 |
| install_time | DATETIME/是 | 已批准用原createTime初始化；该转换来自用户裁决，原表没有单独的安装日期列 |
| installer_user_id | BIGINT/是 | 原createBy是地址记录创建人，不能当安装人，保持NULL |
| environment_check | VARCHAR(1000)/是 | 原表没有检查事实，保持NULL |
| spec_check | VARCHAR(1000)/是 | 原表没有检查事实，保持NULL |
| photo_url | VARCHAR(500)/是 | 原表没有照片，保持NULL |
| result | VARCHAR(1000)/是 | 原表没有安装结果，保持NULL |
| status | TINYINT/不可空 | 已裁决：有安装地址表示已安装，本批948条复用完成状态2。保持不可空，不新增状态；安装日期和当前位置处理见补充裁决 |
| remark | VARCHAR(500)/是 | 不用备注承接缺失业务字段，保持NULL |
| version | INT/否 | 保留已有技术并发版本，纠正写入沿Owner版本规则，不伪称历史业务版本 |
| creator | VARCHAR(64)/是 | 原createBy经既有迁移用户绑定关联正式人员。本批3个来源账号全部能精确关联；Owner校验用户名及目标ID，不猜姓名、不填管理员 |
| create_time | DATETIME/否 | 原createTime，保留历史值，不表示安装完成时间 |
| updater | VARCHAR(64)/是 | 原updateBy为NULL，保持NULL，禁止框架自动填管理员 |
| update_time | DATETIME/不可空 | 整表已批准：原updateTime全NULL，用本记录原createTime初始化正式update_time，原NULL差异列入迁移证据；不使用本次运行时间 |
| deleted | BIT/否 | 保留逻辑删除事实，不将清理重复表等同于业务删除 |
| tenant_id | BIGINT/否 | 迁移任务指定租户，所有查询及Owner校验继续隔离 |

## 原21列完整映射与未承接原因

| 原列 | 正式承接点/处理 |
|---|---|
| id | 既有INT/PLT来源绑定，技术自增ID不新增业务镜像 |
| projectId | imp_eng_installation.project_id |
| barcode | 精确关联equipment_id；正式设备保留SN |
| itemCode | 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致 |
| itemModel | 本批及当前全历史均NULL，不新增安装镜像；后续遇非空重新核对正式产品属性 |
| itemName | 现有正式产品目录已批准的DPPMS名称属性，本批逐字一致 |
| receiveName | 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对 |
| emsNum | 正式com_shipment_package.express_no |
| emsCompany | 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代 |
| packdate | 正式发货及装箱单shipment_time，不推导安装日期 |
| contractNo | 正式发货contract_no及合同关系，不盲目删-C后缀 |
| installAddress | install_location及既有address_snapshot/location_snapshot中的地址业务值 |
| chProjectId | 本批NULL；全历史串货转出项目关系待独立裁决，不丢弃非空关系 |
| chContractNo | 本批NULL；全历史串货转出合同关系待独立裁决 |
| transferProjectId | 本批NULL；全历史串货接收项目关系待独立裁决 |
| transferContractNo | 本批NULL；全历史串货接收合同关系待独立裁决 |
| transferFlag | 本批全-1，没有串货事实；全历史15条转出1/15条转入0待正式转移业务裁决，不据此触发当前动作 |
| createTime | create_time |
| createBy | creator，经正式用户绑定 |
| updateTime | 已批准update_time用原createTime初始化；原NULL在不可变迁移证据列明 |
| updateBy | updater，原NULL保持 |

发货/产品/物流的本批一致不能推广成全历史一致；扩大时每行核对，差异或关联不唯一阻断，不通过引用吞掉不同业务值。

## 待裁决项及清理顺序

1. 已批准：有地址记录status=2；经既有正式迁移绑定保持只读，不调用当前完成动作。无地址记录不能据此判断已安装，扩大历史时另行裁决。
2. 已批准：update_time原NULL使用原createTime初始化；原NULL在迁移证据列明，其余历史审计原样承接。
3. 已批准：其余32列映射按上表复用；没有安装日期、检查、结果等事实的字段不编造。新增表/列均为0。
4. 已批准：原createTime作为install_time/effective_from；20条旧记录effective_to用较新安装生效时间，保留当前较新记录；928条无当前记录通过正式位置Owner补齐来源地址为当前位置。撤回NULL生效日期及修改生成条件的建议。现有生成表达式和唯一约束保持，不新增列。当前地址若在预览后被补入或修改，整批阻断，不自动覆盖。

本批不需要改install_location长度。全历史超长/空地址/串货仍待各自裁决。结构及数据清理仍须在正式承接和依赖核查完成后执行。

通过现有迁移/同步及安装Owner纠正已有948条，保留ID、绑定和原值；按已批准转换初始化安装历史，928个空位置调用资产位置Owner专用保护入口，只允许填空或同一安装重放，不能覆盖其他当前记录。先验收正式查询及重复运行，再核查并清理本任务的21个重复字段和39列来源事实表；原库、不可变来源证据及原运行回执保留。20个较新位置逐项保持，普通安装历史、授权、状态机及已生效安装唯一性保持。发现后续人工变化或依赖冲突阻断，不能仅凭历史标记删除。

规划时的受控只读证据：installation-existing-record-whole-table-planning-20261010.json（全部原行、现有结构、3个账号精确关联及6111条超长统计）；installation-history-association-planning-baseline-20261010.json；installation-current-location-boundary-readonly-20261010.json。

## 2026-10-10 本批实施与验收

- 6个非空项目批次、948条完整21列来源，经既有迁移台账、PROJ校验、IMP安装Owner及AST位置Owner承接；4个无安装来源的项目未制造记录。正式安装ID保持原值，原创建人与3个正式SYS账号精确关联，更新人全部保持NULL。原NULL更新时间使用原创建时间。没有调用当前任务或审批完成动作。
- 20条较早安装取较新安装生效日期作为结束日期，原较新安装、设备投影逐字段不变；928条补入当前安装与设备位置。928台设备只改变位置投影、版本及既有数据库自动更新时间，创建人/创建日期/更新人及其余档案字段不变。20台设备所有字段不变。
- 原298条其他安装、127条原位置历史、地址目录、项目、实施范围和Flyway历史保持。948条再次预览均UNCHANGED，6个命令返回REPLAY_COMPLETED，重放不写业务数据。
- 依赖核查无外键、视图、触发器、存储过程或事件引用任务来源事实表；21列在其他298条安装中均无业务值，既有索引未引用。备份旧DDL和值后撤回21列及imp_installation_address_source_fact。最终imp_eng_installation保留既有32列、1246条正式记录，生成表达式和唯一约束不改；没有新增表、列。
- 清理后948条正式安装历史分页、普通安装完成分页、948条UNCHANGED预览及6个命令重放再次通过。设备真实页面验证新补位置及较早历史/较新当前位置并存，详情原创建时间和NULL更新人可查，清理后重开仍可查询。
- 位置保护额外修正：锁查询读取完整位置投影，填空前阻断其他当前位置及无历史行但已有投影的情形；同一安装重放要求与当前位置历史精确一致。实际XML执行2项及位置Owner10项测试通过。原授权、普通安装修改/删除/完成规则和租户、版本保护继续保留。
- 前端9项设备来源记录测试通过，安装用例按用户裁决验证完成状态、当前/历史边界、日期和NULL更新人；原软件分页、失败重试及过期请求保护用例保留。位置生效日期格式化显示，设备空名称不显示null，AST位置记录ID不误标为安装记录ID。构建、相关ESLint及真实浏览器验收通过。

证据位于任务私有工作目录：verified-installation-record-formal-carry-20261010.json、installation-tables-before-approved-retirement-20261010.json、verified-installation-approved-table-retirement-20261010.json、verified-installation-after-retirement-20261010.json、verified-installation-browser-after-retirement-20261010.txt、verified-newer-installation-browser-20261010.txt。旧来源和迁移回执未覆盖。

最终SQL定义已撤回本任务的安装镜像及来源事实表。已执行版本原文和数据库Flyway历史留在受控证据中；整套基线对齐仍未完成，未执行Flyway repair。全历史空/超长地址、串货关系及首次迁入路径还需按业务边界完成，不能把本批纠正验收当作全历史上线完成。
