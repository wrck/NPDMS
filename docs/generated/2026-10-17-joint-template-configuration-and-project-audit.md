# 联调完成条件模板配置与其他项目排查

依据需求方2026-10-17要求及后续澄清：功能支持模板按需要配置完成条件即可，其他模板可以保留。本次配置当前项目实际来源模板，排查其他项目的实际卡住情况，不将人工提交或业务通过条件一律删除。环境为npdms_domain_test、59191后端、19191前端。

## 配置结果

- 来源模板993009900016（TPL-DELIVERY-FLOW-20260924，项目交付全流程V2）由v3新增发布v4，发布修订ID993009900067。只将DEPLOY_JOINT_DONE由`ALL(TASK_NATIVE_STATUS(DONE), DELIVERABLE(D_JOINT))`配置为`DELIVERABLE(D_JOINT)`；办理绑定、模板匹配、其他任务和阶段规则不变。
- 通过原生草稿API保存，Compiler校验valid=true、issues为空。真实浏览器从项目模板管理进入规则集合，查看“业务联调·完成”：条件类型为交付件，引用D_JOINT，只有一个条件；点击“编译并发布”并确认成功，重新打开版本历史的v4冻结投影验证completionRuleTypeCode=DELIVERABLE、completionRuleConfig={refCode:D_JOINT}。
- 当前模板草稿与新发布Designer只发生上述规则差异；未来按该模板新发布修订创建的项目，从不可变ExecutionSnapshot读取该规则。未为验收创建新项目，不声称已执行新建项目全流程。已有项目的独立计划不因模板发布被隐式改写。
- 其余37个模板根及其版本原样保留，包括3套业务组件型默认模板原有JOINT_TEST_PASSED条件和5套场景草稿；没有自动发布原未发布草稿。原38条PUBLISHED历史修订完整记录均保持不变，仅新增目标模板v4。

## 其他项目排查

只读检查tenant=1下6691个项目，其中285个活动项目、70个存在当前生效计划。51个计划包含同类联调PAGE，42个属于活动项目：7个联调任务已经完成，35个尚待分配且对应必选交付件未满足。另9个属于已关闭项目，保留历史。

按活动项目、当前执行合同、联调页面路由或IMP/jointTest业务绑定和对应交付件关系复核，不仅按任务显示名称判断；同时按联调名称补查旧任务。未发现交付件SATISFIED而任务仍IN_PROGRESS/PENDING_ACCEPT的实际同类卡住情况。35个未开始任务的手工提交规则按用户澄清作为现有配置保留，不批量生成计划版本或强行完成。

真实页面复验：

- 项目992203060197、任务2107837711337496588：联调交付件PENDING，任务待分配、进度0%，页面读取前后任务版本1不变，未误完成。
- 项目992203060198、任务2107838747850678283：联调交付件ACCEPTED，任务完成、进度100%，版本5不变，原计划v7及上次自动完成结果保持。
- 两个页面均等待工作台和交付件加载完成后核验；页面JavaScript错误为空，业务写请求为空。未重新上传、解析日志、补交材料、人工提交或完成任务。
- 全量对照排查前快照，已有项目当前计划、任务状态/进度/版本及交付件要求记录全部不变。本次没有后端代码改动、构建、重启或DAC操作。

## 排查中记录的既有问题

初始宽范围草稿保存尝试在旧示例模板993009001593触发3819约束错误：其DRAFT仍带旧execution_schema_version/快照，通用草稿更新清空execution_snapshot而保留运行元数据，触发ck_project_template_revision_execution_v2。该事务回滚，Designer及快照均与排查前相同。当前来源模板993009900016的规范草稿正常保存、校验、发布；按需求方随后澄清保留其他模板，未绕过约束、清理其历史或外扩修复此旧草稿兼容问题。

## 证据

目录`.run/build/joint-template-unification-20261017/`：

- `templates-db-before.json`、`revisions-db-before.json`、`projects-db-before.json`、`tasks-db-before.json`、`requirements-db-before.json`及相应after：只读范围和历史保护。
- `template-audit.json`、`project-audit.json`、`all-bindings-satisfied-running.json`、`binding-based-stuck-task-audit.json`：旧规则、任务状态和当前合同排查。
- `template-993009900016-draft-before.json`、`template-993009900016-draft-after.json`、`template-993009900016-validation-after.json`、`template-993009900016-published-after.json`：唯一规则变更及校验。
- `template-published-browser.json`、`published-runtime-projection.json`、`template-joint-deliverable-config.png`、`template-published-v4.png`：原生页面发布与冻结运行投影。
- `other-project-browser-evidence.json`、`project-992203060197-joint.png`、`project-992203060198-joint.png`：其他项目与已完成项目真实页面。
- `final-verification.json`、`check-final.py`：其他模板、旧发布历史和现存项目记录不变。

发布脚本有既有发布回执和版本检查，不应删除回执后重复发布。初始audit.py已完成快照但打印Decimal时异常，统计与后续核验使用已保存完整快照；没有将打印异常当作排查通过。未提交或推送Git，不晋级F-IMP-005整体Feature。
