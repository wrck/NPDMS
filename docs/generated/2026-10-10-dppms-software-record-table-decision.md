# 设备软件版本登记整表裁决

状态：RETURNED_FOR_EXISTING_HISTORY_REUSE / BLOCKED_BY_SPEC。2026-10-10用户要求核实本表创建时间，并优先复用既有软件版本更新历史表。下述30列方案未批准，停止实施；改按[既有历史表完整复核及整表候选](2026-10-10-dppms-existing-software-history-table-decision.md)提交裁决。原方案保留用于追溯，不能作为结构或写入授权。

当前ast_device_software_observation是本任务先前创建、尚未得到整表结构批准的表，当前42列、659行。建议整理为正式设备软件版本登记实体，最终30列，新增物理列0。撤回8个技术镜像及4个来源审计镜像；技术身份归既有INT/PLT，原审计迁入同义正式审计字段。现存记录及全部原值核验完成后才清理重复列，原来源证据和旧回执不改写。

## 业务性质与既有表对照

旧系统软件变更保存时先更新旧批次latest及旧清单datastate，再保存整套设备清单，包括未变化行。全历史1570643行中1439315行四标志全0，只有131328行存在差异登记。executeTime原模型注释明确只在发生更新时有意义；旧保存逻辑还会将当日赋给未变化行，不能将这个日期当作每台设备实际升级完成时间。四个Bak可含沿用变化链，保存原文、不拆成新的执行事实。

既有ast_device_network_version_event在本库有32列/635行，ast_device_network_version有26列/425行。DeviceNetworkVersionService.apply会更新当前主档和设备投影，不能用它承接未变化清单。既有事件29个原字段中有当前版本、事件时间、接收时间、版本描述、变更原因/人员、撤销/映射/同步状态及技术来源，没有整项目批次关系和四个Bak、四个差异标志；不能拿revoked当datastate、event_time当executeTime或source_version当项目changeVersion。

本建议保留635既有事件、425当前主档及设备当前版本，不修改、删除或再生成升级事件；事件表本任务加入的3列另作整表审查，本方案不默认批准清理。既有ast_device_version保存设备档案前后快照及设备版本号，没有同义软件清单/批次字段，不能以该JSON快照代替本业务实体。

全历史已核logId无缺批次、无跨项目引用；四个差异标志全部为0/1。原latest仅表示旧批次是否为该系统最新登记，不激活平台任务，不生成审批/升级完成，不由本轮迁入重算。

## 建议最终实体列

| 正式字段 | 类型 | 可空 | 含义 |
|---|---|---|---|
| id | BIGINT | 否 | 保留现存正式ID |
| tenant_id | BIGINT | 否 | 正式租户 |
| project_id | BIGINT | 否 | 原项目精确绑定 |
| change_batch_id | BIGINT | 是 | log_id更名并由INT扩大为BIGINT，精确关联正式批次ID；旧logId由迁移绑定保留 |
| contract_no | VARCHAR(100) | 是 | 原合同号，不按文本猜配正式合同 |
| product_code | VARCHAR(25) | 是 | 原itemCode，不按名称猜配目录 |
| device_sn | VARCHAR(25) | 是 | 原barCode，保留完整序列号 |
| conp_version | VARCHAR(100) | 是 | 原完整版本文字，不更新当前主档 |
| conp_type | VARCHAR(100) | 是 | 原版本分类/系列/标记 |
| conp_series | VARCHAR(100) | 是 | 原版本分类/系列/标记 |
| conp_mark | VARCHAR(255) | 是 | 原版本分类/系列/标记 |
| conp_bak | VARCHAR(255) | 是 | 原备份版本或沿用链，NULL/空串原样 |
| conp_change | INT | 是 | 原人工差异标志0/1，不独立证明执行完成 |
| cpld_version | VARCHAR(100) | 是 | 原完整版本文字，不更新当前主档 |
| cpld_bak | VARCHAR(255) | 是 | 原备份版本或沿用链，NULL/空串原样 |
| cpld_change | INT | 是 | 原人工差异标志0/1，不独立证明执行完成 |
| boot_version | VARCHAR(100) | 是 | 原完整版本文字，不更新当前主档 |
| boot_bak | VARCHAR(255) | 是 | 原备份版本或沿用链，NULL/空串原样 |
| boot_change | INT | 是 | 原人工差异标志0/1，不独立证明执行完成 |
| pcb_version | VARCHAR(100) | 是 | 原完整版本文字，不更新当前主档 |
| pcb_bak | VARCHAR(255) | 是 | 原备份版本或沿用链，NULL/空串原样 |
| pcb_change | INT | 是 | 原人工差异标志0/1，不独立证明执行完成 |
| execute_time | DATE | 是 | 原登记执行日期，DATE原样；不宣称物理升级完成 |
| data_state | INT | 是 | 原datastate整数0/1，登记有效性；不映射删除/撤销 |
| custom_info | JSON | 是 | 原JSON完整保存 |
| creator | VARCHAR(64) | 是 | 已核人员绑定正式用户；无法核实时保留原编码，原NULL保持NULL |
| create_time | DATETIME(3) | 否 | 原创建时间 |
| updater | VARCHAR(64) | 是 | 同上，原NULL保持NULL |
| update_time | DATETIME(3) | 否 | 原更新时间；NULL时建议以各自原创建时间初始化，原NULL列明 |
| deleted | BIT(1) | 否 | 正式软删除标记，不据datastate/latest自动删除 |

## pm_project_soft_version全部原字段

| 原字段 | 原类型/可空 | 正式承接及处理 |
|---|---|---|
| id | int(11)/NO | 既有INT/PLT来源绑定（技术ID） |
| projectId | int(11)/YES | project_id |
| logId | int(11)/YES | change_batch_id |
| contractNo | varchar(100)/YES | contract_no |
| itemCode | varchar(25)/YES | product_code |
| barCode | varchar(25)/YES | device_sn |
| conp | varchar(100)/YES | conp_version |
| conpType | varchar(100)/YES | conp_type |
| conpSeries | varchar(100)/YES | conp_series |
| conpMark | varchar(255)/YES | conp_mark |
| conpBak | varchar(255)/YES | conp_bak |
| conpChange | int(11)/YES | conp_change |
| cpld | varchar(100)/YES | cpld_version |
| cpldBak | varchar(255)/YES | cpld_bak |
| cpldChange | int(11)/YES | cpld_change |
| boot | varchar(100)/YES | boot_version |
| bootBak | varchar(255)/YES | boot_bak |
| bootChange | int(11)/YES | boot_change |
| pcb | varchar(100)/YES | pcb_version |
| pcbBak | varchar(255)/YES | pcb_bak |
| pcbChange | int(11)/YES | pcb_change |
| executeTime | date/YES | execute_time |
| datastate | int(11)/YES | data_state |
| createTime | datetime/YES | create_time |
| createBy | varchar(25)/YES | creator |
| updateTime | datetime/YES | update_time |
| updateBy | varchar(25)/YES | updater |
| customInfo | json/YES | custom_info |

技术ID仅用于绑定，所有原业务值都有正式字段；人员和日期按上述审计规则待裁决。

## 当前物理列逐项纠正

| 当前字段 | 处理 |
|---|---|
| id | 直接复用 |
| tenant_id | 直接复用 |
| project_id | 直接复用 |
| source_project_key | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_system | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_key | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_version | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_read_at | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_checksum | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_id | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| source_project_id | 撤回技术镜像；INT/PLT记录来源身份、校验和及采集回执 |
| log_id | 更名为change_batch_id，类型INT→BIGINT，值转换为正式批次ID |
| contract_no | 直接复用 |
| item_code | 更名为product_code |
| bar_code | 更名为device_sn |
| conp | 更名为conp_version |
| conp_type | 直接复用 |
| conp_series | 直接复用 |
| conp_mark | 直接复用 |
| conp_bak | 直接复用 |
| conp_change | 直接复用 |
| cpld | 更名为cpld_version |
| cpld_bak | 直接复用 |
| cpld_change | 直接复用 |
| boot | 更名为boot_version |
| boot_bak | 直接复用 |
| boot_change | 直接复用 |
| pcb | 更名为pcb_version |
| pcb_bak | 直接复用 |
| pcb_change | 直接复用 |
| execute_time | 直接复用 |
| datastate | 更名为data_state |
| source_created_at | 先准确迁入create_time，再撤回审计镜像 |
| source_created_by | 先准确迁入creator，再撤回审计镜像 |
| source_updated_at | 先准确迁入update_time，再撤回审计镜像 |
| source_updated_by | 先准确迁入updater，再撤回审计镜像 |
| custom_info | 直接复用 |
| creator | 直接复用；creator/updater原NULL须允许保存 |
| create_time | 直接复用 |
| updater | 直接复用；creator/updater原NULL须允许保存 |
| update_time | 直接复用 |
| deleted | 直接复用 |

## 审计、保护和迁移方案

本批659条中原updateTime及updateBy均有427条NULL，原创建人无NULL。建议update_time非空约束保持，仅NULL时以原createTime初始化；creator/updater允许NULL并保留原空值，不让框架补管理员。原日期不得换成运行时间。

按来源、现存目标、绑定和旧回执逐条核对全部字段及审计基线，任何后续修改阻断待裁决。先准确建立项目/批次关系及正式业务字段，再清理技术和审计镜像；当前SQL直接整理为获批准的最终结构，原执行文本及Flyway历史只读保留。结构纠正不等于允许接管人工修改，源码及真实数据验证另记。

与正式设备当前归属不一致时仍保留来源项目的历史登记关联，不因此改变当前设备归属或位置。全量扩大遇到未知项目、人员、批次等精确关联失败时逐项登记，不用快照代替实体，不生成虚拟设备。

证据：任务私有software-whole-tables-readonly-20261010.json、software-original-purpose-statistics-readonly-20261010.json、software-batch-relations-readonly-20261010.json，writes=0；旧ProjectDao/ProjectService及sql-map-project-config.xml的软件批次保存、清单失效和完整清单写入代码；当前DeviceNetworkVersionService.apply与四张正式表全字段核对。

## 既有软件事件和主档全部字段对照

以下保持现有数据与定义，不属于本次候选30列表的新增列授权。事件末尾3列为本任务先前加入，具体清理需另行整表裁决。

### ast_device_network_version_event

| 当前字段 | 类型/可空 | 本轮处理 |
|---|---|---|
| id | bigint/NO | 保护既有数据/定义，不替代原清单独立属性 |
| device_sn | varchar(100)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| source_device_key | varchar(128)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| source_event_key | varchar(128)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| conp_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_type | varchar(100)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_series | varchar(100)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_mark | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| boot_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| cpld_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| pcb_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| customized | bit(1)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| release_date | date/YES | 保护既有数据/定义，不替代原清单独立属性 |
| version_desc | varchar(1000)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| event_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| received_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| change_reason | varchar(500)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| changed_by | varchar(128)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| revoked | bit(1)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| mapping_status | varchar(32)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| source_system | varchar(32)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| source_version | varchar(64)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| sync_status | varchar(32)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| creator | varchar(64)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| create_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| updater | varchar(64)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| update_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| deleted | bit(1)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| tenant_id | bigint/NO | 保护既有数据/定义，不替代原清单独立属性 |
| custom_info | json/YES | 本任务加入，先保留待独立纠正；不能据存在默认批准 |
| source_log_key | varchar(128)/YES | 本任务加入，先保留待独立纠正；不能据存在默认批准 |
| source_data_state | int/YES | 本任务加入，先保留待独立纠正；不能据存在默认批准 |

### ast_device_network_version

| 当前字段 | 类型/可空 | 本轮处理 |
|---|---|---|
| id | bigint/NO | 保护既有数据/定义，不替代原清单独立属性 |
| device_sn | varchar(100)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| conp_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_type | varchar(100)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_series | varchar(100)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| conp_mark | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| boot_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| cpld_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| pcb_version | varchar(255)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| customized | bit(1)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| release_date | date/YES | 保护既有数据/定义，不替代原清单独立属性 |
| version_desc | varchar(1000)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| effective_from | datetime(3)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| source_system | varchar(32)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| source_key | varchar(128)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| source_version | varchar(64)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| source_updated_at | datetime(3)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| synced_at | datetime(3)/YES | 保护既有数据/定义，不替代原清单独立属性 |
| sync_status | varchar(32)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| version | int unsigned/NO | 保护既有数据/定义，不替代原清单独立属性 |
| creator | varchar(64)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| create_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| updater | varchar(64)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| update_time | datetime(3)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| deleted | bit(1)/NO | 保护既有数据/定义，不替代原清单独立属性 |
| tenant_id | bigint/NO | 保护既有数据/定义，不替代原清单独立属性 |
