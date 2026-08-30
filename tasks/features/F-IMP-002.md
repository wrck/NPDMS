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
- Task 5B基础候选已完成V140两列前向迁移、列表/详情/allowedActions读模型，以及本人DRAFT PATCH、raise-difference和未确认批次差异追加处置；均未注册生产Bean。`Q-FIMP002-002`随后独立裁决采用`OPTION_B`：successor原样继承前驱`batch_code`，以服务端`batch_root_marker`根唯一键和前驱唯一键形成线性后继链，不生成新业务编码。
- `Q-FIMP002-002`规格与V141前向迁移Gate已在`0750182e`通过独立复审（`PASS / GO`）：初始根marker显式两值约束关闭MySQL NULL/UNKNOWN绕过，后继marker保持NULL；根唯一性和单前驱单后继均由真实MySQL 8.4验证。
- Task 5B后继运行候选已形成：初始根写`batch_root_marker=1`；CONFIRMED差异处置与`CORRECT_INFORMATION`只在平台NEW命令内锁前驱并创建至多一个DRAFT successor，原样复制批次码和冻结历史后只在后继追加修改；不同intent占用同一前驱返回状态冲突。`ExpireArrivalExemptionsCommand`按服务端时钟领取到期豁免，先取得PROJ权威锁，再锁根/明细/差异并重验COM/AST/PLT，在同事务创建`EXEMPTION_INVALIDATION`后继、追加事实影响revision并按项目联合MAX+1分配版本；Owner失败或CAS冲突零业务写。当前最近Gate为该运行候选的独立Code Review／聚焦测试，Task 5B整体尚未PASS，Task 8继续阻断。
- 锁定提交`935324cf`的Task 5B后继运行候选独立Code Review为`NO-GO`：CONFIRMED后继状态矩阵、任意直接successor排除、跨代豁免证据及内部到期PROJ主体语义存在运行阻断。独立定点裁决已锁定：CONFIRMED只允许current REJECTED进入SUPPLEMENT/EXEMPT/CLOSE；证据只允许当前节点或不可变严格祖先来源；到期领取排除任意直接successor；内部命令使用PROJ当前系统资格锁而非历史approvedBy用户授权。当前先完成规格/端口基线，不回写运行候选PASS。
- 锁定提交`f4aa1ad2`的`T-FIMP002-PROJ-01` Provider独立Code Review／真实MySQL锁与并发测试Gate已复审`PASS / GO`：Provider按根项目→目标项目→当前ACTIVE树版本固定顺序持锁，current `PROJECT_MANAGER`与参与者事实版本沿用PROJ既有权威语义；当前进入A/B/C/D的IMP Adapter与运行整改，不回写Task 5B整体PASS。
- Task 5B的A/B/C/D运行整改候选已形成：CONFIRMED发现任一current OPEN即失败，只允许current REJECTED进入SUPPLEMENT/EXEMPT/CLOSE；到期领取排除任意直接successor；复制豁免以当前节点或同tenant/project/batch严格祖先的不可变sourceRecordId重验文件事实；内部到期通过PROJ系统资格端口取得当前项目/参与者/树水位并只写入新successor。消费端聚焦测试24项及隔离MySQL 8.4单前驱并发/事务回滚2项通过，当前最近Gate为本运行整改候选的独立Code Review／聚焦测试复审。
- 锁定提交`1b2b6a75`的A/B/C/D运行整改四项原阻断均已关闭；其唯一剩余的`SuccessFacts.correlationId`与真实平台事务阻断已由`808151ce`完成整改并经独立复审`PASS / GO`：用户命令使用受信规范化关联标识，内部到期复用服务端稳定幂等键，业务摘要排除关联标识；真实`PlatformCommandExecutionApiImpl`验证NEW成功落账及successor/line/difference/projectFactVersion与平台记录失败时原子回滚。该PASS不等于Task 5B整体PASS；最近Gate为Task 5B八操作应用能力与状态记录的整体Code Review／聚焦测试收口。
- 锁定基线`f10454dc`的Task 5B整体收口复审曾因create/submit空关联标识与旧CONFIRMED节点误显处置动作而`NO-GO`；两项已由`c649c424`关闭并经独立复审`PASS / GO`。Task 5B八操作应用能力、线性后继、事实版本、平台事务、分页/详情与allowedActions整体Gate正式通过。
- Task 8的`TASK5B`实现前置已解除，允许进入VO、六类路由和八操作Controller契约实施；COM/AST正式Provider未合入前仍只允许显式测试组装，不注册生产`@Service/@RestController/@Bean`。Task 12在正式Adapter和唯一服务Bean可用的同一依赖接通提交中激活Controller。
- Task 8实现候选已形成：新增八操作严格请求/响应VO、受信HTTP上下文边界及`/api/v1/pms/arrival-acceptances` Controller映射，五项权限、If-Match/幂等Header、判别联合、Long线协议和局部错误响应均按已通过REST机器契约实现。Controller没有`@RestController/@Component`且无生产Bean，仅显式测试组装；聚焦回归74项通过。当前最近Gate为Task 8独立Code Review／聚焦测试，生产激活继续由Task 12阻断。
- 锁定提交`fb69dbcc`的Task 8首轮独立Code Review为`NO-GO`：须以结构化机器错误贯通Service/Port到Controller并用真实MockMvc验证400/403/404/409/422/503；严格请求须在反序列化前同时拒绝额外键与缺失键。`FileFactVersion`请求三轴按正式PLT公开契约和Feature Spec的非负整数语义收敛为`NON_NEGATIVE_INTEGER`，不修改PLT模块。
- Task 8最小整改候选已形成：移除无生产抛出点的HTTP专用异常，使用HTTP无关的结构化机器错误贯通应用服务、PROJ适配和Controller；真实MockMvc已覆盖缺Header、缺键、畸形JSON、403、404、409、422、503及精确恢复数据；反序列化前按各判别联合校验精确键。含Owner适配回归的聚焦套件83项通过，当前仍为`CODE_REVIEW_REQUIRED`。
- 锁定提交`b63b5a0c`的Task 8整改复审曾因聚合版本类别漏登记及submit/confirm/PATCH裸`IllegalStateException`而`NO-GO`；`d71ced40`已补齐封闭类别，并将不可见、陈旧聚合版本、非法状态和证据缺失从实际Service/状态机贯通为404/409/422。独立复审确认真实Service→Controller路径及聚焦回归84项通过，Task 8 Code Review Gate为`PASS / GO`；最近下一Gate为Task 12正式COM/AST Adapter、唯一生产Service/Controller Bean装配及其独立审查。
- 锁定提交`871cfcbb`的Task 9独立Code Review／迁移证据Gate已复审`PASS / GO`：V142以确定性高段ID幂等登记5个正式批次状态、4个正式差异类型、一个新的到货签收可见菜单及5项锁定权限；不写角色授权、不修改旧`/pms/eng-arrival`菜单、不播种业务事实或自动指派资源。既有证据投递/回执重试Job与新增旧数据核对Job共3项均保持`PAUSED`；隔离MySQL 8.4全量迁移至V142及原脚本重复执行通过，数据断言为字典类型2、字典项9、菜单节点6、权限5、暂停Job 3、角色授权0、业务记录0。当前进入Task 10新前端工作台与组件测试；任何Job激活和生产装配仍由后续Gate阻断。
- 锁定提交`062d1e84`的Task 10独立Code Review／组件交互测试Gate已复审`PASS / GO`：新工作台统一epoch毫秒`WireDateTime`、服务端`allowedActions`与五权限投影、窄屏Dialog和PLT稳定文件引用；幂等action、PATCH与证据保存共用写前刷新屏障，命令或PATCH写成功后刷新失败只登记纯刷新闭包，下一次写调用为0，刷新成功后才允许按最新聚合版本重新发起。协调器/API与四组件真实挂载测试21项、类型检查、定向静态检查及前端构建通过；旧`/pms/eng-arrival`未修改，文件不重复下载，Task 10不作为真实浏览器证据。当前进入Task 11后端全量聚焦回归与真实MySQL边界；Task 12生产装配、ACC Job、真实浏览器和Feature Implementation Done继续阻断。
- 锁定提交`085015c1`的Task 11独立Code Review／真实MySQL聚焦测试Gate已复审`PASS / GO`：独立Compose项目、独立端口与空卷在MySQL 8.4完成138个迁移至V142，指定真实MySQL套件40/40通过；更宽到货相关非IT回归执行168项，Failures=0、Errors=0，另有13项按`skipITs=true`预期跳过。五表正向创建/提交/确认、生产平台幂等/审计/Outbox、事实版本、线性后继、Owner失败回滚、跨租户回执拒绝、并发确认、Accepted/Archived乱序重放恢复及旧`pms_eng_arrival`表/DO/Controller路径均已验证；`DeliveryEvidenceAcceptedUpdate.retryCount`的MyBatis运行绑定与V140/V141测试夹具缺口已关闭。范围外既有ConstructionPlan与FileArtifact迁移合同各1项失败不阻断本Task，但不得宣称全模块/全Reactor绿。最近Gate切换为Task 12正式COM/AST生产Adapter、唯一ApplicationService/FactApi/Controller装配与生产依赖回归；三个Job激活、真实浏览器和Feature Implementation Done仍未授权。
- Task 12继续`NO-GO / BLOCKED_BY_DEPENDENCY`：AST Owner Provider锁定提交`69d37400`已独立复审`PASS / GO`；首次`INVALID`与公共异常到IMP封闭错误的映射候选已形成，当前最近Gate为`DeviceScopeFactApiAdapter`消费映射Contract Gate，通过前Adapter保持`BLOCKED_BY_SPEC`。COM与ACC依赖不变。
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

## PROJ物理Owner支撑Task

- `T-FIMP002-PROJ-01`：PROJ公开`ProjectSystemQualificationFactApi.lockCurrentForSystem`，在受信租户上下文按项目锁定当前主行、唯一`PROJECT_MANAGER`事实和当前根树版本，校验`ACTIVE/S4`并返回当前项目/参与者/树版本。
- 该Task不新增Feature状态、不放宽`ProjectParticipantFactApi/ProjectScopeApi`用户语义，不接收`subjectUserId/ACTION_EDIT/approvedBy/system actor`或消费方冻结版本；缺项目、非`ACTIVE/S4`、经理缺失/重复或树事实不可用均失败关闭。
- 合入顺序：公开API契约Gate → PROJ Provider与真实锁测试 → IMP内部到期Adapter/运行整改。公开契约Gate已在`b4f16bdf`通过，Provider Gate已在`f4aa1ad2`通过，IMP Adapter/运行整改及平台事务最小整改已在`808151ce`复审通过；Task 5B整体Code Review已在`c649c424`通过。
