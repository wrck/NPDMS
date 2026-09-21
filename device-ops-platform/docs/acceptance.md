# Device Ops Platform 验收记录

## 结论范围

本记录区分自动化证据、JAR 运行烟测和仍待外部环境验证的项目。当前证据支持“独立应用可构建、可启动，核心 SSH 适配器和持久化闭环通过模拟测试”；不支持“已连接真实设备、真实 IdP 或已实际启动 Docker”的声明。

## 已确认的自动化证据

### 前端

- 3 个前端测试通过。
- TypeScript `ts:check` 通过。
- ESLint `lint` 通过。
- 生产构建 `build` 通过。

这些检查覆盖应用壳、项目采集工作台、运行时配置和一次性凭据在浏览器内的处理约束。

### 隔离浏览器验收

- 使用本机 Chrome 和 Playwright 从 `/projects/PROJ-ACCEPT` 进入真实 Vue 页面。
- 通过界面完成“只读主档设备”和“直接目标”两条采集旅程，真实点击“冻结并下发”。
- 1 个浏览器用例、39 个断言通过；最终复验耗时 14.1 秒。
- 验证外部脚本 SHA-256、冻结项目/设备/端点快照、终态轮询、stdout、stderr 和解析事实。
- POST 返回后一次性密码输入框为空；浏览器存储中不存在设备凭据。
- Console warning/error、page error 和 failed request 均为 0。
- 截图见 [browser-acceptance.png](browser-acceptance.png)。

该浏览器验收使用 Playwright route 模拟 OIDC 用户、主档 API、采集提交和结果查询；
它验证真实页面、表单、按钮、路由、凭据清理和证据呈现，不等同于真实 IdP、真实后端
或真实设备的跨进程联调。

### 后端构建与运行

- JDK 25 后端构建通过，可执行 JAR 已生成。
- JDK 25 直接运行 JAR，端口 `48181` 烟测通过。
- `/actuator/health/readiness`、`/api/v1/runtime-config`、`/v3/api-docs`、`/`、`/projects/{projectKey}`、`/embed/projects/{projectKey}` 均返回 HTTP 200。
- Tomcat、Spring Boot、Flyway、静态前端和 OpenAPI 在同一可执行 JAR 中启动。

端口 `48181` 是验收时覆盖的运行端口；产品默认端口仍为 `48081`。

### Embedded SSH 适配器

18 个 SSH 适配器测试通过，覆盖：

- 正确密码认证和正确私钥认证。
- 错误主机指纹和错误密码。
- 连接/认证共享截止时间、命令超时、断线清理。
- 远端不返回 exit status。
- stdout 与 stderr 独立的大流量上限和截断标记。
- UTF-8 stdout/stderr。
- 异常信息和输出中的凭据脱敏。

这些测试使用嵌入式 Apache MINA SSH server，不等同于真实厂商设备验收。

### 后端分层闭环

本轮评估过在 server 模块新增单个 Spring/H2/Flyway + embedded SSH journey。由于生产 `RemoteHostPolicy` 有意拒绝 loopback，server 测试若要复用真实 MINA adapter，必须反射包内测试缝隙并复制一套 SSH server command；再加入 fake master-data、OIDC/API 提交和 callback 后，会形成第二套重型全链。该方案没有保留，也没有据此声称新增测试通过。

当前验收复用已有分层证据：

- Core worker 测试覆盖冻结连接上下文、瞬时凭据生命周期、stdout/stderr 脱敏、KEY_VALUE 解析与终态处理。
- H2 + Flyway + JDBC 测试覆盖冻结项目/设备/端点快照、worker 状态持久化、stdout/stderr/facts 查询投影、作用域查询，以及数据库无项目/设备/凭据主档和无凭据列。
- 18 个 embedded SSH adapter 测试覆盖真实 MINA client 与进程内 SSH server 的认证、指纹、超时、输出和脱敏边界。
- JDK 25 JAR runtime smoke 覆盖 Boot 组装、Flyway 自动迁移、H2、Tomcat、Actuator、OpenAPI 和静态应用入口。

这些证据形成代码层闭环，但不是跨进程业务 E2E。真实 master-data、OIDC、callback 和设备联调仍应在部署环境验证。

### NPDP 脱离

- PDP 后端脱离后的 11 个聚焦测试通过。
- PDP 管理端 `vue-tsc --noEmit` 通过。
- PDP 架构边界拒绝依赖 `org.apache.sshd..` 和 `com.dp.deviceops..`。
- Device Ops 的 DO、Mapper、迁移和 Vue/API 集成残留已从 PDP 发布边界清理；NPDP 可选集成仅保留部署和 iframe/URL 契约。

## 安全与数据边界

- Device Ops 不保存项目、设备或凭据主档；只拥有用户主动创建、按 OAuth2 subject 和 namespace 隔离的内部保存连接。
- 执行连接使用提交时快照，不在 worker 阶段回查主系统。
- 临时连接的密码、私钥和私钥口令是 write-only 瞬时内存数据；保存连接的认证材料只以 AES-256-GCM 密文进入数据库，所有查询响应都不返回秘密。
- 查询必须同时按 namespace、projectKey 和 collectionId 限定。
- OAuth2 scope 与项目 claim 是两条独立授权边界。
- H2 文件数据库使用 `AUTO_SERVER=FALSE`，当前只支持单副本。

## 尚未验证，不得声称完成

- 未连接真实网络设备，未验证厂商 CLI 差异、跳板机、弱算法兼容或现场网络策略。
- 未连接真实 OIDC/IdP，未验证真实登录、token 刷新、claim 映射、登出和时钟偏差。
- Docker/Compose 文件已提供，但本轮没有实际执行镜像构建或容器启动。
- 未执行真实外部 master-data 服务与 callback 接收方的跨进程联调。

## 仍待集成环境验证

- [ ] 使用真实 IdP 完成登录、token 刷新、登出和项目 claim 映射。
- [ ] 浏览器连接真实 Device Ops API，刷新页面后确认历史证据仍可查询。
- [ ] 验证无权限 scope、错误项目 claim 和跨项目 URL 均被拒绝。
- [ ] 在真实 iframe 宿主中验证 CSP、登录和 `/embed/projects/{projectKey}` 深链。
- [ ] 连接真实主档代理、callback 接收方和厂商设备。
- [ ] 联合检查服务日志、浏览器存储、网络响应和数据库中不存在明文凭据。
