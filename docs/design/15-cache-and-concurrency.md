# SDS Phase 2：缓存与并发设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：全部100项V1/V2正式需求中的查询性能和并发一致性；重点覆盖PM-02/04/09/11、PROJ-12、EXE、CUT、INS、EQP、AST-01～02、COM-01、PLT、INT-12、NFR-01～02
> Owner：SDS Phase 2 技术架构；业务真值仍归各 Context
> 前置设计：`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`11-event-design.md`

## 1. 原则

1. 数据库聚合当前行、不可变历史和 Owner Context 是真值；缓存、搜索索引、树路径和指标视图均可重建。
2. 任何状态命令、权限判定和凭证授权不能仅依赖可能过期的缓存。
3. 写入采用聚合版本或等价并发令牌，冲突显式返回，不使用最后写入覆盖。
4. 跨聚合不追求分布式强事务；使用 Outbox、版本化快照、幂等和对账实现可恢复最终一致。
5. 缓存失效失败不回滚已提交业务事务，但必须避免返回已知越权数据并可按版本旁路缓存。

## 2. 缓存分类

| 缓存 | 可缓存内容 | Key 必含 | 一致性 |
|---|---|---|---|
| 字典/展示配置 | 类型名称、颜色、排序 | tenant/global、dictType、configVersion | 版本化失效；不控制状态迁移 |
| 项目/任务树查询 | 完整投影版本的子树页、祖先链 | tenant、root/scope、treeVersion、permissionScopeHash、cursor | 只读投影；树变更后新版本切换 |
| 项目工作台投影 | 六页签摘要、Stage→ProjectTask导航、WorkBinding显示摘要 | tenant、project、templateRevision、treeVersion、permissionScopeHash、projectionVersion | 不缓存目标业务敏感正文；操作前回源权限与目标状态 |
| 动态表单发布修订 | 已发布且不可变的FormCreate配置、规则和引擎版本 | tenant、templateId、revisionId、revisionNo | 发布修订可按身份缓存；新建实例必须回源模板可用性、当前发布指针与templateVersion，实例可变值写入不以缓存为真值 |
| 设备祖先统计 | device/project ancestor projection | tenant、treeVersion、assignmentVersion、scope | 可重建；返回水位 |
| 主数据查询 | 客户、设备、合同必要副本 | tenant、objectId/sourceVersion、fieldScopeHash | 本地数据库是真值副本，缓存短期加速 |
| 文件下载授权 | 短时访问令牌 | tenant、subject、artifact/version、operation、scopeHash | 到期/撤销；敏感操作可单次 |
| 权限辅助 | 功能权限、项目成员快照 | tenant、subject、policyVersion、projectTreeVersion | 拒绝优先；写操作回源关键事实 |
| 合同管理员公司范围 | 不缓存正向授权；每次请求回源SYSTEM当前有效scope | tenant、subject、scopeId/version（仅审计，不作缓存授权） | 空/异常失败关闭；撤权、到期或版本变化后的下一请求立即按新事实判定 |
| 看板 | MetricSnapshot/PortfolioView | tenant、metricVersion、watermark、scopeHash | 不回写业务；展示截止时间 |

凭证明文、临时密码、私钥、Token、完整授权码、未脱敏外部报文和待提交业务命令不得进入通用缓存。

## 3. Cache-Aside 与失效

查询先读版本化缓存，未命中回源并设置；业务事务提交 Outbox 后，消费者清理旧 key 或推进 namespace 版本。使用版本化 key 时无需可靠枚举删除全部旧 key，旧值自然到期。

【建议】缓存 key 采用 `npdms:<env>:<tenant>:<context>:<resource>:<scopeHash>:<version>:<id>`，不包含姓名、客户名、密码或其他敏感明文。

- 删除/失效消息重复消费必须幂等。
- 权限收缩、凭证撤销、文件失效等安全事件优先更新真值并推进版本；写/敏感读回源验证。
- 缓存不可用时降级回数据库和已批准限流策略，不能绕过授权或返回跨租户共享缓存。
- TTL 是技术容量配置，不是业务有效期；业务有效期必须存数据库并实时校验。

## 4. 乐观并发控制

所有可变聚合命令携带 `If-Match`/expectedVersion；更新 SQL 包含版本条件并递增。受影响行数为 0 时读取当前版本和状态，返回 `CONCURRENT_MODIFICATION`，不自动覆盖。

| 对象 | 并发令牌 | 冲突处理 |
|---|---|---|
| Project/ProjectTask | aggregateVersion + treeVersion（移动时） | 重新加载树和当前版本后由用户重试 |
| ProjectTask Completion | aggregateVersion + bindingVersion + ruleVersion + businessFactVersion | 任一版本变化即重新读取事实并评估；不得按旧快照完成 |
| ProjectTemplate/Solution/Rule | draft aggregateVersion；发布 revision 不可变 | 创建新 revision，不修改已发布版本 |
| DynamicFormTemplate/Revision | templateVersion + draftVersion；发布revision不可变 | 新建模板初始DISABLED；发布、创建下一草稿和启停锁定模板及当前指针，冲突重新读取，不替换请求中的修订 |
| DynamicFormInstance | instanceVersion + frozen revision identity | 普通字段部分PATCH只按CAS更新请求字段；后到请求不覆盖，模板发布/停用不改既有实例 |
| Arrival/Installation/Quality | aggregateVersion | 提交/确认/整改复核按状态守卫重试 |
| DeviceAssignment | device assignmentVersion + project treeVersion | 一次只有一个当前归属；冲突人工核对 |
| DeliveryScope | orderLineVersion + allocationVersion | 重新计算可分配量，不允许超分配 |
| CollectionTask | aggregateVersion + callback sequence/version | 重复幂等；乱序暂存，不回退状态 |
| FileReference | referenceVersion | 同时替换草稿时后到者冲突 |

## 5. 项目和任务任意层级并发

### 5.1 移动项目

1. 读取并锁定移动节点、目标父节点与必要路径行。
2. 校验同租户、目标存在、权限、expectedVersion 和无环。
3. 更新邻接真值并生成唯一 `changeBatchId/treeVersion`。
4. 更新或异步构建新路径投影；只有完整版本可以成为 activeTreeVersion。
5. 发布 `ProjectTreeChanged`；权限、设备祖先和指标投影按版本重建。

两个交叉/重叠子树移动按稳定项目 ID 顺序加锁，避免死锁；检测死锁后整个业务命令按原幂等键受控重试。路径投影不得出现一部分新版本、一部分旧版本对外可见。

### 5.2 移动任务

同项目内按 taskId 稳定加锁并校验无环；跨项目移动只有 PRD/Feature 明确定义时才允许，不能因数据库字段可写而默认支持。任务层级变化不自动修改 TaskDependency。

### 5.3 查询效率

- 子树和祖先读取路径投影，不在应用层逐节点 N+1 查询。
- 树列表返回 `treeVersion` 和游标；下一页使用同一版本，版本已回收则要求重新查询。
- 大批量统计通过聚合投影/快照，不在单请求实时遍历所有后代。

### 5.4 PM-05 转销与 PM-06 多期关系

- PM-05 发起时以 `sourceProjectId`、`formalSalesBusinessId` 和聚合版本作为互斥边界；同一来源项目只能创建一个生效目标。对象项按稳定的 Context/类型/对象ID/来源版本排序处理，成功项幂等保留，失败项可在原批次重试。
- 设备处置不在 Project Delivery 内直接改 AST 表；逐台携带 `assignmentVersion` 调用 AST，冲突项进入部分失败，不能把源项目归档或把失败设备计入目标项目。
- PM-06 成员调整校验 `groupVersion/memberVersion`，在同一事务内检查关系类型下项目唯一群组和群组内唯一期次；前后期图无环校验失败则整体拒绝本次成员变更。
- 多期视图缓存 key 包含 tenant、groupId、groupVersion、permissionScopeHash 和各来源 revision watermark；权限变化或任一来源版本变化时新版本旁路旧缓存。

### 5.5 Stage—ProjectTask工作台

- 工作台导航投影只从ProjectStage和ProjectTask真值生成，`treeVersion/templateRevision`变化后切换新版本，不维护独立导航缓存真值。
- 绑定业务组件的数据由Owner API返回；缓存只保存无敏感信息的绑定摘要。编辑、创建、审批、文件和设备采集操作必须回源授权。
- ProjectTask完成以`taskVersion + executionContractId/contractVersion + factVersion + Idempotency-Key`为并发边界。服务端锁定任务并重新读取当前执行契约和Owner事实；任一版本变化即拒绝，不用旧判定结果推进。成功判定、`TaskCompletionEvaluation`和任务状态迁移同事务提交。
- 执行契约换版使用`projectTaskId + current contractVersion`乐观锁：新版本插入与旧版本关闭有效区间在同一事务，数据库当前标记唯一键防止两个当前版本。模板发布后任务定义不可原位更新。
- CUT-03重新匹配使用`checklistVersion + inputSnapshotHash`并发令牌；条件变化和采集回调并发时，回调先落DAC结果，再由CUT按当前清单版本、stableItemKey和itemVersion决定追加/选择结果或进入待核对，不覆盖新草稿，也不复制DAC状态。
- 清单项结果以`checklistItemId + resultVersion`追加；选择切换在锁定当前结果后，同一事务写旧行`selectionEndedAt`并插入带新`selectionStartedAt`的结果版本，生成`currentMarker`唯一约束防止两个未结束选择。结果载荷不可覆盖，人工降级不会删除自动失败结果；已提交清单的重新匹配必须创建新清单版本。

### 5.6 PM-07属性修正与匹配历史并发

- 首次匹配决策历史与Project、模板冻结及实例化使用F-PROJ-001同一事务边界，失败不留下Project或历史孤儿。
- 创建后属性修正同时校验`Idempotency-Key`请求摘要与`If-Match` Project版本；事务中重新读取当前属性、冻结模板和匹配器版本，原子更新当前值并追加一条历史。
- `ProjectAttributeSourceCorrectionCommand`还校验受信任服务身份、来源键和单调来源版本；旧版/重复来源事件不更新当前值，不重复追加历史，同一来源版本不同摘要进入冲突处置。
- 同键同请求重放，同键不同请求拒绝，进行中重复返回409；并发冲突整体回滚，不产生与当前值不一致的历史。
- 历史查询按`tenant + projectId + recordedAt/id`游标或受控分页，不缓存敏感全量历史；缓存不可用不能跳过ProjectTreeScope。

## 6. 设备唯一归属并发

设备归属命令以 `tenantId+deviceId` 唯一当前行和 assignmentVersion 为互斥边界：

- 两个项目同时认领同一设备，只允许一个更新成功；另一个收到归属冲突和当前项目。
- 变更当前行、关闭历史区间、创建新历史和写 Outbox 在同一事务。
- 项目树移动不改变实际 assignedProjectId，只触发祖先统计投影重建。
- 业务写操作依赖设备归属时读取当前 assignmentVersion；长流程把版本冻结到快照，执行前按 PRD 门禁决定是否重新验证。
- 投影延迟时详情展示实际归属，汇总展示水位；不创建临时多重归属修补统计。

## 7. 数量与范围并发

COM-01 的可分配量按有效订单量减去其他有效分配量。分配/释放在订单行事务边界内锁定或使用 allocationVersion：

- 校验单位、精度、退货/取消和 ERP 减量后的有效数量；
- 新分配不得使总有效分配超过有效订单量；
- ERP 减量造成既有超分配时不自动删分配，标记冲突并阻止新增，进入受控调整；
- 同一幂等键重放返回原分配；不同请求摘要冲突。
- 所有范围分配、调整和释放先通过`ProjectAcceptanceStageFactApi`锁定PROJ项目当前行，再按稳定订单行ID锁COM订单行；随后通过`ProjectOfficeFactApi`重验同一项目版本及办事处事实，并把部门ID/编码/名称/版本一次性冻结到新范围版本。项目或部门随后变化不更新旧快照。
- PROJ进入验收阶段时已持有项目当前行锁，ACC经`DeliveryScopeAcceptanceLockApi`按稳定范围ID锁定该项目全部当前有效范围，再追加绑定；任一失败使阶段快照、绑定和阶段进入整体回滚。
- 项目已处验收阶段时，COM在同一项目锁下写入新范围版本并同步调用`AcceptanceScopeBindingApi.bindEffectiveScope`；绑定失败使范围版本生效整体回滚。减量在同一锁序下调用`AcceptanceScopeGuardApi`，已有绑定、未知、超时或版本变化均失败关闭。
- 统一锁顺序为PROJ项目当前行→COM订单行（适用时）→COM范围当前行（稳定ID）→ACC绑定。初验/终验报告行不进入该锁链，报告状态不得用于判断绑定。
- F-ACC-001首次发布、替换、撤销均按ACC活动根→旧当前版本（适用时）→目标草稿（适用时）→附件集合锁定。DRAFT的生成`current_marker=NULL`，仅未关闭EFFECTIVE生成1；替换原子关闭旧版再生效草稿，撤销原子关闭当前并清空指针，失败整体回滚。终验发布再按稳定活动ID锁定初验当前报告，版本变化返回冲突。
- 初验/终验任务完成按PROJ项目任务/执行契约→ACC活动根→当前报告版本锁定，ACC Provider以`MANDATORY`加入同一MySQL事务；任一身份、活动版本、报告版本或四项完备校验失败时，ACC活动、TaskCompletionEvaluation和PROJ任务状态均不变化。不得反向从ACC先锁任务或直接写PROJ表。
- 报告命令通过`PlatformCommandExecutionApi`写Outbox；`AcceptanceReportOutboxDeliveryJob`经`PlatformOutboxDeliveryApi`只领取`AcceptanceReportVersionChanged`。消费锁对应`acc_project_deliverable`根和当前来源关系；按变更类型及来源版本幂等追加/切换`source_version`与完整附件集合。投影事务成功后才`markDelivered`，异常则`scheduleRetry`；不得领取`ClosureGateRecheckRequested`。替换/撤销保留旧关系，撤销不恢复旧版。报告事务不等待归档成功，归档失败记录补偿水位。
- 报告文件策略由ACC Provider以`reportVersionId`解析项目范围，`scopeVersion`唯一取PROJ当前`treeVersion`。发布/完成按ACC活动根→报告版本→PLT附件ACTIVE集合重验；归档补偿锁定附件集合后，在独立`ACCEPTANCE_REPORT_ARCHIVE`集合按`FileArtifact→FileVersion→FileReference`稳定顺序整组创建ARCHIVED引用和记录，附件引用不变。PLT成功后ACC才把投影置`ARCHIVED`。
- 新项目创建中，PROJ先生成任务/非ACC契约/里程碑，调用既有ACC initializer形成应交根，再通过`MANDATORY` activity initializer创建ACC活动，最后追加引用返回`acceptanceId`的ACC执行契约；全部共用同一MySQL事务，PROJ不直接写ACC表。存量切换按项目和初验/终验任务对加锁，只有两项均非终态且当前契约均为V63 `TASK_NATIVE`才原子换绑；两项均终态整项目保持不变，终态/非终态混合整批失败。
- 合同列表、详情和项目—合同关系维护不使用正向公司范围缓存。关系写在任何COM业务锁和写入前调用`OrganizationScopeApi.getActiveScopes`，按`companyCode`精确校验并冻结本次授权快照；调用后若进入COM事务，只把scope ID/version写审计，不反向锁SYSTEM或把授权结果持久化为第二真值。并发撤权以写前最后一次Owner读取为本次判定，后续请求必须读取新当前事实。

到货数量、工时和动作数值使用相同原则：保留原值和调整记录，不通过并发最后写覆盖。

## 8. 状态迁移并发

- 状态命令同时校验 expectedVersion、currentStatus、业务守卫和权限。
- 同一对象两个合法命令并发时，只有先提交者成功；后者根据新状态返回冲突，不自动串行成意外迁移。
- 工作流回调以 processInstanceId + node/task ID 幂等；过期节点回调只记审计，不回退业务状态。
- 字典缓存更新不能改变现存实例状态；状态机版本随实例/快照保存。

## 9. 外部同步与回调并发

| 场景 | 控制 |
|---|---|
| 同一来源对象乱序事件 | sourceVersion；旧版本忽略，同版本异内容隔离 |
| 同一批次重复投递 | source eventId/batch item 唯一键 |
| 外部超时后本地重试 | 先查询外部结果；原幂等键不产生第二业务单 |
| DAC 回调乱序 | callback sequence/resultVersion + task aggregateVersion |
| DAC 回调与撤销并发 | 保存实际停止点；撤销阻止后续执行但不改写已发生结果 |
| 通知回执晚到 | 更新通知尝试，不改变业务对象终态 |

## 10. 锁策略

| 锁 | 使用场景 | 禁止用途 |
|---|---|---|
| 数据库行锁 | 设备当前归属、订单行数量分配、短小关键事务 | 长时间外部调用、文件上传、人工审批 |
| 乐观锁 | 普通聚合编辑和状态命令 | 静默自动合并业务冲突 |
| 分布式互斥锁 | 【建议】极少数跨进程调度领取、投影切换 | 作为数据唯一性的唯一保障 |
| 租约/领取令牌 | Outbox、同步批次、后台任务 | 超时后无幂等地重复副作用 |

所有锁都必须有稳定获取顺序、超时、持有者标识和可观测指标；外部网络调用在数据库事务提交后执行。

## 11. 缓存雪崩、穿透和热点

【建议】使用随机抖动 TTL、请求合并、空结果短缓存、租户/主体限流和热点 key 分片；具体数值在 Phase 3 性能测试后确定。不存在的业务对象仍需数据范围友好的统一响应，避免通过时延枚举资源。

项目首页、组合看板和设备统计优先读取版本化投影；投影构建失败展示最近完整快照和截止时间，不回退到无界全表实时聚合拖垮交易库。

## 12. 测试与可观测性

最低并发测试：

- 项目/任务交叉移动、成环拒绝、投影版本原子切换；
- PM-05 同源并发转销、对象部分失败重试、设备归属冲突与归档门禁；PM-06 并发加期、重复期次、冲突群组、循环关系和权限裁剪；
- 同一设备并发分配、项目树移动与归属投影重建；
- 订单行并发分配、ERP减量后超分配；
- 同一状态双命令、工作流重复/过期回调；
- DAC 回调重复、乱序、撤销并发；
- 动态表单唯一草稿、并发发布/启停、选择后指针变化、实例CAS单胜和已发布修订不可变；
- 跨Context动态表单命令先冻结封闭业务动作，再按完整Owner稳定键全量锁定并以同一动作重验Provider事实，之后锁PLT实例/修订，最后按全局稳定顺序锁Artifact→Version→Reference；首个PLT锁后禁止回调Owner Provider。写入和持锁方法传播`MANDATORY`；完整重验比较双版本、规范化值和所有ACTIVE引用集合，显式空集合也必须相等；
- 缓存失效丢失、Redis不可用、热点穿透；
- 权限收缩后缓存不得继续授权敏感访问。
- 合同公司scope撤权/到期、同公司多scope去重、Owner不可用及关系写前版本重验；任何场景不得因缓存继续返回合同或写关系。
- 满意度模板解析零/多匹配、同一业务时点重复触发、同一整改Fact重放/异载荷冲突、collectionKey内taskRevision并发递增、同一问卷同requestId重放/异载荷冲突、访问授权并发消费、Result当前唯一/按expectedVersion失效、RECORDED/INVALIDATED双向乱序保护、精确`T-SAT-SURVEY→D-SAT-REPORT`根锁定及归档补偿；不得用缓存授权令牌、项目范围、应交根身份或当前达标结果。

F-ACC-002固定锁序：触发为PROJ ProjectTask/WorkBinding→ACC Task→Questionnaire；客户提交为ACC AccessGrant→Task→Questionnaire→PLT签字/附件引用→Response→Result→Outbox；失效为PROJ ProjectScope重验→ACC Task链→当前Result→Outbox；归档为ACC交付件根/来源版本→PLT结果文件集合/归档集合→ACC归档投影。失效以Result expectedVersion和current marker单胜；RECORDED消费置CURRENT前重验Owner版本与当前状态，INVALIDATED消费只撤销仍指向该版本的根，双向乱序均保持来源指针单调。首个PLT文件锁取得后不得回调PROJ改变业务时点，归档网络/对象存储步骤不得持有长事务锁。

Result判定在锁定Task/Questionnaire/Response并取得ProjectScope事实后，先以稳定operation调用PLT生成文档，再写Result/ResultFile/Outbox；`createGeneratedBusinessFile`以MANDATORY加入同一MySQL事务。对象存储写入不延长反向Owner锁序：PLT不得在取得文件锁后回调PROJ。并发同Result只允许一个RESULT_DOCUMENT引用；同operation同摘要复用FileUploadSession/回执，异摘要冲突，外层回滚由会话补偿未引用对象。

监控 cache hit/miss/latency、DB fallback、lock wait/deadlock、optimistic conflict、tree projection lag、assignment projection lag、outbox lag 和 callback disorder。

## 13. 门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| 缓存不作为业务真值 | PASS | 第 1～3 节 |
| 项目/任务任意层级并发安全 | PASS | 第 5 节 |
| 设备同一时点唯一归属 | PASS | 第 6 节 |
| 数量、状态、回调冲突明确 | PASS | 第 7～9 节 |
| 锁与外部调用边界明确 | PASS | 第 10 节 |
| 容量和 TTL 数值 | DEFERRED_TO_PHASE_3 | Phase 3 性能验证后登记，不作为业务规则臆造；不构成 Phase 2 未决项 |

本分册满足 Phase 2 并发契约；实现时不得通过关闭乐观锁、放宽唯一键、共享跨租户缓存或忽略版本冲突来使测试通过。
