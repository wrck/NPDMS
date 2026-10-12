# 项目产品合并到实施范围：已裁决整表方案

状态：PILOT_FORMAL_CARRY_VERIFIED / RETIREMENT_AND_FULL_HISTORY_PENDING。用户批准三列及版本唯一性仅约束已确认分配；105条已通过迁移功能和COM Owner迁入正式实施范围，10个纠正命令重放、105条预览均无业务改写。order_quantity撤回，复用正式订单行order_qty；项目数量优先，NULL回退正式订单数量，正数量约束保持。办事处取正式项目；状态ACTIVE、版本和生效时间沿正式Owner生成，allocation_confirmed=false，不占用当前订单数量或分配槽位。原18条范围及范围水位、明细、项目主档和其他项目业务表核对不变。旧105条仍作为初始纠正基线保留，待替换基线依赖后撤回旧表；不能据本批结果宣称持续同步或全历史完成。

消费者保护已完成本轮验证：有效分配、订单数量、锁定、验收和ERP影响查询仅纳入allocation_confirmed=true；列表仍显示历史false范围。设备清单选择通过场景化DeliveryScopeSelectionQuery限定租户、项目、确认值和有效期，空集合直接返回空，false与当前分配混选阻断。既有消费者检查保留；本次Owner、MySQL函数唯一性与纠正路径28项通过，前端范围、缓存面板可见性及正式资料13项通过，日期补正受影响2项通过。真实浏览器已核验项目51922的两个false范围、数量/办事处/版本/生效日期、历史抽屉重开、无调整/释放按钮；范围水位仍0。本次没有在浏览器执行实际分配动作，不将只读验收宣称为当前分配或验收流程完成。

## 建议及必要差异

补充消费者修正：ProjectCommerceMapper中按范围推导当前合同、订单的三个EXISTS分支已限定allocation_confirmed=true。真实MySQL临时表先复现false记录错误引入两个合同，修正后5项检查通过；更新后的59282后端逐项目核对10个合同/订单概览与正式关系一致，123条范围及项目保护数据未改。真实浏览器重新打开正式范围页，数量、未确认标记、历史日期及范围水位0一致。证据：verified-scope-confirmed-commerce-consumers-20261010.json、verified-scope-confirmed-consumer-browser-20261010.txt。

复用现有COM实施范围Owner及com_delivery_scope，撤回独立proj_project_product的10列和19列提案。不另建可维护的项目产品模型。原清单一行形成一条正式项目范围记录；NULL项目数量表示默认涵盖该订单行全部数量，显式数量表示项目的覆盖数量。

不能直接执行当前assign：它要求正数、确认订单数量、单位精度及明确办事处，锁定当前分配，校验总量、发布业务事件并绑定验收。原表没有当前有效分配确认、生效日期或分配版本。建议用明确业务属性allocation_confirmed区别“正式保存的范围”和“已确认的有效分配”，保持现有分配的全部校验。业务状态不新增。旧范围查询有正式数量列，不依赖迁移快照或source_evidence。

现有42列；order_quantity不新增，直接复用正式订单行order_qty，逐条核对原orderQuantity一致后关联承接；全历史本次原库关联未发现此值差异，未来发现差异先阻断并待裁决。已批准新增3列，最终45列。allocated_qty按用户补裁决：projectQuantity为空时填正式订单行完整数量，显式拆分数量按原值。delivered_quantity、open_quantity保留原表当时的发货/未交量，不以后来订单值替换58条差异。新增字段必须逐项裁决，未批准不实施。

原表353030条、78849项目，projectQuantity本库全部NULL。原三种数量没有NULL，3057条订单数量为0；不能伪造成正数。全历史58条发货/未交数量不同于当前订单，53组417条重复，来源刷新会重排ID。按合同/订单/行/产品字符串另有8232组17002条分布于多个项目（最多6个）；此为候选重叠，公司/ERP精确解析尚未完成，不宣称全是分配冲突。本批10项目105条当前范围/明细均0；本批与订单数量等值不取消全历史差异。

## com_delivery_scope 全部42个既有字段

下表类型/可空性是本任务真实库当前结构。此前八项可空方案已撤回；实际分配的条件约束保留，不能全局放宽。

| 字段 | 当前类型 | 当前可空 | 原字段及建议处理 |
|---|---|---|---|
| `id` | bigint | NO | 复用正式ID；原技术ID及旧105条目标关系由INT绑定维护，不按重排ID自动重绑。 |
| `tenant_id` | bigint | NO | 复用当前租户；项目、订单、部门及所有查询保持租户隔离。 |
| `project_id` | bigint | NO | 原projectId经正式项目绑定精确关联，不用同数值ID。 |
| `project_code` | varchar(64) | NO | 通过PROJ Owner取得已裁决的原项目编码；项目纠正前不写入冻结错误编码。 |
| `project_name` | varchar(255) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_company_code` | varchar(64) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_company_name` | varchar(255) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_department_code` | varchar(64) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_department_name` | varchar(255) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_customer_code` | varchar(64) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_customer_name` | varchar(255) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_manager_employee_no` | varchar(64) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `project_manager_name` | varchar(128) | YES | 复用正式项目同义属性，由PROJ Owner精确取得；源产品表无此列，不宣称为源历史值；无可核实值保持NULL。 |
| `order_line_id` | bigint | NO | 复用正式订单行引用；合同、公司转换、订单/行和产品全部核实。不能唯一关联则阻断该行，不删源事实。 |
| `current_order_line_id` | bigint | YES | 生成列不手工写；建议仅allocation_confirmed=TRUE、未删除、effective_to为空时生成。旧范围不占有效分配唯一槽位。 |
| `order_source_system` | varchar(32) | NO | 复用实际SAP/D365/SMS身份；不得由视图名判断ERP。 |
| `order_company_code` | varchar(64) | NO | 复用已裁决01→001、02→002、03→003、04→004；未知码阻断。 |
| `order_company_name` | varchar(255) | YES | 复用正式订单公司属性，经正式目录精确取得，不从名称猜关联。 |
| `order_type` | varchar(32) | NO | 复用精确正式订单类型；旧正常订单按核实原值，不任意选取多来源。 |
| `order_no` | varchar(64) | NO | 原orderNumber原字符串；核实与正式行一致。 |
| `line_no` | varchar(32) | NO | 原lineNum原字符串；不去前导零或按数字猜配。 |
| `product_code` | varchar(64) | YES | 原itemCode原值，目录按租户＋编码关联。 |
| `product_desc` | varchar(512) | YES | 原itemName原样作为项目范围产品名称，不改当前产品目录名称。 |
| `allocated_qty` | decimal(18,6) | NO | projectQuantity优先，为NULL时填正式订单行完整数量；保持allocated_qty>0，结果为0或负数阻断并列明，不伪造正数。 |
| `scope_status` | varchar(32) | NO | 按正式Owner初次分配结果填ACTIVE，不申请可空、不新增状态；旧范围confirmed=false，不据ACTIVE参与当前数量占用或验收锁。 |
| `allocation_version` | bigint | NO | 在Owner锁定项目范围版本后按scope_version+1生成；同一锁定水位的历史记录可同版本，不使用原技术ID或迁移次数，不推进当前分配水位或触发事件。 |
| `allocation_source` | varchar(32) | NO | 复用现有分配来源属性，建议旧范围值DPPMS；这是需确认的新来源取值，不作为有效分配证明。普通分配取值不变。 |
| `change_reason` | varchar(500) | YES | 原表无字段，NULL；不塞原字段或迁移JSON。 |
| `department_id` | bigint | NO | 按用户裁决精确取正式项目办事处；无法核实阻断待关联，不申请可空，不据此扩大项目访问权限。 |
| `department_code` | varchar(64) | NO | 通过正式项目办事处取得，保持必填。 |
| `department_name` | varchar(255) | NO | 通过正式项目办事处取得，保持必填。 |
| `department_version` | int unsigned | NO | 通过正式项目办事处Owner取得真实部门版本，保持必填。 |
| `source_evidence` | varchar(255) | YES | 保持既有技术证据用途；最多引用正式INT纠正运行，不承接11项业务值。 |
| `effective_from` | datetime(3) | NO | 用户要求按正式分配生成结果填充；现行Owner取实际执行时间。明确为本次形成范围的日期，不宣称原表历史生效日期，不申请可空。 |
| `effective_to` | datetime(3) | YES | 旧范围无原结束日期，NULL；不虚构已结束分配。 |
| `status` | varchar(32) | NO | 复用ENABLED表示正式记录可查询；与是否已确认分配分开，不能授予权限或生成完成事实。 |
| `version` | int unsigned | NO | 复用Owner行乐观锁版本；首次迁入0，与allocation_version不同。 |
| `creator` | varchar(64) | NO | 原表无审计人员；已有105条迁移审计保留，新记录用实际迁入操作者。 |
| `create_time` | datetime(3) | NO | 原表无创建日期；已有105条原迁移日期保留，新记录用实际迁入日期。 |
| `updater` | varchar(64) | NO | 保留既有105条迁移审计；后续动作由Owner记录真实操作者，不冒充原人员。 |
| `update_time` | datetime(3) | NO | 保护已有105条历史迁移时间，显式阻止ON UPDATE自动改写；纠正运行另记审计。 |
| `deleted` | tinyint | NO | 保持逻辑删除；不复活旧记录，不据重复自行删除。 |

## 同表已批准新增3列

已有实体字段直接承接。order_quantity不新增，复用com_sales_order_line.order_qty。用户已批准以下3项及上述含义；本批105条已实际迁入并逐字段核对，消费者保护和真实范围页面核验完成。

| 字段 | 类型 | 可空 | 含义及来源 |
|---|---|---|---|
| `allocation_confirmed` | BIT(1) | 否 | 建议默认TRUE供现有普通分配；本次旧范围明确FALSE，表示尚未确认占用订单可分配数量。不是审批或生命周期状态。 |
| `delivered_quantity` | DECIMAL(18,6) | 是 | 原deliverQuantity，范围记录当时已发货数量；不是本平台已到货/安装/验收事实。 |
| `open_quantity` | DECIMAL(18,6) | 是 | 原openQuantity，范围记录当时订单未交数量；58条差异完整保留。 |

## 条件约束与现有消费者

1. allocation_confirmed=TRUE：沿用现有有效分配的正数量、订单行、办事处、版本、生效日期和状态约束；当前数量占用、验收锁、拆分/调整/释放及业务事件仅消费这些记录。现有普通分配默认TRUE，不改变既有事实。
2. allocated_qty、办事处四项、scope_status、allocation_version、effective_from保持必填，按用户最新裁决填充。旧范围allocation_confirmed=FALSE，不占用当前订单数量、不触发分配或验收事件；三个字段由Owner按既有生成规则形成，不调用具有当前分配业务效果的assign。
3. current_order_line_id仅对已确认分配生成，原18条当前槽位不变。版本唯一键按DPPMS-SCOPE-040用户批准改为MySQL函数唯一索引，仅约束allocation_confirmed=true，包含确认分配的关闭历史；不新增可见业务列。false逐条核验来源身份，53组重复不能自行去重。数量按DPPMS-SCOPE-039用户裁决保持allocated_qty>0，先项目数量、NULL才回退订单数量，结果仍为0的行阻断并列明，不伪造正数。跨项目旧范围不占用当前总量，但不据此授权成为确认分配。
4. 范围查询统一由COM Owner提供，可查询旧范围及确认分配，并明确区分数量是否已确认占用；不得将两类数量重复累加。对重复/重叠先逐行展示，不任选一行当权威。不额外创建角色、权限或审批。
5. 原order_line_id关联必须精确；417条多来源候选先解析正式公司＋订单/行业务身份，不能任选技术ID。未来遇拆分子行缺订单身份时阻断并待裁决，不借挂其他订单，不丢弃原记录。

## 原表全部11列承接

| 原字段 | 正式承接点 | 不映射/缺项原因 |
|---|---|---|
| id | 既有INT来源绑定及本次迁入行映射 | 技术ID会重排，不新建业务镜像，也不作为持续同步稳定键。原行不可自行去重。 |
| projectId | com_delivery_scope.project_id | 经正式项目绑定关联；不直接采用原数值ID。 |
| contractNo | 经order_line_id关联正式订单合同及项目合同关系 | 精确核对原合同；不新增重复contract_no。无法一致关联时阻断待裁决，不能丢弃。 |
| itemCode | com_delivery_scope.product_code | 原值保留，正式产品目录租户＋编码关联。 |
| itemName | com_delivery_scope.product_desc | 原项目范围产品名称原样保留；不覆盖目录名称。 |
| projectQuantity | com_delivery_scope.allocated_qty | 用户裁决为空时填正式订单行完整数量；显式拆分值按原值，原NULL由INT来源证据追溯，不创建重复业务列。 |
| orderQuantity | com_sales_order_line.order_qty | 复用既有正式字段，撤回重复order_quantity候选；通过order_line_id精确关联并逐值核对。 |
| deliverQuantity | 建议com_delivery_scope.delivered_quantity | 当前根表无同义数量列；保留58条历史差异，不更新当前完成事实。 |
| openQuantity | 建议com_delivery_scope.open_quantity | 当前根表无同义数量列；保留58条历史差异，不以当前订单值覆盖。 |
| orderNumber | com_delivery_scope.order_no | 原字符串精确承接。 |
| lineNum | com_delivery_scope.line_no | 原字符串精确承接。 |

单位来源没有对应列；通过正式订单行复用已批准可空单位，不增加单位镜像。

## com_delivery_scope_detail 全部21列（建议结构保持）

原项目产品没有SN、设备类型、交付批次或逐设备明细。建议不为旧范围伪造当前设备明细；11项原字段均由根范围或正式关系承接。已存在明细与原功能不改。新增设备关联必须另循已批准设备事实Owner及用户归属裁决。

| 既有字段 | 当前类型 | 可空 | 本次处理 |
|---|---|---|---|
| `id` | bigint | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `tenant_id` | bigint | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `delivery_scope_id` | bigint | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `detail_sequence` | int unsigned | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `serial_no` | varchar(128) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `product_code` | varchar(64) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `product_name` | varchar(255) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `device_type_code` | varchar(64) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `device_type_name` | varchar(255) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `delivery_batch_no` | varchar(64) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `source_record_key` | varchar(128) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `allocated_qty` | decimal(18,6) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `detail_status` | varchar(32) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `source_snapshot` | json | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `remark` | varchar(500) | YES | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `version` | int unsigned | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `creator` | varchar(64) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `create_time` | datetime(3) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `updater` | varchar(64) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `update_time` | datetime(3) | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |
| `deleted` | tinyint | NO | 保持既有结构和业务用途；原产品清单无同义设备明细，本次不伪造本字段或新明细行。 |

## 撤回旧项目产品实体的顺序及持续同步缺口

通过既有迁移/同步功能及COM正式Owner实施，来源原11列必须逐行读取。先整表裁决、核实正式项目/订单/合同/产品关系、生成可审查预览；批准范围内实际迁入，核对根字段、原NULL、三种数量、重复行和历史迁移审计，再替换正式查询。原105条转入过程保持目标关联和审计；当前21列里的技术来源三列归INT绑定，其他业务字段均在上述正式字段/关系承接。清理proj_project_product及其代码、当前SQL定义必须在承接与全表依赖验证完成后，不能先删数据。

持续同步身份仍为BLOCKED_BY_SPEC：原ID重排，53组重复不能构造唯一业务键，未确认稳定身份前只允许本批基线内复用已核实绑定，不启用删除重建或持续刷新。是否将具体旧范围确认为当前分配，以及重复/跨项目数量的裁决，应另列逐项清单；本表批准不等于批准任意激活分配。

本文件是用户已裁决整表方案；DO与SQL/migrations/V421__approved_dppms_delivery_scope_attributes.sql包含获准三列、当前槽位生成条件及版本函数唯一索引。指定任务库共123条：原18条不变，新105条false范围；设备明细未新增。受控执行证据：verified-approved-scope-version-index-20261010.json、verified-product-scope-formal-carry-20261010.json、verified-product-scope-repeat-20261010.json、verified-product-scope-browser-integrated-20261010.txt。

2026-10-10已完成重复产品表退出：105条旧ID及四项审计通过既有迁移证据Owner保留为技术绑定元数据，业务值全部在正式范围及订单实体；新迁入及预览不再读取proj_project_product。10个新对账命令完成并全部重放，123条范围、范围版本/明细及项目事实均保持。确认无外键、视图、触发器依赖后，任务库旧表已清理，其DO/Mapper/XML和当前V412建表定义已撤回；原库、旧来源证据及旧回执未改写。旧表不存在时105条预览仍UNCHANGED、10命令仍REPLAY_COMPLETED。修复本次ISO审计日期误读为时间戳的问题，2项失败复现后相关28项通过；真实页面显示38条首个项目范围及未确认标记。受控证据：product-scope-retirement-guard-reviewed-20261010.json、verified-product-scope-v3-reconciliation-20261010.json、verified-product-scope-v3-replay-20261010.json、verified-product-table-approved-retirement-20261010.json、verified-product-scope-after-table-retirement-20261010.json；旧表完整结构及105行保存在私有恢复证据，不作为业务承接。

整体Flyway历史与当前最终SQL的基线对齐、全历史稳定身份规则仍未完成，不宣称上线完成。切换前仅接受已核查的外部V428/V429追加记录，原364条Flyway记录及全部受保护业务值不变；本次没有repair或改写Flyway历史。

2026-10-10配套关联纠正：用户指出全量实施范围承接已明确批准，原“不写明细”属于漏实现。已沿正常无SN产品数量明细规则，通过COM Owner及既有迁移入口补齐105条com_delivery_scope_detail，复用21既有列；原105条false范围、18条正常范围及29条正常明细均保持。10条新V4回执追加保存，105条重放UNCHANGED，原页面32产品单订单两页及9产品多订单的订单/数量验证通过；此前不写明细的执行证据保留。详见[配套明细纠正及实际验收](2026-10-10-dppms-equipment-association-repair-decision.md)。此纠正不新增物理结构，不激活当前分配，不改变页面取值逻辑。
