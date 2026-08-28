# 数据元、旧库与当前业务对象映射审查

> 文档状态：已核实映射基线，目标物理结构仍按各自文档状态执行
> 基线日期：2026-08-04
> 实现审查基线：`implement/cp-foundation`，提交`ff47f2c32223d053efafb8b2f8d8bfcfffacd0ed`
> 数据边界：旧`dppms`只允许`SELECT/SHOW`只读访问；新平台使用独立MySQL 8.x数据库；禁止跨库SQL

## 1. 结论

1. **当前实现不能完整承接现有业务迁移。** 当前工作树只有14个PMS持久化DO，主要覆盖客户、联系人、基础项目、项目成员、访问范围、通用文档、审计、同步和流程绑定；合同、订单、订单行、实施范围、CRM执行单、发货、SN和设备生命周期尚未进入版本化迁移。
2. **既有物理草案已扩展为29表并完成核心字段级审查，但它仍是设计草案。** 草案已覆盖项目树、项目参与方、项目组合、合同N:N订单、项目N:N合同、订单1:N订单行、项目订单行实施范围、执行单辅助关系、特殊合并下单、订单变更、装箱单、SN生命周期、逐源行迁移证据、外部键、迁移问题和汇总表；这些表尚未成为当前实现的Flyway迁移和DO。
3. **当前`pms_project`必须重构后再迁移。** 单值`contract_code`与已确认的项目—合同、合同—订单多对多关系冲突；仅有`root_project_id`不足以表达正式、非固定层级的子项目树，也缺少`parent_id/tree_path/tree_depth/tree_sort`。
4. **数据元与旧库证明还存在领域缺口。** 项目参与方、项目转派历史、交付件业务清单、设备版本/配置/拓扑、技术公告、故障/ITR，以及阶段、计划、任务、割接、验收、维保、备件和外协等对象没有完整物理实现。
5. **项目实施主粒度继续采用ERP订单行范围。** CRM执行单及其配置只作为辅助证据；特殊合并下单和执行单改单都不能把执行单提升为最小实施范围。大型项目按地区/局点建立正式子项目，再由`pms_project_order_line_scope`分配订单行数量。
6. **结构化数据元已成为默认读取源。** 后续AI先读取`evidence/data-elements`；只有结构化证据不足、源文件哈希变化或必须核验Excel专有显示语义时，才回溯`需求/数据元.xlsx`。

## 2. 证据基线与优先级

### 2.1 数据元结构化基线

| 项目 | 当前结果 |
| --- | --- |
| 原文件 | `需求/数据元.xlsx` |
| SHA-256 | `4250DD8D53C5C312B8C5141A0F626EF80079224D6E2D477978DB697DD85A0116` |
| 提取方式 | XLSX单元格和公式直接导入，无截图、无OCR |
| 隐藏列 | 已使用范围内全部列均读取，隐藏列值包含在结构化结果中 |
| 业务数据元 | 197条 |
| 当前结构记录 | 4,013条，其中3,931条物理字段行、82条业务数据元行 |
| ITR记录 | 277条 |
| 逐行原始证据 | 11,613条，保留工作表、行号、单元格范围和值/公式 |

读取入口：

- `../evidence/data-elements/manifest.json`
- `../evidence/data-elements/semantic-elements.jsonl`
- `../evidence/data-elements/schema-records.jsonl`
- `../evidence/data-elements/itr-elements.jsonl`
- `../evidence/data-elements/source-rows.jsonl`

### 2.2 旧库与实现基线

- 旧库：当前`localhost:3306/dppms`，只读提取到290个表结构和42个视图。
- 数据元当前结构页：归一后262个候选表、3,908个不同物理字段；253个候选表可在当前旧库命中，表命中率96.56%；可比较的3,773个字段全部命中当前旧库同名字段。
- 当前实现：`pms-module-project`有13个PMS DO，`pms-module-integration`有1个PMS DO；`asset/cutover/engineering/outsourcing/service`各只有模块骨架，没有DO。
- 正式设计证据：`project-order-physical-schema.mysql.sql`当前包含50张核心迁移评审表和1,065个带中文描述的字段；ADR-0021新增`cus_market_relation`并明确客户/项目不保存`relation_id`，ADR-0022移除4张V3治理表和跨领域物理外键，ADR-0023-Q03新增交付范围明细并调整当前关系与订单—执行单约束。该清单不是平台全量模型；18张核心旧表326个字段有数据库画像，全部活动结构另有3,931条物理字段证据处置，但仍不是生产迁移。

### 2.3 冲突时的证据优先级

1. 已确认业务规则和已接受ADR；
2. 当前只读数据库结构与可复现数据统计；
3. 数据元结构化基线中的业务语义；
4. 当前规格中的业务对象和领域边界；
5. 当前实现代码与版本化迁移，作为“已实现状态”证据，不反向覆盖已确认需求；
6. Excel原文件，仅在结构化证据不足或哈希变化时读取；
7. 名称含“备份”或`-bak`的工作表，仅作历史补充，不作为当前结构基线。

## 3. 状态说明

| 标识 | 含义 |
| --- | --- |
| I | 当前实现已有版本化表和PMS DO |
| D | 正式设计中已有对象或物理草案，但当前实现未落地 |
| G | 数据元或规格要求存在，当前实现和既有物理草案均不完整 |
| C | 当前实现与确认关系或迁移语义冲突，必须前向重构 |
| R | 旧数据只作为迁移、同步或辅助关联证据，不成为目标权威主档 |
| X | 旧对象停用、仅历史或明确忽略 |

## 4. 三方业务对象映射

| 业务对象 | 数据元/规格依据 | 旧库主要证据 | 当前实现 | 目标设计 | 状态与结论 |
| --- | --- | --- | --- | --- | --- |
| 公司 | 公司是业务主体；租户与所属公司分离 | `ehr_company`、`t_company`、`fnd_company` | `system_tenant`存在，无公司主档 | 需`system_company`及用户—公司—部门业务上下文 | G：不能用租户代替所属公司，也不能从共享部门反推公司 |
| 部门 | 部门编码全平台共享 | `ehr_department`、`fnd_department` | `system_dept`存在但没有`code` | 扩展`system_dept.code` | C：内部ID关联，编码用于集成和对账 |
| 系统账号 | 系统用户与EHR员工分离；工号必须入用户表 | `t_user/t_user_info`、`fnd_user_info`、`ehr_employee` | `system_users`存在，无`employee_no/account_type` | 平台身份迁移文档已定义扩展 | C：迁移前必须补字段和身份归并规则 |
| 员工集成目录 | 只读查找公司或部门人员、按工号建账号 | `ehr_employee/ehr_job/ehr_department` | 无 | 只读集成表/缓存和外部键映射 | G/R：不得成为权限主体 |
| 菜单/角色/权限 | 菜单、操作、数据和字段权限分层 | `t_*`、`fnd_menus/fnd_user_menus/fnd_user_power` | 平台角色菜单已存在 | 需项目授权配置、字段规则、服务范围 | G：`fnd_role_menus`停用并忽略 |
| 外部人员项目授权 | 外部账号按有效转派查看有限项目 | 未匹配EHR的旧账号、`fnd_user_power` | `pms_project_member`、`pms_project_access_scope`过于简化 | `pms_project_assignment`、服务范围、访问配置 | G/C：成员关系不能替代转派事实和期限 |
| 客户 | 客户编码、名称、地址、行业、服务等级 | 数据元中的`pm_account`名称已漂移；项目和CRM表保留客户字段 | `pms_customer` | 扩展行业、服务等级和来源映射 | I/G：基础主档可用，属性不完整 |
| 客户联系人 | 单位、部门、职位、姓名、电话、邮箱、地址 | 数据元中的`pm_account_contact`名称已漂移；旧项目/CRM联系人字段 | `pms_customer_contact` | 增加联系地址或确认复用客户地址 | I/G：主要字段已覆盖，联系地址缺口 |
| 项目主档 | 项目编码、名称、客户项目名称、公司—部门组合、办事处、行业、实施方式、级别、状态 | `pm_project`；`pm_project_header`只是`projectType='10'`视图 | `pms_project` | 树形`pms_project`及`pms_project_company_department_rel` | C：公司与部门主数据分离，但业务组合必须同行保存 |
| 正式子项目树 | 子项目有独立负责人、计划、状态和验收 | 旧库没有可直接等同的新项目树 | 仅`root_project_id` | `parent_id/root_id/tree_path/tree_depth/tree_sort` | C/D：旧项目先迁根节点，新子项目按业务创建 |
| 非树项目关系 | 扩容、续采、改造、改单血缘 | 旧项目编码/历史关系只能作辅助 | 无 | `pms_project_relation` | D：不得塞进父子树 |
| 项目组合 | 查询、治理、统计组合，不改变项目父子 | `pm_project_group*`是旧合同关系技术桥 | 无 | `pms_portfolio/pms_portfolio_project_rel` | D/R：旧项目组不直接迁为项目组合 |
| 项目参与方 | 合同客户、中标代理商、最终用户、服务提供商 | `pm_project`、`pm_project_property*_from_sms`中的名称/编码 | 无独立对象 | `pms_project_party`按角色保存，来源快照同时保留 | D：已补物理草案，仍待版本化迁移和DO |
| 项目干系人/成员 | 销售、服务经理、项目经理、其他成员及生效时间 | 旧项目责任字段、成员和日志表 | `pms_project_member` | 增加角色有效期并与转派事务同步 | I/G：当前唯一键还限制同用户多角色历史 |
| 项目转派 | 内外部实施责任、期限、转派链 | 旧责任字段只能辅助还原 | 无 | `pms_project_assignment` | G：必须保存来源、目标、角色、有效期、状态和历史 |
| 合同主档 | 项目1:N合同；合同N:N订单；所属公司+合同号唯一 | `sms_ofst_contract_head_sap`最接近回款依据；ERP补公司；`fb_contract`仅发货归属 | 无；项目上有冲突字段`contract_code` | `pms_contract/pms_contract_receivable` | D/C：禁止以单合同号或`batch_code`伪造公司 |
| 项目—合同关系 | 多对多可追溯 | `pm_project_group_relationship → pm_project_group → pm_project_contract` | 无，只有项目单值`contract_code` | `pms_project_contract_rel` | D/C：现有字段执行扩展—迁移—收缩 |
| 销售订单 | 订单含合同号、执行单号、项目编码 | `pm_order_data_from_erp`，`pm_order_data_from_sap`为视图/兼容名 | 无 | `pms_sales_order` | D：订单主档不能直接绑定唯一合同或项目 |
| 合同—订单关系 | 合同N:N订单 | 订单头`contractNo`及重复头关系 | 无 | `pms_order_contract_rel` | D：合同号从订单关系化，不作唯一外键 |
| 销售订单行 | 订单1:N订单行，发货数量更新基于订单行 | `pm_order_line_from_erp` | 无 | `pms_sales_order_line` | D：是实施和发货关联的基础粒度 |
| 项目订单行实施范围 | 合同、订单、订单行可拆到不同正式子项目；按局点分配数量 | `pm_project_product_line` | 无 | `pms_project_order_line_scope` | D：是实施主链；执行单不是必填父对象 |
| CRM执行单 | 辅助关联；可能改单、重签/沿用合同、生成新ERP订单 | `pm_project_property_from_sms`、`pm_project_property_af_from_sms` | 无 | `pms_crm_execution_order` | D/R：普通与安服补充头归并为同一执行单 |
| CRM执行单配置 | 数据不全；是否安服取决于是否有安服类产品配置 | `pm_project_real_product_line_from_sms`、`pm_project_product_af_from_sms` | 无 | `pms_crm_execution_config` | D/R：无配置是UNKNOWN，不得判为非安服 |
| 特殊合并下单 | 多CRM项目/执行单合并为合同和订单，实施按订单配置行归属拆分 | `pm_project_soleagent_lend_from_sms`、旧拆单过程 | 无 | `pms_execution_merge_batch/member` | D/R：保存主执行单和全部成员，成员数不写死 |
| 订单变更血缘 | 取消重建、退货、未执行行取消、新增改动行；`-L`只是表现 | 旧过程、订单/退货关联、后缀 | 无 | `pms_order_change_rel` | D：没有明确证据不得仅凭后缀推断 |
| 发货合同归属 | 发货链中的合同归属，不是合同主档 | `fb_contract` | 无 | `pms_shipment_contract_ref` | D/R：不能与正式合同主档行数对等 |
| 装箱单 | 设备发货批次 | `fb_shipment` | 无 | `pms_shipment_package` | D |
| SN设备主档 | 序列号是资产最小追踪单元 | `fb_shipment_barcode.barcode/item` | 资产模块无DO | `pms_device_sn` | D：主SN只对应单一物料；主档缓存该SN最新发货合同匹配的附加SN关系 |
| 设备物流生命周期 | RMA、借转销、借转退、返还、再发放 | 每条`fb_shipment_barcode`及`rma_no` | 无 | `pms_device_shipment_event` | D：每条源记录保留为事件，行为字典未确认前为未分类 |
| SN项目归属 | 设备可跨项目转移 | `pm_project_shipment` | 无 | `pms_project_device_assignment` | D：当前归属由完整事件链计算，不覆盖历史 |
| 设备关系 | 合同维度的主SN—附加SN、RMA替换等关系 | `fb_shipment_barcode_relation.sn1/item1/sn2/item2/contract`、`barcode2/item2`、`rmaBarcode` | 无 | `pms_device_relation` | D：关系表保存权威历史，主档只缓存当前最新关系 |
| 设备型号与数量 | 产品、型号、描述、订单/发货/未发数量 | ERP订单行和项目实施范围 | 无 | 订单行+实施范围+汇总表 | D：汇总可重建，不能另建权威数量主档 |
| 设备版本/配置/拓扑 | 出厂/在网版本、配置、部署、功能、拓扑 | 旧设备/版本/配置表和数据元 | 资产模块无DO | 29表草案仅覆盖SN基础 | G：资产域需版本历史、配置和拓扑对象 |
| 交付件 | 11类交付件、模板、阶段、完整性和审核 | 旧文件、基础交付模板、维护/外协交付件表 | `pms_business_document/pms_document_version` | `pms_deliverable_template/pms_project_deliverable` | I/D：文件载体已实现；交付件实例/模板需以前向迁移新增 |
| 阶段/里程碑/计划/任务/风险 | DR-COM-004/006/007/009及正式子项目独立计划 | `pm_project_task`等旧过程表 | 项目模块尚无对应DO | 29表草案不覆盖 | G：不能以通用流程或文档代替领域对象 |
| 技术公告 | 产品、版本、公告、风险、规避和解决方案 | `prob_*`等表和数据元 | 资产模块无DO | 29表草案不覆盖 | G：DR-COM-014未物理实现 |
| 故障/ITR | SN、问题单、状态、类型、根因、解决方案、报告 | ITR表和项目问题视图 | 服务模块无DO | 29表草案不覆盖 | G：应由服务域拥有，项目仅关联和汇总 |
| 割接、验收、维保、备件、外协 | 分卷规格和旧过程数据 | 对应旧业务表 | 对应模块仅骨架 | 29表草案不覆盖 | G：属于后续领域落表，不阻断先迁核心项目—订单—设备链 |
| 同步、外部键、迁移问题 | 一次性业务迁移、辅助关系只读同步、可追溯对账 | 所有源表和源键 | 已有项目同步/集成任务，但无通用外部键和问题表 | `pms_sync_batch/pms_external_key_map/pms_migration_issue` | I/D：现有同步批次需与正式通用模型收敛 |
| 跟踪统计 | 项目树、订单行范围、发货、SN和状态汇总 | 旧视图和缓存表 | 分析模块无DO | `pms_project_delivery_summary` | D：只作可重建读模型，不是权威数据源 |

## 5. 核心字段映射与改造建议

### 5.1 客户、联系人和项目

| 数据元 | 旧库证据 | 当前字段 | 结论 |
| --- | --- | --- | --- |
| 客户编码/名称/地址 | CRM/项目冗余字段；`pm_account`在当前库未命中 | `pms_customer.code/name/address` | 可承接；必须保存来源外部键，不能只按名称合并 |
| 客户行业 | 项目/CRM字段，数据元要求客户维度 | 无客户行业字段；项目有`industry` | 项目行业和客户行业不得混用；客户行业需独立可空字段或受控属性 |
| 客户服务等级 | 数据元标记“缺失、按规则生成/变更” | 无 | 先定义规则和来源，再落`service_level_code`；不能从项目级别猜测 |
| 联系人姓名/部门/职位/电话/邮箱 | 项目和CRM联系人字段 | `pms_customer_contact`基本覆盖 | 增加联系地址或明确地址只属于客户；外部键和去重规则待补 |
| 项目编码/名称 | `pm_project.projectCode/projectName` | `pms_project.code/name` | 可迁移；旧`projectId`必须进入外部键映射 |
| 客户项目名称 | `pm_project.customerProjectName`、CRM执行单 | 无 | 增加可空项目属性，并记录来源优先级 |
| 归属办事处 | 项目/CRM`officeCode` | `office_id` | 需代码映射到目标ID，保留原办事处编码用于对账 |
| 归属母/子公司 | 项目`compId`、订单`compCode`、CRM`corporationCode` | 无稳定公司映射 | 关联公司ID；不同来源冲突进入迁移问题 |
| 实施方式/行业 | 旧项目及CRM | `implementation_mode/industry` | 可承接，但需字典归一 |
| 重大项目级别/项目服务级别 | 旧项目/CRM和数据元 | 无 | 增加受控编码；不从项目类型自动推断 |
| 合同号 | 项目合同技术桥、ERP订单 | `pms_project.contract_code` | 必须迁至`pms_project_contract_rel`，原字段只在过渡期只读兼容 |
| 项目状态 | `pm_project.projectState` | `status` | 需状态映射表；不能直接复制旧数值 |
| 合同客户/代理商/最终用户/服务提供商 | CRM和项目名称字段 | 无 | 建`pms_project_party`及角色，不继续在项目主表扩四组固定列 |

### 5.2 项目树、成员和授权

1. 现有`pms_project.root_project_id`只支持根过滤，不能证明父子关系和任意层级。
2. 目标项目树统一采用`parent_id/root_id/tree_path/tree_depth/tree_sort`；根项目`parent_id=NULL`、`root_id=id`、`tree_depth=0`。
3. 旧`pm_project_group*`只解析历史项目合同关系，不产生新父子关系或项目组合。
4. `pms_project_member`保存当前有效成员；`pms_project_assignment`保存责任转派事实和历史，两者生效、撤销、到期必须同事务一致。
5. 成员应允许同一用户在同一项目具有多个角色或角色历史；现有唯一键`(tenant_id, project_id, user_id)`需要调整。
6. 外部人员授权至少同时满足账号有效、转派有效、项目成员有效、访问配置有效、菜单/操作允许和字段策略允许。

### 5.3 合同、订单、订单行与实施范围

| 源字段/关系 | 目标 | 强制规则 |
| --- | --- | --- |
| `sms_ofst_contract_head_sap`每条源行 | `pms_contract_receivable` | 完整保留回款金额、客户、公司、部门、有效期和来源载荷 |
| 回款合同号+ERP唯一`compCode` | `pms_contract` | 正式键为`tenant_id + company_code + contract_no` |
| `fb_contract` | `pms_shipment_contract_ref` | 仅发货归属，不创建合同主档 |
| `pm_project_group_relationship → pm_project_group → pm_project_contract` | `pms_project_contract_rel` | 用旧项目ID外部键定位；按项目、合同、角色去重 |
| `pm_order_data_from_erp` | `pms_sales_order` | 业务键为`tenant_id + source + compCode + orderType + orderNumber` |
| 订单头`contractNo` | `pms_order_contract_rel` | 一条订单可关联多个合同 |
| 订单头/行执行单号 | `pms_order_execution_rel` | 辅助血缘；不决定实施最小范围 |
| `pm_order_line_from_erp` | `pms_sales_order_line` | 订单行先定位订单主档；退货行允许负数量 |
| `pm_project_product_line` | `pms_project_order_line_scope` | 项目/订单行/分配数量共同构成实施范围 |
| 特殊合并关系 | `pms_execution_merge_batch/member` | 保存原始成员和主执行单；最终按订单行范围拆项目实施 |
| 明确改单/退货/拆单证据 | `pms_order_change_rel` | `-L`后缀不能单独作为血缘证据 |

实施数量规则：

- 单订单行只关联一个项目且证据唯一时，可用订单数量形成有效实施范围。
- 同一订单行分配到多个子项目时，必须有每项目分配数量，且合计通过订单数量校验。
- 分配数量缺失时状态为待补数量，不参与完成率、交付数量和验收门禁。
- 订单行发货数量来自ERP数量更新；SN事件用于设备级追踪，二者分别对账，不能要求条数相等。

### 5.4 发货、SN、版本、公告和故障

| 数据元 | 目标对象 | 当前覆盖 | 补充要求 |
| --- | --- | --- | --- |
| 产品/型号/描述、订单/发货/未发数量 | 订单行、项目订单行范围、交付汇总 | 仅正式草案 | 汇总可重建，禁止复制为第二权威主档 |
| 序列号、产品编码/名称、在保/在维 | SN主档、设备事件、项目归属 | 仅正式草案的基础字段 | 保修状态需明确计算源和时间口径 |
| 安装地址 | 设备项目归属或实施位置 | 无 | 需允许设备随项目/局点迁移并保留历史 |
| 出厂/在网版本、定制版本 | 设备版本历史 | 无 | 版本必须带生效时间、来源和采集方式 |
| 配置信息、部署模式、启用功能、运行业务 | 设备配置历史 | 无 | JSON只能承载原始载荷，常用查询字段应结构化 |
| 网络拓扑 | 拓扑对象及版本 | 无 | 不能只存文件URL，至少要保存项目/设备关联和版本 |
| 技术公告命中 | 公告主档、公告版本、设备命中关系 | 无 | 命中结果需保存规则版本和判定时间 |
| 故障处理记录 | ITR问题单、设备关联、报告文档 | 无 | 服务域拥有问题主档，项目和设备只保存关系 |
| 11类交付件 | 交付件模板、项目交付件实例、文档版本 | 只有通用文档/版本 | 必交/选交、阶段、签字、审核和完整性必须独立建模 |

## 6. 当前数据量与迁移质量风险

### 6.1 核心对象实时统计

| 旧对象 | 当前行数/键分布 | 映射影响 |
| --- | --- | --- |
| `pm_project` | 83,550行；其中`projectType=10`为80,320行，`afss`为2,534行，`afxx`为696行 | `pm_project_header`只覆盖类型10，迁移不能只读该视图 |
| `pm_project_header` | 80,320行 | 视图，不单独迁移 |
| `pm_project_contract` | 86,780行；86,733个不同合同—项目组对 | 47条多余关系需去重并保留来源映射 |
| `pm_project_group_relationship` | 85,472行；85,469个组、84,803个项目编码、75,097个SMS项目编码 | 仅作为项目合同技术桥，不能推断项目树 |
| `pm_project_property_from_sms` | 102,520行 | CRM执行单辅助头，需按来源+执行单号归并 |
| `pm_project_property_af_from_sms` | 3,120行 | 安服补充头，不是独立执行单类型 |
| `pm_project_product_af_from_sms` | 20,536行 | 安服产品配置证据；不能仅凭执行单头判安服 |
| `pm_project_soleagent_lend_from_sms` | 1,441行；729个合并标识、1,405个执行单、725种订单集合 | 保存全部成员；当前分布不能固化为固定成员数 |
| `pm_order_data_from_erp` | 91,572行；87,865个订单号；91,239个确定性业务键 | 333条业务键重复/冲突候选必须逐组处理 |
| `pm_order_line_from_erp` | 380,605行；379,522个确定性订单行业务键；40行订单号为空 | 1,083条重复/冲突候选及40条空订单号进入问题表 |
| `pm_project_product_line` | 353,030行 | 每条必须映射实施范围或迁移问题 |
| `sms_ofst_contract_head_sap` | 81,547行 | 回款依据；所属公司仍需ERP等证据解析 |
| `fb_contract` | 120,639行 | 全量进入发货合同归属，不与合同主档数量对等 |
| `fb_shipment` | 156,368行 | 装箱单全量迁移，孤立关系进入问题表 |
| `fb_shipment_barcode` | 4,194,864行；3,406,054个SN；832,873行有`rma_no` | SN去重建主档，源行逐条建生命周期事件 |

### 6.2 数据元与当前库漂移

当前结构页总体可信，但不是数据库结构的最终权威源：

- 9个数据元候选表在当前库未命中：`agent_info`、`app_comment`、`dptech_v_project_product_config_level_info`、`pm_account`、`pm_account_contact`、`pm_project_soft_version_history`、`project_info_from_sms`、`t_data_field_relation`、`view_warranty_contract_state`。
- 当前库新增而数据元未收录的代表字段包括`pm_project_maintenance_view`的3个评价字段、`view_warranty*`的`newid/synctime`、`t_mails.failedmessage`、`department.region`和`warehouse.analysisdepartment`。
- `hexiao`在数据元中没有字段行，当前库实际有9个字段。
- 因此：业务语义优先查数据元结构化基线；物理字段、索引和约束以当前数据库为准；两者冲突必须形成问题记录或规格变更。

### 6.3 已确认的SN—物料与附加SN规则

1. `fb_shipment_barcode.barcode`是主SN，`item`是该SN的单一物料编码；目标映射到`pms_device_sn.sn/item_code`。
2. 特殊情况存在额外的`barcode2/item2`。第二SN不是主SN的第二个物料属性，而是另一条独立`pms_device_sn`记录。
3. `fb_shipment_barcode_relation`以`sn1/item1 → sn2/item2 + contract`保存合同维度的正式映射，迁移到`pms_device_relation`；同一主SN在不同合同下允许有不同关系。
4. `pms_device_sn.secondary_sn/secondary_item`缓存该SN最新发货合同下匹配的附加SN和物料；不保存`secondary_contract_id/secondary_relation_id/secondary_effective_time`。
5. 权威关系仍是`pms_device_relation`。按具体合同或历史时间查询必须读取关系表，不能依赖主档缓存。
6. 当前缓存先按设备发货事件确定最新发货合同，再在该合同内按“关系有效、关系时间倒序、关系ID倒序”确定唯一记录；没有候选时同时清空`secondary_sn/secondary_item`。
7. `rmaBarcode`仍按RMA替换关系候选处理，不与附加SN关系混为同一类型。

## 7. 必须先处理的设计冲突

### P0：不处理就无法安全迁移

1. 将29表项目—合同—订单—设备草案转为前向版本化迁移和DO，不允许直接执行评审草案覆盖现表。
2. 合并两版`pms_project`：保留当前主键和已用字段，前向补齐树、公司、部门、负责人和关系；迁出`contract_code`后再收缩兼容字段。
3. 补齐公司、用户工号/账号类型、部门编码及外部键映射，否则合同所属公司、项目责任人和权限都无法稳定关联。
4. 建`pms_external_key_map`、`pms_migration_issue`和统一批次模型，确保每条旧记录只能“成功映射或有明确问题”，不能静默丢弃。
5. 对订单头333条、订单行1,083条重复/冲突候选及40条空订单号逐组分类；正式迁移不得简单取最大ID。
6. 实现项目订单行范围及数量门禁，否则特殊合并、订单行拆子项目和局点实施无法表达。
7. 明确目标ID生成策略。29表草案的`BIGINT`主键没有`AUTO_INCREMENT`，必须决定复用平台ID生成器；旧ID只进入外部键映射，不能与目标ID混用。

### P1：核心业务上线前补齐

1. `pms_project_party`已进入物理草案；下一步需补合同客户、代理商、最终用户、服务提供商的角色字典、归并规则和DO。
2. `pms_project_assignment`、项目访问配置、菜单/操作/字段规则和外部人员期限门禁。
3. 项目交付件模板、实例、状态和通用文档版本的关系。
4. 客户行业/服务等级、项目客户名称、级别、公司和部门等数据元字段和字典映射。
5. 设备版本、配置、安装位置、拓扑、技术公告命中和ITR问题关系。
6. 为跨项目订单行数量分配定义并发控制：锁定订单行或分配余额、版本校验和失败重试，避免并发超配。
7. 定义辅助只读同步的稳定源键、源记录消失、更正、撤销、迟到数据和重跑语义。
8. V1虽是单租户，物理草案启用多租户前仍要复核仅按`id`建立的外键；需要时改为带`tenant_id`的复合候选键和复合外键。
9. 明确汇总表的增量刷新、全量回算、版本、失败恢复和与事务明细的延迟口径。

### P2：按领域分期落表

- 阶段、计划、任务、风险、割接、验收、维保、备件和外协过程对象。
- 分析汇总和搜索索引；它们必须可由权威明细重建。

## 8. 迁移与同步顺序

```text
平台身份/公司与部门编码
  → 客户与联系人
  → 项目主档、树、参与方、成员和转派
  → 合同回款与合同主档
  → 项目合同关系
  → ERP订单头、合同关系和执行单辅助关系
  → ERP订单行
  → 项目订单行实施范围
  → 发货合同归属和装箱单
  → SN主档、物流事件、项目归属和设备关系
  → 交付件、版本/配置、公告、ITR及其他领域过程数据
  → 可重建汇总和统计读模型
```

执行要求：

1. 旧库只读抽取到迁移程序或文件，不在SQL中跨库连接。
2. 业务数据一次性迁移；CRM执行单辅助信息、ERP订单/发货状态等后续只读同步按独立批次运行。
3. 每个目标写入必须带来源系统、来源对象、来源键、批次号和幂等键；正式业务字段不使用`_snapshot`后缀。
4. 当前实现已有同步批次不得与新通用同步批次并行形成两个权威口径，落地前需决定复用或迁移。
5. 所有关系先解析目标ID，再写业务表；不能用名称、可能重复的项目编码或单合同号直接建立永久外键。

## 9. 查询和统计性能结论

对象层级增加不会必然导致项目查询变慢，前提是查询始终从确定的主链和索引进入：

- 项目树：`(tenant_id,parent_id,tree_sort,id)`加载直接子节点，`(tenant_id,root_id,tree_path)`加载后代。
- 项目实施订单：`pms_project_order_line_scope(project_id,status,order_line_id)`进入，连接订单行和订单头；不需要先遍历合同和执行单。
- 订单反查项目：按`order_line_id`反查实施范围；订单头级列表先分页订单，再批量聚合项目范围。
- 设备发货：SN事件按`device_id/event_time`和`order_line_id`索引；项目全景不直接扫描四百多万事件。
- 当前附加SN：设备列表直接读取`pms_device_sn.secondary_sn/secondary_item`；刷新时先按设备事件索引取得最新发货合同，再使用`pms_device_relation`的主SN+合同组合索引。
- 项目统计：`pms_project_delivery_summary`作为可重建读模型；事务明细仍是权威源。
- 所有列表必须分页，禁止在项目全景中逐项目发起N+1查询。

因此，性能风险主要来自错误的查询入口、缺少组合索引和实时扫描全量事件，不是来自模型中存在合同、订单行、范围和事件这些必要层级。

## 10. 迁移验收门禁

1. 数据元源文件哈希与`manifest.json`一致；不一致先重新生成结构化基线和漂移报告。
2. 每个迁移源表记录数等于“成功外部键映射数 + 明确迁移问题数”，没有静默丢失。
3. 项目、合同、订单、订单行、范围、装箱单和设备事件分别按确定性业务键复核唯一性。
4. 项目合同、合同订单、订单执行单和项目订单行关系无悬空目标ID。
5. 多项目分配同一订单行时，只有分配数量完整且合计通过的记录进入有效状态。
6. SN主档去重数与设备事件源行数分别对账；不得用SN主档数代替事件数。
7. 外部人员必须通过有效转派、成员、访问配置、菜单/操作和字段策略联合鉴权。
8. 项目列表、项目全景、订单反查项目、项目反查实施订单、SN生命周期和树汇总完成真实数据量级性能测试。

## 11. 仍需业务确认的明细

1. 客户服务等级的生成规则、变更权限和历史保留方式。
2. 合同所属公司无法由ERP唯一解析时的责任人和处理时限。
3. 合同客户、代理商、最终用户和服务提供商的主数据来源、去重键及是否允许只存名称待认领。
4. 项目成员是否允许同一用户同时承担多个角色，以及角色历史的展示口径。
5. 交付件11类是否为固定基础字典，还是按项目类型/阶段配置模板。
6. 设备安装地址、版本、配置和拓扑分别由哪个系统或实施角色维护。
7. `rma_no`各行为码的正式字典和设备当前状态计算规则。
8. 备件和技术公告是本期完整建设，还是只迁关系并保留外部入口。
9. 目标表ID统一由平台ID生成器还是数据库生成，以及旧ID的展示和追溯方式。
10. 辅助只读同步中源记录删除、更正、重现和迟到数据的处理策略。
11. 项目订单行数量调整的并发锁定和超配防护策略。

## 12. 关联文档

- `project-order-model-options-review.md`
- `project-order-target-schema-evidence.md`
- `project-order-physical-schema.mysql.sql`
- `project-order-migration-mapping.md`
- `platform-identity-access-migration.md`
- `data-dictionary.md`
- `module-boundary-and-naming.md`
- `../../../docs/decisions/0001-project-order-line-scope-model.md`
- `../../../docs/decisions/0002-platform-identity-and-project-scoped-access.md`
- `../../../docs/decisions/0003-contract-scoped-secondary-sn-cache.md`
