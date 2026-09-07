# F-ACC-001 初验/终验报告版本与交付件同步 Feature Spec

> 文档状态：`REVALIDATION_REQUIRED`
> 上次Feature Ready（历史）：`READY / GO`（来源`bde0feac`；master修订011关闭Change ID冲突）
> Feature Ready：`REVALIDATION_REQUIRED`（修订018独立验收及配置化差量）
> 实施状态：`IN_PROGRESS`
> Requirement：`ACC-03（V1）`、`ACC-04（V1局部）`
> Requirement切片覆盖：`ACC-03@V1=PARTIAL；ACC-04@V1=PARTIAL`
> PRD差量重验证：`ACC-03@V1；ACC-04@V1`
> 差量依据：`CHG-PRD-2026-09-07-018`；原报告切片与历史测试不证明完整独立验收能力
> Owner Context：`ACC（验收与闭环）`
> 目标实现载体：`pms-module-project`及其内嵌`pms-module-project-api`；ACC与PROJ语义Owner保持分离，不新增第二套项目任务或应交清单真值
> 适用基线：PRD V1.8修订018；ADR-0045及相关SDS。原修订011、ADR-0039/0040审核只保留历史适用范围，具体新接口/物理差量须先关闭Q-TPLACC-001
> 上次Technical Plan（历史，不授权修订018差量）：`NPDMS-FACC001-TECHPLAN-20260830-01`，`PASS / GO`（原独立整改复审`fca9626c4fce4ccf4b03efdebe997343ce7b5a42`）

## 1. 业务目标

本Feature让项目经理为初验、终验活动形成可修正草稿和不可变有效报告版本，在活动完成时校验验收时间、结论、验收人和完整附件，并把报告当前版本同步到既有项目应交根。报告换版或撤销保留历史、重新触发归档与CLO重校验；归档失败不得破坏有效报告。

```text
PROJ初验/终验任务与执行契约
-> ACC活动根与报告草稿
-> 首次发布 / 替换 / 撤销
-> 当前报告与只追加历史
-> 既有应交根的来源版本和完整附件索引
-> PROJ任务完成时同事务校验ACC报告完备
```

## 2. Scope

### 2.1 包含

- `PRELIMINARY/FINAL`活动根、草稿、当前有效版本和历史版本查询；
- 草稿创建/修改、首次发布、替换、撤销及单文件下载；
- 终验前置及报告业务完备要求按冻结配置判定，典型初验前置作为预置规则；
- PROJ任务完成命令通过ACC Owner接口原子完成活动；
- 既有创建时初始化路径继续保留原项目创建原子契约；修订018独立创建/进入或首次操作解析路径须先按Q-TPLACC-001定稿，不强制所有终验在建项时创建；
- 复用`acc_project_deliverable`唯一应交根，维护初验/终验来源版本、完整附件集合、归档状态和补偿水位；
- 复用PLT持续ACTIVE的报告附件集合，以独立`ACCEPTANCE_REPORT_ARCHIVE`集合建立归档引用和归档记录；
- `AcceptanceReportVersionChanged`与`ClosureGateRecheckRequested` Outbox；
- 直接回归F-COM-001已交付的阶段进入和阶段内新范围两条AcceptanceScopeBinding路径；
- 前向Schema、权限/菜单及受管验收数据；真实浏览器覆盖报告换版、撤销、任务完成和下载范围。

### 2.2 覆盖边界

- `ACC-03@V1=PARTIAL`：保留已限定的活动/报告/同步合法子切片；修订018独立创建、通用触发及范围契约须经Phase 2和Feature覆盖复核，不用历史合同声明当前全部义务已覆盖。
- `ACC-04@V1=PARTIAL`：只实现`D-INITIAL-REPORT/D-FINAL-REPORT`来源索引、换版/撤销同步、单文件下载和CLO重校验请求。

### 2.3 Out of Scope

- ACC-01、ACC-02及ACC-04其他四类基准来源；统一批量下载、人工审核和完整归档管理页面；
- CLO-01/02业务实现或把重校验请求解释为闭环通过；
- Q-FCOM-002的退出/回退关闭或解锁；
- 修改Yudao基础平台源码、固定角色—权限映射、第三方文件平台连接器；PLT现有PMS文件公共契约的本Feature加性接口不属于该项；
- 把V17记录迁为新当前报告，或从旧名称、URL、审批状态、意见和关项结果补造事实。

## 3. 业务规则

### BR-FACC001-001 活动与任务Owner

- ACC拥有活动和报告；PROJ拥有ProjectTask、WorkBinding、执行契约、完成判定和任务状态。
- 初验/终验当前执行契约固定`targetContextCode=ACC`、`targetObjectType=AcceptanceActivity`、`targetObjectKey=acceptanceId`。ACC不得直接写PROJ表。
- PROJ完成命令按项目任务/执行契约→ACC活动根→当前报告版本锁定，并以`MANDATORY`调用`AcceptanceActivityCompletionFactApi`；仅`COMPLETED`允许追加完成判定并把任务置`DONE`，任一失败整体回滚。
- 活动完成按ACC冻结验收规则校验；预置规则保留当前EFFECTIVE报告的时间、结论、验收人及附件。归档不自动成为额外门禁，证据有效、活动完成和验收通过分别表达。

### BR-FACC001-002 草稿、发布、替换与撤销

- 报告状态只允许`DRAFT/EFFECTIVE/SUPERSEDED/REVOKED`；草稿可修改，生效后业务字段和附件固定引用不可更新或删除。
- 同活动可有多个草稿，但只有一个当前EFFECTIVE版本。`current_marker`仅对`EFFECTIVE + effective_to is null`生成1，其他状态为NULL。
- 首次发布原子把草稿置EFFECTIVE；替换原子关闭旧版为SUPERSEDED后发布新草稿；撤销把当前置REVOKED并清空活动当前指针，禁止恢复旧版。
- 发布和撤销要求期望活动/当前/草稿版本及`Idempotency-Key`；同键同载荷返回原结果，同键异载荷稳定冲突。

### BR-FACC001-003 报告完备与终验守卫

- 有效版本按ACC冻结Schema校验业务必填项；预置规则要求验收时间、结论、验收人和至少一条附件。实际引用的文件仍须完成上传、安全检查并通过FileBusinessScope，配置不能伪造文件有效性或绕过授权。
- 附件是完整有序集合，不选择或推断主附件；报告逐项只保存PLT公共`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/fileHash`，不保存内部`FileVersion.id/FileReference.id`或正文。
- 仅当冻结规则要求有效初验前置时，终验发布前才锁定并重验同租户/项目的适用初验事实；未配置不按S5或签约方式补加，已配置但缺失/失效/无权仍拒绝。

### BR-FACC001-004 交付件来源索引与补偿

- `acc_project_deliverable`是唯一应交根；只接受精确`D-INITIAL-REPORT/D-FINAL-REPORT`，不得按名称或旧`D-ACCEPT-REPORT`推断。
- 首次生效创建CURRENT来源关系及完整附件；替换保留旧关系/归档结果并创建新CURRENT；撤销把当前关系置REVOKED/INVALID并清空根指针，不恢复旧版。
- 索引、文件归档或CLO重校验失败不回滚报告，不删除历史，不误标ARCHIVED；保存`PENDING_COMPENSATION`及失败/重试水位。
- 报告附件键固定`ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}/ACCEPTANCE_REPORT_ATTACHMENT`且引用持续`ACTIVE`；归档只在同对象`ACCEPTANCE_REPORT_ARCHIVE`集合创建独立`ARCHIVED`引用及`FileArchiveRecord`，不得改变历史附件引用或下载链。
- 报告首次发布或替换时把服务端认证用户冻结为不可变`publisherUserId`；归档补偿显式以该用户调用PLT，PLT重验当前`pms:file:archive`、租户和FileBusinessScope并记录`archivedBy`。撤权或范围变化保持`PENDING_COMPENSATION`，不得借用Job用户、伪造登录上下文或取消鉴权。
- 报告命令通过`PlatformCommandExecutionApi`同事务写Outbox；`AcceptanceReportOutboxDeliveryJob`经`PlatformOutboxDeliveryApi`只领取`AcceptanceReportVersionChanged`，来源投影提交后才标记成功，失败安排重试。`ClosureGateRecheckRequested`只是请求CLO重新读取Owner事实，本Feature不得领取或标记其已投递。

### BR-FACC001-005 范围绑定回归

- 直接复用F-COM-001的`AcceptanceScopeBindingApi/AcceptanceScopeGuardApi/DeliveryScopeAcceptanceLockApi`及真实Provider。
- 项目进入验收阶段绑定全部当前范围、验收阶段内新范围生效同步绑定，两条路径必须继续同事务成功或整体失败。
- 报告、活动、交付件和Outbox不得触发、补建、关闭或反推范围绑定；Q-FCOM-002保持窄阻断。

### BR-FACC001-006 既有前向与旧载体边界（原实施范围）

- V17 `pms_acc_acceptance`、旧Service/Controller/UI、旧交付清单/归档/完工证明保持不变，判定`DO_NOT_REUSE`，新活动和报告为`NEW_ONLY`。
- 原受管实施/存量迁移仅以精确`T-INITIAL-ACCEPT/T-FINAL-ACCEPT`和`D-INITIAL-REPORT/D-FINAL-REPORT`识别来源，不能从名称猜测。原两项非终态、V63 TASK_NATIVE成对切换及终态保留条件继续作为历史迁移边界；这些固定码不得作为修订018新独立验收的通用创建条件。
- 既有新项目初始化路径保留F-PROJ-001同事务顺序：PROJ任务/契约预分配、ACC应交根、MANDATORY活动初始化、PROJ追加当前绑定，任一步失败回滚。修订018允许的独立/延迟解析不是对此原接口删参放宽，须先完成Q-TPLACC-001新契约。

## 4. API、权限与事务

所有REST路径使用`/api/v1/pms`前缀，租户和操作者只取服务端认证上下文。

| 接口 | 权限 | 契约 |
|---|---|---|
| `GET /acceptances`、`GET /acceptances/{id}`、`GET /acceptances/{id}/report-versions` | `pms:acceptance:report:query` | 按项目树/任务范围返回活动、当前和历史；空范围返回空 |
| `POST /acceptances/{id}/report-versions`、`PATCH /acceptances/{id}/report-versions/{versionId}` | `pms:acceptance:report:write` | 创建/修改DRAFT；附件只能引用当前身份有权的固定PLT版本 |
| `POST /acceptances/{id}/report-versions/{versionId}/actions/publish` | `pms:acceptance:report:write` | 首次发布或替换；按冻结规则校验适用初验及报告事实，与变更Outbox同事务；原接口须重验证 |
| `POST /acceptances/{id}/actions/revoke-current-version` | `pms:acceptance:report:write` | 撤销期望当前版本，不恢复旧版；与变更Outbox同事务 |
| `GET /acceptances/{id}/report-versions/{versionId}/attachments/{sequence}/download` | `pms:acceptance:report:download` | 每次重验项目范围、FileBusinessScope、租户并记录下载审计 |
| `POST /project-tasks/{id}/actions/complete` | `pms:project-task:execute` + `pms:acceptance:report:complete` | 复用PROJ现有任务命令；服务识别当前执行契约为ACC活动后、调用ACC Provider或写任务/判定前必须同时校验两个权限，缺一即任务、判定和活动零写入；`TASK_NATIVE`保持既有行为 |

角色—权限映射保持配置化；验收身份可通过正式授权配置取得全部相关键，不删除服务端鉴权或租户隔离。

稳定模块契约锁定如下：`AcceptanceActivityInitializationApi.initialize`与`AcceptanceActivityCompletionFactApi.lockAndComplete`位于`pms-module-project-api`的ACC契约包并由真实ACC Provider以`MANDATORY`加入现有事务；ACC文件策略Provider使用`ProjectScopeApi.treeVersion`作为唯一`scopeVersion`。`ExistingFileReferenceTarget`仅加性放行ACC报告附件目标并保留既有目标；绑定、重验、下载复用PLT现有接口，`FileArtifactApi.archiveReferenceSets`只为完整ACTIVE附件集合建立独立ARCHIVED归档集合，其命令显式携带报告版本`publisherUserId`。ACC不得访问PLT或PROJ表。

## 5. 状态、事件与异常

| 对象 | 状态 | 关键守卫 |
|---|---|---|
| AcceptanceActivity | `PENDING/COMPLETED` | ACC拥有业务迁移；原PROJ任务绑定路径是调用入口之一，独立命令/来源及范围契约见Q-TPLACC-001；历史完成事实不回退 |
| AcceptanceReportVersion | `DRAFT/EFFECTIVE/SUPERSEDED/REVOKED` | 当前唯一；替换/撤销保留历史 |
| DeliverableSourceVersion | `CURRENT/SUPERSEDED/REVOKED` | 与归档状态分离；当前唯一 |
| Archive | `PENDING_COMPENSATION/ARCHIVED/INVALID` | 失败不得覆盖报告或伪报已归档 |

- `AcceptanceReportVersionChanged`载荷含`EFFECTIVE/REPLACED/REVOKED`、`publisherActorUserId`、当前/前一版本和完整有序公共附件事实集合，不含PLT内部主键。
- 业务缺项与状态门禁返回稳定业务拒绝；版本/当前冲突返回VERSION_CONFLICT；PLT/PROJ Owner未知或不可用返回DEPENDENCY_UNAVAILABLE；所有拒绝路径保持报告、活动、任务、索引和Outbox零写入。

## 6. 数据与迁移

物理字段、生成列、唯一键及前向表精确以`F-ACC-001-physical-contract.json`和已批准P3-E09差量为准；报告版本以可空`publisher_user_id`保存发布时认证用户（DRAFT为空，EFFECTIVE及历史状态非空且不可改），附件两张表只保存PLT公共版本事实。不修改V17/V63；Technical Plan只能新增前向Flyway。跨Context只保存稳定逻辑引用，不建PROJ/PLT物理外键。

## 7. 验收标准

- AC-01：已有当前初验V1时创建V2草稿不冲突；发布V2后V1为SUPERSEDED、V2唯一EFFECTIVE且历史可查。
- AC-02：撤销当前版本后无当前报告、不恢复旧版，来源根失效并请求CLO重校验。
- AC-03：在预置报告完备规则下缺时间/结论/验收人/附件时发布失败；实际附件未完成、安全检查失败或文件越权始终拒绝，失败零写入。
- AC-04：配置初验前置时缺失事实阻断终验；未配置时不得因S5、签约方式或任务码补加；配置版本及判定可追溯。
- AC-05：既有预置初验/终验任务按当前报告四项完备规则及原原子契约完成；新独立活动完成与节点结果引用须按修订018契约另行验收，不使用旧结果替代。
- AC-06：首次、替换、撤销事件分别维护应交根、来源历史和完整附件集合；重放不重复；归档失败保留有效报告并进入补偿，归档成功后历史附件仍通过ACTIVE集合下载。
- AC-07：正式身份只在项目/文件授权范围内查询和下载；跨项目、跨租户或缺权限拒绝且不泄露存在性。
- AC-08：F-COM阶段进入及阶段内新范围两条绑定回归通过；报告换版/撤销不新增、关闭或反推绑定。
- AC-09：V17旧功能和数据不变；精确两项非终态任务前向绑定成功，两项均终态保持旧事实，终态混合、名称相似、关系不完整或歧义输入整批失败。
- AC-10：真实MySQL验证生成列、当前唯一、原子回滚和历史不覆盖；真实Chromium完成草稿→发布→替换→撤销、任务完成和下载权限闭环。

## 8. Feature Ready检查

| 检查项 | 当前结论 |
|---|---|
| Requirement覆盖与纵向闭环 | PASS |
| Owner/API/权限/事务与锁序 | PASS |
| 状态、物理差量和迁移边界 | PASS |
| 旧实现复用审计 | PASS（见独立审计文件） |
| Open Question | `Q-GOV-20260901-001`已由master修订011关闭；Q-FCOM-002仅阻断Out of Scope退出/回退关闭路径 |
| 独立Feature Ready裁决 | `READY / GO`（来源`bde0feac019baf820634ecc6a0e88272672b601d`；master收敛复核） |

检查点：基线=master PRD修订011；当前Gate=Implementation Done复验；已通过=Feature Ready、Technical Plan、代码选择性集成；阻塞=master真实MySQL、Chromium与独立Done裁决未完成；下一步=从最新master新建DU完成运行复验，不沿用旧Flyway编号或倒签Done。

## 代码事实实施状态（2026-09-04三分支重放）

- 已接收代码路径：`32`。
- 已处理来源提交：`23`。
- 实施状态：已实现切片进入集成分支；未关闭Gate时Feature保持 `IN_PROGRESS`。
- 追溯明细：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

## 修订018当前目标与历史实施边界

本Feature原固定T-INITIAL-ACCEPT/T-FINAL-ACCEPT映射、创建事务和lockAndComplete参数只作既有路径契约及迁移解释，不是新独立验收通用目标。ACC活动/报告优先复用，独立创建/办理不以S5或特定ProjectTask为前提，但必须保留项目、范围、来源和责任权限。Q-TPLACC-001须冻结新API/物理合同及前向处置后再实施；旧V17/V166、已形成文件/报告历史和Task事实不覆盖。本Feature当前仅声明报告子切片PARTIAL，完整ACC-03覆盖需重新分配并复核，不能从原FULL声明或分支历史Done派生。

Requirement及批准依据：`CHG-PRD-2026-09-07-018`、`ADR-0045`及本文件已声明切片。相关Feature Ready保持REVALIDATION_REQUIRED；唯一Implementation Status仍由当前Feature Task维护，本文不晋级Done。
