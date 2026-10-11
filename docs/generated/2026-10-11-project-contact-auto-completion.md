# 项目联系人任务自动完成实施回执

2026-10-11需求方明确“用户联系人获取，只要有项目用户联系人有效记录就自动完成”，随后确认具体方案：当前项目至少一条启用、未删除、属于项目关联客户的有效联系人记录即满足完成，不需主联系人或手工提交；新增、载入、恢复及已有记录参与；阶段准入与完成历史保留；停用不自动撤销已完成任务。范围沿用模板993009900016及此前修复的13个ACTIVE项目，关闭项目不改，公共模板只保留草稿。

## 实现与契约

CUS新增公开内部ProjectContactCompletionApi，由自身Mapper锁定和判定联系人，PROJ不跨模块查表。项目customerId优先，缺ID只按稳定customerCode解析；租户、项目和客户严格一致，复用ContactRules校验启用记录内容。响应仅包含completed和contactId/version，不复制个人联系方式。

PROJ新增独立PROJECT_CONTACT_EXISTS完成条件，parameters为空。仅客户联系人PAGE任务的独立完成条件可跳过手工提交；其他PAGE/TASK_NATIVE仍须当前轮次提交。新条件经正式规则引擎和冻结任务状态机完成，阶段准入、退出、门禁、子任务保护、版本/幂等保护不变；当前轮次引用的不可变完成评估保存联系人ID和版本作为证据。已结束轮次和原发布快照不改。

原联系人命令保持原有授权、版本及本地关系维护。在同一事务的项目关系历史写入后追加ProjectRuleReevaluationRequested Outbox，新建、引用、编辑、删除和恢复均触发提交后重读；回滚不发送，不直接改任务/项目生命周期。项目计划生效的既有重判使已有记录参与。前端规则编辑器增加中文条件及范围限制。

公开契约登记于docs/design/10-api-design.md的2026-10-11项目联系人完成事实小节。本次未修改未经批准的其他业务语义，未提交或推送Git，保留其他在途变更。

## 已完成验证

- 固定JDK25.0.1+8、Maven3.8.6、工作树依赖库及独占锁，8组后端测试125项通过：CUS事实8、CUS事务回滚1、原联系人维护11、原计划生效10、影响分析13、规则发布21、任务状态机44、计划完成17。日志.run/contact-auto-tests.log。覆盖现有非主联系人满足、无项目引用不满足、停用/删除/无联系方式不满足、跨租户/跨项目/跨客户失败关闭、code回退、Owner失败、Outbox事务回滚、无提交自动完成、阶段/退出不满足以及其他页面仍需手工提交。
- 前端使用现有vitest.pms-file.config.ts：规则选项3项、编辑器运行测试6项，总9项通过。首次误用默认配置时CSS加载失败，改用仓库既有专用配置后通过，未改配置或屏蔽测试。
- 工作树正式后端构建成功并冻结.run/build/releases/ea0c39c0-8df4-411f-ae58-e84bd667183c/manifest.json，22项既有已部署修复基线保持。通过scripts/worktree-backend.ps1 -Manifest <上述清单> -ValidateOnly：local,dac及冻结包/当前运行包校验通过，no service changed。构建日志.run/contact-auto-backend-build.log。

## 运行切换及业务验收结果

用户确认本次重启后，通过scripts/worktree-backend.ps1及上述冻结清单完成59191部署，运行local,dac，PID为40320，后端健康检查通过。48182未启停，独立就绪检查HTTP 200；本次未验证解析业务。

投用脚本.run/configure-contact-auto-completion.py apply通过正式草稿、完整影响预览、生效及逐项目回读完成配置。13个ACTIVE项目（992203060037、177、180、181、183、184、185、187、188、190、197、198、199，缩写均沿用992203060前缀）全部VERIFIED；逐项目确认生效计划ID等于回执草稿ID，完整Designer与预期一致。唯一直接UPDATE为task:PRE_CONTACT，其他变更仅REEVALUATE，无交付件、里程碑或门禁定义修改。旧规则保留，新规则key为CONTACT_PRESENT_DONE_20261011。回执为.run/contact-auto-config.json。

模板993009900016保存为草稿，回读VERIFIED，Compiler valid=true、issues为空，未发布。项目992203060198的割接上线审批绑定仍为pms-cutover-done-approval。7个关闭项目992203060189、191、192、193、194、195、196保持生效修订1，无待生效草稿。

真实浏览器验收：项目992203060190已有两条有效项目联系人，用户联系人获取任务自动完成，进度100%；当前轮次2状态DONE，结束时间2026-10-11 00:57:43.104，submitted_at及submitted_by为空。轮次引用完成评估2108965208766496770，评估结果SATISFIED、evaluated_by=0，business_facts_json.projectContacts记录completed=true及contactId/version为16/1、17/0，执行方式AUTOMATIC，状态转换路径SUBMIT、COMPLETE。证据位于轮次引用的完成评估，未向历史轮次快照追加或覆盖字段。

真实浏览器同时确认项目992203060198当前无项目联系人，任务保持进行中、进度0%，联系人页面显示无记录。未代用户创建、停用或删除业务联系人；新增、恢复、删除的事务触发及失败路径由后端测试覆盖。

历史只读基线.run/contact-auto-history-before.jsonl与生效后.run/contact-auto-history-after.jsonl按ID逐条比较，原24条已结束执行记录完整一致，仅新增上述190项目联系人任务的完成记录。未提交或推送Git。
