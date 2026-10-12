# 设备清单迁移关联修复：既有范围明细整表方案

状态：105条范围明细补关联已验收；40条发货订单行引用及11台设备发货引用补正已执行并验收。新增物理表、列、索引均为0。各轮页面不同，旧浏览器证据仅证明当时声明的范围，不能代替本次实际页面验收。

用户确认“这个不是之前裁决时已经明确的内容吗，现有设备清单pm_project_product_line按全量实施范围分配，为什么会有遗漏”。本次补齐属于已批准实施范围承接的漏实现，不再要求重复裁决；allocation_confirmed=false等已批准边界保持。

用户最新要求保持原页面取值逻辑，只补全迁移关联。本轮改变设备清单取值、逐范围展示、列配置的实现已全部撤回；新增前端加载器及测试已退出工作树。该方案的浏览器证据仅用于问题定位，不能作为当前修复完成证据。其他既有页面改动保留。

## 已核实缺口与正常业务依据

指定任务库共有123条实施范围、29条既有明细。本批10项目的105条DPPMS范围已关联正式订单行，但全部没有明细。原页面遍历scope.details，按产品汇总项目数量和订单引用；没有明细时订单和数量无法关联进入产品汇总。这不是订单行数量未迁入。

正常COM Owner已有产品数量明细：CommerceDeliveryScopeCommandService.insertDetails与DeliveryScopeCompatibilityService.insertProductDetail在无序列号时写一条product_code＋allocated_qty明细；有序列号时才写逐设备明细。本次复用该规则，不制造SN或设备分配事实。此前仅写主记录而漏写配套明细是迁移实现错误，须通过Owner纠正，保留此前执行证据。

原pm_project_product_line的11列仍按已批准整表方案承接：订单/合同/产品关系及订单数量在正式订单实体，项目数量和原发货/未交数量在范围主记录。原项目数量为空时取正式订单数量；本轮补关系不得覆盖任何原数量，也不得通过修改普通查询绕过缺失关系。

原页面多订单逻辑已在真实页面核验：项目992203060151，SO058302有9条范围、SO078894有3条，按产品汇总为9行。产品99530156分别来自两订单，页面仍显示两个订单号，项目数量4、发货数量4、未发货数量0。主记录及其正式订单行数量一致。原订单表16列核查另外发现本项目的正式source_system仍为ERP、原值为D365；该项目不在已纠正10项目内，本轮未覆盖或调整这项既有差异，不能将数量一致宣称为完整字段一致。

## com_delivery_scope_detail 既有21列：建议整体复用

每条已核实历史范围补一条产品数量明细，主记录allocation_confirmed仍为false。明细不表示当前订单数量占用，也不产生分配、验收或设备归属动作。

| 既有列 | 当前类型/可空 | 建议本次值与依据 |
|---|---|---|
| id | BIGINT / 否 | 正式Owner生成，不复用原技术ID冒充明细ID |
| tenant_id | BIGINT / 否 | 已核实父范围租户 |
| delivery_scope_id | BIGINT / 否 | 精确关联现存105条范围，保留父ID |
| detail_sequence | INT UNSIGNED / 否 | 1，与正常无SN产品明细规则相同 |
| serial_no | VARCHAR(128) / 是 | NULL；原产品清单没有SN，不制造设备明细 |
| product_code | VARCHAR(64) / 是 | 原itemCode及已核实范围product_code |
| product_name | VARCHAR(255) / 是 | 原itemName及已核实范围product_desc |
| device_type_code | VARCHAR(64) / 是 | NULL；原清单没有设备类型 |
| device_type_name | VARCHAR(255) / 是 | NULL；不凭产品名称猜设备类型 |
| delivery_batch_no | VARCHAR(64) / 是 | NULL；原清单没有交付批次 |
| source_record_key | VARCHAR(128) / 是 | 父范围已核实迁移身份＋产品明细标识，受控幂等；不作为业务字段承接 |
| allocated_qty | DECIMAL(18,6) / 否 | 父范围已有正数量，与正常产品数量明细相同；不按设备台数重算 |
| detail_status | VARCHAR(32) / 否 | ACTIVE，沿正式产品明细规则和已批准父范围状态 |
| source_snapshot | JSON / 是 | NULL；业务字段在实体列，不以快照代替 |
| remark | VARCHAR(500) / 是 | NULL；不把未映射原值写备注 |
| version | INT UNSIGNED / 否 | 0，沿正式新明细规则 |
| creator | VARCHAR(64) / 否 | 本次正式迁移操作者；来源没有明细创建人 |
| create_time | DATETIME(3) / 否 | 本次正式补关系时间；不冒充旧清单历史日期 |
| updater | VARCHAR(64) / 否 | 本次正式迁移操作者 |
| update_time | DATETIME(3) / 否 | 本次正式补关系时间 |
| deleted | TINYINT / 否 | 0，沿正式新明细规则 |

## 执行与验证边界

通过既有迁移/同步及COM Owner预览和执行，复用已核实来源、范围绑定及订单行。执行前对105条父范围、11原字段、正式订单行及既有明细完整比对，有变化阻断；既有明细不能删除重建。补关联仅能新增缺失且同租户的产品明细；重放应UNCHANGED，不改父数量/审计/范围水位、不发布当前分配事件、不生成验收关系。新的PRODUCT_SCOPE_V4回执及完整明细Owner结果追加保存，原V1/V2/V3回执继续可读。

真实浏览器沿原页面验证单订单和多订单产品汇总、订单名称与数量；当前29条既有明细及123条范围主记录保护。原页面首批200条设备/范围读取限制保持现状，本方案不通过迁移数据伪装分页完整性。

只读原始证据：equipment-list-readonly-baseline-20261010.json、equipment-list-quantity-verification-20261010.json、equipment-association-missing-detail-audit-20261010.json；原逻辑恢复及多订单浏览器证据：equipment-list-original-logic-multi-order-20261010.txt。证据位于本任务私有目录，不进入业务表。

## 实际纠正与验收结果

31项定向测试通过，其中3项在指定MySQL的连接内临时表验证真实SQL、租户隔离、约束及回滚；首次测试替身的空参错误修正后重跑通过，失败日志保留。完整后端打包通过，已授权的任务59282切换到本次冻结产物；主工作树59191及DAC未操作。本次没有DDL或Flyway操作。

原始11列共105行、123条范围主记录、29条既有明细在执行前完整核对。10项目预览均仅UPDATED，随后通过既有迁移入口及COM Owner新增105条21列明细，明细总数134。父范围ID、全部业务值及审计未变，29条旧明细未变；全表订单头/行、执行单、合同及关系、项目事实、设备、范围版本水位、COM分配事件、验收关联及Flyway历史均核验不变。重放105条预览全部UNCHANGED，10命令均REPLAY_COMPLETED，134条明细21列全部保持。

历史保护另核对原产品范围来源记录、项目来源快照及记录、原幂等回执和操作审计均未改写。新的10条V4操作审计包含105个实际新增明细ID及完整Owner结果，父范围绑定仍指向原ID。旧回执不是本次完成凭据，原“不写明细”的执行结果保留为错误实现证据。

真实浏览器沿原设备清单验证项目992203060189：38条范围汇总为32个产品，两个页面全部订单/项目数量/发货数量/未发货数量与正式关系一致，例如02051429显示SO065355、2/2/0。项目992203060151保留12条正常范围、2个订单、9个产品；99530156及99530157跨订单均显示SO058302、SO078894和4/4/0，53011886显示2/2/0；重新打开产品详情，两个订单及数量仍正确。全部10迁入项目及该多订单项目的接口关联独立核对通过。

本次不改变原页面按产品汇总及订单行归集规则。页面原有设备首批200条读取边界仍在，992203060189/190的设备读取未涵盖全部设备，不能将本次订单/数量恢复宣称为全量设备台数验收。产品名称/型号仍沿原页面设备身份取值，缺设备身份的产品不因此伪造设备或修改页面规则。全历史原ID重排、53组重复的持续同步身份裁决仍未完成，本次限定已核实105条基线，不能宣称全历史迁移或生产上线完成。

受控证据：equipment-association-approved-baseline-20261010.json、equipment-association-approved-preview-20261010.json、equipment-association-approved-applied-20261010.json、equipment-association-approved-repeat-20261010.json、equipment-association-history-verified-20261010.json、equipment-association-original-page-expectations-20261010.json、equipment-association-repaired-browser-verification-20261010.json。真实翻页完成证据使用page2-settled文件；page2文件记录点击后数据尚未完成的中间状态，不作为通过凭据。

## 设备台数与组合产品的后续核查

用户指出992203060189设备数量不等于项目数量、992203060198仅2行有订单及数量。对当前页面、正式表及原表再次只读核查：前者原38条项目产品按编码汇总32组、合计699，正式设备也为699；页面仅读首批200设备，导致台数截断。当前设备清单汇总块与工作树HEAD一致，不能仅以恢复HEAD宣称没有功能缺陷。此前没有完成全量台数验收是验证遗漏。

本轮只把实施范围、设备和订单行的三个分页读取点改为读完全部页；原scope.details项目数量、订单号＋行号去重归集发货/未发货量、产品聚合和列配置保持。3项实际加载器定向测试通过，修复前其中2项分别复现200设备截断与后页订单/范围遗漏；修复后699设备、跨订单后页去重以及不伪造组合子产品数量通过。真实浏览器两页32行逐行核对设备数量与原项目数量，合计699；正常多订单项目992203060151仍为9组，99530156/99530157的两订单和4/4/0/4保持。本轮仅前端及测试修改，没有业务写入、DDL、服务重启或迁移回执改写。

992203060198的原pm_project_product_line和pm_order_line_from_erp均只有2行：SO064298第1行01100518数量4，第2行02010078数量16。当前40设备按产品汇总7组；另5组为02010950×8、02010481×4、02010484×4、02051992×2、02051578×2。发货原字段com_barcode引用4个01100518组合序列号；fb_shipment_barcode_relation按原合同查询为0，不能据此谎称已经找到正式主子关系记录或独立子产品订单数量。原旧设备汇总SQL也只从pm_project_product_line汇总，并不从发货台数生成子产品订单数量。

同时已形成未执行的逐条关联候选：40个正式发货记录的order_line_id均为空。原fb_shipment_barcode.lineNum为INT型10625024/10625025，原ERP订单行customInfo.inventTransId为相应字符串；结合订单号、明确D365来源、已批准01→001转换、正式行号及产品编码，40条候选分别关联第1行24个发货记录、第2行16个。不改变发货line_no原值，不凭子产品编码另造订单行。11台既有设备的shipment_record_id仍指向当前发货表不存在的旧ID，并非该字段为NULL；29台引用已一致。一个原SN字母大小写与正式设备不同，候选保留双方原值，仅依据现有不区分大小写身份查询核对，未更名。候选是只读问题材料，尚非正式Owner预览或实际承接回执。

2026-10-11只读复核：40条发货的全部39列及40台设备的全部53列与上述候选基线一致，没有后续修改。两条正式订单行仍为D365、公司001，其custom_info.inventTransId分别精确匹配10625024/10625025；订单号及正式行号一致，候选仍为24/16。11个旧发货ID在全表按ID查不到，40条order_line_id仍为空。任务私有 equipment-reference-refreshed-readonly-20261011.json 保存完整当前字段、原身份与核查结果。

复用审查：正常发货投影及DeviceProjectionReconciliationService可以刷新一组投影字段，无法作为仅纠正11处引用且保护所有其他字段/审计的动作直接调用。既有DppmsDeviceSourceRelationshipApi承接主子设备历史关系，也不负责本次两种引用修复。order_line_id由既有V324定义，属于39列中的正式字段；当前DeviceShipmentDO尚未声明该Java属性，不能将缺少模型映射误判为需要新增物理列。下一步需通过AST Owner限定补正已有字段，并由COM Owner验证正式订单行身份、PROJ既有迁移/同步保护来源绑定及回执；不从PROJ直接访问或写入AST/COM表。此处仍为核查和实施边界，尚未执行引用补正，不将只读候选标记为已迁入。

原页面数量只来自范围明细；补发货→订单行及设备→发货引用本身不会给5类子设备生成数量。已向用户询问保持订单口径还是指定子设备独立数量依据；未答复前不复制父订单4套数量，不以已发货台数推断项目计划数量，不修改页面取值，不额外创建范围/明细或授权。实际引用纠正仍需通过既有迁移功能、Owner、完整原字段/现值及后续变化保护执行。本问题保持未完成。

当前受控证据：equipment-quantity-followup-audit-20261010.json、equipment-bundle-relations-audit-20261010.json、equipment-order-reference-candidates-20261010.json；真实页面equipment-quantity-followup-project189-page1/page2-20261010.txt、equipment-quantity-followup-multi-order-20261010.txt、equipment-quantity-followup-project198-20261010.txt。原代码SQL定位：D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-project-config.xml的queryOrderDataDetailListByProjectId、query-orderdatalist-byprojectid及WebContent/sys/module/sub/orderData.jsp。

用户追问“组合产品内的设备是从哪里来的”后，已明确说明：额外5类来自fb_shipment_barcode并在正式ast_device中保存；当前页面先从范围明细建立产品行，再遍历全部项目设备并追加未在范围中的产品组。com_barcode可核对组合序列号引用，但不是已找到的正式主子关系或产品配置依据；“组合产品内”作为已确认业务定性的表述撤回。实际待裁决改为显示边界：按实施范围订单产品显示2行，额外设备在序列号详情保留；或沿当前7组汇总、仅2行有订单数量。用户要求保留取值逻辑，未裁决不自行加筛选。

只读保护复核通过：123范围主记录、134明细全字段、105原字段行、订单/项目/设备/事件/历史保护均未变；11个旧发货引用ID在当前发货表确实无对应记录。代码汇总块逐字与HEAD比对，除了三个全量分页调用没有其他变更。证据equipment-quantity-readonly-protection-20261010.json、equipment-quantity-followup-browser-proof-20261010.json。引用缺口和显示边界仍保持未完成，不将只读候选计为迁入。

## 最新显示边界裁决及验收

用户回复“按实施范围订单产品显示（建议）”，批准修正设备遍历中自动追加范围外产品行的行为。范围明细建立产品组后，设备遍历仅查询既有产品组，匹配时补身份和台数，不匹配时不创建产品行；原项目/发货/未发货数量、订单号＋行号去重及列配置不改，不新增范围或明细，不调整设备资料或归属。

实际加载器3项测试通过；第三项按新裁决验证范围外设备不被混入产品清单。真实页面992203060198的2行分别为SO064298、4/4/0/4和16/16/0/16；随后打开序列号详情仍显示40台，包括范围外的02010950设备。当前用户正在查看的序列号页保留，后续验收另开任务页签。992203060189仍按32产品逐行核对全部699台；正常多订单汇总按原规则保持。显示裁决已完成，不再向用户重复询问子产品数量，也不再以显示问题阻断其他已明确工作。先前7行和空值的页面证据作为定位材料保留。

本轮真实迁移业务写入0；原105来源行、123范围、134明细及订单/项目/设备/事件/历史仍全字段保护。代码对照只允许三个全量分页调用及上述已批准设备产品筛选，其他汇总/数量/列保持原文。新证据equipment-scope-display-readonly-protection-20261010.json、equipment-scope-only-project198-20261010.txt、equipment-scope-only-project198-serials-20261010.txt及设备分页验收文件。40个订单行引用和11个旧发货引用尚未修补，不能将本次显示修复计为关联补正。

最终裁决后浏览器证据equipment-scope-only-browser-proof-20261010.json：992203060189两页32行全部项目数量与设备台数逐行一致、合计699；992203060198仅2行4/4/0/4和16/16/0/16且序列号页40台；992203060151仍9组，两个跨订单产品的SO058302、SO078894及4/4/0/4保持。分页、范围筛选3项定向测试和受影响文件diff检查通过；后端和数据结构本轮未变，无需重跑此前31项后端测试或重启服务。

## 2026-10-11引用补正与完整页面导航验收

上述“引用尚未修补”记录是实施前证据，现已完成限定纠正。40条原发货19字段及2条原订单行16字段在执行前再次核对一致；正式40条发货39列及40台设备53列完整保护。AST通过既有迁移入口补正40条order_line_id（D365、公司001、SO064298第1行24条，第2行16条），仅修复11个失效shipment_record_id；29个已正确设备引用保持。原line_no、其他业务字段、审计和版本均保持，订单、实施范围、软件及保修数据未变。一次NEW及两次REPLAY_COMPLETED通过，来源材料和旧回执未改写。实际结果在任务私有shipment-reference-approved-pilot-state-20261011.json。

真实页面另外发现“前往完整页面”未携带实施范围工作台要求的项目、项目版本和范围版本。复用父页面已核实的scopeContext传入三项，未增加伪造版本或放宽工作台守卫。重新打开详情并点击按钮，实际进入`/customer-asset/delivery-scopes?projectId=992203060198&projectVersion=19&projectScopeVersion=1`，两条范围分别数量4/16、发货4/16、未发货0，均false、ACTIVE、版本1、长春办事处。没有执行分配动作。成功DOM证据为shipment-reference-project198-scope-navigation-verified-browser-20261011.txt；较早不带verified的文件是失败定位证据。

当前设备清单仍仅展示实施范围2个产品，序列号保留40台；先前7组显示不是当前口径。本批引用缺口与导航已关闭，全历史稳定身份、持续刷新及生产迁移基线对齐仍未完成。
