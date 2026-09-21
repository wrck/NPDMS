# 外部系统调用解析 API

本页面向通过 HTTP 调用 Device Ops 的服务端应用。复用现有异步解析引擎，不依赖前端会话或 NPDP 进程。API 默认端口为 `48081`；生产请通过 HTTPS 网关访问。

## 1. 接口与认证

| 方法 | 路径 | 用途 | 必需 scope |
|---|---|---|---|
| POST | `/api/v1/parse-tasks` | 提交解析任务，返回 202 与任务 ID | `parser:task:create` |
| GET | `/api/v1/parse-tasks/{taskId}` | 查询状态、固定发布版本与 resultId | `parser:task:read` |
| GET | `/api/v1/parse-tasks/{taskId}/result` | 直接按任务 ID 获取结构化结果 | `parser:task:read` |
| GET | `/api/v1/parse-results/{resultId}` | 按结果 ID 获取同一结果 envelope | `parser:task:read` |
| GET | `/api/v1/parse-tasks?limit=50&afterTaskId=...` | 当前调用方的任务列表，limit 为 1–200 | `parser:task:read` |

OpenAPI JSON：`/v3/api-docs/external-parser`。Swagger：`/swagger-ui/index.html`，选择 `external-parser` 分组，点击 **Authorize** 填写 access token（不用再加 `Bearer ` 前缀）。默认 `/v3/api-docs` 仍包含平台完整 API。文档端点公开，但实际业务请求必须认证；专用分组不是路由隔离机制。

### 服务间 OAuth2

1. 在实际 IdP 注册 **confidential client**，启用 `client_credentials`，只授予所需的两个任务 scopes。不授予 `parser:release:write` 或设备执行权限。
2. 让 IdP 将稳定且唯一的 `client_namespace` claim 放入 access token。它是数据隔离边界，不能由调用方任意指定。未配置此 claim 时回退到 `sub`；两者都缺失、为空或最终 namespace 超过 200 字符时拒绝访问。
3. Device Ops 配置 `OIDC_ISSUER_URI` 和 `OIDC_JWK_SET_URI`，验证签名、issuer 和 token 有效期。scope 决定操作权限，namespace 决定任务/结果/输入的可见范围。
4. 请求头为 `Authorization: Bearer <access_token>`。不使用浏览器登录 cookie；服务间 HTTP 不需要开启 CORS。

建议每个外部系统使用独立 namespace。同一 namespace 下的服务身份共享解析任务与输入访问权限；不要把不互信的客户端映射到同一 namespace。

**不得为了外部调用启用 `local` 模式。** `DEVICE_OPS_AUTH_MODE=local` 是本地全权限调试绕过。外部部署保持 `DEVICE_OPS_AUTH_MODE=oauth2`，只向外部网关开放所需 API；网关还应设置请求体上限、速率限制、网络访问策略和 IdP 所需的 audience 策略。本文不是全平台所有管理/采集路由的安全验收。

## 2. 准备解析发布

提交任务前，管理员必须创建日志类型，导入、验证、发布解析版本；省略 `releaseId` 时还必须激活该日志类型的版本。**仅把 `parser-releases` 文件夹放在磁盘上不会自动注册或激活。**

仓库示例发布目录：`parser-releases/device-command-output-1.3.0`。它的日志类型为 `device-command-output`，输入适配器为 `command-output-block/v1`，输出 schema 为 `1.1.0`。发布/验证/激活由已有解析器管理功能或 `/api/v1/parser-log-types`、`/api/v1/parser-releases` 管理 API 完成，要求独立管理员权限。`releaseId` 是注册发布时返回的 ID，不保证等于目录名称或版本号。

调用方可以显式传入已发布的 `releaseId`；否则平台选择当前活动版本并固定到任务上。后续活动版本变化不会改变历史任务。

## 3. 请求和响应

请求头：

```http
POST /api/v1/parse-tasks HTTP/1.1
Authorization: Bearer <access_token>
Content-Type: application/json
Idempotency-Key: external-job-20260908-0001
```

请求体示例：

```json
{
  "logType": "device-command-output",
  "inputFormat": "command-output-block/v1",
  "mediaType": "application/json",
  "inputContent": "{\"schemaVersion\":\"1.0.0\",\"commandBlocks\":[{\"commandIndex\":1,\"commandText\":\"show version\",\"status\":\"SUCCEEDED\",\"stdout\":\"Serial Number: SN-001\\nSoftware Release TEST-1.2.3\",\"stderr\":\"\",\"receivedBytes\":49,\"pageCount\":1,\"truncated\":false,\"exitCode\":0}]}",
  "contextSnapshot": {
    "externalJobId": "external-job-20260908-0001",
    "deviceModel": "generic"
  }
}
```

`inputContent` 是 **JSON 字符串**，不是嵌套 JSON 对象，也不是任意裸日志。构造字符串时使用 JSON 序列化器，不要手工拼接转义。`stdout` 可以是任意命令的原始输出；已知规则提取领域事实，通用结构解析保留表格、键值、文本等可识别结构。空内容拒绝提交；格式损坏等解析阶段错误通过任务状态反映。

202 响应示例（ID 以实际响应为准）：

```json
{
  "taskId": "returned-task-id",
  "state": "QUEUED",
  "releaseId": "registered-release-id",
  "coordinate": {
    "logType": "device-command-output",
    "releaseVersion": "1.3.0",
    "engineVersion": "1.3.0",
    "ruleVersion": "1.3.0",
    "projectionVersion": "1.3.0",
    "extensionId": null,
    "extensionVersion": null
  },
  "statusUrl": "/api/v1/parse-tasks/returned-task-id"
}
```

不要根据示例猜测版本坐标，始终以返回的 coordinate 为准。重试返回任务当前状态，不一定仍是 `QUEUED`。

- `logType`：非空，最多 200 字符。
- `inputFormat`：非空，最多 100 字符，必须匹配已发布适配器。
- `releaseId`：可选，最多 100 字符。
- `inputRef`：可选，最多 100 字符；与 `inputContent` 二选一。只能复用本 namespace 已拥有的 payload（例如本 namespace 任务详情中的 inputRef）。未知或其他 namespace 的引用统一返回 404 `INPUT_NOT_FOUND`。
- `mediaType`：最多 200 字符，默认 `text/plain`；JSON 示例应显式使用 `application/json`。
- `contextSnapshot`：可选 JSON 对象，省略相当于 `{}`；用于冻结设备型号、业务关联等上下文，不能用它改变认证 namespace。
- `resultConsumerId`：可选，最多 200 字符，只能使用管理员预配置的结果接收器 ID，不能提交任意回调 URL；本页示例使用轮询，不配置回调。

### 幂等重试

`Idempotency-Key` 必填、非空，最多 200 字符。以 **namespace + key** 确定一次请求，数据库唯一约束支持多实例并发。

- 同 key、同请求语义重复提交返回原任务，不创建第二次解析。
- 内容按实际 UTF-8 字节及 mediaType 判断，重新排版 inputContent JSON 也属于不同输入；contextSnapshot 的 JSON 对象键顺序不影响判断。
- 改变输入、日志类型、输入格式、contextSnapshot、consumer 或发布选择返回 409 `IDEMPOTENCY_CONFLICT`。新任务必须使用新 key。
- 自动选版本的原请求应继续省略 releaseId。即使活动发布切换或取消，原请求仍重放已固定的版本；不要在重试时从自动选择改为显式选择。
- 幂等信息保存在任务数据中，不是进程内缓存；有效期取决于任务的保留策略。

### 查询任务与结果

轮询 `GET /api/v1/parse-tasks/{taskId}`：

- `QUEUED` / `RUNNING`：继续轮询，建议 1 秒起步并退避；设置调用方超时。
- `WAITING`：检查 `waitReason`，可能需要管理员处理 worker 能力或发布制品；不要快速无上限重试。
- `SUCCEEDED`：读取 `/api/v1/parse-tasks/{taskId}/result`。
- `FAILED` / `CANCELLED`：结束本次轮询，不能当作成功结果处理。

结果接口返回既有 `ParseResultEnvelope`，包含 taskId、releaseId、coordinate、contextSnapshot 和 `semanticResult`。`semanticResult` 是解析器的结构化 JSON，包含 `genericContent`、`snapshot`、`projections`、`quality` 等字段；具体字段以发布版本/schema 为准。

未产生结果时，按任务读取结果返回 409 `RESULT_NOT_READY`（包括失败和取消的任务）；调用方必须先检查任务状态，不应对所有 409 无限重试。不属于当前 namespace 的任务和结果都返回 404，不暴露它们是否存在。

## 4. PowerShell 完整调用示例

以下示例在 PowerShell 7 中执行。令牌端点以实际 IdP 为准；不要把 client secret 写入代码、版本库或日志。`DEVICE_OPS_CLIENT_SECRET` 应由秘密管理系统注入，`DEVICE_OPS_TOKEN_URL` 必须是受信任 IdP 的 HTTPS 地址。

```powershell
$ErrorActionPreference = 'Stop'
$base = 'https://device-ops.example.com'
$tokenResponse = Invoke-RestMethod -Method Post -Uri $env:DEVICE_OPS_TOKEN_URL `
  -ContentType 'application/x-www-form-urlencoded' -Body @{
    grant_type = 'client_credentials'
    client_id = $env:DEVICE_OPS_CLIENT_ID
    client_secret = $env:DEVICE_OPS_CLIENT_SECRET
    scope = 'parser:task:create parser:task:read'
  }
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }

$stdout = "Serial Number: SN-001`nSoftware Release TEST-1.2.3"
$inputDocument = @{
  schemaVersion = '1.0.0'
  commandBlocks = @(@{
    commandIndex = 1
    commandText = 'show version'
    status = 'SUCCEEDED'
    stdout = $stdout
    stderr = ''
    receivedBytes = [System.Text.Encoding]::UTF8.GetByteCount($stdout)
    pageCount = 1
    truncated = $false
    exitCode = 0
  })
}
$body = @{
  logType = 'device-command-output'
  inputFormat = 'command-output-block/v1'
  mediaType = 'application/json'
  inputContent = ($inputDocument | ConvertTo-Json -Depth 30 -Compress)
  contextSnapshot = @{ deviceModel = 'generic' }
  # 如需显式固定：releaseId = '<管理员注册的已发布 ID>'
} | ConvertTo-Json -Depth 40 -Compress

# 一次业务请求保存此 key；网络失败重试必须复用它与同一份 body。
$key = [guid]::NewGuid().ToString()
$submitHeaders = @{ Authorization = $headers.Authorization; 'Idempotency-Key' = $key }
$submission = Invoke-RestMethod -Method Post -Uri "$base/api/v1/parse-tasks" `
  -Headers $submitHeaders -ContentType 'application/json; charset=utf-8' `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$deadline = [DateTime]::UtcNow.AddMinutes(2)
while ($true) {
  $task = Invoke-RestMethod -Uri "$base$($submission.statusUrl)" -Headers $headers
  if ($task.state -eq 'SUCCEEDED') { break }
  if ($task.state -in @('FAILED', 'CANCELLED')) {
    throw "解析未成功：taskId=$($task.taskId)，state=$($task.state)"
  }
  if ([DateTime]::UtcNow -ge $deadline) {
    throw "轮询超时：taskId=$($task.taskId)，state=$($task.state)，waitReason=$($task.waitReason)"
  }
  Start-Sleep -Seconds 1
}
$result = Invoke-RestMethod -Uri "$base/api/v1/parse-tasks/$($submission.taskId)/result" -Headers $headers
$result | ConvertTo-Json -Depth 100
```

也可以将上面的请求 JSON 保存为 UTF-8 `request.json`，通过 curl 调用（`TOKEN` 来自受信 IdP）：

```bash
curl --fail-with-body "$BASE/api/v1/parse-tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: external-job-20260908-0001' \
  -H 'Content-Type: application/json' --data-binary @request.json

curl --fail-with-body "$BASE/api/v1/parse-tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN"

curl --fail-with-body "$BASE/api/v1/parse-tasks/$TASK_ID/result" \
  -H "Authorization: Bearer $TOKEN"
```

## 5. 错误与资源边界

解析控制器错误格式为 `{ "code": "...", "message": "...", "details": {}, "traceId": "..." }`。认证过滤器的 401/403 不保证具有此 JSON 格式，应首先按 HTTP 状态处理；不能假设所有错误响应都能反序列化为业务 envelope。

| HTTP 状态 | 典型 code | 调用方处理 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 修正缺失/空/过长 header、字段、无效 JSON 或分页参数 |
| 401 | 认证层响应 | 获取有效 token，不重试无效 token |
| 403 | 认证/授权层响应 | 检查 scope 与 namespace/sub 配置 |
| 404 | `TASK_NOT_FOUND`、`RESULT_NOT_FOUND`、`INPUT_NOT_FOUND`、`ACTIVE_RELEASE_NOT_FOUND` | 检查资源归属或管理员发布配置 |
| 409 | `IDEMPOTENCY_CONFLICT`、`RESULT_NOT_READY`、`RELEASE_NOT_PUBLISHED` | 按具体 code 处理，不能统一盲重试 |
| 413 | `INPUT_TOO_LARGE` | 缩小输入或由管理员合理调整上限 |
| 422 | `INPUT_FORMAT_MISMATCH`、`RESULT_CONSUMER_NOT_FOUND` | 修正发布输入格式或接收器配置 |
| 503 | `ARTIFACT_UNAVAILABLE`、`TRANSIENT_STORAGE_ERROR` | 退避重试，复用原 key 和请求体 |

`DEVICE_OPS_PARSER_MAX_INPUT_BYTES` 默认 `8388608`（8 MiB），必须大于 0。校验的是反序列化后 inputContent 的 UTF-8 字节数，在 payload 入库前拒绝超限。它不是整个 HTTP 请求的流式上限：网关仍需限制总请求体大小（包含 JSON 转义、contextSnapshot 等），并设置并发/速率限制。

提交的原始日志会作为不可变输入持久化，不应提交口令、私钥或其他不必要的秘密；输入存储不是秘密保险库。结果解析有脱敏逻辑，但不能依赖它替代数据最小化或安全审查。网关和客户端不得记录 Bearer token、client secret 或原始日志请求体。

## 6. 验证边界

仓库 `ExternalParserHttpTest` 使用随机测试 RSA 密钥签发 JWT，经过真实 OAuth2 Resource Server 校验、真实 HTTP 服务、H2/Flyway、已发布的 1.3.0 示例与后台 worker，验证提交、重试、状态/结果、namespace/scope、请求校验和专用 OpenAPI。测试 decoder 只在测试配置中存在，不改变生产鉴权。

这不代替真实 IdP 的 client credentials 授权配置、实际网关/网络、MySQL 与部署环境验收。已有设备连接/采集 API 仍可使用，但不属于此专用解析接入分组；接口列表见项目 README。
