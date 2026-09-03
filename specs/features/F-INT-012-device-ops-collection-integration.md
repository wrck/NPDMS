# F-INT-012 设备连接与采集平台集成 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY`
> Requirement：`INT-12（V1）`
> Requirement切片覆盖：`INT-12@V1=FULL`
> 关联Requirement：`EXE-03`、`EXE-04`、`CUT-03`、`CUT-06`、`INS-02`、`INS-04`、`NFR-02`；不宣称关联Requirement完成
> Owner Context：`PLT（平台公共能力 / Device Access & Collection）`
> 外部协作Context：`INT（集成域）`、`Device Ops`、`PLT（基础文件平台）`、`IMP（现场实施）`、`CUT（割接管理）`、`SRV（服务运营）`
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 当前实施切片：`Task 7：Platform回调事实、结果事件和消费确认`

## 1. 目标

复用现有Device Ops采集平台，为NPDMS提供统一的设备连接、命令模板执行、完整终态日志接收和业务结果消费闭环。Device Ops保持独立部署、独立数据库和独立运行事实；NPDMS不重复建设SSH/TELNET连接、命令执行、流式输出和执行引擎。

PLT拥有设备凭证、授权、采集批次、设备任务、Platform回调事实、业务主状态、完成模式和消费确认；INT拥有协议适配、下发尝试、回调Receipt、技术重试和对账；Device Ops拥有执行事实；基础文件平台拥有物理文件、`FileArtifact`和`FileVersion`。IMP、CUT、SRV等业务域只保存`fileVersionId`和本域关系，并独立解释业务结果。

本Feature冻结凭证安全、任务授权、同步临时秘密派发、签名multipart回调、完整不可变日志、结果版本、消费确认、幂等、顺序校验、对账和失败语义。技术执行成功不得直接解释为实施完成、割接成功或巡检闭环。

## 2. Scope

### 2.1 包含

- 独立设备连接与采集中心，以及EXE-03/04、CUT-06的V1业务入口；V2的INS-02/04复用相同契约；
- 已保存凭证和临时输入账号密码两种认证模式；
- 设备凭证密文或受支持KMS引用、显式授权、有效期和授权快照；
- 绑定任务、设备、协议、模板、受众、过期时间和`jti`的一次性取密令牌；
- 已发布命令模板及确定版本的任务创建、批次拆分、幂等、派发、取消、查询、重试和对账；
- 每台设备独立的`CollectionTask`、业务主状态、技术阶段、外部状态原值和失败分类；
- 成功、部分成功、失败、超时、取消和安全异常终态的完整、非空、不可变、已脱敏日志文件；
- INT签名`multipart/form-data`回调接收、验签、重放防护、顺序校验、Receipt和ACK；
- 基础文件平台流式保存日志并返回`fileVersionId`，扫描隔离时返回`quarantineEvidenceId`；
- PLT回调事实、任务与批次投影、结果事件、消费确认和Outbox原子提交；
- IMP、CUT、SRV按稳定事件/API消费结果引用并独立形成业务事实；
- 真实MySQL、Redis、HTTP/multipart、并发、失败恢复和真实浏览器闭环验证。

### 2.2 不包含

- 将Device Ops合并进NPDMS主进程、主Maven reactor或主业务数据库；
- 在NPDMS重复实现设备连接协议、命令执行、流式输出或运行时调度引擎；
- 任意命令输入；独立中心默认只能引用已发布模板及确定版本；
- 在PLT、INT、Outbox、缓存、日志、审计详情、异常、回调或导出中保存临时密码；
- 由INT直接写PLT业务表，或模块间依赖目标模块的Service、Mapper、Repository和业务表；
- 由业务域保存文件二进制、存储键、哈希、MIME或基础文件平台内部对象；
- 用技术回调结果替代IMP、CUT、SRV业务判定；
- 通过重试覆盖原任务、原始回调、原始日志或既有结果版本；
- 在本Feature中宣称EXE、CUT、INS完整业务流程、外部联调、SIT、UAT或Release完成。

## 3. Owner与边界

| 事实或能力 | Owner | 边界 |
|---|---|---|
| DeviceCredential、CredentialGrant | PLT | 只保存认证密文或受支持KMS引用；临时秘密无持久化字段 |
| CollectionBatch、CollectionTask | PLT | 拥有业务主状态、完成模式、冻结消费者、结果版本与引用 |
| Platform回调事实、消费确认 | PLT | 按`callbackId`、任务、消费者和`resultVersion`幂等 |
| Provider配置、DispatchAttempt | INT | 保存下发请求摘要、外部任务号、技术状态和对账证据，不保存秘密 |
| IntegrationCallbackReceipt | INT | 保存验签、序号、哈希、ACK和技术处理事实，不解释业务完成 |
| execution attempt、lease、命令块、流式输出 | Device Ops | 独立运行事实，不作为PLT业务状态权威 |
| FileArtifact、FileVersion、存储与扫描 | 基础文件平台 | 唯一正式文件写入方；以流式接口接收完整日志 |
| 实施、割接、巡检业务结果 | IMP、CUT、SRV | 只保存`fileVersionId`和本域关系，独立判定与闭环 |

跨模块只传稳定DTO、业务标识和文件引用；不得传DO、Mapper、Repository、文件二进制或永久凭证明文。

## 4. 业务规则

### BR-FINT012-001 凭证保存与授权

- 永久凭证只保存经认证加密的密文或系统明确支持且可解析的KMS引用；密钥或KMS解析能力不可用时必须失败关闭。
- 凭证默认仅创建人可用。项目成员、任务参与人、服务经理或管理员身份不自动获得凭证使用权。
- 授权必须显式绑定用户、设备、协议、命令模板和有效期；授权检查采用精确五元组及生效时间，零条或多条匹配均拒绝。
- 凭证查询和管理响应不得返回明文、可逆密文、私钥、访问令牌或KMS引用。
- 瞬时秘密使用可清零的`char[]`或`byte[]`，调用完成后由各责任边界清零。

### BR-FINT012-002 临时秘密

- 临时用户名允许写入任务用于审计；临时密码不得进入浏览器持久化存储、数据库、缓存、异步消息、日志、审计详情、异常、回调、Outbox或导出。
- 页面刷新、派发重试和再次执行必须重新输入临时密码。
- `saveAsCredential=true`时，凭证、默认授权和任务必须在同一业务命令内原子创建；失败不得静默降级为临时模式。
- 临时秘密任务先持久化不含秘密的`PENDING_DISPATCH`任务，再同步调用INT Gateway。明确拒绝转`FAILED/DISPATCH_FAILED`；网络结果未知转`RECONCILING`；禁止后台重放秘密。

### BR-FINT012-003 一次性取密令牌

- 令牌绑定`platformTaskId/deviceId/protocol/templateId/templateVersion/audience/expiresAt/jti`，任何绑定不一致均拒绝。
- 服务端只保存令牌哈希；Redis中的瞬时秘密也必须使用受认证加密保护，不得以明文或Base64保存。
- 消费必须以原子操作保证最多一个调用方成功；消费后令牌状态为已消费并清除秘密字段。
- 令牌过期、重复消费、受众错误、任务或模板不匹配均不得返回秘密，且必须留下不含秘密的安全审计。

### BR-FINT012-004 模板与任务

- 任务只能引用已发布模板及确定版本，冻结模板ID、版本和内容哈希。
- 任务记录业务上下文、项目、设备、协议、认证方式、授权快照、完成模式、必要消费者和幂等键。
- 批量请求按设备形成独立任务；单设备失败不得覆盖其他设备事实。
- 同幂等键同请求摘要重放返回既有结果；同键不同摘要拒绝。
- 重试必须创建引用原任务的新任务，不覆盖原任务、原始回调、日志或结果版本；临时秘密任务重试必须重新输入密码。

### BR-FINT012-005 状态模型

业务主状态固定为：

`CREATED → AUTHORIZED → DISPATCHED → EXECUTING → CALLBACK_PROCESSING → RESULT_AVAILABLE → CONSUMED → COMPLETED`

终止分支为`FAILED`、`CANCELLED`、`SECURITY_EXCEPTION`。技术阶段独立保存`PENDING_DISPATCH`、`DISPATCHING`、`ACCEPTED`、`RUNNING`、`TIMED_OUT`、`DISPATCH_FAILED`、`RECONCILING`、`RESULT_FILE_QUARANTINED`等值。

- `PARTIAL_SUCCESS`映射为`RESULT_AVAILABLE`并保留外部状态和技术结果分类。
- 失败、超时、取消、安全异常和文件隔离均是原任务终态，后续重试只能创建新任务。
- 已进入终态的任务不得被迟到或乱序回调回退到非终态。
- 独立中心使用`CALLBACK_TERMINAL`完成模式；有效成功终态回调可完成通用任务。IMP、CUT、SRV入口必须使用消费确认模式。

### BR-FINT012-006 下发、查询与对账

- INT按稳定API调用Device Ops，记录`DispatchAttempt`和请求摘要，不记录凭证明文或临时秘密。
- 明确受理后保存外部任务号和原始状态；下发超时或网络不确定时先查询，确认未创建后才允许按策略重试。
- 外部任务号与平台任务绑定；回调、查询或取消中的外部任务号不匹配时拒绝推进PLT任务。
- 回调丢失通过查询和定时对账补偿；不得伪造成功结果或绕过强制业务门禁。

### BR-FINT012-007 完整终态日志

- Device Ops对成功、部分成功、失败、超时、取消和安全异常均生成非空、完整、不可变、已脱敏的终态日志文件。
- 日志必须包含足以解释执行过程和终态的受控证据，不得包含凭证明文、私钥、Token或可重放秘密。
- 单次请求和组合文件不得超过正式文件契约限制；禁止静默截断。超限必须显式失败并保留可审计原因。
- INT以流式方式转交基础文件平台，不在内存中复制完整文件，不自行保存正式文件记录。
- 正常扫描返回`fileVersionId`；隔离分支返回`quarantineEvidenceId`，不得伪造`FileVersion`。

### BR-FINT012-008 回调安全与幂等

- Device Ops向INT发送签名`multipart/form-data`，包含结构化终态元数据和完整日志文件。
- INT执行来源认证、签名、时间窗、nonce/replay、任务绑定、序号、文件摘要和大小校验，形成不可变Receipt。
- INT向PLT只传结构化命令和`fileVersionId`或`quarantineEvidenceId`，不传文件二进制或通用文件元数据。
- PLT按`callbackId`幂等；重复回调返回既有结果，不重复推进状态、批次投影或发布事件。
- 回调序号小于或等于已处理序号时不得回退状态；序号存在缺口时进入可对账状态，不猜测缺失事实。
- 保留`externalStatus`原值、结果版本、回调证据哈希或Receipt引用和失败分类。

### BR-FINT012-009 结果事件与消费确认

事件链为：

`CollectionTaskRequested → CollectionTaskAccepted → CollectionTaskDispatched → 外部回调 → CollectionResultAvailable/CollectionFailed/CollectionCancelled/CollectionSecurityFailed → CollectionResultConsumed → CollectionCompleted`

- `CollectionResultAvailable`仅表示受控结果引用可读取，不表示业务处理成功。
- 成功和部分成功发布`CollectionResultAvailable(fileVersionId)`；失败或超时发布`CollectionFailed(fileVersionId)`；取消发布`CollectionCancelled(fileVersionId)`；安全异常发布`CollectionSecurityFailed(fileVersionId或quarantineEvidenceId)`。
- 业务消费者确认必须携带`consumerContext/consumerObjectType/consumerObjectId/resultVersion`，并与任务创建时冻结的必要消费者和当前结果版本完全匹配。
- 同一任务、消费者对象和结果版本的重复确认返回既有结果，不重复发布事件或推进任务。
- 只有全部必要消费者确认指定结果版本后，消费确认模式的任务才可进入`COMPLETED`并发布`CollectionCompleted`。
- 失败、取消、安全异常或隔离任务不得发布成功完成事件。

### BR-FINT012-010 事务与Outbox

- PLT处理回调时，在同一数据库事务内插入Platform回调事实、推进任务、更新批次投影并写入Platform Outbox。
- 消费确认在同一事务内写入确认事实、推进任务和写入Outbox。
- 任一步失败必须整体回滚，不允许任务已推进但事件缺失，或事件存在但结果引用未提交。
- Outbox载荷不得包含凭证明文、临时秘密、文件二进制、存储密钥或可逆密文。

## 5. API与稳定命令契约

所有新增PMS Business API遵循`/api/v1/pms/...`。模块间调用优先使用稳定API模块中的命令和DTO。

### 5.1 Platform公开能力

| 能力 | 约束 |
|---|---|
| 凭证创建与安全元数据查询 | 服务端派生租户和操作者；响应不含秘密、密文和KMS引用 |
| 批次/任务创建 | 要求幂等键；按设备拆分；冻结模板、认证方式、完成模式和消费者 |
| 任务详情与列表 | 返回业务状态、技术阶段、外部状态原值和文件引用，不返回秘密 |
| 重试 | 创建新任务并引用原任务；临时秘密必须重新输入 |
| 消费确认 | 校验消费者上下文、对象和`resultVersion`，重复确认无副作用 |

### 5.2 Integration调用Platform

`CollectionCallbackCommand`字段白名单：

- `receiptId`
- `callbackId`
- `sequence`
- `platformTaskId`
- `externalTaskId`
- `externalStatus`
- `resultVersion`
- `fileVersionId`
- `quarantineEvidenceId`
- `failureCategory`
- `startedAt`
- `completedAt`
- `traceId`

该命令不得包含文件二进制、通用文件元数据、存储键或秘密。

`CollectionConsumptionCommand`至少包含：

- `platformTaskId`
- `consumerContext`
- `consumerObjectType`
- `consumerObjectId`
- `resultVersion`
- `traceId`

## 6. 数据与物理边界

- PLT表承载`DeviceCredential`、`CredentialGrant`、`CollectionBatch`、`CollectionTask`、Platform回调事实、消费确认和Platform Outbox。
- INT表承载provider配置、`DispatchAttempt`、`IntegrationCallbackReceipt`、技术重试和对账事实。
- Device Ops使用独立数据库保存target、execution attempt、lease、命令块、流式输出和本地Outbox。
- 基础文件平台独占`FileArtifact/FileVersion`、对象存储键、哈希、大小、MIME、扫描状态和通用访问控制。
- PLT任务和业务消费者只保存`fileVersionId`或`quarantineEvidenceId`及必要关系，不复制文件平台字段。
- 唯一约束至少覆盖任务幂等键、`callbackId`、任务回调序号、任务+消费者对象+结果版本和Outbox事件ID。
- 非主键/稳定复合唯一键查询使用场景化Query对象；复杂查询进入Mapper XML；空权限或空集合必须返回空结果。
- 所有数据库变更使用新的前向Flyway迁移，不修改已执行迁移。

## 7. 权限与安全

- 用户必须同时具备功能权限、业务对象范围、设备范围和凭证授权；前端按钮可见、角色名称或项目成员身份均不替代服务端授权。
- 租户、用户、设备、项目、协议和模板范围均由服务端校验；跨租户标识一律拒绝且不泄露对象存在性。
- Device Ops取密身份必须是受信服务身份，并校验受众和任务绑定。
- 回调验签失败、重放、外部任务号不匹配、摘要不匹配或越权取密进入安全审计，不推进业务成功状态。
- 日志、审计detail、异常、指标标签、Outbox和响应禁止出现密码、私钥、Token、可逆密文或完整限时下载URL。
- 文件访问由基础文件平台逐次鉴权；业务模块不得构造持久对象存储地址。

## 8. 当前实施切片：Task 7

### 8.1 目标

在PLT内部实现回调事实、任务与批次投影、结果事件、消费确认和Outbox事务闭环。该切片接收INT已经完成认证、验签、文件流转和Receipt落库后的稳定命令，不实现INT HTTP客户端、multipart接收、Device Ops日志生成或业务消费者。

### 8.2 包含

- `CollectionCallbackApi`及稳定命令/结果DTO；
- `CollectionCallbackService`；
- Platform回调事实和消费确认持久化；
- `callbackId`幂等、序号校验、外部任务号匹配和结果版本校验；
- 成功、部分成功、失败、超时、取消、安全异常和隔离状态映射；
- 正常结果`fileVersionId`和隔离结果`quarantineEvidenceId`互斥校验；
- 同一事务内回调事实、任务推进、批次投影和Platform Outbox；
- 必要消费者与结果版本匹配、重复消费确认和完成条件；
- 单元测试、真实MySQL事务/唯一约束测试和迁移校验。

### 8.3 不包含

- INT对Device Ops的下发、查询、取消、技术重试和对账；
- 签名multipart接收、验签、Receipt和文件流转；
- Device Ops终态日志和回调Dispatcher；
- IMP、CUT、SRV具体消费者和业务结果解释；
- Platform管理页面和统一工作台。

### 8.4 Task 7验收条件

- `AC-FINT012-T7-001`：首次合法`callbackId`在单事务内插入回调事实、推进任务、更新批次投影并写入一个Outbox事件；任一步失败全部回滚。
- `AC-FINT012-T7-002`：重复`callbackId`返回既有处理结果，不重复推进任务、累计批次或写入Outbox。
- `AC-FINT012-T7-003`：低序号、重复序号、序号缺口和已终态任务的迟到回调不会回退业务状态；需要对账的分支保留明确技术状态。
- `AC-FINT012-T7-004`：`externalTaskId`不匹配时拒绝，且任务、批次和Outbox均无副作用。
- `AC-FINT012-T7-005`：`SUCCEEDED/PARTIAL_SUCCESS`映射结果可用，`FAILED/TIMED_OUT`映射失败，`CANCELLED`映射取消，`SECURITY_EXCEPTION`映射安全失败，并保留外部状态原值。
- `AC-FINT012-T7-006`：正常成功、部分成功、失败、超时和取消终态要求有效`fileVersionId`；隔离安全分支允许`quarantineEvidenceId`且不伪造`fileVersionId`；两个引用不得同时存在。
- `AC-FINT012-T7-007`：结果事件只包含稳定业务标识、状态、版本和文件引用，不包含二进制、存储字段或秘密。
- `AC-FINT012-T7-008`：消费确认必须匹配冻结消费者和当前`resultVersion`；错误消费者或旧版本拒绝且无副作用。
- `AC-FINT012-T7-009`：同一任务、消费者对象和结果版本的重复确认无副作用；全部必要消费者确认后仅发布一次`CollectionCompleted`。
- `AC-FINT012-T7-010`：失败、取消、安全异常和隔离任务不发布成功完成事件；真实MySQL验证唯一约束、并发幂等和事务原子性。

## 9. Feature验收标准

- `AC-FINT012-001`：Device Ops保持独立部署、独立数据库和运行事实；NPDMS未重复实现连接和执行引擎，模块间无实现层或业务表耦合。
- `AC-FINT012-002`：永久凭证仅以认证密文或可用KMS引用保存；临时密码在数据库、缓存、消息、日志、审计、异常、回调、Outbox和导出中均不存在。
- `AC-FINT012-003`：凭证授权精确绑定用户、设备、协议、模板和有效期；零条、多条、过期、停用、跨租户或越权匹配均拒绝且不解密。
- `AC-FINT012-004`：一次性令牌绑定完整任务上下文，服务端仅保存哈希，Redis秘密受认证加密，且并发消费恰好一个成功。
- `AC-FINT012-005`：临时秘密任务按先持久化后同步派发执行；明确拒绝和网络未知分别进入规定状态，系统不存在后台秘密重放。
- `AC-FINT012-006`：任务只引用已发布模板确定版本；批量按设备拆分；同键同请求幂等，同键异请求拒绝；重试创建新任务。
- `AC-FINT012-007`：下发超时先查询后重试；回调丢失可通过查询和对账恢复；外部任务号不匹配不推进PLT任务。
- `AC-FINT012-008`：所有终态均回传非空、完整、不可变、已脱敏日志；基础文件平台是正式文件唯一写入方，业务域只保存引用。
- `AC-FINT012-009`：签名multipart回调通过来源认证、验签、重放、序号、任务绑定、摘要和大小校验；无效请求隔离且不产生业务成功副作用。
- `AC-FINT012-010`：Platform回调按`callbackId`幂等并保留外部状态、证据引用、结果版本和失败分类；乱序或迟到回调不回退状态。
- `AC-FINT012-011`：结果事件映射、消费确认、完成模式和Outbox满足BR-FINT012-009/010；技术成功不直接解释为业务完成。
- `AC-FINT012-012`：IMP、CUT、SRV只通过稳定契约消费`fileVersionId`并独立形成业务结果；失败、取消或安全异常不发布成功业务完成事件。
- `AC-FINT012-013`：真实MySQL、Redis、multipart、并发、网络不确定、回调重放、文件隔离和对账测试通过；真实浏览器完成独立中心和业务入口闭环。
- `AC-FINT012-014`：本Feature完成不宣称EXE、CUT、INS完整业务流程、Deployment、SIT、UAT或Release完成。

## 10. 测试与证据

实现阶段至少需要：

- 凭证加密、KMS失败关闭、授权精确匹配、秘密清零和敏感字段扫描证据；
- 真实Redis令牌绑定、过期、错误受众和并发单次消费证据；
- 任务幂等、批次拆分、模板冻结、临时秘密同步派发和异常分类测试；
- 真实MySQL回调幂等、序号、外部任务号、状态映射、结果引用、批次投影和Outbox原子性测试；
- 消费者冻结、结果版本、重复确认、全部确认完成和失败状态禁止完成测试；
- INT下发超时查询、重试边界、回调丢失和对账测试；
- 签名multipart正常、伪造、重放、摘要错误、超限、扫描隔离和ACK测试；
- Device Ops全终态完整日志、本地Outbox原子性和回调重试测试；
- 模块边界、API字段白名单、跨租户和权限负向测试；
- 真实浏览器独立中心、EXE/CUT入口、刷新后临时秘密不保留、状态与日志访问闭环；
- 独立Feature Ready复审和代码评审。

## 11. 追溯与实施约束

- 本Feature派生自PRD `INT-12`及正式SDS，不改变Owner、状态、权限、文件或集成语义。
- `EXE-03/04`、`CUT-03/06`、`INS-02/04`仅作为入口和消费者关联，不因本Feature完成而自动完成。
- 当前允许实施的切片是第8节Task 7；后续INT客户端、multipart回调、Device Ops改造、管理工作台和业务消费者必须继续按本Feature边界实施与验收。
- NPDMS实现仓库必须锁定包含本文件的规格仓库提交并通过受管快照校验后，才可将本Feature作为实现输入。
