# 项目、合同、订单行与设备SN迁移映射及对账方案

## 1. 文档状态

**状态：评审草案。**

本文把旧库证据转换为可执行迁移规则，但不包含生产环境连接信息，也不授权执行新旧库数据写入。

相关文档：

- [模型方案回顾](project-order-model-options-review.md)
- [目标表结构及旧库证据](project-order-target-schema-evidence.md)
- [MySQL 8.x物理DDL草案](project-order-physical-schema.mysql.sql)
- [ADR-0001：以ERP订单行实施范围作为项目交付主链](../../../docs/decisions/0001-project-order-line-scope-model.md)

## 2. 迁移原则

1. 旧库只读，不执行`INSERT/UPDATE/DELETE/TRUNCATE/DDL`。
2. 迁移程序使用两个独立连接池分别读取旧库、写入新库，不执行跨库SQL。
3. 一次性业务迁移使用一致性快照；不能混用不同刷新时间的ERP订单头、订单行和项目产品关系。
4. 每条旧记录必须满足“映射到目标记录”或“生成迁移问题”之一，不能静默丢弃。
5. 新库业务唯一键只有在重复和多义记录解决后启用或转为`ACTIVE`。
6. CRM执行单及配置缺失不阻断项目、合同、订单和订单行迁移。
7. ERP已发货数量与SN设备事实分别对账，不要求两者数量相等。
8. `-L`、`-C`、`-his`只作为旧值保存，不通过字符串截取自动建立正式关系。
9. 正式切换前旧平台继续作为业务权威源；切换失败时不修改旧库，直接撤销新平台入口并重做目标批次。

## 3. 迁移数据流

```text
旧MySQL 5.7只读连接
        |
        | 分表、分批SELECT
        v
不可变快照文件/批次校验和
        |
        | 离线转换、唯一键解析、问题分类
        v
新MySQL 8.x迁移暂存区
        |
        | 目标业务API或受控迁移服务
        v
业务主表 + external_key_map + migration_issue
        |
        | 全量对账、查询性能验证、业务抽样
        v
切换或整批作废后重做
```

禁止模式：

```sql
-- 禁止：新库SQL直接引用旧库schema
INSERT INTO new_db.pms_project (...)
SELECT ... FROM dppms.pm_project;
```

允许模式：

```text
LegacyProjectReader.selectBatch(lastProjectId, pageSize)
    -> ProjectMigrationTransformer
    -> NewProjectImportService.importBatch(batchNo, rows)
```

## 4. 基准数据快照

以下数量来自2026-07-29只读核验，只作为迁移程序开发基线。正式迁移前必须在同一个一致性快照内重算。

| 对象 | 当前数量/质量 |
| --- | ---: |
| `pm_project` | 31,764行 |
| 重复`(projectType, projectCode)` | 83组、86条多余记录 |
| `pm_project_contract` | 32,735行 |
| `sms_ofst_contract_head_sap`回款信息 | 34,689行、34,686个合同号 |
| 回款表合同号重复 | 1组4行；`batch_code`全部为空 |
| 回款合同经ERP订单解析所属公司 | 唯一34,542个、无法解析144个、多公司0个 |
| 项目合同号在回款表覆盖 | 存在30,887个、不存在1,487个 |
| 项目合同经ERP订单解析所属公司 | 唯一32,020个、无法解析354个、多公司0个 |
| ERP订单头 | 52,914行 |
| ERP订单行 | 217,602行，组合业务键全部唯一 |
| `pm_project_product_line` | 144,152行 |
| 项目产品唯一命中订单行 | 137,829行 |
| 项目产品缺键/未命中/多义 | 缺键3,655行、未命中2,615行、多义53行 |
| 跨项目订单行 | 3,021个，其中3,011个无项目分配数量 |
| `fb_shipment_barcode` | 1,348,396行、1,121,689个SN |
| `fb_contract`发货合同归属 | 45,370行 |
| `fb_shipment`装箱单 | 57,995行；1条找不到发货合同归属 |
| 条码到装箱单 | 命中1,348,379行；15行空装箱单号、17行未命中 |
| `rma_no` | 240,853行有值；`isRMA`全部为空 |
| `pm_project_shipment` | 249,759行 |
| 项目SN命中条码源 | 249,756行 |
| 当前SN到订单行字段覆盖 | 0 |

回款表覆盖不到全部ERP、项目和发货合同；不得据此直接去后缀匹配合同，也不得把`fb_contract`误当主档补齐。

## 5. 迁移批次和执行顺序

| 阶段 | 输出 | 阻断条件 |
| --- | --- | --- |
| M00 源库冻结核验 | 一致性快照时间、表计数、最大更新时间、刷新任务状态 | 订单头、订单行、项目产品行不在同一快照 |
| M01 原始抽取 | 分表快照、逐文件校验和、读取行数 | 文件缺失、校验和变化、读取数量不一致 |
| M02 基础主档 | 项目、合同、订单、订单行、CRM执行单、SN主档 | 主键或确定性外部键冲突 |
| M03 关系迁移 | 项目合同、订单合同、项目订单行范围、SN项目归属、执行单关系 | 找不到主档且未生成问题单 |
| M04 血缘迁移 | 特殊合并、订单变更、设备替换/转移 | 关系循环或两端对象缺失 |
| M05 对账 | 批次对账报告、问题明细、性能报告 | 任一强制门禁失败 |
| M06 业务抽样 | 项目、合同、订单行、SN端到端抽样 | 业务归属、数量或状态不一致 |
| M07 切换 | 新平台入口生效、只读同步游标建立 | 存在P0问题或回退方案未验证 |

M02到M04可以反复重做，但每次必须使用新的`batch_no`。不得覆盖上一批次的问题解决证据。

## 6. 逐表迁移映射

### 6.1 项目主档

| 旧来源 | 目标 | 规则 |
| --- | --- | --- |
| `pm_project` | `pms_project` | 按`projectId`读取；保留项目编码、名称、类型、负责人、组织和状态 |
| `pm_project_header` | 不直接迁移 | 它只是`projectType='10'`的视图 |
| `pm_project_group` | 不迁移为项目组合 | 仅作为解析项目合同关系的技术桥 |
| `pm_project_group_relationship` | `pms_external_key_map`或`pms_migration_issue` | 解析到旧项目，不创建项目父子关系 |

转换规则：

1. `source_system='LEGACY_PMS'`，外部键为`pm_project.projectId`。
2. 旧项目先作为根项目迁移：`parent_id=NULL`、`root_id=id`、`tree_depth=0`。
3. 不从旧项目组推断父项目、组合或地区局点子项目。
4. `(projectType, projectCode)`唯一且字段无冲突的记录自动迁移。
5. 重复编码进入`DUPLICATE_PROJECT_CODE`问题；7组有效记录相互重复必须人工决定保留、合并或重新编码。
6. 所有旧`projectId`均写入`pms_external_key_map`，包括多条旧记录映射到同一清洗后项目的情况。

### 6.2 合同主档

| 旧来源 | 目标 | 规则 |
| --- | --- | --- |
| `sms_ofst_contract_head_sap`每条源行 | `pms_contract_receivable` | 以源`id`建立幂等键，完整保存回款金额和源载荷 |
| 回款合同号 + ERP唯一`compCode` | `pms_contract` | 以`(tenant_id, company_code, contract_no)`形成正式合同 |
| ERP中存在但回款表缺失的合同+公司 | `pms_contract` | `master_source_system='ERP_FALLBACK'`，保留缺少回款主档状态 |
| 公司无法解析的回款/项目合同 | 快照或迁移问题 | 不创建带伪造公司的正式合同 |
| `fb_contract` | `pms_shipment_contract_ref` | 仅作为发货合同归属迁移，不生成合同主档 |

转换规则：

1. 当前旧系统没有单一合同主档，`sms_ofst_contract_head_sap`是合同回款依据，不是无缺口的权威源。
2. 正式合同业务键固定为`所属公司 + 合同号`；合同号单列只用于查询。
3. `sms_ofst_contract_head_sap.batch_code`在当前34,689行中全部为空，禁止直接映射为公司。
4. 回款表`order_num`的34,689行当前均不能直接命中ERP`orderNumber`，只保留快照，不用于解析订单或公司。
5. 先按合同号汇总ERP订单的`compCode`：恰好一个公司时写入合同回款记录并形成/关联正式合同；无公司时生成`CONTRACT_COMPANY_UNKNOWN`；多公司时生成`CONTRACT_COMPANY_CONFLICT`。
6. 回款表一组4行相同合同号全部写入快照；正式合同只生成一次，待业务确认该组是否还缺少区分维度。
7. ERP合同号不在回款表时仍按`contractNo + compCode`生成正式合同，标记`RECEIVABLE_MASTER_MISSING`，保证订单关系可迁移。
8. 项目合同只在公司可由关联ERP订单等明确证据唯一解析时建立`pms_project_contract_rel`；否则保留旧关系键并生成问题。
9. `-L`合同号保持原值。只有存在明确拆单批次证据时才通过`pms_order_change_rel`建立关系。

对账要求：

- 34,689条回款源行全部进入快照或生成行级问题。
- 34,542个可由ERP唯一解析公司的回款合同必须关联正式合同；144个未解析合同全部有问题记录。
- 每个非空ERP订单合同号都按`contractNo + compCode`关联正式合同。
- 45,370条`fb_contract`全部进入发货合同归属表，不与合同主档行数对等。
- 合同号相同但公司不同的记录不得合并。

### 6.3 项目合同关系

| 旧来源 | 目标 |
| --- | --- |
| `pm_project_group_relationship → pm_project_group → pm_project_contract` | `pms_project_contract_rel` |

转换规则：

1. 先通过旧`projectId`外部键定位项目，不使用可能重复的项目编码直接连接。
2. 按项目、合同和关系角色去重。
3. 旧关系找不到项目或项目组时生成`PROJECT_CONTRACT_ORPHAN`。
4. 旧项目组名称、组编码只保存到来源载荷，不创建`pms_portfolio`。

### 6.4 ERP订单头

| 旧字段 | 目标字段 |
| --- | --- |
| `source` | `source_system` |
| `compCode` | `company_code` |
| `orderType` | `order_type` |
| `orderNumber` | `order_no` |
| `salesType` | `sales_type` |
| 时间、客户、项目名称、备注 | 对应订单快照字段 |
| `customInfo` | `source_payload` |
| `syncTime` | `source_sync_time` |

订单确定性业务键：

```text
tenant_id + source + compCode + orderType + orderNumber
```

转换规则：

1. 52,914条旧订单头先按业务键分组。
2. 同一业务键的不同`contractNo`写入`pms_order_contract_rel`。
3. 同一业务键的不同`orderExecNumber`写入`pms_order_execution_rel`。
4. 非键字段完全一致时可以自动归并为一个订单头。
5. 非键字段冲突时生成`ORDER_HEADER_CONFLICT`；不得直接采用`id`最大记录。
6. 每条旧订单头`id`都映射到归并后的目标订单。

### 6.5 合同订单关系

| 旧来源 | 目标 |
| --- | --- |
| `pm_order_data_from_erp.contractNo` | `pms_order_contract_rel` |

转换规则：

1. 先定位归并后的订单主档。
2. 按第6.2节定位正式或待确认合同。
3. 同一订单、合同只保留一条有效关系，但所有旧头行均保留外部键映射。
4. 不把`contractNo`继续保存在订单头作为唯一合同外键。

### 6.6 ERP订单行

| 旧字段 | 目标字段 |
| --- | --- |
| `source + compCode + lineType + orderNumber` | 定位`order_id` |
| `lineNum` | `line_no` |
| `itemCode/itemDesc/bundleCode` | 产品快照字段 |
| `orderQuantity` | `order_qty` |
| `openQuantity` | `open_qty` |
| `orderQuantity-openQuantity` | `delivered_qty` |
| `profitCenter/warrantyMonth` | 对应行属性 |
| `realOrderExecNumber` | `real_execution_no`并尝试写辅助关系 |

转换规则：

1. 当前217,602条组合业务键全部唯一，可批量迁移。
2. 订单行必须先通过订单业务键定位`order_id`。
3. 退货行允许负数量，不做无条件非负约束。
4. `realOrderExecNumber`为空不生成错误；有值时尝试写`pms_order_execution_rel`。
5. `delivered_qty`只与旧ERP行数量对账，不与SN数量对账。

### 6.7 项目订单行实施范围

来源：`pm_project_product_line`。

匹配优先级：

1. `orderNumber + lineNum + itemCode`唯一命中当前ERP订单行。
2. 不允许仅按合同号、产品或数量自动匹配。
3. 命中0条、命中多条或缺关键字段时保留旧键并进入待解析状态。

状态规则：

| 条件 | `scope_status` | `order_line_id` | `allocated_qty` |
| --- | --- | --- | --- |
| 缺订单号/行号 | `PENDING_MAPPING` | 空 | 空 |
| 找不到订单行 | `PENDING_MAPPING` | 空 | 空 |
| 命中多个订单行 | `PENDING_MAPPING` | 空 | 空 |
| 唯一命中且只关联一个项目、无重复冲突 | `ACTIVE` | 有值 | 使用旧`orderQuantity` |
| 唯一命中且关联多个项目、旧`projectQuantity`完整且合计校验通过 | `ACTIVE` | 有值 | 使用`projectQuantity` |
| 唯一命中且关联多个项目、分配数量缺失 | `PENDING_QUANTITY` | 有值 | 空 |

强制规则：

- `projectQuantity`为空时，不得在多个项目间复制完整`orderQuantity`。
- 310组自然键重复先进入`DUPLICATE_SCOPE`，确认后才能形成唯一当前范围。
- `ACTIVE`记录必须同时有`order_line_id`和`allocated_qty`。
- `PENDING_MAPPING`和`PENDING_QUANTITY`不参与完成率、交付数量和验收门禁统计。
- 每条144,152条源记录必须写入外部键映射或迁移问题。

### 6.8 CRM执行单

| 旧来源 | 目标 |
| --- | --- |
| `pm_project_property_from_sms` | `pms_crm_execution_order`基础属性 |
| `pm_project_property_af_from_sms` | 同一执行单的补充属性 |

转换规则：

1. 按`source_system + execution_no`归并。
2. 常规头和安服补充头不创建两个执行单。
3. 重复执行单记录字段一致时合并，字段冲突时生成`CRM_EXECUTION_CONFLICT`。
4. CRM项目只能在唯一命中旧项目映射时填写`primary_project_id`。
5. 不把执行单设为项目订单行实施范围的必填父对象。

### 6.9 CRM产品配置与安服证据

| 旧来源 | 目标 |
| --- | --- |
| `pm_project_real_product_line_from_sms` | `pms_crm_execution_config`，`config_source='CRM_STANDARD'` |
| `pm_project_product_af_from_sms` | `pms_crm_execution_config`，`config_source='CRM_AF'` |

转换规则：

1. 安服产品配置写`is_af_evidence=1`。
2. 执行单存在至少一条有效安服产品配置时，`af_evidence_status='CONFIRMED'`。
3. 没有配置时保持`UNKNOWN`，不得写为“非安服”。
4. CRM配置不全不会阻断ERP订单行和项目范围迁移。

### 6.10 特殊合并下单

| 旧来源 | 目标 |
| --- | --- |
| `pm_project_soleagent_lend_from_sms.soleAgentLendId` | `pms_execution_merge_batch` |
| 每个`orderCodes`令牌/执行单 | `pms_execution_merge_member` |

转换规则：

1. 保存原始CSV和原始源行。
2. 使用与旧过程一致且经过测试的令牌分隔规则拆成员。
3. 当前每批两个成员只是数据分布，目标不限制成员数量。
4. `orderExecNumberShort`当前全空，不能作为成员主键。
5. 主执行单、合同和利润中心存在冲突时生成迁移问题。

### 6.11 发货合同归属、装箱单、SN主档和生命周期事件

| 旧来源 | 目标 |
| --- | --- |
| `fb_contract`每条`contract_id` | `pms_shipment_contract_ref` |
| `fb_shipment.packlist_id` | `pms_shipment_package` |
| `fb_shipment_barcode.barcode` | `pms_device_sn.sn` |
| 每条`fb_shipment_barcode.id` | `pms_device_shipment_event` |

转换规则：

1. 45,370条`fb_contract`全部迁移为发货合同归属；仅在合同号和所属公司都能确定时填写正式`contract_id`。
2. 57,995条`fb_shipment`全部迁移为装箱单。57,994条连接发货合同归属；1条未命中记录保留原`con_id`并生成`SHIPMENT_CONTRACT_REF_NOT_FOUND`。
3. 1,121,689个不同SN形成候选设备主档。
4. 1,348,396条条码源行全部保留为生命周期事件，不因SN重复而丢弃；同一SN允许多装箱单、多次发放和退回事件。
5. 1,348,379条事件关联装箱单；15条空`pack_id`和17条未命中装箱单保留`legacy_package_key`并生成问题。
6. `rma_no`逐字保存；`isRMA`不迁移为判定字段，因为当前所有行均为空。非空`rma_no`只生成`rma_marked=1`，正式`business_action_code`在字典确认前为`UNCLASSIFIED`。
7. 同一SN出现不同物料且无法由RMA/母子公司关系解释时生成`SN_ITEM_CONFLICT`。
8. 当前`orderNumber/lineNum`覆盖为0，发货事件的`order_line_id=NULL`、`mapping_status='PENDING_MAPPING'`。
9. `barcode2`不覆盖主SN，转换为设备关系候选。

### 6.12 SN项目归属与转移

| 旧来源 | 目标 |
| --- | --- |
| `pm_project_shipment` | `pms_project_device_assignment` |

转换规则：

1. 按`pm_project_shipment.id`建立来源幂等键。
2. 249,756条可命中条码源的记录关联SN主档。
3. 3条找不到条码源的记录生成`SN_SOURCE_NOT_FOUND`。
4. `transferFlag=-1`作为普通归属候选。
5. `transferFlag=1/0`结合`chProjectId`、`transferProjectId`形成转出/转入历史。
6. 998个跨项目SN只有在时间和转移链完整时才能确定唯一当前归属；否则生成`SN_MULTI_PROJECT_CONFLICT`。
7. 当前不能填写`project_order_line_scope_id`，保留为空。

### 6.13 设备替换及母子关系

| 旧来源 | 目标 |
| --- | --- |
| `fb_shipment_barcode_relation` | `pms_device_relation` |
| `barcode2/rmaBarcode` | `pms_device_relation`候选 |

转换规则：

1. `sn1`和`sn2`分别解析为设备主档。
2. 关系类型依据明确源字段区分母子公司映射、RMA替换或其他关系。
3. 关系任一端缺失时生成问题，不创建悬空关系。
4. 不允许自环；关系链循环进入问题单。

### 6.14 订单变更血缘

目标：`pms_order_change_rel`。

允许自动建立的证据：

- 拆单过程输出的明确来源成员。
- RMA/退货订单存在明确关联记录。
- 旧业务表中保存的源订单与目标订单键。

不允许自动建立的证据：

- 仅因订单号或合同号以`-L`结尾。
- 仅因项目编码以`-his`结尾。
- 仅因客户、合同、产品和数量相似。

## 7. 外部键与幂等

每个迁移写入操作携带：

```text
tenant_id
batch_no
source_system
source_table
source_pk
source_checksum
```

处理规则：

1. 相同来源键、相同校验和重复提交：返回已有目标映射。
2. 相同来源键、不同校验和：生成`SOURCE_ROW_CHANGED`，不得静默覆盖。
3. 多条来源记录归并为一个目标主档：每条来源记录分别写外部键映射。
4. 业务关系无法建立：写迁移问题，并保留原始业务键和候选目标。
5. 问题解决后由补偿批次创建或更新业务关系，不修改旧批次证据。

## 8. 对账门禁

### 8.1 行级完整性

对每个源表验证：

```text
source_read_count
= mapped_source_count
+ open_issue_source_count
+ explicitly_excluded_count
```

`explicitly_excluded_count`只能用于视图、已证明的技术临时表或经审批排除的无效行，并必须有规则编号。

### 8.2 主档门禁

- 项目编码没有未解决的有效记录重复。
- 每个目标订单满足组合唯一键。
- 每个ERP订单行唯一关联一个目标订单。
- 每个正式合同满足`所属公司 + 合同号`唯一；公司未解析的源行只留在快照/问题中。
- 合同回款记录、发货合同归属与正式合同三者行数分别对账，不把来源行误合并为合同主档。
- 每个设备主档SN唯一；重复源行均已保存为事件或问题。

### 8.3 关系门禁

- 每条有效项目合同关系两端存在。
- 每条`ACTIVE`项目订单行范围有项目、订单行和分配数量。
- 跨项目订单行分配合计满足已批准数量规则。
- `PENDING_*`范围不进入实施完成统计。
- 每条当前设备归属最多有一个有效项目；有冲突时保持待确认。
- 订单、设备关系不存在自环。

### 8.4 数量门禁

- 目标ERP订单行`order_qty/open_qty/delivered_qty`逐行等于源快照。
- `delivered_qty = order_qty - open_qty`只按ERP口径核验。
- SN数只与SN源事件、项目SN归属分别核验，不与ERP发货数量强制相等。
- 项目汇总只能统计`ACTIVE`范围；`PENDING_QUANTITY`单独计数。

### 8.5 查询性能门禁

至少验证：

1. 项目直接子节点P95不超过2秒。
2. 项目详情及合同查询P95不超过2秒。
3. 项目实施订单分页P95不超过2秒。
4. 项目订单行和SN下钻P95不超过2秒。
5. 项目全部后代汇总P95不超过5秒。

必须保存`EXPLAIN ANALYZE`、测试数据规模、冷热缓存条件和P95结果，不能只保存SQL文本。

## 9. 业务抽样

每类至少选择3个项目，且关联对象不少于2个：

1. 普通单项目、单合同、单订单。
2. 一个项目多合同、多订单。
3. 同一合同关联多个项目。
4. 特殊合并下单，多个执行单生成一个ERP订单。
5. 订单行跨多个正式子项目。
6. ERP改单、退货或替代新单。
7. SN发生项目转移。
8. 同一SN发生RMA退回、借用返还和再次发放。
9. 有安服产品配置。
10. CRM配置缺失但ERP订单行可实施。
11. 历史缺订单行映射或缺项目分配数量。

每个样本从项目页面逐层核对：

```text
项目 → 合同 → 订单 → 订单行 → 项目范围 → ERP发货数量 → SN → 转移/替换历史
```

## 10. 切换与回退

### 10.1 切换

1. 冻结旧平台业务写入和ERP聚合刷新。
2. 创建最终一致性快照。
3. 执行增量补齐和全量对账。
4. 业务负责人签署问题接受清单。
5. 切换新平台入口。
6. 建立CRM/ERP辅助数据只读同步游标。
7. 旧平台保持只读查询，直到新平台稳定期结束。

### 10.2 回退

1. 关闭新平台业务入口。
2. 恢复旧平台入口和刷新任务。
3. 将新库迁移批次标记为`REJECTED`，保留对账和失败证据。
4. 不把新平台试运行数据反向写入旧库。
5. 修复转换规则后使用新批次从一致性快照重做。

由于旧库全程不改结构、不写迁移标志，本方案的回退不依赖还原旧库。

## 11. 尚未允许执行的事项

在以下条件完成前，不执行正式迁移：

1. MySQL 8.x具体小版本、字符集和排序规则通过基础工程验证。
2. 数据库版本管理工具及正式迁移目录确定。
3. 目标DDL在空库执行、重复启动和回退策略通过验证。
4. 订单行计量单位和小数规则确认。
5. 跨项目订单行历史分配数量处理规则确认。
6. SN跨项目当前归属判定规则确认。
7. 144个回款合同和354个项目合同缺公司时的补录凭证确认。
8. 回款表一组4行同合同号的归并维度确认。
9. `rma_no`到RMA、借转销、借转退、借用返还、再次发放的动作码映射确认。
10. 切换窗口、冻结范围和业务签署人确认。

## 12. DDL验证记录

2026-07-29使用现有Docker容器`npms-mysql-1`中的MySQL 8.4.10执行了隔离验证：

| 验证项 | 结果 |
| --- | ---: |
| 创建基础表 | 27张 |
| 外键约束 | 39个 |
| CHECK约束 | 42个 |
| `current_order_line_id`存储生成列 | 1个，创建成功 |
| 同合同号、不同公司 | 插入成功 |
| 同公司、同合同号重复 | 被唯一索引拒绝 |
| 一个订单关联两个合同 | 插入成功 |
| `ACTIVE`和`PENDING_MAPPING`项目订单行范围 | 插入成功 |
| 合同回款记录、发货合同归属和2个装箱单 | 插入成功 |
| 同一SN两条生命周期事件，其中一条`rma_no`有值 | 插入成功，`rma_marked`计算为1 |
| SN事件的`order_line_id=NULL` | 插入成功 |
| SN到项目归属 | 插入成功 |
| `ACTIVE`范围缺少分配数量 | 被CHECK约束拒绝 |
| 同一项目、同一订单行重复当前范围 | 被唯一索引拒绝 |

验证使用随机命名的临时schema，完成后已删除。容器中的现有业务schema和旧`dppms`均未参与验证。

本次验证证明DDL在MySQL 8.4.10可执行及关键约束生效，不证明真实迁移数据已经通过、查询达到P95指标或待确认业务规则已经解决。
