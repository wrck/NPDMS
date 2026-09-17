# OA 持久化验收测试

`OaDeliveryPersistenceTest` 使用实际 MySQL、MyBatis Mapper、Spring 独立事务和 Flowable；远程 OA 使用本机 HTTP 契约服务，OAuthTokenCache 为测试替身。不得将其描述为部署环境或真实 OA 的端到端验收。

## 数据安全

测试仅接受 `jdbc:mysql://127.0.0.1:<port>/npdms_migration_acceptance`，可附连接参数。必须使用一次性专用数据库；测试会删除并重建该库的 `int_log`，并创建流程引擎和测试标记表。禁止将开发库、共享验收库或生产库改名后复用。

提供 `NPDMS_OA_TEST_JDBC_URL`、`NPDMS_OA_TEST_DB_USER`、`NPDMS_OA_TEST_DB_PASSWORD`。没有环境变量时本地测试跳过；正式验收必须加 `-Dnpdms.oa.require-mysql=true`，并要求七项用例实际执行、零跳过。仓库 `.github/workflows/oa-mysql-acceptance.yml` 已创建独立 MySQL service 并检查报告完整性。

## 验证内容

七项数据库测试覆盖外层回滚后的失败日志留存、成功重试清空旧错误与重试时间、非 2xx 响应不能记成功、重试耗尽清空时间、空响应清除旧内容、令牌失败不发送 HTTP，以及 OA 持续失败时实际 Flowable 的审批退回路径、重新处理和完成。

流程用例使用测试专用 review/rework BPMN，不替代项目生产流程或应用层退回命令。没有装配实际 Redis、Resilience4j 全部切面、全局鉴权过滤器和租户隔离链。

仓库根目录、JDK 25：

```bash
mvn -B -ntp -pl pms-module-integration -am \
  -Dtest=OaTaskListenerTest,OaTodoPortAdapterTest,OaDeliveryPersistenceTest \
  -Dnpdms.oa.require-mysql=true \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

验收要求三个报告分别包含 5、5、7 项用例，失败、错误、跳过均为零。原有十项监听器/端口用例包含在十七项总数中，不重复计数。

## 故障复现基线

GitHub Actions run `35200704549` 在修复前提交 `98999cf6` 上实际运行 MySQL 8.4.11，七项新增用例中四项断言失败、三项通过，零执行错误和零跳过。失败对应旧错误残留、重试时间残留、空响应未清除以及非 2xx 被记为成功。

生产修复 `e00a6123` 未修改这些断言；run `35200957124` 的十七项测试全部通过，产物为 `10487722887`。该先失败后通过的证据用于防止将持久化测试退化为只检查内存对象或 mock 调用。
