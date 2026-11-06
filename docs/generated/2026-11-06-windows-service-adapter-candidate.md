# Windows 原生服务接入候选

本轮补齐 Electron 到**已安装原生 Windows DAC** 的接入路径，不把 Linux 启动器直接改成 Windows，也不复制 JAR 后宣称服务可用。原生 `ClientConfiguration`、SCM/WinSW、Java/H2 锁及 DPAPI 存储继续负责服务生命周期和密钥。没有改变服务端授权、断网新操作限制、原生配置、主分支或正式冻结入口。

## 实现与边界

- 新增 C# `ServiceBridge`，复用原 `ClientConfiguration.cs`，仅接受 `snapshot` / `start`。校验服务名、固定安装路径、LocalService 账户、文件/注册表 ACL、重解析点、硬链接与元数据大小。不读取或导出 DPAPI 密钥，不安装/停止/卸载服务。
- Electron 在启动前核对环境，使用受保护元数据固定公钥、实例和 PID，再校验新 nonce 的 Ed25519 签名。已有连接桥继续在承载实际请求的同一 TCP 连接上验证签名；操作前后重新核对元数据，服务重启使旧连接失效。
- 断开/关闭桌面只撤销桌面连接。SCM/WinSW 继续管理服务，不强杀 Java、不释放其 H2 锁；界面明确标注这一行为。
- Windows 离线合成试验保持关闭。真实离线业务未增加，命令预设目录仍缺失。没有用替代校验生成正式冻结通过标记。

## 安装前提

此包安装桌面客户端、内置 Vue 和服务适配器，**不安装原生 DAC 服务或其 Java/JAR/WinSW**。需要已有原生 Windows DAC 和 .NET Framework 4.6.1 或更高版本。原生配置的后端与网页来源必须都等于桌面输入的 HTTPS origin；不同则拒绝连接，不自动改写配置。此候选不满足没有原生 DAC 的全新机器上的完整独立安装要求。

## 实际验证

- Node 测试：178 项通过，0 失败、0 跳过；覆盖 Windows 主进程选路、环境拒绝、伪造签名、元数据替换、取消/并发、断开、辅助程序篡改和 UI 前提说明，以及既有回归。
- 前端清单验证：2825 个文件、34,656,500 字节，86 项 handler 检查，无网络请求。
- 辅助程序和 ACL 测试程序以 .NET Framework 4.6.1 / C# 5 / x64 交叉编译成功；保留工作树构建锁。**Windows ACL 测试程序未执行**，不能将交叉编译等同于 Windows 行为验证。
- NSIS 候选 `NPDMS-Windows-x64-Installed-DAC-Integration-CANDIDATE.exe`：126,891,424 字节；SHA256 `9a44a2ffab47df1452ca3b83a29642c813251627638bdb4d3ff890db71773684`。
- 解包 CRC 检查成功；2925 个载荷文件与构建目录逐字节一致，所有 Electron 源文件与本工作树一致；辅助程序为 x64 PE，其清单哈希匹配。
- 安装器 Authenticode 证书表为空，未签名。真实 Windows 安装、卸载、升级、SCM/ACL/DPAPI 和完整业务 UI 验收未执行。
- 遵守上传认证阻塞，本轮没有重试上传，无新附件 ID。原上传流程的 HTTP 401 尚未解决。

源码可继续审查和 Windows 验证。完整原生安装仍需恢复原冻结校验输入，并沿原 Windows 打包和验证链执行；本候选的构建成功不表示该链已通过。
