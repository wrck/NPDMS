# 串口采集接入设计（jSerialComm）

日期：2026-09-24
状态：已确认（用户批准）

## 背景与目标

设备运维平台现有 SSH2（Apache MINA）与 Telnet（Apache Commons Net）两种采集协议。网络设备 Console 串口是排障与初始化的基本通道，用户要求通过 jSerialComm 接入串口方式。

范围决策（已与用户确认）：

- 部署拓扑：**服务器本地 COM 口**——采集服务器（48181 所在 Windows）用 Console 线直连设备串口，jSerialComm 打开本地 COM 口。不做串口服务器/RFC 2217，不做客户端 COM 口代理。
- 认证：**控制台账号 + 提示符交互**（镜像 Telnet 模式）；密码加密存储不回显，与现有凭据体系一致。
- 验收：**纯 Java 虚拟串口测试替身 + 用户真机验收**。自动化测试不发真实字符流、不依赖真实 COM 口；真实设备验收由用户提供 Console 线后按核对清单手动执行。

此前 `docs/2026-07-30-crt-connection-workbench-design.md` 曾明确"不实现串口"；本设计是用户对该边界的显式更新，不改变其余范围。

## 1. 核心模型（device-ops-core）

- `ConnectionProtocol` 增加 `SERIAL`。
- `ConnectionSpec` 扩展：
  - 新 record `SerialParams(String comPort, int baudRate, int dataBits, Parity parity, int stopBits, FlowControl flowControl)`，默认 9600/8/无校验/1 停止位/无流控；`Parity`、`FlowControl` 为新枚举（NONE/EVEN/ODD/MARK/SPACE；NONE/RTS_CTS/XON_XOFF）。
  - 新 record `SerialPrompts(String login, String password, String command, TelnetLineEnding lineEnding)`，与 `TelnetPrompts` 同构（含非空默认提示符正则）。
  - SERIAL 时复用 `host` 存 COM 口名（如 "COM3"），`port` 置 0；构造器校验：port 必须为 0、`serialParams` 必须非空、`serialPrompts` 必须非空；仅 PASSWORD 认证、仅 SHELL 执行模式、无主机指纹（与 TELNET 校验镜像）。
  - 不新增平行连接字段，API JSON 向后兼容（新增可选字段）。

## 2. 适配器模块（device-ops-adapter-serial，新建）

- 依赖 `com.fazecast:jSerialComm` 2.x；模块仅依赖 core 与 jSerialComm。
- `SerialCommandExecutionAdapter implements ProtocolCommandExecutionAdapter`，`protocol()` 返回 `SERIAL`；Spring bean 注册后由 `ProtocolCommandExecutionRouter` 自动纳入路由（server 装配代码只需新增一个 bean 方法，路由零改动）。
- `test(connection, secret, passphrase)`：校验 COM 口存在 → 打开（独占）→ 按提示符完成登录 → 安全关闭。口不存在/被占用返回明确中文错误（不泄漏内部路径）。
- `execute(...)`：登录 → 逐命令发送 → 按命令提示符/回显切分 `CommandOutputBlock`；支持 `ProgressListener` 进度上报；`Deadline` 超时、最大输出字节、分页上限与 Telnet 参数语义一致。
- 提示符匹配引擎在模块内自包含，**不**从 Telnet 适配器抽取共享工具类（避免改动已验证的 Telnet 代码；后续如重复需求再提炼）。
- 构造参数注入 `RemoteEndpointPolicy` 与 SerialProperties（enabled、max-output-bytes、event-chunk-bytes、max-pages），enabled=false 时 test/execute 返回与 Telnet 同构的禁用错误。
- 适配器内部以 `SerialTransport` 小接口（open/close/read/write/configure）隔离 jSerialComm 依赖，生产实现包装 jSerialComm `SerialPort`，测试用内存替身实现同一接口。

## 3. 连接模型与 UI/API 接入

- `ConnectionRequestMapper`（web 层）：请求 JSON 增加可选 `serialParams`/`serialPrompts`；保存连接复用同一 mapper，凭据加密与版本机制不变。
- `ProtocolConnectionForm.vue`：协议页签增加"串口"；SERIAL 分支字段 = COM 口（下拉枚举 + 手输）、波特率（默认 9600；可选 4800/9600/19200/38400/57600/115200）、数据位/校验/停止位/流控（默认 8/无/1/无）、登录名/密码提示符 + 换行符（复用 Telnet 提示符控件样式）。
- 新端点 `GET /api/v1/serial-ports`：返回本机可用 COM 口名列表（仅口名，无敏感信息），`device-ops:collections:read` 权限，`serialEnabled=false` 时返回空列表；UI 下拉使用。
- `TargetSelector` 协议常量、`ManagementStatus` 中文映射（SERIAL→串口）、`runtime-config` 增加 `serialEnabled`。
- 不改工作台页签结构、四卡片布局、850px 分界与输出页签。

## 4. 配置与安全

- `device-ops.serial.enabled`（默认 true）+ `device-ops.serial.max-output-bytes`（8 MiB）/`event-chunk-bytes`（8 KiB）/`max-pages`（10000），与 Telnet 同名参数同构。
- `ManagementSettings.capabilities` 增加 `serialEnabled`。
- 安全警示保留：串口明文传输 el-alert（同 Telnet 级别）；凭据加密存储、明文不回显。
- 串口为独占资源：同一 COM 口并发采集由 `KeyedCollectionDispatcher` 按 host（=COM 口名）天然排他，不新增并发机制。
- 证据边界不变：`script.content` 快照、`omittedFields`、BOM 下载、秘密不泄漏等既有断言全部保留。

## 5. 测试与验收

- TDD 红先行：
  - `ConnectionSpec` SERIAL 校验分支（非法 port/缺 SerialParams/缺 SerialPrompts/非 SHELL）。
  - mapper、路由注册、enabled 开关、capabilities 序列化。
- 集成（模块内）：自写内存虚拟串口测试替身（jSerialComm 接口面窄，替身可实现同一交互层；适配器内部以 `SerialTransport` 小接口隔离 jSerialComm 依赖，替身实现 `SerialTransport`）。覆盖：登录成功、密码错误、提示符超时、口不存在、口被占用、命令输出分块、进度上报、安全关闭、输出超限截断。
- 前端：单测覆盖 SERIAL 表单字段/校验/文案（红先行）；e2e 现有 7 项回归不回归。
- 质量门：全量测试、ts:check、lint、build、`mvn clean verify` 打包后部署 48181。
- 真实验收（用户执行，我提供核对清单）：枚举 COM 口 → 测试连接 → 只读执行一条 show 命令 → 证据与输出页签核对。无真机时仅验端口枚举与开关；不执行设备写入类命令。

## 行为边界

- 不改后端状态机、保存连接逻辑、解析发布机制、数据库 schema（保存连接连接 JSON 为既有 JSON 存储，无迁移）。
- 不翻译/不修改原始输出、脚本、枚举证据；管理页提交仍用原始状态码。
- 不引入 RFC 2217、不实现串口代理、不做多口并发复用。
