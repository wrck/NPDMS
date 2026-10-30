# 项目业务操作差异与变更来源核实

2026-10-24。本报告以源实际工作区为基准，只把有发生环境证据的测试修复列入保留范围。替代此前 focused-business-ui-comparison.md 中“目标独有增强须保留”的取舍判断；此前浏览器观察与文件差异仍作为历史证据，不改写原件。

## 基准与方法

- 来源 E:/AICoding/Projects/NPDMS，codex/domain-migration@4d149f34faec4b66e93373794693d6bbd738b716，包含未提交修改。
- 目标 M:/AICoding/CodexData/worktrees/853c/NPDMS，HEAD c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a，未提交修改单独对照。
- 对限定业务路径重新枚举两端已跟踪和未跟踪文件，比较内容（仅统一 CRLF/LF），再比 HEAD、两端独有提交、引入/删除关键语句的提交、合并和原始验证记录。范围内471文件，438内容一致、33有差异。逐文件账本见 business-operation-provenance.json。
- Git 的“目标独有提交”只证明分支谱系，不证明发生于测试环境。运行包包含某能力也不证明该能力是测试修复。未提交源改动能证明当前差异来源，不能凭此确定作者、发生日期或首次运行环境。
- 未覆盖代码；未修改数据库、权限、实例或服务；本轮不新增保存、审批、客户签字等写入验收。页面只读证据引用上一轮 focused-business-ui.json。

## 业务操作逐项裁决

| 项目 / 操作 | 差异及来源证据 | 核实结论与后续取舍 |
|---|---|---|
| 实施方案正式入口、八章内容、查看 | 正式注册到 solution-reviewed/index.vue，该主体两端一致；不能将旧 solution/index.vue 当正式业务视图 | 该主体无恢复差量；不因历史路径差异认定正式页面缺失 |
| 正式方案脚本保存标识 | saveSolutionScripts.ts 的 number→FileId 差异由 d1dcdfc3c 合并引入；源 f7a289c81 对应脚本逻辑已接入 | 属合并类型适配，未找到测试环境故障→修复证明；不列测试修复保留项，列来源基准适配待处理 |
| 旧方案客户文件选择、下载、草稿保存、提交前附件登记 | solution/index.vue 的 persistDraftAndCustomerFiles 及配套 SolutionChapterForm 更换文件选择组件，来自 be007ec33，后者另经 d1dcdfc3c 整合 | 10月6日独立原生交付开发 checkpoint，提交正文明确验收未完成、没有部署。不是已确认的本次测试修复；撤回“附件增强须保留”。不得直接删整文件，需按源行为整理完整调用链差量 |
| 需求分析额外通用业务页及 Controller/Service/Mapper | 231a53bc0、8f2198c40、6596bf418 引入继承式业务默认实现、修订复制交付件、表单扩展编辑，来源无这套额外入口 | 独立开发增强，不列测试修复保留项。原验证记录明确 fixture 身份/项目/存储及无生产部署，不能当真实测试环境修复证明 |
| 需求分析模型继承、字段元数据 | RequirementAnalysisDO / RevisionDO 改用 BaseProjectBusinessEntity 等，来自231a53bc0 | 同一继承式开发链，不因当前目标使用而保留。相关表结构、接口和历史兼容必须联动评估，不先覆盖实体类 |
| 需求分析放弃草稿 | 源 mapper 改 revision_no=-id 后逻辑删除；目标仅逻辑删除并 version+1。目标231a53bc0与 V399__requirement_revision_active_identity_uniqueness.sql 配套使用活动唯一槽，保留旧修订号 | 可证明来自10月7日开发，不足以证明测试环境修复；不列保留例外。两份V399版本号碰撞且存在数据库契约依赖，不能只回退SQL或改写历史修订号来对齐 |
| 需求分析附加 read 权限辅助方法 | RequirementAnalysisAccess.requireReadPermission 随继承式业务入口引入，d1dcdfc3c整合 | 与额外入口为同一来源；不作为服务经理问题的已证实修复，也不允许借对齐取消实际服务端授权 |
| 需求交底与交付件展示 | EntityPanel 的需求交底可追至08a3ed755；源cbd145505明确删除 RequirementBriefingSection/openBriefing。目标be007ec33交付件接入、d1dcdfc3c整合后仍含交底及交付件刷新 | 属旧开发/合并差异，不是已确认测试修复；尤其需求交底并非“测试新增”，而是源已删除路径在目标仍存在。撤回“测试有效增强须保留” |
| 需求附件 FileId、失效引用提示 | requirement-analysis/index.ts 的 FileId 与 RequirementAnalysisSectionCard 的空引用保护来自9610d1ae7类型契约修复 | 确认是10月14日独立工程修复，有隔离检查但无本次测试发生证据；不列自动保留。涉及文件ID精度和失败保护，必须在后续按源适配中验证，不能直接去掉校验来消差 |
| 网络拓扑非必填 | 当前目标工作区删除@NotBlank；源6654121c5已经删除相同约束。仍存在模型继承/元数据差异 | 这是恢复源字段约束，不是保留目标独有增强；不可用此一行证明整个目标实体应保留 |
| 培训记录列表、新建/邀请入口、打印主体 | training/index.vue 两端HEAD相同，来源20c48c007；既有发布打印模板配置已核验 | 主体不是新目标增强；本轮没有执行新建、发送邀请或打印下载，不能称业务完成 |
| 培训客户联系人空状态 | 源工作区未提交添加 no-data-text，目标等于两端HEAD | 明确是源当前工作区增量，列源基准待接入；不是测试侧回退修复 |
| 培训客户已确认记录回看 | public-confirm.vue 源工作区增加已归档不可修改文案及确认记录文字样式；目标等于共同HEAD | 明确是源未提交增量，列源基准待接入；无依据认定目标禁用逻辑丢失 |
| 满意度列表、完工证明联办 | satisfaction/index.vue、问卷组件主体等两端一致 | 无主体代码差量；配置/实例是否完整另见配置与计划报告，不凭代码相同判业务验收通过 |
| 满意度已提交记录回看、独立调查成功提示 | questionnaire.vue 源工作区按submitted显示历史记录文案、隐藏不适用有效期、区分一份/两份表单；目标等于共同HEAD | 明确是源未提交增量，列源基准待接入；目标原submissionLocked保护仍存在 |
| 满意度任务事件投影、重试 | 源未提交修改SatisfactionTaskTodoProjectionService/OutboxDeliveryJob及测试：当前租户/身份校验、模板规则阈值核对、已处理任务返回OBSOLETE；两端HEAD相同 | 源工作区处理逻辑增量，目标未含；不能归为目标独有修复。旧事件是否已反复失败本轮未查询/重放，不宣称线上事故已证实 |
| 业务保存后任务同步、操作栏反馈 | ProjectFlowPanel两端HEAD相同但两端均有未提交修改。源增加完成状态回读等；目标工作区相对HEAD反而删掉“业务已保存，正在同步任务状态…”提示，只调整TaskStateActions显隐 | 拆为源增量与目标未提交差量；目标此处无故障修复发生证据，不列保留。不能把整个文件当测试修复文件 |
| 审批抽屉关闭、闭环刷新、错误说明、历史阶段诊断 | ProjectApprovalDrawer、ProjectNormalClosurePanel、TaskStateActions、BusinessViewHost、StageGateResultsPanel均为共同HEAD之上的源未提交增量，目标未改 | 列源基准待接入；文件级来源已确认，实际审批/完成后状态变化尚未写入验收 |

## 发生环境证据的限制

1. be007ec33 的正文明确是授权临时分支的开发checkpoint且验收未完成，没有部署；不能将其继承到目标等同于测试修复。
2. docs/generated/survey-requirement-adoption-20261007/verification-summary.json 明确 fixture 身份/项目/存储、无生产部署，不能证明231a53bc0等发生于当前59286测试环境。
3. .run/direct-test/latest-manifest.json只能证明2026-10-24构建产物指向当前M工作树与59286，不能证明其中所有既存改动均在测试环境产生。
4. .run/direct-test/protection/verified-repair-baselines.json指向E工作树10月9日旧产物，approved-repair-successors还含59191授权历史。因此“22项保护通过”不能直接转成“这四类页面目标差异均须保留”。

本次四类业务的目标独有差异中，**尚没有足够证据满足“确认在测试环境发生的修复”这一保留条件**。已能定位为旧开发或合并来源的，不列保留；源未提交增量按源基准单列。不能证明的发生环境保持未知，不推断为测试修复，也不推断为可以无条件删除。

本结论不撤销已经执行的模板/表单/BPM配置修复或其他模块中另有证据的测试修复。历史、批准版本、快照、审计记录与服务端安全约束均不以来源对齐为由覆盖。下一步如实施，应以本清单逐操作调整及验证，不能整文件覆盖。

## 可复核产物

- 逐文件账本：business-operation-provenance.json（33项，含测试与新增旧入口）。
- 生成脚本：.run/project-delivery-recheck-20261024/audit-business-provenance.py。
- 既有浏览器证据：.run/project-delivery-recheck-20261024/deep-comparison/focused-business-ui.json。
- 命令核心：git ls-files、git show HEAD:path、git log 两端范围、git log -S关键语句、git diff --no-index；不执行构建、数据库操作或服务重启。
