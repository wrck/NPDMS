# F-IMP-002 到货签收与里程碑事实

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO`（锁定提交`4b5a2ac9`）
> Technical Plan Gate：`PASS / GO`（锁定提交`e0184ac4`）
> Implementation Done Gate：`NOT_READY`
> Requirement：`EXE-01@V1=FULL`
> Feature Spec：`specs/features/F-IMP-002-arrival-acceptance.md`
> 复用审计：`specs/features/F-IMP-002-legacy-reuse-audit.md`
> 物理契约：`specs/features/F-IMP-002-physical-contract.json`
> 事实契约：`specs/features/F-IMP-002-arrival-fact-contract.json`
> REST/API契约：`specs/features/F-IMP-002-rest-api-contract.json`（`PASS / GO`；锁定提交`dbf62b8f`）
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`

## 当前最小工作单元

- Task 1A公共事实契约已完成：新增`ArrivalAcceptanceFactApi`、查询/锁定重验DTO、结构化范围水位及设备/数量结果契约，定向契约测试4项通过。
- Task 1B生产依赖适配已完成：PROJ按参与者事实锁定后校验`ACTIVE/S4`、项目/事实版本与项目经理角色，`ACTION_EDIT`仅作独立数据范围校验；PLT只读引用集合适配及5项测试通过。
- Task 1C的COM `getAssignedScope`与AST `DeviceScopeFactApi`生产适配保持`BLOCKED_BY_DEPENDENCY`，不注册fallback或Fake Bean；当前转入不依赖这些Provider的Task 2 Schema串行单元。
- Task 2A Schema已完成：V133仅建立五张IMP Owner表、租户内引用、状态/当前版本/数量约束和ACC重试调度字段；静态契约4项及隔离MySQL 8.4真实DDL执行通过。
- Task 2B应用级CURRENT_FORWARD核对仍保持初始暂停；COM/AST/PLT生产资格未满足前不扫描旧表、不生成目标事实，下一单元转入Task 3持久化映射与稳定锁序。
- Task 3A正向持久化已完成：五表DO、批次详情/分页、明细与差异当前版本、证据及修订查询已落地；锁查询全部位于XML，并为后续应用服务按设备、订单行、批次、证据编排稳定锁序提供独立入口；空项目范围直接返回空结果，Mapper合约3项通过。
- Task 3B正向事实查询已完成：仅从CONFIRMED批次读取ACCEPTED当前明细和未过期、已批准且有证据的明确豁免，项目事实来源按事实版本和Owner ID稳定排序；Mapper合约累计4项通过。
- Task 3剩余迁移游标查询随Task 2B继续暂停，不作为正向闭环前置；当前进入Task 4到货领域规则、批次状态机与项目事实计算。
- Task 4A正向领域闭环已完成：DRAFT提交可进入DIFFERENCE_PENDING/PARTIALLY_ACCEPTED/ACCEPTED，项目经理从候选态确认CONFIRMED；项目事实按多批已确认设备/数量与未过期明确豁免计算ACCEPTED或剩余范围，领域测试4项通过。
- Task 4剩余successor DRAFT、更正/补签和豁免失效重开随差异命令补充；为尽快形成首个正向闭环，当前进入Task 5A草稿创建、提交与确认应用服务，不让上述后置分支阻碍进度。
- Task 5A的PROJ资格边界已收敛：创建读取当前项目经理事实，ACTION_EDIT独立校验操作人；独立裁决批准根表持久化projectVersion、participant factVersion与scope treeVersion。物理契约先行同步，V134仅允许V133空表升级；MySQL 8.4已验证空表三列NOT NULL且无默认值、非空根迁移失败并保持1行/0新增列。
- Task 5A草稿创建核心已实现：只从PROJ/COM/AST端口读取权威事实，原子保存项目资格版本、已分配范围快照和设备归属水位；设备缺失、重复或不属于项目时写前失败。COM/AST仅有消费端口和src/test受控替身，生产适配与Spring装配继续`BLOCKED_BY_DEPENDENCY`。
- Task 5A的PLT重验物理缺口经独立裁决放行：证据revision冻结artifactId、referenceKey、versionNo、scopeVersion及三轴FileFactVersion；V135仅允许空revision表升级。MySQL 8.4已验证三列NOT NULL且无默认值、JSON精确三键非负约束，以及非空迁移失败并保持1行/0新增列。
- Task 5A提交核心已实现：同一事务锁定根、明细、差异和当前证据revision，按冻结版本重验PROJ、COM、AST、PLT事实；完整范围进入ACCEPTED，权威差异表存在OPEN时进入DIFFERENCE_PENDING，并以DRAFT/version CAS零副作用推进。当前仅受控端口测试通过，生产COM/AST Provider、REST/UI与真实浏览器闭环仍未完成。
- Task 5A候选累计已纳入历史CONFIRMED批次的ACCEPTED设备/数量，并以严格`scope_snapshot` codec合并当前候选与历史CONFIRMED批次中当前、完整、未过期的EXEMPTED设备/数量范围；活动豁免旧形状解析失败时在状态写入前失败关闭。
- Task 5A差异范围契约已锁定为DEVICE/ORDER_MODEL_QUANTITY严格JSON判别联合；`project_fact_version`经补充裁决改为仅事实影响revision插入时非空，普通OPEN/预确认处置永久NULL。隔离MySQL 8.4已验证V136空表升级后列可空、无默认值、NULL可写且负值拒绝；预确认EXEMPTED在根确认后仍为NULL，独立事实影响revision可在插入时持有非空版本；非空表升级在ALTER前失败且原1行、值5、NOT NULL及旧检查约束均保持不变。
- Task 5A确认前置已增加场景化项目事实版本查询：在调用方持有PROJ权威项目锁的前提下，按同租户项目联合根与差异两类全部非NULL `project_fact_version`取MAX；NULL与逻辑删除行不进入分配集合，查询本身不访问PROJ表或创建本地锁。
- Task 5A首个确认正向闭环已实现：平台幂等命令内先取得PROJ项目经理事实锁，再重验COM/AST/PLT及跨批累计事实；候选状态一致时按项目级MAX+1写根`project_fact_version`并推进CONFIRMED，同时将同一证据revision置为PUBLISHED_PENDING_ACC，通过平台SuccessFacts同事务写`ImplementationEvidencePublished` Outbox。完成重放不再访问业务行，陈旧If-Match在Owner重验和业务写前失败。
- Task 6A投递实现已完成：只领取`ImplementationEvidencePublished`，严格校验tenant/eventId/payload，同步发布成功后markDelivered，发布或校验异常按1/2/4…60分钟重试。V137将`arrivalEvidenceOutboxDeliveryJob`正式登记为PAUSED；生产激活保持`BLOCKED_BY_ACC_CONSUMER`，ACC消费者与真实Spring传播契约未形成前不注册Quartz同步、不运行证据投递或证据回执重试。
- Task 6B消费实现与独立Code Review Gate已完成（`b943461c`，`PASS / GO`）：新增锁定载荷的`ArtifactAcceptedMessage`/`ArtifactArchivedMessage`及同步Listener，运行时租户与载荷tenant一致后才复用平台幂等Inbox；按固定事件类型和全字段摘要处理回执，匹配当前不可变revision后以行锁与version CAS推进`ACCEPTED_PENDING_ARCHIVE`/`ARCHIVED`。审计快照保存收到身份、冻结身份、结果和明确原因；永久异载荷冲突与暂时处理中使用不同公开异常分类。ACC Producer尚未合入，两个生产Job继续PAUSED，生产联调与激活保持`BLOCKED_BY_ACC_CONSUMER`。
- Task 6C的V138定点Gate已通过（`e34930bc`，`PASS / GO`）：首次发布`correlationId`权威持久化、失败关闭迁移可恢复重跑及PAD SPACE尾随空格约束均已闭合。
- Task 6C运行实现与独立Code Review／聚焦测试Gate已完成（`9561384b`，`PASS / GO`）：首次出向消息同步发布成功后，外层本地事务按当前证据状态登记首个Accepted等待水位并完成平台Outbox；双阶段业务重试以`evidenceId:revision:status:retryCount`排他执行，按1/2/4…60分钟退避，在同一次NEW命令内进入重试态并排队同revision新事件，只递增一次retryCount。MyBatis运行绑定、四态单次NEW事务、冻结身份失败关闭及`correlationId`命令边界已通过独立复审；平台白名单已纳入`ImplementationEvidencePublished`，V139幂等登记`arrivalEvidenceRetryJob`为PAUSED。两个生产Job仍不激活，ACC Producer、真实Spring双向传播、真实浏览器和Feature Implementation Done继续受生产依赖阻断。
- Task 7消费端候选独立Code Review Gate已通过（`dfcc224c`，`PASS / GO`）：全部已确认累计和有效豁免先按完整COM当前范围校验超量/越界，再投影调用方请求子范围；COM/AST锁定重验的显式期望版本不一致重新读取当前事实并返回`STALE`，缺失、未知或不可用仍失败关闭。`inspect/lockAndRevalidate`按稳定顺序计算范围结果、来源批次、项目事实版本与`reopened`；最大事实版本只在唯一合格差异revision为来源时标记重开，普通确认根为非重开，缺失/重复/损坏或后继根语义不可证明时失败关闭。实现类保持可代理但不注册生产Bean；聚焦测试26项及一次性隔离MySQL 8.4测试3项通过。该PASS只确认消费端候选；COM `getAssignedScope`与AST `DeviceScopeFactApi`生产Provider/Adapter未合入前，本Task继续`IN_PROGRESS / BLOCKED_BY_DEPENDENCY`，不得回写生产完成、真实浏览器闭环或Feature Implementation Done。
- Task 8实施前独立定点裁决确认：当前直接进入Controller为`NO-GO`，缺口属于现有Task 5未完成的应用义务，不新建并列Task或第二份Technical Plan。当前仅进入Task 5B正式REST/应用机器契约候选及聚焦复审。
- 锁定提交`337757b3`的Task 5B契约候选聚焦复审为`NO-GO / REVIEW_REQUIRED`，当前按A～E整改精确响应DTO、allowedActions同构守卫、correction/豁免失效/部分补签命令闭环、两列前向迁移合同及可恢复错误data。`Q-FIMP002-001`已单项`GO`采用方案A：V1豁免审批人为写事务中由`ProjectParticipantFactApi`锁定重验的current `PROJECT_MANAGER`，同时要求`resolve-difference + ACTION_EDIT`；批准人/时间服务端写入，不新增角色、流程或表。
- 锁定提交`856f458b`的A～E整改复审仍为`NO-GO / REVIEW_REQUIRED`，B/C/D/Q及E的拆码/恢复data已关闭；剩余A/E仅要求锁定Yudao Long number/string wire分支、无证据详情`evidence=null`、错误category/reasonCode封闭枚举及包含PROJ的通用Owner Provider不可用类别。当前按该最小范围整改，未进入Task 5B实现。
- 锁定提交`dbf62b8f`的A1/A2/E1/E2最小整改复审为`PASS / GO`：Long wire严格边界、无证据详情null、13类错误/原因码封闭集合和覆盖PROJ/COM/AST/PLT的Owner不可用语义均已关闭。REST契约已回写`PASS`，physical `task5BExtensionStatus`已回写`BASELINE_READY`；最近Gate切换为Task 5B实现候选独立Code Review／聚焦测试。
- Task 8当前为`BLOCKED_BY_TASK5B_CONTRACT_AND_IMPLEMENTATION_REVIEW`；即使后续候选通过，COM/AST正式Provider未合入前仍只允许显式测试组装，不注册生产`@Service/@RestController/@Bean`。Task 12在正式Adapter和唯一服务Bean可用的同一依赖接通提交中激活Controller。
- 计划输入限于正式PRD/SDS、Feature Spec、旧实现审计和机器契约；XLSX/附件只可参考，不参与决策或形成阻断。

## Technical Plan候选

- 当前候选：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`。
- 候选覆盖五表前向迁移、ArrivalAcceptance聚合与状态机、FactApi、DeliveryEvidence/ACC双向事件、REST/UI、真实MySQL和真实浏览器验收。
- 受控COM/AST/PLT/ACC替身只允许用于计划GO后的单元/集成测试；生产Provider未形成仍阻断Implementation Done和真实浏览器正向闭环。
- `e0b44970`独立复审为NO-GO；当前整改只补齐PROJ项目经理/S4资格、生产Adapter与持久Inbox/调度路径、应用级CURRENT_FORWARD及Flyway合入时定号，不回写PASS。
- `5805db7f`最小复审已关闭上述PROJ、事件链和迁移项；当前仅整改COM正式`getAssignedScope`生产依赖，禁止以现有可分割余量接口降级替代。
- `e0184ac4`独立最小整改复审GO；授权回写Technical Plan PASS并进入计划执行，生产COM/AST/ACC依赖仍阻断相应Task、Implementation Done和真实浏览器正向闭环。

## 已完成的Ready候选输入

- 审计旧后端、前端、配置、运行数据/迁移、状态、权限和测试，逐项裁定复用边界。
- 定义`pms_eng_arrival -> imp_arrival_*`字段、状态、完整性与不可迁行处置；旧tinyint不得直接产生ACCEPTED。
- 锁定COM DeliveryScope与AST Device事实组成的应到范围、水位和失效语义。
- 锁定ACC-04不可变证据事件、归档待重试和不回滚签收边界。
- 形成ArrivalAcceptanceFactApi、三张到货Owner表和EXE-01最窄DeliveryEvidence两表机器契约。
- 明确批次DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED转换、项目里程碑独立判定，以及IMP出向/ACC入向事件幂等边界。

## 未授权事项

- 只执行Technical Plan已授权且依赖已满足的最小工作单元；公共API、Flyway、菜单/Job种子、错误码和事件契约继续串行合入。
- 生产依赖未形成前不得声明Implementation Done、真实MySQL生产闭环或真实浏览器正向验收。
