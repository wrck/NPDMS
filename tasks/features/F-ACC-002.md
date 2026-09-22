# F-ACC-002 满意度问卷、达标判定与归档同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION`<br>
> Feature Ready Gate：`READY / GO`（来源`145e4a61`；master修订011已关闭`Q-GOV-20260901-001`）<br>
> Technical Plan Gate：`PASS / GO`（`41f92526`）<br>
> Implementation Done Gate：`NOT_ESTABLISHED`<br>
> 当前阻断：`代码已选择性集成至master；当前master真实MySQL、Chromium与独立Done裁决未完成；历史分支Done只作证据`<br>
> 当前任务：`master@b3e7c76e代码回执后的运行复验与独立裁决`<br>
> Requirement ID：`ACC-02@V1=FULL`；`ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`<br>
> Feature Spec：`specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md`<br>
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-acc-002-satisfaction-questionnaire-result-deliverable-sync.md`<br>
> 分支/工作树：`master` / `M:\AICoding\CodexData\worktrees\master-governance\NPDMS`<br>
> 独占端口：后端`59340`；前端`19340`

## 2026-09-20 手动首轮发起（需求方本次确认）

- 范围：当前工作树`codex/domain-migration`在已有未提交改动之上增加受控手动首轮入口；不等待初验时点，仍使用项目冻结问卷、当前责任人和交付件关联。已有调查返回原首轮，整改重收与历史保护保持原规则。未提交、未合并，不据此晋级Feature Done。
- 实现：TaskPanel按钮/API调用；`SatisfactionTaskController`新增`POST /satisfaction-tasks/actions/start`；初始化服务复用原创建路径，在PROJ任务事实锁内按项目查首轮，防止自动/手动重复创建；重放不重复发送创建事件。新增场景查询与Mapper XML，无DDL或种子变更。
- 权威同步：本次批准记录到ACC-02 PRD条目与Feature规则；新增Business API记录到SDS第10章。范围仍为ACC-02@V1及原满意度来源切片。
- 验证：后端14项聚焦测试通过（初始化8、验收自动触发3、既有指派3）；前端23项运行测试通过；宿主机JDK25全服务打包通过；前端`pnpm run ts:check`通过。最初直接运行Vitest未使用仓库配置导致CSS加载失败，按`vitest.pms-file.config.ts`重跑通过；默认内存类型检查失败后使用仓库8GB脚本通过。首次打包遇运行中JAR占用，核实并重启本分支59191测试服务后成功。
- 真实Chromium：在19191登录、打开项目`992203060010`满意度入口、点击手动发起，按钮可用且真实接口响应；当前`npdms_domain_test`租户1无已冻结满意度项目任务，接口按预期拒绝，页面显示失败原因检查提示，无未处理页面异常。未直接补写业务表，未完成真实首轮创建/重复创建/后续自动触发及MySQL并发验收；这些路径的现有证据为上述单元测试，不能冒充完整业务验收。
- 自审：检查权限与租户、手动来源真实性、自动/手动首轮去重、历史保护、重复点击及失败重试幂等键、异步项目切换；未做独立审查。命令日志与浏览器截图仅保存在被忽略的`.run/satisfaction-manual-*`。

## 2026-09-21 无冻结配置项目首次手动发起修复（需求方明确批准）

- 批准依据：需求方允许首次发起时选择已发布问卷模板并冻结题目、评分与阈值，已有调查复用，不重复新开。同步ACC-02 PRD、Feature规则和第10章API契约；不晋级Feature或Implementation Done。
- 根因与实现：旧入口仍依赖项目预冻结配置；新增`ManualStartDialog`、`start-options`和PROJ `ProjectManualSatisfactionApi`，支持选择未完成关联任务与当前发布版本。已开始但未完成的任务可首次配置，保留生命周期及开始时间；PROJ负责配置写入，ACC复用唯一交付件或补充非必选满意度报告。发布版本校验、权限/租户校验和事务锁继续生效；已冻结历史版本可读取SUPERSEDED修订，新选择仍只接受当前发布版本。待办投影按真实关联任务代码校验。
- 后续链路：自动触发复用已有首轮；签字上传修复操作编号超过PLT接口32字符限制的问题，同一上传槽重试复用编号，不降低接口约束。
- 初始化数据：V322提供明确标注的草稿示例，V323以前向迁移修复示例十进制字符串编码，仅匹配未修改草稿；已在隔离`npdms_domain_test`应用到323。示例阈值不代表通用业务阈值，实际发起需选择业务确认的发布版本。
- 验证：JDK25/Maven全服务打包通过，后端28项针对性测试通过（初始化8、待办1、PROJ手动冻结4、任务事实15），前端项目上下文25项运行测试通过，范围内diff检查通过。全量前端类型检查未通过，8处错误位于安装、配置调试、业务联调页面，与本次满意度变更无关；未扩展修改这些页面。
- 真实Chromium与MySQL：19191/59191、租户1、项目`992203060010`从无冻结配置开始，页面发布示例并选择S5满意度任务，成功创建首轮`2101710570674630658`，再次发起返回同一taskId且数据库仅一条首轮；冻结模板`992209210001`/修订`992209210002`/阈值80。原项目任务仍为IN_PROGRESS，开始时间保持`2026-09-20 17:27:50.772`。通过页面生成客户链接，移动端填写、手写签字并提交成功，显示得分100、阈值80、达标。未执行真实自动事件重放及并发压测，不以单元测试代替其运行证据。
- 运行与证据：共享target JAR被其他构建覆盖时出现类加载失败，使用已完成构建的`.run/satisfaction-verified-server.jar`副本恢复59191，随后客户提交通过。日志/截图为`.run/satisfaction-config-*`、`.run/satisfaction-submit-resume.log`及`.run/satisfaction-submit-inspect.png`，不保存受控链接Token。已自审，未独立审查、未提交或合并。

## 2026-09-21 参照现场培训的操作与可视化配置优化

- 需求依据：本次用户要求参照现场培训，并明确“同时改为可视化模板配置”，属于ACC-02既有功能交互增强。审计现场培训列表/外发弹窗/客户确认页、满意度模板服务和既定评分解析器后，复用共享FormCreate与签字能力，保留满意度业务模型。无新API、DDL或权限角色，不改PRD评分语义，不晋级Feature Done。
- 变更：`QuestionnaireDesigner.vue`与`questionnaireDesigner.ts`提供四种题型、排序、必填、选项分值、多选范围、文字长度、求和/加权平均、精度与舍入设置；精确分数计算自动生成满分和十进制字符串，不能精确表达的配置在提交前提示。`TemplatePanel`移除JSON输入，创建模板后进入可视化配置，新修订带入已有数据，保存/发布仍使用原接口。`ManualStartDialog`增加所选问卷预览。
- 外发与填写：`TaskPanel`参照培训展示二维码下载、客户系统地址、有效期、打开问卷与复制链接；保留Token/租户，不接受非HTTP(S)地址，防重复外发及关闭/项目切换后的迟到链接展示，修复默认有效期将UTC误作本地时间的问题。客户页沿用共享表单验证，补充文字长度校验，提交期间禁用输入，成功后展示感谢与原判定结果。
- 发布修正：`SatisfactionTemplateManagementService`允许同一模板当前版本正常被后继修订替代；其他模板的适用冲突继续拒绝。管理端提前提示冲突，并只发布高于当前发布版的最新草稿，不默认回退到遗留草稿。
- 实际验证：前端31项针对性测试通过，后端评分与发布7项测试通过、JDK25全服务打包通过；范围内diff检查通过。全量类型检查仍为其他工程页面8处既有错误，满意度文件未报错。未运行全仓测试或独立审查。
- 真实浏览器：在19191/59191、隔离租户1新建`SAT_VISUAL_ACCEPTANCE_0921`，可视化编辑、预览、保存、发布、重新打开数据保持一致；项目`992203060005`使用第2版创建调查`2101716952870211585`，再次发起返回同一调查，二维码保存入口、地址切换、无效地址拦截、必填提示、客户手写签字与真实提交全部通过，得分100/阈值80，无未处理页面异常。随后通过页面发布同模板第3版，API复核第2版为SUPERSEDED，而已发起调查仍保留第2版原题目。
- 证据：`.run/satisfaction-training-tests.log`、`.run/satisfaction-training-types.log`、`.run/satisfaction-visual-backend.log`、`.run/satisfaction-visual-resume.log`、`.run/satisfaction-visual-flow.log`、`.run/satisfaction-visual-successor.log`及可视化编辑/提交截图。未持久化客户链接Token；59191运行已构建JAR的隔离副本。修改未提交、未合并。

## 实施边界

- 实现模板冻结、满意度Task/Questionnaire/Response/Result、现场协助、整改重收、失效、满意度来源归档、历史下载和统一异步导出。
- PLT拥有唯一文件、ExportTask/ExportAudit和归档真值；ACC只提供满意度Owner事实与`ACC/SATISFACTION_RESULT`导出Provider；PROJ继续拥有ProjectTask/WorkBinding/ProjectScope。
- 仅覆盖ACC-04满意度来源切片；不实现其他来源、CLO/SUB消费者、INT连接器或统一批量下载。
- master以V171承载Feature结构；V172仅恢复被历史菜单ID冲突覆盖的既有任务指派权限载体；不修改已执行迁移、旧问卷/回访/电子完工证明或Yudao基础平台源码。
- 权限只落实五个最小键及服务端项目/责任人/字段/文件/租户控制点；验收身份通过正式配置取得所需权限。

## Task 1：共享契约、V133与后端纵向闭环

- [x] Step 1：确定聚焦验收边界；按用户要求不执行测试先行。
- [x] Step 2：实现API、DO/Mapper和领域服务最小闭环。
- [x] Step 3：接入PLT、PROJ和Outbox/Quartz。
- [x] Step 4：来源分支实现并验证V133；master选择性集成时重排为V171。
- [x] Step 5：运行聚焦后端验证和构建。
- [x] Step 6：提交Task 1并更新检查点。

## Task 2：前端与一次真实Chromium闭环

- [x] Step 1：按收益优先口径不执行前端测试先行，只锁定用户可见的正向闭环。
- [x] Step 2：实现API与页面最小闭环。
- [x] Step 3：运行前端聚焦静态检查和`build:local`。
- [x] Step 4：准备正式运行环境。
- [x] Step 5：运行一次真实Chromium纵向验收。
- [x] Step 6：形成Implementation Done整改候选。

Task精确文件、命令和验收条件以唯一Technical Plan为准。Task 1未通过不得进入Task 2；两个Task全部完成只允许申请一次Feature Implementation Done裁决。

## Task 3：master选择性集成与复验

- [x] 在F-ACC-001基础上集成满意度、归档和统一异步导出，不接收旧源推断或外部连接器；迁移收敛为V171～V172。
- [x] 在当前master复核迁移、权限、幂等/并发控制、后端、前端类型和生产构建。
- [ ] 在当前master完成真实MySQL与Chromium复验并申请独立Implementation Done裁决。
- [x] 更新Requirement矩阵和DU回执，保持`IN_PROGRESS / NOT_ESTABLISHED`。

> 检查点（2026-09-02）：代码回执=`b3e7c76e`；当前Gate=`IN_PROGRESS / NOT_ESTABLISHED`；已通过=Feature契约20项、27模块依赖构建、后端117项适用测试（8项MySQL跳过）、前端类型检查与生产构建；阻塞=当前master真实MySQL/Chromium与独立Done裁决未完成；下一步=补齐运行证据并申请独立裁决。
