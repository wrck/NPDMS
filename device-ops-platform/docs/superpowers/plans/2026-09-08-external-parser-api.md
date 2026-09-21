# External Parser API Implementation Plan

**Goal:** 让外部服务通过现有 OAuth2 边界安全、可重试地提交日志，查询任务并获取结构化结果。

**Architecture:** 复用 `/api/v1/parse-tasks`、现有持久化任务/worker/解析发布和结果查询；新增按任务读取结果的便捷端点。HTTP 层处理请求校验与身份边界，runtime/JDBC 层处理跨进程幂等和 payload 归属，server 层提供专门的 OpenAPI 分组。不新增第二套解析引擎、无鉴权接口或 API Key 存储。

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Security OAuth2 Resource Server, springdoc 3, JDBC/Flyway, H2/MySQL, JUnit 5.

## 设计取舍

- 推荐：完善已有异步 API。与采集平台共享不可变发布、重试机制和结果契约，保持旧路径兼容。
- 不采用：新增同步解析端点。大日志可能超过 HTTP 超时，也会绕过既有持久化闭环。
- 不采用：单独建设 API 网关/密钥管理。超出当前需求，现有 IdP 的 client credentials 已满足服务间调用。

## 1. runtime/JDBC 幂等与输入归属

Files: `device-ops-parser-runtime/src/main/java/com/dp/deviceops/parser/runtime/{port/ParserPayloadStore.java,port/ParseTaskRepository.java,service/ParseTaskService.java}`；`device-ops-adapter-persistence-jdbc` 中对应适配器、测试和新增迁移。

- [x] 先写测试证明同请求重试、切换活动发布后重试、改变 context/consumer/输入的冲突，以及 namespace 隔离。
- [x] 增加 `putScoped(callerNamespace, mediaType, content)`：同命名空间、媒体类型、UTF-8 内容得到稳定 ID，并发写入只保留一个 payload。
- [x] 增加 `isOwnedBy(callerNamespace, inputRef)`，明确拒绝其他命名空间及未知输入引用。
- [x] 提交前按 namespace + Idempotency-Key 查找并比较请求语义，重放已固定版本；数据库唯一约束竞争走相同比较逻辑。
- [x] 通过新增迁移存储请求发布选择/归属，不修改已执行迁移，不删除历史数据。

## 2. HTTP 接口

Files: `device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/parser/{ParseTaskController.java,ParserControlProperties.java,ParserApiErrorHandler.java}`；安全路由配置与新增测试。

- [x] 编写真实 HTTP 回归测试，先验证新增行为失败。
- [x] 新增 `GET /api/v1/parse-tasks/{taskId}/result`，需要 `parser:task:read`；成功返回现有结果 envelope，结果未就绪返回 409 `RESULT_NOT_READY`，不存在或跨 namespace 返回 404。
- [x] `POST /parse-tasks` 沿用 202 和原响应，inputContent 走 scoped payload；inputRef 必须属于当前 namespace；命名空间由受信 JWT claim/sub 派生，不能由 body 指定。
- [x] `Idempotency-Key` 与请求字段限制按数据库长度验证。输入 UTF-8 内容上限默认 8 MiB，可配置；超限返回 413。缺失 header、无效 JSON、约束失败统一返回 400 错误 envelope。
- [x] 保留 OAuth2 必须鉴权的默认部署。新增结果路径在过滤器和方法层同时验证 scope。

## 3. OpenAPI 与接入文档

Files: `device-ops-server/src/main/java/com/dp/deviceops/server/ExternalParserOpenApiConfiguration.java` 及其测试；`device-ops-server/src/main/resources/application.yml`；`docs/external-api.md`；`README.md`。

- [x] 在 `/v3/api-docs/external-parser` 提供只包含提交/列表/状态/结果的 OpenAPI 分组，标明 JWT Bearer 与所需 scopes、错误响应。
- [x] 文档给出 IdP client credentials 配置、PowerShell 获取 token/提交/轮询/读结果完整样例；明确 inputContent 是 `command-output-block/v1` 的 JSON 字符串，不是任意裸文本。
- [x] 说明固定 releaseId 与管理员预先发布/激活要求；不将 local 调试认证模式用于外部部署。

## 补充：持久化结果读取

真实 HTTP 测试发现现有通用结构 Section 采用扁平 JSON 序列化，但默认 Jackson 无法恢复其 fields，导致已成功任务的结果接口返回 500。补充 JDBC 专用反序列化适配与 roundtrip 测试，保持已发布的扁平输出 JSON 不变，不改写历史结果，也不触碰其他任务正在修改的 semantic 模块。

## 4. 验证

- [x] `mvn -pl device-ops-adapter-web-spring -am test`：控制器与 HTTP 绑定边界。
- [x] `mvn -pl device-ops-adapter-persistence-jdbc -am test`：JDBC 幂等与迁移。
- [x] 新增 server HTTP 集成测试：本地测试 RSA 签名 JWT，真实服务器 + H2 + worker + 冻结示例发布，验证 401/403、跨 namespace、提交/重试/结果与 OpenAPI。
- [x] `mvn verify`：完整后端测试与打包。不修改前端；真实 IdP、MySQL/Docker、目标网络仍需部署环境验证，最终明确记录未验证边界。

本次不提交 Git，不改动已有运行数据、审查文档、未跟踪 brainstorm 内容，也不启动或修改已有服务。
