# proj_project_product 整表裁决

状态：独立实体提案撤回，实施范围整表结构待裁决。用户已确认“合并到实施范围，提交整表方案”。最新完整方案见[实施范围整表裁决](2026-10-09-dppms-delivery-scope-table-decision.md)。此表实际已有21列、105条，正式范围承接核验前不清理；下文10列及此前19列均为已暂停的旧草案，不能据此实施。

用户最新回复要求先探查旧系统作用和数据来源。以下10列为暂停实施的原草案，不代表最终结论或已批准结构。补充核查见[旧业务作用与来源核查](2026-10-09-dppms-project-product-purpose-audit.md)：原projectQuantity具有拆分数量覆盖语义，NULL时旧查询回退订单数量；刷新会重排原ID；拆分子项目写入路径不保存订单号和行号。非空order_line_id和稳定来源ID假设尚未覆盖全部旧业务，结构继续待裁决。

2026-10-09补充：19列候选方案也没有批准。用户指出其含义接近实施范围，要求结合默认订单全量、拆分及合并数量理解。现进一步核对正式COM范围Owner、42列范围表及21列明细表；本批10个项目的现成实施范围和明细均为0。现成分配会占用订单行数量、维护分配版本并参与验收绑定，不能凭旧清单直接调用当前分配动作。先评估同义业务字段及当前分配与历史清单之间的边界，再提交整表裁决；两版候选均暂停，没有新增或删除本表字段。

## 复用依据与业务主体

- 本表拟仅承担“项目与正式订单行的产品关联”，保留独立项目数量，不复制已有正式订单头、订单行和产品目录的业务字段。
- 原pm_project_product_line实际11列、353,030条。projectQuantity全表NULL；原项目、订单和行号本次统计均未出现NULL/空订单行号。
- 本批105条按当前项目公司、原订单号、原行号、产品编码核对，均唯一命中正式com_sales_order_line。合同号、名称、订单数量、已交数量、未交数量逐字段全部等值。正式关联落库前仍须以已批准来源绑定与公司转换再次核实，不能依赖当前项目公司猜配。
- 现成com_delivery_scope是实施分配范围，要求实施数量、分配状态、办事处等事实；原表没有这些事实，不能把NULL项目数量编为分配数量，也不能创建ACTIVE分配。因此不以该表承接原项目产品关系。
- 原候选组合（项目、合同、产品、订单、行）有53组、417条重复，保留每条原记录和正式ID；不按此组合建立唯一约束或丢弃重复行。

## 建议最终10列

| 列 | 类型 | 可空 | 处理 |
|---|---|---|---|
| `id` | `BIGINT` | 否 | 已有105条正式ID保留 |
| `tenant_id` | `BIGINT` | 否 | 当前迁移租户，所有引用和查询隔离 |
| `project_id` | `BIGINT` | 否 | 正式项目引用；未核实项目先待处理 |
| `order_line_id` | `BIGINT` | 否 | 本表唯一新增物理列；引用已核实正式销售订单行，不借用实施分配范围 |
| `project_quantity` | `DECIMAL(24,6)` | 是 | 保留当前定义及原NULL，不推导实施数量 |
| `creator` | `VARCHAR(64)` | 否 | 保留既有迁移操作者；原表没有审计人，新增行用实际迁移人 |
| `create_time` | `DATETIME(3)` | 否 | 已有105条历史迁移时间不改；新行记录实际迁移时间 |
| `updater` | `VARCHAR(64)` | 否 | 已有值保留，来源无对应审计人，不编造 |
| `update_time` | `DATETIME(3)` | 否 | 本次补引用和去重复列保护已有迁移时间；纠正过程另记正式审计 |
| `deleted` | `BIT(1)` | 否 | 沿用逻辑删除，不复活已删除资料 |

## 原表全部11列映射

| 原列 | 原类型 / 可空 | 正式承接点 | 处理 |
|---|---|---|---|
| `id` | int(11) / NO | `既有INT来源绑定` | 原自增ID用于每行来源身份；不充当产品编码，不在业务表追加技术镜像 |
| `projectId` | int(11) / YES | `proj_project_product.project_id` | 通过正式项目迁移绑定核实父项目，不猜项目 |
| `contractNo` | varchar(45) / YES | `com_sales_order.contract_no` | 经正式订单行的order_id关联订单头；本批105条与原合同号等值 |
| `itemCode` | varchar(15) / YES | `com_sales_order_line.product_code` | 以正式订单行承接；目录按租户＋编码复用已批准产品体系 |
| `itemName` | varchar(255) / YES | `com_sales_order_line.product_desc` | 本批105条原项目产品名称与正式订单行描述原样等值；不改目录名称 |
| `projectQuantity` | int(11) / YES | `proj_project_product.project_quantity` | 原定义为项目数量；全表353,030条均NULL，保存NULL，不填0、不生成实施分配 |
| `orderQuantity` | int(11) / YES | `com_sales_order_line.order_qty` | 本批105条原值等值，不另存项目订单数量镜像 |
| `deliverQuantity` | int(11) / YES | `com_sales_order_line.delivered_qty` | 本批105条原值等值，不产生新的到货/验收事实 |
| `openQuantity` | int(11) / YES | `com_sales_order_line.open_qty` | 本批105条原值等值，不改数量权威来源 |
| `orderNumber` | varchar(25) / YES | `com_sales_order_line.order_no` | 复用已有正式订单号 |
| `lineNum` | varchar(25) / YES | `com_sales_order_line.line_no` | 原字符串精确匹配，不去前导零、不按数字猜行号 |

## 当前全部21列处理

| 当前列 | 处理及原因 |
|---|---|
| `id` | 保留；含义及类型见最终10列 |
| `tenant_id` | 保留；含义及类型见最终10列 |
| `project_id` | 保留；含义及类型见最终10列 |
| `source_system` | 撤回技术来源镜像；原值先迁到既有INT绑定并核验，不改旧V4不可变凭据 |
| `source_table` | 撤回技术来源镜像；原值先迁到既有INT绑定并核验，不改旧V4不可变凭据 |
| `source_record_key` | 撤回技术来源镜像；原值先迁到既有INT绑定并核验，不改旧V4不可变凭据 |
| `contract_no` | 通过order_line_id复用`com_sales_order.contract_no`；正式引用和全表值核验成功后撤回重复列 |
| `product_code` | 通过order_line_id复用`com_sales_order_line.product_code`；正式引用和全表值核验成功后撤回重复列 |
| `product_name` | 通过order_line_id复用`com_sales_order_line.product_desc`；正式引用和全表值核验成功后撤回重复列 |
| `project_quantity` | 保留；含义及类型见最终10列 |
| `order_quantity` | 通过order_line_id复用`com_sales_order_line.order_qty`；正式引用和全表值核验成功后撤回重复列 |
| `delivered_quantity` | 通过order_line_id复用`com_sales_order_line.delivered_qty`；正式引用和全表值核验成功后撤回重复列 |
| `open_quantity` | 通过order_line_id复用`com_sales_order_line.open_qty`；正式引用和全表值核验成功后撤回重复列 |
| `order_no` | 通过order_line_id复用`com_sales_order_line.order_no`；正式引用和全表值核验成功后撤回重复列 |
| `line_no` | 通过order_line_id复用`com_sales_order_line.line_no`；正式引用和全表值核验成功后撤回重复列 |
| `unit_code` | 通过order_line_id复用`com_sales_order_line.unit_code`；正式引用和全表值核验成功后撤回重复列；原表无单位，本批原样保持正式单位为空，沿用户批准可空规则 |
| `creator` | 保留；含义及类型见最终10列 |
| `create_time` | 保留；含义及类型见最终10列 |
| `updater` | 保留；含义及类型见最终10列 |
| `update_time` | 保留；含义及类型见最终10列 |
| `deleted` | 保留；含义及类型见最终10列 |

## 实施与扩大范围边界

先通过既有迁移/同步功能建立精确来源绑定和正式订单行引用，再核对本表全部行的9个复用属性及原NULL/空字符串。实际未唯一关联、跨租户/公司、来源或当前正式值有差异的行必须提交用户裁决，不能用快照或备注计为承接完成；本批105条等值不代表其他353,030条自动等值。
本表无源审计字段，已有105条正式ID及历史迁移审计值保留。用户批准后直接清理SQL/migrations里的本表定义到最终10列，不写增加后删除字段的迁移；真实库仅在正式字段/关系核验后清理12列。全部有用原字段由正式关系或业务字段承接，没有新增source_*列。
本提案尚未实施。独立项目数量字段是否保留，以及新增order_line_id和撤回12列，均由用户本表一次裁决。
