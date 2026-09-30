# 串口采集接入（jSerialComm）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为设备运维平台新增 SERIAL 采集协议：服务器本地 COM 口经 jSerialComm 直连设备 Console，控制台账号+提示符交互认证，完整融入现有连接/保存连接/采集/证据链路。

**Architecture:** 沿用现有协议适配器插件模式——`ConnectionSpec` 增加 SERIAL 分支校验，新建 `device-ops-adapter-serial` Maven 模块实现 `ProtocolCommandExecutionAdapter`（内部以 `SerialTransport` 小接口隔离 jSerialComm，测试用内存替身），Spring 装配后由 `ProtocolCommandExecutionRouter` 自动路由（路由零改动）。保存连接持久化新增 V21 增量迁移（H2）与 MySQL 基线同步。前端 `ProtocolConnectionForm.vue` 增加"串口"协议分支，新增 `GET /api/v1/serial-ports` 枚举端点与 `serialEnabled` 能力位。

**Tech Stack:** Java 25、Spring Boot 4.1、jSerialComm 2.11.0、H2/MySQL(Flyway)、JUnit 5、Vue 3 + Element Plus + vitest。

**Spec:** `docs/superpowers/specs/2026-09-24-serial-port-access-design.md`（已获用户批准）。

**与 spec 的三处偏差（实现时以此计划为准）：**
1. `SerialParams` 不含 `comPort` 字段——COM 口名由 `ConnectionSpec.host` 唯一承载（spec §1 同时写了 host 与 comPort，双字段会产生不一致；host 即 COM 名）。
2. `SerialCommandExecutionAdapter` 不注入 `RemoteEndpointPolicy`——该策略按主机名做 DNS 解析并拒绝回环地址（`SecureRemoteEndpointPolicy`），COM 口名（如 `COM3`）解析必然失败；串口是本地设备，改用适配器内严格 COM 名校验 + 口存在性检查，安全语义不变。
3. 保存连接持久化在数据库中是**专用列**而非 JSON 存储（spec §5 误记），新增串口列需要 V21 增量迁移（H2 `db/migration/V21__*.sql`）并同步 MySQL 基线 `db/mysql-migration/V1__create_device_ops_baseline.sql`。迁移为纯加列、可空，不触碰既有数据。

**全局约束（每个任务都适用）：**
- 分支 `codex/crt-persistence-context`，只 commit 不 push。
- 凭据相关文件（`data/`、`deploy/runtime-48181/`、`device-ops-master-key.dpapi`、`device-ops.mv.db`）已在 `.gitignore`，永不提交。
- 48181 服务正在运行（计划任务 `DeviceOps-48181`，java PID 34032）——编译测试不受影响；只有 Task 12 部署时才停服务。
- Windows Git Bash：PowerShell 一律写成脚本文件再执行；`taskkill` 用 `//PID`。
- jSerialComm 不在本地 Maven 仓库，Task 6 首次编译需联网从 Maven Central 下载。

---

## 文件结构总览

```
device-ops-core/
  src/main/java/com/dp/deviceops/core/model/ConnectionProtocol.java        # +SERIAL
  src/main/java/com/dp/deviceops/core/port/CommandExecutionPort.java       # +SerialParity/SerialFlowControl/SerialParams/SerialPrompts；ConnectionSpec 11 参
  src/main/java/com/dp/deviceops/core/model/ExecutionConnectionContext.java # ConnectionProjection +串口字段
  src/main/java/com/dp/deviceops/core/service/CollectionWorker.java        # connectionSpec() 透传串口字段
  src/test/java/com/dp/deviceops/core/port/ConnectionSpecSerialTest.java   # 新建
device-ops-adapter-web-spring/
  src/main/java/com/dp/deviceops/adapter/web/ConnectionRequestMapper.java  # Connection/SerialParams/SerialPrompts + directSpec
  src/main/java/com/dp/deviceops/adapter/web/CollectionController.java     # Target +串口字段与 SHELL 校验
  src/test/java/com/dp/deviceops/adapter/web/ConnectionRequestMapperSerialTest.java # 新建
  src/test/java/com/dp/deviceops/adapter/web/CollectionBoundaryTest.java   # Connection 构造 arity 更新
  src/test/java/com/dp/deviceops/adapter/web/CollectionRequestFingerprintTest.java # 同上
device-ops-adapter-persistence-jdbc/
  src/main/resources/db/migration/V21__add_saved_connection_serial_params.sql # 新建（H2）
  src/main/resources/db/mysql-migration/V1__create_device_ops_baseline.sql    # 表定义加列
  src/main/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcSavedConnectionStore.java # 读写串口列
  src/test/java/com/dp/deviceops/adapter/persistence/jdbc/SavedConnectionSerialPersistenceTest.java # 新建
device-ops-adapter-serial/                                                 # 新建模块
  pom.xml
  src/main/java/com/dp/deviceops/adapter/serial/SerialTransport.java
  src/main/java/com/dp/deviceops/adapter/serial/JdkSerialTransport.java
  src/main/java/com/dp/deviceops/adapter/serial/JdkSerialPortEnumerator.java
  src/main/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapter.java
  src/test/java/com/dp/deviceops/adapter/serial/InMemorySerialTransport.java
  src/test/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapterTest.java
device-ops-server/
  pom.xml                                                  # +adapter-serial 依赖
  src/main/java/com/dp/deviceops/server/SerialProperties.java        # 新建
  src/main/java/com/dp/deviceops/server/DeviceOpsWiringConfiguration.java # +串口 bean
  src/main/java/com/dp/deviceops/server/RuntimeConfigController.java # +serialEnabled
  src/main/java/com/dp/deviceops/server/ManagementSettingsController.java # Capabilities +serialEnabled
  src/main/java/com/dp/deviceops/server/SerialPortController.java    # 新建
  src/test/java/com/dp/deviceops/server/SerialWiringTest.java        # 新建
device-ops-web/src/
  types/collection.ts                                      # +SERIAL 类型分支
  types/management.ts                                      # capabilities +serialEnabled
  api/saved-connections.ts                                 # connection +串口字段
  api/serial-ports.ts                                      # 新建
  config/runtime.ts                                        # +serialEnabled
  utils/serial-connection.ts                               # 新建（选项与校验纯函数）
  stores/recent-connections.ts                             # +SERIAL
  components/ProtocolConnectionForm.vue                    # 串口分支 UI
  views/ProjectCollectionView.vue                          # SERIAL→SHELL 归一
  views/management/SettingsManagementView.vue              # 能力标签 +串口
  utils/serial-connection.spec.ts                          # 新建
  management/presentation.spec.ts                          # 追加串口源码断言
```

---

### Task 1: core —— SERIAL 协议枚举与 ConnectionSpec 校验

**Files:**
- Modify: `device-ops-core/src/main/java/com/dp/deviceops/core/model/ConnectionProtocol.java`
- Modify: `device-ops-core/src/main/java/com/dp/deviceops/core/port/CommandExecutionPort.java`
- Modify: `device-ops-core/src/main/java/com/dp/deviceops/core/model/ExecutionConnectionContext.java`
- Modify: `device-ops-core/src/main/java/com/dp/deviceops/core/service/CollectionWorker.java`
- Test: `device-ops-core/src/test/java/com/dp/deviceops/core/port/ConnectionSpecSerialTest.java`

- [ ] **Step 1: 写失败测试**

创建 `device-ops-core/src/test/java/com/dp/deviceops/core/port/ConnectionSpecSerialTest.java`：

```java
package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.ConnectionProtocol;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionSpecSerialTest {

    private static CommandExecutionPort.SerialParams params() {
        return new CommandExecutionPort.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE);
    }

    private static CommandExecutionPort.SerialPrompts prompts() {
        return CommandExecutionPort.SerialPrompts.defaults();
    }

    private static CommandExecutionPort.ConnectionSpec serialSpec(int port,
            CommandExecutionPort.SerialParams params, CommandExecutionPort.SerialPrompts prompts,
            CommandExecutionPort.AuthenticationType authenticationType,
            CommandExecutionPort.ExecutionMode executionMode, String fingerprint) {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COM3", port, "admin",
                authenticationType, executionMode, fingerprint, null, params, prompts, Duration.ofSeconds(15));
    }

    @Test
    void serialConnectionAcceptsZeroPortWithSerialConfiguration() {
        CommandExecutionPort.ConnectionSpec spec = serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null);
        assertEquals(ConnectionProtocol.SERIAL, spec.protocol());
        assertEquals(0, spec.port());
        assertEquals("COM3", spec.host());
    }

    @Test
    void serialConnectionRejectsNonZeroPort() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(9600, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsMissingSerialParams() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, null, prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsMissingSerialPrompts() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), null,
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsPrivateKeyAuthentication() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PRIVATE_KEY,
                CommandExecutionPort.ExecutionMode.SHELL, null));
    }

    @Test
    void serialConnectionRejectsExecMode() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null));
    }

    @Test
    void serialConnectionRejectsHostKeyFingerprint() {
        assertThrows(IllegalArgumentException.class, () -> serialSpec(0, params(), prompts(),
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, "SHA256:abc"));
    }

    @Test
    void serialConnectionRejectsTelnetPrompts() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null,
                CommandExecutionPort.TelnetPrompts.defaults(), params(), prompts(), Duration.ofSeconds(15)));
    }

    @Test
    void serialParamsDefaultsAre9600_8_None_1_None() {
        CommandExecutionPort.SerialParams defaults = CommandExecutionPort.SerialParams.defaults();
        assertEquals(9600, defaults.baudRate());
        assertEquals(8, defaults.dataBits());
        assertEquals(CommandExecutionPort.SerialParity.NONE, defaults.parity());
        assertEquals(1, defaults.stopBits());
        assertEquals(CommandExecutionPort.SerialFlowControl.NONE, defaults.flowControl());
    }

    @Test
    void serialParamsRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(0, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE));
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(9600, 9,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE));
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 3, CommandExecutionPort.SerialFlowControl.NONE));
    }

    @Test
    void serialPromptsDefaultsMirrorTelnetDefaults() {
        CommandExecutionPort.SerialPrompts defaults = CommandExecutionPort.SerialPrompts.defaults();
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().login(), defaults.login());
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().password(), defaults.password());
        assertEquals(CommandExecutionPort.TelnetPrompts.defaults().command(), defaults.command());
    }

    @Test
    void nonSerialProtocolsRejectSerialFields() {
        assertThrows(IllegalArgumentException.class, () -> new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "192.0.2.10", 22, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, null, params(), prompts(), Duration.ofSeconds(15)));
    }

    @Test
    void nineArgConstructorStillBuildsTelnetSpecWithoutSerialFields() {
        CommandExecutionPort.ConnectionSpec spec = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.TELNET, "192.0.2.10", 23, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null,
                CommandExecutionPort.TelnetPrompts.defaults(), Duration.ofSeconds(15));
        assertEquals(ConnectionProtocol.TELNET, spec.protocol());
    }
}
```

- [ ] **Step 2: 运行确认红**

Run: `mvn -pl device-ops-core test -Dtest=ConnectionSpecSerialTest`
Expected: 编译失败（`SERIAL`、`SerialParams` 等不存在）。

- [ ] **Step 3: 实现**

`ConnectionProtocol.java` 整文件替换为：

```java
package com.dp.deviceops.core.model;

public enum ConnectionProtocol {
    SSH2,
    TELNET,
    SERIAL
}
```

`CommandExecutionPort.java`：
(a) 在 `enum TelnetLineEnding {...}` 之后、`record ConnectionSpec` 之前加入：

```java
    enum SerialParity {
        NONE,
        EVEN,
        ODD,
        MARK,
        SPACE
    }

    enum SerialFlowControl {
        NONE,
        RTS_CTS,
        XON_XOFF
    }

    record SerialParams(int baudRate, int dataBits, SerialParity parity, int stopBits,
                        SerialFlowControl flowControl) {

        public SerialParams {
            if (baudRate < 1) {
                throw new IllegalArgumentException("baudRate must be positive");
            }
            if (dataBits < 5 || dataBits > 8) {
                throw new IllegalArgumentException("dataBits must be between 5 and 8");
            }
            parity = Objects.requireNonNull(parity, "parity");
            if (stopBits != 1 && stopBits != 2) {
                throw new IllegalArgumentException("stopBits must be 1 or 2");
            }
            flowControl = Objects.requireNonNull(flowControl, "flowControl");
        }

        public static SerialParams defaults() {
            return new SerialParams(9600, 8, SerialParity.NONE, 1, SerialFlowControl.NONE);
        }
    }

    record SerialPrompts(String login, String password, String command, TelnetLineEnding lineEnding) {

        public SerialPrompts {
            login = requireRegex(login, "login");
            password = requireRegex(password, "password");
            command = requireRegex(command, "command");
            lineEnding = lineEnding == null ? TelnetLineEnding.AUTO : lineEnding;
        }

        public SerialPrompts(String login, String password, String command) {
            this(login, password, command, TelnetLineEnding.AUTO);
        }

        public static SerialPrompts defaults() {
            return new SerialPrompts(
                    "(?i)(login|username)\\s*:\\s*$",
                    "(?i)password\\s*:\\s*$",
                    "[>#\\$]\\s*$",
                    TelnetLineEnding.AUTO);
        }
    }
```

(b) `ConnectionSpec` 整体替换为（canonical 11 参；保留 9 参/7 参重载，既有调用点零改动）：

```java
    record ConnectionSpec(
            ConnectionProtocol protocol,
            String host,
            int port,
            String username,
            AuthenticationType authenticationType,
            ExecutionMode executionMode,
            String expectedHostKeyFingerprint,
            TelnetPrompts telnetPrompts,
            SerialParams serialParams,
            SerialPrompts serialPrompts,
            Duration connectTimeout) {

        public ConnectionSpec {
            protocol = Objects.requireNonNull(protocol, "protocol");
            host = requireText(host, "host");
            username = requireText(username, "username");
            authenticationType = Objects.requireNonNull(authenticationType, "authenticationType");
            executionMode = Objects.requireNonNull(executionMode, "executionMode");
            connectTimeout = requirePositive(connectTimeout, "connectTimeout");
            if (protocol == ConnectionProtocol.SERIAL) {
                if (port != 0) {
                    throw new IllegalArgumentException("SERIAL requires port 0");
                }
                if (authenticationType != AuthenticationType.PASSWORD) {
                    throw new IllegalArgumentException("SERIAL requires password authentication");
                }
                if (executionMode != ExecutionMode.SHELL) {
                    throw new IllegalArgumentException("SERIAL requires shell execution mode");
                }
                if (expectedHostKeyFingerprint != null && !expectedHostKeyFingerprint.isBlank()) {
                    throw new IllegalArgumentException("SERIAL does not use a host key fingerprint");
                }
                if (telnetPrompts != null) {
                    throw new IllegalArgumentException("SERIAL does not use Telnet prompts");
                }
                serialParams = Objects.requireNonNull(serialParams, "serialParams");
                serialPrompts = Objects.requireNonNull(serialPrompts, "serialPrompts");
                expectedHostKeyFingerprint = null;
            } else {
                if (port < 1 || port > 65_535) {
                    throw new IllegalArgumentException("port is invalid");
                }
                if (serialParams != null || serialPrompts != null) {
                    throw new IllegalArgumentException(protocol + " does not use serial connection fields");
                }
                if (protocol == ConnectionProtocol.SSH2) {
                    expectedHostKeyFingerprint = expectedHostKeyFingerprint == null
                            || expectedHostKeyFingerprint.isBlank()
                            ? null : expectedHostKeyFingerprint.strip();
                } else {
                    if (authenticationType != AuthenticationType.PASSWORD) {
                        throw new IllegalArgumentException("TELNET requires password authentication");
                    }
                    if (executionMode != ExecutionMode.SHELL) {
                        throw new IllegalArgumentException("TELNET requires shell execution mode");
                    }
                    if (expectedHostKeyFingerprint != null && !expectedHostKeyFingerprint.isBlank()) {
                        throw new IllegalArgumentException("TELNET does not use a host key fingerprint");
                    }
                    expectedHostKeyFingerprint = null;
                    telnetPrompts = Objects.requireNonNull(telnetPrompts, "telnetPrompts");
                }
            }
        }

        public ConnectionSpec(ConnectionProtocol protocol, String host, int port, String username,
                              AuthenticationType authenticationType, ExecutionMode executionMode,
                              String expectedHostKeyFingerprint, TelnetPrompts telnetPrompts,
                              Duration connectTimeout) {
            this(protocol, host, port, username, authenticationType, executionMode,
                    expectedHostKeyFingerprint, telnetPrompts, null, null, connectTimeout);
        }

        public ConnectionSpec(String host, int port, String username, AuthenticationType authenticationType,
                              ExecutionMode executionMode, String expectedHostKeyFingerprint,
                              Duration connectTimeout) {
            this(ConnectionProtocol.SSH2, host, port, username, authenticationType, executionMode,
                    expectedHostKeyFingerprint, null, null, null, connectTimeout);
        }
    }
```

(c) `ExecutionConnectionContext.java`：`projection()` 与 `ConnectionProjection` 增加串口字段：

```java
    public ConnectionProjection projection() {
        return new ConnectionProjection(connection.protocol(), connection.host(), connection.port(), connection.username(),
                connection.authenticationType(), connection.executionMode(), connection.expectedHostKeyFingerprint(),
                connection.telnetPrompts(), connection.serialParams(), connection.serialPrompts(),
                connection.connectTimeout());
    }
```

```java
    public record ConnectionProjection(ConnectionProtocol protocol, String host, int port, String username,
                                       CommandExecutionPort.AuthenticationType authenticationType,
                                       CommandExecutionPort.ExecutionMode executionMode,
                                       String expectedHostKeyFingerprint,
                                       CommandExecutionPort.TelnetPrompts telnetPrompts,
                                       CommandExecutionPort.SerialParams serialParams,
                                       CommandExecutionPort.SerialPrompts serialPrompts,
                                       Duration connectTimeout) { }
```

(d) `CollectionWorker.java` 的 `connectionSpec()`（约 :149-156）替换为：

```java
        public CommandExecutionPort.ConnectionSpec connectionSpec() {
            ExecutionConnectionContext.ConnectionProjection projection = context.projection();
            return new CommandExecutionPort.ConnectionSpec(
                    projection.protocol(), projection.host(), projection.port(), projection.username(),
                    projection.authenticationType(), projection.executionMode(),
                    projection.expectedHostKeyFingerprint(), projection.telnetPrompts(),
                    projection.serialParams(), projection.serialPrompts(), projection.connectTimeout());
        }
```

- [ ] **Step 4: 运行确认绿（含 core 全量回归）**

Run: `mvn -pl device-ops-core test`
Expected: BUILD SUCCESS，全部通过（`ConnectionSpec` 既有测试走 9 参/7 参重载不受影响）。

- [ ] **Step 5: Commit**

```bash
git add device-ops-core
git commit -m "feat(device-ops-core): SERIAL 协议与串口连接校验"
```

---

### Task 2: web 层 —— ConnectionRequestMapper 串口字段

**Files:**
- Modify: `device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/ConnectionRequestMapper.java`
- Modify: `device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/CollectionController.java`
- Modify: `device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/CollectionBoundaryTest.java`（Connection 构造 arity）
- Modify: `device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/CollectionRequestFingerprintTest.java`（同上）
- Test: `device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/ConnectionRequestMapperSerialTest.java`（新建）

- [ ] **Step 1: 写失败测试**

创建 `ConnectionRequestMapperSerialTest.java`：

```java
package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionRequestMapperSerialTest {

    private final ConnectionRequestMapper mapper = new ConnectionRequestMapper(null);

    private static ConnectionRequestMapper.SerialParams serialParams() {
        return new ConnectionRequestMapper.SerialParams(9600, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE);
    }

    private static ConnectionRequestMapper.SerialPrompts serialPrompts() {
        return new ConnectionRequestMapper.SerialPrompts("(?i)(login|username)\\s*:\\s*$",
                "(?i)password\\s*:\\s*$", "[>#\\$]\\s*$", CommandExecutionPort.TelnetLineEnding.AUTO);
    }

    @Test
    void serialDirectRequestBuildsSpecWithZeroPort() {
        CommandExecutionPort.ConnectionSpec spec = mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, null, serialParams(), serialPrompts(),
                15L, null, null, null, "pw".toCharArray(), null, null));
        assertEquals(ConnectionProtocol.SERIAL, spec.protocol());
        assertEquals(0, spec.port());
        assertEquals("COM3", spec.host());
        assertEquals(9600, spec.serialParams().baudRate());
        assertEquals("[>#\\$]\\s*$", spec.serialPrompts().command());
        assertEquals(Duration.ofSeconds(15), spec.connectTimeout());
    }

    @Test
    void serialDirectRequestRejectsNonZeroPort() {
        assertThrows(ResponseStatusException.class, () -> mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SERIAL, "COM3", 23, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.SHELL, null, null, serialParams(), serialPrompts(),
                15L, null, null, null, "pw".toCharArray(), null, null)));
    }

    @Test
    void nonSerialDirectRequestStillRequiresPositivePort() {
        assertThrows(ResponseStatusException.class, () -> mapper.directSpec(new ConnectionRequestMapper.Connection(
                ConnectionProtocol.SSH2, "192.0.2.10", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD,
                CommandExecutionPort.ExecutionMode.EXEC, null, null, null, null,
                15L, null, null, null, "pw".toCharArray(), null, null)));
    }

    @Test
    void savedReferenceMustNotCarrySerialFields() {
        ConnectionRequestMapper.Connection request = new ConnectionRequestMapper.Connection(
                null, null, null, null, null, null, null, null, serialParams(), serialPrompts(),
                null, "standalone", "conn-1", null, null, null, null);
        assertThrows(ResponseStatusException.class, () -> mapper.map("owner-1", "standalone", request));
    }
}
```

- [ ] **Step 2: 运行确认红**

Run: `mvn -pl device-ops-adapter-web-spring test -Dtest=ConnectionRequestMapperSerialTest`
Expected: 编译失败（`SerialParams`/`SerialPrompts` web 记录不存在；`Connection` 构造 arity 不匹配）。

- [ ] **Step 3: 实现 ConnectionRequestMapper**

(a) `Connection` record（:210-241）在 `telnetPrompts` 之后插入 `serialParams`/`serialPrompts` 两个字段；辅助构造器相应补两个 `null`：

```java
    public record Connection(
            ConnectionProtocol protocol,
            @Size(max = 500) String host,
            Integer port,
            @Size(max = 500) String username,
            CommandExecutionPort.AuthenticationType authenticationType,
            CommandExecutionPort.ExecutionMode executionMode,
            @Size(max = 1000) String hostKeyFingerprint,
            @Valid TelnetPrompts telnetPrompts,
            @Valid SerialParams serialParams,
            @Valid SerialPrompts serialPrompts,
            Long connectTimeoutSeconds,
            @Size(max = 100) String credentialNamespace,
            @Size(max = 36) String savedConnectionId,
            @Size(max = 36) String credentialId,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] password,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] privateKey,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) char[] passphrase) {
        public Connection(ConnectionProtocol protocol, String host, int port, String username,
                          CommandExecutionPort.AuthenticationType authenticationType,
                          String hostKeyFingerprint, TelnetPrompts telnetPrompts,
                          long connectTimeoutSeconds, char[] password, char[] privateKey,
                          char[] passphrase) {
            this(protocol, host, port, username, authenticationType, CommandExecutionPort.ExecutionMode.SHELL,
                    hostKeyFingerprint, telnetPrompts, null, null,
                    connectTimeoutSeconds, null, null, null, password, privateKey, passphrase);
        }

        public void clearCredentials() {
            clear(password);
            clear(privateKey);
            clear(passphrase);
        }
    }
```

(b) 在 `TelnetPrompts` record 之后新增两个 web 记录（与 `TelnetPrompts` 同构，校验交给 core）：

```java
    public record SerialParams(Integer baudRate, Integer dataBits,
                               CommandExecutionPort.SerialParity parity,
                               Integer stopBits,
                               CommandExecutionPort.SerialFlowControl flowControl) {
        CommandExecutionPort.SerialParams toCore() {
            return new CommandExecutionPort.SerialParams(baudRate, dataBits, parity, stopBits, flowControl);
        }
    }

    public record SerialPrompts(@Size(max = 500) String login,
                                @Size(max = 500) String password,
                                @Size(max = 500) String command,
                                CommandExecutionPort.TelnetLineEnding lineEnding) {
        CommandExecutionPort.SerialPrompts toCore() {
            return new CommandExecutionPort.SerialPrompts(login, password, command, lineEnding);
        }
    }
```

(c) `directSpec`（:80-90）替换为：

```java
    public CommandExecutionPort.ConnectionSpec directSpec(Connection request) {
        try {
            return new CommandExecutionPort.ConnectionSpec(
                    requireProtocol(request), requireHost(request), requirePort(request), requireUsername(request),
                    requireAuthenticationType(request), requireExecutionMode(request),
                    request.hostKeyFingerprint(), request.telnetPrompts() == null ? null : request.telnetPrompts().toCore(),
                    request.serialParams() == null ? null : request.serialParams().toCore(),
                    request.serialPrompts() == null ? null : request.serialPrompts().toCore(),
                    Duration.ofSeconds(requireConnectTimeout(request)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
    }
```

(d) `requirePort`（:152-157）替换为：

```java
    private static int requirePort(Connection request) {
        if (request.protocol() == ConnectionProtocol.SERIAL) {
            if (request.port() == null || request.port() != 0) {
                throw new ResponseStatusException(BAD_REQUEST, "port must be 0 for a serial connection");
            }
            return request.port();
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            throw new ResponseStatusException(BAD_REQUEST, "port must be between 1 and 65535 for a direct connection");
        }
        return request.port();
    }
```

(e) `rejectSavedReferenceDirectFields`（:96-106）条件中 `request.telnetPrompts() != null` 之后追加 `|| request.serialParams() != null || request.serialPrompts() != null`：

```java
    private static void rejectSavedReferenceDirectFields(Connection request) {
        if (request.protocol() != null || request.host() != null || request.port() != null || request.username() != null
                || request.authenticationType() != null || request.executionMode() != null
                || request.hostKeyFingerprint() != null
                || request.telnetPrompts() != null || request.serialParams() != null || request.serialPrompts() != null
                || request.connectTimeoutSeconds() != null
                || request.credentialId() != null || request.password() != null || request.privateKey() != null
                || request.passphrase() != null) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "saved connection reference must not contain direct connection fields");
        }
    }
```

- [ ] **Step 4: 更新 CollectionController.Target 与既有测试 arity**

`CollectionController.java`：
(a) `Target` record（:180 起）在 `@Valid ConnectionRequestMapper.TelnetPrompts telnetPrompts,` 之后插入：

```java
                         @Valid ConnectionRequestMapper.SerialParams serialParams,
                         @Valid ConnectionRequestMapper.SerialPrompts serialPrompts,
```

(b) `toConnection()` 内执行方式校验（TELNET 分支 :196-198 之后）追加：

```java
                if (protocol == ConnectionProtocol.SERIAL && execution != CommandExecutionPort.ExecutionMode.SHELL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SERIAL requires SHELL execution mode");
                }
```

(c) `toConnection()` 末尾 `return new ConnectionRequestMapper.Connection(...)`（:226-228）替换为：

```java
            return new ConnectionRequestMapper.Connection(protocol, host, port, username, authentication,
                    execution, hostKeyFingerprint, telnetPrompts, serialParams, serialPrompts,
                    connectTimeoutSeconds, credentialNamespace,
                    savedConnectionId, credentialId, password, privateKey, passphrase);
```

`CollectionBoundaryTest.java` :62-63 的 `saved` 构造替换为（在 telnetPrompts 位置后补两个 null）：

```java
        var saved = new ConnectionRequestMapper.Connection(null, null, null, null, null, null,
                null, null, null, null, null, null, "saved-connection", null, secret, null, null);
```

`CollectionRequestFingerprintTest.java` :29-30 的 `saved` 构造替换为：

```java
        var saved = new ConnectionRequestMapper.Connection(null, null, null, null, null, null,
                null, null, null, null, null, null, "saved-1", null, null, null, null);
```

:38-40 的 `connection` 构造替换为：

```java
        var connection = new ConnectionRequestMapper.Connection(ConnectionProtocol.SSH2, "10.0.0.10", 22, "operator",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC,
                "SHA256:fixture", null, null, null, 10L, null, null, null,
                password == null ? null : password.toCharArray(), null, null);
```

- [ ] **Step 5: 运行确认绿（web 模块全量）**

Run: `mvn -pl device-ops-adapter-web-spring test`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add device-ops-adapter-web-spring
git commit -m "feat(device-ops-web): 连接请求映射支持串口参数"
```

---

### Task 3: 持久化 —— V21 迁移与保存连接串口列

**Files:**
- Create: `device-ops-adapter-persistence-jdbc/src/main/resources/db/migration/V21__add_saved_connection_serial_params.sql`
- Modify: `device-ops-adapter-persistence-jdbc/src/main/resources/db/mysql-migration/V1__create_device_ops_baseline.sql`（`device_ops_saved_connection` 表定义，telnet 列之后）
- Modify: `device-ops-adapter-persistence-jdbc/src/main/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcSavedConnectionStore.java`
- Test: `device-ops-adapter-persistence-jdbc/src/test/java/com/dp/deviceops/adapter/persistence/jdbc/SavedConnectionSerialPersistenceTest.java`（新建）

- [ ] **Step 1: 写失败测试**

参考 `CollectionPersistenceTest` 的 H2+Flyway 设施。创建 `SavedConnectionSerialPersistenceTest.java`：

```java
package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.model.SavedConnection;
import com.dp.deviceops.core.model.SavedConnectionDraft;
import com.dp.deviceops.core.model.TransientCredential;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.SavedConnectionCredentialStore;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedConnectionSerialPersistenceTest {

    private static DataSource migrated() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:serial-saved-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        return source;
    }

    private static JdbcSavedConnectionStore store(DataSource source, JdbcCredentialStore credentials) {
        return new JdbcSavedConnectionStore(JdbcClient.create(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), credentials, fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    }

    private static JdbcCredentialStore credentialStore(DataSource source) {
        return new JdbcCredentialStore(JdbcClient.create(source),
                new AesGcmCredentialCipher("0123456789abcdef0123456789abcdef"), fixedClock());
    }

    private static CommandExecutionPort.ConnectionSpec serialSpec() {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COM4", 0, "console-admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(20));
    }

    @Test
    void serialSavedConnectionRoundTripsSerialParamsAndPrompts() {
        DataSource source = migrated();
        JdbcCredentialStore credentials = credentialStore(source);
        JdbcSavedConnectionStore store = store(source, credentials);
        SavedConnection saved = store.create("owner-1", "standalone",
                new SavedConnectionDraft("console", null, serialSpec()),
                "console-secret".toCharArray(), null);
        SavedConnection reloaded = store.find("owner-1", "standalone", saved.id()).orElseThrow();
        CommandExecutionPort.ConnectionSpec connection = reloaded.connection();
        assertEquals(ConnectionProtocol.SERIAL, connection.protocol());
        assertEquals("COM4", connection.host());
        assertEquals(0, connection.port());
        assertEquals(9600, connection.serialParams().baudRate());
        assertEquals(CommandExecutionPort.SerialParity.NONE, connection.serialParams().parity());
        assertEquals("(?i)password\\s*:\\s*$", connection.serialPrompts().password());
        assertEquals(Duration.ofSeconds(20), connection.connectTimeout());
    }

    @Test
    void serialSavedConnectionReplaceUpdatesSerialColumns() {
        DataSource source = migrated();
        JdbcCredentialStore credentials = credentialStore(source);
        JdbcSavedConnectionStore store = store(source, credentials);
        SavedConnection saved = store.create("owner-1", "standalone",
                new SavedConnectionDraft("console", null, serialSpec()),
                "console-secret".toCharArray(), null);
        CommandExecutionPort.ConnectionSpec updated = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM5", 0, "console-admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, new CommandExecutionPort.SerialParams(115200, 8,
                CommandExecutionPort.SerialParity.NONE, 1, CommandExecutionPort.SerialFlowControl.NONE),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(20));
        store.replace("owner-1", "standalone", saved.id(), saved.version(),
                new SavedConnectionDraft("console", null, updated), null, null);
        SavedConnection reloaded = store.find("owner-1", "standalone", saved.id()).orElseThrow();
        assertEquals("COM5", reloaded.connection().host());
        assertEquals(115200, reloaded.connection().serialParams().baudRate());
    }
}
```

注意：`SavedConnectionCredentialStore` 的 import 若未被直接使用则删除；`AesGcmCredentialCipher` 构造密钥字节数以该类实际要求为准（32 字节 AES 密钥的字符串形式）——若构造签名不同，按 `JdbcCredentialStore` 既有测试/生产用法调整，不得为此改生产代码。

- [ ] **Step 2: 运行确认红**

Run: `mvn -pl device-ops-adapter-persistence-jdbc test -Dtest=SavedConnectionSerialPersistenceTest`
Expected: 失败（H2 报列不存在，或 mapRow 构造 arity 不匹配）。

- [ ] **Step 3: 写迁移与实现**

(a) 新建 `V21__add_saved_connection_serial_params.sql`：

```sql
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_baud_rate INTEGER;
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_data_bits INTEGER;
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_parity VARCHAR(16);
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_stop_bits INTEGER;
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_flow_control VARCHAR(16);
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_login_prompt VARCHAR(500);
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_password_prompt VARCHAR(500);
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_command_prompt VARCHAR(500);
ALTER TABLE device_ops_saved_connection ADD COLUMN serial_line_ending VARCHAR(16);
```

(b) `db/mysql-migration/V1__create_device_ops_baseline.sql` 中 `device_ops_saved_connection` 表定义的 `telnet_line_ending VARCHAR(16),` 行之后插入同结构列（MySQL 基线是全新安装的完整 schema，不加 V21）：

```sql
    serial_baud_rate INTEGER,
    serial_data_bits INTEGER,
    serial_parity VARCHAR(16),
    serial_stop_bits INTEGER,
    serial_flow_control VARCHAR(16),
    serial_login_prompt VARCHAR(500),
    serial_password_prompt VARCHAR(500),
    serial_command_prompt VARCHAR(500),
    serial_line_ending VARCHAR(16),
```

(c) `JdbcSavedConnectionStore.java`：
- `insert(...)`：INSERT 列清单在 `telnet_line_ending,` 后加 `serial_baud_rate, serial_data_bits, serial_parity, serial_stop_bits, serial_flow_control, serial_login_prompt, serial_password_prompt, serial_command_prompt, serial_line_ending,`；VALUES 相应加 `:serialBaudRate, :serialDataBits, :serialParity, :serialStopBits, :serialFlowControl, :serialLoginPrompt, :serialPasswordPrompt, :serialCommandPrompt, :serialLineEnding,`；方法体取串口值：

```java
        CommandExecutionPort.SerialParams serial = connection.serialParams();
        CommandExecutionPort.SerialPrompts serialPrompts = connection.serialPrompts();
```

参数绑定：

```java
                .param("serialBaudRate", serial == null ? null : serial.baudRate())
                .param("serialDataBits", serial == null ? null : serial.dataBits())
                .param("serialParity", serial == null ? null : serial.parity().name())
                .param("serialStopBits", serial == null ? null : serial.stopBits())
                .param("serialFlowControl", serial == null ? null : serial.flowControl().name())
                .param("serialLoginPrompt", serialPrompts == null ? null : serialPrompts.login())
                .param("serialPasswordPrompt", serialPrompts == null ? null : serialPrompts.password())
                .param("serialCommandPrompt", serialPrompts == null ? null : serialPrompts.command())
                .param("serialLineEnding", serialPrompts == null ? null : serialPrompts.lineEnding().name())
```

- `updateConnection(...)`：UPDATE SET 子句在 `telnet_line_ending=:lineEnding,` 后加同样的 9 列赋值，绑定同上。
- `mapRow(...)`：读串口列并改用 11 参构造：

```java
    private static ConnectionRow mapRow(ResultSet rs, int row) throws SQLException {
        CommandExecutionPort.TelnetPrompts prompts = rs.getString("telnet_login_prompt") == null ? null
                : new CommandExecutionPort.TelnetPrompts(rs.getString("telnet_login_prompt"),
                rs.getString("telnet_password_prompt"), rs.getString("telnet_command_prompt"),
                CommandExecutionPort.TelnetLineEnding.valueOf(rs.getString("telnet_line_ending")));
        CommandExecutionPort.SerialParams serial = rs.getString("serial_parity") == null ? null
                : new CommandExecutionPort.SerialParams(rs.getInt("serial_baud_rate"), rs.getInt("serial_data_bits"),
                CommandExecutionPort.SerialParity.valueOf(rs.getString("serial_parity")),
                rs.getInt("serial_stop_bits"),
                CommandExecutionPort.SerialFlowControl.valueOf(rs.getString("serial_flow_control")));
        CommandExecutionPort.SerialPrompts serialPrompts = rs.getString("serial_login_prompt") == null ? null
                : new CommandExecutionPort.SerialPrompts(rs.getString("serial_login_prompt"),
                rs.getString("serial_password_prompt"), rs.getString("serial_command_prompt"),
                CommandExecutionPort.TelnetLineEnding.valueOf(rs.getString("serial_line_ending")));
        CommandExecutionPort.ConnectionSpec connection = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.valueOf(rs.getString("protocol")), rs.getString("host"), rs.getInt("port"),
                rs.getString("username"), CommandExecutionPort.AuthenticationType.valueOf(rs.getString("authentication_type")),
                CommandExecutionPort.ExecutionMode.valueOf(rs.getString("execution_mode")),
                rs.getString("expected_host_key_fingerprint"), prompts, serial, serialPrompts,
                Duration.ofMillis(rs.getLong("connect_timeout_millis")));
        return new ConnectionRow(new SavedConnection(rs.getString("connection_id"), rs.getString("namespace"),
                rs.getString("display_name"), rs.getString("description"), connection, true, rs.getLong("version"),
                rs.getObject("created_at", Instant.class), rs.getObject("updated_at", Instant.class)),
                rs.getString("credential_id"));
    }
```

- `selectSql()` 列清单在 `telnet_line_ending,` 后追加 `serial_baud_rate, serial_data_bits, serial_parity, serial_stop_bits, serial_flow_control, serial_login_prompt, serial_password_prompt, serial_command_prompt, serial_line_ending,`。

- [ ] **Step 4: 运行确认绿（持久层全量）**

Run: `mvn -pl device-ops-adapter-persistence-jdbc test`
Expected: BUILD SUCCESS（既有表测试跑全量 V21 迁移不受影响，`target("17")` 的测试同样跳过 V21 无碍）。

- [ ] **Step 5: Commit**

```bash
git add device-ops-adapter-persistence-jdbc
git commit -m "feat(device-ops-persistence): 保存连接持久化串口参数"
```

---

### Task 4: adapter-serial 模块骨架与传输抽象

**Files:**
- Create: `device-ops-adapter-serial/pom.xml`
- Modify: `pom.xml`（根，`<modules>` 加一行）
- Create: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/SerialTransport.java`
- Create: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/JdkSerialTransport.java`
- Create: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/JdkSerialPortEnumerator.java`

- [ ] **Step 1: 创建模块 pom 与根 modules**

`device-ops-adapter-serial/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.dp.deviceops</groupId>
        <artifactId>device-ops-platform</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>device-ops-adapter-serial</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.dp.deviceops</groupId>
            <artifactId>device-ops-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fazecast</groupId>
            <artifactId>jSerialComm</artifactId>
            <version>2.11.0</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

根 `pom.xml` `<modules>` 在 `device-ops-adapter-telnet` 之后加：

```xml
        <module>device-ops-adapter-serial</module>
```

- [ ] **Step 2: 写传输抽象**

`SerialTransport.java`（读契约：阻塞至多 timeoutMillis，无数据返回 0；连接关闭抛 IOException）：

```java
package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.io.IOException;

/** Isolates jSerialComm behind a narrow transport seam; test doubles implement the same contract. */
public interface SerialTransport extends AutoCloseable {

    /** Exclusively opens the port with the given parameters; fails if the port does not exist or is busy. */
    void open(CommandExecutionPort.SerialParams params) throws IOException;

    /** Reads up to buffer.length bytes, blocking at most timeoutMillis; returns 0 when no data arrived. */
    int read(byte[] buffer, long timeoutMillis) throws IOException;

    void write(byte[] data) throws IOException;

    @Override
    void close();
}
```

`JdkSerialTransport.java`（生产实现，薄包装 jSerialComm）：

```java
package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;
import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/** Production SerialTransport over a local COM port via jSerialComm. */
public final class JdkSerialTransport implements SerialTransport {

    private final String comPort;
    private SerialPort port;

    public JdkSerialTransport(String comPort) {
        if (comPort == null || comPort.isBlank()) {
            throw new IllegalArgumentException("comPort is required");
        }
        this.comPort = comPort.strip();
    }

    @Override
    public void open(CommandExecutionPort.SerialParams params) throws IOException {
        SerialPort candidate = Arrays.stream(SerialPort.getCommPorts())
                .filter(existing -> existing.getSystemPortName().equalsIgnoreCase(comPort))
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            throw new IOException("serial port does not exist: " + comPort);
        }
        candidate.setBaudRate(params.baudRate());
        candidate.setNumDataBits(params.dataBits());
        candidate.setNumStopBits(params.stopBits());
        candidate.setParity(switch (params.parity()) {
            case NONE -> SerialPort.NO_PARITY;
            case EVEN -> SerialPort.EVEN_PARITY;
            case ODD -> SerialPort.ODD_PARITY;
            case MARK -> SerialPort.MARK_PARITY;
            case SPACE -> SerialPort.SPACE_PARITY;
        });
        candidate.setFlowControl(switch (params.flowControl()) {
            case NONE -> SerialPort.FLOW_CONTROL_DISABLED;
            case RTS_CTS -> SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case XON_XOFF -> SerialPort.FLOW_CONTROL_XONXOFF_ENABLED;
        });
        candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        if (!candidate.openPort()) {
            throw new IOException("serial port is busy or cannot be opened: " + comPort);
        }
        port = candidate;
    }

    @Override
    public int read(byte[] buffer, long timeoutMillis) throws IOException {
        SerialPort current = port;
        if (current == null || !current.isOpen()) {
            throw new IOException("serial port is closed: " + comPort);
        }
        int read = current.readBytes(buffer, timeoutMillis);
        return read > 0 ? read : 0;
    }

    @Override
    public void write(byte[] data) throws IOException {
        SerialPort current = port;
        if (current == null || !current.isOpen()) {
            throw new IOException("serial port is closed: " + comPort);
        }
        if (current.writeBytes(data, data.length) != data.length) {
            throw new IOException("serial port write failed: " + comPort);
        }
    }

    @Override
    public void close() {
        if (port != null) {
            port.closePort();
            port = null;
        }
    }

    String comPort() {
        return comPort.toLowerCase(Locale.ROOT);
    }
}
```

`JdkSerialPortEnumerator.java`（供 `GET /api/v1/serial-ports` 使用）：

```java
package com.dp.deviceops.adapter.serial;

import com.fazecast.jSerialComm.SerialPort;

import java.util.Arrays;
import java.util.List;

/** Lists local serial port system names; contains no sensitive information. */
public final class JdkSerialPortEnumerator {

    public List<String> list() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl device-ops-adapter-serial -am compile -DskipTests`
Expected: BUILD SUCCESS（首次会从 Maven Central 下载 jSerialComm 2.11.0，需要网络）。

- [ ] **Step 4: Commit**

```bash
git add pom.xml device-ops-adapter-serial
git commit -m "feat(device-ops-adapter-serial): 串口传输抽象与 jSerialComm 模块"
```

---

### Task 5: adapter-serial —— 内存替身与登录引擎（TDD）

**Files:**
- Create: `device-ops-adapter-serial/src/test/java/com/dp/deviceops/adapter/serial/InMemorySerialTransport.java`
- Create: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapter.java`（本任务先实现 protocol()/test()/login）
- Modify: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapter.java`（Task 6 补 execute）
- Test: `device-ops-adapter-serial/src/test/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapterTest.java`

- [ ] **Step 1: 写内存替身**

`InMemorySerialTransport.java`（脚本化响应队列 + 写入记录）：

```java
package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.port.CommandExecutionPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Scriptable in-memory serial transport for tests; no real COM port is touched. */
final class InMemorySerialTransport implements SerialTransport {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataArrived = lock.newCondition();
    private final Deque<byte[]> responses = new ArrayDeque<>();
    private final List<byte[]> written = new ArrayList<>();
    private boolean open;
    private boolean failOpen;
    private CommandExecutionPort.SerialParams openedWith;

    void scriptResponse(String text) {
        lock.lock();
        try {
            responses.add(text.getBytes(StandardCharsets.UTF_8));
            dataArrived.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void failNextOpen() {
        this.failOpen = true;
    }

    List<String> writtenLines() {
        lock.lock();
        try {
            return written.stream().map(bytes -> new String(bytes, StandardCharsets.UTF_8)).toList();
        } finally {
            lock.unlock();
        }
    }

    CommandExecutionPort.SerialParams openedWith() {
        lock.lock();
        try {
            return openedWith;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void open(CommandExecutionPort.SerialParams params) throws IOException {
        if (failOpen) {
            throw new IOException("serial port is busy or cannot be opened: COMTEST");
        }
        lock.lock();
        try {
            open = true;
            openedWith = params;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(byte[] buffer, long timeoutMillis) throws IOException {
        lock.lock();
        try {
            if (!open) {
                throw new IOException("serial port is closed: COMTEST");
            }
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000;
            while (responses.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return 0;
                }
                try {
                    dataArrived.awaitNanos(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while reading serial port", exception);
                }
            }
            byte[] head = responses.peek();
            int count = Math.min(head.length, buffer.length);
            System.arraycopy(head, 0, buffer, 0, count);
            if (count == head.length) {
                responses.poll();
            } else {
                responses.poll();
                byte[] rest = new byte[head.length - count];
                System.arraycopy(head, count, rest, 0, rest.length);
                responses.push(rest);
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        lock.lock();
        try {
            if (!open) {
                throw new IOException("serial port is closed: COMTEST");
            }
            written.add(data.clone());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            open = false;
        } finally {
            lock.unlock();
        }
    }
}
```

- [ ] **Step 2: 写登录失败测试（红）**

`SerialCommandExecutionAdapterTest.java`：

```java
package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerialCommandExecutionAdapterTest {

    private static final char[] PASSWORD = "console-secret".toCharArray();

    private static CommandExecutionPort.ConnectionSpec spec(CommandExecutionPort.SerialPrompts prompts) {
        return new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SERIAL, "COMTEST", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(), prompts, Duration.ofSeconds(5));
    }

    private static SerialCommandExecutionAdapter adapter() {
        return new SerialCommandExecutionAdapter(true, 8 * 1024 * 1024, 8192, 10_000);
    }

    @Test
    void testLoginSendsUsernameAndPasswordAndClosesPort() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport);
        assertEquals("admin\r", transport.writtenLines().get(0));
        assertEquals("console-secret\r", transport.writtenLines().get(1));
    }

    @Test
    void testRejectsWrongPassword() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("%Login invalid");
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport));
        assertEquals(ConnectionFailure.Code.AUTH_FAILED, failure.code());
    }

    @Test
    void testTimesOutWhenCommandPromptNeverAppears() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport));
        assertEquals(ConnectionFailure.Code.PROMPT_NOT_FOUND, failure.code());
    }

    @Test
    void testReportsMissingPort() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.failNextOpen();
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport));
        assertEquals(ConnectionFailure.Code.UNREACHABLE, failure.code());
        assertEquals(ConnectionFailure.Stage.CONNECT, failure.stage());
    }

    @Test
    void testDisabledAdapterFailsFast() {
        SerialCommandExecutionAdapter disabled = new SerialCommandExecutionAdapter(false, 8_388_608, 8_192, 10_000);
        ConnectionFailure failure = assertThrows(ConnectionFailure.class,
                () -> disabled.test(spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, null));
        assertEquals(ConnectionFailure.Code.PROTOCOL_DISABLED, failure.code());
    }

    @Test
    void testRejectsNonSerialConnection() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        CommandExecutionPort.ConnectionSpec ssh = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SSH2, "192.0.2.10", 22, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC,
                null, null, null, null, Duration.ofSeconds(5));
        assertThrows(ConnectionFailure.class,
                () -> adapter().testTransport(ssh, PASSWORD, transport));
    }

    @Test
    void adapterProtocolIsSerial() {
        assertEquals(ConnectionProtocol.SERIAL, adapter().protocol());
    }
}
```

- [ ] **Step 3: 运行确认红**

Run: `mvn -pl device-ops-adapter-serial test`
Expected: 编译失败（`SerialCommandExecutionAdapter` 不存在）。

- [ ] **Step 4: 实现适配器（登录 + test，execute 下任务补）**

`SerialCommandExecutionAdapter.java`：

```java
package com.dp.deviceops.adapter.serial;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.model.CommandPlan;
import com.dp.deviceops.core.model.CommandPlan.CommandSpec;
import com.dp.deviceops.core.model.ConnectionFailure;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import com.dp.deviceops.core.terminal.TerminalPagerController;
import com.dp.deviceops.core.terminal.TerminalTextProcessor;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SerialCommandExecutionAdapter implements ProtocolCommandExecutionAdapter {

    static final long READ_SLICE_MILLIS = 100;
    static final Duration PAGER_TERMINAL_SETTLE = Duration.ofMillis(250);
    private static final int PROMPT_CAPTURE_LIMIT = 8 * 1024;
    private static final String PROTOCOL_DISABLED_MESSAGE = "connection protocol is disabled";
    private static final String SERIAL_PORT_FAILED_MESSAGE = "serial port is unavailable";
    private static final String CONNECT_TIMEOUT_MESSAGE = "connection timed out";
    private static final String PROMPT_NOT_FOUND_MESSAGE = "expected serial console prompt was not found";
    private static final String AUTH_FAILED_MESSAGE = "authentication failed";
    private static final String CONNECTION_CLOSED_MESSAGE = "connection closed during command execution";

    private final boolean enabled;
    private final int maxOutputBytes;
    private final int eventChunkBytes;
    private final int maxPages;

    public SerialCommandExecutionAdapter(boolean enabled, int maxOutputBytes, int eventChunkBytes, int maxPages) {
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        if (eventChunkBytes < 1 || eventChunkBytes > 8_192) {
            throw new IllegalArgumentException("eventChunkBytes must be between 1 and 8192");
        }
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.enabled = enabled;
        this.maxOutputBytes = maxOutputBytes;
        this.eventChunkBytes = eventChunkBytes;
        this.maxPages = maxPages;
    }

    @Override
    public ConnectionProtocol protocol() {
        return ConnectionProtocol.SERIAL;
    }

    @Override
    public void test(ConnectionSpec connection, char[] secret, char[] passphrase) {
        requireEnabled();
        requireSerial(connection, passphrase);
        char[] password = copyRequiredSecret(secret);
        try (SerialTransport transport = new JdkSerialTransport(connection.host())) {
            login(connection, password, transport);
        } catch (IOException exception) {
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    SERIAL_PORT_FAILED_MESSAGE, exception);
        } finally {
            clear(password);
        }
    }

    /** Test seam: identical to test() but with an injected transport. */
    void testTransport(ConnectionSpec connection, char[] secret, SerialTransport transport) {
        requireEnabled();
        requireSerial(connection, null);
        char[] password = copyRequiredSecret(secret);
        try {
            login(connection, password, transport);
        } finally {
            clear(password);
        }
    }

    private void login(ConnectionSpec connection, char[] password, SerialTransport transport) {
        Deadline deadline = new Deadline(connection.connectTimeout());
        try {
            transport.open(connection.serialParams());
            Pattern loginPrompt = Pattern.compile(connection.serialPrompts().login());
            Pattern passwordPrompt = Pattern.compile(connection.serialPrompts().password());
            Pattern commandPrompt = Pattern.compile(connection.serialPrompts().command());
            byte[] lineEnding = lineEndingBytes(connection.serialPrompts().lineEnding());
            readLoginPrompt(transport, loginPrompt, deadline, "username");
            transport.write(join(connection.username(), lineEnding));
            readLoginPrompt(transport, passwordPrompt, deadline, "password");
            transport.write(join(password, lineEnding));
            try {
                readUntilPrompt(transport, commandPrompt, deadline);
            } catch (PromptRejected | PromptClosed exception) {
                throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                        AUTH_FAILED_MESSAGE, exception);
            } catch (PromptTimeout exception) {
                throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                        PROMPT_NOT_FOUND_MESSAGE, exception);
            }
        } catch (ConnectionFailure failure) {
            transport.close();
            throw failure;
        } catch (IOException | RuntimeException exception) {
            transport.close();
            if (deadline.expired()) {
                throw failure(ConnectionFailure.Code.CONNECT_TIMEOUT, ConnectionFailure.Stage.CONNECT,
                        CONNECT_TIMEOUT_MESSAGE, exception);
            }
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    SERIAL_PORT_FAILED_MESSAGE, exception);
        }
    }

    private static void readLoginPrompt(SerialTransport transport, Pattern prompt, Deadline deadline,
                                        String stage) {
        try {
            readUntilPrompt(transport, prompt, deadline);
        } catch (PromptRejected | PromptClosed exception) {
            throw failure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                    AUTH_FAILED_MESSAGE, exception);
        } catch (PromptTimeout exception) {
            throw failure(ConnectionFailure.Code.PROMPT_NOT_FOUND, ConnectionFailure.Stage.LOGIN,
                    PROMPT_NOT_FOUND_MESSAGE, exception);
        }
    }

    private static void readUntilPrompt(SerialTransport transport, Pattern prompt, Deadline deadline)
            throws PromptTimeout, PromptRejected, PromptClosed {
        StringBuilder window = new StringBuilder();
        byte[] buffer = new byte[1_024];
        while (true) {
            int read;
            try {
                read = transport.read(buffer, READ_SLICE_MILLIS);
            } catch (IOException exception) {
                throw new PromptClosed(window.toString());
            }
            if (read > 0) {
                window.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
                if (window.length() > PROMPT_CAPTURE_LIMIT) {
                    window.delete(0, window.length() - PROMPT_CAPTURE_LIMIT);
                }
                if (prompt.matcher(window).find()) {
                    return;
                }
                if (isRejection(window)) {
                    throw new PromptRejected(window.toString());
                }
            }
            deadline.check();
        }
    }

    private static boolean isRejection(CharSequence window) {
        String lower = window.toString().toLowerCase(Locale.ROOT);
        return lower.contains("invalid") || lower.contains("incorrect")
                || lower.contains("denied") || lower.contains("fail")
                || lower.contains("错误") || lower.contains("失败");
    }

    static byte[] lineEndingBytes(CommandExecutionPort.TelnetLineEnding lineEnding) {
        return switch (lineEnding == null ? CommandExecutionPort.TelnetLineEnding.AUTO : lineEnding) {
            case CRLF -> new byte[]{'\r', '\n'};
            case CR, AUTO -> new byte[]{'\r'};
            case LF -> new byte[]{'\n'};
        };
    }

    private static byte[] join(char[] value, byte[] lineEnding) {
        byte[] text = new String(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] joined = new byte[text.length + lineEnding.length];
        System.arraycopy(text, 0, joined, 0, text.length);
        System.arraycopy(lineEnding, 0, joined, text.length, lineEnding.length);
        return joined;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    PROTOCOL_DISABLED_MESSAGE);
        }
    }

    private static void requireSerial(ConnectionSpec connection, char[] passphrase) {
        Objects.requireNonNull(connection, "connection");
        if (connection.protocol() != ConnectionProtocol.SERIAL) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "connection protocol is not serial");
        }
        if (connection.serialPrompts() == null || connection.serialParams() == null) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "serial connection is incomplete");
        }
        if (passphrase != null && passphrase.length > 0) {
            throw new ConnectionFailure(ConnectionFailure.Code.PROTOCOL_DISABLED, ConnectionFailure.Stage.CONNECT,
                    "serial connections do not use a key passphrase");
        }
    }

    private static char[] copyRequiredSecret(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new ConnectionFailure(ConnectionFailure.Code.AUTH_FAILED, ConnectionFailure.Stage.AUTHENTICATE,
                    "credential secret is required");
        }
        char[] copy = Arrays.copyOf(secret, secret.length);
        Arrays.fill(secret, '\0');
        return copy;
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static ConnectionFailure failure(ConnectionFailure.Code code, ConnectionFailure.Stage stage,
                                             String message, Throwable cause) {
        return new ConnectionFailure(code, stage, message, cause);
    }

    private static final class PromptTimeout extends IOException {
    }

    private static final class PromptRejected extends IOException {
        private PromptRejected(String partial) {
            super(partial);
        }
    }

    private static final class PromptClosed extends IOException {
        private PromptClosed(String partial) {
            super(partial);
        }
    }

    private static final class DeadlineExpired extends RuntimeException {
    }

    private static final class Deadline {
        private final long deadlineNanos;

        private Deadline(Duration timeout) {
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        private long remainingMillis() {
            return Math.max(0, (deadlineNanos - System.nanoTime()) / 1_000_000);
        }

        private boolean expired() {
            return System.nanoTime() >= deadlineNanos;
        }

        private void check() {
            if (expired()) {
                throw new DeadlineExpired();
            }
        }
    }
}
```

注意：`login` 中 `DeadlineExpired` 从 `deadline.check()` 抛出时属于 RuntimeException，落入 `catch (IOException | RuntimeException)` 分支按 CONNECT_TIMEOUT 处理，符合语义。`testTransport` 名为包私有测试通道，生产入口仍是 `test()`。

- [ ] **Step 5: 运行确认绿**

Run: `mvn -pl device-ops-adapter-serial test`
Expected: BUILD SUCCESS，7 个测试全绿。

- [ ] **Step 6: Commit**

```bash
git add device-ops-adapter-serial
git commit -m "feat(device-ops-adapter-serial): 串口登录与提示符引擎"
```

---

### Task 6: adapter-serial —— execute 命令循环与输出累积

**Files:**
- Modify: `device-ops-adapter-serial/src/main/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapter.java`（加 execute/内部类）
- Modify: `device-ops-adapter-serial/src/test/java/com/dp/deviceops/adapter/serial/SerialCommandExecutionAdapterTest.java`（追加测试）

- [ ] **Step 1: 追加失败测试**

在 `SerialCommandExecutionAdapterTest.java` 追加：

```java
    @Test
    void executeSplitsCommandBlocksAndReturnsResult() {
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        transport.scriptResponse("show version\r\nIOS XE 17.9");
        transport.scriptResponse("DEVICE>");
        CommandExecutionPort.CommandResult result = adapter().executeTransport(
                spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport,
                "show version", Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertEquals(1, result.commandBlocks().size());
        CommandOutputBlock block = result.commandBlocks().get(0);
        assertEquals("show version", block.commandText());
        assertEquals(CommandBlockStatus.SUCCEEDED, block.status());
        assertEquals(true, block.stdout().contains("IOS XE 17.9"));
    }

    @Test
    void executeReportsProgressAndTruncatesWhenOverLimit() {
        SerialCommandExecutionAdapter small = new SerialCommandExecutionAdapter(true, 16, 8, 10_000);
        InMemorySerialTransport transport = new InMemorySerialTransport();
        transport.scriptResponse("Username:");
        transport.scriptResponse("Password:");
        transport.scriptResponse("DEVICE>");
        transport.scriptResponse("A".repeat(64));
        transport.scriptResponse("DEVICE>");
        CommandExecutionPort.CommandResult result = small.executeTransport(
                spec(CommandExecutionPort.SerialPrompts.defaults()), PASSWORD, transport,
                "show log", Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertEquals(true, result.truncated());
    }
```

- [ ] **Step 2: 运行确认红**

Run: `mvn -pl device-ops-adapter-serial test`
Expected: 编译失败（`executeTransport` 不存在）。

- [ ] **Step 3: 实现 execute**

在 `SerialCommandExecutionAdapter` 中追加（`test`/`testTransport` 之后；复用 core 的 `TerminalTextProcessor`/`TerminalPagerController`/`CommandPlan`）：

```java
    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                 String script, Duration timeout) {
        return execute(connection, secret, passphrase, script, timeout, ProgressListener.noop());
    }

    @Override
    public CommandResult execute(ConnectionSpec connection, char[] secret, char[] passphrase,
                                 String script, Duration timeout, ProgressListener listener) {
        requireEnabled();
        requireSerial(connection, passphrase);
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(listener, "listener");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        char[] password = copyRequiredSecret(secret);
        try (SerialTransport transport = new JdkSerialTransport(connection.host())) {
            return executeTransport(connection, password, transport, script, timeout, listener);
        } catch (IOException exception) {
            throw failure(ConnectionFailure.Code.UNREACHABLE, ConnectionFailure.Stage.CONNECT,
                    SERIAL_PORT_FAILED_MESSAGE, exception);
        } finally {
            clear(password);
        }
    }

    CommandResult executeTransport(ConnectionSpec connection, char[] secret, SerialTransport transport,
                                   String script, Duration timeout) {
        return executeTransport(connection, secret, transport, script, timeout, ProgressListener.noop());
    }

    private CommandResult executeTransport(ConnectionSpec connection, char[] password, SerialTransport transport,
                                           String script, Duration timeout, ProgressListener listener) {
        long started = System.nanoTime();
        OutputAccumulator output = new OutputAccumulator(maxOutputBytes, eventChunkBytes);
        try {
            login(connection, password, transport);
            Deadline deadline = new Deadline(timeout);
            Pattern commandPrompt = Pattern.compile(connection.serialPrompts().command());
            byte[] lineEnding = lineEndingBytes(connection.serialPrompts().lineEnding());
            ExecutionState execution = new ExecutionState(commandPrompt, maxPages);
            TerminalPagerController pager = new TerminalPagerController(PAGER_TERMINAL_SETTLE, maxPages);
            byte[] buffer = new byte[eventChunkBytes];
            CommandPlan commandPlan = CommandPlan.fromScript(script);
            output.initialize(commandPlan);
            for (CommandSpec command : commandPlan.commands()) {
                execution.startCommand(command);
                output.startCommand(command);
                transport.write(join(command.commandText().toCharArray(), lineEnding));
                while (true) {
                    int read = readSlice(transport, buffer, deadline);
                    if (read == 0) {
                        if (pager.continuationReady()) {
                            transport.write(new byte[]{' '});
                            pager.markContinuationSent();
                            execution.updatePageCount(pager.pageCount());
                        }
                        continue;
                    }
                    TerminalTextProcessor.Decision decision = execution.accept(buffer, read);
                    pager.observe(decision);
                    execution.updatePageCount(pager.pageCount());
                    if (output.emit(execution.filter(decision.output()), listener, execution.pageCount())) {
                        return result(0, output, false, started);
                    }
                    if (decision.promptReached()) {
                        if (output.emit(execution.finishCommand(), listener, execution.pageCount())) {
                            return result(0, output, false, started);
                        }
                        output.finishCommand(CommandBlockStatus.SUCCEEDED, 0, null, execution.pageCount());
                        break;
                    }
                }
            }
            return result(0, output, false, started);
        } catch (ProgressListenerFailure failure) {
            throw failure.original();
        } catch (DeadlineExpired timeoutFailure) {
            return result(-1, output, true, started);
        } catch (ConnectionFailure failure) {
            transport.close();
            throw failure;
        } catch (IOException exception) {
            transport.close();
            throw failure(ConnectionFailure.Code.CONNECTION_CLOSED, ConnectionFailure.Stage.EXECUTE,
                    CONNECTION_CLOSED_MESSAGE, exception);
        }
    }

    private static int readSlice(SerialTransport transport, byte[] buffer, Deadline deadline) throws IOException {
        int read = transport.read(buffer, READ_SLICE_MILLIS);
        if (read > 0) {
            return read;
        }
        deadline.check();
        return 0;
    }

    private static CommandResult result(int exitCode, OutputAccumulator output, boolean timedOut, long started) {
        return new CommandResult(exitCode, output.stdout(), "", timedOut, output.truncated(),
                (System.nanoTime() - started) / 1_000_000, output.blocks());
    }
```

同文件追加内部类（`Deadline` 之前）：

```java
    private static final class ExecutionState {
        private final TerminalTextProcessor terminal;
        private String pendingEchoCommand;
        private boolean finished;
        private int pageCount;

        private ExecutionState(Pattern commandPrompt, int maxPages) {
            terminal = new TerminalTextProcessor(commandPrompt, maxPages);
        }

        private void startCommand(CommandSpec command) {
            pendingEchoCommand = command.commandText();
            pageCount = 0;
        }

        private TerminalTextProcessor.Decision accept(byte[] bytes, int length) {
            TerminalTextProcessor.Decision decision = terminal.accept(bytes, length);
            pageCount = decision.pageCount();
            return decision;
        }

        private String filter(String text) {
            String command = pendingEchoCommand;
            if (command == null || text.isEmpty()) {
                return text;
            }
            return text.replace(command.strip(), "");
        }

        private String finishCommand() {
            pendingEchoCommand = null;
            return "";
        }

        private void updatePageCount(int observedPageCount) {
            pageCount = Math.max(pageCount, observedPageCount);
        }

        private int pageCount() {
            return pageCount;
        }
    }

    private static final class OutputAccumulator {
        private final int limit;
        private final int chunkBytes;
        private final StringBuilder text = new StringBuilder();
        private final List<CommandOutputBlock> blocks = new ArrayList<>();
        private final List<CommandOutputBlock> current = new ArrayList<>();
        private boolean truncated;
        private long receivedBytes;

        private OutputAccumulator(int limit, int chunkBytes) {
            this.limit = limit;
            this.chunkBytes = chunkBytes;
        }

        private void initialize(CommandPlan plan) {
            for (CommandSpec command : plan.commands()) {
                blocks.add(pending(command));
            }
        }

        private static CommandOutputBlock pending(CommandSpec command) {
            return new CommandOutputBlock(command.commandIndex(), command.commandText(),
                    CommandBlockStatus.PENDING, "", "", 0, 0, false, null, null, java.util.Map.of(),
                    java.util.List.of(), null, null, false);
        }

        private void startCommand(CommandSpec command) {
            current.clear();
        }

        private boolean emit(String content, ProgressListener listener, int pageCount) {
            if (content == null || content.isEmpty()) {
                return truncated;
            }
            byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            receivedBytes += bytes.length;
            if (receivedBytes > limit) {
                truncated = true;
                return true;
            }
            text.append(content);
            CommandOutputBlock live = new CommandOutputBlock(currentBlockIndex(), currentCommandText(),
                    CommandBlockStatus.RUNNING, text.toString(), "", receivedBytes, pageCount, false,
                    null, null, java.util.Map.of(), java.util.List.of(), null, null, false);
            current.clear();
            current.add(live);
            try {
                listener.onProgress(new OutputProgress(currentBlockIndex(),
                        OutputStreamType.STDOUT, chunk(content), receivedBytes, pageCount, truncated));
            } catch (RuntimeException failure) {
                throw new ProgressListenerFailure(failure);
            }
            return false;
        }

        private void finishCommand(CommandBlockStatus status, Integer exitCode, String outcome, int pageCount) {
            CommandOutputBlock block = new CommandOutputBlock(currentBlockIndex(), currentCommandText(),
                    status, text.toString(), "", receivedBytes, pageCount, truncated, exitCode, outcome,
                    java.util.Map.of(), java.util.List.of(), null, null, false);
            if (blocks.isEmpty()) {
                blocks.add(block);
            } else {
                blocks.set(currentBlockIndex() - 1, block);
            }
            text.setLength(0);
        }

        private int currentBlockIndex() {
            return blocks.size();
        }

        private String currentCommandText() {
            return blocks.isEmpty() ? "" : blocks.get(blocks.size() - 1).commandText();
        }

        private String chunk(String content) {
            return content.length() <= chunkBytes ? content : content.substring(0, chunkBytes);
        }

        private String stdout() {
            return text.toString();
        }

        private boolean truncated() {
            return truncated;
        }

        private List<CommandOutputBlock> blocks() {
            return List.copyOf(blocks);
        }
    }

    private static final class ProgressListenerFailure extends RuntimeException {
        private final RuntimeException original;

        private ProgressListenerFailure(RuntimeException original) {
            this.original = original;
        }

        private RuntimeException original() {
            return original;
        }
    }
```

并在文件头部 import 区补：

```java
import com.dp.deviceops.core.model.ConnectionFailure; // 已有
import java.util.ArrayList;
import java.util.List;
```

（`ArrayList`/`List` 若已 import 则跳过。）

- [ ] **Step 4: 运行确认绿**

Run: `mvn -pl device-ops-adapter-serial test`
Expected: BUILD SUCCESS，9 个测试全绿。

- [ ] **Step 5: Commit**

```bash
git add device-ops-adapter-serial
git commit -m "feat(device-ops-adapter-serial): 串口命令执行循环与输出累积"
```

---

### Task 7: server 装配 —— 属性、Bean、runtime-config、capabilities、serial-ports 端点

**Files:**
- Create: `device-ops-server/src/main/java/com/dp/deviceops/server/SerialProperties.java`
- Create: `device-ops-server/src/main/java/com/dp/deviceops/server/SerialPortController.java`
- Modify: `device-ops-server/src/main/java/com/dp/deviceops/server/DeviceOpsWiringConfiguration.java`
- Modify: `device-ops-server/src/main/java/com/dp/deviceops/server/RuntimeConfigController.java`
- Modify: `device-ops-server/src/main/java/com/dp/deviceops/server/ManagementSettingsController.java`
- Modify: `device-ops-server/pom.xml`
- Modify: `device-ops-server/src/main/resources/application.yml`（若其中显式列出 telnet 开关默认值；先 `grep -n "telnet" device-ops-server/src/main/resources/application.yml` 确认，再按同样格式补 `device-ops.serial.enabled: true`）
- Test: `device-ops-server/src/test/java/com/dp/deviceops/server/SerialWiringTest.java`

- [ ] **Step 1: 写失败测试**

`SerialWiringTest.java`：

```java
package com.dp.deviceops.server;

import com.dp.deviceops.adapter.serial.SerialCommandExecutionAdapter;
import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import com.dp.deviceops.core.port.ProtocolCommandExecutionAdapter;
import com.dp.deviceops.core.service.ProtocolCommandExecutionRouter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SerialWiringTest {

    @Test
    void routerRoutesSerialConnectionsToSerialAdapter() {
        SerialCommandExecutionAdapter serial = new SerialCommandExecutionAdapter(true, 8_388_608, 8_192, 10_000);
        ProtocolCommandExecutionRouter router = new ProtocolCommandExecutionRouter(List.of(serial));
        CommandExecutionPort.ConnectionSpec spec = new CommandExecutionPort.ConnectionSpec(
                ConnectionProtocol.SERIAL, "COM3", 0, "admin",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.SHELL,
                null, null, CommandExecutionPort.SerialParams.defaults(),
                CommandExecutionPort.SerialPrompts.defaults(), Duration.ofSeconds(5));
        assertSame(serial, routerAdapter(router, spec));
    }

    private ProtocolCommandExecutionAdapter routerAdapter(ProtocolCommandExecutionRouter router,
                                                          CommandExecutionPort.ConnectionSpec spec) {
        try {
            var field = ProtocolCommandExecutionRouter.class.getDeclaredField("adapters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var adapters = (java.util.Map<ConnectionProtocol, ProtocolCommandExecutionAdapter>) field.get(router);
            return adapters.get(spec.protocol());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void serialPropertiesDefaultsAreEnabledWithTelnetSizedLimits() {
        SerialProperties properties = new SerialProperties();
        assertEquals(true, properties.isEnabled());
        assertEquals(8_388_608, properties.getMaxOutputBytes());
        assertEquals(8_192, properties.getEventChunkBytes());
        assertEquals(10_000, properties.getMaxPages());
    }
}
```

- [ ] **Step 2: 运行确认红**

Run: `mvn -pl device-ops-server test -Dtest=SerialWiringTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（`SerialProperties` 不存在；server 尚不依赖 adapter-serial）。

- [ ] **Step 3: 实现**

(a) `device-ops-server/pom.xml` 在 telnet 依赖行后加：

```xml
        <dependency><groupId>com.dp.deviceops</groupId><artifactId>device-ops-adapter-serial</artifactId><version>${revision}</version></dependency>
```

(b) `SerialProperties.java`（镜像 `TelnetProperties`；enabled 默认 true）：

```java
package com.dp.deviceops.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("device-ops.serial")
public class SerialProperties {

    private boolean enabled = true;
    private int maxOutputBytes = 8_388_608;
    private int eventChunkBytes = 8_192;
    private int maxPages = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        if (maxOutputBytes < 1) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getEventChunkBytes() {
        return eventChunkBytes;
    }

    public void setEventChunkBytes(int eventChunkBytes) {
        if (eventChunkBytes < 1 || eventChunkBytes > 8_192) {
            throw new IllegalArgumentException("eventChunkBytes must be between 1 and 8192");
        }
        this.eventChunkBytes = eventChunkBytes;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be positive");
        }
        this.maxPages = maxPages;
    }
}
```

(c) `DeviceOpsWiringConfiguration.java`：import 区加 `import com.dp.deviceops.adapter.serial.SerialCommandExecutionAdapter;`；在 `telnetCommandExecutionAdapter` bean（:181-189）之后加：

```java
    @Bean ProtocolCommandExecutionAdapter serialCommandExecutionAdapter(SerialProperties properties) {
        return new SerialCommandExecutionAdapter(
                properties.isEnabled(),
                properties.getMaxOutputBytes(),
                properties.getEventChunkBytes(),
                properties.getMaxPages());
    }
```

（`commandExecutionPort(List<ProtocolCommandExecutionAdapter>)` 自动纳入，路由零改动。）

(d) `RuntimeConfigController.java` 整文件替换：

```java
package com.dp.deviceops.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeConfigController {
    private final RuntimeConfigProperties properties;
    private final TelnetProperties telnet;
    private final SerialProperties serial;
    public RuntimeConfigController(RuntimeConfigProperties properties, TelnetProperties telnet, SerialProperties serial) {
        this.properties = properties;
        this.telnet = telnet;
        this.serial = serial;
    }
    @GetMapping("/api/v1/runtime-config")
    public RuntimeConfig get() {
        return new RuntimeConfig(properties.getAuthMode(), properties.getOidcAuthority(), properties.getClientId(), properties.getScope(),
                properties.getApiBaseUrl(), properties.getProjectClaim(), telnet.isEnabled(), serial.isEnabled());
    }
    public record RuntimeConfig(String authMode, String oidcAuthority, String oidcClientId, String oidcScope,
                                String apiBaseUrl, String projectClaim, boolean telnetEnabled, boolean serialEnabled) { }
}
```

(e) `ManagementSettingsController.java`：
- 构造器注入 `SerialProperties serial`（新增参数与字段）；
- `Capabilities` record 变为：

```java
    public record Capabilities(boolean scheduleEnabled, boolean callbackEnabled, boolean masterDataEnabled,
                               boolean telnetEnabled, boolean serialEnabled, boolean credentialStorageAvailable) { }
```

- `settings(...)` 中 `new Capabilities(schedule.isEnabled(), callback.isEnabled(), masterData.isEnabled(), telnet.isEnabled(), credentials.available())` 替换为：

```java
                new Capabilities(schedule.isEnabled(), callback.isEnabled(), masterData.isEnabled(),
                        telnet.isEnabled(), serial.isEnabled(), credentials.available()));
```

(f) `SerialPortController.java`：

```java
package com.dp.deviceops.server;

import com.dp.deviceops.adapter.serial.JdkSerialPortEnumerator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lists local serial port names for the connection form dropdown; no sensitive information. */
@RestController
public class SerialPortController {
    private final JdkSerialPortEnumerator ports;
    private final SerialProperties properties;

    public SerialPortController(JdkSerialPortEnumerator ports, SerialProperties properties) {
        this.ports = ports;
        this.properties = properties;
    }

    @GetMapping("/api/v1/serial-ports")
    @PreAuthorize("hasAuthority('SCOPE_device-ops:collections:read')")
    public SerialPorts list() {
        return new SerialPorts(properties.isEnabled() ? ports.list() : List.of());
    }

    public record SerialPorts(List<String> ports) { }
}
```

并在 `DeviceOpsWiringConfiguration` 加：

```java
    @Bean JdkSerialPortEnumerator jdkSerialPortEnumerator() { return new JdkSerialPortEnumerator(); }
```

（import `com.dp.deviceops.adapter.serial.JdkSerialPortEnumerator`。）

- [ ] **Step 4: 运行确认绿（server 全量）**

Run: `mvn -pl device-ops-server test -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=SerialWiringTest,CollectionBoundaryTest,ConnectionRequestMapperSerialTest,SavedConnectionSerialPersistenceTest,ConnectionSpecSerialTest`
Expected: BUILD SUCCESS（一次回归跨模块相关测试；`-am` 连带编译上游模块）。

- [ ] **Step 5: 全量后端回归**

Run: `mvn test`
Expected: BUILD SUCCESS。若 `CollectionRequestFingerprintTest` 有针对 Connection 序列化的指纹断言失败，按失败信息把 serialParams/serialPrompts 纳入指纹字段清单后重跑（这是行为正确性要求：串口连接的指纹必须区分参数）。

- [ ] **Step 6: Commit**

```bash
git add pom.xml device-ops-server device-ops-adapter-web-spring device-ops-adapter-persistence-jdbc device-ops-core
git commit -m "feat(device-ops-server): 串口适配器装配与 serial-ports 端点"
```

---

### Task 8: 前端 —— 类型、API 与纯函数

**Files:**
- Modify: `device-ops-web/src/types/collection.ts`
- Modify: `device-ops-web/src/types/management.ts`
- Modify: `device-ops-web/src/api/saved-connections.ts`
- Create: `device-ops-web/src/api/serial-ports.ts`
- Modify: `device-ops-web/src/config/runtime.ts`
- Create: `device-ops-web/src/utils/serial-connection.ts`
- Modify: `device-ops-web/src/stores/recent-connections.ts`
- Test: `device-ops-web/src/utils/serial-connection.spec.ts`（新建）

- [ ] **Step 1: 写失败测试**

`serial-connection.spec.ts`：

```typescript
import { describe, expect, it } from 'vitest'

import {
  SERIAL_BAUD_RATES,
  SERIAL_PARITY_OPTIONS,
  SERIAL_STOP_BIT_OPTIONS,
  SERIAL_FLOW_CONTROL_OPTIONS,
  DEFAULT_SERIAL_PARAMS,
  DEFAULT_SERIAL_PROMPTS,
  serialParamFields
} from '@/utils/serial-connection'

describe('serial connection options', () => {
  it('offers console-standard baud rates with 9600 default', () => {
    expect(SERIAL_BAUD_RATES).toContain(9600)
    expect(SERIAL_BAUD_RATES[0]).toBe(4800)
    expect(SERIAL_BAUD_RATES.at(-1)).toBe(115200)
    expect(DEFAULT_SERIAL_PARAMS.baudRate).toBe(9600)
    expect(DEFAULT_SERIAL_PARAMS.dataBits).toBe(8)
    expect(DEFAULT_SERIAL_PARAMS.parity).toBe('NONE')
    expect(DEFAULT_SERIAL_PARAMS.stopBits).toBe(1)
    expect(DEFAULT_SERIAL_PARAMS.flowControl).toBe('NONE')
  })

  it('provides chinese labels for parity and flow control', () => {
    expect(SERIAL_PARITY_OPTIONS.find((option) => option.value === 'NONE')?.label).toContain('无校验')
    expect(SERIAL_FLOW_CONTROL_OPTIONS.find((option) => option.value === 'NONE')?.label).toContain('无')
  })

  it('serializes param fields with stop-bit options', () => {
    expect(serialParamFields()).toEqual(
      expect.arrayContaining(['baudRate', 'dataBits', 'parity', 'stopBits', 'flowControl'])
    )
    expect(SERIAL_STOP_BIT_OPTIONS.map((option) => option.value)).toEqual([1, 2])
  })

  it('defaults prompts mirror telnet defaults', () => {
    expect(DEFAULT_SERIAL_PROMPTS.login).toContain('login')
    expect(DEFAULT_SERIAL_PROMPTS.lineEnding).toBe('AUTO')
  })
})
```

- [ ] **Step 2: 运行确认红**

Run: `cd device-ops-web && pnpm test -- src/utils/serial-connection.spec.ts`
Expected: FAIL（模块不存在）。

- [ ] **Step 3: 实现**

`utils/serial-connection.ts`：

```typescript
export interface SerialParams {
  baudRate: number
  dataBits: number
  parity: 'NONE' | 'EVEN' | 'ODD' | 'MARK' | 'SPACE'
  stopBits: number
  flowControl: 'NONE' | 'RTS_CTS' | 'XON_XOFF'
}

export interface SerialPrompts {
  login: string
  password: string
  command: string
  lineEnding: 'AUTO' | 'CRLF' | 'CR' | 'LF'
}

export const SERIAL_BAUD_RATES = [4800, 9600, 19200, 38400, 57600, 115200] as const

export const SERIAL_PARITY_OPTIONS = [
  { value: 'NONE', label: '无校验' },
  { value: 'EVEN', label: '偶校验' },
  { value: 'ODD', label: '奇校验' },
  { value: 'MARK', label: 'MARK' },
  { value: 'SPACE', label: 'SPACE' }
] as const

export const SERIAL_STOP_BIT_OPTIONS = [
  { value: 1, label: '1' },
  { value: 2, label: '2' }
] as const

export const SERIAL_FLOW_CONTROL_OPTIONS = [
  { value: 'NONE', label: '无流控' },
  { value: 'RTS_CTS', label: 'RTS/CTS' },
  { value: 'XON_XOFF', label: 'XON/XOFF' }
] as const

export const DEFAULT_SERIAL_PARAMS: SerialParams = {
  baudRate: 9600,
  dataBits: 8,
  parity: 'NONE',
  stopBits: 1,
  flowControl: 'NONE'
}

export const DEFAULT_SERIAL_PROMPTS: SerialPrompts = {
  login: '(?i)(login|username)\\s*:\\s*$',
  password: '(?i)password\\s*:\\s*$',
  command: '[>#\\$]\\s*$',
  lineEnding: 'AUTO'
}

export function serialParamFields(): Array<keyof SerialParams> {
  return ['baudRate', 'dataBits', 'parity', 'stopBits', 'flowControl']
}
```

- [ ] **Step 4: 类型与 API**

`types/collection.ts`：

```typescript
export type ConnectionProtocol = 'SSH2' | 'TELNET' | 'SERIAL'
```

`TelnetPrompts` 接口后加（从 utils 引类型）：

```typescript
import type { SerialParams, SerialPrompts } from '@/utils/serial-connection'
```

`TransientConnectionRequest` 联合追加分支（放在 TELNET 分支之后）：

```typescript
  | {
      protocol: 'SERIAL'
      authenticationType: 'PASSWORD'
      executionMode: 'SHELL'
      hostKeyFingerprint?: never
      telnetPrompts?: never
      serialParams: SerialParams
      serialPrompts: SerialPrompts
      password: string
      privateKey?: never
      passphrase?: never
    }
```

`SavedConnectionWriteConnection` 联合追加（password 可选版本）：

```typescript
  | {
      protocol: 'SERIAL'
      authenticationType: 'PASSWORD'
      executionMode: 'SHELL'
      hostKeyFingerprint?: never
      telnetPrompts?: never
      serialParams: SerialParams
      serialPrompts: SerialPrompts
      password?: string
      privateKey?: never
      passphrase?: never
    }
```

`SavedCollectionTarget`（:149-162）的 never 清单补 `serialParams?: never`、`serialPrompts?: never`。

`api/saved-connections.ts` 的 `SavedConnection.connection` 增加字段：

```typescript
    serialParams: SerialParams | null
    serialPrompts: SerialPrompts | null
```

（import type 补 `SerialParams, SerialPrompts`。）

新建 `api/serial-ports.ts`：

```typescript
import { deviceOpsApi } from '@/api/device-ops'

export function listSerialPorts(signal?: AbortSignal): Promise<string[]> {
  return deviceOpsApi
    .get<{ ports: string[] }>('serial-ports', { signal })
    .then(({ data }) => data.ports)
}
```

`config/runtime.ts` 的 `RuntimeConfig` 接口加 `serialEnabled: boolean`。

`stores/recent-connections.ts` :23 的协议白名单改为：

```typescript
    (item.protocol === 'SSH2' || item.protocol === 'TELNET' || item.protocol === 'SERIAL') &&
```

- [ ] **Step 5: 运行确认绿**

Run: `cd device-ops-web && pnpm test && pnpm ts:check`
Expected: 全部通过（ts:check 会暴露所有漏改的类型分支；若 `ProjectCollectionView`/`workbench-phase-one.spec` 出现联合类型收窄错误，按 TS 提示为对应位置补 SERIAL 分支或类型守卫，不改既有 SSH2/TELNET 行为）。

- [ ] **Step 6: Commit**

```bash
git add device-ops-web/src/types device-ops-web/src/api device-ops-web/src/config device-ops-web/src/utils device-ops-web/src/stores
git commit -m "feat(device-ops-web): 串口连接类型与 API"
```

---

### Task 9: 前端 —— 表单串口分支与管理页能力位

**Files:**
- Modify: `device-ops-web/src/components/ProtocolConnectionForm.vue`
- Modify: `device-ops-web/src/views/ProjectCollectionView.vue`
- Modify: `device-ops-web/src/types/management.ts`（Task 8 未完成部分在此核对）
- Modify: `device-ops-web/src/views/management/SettingsManagementView.vue`
- Test: `device-ops-web/src/management/presentation.spec.ts`（追加源码断言）

- [ ] **Step 1: 写失败测试（源码断言，沿用 presentation.spec 模式）**

`presentation.spec.ts` 追加：

```typescript
describe('serial connection form', () => {
  it('renders a SERIAL protocol branch with console parameters', () => {
    const form = read('components/ProtocolConnectionForm.vue')
    expect(form).toContain(`value="SERIAL"`)
    expect(form).toContain('串口')
    expect(form).toContain('波特率')
    expect(form).toContain('数据位')
    expect(form).toContain('校验')
    expect(form).toContain('停止位')
    expect(form).toContain('流控')
    expect(form).toContain('COM')
    expect(form).toContain('serial-ports')
    expect(form).toContain('串口明文传输')
  })

  it('exposes serial capability on settings page', () => {
    const settings = read('views/management/SettingsManagementView.vue')
    expect(settings).toContain('serialEnabled')
    expect(settings).toContain('串口')
  })
})
```

（`read` 辅助函数沿用该文件既有定义；若该文件没有 `read`，查看其现有源码读取辅助并复用同名函数。）

- [ ] **Step 2: 运行确认红**

Run: `cd device-ops-web && pnpm test -- src/management/presentation.spec.ts`
Expected: FAIL（表单尚无串口分支）。

- [ ] **Step 3: 实现表单串口分支**

`ProtocolConnectionForm.vue`（710 行，改动点逐条列出）：

(a) script 头部 import 补：

```typescript
import { listSerialPorts } from '@/api/serial-ports'
import {
  DEFAULT_SERIAL_PARAMS,
  DEFAULT_SERIAL_PROMPTS,
  SERIAL_BAUD_RATES,
  SERIAL_FLOW_CONTROL_OPTIONS,
  SERIAL_PARITY_OPTIONS,
  SERIAL_STOP_BIT_OPTIONS,
  type SerialParams,
  type SerialPrompts
} from '@/utils/serial-connection'
```

(b) 状态（`telnetEnabled` 之后）加：

```typescript
const serialEnabled = ref(false)
const serialPortNames = ref<string[]>([])
const serialParams = reactive<SerialParams>({ ...DEFAULT_SERIAL_PARAMS })
const serialPrompts = reactive<SerialPrompts>({ ...DEFAULT_SERIAL_PROMPTS })
const serialComPort = ref('')
```

(c) `effectiveAuthenticationType`（:66-68）改为：

```typescript
const effectiveAuthenticationType = computed<'PASSWORD' | 'PRIVATE_KEY'>(() =>
  protocol.value === 'SSH2' ? authenticationType.value : 'PASSWORD'
)
```

(d) `savedConnectionsByProtocol`（:77-87）的分组映射改为：

```typescript
const savedConnectionsByProtocol = computed(() =>
  (['SSH2', 'TELNET', 'SERIAL'] as const)
    .map((savedProtocol) => ({
      protocol: savedProtocol,
      label: savedProtocol === 'SSH2' ? 'SSH2 连接' : savedProtocol === 'TELNET' ? 'Telnet 连接' : '串口连接',
      connections: savedConnections.value.filter(
        (connection) => connection.connection.protocol === savedProtocol
      )
    }))
    .filter((group) => group.connections.length > 0)
)
```

(e) `watch(protocol)`（:91-98）改为（串口不设端口默认值，port 归 0）：

```typescript
watch(protocol, (current, previous) => {
  if (!suppressProtocolPortDefault) {
    if (current === 'TELNET' && previous === 'SSH2' && endpoint.port === 22) endpoint.port = 23
    if (current === 'SSH2' && previous === 'TELNET' && endpoint.port === 23) endpoint.port = 22
    if (current === 'SERIAL') endpoint.port = 0
  }
  if (current !== 'SSH2') executionMode.value = 'SHELL'
  clearTestState()
}, { flush: 'sync' })
```

(f) `directConnectionFields()`（:153-163）改为：

```typescript
function directConnectionFields() {
  if (protocol.value === 'TELNET' && !telnetEnabled.value) {
    throw new Error('当前部署未启用 Telnet。')
  }
  if (protocol.value === 'SERIAL' && !serialEnabled.value) {
    throw new Error('当前部署未启用串口。')
  }
  if (protocol.value === 'SERIAL') {
    return {
      host: requireValue(serialComPort.value || endpoint.host, '请输入 COM 口名，例如 COM3。'),
      port: 0,
      username: requireValue(endpoint.username, '请输入用户名。'),
      connectTimeoutSeconds: connectTimeoutSeconds.value
    }
  }
  return {
    host: requireValue(endpoint.host, '请输入 IP 或主机名。'),
    port: endpoint.port,
    username: requireValue(endpoint.username, '请输入用户名。'),
    connectTimeoutSeconds: connectTimeoutSeconds.value
  }
}
```

(g) `currentTelnetPrompts()`（:165-172）之后加：

```typescript
function currentSerialParams(): SerialParams {
  return { ...serialParams }
}

function currentSerialPrompts(): SerialPrompts {
  return {
    login: requireValue(serialPrompts.login, '请输入登录名提示符。'),
    password: requireValue(serialPrompts.password, '请输入密码提示符。'),
    command: requireValue(serialPrompts.command, '请输入命令提示符。'),
    lineEnding: serialPrompts.lineEnding
  }
}
```

(h) `buildDirectConnection()`（:179-210）在 TELNET 分支之后加 SERIAL 分支：

```typescript
  if (protocol.value === 'SERIAL') {
    return {
      ...fields,
      protocol: 'SERIAL',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      serialParams: currentSerialParams(),
      serialPrompts: currentSerialPrompts(),
      password: requireValue(password.value, '请输入本次连接使用的密码。')
    }
  }
```

(i) `buildSavedConnectionWrite()`（:212-250）在 TELNET 分支之后加：

```typescript
  if (protocol.value === 'SERIAL') {
    return {
      ...fields,
      protocol: 'SERIAL',
      authenticationType: 'PASSWORD',
      executionMode: 'SHELL',
      serialParams: currentSerialParams(),
      serialPrompts: currentSerialPrompts(),
      ...(password.value || credentialRequired
        ? { password: requireValue(password.value, '请输入本次连接使用的密码。') }
        : {})
    }
  }
```

(j) `savedFormSignature()`（:266-280）补两行（`telnetPrompts` 行之后）：

```typescript
    serialParams: protocol.value === 'SERIAL' ? { ...serialParams } : undefined,
    serialPrompts: protocol.value === 'SERIAL' ? { ...serialPrompts } : undefined
```

(k) `applySavedConnection()`（:291-307）在 telnetPrompts 赋值之后加：

```typescript
  if (saved.connection.serialParams) Object.assign(serialParams, saved.connection.serialParams)
  if (saved.connection.serialPrompts) Object.assign(serialPrompts, saved.connection.serialPrompts)
  if (saved.connection.protocol === 'SERIAL') serialComPort.value = saved.connection.host
```

(l) `startNewSavedConnection()`（:360-372）中 `endpoint.port = ...` 行改为：

```typescript
  endpoint.port = protocol.value === 'TELNET' ? 23 : protocol.value === 'SERIAL' ? 0 : 22
  if (protocol.value === 'SERIAL') {
    serialComPort.value = ''
    Object.assign(serialParams, DEFAULT_SERIAL_PARAMS)
    Object.assign(serialPrompts, DEFAULT_SERIAL_PROMPTS)
  }
```

(m) `describeRecent()`（:419-431）保持不变（`protocol.value` 已含 SERIAL，port 为 0）。

(n) `onMounted`（:475-478）改为：

```typescript
onMounted(async () => {
  const runtime = await loadRuntimeConfig()
  telnetEnabled.value = runtime.telnetEnabled
  serialEnabled.value = runtime.serialEnabled
  if (runtime.serialEnabled) {
    listSerialPorts().then((names) => { serialPortNames.value = names }).catch(() => { serialPortNames.value = [] })
  }
  await loadSavedConnections()
})
```

(o) template：
- 协议选择器（:491-495）在 TELNET 按钮后加：

```html
        <el-radio-button value="SERIAL" :disabled="!serialEnabled">串口</el-radio-button>
```

helper 小字（:495）改为：

```html
      <small v-if="!telnetEnabled || !serialEnabled" class="connection-helper">
        {{ [!telnetEnabled ? 'Telnet 未由当前部署启用' : '', !serialEnabled ? '串口未由当前部署启用' : ''].filter(Boolean).join('；') }}
      </small>
```

- TELNET 警示（:503-509）之后加：

```html
  <el-alert
    v-if="protocol === 'SERIAL'"
    title="串口明文传输，仅限受控网络"
    type="warning"
    :closable="false"
    show-icon
  />
```

- 保存连接下拉选项 label（:536）改为（串口没有端口语义）：

```html
              :label="connection.connection.protocol === 'SERIAL'
                ? `${connection.displayName} · ${connection.connection.username}@${connection.connection.host}`
                : `${connection.displayName} · ${connection.connection.username}@${connection.connection.host}:${connection.connection.port}`"
```

- 基本连接区（:583-588）：主机名与端口字段改为按协议切换：

```html
      <el-form-item :label="protocol === 'SERIAL' ? 'COM 口' : 'IP / 主机名'">
        <el-input v-if="protocol === 'SERIAL'" v-model="serialComPort" autocomplete="off" placeholder="COM3" />
        <el-input v-else v-model="endpoint.host" autocomplete="off" placeholder="192.0.2.10" />
        <el-select
          v-if="protocol === 'SERIAL' && serialPortNames.length"
          :model-value="serialComPort"
          aria-label="本机串口列表"
          @update:model-value="serialComPort = String($event)"
        >
          <el-option v-for="name in serialPortNames" :key="name" :label="name" :value="name" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="protocol !== 'SERIAL'" label="端口">
        <el-input-number v-model="endpoint.port" :min="1" :max="65535" controls-position="right" />
      </el-form-item>
```

- 高级设置 `<template v-else>`（:639-651，当前是 Telnet 提示符分支）改为三路：

```html
      <template v-else-if="protocol === 'SERIAL'">
        <el-form-item label="波特率">
          <el-select v-model="serialParams.baudRate" aria-label="波特率">
            <el-option v-for="rate in SERIAL_BAUD_RATES" :key="rate" :label="String(rate)" :value="rate" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据位">
          <el-select v-model="serialParams.dataBits" aria-label="数据位">
            <el-option label="8" :value="8" />
            <el-option label="7" :value="7" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验">
          <el-select v-model="serialParams.parity" aria-label="校验">
            <el-option v-for="option in SERIAL_PARITY_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="停止位">
          <el-select v-model="serialParams.stopBits" aria-label="停止位">
            <el-option v-for="option in SERIAL_STOP_BIT_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流控">
          <el-select v-model="serialParams.flowControl" aria-label="流控">
            <el-option v-for="option in SERIAL_FLOW_CONTROL_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="输入换行方式">
          <el-select v-model="serialPrompts.lineEnding" aria-label="串口输入换行方式">
            <el-option label="自动（终端回车）" value="AUTO" />
            <el-option label="CRLF" value="CRLF" />
            <el-option label="CR" value="CR" />
            <el-option label="LF" value="LF" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录名提示符"><el-input v-model="serialPrompts.login" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="密码提示符"><el-input v-model="serialPrompts.password" class="command-input" autocomplete="off" /></el-form-item>
        <el-form-item label="命令提示符" class="form-grid__wide"><el-input v-model="serialPrompts.command" class="command-input" autocomplete="off" /></el-form-item>
      </template>
      <template v-else>
        <!-- 原 Telnet 提示符分支（:640-650）原样保留 -->
      </template>
```

- 认证区（:598-619）：SSH2 `<template v-if>` 保持；TELNET/串口共用密码输入（当前 `v-else` 分支 :617-619 已覆盖两者，无需改）。

(p) `views/ProjectCollectionView.vue` :106 改为：

```typescript
  if (connection.protocol === 'TELNET' || connection.protocol === 'SERIAL') {
```

（SERIAL 展开时 executionMode 已是 'SHELL'，与 TELNET 同一归一分支。）

(q) `views/management/SettingsManagementView.vue` :10 labels 对象加 `serialEnabled: '串口'`（`types/management.ts` :15 capabilities 类型加 `serialEnabled: boolean`）。

- [ ] **Step 4: 运行确认绿（前端全量门）**

Run: `cd device-ops-web && pnpm test && pnpm ts:check && pnpm lint && pnpm build:local`
Expected: 全部通过。

- [ ] **Step 5: Commit**

```bash
git add device-ops-web/src
git commit -m "feat(device-ops-web): 连接表单串口分支与能力展示"
```

---

### Task 10: e2e 回归与后端全量验证

**Files:** 无新文件（回归门）。

- [ ] **Step 1: 后端全量**

Run: `mvn test`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 前端 e2e 回归（既有 7 项不回归）**

Run: `cd device-ops-web && pnpm test:e2e`
Expected: 全部通过（Playwright 全 mock，不触真实后端；若连接表单 mock 数据缺 serialEnabled 字段导致运行时报错，在对应 mock 的 runtime-config 响应中补 `serialEnabled: false`，不改变既有断言）。

- [ ] **Step 3: Commit（若有 mock 修订）**

```bash
git add device-ops-web
git commit -m "test(device-ops-web): e2e mock 补充串口能力位"
```

（无修订则跳过本步。）

---

### Task 11: 部署 48181 与冒烟验证

**Files:** 无源码改动。沿用既有维护流程（见记忆 `device-ops-48181-deploy`：计划任务 `DeviceOps-48181` 启动，密钥仅以环境变量传子进程）。

- [ ] **Step 1: 打包**

Run: `mvn -DskipTests package`
Expected: BUILD SUCCESS，`device-ops-server/target/device-ops-server.jar` 生成。

- [ ] **Step 2: 核对环境并停服务**

- 确认无活动采集/解析（管理页"近期采集/解析等待摘要"为空，连续两次核对间隔 ≥1 分钟）。
- `schtasks //end //tn DeviceOps-48181` 并确认 java 进程退出；离线备份 `data/device-ops.mv.db` 与密钥文件（复制，不移动）。

- [ ] **Step 3: 更新部署目录并启动**

- 复制新 jar 到 `deploy/runtime-48181/device-ops-server.jar`（替换前校验 SHA256 与 target 一致）。
- `schtasks //change //tn DeviceOps-48181 //enable && schtasks //run //tn DeviceOps-48181`，随后 `schtasks //change //tn DeviceOps-48181 //disable` 防止 23:59 重复触发。
- 就绪检查：`curl -s http://127.0.0.1:48181/actuator/health/readiness` 返回 UP。

- [ ] **Step 4: 冒烟（只读）**

- `curl -s http://127.0.0.1:48181/api/v1/runtime-config` 含 `"serialEnabled":true`（Windows 本地模式默认 true）。
- 浏览器打开工作台连接区：协议行出现"串口"按钮；点击后出现 COM 口下拉（本机有 COM 口时列出；无 COM 口时空列表不报错）、波特率/数据位/校验/停止位/流控默认 9600/8/无校验/1/无流控、"串口明文传输，仅限受控网络"警示。
- 既有 SSH2/Telnet 表单、保存连接回显（不回 503）、四页签与输出区不变。
- 截图存档到既有验收截图目录。

- [ ] **Step 5: Commit（截图与文档）**

```bash
git add device-ops-web/test-results docs
git commit -m "test(device-ops): 串口接入验收截图"
```

---

### Task 12: 用户真机验收（人工，非本计划自动步骤）

我提供核对清单，由用户在真机（Console 线连接设备串口）执行：

1. 打开连接区 → 协议选"串口"→ COM 口下拉应枚举出真机 COM 口。
2. 波特率按设备说明书设置（默认 9600），输入控制台账号密码，点"测试连接"→ 应显示"连接成功"。
3. 提交一条只读 show 命令采集（不执行任何写入/重启类命令）。
4. 核对：输出页签显示命令回显、请求快照保留 `script.content`、下载 BOM 正常、证据页签出现命令块。
5. 保存连接：验证并保存后刷新页面回显串口参数正确。
6. 无真机时的降级验收：仅核对第 1 步端口枚举与警示文案。

---

## 自审记录（写计划时已核对）

- **Spec 覆盖**：spec §1 → Task 1；§2 → Task 4/5/6（偏差 1/2 已声明）；§3 → Task 2/7/8/9；§4 → Task 7（SerialProperties/capabilities）、Task 9（警示 el-alert 保留）、KeyedCollectionDispatcher 按 host 天然排他零改动；§5 → 各任务 TDD 步骤 + Task 10/11/12。
- **占位符**：无 TBD/TODO；所有代码步骤给出完整代码；`application.yml` 的检查以 grep 指令+格式规则给出（该文件当前未显式声明 telnet 默认值的可能性已提示执行者先核对）。
- **类型一致性**：`SerialParams`/`SerialPrompts` 命名在 core（`CommandExecutionPort` 内嵌）、web（`ConnectionRequestMapper` 内嵌）、前端（`utils/serial-connection.ts`）三层一致；`ConnectionSpec` canonical 11 参贯穿 Task 1/2/3/7；web `Connection` 17 字段贯穿 Task 2 的全部构造点。
