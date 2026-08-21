# F-PROJ-001 V1.8 真实 MySQL 验证证据

## 环境边界

- 执行日期：2026-08-22
- Compose 项目：`npdms-50eb`
- 数据库容器：`npdms-50eb-mysql-1`
- MySQL：`8.4.10`，宿主机端口 `13306`
- 数据卷：`npdms-50eb_npdms-mysql-data`
- 旧容器 `npdms-t8-mysql-1` 仅按授权停止，未删除、未复用其数据库或数据卷。
- Flyway `11.10.5` 校验通过，共校验 `63` 个迁移；MySQL 8.4 兼容性提示不影响本次校验结论。

## 定向原子性与并发验证

执行命令：

```text
mvn.cmd -pl pms-module-project -am '-DskipITs=false' '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=ProjectManualCreationMySqlIntegrationTest,ProjectManualCreationConcurrencyMySqlIntegrationTest' test
```

结果：`11/11 PASS`，无失败、错误或跳过。

原子失败测试使用真实 MySQL Trigger 分别在以下九个写入点制造失败：

- `STAGE`
- `TASK`
- `MILESTONE`
- `GATE`
- `CONTRACT`
- `ACC_DELIVERABLE`
- `IDEMPOTENCY_SUCCESS`
- `AUDIT`
- `OUTBOX`

每个失败点均在事务结束后查询 Project、Stage、Task、Milestone、Gate、任务执行契约、ACC 交付件、成功幂等事实、成功审计和 `ProjectCreated` Outbox，断言本次创建产生的事实全部为 `0`。

并发测试结果：

- 相同幂等 Key、相同摘要：只创建一个 Project，并返回一次成功与一次成功重放。
- 相同幂等 Key、不同摘要：只允许一个请求成功，另一请求返回幂等冲突。

## 全量后端回归

执行命令：

```text
mvn.cmd -pl pms-module-project -am '-DskipITs=false' test
```

结果：Reactor `19/19 SUCCESS`，`pms-module-project` 共 `154` 项测试全部通过，真实 MySQL 两个测试类共 `11` 项全部执行；构建结果为 `BUILD SUCCESS`。

## 验证中发现并修复的问题

- 测试从 Maven 子模块目录运行时无法定位仓库 `.env`，改为向上定位包含 `compose.yaml` 的仓库根目录。
- Connector/J 的 Java 字符集参数由无效的 `utf8mb4` 改为 `UTF-8`。
- Spring Boot 4 / MyBatis-Plus 3.5.16 下，用于初始化 Jackson TypeHandler 的返回对象被误注册为全局 `Object` TypeHandler，导致普通 Mapper 参数被 JSON 引号包裹；初始化后改为返回普通对象，并补充回归测试。
- 真实 MySQL 环境暴露出任务执行契约主键、租户上下文及审计时间字段与物理约束不一致，已分别按分布式主键、生产租户拦截语义和显式审计时间修正。

## 结论边界

本证据证明 Task 9 所要求的同库事务回滚、幂等并发和故障注入已在当前工作树隔离的真实 MySQL 上通过。它不替代真实浏览器验收、UAT、发布或治理门禁。自2026-08-22起，`specs/001-project-delivery-platform/`仅作历史参考，不参与本轮实施校验或门禁判定。
