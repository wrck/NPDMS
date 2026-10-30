# 独立TEST_ONLY项目的TF-031和TF-060验收准备

2026-10-29。本文只读梳理当前源码和正式规格；未办理页面、未写业务数据库、未迁移或重启。实际业务操作仍从独立TEST_ONLY项目的阶段任务办理进入，按项目冻结模板逐阶段完成，不能直接写状态或跨阶段调用接口。

## 材料路径

目录：`E:/AICoding/Projects/NPDMS/.run/feedback-20261029-stage-acceptance/materials/`。

- `TEST_ONLY_topology.png`：新绘制的逻辑示意图，用于工勘/需求分析/方案的正常附件上传。包含明确TEST_ONLY标识，无客户或真实设备身份。
- `TEST_ONLY_implementation_plan.txt`：明确为软件验收的方案附件样本。客户方案上传组件及保存层支持TXT，但TF-031主验建议使用在线正文，避免把不同方案编制方式的行为混为一项。
- `TEST_ONLY_manual_evidence.txt`：人工证据文件，明确不是实际设备采集或健康结论。
- `TEST_ONLY_non_executable_config.txt`：非执行性描述文本，不能当show-tech或自动采集结果使用。
- `TEST_ONLY_placeholder.lic`、`TEST_ONLY_backup.zip`：无实际授权含义的占位LIC及只含合成证据文本的ZIP。
- `TEST_ONLY_interface.csv`、`TEST_ONLY_ip.csv`：当前模板表头的合成导入样本，使用文档示例网段，不填真实设备SN。

文件大小和SHA清单：`.run/feedback-20261029-stage-acceptance/materials-manifest.json`。图示已实际打开检查。合成材料只用于获授权的独立测试对象，不能替代人员授权、资产归属、来源同步、设备采集或实施就绪事实。

## TF-031实施方案驳回与新草稿

1. 从新项目S0正式指派服务经理和另一名主责项目经理；随后完成冻结模板要求的S1工勘/需求分析，再录入工期、制定并生效S2施工计划。以实际任务前置检查为准，不能因为模板允许并行就跳过本次要求的正常顺序。
2. 项目经理从实施方案任务编写在线方案。当前基础提交必填为项目背景、项目目标、网络拓扑、至少一行含设备名称及部署位置的部署方案。引用需求拓扑时，需求版本必须是本项目FROZEN的完成版本，并具有有效AVAILABLE拓扑附件；需求已更新时按页面选择刷新引用或正式说明差异。
3. 提交前服务端重验`SOL/IMPLEMENTATION_SOLUTION`任务前置、当前项目经理正式角色、项目管理范围、方案版本及来源。审核级别由已有项目字段规则冻结，不是客户端手选；缺规则不能默认普通级别。普通进入项目服务经理审核，重大方案继续按真实复审授权路由。
4. 服务经理在正常审批入口填写明确的TEST_ONLY驳回意见并驳回。原方案应为状态4、审批REJECT，不能把状态3（已批准）当驳回样本。
5. 项目经理回到原项目阶段任务的审批记录，点击“复制为新方案版本”。检查自动打开新的方案ID、状态0且正文可编辑；保存一个明确TEST_ONLY增量，再重开核对。原方案ID、状态4及原驳回意见必须保持。
6. 新ID在分页外也应直接读取；复制失败不切换。复制等待期间改选其他方案时，回执不应抢回当前选择。

当前修复只改变前端导航和类型传递，不修改审批状态规则。完整客户文档分支与在线正文不同；`revise()`会将新草稿remark写为来源追溯文字，客户文档继承语义不在本次导航修复范围，不用该分支替代在线正文验收。

依据：`SolutionTieredReviewService.start/authorize/revise`、`SolutionSubmissionValidator.validateAndFreezeSources`、`SolutionReviewPolicyService.freeze`及`SolutionTieredReviewDialog.vue`。

## S3正常完成至S4割接P1—P4

- **设备前置**：至少一台由AST正式返回且归属于新项目的设备，SN集合、归属版本完整，没有活动割接占用。文件中的逻辑节点不是资产事实；不能搬用其他项目设备或手造SN绕过归属。
- **实施前置**：需要SCH-05最终批准方案；按项目冻结计划的适用性完成到货验收EXE-01、硬件安装EXE-02、配置调试EXE-03、业务联调EXE-04，并从正式入口执行EXE-06就绪校验生成READY快照。明确NOT_APPLICABLE由冻结计划决定，不能人工把必做项改为不适用；同一设备范围的当前记录不能有未完成项。若受真实设备或DAC依赖阻断，应保留阻断事实，不把合成文本冒充自动采集或真实配置完成。
- **P1创建**：从S4割接任务入口选择新项目、上述可选设备及真实已发布配置；任务负责人为正式当前操作者。创建时重新检验项目、设备、客户等级及IMP READY来源。
- **P2评估**：填写当前模板业务重要等级、操作复杂度、隐患风险、备件是否申请四项；客户服务等级已维护时只读，未维护且服务端明确NOT_CONFIGURED时才从启用字典手选。最终等级本次应使用合法人工选择的A/B/C标准方案路径；D级会跳过P3并使用简易模板，不适合验证TF-060客户保障分支。不计算或臆造自动等级。
- **P3清单**：基于任务冻结配置生成；逐项按实际Schema办理DIRECT、COLLECTION、MANUAL或EXTERNAL。COLLECTION可从正式人工证据入口直接选择上述合成文件，不必伪造一次自动失败；人工核查仍为MANUAL，不改成自动成功。EXTERNAL是否可人工降级及失败前置沿实际配置。所有适用必填项须有有效结果；未通过项须保留事实说明和后续风险措施。提交后清单SUBMITTED，正式推进P4。
- **P4标准草稿**：选择ONLINE_TEMPLATE_STANDARD。计划割接时间独立填写；项目说明、至少一条明细计划、六类步骤（割接前准备、操作、收尾采集、割接后业务测试、回退、割接后保障）、全部冻结未通过风险的措施，均按正式表单填写。设备清单使用冻结身份，不从文件补造型号或SN。客户和迪普一线保障必填；二线和研发可选，添加的行也须完整。一线人员从正式授权候选选择，不能手造系统用户引用。

依据：`F-CUT-002-cutover-intake-and-manual-assessment.md`、`F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`、`F-CUT-004-p4-cutover-plan-authoring.md`、`ImplementationReadinessContextFact`、`ImplementationReadinessService.context`及`CutoverPlanRules`。

## TF-060应使用生成下载按钮触发

本次后端修复的确切路径是`downloadNew()`先调用`contacts.validate(...,true)`。`submitNew()`在之前先执行`requireComplete()`，缺客户会更早被已有通用保障完整性拒绝；**只点提交不能证明本次两种错误分类修复**。

1. P4标准草稿先正常保存；保留合法完整的一线保障行，暂不添加CUSTOMER客户行。点击“生成并下载方案”。预期HTTP422、`BUSINESS_INCOMPLETE`、`SUPPORT_ARRANGEMENT_INCOMPLETE`、`FIX_REQUEST`；原消息为“客户保障安排尚未填写”，页面可显示具体缺失角色并展开/聚焦保障安排。不能产生新生成文件或进入P5。
2. 添加手填客户姓名，暂不填客户电话，再保存并生成。预期同一错误分类，原消息“请填写客户姓名和联系电话”；页面高亮对应缺项，当前草稿应保留，不能静默丢行。
3. 补齐独立测试客户的合成联系信息、职责及到位时间，保存并重开确认仍有客户行。一线人员仍使用正式候选身份与实际允许的信息。填写全部其他必填后重新生成，核对新版本文件及状态仍为P4草稿。
4. 本项不要求发起P5审批；如需继续正常流程，以主线程实际授权和验收目标为准。原反馈“已填仍报未填”仍应对照同一revision的保存请求、回包及重开数据记录，不能只靠缺项高亮判为原现象全修复。

记录每次taskId、planRevisionId、planVersion、草稿保存回包、生成错误分类、页面高亮、阶段及文件引用前后值。不得存储访问Token或其他Secret。
