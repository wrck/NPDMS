# 软件版本维护及一次性历史承接：整表候选方案

状态：WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS。用户已回复“31列及旧八项扩展整表按建议”，批准以下31列、九项新增、八项正式扩展、初始记录用途及整表处理规则，不再重复申请。此前候选措辞保留设计讨论追溯，以下表以本次整表裁决为准。已建立批准的31列正式历史表，正常手工保存、重开和比较已完成真实页面验证。原批次048的11列结构以及659条旧记录、6条批次的限定纠正均已另行批准；旧635条事件不包含在纠正中。659＋6尚未执行，须先完成正常日志功能验收；实际缺失的材料version列正等待同表裁决。

## 已明确的业务边界

软件版本由项目人员手工维护或上传日志自动回填，与ITR无关；BOOT取Basic Bootloader。按人工登记/日志接收时间保护较新当前值，迟到旧日志保留解析历史，缺失解析字段保留当前值。先完成正常功能，再一次性迁入旧历史，之后不持续同步旧系统。Bak是变更前版本备份，Change标识是否变更。

用户要求具名实体列，前次ast_device_version.before_snapshot/after_snapshot方案撤回；ast_device_network_version_event也已被排除为正式业务权威历史。现有日志上传、解析、关联、回填及解析历史直接复用；经审查的设备维护请求、Service和版本页面没有手工软件维护入口。主工作区后续解析增量尚未提交，接入先完成提供方交接，不复制未交接代码另建Owner。

当前值直接复用ast_device_network_version全部26列。正式历史名称ast_device_software_version_record：物理整理本任务V414创建的ast_device_software_observation，不在其旁再建重复表。复用/整理22列、撤回12镜像、将8个旧备份/变化属性转正式实体扩展、新增9列，最终31列。39列方案已撤回；31列方案的更名、类型、可空性、索引及业务用途已获本次整表批准，按本方案实施。

## 当前版本：既有26列直接复用

| 既有列 | 用途 |
|---|---|
| id | 当前实体主键 |
| tenant_id | 租户隔离 |
| device_sn | 正式设备SN |
| conp_version | CONP原文 |
| conp_type | CONP类型 |
| conp_series | CONP系列 |
| conp_mark | CONP标记 |
| boot_version | BOOT，日志取Basic Bootloader |
| cpld_version | CPLD |
| pcb_version | PCB |
| customized | 定制标记 |
| release_date | 已有发布日期，不存executeTime |
| version_desc | 已有版本说明 |
| effective_from | 当前值采用的登记/日志接收依据时间，不是物理升级完成时间 |
| source_system | 真实维护来源，手工候选PLATFORM_MANUAL，不冒充日志 |
| source_key | 沿现有来源定位 |
| source_version | 真实来源版本，不伪造解析器版本 |
| source_updated_at | 沿既有来源时间 |
| synced_at | 现有处理元数据，不作为业务发生时间 |
| sync_status | 沿既有投影处理含义，不作为升级状态 |
| version | 现有并发版本 |
| creator | 原创建人不被更新覆盖 |
| create_time | 原创建时间不被更新覆盖 |
| updater | 实际本次处理人员 |
| update_time | 实际处理时间 |
| deleted | 沿既有含义 |

本表新增列0、类型/可空性变更0，现有技术元数据不清理。既有release_date/version_desc尚未映射当前Java实体；批准使用时仅补同名映射，不新增物理列。

## 正式历史：候选全部31列

复用指V414观察表已有物理列，不表示该表已经获业务批准。正常维护在服务端强制正式设备及项目授权校验；历史原NULL不得伪造。9项新增明确标记。

| 序号 | 候选列 | 类型/可空性 | 物理处理 | 用途 |
|---|---|---|---|---|
| 1 | id | BIGINT NOT NULL | 复用 | 正式记录ID，保护现存ID |
| 2 | tenant_id | BIGINT NOT NULL | 复用 | 租户 |
| 3 | device_id | BIGINT NULL | 新增1 | 精确设备；正常必填，旧未关联保留SN并登记待关联 |
| 4 | device_sn | VARCHAR(100) NULL | bar_code更名并扩长 | 记录时SN原文，不改设备主档SN |
| 5 | project_id | BIGINT NULL | 复用，允许原NULL | 历史项目关联，不改变当前归属 |
| 6 | previous_record_id | BIGINT NULL | 新增2 | 操作前正式记录，不按日期猜前记录 |
| 7 | conp_version | VARCHAR(255) NULL | conp更名并扩长 | 完整CONP值 |
| 8 | conp_type | VARCHAR(100) NULL | 复用 | CONP类型 |
| 9 | conp_series | VARCHAR(100) NULL | 复用 | CONP系列 |
| 10 | conp_mark | VARCHAR(255) NULL | 复用 | CONP标记 |
| 11 | boot_version | VARCHAR(255) NULL | boot更名并扩长 | BOOT值 |
| 12 | cpld_version | VARCHAR(255) NULL | cpld更名并扩长 | CPLD值 |
| 13 | pcb_version | VARCHAR(255) NULL | pcb更名并扩长 | PCB值 |
| 14 | customized | BIT NULL | 新增3 | 完整组合的定制标记，沿当前实体含义 |
| 15 | release_date | DATE NULL | 新增4 | 完整组合的发布日期 |
| 16 | version_desc | VARCHAR(1000) NULL | 新增5 | 完整组合的版本说明 |
| 17 | recorded_at | DATETIME(3) NULL | 新增6 | 登记/日志接收依据时间，旧取createTime；缺失不编造 |
| 18 | data_source | VARCHAR(32) NOT NULL | 新增7 | 候选PLATFORM_MANUAL、DEVICE_LOG、DPPMS、CURRENT_BASELINE |
| 19 | parsed_fact_id | BIGINT NULL | 新增8 | 真实ast_device_log_parsed_fact引用，手工/旧登记为空 |
| 20 | request_key | VARCHAR(180) NULL | 新增9 | 正常维护请求防重，旧ID归迁移绑定 |
| 21 | change_batch_id | BIGINT NULL | log_id更名并转BIGINT | 旧登记批次精确绑定，048的11列结构已批准 |
| 22 | contract_no | VARCHAR(100) NULL | 复用 | 原合同号 |
| 23 | product_code | VARCHAR(25) NULL | item_code更名 | 原产品编码 |
| 24 | execute_time | DATE NULL | 复用 | 原DATE，不直接作为升级完成时间 |
| 25 | data_state | INT NULL | datastate更名 | 原登记有效标记，不映射deleted |
| 26 | custom_info | JSON NULL | 复用 | 原JSON完整对象，不包住其他未映射字段 |
| 27 | creator | VARCHAR(64) NULL | 复用 | 实际人员/精确原用户绑定，原NULL保持 |
| 28 | create_time | DATETIME(3) NOT NULL | 复用 | 正常产生时间；旧原创建时间 |
| 29 | updater | VARCHAR(64) NULL | 复用 | 原更新人，NULL保持，历史无编辑入口 |
| 30 | update_time | DATETIME(3) NOT NULL | 复用 | 原更新时间，NULL用原创建时间初始化 |
| 31 | deleted | BIT NOT NULL | 复用 | 沿既有含义，无历史删除入口 |

新增9列：device_id、previous_record_id、customized、release_date、version_desc、recorded_at、data_source、parsed_fact_id、request_key。历史表没有同义列，当前表的同义值需要随每次历史组合保存；不能仅查询不断变化的当前值冒充当年历史。

用户补问“完整组合”的用途：核心指同一登记时刻的CONP、BOOT、CPLD、PCB四项值。例如仅将CONP由A改为B，其余为1/2/3，则前记录为A/1/2/3，后记录为B/1/2/3；可直接还原后状态并计算只有CONP变化。previous_record_id保存前后关联，不替代每条记录自身的四项值。customized、release_date、version_desc不属于这四项核心版本；用途解释时这些附属属性仍为候选，不能仅以“完整组合”为由认定必须新增；随后用户明确批准31列整表，包含这三列及其含义，不再重复确认。


## 正常比较与旧Bak/Change承接

新操作使用previous_record_id与具名完整版本组合比较，不在正常主表重复保存Bak/Change。旧八属性通过已有正式实体扩展承接；这项用途已获本次软件历史整表批准，不是沿用产品目录批准。

最新实际原表核对：本批659行中，8行的四组Bak都包含“->”版本串，每组原Change均与直接比较完整Bak/当前值的结果不同。旧页面softVersion.jsp第169—203行会将已有备份与上次当前版本串接，同时为本次输入独立保存Change；因此不能把完整Bak串当一个版本号，或重新计算覆盖原Change。这不改变用户明确的业务含义：Bak是变更前备份，Change标识本次是否变更。

候选正式扩展Owner=AST，实体类型=SoftwareVersionRecord，entity_id为正式历史主键，使用既有EntityExtensionApi由AST Owner读写/查询。八个明确属性分别发布、校验、只读，保留NULL、空串和原值，不保存整个来源行；正常记录不写这些扩展属性。不能用迁移快照、来源绑定或未对用户开放的JSON代替该正式业务承接。

| 原字段 | 候选正式扩展属性 | 既有类型 | 用途 |
|---|---|---|---|
| conpBak | SoftwareVersionRecord正式扩展.conp_bak | TEXT，可空 | 原CONP变更前备份文字，含原串 |
| conpChange | SoftwareVersionRecord正式扩展.conp_change | NUMBER，可空 | 原CONP标志0/1，不重算 |
| bootBak | SoftwareVersionRecord正式扩展.boot_bak | TEXT，可空 | 原BOOT备份文字 |
| bootChange | SoftwareVersionRecord正式扩展.boot_change | NUMBER，可空 | 原BOOT标志0/1 |
| cpldBak | SoftwareVersionRecord正式扩展.cpld_bak | TEXT，可空 | 原CPLD备份文字 |
| cpldChange | SoftwareVersionRecord正式扩展.cpld_change | NUMBER，可空 | 原CPLD标志0/1 |
| pcbBak | SoftwareVersionRecord正式扩展.pcb_bak | TEXT，可空 | 原PCB备份文字 |
| pcbChange | SoftwareVersionRecord正式扩展.pcb_change | NUMBER，可空 | 原PCB标志0/1 |

既有plt_entity_extension_definition全部12列保持：id、tenant_id、owner_module、entity_type、revision_no、source_form_revision_id、fields_json、creator、create_time、updater、update_time、deleted。既有plt_entity_extension_value全部14列保持：id、tenant_id、owner_module、entity_type、entity_id、revision_id、definition_revision_id、values_json、version、creator、create_time、updater、update_time、deleted。本方案新增扩展物理列0；只建议新增这八个明确业务属性定义。类型/结构来源已核实；具体属性配置、SPI接入及只读业务查询已获批、待实现，不能当作已经具备的软件历史功能。

界面分别展示系统前后记录对比及原备份/原标志，不能混为一项。一次性迁入旧行previous_record_id保持NULL，不按ID/日期猜链，也不将备份串拆成无发生时间的伪造升级事件。正式扩展写入、查询及逐值核验通过前，八个原物理列和已有659行不得清理。

首次正常维护若当前值已有而正式历史无对应记录，候选同一事务先记录真实当前组合作为CURRENT_BASELINE，再登记本次值并引用它；全为具名列。初始recorded_at取既有effective_from，缺失保持NULL，create_time为实际产生时间，不伪装历史升级/人工/日志操作。原来没有当前值则前记录为空，不造空白版本。该初始记录用途已随整表批准。

## 全部28原字段

| 原字段 | 正式承接/处理 |
|---|---|
| id | INT/PLT绑定，技术ID不新增镜像 |
| projectId | project_id，精确项目绑定，原NULL保持 |
| logId | change_batch_id，正式批次048的11列结构已批准，不当parser/file ID |
| contractNo | contract_no |
| itemCode | product_code |
| barCode | device_sn保留原文；device_id精确匹配，未关联登记待处理 |
| conp | conp_version |
| conpType | conp_type |
| conpSeries | conp_series |
| conpMark | conp_mark |
| conpBak | SoftwareVersionRecord正式扩展.conp_bak |
| conpChange | SoftwareVersionRecord正式扩展.conp_change |
| cpld | cpld_version |
| cpldBak | SoftwareVersionRecord正式扩展.cpld_bak |
| cpldChange | SoftwareVersionRecord正式扩展.cpld_change |
| boot | boot_version，旧字符串不重新解析 |
| bootBak | SoftwareVersionRecord正式扩展.boot_bak |
| bootChange | SoftwareVersionRecord正式扩展.boot_change |
| pcb | pcb_version |
| pcbBak | SoftwareVersionRecord正式扩展.pcb_bak |
| pcbChange | SoftwareVersionRecord正式扩展.pcb_change |
| executeTime | execute_time，原DATE保持 |
| datastate | data_state，不映射删除/升级完成 |
| createTime | create_time及recorded_at；全量若原创建时间也NULL，阻断待裁决 |
| createBy | creator，精确绑定/原NULL保持；未知非空人员另行裁决 |
| updateTime | update_time，原NULL沿已决规则用原createTime初始化，列明原NULL |
| updateBy | updater，同原创建人规则 |
| customInfo | custom_info完整JSON |

原类型/可空性完整核对见既有软件历史表审查的28列清单。无原业务字段自行排除，NULL与空字符串区分。旧表没有previous_record_id、customized、release_date、version_desc、parsed_fact_id、request_key，保持NULL；data_source=DPPMS。一次性迁入不覆盖当前软件/设备归属，不生成升级完成、任务或审批事实。

## 当前42列全部物理处理

| 当前字段 | 候选处理 |
|---|---|
| id、tenant_id、project_id | 复用，project_id允许历史原NULL |
| log_id | change_batch_id，BIGINT及精确正式批次绑定 |
| contract_no | 复用 |
| item_code | product_code |
| bar_code | device_sn，VARCHAR(100) |
| conp、boot、cpld、pcb | 各更名*_version，VARCHAR(255) |
| conp_type、conp_series、conp_mark | 复用 |
| conp_bak、conp_change、boot_bak、boot_change、cpld_bak、cpld_change、pcb_bak、pcb_change | 转上述正式实体扩展；完整读写及原值核验后再清理物理列 |
| execute_time | 复用 |
| datastate | data_state |
| custom_info | 复用 |
| creator、create_time、updater、update_time、deleted | 复用并保护原审计 |
| source_project_key、source_system、source_key、source_version、source_read_at、source_checksum、source_id、source_project_id | 撤回8技术镜像，既有INT/PLT绑定、原材料和回执不改写 |
| source_created_at、source_created_by、source_updated_at、source_updated_by | 先核实迁入正式审计，再撤回4镜像 |

合计复用/整理22列、撤回12镜像列、8业务列转正式扩展、新增9列，最终31列。撤列前逐字段检查全部现存记录、依赖、来源绑定及后续修改；当前659观察记录和635来源事件均先保护，结构批准不授权覆盖后续修改或纠正635事件。具体纠正和退役先生成预览并另经必要限定裁决。SQL/migrations待最终结构批准再整理，已执行原文及Flyway运行证据不可改写。

## 正常Owner、约束及验收

AST事务内锁定并重验租户、项目编辑授权、设备归属及当前版本，确定真实前记录、追加完整历史、更新当前值和设备CONP投影，失败整体回滚。前记录须同租户同设备、不自引用。历史只追加，无编辑或删除入口。

候选索引：(tenant_id,device_id,recorded_at,id)、(tenant_id,project_id,recorded_at,id)；租户内非NULL request_key唯一；租户内非NULL parsed_fact_id唯一。同请求异内容拒绝，同解析重放不重复写入。不能按SN+软件值去重，原重复行完整保留。不新增状态机、审批或升级完成判定。

早日志迟到只保留已有解析事实，不进入当前采用链；新日志只回填已解析字段。原始日志和解析事实不可变。正常审计为实际人员/运行时间，迁移审计为原事实，框架不能将旧NULL填成管理员。CONP原文及派生属性沿已核实解析/维护契约，不臆造新的解析规则。

先落实正常业务Owner/API和主工作区增量交接，完成手工入口及既有日志接入的真实浏览器保存、刷新、历史比较验收，再执行一次性迁移。失败验证覆盖租户/授权、并发过期、请求异内容、跨设备前引用、事务回滚、初始前值保护、迟到日志、部分解析及迁移重跑防重及主表/扩展共同回滚。原28字段、扩展八属性、空串/NULL和全部审计逐项核对，未知关联待裁决。

证据复用：software-whole-tables-readonly-20261010.json、software-existing-history-reuse-readonly-20261010.json、existing-log-version-function-readonly-20261010.json、version-history-table-inventory-readonly-20261010.json以及旧ShipmentInfo/ProjectSoftVersionEntity的Bak/Change注释。本轮只改方案，未再次写入软件数据。

新增只读核对证据：software-backup-flag-comparison-readonly-20261010T171953.json，writes=0，659来源行；四组各8条链/直接比较不等于原标志。此核对没有批准清理字段。
