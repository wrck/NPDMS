# 项目软件变更批次整表裁决

状态：APPLIED_AND_VERIFIED。用户已回复“11列及上述处理整表批准”，批准下述11列、原9列映射、NULL审计及日期处理、撤回12个镜像和6条记录的完整保护核查。批次正式11列及6条限定审计纠正已完成并逐值验证，不再重复确认。正常软件维护和正式日志来源由另外已批准的31列历史方案承接，本批次仅保存旧项目登记事实。

当前ast_device_software_change_batch由本任务先前创建，本次已获整表结构批准，原23列、6行。已整理为正式项目软件变更批次实体，最终11列，新增物理列0。撤回8个技术镜像及4个来源审计镜像；技术身份归既有INT/PLT，原审计迁入同义正式审计字段。现存记录及全部原值核验完成后才清理重复列，原来源证据和旧回执不改写。

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
| change_version | VARCHAR(10) | 是 | 原项目登记批次版本文字 |
| change_remark | VARCHAR(255) | 是 | 原批次备注 |
| latest | INT | 是 | 原最新批次标记0/1；不重算当前平台状态 |
| creator | VARCHAR(64) | 是 | 已核人员绑定正式用户；无法核实时保留原编码，原NULL保持NULL |
| create_time | DATETIME(3) | 否 | 原创建时间 |
| updater | VARCHAR(64) | 是 | 同上，原NULL保持NULL |
| update_time | DATETIME(3) | 否 | 原更新时间；NULL时建议以各自原创建时间初始化，原NULL列明 |
| deleted | BIT(1) | 否 | 正式软删除标记，不据datastate/latest自动删除 |

## pm_project_soft_change_logs全部原字段

| 原字段 | 原类型/可空 | 正式承接及处理 |
|---|---|---|
| id | int(11)/NO | 既有INT/PLT来源绑定（技术ID） |
| projectId | int(11)/YES | project_id |
| changeVersion | varchar(10)/YES | change_version |
| changeRemark | varchar(255)/YES | change_remark |
| latest | int(11)/YES | latest |
| createBy | varchar(25)/YES | creator |
| createTime | datetime/YES | create_time |
| updateBy | varchar(25)/YES | updater |
| updateTime | datetime/YES | update_time |

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
| change_version | 直接复用 |
| change_remark | 直接复用 |
| latest | 直接复用 |
| source_created_by | 先准确迁入creator，再撤回审计镜像 |
| source_created_at | 先准确迁入create_time，再撤回审计镜像 |
| source_updated_by | 先准确迁入updater，再撤回审计镜像 |
| source_updated_at | 先准确迁入update_time，再撤回审计镜像 |
| creator | 直接复用；creator/updater原NULL须允许保存 |
| create_time | 直接复用 |
| updater | 直接复用；creator/updater原NULL须允许保存 |
| update_time | 直接复用 |
| deleted | 直接复用 |

## 审计、保护和迁移方案

本批6条中原updateTime及updateBy均有5条NULL，原创建人无NULL。建议update_time非空约束保持，仅NULL时以原createTime初始化；creator/updater允许NULL并保留原空值，不让框架补管理员。原日期不得换成运行时间。

按来源、现存目标、绑定和旧回执逐条核对全部字段及审计基线，任何后续修改阻断待裁决。先准确建立项目/批次关系及正式业务字段，再清理技术和审计镜像；当前SQL直接整理为获批准的最终结构，原执行文本及Flyway历史只读保留。结构纠正不等于允许接管人工修改，源码及真实数据验证另记。

与正式设备当前归属不一致时仍保留来源项目的历史登记关联，不因此改变当前设备归属或位置。全量扩大遇到未知项目、人员、批次等精确关联失败时逐项登记，不用快照代替实体，不生成虚拟设备。

证据：任务私有software-whole-tables-readonly-20261010.json、software-original-purpose-statistics-readonly-20261010.json、software-batch-relations-readonly-20261010.json，writes=0；旧ProjectDao/ProjectService及sql-map-project-config.xml的软件批次保存、清单失效和完整清单写入代码；当前DeviceNetworkVersionService.apply与四张正式表全字段核对。
