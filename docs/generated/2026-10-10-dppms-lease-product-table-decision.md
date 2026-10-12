# 项目租赁产品整表裁决

状态：WHOLE_TABLE_APPROVED / PILOT_VERIFIED。用户已裁决：productFirstName→productCategory、productName→productType，产品明细使用product_code/product_model/product_name；其余沿19列方案。按该裁决整理本表及迁移/同步实现。业务主体沿用户已确认的附加项目关联子表；不并入实施范围或产品目录，不生成租赁生效、交付、回款或分配完成事实。

原表pm_project_product_lease_line_from_crm有11列、454条；当前proj_project_lease_product是本任务先前创建的22列表，裁决前整表未批准，全表为空。已批准整理为下述正式19列，新增物理列0：复用18列并更名project_code，撤回3个技术镜像；其中lease_duration从TEXT纠正为原租赁月数对应的DECIMAL(16,2)，data_source从TEXT纠正为原编码对应的VARCHAR(25)。两项类型纠正沿整表方案实施。

## 最终19列

| 字段 | 类型/可空 | 承接与处理 |
|---|---|---|
| id | BIGINT/否 | 正式实体ID；原表无自增ID，不伪造原ID |
| tenant_id | BIGINT/否 | 来自正式迁移任务租户 |
| project_id | BIGINT/否 | 精确关联正式项目；未明确关联时阻断，不按名称挂靠 |
| project_code | TEXT/是 | source_project_code更名，承接原清单自身项目编码 |
| execution_no | TEXT/是 | 原orderExecNumber，保存完整版本文字 |
| product_category | TEXT/是 | 原productFirstName产品类型原文，不猜分类字典 |
| product_type | TEXT/是 | 原productName产品类型，不覆盖当前目录 |
| product_code | TEXT/是 | 原productSubCode产品编码 |
| product_model | TEXT/是 | 原productSubModel型号；NULL与空字符串区分 |
| product_name | TEXT/是 | 原productSubName描述；NULL与空字符串区分 |
| quantity | DECIMAL(24,6)/是 | 原num订单数量；保存清单自身值，不形成当前订单分配 |
| memo | TEXT/是 | 原memo说明；非空190条，NULL与空字符串区分，扩大时校验长度、不截断 |
| lease_duration | DECIMAL(16,2)/是 | 原leaseDuration，原表明确注释为“租赁月数”，纠正现TEXT类型，不猜其他单位 |
| data_source | VARCHAR(25)/是 | 原dataSource，纠正现TEXT类型；454条真实值均CRM，不以技术来源系统代替 |
| creator | VARCHAR(64)/否 | 原表无审计，建议实际迁入操作者 |
| create_time | DATETIME(3)/否 | 原表无创建日期，建议实际迁入时间 |
| updater | VARCHAR(64)/否 | 初建同实际迁入操作者，后续正式更新沿Owner |
| update_time | DATETIME(3)/否 | 初建同实际迁入时间，保持毫秒精度 |
| deleted | BIT(1)/否 | 正式逻辑删除；不因来源刷新自动删除 |

## 全部11原字段

| 原字段 | 原类型/可空 | 正式字段/排除原因 |
|---|---|---|
| projectCode | VARCHAR(255)/否 | project_code |
| orderExecNumber | VARCHAR(255)/是 | execution_no |
| productFirstName | VARCHAR(255)/否 | product_category |
| productName | VARCHAR(128)/否 | product_type |
| productSubCode | VARCHAR(255)/否 | product_code |
| productSubModel | VARCHAR(255)/是 | product_model |
| productSubName | VARCHAR(255)/是 | product_name |
| num | INT/否 | quantity |
| memo | MEDIUMTEXT/是 | memo；超出正式字段容量时阻断、不截断 |
| leaseDuration | DECIMAL(16,2)/是 | lease_duration，月数及两位小数原样 |
| dataSource | VARCHAR(25)/是 | data_source |

11项均有正式业务承接点，不排除任何原业务字段。除memo外，本次各列454条均非NULL；数量全部为正。扩大历史遇0、负数、超长或其他异常值时重新登记，不以本次正数量统计猜测后续数据。

## 当前22列逐项处理

上表19列覆盖当前18列复用和source_project_code更名。剩余source_system、source_table、source_record_key三列撤回：它们是本任务加入的技术镜像，来源系统、表、输入校验和、批次和无键原表的逐行身份由既有INT/PLT绑定及回执承接，不作为业务属性。先确认全表及调用依赖，再清理物理镜像及相应SQL定义；不修改既有不可变来源证据或旧回执。

## 原表与旧页面差异及待裁决处理

旧ProjectDaoImpl先将传入项目编码按“-”拆分取第一段，再调用SQL；旧SQL按projectCode查最大orderExecNumber，只展示对应行。原表全454条存在25组完全相同业务行、多出31条。建议完整承接所选项目对应的所有原执行单行及重复行，不以旧页面筛选后的结果替代原表；重复行各自保存正式实体及迁移绑定，不自行去重。首次采集内使用完整原值和出现序号区分无键行；不能将出现序号当作跨刷新稳定身份，持续刷新仍待独立裁决。

本批10项目，按原完整编码和旧DAO拆分编码分别查原表，均为0条；不制造示例业务行。该编码预处理为已核实的旧查询行为，本报告不据此扩大全历史关联；遇同一基础编码关联多个旧项目时单列裁决，不任选项目或猜测数量分配。

建议原表没有审计字段的首次迁入用实际人员/时间，后续重放核对完整目标业务及审计基线，有变化阻断。与当前产品目录、实施范围、分配额度及业务状态保持各自Owner职责，不通过读取或迁入清单触发这些动作。

旧实现：D:/EclipseWorkspace/Parctice/PMS/PMS-struts/src/com/dp/plat/dao/ProjectDaoImpl.java:1143、ProjectAction.java:901；config-ibaits/sql-map-project-config.xml:4156；sql-map-refresh-data-crm-config.xml:473、481。完整11原列、22目标列、非空/重复统计及10项目核对见任务私有lease-config-whole-tables-readonly-20261010.json，writes=0。

整表19列、两项类型纠正及全部原值处理已获裁决。454条原productName与productSubName全不同，按用户明确字段映射独立承接类型和实际产品名称，不使用候选product_group_name。实际结构与本批迁移验收已通过，验证边界见下节。044已裁决保留原36列来源及原有材料继续推进；当前配置来源缺少view_warranty，新的完整采集仍受该实际依赖限制，不替换为31列视图。

## 2026-10-10 已批准方案验收

实际表已整理为19列，新增物理表/列均0，原22列表为空且数据库依赖/绑定为空后才清理三列；现有业务、来源头/快照/记录及Flyway执行历史逐项保护核对无变化。59282已切换本任务新运行包，原工作目录和其他服务保持。

本批10项目原租赁行0条：10次正式迁移预览、10个空来源命令回执及10次REPLAY_COMPLETED通过，正式分页均0条，不制造源数据。真实浏览器重开项目、选择租赁页签并刷新，产品分类、类型、编码、型号、名称分别显示正确栏目，空结果与正式查询一致；非空详情未在本批浏览器验证。

与配置及受影响旧能力合计73项后端回归、16项真实MySQL临时表检查、31项来源采集Python检查通过，完整后端打包、前端本次文件lint通过。MySQL检查覆盖11项原值、19列审计、租赁小数、重复行、NULL/空串、租户和项目边界、事务回滚、后续变更及刷新阻断；临时表不写正式业务表。全454条尚未迁入，跨刷新身份和共享基础编码规则仍待裁决，生产Flyway基线对齐尚未完成。

证据：任务私有associated-lists-approved-pre-schema-20261010.json、verified-associated-lists-approved-schema-20261010.json、verified-associated-lists-approved-pilot-20261010.json、verified-lease-product-approved-browser-20261010.txt及对应构建/检查日志。
