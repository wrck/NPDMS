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
- Task 6C最近Gate候选已形成：Feature/数据库SDS与physical contract锁定`acc_correlation_id`在首次发布事务写入并供全部重试原样继承；V138不设默认值或补造回填，已有非`NOT_PUBLISHED`行时在ALTER前失败关闭。迁移静态测试13项及隔离MySQL 8.4未发布成功/已发布失败双向验证通过；RetryJob/RetryService/Mapper/投递收口等待本Gate独立复审，不提前实现或启用。
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
