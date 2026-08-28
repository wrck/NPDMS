# F-PROJ-006 项目回退、异常关闭与受控重开 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FPROJ006-FEATURE-READY-20260825-01`
> Requirement：`PM-10（V1）`
> 关联Requirement：`PM-01`、`PM-02`、`PM-04`、`PM-08`、`PM-11`、`CLO-02`；不宣称关联Requirement完成
> Owner Context：`PROJ（项目治理）`
> 前置Feature：`F-PROJ-001`～`F-PROJ-005`均已完成
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成；V1.7治理动作实现只作存量审计证据

## 1. 目标

为异常项目提供三种受控状态动作：服务经理回退本人主责项目、工程管理部异常关闭不再跟踪的项目、工程管理部重新开启异常关闭项目。所有动作保留项目编码与历史事实，使用V1.8分离状态轴、完整树和跨域守卫，禁止把异常关闭冒充正常闭环。

## 2. Scope

### 2.1 包含

- `ACTIVE`项目回退至S0并重新进入待指派；
- `ACTIVE`项目在完整关闭守卫通过后进入`EXCEPTION_CLOSED`只读状态；
- `EXCEPTION_CLOSED`项目受控重开为`ACTIVE`，恢复关闭前阶段并重新进入待指派；
- 全部后代、在途审批、项目任务、割接、巡检等当前已实施权威事实的阻断检查；
- 不可变动作快照、幂等、乐观锁、租户与ProjectTreeScope、审计及Outbox事件；
- 项目详情中的响应式治理面板、守卫明细和动作历史。

### 2.2 不包含

- CLO-01/02正常闭环、`NORMAL_CLOSED`重开或正常闭环事实修改；
- PM-11任务建设、跨域任务中止/撤回/移交本身；
- 自动恢复已经终止的外部任务；
- 独立治理审批流、责任工单表或新的项目状态轴；
- PM-05/06/09、Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FPROJ006-001 回退

- 仅当前`lifecycle_status=ACTIVE`、存在有效主责服务经理且操作者就是该主责服务经理时允许回退；工程管理部可查询但不代替服务经理发起。
- 请求必须包含`reasonCode/reasonDetail/reassignmentRequirement`。原因编码使用可配置字典，原因说明及重新指派要求不得为空。
- 回退前执行`ROLLBACK`守卫；存在不可逆交付结果、进行中审批或不能安全保留的活动任务时拒绝。守卫不可用或超时同样拒绝。
- 成功后保持`lifecycle_status=ACTIVE`，设置`current_stage=S0`、`assignment_status=UNASSIGNED`，以同一事务时间结束该项目节点全部有效服务经理主责/协同区间；不清空或覆盖项目经理历史事实。
- 已有任务、设备、文件引用、进度和阶段快照均保留，项目编码不变；本Feature不删除、回退或重建外域事实。

### BR-FPROJ006-002 异常关闭

- 仅工程管理部关闭岗可关闭`ACTIVE`项目。服务经理可提交回退，不能直接关闭。
- 请求必须包含`reasonCode/reasonDetail/businessBasis/legacyItems`；遗留事项以结构化数组保存`type/summary/owner/status`快照，允许明确“无遗留事项”。
- 使用F-PROJ-002完整树版本检查全部后代。任一后代仍为`ACTIVE`、树投影未形成完整版本或调用者无权完成完整守卫时拒绝；不得用权限裁剪后的可见子集冒充完整后代集合。
- BPM审批、PM-11项目任务、CUT割接与INS巡检等当前已实施提供方通过公开只读守卫接口返回活动阻断。任一当前必需提供方缺失、异常、超时或返回未知状态时按阻断处理。
- `COLLECTION`继续预留同一公共守卫接口，但INT-12尚未进入Implementation时不属于F-PROJ-006当前必需提供方，不前置建设采集任务、端点或适配器，也不以占位不可用结果阻断本Feature。INT-12后续实施时由PLT/DAC权威事实接入并单独完成联动验收。
- PM-10只判断是否可关闭，不代替提供方中止、撤回或移交任务。处理完成后调用方以同一幂等意图重试关闭。
- 守卫通过后保存动作及阻断检查快照，将`lifecycle_status`置为`EXCEPTION_CLOSED`、`assignment_status`置为`UNASSIGNED`，并以同一事务时间结束该节点全部有效服务经理主责/协同区间；`current_stage`保留为关闭前阶段，页面及写命令依据生命周期进入只读。异常关闭不计入正常闭环率。

### BR-FPROJ006-003 受控重开

- 仅工程管理部关闭岗可重开`EXCEPTION_CLOSED`；`NORMAL_CLOSED`、`ACTIVE`及未知状态均拒绝。
- 重开必须填写`reasonCode/reasonDetail`，并引用本项目最近一次有效异常关闭快照；关闭快照缺失、租户不一致或已被后续重开消费时拒绝。
- 成功后设置`lifecycle_status=ACTIVE`，恢复异常关闭快照中的`before_stage`，并设置`assignment_status=UNASSIGNED`。既有已结束服务经理关系及外域任务不自动恢复。
- “新的责任处理事项”由`ACTIVE + UNASSIGNED`及`ProjectStageChanged(action=REOPEN)`驱动既有待指派视图和站内通知，不新增独立责任工单表；后续人工指派继续使用F-PROJ-005。

### BR-FPROJ006-004 快照、并发与幂等

- `proj_project_stage_snapshot`是PM-03、PM-10与EXE-06共享表，继续使用既有`uk(tenant_id, project_id, stage_code, snapshot_no)`。PM-10只以前向可空字段加法保存`ROLLBACK/EXCEPTION_CLOSE/REOPEN`动作；非PM-10快照不要求填写动作字段。
- PM-10通用动作字段为前后阶段、生命周期、指派状态、`reason_code/reason_detail`、操作者、操作时间和稳定`operation_id`；回退另填重新指派要求，异常关闭另填业务依据和遗留事项，回退/关闭另填守卫、完整树版本及提供方事实版本，重开另填被消费关闭快照ID。每个动作的API到字段映射以机器契约为准。
- 快照只追加不覆盖。重开通过`related_snapshot_id`关联被重开的异常关闭快照；一个异常关闭快照最多成功重开一次。
- 写命令必须提供`Idempotency-Key`和`If-Match`。回退/关闭还必须提交最近一次守卫查询返回的`guardToken`；同键同请求返回首次结果，同键异请求冲突。
- `guardToken`冻结项目版本、完整树根与`treeVersion`、每个必需提供方的`factVersion/watermark/factDigest`及检查时间。命令提交前服务端重新读取最新完整树和全部提供方事实；任一版本、水位或摘要变化返回`VERSION_CONFLICT`，不得用旧守卫结果继续写入。
- Project版本CAS与守卫令牌重验共同生效：Project自身并发由CAS拒绝，树或跨域事实独立变化由令牌重验拒绝。Project状态、成员区间、动作快照、幂等成功、`plt_operation_audit`和Outbox在同一事务写入。任一失败保持原有效事实不变。

### BR-FPROJ006-005 权限、只读与跨模块边界

- 查询按当前租户和ProjectTreeScope裁剪；关闭命令使用服务端完整树守卫，但响应只返回调用者有权查看的最小阻断引用，不泄露未授权对象正文。
- `EXCEPTION_CLOSED`与`NORMAL_CLOSED`项目的PROJ写命令默认拒绝；仅PM-10重开命令可改变`EXCEPTION_CLOSED`。其他模块继续由自身权限和状态守卫负责。
- PROJ不得直查BPM、CUT、SCH、INS等业务表，不依赖其`-biz`、Service、Mapper或Repository。守卫只通过提供方公开应用API；返回`provider/objectType/objectId/status/code/summary`最小结果。
- 单租户/多租户沿用基础框架同一租户上下文和MyBatis租户拦截，不在业务代码按配置分支获取租户。

### BR-FPROJ006-006 事件与审计

- 回退和重开发布`ProjectStageChanged`；异常关闭发布`ProjectClosed(lifecycleStatus=EXCEPTION_CLOSED)`。payload在事务内冻结项目ID、动作、前后状态、项目版本、动作快照ID、操作者和发生时间。
- 工程管理部待指派通知由Outbox消费者读取冻结payload发送；通知失败不回滚已提交状态动作，重复投递按eventId幂等。
- 审计保存命令、前后状态、原因、守卫结果摘要、操作者、时间、operationId和traceId；阻断请求可记录拒绝审计，但不得产生成功快照、状态变化或成功事件。

### BR-FPROJ006-007 稳定权限矩阵

| 能力 | 功能权限码 | ProjectTreeScope动作 | 附加主体约束 |
|---|---|---|---|
| 守卫查询 | `pms:project:governance:query` | `VIEW` | 仅返回目标节点和有权阻断摘要；完整守卫由服务端内部执行 |
| 动作历史 | `pms:project:governance:query` | `VIEW` | 业务依据和遗留事项仍按原项目权限只读 |
| 回退 | `pms:project:rollback` | `MANAGE` | 操作者必须是目标节点当前有效主责服务经理 |
| 异常关闭 | `pms:project:close` | `MANAGE` | 操作者必须具备工程管理部关闭岗功能权限 |
| 受控重开 | `pms:project:reopen` | `MANAGE` | 操作者必须具备工程管理部关闭岗功能权限 |

- 前端按钮、路由和接口调用使用上述同一权限码；后端按“租户→功能权限→ProjectTreeScope动作→主体/状态/版本→业务守卫”顺序重新校验，前端隐藏不代替服务端授权。
- 不从角色名称推导权限，不新增“服务经理/关闭岗即全项目可操作”的隐式规则。角色只通过基础平台授权获得功能权限，项目范围继续由F-PROJ-003真值决定。

## 4. API契约

所有路径继承`/api/v1/pms`前缀。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/projects/{id}/governance-guard` | `GET` | 权限`pms:project:governance:query + VIEW`；参数`action=ROLLBACK/EXCEPTION_CLOSE/REOPEN`；返回Project版本、当前状态、`allowed`、`guardToken/treeVersion/providerFacts`及分页阻断摘要；提供方未知/超时明确返回阻断，不降级为通过 |
| `/projects/{id}/actions/rollback` | `POST` | 权限`pms:project:rollback + MANAGE + 当前主责`；Header必填`Idempotency-Key/If-Match`；请求含`guardToken/reasonCode/reasonDetail/reassignmentRequirement` |
| `/projects/{id}/actions/close` | `POST` | 权限`pms:project:close + MANAGE`；Header必填`Idempotency-Key/If-Match`；请求含`guardToken/reasonCode/reasonDetail/businessBasis/legacyItems`；提交前重验树与全部提供方事实 |
| `/projects/{id}/actions/reopen` | `POST` | 权限`pms:project:reopen + MANAGE`；Header必填`Idempotency-Key/If-Match`；请求含`reasonCode/reasonDetail/exceptionCloseSnapshotId`；只允许消费最近有效异常关闭快照 |
| `/projects/{id}/governance-history` | `GET` | 分页返回调用者可见的动作、前后状态、原因摘要、操作者和时间；遗留事项及业务依据按原项目权限只读 |

提供方守卫接口为只读批量契约：`inspect(ProjectGovernanceGuardQuery)`。请求至少包含受信任租户上下文、`projectIds`、动作和检查时点；响应包含提供方稳定`factVersion/watermark/factDigest`及逐项阻断引用。提交重验必须再次调用同一接口并逐提供方比较；空Project集合返回空结果，禁止省略条件扩大查询。

## 5. 数据与物理边界

机器契约：`specs/features/F-PROJ-006-physical-contract.json`。

- 复用`proj_project.current_stage/lifecycle_status/assignment_status/version`，不新增统一`status_code`或`archive_status`；只读归档由生命周期状态派生。
- 前向创建共享`proj_project_stage_snapshot`时保持已批准公共字段和`uk(tenant_id, project_id, stage_code, snapshot_no)`；PM-10新增列物理上均可空，只对PM-10动作按机器契约校验必填。另加`uk(tenant_id, operation_id)`，不得替换共享唯一键。
- 回退和异常关闭均结束`proj_project_member_assignment`当前服务经理区间，不删除历史，不清空项目经理事实。
- V1.7 `pms_project_governance_action`的状态轴、审批草稿和整数Project状态与V1.8不兼容，只作存量审计输入；不得据此勾选AC，不自动转换为当前快照。
- 所有迁移使用新Flyway版本，不修改已执行SQL。新增查询遵守场景Query、LambdaQueryWrapperX/XML及空范围返回空结果规则。

## 6. UI

- 在项目详情复用Yudao卡片、Descriptions、Table、Dialog/Drawer、Form和权限组件，新增“异常治理”区域，不另造独立视觉体系。
- 动作按钮由权限与服务端状态共同控制；守卫结果展示对象类型、编号、状态和处理提示，不展示无权正文。
- 异常关闭项目展示只读标识、关闭原因摘要、遗留事项和重开入口；正常闭环项目不显示PM-10重开入口。
- 320/768/1024/1440宽度无页面级横向溢出，样式使用Element Plus主题变量，减少内联样式。

## 7. 验收标准

- `AC-FPROJ006-001`：本人主责服务经理可回退合法ACTIVE项目；成功后为`ACTIVE/S0/UNASSIGNED`，服务经理区间结束，项目经理、任务、设备、文件、进度和编码不被删除或覆盖。
- `AC-FPROJ006-002`：非主责服务经理、普通成员、跨租户或越权项目回退均拒绝且无成功副作用；不可逆结果或守卫不可用时同样拒绝。
- `AC-FPROJ006-003`：关闭岗只能在完整树无ACTIVE后代且所有必需提供方无活动阻断时关闭；成功保存依据、遗留事项和守卫快照并进入`EXCEPTION_CLOSED`只读状态。
- `AC-FPROJ006-004`：存在未关闭后代、审批、项目任务、割接、巡检或当前必需提供方未知/超时时返回最小阻断明细；守卫后树版本或任一当前必需提供方事实版本/水位变化时提交返回`VERSION_CONFLICT`，Project、快照、事件和幂等成功事实均不变化。`COLLECTION`待INT-12实施后按同一契约纳入，不作为本Feature当前验收前置。
- `AC-FPROJ006-005`：只能重开最近有效`EXCEPTION_CLOSED`快照；成功恢复关闭前阶段并进入`ACTIVE/UNASSIGNED`，生成待指派事件但不恢复成员关系或外域任务；`NORMAL_CLOSED`永远拒绝。
- `AC-FPROJ006-006`：动作快照append-only且前后状态、原因、遗留事项、树版本、操作者和operationId可追溯；异常关闭快照并发重开最多一个成功。
- `AC-FPROJ006-007`：同幂等键同请求重放不重复快照/审计/事件，同键异请求、旧If-Match及并发状态动作冲突无有效副作用。
- `AC-FPROJ006-008`：事件payload冻结且按eventId幂等；通知失败不回滚状态动作，重试不重建第二个业务事件。
- `AC-FPROJ006-009`：真实MySQL验证迁移、CAS、快照关联、区间结束和无副作用；真实浏览器验证三类动作、守卫、刷新持久化、权限负向与四档响应式。
- `AC-FPROJ006-010`：不宣称CLO-02、PM-11任务建设、跨域任务中止、PM-05/06/09、Deployment、SIT、UAT或Release完成。
- `AC-FPROJ006-011`：守卫、历史、回退、关闭和重开分别使用固定权限码及VIEW/MANAGE范围；前端与后端权限真值一致，缺少任一功能权限、范围或主体约束均拒绝且无成功副作用。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，不把失败测试作为实现前置；每个Task完成后按风险补齐自动化回归。完成证据至少包括服务/API自动化、跨域守卫契约测试、真实MySQL、并发与幂等负向、事件重试、真实浏览器响应式闭环和独立代码评审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PM-10三动作与CLO-02边界 | PASS |
| V1.7存量实现处置 | PASS |
| 状态、成员区间与不可变快照 | PASS |
| 完整树与跨域守卫 | PASS |
| API、权限、幂等、事件与UI验收 | PASS |
| 独立Feature Ready裁决 | PASS（`NPDMS-FPROJ006-FEATURE-READY-20260825-01`） |

结论：`BASELINE / READY`。三项原NO-GO阻断均已闭环，独立Feature Ready裁决已GO；本规格修订合入目标分支后创建全新Technical Plan。不重开已通过的PRD/SDS门禁，不得根据V1.7现有实现直接勾选任何AC。
