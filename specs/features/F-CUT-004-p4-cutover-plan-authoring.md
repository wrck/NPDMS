# F-CUT-004 P4割接方案编制与版本提交 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY / REVIEW_REQUIRED`
> Requirement：`CUT-04（V1/P0）`
> Requirement切片覆盖：`CUT-04@V1=FULL`
> Owner Context：`CUT（变更切换与稳定治理）`
> 前置Feature：`F-CUT-001`、`F-CUT-002`；A/B/C另依赖`F-CUT-003`
> 后置Feature：`F-CUT-005（CUT-05分级审批）`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`
> 旧实现复用审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 边界裁决：独立Feature `GO`；Feature Ready `NO-GO`（锁定基线`83cc20d7`）

## 1. 业务目标

割接-一线工程师在P4基于冻结的任务、最终评估和适用清单编制标准或简易割接方案。系统支持已有完整方案上传与在线模板两种方式，形成只追加、可追溯的方案revision；“下一步”必须同时冻结提交版本并通过CUT-05公开端口创建审批实例，任一步失败均保持P4草稿。

## 2. Scope

### 2.1 包含

- A/B/C在线模板与完整方案上传两种编辑方式；D级仅填写阶段操作步骤和回退步骤；
- 冻结任务、最终评估、项目、设备、配置revision及CUT-07方案模板章节快照；A/B/C同时冻结有效CUT-03清单，D级不要求清单；
- CUT-03所有未通过风险项与应对措施一一对应；
- 客户、迪普一线、迪普二线、迪普研发四类保障人员的姓名、任务描述、电话和到位时间；
- 草稿保存、初稿生成与下载审计、完整方案文件事实冻结；
- 只追加方案revision、步骤、风险措施、保障人员及联系人变更审计；
- 幂等提交、并发单胜、来源失效、审批驳回派生新revision及批准后保障人员变更规则；
- CUT-05审批创建与审批结果读取/回执的双向内部合同；
- P4查询、保存、下载、提交权限和任务范围。

### 2.2 不包含

- CUT-05审批实例实现、路由、评审项、待办、通知、通过/驳回按钮或`CutoverApproved`；
- CUT-06执行、观察或闭环；
- PLT文件存储/扫描Provider、文档解析器、CUT-05生产Provider或生产Fake/fallback；
- 修改旧`pms_cut_plan`、`/pms/cut-plan`、旧页面、旧权限或旧数据；
- V2/V3能力、通用工单、Yudao基础平台修改。

## 3. 业务规则

### BR-FCUT004-001 资格与来源冻结

- 仅当前任务处于`PLAN_DRAFTING`且操作人为当前任务负责人并具备项目`ACTION_EDIT`时可创建或修改草稿。
- A/B/C必须引用当前有效`SUBMITTED`清单；D级必须没有清单引用。所有等级均引用当前有效最终评估。
- 首个revision冻结任务/评估/清单、项目/设备、任务创建时配置revision、方案模板章节及输入快照。来源变化不得静默刷新草稿或已提交内容。
- CUT-03未通过风险项必须全部进入风险措施明细；每项提交前必须有非空措施。

### BR-FCUT004-002 编辑方式与内容

- `FULL_FILE_UPLOAD`只冻结经PLT校验的`artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/sha256`及人工归属确认，不强制填写在线模板章节。
- `ONLINE_TEMPLATE`按冻结模板章节保存割接概述、计划、拓扑/组网文件引用、设备清单快照、操作/验证/收尾/测试/回退/保障内容。
- D级仅允许`ONLINE_TEMPLATE`的阶段操作步骤和回退步骤；不得生成A/B/C完整章节或风险清单。
- 方案步骤是已审批方案内容，不是运行时执行状态，不建立执行步骤生命周期。

### BR-FCUT004-003 revision与审批交接

- 同一任务revision编号从1单调递增；`DRAFT/SUBMITTED/INVALIDATED`是P4本地生命周期。提交后正文、步骤、风险措施和职责不可覆盖。
- 提交在一个外层CUT事务内锁定任务、来源、当前revision和项目范围，将revision置`SUBMITTED`并调用`CutoverApprovalStartPort.start`。只有审批实例创建成功并返回稳定`approvalInstanceId/approvalVersion`后，任务才进入P5；否则全部回滚。
- 同一`Idempotency-Key + taskId + revisionNo + normalizedPayload`重放返回原结果；异载荷冲突。`If-Match`陈旧或并发提交仅一方成功。
- `SUBMITTED`来源失效时追加失效事实并将revision置`INVALIDATED`；不得把审批`REJECTED`当作失效。

### BR-FCUT004-004 审批结果后的P4规则

- CUT-05通过只产生审批事实和`CutoverApproved`，不得改写方案正文；CUT读取/消费明确的审批结果合同投影审批状态。
- CUT-05驳回后，工程师以原提交revision为`source_plan_revision_id`创建新DRAFT，原revision与审批意见保持不可变。
- 批准后仅姓名、联系电话、到位时间变更可在原批准revision的保障安排投影上更新并追加前后审计，不重审。
- 角色或任务职责变化必须创建引用原批准revision的新DRAFT，并通过同一提交合同重新进入P5。

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

- `CutoverApprovalStartPort.start(command)`：接收受信租户、任务、不可变方案revision、最终等级、评估/清单版本、来源快照、幂等键和关联ID；成功返回审批实例身份。生产Provider归F-CUT-005。
- `CutoverApprovalResultPort.inspect/lockAndRevalidate(query)`：返回`PENDING/APPROVED/REJECTED/TERMINATED`审批事实及版本；版本变化为`STALE`，不可用失败关闭。审批正文、节点与待办不由F-CUT-004保存。
- `src/test`可提供确定性受控替身完成P4正向提交和驳回/批准后的CUT规则测试；不得进入生产装配或作为真实浏览器/Implementation Done证据。

## 5. 数据与迁移

- 新平台仅写`cut_plan_revision`、`cut_step`、`cut_cutover_support_arrangement`及保障人员联系人变更审计表；精确合同见physical JSON。
- `pms_cut_plan`保持原表、源码、API、页面和权限不变。合格旧行只可经PLT迁移证据形成只读`LEGACY_FORWARD` revision；不补造编辑方式、评估/清单/设备/模板/文件/风险/保障人员或审批事实。
- `CutoverSupportArrangement`为`NEW_ONLY`；旧表没有可证明的角色、职责、电话和到位时间来源。
- Flyway版本在实际串行合入时确定；Feature Ready不预约DDL。

## 6. 验收标准

- AC-FCUT004-001：A/B/C以冻结评估、清单和配置创建标准DRAFT，D级不读取清单且只生成简易步骤/回退内容。
- AC-FCUT004-002：上传分支只冻结有效PLT文件事实并可直接提交；在线分支完整保存章节、未通过风险措施和四类保障人员。
- AC-FCUT004-003：保存与初稿下载不推进状态；下载审计包含人、时间、revision和文件事实。
- AC-FCUT004-004：提交revision与CUT-05审批实例同成同败，重放不重复，并发单胜，失败保持P4草稿。
- AC-FCUT004-005：驳回创建新revision；批准后联系人类变更留前后审计且不重审，职责变化创建新revision并重走P5。
- AC-FCUT004-006：旧页面/API/权限/表不变；受控替身仅证明CUT单元/集成正向链，生产依赖未关闭不得声明真实浏览器或Implementation Done。

## 7. Feature Ready Gate

当前：`DRAFT / NOT_READY / REVIEW_REQUIRED / NOT_STARTED`。最近Gate为`F-CUT-004 API/Physical/Legacy Machine Contract Gate`；该Gate通过后再申请Feature Ready最终裁决。不得提前生成Technical Plan或实现。
