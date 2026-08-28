# Device Ops 采集平台完整业务集成设计

> 文档状态：`PROPOSAL_FOR_REVIEW`
> Requirement ID：INT-12、EXE-03～EXE-04、CUT-03、CUT-06、INS-02、INS-04、NFR-02
> 正式依据：PRD V1.8、SDS 08/10/11/12/13/14 分册及跨 Context 契约
> 文档层级：经用户确认的目标设计变更提案；Device Ops 集成域归属和完整日志强制回调属于对现行 PRD/SDS 的收紧变更，实施前必须完成正式基线回写
> Device Ops 来源仓库：NPDP
> 来源提交：`49c6cd2f313b5ae0c74fdb61d8765e6354232f73`

## 1. 背景与目标

现有 Device Ops 已形成独立设备连接与采集子应用，覆盖 SSH2、可选 Telnet、临时连接、加密保存连接、采集任务、连接级并发、租约恢复、流式输出、命令块、回调 Outbox、Vue 工作台及独立 Java 语义解析器。

NPDMS 的 INT-12 平台业务闭环尚未实现。本设计保持 Device Ops 独立构建、运行和持久化，在 NPDMS 建立凭证、授权、统一采集任务、可靠下发、回调证据、结果引用、消费确认和业务事件闭环。

NPDMS 不重复建设 SSH/Telnet、命令执行和原始采集引擎，也不把 Device Ops Java 包、Vue 组件、Flyway 或业务表并入主服务。

## 2. 核心裁决

### 2.1 Owner 复核范围

数据 Owner 规则只用于关闭此前评审中尚不明确的数据归属，不作为全局“首次出现即自动归属”的新规则，也不改写正式 PRD/SDS 已明确的 Owner。

本次复核结论仅包括：

- PLT/DAC 继续拥有 `DeviceCredential`、`CredentialGrant`、平台 `CollectionBatch`、设备级 `CollectionTask`、平台状态、完成模式和消费确认。
- Device Ops 的产品、源码、构建、发布和运维维护归属 INT 集成域，但运行时仍是独立执行子应用和独立数据边界。
- INT 的 NPDMS 侧适配层拥有 provider 配置、协议适配、平台到 Device Ops 的 `DispatchAttempt`、技术重试、对账批次、`IntegrationCallbackReceipt` 和完整日志文件接收过程。
- 基础平台负责回调文件的底层存储和文件记录保存，是 NPDMS 内原始文件二进制、storageKey、`FileArtifact`、`FileVersion`、通用文件元数据和访问控制的唯一正式持有方。
- Device Ops 执行侧拥有 SSH2/Telnet 会话、target、内部 execution attempt、lease、命令块、流式输出和执行侧解析事实，是原始执行过程的唯一写入方；其本地日志制品只是回调可靠投递缓冲副本，不是 NPDMS 正式文件。
- PLT 保存由 INT 验证后提交的 `CollectionCallbackRecord`、externalTaskId、外部状态原值、映射后的业务主状态、完整日志 `fileVersionId`、结果版本和业务结果事件；哈希、大小、MIME 等通用文件元数据只从基础文件平台读取，不在 PLT 重复持有。
- IMP、AST、CUT、Inspection 只保存基础文件平台 `fileVersionId` 与自身业务对象的关联，不持有或复制原始文件内容。

三类事实严格分离：Device Ops execution attempt 不进入 NPDMS 表；INT `DispatchAttempt` 只记录 NPDMS 与 Device Ops 的一次技术调用、查询、取消或对账尝试；PLT `CollectionTask` 只保存当前业务投影和稳定外部标识。INT 是 `DispatchAttempt` 唯一写入方，Device Ops 是内部 attempt/lease 唯一写入方，PLT 是 CollectionTask 业务状态唯一写入方。

`IntegrationCallbackReceipt` 以 `provider + callbackId` 唯一，记录网络接入、验签、nonce、文件接收、扫描、重试和提交 platform 的技术生命周期；`CollectionCallbackRecord` 以 `platformTaskId + callbackId` 唯一，记录平台任务状态推进所依据的业务回调事实。前者属于 INT 技术接入证据，后者属于 PLT 业务事实，两者通过 receiptId 关联，不共用表、不互相覆盖。
- IMP、AST、CUT、Inspection 的结果解释、正式配置 Log、清单判定和巡检闭环继续沿用正式 SDS 已明确的 Owner，不在本设计中重新裁决。

Device Ops Saved Connection 属于集成域子应用的连接能力，但 NPDMS 正式统一入口仍只允许平台 `DeviceCredential` 或临时输入，避免出现两套面向同一平台任务的永久凭证真值。

### 2.2 模块归属

Device Ops 的产品与工程维护整体放入 `pms-module-integration` 集成域，运行时保持独立子应用边界：

- `device-ops-platform` 源码、独立 Maven Reactor、Vue 工作台和部署材料归集成域维护；其执行数据库属于 Device Ops 运行时独立数据边界，不并入 NPDMS integration 数据库。
- `pms-module-integration` 负责 Device Ops HTTP/回调协议、服务身份、DTO 和状态映射、技术重试、对账、完整日志文件接收与安全校验。
- `pms-module-platform` 负责凭证、授权、批次、平台任务、业务状态、完成模式、消费确认和结果事件。
- 集成域通过 `pms-module-platform-api` 提交受理结果、回调事实和日志文件引用，不直接写 platform 业务表。
- platform 通过集成域公开的稳定调用端口发起下发、查询、取消和重试，不依赖 Device Ops 内部 Java 包、Repository 或数据库。

IMP/CUT/Inspection 等业务模块只依赖 platform 的稳定 API 或事件，不直接调用 Device Ops，也不依赖 integration 的内部实现。

### 2.3 构建与部署

`device-ops-platform` 保持独立 Maven Reactor、Vue 工程、JAR 和数据库，不加入 NPDMS 根 Maven Reactor。NPDMS 与 Device Ops 分别构建和验收，通过 HTTPS 集成。

## 3. 总体架构

```text
IMP / CUT / Inspection / 独立中心
                |
                | DAC API / CollectionTaskRequested
                v
        pms-module-platform
 Credential / Grant / Batch / Task
 Business State / Consumption / Outbox
                |
                | Integration Gateway API
                v
      pms-module-integration
 Device Ops Adapter / Auth / Mapping
 Retry / Reconcile / Callback / Log File
                |
                | 受保护同步接口
                v
       Device Ops Platform
 SSH2 / Telnet / Worker / Stream / JDBC
                |
                | 签名回调 + 完整日志文件
                v
     Integration Callback Endpoint
 验签 / 防重放 / 幂等 / 文件校验
                |
                | Platform API
                v
       Platform Callback Facts
```

## 4. 批量与设备级模型

### 4.1 CollectionBatch

一个用户请求形成一个批次外壳，保存：

- 批次 ID、租户、来源 Context、来源业务对象和版本。
- 项目、用途、命令模板 Owner、模板 ID、版本和哈希。
- 批次幂等键、请求摘要、权限快照和 traceId。
- 设备任务总数、成功数、失败数、处理中数量和批次汇总状态。

批次不直接下发。平台先在一个本地事务内完成全部设备的业务、权限、模板、凭证授权和字段校验，再创建批次及设备级任务骨架；此处的原子性仅覆盖 NPDMS 本地数据，不跨越 Device Ops。保存凭证任务提交后由可靠 worker 独立下发。临时输入任务在本地事务提交后立即逐设备同步下发；某台设备外部拒绝不回滚其他已受理任务，而是准确记录该设备 `DISPATCH_FAILED`，批次形成部分受理。临时秘密随该设备同步调用结束清除。

### 4.2 CollectionTask

每台设备独立任务保存：

- 平台任务 ID、批次 ID、租户。
- 来源业务对象、项目和设备稳定 ID及必要冻结快照。
- 协议、端点和命令模板版本。
- 认证模式、临时登录用户名或平台凭证 ID/版本/授权快照。
- 设备级幂等键和请求摘要。
- `completionMode` 和必要消费者快照。
- 外部任务 ID、技术状态、平台状态和外部状态原值。
- 结果版本、结果引用、来源哈希、平台校验哈希和执行时间。
- 失败分类、原任务 ID 和审计字段。

任务不保存密码、私钥、口令、Token 或完整 stdout/stderr。

批次汇总由设备任务投影生成：设备任务可部分成功或失败；取消和重试以设备任务为最小粒度，批次操作展开为对符合条件设备任务的受控命令。回调序号按设备任务和 provider 作用域递增。

### 4.3 CredentialGrant

授权绑定：用户、凭证、设备范围、协议、命令模板范围、有效期和平台任务。默认仅创建人可用，扩大范围必须显式授权。撤销不改写历史任务快照。

### 4.4 DispatchAttempt 与回调记录

INT 每次向 Device Ops 下发、查询、取消或对账时创建不可覆盖的 `DispatchAttempt`，保存尝试序号、脱敏摘要、目标、耗时、HTTP 状态、外部错误、外部任务 ID、重试原因和 traceId。

回调分为两类记录：INT 创建 `IntegrationCallbackReceipt`，保存 callbackId、验签、防重放、传输、基础平台文件保存状态、fileVersionId 和 platform 提交结果；PLT 创建 `CollectionCallbackRecord`，保存 callbackId、设备任务 ID、外部任务 ID、序号、外部状态原值、fileVersionId、结果版本、执行时间、失败分类和状态推进结论。文件哈希、大小、MIME、storageKey 和扫描状态只由基础平台文件记录保存。重复回调幂等返回；冲突回调隔离，不覆盖已确认事实。

## 5. 凭证与临时秘密链路

### 5.1 已保存凭证

平台 `DeviceCredential` 保存密文、算法、密钥版本、账号掩码和元数据，主密钥不进入业务数据库。首期唯一拓扑为 Device Ops 主动向平台秘密代理取密：平台下发任务时只传一次性取密令牌，Device Ops 不接收平台永久凭证明文。

一次性令牌绑定任务、设备、协议、模板版本、Device Ops 执行实例 audience、失效时间和唯一 jti。Device Ops 使用自己的服务身份调用 platform 内部取密端点；平台在同一事务中校验任务授权、撤销状态和 jti 未消费，原子标记 jti 已消费后返回一次秘密。网络超时后不得再次消费同一 jti；若平台无法确认响应是否到达，只能查询令牌消费状态，已消费则任务进入对账或失败，未消费且仍有效时由平台签发新 jti。Device Ops 不持久化取密结果，执行结束、失败、超时、撤销或会话关闭后清除内存。

### 5.2 临时输入

临时用户名和密码只在创建命令的 TLS 同步链路中传递。平台完成业务、设备、模板和临时认证校验后，在同一次同步调用中创建平台任务并向 Device Ops 下发；密码不得进入数据库、Redis、Outbox、消息、调度表、重试载荷、回调或导出。

代理、网关、APM、HTTP Client、Controller 和异常处理必须对该接口关闭正文采集，使用结构化禁止策略，不能只依赖事后正则脱敏。

同步结果规则统一采用“先提交平台任务，再执行外部同步调用”，数据库事务不跨越网络：

- 平台先提交不含密码的任务骨架和 `PENDING_DISPATCH` 技术阶段，再调用 Device Ops。
- Device Ops 明确受理后，在新事务中保存外部任务 ID、受理事实和下发尝试结果。
- Device Ops 明确拒绝时，平台任务仍保留并进入失败主状态，技术阶段记录为 `DISPATCH_FAILED`；不得删除任务或回滚其他设备任务。
- 网络超时导致受理状态未知时，只保存不含秘密的请求摘要并进入对账技术阶段，按平台任务 ID 查询外部状态；不得后台重放临时密码。
- 查询确认外部未创建后，任务保持失败；用户重试必须重新输入密码并创建引用原任务的新任务。
- `saveAsCredential=true` 时，平台在同一业务命令内原子创建凭证、创建人默认授权和任务；加密或授权失败则不创建任务，不降级为临时模式。

### 5.3 撤销

连接前撤销立即拒绝取密。连接中撤销通知 Device Ops 停止剩余命令、取消当前执行并关闭会话；无法安全中断的当前命令只等待返回或超时，必须记录实际停止点并禁止新连接和重试。

## 6. 状态与完成模式

正式主状态沿用 SDS 已冻结的粗粒度生命周期：

```text
CREATED
AUTHORIZED
DISPATCHED
EXECUTING
CALLBACK_PROCESSING
RESULT_AVAILABLE
CONSUMED
COMPLETED
FAILED
CANCELLED
SECURITY_EXCEPTION
```

外部受理、运行、超时、取消、安全异常、下发失败和对账中不新增为主状态，而记录在独立技术阶段、外部状态原值和失败分类中：

```text
PENDING_DISPATCH / DISPATCHING / ACCEPTED / RUNNING /
TIMED_OUT / CANCELLED / SECURITY_EXCEPTION / DISPATCH_FAILED / RECONCILING
```

平台一条设备任务严格对应 Device Ops 一个 target。Device Ops target 返回 `PARTIAL_SUCCESS` 时，platform 主状态映射为 `RESULT_AVAILABLE`，技术结果分类保存为 `PARTIAL_SUCCESS`，完整日志文件必填，并发布带该分类的 `CollectionResultAvailable` 供业务消费者决定是否接受；它不作为 platform 主状态。批次部分成功由各设备任务的成功、部分成功和失败结果汇总形成。技术阶段迁移表、失败后能否恢复以及取消/撤销路径必须在实施前回写状态机 SDS 和 Feature Spec，数据库约束只使用正式冻结代码。

- 外部 `ACCEPTED` 只表示受理，对应主状态 `DISPATCHED`。
- Device Ops 技术成功回调后，平台形成结果版本并进入 `RESULT_AVAILABLE`。
- `BUSINESS_CONSUMPTION`：IMP、CUT、Inspection 入口必须冻结必要消费者 Context、对象类型和对象 ID；只有匹配任务、结果版本和业务对象的幂等消费确认到达后，任务才依次进入 `CONSUMED`、`COMPLETED`。
- `CALLBACK_TERMINAL`：仅允许服务端为独立中心设置；有效成功终态回调可直接进入 `COMPLETED`。
- 外部失败和超时进入 `FAILED`；取消进入 `CANCELLED`；安全异常进入 `SECURITY_EXCEPTION`。三者均保留精确技术阶段和失败分类，不能通过消费确认变成成功。`CANCELLED`、`SECURITY_EXCEPTION` 作为本次目标设计需回写正式状态机 SDS。
- 技术成功不等于割接、实施、巡检或服务业务通过。

失败、取消、安全异常和文件隔离均为原任务终态，不原地恢复。重试或安全处置后的重新采集创建引用原任务的新任务；Device Ops 使用新 callbackId 和新任务结果版本回调，不重置原任务和证据。批次汇总区分全部成功、部分成功、全部失败和处理中。

## 7. 下发契约

NPDMS 向 Device Ops 提交：

- `platformTaskId`、`batchId`、设备级 `idempotencyKey`。
- 项目、设备稳定标识和冻结快照。
- 协议和受控端点。
- 已发布命令模板内容、Owner、版本和哈希。
- 任务级短期执行授权。
- 一次性取密令牌，或仅在临时模式同步请求中的 write-only 秘密。
- 回调 provider、受控回调标识和 traceId。

Device Ops 返回 `externalTaskId`、是否首次创建、受理状态和受理时间，并以平台任务 ID 与幂等键原子去重。

下发超时后先按平台任务 ID 查询，确认不存在后才能重试。临时模式不能自动重放；保存凭证模式只能在原授权仍有效且未撤销时受控重试。

## 8. 回调认证协议

首期冻结为服务到服务 HMAC-SHA256 协议；密钥按 provider、环境和 key version 分离管理，不与业务数据库同库保存。

请求头：

```text
X-DAC-Provider
X-DAC-Key-Version
X-DAC-Timestamp
X-DAC-Nonce
X-DAC-Content-SHA256
X-DAC-Signature
```

canonical string：

```text
HTTP_METHOD + "\n" +
NORMALIZED_PATH + "\n" +
PROVIDER + "\n" +
ENVIRONMENT + "\n" +
AUDIENCE + "\n" +
TIMESTAMP + "\n" +
NONCE + "\n" +
CONTENT_SHA256
```

规则：

- `CONTENT_SHA256` 不使用带 boundary 的原始 multipart body；固定按第 242 行定义的规范化 manifest 与日志文件内容算法计算。路径使用网关验签前的登记规范路径，不接受重写后的不确定值。
- audience 固定为 NPDMS Integration Device Ops callback，provider 和 environment 必须与集成域配置档案一致。
- nonce 唯一键作用域为 `provider + environment + keyVersion + nonce`，保存时间不少于重放窗口。
- 首期重放窗口为 5 分钟；部署可收紧，不得放宽而不变更配置档案和安全评审。
- 密钥轮换允许当前和下一版本短期双读，旧版本到期后拒绝。
- 未知 key version、时间窗外、nonce 重复、body hash 不符或常量时间验签失败均返回统一拒绝，不推进任务。
- 外部状态查询使用双向 TLS 或 OAuth2 client credentials，并校验响应来源；查询结果必须经过与回调相同的任务、租户、设备和结果哈希校验。

回调采用签名 `multipart/form-data`，单次终态请求包含一个 JSON manifest 和 `1..N` 个日志 part。manifest 包含 callbackId、序号、platformTaskId、externalTaskId、设备标识、外部状态原值、resultVersion、partCount、每个 part 的文件名/MIME/字节数/SHA-256、组合文件 SHA-256、执行时间和失败分类。所有 part 必须在同一请求内到齐，共用一个 callbackId、nonce 和回调序号。manifest 使用 UTF-8 RFC 8785 JSON Canonicalization Scheme；`CONTENT_SHA256 = SHA256(canonicalManifestBytes || part1Bytes || ... || partNBytes)`，part 按 partNumber 升序，`||` 表示无分隔符的原始字节连接。Feature 契约测试必须提供固定 manifest、part 和 HMAC 测试向量。

## 9. 回调、消费确认与对账

回调首先进入 `pms-module-integration`：

1. 以流式方式接收 manifest 和全部日志 part，不把任一文件整体加载到 JVM 内存；所有 part 必须在同一请求中到齐，本 Feature 不支持断点续传。
2. 校验服务身份、签名、时间窗、nonce、声明大小和实际大小。
3. 以 `provider + callbackId` 查找或创建 `IntegrationCallbackReceipt`。相同 callbackId 且 manifest/hash 相同的请求执行整包幂等重传：`RECEIVING/VALIDATING/PROCESSING` 返回当前 Receipt，`COMPLETED` 返回既有 receiptId/fileVersionId，`REJECTED` 且 retryable=true 允许同 callbackId 重新进入 `RECEIVING` 执行整包重传，`REJECTED` 且 retryable=false 或 `QUARANTINED` 返回稳定失败；相同 callbackId 但摘要不同立即冲突隔离。传输不完整不保存 part 内容，只记录可重试失败，发送端必须使用同 callbackId 重新发送完整 multipart。
4. 校验平台任务、外部任务、provider、租户和设备绑定。
5. 校验设备任务级序号和状态转换。
6. 对文件计算 SHA-256，执行类型、魔数和恶意内容扫描；失败文件进入隔离区。传输缺失可用原 callbackId 恢复，内容扫描失败或终态事实变化必须使用新 callbackId 和更高 resultVersion。
7. 将通过校验的日志 part 组合为一个受控归档，通过基础平台内部文件 API 完成底层对象写入和文件记录保存，创建 `FileArtifact/FileVersion`。基础平台保存文件二进制、storageKey、内容版本、来源文件名、大小、MIME、来源哈希、平台哈希、扫描状态和通用访问控制；INT、PLT、IMP、AST、CUT、Inspection 均不另建文件记录、不复制原始二进制。
8. 集成域取得基础文件平台返回的 `fileVersionId` 后，通过 `pms-module-platform-api` 提交回调事实及文件引用；platform 幂等推进任务并同事务写入 Outbox。
9. 成功终态进入 `RESULT_AVAILABLE` 并发布 `CollectionResultAvailable`；失败和超时进入 `FAILED` 并发布携带证据 `fileVersionId` 的 `CollectionFailed`；取消进入 `CANCELLED` 并发布 `CollectionCancelled`；安全异常进入 `SECURITY_EXCEPTION` 并发布携带证据引用的安全失败事件。`CALLBACK_TERMINAL` 仅对成功终态可完成，`BUSINESS_CONSUMPTION` 成功任务保持 `RESULT_AVAILABLE`。

消费确认必须携带 consumerContext、consumerObjectType、consumerObjectId 和 resultVersion，并与任务冻结消费者完全匹配。重复确认幂等；旧结果版本不能完成新结果。

回调丢失通过集成域状态查询和对账补偿。乱序但不冲突的回调保留证据，仅合法高序号推进；缺号、冲突状态或文件哈希冲突进入人工核对。重复回调不得重复保存文件、注册 FileVersion 或发布事件。

## 10. 完整日志文件契约与安全

Device Ops 必须在每个设备任务终态回调中上传完整日志文件，不只返回引用。日志文件是单设备、单次执行、单结果版本的不可变证据，至少完整包含：

- 执行上下文摘要、脚本及模板版本。
- 每条命令的开始、结束、退出状态和命令块边界。
- 完整脱敏后的 stdout、stderr 和交互会话输出。
- 分页控制、超时、取消、截断和输出上限事实。
- 执行侧解析事实、解析器版本和质量报告。

完整表示“不因回调裁剪业务日志内容”，但秘密仍必须在 Device Ops 输出持久化前完成结构化脱敏；密码、私钥、Token、认证头和分页控制输入不得因“完整”要求重新写入文件。

文件传输和存储规则：

- 回调接口使用流式 multipart 上传，集成域边接收边计算 SHA-256，并写入隔离临时对象。
- 每个设备任务的成功、失败、超时、取消和安全异常终态均必须回传日志文件；即使尚无设备输出，也要回传包含执行上下文、终态和失败事实的非空日志。批次部分成功由各设备终态及其日志汇总形成。
- 每个 part 和组合后的完整日志均遵循平台 50MB 上限，单次 multipart 总请求上限为 50MB，partCount 上限为 16。分卷只用于传输和流式处理，不允许绕过文件总大小限制；完整日志超过 50MB 时回调失败并记录 `RESULT_FILE_TOO_LARGE`，不得静默截断或形成有效 FileVersion。每个 part 独立计算哈希和排序，但不单独签名；整个 multipart 请求按组合哈希只签名一次。
- manifest 固定包含 `resultVersion`、`partCount`、每个 part 的 `partNumber/fileName/contentType/size/sha256` 和组合文件 SHA-256；单文件时 `partCount=1`。
- 文件校验和扫描成功的正常终态，platform API 和事件中的 `fileVersionId` 必填；分卷上传完成后指向统一受控归档 FileVersion。文件被隔离时改传 `quarantineEvidenceId`，不得伪造有效 fileVersionId。旧的纯外部结果引用字段只用于读取历史数据，不允许新任务继续产生。
- 只允许登记的文本、JSON 或受控归档 MIME；归档文件禁止路径穿越、符号链接、宏、脚本和外部实体。
- 分卷缺失、顺序错误或哈希不一致时整次回调拒绝并由 Device Ops 重传。文件扫描失败时，集成域仍向 platform 提交外部终态、`receiptId` 和隔离证据引用，使任务不会永久停留在处理中；platform 主状态进入 `FAILED`，失败分类为 `RESULT_FILE_QUARANTINED`，发布携带隔离证据引用的 `CollectionFailed`。隔离对象不是可消费 `FileVersion`，不满足正常事件的 `fileVersionId` 必填规则；安全处置后必须创建引用原任务的新平台任务，由 Device Ops 使用新 callbackId 和新任务 resultVersion 回传完整日志，不覆盖或恢复原失败证据。
- 集成域只保存 `IntegrationCallbackReceipt`、临时接收/隔离状态、校验事实和基础平台返回的 `fileVersionId`，不创建正式 `FileArtifact/FileVersion` 记录，也不保存正式文件二进制。
- 基础平台负责底层文件存储和文件记录保存，是 NPDMS 内 `FileArtifact/FileVersion` 的唯一写入方。基础平台记录来源系统、来源制品键、内容哈希、大小、MIME、版本、扫描状态和通用访问控制；平台任务、外部任务、设备、结果版本及用途等业务关系不写入文件记录，由各业务 Owner 仅保存 `fileVersionId` 及本域关联。
- 文件名只作为展示元数据，物理 storageKey 由基础文件平台生成，不包含客户、项目、用户名、密码或设备秘密。
- Device Ops 在本地同一事务中原子写入终态、不可变日志制品元数据和 Callback Outbox；日志二进制先原子落入本地制品文件，再由 Outbox 保存制品 ID、manifest 快照和哈希，不把文件内容写入 Outbox 数据库字段。启动恢复会扫描终态但缺失 Outbox 或缺失制品的异常记录并转失败隔离。
- 集成域只有在基础文件平台已持久化 FileVersion、platform 回调事实已接受且 `IntegrationCallbackReceipt` 标记完成后，才返回 `200` 与 `{callbackId, receiptId, status:"COMPLETED", fileVersionId, accepted:true}`。处理中返回 `202` 与 `{callbackId, receiptId, status}`；稳定失败返回明确 4xx/5xx 和错误码，均不标记投递成功。
- 提供 `GET /internal/device-ops/callback-receipts/{provider}/{callbackId}`：返回 receiptId、status、fileVersionId、errorCode 和 retryable。Device Ops 收到 `202` 后按 callbackId 查询；`COMPLETED` 后删除投递资格，`RECEIVING/VALIDATING/PROCESSING` 继续轮询，`REJECTED` 且 retryable=true 时使用同 callbackId 整包重传，`QUARANTINED` 或 retryable=false 时进入死信并等待新任务/新 callbackId。
- Device Ops 在收到上述成功 ACK 前保留本地原始日志；重复 callbackId 返回同一 receiptId/fileVersionId。死信后日志继续保留；只有可重试 Receipt 才允许按原 callbackId 人工整包重投，确认成功后按配置保留期清理，不影响基础文件平台中的 FileVersion。

各业务域只持有文件引用及自身业务关系：PLT 保存 DAC 结果 `fileVersionId`、结果版本和文件可用状态；IMP/CUT/Inspection 保存 `fileVersionId` 与各自业务结果、清单项或报告的关联；当用途属于 EXE-03 ConfigurationLog 时，IMP 通过既有 `ConfigurationLogPublished` 契约把同一 `fileVersionId` 提交 AST，AST 只登记 ConfigurationLog 业务身份、设备关联和不可变正式解析版本，不复制文件二进制。来源哈希、平台校验哈希、大小、MIME 和扫描状态统一由基础文件平台提供。

## 11. 稳定 API 与事件

平台 API 至少支持：

- 批量创建采集任务，按设备返回独立任务。
- 查询批次和设备任务详情。
- 下发、查询、取消和显式重试。
- 查询完整日志文件元数据并按权限获取 FileVersion。
- 业务消费者确认结果消费。
- 内部 provider 回调。

`CollectionTaskRequested` 只传稳定业务对象、设备、模板版本、授权引用、幂等键、权限快照和 traceId，不传永久凭证明文。

`CollectionResultAvailable` 至少包含 eventId、eventVersion、aggregateVersion、任务和批次 ID、来源业务对象、项目、设备、模板版本、技术状态、外部状态原值、结果版本、完整日志 `fileVersionId`、执行时间、失败分类、租户、权限快照和 traceId。文件哈希、大小、MIME 和扫描状态由消费者按 fileVersionId 从基础文件平台查询。

匹配消费者完成解释和证据关联后发送 `CollectionResultConsumed`；平台形成 `CollectionCompleted`。CUT 必须按清单项、设备和规则版本独立判定，技术成功不能直接完成清单项。

## 12. 统一入口与浏览器安全

首期冻结为 NPDMS 同源统一入口：NPDMS 菜单进入平台 DAC 页面，由 NPDMS 后端创建任务和授权；Device Ops 工作台通过受控同源反向代理路径提供，不采用外部简单跳转，也不采用 iframe。

统一入口要求：

- 登录由 NPDMS 统一身份体系完成，Device Ops 只接受受控代理签发的短期 audience 限定身份或服务端会话交换。
- 业务上下文通过服务端生成的短期签名 context token 传递，绑定租户、用户、来源对象、项目、设备、允许操作和失效时间。
- 不接受浏览器直接传入任意返回 URL；只接受服务端登记的 routeKey。
- 代理层固定 upstream，剥离外部伪造身份头，并关闭秘密接口正文日志。
- CORS 仅允许登记同源，启用 HSTS、CSP、X-Content-Type-Options、Referrer-Policy 和适当的 `frame-ancestors`。
- `/device-ops/**` 集成必须同步配置 Vite base、Router base、API base、认证回调和 SPA fallback。
- 页面不得把临时密码写入 localStorage、sessionStorage、草稿或自动填充数据。

独立 Device Ops 运维入口可保留，但不作为 PRD 统一业务入口，也不能绕过 NPDMS 的任务、凭证和授权模型。

## 13. 源码同步范围与可复现性

权威同步源固定为 NPDP 提交：

```text
49c6cd2f313b5ae0c74fdb61d8765e6354232f73
```

只同步该提交中已纳入版本控制的 `device-ops-platform`。不以未提交工作树内容作为实施输入，不同步 `node_modules`、`.tmp-mock`、`data`、构建产物或本地秘密。

如需纳入额外 UI 修复，必须先在来源仓库形成提交 SHA，或生成可审计补丁文件及 SHA-256，并在实施计划中登记；未冻结前不得复制。同步后记录源 SHA、目标文件清单和差异校验结果。

## 14. 错误处理

- 权限、设备、模板或授权校验失败：不创建任务。
- 保存凭证加密失败：凭证、授权和任务全部失败，不降级。
- 下发 4xx：不可自动重试，进入 `DISPATCH_FAILED`。
- 下发 5xx、断网或超时：先查询外部结果；临时秘密任务禁止后台重放。
- Device Ops 队列满：保存明确错误分类和建议重试时间。
- 验签、重放、任务绑定或哈希校验失败：登记隔离证据，不推进状态。
- 未知外部状态：保留原值，进入 `RECONCILING`。
- 结果扫描失败或引用越界：隔离且不可下载、消费或归档。
- 业务消费者失败：由 Outbox/Inbox 重试，不回滚已确认采集事实。

## 15. 测试与验收

### 15.1 NPDMS 单元与 MySQL 集成测试

覆盖：

- 批次原子创建和设备级幂等。
- 状态机、完成模式和消费确认。
- 凭证授权、撤销和一次性取密令牌。
- 临时秘密不进入持久化、消息、日志和重试载荷。
- 并发相同请求只有一个赢家。
- 下发尝试历史不可覆盖。
- callbackId、防重放、乱序、缺号和冲突。
- 任务、回调、结果版本和 Outbox 原子提交。
- 多设备部分成功、取消和重试。
- 租户、权限、设备和结果访问隔离。

### 15.2 契约与安全测试

覆盖：

- Device Ops 提交、查询、取消和回调 DTO。
- HMAC canonicalization、key version、轮换、时间窗和 nonce。
- 未知状态不映射为成功。
- 任意 URL、重定向、私网目标和跨租户对象引用被拒绝。
- 文件大小、类型、哈希和扫描隔离。
- 敏感字段不出现在响应、日志、事件和错误中。
- `CollectionResultAvailable`、`CollectionResultConsumed` 和 `CollectionCompleted` 稳定兼容。

### 15.3 Device Ops 独立验证

通过 Vue 单元测试、TypeScript、ESLint、生产构建、Java 单元与集成测试、Maven verify、可执行 JAR和静态资源检查。

### 15.4 NPDMS 质量门禁

通过相关模块单元、MySQL 集成和契约测试、模块边界脚本、Maven 编译测试打包、前端 TypeScript/Lint/生产构建及 Flyway validate。

### 15.5 端到端闭环

1. NPDMS 批量创建多设备任务。
2. 保存凭证和临时输入分别通过受保护链路下发。
3. Device Ops 原子受理并返回设备级外部任务 ID。
4. Device Ops 执行模拟 SSH/Telnet 并生成完整脱敏日志文件。
5. Device Ops 通过签名 multipart 回调上传完整日志文件。
6. 集成域流式接收、验签、计算哈希、扫描并注册 FileVersion，platform 幂等进入 `RESULT_AVAILABLE` 并发布事件。
7. 业务消费者独立解释并确认消费，平台任务进入 `COMPLETED`。
8. 验证部分失败、重复/乱序回调、下发超时、回调丢失、撤销、哈希冲突和扫描失败。

真实厂商设备、真实 OIDC/KMS、生产网络、跳板机和弱算法兼容属于部署环境验收，不得由模拟测试替代生产就绪结论。

## 16. 非目标

本次不包含：

- 把 Device Ops 合并进 `yudao-server` 或 NPDMS MySQL。
- 导入 Device Ops Vue 组件到 NPDMS 管理端源码。
- 将完整日志文件二进制写入 integration 或 platform 数据库大字段；完整日志必须进入统一文件存储。
- Device Ops H2 多副本或高可用改造。
- 真实厂商设备兼容承诺。
- 任意命令输入治理。
- 将技术成功直接解释为业务通过。
- 未经正式基线变更把永久凭证 Owner 迁移到 Device Ops。

## 17. 实施顺序

1. 将 Device Ops 归属集成域、主状态/技术阶段和 CollectionBatch 模型回写正式 SDS/ADR并完成评审。
2. 冻结 platform、integration 与基础平台的内部 API：integration 流式转交文件，基础平台执行底层存储、扫描和 `FileArtifact/FileVersion` 记录保存，并返回幂等 `fileVersionId`；同时冻结隔离、错误分类、补偿、回调 ACK、设备档案和各模板 Owner 契约。
3. 生成可追溯的 INT-12 Feature Spec、实施计划和 Task。
4. 固定 Device Ops 来源 SHA 和同步清单。
5. 将 `device-ops-platform` 同步到集成域目录并验证独立构建。
6. 在 platform 实现凭证、授权、批次、设备任务、状态和消费确认。
7. 在 integration 实现 Device Ops 下发、一次性取密适配、查询、取消、技术重试和对账。
8. 改造 Device Ops 终态产物，生成完整脱敏日志文件和签名 multipart 回调。
9. 在 integration 实现流式回调接收、验签、防重放、分卷和幂等接入记录，并调用基础平台完成哈希复核、扫描、底层文件存储及 FileVersion 记录保存。
10. 通过 platform API 提交回调事实并发布结果事件。
11. 实现统一同源入口和受控代理。
12. 实现首个业务消费者及独立业务判定。
13. 完成自动化、端到端、追溯矩阵和部署验收证据。

## 18. 完成标准

仅当以下条件全部满足时可声明完成：

- 来源 SHA、同步文件和差异可复现，无本地数据、依赖目录、产物和秘密混入。
- 两套工程独立构建并通过质量门禁。
- NPDMS 拥有凭证、授权、批次、设备任务、下发、回调、结果和消费确认闭环。
- 临时秘密和平台凭证遵守正式安全边界。
- 批量部分成功、重复、乱序、超时、撤销、未知状态、引用越界和哈希冲突均有自动化证据。
- 统一入口满足 SSO、同源、CSP、日志和上下文防篡改要求。
- 业务消费者不会把技术成功直接解释为业务通过。
- 模块边界、数据库 Owner、文件 Owner 和独立部署边界未被破坏。
