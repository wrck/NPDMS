# DAC 1.1.2 安装失败修复准备（2026-10-30）

用户实机记录：1.1.1 Inno在18:57:30～18:58:00卸载旧MSI1.0.6成功；18:58:14尝试启动宿主，18:58:20 WinSW报告CreateProcess拒绝访问；18:58:09已经执行打开状态页，18:58:44才报告服务初始化失败，之后清理新服务但留下Inno安装登记。随后用户尝试MSI并得到1603，具体失败动作缺少完整安装日志。不能把1603解释为已证实与Inno完全相同的原因。

只读证据：用户临时目录`Setup Log 2026-10-30 #003.txt`、Windows Application/System事件、受Windows管理员确认后只读导出的`.run/dac-install-failure-readonly-20261030.txt`。只读复核MSI卸载Plan通过（`.run/build/dac-readonly-install-plan-20261030/plan.log`），没有执行Remove动作。

## 1.1.2修复与检查

- 程序目录显式增加LocalService读/执行权限，不授予程序写权限，保留其他ACL/拒绝规则；不进入ProgramData密钥、连接或历史目录；遇重解析点拒绝跨目录访问。WinSW工作目录明确为安装目录。已确认Windows拒绝访问发生在创建宿主进程阶段，实际触发的Windows规则仍需通过恢复验收确认，不能仅凭隔离ACL测试宣布服务已恢复。
- Inno服务初始化改到最后程序文件安装后的AfterInstall，在注册和Run之前执行；成功后ServiceReady才允许打开状态页，静默仍不打开。移除ssPostInstall中太晚执行的启动逻辑，防止先开页面或先登记安装成功再报初始化失败。
- 原生操作失败只记动作名、异常类型、Win32错误码，不写参数或秘密。
- 隔离目录真实ACL测试通过：宿主/JRE获LocalService读执行，无新增写权限，旁边数据文件内容与ACL不变。自动卸载策略、隔离浏览器策略、URL/协议与DPAPI往返检查通过；真实MSI会话只读校验及251项解包哈希通过；前端80项回归通过。Java未改动，沿用冻结载荷37fe9c5a，不重复计算此前518/20项结果。
- 日志：`.run/dac-install-fix-msi-release-20261030.log`、`.run/dac-install-fix-inno-release-20261030.log`、`.run/dac-install-fix-frontend-20261030.log`。

## 待授权恢复包

| 类型 | 文件 | 字节 | SHA256 |
|---|---|---:|---|
| MSI | [NPDMS-DAC-1.1.2-x64.msi](E:/AICoding/Projects/NPDMS/.run/build/dac-desktop-1.1.2-20261030-install-fix-release/NPDMS-DAC-1.1.2-x64.msi) | 90364405 | `dd7a875da30aa776c45ecb202aa20a0d24fa4ea473ee08eff6dfb09e5a6624c2` |
| Inno | [NPDMS-DAC-1.1.2-x64-inno.exe](E:/AICoding/Projects/NPDMS/.run/build/dac-inno-1.1.2-20261030-install-fix-release/NPDMS-DAC-1.1.2-x64-inno.exe) | 87795086 | `46a829f42dc9f1c2b84d3c81ba811c4b8b97efeded222a50a8530697b1f5129d` |

两包同载荷，旧包和历史证据保留。本轮未实际安装、卸载或启停服务、未部署/发布下载、未提交或推送。当前失败安装残留未清理。真正的本机恢复需要批准自动卸载失败1.1.1、安装1.1.2、注册/启动服务，之后核对实际端口、进程及本机状态页，并用完整安装日志复核；不重新下发设备命令，原Outbox按既有逻辑可能补传原结果。
