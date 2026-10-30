# DAC 1.1.3 普通用户打开状态页修复（2026-10-30）

用户安装1.1.2后反馈启动器通用错误。只读证据确认：20:31:24服务成功运行，安装版本1.1.2，运行端口8280，发现协议3，HTTP状态READY，Inno本次安装日志显示成功；此问题与此前1.1.1无法创建服务子进程不同。

已在普通用户上下文复现旧逻辑Process.HasExited读取LocalService进程句柄时抛Win32Exception(5)。新逻辑去掉跨账户进程查询，继续核对受保护运行记录、公钥、运行实例、端口、随机挑战签名和READY；不降低业务票据或身份校验。

验证：只读集成回归先复现旧拒绝访问，再用实际已运行DAC和随包JRE完成签名验证并取得http://127.0.0.1:8280/desktop-status，未启停服务或打开浏览器（`.run/dac-status-permission-regression-20261030.log`）。随后实际运行新产物payload/NpdmsDac.exe --open-status，默认浏览器打开动作执行成功，进程退出码0；未安装新包、未重启服务、未执行设备命令。未将浏览器打开动作等同完整业务验收。

MSI真实只读会话/251项解包哈希、原生ACL及自动卸载/配置/策略测试通过；打包额外执行普通用户实机签名回归通过。前端80项测试通过（`.run/dac-status-fix-frontend-20261030.log`）。日志：`.run/dac-status-fix-msi-20261030.log`、`.run/dac-status-fix-inno-20261030.log`。Java未变，复用37fe9c5a冻结载荷。

| 安装器 | 文件 | 字节 | SHA256 |
|---|---|---:|---|
| MSI | [NPDMS-DAC-1.1.3-x64.msi](E:/AICoding/Projects/NPDMS/.run/build/dac-desktop-1.1.3-20261030-status-fix/NPDMS-DAC-1.1.3-x64.msi) | 90364405 | `00a56fbc80673433ec86d26689880dc0069bf933bda7a998f2faf15715036480` |
| Inno | [NPDMS-DAC-1.1.3-x64-inno.exe](E:/AICoding/Projects/NPDMS/.run/build/dac-inno-1.1.3-20261030-status-fix/NPDMS-DAC-1.1.3-x64-inno.exe) | 87793630 | `792643079022ec5bb826e5b0d0ba445ca5620aff8aaed1ac4f2524d2d3f1ad8d` |

当前安装版本仍为用户自行安装的1.1.2，1.1.3仅准备代码和包。未修改已安装启动器、未部署/发布下载、未提交/推送。用户最新希望取消配对码，已提出首次点击连接由客户端握手、以后自动检测的免输码方案，尚待确认体验与协议设计；本1.1.3包仍保留原配对码流程，不声称已完成免配对。
