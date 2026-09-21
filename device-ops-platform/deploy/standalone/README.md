# 独立 Compose 部署

启动前必须由部署环境或秘密管理系统注入以下变量；Compose 不提供主密钥默认值，也不应把真实值写入仓库或 `.env`：

| 环境变量 | 要求 |
|---|---|
| `DEVICE_OPS_CREDENTIAL_MASTER_KEY` | 必填。Base64 解码后必须为 32 字节的稳定 AES-256-GCM 主密钥；更换或丢失会导致已有保存连接无法解密。 |
| `DEVICE_OPS_TELNET_ENABLED` | 可选，默认 `false`；同时控制后端 Telnet 适配器和前端运行时开关。Telnet 会明文传输凭据和命令。 |
| `OIDC_ISSUER_URI` | Resource Server 校验 JWT 的 issuer。 |
| `OIDC_JWK_SET_URI` | Resource Server 读取 JWT 签名公钥的地址，必须从容器内部可访问。 |
| `OIDC_AUTHORITY` | Web Authorization Code + PKCE 使用的 authority。 |

示例仅展示注入方式，不包含真实秘密：

```powershell
$env:DEVICE_OPS_CREDENTIAL_MASTER_KEY = '<secret-store-injected-base64-key>'
$env:DEVICE_OPS_TELNET_ENABLED = 'false'
$env:OIDC_ISSUER_URI = 'https://idp.example.invalid/issuer'
$env:OIDC_JWK_SET_URI = 'https://idp.example.invalid/issuer/jwks'
$env:OIDC_AUTHORITY = 'https://idp.example.invalid/issuer'
docker compose -f .\device-ops-platform\deploy\standalone\compose.yaml up -d --build
```

Device Ops 只拥有按 OAuth2 subject 和 namespace 隔离、以 AES-256-GCM 加密的内部保存连接材料；项目和设备主数据仍来自外部主数据系统，不属于本服务。

## 双节点高可用部署

`compose.yaml` 仍是单节点 H2 调试配置，不能横向扩容。`compose-ha.yaml` 是支持的多实例配置：
MySQL 8.4 保存共享状态，`device-ops-a` 与 `device-ops-b` 使用同一数据库、凭据主密钥、OIDC
配置和解析结果接收方，HAProxy 在宿主机 `48081` 提供统一入口。该配置不使用单节点
`device-ops-data` H2 卷。

启动前额外注入 MySQL 密码和解析结果接收地址：

```powershell
$env:DEVICE_OPS_MYSQL_PASSWORD = '<secret-store-injected-password>'
$env:DEVICE_OPS_MYSQL_ROOT_PASSWORD = '<secret-store-injected-root-password>'
$env:DEVICE_OPS_PARSER_RESULT_CONSUMERS_NPDP = 'https://npdp.example.invalid/api/device-ops/parser-results'
docker compose -f .\device-ops-platform\deploy\standalone\compose-ha.yaml up -d --build
```

生产环境应把 MySQL 替换为受管高可用数据库；应用节点仍使用 `mysql` Profile 和
`classpath:db/mysql-migration`，并保持 `DEVICE_OPS_PARSER_ACTIVATION_MINIMUM_CAPABLE_WORKERS=2`。

解析节点通过数据库租约竞争任务，领取语句使用 `FOR UPDATE SKIP LOCKED`。节点退出时会缩短已有租约；
租约过期后其他节点以新 generation 接管，旧 generation 不能写入结果。发布上线前至少需要两个仍在
心跳有效期内、支持对应引擎/扩展坐标的工作节点。运行状态可通过
`GET /api/v1/parser-runtime/status` 检查，聚合指标从 `/actuator/metrics` 查询。
