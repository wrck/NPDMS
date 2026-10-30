# 实施方案、需求分析、现场培训、满意度调查专项对比

2026-10-24，只读审查；没有修改业务代码、数据库、权限或业务记录，没有保存、提交、审批或重启。

## 范围与证据边界

比较源实际工作目录 E:/AICoding/Projects/NPDMS 与当前测试工作树，包含源未提交修改。复用此前配置内容核验，新增逐文件差异及两端真实浏览器入口检查。原始页面记录：`.run/project-delivery-recheck-20261024/deep-comparison/focused-business-ui.json`。

浏览器来源为 19191 管理员、项目 2109733194632396801；测试为 19086 的 z01010、已有初始化测试项目 84573。两者不是同一业务实例，不能据按钮、数据行或角色差异判定权限回归。实际同业务测试项目 84529 缺生效计划，详见 deep-project-stage-view-comparison.md。

## 逐项结果

| 界面 | 已确认内容 | 差异及结论 |
|---|---|---|
| 实施方案 | 实际业务视图 SOL_IMPLEMENTATION_SOLUTION 注册到 engineering/solution-reviewed/index.vue，两端入口均呈现八章方案、配置脚本和培训资料章节 | saveSolutionScripts.ts 测试使用 FileId，源使用 number。旧 engineering/solution/index.vue 测试还增加客户方案附件保存、版本回读及失败提示；不能把旧页面差异当成当前正式视图缺失，也不能覆盖掉测试已有附件修复。引用按钮是否可用受项目数据/权限影响，本次两个实例不等价，未据此判为缺陷 |
| 需求分析 | PROJ_REQUIREMENT_ANALYSIS 注册到 delivery-business/requirement-analysis/entity/EntityPanel.vue。两端加载结束均显示版本选择和“创建草稿后可填写11项核心内容及表单扩展项”；已有核验表单内容语义相同，源 v10 与目标 v9 的版本数字无需强行一致 | 测试 EntityPanel 新增需求交底、交付件只读展示和保存后的交付件刷新，RequirementAnalysisSectionCard 新增失效文件引用提示，均须保留。当前两个实例没有可展示的分析版本，未新建草稿，因此不能宣称已验收11项编辑、冻结与归档闭环 |
| 现场培训 | IMP_TRAINING_CONFIRMATION 注册到 engineering/training/index.vue；两端业务入口都能显示培训记录表。此前已核验两份发布培训表单和打印选择 | 测试缺来源客户联系人空列表的权限核对说明；public-confirm.vue 缺“已确认、归档不可修改”动态文案及确认后表单文字增强样式。这是源码确认的呈现差异；并非证明确认记录可重新编辑，本轮没有提交客户确认 |
| 满意度调查 | ACC_SATISFACTION 注册到 acceptance/satisfaction/index.vue；两端都展示完工证明与调查联办列表及空状态 | questionnaire.vue 测试重开已提交记录时仍有填写引导及有效期文案，未按源已提交状态显示历史记录说明；无完工证明的独立调查成功提示也固定写“两份表单”。原有 submissionLocked、已提交禁用和提交入口保护仍在，不能夸大为可篡改历史 |

## 满意度后台处理差异

`SatisfactionTaskTodoProjectionService.java` 来源额外校验当前租户、项目任务身份以及模板版本、规则版本和阈值，并将已进入 PENDING_DECISION/PASSED/PENDING_ARCHIVE/FAILED 的收集任务判为 OBSOLETE，避免已消费事件重试时生成新的待办。测试仍直接按事件旧 projectTaskVersion 执行 lockAndRevalidateSatisfactionTask，没有该已处理任务分支；`SatisfactionTaskOutboxDeliveryJob.java` 也没有对应跳过统计。

这是实际处理逻辑缺口，可能使消费后的旧事件重试持续失败；是否已经发生须进一步读取相关 Outbox 状态、运行类与日志证明，本轮没有重放事件或改任务状态。不能用补表单配置替代该差异分析。

## 本次结论

四类入口均存在并可只读打开，但尚不能认定业务全流程与来源一致。缺口分为客户历史确认页呈现、满意度事件处理逻辑，以及项目实例计划/任务缺失三类；需求分析和方案附件还存在测试独有的有效增强，应逐项保留。没有执行保存、上传、审批或客户签字，以上结果不构成这些写入流程的业务验收。
