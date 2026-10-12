# 保修来源契约与原表核对

状态：ADJUDICATED_EXISTING_MATERIAL / NEW_CAPTURE_DEPENDENCY_UNAVAILABLE。DPPMS-WARRANTY-SOURCE-044已裁决原表不变，按原材料继续推进。本报告记录实际配置、原视图定义及字段来源，不批准切换来源、重建视图或排除字段；新的完整采集仍需实际来源提供已登记契约。

当前既有同步连接2101561624635936769指向本机3306的dppms，任务沿此连接读取，不自动改用其他库。登记契约中的view_warranty为36列，但当前库不存在该对象；实际view_warranty_source是31列视图。旧仓库配置所指dppms_d365及其他同服务器库中有同名view_warranty，这是元数据核查结果，不能据此自行切换已配置同步连接或混合不同库的数据。

## 实际31列的来源

| 实际字段 | 原视图中的来源或计算 |
|---|---|
| id | fb_shipment_barcode.id |
| barcode | fb_shipment_barcode.barcode |
| comBarCode | fb_shipment_barcode.com_barcode |
| old_warrantyEndTime | fb_service.end_date优先，否则fb_shipment.warrantyEndTime减1天 |
| warrantyEndTime | 原视图按服务结束日期、发货保修开始日期和保修月数计算，保留原公式 |
| old_diff | NOW与old_warrantyEndTime的天差，随查询时间变化 |
| diff | NOW与warrantyEndTime的天差，随查询时间变化 |
| warrantyStartTime | fb_shipment_barcode.warrantyStartDate优先，否则fb_shipment.warrantyStartTime |
| old_warrantyStartTime | fb_shipment.warrantyStartTime |
| item | fb_shipment_barcode.item |
| describe_ | 以item关联fb_items.describe_ |
| itemName | 以item关联fb_items.itemname |
| gradeName | fb_warranty_grade.gradename或原视图默认文案 |
| gradeCode | fb_warranty_grade.gradecode或原视图默认53030001 |
| packdate | fb_shipment.packdate |
| contract_code | fb_contract.contract_code |
| contract_type | fb_contract.contract_type |
| contract_type_name | sys_state_or_type.resolveName，原stCode=01关系 |
| project_name | fb_contract.project_name |
| customer_name | fb_contract.customer_name |
| office_code | fb_office_relationship.officeCode优先，否则fb_contract.office_code |
| office_name | 对应department.ocrName |
| marketCode | fb_contract.marketCode |
| marketName | fb_contract.marketName |
| systemId | fb_contract.systemId |
| systemName | fb_contract.systemName |
| warranty | fb_contract.warranty |
| warrantyMonth | 正的fb_shipment_barcode.warrantyMonth优先，否则合同warranty乘12 |
| barcode2 | fb_shipment_barcode.barcode2 |
| item2 | fb_shipment_barcode.item2 |
| syncTime | fb_shipment_barcode.syncTime |

原视图还有合同类型0/11、非空主序列号及RMA编号为空等筛选条件。它的行集和日期计算有自身含义，不能将另一对象的返回行直接替代。old_diff/diff是查询时派生值，不应据某次采集值认定永久的当前保内/保外状态；正式保修整表处理仍待用户裁决。

## 登记契约多出的5项

| 登记字段 | 当前核对结果 | 尚未确定的处理 |
|---|---|---|
| newId | 当前dppms所有对象的字段元数据均未发现此字段；不能直接假定等于id | 确认原36列对象及技术身份规则，不自造原值 |
| agentName | 实际31列视图及fb_contract均没有；项目SMS属性表有同名字段 | 同名不证明合同保修关联，需追查真实来源关系 |
| finalCustomerName | 实际31列视图及fb_contract均没有；项目属性/展示对象中存在 | 需核实合同、项目及最终用户关系，不能按名称或任意项目取值 |
| itemName2 | 原31列有item2，fb_items有itemname，原保修展示依赖该返回字段 | 产品编码关联是候选来源，原36列定义和精确关系仍待核实 |
| describe_2 | 原31列有item2，fb_items有describe_ | 同上，不能伪称原31列已有此列 |

实际view_warranty_info_4_ts定义直接引用缺失的view_warranty；读取字段报1356。view_project_warranty_4_itr定义依赖缺失的view_warranty_with_presales，也报1356。已读取定义区分明确依赖缺失与一般权限错误，未自行创建原视图或修复来源环境。

完整来源采集保持失败关闭，不跳过5项、不覆盖不可变快照。本轮已批准真实产品原表的独立核对及10项目空结果验收不依赖此错误，但不能作为完整来源契约通过的证据。

只读证据：任务私有warranty-dependency-ddl-readonly-20261010.json保存实际视图定义与表列；warranty-five-field-metadata-readonly-20261010.json保存字段候选及同名对象所在库。原库及目标业务数据均未写入。用户已收到044问题：指定真实来源，或按实际原表追查5项后重新提交保修整表方案；答复前不执行依赖此选择的变更。

2026-10-10用户最新裁决：“原表不变，按原来的材料证明继续推进”。本报告的31列核查仅作为当前连接运行差异，不替换已批准的原36列契约或旧不可变材料，不切换来源库。保修各正式实体整表方案继续以原材料及既有实体复核；新增完整采集仍须实际连接满足原契约，不隐瞒当前缺表。
