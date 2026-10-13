# 续保记录新表及维保计算：已批准整表方案

日期：2026-10-13。工作树：C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup。本文件记录整表裁决及实施边界；结构批准不代表运行验收已完成。

最新裁决：用户已明确批准“17列及处理整表批准”。此前选择“另建续保表，重新提交方案”。因此撤回复用 ast_device_warranty_record 承接新续保的建议，不给该表新增 is_latest，也不解除其设备外键。该表现有50列及149条记录仍按此前裁决保留。以下17列及处理已实施；新增10个多合同项目222条及此前单条共223条已通过正式任务落库、重放和页面验收，见[实际验证报告](2026-10-13-dppms-renewal-multi-contract-validation.md)。旧表保持，当前维保计算投影仍未执行。

## 1. 三类业务数据的分工

| 数据 | 承接主体 | 本次边界 |
| --- | --- | --- |
| 逐次续保 | 正式新表 ast_device_warranty_renewal_record（设备续保记录） | 完整保存 fb_service 的每一条续保业务事实。表名、17列及约束已整表批准。 |
| 当前维保结果 | 既有 ast_device_warranty，21列 | 结合正式发货输入和续保记录，沿原SQL计算；不新增物理列。现有31条当前值仍受保护，不自动覆盖。 |
| 原有维保记录 | 既有 ast_device_warranty_record，50列 | 现存149条及结构保持，不将其118条旧缓存派生记录算作续保。每日计算不往记录表追加一条“历史”。 |

续保记录是独立的正式业务实体。原 fb_service.id 使用已有迁移来源绑定追溯；新表不复制 source_* 镜像、JSON整行快照、项目ID或当前设备ID，也不另建项目关联辅助表。

已确认：fb_service.warranty 是年数，乘12转月数；实际 begin_date/end_date 参与计算，月数仅作参考。规格中原“月数”的错误单位已经纠正，此裁决不再重复询问。

原 view_warranty 是每日固化的计算结果物理表，view_warranty_source 是计算视图。dailyWarrantyInfoTable 从视图填充临时表、补目录/商务名称并交换表名，newId 在固化时重新生成。它不是续保原表，不能替代 fb_service 的462,213条记录。初次核查时既有保存任务尚无独立 fb_service 承接，V414的空原型不作为本次新表方案。

原SQL证据：[实际视图及两个固化过程](2026-10-13-dppms-migration-mapping/sql/warranty-original-calculation.sql)。

## 2. 已批准新表全部17列

建议表名：ast_device_warranty_renewal_record；含义：设备逐次续保记录。业务字段沿既有AST维保模型的同义命名，但数据不再混入原维保记录表。新建物理表1张、列17个；按本次整表裁决实施。

| 序号 | 字段 | 类型 / 可空 | 含义、原字段与处理 |
| --- | --- | --- | --- |
| 1 | id | BIGINT / 否，主键 | 平台记录身份；原字符ID不覆盖正式主键。 |
| 2 | tenant_id | BIGINT / 否 | 既有任务执行上下文租户，不从来源资料猜租户。 |
| 3 | device_sn | VARCHAR(100) / 是 | 原 barcode；原样保存SN、NULL和空串，不要求先有设备主档。15条空串不参与设备或项目关联。 |
| 4 | warranty_contract_no | VARCHAR(64) / 是 | 原 con_xb，续保合同号；不替代项目销售合同号，不要求解析唯一合同主档才能保存。 |
| 5 | warranty_grade | VARCHAR(32) / 是 | 原 grade 编码；4条空串原样保留。默认等级只用于原计算查询，不回写本记录。 |
| 6 | warranty_start_date | DATE / 是 | 原 begin_date 实际开始日期。原全表均零点，DATE无损；以后非零点须报告精度冲突，不截断。 |
| 7 | warranty_end_date | DATE / 是 | 原 end_date 实际结束日期。原全表均零点，不减一天，不按月数重算。 |
| 8 | warranty_months | INT UNSIGNED / 是 | 原 warranty 年数×12。建议空串→NULL、0→0；只作参考，不约束或覆盖实际起止日期。 |
| 9 | extended | BIT / 是 | 原 isyb，注释为1延保、0其他数据；1→true、0→false、NULL保持。当前全部1，不因此删掉原属性。 |
| 10 | is_latest | BIT / 是，默认NULL | 原 state 的最新选择标记；1→true、NULL保持，不能当在保状态、删除标志或审批结果。 |
| 11 | remark | TEXT / 是 | 原 remark 完整承接；当前全NULL。新表按原TEXT规划，不施加旧表500字符限制。 |
| 12 | version | INT UNSIGNED / 否，默认0 | 既有AST并发控制用途，不伪装来源版本。 |
| 13 | creator | VARCHAR(64) / 否 | 原无创建人，实际迁入操作者；普通业务写入沿既有正式审计规则。 |
| 14 | create_time | DATETIME(3) / 否 | 实际迁入时间，不拿续保开始日期当创建时间；重放不改。 |
| 15 | updater | VARCHAR(64) / 否 | 实际写入操作者；重放不改。 |
| 16 | update_time | DATETIME(3) / 否 | 实际写入时间；无变化不改。 |
| 17 | deleted | BIT / 否，默认false | 既有逻辑删除用途；原 state 为NULL不代表删除。 |

不增加 device_id、project_id、contract_id 或一组来源镜像。关联查询使用正式业务中的租户、合同、发货和SN；技术追溯使用现有INT/PLT绑定。未解析主档的记录仍是正式续保记录，不另造暂存业务表。

## 3. 全部10原字段、身份和约束

| 原字段 / 类型 / 可空 | 新表正式承接点或技术处理 | 完整性说明 |
| --- | --- | --- |
| id / VARCHAR(64) / 是 | 既有迁移来源绑定，以 id＋barcode 形成来源身份 | 41组同ID是不同SN记录，不按id单独去重；原技术ID不新增为业务列。 |
| con_xb / VARCHAR(25) / 是 | warranty_contract_no | 当前全部有值；原续保合同，不能反推或替换项目销售合同。 |
| barcode / VARCHAR(50) / 是 | device_sn | 全表0个NULL、15个空串；全部保留，不造设备。 |
| grade / VARCHAR(15) / 是 | warranty_grade | 保留4个空串及全部原码；名称由正式目录查询。 |
| begin_date / DATETIME / 是 | warranty_start_date | 全表有值、均零点，保留实际日期。 |
| end_date / DATETIME / 是 | warranty_end_date | 全表有值、均零点，无倒置区间。 |
| warranty / CHAR(1) / 是 | warranty_months | 年数×12已批准；258,238个空串建议NULL，474个原0保留0，其余1～6转12～72。 |
| remark / TEXT / 是 | remark | 全NULL仍按完整字段规划，后续非空可以完整保存。 |
| isyb / INT / 是 | extended | 全表462,213条为1；0的原定义是其他数据，不臆测为普通保修。 |
| state / INT / 是 | is_latest | 213,658条为1，248,555条为NULL；全部记录承接。 |

本表不丢弃任何原业务字段。原id仅承担技术身份，保存到现有迁移绑定；原始十列继续保留为迁移证据，但证据不代替上表业务承接。

已批准的约束和索引：

1. 主键 id；按既有框架生成。租户必填，所有查询同时受租户和正式业务授权约束。
2. 普通查询索引 (tenant_id, device_sn, warranty_start_date, id)，合同查询索引 (tenant_id, warranty_contract_no, device_sn)。不对SN、合同＋SN、日期组合加业务唯一键，因为同设备允许多次续保，原重复业务值也须逐行保留。
3. 日期约束仅要求两端都有值时 end_date 不早于 start_date。不校验起止跨度等于参考月数；用户已明确月数仅参考。
4. 新表不建立指向设备或合同当前主档的强制外键。正常设备详情仍先核实设备可见性；项目展示沿已授权项目→合同→发货SN读取。空SN和空权限集合返回空关联结果，不能省略条件扩大查询范围。
5. 来源身份沿既有INT绑定使用 id＋barcode；当前组合全表唯一。可配置来源键 fb_service:<原id>:<原barcode> 最大84字符，按目标字符比较规则核对也无重复。它属于任务和绑定，不在新表增加 source_key 业务列。
6. 1,101个SN的最大结束日期存在并列，保留原 state 所选择的记录，不能仅按最大日期任取一条。is_latest 不设默认true，不把空状态变成删除。
7. 无原审计，用实际迁入人员/时间；后续重放、并发和来源变化按现有同步保护。来源缺行不自动删除正式记录，后续人工修改不被静默覆盖。

只读精确文本比较发现112,842个非空来源SN未匹配当前设备主档，对应241,165条原续保。这是字节级比较结果，不等于按旧库大小写/尾空格规则计算的缺设备总数；不据此造主档、归并原行或丢弃续保。

本次新表不需要解除旧表外键；先前“在旧表增加is_latest及解除外键”的提议已经撤回。现有149条记录、50列和原外键原样保持。

## 4. 原SQL计算规则：保存输入，重新生成结果

1. 原发货链为`fb_contract → fb_shipment → fb_shipment_barcode`。对应输入已由正式发货合同引用、装箱单和设备发货承接，继续复用；不把原计算视图再迁成业务历史。
2. 初始起始日期取`IFNULL(sb.warrantyStartDate, s.warrantyStartTime)`。
3. 初始结束日期按原表达式：`IFNULL(起始日期 + INTERVAL IF(IFNULL(sb.warrantyMonth,0)=0,c.warranty*12,sb.warrantyMonth) MONTH, s.warrantyEndTime) - INTERVAL 1 DAY`。此处是初始发货保修计算；`c.warranty`已裁决为合同年数。
4. 续保覆盖条件严格沿原SQL：同SN、原`state=1`、`s.packdate < fs.end_date`。匹配后结束日期直接取`fs.end_date`，不乘月数、不再减一天、不擅自改成两者MAX。原续保`begin_date`保存在该条正式记录，不能丢弃。
5. 原视图的汇总起始日期仍为初始发货起始日期；逐条续保展示各自实际起止日期。原视图参考月数是发货月数/合同月数，不拿它表示整个续保后跨度；续保记录自己的参考月数单独展示。
6. 等级按原`fs.grade`匹配目录；查询结果未匹配等级时，沿原默认`53030001`及对应名称。原记录的空等级不被回写为默认值。
7. 原筛选保留合同类型0/11、SN非NULL及原RMA过滤。原`diff`为业务当天日期减结束日期；结束当天仍属“维保内”，次日才过期。合同聚合沿原SQL得到维保内、维保外、部分保外；缺日期不猜状态，不新增项目状态或审批事实。
8. 原合同缓存的`renewalDesc`按SN查询全部续保，未启用`state=1`过滤。因此原NULL状态的248,555条同样迁入并正常展示，不能只迁最新记录。

以上规则可复用原SQL关系及正式字段完成。新系统日常时间变化只重算当前结果或查询状态，不新增续保记录；实际续保输入发生变化才经正式Owner更新相应业务记录。

## 5. `ast_device_warranty`现有21列与计算承接边界

本表不建议新增列、改类型或解除设备外键。31条当前值仍受此前保护裁决约束，不能因本方案自动覆盖；后续计算应先列出新旧结果差异。

| 现有字段 | 现类型/可空 | 承接边界 |
| --- | --- | --- |
| `id` | BIGINT / 否 | 既有当前投影身份。 |
| `device_sn` | VARCHAR(100) / 否 | 仅有正式设备时建立设备当前投影；不限制独立续保记录保存。 |
| `warranty_start_date` | DATE / 是 | 原计算的发货保修起始日期。 |
| `warranty_end_date` | DATE / 是 | 原计算选中的续保结束日期，或初始发货结束日期。 |
| `warranty_months` | INT UNSIGNED / 是 | 原视图的发货参考月数，不冒充续保后总跨度；续保月数查对应记录。 |
| `warranty_grade` | VARCHAR(32) / 是 | 原计算的等级编码。 |
| `warranty_contract_no` | VARCHAR(64) / 是 | 原计算行对应的发货合同；续保合同单独在正式续保记录展示，不替换项目销售合同关联。 |
| `warranty_provider` | VARCHAR(128) / 是 | 原续保表和原视图均无同义值，不猜服务商；已有值保护。 |
| `warranty_type` | VARCHAR(32) / 是 | 不把`state/isyb`臆测为类型；已有值保护。 |
| `warranty_status` | VARCHAR(32) / 是 | 建议直接采用原SQL的“维保内/维保外”客观结果；“部分保外”仅用于合同汇总，不塞入单设备状态。现存ACTIVE及验收种子值按原保护裁决保持，不拿测试值定义新状态，也不套用服务过程的0～4状态字典。 |
| `remark` | VARCHAR(500) / 是 | 不塞入计算链或全部续保列表；已有值保护。 |
| `source_system` | VARCHAR(32) / 否 | 既有来源标识；不得将计算结果伪称外部原事实。 |
| `source_key` | VARCHAR(128) / 否 | 既有当前投影唯一身份；计算不能每天产生新业务记录。 |
| `source_version` | VARCHAR(64) / 是 | 既有输入版本追溯；无原版本不伪造。 |
| `version` | INT UNSIGNED / 否 | 当前结果并发保护。 |
| `creator` | VARCHAR(64) / 否 | 实际生成操作者；现存值保护。 |
| `create_time` | DATETIME(3) / 否 | 实际生成时间；不借续保开始日期初始化。 |
| `updater` | VARCHAR(64) / 否 | 实际计算更新操作者；无变化不改。 |
| `update_time` | DATETIME(3) / 否 | 实际计算更新时间；无变化不改。 |
| `deleted` | BIT / 否 | 既有规则，不因过保删除。 |
| `tenant_id` | BIGINT / 否 | 既有租户边界。 |

项目/合同的维保列表应按原“项目→合同→发货行→SN→续保”关系计算和展示，不能先压缩成设备主档一条当前结果，再据当前设备归属删掉项目原有发货。设备详情的唯一当前投影可复用既有正式发货选择结果；多条来源仍在正常发货及续保记录区完整展示。

## 6. 迁移任务和正常页面

- 复用现有迁移/同步框架和AST Owner，将原表10列映射到本次已批准的17列续保实体。SQL可编辑、分页、参数化；同一份任务覆盖整表，不在Java中写死十项目SQL。不新建迁移基础设施。
- 源表无更新时间，不能伪造增量水位；采用已有分页全量核对能力，按复合来源身份写入/重放。后续已有行变化沿现有同步字段保护，不接管人工后续修改，缺行也不自动删除业务历史。
- 当前462,213是原表总量，既不代表已经迁入，也不扩大本次数据写入授权。实际执行仍限定已授权的用服售后验证范围；完整设计不能只处理试点恰好为0的情况。
- 对正常项目页面，由既有项目合同关系及正式发货SN查询相应续保；复用迁入设备主档不是展示前置条件。原`con_xb`是续保合同，不拿它反推销售项目。
- 复用设备正常“维保信息”页及项目正常维保资料区域，展示续保合同、SN、等级、实际起止日期、参考月数、是否延保及查询选中标记。不新增“DPPMS历史资料”信息块，不直接展示迁移快照替代业务记录。
- 已核实现用`project/project-master-detail/index.vue`接入`ProjectSerialPanel.vue`。该组件原实现先查询当前项目设备主档，再按`deviceId`逐台取维保；此链路无法覆盖未解析主档的正式续保。现已增加正常续保页签，按本节已授权项目合同/发货SN关系接入，保留正常页面位置及其他设备功能，不转向停止更新的`pms-inheritance`路径，也不把现有设备主档查询误称为旧SQL等价实现。
- 新增记录及计算Owner补齐后，统一核对原SQL结果、正式表值和正常页面；不能把空试点的成功重放称为续保功能已验收。

## 7. 一并登记、未混入本次批准的其他缺项

### 原等级目录`fb_warranty_grade`

全表12条、7字段已读。现有SYSTEM目录可承接其编码和名称，但本次只读目标库未发现正式维保等级类型。以下是后续复用方向，不能声称目录已经承接，也不在本次悄然排除它的有效期字段。

| 原字段 | 正式承接/待明确 |
| --- | --- |
| `id` | 技术绑定，不覆盖字典正式ID。 |
| `gradecode` | 建议既有`system_dict_data.value`。 |
| `gradename` | 建议既有`system_dict_data.label`。 |
| `gradestatus` | 建议沿原启停含义进入既有`status`。 |
| `sort` | 建议既有`sort`；原合同计算还使用它区分等级，不能当作无关显示顺序丢掉。 |
| `effectiveFrom` | 既有字典无同义字段，承接或排除待同表明确。 |
| `effectiveTo` | 同上；当前12条均NULL不代替全表规则。 |

本次`fb_service.grade`原编码可先准确保存；正式名称查询及计算默认等级须复用经过确认的目录，不能写死十二项标签冒充目录承接。

### 原人工变更`warranty_change_logs`

55条、8列全部读过；两个旧代码根目录及原库存储过程未定位到写入含义。旧清单把`warrantyTimes`直接称为期限，证据不足，现纠正为语义待核实：它的1～5与实际起止月数不对应，不能沿`fb_service.warranty`乘12规则处理。

| 原字段 | 已知用途及缺口 |
| --- | --- |
| `id` | 原变更身份，使用已有技术绑定。 |
| `username` | 原操作者，人员关联需沿已批准账号/审计规则核对。 |
| `updateType` | 当前均1，操作类型的真实定义未核实。 |
| `barcode` | 原设备SN。 |
| `warrantyStartTime` | 原登记的开始日期。 |
| `warrantyEndTime` | 原登记的结束日期。 |
| `warrantyTimes` | 未核实是何种业务数值，不能猜为月数、年数或次数。 |
| `updateTime` | 原操作时间；不能覆盖续保原记录自身不存在的审计。 |

这些变更不是已批准丢弃，也不写入空的V414原型冒充正式承接；状态`BLOCKED_BY_SPEC`。`warranty_info`当前0条，完整15原列仍保留既有映射清单的未承接状态，本次不擅自恢复写入。

## 8. 通过整表裁决后的验证范围

验证直接针对可能丢行或错误计算的情况：同ID不同SN的41组不合并；相同最大截止日期按原选择标记；NULL状态记录也完整迁入；空参考年数不变为0；原0年月数不改实际起止日期；结束日当天与次日边界；无主档/空SN只保留业务记录且不错误授权；项目不因设备当前归属变化丢失原发货续保；失败回滚与重放不重复插入；旧维保记录表50列及外键不变、现存149条记录/31条投影及原回执未被覆盖。

批准前只完成证据、方案及已明确单位的规格纠正。批准后仍沿正式任务先预览、通过Owner写入，再统一核对正式表和正常页面；浏览器机械操作按用户要求交GPT-6.1-sol轻度执行，发现实现问题交回主任务。

## 9. 证据和代码定位

- [原维保整表审查及现存保护裁决](2026-10-11-dppms-warranty-record-table-decision.md)
- [67表映射清单](2026-10-13-dppms-migration-mapping/field-mapping.md)
- `SQL/migrations/V112__fast001_device_relationship_location_warranty.sql`：原19列记录、21列投影及外键。
- `pms-module-asset/.../service/warranty/DeviceWarrantyQueryService.java`：当前仅按已有设备读取当前投影和记录。
- `pms-module-asset/.../service/projection/DeviceProjectionReconciliationService.java`：复制已有当前维保到设备主档，不负责从续保计算当前维保。
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceWarrantyPanel.vue`：正常维保页，可复用。
- 私有只读证据目录：`C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private`。
- `warranty-renewal-design-source-20261013.json`、`warranty-renewal-design-additional-20261013.json`、`warranty-renewal-routines-20261013.json`：实际视图、原字段、注释、数值分布和固化过程。
- `warranty-renewal-service-views-20261013.json`：原最新服务视图、41组同ID记录、目录12行。
- `warranty-renewal-design-target-20261013.json`：当前正式表及未批准原型的完整DDL。
- `warranty-renewal-device-association-profile-20261013.json`：精确SN关联、复合键和截止日期并列核查。
- `warranty-renewal-final-proposal-check-20261013.json`：目标字符比较下来源键无重复、标记分布、现存记录与目录查询。
