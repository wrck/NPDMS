# Windows 服务归属验证与原生载荷候选

## 本轮结果

在 `1d35e9e10` 之上实现不读取私有数据根目录 ACL 的 Windows 运行时信任边界。原生数据/密钥 ACL、服务端授权、DPAPI、进程锁和正式冻结入口均未放宽或修改。当前只能称实现和安装候选，不能称真实 Windows 验证通过。

## 信任来源

原生安装器已授予普通用户查询和启动 `NpdmsDac` 的服务权限。本轮使用 SCM 的 `SERVICE_QUERY_STATUS` 取得 RUNNING 服务 PID，保留服务注册表、安装程序目录和公开文件的 ACL 校验。

辅助程序仅直接打开原本可读的 `client.xml` 和 `runtime/endpoint.properties`。对打开的同一文件句柄校验最终解析路径、文件 owner/DACL、单链接和大小；配置从该句柄解析，不再次按路径打开。私有根目录不再作为普通用户的 ACL 查询目标，其运行时信任由系统归属证明替代：

1. Toolhelp 快照和保留的 `PROCESS_QUERY_LIMITED_INFORMATION` 句柄确认 WinSW → SdpDacHost → Java 父子关系、准确程序路径、创建时间顺序及存活状态；取得句柄后重新读取父子关系，防止首次快照与 PID 复用之间的竞争。
2. `GetExtendedTcpTable` 必须返回唯一的 `127.0.0.1` 目标端口监听者，PID 必须为该 Java 进程。其他 PID、通配地址、外网地址、重复监听和缺失记录均拒绝。
3. 查询监听者后再次确认 SCM PID 及所有进程存活。只有全部通过才返回 `ownershipVerified=true`；Electron 拒绝没有该证明的元数据，仍执行新 nonce 的 Ed25519 验证和原有同连接请求校验。

不请求 `SeDebugPrivilege`、完整进程访问权或 UAC 提权，不以 HTTP 可达、程序名称或捕获权限错误后继续替代验证。标准用户是否能在目标机器取得这些查询权限仍须 Windows 实测；若拒绝，保持 `DAC_WINDOWS_ACCESS_DENIED`，不能声称已消除全部权限兼容问题。API 契约来源链接在 `npdms-desktop/packaging/windows/README.md`。

## 验证和新的 EXE

- Node：179 项通过，0 失败、0 跳过。
- 原生辅助程序、Windows ACL 测试程序交叉编译成功。21 项共用 C# 归属判定测试在 .NET 8/Linux 实际运行通过，覆盖错误父进程、创建时间/PID 复用、替换映像、已退出进程、错误监听 PID/地址/端口、重复和截断 TCP 表。这些是纯判定测试，未调用 Windows API。
- 新 NSIS 候选 `NPDMS-Windows-x64-Installed-DAC-Integration-CANDIDATE.exe`：126,894,016 字节；SHA256 `aade88cb3265fbc68716b06dc20fb9545ecc5a65aa71eff3e237b5b4fa4d202c`。
- 解包 CRC 与 2925 个载荷文件逐字节核验通过；源文件一致；新辅助程序 x64，SHA256 `28d6cc1003e7de918dc69dd5abfe57da2ea07ea4ce0b83c53c412b020e040d47`，清单要求 `scm-process-listener` 边界。旧候选仍保留，本次不是旧包。
- 安装器证书表为空，未签名。真实 Windows API、ACL/SCM/DPAPI、安装/卸载/升级及业务 UI 均未执行验收。此 EXE 仍只接入已安装的原生 DAC，不安装原生服务。

## 独立安装的输入准备

另装配原生载荷候选 338 个文件 / 240,674,462 字节：原始 SdpDac/SdpDacHost 编译产物、完整 DAC JAR、Windows JRE、WinSW、服务 XML、解析器和许可证。逐文件哈希已核验，原生可执行文件与先前记录的源码及编译哈希一致。

- 官方 Temurin Windows x64 JRE 25.0.1+8-LTS，归档 SHA256 与官方校验文件一致：`317ffcdd88eec2914506ba8456a6d1717f8efb9b148d2214ae793cfa9ec7b54d`。确认 x64 PE、Windows release 标识及 `jdk.crypto.mscapi`。这是完整 JRE 候选，不宣称与原 Windows jlink 产物相同。
- WinSW 2.12.0 来自原脚本指定的官方发布地址；下载后固定 SHA256 `b5066b7bbdfba1293e5d15cda3caaea88fbeab35bd5b38c41c913d492aadfc4f`，没有声称另行验证了上游发布的独立校验文件。
- JAR SHA256 仍为 `b9ee3b4b6c84255baabcc5f32b7e431880881935d131258fda51a469022ee789`。未修改、未冻结。
- 新 `stage-windows-dac-candidate.py` 可复现组件装配；错误 JRE 校验值会在创建输出前拒绝。目录有明确“非安装器”标记，不自动部署、启动、覆盖数据或生成通过标记。

## 仍需完成

需要真实 Windows 普通用户令牌下的只读访问/归属验证、正式原生 MSI 打包和安装回归；原始冻结脚本与权威命令预设仍缺失。已备组件不能替代这些步骤。上传认证仍阻塞，本轮未尝试上传，无新的附件 ID。未修改 master。
