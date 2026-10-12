# 项目阶段读取的联系人规则依赖

状态：HANDOFF_AUTHORIZED / RUNTIME_AND_BROWSER_VERIFIED。用户2026-10-11明确授权按清单直接复用，覆盖此次交接顺序。已接收17个必要文件，源工作区未修改，计划、结构及业务状态未改；来源文件混有另一项安装地址完成能力，已排除其字段、求值及测试增量。

实际验证：92项联系人Owner、租户/项目/客户边界、事务唤醒回滚、发布及计划完成规则测试通过，完整构建通过。已切换任务59282，正确实例和阶段接口成功，23个执行节点可读，三个原计划全字段未变。真实浏览器重新进入992203060190，S0完成、S1进行中、其他阶段等待准入；用户联系人获取任务显示既有完成结果及100%进度，嵌入工作区加载既有2条启用联系人。没有重复配置计划、修改联系人或再生成任务完成事实。私有证据project-stage-contact-dependency-api-verified-20261011.json、project-stage-contact-dependency-browser-20261011.txt、project-stage-contact-task-browser-20261011.txt及contact-approved-handoff-tests-20261011.log。

任务前端19082、后端59282读取项目992203060190时，正确的实例接口 `/pms/projects/992203060190/instances` 成功；阶段接口 `/api/v1/pms/projects/992203060190/node-executions` 返回系统异常。异常栈为 DeliveryDefinitionPayloadValidator.rule → TemplateVersionSnapshot.validate → TemplateExecutionSnapshotReader.read → ProjectStageSubmissionService.list，具体异常是 `unregistered predicate`。XML schema启动告警与本次失败栈无关，不作为Mapper修复依据。

指定库的当前有效计划2104870198458802227（格式3）包含 PROJECT_CONTACT_EXISTS；两个已取代计划不包含此规则。主工作区 E:/AICoding/Projects/NPDMS 已有该规则的完整正常业务实现和真实配置回执，来源 docs/generated/2026-10-11-project-contact-auto-completion.md 明确记录它已经用于本项目。任务工作树缺少该依赖，不能修改冻结计划、移除规则或只放宽读取校验来隐藏版本差异。

## 建议接收边界

来源工作区只读，接收仅修改本任务工作树；不提交、推送、修改数据库结构、改已有计划或重新执行来源配置脚本。增量尚未提交；需按技能交接要求等待正式交接，或由用户明确允许本次选择性复用。此前日志增量的例外只覆盖日志清单。

新增源文件按原实现接收：

- CUS API `pms-module-customer-api/.../api/contact/ProjectContactCompletionApi.java`。
- CUS `.../api/ProjectContactCompletionApiImpl.java`、`.../dal/mysql/contact/query/ProjectContactCompletionQuery.java`、`.../service/contact/ProjectContactRuleWakeup.java`。
- PROJ `.../domain/rule/ProjectContactCompletionCondition.java`。

已有文件仅合并该规则相关差异：

- CUS ProjectCustomerContactMapper.java / 对应XML的场景查询和租户、项目、客户锁定；ProjectCustomerContactService的事务内Outbox唤醒。
- PROJ DeliveryDefinitionPayloadValidator、DeliveryDefinitionResolver、ProjectRulePublicationValidator及ProjectTaskPlanCompletionService的注册、发布边界和Owner事实求值。
- 对应CUS事实/唤醒/既有联系人维护，以及PROJ发布校验/计划完成的原失败测试；保留本任务其他差异。

不新增规则语义，不跨模块查业务表，不复制其他任务功能；按来源既有正式API核对直接依赖。接入前核对引用的既有API和模型，若发现未列的实际必需依赖，先登记具体调用及最小范围。仅后台接收，无新增前端编辑功能；当前已配置计划读取是本次直接消费者。

## 验证边界

接收后验证联系人规则允许的确切任务绑定、空参数、有效联系人Owner事实、租户/客户/项目边界、事务回滚、其他页面仍需手工提交及冻结计划读取。正常完成仍通过现有任务状态机，不能直接更新生命周期或任务状态。完整构建后仅切换本任务59282；按现有授权可重启，其他服务不变。真实浏览器重新读取本项目阶段状态；原计划、配置回执、已完成历史和迁移证据保护，不通过重复配置或修改业务联系人制造成功。

只读证据位于任务私有 project-stage-snapshot-predicates-20261011.json 与 project-stage-read-failure-20261011.json。后一文件最初对instances误用 `/api/v1` 路径所产生的404，不是实际前端失败；正确路径已独立核实成功。

## 授权接收与实际验收

用户明确授权按清单直接选择性复用，覆盖本次先合master顺序。17个最小文件接入本任务工作树，来源安装功能的无关差异排除；92项联系人事实、权限、事务回滚、发布及计划规则测试通过。完整后端打包完成并切换任务59282，来源工作区及其他服务未操作。真实浏览器重新读取阶段：S0为DONE、S1为ACTIVE，联系人任务DONE，两条原联系人保持。冻结计划、配置回执、原完成历史及来源证据未改写。该来源依赖缺口LOCAL_VERIFIED，不再保留为待打包或待真实读取。
