# 正式产品目录整表承接裁决

2026-10-14后续裁决：用户明确同编码名称不同以CRM为准，不再用扩展承接DPPMS名称/型号。生产任务P12只关联或补齐正式产品，取消产品扩展定义及读写依赖；缺失产品创建规则保持。详见[现行裁决DPPMS-PRODUCT-028](../decisions/open-questions.md#dppms-product-028-正式产品目录实体扩展裁决)。本文件下文保留原方案及当时执行证据，不能再作为生产环境创建产品目录扩展的依据；原库已有扩展及历史未清理，现有实现的调整和验证仍待完成。

实际执行更新：INT任务2108534088732168194、运行2108538310282362881成功承接104个编码（INSERT=5、UPDATE=99）。逐原字段和正式字段核对通过：5个缺失产品可用；99个既有目录全部21列保持原值；104套DPPMS原名称/型号由正式产品Owner及既有实体扩展承接。再次预览2108539352675958786为UNCHANGED=104，没有重复新增。迁移任务页面已核实最近成功运行，业务属性页面查询验收仍待完成；这不代表其他发货/项目关联表已完成纠正。

已核对现有ast_product_official_info全部21列及原fb_items全部4列。本批104个产品编码、99个已有目录、5个缺失；99个已有目录型号均相同，30个名称不同。30个差异行的product_desc全部已有不同内容，不能利用覆盖描述来同时保存两套名称。

此前已批准：按租户+产品编码唯一关联、补齐缺失5个并设可用、已有产品保持CRM名称并完整保留DPPMS来源名称。目录目前没有已实现的正式修订表；先前代码中的ast_product_source_record未建表且结构未经整表批准，不能把它当作已存在能力或先行创建。

用户已裁决“复用正式实体扩展承接”。不新增表、列，复用下列21个目录字段，并将DPPMS名称/型号作为正式产品的业务扩展属性dppms_catalog_labels保存到既有plt_entity_extension_value。对象列表明确包含productCode/productName/productModel，通过正式产品ID关联、按租户隔离；产品Owner实现查询/写入并调用现有EntityExtensionApi，不写中间迁移快照，不冒充发货时标签，不改technical_spec。现有扩展定义、值表全部字段在下方列明。

当前为已批准、正在实施；尚未向真实目标库写入目录或扩展属性，不能视为迁移完成。此前拟建的ast_product_source_record路径撤回，以既有实体扩展取代。

| 既有产品列 | 来源及处理 |
|---|---|
| id | 现有目标产品技术ID，不导入fb_items.id作为业务键 |
| product_code | fb_items.item；租户+编码唯一，沿已批准同步转换 |
| product_model | fb_items.itemname；99个已匹配目录型号全部相同；新5个按来源填写 |
| product_name | 新5个按fb_items.describe_；已有99个沿已批准当前CRM名称，其中30个来源不同，需下述既有实体扩展属性保留 |
| product_desc | 已有99个保持CRM描述；30个名称差异的描述也有内容且全部不同，不覆盖；新5个按describe_填写 |
| technical_spec | 保留已有技术规格，不写名称、来源快照或任意JSON |
| document_id | 保留现有文档关系，fb_items无对应字段 |
| source_system | 已有CRM保持；新5个来源DPPMS，现有同步身份 |
| source_key | 保持既有来源绑定，新DPPMS按产品编码，不采用无业务意义ID |
| source_version | 现有同步版本，不伪造原目录修订日期 |
| source_updated_at | fb_items无更新时间，不能生成来源更新日期 |
| synced_at | 现有本平台同步时钟，区分原业务时间 |
| sync_status | 现有同步结果，承接缺口不能假报完成 |
| status | 新5个ACTIVE（已批准）；已有产品保持当前启停，不据发货推断状态 |
| version | 现有乐观锁，不导入原自增ID |
| creator | 现有Owner写入审计，fb_items无创建人 |
| create_time | 现有Owner写入审计，fb_items无创建时间 |
| updater | 现有Owner写入审计，fb_items无更新人 |
| update_time | 现有Owner写入审计，fb_items无更新时间 |
| deleted | 保持现有逻辑删除，已删除目录不自动复活 |
| tenant_id | 当前迁移租户，跨租户不能关联 |

## 原fb_items全部4列

| 原字段 | 承接/排除原因 |
|---|---|
| id | 用户说明技术自增ID无业务意义，不作为业务属性/键迁入，列明排除 |
| item | 正式product_code |
| itemname | 正式product_model及目录来源标签业务属性 |
| describe_ | 新增产品正式product_name/product_desc；已有产品来源名称承接到已批准的正式实体扩展属性 |

## 既有plt_entity_extension_definition（12列，无结构变化）

| 既有列 | 原类型 | 用途 |
|---|---|---|
| id | bigint | 既有目标技术ID |
| tenant_id | bigint | 租户隔离 |
| owner_module | varchar(64) | AST正式Owner |
| entity_type | varchar(64) | 正式ProductOfficial |
| revision_no | int | 既有定义修订号 |
| source_form_revision_id | bigint | 沿既有正式实体扩展协议，不导入来源技术ID |
| fields_json | json | 明确发布dppms_catalog_labels业务属性定义 |
| creator | varchar(64) | 既有Owner审计 |
| create_time | datetime(3) | 既有Owner审计 |
| updater | varchar(64) | 既有Owner审计 |
| update_time | datetime(3) | 既有Owner审计 |
| deleted | bit(1) | 既有逻辑删除 |

## 既有plt_entity_extension_value（14列，无结构变化）

| 既有列 | 原类型 | 用途 |
|---|---|---|
| id | bigint | 既有目标技术ID |
| tenant_id | bigint | 租户隔离 |
| owner_module | varchar(64) | AST正式Owner |
| entity_type | varchar(64) | 正式ProductOfficial |
| entity_id | bigint | 正式产品ID，非来源自增ID |
| revision_id | bigint | 当前实体扩展按既有协议；不伪造产品修订历史 |
| definition_revision_id | bigint | 关联已发布不可变属性定义 |
| values_json | json | 明确业务名称/型号对象列表；保留原文、NULL及已存在其他业务属性 |
| version | int | 既有乐观锁 |
| creator | varchar(64) | 既有Owner审计 |
| create_time | datetime(3) | 既有Owner审计 |
| updater | varchar(64) | 既有Owner审计 |
| update_time | datetime(3) | 既有Owner审计 |
| deleted | bit(1) | 既有逻辑删除 |

验证边界：原表及正式目录只读核对完成；没有创建表/列、没有写入产品或扩展值。

## 已批准方案实施进展

已撤回9个拟建ast_product_source_record模型/写入/查询文件，私有任务证据保留；没有建该表。AST已实现正式产品Owner和实体属性SPI，PLT管理既有扩展表；INT提供DPPMS_PRODUCT_CATALOG的参数化四原列提取、流式分块同步、运行与来源绑定，采用真实运行发起人。既有CRM字段不覆盖，新产品按批准规则设可用；同一正式产品上的旧标签保留并追加，新编码冲突名称、来源绑定漂移、已删除/跨租户目录均阻断。发货响应从正式目录和实体扩展返回当前目录与DPPMS名称/型号；没有增加发货物理列。23项不同后端针对性检查通过，已改前端文件ESLint/格式检查通过；整项目类型检查仍在复核。真实属性定义发布、目录同步落库、迁移状态纠正、部署及浏览器验收尚未完成。
