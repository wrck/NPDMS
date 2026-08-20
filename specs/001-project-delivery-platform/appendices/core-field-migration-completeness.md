# 核心历史字段迁移完整性审查

## 1. 结论

当前项目—合同—订单—设备草案已从“对象级可迁移”提升到“字段级可验证迁移”：

- 覆盖18张核心旧表、326个物理字段；
- 326个字段均有明确目标去向，未映射字段为0；
- 每条来源记录要求写入`pms_migration_source_record`保存完整`source_payload`，同时把查询、关联、统计、同步和审计字段结构化；
- 去冗余后的物理草案为52张表、1079个带中文注释的字段，覆盖客户、项目、合同、订单行实施范围、CRM辅助关系、SN物流、交付件、配置、版本、公告和故障；
- DDL已在隔离MySQL 8.4.10实例验证，结果为52张表、79个租户复合外键、81个CHECK约束。

> 以上DDL验证是2026-08-05历史快照。当前核心迁移子集已重建为60表、1,240列、48个同域外键、89个CHECK及122项候选普通索引，哈希为`5EB974...E4249`；ADR-0028已按该哈希接受九组需求方清单，4张V3技术公告治理表及跨领域物理外键仍被排除。当前子集已在隔离MySQL 8.4.10完整执行；正式独立复审已GO、模型基线已发布，可作为SDS/Feature模型输入。不得把“模型可用”误作“已批准迁移切换”。P3-E09不定义迁移批准哈希；只有Release包含历史迁移或数据切换时，才另行启用`AI-MIG-000`真实批次门禁并绑定批准窗口，普通功能Release不适用。

逐字段机器可读证据见[`../evidence/migration/core-field-mapping.jsonl`](../evidence/migration/core-field-mapping.jsonl)，汇总见[`../evidence/migration/core-field-mapping-summary.json`](../evidence/migration/core-field-mapping-summary.json)。

## 2. 审查边界

本轮覆盖以下旧表：

1. 项目和项目合同技术桥：`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；
2. CRM执行单及配置：`pm_project_property_from_sms`、`pm_project_property_af_from_sms`、`pm_project_product_af_from_sms`、`pm_project_real_product_line_from_sms`、`pm_project_soleagent_lend_from_sms`；
3. ERP订单和实施范围：`pm_order_data_from_erp`、`pm_order_line_from_erp`、`pm_project_product_line`；
4. 合同回款：`sms_ofst_contract_head_sap`；
5. 发货与设备：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`、`fb_shipment_barcode_relation`、`pm_project_shipment`。

“326/326”只表示18张核心表已经做过旧库数据画像。全部活动结构已另行建立3,931条物理字段证据、3,908个唯一表字段、197条语义来源行和82条活动业务数据元的处置矩阵，见[`complete-field-migration-matrix.md`](complete-field-migration-matrix.md)。其中`SOURCE_ONLY`和`PLATFORM_REPLACED`只保证原值不丢失，不能冒充结构化业务完成率。

## 3. 判定规则

### 3.1 两层保存

每条旧记录同时满足：

1. **原值层**：按来源字段名和值逐行写入`pms_migration_source_record.source_payload`；
2. **业务层**：需要查询、关联、统计、同步、权限判断或历史审计的字段写入正式列或关系表。

因此，迁移来源记录是无损证据，不是业务字段缺失的替代方案。目标正式列发生转换时，必须能够回查原值；多条来源记录归并为一个目标时也不会互相覆盖。

### 3.2 主键与审计字段

- 旧主键不复用为目标主键，写入对象`source_record_key`和`pms_external_key_map.source_pk`。
- 没有物理主键的来源表使用规范化完整行哈希和抽取文件内重复序号生成批次内来源键；不得依赖数据库无序返回的行号。
- 旧`createTime/createBy/updateTime/updateBy`写入`source_*`字段；目标`create_time/creator/update_time/updater`只表示新平台写入审计。
- 旧`disabled/effectiveTo`不能直接映射为新平台逻辑删除；它们参与状态和有效期转换，原值仍在来源载荷中。

### 3.3 关系解析失败

合同所属公司、项目重复编码、订单重复业务键、订单行多义、SN找不到装箱单或项目转移链不完整时：

- 原记录和原字段仍写入暂存/来源载荷；
- 能建立待解析目标记录的，使用`mapping_status`或待处理状态；
- 不能安全建立关系的，写`pms_migration_issue`；
- 禁止丢行、拼接后缀猜关系或使用任意一条候选记录。

## 4. 当前数据支持的结构化决定

以下填充率来自2026-08-05对`localhost:3306/dppms`的只读单表统计，每张表只执行聚合读取：

| 来源字段组 | 当前填充情况 | 设计决定 |
| --- | ---: | --- |
| `pm_project`办事处、客户、市场/系统/拓展/行业 | 95.4%–98.0% | 客户和行业进入`pms_project`；公司—部门角色进入`pms_project_company_department_rel` |
| `pm_project.column012`实施方式 | 68,657/83,550，82.2% | `implementation_mode`结构化 |
| `pm_project.majorProjectLevel` | 75,694/83,550，90.6% | `major_project_level`结构化 |
| `pm_project`开始、刷新、关闭时间 | 25.2%、28.6%、78.0% | 独立保存，不能被目标审计时间覆盖 |
| 普通CRM执行单公司、部门和项目字段 | 基本100% | 进入`pms_crm_execution_order`正式列 |
| 普通CRM最终客户/代理商 | 100%/95.6% | 执行单保留来源值；项目解析成功后生成参与方关系 |
| 安服CRM扩展接收/借货字段 | 0.7%–6.1% | 虽稀疏但有真实数据，保留正式列和来源载荷 |
| 安服产品配置核心产品和金额字段 | 基本100% | 进入`pms_crm_execution_config`，作为安服正向证据 |
| `pm_project_product_line`订单/发货/未发数量 | 100% | 原数量全部独立保存；分配数量按校验规则生成 |
| `fb_shipment_barcode`订单行键 | 79.4% | 原订单号、行号结构化，解析失败保持待映射 |
| `fb_shipment_barcode`维保月数 | 80.8% | 事件级结构化，不只保留在JSON |
| `fb_shipment_barcode_relation`主/附加SN、物料、合同 | 100% | 完整进入合同维度`pms_device_relation` |
| `pm_project_shipment`合同号、安装地址 | 100%/97.6% | 进入设备项目归属正式列 |
| `pm_project_shipment`转移链字段 | 仅30行 | 稀疏但直接决定转移历史，仍需结构化 |

## 5. 主要表结构修订

### 5.1 项目

`pms_project`新增旧项目高频属性、来源审计、有效期、生命周期时间及自定义JSON。旧库有1,550条项目名称为空，因此数据库列允许空值，迁移时标记待补；新建项目的名称必填由应用校验和状态机保证，不能用伪造名称解决历史缺失。

项目最终用户在主档保留迁移快照，同时通过`pms_project_party`按角色保存正式参与方。CRM执行单中的最终用户、代理商只在项目唯一解析成功后生成参与方关系，不按名称猜测合并。

### 5.2 CRM执行单和产品配置

`pms_crm_execution_order`作为只读辅助镜像承接当前查询和关联需要的销售、公司、部门、服务类型、渠道、工程费、客户项目、最终用户、代理商、接收信息、借货原因、金额和联系人字段。`engineeFee`在普通来源是文本，因此同时保存`engineering_fee_raw`；只有可安全解析时才写数值列。完整旧行只保存在`pms_migration_source_record`，不得再次复制到执行单业务行。

`pms_crm_execution_config`承接产品层级、物料、型号、数量、借货数量、价格、折扣、采购价、行类型和备注。是否安服仍只允许由安服产品配置形成正向证据，不能由执行单类型反推。

### 5.3 订单行实施范围

`pms_project_order_line_scope`除正式`allocated_qty`外，独立保留旧合同号、产品名称和项目/订单/发货/未发数量。当前`projectQuantity`全为空，不能因此删字段；它仍是未来重新统计或历史批次可能使用的分配证据。

### 5.4 发货与设备

`pms_device_shipment_event`保存每条条码记录的原订单行键、主/附加物料和SN、企业条码、维保、RMA关联SN、UUID及来源更新时间。正式主附加SN关系仍以`fb_shipment_barcode_relation`迁入`pms_device_relation`为准。

`pms_project_device_assignment`只保留项目、SN、订单行实施范围、安装地址、归属类型、有效期和转移批次。产品、合同、快递和发货时间分别从设备、订单行、装箱单和发货事件读取；旧转移链原值在迁移来源记录中保存。只有项目、SN、时间和转移证据可完整解析时，才生成正式当前归属和转移批次。

## 6. 切换门禁

正式迁移批次必须同时满足：

1. 源表字段数与矩阵一致，核心字段覆盖率为100%；
2. 每条源记录先进入`pms_migration_source_record`，再按最终映射状态归入已映射、仅问题或明确排除之一；
3. 每条迁移来源记录保存完整`source_payload`、来源主键和校验和；
4. 正式列转换值与来源载荷逐字段抽样一致，金额和数量执行全量聚合对账；
5. 所有`PAYLOAD`分类仍与本文件说明一致，新增仅载荷字段必须重新评审；
6. DDL、迁移配置和字段矩阵由自动检查确认无不存在目标列；
7. 当前已观察文本最大长度不得超过目标`VARCHAR`，最终一致性抽取后必须重新校验；
8. 旧库只读、新旧库双连接、禁止跨库SQL的边界不变。
