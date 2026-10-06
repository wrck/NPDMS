# 公共默认框架浏览器验证

本夹具使用真实 `useBusinessEntity`、`BusinessEntityForm`、form-create、`DeclaredBusinessView`
和 `BusinessEntityHost`，调用 `DeclaredLoginRuntimePersistenceTest` 的隔离 HTTP 服务。
后端执行实际 AdminAuth/OAuth2、SQL 角色/菜单授权、Redis Token 缓存、生产 Token/租户
过滤器、方法权限、默认服务、MyBatis、事务和账本/审计/outbox。每次生成内存随机密码，
浏览器只接收测试服务的 HttpOnly Cookie；恢复记录不保存业务正文或凭据。

必须先创建独占 MySQL 8.4 和 Redis 7.4 Compose 资源，核对项目标签、tmpfs 和监听：
MySQL 仅绑定 `127.0.0.1:27461`，库名 `npdms_declared_framework`，临时 root 无密码；
Redis 仅绑定 `127.0.0.1:27464`、无持久卷。不得指向应用、共享或生产资源。
测试会重建该独占库的夹具表。基础设施不包含前端/后端应用容器。

在 JDK 25、Maven、Node、pnpm 环境中安装锁定依赖，然后在 UI 根目录运行：

```bash
NPDMS_DECLARED_EXCLUSIVE=true \
NPDMS_MAVEN_SETTINGS=/path/to/maven-settings.xml \
NPDMS_BROWSER_PACKAGES=/path/to/node_modules \
NPDMS_BROWSER_EXECUTABLE=/usr/bin/chromium \
node tests/declared-business-browser/verify.mjs
```

Maven settings 和 Browser packages 参数在默认安装已能找到依赖时可以省略。
脚本启动并清理自己创建的 Maven/浏览器/Vite 进程；Compose 由创建者清理。
结果、截图及后端日志写入仓库 `.run/cloud-20261006/browser/`，不自动提交。

断言包括：真实登录；已提交但丢响应的创建通过 GET 恢复且不重发；恢复记录无正文；
真实表单固定字段别名、必填扩展和多选扩展原子保存；显式清空获得精确
`ENTITY_VALUE_INVALID` 错误，主体/ledger/audit/outbox/扩展记录保持不变；实际公共视图
所有输入及保存按钮只读。测试未改写服务器返回正文，也不降低授权或必填规则。

限制：HTTP 传输使用 MockMvc 生产过滤器/控制器；不是完整 Tomcat 应用。项目范围为
确定性测试事实；没有原生 Owner 页面，未启用的交付面板以空组件隔离。未覆盖真实
账号、完整 Owner 生命周期、两浏览器并发、生产迁移或部署；这些不能由本测试晋级。
