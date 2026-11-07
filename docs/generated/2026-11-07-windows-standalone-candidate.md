# Windows 独立安装验证候选

2026-11-07，基于已发布 `265d48f4450c74cfc7f8e9b26d922a57867ff794`，在云端生成了实际安装器，不再要求预装原生 DAC。它是全新测试环境候选，不是正式冻结发布或已完成 Windows 验收的产品。

## 产物与验证

- 文件：`SDP-Standalone-1.1.8-Windows-x64-CANDIDATE.exe`
- 大小：202,569,129 字节。
- SHA256：`f02af728c15aad80a1f810e62e94322439a52002c2a7818dd5b15a6afa240692`。
- 云端保留目录：`/workspace/scratch/npdms-standalone-build-v2/`；包含安装器、SHA256SUMS、编译日志、输入清单、解包结果及验证记录。此路径不是下载链接。
- Inno 7.0.2 x64 编译成功，Pascal 脚本实际编译通过。Linux 原生只读解包后，3265 个归档文件条目（含重复的临时 SdpDac.exe）对应 3264 个唯一文件：339 个原生载荷/清单文件、2925 个桌面文件。解压校验和及全部文件大小、SHA256 与输入一致。
- 安装器 PE 证书表为 `(0, 0)`，未签名。详细机器可读记录：[验证 JSON](2026-11-07-windows-standalone-verification.json)。
- 准备脚本 8 项测试通过，包括同大小篡改、额外文件、必需组件缺失、符号链接、不安全路径、原生脚本漂移拒绝，以及原服务生命周期代码逐字保持的检查。桌面源码、资源、前端清单/来源、服务适配器哈希已核验。

## 安装内容和边界

包含原生 SdpDac/SdpDacHost、WinSW、DAC JAR、解析器、完整 Windows x64 Temurin JRE 和 Electron 桌面/前端/服务身份验证辅助程序。具体原生构建与 JRE 来源沿用 [前次记录](2026-11-06-windows-service-ownership-candidate.md)，本轮没有替换业务实现或密钥实现。

候选要求 Windows x64（原生脚本最低 Windows 10）、.NET Framework 4.6.1 或更高版本、管理员安装权限、可用的同源 HTTPS 后端和网页来源。不会部署中心后端或数据库，也不内置凭据。网页来源留空时取后端地址；两个地址必须一致并通过原生配置校验。安装注册并启动 NpdmsDac 服务；安装后从开始菜单手动启动桌面，退出桌面不会停止共享服务。

`prepare-standalone-candidate.py` 校验原始 Package.iss SHA256 后派生独立 Candidate.iss；原文件未改动。候选使用独立 AppId。在 InitializeSetup 和 PrepareToInstall 两处拒绝旧 MSI、旧/候选 Inno、既有服务、产品/协议配置、当前/旧程序目录以及 ProgramData 数据目录。因而旧升级/迁移代码不能在允许范围内执行；没有声称支持或验证了升级迁移。

原有 --initialize、--service-install、--service-start 顺序、安装失败时的服务/浏览器策略清理，以及卸载前停止/移除服务和仅移除归属策略的行为保持。清理失败仍中止卸载；ProgramData 配置、DPAPI 密钥、连接和任务历史不加入删除列表。没有放宽 ACL、身份验证或业务授权。上述守卫是源码审查及编译检查，尚非 Windows 实测。

## 构建复现与工具

准备命令：

```sh
python3 npdms-desktop/scripts/prepare-standalone-candidate.py --native /workspace/scratch/npdms-windows-native-payload-candidate --desktop /workspace/scratch/npdms-windows-ownership-candidate/win-unpacked --output /workspace/scratch/npdms-standalone-build-new
python3 npdms-desktop/scripts/test-standalone-candidate.py
```

将该新目录映射为 `Z:\out`、原生载荷映射为 `Z:\native` 后，使用 ISCC 编译：

```text
ISCC.exe /DPayload=Z:\native /DDesktopPayload=Z:\out\desktop /DVersion=1.1.8 /DOutputDirectory=Z:\out /DCandidateNotice=Z:\out\candidate-notice.txt Z:\out\Candidate.iss
```

编译器取自 [官方 Inno 7.0.2 分发](https://github.com/jrsoftware/issrc/releases/tag/is-7_0_2)，下载文件 SHA256 `5ad54ca3def786f8f4212552e54cc6d8d61329e2d24a1cfee0571d42c2684ff1`。使用 [inno 解析库](https://github.com/russellbanks/inno/tree/b24d75f318b0689be25eefa4a52c4534749a738c) 的 extract 功能，原生 Linux 解包并校验 135 个编译器文件，然后在 Debian Wine 10 的隔离工具容器运行 64 位 ISCC；产品安装器没有在 Wine 运行。该库也用于最终安装器只读解包及校验。工具和 Wine 不进入交付包。

曾尝试 Inno 7.1.1，其工具安装器因 Wine 无 x86 兼容能力而拒绝；Debian wine32 和新 WoW64 工具尝试均未解决当前云端兼容问题。原生解包库也明确拒绝 7.1.1，因此使用其支持的官方 7.0.2，未修改或绕过版本检查。源码树、旧产物和调试日志均保留。

## 未完成事项

- 未在真实 Windows 安装、卸载、启动桌面或运行 DAC；SCM、ACL、进程归属、DPAPI、服务启动/清理失败分支、业务 UI 闭环均待实测。既有 Linux/Node/C# 判定测试不能替代这些验收。
- 正式冻结校验脚本和权威命令预设仍缺失；本流程没有制造冻结标记、MSI 实机通过标记或命令目录。真实离线业务未启用，不能称完整离线产品。
- 上传准备流程此前返回 HTTP 401；本轮没有认证状态实质变化，按指示未重试或改用其他路径绕过。尚无本安装器的附件 ID、下载链接或持久上传回读验证。生成 EXE 不等于已经完成下载交付。
