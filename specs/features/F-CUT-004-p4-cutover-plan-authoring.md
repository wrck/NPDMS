# F-CUT-004 P4割接方案编制与版本提交 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO@644816f2`
> API/Physical/Legacy Machine Contract Gate：`PASS / GO@87b0b066`
> Requirement：`CUT-04（V1/P0）`
> Requirement切片覆盖：`CUT-04@V1=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-001`、`F-CUT-002`；A/B/C另依赖`F-CUT-003`
> 后置Feature：`F-CUT-005（CUT-05分级审批）`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`（`PASS / GO@9ef7545d`）
> 最终裁决：Feature Ready `GO`（状态基线`644816f2`；独立正式裁决）

## 1. 业务目标

割接-一线工程师在P4基于冻结的任务、最终评估和适用清单编制标准或简易割接方案。系统支持已有完整方案上传与在线模板两种方式，形成只追加、可追溯的方案revision；“下一步”必须同时冻结提交版本并通过CUT-05公开端口创建审批实例，任一步失败均保持P4草稿。

## 2. Scope

### 2.1 包含

- A/B/C可选择在线标准模板或完整方案上传；D级可选择完整方案上传，选择在线填写时仅填写阶段操作步骤和回退步骤；
- 冻结任务、最终评估、项目、设备、配置revision及CUT-07方案模板章节快照；A/B/C同时冻结有效CUT-03清单，D级不要求清单；
- CUT-03所有未通过风险项与应对措施一一对应；
- 客户、迪普一线、迪普二线、迪普研发四类保障人员的姓名、任务描述、电话和到位时间；
- 草稿保存、初稿生成与下载审计、完整方案文件事实冻结；
- 只追加方案revision、步骤、风险措施、保障人员及联系人变更审计；
- 幂等提交、并发单胜、来源失效、审批驳回派生新revision及批准后保障人员变更规则；
- CUT-05审批创建、读取、锁定重验、来源失效暂停与替代审批恢复链的内部Owner合同；
- P4查询、保存、下载、提交权限和任务范围。

### 2.2 不包含

- CUT-05审批实例实现、路由、评审项、待办、通知、通过/驳回按钮或`CutoverApproved`；
- CUT-06执行、观察或闭环；
- PLT文件存储/扫描Provider、文档解析器、CUT-05生产Provider或生产Fake/fallback；
- 修改旧`pms_cut_plan`、`/pms/cut-plan`、旧页面、旧权限或旧数据；
- V2/V3能力、通用工单、Yudao基础平台修改。

## 3. 业务规则

### BR-FCUT004-001 资格与来源冻结

- 按2026-10-20需求方确认，割接仅关联项目身份，不绑定项目通用行版本。P3清单提交、P4保存/生成/提交及P5来源组装不得因当前项目版本与历史读取版本不同而阻塞，也不得要求仅为项目版本变化重新匹配清单或修订方案。projectVersion保留为读取时的审计信息；未提交草稿保存时可更新该审计值，已提交、批准及历史快照保持原件。当前权限、租户和项目范围、设备归属、割接任务/评估/清单/方案版本、配置及模板来源仍重验；同一次操作的当前项目读取与锁定并发重验保留。
- 仅当前任务处于`PLAN_DRAFTING`且操作人为当前任务负责人并具备项目`ACTION_EDIT`时可创建或修改草稿。
- A/B/C必须引用当前有效`SUBMITTED`清单；D级必须没有清单引用。所有等级均引用当前有效最终评估。创建草稿同样先inspect完整来源并在写事务内重验评估和适用清单：评估变化返回`ASSESSMENT_STALE`，A/B/C清单变化返回`CHECKLIST_STALE`，D级不产生清单陈旧结果。
- 首个revision冻结任务/评估/清单、项目/设备、任务创建时配置revision、方案模板章节及完整CUT-03失败风险Owner集合；D级仅清单身份为空且失败风险集合为空，配置与适用的操作/回退模板章节仍须冻结。初稿生成和提交必须从持久来源重建完整期望来源后锁定重验，不得从草稿缺行或当前Owner查询反推冻结事实；来源变化不得静默刷新草稿或已提交内容。按2026-10-20需求方确认，当前有效DRAFT的内容暂存不以来源已满足提交条件为前提：使用该草稿持久来源检查内容结构，保留任务负责人、项目编辑范围、任务及方案版本、文件和人员来源校验，原子保存正文和明细但不改来源、不推进流程；来源变化或未完成仍阻止生成和提交。
- CUT-03未通过风险项必须全部进入风险措施明细；每项提交前必须有非空措施。

### BR-FCUT004-002 编辑方式与内容

- `createDraft`是按`editMode`封闭的请求联合：两种在线模式仅提交`editMode`并由服务端生成合法空骨架；`FULL_FILE_UPLOAD`必须在创建时同时提交完整`fileArtifactFact`和`ownershipConfirmed=true`。上传分支以请求文件事实为期望，创建前inspect并在写事务内锁定重验，仅冻结匹配的PLT Owner `artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/sha256`及人工归属确认，不先插入不完整文件根，也不强制填写在线模板章节。
- `ONLINE_TEMPLATE`按冻结模板章节保存割接概述、计划、拓扑/组网文件引用、设备清单快照、操作/验证/收尾/测试/回退/保障内容。
- `DRAFT`暂存与提交完整性分层：标准/简易草稿始终使用判别联合的exact keys；项目说明可为空字符串，计划表、步骤、风险措施和保障人员可为空或合法子集。计划、步骤和风险明细仍须字段完整、Owner身份匹配。未填写风险以缺少对应`RiskMitigation`表示，不使用`null`或空措施占位。按2026-10-14需求方确认，保障人员为可新增、删除的明细，角色下拉沿用客户、DP一线、DP二线、DP研发，同角色允许多人；每行独立保存姓名、职责、电话、到位时间和客户联系引用，以`arrangementId`定位并保留填写顺序。草稿允许人员、职责、电话为空字符串、到位时间未填；已选客户引用仍须校验项目归属、启用状态及版本。生成初稿和提交时要求标准方案项目说明/计划表、六类步骤、全部当前未通过风险措施，并逐行检查人员必填项和客户来源；按2026-10-20需求方确认，仅客户、迪普一线工程师两类保障必须覆盖，迪普二线工程师、迪普研发为可选，已添加的行仍检查完整性；简易方案要求操作/回退两类步骤齐全。既有提交、批准版本及历史保障记录不回写。
- D级选择在线填写时仅允许`ONLINE_TEMPLATE_SIMPLE_D`的阶段操作步骤和回退步骤，不得生成A/B/C完整章节或风险清单；D级选择已有完整方案时可使用`FULL_FILE_UPLOAD`，不强制解析在线章节。
- 方案步骤是已审批方案内容，不是运行时执行状态，不建立执行步骤生命周期。

### BR-FCUT004-003 revision与审批交接

- 同一任务revision编号从1单调递增；`DRAFT/SUBMITTED/INVALIDATED`是P4本地生命周期。提交后正文、步骤、风险措施和职责不可覆盖。
- 提交在一个外层CUT事务内锁定任务、来源、当前revision和项目范围，先分配唯一服务端`planSubmittedAt`并将其作为受信内部事实传入`CutoverApprovalFactApi.start`；审批实例创建成功后，同一时间原样写入revision并置`SUBMITTED`。只有审批实例返回稳定`approvalInstanceId/approvalVersion`后，任务才进入P5；否则全部回滚。
- 同一`Idempotency-Key + taskId + revisionNo + normalizedPayload`重放返回原结果；异载荷冲突。`If-Match`陈旧或并发提交仅一方成功。
- `SUBMITTED`来源失效时追加失效人、时间和原因，将revision置`INVALIDATED`，并在同一事务调用CUT-05将同一审批实例`PENDING -> PAUSED_SOURCE_INVALIDATED`，同时由F-CUT-004把任务`P5/APPROVING -> P4/PLAN_DRAFTING`并追加`P5_SOURCE_INVALIDATED`历史。该revision和审批不恢复原状态；恢复办理必须派生新方案revision并在提交时创建引用旧实例的替代审批；不得把审批`REJECTED`当作失效。

### BR-FCUT004-004 审批结果后的P4规则

- CUT-05通过只产生审批事实和`CutoverApproved`，不得改写方案正文；CUT读取并锁定重验明确的审批事实。CUT-05最终驳回拥有`P5/APPROVING -> P4/PLAN_DRAFTING`，全部通过拥有`P5/APPROVING -> P6/CLOSURE_IN_PROGRESS`；来源失效的P5→P4由F-CUT-004在暂停审批的同一事务拥有。
- CUT-05驳回后，工程师以原提交revision为`source_plan_revision_id`创建新DRAFT，原revision与审批意见保持不可变。
- `APPROVAL_REJECTED/SOURCE_REPLACED`派生DRAFT必须冻结写事务内重验得到的当前Owner来源事实，不能把旧revision的来源绑定投影原样复制到新快照。允许继承的仅是用户填写的项目说明、计划、文件引用、步骤文字、风险措施文字和保障安排；标准在线方案的`deviceSummary`必须由当前设备事实完整重建，风险项按`stableItemKey`保留措施文字并以当前风险事实重建，已消失风险删除、新增风险保持未填写。若等级与原编辑方式不再兼容，或在线方案适用模板章节发生变化而无法唯一映射，必须在插入新DRAFT前以`CONFIGURATION_OR_TEMPLATE_STALE`失败关闭；不得持久化不一致草稿。
- 批准后仅姓名、联系电话、到位时间变更可在原批准revision的保障安排投影上更新并追加前后审计，不重审。该PATCH只接受方案根`If-Match`：事务先CAS递增`cut_plan_revision.version`，再更新锁定人员行内部版本与审计；任一步失败整体回滚，不接受客户端从表版本。
- 角色或任务职责变化必须创建引用原批准revision的新DRAFT，并通过同一提交合同重新进入P5。
- `DUTY_CHANGED`的上述业务义务保持不变，但当前正式状态机尚未分配`P6/CLOSURE_IN_PROGRESS -> P4/PLAN_DRAFTING`的物理Owner、历史触发器、旧APPROVED事实及可能存在的CUT-06闭环处置；在`Q-FCUT004-001`关闭前，该分支为`BLOCKED_BY_SPEC`，不得用来源失效、审批驳回或联系人PATCH替代。Task 6可独立交付REJECTED、SOURCE_REPLACED和批准后联系人PATCH。

### BR-FCUT004-005 文件、下载与权限

- 初稿下载从当前revision生成受控文件并记录下载人、时间、revision和文件引用；下载不改变方案状态。
- CUT只保存PLT公开文件事实，不保存URL、正文、PLT内部表ID或客户端自报扫描结论。
- 权限固定为`query-plan/save-plan/download-plan/submit-plan`；功能权限不扩大项目/任务范围。服务端返回`allowedActions`，前端不得按状态或角色猜测。

## 4. API与交接合同

精确字段、错误与幂等见`F-CUT-004-api-contract.json`。用户REST：

| 接口 | 操作 | 结果 |
|---|---|---|
| `/api/v1/pms/cutover-tasks/{taskId}/plan` | `GET` | 当前P4方案、来源快照、内容、保障人员、审批结果投影和`allowedActions` |
| `/api/v1/pms/cutover-tasks/{taskId}/plan/actions/create-draft` | `POST` | 按编辑方式创建唯一当前DRAFT |
| `/api/v1/pms/cutover-tasks/{taskId}/plan` | `PUT` | 暂存当前DRAFT正文、步骤、风险措施和保障人员 |
| `/api/v1/pms/cutover-tasks/{taskId}/plan/actions/download-draft` | `POST` | 生成初稿并记录下载审计 |
| `/api/v1/pms/cutover-tasks/{taskId}/plan/actions/submit` | `POST` | 冻结revision并与CUT-05审批实例同成同败 |
| `/api/v1/pms/cutover-tasks/{taskId}/plan/support-arrangements/{id}` | `PATCH` | 仅批准后联系人类字段变更并审计 |
| `/api/v1/pms/cutover-tasks/{taskId}/plan/actions/revise` | `POST` | 驳回后或职责变化创建新DRAFT |

内部端口：

- `CutoverApprovalFactApi.start/inspect/lockAndRevalidate/pauseForSourceInvalidation`的精确Java签名、DTO、版本、幂等与结果联合见`F-CUT-005-approval-owner-contract.json`。审批状态封闭为`PENDING/PAUSED_SOURCE_INVALIDATED/APPROVED/REJECTED`四态；来源失效后的恢复通过新方案revision与替代审批链完成。
- `src/test`可实现该合同的确定性受控替身，模拟P4提交、P5驳回/批准、来源失效暂停及替代审批链的CUT规则；生产Provider、节点、待办和通知仍归F-CUT-005。
- `src/test`可提供确定性受控替身完成P4正向提交和驳回/批准后的CUT规则测试；不得进入生产装配或作为真实浏览器/Implementation Done证据。

## 5. 数据与迁移

- 新平台仅写`cut_plan_revision`、`cut_step`、`cut_cutover_support_arrangement`三张方案业务表。`content_snapshot`只保存标准方案概述/风险措施或简易方案根判别，不复制步骤与保障人员；`cut_step`是步骤唯一事实，`cut_cutover_support_arrangement`是职责和当前联系人唯一事实。草稿保存原子更新根与从表，读取时组装REST内容；批准后联系人合法变更只更新保障人员行并写平台前后审计，不改不可变正文或职责，不新增第四张CUT业务表。
- `pms_cut_plan`保持原表、源码、API、页面和权限不变。受控Release导入器先把原始行写入PLT不可变来源记录；CUT迁移Job只领取`STAGED_READY`批次。合格旧行通过`pms_cut_task`既有PLT映射解析同租户目标任务，形成只读`LEGACY_FORWARD` revision和`pre_check/procedure/verification/rollback -> PRE_OPERATION/OPERATION/POST_BUSINESS_TEST/ROLLBACK`步骤；旧`status/approved_* /baseline_version`只保留为原始迁移证据。
- 根身份、原始状态、来源版本、字段资格、目标冲突、逐行issue/retained及同一外层事务内目标写+PLT分类+批次完成规则见physical contract；正常CUT生产Bean不连接或直读遗留表。
- `CutoverSupportArrangement`为`NEW_ONLY`；旧表没有可证明的角色、职责、电话和到位时间来源。
- Flyway版本在实际串行合入时确定；Feature Ready不预约DDL。

## 6. 验收标准

- AC-FCUT004-001：A/B/C以冻结评估、清单和配置创建标准或完整文件DRAFT；D级不读取清单，可上传完整方案，选择在线填写时只生成简易步骤/回退内容。
- AC-FCUT004-002：上传分支只冻结有效PLT文件事实并可直接提交；在线分支完整保存章节、未通过风险措施，保障人员必填客户和迪普一线工程师；二线、研发按需添加，已添加的明细仍须完整填写。该必填范围按2026-10-20需求方确认调整，既有历史不回写。
- AC-FCUT004-003：保存与初稿下载不推进状态；下载审计包含人、时间、revision和文件事实。
- AC-FCUT004-004：提交revision与CUT-05审批实例同成同败，重放不重复，并发单胜，失败保持P4草稿。
- AC-FCUT004-005：驳回创建新revision；批准后联系人类变更留前后审计且不重审，职责变化创建新revision并重走P5。
- AC-FCUT004-006：旧页面/API/权限/表不变；受控替身仅证明CUT单元/集成正向链，生产依赖未关闭不得声明真实浏览器或Implementation Done。

## 7. Feature Ready Gate

最终：`BASELINE / READY / GO / NOT_STARTED`。API/Physical/Legacy Machine Contract已在`87b0b066`独立复审`PASS / GO`，Feature Ready最终关闭以状态基线`644816f2`取得独立`GO`。允许生成唯一当前Technical Plan并申请独立Plan Gate；本结论不授权DDL、运行实现、生产CUT-05 Provider、真实浏览器或Implementation Done。


### 生成文件重新打开（2026-10-15局部修复）

在线方案生成成功后，在同一事务追加 `cut_plan_generated_file`，按租户、方案修订及正文版本保存精确文件引用和生成时间。GET `PlanView.generatedDraft` 返回当前正文版本的记录，未生成则为 null；页面重开恢复文件区，下载仍由 PLT 实时授权和可用性校验。新正文保存推进版本后不再展示旧版本生成文件，旧记录和平台文件保留。生成不提交审批、不改变任务或正文版本；并发保存造成版本不符时整次生成引用写入失败回滚。完整上传方案的既有字段与数据库约束保持不变。


### 2026-10-16 方案计划时间输入

需求方确认所有方案模式共用单个“计划割接时间”输入框，保存为 `cut_plan_revision.scheduled_time`，wire 字段为 `content.scheduledTime`。草稿可暂存空值，生成及提交必须填写；在线标准方案原有明细计划保留，不以最早计划项推导该字段。审批及A/B提前时限读取当前方案的明确时间，不读取任务创建时间。历史正文及批准来源不回填，已提交修订的计划时间不可改写。D级不需要割接类型、组网模式或设备类型，仍保留方案时间、操作和回退及正式审批校验。


### 2026-10-20 保障人员选择差量（需求方批准）

标准方案仅客户、一线必填，二线和研发可选；已添加行保持完整性校验。按需求方后续澄清，仅在既有项目成员接口增加可选角色筛选：客户候选及所选成员重验使用 END_USER，仍保留原手填和回显行为；不传角色时接口行为不变。一线、二线、研发分别选择当前租户启用的 PROJECT_MANAGER、probSupporter、probRD 系统角色成员。
新增只读 GET `/api/v1/pms/cutover-tasks/{taskId}/support-person-choices?roleCode=...`，沿用割接查询权限并校验任务租户及项目范围，返回 `[{reference:{sourceCode,id,version},name,phone}]`；sourceCode 仅 PROJECT_MEMBER 或 SYSTEM_USER。
标准方案人员行及批准后联系人 PATCH 可带可空 `personReference`，原 customerContactReference 用于保留既有历史，不与新引用同时使用。CUT 以可空关系列保存人员来源，不以 JSON 保存固定关系；保存重验所选来源，提交要求工程师选择实际用户并重验角色，姓名不得伪造。成员失效、改角色、用户停用或版本变化均要求重选。客户手填仍需完整姓名和电话。所有授权、项目范围、幂等与根版本并发校验保留；历史来源不回填，批准后变更继续追加原审计链。

### 2026-10-20 草稿保留与修订回显（需求方批准）

前序修订导致方案失效后，重新选择相同在线模式编制时，沿用既有受控修订命令创建新版本，复制上一版已保存的正文、计划、步骤、保障人员和文件引用，并按当前来源执行既有重基。原版保持不可变；模式变化和完整文件上传沿用原创建规则，不臆造未保存内容。

可编辑在线标准草稿重开时，按本任务各文件槽位完整业务键读取平台当前有效引用，恢复空槽或草稿尚未保存的较新引用，并提示保存后生效；读取完成前禁止保存旧值。来源较旧或迟到的响应不得降低已知文件版本及引用并发版本，仍由正常草稿保存执行服务端授权和版本校验。已提交、批准或历史只读方案使用原冻结文件引用，不随平台当前引用变化回填。

评估、清单和方案写入失败后刷新权威信息时保留当前尚未提交的输入。方案生成、提交和正常离页前先保存草稿；保存失败不离页，浏览器刷新或关闭时提示未保存内容。并发修改不能用自动换版本后重试覆盖；权限、范围、状态或引用校验失败时仍拒绝写入，保留本地输入并明确告知尚未保存。前端缓存不作为业务事实或提交成功证据。

### 2026-10-20 填写中切换编制方式及日期时间控件（需求方批准）

可编辑草稿允许在当前等级对应的在线模板和完整文件上传之间切换，沿用既有草稿保存与版本校验。在线切换为上传后，须上传有效完整方案并明确确认文件使用权才能保存或提交；上传转在线时按冻结等级与设备来源初始化表单，不解析导入文档正文，原平台附件保留供填写时参考。在当前编辑会话内切换保留双方尚未保存的输入及计划割接时间。已提交、批准、审批快照和历史版本不随此切换修改，须先经既有修订流程。

方案操作清单及回退步骤的操作时间、割接后保障的时间使用日期时间选择器，与保障人员到位时间保持相同控件风格。已有日期、计划割接时间和到位时间继续使用日期时间选择器；业务影响时间仍表示时长。新选择值沿用既有字符串字段的日期时间格式，旧自由文本保留并显示原填写，不自动补日期或改写历史。

### 2026-10-20 提交后保留下载（需求方批准）

方案提交后保留底栏“下载方案”。完整上传模式下载原冻结文件版本；在线模式从已提交方案原始内容只读导出DOCX，复用方案渲染器，不要求事先生成草稿文件，不修改方案、审批或既有生成记录。继续检查功能权限、任务可见性、当前提交状态和并发版本，不使用已批准联系人后续修订替换提交时的人员安排，不以新一轮成员资格或前序匹配阻止历史内容下载。

### 2026-10-20 在线方案附件图片内嵌（需求方批准）

在线方案生成与提交后导出共用渲染器，将割接前拓扑、割接后拓扑及组网配置中的PNG/JPEG图片以精确工件版本内嵌正文对应章节，按比例适配正文页面，保留原名称与版本标注；PDF等非图片继续显示引用。读取必须经过PLT正式用户下载授权、Owner业务键、可用性及内容完整性校验，读取或图片解析失败则整次生成失败，不静默遗漏。已生成旧文件与审计不覆盖；再次生成使用图片格式的新生成记录，后续重复生成复用同格式缓存。

### 2026-10-20 方案设备清单自动回填（需求方批准）

可编辑在线标准方案的设备范围和序列号继续使用任务冻结设备清单；涉及产品型号、在网软件版本的空值从同一设备公开查询结果自动回填，分别使用productModel和conpVersion。已有非空填写、升级目标版本和备注保留。回填内容作为当前草稿输入，经既有保存、生成前保存和提交校验落库，不自动提交或改写历史。未获设备查询权限、查询失败或设备身份不符时不填入来源值，保留人工填写；已提交、批准及历史只读内容不读取当前设备信息覆盖原记录。D级简易方案和完整文件上传结构不变，上传切换为在线标准方案时同样执行空值回填。

升级版本信息的空值从方案来源快照所引用的调研清单读取targetSoftwareVersion，按同一设备ID和序列号匹配；保留方案已有非空填写，不以当前最新清单替代所引用版本，不猜测未填写的目标版本。该CUT查询独立于设备资产查询权限；无法读取时保留人工填写并提示，不阻止其他设备资料回填，保存、提交与历史保护沿用原规则。

### 2026-10-20 前序调研资料带入（需求方批准并澄清）

仅带入具有明确对应关系的实际已填写数据，无对应项或无值则不带入。现网拓扑图SURVEY_CURRENT_TOPOLOGY对应割接前拓扑；割接后拓扑和组网配置在当前调研定义中无对应来源，保持单独上传，不将现网图冒充割接后图，不拼接接口/IP/VLAN规划或挪用友商配置附件。在线标准草稿的空概述可使用所引用调研中的割接背景表述及涉及业务简述，保留来源标签；已有任务背景初始化、方案概述及附件均不覆盖。风险项继续使用既有冻结失败项引用，不自动填写应对措施或测试结果。

带入前读取方案来源快照指定的已提交清单及版本，不能跟随其他修订或当前文件最新版。文件保存时在原草稿事务内通过PLT现有版本附加接口建立CUTOVER_PLAN/FULL_PLAN业务引用，仍引用源工件的精确版本，不覆盖源清单引用或原文件。读取页面仅预览原来源，不产生文件绑定或改写历史；草稿保存、生成前保存、提交继续执行现有权限、范围、状态、并发和文件校验。无文件时保持空，来源绑定失败则保存失败并回滚，不静默保存无效引用。D级、完整上传及提交后只读历史不自动改写。
