# F-CUS-001 V1.8 回归摘要

验证日期：2026-08-26

结论：`PARTIAL PASS`。实现与当前可执行验收路径通过；CRM 权威分类依赖导致创建、删除成功和恢复浏览器闭环未完成。

## 已通过

- Java 定向契约与应用服务：`CustomerControllerContractTest`、`CustomerApplicationServiceTest`、`CustomerLifecycleApplicationServiceTest` 共 25/25 通过，Reactor `BUILD SUCCESS`。
- 真实 MySQL：`CustomerCommandMySqlTest`、`CustomerLifecycleMySqlTest`、`CustomerScopeSqlMySqlTest` 共 10/10 通过，Reactor `BUILD SUCCESS`。
- Python F-CUS-001 契约：模块边界、V87 迁移、旧写退役、Owner 边界、前端契约共 16/16 通过。
- Python 回归中发现并修正静态契约路径：后端资源路径为 `/pms/customers`，动态菜单前端页面路径为 `/customer-asset/customers`；生产页面无需修改。
- 浏览器：客户列表、平台字段更新、停用、删除引用守卫、刷新持久化、旧页面只读和历史快照分离通过。
- 响应式：320、768、1024、1440 四档设备视口无页面级横向溢出。
- 浏览器控制台：320 视口下新工作台与旧历史页均无 error。
- 浏览器网络：新客户列表和旧历史列表请求均返回 HTTP 200。
- 数据库：Flyway V87 至 V89 成功，5 条迁移客户保留原 ID；客户 `1005` 当前为 `DISABLED/version=2/deleted=0`。
- 审计：简称、生命周期状态和生命周期原因均写入摘要历史。
- 差异质量：`git diff --check` 通过。

## 本轮修复

- 单租户关闭模式下，客户命令从认证 `LoginUser` 回退 tenantId，同时保持命令 tenantId 一致性校验。
- 无分类快照迁移客户仅允许全量管理员执行生命周期治理，普通范围仍失败关闭。
- OAuth2 单租户令牌携带 tenantId，保证刷新后的真实客户请求仍有正确租户身份。
- 旧客户历史页保持只读，并跳转动态菜单前端路径。

## 未完成与阻断

- 平台客户创建、临时客户创建、CRM 四级级联和 CRM 字段只读真实闭环：`cus_market_relation=0`，没有合法权威分类组合。
- 删除成功与恢复真实浏览器闭环：当前客户 `1005` 存在 PROJ/AST 引用；没有合法创建的无引用客户或已删除客户。
- 浏览器自动化脚本与截图落盘未完成。当前浏览器验收由真实 Chrome MCP 执行并取得 DOM、网络、控制台和设备视口证据；截图接口因工作区白名单拒绝写入当前工作树。
- Task 14 规格追踪与实现基线尚未执行。
- Task 15 全量后端、前端 lint/typecheck/build 和基线测试尚未执行。

以上未完成项不得标记为完整 F-CUS-001 UAT 通过，也不构成发布 Gate 或 Release GO。
