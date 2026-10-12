# 项目人员原字段与现有业务实体只读核查

状态：SOURCE_AND_EXISTING_STRUCTURE_VERIFIED / AUTHORIZATION_BOUNDARY_APPROVED / WHOLE_TABLE_PLAN_PENDING。本文件不批准新增表列、扩展属性、状态或成员写入。2026-10-11用户批准有效成员经正式Owner资格核验承接项目授权，过期区间保留历史，当前成员或主责冲突另交裁决；结构仍须整表确认。

## 原表及本批数据

pm_project_member实际15列、435342条；本批10项目为70条：销售10、服务经理10、项目经理41、最终客户人员9。现有proj_project_member_assignment为29列、本批7条；CUS项目联系人实际24列、本批3条，主档19列、历史10列。所有现存事实保持。

按完整登录名（不按姓名猜配）核对，53条成员记录有正式账号候选，17条没有：8条销售涉及x04362、l01663、z02371、j03309、c03143、l05207六个登录名；9条最终客户人员原memberCode为空字符串。六个销售账号在原fnd_user_info和t_user也没有记录；尚不据此判定EHR没有人员。候选匹配不等于已迁入或授权。

## 全15原字段逐项登记

| 原字段 | 既有字段/明确缺项 |
|---|---|
| id | INT/PLT来源绑定；不直接作为正式记录主键 |
| projectId | 成员区间project_id或CUS项目联系人project_id，须按真实主体及原项目绑定关联 |
| projectType | 本批全部10；可核实同义的项目类型通过正式项目实体关联，全量不同主体不得只按数字ID挂靠 |
| memberRole | 成员member_role具备角色属性，但当前四类正式角色与全部来源分类不一致；最终客户人员属于联系人，不是平台账号角色 |
| memberCode | 可核实账号关联user_id；无账号时没有可用的非账号编码承接点，不写employee_no、不伪造账号 |
| memberName | 成员member_name或联系人name；历史姓名不能被当前用户姓名自动覆盖 |
| phoneNum | 联系人mobile/phone具备联系方式字段；成员区间无同义列，不能把历史值覆盖到system_users或写备注 |
| email | 联系人email具备字段；成员区间无同义列，当前用户邮箱不能代表旧区间邮箱 |
| fromFlag | 旧1来自项目信息，2来自手工成员资料；不是内部/外部账号类型；现有成员/联系人没有同义登记来源字段 |
| createTime | 正式create_time；全量54条NULL与非空正式约束冲突，未擅自造日期 |
| createBy | 正式creator；原NULL及未匹配操作者处理仍须整表明确 |
| updateTime | 正式update_time；全量265607条NULL，尚未把其他表限定规则自动扩大到本表 |
| updateBy | 正式updater；原NULL及人员引用保持待明确 |
| effectiveFrom | 成员effective_from直接具备同义列；联系人现有结构没有该区间列 |
| effectiveTo | 成员effective_to直接具备同义列；联系人启停不是同义的历史结束日期 |

## 既有成员区间表全部29列

| 字段 | 实际类型 | 可空 |
|---|---|---|
| id | bigint | NO |
| project_id | bigint | NO |
| user_id | bigint | NO |
| employee_no | varchar(64) | YES |
| member_name | varchar(128) | YES |
| company_id | bigint | YES |
| company_code | varchar(64) | YES |
| company_name | varchar(255) | YES |
| department_id | bigint | YES |
| department_code | varchar(64) | YES |
| department_name | varchar(255) | YES |
| member_role | varchar(32) | NO |
| assignment_type | varchar(32) | YES |
| site_id | bigint | YES |
| responsibility | varchar(500) | YES |
| change_reason | varchar(500) | YES |
| effective_from | datetime(3) | YES |
| effective_to | datetime(3) | YES |
| status | varchar(32) | NO |
| version | int unsigned | NO |
| creator | varchar(64) | YES |
| create_time | datetime | NO |
| updater | varchar(64) | YES |
| update_time | datetime | NO |
| deleted | bit(1) | NO |
| deleted_time | datetime | YES |
| tenant_id | bigint | NO |
| remark | varchar(500) | YES |
| end_reason | varchar(500) | YES |

## 已核实的业务和约束边界

正常OrdinaryProjectMemberService和ProjectManagerMemberApplicationService会核验有效账号、系统角色、项目权限、ACTIVE项目及版本，开启/关闭成员区间，修改项目经理及指派状态，并发布规则重评事件。不能把保存旧资料等同于运行这些动作。成员user_id非空，唯一键为租户＋项目＋用户＋角色＋effective_from；原数据是否重复及区间重叠需另核实，不关闭约束来吞入记录。

当前正式角色为销售代表、服务经理、项目经理、团队成员；原03字典还定义渠道工程师、最终客户、技术经理及研发人员。原全量实际还出现15、80和other，未用现有03字典解释它们，不随意创造编码或猜成某个现角色。

原全量主体类型：10为389570条，20为36851条，afss为7038条，afxx为1883条。本批只含10。其余主体的正式承接范围和含义尚未完成核实，不将它们按相同数字ID关联到本批售后项目，也不称为已丢弃。

CUS ProjectCustomerContactService实际具备项目联系事实、主档及历史，直接复用优先；正常新增也会建立客户主档并可能设置首个有效联系人为主联系人。其客户上下文取正式项目customer_id，不据最终客户人员角色自动认定属于这个购货方客户。原9名最终客户人员的单位关系仍要精确核实；不挪用客户姓名猜配、不自动改当前主联系人或激活任务。

后续完整整表方案须同时说明账号成员与无账号联系人各自的业务归属、区间/登记来源/历史联系字段缺口、原NULL日期、旧角色和现存记录保护。没有原字段获准排除；不以来源快照、备注、未经批准扩展或本任务原型表替代正式承接。当前为审计结果，不是新增结构建议或实施授权。

## 后续只读核查与待裁决边界

按原库2026-10-11 09:53:04的时间，以effectiveFrom不晚于核查时间且effectiveTo为空或晚于核查时间判断，本批70条中有44条来源有效区间；其中29条匹配正式账号（销售2、服务经理10、项目经理17），25条匹配账号处于启用状态。尚未完成正式Owner所需的角色、公司和成员资格核验，这些数字不代表可直接授权的人数，也未产生授权或当前主责变更。

全历史按projectType、projectId、memberCode、memberRole、effectiveFrom分组，存在133组重复区间身份、156条额外记录；尚未核对这些重复行的全部非键属性差异，不去重、不调整唯一键。来源15、80及other角色仅出现在afss/afxx主体，未出现在售后10主体；不能将它们作为本批售后角色的缺项随意补入。

用户已回复“有效成员经Owner核验承接项目授权（建议）”。该回复确定授权方向，未批准结构、缺项排除、角色替换或当前主责覆盖。资格核验必须复用ActiveUserSelectionApi的有效账号、有效系统角色及适用公司范围规则；原29条匹配、25条启用仍是候选数，不能作为核验通过数。所有结构及缺项处理仍按完整整表方案另行提交。

来源基础数据全表核查后补充：原projectTypes明确afss为“安服售后项目”、afxx为“安服先行借货”；原afProjectMember明确15为“售前”、80为“安服质量监督员”、other为“其他人员”。PMS-springmvc的ProjectMemberMapper.xml:452实际按afProjectMember关联成员角色名称，印证它们属于另一组原角色分类；该组20为“项目区域负责人”，不能因同码20覆盖售后03组“服务经理”含义。原标签已查明，正式主体/角色的关联和权限仍未获准，不猜配当前角色，也不改变本批10主体的映射边界。证据见任务私有basic-data-original-schema-readonly-20261011.json及上述原Mapper。

依据：任务私有project-member-original-readonly-20261011.json；旧sql-map-project-config.xml的成员写入及queryProjectMembers（2754）、ProjectServiceImpl.insertProjectMember、MessageUtil.FLAG_FROM_PROJECT/FLAG_FROM_MEMBER（391/393）；现有成员Owner/Mapper、ProjectContactContextApiImpl与CUS联系人Owner，以及实际SHOW FULL COLUMNS/SHOW INDEX。未读取用户密码，schemaWrites=0、businessWrites=0。
