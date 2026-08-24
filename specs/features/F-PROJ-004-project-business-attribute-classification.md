# F-PROJ-004 项目业务属性判定、模板匹配历史与影响识别 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FPROJ004-FEATURE-READY-20260825-06`
> Requirement：`PM-07`
> 关联契约：`PM-01`、`PM-03`、`INT-01/INT-03`、`CHG-01`；不宣称关联Requirement完成
> Owner Context：`PROJ（项目治理）`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`及本Feature聚焦修正
> 前置Feature：`F-PROJ-001`（`IMPLEMENTATION_COMPLETE / PASS`）
> Technical Plan：Feature Ready且NPDMS锁定新规格提交后重新生成；不得使用历史计划或根据现有实现判定完成
> 实施状态：`NOT_STARTED`

## 1. 目标

复用现有签约方式、项目类别、项目实施方式、CRM重大项目级别和模板匹配能力，在正式Project创建与模板冻结之前完成属性判定，并以不可变模板匹配决策历史证明“当时使用了什么输入、为何命中该模板”。Project创建后属性发生受控变化时，再执行一次只读匹配评估并追加历史，用于识别对当前冻结模板的影响，但不重新实例化或覆盖交付事实。

## 2. 权威输入与边界

- PRD V1.8的PM-07；工程链与已批准SDS；
- PRD V1.8批准修订003（`CHG-PRD-2026-08-25-003`）；
- F-PROJ-001的正式Project、模板冻结及阶段/任务/审批/交付件全有或全无不变量；
- 独立裁决`NPDMS-FPROJ004-PM07-REEXAMINATION-20260825-03`及用户确认的最小闭环。

V1.7代码、字段、页面和测试只作复用审计证据。字段或匹配器已经存在不代表PM-07已完成，仍须检查值域、输入权限、来源证据和历史不可变性。

## 3. Scope

### 3.1 包含

- 复用`proj_project.signing_method/project_category/implementation_mode/major_project_level`四列，不新增同义业务属性；
- 模板匹配前的四属性判定和服务端输入守卫；
- 复用既有`TemplateMatcher`，不建立第二套匹配算法；
- 首次创建时的不可变`INITIAL_CREATE`决策历史；
- 创建后的受控属性修正、只读重新评估和不可变影响识别历史；
- 模板匹配历史分页查询；
- 项目类别值域纠偏及错误存量数据清查；
- 权限、幂等、乐观并发、事务、真实MySQL和真实浏览器验证。

### 3.2 不包含

- 正式Project上的待分类/待选模状态；
- 独立分类案例、独立模板影响表、分类工作台或重复当前属性详情API；
- 模板重新实例化、替换冻结模板、修改阶段/任务/审批/交付件/门禁；
- CHG事件、工单分派、处理和关闭；
- CRM自动建项、来源Project绑定、INT传输重试与端到端对账；
- PM-08、E2E、Deployment、SIT、UAT和Release。

## 4. 能力图

| Capability ID | 输入 | 输出 | 禁止职责 |
|---|---|---|---|
| `pre-template-attribute-resolution` | 手工或经未来INT编排提供的四属性输入、来源/映射元数据 | 可供既有TemplateMatcher使用的确定输入；成功时`INITIAL_CREATE`决策历史 | 不持久化待分类Project；不重复实现匹配器；判定失败不冻结模板 |
| `post-creation-attribute-history-impact` | 已存在Project、受控属性修正、当前冻结模板 | 更新既有当前值列；按来源追加`SOURCE_CORRECTION`或`MANUAL_ADJUSTMENT`匹配决策历史和影响结论 | 不更换模板、不重新实例化、不产生CHG完成事实 |

## 5. 业务规则

### BR-FPROJ004-001 手工项目输入

- 人工填写签约方式、项目类别和项目实施方式；CRM重大项目级别必须为数据库`NULL`，界面显示“不适用”，不新增`NOT_APPLICABLE`字典码。
- 前端不展示可编辑CRM重大级别，服务端同时拒绝任何非空CRM重大级别，不能只依赖隐藏字段。
- 四属性判定成功后调用现有TemplateMatcher。候选结果为`NO_MATCH/UNIQUE/MULTIPLE_MATCHES`，决策方式为`AUTO_UNIQUE/EXPLICIT_SELECTION`：无匹配整体拒绝；唯一候选可自动决定；多候选仅允许授权用户从本次合法候选集中显式选择一个模板修订。失败不产生正式Project或半成品模板实例。

### BR-FPROJ004-002 CRM来源输入边界

- INT负责协议转换、传输、重试和对账；PROJ只消费已校验的属性输入和来源/版本元数据。
- CRM签约方式、实施方式和重大项目级别是CRM Owner事实；平台项目类别是PROJ事实。
- 重大项目级别命中批准规则时项目类别可初始化为`ENGINEERING`；未命中时项目类别必须来自明确的平台决定，不从其他维度推导。
- 自动建项和来源Project绑定属于INT-01/PM-01后续编排；本Feature不能以预置Project证明该闭环完成。

### BR-FPROJ004-003 模板匹配决策历史

每条`proj_project_template_match_history`至少冻结：

- 四属性完整快照及变更前后值；
- 属性Owner、来源系统、来源键/事件、来源版本、发生时间和原始值摘要；
- 映射/判定版本、模板匹配规则或算法版本；
- 匹配结果`UNIQUE/NO_MATCH/MULTIPLE_MATCHES`、候选摘要、命中模板及模板修订；
- 记录用途`CREATE_DECISION/IMPACT_EVALUATION`；仅`INITIAL_CREATE`使用决策方式`AUTO_UNIQUE/EXPLICIT_SELECTION`，创建后影响评估的`decisionMode`必须为空；
- 触发类型`INITIAL_CREATE/SOURCE_CORRECTION/MANUAL_ADJUSTMENT`（首次创建/来源修正/人工调整）；
- 影响结论`NO_IMPACT/CANDIDATE_CHANGED/NO_MATCH/MULTIPLE_MATCHES`；
- 操作者、原因、发生/记录时间、幂等键、请求摘要和稳定`operationId`；
- 可选`traceId/auditLogId`，用于与技术Trace或既有系统操作日志关联；关联缺失不得导致本业务事实缺失。

历史严格append-only，不更新旧行。首次创建时，正式Project、当前四属性、决策历史、模板冻结和全部实例要素同事务提交或回滚。

字段值按`triggerType × matchResult`约束：

- `INITIAL_CREATE`：`recordPurpose=CREATE_DECISION`、前值为空、`impactResult=NOT_APPLICABLE`；`UNIQUE`须保存命中模板及修订，`MULTIPLE_MATCHES`仅在`decisionMode=EXPLICIT_SELECTION`并选中当前候选时持久化，`NO_MATCH`不产生Project级历史。
- `SOURCE_CORRECTION/MANUAL_ADJUSTMENT`：`recordPurpose=IMPACT_EVALUATION`且`decisionMode`为空，前值必填；结果为`UNIQUE`时新候选模板及修订必填，结果为`NO_MATCH/MULTIPLE_MATCHES`时命中模板字段必须为空、候选摘要必填。
- `SOURCE_CORRECTION`还要求来源Owner、键、事件、版本、发生时间、原值摘要和映射版本；`MANUAL_ADJUSTMENT`要求操作者与原因。所有记录物理列集合固定，但允许为空与必须非空由上述矩阵决定。
- `operatorId/changeReason`所有持久化行均有效：手工首次创建取认证用户稳定ID和`POST /projects`必填非空白`createReason`；未来自动创建取已注册受信任服务主体稳定ID和必填非空白创建原因；来源修正把`serviceIdentity`解析为已注册服务主体稳定ID，并使用命令必填非空白`correctionReason`；人工调整取认证用户稳定ID和classify命令必填非空白`adjustmentReason`。三类原因均先去除首尾空白，`null`、空字符串或纯空白在事务开始前拒绝。服务身份无法稳定解析时拒绝命令，不以临时线程名、IP或显示名代替。

### BR-FPROJ004-003A 既有属性审核能力核验

Feature Ready前对当前NPDMS物理实现的核验结论为`FAIL / 不可复用为PM-07权威追溯`：

- `pms-module-project/src/main/java`未发现属性变更的`@LogRecord`、`@OperateLog`或等价结构化接入；
- `system_operate_log`及`OperateLogPageReqVO/OperateLogMapper`只能按`bizId/type/subType/action/time`等通用字段查询，不能按`projectId + 属性维度`稳定检索结构化前后值、原因和来源；
- `LogRecordServiceImpl`调用`OperateLogCommonApi.createOperateLogAsync`，后者使用`@Async`，属于异步尽力写入，不能保证属性变更事务成功后日志必然存在；
- 未发现`system_operate_log`满足ADR-0006 `PERMANENT_NON_DELETABLE`的已验证物理保留策略。

因此不新增`proj_project_business_attribute_history`，也不把通用操作日志作为属性变更成功的前置依赖。`proj_project_template_match_history`自身保存完整变更前后快照、操作者、原因、来源和时间，并以稳定`operationId`作为业务关联主键；可获得时再写入`traceId/auditLogId`。若将来需要把所有项目属性变更统一纳入公共审计，须作为公共审计能力独立立项，补齐结构化维度查询、事务完整性和永久保留后再替换可选关联策略。

### BR-FPROJ004-004 创建后属性变化

- 同步服务只能修正CRM Owner的签约方式、实施方式和重大项目级别；业务用户不能填写或覆盖CRM重大级别。
- 工程管理部可受控调整平台项目类别；手工项目的签约方式和实施方式可通过受控命令修正。
- 接受变化后更新`proj_project`既有当前列，并基于新快照调用TemplateMatcher做只读评估、追加历史。
- 即使候选模板改变、无匹配或多匹配，也不更换当前冻结模板，不修改已实例化事实；未来CHG-01可从历史派生独立工单。
- INT在完成来源定位后，只能通过PROJ内部`ProjectAttributeSourceCorrectionCommand`按稳定`projectId`提交来源修正；命令必须携带来源键/事件/版本/发生时间/原值摘要、非空`correctionReason`、幂等键和服务身份。服务身份须映射为已注册稳定服务主体ID并写入`operatorId`。该命令不负责来源定位、传输重试或对账。

### BR-FPROJ004-005 项目类别值域纠偏

- `pms_project_category`只允许PM-07业务类别`GENERAL/ENGINEERING`。
- `MAIN/SUB`属于项目树结构语义，不再作为项目类别候选；结构由`parent_id/root_id/business_level_code`表达。
- 使用前向迁移修正字典和模板候选，不修改已执行迁移。
- 先清查现有`project_category in ('MAIN','SUB')`及手工来源但重大项目级别非空的数据；不得自动映射或静默清空，原值作为证据进入受控人工处置清单。

## 6. API契约

| 接口 | 操作 | 契约 |
|---|---|---|
| 既有`POST /projects` | `POST` | 必填去除首尾空白后仍非空的`createReason`；返回候选结果、决策方式和INITIAL_CREATE operationId；无匹配拒绝，多匹配须显式选择本次合法候选；Project、历史、模板冻结和实例化同事务 |
| 既有`GET /projects/{id}` | `GET` | 继续返回当前四属性，不增加重复当前属性资源 |
| `/projects/{id}/template-match-history` | `GET` | 按触发类型、匹配结果、影响结论和时间分页；排序白名单；ProjectTreeScope |
| `/projects/{id}/actions/classify` | `POST` | 仅修正调用方有权维护的维度；要求必填非空白`adjustmentReason`、`Idempotency-Key`、`If-Match`；原子写当前值和一条匹配决策历史，不重新实例化 |
| 内部`ProjectAttributeSourceCorrectionCommand` | 应用命令 | INT已定位`projectId`后修正CRM Owner维度；要求来源版本、必填非空白`correctionReason`、幂等和服务身份；原子写当前值和SOURCE_CORRECTION历史；不承担来源定位、重试、对账或直接表写入 |

PROJ内部提供统一属性判定/匹配服务，供手工创建和未来CRM自动创建编排复用。未来INT入口必须先经已批准的项目定位/创建编排，不能直接写PROJ表；本Feature不定义一个仅凭来源键更新已有Project的独立同步入口。

## 7. 数据与物理边界

机器契约：`specs/features/F-PROJ-004-physical-contract.json`。

- `proj_project`复用既有四列；不增加同义字段或分类/选模状态轴。
- 新增`proj_project_template_match_history`作为唯一新增业务事实表，严格append-only。
- 本表是PM-07决策与影响追溯的权威记录，不是覆盖所有项目字段的通用属性历史表；稳定`operation_id`必填且唯一，`trace_id/audit_log_id`仅作可选关联。
- 四属性快照使用版本化JSON结构；物理字段存在性与逐行非空性分离，完整条件矩阵以机器契约为准。`decision_mode`只表达首次创建选模决策，创建后通过`record_purpose=IMPACT_EVALUATION`表达只读影响评估。
- 映射规则若需持久化由后续INT/自动创建契约确定；本Feature只在历史中冻结实际消费的映射/判定版本，不提前建设规则管理子系统。
- 无匹配，或多匹配但未显式选择本次合法候选的创建请求，在Project产生前失败；可记录通用请求/审计证据，但不创建项目级分类案例。多匹配且显式选择合法候选时按正常原子创建处理。
- 迁移只新增前向版本；回退代码不得删除或改写决策历史。

## 8. 权限、幂等与并发

- 工程管理部需功能权限及目标ProjectTreeScope才可修正允许的属性；项目经理/服务经理只读。
- 同步服务身份只能写CRM Owner维度；业务用户写CRM重大级别返回FORBIDDEN。
- 写命令原子认领幂等键并绑定请求摘要；同键同请求重放，同键不同请求冲突，进行中重复返回409。
- `If-Match`校验Project版本；事务中重新读取当前属性、冻结模板和匹配器版本。并发冲突整体回滚，不追加与当前值不一致的历史。

## 9. UI

- 复用现有项目创建、列表和详情页面，不建设独立分类工作台。
- 手工创建移除CRM重大级别编辑入口；项目类别只展示`GENERAL/ENGINEERING`。
- 项目详情增加“模板匹配历史”分页区域；属性修正使用Yudao既有表单/抽屉/权限组件。
- 响应式支持320/768/1024/1440；使用Element Plus结构、组件和主题变量，不堆叠内联样式。

## 10. 验收标准

- `AC-FPROJ004-001`：手工项目非空重大级别在服务端被拒绝；三项人工属性合法时才进入既有模板匹配。
- `AC-FPROJ004-002`：项目类别候选不含MAIN/SUB；存量错误值不自动映射或静默清空。
- `AC-FPROJ004-003`：唯一候选自动决定或从合法候选显式选择时，Project、决策历史、模板冻结和全部实例要素原子提交；任一步失败无半成品。
- `AC-FPROJ004-004`：无匹配时不创建Project；多匹配未显式选择合法候选时不创建，合法显式选择时按EXPLICIT_SELECTION原子创建；失败仅保留通用失败审计。
- `AC-FPROJ004-005`：INITIAL_CREATE历史完整冻结四属性、来源/映射/算法版本、候选结果、AUTO_UNIQUE/EXPLICIT_SELECTION决策、命中模板修订和幂等证据。
- `AC-FPROJ004-006`：创建后合法属性修正更新既有当前列，并按来源追加SOURCE_CORRECTION或MANUAL_ADJUSTMENT历史；旧历史业务字段不变。
- `AC-FPROJ004-007`：创建后重新评估即使候选变化、无匹配或多匹配，也不修改冻结模板和实例事实，只记录影响结论。
- `AC-FPROJ004-008`：越权、跨租户、业务用户写CRM重大级别、版本或幂等冲突不产生有效副作用。
- `AC-FPROJ004-009`：真实浏览器完成创建、历史查看、合法修正、负向拒绝和刷新保持；四类视口无页面级溢出或未解释错误。
- `AC-FPROJ004-010`：不声明CRM自动建项、INT重试/对账、CHG执行、PM-08或E2E完成。
- `AC-FPROJ004-011`：每条决策历史可按稳定operationId定位；既有异步操作日志缺失时，事务内四属性快照、前后值、操作者、原因、来源和时间仍完整存在且永久不可改写。
- `AC-FPROJ004-012`：通用项目PATCH不能修改四项模板输入；INT仅能在已定位projectId后通过受信任来源修正命令写CRM Owner维度并追加SOURCE_CORRECTION历史。

## 11. 测试与证据

本Feature按已确认的非TDD实施方式推进，不以“先写失败测试”作为任务前置；每个Task完成后仍按风险补齐自动化回归。最终证据覆盖自动化、真实MySQL原子性与append-only、权限负向、幂等并发、真实浏览器响应式闭环、值域清查和独立代码评审。

## 12. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| Scope与两个Capability | PASS |
| 既有四属性和TemplateMatcher复用 | PASS |
| 单一历史表物理边界 | PASS |
| 既有属性审核能力核验 | FAIL（不阻断本Feature；已明确独立公共审计后续边界） |
| SDS与追溯一致性 | PASS（独立裁决`NPDMS-FPROJ004-FEATURE-READY-20260825-06`） |

结论：`BASELINE / READY`。独立Feature Ready裁决已GO；规格提交并由NPDMS锁定新快照后方可重新生成Technical Plan。在快照锁定前不得修改业务代码、Flyway和UI。

## 13. 完成边界

F-PROJ-004只完成PM-07的PROJ子切片：模板前属性判定、匹配决策历史和创建后影响识别。INT来源定位/自动建项/传输重试/对账由后续INT Feature承接；CHG分派/处理/关闭由后续CHG-01 Feature承接；PM-08和E2E均保持未完成，不能因本Feature完成而关闭PM-07全部验收。
