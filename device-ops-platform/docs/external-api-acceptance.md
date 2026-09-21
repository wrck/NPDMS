# 外部解析 API 验收记录（2026-09-08）

## 交付范围

- 沿用 `POST /api/v1/parse-tasks`、任务列表/状态、按 resultId 查询接口。
- 新增 `GET /api/v1/parse-tasks/{taskId}/result`，复用既有结果 envelope 和 `semanticResult`，无结果返回 409 `RESULT_NOT_READY`。
- 保持生产 OAuth2 Resource Server，任务接口按 scope + JWT namespace 隔离；不增加无鉴权模式或 API Key 存储。
- 修复重复 inputContent 请求幂等；请求原始语义与固定发布分离，自动发布切换不影响重放，并发高隔离级别下读取已提交的原任务。
- scoped payload 稳定 ID/归属校验；默认输入 UTF-8 上限 8 MiB；非法输入统一 400，超限 413。
- 读取任务、结果、输入时加强 namespace 精确比较，避免数据库大小写不敏感排序规则造成跨 namespace 读取。
- 修复持久化 genericContent 的扁平 Section JSON 反序列化，保持已发布输出结构与历史结果不变。
- 新增 H2/MySQL `V17__record_parser_submission_identity.sql` 迁移，不修改历史迁移。
- 专用 OpenAPI `/v3/api-docs/external-parser` 和完整外部调用文档 `docs/external-api.md`。

## 可重复测试

```powershell
# 完整后端（项目根目录）
mvn verify

# HTTP、文档和入口校验
mvn -pl device-ops-server -am test `
  '-Dtest=ExternalParserHttpTest,ExternalParserOpenApiConfigurationTest,ParseTaskControllerTest' `
  -Dsurefire.failIfNoSpecifiedTests=false

# runtime 与持久化
mvn -pl device-ops-parser-runtime,device-ops-adapter-persistence-jdbc -am test `
  '-Dtest=com.dp.deviceops.parser.runtime.**.*Test,com.dp.deviceops.adapter.persistence.jdbc.**.*Test' `
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 已完成验证

工作期间，另一个任务正在同一工作区修改 semantic 模块，造成数次临时编译错误。未覆盖、撤销或纳入本次实现。为验证本次改动本身，使用 Git HEAD 加本次 API 文件的临时独立副本执行完整验收；未使用运行数据，未修改现有服务。

独立副本 `mvn verify`：**BUILD SUCCESS，267 个测试，0 失败、0 错误、0 跳过**。

| 模块 | 测试数 |
|---|---:|
| core | 49 |
| parser-semantic（原提交基线） | 76 |
| parser-runtime | 17 |
| SSH 适配器 | 27 |
| web-spring | 34 |
| JDBC | 42 |
| server | 22 |

当前工作区 runtime/JDBC 聚焦测试同样通过：**59 个测试，0 失败、0 错误、0 跳过**。

并行代码达到稳定状态后，当前工作区再次执行完整 `mvn verify`：**BUILD SUCCESS，341 个测试，0 失败、0 错误、0 跳过**（含其他任务新增的 semantic 测试，不将那些改动归入本次交付）。耗时约 63 秒。已生成 `device-ops-server/target/device-ops-server.jar`，验证包含本次 OpenAPI 配置和既有 `static/index.html`；前端使用已有 dist，未重新构建。

- `ExternalParserHttpTest` 使用真实 HTTP 服务、临时 H2/Flyway、后台 worker 和冻结 1.3.0 示例发布；使用测试专属 RSA 签名 JWT，经过真实资源服务器签名/issuer/有效期校验。
- 已验证：提交 202、重复请求同 taskId、变更请求冲突、状态轮询、两种结果查询、跨 namespace 404、scope 403、未认证/无效 token 401、无身份 403、输入引用归属、UTF-8 上限、无效 JSON/分页/header 和专用 OpenAPI。
- `ParserHaMySqlIntegrationTest` 实际启动 Docker MySQL 并通过，未跳过；H2 与 MySQL 均应用 V17 迁移。
- 并发回归覆盖相同请求合并、不同 context 冲突、可重复读事务竞争、活动发布切换/撤销与旧记录兼容。
- 已用 JSON 解析器校验文档中外层/内层请求、receivedBytes 和发布版本坐标。
- `git diff --check` 通过。

## 未替代的部署验收

没有连接真实生产 IdP、注册外部 OAuth2 客户端、修改网关、防火墙或当前运行服务。目标环境仍需配置受信 IdP、最小 scopes、独立 namespace、HTTPS、请求总大小与速率限制，以及已发布/激活的解析版本。

本次未修改前端，也未执行前端重新构建或浏览器验收；隔离构建用于后端 API/JAR 验证，不把前端静态页面产物作为本次交付。现有设备执行、保存连接和管理路由不属于这次专用解析 API 的安全验收范围。

所有修改未提交 Git，保留已有数据、review、brainstorm 及其他任务改动。
