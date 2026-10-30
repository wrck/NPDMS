# 项目模板复制与真实浏览器专项验收

验收日期：2026-09-21，Asia/Shanghai。依据本次用户专项要求，不进入工程实施链，不回写 Feature、DU、PRD 或发布状态。审查代码基线为当前工作树 HEAD `94d9d6aac`。

## 验收判断更正（同日用户复核后）

**撤回本报告对“交付件人工提交、归集和判定功能已验收通过”的泛化结论。该部分未通过验收，不能以项目已结项代替。** 下文保留的是特定路径实际产生的记录，不构成用户正常办理入口可用的证明。

本次验收存在三个覆盖和判定错误：

1. 上传脚本固定使用项目经理 `yuanma / 103`，从“生命周期实例 → 阶段交付件 → 提交与查看”进入；未验收用户常用的“业务中心 → 交付件检查”路径。当前后者调用独立旧检查清单接口，只提供记录的新增、提交、通过等操作，没有接入模板交付件上传弹窗。不能将一个入口的成功推广到其他入口。
2. 交付件弹窗的可写条件限制为 ACTIVE 项目的当前项目经理，服务端也进行同样限制；本次未验证用户实际账号能否提交。具体账号和阻断点尚待实际复现，不擅自将权限限制认定为正确业务规则。
3. 实际操作先完成任务再上传材料，掩盖了 `confirmationRule=TASK` 对任务完成的依赖。用户本次已明确要求判定依据为有效交付件存在，而不是任务完成；现有模板默认规则与这一要求不符。手动另传合成材料也没有验证业务办理页面文档的自动归集。

后续验收应从用户实际入口、账号与未完成任务状态开始，分别证明上传可见且可提交、有效交付件独立判定、业务文档自动归集。当前只读复核并修正本报告，尚未实施产品修复，也没有覆盖或修改旧冻结模板及结项历史。

用户进一步确认三个入口（项目交付件检查、生命周期实例交付件、独立交付件页面）均有无法提交问题。主代理只读发现新建项目 `992203060010 / PJT2026000008` 使用本次副本 `993009001592`，处于 ACTIVE/S1，但 `manager_id` 为空；`992203060009 / PJT2026000007` 也为空。`ProjectDeliverableSubmissionService.detail` 的 `writable` 同时要求 ACTIVE 和 `managerId == 当前账号`，`ProjectDeliverableDialog` 据此直接隐藏表单，`ProjectDeliverableAccess` 在服务端作相同拦截。因此上次预先指派项目经理的验收没有覆盖新建项目的这个阻断条件。没有替用户指派经理、改变权限或修改这些项目。数据库证据为 `review/new-project-entry-blocker.jsonl`。其他模型已用真实浏览器、yuanma/103 只读复现前两个入口，主代理查看截图并核对回执：旧检查页无文件输入；生命周期交付件 `992004200193` 虽允许 UPLOAD，但返回 `writable=false / automaticSource=null / history=[]`，画面只有状态与空历史。证据为 `browser/deliverable-entry-review/yuanma-010-review.json`、`04-yuanma-010-business-new-dialog.png`、`06-yuanma-010-lifecycle-dialog.png`。不能把三个入口都归因于同一个权限条件。

独立旧清单页 `views/pms/acceptance/deliverable-checklist/index.vue` 的新增/编辑弹窗确有 `UploadFile` 附件控件，不能断言它完全不存在上传组件。但它调用 `/pms/acc-deliverable-checklist`，对应 `acc_deliverable_checklist`；其提交服务只把旧清单状态从 0 改为 1，没有提交到模板实例表 `acc_project_deliverable`，也没有调用项目交付件判定。这与用户需要的模板材料提交并非同一条链路，尚未证明该入口可以完成用户要求的提交。

**补充复核完成**：gpt-5.6-sol 对 yuanma/103 和 admin/1 均执行真实浏览器只读检查，主代理核对回执及关键截图。两账号打开项目 `PJT2026000008` 的生命周期交付件均无上传/提交/重检；独立 `/pms/acceptance/deliverable-checklist` 页虽有附件控件，却不展示该项目 29 个模板实例。另一个 `/pms/engineering/execution/imp-deliverable` 归集页也是独立旧业务模型，不能替代模板实例提交。结果索引为 `browser/deliverable-entry-review/entry-review-summary.json`，两账号证据为 `yuanma-entry-review.json`、`admin-entry-review.json`；登录后非 GET 请求均为 0，没有保存、上传或改动用户项目。**三个入口的模板交付件办理均不能按原报告判为通过；本轮完成的是结论纠正与缺陷定位，尚未修复。**

## 1. 结论

**副本项目在特定项目经理/生命周期实例路径下形成了 S1～S6、材料来源、门禁和两节点审批记录，正式正常结项；交付件功能验收及“业务事件完整驱动任务推进”均不通过。** 独立初验、终验报告发布 PASS 后，没有及时唤醒本模板的任务规则；后续另一项正常操作触发重评后才补完成。最终结项不能覆盖这些失败。

业务操作与执行节点已有解耦能力：S3 尚未激活时可以从项目业务入口完成方案审批，同时 S3 任务仍待分配。模板规则继续决定任务与阶段何时完成。该模板只有 5 个业务事实型任务，另有 7 个 PAGE 任务和 2 个原生任务，不能将这 9 个任务的手动提交表述为业务事件自动完成。

浏览器配置、操作及原始证据由另选的 **gpt-5.6-sol** 子代理执行；主代理独立读取数据库、核对冻结模板、代码事件路径、业务历史、审批记录并查看关键截图。主代理的 37 项闭环事实核对全部通过，事件传播专项结论仍为失败。

## 2. 确认的缺陷

### P1：独立验收报告发布缺少现有业务事实型模板的规则唤醒

- 初验 PASS v2 `2101862266776252417` 于 **10:34:33.366** 生效，主代理随后捕获的任务仍为 `IN_PROGRESS`；直到后续操作后 **10:37:01.076** 才完成。
- 终验 PASS v1 `2101863357899272194` 于 **10:37:32.575** 生效。浏览器立即重开任务页，初验已完成、终验仍进行中；随后通过 UI 提交正常的“现场培训”原生任务，终验才于 **10:38:31.337** 补完成。没有手工提交初验或终验任务；两者执行轮次的 `submitted_at/submitted_by` 均为空。
- 发布产生 `ACC.ProjectAcceptanceReportChanged.v1` 和适配后的 `PMS.BusinessOperationResultCommitted.v1`；后者已 `DELIVERED`、重试数 0，发布时没有对应通用规则重评事件。不是简单的浏览器显示延迟或操作事件投递失败。
- [AcceptanceOperationResultBridge.java](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/operation/AcceptanceOperationResultBridge.java) 第 59～62 行只记结果并发布操作结果事件；[ProjectOperationResultFanout.java](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/operation/ProjectOperationResultFanout.java) 第 63 行跳过 `operationContract == null` 的绑定。本次复制的发布版所有任务均无该契约，也没有结果订阅；因此这条事件链无法唤醒既有业务事实型完成规则。

影响：支持的既有模板可能在业务已完成后一直停留在进行中，依赖无关动作才能继续。需要补全这类独立 Owner 结果到规则重评的连接，保留租户、权限、幂等及事务提交边界；本次没有修改实现。

证据：`review/event-propagation-review.json`、`review/acceptance-event-timeline.jsonl`、`review/992203060008-preliminary-pass.jsonl`，以及 `browser/execution/s5-after-final-pass-tasks.png`、`s5-after-training-trigger-tasks.png`。这些路径均相对于本文第 7 节证据根目录。

### P2：满意度问卷同范围修订被自身发布版阻断，界面只显示系统异常

浏览器在旧问卷模板 `2101674324112179201` 下误建 v2 草稿后，尝试发布返回 HTTP 200、业务码 500。主代理从错误记录 `24489` 确认原因为 `SATISFACTION_TEMPLATE_APPLICABILITY_AMBIGUOUS`。v1 与 v2 的项目类型、签约/实施方式、用途、时机、优先级一致；唯一已发布匹配项正是该模板自己的 v1。

[SatisfactionTemplateManagementService.java](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/satisfaction/SatisfactionTemplateManagementService.java) 第 119～121 行只排除待发布 revisionId，而非同一模板即将被替换的当前发布版；旧版的 `SUPERSEDED` 处理又在此检查之后。因此正常的同范围升级也被拦截。另一次新模板发布确实与旧模板范围冲突，应当被阻止，但也只呈现“系统异常”，缺少可操作的业务提示。

本次没有改旧发布版。实际满意度调查改用既有 v1 完成。证据：`review/satisfaction-publish-errors.jsonl`、`questionnaire-revision-scope.jsonl`、`questionnaire-roots-after.jsonl`。误建草稿作为验收副作用单独保留，不将其称为正常计划内操作。

## 3. 复制对象与项目身份

| 对象 | 本次实际值 |
|---|---|
| 原模板 | `FPROJ009_E2E_S1S6_20260920`，ID `993009001591` |
| 复制来源 | 明确选择已发布 v2，revision `993009001599`，不是工作草稿 |
| 新模板 | `FPROJ009_E2E_S1S6_20260921`，ID `993009001592` |
| 新发布版 | v1，revision `993009001601` |
| 新项目 | `PJT2026000006`，ID `992203060008` |
| 项目名称 | `FPROJ009_REPAIR_20260921_全流程验收`；名称沿用验收脚本前缀，本次未实施修复 |
| 唯一冻结计划 | `2101853193410375682`，全程没有更换模板或计划 |
| 最终状态 | `NORMAL_CLOSED`，S6，`2026-09-21 10:47:23.868` |

主代理逐内容比较新发布版与原 v2：designer document 和 execution snapshot 均完全相同。共 6 阶段、14 任务、29 交付件、11 门禁；其中 20 项交付件必需。本次未修改源模板或放宽模板条件。

## 4. 实际完成的业务及规则结果

| 范围 | 实際浏览器操作及独立核对结果 |
|---|---|
| 未激活阶段的独立业务 | 在 S1 创建并批准 S3 方案 `30011`，方案状态 3；S3/其任务保持未激活，证明业务操作不以活动任务为前置 |
| S1 | 工勘 `2101854071441780738` 确认；需求 `2101854616407699457` 完成、revision `2101854616407699458` 冻结；两项任务按事实自动完成；启动会原生提交；4 项必需材料接受 |
| S1 失败保护 | 阶段先提交、任务尚未齐全时仍 ACTIVE；后来三项任务都完成但材料未齐时仍 ACTIVE；最后必需材料接受后才进入 S2 |
| S2 | 工期方案 `2` 的 revision 2 生效，任务按 `DURATION_EFFECTIVE` 完成；2 项必需材料接受 |
| S3 | 使用前面实际批准的方案 `30011`，按 PAGE/原生规则提交方案任务；2 项必需材料接受 |
| S4 | 到货 `30012` 已签收；安装 `30013` 已完成；配置 `30014` 已完成；联调 `30015` 通过；四项 PAGE 任务正常提交；5 项必需材料接受 |
| S5 初验失败→通过 | 活动 `2101861016458424322`；v1 `2101861269349789698` EFFECTIVE/FAIL 时任务不完成；替换发布 v2 PASS 后，v1 保留为 SUPERSEDED/FAIL，不覆盖历史；唤醒失败见第 2 节 |
| S5 终验 | 独立活动 `2101863347803582466`，报告 `2101863357899272194` EFFECTIVE/PASS；刷新重开结果一致；唤醒失败见第 2 节 |
| S5 满意度 | 独立调查 `2101863808916975618`；缺少签字文件被 UI 拦截；实际填写、签字上传后，结果 `2101863820640055298` 为 EFFECTIVE/PASSED，100 分、阈值 80 分 |
| S5 交付件 | D0/D1 实际上传 PDF；D2 精确关联初验 PASS v2；D3 关联满意度 PASSED 结果；D4 关联终验 PASS v1，均为本项目 CURRENT 结果，5 项接受 |
| S6 | 原生闭环办理任务提交、D0/D2 两项必需材料接受；正式校验、申请与实际审批完成 |

S1～S5 完成时间分别为 **10:16:14.220、10:18:05.793、10:19:46.745、10:27:23.424、10:44:02.040**；每个后续阶段的开始时间均不早于前阶段完成。S6 通过正式正常结项审批收口。

最终数据库：6/6 阶段 DONE、14/14 任务 DONE、11/11 门禁 PASSED、20/20 必需交付件 ACCEPTED，20 个阶段/任务执行轮次均有完成证据。20 项材料中 17 项通过上传提交、3 项关联有效业务结果；其余 9 项是模板可选交付件，未强行补齐。

## 5. 正式闭环与结项后回读

创建项目后先实际点击闭环校验，快照 `2101853595266641921` 未通过，54 项检查中 28 项失败，未生成结项申请；保留终端阶段、任务、门禁等失败事实。

完整办理后，快照 `2101865536093614081` 通过，申请 `2101865629412683778` 最终 `APPROVED`。BPM 实例为 `a6e40969-b566-11f1-ac9e-00ff0b78abd7`，流程为冻结规则指定的 `PMS_MINIMAL_NORMAL_CLOSURE`：

- `serviceManagerReview`：真实管理员 UI 审批，10:46:57.288，APPROVE。
- `materialReview`：真实管理员 UI 审批，10:47:17.651，APPROVE。

两节点使用冻结候选人 ID 1；申请人为项目经理 ID 103。正式 exit 记录与申请、流程实例及 `NORMAL_CLOSED` 一致。浏览器新建上下文重新登录/打开项目，显示正常闭环、100%、14/14。

结项后 S6-D0 保留提交历史，界面无来源选择、提交材料、重新检测按钮；实际点击下载与预览均成功，下载 PDF 24,197 bytes，浏览器 PDF 预览可读。该证据证明已验收入口的只读表现，未将其扩展为全部接口的关闭后写入攻击测试。

## 6. 限制、旧数据保护与验收残留

- 本模板没有配置结果订阅，也没有 operationContract；本次没有验证结果订阅模式、全部操作绑定配置、并发/跨租户/跨权限攻击矩阵及报告撤销后的重开流程。只读代码检查与真实浏览器已执行场景分别记录。
- S2 只要求 `DURATION_EFFECTIVE`。当前工期方案仍 `PENDING_RECALCULATION`；不能宣称完整施工计划编排与重算功能已验收。
- S4 实际完成的是既有到货、安装、配置和联调记录操作；页面明确在线采集、自动对比未接入。本次没有验证设备现场效果。
- 文件和验收意见为隔离环境合成材料，证明上传、绑定、版本、判定和闭环链路，不代表真实客户交付确认。
- 原项目模板根记录及三份修订文档/快照与验收前一致。旧项目 `992203060006`、`992203060007` 的本次捕获范围（计划、阶段、任务、执行、门禁、交付件、闭环等 14 类表）分别 95/118 行，前后完全一致；未宣称所有旧业务表均做了全量审计。
- **误操作残留**：旧满意度模板 `2101674324112179201` 新增未发布草稿 v2 `2101863633955778561`，其 v1 `2101674336615399426` 仍为当前 PUBLISHED，根版本仍 1。未找到草稿删除/撤销的受支持 UI/控制器入口，未用数据库清理或改变旧发布版本。
- 另保留新问卷模板 `2101863145994645505` 及未发布草稿 `2101863154660077570`，以及 S1-D0 额外已绑定但未提交 artifact `2101857498217508866`。这些不计入成功材料数量。
- 浏览器使用实际 Chrome/Playwright；内置浏览器运行文件缺失后改用本地隔离浏览器上下文，未改全局插件配置。页面自动化的选择器/等待超时已从现有成功业务记录继续，不把这些脚本故障冒充产品失败。
- 本次没有修改产品实现、迁移、正式规格或工程状态，没有运行编译/全套自动化测试，也没有提交或推送。工作区原有 PID 文件变更及截图保持原样。

## 7. 证据与复核入口

原始证据根目录：`M:/AICoding/CodexData/worktrees/a4f5/NPDMS/.run/template-special-20260921/`。该目录属于本地忽略的验收资产，未随报告自动提交。

| 证据 | 用途 |
|---|---|
| `browser/acceptance-summary.json` | 其他模型的操作结果索引及残留声明 |
| `browser/operations.jsonl`、`browser/execution/`、`browser/acceptance-reports/`、`browser/satisfaction/` | 实际 UI 请求回执、阶段操作、报告历史、调查和审批截图 |
| `review/copy-independent-check.json` | 主代理确认原发布 v2 与副本发布 v1 内容一致 |
| `review/992203060008-closure-progress.jsonl` | 已正常结项的完整受限表快照；文件名表示采集节点，不表示仍未结项 |
| `review/closure-independent-check.json` | 主代理 37 项闭环事实断言，通过；不包括事件传播失败项 |
| `review/event-propagation-review.json` | 主代理对初终验事件唤醒失败的独立结论 |
| `review/992203060008-owner-s5-complete.jsonl` | 初验 FAIL/PASS 历史、终验 PASS、满意度及其他 Owner 事实 |
| `review/preservation-independent-check.json` | 原项目模板及两旧项目指定范围前后内容比较 |
| `browser/execution/closed-project-fresh-readback.png` | 全新浏览器上下文回读已结项项目 |
| `browser/execution/closed-project-file-readonly.png`、`closed-project-s6-d0-preview.png`、`closed-project-file-download-result.json` | 结项后材料历史、只读入口、实际预览/下载 |

主代理使用 `review/capture-project.ps1` 和 `capture-owner-facts.ps1` 在指定本地实例中按 tenant/project 执行只读事务；`verify-closure.ps1 -SnapshotPath <已捕获快照>` 复核结项事实，`verify-preservation.ps1` 对照指定旧数据。未直接调用写 API、写 SQL 或通过脚本伪造业务完成。
