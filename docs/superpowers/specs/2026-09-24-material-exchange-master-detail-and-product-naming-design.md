# 换货申请主子表重构与命名对齐设计

- 日期：2026-09-24
- 状态：待评审（用户逐节确认后的汇总设计稿）
- 目标正式资产：`pms-module-engineering` 换货申请链（`imp_eng_material_exchange` 主表 / `imp_eng_material_exchange_serial` 子表）、`pms-module-asset` 产品信息只读 API、`sql/migrations/V354`
- 上游依据：换货申请勾选设备清单行换货已交付（提交 986caa34f）；本设计为其主子表信息重构与全链命名对齐的后续变更，属用户当面批准的需求调整

## 1 背景与目标

用户需求："换货申请是主子表关系，主表不能只保存单个型号，所以很多信息都要进行调整。子表订单行要有对应的换货产品选择。产品表见产品信息下拉选择。"

目标：

1. 主表物料信息从 SN 时代单值事实改为订单行多值事实（只显示物料编码，多值标签）。
2. 子表每行增加换货产品选择（产品信息下拉）。
3. 全链命名对齐仓库既有规范（plain 产品组/设备组命名），清除退役 vocabulary。

## 2 业务决定（Q&A 已确认）

| # | 问题 | 决定 |
|---|---|---|
| Q1 | 主表字段处置 | 只显示物料编码，数据存储按自动去重拼接，拼接多值，界面标签分割；不显示型号、名称、关联设备、单号；项目内的关联界面不显示项目、名称等 |
| Q2 | 主表编码来源 | A：子表订单行物料编码（原物料）去重拼接，由勾选行决定，与换货产品无关 |
| Q3 | 子表换货产品载荷 | A：`product_id` 引用 + 快照（编码/名称/型号） |
| Q4 | 换货产品必填性 | B：可留空，草稿后补 |
| Q5 | 下拉筛选 | C：全量可选 + 关键字搜索，停用行禁选（status ≠ ACTIVE 置灰） |

## 3 命名规范依据

- **plain 产品组**：`com_sales_order_line`（V160 新增、V294 命名对齐）既有 `item_code` 物料编码 / `item_desc` 物料描述 / `product_id` 产品ID / `product_code` ERP订单行产品编码。产品组命名不带语义前缀（如 `new_`、`legacy_`）。
- **产品名称/型号主导命名**：V100+ 迁移分布 `product_name` 7 / `item_name` 5 / `material_name` 0（后者全在 V25/V26/V35 旧领用采购表）；`product_model` 14 / `specification` 3（同旧表）。
- **设备组**：V310 已将 `pms_equipment` 整表退役（改名 `pms_equipment_retired` + 只读触发器），设备读取走 `ast_device`（资产域 DeviceDO/DeviceArchiveController）。换货链 `equipment_id` 列的值本就是 `ast_device` 主键（勾选设备一直读设备链），仅命名挂着退役的 equipment，故全链更名 device。
- **退役处置原则**：不重命名历史快照为 `legacy_*`、不追加退役无用列、不新建子承接表；历史数据随更名原样保留（RENAME 不动数据）。

## 4 数据模型最终设计

### 4.1 主表 `imp_eng_material_exchange`

| 列 | 处置 | 注释 |
|---|---|---|
| `material_code` | **更名 `product_code`** + varchar(64)→varchar(500) | 产品编码（订单行去重拼接，多值标签展示） |
| `material_name` | **更名 `product_name`** | 产品名称（旧值保留，新申报退出写入置 NULL） |
| `specification` | **更名 `product_model`** | 产品型号（旧值保留，新申报退出写入置 NULL） |
| `equipment_id` | **更名 `device_id`**（保留 FieldStrategy.ALWAYS，新申报置 NULL 依赖其强制置空语义） | 设备ID（设备表引用） |
| `new_equipment_id` | **更名 `new_device_id`** | 换后设备ID |
| `original_order_no` | 保留更名不动 | 原订单号（旧值保留，新申报置 NULL） |
| 其余列（`code` 申报编号、`project_id`、`name` 申报名称、`exchange_type`、`quantity`、`unit`、`reason` 组、CRM 推送组、审批组、状态机、`version`） | 一律不动 | — |

- 申报名称（`name`）：记录标识。工程列表流程保留输入；项目内关联界面隐藏（Q1"项目内的关联界面不显示项目、名称等"）。
- 申报编号（`code`）：记录标识，列表"单号"列保留。

### 4.2 子表 `imp_eng_material_exchange_serial`

| 列 | 处置 | 注释 |
|---|---|---|
| `product_id` | **新增** | 换货产品ID（产品信息引用） |
| `name` | **更名 `product_name`** | 换货产品名称（快照） |
| `equipment_id` | **更名 `device_id`** | 设备ID（设备表引用；旧行绑定值随更名保留，新行不写） |
| `product_code` / `product_model` | 保留 | 快照组（新行写换货产品编码/型号；旧行 SN 时代事实随列保留） |
| `sn` / `contract_no` | 原样 | 旧行设备事实，新行不写 |
| 清单行引用与订单行事实（`scope_detail_id`/`scope_id`/`order_no`/`line_no`/`item_code`/`quantity`/`device_type_code`/`device_type_name`） | 原样（986caa34f 已交付） | — |

- 不新建子承接表、不改历史列命名（V310 语义下子表无活跃的退役表主键链路）。
- 旧明细"名称"列展示链随 DO 字段更名（`name`→`productName`）改引，界面列名不变。

### 4.3 V354 迁移（单文件）

- 主表：3 处 RENAME（`product_code` 同步 MODIFY 加宽 varchar(500)、`product_name`、`product_model`）+ 2 处 RENAME（`device_id`、`new_device_id`）
- 子表：2 处 RENAME（`product_name`、`device_id`）+ 1 处 ADD（`product_id`）+ 按 V294 先例对齐复用列 COMMENT
- 数据随更名保留，历史不动；不修改任何历史迁移文件

## 5 产品信息只读 API 设计

`ast_product_official_info`（资产域"CRM产品信息副本"，8442 行；列 `id/product_code/product_model/product_name/product_desc/technical_spec/source_system/source_key/source_version/status`；status 取值 ACTIVE、FAST001_TEST_PUBLISHED）目前仅集成同步写入（`pms-module-integration` sync/generic-targets.json），无任何域 DO/API。新增：

- **资产域 DO**：`ast_product_official_info` 只读 DO，`@TableId(ASSIGN_ID)`（规格 DDL 无自增，见主键生成约定），含 `status`
- **Mapper**：场景化 Query 对象单参查询（遵循 `docs/coding/database-query-interface.md`；简单单表条件用 `LambdaQueryWrapperX`；空关键字/空集合筛选返回空结果，不因省略条件扩大范围）
- **Controller**：`/api/v1/pms/asset-product-officials/page` —— 分页 + 关键字（`product_name`/`product_code`/`product_model` 模糊）+ 返回 `status` 供前端禁选判断（路径遵循新增 PMS Business API `/api/v1/pms/...` 规范，实例：`/api/v1/pms/delivery-scopes`）
- **跨模块边界**：工程域换货下拉只调此 API，不依赖资产域 `-biz`/Service/Mapper、不直接访问 `ast_product_official_info`

## 6 后端保存/校验联动设计

- 保存时主表 `product_code` = 子表行 `item_code` 去重、半角逗号拼接（勾选行决定，与换货产品无关，Q2=A）；`quantity` = 子表行合计（现状保持）
- 换货产品可留空（Q4=B）；填写时服务端校验 `product_id` 存在且 `status=ACTIVE`（停用禁选，Q5=C），快照 `product_code`/`product_name`/`product_model` 由服务端按产品引用写入，不信任前端传值
- 置空联动：换货产品清空时 `product_id` + 快照组一并置空
- 主表 `product_name`/`product_model`/`original_order_no`/`device_id` 新申报退出写入置 NULL（Q1；`device_id` 置空依赖保留的 ALWAYS 强制策略）
- 子表行不再写 `device_id`/`sn`/`contract_no`（旧行原样）
- VO/DO 字段同步：主表 `materialCode`→`productCode`、`materialName`→`productName`、`specification`→`productModel`、`equipmentId`→`deviceId`、`newEquipmentId`→`newDeviceId`；子表 `name`→`productName`、`equipmentId`→`deviceId`
- openEdit 极旧申请兜底（无子行时按主表设备绑定伪造子行）保留：读的本来就是 DeviceArchiveApi 设备链，字段随更名

## 7 前端设计（yudao-ui 换货页 + picker）

- **editor**：项目（standalone 选择，项目内隐藏——现状已实现）、单号（编号只读展示）、申报名称（项目内隐藏）、换货类型、设备清单 picker、换货产品行内下拉；主表物料编码多值标签只读派生展示，物料名称/型号/关联设备/原订单号退出显示（Q1）
- **换货产品行内下拉**（picker 已选行表 + 明细行表）：可留空、关键字搜索、停用行置灰禁选（Q5=C）；数据源 `pms-module-asset` 产品信息 page API
- **列表页**：`物料名称` 列替换为物料编码多值标签列；名称（申报名称）与原订单号列移除；其余列（单号/项目/换货类型/数量/CRM状态等）保留
- **明细/详情**：明细行表增加换货产品列（旧行 NULL 展示）；详情增加申报名称/项目内隐藏与 Q1 对齐
- **组件链路**：换货页移除 EquipmentTag 用法（Q1 详情不显示关联设备，行内设备绑定链路随字段更名）；`EquipmentTag`→`DeviceTag` 组件级更名波及 7+ 其他页面（工程配置/安装/联调、设备台账等），按"所有链路改 device"随本设计一并执行，边界见评审确认项

## 8 测试与种子设计

- **资产域 page API 测试**：精确命中、部分限定（名称/编码/型号模糊）、优先级让位、无匹配、停用不参与（status 过滤）、空权限/空集合返回空结果
- **工程域保存联动测试**：拼接去重（多值半角逗号）、主表退出字段置 NULL、换货产品引用校验（不存在/停用拒绝）、快照服务端写入、置空联动
- **前端验证**：真实浏览器闭环（勾选清单行 → 行内选换货产品 → 保存 → 主表多值标签/列表列对齐 → 留空草稿后补），HTTP 200 ≠ 业务成功需查落库
- **种子**：功能完成后幂等示例数据迁移（前向版本、creator 标识、高段 ID），覆盖换货产品已选/未选、多值编码、快照组合等关键维度；换货产品引用取 `ast_product_official_info` 真实行，不臆造取值

## 9 验证计划

- 后端：asset 新增 API 测试、engineering 换货 Service 测试（隔离重跑受影响用例）；并行工作流既有无关失败如实记录不扩大
- 迁移：V354 前向应用 + Flyway 校验；更名后旧行数据比对（更名前后抽样）
- 前端：vitest 受影响用例 + 真实浏览器业务闭环（10.210.0.11:19191）
- 汇报边界：编译/单测不替代业务验收；既有无关失败不掩盖不借机扩大

## 10 评审确认项

1. 申报名称（`name`）：设计按"记录标识"保留工程列表流程输入、项目内隐藏；列表名称列移除。如 Q1 的"名称"另有所指请指出。
2. 申报编号（`code`，界面标签"单号"）：设计按 Q1"单号"= 原订单号（`original_order_no`）理解，申报编号列保留。如"单号"另有所指请指出。
3. `EquipmentTag`→`DeviceTag` 组件级更名波及 7+ 其他页面（工程配置/安装/联调 workspace、设备台账 config-log 及其 runtime spec）。按"所有链路改 device"默认随本设计一并更名全部引用；如需收窄到换货链边界（仅移除换货页用法、组件更名另立任务）请指出。
