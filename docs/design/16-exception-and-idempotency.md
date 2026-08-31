# SDS Phase 2：异常与幂等设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：全部100项V1/V2正式需求中的正常/异常、降级、重试、补偿和留痕；重点覆盖跨系统、文件、状态机、树、设备归属和 Device Access & Collection
> Owner：SDS Phase 2 应用与可靠性架构
> 前置设计：`09-database-design.md`～`15-cache-and-concurrency.md`

## 1. 异常分类

| 分类 | HTTP/处理语义 | 是否重试 | 示例 |
|---|---|---|---|
| `VALIDATION` | 400 | 修正输入后新请求 | 必填缺失、格式错误、临时模式无密码 |
| `AUTHENTICATION` | 401 | 重新认证 | 会话失效、回调签名无效 |
| `AUTHORIZATION` | 403 | 权限改变后再试 | 跨项目设备、凭证五元组不匹配 |
| `NOT_FOUND` | 404 | 通常否 | 授权范围内对象不存在；避免泄露越权对象 |
| `STATE_CONFLICT` | 409 | 重新读取后决定 | 非法状态迁移、已归档版本修改 |
| `VERSION_CONFLICT` | 409 | 使用新版本人工/受控重试 | If-Match 过期、树版本变化 |
| `IDEMPOTENCY_CONFLICT` | 409 | 不可原样重试 | 同幂等键不同请求摘要 |
| `BUSINESS_GATE` | 422 | 满足门禁后重试 | 质量检查不合格、交付件不齐、数量不足 |
| `DEPENDENCY_TEMPORARY` | 503/异步失败 | 按策略 | 外部超时、Broker/存储暂时不可用 |
| `DEPENDENCY_REJECTED` | 422/异步失败 | 修正映射后 | 外部业务拒绝、未知字典、来源冲突 |
| `INTERNAL` | 500 | 仅幂等安全时 | 未预期错误；响应不含堆栈和秘密 |

批量接口整体可返回成功包装但必须逐项标记结果；调用方不得把“有一个成功”解释为全部完成。

## 2. 错误码规范

格式：`PMS-<CONTEXT>-<CATEGORY>-<NNNN>`，例如 `PMS-PROJ-VERSION-0001`。错误码稳定，message 可本地化。

| 通用码 | 含义 | 响应附加信息 |
|---|---|---|
| `PMS-COMMON-VALIDATION-0001` | 输入校验失败 | fieldErrors |
| `PMS-COMMON-AUTHZ-0001` | 数据范围拒绝 | requestId，不回显越权详情 |
| `PMS-COMMON-VERSION-0001` | 聚合版本冲突 | currentVersion/currentStatus |
| `PMS-COMMON-IDEMPOTENCY-0001` | 幂等键请求摘要冲突 | scope/key hash，不回显原请求 |
| `PMS-COMMON-STATE-0001` | 非法状态命令 | currentStatus/allowedActionCodes |
| `PMS-COMMON-DEPENDENCY-0001` | 外部依赖暂时不可用 | retryable、operationId |
| `PMS-COMMON-PARTIAL-0001` | 批量部分失败 | item results/detailsRef |

领域实现需扩展具体错误，但不得以一个 `OPERATION_FAILED` 吞掉权限、状态、并发、外部拒绝和数据质量差异。

## 3. 幂等作用域与记录

| 场景 | 幂等键 | 作用域 | 首次结果重放 |
|---|---|---|---|
| API 创建/命令 | `Idempotency-Key` | tenant + endpoint/command + actor/business object | 返回原资源/operation 和响应摘要 |
| 动态表单模板/实例命令 | `Idempotency-Key` | tenant + command + actor + template/revision/instance intent | 新建模板、下一草稿、发布、启停或手工实例同载荷重放原结果；业务实例由消费Context外层命令持有唯一幂等记录，PLT写/持锁API以`MANDATORY`加入且不得自开事务；完成/克隆同时校验PLT与Owner双版本 |
| 项目授权创建/撤权 | `Idempotency-Key` | tenant + actor + project/grant + command | 同键同摘要返回原授权或撤权版本；同键不同摘要拒绝 |
| 实施就绪评估 | `Idempotency-Key` | tenant + `IMP_READINESS_EVALUATE` + project + actor | 同键同规范化项目/设备/方案请求返回原快照；同键异请求冲突；Provider失败不产生READY或成功事件 |
| 外部入向 | source eventId 或 sourceKey+version | sourceSystem + interfaceCode | 返回已处理结果；旧版本忽略 |
| 钉钉待办/通知回执 | providerMessageId+状态版本 | tenant + DingTalk notification | 更新同一通知投递状态，不推进业务状态 |
| 财务出向 | 费用单ID+批准版本 | tenant + finance interface | 查询/返回原财务业务单 |
| CollectionTask | 业务来源+平台任务幂等键 | tenant + sourceContext | 返回原平台任务 |
| DAC 下发 | 平台 CollectionTaskId | provider | 返回原外部任务或查询状态 |
| DAC 回调 | provider+callbackId；缺失时受控摘要 | tenant + provider | 不重复推进，返回首次处理 |
| 文件上传完成 | uploadSessionId+hash | tenant + file service | 返回原 FileVersion |
| 事件消费 | consumerCode+eventId | tenant + consumer | 不重复副作用 |

幂等记录保存请求规范化摘要、状态 `PROCESSING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL`、资源/operation 引用、响应摘要和到期策略。秘密字段在摘要前移除或使用不可逆受控摘要，绝不保存临时密码。

## 4. 同键重放规则

1. 同键同摘要且首次成功：返回相同业务结果，不再次执行。
2. 同键同摘要且处理中：返回平台约定的稳定`IN_PROGRESS`响应或业务错误，并在已有时返回同一operationId；禁止并发执行第二次或伪造成功响应。
3. 同键同摘要且可重试失败：由服务端状态机决定继续原 operation，不新建业务事实。
4. 同键不同摘要：返回 `IDEMPOTENCY_CONFLICT`，不使用新请求覆盖旧记录。
5. 业务要求“失败重试必须新任务”时（如 CollectionTask），retry command 创建新任务ID和新幂等键，并保存 `retryOfTaskId`；临时密码重新输入。

项目授权创建或撤权由PLT在本地事务内原子提交授权事实、幂等完成点和审计。跨租户、超出授权人范围、已到期、已撤销或版本冲突不得生成当前有效授权或成功幂等结果；授权版本或项目树版本变化后，敏感访问不得继续信任旧范围缓存。

## 5. 事务、部分失败与 Saga

- 单聚合命令在本地事务内原子提交聚合、状态历史和 Outbox。
- 跨聚合批量操作逐项事务，返回逐项结果；只有 PRD 明确要求全有或全无时才在单 Context 内扩大事务。
- 跨 Context 流程以事件和 Saga/过程状态跟踪；每个步骤有 forward action、确认条件、可重试分类、补偿命令和人工接管点。
- 补偿创建反向业务事实，不删除成功历史。例如释放 DeliveryScope、撤销未生效授权、取消外部未执行任务；已发生设备执行/财务入账需对账处理，不能技术回滚伪装未发生。

ADR-0032为F-PROJ-001建立限定的跨Context同步原子例外：PROJ在同一MySQL本地事务中同步调用ACC公开内部应用接口，ACC以`Propagation.MANDATORY`加入该事务；正式Project、ProjectTask执行契约、ACC交付件实例、幂等成功结果、成功审计和Outbox必须共同提交或共同回滚。失败不得形成Project草稿、初始化operation、`INITIALIZING`状态、Saga或异步补建任务。该例外不授权跨Context Repository访问；部署不再共享事务资源时必须阻断Feature并先完成批准的语义变更。

## 6. 状态机异常

| 异常 | 处理 |
|---|---|
| 通用 update 试图改 status | 输入拒绝，要求调用明确 command |
| 非法当前状态 | 409，返回当前状态和允许动作代码 |
| 状态字典存在但状态机无迁移 | 仍拒绝；字典值不产生迁移权限 |
| 工作流节点重复回调 | workflow task ID 幂等，返回原处理结果 |
| 工作流过期节点回调 | 记录审计，不回退/推进当前业务状态 |
| 状态命令事务成功、通知失败 | 业务保持成功；通知独立重试 |
| 状态命令事务成功、事件暂未发送 | Outbox 重试；响应可说明异步处理中 |

## 7. 树、设备归属和数量异常

| 场景 | 错误/降级 |
|---|---|
| 项目/任务移动形成环 | BUSINESS_GATE，拒绝并返回目标节点不合法 |
| 树版本变化 | VERSION_CONFLICT，重新加载完整版本 |
| 路径投影构建失败 | 真值已提交时保持旧完整投影并告警；不暴露半版本 |
| 同一设备并发归属 | VERSION_CONFLICT，返回当前最具体项目和 assignmentVersion |
| 归属投影延迟 | 详情使用当前真值；统计带旧水位和“更新中” |
| 订单行超分配 | BUSINESS_GATE；ERP减量造成存量冲突进入待调整，不删历史 |
| COM当前范围陈旧/冲突 | STALE与SCOPE_CONFLICT分离；版本变化允许刷新重试，项目任一未解决冲突整体失败关闭且不得返回部分范围 |
| COM Owner损坏/不可用 | OWNER_DATA_CORRUPTED不伪装为陈旧或503；PROVIDER_UNAVAILABLE可按同请求重试，不以空结果降级 |
| 数值调整 | 新增 adjustment，保存动作类型、方向和正负值，不覆盖原值 |
| PM-05 正式项目/销售业务无效 | BUSINESS_GATE；不创建转销批次、不归档临时项目 |
| PM-05 对象或设备部分失败 | 保持原 conversion 为部分失败/待处理，返回逐项结果；成功项不回滚，失败项按原批次幂等重试 |
| PM-05 同源已有生效目标 | IDEMPOTENCY_CONFLICT 或 BUSINESS_GATE；相同正式销售业务返回既有转销，不同目标拒绝 |
| PM-06 期次/群组/循环冲突 | BUSINESS_GATE；返回冲突项目、关系类型和期次，不修改现有成员关系 |
| PM-06 来源版本失效或部分期次无权 | 派生操作拒绝且不生成无来源副本；查询标记范围不完整，不把缺失期次计零 |
| ProjectTask绑定缺失或类型非法 | 模板发布/任务实例化拒绝；通用任务必须显式使用TASK_NATIVE，不允许以空绑定形成旁路 |
| 非TASK_NATIVE绑定目标不存在/无权/版本失效 | 任务保持原状态；返回绑定错误或权限拒绝，不创建通用任务内容替代，不泄露目标是否存在 |
| ProjectTask完成事实或规则版本冲突 | VERSION_CONFLICT/BUSINESS_GATE；重新读取事实和CompletionRule后评估，不按旧快照完成 |
| ProjectTask完成判定失败 | 追加失败的TaskCompletionEvaluation及未满足项，不推进状态；同一幂等键重放返回原判定，改变请求摘要则IDEMPOTENCY_CONFLICT |
| PM-07手工项目非空CRM重大级别 | BUSINESS_GATE/FORBIDDEN；拒绝创建或修正，不依赖前端隐藏 |
| PM-07首次无候选，或多候选未显式选择合法候选 | BUSINESS_GATE；不创建正式Project、决策历史或模板实例，仅允许通用失败审计；多候选显式选择本次合法候选可继续原子创建 |
| PM-07属性修正幂等重放 | 同键同摘要返回原当前值和历史ID；同键不同摘要IDEMPOTENCY_CONFLICT，不重复追加历史 |
| PM-07属性修正版本冲突 | VERSION_CONFLICT；当前值与历史均不改变 |
| PM-07创建后候选变化/无匹配/多匹配 | 当前值和只读评估历史按命令事务保存；冻结模板及阶段、任务、审批、交付件保持不变，不产生CHG完成事实 |
| PM-07既有异步操作日志写入失败 | 不删除或回滚已原子提交的匹配决策历史；以operationId保留完整业务证据并告警，可选auditLogId保持空；公共审计必达能力另行立项 |
| CUT-03条件变化与采集回调并发 | DAC结果保留；CUT仅关联与当前清单版本/stableItemKey/itemVersion/设备相符的结果，其余进入待核对，不覆盖当前答案、不复制DAC技术状态 |
| CUT-03自动采集失败后人工降级 | 自动失败结果正文和原因保持不变；授权工程师在同一事务关闭旧选择区间并追加带人工证据的MANUAL结果，唯一当前选择冲突则整体回滚，不把原CollectionTask改写为成功 |
| CUT-03已提交版本再次编辑 | VERSION_CONFLICT/BUSINESS_GATE；创建新清单版本并使下游未审批方案按PRD失效，不原位解锁或覆盖 |
| CUT任务创建缺少配置代码、代码在任务创建时点无适用发布修订或出现多个适用修订 | BUSINESS_GATE/CONFIGURATION_CONFLICT；任务、设备范围、阶段历史和成功审计零写入，不使用种子代码、当前时间或任意候选降级 |
| V146后既有NEW_PLATFORM任务配置身份前向补齐为零候选或多候选 | MIGRATION_INPUT_CONFLICT；任何任务更新前整批失败，保留原任务；LEGACY_FORWARD保持配置身份为空且不可进入CUT-03 |

## 8. 外部集成异常与降级

### 8.1 通用

- 连接/读取超时、限流和 5xx：可重试；使用退避和熔断，不在用户请求线程无限等待。
- 4xx/业务拒绝、Schema错误、未知字典：通常不可自动重试，进入隔离/待映射。
- 超时后可能已成功：先调用结果查询/对账；不能确认时保持 `UNKNOWN/PENDING_RECONCILIATION`。
- 外部恢复后按原业务键和版本重试；人工补录必须保存来源和待对账标记。

### 8.2 已确认降级

| 系统 | 降级 |
|---|---|
| CRM/ERP/ITR/MES | 最近成功本地副本 + 截止时间；允许 PRD 定义的人工补缺，恢复后对账 |
| 钉钉 | 站内待办兜底；恢复后按通知业务键重试并受控合并送达/阅读回执 |
| OA | 平台内可继续流程不阻断；授权申请保持待审批，站内/邮件通知 |
| 备件 | 线下申请后补录和核验；平台不重建库存业务 |
| 授权系统 | 邮件申请/人工查询后补录，完整授权码不入日志 |
| UMC | 以 INT-12 结果生成基础报告或人工报告，不重复采集 |
| 财务 | 导出文件人工对账并回填，未经核验不标成功 |
| 短信/邮件 | 站内消息+钉钉兜底；不改变业务状态 |
| 采集子应用 | 任务失败/待下发，保存实际停止点；不伪造成功结果 |

## 9. DAC 专项异常

| 异常 | 处理 |
|---|---|
| 凭证不存在/停用/撤销 | 授权拒绝；不暴露凭证是否属于他人 |
| 五元组不匹配 | 授权拒绝并记录主体、设备、协议、命令模板和业务对象 |
| 临时密码缺失/刷新/重试 | 要求重新输入；不得从历史任务恢复 |
| `saveAsCredential` 创建凭证失败 | 整个创建命令失败，不创建采集任务；不得在未获用户同意时降级为临时任务。重试沿用原幂等键和请求摘要，成功后凭证、默认创建人授权与任务只生成一次 |
| 下发超时 | 按平台任务ID查询外部任务，确认未创建后再重试 |
| 外部未知状态 | 保存原值并标记待映射，不默认成功/失败 |
| 回调验签失败 | 401/隔离，禁止推进任务 |
| 回调重复 | 返回首次结果，不重复业务消费 |
| 回调乱序/缺序 | 暂存并查询状态；超过窗口进入对账 |
| 撤销与执行并发 | 保存撤销时间和实际停止点；已产生结果不删除 |
| 结果文件哈希不符 | 隔离结果，任务不得完成 |
| 业务消费确认缺失或消费者不匹配 | `BUSINESS_CONSUMPTION` 任务保持 `RESULT_AVAILABLE`；不匹配确认返回业务门禁错误并留痕，不得发布 `CollectionCompleted` |
| 独立中心回调终态 | 仅服务端冻结为 `CALLBACK_TERMINAL` 的任务可按有效成功回调直接完成；失败/取消/安全异常进入各自终态 |

任何错误、日志和审计详情均不得包含临时密码、凭证明文、私钥、Token 或完整命令敏感输出。

## 10. 文件异常

上传中断不创建版本；hash/大小/MIME不符或扫描失败进入隔离；对象存储和数据库部分提交按 uploadSession 幂等补偿。预览失败不改变原文件和业务审核状态；归档失败保持待归档。

### 10.1 共享动态表单异常

| 场景 | 错误/失败行为 |
|---|---|
| 模板编码重复或已有唯一草稿 | 409；不创建第二模板/草稿，不改变当前发布指针 |
| 草稿配置JSON无效、字段键缺失或重复、引擎版本不支持 | 400/422；保持草稿最近成功内容，不发布也不静默裁剪配置 |
| 手工选择后模板停用或当前发布指针变化 | VERSION_CONFLICT；不得改用新修订或创建实例 |
| 已发布修订更新/删除 | STATE_CONFLICT；修订及既有实例保持不变 |
| 实例If-Match过期、未知字段或普通PATCH伪造`PmsFileArtifact`值 | VERSION_CONFLICT/VALIDATION；不覆盖普通值或文件事实 |
| 业务Owner Provider未知、不可用、scopeVersion漂移或拒绝动作 | 失败关闭；PLT实例/值/文件、SOL版本及成功审计均不改变 |
| 业务实例复制中任一FileReference失败 | 同一外层事务回滚目标实例、全部新引用、Outbox与消费方成功幂等/审计；同目标同版本重放不新增事件 |
| 模板API/iframe被CORS、CSP、frame策略或目标权限拒绝 | 浏览器如实显示失败；PLT不代理、不降级为服务端请求，不形成表单保存或其他业务成功事实 |

## 11. 重试、熔断和人工接管

重试策略按接口/操作注册，不使用一个全局次数覆盖全部系统。注册项包括 retryable errors、最大尝试、退避、总时间预算、查询结果动作、熔断条件、半开探测和人工接管阈值。

【待接口联调登记】具体数值由 12 分册的外部技术 Owner 和 Phase 3 运行设计基于 SLA/压测登记。业务有效期、审批期限和门禁阈值不得偷放进技术重试配置。

人工待办包含业务对象、失败步骤、错误分类、已尝试次数、当前外部状态、建议动作和证据引用；人工处理也必须调用受控 command 并幂等留痕。

## 12. 日志、留痕与敏感信息

每次异常记录 requestId/traceId/correlationId、context、aggregate、command、版本、主体、权限结果、错误码、重试/补偿状态和 detailsRef。响应不返回堆栈、SQL、内部 URL、对象存储键、认证头或秘密。

敏感字段在进入日志框架前结构化标记并移除/掩码；禁止依赖事后正则清洗作为唯一保护。临时密码即使请求失败也不保存到幂等请求原文。

## 13. 测试矩阵

每个有副作用的 API/Consumer 至少验证：

- 首次成功、同键同请求重放、同键不同请求冲突；
- 权限拒绝不创建幂等“成功”结果；
- 事务提交前/后崩溃恢复；
- Outbox 重复、Inbox 重复、事件乱序；
- 乐观锁冲突、非法状态、门禁失败；
- 动态表单唯一草稿、不可变发布修订、停用/指针漂移、实例CAS、受控文件字段伪造及浏览器配置失败；
- 外部超时但实际成功、部分失败、永久拒绝、恢复后对账；
- 日志/错误/事件/缓存/数据库敏感字段扫描；
- 批量逐项结果和人工补偿。

## 14. 门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| 错误可分类、可追踪 | PASS | 第 1、2、12 节 |
| 幂等作用域和重放明确 | PASS | 第 3、4 节 |
| 部分失败与补偿明确 | PASS | 第 5、8 节 |
| 树/设备/状态/DAC异常明确 | PASS | 第 6、7、9 节 |
| 敏感数据不进入失败证据 | PASS | 第 9、12 节 |
| 重试/熔断数值 | DEFERRED_TO_FEATURE_INTEGRATION | 对应 Feature 联调按外部 SLA 登记；Phase 3验证运行策略，不阻断 Phase 2 结构契约 |

本分册满足 Phase 2 异常与幂等结构门禁；任何 Feature 不得通过禁用幂等、吞掉冲突、把未知状态当成功或减少授权校验来实现“流程顺畅”。

## F-PROJ-008 阶段推进异常候选差量（IN_REVIEW）

| 分类 | 稳定语义 | 写入结果 |
|---|---|---|
| `VALIDATION` | 命令缺字段、目标非服务端相邻阶段 | 零业务写入 |
| `AUTHORIZATION` | 缺功能权限、`PROJECT_MANAGE`范围或当前项目经理关系 | 零业务写入 |
| `STATE_CONFLICT` | 生命周期非ACTIVE、当前非S0～S3或Stage形状冲突 | 零业务写入 |
| `BUSINESS_GATE` | Owner事实确定且存在未满足引用 | 返回有序稳定未满足引用，零推进写入 |
| `DEPENDENCY_UNAVAILABLE` | Provider缺失/重复/不可用或事实不可判定 | 与业务未满足区分，零推进写入 |
| `VERSION_CONFLICT` | Project/tree/stage/ref Owner版本变化 | 零推进写入，可重新读取后以新意图重试 |
| `IDEMPOTENCY_CONFLICT` | 同键异规范摘要 | 零业务写入 |

失败只允许保存拒绝审计或可重试技术状态，不得把Gate标为PASSED、写成功Snapshot/Outbox或推进任一Stage。已知未满足不是依赖故障；依赖未知不得当作未满足后继续，也不得当作通过。
