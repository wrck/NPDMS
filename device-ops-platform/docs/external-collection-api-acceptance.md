# 外部采集 API 验收（2026-09-08）

## 已交付

保留单设备 `/api/v1/collections` 和项目批量 `/api/v1/projects/{projectKey}/collections` 的既有请求/响应格式与异步执行引擎。外部系统可提交命令/脚本，轮询状态和完整输出，订阅 SSE，并另行查询每个目标的语义解析结果。

新增/完善：

- scope + namespace + project + subject 授权边界；通用状态读取不再绕过持久化项目授权。
- 路径参数显式绑定，生产 Web/M2M 客户端的 namespace 授权要求见 `external-collection-api.md`。
- 无秘密请求指纹与 V18 H2/MySQL 迁移；相同请求及并发请求复用原任务，不重复执行；不同内容/subject/项目或 externalRequestId 竞争返回 409。
- 幂等查询先于连接/秘密加载；已受理任务重放可省略临时密码，保存连接重放不重新解密或查询；finally 清理敏感数组保持有效。
- 采集控制器错误 envelope，`external-collection` OpenAPI 分组及完整 PowerShell/curl 参数文档。
- 保留之前交付的 `external-parser` 文档与 API，不混淆“连接设备采集”和“上传离线日志解析”。

## 验证

工作区执行：

```powershell
mvn verify
```

结果：**BUILD SUCCESS，383 个测试，0 failures，0 errors，0 skipped**。最终约 100 秒完成，生成 `device-ops-server/target/device-ops-server.jar`。测试数量包含工作区之前存在的解析器改动，不把其他任务内容归入本次交付。

关键测试：

- `ExternalCollectionHttpTest`：12 个真实 HTTP 用例，RSA 签名 JWT 经过实际资源服务器，真实 MINA SSH fixture 和真实 JDBC/H2。验证单设备嵌套 connection、批量平铺 targets、主机指纹、终态输出、SSE output/complete/after、语义解析显式关闭、重复及无密码重试只执行一次、变更请求409、不同 subject409、scope/namespace/project/身份拒绝、路径绑定与 OpenAPI。
- `ExternalCollectionOpenApiConfigurationTest`：4 个文档边界测试，七个允许操作，不混入管理接口，不改变 parser 分组或全局安全。
- `CollectionBoundaryTest`、`CollectionRequestFingerprintTest`：授权 fail-closed、项目读取检查、不加载保存秘密的重放、敏感数组清理、请求对象键顺序稳定、秘密不参与指纹。
- core/JDBC：新旧构造兼容、历史 fingerprint 缺失安全冲突、指纹持久化、namespace/project 精确比较、并发胜者、外部请求 ID 冲突、事务回滚与可重复读竞争。
- `ParserHaMySqlIntegrationTest` 实际运行 Docker MySQL，验证迁移可用，未跳过。
- 既有 parser HTTP/OpenAPI 与生产 `SecureRemoteEndpointPolicyTest` 保持通过。
- `git diff --check` 无空白错误。

测试 fixture 的 loopback 授权仅存在测试专用 `@Primary` 适配器，限定 fixture host + port；生产远端地址策略、凭据处理与设备主机指纹校验未放松。测试服务/SSH/线程在结束时关闭，没有修改已有运行服务。

## 部署注意

- 生产外部客户端使用真实 IdP client_credentials、execute/read 最小 scopes、独立 namespace；涉及项目还需要 project claim。
- 既有 Web 客户端应由 IdP 显式授权实际 namespace，例如 standalone 与项目来源 namespace；旧的缺失 claim 任意访问行为不再允许。
- V18 为新增 nullable 指纹列，不修改历史迁移；旧任务查询保持可用，新 HTTP 请求不能猜测旧 key 对应身份或重跑它。
- 429 可能发生在落库/部分派发之后，务必保留并复用原 key；它不承诺“没有命令执行”。
- 仅验证受控本地 SSH fixture，不代表任意真实网络设备都兼容 EXEC/SHELL、分页提示符或命令。真实 IdP、网关、目标设备、callback 和生产网络仍需目标环境验收。
- 没有修改前端或重新构建前端，本轮只验证后端 API/JAR；保留已有 frontend/semantic 并行改动和数据。
- 未提交 Git，未部署或重启当前服务。
