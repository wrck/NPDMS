# 项目产品配置层级整表裁决

状态：WHOLE_TABLE_APPROVED / PILOT_VERIFIED。用户已回复“19列及上述处理整表按建议”。本报告保留完整原表、旧实现和目标核对，并按本次整表裁决实施。产品配置是附加项目关联清单，保存原配置层级与数量，不生成实施范围、当前设备关系、分配或交付完成事实。

原表pm_project_product_config_level_info_from_crm全部11列、736条；本任务先前创建的proj_project_product_configuration为22列，裁决前未获整表批准，全表为空。已批准最终19列，新增物理列0：复用18列、source_project_code更名project_code，撤回3个技术镜像；item_group从TEXT纠正为原INT，data_source从TEXT纠正为VARCHAR(25)。两项类型纠正沿本次整表裁决实施。

## 最终19列

| 字段 | 类型/可空 | 承接与处理 |
|---|---|---|
| id | BIGINT/否 | 正式实体ID；原表没有ID，不伪造来源ID |
| tenant_id | BIGINT/否 | 正式迁移任务租户 |
| project_id | BIGINT/否 | 精确关联正式项目；关联不明确时阻断 |
| project_code | TEXT/是 | 复用更名source_project_code，保存原清单自身projectCode |
| execution_no | TEXT/是 | orderExecNumber；本来源736条均为空字符串，原样保存，不猜执行单 |
| item_group | INT/是 | itemGroup；纠正现TEXT类型，原组号不转为角色或产品分类 |
| product_code | TEXT/是 | itemCode原产品编码 |
| parent_code | TEXT/是 | parentCode原父产品编码，NULL与空字符串原样区分 |
| quantity | DECIMAL(24,6)/是 | quantity原配置数量，不作为当前订单分配 |
| bom_path | TEXT/是 | bomPaths原完整路径；不截断，不改写路径 |
| product_model | TEXT/是 | itemModel原型号 |
| product_description | TEXT/是 | itemDesc原描述 |
| level | INT/是 | 原level；不设计固定层级上限 |
| data_source | VARCHAR(25)/是 | 原dataSource，纠正现TEXT类型；736条真实值均CRM |
| creator | VARCHAR(64)/否 | 原表无审计，建议实际迁入操作者 |
| create_time | DATETIME(3)/否 | 原表无创建日期，建议实际迁入时间 |
| updater | VARCHAR(64)/否 | 首次同实际迁入操作者，后续沿正式Owner |
| update_time | DATETIME(3)/否 | 首次同实际迁入时间，保留毫秒精度 |
| deleted | BIT(1)/否 | 正式逻辑删除；不因来源刷新自行删除 |

## 全部11原字段

| 原字段 | 原类型/可空 | 正式字段/处理 |
|---|---|---|
| projectCode | VARCHAR(100)/是 | project_code |
| orderExecNumber | VARCHAR(255)/是 | execution_no |
| itemGroup | INT/是 | item_group |
| itemCode | VARCHAR(100)/是 | product_code |
| parentCode | VARCHAR(1000)/是 | parent_code |
| quantity | INT/是 | quantity |
| bomPaths | VARCHAR(1000)/是 | bom_path |
| itemModel | VARCHAR(100)/是 | product_model |
| itemDesc | VARCHAR(500)/是 | product_description |
| level | INT/是 | level |
| dataSource | VARCHAR(25)/是 | data_source |

11项全部承接，不排除原业务字段。除parentCode仅243条非NULL以外，其余各列736条均非NULL。数量均为正，未发现完整业务行重复或同项目编码、执行单、组号、BOM路径的重复节点；这些统计不授权全历史自动去重或持续刷新。

## 当前22列逐项处理

最终19列覆盖当前18列复用及source_project_code更名。source_system、source_table、source_record_key撤回：技术身份、来源输入校验和、批次及绑定由既有INT/PLT承接，不作为清单业务属性。先核查全表和调用依赖，再清理物理技术镜像及当前SQL定义；原库、不可变来源证据与旧回执保留。

## 层级事实与旧返回字段

旧DAO将传入项目编码按“-”拆分取第一段；SQL以projectCode筛选最大orderExecNumber，并按组号、BOM路径排序。当前原表77个项目编码，每个仅一个执行单值且全部为空字符串，旧最大执行单筛选在这次全表中仍返回736条。首次迁入建议完整承接所选项目的原行；本批10项目按完整编码及旧拆分编码均为0条，不补造数据。

旧SQL额外返回parentBomPaths、parentGroupPaths、groupPaths三项计算值，它们不是原表列。建议查询时从正式bom_path、level、item_group计算，不新增物理列。组内BOM路径为节点关系依据，parent_code仍完整保存产品编码；315个非根节点中83个parentCode为NULL，不能仅凭该字段构造完整树。

原层级1、2、3分别421、290、25条，原路径深度与level一致；10个非根节点在同项目编码、执行单、组号下没有对应父路径。建议原样保留这些节点与路径，在正式查询中列明缺失父节点，不补造父产品或改写层级。具体树展示与扩大迁入遇到的关联冲突仍须按实际记录处理，不能从本次三层统计推导固定树模型。

原表没有主键、时间或顺序。首次采集可使用完整原值及出现序号区分输入行；该序号不能作为跨刷新稳定身份。持续刷新、删除及同一基础编码关联多个项目的规则另行裁决；未明确前不启动持续刷新，也不任选目标项目。

## 建议实施及验证边界

整表获批准后，通过迁移/同步功能与PROJ Owner承接，核验全部业务值和正式审计基线后才清理技术列；有后续业务修改时阻断。普通清单查询继续要求租户及项目权限。原表没有审计字段的首次迁入采用实际操作者和时间，不把当前时间冒充来源历史日期。

旧实现：D:/EclipseWorkspace/Parctice/PMS/PMS-struts/src/com/dp/plat/dao/ProjectDaoImpl.java:1161；config-ibaits/sql-map-project-config.xml:4187；sql-map-refresh-data-crm-config.xml:518。完整11原列、22目标列、全部736原行及层级统计分别见任务私有lease-config-whole-tables-readonly-20261010.json和config-hierarchy-original-readonly-20261010.json，writes=0。完整来源采集另受DPPMS-WARRANTY-SOURCE-044阻断，本表只读核查不代表全源契约已通过。

整表19列、两项类型纠正、原数量/层级及缺失父路径保留处理已获批准。已完成结构整理及本批空来源迁移/同步验收，验证边界见下节。

## 2026-10-10 已批准方案验收

实际表已由22列整理为19列，无新增物理表/列；item_group为INT、data_source为VARCHAR(25)，技术绑定使用既有INT/PLT。全表为空、依赖及绑定为空后清理三列；现有业务、原始证据和Flyway执行历史保护核对无变化。

本批10项目原配置行0条，10次预览、10个正式空来源迁移回执、10次REPLAY_COMPLETED及正式查询通过。真实浏览器选择配置页签和刷新通过，原组号/产品/父产品/路径栏目独立展示且0条与来源一致。非空层级浏览器验证未执行；全部736行尚未迁入。

受影响后端73项回归、16项真实MySQL临时表检查、31项Python来源检查及完整打包通过，本次前端文件lint通过。MySQL覆盖完整字段、事务回滚、NULL安全父路径查询、空权限/空路径集合、审计及租户/项目保护；缺失父节点保留原事实，不补造。扩大全历史及持续刷新身份仍待裁决，当前缺少原view_warranty的完整采集依赖保持阻断。证据与租赁表共用verified-associated-lists-approved-schema/pilot-20261010.json，页面证据为verified-product-configuration-approved-browser-20261010.txt。
