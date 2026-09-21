# CRT 风格设备连接工作台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在独立 Device Ops Platform 中交付不依赖项目主档的 SSH2/Telnet 连接测试、脚本采集、最近连接、记录下载和交互式终端预留入口。

**Architecture:** 核心层以协议无关连接契约和可选业务上下文为边界，SSH2 与 Telnet 由独立适配器实现并通过协议路由器选择。通用采集接口不要求项目，原项目级接口继续校验项目 claim；前端使用单一连接工作台承载三种入口，并仅在组件内存中保留凭据。

**Tech Stack:** JDK 25、Spring Boot 4.1、Apache MINA SSHD 2.19.0、Apache Commons Net 3.13.0、H2/Flyway、Vue 3、TypeScript 5.9、Element Plus、Vitest/Playwright。

## 全局约束

- 不创建项目、设备或凭据主档；项目、设备和回调上下文均为可选快照。
- 连接入口仅包含项目设备、快速连接和最近连接。
- 本期协议仅包含 SSH2 与 Telnet；Telnet 默认关闭并明确提示明文风险。
- 密码、私钥和口令只保留在当前工作台组件内存，页面关闭、刷新、路由离开或手工清除时清零。
- 凭据不得进入数据库、浏览器持久化存储、URL、任务证据、日志、错误响应、导出文件或回调。
- 最近连接最多 20 条，只保存脱敏端点参数。
- 交互式终端本期只保留入口和扩展契约，不创建 WebSocket 会话。
- 保留现有项目级接口兼容性；只有携带项目上下文时才校验项目 claim。
- 禁用测试驱动：每项任务先实现，再补必要断言并执行聚焦验证，不安排“先失败再实现”循环。
- 不操作或提交 `device-ops-platform/data/`；不混入工作区无关变更。
- 每项任务独立审查和提交；提交前必须加载 `git-commit` 技能，不自动 push。

---

### Task 1：收口当前本地调试认证基线

**Files:**
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/security/DeviceOpsSecurityConfiguration.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/security/LocalDebugAuthenticationFilter.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/RuntimeConfigProperties.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/RuntimeConfigController.java`
- Modify: `device-ops-platform/device-ops-server/src/main/resources/application.yml`
- Modify: `device-ops-platform/device-ops-web/src/config/runtime.ts`
- Modify: `device-ops-platform/device-ops-web/src/auth/oidc.ts`
- Modify: `device-ops-platform/device-ops-web/src/router/index.ts`

**Interfaces:**
- Produces: `RuntimeConfig.authMode: "oauth2" | "local"`
- Produces: `DEVICE_OPS_AUTH_MODE=local` 仅用于无 IdP 的本地运行
- Preserves: 生产默认 `oauth2`

- [ ] **Step 1：核对现有未提交实现**

确认本地模式只注入带必要 scope 和 `device_ops_projects=["*"]` 的内存 JWT，不改变 OAuth2 默认值；确认前端本地模式不执行 OIDC 重定向。

- [ ] **Step 2：执行构建验证**

```powershell
pnpm.cmd --dir device-ops-platform/device-ops-web ts:check
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.1+8'
mvn.cmd -f device-ops-platform/pom.xml -pl device-ops-server -am package -DskipTests
```

Expected: TypeScript 检查和 Maven 打包成功。

- [ ] **Step 3：执行本地运行冒烟**

```powershell
java -jar device-ops-platform/device-ops-server/target/device-ops-server.jar `
  --server.port=48181 `
  --device-ops.security.mode=local `
  --device-ops.runtime.auth-mode=local
```

Expected:

```text
GET /actuator/health/readiness -> 200 {"status":"UP"}
GET /api/v1/runtime-config -> 200，authMode=local
GET /projects/direct -> 200 text/html
```

- [ ] **Step 4：独立提交**

```text
fix(device-ops): support local standalone authentication
```

只暂存本任务列出的文件，不暂存 `device-ops-platform/data/`。

---

### Task 2：建立协议无关连接契约和可选业务上下文

**Files:**
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/ConnectionProtocol.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/ConnectionFailure.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/ProtocolCommandExecutionAdapter.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/RemoteEndpointPolicy.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/ProtocolCommandExecutionRouter.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/CommandExecutionPort.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/CollectionContextSnapshot.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/CollectionTarget.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/CollectionTask.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/ExecutionConnectionContext.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/SubmitCollectionService.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/CollectionWorker.java`
- Modify: `device-ops-platform/device-ops-core/src/test/java/com/dp/deviceops/core/model/CollectionTaskTest.java`
- Modify: `device-ops-platform/device-ops-core/src/test/java/com/dp/deviceops/core/service/SubmitCollectionServiceTest.java`
- Modify: `device-ops-platform/device-ops-core/src/test/java/com/dp/deviceops/core/service/CollectionWorkerTest.java`
- Create: `device-ops-platform/device-ops-core/src/test/java/com/dp/deviceops/core/service/ProtocolCommandExecutionRouterTest.java`

**Interfaces:**
- Produces: `enum ConnectionProtocol { SSH2, TELNET }`
- Produces: `record TelnetPrompts(String login, String password, String command)`
- Produces: `ConnectionSpec(ConnectionProtocol protocol, String host, int port, String username, AuthenticationType authenticationType, ExecutionMode executionMode, String expectedHostKeyFingerprint, TelnetPrompts telnetPrompts, Duration connectTimeout)`
- Produces: `ProtocolCommandExecutionAdapter.protocol()`
- Produces: `RemoteEndpointPolicy.resolveConnectAddress(String host, int port)`
- Produces: `ConnectionFailure(Code code, Stage stage, String safeMessage)`
- Produces: `CollectionContextSnapshot.ofOptional(ProjectSnapshot project, DeviceSnapshot device, Map<String,String> extensions)`
- Produces: `Optional<ProjectSnapshot> project()` 与 `Optional<DeviceSnapshot> device()`

- [ ] **Step 1：实现连接协议、提示符和安全错误类型**

核心类型固定为：

```java
public enum ConnectionProtocol { SSH2, TELNET }

public record TelnetPrompts(String login, String password, String command) {
    public static TelnetPrompts defaults() {
        return new TelnetPrompts(
                "(?i)(login|username)\\s*:\\s*$",
                "(?i)password\\s*:\\s*$",
                "[>#\\$]\\s*$");
    }
}

public final class ConnectionFailure extends RuntimeException {
    public enum Code {
        UNREACHABLE, CONNECT_TIMEOUT, AUTH_FAILED, HOST_KEY_MISMATCH,
        PROMPT_NOT_FOUND, PROTOCOL_DISABLED, EXECUTION_TIMEOUT, CONNECTION_CLOSED
    }
    public enum Stage { RESOLVE, CONNECT, VERIFY_HOST, AUTHENTICATE, LOGIN, EXECUTE }
}
```

`ConnectionFailure` 只暴露固定 `code`、`stage` 和 `safeMessage`，不得把底层异常消息拼入安全消息。

- [ ] **Step 2：扩展连接执行契约**

将 `ConnectionSpec` 增加 `protocol` 和 `telnetPrompts`：

```java
public record ConnectionSpec(
        ConnectionProtocol protocol,
        String host,
        int port,
        String username,
        AuthenticationType authenticationType,
        ExecutionMode executionMode,
        String expectedHostKeyFingerprint,
        TelnetPrompts telnetPrompts,
        Duration connectTimeout) {
}
```

校验规则：

- SSH2 必须提供主机指纹；可用密码或私钥。
- Telnet 只允许密码认证和 `SHELL` 执行模式；主机指纹为空。
- 提示符正则每项不超过 500 字符，并在构造时预编译验证。

- [ ] **Step 3：实现协议适配器路由**

```java
public interface ProtocolCommandExecutionAdapter extends CommandExecutionPort {
    ConnectionProtocol protocol();
}

@FunctionalInterface
public interface RemoteEndpointPolicy {
    String resolveConnectAddress(String host, int port);
}

public final class ProtocolCommandExecutionRouter implements CommandExecutionPort {
    private final Map<ConnectionProtocol, ProtocolCommandExecutionAdapter> adapters;
    // test/execute 根据 connection.protocol() 选择适配器；
    // 缺失适配器时抛出 PROTOCOL_DISABLED。
}
```

- [ ] **Step 4：允许项目和设备快照独立缺省**

`CollectionContextSnapshot` 使用 `Optional` 返回项目和设备；不得创建 `direct`、`unknown` 等伪主档。`CollectionTask.projectKey()` 改为 `Optional<String>`。提交校验规则：

- 无项目上下文时允许提交。
- 有项目上下文时，任务项目键与所有包含项目的目标一致。
- 回调地址存在时必须同时包含项目和设备快照。

- [ ] **Step 5：让 worker 持久化安全连接结果**

`CollectionWorker` 单独捕获 `ConnectionFailure`，将固定错误码写入 outcome：

```java
catch (ConnectionFailure failure) {
    update(item, CollectionStatus.FAILED,
            evidence[0].withOutcome(failure.code().name()));
}
```

命令结果 `timedOut=true` 时使用 `EXECUTION_TIMEOUT`，不得把 cause message 写入 stdout、stderr 或 outcome。

- [ ] **Step 6：补充实现后的聚焦断言**

增加以下断言：

```java
assertDoesNotThrow(() -> submit(unscopedTarget()));
assertThrows(IllegalArgumentException.class, () -> submit(callbackWithoutContext()));
assertEquals(ConnectionProtocol.TELNET, telnetSpec.protocol());
assertEquals(ConnectionFailure.Code.PROTOCOL_DISABLED,
        assertThrows(ConnectionFailure.class, () -> router.test(missingProtocolSpec(), secret, null)).code());
assertEquals("AUTH_FAILED", persistedOutcomeAfter(authenticationFailure()));
```

- [ ] **Step 7：运行聚焦验证**

```powershell
mvn.cmd -f device-ops-platform/pom.xml `
  -pl device-ops-core `
  test
```

Expected: core tests pass.

- [ ] **Step 8：独立提交**

```text
feat(device-ops-core): add protocol-neutral connection contracts
```

---

### Task 3：持久化协议快照和无项目采集证据

**Files:**
- Create: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/resources/db/migration/V6__support_protocol_and_optional_context.sql`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/java/com/dp/deviceops/adapter/persistence/jdbc/CollectionJdbcRepository.java`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcCollectionQueryAdapter.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/CollectionQueryPort.java`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/test/java/com/dp/deviceops/adapter/persistence/jdbc/CollectionPersistenceTest.java`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/test/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcCollectionQueryAdapterTest.java`

**Interfaces:**
- Consumes: Task 2 optional `CollectionContextSnapshot`
- Produces: `CollectionQueryPort.find(String namespace, String collectionId)`
- Produces: `EndpointSnapshot.protocol()` 与脱敏 Telnet 提示符快照
- Preserves: `find(String namespace, String projectKey, String collectionId)`

- [ ] **Step 1：增加 Flyway V6**

迁移执行以下变化：

```sql
ALTER TABLE device_ops_collection ALTER COLUMN project_key DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN project_name DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN project_code DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN device_key DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN device_name DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN vendor DROP NOT NULL;
ALTER TABLE device_ops_collection_target ALTER COLUMN model DROP NOT NULL;
ALTER TABLE device_ops_collection_target ADD COLUMN protocol VARCHAR(20) NOT NULL DEFAULT 'SSH2';
ALTER TABLE device_ops_collection_target ADD COLUMN telnet_prompts_json CLOB;
```

凭据字段不得出现在迁移中。

- [ ] **Step 2：更新写入与恢复**

`CollectionJdbcRepository` 对缺省上下文写入 SQL `NULL`，写入 `protocol` 和提示符 JSON；恢复时使用 `CollectionContextSnapshot.ofOptional(...)`，不得补伪值。

- [ ] **Step 3：增加通用任务查询**

```java
Optional<CollectionDetails> find(String namespace, String collectionId);
Optional<CollectionDetails> find(String namespace, String projectKey, String collectionId);
```

两个查询都必须在同一 SQL 中约束 namespace；项目级查询额外约束 project_key。

`CollectionQueryPort.ContextSnapshot` 的 `project` 和 `device` 允许为空；JSON 响应使用 `null` 表达缺省上下文，不返回空字符串对象。

- [ ] **Step 4：修正回调投影**

`JdbcCollectionExecutionPersistencePort` 只在 callback URI 存在且项目、设备键均非空时创建 outbox payload；无回调的直接连接不得生成伪业务指向。

- [ ] **Step 5：补充实现后的持久化断言**

覆盖：

- 无项目/设备快照可以写入并查询。
- SSH2/Telnet 协议快照可恢复。
- 项目级查询不能越过项目键。
- 持久化列、回调 payload 和查询 DTO 不包含 credential。

- [ ] **Step 6：运行聚焦验证**

```powershell
mvn.cmd -f device-ops-platform/pom.xml `
  -pl device-ops-adapter-persistence-jdbc -am `
  test
```

Expected: Flyway 迁移和 JDBC tests pass.

- [ ] **Step 7：独立提交**

```text
feat(device-ops-db): persist optional connection context
```

---

### Task 4：将现有 SSH2 适配器纳入统一错误模型

**Files:**
- Modify: `device-ops-platform/device-ops-adapter-ssh-mina/src/main/java/com/dp/deviceops/adapter/ssh/mina/MinaCommandExecutionAdapter.java`
- Delete: `device-ops-platform/device-ops-adapter-ssh-mina/src/main/java/com/dp/deviceops/adapter/ssh/mina/RemoteHostPolicy.java`
- Create: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/SecureRemoteEndpointPolicy.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/DeviceOpsWiringConfiguration.java`
- Modify: `device-ops-platform/device-ops-adapter-ssh-mina/src/test/java/com/dp/deviceops/adapter/ssh/mina/MinaCommandExecutionAdapterTest.java`

**Interfaces:**
- Consumes: `ProtocolCommandExecutionAdapter`
- Produces: `protocol() == ConnectionProtocol.SSH2`
- Produces: Task 2 `ConnectionFailure` codes

- [ ] **Step 1：让 MINA 适配器实现协议适配器接口**

增加：

```java
@Override
public ConnectionProtocol protocol() {
    return ConnectionProtocol.SSH2;
}
```

非 SSH2 spec 直接抛 `PROTOCOL_DISABLED`。

- [ ] **Step 2：按连接阶段转换异常**

- 地址策略拒绝或解析失败：`UNREACHABLE / RESOLVE`
- TCP/握手超时：`CONNECT_TIMEOUT / CONNECT`
- 指纹不匹配：`HOST_KEY_MISMATCH / VERIFY_HOST`
- 用户名、密码或私钥失败：`AUTH_FAILED / AUTHENTICATE`
- 命令超时：结果保持 `timedOut=true`，worker 映射 `EXECUTION_TIMEOUT`
- 连接提前关闭：`CONNECTION_CLOSED / EXECUTE`

保留底层异常为 cause，但不将其 message 返回给 API。

`SecureRemoteEndpointPolicy` 同时接受 IP 和 DNS 主机名：DNS 解析后使用解析出的字面地址建立连接，并拒绝 any-local、loopback、link-local 和 multicast；允许设备管理网常见的 RFC1918 私网地址。`localhost` 及其子域始终拒绝。SSH 与 Telnet 适配器都通过构造器接收同一个 `RemoteEndpointPolicy`，测试使用固定 lambda 注入模拟回环端点。

- [ ] **Step 3：补充实现后的模拟 SSH 断言**

复用进程内 Apache MINA SSH server，断言成功、认证失败、指纹不匹配和超时的固定错误码。

- [ ] **Step 4：运行聚焦验证**

```powershell
mvn.cmd -f device-ops-platform/pom.xml `
  -pl device-ops-adapter-ssh-mina,device-ops-server -am `
  test
```

- [ ] **Step 5：独立提交**

```text
refactor(device-ops-ssh): normalize SSH2 connection outcomes
```

---

### Task 5：新增可插拔 Telnet 适配器

**Files:**
- Create: `device-ops-platform/device-ops-adapter-telnet/pom.xml`
- Create: `device-ops-platform/device-ops-adapter-telnet/src/main/java/com/dp/deviceops/adapter/telnet/TelnetCommandExecutionAdapter.java`
- Create: `device-ops-platform/device-ops-adapter-telnet/src/test/java/com/dp/deviceops/adapter/telnet/TelnetCommandExecutionAdapterTest.java`
- Create: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/TelnetProperties.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/DeviceOpsWiringConfiguration.java`
- Modify: `device-ops-platform/pom.xml`
- Modify: `device-ops-platform/device-ops-server/pom.xml`
- Modify: `device-ops-platform/device-ops-server/src/main/resources/application.yml`

**Interfaces:**
- Consumes: Task 2 `ProtocolCommandExecutionAdapter`
- Consumes: Task 2 `RemoteEndpointPolicy`
- Produces: `protocol() == ConnectionProtocol.TELNET`
- Produces: `device-ops.telnet.enabled=false`
- Dependency: `commons-net:commons-net:3.13.0`

- [ ] **Step 1：新增 Maven 模块和依赖**

父 POM 增加：

```xml
<commons-net.version>3.13.0</commons-net.version>
<module>device-ops-adapter-telnet</module>
```

新模块依赖 `device-ops-core`、`commons-net:commons-net:${commons-net.version}` 和测试范围的 JUnit Jupiter，不依赖 Spring。server 依赖新模块并负责属性绑定和 bean 装配。

- [ ] **Step 2：实现配置边界**

```java
@ConfigurationProperties("device-ops.telnet")
public class TelnetProperties {
    private boolean enabled;
    private int maxOutputBytes = 1_048_576;
}
```

`enabled=false` 时 `test` 和 `execute` 都返回 `PROTOCOL_DISABLED`。

- [ ] **Step 3：实现 Telnet 登录状态机**

`TelnetCommandExecutionAdapter` 使用 `TelnetClient`：

1. 按与 SSH2 相同的远端地址规则解析并固定连接地址，在连接超时内建立会话。
2. 读取到 login prompt 后发送用户名和 `CRLF`。
3. 读取到 password prompt 后发送密码和 `CRLF`。
4. 读取到 command prompt 后判定登录成功。
5. 私钥认证或非 SHELL 模式返回安全参数错误。

所有读取共用单调时钟 deadline；缓冲区达到 `maxOutputBytes` 时设置 truncated。

- [ ] **Step 4：实现按行命令执行**

规范化 `CRLF` 后逐行发送非空命令；每条命令读取至命令提示符，去除输入回显并追加到 stdout。超时返回：

```java
new CommandResult(-1, stdout, "", true, truncated, durationMillis)
```

设备主动关闭映射 `CONNECTION_CLOSED`，提示符缺失映射 `PROMPT_NOT_FOUND`。

- [ ] **Step 5：补充实现后的模拟 Telnet 断言**

测试使用进程内 `ServerSocket` 模拟：

- 正常 username/password/prompt/command 输出；
- 错误密码后连接关闭；
- 登录提示符缺失；
- 命令提示符超时；
- 输出截断；
- `enabled=false`。

- [ ] **Step 6：运行聚焦验证**

```powershell
mvn.cmd -f device-ops-platform/pom.xml `
  -pl device-ops-adapter-telnet -am `
  test
```

- [ ] **Step 7：独立提交**

```text
feat(device-ops-telnet): add optional Telnet execution adapter
```

---

### Task 6：提供连接测试和通用采集 API

**Files:**
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/ConnectionTestService.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/ConnectionRequestMapper.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/ConnectionTestController.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/GenericCollectionController.java`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/CollectionController.java`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/CollectionRequestBodyAdvice.java`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/CollectionCredentialCleanupFilter.java`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/security/DeviceOpsSecurityConfiguration.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/DeviceOpsWiringConfiguration.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/RuntimeConfigProperties.java`
- Modify: `device-ops-platform/device-ops-server/src/main/java/com/dp/deviceops/server/RuntimeConfigController.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/ConnectionTestControllerTest.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/GenericCollectionControllerTest.java`

**Interfaces:**
- Produces: `POST /api/v1/connections/test`
- Produces: `POST /api/v1/collections`
- Produces: `GET /api/v1/collections/{collectionId}?namespace=...`
- Preserves: `/api/v1/projects/{projectKey}/collections`
- Produces runtime field: `telnetEnabled`

- [ ] **Step 1：实现共享请求映射**

`ConnectionRequestMapper` 定义并复用以下 DTO：

```java
record Connection(
        @NotNull ConnectionProtocol protocol,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotBlank String username,
        @NotNull AuthenticationType authenticationType,
        String hostKeyFingerprint,
        TelnetPrompts telnetPrompts,
        @Min(1) long connectTimeoutSeconds,
        @JsonProperty(access = WRITE_ONLY) char[] password,
        @JsonProperty(access = WRITE_ONLY) char[] privateKey,
        @JsonProperty(access = WRITE_ONLY) char[] passphrase) {
}
```

mapper 负责构造 `ConnectionSpec`、复制 `TransientCredential` 并在 finally 中清除 DTO 数组。

`DeviceOpsWiringConfiguration` 将 `MinaCommandExecutionAdapter` 和 `TelnetCommandExecutionAdapter` 收集为
`List<ProtocolCommandExecutionAdapter>`，并用 `ProtocolCommandExecutionRouter` 提供唯一的
`CommandExecutionPort` bean。

- [ ] **Step 2：实现连接测试服务和控制器**

```java
public record TestResult(
        boolean reachable,
        ConnectionFailure.Stage stage,
        long durationMillis,
        ConnectionFailure.Code errorCode,
        String safeMessage) {
}
```

成功返回 `reachable=true`；已知 `ConnectionFailure` 返回 200 和固定诊断结果；无效请求返回 400；不得返回底层异常。

- [ ] **Step 3：实现通用采集控制器**

`POST /api/v1/collections` 接受：

- `namespace` 必填；
- `context.project`、`context.device` 可独立为空；
- callback URL 存在时项目和设备均必填；
- target 连接字段和 script 必填。

携带项目时调用 `ProjectClaimAuthorizer.require(jwt, projectKey)`；无项目时只依赖 execute scope。

- [ ] **Step 4：保留项目级接口**

项目级 controller 继续要求路径键、项目快照和 JWT claim 一致；内部复用 mapper 和提交协调逻辑，避免复制凭据处理。

- [ ] **Step 5：调整安全规则和运行时配置**

```text
POST /api/v1/connections/test -> SCOPE_device-ops:collections:execute
POST /api/v1/collections -> SCOPE_device-ops:collections:execute
GET  /api/v1/collections/* -> SCOPE_device-ops:collections:read
```

`/api/v1/runtime-config` 增加 `telnetEnabled`，供前端禁用 Telnet 选项。

- [ ] **Step 6：补充实现后的 API 断言**

覆盖：

- 无项目连接测试成功。
- 无项目通用采集 accepted。
- 带项目通用采集执行项目 claim 校验。
- callback 无项目/设备时 400。
- 凭据不出现在 JSON 响应和日志捕获中。
- 项目级旧接口行为保持不变。

- [ ] **Step 7：运行聚焦验证**

```powershell
mvn.cmd -f device-ops-platform/pom.xml `
  -pl device-ops-adapter-web-spring,device-ops-server -am `
  test
```

- [ ] **Step 8：独立提交**

```text
feat(device-ops-api): add direct connection and collection endpoints
```

---

### Task 7：重构前端为轻量连接工作台

**Files:**
- Create: `device-ops-platform/device-ops-web/src/types/connection.ts`
- Create: `device-ops-platform/device-ops-web/src/api/connections.ts`
- Create: `device-ops-platform/device-ops-web/src/security/recent-connections.ts`
- Create: `device-ops-platform/device-ops-web/src/components/ConnectionWorkbench.vue`
- Create: `device-ops-platform/device-ops-web/src/components/ConnectionDiagnostics.vue`
- Modify: `device-ops-platform/device-ops-web/src/components/TargetSelector.vue`
- Modify: `device-ops-platform/device-ops-web/src/components/CollectionTaskPanel.vue`
- Modify: `device-ops-platform/device-ops-web/src/views/ProjectCollectionView.vue`
- Modify: `device-ops-platform/device-ops-web/src/api/device-ops.ts`
- Modify: `device-ops-platform/device-ops-web/src/types/collection.ts`
- Modify: `device-ops-platform/device-ops-web/src/config/runtime.ts`
- Modify: `device-ops-platform/device-ops-web/src/styles/main.css`
- Modify: `device-ops-platform/device-ops-web/src/app-shell.spec.ts`
- Modify: `device-ops-platform/device-ops-web/e2e/collection.spec.ts`

**Interfaces:**
- Produces: `ConnectionDraft`
- Produces: `RecentConnection`
- Produces: `testConnection(connection): Promise<ConnectionTestResult>`
- Consumes: Task 6 generic collection API

- [ ] **Step 1：定义前端连接类型**

```ts
export type ConnectionSource = 'PROJECT_DEVICE' | 'QUICK_CONNECT' | 'RECENT'
export type ConnectionProtocol = 'SSH2' | 'TELNET'

export interface ConnectionDraft {
  source: ConnectionSource
  protocol: ConnectionProtocol
  host: string
  port: number
  username: string
  hostKeyFingerprint: string
  authenticationType: 'PASSWORD' | 'PRIVATE_KEY'
  password: string
  privateKey: string
  passphrase: string
  telnetPrompts: {
    login: string
    password: string
    command: string
  }
}
```

- [ ] **Step 2：实现最近连接存储**

`recent-connections.ts` 只序列化：

```ts
type RecentConnection = Pick<
  ConnectionDraft,
  'protocol' | 'host' | 'port' | 'username' | 'hostKeyFingerprint' | 'telnetPrompts'
> & { deviceHint?: string; lastUsedAt: string }
```

规则：

- localStorage key 固定为 `device-ops:recent-connections:v1`；
- 按 `protocol|host|port|username` 去重；
- 最新优先，最多 20 条；
- 解析失败时返回空数组；
- 保存前显式删除所有 credential 字段。

- [ ] **Step 3：实现三个连接入口**

`ConnectionWorkbench.vue` 使用三个 `el-tab-pane`：

- 项目设备：复用只读主档代理，可不选择项目/设备并切到快速连接。
- 快速连接：直接输入连接参数。
- 最近连接：选择、单条删除、全部清空。

协议切换时自动使用端口 22/23；SSH2 显示指纹和密码/私钥，Telnet 只显示密码和折叠的提示符高级配置。

- [ ] **Step 4：实现连接诊断**

“测试连接”调用 Task 6 API，`ConnectionDiagnostics.vue` 显示阶段、耗时、安全错误码和安全消息。Telnet 未启用时禁用选项并展示部署提示。

- [ ] **Step 5：修正凭据生命周期**

移除 `CollectionTaskPanel` 提交后触发的 `credentials-dispatched` 清空逻辑。提交使用从当前表单创建的请求副本，HTTP 请求完成后只清除该副本：

```ts
const request = buildRequest(connectionDraft)
try {
  return await submitCollection(request)
} finally {
  scrubRequestCredentials(request)
}
```

组件原始凭据只在以下场景清除：

- 点击“清除凭据”；
- `onBeforeRouteLeave`；
- `onBeforeUnmount`；
- 浏览器刷新或关闭导致页面内存释放。

- [ ] **Step 6：接入通用采集**

无项目上下文调用 `/api/v1/collections`；有明确项目上下文时继续调用项目级路径。证据面板根据任务来源选择相应 GET 路径。

- [ ] **Step 7：增加交互式终端占位入口**

与“脚本采集”并列显示“交互式终端（规划中）”，按钮 disabled，并说明本期不建立 WebSocket 会话。

- [ ] **Step 8：补充实现后的组件断言**

断言：

- 快速连接无需项目设备。
- SSH2/Telnet 字段正确切换。
- 最近连接 JSON 不含 `password`、`privateKey`、`passphrase`。
- 任务下发后表单凭据仍存在。
- 路由离开时凭据被清除。
- 终端入口可见但不可点击。

- [ ] **Step 9：运行前端验证**

```powershell
pnpm.cmd --dir device-ops-platform/device-ops-web test
pnpm.cmd --dir device-ops-platform/device-ops-web ts:check
pnpm.cmd --dir device-ops-platform/device-ops-web build
```

- [ ] **Step 10：独立提交**

```text
feat(device-ops-web): add CRT-style connection workbench
```

---

### Task 8：提供输入记录和完整会话记录下载

**Files:**
- Create: `device-ops-platform/device-ops-web/src/utils/session-export.ts`
- Create: `device-ops-platform/device-ops-web/src/utils/session-export.spec.ts`
- Modify: `device-ops-platform/device-ops-web/src/components/CollectionTaskPanel.vue`
- Modify: `device-ops-platform/device-ops-web/e2e/collection.spec.ts`

**Interfaces:**
- Produces: `downloadInputRecord(script, deviceHint, startedAt)`
- Produces: `downloadSessionRecord(details, script, startedAt, finishedAt)`

- [ ] **Step 1：实现安全文件名**

```ts
export function safeFilePart(value: string): string {
  return value.normalize('NFKC').replace(/[^\p{L}\p{N}._-]+/gu, '_').slice(0, 80) || 'device'
}
```

生成：

```text
{device}_{yyyyMMdd-HHmmss}_input.txt
{device}_{yyyyMMdd-HHmmss}_session.log
```

- [ ] **Step 2：实现输入记录下载**

使用 UTF-8 `Blob` 下载脚本正文，MIME 为 `text/plain;charset=utf-8`。不得拼接连接表单或凭据对象。

- [ ] **Step 3：实现完整记录格式**

日志固定段落：

```text
[SESSION]
startedAt=...
finishedAt=...
protocol=...
endpoint=username@host:port
project=...
device=...
status=...

[INPUT]
...

[STDOUT]
...

[STDERR]
...
```

只从脱敏任务证据和脚本正文生成；不读取连接表单 credential 字段。

- [ ] **Step 4：在证据面板增加下载按钮**

“保存输入记录”在脚本存在时启用；“保存完整记录”在任务有查询结果时启用。

- [ ] **Step 5：补充实现后的断言**

断言文件名清理、UTF-8 内容、段落顺序，以及日志不包含示例密码、私钥标记或口令。

- [ ] **Step 6：运行前端验证**

```powershell
pnpm.cmd --dir device-ops-platform/device-ops-web test
pnpm.cmd --dir device-ops-platform/device-ops-web build
```

- [ ] **Step 7：独立提交**

```text
feat(device-ops-web): export input and session records
```

---

### Task 9：集成打包、模拟联通和浏览器验收

**Files:**
- Modify: `device-ops-platform/README.md`
- Modify: `device-ops-platform/docs/acceptance.md`
- Modify: `device-ops-platform/deploy/standalone/compose.yaml`
- Modify: `device-ops-platform/deploy/npdp-compose/compose.device-ops.yaml`
- Modify: `device-ops-platform/device-ops-web/e2e/collection.spec.ts`
- Create: `device-ops-platform/docs/crt-connection-acceptance.png`

**Interfaces:**
- Documents: `DEVICE_OPS_TELNET_ENABLED`
- Documents: 通用连接/采集 API
- Documents: local/OAuth2 两种认证启动方式

- [ ] **Step 1：补齐部署配置**

两个 compose 文件增加：

```yaml
DEVICE_OPS_TELNET_ENABLED: ${DEVICE_OPS_TELNET_ENABLED:-false}
```

README 明确 Telnet 为明文协议、默认关闭，并记录 SSH2/Telnet 字段差异。

- [ ] **Step 2：执行完整构建**

```powershell
pnpm.cmd --dir device-ops-platform/device-ops-web build
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.1+8'
mvn.cmd -f device-ops-platform/pom.xml package
```

Expected: 所有模块和前端构建成功。

- [ ] **Step 3：运行独立服务**

```powershell
java -jar device-ops-platform/device-ops-server/target/device-ops-server.jar `
  --server.port=48181 `
  --device-ops.security.mode=local `
  --device-ops.runtime.auth-mode=local `
  --device-ops.telnet.enabled=true
```

- [ ] **Step 4：执行模拟连接验证**

使用进程内模拟 SSH/Telnet 服务验证：

- 成功连接和命令输出；
- 地址/端口不可达；
- 认证失败；
- SSH 指纹不匹配；
- Telnet 提示符缺失；
- 命令超时；
- 输出截断。

这些模拟只验证真实协议状态和功能分支，不等待外部设备。

- [ ] **Step 5：执行真实页面冒烟**

在真实浏览器中通过按钮完成：

1. 进入 `/projects/direct`。
2. 快速连接选择 SSH2，填写模拟端点并测试。
3. 切换 Telnet，确认风险提示和字段变化。
4. 下发模拟命令并查看状态、stdout、stderr。
5. 保存输入文件和完整记录。
6. 打开最近连接，确认没有凭据。
7. 刷新页面，确认凭据字段为空。
8. 确认交互式终端入口存在且未启用。

验收要求：页面无控制台错误；相关 HTTP 请求成功且业务语义正确；保存验收截图。

- [ ] **Step 6：更新验收证据**

`docs/acceptance.md` 逐项记录命令、结果、限制和截图路径。明确真实网络设备与真实 IdP 仍属于目标环境验收，不把模拟服务表述为真实设备。

- [ ] **Step 7：独立审查**

按以下风险顺序审查：

1. 凭据是否进入持久化、日志、回调、localStorage 或下载文件。
2. 无项目通用 API 是否绕过了携带项目时的 claim 校验。
3. Telnet 是否默认关闭且没有错误地支持私钥。
4. 原项目级 API 是否兼容。
5. worker、租约、输出限长和解析链是否保持一致。

- [ ] **Step 8：独立提交**

```text
docs(device-ops): record CRT connection workbench acceptance
```

---

## Task → 需求追踪

| Task | 需求 |
|---|---|
| Task 1 | 无 IdP 本地界面可运行 |
| Task 2 | SSH2/Telnet 统一连接模型；项目/设备可选 |
| Task 3 | 脱敏协议证据；无项目任务持久化和查询 |
| Task 4 | SSH2 统一错误结果 |
| Task 5 | 可插拔 Telnet；默认关闭 |
| Task 6 | 测试连接；通用采集；项目级兼容 |
| Task 7 | 项目设备、快速连接、最近连接；凭据页面级生命周期；终端入口 |
| Task 8 | 输入记录和完整记录下载 |
| Task 9 | 模拟真实连接分支、构建、浏览器和部署验收 |
