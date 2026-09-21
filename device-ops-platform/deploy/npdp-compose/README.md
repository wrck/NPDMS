# NPDP 可选部署集成

本目录只提供部署级松耦合。NPDP 与 Device Ops 保持独立进程、独立数据库和独立
OAuth2 资源边界；NPDP 的 Java、Vue、Flyway 和数据库表不依赖 Device Ops。

## 启用可选 Profile

以下命令必须从 NPDP 仓库根目录执行，并以仓库根目录的 `compose.yaml` 作为第一个
Compose 文件。Compose 会按第一个文件所在目录解析覆盖文件中的相对构建路径：

```powershell
docker compose `
  -f compose.yaml `
  -f device-ops-platform/deploy/npdp-compose/compose.device-ops.yaml `
  --profile device-ops up -d device-ops
```

如果第一个 Compose 文件不在 NPDP 仓库根目录，必须显式传入
`--project-directory <NPDP_REPO_ROOT>`，并确保两个 `-f` 路径均相对于调用位置正确。

Compose 网络内的服务地址为 `http://device-ops:48081`。该覆盖文件默认不发布宿主机
端口，网关应通过 Compose 网络访问服务。生产环境也可以设置
`DEVICE_OPS_IMAGE` 使用已构建的不可变镜像，而不在部署主机现场构建。

这是 H2 文件数据库的单副本配置。`AUTO_SERVER=FALSE`，数据写入
`device-ops-data` 卷；不得把该 Profile 扩展为多个服务副本。需要高可用或横向扩容
时，必须先迁移到受支持的外部数据库。

## OAuth2 / OIDC

启动前必须同时设置 `OIDC_ISSUER_URI`、`OIDC_JWK_SET_URI` 和 `OIDC_AUTHORITY`。以下配置属于
Device Ops，不与 NPDP 进程内 SecurityContext 共享：

| 配置 | 用途 |
|---|---|
| `OIDC_ISSUER_URI` | Resource Server 校验 JWT 的 issuer |
| `OIDC_JWK_SET_URI` | Resource Server 读取 JWT 签名公钥的容器内可达 URL |
| `OIDC_AUTHORITY` | Web Authorization Code + PKCE 使用的明确 authority |
| `OIDC_CLIENT_ID` | 公共 Web client，默认 `device-ops-web`，不得配置 client secret |
| `OIDC_PROJECT_CLAIM` | JWT 中允许访问的不可变 `projectKey` claim |
| `OIDC_SCOPE` | Web 申请的 Device Ops scopes |
| `DEVICE_OPS_CREDENTIAL_MASTER_KEY` | 必填，由部署秘密系统注入；Base64 解码后必须为 32 字节，不提供默认值 |
| `DEVICE_OPS_TELNET_ENABLED` | 可选，默认 `false`；同时控制后端 Telnet 适配器和前端运行时开关 |

`OIDC_JWK_SET_URI` 必须按 `device-ops` 容器看到的网络地址配置，不能填写只对部署宿主机
成立的 `localhost` URL。例如 IdP 在同一 Compose 网络中的服务名为 `<idp-service>`、容器端口为
`<idp-port>` 时，应使用 `http://<idp-service>:<idp-port>/<issuer-jwks-path>`。若 IdP 在网络外，
则使用容器可解析、可路由且与证书匹配的 HTTPS URL。

`DEVICE_OPS_CREDENTIAL_MASTER_KEY` 只能由受管秘密存储在部署时注入，不得写入 Compose、
`.env`、日志或版本库。密钥必须跨重启保持稳定，否则已有保存连接无法解密。Telnet 默认关闭；
启用后凭据和命令会以明文在网络上传输，只能用于受控网络。

OIDC client 需要登记准确的回调地址 `/auth/callback`。最小 scopes 按实际入口裁剪：

```text
openid profile
device-ops:projects:read
device-ops:devices:read
device-ops:scripts:read
device-ops:scripts:write
device-ops:collections:execute
device-ops:collections:read
```

## 网关与入口合同

API 反向代理合同为：

```text
public  /device-ops/**
upstream http://device-ops:48081/**
```

网关转发 API 时必须剥离 `/device-ops` 前缀，并透传 `Authorization`、
`Idempotency-Key`、请求体和响应状态；不得记录包含一次性密码、私钥或私钥口令的
采集请求体。

当前前端产物使用根路径部署：Vite assets、Vue Router、API 和 OIDC callback 都是
root-relative。推荐为 Device Ops 分配独立虚拟主机，在该主机根路径反代到
`http://device-ops:48081`。此时菜单或 iframe 深链为：

```text
https://device-ops.example.com/embed/projects/{projectKey}
```

如果必须从 NPDP 同一域名公开 `/device-ops/**`，仅在上游剥离前缀并不足以让浏览器
路由工作。发布前还必须用 `/device-ops/` 重新配置前端 Vite base、Vue Router
history base、API base 和 OIDC redirect URI，或者由网关完整重写 HTML、assets、
SPA fallback、API 与 callback 路径。未完成这些配置时，不应发布
`/device-ops/embed/projects/{projectKey}` 菜单。

NPDP 菜单只保存 URL/iframe 配置，不导入 Device Ops Vue 组件或 API 客户端。宿主
发起采集时显式提交项目和设备快照、连接端点及本次执行的一次性凭据；Device Ops
不回查执行上下文、不共享 NPDP 数据库，也不保存项目、设备或凭据主档。用户主动保存连接时，
Device Ops 只拥有按 OAuth2 subject 和 namespace 隔离的内部连接配置及加密认证材料。

## 移除 Profile

1. 从 NPDP 菜单、iframe 和网关中移除 Device Ops 深链与路由。
2. 使用与启动时相同的 Compose 文件集合停止并删除服务：

   ```powershell
   docker compose `
     -f compose.yaml `
     -f device-ops-platform/deploy/npdp-compose/compose.device-ops.yaml `
     --profile device-ops stop device-ops
   docker compose `
     -f compose.yaml `
     -f device-ops-platform/deploy/npdp-compose/compose.device-ops.yaml `
     --profile device-ops rm -f device-ops
   ```

3. 从部署命令中移除覆盖文件和 `device-ops` Profile。
4. 默认保留 `device-ops-data` 卷以便审计和回滚。确认已备份且不再需要采集证据后，
   再由运维人员显式删除该卷；不要使用会连带删除 NPDP 其他卷的宽泛 `down -v`。

完成上述步骤后，NPDP 应继续以原有 Compose 配置独立启动。

## 高可用边界

需要两个或更多 Device Ops 实例时，使用 `deploy/standalone/compose-ha.yaml` 或等价的外部
MySQL 部署。NPDP 与 Device Ops 始终通过 OAuth2/API 集成：两者不共享数据库、Flyway
历史或进程内事务。NPDP 只下发本次项目/设备快照，并通过配置的结果接收地址回收解析结果。
