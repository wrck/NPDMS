# DAC 1.1.5 剩余升级验证（2026-10-31）

状态：包验证和真实浏览器下载均通过，纳入安装包升级提交。未执行本机覆盖安装。

- 首次DAC完整构建因测试发布样例input.log缺失失败。源副本原文件位于E:/AICoding/Projects/NPDMS/device-ops-platform/device-ops-parser-semantic/src/test/resources/releases/line-log-1.0.0/input.log，内容为单行合成ERROR事件，受通用*.log忽略影响。已原样恢复到本工作树相同路径；此次显式纳入版本控制，避免新工作树再次缺少样例。
- 恢复后scripts/worktree-build.ps1 -Target dac完整测试及打包成功，DAC既有修复保护通过；冻结清单.run/build/releases/e079d3e9-c55d-4350-b6e4-2e85501f7d4c/manifest.json。
- M盘首次MSI链接失败，磁盘仅剩约33MiB。清理本轮生成目录的命令被自动审批拒绝，未执行删除。保留失败现场，改在E盘生成。
- 成功产物：E:/AICoding/Projects/NPDMS/.run/build/dac-desktop-1.1.5-853c-validation/NPDMS-DAC-1.1.5-x64.msi，90,368,497字节。
- InstallerValidationTests通过真实MSI会话检查：ProductVersion大于1.1.4，Upgrade范围包含1.1.4且允许替换；原有验证、安装动作次序和配置校验仍通过。251个文件提取后与payload逐项一致。
- 修正publish-dac-download.ps1仍固定1.1.4的遗漏，对齐已批准的1.1.5。磁盘释放后已通过该脚本发布到测试前端下载目录。
- 真实浏览器项目84574配置调试的命令采集弹窗，下载链接为/downloads/dac/NPDMS-DAC-1.1.5-x64.msi。该地址HEAD返回200 text/html，不能认定为MSI下载成功。
- 用户释放空间后，M盘可用约1.75GiB。发布后该地址返回application/octet-stream，Content-Length为90368497。真实浏览器从上述弹窗点击“下载并安装 DAC”，文件保存为F:/下载/NPDMS-DAC-1.1.5-x64.msi；文件大小90,368,497字节，SHA256为02732AAF58CB5006B14B49C17B2B79F71DA8F10BD4EC92202FE2E11110CAC8F6，与构建产物完全一致。截图.run/remaining-validation/dac-download.png。
- 未执行实际软件安装、DAC启动、后端重启、数据库操作或59191操作；MSI升级定义验证不代表已在用户机器完成覆盖安装。

日志：.run/remaining-dac-build.log、.run/remaining-dac-build-retest.log、.run/remaining-msi-package.log、.run/remaining-msi-retest.log；包目录logs/installer-validation.log及package-result.json。
