# 来源代码复核（批次 3）

只读对照 2026-10-26 的《问题跟进.md》及当前工作树实现，覆盖 TF-041/043/046/049/050/051/058/060。项目任务工作区只内嵌 TF-041/043 对应组件，没有割接组件映射；割接反馈按当前 `/pms/cutover/tasks/:taskId` 详情入口核查（`yudao-ui/yudao-ui-admin-vue3/src/router/modules/remaining.ts:73-76`，从业务入口打开任务的路由见 `.../views/pms/cutover/components/CutoverBusinessEntry.vue:29-35`），未以旧独立页面替代当前入口判断。以下结论不是现场复现或生产数据核验。

## TF-041｜硬件安装地址或物理位置无法更新

- 当前入口：项目任务工作区将模板路由 `/pms/engineering/execution/imp-installation` 内嵌为 `InstallationWorkbench`，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectFlowPanel.vue:602-610`；工作台提供“维护安装地址”动作，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/installation/ProjectInstallationWorkbench.vue:20-23`。
- 现状判断：已存在安装记录被设计为只读（`readOnly` 对已有 `form.id` 为真），见 `.../ProjectInstallationWorkbench.vue:168`。重新维护从 `openDevices` 建立新表单，并把一致的当前地点带入，见同文件 `215-246`；保存走 `createInstallationBatch`，追加关联设备的安装记录，见 `273-291`。这保留安装历史，未见允许原地覆盖历史的实现缺陷。错误位置应通过“维护安装地址”追加新的当前安装记录；若该入口缺失或新记录没有成为当前有效记录，才需进一步修复。
- 需现场数据：项目ID、设备ID/序列号、当前及历史安装记录ID/版本/状态、操作者权限、按钮可见性、提交请求及保存后列表。重点核对设备当前安装记录选择规则与所新增记录，不要只确认旧记录仍显示。
- 验收边界：现有 `projectInstallationWorkbench.runtime.spec.ts` 是组件运行时验证，不等于真实项目浏览器验收；需按具体设备验证录入新地点、保存刷新、历史仍可读且当前地点更新。

## TF-043｜业务联调候选设备少于项目设备清单

- 当前入口：项目任务工作区将 `.../imp-joint-test` 映射为 `JointTestWorkbench`，实际导入的是 `engineering/joint-test/with-device-logs.vue`，见 `ProjectFlowPanel.vue:607-609,348`；该页面的设备字段使用 `ProjectDeviceSelect`（`with-device-logs.vue:146,333`）。选择器调用设备档案分页并传 `selectionProjectId`，见 `yudao-ui/yudao-ui-admin-vue3/src/components/ProjectDeviceSelect/index.vue:1-18`。
- 现状判断：服务端对该参数切换到项目设备候选查询，`ProjectDeviceSelectionService.getPage` 传入序列号、名称、型号、合同筛选，见 `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/ProjectDeviceSelectionService.java:34-42`；候选SQL取该项目设备并联合项目合同号下设备，见 `pms-module-asset/src/main/resources/mapper/device/ProjectDeviceSelectionMapper.xml:4-31`。目前没有证据表明它和项目清单的定义应完全相同：合同设备并入、项目清单查询范围/状态、数据权限可能不同。不能仅据“少于”判断是前端漏项。
- 可执行判断：用现场项目编号和缺失序列号分别查询项目清单、选择器请求及响应；比对项目归属、合同号、租户、分页与筛选。若符合项目候选SQL范围但响应缺失，查查询/数据问题；若是工程上应当可选但既不属于项目也无关联合同，则先确认项目设备关联或候选范围规则，不要直接放宽查询。
- 需现场数据：项目ID、缺失设备ID/序列号、当前筛选词与页码、项目清单请求/响应和设备项目/合同关联。候选服务已有 `ProjectDeviceSelectionServiceTest`、`ProjectDeviceSelectionMysqlTest`，它们不能替代本项目缺失设备的现场对照。

## TF-046｜业务联调无法下载 DMC

- 当前入口：内嵌联调页是 `engineering/joint-test/with-device-logs.vue`。代码显示其设备选择和日志文件上传（`.../with-device-logs.vue:146,261-264`），工作区组件映射见 `ProjectFlowPanel.vue:607-609,348`。在该联调入口中未定位到 DMC 下载动作或 DMC 文件契约；仓库相关代码搜索也未找到可据以归因的 DMC 实现。
- 判断：现有证据不足以判定下载缺陷或明确修复点；“DMC”可能指另一个文件/功能。保持 `BLOCKED_BY_DATA`，不要改联调日志下载或猜测 DMC 含义。
- 需现场数据：项目/任务ID、DMC完整名称及预期来源、下载按钮所在页面/截图、文件名、失败请求URL/状态码/响应体和发生时间。先确认是否确为当前项目任务工作区。

## TF-049｜割接等级评估保存超时

- 当前入口：割接任务详情 `task-detail/index.vue` 在 P2 展示评估组件（`yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/task-detail/index.vue:18-27`）；保存组装任务版本、评估版本、幂等键并调用 `saveCutoverAssessment`，见同文件 `234-240`。客户端全局请求超时为 30 秒（`yudao-ui/yudao-ui-admin-vue3/src/config/axios/config.ts:19`），因此截图中的 `timeout of 30000ms exceeded` 本身只说明前端等待超时，不证明保存失败或业务代码缺陷。
- 现状判断：保存服务涉及上下文读取/锁定及评估落库（`pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutoverTaskApplicationService.java:153-205`）；静态代码无法解释某次请求为何超过30秒，也不能判断超时后是否已提交。前端已有超时后保留填写并以原请求幂等信息重试的测试（`task-detail/assessment.spec.ts:63-73`）。
- 可执行判断：以现场 taskId、taskVersion、assessmentVersion 和幂等键关联网关及应用日志，确认请求是否到达、耗时卡点、事务提交结果及重试结果；若服务端已提交，应先刷新状态，不能重复创建/提交。只有定位到稳定慢点或死锁再修服务/查询。
- 需现场数据：任务ID、账号、时间戳及时区、浏览器网络请求/响应、服务端 trace 和数据库锁/慢查询记录、超时后任务评估版本与审计结果。

## TF-050｜割接方案驳回重提后不能推进

- 当前入口：割接任务详情中的 `CutoverPlanPanel`（`task-detail/index.vue:35-41`）。已驳回方案由服务端 `allowedActions` 决定是否显示修订/提交；`revise` 以当前 `planRevisionId` 发起 `reviseCutoverPlan`，再刷新，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverPlanPanel.vue:228-230,556-570`。
- 现状判断：组件测试已覆盖驳回方案通过“修订”创建后续草稿、保留原任务和修订原因，再由服务端动作决定可提交（`yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/workbench/cutoverPlanComponents.spec.ts:662-685`）。未发现足以支持普遍代码故障的证据；这条路径依赖原任务、当前方案修订和审批事实关联，不能把新方案视为独立任务或直接改状态绕过状态机。
- 可执行判断：复核原任务详情的 `currentStage/taskVersion`、旧方案 `planRevisionId/revisionNo/status`、审批实例拒绝结果、修订API响应及刷新后的 `allowedActions`。若后端返回 REVISE/SUBMIT 已禁用或拒绝，依其实际状态/业务码定位；若服务端允许而页面未显示/执行，则再查前端。
- 需现场数据：任务ID、驳回审批实例ID及原因、被驳回与重提方案revision ID、提交/修订请求及响应、账号权限和发生时间。

## TF-051｜License备份上传每个材料槽位只允许一个文件

- 当前入口：风险检查清单的人工材料入口位于 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverChecklistField.vue:56-74`；`LICENSE_BACKUP` 是已有风险检查矩阵项（`pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/configuration/CutoverRiskMatrixRules.java:28,35`）。该字段上传绑定到单个清单项的 `MANUAL_EVIDENCE` 引用，而非多文件集合。
- 现状判断：当前契约按单项单个当前材料引用/版本工作；一次选择一个文件属于现有控件能力。反馈是否要求“一个槽位关联多个不同文件”尚不明确，仓库证据也未显示该场景已获批准。不得将一个文件的新版本当作多个独立文件，或在未确认需求/历史保护契约前改动引用模型。
- 可执行判断：先以截图和实际操作确认单文件重复上传是否成功、后上传是新版本还是替换引用，并确认License文件是否必须逐个保留；若业务确认同槽多文件，再按独立引用和可撤销关联设计评估规格/工程范围。
- 需现场数据：任务ID、清单stableItemKey/itemId、材料槽位用途、文件数及文件名/类型/大小、逐次上传响应、重开后引用列表，以及是否明确要求并列保留多个License文件。

## TF-058｜割接任务设备产品型号疑似错误

- 当前入口：割接任务详情的设备序列号表展示任务冻结设备范围，再按 `deviceId` 调用档案详情读取产品编码、型号与版本，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverTaskDevices.vue:5-10,22-48`。方案编辑又按设备ID补齐档案中的型号，但校验返回设备ID与序列号匹配后才采用，见 `CutoverPlanPanel.vue:299-316`。
- 现状判断：静态映射没有发现把一台设备型号写到另一台设备上的明确代码错误；详情读取失败时页面显示 `—` 并提示部分信息暂不可读，不会把异常请求误作正确型号。提报人未给产品编码/期望型号，无法区分档案主数据错误、任务冻结设备绑定错误或展示错误。
- 可执行判断：用同一设备ID/序列号比对资产档案详情API、割接任务冻结设备scope、任务详情表和编辑后保存的方案明细。若档案API本身不符，核查权威设备来源记录；若设备ID与序列号不一致，检查冻结scope来源；若接口正确而页面错，再修映射。
- 需现场数据：项目/任务ID、设备ID、序列号、当前显示型号、期望型号、产品编码、档案API响应以及原发货/设备来源记录。

## TF-060｜填写后仍提示客户保障安排未填

- 当前入口：割接详情方案面板编辑并先保存草稿，再提交（`CutoverPlanPanel.vue:449-480,556-561`）。服务端客户保障策略在标准在线方案提交时检查解码后的 `supportArrangements` 是否至少有一条 `CUSTOMER`，为空才返回截图所示“客户保障安排尚未填写”，见 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanContactPolicy.java:42-52`。前端已有辅助校验会提示缺少客户/一线角色或行字段，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/shared/cutoverPlanValidation.ts:1-19`。
- 现状判断：错误文本对应服务端解码后的保障安排中没有客户角色行；单凭“填完”无法证明该行属于当前任务/当前方案版本并已成功保存。当前方案规范化也会去除首尾空格并保存输入（`CutoverPlanPanel.vue:477-480`），所以需核对重开草稿中的行及提交请求。没有足够证据认定服务端拒绝了已保存的客户行。
- 可执行判断：对比编辑前端的 `supportArrangements`、保存请求/响应、保存后重新读取的当前 `planRevisionId/planVersion` 及客户角色行、最终提交请求。若保存后读取缺行，追查草稿保存或查询聚合；若保存有行而提交缺行，追查客户端请求版本/内容；若服务端收到该行仍报“客户保障安排未填写”，再定位服务端解码/版本关联缺陷。不要放松提交校验。
- 需现场数据：任务ID、账号、当前方案修订ID/版本、客户安排行的角色/姓名与保存回包、重开读取结果、提交请求/响应及发生时间。

## TF-044｜show tech-s 日志中的版本信息未识别

- 当前入口：项目详情内“配置Log”页签由 `ProjectDeviceLogsPanel` 承载，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue:633-641` 及 `.../components/ProjectDeviceLogsPanel.vue:1-28`；导入服务调用设备日志拆分 API，见 `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogArchiveService.java:107-112`。日志归档后另起解析任务，解析语义版本只读 `device.software.version`，见 `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceLogSemanticFacts.java:7-18`；归档读取实际提交的 parser releaseVersion 和语义结果，见 `.../service/configurationlog/DeviceArchivedLogParsingService.java:48-52,194-216`。
- 现状判断：本地服务不从原始文本推断版本，也不定义 show tech-s 的提取规则；实际识别取决于当次 DAC 拆分/已发布解析版本结果。未提供人工构造文件样本和解析release，不能判定解析规则缺陷。若拆分已归档但 `device.software.version` 缺失，应查对应解析任务使用的已发布 release 和结构化结果；若文件在拆分阶段即拒绝，应查 split endpoint 响应。不要在本地服务加未经样本验证的正则或猜测映射。
- 需现场数据：脱敏原文件及SHA-256、上传格式、设备序列号、归档文件ID/导入批次、DAC解析任务ID与releaseVersion、结构化 `semanticResult` 中版本事实状态/值、对应日期的 DAC日志。两个解析阶段要先区分清楚。

## TF-045｜第四份配置 Log 含序列号仍报缺少序列号

- 当前入口：项目“配置Log”上传最终进入 `DeviceLogArchiveService.archive`，调用 DAC 拆分服务，随后按拆分结果的每设备序列号匹配本项目设备；见 `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceLogArchiveService.java:107-151`。split gateway 把 DAC 422 `DEVICE_SERIAL_MISSING` 映射为“日志块缺少设备序列号”，见 `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/DacDeviceOpsLogSplitGateway.java:42-57`。
- 现状判断：反馈原句与 DAC 返回的结构化错误码完全对应，说明失败发生于多设备文本拆块/序列号抽取，不能仅凭整份文件中出现序列号证明对应的第4块具有唯一可识别序列号。业务服务没有擅自补造序列号，并且只在全部块匹配后归档，避免部分导入。未见可由现有源代码直接确定的解析缺陷；DAC拆分源规则和现场格式是待核对象。
- 可执行判断：用实际第4块和提交时 `format`/解析版本复现 DAC split；核查被识别的块边界、唯一序列号抽取结果、错误回包 `block`。若第4块有受支持格式的序列号但 split release 未识别，修对应 DAC split 规则；若原始串在边界/格式上无法唯一归属，需先补足输入数据，不能自动继承前三块序列号。
- 需现场数据：四个文件原顺序、每份脱敏原文和哈希、上传文件名/格式、导入 `sourceKey`、项目/设备ID及序列号、DAC拆分请求响应（特别 `block=4`）与版本。不要重传为新请求以覆盖原导入证据。

## TF-032｜提交审批提示工程管理部方案复审授权未配置持有人

- 当前入口：项目详情中的实施方案工作台使用 `solution-reviewed/index.vue`（`yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue:592,641`）；页面 `submitReview` 打开/提交分级审批组件，见 `.../engineering/solution-reviewed/index.vue:517` 和 `.../SolutionTieredReviewDialog.vue:57-75`。
- 现状判断：后端按锁定的方案审核级别分支解析审批人：普通级别不解析工程管理部候选，重大级别才调用唯一 `ENGINEERING_REVIEW_PERMISSION` 持有人解析，见 `yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/solutionreview/SolutionReviewBpmService.java:93-109`；无持有人会精确报“工程管理部方案复审授权未配置持有人”，见 `.../SolutionReviewBpmGuard.java:114-118`。因此错误仅对已冻结的重大复审判定是预期配置阻塞；若该方案应走普通审核，则需检查冻结 `reviewLevel`、策略事实及是否误套用授权；不能为了通过而授予猜测对象。
- 可执行判断：读取该方案 `solutionId/version/status` 对应冻结策略记录 `reviewLevel/evidenceJson`、项目来源字段/规则命中、审批流程定义和租户下该权限持有人数量。确认重大方案后由授权管理者配置唯一实际持有人；普通方案出现该错误则追查级别快照与BPM实际分支。
- 需现场数据：项目/方案ID及版本、工作区账号/租户、审批提交请求的 processDefinitionId、冻结审核级别和策略依据、权限查询的持有人数量及当时生效时间、完整业务错误码/日志。现有 `SolutionTieredReviewServiceTest`、`SolutionReviewPolicyServiceTest` 不替代现场权限与策略数据核验。

## TF-034｜新增方案审批通过，原实施方案任务未完成

- 当前语义：此项不能再按“新方案ID是否自动替换旧ID尚未确认”处理。F-PROJ-007已引用需求方批准的Q-TASK-BUSINESS-AUTO-LINK-001；该决议要求同项目同类记录中已审批通过等定格记录自动纳入，完成事实取最新完成记录，草稿、驳回、撤销等不纳入，见 `tasks/features/F-PROJ-007.md:53-55`、`docs/decisions/open-questions.md:92-93` 和 `docs/design/04a-template-compilation-runtime.md:418`。
- 当前不是固定具体solution ID，也不是“只取最新已批准”：`SolutionApprovalCompletionProvider.associationCandidates` 校验绑定目标类型后，按租户、项目动态分页取候选；`ProjectTaskBusinessAssociationService.reconcile` 将Owner返回的候选对象ID集合与当前活动关联对齐。因此新方案可自动增加关联，旧记录不会因新ID自动覆盖或删除。结果事件处理器执行 `synchronize` 后再用Owner事实调用任务状态机；常规业务完成扫描也执行同一顺序，见 `.../SolutionApprovalCompletionProvider.java:89-95`、`.../ProjectTaskBusinessAssociationService.java:42-46,104-168`、`.../ProjectOperationNodeResultProcessor.java:61-62`、`.../ProjectBusinessTaskCompletionService.java:36-37`。
- 明确实现缺口：SOL候选查询 `SolutionCompletionMapper.page` 仅排除状态5、6，故草稿、审批中及驳回等其它状态仍被纳入；`load`虽只把已审批且版本匹配（重大方案还须复审通过）的单条记录算作完成事实，但这不能修正不合格候选被关联的问题，见 `.../SolutionCompletionMapper.java:11-15`、`.../SolutionCompletionMapper.xml:5-8`、`.../SolutionApprovalCompletionProvider.java:148-157`。随后 `TaskBusinessCompletionEvaluator.businessFact` 对每条活动关联逐条取同一completion fact并依冻结量词ALL/ANY聚合，没有“最新已完成记录”选择；若旧驳回方案仍在活动链接中，ALL规则会被其false事实阻塞，即使新方案已审批。该行为与已批准契约不符，不应归因为新ID缺少人工关联或改写历史状态。
- 可执行判断：代码侧应核对/实现Owner候选资格过滤及按契约选取最新完成事实的读取语义，保持租户、项目、版本、重大复审和锁定复验；既有关系和完成历史不自动重写。现场先看 taskId、当前 executionContract 的目标类型/绑定策略、活动 link objectId/factVersion、旧新方案状态/批准版本与复审事实、冻结 completion rule 量词，以及结果Outbox的投递/重评估结果。现有 `SolutionApprovalCompletionProviderTest`、`ProjectTaskBusinessAssociationServiceTest`、`TaskBusinessCompletionEvaluatorTest`可说明各局部行为，但不能代替真实项目的关联及自动完成验收。
## TF-065｜割接报备缺少设备类型

- 当前入口判断：当前割接任务工作台可见“设备类型”选择只在 P3 清单匹配面板，见 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/components/CutoverChecklistPanel.vue:31-49`，提交清单也提交 `deviceTypeCodes`（`.../CutoverChecklistPanel.vue:728-731`）；服务端只接受任务冻结配置的有效值，见 `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/checklist/CutoverChecklistApplicationService.java:1008-1018`。任务创建入口提交的是设备序列号，没有设备类型输入字段；任务在创建时冻结设备范围，见 `.../service/task/CutoverTaskApplicationService.java:226-238,292-307`。在当前 cutover 代码中未找到叫“割接报备”的独立页面/API。
- 现状判断：报告未标明“报备”所指的业务动作，暂不能断定缺的是任务创建输入、清单匹配选择、审批展示或外部报备载荷。当前P3设备类型是由使用者按配置选取并冻结用于匹配，而不是从每个档案设备类型自动带入；不能擅自新增输入字段或虚构类型值。
- 可执行判断：先确认反馈页面/路由、操作按钮和失败请求。若是 P3 生成/提交清单，查 `matchingOptions.deviceTypes`、`deviceTypeCodes`、所选配置 revision 与请求载荷；若是审批窗口，查 sourceSnapshot 设备类型/清单匹配事实；若是未定位的外部报备，则补充外部报备契约和载荷再确定代码归属。
- 需现场数据：cutover taskId、入口URL/截图、具体“提交报备”按钮、操作者和阶段、任务冻结配置revision、设备ID/序列号和类型来源、请求与响应。规则不明确时标记 `BLOCKED_BY_SPEC`，先查清其字段权威来源，不以仓库测试替代业务复现。
