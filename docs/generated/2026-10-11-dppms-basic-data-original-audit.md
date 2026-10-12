# 来源基础数据与现有系统字典只读核查

状态：SOURCE_AND_EXISTING_STRUCTURE_VERIFIED / WHOLE_TABLE_PLAN_PENDING。基础数据领域已获准规划正式承接，本文件只核实来源及既有结构；未新增表列、扩展、字典类型、字典项、状态或当前权限。

## 实际来源与语义差异

fnd_basic_data完整12列、532条，属于80个dataTypeCode；fnd_basic_data_type完整10列、31条。49个项分组在原类型表没有定义，不能只按类型表31条采集，也不能凭代码字符串猜造中文类型名称。原全部分组＋键值组合无重复；实际键值、名称最大各51字符，属性最大79字符，均未超过现有系统字典相应100字符列。

原类型表status的原注释为“是否需要放在前台管理”。旧query_basic_data_type确实用STATUS=1与有效期筛选管理列表；它不是新system_dict_type的“0正常、1停用”，不得直接把相同整数映射为启停。

原项本身没有status列，旧业务查询按effectiveFrom/effectiveTo筛选，部分Map查询只取effectiveTo为空。不同查询的筛选不能替代统一的有效期承接规则。原sortId注释说“数值越大越在前”，但已核实旧查询实际ORDER BY sortId升序，新字典也是升序；未经进一步裁决不自动反转排序。

109条basicDataAttri1非NULL，实际包含旧页面入口、指标单位、分类关联和筛选值。旧query_basicdata_by_attri及query_basic_data_for_map_with_sub确实消费该属性；它具有业务含义，不能塞备注、颜色或CSS字段，也不能一律当作预留空值排除。532条中129条createTime为空，updateTime/updateBy全部NULL，原有效期76条开始为空、7条结束非空；现字典审计日期非空、无同义有效期列，尚无获准补值或缺项排除规则。

## 全12项字段的承接候选及明确缺项

| 原字段 | 既有同义字段/待明确 |
|---|---|
| id | 迁移来源绑定；不作为字典业务编码 |
| dataTypeCode | dict_type，但必须逐组明确正式类型编码与已有分类的对应，不能因同名猜配 |
| basicDataId | value；1条NULL与正式非空冲突，不能造值 |
| basicDataName | label；1条NULL与正式非空冲突，不能造名称 |
| basicDataAttri1 | 正式字典没有同义属性列，109条非空，承接方式待裁决 |
| sortId | sort，原33条NULL与正式非空冲突，排序含义须明确，不自动逆序 |
| createTime | create_time，129条NULL待处理；不得以本轮时间覆盖原历史非空时间 |
| createBy | creator，可核实账号按已裁决来源绑定关联，原NULL不自动填写admin |
| updateTime | update_time，全部NULL，不能擅自扩大其他表的“原创建时间初始化”裁决 |
| updateBy | updater，全部NULL，不自动填admin |
| effectiveFrom | 原有效期开始，现有字典无同义列 |
| effectiveTo | 原有效期结束，不能仅靠启停状态代替原日期承接 |

## 原类型表全部10列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | int(11) | NO |
| dataTypeCode | varchar(45) | YES |
| dataTypeName | varchar(45) | YES |
| status | int(11) | YES |
| createTime | datetime | YES |
| createBy | varchar(45) | YES |
| updateTime | datetime | YES |
| updateBy | varchar(45) | YES |
| effectiveFrom | datetime | YES |
| effectiveTo | datetime | YES |

类型id通过来源绑定；dataTypeCode/type、dataTypeName/name、四项审计具有候选同义字段。status为前台管理标记、effectiveFrom/effectiveTo为区间，均不是现有启停或删除字段，缺项处理等待整表裁决，不能丢弃。没有原字段被本文件批准排除。

## 现有system_dict_type全部11列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| name | varchar(100) | NO |
| type | varchar(100) | NO |
| status | tinyint | NO |
| remark | varchar(500) | YES |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |
| deleted_time | datetime | YES |

## 现有system_dict_data全部14列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| sort | int | NO |
| label | varchar(100) | NO |
| value | varchar(100) | NO |
| dict_type | varchar(100) | NO |
| status | tinyint | NO |
| color_type | varchar(100) | YES |
| css_class | varchar(100) | YES |
| remark | varchar(500) | YES |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |

这两张现有系统字典表均无tenant_id；字典是当前全局模型，不能按单一项目关联擅自修改全局已有类型及值，亦不新增租户列。当前DictTypeService/DictDataService校验类型、名称及类型＋键值唯一，新增项要求有效类型。它们的正常保存会使用现平台审计行为，不等于已有历史迁移Owner；后续历史写入仍须走迁移/同步及SYSTEM明确Owner，不跨模块直接写表。

## 原项字段NULL数量

| 字段 | NULL条数 |
|---|---:|
| id | 0 |
| dataTypeCode | 0 |
| basicDataId | 1 |
| basicDataName | 1 |
| basicDataAttri1 | 423 |
| sortId | 33 |
| createTime | 129 |
| createBy | 481 |
| updateTime | 532 |
| updateBy | 532 |
| effectiveFrom | 76 |
| effectiveTo | 525 |

## 原类型表无定义的49个项分组

| 原dataTypeCode | 项数 |
|---|---:|
| 19 | 3 |
| 20 | 3 |
| 22 | 9 |
| 23 | 10 |
| 24 | 3 |
| 25 | 4 |
| 26 | 4 |
| 28 | 5 |
| 30 | 3 |
| 31 | 7 |
| 32 | 7 |
| 33 | 4 |
| 34 | 3 |
| afProjectMember | 9 |
| afProjectTaskDeliverFileType | 2 |
| afProjectTaskType | 20 |
| af_dailyReportCategory | 5 |
| af_dailyReportProjectPriority | 3 |
| af_dailyReportProjectType | 4 |
| af_dailyReportSubCategory | 15 |
| af_dailyReportType | 2 |
| afss_projectState | 6 |
| afxx_projectState | 6 |
| assetTypes | 9 |
| crmReferenceEvent | 3 |
| d365DeliverablesReq | 3 |
| dispatchServiceType | 2 |
| dispatchState | 3 |
| dispatchType | 2 |
| dispatchWorkContentUnit | 3 |
| frameTypes | 2 |
| industryTypes | 40 |
| leakLevels | 3 |
| majorProjectLevel | 4 |
| mitigationActionType | 5 |
| prepaidRules | 3 |
| processStatus | 8 |
| projectServiceDeliveryType | 2 |
| projectTypes | 3 |
| projectWarrantyCallback_phoneAnswerState | 4 |
| relatedSceneType | 10 |
| sofeVersionAffectedType | 3 |
| solutionActionType | 5 |
| subcontractCbState | 4 |
| subcontractDeliverState | 3 |
| subcontractState | 11 |
| subcontractTax | 10 |
| subcontractWorkFlowState | 9 |
| trackStatus | 3 |

后续需以旧代码、原业务消费和正式字典定义逐组映射；回访/分包等未获准启动的业务域不能仅因其字典存在而激活流程。已批准来源项目类型10等具体映射继续沿既有裁决，不能扩展为所有旧状态同值有效。

证据：任务私有basic-data-original-schema-readonly-20261011.json，含全部12＋10来源列及原行、现11＋14列。旧PMS-struts/config-ibaits/sql-map-admin-config.xml:793、825、962、1044，BasicDataDaoImpl；现SYSTEM DictTypeServiceImpl、DictDataServiceImpl。schemaWrites=0、businessWrites=0，只读审查不代表正式字典承接及浏览器验收完成。
