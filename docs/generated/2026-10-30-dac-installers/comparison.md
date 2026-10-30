# DAC 1.1.0 安装器比较（未部署）

来源：2026-10-30需求方批准8280～8289候选端口、首次配对、九入口测试及双安装器；禁止部署、重启、提交或推送。正式契约见F-INT-012 Spec，进展见对应Task。

两包共享同一冻结JAR、精简Java 25、WinSW 2.12.0及251项载荷；Inno为独立原生安装器，不调用MSI引擎。

| 项目 | MSI / WiX | Inno Setup EXE |
|---|---|---|
| 文件 | [NPDMS-DAC-1.1.0-x64.msi](E:/AICoding/Projects/NPDMS/.run/build/dac-desktop-1.1.0-20261030-paired/NPDMS-DAC-1.1.0-x64.msi) | [NPDMS-DAC-1.1.0-x64-inno.exe](E:/AICoding/Projects/NPDMS/.run/build/dac-inno-1.1.0-20261030-paired/NPDMS-DAC-1.1.0-x64-inno.exe) |
| 字节 | 90,360,138 | 87,785,041 |
| MiB | 86.17 | 83.72 |
| 安装依赖 | Windows Installer、.NET Framework 4.6.1+ | .NET Framework 4.6.1+ |
| 服务身份 | LocalService | LocalService |
| 静默参数 | `/qn /norestart` | `/VERYSILENT /SUPPRESSMSGBOXES /NORESTART` |
| 设置参数 | `NPDMS_SERVER_URL`、`NPDMS_WEB_ORIGIN` | `/SERVERURL=`、`/WEBORIGIN=` |
| 静默弹浏览器 | 不弹 | 不弹 |
| 升级 | MSI MajorUpgrade | Inno自身升级，阻止降级 |
| 跨安装器覆盖 | 阻止，先卸载Inno | 阻止，先卸载MSI |
| 数据与密钥 | ProgramData原目录保留 | ProgramData原目录保留 |
| 产物验证 | 真实MSI会话只读配置校验、WiX标准校验、251项解包哈希通过 | 编译及251项共用输入载荷哈希通过，未执行解包或安装 |
| 实际安装/升级/卸载 | 未执行 | 未执行 |

MSI SHA256：`c9bb1e6a96fe5cb35e94dee05e8f9330b14d4b13cecc1ac13c6a3d2bfb35f760`

Inno SHA256：`0849f4b0cf850ff89681650448ae7df218830b1805abd0fb4722c46753da0815`

Inno比MSI小约2.85%，仅为本次压缩结果，不代表运行性能差异。

最终冻结清单：`E:\AICoding\Projects\NPDMS\.run\build\releases\37fe9c5a-5beb-4cfb-b67b-2b55b8397919\manifest.json`。

## 验证范围

- 前端发现、配对UI及九类业务入口80项通过；全量vue-tsc在8 GiB堆限制下通过。
- DAC此前完整构建96个报告、518项测试通过无跳过。随后新增原生启动器验签入口，20项Desktop定向测试再通过并生成本次冻结清单；二者有重叠，不累加为538项。原生入口不初始化Spring、数据库或服务。
- 使用随包精简JRE及实际可执行JAR验证浏览器密码库生成的合法签名与错误nonce，退出码分别0/1；没有启动应用服务。
- 原生候选顺序、URL、协议限制、DPAPI往返和隔离浏览器策略测试通过。管理员权限下配置持久化与ACL检查未运行。

证据日志：`.run/dac-ports-front-nine-20261030.log`、`.run/dac-ports-typecheck-final-20261030.log`、`.run/dac-port-full-build-20261030.log`、`.run/dac-port-java-verifier-build-20261030.log`、`.run/dac-native-proof-process-20261030.log`、`.run/dac-port-msi-package-paired-20261030.log`、`.run/dac-port-inno-package-paired-20261030.log`。

未部署、未发布下载载荷、未安装/升级DAC、未操作产品服务、未提交或推送。真实端口占用切换、原数据升级、两安装器安装/取消/失败/卸载及九类入口真实浏览器闭环留待单独授权；编译及自动测试不替代运行验收，比较包不声明生产就绪。

## 兼容与后续

协议3需同时更新客户端与网页；旧48182书签不适用，应通过开始菜单“DAC 本机状态”打开实际端口。首次核对32位公钥指纹，配对后自动发现。沿用原连接、凭据、数据库及Outbox目录，无业务表变更；新增身份文件使用原AES-GCM主密钥加密，不覆盖历史。原生启动器和业务网页均验证随机挑战签名与实际端口，不仅比对公开产品字段。

Linux尚未提供安装包：Java签名发现与网页配对可复用，后续独立实现systemd、服务账号、文件权限、密钥保护、单实例锁、候选端口启动重试与Linux JRE；Windows DPAPI、注册表及WinSW保持在Windows边界。
