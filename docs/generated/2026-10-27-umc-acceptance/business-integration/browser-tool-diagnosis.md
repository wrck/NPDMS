# 浏览器控制constraints故障排查

日期：2026-10-28。范围：本聊天内置真实浏览器控制恢复，不变更NPDMS业务代码或安全策略。

## 已确认

- 浏览器可以枚举当前任务页，URL为http://localhost:19191/pms/inspection/tasks/2；页面操作之前，导航、AX、DOM、截图及CDP均报Cannot read properties of undefined (reading 'constraints')。
- 异常通过Node控制工具可信RPC返回，堆栈落在kernel.js的requestTrustedWorker响应处理；不是浏览器页面JavaScript抛出的业务异常。
- 当前Codex桌面版本26.1023.6123.0，CLI为0.165.0-alpha.1.2。实际CLI和node_repl二进制SHA-256均与本机官方安装包相同。
- 插件缓存仍为26.908.40834；官方安装包附带浏览器插件26.1023.50603。桌面日志出现plugin_marketplace_folder_write_failed / UNKNOWN copyfile，说明插件更新复制异常。此项与故障同时存在，尚不能断言它是根因。
- 已改用当前官方安装包附带的browser-client.mjs，未修改插件代码、权限或策略；错误仍存在。随后重建控制会话，再复核仍存在。没有覆盖缓存或应用文件。
- 官方桌面包中浏览器attach方法直接读取e.networkPolicy.constraints及timeoutMs，该属性读取可产生当前异常；缺失上下文的具体来源仍待桌面桥接重新初始化后复核。没有给缺失策略填宽松默认值或跳过校验。
- 当前仅内置浏览器连接可用，没有其他已连接的真实浏览器可切换。

## 恢复步骤与验收边界

已请需求方重启Codex桌面应用，再回同一聊天继续。依据官方故障建议https://learn.chatgpt.com/docs/chrome-extension#troubleshooting，应用重启可作为连接恢复步骤；该说明未直接解释此constraints错误，不能作为已修复证据。业务后端59191、前端19191、DAC48182、网关59192无需重启，已有授权保留。

恢复后直接在原离线巡检入口上传既有合成样本qa-umc-plaintext.txt，来源IP192.0.2.31，验证原offline-logs请求、原execution关联、新UMC任务/DOC、本地结果入库及原报告列表下载，记录新报告ID及摘要。不能用旧报告1、接口上传或构建通过代替该完整浏览器验收。

2026-10-28后续：需求方重启Codex后故障仍存在。随后明确同意改用独立Chrome测试会话，原页面真实浏览器新日志闭环通过（UMC77/692、本地V2）。内置控制故障没有被宣称修复，详情以report.md为准。
