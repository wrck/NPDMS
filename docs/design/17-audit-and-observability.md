# SDS Phase 3：审计与可观测设计

> 文档状态：`DEFERRED_TO_PHASE_3`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：NFR-01～03，以及全部100项V1/V2需求的业务审计、异常、集成、事件、性能和发布证据
> Owner：SDS Phase 3可观测性架构；业务审计事实仍归各Owner Context
> 前置设计：11、12、15、16、14分册

## 1. 要回答的运行问题

| 角色 | 必须能回答的问题 | 主要信号 |
|---|---|---|
| 值班/运维 | 哪个版本、租户、接口或依赖正在变慢/失败？影响范围和开始时间是什么？ | RED/USE指标、Trace、结构化错误日志、releaseId |
| 项目/业务支持 | 某项目、任务、审批、文件、采集或通知当前停在哪一步，最近一次有效事实是什么？ | 业务审计、状态历史、operationId/correlationId |
| 安全审计 | 谁以什么权限访问/拒绝了何对象，是否发生秘密取用/泄露或越权尝试？ | 不可变审计、安全事件、拒绝指标 |
| 集成运维 | 外部请求是否受理、业务是否确认、是否重复/乱序/待对账？ | SyncBatch、外部调用span、回调/Inbox/Outbox指标 |
| 发布负责人 | 当前构建、配置、迁移和数据水位是什么，失败后是否已回退/前滚完成？ | releaseId/buildId/migrationVersion、部署审计、健康与业务探针 |

任何新增端点、后台任务、Consumer或外部接口必须至少映射上述一个问题；不能为了“多打日志”记录完整请求体或无界字段。

## 2. 五类证据分离

| 类型 | 用途 | Owner/存储 | 不得承担 |
|---|---|---|---|
| 业务审计 AuditRecord | 证明主体、权限、动作、对象、前后值、结果和时间 | Owner Context事务内审计/不可变审计库 | 高吞吐调试日志、秘密正文 |
| 结构化运行日志 | 解释一次请求/任务为何失败或降级 | 应用/采集器→集中日志 | 作为唯一业务真值、保存完整请求/响应 |
| 指标 | 判断频率、错误、延迟、饱和和SLO | 指标采集器→监控后端 | 用户/项目/设备ID等高基数明细 |
| Trace | 定位一次跨组件调用耗时和错误位置 | OpenTelemetry或等价标准→Trace后端 | 永久保存全部业务正文 |
| 安全事件 | 越权、重放、秘密泄露、异常取密和高风险审计失败 | 安全审计/告警后端 | 被普通管理员修改/删除 |

【方向已确认：P3-E05】按ADR-0004使用OpenTelemetry统一采集并接入企业现有后端。具体日志、指标、Trace、告警、安全事件后端、访问角色、留存和归档仍由运维/安全登记；未形成可复核证据前本分册不能基线化。

## 3. 统一关联标识

| 标识 | 生成/传播 | 作用 |
|---|---|---|
| `requestId` | 入口生成或接受合规值，响应回传 | 单个HTTP请求关联日志/错误 |
| `traceId/spanId` | 入口/Consumer创建并跨HTTP、消息、任务传播 | 跨服务/异步耗时链路 |
| `correlationId` | 一个业务过程首次创建，后续重试/补偿继承 | 跨请求、事件和外部系统业务链 |
| `tenantId` | 认证上下文服务端注入 | 隔离查询；不信任客户端值 |
| `businessObjectType/Id` | 业务命令确认后记录 | 关联项目/任务/文件/采集等对象；不作指标label |
| `operationId` | 异步/幂等操作创建 | 查询处理、重试和最终结果 |
| `eventId/callbackId` | Producer/外部源提供并校验 | Inbox/回调幂等和顺序 |
| `releaseId/buildId` | 发布制品生成并注入运行环境 | 比较版本前后性能、错误和迁移证据 |

关联ID进入日志、Trace和审计；指标只使用低基数的环境、服务、route模板、状态类、Context、provider和版本标签，不使用用户ID、原始URL、错误文本或业务对象ID。

## 4. 结构化日志

统一事件字段：`timestamp/level/service/env/releaseId/eventName/requestId/traceId/correlationId/tenantId/context/operation/routeTemplate/resultCode/errorCategory/durationMs/retryAttempt/detailsRef`。

| Level | 使用边界 | 示例 |
|---|---|---|
| ERROR | 不变量破坏、永久失败或需要人工处置 | migration_checksum_mismatch、credential_secret_leak_detected |
| WARN | 已受控降级、重试、回调冲突或投影延迟 | external_timeout_pending_reconciliation、tree_projection_lag |
| INFO | 重要业务/运行事实，不记录每次普通查询全文 | release_started、collection_dispatched、workflow_completed |
| DEBUG | 临时诊断，生产默认关闭 | 查询计划摘要、映射分支；仍禁止秘密/完整正文 |

- 使用稳定`eventName`和结构化字段，不用拼接长文本承担查询语义。
- 日志字段使用白名单；密码、私钥、Token、License全文、认证头、Cookie、临时密码、可逆密文和完整命令敏感输出在日志框架前移除。
- 姓名、电话、邮箱、客户、金额、设备端点按字段权限脱敏；普通值班人员只见定位所需摘要。
- 错误堆栈存受限日志并通过`detailsRef`关联，API响应只返回稳定错误码和requestId。
- 日志写入失败不能阻塞普通只读请求；高风险业务审计失败按14 §11 fail closed。

## 5. 业务审计模型

### 5.1 必审动作

| 类别 | 必审动作 | 必须前后值/快照 |
|---|---|---|
| 身份权限 | 登录/失败/登出、角色/数据范围、授权创建/撤销、拒绝 | 主体、来源、权限策略版本、范围摘要、拒绝码 |
| 项目治理 | 创建、层级移动、模板发布、WorkBinding/PermissionPolicy/CompletionRule变更、负责人/成员、回退、关闭、PM-05/06关系 | 前后父节点/定义版本、绑定类型与目标引用、规则版本/输入事实摘要、来源/目标、逐项结果 |
| 割接P3工作台 | 条件重匹配、人工填写/上传、采集任务下发、回调接收、结果绑定与人工复核 | 清单/规则版本、稳定项目ID、差异摘要、CollectionTask/callback引用、校验结果和失败原因；不记录秘密或完整敏感输出 |
| 审批状态 | 提交、审批、驳回、撤回、豁免、状态命令 | 输入快照、节点、意见、from/to状态和版本 |
| 设备凭证 | 创建、更新、轮换、授权、取用/拒绝、撤销、停用、任务引用 | 凭证ID/版本、主体、设备/协议/模板/有效期、任务、结果；无秘密 |
| 文件 | 上传完成、替换、下载、引用、审核、归档、隔离 | artifact/version/hash/purpose/业务对象/结果 |
| 集成 | 同步、字段裁决、人工补录、重试、补偿、对账、回调隔离 | sourceKey/version、批次、前后映射、结果 |
| 发布 | 构建、制品验证、配置发布、迁移、切换、回退/前滚、恢复演练 | release/build/migration/config/hash和操作者 |

业务审计与业务事务原子提交；跨Context过程各Owner记录自己的动作并共享correlationId。审计记录追加写；依据ADR-0006，业务事实、审批历史及PRD/SDS明确要求留痕的操作采用`PERMANENT_NON_DELETABLE`策略，业务用户、管理员、API、后台任务和存储生命周期均无修改、覆盖或删除能力；Word正文不做内容级审计。

### 5.2 审计查询

- 审计人员按授权tenant、时间、主体、对象类型、动作和结果查询；敏感对象遵守字段级脱敏。
- 业务管理员只能查询本人管理范围的业务留痕，不能查询凭证取密详情、跨项目拒绝或安全事件原文。
- 按ADR-0014，用户同时具备导出功能权限和对应数据范围即可导出，不增加独立审批节点；统一使用一个`ExportTask/ExportAudit`记录申请、生成、成功/失败/拒绝、多次下载和文件到期清理，不建立第二套导出审计。导出复用服务端实时授权和字段脱敏，审计记录永久留痕。按ADR-0016，关联文件生成后仅保留24小时；大范围导出使用异步任务，下载时重新校验原主体权限。

## 6. 指标体系

### 6.1 HTTP/页面 RED

| 指标 | 类型/标签 | NFR用途 |
|---|---|---|
| `http.server.requests` | counter+histogram；service/env/routeTemplate/method/statusClass/releaseId | 请求量、错误率、服务端P50/P95/P99 |
| `ui.navigation.duration` | histogram；browser/viewport/scenario/releaseId | 页面加载/主要交互P95≤2秒 |
| `active.sessions` | gauge；env/tenantClass | 50并发用户稳态证据；不以userId作label |
| `file.transfer.duration/bytes/result` | histogram/counter；operation/sizeBucket/result | 50MB上传/下载完整性与耗时独立统计 |

NFR-01错误率=稳态有效业务请求中服务端失败数/有效请求总数，目标≤0.5%；业务可预期校验拒绝和权限负向用例单独分类，不通过把失败改成2xx来降低错误率。

### 6.2 资源与内部队列USE

| 范围 | 指标 |
|---|---|
| JVM/进程 | CPU、heap/non-heap、GC pause、thread、file descriptor、process restart |
| DB | connection active/wait、query duration、slow query、lock wait/deadlock、replication/backup状态（生产） |
| Redis | command duration/error、connection、memory、eviction、cache hit/miss |
| 文件 | upload session、scan queue age、object storage error、hash mismatch |
| 事件 | outbox pending/age、publish retry、inbox duplicate/failure、dead-letter/isolated count |
| 投影 | project/task tree、device ancestor和analytics projection lag/watermark |

### 6.3 领域/集成指标

| 能力 | 指标与低基数标签 |
|---|---|
| 状态/审批 | command result、workflow node duration、rejection category；context/command/result |
| DAC | task state、dispatch duration、callback lag、result consumption lag、credential denial、secret scan hit；sourceContext/result |
| 外部集成 | request rate/error/duration、retry、circuit、reconciliation difference；interfaceCode/operation/result |
| 通知NFR-03 | valid sent、accepted、delivered、failed、delivery ratio；nodeCode/channel/result |
| 巡检进度NFR-03 | event produced/consumed、progress update lag、duplicate/out-of-order；nodeCode/result |

通知到达率按“已送达/有效发送”计算，目标≥99%；接口受理不计已送达。项目进度延迟从巡检事件发生时间到Project读取对应状态版本，目标≤60秒。

## 7. 分布式Trace

- 使用OpenTelemetry或等价标准；HTTP入口、数据库、Redis、消息、后台任务和外部HTTP自动采集基础span，业务关键命令/消费手工增加span。
- 入口将trace上下文传播到Outbox事件、Consumer、外部请求、回调映射和异步operation；不信任外部任意trace header跨越租户污染内部Trace。
- span属性只使用低基数/脱敏值：context、aggregateType、commandCode、interfaceCode、resultCode、retryAttempt、releaseId；businessObjectId可用于受限Trace检索但不作指标label。
- 生产采样按ADR-0011执行：普通成功请求保留10%；错误、高风险安全操作、审计写入失败及发布/迁移链路保留100%。未被Trace采样的请求仍产生规定指标；业务事实、审批历史及明确留痕操作仍按ADR-0006永久记录。
- 验收通过一次真实请求串联浏览器→后端→DB/事件/外部模拟→回调，确认无断链且各span时间合理。

## 8. 看板与告警

### 8.1 最小看板

1. 发布总览：releaseId、请求/错误/P95/P99、JVM/DB/Redis、迁移版本和变更时间线。
2. 业务可靠性：状态命令、审批、文件、Outbox/Inbox、投影水位、人工待办。
3. 集成：按interfaceCode显示请求、失败、重试、熔断、回调、对账和数据截止时间。
4. DAC安全与执行：凭证授权拒绝、取密、任务状态、回调、消费延迟、撤销停止点和秘密扫描。
5. NFR验收：50用户/30分钟/10000请求、三浏览器四视口、50MB、树规模、通知到达率和进度延迟。

### 8.2 告警原则

- 告警优先用户可感知症状：错误率、P95/P99、队列年龄、回调/消费延迟、业务成功率、通知到达率；CPU/内存等原因指标主要用于诊断。
- 每条告警必须有page/ticket级别、阈值、持续时间、适用环境、runbook、Owner和测试触发证据。除PRD已给出的0.5%、2秒、99%、60秒外，其他数值【待P3-E05和19压测登记】，不得猜测。
- 安全告警包括秘密命中、异常取密、跨租户拒绝突增、签名/重放失败、审计写失败和制品/迁移校验差异。
- 告警通道失败不改变业务状态；告警自身有发送/失败指标和兜底路径。

## 9. 降级、缺口与留存

| 故障 | 业务行为 | 证据要求 |
|---|---|---|
| 集中日志/Trace不可用 | 普通业务可在不泄密的本地结构化输出和受控缓冲下继续；不得无限缓冲拖垮服务 | 缺口起止、丢弃/积压量、恢复核对 |
| 指标后端不可用 | 交易可继续，但发布/NFR验收暂停，恢复后验证采集 | 告警、恢复时间、缺失窗口 |
| 不可变审计不可用 | 14列出的高风险动作fail closed；普通操作按批准策略 | 拒绝/缓冲计数和补偿审核 |
| Trace断链 | 不影响已提交业务，但测试/发布门禁失败 | 断点组件、修复和复验Trace |

业务事实、审批历史及PRD/SDS明确要求留痕的操作依据ADR-0006永久保留，不设置业务到期时间；允许转入冷存储，但必须保留对象关联、审批链、操作者、时间、前后状态、证据引用、完整性校验和授权检索能力，迁移前后数量与校验结果一致。更正只能追加新事实，不得覆盖原记录。

不属于ADR-0006永久范围的普通网络、安全运行日志按ADR-0007保存1年：在线180天，随后进入不可变冷存储185天。Trace按ADR-0008分层：普通Trace在线30天、冷存储60天，总计90天；错误及高风险Trace在线30天、冷存储150天，总计180天。指标按ADR-0009分层：原始高精度指标保存90天，5分钟级和小时级聚合指标保存13个月。调试日志按ADR-0010默认保存7天；专项故障临时延长最长30天，必须登记原因、负责人和到期时间。到期仅能由受控生命周期策略清理，并保留清理清单和审计；关联的永久业务事实与审计不随技术数据删除。任何归档不得使秘密进入低保护介质。

## 10. 验证矩阵

| Requirement/Gate | 操作 | 通过标准 |
|---|---|---|
| NFR-01审计 | 执行登录、权限、层级、审批、凭证、文件、集成补偿和状态命令，并尝试通过业务界面、管理接口、批处理和存储生命周期删除 | 审计字段完整、前后值/快照可追溯；永久记录无法修改、覆盖或删除；冷迁移前后数量、关联和完整性一致且可授权检索 |
| NFR-01性能 | 50用户30分钟≥10000有效请求 | 可查询总量、成功/失败、错误率≤0.5%、P50/P95/P99，release/env/dataSet齐全 |
| NFR-02秘密 | 唯一标记秘密执行成功/失败/超时/重试/撤销并扫描Telemetry | 日志、span、label、告警、审计明文命中0；只有受限安全事件保存摘要 |
| NFR-03通知 | 覆盖割接/巡检五节点和全部启用渠道 | delivered/valid sent≥99%，可下钻到脱敏发送/回执 |
| NFR-03内部事件 | 注入成功、重复、乱序和Consumer失败 | 对应版本≤60秒可读；重复不推进、乱序不回退、失败可补偿 |
| 故障定位 | 在验收环境诱发外部超时、DB冲突、回调验签失败 | 仅凭requestId/traceId/correlationId定位到失败步骤、重试/补偿和最终结果 |
| 告警 | 测试触发每条新告警 | 到达正确通道、链接runbook、恢复后自动/人工关闭证据完整 |

## 11. Phase 3可观测门禁

| 门禁 | 当前结论 |
|---|---|
| 审计对象、字段、权限和高风险fail-closed | PASS |
| RED/USE、NFR-01～03指标和关联标识 | PASS |
| Trace、看板、告警和故障验证方案 | PASS-WITH-EVIDENCE |
| 永久业务审计留存策略 | PASS（ADR-0006） |
| 普通网络/安全运行日志期限 | PASS（ADR-0007：在线180天+不可变冷存储185天） |
| Trace分层留存 | PASS（ADR-0008：普通90天，错误/高风险180天） |
| 指标分层留存 | PASS（ADR-0009：原始90天，5分钟/小时聚合13个月） |
| 调试日志有限期留存 | PASS（ADR-0010：默认7天，专项最长30天） |
| 生产Trace采样 | PASS（ADR-0011：普通10%，强制类别100%） |
| Telemetry后端和告警Owner | BLOCKED_BY_EVIDENCE（P3-E05） |

逻辑设计、留存/采样策略和验收契约完整后，本分册可进入SDS基线评审。P3-E05具体后端与实测证据在部署/可观测验收/生产发布前关闭，不前置阻断逻辑设计基线。
