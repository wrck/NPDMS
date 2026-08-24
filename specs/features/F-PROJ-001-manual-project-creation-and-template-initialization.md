# F-PROJ-001 手动项目创建与模板初始化 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY`
> Technical Plan：由目标实现仓库基于当前正式规格重新生成；禁止使用2026-08-21旧计划
> Implementation Start：`SATISFIED`（NPDMS 已锁定包含`CHG-PRD-2026-08-23-002`的规格提交）
> Implementation Done：`PASS`（NPDMS `1c76050`；2026-08-25 创建人详情访问集成回归已完成独立 `GO`）
> 已关闭问题：`Q-FPROJ-001`（方案B：创建失败不持久化草稿）、`Q-FPROJ-002`（跨Context同步同事务、全有或全无）
> Requirement：`PM-01`、`PM-03`
> 关联边界：`PM-08`仅引用V1人工确认服务经理的边界，不覆盖V2自动指派
> Owner Context：`PROJ（项目治理）`
> Gate Owner：需求方关闭业务语义问题；项目治理Feature负责人关闭其余DoR并在实施启动前登记具体责任人
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`

本Feature同时适用已批准PRD增量`CHG-PRD-2026-08-21-001`与`CHG-PRD-2026-08-23-002`。前者替代PM-01、PM-03的项目创建草稿语义；后者补齐公司、部门、站点、地点解析和AST所有权契约。

## 1. 业务价值与目标

工程管理部在CRM/ERP接口不可用、紧急立项或内部项目场景下，通过平台录入项目基本信息，选择适用的已发布项目模板，并在一次受控创建中形成项目、冻结模板/流程版本、实例化阶段/任务/里程碑/交付件/门禁以及人工确认服务经理。成功结果是可审计、可查询且处于`ACTIVE / S0`的正式项目，不产生接口侧或线下台账旁路。

本Feature验证第一条业务Vertical Slice中的项目治理核心段：

```text
项目创建表单
-> 候选模板与预览
-> 服务端权限/幂等/版本校验
-> 正式项目与模板内容原子实例化
-> 人工确认服务经理或明确保持UNASSIGNED
-> 项目详情、权限和审计
```

## 2. 权威输入与约束

实现和验收必须同时遵守：

- `docs/baseline/prd-v1.8.md`的PM-01、PM-03；
- `docs/engineering/00-engineering-chain.md`的Feature Ready、DoR和DoD；
- `docs/design/04-module-design.md`、`05-state-machine.md`、`07-authorization-design.md`；
- `docs/design/08-data-model.md`、`09-database-design.md`、`10-api-design.md`；
- `docs/design/15-cache-and-concurrency.md`、`16-exception-and-idempotency.md`；
- `docs/decisions/0020-project-code-identity-and-namespace.md`、`0029-stage-task-work-binding-workbench.md`、`0030-project-task-execution-contract-and-cutover-checklist-carriers.md`、`0032-manual-project-creation-cross-context-atomicity.md`。

如本Feature与上述资产冲突，按`PRD > 工程链 > SDS > Feature Spec > Technical Plan > Task > Code`处理，不由实现自行选择。

## 3. Scope

### 3.1 包含范围

1. 工程管理部手动录入项目名称、客户、合同号（适用时），选择独立公司、办事处部门和一个或多个实施站点，并录入签约方式、项目类别、实施方式和创建原因；站点未维护时可显式使用待维护文本地点降级。
2. 无CRM来源时重大项目级别保持空/不适用，不伪造CRM权威字段。
3. 按四个独立业务维度和业务场景查询当前租户可用的已发布模板候选。
4. 预览候选模板的阶段、任务、里程碑、交付件和门禁摘要。
5. 显式选择适用模板；未显式选择时仅允许唯一命中的默认模板。
6. 生成租户内唯一且创建后不随项目层级变化的项目编码。
7. 冻结模板revision、流程定义及相关规则版本。
8. 原子实例化Project、Stage、ProjectTask、Milestone、Deliverable、Gate以及每个任务的执行契约。
9. 初始化正式项目为`lifecycle_status=ACTIVE`、`current_stage=S0`。
10. V1人工确认服务经理；未满足全部主责指派时保持`assignment_status=UNASSIGNED`。
11. 查询创建结果与项目详情，并记录权限、模板选择、创建、指派和失败审计。

### 3.2 Out of Scope

- CRM/ERP自动同步、失败重试和恢复后的来源补关联闭环；
- PM-07自动业务属性识别；本Feature只消费人工录入的独立属性；
- PM-08 V2服务经理自动匹配算法；
- 项目经理指派、完整团队组建和成员批量变更；
- 项目拆分、主子项目树移动与进度汇总；
- 模板草稿编辑、校验、发布和停用后台；
- S0之后的阶段推进、任务办理和任务完成；
- 历史数据迁移、数据切换、UAT和生产发布；
- V2/V3能力。

## 4. 前置依赖

| 依赖 | 最小可用条件 | 未满足时处理 |
|---|---|---|
| 身份与租户 | 已认证主体和租户上下文可用 | 拒绝请求，不建立业务事实 |
| 功能与数据权限 | 服务端可判定项目创建、模板候选和项目范围 | `AUTHORIZATION_DENIED`，不依赖前端隐藏 |
| 客户/组织主数据 | 客户、公司、办事处部门可查询并带稳定ID/编码/版本；公司与部门从同一授权范围校验 | 保持表单未提交；不得保存伪造主数据或由部门推导公司 |
| 资产地点主数据 | AST可查询Address/Site/SiteLocation并校验版本；未维护站点允许显式`UNRESOLVED`文本降级 | 结构化引用无效时拒绝；未解析文本不得参与自动指派或结构化权限判断 |
| 项目模板 | 至少一个完整、已发布且适用的revision | 拒绝创建，不持久化Project或创建草稿 |
| 编码、审计、幂等 | 服务端编码、审计、`plt_idempotency_record`能力可用 | Feature不得标记READY |
| 实现仓库 | 可构建的后端、前端、迁移与测试工程存在 | 当前规格仓库无业务源码；实施启动前另行验证 |

## 5. 业务规则

### BR-FPROJ-001 创建主体与来源

- 只有具备工程管理部项目创建权限的已认证主体可以提交。
- 手动创建必须保存创建原因、创建人、创建方式和时间。
- 手工输入不能冒充CRM/ERP权威字段；无CRM来源时重大项目级别为空/不适用。

### BR-FPROJ-002 项目编码

- 正式项目生成租户内唯一编码；编码命名空间与项目层级分离。
- 项目移动、改名、指派或后续补关联均不得改变项目编码。
- 数据库唯一约束是最终防线；编码碰撞只允许在同一幂等operation内受控重试。
- 失败请求不分配可见项目编码，不持久化预分配编码或Project草稿。

### BR-FPROJ-003 模板候选与选择

- 候选必须属于当前租户可见范围，状态为已发布/生效，并匹配签约方式、项目类别、实施方式、重大项目级别及适用业务场景。
- 客户端传入的候选、模板状态和匹配结论均不可信；提交时服务端重新校验。
- 显式选择的revision必须仍属于当前候选。
- 未显式选择时，仅允许采用唯一命中的默认模板。
- 无匹配或同优先级多匹配时不得静默选择或实例化。

### BR-FPROJ-004 模板冻结与实例化

- 创建时冻结模板revision、流程定义版本、Stage/Task定义版本、WorkBinding、PermissionPolicy、CompletionRule和GateRef。
- 每个项目任务必须且只能生成一个当前有效执行契约。
- `TASK_NATIVE`不配置外部目标；其他绑定只能保存受控目标引用和最小快照，不复制Owner业务正文。
- 绑定缺失、类型与字段不一致、目标未发布、CompletionRule不可解析或GateRef失效时，正式项目创建整体拒绝。

### BR-FPROJ-005 正式项目原子创建

以下事实必须在PROJ本地事务中原子提交：

```text
Project
+ Template/Workflow frozen references
+ Stage instances
+ ProjectTask instances
+ Milestone instances
+ Deliverable instances
+ Gate instances/references
+ Task execution contracts
+ Idempotency success record
+ Audit and Outbox
```

交付件事实仍由ACC Context拥有。PROJ不得直接访问ACC Repository，而是在同一数据库、同一Spring事务中同步调用ACC公开的内部应用接口，由ACC写入交付件实例。该内部接口必须参与调用方事务，不得异步化、另起事务或吞掉异常。

任一正式实例化步骤失败时整体回滚，不允许留下只有Project主记录、缺少执行契约、缺少交付件或交付件未完成初始化的半成品。创建命令不提供`PENDING`、`INITIALIZING`、后台补偿成功或“Project已成功但交付件稍后生成”等中间结果。

### BR-FPROJ-006 服务经理人工确认

- V1只实现工程管理部按PRD规则人工确认服务经理，不实现自动匹配算法。
- 单省份项目可按主站点的`area_code + area_level`精确映射办事处部门候选；多省份/多办事处项目按各站点提示候选。本Feature只保存授权人员人工确认的合法候选，不自动决定服务经理，不做父级区划回退。
- 指派命令必须记录角色层级、责任范围、生效区间、前后值和操作人。
- 只要PRD要求的主责指派未全部完成，项目保持`UNASSIGNED`；本Feature不以只指派服务经理伪装S0指派完成。

### BR-FPROJ-007 创建失败不持久化

依据`CHG-PRD-2026-08-21-001`：

- 无模板、多默认、显式模板不再适用、模板引用失效、必填字段或主数据校验失败时，不创建Project或任何项目创建草稿；
- 不生成可见项目编码，不进入S0，不实例化Stage/Task/Milestone/Deliverable/Gate；
- 服务端返回具体错误和可修正项；页面可在未刷新期间以内存状态保留输入，便于修正后使用新`Idempotency-Key`重新提交；
- 不写服务端草稿、`localStorage`、`IndexedDB`、离线缓存或其他刷新后可恢复的创建草稿；
- Project不新增`DRAFT`状态，也不新增`ProjectCreationDraft`聚合、API、表、事件或迁移。

## 6. 状态与命令

### 6.1 正式Project

| 命令 | 前置 | 结果 | 禁止事项 |
|---|---|---|---|
| `CreateManualProject` | 权限、幂等、字段、主数据、模板revision及全部引用校验通过 | 创建`ACTIVE / S0 / UNASSIGNED`正式项目和完整实例 | 不创建无模板Project；不直接进入S1 |
| `AssignProjectManagerRole`（role=SERVICE_MANAGER） | Project版本匹配、操作者有权、候选人在允许范围 | 追加/关闭ProjectMemberAssignment有效区间并更新负责人投影 | 不自动推导候选；不覆盖历史 |

`display_status`仅为只读派生值。通用`PATCH /projects/{id}`不得修改`lifecycle_status`、`current_stage`或`assignment_status`。

### 6.2 失败状态

创建校验失败只返回失败结果和可修正项，不形成业务生命周期状态。修正后是新的创建命令，不是草稿提交或状态迁移。

## 7. 权限与数据范围

| 操作 | 允许主体 | 服务端校验 | 负向要求 |
|---|---|---|---|
| 查询候选/预览模板 | 有项目创建权限的工程管理部人员 | tenant、四维条件、业务场景、模板发布状态 | 不返回其他租户、草稿/停用模板或敏感配置正文 |
| 创建正式项目 | 有项目创建权限的工程管理部人员 | tenant、客户/组织范围、字段权限、模板候选、幂等 | 普通成员、未授权办事处和跨租户请求拒绝 |
| 人工确认服务经理 | 有V1指派权限的工程管理部人员 | Project版本、角色范围、办事处/实施地点范围 | 被指派服务经理不能借此改来源字段或模板版本 |
| 查看项目详情 | 创建人及实时ProjectTreeScope内主体 | 每次查询按当前权限裁剪 | 项目名称可见不推导任务、商务、文件或敏感字段权限 |

WorkBinding不授予新权限。模板预览只返回创建决策所需摘要，不返回任意脚本、Repository名、未授权业务正文或秘密。

## 8. API契约

路径继承SDS `/api/v1/pms`前缀；本节只细化已有PROJ API，不创建第二套路由。

### 8.1 候选模板

`GET /project-templates`

输入：签约方式、项目类别、实施方式、重大项目级别（可空/不适用）、业务场景、客户、公司、办事处部门及站点稳定ID/版本；未解析时使用明确的文本降级标记。
输出：适用的已发布revision摘要、匹配维度、优先级、是否默认以及预览引用。
约束：服务端过滤tenant和权限；结果为空或多默认不代表创建成功。

### 8.2 模板预览

`GET /project-templates/{revisionId}`

输出仅包含创建决策所需的模板元数据、Stage→Task、里程碑、交付件、门禁和流程版本摘要；不得返回可执行脚本或其他Context正文。

### 8.3 创建正式项目

`POST /projects`

必需Header：`Idempotency-Key`。
请求至少包含：项目基本信息、独立四维属性、去除首尾空白后仍非空的创建原因、公司与办事处部门稳定引用、零到多个站点（含一个可选主站点）或未解析文本地点、选定`templateRevisionId`（可省略仅限唯一默认）、候选查询水位/版本以及可选的服务经理人工确认信息。
响应包含：Project稳定ID、项目编码、`ACTIVE / S0 / UNASSIGNED`、冻结模板/流程版本、实例化数量摘要、服务经理确认结果、Project版本和详情链接。

F-PROJ-004生效后继续保留授权用户从本次合法候选中显式选择`templateRevisionId`的能力：候选唯一且未显式选择时记`AUTO_UNIQUE`，显式选择合法候选时记`EXPLICIT_SELECTION`。所有成功创建路径都必须在本事务内追加`INITIAL_CREATE`模板匹配决策历史；无匹配或多匹配且未选择合法候选时拒绝创建。

### 8.4 人工确认服务经理

`POST /projects/{id}/actions/assign-manager`

必需Header：`Idempotency-Key`、`If-Match`。
请求包含：`roleCode=SERVICE_MANAGER`、人员稳定ID、一级/二级层级、`siteId/departmentCode`责任范围和生效时间；区划映射仅提供候选，最终值由授权人员确认。
响应包含新Project版本、当前关系引用和`assignment_status`；不得仅因服务经理已确认就把未完成主责项目标记为`ASSIGNED`。

### 8.5 项目详情

`GET /projects/{id}`

按ProjectTreeScope和字段权限返回项目、冻结版本、实例化摘要、负责人及审计摘要。响应中的`allowedActions`由服务端实时计算。

### 8.6 明确不提供草稿API

不提供`/project-creation-drafts`、草稿提交命令或草稿恢复接口。模板匹配/校验错误由`POST /projects`返回，前端在当前页面展示并保留内存表单供修正。

## 9. 数据变化与事务边界

本Feature预期使用SDS定义的下列Owner事实；实际Flyway表名和约束必须在Technical Plan中以当前Schema核对后确定：

- Project及项目编码唯一约束；
- ProjectTemplate发布revision及Stage/Task定义；
- Stage、ProjectTask、Milestone、Deliverable、Gate实例；
- `proj_project_task_execution_contract`；
- ProjectMemberAssignment时态关系；
- `plt_idempotency_record`、`plt_operation_audit`、`plt_outbox_event`。

正式创建使用一个同库本地事务。PROJ负责Project、Stage、ProjectTask、Milestone、Gate冻结引用、任务执行契约、幂等、审计和Outbox；ACC通过同步内部应用接口在该事务中负责交付件实例。PROJ不得访问ACC Repository，ACC也不得反向修改Project状态。

该全有或全无语义要求PROJ与ACC写模型在本Feature实施和部署时共享同一MySQL事务资源。若后续拆分数据库或服务，必须先经批准变更本Feature完成语义；不得以Saga、最终一致性、`PENDING`状态或分布式消息替代。跨Context主数据仍只保存稳定引用和必要快照。本Feature无项目创建草稿数据变化。

## 10. 幂等、并发与错误语义

### 10.1 幂等

- 作用域：`tenant + POST /projects + actor + Idempotency-Key`。
- 同键同规范化摘要且首次成功：返回原Project和响应摘要。
- 同键处理中：返回同一operation，不并发执行第二次。
- 同键不同摘要：`PMS-COMMON-IDEMPOTENCY-0001`，不覆盖首次请求。
- 摘要不保存秘密或不必要个人信息。

### 10.2 并发

- 提交时重读模板revision、发布状态、候选条件和全部引用；预览缓存不能作为真值。
- 模板revision不可变；停用只阻止新项目使用，不影响已创建实例。
- 指派使用Project `If-Match`/expectedVersion，冲突返回`VERSION_CONFLICT`，不最后写入覆盖。

### 10.3 错误分类

| 场景 | 错误语义 | 业务结果 |
|---|---|---|
| 未认证/无权限/跨租户 | AUTHENTICATION/AUTHORIZATION | 不创建事实，不泄露对象存在性 |
| 字段或主数据无效 | VALIDATION/DATA_QUALITY | 不创建正式Project |
| 无模板/多默认/显式模板不再适用 | BUSINESS_GATE | 不持久化Project或创建草稿；不进入S0 |
| 模板引用、WorkBinding或CompletionRule无效 | BUSINESS_GATE | 正式创建整体拒绝 |
| 同键不同摘要 | IDEMPOTENCY_CONFLICT | 返回冲突，不覆盖原operation |
| 模板/Project版本变化 | VERSION_CONFLICT | 返回当前版本，要求重新加载 |
| ACC交付件初始化不可用或任一交付件写入失败 | RETRYABLE或FAILED_FINAL | 同一事务整体回滚；不存在Project或交付件中间状态 |
| 本地事务失败 | RETRYABLE或FAILED_FINAL | 回滚正式创建；不得宣称成功 |

## 11. 审计、事件与可观测性

必须追加记录：

- 主体、租户、correlationId、Idempotency-Key摘要和创建方式；
- 创建原因、项目编码生成结果和字段来源摘要；
- 候选模板、匹配依据、最终revision及流程/规则冻结版本；
- Stage/Task/Milestone/Deliverable/Gate/执行契约实例化数量；
- 服务经理指派前后值、责任范围、操作者和时间；
- 权限拒绝、模板冲突、版本冲突、幂等冲突和事务回滚原因。

只有Project、Stage/Task/Milestone/Gate、任务执行契约和ACC交付件实例全部写入成功后，事务内才写`ProjectCreated` Outbox事件并提交。ACC交付件初始化失败时不得写成功事件或成功幂等记录。通知投递或事件发送成功不构成项目创建成功；消费者失败不回滚已经完整提交的业务事务，但必须幂等重试。

日志不得记录未脱敏客户敏感字段、认证信息、Token或秘密。

## 12. 验收标准

### AC-FPROJ-001 候选与预览

给定合法四维属性和业务场景，授权创建人只能看到当前租户适用的已发布模板；预览展示阶段、任务、里程碑、交付件和门禁摘要。跨租户、停用、草稿和不适用revision不可见。

### AC-FPROJ-002 显式模板创建

给定合法字段和适用的显式模板revision，提交后仅生成一个项目编码；Project为`ACTIVE / S0`，冻结所选模板/流程版本并在同一事务中完整实例化Stage、Task、Milestone、Deliverable、Gate和任务执行契约。响应不包含交付件初始化中间状态。

### AC-FPROJ-003 唯一默认模板

未显式选择模板且只有一个默认候选时，系统采用该revision；无候选或多默认时不静默选择，不生成正式Project或创建草稿。

### AC-FPROJ-004 WorkBinding完整性

每个ProjectTask恰好一个当前执行契约；`TASK_NATIVE`无外部目标，其他类型保存合法受控引用。任一绑定/规则/门禁失效导致创建整体回滚。

### AC-FPROJ-005 V1人工确认服务经理

授权主体可按办事处/实施地点范围人工确认服务经理，历史关系和审计保留；未完成全部主责指派时`assignment_status`仍为`UNASSIGNED`。

### AC-FPROJ-006 幂等与并发

相同Key和请求只产生一个Project并重放首次结果；同Key不同请求返回冲突；模板或Project版本变化返回版本冲突且不覆盖当前事实。

### AC-FPROJ-007 权限负向

普通成员、未授权办事处、跨租户主体及仅有模板维护权限的主体均不能创建或指派项目；前端隐藏按钮不是服务端授权证据。

### AC-FPROJ-008 原子失败

模拟任一Stage/Task/Milestone/Gate/执行契约持久化失败，或ACC同步交付件初始化接口不可用、超时、抛错、少写一项，数据库均不存在残缺正式Project、任何交付件实例、成功幂等记录或虚假`ProjectCreated`事件。

### AC-FPROJ-009 真实界面闭环

真实浏览器完成候选加载、预览、创建、刷新详情和服务经理人工确认；检查页面正文、控制台错误、失败请求、按钮权限、刷新后持久化结果及审计记录。构建成功、HTTP 200或Mock不替代此验收。

### AC-FPROJ-010 创建失败无持久化

无模板、多默认、模板失效或字段校验失败时，数据库不存在Project、项目创建草稿、项目编码、Stage/Task/Milestone/Deliverable/Gate实例、成功幂等记录或`ProjectCreated`事件。页面在不刷新时保留当前内存表单和逐项错误；刷新后不恢复，浏览器持久化存储中不存在该表单。

## 13. 测试与证据要求

| 类别 | 最小覆盖 |
|---|---|
| Unit | 模板候选、唯一默认、字段来源、编码、WorkBinding完整性、assignment派生 |
| Integration | 正式创建事务、Flyway约束、幂等重放/冲突、模板并发停用、指派版本冲突、Outbox |
| Authorization Negative | 普通成员、跨租户、未授权组织、模板维护权不等于项目创建权 |
| Business Negative | 无模板、多默认、模板失效、绑定缺失、规则失效、主数据失效、残缺实例回滚 |
| API Contract | `/projects`、模板查询/详情、`assign-manager`错误与版本语义 |
| Browser E2E | 表单、模板预览、正式创建、刷新、详情、指派、错误提示、权限和审计 |
| Security | 越权、敏感字段/日志、Idempotency摘要、输入校验 |

每项证据必须回指`PM-01`或`PM-03`以及本Feature AC编号。

## 14. Definition of Ready

| 条件 | 当前证据 | 状态 |
|---|---|---|
| Requirement、Scope、Out of Scope | 本文第1～3节 | PASS |
| Business Rules、State、Permission | 本文第5～7节 | PASS |
| API、Data Change | 本文第8～9节 | PASS |
| Integration Contract | 本Feature不执行CRM/ERP；公司、部门和AST地点使用稳定公开API | PASS |
| Acceptance Criteria | 本文第12节 | PASS |
| 相关Open Question | Q-FPROJ-001、Q-FPROJ-002均已关闭 | PASS |
| 实现基线 | 当前仓库无业务源码；由Technical Plan登记目标工程和启动前检查 | PLAN_INPUT |

结论：`BASELINE / READY`。方案B、跨Context同步全有或全无语义以及组织/AST地点契约均已关闭。目标实现仓库必须重新生成Technical Plan，并在规格快照锁定包含`CHG-PRD-2026-08-23-002`的提交后进入Implementation；不得沿用2026-08-21旧计划判断完成度。

## 15. 追溯

| Requirement | Feature规则/AC | SDS | 后续Code/Test |
|---|---|---|---|
| PM-01 | BR-FPROJ-001/002/005/006/007；AC-FPROJ-002/005/006/007/008/009/010 | 04/05/07/08/09/10/15/16分册 | Technical Plan生成后登记 |
| PM-03 | BR-FPROJ-003/004/005/007；AC-FPROJ-001/003/004/008/010 | 04/05/07/08/09/10/15/16分册；ADR-0029/0030/0032 | Technical Plan生成后登记 |

PM-08只作为V1人工确认与V2自动指派的范围边界，不宣称本Feature完成PM-08需求。

### 2026-08-25 创建人详情访问集成回归

后续统一 ProjectTreeScope 接入后，手工根项目创建路径未同步发布首版树投影，且创建人
基础查看范围未被合并，造成创建成功后无法立即打开详情。NPDMS 已在原 Feature 边界内
关闭该回归：根项目创建事务同步生成版本 1 与自路径；创建人仅进入 `VIEW` 范围，
不新增项目成员、显式授权或 `MANAGE` 权限。定向单测、真实 MySQL、项目模块完整回归及
真实 Chromium 创建后首次进入与刷新均通过，独立裁决结论为 `GO`。该修复不改变
F-PROJ-003 的项目角色与子树授权分离模型。

## 16. 已关闭问题

- `Q-FPROJ-001`：需求方选择方案B。创建失败不持久化Project或创建草稿；批准依据为`CHG-PRD-2026-08-21-001`。
- `Q-FPROJ-002`：需求方确认创建时同步完成PROJ与ACC初始化，要么全部完成，要么全部回滚，不允许中间状态。实现必须采用同库同Spring事务的同步内部应用接口，不得改为最终一致性。
