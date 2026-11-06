# Windows 完整构建复核与云端修复记录（2026-11-06）

结论：原生 Windows DAC 主要实现确实存在。Java reactor 与网页构建已通过，生成包含网页的真实 DAC 服务 JAR；正式冻结校验因缺失输入失败，尚无本轮完整 Windows 安装包，也没有真实 Windows 安装运行验收。

## 源码范围与纠正

固定起点 `08bfe65ad5a7c91e554c4441ffaeb2eaef8627c8`，对应远端 `implementation/electron-rebuild-20261105`。原生 DAC 脚本默认版本 1.1.8；主分支快照为 `71b525248d224c0cddf5f507aff03045e476d057`，未修改、未推送。修复仅在 `build/windows-complete-cloud` 分支。

| 对象 | 真实源码与构建情况 |
|---|---|
| Windows 密钥存储 | `device-ops-platform/packaging/windows/ClientConfiguration.cs` 已实现 `ProtectedData.Protect/Unprotect`、LocalMachine DPAPI 和服务/管理员 ACL；不能说原生 Windows 没有密钥存储。未在真实 Windows 验证。 |
| Windows DAC 服务与安装器 | `RuntimeHost.cs`、`Launcher.cs`、`SdpDacService.xml`、`Package.wxs`、`Package.iss` 及打包脚本存在；包含独立宿主、端口候选、协议注册、升级与保留数据逻辑。 |
| 断网续跑与补传 | `DesktopSynchronization.java` 和 JDBC Outbox 已实现现有任务结果的断网保留、重试及匹配回执；本轮相关 Java 测试通过。 |
| 断网新操作 | `DesktopTicketFilter` 每次在线兑换操作票据；测试 `unreachableServerNeverFallsBackToLocalDebugAuthentication` 明确要求断网拒绝。不能通过打包取消授权。 |
| 离线巡检 | 主平台前后端有离线脚本/日志上传流程；它不是整个平台完全脱网运行。 |
| Electron 外壳 | `npdms-desktop/src/key-store.cjs` 的 Linux 限制、`main.cjs` 的离线关闭与合成试验属于新增外壳；与原生 DAC DPAPI/WinSW 是不同路径。把后者接入外壳需真实集成与验收，不能只复制 JAR 就称完成。 |

## 本轮修复

1. 受控工具链增加显式路径覆盖，保留 Windows 默认值、共享文件锁、工作树独立 Maven 仓库、源码前后指纹和冻结验证；校验 JDK 25.0.1+8、Maven 3.8.6 精确基线。
2. 原 `data/` 忽略规则会漏掉前端 `src/data/common-commands.ts`。原基线 Git 树没有该文件。本轮仅接入已有 `npdms-desktop/dac-web-overrides` 的显式不可用目录及手动编辑保持补丁，并窄化源码例外。未编造设备命令；完整预设仍需权威原文件。
3. SSH 并发测试的主机密钥屏障可在 NIO2 accept 初始读取内同步阻塞，导致只能接收少数连接。线程诊断证明这一调用栈。测试夹具将首次读取移出 accept 回调，并为八个刻意阻塞的会话配置足够 I/O workers，清理专用执行器。生产 SSH 代码未改；八会话、四命令、错误指纹拒绝、输出隔离和共享客户端生命周期断言保留。

## 验证与产物

- DAC Java 全 reactor：519 tests，0 failures/errors/skipped；包含真实隔离 MySQL Testcontainers 测试。Maven `BUILD SUCCESS`。
- DAC 网页：27 个测试文件 / 131 tests 通过；TypeScript 与 Vite 构建通过；lint 0 errors、598 原有格式 warnings，未批量改格式。
- 工具链验证通过；并发第二次构建被原工作树锁拒绝；错误 JDK 21 被拒绝；8 个 source/freeze 工具单元测试通过。
- `device-ops-server.jar`：50,022,417 字节；SHA256 `b9ee3b4b6c84255baabcc5f32b7e431880881935d131258fda51a469022ee789`。
- JAR CRC 完整性通过；嵌入 27 个网页文件 / 1,700,004 字节逐项与构建 dist 相同，DesktopTicketFilter、DesktopSynchronization、DesktopIdentity、DesktopParserInitialization、DesktopRuntimePublication 及 H2 依赖存在。
- 没有运行生产服务、修改用户本机、改动 master、禁用沙箱/证书校验/授权或跳过失败测试。

## 精确阻塞

1. **正式冻结失败**：`scripts/worktree-artifact.py` 要求 `.run/dac-host/verify-deployed-repairs.py`，但所选仓库和基线无此文件。Maven 成功之后受控入口以 `Release validation failed` 退出，没有生成可发布 manifest。既有 `docs/generated/2026-10-29-179-base-sync-execution-report.md` 已记录同一缺失。没有删掉、伪造或代替这一未知校验。
2. **原生 Windows 验证环境缺失**：`package-dac-desktop.ps1` 要求 Windows .NET Framework 编译器、DPAPI/ACL tests、WiX/DTF 真实只读 MSI Session、Windows JRE/jlink；Inno 必须消费该校验通过的 MSI payload。本轮 Linux 云实例未执行这些 Windows 步骤，未构造虚假 `passed-real-msi-session`。
3. **命令预设源码未入库**：已有缺失提示允许构建，但不表示恢复了完整预设目录。问题登记于 `docs/decisions/open-questions.md` 的 `Q-DAC-WINDOWS-SOURCE-COMPLETENESS-20261106`。
4. **交付认证**：上轮大型安装器上传 helper 在 tools/list 初始化收到 HTTP 401，未进入 prepare 或传输，本轮没有重试该安装器写入。独立小型构建证据归档如保存成功，其 Library ID 和回读结果另行记录；不能据此声称完整安装器已上传。

此 JAR 是未冻结的构建候选，用于继续验证，不是 Windows 安装器或已验收发布。没有组织签名、真实 Windows 安装/卸载/升级、桌面窗口或线上业务验收结果。下一步先恢复原冻结校验脚本和命令预设原文件，再在云端 Windows 环境沿既有脚本生成并验证原生安装器；若目标是 Electron 整体脱网产品，还需确认并实现超出现有 DAC 票据契约的集成范围。
