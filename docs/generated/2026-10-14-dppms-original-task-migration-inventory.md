# 原验证库任务迁移清单

核对时间：2026-10-14T18:01:18.729001（Asia/Shanghai）。来源：`127.0.0.1:24306/npdms_domain_test`，租户1。
本轮使用数据库只读会话读取任务、连接和回执；没有登录原库应用，没有更改原任务、旧绑定或业务数据。下列分组和迁移次序是整理建议，不是执行批准。

原库共有 **45个未删除任务、3个来源连接，全部任务停用、运行中任务0个**。建议把24个主线候选任务与8个发货/设备任务、5个历史纠正/清理专项、8个验收样本分开处理。24个候选任务也须按新库依赖和专属引用逐项适配，不能直接批量执行。

## 范围与连接

| 连接 | 原连接ID / 数据源ID | 使用任务数 | 迁移处理 |
| --- | --- | ---: | --- |
| DPPMS | `2101561624635936769` / `18` | 42 | 重建相同来源连接，凭据仅经受保护配置传递；新连接ID替换任务connectionId。 |
| DETAIL_ACCEPTANCE_ISOLATED | `2101613038573838337` / `19` | 1 | 原验证库自身的验收测试数据源；不迁入新库主线。 |
| CRM SQL Server | `2102221331933859841` / `20` | 2 | 先重建并验证CRM连接；本轮尚未复制或验证此连接。 |

目前业务主线参数仍为`projectTypes=10`（用服），`projectIds`为空代表不限定项目；部分任务名称还写“10项目验证”或“样本”，应以配置为准。组织补充和验收任务的限定范围另见下表。当前`validatedVersion`、最近执行版本和全量完成状态是不同事实，旧版本回执不能证明当前配置已经验证。

## 45项任务

“来源→目标”列中的来源是任务对象；具体来源表、命名参数、映射数量、适配器和运行ID见文末逐项明细及JSON。目标列为配置/适配器声明的主要目标表，不穷举Owner内部关联表。最近回执含问题或为样本时，不代表全量迁入通过。

### A．基础资料候选任务（12项）

| 编号 / 原任务ID / 版本 | 原任务名称 | 来源对象 → 主要目标 | 迁移处理与依赖 | 最近回执 |
| --- | --- | --- | --- | --- |
| A01 / `2101562667583217666` / v16 | EHR 公司与部门同步 | COMPANY、DEPARTMENT → system_company、system_dept | 保留现有公司映射及部门筛选；部门仅 depGrade<=2 且编码长度=6，并非全组织树。 | 执行 SUCCESS / 171 行 / v16 |
| A02 / `2108538095265562625` / v0 | DPPMS 已裁决 ehr-departments | ehr_department → system_dept | 在公司/上级部门就绪后处理；当前只限定8个已批准部门编码。 | 预览 PREVIEW_READY / 8 行 / v0 |
| A03 / `2108524501517955073` / v1 | DPPMS department-163130 来源迁移验证 | fnd_department → system_dept | 当前仅 departmentCode0=163130；不能据任务名称视为部门整表任务。 | 执行 SUCCESS / 1 行 / v1 |
| A04 / `2108524822998773762` / v0 | DPPMS 已裁决 users | t_user、t_user_info、fnd_user_info → system_users | 先核对公司/部门；在新库重新形成来源用户绑定，保留原审计和同名账号规则。 | 预览 PREVIEW_READY / 1566 行 / v0 |
| A05 / `2108554583850524673` / v0 | DPPMS 已裁决 roles | T_ROLE、FND_ROLE → system_role | 依赖人员审计解析；保留18项来源角色编码映射，不推导新增权限。 | 执行 SUCCESS / 31 行 / v0 |
| A06 / `2108568095503093761` / v0 | DPPMS 已裁决 user-roles | T_USER_ROLE、FND_USER_ROLE → system_user_role | 先有正式用户、角色及相应来源绑定；历史关系不直接生成额外权限。 | 预览 PREVIEW_READY / 1709 行 / v0 |
| A07 / `2108569376783273985` / v0 | DPPMS 已裁决 user-scopes | t_user、t_user_info、fnd_user_info、t_user_role、fnd_user_power → system_user_company_department_scope | 先有公司、部门、用户及所需角色；保留已批准的数据范围规则。 | 预览 PREVIEW_READY / 3275 行 / v0 |
| A08 / `2109351154645135362` / v0 | DPPMS 已裁决基础字典类型（原31＋缺失49） | fnd_basic_data_type → system_dict_type | 保留原类型及补充49组的既定规则；先核对新库已有字典。 | 执行 SUCCESS / 80 行 / v0 |
| A09 / `2109371486114320385` / v0 | DPPMS 已裁决字典项531条（保持14列） | fnd_basic_data → system_dict_data | 依赖字典类型；保留531条承接及1条跳过规则。 | 执行 SUCCESS / 532 行 / v0 |
| A10 / `2102221608959250434` / v2 | CRM客户信息同步 | CUSTOMER → cus_customer_master | 需重建CRM SQL Server连接；映射值域继续以CRM权威来源为准。 | 执行 SUCCESS / 59601 行 / v2 |
| A11 / `2102221608179109889` / v1 | CRM产品信息同步 | PRODUCT → ast_product_official_info | 需重建CRM SQL Server连接；与DPPMS产品名称/型号承接任务配合，避免重复覆盖。 | 执行 SUCCESS / 8441 行 / v1 |
| A12 / `2108534088732168194` / v2 | DPPMS 已有正式产品整表原名称型号承接 | fb_items → ast_product_official_info | 需先准备同定义的实体扩展版本；migrationDefinitionRevisionId=2108533319597477890属于旧库，不能直接沿用。 | 执行 SUCCESS / 34976 行 / v2 |

### B．业务主线候选任务（12项）

| 编号 / 原任务ID / 版本 | 原任务名称 | 来源对象 → 主要目标 | 迁移处理与依赖 | 最近回执 |
| --- | --- | --- | --- | --- |
| B01 / `2101625228492091393` / v11 | DPPMS execution-canonical 来源迁移验证 | EXECUTION_ORDER → com_crm_execution_order | 保留用服筛选及公司码规则；开启自动分页，查看根回执合计，不能只看最后136行。 | 预览根回执 100136 行；51 个分页；PREVIEW_READY |
| B02 / `2101587859478949890` / v31 | DPPMS 订单原字段受保护承接 order-field-valid-efficient | ORDER、LINE → com_sales_order、com_sales_order_line | 当前含91,161项旧库字段承接证明，须另行准备新库首次承接配置；原映射和冲突保护保留。 | 预览 FAILED / 467235 行 / v31 |
| B03 / `2108608417343934465` / v10 | DPPMS 项目主档整表迁移验证（续保多合同样本） | pm_project_header → proj_project | 先有用户来源绑定及组织资料；当前还含6,570项旧恢复凭据和10项现值版本保护，不能直接用于空库首次创建。 | 预览 PREVIEW_READY / 80170 行 / v10 |
| B04 / `2109622534918856706` / v6 | DPPMS identity-carried 整表迁移验证 | pm_project_identity → proj_project | 先有规范主档；保留所有原ID到规范主档的映射，不创建第二份同码项目。 | 预览 PREVIEW_READY / 80320 行 / v6 |
| B05 / `2109363204544983041` / v12 | DPPMS contract-reference-ready 整表迁移验证 | pm_project_contract → com_project_contract_relation | 依赖项目身份和正式合同主档；当前保留18项已批准来源裁决。发货组合中的合同主档步骤须单独明确。 | 预览 PREVIEW_READY / 83472 行 / v12 |
| B06 / `2108574253093683202` / v10 | DPPMS collection-approved-actors 整表迁移验证 | pm_pb_plan_from_sms → com_contract_collection_plan | 依赖正式合同及人员来源解析；保留540个未知审计编码，不默认补建账号。 | 预览 PREVIEW_READY / 35451 行 / v10 |
| B07 / `2109967339937804289` / v4 | DPPMS 合同回款信息整表迁移与同步 | sms_ofst_contract_head_sap → com_contract_receivable | 依赖正式合同引用及既定回款字段映射；不以HTTP成功视为合同业务完成。 | 预览 PREVIEW_READY / 79321 行 / v4 |
| B08 / `2109829769958453249` / v7 | DPPMS项目产品实施范围（整表，10项目验证） | pm_project_product_line → com_delivery_scope | 依赖项目、办事处、有效合同关系和精确订单行；当前105项baseline属于旧库，正数量规则继续有效。 | 预览 FAILED / 2 行 / v6 |
| B09 / `2109865269478027265` / v4 | DPPMS项目真实产品清单（整表，10项目验证） | pm_project_product_line_real → proj_project_real_product | 先有项目来源绑定；保持原产品字段及来源修订关系。 | 预览 PREVIEW_READY / 7848 行 / v4 |
| B10 / `2109865270430134273` / v4 | DPPMS项目租赁及产品配置（整表，10项目验证） | pm_project_product_lease_line_from_crm、pm_project_product_config_level_info_from_crm → proj_project_lease_product、proj_project_product_configuration | 先有项目来源绑定；两类原表独立保留，原缺失项目继续报告问题。 | 预览 PREVIEW_READY / 1402 行 / v4 |
| B11 / `2109968219214897153` / v5 | DPPMS 项目相关方及状态整表迁移与同步 | pm_project_related_party、pm_project_state → proj_project、proj_project_party | 先有项目绑定；55项旧前值证明和扩展定义ID 2109967949395320833需新库适配；不制造当前审批完成事实。 | 预览 PREVIEW_READY / 268878 行 / v5 |
| B12 / `2110010787726274561` / v5 | DPPMS 设备续保记录整表迁移与同步 | fb_service → ast_device_warranty_renewal_record | 保持设备标识、合同号及既定续保映射；与主档及关联层分别核定实际写入范围。 | 预览 PREVIEW_READY / 453454 行 / v5 |

### C．发货与设备相关任务：暂缓执行（8项）

| 编号 / 原任务ID / 版本 | 原任务名称 | 来源对象 → 主要目标 | 迁移处理与依赖 | 最近回执 |
| --- | --- | --- | --- | --- |
| C01 / `2107037031219654657` / v14 | DPPMS 合同、装箱单与发货记录完整同步 | CONTRACT、SHIPMENT_CONTRACT、PACKAGE、DEVICE_SN、DEVICE_RELATION → ast_device_relationship、ast_device_shipment、com_contract、com_shipment_contract_reference、com_shipment_package | 当前v14、5个来源对象；按用户决定暂不执行。后续若仅迁合同主档，应基于此已复核CONTRACT段单独明确范围。 | 预览 FAILED / 166370 行 / v14 |
| C02 / `2101857698189352962` / v5 | 发货合同同步 | SHIPMENT_CONTRACT → com_contract、com_shipment_contract_reference | 与当前组合入口重叠；原库无运行回执，不作为默认迁入入口。 | 无回执 |
| C03 / `2101875784317583361` / v4 | 合同主档生成 | CONTRACT → com_contract | 当前v4，最近成功仍为v3；不能替代当前组合入口中的已复核合同规则。 | 执行 SUCCESS / 20000 行 / v3 |
| C04 / `2101875784728625154` / v1 | 合同装箱单同步 | PACKAGE → com_shipment_package | 与当前组合入口重叠；原库无运行回执，保留配置供追溯。 | 无回执 |
| C05 / `2101875785110306818` / v7 | 发货条码同步 | BARCODE → ast_device、ast_device_shipment | 当前v7，最近回执v6；包含设备主体写入，与当前发货事件/关系入口区分。 | 执行 SUCCESS / 5 行 / v6 |
| C06 / `2101891886242299906` / v6 | DPPMS 发货合同、主档、装箱单及条码完整同步 | SHIPMENT_CONTRACT、CONTRACT、SHIPMENT_CONTRACT_LINK、PACKAGE、BARCODE → ast_device、ast_device_shipment、com_contract、com_shipment_contract_reference、com_shipment_package | 当前v6，最近回执v4；与当前组合任务存在重复目标，保留为历史配置。 | 执行 SUCCESS / 100000 行 / v4 |
| C07 / `2102825819916980226` / v10 | 设备序列号同步 | DEVICE_SN、DEVICE_RELATION → ast_device_relationship、ast_device_shipment | 当前v10最后执行FAILED、读取0；保留历史成功回执与来源关系，不恢复为新库默认入口。 | 执行 FAILED / 0 行 / v10 |
| C08 / `2104524842835423234` / v0 | 发货条码原样承接 | SHIPMENT_BARCODE_RAW、SHIPMENT_BARCODE_RELATION_RAW → imp_shipment_barcode、imp_shipment_barcode_relation | 目标为imp_shipment_barcode及imp_shipment_barcode_relation旧表；最后FAILED，不迁为新功能入口。 | 执行 FAILED / 0 行 / v0 |

### D．历史纠正与清理专项：不作为新库初始化任务（5项）

| 编号 / 原任务ID / 版本 | 原任务名称 | 来源对象 → 主要目标 | 迁移处理与依赖 | 最近回执 |
| --- | --- | --- | --- | --- |
| D01 / `2108545067259985922` / v1 | DPPMS 已裁决557处历史更新人纠正 | fnd_user_info → system_users | 限定原成功迁入用户和完整保护证据；旧目标ID、绑定、回执不迁为新库执行凭据。 | 预览 PREVIEW_READY / 557 行 / v1 |
| D02 / `2108567804242235394` / v0 | DPPMS 13个角色25处空审计人员纠正（待裁决） | fnd_roles → system_role | 原任务名仍写“待裁决”，但决策文档和成功回执确认已批准执行；仅旧批次13角色，不是新库初始化步骤。 | 预览 PREVIEW_READY / 13 行 / v0 |
| D03 / `2108799116748152833` / v0 | 已裁决发货合同空保修年数纠正 | SHIPMENT_CONTRACT → com_shipment_contract_reference | 限定原合同ID 11824、13560及旧正式目标/前值；不将纠正证明复制为新库业务数据。 | 执行 SUCCESS / 2 行 / v0 |
| D04 / `2109382583085117441` / v3 | DPPMS 已裁决65046条关系审计纠正＋360条明确排除 | fb_shipment_barcode_relation → ast_device_relationship | 65,406项逐键凭据含65,046项纠正、360项排除；依赖旧绑定及旧成功运行，不能平移执行。 | 执行 SUCCESS / 65406 行 / v2 |
| D05 / `2109968221270106114` / v0 | DPPMS 旧01订单行迁移绑定清理 | pm_order_line_from_erp → int_sync_binding | 指向原订单任务2101587859478949890；原执行删除236,259旧绑定。新库无这些旧绑定，不重放清理。 | 执行 SUCCESS / 361032 行 / v0 |

### E．验收与专项样本：保留证据，不作为全量迁移入口（8项）

| 编号 / 原任务ID / 版本 | 原任务名称 | 来源对象 → 主要目标 | 迁移处理与依赖 | 最近回执 |
| --- | --- | --- | --- | --- |
| E01 / `2101613039156846593` / v0 | DETAIL_ACCEPTANCE_ISOLATED | ACCEPTANCE → proj_payment_acceptance_binding | 连接直接指向原npdms_domain_test的codex_detail_*测试源；不复制到新库任务链。 | 执行 SUCCESS / 11 行 / v0 |
| E02 / `2107037031798468609` / v1 | DPPMS 完整同步专项预览（每来源500条） | CONTRACT、SHIPMENT_CONTRACT、PACKAGE、BARCODE、DEVICE_RELATION → ast_device、ast_device_relationship、ast_device_shipment、com_contract、com_shipment_contract_reference、com_shipment_package | 样本任务，且当前v1无同版本运行；不能替代当前全量组合任务。 | 预览 FAILED / 500 行 / v0 |
| E03 / `2107040712333811713` / v1 | DPPMS 专项有效主键样本（每来源500条） | CONTRACT、SHIPMENT_CONTRACT、PACKAGE、DEVICE_SN、DEVICE_RELATION → ast_device_relationship、ast_device_shipment、com_contract、com_shipment_contract_reference、com_shipment_package | 仅专项样本；当前v1，最近回执v0。 | 执行 SUCCESS / 2500 行 / v0 |
| E04 / `2107041701187117058` / v0 | DPPMS 专项并发样本 CONTRACT（2000条） | CONTRACT → com_contract | 仅原并发验证证据，不作为常规迁移步骤。 | 执行 SUCCESS / 2000 行 / v0 |
| E05 / `2107041701468135425` / v0 | DPPMS 专项并发样本 PACKAGE（2000条） | PACKAGE → com_shipment_package | 仅原并发验证证据，不作为常规迁移步骤。 | 执行 SUCCESS / 2000 行 / v0 |
| E06 / `2107041701736570882` / v0 | DPPMS 专项并发样本 DEVICE_SN（2000条） | DEVICE_SN → ast_device_shipment | 仅原并发验证证据，不作为常规迁移步骤。 | 执行 SUCCESS / 2000 行 / v0 |
| E07 / `2107041701984034818` / v0 | DPPMS 专项并发样本 DEVICE_RELATION（2000条） | DEVICE_RELATION → ast_device_relationship | 仅原并发验证证据，不作为常规迁移步骤。 | 执行 SUCCESS / 2000 行 / v0 |
| E08 / `2107046961754501121` / v1 | DPPMS 专项异常保留样本（每来源500条） | CONTRACT、SHIPMENT_CONTRACT、PACKAGE、DEVICE_SN、DEVICE_RELATION → ast_device_relationship、ast_device_shipment、com_contract、com_shipment_contract_reference、com_shipment_package | 仅异常保留验证；当前v1，最近回执v0。 | 执行 SUCCESS / 2500 行 / v0 |

## 新库必须单独处理的引用

| 任务 | 当前配置中的旧库引用 | 新库处理边界 |
| --- | --- | --- |
| B02 订单/订单行 | 91,161项`fieldCarryProof`，含targetId、版本和来源/现值证明 | 当前任务用于已有订单的受保护字段承接；新库没有对应绑定，已在预览中阻断。需另行准备首次承接配置，不能伪造旧绑定或删除校验。 |
| B03 项目主档 | 6,570项`migrationRestoration`，引用原目标、撤回绑定、创建/重放回执；另有10项版本保护 | 原库恢复凭据原样保留；新库首次创建范围与原库恢复范围分别确认。当前首先被用户来源绑定缺失阻断。 |
| A12 产品目录 | `migrationDefinitionRevisionId=2108533319597477890` | 在新库建立相同语义的正式扩展定义后，通过新修订ID引用；不沿用不存在的旧ID。 |
| B11 项目相关方/状态 | 55项`_expectedFingerprint`、`_definitionId=2109967949395320833` | 先有项目绑定，再核对新库同定义版本及前值；不把旧库状态证明当作新库事实。 |
| B08 实施范围 | 105项旧`baseline` | 保留原保护证据；新库缺少对应正式范围，不能直接声明这些前值仍有效。 |
| D01～D05 历史专项 | 原任务、回执、正式目标、绑定及前值 | 留在原库追溯；不复制旧运行、旧绑定或旧证据来通过新库校验。 |

可按已批准语义保留的业务映射包括：公司/角色/状态枚举、合同关系18项来源裁决、回款计划540个未关联审计编码，以及原SQL和命名参数。它们与旧目标ID、旧回执和前值保护证明用途不同；逐项核对后处理，不批量清空所有LOOKUP。

## 建议的迁移次序

1. 明确DPPMS、CRM两个业务来源连接，以及产品/项目状态扩展定义的新库引用；全部任务保持停用。
2. 按依赖准备A组：公司与部门、限定补充部门、来源用户，再到角色、用户角色与组织范围；字典和CRM客户/正式产品在各自依赖就绪后推进。每层先预览，实际写入范围单独确认。
3. 准备B02、B03的新库首次承接配置，并明确正式合同主档的独立步骤。当前最新合同主档规则在C01组合任务的CONTRACT来源内；C03是旧入口，不能直接替代。发货暂缓的决定继续有效。
4. 规范项目主档就绪后建立全部原项目身份；项目身份与正式合同就绪后再处理项目合同关系。订单行、办事处、有效合同关系齐备后再复验实施范围。其他产品、回款、状态和续保任务按各自依赖推进。
5. D组保留历史追溯，E组保留验收证据；不把它们混入新库初始化执行队列。

上述顺序不会自动批准新建项目、业务明细或关联关系。此前“73,580个项目暂不新增”和“发货暂不执行”继续有效。

## 本轮已发生的预览与清单边界

用户要求先整理清单后，后续预览队列已停止继续发起。新库此前只复制了项目主档、原项目身份、项目相关方/状态、订单/订单行、项目合同关系5项任务；SQL和映射逐项比对一致，仅连接ID替换为新库连接。已启动运行保留，未执行正式业务迁入。

已得到的结果：项目主档因用户来源绑定缺失停止；订单因旧字段承接绑定不存在停止；原项目身份完整读取80,320行、项目相关方/状态完整读取268,878行，均报告正式项目/绑定缺失。已启动的合同关系预览也已完整结束：83,472行，其中83,454项ISSUE、18项已批准跳过。目前没有运行中预览。新库133张项目/商务/设备业务表的行数与运行前基础种子一致，来源绑定仍为0。这说明新库需要先准备基础资料和首次承接配置，不能把前置阻断解释成旧问题已经消失。

另外只读复核了12项业务主线的当前来源规模（详见逐项明细/JSON），没有写来源库；项目主档80,170行的完整来源与上一轮相同，订单410条同业务键冲突仍存在。来源统计不是Owner完整预览通过证明。

这份清单覆盖原库实际保存的45个int_sync_task。清单中没有独立的安装、软件、许可、项目成员、终端客户联系人整表任务；不能据已有采集材料或专项迁入回执推断这些全量任务已经存在。

## 逐项配置与回执明细

### A01．公司、部门基础

- 原任务：`2101562667583217666`；当前v16；已验证配置版本：16；调度：停用。
- 适配器：`EHR_ORGANIZATION`；模式：`SNAPSHOT`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`COMPANY`：读取方式`TABLE`；来源表：`ehr_company`；映射3项。
  特定映射：`status`（ENUM，5个值）。
- 来源对象`DEPARTMENT`：读取方式`TABLE`；来源表：`ehr_department`；映射6项。
  表级筛选：`[{"value":"2","column":"depGrade","function":null,"operator":"<="},{"value":"6","column":"depCode","function":"LENGTH","operator":"="}]`。
  特定映射：`status`（ENUM，5个值）。
- 最近回执：`2108203460623028226`，v16，执行，SUCCESS，读取171行；汇总`{"UNCHANGED":171}`。
- 最近正式成功回执：`2108203460623028226`，v16，执行，SUCCESS，读取171行；汇总`{"UNCHANGED":171}`。
- 迁移建议：保留现有公司映射及部门筛选；部门仅 depGrade<=2 且编码长度=6，并非全组织树。

### A02．补充部门

- 原任务：`2108538095265562625`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_EHR_DEPARTMENT_COMPLETION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`ehr_department`：读取方式`SQL`；来源表：`ehr_department`、`ehr_login`、`fnd_department`、`fnd_user_info`、`t_user`；映射40项。
  命名参数：`{"departmentCode0":"167010","departmentCode1":"167020","departmentCode2":"167050","departmentCode3":"162007003","departmentCode4":"162012003","departmentCode5":"162022003","departmentCode6":"310409001","departmentCode7":"310422001"}`。
- 最近回执：`2108553120462053377`，v0，预览，PREVIEW_READY，读取8行；汇总`{"UNCHANGED":8}`。
- 最近正式成功回执：`2108549492405899265`，v0，执行，SUCCESS，读取8行；汇总`{"UPDATED":8}`。
- 迁移建议：在公司/上级部门就绪后处理；当前只限定8个已批准部门编码。

### A03．补充部门

- 原任务：`2108524501517955073`；当前v1；已验证配置版本：1；调度：停用。
- 适配器：`DPPMS_DEPARTMENTS`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fnd_department`：读取方式`SQL`；来源表：`ehr_department`、`fnd_department`；映射11项。
  命名参数：`{"departmentCode0":"163130"}`。
- 最近回执：`2109732078985281537`，v1，执行，SUCCESS，读取1行；汇总`{"UNCHANGED":1}`。
- 最近正式成功回执：`2109732078985281537`，v1，执行，SUCCESS，读取1行；汇总`{"UNCHANGED":1}`。
- 迁移建议：当前仅 departmentCode0=163130；不能据任务名称视为部门整表任务。

### A04．来源用户

- 原任务：`2108524822998773762`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_USERS`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`t_user`：读取方式`SQL`；来源表：`ehr_employee`、`ehr_login`、`t_user`；映射22项。
- 来源对象`t_user_info`：读取方式`SQL`；来源表：`ehr_department`、`ehr_employee`、`ehr_job`、`t_company`、`t_user`、`t_user_info`；映射30项。
- 来源对象`fnd_user_info`：读取方式`SQL`；来源表：`ehr_employee`、`ehr_login`、`fnd_user_info`；映射24项。
- 最近回执：`2108556946065764353`，v0，预览，PREVIEW_READY，读取1566行；汇总`{"SKIPPED":39,"UNCHANGED":1525,"REFERENCED":2}`。
- 最近正式成功回执：`2108554375892738049`，v0，执行，SUCCESS，读取1566行；汇总`{"SKIPPED":39,"UPDATED":676,"UNCHANGED":849,"REFERENCED":2}`。
- 迁移建议：先核对公司/部门；在新库重新形成来源用户绑定，保留原审计和同名账号规则。

### A05．角色

- 原任务：`2108554583850524673`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_ROLES`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`T_ROLE`：读取方式`SQL`；来源表：`t_role`、`t_user`；映射12项。
  特定映射：`status`（ENUM，1个值）。
- 来源对象`FND_ROLE`：读取方式`SQL`；来源表：`fnd_roles`、`fnd_user_info`；映射12项。
  特定映射：`sourceCode`（ENUM，18个值）；`status`（ENUM，1个值）。
- 最近回执：`2108559110330822657`，v0，执行，SUCCESS，读取31行；汇总`{"ADOPTED":6,"CREATED":25}`。
- 最近正式成功回执：`2108559110330822657`，v0，执行，SUCCESS，读取31行；汇总`{"ADOPTED":6,"CREATED":25}`。
- 迁移建议：依赖人员审计解析；保留18项来源角色编码映射，不推导新增权限。

### A06．用户角色关系

- 原任务：`2108568095503093761`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_USER_ROLES`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`T_USER_ROLE`：读取方式`SQL`；来源表：`t_user`、`t_user_role`；映射5项。
- 来源对象`FND_USER_ROLE`：读取方式`SQL`；来源表：`fnd_user_info`；映射17项。
- 最近回执：`2108569187448197122`，v0，预览，PREVIEW_READY，读取1709行；汇总`{"SKIPPED":51,"UNCHANGED":1932}`。
- 最近正式成功回执：`2108569008816984065`，v0，执行，SUCCESS，读取1709行；汇总`{"CREATED":1932,"SKIPPED":51}`。
- 迁移建议：先有正式用户、角色及相应来源绑定；历史关系不直接生成额外权限。

### A07．用户公司部门范围

- 原任务：`2108569376783273985`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_USER_SCOPES`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`t_user`：读取方式`SQL`；来源表：`ehr_employee`、`ehr_login`、`t_user`；映射22项。
- 来源对象`t_user_info`：读取方式`SQL`；来源表：`ehr_department`、`ehr_employee`、`ehr_job`、`t_company`、`t_user`、`t_user_info`；映射30项。
- 来源对象`fnd_user_info`：读取方式`SQL`；来源表：`ehr_employee`、`ehr_login`、`fnd_user_info`；映射24项。
- 来源对象`t_user_role`：读取方式`SQL`；来源表：`t_company`、`t_user`、`t_user_role`；映射6项。
- 来源对象`fnd_user_power`：读取方式`SQL`；来源表：`fnd_user_power`；映射10项。
- 最近回执：`2108571647373885441`，v0，预览，PREVIEW_READY，读取3275行；汇总`{"SKIPPED":1329,"UNCHANGED":9492}`。
- 最近正式成功回执：`2108571476367917057`，v0，执行，SUCCESS，读取3275行；汇总`{"CREATED":9492,"SKIPPED":1329}`。
- 迁移建议：先有公司、部门、用户及所需角色；保留已批准的数据范围规则。

### A08．字典类型

- 原任务：`2109351154645135362`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_DICTIONARY_TYPES`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fnd_basic_data_type`：读取方式`SQL`；来源表：`fnd_basic_data`、`fnd_basic_data_type`；映射11项。
- 最近回执：`2109352251665297409`，v0，执行，SUCCESS，读取80行；汇总`{"UNCHANGED":80}`。
- 最近正式成功回执：`2109352251665297409`，v0，执行，SUCCESS，读取80行；汇总`{"UNCHANGED":80}`。
- 迁移建议：保留原类型及补充49组的既定规则；先核对新库已有字典。

### A09．字典项

- 原任务：`2109371486114320385`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_DICTIONARY_DATA`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fnd_basic_data`：读取方式`SQL`；来源表：`fnd_basic_data`；映射12项。
- 最近回执：`2109371924867878913`，v0，执行，SUCCESS，读取532行；汇总`{"SKIPPED":1,"UNCHANGED":531}`。
- 最近正式成功回执：`2109371924867878913`，v0，执行，SUCCESS，读取532行；汇总`{"SKIPPED":1,"UNCHANGED":531}`。
- 迁移建议：依赖字典类型；保留531条承接及1条跳过规则。

### A10．CRM客户

- 原任务：`2102221608959250434`；当前v2；已验证配置版本：2；调度：停用。
- 适配器：`CRM_CUSTOMER`；模式：`INCREMENTAL`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2102221331933859841`；源系统：`CRM`。
- 来源对象`CUSTOMER`：读取方式`SQL`；来源表：`Account`；映射19项。
  特定映射：`lifecycleStatus`（ENUM，2个值）。
- 最近回执：`2102226982617640961`，v2，执行，SUCCESS，读取59601行；汇总`{"ISSUE":83,"CREATED":49525,"UNCHANGED":9993}`。
- 最近正式成功回执：`2102226982617640961`，v2，执行，SUCCESS，读取59601行；汇总`{"ISSUE":83,"CREATED":49525,"UNCHANGED":9993}`。
- 迁移建议：需重建CRM SQL Server连接；映射值域继续以CRM权威来源为准。

### A11．CRM正式产品

- 原任务：`2102221608179109889`；当前v1；已验证配置版本：1；调度：停用。
- 适配器：`TABLE_MAPPING:crm-product`；模式：`INCREMENTAL`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2102221331933859841`；源系统：`CRM`。
- 来源对象`PRODUCT`：读取方式`SQL`；来源表：`DPtech_v_product_info_single`；映射12项。
- 最近回执：`2102223719172337665`，v1，执行，SUCCESS，读取8441行；汇总`{"CREATED":8441}`。
- 最近正式成功回执：`2102223719172337665`，v1，执行，SUCCESS，读取8441行；汇总`{"CREATED":8441}`。
- 迁移建议：需重建CRM SQL Server连接；与DPPMS产品名称/型号承接任务配合，避免重复覆盖。

### A12．DPPMS产品名称、型号

- 原任务：`2108534088732168194`；当前v2；已验证配置版本：2；调度：停用。
- 适配器：`DPPMS_PRODUCT_CATALOG`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fb_items`：读取方式`SQL`；来源表：`fb_items`；映射5项。
  命名参数：`{"productCodes":"","migrationDefinitionRevisionId":"2108533319597477890"}`。
- 最近回执：`2110077295412371457`，v2，执行，SUCCESS，读取34976行；汇总`{"UNCHANGED":34976}`。
- 最近正式成功回执：`2110077295412371457`，v2，执行，SUCCESS，读取34976行；汇总`{"UNCHANGED":34976}`。
- 迁移建议：需先准备同定义的实体扩展版本；migrationDefinitionRevisionId=2108533319597477890属于旧库，不能直接沿用。

### B01．执行单

- 原任务：`2101625228492091393`；当前v11；已验证配置版本：11；调度：停用。
- 适配器：`DPPMS_CRM_EXECUTION_ORDER`；模式：`ONCE`；加载：`UPSERT`；自动分页：是；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`EXECUTION_ORDER`：读取方式`SQL`；来源表：`pm_project`、`pm_project_property_from_sms`；映射63项。
  命名参数：`{"projectIds":"","projectTypes":"10","afterSalesType":"10"}`。
- 最近回执：`2110083758469812226`，v11，预览，PREVIEW_READY，读取136行；汇总`{"UNCHANGED":136}`。
- 最近正式成功回执：`2109759451957006338`，v8，执行，SUCCESS，读取520行；汇总`{"UNCHANGED":520}`。
- 自动分页核对：根`2110082903624519682`已合计100136行；51个分页合计100136行。根与子页不重复相加。
- 本轮只读来源规模：EXECUTION_ORDER 100136行。
- 迁移建议：保留用服筛选及公司码规则；开启自动分页，查看根回执合计，不能只看最后136行。

### B02．订单及订单行

- 原任务：`2101587859478949890`；当前v31；已验证配置版本：None；调度：停用。
- 适配器：`DPPMS_ERP_ORDER`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`ORDER`：读取方式`SQL`；来源表：`pm_order_data_from_erp`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射19项。
  命名参数：`{"projectIds":"","projectTypes":"10","reviewedSourceIds":"","unreviewedSourceIds":""}`。
  特定映射：`fieldCarryProof`（LOOKUP，91161个值）。
- 来源对象`LINE`：读取方式`SQL`；来源表：`pm_order_data_from_erp`、`pm_order_line_from_erp`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射17项。
  命名参数：`{"sourceIds":"","projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110095270865473537`，v31，预览，FAILED，读取467235行；汇总`{"ISSUE":419,"CREATED":235295,"UPDATED":11,"CONFLICT":3,"UNCHANGED":231507}`。
  运行错误：完整预览存在目标归属或字段冲突，未写入任何业务记录。
- 最近正式成功回执：`2110030355538575362`，v28，执行，SUCCESS，读取50行；汇总`{"UNCHANGED":50}`。
- 本轮只读来源规模：ORDER 90478行；LINE 376757行。
- 迁移建议：当前含91,161项旧库字段承接证明，须另行准备新库首次承接配置；原映射和冲突保护保留。

### B03．项目主档

- 原任务：`2108608417343934465`；当前v10；已验证配置版本：10；调度：停用。
- 适配器：`DPPMS_PROJECT_MASTER`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_header`：读取方式`SQL`；来源表：`fnd_user_info`、`pm_project`、`t_company`、`t_user`；映射53项。
  命名参数：`{"projectIds":"","projectTypes":"10","excludedFndActorIds":""}`。
  特定映射：`expectedProjectVersion`（LOOKUP，10个值）；`creatorHistoricalCode`（LOOKUP，2个值）；`updaterHistoricalCode`（LOOKUP，2个值）；`migrationRestoration`（LOOKUP，6570个值）。
- 最近回执：`2110085474984529922`，v10，预览，PREVIEW_READY，读取80170行；汇总`{"ISSUE":10,"CREATED":73580,"UNCHANGED":6580}`。
- 最近正式成功回执：`2110078518484000770`，v9，执行，SUCCESS，读取6570行；汇总`{"UNCHANGED":6570}`。
- 本轮只读来源规模：pm_project_header 80170行。
- 迁移建议：先有用户来源绑定及组织资料；当前还含6,570项旧恢复凭据和10项现值版本保护，不能直接用于空库首次创建。

### B04．全部原项目身份

- 原任务：`2109622534918856706`；当前v6；已验证配置版本：6；调度：停用。
- 适配器：`DPPMS_PROJECT_IDENTITY`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_identity`：读取方式`SQL`；来源表：`pm_project`；映射4项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110096852839501826`，v6，预览，PREVIEW_READY，读取80320行；汇总`{"ISSUE":73722,"UNCHANGED":6598}`。
- 最近正式成功回执：`2110084859474612226`，v5，执行，SUCCESS，读取6577行；汇总`{"UNCHANGED":6577}`。
- 本轮只读来源规模：pm_project_identity 80320行。
- 迁移建议：先有规范主档；保留所有原ID到规范主档的映射，不创建第二份同码项目。

### B05．项目合同关系

- 原任务：`2109363204544983041`；当前v12；已验证配置版本：12；调度：停用。
- 适配器：`DPPMS_PROJECT_CONTRACT_RELATION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_contract`：读取方式`SQL`；来源表：`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射63项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
  特定映射：`migrationDecision`（LOOKUP，18个值）。
- 最近回执：`2110223047316934657`，v12，预览，PREVIEW_READY，读取83472行；汇总`{"ISSUE":76952,"SKIPPED":18,"UNCHANGED":6502}`。
- 最近正式成功回执：`2110212969792782338`，v11，执行，SUCCESS，读取6470行；汇总`{"UNCHANGED":6470}`。
- 本轮只读来源规模：pm_project_contract 83472行。
- 迁移建议：依赖项目身份和正式合同主档；当前保留18项已批准来源裁决。发货组合中的合同主档步骤须单独明确。

### B06．合同回款计划

- 原任务：`2108574253093683202`；当前v10；已验证配置版本：10；调度：停用。
- 适配器：`DPPMS_CONTRACT_COLLECTION_PLAN`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_pb_plan_from_sms`：读取方式`SQL`；来源表：`fnd_user_info`、`pm_pb_plan_from_sms`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`、`t_user`；映射28项。
  命名参数：`{"planIds":"","projectIds":"","projectTypes":"10"}`。
  特定映射：`creatorUnlinkedCode`（LOOKUP，540个值）；`updaterUnlinkedCode`（LOOKUP，540个值）。
- 最近回执：`2110077014557581314`，v10，预览，PREVIEW_READY，读取35451行；汇总`{"CREATED":30152,"UNCHANGED":5299}`。
- 最近正式成功回执：`2110030444873056258`，v7，执行，SUCCESS，读取0行；汇总`{}`。
- 本轮只读来源规模：pm_pb_plan_from_sms 35451行。
- 迁移建议：依赖正式合同及人员来源解析；保留540个未知审计编码，不默认补建账号。

### B07．合同回款信息

- 原任务：`2109967339937804289`；当前v4；已验证配置版本：4；调度：停用。
- 适配器：`DPPMS_CONTRACT_RECEIVABLE`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：500。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`sms_ofst_contract_head_sap`：读取方式`SQL`；来源表：`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`、`sms_ofst_contract_head_sap`；映射39项。
  命名参数：`{"projectIds":"","contractNos":"","projectTypes":"10"}`。
- 最近回执：`2110077032022663169`，v4，预览，PREVIEW_READY，读取79321行；汇总`{"CREATED":79296,"UNCHANGED":25}`。
- 最近正式成功回执：`2110025660233351169`，v1，执行，SUCCESS，读取15行；汇总`{"UNCHANGED":15}`。
- 本轮只读来源规模：sms_ofst_contract_head_sap 79321行。
- 迁移建议：依赖正式合同引用及既定回款字段映射；不以HTTP成功视为合同业务完成。

### B08．项目实施范围

- 原任务：`2109829769958453249`；当前v7；已验证配置版本：None；调度：停用。
- 适配器：`DPPMS_PROJECT_PRODUCT_SCOPE`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_product_line`：读取方式`SQL`；来源表：`pm_project`、`pm_project_product_line`；映射14项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
  特定映射：`baseline`（LOOKUP，105个值）。
- 最近回执：`2110224606482337794`，v6，预览，FAILED，读取2行；汇总`{"CONFLICT":2}`。
  运行错误：完整预览存在目标归属或字段冲突，未写入任何业务记录。
- 最近正式成功回执：`2110033455561269249`，v2，执行，SUCCESS，读取77行；汇总`{"UNCHANGED":77}`。
- 本轮只读来源规模：pm_project_product_line 345034行。
- 迁移建议：依赖项目、办事处、有效合同关系和精确订单行；当前105项baseline属于旧库，正数量规则继续有效。

### B09．项目真实产品

- 原任务：`2109865269478027265`；当前v4；已验证配置版本：4；调度：停用。
- 适配器：`DPPMS_PROJECT_REAL_PRODUCT`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_product_line_real`：读取方式`SQL`；来源表：`pm_project`、`pm_project_product_line_real`、`pm_project_real_product_line_from_sms`；映射25项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110084879938621442`，v4，预览，PREVIEW_READY，读取7848行；汇总`{"ISSUE":7310,"CREATED":538}`。
- 最近正式成功回执：`2110030370134753281`，v1，执行，SUCCESS，读取0行；汇总`{}`。
- 本轮只读来源规模：pm_project_product_line_real 7848行。
- 迁移建议：先有项目来源绑定；保持原产品字段及来源修订关系。

### B10．项目租赁、产品配置

- 原任务：`2109865270430134273`；当前v4；已验证配置版本：4；调度：停用。
- 适配器：`DPPMS_PROJECT_ASSOCIATED_PRODUCTS`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_product_lease_line_from_crm`：读取方式`SQL`；来源表：`pm_project`、`pm_project_product_lease_line_from_crm`；映射14项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 来源对象`pm_project_product_config_level_info_from_crm`：读取方式`SQL`；来源表：`pm_project`、`pm_project_product_config_level_info_from_crm`；映射14项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110085069114314754`，v4，预览，PREVIEW_READY，读取1402行；汇总`{"ISSUE":1401,"CREATED":1}`。
- 最近正式成功回执：`2110030443258249218`，v1，执行，SUCCESS，读取0行；汇总`{}`。
- 本轮只读来源规模：pm_project_product_lease_line_from_crm 627行；pm_project_product_config_level_info_from_crm 775行。
- 迁移建议：先有项目来源绑定；两类原表独立保留，原缺失项目继续报告问题。

### B11．项目相关方、过程状态

- 原任务：`2109968219214897153`；当前v5；已验证配置版本：5；调度：停用。
- 适配器：`DPPMS_PROJECT_FACTS`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：500。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_project_related_party`：读取方式`SQL`；来源表：`pm_project`、`pm_project_related_party`；映射14项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
  特定映射：`_expectedFingerprint`（LOOKUP，55个值）。
- 来源对象`pm_project_state`：读取方式`SQL`；来源表：`pm_project`、`pm_project_state`；映射12项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
  特定映射：`_definitionId`（CONSTANT，0个值，常量=2109967949395320833）。
- 最近回执：`2110097792346820610`，v5，预览，PREVIEW_READY，读取268878行；汇总`{"ISSUE":254345,"CREATED":14400,"UNCHANGED":133}`。
- 最近正式成功回执：`2110024518287949825`，v2，执行，SUCCESS，读取68行；汇总`{"UNCHANGED":68}`。
- 本轮只读来源规模：pm_project_related_party 188770行；pm_project_state 80108行。
- 迁移建议：先有项目绑定；55项旧前值证明和扩展定义ID 2109967949395320833需新库适配；不制造当前审批完成事实。

### B12．设备续保记录

- 原任务：`2110010787726274561`；当前v5；已验证配置版本：5；调度：停用。
- 适配器：`DPPMS_WARRANTY_RENEWAL`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fb_service`：读取方式`SQL`；来源表：`fb_service`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射10项。
  命名参数：`{"deviceSns":"","projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110070785730871298`，v5，预览，PREVIEW_READY，读取453454行；汇总`{"CREATED":453231,"UNCHANGED":223}`。
- 最近正式成功回执：`2110024910384070658`，v2，执行，SUCCESS，读取222行；汇总`{"UNCHANGED":222}`。
- 本轮只读来源规模：fb_service 453454行。
- 迁移建议：保持设备标识、合同号及既定续保映射；与主档及关联层分别核定实际写入范围。

### C01．当前合同/发货组合入口

- 原任务：`2107037031219654657`；当前v14；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:delivery-full-sync`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`fnd_company`、`pm_order_data_from_erp`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`、`sms_ofst_contract_head_sap`；映射22项。
  命名参数：`{"projectIds":"","contractNos":"","projectTypes":"10","approvedContractNos":"31020181224A12,31020211228A12","customerCodeOverrides":"{\"31020220620A23\":\"C00013\",\"31120220620A37\":\"C00013\"}"}`。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射21项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fnd_basic_data`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射10项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 来源对象`DEVICE_SN`：读取方式`SQL`；来源表：`fb_shipment_barcode`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射26项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`、`pm_project`、`pm_project_contract`、`pm_project_group`、`pm_project_group_relationship`；映射10项。
  命名参数：`{"projectIds":"","projectTypes":"10"}`。
- 最近回执：`2110101484592644098`，v14，预览，FAILED，读取166370行；汇总`{"ISSUE":814,"UPDATED":13163,"CONFLICT":3562,"UNCHANGED":148831}`。
  运行错误：运行进程中断，已核对业务提交状态；可创建关联重试。
- 最近正式成功回执：`2110025218778660865`，v9，执行，SUCCESS，读取4行；汇总`{"UNCHANGED":4}`。
- 迁移建议：当前v14、5个来源对象；按用户决定暂不执行。后续若仅迁合同主档，应基于此已复核CONTRACT段单独明确范围。

### C02．旧发货合同入口

- 原任务：`2101857698189352962`；当前v5；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:shipment-contract`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射21项。
- 最近正式成功回执：未找到；不能写成已执行完成。
- 迁移建议：与当前组合入口重叠；原库无运行回执，不作为默认迁入入口。

### C03．旧合同主档入口

- 原任务：`2101875784317583361`；当前v4；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:contract-master`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射22项。
- 最近回执：`2104966820106629122`，v3，执行，SUCCESS，读取20000行；汇总`{"ISSUE":442,"UPDATED":19029,"UNCHANGED":529}`。
- 最近正式成功回执：`2104966820106629122`，v3，执行，SUCCESS，读取20000行；汇总`{"ISSUE":442,"UPDATED":19029,"UNCHANGED":529}`。
- 迁移建议：当前v4，最近成功仍为v3；不能替代当前组合入口中的已复核合同规则。

### C04．旧装箱单入口

- 原任务：`2101875784728625154`；当前v1；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:shipment-package`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 最近正式成功回执：未找到；不能写成已执行完成。
- 迁移建议：与当前组合入口重叠；原库无运行回执，保留配置供追溯。

### C05．旧发货条码入口

- 原任务：`2101875785110306818`；当前v7；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:shipment-barcode`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`BARCODE`：读取方式`SQL`；来源表：`fb_contract`、`fb_items`、`fb_shipment`、`fb_shipment_barcode`；映射35项。
- 最近回执：`2104961139412668418`，v6，执行，SUCCESS，读取5行；汇总`{"UNCHANGED":5}`。
- 最近正式成功回执：`2104961139412668418`，v6，执行，SUCCESS，读取5行；汇总`{"UNCHANGED":5}`。
- 迁移建议：当前v7，最近回执v6；包含设备主体写入，与当前发货事件/关系入口区分。

### C06．旧五对象组合入口

- 原任务：`2101891886242299906`；当前v6；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:shipment-complete`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射21项。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射20项。
- 来源对象`SHIPMENT_CONTRACT_LINK`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射3项。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 来源对象`BARCODE`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射33项。
- 最近回执：`2102020635650670594`，v4，执行，SUCCESS，读取100000行；汇总`{"ISSUE":442,"SKIPPED":18049,"UNCHANGED":81509}`。
- 最近正式成功回执：`2102020635650670594`，v4，执行，SUCCESS，读取100000行；汇总`{"ISSUE":442,"SKIPPED":18049,"UNCHANGED":81509}`。
- 迁移建议：当前v6，最近回执v4；与当前组合任务存在重复目标，保留为历史配置。

### C07．旧设备发货、关系入口

- 原任务：`2102825819916980226`；当前v10；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:device-sn`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：5000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`DEVICE_SN`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射26项。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2109856713554993154`，v10，执行，FAILED，读取0行；汇总`{}`。
  运行错误：流式分块存在目标归属或字段冲突，当前分块已回滚。
- 最近正式成功回执：`2104769259756421121`，v10，执行，SUCCESS，读取4259910行；汇总`{"CREATED":4259910}`。
- 迁移建议：当前v10最后执行FAILED、读取0；保留历史成功回执与来源关系，不恢复为新库默认入口。

### C08．旧条码原样承接

- 原任务：`2104524842835423234`；当前v0；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:shipment-barcode-raw`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`SHIPMENT_BARCODE_RAW`：读取方式`SQL`；来源表：`fb_shipment_barcode`；映射21项。
- 来源对象`SHIPMENT_BARCODE_RELATION_RAW`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2104535183266988034`，v0，执行，FAILED，读取0行；汇总`{}`。
  运行错误：目标表未开放同步: imp_shipment_barcode。
- 最近正式成功回执：未找到；不能写成已执行完成。
- 迁移建议：目标为imp_shipment_barcode及imp_shipment_barcode_relation旧表；最后FAILED，不迁为新功能入口。

### D01．557处用户更新人纠正

- 原任务：`2108545067259985922`；当前v1；已验证配置版本：1；调度：停用。
- 适配器：`DPPMS_USER_AUDIT_CORRECTION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fnd_user_info`：读取方式`SQL`；来源表：`fnd_user_info`；映射18项。
  命名参数：`{"approvedId0":10,"approvedId1":100,"approvedId2":101,"approvedId3":102,"approvedId4":103,"approvedId5":105,"approvedId6":106,"approvedId7":108,"approvedId8":109,"approvedId9":11,"approvedId10":110,"approvedId11":111,"approvedId12":112,"approvedId13":113,"approvedId14":114,"approvedId15":115,"approvedId16":116,"approvedId17":117,"approvedId18":118,"approvedId19":12,"approvedId20":120,"approvedId21":121,"approvedId22":122,"approvedId23":123,"approvedId24":124,"approvedId25":125,"approvedId26":126,"approvedId27":128,"approvedId28":129,"approvedId29":13,"approvedId30":130,"approvedId31":131,"approvedId32":133,"approvedId33":135,"approvedId34":136,"approvedId35":137,"approvedId36":138,"approvedId37":139,"approvedId38":140,"approvedId39":141,"approvedId40":142,"approvedId41":143,"approvedId42":144,"approvedId43":145,"approvedId44":146,"approvedId45":147,"approvedId46":149,"approvedId47":15,"approvedId48":150,"approvedId49":151,"approvedId50":152,"approvedId51":153,"approvedId52":154,"approvedId53":155,"approvedId54":156,"approvedId55":157,"approvedId56":158,"approvedId57":159,"approvedId58":16,"approvedId59":160,"approvedId60":161,"approvedId61":162,"approvedId62":164,"approvedId63":165,"approvedId64":166,"approvedId65":167,"approvedId66":168,"approvedId67":169,"approvedId68":17,"approvedId69":170,"approvedId70":172,"approvedId71":173,"approvedId72":174,"approvedId73":175,"approvedId74":176,"approvedId75":177,"approvedId76":178,"approvedId77":18,"approvedId78":180,"approvedId79":181,"approvedId80":182,"approvedId81":183,"approvedId82":184,"approvedId83":185,"approvedId84":186,"approvedId85":188,"approvedId86":189,"approvedId87":19,"approvedId88":190,"approvedId89":192,"approvedId90":193,"approvedId91":194,"approvedId92":195,"approvedId93":196,"approvedId94":197,"approvedId95":198,"approvedId96":2,"approvedId97":201,"approvedId98":204,"approvedId99":207,"approvedId100":209,"approvedId101":21,"approvedId102":210,"approvedId103":211,"approvedId104":212,"approvedId105":213,"approvedId106":214,"approvedId107":216,"approvedId108":218,"approvedId109":219,"approvedId110":22,"approvedId111":220,"approvedId112":221,"approvedId113":222,"approvedId114":224,"approvedId115":225,"approvedId116":226,"approvedId117":23,"approvedId118":230,"approvedId119":231,"approvedId120":232,"approvedId121":233,"approvedId122":234,"approvedId123":236,"approvedId124":237,"approvedId125":238,"approvedId126":239,"approvedId127":240,"approvedId128":241,"approvedId129":242,"approvedId130":243,"approvedId131":244,"approvedId132":245,"approvedId133":246,"approvedId134":247,"approvedId135":248,"approvedId136":249,"approvedId137":250,"approvedId138":251,"approvedId139":252,"approvedId140":253,"approvedId141":254,"approvedId142":255,"approvedId143":256,"approvedId144":257,"approvedId145":26,"approvedId146":260,"approvedId147":264,"approvedId148":265,"approvedId149":266,"approvedId150":267,"approvedId151":269,"approvedId152":27,"approvedId153":270,"approvedId154":271,"approvedId155":272,"approvedId156":274,"approvedId157":276,"approvedId158":277,"approvedId159":278,"approvedId160":279,"approvedId161":28,"approvedId162":280,"approvedId163":282,"approvedId164":283,"approvedId165":284,"approvedId166":289,"approvedId167":29,"approvedId168":290,"approvedId169":291,"approvedId170":292,"approvedId171":293,"approvedId172":294,"approvedId173":295,"approvedId174":296,"approvedId175":298,"approvedId176":299,"approvedId177":3,"approvedId178":30,"approvedId179":300,"approvedId180":302,"approvedId181":303,"approvedId182":304,"approvedId183":305,"approvedId184":306,"approvedId185":308,"approvedId186":31,"approvedId187":310,"approvedId188":311,"approvedId189":313,"approvedId190":314,"approvedId191":315,"approvedId192":316,"approvedId193":32,"approvedId194":320,"approvedId195":321,"approvedId196":322,"approvedId197":323,"approvedId198":324,"approvedId199":326,"approvedId200":328,"approvedId201":329,"approvedId202":330,"approvedId203":332,"approvedId204":333,"approvedId205":334,"approvedId206":336,"approvedId207":337,"approvedId208":338,"approvedId209":339,"approvedId210":34,"approvedId211":340,"approvedId212":341,"approvedId213":342,"approvedId214":343,"approvedId215":344,"approvedId216":345,"approvedId217":346,"approvedId218":347,"approvedId219":348,"approvedId220":349,"approvedId221":35,"approvedId222":350,"approvedId223":351,"approvedId224":354,"approvedId225":355,"approvedId226":356,"approvedId227":357,"approvedId228":358,"approvedId229":359,"approvedId230":36,"approvedId231":360,"approvedId232":361,"approvedId233":363,"approvedId234":364,"approvedId235":365,"approvedId236":366,"approvedId237":367,"approvedId238":369,"approvedId239":37,"approvedId240":370,"approvedId241":371,"approvedId242":372,"approvedId243":373,"approvedId244":375,"approvedId245":377,"approvedId246":378,"approvedId247":38,"approvedId248":380,"approvedId249":382,"approvedId250":385,"approvedId251":386,"approvedId252":388,"approvedId253":389,"approvedId254":39,"approvedId255":391,"approvedId256":392,"approvedId257":393,"approvedId258":396,"approvedId259":397,"approvedId260":398,"approvedId261":399,"approvedId262":4,"approvedId263":40,"approvedId264":400,"approvedId265":401,"approvedId266":402,"approvedId267":409,"approvedId268":410,"approvedId269":411,"approvedId270":418,"approvedId271":42,"approvedId272":420,"approvedId273":421,"approvedId274":422,"approvedId275":423,"approvedId276":424,"approvedId277":426,"approvedId278":429,"approvedId279":43,"approvedId280":430,"approvedId281":431,"approvedId282":432,"approvedId283":433,"approvedId284":434,"approvedId285":436,"approvedId286":437,"approvedId287":439,"approvedId288":44,"approvedId289":441,"approvedId290":443,"approvedId291":444,"approvedId292":445,"approvedId293":45,"approvedId294":451,"approvedId295":453,"approvedId296":454,"approvedId297":457,"approvedId298":46,"approvedId299":462,"approvedId300":465,"approvedId301":466,"approvedId302":467,"approvedId303":468,"approvedId304":469,"approvedId305":47,"approvedId306":470,"approvedId307":472,"approvedId308":477,"approvedId309":478,"approvedId310":48,"approvedId311":480,"approvedId312":481,"approvedId313":482,"approvedId314":483,"approvedId315":484,"approvedId316":488,"approvedId317":489,"approvedId318":49,"approvedId319":491,"approvedId320":494,"approvedId321":496,"approvedId322":5,"approvedId323":50,"approvedId324":504,"approvedId325":51,"approvedId326":514,"approvedId327":518,"approvedId328":52,"approvedId329":524,"approvedId330":525,"approvedId331":527,"approvedId332":529,"approvedId333":53,"approvedId334":530,"approvedId335":539,"approvedId336":54,"approvedId337":547,"approvedId338":548,"approvedId339":55,"approvedId340":551,"approvedId341":552,"approvedId342":558,"approvedId343":56,"approvedId344":561,"approvedId345":562,"approvedId346":564,"approvedId347":565,"approvedId348":566,"approvedId349":567,"approvedId350":57,"approvedId351":572,"approvedId352":575,"approvedId353":577,"approvedId354":578,"approvedId355":579,"approvedId356":58,"approvedId357":581,"approvedId358":586,"approvedId359":587,"approvedId360":588,"approvedId361":59,"approvedId362":592,"approvedId363":593,"approvedId364":595,"approvedId365":60,"approvedId366":602,"approvedId367":608,"approvedId368":61,"approvedId369":610,"approvedId370":619,"approvedId371":62,"approvedId372":626,"approvedId373":63,"approvedId374":630,"approvedId375":632,"approvedId376":633,"approvedId377":637,"approvedId378":639,"approvedId379":64,"approvedId380":643,"approvedId381":644,"approvedId382":648,"approvedId383":65,"approvedId384":654,"approvedId385":659,"approvedId386":66,"approvedId387":662,"approvedId388":663,"approvedId389":664,"approvedId390":665,"approvedId391":669,"approvedId392":67,"approvedId393":671,"approvedId394":674,"approvedId395":676,"approvedId396":677,"approvedId397":678,"approvedId398":679,"approvedId399":68,"approvedId400":681,"approvedId401":684,"approvedId402":686,"approvedId403":69,"approvedId404":70,"approvedId405":71,"approvedId406":719,"approvedId407":72,"approvedId408":722,"approvedId409":728,"approvedId410":729,"approvedId411":73,"approvedId412":731,"approvedId413":732,"approvedId414":733,"approvedId415":734,"approvedId416":735,"approvedId417":736,"approvedId418":737,"approvedId419":739,"approvedId420":74,"approvedId421":740,"approvedId422":741,"approvedId423":742,"approvedId424":743,"approvedId425":744,"approvedId426":746,"approvedId427":747,"approvedId428":749,"approvedId429":750,"approvedId430":751,"approvedId431":757,"approvedId432":758,"approvedId433":759,"approvedId434":76,"approvedId435":760,"approvedId436":762,"approvedId437":763,"approvedId438":764,"approvedId439":765,"approvedId440":766,"approvedId441":769,"approvedId442":77,"approvedId443":770,"approvedId444":771,"approvedId445":773,"approvedId446":774,"approvedId447":775,"approvedId448":777,"approvedId449":778,"approvedId450":779,"approvedId451":780,"approvedId452":781,"approvedId453":782,"approvedId454":783,"approvedId455":784,"approvedId456":786,"approvedId457":787,"approvedId458":788,"approvedId459":790,"approvedId460":796,"approvedId461":797,"approvedId462":798,"approvedId463":799,"approvedId464":8,"approvedId465":80,"approvedId466":800,"approvedId467":801,"approvedId468":802,"approvedId469":803,"approvedId470":805,"approvedId471":806,"approvedId472":807,"approvedId473":808,"approvedId474":809,"approvedId475":810,"approvedId476":811,"approvedId477":812,"approvedId478":813,"approvedId479":814,"approvedId480":816,"approvedId481":817,"approvedId482":818,"approvedId483":819,"approvedId484":82,"approvedId485":820,"approvedId486":821,"approvedId487":822,"approvedId488":823,"approvedId489":824,"approvedId490":825,"approvedId491":826,"approvedId492":827,"approvedId493":83,"approvedId494":830,"approvedId495":831,"approvedId496":832,"approvedId497":833,"approvedId498":834,"approvedId499":835,"approvedId500":836,"approvedId501":837,"approvedId502":838,"approvedId503":839,"approvedId504":84,"approvedId505":840,"approvedId506":841,"approvedId507":842,"approvedId508":846,"approvedId509":848,"approvedId510":849,"approvedId511":85,"approvedId512":850,"approvedId513":851,"approvedId514":852,"approvedId515":853,"approvedId516":854,"approvedId517":855,"approvedId518":856,"approvedId519":857,"approvedId520":858,"approvedId521":859,"approvedId522":86,"approvedId523":861,"approvedId524":862,"approvedId525":863,"approvedId526":864,"approvedId527":866,"approvedId528":867,"approvedId529":868,"approvedId530":869,"approvedId531":87,"approvedId532":871,"approvedId533":872,"approvedId534":873,"approvedId535":874,"approvedId536":875,"approvedId537":876,"approvedId538":877,"approvedId539":878,"approvedId540":879,"approvedId541":88,"approvedId542":880,"approvedId543":881,"approvedId544":883,"approvedId545":884,"approvedId546":89,"approvedId547":9,"approvedId548":90,"approvedId549":92,"approvedId550":93,"approvedId551":94,"approvedId552":95,"approvedId553":96,"approvedId554":97,"approvedId555":98,"approvedId556":99}`。
  特定映射：`migrationAuditCorrection`（ENUM，557个值）。
- 最近回执：`2108549489075621889`，v1，预览，PREVIEW_READY，读取557行；汇总`{"UNCHANGED":557}`。
- 最近正式成功回执：`2108549172460195841`，v1，执行，SUCCESS，读取557行；汇总`{"UPDATED":557}`。
- 迁移建议：限定原成功迁入用户和完整保护证据；旧目标ID、绑定、回执不迁为新库执行凭据。

### D02．13个角色25处审计纠正

- 原任务：`2108567804242235394`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_ROLE_AUDIT_CORRECTION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fnd_roles`：读取方式`SQL`；来源表：`fnd_roles`；映射12项。
  命名参数：`{"roleId0":14,"roleId1":9,"roleId2":10,"roleId3":13,"roleId4":16,"roleId5":17,"roleId6":18,"roleId7":19,"roleId8":20,"roleId9":21,"roleId10":6,"roleId11":22,"roleId12":1}`。
  特定映射：`migrationAuditCorrection`（ENUM，13个值）。
- 最近回执：`2108568455621840898`，v0，预览，PREVIEW_READY，读取13行；汇总`{"UNCHANGED":13}`。
- 最近正式成功回执：`2108568396289216514`，v0，执行，SUCCESS，读取13行；汇总`{"UPDATED":13}`。
- 迁移建议：原任务名仍写“待裁决”，但决策文档和成功回执确认已批准执行；仅旧批次13角色，不是新库初始化步骤。

### D03．2条发货合同空保修年数纠正

- 原任务：`2108799116748152833`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_SHIPMENT_CONTRACT_WARRANTY_CORRECTION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`；映射13项。
  命名参数：`{"contract1":"11824","contract2":"13560"}`。
  特定映射：`correctionEvidence`（CONSTANT，0个值，常量=[旧库证明内容保留在原任务定义中，本清单不复制完整前值]）。
- 最近回执：`2108801809084485634`，v0，执行，SUCCESS，读取2行；汇总`{"UNCHANGED":2}`。
- 最近正式成功回执：`2108801809084485634`，v0，执行，SUCCESS，读取2行；汇总`{"UNCHANGED":2}`。
- 迁移建议：限定原合同ID 11824、13560及旧正式目标/前值；不将纠正证明复制为新库业务数据。

### D04．设备关系审计纠正

- 原任务：`2109382583085117441`；当前v3；已验证配置版本：None；调度：停用。
- 适配器：`DPPMS_DEVICE_RELATIONSHIP_AUDIT_CORRECTION`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：500。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`fb_shipment_barcode_relation`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
  特定映射：`auditCorrectionProof`（LOOKUP，65406个值）；`originalRunProof`（CONSTANT，0个值，常量=[旧库证明内容保留在原任务定义中，本清单不复制完整前值]）。
- 最近回执：`2109399267682521089`，v2，执行，SUCCESS，读取65406行；汇总`{"SKIPPED":360,"UNCHANGED":65046}`。
- 最近正式成功回执：`2109399267682521089`，v2，执行，SUCCESS，读取65406行；汇总`{"SKIPPED":360,"UNCHANGED":65046}`。
- 迁移建议：65,406项逐键凭据含65,046项纠正、360项排除；依赖旧绑定及旧成功运行，不能平移执行。

### D05．旧01订单行绑定清理

- 原任务：`2109968221270106114`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`DPPMS_OBSOLETE_ORDER_LINE_BINDING`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：500。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`pm_order_line_from_erp`：读取方式`SQL`；来源表：`pm_order_line_from_erp`；映射3项。
  特定映射：`_originalTaskId`（CONSTANT，0个值，常量=2101587859478949890）。
- 最近回执：`2109969877659475970`，v0，执行，SUCCESS，读取361032行；汇总`{"DELETED":236259,"SKIPPED":124773}`。
- 最近正式成功回执：`2109969877659475970`，v0，执行，SUCCESS，读取361032行；汇总`{"DELETED":236259,"SKIPPED":124773}`。
- 迁移建议：指向原订单任务2101587859478949890；原执行删除236,259旧绑定。新库无这些旧绑定，不重放清理。

### E01．项目详情回款验收隔离样本

- 原任务：`2101613039156846593`；当前v0；已验证配置版本：0；调度：停用。
- 适配器：`PAYMENT_PLAN_ACCEPTANCE`；模式：`SNAPSHOT`；加载：`UPSERT`；自动分页：否；配置maxRows：10000。
- 来源连接：`2101613038573838337`；源系统：`DETAIL_ACCEPTANCE_20260920`。
- 来源对象`ACCEPTANCE`：读取方式`SQL`；来源表：`codex_detail_event_source`、`codex_detail_payment_source`；映射5项。
  特定映射：`nodeType`（LOOKUP，4个值）；`nodeCode`（LOOKUP，4个值）。
- 最近回执：`2102958773804687362`，v0，执行，SUCCESS，读取11行；汇总`{"ISSUE":5,"UPDATED":1,"UNCHANGED":5}`。
- 最近正式成功回执：`2102958773804687362`，v0，执行，SUCCESS，读取11行；汇总`{"ISSUE":5,"UPDATED":1,"UNCHANGED":5}`。
- 迁移建议：连接直接指向原npdms_domain_test的codex_detail_*测试源；不复制到新库任务链。

### E02．组合预览样本，每来源500条

- 原任务：`2107037031798468609`；当前v1；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:delivery-sync-preview-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射22项。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射21项。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 来源对象`BARCODE`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射44项。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2107037032293396481`，v0，预览，FAILED，读取500行；汇总`{"ISSUE":335,"UNCHANGED":165}`。
  运行错误：来源主键缺失或无效: SHIPMENT_CONTRACT。
- 最近正式成功回执：未找到；不能写成已执行完成。
- 迁移建议：样本任务，且当前v1无同版本运行；不能替代当前全量组合任务。

### E03．有效主键样本，每来源500条

- 原任务：`2107040712333811713`；当前v1；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:delivery-valid-sample-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射22项。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射21项。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 来源对象`DEVICE_SN`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射26项。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2107041136130482178`，v0，执行，SUCCESS，读取2500行；汇总`{"ISSUE":335,"UNCHANGED":2165}`。
- 最近正式成功回执：`2107041136130482178`，v0，执行，SUCCESS，读取2500行；汇总`{"ISSUE":335,"UNCHANGED":2165}`。
- 迁移建议：仅专项样本；当前v1，最近回执v0。

### E04．并发样本：合同2000条

- 原任务：`2107041701187117058`；当前v0；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:parallel-contract-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射22项。
- 最近回执：`2107041702294413316`，v0，执行，SUCCESS，读取2000行；汇总`{"ISSUE":357,"UNCHANGED":1643}`。
- 最近正式成功回执：`2107041702294413316`，v0，执行，SUCCESS，读取2000行；汇总`{"ISSUE":357,"UNCHANGED":1643}`。
- 迁移建议：仅原并发验证证据，不作为常规迁移步骤。

### E05．并发样本：装箱单2000条

- 原任务：`2107041701468135425`；当前v0；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:parallel-package-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 最近回执：`2107041702294413315`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 最近正式成功回执：`2107041702294413315`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 迁移建议：仅原并发验证证据，不作为常规迁移步骤。

### E06．并发样本：设备发货2000条

- 原任务：`2107041701736570882`；当前v0；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:parallel-device_sn-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`DEVICE_SN`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射26项。
- 最近回执：`2107041702294413317`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 最近正式成功回执：`2107041702294413317`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 迁移建议：仅原并发验证证据，不作为常规迁移步骤。

### E07．并发样本：设备关系2000条

- 原任务：`2107041701984034818`；当前v0；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:parallel-device_relation-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2107041702294413314`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 最近正式成功回执：`2107041702294413314`，v0，执行，SUCCESS，读取2000行；汇总`{"UNCHANGED":2000}`。
- 迁移建议：仅原并发验证证据，不作为常规迁移步骤。

### E08．异常保留样本，每来源500条

- 原任务：`2107046961754501121`；当前v1；已验证配置版本：None；调度：停用。
- 适配器：`TABLE_MAPPING:delivery-checked-sample-20261005`；模式：`ONCE`；加载：`UPSERT`；自动分页：否；配置maxRows：2000。
- 来源连接：`2101561624635936769`；源系统：`DPPMS`。
- 来源对象`CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`、`sms_ofst_contract_head_sap`；映射22项。
- 来源对象`SHIPMENT_CONTRACT`：读取方式`SQL`；来源表：`fb_contract`、`pm_order_data_from_erp`；映射21项。
- 来源对象`PACKAGE`：读取方式`SQL`；来源表：`fb_shipment`；映射9项。
- 来源对象`DEVICE_SN`：读取方式`SQL`；来源表：`fb_contract`、`fb_shipment`、`fb_shipment_barcode`；映射26项。
- 来源对象`DEVICE_RELATION`：读取方式`SQL`；来源表：`fb_shipment_barcode_relation`；映射10项。
- 最近回执：`2107047315678261250`，v0，执行，SUCCESS，读取2500行；汇总`{"ISSUE":336,"UNCHANGED":2164}`。
- 最近正式成功回执：`2107047315678261250`，v0，执行，SUCCESS，读取2500行；汇总`{"ISSUE":336,"UNCHANGED":2164}`。
- 迁移建议：仅异常保留验证；当前v1，最近回执v0。

## 证据与生成入口

- [结构化清单](2026-10-14-dppms-original-task-migration-inventory.json)：45项完整索引、来源表/参数、主目标、映射计数和新旧回执版本。
- [上一轮全量预览与阻断修复](2026-10-14-dppms-full-preview-blocker-repair.md)：用于区分已批准恢复、待新增范围和暂缓发货。
- [角色空审计纠正的实际批准及回执](2026-10-09-dppms-role-audit-correction-decision.md)：任务名“待裁决”已过时，不能据名称否认已批准事实。
- 私有原始证据及生成脚本：`D:/CodexTaskArtifacts/project-detail-followup/preview-recheck-20261014/`。`inventory-original-tasks.py`只读抓取，`render-task-inventory.py`生成本报告；原始任务定义、连接凭据与完整旧前值没有复制进本报告。
- 本次未提交Git，未变更PRD/SDS语义，未修改原库任务或运行状态。
