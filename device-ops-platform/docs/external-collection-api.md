# 外部设备采集 API

本文描述**连接设备、执行命令/脚本并读取采集输出**的接口，不是上传离线日志的解析接口。复用平台现有异步采集引擎，支持单设备直接采集、项目内批量采集、实时输出和后续语义解析结果。

默认端口 `48081`；生产通过 HTTPS 网关访问。请求/响应使用 JSON，输出流使用 SSE。

## 1. 接口清单

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/v1/collections` | 单设备采集，项目/设备上下文可选 |
| GET | `/api/v1/collections/{collectionId}?namespace=...` | 查询状态、目标输出和命令块 |
| GET | `/api/v1/collections/{collectionId}/output-events?namespace=...&after=0` | SSE 实时/历史输出 |
| POST | `/api/v1/projects/{projectKey}/collections` | 项目内批量设备采集 |
| GET | `/api/v1/projects/{projectKey}/collections/{collectionId}?namespace=...` | 查询批量状态和输出 |
| GET | `/api/v1/projects/{projectKey}/collections/{collectionId}/output-events?namespace=...&after=0` | 批量采集 SSE 输出 |
| GET | `/api/v1/collections/{collectionId}/semantic-results?namespace=...` | 各目标的异步语义解析状态和结果 |
| GET | `/api/v1/collections/{collectionId}/evidence?namespace=...` | 读取采集输入与请求证据 |
| GET | `/api/v1/projects/{projectKey}/collections/{collectionId}/evidence?namespace=...` | 读取项目采集证据（校验路径 projectKey） |

Swagger：`/swagger-ui/index.html`，选择 **external-collection** 分组。OpenAPI JSON：`/v3/api-docs/external-collection`。

仅需解析已有日志时，使用另一个[离线日志解析 API](external-api.md)，不要把离线日志放到采集脚本字段。

## 2. OAuth2 与调用方授权

外部服务在受信 IdP 注册 confidential client，使用 `client_credentials` 获取 access token。服务配置真实 `OIDC_ISSUER_URI` 和 `OIDC_JWK_SET_URI`，验证 token 签名、issuer、有效期。不要使用 `DEVICE_OPS_AUTH_MODE=local` 对外提供服务，也不要为了调用 API 放松生产设备地址策略。

所有采集请求带：

```http
Authorization: Bearer <access_token>
```

- 提交 scope：`device-ops:collections:execute`。
- 查询、SSE、语义结果 scope：`device-ops:collections:read`。
- `sub` 必须非空，标识调用者；保存连接始终按 subject + namespace 隔离。
- namespace 必填，最长 100 字符，并受 JWT 授权控制。

推荐服务端 token claims：

```json
{
  "sub": "service-device-collector",
  "scope": "device-ops:collections:execute device-ops:collections:read",
  "client_namespace": "partner-a",
  "device_ops_projects": ["project-001"]
}
```

namespace 权限的优先级：

1. 存在 `device_ops_namespaces` claim 时，必须是数组且包含请求 namespace；只有显式数组元素 `"*"` 允许全部 namespace。
2. 不存在上述 claim 时，要求 namespace 等于字符串 `client_namespace`。
3. 两者都缺失时，只允许 namespace 与 `sub` 完全相同。

空数组、错误类型、空身份都不默认放行。字符比较区分大小写；部署应给各业务系统分配不同的稳定 namespace，避免数据库排序规则下仅大小写不同的命名。

带项目的提交还需要 `device_ops_projects` 数组包含 projectKey；管理员显式 `"*"` 可授权全部项目。**项目授权不能代替 namespace 授权。** 通用状态/输出/语义结果接口同样校验记录中保存的 projectKey，不是绕过项目权限的入口。

### 既有 Web 客户端升级注意

接口路径、JSON 结构保持兼容，但生产认证现在会验证 namespace 授权。已有 Web 客户端通常使用项目主数据的 namespace，直接采集使用 `standalone`。升级前应让 IdP 给它签发对应 `device_ops_namespaces`，例如 `["standalone", "npdp"]`；不能依赖缺少 claim 时访问任意 namespace 的旧行为。local 调试身份显式拥有 namespace 通配授权，仅用于本机调试。

## 3. 单设备提交

```http
POST /api/v1/collections
Content-Type: application/json
Authorization: Bearer <access_token>
Idempotency-Key: external-job-0001
```

请求示例（`sha256` 必须按真实 content 计算，以下仅展示字段）：

```json
{
  "namespace": "partner-a",
  "connection": {
    "protocol": "SSH2",
    "host": "192.0.2.10",
    "port": 22,
    "username": "collector",
    "authenticationType": "PASSWORD",
    "executionMode": "EXEC",
    "connectTimeoutSeconds": 10,
    "hostKeyFingerprint": "SHA256:<设备受信主机公钥指纹>",
    "password": "<由秘密管理系统提供>"
  },
  "script": {
    "source": "ADHOC_INLINE",
    "key": "external-readonly",
    "version": "1",
    "content": "show version",
    "sha256": "<content UTF-8 字节的 64 位十六进制 SHA-256>",
    "policy": "EXECUTION_ONLY",
    "parserType": "NONE"
  },
  "commandTimeoutSeconds": 30,
  "parseTimeoutSeconds": 5,
  "leaseGraceSeconds": 10,
  "semanticParsing": { "enabled": false }
}
```

收到 **202** 表示任务已受理，不表示设备命令执行成功：

```json
{"collectionId":"实际返回的任务 ID","existing":false}
```

完全相同请求的重试返回同一 collectionId，`existing:true`。此响应不带 statusUrl，按接口清单自行构造查询 URL。

### 连接选择

**临时连接：** 明确提供协议、host、port、username、authenticationType、executionMode、connectTimeoutSeconds 和对应认证材料。密码认证只提供 `password`；私钥认证用 `authenticationType:"PRIVATE_KEY"`、`privateKey`，可选 `passphrase`，不得混入 password。首次提交临时连接必须有认证材料，后续已受理请求重放可以省略认证材料。

**已保存连接：** 用下列对象替代整个 connection：

```json
{"savedConnectionId":"已保存连接 ID","credentialNamespace":"partner-a"}
```

credentialNamespace 可省略，默认请求 namespace，显式值必须与其相同。不允许同时传直接连接字段或 `credentialId`；只能使用当前 JWT subject 的保存连接。成功提交后，相同 savedConnectionId 请求重放不会再加载该保存连接，即使保存连接已被删除或修改，也只是返回原任务，不重新执行。

SSH2 单设备接口支持 `EXEC` 与 `SHELL`，设备应支持所选通道。外部调用应始终传受信 hostKeyFingerprint；缺省指纹会失去服务器身份校验，不能作为生产示例实践。Telnet 必须显式部署启用、使用 `SHELL`/密码认证，并提供 `telnetPrompts:{login,password,command,lineEnding}`；它会明文传输秘密，不推荐外部系统使用。

### 脚本与上下文

- source：`ADHOC_INLINE`、`EXTERNAL_DELIVERED` 或 `LOCAL_MANAGED`。它们都需要提交 content 与匹配的 sha256，不是只传服务端脚本 ID。
- policy：`EXECUTION_ONLY` 或 `REGISTER_VERSION`；ADHOC_INLINE 固定为 EXECUTION_ONLY。REGISTER_VERSION 相同 key/version 内容不可改变。
- parserType：`NONE`、`JSON`、`KEY_VALUE` 等已注册传统输出解析器，与 `semanticParsing` 的发布版语义解析不同。
- key、version、content 和其他非秘密参数发生变化时，应使用新的 Idempotency-Key。
- context 可选：`{project:{namespace,projectKey,projectName?,projectCode?},device:{deviceKey,deviceName?,vendor?,model?},extensions:{}}`。context.project.namespace 必须与请求 namespace 相同，扩展值为字符串。
- externalRequestId、activityType 可选。externalRequestId 在 namespace 内唯一；不同 key 重用同一 externalRequestId 返回冲突。
- callbackUrl 默认不需要。启用回调时，所有目标必须有项目和设备快照，并满足部署的 callback 开关与目标允许列表；不能假设任意 URL 都会被投递。

## 4. 可直接运行的 PowerShell 单设备示例

环境变量通过部署/秘密管理系统注入：`DEVICE_OPS_TOKEN_URL`（受信 HTTPS IdP token 地址）、`DEVICE_OPS_CLIENT_ID`、`DEVICE_OPS_CLIENT_SECRET`、`DEVICE_PASSWORD`、`DEVICE_HOST_KEY_FINGERPRINT`。不要打印这些秘密或记录请求体。

```powershell
$ErrorActionPreference = 'Stop'
$base = 'https://device-ops.example.com'
$namespace = 'partner-a'
$token = Invoke-RestMethod -Method Post -Uri $env:DEVICE_OPS_TOKEN_URL `
  -ContentType 'application/x-www-form-urlencoded' -Body @{
    grant_type = 'client_credentials'
    client_id = $env:DEVICE_OPS_CLIENT_ID
    client_secret = $env:DEVICE_OPS_CLIENT_SECRET
    scope = 'device-ops:collections:execute device-ops:collections:read'
  }
$headers = @{ Authorization = "Bearer $($token.access_token)" }

$content = 'show version'
$sha = [System.Security.Cryptography.SHA256]::Create()
try {
  $hash = [System.BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($content))).Replace('-', '').ToLowerInvariant()
} finally { $sha.Dispose() }
$request = @{
  namespace = $namespace
  connection = @{
    protocol = 'SSH2'
    host = '192.0.2.10' # 改为已授权设备地址
    port = 22
    username = 'collector'
    authenticationType = 'PASSWORD'
    executionMode = 'EXEC'
    connectTimeoutSeconds = 10
    hostKeyFingerprint = $env:DEVICE_HOST_KEY_FINGERPRINT
    password = $env:DEVICE_PASSWORD
  }
  script = @{
    source = 'ADHOC_INLINE'
    key = 'external-readonly'
    version = '1'
    content = $content
    sha256 = $hash
    policy = 'EXECUTION_ONLY'
    parserType = 'NONE'
  }
  commandTimeoutSeconds = 30
  parseTimeoutSeconds = 5
  leaseGraceSeconds = 10
  semanticParsing = @{ enabled = $false }
}
$body = $request | ConvertTo-Json -Depth 30 -Compress
$key = [guid]::NewGuid().ToString() # 保存到调用方业务任务，网络重试复用此 key
$submitHeaders = @{ Authorization = $headers.Authorization; 'Idempotency-Key' = $key }
$submission = Invoke-RestMethod -Method Post -Uri "$base/api/v1/collections" `
  -Headers $submitHeaders -ContentType 'application/json; charset=utf-8' `
  -Body ([Text.Encoding]::UTF8.GetBytes($body))
$id = $submission.collectionId
$ns = [Uri]::EscapeDataString($namespace)
$deadline = [DateTime]::UtcNow.AddMinutes(3)
$terminal = @('SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'TIMED_OUT', 'CANCELLED')
do {
  $task = Invoke-RestMethod -Uri "$base/api/v1/collections/${id}?namespace=$ns" -Headers $headers
  if ($task.status -in $terminal) { break }
  if ([DateTime]::UtcNow -gt $deadline) { throw "采集轮询超时：$id" }
  Start-Sleep -Seconds 1
} while ($true)

$task.targets | Select-Object targetId,status,stdout,stderr,exitCode,truncated
if ($task.status -ne 'SUCCEEDED') {
  throw "采集未全部成功：$($task.status)，请检查各目标 outcome 和命令块"
}
```

本示例的 `192.0.2.10` 是文档地址，必须替换成真实且有权限的设备；不会在文档中提供生产凭据。

## 5. 项目批量提交

`POST /api/v1/projects/project-001/collections` 使用顶层 project 和 targets 数组。**targets 中连接字段是平铺结构，不是 connection 子对象。** project.namespace 与 request.namespace 一致；顶层和每个目标 projectKey 必须匹配路径。

可从上面的 PowerShell 已构造 `$request` 生成批量请求：

```powershell
$project = @{ namespace = $namespace; projectKey = 'project-001'; projectName = '示例项目' }
$targets = @()
foreach ($device in @(
  @{ key = 'device-01'; host = '192.0.2.10' },
  @{ key = 'device-02'; host = '192.0.2.11' }
)) {
  $target = @{
    project = $project
    device = @{ deviceKey = $device.key; deviceName = $device.key }
    extensions = @{}
  }
  foreach ($name in $request.connection.Keys) { $target[$name] = $request.connection[$name] }
  $target.host = $device.host
  # 每台设备应填入自己的受信主机指纹和认证材料，不要错误复用示例中的值。
  $targets += $target
}
$batch = @{
  namespace = $namespace
  project = $project
  targets = $targets
  script = $request.script
  commandTimeoutSeconds = 30
  parseTimeoutSeconds = 5
  leaseGraceSeconds = 10
  semanticParsing = @{ enabled = $false }
}
# 只有为各目标填入正确指纹/凭据后才提交；这里故意不自动发送示例批量任务。
$batchBody = $batch | ConvertTo-Json -Depth 30 -Compress
```

使用新的 Idempotency-Key 提交 batchBody；状态路径为 `/api/v1/projects/project-001/collections/{collectionId}?namespace=partner-a`。批量 SSH2 只支持 EXEC；Telnet 只支持 SHELL。外部系统只能向自己被授权的项目和设备下发命令。

## 6. 输出、结果和 SSE

状态响应包含：

```text
collectionId, namespace, projectKey, externalRequestId, activityType, status,
script:{source,key,version,sha256,parserType},
targets:[{targetId,contextSnapshot,endpointSnapshot,status,stdout,stderr,
          exitCode,truncated,parsedFacts,outcome,commandBlocks}]
```

状态可能为 `QUEUED`、`CONNECTING`、`EXECUTING`、`PARSING` 和终态 `SUCCEEDED`、`PARTIAL_SUCCESS`、`FAILED`、`TIMED_OUT`、`CANCELLED`。不要仅凭 HTTP 200/202 判断执行成功；读取整体状态、各目标状态、outcome、退出码、截断标志和命令块。

curl 输出流示例（在 Bash 中）：

```bash
curl --fail-with-body --no-buffer \
  "$BASE/api/v1/collections/$COLLECTION_ID/output-events?namespace=$NAMESPACE&after=0" \
  -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream'
```

namespace 包含特殊字符时先 URL 编码。

- `event: output`：id 是 sequence；data 包含 targetId、commandIndex、stream、content、receivedBytes、pageCount、truncated、createdAt。
- `event: heartbeat`：data 包含 lastSequence。
- `event: complete`：data 包含整体采集 status 和 lastSequence。
- 连接最多持续约 30 秒。断线/超时后，将最后收到的 sequence 放入 `after` 参数重连，可重放持久化输出；当前不读取 `Last-Event-ID` header。
- SSE 客户端断开不取消采集。仅订阅状态时不需要一直保持 SSE。

### 语义解析

`semanticParsing:{enabled:false}` 禁用本次语义解析；省略整个 semanticParsing 时遵循平台默认自动解析策略。显式启用可传：

```json
{
  "enabled": true,
  "logType": "device-command-output",
  "inputFormat": "command-output-block/v1",
  "releaseId": "管理员已注册并发布的版本 ID"
}
```

提交时固定版本；省略 releaseId 时使用已激活版本。**采集终态或 SSE complete 不代表语义解析已完成。** 通过 `/api/v1/collections/{id}/semantic-results?namespace=...` 另行查询，数组按目标返回 taskId、state、waitReason、releaseId、coordinate、resultId、result。未创建解析任务时可为空；成功结果 envelope 的 `semanticResult` 包含对应发布版结构化数据。管理员须预先导入/发布/激活解析器，磁盘有发布目录不等于已注册。

### 采集证据

`GET /api/v1/collections/{collectionId}/evidence?namespace=...`（项目采集用 `/api/v1/projects/{projectKey}/collections/{collectionId}/evidence?namespace=...`，同时校验记录中的 projectKey）返回任务级证据，权限与状态查询相同：`device-ops:collections:read` 加 namespace/project 授权。响应带 `Cache-Control: no-store`，按四组返回：

- `metadata`：collectionId、namespace、projectKey、externalRequestId、activityType、createdAt。
- `input`：任务自身冻结的 source/key/version/policy/parserType/sha256、contentStatus 和脚本正文 `content`；从任务冻结内容读取，不读取当前脚本版本替代历史。
- `submission`：provenance、snapshot、omittedFields。新任务 provenance 为 `CAPTURED_SUBMISSION`，snapshot 是不含连接密码、私钥、口令和脚本正文的白名单投影，省略字段列在 omittedFields；历史任务为 `RECONSTRUCTED_FACTS`，snapshot 为 null，只返回已保存的脚本身份、目标端点等执行事实。
- `executionFacts`：目标端点快照与状态，以及冻结的 semanticParsing 坐标。

脚本正文、命令文本和请求快照属于敏感运维证据，与状态接口的命令块同等对待：仅授权调用者可读，不要写入日志、URL 或浏览器存储。证据不包含也不解密保存凭据。内容状态区分 AVAILABLE 与 UNAVAILABLE，空字符串不自动等同于未取得。

## 7. 幂等、错误与部署边界

Idempotency-Key 必填、非空、最长 200 字符。按 namespace + key 去重，并验证**同一 subject 和无秘密请求内容**：目标顺序、上下文、连接选择及非秘密参数、脚本、超时、callback、externalRequestId、语义解析选择等必须一致。对象键顺序不影响指纹，省略字段与显式 null 按反序列化模型处理；不要在重试中改写其他字段。

- 相同请求返回原任务，不再派发设备执行；不能通过重复 key 重启失败/中断任务。
- 密码、私钥、口令不入指纹、不作为持久化身份；已受理任务重放可以不带这些材料。改密码不会让原任务重新执行。
- 其他 subject 即使拥有相同 namespace，重用同 key 也返回 409，不泄露原任务 ID；有读权限的同 namespace 调用者仍按项目授权共享结果。
- 重用 key 改变请求返回 409 `IDEMPOTENCY_CONFLICT`；新业务任务必须使用新 key。
- 历史 V18 之前的记录没有请求指纹，新 HTTP 请求重用那些 key 返回 409；历史状态/输出仍可查询，不会因升级自动重跑。
- 队列满时返回 429 `QUEUE_FULL`，但任务可能已经保存、部分目标已派发。必须用原 key 查询/重试，不能换 key 盲目重发。

采集控制器错误使用 `{code,message,traceId}`；认证过滤器的 401/403 不保证返回此 JSON。

| 状态 | 含义 |
|---|---|
| 400 | `INVALID_REQUEST`：参数、连接组合、脚本哈希、上下文或解析选择不合法 |
| 401 | 缺少、无效或过期 JWT |
| 403 | scope、namespace、subject 或项目授权不足 |
| 404 | `RESOURCE_NOT_FOUND`：采集记录或当前 subject 的保存连接不存在 |
| 409 | `IDEMPOTENCY_CONFLICT` 或 `SCRIPT_VERSION_CONFLICT` |
| 429 | `QUEUE_FULL`：检查已有任务，使用原 key 重试 |

V18 迁移在服务启动时由 Flyway 应用（H2/MySQL 各自迁移目录），新增请求指纹列，不更改旧迁移、不回填猜测身份、不删除历史数据。

生产网关应设置总请求大小、批量规模/并发、速率与请求超时限制；设备访问应有专门 egress allowlist，不能把 API 当成任意网络代理。只给可信客户端执行 scope，并使用设备端最小权限账号和只读命令。现有远端策略拒绝 loopback/link-local/multicast 等目标；测试只在测试配置中允许隔离 SSH fixture，不放松生产策略。

不记录 Bearer token、client secret、密码/私钥/口令或原始请求体。采集证据、命令文本、脚本内容及日志可能被持久化并包含敏感业务信息；EXECUTION_ONLY 不表示内容完全不落库。临时连接凭据丢失的中断任务不会自动复活执行，需要调用方明确创建新业务任务。
