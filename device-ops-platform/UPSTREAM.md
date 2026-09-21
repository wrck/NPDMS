# DAC 来源与部署边界

- 来源仓库：NPDP。
- 来源分支：`codex/crt-persistence-context`。
- 导入提交：`2d8a66d5197736ee612e879660bf0255b968bc86`。
- 导入内容：该提交的 `device-ops-platform/`，不含工作区数据库、密钥、依赖及运行数据。

本目录为独立 Maven reactor 和独立应用，使用独立数据库。NPDMS 通过
`pms-module-integration` 的 HTTP 网关调用，不将其加入根 Maven reactor，
不将其 Flyway 脚本交给 NPDMS 数据库执行。原生前端、解析器、SSH/TELNET
连接和脚本执行能力完整保留。

2026-09-21 专项授权：在该来源基础上补齐 NPDMS 的下发、查询、取消与签名
multipart 终态日志适配，保留 NPDMS 的授权、状态及业务消费契约。
