# F-CUS-001 V1.8 数据库证据

验证日期：2026-08-26

结论：`PASS WITH EXTERNAL BLOCKER`。迁移、当前主档、审计历史和数据库行为验证通过；创建闭环受 CRM 权威分类目录缺失阻断。

## 环境

- Compose project：`npdms`
- MySQL 容器：`npdms-mysql-1`
- MySQL：8.4，宿主机端口 `13307`
- 数据库：`npdms`
- 容器健康状态：`healthy`

## Flyway

当前库已成功执行：

- V87 `fcus001 customer master`，`success=1`
- V88 `fcus001 customer classification scope`，`success=1`
- V89 `fcus001 customer menu and permissions`，`success=1`

V87、V88、V89 已执行后保持不可变。

## 迁移主档

- `cus_customer_master` 共 5 条。
- ID 范围为 `1001` 至 `1005`，保留旧客户原 ID。
- 客户 `1005` 当前状态：
  - `code=CUST-005`
  - `short_name=成都智慧验收`
  - `lifecycle_status=DISABLED`
  - `version=2`
  - `deleted=0`
  - `tenant_id=1`

## 字段与生命周期历史

`cus_customer_field_history` 使用摘要列：

- `before_value_digest`
- `after_value_digest`

客户 `1005` 已记录三条审计历史：

- `shortName` 更新，Owner 为 `CRM`，operation ID 为 `28453c00-1799-4c78-baf6-e2d64ad1805f`。
- `lifecycleStatus` 变更，Owner 为 `CUS`，operation ID 为 `f02f2e3e-0ac2-4360-980a-9bb5598dde95`。
- `lifecycleReason` 记录，Owner 为 `CUS`，operation ID 同上。

历史表只保存摘要，不暴露明文前后值。删除守卫失败后没有新增成功删除状态，也没有改变客户版本。

## 真实 MySQL 自动测试

执行：

```text
mvn.cmd -pl pms-module-customer -am "-DskipITs=false" "-Dtest=CustomerCommandMySqlTest,CustomerLifecycleMySqlTest,CustomerScopeSqlMySqlTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：10/10 通过，Reactor `BUILD SUCCESS`。

覆盖：

- 同租户客户编码唯一，跨租户可复用。
- 软删除不释放客户编码。
- 同租户 CRM 当前映射唯一。
- 同版本并发平台更新仅一个成功，另一个 CAS 失败。
- 停用不触发软删除并递增版本。
- 同版本并发删除仅一个成功。
- 恢复保留原客户 ID 和编码并递增版本。
- 同切片五维 AND、多切片 OR，未展平重组。
- 详情与列表使用同一未展平切片语义。
- 分类字段 CAS 更新拒绝陈旧版本。

## 权威分类阻断

`cus_market_relation` 当前为 0 条。数据库中不存在可用于构造 CRM 权威四级组合的正式来源，因此不能合法生成：

- `marketCode`
- `systemCode`
- `expendCode`
- `industryCode`

本轮未修改已执行迁移，也未以测试种子、名称推断或人工编码伪造权威值。平台客户创建和临时客户创建继续保持外部阻断。

本记录只证明当前数据库迁移与行为验证结果，不构成完整 UAT 或发布批准。
