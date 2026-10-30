# DAC 1.1.1 自动卸载安装器比较（2026-10-30）

需求方要求安装时自动卸载旧版；沿用“不部署、不实际安装/卸载或重启、不提交或推送”边界。旧1.1.0比较报告与包保留，本报告是自动卸载增量的新入口。

| 项目 | MSI | Inno EXE |
|---|---|---|
| 包 | [NPDMS-DAC-1.1.1-x64.msi](E:/AICoding/Projects/NPDMS/.run/build/dac-desktop-1.1.1-20261030-autoremove-release/NPDMS-DAC-1.1.1-x64.msi) | [NPDMS-DAC-1.1.1-x64-inno.exe](E:/AICoding/Projects/NPDMS/.run/build/dac-inno-1.1.1-20261030-autoremove-release/NPDMS-DAC-1.1.1-x64-inno.exe) |
| 字节 | 90,364,405 | 87,788,692 |
| MiB | 86.18 | 83.72 |
| 旧 MSI | MajorUpgrade自动移除 | 重新确认ProductCode/版本后，系统msiexec静默卸载 |
| 旧 Inno | 延迟提权动作自动卸载 | PrepareToInstall自动卸载旧版 |
| 顺序 | 校验后、InstallFiles前移除旧版，强制重新复制载荷 | 配置与归属校验后移除旧版，再复制载荷 |
| 异常 | 失败、降级、来源异常、旧记录/服务残留均中止 | 同左；3010不自动重启，重启后重试 |
| ProgramData | 配置、凭据、连接、配对身份、任务与Outbox保留 | 同左 |
| 静默 | `/qn /norestart`，不弹浏览器 | `/VERYSILENT /SUPPRESSMSGBOXES /NORESTART`，不弹浏览器 |

MSI SHA256：`a0ca42f705a3acd6a0ef098c134add3e39d94584105608c736e43aaf67598ba0`

Inno SHA256：`e239ce9990bd597d43852c6d8694a529414068475c120e3013d89131f6931670`

两包共享冻结JAR、JRE、WinSW及251项载荷。Java与网页发现协议不变，复用清单 `E:\AICoding\Projects\NPDMS\.run\build\releases\37fe9c5a-5beb-4cfb-b67b-2b55b8397919\manifest.json`。本轮仅改变安装/卸载编排与网页安装包版本链接。

## 验证证据与限制

- 失败基线：用旧包执行新增的只读MSI动作表检查，因缺少PlanRemovePreviousInno而失败，未执行卸载（`.run/dac-auto-remove-before-20261030.log`）。
- 新MSI：真实MSI会话中只读检查动作次序、延迟/提权/失败中止属性、重新复制规则和原配置校验；WiX标准验证与251项解包哈希通过。没有调用PlanRemovePreviousInno或RemovePreviousInno的真实卸载流程。日志 `.run/dac-auto-remove-msi-release-20261030.log`。
- 原生策略测试：旧版路径/版本与命令参数校验，拒绝降级、异常卸载路径/参数；退出1/1603/3010不继续，不重复执行；退出0仍有注册记录或服务也拒绝继续。使用内存委托，不启动任何真实卸载器。管理员ACL检查仍未运行。
- Inno：原生脚本编译及251项共享输入载荷哈希核对通过；日志 `.run/dac-auto-remove-inno-release-20261030.log`。未执行Inno解包或实际安装、卸载。
- 前端下载链接1.1.1及原九入口/配对/发现层80项定向测试通过（`.run/dac-auto-remove-frontend-20261030.log`）；Java未改动，复用此前结果，不重复测试或累计虚增数量。

跨安装器和Inno的旧版卸载与新程序安装不是一个事务。旧程序卸载成功而新安装失败时，数据保留，但不承诺自动恢复旧程序；需查看日志后重新运行完整新安装器。同类MSI升级仍由原MajorUpgrade事务处理。取消发生在卸载执行之前不移除旧版；进入安装执行阶段可能中断正在执行的设备命令。

本轮未实际卸载、安装或升级DAC，未启停/重启产品服务，未部署、发布下载、提交或推送。真正的MSI→MSI、MSI→Inno、Inno→MSI、Inno→Inno、取消/失败/需重启与历史数据保护验收，留待单独授权。本报告不将编译/模拟/只读会话当成实机升级完成。
