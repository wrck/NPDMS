# pm_project_product_line 旧业务作用与来源核查

状态：旧实现与本地原库只读核查完成；原10列整改方案暂停，未获结构批准、未改变本表结构或数据。本报告不推断旧生产当前调度配置。

## 结论

此表承载项目的合同订单产品清单，并允许项目拆分后的数量覆盖；它既有按订单复制并刷新的属性，也有独立的项目数量属性。不能仅因本批105条与订单行相同，就把整个历史表认定为没有独立业务语义的重复订单表。

原 `projectQuantity=NULL` 表示没有显式项目数量覆盖。旧明细查询用 `orderQuantity` 作为显示的项目数量；不是零数量，也不能据此宣称没有项目产品。拆分代码会修改原项目的 `projectQuantity` 并将拆出数量写入子项目。这个覆盖值必须承接，不能用当前实施分配状态或完成事实代替。

旧产品记录 `id` 会被刷新任务重新编号，它不是跨刷新稳定身份。合同/项目/产品/订单/行组合实际有重复，不能自行去重、设唯一约束或用排序序号为持续同步编造身份。

## 写入来源和更新过程

| 入口 | 数据来源及动作 | 已核实代码 |
|---|---|---|
| 新建项目/服务项目 | 按合同查询正常销售订单行，复制产品、名称、订单/发货/未交数量和订单/行号；不写 projectQuantity | `ProjectServiceImpl.java:242,383`；`ProjectDaoImpl.java:448,453`；`sql-map-project-config.xml:2098` |
| SpringMVC 项目建档 | 调用同一 `projectDao.insertProjectProductLine` 路径 | `PMS-springmvc/.../ProjectHeaderService.java:433`；`ProjectService.java:221` |
| 合并合同 | 将该合同正常订单行加入当前项目；已发货量计算为订单数量减未发货数量 | `ProjectServiceImpl.java:2084`；`sql-map-project-config.xml:3366` |
| 拆分项目 | 原项目数量减拆出量，子项目保存拆出量以及产品与订单数量属性。子项目插入 SQL 没有订单号/行号两列 | `ProjectServiceImpl.java:2104,2121`；`sql-map-project-config.xml:3455,3460` |
| ERP定时刷新 | 按项目/合同选取需刷新清单，删除重插；正常订单头 orderType=0，行号/公司码/订单类型精确连接；已发货量为订单数量减未交量 | `GainOrderByERP.java:38,145`；`sql-map-refresh-data-common-config.xml:46,97,136` |
| 刷新中的身份操作 | 对仍存记录重新排列 id，再重置 AUTO_INCREMENT；不能持续信任原 id | `GainOrderByERP.java:163`；`sql-map-refresh-data-common-config.xml:111,131` |

刷新选择条件对已有记录包含 `projectQuantity IS NULL OR projectQuantity=0`，以及无匹配订单行的情况；另含尚缺某订单行的项目合同。因此不能把它简化为“所有拆分项目永不刷新”。项目/合同范围删除会影响该范围全部产品行，不能把旧执行实现的缺陷直接当新平台同步契约。

`GainOrderBySAP` 标记废弃、合并到 `GainOrderByERP`，但仍有批量刷新代码；其逐条更新代码已注释。项目DAO实际加载 `sql-map-project-config.xml`，任务加载 `sql-map-refresh-data-common-config.xml`；`config2` 与 `_bak` 不作为当前映射依据。默认和profile调度文件存在差异，未核实旧生产运行包，不宣称某个触发时间已在生产生效。

## 真实来源对象

本地配置来源库 `dppms` 的两个所谓 SAP 对象实际均为视图：

- `pm_order_data_from_sap` 直接读取 `pm_order_data_from_erp`；
- `pm_order_line_from_sap` 直接读取 `pm_order_line_from_erp`。

因此表名带SAP不能作为实际ERP来源判定。实际 SAP/D365 来源由正式订单的来源系统字段和已批准公司码转换承接。

## 使用位置

`ProjectAction.java:864,872` 调用汇总和明细查询，`orderData.jsp:22,41` 显示设备汇总/明细并支持导出；不是设备序列号事实表。名称来自产品行，型号通过产品编码查询 `fb_items`。

明细 `queryOrderDataDetailListByProjectId` 对每行计算 `CASE WHEN projectQuantity IS NULL THEN orderQuantity ELSE projectQuantity END`。汇总 `query-orderdatalist-byprojectid` 按产品编码汇总订单/发货/未交数量及项目数量。旧汇总SQL对未分组字段存在非严格分组写法；新平台不能任选合同、记录ID或覆盖值，需要逐行计算项目数量后再按明确维度汇总。

`mergeorbranchcontract.jsp:95` 以显示的项目数量限制拆分量。`projectQuantity` 既服务显示，也参与拆分业务，不能被解释成没有用的空列。

## 原表全部11列及本次证据

| 原列 | 旧系统作用 |
|---|---|
| id | 原记录技术主键；刷新可能重排，不是稳定同步身份 |
| projectId | 产品清单所属项目 |
| contractNo | 来源合同，也用于合并合同及刷新范围 |
| itemCode | 订单产品编码，并用于查询产品目录型号 |
| itemName | 原清单中的产品名称，订单属性复制值 |
| projectQuantity | 项目数量显式覆盖；NULL时旧页面回退订单数量；拆分写入 |
| orderQuantity | 对应订单数量 |
| deliverQuantity | 已发货数量；上述写入路径取订单数量减未交数量 |
| openQuantity | 订单未交数量 |
| orderNumber | 对应订单号；拆分的子项目插入路径未写此列 |
| lineNum | 对应订单行号；拆分的子项目插入路径未写此列 |

2026-10-09实际原表353,030条、78,849个项目；projectQuantity全部NULL，订单号和行号无NULL/空字符串；订单、已发货、未交数量本次未出现负值。这仅描述当前源库，不能取消旧系统已存在的拆分数量语义。

候选组合（项目、合同、产品、订单、行）有53组重复，共417条。此前本批105条已核对与正式订单行合同、名称、三种数量一致，仍不能外推为全部历史等值；本次没有扩大迁入其他项目。

进一步按旧写入的正常订单头、公司码、订单/行号关联全量原表：353,030条没有找不到订单行的记录，352,613条唯一命中来源ERP行，417条命中多个来源行。唯一命中的记录中58条已发货数量与未交数量同时不同；产品编码、名称、订单数量均无差异。这58条项目清单中的数量不能直接替换成当前订单行值。417条多匹配记录进一步读出1,203条候选，原公司均为01（按已批准规则正式001），候选来源SMS 1,107条、D365 96条；候选的产品编码、名称、订单/已发货/未交数量全部与原项目产品一致。这是相同业务值的多个技术来源身份，仍须按正式公司＋订单/行业务键解析关联，不能任选技术ID或擅自删除重复项目产品记录。

## 对整改方案的影响

1. 正式项目产品业务实体必须保留独立项目数量覆盖及项目关联，查询复用原NULL回退订单数量的含义；不生成实施分配或到货/验收完成事实。
2. 复用正式订单行属性必须先核实公司转换、原订单/行、合同和所有原复制属性；不能凭相同编码就删除不同历史值。未匹配或差异必须阻断并提交裁决。
3. 原10列草案的 order_line_id 非空假设只被本批105条支持，未覆盖拆分子项目路径。字段结构继续待裁决，不能直接对全部历史套用。
4. 原自增ID不能作为持续同步主键；重复记录和缺失订单身份必须明确裁决后才能扩大同步。不得以迁移中间快照代替未承接业务事实。

只读原库证据保存在本任务受控目录 `project-product-purpose-source-readonly-20261009.json`。原库写入0；本产品业务表写入0；未改SQL建表或删除任何列。
