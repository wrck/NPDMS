# 保修等级及合同类型原始目录核查

状态：READ_ONLY / BLOCKED_BY_SPEC。此核查不新增表、列、字典数据或扩展属性，不排除原字段，也不改变原36列view_warranty契约。仅补充原始目录证据，不能作为字典承接方案的批准。

原材料gradeName/gradeCode依赖fb_warranty_grade；contract_type_name依赖sys_state_or_type的stCode=01。仅核对旧接口返回名称不足以证明全部原表字段承接。本次通过既有配置来源连接读取这两张原表完整列和记录，未切换来源库。

## fb_warranty_grade（7列、12条）

| 原字段 | 类型/可空 | 当前核查结论 |
|---|---|---|
| id | INT/否 | 旧目录技术ID；应由既有迁移绑定保存，不冒充等级业务码 |
| gradecode | VARCHAR(25)/是 | 等级业务编码；保修实体已有warranty_grade，目录自身承接待规划 |
| gradename | VARCHAR(125)/是 | 原等级名称；不能塞入保修备注或使用相似标签代替 |
| gradestatus | INT/是 | 原启停；须追查旧状态含义再提交转换，不凭0/1习惯推断 |
| sort | INT/是 | 原排序；不得按ID重新编码 |
| effectiveFrom | DATETIME/是 | 原有效期开始；不当创建时间，不能未经裁决丢弃 |
| effectiveTo | DATETIME/是 | 原有效期结束；不据其直接关闭设备保修 |

本批原gradeCode=53030001，对应原目录名称“DPtech 维保服务,基本维保 5x10xNBD发出”。该名称是维保等级标签，不能根据合同年数猜配。

## sys_state_or_type（7列、30条）

| 原字段 | 类型/可空 | 当前核查结论 |
|---|---|---|
| id | INT/否 | 技术ID归来源绑定，不作为字典业务编码 |
| stCode | VARCHAR(25)/是 | 字典分类原码；01为合同类型，不能把全部30条混为保修等级 |
| stName | VARCHAR(25)/是 | 原分类名称 |
| resolveCode | VARCHAR(10)/是 | 分类内业务码；合同引用contract_type已有正式承接 |
| resolveName | VARCHAR(25)/是 | 原业务标签；应精确关联同一分类与编码，不能按名称猜配 |
| validity | INT/是 | 原启停含义仍待旧代码核实，不能自行反转 |
| remark | VARCHAR(100)/是 | 目录条目自身备注，不承接其他原字段 |

例如合同类型0为销售合同、1为借货合同、2为报备项目。原表还有其他分类，不能仅凭这三条推断其余记录或权限。

## 现成承接点检查及下一步

正式system_dict_type/system_dict_data现有查询中，类型含warranty、contract与type组合、service与grade组合的记录均为0；元数据亦未发现名字含warranty_grade/service_grade的其他表。这是按上述条件查询的结果，不声称已排除任何不同命名的业务目录。还须结合正式字典消费者完整核对，才能决定复用哪种分类。

后续先核对现有字典全部字段、旧状态转换及消费者，按现有结构提出整表映射；原有效期等无同义字段的处理由用户裁决，不自行新增物理列、用扩展替代或排除。原12条与30条已留完整只读证据。此项未批准任何迁移写入。

证据：warranty-dictionary-originals-readonly-20261010.json，writes=0；与既有原36列材料及源视图依赖定义对照。实际表查询和字段定义均来自本任务配置连接。
