# 已撤回的设备地址新实体方案

状态：SUPERSEDED / NEW_ENTITY_PROPOSAL_WITHDRAWN。用户最新指出“有安装历史记录表，为啥要在新建”。既有正式运行表为 imp_eng_installation，已有项目、设备、安装位置、地址引用及快照、审计等字段；新建 ast_device_address_record 或将任务来源事实表改名为该实体的方向撤回。用户此前的12列回复不能继续作为新实体实施依据。没有执行该方案的建表、改名或地址整改写入。

当前方向：复用 imp_eng_installation 原有32列，逐项对照原表21列；产品、物流继续复用已承接的正式发货、目录和装箱单字段。本任务追加的21列及来源事实表须在正式承接、依赖和全表保护核查完成后清理，不能先删数据。旧表没有安装状态、安装完成日期和地址更新时间，相关冲突须整表提交用户裁决，不自行生成安装事实或改写当前位置。以下内容仅保留为已撤回方案的过程记录，不是当前实施依据。

用户已裁决“承接正式历史地址关联，保留当前位置”。建议由资产Owner承接正式 `ast_device_address_record`，将本任务此前未经整表裁决的 `imp_installation_address_source_fact` 整理为此实体。名称、结构及撤回仍等待本次整表裁决；不将来源事实表或迁移快照作为最终业务承接。

数据库当前记录：来源事实表由V414于2026-10-09 00:29:17创建，948条记录于01:05:35.049—01:09:34.601写入，当前39列。以下12列是该本任务新表的精简提案，不是系统原有正式表结构。用户已批准历史地址业务归属；复用真正既有模型的可行性必须先讲明，不能以“没有新增列”回避新业务实体的事实。

## 既有实体复用依据

- `ast_address` 是地址目录，现有24列保持。`AssetLocationApiImpl.maintainAddress` 原地修改目录、递增版本；`getAddress` 只能查询当前值和校验版本，不能返回旧版。历史关联仅存地址ID会丢失原文。
- 地址业务快照已有正式消费模型：`imp_eng_installation.address_snapshot`、`proj_project_site.address_snapshot`。本方案复用当前事实表已有 `address_snapshot JSON`，保存地址业务值，不存来源整行、技术身份、物流或迁移回执。原 `installAddress` 必须与快照的 `fullAddress` 逐字一致，保留NULL、空串及空白。
- `ast_device_location` 是位置生效记录。它会结束当前记录并更新设备投影，且依赖正式安装动作及生效日期；本次没有这些来源事实，因此保持其20列及全部当前记录。
- 948条来源均唯一匹配正式设备及该项目；按序列号关联与既有资产位置模型一致，不再重复保存 equipment_id。设备后续归属变化不改写旧项目关联。
- 本批948条均精确关联正式发货记录；其产品、合同、物流、装箱单业务值已逐项核对相同。不在地址表重复这些属性，不增加发货关系物理列。原行与已存在发货记录的技术映射归既有迁移绑定，实际业务值由正式发货、目录及装箱单承接；跨模块资料通过Owner API查询。全历史无法精确关联的记录阻断待裁决，不任选发货行。

## 修订后12列候选（全部列一起裁决，无新增物理列）

| 列 | 类型 | 可空 | 处理及业务含义 |
|---|---|---|---|
| id | BIGINT | 否 | 复用现有记录ID；不取原自增ID代替迁移身份 |
| tenant_id | BIGINT | 否 | 复用既有租户字段 |
| project_id | BIGINT | 是 | 原项目的精确正式绑定；无法绑定时待关联，不挂靠当前设备项目 |
| device_sn | VARCHAR(100) | 否 | 原 barcode；source_device_sn 更名，沿用正式资产SN命名 |
| address_id | BIGINT | 是 | 复用正式地址目录引用；空地址、无法合法建目录时不伪造引用 |
| address_version | BIGINT | 是 | 保存取用目录时版本；不宣称该版本仍可从目录还原 |
| address_snapshot | JSON | 是 | 复用已有列，保存地址业务属性及原文；独立于目录后续修改 |
| creator | VARCHAR(64) | 是 | 原 createBy 精确用户绑定；未绑定原编码须列出，不默认管理员 |
| create_time | DATETIME(3) | 否 | 原 createTime，不表示安装完成或地址最后更新时间 |
| updater | VARCHAR(64) | 是 | 原 updateBy，原NULL保持NULL；不自动填管理员 |
| update_time | DATETIME(3) | 是 | 原 updateTime，原NULL保持NULL；不编造地址更新时间 |
| deleted | BIT | 否 | 复用既有逻辑删除字段 |

不新增状态、安装时间、地址生效期、当前标记或设备位置版本。不对“租户＋项目＋SN”设唯一键：同一设备的多个来源记录不能自行去重。技术身份和幂等由既有迁移绑定承接。历史业务值不由普通目录维护覆盖。

物理整改建议：现有39列保留12列（其中 source_device_sn 更名），新增物理列0，撤回27列；现有来源事实表转为正式资产地址关联表仍待用户确认。被否决的 shipment_record_id 不写入SQL或代码。原已执行迁移原文保存在受控证据，按用户要求整理当前 SQL/migrations 最终定义；Flyway基线对齐必须另外核验，不直接repair。

## 原表全部21列映射

| 原字段 | 正式承接点 / 排除或待处理原因 |
|---|---|
| id | 既有INT/PLT来源绑定；技术自增身份，不新增业务镜像 |
| projectId | project_id，精确项目绑定；未绑定保留待关联 |
| barcode | device_sn；不由设备当前归属替换历史项目 |
| itemCode | 已精确匹配的 ast_device_shipment.product_code → 正式产品目录；来源行对应的现有发货技术身份归既有迁移绑定，不增加地址表关联列 |
| itemModel | 正式产品目录业务属性；全历史1064774条均NULL，不另加地址列，出现非空重新核对 |
| itemName | 正式产品目录已批准的 DPPMS目录名称业务扩展；本批948条逐字相同 |
| receiveName | 正式发货关联装箱单 receiver_name；本批逐项一致 |
| emsNum | 正式发货关联装箱单 express_no；本批逐项一致 |
| emsCompany | 正式装箱单 carrier_name/已批准carrier_code转换；本批逐项一致，不能用名称替代编码 |
| packdate | 正式发货/装箱单发货日期；不推导安装日期 |
| contractNo | 正式发货 contract_no 及正式合同关系；不盲目去掉 -C 后缀 |
| installAddress | address_snapshot.fullAddress 原样保存，并引用合法正式地址目录；原文不可截断 |
| chProjectId | 串货转出项目关系待单独裁决；本批全部NULL，不新增地址镜像列 |
| chContractNo | 串货转出合同关系待单独裁决；本批全部NULL |
| transferProjectId | 串货接收项目关系待单独裁决；本批全部NULL |
| transferContractNo | 串货接收合同关系待单独裁决；本批全部NULL |
| transferFlag | 本批全为-1，无串货动作。全历史转出1与转入0各15条，保留待转移业务整表裁决，不丢弃或生成当前动作 |
| createTime | create_time，历史原值 |
| createBy | creator，精确人员绑定，未绑定另列 |
| updateTime | update_time，全历史原值均NULL，不用本次运行时间替代 |
| updateBy | updater，原值及NULL完整核对，不自动填充 |

发货、目录、装箱单关系的“本批一致”不能用于推断全历史一致。扩大迁移时每行比对，差异或关系歧义阻断并提交裁决，禁止通过引用吞掉不同历史业务值。

## 27列撤回及保护

来源技术镜像9列：source_project_key、source_system、source_key、source_version、source_read_at、source_checksum、source_id、source_project_id、equipment_id。前8项归既有来源绑定与不可变证据；equipment_id改由正式SN精确关联。

业务重复/待独立分域14列：install_location（原文归地址业务快照）、source_product_code、source_product_model、source_product_name、source_receiver_name、source_express_no、source_carrier_name、source_shipment_time、source_contract_no（归正式发货/目录/装箱单）；changed_project_key、changed_contract_no、transfer_project_key、transfer_contract_no、source_transfer_flag（串货业务待裁决，不能删除其非空事实）。

重复审计4列：source_created_at、source_created_by、source_updated_at、source_updated_by，原值迁入正式审计列后再撤回。

当前948条来源事实及948条“历史安装候选”须先逐条核对原值、引用、ID、审计和后续变化。正式历史地址关联与来源绑定验收后，撤回本任务制造的候选安装记录及 imp_eng_installation 的21个未批准字段，原32正式列、普通安装记录和20个当前位置保持。发现任何人工修改或依赖冲突即阻断，不能仅凭source_history标记删除。

## 扩大历史的边界

原表1064774条，24143条NULL地址、1860条空地址；没有空SN、空项目ID或空创建时间。最长地址571字符，其中5013条超过目录detail_address的512字符限制。现有目录结构不变，不截断或拼凑行政区划；完整原文由正式地址关联的业务快照保存，目录引用未能合法建立时待关联。NULL/空地址不生成当前位置或完成事实，保留其原声明值；是否作为无地址关联记录迁入应随全历史范围明确核对。

30条串货记录的项目/合同双向关系、长地址目录引用及没有精确目标的人员/发货关系，扩大历史时登记待裁决。本次只裁决该地址关联实体，不批准这些领域的额外表列。

## 证据及实施验收要求

原作用、39列与53列完整清单见 [安装地址原业务复核](2026-10-10-dppms-installation-original-purpose-audit.md)。受控证据：installation-original-purpose-readonly-20261010.json、installation-existing-business-fit-readonly-v2-20261010.json、installation-product-name-fit-readonly-v2-20261010.json、installation-current-location-boundary-readonly-20261010.json、installation-history-association-planning-baseline-20261010.json、installation-full-source-purpose-profile-readonly-20261010.json。均为只读核对；未实施本方案。

批准后通过现有迁移/同步与资产Owner执行，覆盖原NULL/空串、目录变更不改历史、错误租户/项目/绑定、人工后续变化阻断、重复运行无业务写入，并以真实页面验证948条可查询、20个当前位置及928个空当前位置保持。不得用打包或HTTP200替代上述验收。
