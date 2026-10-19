# P01 原表逐字段承载清单

合并组件：P01, P02, P03。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| ehr_company.compID | int(11) / NO | INT来源绑定 | 原公司身份；不直接套用正式id。；当前配置/Owner | P01 |
| ehr_company.compCode | varchar(10) / YES | system_company.code | 配置TRIM；以已保存编码语义承接，不擅自套DPPMS四码转换。；当前配置/Owner | P01 |
| ehr_company.compName | varchar(100) / YES | system_company.name | 配置TRIM。；当前配置/Owner | P01 |
| ehr_company.compAbbr | varchar(100) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.adminID | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.compGrade | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.compType | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.compArea | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.effectDate | datetime / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.lawyer | varchar(50) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.address | varchar(200) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.regAddress | varchar(200) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.tel | varchar(50) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.fax | varchar(50) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.postCode | varchar(50) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.webSite | varchar(100) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.isDisabled | bit(1) / YES | system_company.status | 按当前完整ENUM映射；未定义值阻断。；当前配置/Owner | P01 |
| ehr_company.disabledDate | datetime / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_company.remark | varchar(500) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depID | int(11) / NO | INT来源绑定 | 原部门身份。；当前配置/Owner | P01 |
| ehr_department.depCode | varchar(20) / YES | system_dept.code | 配置TRIM。；当前配置/Owner | P01 |
| ehr_department.depName | varchar(100) / YES | system_dept.name | 配置TRIM。；当前配置/Owner | P01 |
| ehr_department.depAbbr | varchar(100) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.compID | int(11) / YES | 公司来源关联核验 | 作为companyKey验证来源公司存在；system_dept没有公司外键列，不声称落库。；当前配置/Owner | P01 |
| ehr_department.adminID | int(11) / YES | system_dept.parent_id | 先作为parentKey，经来源/正式绑定解析；0或空为根。；当前配置/Owner | P01 |
| ehr_department.depGrade | int(11) / YES | system_dept.sort；来源筛选 | 本任务LONG映射sort并按depGrade<=2筛选；不是固定树层级设计。；当前配置/Owner | P01 |
| ehr_department.depType | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depProperty | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCost | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.director | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.director2 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depEmp | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depNum | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.effectDate | datetime / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.xOrder | varchar(20) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.isDisabled | bit(1) / YES | system_dept.status | 按当前完整ENUM转换。；当前配置/Owner | P01 |
| ehr_department.disabledDate | datetime / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.remark | varchar(500) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCustom1 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCustom2 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCustom3 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCustom4 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| ehr_department.depCustom5 | int(11) / YES | P01未采集 | 现P01仅采集配置中列；本字段无P01映射。P03八码的排除裁决不自动泛化为全EHR永久排除。；本任务未采集 | P01 |
| fnd_department.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P02 |
| fnd_department.departmentNum | varchar(20) / NO | system_dept.code；system_dept.parent_id | 16开头父160000，31开头父310000，280700父280000，999999根；311202—311210显式转162102—162110，不截断猜配；业务字段/正式关系 | P02 |
| fnd_department.departmentName | varchar(20) / NO | system_dept.name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P02 |
| fnd_department.isparam | int(11) / YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性；已批准排除 | P02 |
| fnd_department.status | int(11) / NO | system_dept.status | 来源1→启用0、0→停用1；指定两码按EHR停用；已有目录不覆盖；业务字段/正式关系 | P02 |
| fnd_department.createTime | datetime / YES | system_dept.create_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P02 |
| fnd_department.effectiveFrom | datetime / YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性；已批准排除 | P02 |
| fnd_department.effectiveTo | datetime / YES | 不迁入 | 用户保持system_dept结构，明确不迁入缺少同义字段的三属性；已批准排除 | P02 |
| fnd_department.updateTime | datetime / YES | system_dept.update_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P02 |
| ehr_department.depID | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P03 |
| ehr_department.depCode | varchar(20) / YES | system_dept.code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P03 |
| ehr_department.depName | varchar(100) / YES | system_dept.name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P03 |
| ehr_department.depAbbr | varchar(100) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.compID | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.adminID | int(11) / YES | system_dept.parent_id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P03 |
| ehr_department.depGrade | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depType | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depProperty | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL；无可承接业务值，不新增字段。全历史出现非空需重新裁决。；业务字段/正式关系 | P03 |
| ehr_department.depCost | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.director | int(11) / YES | system_dept.leader_user_id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P03 |
| ehr_department.director2 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depEmp | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depNum | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL，原部门人数无业务值。；业务字段/正式关系 | P03 |
| ehr_department.effectDate | datetime / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.xOrder | varchar(20) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL，不编造排序；正式sort建议0。；业务字段/正式关系 | P03 |
| ehr_department.isDisabled | bit(1) / YES | system_dept.status | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P03 |
| ehr_department.disabledDate | datetime / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.remark | varchar(500) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL，既有正式表无备注列，不新增。；业务字段/正式关系 | P03 |
| ehr_department.depCustom1 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL；无业务值。；业务字段/正式关系 | P03 |
| ehr_department.depCustom2 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depCustom3 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL；无业务值。；业务字段/正式关系 | P03 |
| ehr_department.depCustom4 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】既有16列无同义字段，用户不增列且不用扩展；已获用户确认不迁入；原因逐列登记。原值只保留来源核对证据，明确不计为业务承接。；业务字段/正式关系 | P03 |
| ehr_department.depCustom5 | int(11) / YES | 不迁入正式业务字段 | 原清单该格为非空统计，已校正为处理结果；【已批准不迁入】本8项全NULL；无业务值。；业务字段/正式关系 | P03 |
