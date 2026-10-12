# 既有设备软件版本历史表复用整表裁决

状态：RETURNED_FOR_FORMAL_BUSINESS_DESIGN / BLOCKED_BY_SPEC。用户最新裁决“先补齐功能，再承接历史”，并明确“当前的ast_device_network_version_event也不能作为权威表进行，这不是正常业务规划产生的正式业务表”。因此本表退出正式业务承接候选，下述原44列方案停止实施。此前批准12列是针对本候选的整表方案，不自动授权在其他表增列；字段业务含义保留为重新规划输入。实际软件代码、SQL、结构和数据均未切换。635条现存记录、659条观察记录、来源证据及迁移回执保留，任何纠正或撤回另经具体预览裁决。

最新复核：已核实635条全为本任务DPPMS迁入，当前业务未接入DeviceNetworkVersionService.apply；表实际来自V110的来源事件模型，不能将存在的模型称为已运行的更新历史功能。详见[来源和真实用途核查](2026-10-10-dppms-software-history-provenance-audit.md)。新的业务模型必须先形成独立功能闭环，再承接历史，不先建迁移表。版本更新的权威来源（既定ITR同步或经用户批准的平台登记）正等待明确。

## 创建记录与复用结论

ast_device_software_observation是本任务V4的V414创建，本任务库Flyway记录2026-10-09 00:29:17成功；当前42列、659行。既有ast_device_network_version_event在V110创建，本任务库2026-09-17 20:37:35成功，原29列，目前32列、635行。ast_device_network_version为当前版本主档，26列、425行。

复用既有版本历史表，不继续建设独立观察实体。本表原29列全部核查；可同义承接的版本与审计直接使用原字段。V4的custom_info、source_log_key、source_data_state三列曾未经整表批准，本次明确按下述方案裁决。批准最终44列：原29列＋保留/整理3列＋12个新增列。新增表0。

现有DeviceNetworkVersionService.apply插入事件后还会更新当前主档及ast_device投影；历史迁入应经同一个AST Owner的独立历史承接入口，不调用当前更新动作。只保留来源登记事实，不根据四标志或executeTime生成物理升级完成事实。

## 12个候选新增物理列

全部可空、无推测默认值；用于原独立业务属性，不是技术镜像或快照。既有release_date是版本发布日期，change_reason是变更原因，version_desc是版本说明，不能代替下列字段。

| 序号 | 候选新增列 | 类型 | 原字段 | 含义与不能直接复用的原因 |
|---|---|---|---|---|
| 1 | project_id | BIGINT NULL | projectId | 经既有项目迁移绑定关联正式项目，不改变当前设备归属；既有历史表无同义字段 |
| 2 | contract_no | VARCHAR(100) NULL | contractNo | 原合同号，不按名称猜配合同；既有历史表无同义字段 |
| 3 | product_code | VARCHAR(25) NULL | itemCode | 原产品编码，不用名称代替；既有历史表无同义字段 |
| 4 | conp_bak | VARCHAR(255) NULL | conpBak | 原备份版本/沿用链，原文保存；既有历史表无同义字段 |
| 5 | conp_change | INT NULL | conpChange | 原0/1变化标志，不等于执行完成；既有历史表无同义字段 |
| 6 | cpld_bak | VARCHAR(255) NULL | cpldBak | 原备份版本/沿用链，原文保存；既有历史表无同义字段 |
| 7 | cpld_change | INT NULL | cpldChange | 原0/1变化标志，不等于执行完成；既有历史表无同义字段 |
| 8 | boot_bak | VARCHAR(255) NULL | bootBak | 原备份版本/沿用链，原文保存；既有历史表无同义字段 |
| 9 | boot_change | INT NULL | bootChange | 原0/1变化标志，不等于执行完成；既有历史表无同义字段 |
| 10 | pcb_bak | VARCHAR(255) NULL | pcbBak | 原备份版本/沿用链，原文保存；既有历史表无同义字段 |
| 11 | pcb_change | INT NULL | pcbChange | 原0/1变化标志，不等于执行完成；既有历史表无同义字段 |
| 12 | execute_time | DATE NULL | executeTime | 原登记日期原样保存，不当实际升级完成时间；既有历史表无同义字段 |

## V4三列也须本次裁决

| 当前未批准字段 | 候选处理 | 本次需裁决的理由 |
|---|---|---|
| custom_info JSON NULL | 保留正式custom_info，完整承接原customInfo | 虽已加到任务库，仍属于V4未批准新增列 |
| source_log_key VARCHAR(128) NULL | 更名change_batch_id BIGINT NULL，关联经批准的正式批次 | 原logId是批次外键；原技术ID归INT绑定，不把原ID当正式ID；批次方案仍待裁决 |
| source_data_state INT NULL | 更名data_state INT NULL，原0/1登记有效性 | revoked表示平台事件撤销，deleted表示软删除，均不能代替原登记有效性 |

## 本表全部32个当前列

| 当前字段 | 真实类型/可空 | 候选复用或处理 |
|---|---|---|
| id | bigint / NO | 保留既有正式ID；新增由正式Owner产生 |
| device_sn | varchar(100) / YES | 直接承接原barCode；保留原文，关联精确正式设备 |
| source_device_key | varchar(128) / NO | 原有来源设备身份键，维持既有唯一键语义，不新增副本 |
| source_event_key | varchar(128) / NO | 原有来源事件身份键，维持既有唯一键语义；来源原id由INT绑定 |
| conp_version | varchar(255) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| conp_type | varchar(100) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| conp_series | varchar(100) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| conp_mark | varchar(255) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| boot_version | varchar(255) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| cpld_version | varchar(255) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| pcb_version | varchar(255) / YES | 直接复用同义正式版本字段，原文字及空字符串完整承接 |
| customized | bit(1) / YES | 保持是否定制含义；原表无同义值，新历史不推测 |
| release_date | date / YES | 保持版本发布日期含义；不得承接executeTime |
| version_desc | varchar(1000) / YES | 保持版本说明含义；不得塞备份版本/JSON |
| event_time | datetime(3) / NO | 新登记候选用原createTime作为登记产生时间；现存635条取executeTime零点，不能静默纠正，须单独列单裁决 |
| received_time | datetime(3) / NO | 保留平台接收时间含义，不覆盖为原审计时间 |
| change_reason | varchar(500) / YES | 保持变更原因含义；不得塞项目/产品/备份版本 |
| changed_by | varchar(128) / YES | 保持版本变更人含义；不把原创建人直接当实际升级操作者 |
| revoked | bit(1) / NO | 保持撤销含义；不映射datastate |
| mapping_status | varchar(32) / NO | 按正式设备唯一匹配结果；不按项目名或产品名猜配 |
| source_system | varchar(32) / NO | 原有来源系统标识；保持正式Owner幂等语义 |
| source_version | varchar(64) / YES | 原有采集来源版本；不替代旧批次changeVersion |
| sync_status | varchar(32) / NO | 原有同步结果；不替代原登记有效性 |
| creator | varchar(64) / NO | 复用；建议允许NULL且无自动填充，绑定可核实用户；原NULL保持NULL，此约束尚未批准 |
| create_time | datetime(3) / NO | 复用原createTime；现存635条为本任务迁入时间，不直接覆盖，纠正需完整保护及裁决 |
| updater | varchar(64) / NO | 复用；建议允许NULL且无自动填充，绑定可核实用户；原NULL保持NULL，此约束尚未批准 |
| update_time | datetime(3) / NO | 复用原updateTime；候选仅原NULL用原createTime初始化，原NULL列明；现存635条纠正另列 |
| deleted | bit(1) / NO | 保持正式软删除；不据datastate自动删除 |
| tenant_id | bigint / NO | 保持租户隔离 |
| custom_info | json / YES | 见V4三列裁决 |
| source_log_key | varchar(128) / YES | 见V4三列裁决 |
| source_data_state | int / YES | 见V4三列裁决 |

## pm_project_soft_version全部28原字段

没有业务字段被排除；所有尚无承接点的字段列为待裁决，不转入迁移快照冒充承接。

| 原字段 | 真实类型/可空 | 候选正式承接 |
|---|---|---|
| id | int(11) / NO | INT/PLT来源绑定；既有source_event_key维持同一来源事件身份，不生成任意业务编码 |
| projectId | int(11) / YES | 候选project_id |
| logId | int(11) / YES | 候选change_batch_id，复用V4 source_log_key列更名并转BIGINT；正式批次须另经整表批准及绑定核验 |
| contractNo | varchar(100) / YES | 候选contract_no |
| itemCode | varchar(25) / YES | 候选product_code |
| barCode | varchar(25) / YES | 既有device_sn；原文保留，设备关联沿既有数据库唯一键和排序规则，不擅自改变大小写 |
| conp | varchar(100) / YES | 既有conp_version |
| conpType | varchar(100) / YES | 既有conp_type |
| conpSeries | varchar(100) / YES | 既有conp_series |
| conpMark | varchar(255) / YES | 既有conp_mark |
| conpBak | varchar(255) / YES | 候选conp_bak |
| conpChange | int(11) / YES | 候选conp_change |
| cpld | varchar(100) / YES | 既有cpld_version |
| cpldBak | varchar(255) / YES | 候选cpld_bak |
| cpldChange | int(11) / YES | 候选cpld_change |
| boot | varchar(100) / YES | 既有boot_version |
| bootBak | varchar(255) / YES | 候选boot_bak |
| bootChange | int(11) / YES | 候选boot_change |
| pcb | varchar(100) / YES | 既有pcb_version |
| pcbBak | varchar(255) / YES | 候选pcb_bak |
| pcbChange | int(11) / YES | 候选pcb_change |
| executeTime | date / YES | 候选execute_time |
| datastate | int(11) / YES | V4 source_data_state候选更名data_state；不映射revoked/deleted |
| createTime | datetime / YES | 既有create_time；候选同时用于登记event_time，原记录产生时间不冒充物理升级完成时间 |
| createBy | varchar(25) / YES | 既有creator，精确用户绑定；原NULL候选保持NULL |
| updateTime | datetime / YES | 既有update_time；原NULL候选用原createTime初始化，原NULL列明 |
| updateBy | varchar(25) / YES | 既有updater，精确用户绑定；原NULL候选保持NULL |
| customInfo | json / YES | V4 custom_info候选保留JSON，完整原对象保存 |

## 现存历史逐条复核及独立待纠正项

本批659个来源身份全部唯一；635个已存在正式历史事件，24个没有事件；既有事件全为DPPMS，本批以外事件0。635事件中的600条四变化标志均为0，与旧保存“全清单登记”相符，不能作为600次实际升级完成。版本文字没有非空差异；590条有空字符串→NULL差异，涉及3722个字段值（CONP584、BOOT/CPLD/PCB各590、类型/系列/标记各456）。原NULL与空串需区分。

635条的四个审计字段均与原表不同，当前创建/更新人均为1，时间为本任务迁入时间。event_time当前取原executeTime零点，原表createTime含准确时间。上述现存业务/审计差异不凭结构批准直接覆盖：先按来源、创建回执、绑定及目标全字段逐条生成纠正预览，出现后续变化阻断，再提交具体限定纠正裁决。不修改不可变来源材料、旧运行回执或旧审计证据。

原SN逐字匹配有8条大小写不同，经现有数据库唯一键排序规则查询，8条均唯一对应正式有效设备；这是原有SQL关系的验证，不增加猜配或改名规则。保存原SN文字，不改变设备主档SN或当前项目。其他缺失事件也须逐行满足真实身份和FK约束，不能借迁移伪造设备。

完整承接、重放、业务查询及依赖核验通过后才规划撤回659条观察表记录、观察表业务代码和SQL定义；本次没有删除授权或执行。635历史、425主档、当前设备版本作为保护基线。全历史1570643原行后续通过同一迁移/同步能力扩展，不按本批数量假称全量完成。

## 已裁决规则与待决边界

1. 原12列及V4三列曾获针对本候选的批准；用户后续明确排除本表作为正式承接目标，该方案停止，不执行上述新增或更名。
2. 原审计与日期裁决保留追溯：原NULL操作者保持NULL，原NULL更新时间用原创建时间初始化，登记时间保留原创建时间。重新规划时逐列明确正式承接点，不把该裁决扩大为历史覆盖授权。
3. 已明确先完成当前功能，再承接历史。用户已裁决“软件版本跟ITR无关，项目人员手动维护或者上传日志是通过日志自动回填”，并明确BOOT取Basic Bootloader。版本来源不再待决；其余未批准表、列仍不实施。
4. 正式业务表及完整字段方案重新提交用户整表裁决；V414批次表也不因现有6条迁入记录成为已批准实体。635条纠正和659条观察记录撤回仍需具体预览。

审查证据：software-existing-history-reuse-readonly-20261010.json、software-existing-history-sn-collation-readonly-20261010.json、software-whole-tables-readonly-20261010.json、software-original-purpose-statistics-readonly-20261010.json，全部writes=0。代码证据：V110原建表、V414观察表建表、DeviceNetworkVersionService.apply及DppmsAssetEntityImportService.importSoftware。Flyway执行历史未改动。

## 补充复核：现有日志回填已运行，不重复建设

用户指出日志回填已具备，并要求所有实施必须先有明确缺失证据。本任务工作树没有包含主工作区后续日志解析回传增量，先前只检索本工作树而判断功能缺失，结论撤回。主工作区`E:/AICoding/Projects/NPDMS`已有`DeviceArchivedLogParsingService`→`DeviceLogParsedFactApi.record`→`DeviceLogParsedFactService.updateCurrent`，通过正式Mapper更新`ast_device_network_version`及`ast_device`版本投影；历史保存在`ast_device_log_parsed_fact`，不是`ast_device_network_version_event`。

指定库重新只读核查：解析事实50条（UPDATED 42、HISTORICAL 1、VERSION_NOT_IDENTIFIED 4、未关联3）；当前版本425条，其中DEVICE_LOG 14、DPPMS 410、FAST001_TEST_ITR 1。解析任务32条、设备日志文件51条、导入批次45条。统计只证明现有代码与实际数据，不代替本轮真实浏览器验收；过去已完成的上传/版本更新/历史重开证据见主工作区`docs/generated/2026-10-06-device-log-parsing.md`及`tasks/features/F-IMP-004.md`。

相关解析服务、API和V410在主工作区仍为未提交内容；已提交V409日志归档不能等同全部解析回传已合入。本任务不复制未交接代码另造Owner、不自动提交或合并，不修改这些现有业务数据。复核证据`existing-log-version-function-readonly-20261010.json`，writes=0。

后续只处理得到明确缺失证据的部分：继续核实手工维护入口、正常业务记录模型及原28字段的同义承接；不能把解析记录中没有迁移字段，直接认定为正常业务功能缺陷。未确认项不实施；全部候选新表、新列和不明确映射仍整表提交用户裁决。

### 已有解析记录的完整24列及复用边界

下面是指定库实际结构，新增列0、约束调整0；不是将原软件登记迁入解析记录的批准方案。

| 既有实体列 | 实际类型/可空性 | 正常业务含义及承接边界 |
|---|---|---|
| id | BIGINT/必填 | 正式解析事实主键；原软件技术ID只能使用迁移绑定，不能冒充本表业务ID |
| tenant_id | BIGINT/必填 | 本租户隔离 |
| project_id | BIGINT/必填 | 受授权校验的日志项目；旧项目引用须精确转换，关联相似不构成迁入批准 |
| actor_id | BIGINT/必填 | 实际发起解析的人员；不能以旧创建人或管理员伪造解析操作者 |
| device_id | BIGINT/可空 | 经SN及项目范围核实的正式设备 |
| request_key | VARCHAR(180)/必填 | 真实解析请求幂等身份；不是原软件表技术ID |
| log_key | VARCHAR(180)/必填 | 真实日志来源引用；原logId是软件登记批次，不是此字段 |
| log_received_at | DATETIME(3)/必填 | 日志接收时间；不能将旧executeTime/createTime伪装成日志接收事实 |
| parser_task_id | VARCHAR(128)/必填 | 真实DAC解析任务；原logId不可映射到本列 |
| parser_version | VARCHAR(128)/可空 | 真实解析器发布版本；不能保存原软件版本或changeVersion |
| source_file_id | BIGINT/可空 | 正式原始日志文件引用；不等于旧软件记录或批次ID |
| log_version | BIGINT/可空 | 已归档日志的设备文件版本；不是旧latest/datastate |
| serial_number | VARCHAR(128)/可空 | 解析出的设备SN；与barCode具有相同设备识别值，不代表整条旧记录是日志解析 |
| conp_version | VARCHAR(255)/可空 | 日志实际解析CONP值 |
| boot_version | VARCHAR(255)/可空 | 日志实际解析Basic Bootloader值；当前代码与本轮用户裁决一致 |
| cpld_version | VARCHAR(255)/可空 | 日志实际解析CPLD值 |
| pcb_version | VARCHAR(255)/可空 | 日志实际解析PCB值 |
| status | VARCHAR(40)/必填 | 日志设备关联结果；不能存原datastate |
| version_status | VARCHAR(40)/必填 | 该解析是否更新当前版本；不是旧conpChange等变化标记 |
| creator | VARCHAR(64)/必填 | 正式解析创建人；原NULL审计不能被框架填充或编造 |
| create_time | DATETIME(3)/必填 | 正式解析记录产生时间 |
| updater | VARCHAR(64)/必填 | 正式解析审计；旧NULL需要合法模型，不能直接套非空约束 |
| update_time | DATETIME(3)/必填 | 正式审计时间 |
| deleted | BIT/必填 | 本表删除标记，不同于旧datastate |

原28列逐项复核的当前结果如下。这里的缺项只指旧登记字段没有得到本解析实体同义承接，不宣称现有日志功能缺失，也不授权新增字段或排除原值。

| 原字段 | 已有能力/明确不兼容点 | 当前处理 |
|---|---|---|
| id | 纯技术身份 | 既有INT/PLT绑定；保留原身份 |
| projectId | 正式项目ID及关系已有 | 精确项目绑定；具体历史实体关联待整表确认 |
| logId | 软件登记批次，非日志解析任务 | 048待裁决；不塞入parser_task_id/log_key |
| contractNo | 本解析记录无合同属性 | 正式合同关联及历史值的承接点待明确 |
| itemCode | 本解析记录无产品属性 | 正式产品关系及该登记值的承接点待明确 |
| barCode | 设备SN识别字段已有 | 保留原文字及精确设备关系，历史登记主体待确认 |
| conp | CONP当前值与正常日志记录已有 | 正式历史登记承接点待整表确认；不能伪造日志来源 |
| conpType | 当前版本已有conp_type，本解析历史无此列 | 保留原值，历史承接点待确认 |
| conpSeries | 当前版本已有conp_series，本解析历史无此列 | 保留原值，历史承接点待确认 |
| conpMark | 当前版本已有conp_mark，本解析历史无此列 | 保留原值，历史承接点待确认 |
| conpBak | 旧备份值不一定是本次实际变更前值 | 保留原语义，不套用正常before字段 |
| conpChange | 旧CONP变化标记，非解析更新状态 | 保留原值，历史承接点待确认 |
| cpld | CPLD当前值与正常日志记录已有 | 历史登记承接点待整表确认 |
| cpldBak | 旧备份值 | 保留原语义，历史承接点待确认 |
| cpldChange | 旧CPLD变化标记 | 保留原值，历史承接点待确认 |
| boot | BOOT当前值与正常日志记录已有 | 原值保留，不再解析转换旧字符串 |
| bootBak | 旧备份值 | 保留原语义，历史承接点待确认 |
| bootChange | 旧BOOT变化标记 | 保留原值，历史承接点待确认 |
| pcb | PCB当前值与正常日志记录已有 | 历史登记承接点待整表确认 |
| pcbBak | 旧备份值 | 保留原语义，历史承接点待确认 |
| pcbChange | 旧PCB变化标记 | 保留原值，历史承接点待确认 |
| executeTime | 旧登记执行日期，非日志接收时间 | 原日期保留，历史承接点待确认 |
| datastate | 旧有效标记，非解析/删除状态 | 原标记保留，历史承接点待确认 |
| createTime | 原历史创建时间 | 既有日期裁决保留，承接点待整表确认 |
| createBy | 原创建人可空 | 精确人员绑定或保留原NULL，不伪造解析actor |
| updateTime | 原更新时间可空 | 沿原创建时间初始化的已决规则；具体实体待确认 |
| updateBy | 原更新人可空 | 精确人员绑定或保留原NULL |
| customInfo | 本解析记录没有完整业务JSON属性 | 完整对象保留，承接点待整表确认 |

进一步只读核查库内全部AST软件/版本表及设备档案历史：`ast_device_version`只有一条CREATE档案记录，没有软件维护记录；不能据表名认定手工软件维护已有。主工作区设备档案保存请求及处理均不接受CONP/BOOT/CPLD/PCB，当前版本页也只读；既有版本写入者为日志回填、迁移及未接业务的来源事件服务。现有检索范围尚没有手工版本维护入口证据，本轮未实施该功能。库存证据`version-history-table-inventory-readonly-20261010.json`，writes=0；未来实施仍须以明确入口及契约核对结果为依据。
