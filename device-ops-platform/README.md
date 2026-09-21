# Device Ops Platform

Device Ops Platform 是独立的设备脚本采集执行平台。它接收调用方提交的项目、设备和完整连接快照，以一次性连接或已保存连接连接设备，执行冻结版本的脚本，并保存脱敏后的输出、解析事实和审计状态。它不是项目、设备或凭据主数据系统，也不共享 NPDP 的数据库或进程内安全上下文。

## 构建与运行

环境要求：

- JDK 25
- Node.js 20.19+
- pnpm
- PowerShell

从仓库根目录执行一键构建：

```powershell
.\device-ops-platform\build.ps1
```

该脚本安装并验证前端、执行 Maven `clean verify`，最终生成：

```text
device-ops-platform/device-ops-server/target/device-ops-server.jar
```

仅构建独立 Java 语义解析器：

```powershell
mvn -f device-ops-platform/pom.xml -pl device-ops-parser-semantic -am clean package
```

生成的 `device-ops-parser-semantic/target/device-ops-parser-semantic.jar` 直接接收
`CommandOutputBlock JSON` 并输出一个确定性的结构化 JSON；它不依赖 Spring、数据库或设备连接。
输入、Java API、CLI、规则扩展和错误码见
[Java 设备日志语义解析器](device-ops-parser-semantic/README.md)。

本地运行（验收端口 `48181`）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.1+8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:OIDC_ISSUER_URI = 'https://idp.example.com/realms/device-ops'
$env:OIDC_JWK_SET_URI = 'https://idp.example.com/realms/device-ops/protocol/openid-connect/certs'
$env:OIDC_AUTHORITY = 'https://idp.example.com/realms/device-ops'
$env:OIDC_CLIENT_ID = 'device-ops-web'
$env:OIDC_PROJECT_CLAIM = 'device_ops_projects'
$env:DEVICE_OPS_CREDENTIAL_MASTER_KEY = '<由受管秘密存储注入的 Base64 编码 32 字节稳定主密钥>'
$env:DEVICE_OPS_TELNET_ENABLED = 'true'
& "$env:JAVA_HOME\bin\java.exe" -jar .\device-ops-platform\device-ops-server\target\device-ops-server.jar `
  --server.port=48181 `
  --device-ops.security.mode=local `
  --device-ops.runtime.auth-mode=local
```

服务默认监听 `http://localhost:48081`；本地验收按上述参数监听 `http://localhost:48181`。默认数据库为 `./data/device-ops` 的 H2 文件数据库。

## OIDC 配置

| 环境变量 | 用途 |
|---|---|
| `OIDC_ISSUER_URI` | Resource Server 校验 JWT 的 issuer |
| `OIDC_JWK_SET_URI` | JWT 签名公钥地址 |
| `OIDC_AUTHORITY` | Web Authorization Code + PKCE 的 authority |
| `OIDC_CLIENT_ID` | 公共 Web client，不能配置 client secret |
| `OIDC_SCOPE` | 前端申请的 scopes |
| `OIDC_PROJECT_CLAIM` | JWT 中允许访问的不可变 `projectKey` 集合 claim |
| `DEVICE_OPS_CREDENTIAL_MASTER_KEY` | 保存连接所需的 AES-256-GCM 稳定部署主密钥，Base64 解码后必须为 32 字节 |
| `DEVICE_OPS_TELNET_ENABLED` | Telnet 后端适配器和前端运行时开关；默认 `false`，启用后会明文传输凭据与命令 |

生产环境必须连接真实 IdP，并为 `/auth/callback` 注册准确的回调地址。仓库不提供生产密钥或测试绕过认证的 decoder。

只要启用保存连接，`DEVICE_OPS_CREDENTIAL_MASTER_KEY` 就是必需的稳定部署密钥，应仅由受管秘密存储在启动时注入；不得写入 `.env`、数据库、日志、前端或版本库。不得在每次重启时随机生成它：丢失或更换该密钥会使现有保存连接中的加密凭据无法解密。密钥轮换必须先完成受控迁移和恢复验证；未配置密钥时，仅临时连接可用。

SSH 交互式 Shell 会在打开通道后观察真实初始提示符，并把后续完成判断绑定到该提示符 identity。
`device-ops.ssh.command-prompt` 默认为空；如部署需要额外限制，可配置正则作为 identity 判断的收窄条件，
不得使用未绑定设备 identity 的宽泛 `[>#$]` 结尾规则。

## 部署方式

独立部署：

```powershell
docker compose -f .\device-ops-platform\deploy\standalone\compose.yaml up -d --build
```

两种 Compose 部署都要求从部署环境或秘密管理系统显式注入
`DEVICE_OPS_CREDENTIAL_MASTER_KEY`；NPDP 可选部署还要求显式提供容器内可达的
`OIDC_JWK_SET_URI`。具体变量和网络地址约束见各部署目录 README。

作为 NPDP 的可选独立服务启用：

```powershell
docker compose `
  -f compose.yaml `
  -f device-ops-platform/deploy/npdp-compose/compose.device-ops.yaml `
  --profile device-ops up -d device-ops
```

第二种方式只复用部署编排和网络。Device Ops 仍是独立进程、独立 OAuth2 资源边界和独立数据库；NPDP 不导入它的 Java、Vue、Flyway 或数据表。

`compose.yaml` 使用 `AUTO_SERVER=FALSE` 的 H2 单副本，仅用于本地调试，不得横向启动多个写入副本。
`compose-ha.yaml` 使用 MySQL 8.4、两个 Device Ops 节点和 HAProxy，是解析任务多实例部署基线。

## 版本化日志解析闭环

采集完成后，平台把命令块固化为不可变 payload，并在任务提交时固定解析发布版本，后续不会查询
“当前版本”：

```text
collection -> immutable payload -> fixed release -> worker -> SemanticParseResult
           -> ResultEnvelope -> RESULT_READY -> integration consumer
```

- Java API：`ParserPlanCompiler` 编译发布包，`DefaultDynamicSemanticParser` 执行不可变计划；
- CLI：`device-ops-parser-semantic.jar --release-directory ... --input ... --output ...`；
- 控制 API：`/api/v1/parser-log-types`、`/api/v1/parser-releases`、`/api/v1/parse-tasks` 和
  `/api/v1/parse-results` 管理日志类型、发布、任务和结果；
- 运维 API：`GET /api/v1/parser-runtime/status` 返回工作节点能力、线程池占用、最近心跳和 WAITING
  原因统计，需要 `parser:release:read` scope；
- 指标：Actuator `/actuator/metrics` 暴露 `device_ops_parser_*` 低基数指标，不使用任务 ID、设备 IP
  或原始日志作为标签；
- 结果投递：预配置 consumer 的失败投递只重试同一 `resultId` 的 outbox 事件，不重新执行解析。

采集工作台不要求用户预先选择日志类型。提交任意命令或脚本时，平台按以下顺序固定解析版本：

1. 请求显式绑定的已发布版本（外部下发脚本也可在 `semanticParsing.releaseId` 中绑定）；
2. `device-ops.parser.default-log-type` 对应的当前活动版本；
3. 没有可用版本时仅保留原始命令块，不阻断采集。

仓库内置的 `parser-releases/device-command-output-*` 是通用命令输出示例包。它演示已知命令规则、
任意命令组合和未知命令 `UNPARSED` 共存；新增设备日志解析能力应发布独立版本包，而不是在连接器中写死
厂商判断。相同 payload 与固定 release 会产生可复现的结构化结果。

`device-command-output-1.1.0` 将发布制品拆为 `rules/base.json`、型号族规则文件、
`model-profiles.json` 和统一投影。解析先从日志提取型号，日志无型号时使用采集发起方提供的
`contextSnapshot.deviceModel`，仍无法识别时回退 `generic`。精确值和前缀只写在
`model-profiles.json`；增加新型号族时新增一个规则文件和 profile 映射即可，无需修改 Java 引擎。

`device-command-output-1.2.0` 支持从聚合诊断输出中展开一级内嵌命令块。规则在
`DELIMITED_SECTION` 提取器上显式声明 `emitNestedUnits`，并使用命名分组提供子命令文本：

```json
{
  "type": "DELIMITED_SECTION",
  "startPattern": "^\\s*\\*{8,}\\s*(?<command>.+?)\\s*\\*{8,}\\s*$",
  "headerGroup": "command",
  "emitNestedUnits": true
}
```

引擎最多展开一层，内嵌块不会再次递归；无法识别的子块保持 `UNPARSED`，原始分段仍可在
`technicalDiagnostics.sections` 中查看。语义冲突始终优先采用顶层命令的直接证据，再考虑内嵌证据；
多值事实也按直接证据、内嵌深度和源位置稳定排序。字段证据记录父命令、子命令、分段序号和父输出中的
绝对行号，使聚合日志结果可定位、可解释并可重放。

`device-command-output-1.3.0` 将输出 schema 升至 `1.1.0`，在原有领域 `snapshot/projections` 之外新增
`genericContent`。它对所有顶层命令及有效一级内嵌命令按原顺序解析普通/缩进键值、对齐/管道表格、
重复记录、配置段、列表和文本；无法可靠识别的内容保留为文本，空内容和资源限制有独立状态。发布规则
可用 `AUTO`、`FORCE` 或 `TEXT` 控制结构识别，结构结果始终来自统一脱敏证据。工作台仅对包含该字段的
新结果显示“通用结构”页签；历史 1.2 及更早结果不重算、不补字段，也不会被当前活动版本覆盖。

兼容解析产生的 `parsedFacts/parseWarnings` 会原样保留在采集记录中，但不参与型号选择、角色识别和
语义事实冲突。`show version`、配置类输出和综合诊断输出只是当前发布包的示例规则，调用方仍可下发
任意命令组合并通过新发布版本扩展。`profileSelection` 记录最终 profile、型号来源及日志证据位置，
使历史结果可以解释和重放。

本地 H2 启动使用默认 profile。MySQL 高可用启动：

```powershell
docker compose -f .\device-ops-platform\deploy\standalone\compose-ha.yaml up -d --build
```

两个应用节点共享数据库、凭据主密钥、OIDC 和结果接收配置，但仍与 NPDP 保持独立数据库和安全边界。

## 外部 API 接入

### 设备采集（连接设备并执行命令）

完整参数、鉴权 claims、临时/保存连接、批量请求和 PowerShell 示例见
[外部设备采集 API](docs/external-collection-api.md)。

- 单设备：`POST /api/v1/collections`；项目批量：`POST /api/v1/projects/{projectKey}/collections`。
- 状态与输出：`GET /api/v1/collections/{collectionId}?namespace=...`；实时输出追加 `/output-events`；输入与请求证据追加 `/evidence`。
- 专用 OpenAPI：`/v3/api-docs/external-collection`；Swagger 选择 `external-collection`。
- 提交需要 `device-ops:collections:execute` 和 `Idempotency-Key`；读取需要 `device-ops:collections:read`。
- JWT 必须授权请求 namespace（`device_ops_namespaces` 数组或 `client_namespace`，都没有时仅允许 sub 同名 namespace）；涉及项目时还需项目 claim。
- 同请求重试不会重复执行设备命令；不同请求重用 key 返回 409。新增 V18 迁移保存无秘密指纹，保留既有采集数据。

### 离线日志解析

外部服务通过 OAuth2 Bearer token 调用现有解析 API：提交日志、查询任务状态、获取结构化结果。
完整的 client credentials、请求/响应、幂等重试和 PowerShell/curl 示例见
[外部系统调用解析 API](docs/external-api.md)。

- 专用 OpenAPI：`/v3/api-docs/external-parser`；Swagger 选择 `external-parser` 分组。
- 提交：`POST /api/v1/parse-tasks`，需要 `parser:task:create` 和 `Idempotency-Key`。
- 查询：`GET /api/v1/parse-tasks/{taskId}`；结果：`GET /api/v1/parse-tasks/{taskId}/result`，需要 `parser:task:read`。
- namespace 从受信 JWT 派生；同请求重试返回原任务，跨 namespace 的任务、结果和输入引用不可访问。
- 原始输入默认限制 8 MiB（`DEVICE_OPS_PARSER_MAX_INPUT_BYTES`）；外部部署必须保持 OAuth2 模式，不可使用 local 调试绕过。

## 平台管理

管理页面、只读查询 API 和操作边界见 [平台管理功能说明](docs/platform-management.md)。
任务与记录查询按 namespace/project 授权过滤，脚本内容仅对可证明授权的版本开放；
停止查看不取消设备执行，SCHEDULE_DUE 调度也不等于无人值守设备采集。
解析发布和激活分离，管理操作不会改写历史结果。

## 入口与接口

| 入口 | 说明 |
|---|---|
| `/` | 独立 Web 应用 |
| `/projects/{projectKey}` | 项目采集工作台 |
| `/embed/projects/{projectKey}` | 可选 iframe 深链 |
| `/auth/callback` | OIDC 回调 |
| `/api/v1/runtime-config` | 前端运行时 OIDC/API 配置 |
| `/api/v1/master-data/projects` | 外部主数据项目查询适配入口 |
| `/api/v1/master-data/projects/{projectKey}/devices` | 外部主数据设备查询适配入口 |
| `/api/v1/projects/{projectKey}/collections` | 提交采集 |
| `/api/v1/projects/{projectKey}/collections/{collectionId}` | 查询采集状态与输出 |
| `GET /api/v1/collections/{collectionId}/evidence`、`GET /api/v1/projects/{projectKey}/collections/{collectionId}/evidence` | 读取采集输入与请求证据 |
| `GET /api/v1/saved-connections`、`GET /api/v1/saved-connections/{id}` | 列出或读取当前用户和命名空间内的保存连接 |
| `POST /api/v1/saved-connections/verify-and-create` | 验证成功后创建完整保存连接 |
| `PUT /api/v1/saved-connections/{id}/verify-and-replace` | 验证成功后替换完整保存连接；失败保留原连接 |
| `PATCH /api/v1/saved-connections/{id}`、`DELETE /api/v1/saved-connections/{id}` | 重命名或删除保存连接 |
| `/api/v1/credentials` | 过渡兼容的用户管理凭据元数据 API，不是完整保存连接主入口；保存连接内部凭据对其不可见，执行请求也不再接受 `credentialId` |
| `/api/v1/projects/{projectKey}/schedules` | 可选巡检计划 |
| `/api/v1/parser-log-types`、`/api/v1/parser-releases` | 版本化解析器控制面 |
| `GET /api/v1/parser-options` | 工作台可用解析版本与默认自动解析状态 |
| `/api/v1/parse-tasks`、`/api/v1/parse-results/{resultId}` | 解析任务提交、状态与结构化结果 |
| `GET /api/v1/collections/{collectionId}/semantic-results` | 按采集任务读取各目标的固定版本解析结果 |
| `/api/v1/parser-runtime/status` | 解析工作节点与等待原因，不含 payload 或凭据 |
| `/v3/api-docs`、`/swagger-ui/index.html` | OpenAPI |
| `/actuator/health/liveness`、`/actuator/health/readiness`、`/actuator/metrics` | 运行状态与指标 |

受保护 API 同时校验 OAuth2 scope 和项目 claim。网关必须透传 `Authorization`、`Idempotency-Key`、请求体和响应状态，且不得记录包含一次性密码、私钥或私钥口令的采集请求。

## 数据与凭据边界

- Device Ops 只持久化提交时冻结的项目/设备/端点快照、脚本身份、执行证据和用户主动保存的完整连接；不创建项目或设备主档。
- 临时密码、私钥和私钥口令只存在于页面及单次执行的内存上下文，使用后清零。
- 保存连接包含非敏感连接配置及 AES-256-GCM 加密的认证材料，按 OAuth2 subject 和 namespace 隔离；执行请求只能引用完整的 `savedConnectionId`，不能拼接独立凭据选择器或直接敏感字段。
- 新建或替换保存连接必须先完成连通性验证；验证失败不得写入或覆盖原保存连接及其加密认证材料。读取保存连接时 API 只返回非敏感元数据和配置，永不回传秘密。
- 未配置 `DEVICE_OPS_CREDENTIAL_MASTER_KEY` 时服务仍可使用临时完整连接，但保存连接功能不可用。
- stdout、stderr 和解析事实持久化前执行凭据脱敏，并受输出上限保护。

## 模拟测试边界

仓库中的 SSH 测试使用进程内 Apache MINA SSH server，仅验证确定性的协议、超时、输出、指纹和凭据处理行为；它不是实际网络设备。核心 worker、H2/Flyway/JDBC 持久化与 SSH 适配器分别由聚焦测试覆盖，独立 JAR 另有运行烟测。真实设备、真实 IdP 和实际 Docker 启动仍需在目标环境验收，详见 [验收记录](docs/acceptance.md)。

浏览器自动化使用 Playwright Chromium，或通过环境变量指定已经安装的兼容浏览器；不再硬编码单台机器路径：

```powershell
Set-Location .\device-ops-platform\device-ops-web
# 如需使用本机 Chrome，设为该机器真实存在的路径：
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
pnpm.cmd test:e2e
```

未设置可执行路径时需已有 Playwright 管理的 Chromium。自动化用例以 route mock 替代真实
OIDC、后端和设备；管理功能的隔离后端浏览器验证与边界见
[平台管理验收记录](docs/platform-management-acceptance.md)。

构建完成后，可用 `powershell -File tools/start-management-acceptance.ps1` 启动仅监听
`127.0.0.1:48182` 的临时管理验收服务。它使用全新 H2 内存数据库、local 调试身份和合成终态记录，
不连接真实设备；其 V999 fixture 迁移仅用于该隔离进程，绝不能加入生产 Flyway 配置。
`-PrepareOnly` 仅生成 `target/` 下的样例，不启动服务。
