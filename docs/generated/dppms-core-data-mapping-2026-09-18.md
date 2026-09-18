# dppms 核心基础数据与当前系统表结构映射梳理

生成日期：2026-09-18。本文件为只读梳理报告，位于`docs/generated/`，不改变任何正式规格、Feature状态或迁移授权。历史迁移与生产切换仍受`specs/001-project-delivery-platform/appendices/data-migration-and-core-business-ai-handoff.md`第13节阻断项约束。

## 生成方式与证据来源

- 旧库结构：`localhost/dppms`只读`information_schema`查询（2026-09-18）。
- 当前系统实库结构：Docker `npdms`库（`npdms-mysql-1`容器）只读`information_schema`查询。
- 字段级映射：`specs/001-project-delivery-platform/evidence/migration/core-field-mapping.jsonl`（18核心源表/326字段，映射目标为规格DDL表名）。**该证据即此前的字段规范裁定结果，本报告全部字段表以其为准**；行业划分维度与部门维度的列名口径按第0节2026-09-18需求方裁决呈现，现DDL偏差列名仅作对照，不新增裁定；源字段名与前缀口径（`apply_type`、去`crm_`前缀）按Q-MIG-DIM-003裁决呈现。
- 目标DDL：`appendices/project-order-physical-schema.mysql.sql`，SHA-256 `AC2D3C6E8F862A9A8DCF8632B3F495E7F57E085C9874674F2835C70A9ADC4833`。2026-09-18按Q-MIG-DIM-001裁决修订行业划分/部门/原值列名并精简列注释为界面显示口径（修订后`AF8C3BA5D44BDE9809E9F10AC70A60E0CA4DA43E9BFE7E6B1714759294BBB3D0`；修订前基线`6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`，与证据基线一致），再按Q-MIG-DIM-003裁决将执行单两表列名改为源字段名口径（`apply_type`/`project_code`/`project_name`/`project_type`，索引更名`idx_crm_execution_source_project`），并按Q-MIG-DIM-004裁决将`com_crm_execution_config.config_source`统一为`source_system`、按Q-MIG-DIM-005裁决将全部66张表410条字段注释精简为界面显示口径（已建实库35张表464列由注释同步迁移落实，现为合并迁移`V294__migration_field_naming_and_comment_alignment.sql`第3段），再按Q-MIG-DIM-006裁决为合同主档`com_contract`扩列17个归并字段（修订后SHA-256 `F9B29ED5D9EE651453AA11DBFFC771E9346D2F5B3B45374F9CEF21F075E996D6`），并以前向迁移`V300__contract_master_field_consolidation.sql`落实开发库后复核；修订后DDL已在隔离MySQL 8.4容器完整执行，66张表全部建成。
- 行数核对：dppms各源表当前行数与2026-08-05基线画像完全一致，无漂移。
- 实库落地核对：npdms库`com_*`/`ast_*`表清单来自`sql/migrations/V160`（合同订单）、`V258`（合同来源四表）及F-AST-001/002设备域迁移；`npdms-domain-test`环境（24306/`npdms_domain_test`）已于2026-09-18直连核实。

处置口径（来自映射证据`preservation`）：

| 标记 | 含义 |
|---|---|
| 直接 | 源值写入目标正式列（STRUCTURED） |
| 关系 | 源值经0/1/N解析器定位目标ID后写关系列（RELATION） |
| 血缘 | 源主键进入`plt_external_key_mapping`外部键映射（LINEAGE） |
| 载荷 | 原值只进`plt_migration_source_record.source_payload`（PAYLOAD/SOURCE_ONLY） |

## 0. 当前系统落地状态总览

| 域 | 目标表（规格DDL） | npdms实库状态 |
|---|---|---|
| 合同主档/回款/发货合同归属 | `com_contract`、`com_contract_receivable`、`com_shipment_contract_reference`、`com_shipment_package` | 已建表（V160/V258）；实库仅测试数据（`com_contract` 1行、回款/发货合同0行） |
| 订单/订单行/关系 | `com_sales_order`、`com_sales_order_line`、`com_order_contract_relation`、`com_project_contract_relation`、`com_delivery_scope(_detail)` | 已建表（V160）；实库仅测试数据（订单2行、行9行）。`npdms_test`库曾按DPPMS订单模板加载真实订单81,673条（2026-09-15记录，订单行0，任务暂停） |
| CRM执行单 | `com_crm_execution_order`、`com_crm_execution_config`、`com_order_execution_relation`、`com_order_line_execution_relation`、`com_execution_order_merge_batch/member` | **未建表**，仅存在于规格DDL；字段级映射已由证据固定 |
| 设备档案/发货事件/关系 | 规格DDL命名`ast_device_sn`、`ast_device_shipment_event`、`ast_device_project_assignment`、`ast_device_relation` | 实库为**另一套已落地模型**：`ast_device`、`ast_device_shipment`、`ast_device_project_relationship`、`ast_device_relationship`（F-AST-001/002），命名与规格DDL不同，见第4节 |

注意：`proj_project_node_execution`（实库，V231）是项目计划节点执行轮次，与CRM执行单是不同概念，不参与本映射。

行业划分维度口径（执行单/回款/发货合同归属通用）："行业划分"是市场/系统/拓展/行业四个维度的统称（不单指industry），主档为新系统`cus_market_relation`（规格DDL已定义：客户市场行业划分组合目录，幂等键`source_system+source_record_key`，业务唯一键`tenant+四级组合码`）。行业划分四个维度不是部门，与部门主档无关：列名按主档`market_code/market_name`、`system_code/system_name`、`expend_code/expend_name`、`industry_code/industry_name`，全部经组合目录解析，不解析到实库`system_dept`，无`*_department_id`列；CRM原始键血缘列`system_source_key`/`expend_source_key`只放来源原值；记录创建时的行业划分原值（源表`systemid_o`/`expendid_o`/`industry_name_o`）统一存入单个JSON字段`original_division_values`（2026-09-18裁决：`{"systemSourceKey":...,"expendSourceKey":...,"industryName":...}`），不设独立`original_*`列。办事处是部门维度：列名`department_code/department_name/department_id`（`office_`与`department_`语义重复，取`department_`），`department_id`解析到平台共享部门主档（实库`system_dept`，统一编码`code`+树形`parent_id`）。市场编码/名称已裁定为源值直接复制，系统/拓展编码为解析列。业务表维度字段的对齐方式：源编码/名称按裁定直接落列，再以四级组合经`cus_market_relation`业务唯一键`uk(tenant_id, market_code, system_code, expend_code, industry_code)`定位主档记录，命中失败保留原值生成问题。

**库内字段统一裁决（2026-09-18需求方）**：四级维度字段在数据库内必须统一，以`cus_market_relation`主档列名为准；现行业务表DDL列名定性为实现偏差，须按下列对照更正。本报告字段表已按设计裁定列名记录，规格DDL文件的修订与前向迁移属实施范围（`Q-MIG-DIM-001`）：

| 设计裁定列名 | 现DDL偏差列名 |
|---|---|
| `market_code` / `market_name` | `marketing_department_code` / `marketing_department_name`（行业划分维度非部门，根名应为`market`） |
| `system_code` / `system_name` / `system_source_key` | `system_department_code` / `system_department_name` / `system_department_source_key` |
| `expend_code` / `expend_name` / `expend_source_key` | `expansion_department_code` / `expansion_department_name` / `expansion_department_source_key` |
| `original_division_values`（JSON，统一存储行业划分创建时原值） | `original_system_department_source_key` / `original_expansion_department_source_key` / `original_industry_name`（三列合并为JSON字段） |
| `department_code` / `department_name` / `department_id` | `office_department_code` / `office_department_name` / `office_department_id`（办事处是部门维度，取`department_`） |
| `industry_code` / `industry_name` | 已一致，无需更正 |
| （删除：行业划分维度无部门主档ID列） | `marketing_department_id`、`system_department_id`、`expansion_department_id` |

落实路径：CRM执行单6张未建表按裁定列名定稿；`com_contract_receivable`、`com_shipment_contract_reference`（V258已建表）已于2026-09-18经列名更正迁移（现为合并迁移`V294__migration_field_naming_and_comment_alignment.sql`）更正列名并复核证据哈希（落实回执见第6节）。dppms侧组合目录承载为同步副本`pm_project_market_relations_from_sms`（529行：10市场/53系统/119拓展/528行业，拓展与行业编码无空值；字段映射见1.6），办事处/市场部门另有`fnd_department`；CRM侧正式主档按只读关联同步协议供给。

## 1. 项目执行单（CRM执行单）域

dppms的"项目执行单"即CRM执行单（业务键`orderExecNumber`）。当前系统定位为**辅助主档**：不作为实施范围的必填父对象，订单—执行单为多对多，普通关系默认即主关系且允许多个默认主关系（Q03决策，提交`cf45f3828`）。

### 1.1 表级映射

| dppms源表 | 行数 | 目标表 | 说明 |
|---|---:|---|---|
| `pm_project_property_from_sms` | 102,520 | `com_crm_execution_order` | 执行单常规头属性 |
| `pm_project_property_af_from_sms` | 3,120 | `com_crm_execution_order`（同一执行单归并） | 安服补充头；常规头与安服头不创建两条执行单 |
| `pm_project_real_product_line_from_sms` | 9,992 | `com_crm_execution_config`（`source_system`='CRM_STANDARD'口径，Q-MIG-DIM-004统一） | 标准产品配置行 |
| `pm_project_product_af_from_sms` | 20,536 | `com_crm_execution_config`（`is_af_evidence=1`） | 安服产品配置行，安服唯一正向证据 |
| `pm_project_soleagent_lend_from_sms` | 1,441 | `com_execution_order_merge_batch` + `com_execution_order_merge_member` | 总代借货/特殊合并下单，成员由`orderCodes`拆分，数量不限 |
| `pm_project_product_config_level_info_from_crm` | 736 | 载荷保留（SOURCE_ONLY） | 产品配置层级，未进入核心映射 |
| `pm_project_product_lease_line_from_crm` | 454 | 载荷保留（SOURCE_ONLY） | 租赁产品行，未进入核心映射 |
| `pm_project_market_relations_from_sms` | 529 | `cus_market_relation`（CRM同步的客户市场行业划分组合目录） | 四级"市场-系统-拓展-行业"组合关系主档；字段映射见1.6 |
| `*_history`系列 | — | 载荷保留（SOURCE_ONLY） | 同步历史版本 |

执行单关系来源：订单头`pm_order_data_from_erp.orderExecNumber` → `com_order_execution_relation`；订单行`pm_order_line_from_erp.realOrderExecNumber` → `com_order_line_execution_relation`（见第3节）。

### 1.2 常规头 `pm_project_property_from_sms` → `com_crm_execution_order`（32字段）

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `plt_external_key_mapping.source_pk` | 血缘 |
| `orderExecNumber` | `execution_no` | 直接；与`source_system`构成唯一键`uk(tenant_id, source_system, execution_no)` |
| `dataSource` | `source_system` | 直接 |
| `projectCode` / `projectName` | `project_code` / `project_name`（2026-09-18裁决去`crm_`前缀，Q-MIG-DIM-003） | 直接；`primary_project_id`仅唯一命中旧项目映射时填写 |
| `salesManCode` / `salesManName` | `sales_rep_code` / `sales_rep_name` | 直接 |
| `marketCode` / `marketName` | `market_code` / `market_name`（已裁定：编码/名称直接；行业划分维度非部门，现DDL`marketing_department_*`为实现偏差） | 直接 |
| `systemId` / `systemName` | `system_source_key`（CRM原始ID原值）/ `system_name`（已裁定：直接；现DDL`system_department_*`为实现偏差）；`system_code` 经组合目录解析 | 直接（编码列为解析列） |
| `expendId` / `expendName` | `expend_source_key`（原值）/ `expend_name`（已裁定：直接；现DDL`expansion_department_*`为实现偏差）；`expend_code` 经组合目录解析 | 直接（编码列为解析列） |
| `industryId` / `industryName` | `industry_code` / `industry_name`（已裁定：`industryId`整数转字符串无损，直接映射） | 直接；编码经`cus_market_relation`四级组合目录校验 |
| `officeCode` / `officeName` | `department_code` / `department_name`（已裁定：编码/名称直接；办事处是部门，`office_`与`department_`重复取`department_`，现DDL`office_department_*`为实现偏差）；`department_id` 经共享部门主档解析 | 直接（ID列为解析列） |
| `serviceTypeName` | `service_type_name` | 直接 |
| `channelName` | `channel_name` | 直接 |
| `engineeFee` | `engineering_fee_raw` + `engineering_fee` | 直接（原始串+数值） |
| `objId` | `source_object_id` | 直接 |
| `applyType` | `apply_type`（2026-09-18裁决跟源字段名，Q-MIG-DIM-003） | 直接 |
| `corporationCode` | `company_code` + `company_id` + `company_name` | 直接+关系（解析平台公司主档） |
| `customerProjectName` / `finalCustomerName` | `customer_project_name` / `final_customer_name`（`finalCustomerName`另参与`proj_project_party.party_name`） | 直接 |
| `agentName` | `agent_name`（另参与`proj_project_party.party_name`） | 直接 |
| `projectMoney` | `project_amount` | 直接 |
| `majorProjectLevel` | `major_project_level`（另参与`proj_project.major_project_level`） | 直接 |
| `submitTime` / `predBidDate` | `submit_time` / `predicted_bid_time` | 直接 |
| `linkmanName` / `linkmanTel` | `contact_name` / `contact_phone` | 直接 |

### 1.3 安服补充头 `pm_project_property_af_from_sms` → `com_crm_execution_order`（43字段）

同1.2全部字段外，新增：

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `pspm` / `pspmName` | `project_manager_code` / `project_manager_name` | 直接 |
| `salesMenTel` | `sales_rep_phone` | 直接 |
| `decPath` | `decision_path` | 直接 |
| `requireInDate` | `required_in_date` | 直接 |
| `receiveMen` / `reveiveContactWay` / `receiveAddress` | `receiver_name` / `receiver_contact` / `receiver_address` | 直接 |
| `lendCause` | `loan_reason` | 直接 |
| `projectType` | `project_type`（2026-09-18裁决去`crm_`前缀） | 直接 |
| `afProjectMoney` | `af_project_amount` | 直接 |
| `customInfo` | `plt_migration_source_record.source_payload` | 载荷 |

归并规则：按`source_system + execution_no`归并；字段一致合并、字段冲突生成`CRM_EXECUTION_CONFLICT`；重复记录不生成两条执行单。

### 1.6 四级维度组合目录 `pm_project_market_relations_from_sms`（529行）→ `cus_market_relation`

dppms侧为CRM组合目录的同步副本，9列全量对齐目标主档；执行单/回款/发货合同归属的维度字段均按此目录对齐（口径见第0节）。

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `source_record_key`（幂等键`source_system+source_record_key`组成部分） | 血缘 |
| `marketCode` / `marketName` | `market_code` / `market_name` | 直接 |
| `systemCode` / `systemName` | `system_code` / `system_name` | 直接 |
| `expendCode` / `expendName` | `expend_code` / `expend_name`（沿用CRM来源字段语义） | 直接 |
| `industryCode` / `industryName` | `industry_code` / `industry_name` | 直接 |
| — | `source_system`='CRM'、`source_sync_time`（同步运行时间）、`status`='ENABLED' | 目标默认/运行值 |

业务唯一键`uk(tenant_id, market_code, system_code, expend_code, industry_code)`；四级编码组合是执行单、回款与发货合同归属行业划分维度列（`market_code`/`system_code`/`expend_code`/`industry_code`）的解析校验依据。

### 1.4 产品配置行 → `com_crm_execution_config`

`pm_project_real_product_line_from_sms`（标准，10字段）与`pm_project_product_af_from_sms`（安服，20字段）共用目标结构，`source_system`区分来源（Q-MIG-DIM-004统一，原名`config_source`）：

| 旧字段（标准行） | 旧字段（安服行） | 目标字段 | 处置 |
|---|---|---|---|
| — | `id` | `source_config_key`（唯一键组成部分） | 血缘 |
| `projectCode` | `projectCode` | `project_code`（Q-MIG-DIM-003去前缀） | 直接 |
| `orderExecNumber` | `orderExecNumber` | `execution_id` | 关系（定位执行单） |
| — | `corporationCode` | `company_code/company_id/company_name` | 直接+关系 |
| — | `ssfrId` | `settlement_id` | 直接 |
| — | `productCode` | `product_code` | 直接 |
| `productFirstName` | `productfirstCode`/`productfirstName` | `product_first_code`/`product_first_name` | 直接 |
| `productName` | `productName` | `product_name` | 直接 |
| `productSubCode` | `productsubCode` | `item_code` | 直接 |
| `productSubModel` | `productSubModel` | `item_model` | 直接 |
| `productSubName` | `productSubName` | `item_name` | 直接 |
| `num` | `num` | `qty` | 直接 |
| — | `borrowNum` | `borrow_qty` | 直接 |
| — | `price` | `unit_price` | 直接 |
| — | `purchaseDiscount` | `purchase_discount` | 直接 |
| — | `purchasePrice` | `purchase_price` | 直接 |
| `memo` | — | `memo` | 直接 |
| `dataSource` | `dataSource`/`lineType` | `source_system`/`line_type`（Q-MIG-DIM-004统一，原`config_source`） | 直接 |
| — | `customInfo` | `source_payload` | 载荷 |

安服证据规则：存在至少一条`is_af_evidence=1`配置时执行单`af_evidence_status='CONFIRMED'`；无配置保持`UNKNOWN`，不得推断为"非安服"。

### 1.5 特殊合并下单 `pm_project_soleagent_lend_from_sms`

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `plt_external_key_mapping.source_pk` | 血缘 |
| `soleAgentLendId` | `com_execution_order_merge_batch.source_merge_key` | 血缘 |
| `orderExecNumber` | `primary_execution_id` | 关系 |
| `orderCodes` | `source_order_codes`（CSV原值保留，按与旧过程一致的令牌规则拆成员） | 直接 |
| `contract` | `legacy_contract_no` + `contract_id` | 直接+关系 |
| `projectName` / `soleAgent` | `project_name` / `agent_name` | 直接 |
| `orderExecNumberShort` | `com_execution_order_merge_member.execution_no_short` | 直接；当前全空，不能作成员主键 |
| `profitCenter` | `com_execution_order_merge_member.profit_center` | 直接 |
| `dataSource` | `source_system` | 直接 |

## 2. 合同主档与合同相关信息

当前系统没有旧式单一合同主档源：正式合同`com_contract`由**回款表合同号+唯一解析公司**或**ERP订单合同号+公司**生成；`fb_contract`只是发货合同归属，不生成主档。

### 2.1 表级映射

| dppms源表 | 行数 | 目标表 | 说明 |
|---|---:|---|---|
| `sms_ofst_contract_head_sap` | 81,547 | `com_contract_receivable`（逐源行）+ `com_contract`（按公司+合同号生成/关联正式合同） | SAP定时刷新的合同回款头 |
| `fb_contract` | 120,639 | `com_shipment_contract_reference` | 发货合同归属，全部迁移，不与主档行数对等 |
| `pm_project_contract` | 86,780 | `com_project_contract_relation` | 项目—合同关系 |
| `pm_project_group` | 85,496 | 仅血缘/载荷 | 项目组只作解析技术桥，不迁移为组合 |
| `pm_project_group_relationship` | 85,472 | `com_project_contract_relation`（解析来源） | 组—项目关系解析到项目 |

正式合同业务键：`tenant_id + company_code + contract_no`（实库`com_contract`已含`master_source_system`、`master_source_record_key`、`authority_status`等列）。生成规则：公司由关联ERP订单`compCode`唯一解析；ERP有而回款表缺时按`ERP_FALLBACK`生成并标记`RECEIVABLE_MASTER_MISSING`；公司无法唯一解析生成`CONTRACT_COMPANY_UNKNOWN`/`CONTRACT_COMPANY_CONFLICT`，不创建伪造公司合同。合同主档汇总方式正由需求方重新选择（F-COM-001 2026-09-17检查点），`batch_code`当前全空禁止映射公司，`order_num`不用于解析订单。

### 2.2 回款头 `sms_ofst_contract_head_sap` → `com_contract_receivable`（39字段）

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `source_record_key`（幂等键） | 血缘 |
| `contract_num` | `contract_no` | 直接 |
| `batch_code` | `source_batch_code` | 直接（全空，禁止当公司） |
| `project_name` / `projectCode` | `project_name` / `project_code` | 直接 |
| `order_num` | `source_order_no` | 直接（不用于解析） |
| `client_supplier_code` / `client_supplier_name` | `customer_code` / `customer_name` | 直接 |
| `contract_money_amount` | `contract_amount` | 直接 |
| `delivered_money_amount` | `delivered_amount` | 直接 |
| `collected_money_amount` | `collected_amount` | 直接 |
| `collected_money_ratio` | `collected_ratio` | 直接 |
| `receivables_money_amount` | `receivable_amount` | 直接 |
| `over_due_money_amount` | `overdue_amount` | 直接 |
| `maketing_department_name` / `marketCode` | `market_name` / `market_code`（已裁定：编码/名称直接；行业划分维度非部门，现DDL`marketing_department_*`为实现偏差） | 直接 |
| `office_name` / `officeCode` | `department_name` / `department_code`（已裁定：编码/名称直接；办事处是部门，现DDL`office_department_*`为实现偏差）；`department_id` 经主档解析 | 直接（ID列为解析列） |
| `industry_name` / `industryId` | `industry_name` / `industry_code`（已裁定：`industryId`整数转字符串无损，直接映射） | 直接；编码经`cus_market_relation`目录校验 |
| `marketing_representative_name` / `usernamec` | `marketing_representative_name` / `marketing_representative_code` | 直接 |
| `usernamec2` | `secondary_representative_code` | 直接 |
| `currency_name` | `currency_name` | 直接 |
| `create_by`/`create_time`/`update_by`/`update_time` | `creator`/`create_time`/`updater`/`update_time`+`source_sync_time` | 直接 |
| `effective_from` / `effective_to` | `source_effective_from` / `source_effective_to` | 直接 |
| `import_batch_num` | `import_batch_no` | 直接 |
| `contract_create_date` | `contract_create_time` | 直接 |
| `systemId` / `expendId` | `system_source_key` / `expend_source_key`（已裁定：原值保留，编码/名称单独解析；现DDL`*_department_source_key`为实现偏差）；`*_code` 经组合目录解析 | 已裁定：原值保留+单独解析 |
| `systemid_o` / `expendid_o` / `industry_name_o` | `original_division_values`（JSON统一存储：`{"systemSourceKey":...,"expendSourceKey":...,"industryName":...}`，按来源原值保留；源数据当前81,547行全空，列为保结构） | 直接（JSON原值保留） |
| `latest_ship_date` | `latest_ship_time` | 直接 |
| `dataSource` | `source_system` | 直接 |

### 2.3 发货合同归属 `fb_contract` → `com_shipment_contract_reference`（12字段）

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `contract_id` | `source_record_key` | 血缘 |
| `contract_code` | `contract_no` | 直接 |
| `office_code` | `department_code`（已裁定：编码直接；办事处是部门，现DDL`office_department_code`为实现偏差）；`department_id` 经共享部门主档解析 | 直接（ID列为解析列） |
| `contract_type` | `contract_type` | 直接（int→字典编码） |
| `customer_name` / `project_name` | `customer_name` / `project_name` | 直接 |
| `warranty` | `warranty_flag` | 直接 |
| `marketCode` / `marketName` | `market_code` / `market_name`（已裁定：编码/名称直接；行业划分维度非部门，现DDL`marketing_department_*`为实现偏差） | 直接 |
| `systemId` / `systemName` | `system_source_key`（原值）/ `system_name`（已裁定：直接；现DDL`system_department_*`为实现偏差）；`system_code` 经组合目录解析 | 直接（编码列为解析列） |
| `remark` | `remark` | 直接 |

### 2.4 项目合同关系（三表桥接 → `com_project_contract_relation`）

`pm_project_contract`：`id`血缘；`contractNo`→`contract_id`（关系）；`projectGroupCode`→`project_id`（关系，经组解析）；审计四字段直接。
`pm_project_group_relationship`：`projectGroupCode`→`contract_id`、`projectCode`/`smsProjectCode`→`project_id`（关系）；`mergeBranchMark`载荷；审计四字段直接。
规则：先经旧`projectId`外部键定位项目，不用可能重复的项目编码直连；按项目+合同+角色去重；找不到项目或组生成`PROJECT_CONTRACT_ORPHAN`；组名称/编码只留载荷，不创建组合。

## 3. 销售订单与销售订单行

### 3.1 订单头 `pm_order_data_from_erp`（91,572行）→ `com_sales_order`

确定性业务键：`tenant_id + source_system + compCode + orderType + orderNumber`（旧库91,572行归并为91,239个业务键组，333条重复/冲突候选逐组处理；非键字段一致自动归并，冲突生成`ORDER_HEADER_CONFLICT`）。

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `plt_external_key_mapping.source_pk` | 血缘 |
| `source` | `source_system` | 直接 |
| `compCode` | `company_code` | 直接 |
| `orderType` | `order_type` | 直接（0销售/1退货） |
| `orderNumber` | `order_no` | 直接 |
| `salesType` | `sales_type` | 直接 |
| `orderCreateTime` / `customerRequireTime` | `order_create_time` / `customer_required_time` | 直接 |
| `customerCode` / `customerName` | `customer_code` / `customer_name` | 直接 |
| `projectName` | `source_project_name` | 直接 |
| `orderComment` | `order_comment` | 直接 |
| `syncTime` | `source_sync_time` | 直接 |
| `contractNo` | `com_order_contract_relation.contract_id` | 关系（不固化在订单头） |
| `orderExecNumber` | `com_order_execution_relation.execution_id` | 关系 |
| `customInfo` | `source_payload` | 载荷 |

实库`com_sales_order`另有`source_record_key`、`source_version`、`order_amount`、`currency_code`、`authority_status`、`source_lifecycle_status`、`source_updated_at`等列，由商务权威接收API填充，无dppms直接来源。

### 3.2 订单行 `pm_order_line_from_erp`（380,605行）→ `com_sales_order_line`

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `plt_external_key_mapping.source_pk` | 血缘 |
| `orderNumber` | `order_id`（关系）+ `order_no`（快照） | 关系+直接 |
| `lineNum` | `line_no` | 直接 |
| `lineType` | `line_type` | 直接 |
| `itemCode` / `itemDesc` | `item_code` / `item_desc` | 直接 |
| `orderQuantity` / `openQuantity` | `order_qty` / `open_qty`；`delivered_qty` = `orderQuantity - openQuantity` | 直接 |
| `bundleCode` | `bundle_code` | 直接 |
| `warrantyMonth` | `warranty_month` | 直接 |
| `compCode` | `company_code` | 直接 |
| `profitCenter` | `profit_center` | 直接 |
| `realOrderExecNumber` | `real_execution_no` + 尝试写`com_order_line_execution_relation` | 直接+关系 |
| `source` | `source_system` | 直接 |
| `syncTime` | `source_sync_time` | 直接 |
| `customInfo` | `source_payload` | 载荷 |

规则：退货行允许负数量，不做无条件非负约束；`delivered_qty`只与ERP行数量对账，不与SN数量对账；订单行先经订单业务键定位`order_id`。实库`com_sales_order_line`另有无dppms来源的增强列：`model_code`、`product_id`/`product_code`、`unit_code`/`unit_scale`/`quantity_status`（计量单位缺失时`PENDING_AUTHORITY`，见2026-09-14 DPPMS订单模板）、`source_lifecycle_status`等。

### 3.3 实施范围 `pm_project_product_line`（353,030行）→ `com_delivery_scope`

与执行单/订单域直接相关的分配关系：`projectId`→`project_id`（关系）；`orderNumber`+`lineNum`→`order_line_id`/`order_no`/`line_no`（须`orderNumber+lineNum+itemCode`唯一命中ERP订单行）；`itemCode`/`itemName`→`item_code`/`item_desc`；`projectQuantity`→`allocated_qty`；`contractNo`、`orderQuantity`、`deliverQuantity`、`openQuantity`只进载荷（不设`legacy_*`列）。0/N命中或缺键只写`PENDING_MAPPING`问题不写正式范围；唯一命中多项目且缺分配量为`PENDING_QUANTITY`，不参与统计。

## 4. 设备装箱单、发货记录与设备档案

### 4.1 规格DDL命名与实库模型的对应

字段级映射证据按规格DDL目标生成；实库（F-AST-001/002已实现）为另一套命名并含增强字段。两者尚未统一（属DDL漂移裁决`BLK-002`及AI-MIG-011/012实施范围，本报告不裁决）：

| 迁移证据目标（规格DDL） | 实库表 | 实库增强（无dppms来源） |
|---|---|---|
| `ast_device_sn`（SN主档） | `ast_device` | `product_model/product_name/product_desc`、`name`、客户归属（`customer_id`+版本）、站点/位置（`site_id`/`site_location_id`/`location_*`）、CONP在网信息（`conp_version/type/series/mark`）、`shipment_record_id`、`sync_status`等 |
| `ast_device_shipment_event`（生命周期事件） | `ast_device_shipment` | `related_device_sn`、`sync_status`等 |
| `ast_device_project_assignment`（项目归属） | `ast_device_project_relationship` | `current_direct_device_sn`、`operation_id`、`assignment_version`等 |
| `ast_device_relation`（设备间关系） | `ast_device_relationship` | 结构一致（SN外键式、含`contract_no`） |

### 4.2 装箱单 `fb_shipment`（156,368行）→ `com_shipment_package`

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `packlist_id` | `package_no` + `source_record_key` | 直接+血缘 |
| `con_id` | `shipment_contract_ref_id` | 关系（定位`fb_contract`发货合同归属；未命中保留原值+`SHIPMENT_CONTRACT_REF_NOT_FOUND`） |
| `packdate` | `shipment_time` | 直接 |
| `warrantyStartTime` / `warrantyEndTime` | `warranty_start_time` / `warranty_end_time` | 直接 |
| `receiveName` | `receiver_name` | 直接 |
| `emsNum` / `emsCompany` | `express_no` / `carrier_name` | 直接 |

### 4.3 发货条码 `fb_shipment_barcode`（4,194,864行）→ 发货事件 + SN主档

每条源行保留为生命周期事件（不因SN重复丢弃）；3,406,054个不同SN形成设备主档候选。

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | 事件表`source_record_key` | 血缘 |
| `pack_id` | 事件表`legacy_package_key` | 直接（仅保留有后续解析用途的） |
| `barcode` | 事件表`device_id`（关系）+ SN主档`sn` | 关系 |
| `item` | SN主档`item_code`（实库`ast_device.product_code`） | 直接；`item2`不并入主设备物料 |
| `com_barcode` | SN主档`internal_serial_no` | 直接 |
| `barcode2` / `item2` | SN主档`secondary_sn` / `secondary_item`（规格口径：最新发货合同关系缓存，可重建）；正式关系以`fb_shipment_barcode_relation`为准 | 直接 |
| `rma_no` | 事件表`rma_no`（逐字保存；`rma_marked=1`；动作码未确认前`UNCLASSIFIED`） | 直接 |
| `rmaBarcode` | 事件表`rma_related_sn`（RMA替换候选） | 直接 |
| `warrantyStartDate` / `warrantyMonth` | 事件表`warranty_start_date` / `warranty_month` | 直接 |
| `updateTime` / `syncTime` | 事件表`update_time` / `source_sync_time`（`syncTime`亦进SN主档） | 直接 |
| `isRMA`、`orderNumber`、`lineNum`、`profitCenter`、`soleAgentSuffix`、`uuid` | `source_payload` | 载荷；`orderNumber/lineNum`仅唯一命中ERP订单行才写事件`order_line_id`，其余`PENDING_MAPPING` |

### 4.4 合同维度附加SN关系 `fb_shipment_barcode_relation`（65,406行）→ `ast_device_relationship`

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `source_record_key` | 血缘 |
| `sn1` / `sn2` | `source_device_id` / `target_device_id`（实库按SN引用） | 关系 |
| `item1` / `item2` | `source_payload`（物料分别保存在各自设备主档） | 载荷 |
| `contract` | `contract_id`（关系）+ 载荷 | 关系 |
| `createtime` | `effective_from` + `create_time` | 直接 |
| `updatetime` | `update_time` | 直接 |

同一主SN允许按不同合同保存不同关系（权威历史）；主档缓存按最新发货合同回填，可重建不可手改。

### 4.5 SN项目归属与转移 `pm_project_shipment`（1,064,774行）→ `ast_device_project_relationship`

| 旧字段 | 目标字段 | 处置 |
|---|---|---|
| `id` | `source_record_key` | 血缘 |
| `projectId` | `project_id`（+项目发生时快照：`project_code/name/customer/company/department`） | 关系+直接 |
| `barcode` | `device_id` + `device_sn` | 关系 |
| `itemCode` | `item_code` | 直接 |
| `packdate` | `effective_from` | 直接 |
| `installAddress` | `install_address` | 直接 |
| 审计四字段 | `creator/create_time/updater/update_time` | 直接 |
| `itemModel`、`itemName`、`receiveName`、`emsNum`、`emsCompany`、`contractNo`、`chProjectId`、`chContractNo`、`transferProjectId`、`transferContractNo`、`transferFlag` | `source_payload`（转移历史经`transferFlag=1/0`+`ch*/transfer*`形成转出/转入记录；`-1`为普通归属候选） | 载荷（转移字段按规则进正式转移链） |

SN源未命中生成`SN_SOURCE_NOT_FOUND`；跨项目SN时间链完整才定唯一当前归属，否则`SN_MULTI_PROJECT_CONFLICT`。

## 5. 通用迁移承载表（实库已落地）

所有域共用的证据与血缘机制：

| 实库表 | 用途 |
|---|---|
| `plt_migration_source_record` | 逐源行完整原值载荷（所有`source_payload`处置的落点） |
| `plt_external_key_mapping` | 旧库主键/业务键 → 目标ID外部键映射（所有`血缘`处置的落点） |
| `plt_migration_issue` | 迁移问题（`PENDING_MAPPING`、`*_NOT_FOUND`、`*_CONFLICT`等） |
| `plt_migration_batch` / `int_sync_*` | 批次与同步运行记录（DPPMS订单模板已使用） |

## 6. 未决事项（不阻断本梳理，影响后续实施）

- 行业划分维度字段命名统一（`Q-MIG-DIM-001`，已关闭）：规格DDL已按裁决修订并复验（见第1节哈希），V258已建表由列名更正与注释精简迁移（现为合并迁移V294）落实；执行单两表列名源字段口径另见`Q-MIG-DIM-003`（已关闭，未建表无需迁移）。

- CRM执行单6张目标表未在实库建表；`com_order_change_relation`（订单变更血缘）同样未建表。
- 设备域规格DDL命名（`ast_device_sn`系）与实库模型（`ast_device`系）未统一，设备域迁移的物理落点需在AI-MIG-011/012实施前对齐（关联`BLK-002`）。
- 合同主档生成方式（多来源字段优先级）需求方重新选择中（F-COM-001 2026-09-17）。
- `rma_no`正式动作码（`BLK-010`）、跨项目SN当前归属（`BLK-008`）、订单行计量单位与可分配基数（`BLK-006`）仍为OPEN。
- `npdms-domain-test`环境（24306/`npdms_domain_test`，401表）2026-09-18直连核实：仅合成数据（商务订单1行、订单行9行、设备39行、`plt_*`迁移证据表0行），无真实dppms数据。`npdms_test`（23316）中曾加载的DPPMS订单真实数据（81,673条）为模板验证数据，任务已暂停，不代表生产迁移启动；该容器MySQL凭据当前不可登录（F-COM-001已记录）。
