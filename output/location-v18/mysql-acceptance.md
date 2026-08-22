# V1.8 组织与地点 MySQL 验证记录

验证日期：2026-08-23
验证分支：`codex/v1-8-feature-revalidation-50eb`
基线提交：`e1a0672`

## 隔离环境

- Compose project：`npdms-50eb-location-v18`
- MySQL：`127.0.0.1:23306`，`mysql:8.4`，镜像 ID `sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d`
- Redis：`127.0.0.1:26379`，`redis:7.4-alpine`，镜像 ID `sha256:6ab0b6e7381779332f97b8ca76193e45b0756f38d4c0dcda72dbb3c32061ab99`
- Flyway：`flyway/flyway:11.10.5-alpine`，镜像 ID `sha256:de6bc28fa30c8b6b9f8118e0d283016a1943b79633d5f27b285bb7793a6ec5bd`
- MySQL、Redis 验证结束时均为 `healthy`。
- 未使用或启动 `npdms-t8-mysql-1`。

## 迁移验证

1. 对隔离 Compose project 执行 `down --volumes`，确认从空数据卷开始。
2. 启动 MySQL、Redis 后执行首次 `flyway migrate`：V1 至 V68 共 68 条迁移全部成功，当前版本 V68。
3. 再次执行 `flyway migrate`：校验 68 条迁移，无待执行迁移。
4. 执行 `flyway info`：V1 至 V68 均为 `Success`。
5. 执行 `flyway validate`：68 条迁移校验通过。
6. 对已有 `npdms-50eb` 环境执行前向升级：从 V63 连续应用 V64 至 V68，5 条新增迁移成功；随后 `validate` 通过，当前版本 V68。

隔离库复核结果：最新版本 `68 / success=1`，成功历史记录 `68` 条。

## 真实 MySQL 场景

执行命令：

```text
mvn.cmd -pl pms-module-project,pms-module-engineering,pms-module-asset -am "-DskipITs=false" "-Dtest=LocationMySqlIntegrationTest,ProjectManualCreationMySqlIntegrationTest,InstallationLocationMySqlIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

为允许隔离库创建故障注入 Trigger，仅在该隔离 MySQL 运行期设置 `log_bin_trust_function_creators=1`，未修改应用配置或 Flyway 迁移。

结果：12/12 通过。

- `LocationMySqlIntegrationTest`：2/2。覆盖启用精确映射、停用映射不参与、无匹配、同地址多站点、任意深度位置树、来源映射及事务回滚。
- `ProjectManualCreationMySqlIntegrationTest`：9/9。覆盖阶段、任务、里程碑、门禁、执行契约、交付件、幂等成功、审计及 Outbox 各故障点的全事实回滚。
- `InstallationLocationMySqlIntegrationTest`：1/1。覆盖设备安装、迁移、拆除的当前位置变化，并验证事务回滚后恢复原状态。

代表性种子复核：

- `330106 + DISTRICT` 启用精确映射：1 条。
- `330108 + DISTRICT` 启用映射：0 条，停用记录不参与建议。
- 地址 `930810` 关联站点：2 个。
- 站点 `930811` 位置节点：6 个，最大深度：5。

本记录仅证明迁移与数据库场景验证结果，不代表 UAT、发布 Gate 或 Release GO。
