# 项目全流程源／测试界面差异与裁决建议

> 2026-10-25 基准纠错：本报告将当前源分支 e0ae05247 及工作区内容直接当作原源环境基准，未与既有来源核实采用的 4d149f34 分开。LOG-02“恢复长说明”的裁决错误，已撤回；该说明来自后续源提交 3b007dd7f。本文原清单不能再作为“已按原提交对齐”的证明，其他裁决须按明确提交重核。见 [基准纠错记录](baseline-correction.md)。

> 后续32文件已按原源Git提交重核69个片段并处理，最新结果见[逐片段裁决](32-file-hunk-adjudication.md)和[验证回执](32-file-verification.md)。本报告旧“恢复源”建议中涉及这32文件的内容，以新裁决替代。

> 用户再次标记LOG-03页面后，本页已单独补查并修复说明遗漏；LOG-01的后续源catch迁入也撤回。分页/异步解析按实证性能修复保留，明确仍与原源有差异。见[日志档案弹窗纠正](archive-page-correction.md)。

核查日期：2026-10-25。性质：只读核查及裁决建议，未实施修复、重启、权限调整或业务数据变更。

## 结论与优先顺序

存在源功能丢失、交互退化和未经证明应保留的测试侧新增功能。此前项目完成 16/16 任务，仅证明该复验实例走通，不能证明两个系统的全部操作一致。

建议先恢复割接审批专用重验契约、审批用户入口、创建时客户服务等级上下文；再恢复配置 Log 的错误反馈、业务说明与源交互；随后逐项处理附件操作顺序、闭环审批展示及其他表单差异。已有证据的测试修复必须按代码片段保护，不能整文件回退。

“恢复源”在本文均指建议：对齐完整调用契约及用户行为，保留授权、并发、历史和文件引用保护；不授权直接修改历史记录或扩大权限。

## 基线与证据边界

- 源目录：`E:/AICoding/Projects/NPDMS`；测试目录：`M:/AICoding/CodexData/worktrees/853c/NPDMS`。比较的是双方当前工作文件，包含未提交内容，不能只用 Git 提交判断来源。逐文件摘要见 [inventory.json](inventory.json)。
- 扩展后的静态界面范围：项目详情、阶段任务、工程实施、割接、验收、设备关联操作、工勘业务入口、通用业务实体宿主及其可解析依赖，共 555 个生产文件；121 个有差异，434 个一致。完整逐文件清单见 [file-ledger.md](file-ledger.md)，所有差异片段见 [ui-differences.diff](ui-differences.diff)，操作控件与条件见 [control-inventory.json](control-inventory.json)，浏览器覆盖见 [operation-coverage.md](operation-coverage.md)。依赖分析不是运行时路由覆盖证明。
- 更宽的发现扫描覆盖 5,070 个生产文件，726 个存在差异，包含大量迁移与非项目业务；没有将这 726 个全部裁定为界面缺陷。发现清单见 [discovery-inventory.json](discovery-inventory.json)。
- 真实浏览器源端 19191／59191，账号管理员，项目 `2110018733130579984`；测试端 19086／59286，账号 z01010，项目 `84574`。双方均逐项点击 23 个业务入口及 16 个已完成阶段任务，保存只读快照。源端序列号入口首次因两个同名按钮导致自动化定位失败，已重新打开确认；该定位错误不算产品缺陷。
- 入口点击和即时 DOM 快照仅证明该只读入口可检查；部分快照仍含加载态，不计为数据加载通过。不同账号、不同项目和不同数据不能直接用于裁定权限、数量、空列表或字段值回归。新增、编辑、上传、撤销、审批等写操作本次按代码比较，未在业务记录上重做。
- 本次未重新逐表核对数据库模板／视图／表单／业务规则内容，也未证明运行中的后端冻结包等于当前所有工作文件。下述后端差异是当前代码事实，不能冒充线上故障复现。不能据此宣布“所有状态、所有角色、所有配置均已验收”。

## 差异与逐项建议

证据文件均可在逐文件清单及差异附件按文件名定位。优先级：P0 为关键业务契约；P1 为丢失功能或明显操作退化；P2 为展示或待证明的新增能力。保留项不等于批准新增业务。

### 配置 Log：必须分别核对三个入口

| 编号 | 源与测试差异／影响 | 建议裁决 | 证据 |
|---|---|---|---|
| LOG-01 / P1 | 日志档案上传：源捕获失败并写入 `uploadError`，提示“本次未确认归档”；测试删除 catch，仅保留 finally，页面错误区失去这一反馈来源。 | 恢复源失败反馈，保留实际错误信息及可重试状态；不能把失败显示成成功。 | `asset/device-log-archive/index.vue` 的 `uploadFile` |
| LOG-02 / P1 | 配置调试源说明区明确区分“记录附件”和任务要求的“配置Log”，指向上传含 SN 的完整日志／命令采集，要求等待归集后刷新校验；测试替换成笼统采集说明。两边真实页面已确认不同。 | 恢复源说明。业务记录附件不能被说明为可替代配置日志证据。 | `engineering/configuration/with-device-logs.vue` |
| LOG-03 / P2 | 档案列表源无本地分页；测试新增 20／50／100／全部分页及编码、20MiB、拆分等技术段落。布局与源不同。 | 已有性能修复保留；技术段落不作为默认应保留增强。建议恢复源业务说明和布局语义；分页单独按性能必要性裁决，不能整页回退。 | `asset/device-log-archive/index.vue`；[既有加载性能记录](../project-template-transfer-20261024/config-log-loading-performance.md) |
| LOG-04 / 保留 | 测试先展示档案行，再按当前页获取解析状态／轮询，增加请求序号和加载反馈；源等待较多解析读取。 | 保留有证据的性能修复；验证快速切页不串数据、失败不阻塞档案列表。 | 同上 |
| LOG-05 / 保留 | 单设备日志抽屉源等待档案与旧日志均结束；测试分别提交结果，显示尚未完成／失败来源并防串设备。 | 保留已验证的加载修复，不恢复为被慢来源整体阻塞。 | `ProjectDeviceConfigLogDrawer.vue` |
| LOG-06 / 非代码回归 | 项目概览 `ProjectDeviceLogsPanel.vue` 两边文件相同。旧“历史配置日志”折叠区仅在旧日志总数大于 0 时显示；源样例有、测试样例无。 | 不能据此裁定组件被合并丢失。核对同类旧日志数据及读取结果，不能补造旧日志或无条件显示历史。 | `ProjectDeviceLogsPanel.vue`；双方配置Log快照 |
| LOG-07 / P1 | 配置记录附件从源 `UploadFile` 改为测试 `NativeAttachments`，无 entityId 时要求先保存再重开上传。 | 恢复源操作连续性；文件仍走有效的原生引用契约，不能回退为任意 URL 写入或改历史。 | `engineering/configuration/index.vue`、`attachment/NativeAttachments.vue` |

### 割接：源功能丢失及交互退化

| 编号 | 源与测试差异／影响 | 建议裁决 | 证据 |
|---|---|---|---|
| CUT-01 / P0 | 源最终角色审批调用 `requireCurrentForApproval`，带任务／实例／版本／节点／设备快照上下文；测试删去该专用链路，改成工作流决定后调用通用 `requireCurrent(task)`。 | 恢复源端整条专用重验契约及调用时序，再验证原候选人、非候选人、过期版本和变化的准备快照；不能用通用成功路径替代。尚未据此证明运行时已绕过校验。 | `CutoverApprovalApplicationService.java`、`CutoverExecutionReadinessPolicy.java`、`CutoverReadinessPort.java`、`CutoverOwnerAdapters.java` |
| CUT-02 / P0 | 源 `CutoverApprovalReadinessAccessApiImpl` 校验当前租户／演员、P5 APPROVING、任务与项目、冻结准备快照、审批根版本／节点、当前角色候选资格及冻结设备精确集合；测试缺此实现。 | 与 CUT-01 一起恢复，不只补同名方法或删资格校验。 | [后端差异附件](cutover-contract-differences.diff) |
| CUT-03 / P0 | 源准备重验的公共比较包含 projectId；测试通用比较删除该项目一致性比较。 | 恢复项目一致性比较；保留原事务及其他准备条件。 | `CutoverExecutionReadinessPolicy.java` |
| CUT-04 / P1 | 创建割接时源选项目后即读取 `getCreationProject` 的完整 `customerServiceLevelContext`，失败提示重选；测试删除请求、部分序号与项目一致性保护，项目上下文相同即可返回。 | 恢复选择项目即加载服务等级、失败可重试和防串项目行为。不能依赖选择设备后才偶然补齐。 | `cutover/task-create/index.vue` |
| CUT-05 / P0 | 对应 API 类型由含完整等级事实的 `CreationProjectContext` 退为普通项目上下文；后端从 `projectContexts.inspect`＋客户事实解析退为较弱展示组合。 | 前后端一起恢复源客户事实契约；不要只补一个展示字段。 | `api/pms/cutover/task.ts`、`CutoverTaskCreationQueryService.java` |
| CUT-06 / P1 | 源无割接查询权限但有 BPM 查询权限时显示“割接审批入口／查看我的审批待办”，并跳过列表查询；测试删除入口及查询条件，直接请求列表。 | 恢复源审批入口及无权限不发查询；不得给审批人补割接查询权解决。 | `cutover/tasks/index.vue` |
| CUT-07 / P2 | 源 `STANDARD` 显示“标准服务”；测试显示原始编码。 | 恢复中文标签；保留未知值兜底。 | `CutoverApprovalPanel.vue` |
| CUT-08 / P2 | 检查单字段与面板源“填写说明与来源”可折叠；测试平铺说明，占用空间。 | 恢复源折叠交互，字段值／必填／来源内容不变。 | `CutoverChecklistField.vue`、`CutoverChecklistPanel.vue` |
| CUT-09 / P2 | 已提交闭环源用只读结果文本展示正常／异常／未记录及回退内容；测试保留禁用单选和文本框。 | 恢复源历史结果的文本展示；保留不可编辑约束。 | `CutoverClosureForm.vue`、`CutoverClosurePanel.vue` |
| CUT-10 / P2 | 采集证据详情源按有无证据及提交状态控制初始展开；测试始终展开。 | 恢复源展开条件，不改证据内容。 | `CutoverClosurePanel.vue` |
| CUT-11 / 保留 | 测试对已完成割接审批任务增加严格限定的 P6 `closureOnly` 入口；其他阶段和历史仍只读。 | 保留此前真实复验确认的归档修复。不能因对齐源而重新封死 P6。 | `TaskBusinessPanel.vue`、`cutover/task-detail/index.vue`；既有复验报告 |

### 附件与业务表单：逐个裁决，不能一概称为测试修复

下列原生附件组件新增文件版本／引用、交付件归集、撤回、待归集重试、旧 URL 只读；其中“先保存再重开”的操作顺序与源有实际区别。建议对齐源连续办理体验，同时保留原生文件安全、历史及服务端规则。没有测试来源证据的额外交付件操作不默认保留。

| 编号 | 业务／界面差异 | 建议裁决 | 证据 |
|---|---|---|---|
| ATT-01 / P1 | 配置调试：日志附件上传替换、保存后重开门槛、保存后同步记录版本／状态。 | 按 LOG-07 恢复连续办理，核对任务归集，不混同配置Log。 | `configuration/index.vue` |
| ATT-02 / P1 | 业务联调：原证据上传换为同样的原生附件区及先保存门槛。 | 对齐源上传／保存顺序；不改变联调通过动作。 | `joint-test/index.vue` |
| ATT-03 / P2 | 外部采购：表单和详情新增原生附件，列表增加附件变动后的版本更新。 | 新能力待来源裁决；历史只读保留，未经证实的写入口不应默认保留。 | `ext-proc/*Dialog.vue`、`ext-proc/index.vue` |
| ATT-04 / P2 | 外包申请：同类新增附件区、详情展示和版本回写。 | 同上，逐操作检查保存、撤回、重试权限，不能凭共用组件通过推定。 | `outsource/*Dialog.vue`、`outsource/index.vue` |
| ATT-05 / P2 | 物料领用：同类新增附件区、详情展示和版本回写。 | 同上。 | `material-req/*Dialog.vue`、`material-req/index.vue` |
| ATT-06 / P2 | 物料换货：新增换货原因附件及详情附件、备注输入、版本回写。 | 附件及备注均为测试侧功能增量，分别查来源；未经证明不作为保留修复。 | `material-exch/index.vue`、`MaterialExchangeDetailDialog.vue` |
| ATT-07 / P1 | 工程简报：源上传组件换为 `AdditionalNativeAttachments`，先保存门槛及原生材料管理。 | 恢复源连续操作；额外材料动作待来源裁决。 | `briefing/index.vue` |
| ATT-08 / P2 | 旧验收页面：源无对应新增附件区，测试加入 `AdditionalNativeAttachments`。 | 不默认保留新增写能力；既存历史文件仍应可读。 | `acceptance/acceptance/index.vue` |
| ATT-09 / P1 | 交付件检查清单：源 URL 上传替换为 `ChecklistAttachments`；新增版本、旧附件只读；保存后源关闭弹窗，测试创建并重读、留在弹窗继续上传；“取消”改“关闭”。 | 恢复源保存／关闭语义或以已证实的文件生命周期要求明确调整；不能默认为无害重构。 | `deliverable-checklist/index.vue`、`ChecklistAttachments.vue` |
| ATT-10 / P2 | 到货签收：新增当前引用恢复、加载／错误重试、保存归集、撤回旧材料、交付保存期间禁用。 | 原生材料扩展逐项追来源；首次上传不发送 null fileId 是已复验修复，保留。 | `engineering/arrival/index.vue`、`saveArrivalReceipt.ts` |
| ATT-11 / 保留 | 工期变更审批附件：测试缺引用 key 时显示历史信息缺失，而不再直接拿文件 ID 查询。 | 保留安全兜底，不伪造引用、不解锁历史。 | `duration-change/ApprovalDetail.vue` |
| ATT-12 / 保留 | 实施方案脚本、需求分析章节、准备项、到货证据、割接方案及闭环材料补充 FileId 精度类型与空 artifact 保护。 | 保留；对齐源界面不等于恢复无效文件请求。 | `saveSolutionScripts.ts`、`RequirementAnalysisSectionCard.vue`、`PreparationItemDrawer.vue`、割接证据组件等 |
| ATT-13 / 保留 | 统一文件上传与引用列表新增无 ID 时发现引用、有效 ID 校验、请求防串对象、上传尝试／代次保护、可配置大小上限。 | 精度、安全和并发保护保留；大小配置作为接口增量独立列账，不声称所有消费页上限都改变。 | `components/PmsFileArtifact/*`、`api/pms/platform/file/index.ts` |

### 项目概览、设备及创建界面

| 编号 | 差异 | 建议裁决 | 证据 |
|---|---|---|---|
| PM-01 / P2 | 项目详情测试新增“产品清单”“实施范围”入口，源无；范围入口依赖项目／树版本上下文。 | 新业务入口查批准来源，未经证明不默认保留；版本上下文校验不得删。 | `project-master-detail/index.vue`、`ProjectAssociatedDataPanel.vue` |
| PM-02 / P2 | 基础信息新增合同关系生效／历史时间、购货方／订单购货方、回款明细、执行单来源、回款计划和设备许可。 | 逐区裁决新增展示，不能以“补数据”名义把额外业务增强混入。源已有合同加载反馈修复及零值正确展示应保留。 | `ProjectBaseInfo.vue` |
| PM-03 / P2 | 继承项目详情由独立描述区改为共享基础信息组件；管理员与非管理员创建来源／原因／时间的显示位置和条件改变。 | 按字段、角色逐项对齐；不能把删除模板行等同字段丢失：项目结束日期仍在共享基础信息中。 | `inheritance/detail/index.vue`、`ProjectBaseInfo.vue` |
| PM-04 / P2 | 项目序列号详情增加“续保记录”页签，源仅设备列表。 | 新增功能待证据裁决；保留真实旧续保记录，不转成当前授权。 | `ProjectSerialPanel.vue`、`DeviceWarrantyRenewalTable.vue` |
| PM-05 / P2 | 实施范围新增合同／产品／数量／单位／确认状态、日期格式及历史字段；未确认分配不再当“当前有效”。 | 展示增量独立裁决；保留确认状态边界，不能恢复未确认分配可作为有效业务事实。 | `commerce/delivery-scope/*`、`DeliveryModuleTable.vue` |
| PM-06 / P2 | 客户联系人增加人员编码、单位、来源、生效区间及历史只读；无 customerId 项目也可读取项目历史联系人。 | 保留历史读取与不可编辑边界；新增字段按迁移证据裁决，不能给历史人员补主档权限。 | `customer/contacts/index.vue` 及 API |
| PM-07 / 保留 | 项目成员新增人员编码／授权确认，未确认或无系统用户的历史成员不可直接维护；普通成员表单不再用 null 用户查询。 | 保留授权隔离；新增展示字段不据此视为业务批准。 | `ProjectMembersPanel.vue`、`OrdinaryMemberForm.vue` |
| PM-08 / P1 | 主项目列表及继承项目列表创建向导改变：合同号选择变合同 ID；项目名跟随合同来源只读；新增销售订单选择、来源指纹、重载；来源未完成会阻止下一步／提交，源较宽松。 | 属于业务输入与创建前置条件变化，必须连后端契约查来源，不能按普通数据补齐保留；也不能只删前端限制绕过后端规则。 | `projects/index.vue`、`inheritance/projects/index.vue`、`ProjectCreationSource.vue`、`useProjectCreationSource.ts` |
| PM-09 / P2 | 创建向导“客户主档”改为“最终客户主档”，新增订单项目／购货方／执行单／市场系统等来源摘要；创建原因丢失源“BR-2 脱离 CRM/ERP 必填”提示。 | 恢复源业务说明；新增字段与来源语义按批准来源逐项裁决。 | 同上 |
| PM-10 / P2 | 新增旧系统资料面板、原字段详情、来源项目切换、父子／引用导航、业务承接及保留缺项原因。 | 历史只读证据必须保留；界面入口／整套新能力尚不能仅凭存在就判为应保留测试修复。 | `ProjectLegacyDetailPanel.vue`、`ProjectLegacyRecordDetails.vue` |
| DEV-01 / P2 | 设备列表新增状态变更／删除按钮，操作列变宽；设备工作台增加状态变更，仍有各自权限条件。 | 属于业务操作增量，须证明来自获准测试修复，否则建议从本次对齐范围排除新增入口；不执行删除，也不放宽权限。 | `asset/device/index.vue`、`DeviceWorkbenchDialogs.vue` |
| DEV-02 / P2 | 设备工作台增加“发货事实”“主附SN关系”页签。 | 新增只读关联能力待来源裁决；与源不同必须列出，不称丢失功能。 | `DeviceShipmentPanel.vue`、`DeviceContractRelationshipPanel.vue` |
| DEV-03 / P2 | 出厂版本加入来源原字段历史；位置加入来源安装历史及原地址快照；在网版本加入维护、登记历史与前后比较、项目日志档案入口。 | 分开裁决历史读取与当前软件版本写入，不能将只读迁移授权扩展为维护授权。 | `DeviceFactoryPanel.vue`、`DeviceLocationPanel.vue`、`DeviceNetworkVersionPanel.vue` 及来源历史组件 |
| DEV-04 / 保留 | 设备维保资料增加错误反馈／分页／防串请求；解析日志记录增加卸载失效序号；位置日期规范显示、不拼出 undefined 名称。 | 可保留工程安全和真实缺陷修复；不将其当作新增业务批准。 | `DeviceWarrantyPanel.vue`、`DeviceParsedLogRecords.vue`、`DeviceLocationPanel.vue` |

### 通用业务页、路由与最终审批展示

| 编号 | 差异 | 建议裁决 | 证据 |
|---|---|---|---|
| HOST-01 / P2 | 通用实体列表增加按字段筛选／重置，TEXT 为 LIKE，其他支持类型为 EQ；实体页增加删除及删除回执恢复。 | 新业务操作／查询能力单独追来源，未经证明不默认保留；不得删后端历史引用保护换取一致。 | `BusinessEntityList.vue`、`businessListFilter.ts`、`BusinessEntityHost.vue`、`useBusinessEntity.ts` |
| HOST-02 / P2 | 通用实体宿主新增默认交付件区，可输入交付件类型，上传／重试／改标题／删除／下载／分页，和原 DeliveryPanel 并存；有 allowedActions 时默认只读。 | 这是宿主行为增量，会影响多个业务视图；应核对每个模型的正式交付件契约。建议未证实增量不保留为默认通用能力，历史文件和引用仍保留。 | `DefaultBusinessDelivery.vue`、`DefaultDeliveryUpload.vue`、`DefaultDeliveryRecords.vue` |
| HOST-03 / P2 | 新增独立 `ProjectBusinessPage`／项目业务 API 体系及工勘入口；带结构化地点、项目要求完成日期、保存后执行业务动作确认及交付件管理。 | 新业务接入面不能混入源界面对齐；查正式来源后逐功能裁决。不能用新工勘页替换源阶段任务页而宣称相同。 | `components/ProjectBusiness/*`、`business/site-survey/*`、`api/pms/platform/business/*` |
| HOST-04 / P2 | 剩余静态路由删除源割接任务列表隐藏直达根路由，增加工勘／交付件入口。 | 恢复源割接直达路径的可靠解析，先查动态菜单是否承接，避免新增重复路由；新入口另裁决。 | `router/modules/remaining.ts` |
| HOST-05 / P1 | 正常闭环审批源中文“正常闭环／服务经理审核／材料审核／同意／驳回”、格式化日期及折叠技术证据，测试退成原始 NORMAL／节点 Key／结果编码／原始日期，技术标识直接平铺。 | 恢复源业务化展示、日期及折叠交互；审批实例／候选人证据保持原件。 | `project-closure/NormalClosureProcessView.vue` |
| HOST-06 / 保留 | 通用内容历史编辑补类型适配；表单新增 losslessNumbers 输入模式；审批标签映射仅类型写法变化。 | 类型安全／精度保护保留；新输入模式仅在实际启用的新增业务页另裁决。审批标签映射不算功能差异。 | `ContentHistoryPanel.vue`、`BusinessEntityForm.vue`、`ApprovalPanel.vue` |
| HOST-07 / 非业务差异 | `TaskStateActions.vue` 和权限路由生成包装表达式仅排版不同；部分日期／approver 参数及 FileId 仅类型兼容变化。 | 不制造业务缺陷，不恢复格式来冒充修复。 | 文件差异附件 |

## 四类重点业务及其关联操作

| 业务 | 本次发现／一致部分 | 不能省略的后续验证 |
|---|---|---|
| 实施方案 | 主要专业视图已与源相同；脚本引用存在 FileId 及空文件保护差异，应保留。 | 草稿离开、在线文档、脚本上传／版本／下载、发布及阶段绑定；本次未重做写操作。 |
| 需求分析 | 主要业务视图相同；附件事实类型与空 artifact 防护不同，应保留。通用宿主新增交付件能力另列 HOST-02，不能据专业页相同就忽略。 | 动态表单定义、必填、修订／确认、附件及任务结果绑定；数据库定义未在本轮重新取证。 |
| 现场培训 | 本次静态闭包内主要培训及公开确认组件未发现新的源／测试文本差异；双方已打开只读业务入口和完成任务。 | 客户确认、手签／签章、PDF、联办满意度及撤回条件，需要相同权限、相同状态的隔离样例验证。 |
| 满意度调查 | 主要专业页面未发现新的源／测试文本差异；原生已关联结果读取修复须保留，不能恢复审核人按办理人查询。 | 发送／公开评价／签字／达标／归档／审核引用；不向真实客户发送测试数据。 |

## 必须保护的已证实测试修复

沿用已有 [阶段全流程复验记录](../project-delivery-configuration-alignment-20261024/stage-workflow-retest-result.md) 与 [业务操作来源清单](../project-delivery-configuration-alignment-20261024/business-operation-change-origins.md)：P6 限定归档入口、已关联结果的受控读取与材料审核、客户显式关系幂等、首次到货文件 null ID、省略无效引用、真实业务错误保留、日志／合同请求加载反馈及范围查询性能修复。保留的证据须与具体片段对应，不能据此保留同文件内所有新增业务。

## 修复后的逐项验收要求

1. 按本文编号修复，每项记录源片段、保留的测试片段、涉及 API／数据库配置和具体结果；不整文件覆盖。
2. 对 LOG-01、CUT-01～06、HOST-05 先做失败／无权限／过期版本保护验证，再做正常路径。
3. 用独立复验实例分别覆盖草稿、办理中、退回、已完成和历史只读；仍从阶段任务办理。不同角色只使用现有候选资格，不增加权限。
4. 配置Log分别验收项目总档案、设备抽屉、配置记录附件／采集入口：上传失败、慢源、空历史、旧历史、有解析／待解析、分页、切项目、任务归集后校验。
5. 所有附件页逐一验证首次创建→上传→保存→返回→重开、替换版本、失败重试、撤回、下载、只读历史；共用组件通过不代表所有业务页面通过。
6. 扩展到同账号同状态的源／测试页面及数据库配置对照后，才可关闭“全流程界面与功能一致”核查。当前报告列出的发现可进入逐项裁决，但未给全系统无遗漏的通过结论。
