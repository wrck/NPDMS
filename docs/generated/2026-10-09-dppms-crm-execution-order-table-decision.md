# com_crm_execution_order 整表裁决

状态：PILOT_FORMAL_CARRY_AND_MIRROR_RETIREMENT_VERIFIED。用户已批准“整表按建议”；10条执行单通过迁移纠正及COM Owner完成32原字段核对、精确公司/办事处关联和4处空字符串补承接，10次重放无业务改写。全表83487条备份及依赖核查完成后撤回legacy_record_id、source_company_code两个重复列，正式表62→60列；其余所有行值、10个正式资料页面API响应、项目及其他项目业务表、Flyway历史均不变。清理后又核实10次重放无业务改写及32原字段未变化。真实浏览器重开项目51922正式执行单详情，SMS、公司001、办事处162007及日期与落库一致；标签使用中文业务含义。受控证据：verified-execution-fields-formal-carry-20261010.json、verified-execution-fields-repeat-20261010.json、verified-execution-mirror-finalization-20261010.json、verified-execution-fields-after-mirror-cleanup-repeat-20261010.json、verified-execution-fields-browser-business-labels-20261010.txt。整体上线基线对齐仍待处理。

原表pm_project_property_from_sms共32列，正式表60个既有字段及2个本任务新增镜像。逐项复用既有正式字段，不直接按source_前缀撤销具有既有业务含义的原字段。

## 建议一起确认

1. 撤回legacy_record_id、source_company_code两列，原技术ID沿既有INT来源绑定，原公司码按已批准显式转换承接company_code/company_id。
2. 原32列按以下完整清单承接；channelName、customerProjectName复用既有channel_name、customer_project_name，原字段原值保存，不另加镜像。
3. systemId/expendId/objId使用已有system_source_key/expend_source_key/source_object_id。这些是已存在的系统部、拓展部字典键及CRM对象引用，不新增编码、不猜成其他业务属性。
4. 已有来源业务内容冲突先登记，原空字符串等差异原样补承接。公司/部门ID按正式编码精确绑定；当前缺失关系不得宣称完整。
5. 真实库清理两列前完成全表数据、绑定和依赖保护；SQL/migrations按最终裁决直接整理，无先加后删。

## 60个既有字段

| 正式字段 | 类型 | 处理 |
|---|---|---|
| id | bigint | 正式执行单技术主键；原id通过既有来源绑定关联 |
| tenant_id | bigint | 迁移租户，关系和查询保持同租户 |
| source_system | varchar(32) | 原dataSource直接承接 |
| execution_no | varchar(255) | 原orderExecNumber直接承接 |
| project_code | varchar(255) | 原projectCode直接承接 |
| project_name | varchar(1024) | 原projectName直接承接 |
| primary_project_id | bigint | 精确关联来源项目；当前项目关系冲突列明，不跨项目猜配 |
| sales_rep_code | varchar(64) | 原salesManCode直接承接 |
| sales_rep_name | varchar(128) | 原salesManName直接承接 |
| sales_rep_phone | varchar(300) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| market_code | varchar(64) | 原marketCode直接承接 |
| market_name | varchar(255) | 原marketName直接承接 |
| system_source_key | varchar(64) | 原systemId直接承接 |
| system_code | varchar(64) | 原systemId保存既有system_source_key，不臆造正式系统部编码 |
| system_name | varchar(255) | 原systemName直接承接 |
| expend_source_key | varchar(64) | 原expendId直接承接 |
| expend_code | varchar(64) | 原expendId保存既有expend_source_key，不臆造正式拓展部编码 |
| expend_name | varchar(255) | 原expendName直接承接 |
| industry_code | varchar(64) | 原industryId直接承接 |
| industry_name | varchar(255) | 原industryName直接承接 |
| department_id | bigint | 按正式部门编码及已批准别名精确关联；本批10条当前为空，列为待关联 |
| department_code | varchar(64) | 原officeCode直接承接 |
| department_name | varchar(128) | 原officeName直接承接 |
| service_type_name | varchar(128) | 原serviceTypeName直接承接 |
| channel_name | varchar(1024) | 原channelName直接承接 |
| engineering_fee_raw | varchar(64) | 原engineeFee直接承接 |
| engineering_fee | decimal(20,4) | 合法原engineeFee金额按既有DECIMAL(20,4)解析，与engineering_fee_raw同时保留 |
| source_object_id | varchar(64) | 原objId直接承接 |
| apply_type | varchar(64) | 原applyType直接承接 |
| company_id | bigint | 按已批准转换后公司编码精确关联正式公司，本批10条当前为空，列为待关联 |
| company_code | varchar(64) | 原corporationCode直接承接 |
| company_name | varchar(255) | 从精确正式公司关系读取；原表没有公司名称，不伪造来源名称 |
| customer_project_name | varchar(1024) | 原customerProjectName直接承接 |
| final_customer_name | varchar(1024) | 原finalCustomerName直接承接 |
| agent_name | varchar(1024) | 原agentName直接承接 |
| project_manager_code | varchar(1024) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| project_manager_name | varchar(512) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| decision_path | varchar(1024) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| required_in_date | date | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| receiver_name | varchar(512) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| receiver_contact | varchar(300) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| receiver_address | varchar(1024) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| loan_reason | text | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| project_type | varchar(32) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| major_project_level | varchar(255) | 原majorProjectLevel直接承接 |
| project_amount | decimal(20,4) | 原projectMoney直接承接 |
| af_project_amount | decimal(20,4) | 本原表没有对应来源值，保留已有正式业务事实，不推测填充或覆盖 |
| submit_time | datetime(3) | 原submitTime直接承接 |
| predicted_bid_time | datetime(3) | 原predBidDate直接承接 |
| contact_name | varchar(255) | 原linkmanName直接承接 |
| contact_phone | varchar(128) | 原linkmanTel直接承接 |
| af_evidence_status | varchar(32) | 保持既有UNKNOWN/CONFIRMED Owner语义，不从普通项目金额推断安服证据 |
| source_sync_time | datetime(3) | 沿既有Owner同步接收时间；原表没有同步时间列，不伪造历史时钟 |
| status | varchar(32) | 保持既有正式启停事实；不从原项目状态臆造执行单状态 |
| version | int unsigned | 既有乐观锁并发控制 |
| creator | varchar(64) | 沿正式Owner审计；原表没有创建人 |
| create_time | datetime(3) | 沿正式Owner记录创建审计；不把submitTime当原记录审计时间 |
| updater | varchar(64) | 沿正式Owner审计；原表没有更新人 |
| update_time | datetime(3) | 沿正式Owner更新审计；原表没有更新时间 |
| deleted | tinyint | 保持逻辑删除历史，不复活旧记录 |

## 2个新增镜像撤回

| 列 | 正式复用及原因 |
|---|---|
| legacy_record_id | 原技术ID保留既有INT来源绑定；完成所有关联/调用依赖核查后撤回重复业务列 |
| source_company_code | 复用company_id/company_code和已批准转换；原码仅保留已有来源证据，不造重复实体字段 |

## 原表全部32列

| 原字段 | 正式承接点 | 处理及原因 |
|---|---|---|
| id | 既有迁移/同步来源绑定 | 原自增ID是技术身份，沿既有INT来源绑定关联正式执行单，不新增legacy_record_id业务列 |
| orderExecNumber | execution_no | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| projectCode | project_code | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| projectName | project_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| salesManCode | sales_rep_code | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| salesManName | sales_rep_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| marketCode | market_code | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| marketName | market_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| systemId | system_source_key | 既有正式字段就是系统部原键；不把字典ID臆造为system_code |
| systemName | system_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| expendId | expend_source_key | 既有正式字段就是拓展部原键；不把字典ID臆造为expend_code |
| expendName | expend_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| industryId | industry_code | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| industryName | industry_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| officeCode | department_code / department_id | 原办事处编码保存到正式编码列；按已批准精确部门及别名规则关联ID，不截断或按父级扩大授权 |
| officeName | department_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| serviceTypeName | service_type_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| channelName | channel_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| engineeFee | engineering_fee_raw / engineering_fee | 原字符串直接保存到既有raw字段；合法金额另解析到既有金额列，不能只保存解析结果；非法或超精度值列明阻断 |
| objId | source_object_id | 既有字段保存CRM来源业务对象引用，区别于原表自增ID，不撤销或编造对象关系 |
| applyType | apply_type | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| corporationCode | company_code / company_id | 沿已批准01→001、02→002、03→003、04→004；未知阻断；公司ID仅从正式公司按编码精确关联 |
| customerProjectName | customer_project_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| finalCustomerName | final_customer_name | 产品实际最终用户；沿已裁决含义承接，不拿代理商或订单买方替代 |
| agentName | agent_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| projectMoney | project_amount | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| submitTime | submit_time | 原业务时间直接承接，不把它冒充执行单审计创建时间 |
| majorProjectLevel | major_project_level | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| predBidDate | predicted_bid_time | 复用既有预计投标/中标业务日期，保留原值，不新增镜像日期 |
| linkmanName | contact_name | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| linkmanTel | contact_phone | 原值直接复用正式字段，保留原NULL、空字符串及合法内容；冲突先列明，不覆盖现存不同业务事实 |
| dataSource | source_system | 原实际SMS/SAP/D365来源类别，直接复用既有正式字段，不新增ERP镜像 |

## 实际原表与现表核查

本批10条均唯一匹配。逐字段比较差异：{"channelName": 3, "customerProjectName": 1}；数值金额按十进制比较，不将显示精度差异当业务损失。

公司ID待关联10条，部门ID待关联10条。原表无创建/更新人、记录审计时间、同步时间字段，不能拿submitTime或本次运行时间冒充历史审计。

全租户含逻辑删除共83487条；新增镜像非NULL数量{"legacy_record_id": 10, "source_company_code": 10}；公司映射不一致0条。原镜像值受控保留在本任务私有保护证据中，本次数据库写入0。

上述是只读值核查，尚未完成来源绑定、正式关系补全、真实库列清理、部署或页面验收。

## 实际MySQL Mapper验证

3项实际MySQL/XML验证及47项受影响定向回归通过。覆盖实际ERP来源类别与旧ERP类别兼容、跨租户/删除/错来源键排除、空筛选集合返回空、同步识别已删除身份以阻止重建，以及执行单原空字符串、正式组织关系、乐观锁和审计更新。测试数据只存在于单连接临时表，关闭连接后消失；未改写真实业务行或表结构。服务、迁移和页面验收仍待后续执行。
